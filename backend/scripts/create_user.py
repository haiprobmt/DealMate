"""Tạo người dùng và in mã truy cập (chỉ hiện một lần).   python -m scripts.create_user "Tên" """
import asyncio
import sys

from app.core.db import dispose_engine, get_sessionmaker
from app.core.security import create_user


async def main() -> None:
    name = sys.argv[1] if len(sys.argv) > 1 else "Gia đình"
    async with get_sessionmaker()() as session:
        user, token = await create_user(session, name)
    await dispose_engine()
    print(f"Đã tạo người dùng #{user.id} ({user.display_name}).")
    print(f"Mã truy cập (lưu lại, sẽ không hiển thị lần nữa): {token}")


if __name__ == "__main__":
    asyncio.run(main())
