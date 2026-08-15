package com.betai.service;

import com.betai.api.dto.AutomationProgressResponse;
import com.betai.api.dto.AutomationProgressStepResponse;
import com.betai.domain.automation.AutomationRun;
import com.betai.domain.automation.AutomationRunStatus;
import com.betai.repository.AutomationRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AutomationProgressService {

    private static final int KNOWN_PIPELINE_STEP_COUNT = 8;

    private final AutomationRunRepository automationRunRepository;
    private final ObjectMapper objectMapper;

    public AutomationProgressResponse latestProgress() {
        return automationRunRepository.findTopByOrderByStartedAtDesc()
                .map(this::response)
                .orElseGet(this::notStarted);
    }

    private AutomationProgressResponse response(AutomationRun run) {
        List<AutomationProgressStepResponse> steps = parseSteps(run.getStepSummaryJson());
        int totalSteps = run.getTotalSteps() > 0 ? run.getTotalSteps() : KNOWN_PIPELINE_STEP_COUNT;
        int completedSteps = run.getCompletedSteps() > 0
                ? run.getCompletedSteps()
                : Math.min(steps.size(), totalSteps);
        int progress = totalSteps == 0 ? 0 : Math.min(100, (int) Math.round(completedSteps * 100.0 / totalSteps));
        if (run.getRunStatus() == AutomationRunStatus.SUCCESS) {
            completedSteps = totalSteps;
            progress = 100;
        }
        String status = apiStatus(run.getRunStatus());
        String errorMessage = errorMessage(run, steps);
        OffsetDateTime lastUpdate = run.getUpdatedAt() != null
                ? run.getUpdatedAt()
                : run.getFinishedAt() != null ? run.getFinishedAt() : run.getStartedAt();
        return new AutomationProgressResponse(
                run.getId(),
                status,
                statusLabel(status),
                progress,
                run.getCurrentStep(),
                completedSteps,
                totalSteps,
                run.getStartedAt(),
                lastUpdate,
                run.getFinishedAt(),
                errorMessage,
                steps
        );
    }

    private AutomationProgressResponse notStarted() {
        return new AutomationProgressResponse(
                null,
                "NOT_STARTED",
                "Not Started",
                0,
                null,
                0,
                KNOWN_PIPELINE_STEP_COUNT,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private List<AutomationProgressStepResponse> parseSteps(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AutomationProgressStepResponse>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String errorMessage(AutomationRun run, List<AutomationProgressStepResponse> steps) {
        if (StringUtils.hasText(run.getFailureReason())) {
            return run.getFailureReason();
        }
        String failedSteps = steps.stream()
                .filter(step -> "FAILED".equals(step.status()))
                .map(step -> step.step() + ": " + firstText(step.failureReason(), step.summary(), "Step failed."))
                .collect(Collectors.joining("; "));
        return StringUtils.hasText(failedSteps) ? failedSteps : null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String apiStatus(AutomationRunStatus status) {
        if (status == null) {
            return "NOT_STARTED";
        }
        return switch (status) {
            case RUNNING -> "RUNNING";
            case SUCCESS -> "COMPLETED";
            case PARTIAL_SUCCESS -> "PARTIAL_SUCCESS";
            case FAILED -> "FAILED";
        };
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "RUNNING" -> "Running";
            case "COMPLETED" -> "Fully Completed";
            case "PARTIAL_SUCCESS" -> "Partial Success";
            case "FAILED" -> "Failed";
            default -> "Not Started";
        };
    }
}
