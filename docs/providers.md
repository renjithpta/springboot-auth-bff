# Add an enterprise OIDC provider

## Registration ownership

The Spring authorization server is registered with Entra/Okta/another provider. The BFF is registered with the Spring authorization server. React is not an OAuth client. For Entra, register a **Web** application callback:

```text
http://127.0.0.1:9000/login/oauth2/code/entra
```

Use approved HTTPS identity hostnames for deployed environments. The BFF callback stays:

```text
http://localhost:8080/login/oauth2/code/platform
```

These callbacks belong to different OAuth relationships; do not interchange them.

## Step 1 — obtain approved metadata

Request tenant/provider issuer, application client ID, approved confidential-client authentication method and credential, discovery metadata, and required consent. Start with OIDC scopes `openid profile`; the authorization server issues its own application access tokens, so upstream flight API scopes are not necessary. Add Graph/API permissions only for an explicit requirement.

## Step 2 — configure

Copy `infra/config/providers-example.yml` to `.runtime/providers.yml`. Fill provider metadata and supply ENTRA_TENANT_ID, ENTRA_CLIENT_ID, ENTRA_CLIENT_SECRET through the environment or private `.env`. Read the actual discovery document to confirm issuer and JWKS URLs. The sample's upstream client authentication method is configurable: Entra example uses `client_secret_post`; the simulator uses `client_secret_basic`.

Native execution loads explicit environment variables first and `.env` values second:

```bash
SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:./.runtime/providers.yml scripts/native.sh start --redis
```

The Compose guide mounts `.runtime/providers.yml` automatically. For Kubernetes, update the `providers` ConfigMap and supply upstream credentials through Secrets; restart the auth deployment after configuration changes. Add another entry under `app.federation.providers` to support another OIDC provider. Adding providers requires restart; hot reload is not implemented. SAML needs a separate Spring Security SAML integration and is not implemented by this OIDC configuration format.

## Step 3 — onboard identity and entitlements

Before an external user can access the app, an administrator must create an application account/entitlement record and map the verified provider issuer + subject. Example SQL, executed using an approved database administration connection:

```sql
-- Use a unique account identifier; this is not an email-based merge.
insert into app_user(username,password_hash,enabled)
values ('external-ops-001','!EXTERNAL_ONLY!',true);
insert into user_role values ('external-ops-001','OPERATOR');
insert into user_terminal values ('external-ops-001','T1');
insert into identity_link(issuer,subject_id,username)
values ('https://login.microsoftonline.com/REPLACE-TENANT/v2.0',
        'REPLACE-VERIFIED-SUBJECT','external-ops-001');
```

The external-only password marker is deliberately not a usable password hash. Obtain `sub` through a controlled onboarding process against the verified provider issuer; never accept a browser-supplied identity claim as proof. Entra `sub` can depend on the registered application; do not blindly reuse a subject from another application's token.

Unlinked external identities are denied. The local simulator is the only fixture that creates a predefined link (`sim-user` → `operator`). Disable demo users and simulator links outside development. Do not enable a local password on enterprise-linked accounts as an outage fallback.

## Step 4 — test

1. Open the app and start login.
2. Select the configured provider on the identity login page.
3. Authenticate with a permitted test user.
4. Verify successful BFF session, allowed T1 operation, and denied T2 operation.
5. Repeat with an unlinked user; sign-in must fail.
6. Disable the application account and confirm new login/refresh cannot issue fresh API tokens.
7. Verify provider account deactivation/session revocation according to your chosen propagation mechanism. Upstream deactivation alone does not automatically revoke this application's tokens.

## Local simulator

Start native/hybrid with `--simulator`. It runs another isolated authorization-server process at `127.0.0.1:9001`, with its own DB and signing key. The main identity service has an ordinary configured upstream OIDC registration. Login using `sim-user` and DEMO_PASSWORD.

This validates the federation protocol and explicit linking without tenant credentials. It does not certify compatibility with your tenant's Conditional Access, MFA, certificates, or logout policies. Real provider acceptance testing is still required.
