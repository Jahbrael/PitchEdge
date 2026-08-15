package com.betai.service;

import com.betai.api.dto.PredictionRequest;
import com.betai.api.dto.PredictionResponse;

public interface PredictionFormService {

    PredictionResponse generatePredictions(PredictionRequest request);
}
