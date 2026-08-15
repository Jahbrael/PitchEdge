# Phases 20-23: Automated Odds, Tuning, UI Polish, Production Hardening

## Project Constraint

Production operation must not require manual imports.

Manual admin endpoints remain useful for debugging, but the normal path is:

```text
daily scheduler/full pipeline
-> source refresh
-> fixture/result extraction
-> odds extraction
-> feature generation
-> prediction generation
-> settlement
-> model quality snapshots
-> backtesting
-> tuning profiles
```

## Phase 20: Automatic Odds Connector

Football-Data fixture odds sources are registered automatically during startup:

- `https://www.football-data.co.uk/fixtures.csv`
- `https://www.football-data.co.uk/new_league_fixtures.csv`

The odds connector parses Football-Data wide odds columns directly:

- 1X2: `B365H/B365D/B365A`, `PSH/PSD/PSA`, `MaxH/MaxD/MaxA`, `AvgH/AvgD/AvgA`, `BFEH/BFED/BFEA`
- Over 2.5: `B365>2.5`, `P>2.5`, `Max>2.5`, `Avg>2.5`, `BFE>2.5`

Those rows become `odds_snapshots`, then the app recalculates:

- implied probability
- value edge
- expected value
- value rating

No manual odds import is needed once the daily pipeline is running.

## Phase 21: Automatic Model Tuning Profiles

Backtests now create `model_tuning_profiles`.

Rules:

- recommendations are sample-gated
- applied adjustment is half-strength
- applied adjustment is capped at `+/-0.050000`
- predictions apply the latest active tuning profile for league, market, model, and date

Prediction generation now records:

- `model_tuning_profile_id`
- `tuning_adjustment`
- `tuning_note`

## Phase 22: UI Polish

The public prediction form now supports value mode:

- `ALL`
- `POSITIVE_VALUE_ONLY`
- `STRONG_VALUE_ONLY`

Selection tables show:

- tuned probability
- tuning adjustment
- odds
- expected value
- value rating
- confidence band

## Phase 23: Production Hardening

Docker Compose now includes app health checks and automation flags:

- `BETAI_AUTOMATION_RUN_REFRESH`
- `BETAI_AUTOMATION_RUN_EXTRACTION`
- `BETAI_AUTOMATION_RUN_ODDS_EXTRACTION`
- `BETAI_AUTOMATION_RUN_FIXTURE_DISCOVERY`
- `BETAI_AUTOMATION_FIXTURE_DISCOVERY_AUTO_REGISTER_FOOTBALL_DATA_SOURCES`
- `BETAI_AUTOMATION_FIXTURE_DISCOVERY_GENERATE_PENDING_SLATE`
- `BETAI_AUTOMATION_RUN_FEATURES`
- `BETAI_AUTOMATION_RUN_PREDICTIONS`
- `BETAI_AUTOMATION_RUN_SETTLEMENT`
- `BETAI_AUTOMATION_RUN_MODEL_QUALITY`

Recommended production startup:

```bash
cp .env.example .env
```

Set strong secrets in `.env`, then:

```bash
docker compose up -d --build
```

Check health:

```bash
curl http://localhost:8080/actuator/health
```

Check dashboard:

```bash
curl -H "X-BETAI-ADMIN-KEY: your-admin-key" \
  http://localhost:8080/api/v1/admin/dashboard/overview
```

## Manual Full Pipeline Trigger

This is for debugging or immediate runs. The scheduler is the normal production path.

```bash
curl -X POST http://localhost:8080/api/v1/admin/pipeline/run \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA", "SERIE_A", "ALLSVENSKAN", "ELITESERIEN"],
    "pipelineDate": "2026-06-07",
    "fixtureDateFrom": "2026-06-07",
    "fixtureDateTo": "2026-06-21",
    "predictionMatchStatuses": ["SCHEDULED"],
    "modelVersion": "phase5-deterministic-v1"
  }'
```
