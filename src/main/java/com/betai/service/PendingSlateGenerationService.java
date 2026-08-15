package com.betai.service;

import com.betai.api.dto.PendingSlateGenerationRequest;
import com.betai.api.dto.PredictionGenerationResponse;

public interface PendingSlateGenerationService {

    PredictionGenerationResponse generatePendingSlate(PendingSlateGenerationRequest request);
}
