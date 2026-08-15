package com.betai.domain.automation;

import com.betai.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "automation_runs")
public class AutomationRun extends BaseEntity {

    @Column(nullable = false)
    private LocalDate automationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AutomationTriggerType triggerType = AutomationTriggerType.SCHEDULED;

    @Column(nullable = false, columnDefinition = "text")
    private String leagueCodes;

    @Column(nullable = false, length = 80)
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AutomationRunStatus runStatus = AutomationRunStatus.RUNNING;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    private OffsetDateTime finishedAt;

    private Long durationMs;

    @Column(nullable = false)
    private int attemptCount;

    @Column(columnDefinition = "text")
    private String stepSummaryJson;

    @Column(nullable = false)
    private int warningCount;

    @Column(length = 1000)
    private String failureReason;

    @Column(length = 64)
    private String currentStep;

    @Column(nullable = false)
    private int completedSteps;

    @Column(nullable = false)
    private int totalSteps;

    public void finish(
            OffsetDateTime finishedAt,
            AutomationRunStatus status,
            int attemptCount,
            int warningCount,
            String stepSummaryJson,
            String failureReason
    ) {
        this.finishedAt = finishedAt;
        this.durationMs = startedAt == null ? null : Duration.between(startedAt, finishedAt).toMillis();
        this.runStatus = status;
        this.attemptCount = attemptCount;
        this.warningCount = warningCount;
        this.stepSummaryJson = stepSummaryJson;
        this.failureReason = failureReason;
    }
}
