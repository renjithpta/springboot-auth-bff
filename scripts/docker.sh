#!/usr/bin/env bash
# Run the same topology with the Docker CLI only; no Compose dependency.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/init.py
if [[ "${1:-start}" == stop ]]; then
 for name in gateway ui bff flight-service auth-server redis; do docker rm -f "aobp-$name" 2>/dev/null || true; done
 exit 0
fi
docker network inspect aobp >/dev/null 2>&1 || docker network create aobp
for module in auth-server bff flight-service gateway; do docker build --build-arg "MODULE=$module" -f infra/docker/Dockerfile.java -t "aobp/$module:local" .; done
docker build -f infra/docker/Dockerfile.ui -t aobp/ui:local .
# Values are read by the container from the private env file, not embedded in image layers.
docker run -d --name aobp-redis --network aobp --network-alias redis --env-file .runtime/redis.env -v aobp-redis:/data redis:7.4-alpine sh -c 'exec redis-server --appendonly yes --requirepass "$REDIS_PASSWORD"'
docker run -d --name aobp-auth-server --network aobp --network-alias auth-server --user "$(id -u):$(id -g)" --env-file .runtime/auth.env -e 'AUTH_DB_URL=jdbc:h2:file:/app/data/auth-db;DB_CLOSE_ON_EXIT=FALSE' -e SIGNING_KEY_PATH=/app/data/signing.pem -v "$PWD/.runtime:/app/data" -p 127.0.0.1:9000:9000 aobp/auth-server:local
docker run -d --name aobp-flight-service --network aobp -e AUTH_INTERNAL_URL=http://auth-server:9000 aobp/flight-service:local
docker run -d --name aobp-bff --network aobp --network-alias bff --env-file .runtime/bff.env -e REDIS_HOST=redis -e AUTH_INTERNAL_URL=http://auth-server:9000 -e FLIGHT_URL=http://aobp-flight-service:8082 aobp/bff:local
docker run -d --name aobp-ui --network aobp --network-alias ui aobp/ui:local
docker run -d --name aobp-gateway --network aobp -e BFF_URL=http://bff:8081 -e UI_URL=http://ui:8080 -p 127.0.0.1:8080:8080 aobp/gateway:local
printf 'Open http://localhost:8080 once service startup completes.\n'
