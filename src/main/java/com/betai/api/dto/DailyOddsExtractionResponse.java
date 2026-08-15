package com.betai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DailyOddsExtractionResponse(
        UUID requestId,
        OffsetDateTime triggeredAt,
        List<OddsExtractionRunResponse> oddsExtractionRuns,
        List<String> warnings
) {
}
