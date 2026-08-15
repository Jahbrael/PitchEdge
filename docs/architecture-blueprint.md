# Bet AI Architecture Blueprint

This document defines a form-based football prediction platform for structured sports betting analysis. It is not a chatbot system. The user submits deterministic form inputs, the backend reads cached daily football data, computes market probabilities, builds batches, and returns structured JSON and optional Excel exports.

The MVP uses Java 21, Spring Boot 3.x, PostgreSQL, scheduled scraping, raw-source retention, strict validation, and probabilistic reporting. Spring Boot `3.5.13` was selected from Spring's official March 26, 2026 release note for the current 3.x line: https://spring.io/blog/2026/03/26/spring-boot-3-5-13-available-now.

## 1. Full Product Explanation

End-to-end pipeline:

1. Admin configures supported leagues, source URLs, selectors, scrape cadence, and reliability weights.
2. Daily scheduler runs per league and calendar date. It checks `data_refresh_logs` before scraping.
3. If a successful refresh already exists for the league/date, the scheduler reuses cached data.
4. If data is missing or explicitly force-refreshed, the scraper fetches source pages with HTTP or browser automation depending on page rendering.
5. Raw HTML, extracted text, metadata, checksums, HTTP status, and source timing are stored before parsing.
6. AI extraction receives only raw source text plus a strict schema and returns normalized JSON. It is forbidden to infer missing statistics.
7. Deterministic validators reject malformed, impossible, or low-confidence extraction output.
8. Cleaned stats are normalized into canonical league, team, fixture, result, event, and aggregate stat tables.
9. Feature engineering jobs compute rolling form metrics, xG aggregates, cards, corners, BTTS rates, and league baselines.
10. Prediction services score configured markets for eligible fixtures.
11. Form submission queries existing cached predictions; it does not scrape.
12. Batch builder groups selections according to user constraints, removes conflicts, computes accumulator probability, and returns batch-level risk.
13. Excel export streams the same structured response into tabs for batches, selections, fixtures, and warnings.
14. Post-match settlement jobs verify actual results and write historical accuracy metrics.

The core invariant is that scraping and AI extraction are ingestion-time activities. User prediction requests are read-side operations against cached validated data.

## 2. Recommended MVP Version

MVP scope:

- Sport: football only.
- Leagues: Premier League, La Liga, Serie A.
- Markets:
  - Home Win
  - Draw
  - Away Win
  - Over 1.5 Goals
  - Over 2.5 Goals
  - Under 3.5 Goals
  - Both Teams To Score
  - Yellow Cards Over 3.5 baseline
  - Red Card Yes
  - Corners Over 8.5 baseline
- Form fields:
  - `leagueCodes`
  - `marketCodes`
  - `fixtureDateFrom`
  - `fixtureDateTo`
  - `batchCount`
  - `selectionsPerBatch`

Excluded from MVP:

- Live betting.
- Paid sports feeds.
- User wallet, bookmaker integration, or bet placement.
- Real-money guarantees.
- Chat-based interaction.
- Markets beyond the ten listed above.

## 3. Full Java Spring Boot Architecture

Primary components:

- API layer: REST controllers accept validated form and admin refresh requests.
- Application service layer: orchestrates reads, validation, batch construction, and refresh lifecycle.
- Domain layer: entities and enums represent leagues, teams, matches, markets, predictions, and refresh logs.
- Repository layer: Spring Data JPA repositories expose constrained read/write access.
- Ingestion layer: daily scheduler, source manager, scraper, raw snapshot writer, AI extraction adapter, validator, normalizer.
- Prediction layer: feature readers, market scorers, calibration, batch builder, correlation filter.
- Export layer: Apache POI streaming workbook generation.
- Admin layer: scrape logs, source health, model versioning, manual overrides, settlement metrics.

Request flow:

`PredictionController -> PredictionFormService -> repositories -> BatchBuilder -> PredictionResponse`

Daily refresh flow:

`Scheduler/AdminRefreshController -> DailyRefreshService -> SourceManager -> Scraper -> RawSnapshotStore -> ExtractionService -> ValidationService -> Normalizer -> FeatureJob -> PredictionJob`

