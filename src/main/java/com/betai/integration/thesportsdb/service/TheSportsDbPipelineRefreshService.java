package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.LeagueCode;
import com.betai.integration.thesportsdb.dto.TheSportsDbPipelineRefreshSummary;

import java.util.Set;

public interface TheSportsDbPipelineRefreshService {

    default TheSportsDbPipelineRefreshSummary refresh(Set<LeagueCode> leagueCodes) {
        return refresh(leagueCodes, null);
    }

    TheSportsDbPipelineRefreshSummary refresh(Set<LeagueCode> leagueCodes, Integer requestedSeasonCount);
}
