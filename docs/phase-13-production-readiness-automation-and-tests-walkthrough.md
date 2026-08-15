# Phase 13 Production Readiness, Automation, And Tests Walkthrough

Phase 13 hardens the MVP operating model without expanding product scope. It adds repeatable container startup, disabled-by-default scheduled pipeline automation, and focused tests for the most important deterministic betting-risk logic.

## Added Production-Readiness Files

- `Dockerfile`: multi-stage Java 21 build and slim runtime image.
- `docker-compose.yml`: app plus PostgreSQL 16.
- `.dockerignore`: excludes build output and local IDE files from Docker build context.
- `.env.example`: environment template for local Compose or deployment.

Compose defaults:

- app host port: `8080`
- Postgres host port: `55432`
- internal Postgres service port: `5432`

Postgres uses host port `55432` by default so it does not collide with the manual Postgres container already running on `localhost:5432`.

## Docker Commands

On Kali rolling, install Compose with:

```bash
sudo apt update
sudo apt install -y docker-compose
```

Kali currently packages Compose v2 under the `docker-compose` package name. The installed package provides both `docker compose` and `docker-compose` commands.

On other distributions, install the package that provides Docker Compose v2 for that distribution and confirm it with `docker compose version` or `docker-compose version`.

Start the stack:

```bash
cd "/home/dell/IdeaProjects/Bet AI"
test -f .env || cp .env.example .env
docker compose up --build
```

If `docker compose` is unavailable but `docker-compose` exists:

```bash
docker-compose up --build
```

Local verification note: this machine has Docker installed, and the installed Kali `docker-compose` package is version `2.40.3-3`.

## Added Scheduled Automation

Added Java components:

- `AutomationProperties`
- `DailyPipelineScheduler`

Updated:

- `BetAiApplication`: enables Spring scheduling.
- `application.yml`: adds `bet-ai.automation.*` settings.

The scheduler is disabled by default:

```yaml
bet-ai:
  automation:
    enabled: false
```

Enable it with:

```bash
export BETAI_AUTOMATION_ENABLED=true
```

Default cron:

```text
0 15 6 * * *
```

That means `06:15` every day in the configured automation timezone.

Default timezone:

```bash
export BETAI_AUTOMATION_ZONE=UTC
```

## Automated Pipeline Order

When enabled, the daily scheduler runs:

1. daily refresh
2. extraction
3. feature engineering
4. prediction generation
5. settlement

It calls the same service layer used by the existing admin endpoints. It does not duplicate business logic.

The scheduler has an in-process overlap guard. If a previous scheduled run is still active, the next run is skipped and logged.

## Automation Environment Variables

Core:

```bash
export BETAI_AUTOMATION_ENABLED=false
export BETAI_AUTOMATION_DAILY_CRON="0 15 6 * * *"
export BETAI_AUTOMATION_ZONE=UTC
export BETAI_AUTOMATION_LEAGUE_CODES=
```

Leave `BETAI_AUTOMATION_LEAGUE_CODES` empty to process every active league with imports enabled. Set an explicit comma-separated list only when an intentionally scoped run is required.

Prediction and settlement:

```bash
export BETAI_AUTOMATION_PREDICTION_MATCH_STATUSES=SCHEDULED
export BETAI_AUTOMATION_PREDICTION_WINDOW_DAYS=14
export BETAI_AUTOMATION_SETTLEMENT_LOOKBACK_DAYS=3
```

Step toggles:

```bash
export BETAI_AUTOMATION_RUN_REFRESH=true
export BETAI_AUTOMATION_RUN_EXTRACTION=true
export BETAI_AUTOMATION_RUN_FEATURES=true
export BETAI_AUTOMATION_RUN_PREDICTIONS=true
export BETAI_AUTOMATION_RUN_SETTLEMENT=true
```

Force flags, normally `false`:

```bash
export BETAI_AUTOMATION_FORCE_REFRESH=false
export BETAI_AUTOMATION_FORCE_REPROCESS=false
export BETAI_AUTOMATION_FORCE_REGENERATE_FEATURES=false
export BETAI_AUTOMATION_FORCE_REGENERATE_PREDICTIONS=false
export BETAI_AUTOMATION_FORCE_RESETTLE=false
```

## Added Tests

Added dependency:

```xml
spring-boot-starter-test
```

Added test:

- `BatchBuilderTest`

Covered behavior:

- full accumulator probability is the product of individual probabilities
- 20 selections at `80%` each produce `0.011529`, about `1.15%`
- batch risk band is `EXTREME` for that accumulator
- same-match selections are not grouped into the same batch
- incomplete batches are not returned

Run:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw test
```

Verified result:

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

## Verified Local Output

Verified:

- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw test`
- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw -q package`
- `docker compose config`
- `docker-compose config`
- `sudo docker-compose up --build`

All passed.

The Compose runtime startup built `betai-app`, created the Postgres volume and network, initialized PostgreSQL 16, applied all 8 Flyway migrations, and started Spring Boot on port `8080`. The stack then stopped cleanly after manual shutdown.

## Recommended Production Defaults

Before any deployment:

```bash
export BETAI_ADMIN_API_KEY='replace-with-a-long-random-secret'
export BETAI_FORM_MATCH_STATUSES=SCHEDULED
export BETAI_AUTOMATION_ENABLED=false
```

Only enable automation after source targets and future fixture ingestion are correct:

```bash
export BETAI_AUTOMATION_ENABLED=true
```

## Current Limits

- Scheduler overlap protection is in-memory. Multi-instance deployment needs a database lock or distributed scheduler.
- Docker Compose is local deployment scaffolding, not Kubernetes or cloud infrastructure.
- Tests are focused unit tests, not a full integration suite yet.
- Endpoint smoke checks were not captured while the Compose stack was running.

## Phase 14 Continuation

Upcoming fixture import and pending slate generation are documented in [phase-14-upcoming-fixture-import-and-pending-slate-walkthrough.md](</home/dell/IdeaProjects/Bet AI/docs/phase-14-upcoming-fixture-import-and-pending-slate-walkthrough.md>).
