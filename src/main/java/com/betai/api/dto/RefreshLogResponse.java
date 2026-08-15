package com.betai.api.dto;

import com.betai.domain.refresh.DataRefreshLog;
import com.betai.domain.refresh.RefreshStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RefreshLogResponse(
        UUID refreshLogId,
        String leagueCode,
        LocalDate refreshDate,
        RefreshStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        int sourceCount,
        Long recordsIngested,
        Long recordsRejected,
        String rawPayloadReference,
        String payloadChecksum,
        boolean cacheReused,
        String message
) {
    public static RefreshLogResponse from(DataRefreshLog log, boolean cacheReused, String message) {
        return new RefreshLogResponse(
                log.getId(),
                log.getLeague().getCode().name(),
                log.getRefreshDate(),
                log.getRefreshStatus(),
                log.getStartedAt(),
                log.getFinishedAt(),
                log.getDurationMs(),
                log.getSourceCount(),
                log.getRecordsIngested(),
                log.getRecordsRejected(),
                log.getRawPayloadReference(),
                log.getPayloadChecksum(),
                cacheReused,
                message
        );
    }
}
