package com.betai.domain.quality;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.league.League;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.prediction.PredictionConfidenceBand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "model_quality_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_model_quality_snapshots",
                        columnNames = {"league_id", "market_definition_id", "model_version", "quality_date"}
                )
        },
        indexes = {
                @Index(name = "idx_model_quality_league_model_date", columnList = "league_id, model_version, quality_date"),
                @Index(name = "idx_model_quality_market_band", columnList = "market_definition_id, confidence_band")
        }
)
public class ModelQualitySnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_definition_id", nullable = false)
    private MarketDefinition marketDefinition;

    @Column(nullable = false, length = 80)
    private String modelVersion;

    @Column(nullable = false)
    private LocalDate qualityDate;

    @Column(nullable = false)
    private int sampleSize;

    @Column(nullable = false)
    private int wonCount;

    @Column(nullable = false)
    private int lostCount;

    @Column(nullable = false)
    private int voidCount;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal observedWinRate;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal averageRawProbability;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal brierScore;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal calibrationError;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal probabilityAdjustment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PredictionConfidenceBand confidenceBand = PredictionConfidenceBand.UNRATED;

    @Column(nullable = false)
    private OffsetDateTime generatedAt;

    @PrePersist
    void prePersistQuality() {
        if (generatedAt == null) {
            generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
