package com.betai.service;

import com.betai.api.dto.PredictionGenerationRequest;
import com.betai.api.dto.PredictionGenerationResponse;

public interface PredictionGenerationService {

    PredictionGenerationResponse generatePredictions(PredictionGenerationRequest request);
}
