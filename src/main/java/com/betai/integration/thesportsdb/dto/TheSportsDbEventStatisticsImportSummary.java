package com.betai.integration.thesportsdb.dto;

public record TheSportsDbEventStatisticsImportSummary(
        String externalEventId,
        int statisticsImported,
        int fixedMatchStatisticsUpdated
) {
}
