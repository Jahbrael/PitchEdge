package com.betai.api.dto;

import com.betai.domain.snapshot.ScrapeStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PreMatchOddsSourceRefreshResponse(
        UUID sourceTargetId,
        UUID rawSnapshotId,
        String leagueCode,
        String sourceName,
        String sourceUrl,
        ScrapeStatus scrapeStatus,
        Integer httpStatusCode,
        OffsetDateTime fetchedAt,
        Long durationMs,
        boolean cacheReused,
        String message
) {
}
