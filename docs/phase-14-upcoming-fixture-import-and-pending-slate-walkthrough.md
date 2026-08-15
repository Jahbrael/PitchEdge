# Phase 14 Upcoming Fixture Import And Pending Slate Walkthrough

Phase 14 fixes the practical off-season gap: public feeds may not publish MVP league fixtures yet, but the app still needs a deterministic way to load real upcoming fixtures from a permitted or official source and generate pending selections for the public form.

This phase does not invent fixtures. It adds a protected structured import path. The operator is responsible for entering fixtures from a reliable permitted source.

## Added Java Components

DTOs:

- `UpcomingFixtureImportItem`
- `UpcomingFixtureImportRequest`
- `UpcomingFixtureImportResponse`
- `PendingSlateGenerationRequest`

Services:

- `UpcomingFixtureImportService`
- `UpcomingFixtureImportServiceImpl`
- `PendingSlateGenerationService`
- `PendingSlateGenerationServiceImpl`

Updated controllers:

- `AdminFixtureController`
- `AdminPredictionGenerationController`

Updated repositories:

- `MatchRepository`
- `FeatureGenerationRunRepository`

Added tests:

- `UpcomingFixtureImportServiceImplTest`

## Admin Fixture Import

Endpoint:

```http
POST /api/v1/admin/fixtures/import
```

Example:

```bash
curl -X POST http://localhost:8080/api/v1/admin/fixtures/import \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCode": "PREMIER_LEAGUE",
    "seasonLabel": "2026/2027",
    "fixtures": [
      {
        "homeTeam": "Arsenal",
        "awayTeam": "Chelsea",
        "matchDate": "2026-08-15",
        "kickoffTime": "15:00:00",
        "roundLabel": "Matchweek 1",
        "venue": "Emirates Stadium",
        "sourceFixtureKey": "OFFICIAL:EPL:2026-08-15:ARS-CHE"
      }
    ]
  }'
```

Behavior:

- Resolves existing teams by alias or canonical name.
- Creates a team and alias only if no known team exists.
- Stores imported fixtures as `SCHEDULED`.
- Leaves `homeScore` and `awayScore` as `null`.
- Upserts by `sourceFixtureKey`.
- Falls back to fixture identity: league, home team, away team, kickoff.
- Rejects same-team fixtures.
- Converts local kickoff time using the league timezone:
  - Premier League: `Europe/London`
  - La Liga: `Europe/Madrid`
  - Serie A: `Europe/Rome`

## Query Imported Fixtures

```bash
curl -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  "http://localhost:8080/api/v1/admin/fixtures/upcoming?leagueCodes=PREMIER_LEAGUE&fromDate=2026-08-01&toDate=2026-08-31"
```

## Generate Pending Slate

Endpoint:

```http
POST /api/v1/admin/predictions/generate-pending-slate
```

Example:

```bash
curl -X POST http://localhost:8080/api/v1/admin/predictions/generate-pending-slate \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE"],
    "fixtureDateFrom": "2026-08-01",
    "fixtureDateTo": "2026-08-31",
    "modelVersion": "phase5-deterministic-v1",
    "forceRegenerate": true
  }'
```

Behavior:

- Uses only `SCHEDULED` matches.
- Automatically finds the latest successful feature generation run per league.
- Uses that feature run's `calculationDate` and `seasonLabel`.
- Calls the existing prediction generation service.
- Creates `PENDING` prediction selections for imported scheduled fixtures.

This avoids manually guessing the correct `featureSeasonLabel`. In the current local database, the latest successful feature baseline is based on `2025/2026`, which is the correct baseline to score early `2026/2027` scheduled fixtures until new-season finished matches exist.

## Public Form After Pending Slate Generation

After importing fixtures and generating the pending slate, the public form can return batches:

```text
http://localhost:8080/predictions.html
```

Or with curl:

```bash
curl -X POST http://localhost:8080/api/v1/predictions/form \
  -H "Content-Type: application/json" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE"],
    "marketCodes": ["HOME_WIN", "OVER_1_5_GOALS", "OVER_2_5_GOALS", "BTTS_YES", "UNDER_3_5_GOALS"],
    "fixtureDateFrom": "2026-08-01",
    "fixtureDateTo": "2026-08-31",
    "batchCount": 1,
    "selectionsPerBatch": 3
  }'
```

The form still returns only `PENDING` selections. Settled historical predictions remain excluded from public betting output.

## Tests

Added `UpcomingFixtureImportServiceImplTest`:

- imports a structured fixture as `SCHEDULED`
- keeps scores null
- preserves season, venue, round, and source fixture key
- rejects same-team fixture rows

Existing `BatchBuilderTest` still verifies accumulator probability and batch conflict behavior.

Run:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw test
```

Verified:

- test build passed
- package build passed

## Current Limits

- The import endpoint trusts admin-entered fixture data. The data must come from a permitted reliable source.
- It does not parse PDFs or arbitrary fixture webpages yet.
- Team aliases are conservative. If an unknown abbreviation is imported, the service may create a new team unless an alias already exists.
- Pending slate generation needs at least one successful feature generation run for each requested league.
- Multi-league pending slate generation may use different latest feature dates internally, one per league.
