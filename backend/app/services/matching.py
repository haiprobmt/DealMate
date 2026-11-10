"""Stage A — so khớp sản phẩm tất định (deterministic).

Two entry points share one attribute comparison:

* ``match_products(a, b)``  — are two listings the same purchasable item?
* ``match_query(query, listing)`` — does a listing satisfy what the user asked for?

Hard rules (never overridden, not even by the AI stage):
  different brand/model/volume/weight/count/size/storage/pack => never ``exact``;
  an attribute known on one side only => never ``exact`` (it stays *unknown*).
"""
from rapidfuzz import fuzz

from app.schemas.domain import MatchResult, MatchType, ProductAttributes

NUMERIC_TOLERANCE = 0.011  # 1.1% — absorbs "3.6kg" vs "3600g" rounding only
BASE_DEFAULT_TRAITS = {"line_variant"}
VARIANT_FIELDS = {"volume_ml", "weight_g", "count", "size", "storage_gb"}
SCALAR_FIELDS = ["model", "volume_ml", "weight_g", "count", "size", "storage_gb", "color", "origin"]
LABELS = {
    "brand": "thương hiệu", "category": "loại sản phẩm", "model": "model", "volume_ml": "dung tích",
    "weight_g": "khối lượng", "count": "số miếng/viên", "size": "size", "storage_gb": "bộ nhớ",
    "color": "màu", "origin": "nguồn hàng (chính hãng/nhập khẩu)", "condition": "tình trạng",
    "pack_quantity": "số lượng trong combo", "gtin": "mã vạch", "product_line": "dòng sản phẩm",
}


def label_of(field: str) -> str:
    if field.startswith("trait:"):
        return {"formula": "dạng (sữa/gel/xịt)", "line_variant": "phiên bản dòng",
                "diaper_type": "loại tã (quần/dán)", "detergent_form": "dạng (nước/bột/viên)",
                "washer_type": "loại máy giặt", "packaging": "bao bì (túi/chai)"}.get(field[6:], field[6:])
    return LABELS.get(field, field)


def _equal(field: str, x, y) -> bool:
    if field in ("volume_ml", "weight_g"):
        return abs(x - y) <= NUMERIC_TOLERANCE * max(x, y)
    if field == "model":
        return str(x).replace(" ", "") == str(y).replace(" ", "")
    return x == y


class Comparison:
    def __init__(self) -> None:
        self.matched: list[str] = []
        self.different: list[str] = []
        self.unknown_a: list[str] = []   # known on b, unknown on a
        self.unknown_b: list[str] = []   # known on a, unknown on b

    def add(self, field: str, x, y) -> None:
        if x is None and y is None:
            return
        if x is None:
            self.unknown_a.append(field)
        elif y is None:
            self.unknown_b.append(field)
        elif _equal(field, x, y):
            self.matched.append(field)
        else:
            self.different.append(field)


def compare_attributes(a: ProductAttributes, b: ProductAttributes,
                       for_listings: bool = True) -> Comparison:
    c = Comparison()
    c.add("brand", a.brand, b.brand)
    c.add("category", a.category, b.category)
    for field in SCALAR_FIELDS:
        c.add(field, getattr(a, field), getattr(b, field))
    for key in sorted(set(a.traits) | set(b.traits)):
        x, y = a.traits.get(key), b.traits.get(key)
        if key in BASE_DEFAULT_TRAITS and (x is None) != (y is None) and for_listings:
            # Modifiers such as "Mild"/"Brightening": a listing that does not carry the
            # modifier is the base version, so one-sided presence is a real difference.
            c.different.append(f"trait:{key}")
            continue
        c.add(f"trait:{key}", x, y)
    # An unlabeled listing is sold as new; only explicit used/refurbished creates a difference.
    if (a.condition or "new") != (b.condition or "new"):
        c.different.append("condition")
    return c


def line_similarity(a: ProductAttributes, b: ProductAttributes) -> float | None:
    if not a.product_line or not b.product_line:
        return None
    return float(fuzz.token_set_ratio(a.product_line, b.product_line))


def line_relation(a: ProductAttributes, b: ProductAttributes) -> str:
    """same | subset (one side says more) | conflict (each side says something else) | unknown."""
    ta, tb = set((a.product_line or "").split()), set((b.product_line or "").split())
    if not ta and not tb:
        return "both_empty"
    if not ta or not tb:
        return "unknown"
    if ta == tb:
        return "same"
    if ta < tb or tb < ta:
        return "subset"
    # Typo-level differences ("skin care" vs "skincare") still count as the same line.
    if fuzz.ratio("".join(sorted(ta)), "".join(sorted(tb))) >= 90:
        return "same"
    return "conflict"


