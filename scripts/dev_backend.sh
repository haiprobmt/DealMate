#!/usr/bin/env sh
# Run the backend locally (without Docker) against a local PostgreSQL in demo mode.
#   DEALMATE_DATABASE_URL=postgresql+asyncpg://user:pass@localhost:5432/dealmate ./scripts/dev_backend.sh
set -eu
cd "$(dirname "$0")/../backend"
[ -d .venv ] || python3 -m venv .venv
.venv/bin/pip install -q -e ".[dev,fcm]"
export DEALMATE_MOCK_MODE="${DEALMATE_MOCK_MODE:-true}"
export DEALMATE_BOOTSTRAP_TOKEN="${DEALMATE_BOOTSTRAP_TOKEN:-dev-token-change-me}"
.venv/bin/alembic upgrade head
echo "API: http://localhost:8000  (docs: /docs)  token: $DEALMATE_BOOTSTRAP_TOKEN"
exec .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
