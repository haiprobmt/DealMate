#!/usr/bin/env sh
# Restore a dump created by backup.sh. THIS REPLACES THE CURRENT DATABASE CONTENT.
#   ./infrastructure/restore.sh backups/dealmate-20260101-030000.sql.gz
set -eu
cd "$(dirname "$0")/.."
file="${1:?usage: restore.sh <backup.sql.gz>}"
gzip -t "$file"
printf 'Restore %s over the current database? Type "yes": ' "$file"
read -r answer
[ "$answer" = "yes" ] || { echo "Aborted."; exit 1; }
docker compose stop api price-monitor notification-worker
gunzip -c "$file" | docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
docker compose start api price-monitor notification-worker
echo "Restore complete."
