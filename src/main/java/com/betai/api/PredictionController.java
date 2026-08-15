package com.betai.api;

import com.betai.api.dto.PredictionRequest;
import com.betai.api.dto.PredictionResponse;
import com.betai.export.ExcelExportService;
import com.betai.security.CustomUserDetails;
import com.betai.service.PredictionFormService;
import com.betai.service.UserHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.format.DateTimeFormatter;

import com.betai.api.dto.PredictionSelectionResponse;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.repository.PredictionSelectionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestParam;
import com.betai.api.dto.details.FixturePredictionDetailsResponse;
import com.betai.service.FixturePredictionDetailsService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/predictions")
public class PredictionController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final PredictionFormService predictionFormService;
    private final ExcelExportService excelExportService;
    private final UserHistoryService userHistoryService;
    private final PredictionSelectionRepository predictionSelectionRepository;
    private final FixturePredictionDetailsService fixturePredictionDetailsService;
    private final com.betai.service.PredictionRunCacheService predictionRunCacheService;

    @GetMapping("/fixtures/{matchId}/details")
    public ResponseEntity<FixturePredictionDetailsResponse> getFixtureDetails(
            @PathVariable UUID matchId,
            @RequestParam String modelVersion,
            @RequestParam(required = false) String recommendedMarketCode,
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) UUID selectionId
    ) {
        return ResponseEntity.ok(fixturePredictionDetailsService.getFixtureDetails(matchId, modelVersion, recommendedMarketCode, runId, selectionId));
    }

    @GetMapping("/fixtures/{matchId}")
    public ResponseEntity<List<PredictionSelectionResponse>> getPredictionsForFixture(
            @PathVariable UUID matchId,
            @RequestParam(defaultValue = "v1") String modelVersion
    ) {
        return ResponseEntity.ok(predictionSelectionRepository
                .findExistingForMatchesAndModel(List.of(matchId), modelVersion)
                .stream()
                .sorted(java.util.Comparator.comparing(PredictionSelection::getProbability, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(PredictionSelectionResponse::from)
                .toList());
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<PredictionResponse> getPredictionRun(@PathVariable UUID runId) {
        PredictionResponse response = predictionRunCacheService.get(runId);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/form")
    public ResponseEntity<PredictionResponse> submitPredictionForm(
            @Valid @RequestBody PredictionRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        PredictionResponse response = predictionFormService.generatePredictions(request);
        if (user != null) {
            userHistoryService.savePredictionResponse(user.getId(), response);
        }
        predictionRunCacheService.put(response.requestId(), response);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/form/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<StreamingResponseBody> exportPredictionForm(@Valid @RequestBody PredictionRequest request) {
        PredictionResponse response = predictionFormService.generatePredictions(request);
        String filename = "pitchedge-predictions-" + FILE_DATE.format(response.input().fixtureDateFrom())
                + "-" + FILE_DATE.format(response.input().fixtureDateTo()) + ".xlsx";
        StreamingResponseBody body = outputStream -> excelExportService.writePredictionResponse(response, outputStream);
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
