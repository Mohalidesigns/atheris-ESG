#!/usr/bin/env bash
# WP-51 performance runs (TRD §11.2.4) on a throwaway database: fresh esg_perf, backend jar (local profile = demo tenant),
# volume seed (perf/seed-volume.sql: 1M data points, 10k emission records), then k6. Results: perf/results/<run>/.
#
#   ./scripts/perf.sh                 # everything with defaults
#   VUS=500 HOLD=120s ./scripts/perf.sh submit
#   POINTS=200000 ./scripts/perf.sh dashboard report
#
# Needs k6 (brew install k6). Port: PERF_BACKEND_PORT (18180). Never point this at a shared database.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${PERF_BACKEND_PORT:-18180}"
export ESG_OWNER_PASSWORD="${ESG_OWNER_PASSWORD:-esg_owner_local}"
export ESG_APP_PASSWORD="${ESG_APP_PASSWORD:-esg_app_local}"
RUN="$(date +%Y%m%d-%H%M%S)"
OUT="$ROOT/perf/results/$RUN"
mkdir -p "$OUT"
SCRIPTS=("${@:-submit dashboard report calculate}")
read -r -a SCRIPTS <<< "${SCRIPTS[*]}"
log() { printf '\033[1;36m[perf]\033[0m %s\n' "$*"; }
command -v k6 >/dev/null || { echo "k6 not found (brew install k6)"; exit 1; }
PSQL="${PSQL:-$(command -v psql || echo /Library/PostgreSQL/18/bin/psql)}"

"$ROOT/scripts/dev.sh" perfdb
log "Building backend"
(cd "$ROOT/esg-backend" && ./mvnw -q -B package -DskipTests -Dcheckstyle.skip -Djacoco.skip)

PID=""
cleanup() { [[ -n "$PID" ]] && kill "$PID" 2>/dev/null || true; }
trap cleanup EXIT INT TERM
log "Starting backend on :$PORT (logs: $OUT/backend.log)"
DB_URL="jdbc:postgresql://localhost:5432/esg_perf" DB_USERNAME=esg_app DB_PASSWORD="$ESG_APP_PASSWORD" \
  DB_OWNER_USERNAME=esg_owner DB_OWNER_PASSWORD="$ESG_OWNER_PASSWORD" ESG_AI_URL="" \
  java ${PERF_JAVA_OPTS:--Xmx2g} -jar "$(ls "$ROOT"/esg-backend/target/esg-platform-*.jar | grep -v plain | head -1)" \
  --spring.profiles.active=local --server.port="$PORT" > "$OUT/backend.log" 2>&1 &
PID=$!
for i in $(seq 1 90); do curl -sf "localhost:$PORT/api/actuator/health" >/dev/null && break; [[ $i == 90 ]] && { tail -50 "$OUT/backend.log"; exit 1; }; sleep 2; done

log "Seeding volume (POINTS=${POINTS:-1000000}, EMISSIONS=${EMISSIONS:-10000})"
PGPASSWORD="$ESG_OWNER_PASSWORD" "$PSQL" -h localhost -U esg_owner -d esg_perf -v points="${POINTS:-1000000}" -v emissions="${EMISSIONS:-10000}" \
  -f "$ROOT/perf/seed-volume.sql" > "$OUT/seed.log" 2>&1
grep -E "Time:|data_points|emission_records|^ *[0-9]+$" "$OUT/seed.log" | tail -6

# Settle: a production database doesn't take a million-row bulk load just before peak traffic, so vacuum, analyse and
# checkpoint first; otherwise the first run measures the seed's WAL flush, not the application.
log "Settling the database (VACUUM ANALYZE, CHECKPOINT)"
for su in "${ESG_PG_SUPERUSER:-$(whoami)}" postgres; do
  "$PSQL" -w -h localhost -U "$su" -d esg_perf -qc "VACUUM ANALYZE" -c "CHECKPOINT" 2>/dev/null && break
done

export BASE_URL="http://localhost:$PORT" PERF_PASSWORD="${PERF_PASSWORD:-LocalDev!2026}"
run_k6() {  # name, script, extra env…
  local name="$1" script="$2"; shift 2
  log "k6: $name"
  env "$@" k6 run --quiet --summary-export "$OUT/$name.json" "$ROOT/perf/k6/$script.js" > "$OUT/$name.txt" 2>&1 || log "$name: thresholds not met (see $OUT/$name.txt)"
  # The summary carries setup() data, i.e. the bearer token: never keep it (WP-52, found by gitleaks).
  python3 -c "import json,sys; p=sys.argv[1]; d=json.load(open(p)); d.pop('setup_data', None); json.dump(d, open(p, 'w'), indent=2)" "$OUT/$name.json"
  grep -E "✓ '|✗ '|http_req_duration\.\.|report_total_ms\.\.|iterations\.\.|vus_max|dropped_iterations" "$OUT/$name.txt" || true
}
for s in "${SCRIPTS[@]}"; do
  if [[ "$s" == "submit" ]]; then
    # Open model at each rate (submissions/s, each = create + submit), then a closed saturation run.
    offset=0
    for rate in ${RATES:-100 200 300}; do run_k6 "submit-rate-$rate" submit RATE="$rate" HOLD="${HOLD:-60s}" OFFSET="$offset"; offset=$((offset + 100000)); done
    run_k6 "submit-saturation" submit VUS="${VUS:-200}" HOLD="${HOLD:-60s}" OFFSET="$offset"
  else
    run_k6 "$s" "$s"
  fi
done
{
  echo "Machine: $(sysctl -n machdep.cpu.brand_string 2>/dev/null || uname -m), $(($(sysctl -n hw.memsize 2>/dev/null || echo 0) / 1073741824)) GB RAM; one backend instance; PostgreSQL on the same machine."
  echo "Run: $RUN; scripts: ${SCRIPTS[*]}; volume: ${POINTS:-1000000} data points, ${EMISSIONS:-10000} emission records."
} > "$OUT/MACHINE.txt"
log "Results in perf/results/$RUN"
