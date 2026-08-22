#!/usr/bin/env bash
# =============================================================================
# bootstrap-apachiy.sh — end-to-end bootstrap of the Apachiy dev environment
# Steps:
#   1. Validate Docker / Java / git
#   2. Create infra/supabase/.env from .env.example (if missing) and generate
#      strong secrets
#   3. Create local.properties from local.example.properties (if missing)
#   4. Start Supabase stack
#   5. Apply Apachiy schema migrations
#   6. Run health checks
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

ok()   { echo "  [OK]   $*"; }
fail() { echo "  [FAIL] $*"; exit 1; }

echo "=== 1. Validating prerequisites ==="
command -v docker >/dev/null 2>&1     || fail "docker not installed"
docker version >/dev/null 2>&1        || fail "docker daemon not running"
command -v java >/dev/null 2>&1      || echo "  [WARN] java not in PATH (needed only for Gradle builds)"
if command -v java >/dev/null 2>&1; then
  ver=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)(\..*)?".*/\1/')
  if [ "${ver:-0}" -lt 17 ] 2>/dev/null; then
    fail "Java >= 17 required (found $ver)"
  fi
  ok "Java $ver"
fi
ok "Docker"
command -v git >/dev/null 2>&1 && ok "git" || fail "git not installed"
command -v openssl >/dev/null 2>&1 && ok "openssl" || fail "openssl not installed"

echo
echo "=== 2. Setting up infra/supabase/.env ==="
ENV_FILE="$ROOT_DIR/infra/supabase/.env"
ENV_EXAMPLE="$ROOT_DIR/infra/supabase/.env.example"
if [ -f "$ENV_FILE" ]; then
  ok "infra/supabase/.env already exists (keeping)"
else
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  # Generate secrets
  gen() { openssl rand -hex 32; }
  sed -i "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(gen)|" "$ENV_FILE"
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(gen)|" "$ENV_FILE"
  sed -i "s|^GOTRUE_ADMIN_PASSWORD=.*|GOTRUE_ADMIN_PASSWORD=$(gen)|" "$ENV_FILE"
  ok "infra/supabase/.env created with generated secrets"
  echo "  >>> review and tweak infra/supabase/.env before production use <<<"
fi

echo
echo "=== 3. Setting up local.properties ==="
LOCAL_PROP="$ROOT_DIR/local.properties"
LOCAL_EXAMPLE="$ROOT_DIR/local.example.properties"
if [ -f "$LOCAL_PROP" ]; then
  ok "local.properties already exists (keeping)"
else
  cp "$LOCAL_EXAMPLE" "$LOCAL_PROP"
  chmod 600 "$LOCAL_PROP"
  ok "local.properties created from example"
  echo "  >>> fill in APACHIY_SUPABASE_URL, APACHIY_SUPABASE_ANON_KEY, APACHIY_API_BASE_URL before building the APK <<<"
fi

echo
echo "=== 4. Starting Supabase stack ==="
"$SCRIPT_DIR/apachiy-infra-up.sh"

echo
echo "=== 5. Applying Apachiy schema migrations ==="
POSTGRES_PASSWORD=$(grep -E '^POSTGRES_PASSWORD=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')
MIG_DIR="$ROOT_DIR/supabase/migrations"
if compgen -G "$MIG_DIR/*.sql" >/dev/null; then
  for m in "$MIG_DIR"/*.sql; do
    echo "  applying $(basename "$m")"
    docker exec -i apachiy-supabase-db \
      psql -U supabase_admin -d postgres -v ON_ERROR_STOP=1 -f - < "$m" \
      && docker exec apachiy-supabase-db \
           psql -U supabase_admin -d postgres -c \
           "INSERT INTO public._apachiy_migrations(name) VALUES ('$(basename "$m")') ON CONFLICT DO NOTHING;" >/dev/null
  done
  docker exec apachiy-supabase-db \
    psql -U supabase_admin -d postgres -v ON_ERROR_STOP=1 \
    -c "INSERT INTO auth.schema_migrations (version) SELECT version FROM public.schema_migrations WHERE version NOT IN (SELECT version FROM auth.schema_migrations);" >/dev/null
  docker exec apachiy-supabase-db \
    psql -U supabase_admin -d postgres -v ON_ERROR_STOP=1 \
    -c "ALTER ROLE supabase_admin SET search_path TO auth, public, extensions;" >/dev/null
  if [ -f "$ROOT_DIR/../Apachiy/.env" ]; then
    JWT_SECRET=$(grep -E '^JWT_SECRET=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')
    if [ -n "$JWT_SECRET" ]; then
      APACHIY_ENV="$ROOT_DIR/../Apachiy/.env"
      if grep -q '^APACHIY_SUPABASE_JWT_SECRET=' "$APACHIY_ENV"; then
        sed -i "s|^APACHIY_SUPABASE_JWT_SECRET=.*|APACHIY_SUPABASE_JWT_SECRET=$JWT_SECRET|" "$APACHIY_ENV"
      else
        echo "APACHIY_SUPABASE_JWT_SECRET=$JWT_SECRET" >> "$APACHIY_ENV"
      fi
      ok "synced APACHIY_SUPABASE_JWT_SECRET into Apachiy/.env"
    fi
  fi
  ok "migrations applied"
else
  echo "  [SKIP] no migration files in $MIG_DIR"
fi

echo
echo "=== 6. Running health checks ==="
"$SCRIPT_DIR/apachiy-health.sh"

echo
echo "Bootstrap complete."
echo
echo "Next steps:"
echo "  1. Open the Android Studio project: file://$ROOT_DIR"
echo "  2. Build:  cd $ROOT_DIR && ./gradlew :app:assembleFullDebug"
echo "  3. Install:  adb install -r app/build/outputs/apk/full/debug/app-full-universal-debug.apk"