## 4. Backend Modules

Recommended modular boundaries:

- `api`: REST controllers, DTOs, exception mapping.
- `domain`: JPA entities, enums, domain invariants.
- `repository`: database access.
- `service`: orchestration services.
- `scraping`: HTTP/browser clients, rate limiting, robots handling.
- `source`: source URL registry, selectors, reliability scoring.
- `ingestion`: raw snapshot persistence, extraction queue, normalization.
- `extraction-ai`: LLM adapter, prompt templates, JSON schema validation.
- `features`: rolling metrics, league baselines, feature snapshots.
- `prediction`: market probability engines and calibration.
- `batching`: selection grouping, correlation filters, accumulator math.
- `export`: Excel and later CSV/parquet export.
- `admin`: operational dashboard APIs.

MVP Phase 1 uses a clean single-module Maven application with package-level separation. A later split into Maven modules is justified when scraping workers and API nodes are deployed independently.

## 5. Database Schema

Core table groups:

- Reference: `leagues`, `teams`, `market_definitions`, `source_targets`.
- Raw ingestion: `raw_snapshots`, `extraction_runs`, `data_refresh_logs`.
- Fixtures: `matches`, `match_results`, `match_events`.
- Stats: `team_match_stats`, `team_rolling_features`, `league_baselines`.
- Prediction: `prediction_selections`, `prediction_batches`, `prediction_batch_items`.
- Settlement: `settlement_runs`, `selection_outcomes`, `model_accuracy_daily`.

Indexing strategy:

- `matches(league_id, match_date, status)` for candidate lookup.
- `matches(home_team_id, away_team_id, kickoff_at)` for duplicate protection.
- `prediction_selections(match_id, market_definition_id, model_version)` unique.
- `prediction_selections(generated_at)` for cleanup and model audits.
- `data_refresh_logs(league_id, refresh_date)` plus partial unique success index.
- `raw_snapshots(source_target_id, snapshot_date, checksum)` for idempotent raw capture.
- Time-series stats tables partitioned by season or month once write volume grows.

## 6. Data Scraping Strategy

Scraping modes:

- Static HTML: Java HTTP client or Spring `RestClient`.
- JS-rendered pages: Playwright browser worker, with DOM-ready selectors and network-idle timeout.
- API-like JSON endpoints found in page network calls: direct HTTP fetch when allowed.

Operational controls:

- Per-domain token bucket rate limits.
- Randomized low-volume crawl windows.
- Conditional requests using ETag and Last-Modified where supported.
- User-agent identification and contact email for compliance.
- Proxy rotation only for resilience and IP preservation, never to bypass access controls.
- Backoff on 403, 429, CAPTCHA, login walls, or robots disallow.
- HTML snapshot checksum prevents reprocessing identical content.

## 7. Source Management System

`source_targets` stores:

- League.
- Source type: fixture, result, team stats, cards, corners, xG.
- URL template.
- CSS/XPath selectors.
- Rendering mode.
- Robots status.
- Reliability score.
- Last success/failure timestamps.
- Consecutive failure count.
- Parse confidence history.

Source reliability affects ingestion priority. It must not alter raw statistics. If two sources disagree, the system marks conflict state and requires deterministic source precedence or admin review.

## 8. Raw Data Storage

Raw source artifacts are stored before parsing:

- HTML/text payload in object storage or PostgreSQL large text table for MVP.
- Snapshot metadata: URL, headers subset, status code, fetched timestamp, checksum, source target, league, date.
- Rendered DOM snapshot for JS pages.
- Extracted visible text for LLM processing.

Retention:

- Keep raw snapshots at least one season for auditability.
- Deduplicate by checksum.
- Never overwrite a raw snapshot; create a new immutable row/reference.

## 9. AI-Assisted Extraction Flow

The LLM is a parser and normalizer only:

1. Build prompt with source name, extraction purpose, allowed fields, and JSON schema.
2. Include raw visible text or table text only.
3. Instruct the model to use `null` when data is absent.
4. Require source-location hints where possible.
5. Validate response with JSON schema.
6. Run deterministic sanity checks.
7. Persist accepted extraction output and validation report.

