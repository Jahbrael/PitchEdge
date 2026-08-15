package com.betai.api.dto;

import java.math.BigDecimal;

public record BatchRiskMetricsResponse(
        BigDecimal jointProbability,
        BigDecimal averageIndividualProbability,
        BigDecimal minimumIndividualProbability,
        BigDecimal maximumIndividualProbability,
        Integer pricedSelectionCount,
        Integer positiveValueSelectionCount,
        BigDecimal averageExpectedValue,
        BigDecimal minimumExpectedValue,
        BigDecimal maximumExpectedValue,
        BigDecimal aggregateDecimalOdds,
        BigDecimal accumulatorExpectedValue,
        RiskBand riskBand,
        String varianceWarning
) {
}
