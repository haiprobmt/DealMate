"""Chuẩn hoá tiêu đề sản phẩm trên sàn thành thuộc tính chuẩn (ProductAttributes).

Deterministic and conservative: an attribute is only set when the text states it.
Anything not stated stays ``None`` (unknown) — it is never guessed.
"""
import re
import unicodedata

from app.schemas.domain import ProductAttributes, ProductQuery
from app.utils.text import fold

# --- Lexicons ---------------------------------------------------------------------
BRAND_ALIASES: dict[str, str] = {
    "anessa": "anessa", "dyson": "dyson", "merries": "merries", "ariel": "ariel",
    "apple": "apple", "iphone": "apple", "ipad": "apple", "macbook": "apple", "airpods": "apple",
    "philips": "philips", "phillips": "philips", "biore": "biore", "la roche posay": "la roche-posay",
    "la roche-posay": "la roche-posay", "omo": "omo", "bobby": "bobby", "pampers": "pampers",
    "huggies": "huggies", "moony": "moony", "samsung": "samsung", "xiaomi": "xiaomi",
    "lock&lock": "lock&lock", "locknlock": "lock&lock", "sunhouse": "sunhouse", "tefal": "tefal",
    "panasonic": "panasonic", "sunplay": "sunplay", "skin aqua": "skin aqua", "tide": "tide",
    "downy": "downy", "comfort": "comfort", "kangaroo": "kangaroo", "cosori": "cosori",
    "oppo": "oppo", "vivo": "vivo", "realme": "realme", "sony": "sony", "lg": "lg",
    "goo.n": "goo.n", "goon": "goo.n", "molfix": "molfix", "tresemme": "tresemme",
    "sharp": "sharp", "toshiba": "toshiba", "electrolux": "electrolux", "surf": "surf",
    "innisfree": "innisfree", "vichy": "vichy", "cerave": "cerave", "senka": "senka",
}

# Brands that are scents/co-brands inside other brands' titles ("Ariel hương Downy").
_COBRAND_CONTEXT = re.compile(r"\b(huong|voi|with)\s+(downy|comfort)\b")

CATEGORY_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("accessory", re.compile(
        r"\b(op lung|op dien thoai|kinh cuong luc|cuong luc|mieng dan|cap sac|cu sac|bao da|"
        r"gia do|phu kien|tui dung|vo hop|mo hinh|dau say|luoi loc|bo loc|giay nen|khay nuong|"
        r"case|charger|tay cam|day deo|sticker)\b")),
    ("sunscreen", re.compile(r"\b(chong nang|sunscreen|sun screen|sunblock|uv|spf\d*\+*)(?![a-z0-9])")),
    ("hair_dryer", re.compile(r"\b(may say toc|hair dryer|hairdryer|say toc)\b")),
    ("diaper", re.compile(r"\b(bim|ta quan|ta dan|ta giay|ta em be|diapers?|bim quan|bim dan)\b")),
    ("detergent", re.compile(r"\b(nuoc giat|bot giat|vien giat|giat xa|detergent)\b")),
    ("phone", re.compile(r"\b(iphone|dien thoai|smartphone|galaxy|redmi|poco|reno)\b")),
    ("air_fryer", re.compile(r"\b(noi chien khong dau|air ?fryer|noi chien)\b")),
]
BRAND_DEFAULT_CATEGORY = {"anessa": "sunscreen", "merries": "diaper", "bobby": "diaper", "pampers": "diaper",
                          "huggies": "diaper", "moony": "diaper", "molfix": "diaper", "goo.n": "diaper"}
COLOR_CATEGORIES = {"phone", "hair_dryer", "air_fryer"}

# Words describing the category itself — removed from product_line so that Vietnamese and
# English titles of the same product compare equal.
CATEGORY_WORDS = re.compile(
    r"\b(kem chong nang|sua chong nang|gel chong nang|xit chong nang|chong nang|sunscreen|sun screen|sunblock|may say toc|hair dryer|hairdryer|"
    r"say toc|bim quan|bim dan|ta quan|ta dan|ta giay|bim|diapers?|nuoc giat xa|nuoc giat|bot giat|"
    r"vien giat|giat xa|detergent|dien thoai|smartphone|noi chien khong dau|air ?fryer|noi chien)\b")

