"""End-to-end scenarios over real HTTP against a RUNNING demo-mode DealMate API.

    DEALMATE_E2E_URL=http://localhost:8000 DEALMATE_E2E_TOKEN=<token> pytest tests/e2e -q

Unlike backend/tests (in-process), this exercises the deployed stack: uvicorn/Docker,
migrations, PostgreSQL and the JSON contract the Android app consumes. Skipped when
DEALMATE_E2E_URL is not set.
"""
import os

import httpx
import pytest

BASE = os.environ.get("DEALMATE_E2E_URL")
TOKEN = os.environ.get("DEALMATE_E2E_TOKEN", "")
pytestmark = pytest.mark.skipif(not BASE, reason="DEALMATE_E2E_URL not set")


@pytest.fixture(scope="module")
def api():
    with httpx.Client(base_url=BASE, headers={"Authorization": f"Bearer {TOKEN}"}, timeout=30) as client:
        health = client.get("/health").json()
        assert health["status"] == "ok"
        if not health["demo_mode"]:
            pytest.skip("scenarios are written for the mock catalogue (demo mode)")
        yield client


def search(api, query, **extra):
    response = api.post("/api/v1/search", json={"query": query, **extra})
    assert response.status_code == 200, response.text
    return response.json()


def test_scenario_1_only_correct_variants_are_exact(api):
    body = search(api, "Anessa 60ml")
    exact = [g for g in body["groups"] if g["match"]["match_type"] == "exact"]
    assert exact and all(g["attributes"]["volume_ml"] == 60 and g["attributes"]["brand"] == "anessa" for g in exact)
    others = {g["title"]: g["match"]["match_type"] for g in body["similar_groups"]}
    assert others.get("Anessa Perfect UV Skincare Milk 20ml") == "similar"
    assert others.get("Anessa Perfect UV Skincare Gel 90g") == "similar"


def test_scenario_2_three_marketplaces_sorted_by_final_price(api):
    body = search(api, "kem chống nắng Anessa Perfect UV Skincare Milk 60ml")
    assert {s["marketplace"] for s in body["sources"] if s["status"] == "ok"} == {"shopee", "lazada", "tiktok_shop"}
    offers = body["groups"][0]["offers"]
    tier = {"verified": 0, "estimated": 1, "incomplete": 2}
    keys = [(tier[o["price"]["confidence"]], o["price"]["final_price"] or o["price"]["known_subtotal"]) for o in offers]
    assert keys == sorted(keys) and {o["marketplace"] for o in offers} == {"shopee", "lazada", "tiktok_shop"}
    assert body["best"]["final_price"] == offers[0]["price"]["final_price"] == 319_000
    assert all(o["is_mock"] and ".invalid/" in o["url"] for o in offers), "demo data must be labelled"


def test_scenario_3_partial_results_when_a_marketplace_is_excluded(api):
    # Over HTTP we cannot break a connector; the in-process suite covers failure/timeout.
    # Here we verify that a subset of marketplaces still yields a complete, ranked answer.
    body = search(api, "Tã quần Merries size L 44 miếng", marketplaces=["shopee", "tiktok_shop"])
    assert {s["marketplace"] for s in body["sources"]} == {"shopee", "tiktok_shop"}
    assert body["best"]["marketplace"] == "shopee" and body["groups"]


def test_scenario_4_unknown_shipping_is_not_the_verified_cheapest(api):
    body = search(api, "Dyson HD15 màu đen")
    group = next(g for g in body["groups"] if g["title"] == "Dyson Supersonic HD15 màu đen")
    incomplete = [o for o in group["offers"] if o["price"]["confidence"] == "incomplete"]
    assert incomplete and all(o["price"]["final_price"] is None for o in incomplete)
    assert group["best_offer_id"] not in {o["id"] for o in incomplete}
    assert min(o["price"]["listed_price"] for o in group["offers"]) == incomplete[0]["price"]["listed_price"]
    assert body["best"]["confidence"] == "verified"


def test_scenario_5_save_product_with_target_price(api):
    group = search(api, "Nước giặt Ariel Matic cửa trước túi 3.6kg")["groups"][0]
    created = api.post("/api/v1/wishlist", json={"variant_id": group["variant_id"], "target_price": 200_000})
    assert created.status_code == 201, created.text
    item = created.json()
    try:
        assert any(w["id"] == item["id"] for w in api.get("/api/v1/wishlist").json()["items"])
        alerts = api.get("/api/v1/price-alerts").json()["items"]
        assert any(a["id"] == item["alert_id"] and a["target_price"] == 200_000 for a in alerts)
    finally:
        assert api.delete(f"/api/v1/wishlist/{item['id']}").status_code == 204
