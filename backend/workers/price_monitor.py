"""Kiểm tra giá định kỳ cho sản phẩm đã lưu (wishlist / cảnh báo giá).

    python -m workers.price_monitor --once        # one pass (cron / tests)
    python -m workers.price_monitor               # loop every DEALMATE_PRICE_CHECK_INTERVAL_MINUTES

This is periodic polling of the configured sources — NOT real-time monitoring. A price can
change between two checks; every stored price carries the time it was retrieved.
"""
import argparse
import asyncio

from sqlalchemy import select, union
from sqlalchemy.ext.asyncio import AsyncSession

from app.connectors.registry import ConnectorRegistry, get_registry
from app.core.config import get_settings
from app.core.db import dispose_engine, get_sessionmaker
from app.core.logging import configure_logging, get_logger
from app.models.tables import PriceAlert, Wishlist
from app.services.alerts import evaluate_alerts
from app.services.search import refresh_variant

log = get_logger("dealmate.price_monitor")


async def run_once(session: AsyncSession, registry: ConnectorRegistry) -> dict:
    variant_ids = (await session.execute(union(
        select(Wishlist.variant_id),
        select(PriceAlert.variant_id).where(PriceAlert.is_active)))).scalars().all()
    stats = {"variants": len(variant_ids), "notifications": 0, "errors": 0}
    for variant_id in variant_ids:
        try:
            best = await refresh_variant(session, registry, variant_id)
            stats["notifications"] += await evaluate_alerts(session, variant_id, best)
            await session.commit()
        except Exception:
            await session.rollback()
            stats["errors"] += 1
            log.exception("variant_refresh_failed", extra={"variant_id": variant_id})
    log.info("price_check_done", extra=stats)
    return stats


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--once", action="store_true")
    args = parser.parse_args()
    settings = get_settings()
    configure_logging(settings.log_level)
    try:
        while True:
            async with get_sessionmaker()() as session:
                await run_once(session, get_registry())
            if args.once:
                break
            await asyncio.sleep(settings.price_check_interval_minutes * 60)
    finally:
        await dispose_engine()


if __name__ == "__main__":
    asyncio.run(main())
