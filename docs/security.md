# Security decisions and production gaps

## Implemented controls

- Confidential BFF with authorization code + PKCE, framework state/nonce validation, server-owned token relay.
- Separate browser session cookie; HttpOnly and SameSite=Lax, configurable Secure flag.
- Session-bound CSRF protection on browser state changes; no token storage in browser JavaScript.
- AES-256-GCM protection of Redis session values and authorized-client records; fresh random nonce per encryption.
- Per-session authorized clients and Redis refresh coordination across BFF replicas.
- Persistent signing key and JDBC OAuth authorization/client/consent state.
- Local adaptive password hashes; exact issuer/subject links for external users; disabled-account checks on token issuance.
- Independent API JWT issuer/audience/time validation, OAuth scope, RBAC and terminal ABAC.
- Explicit fixed relay routes, bounded request ID, controlled downstream timeouts, no automatic write replay.
- Container non-root defaults, K8s secret separation, probes, resource bounds and private-service ingress policies.

## Threats and required operational controls

| Area | Risk | Mitigation / owner |
|---|---|---|
| Browser/XSS | Session actions can still be performed by malicious same-origin scripts | Frontend owner: CSP, dependency scanning, output encoding, trusted MFE supply chain |
| Credential attacks | Brute force and account enumeration | Identity owner: distributed rate limits/lockout, MFA, generic errors, monitoring; not implemented as a complete product here |
| Account linking | Email collisions lead to takeover | Exact verified issuer/subject onboarding; no automatic email merge |
| Refresh | Rotation races across replicas | Per-session Redis lock; bound all token requests below lease; design stronger fencing for stricter availability requirements |
| Token theft | Bearer tokens can be replayed until expiry | TLS, protected stores, short lifetime; stronger sender constraints and revocation if required |
| Provider deactivation | Application refresh/session outlives corporate access | Define and implement entitlement/session revocation integration and maximum reauthentication age |
| Logout | Existing JWT or upstream SSO session remains valid | UI clearly uses local logout; implement RP/upstream logout and revocation propagation for full SSO logout |
| DB compromise | OAuth grants and local password hashes are sensitive | Encrypt disks/backups, strict DB roles/network isolation, retention and key separation |
| Signing keys | Key loss breaks trust; theft permits forgery | Vault/HSM integration, stable keys, rotation overlap and incident procedure |
| Redis compromise | Session metadata and encrypted records exposed | Password, private network, TLS and ACLs; encryption key outside Redis; limit deserialization to authenticated records |

## Deliberate scope limits

This is a security reference sample, not an enterprise identity product. It does **not** provide account-administration UI/API, MFA enrollment, password recovery, SCIM, SAML federation, production audit retention, policy distribution, global revocation, tenant isolation, key rotation automation, full OpenTelemetry dashboards, or an HA identity cluster. Do not represent these as implemented.

H2 and single-instance authorization-server HTTP sessions are demo choices. Use an approved external database, controlled schema migrations and shared/appropriately routed identity sessions before scaling the issuer. The BFF can share Redis sessions across replicas, but the delivered lease lock is not a substitute for a formal consistency design under arbitrary partitions.

The requested Spring Boot 3.4.13 line is out of OSS support. Upgrade to a supported compatible Boot/Cloud/Security set or obtain approved extended support before production. Pin and scan container digests and dependencies in CI. HTTP localhost and generated demo accounts are development-only settings.

Provider credentials, password hashes and token records must never be committed. `.env` and `.runtime` are ignored. Kubernetes Secrets need encryption at rest and controlled access; base64 is not a security boundary.
