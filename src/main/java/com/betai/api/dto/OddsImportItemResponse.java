package com.betai.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OddsImportItemResponse(
        int itemIndex,
        String status,
        UUID oddsSnapshotId,
        UUID matchId,
        String fixture,
        String marketCode,
        String bookmakerCode,
        BigDecimal decimalOdds,
        BigDecimal impliedProbability,
        int selectionsUpdated,
        String message
) {
}
