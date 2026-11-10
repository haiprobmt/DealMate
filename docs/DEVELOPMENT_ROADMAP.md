# Development Roadmap

## Milestone status
| Milestone | Status | Notes |
|---|---|---|
| 0 — Inspection & planning | Done | Empty repo; toolchain installed; constraints recorded in SYSTEM_ARCHITECTURE.md |
| 1 — Foundation | Done | FastAPI, PostgreSQL, Alembic, Docker Compose, health check, CI workflow (CI defined but not yet run on GitHub) |
| 2 — Android UI | Done (build + JVM/Robolectric verification) | Not run on a device/emulator |
| 3 — Backend search engine | Done | Orchestration, interpreter, connectors, normalization, matching, pricing, ranking — all tested |
| 4 — Marketplace integration | **Blocked on credentials** | Shopee Affiliate connector written but unverified; Lazada/TikTok not integrated; URL search implemented |
| 5 — AI features | Implemented, **unverified live** | Works behind a fake client in tests; needs an Anthropic key for a real run |
| 6 — Personal shopping features | Done on mock data | Wishlist, price history, target prices, scheduled checks, notifications (in-app polling verified in tests; FCM unverified) |
| 7 — QA & release | Partly done | Automated tests green; signed APK built; on-device QA and real-link validation outstanding |

## Next actions (in priority order)
1. **Get Shopee Affiliate credentials** (affiliate.shopee.vn → Open API). Set them, switch
   `DEALMATE_MOCK_MODE=false`, run the first-run checklist in MARKETPLACE_INTEGRATION.md, fix any
   response-shape differences. This is the shortest path to genuine offers.
2. **Install the APK on the real phone** and walk through: search, results, detail, open listing,
   wishlist, target price, notification, image search, language switch. File what breaks.
3. **Add an Anthropic API key** on the server; try 20 real queries and 10 product photos; tune prompts.
4. **Apply for Lazada affiliate API and TikTok Shop partner access**; implement the two connectors.
5. **Shipping and vouchers for real offers.** Affiliate feeds do not expose them, so real offers will
   be `incomplete`. Options: per-marketplace shipping estimate by delivery location (clearly
   "estimated"), or let the user enter the checkout price they actually saw.
6. Harvest real listing titles into the matching dataset (with a held-out split); extend the brand
   lexicon (consider moving it to a data file / DB table).
7. Firebase project + service account → verify FCM push end to end.
8. Run CI on GitHub; add an instrumented (emulator) smoke test job.

## Later ideas
* "Find a cheaper alternative" across brands (may justify embeddings/pgvector).
* Barcode (GTIN) scan → exact identification.
* Unit-price comparison (đ/100ml, đ/miếng) across pack sizes.
* Per-seller reliability signals (rating history, return policy).
