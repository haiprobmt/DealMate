import hashlib
import json

import httpx
import pytest

from app.connectors.base import ConnectorError, ConnectorNotConfigured, ConnectorNotImplemented
from app.connectors.mock import MOCK_HOST, MockConnector
from app.connectors.pending import LazadaConnector, TikTokShopConnector
from app.connectors.registry import build_registry
from app.connectors.shopee_affiliate import ShopeeAffiliateConnector
from app.core.config import Settings
from app.schemas.domain import Marketplace, ShippingStatus


async def test_mock_offers_are_always_labelled_mock():
    for marketplace in Marketplace:
        offers = await MockConnector(marketplace).search_products("anessa 60ml")
        assert offers
        for o in offers:
            assert o.is_mock and o.marketplace == marketplace and MOCK_HOST in o.url
            assert o.title.startswith("[MẪU]") and o.external_id.startswith("MOCK-") and o.retrieved_at.tzinfo


async def test_mock_details_and_url_round_trip():
    c = MockConnector(Marketplace.LAZADA)
    offer = (await c.search_products("anessa 60ml"))[0]
    again = await c.get_product_details(offer.external_id)
    assert again.title == offer.title and await c.get_product_url(offer.external_id) == offer.url
    assert await c.get_product_details("MOCK-NOPE") is None
    assert (await c.health_check()).verified_live is False


def test_registry_never_mixes_mock_and_real():
    demo = build_registry(Settings(mock_mode=True))
    assert demo.demo_mode and all(c.is_mock for c in demo.all())
    live = build_registry(Settings(mock_mode=False))
    assert not live.demo_mode and not any(c.is_mock for c in live.all())
    assert {c.marketplace for c in live.all()} == set(Marketplace)


async def test_pending_connectors_refuse_instead_of_inventing():
    for connector in (LazadaConnector(), TikTokShopConnector()):
        with pytest.raises(ConnectorNotImplemented):
            await connector.search_products("anessa")
        health = await connector.health_check()
        assert health.status == "not_implemented" and not health.verified_live


async def test_shopee_requires_credentials():
    connector = ShopeeAffiliateConnector(None, None, "https://shopee.invalid/graphql")
    with pytest.raises(ConnectorNotConfigured):
        await connector.search_products("anessa")
    assert (await connector.health_check()).status == "not_configured"


def _shopee(handler):
    return ShopeeAffiliateConnector("app123", "secret456", "https://shopee.invalid/graphql",
                                    transport=httpx.MockTransport(handler))


async def test_shopee_request_signing_and_response_mapping():
    """Shape test against a fake transport built from the public docs — NOT a live verification."""
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["auth"], seen["body"] = request.headers["authorization"], request.content.decode()
        return httpx.Response(200, json={"data": {"productOfferV2": {"nodes": [
            {"itemId": 777, "shopId": 42, "productName": "Sữa chống nắng Anessa 60ml", "priceMin": "365000",
             "priceMax": "365000", "imageUrl": "https://img.invalid/a.jpg", "shopName": "Shop A",
             "ratingStar": "4.9", "sales": 120, "productLink": "https://shopee.vn/product/42/777",
             "offerLink": "https://s.shopee.vn/abc"},
            {"itemId": 1, "shopId": 2, "productName": "broken", "priceMin": "not-a-number"}]}}})

    offers = await _shopee(handler).search_products("anessa 60ml")
    credential, timestamp, signature = (part.split("=")[1] for part in seen["auth"].removeprefix("SHA256 ").split(", "))
    assert credential == "app123"
    assert signature == hashlib.sha256(f"app123{timestamp}{seen['body']}secret456".encode()).hexdigest()
    assert json.loads(seen["body"])["variables"]["keyword"] == "anessa 60ml"
    assert len(offers) == 1, "the malformed node is skipped, not guessed"
    o = offers[0]
    assert (o.external_id, o.listed_price, o.url, o.is_mock) == ("42.777", 365000, "https://shopee.vn/product/42/777", False)
    assert o.shipping_fee is None and o.shipping_status == ShippingStatus.UNKNOWN and not o.vouchers


@pytest.mark.parametrize("response", [
    httpx.Response(500, text="oops"), httpx.Response(200, text="<html>"),
    httpx.Response(200, json={"errors": [{"message": "Invalid Signature"}]}),
    httpx.Response(200, json={"data": {}}),
])
async def test_shopee_upstream_problems_raise_connector_error(response):
    with pytest.raises(ConnectorError):
        await _shopee(lambda request: response).search_products("x")


async def test_shopee_network_failure_raises_connector_error():
    def handler(request):
        raise httpx.ConnectTimeout("timeout")
    with pytest.raises(ConnectorError):
        await _shopee(handler).search_products("x")
    assert (await _shopee(handler).health_check()).status == "error"
