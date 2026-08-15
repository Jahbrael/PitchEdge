# Phases 17-19: Odds Ingestion, Full Pipeline, Backtesting

## Phase 17: Automated Odds Source Ingestion

Phase 17 adds an odds extraction pipeline that reuses the existing source registry and raw snapshot cache.

Register permitted odds sources as `SourceType.ODDS_REFERENCE`. The refresh step stores raw payloads in `raw_snapshots`; the odds extraction step parses those snapshots into `odds_snapshots` and recalculates value metrics on existing `prediction_selections`.

### Supported Odds CSV Contract

Default CSV headers:

```csv
MatchId,LeagueCode,MatchDate,HomeTeam,AwayTeam,MarketCode,BookmakerCode,BookmakerName,DecimalOdds,CapturedAt,SourceName,SourceUrl,RawPayloadReference
```

`MatchId` is preferred. If `MatchId` is blank, the extractor resolves the match with:

- `LeagueCode`
- `MatchDate`
- `HomeTeam`
- `AwayTeam`

`MarketCode` must match one of the configured market enums, such as `HOME_WIN` or `OVER_2_5_GOALS`.

Column names can be overridden with `selectorsJson`:

```json
{
  "format": "generic-odds-csv",
  "columns": {
    "matchId": "match_id",
    "marketCode": "market",
    "bookmakerCode": "book",
    "decimalOdds": "odds"
  }
}
```

### Register An Odds Source

```bash
curl -X POST http://localhost:8080/api/v1/admin/sources \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCode": "PREMIER_LEAGUE",
    "sourceType": "ODDS_REFERENCE",
    "name": "Permitted Odds CSV",
    "urlTemplate": "https://permitted-source.example/odds.csv",
    "renderMode": "STATIC_HTML",
    "active": true,
    "robotsTxtRequired": true,
    "rateLimitPerMinute": 6,
    "timeoutMs": 15000,
    "reliabilityScore": 75.00,
    "selectorsJson": {"format":"generic-odds-csv"}
  }'
```

### Run Odds Extraction

```bash
curl -X POST http://localhost:8080/api/v1/admin/odds/extraction/daily \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE"],
    "snapshotDate": "2026-06-07",
    "forceReprocess": false,
    "recalculateExistingSelections": true
  }'
```

## Phase 18: Full Pipeline Orchestration

The full pipeline endpoint runs the daily workflow in one admin request:

```text
refresh -> extraction -> odds extraction -> features -> predictions -> settlement -> model quality
```

```bash
curl -X POST http://localhost:8080/api/v1/admin/pipeline/run \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA", "SERIE_A", "ALLSVENSKAN", "ELITESERIEN"],
    "pipelineDate": "2026-06-07",
    "fixtureDateFrom": "2026-06-07",
    "fixtureDateTo": "2026-06-21",
    "settlementMatchDateFrom": "2026-06-04",
    "settlementMatchDateTo": "2026-06-06",
    "predictionMatchStatuses": ["SCHEDULED"],
    "modelVersion": "phase5-deterministic-v1"
  }'
```

Each step records `SUCCESS` or `FAILED`. The pipeline persists `SUCCESS`, `PARTIAL_SUCCESS`, or `FAILED` in `pipeline_runs`.

Scheduled automation also now supports:

- `BETAI_AUTOMATION_RUN_ODDS_EXTRACTION`
- `BETAI_AUTOMATION_RUN_MODEL_QUALITY`

## Phase 19: Backtesting And Model Tuning Summary

Backtesting evaluates settled historical predictions and returns:

- observed win rate
- average predicted probability
- Brier score
- calibration error
- average expected value where odds exist
- realized ROI where odds exist
- per-league/market tuning recommendation

```bash
curl -X POST http://localhost:8080/api/v1/admin/backtesting/run \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA", "SERIE_A"],
    "modelVersion": "phase5-deterministic-v1",
    "backtestDate": "2026-06-07",
    "matchDateFrom": "2025-08-01",
    "matchDateTo": "2026-06-07",
    "minimumSampleSize": 30
  }'
```

Tuning recommendations are advisory:

- `INCREASE_PROBABILITY`
- `DECREASE_PROBABILITY`
- `HOLD`
- `INSUFFICIENT_SAMPLE`

The app does not automatically mutate model weights from backtest output. That remains an explicit future tuning step.
