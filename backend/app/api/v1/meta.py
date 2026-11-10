from datetime import UTC, datetime

from fastapi import APIRouter, Depends, Query, Response
from sqlalchemy import func, select, update
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.ai import assistant
from app.ai.client import StructuredLLM
from app.api.deps import llm_dep, registry_dep
from app.connectors.registry import ConnectorRegistry
from app.core.config import get_settings
from app.core.db import get_session
from app.core.errors import NotFoundError
from app.core.security import current_user
from app.models.tables import (
    DeviceToken,
    MarketplaceOffer,
    MarketplaceSource,
    Notification,
    Product,
    ProductVariant,
    User,
)
from app.repositories import catalog
from app.schemas.api import (
    AssistantRequest,
    AssistantResponse,
    DeviceRegister,
    MeResponse,
    NotificationOut,
    PriceDropItem,
    SourcesStatusResponse,
    SourceStatus,
    TrendingResponse,
    VariantSummary,
)
from app.schemas.domain import Marketplace, ProductAttributes
from app.services.products import build_offers_response, get_variant_or_404
from app.services.push import FcmSender

router = APIRouter(tags=["meta"])


@router.get("/me", response_model=MeResponse)
async def me(user: User = Depends(current_user),
             registry: ConnectorRegistry = Depends(registry_dep)) -> MeResponse:
    return MeResponse(user_id=user.id, display_name=user.display_name, demo_mode=registry.demo_mode)


@router.get("/sources/status", response_model=SourcesStatusResponse)
async def sources_status(_: User = Depends(current_user),
                         registry: ConnectorRegistry = Depends(registry_dep),
                         llm: StructuredLLM | None = Depends(llm_dep)) -> SourcesStatusResponse:
    settings = get_settings()
    statuses = []
    for connector in registry.all():
        health = await connector.health_check()
        statuses.append(SourceStatus(
            marketplace=connector.marketplace, marketplace_name=connector.marketplace_name,
            connector=connector.connector_name, is_mock=connector.is_mock, status=health.status,
            verified_live=health.verified_live and not connector.is_mock, message=health.message,
            checked_at=health.checked_at))
    return SourcesStatusResponse(
        demo_mode=registry.demo_mode, ai_available=llm is not None,
        ai_model=llm.model if llm else None, push_configured=FcmSender(settings).configured,
        price_check_interval_minutes=settings.price_check_interval_minutes, sources=statuses)


