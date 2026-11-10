"""API tests incl. the end-to-end scenarios 1-4 of the specification (mock marketplaces)."""
import io

from app.ai.image_identifier import ImageIdentification
from app.ai.match_judge import JudgedCandidate, JudgeOutput
from app.connectors.mock import MockFailure
from app.schemas.domain import Marketplace, MatchType

PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 64


async def search(client, query, **extra):
    response = await client.post("/api/v1/search", json={"query": query, **extra})
    assert response.status_code == 200, response.text
    return response.json()


def all_offers(body, key="groups"):
    return [o for g in body[key] for o in g["offers"]]


# --- Scenario 1 --------------------------------------------------------------------------
async def test_scenario1_anessa_60ml_only_correct_variants_are_exact(client):
    body = await search(client, "Anessa 60ml")
    exact = [g for g in body["groups"] if g["match"]["match_type"] == "exact"]
    assert exact
    for group in exact:
        assert group["attributes"]["brand"] == "anessa" and group["attributes"]["volume_ml"] == 60
    titles = {g["title"]: g["match"]["match_type"] for g in body["groups"] + body["similar_groups"]}
    assert titles["Anessa Perfect UV Skincare Milk 20ml"] == "similar"
    assert titles["Anessa Perfect UV Skincare Gel 90g"] == "similar"
    assert titles["Anessa chống nắng 60ml"] == "likely", "a vague listing is not a confirmed match"
    assert not any("Bior" in o["title"] for o in all_offers(body) + all_offers(body, "similar_groups"))
    # combo 2 and the imported version are kept apart from the single official-channel item
    main = next(g for g in exact if g["title"] == "Anessa Perfect UV Skincare Milk 60ml")
    assert all("Combo" not in o["title"] and "nội địa" not in o["title"] for o in main["offers"])


# --- Scenario 2 --------------------------------------------------------------------------
async def test_scenario2_three_marketplaces_combined_and_sorted_by_final_price(client):
    body = await search(client, "kem chống nắng Anessa Perfect UV Skincare Milk 60ml")
    assert body["demo_mode"] is True and body["partial"] is False
    assert {s["marketplace"]: s["status"] for s in body["sources"]} == {
        "shopee": "ok", "lazada": "ok", "tiktok_shop": "ok"}
    group = body["groups"][0]
    assert {o["marketplace"] for o in group["offers"]} == {"shopee", "lazada", "tiktok_shop"}
    tiers = {"verified": 0, "estimated": 1, "incomplete": 2}
    keys = [(tiers[o["price"]["confidence"]], o["price"]["final_price"] or o["price"]["known_subtotal"])
            for o in group["offers"]]
    assert keys == sorted(keys)
    best = body["best"]
    # Lazada: 359.000 − 30.000 (shop) − 25.000 (sàn, 8% capped) + 15.000 ship = 319.000
    assert (best["marketplace"], best["final_price"], best["confidence"]) == ("lazada", 319_000, "verified")
    assert best["offer_id"] == group["best_offer_id"] == group["offers"][0]["id"]
    assert "DỮ LIỆU MẪU" in best["explanation"] and "319.000đ" in best["explanation"]
    for o in group["offers"]:
        assert o["is_mock"] and o["url"].startswith("https://mock.dealmate.invalid/") and o["retrieved_at"]
        p = o["price"]
        if p["final_price"] is not None:
            assert p["final_price"] == p["subtotal"] - p["voucher_discount"] + p["shipping_fee"] + p["mandatory_fees"]


