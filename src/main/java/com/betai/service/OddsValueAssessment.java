package com.betai.service;

import com.betai.domain.odds.ValueRating;

import java.math.BigDecimal;

public record OddsValueAssessment(
        BigDecimal impliedProbability,
        BigDecimal valueEdge,
        BigDecimal expectedValue,
        ValueRating valueRating,
        String note
) {
}
