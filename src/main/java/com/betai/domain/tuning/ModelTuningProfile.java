package com.betai.domain.tuning;

import com.betai.domain.backtest.BacktestRun;
import com.betai.domain.backtest.TuningRecommendation;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "model_tuning_profiles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_model_tuning_profile",
                        columnNames = {"league_id", "market_definition_id", "model_version", "profile_date", "segment_key"}
                )
        }
)
public class ModelTuningProfile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_definition_id", nullable = false)
    private MarketDefinition marketDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_backtest_run_id")
    private BacktestRun sourceBacktestRun;

    @Column(nullable = false, length = 80)
    private String modelVersion;

    @Column(nullable = false)
    private LocalDate profileDate;

    @Column(nullable = false, length = 64)
    private String segmentKey = "GLOBAL";

    @Column(nullable = false)
    private int sampleSize;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal recommendedProbabilityAdjustment;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal appliedProbabilityAdjustment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TuningRecommendation tuningRecommendation;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 1000)
    private String note;
}
