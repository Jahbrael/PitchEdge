# Phase 8 Settlement And Accuracy Tracking Walkthrough

Phase 8 settles predictions after matches finish. It does not generate predictions. It reads finished matches, evaluates stored `prediction_selections` against final scores and match statistics, updates each selection outcome, and writes market-level model accuracy aggregates.

## Added Database Objects

Migration:

- `V8__settlement_and_accuracy_tracking.sql`

Tables:

- `settlement_runs`: auditable settlement execution logs per league, model, date range, and settlement date.
- `model_accuracy_daily`: model accuracy aggregates by league, market, model version, and accuracy date.

`prediction_selections.outcome` is updated from `PENDING` to `WON`, `LOST`, or `VOID`.

## Added Java Components

- `AdminSettlementController`: admin endpoints for settlement and accuracy reporting.
- `SettlementService`: settlement service boundary.
- `SettlementServiceImpl`: deterministic settlement engine and accuracy aggregator.
- `SettlementRun`, `SettlementStatus`, `SettlementCounters`: settlement run domain model.
- `ModelAccuracyDaily`: persisted model accuracy aggregate.
- `SettlementRunRepository`
- `ModelAccuracyDailyRepository`
- Settlement and accuracy DTOs.

## Settlement Rules

Score-based markets:

- `HOME_WIN`: won when home score is greater than away score.
- `DRAW`: won when home score equals away score.
- `AWAY_WIN`: won when away score is greater than home score.
- `OVER_1_5_GOALS`: won when total goals are greater than `1.5`.
- `OVER_2_5_GOALS`: won when total goals are greater than `2.5`.
- `UNDER_3_5_GOALS`: won when total goals are lower than `3.5`.
- `BTTS_YES`: won when both teams score at least one goal.

Statistics-based markets:

- `YELLOW_CARDS_OVER`: won when total yellow cards are greater than `3.5`.
- `RED_CARD_YES`: won when total red cards are greater than `0.5`.
- `CORNERS_OVER`: won when total corners are greater than `8.5`.

If required score or statistic fields are missing, the selection is marked `VOID`. The system does not infer missing cards, corners, or scores.

## Run Settlement

```bash
curl -X POST http://localhost:8080/api/v1/admin/settlement/run \
  -H "Content-Type: application/json" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA", "SERIE_A"],
    "settlementDate": "2026-06-06",
    "matchDateFrom": "2025-08-15",
    "matchDateTo": "2026-05-25",
    "modelVersion": "phase5-deterministic-v1",
    "forceResettle": false
  }'
```

Use `forceResettle: true` only when you intentionally want to recalculate already-settled predictions after correcting match data.

## Read Accuracy

```bash
curl "http://localhost:8080/api/v1/admin/settlement/accuracy?leagueCode=PREMIER_LEAGUE&modelVersion=phase5-deterministic-v1&accuracyDate=2026-06-06"
```

Each row returns:

- `settledSelections`
- `wonCount`
- `lostCount`
- `voidCount`
- `winRate`
- `averageProbability`
- `brierScore`
- `calibrationError`

`winRate` excludes void selections. `brierScore` is calculated from won/lost settled selections only.

## Verified Local Output

Settlement was run against the Phase 5 historical prediction rows:

- `11,400` selections evaluated.
- `5,519` selections won.
- `5,881` selections lost.
- `0` selections void.
- `30` model accuracy rows written: `3` leagues times `10` markets.

Per league:

- Premier League: `3,800` evaluated, `1,870` won, `1,930` lost.
- La Liga: `3,800` evaluated, `1,914` won, `1,886` lost.
- Serie A: `3,800` evaluated, `1,735` won, `2,065` lost.

Example Premier League accuracy rows:

- `BTTS_YES`: win rate `0.560526`, average probability `0.551601`, calibration error `0.008925`.
- `CORNERS_OVER`: win rate `0.660526`, average probability `0.665954`, calibration error `0.005428`.
- `OVER_1_5_GOALS`: win rate `0.789474`, average probability `0.758236`, calibration error `0.031238`.

## Current Limits

- There is no scheduled cron yet; settlement is exposed as an admin-triggered endpoint.
- Accuracy aggregates are daily snapshots, not a full model registry UI.
- Settled historical predictions are no longer returned by the public form because the public form reads `PENDING` selections for actionable prediction batches.
- More advanced calibration, backtesting dashboards, and model comparison belong in later model-improvement phases.

## Phase 9 Continuation

Excel exports for prediction and accuracy outputs are documented in [phase-9-excel-export-walkthrough.md](</home/dell/IdeaProjects/Bet AI/docs/phase-9-excel-export-walkthrough.md>).
