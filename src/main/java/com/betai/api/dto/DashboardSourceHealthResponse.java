package com.betai.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DashboardSourceHealthResponse(
        UUID sourceTargetId,
        String leagueCode,
        String sourceType,
        String name,
        String targetSeasonLabel,
        boolean active,
        boolean robotsTxtRequired,
        int rateLimitPerMinute,
        int timeoutMs,
        BigDecimal reliabilityScore,
        int fallbackPriority,
        boolean systemDisabled,
        OffsetDateTime quarantinedUntil,
        String healthNote,
        int consecutiveFailures,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime lastFailureAt,
        String lastFailureReason
) {
}
