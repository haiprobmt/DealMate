from fastapi import APIRouter, Depends, Query, Response
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.db import get_session
from app.core.security import current_user
from app.models.tables import ProductVariant, ProductView, Search, User
from app.repositories import catalog
from app.schemas.api import HistoryResponse, SearchHistoryItem, ViewedProductItem

router = APIRouter(prefix="/history", tags=["history"])


@router.get("", response_model=HistoryResponse)
async def get_history(limit: int = Query(default=30, ge=1, le=100), user: User = Depends(current_user),
                      session: AsyncSession = Depends(get_session)) -> HistoryResponse:
    searches = (await session.execute(
        select(Search).where(Search.user_id == user.id)
        .order_by(Search.created_at.desc(), Search.id.desc()).limit(limit))).scalars().all()
    views = (await session.execute(
        select(ProductView, ProductVariant).join(ProductVariant, ProductView.variant_id == ProductVariant.id)
        .where(ProductView.user_id == user.id).order_by(ProductView.viewed_at.desc()).limit(limit))).all()
    viewed = []
    for view, variant in views:
        best = catalog.pick_best(await catalog.variant_offers(session, variant.id))
        viewed.append(ViewedProductItem(
            product_id=variant.product_id, variant_id=variant.id, title=variant.title,
            image_url=variant.image_url, lowest_final_price=best.price.final_price if best else None,
            viewed_at=view.viewed_at))
    return HistoryResponse(
        searches=[SearchHistoryItem(id=s.id, search_type=s.search_type, query=s.query,
                                    result_count=s.result_count, lowest_price=s.lowest_price,
                                    created_at=s.created_at) for s in searches],
        viewed_products=viewed)


@router.delete("", status_code=204)
async def clear_history(user: User = Depends(current_user),
                        session: AsyncSession = Depends(get_session)) -> Response:
    await session.execute(delete(Search).where(Search.user_id == user.id))
    await session.execute(delete(ProductView).where(ProductView.user_id == user.id))
    await session.commit()
    return Response(status_code=204)
