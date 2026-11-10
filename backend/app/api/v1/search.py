import asyncio

from fastapi import APIRouter, Depends, File, Form, UploadFile
from sqlalchemy.ext.asyncio import AsyncSession

from app.ai.client import AIUnavailable, StructuredLLM
from app.ai.image_identifier import (
    CONFIDENCE_THRESHOLD,
    identification_to_query,
    identify_image,
    sniff_image_type,
)
from app.ai.query_interpreter import interpret, interpret_with_rules
from app.api.deps import llm_dep, registry_dep
from app.connectors.base import ConnectorError
from app.connectors.registry import ConnectorRegistry
from app.core.config import get_settings
from app.core.db import get_session
from app.core.errors import AppError, ServiceUnavailableError
from app.core.security import current_user
from app.models.tables import Search, User
from app.schemas.api import (
    Clarification,
    InterpretedQuery,
    SearchRequest,
    SearchResponse,
    UrlSearchRequest,
)
from app.schemas.domain import MARKETPLACE_NAMES, ProductQuery
from app.services.search import run_search
from app.services.url_parser import parse_marketplace_url

router = APIRouter(prefix="/search", tags=["search"])


@router.post("", response_model=SearchResponse)
async def search_text(body: SearchRequest, user: User = Depends(current_user),
                      session: AsyncSession = Depends(get_session),
                      registry: ConnectorRegistry = Depends(registry_dep),
                      llm: StructuredLLM | None = Depends(llm_dep)) -> SearchResponse:
    query, interpreter, notes = await interpret(body.query, llm)
    if query.quantity and body.quantity == 1:
        body.quantity = min(query.quantity, 20)
    # "mua 2 chai": quantity is how many to buy, not a pack-size filter on listings.
    query_for_match = query.model_copy(update={"quantity": None})
    return await run_search(
        session, user_id=user.id, registry=registry, llm=llm, query=query_for_match, raw_text=body.query,
        search_type="text", interpreter=interpreter, notes=notes, marketplaces=body.marketplaces,
        quantity=body.quantity, include_similar=body.include_similar)


@router.post("/image", response_model=SearchResponse)
async def search_image(image: UploadFile = File(...), hint: str | None = Form(default=None, max_length=200),
                       user: User = Depends(current_user), session: AsyncSession = Depends(get_session),
                       registry: ConnectorRegistry = Depends(registry_dep),
                       llm: StructuredLLM | None = Depends(llm_dep)) -> SearchResponse:
    limit = get_settings().max_image_bytes
    data = await image.read(limit + 1)
    if len(data) > limit:
        raise AppError(f"Ảnh quá lớn (tối đa {limit // (1024 * 1024)} MB).", code="image_too_large",
                       status_code=413)
    if not data:
        raise AppError("Ảnh trống.", code="empty_image", status_code=422)
    sniff_image_type(data)
    if llm is None:
        raise ServiceUnavailableError(
            "Tìm bằng hình ảnh cần AI nhưng máy chủ chưa được cấu hình khoá AI. Hãy tìm bằng văn bản.",
            code="ai_unavailable")
    try:
        ident = await identify_image(llm, data, hint)   # the image is processed in memory, never stored
    except AIUnavailable as exc:
        raise ServiceUnavailableError(f"Không nhận diện được ảnh: {exc}", code="ai_unavailable") from exc

    label = "Ảnh sản phẩm" + (f" — {hint}" if hint else "")
    if not ident.is_product or ident.confidence < CONFIDENCE_THRESHOLD:
        search = Search(user_id=user.id, search_type="image", query=label, spec={}, result_count=0)
        session.add(search)
        await session.commit()
        from datetime import UTC, datetime
        message = ("Ảnh không giống một sản phẩm. Hãy thử ảnh khác hoặc nhập tên sản phẩm."
                   if not ident.is_product else
                   "Chưa đủ chắc chắn về sản phẩm trong ảnh. Chọn một gợi ý hoặc nhập tên sản phẩm.")
        return SearchResponse(
            search_id=search.id, search_type="image",
            query=InterpretedQuery(raw=label, interpreter="ai", spec=identification_to_query(ident),
                                   notes=[f"Độ tin cậy nhận diện: {ident.confidence:.0%}. {ident.reason}"]),
            demo_mode=registry.demo_mode, partial=False, sources=[], groups=[], similar_groups=[],
            clarification=Clarification(needed=True, message=message,
                                        suggestions=ident.alternative_queries[:3]),
            generated_at=datetime.now(UTC))
    query = identification_to_query(ident)
    notes = [f"Nhận diện từ ảnh với độ tin cậy {ident.confidence:.0%}: {ident.reason}"]
    return await run_search(
        session, user_id=user.id, registry=registry, llm=llm, query=query,
        raw_text=query.search_keywords or label, search_type="image", interpreter="ai", notes=notes)


def _query_from_title(title: str) -> ProductQuery:
    return interpret_with_rules(title.replace("[MẪU]", " "))


@router.post("/url", response_model=SearchResponse)
async def search_url(body: UrlSearchRequest, user: User = Depends(current_user),
                     session: AsyncSession = Depends(get_session),
                     registry: ConnectorRegistry = Depends(registry_dep),
                     llm: StructuredLLM | None = Depends(llm_dep)) -> SearchResponse:
    parsed = parse_marketplace_url(body.url)
    name = MARKETPLACE_NAMES[parsed.marketplace]
    if parsed.is_short_link:
        raise AppError("Link rút gọn chưa được hỗ trợ. Hãy mở link và sao chép link đầy đủ của sản phẩm.",
                       code="short_link_unsupported", status_code=422)
    if parsed.is_mock != registry.demo_mode and parsed.is_mock:
        raise AppError("Link mẫu chỉ dùng được trong chế độ demo.", code="invalid_url", status_code=422)

    seed, failure = None, None
    connector = registry.get(parsed.marketplace)
    if parsed.product_id and connector.is_mock == parsed.is_mock:
        try:
            seed = await asyncio.wait_for(connector.get_product_details(parsed.product_id),
                                          get_settings().connector_timeout_seconds)
            if seed is None:
                failure = "không tìm thấy tin đăng"
        except TimeoutError:
            failure = "sàn không phản hồi kịp"
        except ConnectorError as exc:
            failure = str(exc)
    elif parsed.product_id:
        failure = "máy chủ đang ở chế độ demo nên không truy xuất link thật"
    else:
        failure = "link không chứa mã sản phẩm"

    if seed is not None:
        query = _query_from_title(seed.title)
        notes = [f"Đã lấy thông tin tin đăng gốc từ {name}."]
        seeds = [seed]
    elif parsed.slug_text:
        query = _query_from_title(parsed.slug_text)
        notes = [f"Không truy xuất được tin đăng gốc từ {name} ({failure}). "
                 "Đang tìm theo tên sản phẩm đọc được trong link — tin đăng gốc không nằm trong kết quả."]
        seeds = []
    else:
        raise AppError(f"Không lấy được thông tin sản phẩm từ link {name} này ({failure}). "
                       "Hãy thử tìm bằng tên sản phẩm.", code="url_unresolvable", status_code=422)
    return await run_search(
        session, user_id=user.id, registry=registry, llm=llm, query=query, raw_text=body.url,
        search_type="url", interpreter="rules", notes=notes, marketplaces=body.marketplaces,
        seed_offers=seeds)
