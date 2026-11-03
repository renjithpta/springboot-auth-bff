# Validation report

Validated on 3 November 2026 with Java 17 and Maven 3.9.11.

| Check | Result |
|---|---|
| Clean Maven reactor verification | Passed; 14 tests, zero failures/errors |
| React production bundle | Passed |
| Real native stack with Redis | Passed |
| Browser-style local and external-provider login | Passed with local OIDC simulator |
| Token relay, refresh, CSRF, RBAC, ABAC, logout | Passed in native end-to-end run |
| Real signed JWT validation | Passed: valid, wrong audience, wrong issuer, expired, missing role, missing scope |
| Python/shell syntax and YAML structure | Passed |
| Docker image builds | Passed in initial GitHub Actions run |
| Compose full-stack smoke | Fix committed for the test's private-port assumption; final CI result pending |
| Plain Kubernetes deployment | CI job provided; final CI result pending |
| Real Entra/Okta tenant | Not executed; requires approved client credentials and account onboarding |
| PostgreSQL path | Packaged/configurable, not executed |

Native smoke originally completed 34 checks; its direct private-API check was subsequently moved into token-boundary.py so the same browser smoke works across native, Compose and Kubernetes. Run logs in `.runtime` are private development artifacts and are not committed.

Reproduce using scripts/build.sh, the chosen run guide, scripts/smoke.py, and (native only) scripts/token-boundary.py. The short-token refresh option requires ACCESS_TOKEN_TTL=PT10S. CI executes native, Compose and kind deployment paths. Check the latest GitHub Actions run before relying on a deployment verification claim.

This report is evidence of the stated sample checks, not a security certification, performance benchmark, failover test or production-readiness approval. See security.md for explicit scope limits.
