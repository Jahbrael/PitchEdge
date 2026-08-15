package com.betai.domain.refresh;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.league.League;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "data_refresh_logs",
        indexes = {
                @Index(name = "idx_data_refresh_logs_league_date_status", columnList = "league_id, refresh_date, refresh_status"),
                @Index(name = "idx_data_refresh_logs_started_at", columnList = "started_at")
        }
)
public class DataRefreshLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false)
    private LocalDate refreshDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RefreshStatus refreshStatus = RefreshStatus.RUNNING;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    @Column
    private OffsetDateTime finishedAt;

    @Column
    private Long durationMs;

    @Column(nullable = false)
    private int sourceCount;

    @Column
    private Long recordsIngested;

    @Column
    private Long recordsRejected;

    @Column(length = 500)
    private String rawPayloadReference;

    @Column(length = 128)
    private String payloadChecksum;

    @Column(length = 1000)
    private String failureReason;

    public void markSucceeded(OffsetDateTime finishedAt, int sourceCount, long recordsIngested, long recordsRejected,
                              String rawPayloadReference, String payloadChecksum) {
        this.refreshStatus = RefreshStatus.SUCCESS;
        this.finishedAt = finishedAt;
        this.durationMs = Duration.between(startedAt, finishedAt).toMillis();
        this.sourceCount = sourceCount;
        this.recordsIngested = recordsIngested;
        this.recordsRejected = recordsRejected;
        this.rawPayloadReference = rawPayloadReference;
        this.payloadChecksum = payloadChecksum;
        this.failureReason = null;
    }

    public void markFailed(OffsetDateTime finishedAt, String failureReason) {
        this.refreshStatus = RefreshStatus.FAILED;
        this.finishedAt = finishedAt;
        this.durationMs = Duration.between(startedAt, finishedAt).toMillis();
        this.failureReason = failureReason;
    }

    public void markSkipped(OffsetDateTime finishedAt, String reason) {
        this.refreshStatus = RefreshStatus.SKIPPED;
        this.finishedAt = finishedAt;
        this.durationMs = Duration.between(startedAt, finishedAt).toMillis();
        this.failureReason = reason;
    }

    public void markSuperseded() {
        this.refreshStatus = RefreshStatus.SUPERSEDED;
    }
}
