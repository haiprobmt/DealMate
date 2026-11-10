import re
import unicodedata


def strip_accents(text: str) -> str:
    """Remove Vietnamese diacritics: "Kem chống nắng" -> "Kem chong nang"."""
    text = text.replace("đ", "d").replace("Đ", "D")
    decomposed = unicodedata.normalize("NFD", text)
    return "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")


def fold(text: str) -> str:
    """Lower-case, accent-free, single-spaced form used for all comparisons."""
    text = strip_accents(unicodedata.normalize("NFC", text)).lower()
    text = re.sub(r"(?<=\d),(?=\d)", ".", text)  # decimal comma: "3,6kg" -> "3.6kg"
    text = re.sub(r"[\[\]\(\)\{\}【】「」|,;:!?*~#\"'“”‘’]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def format_vnd(amount: int | None) -> str:
    if amount is None:
        return "chưa rõ"
    return f"{amount:,}".replace(",", ".") + "đ"
