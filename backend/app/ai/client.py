"""Lớp bọc mỏng quanh Anthropic SDK. Mọi lời gọi AI đều đi qua ``StructuredLLM.parse`` và trả
về một Pydantic model đã được SDK xác thực theo schema (structured outputs)."""
import os
from typing import Protocol, TypeVar

import anthropic
from pydantic import BaseModel

from app.core.config import get_settings
from app.core.logging import get_logger

log = get_logger(__name__)
T = TypeVar("T", bound=BaseModel)


class AIUnavailable(Exception):
    """AI is not configured, refused, or failed — callers fall back to deterministic logic."""


class StructuredLLM(Protocol):
    model: str

    async def parse(self, schema: type[T], system: str, content: str | list[dict],
                    max_tokens: int = 2000) -> T: ...


class AnthropicLLM:
    def __init__(self, model: str, timeout: float):
        self.model = model
        # Credentials are resolved by the SDK from the environment (ANTHROPIC_API_KEY, ...).
        self._client = anthropic.AsyncAnthropic(timeout=timeout, max_retries=1)

    async def parse(self, schema: type[T], system: str, content: str | list[dict],
                    max_tokens: int = 2000) -> T:
        try:
            response = await self._client.beta.messages.parse(
                model=self.model,
                max_tokens=max_tokens,
                system=system,
                messages=[{"role": "user", "content": content}],
                output_format=schema,
                output_config={"effort": "low"},
                # If a safety classifier declines, let the API retry on its default fallback model.
                betas=["server-side-fallback-2026-07-01"],
                fallbacks="default",
            )
        except anthropic.RateLimitError as exc:
            raise AIUnavailable("AI đang bị giới hạn tần suất.") from exc
        except anthropic.APIStatusError as exc:
            log.warning("ai_status_error", extra={"status": exc.status_code})
            raise AIUnavailable(f"AI trả về lỗi HTTP {exc.status_code}.") from exc
        except anthropic.APIConnectionError as exc:
            raise AIUnavailable("Không kết nối được dịch vụ AI.") from exc
        if response.stop_reason == "refusal":
            raise AIUnavailable("AI từ chối xử lý yêu cầu này.")
        if response.stop_reason == "max_tokens" or response.parsed_output is None:
            raise AIUnavailable("AI trả về kết quả không đầy đủ.")
        return response.parsed_output


_llm: StructuredLLM | None = None
_llm_resolved = False


def _credentials_present() -> bool:
    return bool(os.environ.get("ANTHROPIC_API_KEY") or os.environ.get("ANTHROPIC_AUTH_TOKEN"))


def get_llm() -> StructuredLLM | None:
    """Return the LLM, or None when AI is disabled or has no credentials."""
    global _llm, _llm_resolved
    if not _llm_resolved:
        settings = get_settings()
        _llm = (AnthropicLLM(settings.ai_model, settings.ai_timeout_seconds)
                if settings.ai_enabled and _credentials_present() else None)
        _llm_resolved = True
    return _llm


def set_llm(llm: StructuredLLM | None, resolved: bool = True) -> None:
    global _llm, _llm_resolved
    _llm, _llm_resolved = llm, resolved
