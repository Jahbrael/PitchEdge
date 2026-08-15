# Phase 14C Active League Expansion Walkthrough

Phase 14C adds two active calendar-year leagues so the app can keep building against real football data while the Premier League, La Liga, and Serie A are between seasons.

Added leagues:

- `ALLSVENSKAN` - Sweden Allsvenskan
- `ELITESERIEN` - Norway Eliteserien

Football-Data lists Sweden Allsvenskan and Norway Eliteserien in its extra-league coverage and publishes CSV data for those leagues. The app now seeds their result and fixture source targets automatically at startup.

## Source Targets Seeded At Startup

Allsvenskan:

- Results: `https://www.football-data.co.uk/new/SWE.csv`
- Fixtures: `https://www.football-data.co.uk/new_league_fixtures.csv`
- Filters: `Country=Sweden`, `League=Allsvenskan`
- Season label: `2026`

Eliteserien:

- Results: `https://www.football-data.co.uk/new/NOR.csv`
- Fixtures: `https://www.football-data.co.uk/new_league_fixtures.csv`
- Filters: `Country=Norway`, `League=Eliteserien`
- Season label: `2026`

## Parser Change

The existing Football-Data extractor now supports two CSV schemas:

- Main European schema: `HomeTeam`, `AwayTeam`, `FTHG`, `FTAG`, `FTR`
- Extra-league schema: `Home`, `Away`, `HG`, `AG`, `Res`

Extra-league files do not include match-level corners/cards/shots in the same way as the main European files. The app stores goal/result data and leaves unavailable statistics null. The probability engine already falls back to league defaults for unavailable corner/card features.

The extractor also protects finished results from stale fixture-feed rows. If a fixture row has blank scores but the matching stored fixture is already `FINISHED`, the existing score and status are preserved.

## Run Refresh And Extraction

After restarting the app:

```bash
curl -X POST http://localhost:8080/api/v1/admin/refresh/daily \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCodes": ["ALLSVENSKAN", "ELITESERIEN"],
    "refreshDate": "2026-06-07",
    "forceRefresh": true
  }'
```

Then:

```bash
curl -X POST http://localhost:8080/api/v1/admin/extraction/daily \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCodes": ["ALLSVENSKAN", "ELITESERIEN"],
    "snapshotDate": "2026-06-07",
    "forceReprocess": true
  }'
```

Then:

```bash
curl -X POST http://localhost:8080/api/v1/admin/features/daily \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCodes": ["ALLSVENSKAN", "ELITESERIEN"],
    "calculationDate": "2026-06-07",
    "forceRegenerate": true
  }'
```

If the fixture feed contains future rows for the requested date window, run pending slate generation:

```bash
curl -X POST http://localhost:8080/api/v1/admin/predictions/generate-pending-slate \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCodes": ["ALLSVENSKAN", "ELITESERIEN"],
    "fixtureDateFrom": "2026-06-07",
    "fixtureDateTo": "2026-08-31",
    "modelVersion": "phase5-deterministic-v1",
    "forceRegenerate": true
  }'
```

## Current Caveat

On `2026-06-07`, Football-Data's `new_league_fixtures.csv` may still contain only the most recent weekly fixture set. If no future rows exist, extraction will still load finished result history for feature generation, but pending predictions will wait until the fixture file publishes future rows.
