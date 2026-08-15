package com.betai.api;

import com.betai.api.dto.ModelAccuracyResponse;
import com.betai.api.dto.SettlementRequest;
import com.betai.api.dto.SettlementResponse;
import com.betai.domain.league.LeagueCode;
import com.betai.export.ExcelExportService;
import com.betai.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/settlement")
public class AdminSettlementController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final SettlementService settlementService;
    private final ExcelExportService excelExportService;

    @PostMapping("/run")
    public ResponseEntity<SettlementResponse> runSettlement(@Valid @RequestBody SettlementRequest request) {
        return ResponseEntity.ok(settlementService.settlePredictions(request));
    }

    @GetMapping("/accuracy")
    public ResponseEntity<List<ModelAccuracyResponse>> accuracy(
            @RequestParam LeagueCode leagueCode,
            @RequestParam(required = false) String modelVersion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate accuracyDate
    ) {
        return ResponseEntity.ok(settlementService.getAccuracy(leagueCode, modelVersion, accuracyDate));
    }

    @GetMapping(value = "/accuracy/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<StreamingResponseBody> exportAccuracy(
            @RequestParam LeagueCode leagueCode,
            @RequestParam(required = false) String modelVersion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate accuracyDate
    ) {
        List<ModelAccuracyResponse> accuracyRows = settlementService.getAccuracy(leagueCode, modelVersion, accuracyDate);
        LocalDate resolvedDate = accuracyRows.isEmpty() || accuracyRows.getFirst().accuracyDate() == null
                ? LocalDate.now()
                : accuracyRows.getFirst().accuracyDate();
        String resolvedModelVersion = accuracyRows.isEmpty() || accuracyRows.getFirst().modelVersion() == null
                ? "default"
                : accuracyRows.getFirst().modelVersion();
        String filename = "bet-ai-accuracy-" + leagueCode.name().toLowerCase()
                + "-" + resolvedModelVersion.replaceAll("[^A-Za-z0-9._-]", "_")
                + "-" + FILE_DATE.format(resolvedDate) + ".xlsx";
        StreamingResponseBody body = outputStream -> excelExportService.writeModelAccuracy(accuracyRows, outputStream);
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
