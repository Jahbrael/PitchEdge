package com.betai.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FullPipelineResponse(
        UUID pipelineRunId,
        LocalDate pipelineDate,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        String modelVersion,
        List<String> leagueCodes,
        List<PipelineStepResponse> steps,
        String failureReason
) {
}
