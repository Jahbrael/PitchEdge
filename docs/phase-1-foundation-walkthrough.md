# Phase 1 Foundation Walkthrough

This file explains the production foundation generated for the form-based football prediction application.

## Project Build

- `pom.xml`: Maven Spring Boot project pinned to Spring Boot `3.5.13`, Java `21`, and Apache POI `5.5.1`. It includes Spring Web, Data JPA, Validation, Actuator, PostgreSQL runtime driver, Flyway migrations, Lombok, and POI OOXML support. Lombok is excluded from the final boot artifact.
- `.gitignore`: Adds Maven `target/` output to the existing IDE ignores.

## Runtime Configuration

- `src/main/resources/application.yml`: Production-style configuration with environment-variable database credentials, HikariCP pool sizing, Flyway enabled, Hibernate `ddl-auto: validate`, UTC JDBC timestamps, Jackson ISO date output, and basic actuator exposure.
- `src/main/java/com/betai/BetAiApplication.java`: Main Spring Boot entry point. It enables configuration-properties scanning and JPA auditing.
- `src/main/java/com/betai/config/ClockConfig.java`: Provides a UTC-capable injectable `Clock` so time-sensitive services can be tested and made deterministic.
- `src/main/java/com/betai/config/PredictionProperties.java`: Binds request limits and active model version from `application.yml`.

## Database Migration

- `src/main/resources/db/migration/V1__foundation_schema.sql`: Creates the Phase 1 PostgreSQL schema for leagues, teams, matches, market definitions, prediction selections, and daily refresh logs. It includes foreign keys, uniqueness constraints, score/probability sanity checks, date lookup indexes, and a partial unique index ensuring one successful refresh per league/date.

## Domain Common

- `domain/common/BaseEntity.java`: Shared UUID primary key plus audited `createdAt` and `updatedAt` timestamps. Equality is ID-based only after persistence.

## League and Team Domain

- `domain/league/LeagueCode.java`: Enum boundary for the MVP leagues: Premier League, La Liga, Serie A.
- `domain/league/League.java`: League reference entity with activity and scrape-enable flags plus current season.
- `domain/team/Team.java`: Canonical team entity scoped to a league with a source-facing external key. Alias mapping belongs in a later normalization phase.

## Fixture Domain

- `domain/match/MatchStatus.java`: Controlled fixture lifecycle values.
- `domain/match/Match.java`: Football fixture entity with league, home/away teams, kickoff, match date, status, optional scores, season, round, venue, and source fixture key. The table is named `matches`; the JPA entity name is `FootballMatch` to keep JPQL unambiguous.

## Market Domain

- `domain/market/MarketType.java`: Groups markets by result, total goals, BTTS, discipline, and corners.
- `domain/market/MarketCode.java`: Defines the ten MVP markets, including baseline thresholds for yellow cards over `3.5`, red card yes `0.5`, and corners over `8.5`.
- `domain/market/MarketDefinition.java`: Database-backed market configuration. It lets future markets be added as data/configuration while preserving an enum boundary for the MVP API.

## Prediction Domain

- `domain/prediction/PredictionOutcome.java`: Settlement state for each prediction selection.
- `domain/prediction/PredictionSelection.java`: Stores one predicted market selection for one fixture, including predicted value, probability, model version, generation time, correlation key, feature snapshot text, and outcome. Probability is constrained to `0..1` in the database.

## Refresh Domain

- `domain/refresh/RefreshStatus.java`: Refresh lifecycle states including `SUPERSEDED` for force refreshes.
- `domain/refresh/DataRefreshLog.java`: Tracks league/date refresh execution, duration, raw payload references, record counts, checksums, and failures. It contains state-transition methods for success, failure, and superseding.

## Repositories

- `LeagueRepository.java`: Active league and scrape-enabled league lookups.
- `TeamRepository.java`: League/team and external-key lookups for normalization and fixture ingestion.
- `MatchRepository.java`: Date-based fixture queries with league/team fetch joins for API responses.
- `MarketDefinitionRepository.java`: Enabled market lookups and idempotent market seeding support.
- `PredictionSelectionRepository.java`: Candidate selection query by league, market, date range, match status, and prediction outcome.
- `DataRefreshLogRepository.java`: Refresh history lookups plus a pessimistic-write lookup used by the idempotent daily refresh service.

## DTOs

- `PredictionRequest.java`: The six-field form contract: leagues, markets, date-from, date-to, batch count, selections per batch.
- `PredictionResponse.java`: Structured response containing request metadata, fixture/selection counts, batches, and warnings.
- `PredictionBatchResponse.java`: One generated batch plus risk metrics and selections.
- `PredictionSelectionResponse.java`: API-safe projection of a stored prediction selection.
- `BatchRiskMetricsResponse.java`: Joint accumulator probability, individual probability summary, risk band, and variance warning.
- `RiskBand.java`: LOW, MODERATE, HIGH, EXTREME labels derived from joint probability.
- `DailyRefreshRequest.java`: Admin refresh trigger contract with optional league list, optional date, and force flag.
- `DailyRefreshResponse.java`: Admin refresh response with request id and log rows.
- `RefreshLogResponse.java`: API projection of a refresh log.
- `ApiErrorResponse.java`: Stable error payload for validation and server errors.

## Services

- `PredictionFormService.java`: Form submission boundary.
- `PredictionFormServiceImpl.java`: Validates request bounds/reference data, reads scheduled fixtures and stored prediction selections, builds batches, and emits cache/data warnings. It never scrapes on form submission.
- `BatchBuilder.java`: Sorts selections by probability, prevents duplicate/conflicting match selections in a batch, creates discrete batches, and computes accumulator probability as the product of individual probabilities.
- `DailyRefreshService.java`: Admin refresh boundary.
- `DailyRefreshServiceImpl.java`: Implements idempotent daily refresh logging. It reuses a successful refresh unless `forceRefresh` is true, in which case the previous success is superseded before a new success row is created.
- `ReferenceDataInitializer.java`: Idempotently seeds the three MVP leagues and ten MVP markets on startup without overwriting operational enabled/disabled flags.

## Phase 2 Continuation

The ingestion layer added after this foundation is documented in [phase-2-ingestion-walkthrough.md](</home/dell/IdeaProjects/Bet AI/docs/phase-2-ingestion-walkthrough.md>).

## API

- `PredictionController.java`: `POST /api/v1/predictions/form` for structured prediction form submissions.
- `AdminRefreshController.java`: `POST /api/v1/admin/refresh/daily` for manual daily refresh lifecycle triggering.
- `GlobalExceptionHandler.java`: Converts validation, bad JSON/enum input, reference-data errors, data-integrity errors, and unexpected failures into stable JSON error responses.
