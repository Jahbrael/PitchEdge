package com.betai.service;

import com.betai.api.dto.FixtureScoreRefreshSummary;
import com.betai.domain.league.LeagueCode;

import java.time.LocalDate;
import java.util.Set;

public interface FixtureScoreRefreshService {
    FixtureScoreRefreshSummary refreshScores(LocalDate date, Set<LeagueCode> leagues);
    FixtureScoreRefreshSummary refreshLiveScores(LocalDate date);
}
