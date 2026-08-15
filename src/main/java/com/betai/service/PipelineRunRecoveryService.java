package com.betai.service;

import com.betai.domain.automation.AutomationRun;
import com.betai.domain.automation.AutomationRunStatus;
import com.betai.domain.pipeline.PipelineRun;
import com.betai.domain.pipeline.PipelineStatus;
import com.betai.repository.AutomationRunRepository;
import com.betai.repository.PipelineRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PipelineRunRecoveryService {

    private static final String STALE_RUN_FAILURE_REASON =
            "Marked failed on application startup because the previous process stopped before this pipeline finalized.";

    private final PipelineRunRepository pipelineRunRepository;
    private final AutomationRunRepository automationRunRepository;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failStaleRunningPipelineRuns() {
        OffsetDateTime recoveredAt = OffsetDateTime.now(clock);

        List<PipelineRun> staleRuns = pipelineRunRepository.findByPipelineStatus(PipelineStatus.RUNNING);
        if (!staleRuns.isEmpty()) {
            for (PipelineRun run : staleRuns) {
                run.finish(
                        recoveredAt,
                        PipelineStatus.FAILED,
                        run.getStepSummaryJson(),
                        STALE_RUN_FAILURE_REASON
                );
            }
            pipelineRunRepository.saveAll(staleRuns);
        }

        List<AutomationRun> staleAutomationRuns = automationRunRepository.findByRunStatus(AutomationRunStatus.RUNNING);
        if (staleAutomationRuns.isEmpty()) {
            return;
        }

        for (AutomationRun run : staleAutomationRuns) {
            int attempts = Math.max(1, run.getAttemptCount());
            run.finish(
                    recoveredAt,
                    AutomationRunStatus.FAILED,
                    attempts,
                    run.getWarningCount() + 1,
                    run.getStepSummaryJson(),
                    STALE_RUN_FAILURE_REASON
            );
        }
        automationRunRepository.saveAll(staleAutomationRuns);
    }
}
