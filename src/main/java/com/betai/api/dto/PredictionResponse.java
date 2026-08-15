package com.betai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PredictionResponse(
        UUID requestId,
        OffsetDateTime generatedAt,
        PredictionRequest input,
        String modelVersion,
        List<String> matchStatusesUsed,
        int fixturesConsidered,
        int candidateSelections,
        int requestedMinimumSelections,
        int requestedMaximumSelections,
        int qualifiedSelectionsFound,
        int returnedSelections,
        int requestedSeasonCount,
        boolean defaultSeasonCountApplied,
        List<String> leaguesWithFullRequestedHistory,
        List<String> leaguesUsingFallbackHistory,
        PredictionResponseStatus status,
        String warningMessage,
        int selectionsReturned,
        List<PredictionBatchResponse> batches,
        List<String> warnings,
        Map<UUID, PredictionFixtureIndicatorsResponse> fixtureIndicators
) {
    public PredictionResponse {
        fixtureIndicators = fixtureIndicators == null ? Map.of() : Map.copyOf(fixtureIndicators);
    }
}
