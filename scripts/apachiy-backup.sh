#!/usr/bin/env bash
# =============================================================================
# apachiy-backup.sh — full Postgres dump of the Apachiy Supabase DB
# Usage:  apachiy-backup.sh [output-dir]
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$ROOT_DIR/infra/supabase/.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "missing $ENV_FILE"
  exit 1
fi

OUT_DIR="${1:-$ROOT_DIR/backups}"
mkdir -p "$OUT_DIR"

POSTGRES_PASSWORD=$(grep -E '^POSTGRES_PASSWORD=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')
POSTGRES_USER=$(grep -E '^POSTGRES_USER=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')
POSTGRES_DB=$(grep -E '^POSTGRES_DB=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')

TIMESTAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT_FILE="$OUT_DIR/apachiy-db-${TIMESTAMP}.dump"

echo "[apachiy-backup] dumping to $OUT_FILE"
docker exec apachiy-supabase-db \
  pg_dump -U "${POSTGRES_USER:-supabase_admin}" -d "${POSTGRES_DB:-postgres}" \
  --no-owner --clean --if-exists \
  -Fc -f /tmp/apachiy.dump
docker cp apachiy-supabase-db:/tmp/apachiy.dump "$OUT_FILE"
docker exec apachiy-supabase-db rm -f /tmp/apachiy.dump

# Also a plain SQL for grep-based verification
OUT_SQL="$OUT_DIR/apachiy-db-${TIMESTAMP}.sql"
docker exec apachiy-supabase-db \
  pg_dump -U "${POSTGRES_USER:-supabase_admin}" -d "${POSTGRES_DB:-postgres}" \
  --no-owner --clean --if-exists > "$OUT_SQL"

echo "[apachiy-backup] done."
echo "  binary: $OUT_FILE"
echo "  plain : $OUT_SQL"
echo "  size  : $(du -h "$OUT_FILE" "$OUT_SQL" | cut -f1 | tr '\n' ' ')"