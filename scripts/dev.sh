#!/usr/bin/env bash
# One-command local start for Atheris ESG (WP-10).
#
#   ./scripts/dev.sh            # start database, backend (local profile) and frontend
#   ./scripts/dev.sh db         # only prepare the database
#
# Database mode (ESG_DB_MODE):
#   docker  - docker compose stack (postgres:17 on 5433, redis, minio, mailpit)
#   local   - an already-running PostgreSQL on localhost:5432 (e.g. Homebrew); needs a superuser via psql
#   auto    - docker if available, otherwise local (default)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${ESG_DB_MODE:-auto}"
DB_NAME="esg_dev"
DB_OWNER="esg_owner"
export ESG_OWNER_PASSWORD="${ESG_OWNER_PASSWORD:-esg_owner_local}"

log() { printf '\033[1;34m[dev]\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[dev]\033[0m %s\n' "$*" >&2; exit 1; }

if [[ "$MODE" == "auto" ]]; then
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then MODE=docker; else MODE=local; fi
fi

find_psql() {
  if command -v psql >/dev/null 2>&1; then command -v psql; return; fi
  for p in /opt/homebrew/bin/psql /usr/local/bin/psql /Library/PostgreSQL/*/bin/psql /Applications/Postgres.app/Contents/Versions/latest/bin/psql; do
    [[ -x "$p" ]] && { echo "$p"; return; }
  done
  return 1
}

prepare_db() {
  case "$MODE" in
    docker)
      log "Starting docker compose stack (postgres on 5433, redis, minio, mailpit)"
      docker compose -f "$ROOT/docker-compose.yml" up -d --wait
      export DB_URL="jdbc:postgresql://localhost:${ESG_PG_PORT:-5433}/$DB_NAME"
      ;;
    local)
      local PSQL; PSQL="$(find_psql)" || die "psql not found. Install PostgreSQL 17 or Docker."
      local SU="${ESG_PG_SUPERUSER:-$(whoami)}"
      log "Using local PostgreSQL on localhost:5432 as superuser '$SU' ($PSQL)"
      "$PSQL" -w -h localhost -U "$SU" -d postgres -qtAc "select 1" >/dev/null \
        || die "Can't connect to localhost:5432 as '$SU'. Set ESG_PG_SUPERUSER or start PostgreSQL."
      if ! "$PSQL" -w -h localhost -U "$SU" -d postgres -qtAc "select 1 from pg_roles where rolname='$DB_OWNER'" | grep -q 1; then
        log "Creating role $DB_OWNER"
        "$PSQL" -w -h localhost -U "$SU" -d postgres -qc "CREATE ROLE $DB_OWNER LOGIN PASSWORD '$ESG_OWNER_PASSWORD'"
      fi
      if ! "$PSQL" -w -h localhost -U "$SU" -d postgres -qtAc "select 1 from pg_database where datname='$DB_NAME'" | grep -q 1; then
        log "Creating database $DB_NAME"
        "$PSQL" -w -h localhost -U "$SU" -d postgres -qc "CREATE DATABASE $DB_NAME OWNER $DB_OWNER"
      fi
      export DB_URL="jdbc:postgresql://localhost:5432/$DB_NAME"
      ;;
    *) die "Unknown ESG_DB_MODE '$MODE' (use docker, local or auto)";;
  esac
  export DB_USERNAME="$DB_OWNER" DB_PASSWORD="$ESG_OWNER_PASSWORD"
  log "Database ready: $DB_URL"
}

prepare_db
[[ "${1:-}" == "db" ]] && exit 0

[[ -d "$ROOT/esg-frontend/node_modules" ]] || { log "Installing frontend dependencies"; (cd "$ROOT/esg-frontend" && npm ci); }

PIDS=()
cleanup() { log "Stopping"; for p in "${PIDS[@]}"; do kill "$p" 2>/dev/null || true; done; }
trap cleanup EXIT INT TERM

log "Starting backend (profile: local) on http://localhost:8080/api"
(cd "$ROOT/esg-backend" && ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=local) &
PIDS+=($!)

log "Starting frontend on http://localhost:5173"
(cd "$ROOT/esg-frontend" && npm run dev -- --port 5173 --strictPort) &
PIDS+=($!)

log "Ctrl+C stops both. Local login: see README 'Run locally in 5 minutes'."
wait
