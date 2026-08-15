package com.betai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AutomationProgressResponse(
        UUID runId,
        String status,
        String statusLabel,
        int progressPercentage,
        String currentStep,
        int completedSteps,
        int totalSteps,
        OffsetDateTime startTime,
        OffsetDateTime lastUpdateTime,
        OffsetDateTime completionTime,
        String errorMessage,
        List<AutomationProgressStepResponse> steps
) {
}
