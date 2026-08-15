package com.betai.domain.backtest;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.league.League;
import com.betai.domain.market.MarketDefinition;
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

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "backtest_market_summaries")
public class BacktestMarketSummary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "backtest_run_id", nullable = false)
    private BacktestRun backtestRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_definition_id", nullable = false)
    private MarketDefinition marketDefinition;

    @Column(nullable = false)
    private int sampleSize;

    @Column(nullable = false)
    private int wonCount;

    @Column(nullable = false)
    private int lostCount;

    @Column(nullable = false)
    private int voidCount;

    @Column(nullable = false)
    private int pricedCount;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal observedWinRate;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal averageProbability;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal brierScore;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal calibrationError;

    @Column(precision = 10, scale = 6)
    private BigDecimal averageExpectedValue;

    @Column(precision = 10, scale = 6)
    private BigDecimal realizedRoi;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal recommendedProbabilityAdjustment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TuningRecommendation tuningRecommendation;
}
