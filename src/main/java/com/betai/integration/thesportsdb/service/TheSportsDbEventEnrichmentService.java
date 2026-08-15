package com.betai.integration.thesportsdb.service;

import com.betai.integration.thesportsdb.dto.TheSportsDbEventStatisticsImportSummary;

import java.util.UUID;

public interface TheSportsDbEventEnrichmentService {

    TheSportsDbEventStatisticsImportSummary importEventStatistics(String externalEventId);

    TheSportsDbEventStatisticsImportSummary importEventStatisticsForMatch(UUID matchId, String externalEventId);
}
