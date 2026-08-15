package com.betai.api.dto;

import java.time.OffsetDateTime;

public record PipelineStepResponse(
        String step,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        String summary,
        String failureReason
) {
}