# --- Scenario 3 --------------------------------------------------------------------------
async def test_scenario3_one_marketplace_down_others_still_shown(client, registry):
    registry.get(Marketplace.LAZADA).fail_with = MockFailure("Lazada bảo trì")
    body = await search(client, "Tã quần Merries size L 44 miếng")
    statuses = {s["marketplace"]: s for s in body["sources"]}
    assert statuses["lazada"]["status"] == "error" and "bảo trì" in statuses["lazada"]["message"]
    assert statuses["shopee"]["status"] == statuses["tiktok_shop"]["status"] == "ok"
    assert body["partial"] is True
    assert {o["marketplace"] for o in all_offers(body)} == {"shopee", "tiktok_shop"}
    assert body["best"]["marketplace"] == "shopee"


async def test_marketplace_timeout_is_isolated(client, registry):
    registry.get(Marketplace.TIKTOK_SHOP).delay_seconds = 2
    body = await search(client, "Nước giặt Ariel Matic cửa trước túi 3.6kg")
    statuses = {s["marketplace"]: s["status"] for s in body["sources"]}
    assert statuses == {"shopee": "ok", "lazada": "ok", "tiktok_shop": "timeout"}
    assert body["partial"] and body["groups"]
    assert next(s for s in body["sources"] if s["marketplace"] == "tiktok_shop")["elapsed_ms"] < 1500


async def test_all_marketplaces_down_returns_empty_not_error(client, registry):
    for connector in registry.all():
        connector.fail_with = MockFailure("down")
    body = await search(client, "Anessa 60ml")
    assert body["groups"] == [] and body["best"] is None and body["partial"]


# --- Scenario 4 --------------------------------------------------------------------------
async def test_scenario4_unknown_shipping_is_incomplete_and_never_the_verified_cheapest(client):
    body = await search(client, "Dyson HD15 màu đen")
    group = next(g for g in body["groups"] if g["title"] == "Dyson Supersonic HD15 màu đen")
    tiktok = next(o for o in group["offers"] if o["marketplace"] == "tiktok_shop")
    assert tiktok["price"]["listed_price"] == 7_950_000, "lowest listed price in the group"
    assert tiktok["price"]["confidence"] == "incomplete"
    assert tiktok["price"]["final_price"] is None and tiktok["price"]["shipping_fee"] is None
    assert group["offers"][-1]["id"] == tiktok["id"], "incomplete offers rank last"
    assert group["best_offer_id"] != tiktok["id"] and group["lowest_final_price"] == 8_390_000
    assert body["best"]["offer_id"] != tiktok["id"] and body["best"]["confidence"] == "verified"
    assert "có thể rẻ hơn" in body["best"]["caveat"]


async def test_estimated_offer_does_not_outrank_verified(client):
    body = await search(client, "kem chống nắng Anessa Perfect UV Skincare Milk 60ml")
    offers = body["groups"][0]["offers"]
    estimated = next(o for o in offers if o["price"]["confidence"] == "estimated")
    assert estimated["price"]["final_price"] == 309_000 < body["best"]["final_price"]
    assert offers.index(estimated) > max(i for i, o in enumerate(offers) if o["price"]["confidence"] == "verified")
    assert estimated["price"]["applied_vouchers"][0]["estimated"] is True


# --- Other search behaviour ---------------------------------------------------------------
async def test_budget_marks_and_hides_over_budget(client):
    body = await search(client, "Tìm máy sấy tóc Dyson dưới 8 triệu")
    assert body["query"]["spec"]["max_price"] == 8_000_000 and body["over_budget_count"] >= 2
    assert body["best"]["final_price"] <= 8_000_000
    for group in body["groups"]:
        assert not all(o["over_budget"] for o in group["offers"])
        if group["best_offer_id"]:
            assert next(o for o in group["offers"] if o["id"] == group["best_offer_id"])["over_budget"] is False


async def test_vague_query_asks_which_variant(client):
    body = await search(client, "Tìm bỉm Merries size L")
    assert body["clarification"]["needed"] and len(body["clarification"]["suggestions"]) >= 2
    assert "biến thể" in body["best"]["caveat"]


