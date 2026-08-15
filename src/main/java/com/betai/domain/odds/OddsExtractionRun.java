package com.betai.domain.odds;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.snapshot.RawSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
@Table(name = "odds_extraction_runs")
public class OddsExtractionRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raw_snapshot_id", nullable = false)
    private RawSnapshot rawSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OddsExtractionStatus extractionStatus = OddsExtractionStatus.RUNNING;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    private OffsetDateTime finishedAt;

    private Long durationMs;

    @Column(nullable = false)
    private int rowsSeen;

    @Column(nullable = false)
    private int rowsAccepted;

    @Column(nullable = false)
    private int snapshotsImported;

    @Column(nullable = false)
    private int selectionsUpdated;

    @Column(nullable = false)
    private int validationErrorCount;

    @Column(length = 1000)
    private String failureReason;

    public void finish(
            OffsetDateTime finishedAt,
            OddsExtractionStatus status,
            int rowsSeen,
            int rowsAccepted,
            int snapshotsImported,
            int selectionsUpdated,
            int validationErrorCount,
            String failureReason
    ) {
        this.finishedAt = finishedAt;
        this.durationMs = startedAt == null ? null : Duration.between(startedAt, finishedAt).toMillis();
        this.extractionStatus = status;
        this.rowsSeen = rowsSeen;
        this.rowsAccepted = rowsAccepted;
        this.snapshotsImported = snapshotsImported;
        this.selectionsUpdated = selectionsUpdated;
        this.validationErrorCount = validationErrorCount;
        this.failureReason = failureReason;
    }
}
