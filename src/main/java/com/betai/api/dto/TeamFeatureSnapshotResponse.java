package com.betai.api.dto;

import com.betai.domain.feature.TeamFeatureSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TeamFeatureSnapshotResponse(
        UUID id,
        String leagueCode,
        UUID teamId,
        String teamName,
        String seasonLabel,
        LocalDate calculationDate,
        int matchesPlayed,
        int homeMatches,
        int awayMatches,
        int last5Matches,
        int last10Matches,
        BigDecimal pointsPerMatch,
        BigDecimal last5PointsPerMatch,
        BigDecimal last10PointsPerMatch,
        BigDecimal goalsForPerMatch,
        BigDecimal goalsAgainstPerMatch,
        BigDecimal cleanSheetRate,
        BigDecimal failedToScoreRate,
        BigDecimal bttsRate,
        BigDecimal over15Rate,
        BigDecimal over25Rate,
        BigDecimal under35Rate,
        BigDecimal cornersForPerMatch,
        BigDecimal cornersAgainstPerMatch,
        BigDecimal yellowCardsForPerMatch,
        BigDecimal yellowCardsAgainstPerMatch,
        BigDecimal redCardRate,
        BigDecimal formScore
) {
    public static TeamFeatureSnapshotResponse from(TeamFeatureSnapshot snapshot) {
        return new TeamFeatureSnapshotResponse(
                snapshot.getId(),
                snapshot.getLeague().getCode().name(),
                snapshot.getTeam().getId(),
                snapshot.getTeam().getCanonicalName(),
                snapshot.getSeasonLabel(),
                snapshot.getCalculationDate(),
                snapshot.getMatchesPlayed(),
                snapshot.getHomeMatches(),
                snapshot.getAwayMatches(),
                snapshot.getLast5Matches(),
                snapshot.getLast10Matches(),
                snapshot.getPointsPerMatch(),
                snapshot.getLast5PointsPerMatch(),
                snapshot.getLast10PointsPerMatch(),
                snapshot.getGoalsForPerMatch(),
                snapshot.getGoalsAgainstPerMatch(),
                snapshot.getCleanSheetRate(),
                snapshot.getFailedToScoreRate(),
                snapshot.getBttsRate(),
                snapshot.getOver15Rate(),
                snapshot.getOver25Rate(),
                snapshot.getUnder35Rate(),
                snapshot.getCornersForPerMatch(),
                snapshot.getCornersAgainstPerMatch(),
                snapshot.getYellowCardsForPerMatch(),
                snapshot.getYellowCardsAgainstPerMatch(),
                snapshot.getRedCardRate(),
                snapshot.getFormScore()
        );
    }
}
