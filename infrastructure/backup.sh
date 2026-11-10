#!/usr/bin/env sh
# Dump the DealMate database to ./backups/dealmate-YYYYmmdd-HHMMSS.sql.gz (keeps the last 14).
# Run from the repository root:  ./infrastructure/backup.sh     (cron: 0 3 * * *)
set -eu
cd "$(dirname "$0")/.."
mkdir -p backups
file="backups/dealmate-$(date +%Y%m%d-%H%M%S).sql.gz"
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' | gzip > "$file"
gzip -t "$file"
ls -1t backups/dealmate-*.sql.gz | tail -n +15 | xargs -r rm --
echo "Backup written: $file"
