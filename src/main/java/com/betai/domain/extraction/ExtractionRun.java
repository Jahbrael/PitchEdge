package com.betai.domain.extraction;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.snapshot.RawSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "extraction_runs",
        indexes = {
                @Index(name = "idx_extraction_runs_snapshot_status", columnList = "raw_snapshot_id, extraction_status"),
                @Index(name = "idx_extraction_runs_started_at", columnList = "started_at")
        }
)
public class ExtractionRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raw_snapshot_id", nullable = false)
    private RawSnapshot rawSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExtractionStatus extractionStatus = ExtractionStatus.RUNNING;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    @Column
    private OffsetDateTime finishedAt;

    @Column
    private Long durationMs;

    @Column(nullable = false)
    private int rowsSeen;

    @Column(nullable = false)
    private int rowsAccepted;

    @Column(nullable = false)
    private int teamsUpserted;

    @Column(nullable = false)
    private int matchesUpserted;

    @Column(nullable = false)
    private int statsUpserted;

    @Column(nullable = false)
    private int validationErrorCount;

    @Column(length = 1000)
    private String failureReason;

    public void finish(
            OffsetDateTime finishedAt,
            ExtractionStatus status,
            int rowsSeen,
            int rowsAccepted,
            int teamsUpserted,
            int matchesUpserted,
            int statsUpserted,
            int validationErrorCount,
            String failureReason
    ) {
        this.finishedAt = finishedAt;
        this.durationMs = Duration.between(startedAt, finishedAt).toMillis();
        this.extractionStatus = status;
        this.rowsSeen = rowsSeen;
        this.rowsAccepted = rowsAccepted;
        this.teamsUpserted = teamsUpserted;
        this.matchesUpserted = matchesUpserted;
        this.statsUpserted = statsUpserted;
        this.validationErrorCount = validationErrorCount;
        this.failureReason = failureReason;
    }
}
