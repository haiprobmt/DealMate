# API Specification (v1)

Machine-readable contract: [`openapi.json`](openapi.json) (generated from the code; also served at
`/docs` and `/openapi.json` when `DEALMATE_ENV` is not `production`). Schemas live in `backend/app/schemas/api.py`.

## Conventions
* Base path `/api/v1`. JSON, UTF-8. Money = integer VND. Timestamps = ISO-8601 UTC.
* **Auth:** `Authorization: Bearer <token>` on every `/api/v1/*` route. `GET /health` is public.
* **Correlation id:** send `X-Request-ID` (8–64 chars) or one is generated; echoed in the response
  header, in error bodies and in every log line.
* **Errors** always look like:
  ```json
  {"error": {"code": "validation_error", "message": "Dữ liệu gửi lên không hợp lệ.",
             "details": [{"field": "query", "message": "..."}], "request_id": "…"}}
  ```
  | HTTP | `code` examples |
  |------|-----------------|
  | 401 | `unauthorized` |
  | 404 | `not_found` |
  | 413 / 415 | `image_too_large`, `payload_too_large` / `unsupported_image` |
  | 422 | `validation_error`, `invalid_url`, `unsupported_marketplace`, `short_link_unsupported`, `url_unresolvable` |
  | 429 | `rate_limited` (+ `Retry-After`) |
  | 503 | `ai_unavailable`, `db_unavailable` |
  | 504 | `timeout` |
* **Limits:** 60 requests/min per token (configurable), 45 s per request, 8 s per marketplace,
  images ≤ 5 MB (JPEG/PNG/WebP verified by magic bytes).

## Endpoints
| Method & path | Body / params | Returns |
|---|---|---|
| `GET /health` | – | `{status, demo_mode, version}` |
| `GET /api/v1/me` | – | `MeResponse` |
| `POST /api/v1/search` | `SearchRequest {query, marketplaces?, quantity?, include_similar?}` | `SearchResponse` |
| `POST /api/v1/search/image` | multipart `image`, optional `hint` | `SearchResponse` (may carry `clarification`) |
| `POST /api/v1/search/url` | `{url, marketplaces?}` | `SearchResponse` |
| `GET /api/v1/products/{id}` | – | `ProductDetail` (variants with lowest price) |
| `GET /api/v1/products/{id}/offers` | `variant_id?` | `ProductOffersResponse` (offers, price history, wishlist state) |
| `POST /api/v1/wishlist` | `{variant_id, target_price?}` | `201 WishlistItem` (idempotent) |
| `GET /api/v1/wishlist` | – | `{items: WishlistItem[]}` |
| `DELETE /api/v1/wishlist/{id}` | – | `204` (also removes the item's price alert) |
| `POST /api/v1/price-alerts` | `{variant_id, target_price}` | `201 PriceAlertOut` (upsert) |
| `GET /api/v1/price-alerts` | – | `{items: PriceAlertOut[]}` |
| `DELETE /api/v1/price-alerts/{id}` | – | `204` |
| `GET /api/v1/history` | `limit?` | `HistoryResponse {searches, viewed_products}` |
| `DELETE /api/v1/history` | – | `204` |
| `GET /api/v1/sources/status` | – | `SourcesStatusResponse` (per-marketplace connector health, AI/push availability) |
| `GET /api/v1/explore` | `category?`, `marketplace?` | `TrendingResponse` (recorded price drops only) |
| `GET /api/v1/notifications` | `unread_only?` | `{items: NotificationOut[]}` |
| `POST /api/v1/notifications/{id}/read` | – | `204` |
| `POST /api/v1/devices` | `{token, platform}` | `204` (register FCM token) |
| `POST /api/v1/assistant/ask` | `{question, variant_ids[1..5]}` | `AssistantResponse {answer, generated_by, facts, disclaimer}` |

## `SearchResponse` in brief
```jsonc
{
  "search_id": 12, "search_type": "text",
  "query": {"raw": "...", "interpreter": "rules|ai", "spec": { /* ProductQuery */ }, "notes": []},
  "demo_mode": true,              // true => every offer below is MOCK data
  "partial": false,               // true => at least one marketplace did not answer
  "sources": [{"marketplace": "lazada", "status": "ok|timeout|error|not_configured|not_implemented",
               "is_mock": true, "offers_found": 3, "elapsed_ms": 12, "message": null}],
  "best": {"variant_id": 1, "variant_title": "...", "offer_id": 4, "final_price": 319000,
           "confidence": "verified", "marketplace": "lazada", "explanation": "...", "caveat": "..."},
  "groups": [{                    // exact + likely matches, best first
    "product_id": 1, "variant_id": 1, "title": "Anessa Perfect UV Skincare Milk 60ml",
    "attributes": { /* ProductAttributes; null = unknown */ },
    "match": {"match_type": "exact|likely|similar|different", "confidence": 0.94,
              "matched_attributes": [], "different_attributes": [], "unknown_attributes": [],
              "reason": "...", "decided_by": "rules|ai"},
    "offers": [{ "id": 4, "marketplace": "lazada", "is_mock": true, "title": "...", "url": "...",
      "seller": {"name": "...", "rating": 4.8, "is_official_store": true},
      "price": {"listed_price": 359000, "subtotal": 359000, "voucher_discount": 55000,
                "applied_vouchers": [], "skipped_vouchers": [], "shipping_fee": 15000,
                "shipping_status": "verified|estimated|unknown", "mandatory_fees": 0,
                "final_price": 319000,          // null when confidence = incomplete
                "known_subtotal": 304000, "confidence": "verified|estimated|incomplete", "notes": []},
      "match": { /* MatchResult */ }, "retrieved_at": "...", "is_stale": false, "over_budget": false }],
    "best_offer_id": 4, "lowest_final_price": 319000, "lowest_price_confidence": "verified",
    "offer_count": 4, "in_wishlist": false }],
  "similar_groups": [ /* same shape, match_type = similar */ ],
  "excluded_count": 1, "over_budget_count": 0,
  "clarification": {"needed": true, "message": "...", "suggestions": ["..."]},
  "generated_at": "..."
}
```
Offer order inside a group is final: within-budget first, then verified (ascending final price),
estimated (ascending), incomplete (by known part). Clients must not re-rank across those tiers.
