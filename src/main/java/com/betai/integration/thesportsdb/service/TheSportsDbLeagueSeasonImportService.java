package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.LeagueCode;
import com.betai.integration.thesportsdb.dto.TheSportsDbImportSummary;

public interface TheSportsDbLeagueSeasonImportService {

    default TheSportsDbImportSummary importLeagueSeason(LeagueCode leagueCode, String externalLeagueId, String season) {
        return importLeagueSeason(
                leagueCode,
                externalLeagueId,
                season,
                SeasonLabelStrategy.USE_EVENT_SEASON_WHEN_PRESENT
        );
    }

    TheSportsDbImportSummary importLeagueSeason(
            LeagueCode leagueCode,
            String externalLeagueId,
            String season,
            SeasonLabelStrategy seasonLabelStrategy
    );

    enum SeasonLabelStrategy {
        USE_EVENT_SEASON_WHEN_PRESENT,
        PRESERVE_REQUESTED_SEASON
    }
}
