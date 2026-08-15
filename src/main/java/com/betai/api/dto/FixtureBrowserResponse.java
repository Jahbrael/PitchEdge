package com.betai.api.dto;

import com.betai.domain.match.MatchStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FixtureBrowserResponse(
        UUID matchId,
        String leagueCode,
        String leagueName,
        String leagueBadgeUrl,
        String leagueLogoUrl,
        String homeTeam,
        String homeTeamBadgeUrl,
        String homeTeamLogoUrl,
        String awayTeam,
        String awayTeamBadgeUrl,
        String awayTeamLogoUrl,
        OffsetDateTime kickoffTime,
        MatchStatus status,
        Integer homeScore,
        Integer awayScore,
        String liveMinute,
        String venue,
        boolean hasPredictions,
        String predictionStatus,
        boolean hasOdds,
        String oddsProvider,
        OffsetDateTime latestOddsSnapshotTime,
        OffsetDateTime lastRefreshedTime
) {}
