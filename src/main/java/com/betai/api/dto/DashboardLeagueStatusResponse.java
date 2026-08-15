package com.betai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DashboardLeagueStatusResponse(
        String leagueCode,
        String name,
        String country,
        String currentSeason,
        String historyPolicy,
        int requiredSeasonCount,
        int importedSeasonCount,
        List<String> importedSeasonLabels,
        List<DashboardLeagueSeasonStatusResponse> seasonBreakdowns,
        String historyStatus,
        String sourceUsed,
        boolean active,
        boolean scrapeEnabled,
        long teams,
        long matches,
        long scheduledMatches,
        long finishedMatches,
        long sourceTargets,
        long activeSourceTargets,
        String dataCoverageStatus,
        String dataCoverageMessage,
        String latestRefreshStatus,
        OffsetDateTime latestRefreshStartedAt,
        OffsetDateTime latestRefreshFinishedAt,
        Long latestRefreshDurationMs,
        String latestRefreshFailureReason
) {
}
