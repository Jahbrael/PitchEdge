# Phase 14B Automated Fixture Discovery Walkthrough

Phase 14B removes the need to manually import fixtures before generating upcoming predictions. It adds an admin orchestration endpoint that uses registered/permitted source targets, stores raw snapshots, extracts scheduled fixtures, and optionally generates pending selections.

It still does not scrape search engines and it does not invent fixtures. If a public fixture feed has not published future matches yet, the response returns zero discovered fixtures with a warning.

## Added Components

DTOs:

- `FixtureDiscoveryRequest`
- `FixtureDiscoveryResponse`

Services:

- `FixtureDiscoveryService`
- `FixtureDiscoveryServiceImpl`

Updated files:

- `AdminFixtureController`
- `DailyPipelineScheduler`
- `AutomationProperties`
- `application.yml`

Added test:

- `FixtureDiscoveryServiceImplTest`

## Discovery Endpoint

Endpoint:

```http
POST /api/v1/admin/fixtures/discover
```

Example:

```bash
curl -X POST http://localhost:8080/api/v1/admin/fixtures/discover \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA", "SERIE_A"],
    "discoveryDate": "2026-06-07",
    "fixtureDateFrom": "2026-06-07",
    "fixtureDateTo": "2026-08-31",
    "autoRegisterFootballDataSources": true,
    "forceRefresh": false,
    "forceReprocess": false,
    "generatePendingSlate": true,
    "modelVersion": "phase5-deterministic-v1",
    "forceRegeneratePredictions": false
  }'
```

If `targetSeasonLabel` is omitted, the service computes the football season from the discovery date:

- June through December: `year/year+1`
- January through May: `year-1/year`

For `2026-06-07`, the default is `2026/2027`.

## Pipeline Behavior

The endpoint runs this sequence:

1. Resolve active requested MVP leagues.
2. Optionally upsert the Football-Data latest fixture source targets.
3. Trigger the normal daily refresh lifecycle.
4. Store raw snapshots using the existing `RawSnapshot` table.
5. Run extraction against successful snapshots.
6. Query `SCHEDULED` fixtures in the requested date window.
7. Optionally call pending slate generation for those fixtures.

Refresh and extraction remain idempotent. If a successful refresh/extraction exists for the same league/date and force flags are false, the cached state is reused.

## Scheduled Automation

New config:

```yaml
bet-ai:
  automation:
    run-fixture-discovery: false
    fixture-discovery-auto-register-football-data-sources: true
    fixture-discovery-generate-pending-slate: false
    fixture-discovery-target-season-label:
```

Environment variables:

```bash
export BETAI_AUTOMATION_RUN_FIXTURE_DISCOVERY=true
export BETAI_AUTOMATION_FIXTURE_DISCOVERY_AUTO_REGISTER_FOOTBALL_DATA_SOURCES=true
export BETAI_AUTOMATION_FIXTURE_DISCOVERY_GENERATE_PENDING_SLATE=false
export BETAI_AUTOMATION_FIXTURE_DISCOVERY_TARGET_SEASON_LABEL=2026/2027
```

When `run-fixture-discovery=true`, the scheduler uses fixture discovery as the refresh/extraction step, then continues to features, predictions, and settlement. This avoids duplicate refresh/extraction work in the same automated run.

## Current Limits

- The default automated source is the registered Football-Data latest fixtures CSV.
- If that feed has no rows for the MVP leagues, discovery returns no fixtures.
- It is not a general web search crawler.
- Additional official or permitted fixture sources can be added later as more `SourceTarget` records and source-specific extractors.
- The manual import endpoint from Phase 14 remains available as an admin fallback.

## Verification

Run:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw test
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw package
```
