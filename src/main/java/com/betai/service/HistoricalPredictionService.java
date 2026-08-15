package com.betai.service;

import com.betai.api.dto.HistoricalPredictionRequest;
import com.betai.api.dto.HistoricalPredictionResponse;

public interface HistoricalPredictionService {

    HistoricalPredictionResponse generateHistoricalPredictions(HistoricalPredictionRequest request);
}
