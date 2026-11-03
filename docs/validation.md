# Validation report

Validated on **3 November 2026**. Application code is based on Java 17, Spring Boot 3.4.13 and Maven 3.9.11.

## Reproducible evidence

The [complete five-mode GitHub Actions run](https://github.com/renjithpta/springboot-auth-bff/actions/runs/41663860810) passed against application commit `94df8c5af700da5fddbc86abf8f32861708c64e5`.

| Execution mode | Actual verification | Result |
|---|---|---|
| Native | Clean Maven build, React bundle, native Redis, local + simulated OIDC login, refresh, token relay, authorization, logout | Passed |
| Hybrid | Redis in Compose; identity, BFF, gateway, API and UI native; same authentication checks | Passed |
| Docker CLI | Image builds and separate containers without Compose; browser/BFF business-flow smoke checks | Passed |
| Docker Compose | Image builds, full topology, local login and BFF/API authorization smoke checks | Passed |
| Kubernetes | Real kind cluster; image loading, plain YAML rollout, two BFF replicas, port forwarding and browser/BFF authorization checks | Passed |

Kubernetes validation is a functional deployment test. It does not certify NetworkPolicy enforcement: use a CNI that enforces NetworkPolicy in your target cluster. Identity-server high availability, cluster failure recovery and performance are outside this sample's test scope.

## Security checks

- **14 Java tests**, zero failures/errors: policy allow/deny cases, HTTP authentication boundary, authenticated encryption/tamper rejection, removed-session-attribute handling, and storage identity across session-ID rotation and separate browsers.
- **41 native end-to-end checks** with short token lifetime and the local external OIDC simulator. These cover local and federated login, no OAuth tokens in session JSON, token relay, refresh after access-token expiry, missing-CSRF rejection, RBAC denial, terminal ABAC denial, logout and independent browser sessions for the same user.
- **Seven real signed-token boundary checks**: anonymous rejection, valid token, wrong audience, wrong issuer, expired token, missing role, and missing read scope. These exercise the actual resource server rather than mocking its JWT filter.
- Python compilation, shell syntax and YAML/deployment structure checks passed.
- `npm audit --omit=dev` reported **zero known production dependency vulnerabilities** at validation time. This is a point-in-time package audit, not a full security assessment.

The native and hybrid CI jobs run simulator/refresh tests. Container and Kubernetes jobs run the shared browser-flow smoke suite. They do not claim real Entra tenant acceptance testing.

## Run the checks yourself

```bash
scripts/build.sh
ACCESS_TOKEN_TTL=PT10S scripts/native.sh start --redis --simulator
python3 scripts/smoke.py --federation --refresh
python3 scripts/token-boundary.py
scripts/native.sh stop
```

The signed-token script needs the local demo signing key and a native API port. It is intentionally separate from browser smoke tests because APIs remain private in Docker and Kubernetes topologies. Use the appropriate run guide for the other execution modes.

Install PyYAML in an isolated environment if you want to run the optional structural validator:

```bash
python3 -m venv .runtime/checks-venv
.runtime/checks-venv/bin/pip install PyYAML==6.0.2
.runtime/checks-venv/bin/python scripts/validate-config.py
```

Sanitized native test output is retained in [validation-results.txt](validation-results.txt). Runtime logs, credentials, keys and database files are private development artifacts and are not committed.

## Limits of the result

Real Microsoft Entra/Okta/other tenant acceptance requires approved credentials, callback registration and account onboarding. PostgreSQL is packaged/configurable but was not executed in this verification. The delivered identity database used file-backed H2. SAML, MFA enrollment, SCIM, global revocation, automated signing-key rotation and production identity administration are not implemented.

Spring Boot 3.4.13 is the requested version and is out of open-source support. See [security.md](security.md) for production prerequisites. These results are not a security certification or production-readiness approval.
