import subprocess
import sys

from app.ai.assistant import AssistantAnswer
from app.core.config import get_settings
from tests.conftest import BACKEND_DIR


async def test_health_needs_no_auth(client):
    r = await client.get("/health", headers={"Authorization": ""})
    assert r.status_code == 200 and r.json() == {"status": "ok", "demo_mode": True, "version": "0.1.0"}


async def test_every_api_route_requires_auth(client):
    from app.main import app
    for route in app.routes:
        if getattr(route, "path", "").startswith("/api/"):
            method = sorted(route.methods - {"HEAD", "OPTIONS"})[0]
            path = route.path.replace("{product_id}", "1").replace("{wishlist_id}", "1") \
                .replace("{alert_id}", "1").replace("{notification_id}", "1")
            r = await client.request(method, path, headers={"Authorization": "Bearer nope"})
            assert r.status_code == 401, (method, path, r.status_code)


async def test_me_and_request_id_echo(client):
    r = await client.get("/api/v1/me", headers={"X-Request-ID": "abc12345-client-id"})
    assert r.json()["display_name"] == "Test" and r.json()["demo_mode"] is True
    assert r.headers["x-request-id"] == "abc12345-client-id"


async def test_unknown_route_uses_error_envelope(client):
    r = await client.get("/api/v1/nope")
    assert r.status_code == 404 and r.json()["error"]["code"] == "not_found"
    assert (await client.get("/api/v1/products/987654")).json()["error"]["code"] == "not_found"


async def test_history_records_and_clears(client):
    body = (await client.post("/api/v1/search", json={"query": "Tã quần Merries size L 44 miếng"})).json()
    await client.post("/api/v1/search", json={"query": "Anessa 60ml"})
    group = body["groups"][0]
    await client.get(f"/api/v1/products/{group['product_id']}/offers", params={"variant_id": group["variant_id"]})
    history = (await client.get("/api/v1/history")).json()
    assert [s["query"] for s in history["searches"]] == ["Anessa 60ml", "Tã quần Merries size L 44 miếng"]
    assert history["searches"][1]["lowest_price"] == 334_000 and history["searches"][1]["created_at"]
    assert [v["variant_id"] for v in history["viewed_products"]] == [group["variant_id"]]
    assert history["viewed_products"][0]["lowest_final_price"] == 334_000
    assert (await client.delete("/api/v1/history")).status_code == 204
    assert (await client.get("/api/v1/history")).json() == {"searches": [], "viewed_products": []}


async def test_product_detail_lists_variants(client):
    body = (await client.post("/api/v1/search", json={"query": "Anessa 60ml"})).json()
    group = body["groups"][0]
    detail = (await client.get(f"/api/v1/products/{group['product_id']}")).json()
    assert detail["brand"] == "anessa" and group["variant_id"] in [v["variant_id"] for v in detail["variants"]]
    r = await client.get(f"/api/v1/products/{group['product_id']}/offers", params={"variant_id": 999_999})
    assert r.status_code == 404


async def test_sources_status_in_demo_mode(client):
    body = (await client.get("/api/v1/sources/status")).json()
    assert body["demo_mode"] is True and body["ai_available"] is False and body["push_configured"] is False
    assert len(body["sources"]) == 3
    for source in body["sources"]:
        assert source["is_mock"] and source["connector"] == "mock" and source["status"] == "available"
        assert source["verified_live"] is False, "mock data is never reported as a verified live integration"


async def test_sources_status_live_mode_reports_missing_integrations(client):
    from app.connectors.registry import build_registry, set_registry
    from app.core.config import Settings
    set_registry(build_registry(Settings(mock_mode=False)))
    body = (await client.get("/api/v1/sources/status")).json()
    assert body["demo_mode"] is False
    assert {s["marketplace"]: s["status"] for s in body["sources"]} == {
        "shopee": "not_configured", "lazada": "not_implemented", "tiktok_shop": "not_implemented"}
    assert not any(s["verified_live"] or s["is_mock"] for s in body["sources"])
    search = (await client.post("/api/v1/search", json={"query": "Anessa 60ml"})).json()
    assert search["groups"] == [] and search["best"] is None and search["demo_mode"] is False
    assert {s["status"] for s in search["sources"]} == {"not_configured", "not_implemented"}


