package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.League;
import com.betai.domain.source.SourceTarget;

import java.util.Map;

public record TheSportsDbSnapshotMetadata(
        League league,
        SourceTarget sourceTarget,
        Map<String, String> requestParameters,
        String externalEntityId,
        String externalLeagueId,
        String season,
        String externalFixtureId,
        String externalEventId,
        String parserVersion,
        String processingStatus,
        String processingErrorSummary
) {
}
