package com.betai.integration.thesportsdb.dto;

import java.math.BigDecimal;

public record TheSportsDbEventStatisticDto(
        String teamName,
        String statisticCode,
        String statisticName,
        BigDecimal numericValue,
        String textValue,
        String period,
        String sourceStatisticName
) {
}
