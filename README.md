# DealMate

Trợ lý mua sắm cá nhân cho gia đình: tìm **giá mua thực tế thấp nhất** cho **đúng sản phẩm** trên
Shopee, Lazada và TikTok Shop Việt Nam.

A private, Android-first shopping assistant for the Vietnamese market.

> **Find the cheapest matching product, not simply the cheapest product.**

## Read this first — what is real and what is not

| Area | State |
|---|---|
| Search → match → price → rank → wishlist → alerts workflow | **Works end to end** (backend tests + HTTP scenarios on the Docker stack) |
| Marketplace data | **MOCK only.** No marketplace API credentials were available, so no real integration has been verified. In demo mode every offer is labelled `[MẪU]` / DEMO and links to `mock.dealmate.invalid` |
| Shopee | Connector for the Shopee Affiliate Open API is written but **never run against Shopee**. Needs affiliate App ID + Secret |
| Lazada, TikTok Shop | **Not integrated.** Placeholders return no data and report `not_implemented` |
| AI (natural-language, image search, assistant) | Implemented with the Anthropic SDK; **tested only with a fake client** (no API key). Without a key the app uses deterministic rules; image search then returns a clear "AI unavailable" error |
| Push (FCM) | Implemented, **unverified** (no Firebase project). Price alerts still reach the phone through the app's periodic check |
| Android app | Builds; unit, UI (Robolectric) and screenshot tests pass. **Not run on a physical device or emulator** |

So the success criterion — *"my wife can search, see genuine matching offers, find the lowest price
and open the original listing"* — is met for everything **except "genuine offers"**, which needs the
credentials listed under [What you need to provide](#what-you-need-to-provide).

## Repository layout
```
android/          Kotlin + Jetpack Compose app (MVVM, Hilt, Room, Retrofit, WorkManager)
backend/          FastAPI service, Alembic migrations, workers, tests
infrastructure/   Caddy HTTPS config, backup / restore scripts
docs/             Requirements, architecture, database, API, marketplace, AI, UI/UX, tests, deployment, roadmap
scripts/          Dev server, APK build, demo seeding
tests/e2e/        The five specification scenarios over real HTTP
docker-compose.yml, .env.example
```

## Quick start (demo mode, ~5 minutes)
```bash
cp .env.example .env
# edit .env: set POSTGRES_PASSWORD and DEALMATE_BOOTSTRAP_TOKEN (any long random string)
docker compose up -d --build
curl http://127.0.0.1:8000/health            # {"status":"ok","demo_mode":true,...}

curl -s -X POST http://127.0.0.1:8000/api/v1/search \
  -H "Authorization: Bearer <your token>" -H "Content-Type: application/json" \
  -d '{"query": "Tìm kem chống nắng Anessa 60ml dưới 350k"}'
```
Then build/install the app (`android/README.md`), open **Cài đặt**, enter the server URL and token.
Without Docker: `./scripts/dev_backend.sh` (needs a local PostgreSQL).

Full instructions: [docs/DEPLOYMENT_GUIDE.md](docs/DEPLOYMENT_GUIDE.md).

## How it decides "cheapest"
1. **Understand** the request (AI if configured, otherwise rules): brand, product, volume, budget…
   Anything the user did not say stays unknown.
2. **Retrieve** listings from each marketplace in parallel; a slow or failing marketplace is reported
   and the rest still answer.
3. **Normalize and match** each listing: `exact` / `likely` / `similar` / `different`. Different
   volume, model, size, count, storage or pack size is never `exact`; a listing that omits a variant
   attribute is only `likely`. Accessories, used and imported items are kept apart.
4. **Compute** `final = listed − applicable vouchers + shipping + mandatory fees` in code. One shop
   voucher and one platform voucher at most; minimum spend respected; unverified or conditional
   vouchers make the price *estimated*; unknown shipping makes it *incomplete* (no final price).
5. **Rank**: verified prices, then estimated, then incomplete. The banner names the cheapest
   **verified** offer of an exactly matching product and warns if an unverified one might be cheaper.

AI never computes prices or ranks. See [docs/AI_DESIGN.md](docs/AI_DESIGN.md).

## Tests
```bash
cd backend && pip install -e ".[dev,fcm]" && pytest            # needs PostgreSQL (TEST_DATABASE_URL)
python -m scripts.eval_matching --verbose                       # 119 labelled product pairs
DEALMATE_E2E_URL=http://localhost:8000 DEALMATE_E2E_TOKEN=... pytest tests/e2e
cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug
```
Results of the last run are in [docs/TEST_STRATEGY.md](docs/TEST_STRATEGY.md) and the
implementation report below.

## What you need to provide
| Needed | For | Where it goes |
|---|---|---|
| Shopee Affiliate App ID + Secret (affiliate.shopee.vn) | Genuine Shopee listings | `DEALMATE_SHOPEE_AFFILIATE_APP_ID/_SECRET`, `DEALMATE_MOCK_MODE=false` |
| Lazada affiliate API access | Genuine Lazada listings | new connector — see docs/MARKETPLACE_INTEGRATION.md |
| TikTok Shop Partner API access | Genuine TikTok Shop listings | new connector |
| Anthropic API key | Natural-language understanding, image search, AI answers | `ANTHROPIC_API_KEY` (server only) |
| Firebase project + service account | Push notifications | `DEALMATE_FCM_*`, Gradle `dealmate.firebase.*` |
| A host with HTTPS (domain or Tailscale) | Phone ↔ server | `DEALMATE_DOMAIN` |
| Your own release keystore | Updating the app later without reinstalling | `android/keystore.properties` |

## Documentation
[Product requirements](docs/PRODUCT_REQUIREMENTS.md) ·
[System architecture](docs/SYSTEM_ARCHITECTURE.md) ·
[Database design](docs/DATABASE_DESIGN.md) ·
[API specification](docs/API_SPECIFICATION.md) ·
[Marketplace integration](docs/MARKETPLACE_INTEGRATION.md) ·
[AI design](docs/AI_DESIGN.md) ·
[UI/UX specification](docs/UI_UX_SPECIFICATION.md) ·
[Test strategy](docs/TEST_STRATEGY.md) ·
[Deployment guide](docs/DEPLOYMENT_GUIDE.md) ·
[Roadmap](docs/DEVELOPMENT_ROADMAP.md) ·
[Implementation report](docs/IMPLEMENTATION_REPORT.md)
