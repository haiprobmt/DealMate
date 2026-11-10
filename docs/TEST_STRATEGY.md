# Test Strategy

## Principles
* The risky parts — "is it the same product?" and "what does it really cost?" — are deterministic
  code with dense unit tests and a labelled dataset.
* Integration tests run the real FastAPI app against real PostgreSQL built by the real migrations.
* External systems (marketplaces, Anthropic, FCM) are replaced at their boundary: mock connectors
  with failure/latency injection, `httpx.MockTransport`, a scripted fake LLM, a fake push sender.
  These prove our side of the contract; they do **not** prove the external side.
* Nothing is reported as passing unless it was executed (see the README status table).

## Layers
| Layer | Location | Notes |
|---|---|---|
| Backend unit | `backend/tests/test_normalization.py`, `test_matching.py`, `test_pricing.py`, `test_query_and_url.py`, `test_ai_guards.py` | title parsing, hard matching rules, voucher eligibility/stacking/caps, min-spend, unknown shipping, ranking tiers, budget parsing, URL parsing/rejection, AI clamp |
| Matching dataset | `backend/tests/data/matching_pairs.json` + `scripts/eval_matching.py` | 119 hand-labelled pairs; test enforces exact precision ≥ 0.98 and recall ≥ 0.90 |
| Connectors | `backend/tests/test_connectors.py` | mock labelling, registry never mixes mock/live, pending connectors refuse, Shopee signing + mapping + upstream failures via fake transport |
| API integration | `backend/tests/test_api_search.py`, `test_api_wishlist_alerts.py`, `test_api_misc.py` | scenarios 1–5, timeout/failure isolation, validation, auth on every route, error envelope, rate limit, history, explore, sources, assistant, price monitor + notification de-duplication + push worker, migration drift |
| HTTP end-to-end | `tests/e2e/test_scenarios.py` | the five scenarios over real HTTP against a running stack (Docker) |
| Android | `android/app/src/test/` | unit, ViewModel, MockWebServer repository tests, contract tests on real backend JSON, Robolectric Compose UI tests, screenshot rendering |

## Specification scenarios → tests
| # | Scenario | Expected | Tests |
|---|---|---|---|
| 1 | Search "Anessa 60ml" | Only correct variants are exact | `test_scenario1_anessa_60ml_only_correct_variants_are_exact`, e2e 1 |
| 2 | Three marketplaces | Combined, sorted by final price | `test_scenario2_three_marketplaces_combined_and_sorted_by_final_price`, e2e 2 |
| 3 | One marketplace unavailable | Others still shown | `test_scenario3_one_marketplace_down_others_still_shown`, `test_marketplace_timeout_is_isolated`, `test_all_marketplaces_down_returns_empty_not_error` |
| 4 | Shipping unavailable | Marked incomplete, not the verified cheapest | `test_scenario4_unknown_shipping_is_incomplete_and_never_the_verified_cheapest`, `test_unknown_shipping_is_never_zero`, e2e 4 |
| 5 | Save product + target price | In wishlist, alert registered | `test_scenario5_save_product_with_target_price`, e2e 5 |

## Not covered (known gaps)
* No test against a live marketplace API, the live Anthropic API or live FCM (no credentials).
* No on-device / emulator run of the Android app (no emulator in the build environment);
  camera capture, photo picker, runtime permissions, WorkManager scheduling and FCM delivery are
  therefore untested on real hardware.
* The matching dataset is a development set (rules were tuned on it) and its titles are written in
  marketplace style rather than harvested from live listings.
* No load/performance testing (single-family usage).

## Running
```bash
cd backend && pip install -e ".[dev,fcm]"
TEST_DATABASE_URL=postgresql+asyncpg://user:pass@localhost:5432/dealmate_test pytest
python -m scripts.eval_matching --verbose
DEALMATE_E2E_URL=http://localhost:8000 DEALMATE_E2E_TOKEN=<token> pytest ../tests/e2e
cd ../android && ./gradlew :app:testDebugUnitTest :app:lintDebug
```
CI (`.github/workflows/ci.yml`) runs all of the above and uploads a debug APK.
