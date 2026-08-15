# Phase 7 Upcoming Fixture Source Integration Walkthrough

Phase 7 adds the production path for upcoming scheduled fixture ingestion. The application can now register Football-Data's latest fixtures CSV as a source for the three MVP leagues, filter the shared feed by division code, store raw snapshots, extract blank-score fixture rows as `SCHEDULED` matches, and later generate predictions for those scheduled matches.

## Source Decision

Football-Data is the active fixture source for this phase because its public data page states that fixture and betting odds files for upcoming games are made available, and its fixtures page exposes a downloadable latest fixtures CSV.

BBC Sport was inspected but not integrated. Its robots/terms text explicitly discourages automated scraping and dataset creation, so it is not a safe default source for this app.

## Added Database Metadata

Migration:

- `V7__upcoming_fixture_source_metadata.sql`

Added to `source_targets`:

- `source_season_token`: optional source URL token such as `2526` or `2627`.
- `target_season_label`: normalized season label to assign to extracted matches, such as `2026/2027`.

Added to `prediction_generation_runs`:

- `feature_season_label`: the feature season used to score fixtures. This lets future fixtures be scored from the latest completed feature baseline without mislabeling the fixture season.

## Added Java Components

- `AdminFixtureController`: admin endpoints for fixture source registration and scheduled fixture listing.
- `FootballDataFixtureSourceService`: service boundary for fixture source registration.
- `FootballDataFixtureSourceServiceImpl`: registers Football-Data latest fixture sources for the MVP leagues.
- `FootballDataFixtureSourceRegistrationRequest`
- `FootballDataFixtureSourceRegistrationResponse`
- `UpcomingFixtureResponse`

Updated components:

- `SourceTarget`: stores source/target season metadata.
- `SourceTargetRequest` and `SourceTargetResponse`: expose the new metadata.
- `UrlTemplateRenderer`: supports `{seasonToken}` and `{seasonLabel}` placeholders.
- `FootballDataCsvExtractionService`: supports `Div` filtering and target season labeling.
- `PredictionGenerationRequest`: supports optional `featureSeasonLabel`.
- `PredictionGenerationRun`: persists `featureSeasonLabel`.

## Football-Data Latest Fixtures Registration

Register the latest fixtures feed for the three MVP leagues:

```bash
curl -X POST http://localhost:8080/api/v1/admin/fixtures/sources/football-data/latest \
  -H "Content-Type: application/json" \
  -d '{
    "targetSeasonLabel": "2026/2027",
    "active": true,
    "robotsTxtRequired": true,
    "rateLimitPerMinute": 6,
    "timeoutMs": 10000
  }'
```

This creates or updates:

- Premier League: `Div = E0`
- La Liga: `Div = SP1`
- Serie A: `Div = I1`

All three source targets point at:

```text
https://www.football-data.co.uk/fixtures.csv
```

The feed is shared, so each league source uses `selectorsJson.divisionCode` to accept only its own rows.

## Daily Refresh And Extraction

Refresh:

```bash
curl -X POST http://localhost:8080/api/v1/admin/refresh/daily \
  -H "Content-Type: application/json" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA", "SERIE_A"],
    "refreshDate": "2026-06-06",
    "forceRefresh": true
  }'
```

Extract:

```bash
curl -X POST http://localhost:8080/api/v1/admin/extraction/daily \
  -H "Content-Type: application/json" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA", "SERIE_A"],
    "snapshotDate": "2026-06-06",
    "forceReprocess": false
  }'
```

When the latest fixture file contains no rows for `E0`, `SP1`, or `I1`, extraction returns `SKIPPED` for those source targets with:

```text
No rows matched the configured source filters.
```

That is correct behavior. It means the source was fetched, but the current public file does not contain MVP league fixtures.

## Query Scheduled Fixtures

```bash
curl "http://localhost:8080/api/v1/admin/fixtures/upcoming?leagueCodes=PREMIER_LEAGUE,LA_LIGA,SERIE_A&fromDate=2026-06-06&toDate=2026-08-31"
```

If Football-Data has no current MVP rows, this returns:

```json
[]
```

Once the feed contains `E0`, `SP1`, or `I1` rows with blank scores, extraction stores them as `SCHEDULED` matches.

## Generate Scheduled Predictions

After scheduled fixtures exist:

```bash
curl -X POST http://localhost:8080/api/v1/admin/predictions/generate \
  -H "Content-Type: application/json" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA", "SERIE_A"],
    "calculationDate": "2026-06-06",
    "featureSeasonLabel": "2025/2026",
    "fixtureDateFrom": "2026-08-01",
    "fixtureDateTo": "2026-08-31",
    "matchStatuses": ["SCHEDULED"],
    "modelVersion": "phase5-deterministic-v1",
    "forceRegenerate": true
  }'
```

`featureSeasonLabel` should point at the feature snapshot season used for scoring. For the current local database, that is `2025/2026`.

## Verified Local Output

On `2026-06-06`:

- V7 migration applied successfully.
- Football-Data latest fixture sources registered for all three MVP leagues.
- Refresh completed against active historical and latest-fixture sources.
- Latest-fixture extraction returned `SKIPPED` for all three MVP league filters because the current `fixtures.csv` contained no `E0`, `SP1`, or `I1` rows.
- Upcoming fixture query returned `[]`.
- Scheduled prediction generation returned `SKIPPED` cleanly when no scheduled matches existed.

## Current Limits

- The app cannot invent upcoming fixtures. If the public source has no MVP league rows, the scheduled fixture table remains empty.
- Football-Data latest fixtures are short-window fixtures, not necessarily full-season future schedules.
- Existing feature snapshots are still based on `2025/2026` finished matches.
- Full scheduled prediction output requires the public source to publish actual `E0`, `SP1`, or `I1` upcoming rows.

## Phase 8 Continuation

Post-match settlement and model accuracy tracking are documented in [phase-8-settlement-and-accuracy-tracking-walkthrough.md](</home/dell/IdeaProjects/Bet AI/docs/phase-8-settlement-and-accuracy-tracking-walkthrough.md>).
