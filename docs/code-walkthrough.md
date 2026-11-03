# Read the code as a login story

## 1. The gateway receives a browser request

Start in `code/gateway/src/main/resources/application.yml`. Approved BFF/session/protocol callback paths route to BFF; everything else routes to the separate UI server. Browser-supplied Authorization is removed on BFF routes. The gateway does not validate business permissions.

## 2. The BFF asks the identity service to authenticate

`BffSecurity` registers the BFF as a confidential OAuth client and enables PKCE. Authorization URLs use the public issuer address. Token/JWKS/UserInfo backchannels can use a private address. This distinction makes the same image usable locally, in Docker, and in Kubernetes.

## 3. The identity server chooses a login mechanism

`AuthorizationConfig` has two ordered security chains. The first owns OAuth/OIDC protocol endpoints. The second owns human login and configured upstream providers. `LoginPage` reads configured provider registrations rather than hard-coding provider buttons. `ProviderConfiguration` binds OIDC registrations from YAML.

## 4. Local users and external users converge

`IdentityStore` loads local credential records and role/terminal grants. For external identities it requires an exact issuer/subject link to an enabled account. No email-based merge is performed. `DatabaseSetup` creates the sample schema and optional development fixtures. `SimulatorLink` exists only to demonstrate approved federation mapping.

## 5. The issuer signs application tokens

`AuthorizationConfig.claims` emits application roles and terminal assignments for the current account. The issuer owns audience `flight-api`. Its signing key is loaded from a persisted PKCS8 file. Spring handles authorization codes, PKCE validation, token endpoint processing and refresh-token issuance. JDBC repositories persist protocol state.

## 6. BFF protects browser state

`TokenCipher` implements nonce-based AES-GCM. `EncryptedClients` stores token records separately for each browser session. `BffSecurity` encrypts complete Redis session values because the Spring Security context can contain an OIDC ID token. The key must be identical across replicas and protected independently of Redis.

## 7. Business requests use a fixed relay

`BffController` exposes a small, explicit contract. `FlightRelay` acquires a Redis refresh lock, asks Spring's authorized-client manager for a valid token, and calls only known Flight API paths. It never accepts a downstream URL from the browser. Session cookies never go to the API; the API receives a bearer access token.

## 8. The service makes the final decision

`ApiSecurity` validates JWT cryptography, issuer, timestamps and audience. `FlightPolicy` checks OAuth scope and role, then checks terminal access against a server-owned resource. `FlightController` demonstrates list filtering, an allowed approval, and two independent reasons to deny it.

## 9. Errors remain safe and understandable

`ApiErrors` turns expected controller failures into problem details. `RequestLog` adds correlation without logging credentials. Tests prove security properties rather than merely repeating implementation statements. Read `docs/validation.md` for the precise execution scope.
