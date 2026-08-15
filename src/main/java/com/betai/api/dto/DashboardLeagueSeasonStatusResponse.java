package com.betai.api.dto;

import java.time.LocalDate;

public record DashboardLeagueSeasonStatusResponse(
        String seasonLabel,
        long matches,
        long finishedMatches,
        long scheduledMatches,
        LocalDate firstMatchDate,
        LocalDate lastMatchDate
) {
}
