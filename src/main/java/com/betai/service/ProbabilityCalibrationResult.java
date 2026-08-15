package com.betai.service;

import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.quality.ModelQualitySnapshot;

import java.math.BigDecimal;

public record ProbabilityCalibrationResult(
        BigDecimal rawProbability,
        BigDecimal calibratedProbability,
        PredictionConfidenceBand confidenceBand,
        ModelQualitySnapshot modelQualitySnapshot,
        String calibrationNote
) {
}
