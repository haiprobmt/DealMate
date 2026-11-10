from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.api.deps import registry_dep
from app.connectors.registry import ConnectorRegistry
from app.core.db import get_session
from app.core.errors import NotFoundError
from app.core.security import current_user
from app.models.tables import Product, User
from app.repositories import catalog
from app.schemas.api import ProductDetail, ProductOffersResponse, VariantSummary
from app.schemas.domain import ProductAttributes
from app.services.products import build_offers_response, get_variant_or_404, record_view

router = APIRouter(prefix="/products", tags=["products"])


async def _product_or_404(session: AsyncSession, product_id: int) -> Product:
    product = (await session.execute(
        select(Product).where(Product.id == product_id)
        .options(selectinload(Product.variants)))).scalar_one_or_none()
    if product is None:
        raise NotFoundError("Không tìm thấy sản phẩm.")
    return product


@router.get("/{product_id}", response_model=ProductDetail)
async def get_product(product_id: int, _: User = Depends(current_user),
                      session: AsyncSession = Depends(get_session)) -> ProductDetail:
    product = await _product_or_404(session, product_id)
    variants = []
    for v in sorted(product.variants, key=lambda v: v.id):
        offers = await catalog.variant_offers(session, v.id)
        best = catalog.pick_best(offers)
        variants.append(VariantSummary(
            variant_id=v.id, title=v.title, attributes=ProductAttributes(**v.attributes),
            lowest_final_price=best.price.final_price if best else None,
            lowest_price_confidence=best.price.confidence if best else None, offer_count=len(offers)))
    return ProductDetail(product_id=product.id, title=product.title, brand=product.brand,
                         category=product.category, image_url=product.image_url, variants=variants)


@router.get("/{product_id}/offers", response_model=ProductOffersResponse)
async def get_product_offers(product_id: int, variant_id: int | None = Query(default=None),
                             user: User = Depends(current_user),
                             session: AsyncSession = Depends(get_session),
                             registry: ConnectorRegistry = Depends(registry_dep)) -> ProductOffersResponse:
    product = await _product_or_404(session, product_id)
    if variant_id is None:
        if not product.variants:
            raise NotFoundError("Sản phẩm chưa có biến thể nào.")
        variant_id = min(v.id for v in product.variants)
    variant = await get_variant_or_404(session, variant_id)
    if variant.product_id != product.id:
        raise NotFoundError("Biến thể không thuộc sản phẩm này.")
    response = await build_offers_response(session, user.id, variant, registry.demo_mode)
    await record_view(session, user.id, variant.id)
    return response
