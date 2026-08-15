package com.betai.integration.thesportsdb.dto;

import com.betai.domain.league.LeagueCode;

public record TheSportsDbImportSummary(
        LeagueCode leagueCode,
        String externalLeagueId,
        String season,
        int seasonsImported,
        int teamsResolved,
        int teamsCreated,
        int teamsUnresolved,
        int fixturesCreated,
        int fixturesUpdated,
        int fixturesSkipped
) {
}