Forbidden AI behavior:

- Guessing missing scores, xG, cards, corners, or fixture dates.
- Inferring a team from context if the raw source is ambiguous.
- Filling league averages from memory.
- Repairing impossible values by approximation.

## 10. Data Cleaning and Normalisation

Normalization tables:

- `teams`: canonical identity.
- `team_aliases`: alternate spellings and source-specific names.
- `source_team_mappings`: exact source key to canonical team.

Examples:

- `Man Utd`, `Manchester Utd`, `Manchester United FC` normalize to `Manchester United`.
- `Inter`, `Internazionale`, `Inter Milan` normalize to `Inter Milan`.

Rules:

- First use exact source key mapping.
- Then use case-folded alias match.
- Then use similarity matching only as a candidate requiring validation.
- Store units explicitly for metrics.
- Convert percentages to decimal internally when used by models.
- Store display percentages separately only in API/export formatting.

## 11. Data Validation Rules

Deterministic checks:

- Played matches >= 0.
- Wins + draws + losses = played when all are present.
- Percent values are 0 to 100 at extraction boundary.
- Probability values are 0.000000 to 1.000000.
- Goals, cards, and corners cannot be negative.
- Fixture home and away teams must differ.
- Finished match must have non-null home and away scores.
- Scheduled match must not have final scores unless source marks it as postponed/abandoned with partial data.
- Kickoff date must belong to the declared season window.
- League source data must match the requested league.

Invalid records are rejected into an error table with source reference, reason, and payload pointer.

## 12. Feature Engineering

Computed metrics:

- Rolling xG for and against: last 5, last 10, home-only, away-only.
- Goals scored/conceded rolling averages.
- Clean sheet percentage.
- Failed-to-score percentage.
- BTTS percentage.
- Over 1.5/2.5 and Under 3.5 historical hit rates.
- Home and away split strength.
- Head-to-head ratios, capped to avoid overweighting old matches.
- Cards for/against and referee/league cards baseline.
- Corners for/against and match total corner baseline.
- Rest days and fixture congestion.
- Recent form weighted by recency.

Each feature row carries:

- `feature_date`.
- `source_cutoff_at`.
- `sample_size`.
- `quality_score`.
- `raw_snapshot_refs`.

## 13. Market Definition System

`market_definitions` is the market registry:

- Code.
- Display name.
- Type.
- Selection value.
- Threshold.
- Enabled flag.
- Minimum sample size.
- Settlement rule.
- Correlation group.

The current catalogue contains 100 markets: 89 enabled full-time markets and 11 staged half/event markets that remain disabled until their required data is reliable. The enabled catalogue spans match results, double chance, draw-no-bet, total and team goals, BTTS, clean sheets, total and team corners, yellow cards, and red cards. The API receives `marketCodes`, not hard-coded form booleans.

## 14. Market Candidate Generation

Candidate filtering:

1. Match league must be in request.
2. Match date must be within requested range.
3. Match status must be scheduled, postponed, or live depending on product policy; MVP uses scheduled only.
4. Market must be enabled.
5. Required source features must exist and pass minimum sample size.
6. The selected market must not be administratively blocked for the fixture.
7. Prediction must be generated for the active model version.

Candidates are read from precomputed `prediction_selections`. Form submission does not trigger scraping or model generation.

## 15. Probability Prediction Engine

MVP model design:

- Market-specific deterministic scorer using calibrated statistical features.
- Use logistic regression or gradient-boosted model after enough historical samples exist.
- Before enough data exists, use transparent weighted formulas with league priors and sample-size penalties.
- Output probability is always a decimal from `0.000000` to `1.000000`.
- Every prediction stores model version, generated timestamp, feature snapshot, and calibration metadata.

Example formula for goals:

`p_over_2_5 = calibrated_sigmoid(w0 + w1*home_xg_for + w2*away_xg_for + w3*home_xg_against + w4*away_xg_against + w5*league_over_2_5_baseline)`

