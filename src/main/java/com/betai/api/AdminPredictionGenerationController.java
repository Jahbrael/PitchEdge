package com.betai.api;

import com.betai.api.dto.PendingSlateGenerationRequest;
import com.betai.api.dto.PredictionGenerationRequest;
import com.betai.api.dto.PredictionGenerationResponse;
import com.betai.api.dto.HistoricalPredictionRequest;
import com.betai.api.dto.HistoricalPredictionResponse;
import com.betai.service.HistoricalPredictionService;
import com.betai.service.PendingSlateGenerationService;
import com.betai.service.PredictionGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/predictions")
public class AdminPredictionGenerationController {

    private final PredictionGenerationService predictionGenerationService;
    private final PendingSlateGenerationService pendingSlateGenerationService;
    private final HistoricalPredictionService historicalPredictionService;

    @PostMapping("/generate")
    public ResponseEntity<PredictionGenerationResponse> generatePredictions(
            @Valid @RequestBody PredictionGenerationRequest request
    ) {
        return ResponseEntity.ok(predictionGenerationService.generatePredictions(request));
    }

    @PostMapping("/generate-pending-slate")
    public ResponseEntity<PredictionGenerationResponse> generatePendingSlate(
            @Valid @RequestBody PendingSlateGenerationRequest request
    ) {
        return ResponseEntity.ok(pendingSlateGenerationService.generatePendingSlate(request));
    }

    @PostMapping("/historical")
    public ResponseEntity<HistoricalPredictionResponse> generateHistoricalPredictions(
            @Valid @RequestBody HistoricalPredictionRequest request
    ) {
        return ResponseEntity.ok(historicalPredictionService.generateHistoricalPredictions(request));
    }
}
