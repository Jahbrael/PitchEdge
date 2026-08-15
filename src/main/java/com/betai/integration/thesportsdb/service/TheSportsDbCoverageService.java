package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.source.LeagueSeasonCoverage;

public interface TheSportsDbCoverageService {

    LeagueSeasonCoverage recalculate(LeagueCode leagueCode, String seasonLabel);
}
