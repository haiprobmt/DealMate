"""Lưu trữ danh mục: sản phẩm chuẩn, biến thể, tin đăng, người bán, lịch sử giá."""
from datetime import UTC, datetime, timedelta

from sqlalchemy import select, text
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.config import get_settings
from app.models.tables import (
    MarketplaceOffer,
    MarketplaceSource,
    PriceHistory,
    Product,
    ProductVariant,
    Seller,
)
from app.schemas.api import OfferOut, PricePoint, SellerOut
from app.schemas.domain import (
    MARKETPLACE_NAMES,
    Marketplace,
    MatchResult,
    PriceBreakdown,
    PriceConfidence,
    ProductAttributes,
    RawOffer,
)
from app.services.matching import product_key, same_variant, variant_key
from app.services.normalization import display_title
from app.services.pricing import ranking_key

HISTORY_MIN_INTERVAL = timedelta(hours=1)


async def ensure_source(session: AsyncSession, marketplace: Marketplace, is_mock: bool) -> MarketplaceSource:
    stmt = (pg_insert(MarketplaceSource)
            .values(code=marketplace.value, name=MARKETPLACE_NAMES[marketplace], is_mock=is_mock)
            .on_conflict_do_update(constraint="uq_sources_code_mock",
                                   set_={"name": MARKETPLACE_NAMES[marketplace]})
            .returning(MarketplaceSource))
    return (await session.execute(stmt)).scalar_one()


async def upsert_seller(session: AsyncSession, source_id: int, raw: RawOffer) -> int:
    s = raw.seller
    stmt = (pg_insert(Seller)
            .values(source_id=source_id, external_id=s.external_id, name=s.name, rating=s.rating,
                    is_official_store=s.is_official_store, location=s.location)
            .on_conflict_do_update(constraint="uq_sellers_source_ext",
                                   set_={"name": s.name, "rating": s.rating,
                                         "is_official_store": s.is_official_store,
                                         "location": s.location, "updated_at": datetime.now(UTC)})
            .returning(Seller.id))
    return (await session.execute(stmt)).scalar_one()


async def find_or_create_variant(session: AsyncSession, attrs: ProductAttributes, fallback_title: str,
                                 image_url: str | None, search_query: str) -> ProductVariant:
    key = variant_key(attrs)[:500]
    variant = (await session.execute(
        select(ProductVariant).where(ProductVariant.variant_key == key))).scalar_one_or_none()
    if variant:
        return variant

    # Same purchasable item described with slightly different words -> reuse the stored variant.
    if attrs.brand:
        candidates = (await session.execute(
            select(ProductVariant).join(Product).where(
                Product.brand == attrs.brand, Product.category == attrs.category))).scalars().all()
        for candidate in candidates:
            if same_variant(ProductAttributes(**candidate.attributes), attrs):
                return candidate

    pkey = product_key(attrs)[:300]
    title = display_title(attrs, fallback_title)[:300]
    product_title = display_title(
        ProductAttributes(brand=attrs.brand, model=attrs.model, product_line=attrs.product_line),
        fallback_title)[:300]
    product_id = (await session.execute(
        pg_insert(Product)
        .values(product_key=pkey, title=product_title, brand=attrs.brand, category=attrs.category,
                model=attrs.model, image_url=image_url)
        .on_conflict_do_update(index_elements=["product_key"], set_={"product_key": pkey})
        .returning(Product.id))).scalar_one()
    # Upsert: two concurrent searches may discover the same new variant.
    return (await session.execute(
        pg_insert(ProductVariant)
        .values(product_id=product_id, variant_key=key, title=title,
                attributes=attrs.model_dump(mode="json"), gtin=attrs.gtin, image_url=image_url,
                search_query=search_query[:300])
        .on_conflict_do_update(index_elements=["variant_key"], set_={"variant_key": key})
        .returning(ProductVariant), execution_options={"populate_existing": True})).scalar_one()


