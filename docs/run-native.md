# Run natively — no Docker or Kubernetes

## Prerequisites

Use Linux (or Linux through WSL2), Java **17**, Maven **3.9.x**, Node **22**, npm, Python **3.10+**, OpenSSL, and Redis **7.4**. Install these through your approved OS/package tooling. Verify:

```bash
java -version
mvn -version
node --version
python3 --version
openssl version
redis-server --version
```

Ports 8080, 8081, 8082, 5173, 6379, and 9000 must be free; 9001 is used by the optional external-provider simulator. Commands below run from the repository root. Do not switch between `localhost` and `127.0.0.1` for the UI; open exactly the documented URL.

## Step 1 — initialize private configuration

```bash
python3 scripts/init.py
```

This generates `.env`, RSA signing keys, and Redis configuration under `.runtime/`. Files are ignored by Git. Inspect `.env` locally to obtain DEMO_PASSWORD. Existing configuration and keys are not overwritten.

## Step 2 — build

```bash
scripts/build.sh
```

Expect Maven BUILD SUCCESS and a Vite production bundle. The build runs meaningful security tests. Maven enforces Java 17 and Maven 3.9.x.

## Step 3 — start all native processes

```bash
scripts/native.sh start --redis
```

`--redis` starts a Redis process using the generated configuration. If you operate an existing Redis instance, omit the flag and configure REDIS_HOST/PORT/PASSWORD in your environment or `.env`. Explicit process environment values override `.env`.

Expected output: `Ready: http://localhost:8080`.

To include the external-provider simulator instead:

```bash
scripts/native.sh stop
scripts/native.sh start --redis --simulator
```

## Step 4 — verify

```bash
curl --fail http://localhost:8080/bff/api/v1/session
curl --fail http://127.0.0.1:9000/.well-known/openid-configuration
python3 scripts/smoke.py
# With --simulator enabled:
python3 scripts/smoke.py --federation
```

The session initially reports `authenticated:false`. Discovery reports issuer `http://127.0.0.1:9000`. Follow the README browser demo for operator/viewer/T2 denial. Smoke tests create their own temporary browser sessions and clean them up.

To test refresh quickly, restart with a short token lifetime:

```bash
scripts/native.sh stop
ACCESS_TOKEN_TTL=PT10S scripts/native.sh start --redis --simulator
python3 scripts/smoke.py --federation --refresh
```

This value is for tests only. Spring refreshes near-expiry access tokens proactively.

## Step 5 — logs and stop

```bash
tail -f .runtime/auth-server.log .runtime/bff.log .runtime/flight-service.log
scripts/native.sh status
scripts/native.sh stop
```

Only processes started by this runner are stopped. An externally managed Redis is left running. Runtime data persists. Do not delete signing keys or database files to solve a transient startup issue.

## Troubleshooting

- Identity startup fails: check SIGNING_KEY_PATH, DB password and writable runtime directory.
- BFF health fails: check Redis credentials and port; do not disable Redis to bypass authentication.
- `invalid_redirect_uri`: compare the BFF callback exactly with its registered client redirect.
- Callback/issuer failure: use `localhost:8080` for UI and `127.0.0.1:9000` for identity.
- Process record exists: run `stop`, inspect logs, then restart. Do not start two native runners simultaneously.