TRAIT_PATTERNS: dict[str, list[tuple[str, re.Pattern[str]]]] = {
    "formula": [
        ("milk", re.compile(r"\b(milk|dang sua|sua chong nang)\b")),
        ("gel", re.compile(r"\bgel\b")),
        ("essence", re.compile(r"\b(essence|tinh chat)\b")),
        ("spray", re.compile(r"\b(spray|xit|dang xit)\b")),
        ("stick", re.compile(r"\b(stick|dang thoi|dang sap)\b")),
    ],
    "line_variant": [
        ("mild", re.compile(r"\b(mild|diu nhe|da nhay cam|tre em|baby)\b")),
        ("brightening", re.compile(r"\b(brightening|whitening|duong trang|sang da|nang tone)\b")),
        ("moisture", re.compile(r"\b(moisture|duong am)\b")),
    ],
    "diaper_type": [
        ("pants", re.compile(r"\b((ta|bim) quan|quan|pants?)\b")),
        ("tape", re.compile(r"\b((ta|bim) dan|dan|tape)\b")),
    ],
    "detergent_form": [
        ("liquid", re.compile(r"\bnuoc giat\b")),
        ("powder", re.compile(r"\bbot giat\b")),
        ("pods", re.compile(r"\bvien giat\b")),
    ],
    "washer_type": [
        ("front", re.compile(r"\b(cua truoc|cua ngang|front load)\b")),
        ("top", re.compile(r"\b(cua tren|top load)\b")),
    ],
    "packaging": [
        ("bag", re.compile(r"\btui\b")),
        ("bottle", re.compile(r"\b(chai|can|binh)\b")),
    ],
}
TRAIT_CATEGORIES = {
    "formula": {"sunscreen"}, "line_variant": {"sunscreen"}, "diaper_type": {"diaper"},
    "detergent_form": {"detergent"}, "washer_type": {"detergent"}, "packaging": {"detergent"},
}

# Colors are matched on the accented text ("đỏ" vs "độ" must not collide). Longest first.
COLORS: list[tuple[str, str]] = sorted([
    ("titan tự nhiên", "natural titanium"), ("natural titanium", "natural titanium"),
    ("titan đen", "black titanium"), ("black titanium", "black titanium"),
    ("titan trắng", "white titanium"), ("white titanium", "white titanium"),
    ("titan sa mạc", "desert titanium"), ("desert titanium", "desert titanium"),
    ("xanh dương", "blue"), ("xanh lá", "green"), ("xanh", "blue"), ("đen", "black"),
    ("trắng", "white"), ("hồng", "pink"), ("đỏ", "red"), ("vàng", "gold"), ("tím", "purple"),
    ("xám", "gray"), ("bạc", "silver"), ("black", "black"), ("white", "white"), ("pink", "pink"),
    ("blue", "blue"), ("gold", "gold"), ("silver", "silver"), ("fuchsia", "fuchsia"),
    ("nickel", "nickel"), ("copper", "copper"), ("đồng", "copper"),
], key=lambda kv: -len(kv[0]))

STOPWORDS = set(
    "chinh hang gia re nhat freeship free ship hot sale tang qua kem moi new auth authentic "
    "official store mall flagship date han su dung hsd giam soc deal sieu uu dai khuyen mai "
    "hang san sale off cho be va cua the nhat ban nhap khau noi dia xach tay bao hanh thang nam "
    "full box fullbox nguyen seal vn a ban quoc te phan phoi cong ty voi with for de loai dung "
    "tich san pham combo set bo size mau dung tich khoi luong ml kg gb g l plus+ cao cap "
    "mua 1 2 tem phu san sang giao nhanh hoa toc toan quoc uy tin chat luong len den tu "
    "dang mini huong mieng vien pcs my nhat uc han bich hop tuyp loc lo thung goi tui chai can binh".split())
RE_NOISE = re.compile(r"\b(spf\s?\d+\+*|pa\++|\d+\s*-\s*\d+\s*kg|\d+\s*w)(?![a-z0-9])")

