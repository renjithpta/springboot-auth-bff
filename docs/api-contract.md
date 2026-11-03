# API, logging and error conventions

## URI convention

Owned REST resources use lowercase plural nouns under `/bff/api/v1` for browser contracts and `/api/v1` for service contracts. Protocol endpoints retain Spring's standard paths. Do not rename OAuth protocol paths merely to make them look like business APIs.

| Method | Browser endpoint | Behavior |
|---|---|---|
| GET | `/bff/api/v1/session` | 200 with authentication presentation; no tokens |
| GET | `/bff/api/v1/csrf-token` | 200 with session-bound token/header name; no-store |
| DELETE | `/bff/api/v1/session` | CSRF-protected local logout; 204 |
| GET | `/bff/api/v1/flights` | Fixed relay to GET `/api/v1/flights` |
| POST | `/bff/api/v1/flights/{id}/delay-approvals` | Fixed relay to simulation action resource |
| GET | `/oauth2/authorization/platform` | BFF login initiation; browser navigation |
| GET | `/login/oauth2/code/platform` | BFF callback; gateway forwards it |

Local identity form login uses `/login`. Authorization-server endpoints include `/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/.well-known/openid-configuration`, and OIDC endpoints when enabled. Consumers should use discovery metadata. Password grant is not enabled.

## Success response

Business responses use a `data` property. Session/CSRF responses are deliberately small purpose-specific contracts rather than pretending to be paginated business collections.

```json
{"data":[{"id":"FL-101","terminal":"T1","destination":"London","status":"Scheduled"}]}
```

## Errors

Controller errors use RFC 9457 `application/problem+json` with status/detail/instance and a correlation extension. Authentication/CSRF handlers use minimal problem responses. OAuth protocol endpoints keep the OAuth-defined error format. Infrastructure filters and gateway-generated failures may have framework-defined errors; a fully unified edge error renderer is a production extension.

```json
{"type":"about:blank","title":"Forbidden","status":403,"detail":"Role or terminal policy denied approval","instance":"/api/v1/flights/FL-202/delay-approvals","requestId":"2e562ae2-d21f-438c-b1fe-9581cf41a10c"}
```

401: sign-in/token invalid; 403: permission or CSRF denied; 404: missing resource; 503: relevant dependency unavailable; 500: unexpected server fault with no public stack trace. The UI displays safe error details. Retry rules must account for request idempotency; writes are not automatically replayed.

## Logging

Servlet services emit structured Logstash JSON. RequestLog logs method, URI path, status and elapsed milliseconds; a bounded `X-Request-ID` is generated/propagated. Query strings, passwords, cookies and OAuth tokens are not logged. Approval simulation emits a business event with subject, flight and result. Treat subjects as personal data and apply access/retention controls.

Request IDs provide correlation, not authenticated identity and not full distributed tracing. Gateway and external-provider tracing, business audit durability, OpenTelemetry instrumentation and production dashboards are extension points, not delivered capabilities.
