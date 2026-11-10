"""Phân tích URL sản phẩm của các sàn được hỗ trợ (chỉ phân tích chuỗi, không tải trang)."""
import re
from dataclasses import dataclass
from urllib.parse import unquote, urlparse

from app.connectors.mock import MOCK_HOST
from app.core.errors import AppError
from app.schemas.domain import Marketplace

_HOSTS = {
    Marketplace.SHOPEE: ("shopee.vn",),
    Marketplace.LAZADA: ("lazada.vn",),
    Marketplace.TIKTOK_SHOP: ("tiktok.com", "tiktokshop.vn"),
}
_SHORT_HOSTS = ("s.shopee.vn", "shope.ee", "s.lazada.vn", "vt.tiktok.com", "vm.tiktok.com")


@dataclass
class ParsedUrl:
    marketplace: Marketplace
    product_id: str | None      # marketplace listing id, when the URL carries one
    slug_text: str | None       # human-readable words found in the URL path
    is_mock: bool = False
    is_short_link: bool = False


def _host_matches(host: str, domain: str) -> bool:
    return host == domain or host.endswith("." + domain)


def parse_marketplace_url(url: str) -> ParsedUrl:
    parsed = urlparse(url.strip())
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        raise AppError("Link không hợp lệ. Hãy dán link sản phẩm bắt đầu bằng https://", code="invalid_url",
                       status_code=422)
    host = parsed.hostname.lower()
    path = unquote(parsed.path)

    if host == MOCK_HOST:
        m = re.fullmatch(r"/(shopee|lazada|tiktok_shop)/product/([A-Za-z0-9\-]+)", path)
        if not m:
            raise AppError("Link mẫu không đúng định dạng.", code="invalid_url", status_code=422)
        return ParsedUrl(Marketplace(m.group(1)), m.group(2), None, is_mock=True)

    marketplace = next((mp for mp, domains in _HOSTS.items()
                        if any(_host_matches(host, d) for d in domains)), None)
    if marketplace is None:
        raise AppError("Chưa hỗ trợ link từ trang này. Hiện hỗ trợ Shopee, Lazada và TikTok Shop Việt Nam.",
                       code="unsupported_marketplace", status_code=422)
    if host in _SHORT_HOSTS:
        return ParsedUrl(marketplace, None, None, is_short_link=True)

    product_id: str | None = None
    slug: str | None = None
    if marketplace == Marketplace.SHOPEE:
        m = re.search(r"-i\.(\d+)\.(\d+)", path) or re.search(r"/product/(\d+)/(\d+)", path)
        if m:
            product_id = f"{m.group(1)}.{m.group(2)}"
        s = re.match(r"/(.+?)-i\.\d+\.\d+", path)
        slug = s.group(1) if s else None
    elif marketplace == Marketplace.LAZADA:
        m = re.search(r"-i(\d+)(?:-s\d+)?\.html", path) or re.search(r"/products/i(\d+)", path)
        if m:
            product_id = m.group(1)
        s = re.match(r"/products/(.+?)-i\d+", path)
        slug = s.group(1) if s else None
    else:
        m = re.search(r"/(?:view/)?product/(\d+)", path)
        if m:
            product_id = m.group(1)
    slug_text = re.sub(r"[-_+]+", " ", slug).strip() if slug else None
    return ParsedUrl(marketplace, product_id, slug_text or None)
