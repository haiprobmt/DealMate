"""Gửi các thông báo đang chờ (status = pending).

    python -m workers.notification_worker --once
    python -m workers.notification_worker          # loop every 60 s

With FCM configured each pending notification is pushed to the user's devices
(status -> pushed / failed). Without FCM it is marked ``in_app``: it is still delivered,
because the Android app polls GET /api/v1/notifications.
"""
import argparse
import asyncio
from datetime import UTC, datetime

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.db import dispose_engine, get_sessionmaker
from app.core.logging import configure_logging, get_logger
from app.models.tables import DeviceToken, Notification
from app.services.push import FcmSender

log = get_logger("dealmate.notification_worker")


async def run_once(session: AsyncSession, sender: FcmSender) -> dict:
    pending = (await session.execute(
        select(Notification).where(Notification.status == "pending")
        .order_by(Notification.created_at).limit(200).with_for_update(skip_locked=True))).scalars().all()
    stats = {"pending": len(pending), "pushed": 0, "in_app": 0, "failed": 0}
    for note in pending:
        tokens = (await session.execute(
            select(DeviceToken.token).where(DeviceToken.user_id == note.user_id))).scalars().all()
        if not sender.configured or not tokens:
            note.status = "in_app"
            stats["in_app"] += 1
            continue
        results = []
        for token in tokens:
            try:
                outcome = await sender.send(token, note.title, note.body, {
                    "notification_id": str(note.id), "variant_id": str(note.variant_id or ""),
                    "url": note.url or "", "is_mock": "true" if note.is_mock else "false"})
            except Exception:
                log.exception("push_error", extra={"notification_id": note.id})
                outcome = "failed"
            if outcome == "invalid_token":
                await session.execute(delete(DeviceToken).where(DeviceToken.token == token))
            results.append(outcome)
        if "sent" in results:
            note.status, note.sent_at = "pushed", datetime.now(UTC)
            stats["pushed"] += 1
        else:
            note.status = "failed"     # still visible in-app through polling
            stats["failed"] += 1
    await session.commit()
    if pending:
        log.info("notifications_processed", extra=stats)
    return stats


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--once", action="store_true")
    args = parser.parse_args()
    settings = get_settings()
    configure_logging(settings.log_level)
    sender = FcmSender(settings)
    try:
        while True:
            async with get_sessionmaker()() as session:
                await run_once(session, sender)
            if args.once:
                break
            await asyncio.sleep(60)
    finally:
        await dispose_engine()


if __name__ == "__main__":
    asyncio.run(main())
