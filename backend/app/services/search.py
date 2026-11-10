"""Điều phối tìm kiếm: gọi connector song song -> chuẩn hoá -> so khớp -> tính giá -> xếp hạng.

Ranking and "cheapest" are decided here by deterministic code only. AI is used solely to
interpret the query (validated) and, optionally, to refine ambiguous matches (clamped).
"""
import asyncio
import time
from dataclasses import dataclass
from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.ai import match_judge
from app.ai.client import StructuredLLM
from app.connectors.base import (
    ConnectorError,
    ConnectorNotConfigured,
    ConnectorNotImplemented,
    MarketplaceConnector,
)
from app.connectors.registry import ConnectorRegistry
from app.core.config import get_settings
from app.core.logging import get_logger
from app.models.tables import Search, Seller, Wishlist
from app.repositories import catalog
from app.schemas.api import (
    BestOffer,
    Clarification,
    InterpretedQuery,
    OfferOut,
    ProductGroup,
    SearchResponse,
    SourceResult,
)
from app.schemas.domain import (
    Marketplace,
    MatchResult,
    MatchType,
    PriceConfidence,
    ProductAttributes,
    ProductQuery,
    RawOffer,
)
from app.services.matching import is_vaguer_description, label_of, match_query, same_variant
from app.services.normalization import normalize_title, query_to_attributes
from app.services.pricing import compute_price, ranking_key
from app.utils.text import fold, format_vnd

log = get_logger(__name__)
_MATCH_ORDER = {MatchType.EXACT: 0, MatchType.LIKELY: 1, MatchType.SIMILAR: 2, MatchType.DIFFERENT: 3}


@dataclass
class Candidate:
    raw: RawOffer
    attrs: ProductAttributes
    match: MatchResult


async def _query_connector(connector: MarketplaceConnector, keywords: str,
                           timeout: float) -> tuple[SourceResult, list[RawOffer]]:
    started = time.perf_counter()
    status, message, offers = "ok", None, []
    try:
        offers = await asyncio.wait_for(connector.search_products(keywords), timeout)
        # A connector must label its data honestly; enforce it here rather than trust it.
        offers = [o for o in offers if o.is_mock == connector.is_mock and o.marketplace == connector.marketplace]
    except TimeoutError:
        status, message = "timeout", f"{connector.marketplace_name} không phản hồi trong {timeout:g} giây."
    except ConnectorNotConfigured as exc:
        status, message = "not_configured", str(exc)
    except ConnectorNotImplemented as exc:
        status, message = "not_implemented", str(exc)
    except ConnectorError as exc:
        status, message = "error", str(exc)
    except Exception as exc:  # a connector bug must not break the other marketplaces
        log.exception("connector_crash", extra={"marketplace": connector.marketplace.value})
        status, message = "error", f"Lỗi không mong đợi: {type(exc).__name__}"
    return SourceResult(
        marketplace=connector.marketplace, marketplace_name=connector.marketplace_name, status=status,
        is_mock=connector.is_mock, offers_found=len(offers),
        elapsed_ms=round((time.perf_counter() - started) * 1000), message=message), offers


async def fetch_offers(registry: ConnectorRegistry, keywords: str,
                       marketplaces: list[Marketplace] | None) -> tuple[list[SourceResult], list[RawOffer]]:
    timeout = get_settings().connector_timeout_seconds
    results = await asyncio.gather(*(_query_connector(c, keywords, timeout)
                                     for c in registry.select(marketplaces)))
    sources = [r[0] for r in results]
    offers = [o for r in results for o in r[1]]
    return sources, offers


def cluster(candidates: list[Candidate]) -> list[list[Candidate]]:
    groups: list[list[Candidate]] = []
    for cand in candidates:
        for group in groups:
            if same_variant(group[0].attrs, cand.attrs):
                group.append(cand)
                break
        else:
            groups.append([cand])
    return groups


