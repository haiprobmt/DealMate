import pytest

from app.ai.client import AIUnavailable
from app.ai.query_interpreter import interpret, interpret_with_rules, validate_ai_query
from app.core.errors import AppError
from app.schemas.domain import Marketplace, ProductQuery
from app.services.url_parser import parse_marketplace_url
from tests.conftest import FakeLLM


def test_example_from_the_spec():
    q = interpret_with_rules("Tìm Anessa sunscreen 60ml dưới 350k.")
    assert (q.brand, q.volume, q.max_price, q.currency) == ("anessa", "60ml", 350_000, "VND")
    assert q.search_keywords == "Anessa sunscreen 60ml"


@pytest.mark.parametrize("text,max_price", [
    ("máy sấy tóc Dyson dưới 8 triệu", 8_000_000), ("nồi chiên dưới 2.000.000đ", 2_000_000),
    ("nồi chiên không quá 1,5 triệu", 1_500_000), ("bỉm merries tối đa 400k", 400_000),
    ("tai nghe dưới 2tr", 2_000_000),
])
def test_budget_parsing(text, max_price):
    assert interpret_with_rules(text).max_price == max_price


def test_price_range_and_min():
    q = interpret_with_rules("iPhone 16 Pro từ 25 đến 28 triệu")
    assert (q.min_price, q.max_price) == (25_000_000, 28_000_000)
    assert interpret_with_rules("điện thoại trên 5 triệu").min_price == 5_000_000


def test_ambiguous_budget_is_not_guessed():
    q = interpret_with_rules("nước giặt dưới 350")
    assert q.max_price is None and "max_price" in q.uncertain_fields


def test_no_model_is_invented_for_vague_query():
    q = interpret_with_rules("Tìm máy sấy tóc Dyson dưới 8 triệu.")
    assert q.brand == "dyson" and q.model is None and q.color is None and q.category == "hair_dryer"
    assert "brand" in interpret_with_rules("nồi chiên không dầu 4 lít").uncertain_fields


def test_other_examples():
    q = interpret_with_rules("Tìm bỉm Merries size L.")
    assert (q.brand, q.size, q.category) == ("merries", "L", "diaper")
    q = interpret_with_rules("Tìm nước giặt Ariel 3.6kg.")
    assert (q.brand, q.weight) == ("ariel", "3.6kg")


def test_ai_output_is_grounded_by_validation():
    text = "Tìm máy sấy tóc Dyson dưới 8 triệu"
    hallucinated = ProductQuery(brand="Dyson", product_name="máy sấy tóc", model="HD15", volume="60ml",
                                max_price=7_000_000, search_keywords="máy sấy tóc Dyson")
    cleaned = validate_ai_query(hallucinated, text)
    assert cleaned.brand == "Dyson"
    assert cleaned.model is None and cleaned.volume is None, "values absent from the user's text are dropped"
    assert {"model", "volume"} <= set(cleaned.uncertain_fields)
    assert cleaned.max_price == 8_000_000, "money comes from the deterministic parser"


async def test_interpret_uses_ai_then_falls_back():
    llm = FakeLLM({ProductQuery: ProductQuery(brand="Anessa", product_name="kem chống nắng", volume="60ml",
                                              search_keywords="kem chống nắng Anessa 60ml")})
    query, who, notes = await interpret("kem chống nắng Anessa 60ml", llm)
    assert who == "ai" and query.brand == "Anessa" and not notes
    query, who, notes = await interpret("kem chống nắng Anessa 60ml", FakeLLM({ProductQuery: AIUnavailable("down")}))
    assert who == "rules" and query.brand == "anessa" and notes
    assert (await interpret("x y", None))[1] == "rules"


def test_url_parsing_supported_marketplaces():
    p = parse_marketplace_url("https://shopee.vn/Kem-ch%E1%BB%91ng-n%E1%BA%AFng-Anessa-60ml-i.123456.7890123?x=1")
    assert (p.marketplace, p.product_id, p.slug_text) == (Marketplace.SHOPEE, "123456.7890123", "Kem chống nắng Anessa 60ml")
    assert parse_marketplace_url("https://shopee.vn/product/11/22").product_id == "11.22"
    p = parse_marketplace_url("https://www.lazada.vn/products/anessa-perfect-uv-60ml-i2345678-s99.html")
    assert (p.marketplace, p.product_id, p.slug_text) == (Marketplace.LAZADA, "2345678", "anessa perfect uv 60ml")
    p = parse_marketplace_url("https://www.tiktok.com/view/product/1729384756")
    assert (p.marketplace, p.product_id) == (Marketplace.TIKTOK_SHOP, "1729384756")
    assert parse_marketplace_url("https://vt.tiktok.com/ZSabc/").is_short_link
    assert parse_marketplace_url("https://mock.dealmate.invalid/lazada/product/MOCK-LA-002").is_mock


@pytest.mark.parametrize("url,code", [
    ("ftp://shopee.vn/x", "invalid_url"), ("not a url", "invalid_url"),
    ("https://example.com/product/1", "unsupported_marketplace"),
    ("https://shopee.vn.evil.com/a-i.1.2", "unsupported_marketplace"),
    ("https://evilshopee.vn/a-i.1.2", "unsupported_marketplace"),
])
def test_url_rejections(url, code):
    with pytest.raises(AppError) as exc:
        parse_marketplace_url(url)
    assert exc.value.code == code
