# Phases 24-28: Final Production Pass

## Project Rule

Production operation must not rely on manual imports.

The normal path is now:

```text
scheduled automation
-> fixture discovery and source refresh
-> extraction
-> odds extraction
-> feature generation
-> prediction generation
-> settlement
-> model quality
-> backtesting and tuning profiles
-> dashboard visibility
```

Admin endpoints remain available for debugging and emergency repair.

## Phase 24: Source Fallback and Resilience

`source_targets` now stores operational failover metadata:

- `fallback_priority`
- `system_disabled`
- `quarantined_until`
- `health_note`

The refresh layer orders targets by:

```text
fallback_priority asc
consecutive_failures asc
reliability_score desc
name asc
```

Failed sources lose reliability. After repeated failures, a source is temporarily quarantined for six hours. If every active source is quarantined, the scraper still tries them in priority order so a league is not permanently starved by stale quarantine state.

## Phase 25: Scheduler Hardening

Scheduled automation now persists `automation_runs`.

Each run records:

- date
- trigger type
- league set
- model version
- final status
- total attempts
- warning count
- step JSON
- failure reason

Each scheduled step has retry handling through:

- `BETAI_AUTOMATION_MAX_STEP_ATTEMPTS`
- `BETAI_AUTOMATION_RETRY_BACKOFF_MS`

## Phase 26: Admin UX Completion

The dashboard now exposes:

- automation run totals
- failed automation count
- recent automation runs
- source fallback priority
- source quarantine state
- degraded source alerts

## Phase 27: Model Improvement Layer

The raw probability engine now uses expected scoreline modeling:

- independent Poisson scoreline matrix from expected home/away goals
- Dixon-Coles low-score adjustment
- scoreline-derived 1X2, over goals, under goals, and BTTS probabilities
- blended 1X2 probability using both scoreline and form/strength logits

Calibration, model quality, odds value, and tuning profiles still run after the raw model.

Default model version:

```text
phase27-dixon-coles-v2
```

Old model data remains queryable by explicitly requesting the old model version.

## Phase 28: Final Readiness

Production readiness now includes:

- Docker health checks
- non-root application container
- externalized secrets
- automated source registration
- automated fixture discovery
- automated odds ingestion
- scheduler retries
- persisted automation history
- dashboard alerts
- Flyway-managed schema
- focused regression tests for probability, odds value, calibration, batch building, and fixture discovery

## Operational Startup

Start Postgres and the app:

```bash
docker compose up -d --build
```

Check health:

```bash
curl http://localhost:8080/actuator/health
```

Open:

```text
http://localhost:8080/predictions.html
http://localhost:8080/admin/dashboard.html
```

## Important Limits

The app can automate scraping and ingestion only when a permitted source has published data.

It will not fabricate fixtures, odds, or stats when the football season has no published fixtures. In that case the system records warnings and waits for the next scheduled run.
