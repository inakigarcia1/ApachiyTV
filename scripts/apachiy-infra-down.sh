#!/usr/bin/env bash
# =============================================================================
# apachiy-infra-down.sh — stop the Apachiy Supabase stack (keeps volumes)
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_DIR="$ROOT_DIR/infra/supabase"
ENV_FILE="$COMPOSE_DIR/.env"

cd "$COMPOSE_DIR"
docker compose --env-file "$ENV_FILE" -f docker-compose.yml down