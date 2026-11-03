# Run with Docker CLI — without Compose

## Prerequisites

Docker Engine, Python 3, OpenSSL, and available ports 8080/9000. Start from a clean demo topology; native or Compose processes must be stopped.

## Start and verify

```bash
scripts/docker.sh start
docker ps --filter name=aobp-
curl --fail http://localhost:8080/bff/api/v1/session
python3 scripts/smoke.py
```

The script builds four Java images and a separate UI image, creates network `aobp`, and launches Redis plus the application containers. Wait for service startup before curl/smoke. Redis state uses volume `aobp-redis`; identity data uses `.runtime`.

Open http://localhost:8080 and follow the README demo. Inspect errors with:

```bash
docker logs -f aobp-auth-server
docker logs -f aobp-bff
```

## Stop

```bash
scripts/docker.sh stop
```

This removes only named application containers, retaining data and images. If a start is interrupted, run stop before starting again. For provider configuration, mount a provider YAML into the identity container and set SPRING_CONFIG_ADDITIONAL_LOCATION; see the Compose guide and provider runbook for the same configuration contract.