async def test_explore_never_invents_content(client):
    empty = (await client.get("/api/v1/explore")).json()
    assert empty["available"] is False and empty["price_drops"] == [] and empty["recently_seen"] == []
    assert "Chưa có dữ liệu" in empty["reason"]
    await client.post("/api/v1/search", json={"query": "Tã quần Merries size L 44 miếng"})
    seen = (await client.get("/api/v1/explore", params={"category": "diaper"})).json()
    assert seen["available"] and seen["recently_seen"] and seen["price_drops"] == []
    assert (await client.get("/api/v1/explore", params={"category": "phone"})).json()["available"] is False
    only = (await client.get("/api/v1/explore", params={"marketplace": "lazada"})).json()
    assert only["recently_seen"]


async def ask(client, question, variant_ids):
    r = await client.post("/api/v1/assistant/ask", json={"question": question, "variant_ids": variant_ids})
    assert r.status_code == 200, r.text
    return r.json()


async def test_assistant_rule_based_answers_use_only_retrieved_data(client):
    body = (await client.post("/api/v1/search", json={"query": "kem chống nắng Anessa Perfect UV Skincare Milk 60ml"})).json()
    vid = body["groups"][0]["variant_id"]
    answer = await ask(client, "Trong mấy shop này mua shop nào rẻ nhất?", [vid])
    assert answer["generated_by"] == "rules"
    assert "Lazada" in answer["answer"] and "319.000đ" in answer["answer"] and "dữ liệu mẫu" in answer["answer"]
    assert "có thể rẻ hơn" in answer["answer"] and "trang thanh toán" in answer["answer"]
    assert any("319.000đ" in fact for fact in answer["facts"]) and "chính hãng" in answer["disclaimer"]
    assert "Chưa có đủ lịch sử giá" in (await ask(client, "Đây có phải giá tốt không?", [vid]))["answer"] \
        or "chưa có đủ lịch sử giá" in (await ask(client, "Đây có phải giá tốt không?", [vid]))["answer"]
    why = await ask(client, "Vì sao shop kia đắt hơn?", [vid])
    assert "niêm yết" in why["answer"] and "voucher" in why["answer"]
    same = await ask(client, "Có đúng cùng sản phẩm không?", [vid])
    assert "chính hãng" in same["answer"]
    assert (await client.post("/api/v1/assistant/ask", json={"question": "?", "variant_ids": [vid]})).status_code == 422
    assert (await client.post("/api/v1/assistant/ask", json={"question": "rẻ nhất?", "variant_ids": [99999]})).status_code == 404


async def test_assistant_ai_gets_facts_and_falls_back_on_failure(client, fake_llm):
    body = (await client.post("/api/v1/search", json={"query": "Tã quần Merries size L 44 miếng"})).json()
    vid = body["groups"][0]["variant_id"]
    fake_llm.responses[AssistantAnswer] = AssistantAnswer(answer="Shopee đang rẻ nhất (dữ liệu mẫu).")
    answer = await ask(client, "Shop nào rẻ nhất?", [vid])
    assert answer["generated_by"] == "ai" and answer["answer"].startswith("Shopee")
    prompt = fake_llm.calls[-1][2]
    assert "<facts>" in prompt and "334.000đ" in prompt and "DỮ LIỆU MẪU" in prompt
    del fake_llm.responses[AssistantAnswer]
    assert (await ask(client, "Shop nào rẻ nhất?", [vid]))["generated_by"] == "rules"


async def test_rate_limit(client, monkeypatch):
    monkeypatch.setattr(get_settings(), "rate_limit_per_minute", 3)
    codes = [(await client.get("/api/v1/me", headers={"Authorization": "Bearer rate-test"})).status_code for _ in range(5)]
    assert codes[:3] == [401, 401, 401] and codes[3:] == [429, 429]
    r = await client.get("/api/v1/me", headers={"Authorization": "Bearer rate-test"})
    assert r.json()["error"]["code"] == "rate_limited" and int(r.headers["retry-after"]) >= 1


def test_models_and_migrations_do_not_drift():
    result = subprocess.run([sys.executable, "-m", "alembic", "check"], cwd=BACKEND_DIR, capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
