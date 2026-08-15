package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;

import java.util.Set;

public record TheSportsDbArtworkBackfillRequest(
        Set<LeagueCode> leagueCodes,
        LeagueCode leagueCode,
        Integer limit,
        String teamExternalKey,
        Boolean dryRun,
        Boolean teamsOnly,
        Boolean leaguesOnly
) {
    public TheSportsDbArtworkBackfillRequest(Set<LeagueCode> leagueCodes) {
        this(leagueCodes, null, null, null, null, null, null);
    }
}