async def test_marketplace_filter_and_quantity(client):
    body = await search(client, "Nước giặt Ariel Matic cửa trước túi 3.6kg", marketplaces=["shopee"], quantity=2)
    assert [s["marketplace"] for s in body["sources"]] == ["shopee"]
    offer = body["groups"][0]["offers"][0]
    assert offer["price"]["quantity"] == 2 and offer["price"]["subtotal"] == 470_000


async def test_no_results_gives_clarification(client):
    body = await search(client, "ghế công thái học Herman Miller")
    assert body["groups"] == [] and body["similar_groups"] == [] and body["best"] is None
    assert body["clarification"]["needed"]


async def test_search_validation_and_auth(client):
    r = await client.post("/api/v1/search", json={"query": " "})
    assert r.status_code == 422 and r.json()["error"]["code"] == "validation_error"
    assert r.json()["error"]["request_id"] == r.headers["x-request-id"]
    r = await client.post("/api/v1/search", json={"query": "anessa", "marketplaces": ["amazon"]})
    assert r.status_code == 422
    r = await client.post("/api/v1/search", json={"query": "anessa"}, headers={"Authorization": "Bearer wrong"})
    assert r.status_code == 401 and r.json()["error"]["code"] == "unauthorized"
    r = await client.post("/api/v1/search", json={"query": "anessa"}, headers={"Authorization": ""})
    assert r.status_code == 401


async def test_ai_judge_can_downgrade_but_not_break_hard_rules(client, fake_llm):
    def verdict(content):
        lines = [line for line in content.split("\n") if line.startswith("[")]
        return JudgeOutput(results=[JudgedCandidate(index=i, match_type=MatchType.EXACT, confidence=0.99,
                                                    matched_attributes=[], different_attributes=[], reason="AI nói trùng")
                                    for i in range(len(lines))])
    fake_llm.responses[JudgeOutput] = verdict
    body = await search(client, "kem chống nắng Anessa 60ml")
    assert body["query"]["interpreter"] == "rules", "interpreter fell back because it was not scripted"
    by_title = {g["title"]: g["match"] for g in body["groups"] + body["similar_groups"]}
    # 20ml / gel 90g conflict with the request deterministically: the AI never sees or overrides them
    assert by_title["Anessa Perfect UV Skincare Milk 20ml"]["match_type"] == "similar"
    assert by_title["Anessa Perfect UV Skincare Gel 90g"]["match_type"] == "similar"


# --- URL search ---------------------------------------------------------------------------
async def test_url_search_resolves_listing_and_compares_other_marketplaces(client):
    url = "https://mock.dealmate.invalid/shopee/product/MOCK-SH-010"
    r = await client.post("/api/v1/search/url", json={"url": url})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["search_type"] == "url" and "Shopee" in body["query"]["notes"][0]
    group = body["groups"][0]
    assert group["title"] == "Merries tã quần size L 44 miếng"
    assert {o["marketplace"] for o in group["offers"]} == {"shopee", "lazada", "tiktok_shop"}
    assert any(o["url"] == url for o in group["offers"]), "the original listing keeps its own URL"


async def test_url_search_real_link_in_demo_mode_falls_back_to_slug_text(client):
    r = await client.post("/api/v1/search/url", json={"url": "https://shopee.vn/Ta-quan-Merries-size-L-44-mieng-i.11.22"})
    body = r.json()
    assert r.status_code == 200 and "không nằm trong kết quả" in body["query"]["notes"][0]
    assert body["groups"] and all(o["is_mock"] for o in all_offers(body))


async def test_url_search_rejections(client):
    for url, code in [("https://example.com/p/1", "unsupported_marketplace"),
                      ("https://vt.tiktok.com/ZSabc/", "short_link_unsupported"),
                      ("https://www.tiktok.com/view/product/123", "url_unresolvable"),
                      ("https://mock.dealmate.invalid/shopee/product/MOCK-NOPE", "url_unresolvable")]:
        r = await client.post("/api/v1/search/url", json={"url": url})
        assert r.status_code == 422 and r.json()["error"]["code"] == code, (url, r.text)


