package com.betai.service;

import com.betai.api.dto.RankingMode;
import com.betai.api.dto.SelectionStrategy;
import com.betai.domain.prediction.PredictionSelection;

import java.math.BigDecimal;

record PredictionCandidate(
        PredictionSelection selection,
        SelectionStrategy strategy,
        RankingMode rankingMode,
        BigDecimal rankingScore,
        BigDecimal confidenceScore,
        BigDecimal dataQualityScore,
        BigDecimal calibrationQualityScore,
        BigDecimal marketReliabilityScore,
        BigDecimal calibratedProbability,
        String calibrationStatus,
        String reason
) {
}
