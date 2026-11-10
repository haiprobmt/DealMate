"""Xác thực bằng API token cá nhân (ứng dụng gia đình, không đăng ký công khai).
Only the SHA-256 of a token is stored. Tokens are random 256-bit values, so a fast hash is fine."""
import hashlib
import secrets

from fastapi import Depends, Request
from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.db import get_session
from app.core.errors import UnauthorizedError
from app.models.tables import User


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def generate_token() -> str:
    return "dm_" + secrets.token_urlsafe(32)


async def create_user(session: AsyncSession, display_name: str, token: str | None = None) -> tuple[User, str]:
    token = token or generate_token()
    user = (await session.execute(
        pg_insert(User).values(display_name=display_name, token_hash=hash_token(token))
        .on_conflict_do_update(index_elements=["token_hash"], set_={"display_name": display_name})
        .returning(User))).scalar_one()
    await session.commit()
    return user, token


async def current_user(request: Request, session: AsyncSession = Depends(get_session)) -> User:
    header = request.headers.get("authorization", "")
    scheme, _, token = header.partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise UnauthorizedError("Thiếu mã truy cập. Hãy nhập mã trong phần Cài đặt.")
    user = (await session.execute(
        select(User).where(User.token_hash == hash_token(token.strip())))).scalar_one_or_none()
    if user is None:
        raise UnauthorizedError("Mã truy cập không đúng.")
    return user
