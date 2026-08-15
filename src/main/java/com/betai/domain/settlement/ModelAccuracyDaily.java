package com.betai.domain.settlement;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.league.League;
import com.betai.domain.market.MarketDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
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
        name = "model_accuracy_daily",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_model_accuracy_daily",
                        columnNames = {"league_id", "market_definition_id", "model_version", "accuracy_date"}
                )
        },
        indexes = {
                @Index(name = "idx_model_accuracy_daily_league_model_date", columnList = "league_id, model_version, accuracy_date"),
                @Index(name = "idx_model_accuracy_daily_market_date", columnList = "market_definition_id, accuracy_date")
        }
)
public class ModelAccuracyDaily extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_definition_id", nullable = false)
    private MarketDefinition marketDefinition;

    @Column(nullable = false, length = 80)
    private String modelVersion;

    @Column(nullable = false)
    private LocalDate accuracyDate;

    @Column(nullable = false)
    private int settledSelections;

    @Column(nullable = false)
    private int wonCount;

    @Column(nullable = false)
    private int lostCount;

    @Column(nullable = false)
    private int voidCount;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal winRate;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal averageProbability;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal brierScore;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal calibrationError;
}
