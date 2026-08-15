# Phase 10 Admin Dashboard Walkthrough

Phase 10 adds an operational admin dashboard for the foundation, ingestion, extraction, feature engineering, prediction, settlement, export, and security layers already built.

The dashboard is not a betting UI. It is an operator view for system health, source reliability, refresh/extraction/prediction run history, and aggregate database counts.

## Added Java Components

- `AdminDashboardController`: protected admin API route for dashboard data.
- `AdminDashboardService`: dashboard aggregation service boundary.
- `AdminDashboardServiceImpl`: read-only aggregation across league, source, run, prediction, and accuracy repositories.

Added response DTOs:

- `DashboardOverviewResponse`
- `DashboardTotalsResponse`
- `DashboardLeagueStatusResponse`
- `DashboardSourceHealthResponse`
- `DashboardRunSummaryResponse`

Updated repositories with count/recent-run methods:

- `TeamRepository`
- `MatchRepository`
- `SourceTargetRepository`
- `DataRefreshLogRepository`
- `ExtractionRunRepository`
- `FeatureGenerationRunRepository`
- `PredictionGenerationRunRepository`
- `SettlementRunRepository`
- `PredictionSelectionRepository`
- `MarketDefinitionRepository`

## Added Static Admin Page

Static files:

- `src/main/resources/static/admin/dashboard.html`
- `src/main/resources/static/admin/dashboard.css`
- `src/main/resources/static/admin/dashboard.js`

Browser URL:

```text
http://localhost:8080/admin/dashboard.html
```

The static page is publicly loadable, but it cannot read admin data without the configured admin API key. The browser stores the key only in `sessionStorage` and sends it as:

```text
X-BETAI-ADMIN-KEY: <admin key>
```

## Dashboard API

Endpoint:

```bash
curl -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  http://localhost:8080/api/v1/admin/dashboard/overview
```

Response sections:

- `status`: `OK`, `RUNNING`, `DEGRADED`, or `CRITICAL`.
- `totals`: global counts for leagues, sources, teams, matches, selections, and accuracy rows.
- `leagues`: per-league season, source count, match count, and latest refresh status.
- `sources`: source registry reliability, active state, rate limit, timeout, and failure counters.
- `recentRuns`: recent refresh, extraction, feature, prediction, and settlement run summaries.
- `alerts`: deterministic operational warnings derived from failed runs or repeated source failures.

## Security Integration

`SecurityConfig` now permits only the static dashboard assets:

- `GET /admin/dashboard.html`
- `GET /admin/dashboard.css`
- `GET /admin/dashboard.js`

All dashboard data remains protected by the existing Phase 11 admin rule:

```text
/api/v1/admin/** requires ROLE_ADMIN via X-BETAI-ADMIN-KEY
```

The Content Security Policy was relaxed from `default-src 'none'` to a self-only policy so the local CSS, JavaScript, and same-origin API calls can run:

```text
default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; form-action 'none'; base-uri 'none'
```

## Verified Local Output

Verified on a temporary server at port `18080`:

- `GET /admin/dashboard.html` returned `200 text/html`.
- `GET /admin/dashboard.css` returned `200 text/css`.
- `GET /admin/dashboard.js` returned `200 text/javascript`.
- `GET /api/v1/admin/dashboard/overview` without the admin key returned `401`.
- `GET /api/v1/admin/dashboard/overview` with `X-BETAI-ADMIN-KEY: local-dev-admin-key` returned dashboard JSON.
- Security headers include `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, and the self-only CSP.
- Maven package build passed under JDK 21.

Build command used:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw -q -DskipTests package
```

## Running Locally

Use JDK 21 for Maven:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

Then open:

```text
http://localhost:8080/admin/dashboard.html
```

Default local admin key:

```text
local-dev-admin-key
```

Production must override it:

```bash
export BETAI_ADMIN_API_KEY='replace-with-a-long-random-secret'
```

## Current Limits

- The dashboard is a static HTML/CSS/JS page, not a full frontend build pipeline.
- There is no user login or per-user role management yet.
- The page does not trigger refresh, extraction, prediction, or settlement actions. It only observes system state.
- Run-history aggregation is intentionally read-only and capped to recent rows for a lightweight MVP admin view.

## Phase 11 Continuation

Security and production hardening are documented in [phase-11-security-production-hardening-walkthrough.md](</home/dell/IdeaProjects/Bet AI/docs/phase-11-security-production-hardening-walkthrough.md>).
