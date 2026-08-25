#!/usr/bin/env bash
# =============================================================================
# apachiy-health.sh — quick health probes for the Apachiy self-hosted stack
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$ROOT_DIR/infra/supabase/.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "missing $ENV_FILE — run scripts/apachiy-infra-up.sh first"
  exit 1
fi

API_URL=$(grep -E '^API_EXTERNAL_URL=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')
ANON_KEY=$(grep -E '^ANON_KEY=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')
STUDIO_URL=$(grep -E '^STUDIO_PUBLIC_URL=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')
POSTGRES_PASSWORD=$(grep -E '^POSTGRES_PASSWORD=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')
POSTGRES_USER=$(grep -E '^POSTGRES_USER=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r' | sed 's/^supabase_admin$/supabase_admin/')
POSTGRES_DB=$(grep -E '^POSTGRES_DB=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')

ok=0
fail=0
check() {
  local name="$1"
  local cmd="$2"
  if eval "$cmd" >/dev/null 2>&1; then
    echo "  [OK]   $name"
    ok=$((ok+1))
  else
    echo "  [FAIL] $name"
    fail=$((fail+1))
  fi
}

echo "=== Apachiy infra health ==="
check "Kong gateway reachable" "curl -fsS -o /dev/null -m 5 $API_URL/auth/v1/health"
check "PostgREST (public schema)" "curl -fsS -o /dev/null -m 5 '$API_URL/rest/v1/profiles?select=id&limit=0' -H 'apikey: $ANON_KEY' -H 'Authorization: Bearer $ANON_KEY'"
check "Auth (GoTrue) health" "curl -fsS -o /dev/null -m 5 $API_URL/auth/v1/health"
check "Storage health" "curl -fsS -o /dev/null -m 5 $API_URL/storage/v1/status"
check "Studio reachable" "curl -fsS -o /dev/null -m 5 $STUDIO_URL/"

if command -v psql >/dev/null 2>&1; then
  PGPASSWORD="$POSTGRES_PASSWORD" check "Postgres reachable" \
    "psql -h localhost -U ${POSTGRES_USER:-supabase_admin} -d ${POSTGRES_DB:-postgres} -c 'SELECT 1'"
else
  echo "  [SKIP] Postgres (psql not installed)"
fi

if [ -d "$ROOT_DIR/supabase/migrations" ]; then
  applied=$(docker exec apachiy-supabase-db psql -U "${POSTGRES_USER:-supabase_admin}" -d "${POSTGRES_DB:-postgres}" -tA \
    -c "SELECT count(*) FROM public._apachiy_migrations" 2>/dev/null || echo "0")
  total=$(ls "$ROOT_DIR/supabase/migrations"/*.sql 2>/dev/null | wc -l | tr -d ' ')
  echo
  echo "  migrations: $applied / $total applied"
fi

echo
echo "OK=$ok  FAIL=$fail"
[ "$fail" -eq 0 ]