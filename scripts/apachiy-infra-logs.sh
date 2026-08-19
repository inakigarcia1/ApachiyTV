#!/usr/bin/env bash
# =============================================================================
# apachiy-infra-logs.sh — tail logs from the Apachiy Supabase stack
# Usage:  apachiy-infra-logs.sh [service-name] [lines]
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_DIR="$ROOT_DIR/infra/supabase"
ENV_FILE="$COMPOSE_DIR/.env"

cd "$COMPOSE_DIR"

SERVICE="${1:-}"
LINES="${2:-100}"

if [ -n "$SERVICE" ]; then
  docker compose --env-file "$ENV_FILE" -f docker-compose.yml logs -f --tail "$LINES" "$SERVICE"
else
  docker compose --env-file "$ENV_FILE" -f docker-compose.yml logs -f --tail "$LINES"
fi