#!/usr/bin/env bash
# One-command local start for Atheris ESG (WP-10).
#
#   ./scripts/dev.sh            # start database, backend (local profile) and frontend
#   ./scripts/dev.sh db         # only prepare the database
#   ./scripts/dev.sh reset-db   # drop and recreate esg_dev (needed once after the WP-11 re-baseline)
#   ./scripts/dev.sh testdb     # create esg_test for integration tests without Docker (see README)
#   ./scripts/dev.sh e2edb      # create a fresh esg_e2e for the Cypress journeys (used by scripts/e2e.sh)
#   ./scripts/dev.sh perfdb     # create a fresh esg_perf for the performance runs (used by scripts/perf.sh)
#   ./scripts/dev.sh reset-dev  # wipe esg_dev, migrate and reseed the demo tenant, then exit (make reset-dev)
#   ./scripts/dev.sh clean-test-data  # remove records marked as test data from the demo tenant (make clean-test-data)
#
# Roles: esg_owner owns the schema and runs Flyway; esg_app is the application login. esg_app is not
# the owner, not a superuser and has no BYPASSRLS, so Row-Level Security applies to it (WP-11).
#
# Database mode (ESG_DB_MODE):
#   docker  - docker compose stack (postgres:17 on 5433, redis, minio, mailpit)
#   local   - an already-running PostgreSQL on localhost:5432 (e.g. Homebrew); needs a superuser via psql
#   auto    - docker if available, otherwise local (default)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Local settings that must not be committed (SMTP credentials, …): .env.local at the repo root, git-ignored.
if [ -f "$ROOT/.env.local" ]; then set -a; . "$ROOT/.env.local"; set +a; fi
MODE="${ESG_DB_MODE:-auto}"
DB_NAME="esg_dev"
DB_OWNER="esg_owner"
DB_APP="esg_app"
export ESG_OWNER_PASSWORD="${ESG_OWNER_PASSWORD:-esg_owner_local}"
export ESG_APP_PASSWORD="${ESG_APP_PASSWORD:-esg_app_local}"
CMD="${1:-}"

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

# Run SQL as a superuser against the local server (local mode) or the compose container (docker mode).
su_sql() {
  local db="$1"; shift
  if [[ "$MODE" == "docker" ]]; then
    docker compose -f "$ROOT/docker-compose.yml" exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d "$db" -qtAc "$*"
  else
    "$PSQL" -w -h localhost -U "$SU" -d "$db" -v ON_ERROR_STOP=1 -qtAc "$*"
  fi
}

ensure_roles() {
  su_sql postgres "select 1 from pg_roles where rolname='$DB_OWNER'" | grep -q 1 \
    || { log "Creating role $DB_OWNER"; su_sql postgres "CREATE ROLE $DB_OWNER LOGIN PASSWORD '$ESG_OWNER_PASSWORD'"; }
  su_sql postgres "select 1 from pg_roles where rolname='$DB_APP'" | grep -q 1 \
    || { log "Creating role $DB_APP (no superuser, no BYPASSRLS)"; su_sql postgres "CREATE ROLE $DB_APP LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '$ESG_APP_PASSWORD'"; }
}

ensure_db() {
  local db="$1"
  su_sql postgres "select 1 from pg_database where datname='$db'" | grep -q 1 \
    || { log "Creating database $db"; su_sql postgres "CREATE DATABASE $db OWNER $DB_OWNER"; }
  su_sql postgres "GRANT CONNECT ON DATABASE $db TO $DB_APP"
}

prepare_db() {
  case "$MODE" in
    docker)
      log "Starting docker compose stack (postgres on 5433, redis, minio, mailpit)"
      docker compose -f "$ROOT/docker-compose.yml" up -d --wait
      PG_PORT="${ESG_PG_PORT:-5433}"
      ;;
    local)
      PSQL="$(find_psql)" || die "psql not found. Install PostgreSQL 17 or Docker."
      SU="${ESG_PG_SUPERUSER:-$(whoami)}"
      log "Using local PostgreSQL on localhost:5432 as superuser '$SU' ($PSQL)"
      "$PSQL" -w -h localhost -U "$SU" -d postgres -qtAc "select 1" >/dev/null \
        || die "Can't connect to localhost:5432 as '$SU'. Set ESG_PG_SUPERUSER or start PostgreSQL."
      PG_PORT=5432
      ;;
    *) die "Unknown ESG_DB_MODE '$MODE' (use docker, local or auto)";;
  esac
  ensure_roles
  if [[ "$CMD" == "reset-db" || "$CMD" == "reset-dev" ]]; then
    log "Dropping database $DB_NAME"
    su_sql postgres "DROP DATABASE IF EXISTS $DB_NAME WITH (FORCE)"
  fi
  ensure_db "$DB_NAME"
  export DB_URL="jdbc:postgresql://localhost:$PG_PORT/$DB_NAME"
  export DB_USERNAME="$DB_APP" DB_PASSWORD="$ESG_APP_PASSWORD"
  export DB_OWNER_USERNAME="$DB_OWNER" DB_OWNER_PASSWORD="$ESG_OWNER_PASSWORD"
  log "Database ready: $DB_URL (app role $DB_APP, migrations as $DB_OWNER)"
}

