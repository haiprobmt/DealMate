import os
import subprocess
import sys

TEST_DB = os.environ.get("TEST_DATABASE_URL", "postgresql+asyncpg://postgres@localhost:5433/dealmate_test")
os.environ.update({
    "DEALMATE_ENV": "test", "DEALMATE_DATABASE_URL": TEST_DB, "DEALMATE_MOCK_MODE": "true",
    "DEALMATE_RATE_LIMIT_PER_MINUTE": "0", "DEALMATE_CONNECTOR_TIMEOUT_SECONDS": "0.3",
    "DEALMATE_LOG_LEVEL": "WARNING",
})
os.environ.pop("DEALMATE_BOOTSTRAP_TOKEN", None)

import httpx  # noqa: E402
import pytest  # noqa: E402
from sqlalchemy import text  # noqa: E402

from app.ai.client import AIUnavailable, set_llm  # noqa: E402
from app.connectors.registry import build_registry, set_registry  # noqa: E402
from app.core.config import get_settings  # noqa: E402
from app.core.db import get_sessionmaker  # noqa: E402
from app.core.security import create_user  # noqa: E402
from app.main import app  # noqa: E402

BACKEND_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


@pytest.fixture(scope="session", autouse=True)
def _migrate():
    """The test database schema is built by the real Alembic migrations."""
    subprocess.run([sys.executable, "-m", "alembic", "upgrade", "head"], cwd=BACKEND_DIR, check=True,
                   env=os.environ.copy())


@pytest.fixture(autouse=True)
async def _clean(_migrate):
    async with get_sessionmaker()() as session:
        await session.execute(text(
            "TRUNCATE users, marketplace_sources, products, searches RESTART IDENTITY CASCADE"))
        await session.commit()
    set_registry(build_registry(get_settings()))
    set_llm(None)
    yield
    set_registry(None)
    set_llm(None, resolved=False)


@pytest.fixture
async def session():
    async with get_sessionmaker()() as s:
        yield s


@pytest.fixture
def registry():
    from app.connectors.registry import get_registry
    return get_registry()


@pytest.fixture
async def user(session):
    u, token = await create_user(session, "Test", "test-token-0123456789")
    return u, token


@pytest.fixture
async def client(user):
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test",
                                 headers={"Authorization": f"Bearer {user[1]}"}) as c:
        yield c


class FakeLLM:
    """Scripted stand-in for the Anthropic client: maps schema class -> response or exception."""
    model = "fake-model"

    def __init__(self, responses: dict | None = None):
        self.responses = responses or {}
        self.calls: list[tuple] = []

    async def parse(self, schema, system, content, max_tokens=2000):
        self.calls.append((schema, system, content))
        value = self.responses.get(schema, AIUnavailable("not scripted"))
        if isinstance(value, Exception):
            raise value
        return value(content) if callable(value) else value


@pytest.fixture
def fake_llm():
    llm = FakeLLM()
    set_llm(llm)
    return llm
