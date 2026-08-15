# Phase 3 Extraction Walkthrough

Phase 3 converts raw snapshots into normalized football data. For the current MVP sources, the raw snapshots are Football-Data CSV files, so extraction is deterministic and does not use an LLM. An AI extraction adapter can be added later for genuinely unstructured HTML, but it must follow the same validation and audit model.

## Added Database Objects

- `team_aliases`: source-specific team names mapped to canonical `teams`.
- `extraction_runs`: one auditable extraction attempt per raw snapshot.
- `extraction_validation_errors`: row-level validation failures.
- `match_statistics`: normalized shots, fouls, corners, cards, and referee data linked to `matches`.
- `V3__extraction_and_match_statistics.sql`: Flyway migration for the Phase 3 schema.

## Added Java Components

- `AdminExtractionController`: admin endpoints for raw snapshot and daily extraction.
- `ExtractionService`: extraction application boundary.
- `FootballDataCsvExtractionService`: deterministic parser for Football-Data CSV snapshots.
- `ExtractionRun`, `ExtractionValidationError`, `TeamAlias`, `MatchStatistics`: new JPA entities.
- Repositories for extraction runs, validation errors, aliases, and match statistics.

## Validation Rules

The extractor rejects a row when:

- `Date` is missing or cannot be parsed.
- `HomeTeam` or `AwayTeam` is missing.
- Home and away teams normalize to the same value.
- Only one of `FTHG` or `FTAG` is present.
- Scores or stat fields are not non-negative integers.
- `FTR` contradicts the full-time score.

The extractor does not infer missing scores or statistics. Optional blank stats remain `null`.

## Endpoints

Extract one raw snapshot:

```bash
curl -X POST "http://localhost:8080/api/v1/admin/extraction/raw-snapshots/<raw-snapshot-id>?forceReprocess=false"
```

Extract all successful raw snapshots for a league/date:

```bash
curl -X POST http://localhost:8080/api/v1/admin/extraction/daily \
  -H "Content-Type: application/json" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA", "SERIE_A"],
    "snapshotDate": "2026-06-06",
    "forceReprocess": false
  }'
```

Use `forceReprocess: true` when you intentionally want a new extraction run for the same raw snapshot.

## Verified Local Extraction

Using the Phase 2 Football-Data snapshots for `2026-06-06`, extraction produced:

- `60` teams.
- `60` team aliases.
- `1,140` matches.
- `1,140` match statistics rows.
- `3` extraction runs.
- `0` validation errors.

Per league:

- Premier League: `380` matches.
- La Liga: `380` matches.
- Serie A: `380` matches.

## Current Limits

- Football-Data CSV is supported.
- Unstructured HTML extraction is not implemented yet.
- This phase stores historical fixtures/results and match statistics, but it does not compute features or probabilities.
- Prediction generation remains the next phase.

## Phase 4 Continuation

Feature engineering over normalized matches/statistics is documented in [phase-4-feature-engineering-walkthrough.md](</home/dell/IdeaProjects/Bet AI/docs/phase-4-feature-engineering-walkthrough.md>).
