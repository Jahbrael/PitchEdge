# Phase 12 Public Prediction Web UI Walkthrough

Phase 12 adds the user-facing prediction form page. This is a structured form interface, not a chatbot. It submits the existing six-field `PredictionRequest` contract to the public prediction API and renders deterministic batch outputs.

## Added Static UI Files

- `src/main/resources/static/predictions.html`
- `src/main/resources/static/predictions.css`
- `src/main/resources/static/predictions.js`

Browser URL:

```text
http://localhost:8080/predictions.html
```

## Public Form Fields

The page maps directly to `PredictionRequest`:

- `leagueCodes`
- `marketCodes`
- `fixtureDateFrom`
- `fixtureDateTo`
- `batchCount`
- `selectionsPerBatch`

The page defaults to:

- all three MVP leagues selected
- all ten MVP markets selected
- a 14-day inclusive fixture window
- `3` batches
- `5` selections per batch

The browser validates:

- at least one league
- at least one market
- valid date order
- max 14 inclusive fixture days
- `1..20` batches
- `1..20` selections per batch

## API Calls Used

Prediction generation:

```http
POST /api/v1/predictions/form
```

Excel export:

```http
POST /api/v1/predictions/form/export
```

Both routes are intentionally public and remain covered by the Phase 11 public API rate limiter.

## Rendered Output

The page displays:

- model version
- generated timestamp
- match statuses used by the backend
- fixtures considered
- candidate selections
- selections returned
- batch count
- warning messages
- empty-state message when no pending slate exists
- per-batch full accumulator probability
- average/min/max individual selection probability
- risk band
- variance warning
- one table of selections per batch

The UI explicitly separates individual selection probability from full batch probability. Full batch probability is the product of individual selection probabilities as calculated by the backend `BatchBuilder`.

## Security Integration

`SecurityConfig` now permits:

- `GET /predictions.html`
- `GET /predictions.css`
- `GET /predictions.js`

The public prediction API was already open:

```text
/api/v1/predictions/**
```

Admin routes remain protected:

```text
/api/v1/admin/** requires X-BETAI-ADMIN-KEY
```

## Current Empty-Slate Behavior

The current local database has historical predictions that were settled in Phase 8:

- `pendingSelections = 0`
- historical predictions are `WON` or `LOST`

The public form API only returns `PENDING` selections. Therefore the public page can correctly show:

```text
No active prediction slate
```

This is expected until scheduled future fixtures are loaded and Phase 5 prediction generation creates pending selections for them.

## Verified Local Output

Verified on a temporary server at port `18080`:

- `GET /predictions.html` returned `200 text/html`.
- `GET /predictions.css` returned `200 text/css`.
- `GET /predictions.js` returned `200 text/javascript`.
- `POST /api/v1/predictions/form` returned `200` JSON with the expected empty-slate warnings for the current date range.
- `POST /api/v1/predictions/form/export` returned `200 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- The generated workbook `/tmp/betai-phase12-form-export.xlsx` passed `unzip -t`.
- Security headers include `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, and the self-only CSP.
- Maven package build passed under JDK 21.

Build command used:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw -q -DskipTests package
```

## Running Locally

```bash
cd "/home/dell/IdeaProjects/Bet AI"
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

Open:

```text
http://localhost:8080/predictions.html
```

## Current Limits

- This is a static frontend, not a React/Vue/Angular build.
- The page does not generate predictions itself. It only submits forms to the backend.
- Empty slates are expected when no pending predictions exist.
- The page does not include user accounts, payment, odds shopping, or bookmaker integration.
- Production should use `BETAI_FORM_MATCH_STATUSES=SCHEDULED` so public form results do not include historical/backtest modes.

## Phase 13 Continuation

Production readiness, scheduled automation, Docker Compose, and tests are documented in [phase-13-production-readiness-automation-and-tests-walkthrough.md](</home/dell/IdeaProjects/Bet AI/docs/phase-13-production-readiness-automation-and-tests-walkthrough.md>).
