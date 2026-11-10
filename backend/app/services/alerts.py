"""Đánh giá cảnh báo giá và tạo thông báo (không gửi trùng)."""
from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.tables import Notification, PriceAlert, ProductVariant
from app.schemas.api import OfferOut
from app.utils.text import format_vnd


async def evaluate_alerts(session: AsyncSession, variant_id: int, best: OfferOut | None) -> int:
    """Create notifications for alerts whose target is met by the current best offer.

    Dedupe rules:
    * one notification per (alert, offer, price, day) — enforced by a unique key;
    * after notifying at price P, notify again only for a price strictly below P;
    * when the price rises above the target the alert re-arms.
    """
    alerts = (await session.execute(
        select(PriceAlert).where(PriceAlert.variant_id == variant_id, PriceAlert.is_active))).scalars().all()
    if not alerts:
        return 0
    variant = await session.get(ProductVariant, variant_id)
    created = 0
    for alert in alerts:
        price = best.price.final_price if best else None
        if price is None or price > alert.target_price:
            alert.last_notified_price = None      # re-arm
            continue
        if alert.last_notified_price is not None and price >= alert.last_notified_price:
            continue
        estimated = " (giá ước tính, cần kiểm tra lại)" if best.price.confidence.value != "verified" else ""
        prefix = "[DEMO] " if best.is_mock else ""
        body = (f"{prefix}{variant.title} đang có giá {format_vnd(price)} tại {best.marketplace_name}"
                f"{estimated}, thấp hơn mức mong muốn {format_vnd(alert.target_price)}. "
                f"Giá ghi nhận lúc {best.retrieved_at:%H:%M %d/%m} (UTC).")
        result = await session.execute(
            pg_insert(Notification).values(
                user_id=alert.user_id, alert_id=alert.id, offer_id=best.id, variant_id=variant_id,
                title=f"{prefix}DealMate: giá đã giảm", body=body, url=best.url, price=price,
                is_mock=best.is_mock, status="pending",
                dedupe_key=f"alert:{alert.id}:offer:{best.id}:price:{price}:{datetime.now(UTC):%Y%m%d}")
            .on_conflict_do_nothing(index_elements=["dedupe_key"]).returning(Notification.id))
        if result.scalar_one_or_none() is not None:
            created += 1
        alert.last_notified_price = price
        alert.last_notified_at = datetime.now(UTC)
    return created
