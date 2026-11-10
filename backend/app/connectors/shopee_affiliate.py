"""Shopee Việt Nam — Shopee Affiliate Open API (GraphQL).

STATUS: implemented from the public description of the API (endpoint, SHA256 request signing
and the productOfferV2 query, cross-checked against an open-source community client) and
exercised only against a fake HTTP transport in tests. It has NOT been verified against the live API because no
affiliate credentials were available during development. ``health_check`` reports
``verified_live=True`` only after a real, well-formed API response at runtime.
See docs/MARKETPLACE_INTEGRATION.md.

What the affiliate API provides: product name, price range, shop, rating, sales, product link.
What it does NOT provide: shipping fee and vouchers for a given buyer. Consequently every
offer from this connector has unknown shipping and is ranked as ``incomplete``.
"""
import hashlib
import json
import time
from datetime import UTC, datetime

import httpx

from app.connectors.base import (
    ConnectorError,
    ConnectorHealth,
    ConnectorNotConfigured,
    MarketplaceConnector,
)
from app.schemas.domain import Marketplace, RawOffer, SellerInfo, ShippingStatus

_FIELDS = ("itemId productName priceMin priceMax imageUrl shopId shopName ratingStar sales "
           "productLink offerLink")


def _search_query(keyword: str, limit: int) -> str:
    # Arguments are inlined (json.dumps yields a valid GraphQL string literal), which is the
    # form used by the documentation examples and community clients.
    return ("{ productOfferV2(keyword: %s, limit: %d, page: 1, sortType: 1) { nodes { %s } } }"
            % (json.dumps(keyword, ensure_ascii=False), limit, _FIELDS))


def _detail_query(shop_id: int, item_id: int) -> str:
    return "{ productOfferV2(shopId: %d, itemId: %d) { nodes { %s } } }" % (shop_id, item_id, _FIELDS)


class ShopeeAffiliateConnector(MarketplaceConnector):
    marketplace = Marketplace.SHOPEE
    connector_name = "shopee_affiliate"

    def __init__(self, app_id: str | None, secret: str | None, endpoint: str,
                 timeout: float = 8.0, transport: httpx.AsyncBaseTransport | None = None):
        self._app_id, self._secret, self._endpoint = app_id, secret, endpoint
        self._timeout, self._transport = timeout, transport

    @property
    def configured(self) -> bool:
        return bool(self._app_id and self._secret)

    def _auth_header(self, payload: str, timestamp: int) -> str:
        signature = hashlib.sha256(
            f"{self._app_id}{timestamp}{payload}{self._secret}".encode()).hexdigest()
        return f"SHA256 Credential={self._app_id}, Timestamp={timestamp}, Signature={signature}"

    async def _call(self, query: str) -> list[dict]:
        if not self.configured:
            raise ConnectorNotConfigured(
                "Thiếu DEALMATE_SHOPEE_AFFILIATE_APP_ID / DEALMATE_SHOPEE_AFFILIATE_SECRET.")
        payload = json.dumps({"query": query}, ensure_ascii=False)
        headers = {"Content-Type": "application/json",
                   "Authorization": self._auth_header(payload, int(time.time()))}
        try:
            async with httpx.AsyncClient(timeout=self._timeout, transport=self._transport) as client:
                response = await client.post(self._endpoint, content=payload.encode(), headers=headers)
        except httpx.HTTPError as exc:
            raise ConnectorError(f"Không kết nối được Shopee Affiliate API: {exc!r}") from exc
        if response.status_code != 200:
            raise ConnectorError(f"Shopee Affiliate API trả về HTTP {response.status_code}.")
        try:
            body = response.json()
        except ValueError as exc:
            raise ConnectorError("Shopee Affiliate API trả về dữ liệu không phải JSON.") from exc
        if body.get("errors"):
            message = body["errors"][0].get("message", "unknown")
            raise ConnectorError(f"Shopee Affiliate API báo lỗi: {message}")
        try:
            return body["data"]["productOfferV2"]["nodes"] or []
        except (KeyError, TypeError) as exc:
            raise ConnectorError("Shopee Affiliate API trả về cấu trúc không như tài liệu.") from exc

    @staticmethod
    def _to_offer(node: dict) -> RawOffer | None:
        try:
            price = int(float(node["priceMin"]))
            if price <= 0:
                return None
            return RawOffer(
                marketplace=Marketplace.SHOPEE,
                external_id=f"{node['shopId']}.{node['itemId']}",
                title=node["productName"], url=node["productLink"], image_url=node.get("imageUrl"),
                seller=SellerInfo(external_id=str(node["shopId"]), name=node.get("shopName") or "Shopee shop"),
                listed_price=price, shipping_fee=None, shipping_status=ShippingStatus.UNKNOWN,
                rating=float(node["ratingStar"]) if node.get("ratingStar") else None,
                sold_count=int(node["sales"]) if node.get("sales") is not None else None,
                retrieved_at=datetime.now(UTC), is_mock=False)
        except (KeyError, TypeError, ValueError):
            return None   # malformed node: skip it rather than guess

    async def search_products(self, query: str, limit: int = 30) -> list[RawOffer]:
        nodes = await self._call(_search_query(query, min(limit, 50)))
        return [o for o in (self._to_offer(n) for n in nodes) if o]

    async def get_product_details(self, product_id: str) -> RawOffer | None:
        try:
            shop_id, item_id = (int(p) for p in product_id.split("."))
        except ValueError as exc:
            raise ConnectorError("Mã sản phẩm Shopee phải có dạng <shopId>.<itemId>.") from exc
        nodes = await self._call(_detail_query(shop_id, item_id))
        return self._to_offer(nodes[0]) if nodes else None

    async def get_product_url(self, product_id: str) -> str:
        shop_id, _, item_id = product_id.partition(".")
        return f"https://shopee.vn/product/{shop_id}/{item_id}"

    async def health_check(self) -> ConnectorHealth:
        if not self.configured:
            return ConnectorHealth("not_configured",
                                   "Chưa có thông tin Shopee Affiliate (App ID / Secret).")
        try:
            await self._call(_search_query("test", 1))
        except ConnectorError as exc:
            return ConnectorHealth("error", str(exc))
        return ConnectorHealth(
            "available", "Shopee Affiliate API phản hồi hợp lệ. Lưu ý: API không cung cấp phí vận "
            "chuyển/voucher nên giá cuối luôn ở trạng thái thiếu thông tin.", verified_live=True)