_NUM = r"(\d+(?:[.,]\d+)?)"
RE_STORAGE = re.compile(r"\b(\d+)\s*(gb|tb)\b")
RE_VOLUME = re.compile(rf"\b{_NUM}\s*(ml|lit|l)\b")
RE_WEIGHT = re.compile(rf"\b{_NUM}\s*(kg|gram|gr|g)\b")
RE_SIZE_COUNT = re.compile(r"\b(?:size\s*)?(nb|xxxl|xxl|xl|s|m|l)\s?-?\s?(\d{2,3})\b(?:\s*(?:mieng|pcs))?")
RE_SIZE = re.compile(r"\bsize\s*(nb|xxxl|xxl|xl|s|m|l)\b")
RE_COUNT = re.compile(r"\b(\d{1,3})\s*(mieng|vien|pcs|pc|cai|chiec)\b")
RE_PACK = [
    re.compile(r"\b(?:combo|set|bo)\s*(\d{1,2})\b"),
    re.compile(r"(?:^|\s)x\s?(\d{1,2})\b"),
    re.compile(r"\b(\d{1,2})\s*(?:chai|hop|tuyp|bich|tui|loc|binh|lo|can|thung|goi)\b"),
]
RE_IPHONE = re.compile(r"\biphone\s*(\d{1,2}|se)\s*(pro\s*max|promax|pro|plus|mini|e)?\b")
RE_MODEL = re.compile(r"\b([a-z]{1,5}-?\d{2,5}[a-z0-9]*)(?:/\d{2})?\b")
RE_MODEL_EXCLUDE = re.compile(r"^(spf|pa|ip|uv|usb|mp|hz|w|v|a1[0-9]|m[1-4]$|type|top|no|vn|ver|gen|size)")
RE_GTIN = re.compile(r"\b(\d{13}|\d{12}|\d{8})\b")


def _num(text: str) -> float:
    return float(text.replace(",", "."))


def _take(pattern: re.Pattern[str], text: str) -> tuple[re.Match[str] | None, str]:
    """Find first match and blank it out of the text so later steps don't re-read it."""
    m = pattern.search(text)
    if not m:
        return None, text
    return m, text[:m.start()] + " " + text[m.end():]


def detect_category(folded: str, brand: str | None = None) -> str | None:
    for name, pattern in CATEGORY_PATTERNS:
        if pattern.search(folded):
            return name
    return BRAND_DEFAULT_CATEGORY.get(brand or "")


def detect_brand(folded: str) -> str | None:
    cobrand = {m.group(2) for m in _COBRAND_CONTEXT.finditer(folded)}
    best: tuple[int, str] | None = None
    for alias, canonical in BRAND_ALIASES.items():
        m = re.search(rf"(?<![a-z0-9]){re.escape(alias)}(?![a-z0-9])", folded)
        if m and canonical not in cobrand and (best is None or m.start() < best[0]):
            best = (m.start(), canonical)
    return best[1] if best else None