def _out_of_budget(price, query: ProductQuery) -> bool:
    amount = price.final_price if price.final_price is not None else price.known_subtotal
    if query.max_price is not None and amount > query.max_price:
        return True
    return query.min_price is not None and amount < query.min_price


def _sort_offers(offers: list[OfferOut]) -> list[OfferOut]:
    return sorted(offers, key=lambda o: (o.over_budget,
                                         *ranking_key(o.price, f"{o.marketplace.value}:{o.id}")))


def _group_sort_key(group: ProductGroup) -> tuple:
    first = group.offers[0]
    return (_MATCH_ORDER[group.match.match_type], group.best_offer_id is None,
            *ranking_key(first.price, str(group.variant_id)))


def _best_banner(groups: list[ProductGroup], query: ProductQuery) -> tuple[BestOffer | None, Clarification]:
    exact = [g for g in groups if g.match.match_type == MatchType.EXACT and g.best_offer_id]
    if not exact:
        if groups:
            return None, Clarification(
                needed=True,
                message="Chưa có tin đăng nào vừa chắc chắn đúng sản phẩm vừa đủ thông tin giá, nên DealMate "
                        "chưa khẳng định nơi rẻ nhất. Hãy xem các kết quả bên dưới hoặc tìm cụ thể hơn.",
                suggestions=[g.title for g in groups[:4]])
        return None, Clarification()

    def best_of(group: ProductGroup) -> OfferOut:
        return next(o for o in group.offers if o.id == group.best_offer_id)

    verified = [g for g in exact if best_of(g).price.confidence == PriceConfidence.VERIFIED]
    group = min(verified or exact, key=lambda g: best_of(g).price.final_price)
    offer = best_of(group)
    is_verified = offer.price.confidence == PriceConfidence.VERIFIED
    label = "đã xác minh" if is_verified else "ước tính"
    explanation = (
f"«{group.title}»: {offer.marketplace_name} — shop {offer.seller.name} có giá cuối thấp nhất ({label}): "
        f"{format_vnd(offer.price.final_price)} = niêm yết {format_vnd(offer.price.subtotal)} "
        f"− voucher {format_vnd(offer.price.voucher_discount)} + ship {format_vnd(offer.price.shipping_fee)}"
        + (f" + phí {format_vnd(offer.price.mandatory_fees)}" if offer.price.mandatory_fees else "") + ".")
    if offer.is_mock:
        explanation = "[DỮ LIỆU MẪU] " + explanation

    caveats: list[str] = []
    maybe_cheaper = [o for o in group.offers if o.id != offer.id and not o.over_budget
                     and (o.price.final_price if o.price.final_price is not None
                          else o.price.known_subtotal) < offer.price.final_price]
    if maybe_cheaper:
        caveats.append(f"Có {len(maybe_cheaper)} tin đăng có thể rẻ hơn nhưng giá chỉ là ước tính hoặc "
                       "thiếu phí vận chuyển, nên chưa được coi là rẻ nhất.")
    if not is_verified:
        caveats.append("Giá này có thành phần ước tính; hãy kiểm tra ở trang thanh toán.")
    clarification = Clarification()
    if len(exact) > 1:
        caveats.append(f"Yêu cầu của bạn khớp {len(exact)} biến thể khác nhau; đây là biến thể rẻ nhất.")
        clarification = Clarification(
            needed=True, message="Bạn muốn biến thể nào? Chọn để so sánh đúng sản phẩm.",
            suggestions=[g.title for g in exact[:5]])
    return BestOffer(variant_id=group.variant_id, variant_title=group.title, offer_id=offer.id, final_price=offer.price.final_price,
                     confidence=offer.price.confidence, marketplace=offer.marketplace,
                     marketplace_name=offer.marketplace_name, explanation=explanation,
                     caveat=" ".join(caveats) or None), clarification