Calibration:

- Backtest by league, season, market.
- Use Brier score, log loss, and reliability buckets.
- Penalize low sample size.
- Do not output unsupported markets when required features are missing.

## 16. Batch Builder Logic

Inputs:

- Candidate selections sorted by probability, calibration quality, and kickoff separation.
- User `batchCount`.
- User `selectionsPerBatch`.
- Correlation matrix and conflict rules.

Algorithm:

1. Sort candidates by descending adjusted probability.
2. Create batches one at a time.
3. Add highest-ranked selection that does not conflict with existing items.
4. Enforce at most one mutually exclusive result market per match.
5. Enforce max selections per team/time window if risk controls require it.
6. Compute batch probability as the product of individual probabilities.
7. Surface warnings when accumulator risk is extreme.

The builder returns fewer batches than requested if valid uncorrelated candidates do not exist.

## 17. Risk and Correlation Handling

Hard conflicts:

- Same match: `HOME_WIN`, `DRAW`, and `AWAY_WIN` cannot appear together.
- Same match: `OVER_2_5_GOALS` conflicts with very low total-goal under markets.
- Same match: duplicate market code cannot appear twice.

Soft correlations:

- `HOME_WIN` and `OVER_2_5_GOALS` may be positively correlated for attacking favorites.
- `BTTS_YES` and `OVER_2_5_GOALS` are correlated.
- Cards and red cards are correlated.
- Corners over and strong attacking-side markets may be correlated.

MVP handles hard conflicts deterministically and exposes a correlation warning. Production scoring should apply a correlation-adjusted accumulator probability or reduce correlated selections per batch.

## 18. Daily Cache and Refresh Logic

Daily lifecycle:

1. At local business cutoff, create refresh plan by league.
2. For each league/date, query `data_refresh_logs`.
3. If successful refresh exists, skip scraping and reuse existing data.
4. If not, fetch sources and create raw snapshots.
5. Parse, validate, normalize, compute features, generate predictions.
6. Mark refresh success only when all mandatory source categories complete.
7. Mark partial/failure with explicit reason.

Stale handling:

- Current calendar date cache is valid until next configured refresh window or manual force.
- Old raw snapshots are immutable.
- Derived predictions carry model version and generated date.
- Cache eviction removes derived rows only after raw and settlement audit windows are satisfied.

## 19. Excel Export Strategy

Use Apache POI SXSSF streaming:

- `Batches` tab: batch id, selection count, accumulator probability, risk band.
- `Selections` tab: fixture, market, predicted value, individual probability, model version.
- `Fixtures` tab: league, teams, kickoff, status.
- `Warnings` tab: missing data, correlation notes, stale data notes.
- `Metadata` tab: request id, generated time, active model version.

For large exports:

- Use streaming row window.
- Avoid holding complete workbook rows in memory.
- Use stable column schemas.
- Write numeric probabilities as decimals and apply Excel percentage formatting.

## 20. Admin Dashboard Requirements

Dashboard capabilities:

- Refresh status by league/date.
- Source target health.
- Consecutive source failures.
- Raw snapshot counts and checksums.
- AI extraction runs, token usage, schema failures, and validation failures.
- Prediction generation count by market.
- Model version currently active.
- Manual refresh trigger.
- Source disable/enable.
- Manual team alias mapping.
- Fixture override with audit trail.
- Settlement accuracy by league/market/model.

All manual changes require audit rows with admin identity, timestamp, old value, and new value.

## 21. Accuracy Tracking Post-Match

Settlement cron:

1. Fetch official final results and match events.
2. Validate final scores and event stats.
3. For every prediction selection, apply market settlement rule.
4. Write `WON`, `LOST`, or `VOID`.
5. Aggregate accuracy by market, league, model version, probability bucket, and month.

Metrics:

- Hit rate.
- Brier score.
- Log loss.
- Calibration error.
- ROI simulation only when odds are later collected.

Settlement must not rewrite original predicted probability.

## 22. Security Considerations

Controls:

