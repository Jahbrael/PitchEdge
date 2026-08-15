# Phase 16: Odds And Value Layer

Phase 16 adds deterministic value analysis on top of the existing calibrated prediction probabilities.

## Core Rule

The prediction model estimates event probability. Odds determine whether a selection is good value.

For decimal odds:

```text
impliedProbability = 1 / decimalOdds
valueEdge = calibratedProbability - impliedProbability
expectedValue = calibratedProbability * decimalOdds - 1
```

The system stores all imported odds quotes in `odds_snapshots`, then copies the current best quote summary onto `prediction_selections` for fast form responses.

## Tables

- `bookmakers`: normalized bookmaker registry.
- `odds_snapshots`: immutable imported odds quotes for a match, market, and bookmaker.
- `prediction_selections`: now includes best decimal odds, implied probability, edge, expected value, value rating, odds timestamp, and value note.

## Value Ratings

- `STRONG_VALUE`: expected value >= `0.100000` and edge >= `0.030000`.
- `VALUE`: expected value >= `0.030000` and edge >= `0.010000`.
- `FAIR`: expected value >= `-0.030000`.
- `NEGATIVE_VALUE`: odds are below the model break-even price.
- `NO_ODDS`: no imported odds quote exists for that selection.

## Import Odds

```bash
curl -X POST http://localhost:8080/api/v1/admin/odds/import \
  -H "Content-Type: application/json" \
  -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  -d '{
    "recalculateExistingSelections": true,
    "odds": [
      {
        "matchId": "MATCH_UUID",
        "marketCode": "HOME_WIN",
        "bookmakerCode": "BOOKMAKER_CODE",
        "bookmakerName": "Bookmaker Name",
        "decimalOdds": 2.05,
        "capturedAt": "2026-06-07T13:00:00Z",
        "sourceName": "permitted-source-name",
        "sourceUrl": "https://permitted-source.example/match"
      }
    ]
  }'
```

`matchId` is preferred. If unavailable, provide `leagueCode`, `matchDate`, `homeTeam`, and `awayTeam`.

## Inspect Odds Snapshots

```bash
curl -H "X-BETAI-ADMIN-KEY: local-dev-admin-key" \
  "http://localhost:8080/api/v1/admin/odds/snapshots?leagueCode=PREMIER_LEAGUE&marketCode=HOME_WIN&limit=100"
```

## Form Response Changes

Each returned selection can now include:

- `bestDecimalOdds`
- `bestOddsBookmaker`
- `bestImpliedProbability`
- `valueEdge`
- `expectedValue`
- `valueRating`
- `oddsCapturedAt`
- `valueNote`

Batch risk metrics now include priced selection count, positive value count, average EV, aggregate decimal odds, and accumulator EV when all selections have odds.
