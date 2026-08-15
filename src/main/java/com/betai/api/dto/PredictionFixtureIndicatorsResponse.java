package com.betai.api.dto;

public record PredictionFixtureIndicatorsResponse(
        boolean h2hAvailable,
        Integer h2hMatchCount,
        Integer homeLeaguePosition,
        Integer awayLeaguePosition,
        Integer leagueTableTeamCount,
        boolean partialSeasonData,
        String partialSeasonCoverage,
        Integer homeRecentFormPercentage,
        Integer awayRecentFormPercentage,
        Integer homeRecentFormSampleSize,
        Integer awayRecentFormSampleSize
) {
}
