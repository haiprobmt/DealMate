"""Tính giá mua cuối cùng — hoàn toàn tất định, không dùng AI.

    final_price = listed_price × quantity − voucher_discount + shipping_fee + mandatory_fees

Rules:
* At most ONE store voucher and ONE platform voucher are applied (no double counting).
* The store voucher is applied first; a percent platform voucher is computed on the remainder.
* Minimum-spend is checked against the listed subtotal.
* Unknown shipping is never treated as 0đ: the offer becomes ``incomplete`` with no final price.
* Any applied voucher that is unverified/conditional, or estimated shipping, makes it ``estimated``.
"""
from app.schemas.domain import (
    AppliedVoucher,
    PriceBreakdown,
    PriceConfidence,
    RawOffer,
    ShippingStatus,
    SkippedVoucher,
    Voucher,
)
from app.utils.text import format_vnd


def voucher_discount(voucher: Voucher, amount: int) -> int:
    if voucher.kind == "fixed":
        discount = voucher.value
    else:
        discount = amount * min(voucher.value, 100) // 100
        if voucher.max_discount is not None:
            discount = min(discount, voucher.max_discount)
    return max(0, min(discount, amount))


def is_estimated(voucher: Voucher) -> bool:
    return (not voucher.verified) or bool(voucher.requires)


def _pick_best(vouchers: list[Voucher], subtotal: int, amount: int,
               skipped: list[SkippedVoucher]) -> AppliedVoucher | None:
    """Best eligible voucher of one source. Verified vouchers win ties."""
    best: AppliedVoucher | None = None
    candidates: list[AppliedVoucher] = []
    for v in vouchers:
        if subtotal < v.min_spend:
            skipped.append(SkippedVoucher(
                voucher=v, reason=f"Chưa đạt đơn tối thiểu {format_vnd(v.min_spend)}."))
            continue
        d = voucher_discount(v, amount)
        if d <= 0:
            continue
        candidates.append(AppliedVoucher(voucher=v, discount=d, estimated=is_estimated(v)))
    for cand in candidates:
        if best is None or (cand.discount, not cand.estimated) > (best.discount, not best.estimated):
            best = cand
    for cand in candidates:
        if cand is not best:
            skipped.append(SkippedVoucher(
                voucher=cand.voucher, reason="Không cộng dồn: đã dùng voucher cùng loại tốt hơn."))
    return best


def compute_price(offer: RawOffer, quantity: int = 1) -> PriceBreakdown:
    if quantity < 1:
        raise ValueError("quantity must be >= 1")
    subtotal = offer.listed_price * quantity
    skipped: list[SkippedVoucher] = []
    applied: list[AppliedVoucher] = []
    notes: list[str] = []

    remaining = subtotal
    for source in ("store", "platform"):
        best = _pick_best([v for v in offer.vouchers if v.source == source], subtotal, remaining, skipped)
        if best:
            applied.append(best)
            remaining -= best.discount
    discount = subtotal - remaining
    fees = sum(f.amount for f in offer.mandatory_fees)
    known_subtotal = remaining + fees

    shipping_unknown = offer.shipping_fee is None or offer.shipping_status == ShippingStatus.UNKNOWN
    estimated = any(a.estimated for a in applied) or offer.shipping_status == ShippingStatus.ESTIMATED

    if shipping_unknown:
        confidence, final, shipping = PriceConfidence.INCOMPLETE, None, None
        notes.append("Chưa có phí vận chuyển nên chưa tính được giá cuối.")
    else:
        shipping = offer.shipping_fee
        final = known_subtotal + shipping
        confidence = PriceConfidence.ESTIMATED if estimated else PriceConfidence.VERIFIED
    for a in applied:
        if a.estimated:
            cond = f" ({a.voucher.requires})" if a.voucher.requires else ""
            notes.append(f"Voucher giảm {format_vnd(a.discount)} là ước tính{cond}, "
                         "cần kiểm tra lại ở trang thanh toán.")
    if offer.shipping_status == ShippingStatus.ESTIMATED and not shipping_unknown:
        notes.append("Phí vận chuyển là ước tính, có thể thay đổi theo địa chỉ nhận hàng.")

    return PriceBreakdown(
        currency=offer.currency, quantity=quantity, listed_price=offer.listed_price, subtotal=subtotal,
        voucher_discount=discount, applied_vouchers=applied, skipped_vouchers=skipped,
        shipping_fee=shipping, shipping_status=ShippingStatus.UNKNOWN if shipping_unknown
        else offer.shipping_status, mandatory_fees=fees, final_price=final,
        known_subtotal=known_subtotal, confidence=confidence, notes=notes)


_TIER = {PriceConfidence.VERIFIED: 0, PriceConfidence.ESTIMATED: 1, PriceConfidence.INCOMPLETE: 2}


def ranking_key(price: PriceBreakdown, tiebreak: str = "") -> tuple:
    """Verified final prices first (ascending), then estimated, then incomplete (by known part)."""
    amount = price.final_price if price.final_price is not None else price.known_subtotal
    return (_TIER[price.confidence], amount, tiebreak)
