# Implementation Report

State of the repository at delivery. Everything listed as "verified" was executed during
development; everything else is called out as unverified.

## Milestones
| Milestone | Result |
|---|---|
| 0 Inspection & planning | Done. Empty repository; toolchain and constraints recorded in SYSTEM_ARCHITECTURE.md |
| 1 Foundation | Done. FastAPI + PostgreSQL + Alembic + Docker Compose + health check + CI workflow |
| 2 Android UI | Done. Builds; verified by JVM/Robolectric tests and rendered screenshots only |
| 3 Backend search engine | Done and tested |
| 4 Marketplace integration | **Not achieved.** No credentials: Shopee connector unverified, Lazada/TikTok not integrated |
| 5 AI features | Implemented; tested with a fake client only |
| 6 Personal shopping features | Done on mock data |
| 7 QA & release | Automated tests green, signed APK built; no on-device QA, no real-link validation |

## What actually works (verified)
* Text search with a Vietnamese rule-based interpreter (brand, model, volume/weight, size, count,
  storage, colour, pack, budget), URL search for mock links and slug fallback for real links.
* Parallel connector calls with per-marketplace timeout, failure isolation and partial results.
* Normalization, deterministic matching, variant clustering, vague-listing downgrade, accessory /
  used / imported / combo separation.
* Final-price calculation (voucher eligibility, stacking limits, caps, min spend, fees),
  three-tier ranking, best-offer banner with caveats, budget filtering.
* Persistence of products, variants, offers, sellers, price history; history; explore from recorded
  data; wishlist; target prices; periodic price check; notification creation without duplicates;
  in-app notification delivery; rule-based assistant.
* Token auth on every API route, error envelope, request ids, rate limit, body-size limit.
* Docker Compose stack (db + api + two workers) — started and exercised, including backup script.
* Android: all five tabs, results, product detail, assistant sheet, filters, settings, offline
  cache, demo labelling — under Robolectric.

## Test results (last run)
| Suite | Result |
|---|---|
| Backend `pytest` (PostgreSQL 16) | **129 passed** |
| Matching evaluation, 119 labelled pairs | exact precision **1.000**, exact recall **0.972**, accuracy 0.992 (development set — rules were tuned on it) |
| HTTP end-to-end scenarios against the Docker stack | **5 passed** |
| Android `testDebugUnitTest` | **140 passed**, 0 failed, 0 skipped (incl. contract test on 14 real backend responses) |
| Android `lintDebug` | 0 errors, 40 warnings (mostly "newer dependency version available") |
| Android `assembleDebug` / `assembleRelease` (R8) | both succeed; release APK signed and `apksigner verify` passes |
| `ruff check` | clean |
| GitHub Actions (`backend`, `e2e`, `android` jobs) | **all passed** on GitHub runners; the e2e job builds and runs the committed Docker stack unmodified, the android job runs the tests, lint and debug build and uploads the APK |

Inside the development sandbox the Docker stack needed two local-only workarounds (proxy CA
during `pip install`, database addressed by IP). The committed configuration itself was then
confirmed by the GitHub `e2e` job, which builds and runs it unmodified.

## Marketplace integrations verified
**None.** See MARKETPLACE_INTEGRATION.md.

## Still mock / unverified
| Item | Why |
|---|---|
| All offers, sellers, prices, vouchers, ratings | Mock catalogue (labelled) — no marketplace credentials |
| Shopee Affiliate connector | Written from the public API shape; never called Shopee |
| Lazada, TikTok Shop connectors | Not implemented |
| AI query interpretation, image search, AI matching, AI assistant | No Anthropic API key; fake client in tests |
| FCM push | No Firebase project |
| Android on a device | No emulator: camera, photo picker, Keystore encryption, WorkManager scheduling, locale switch, the minified release APK at runtime are all unexercised |

## Known limitations and bugs
* Real offers from an affiliate feed will have no shipping fee or buyer vouchers, so they will be
  shown as "thiếu phí vận chuyển" and no verified cheapest can be declared until shipping is sourced.
* Brand recognition uses a fixed lexicon (~45 brands). Listings of unknown brands are never `exact`.
* Category/trait rules cover six product families (sunscreen, hair dryer, diapers, detergent,
  phones, air fryers); other categories fall back to brand + model + measured attributes.
* Imported ("xách tay") items count as matching the request when the user did not specify origin;
  they are shown as a separate, labelled variant and can become the best offer.
* Explore's "recently seen" rows are not tappable (the API item has no `product_id`).
* The default delivery location is stored on the phone only; it does not influence shipping fees.
* "Find a cheaper alternative" exists only as the similar-products section.
* Category shortcuts, example queries and assistant chips stay Vietnamese in the English locale.
* Rate limiting and the notification queue assume a single API process.
* Short marketplace links (`s.shopee.vn`, `vt.tiktok.com`) are rejected.
* Two sandbox build helper files (`backend/.Dockerfile.sandbox`, `backend/.sandbox-ca.crt`, a public
  proxy CA bundle) were committed by mistake in an earlier commit and removed in a later one; they
  remain in git history and contain no secrets.

## Required credentials
Shopee Affiliate App ID + Secret; Lazada affiliate API access; TikTok Shop partner API access;
Anthropic API key; Firebase project + service account; a host with HTTPS. Details in the README.

## Recommended next actions
1. Obtain Shopee Affiliate credentials and run the first-run checklist (MARKETPLACE_INTEGRATION.md).
2. Install the APK on the phone and do a manual pass over every screen.
3. Add the Anthropic key; try real queries and photos; tune prompts.
4. Decide how to source shipping fees for real offers.
5. Apply for Lazada and TikTok Shop API access.
6. Replace the delivery keystore with your own and keep it backed up.
