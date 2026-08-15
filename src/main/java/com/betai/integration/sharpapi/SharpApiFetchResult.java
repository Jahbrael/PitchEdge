package com.betai.integration.sharpapi;

import com.betai.domain.snapshot.ScrapeStatus;

import java.time.OffsetDateTime;

public record SharpApiFetchResult(
        String sourceUrl,
        ScrapeStatus status,
        Integer httpStatusCode,
        OffsetDateTime fetchedAt,
        long durationMs,
        String checksumSha256,
        String contentType,
        Long contentLength,
        String responseHeadersJson,
        String rawPayload,
        String errorMessage
) {
}
