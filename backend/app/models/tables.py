"""SQLAlchemy ORM models. Schema source of truth = Alembic migrations (alembic/versions);
tests assert that the two stay identical."""
from datetime import datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
    func,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


def _pk() -> Mapped[int]:
    return mapped_column(BigInteger, primary_key=True, autoincrement=True)


def _now() -> Mapped[datetime]:
    return mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = _pk()
    display_name: Mapped[str] = mapped_column(String(100))
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    created_at: Mapped[datetime] = _now()


class MarketplaceSource(Base):
    __tablename__ = "marketplace_sources"
    id: Mapped[int] = _pk()
    code: Mapped[str] = mapped_column(String(32))
    name: Mapped[str] = mapped_column(String(100))
    is_mock: Mapped[bool] = mapped_column(Boolean, default=False)
    __table_args__ = (UniqueConstraint("code", "is_mock", name="uq_sources_code_mock"),)


class Seller(Base):
    __tablename__ = "sellers"
    id: Mapped[int] = _pk()
    source_id: Mapped[int] = mapped_column(ForeignKey("marketplace_sources.id", ondelete="CASCADE"))
    external_id: Mapped[str] = mapped_column(String(128))
    name: Mapped[str] = mapped_column(String(255))
    rating: Mapped[float | None] = mapped_column(Float)
    is_official_store: Mapped[bool | None] = mapped_column(Boolean)
    location: Mapped[str | None] = mapped_column(String(120))
    updated_at: Mapped[datetime] = _now()
    __table_args__ = (UniqueConstraint("source_id", "external_id", name="uq_sellers_source_ext"),)


class Product(Base):
    __tablename__ = "products"
    id: Mapped[int] = _pk()
    product_key: Mapped[str] = mapped_column(String(300), unique=True)
    title: Mapped[str] = mapped_column(String(300))
    brand: Mapped[str | None] = mapped_column(String(80), index=True)
    category: Mapped[str | None] = mapped_column(String(60), index=True)
    model: Mapped[str | None] = mapped_column(String(80))
    image_url: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = _now()
    variants: Mapped[list["ProductVariant"]] = relationship(back_populates="product")


class ProductVariant(Base):
    __tablename__ = "product_variants"
    id: Mapped[int] = _pk()
    product_id: Mapped[int] = mapped_column(ForeignKey("products.id", ondelete="CASCADE"), index=True)
    variant_key: Mapped[str] = mapped_column(String(500), unique=True)
    title: Mapped[str] = mapped_column(String(300))
    attributes: Mapped[dict] = mapped_column(JSONB)
    gtin: Mapped[str | None] = mapped_column(String(20), index=True)
    image_url: Mapped[str | None] = mapped_column(Text)
    search_query: Mapped[str] = mapped_column(String(300))   # query used to refresh offers
    created_at: Mapped[datetime] = _now()
    product: Mapped[Product] = relationship(back_populates="variants")
    offers: Mapped[list["MarketplaceOffer"]] = relationship(back_populates="variant")


class MarketplaceOffer(Base):
    __tablename__ = "marketplace_offers"
    id: Mapped[int] = _pk()
    variant_id: Mapped[int] = mapped_column(ForeignKey("product_variants.id", ondelete="CASCADE"))
    source_id: Mapped[int] = mapped_column(ForeignKey("marketplace_sources.id", ondelete="CASCADE"))
    seller_id: Mapped[int] = mapped_column(ForeignKey("sellers.id", ondelete="CASCADE"))
    external_id: Mapped[str] = mapped_column(String(128))       # the marketplace's own listing id
    url: Mapped[str] = mapped_column(Text)                      # original listing URL, unmodified
    title: Mapped[str] = mapped_column(Text)
    raw: Mapped[dict] = mapped_column(JSONB)                    # RawOffer as retrieved
    price: Mapped[dict] = mapped_column(JSONB)                  # PriceBreakdown (quantity 1)
    match: Mapped[dict] = mapped_column(JSONB)                  # MatchResult vs. the variant
    listed_price: Mapped[int] = mapped_column(BigInteger)
    final_price: Mapped[int | None] = mapped_column(BigInteger)
    price_confidence: Mapped[str] = mapped_column(String(16))
    is_mock: Mapped[bool] = mapped_column(Boolean, default=False)
    retrieved_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = _now()
    variant: Mapped[ProductVariant] = relationship(back_populates="offers")
    source: Mapped[MarketplaceSource] = relationship()
    seller: Mapped[Seller] = relationship()
    __table_args__ = (
        UniqueConstraint("source_id", "external_id", name="uq_offers_source_ext"),
        Index("ix_offers_variant_price", "variant_id", "final_price"),
    )


