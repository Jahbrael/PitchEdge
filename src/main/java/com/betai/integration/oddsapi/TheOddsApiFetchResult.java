package com.betai.integration.oddsapi;

import com.betai.domain.snapshot.ScrapeStatus;

import java.time.OffsetDateTime;

public record TheOddsApiFetchResult(
        String sourceUrl,
        ScrapeStatus status,
        Integer httpStatusCode,
        OffsetDateTime fetchedAt,
        Long durationMs,
        String checksumSha256,
        String contentType,
        Long contentLength,
        String responseHeadersJson,
        String rawPayload,
        String errorMessage
) {
}
