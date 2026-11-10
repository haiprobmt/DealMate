"""Wishlist, price alerts, scheduled price checks and notifications (incl. scenario 5)."""
import copy

import pytest

from app.connectors import mock
from app.core.config import get_settings
from app.services.push import FcmSender
from workers import notification_worker, price_monitor

QUERY = "Tã quần Merries size L 44 miếng"
SHOPEE_ID = "MOCK-SH-010"      # 349.000 − 15.000 voucher + 0 ship = 334.000 (verified)


@pytest.fixture
def catalog():
    """Mutable copy of the mock catalogue so a test can simulate marketplace price changes."""
    original = mock._load_catalog()
    backup = copy.deepcopy(original)
    yield {row["external_id"]: row for row in original}
    original[:] = backup


async def find_variant(client, query=QUERY):
    body = (await client.post("/api/v1/search", json={"query": query})).json()
    return body["groups"][0]


async def check_prices(session, registry):
    stats = await price_monitor.run_once(session, registry)
    await notification_worker.run_once(session, FcmSender(get_settings()))
    return stats


async def test_scenario5_save_product_with_target_price(client):
    group = await find_variant(client)
    r = await client.post("/api/v1/wishlist", json={"variant_id": group["variant_id"], "target_price": 320_000})
    assert r.status_code == 201, r.text
    item = r.json()
    assert item["title"] == "Merries tã quần size L 44 miếng" and item["target_price"] == 320_000
    assert (item["current_lowest_price"], item["current_marketplace"]) == (334_000, "shopee")
    assert item["current_price_confidence"] == "verified" and item["is_mock"] is True

    wishlist = (await client.get("/api/v1/wishlist")).json()["items"]
    assert [w["variant_id"] for w in wishlist] == [group["variant_id"]]
    alerts = (await client.get("/api/v1/price-alerts")).json()["items"]
    assert len(alerts) == 1 and alerts[0]["id"] == item["alert_id"]
    assert alerts[0]["target_price"] == 320_000 and alerts[0]["is_active"]

    detail = (await client.get(f"/api/v1/products/{group['product_id']}/offers",
                               params={"variant_id": group["variant_id"]})).json()
    assert detail["in_wishlist"] and detail["wishlist_id"] == item["id"] and detail["target_price"] == 320_000
    again = (await client.post("/api/v1/search", json={"query": QUERY})).json()
    assert again["groups"][0]["in_wishlist"] is True


async def test_wishlist_is_idempotent_and_removable(client):
    group = await find_variant(client)
    first = (await client.post("/api/v1/wishlist", json={"variant_id": group["variant_id"]})).json()
    second = (await client.post("/api/v1/wishlist", json={"variant_id": group["variant_id"], "target_price": 300_000})).json()
    assert first["id"] == second["id"] and first["target_price"] is None and second["target_price"] == 300_000
    assert (await client.delete(f"/api/v1/wishlist/{first['id']}")).status_code == 204
    assert (await client.get("/api/v1/wishlist")).json()["items"] == []
    assert (await client.get("/api/v1/price-alerts")).json()["items"] == [], "the target goes with the item"
    assert (await client.delete(f"/api/v1/wishlist/{first['id']}")).status_code == 404


async def test_wishlist_and_alert_validation(client):
    assert (await client.post("/api/v1/wishlist", json={"variant_id": 999_999})).status_code == 404
    group = await find_variant(client)
    for bad in (0, -5):
        r = await client.post("/api/v1/price-alerts", json={"variant_id": group["variant_id"], "target_price": bad})
        assert r.status_code == 422
    assert (await client.delete("/api/v1/price-alerts/424242")).status_code == 404


async def test_alert_crud_and_isolation_between_users(client, session):
    from app.core.security import create_user
    group = await find_variant(client)
    alert = (await client.post("/api/v1/price-alerts", json={"variant_id": group["variant_id"], "target_price": 300_000})).json()
    updated = (await client.post("/api/v1/price-alerts", json={"variant_id": group["variant_id"], "target_price": 310_000})).json()
    assert updated["id"] == alert["id"] and updated["target_price"] == 310_000
    _, other_token = await create_user(session, "Người khác", "other-token-0123456789")
    other = {"Authorization": f"Bearer {other_token}"}
    assert (await client.get("/api/v1/price-alerts", headers=other)).json()["items"] == []
    assert (await client.delete(f"/api/v1/price-alerts/{alert['id']}", headers=other)).status_code == 404
    assert (await client.delete(f"/api/v1/price-alerts/{alert['id']}")).status_code == 204


