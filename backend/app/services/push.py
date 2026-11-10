"""Gửi thông báo đẩy qua Firebase Cloud Messaging (HTTP v1).

STATUS: implemented against the public FCM HTTP v1 contract; NOT verified end-to-end in
development (no Firebase project/service account was available). Without configuration the
sender reports ``configured == False`` and notifications stay in-app (the Android app polls)."""
import asyncio

import httpx

from app.core.config import Settings
from app.core.logging import get_logger

log = get_logger(__name__)
_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"


class FcmSender:
    def __init__(self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None):
        self._project = settings.fcm_project_id
        self._key_file = settings.fcm_service_account_file
        self._transport = transport
        self._credentials = None

    @property
    def configured(self) -> bool:
        return bool(self._project and self._key_file)

    def _access_token(self) -> str:
        from google.auth.transport.requests import Request  # optional dependency ("fcm" extra)
        from google.oauth2 import service_account
        if self._credentials is None:
            self._credentials = service_account.Credentials.from_service_account_file(
                self._key_file, scopes=[_SCOPE])
        if not self._credentials.valid:
            self._credentials.refresh(Request())
        return self._credentials.token

    async def send(self, device_token: str, title: str, body: str, data: dict[str, str]) -> str:
        """Returns 'sent', 'invalid_token' (device should be forgotten) or 'failed'."""
        token = await asyncio.to_thread(self._access_token)
        message = {"message": {"token": device_token, "notification": {"title": title, "body": body},
                               "data": data, "android": {"priority": "high"}}}
        async with httpx.AsyncClient(timeout=10, transport=self._transport) as client:
            response = await client.post(
                f"https://fcm.googleapis.com/v1/projects/{self._project}/messages:send",
                json=message, headers={"Authorization": f"Bearer {token}"})
        if response.status_code == 200:
            return "sent"
        if response.status_code in (400, 404):
            return "invalid_token"
        log.warning("fcm_send_failed", extra={"status": response.status_code})
        return "failed"
