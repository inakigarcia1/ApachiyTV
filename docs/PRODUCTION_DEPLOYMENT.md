# Apachiy TV — Production Deployment Guide

> This document is for the **operator** who will deploy Apachiy TV's
> self-hosted infrastructure to a real server. It assumes you have already
> forked the NuvioTV/ApachiyTV source and produced an APK per the
> [README-APACHIY.md](../README-APACHIY.md) walk-through.

## 1. Hardware sizing

The stack runs comfortably on a single VPS. Recommended starting point:

| Component | Min | Recommended |
|---|---|---|
| CPU | 2 vCPU | 4 vCPU |
| RAM | 4 GB | 8 GB |
| Disk | 30 GB SSD | 60 GB SSD (Postgres + Storage buckets) |
| Network | 100 Mbps | 1 Gbps |

Memory distribution (defaults in `infra/supabase/docker-compose.yml`):

- `supabase-db` (Postgres) — 1 GB
- `supabase-auth` (GoTrue) — 512 MB
- `supabase-rest` (PostgREST) — 256 MB
- `supabase-storage` — 512 MB
- `supabase-realtime` — 256 MB
- `supabase-studio` — 512 MB
- `supabase-kong` — 512 MB
- `supabase-edge-functions` — 256 MB
- `supabase-imgproxy` — 256 MB
- `supabase-analytics` — 256 MB

Override with `APACHIY_SUPABASE_*_MEM` / `APACHIY_SUPABASE_*_CPUS` env vars.

## 2. DNS

