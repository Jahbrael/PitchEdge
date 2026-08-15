package com.betai.domain.prediction;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.Match;
import com.betai.domain.odds.Bookmaker;
import com.betai.domain.odds.OddsSnapshot;
import com.betai.domain.odds.ValueRating;
import com.betai.domain.quality.ModelQualitySnapshot;
import com.betai.domain.tuning.ModelTuningProfile;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "prediction_selections",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_prediction_selections_match_market_model",
                        columnNames = {"match_id", "market_definition_id", "model_version"}
                )
        },
        indexes = {
                @Index(name = "idx_prediction_selections_generated_at", columnList = "generated_at"),
                @Index(name = "idx_prediction_selections_probability", columnList = "probability"),
                @Index(name = "idx_prediction_selections_outcome", columnList = "outcome")
        }
)
public class PredictionSelection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_definition_id", nullable = false)
    private MarketDefinition marketDefinition;

    @Column(nullable = false, length = 64)
    private String predictedValue;

    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal probability;

    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal rawProbability;

    @Column(nullable = false, length = 80)
    private String modelVersion;

    @Column(nullable = false)
    private OffsetDateTime generatedAt;

    @Column(nullable = false, length = 160)
    private String correlationGroupKey;

    @Column(columnDefinition = "text")
    private String featureSnapshotJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PredictionConfidenceBand confidenceBand = PredictionConfidenceBand.UNRATED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_quality_snapshot_id")
    private ModelQualitySnapshot modelQualitySnapshot;

    @Column(length = 500)
    private String calibrationNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_tuning_profile_id")
    private ModelTuningProfile modelTuningProfile;

    @Column(precision = 9, scale = 6)
    private BigDecimal tuningAdjustment;

    @Column(length = 500)
    private String tuningNote;

    @Column(precision = 10, scale = 4)
    private BigDecimal bestDecimalOdds;

    @Column(precision = 7, scale = 6)
    private BigDecimal bestImpliedProbability;

    @Column(precision = 8, scale = 6)
    private BigDecimal valueEdge;

    @Column(precision = 10, scale = 6)
    private BigDecimal expectedValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ValueRating valueRating = ValueRating.NO_ODDS;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "best_odds_bookmaker_id")
    private Bookmaker bestOddsBookmaker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "best_odds_snapshot_id")
    private OddsSnapshot bestOddsSnapshot;

    private OffsetDateTime oddsCapturedAt;

    private OffsetDateTime valueAssessedAt;

    @Column(length = 500)
    private String valueNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PredictionOutcome outcome = PredictionOutcome.PENDING;

    @Column
    private Integer requestedSeasonCount;

    @Column
    private Integer actualSeasonCountUsed;

    @Column(columnDefinition = "text")
    private String selectedSeasons;

    @Column
    private Integer completedMatchesUsed;

    @Column
    private Boolean fallbackApplied;

    @Column(length = 40)
    private String historicalDepthStatus;

    @Column(length = 120)
    private String marketSpecificDataCoverage;

    @Column(length = 220)
    private String seasonWindowKey;

    @PrePersist
    void prePersist() {
        if (generatedAt == null) {
            generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (rawProbability == null) {
            rawProbability = probability;
        }
        if (confidenceBand == null) {
            confidenceBand = PredictionConfidenceBand.UNRATED;
        }
        if (valueRating == null) {
            valueRating = ValueRating.NO_ODDS;
        }
    }
}
