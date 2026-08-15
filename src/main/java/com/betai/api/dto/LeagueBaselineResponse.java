package com.betai.api.dto;

import com.betai.domain.feature.LeagueBaseline;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeagueBaselineResponse(
        UUID id,
        String leagueCode,
        String seasonLabel,
        LocalDate calculationDate,
        int matchesSampled,
        BigDecimal avgHomeGoals,
        BigDecimal avgAwayGoals,
        BigDecimal avgTotalGoals,
        BigDecimal homeWinRate,
        BigDecimal drawRate,
        BigDecimal awayWinRate,
        BigDecimal bttsRate,
        BigDecimal over15Rate,
        BigDecimal over25Rate,
        BigDecimal under35Rate,
        BigDecimal avgTotalCorners,
        BigDecimal avgTotalYellowCards,
        BigDecimal redCardRate
) {
    public static LeagueBaselineResponse from(LeagueBaseline baseline) {
        return new LeagueBaselineResponse(
                baseline.getId(),
                baseline.getLeague().getCode().name(),
                baseline.getSeasonLabel(),
                baseline.getCalculationDate(),
                baseline.getMatchesSampled(),
                baseline.getAvgHomeGoals(),
                baseline.getAvgAwayGoals(),
                baseline.getAvgTotalGoals(),
                baseline.getHomeWinRate(),
                baseline.getDrawRate(),
                baseline.getAwayWinRate(),
                baseline.getBttsRate(),
                baseline.getOver15Rate(),
                baseline.getOver25Rate(),
                baseline.getUnder35Rate(),
                baseline.getAvgTotalCorners(),
                baseline.getAvgTotalYellowCards(),
                baseline.getRedCardRate()
        );
    }
}
