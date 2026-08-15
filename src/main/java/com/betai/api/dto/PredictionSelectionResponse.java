package com.betai.api.dto;

import com.betai.domain.prediction.PredictionSelection;
import com.betai.domain.quality.ModelQualitySnapshot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record PredictionSelectionResponse(
        UUID selectionId,
        UUID matchId,
        String leagueCode,
        String leagueBadgeUrl,
        String leagueLogoUrl,
        String homeTeamBadgeUrl,
        String homeTeamLogoUrl,
        String awayTeamBadgeUrl,
        String awayTeamLogoUrl,
        String fixture,
        OffsetDateTime kickoffAt,
        String marketCode,
        String marketName,
        String marketFamily,
        String period,
        String direction,
        BigDecimal threshold,
        String teamScope,
        String predictedValue,
        String match,
        String league,
        String market,
        String teamOrPlayer,
        BigDecimal rawModelProbability,
        BigDecimal calibratedProbability,
        BigDecimal tunedModelProbability,
        BigDecimal dataQualityScore,
        Integer historicalSampleSize,
        String calibrationStatus,
        BigDecimal decimalOdds,
        BigDecimal bookmakerImpliedProbability,
        BigDecimal probabilityEdge,
        BigDecimal rankingScore,
        String reason,
        BigDecimal probability,
        BigDecimal rawProbability,
        BigDecimal probabilityAdjustment,
        String confidenceBand,
        UUID modelQualitySnapshotId,
        LocalDate modelQualityDate,
        Integer modelQualitySampleSize,
        BigDecimal modelQualityCalibrationError,
        String calibrationNote,
        UUID modelTuningProfileId,
        BigDecimal tuningAdjustment,
        String tuningNote,
        BigDecimal bestDecimalOdds,
        String bestOddsBookmaker,
        BigDecimal bestImpliedProbability,
        BigDecimal valueEdge,
        BigDecimal expectedValue,
        String valueRating,
        OffsetDateTime oddsCapturedAt,
        OffsetDateTime valueAssessedAt,
        String valueNote,
        String modelVersion,
        Integer requestedSeasonCount,
        Integer actualSeasonCountUsed,
        List<String> selectedSeasons,
        Integer completedMatchesUsed,
        Boolean fallbackApplied,
        String historicalDepthStatus,
        String marketSpecificDataCoverage,
        Integer homeScore,
        Integer awayScore,
        String matchStatus,
        String liveMinute
) {
    public static PredictionSelectionResponse from(PredictionSelection selection) {
        return from(selection, null, null, null, null, null);
    }

    public static PredictionSelectionResponse from(
            PredictionSelection selection,
            BigDecimal rankingScore,
            String reason,
            BigDecimal dataQualityScore,
            BigDecimal calibratedProbability,
            String calibrationStatus
    ) {
        var match = selection.getMatch();
        var market = selection.getMarketDefinition();
        ModelQualitySnapshot quality = selection.getModelQualitySnapshot();
        BigDecimal tunedModelProbability = selection.getProbability();
        BigDecimal rawModelProbability = selection.getRawProbability();
        BigDecimal resolvedCalibratedProbability = calibratedProbability == null
                ? inferCalibratedProbability(selection)
                : calibratedProbability;
        String fixture = match.getHomeTeam().getCanonicalName() + " vs " + match.getAwayTeam().getCanonicalName();
        String predictedEntity = predictedEntity(selection);
        return new PredictionSelectionResponse(
                selection.getId(),
                match.getId(),
                match.getLeague().getCode().name(),
                match.getLeague().getBadgeUrl(),
                match.getLeague().getLogoUrl(),
                match.getHomeTeam().getBadgeUrl(),
                match.getHomeTeam().getLogoUrl(),
                match.getAwayTeam().getBadgeUrl(),
                match.getAwayTeam().getLogoUrl(),
                fixture,
                match.getKickoffAt(),
                market.getCode().name(),
                market.getDisplayName(),
                market.getMarketFamily() == null ? market.getMarketType().name() : market.getMarketFamily().name(),
                market.getPeriod() == null ? null : market.getPeriod().name(),
                market.getDirection() == null ? null : market.getDirection().name(),
                market.getThreshold(),
                market.getTeamScope() == null ? null : market.getTeamScope().name(),
                selection.getPredictedValue(),
                fixture,
                match.getLeague().getCode().name(),
                market.getCode().name(),
                predictedEntity,
                rawModelProbability,
                resolvedCalibratedProbability,
                tunedModelProbability,
                dataQualityScore,
                quality == null ? null : quality.getSampleSize(),
                calibrationStatus,
                selection.getBestDecimalOdds(),
                selection.getBestImpliedProbability(),
                selection.getValueEdge(),
                rankingScore,
                reason,
                selection.getProbability(),
                selection.getRawProbability(),
                selection.getRawProbability() == null ? null : selection.getProbability().subtract(selection.getRawProbability()),
                selection.getConfidenceBand() == null ? null : selection.getConfidenceBand().name(),
                quality == null ? null : quality.getId(),
                quality == null ? null : quality.getQualityDate(),
                quality == null ? null : quality.getSampleSize(),
                quality == null ? null : quality.getCalibrationError(),
                selection.getCalibrationNote(),
                selection.getModelTuningProfile() == null ? null : selection.getModelTuningProfile().getId(),
                selection.getTuningAdjustment(),
                selection.getTuningNote(),
                selection.getBestDecimalOdds(),
                selection.getBestOddsBookmaker() == null ? null : selection.getBestOddsBookmaker().getDisplayName(),
                selection.getBestImpliedProbability(),
                selection.getValueEdge(),
                selection.getExpectedValue(),
                selection.getValueRating() == null ? null : selection.getValueRating().name(),
                selection.getOddsCapturedAt(),
                selection.getValueAssessedAt(),
                selection.getValueNote(),
                selection.getModelVersion(),
                selection.getRequestedSeasonCount(),
                selection.getActualSeasonCountUsed(),
                splitCsv(selection.getSelectedSeasons()),
                selection.getCompletedMatchesUsed(),
                selection.getFallbackApplied(),
                selection.getHistoricalDepthStatus(),
                selection.getMarketSpecificDataCoverage(),
                match.getHomeScore(),
                match.getAwayScore(),
                match.getStatus() == null ? null : match.getStatus().name(),
                match.getLiveMinute()
        );
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private static BigDecimal inferCalibratedProbability(PredictionSelection selection) {
        if (selection.getProbability() == null) {
            return null;
        }
        if (selection.getTuningAdjustment() == null) {
            return selection.getProbability();
        }
        return selection.getProbability().subtract(selection.getTuningAdjustment());
    }

    private static String predictedEntity(PredictionSelection selection) {
        if (selection.getMarketDefinition().getTeamScope() == null) {
            return selection.getPredictedValue();
        }
        return switch (selection.getMarketDefinition().getTeamScope()) {
            case HOME_TEAM -> selection.getMatch().getHomeTeam().getCanonicalName();
            case AWAY_TEAM -> selection.getMatch().getAwayTeam().getCanonicalName();
            case MATCH -> switch (selection.getMarketDefinition().getCode()) {
                case HOME_WIN, HOME_OR_DRAW, HOME_OR_AWAY, HOME_DRAW_NO_BET -> selection.getMatch().getHomeTeam().getCanonicalName();
                case AWAY_WIN, AWAY_OR_DRAW, AWAY_DRAW_NO_BET -> selection.getMatch().getAwayTeam().getCanonicalName();
                default -> selection.getPredictedValue();
            };
        };
    }
}