@router.get("/explore", response_model=TrendingResponse)
async def explore(category: str | None = Query(default=None, max_length=60),
                  marketplace: Marketplace | None = None, _: User = Depends(current_user),
                  session: AsyncSession = Depends(get_session),
                  registry: ConnectorRegistry = Depends(registry_dep)) -> TrendingResponse:
    """Built only from prices this installation has itself recorded — nothing is invented.
    Mock and real data are never mixed: rows are filtered by the current mode."""
    stmt = (select(ProductVariant, Product.category, func.max(MarketplaceOffer.retrieved_at).label("seen"))
            .join(Product, ProductVariant.product_id == Product.id)
            .join(MarketplaceOffer, MarketplaceOffer.variant_id == ProductVariant.id)
            .join(MarketplaceSource, MarketplaceOffer.source_id == MarketplaceSource.id)
            .where(MarketplaceOffer.is_mock == registry.demo_mode)
            .group_by(ProductVariant.id, Product.category).order_by(func.max(MarketplaceOffer.retrieved_at).desc())
            .limit(40))
    if category:
        stmt = stmt.where(Product.category == category)
    if marketplace:
        stmt = stmt.where(MarketplaceSource.code == marketplace.value)
    rows = (await session.execute(stmt)).all()

    drops: list[PriceDropItem] = []
    recent: list[VariantSummary] = []
    for variant, cat, _seen in rows:
        offers = [o for o in await catalog.variant_offers(session, variant.id)
                  if marketplace is None or o.marketplace == marketplace]
        best = catalog.pick_best(offers)
        if len(recent) < 12:
            recent.append(VariantSummary(
                variant_id=variant.id, title=variant.title, attributes=ProductAttributes(**variant.attributes),
                lowest_final_price=best.price.final_price if best else None,
                lowest_price_confidence=best.price.confidence if best else None, offer_count=len(offers)))
        timeline = await catalog.price_timeline(session, variant.id, limit=2)
        if len(timeline) == 2 and timeline[1].lowest_final_price < timeline[0].lowest_final_price:
            drops.append(PriceDropItem(
                variant_id=variant.id, product_id=variant.product_id, title=variant.title,
                image_url=variant.image_url, category=cat,
                previous_lowest_price=timeline[0].lowest_final_price,
                current_lowest_price=timeline[1].lowest_final_price,
                marketplace=timeline[1].marketplace, is_mock=timeline[1].is_mock,
                recorded_at=timeline[1].recorded_at))
    drops.sort(key=lambda d: d.current_lowest_price / d.previous_lowest_price)
    reason = None
    if not rows:
        reason = ("Chưa có dữ liệu. Khám phá chỉ hiển thị sản phẩm và lượt giảm giá mà ứng dụng đã tự ghi "
                  "nhận qua các lần tìm kiếm và kiểm tra giá — hãy tìm một sản phẩm để bắt đầu.")
    elif not drops:
        reason = "Chưa ghi nhận lượt giảm giá nào giữa các lần kiểm tra. Dưới đây là sản phẩm đã xem gần đây."
    return TrendingResponse(demo_mode=registry.demo_mode, available=bool(rows), reason=reason,
                            price_drops=drops[:20], recently_seen=recent)


@router.get("/notifications")
async def list_notifications(unread_only: bool = False, user: User = Depends(current_user),
                             session: AsyncSession = Depends(get_session)) -> dict[str, list[NotificationOut]]:
    stmt = select(Notification).where(Notification.user_id == user.id)
    if unread_only:
        stmt = stmt.where(Notification.read_at.is_(None))
    rows = (await session.execute(stmt.order_by(Notification.created_at.desc()).limit(100))).scalars().all()
    return {"items": [NotificationOut.model_validate(n) for n in rows]}


@router.post("/notifications/{notification_id}/read", status_code=204)
async def mark_read(notification_id: int, user: User = Depends(current_user),
                    session: AsyncSession = Depends(get_session)) -> Response:
    result = await session.execute(
        update(Notification).where(Notification.id == notification_id, Notification.user_id == user.id)
        .values(read_at=datetime.now(UTC)))
    if result.rowcount == 0:
        raise NotFoundError("Không tìm thấy thông báo.")
    await session.commit()
    return Response(status_code=204)


@router.post("/devices", status_code=204)
async def register_device(body: DeviceRegister, user: User = Depends(current_user),
                          session: AsyncSession = Depends(get_session)) -> Response:
    await session.execute(
        pg_insert(DeviceToken).values(user_id=user.id, token=body.token, platform=body.platform)
        .on_conflict_do_update(index_elements=["token"],
                               set_={"user_id": user.id, "updated_at": datetime.now(UTC)}))
    await session.commit()
    return Response(status_code=204)


@router.post("/assistant/ask", response_model=AssistantResponse)
async def ask_assistant(body: AssistantRequest, user: User = Depends(current_user),
                        session: AsyncSession = Depends(get_session),
                        registry: ConnectorRegistry = Depends(registry_dep),
                        llm: StructuredLLM | None = Depends(llm_dep)) -> AssistantResponse:
    products = []
    for variant_id in dict.fromkeys(body.variant_ids):
        variant = await get_variant_or_404(session, variant_id)
        products.append(await build_offers_response(session, user.id, variant, registry.demo_mode))
    return await assistant.answer(body.question, products, llm)