- Bean validation on all input DTOs.
- Enum-based league and market selection.
- Request size limits.
- Rate limiting by IP/user.
- Authentication for admin refresh endpoints.
- CSRF protection for browser admin UI.
- Server-side date range caps.
- No raw URL submission from users.
- Scraper only reads admin-managed source targets.
- Escape all raw-source display in admin UI.
- Protect object-storage raw snapshots with private access.

The user form cannot influence scraper target URLs, selectors, headers, or browser execution scripts.

## 23. Legal and Scraping Risks

Operational safety:

- Respect robots.txt.
- Review source terms of service.
- Prefer sources that permit indexing or public access.
- Identify crawler politely.
- Use low-volume scheduled scraping.
- Cache daily data and do not scrape per user request.
- Stop scraping on access-denied signals.
- Avoid bypassing paywalls, login walls, CAPTCHA, or technical access controls.
- Keep source attribution and audit trails.

Legal risk is product risk. If a source disallows scraping, remove it and replace it with a permitted public source or manual import.

## 24. Development Roadmap

Phase 1: Foundation setup

- Maven Spring Boot project.
- PostgreSQL schema.
- Core entities, repositories, DTOs, controllers, services.
- League and market reference data.
- Daily refresh log idempotency.

Phase 2: Source registry and raw scraping

- Source target CRUD.
- Static scraper.
- Playwright worker.
- Raw snapshots.
- Rate limiting and robots checks.

Phase 3: AI extraction and validation

- Prompt templates.
- JSON schema validation.
- Rejection tables.
- Team alias mapping.

Phase 4: Feature engineering

- Rolling team stats.
- League baselines.
- Head-to-head calculations.

Phase 5: Prediction engine

- Market scorer implementations.
- Calibration.
- Model versioning.

Phase 6: Batch builder and export

- Correlation rules.
- SXSSF Excel export.
- Form UI.

Phase 7: Admin and settlement

- Dashboard.
- Manual override audit.
- Post-match settlement.
- Accuracy reporting.

## 25. Folder and Package Structure

Phase 1 single-module layout:

```text
src/main/java/com/betai
  BetAiApplication.java
  api
  api/dto
  config
  domain/common
  domain/league
  domain/team
  domain/match
  domain/market
  domain/prediction
  domain/refresh
  exception
  repository
  service
src/main/resources
  application.yml
  db/migration
docs
  architecture-blueprint.md
```

Production multi-module target:

```text
bet-ai-api
bet-ai-domain
bet-ai-ingestion
bet-ai-prediction
bet-ai-export
bet-ai-admin
bet-ai-shared
```

## 26. Entity Relationship Design

Relations:

- `League` one-to-many `Team`.
- `League` one-to-many `Match`.
- `Team` one-to-many `Match` as home team.
- `Team` one-to-many `Match` as away team.
- `Match` one-to-many `PredictionSelection`.
- `MarketDefinition` one-to-many `PredictionSelection`.
- `League` one-to-many `DataRefreshLog`.
- Future `RawSnapshot` many-to-one `SourceTarget`.
- Future `PredictionBatch` many-to-many `PredictionSelection` through `prediction_batch_items`.

Important constraints:

- A match cannot have the same home and away team.
- A market prediction is unique by match, market, and model version.
- One successful daily refresh per league/date is allowed unless force refresh supersedes the previous success.

## 27. Example SQL Tables

Representative schema:

```sql
create table leagues (
  id uuid primary key,
  code varchar(64) not null unique,
  name varchar(128) not null,
  country varchar(128) not null,
  tier integer not null,
  active boolean not null,
  scrape_enabled boolean not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create table matches (
  id uuid primary key,
  league_id uuid not null references leagues(id),
  home_team_id uuid not null references teams(id),
  away_team_id uuid not null references teams(id),
  match_date date not null,
  kickoff_at timestamptz not null,
  status varchar(32) not null,
  home_score integer,
  away_score integer,
  check (home_team_id <> away_team_id),
  check (home_score is null or home_score >= 0),
  check (away_score is null or away_score >= 0)
);

create index idx_matches_league_date_status
  on matches(league_id, match_date, status);

create unique index ux_prediction_selection_model
  on prediction_selections(match_id, market_definition_id, model_version);

create unique index ux_refresh_success_once
  on data_refresh_logs(league_id, refresh_date)
  where refresh_status = 'SUCCESS';
```