# --- Image search -------------------------------------------------------------------------
def ident(**kw):
    base = dict(is_product=True, confidence=0.9, brand="Anessa", product_name="Perfect UV Sunscreen Skincare Milk",
                model=None, variant=None, volume="60ml", weight=None, size=None, color=None,
                visible_text=["ANESSA", "60mL"], alternative_queries=["kem chống nắng Anessa"], reason="Đọc được nhãn.")
    return ImageIdentification(**{**base, **kw})


async def test_image_search_without_ai_is_a_clear_503(client):
    r = await client.post("/api/v1/search/image", files={"image": ("a.png", io.BytesIO(PNG), "image/png")})
    assert r.status_code == 503 and r.json()["error"]["code"] == "ai_unavailable"


async def test_image_search_identifies_and_searches(client, fake_llm):
    fake_llm.responses[ImageIdentification] = ident()
    r = await client.post("/api/v1/search/image", files={"image": ("a.png", io.BytesIO(PNG), "image/png")},
                          data={"hint": "kem chống nắng"})
    body = r.json()
    assert r.status_code == 200 and body["search_type"] == "image" and body["query"]["interpreter"] == "ai"
    assert body["groups"][0]["title"] == "Anessa Perfect UV Skincare Milk 60ml"
    schema, _, content = fake_llm.calls[0]
    assert content[0]["type"] == "image" and content[0]["source"]["media_type"] == "image/png"


async def test_image_search_low_confidence_asks_for_clarification(client, fake_llm):
    fake_llm.responses[ImageIdentification] = ident(confidence=0.4, brand=None, volume=None)
    r = await client.post("/api/v1/search/image", files={"image": ("a.png", io.BytesIO(PNG), "image/png")})
    body = r.json()
    assert r.status_code == 200 and body["groups"] == [] and body["best"] is None
    assert body["clarification"]["needed"] and body["clarification"]["suggestions"] == ["kem chống nắng Anessa"]


async def test_image_upload_validation(client, fake_llm):
    r = await client.post("/api/v1/search/image", files={"image": ("a.png", io.BytesIO(b"<html>not an image"), "image/png")})
    assert r.status_code == 415 and r.json()["error"]["code"] == "unsupported_image"
    big = PNG + b"\x00" * (5 * 1024 * 1024 + 1)
    r = await client.post("/api/v1/search/image", files={"image": ("a.png", io.BytesIO(big), "image/png")})
    assert r.status_code == 413 and r.json()["error"]["code"] == "image_too_large"
    r = await client.post("/api/v1/search/image", files={"image": ("a.png", io.BytesIO(b""), "image/png")})
    assert r.status_code == 422
    assert fake_llm.calls == [], "invalid uploads never reach the AI"


async def test_combo_is_a_pack_filter_but_n_bottles_is_a_purchase_quantity(client):
    combo = await search(client, "combo 2 sữa chống nắng Anessa Perfect UV Skincare Milk 60ml")
    assert combo["groups"][0]["title"].endswith("(combo 2)") and combo["groups"][0]["offers"][0]["price"]["quantity"] == 1
    assert all(g["attributes"]["pack_quantity"] == 2 for g in combo["groups"] if g["match"]["match_type"] == "exact")
    two = await search(client, "mua 2 túi nước giặt Ariel Matic cửa trước 3.6kg")
    offer = two["groups"][0]["offers"][0]
    assert offer["price"]["quantity"] == 2 and two["groups"][0]["attributes"]["pack_quantity"] is None


async def test_oversized_request_is_rejected_before_processing(client):
    r = await client.post("/api/v1/search", content=b"x" * (6 * 1024 * 1024),
                          headers={"content-type": "application/json"})
    assert r.status_code == 413 and r.json()["error"]["code"] == "payload_too_large"
