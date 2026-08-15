package com.betai.api.dto;

import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UpcomingFixtureResponse(
        UUID matchId,
        String leagueCode,
        String seasonLabel,
        LocalDate matchDate,
        OffsetDateTime kickoffAt,
        MatchStatus status,
        String homeTeam,
        String awayTeam,
        String sourceFixtureKey
) {
    public static UpcomingFixtureResponse from(Match match) {
        return new UpcomingFixtureResponse(
                match.getId(),
                match.getLeague().getCode().name(),
                match.getSeasonLabel(),
                match.getMatchDate(),
                match.getKickoffAt(),
                match.getStatus(),
                match.getHomeTeam().getCanonicalName(),
                match.getAwayTeam().getCanonicalName(),
                match.getSourceFixtureKey()
        );
    }
}
