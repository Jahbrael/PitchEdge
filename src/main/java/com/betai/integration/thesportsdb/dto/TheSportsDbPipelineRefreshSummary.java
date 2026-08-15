package com.betai.integration.thesportsdb.dto;

import java.util.List;

public record TheSportsDbPipelineRefreshSummary(
        int requestedLeagues,
        int resolvedLeagues,
        int unresolvedLeagues,
        int refreshedLeagues,
        int skippedLeagues,
        int failedLeagues,
        int requestedSeasons,
        int importedSeasons,
        int alreadyCompleteSeasons,
        int partiallyRefreshedSeasons,
        int fixturesCreated,
        int fixturesUpdated,
        int duplicateMatchesIgnored,
        int teamsResolved,
        int teamsCreated,
        int teamsUnresolved,
        int apiCallsMade,
        List<String> leagueSkipReasons,
        List<String> leagueFailureReasons
) {
    public TheSportsDbPipelineRefreshSummary {
        leagueSkipReasons = leagueSkipReasons == null ? List.of() : List.copyOf(leagueSkipReasons);
        leagueFailureReasons = leagueFailureReasons == null ? List.of() : List.copyOf(leagueFailureReasons);
    }

    public String summary() {
        return "requestedLeagues=" + requestedLeagues
                + ", resolvedLeagues=" + resolvedLeagues
                + ", unresolvedLeagues=" + unresolvedLeagues
                + ", refreshedLeagues=" + refreshedLeagues
                + ", skippedLeagues=" + skippedLeagues
                + ", failedLeagues=" + failedLeagues
                + ", requestedSeasons=" + requestedSeasons
                + ", importedSeasons=" + importedSeasons
                + ", alreadyCompleteSeasons=" + alreadyCompleteSeasons
                + ", partiallyRefreshedSeasons=" + partiallyRefreshedSeasons
                + ", fixturesCreated=" + fixturesCreated
                + ", fixturesUpdated=" + fixturesUpdated
                + ", duplicateMatchesIgnored=" + duplicateMatchesIgnored
                + ", teamsResolved=" + teamsResolved
                + ", teamsCreated=" + teamsCreated
                + ", teamsUnresolved=" + teamsUnresolved
                + ", apiCallsMade=" + apiCallsMade
                + ", leagueSkipReasons=" + leagueSkipReasons
                + ", leagueFailureReasons=" + leagueFailureReasons;
    }
}
