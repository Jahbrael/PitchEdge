package com.betai.api.dto;

import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SourceTargetResponse(
        UUID id,
        String leagueCode,
        SourceType sourceType,
        String name,
        String urlTemplate,
        String sourceSeasonToken,
        String targetSeasonLabel,
        RenderMode renderMode,
        boolean active,
        boolean robotsTxtRequired,
        String userAgent,
        int rateLimitPerMinute,
        int timeoutMs,
        BigDecimal reliabilityScore,
        int fallbackPriority,
        boolean systemDisabled,
        OffsetDateTime quarantinedUntil,
        String healthNote,
        String selectorsJson,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime lastFailureAt,
        int consecutiveFailures,
        String lastFailureReason
) {
    public static SourceTargetResponse from(SourceTarget sourceTarget) {
        return new SourceTargetResponse(
                sourceTarget.getId(),
                sourceTarget.getLeague().getCode().name(),
                sourceTarget.getSourceType(),
                sourceTarget.getName(),
                sourceTarget.getUrlTemplate(),
                sourceTarget.getSourceSeasonToken(),
                sourceTarget.getTargetSeasonLabel(),
                sourceTarget.getRenderMode(),
                sourceTarget.isActive(),
                sourceTarget.isRobotsTxtRequired(),
                sourceTarget.getUserAgent(),
                sourceTarget.getRateLimitPerMinute(),
                sourceTarget.getTimeoutMs(),
                sourceTarget.getReliabilityScore(),
                sourceTarget.getFallbackPriority(),
                sourceTarget.isSystemDisabled(),
                sourceTarget.getQuarantinedUntil(),
                sourceTarget.getHealthNote(),
                sourceTarget.getSelectorsJson(),
                sourceTarget.getLastSuccessAt(),
                sourceTarget.getLastFailureAt(),
                sourceTarget.getConsecutiveFailures(),
                sourceTarget.getLastFailureReason()
        );
    }
}
