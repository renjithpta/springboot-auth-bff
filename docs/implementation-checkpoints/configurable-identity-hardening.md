# Configurable identity hardening — implementation checkpoint

Date: 3 November 2026.

## Publication status

This branch contains a checkpoint document only. The implementation described below was written in the development workspace but **has not been pushed**. The execution environment became unavailable during final formatting/configuration checks. Do not treat this document or its branch as the amended application.

Main retains the previous verified application at commit 60b32b8d6f286a2353c922271e2de6f70465a8ca. Its existing validation report concerns that earlier baseline.

## Requested amendments

- Enforced formatting for Java, JSX/CSS, JSON/YAML/XML/Markdown, Python and shell.
- Configuration-only selection of dev, test, staging and prod environments.
- Independent Entra and Okta enable flags, configuration-driven OIDC registrations and scopes.
- A local-login flag that hides the form and denies password submissions at the server.
- Shared encrypted BFF state, renewable refresh leases and owner-fenced writes/deletes.
- Logout tombstones, session-key-bound token ciphertext, bounded idle/absolute lifetimes and failed-refresh session termination.
- Encrypted shared issuer sessions; safe PostgreSQL-backed issuer scaling with controlled one-time schema/client bootstrap.
- Updated mode-specific runbooks, environment/provider documentation and CI.

## Workspace implementation

The repository checkout was /workspace/scratch/e9b6c5de1916/app, based on the main commit above. Recover that workspace before reimplementing.

Notable new/updated files include:

- code/common: AesGcmCipher, EncryptedSessionSerializer, DeploymentGuard.
- code/auth-server: ProviderConfiguration, AuthorizationConfig, IdentitySessionConfiguration, IdentitySessionLifetime, IdentitySessionController, local-login template and environment profiles.
- code/bff: TokenSettings, RefreshCoordinator, SessionLifetime, SessionTermination, EncryptedClients, TokenCipher, FlightRelay and BffSecurity.
- infra/environments: dev/test/staging/prod non-secret defaults and a private-settings example.
- infra/config/providers.yml: independently enabled Entra/Okta templates and generic OIDC schema.
- scripts/runtime_config.py and environment.py: literal dotenv loading, private credentials, isolated runtime state, scopes and redacted diagnostics.
- scripts/render-k8s.py: configuration/Secret separation, safe replica checks, checksums and optional test PostgreSQL deployment.
- scripts/native.py: configurable readiness ports, simulator isolation and a second BFF.
- Updated Compose, Docker CLI, hybrid and Kubernetes launchers.
- Spotless in Maven; root Prettier/XML tooling; Black/shfmt scripts and CI.
- New environment, formatting and distributed-token guides; revised existing runbooks.

Generator/check scripts and logs were under the ignored .tooling directory. Private .env files, runtime state, generated keys, dependencies and .tooling must not be committed.

## Checks observed before the interruption

- The clean Java verification completed and the amended native stack started.
- 42 browser-style checks passed with local login, simulated upstream OIDC, shared access through two BFF instances and concurrent refresh.
- Seven actual signed-JWT boundary checks passed.
- Four real Redis contract tests passed: coordination between independent instances, stale-owner write/delete rejection, lease renewal and logout resurrection prevention.
- Python literal-settings tests passed; a diagnostic-redaction test was subsequently added.
- Earlier formatting/structural checks passed, but the final post-edit pass was interrupted.

These results do not replace final verification of the complete latest workspace snapshot.

## Work still required

1. Restore execution access and inspect the working tree and pending tool results.
2. Run the complete formatting checks and clean Maven verification.
3. Rebuild the React UI and rerun native two-BFF/federation/refresh/JWT/Redis checks.
4. Check Compose literal credential preservation, including dollar signs, quotes, backslashes and equals signs.
5. Review all rendered settings, scoped env files and ConfigMaps for correct endpoint selection and absence of credentials.
6. Publish the actual source changes to a reviewable implementation branch or main as authorized.
7. Run the updated CI matrix: native, hybrid, Docker CLI, Compose, Kubernetes H2, PostgreSQL issuer replicas and quality checks.
8. Resolve failures, update the validation report with exact evidence and complete publication.

The PostgreSQL issuer-replica CI job and real Entra/Okta tenant acceptance had not been executed for the amendment at interruption. Real tenant testing requires approved registrations and credentials.

## Important contracts

- The BFF is registered with the Spring issuer; the Spring issuer is the Web client registered with Entra/Okta. React receives no OAuth tokens.
- All BFF replicas share their encryption key, client settings and namespaces.
- Issuer replicas separately share their session encryption key, signing key and PostgreSQL state.
- Multiple H2 replicas or concurrent schema/client bootstrap are rejected.
- A refresh lease is not a transaction spanning Redis and the issuer; a crash after rotation can require sign-in again.
- Application logout does not instantly revoke already-issued JWTs or corporate SSO sessions.
- Boot 3.4 remains the requested unsupported OSS line; production prerequisites remain documented.
