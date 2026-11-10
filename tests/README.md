# Tests

| Location | What | How to run |
|---|---|---|
| `backend/tests/` | Unit + integration tests (in-process API against PostgreSQL), incl. E2E scenarios 1–5 and failure/timeout injection | `cd backend && pytest` |
| `backend/tests/data/matching_pairs.json` | 119 hand-labelled product pairs | `cd backend && python -m scripts.eval_matching --verbose` |
| `tests/e2e/` | The five scenarios over real HTTP against a running stack (Docker or uvicorn) | `DEALMATE_E2E_URL=http://localhost:8000 DEALMATE_E2E_TOKEN=... pytest tests/e2e` |
| `android/app/src/test/` | Android unit, Robolectric UI and screenshot tests | `cd android && ./gradlew :app:testDebugUnitTest` |

See `docs/TEST_STRATEGY.md`.
