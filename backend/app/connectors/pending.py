"""Sàn chưa có tích hợp được uỷ quyền. Các connector này KHÔNG trả dữ liệu; chúng chỉ khai báo
trạng thái để phần còn lại của hệ thống (và người dùng) biết rõ sàn chưa được kết nối."""
from app.connectors.base import ConnectorHealth, ConnectorNotImplemented, MarketplaceConnector
from app.schemas.domain import Marketplace, RawOffer


class _PendingConnector(MarketplaceConnector):
    reason = ""

    async def search_products(self, query: str, limit: int = 30) -> list[RawOffer]:
        raise ConnectorNotImplemented(self.reason)

    async def get_product_details(self, product_id: str) -> RawOffer | None:
        raise ConnectorNotImplemented(self.reason)

    async def health_check(self) -> ConnectorHealth:
        return ConnectorHealth("not_implemented", self.reason)


class LazadaConnector(_PendingConnector):
    marketplace = Marketplace.LAZADA
    connector_name = "lazada_pending"
    reason = ("Chưa tích hợp Lazada: Lazada Open Platform chỉ cấp API cho người bán; tìm kiếm sản phẩm "
              "cho người mua cần tài khoản Lazada Affiliate (LazAffiliates) được duyệt và product feed.")

    async def get_product_url(self, product_id: str) -> str:
        return f"https://www.lazada.vn/products/i{product_id}.html"


class TikTokShopConnector(_PendingConnector):
    marketplace = Marketplace.TIKTOK_SHOP
    connector_name = "tiktok_shop_pending"
    reason = ("Chưa tích hợp TikTok Shop: TikTok Shop Partner/Affiliate API yêu cầu ứng dụng đối tác "
              "được TikTok duyệt; chưa có quyền truy cập.")

    async def get_product_url(self, product_id: str) -> str:
        return f"https://www.tiktok.com/view/product/{product_id}"
