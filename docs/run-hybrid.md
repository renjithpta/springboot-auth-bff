# Run hybrid — Redis in Compose, applications native

## Prerequisites

Install the native guide's Java/Maven/Node/Python/OpenSSL tools plus Docker Engine and Docker Compose v2. A native Redis installation is not required. Build the application first:

```bash
python3 scripts/init.py
scripts/build.sh
```

## Start

```bash
scripts/hybrid.sh start
# Or include the local external OIDC provider:
scripts/hybrid.sh start --simulator
```

The script starts only Redis using `infra/compose/infra.yml`, waits for Redis health, then launches Java services and the UI natively. Do not pass `--redis`: Compose owns Redis in this mode.

## Verify

```bash
docker compose --env-file .env -p aobp-infra -f infra/compose/infra.yml ps
curl --fail http://localhost:8080/bff/api/v1/session
python3 scripts/smoke.py
# If simulator enabled:
python3 scripts/smoke.py --federation
```

Open http://localhost:8080 and use the README demonstration. Native logs are in `.runtime/*.log`. Redis logs:

```bash
docker compose --env-file .env -p aobp-infra -f infra/compose/infra.yml logs -f redis
```

## Stop

```bash
scripts/hybrid.sh stop
```

The named Redis volume persists. You can replace any dependency with an externally managed service by changing its host/URL; applications do not need to know whether that dependency runs natively, in Docker, or in Kubernetes.
