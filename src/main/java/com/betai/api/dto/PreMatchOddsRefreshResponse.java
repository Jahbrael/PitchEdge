package com.betai.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PreMatchOddsRefreshResponse(
        UUID requestId,
        OffsetDateTime triggeredAt,
        LocalDate refreshDate,
        int sourcesConsidered,
        int successfulSnapshots,
        int cacheReusedSnapshots,
        int failedSnapshots,
        List<PreMatchOddsSourceRefreshResponse> sourceRefreshes,
        DailyOddsExtractionResponse extraction,
        List<String> warnings
) {
}
