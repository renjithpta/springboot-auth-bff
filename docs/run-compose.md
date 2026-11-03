# Run with Docker Compose

## Prerequisites

Linux/WSL2, Docker Engine with Compose v2, Python 3 and OpenSSL. Docker builds provide Java/Maven/Node, so local installations of those are unnecessary. Reserve ports 8080, 9000 and 6379. Do not share the runtime H2 database with a native process.

## Initialize and start

```bash
python3 scripts/init.py
scripts/compose.sh up --build -d
scripts/compose.sh ps
```

The script maps the authorization container's UID/GID to the owner of the private signing key. Development state is persisted in `.runtime/`; Redis uses a named volume. On hosts with SELinux, authorize the bind mount using your platform's approved labeling policy.

## Verify

```bash
curl --fail http://localhost:8080/bff/api/v1/session
curl --fail http://127.0.0.1:9000/.well-known/openid-configuration
python3 scripts/smoke.py
```

Wait for Java startup before testing. Open http://localhost:8080, then follow the README operator/viewer demo. Read the generated DEMO_PASSWORD locally from `.env`.

## Configure real providers

Copy `infra/config/providers-example.yml` to `.runtime/providers.yml`, configure approved provider metadata and secret environment variables, and add these variable names to `.env`. Also add ENTRA_TENANT_ID, ENTRA_CLIENT_ID and ENTRA_CLIENT_SECRET entries to the auth-server environment mapping in `infra/compose/compose.yml` using `${VARIABLE}` interpolation. Only explicitly selected secrets are passed to each container. Recreate the identity service:

```bash
scripts/compose.sh up -d --force-recreate auth-server
```

The full Compose file uses local login by default. Use native/hybrid simulator mode for a credential-free federation demonstration, or add the simulator service following the native simulator environment contract. Do not point a container's backchannel token URI at its own localhost.

## Logs and shutdown

```bash
scripts/compose.sh logs -f auth-server bff flight-service gateway
scripts/compose.sh down
```

`down` preserves the named Redis volume and `.runtime` data. Avoid `down -v` unless you intentionally want to remove demo session storage. These HTTP and local-bound ports are development defaults, not a public production deployment.
