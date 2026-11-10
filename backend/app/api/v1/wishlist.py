from fastapi import APIRouter, Depends, Response
from sqlalchemy import delete, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.db import get_session
from app.core.errors import NotFoundError
from app.core.security import current_user
from app.models.tables import PriceAlert, ProductVariant, User, Wishlist
from app.repositories import catalog
from app.schemas.api import PriceAlertCreate, PriceAlertOut, WishlistCreate, WishlistItem
from app.services.products import get_variant_or_404

router = APIRouter(tags=["wishlist"])


async def _upsert_alert(session: AsyncSession, user_id: int, variant: ProductVariant,
                        target_price: int) -> PriceAlert:
    return (await session.execute(
        pg_insert(PriceAlert)
        .values(user_id=user_id, product_id=variant.product_id, variant_id=variant.id,
                target_price=target_price, is_active=True)
        .on_conflict_do_update(constraint="uq_alert_user_variant",
                               set_={"target_price": target_price, "is_active": True,
                                     "last_notified_price": None})
        .returning(PriceAlert), execution_options={"populate_existing": True})).scalar_one()


async def _wishlist_item(session: AsyncSession, row: Wishlist, variant: ProductVariant,
                         alert: PriceAlert | None) -> WishlistItem:
    offers = await catalog.variant_offers(session, variant.id)
    best = catalog.pick_best(offers)
    return WishlistItem(
        id=row.id, product_id=row.product_id, variant_id=row.variant_id, title=variant.title,
        image_url=variant.image_url,
        current_lowest_price=best.price.final_price if best else None,
        current_price_confidence=best.price.confidence if best else None,
        current_marketplace=best.marketplace if best else None,
        current_offer_url=best.url if best else None,
        previous_lowest_price=row.previous_lowest_price,
        last_checked_at=row.last_checked_at or (max(o.retrieved_at for o in offers) if offers else None),
        target_price=alert.target_price if alert and alert.is_active else None,
        alert_id=alert.id if alert and alert.is_active else None,
        is_mock=any(o.is_mock for o in offers), created_at=row.created_at)


@router.post("/wishlist", response_model=WishlistItem, status_code=201)
async def add_to_wishlist(body: WishlistCreate, user: User = Depends(current_user),
                          session: AsyncSession = Depends(get_session)) -> WishlistItem:
    variant = await get_variant_or_404(session, body.variant_id)
    best = catalog.pick_best(await catalog.variant_offers(session, variant.id))
    row = (await session.execute(
        pg_insert(Wishlist)
        .values(user_id=user.id, product_id=variant.product_id, variant_id=variant.id,
                current_lowest_price=best.price.final_price if best else None)
        .on_conflict_do_update(constraint="uq_wishlist_user_variant",
                               set_={"product_id": variant.product_id})
        .returning(Wishlist), execution_options={"populate_existing": True})).scalar_one()
    alert = None
    if body.target_price is not None:
        alert = await _upsert_alert(session, user.id, variant, body.target_price)
    else:
        alert = (await session.execute(select(PriceAlert).where(
            PriceAlert.user_id == user.id, PriceAlert.variant_id == variant.id))).scalar_one_or_none()
    item = await _wishlist_item(session, row, variant, alert)
    await session.commit()
    return item


@router.get("/wishlist")
async def list_wishlist(user: User = Depends(current_user),
                        session: AsyncSession = Depends(get_session)) -> dict[str, list[WishlistItem]]:
    rows = (await session.execute(
        select(Wishlist, ProductVariant, PriceAlert)
        .join(ProductVariant, Wishlist.variant_id == ProductVariant.id)
        .outerjoin(PriceAlert, (PriceAlert.user_id == Wishlist.user_id)
                   & (PriceAlert.variant_id == Wishlist.variant_id))
        .where(Wishlist.user_id == user.id).order_by(Wishlist.created_at.desc()))).all()
    return {"items": [await _wishlist_item(session, w, v, a) for w, v, a in rows]}


@router.delete("/wishlist/{wishlist_id}", status_code=204)
async def remove_from_wishlist(wishlist_id: int, user: User = Depends(current_user),
                               session: AsyncSession = Depends(get_session)) -> Response:
    row = (await session.execute(select(Wishlist).where(
        Wishlist.id == wishlist_id, Wishlist.user_id == user.id))).scalar_one_or_none()
    if row is None:
        raise NotFoundError("Không tìm thấy mục yêu thích.")
    # The target price belongs to the saved item: removing the item removes its alert.
    await session.execute(delete(PriceAlert).where(
        PriceAlert.user_id == user.id, PriceAlert.variant_id == row.variant_id))
    await session.delete(row)
    await session.commit()
    return Response(status_code=204)


def _alert_out(alert: PriceAlert, variant: ProductVariant) -> PriceAlertOut:
    return PriceAlertOut(id=alert.id, product_id=alert.product_id, variant_id=alert.variant_id,
                         title=variant.title, target_price=alert.target_price, is_active=alert.is_active,
                         last_notified_price=alert.last_notified_price,
                         last_notified_at=alert.last_notified_at, created_at=alert.created_at)


@router.post("/price-alerts", response_model=PriceAlertOut, status_code=201)
async def create_alert(body: PriceAlertCreate, user: User = Depends(current_user),
                       session: AsyncSession = Depends(get_session)) -> PriceAlertOut:
    variant = await get_variant_or_404(session, body.variant_id)
    alert = await _upsert_alert(session, user.id, variant, body.target_price)
    out = _alert_out(alert, variant)
    await session.commit()
    return out


@router.get("/price-alerts")
async def list_alerts(user: User = Depends(current_user),
                      session: AsyncSession = Depends(get_session)) -> dict[str, list[PriceAlertOut]]:
    rows = (await session.execute(
        select(PriceAlert, ProductVariant).join(ProductVariant, PriceAlert.variant_id == ProductVariant.id)
        .where(PriceAlert.user_id == user.id).order_by(PriceAlert.created_at.desc()))).all()
    return {"items": [_alert_out(a, v) for a, v in rows]}


@router.delete("/price-alerts/{alert_id}", status_code=204)
async def delete_alert(alert_id: int, user: User = Depends(current_user),
                       session: AsyncSession = Depends(get_session)) -> Response:
    result = await session.execute(delete(PriceAlert).where(
        PriceAlert.id == alert_id, PriceAlert.user_id == user.id))
    if result.rowcount == 0:
        raise NotFoundError("Không tìm thấy cảnh báo giá.")
    await session.commit()
    return Response(status_code=204)