The Phase 1 migration contains the concrete implementation.

## 28. Example Java Classes and Services

Architectural pattern:

```java
@RestController
class PredictionController {
    private final PredictionFormService predictionFormService;

    @PostMapping("/api/v1/predictions/form")
    PredictionResponse submit(@Valid @RequestBody PredictionRequest request) {
        return predictionFormService.generatePredictions(request);
    }
}

interface PredictionFormService {
    PredictionResponse generatePredictions(PredictionRequest request);
}

@Service
class PredictionFormServiceImpl implements PredictionFormService {
    private final MatchRepository matchRepository;
    private final PredictionSelectionRepository selectionRepository;
    private final BatchBuilder batchBuilder;
}
```

Domain rules stay in services and value objects. Controllers do not build predictions directly.

## 29. Example Request and Response JSON

Request:

```json
{
  "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA"],
  "marketCodes": ["HOME_WIN", "OVER_2_5_GOALS", "BTTS_YES"],
  "fixtureDateFrom": "2026-08-15",
  "fixtureDateTo": "2026-08-17",
  "batchCount": 3,
  "selectionsPerBatch": 5
}
```

Response:

```json
{
  "requestId": "7b58e899-8d4b-4483-86b5-064ad3709e56",
  "generatedAt": "2026-08-15T08:30:00Z",
  "fixturesConsidered": 18,
  "candidateSelections": 41,
  "batches": [
    {
      "batchNumber": 1,
      "selectionCount": 5,
      "risk": {
        "jointProbability": 0.327680,
        "averageIndividualProbability": 0.800000,
        "riskBand": "MODERATE",
        "varianceWarning": "Five 80% selections produce a 32.77% accumulator probability under independence."
      },
      "selections": [
        {
          "matchId": "9fedf828-1a7e-4c97-9d88-f54a79075143",
          "leagueCode": "PREMIER_LEAGUE",
          "fixture": "Arsenal vs Chelsea",
          "marketCode": "OVER_2_5_GOALS",
          "predictedValue": "OVER",
          "probability": 0.800000
        }
      ]
    }
  ],
  "warnings": []
}
```

## 30. Step-by-Step Build Order

1. Create Spring Boot project, dependency management, Java 21 build.
2. Configure PostgreSQL, HikariCP, Flyway, and Hibernate validation.
3. Create core entities and migration.
4. Seed MVP leagues and market definitions.
5. Add repositories with date-based lookup queries.
6. Add validated prediction form DTO.
7. Add prediction form API and response DTOs.
8. Add daily refresh log API and idempotency service.
9. Add batch probability math and conflict filtering.
10. Add raw source target and snapshot tables.
11. Add scraper worker.
12. Add extraction and deterministic validation.
13. Add normalization and feature tables.
14. Add prediction scorers.
15. Add settlement and accuracy tracking.
16. Add admin dashboard and Excel export.

## Probability Modeling Constraint

Individual probability and batch probability are different quantities.

- Individual selection probability: probability that one market selection wins.
- Full accumulator probability: probability that every selection in the batch wins at the same time.

Core MVP math assumes independence:

```text
P(Batch) = product(P(A_i)) for i = 1..n
```

Example:

```text
P(A_i) = 0.80
n = 20
P(Batch) = 0.80^20 = 0.0115292150 = 1.15%
```

The UI must display both:

- Each selection confidence, e.g. `80%`.
- Full batch probability, e.g. `1.15%`.

Risk wording:

- "High-confidence individual selections do not imply a high-confidence accumulator."
- "Accumulator variance decays exponentially as selections are added."
- "The displayed batch probability is not a guarantee and ignores bookmaker margin, odds value, and unresolved correlation."

For correlated selections, the MVP removes hard conflicts and warns about soft correlation. A production model should estimate joint probability directly or apply correlation penalties.
