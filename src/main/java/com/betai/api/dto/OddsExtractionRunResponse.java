package com.betai.api.dto;

import com.betai.domain.odds.OddsExtractionRun;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OddsExtractionRunResponse(
        UUID oddsExtractionRunId,
        UUID rawSnapshotId,
        String leagueCode,
        String sourceTargetName,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        int rowsSeen,
        int rowsAccepted,
        int snapshotsImported,
        int selectionsUpdated,
        int validationErrorCount,
        String failureReason,
        boolean cacheReused
) {
    public static OddsExtractionRunResponse from(OddsExtractionRun run, boolean cacheReused) {
        return new OddsExtractionRunResponse(
                run.getId(),
                run.getRawSnapshot().getId(),
                run.getRawSnapshot().getLeague().getCode().name(),
                run.getRawSnapshot().getSourceTarget().getName(),
                run.getExtractionStatus().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                run.getRowsSeen(),
                run.getRowsAccepted(),
                run.getSnapshotsImported(),
                run.getSelectionsUpdated(),
                run.getValidationErrorCount(),
                run.getFailureReason(),
                cacheReused
        );
    }
}