async def test_price_check_records_history_and_notifies_once(client, session, registry, catalog):
    group = await find_variant(client)
    await client.post("/api/v1/wishlist", json={"variant_id": group["variant_id"], "target_price": 320_000})

    stats = await check_prices(session, registry)
    assert stats == {"variants": 1, "notifications": 0, "errors": 0}, "334.000 is above the 320.000 target"
    assert (await client.get("/api/v1/notifications")).json()["items"] == []

    catalog[SHOPEE_ID]["listed_price"] = 330_000          # -> 315.000 final
    assert (await check_prices(session, registry))["notifications"] == 1
    notes = (await client.get("/api/v1/notifications")).json()["items"]
    assert len(notes) == 1
    note = notes[0]
    assert note["price"] == 315_000 and note["status"] == "in_app" and note["is_mock"]
    assert "315.000đ" in note["body"] and "320.000đ" in note["body"] and "Shopee" in note["body"]
    assert note["title"].startswith("[DEMO]") and note["url"].endswith(SHOPEE_ID), "links to the original listing"

    assert (await check_prices(session, registry))["notifications"] == 0, "same price -> no duplicate"
    catalog[SHOPEE_ID]["listed_price"] = 320_000          # -> 305.000, a further drop
    assert (await check_prices(session, registry))["notifications"] == 1
    catalog[SHOPEE_ID]["listed_price"] = 349_000          # back above target: re-arms silently
    assert (await check_prices(session, registry))["notifications"] == 0
    assert len((await client.get("/api/v1/notifications")).json()["items"]) == 2

    item = (await client.get("/api/v1/wishlist")).json()["items"][0]
    assert item["current_lowest_price"] == 334_000 and item["previous_lowest_price"] == 305_000
    assert item["last_checked_at"]

    detail = (await client.get(f"/api/v1/products/{group['product_id']}/offers",
                               params={"variant_id": group["variant_id"]})).json()
    shopee = next(o for o in detail["offers"] if o["marketplace"] == "shopee")
    assert shopee["price"]["final_price"] == 334_000, "offers show the current price, history is separate"
    assert detail["price_history"] and all(p["is_mock"] for p in detail["price_history"])


async def test_price_check_only_records_the_same_variant(client, session, registry):
    group = await find_variant(client)
    await client.post("/api/v1/wishlist", json={"variant_id": group["variant_id"]})
    await check_prices(session, registry)
    detail = (await client.get(f"/api/v1/products/{group['product_id']}/offers",
                               params={"variant_id": group["variant_id"]})).json()
    assert {o["title"] for o in detail["offers"]} == {
        "[MẪU] Tã quần Merries size L 44 miếng", "[MẪU] Bỉm quần Merries L44"}


async def test_price_check_survives_marketplace_failure(client, session, registry):
    from app.connectors.mock import MockFailure
    group = await find_variant(client)
    await client.post("/api/v1/wishlist", json={"variant_id": group["variant_id"]})
    for connector in registry.all():
        connector.fail_with = MockFailure("down")
    assert (await price_monitor.run_once(session, registry))["errors"] == 0


async def test_notifications_read_flow(client, session, registry, catalog):
    group = await find_variant(client)
    await client.post("/api/v1/wishlist", json={"variant_id": group["variant_id"], "target_price": 340_000})
    await check_prices(session, registry)
    unread = (await client.get("/api/v1/notifications", params={"unread_only": "true"})).json()["items"]
    assert len(unread) == 1 and unread[0]["read_at"] is None
    assert (await client.post(f"/api/v1/notifications/{unread[0]['id']}/read")).status_code == 204
    assert (await client.get("/api/v1/notifications", params={"unread_only": "true"})).json()["items"] == []
    assert (await client.post("/api/v1/notifications/999999/read")).status_code == 404


async def test_push_is_sent_when_fcm_is_configured(client, session, registry, catalog):
    class FakeSender:
        configured = True
        sent: list = []

        async def send(self, token, title, body, data):
            self.sent.append((token, title, data))
            return "invalid_token" if token.startswith("dead") else "sent"

    group = await find_variant(client)
    await client.post("/api/v1/wishlist", json={"variant_id": group["variant_id"], "target_price": 340_000})
    for token in ("live-device-token-000000000", "dead-device-token-000000000"):
        assert (await client.post("/api/v1/devices", json={"token": token})).status_code == 204
    await price_monitor.run_once(session, registry)
    sender = FakeSender()
    stats = await notification_worker.run_once(session, sender)
    assert stats["pushed"] == 1 and len(sender.sent) == 2
    assert sender.sent[0][2]["variant_id"] == str(group["variant_id"])
    note = (await client.get("/api/v1/notifications")).json()["items"][0]
    assert note["status"] == "pushed"
    from sqlalchemy import select

    from app.models.tables import DeviceToken
    assert (await session.execute(select(DeviceToken.token))).scalars().all() == ["live-device-token-000000000"]
