# Phase 15 Model Quality And Probability Calibration Walkthrough

Phase 15 adds a model-quality layer between raw prediction probabilities and public output. The app now tracks settled model performance by league/market/model, creates calibration snapshots, and stores both raw and calibrated probabilities on generated selections.

This phase does not claim predictions are guaranteed. It makes the uncertainty more explicit.

## Added Components

Database:

- `V9__model_quality_calibration.sql`
- `model_quality_snapshots`
- `prediction_selections.raw_probability`
- `prediction_selections.confidence_band`
- `prediction_selections.model_quality_snapshot_id`
- `prediction_selections.calibration_note`

Domain:

- `PredictionConfidenceBand`
- `ModelQualitySnapshot`

Repositories:

- `ModelQualitySnapshotRepository`
- new settled-selection quality query in `PredictionSelectionRepository`

Services:

- `ModelQualityService`
- `ModelQualityServiceImpl`
- `ProbabilityCalibrationService`
- `ProbabilityCalibrationServiceImpl`

Admin API:

- `AdminModelQualityController`

UI/export:

- Public prediction table now shows confidence.
- Prediction Excel export now includes raw probability, adjustment, confidence, model-quality sample, calibration error, and calibration note.

## Calibration Flow

1. Settlement marks historical selections as `WON`, `LOST`, or `VOID`.
2. Model-quality generation groups settled selections by league, market, and model version.
3. The service computes:
   - sample size
   - observed win rate
   - average raw probability
   - Brier score
   - calibration error
   - probability adjustment
   - confidence band
4. Future prediction generation calls the calibration service per selection.
5. Each generated selection stores:
   - `rawProbability`
   - calibrated `probability`
   - `confidenceBand`
   - linked `modelQualitySnapshot`
   - `calibrationNote`

Small samples are marked `UNRATED`, and the raw probability is preserved without adjustment.

## Generate Model Quality

Example for Premier League:

```bash
curl -X POST http://localhost:8080/api/v1/admin/model-quality/generate \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE"],
    "modelVersion": "phase5-deterministic-v1",
    "qualityDate": "2026-06-07",
    "minimumSampleSize": 30
  }'
```

Generate for all leagues with available settled data:

```bash
curl -X POST http://localhost:8080/api/v1/admin/model-quality/generate \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA", "SERIE_A", "ALLSVENSKAN", "ELITESERIEN"],
    "modelVersion": "phase5-deterministic-v1",
    "qualityDate": "2026-06-07",
    "minimumSampleSize": 30
  }'
```

Leagues without settled predictions return warnings and no quality rows.

## Read Model Quality

```bash
curl -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  "http://localhost:8080/api/v1/admin/model-quality?leagueCode=PREMIER_LEAGUE&modelVersion=phase5-deterministic-v1&qualityDate=2026-06-07"
```

## Confidence Bands

- `VERY_HIGH`: large sample, low calibration error, strong Brier score, high calibrated probability
- `HIGH`: good sample and acceptable calibration
- `MEDIUM`: usable but not elite calibration
- `LOW`: available quality data is weak, or the calibrated selection is not strong
- `UNRATED`: no adequate settled sample exists

The public form now adds warnings when candidate selections are `LOW` or `UNRATED`.

## Important Operational Order

Normal daily sequence after this phase:

1. Refresh sources.
2. Extract normalized data.
3. Generate features.
4. Generate predictions.
5. Settle completed predictions.
6. Generate model-quality snapshots.
7. Generate the next pending slate using calibrated probabilities.

If model-quality snapshots do not exist yet, prediction generation still works but stores `UNRATED` confidence and unadjusted probabilities.

## Verification

Verified locally:

- unit tests passed
- package build passed
- Flyway migration V9 applied
- model-quality generation returned 10 Premier League market snapshots
- calibrated regeneration produced stored selections with raw/calibrated probabilities and confidence bands