class PriceHistory(Base):
    __tablename__ = "price_history"
    id: Mapped[int] = _pk()
    offer_id: Mapped[int] = mapped_column(ForeignKey("marketplace_offers.id", ondelete="CASCADE"))
    listed_price: Mapped[int] = mapped_column(BigInteger)
    final_price: Mapped[int | None] = mapped_column(BigInteger)
    price_confidence: Mapped[str] = mapped_column(String(16))
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    __table_args__ = (Index("ix_price_history_offer_time", "offer_id", "recorded_at"),)


class Search(Base):
    __tablename__ = "searches"
    id: Mapped[int] = _pk()
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    search_type: Mapped[str] = mapped_column(String(8))
    query: Mapped[str] = mapped_column(String(2000))
    spec: Mapped[dict] = mapped_column(JSONB)
    result_count: Mapped[int] = mapped_column(Integer, default=0)
    lowest_price: Mapped[int | None] = mapped_column(BigInteger)
    created_at: Mapped[datetime] = _now()
    __table_args__ = (Index("ix_searches_user_time", "user_id", "created_at"),)


class ProductView(Base):
    __tablename__ = "product_views"
    id: Mapped[int] = _pk()
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    variant_id: Mapped[int] = mapped_column(ForeignKey("product_variants.id", ondelete="CASCADE"))
    viewed_at: Mapped[datetime] = _now()
    __table_args__ = (UniqueConstraint("user_id", "variant_id", name="uq_views_user_variant"),)


class Wishlist(Base):
    __tablename__ = "wishlists"
    id: Mapped[int] = _pk()
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    product_id: Mapped[int] = mapped_column(ForeignKey("products.id", ondelete="CASCADE"))
    variant_id: Mapped[int] = mapped_column(ForeignKey("product_variants.id", ondelete="CASCADE"))
    current_lowest_price: Mapped[int | None] = mapped_column(BigInteger)
    previous_lowest_price: Mapped[int | None] = mapped_column(BigInteger)
    last_checked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = _now()
    __table_args__ = (UniqueConstraint("user_id", "variant_id", name="uq_wishlist_user_variant"),)


class PriceAlert(Base):
    __tablename__ = "price_alerts"
    id: Mapped[int] = _pk()
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    product_id: Mapped[int] = mapped_column(ForeignKey("products.id", ondelete="CASCADE"))
    variant_id: Mapped[int] = mapped_column(ForeignKey("product_variants.id", ondelete="CASCADE"))
    target_price: Mapped[int] = mapped_column(BigInteger)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    last_notified_price: Mapped[int | None] = mapped_column(BigInteger)
    last_notified_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = _now()
    __table_args__ = (UniqueConstraint("user_id", "variant_id", name="uq_alert_user_variant"),)


class Notification(Base):
    __tablename__ = "notifications"
    id: Mapped[int] = _pk()
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    alert_id: Mapped[int | None] = mapped_column(ForeignKey("price_alerts.id", ondelete="SET NULL"))
    offer_id: Mapped[int | None] = mapped_column(ForeignKey("marketplace_offers.id", ondelete="SET NULL"))
    variant_id: Mapped[int | None] = mapped_column(ForeignKey("product_variants.id", ondelete="SET NULL"))
    title: Mapped[str] = mapped_column(String(200))
    body: Mapped[str] = mapped_column(Text)
    url: Mapped[str | None] = mapped_column(Text)
    price: Mapped[int | None] = mapped_column(BigInteger)
    is_mock: Mapped[bool] = mapped_column(Boolean, default=False)
    # pending -> pushed (FCM accepted) | in_app (no push channel; shown in-app only) | failed
    status: Mapped[str] = mapped_column(String(16), default="pending")
    dedupe_key: Mapped[str] = mapped_column(String(200), unique=True)
    created_at: Mapped[datetime] = _now()
    sent_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    read_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    __table_args__ = (Index("ix_notifications_user_time", "user_id", "created_at"),)


class DeviceToken(Base):
    __tablename__ = "device_tokens"
    id: Mapped[int] = _pk()
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    token: Mapped[str] = mapped_column(Text, unique=True)
    platform: Mapped[str] = mapped_column(String(16), default="android")
    updated_at: Mapped[datetime] = _now()
