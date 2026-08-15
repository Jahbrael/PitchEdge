package com.betai.service;

import com.betai.domain.automation.AutomationRun;
import com.betai.domain.automation.AutomationRunStatus;
import com.betai.repository.AutomationRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutomationProgressServiceTest {

    private final AutomationRunRepository repository = mock(AutomationRunRepository.class);
    private final AutomationProgressService service = new AutomationProgressService(repository, new ObjectMapper());

    @Test
    void returnsNotStartedWhenNoAutomationRunExists() {
        when(repository.findTopByOrderByStartedAtDesc()).thenReturn(Optional.empty());

        var response = service.latestProgress();

        assertThat(response.status()).isEqualTo("NOT_STARTED");
        assertThat(response.statusLabel()).isEqualTo("Not Started");
        assertThat(response.progressPercentage()).isZero();
        assertThat(response.totalSteps()).isEqualTo(8);
    }

    @Test
    void reportsPersistedRunningStepAndCompletedCount() {
        AutomationRun run = run(AutomationRunStatus.RUNNING)
                .setCurrentStep("SETTLEMENT")
                .setCompletedSteps(2)
                .setTotalSteps(8)
                .setStepSummaryJson("""
                        [{"step":"THESPORTSDB_REFRESH","status":"SUCCESS","attempts":1,"summary":"ok","warningCount":0,"failureReason":null},
                         {"step":"ODDS_EXTRACTION","status":"SUCCESS","attempts":1,"summary":"ok","warningCount":0,"failureReason":null}]
                        """);
        when(repository.findTopByOrderByStartedAtDesc()).thenReturn(Optional.of(run));

        var response = service.latestProgress();

        assertThat(response.status()).isEqualTo("RUNNING");
        assertThat(response.statusLabel()).isEqualTo("Running");
        assertThat(response.currentStep()).isEqualTo("SETTLEMENT");
        assertThat(response.completedSteps()).isEqualTo(2);
        assertThat(response.progressPercentage()).isEqualTo(25);
        assertThat(response.steps()).hasSize(2);
    }

    @Test
    void exposesActualPartialSuccessAndFailedStepDetails() {
        AutomationRun run = run(AutomationRunStatus.PARTIAL_SUCCESS)
                .setCurrentStep("BACKTEST_TUNING")
                .setCompletedSteps(8)
                .setTotalSteps(8)
                .setFinishedAt(OffsetDateTime.parse("2026-07-14T10:10:00Z"))
                .setStepSummaryJson("""
                        [{"step":"ODDS_EXTRACTION","status":"FAILED","attempts":2,"summary":"provider unavailable","warningCount":2,"failureReason":"provider unavailable"}]
                        """);
        when(repository.findTopByOrderByStartedAtDesc()).thenReturn(Optional.of(run));

        var response = service.latestProgress();

        assertThat(response.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(response.statusLabel()).isEqualTo("Partial Success");
        assertThat(response.progressPercentage()).isEqualTo(100);
        assertThat(response.errorMessage()).contains("ODDS_EXTRACTION", "provider unavailable");
        assertThat(response.steps().getFirst().failureReason()).isEqualTo("provider unavailable");
    }

    @Test
    void labelsSuccessfulCompletionAndForcesOneHundredPercent() {
        AutomationRun run = run(AutomationRunStatus.SUCCESS)
                .setCompletedSteps(7)
                .setTotalSteps(8)
                .setFinishedAt(OffsetDateTime.parse("2026-07-14T10:10:00Z"));
        when(repository.findTopByOrderByStartedAtDesc()).thenReturn(Optional.of(run));

        var response = service.latestProgress();

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.statusLabel()).isEqualTo("Fully Completed");
        assertThat(response.completedSteps()).isEqualTo(8);
        assertThat(response.progressPercentage()).isEqualTo(100);
    }

    private AutomationRun run(AutomationRunStatus status) {
        AutomationRun run = new AutomationRun()
                .setRunStatus(status)
                .setStartedAt(OffsetDateTime.parse("2026-07-14T10:00:00Z"));
        run.setId(UUID.randomUUID());
        run.setUpdatedAt(OffsetDateTime.parse("2026-07-14T10:05:00Z"));
        return run;
    }
}
