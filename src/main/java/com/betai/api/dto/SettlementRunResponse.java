package com.betai.api.dto;

import com.betai.domain.settlement.SettlementRun;
import com.betai.domain.settlement.SettlementStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SettlementRunResponse(
        UUID settlementRunId,
        String leagueCode,
        String modelVersion,
        LocalDate settlementDate,
        LocalDate matchDateFrom,
        LocalDate matchDateTo,
        SettlementStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        int selectionsEvaluated,
        int wonCount,
        int lostCount,
        int voidCount,
        int skippedCount,
        String failureReason
) {
    public static SettlementRunResponse from(SettlementRun run) {
        return new SettlementRunResponse(
                run.getId(),
                run.getLeague().getCode().name(),
                run.getModelVersion(),
                run.getSettlementDate(),
                run.getMatchDateFrom(),
                run.getMatchDateTo(),
                run.getSettlementStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                run.getSelectionsEvaluated(),
                run.getWonCount(),
                run.getLostCount(),
                run.getVoidCount(),
                run.getSkippedCount(),
                run.getFailureReason()
        );
    }
}
