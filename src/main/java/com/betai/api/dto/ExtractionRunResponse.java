package com.betai.api.dto;

import com.betai.domain.extraction.ExtractionRun;
import com.betai.domain.extraction.ExtractionStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ExtractionRunResponse(
        UUID extractionRunId,
        UUID rawSnapshotId,
        String leagueCode,
        String sourceTargetName,
        ExtractionStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        int rowsSeen,
        int rowsAccepted,
        int teamsUpserted,
        int matchesUpserted,
        int statsUpserted,
        int validationErrorCount,
        String failureReason,
        boolean cacheReused,
        List<ExtractionValidationErrorResponse> validationErrors
) {
    public static ExtractionRunResponse from(
            ExtractionRun run,
            boolean cacheReused,
            List<ExtractionValidationErrorResponse> validationErrors
    ) {
        var snapshot = run.getRawSnapshot();
        return new ExtractionRunResponse(
                run.getId(),
                snapshot.getId(),
                snapshot.getLeague().getCode().name(),
                snapshot.getSourceTarget().getName(),
                run.getExtractionStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                run.getRowsSeen(),
                run.getRowsAccepted(),
                run.getTeamsUpserted(),
                run.getMatchesUpserted(),
                run.getStatsUpserted(),
                run.getValidationErrorCount(),
                run.getFailureReason(),
                cacheReused,
                validationErrors
        );
    }
}
