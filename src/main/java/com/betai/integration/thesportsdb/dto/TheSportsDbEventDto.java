package com.betai.integration.thesportsdb.dto;

import com.betai.domain.match.MatchStatus;

import java.time.OffsetDateTime;

public record TheSportsDbEventDto(
        String externalEventId,
        String externalLeagueId,
        String season,
        String externalHomeTeamId,
        String externalAwayTeamId,
        String homeTeamName,
        String awayTeamName,
        OffsetDateTime kickoffAt,
        MatchStatus status,
        Integer homeScore,
        Integer awayScore,
        String roundLabel,
        String venue,
        String originalDate,
        String originalTime,
        String referee,
        Integer homeHalfTimeScore,
        Integer awayHalfTimeScore,
        String progress,
        String strHomeTeamBadge,
        String strAwayTeamBadge,
        String strLeagueBadge,
        String strPoster,
        String strThumb
) {
}
