from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.errors import NotFoundError
from app.models.tables import PriceAlert, ProductVariant, ProductView, Wishlist
from app.repositories import catalog
from app.schemas.api import ProductOffersResponse
from app.schemas.domain import ProductAttributes


async def get_variant_or_404(session: AsyncSession, variant_id: int) -> ProductVariant:
    variant = await catalog.load_variant(session, variant_id)
    if variant is None:
        raise NotFoundError("Không tìm thấy sản phẩm.")
    return variant


async def build_offers_response(session: AsyncSession, user_id: int, variant: ProductVariant,
                                demo_mode: bool) -> ProductOffersResponse:
    offers = await catalog.variant_offers(session, variant.id)
    best = catalog.pick_best(offers)
    wish = (await session.execute(select(Wishlist).where(
        Wishlist.user_id == user_id, Wishlist.variant_id == variant.id))).scalar_one_or_none()
    alert = (await session.execute(select(PriceAlert).where(
        PriceAlert.user_id == user_id, PriceAlert.variant_id == variant.id,
        PriceAlert.is_active))).scalar_one_or_none()
    return ProductOffersResponse(
        product_id=variant.product_id, variant_id=variant.id, title=variant.title,
        image_url=variant.image_url, attributes=ProductAttributes(**variant.attributes),
        demo_mode=demo_mode, offers=offers, best_offer_id=best.id if best else None,
        price_history=await catalog.price_timeline(session, variant.id),
        in_wishlist=wish is not None, wishlist_id=wish.id if wish else None,
        target_price=alert.target_price if alert else None)


async def record_view(session: AsyncSession, user_id: int, variant_id: int) -> None:
    from datetime import UTC, datetime
    await session.execute(
        pg_insert(ProductView).values(user_id=user_id, variant_id=variant_id)
        .on_conflict_do_update(constraint="uq_views_user_variant",
                               set_={"viewed_at": datetime.now(UTC)}))
    await session.commit()
