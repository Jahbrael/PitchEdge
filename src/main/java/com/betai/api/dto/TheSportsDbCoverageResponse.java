package com.betai.api.dto;

import com.betai.domain.source.CoverageLevel;
import com.betai.domain.source.LeagueSeasonCoverage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TheSportsDbCoverageResponse(
        String leagueCode,
        String season,
        boolean hasFixtures,
        boolean hasResults,
        boolean hasEventStatistics,
        boolean hasCorners,
        boolean hasCards,
        boolean hasShots,
        int completedEventsChecked,
        int eventsWithStatistics,
        BigDecimal coveragePercentage,
        CoverageLevel statisticsCoverageLevel,
        CoverageLevel cornersCoverageLevel,
        CoverageLevel cardsCoverageLevel,
        OffsetDateTime lastVerifiedAt
) {
    public static TheSportsDbCoverageResponse from(LeagueSeasonCoverage coverage) {
        return new TheSportsDbCoverageResponse(
                coverage.getLeague().getCode().name(),
                coverage.getSeasonLabel(),
                coverage.isHasFixtures(),
                coverage.isHasResults(),
                coverage.isHasEventStatistics(),
                coverage.isHasCorners(),
                coverage.isHasCards(),
                coverage.isHasShots(),
                coverage.getCompletedEventsChecked(),
                coverage.getEventsWithStatistics(),
                coverage.getCoveragePercentage(),
                coverage.getStatisticsCoverageLevel(),
                coverage.getCornersCoverageLevel(),
                coverage.getCardsCoverageLevel(),
                coverage.getLastVerifiedAt()
        );
    }
}
