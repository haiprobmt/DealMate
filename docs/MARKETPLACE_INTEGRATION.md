# Marketplace Integration

## Status (honest summary)
| Marketplace | Connector | Status | Verified against the live API? |
|---|---|---|---|
| Shopee VN | `ShopeeAffiliateConnector` (Shopee Affiliate Open API) | Code complete, credential-gated | **No** — no affiliate credentials were available. Tested only against a fake HTTP transport |
| Lazada VN | `LazadaConnector` (pending) | **Not integrated** — returns no data, reports `not_implemented` | No |
| TikTok Shop VN | `TikTokShopConnector` (pending) | **Not integrated** — returns no data, reports `not_implemented` | No |
| All three (demo) | `MockConnector` | Working, clearly labelled mock data | n/a (not a marketplace) |

**No real marketplace integration has been verified.** With `DEALMATE_MOCK_MODE=false` and no
credentials, searches return *zero* offers and each source reports why. Nothing is faked.

The marketplace developer portals could not be opened from the development environment
(network policy), so the notes below combine public third-party descriptions with what is
generally known; treat anything marked *unconfirmed* as needing a check in the official portal.

## Connector contract (`backend/app/connectors/base.py`)
```python
class MarketplaceConnector:
    marketplace: Marketplace; connector_name: str; is_mock: bool
    async def search_products(self, query, limit=30) -> list[RawOffer]
    async def get_product_details(self, product_id) -> RawOffer | None
    async def get_product_url(self, product_id) -> str
    async def health_check(self) -> ConnectorHealth   # status, message, verified_live
```
Rules every connector follows: official/authorised APIs and feeds only; never bypass login, CAPTCHA
or anti-bot controls; raise `ConnectorError` / `ConnectorNotConfigured` / `ConnectorNotImplemented`
instead of inventing data; keep the marketplace's listing id and URL; stamp `retrieved_at`;
`shipping_fee=None` when the source does not give shipping. The orchestrator additionally drops any
offer whose `is_mock`/`marketplace` does not match its connector, runs every connector under its own
timeout, and reports per-source status (`ok`, `timeout`, `error`, `not_configured`, `not_implemented`).

## Shopee — Affiliate Open API
* **Why this API:** the Shopee Open Platform is for sellers managing their own shop; it has no
  buyer-side catalogue search. The Affiliate Open API exposes product offers to affiliates.
* **How to get access:** register at <https://affiliate.shopee.vn>, get approved, then copy the
  **App ID** and **Secret** from the Open API section of the affiliate dashboard.
* **Implemented:** `POST https://open-api.affiliate.shopee.vn/graphql`, header
  `Authorization: SHA256 Credential={appId}, Timestamp={ts}, Signature={sha256(appId+ts+payload+secret)}`,
  query `productOfferV2(keyword, limit, page, sortType)` / `(shopId, itemId)` returning
  `itemId, productName, priceMin, priceMax, imageUrl, shopId, shopName, ratingStar, sales, productLink`.
  This shape was cross-checked against an open-source community client; it was **not** run against Shopee.
* **Known limitation even when it works:** the affiliate API returns no shipping fee and no
  buyer vouchers. Every Shopee offer will therefore be `incomplete` (shown with "chưa gồm phí vận
  chuyển") and cannot be declared the verified cheapest. It still gives genuine listings, prices and links.
* **Enable:** set `DEALMATE_MOCK_MODE=false`, `DEALMATE_SHOPEE_AFFILIATE_APP_ID`,
  `DEALMATE_SHOPEE_AFFILIATE_SECRET`; then open *Cài đặt → Nguồn dữ liệu* (or
  `GET /api/v1/sources/status`). `verified_live: true` appears only after a real, well-formed response.
* **First-run checklist:** (1) health check is `available`; (2) search a known product and compare
  3 results with the Shopee app — title, price, shop; (3) open `url` and confirm it is the same
  listing; (4) check rate limits in the dashboard; (5) review Shopee's affiliate terms for
  personal price-comparison use, and whether links must be affiliate (`offerLink`) links.

## Lazada
* Lazada Open Platform (`open.lazada.com`) is seller-oriented (orders, own products).
* A separate affiliate programme (LazAffiliates) reportedly offers product feed/search and link
  generation with app key + secret + HMAC-SHA256 signing — *unconfirmed*; needs an approved
  affiliate account and the official endpoint documentation.
* **To integrate:** obtain affiliate API access → implement `LazadaConnector.search_products` /
  `get_product_details` against the documented endpoints (replace the class in
  `connectors/pending.py`) → add a fake-transport test like `test_shopee_request_signing_and_response_mapping`
  → run the first-run checklist above.

## TikTok Shop
* TikTok Shop Partner Center APIs require registering an app (ISV/partner) and approval; affiliate
  APIs can search products that have open collaborations by keyword/category — availability for
  Vietnam and for a personal, non-seller app is *unconfirmed*.
* **To integrate:** obtain Partner Center app credentials and the relevant scopes → implement
  `TikTokShopConnector` → tests → checklist.

## URL search
`services/url_parser.py` recognises `shopee.vn` (`…-i.<shop>.<item>`, `/product/<shop>/<item>`),
`lazada.vn` (`…-i<item>-s<sku>.html`), `tiktok.com` (`/view/product/<id>`) by exact host match
(look-alike hosts are rejected). Short links (`s.shopee.vn`, `vt.tiktok.com`, …) are refused with a
clear message because resolving them would mean fetching the marketplace page. The listing is then
fetched through that marketplace's connector; if that is impossible the product name embedded in the
link (Shopee/Lazada slugs) is searched instead and the response says the original listing is not included.

## Mock mode
`DEALMATE_MOCK_MODE=true` (default in `.env.example`). 53 listings for six products
(Anessa 60ml, Dyson hair dryer, Merries size L, Ariel 3.6kg, iPhone 16 Pro 256GB, Philips air fryer)
including deliberate traps: other volumes, combos, imported/used items, accessories, vague titles,
missing shipping, conditional vouchers. Every mock listing has a `[MẪU]` title prefix, `MOCK-` ids,
`is_mock: true` and a `https://mock.dealmate.invalid/...` URL (the `.invalid` TLD can never resolve).
The app shows a demo banner and a DEMO tag and refuses to open these links in a browser.

## Adding a marketplace
1. Add the code to `Marketplace` (`schemas/domain.py`) and its display name.
2. Implement `MarketplaceConnector`; map the API response to `RawOffer` without filling gaps.
3. Register it in `connectors/registry.py` (live list) and add a mock entry if useful.
4. Add host patterns to `services/url_parser.py`.
5. Tests with `httpx.MockTransport`; then verify live and record the result in this file.