def _result(match_type: MatchType, confidence: float, c: Comparison, reason: str,
            unknown: list[str]) -> MatchResult:
    return MatchResult(match_type=match_type, confidence=round(min(max(confidence, 0.0), 1.0), 2),
                       matched_attributes=c.matched, different_attributes=c.different,
                       unknown_attributes=unknown, reason=reason)


def _names(fields: list[str]) -> str:
    return ", ".join(label_of(f) for f in fields)


def match_products(a: ProductAttributes, b: ProductAttributes) -> MatchResult:
    c = compare_attributes(a, b)
    pack_a, pack_b = a.pack_quantity or 1, b.pack_quantity or 1
    if pack_a != pack_b:
        c.different.append("pack_quantity")
    elif a.pack_quantity or b.pack_quantity:
        c.matched.append("pack_quantity")
    unknown = c.unknown_a + c.unknown_b
    sim = line_similarity(a, b)

    if a.gtin and b.gtin:
        if a.gtin != b.gtin:
            c.different.append("gtin")
            return _result(MatchType.DIFFERENT, 0.99, c, "Mã vạch khác nhau.", unknown)
        c.matched.append("gtin")
        if not c.different:
            return _result(MatchType.EXACT, 0.99, c, "Trùng mã vạch (GTIN).", unknown)

    if "brand" in c.different:
        return _result(MatchType.DIFFERENT, 0.97, c, "Khác thương hiệu.", unknown)
    if (a.category == "accessory") != (b.category == "accessory"):
        c.different.append("category")
        return _result(MatchType.DIFFERENT, 0.95, c, "Một bên là phụ kiện.", unknown)
    if "category" in c.different:
        return _result(MatchType.DIFFERENT, 0.95, c, "Khác loại sản phẩm.", unknown)

    brand_known = "brand" in c.matched
    if c.different:
        related = brand_known and (sim is None or sim >= 40 or "model" in c.matched)
        if not related:
            return _result(MatchType.DIFFERENT, 0.8, c, f"Khác {_names(c.different)}.", unknown)
        return _result(MatchType.SIMILAR, 0.9, c,
                       f"Cùng thương hiệu nhưng khác {_names(c.different)}.", unknown)

    if not brand_known:
        if sim is not None and sim >= 85:
            return _result(MatchType.SIMILAR, 0.4, c, "Tên gần giống nhưng không rõ thương hiệu.", unknown)
        return _result(MatchType.DIFFERENT, 0.6, c, "Không đủ thông tin để xác định cùng sản phẩm.", unknown)

    relation = line_relation(a, b)
    variant_hits = sum(1 for f in c.matched if f in VARIANT_FIELDS or f.startswith("trait:"))
    identity = ("model" in c.matched or relation == "same"
                or (relation == "both_empty" and variant_hits >= 2))
    blocking_unknown = [f for f in unknown if f != "category"]
    if relation == "conflict" and "model" not in c.matched:
        c.different.append("product_line")
        return _result(MatchType.SIMILAR, 0.7, c, "Cùng thương hiệu nhưng khác dòng sản phẩm.", unknown)
    if identity and not blocking_unknown:
        confidence = 0.9 + (0.05 if "model" in c.matched else 0) + min(len(c.matched), 5) * 0.008
        return _result(MatchType.EXACT, confidence, c,
                       f"Trùng {_names([f for f in c.matched if f != 'category'])}.", unknown)
    if identity:
        return _result(MatchType.LIKELY, 0.7, c,
                       f"Trùng các thuộc tính đã biết, chưa rõ {_names(blocking_unknown)}.", unknown)
    return _result(MatchType.LIKELY, 0.55, c,
                   "Không có thuộc tính nào mâu thuẫn, nhưng một bên mô tả dòng sản phẩm chưa đủ rõ.",
                   unknown + ["product_line"])


