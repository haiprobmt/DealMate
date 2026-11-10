"""Chọn connector cho từng sàn. Không bao giờ trộn mock với dữ liệu thật:
mock_mode=True -> mọi sàn dùng MockConnector; mock_mode=False -> chỉ connector thật."""
from app.connectors.base import MarketplaceConnector
from app.connectors.mock import MockConnector
from app.connectors.pending import LazadaConnector, TikTokShopConnector
from app.connectors.shopee_affiliate import ShopeeAffiliateConnector
from app.core.config import Settings, get_settings
from app.schemas.domain import Marketplace


class ConnectorRegistry:
    def __init__(self, connectors: list[MarketplaceConnector], demo_mode: bool):
        self._connectors = {c.marketplace: c for c in connectors}
        self.demo_mode = demo_mode

    def all(self) -> list[MarketplaceConnector]:
        return list(self._connectors.values())

    def get(self, marketplace: Marketplace) -> MarketplaceConnector:
        return self._connectors[marketplace]

    def select(self, marketplaces: list[Marketplace] | None) -> list[MarketplaceConnector]:
        if not marketplaces:
            return self.all()
        return [c for m, c in self._connectors.items() if m in marketplaces]


def build_registry(settings: Settings) -> ConnectorRegistry:
    if settings.mock_mode:
        return ConnectorRegistry([MockConnector(m) for m in Marketplace], demo_mode=True)
    return ConnectorRegistry([
        ShopeeAffiliateConnector(settings.shopee_affiliate_app_id, settings.shopee_affiliate_secret,
                                 settings.shopee_affiliate_endpoint, settings.connector_timeout_seconds),
        LazadaConnector(),
        TikTokShopConnector(),
    ], demo_mode=False)


_registry: ConnectorRegistry | None = None


def get_registry() -> ConnectorRegistry:
    global _registry
    if _registry is None:
        _registry = build_registry(get_settings())
    return _registry


def set_registry(registry: ConnectorRegistry | None) -> None:
    global _registry
    _registry = registry
