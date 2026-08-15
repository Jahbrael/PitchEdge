package com.betai.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HistoricalPredictionResponse(
        UUID requestId,
        OffsetDateTime triggeredAt,
        LocalDate calculationDate,
        LocalDate matchDateFrom,
        LocalDate matchDateTo,
        String baseModelVersion,
        String historicalModelVersion,
        DailyFeatureGenerationResponse features,
        PredictionGenerationResponse predictions,
        SettlementResponse settlement,
        List<String> warnings
) {
}