async def refresh_wishlist_prices(session: AsyncSession, variant_id: int, lowest: int | None) -> None:
    """Keep 'current' and 'previous' lowest price on wishlist rows (previous changes only
    when the lowest price actually changes)."""
    now = datetime.now(UTC)
    rows = (await session.execute(select(Wishlist).where(Wishlist.variant_id == variant_id))).scalars().all()
    for row in rows:
        if lowest is not None and lowest != row.current_lowest_price:
            if row.current_lowest_price is not None:
                row.previous_lowest_price = row.current_lowest_price
            row.current_lowest_price = lowest
        row.last_checked_at = now


async def run_search(
    session: AsyncSession, *, user_id: int, registry: ConnectorRegistry, llm: StructuredLLM | None,
    query: ProductQuery, raw_text: str, search_type: str, interpreter: str, notes: list[str],
    marketplaces: list[Marketplace] | None = None, quantity: int = 1, include_similar: bool = True,
    seed_offers: list[RawOffer] | None = None, extra_sources: list[SourceResult] | None = None,
) -> SearchResponse:
    settings = get_settings()
    keywords = (query.search_keywords or " ".join(
        p for p in [query.brand, query.product_name, query.model, query.volume, query.weight] if p)).strip()
    sources, offers = ([], [])
    if keywords:
        sources, offers = await fetch_offers(registry, keywords, marketplaces)
    sources = (extra_sources or []) + sources
    seen = {(o.marketplace, o.external_id) for o in offers}
    offers += [o for o in (seed_offers or []) if (o.marketplace, o.external_id) not in seen]

    q_attrs = query_to_attributes(query)
    candidates: list[Candidate] = []
    for raw in offers:
        attrs = normalize_title(raw.title, raw.variant_label, raw.gtin)
        candidates.append(Candidate(raw, attrs, match_query(q_attrs, attrs, fold(raw.title))))

    # Stage B: AI refinement of ambiguous matches only.
    ambiguous = [c for c in candidates if c.match.match_type == MatchType.LIKELY]
    if llm is not None and settings.ai_matching_enabled and ambiguous:
        judged = await match_judge.judge(llm, raw_text, [(c.raw.title, c.match) for c in ambiguous])
        for cand, result in zip(ambiguous, judged, strict=True):
            cand.match = result

    excluded = sum(1 for c in candidates if c.match.match_type == MatchType.DIFFERENT)
    kept = [c for c in candidates if c.match.match_type != MatchType.DIFFERENT]
    if not include_similar:
        kept = [c for c in kept if c.match.match_type != MatchType.SIMILAR]

    clusters = cluster(kept)
    # A vaguely described listing that could be one of several better-described variants found
    # in the same search is not a confirmed match: it is shown as "likely", never as the best.
    for members in clusters:
        if all(c.match.match_type == MatchType.EXACT for c in members):
            missing: list[str] = []
            for other in clusters:
                if other is not members and other[0].match.match_type in (MatchType.EXACT, MatchType.LIKELY):
                    missing = is_vaguer_description(members[0].attrs, other[0].attrs)
                    if missing:
                        break
            if missing:
                for cand in members:
                    cand.match = cand.match.model_copy(update={
                        "match_type": MatchType.LIKELY, "confidence": 0.6,
                        "unknown_attributes": sorted(set(cand.match.unknown_attributes) | set(missing)),
                        "reason": "Tin đăng mô tả chung chung, không ghi rõ: "
                                  + ", ".join(label_of(f) for f in missing) + "."})

    groups: list[ProductGroup] = []
    over_budget_count = 0
    wish_variants = set((await session.execute(
        select(Wishlist.variant_id).where(Wishlist.user_id == user_id))).scalars().all())
    for members in clusters:
        rep = members[0]
        variant = await catalog.find_or_create_variant(
            session, rep.attrs, rep.raw.title, rep.raw.image_url, keywords or rep.raw.title)
        outs: list[OfferOut] = []
        for cand in members:
            unit_price = compute_price(cand.raw, 1)
            row = await catalog.upsert_offer(session, variant.id, cand.raw, unit_price, cand.match)
            price = unit_price if quantity == 1 else compute_price(cand.raw, quantity)
            source_code = cand.raw.marketplace.value
            seller = Seller(name=cand.raw.seller.name, rating=cand.raw.seller.rating,
                            is_official_store=cand.raw.seller.is_official_store,
                            location=cand.raw.seller.location)
            out = catalog.offer_to_out(row, source_code, seller, price=price, match=cand.match,
                                       over_budget=_out_of_budget(price, query))
            over_budget_count += out.over_budget
            outs.append(out)
        outs = _sort_offers(outs)
        best = catalog.pick_best(outs)
        # The most conservative member decides how the group is labelled.
        group_match = max((c.match for c in members), key=lambda m: _MATCH_ORDER[m.match_type])
        await refresh_wishlist_prices(session, variant.id, best.price.final_price if best else None)
        groups.append(ProductGroup(
            product_id=variant.product_id, variant_id=variant.id, title=variant.title,
            image_url=variant.image_url or next((o.image_url for o in outs if o.image_url), None),
            attributes=rep.attrs, match=group_match, offers=outs,
            best_offer_id=best.id if best else None,
            lowest_final_price=best.price.final_price if best else None,
            lowest_price_confidence=best.price.confidence if best else None,
            offer_count=len(outs), in_wishlist=variant.id in wish_variants))

    in_budget = [g for g in groups if not all(o.over_budget for o in g.offers)]
    main = sorted((g for g in in_budget if g.match.match_type in (MatchType.EXACT, MatchType.LIKELY)),
                  key=_group_sort_key)
    similar = sorted((g for g in in_budget if g.match.match_type == MatchType.SIMILAR), key=_group_sort_key)
    best, clarification = _best_banner(main, query)
    if not main and not similar and not clarification.needed:
        if any(s.status == "ok" for s in sources):
            clarification = Clarification(
                needed=True, message="Không tìm thấy tin đăng khớp với yêu cầu. Thử thêm thương hiệu, "
                                     "dung tích/size hoặc bỏ bớt điều kiện giá.")
    answered = [s for s in sources if s.status == "ok"]
    partial = bool(sources) and len(answered) < len(sources)

    search = Search(user_id=user_id, search_type=search_type, query=raw_text[:2000],
                    spec=query.model_dump(mode="json"), result_count=len(main),
                    lowest_price=best.final_price if best else None)
    session.add(search)
    await session.commit()
    return SearchResponse(
        search_id=search.id, search_type=search_type,
        query=InterpretedQuery(raw=raw_text, interpreter=interpreter, spec=query, notes=notes),
        demo_mode=registry.demo_mode, partial=partial, sources=sources, best=best, groups=main,
        similar_groups=similar, excluded_count=excluded, over_budget_count=over_budget_count,
        clarification=clarification, generated_at=datetime.now(UTC))


async def refresh_variant(session: AsyncSession, registry: ConnectorRegistry, variant_id: int) -> OfferOut | None:
    """Re-query marketplaces for one stored variant (price monitor). Only listings that are
    deterministically the same variant are recorded. Returns the current best offer."""
    variant = await catalog.load_variant(session, variant_id)
    if variant is None:
        return None
    reference = ProductAttributes(**variant.attributes)
    _, offers = await fetch_offers(registry, variant.search_query, None)
    for raw in offers:
        attrs = normalize_title(raw.title, raw.variant_label, raw.gtin)
        if not same_variant(reference, attrs):
            continue
        match = MatchResult(match_type=MatchType.EXACT, confidence=0.9,
                            reason="Trùng biến thể đã lưu (kiểm tra giá định kỳ).")
        await catalog.upsert_offer(session, variant.id, raw, compute_price(raw, 1), match)
    await session.flush()
    best = catalog.pick_best(await catalog.variant_offers(session, variant.id))
    await refresh_wishlist_prices(session, variant.id, best.price.final_price if best else None)
    return best