def normalize_title(title: str, variant_label: str | None = None,
                    gtin: str | None = None) -> ProductAttributes:
    raw = f"{title} {variant_label or ''}".strip()
    accented = unicodedata.normalize("NFC", raw).lower()
    text = fold(raw)
    attrs = ProductAttributes(gtin=gtin)

    attrs.brand = detect_brand(text)
    attrs.category = detect_category(text, attrs.brand)

    # Condition / origin (accented text: "cũ" must not match "củ sạc").
    if re.search(r"(?<!\w)(cũ|like ?new|đã qua sử dụng|qua sử dụng|second ?hand|9[0-9]%)(?!\w)", accented):
        attrs.condition = "used"
    elif re.search(r"(refurbished|tân trang|\bcpo\b)", accented):
        attrs.condition = "refurbished"
    elif re.search(r"(mới 100%|new 100%|nguyên seal|new seal|full ?box)", accented):
        attrs.condition = "new"
    if re.search(r"(xách tay|nhập khẩu|nội địa|bản mỹ|bản nhật|\bll/a\b|\bj/a\b|hàng nhật|hàng mỹ|hàng úc)",
                 accented):
        attrs.origin = "imported"
    elif re.search(r"(\bvn/a\b|phân phối chính hãng|bảo hành chính hãng|chính hãng việt nam)", accented):
        attrs.origin = "official"

    text = RE_NOISE.sub(" ", text)
    # Measured attributes — each match is removed from the working text.
    storages = [int(m.group(1)) * (1024 if m.group(2) == "tb" else 1) for m in RE_STORAGE.finditer(text)]
    if storages:
        attrs.storage_gb = max(storages)
        text = RE_STORAGE.sub(" ", text)

    m, text = _take(RE_VOLUME, text)
    if m:
        attrs.volume_ml = round(_num(m.group(1)) * (1 if m.group(2) == "ml" else 1000), 2)
    m = RE_WEIGHT.search(text)
    if m and not (m.group(2) == "g" and m.group(1) in {"3", "4", "5"} and attrs.category == "phone"):
        attrs.weight_g = round(_num(m.group(1)) * (1000 if m.group(2) == "kg" else 1), 2)
        text = text[:m.start()] + " " + text[m.end():]

    if attrs.category == "diaper":
        m, text = _take(RE_SIZE_COUNT, text)
        if m:
            attrs.size, attrs.count = m.group(1).upper(), int(m.group(2))
    m, text = _take(RE_SIZE, text)
    if m and not attrs.size:
        attrs.size = m.group(1).upper()
    m, text = _take(RE_COUNT, text)
    if m and attrs.count is None:
        attrs.count = int(m.group(1))

    for pattern in RE_PACK:
        m, text = _take(pattern, text)
        if m:
            attrs.pack_quantity = int(m.group(1))
            break

    # Traits are read before category words are stripped ("nuoc giat" carries the form).
    for trait, options in TRAIT_PATTERNS.items():
        if attrs.category not in TRAIT_CATEGORIES[trait]:
            continue
        found = [value for value, pattern in options if pattern.search(text)]
        if len(found) == 1:
            attrs.traits[trait] = found[0]
    for trait, options in TRAIT_PATTERNS.items():   # trait words are not part of the line name
        if attrs.category in TRAIT_CATEGORIES[trait]:
            for _, pattern in options:
                text = pattern.sub(" ", text)

    # Model.
    m = RE_IPHONE.search(text)
    if m:
        suffix = (m.group(2) or "").replace(" ", "")
        suffix = {"promax": "pro max"}.get(suffix, suffix)
        attrs.model = f"iphone {m.group(1)} {suffix}".strip()
        text = text.replace(m.group(0), " ")
    else:
        for m in RE_MODEL.finditer(text):
            token = m.group(1)
            if RE_MODEL_EXCLUDE.match(token) or token in BRAND_ALIASES:
                continue
            attrs.model = token.replace("-", "")
            text = re.sub(rf"\b{re.escape(token)}(?:/\d{{2}})?\b", " ", text)   # every occurrence
            break

    explicit_color = re.search(r"(?<!\w)(màu|color|colour)\s", accented) is not None
    if attrs.category in COLOR_CATEGORIES or explicit_color:
        for needle, canonical in COLORS:
            prefix = "" if attrs.category in COLOR_CATEGORIES else r"(?:màu|color|colour)\s+"
            if re.search(rf"(?<!\w){prefix}{re.escape(needle)}(?!\w)", accented):
                attrs.color = canonical
                text = text.replace(fold(needle), " ")
                break

    m = RE_GTIN.search(text)
    if m and not attrs.gtin and re.search(r"\b(gtin|ean|barcode|ma vach)\b", text):
        attrs.gtin = m.group(1)

    # Product line: whatever distinctive words remain.
    text = CATEGORY_WORDS.sub(" ", text)
    for alias in BRAND_ALIASES:
        text = re.sub(rf"(?<![a-z0-9]){re.escape(alias)}(?![a-z0-9])", " ", text)
    tokens: list[str] = []
    for token in re.findall(r"[a-z][a-z0-9+&]*", text):
        if token in STOPWORDS or len(token) < 2 or token in tokens:
            continue
        tokens.append(token)
    attrs.product_line = " ".join(tokens) or None
    return attrs


