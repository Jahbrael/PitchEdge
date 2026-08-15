package com.betai.domain.pipeline;

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
@Table(name = "pipeline_runs")
public class PipelineRun extends BaseEntity {

    @Column(nullable = false)
    private LocalDate pipelineDate;

    @Column(nullable = false, columnDefinition = "text")
    private String leagueCodes;

    @Column(nullable = false, length = 80)
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PipelineStatus pipelineStatus = PipelineStatus.RUNNING;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    private OffsetDateTime finishedAt;

    private Long durationMs;

    @Column(columnDefinition = "text")
    private String stepSummaryJson;

    @Column(length = 1000)
    private String failureReason;

    public void finish(OffsetDateTime finishedAt, PipelineStatus status, String stepSummaryJson, String failureReason) {
        this.finishedAt = finishedAt;
        this.durationMs = startedAt == null ? null : Duration.between(startedAt, finishedAt).toMillis();
        this.pipelineStatus = status;
        this.stepSummaryJson = stepSummaryJson;
        this.failureReason = failureReason;
    }
}
