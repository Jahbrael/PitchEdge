# Phase 6 Public Form Flow Walkthrough

Phase 6 completes the read-side form workflow. The form does not scrape, extract, engineer features, or generate probabilities. It reads persisted `prediction_selections`, filters them by the submitted form fields, builds complete batches, and returns individual selection probabilities plus full-batch accumulator risk.

## Public Form Contract

The public form remains the original six-field contract:

- `leagueCodes`
- `marketCodes`
- `fixtureDateFrom`
- `fixtureDateTo`
- `batchCount`
- `selectionsPerBatch`

Endpoint:

```bash
curl -X POST http://localhost:8080/api/v1/predictions/form \
  -H "Content-Type: application/json" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA"],
    "marketCodes": ["HOME_WIN", "DRAW", "AWAY_WIN", "OVER_1_5_GOALS", "OVER_2_5_GOALS", "UNDER_3_5_GOALS", "BTTS_YES", "YELLOW_CARDS_OVER", "RED_CARD_YES", "CORNERS_OVER"],
    "fixtureDateFrom": "2025-08-15",
    "fixtureDateTo": "2025-08-21",
    "batchCount": 3,
    "selectionsPerBatch": 5
  }'
```

## Data Flow

1. `PredictionController` receives the validated form request.
2. `PredictionFormServiceImpl` validates date range, league codes, market codes, batch count, and selections per batch.
3. The service resolves the configured model version from `bet-ai.prediction.default-model-version`.
4. The service resolves allowed form match statuses from `bet-ai.prediction.form-match-statuses`.
5. Matching fixtures are counted for response metadata.
6. Matching `prediction_selections` are loaded by league, market, date, match status, outcome, and model version.
7. `BatchBuilder` sorts selections by probability and builds complete batches only.
8. Conflict handling prevents multiple selections from the same match or same correlation group inside one batch.
9. Each batch returns individual probabilities and a joint accumulator probability.
10. Warnings explain stale data, missing generation coverage, historical/backtest windows, and incomplete batch fulfillment.

## Configuration

Local backtest mode uses both finished and scheduled rows:

```yaml
bet-ai:
  prediction:
    form-match-statuses: SCHEDULED,FINISHED
```

Production betting mode should restrict the form to upcoming fixtures:

```bash
export BETAI_FORM_MATCH_STATUSES=SCHEDULED
```

Phase 7 is still required to ingest real upcoming fixtures and generate `SCHEDULED` predictions.

## Batch Rules

The batch builder:

- Returns only complete batches.
- Does not put two selections from the same match into the same batch.
- Does not put two selections from the same correlation group into the same batch.
- Uses stored `correlationGroupKey` values from Phase 5.
- Avoids reusing the same selection across different batches.

Current correlation groups:

- Result markets: `HOME_WIN`, `DRAW`, `AWAY_WIN`
- Goals markets: `OVER_1_5_GOALS`, `OVER_2_5_GOALS`, `UNDER_3_5_GOALS`
- BTTS market: `BTTS_YES`
- Discipline markets: `YELLOW_CARDS_OVER`, `RED_CARD_YES`
- Corners market: `CORNERS_OVER`

## Risk Math

Individual selection probability and full-batch probability are separate values.

For a five-selection batch:

```text
P(batch) = P(selection_1) * P(selection_2) * P(selection_3) * P(selection_4) * P(selection_5)
```

The response exposes:

- `jointProbability`
- `averageIndividualProbability`
- `minimumIndividualProbability`
- `maximumIndividualProbability`
- `riskBand`
- `varianceWarning`

This preserves the accumulator decay rule. High individual selection probabilities can still produce a much lower full-batch probability.

## Verified Local Output

Using the Phase 5 generated predictions for `2025/2026` backtest rows:

- Request: `2` leagues, `10` markets, `2025-08-15` through `2025-08-21`.
- Fixtures considered: `20`.
- Candidate selections: `200`.
- Requested batches: `3`.
- Selections per batch: `5`.
- Returned batches: `3`.
- Returned selections: `15`.

Example risk outputs:

- Batch 1: average individual probability `0.835133`, joint probability `0.403941`, risk band `MODERATE`.
- Batch 2: average individual probability `0.778468`, joint probability `0.285102`, risk band `MODERATE`.
- Batch 3: average individual probability `0.752067`, joint probability `0.240589`, risk band `HIGH`.

The response included historical/backtest warnings because the request used finished historical fixtures.

## Current Limits

- The form can only return rows that already exist in `prediction_selections`.
- No scraping or model generation happens on form submission.
- Real upcoming fixture ingestion is implemented in Phase 7, but it depends on public sources publishing actual scheduled rows.
- Auth/rate limiting for the public endpoint remains a production-hardening phase.

## Phase 7 Continuation

Upcoming fixture source integration is documented in [phase-7-upcoming-fixture-source-integration-walkthrough.md](</home/dell/IdeaProjects/Bet AI/docs/phase-7-upcoming-fixture-source-integration-walkthrough.md>).
