package com.betai.service;

import com.betai.domain.automation.AutomationRun;
import com.betai.domain.automation.AutomationRunStatus;
import com.betai.domain.automation.AutomationTriggerType;
import com.betai.domain.pipeline.PipelineRun;
import com.betai.domain.pipeline.PipelineStatus;
import com.betai.repository.AutomationRunRepository;
import com.betai.repository.PipelineRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineRunRecoveryServiceTest {

    @Mock
    private PipelineRunRepository pipelineRunRepository;
    @Mock
    private AutomationRunRepository automationRunRepository;

    private PipelineRunRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new PipelineRunRecoveryService(
                pipelineRunRepository,
                automationRunRepository,
                Clock.fixed(Instant.parse("2026-06-18T11:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void marksRunningPipelineRunsFailedOnStartup() {
        PipelineRun staleRun = new PipelineRun()
                .setPipelineDate(LocalDate.of(2026, 6, 18))
                .setLeagueCodes("PREMIER_LEAGUE")
                .setModelVersion("test-model")
                .setPipelineStatus(PipelineStatus.RUNNING)
                .setStartedAt(OffsetDateTime.parse("2026-06-18T09:00:00Z"));
        when(pipelineRunRepository.findByPipelineStatus(PipelineStatus.RUNNING))
                .thenReturn(List.of(staleRun));
        when(automationRunRepository.findByRunStatus(AutomationRunStatus.RUNNING))
                .thenReturn(List.of());

        service.failStaleRunningPipelineRuns();

        ArgumentCaptor<List<PipelineRun>> captor = ArgumentCaptor.forClass(List.class);
        verify(pipelineRunRepository).saveAll(captor.capture());
        PipelineRun saved = captor.getValue().getFirst();
        assertThat(saved.getPipelineStatus()).isEqualTo(PipelineStatus.FAILED);
        assertThat(saved.getFinishedAt()).isEqualTo(OffsetDateTime.parse("2026-06-18T11:00:00Z"));
        assertThat(saved.getDurationMs()).isEqualTo(7_200_000L);
        assertThat(saved.getFailureReason()).contains("previous process stopped");
    }

    @Test
    void marksRunningAutomationRunsFailedOnStartupEvenWhenNoPipelineRunsAreStale() {
        AutomationRun staleRun = new AutomationRun()
                .setAutomationDate(LocalDate.of(2026, 6, 18))
                .setTriggerType(AutomationTriggerType.SCHEDULED)
                .setLeagueCodes("PREMIER_LEAGUE")
                .setModelVersion("test-model")
                .setRunStatus(AutomationRunStatus.RUNNING)
                .setStartedAt(OffsetDateTime.parse("2026-06-18T09:00:00Z"))
                .setAttemptCount(0)
                .setWarningCount(0);
        when(pipelineRunRepository.findByPipelineStatus(PipelineStatus.RUNNING))
                .thenReturn(List.of());
        when(automationRunRepository.findByRunStatus(AutomationRunStatus.RUNNING))
                .thenReturn(List.of(staleRun));

        service.failStaleRunningPipelineRuns();

        ArgumentCaptor<List<AutomationRun>> captor = ArgumentCaptor.forClass(List.class);
        verify(automationRunRepository).saveAll(captor.capture());
        AutomationRun saved = captor.getValue().getFirst();
        assertThat(saved.getRunStatus()).isEqualTo(AutomationRunStatus.FAILED);
        assertThat(saved.getFinishedAt()).isEqualTo(OffsetDateTime.parse("2026-06-18T11:00:00Z"));
        assertThat(saved.getDurationMs()).isEqualTo(7_200_000L);
        assertThat(saved.getFailureReason()).contains("previous process stopped");
        assertThat(saved.getAttemptCount()).isEqualTo(1);
        assertThat(saved.getWarningCount()).isEqualTo(1);
    }
}
