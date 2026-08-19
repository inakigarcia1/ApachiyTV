#!/usr/bin/env bash
# =============================================================================
# apachiy-infra-status.sh — print container health + resource usage
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_DIR="$ROOT_DIR/infra/supabase"
ENV_FILE="$COMPOSE_DIR/.env"

cd "$COMPOSE_DIR"

echo "=== docker compose ps ==="
docker compose --env-file "$ENV_FILE" -f docker-compose.yml ps

echo
echo "=== docker stats (top-10 by CPU) ==="
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}" \
  $(docker compose --env-file "$ENV_FILE" -f docker-compose.yml ps -q) 2>/dev/null \
  | sort -k2 -hr | head -11