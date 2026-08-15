package com.betai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DashboardOverviewResponse(
        OffsetDateTime generatedAt,
        String status,
        DashboardTotalsResponse totals,
        List<DashboardLeagueStatusResponse> leagues,
        List<DashboardSourceHealthResponse> sources,
        List<DashboardRunSummaryResponse> recentRuns,
        List<String> alerts
) {
}