def match_query(query: ProductAttributes, listing: ProductAttributes,
                listing_title_folded: str = "") -> MatchResult:
    """Does ``listing`` satisfy every attribute the user specified?
    Attributes the user did not mention are ignored (they separate variants, not relevance)."""
    c = compare_attributes(query, listing, for_listings=False)
    if query.pack_quantity:
        if (listing.pack_quantity or 1) != query.pack_quantity:
            c.different.append("pack_quantity")
        else:
            c.matched.append("pack_quantity")
    # The user asked for a volume but the listing states a weight (or vice versa): a different
    # number means a different size; the same number ("60ml" vs "60g") stays unknown.
    for asked, other in (("volume_ml", "weight_g"), ("weight_g", "volume_ml")):
        q_value, l_value = getattr(query, asked), getattr(listing, other)
        if asked in c.unknown_b and q_value and l_value and not _equal(asked, q_value, l_value):
            c.unknown_b.remove(asked)
            c.different.append(asked)
    # Only what the user asked for and the listing does not state counts as unknown.
    unknown = [f for f in c.unknown_b]
    # Listing explicitly used/refurbished while user did not ask for it -> a real difference
    # (already added by compare_attributes via condition).

    if listing.category == "accessory" and query.category != "accessory":
        c.different.append("category")
        return _result(MatchType.DIFFERENT, 0.95, c, "Đây là phụ kiện, không phải sản phẩm bạn tìm.", unknown)
    if "brand" in c.different:
        return _result(MatchType.DIFFERENT, 0.97, c, "Khác thương hiệu bạn tìm.", unknown)
    if "category" in c.different:
        return _result(MatchType.DIFFERENT, 0.95, c, "Không phải loại sản phẩm bạn tìm.", unknown)

    sim: float | None = None
    if query.product_line:
        haystack = " ".join(filter(None, [listing.product_line, listing_title_folded]))
        sim = float(fuzz.token_set_ratio(query.product_line, haystack)) if haystack else None

    if c.different:
        if query.brand and "brand" not in c.matched:
            return _result(MatchType.DIFFERENT, 0.7, c, f"Khác {_names(c.different)}.", unknown)
        return _result(MatchType.SIMILAR, 0.9, c, f"Khác {_names(c.different)} so với yêu cầu.", unknown)

    if query.brand and "brand" not in c.matched:
        return _result(MatchType.SIMILAR, 0.4, c, "Tin đăng không ghi rõ thương hiệu.", unknown)
    if not query.brand and not query.category and (sim is None or sim < 60):
        return _result(MatchType.DIFFERENT, 0.6, c, "Không khớp với từ khoá tìm kiếm.", unknown)
    if sim is not None and sim < 60:
        return _result(MatchType.SIMILAR, 0.6, c, "Khác dòng sản phẩm bạn tìm.", unknown + ["product_line"])
    if unknown:
        return _result(MatchType.LIKELY, 0.7, c, f"Tin đăng không ghi rõ {_names(unknown)}.", unknown)
    if sim is not None and sim < 80:
        return _result(MatchType.LIKELY, 0.6, c, "Tên dòng sản phẩm chỉ gần giống yêu cầu.", unknown)
    matched = [f for f in c.matched if f != "category"]
    confidence = 0.88 + min(len(matched), 5) * 0.02
    return _result(MatchType.EXACT, confidence, c,
                   f"Khớp {_names(matched)}." if matched else "Khớp yêu cầu tìm kiếm.", unknown)


def variant_key(attrs: ProductAttributes) -> str:
    """Stable fingerprint of a purchasable variant (used to cluster and to store variants)."""
    line = attrs.model or " ".join(sorted((attrs.product_line or "").split()))
    traits = ",".join(f"{k}={v}" for k, v in sorted(attrs.traits.items()))
    parts = [attrs.brand, attrs.category, line, attrs.volume_ml, attrs.weight_g, attrs.count, attrs.size,
             attrs.storage_gb, attrs.color, attrs.origin, attrs.condition or "new",
             attrs.pack_quantity or 1, traits, attrs.gtin]
    return "|".join("?" if p is None else str(p) for p in parts)


def product_key(attrs: ProductAttributes) -> str:
    line = attrs.model or " ".join(sorted((attrs.product_line or "").split()))
    return "|".join(str(p or "?") for p in (attrs.brand, attrs.category, line))


def same_variant(a: ProductAttributes, b: ProductAttributes) -> bool:
    """Clustering predicate: identical fingerprint, or deterministically exact."""
    return variant_key(a) == variant_key(b) or match_products(a, b).match_type == MatchType.EXACT


SPECIFICITY_FIELDS = {"model", "volume_ml", "weight_g", "count", "size", "storage_gb", "color"}


def is_vaguer_description(a: ProductAttributes, b: ProductAttributes) -> list[str]:
    """Attributes that ``b`` states and ``a`` leaves out, when nothing else conflicts — i.e. ``a``
    is a vaguer listing that *could* be ``b`` (or something else). Empty list otherwise."""
    c = compare_attributes(a, b)
    if c.different or "brand" not in c.matched or (a.pack_quantity or 1) != (b.pack_quantity or 1):
        return []
    missing = [f for f in c.unknown_a if f in SPECIFICITY_FIELDS or f.startswith("trait:")]
    ta, tb = set((a.product_line or "").split()), set((b.product_line or "").split())
    if ta < tb and "model" not in c.matched:
        missing.append("product_line")
    elif ta != tb and not ta <= tb and "model" not in c.matched:
        return []          # each names a different line: not a vaguer description of b
    return missing
