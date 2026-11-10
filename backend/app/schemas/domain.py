"""Các kiểu dữ liệu nghiệp vụ dùng chung giữa connector, engine và API."""
from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field


class Marketplace(StrEnum):
    SHOPEE = "shopee"
    LAZADA = "lazada"
    TIKTOK_SHOP = "tiktok_shop"


MARKETPLACE_NAMES = {
    Marketplace.SHOPEE: "Shopee",
    Marketplace.LAZADA: "Lazada",
    Marketplace.TIKTOK_SHOP: "TikTok Shop",
}


class MatchType(StrEnum):
    EXACT = "exact"
    LIKELY = "likely"
    SIMILAR = "similar"
    DIFFERENT = "different"


class PriceConfidence(StrEnum):
    VERIFIED = "verified"      # every component known and confirmed by the source
    ESTIMATED = "estimated"    # all components known, at least one is an estimate
    INCOMPLETE = "incomplete"  # at least one component unknown -> no final price


class ShippingStatus(StrEnum):
    VERIFIED = "verified"
    ESTIMATED = "estimated"
    UNKNOWN = "unknown"


class ProductAttributes(BaseModel):
    """Canonical, marketplace-independent description. ``None`` always means *unknown*."""

    model_config = ConfigDict(extra="forbid")

    brand: str | None = None
    category: str | None = None
    product_line: str | None = None       # normalized descriptive tokens, e.g. "perfect uv sunscreen"
    model: str | None = None              # e.g. "hd15", "iphone 16 pro", "hd9200/90"
    sku: str | None = None
    gtin: str | None = None
    volume_ml: float | None = None
    weight_g: float | None = None
    count: int | None = None              # pieces inside one pack (diapers, tablets...)
    pack_quantity: int | None = None      # number of packs sold together (combo 2, x3...)
    size: str | None = None               # NB/S/M/L/XL...
    storage_gb: int | None = None
    color: str | None = None
    condition: str | None = None          # new | used | refurbished
    origin: str | None = None             # official (chính hãng VN) | imported (xách tay/nhập khẩu)
    traits: dict[str, str] = Field(default_factory=dict)  # e.g. {"formula": "milk", "diaper_type": "pants"}


class MatchResult(BaseModel):
    match_type: MatchType
    confidence: float = Field(ge=0, le=1)
    matched_attributes: list[str] = Field(default_factory=list)
    different_attributes: list[str] = Field(default_factory=list)
    unknown_attributes: list[str] = Field(default_factory=list)
    reason: str = ""
    decided_by: str = "rules"             # rules | ai


class Voucher(BaseModel):
    code: str | None = None
    source: str = Field(pattern="^(store|platform)$")
    kind: str = Field(pattern="^(fixed|percent)$")
    value: int = Field(gt=0)              # VND for fixed, whole percent for percent
    max_discount: int | None = None       # cap for percent vouchers (VND)
    min_spend: int = 0
    verified: bool = False                # confirmed applicable to any buyer by the source
    requires: str | None = None           # e.g. "new_user", "bank_card" -> never verified
    description: str | None = None


class Fee(BaseModel):
    name: str
    amount: int = Field(ge=0)


class SellerInfo(BaseModel):
    external_id: str
    name: str
    rating: float | None = None
    is_official_store: bool | None = None  # only what the marketplace itself labels (Mall/LazMall)
    location: str | None = None


class RawOffer(BaseModel):
    """One marketplace listing exactly as retrieved by a connector."""

    marketplace: Marketplace
    external_id: str
    title: str
    url: str
    image_url: str | None = None
    seller: SellerInfo
    currency: str = "VND"
    listed_price: int = Field(gt=0)
    original_price: int | None = None     # crossed-out price shown by the marketplace
    vouchers: list[Voucher] = Field(default_factory=list)
    shipping_fee: int | None = None       # None == unknown, never treated as zero
    shipping_status: ShippingStatus = ShippingStatus.UNKNOWN
    mandatory_fees: list[Fee] = Field(default_factory=list)
    rating: float | None = None
    rating_count: int | None = None
    sold_count: int | None = None
    in_stock: bool | None = None
    variant_label: str | None = None      # variant text given by the marketplace, if any
    gtin: str | None = None
    retrieved_at: datetime
    is_mock: bool = False


class AppliedVoucher(BaseModel):
    voucher: Voucher
    discount: int
    estimated: bool


class SkippedVoucher(BaseModel):
    voucher: Voucher
    reason: str


class PriceBreakdown(BaseModel):
    currency: str = "VND"
    quantity: int = 1
    listed_price: int
    subtotal: int
    voucher_discount: int
    applied_vouchers: list[AppliedVoucher] = Field(default_factory=list)
    skipped_vouchers: list[SkippedVoucher] = Field(default_factory=list)
    shipping_fee: int | None
    shipping_status: ShippingStatus
    mandatory_fees: int
    final_price: int | None               # None when confidence == incomplete
    known_subtotal: int                   # everything that IS known (never shown as the final price)
    confidence: PriceConfidence
    notes: list[str] = Field(default_factory=list)


class ProductQuery(BaseModel):
    """Structured interpretation of what the user wants. ``None`` == not specified."""

    model_config = ConfigDict(extra="forbid")

    brand: str | None = None
    product_name: str | None = None
    model: str | None = None
    category: str | None = None
    variant: str | None = None
    volume: str | None = None             # as written, e.g. "60ml"
    weight: str | None = None             # e.g. "3.6kg"
    size: str | None = None
    quantity: int | None = None           # packs wanted
    count: int | None = None              # pieces per pack
    storage: str | None = None            # e.g. "256GB"
    color: str | None = None
    max_price: int | None = None
    min_price: int | None = None
    currency: str = "VND"
    constraints: list[str] = Field(default_factory=list)
    uncertain_fields: list[str] = Field(default_factory=list)
    search_keywords: str | None = None    # text to send to marketplaces
