# Phase 5 Prediction Generation Walkthrough

Phase 5 converts `league_baselines` and `team_feature_snapshots` into persisted `prediction_selections`. The generator is deterministic: it reads normalized feature snapshots, scores the ten MVP markets, stores the probability inputs used for audit, and records an idempotent generation run per league/date/model/status set.

## Added Database Objects

- `prediction_generation_runs`: auditable prediction generation attempts by league, model version, calculation date, fixture date range, and match status set.
- `V6__prediction_generation_runs.sql`: Flyway migration for run metadata and idempotency lookups.

`prediction_selections` already existed from Phase 1 and is now populated by Phase 5.

## Added Java Components

- `AdminPredictionGenerationController`: admin endpoint for probability generation.
- `PredictionGenerationService`: generation application boundary.
- `PredictionGenerationServiceImpl`: orchestrates feature lookup, fixture lookup, idempotency, scoring, and persistence.
- `MarketProbabilityEngine`: deterministic market scorer over Phase 4 feature snapshots.
- `PredictionGenerationRun` and `PredictionGenerationStatus`: run tracking domain model.
- `PredictionGenerationRunRepository`: idempotency lookup for successful generation runs.

## Probability Model

The Phase 5 scorer is intentionally transparent and conservative. It is not a paid feed, bookmaker odds model, or guarantee engine.

For each match, the engine builds an expected profile:

- Expected home goals.
- Expected away goals.
- Expected total goals.
- Expected total corners.
- Expected total yellow cards.

Market probabilities are then calculated as follows:

- `HOME_WIN`, `DRAW`, `AWAY_WIN`: softmax over league result baselines, team form difference, goal-strength difference, and expected goal difference.
- `OVER_1_5_GOALS`, `OVER_2_5_GOALS`, `UNDER_3_5_GOALS`: Poisson probabilities from expected total goals.
- `BTTS_YES`: blend of Poisson both-team-scoring probability, team BTTS rates, and league BTTS baseline.
- `YELLOW_CARDS_OVER`: Poisson over 3.5 cards from expected yellow cards.
- `RED_CARD_YES`: blend of league red-card rate and both team red-card rates.
- `CORNERS_OVER`: Poisson over 8.5 corners from expected corners.

All probabilities are clamped to `0.020000` through `0.980000` and stored as decimals from `0.000000` to `1.000000`.

## Individual Probability Versus Batch Risk

Each `prediction_selections.probability` is an individual market probability for one fixture. A future batch or accumulator must multiply the selected independent probabilities to calculate the full-batch probability:

```text
P(batch) = product(P(selection_i))
```

Example:

```text
20 selections at 0.80 each = 0.80^20 = 0.0115 = 1.15%
```

That means a batch can contain high-confidence individual selections while still having low combined hit probability. Phase 5 stores the individual probabilities; the existing batch response layer computes and exposes full-batch risk metrics when form submissions read generated selections.

## Endpoint

Generate predictions:

```bash
curl -X POST http://localhost:8080/api/v1/admin/predictions/generate \
  -H "Content-Type: application/json" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA", "SERIE_A"],
    "calculationDate": "2026-06-06",
    "fixtureDateFrom": "2025-08-15",
    "fixtureDateTo": "2026-05-25",
    "matchStatuses": ["FINISHED"],
    "modelVersion": "phase5-deterministic-v1",
    "forceRegenerate": true
  }'
```

For real upcoming fixtures, use `matchStatuses: ["SCHEDULED"]`. The verified local command above uses `FINISHED` because the current imported Football-Data rows are historical/backtest season rows.

Run the same command with `forceRegenerate: false` to reuse successful generation runs for the same league/date/status/model tuple.

## Verified Local Output

Using the normalized Football-Data `2025/2026` rows and Phase 4 features for `2026-06-06`, Phase 5 generated:

- `11,400` prediction selections.
- `3,800` selections per league.
- `1,140` selections per MVP market.
- `3` successful prediction generation runs.

Per league:

- Premier League: `380` matches evaluated, `3,800` selections generated.
- La Liga: `380` matches evaluated, `3,800` selections generated.
- Serie A: `380` matches evaluated, `3,800` selections generated.

Per market:

- `HOME_WIN`: `1,140`
- `DRAW`: `1,140`
- `AWAY_WIN`: `1,140`
- `OVER_1_5_GOALS`: `1,140`
- `OVER_2_5_GOALS`: `1,140`
- `UNDER_3_5_GOALS`: `1,140`
- `BTTS_YES`: `1,140`
- `YELLOW_CARDS_OVER`: `1,140`
- `RED_CARD_YES`: `1,140`
- `CORNERS_OVER`: `1,140`

The generation endpoint returned cached run metadata on a repeated request with `forceRegenerate: false`.

## Verification SQL

```sql
select count(*) as prediction_selections
from prediction_selections;

select l.code, count(ps.id) as selections
from prediction_selections ps
join matches m on m.id = ps.match_id
join leagues l on l.id = m.league_id
group by l.code
order by l.code;

select md.code, count(ps.id) as selections
from prediction_selections ps
join market_definitions md on md.id = ps.market_definition_id
group by md.code
order by md.code;
```

## Current Limits

- The model is deterministic and heuristic, not calibrated against settled prediction history yet.
- xG is not included because the current source does not provide xG.
- The public form status filter is configurable. The local default includes `FINISHED` for development/backtesting; production should set it to `SCHEDULED`.
- Settlement and model accuracy tracking remain future phases.

## Phase 6 Continuation

The public form flow that consumes generated `prediction_selections` is documented in [phase-6-public-form-flow-walkthrough.md](</home/dell/IdeaProjects/Bet AI/docs/phase-6-public-form-flow-walkthrough.md>).
