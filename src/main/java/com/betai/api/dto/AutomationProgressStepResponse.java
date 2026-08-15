package com.betai.api.dto;

public record AutomationProgressStepResponse(
        String step,
        String status,
        int attempts,
        String summary,
        int warningCount,
        String failureReason
) {
}
