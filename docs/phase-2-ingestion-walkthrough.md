# Phase 2 Ingestion Walkthrough

Phase 2 adds the first real ingestion layer. It does not parse football statistics into normalized fixtures yet; it registers source targets, fetches raw HTML, extracts visible text, and stores immutable raw snapshots for later AI-assisted extraction.

## Added Database Objects

- `source_targets`: admin-managed scrape targets by league/source type.
- `raw_snapshots`: immutable scrape results linked to a source target, league, refresh log, date, URL, checksum, raw HTML, extracted visible text, HTTP metadata, and scrape status.
- `V2__source_registry_and_raw_snapshots.sql`: Flyway migration that upgrades the Phase 1 database.

## Added Domain Packages

- `domain/source`: `SourceTarget`, `SourceType`, `RenderMode`.
- `domain/snapshot`: `RawSnapshot`, `ScrapeStatus`.

## Added Repositories

- `SourceTargetRepository`: active target lookup by league, type, and reliability.
- `RawSnapshotRepository`: snapshot lookup by league/date and checksum deduplication.

## Added Scraping Components

- `UrlTemplateRenderer`: expands `{leagueCode}`, `{date}`, `{yyyyMMdd}`, and `{season}` placeholders.
- `RobotsTxtService`: checks `robots.txt` before fetching when required.
- `HostRateLimiter`: enforces per-host scrape spacing from `rateLimitPerMinute`.
- `StaticHttpSourceScraper`: fetches static HTML using Java `HttpClient`, stores response metadata, and extracts visible text using jsoup.
- `HashingService`: computes SHA-256 payload and aggregate refresh checksums.
- `SourceRefreshServiceImpl`: runs active source targets for a league/date and persists raw snapshots.

## Added Admin APIs

Create a source target:

```bash
curl -X POST http://localhost:8080/api/v1/admin/sources \
  -H "Content-Type: application/json" \
  -d '{
    "leagueCode": "PREMIER_LEAGUE",
    "sourceType": "FIXTURES",
    "name": "Example fixture page",
    "urlTemplate": "https://example.com/{date}",
    "renderMode": "STATIC_HTML",
    "active": true,
    "robotsTxtRequired": true,
    "rateLimitPerMinute": 6,
    "timeoutMs": 10000,
    "reliabilityScore": 50.00,
    "selectorsJson": {"fixtureRows": "table.fixtures tr"}
  }'
```

List source targets:

```bash
curl "http://localhost:8080/api/v1/admin/sources?leagueCode=PREMIER_LEAGUE"
```

Disable a source target without deleting its historical snapshots:

```bash
curl -X PATCH "http://localhost:8080/api/v1/admin/sources/<source-target-id>/active?active=false"
```

Update a source target:

```bash
curl -X PUT http://localhost:8080/api/v1/admin/sources/<source-target-id> \
  -H "Content-Type: application/json" \
  -d '{
    "leagueCode": "PREMIER_LEAGUE",
    "sourceType": "FIXTURES",
    "name": "Example fixture page",
    "urlTemplate": "https://example.com/{date}",
    "renderMode": "STATIC_HTML",
    "active": false,
    "robotsTxtRequired": true,
    "rateLimitPerMinute": 6,
    "timeoutMs": 10000,
    "reliabilityScore": 50.00,
    "selectorsJson": {"fixtureRows": "table.fixtures tr"}
  }'
```

List recent raw snapshots:

```bash
curl "http://localhost:8080/api/v1/admin/raw-snapshots?leagueCode=PREMIER_LEAGUE&snapshotDate=2026-06-06"
```

Trigger a refresh:

```bash
curl -X POST http://localhost:8080/api/v1/admin/refresh/daily \
  -H "Content-Type: application/json" \
  -d '{"leagueCodes":["PREMIER_LEAGUE"],"forceRefresh":true}'
```

Use `forceRefresh: true` if a successful Phase 1 refresh already exists for the same league/date.

## Refresh Status Behavior

- Existing successful league/date refresh and `forceRefresh=false`: cache reused.
- No active source targets: refresh is marked `SKIPPED`.
- Every configured target fails, is blocked by `robots.txt`, or requires unsupported rendering: refresh is marked `FAILED`.
- At least one target succeeds: refresh is marked `SUCCESS`, with rejected counts recorded.

## Current Limits

- `STATIC_HTML` is implemented.
- `JS_RENDERED` targets are stored and tracked, but return `UNSUPPORTED_RENDER_MODE` until a browser worker is added.
- AI extraction, fixture normalization, feature engineering, and probability scoring remain later phases.
- Admin endpoints are still unsecured in local development; production auth is a later security phase.

## Phase 3 Continuation

Raw snapshot extraction and normalization are documented in [phase-3-extraction-walkthrough.md](</home/dell/IdeaProjects/Bet AI/docs/phase-3-extraction-walkthrough.md>).
