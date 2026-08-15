# Phase 9 Excel Export Walkthrough

Phase 9 adds downloadable Excel exports for structured prediction and accuracy outputs. Exports use Apache POI `SXSSFWorkbook`, which writes rows through a streaming workbook API instead of holding an entire in-memory XSSF workbook.

## Added Java Components

- `ExcelExportService`: export service boundary.
- `ExcelExportServiceImpl`: Apache POI streaming workbook writer.

Updated controllers:

- `PredictionController`: adds public form export endpoint.
- `AdminSettlementController`: adds model accuracy export endpoint.

No new dependency was needed because Apache POI was already included in `pom.xml`.

## Prediction Form Export

Endpoint:

```bash
curl -o bet-ai-predictions.xlsx \
  -X POST http://localhost:8080/api/v1/predictions/form/export \
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

Workbook tabs:

- `Summary`: request metadata, model version, fixture counts, candidate counts, returned selection count.
- `Batches`: batch-level risk metrics and accumulator variance warning.
- `Selections`: one row per returned selection.
- `Warnings`: stale data, historical/backtest, missing generation, or incomplete batch warnings.

Current note: after Phase 8, historical predictions are settled and no longer `PENDING`, so historical form exports can have empty batch/selection sheets. That is correct. Future scheduled predictions remain pending until settlement.

## Accuracy Export

Endpoint:

```bash
curl -o bet-ai-premier-league-accuracy.xlsx \
  "http://localhost:8080/api/v1/admin/settlement/accuracy/export?leagueCode=PREMIER_LEAGUE&modelVersion=phase5-deterministic-v1&accuracyDate=2026-06-06"
```

Workbook tabs:

- `Summary`: total rows, settled selections, wins, losses, voids.
- `Accuracy`: one row per market with win rate, average probability, Brier score, and calibration error.

## Verified Local Output

The following files were generated and validated with `unzip -t`:

- `/tmp/bet-ai-premier-league-accuracy.xlsx`
- `/tmp/bet-ai-form-export.xlsx`

Both returned:

```text
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

Both were recognized as:

```text
Microsoft Excel 2007+
```

Both passed compressed package validation with no errors.

## Current Limits

- Exports are generated on demand from the current API response or current accuracy rows.
- Exports are not yet persisted as files or attached to an admin dashboard.
- Styling is intentionally minimal: stable headers, typed numeric/date cells, and fixed column widths.
- Large export pressure is reduced with `SXSSFWorkbook`, but HTTP timeout limits still depend on deployment configuration.

## Phase 10 Continuation

The admin dashboard is documented in [phase-10-admin-dashboard-walkthrough.md](</home/dell/IdeaProjects/Bet AI/docs/phase-10-admin-dashboard-walkthrough.md>).
