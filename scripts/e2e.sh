#!/usr/bin/env bash
# Cypress E2E journeys (WP-18) on a clean, seeded stack: fresh esg_e2e database, backend jar (local profile = demo
# tenant seed), production frontend build served by `vite preview`, then `cypress run`. Everything stops afterwards.
#
#   ./scripts/e2e.sh                      # all journeys, headless
#   ./scripts/e2e.sh --spec cypress/e2e/journey-02-roles-and-menus.cy.js
#
# Ports (override if a dev stack is running): E2E_BACKEND_PORT (18080), E2E_FRONTEND_PORT (4173).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_PORT="${E2E_BACKEND_PORT:-18080}"
FRONTEND_PORT="${E2E_FRONTEND_PORT:-4173}"
export ESG_OWNER_PASSWORD="${ESG_OWNER_PASSWORD:-esg_owner_local}"
export ESG_APP_PASSWORD="${ESG_APP_PASSWORD:-esg_app_local}"
LOGS="$ROOT/esg-frontend/cypress/logs"
mkdir -p "$LOGS"

log() { printf '\033[1;35m[e2e]\033[0m %s\n' "$*"; }

PG_PORT=5432
if [[ "${ESG_DB_MODE:-auto}" == "docker" ]]; then PG_PORT="${ESG_PG_PORT:-5433}"; fi
"$ROOT/scripts/dev.sh" e2edb

log "Building backend"
(cd "$ROOT/esg-backend" && ./mvnw -q -B package -DskipTests -Dcheckstyle.skip -Djacoco.skip)
log "Building frontend"
(cd "$ROOT/esg-frontend" && npm run -s build)

PIDS=()
cleanup() { log "Stopping"; for p in "${PIDS[@]}"; do kill "$p" 2>/dev/null || true; done; }
trap cleanup EXIT INT TERM

log "Starting backend on :$BACKEND_PORT (logs: cypress/logs/backend.log)"
DB_URL="jdbc:postgresql://localhost:$PG_PORT/esg_e2e" DB_USERNAME=esg_app DB_PASSWORD="$ESG_APP_PASSWORD" \
  DB_OWNER_USERNAME=esg_owner DB_OWNER_PASSWORD="$ESG_OWNER_PASSWORD" \
  java -jar "$(ls "$ROOT"/esg-backend/target/esg-platform-*.jar | grep -v plain | head -1)" \
  --spring.profiles.active=local --server.port="$BACKEND_PORT" > "$LOGS/backend.log" 2>&1 &
PIDS+=($!)

log "Starting frontend preview on :$FRONTEND_PORT"
(cd "$ROOT/esg-frontend" && VITE_BACKEND_URL="http://localhost:$BACKEND_PORT" \
  npx vite preview --port "$FRONTEND_PORT" --strictPort > "$LOGS/frontend.log" 2>&1) &
PIDS+=($!)

for i in $(seq 1 90); do
  curl -sf "localhost:$BACKEND_PORT/api/actuator/health" >/dev/null && break
  [[ $i == 90 ]] && { tail -50 "$LOGS/backend.log"; exit 1; }
  sleep 2
done
for i in $(seq 1 30); do curl -sf "localhost:$FRONTEND_PORT/login" >/dev/null && break; sleep 1; done
log "Stack is up; running Cypress"

cd "$ROOT/esg-frontend"
E2E_BASE_URL="http://localhost:$FRONTEND_PORT" npx cypress run "$@"
