package com.betai.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DashboardRunSummaryResponse(
        String stage,
        UUID runId,
        String leagueCode,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        String summary,
        String failureReason,
        Integer attempts
) {
}
