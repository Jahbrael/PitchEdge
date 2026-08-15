package com.betai.domain.settlement;

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
        name = "settlement_runs",
        indexes = {
                @Index(
                        name = "idx_settlement_runs_league_model_dates_status",
                        columnList = "league_id, model_version, settlement_date, match_date_from, match_date_to, settlement_status"
                ),
                @Index(name = "idx_settlement_runs_started_at", columnList = "started_at")
        }
)
public class SettlementRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false, length = 80)
    private String modelVersion;

    @Column(nullable = false)
    private LocalDate settlementDate;

    @Column(nullable = false)
    private LocalDate matchDateFrom;

    @Column(nullable = false)
    private LocalDate matchDateTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SettlementStatus settlementStatus = SettlementStatus.RUNNING;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    @Column
    private OffsetDateTime finishedAt;

    @Column
    private Long durationMs;

    @Column(nullable = false)
    private int selectionsEvaluated;

    @Column(nullable = false)
    private int wonCount;

    @Column(nullable = false)
    private int lostCount;

    @Column(nullable = false)
    private int voidCount;

    @Column(nullable = false)
    private int skippedCount;

    @Column(length = 1000)
    private String failureReason;

    public void finish(
            OffsetDateTime finishedAt,
            SettlementStatus status,
            SettlementCounters counters,
            String failureReason
    ) {
        this.finishedAt = finishedAt;
        this.durationMs = Duration.between(startedAt, finishedAt).toMillis();
        this.settlementStatus = status;
        this.selectionsEvaluated = counters.evaluated();
        this.wonCount = counters.won();
        this.lostCount = counters.lost();
        this.voidCount = counters.voided();
        this.skippedCount = counters.skipped();
        this.failureReason = failureReason;
    }
}
