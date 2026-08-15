package com.betai.export;

import com.betai.api.dto.ModelAccuracyResponse;
import com.betai.api.dto.PredictionBatchResponse;
import com.betai.api.dto.PredictionResponse;
import com.betai.api.dto.PredictionSelectionResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

@Service
public class ExcelExportServiceImpl implements ExcelExportService {

    private static final int ROW_ACCESS_WINDOW_SIZE = 100;

    @Override
    public void writePredictionResponse(PredictionResponse response, OutputStream outputStream) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE);
        try {
            workbook.setCompressTempFiles(true);
            WorkbookStyles styles = new WorkbookStyles(workbook);
            writePredictionSummary(workbook, styles, response);
            writePredictionBatches(workbook, styles, response);
            writePredictionSelections(workbook, styles, response);
            writeWarnings(workbook, styles, response.warnings());
            workbook.write(outputStream);
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    @Override
    public void writeModelAccuracy(List<ModelAccuracyResponse> accuracyRows, OutputStream outputStream) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE);
        try {
            workbook.setCompressTempFiles(true);
            WorkbookStyles styles = new WorkbookStyles(workbook);
            writeAccuracySummary(workbook, styles, accuracyRows);
            writeAccuracyRows(workbook, styles, accuracyRows);
            workbook.write(outputStream);
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    private void writePredictionSummary(Workbook workbook, WorkbookStyles styles, PredictionResponse response) {
        Sheet sheet = workbook.createSheet("Summary");
        int rowNumber = 0;
        rowNumber = keyValue(sheet, styles, rowNumber, "Request ID", response.requestId().toString());
        rowNumber = keyValue(sheet, styles, rowNumber, "Generated At", response.generatedAt());
        rowNumber = keyValue(sheet, styles, rowNumber, "Model Version", response.modelVersion());
        rowNumber = keyValue(sheet, styles, rowNumber, "League Codes", String.join(", ", response.input().leagueCodes().stream().map(Enum::name).sorted().toList()));
        rowNumber = keyValue(sheet, styles, rowNumber, "Market Codes", String.join(", ", response.input().marketCodes().stream().map(Enum::name).sorted().toList()));
        rowNumber = keyValue(sheet, styles, rowNumber, "Fixture Date From", response.input().fixtureDateFrom());
        rowNumber = keyValue(sheet, styles, rowNumber, "Fixture Date To", response.input().fixtureDateTo());
        rowNumber = keyValue(sheet, styles, rowNumber, "Strategy", response.input().strategy());
        rowNumber = keyValue(sheet, styles, rowNumber, "Requested Batches", response.input().numberOfBatches() == null ? response.input().batchCount() : response.input().numberOfBatches());
        rowNumber = keyValue(sheet, styles, rowNumber, "Minimum Selections", response.requestedMinimumSelections());
        rowNumber = keyValue(sheet, styles, rowNumber, "Maximum Selections", response.requestedMaximumSelections());
        rowNumber = keyValue(sheet, styles, rowNumber, "Qualified Selections", response.qualifiedSelectionsFound());
        rowNumber = keyValue(sheet, styles, rowNumber, "Response Status", response.status());
        rowNumber = keyValue(sheet, styles, rowNumber, "Match Statuses Used", String.join(", ", response.matchStatusesUsed()));
        rowNumber = keyValue(sheet, styles, rowNumber, "Fixtures Considered", response.fixturesConsidered());
        rowNumber = keyValue(sheet, styles, rowNumber, "Candidate Selections", response.candidateSelections());
        keyValue(sheet, styles, rowNumber, "Selections Returned", response.selectionsReturned());
        setWidths(sheet, 28, 80);
    }

    private void writePredictionBatches(Workbook workbook, WorkbookStyles styles, PredictionResponse response) {
        Sheet sheet = workbook.createSheet("Batches");
        Row header = sheet.createRow(0);
        writeHeader(header, styles, "Batch Number", "Selection Count", "Joint Probability", "Average Probability",
                "Minimum Probability", "Maximum Probability", "Priced Selections", "Positive Value Selections",
                "Average EV", "Minimum EV", "Maximum EV", "Aggregate Decimal Odds", "Accumulator EV",
                "Risk Band", "Variance Warning");

        int rowNumber = 1;
        for (PredictionBatchResponse batch : response.batches()) {
            Row row = sheet.createRow(rowNumber++);
            int cell = 0;
            value(row, cell++, batch.batchNumber(), styles.integerStyle());
            value(row, cell++, batch.selectionCount(), styles.integerStyle());
            value(row, cell++, batch.risk().jointProbability(), styles.decimalStyle());
            value(row, cell++, batch.risk().averageIndividualProbability(), styles.decimalStyle());
            value(row, cell++, batch.risk().minimumIndividualProbability(), styles.decimalStyle());
            value(row, cell++, batch.risk().maximumIndividualProbability(), styles.decimalStyle());
            value(row, cell++, batch.risk().pricedSelectionCount(), styles.integerStyle());
            value(row, cell++, batch.risk().positiveValueSelectionCount(), styles.integerStyle());
            value(row, cell++, batch.risk().averageExpectedValue(), styles.decimalStyle());
            value(row, cell++, batch.risk().minimumExpectedValue(), styles.decimalStyle());
            value(row, cell++, batch.risk().maximumExpectedValue(), styles.decimalStyle());
            value(row, cell++, batch.risk().aggregateDecimalOdds(), styles.decimalStyle());
            value(row, cell++, batch.risk().accumulatorExpectedValue(), styles.decimalStyle());
            value(row, cell++, batch.risk().riskBand().name(), styles.textStyle());
            value(row, cell, batch.risk().varianceWarning(), styles.textStyle());
        }
        setWidths(sheet, 16, 16, 18, 20, 20, 20, 18, 24, 16, 16, 16, 24, 18, 14, 110);
    }

    private void writePredictionSelections(Workbook workbook, WorkbookStyles styles, PredictionResponse response) {
        Sheet sheet = workbook.createSheet("Selections");
        Row header = sheet.createRow(0);
        writeHeader(header, styles, "Batch Number", "Selection ID", "Match ID", "League", "Fixture", "Kickoff",
                "Market Code", "Market Name", "Predicted Value", "Probability", "Raw Probability",
                "Probability Adjustment", "Confidence Band", "Model Quality Sample", "Calibration Error",
                "Calibration Note", "Tuning Adjustment", "Tuning Note", "Decimal Odds", "Bookmaker", "Implied Probability", "Value Edge",
                "Expected Value", "Value Rating", "Odds Captured At", "Value Note", "Model Version");

        int rowNumber = 1;
        for (PredictionBatchResponse batch : response.batches()) {
            for (PredictionSelectionResponse selection : batch.selections()) {
                Row row = sheet.createRow(rowNumber++);
                int cell = 0;
                value(row, cell++, batch.batchNumber(), styles.integerStyle());
                value(row, cell++, selection.selectionId().toString(), styles.textStyle());
                value(row, cell++, selection.matchId().toString(), styles.textStyle());
                value(row, cell++, selection.leagueCode(), styles.textStyle());
                value(row, cell++, selection.fixture(), styles.textStyle());
                value(row, cell++, selection.kickoffAt(), styles.dateTimeStyle());
                value(row, cell++, selection.marketCode(), styles.textStyle());
                value(row, cell++, selection.marketName(), styles.textStyle());
                value(row, cell++, selection.predictedValue(), styles.textStyle());
                value(row, cell++, selection.probability(), styles.decimalStyle());
                value(row, cell++, selection.rawProbability(), styles.decimalStyle());
                value(row, cell++, selection.probabilityAdjustment(), styles.decimalStyle());
                value(row, cell++, selection.confidenceBand(), styles.textStyle());
                value(row, cell++, selection.modelQualitySampleSize(), styles.integerStyle());
                value(row, cell++, selection.modelQualityCalibrationError(), styles.decimalStyle());
                value(row, cell++, selection.calibrationNote(), styles.textStyle());
                value(row, cell++, selection.tuningAdjustment(), styles.decimalStyle());
                value(row, cell++, selection.tuningNote(), styles.textStyle());
                value(row, cell++, selection.bestDecimalOdds(), styles.decimalStyle());
                value(row, cell++, selection.bestOddsBookmaker(), styles.textStyle());
                value(row, cell++, selection.bestImpliedProbability(), styles.decimalStyle());
                value(row, cell++, selection.valueEdge(), styles.decimalStyle());
                value(row, cell++, selection.expectedValue(), styles.decimalStyle());
                value(row, cell++, selection.valueRating(), styles.textStyle());
                value(row, cell++, selection.oddsCapturedAt(), styles.dateTimeStyle());
                value(row, cell++, selection.valueNote(), styles.textStyle());
                value(row, cell, selection.modelVersion(), styles.textStyle());
            }
        }
        setWidths(sheet, 14, 38, 38, 18, 38, 22, 20, 28, 18, 16, 16, 20, 18, 20, 20, 72, 18, 72,
                16, 20, 20, 16, 16, 18, 22, 90, 28);
    }

    private void writeWarnings(Workbook workbook, WorkbookStyles styles, List<String> warnings) {
        Sheet sheet = workbook.createSheet("Warnings");
        Row header = sheet.createRow(0);
        writeHeader(header, styles, "Warning");

        int rowNumber = 1;
        for (String warning : warnings) {
            Row row = sheet.createRow(rowNumber++);
            value(row, 0, warning, styles.textStyle());
        }
        setWidths(sheet, 120);
    }

    private void writeAccuracySummary(Workbook workbook, WorkbookStyles styles, List<ModelAccuracyResponse> accuracyRows) {
        Sheet sheet = workbook.createSheet("Summary");
        int totalSettled = accuracyRows.stream().mapToInt(ModelAccuracyResponse::settledSelections).sum();
        int totalWon = accuracyRows.stream().mapToInt(ModelAccuracyResponse::wonCount).sum();
        int totalLost = accuracyRows.stream().mapToInt(ModelAccuracyResponse::lostCount).sum();
        int totalVoid = accuracyRows.stream().mapToInt(ModelAccuracyResponse::voidCount).sum();

        int rowNumber = 0;
        rowNumber = keyValue(sheet, styles, rowNumber, "Rows", accuracyRows.size());
        rowNumber = keyValue(sheet, styles, rowNumber, "Settled Selections", totalSettled);
        rowNumber = keyValue(sheet, styles, rowNumber, "Won", totalWon);
        rowNumber = keyValue(sheet, styles, rowNumber, "Lost", totalLost);
        keyValue(sheet, styles, rowNumber, "Void", totalVoid);
        setWidths(sheet, 28, 24);
    }

    private void writeAccuracyRows(Workbook workbook, WorkbookStyles styles, List<ModelAccuracyResponse> accuracyRows) {
        Sheet sheet = workbook.createSheet("Accuracy");
        Row header = sheet.createRow(0);
        writeHeader(header, styles, "Accuracy ID", "League", "Market Code", "Market Name", "Model Version",
                "Accuracy Date", "Settled", "Won", "Lost", "Void", "Win Rate", "Average Probability",
                "Brier Score", "Calibration Error");

        int rowNumber = 1;
        for (ModelAccuracyResponse accuracy : accuracyRows) {
            Row row = sheet.createRow(rowNumber++);
            int cell = 0;
            value(row, cell++, accuracy.accuracyId().toString(), styles.textStyle());
            value(row, cell++, accuracy.leagueCode(), styles.textStyle());
            value(row, cell++, accuracy.marketCode(), styles.textStyle());
            value(row, cell++, accuracy.marketName(), styles.textStyle());
            value(row, cell++, accuracy.modelVersion(), styles.textStyle());
            value(row, cell++, accuracy.accuracyDate(), styles.dateStyle());
            value(row, cell++, accuracy.settledSelections(), styles.integerStyle());
            value(row, cell++, accuracy.wonCount(), styles.integerStyle());
            value(row, cell++, accuracy.lostCount(), styles.integerStyle());
            value(row, cell++, accuracy.voidCount(), styles.integerStyle());
            value(row, cell++, accuracy.winRate(), styles.decimalStyle());
            value(row, cell++, accuracy.averageProbability(), styles.decimalStyle());
            value(row, cell++, accuracy.brierScore(), styles.decimalStyle());
            value(row, cell, accuracy.calibrationError(), styles.decimalStyle());
        }
        setWidths(sheet, 38, 18, 22, 30, 28, 16, 12, 12, 12, 12, 14, 22, 16, 20);
    }

    private int keyValue(Sheet sheet, WorkbookStyles styles, int rowNumber, String key, Object value) {
        Row row = sheet.createRow(rowNumber);
        Cell keyCell = row.createCell(0);
        keyCell.setCellValue(key);
        keyCell.setCellStyle(styles.headerStyle());
        value(row, 1, value, styleFor(styles, value));
        return rowNumber + 1;
    }

    private void writeHeader(Row row, WorkbookStyles styles, String... headers) {
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.headerStyle());
        }
    }

    private void value(Row row, int cellIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(cellIndex);
        cell.setCellStyle(style);
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Integer integer) {
            cell.setCellValue(integer);
        } else if (value instanceof Long longValue) {
            cell.setCellValue(longValue);
        } else if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
        } else if (value instanceof LocalDate localDate) {
            cell.setCellValue(Date.from(localDate.atStartOfDay().toInstant(ZoneOffset.UTC)));
        } else if (value instanceof OffsetDateTime offsetDateTime) {
            cell.setCellValue(Date.from(offsetDateTime.toInstant()));
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private CellStyle styleFor(WorkbookStyles styles, Object value) {
        if (value instanceof Integer || value instanceof Long) {
            return styles.integerStyle();
        }
        if (value instanceof BigDecimal) {
            return styles.decimalStyle();
        }
        if (value instanceof LocalDate) {
            return styles.dateStyle();
        }
        if (value instanceof OffsetDateTime) {
            return styles.dateTimeStyle();
        }
        return styles.textStyle();
    }

    private void setWidths(Sheet sheet, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, Math.min(widths[i], 255) * 256);
        }
    }

    private record WorkbookStyles(
            CellStyle headerStyle,
            CellStyle textStyle,
            CellStyle integerStyle,
            CellStyle decimalStyle,
            CellStyle dateStyle,
            CellStyle dateTimeStyle
    ) {
        private WorkbookStyles(Workbook workbook) {
            this(
                    headerStyle(workbook),
                    textStyle(workbook),
                    integerStyle(workbook),
                    decimalStyle(workbook),
                    dateStyle(workbook),
                    dateTimeStyle(workbook)
            );
        }

        private static CellStyle headerStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            return style;
        }

        private static CellStyle textStyle(Workbook workbook) {
            return workbook.createCellStyle();
        }

        private static CellStyle integerStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("0"));
            return style;
        }

        private static CellStyle decimalStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("0.000000"));
            return style;
        }

        private static CellStyle dateStyle(Workbook workbook) {
            CreationHelper helper = workbook.getCreationHelper();
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));
            return style;
        }

        private static CellStyle dateTimeStyle(Workbook workbook) {
            CreationHelper helper = workbook.getCreationHelper();
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            return style;
        }
    }
}
