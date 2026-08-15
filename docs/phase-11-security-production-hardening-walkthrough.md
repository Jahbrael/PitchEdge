# Phase 11 Security And Production Hardening Walkthrough

Phase 11 adds baseline API hardening for the Spring Boot application. It protects the existing API surface and also supports the Phase 10 dashboard by allowing only static dashboard assets while keeping dashboard data under the protected admin API.

## Added Dependency

`pom.xml` now includes:

```xml
spring-boot-starter-security
```

## Added Java Components

- `SecurityProperties`: environment-driven security configuration.
- `SecurityConfig`: stateless Spring Security filter chain, CORS, route authorization, and security headers.
- `AdminApiKeyAuthenticationFilter`: API-key authentication for `/api/v1/admin/**`.
- `RateLimitingFilter`: in-memory fixed-window rate limiting for `/api/v1/**`.

## Route Access Rules

Open:

- `GET /actuator/health/**`
- `GET /actuator/info`
- `/api/v1/predictions/**`
- `GET /admin/dashboard.html`
- `GET /admin/dashboard.css`
- `GET /admin/dashboard.js`

Protected:

- `/api/v1/admin/**`

Denied:

- Any other route unless explicitly allowed later.

## Admin API Key

Admin routes require this header:

```text
X-BETAI-ADMIN-KEY: <admin key>
```

Local development default:

```text
local-dev-admin-key
```

Production must override it:

```bash
export BETAI_ADMIN_API_KEY='replace-with-a-long-random-secret'
```

Example:

```bash
curl -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  "http://localhost:8080/api/v1/admin/settlement/accuracy?leagueCode=PREMIER_LEAGUE&modelVersion=phase5-deterministic-v1&accuracyDate=2026-06-06"
```

Without the header, admin endpoints return:

```json
{"status":401,"error":"Unauthorized","message":"Admin API key is missing or invalid."}
```

## Rate Limiting

Defaults:

- Public API: `60` requests per minute per client IP.
- Admin API: `120` requests per minute per client IP.

Environment overrides:

```bash
export BETAI_PUBLIC_REQUESTS_PER_MINUTE=60
export BETAI_ADMIN_REQUESTS_PER_MINUTE=120
```

When a client exceeds the configured limit, the API returns:

```json
{"status":429,"error":"Too Many Requests","message":"Rate limit exceeded for public routes."}
```

The response includes:

- `Retry-After`
- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`

This is an in-memory limiter suitable for a single-node MVP. Multi-node deployment should move rate limiting to a reverse proxy, API gateway, or shared store.

## CORS

Default allowed origins:

```text
http://localhost:3000
http://localhost:5173
```

Override:

```bash
export BETAI_CORS_ALLOWED_ORIGINS='https://your-production-frontend.example'
```

## Security Headers

Configured:

- `X-Content-Type-Options`
- `X-Frame-Options: DENY`
- `Referrer-Policy: no-referrer`
- `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; form-action 'none'; base-uri 'none'`

The app also disables:

- CSRF, because the API is stateless and does not use cookie sessions.
- Form login.
- HTTP Basic.
- Server-side sessions.

## Server Hardening

Configured:

- `server.max-http-request-header-size`
- `server.error.include-stacktrace: never`
- `server.error.include-binding-errors: never`

The default Spring generated-password user configuration is excluded because this app uses API-key authentication for admin endpoints instead of interactive login.

## Verified Local Output

Verified on a temporary server:

- `GET /actuator/health` returned `200`.
- Admin accuracy endpoint without `X-BETAI-ADMIN-KEY` returned `401`.
- Admin accuracy endpoint with `X-BETAI-ADMIN-KEY: local-dev-admin-key` returned `200`.
- Public form endpoint returned `200`.
- With `BETAI_PUBLIC_REQUESTS_PER_MINUTE=2`, the third public request returned `429`.
- Final Maven package build passed.

## Current Limits

- Admin auth is API-key based, not user/role login.
- Rate limiting is in-memory and resets on restart.
- No audit table is written for authentication failures yet.
- TLS termination should be handled by the deployment layer, such as Nginx, Caddy, Traefik, or a cloud load balancer.
- Secrets should be supplied through environment variables or a secret manager, not committed configuration.

## Phase 12 Continuation

The public prediction web UI is documented in [phase-12-public-prediction-web-ui-walkthrough.md](</home/dell/IdeaProjects/Bet AI/docs/phase-12-public-prediction-web-ui-walkthrough.md>).
