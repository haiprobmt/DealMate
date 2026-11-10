"""Connector MẪU dùng cho phát triển/demo. Mọi offer trả về đều có ``is_mock=True``
và URL thuộc miền ``mock.dealmate.invalid`` (miền .invalid không bao giờ tồn tại)."""
import asyncio
import json
from datetime import UTC, datetime
from functools import lru_cache
from pathlib import Path

from app.connectors.base import ConnectorError, ConnectorHealth, MarketplaceConnector
from app.schemas.domain import Marketplace, RawOffer
from app.services.normalization import detect_brand
from app.utils.text import fold

MOCK_HOST = "mock.dealmate.invalid"
_CATALOG = Path(__file__).parent / "mock_data" / "catalog.json"
_QUERY_NOISE = set("tim mua gia re nhat tot duoi tren khoang cho toi can muon cai chiec hang chinh "
                   "trieu nghin k d vnd dong mau".split())


@lru_cache
def _load_catalog() -> list[dict]:
    return json.loads(_CATALOG.read_text(encoding="utf-8"))["listings"]


class MockConnector(MarketplaceConnector):
    connector_name = "mock"
    is_mock = True

    def __init__(self, marketplace: Marketplace):
        self.marketplace = marketplace
        # Test/demo hooks to exercise failure handling.
        self.delay_seconds: float = 0.0
        self.fail_with: Exception | None = None

    def _to_offer(self, row: dict) -> RawOffer:
        return RawOffer(**row, url=f"https://{MOCK_HOST}/{self.marketplace.value}/product/{row['external_id']}",
                        retrieved_at=datetime.now(UTC), is_mock=True, in_stock=True)

    def _rows(self) -> list[dict]:
        return [r for r in _load_catalog() if r["marketplace"] == self.marketplace.value]

    async def _simulate(self) -> None:
        if self.delay_seconds:
            await asyncio.sleep(self.delay_seconds)
        if self.fail_with:
            raise self.fail_with

    async def search_products(self, query: str, limit: int = 30) -> list[RawOffer]:
        await self._simulate()
        tokens = [t for t in fold(query).split() if t not in _QUERY_NOISE and not t.isdigit()]
        if not tokens:
            return []
        brand = detect_brand(fold(query))
        scored: list[tuple[float, dict]] = []
        for row in self._rows():
            folded_title = fold(row["title"])
            title = f" {folded_title} "
            hits = sum(1 for t in tokens if f" {t} " in title or (len(t) > 3 and t in title))
            score = hits / len(tokens)
            # Like a real marketplace search this is recall-oriented: every listing of the
            # brand is returned and precision is left to the matching engine.
            if score >= 0.5 or (brand and detect_brand(folded_title) == brand):
                scored.append((score, row))
        scored.sort(key=lambda pair: -pair[0])
        return [self._to_offer(row) for _, row in scored[:limit]]

    async def get_product_details(self, product_id: str) -> RawOffer | None:
        await self._simulate()
        for row in self._rows():
            if row["external_id"] == product_id:
                return self._to_offer(row)
        return None

    async def get_product_url(self, product_id: str) -> str:
        return f"https://{MOCK_HOST}/{self.marketplace.value}/product/{product_id}"

    async def health_check(self) -> ConnectorHealth:
        if self.fail_with:
            return ConnectorHealth("error", f"Mock connector lỗi giả lập: {self.fail_with}")
        return ConnectorHealth("available", "Dữ liệu MẪU cho phát triển — không phải dữ liệu thật từ sàn.")


class MockFailure(ConnectorError):
    pass
