#!/usr/bin/env sh
# Seed data for the demo: runs the six example searches against a running demo-mode API so that
# products, variants, offers and price history exist (the "seed data" IS the labelled mock
# catalogue in backend/app/connectors/mock_data/catalog.json — nothing else is inserted).
#   ./scripts/seed_demo.sh http://localhost:8000 <token>
set -eu
base="${1:-http://localhost:8000}"
token="${2:?usage: seed_demo.sh <base-url> <token>}"
for q in "kem chống nắng Anessa 60ml" "máy sấy tóc Dyson" "bỉm Merries size L" \
         "nước giặt Ariel 3.6kg" "iPhone 16 Pro 256GB" "nồi chiên không dầu Philips"; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$base/api/v1/search" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    --data "{\"query\": \"$q\"}")
  echo "$code  $q"
done
