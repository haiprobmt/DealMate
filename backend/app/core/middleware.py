"""Middleware: correlation id, access log, timeout toàn request, giới hạn tần suất."""
import asyncio
import time
import uuid
from collections import defaultdict, deque

from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from app.core.config import get_settings
from app.core.errors import error_body
from app.core.logging import get_logger, request_id_var

log = get_logger("dealmate.http")


class _TooLarge(Exception):
    pass


class RequestContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        incoming = request.headers.get("x-request-id", "")
        rid = incoming if 8 <= len(incoming) <= 64 and incoming.isascii() else uuid.uuid4().hex
        token = request_id_var.set(rid)
        started = time.perf_counter()
        try:
            try:
                declared = request.headers.get("content-length", "0")
                if declared.isdigit() and int(declared) > get_settings().max_image_bytes + 256 * 1024:
                    # No endpoint accepts a body this large; refuse before reading it.
                    raise _TooLarge
                response = await asyncio.wait_for(call_next(request),
                                                  get_settings().request_timeout_seconds)
            except _TooLarge:
                response = JSONResponse(error_body("payload_too_large", "Dữ liệu gửi lên quá lớn."),
                                        status_code=413)
            except TimeoutError:
                response = JSONResponse(error_body("timeout", "Yêu cầu xử lý quá lâu, vui lòng thử lại."),
                                        status_code=504)
            response.headers["X-Request-ID"] = rid
            log.info("request", extra={"method": request.method, "path": request.url.path,
                                       "status": response.status_code,
                                       "ms": round((time.perf_counter() - started) * 1000)})
            return response
        finally:
            request_id_var.reset(token)


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Sliding-window limiter kept in process memory — enough for a single-family deployment
    running one API process. Keyed by bearer token, else client address."""

    def __init__(self, app):
        super().__init__(app)
        self._hits: dict[str, deque[float]] = defaultdict(deque)

    async def dispatch(self, request: Request, call_next):
        limit = get_settings().rate_limit_per_minute
        if limit <= 0 or not request.url.path.startswith("/api/"):
            return await call_next(request)
        key = request.headers.get("authorization") or (request.client.host if request.client else "?")
        now = time.monotonic()
        if len(self._hits) > 5000:   # bound memory: forget keys with no recent activity
            self._hits = defaultdict(deque, {k: v for k, v in self._hits.items()
                                             if v and now - v[-1] <= 60})
        window = self._hits[key]
        while window and now - window[0] > 60:
            window.popleft()
        if len(window) >= limit:
            retry = max(1, int(60 - (now - window[0])))
            return JSONResponse(
                error_body("rate_limited", "Bạn thao tác quá nhanh, vui lòng thử lại sau ít phút."),
                status_code=429, headers={"Retry-After": str(retry)})
        window.append(now)
        return await call_next(request)
