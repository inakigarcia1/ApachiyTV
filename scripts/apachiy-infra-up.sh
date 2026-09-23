#!/usr/bin/env bash
# =============================================================================
# apachiy-infra-up.sh — start the Apachiy Supabase self-hosted stack
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_DIR="$ROOT_DIR/infra/supabase"
ENV_FILE="$COMPOSE_DIR/.env"

cd "$COMPOSE_DIR"

if [ ! -f "$ENV_FILE" ]; then
  echo "[apachiy-infra-up] .env missing in $COMPOSE_DIR"
  echo "  cp $COMPOSE_DIR/.env.example $ENV_FILE"
  echo "  then edit and fill POSTGRES_PASSWORD, JWT_SECRET, ANON_KEY, SERVICE_ROLE_KEY, GOTRUE_ADMIN_PASSWORD"
  exit 1
fi

# Ensure required secrets are present (no defaults allowed for these)
required_keys=(POSTGRES_PASSWORD JWT_SECRET ANON_KEY SERVICE_ROLE_KEY GOTRUE_ADMIN_PASSWORD)
for k in "${required_keys[@]}"; do
  v=$(grep -E "^${k}=" "$ENV_FILE" | head -1 | cut -d= -f2-)
  if [ -z "$v" ]; then
    echo "[apachiy-infra-up] required env var $k is empty in $ENV_FILE"
    exit 1
  fi
done

FUNCTIONS_MAIN="$COMPOSE_DIR/volumes/functions/main/index.ts"
if [ ! -f "$FUNCTIONS_MAIN" ]; then
  echo "[apachiy-infra-up] missing edge function entrypoint: $FUNCTIONS_MAIN"
  exit 1
fi

echo "[apachiy-infra-up] starting stack..."
docker compose --env-file "$ENV_FILE" -f docker-compose.yml up -d

echo "[apachiy-infra-up] waiting for edge functions to boot..."
for i in $(seq 1 30); do
  status="$(docker inspect apachiy-supabase-functions --format '{{.State.Status}}' 2>/dev/null || true)"
  if [ "$status" = "running" ]; then
    if docker logs apachiy-supabase-functions 2>&1 | grep -q "Apachiy edge main router started"; then
      echo "[apachiy-infra-up] edge functions ready."
      break
    fi
  fi
  if [ "$status" = "restarting" ] || [ "$status" = "exited" ]; then
    echo "[apachiy-infra-up] edge functions failed to boot (status=$status); recreating with fresh bind mount..."
    docker compose --env-file "$ENV_FILE" -f docker-compose.yml up -d --force-recreate supabase-edge-functions
    sleep 5
  fi
  sleep 2
done

if ! docker logs apachiy-supabase-functions 2>&1 | grep -q "Apachiy edge main router started"; then
  echo "[apachiy-infra-up] WARN: edge functions did not log a successful boot."
  echo "  Tail logs: docker logs apachiy-supabase-functions"
fi

echo "[apachiy-infra-up] waiting for services to report healthy..."
healthy=0
for i in $(seq 1 60); do
  healthy=$(docker compose --env-file "$ENV_FILE" -f docker-compose.yml ps --format json 2>/dev/null \
    | grep -c '"Health":"healthy"' || true)
  if [ "$healthy" -ge 6 ]; then
    echo "[apachiy-infra-up] $healthy services healthy."
    break
  fi
  sleep 5
done

if [ "$healthy" -lt 6 ]; then
  echo "[apachiy-infra-up] WARN: only $healthy services healthy after 5 min."
  echo "  Tail logs with: $SCRIPT_DIR/apachiy-infra-logs.sh"
  exit 1
fi

# After Postgres image/recreate, GoTrue/PostgREST keep stale sockets until restarted.
echo "[apachiy-infra-up] refreshing DB client pools (auth, rest, storage, realtime)..."
docker compose --env-file "$ENV_FILE" -f docker-compose.yml restart \
  supabase-auth supabase-rest supabase-storage supabase-realtime >/dev/null
sleep 5

echo
echo "Apachiy Supabase is up."
echo "  Kong gateway:   $(grep '^API_EXTERNAL_URL=' "$ENV_FILE" | cut -d= -f2-)"
echo "  Studio:         $(grep '^STUDIO_PUBLIC_URL=' "$ENV_FILE" | cut -d= -f2-)"
echo "  Apply migrations:"
echo "    psql \"postgres://supabase_admin:\$POSTGRES_PASSWORD@localhost:5432/postgres\" -f \"$ROOT_DIR/supabase/migrations/\"*.sql"