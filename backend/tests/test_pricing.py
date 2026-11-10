from datetime import UTC, datetime

import pytest

from app.schemas.domain import (
    Fee,
    Marketplace,
    PriceConfidence,
    RawOffer,
    SellerInfo,
    ShippingStatus,
    Voucher,
)
from app.services.pricing import compute_price, ranking_key


def offer(price=359_000, shipping=15_000, status=ShippingStatus.VERIFIED, vouchers=(), fees=()):
    return RawOffer(marketplace=Marketplace.LAZADA, external_id="x", title="t", url="https://example.invalid/x",
                    seller=SellerInfo(external_id="s", name="shop"), listed_price=price, shipping_fee=shipping,
                    shipping_status=status if shipping is not None else ShippingStatus.UNKNOWN,
                    vouchers=list(vouchers), mandatory_fees=list(fees), retrieved_at=datetime.now(UTC))


def v(source, kind, value, min_spend=0, verified=True, max_discount=None, requires=None):
    return Voucher(source=source, kind=kind, value=value, min_spend=min_spend, verified=verified,
                   max_discount=max_discount, requires=requires)


def test_plain_price_is_listed_plus_shipping():
    p = compute_price(offer(300_000, 20_000))
    assert (p.final_price, p.voucher_discount, p.confidence) == (320_000, 0, PriceConfidence.VERIFIED)
    assert p.listed_price == 300_000, "the original listed price is preserved"


def test_store_and_platform_voucher_stack_once_each_with_cap():
    p = compute_price(offer(359_000, 15_000, vouchers=[
        v("store", "fixed", 30_000, 350_000), v("platform", "percent", 8, 300_000, max_discount=25_000)]))
    # 359000 - 30000 = 329000 ; 8% of 329000 = 26320 -> capped 25000 ; + 15000 shipping
    assert p.voucher_discount == 55_000 and p.final_price == 319_000
    assert p.confidence == PriceConfidence.VERIFIED and len(p.applied_vouchers) == 2


def test_two_store_vouchers_are_not_double_counted():
    p = compute_price(offer(219_000, 0, vouchers=[v("store", "fixed", 10_000, 200_000), v("store", "fixed", 15_000, 200_000)]))
    assert p.voucher_discount == 15_000
    assert [s.reason for s in p.skipped_vouchers] == ["Không cộng dồn: đã dùng voucher cùng loại tốt hơn."]


def test_minimum_spend_not_met_voucher_is_skipped():
    p = compute_price(offer(219_000, 0, vouchers=[v("store", "fixed", 15_000, 400_000)]))
    assert p.voucher_discount == 0 and p.final_price == 219_000
    assert "Chưa đạt đơn tối thiểu 400.000đ." in p.skipped_vouchers[0].reason


def test_minimum_spend_met_through_quantity():
    p = compute_price(offer(219_000, 0, vouchers=[v("store", "fixed", 15_000, 400_000)]), quantity=2)
    assert p.subtotal == 438_000 and p.final_price == 423_000


def test_unverified_or_conditional_voucher_makes_price_estimated():
    for voucher in (v("platform", "fixed", 40_000, verified=False), v("platform", "fixed", 40_000, requires="khách mới")):
        p = compute_price(offer(349_000, 0, vouchers=[voucher]))
        assert p.final_price == 309_000 and p.confidence == PriceConfidence.ESTIMATED
        assert p.applied_vouchers[0].estimated and p.notes


def test_estimated_shipping_makes_price_estimated():
    p = compute_price(offer(100_000, 30_000, ShippingStatus.ESTIMATED))
    assert p.final_price == 130_000 and p.confidence == PriceConfidence.ESTIMATED


def test_unknown_shipping_is_never_zero():
    p = compute_price(offer(329_000, None))
    assert p.final_price is None and p.shipping_fee is None
    assert p.confidence == PriceConfidence.INCOMPLETE and p.known_subtotal == 329_000


def test_shipping_fee_given_but_flagged_unknown_is_incomplete():
    p = compute_price(offer(100_000, 0, ShippingStatus.UNKNOWN))
    assert p.final_price is None and p.confidence == PriceConfidence.INCOMPLETE


def test_mandatory_fees_are_added():
    p = compute_price(offer(1_650_000, 0, fees=[Fee(name="Phí xử lý", amount=20_000)]))
    assert p.mandatory_fees == 20_000 and p.final_price == 1_670_000


def test_discount_never_exceeds_subtotal():
    p = compute_price(offer(50_000, 0, vouchers=[v("store", "fixed", 80_000), v("platform", "percent", 50)]))
    assert p.voucher_discount == 50_000 and p.final_price == 0


def test_percent_over_100_is_clamped_and_quantity_validated():
    assert compute_price(offer(100_000, 0, vouchers=[v("store", "percent", 150)])).final_price == 0
    with pytest.raises(ValueError):
        compute_price(offer(), quantity=0)


def test_ranking_tiers_verified_then_estimated_then_incomplete():
    verified = compute_price(offer(345_000, 0))
    estimated = compute_price(offer(349_000, 0, vouchers=[v("platform", "fixed", 40_000, verified=False)]))
    incomplete = compute_price(offer(259_000, None))
    ordered = sorted([incomplete, estimated, verified], key=ranking_key)
    assert [p.confidence.value for p in ordered] == ["verified", "estimated", "incomplete"]
    assert estimated.final_price < verified.final_price, "a cheaper estimate still ranks below a verified price"
    cheaper = compute_price(offer(300_000, 0))
    assert sorted([verified, cheaper], key=ranking_key)[0] is cheaper
