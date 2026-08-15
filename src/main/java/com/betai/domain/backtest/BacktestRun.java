package com.betai.domain.backtest;

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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "backtest_runs")
public class BacktestRun extends BaseEntity {

    @Column(nullable = false, columnDefinition = "text")
    private String leagueCodes;

    @Column(nullable = false, length = 80)
    private String modelVersion;

    @Column(nullable = false)
    private LocalDate backtestDate;

    @Column(nullable = false)
    private LocalDate matchDateFrom;

    @Column(nullable = false)
    private LocalDate matchDateTo;

    @Column(nullable = false)
    private int minimumSampleSize;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    private OffsetDateTime finishedAt;

    private Long durationMs;

    @Column(nullable = false)
    private int totalSelections;

    @Column(nullable = false)
    private int totalWon;

    @Column(nullable = false)
    private int totalLost;

    @Column(nullable = false)
    private int totalVoid;

    @Column(nullable = false)
    private int totalPriced;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal averageProbability = BigDecimal.ZERO;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal observedWinRate = BigDecimal.ZERO;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal brierScore = BigDecimal.ZERO;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal calibrationError = BigDecimal.ZERO;

    @Column(precision = 10, scale = 6)
    private BigDecimal averageExpectedValue;

    @Column(precision = 10, scale = 6)
    private BigDecimal realizedRoi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BacktestStatus status = BacktestStatus.SUCCESS;

    @Column(length = 1000)
    private String summary;

    public void finish(OffsetDateTime finishedAt, BacktestStatus status, String summary) {
        this.finishedAt = finishedAt;
        this.durationMs = startedAt == null ? null : Duration.between(startedAt, finishedAt).toMillis();
        this.status = status;
        this.summary = summary;
    }
}
