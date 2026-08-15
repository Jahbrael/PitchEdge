package com.betai.api.dto;

import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.snapshot.ScrapeStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RawSnapshotResponse(
        UUID id,
        UUID sourceTargetId,
        String sourceTargetName,
        String leagueCode,
        UUID refreshLogId,
        LocalDate snapshotDate,
        String sourceUrl,
        ScrapeStatus scrapeStatus,
        Integer httpStatusCode,
        OffsetDateTime fetchedAt,
        Long durationMs,
        String checksumSha256,
        String contentType,
        Long contentLength,
        String errorMessage
) {
    public static RawSnapshotResponse from(RawSnapshot snapshot) {
        return new RawSnapshotResponse(
                snapshot.getId(),
                snapshot.getSourceTarget().getId(),
                snapshot.getSourceTarget().getName(),
                snapshot.getLeague().getCode().name(),
                snapshot.getDataRefreshLog() == null ? null : snapshot.getDataRefreshLog().getId(),
                snapshot.getSnapshotDate(),
                snapshot.getSourceUrl(),
                snapshot.getScrapeStatus(),
                snapshot.getHttpStatusCode(),
                snapshot.getFetchedAt(),
                snapshot.getDurationMs(),
                snapshot.getChecksumSha256(),
                snapshot.getContentType(),
                snapshot.getContentLength(),
                snapshot.getErrorMessage()
        );
    }
}
