package com.betai.scraping;

import com.betai.domain.snapshot.ScrapeStatus;

import java.time.OffsetDateTime;

public record ScrapeOutcome(
        String sourceUrl,
        ScrapeStatus scrapeStatus,
        Integer httpStatusCode,
        OffsetDateTime fetchedAt,
        Long durationMs,
        String checksumSha256,
        String contentType,
        Long contentLength,
        String responseHeadersJson,
        String rawPayload,
        String extractedText,
        String errorMessage
) {
}
