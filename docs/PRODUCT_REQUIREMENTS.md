# DealMate — Product Requirements

## 1. Purpose
DealMate is a **private, family-use** Android shopping assistant for Vietnam. It answers one
question well: *"Where can I buy **this exact product** for the lowest **actual** price?"*

> Core principle: **find the cheapest matching product, not simply the cheapest product.**

Primary user: a family member (the owner's wife) who wants to buy conveniently at the lowest
real price. Not a public SaaS; no sign-up, no multi-tenant features.

## 2. Product rules (non-negotiable)
| # | Rule | Where enforced |
|---|------|----------------|
| R1 | Different model / volume / weight / count / size / storage / pack are never merged as the same product | `services/matching.py`, labelled dataset |
| R2 | Unknown attributes stay unknown; nothing is guessed | `services/normalization.py`, AI output validation |
| R3 | Final price = listed − applicable vouchers + shipping + mandatory fees, computed by deterministic code | `services/pricing.py` |
| R4 | Unknown shipping is **not** 0đ: the offer is `incomplete` and can never be "cheapest" | `services/pricing.py`, `repositories/catalog.pick_best` |
| R5 | Ranking: verified final price → estimated final price → incomplete | `pricing.ranking_key` |
| R6 | AI never ranks, never computes prices, and may only use retrieved data | `ai/assistant.py`, `ai/match_judge.clamp` |
| R7 | No invented listings, sellers, prices, reviews, discounts or "trending" items | connectors raise instead of inventing; Explore is built from recorded data only |
| R8 | Mock data is never presented as live and never mixed with live data | `connectors/registry.py`, `is_mock` on every offer, demo banner in the app |
| R9 | Never claim a product is authentic/authorised; only relay the marketplace's own Mall/Official label | assistant prompt + rule answers, UI wording |
| R10 | Every offer keeps its original listing URL and retrieval time | `RawOffer.url`, `retrieved_at`, `is_stale` |

## 3. Navigation and features
| Section | Features |
|---------|----------|
| **Trang chủ (Home)** | Natural-language search, image search (gallery/camera), product-URL search, recent searches, category shortcuts, recently viewed, quick link to wishlist |
| **Khám phá (Explore)** | Price drops and recently seen products **recorded by this installation**, category and marketplace filters. Empty state with an explanation when there is no data |
| **Yêu thích (Wishlist)** | Saved products, current lowest price, previous recorded price, target price, remove |
| **Lịch sử (History)** | Past searches with timestamps, repeat search, viewed products, clear history |
| **Cài đặt (Settings)** | Language, preferred marketplaces, default sorting, notifications, default delivery location, server URL + access token, source/AI status, app info |
| Results | Interpreted query, per-marketplace status, best-offer banner, product groups, similar products, filter/sort |
| Product detail | Offers sorted by final price, price breakdown, price history chart, wishlist + target price, open original listing, AI assistant |

## 4. Search methods
1. **Text** — "Tìm kem chống nắng Anessa 60ml dưới 350k" → structured query (brand, product, model,
   category, variant, size/weight/volume, quantity, colour, budget, constraints).
2. **Image** — photo/screenshot → vision model identifies the product → same pipeline. Low confidence
   → the app asks the user to choose/clarify instead of guessing. Requires an AI key on the server.
3. **URL** — Shopee / Lazada / TikTok Shop link → marketplace + listing id → listing metadata through
   the authorised connector → compare on the other marketplaces. If the listing cannot be retrieved,
   the product name in the link is used and the user is told so.

## 5. Out of scope (deliberately)
Checkout/ordering inside the app, account linking with marketplaces, scraping or bypassing
anti-bot protections, public multi-user sign-up, product reviews aggregation.

## 6. Success criterion
*My wife can search for a product, see genuine matching offers from supported marketplaces, identify
the lowest available purchase price, and open the original listing to buy it.*

**Current status of that criterion:** the full workflow works end to end against **mock**
marketplace data. Genuine offers require marketplace API credentials that are not available yet —
see [MARKETPLACE_INTEGRATION.md](MARKETPLACE_INTEGRATION.md).
