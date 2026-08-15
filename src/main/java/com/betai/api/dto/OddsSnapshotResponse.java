package com.betai.api.dto;

import com.betai.domain.odds.OddsSnapshot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OddsSnapshotResponse(
        UUID oddsSnapshotId,
        UUID matchId,
        String leagueCode,
        String fixture,
        OffsetDateTime kickoffAt,
        String marketCode,
        String marketName,
        String bookmakerCode,
        String bookmakerName,
        BigDecimal decimalOdds,
        BigDecimal impliedProbability,
        OffsetDateTime capturedAt,
        String sourceName,
        String sourceUrl,
        String rawPayloadReference
) {
    public static OddsSnapshotResponse from(OddsSnapshot snapshot) {
        var match = snapshot.getMatch();
        var market = snapshot.getMarketDefinition();
        var bookmaker = snapshot.getBookmaker();
        return new OddsSnapshotResponse(
                snapshot.getId(),
                match.getId(),
                match.getLeague().getCode().name(),
                match.getHomeTeam().getCanonicalName() + " vs " + match.getAwayTeam().getCanonicalName(),
                match.getKickoffAt(),
                market.getCode().name(),
                market.getDisplayName(),
                bookmaker.getCode(),
                bookmaker.getDisplayName(),
                snapshot.getDecimalOdds(),
                snapshot.getImpliedProbability(),
                snapshot.getCapturedAt(),
                snapshot.getSourceName(),
                snapshot.getSourceUrl(),
                snapshot.getRawPayloadReference()
        );
    }
}