prepare_db
# Demo tenant of the local seed (db/seed/local/R__local_demo_tenant.sql).
DEMO_TENANT="0a000000-0000-4000-8000-000000000001"

case "$CMD" in
  db|reset-db) exit 0 ;;
  reset-dev)
    log "Building the backend and migrating + seeding $DB_NAME (local profile)"
    (cd "$ROOT/esg-backend" && ./mvnw -q -B package -DskipTests -Dcheckstyle.skip -Djacoco.skip)
    java -jar "$(ls "$ROOT"/esg-backend/target/esg-platform-*.jar | grep -v plain | head -1)" \
      --spring.profiles.active=local --server.port=0 --esg.migrate-only=true
    log "esg_dev is fresh: demo tenant reseeded (sign-in details: esg-backend/src/main/resources/db/seed/README.md)"
    exit 0 ;;
  clean-test-data)
    # Runs as the schema owner inside the demo tenant (FORCE RLS still applies). Soft deletes, audited.
    if [[ "$MODE" == "docker" ]]; then
      docker compose -f "$ROOT/docker-compose.yml" exec -T -e PGPASSWORD="$ESG_OWNER_PASSWORD" postgres \
        psql -v ON_ERROR_STOP=1 -U "$DB_OWNER" -d "$DB_NAME" -c "select set_config('app.current_tenant', '$DEMO_TENANT', false)" \
        -c "select * from auth.purge_test_data()"
    else
      PGPASSWORD="$ESG_OWNER_PASSWORD" "$PSQL" -w -h localhost -U "$DB_OWNER" -d "$DB_NAME" -v ON_ERROR_STOP=1 \
        -c "select set_config('app.current_tenant', '$DEMO_TENANT', false)" -c "select * from auth.purge_test_data()"
    fi
    exit 0 ;;
  e2edb)
    su_sql postgres "DROP DATABASE IF EXISTS esg_e2e WITH (FORCE)"
    ensure_db esg_e2e
    log "E2E database ready: jdbc:postgresql://localhost:$PG_PORT/esg_e2e"
    exit 0 ;;
  perfdb)
    su_sql postgres "DROP DATABASE IF EXISTS esg_perf WITH (FORCE)"
    ensure_db esg_perf
    log "Performance database ready: jdbc:postgresql://localhost:$PG_PORT/esg_perf"
    exit 0 ;;
  testdb)
    su_sql postgres "DROP DATABASE IF EXISTS esg_test WITH (FORCE)"
    ensure_db esg_test
    log "Test database ready. Run integration tests with:"
    echo "  ESG_TEST_DB_URL=jdbc:postgresql://localhost:$PG_PORT/esg_test ./mvnw verify   (from esg-backend/)"
    exit 0 ;;
esac

[[ -d "$ROOT/esg-frontend/node_modules" ]] || { log "Installing frontend dependencies"; (cd "$ROOT/esg-frontend" && npm ci); }

PIDS=()
cleanup() { log "Stopping"; for p in "${PIDS[@]}"; do kill "$p" 2>/dev/null || true; done; }
trap cleanup EXIT INT TERM

# WP-50: the AI service, when its virtualenv exists (the backend's local profile points at :8090 with a dev-only token).
if [[ -x "$ROOT/esg-ai/.venv/bin/uvicorn" ]]; then
  log "Starting AI service on http://localhost:8090 (Ollama: ${OLLAMA_URL:-http://localhost:11434}, model ${OLLAMA_MODEL:-granite4:micro})"
  (cd "$ROOT/esg-ai" && ESG_AI_TOKEN="${ESG_AI_TOKEN:-local-dev-ai-token}" .venv/bin/uvicorn app.main:app --port 8090) &
  PIDS+=($!)
else
  log "AI service not started: create esg-ai/.venv (see esg-ai/README.md); AI features will say the service isn't running"
fi

log "Starting backend (profile: local) on http://localhost:8080/api"
(cd "$ROOT/esg-backend" && ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=local) &
PIDS+=($!)

log "Starting frontend on http://localhost:5173"
(cd "$ROOT/esg-frontend" && npm run dev -- --port 5173 --strictPort) &
PIDS+=($!)

log "Ctrl+C stops both. Local login: see README 'Run locally in 5 minutes'."
wait
