"""Cấu hình ứng dụng — mọi giá trị đều đọc từ biến môi trường (tiền tố DEALMATE_)."""
from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="DEALMATE_", env_file=".env", extra="ignore")

    env: str = "development"
    database_url: str = "postgresql+asyncpg://dealmate:dealmate@localhost:5432/dealmate"
    log_level: str = "INFO"

    # --- Marketplace connectors -------------------------------------------------
    # mock_mode=True: every marketplace is served by a clearly labelled mock connector.
    # mock_mode=False: only real connectors are used; unconfigured ones report "not_configured".
    mock_mode: bool = True
    connector_timeout_seconds: float = 8.0
    offer_freshness_hours: int = 24

    shopee_affiliate_app_id: str | None = None
    shopee_affiliate_secret: str | None = None
    shopee_affiliate_endpoint: str = "https://open-api.affiliate.shopee.vn/graphql"

    # --- Auth -------------------------------------------------------------------
    # Optional: creates (or updates) a user with this API token at startup. Dev/single-family use.
    bootstrap_token: str | None = None
    bootstrap_user_name: str = "Gia đình"

    # --- AI ---------------------------------------------------------------------
    # The Anthropic SDK reads ANTHROPIC_API_KEY itself; this flag only turns the AI layer on/off.
    ai_enabled: bool = True
    ai_model: str = "claude-opus-5-5"
    ai_timeout_seconds: float = 30.0
    ai_matching_enabled: bool = True

    # --- Limits -----------------------------------------------------------------
    rate_limit_per_minute: int = 60
    max_image_bytes: int = 5 * 1024 * 1024
    request_timeout_seconds: float = 45.0

    # --- Notifications ----------------------------------------------------------
    fcm_project_id: str | None = None
    fcm_service_account_file: str | None = None
    price_check_interval_minutes: int = Field(default=360, ge=15)


@lru_cache
def get_settings() -> Settings:
    return Settings()
