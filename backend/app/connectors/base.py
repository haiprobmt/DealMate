"""Khung connector sàn TMĐT — độc lập nhà cung cấp.

Rules every connector must follow:
* use only official / authorized APIs and feeds; never bypass login, CAPTCHA or anti-bot controls;
* never return invented listings — raise instead;
* keep the marketplace's own listing id and URL; stamp ``retrieved_at``;
* set ``is_mock=True`` on every offer if (and only if) the data is not real.
"""
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import UTC, datetime

from app.schemas.domain import MARKETPLACE_NAMES, Marketplace, RawOffer


class ConnectorError(Exception):
    """The marketplace could not be queried (network, auth, upstream error)."""


class ConnectorNotConfigured(ConnectorError):
    """Credentials / marketplace approval are missing."""


class ConnectorNotImplemented(ConnectorError):
    """No authorized integration exists yet for this marketplace."""


@dataclass
class ConnectorHealth:
    status: str                 # available | not_configured | not_implemented | error
    message: str
    verified_live: bool = False  # True only after a real API response was validated
    checked_at: datetime = field(default_factory=lambda: datetime.now(UTC))


class MarketplaceConnector(ABC):
    marketplace: Marketplace
    connector_name: str = "base"
    is_mock: bool = False

    @property
    def marketplace_name(self) -> str:
        return MARKETPLACE_NAMES[self.marketplace]

    @abstractmethod
    async def search_products(self, query: str, limit: int = 30) -> list[RawOffer]:
        """Keyword search. Must raise ConnectorError rather than return made-up data."""

    @abstractmethod
    async def get_product_details(self, product_id: str) -> RawOffer | None:
        """Fetch one listing by the marketplace's own identifier; None when it does not exist."""

    @abstractmethod
    async def get_product_url(self, product_id: str) -> str:
        """Canonical URL of the original listing."""

    @abstractmethod
    async def health_check(self) -> ConnectorHealth:
        ...
