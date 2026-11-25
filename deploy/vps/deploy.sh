#!/usr/bin/env bash
# Build and roll out Atheris ESG on the shared GRC VPS (deploy/vps/README.md). Run from /var/www/esg as root:
#   ./deploy/vps/deploy.sh            # deploy what is checked out
#   ./deploy/vps/deploy.sh v1.0.0     # check out a tag or branch first
set -euo pipefail
cd "$(dirname "$0")/../.."
ROOT="$(pwd)"
COMPOSE=(docker compose -f deploy/vps/docker-compose.vps.yml --env-file deploy/vps/.env)

[ -f deploy/vps/.env ] || { echo "deploy/vps/.env is missing (copy deploy/vps/.env.example)"; exit 1; }
if grep -q CHANGE_ME deploy/vps/.env; then echo "deploy/vps/.env still has CHANGE_ME values"; exit 1; fi

if [ "${1:-}" != "" ]; then
  git fetch --tags origin
  git checkout "$1"
  git pull --ff-only origin "$1" 2>/dev/null || true   # no-op for tags
fi
echo "Deploying $(git describe --tags --always --dirty)"

# 1. Web app: build in Docker (no Node on the host), then swap /var/www/esg/web atomically.
docker build -q -f deploy/frontend.Dockerfile -t atheris-esg-frontend:build .
cid=$(docker create atheris-esg-frontend:build)
rm -rf "$ROOT/web.new"
docker cp "$cid:/usr/share/nginx/html" "$ROOT/web.new"
docker rm "$cid" >/dev/null
chmod -R a+rX "$ROOT/web.new"

# 2. Backend, AI, ClamAV, MinIO. Flyway migrates on backend start.
"${COMPOSE[@]}" up -d --build --remove-orphans

echo -n "Waiting for the backend"
for _ in $(seq 1 90); do
  if curl -fsS http://127.0.0.1:8085/api/actuator/health >/dev/null 2>&1; then echo " up"; break; fi
  echo -n "."; sleep 2
done
curl -fsS http://127.0.0.1:8085/api/actuator/health >/dev/null || {
  echo; echo "Backend did not become healthy. Web app NOT switched. Logs:"; "${COMPOSE[@]}" logs --tail 80 backend; exit 1; }

# 3. Switch the web app only once the API that matches it is up.
rm -rf "$ROOT/web.old"
[ -d "$ROOT/web" ] && mv "$ROOT/web" "$ROOT/web.old"
mv "$ROOT/web.new" "$ROOT/web"
nginx -t && systemctl reload nginx
echo "Done: $(grep ^PUBLIC_URL deploy/vps/.env | cut -d= -f2)"
