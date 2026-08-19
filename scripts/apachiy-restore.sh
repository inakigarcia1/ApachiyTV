#!/usr/bin/env bash
# =============================================================================
# apachiy-restore.sh — restore a Postgres dump into the Apachiy Supabase DB
# Usage:  apachiy-restore.sh <dump-file>
# =============================================================================
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <dump-file>" >&2
  echo "  dump-file can be a .dump (binary) or .sql (plain) file produced by apachiy-backup.sh" >&2
  exit 1
fi

DUMP="$1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$ROOT_DIR/infra/supabase/.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "missing $ENV_FILE"
  exit 1
fi

if [ ! -f "$DUMP" ]; then
  echo "dump not found: $DUMP"
  exit 1
fi

POSTGRES_PASSWORD=$(grep -E '^POSTGRES_PASSWORD=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')
POSTGRES_USER=$(grep -E '^POSTGRES_USER=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')
POSTGRES_DB=$(grep -E '^POSTGRES_DB=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')

case "$DUMP" in
  *.dump)
    echo "[apachiy-restore] copying $DUMP into container..."
    docker cp "$DUMP" apachiy-supabase-db:/tmp/apachiy-restore.dump
    echo "[apachiy-restore] restoring (binary)..."
    docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" apachiy-supabase-db \
      pg_restore -U "${POSTGRES_USER:-supabase_admin}" -d "${POSTGRES_DB:-postgres}" \
      --no-owner --clean --if-exists -Fc /tmp/apachiy-restore.dump
    docker exec apachiy-supabase-db rm -f /tmp/apachiy-restore.dump
    ;;
  *.sql)
    echo "[apachiy-restore] applying plain SQL $DUMP..."
    docker exec -i apachiy-supabase-db \
      psql -U "${POSTGRES_USER:-supabase_admin}" -d "${POSTGRES_DB:-postgres}" \
      -v ON_ERROR_STOP=1 < "$DUMP"
    ;;
  *)
    echo "unknown dump format: $DUMP (expected .dump or .sql)" >&2
    exit 1
    ;;
esac

echo "[apachiy-restore] done."