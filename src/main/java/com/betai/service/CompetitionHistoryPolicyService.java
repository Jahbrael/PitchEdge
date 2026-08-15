package com.betai.service;

import com.betai.domain.league.CompetitionHistoryPolicy;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import org.springframework.stereotype.Service;

@Service
public class CompetitionHistoryPolicyService {

    public static final int INTERNATIONAL_HISTORY_WINDOW_YEARS = 4;

    public CompetitionHistoryPolicy policyFor(League league) {
        return league == null ? CompetitionHistoryPolicy.LEAGUE_SEASONS : policyFor(league.getCode());
    }

    public CompetitionHistoryPolicy policyFor(LeagueCode leagueCode) {
        if (leagueCode == LeagueCode.FIFA_WORLD_CUP_2026) {
            return CompetitionHistoryPolicy.INTERNATIONAL_FOUR_YEAR_WINDOW;
        }
        return CompetitionHistoryPolicy.LEAGUE_SEASONS;
    }
}
