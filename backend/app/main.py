from contextlib import asynccontextmanager

from fastapi import APIRouter, FastAPI
from sqlalchemy import text

from app import __version__
from app.api.v1 import history, meta, products, search, wishlist
from app.connectors.registry import get_registry
from app.core.config import get_settings
from app.core.db import dispose_engine, get_sessionmaker
from app.core.errors import ServiceUnavailableError, install_error_handlers
from app.core.logging import configure_logging, get_logger
from app.core.middleware import RateLimitMiddleware, RequestContextMiddleware
from app.core.security import create_user

log = get_logger("dealmate")


@asynccontextmanager
async def lifespan(_: FastAPI):
    settings = get_settings()
    configure_logging(settings.log_level)
    if settings.bootstrap_token:
        async with get_sessionmaker()() as session:
            await create_user(session, settings.bootstrap_user_name, settings.bootstrap_token)
    log.info("startup", extra={"env": settings.env, "mock_mode": settings.mock_mode})
    yield
    await dispose_engine()


def create_app() -> FastAPI:
    production = get_settings().env == "production"
    app = FastAPI(title="DealMate API", version=__version__, lifespan=lifespan,
                  description="Tìm giá mua thực tế thấp nhất cho đúng sản phẩm.",
                  # The interactive docs describe private endpoints: off in production.
                  docs_url=None if production else "/docs", redoc_url=None,
                  openapi_url=None if production else "/openapi.json")
    app.add_middleware(RateLimitMiddleware)
    app.add_middleware(RequestContextMiddleware)   # outermost: sets the request id first
    install_error_handlers(app)

    @app.get("/health", tags=["health"])
    async def health() -> dict:
        try:
            async with get_sessionmaker()() as session:
                await session.execute(text("SELECT 1"))
        except Exception as exc:
            raise ServiceUnavailableError("Không kết nối được cơ sở dữ liệu.", code="db_unavailable") from exc
        return {"status": "ok", "demo_mode": get_registry().demo_mode, "version": __version__}

    v1 = APIRouter(prefix="/api/v1")
    for module in (search, products, wishlist, history, meta):
        v1.include_router(module.router)
    app.include_router(v1)
    return app


app = create_app()
