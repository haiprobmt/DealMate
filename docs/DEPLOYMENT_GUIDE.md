# Deployment Guide (private family use)

## 1. What you need
* A machine that stays on (mini PC, NAS, Raspberry Pi 4+, or a small VPS) with Docker + Compose v2.
* For the phone to reach it over **HTTPS** (the release APK refuses plain HTTP), one of:
  * a domain name pointing at the machine with ports 80/443 reachable → built-in Caddy profile; or
  * a private mesh VPN with HTTPS certificates (e.g. Tailscale `tailscale serve`) → no open ports; or
  * your own reverse proxy.

## 2. Start the backend
```bash
git clone <this repo> && cd DealMate
cp .env.example .env
# edit .env: POSTGRES_PASSWORD, DEALMATE_BOOTSTRAP_TOKEN (or create users later), DEALMATE_DOMAIN
docker compose up -d --build                 # db + api + price-monitor + notification-worker
docker compose --profile https up -d         # adds Caddy with automatic HTTPS
curl http://127.0.0.1:8000/health            # {"status":"ok","demo_mode":true,...}
```
Migrations run automatically when the API starts. If Docker Hub rate-limits image pulls, set the
`*_IMAGE` overrides at the bottom of `.env.example`.

Create one access token per family member (shown once, only its hash is stored):
```bash
docker compose exec api python -m scripts.create_user "Vợ"
```

## 3. Modes
* `DEALMATE_MOCK_MODE=true` — demo. All offers are labelled mock data; safe for trying the app.
* `DEALMATE_MOCK_MODE=false` — live. Only configured real connectors return data; see
  [MARKETPLACE_INTEGRATION.md](MARKETPLACE_INTEGRATION.md) for credentials. Old mock rows stay in
  the database but are never mixed into Explore; use a fresh database for a clean live setup.

Optional: `ANTHROPIC_API_KEY` (natural-language interpretation, image search, AI answers),
`DEALMATE_FCM_PROJECT_ID` + `DEALMATE_FCM_SERVICE_ACCOUNT_FILE` (push; mount the JSON file into the
`api`, `price-monitor` and `notification-worker` containers).

## 4. Install the Android app
1. Build: `./scripts/build_apk.sh release https://your-domain/` (or download the CI artifact / the
   APK provided with this delivery). Signing: see `android/README.md`.
2. Copy the APK to the phone, open it, allow "install unknown apps" for the file manager once.
3. Open DealMate → **Cài đặt** → enter the server URL and the access token → **Kiểm tra kết nối**.
4. Allow notifications when asked (Android 13+).

Keep the signing keystore and its password safe: an update must be signed with the same key,
otherwise Android requires uninstalling the old app first.

## 5. Price checks and notifications
`price-monitor` re-queries every saved product every `DEALMATE_PRICE_CHECK_INTERVAL_MINUTES`
(default 360). This is periodic polling, **not** real-time monitoring. `notification-worker`
delivers alerts: via FCM when configured, otherwise they are picked up by the app's hourly
background check and shown as local notifications.

## 6. Backup and recovery
```bash
./infrastructure/backup.sh                    # backups/dealmate-YYYYmmdd-HHMMSS.sql.gz, keeps 14
./infrastructure/restore.sh backups/<file>    # asks for confirmation, replaces current data
```
Schedule the backup with cron (`0 3 * * * /path/DealMate/infrastructure/backup.sh`) and copy
`backups/` off the machine. Also keep a copy of `.env` (it holds the DB password and tokens) in a
password manager. Disaster recovery = fresh clone + `.env` + `docker compose up -d` + `restore.sh`.

## 7. Updating
```bash
git pull && docker compose up -d --build      # migrations apply on start
```
Take a backup first.

## 8. Security checklist
- [ ] Strong `POSTGRES_PASSWORD`; database port not published (default).
- [ ] API bound to `127.0.0.1` and reached only through HTTPS (Caddy/Tailscale/reverse proxy).
- [ ] One token per person; revoke by deleting the row in `users`.
- [ ] `.env`, keystores and service-account files are git-ignored — never commit them.
- [ ] AI and marketplace keys exist only on the server (the APK contains none).
- [ ] Backups stored off-device.

## 9. Troubleshooting
| Symptom | Check |
|---|---|
| App: "Chưa kết nối máy chủ" | URL must be `https://…/`; token correct; `curl https://domain/health` |
| 401 | Token mistyped or user deleted |
| No offers in live mode | `GET /api/v1/sources/status` → connector not configured/implemented |
| Image search 503 `ai_unavailable` | `ANTHROPIC_API_KEY` missing or invalid |
| No notifications | Target price reached? `docker compose logs price-monitor notification-worker`; app notification permission |