def query_to_attributes(query: ProductQuery) -> ProductAttributes:
    """Turn the user's structured query into the same attribute space as listings.
    Only fields the user actually specified are set."""
    base = " ".join(p for p in [query.brand, query.product_name, query.model] if p)
    extras = [query.variant, query.volume, query.weight, query.storage,
              f"màu {query.color}" if query.color else None,
              f"size {query.size}" if query.size else None,
              f"{query.count} miếng" if query.count else None]
    attrs = normalize_title(" ".join(p for p in [base, *extras] if p))
    # The descriptive line comes from the name only, so repeated structured fields
    # ("HD9200" in both name and model) cannot leak into it as fake line words.
    attrs.product_line = normalize_title(base).product_line if base else None
    if query.category and not attrs.category:
        attrs.category = query.category
    if query.brand and not attrs.brand:
        attrs.brand = fold(query.brand)
    attrs.pack_quantity = query.quantity
    attrs.condition = None
    constraints = fold(" ".join(query.constraints))
    if "xach tay" in constraints or "nhap khau" in constraints:
        attrs.origin = "imported"
    return attrs


TRAIT_LABELS = {
    "milk": "Milk", "gel": "Gel", "essence": "Essence", "spray": "dạng xịt", "stick": "dạng thỏi",
    "mild": "Mild", "brightening": "Brightening", "moisture": "Moisture", "pants": "tã quần",
    "tape": "tã dán", "liquid": "nước giặt", "powder": "bột giặt", "pods": "viên giặt",
    "front": "cửa trước", "top": "cửa trên", "bag": "túi", "bottle": "chai",
}
COLOR_LABELS = {
    "black": "đen", "white": "trắng", "pink": "hồng", "blue": "xanh", "green": "xanh lá", "red": "đỏ",
    "gold": "vàng", "purple": "tím", "gray": "xám", "silver": "bạc", "copper": "đồng",
    "natural titanium": "Titan Tự Nhiên", "black titanium": "Titan Đen", "white titanium": "Titan Trắng",
    "desert titanium": "Titan Sa Mạc",
}
CATEGORY_LABELS = {"sunscreen": "chống nắng", "hair_dryer": "máy sấy tóc", "diaper": "tã/bỉm",
                   "detergent": "giặt xả", "phone": "điện thoại", "air_fryer": "nồi chiên không dầu",
                   "accessory": "phụ kiện"}


def _cap(words: str) -> str:
    return " ".join(w.upper() if len(w) <= 2 else w.capitalize() for w in words.split())


def _measure(value: float, small: str, big: str) -> str:
    return f"{value / 1000:g}{big}" if value >= 1000 else f"{value:g}{small}"


def display_title(attrs: ProductAttributes, fallback: str) -> str:
    """Short canonical display name built only from known attributes."""
    if not attrs.brand:
        return fallback
    bits = [_cap(attrs.brand)]
    if attrs.model and attrs.model.startswith("iphone"):
        bits.append(_cap(attrs.model).replace("Iphone", "iPhone"))
    else:
        if attrs.product_line:
            bits.append(_cap(attrs.product_line))
        if attrs.model:
            bits.append(attrs.model.upper())
    order = ["line_variant", "formula", "diaper_type", "detergent_form", "washer_type", "packaging"]
    bits += [TRAIT_LABELS.get(attrs.traits[k], attrs.traits[k]) for k in order if k in attrs.traits]
    if len(bits) == 1 and attrs.category in CATEGORY_LABELS:
        bits.append(CATEGORY_LABELS[attrs.category])
    if attrs.storage_gb:
        bits.append(f"{attrs.storage_gb}GB")
    if attrs.volume_ml:
        bits.append(_measure(attrs.volume_ml, "ml", "L"))
    if attrs.weight_g:
        bits.append(_measure(attrs.weight_g, "g", "kg"))
    if attrs.size:
        bits.append(f"size {attrs.size}")
    if attrs.count:
        bits.append(f"{attrs.count} miếng")
    if attrs.color:
        bits.append(f"màu {COLOR_LABELS.get(attrs.color, attrs.color)}")
    if attrs.pack_quantity and attrs.pack_quantity > 1:
        bits.append(f"(combo {attrs.pack_quantity})")
    if attrs.origin == "imported":
        bits.append("(nhập khẩu/xách tay)")
    if attrs.condition in ("used", "refurbished"):
        bits.append("(đã qua sử dụng)" if attrs.condition == "used" else "(tân trang)")
    return " ".join(bits)
