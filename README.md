# AOBP Spring identity reference application

[![Verify reference application](https://github.com/renjithpta/springboot-auth-bff/actions/workflows/verify.yml/badge.svg)](https://github.com/renjithpta/springboot-auth-bff/actions/workflows/verify.yml)

A runnable security sample for Java 17, Spring Boot **3.4.13**, Spring Cloud Gateway **2024.0.3 BOM**, and Maven **3.9.x**. A separate React UI uses a confidential BFF. A Spring Authorization Server authenticates local database accounts and any configured OIDC providers. The Flight API validates application tokens and applies **RBAC and terminal-based ABAC**.

> This is an educational reference, not a turnkey production identity product. Boot 3.4 reached end of open-source support in December 2025. The requested version is retained deliberately. Read [security and production gaps](docs/security.md) before using real users or data.

## Choose how to run

| Mode | Application processes | Infrastructure | Guide |
|---|---|---|---|
| Native | Java + Python static UI server | Native Redis; file-backed H2 identity DB | [Native](docs/run-native.md) |
| Hybrid | Java + Python static UI server | Redis in Compose; file-backed H2 | [Hybrid](docs/run-hybrid.md) |
| Docker CLI | Separate containers | Redis container; persisted H2 | [Docker](docs/run-docker.md) |
| Docker Compose | Separate containers | Redis container; persisted H2 | [Compose](docs/run-compose.md) |
| Kubernetes | Deployments and ClusterIP Services | Redis PVC + identity DB PVC | [Kubernetes](docs/run-kubernetes.md) |

Start with the native or hybrid guide. Each guide includes prerequisites, commands, expected output, verification, logs, and shutdown. Do not run two modes on the same ports or database simultaneously.

## Repository map

```text
code/
  auth-server/       Login, external OIDC federation, local users, token issuance
  bff/               Session APIs, encrypted Redis storage, refresh, fixed token relay
  common/            Servlet correlation logs and problem responses
  flight-service/    JWT validation and service-owned authorization
  gateway/           Routes browser traffic; does not issue tokens
  ui/                Separately deployed React application
infra/
  config/            Provider configuration examples
  docker/            Multi-stage image builds
  compose/           Full stack and infrastructure-only definitions
  k8s/               Plain Kubernetes manifests and secret documentation
scripts/              Initialization, lifecycle, build and end-to-end verification
docs/                Architecture, provider onboarding, operations and runbooks
```

## Demonstrate the feature

1. Open **http://localhost:8080**.
2. Select **Continue to sign in**. The identity server opens at **http://127.0.0.1:9000**.
3. Sign in as `operator` using `DEMO_PASSWORD` from your locally generated `.env`.
4. You see only `FL-101` in Terminal `T1`.
5. Approve `FL-101`: `SIMULATED_APPROVAL` confirms RBAC + ABAC allowed it.
6. Test `FL-202` in `T2`: expect `403` because the terminal assignment does not allow it.
7. End the application session. Sign in as `viewer`: reading is allowed; approval returns `403` even for `T1`.
8. With the optional simulator enabled, choose **Airport Partner** and sign in as `sim-user` at port `9001`. Its explicitly linked identity receives the operator fixture's application permissions.

All three fixture passwords are generated locally, not committed. Local users are enabled only by `DEMO_USERS=true`. Approval is a simulation and does not persist flight changes.

## Further reading

- [Architecture and flows](docs/architecture.md)
- [Provider configuration and Entra onboarding](docs/providers.md)
- [Configuration reference](docs/configuration.md)
- [Code walkthrough](docs/code-walkthrough.md)
- [API, logs and error conventions](docs/api-contract.md)
- [Security decisions and limitations](docs/security.md)
- [Tests and actual validation evidence](docs/validation.md)
