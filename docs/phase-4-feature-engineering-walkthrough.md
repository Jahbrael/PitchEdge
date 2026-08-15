# Phase 4 Feature Engineering Walkthrough

Phase 4 computes reusable model inputs from normalized matches and match statistics. It does not generate predictions yet. The feature tables are the stable input layer for Phase 5 probability scoring.

## Added Database Objects

- `feature_generation_runs`: auditable generation attempts by league/date/season.
- `league_baselines`: league-wide scoring, result, goals, corners, cards, and red-card rates.
- `team_feature_snapshots`: one feature row per team, league, season, and calculation date.
- `V4__feature_engineering_outputs.sql`: creates feature output tables.
- `V5__align_football_data_2025_2026_season.sql`: aligns the Football-Data `2526` source rows to season label `2025/2026`.

## Added Java Components

- `AdminFeatureController`: admin generation and query endpoints.
- `FeatureEngineeringService`: feature engineering boundary.
- `FeatureEngineeringServiceImpl`: deterministic calculator over finished matches and match statistics.
- `FeatureGenerationRun`, `LeagueBaseline`, `TeamFeatureSnapshot`: feature entities.
- Repositories for feature generation runs, league baselines, and team feature snapshots.

## League Baseline Features

Each `league_baselines` row stores:

- Matches sampled.
- Average home goals.
- Average away goals.
- Average total goals.
- Home win, draw, and away win rates.
- BTTS rate.
- Over 1.5 goals rate.
- Over 2.5 goals rate.
- Under 3.5 goals rate.
- Average total corners.
- Average total yellow cards.
- Red-card match rate.

Rates are decimals from `0.000000` to `1.000000`, not display percentages.

## Team Feature Snapshot Features

Each `team_feature_snapshots` row stores:

- Matches played, home matches, away matches.
- Last 5 and last 10 sample sizes.
- Points per match.
- Last 5 and last 10 points per match.
- Goals for and against per match.
- Home and away goals for/against splits.
- Clean-sheet rate.
- Failed-to-score rate.
- BTTS rate.
- Over 1.5, Over 2.5, Under 3.5 rates.
- Corners for/against per match.
- Yellow cards for/against per match.
- Team red-card rate.
- Form score.

`formScore` is deterministic:

```text
formScore = 0.30 * pointsPerMatch
          + 0.50 * last5PointsPerMatch
          + 0.20 * last10PointsPerMatch
```

## Endpoints

Generate features:

```bash
curl -X POST http://localhost:8080/api/v1/admin/features/daily \
  -H "Content-Type: application/json" \
  -d '{
    "leagueCodes": ["PREMIER_LEAGUE", "LA_LIGA", "SERIE_A"],
    "calculationDate": "2026-06-06",
    "forceRegenerate": false
  }'
```

Read one league baseline:

```bash
curl "http://localhost:8080/api/v1/admin/features/league-baseline?leagueCode=PREMIER_LEAGUE&calculationDate=2026-06-06"
```

List team feature snapshots:

```bash
curl "http://localhost:8080/api/v1/admin/features/team-snapshots?leagueCode=PREMIER_LEAGUE&calculationDate=2026-06-06"
```

## Verified Local Output

Using the normalized Football-Data `2025/2026` rows for the three MVP leagues:

- `3` league baselines.
- `60` team feature snapshots.
- `3` feature generation runs.

Per league:

- `380` matches sampled.
- `20` team feature snapshots generated.
- `1` league baseline generated.

Example Premier League baseline:

```text
avgHomeGoals: 1.5263
avgAwayGoals: 1.2237
avgTotalGoals: 2.7500
homeWinRate: 0.426316
drawRate: 0.273684
awayWinRate: 0.300000
bttsRate: 0.560526
over15Rate: 0.789474
over25Rate: 0.550000
under35Rate: 0.715789
avgTotalCorners: 9.9974
avgTotalYellowCards: 3.7474
redCardRate: 0.092105
```

## Current Limits

- xG is not computed because the current Football-Data CSV source does not provide xG.
- Features are computed from finished matches only.
- Prediction probabilities are generated in Phase 5 from these feature snapshots.

## Phase 5 Continuation

Prediction generation from feature snapshots is documented in [phase-5-prediction-generation-walkthrough.md](</home/dell/IdeaProjects/Bet AI/docs/phase-5-prediction-generation-walkthrough.md>).
