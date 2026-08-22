# Apachiy TV — Supabase self-hosted infra

This directory contains the Docker Compose stack for running a self-hosted
Supabase instance that backs Apachiy TV.

## Quick start

```bash
# 1. Generate secrets and create the env file
cp .env.example .env
# Edit .env and set POSTGRES_PASSWORD, JWT_SECRET, ANON_KEY, SERVICE_ROLE_KEY,
# GOTRUE_ADMIN_PASSWORD (use `openssl rand -hex 32` for each).

# 2. Start the stack
./scripts/apachiy-infra-up.sh

# 3. Verify health
./scripts/apachiy-health.sh

# 4. Apply Apachiy schema migrations
psql "postgres://supabase_admin:$POSTGRES_PASSWORD@localhost:5432/postgres" \
  -f ../supabase/migrations/*.sql
```

## Services

| Service | Port (host) | Purpose |
|---|---|---|
| `supabase-kong` | 8000 / 8443 | API gateway (single entrypoint) |
| `supabase-studio` | 3001 | Web UI for managing the DB |
| `supabase-auth` | 9999 (internal) | GoTrue email/password auth |
| `supabase-rest` | 3000 (internal) | PostgREST (auto-generated REST over tables) |
| `supabase-storage` | 5000 (internal) | S3-like storage (avatars bucket) |
| `supabase-realtime` | 4000 (internal) | WebSocket subscriptions |
| `supabase-edge-functions` | 9000 (internal) | Deno functions (tv-logins-exchange) |
| `supabase-meta` | 8080 (internal) | Postgres metadata for Studio |
| `supabase-imgproxy` | 5001 (internal) | On-the-fly image resizer |
| `supabase-analytics` | 4000 (internal) | Logflare for log shipping |
| `supabase-db` | 5432 (internal) | Postgres 15 |

## Production

For production, put this stack behind a reverse proxy (Caddy or Nginx) that
terminates TLS. See `docs/PRODUCTION_DEPLOYMENT.md`.

## Backups

```bash
./scripts/apachiy-backup.sh                    # full DB dump
./scripts/apachiy-restore.sh backups/db-...    # restore from a dump
```