async def upsert_offer(session: AsyncSession, variant_id: int, raw: RawOffer, price: PriceBreakdown,
                       match: MatchResult) -> MarketplaceOffer:
    source = await ensure_source(session, raw.marketplace, raw.is_mock)
    seller_id = await upsert_seller(session, source.id, raw)
    values = dict(
        variant_id=variant_id, source_id=source.id, seller_id=seller_id, external_id=raw.external_id,
        url=raw.url, title=raw.title, raw=raw.model_dump(mode="json"), price=price.model_dump(mode="json"),
        match=match.model_dump(mode="json"), listed_price=raw.listed_price, final_price=price.final_price,
        price_confidence=price.confidence.value, is_mock=raw.is_mock, retrieved_at=raw.retrieved_at)
    update = {k: v for k, v in values.items() if k not in ("source_id", "external_id")}
    offer = (await session.execute(
        pg_insert(MarketplaceOffer).values(**values)
        .on_conflict_do_update(constraint="uq_offers_source_ext", set_=update)
        .returning(MarketplaceOffer), execution_options={"populate_existing": True})).scalar_one()

    latest = (await session.execute(
        select(PriceHistory).where(PriceHistory.offer_id == offer.id)
        .order_by(PriceHistory.recorded_at.desc()).limit(1))).scalar_one_or_none()
    changed = (latest is None or latest.listed_price != raw.listed_price
               or latest.final_price != price.final_price
               or latest.price_confidence != price.confidence.value)
    if changed or raw.retrieved_at - latest.recorded_at >= HISTORY_MIN_INTERVAL:
        session.add(PriceHistory(offer_id=offer.id, listed_price=raw.listed_price,
                                 final_price=price.final_price, price_confidence=price.confidence.value,
                                 recorded_at=raw.retrieved_at))
    return offer


def offer_to_out(offer: MarketplaceOffer, source_code: str, seller: Seller, *,
                 price: PriceBreakdown | None = None, match: MatchResult | None = None,
                 over_budget: bool = False) -> OfferOut:
    raw = offer.raw
    marketplace = Marketplace(source_code)
    stale_after = timedelta(hours=get_settings().offer_freshness_hours)
    return OfferOut(
        id=offer.id, marketplace=marketplace, marketplace_name=MARKETPLACE_NAMES[marketplace],
        is_mock=offer.is_mock, title=offer.title, url=offer.url, image_url=raw.get("image_url"),
        seller=SellerOut(name=seller.name, rating=seller.rating,
                         is_official_store=seller.is_official_store, location=seller.location),
        price=price or PriceBreakdown(**offer.price), original_price=raw.get("original_price"),
        match=match or MatchResult(**offer.match), rating=raw.get("rating"),
        rating_count=raw.get("rating_count"), sold_count=raw.get("sold_count"),
        in_stock=raw.get("in_stock"), retrieved_at=offer.retrieved_at,
        is_stale=datetime.now(UTC) - offer.retrieved_at > stale_after, over_budget=over_budget)


async def load_variant(session: AsyncSession, variant_id: int) -> ProductVariant | None:
    return (await session.execute(
        select(ProductVariant).where(ProductVariant.id == variant_id)
        .options(selectinload(ProductVariant.product)))).scalar_one_or_none()


async def variant_offers(session: AsyncSession, variant_id: int) -> list[OfferOut]:
    rows = (await session.execute(
        select(MarketplaceOffer, MarketplaceSource.code, Seller)
        .join(MarketplaceSource, MarketplaceOffer.source_id == MarketplaceSource.id)
        .join(Seller, MarketplaceOffer.seller_id == Seller.id)
        .where(MarketplaceOffer.variant_id == variant_id))).all()
    offers = [offer_to_out(o, code, seller) for o, code, seller in rows]
    # Fresh offers first; inside each freshness class the deterministic price ranking applies.
    offers.sort(key=lambda o: (o.is_stale, *ranking_key(o.price, f"{o.marketplace.value}:{o.id}")))
    return offers


def pick_best(offers: list[OfferOut]) -> OfferOut | None:
    """Cheapest verified offer, else cheapest estimated. Stale/incomplete/over-budget never win."""
    eligible = [o for o in offers if not o.is_stale and not o.over_budget and o.price.final_price is not None]
    verified = [o for o in eligible if o.price.confidence == PriceConfidence.VERIFIED]
    pool = verified or eligible
    return min(pool, key=lambda o: ranking_key(o.price, f"{o.marketplace.value}:{o.id}")) if pool else None


async def price_timeline(session: AsyncSession, variant_id: int, limit: int = 90) -> list[PricePoint]:
    """Lowest complete final price per check hour across all offers of the variant."""
    rows = (await session.execute(text("""
        SELECT * FROM (
            SELECT DISTINCT ON (bucket) bucket, final_price, price_confidence, code, is_mock
            FROM (
                SELECT date_trunc('hour', h.recorded_at) AS bucket, h.final_price, h.price_confidence,
                       s.code, o.is_mock
                FROM price_history h
                JOIN marketplace_offers o ON o.id = h.offer_id
                JOIN marketplace_sources s ON s.id = o.source_id
                WHERE o.variant_id = :variant_id AND h.final_price IS NOT NULL
            ) t
            ORDER BY bucket, (price_confidence <> 'verified'), final_price
        ) latest ORDER BY bucket DESC LIMIT :limit"""), {"variant_id": variant_id, "limit": limit})).all()
    return [PricePoint(recorded_at=r.bucket, lowest_final_price=r.final_price,
                       confidence=PriceConfidence(r.price_confidence), marketplace=Marketplace(r.code),
                       is_mock=r.is_mock) for r in reversed(rows)]
