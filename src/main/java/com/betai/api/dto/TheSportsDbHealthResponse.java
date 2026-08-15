package com.betai.api.dto;

import java.time.OffsetDateTime;

public record TheSportsDbHealthResponse(
        boolean enabled,
        boolean apiKeyConfigured,
        String baseUrl,
        int requestsPerMinute,
        int requestsMadeInCurrentMinute,
        int tooManyRequestsCount,
        OffsetDateTime lastSuccessfulRequestAt,
        long unresolvedTeamMappings
) {
}