Pick a domain (we'll call it `apachiy.example`) and point an A record at
your server. You'll need at minimum:

- `apachiy.example` — Kong API gateway (HTTPS)
- `studio.apachiy.example` — Supabase Studio (optional, can be IP-restricted)
- `auth.apachiy.example` — GoTrue (the Supabase gateway proxies here; usually
  no separate A record needed)
- `db.apachiy.example` — Postgres (private network only; do NOT expose)

If you do not yet have a domain, use the server's IP for testing and put a
real domain in front before opening sign-ups.

## 3. Reverse proxy (Caddy)

Caddy is the recommended reverse proxy — it auto-provisions Let's Encrypt
certificates. Example `Caddyfile`:

```caddyfile
apachiy.example, *.apachiy.example {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8000  # kong
}

studio.apachiy.example {
    basicauth {
        admin $2a$14$HASHED_OPERATOR_PASSWORD
    }
    reverse_proxy 127.0.0.1:3001  # supabase studio
}
```

`caddy reload` after writing the file. Caddy will fetch certs automatically.

If you prefer Nginx + Certbot, see the official Supabase self-hosted
docs for the equivalent config.

## 4. Supabase configuration

Copy `infra/supabase/.env.example` to `infra/supabase/.env` and fill:

```bash
# Mandatory secrets
POSTGRES_PASSWORD=$(openssl rand -hex 32)
JWT_SECRET=$(openssl rand -hex 32)
GOTRUE_ADMIN_PASSWORD=$(openssl rand -hex 32)

# Generate ANON_KEY and SERVICE_ROLE_KEY
ANON_KEY=$(supabase gen keys --type anon)
SERVICE_ROLE_KEY=$(supabase gen keys --type service_role)
```

The `ANON_KEY` goes into the TV APK (it is the public client key). The
`SERVICE_ROLE_KEY` is server-side only — never commit it, never ship it in
the APK.

## 5. SMTP

If you want email confirmation, password reset, etc., set:

```env
SMTP_HOST=smtp.your-provider.com
SMTP_PORT=587
SMTP_USER=apachiy@your-domain.example
SMTP_PASSWORD=<app-password>
SMTP_FROM_ADMIN=noreply@your-domain.example
SMTP_SENDER_NAME=Apachiy
ENABLE_AUTOCONFIRM=false
```

For local development, install `mailhog` or `smtp4dev` and point SMTP_HOST
at it. The simplest path is to leave `ENABLE_AUTOCONFIRM=true` (signup
succeeds without an email round-trip) for the first rollout, then
enable SMTP once you're ready to verify emails.

## 6. Apply migrations

```bash
cd /opt/apachiy-nuviofork   # or wherever you cloned the repo
./scripts/apachiy-infra-up.sh
./scripts/apachiy-infra-health.sh
# apply migrations
for m in supabase/migrations/*.sql; do
  docker exec -i apachiy-supabase-db \
    psql -U supabase_admin -d postgres -v ON_ERROR_STOP=1 < "$m"
done
```

For local dev, `./scripts/bootstrap-apachiy.sh` does all of the above in
one shot.

## 7. Wire the Apachiy .NET API

Your existing `D:\Proyectos\Apachiy-Repos\Apachiy` (or wherever the .NET API
lives) must be able to validate Supabase JWTs. Add to its
`docker-compose.yml` / `.env`:

```env
APACHIY_SUPABASE_URL=https://apachiy.example
APACHIY_SUPABASE_JWT_SECRET=<same JWT_SECRET as the Supabase .env>
```

The `apachiy-api` service then validates incoming `/v1/devices/register`
calls with HS256, extracts the `sub` claim, and upserts into the
`user_devices` table.

The .NET API also needs the `Jwt:Supabase:*` settings in `appsettings.json`
(only `Issuer` and `Secret` are required; `Audience` defaults to
`authenticated`).

## 8. APK release signing

1. Generate a keystore:
   ```bash
   keytool -genkey -v \
     -keystore keystore/apachiy.jks \
     -alias apachiy \
     -keyalg RSA -keysize 2048 -validity 9125 \
     -storepass "$KEYSTORE_PASS" -keypass "$KEY_PASS" \
     -dname "CN=Apachiy TV, O=Apachiy, C=AR"
   ```
2. Encode for CI (GitHub Actions uses base64):
   ```bash
   base64 -i keystore/apachiy.jks -o keystore/apachiy.jks.b64
   ```
3. Store the encoded keystore + passwords as CI secrets
   (`APACHIY_RELEASE_KEYSTORE_BASE64`, `APACHIY_RELEASE_KEY_ALIAS`,
   `APACHIY_RELEASE_KEY_PASSWORD`, `APACHIY_RELEASE_STORE_PASSWORD`).
4. Build the signed APK:
   ```bash
   ./gradlew :app:assembleFullRelease
   # or
   ./gradlew :app:bundleFullRelease  # AAB for Play Store
   ```
5. Distribute via Play Console or sideload via your website.

## 9. Backups

Postgres:

```bash
./scripts/apachiy-backup.sh   # creates backups/apachiy-db-<timestamp>.{dump,sql}
```

Restore:

```bash
./scripts/apachiy-restore.sh backups/apachiy-db-20260818T120000Z.dump
```

Storage bucket `avatars`: rsync the `supabase_storage_data` Docker volume:

```bash
docker run --rm -v apachiy-supabase-storage-data:/data -v $(pwd):/backup \
    alpine tar czf /backup/storage-$(date +%F).tgz -C /data .
```

For production, add a cron:

```cron
0 3 * * * cd /opt/apachiy-nuviofork && ./scripts/apachiy-backup.sh >> /var/log/apachiy-backup.log 2>&1
0 4 * * * rsync -a /var/lib/docker/volumes/apachiy-supabase-storage-data/_data/ /backups/storage/$(date +\%F)/
```

Retention: keep 7 daily, 4 weekly, 6 monthly; prune older files with a
small shell script or a tool like `restic` / `borgbackup`.

## 10. Health checks

```bash
./scripts/apachiy-health.sh
```

Caddy can be configured to hit `/health` on Kong as part of its own
upstream health probe. The .NET API exposes `/health/live` and
`/health/ready` on its own port (8080 by default).

## 11. Upgrades

```bash
cd /opt/apachiy-nuviofork
git pull
./scripts/apachiy-infra-up.sh    # rolls containers
# Apply any new migrations
for m in supabase/migrations/*.sql; do
  docker exec -i apachiy-supabase-db \
    psql -U supabase_admin -d postgres -v ON_ERROR_STOP=1 < "$m"
done
# Build a new APK and roll it out via Play Store or sideload
```

## 12. Secrets management

- `infra/supabase/.env` — never commit; chmod 600.
- `keystore/apachiy.jks` — never commit; chmod 600.
- `release.keystore.properties` — never commit; chmod 600.
- Production secrets live in your secrets manager of choice
  (HashiCorp Vault, AWS Secrets Manager, Bitwarden, etc.) and are
  injected as env vars at container start.

## 13. Monitoring

The Kong gateway exposes Prometheus metrics at `/metrics`. The .NET API
also exposes Prometheus metrics. Wire to your existing Prometheus /
Grafana stack and alert on:

- `kong_http_status` 5xx rate > 1%
- `apachiy_api_request_duration_seconds` p99 > 2s
- Postgres connection count > 80% of `max_connections`
- Storage bucket total size > 80% of disk

## 14. Known limitations

- The Apachiy TV client itself does not yet auto-update via the Play Store
  in this fork — for production, the in-app updater fetches the latest
  release from `https://github.com/inakigarcia1/ApachiyTV/releases` (set via
  `BuildConfig.GITHUB_OWNER` / `GITHUB_REPO`). Publish a release there
  before you start accepting users.
- No email-based password reset is wired up unless you configure SMTP
  (section 5). Without SMTP, only QR-login works.
- The device GUID is stored in `apachiy_installation.xml` SharedPreferences
  with `disableAutoBackup()`. Verified in unit tests; full uninstall/reinstall
  validation requires a physical Android TV device.
