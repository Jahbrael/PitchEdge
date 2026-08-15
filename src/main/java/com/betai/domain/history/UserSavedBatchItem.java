package com.betai.domain.history;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.market.MarketCode;
import com.betai.domain.odds.ValueRating;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.prediction.PredictionSelection;
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
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "user_saved_batch_items")
public class UserSavedBatchItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_saved_batch_id", nullable = false)
    private UserSavedBatch userSavedBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prediction_selection_id")
    private PredictionSelection predictionSelection;

    private UUID matchId;

    @Column(length = 64)
    private String leagueCode;

    @Column(length = 256)
    private String fixture;

    private OffsetDateTime kickoffAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private MarketCode marketCode;

    @Column(length = 128)
    private String marketName;

    @Column(length = 64)
    private String predictedValue;

    @Column(length = 160)
    private String teamOrPlayer;

    @Column(precision = 7, scale = 6)
    private BigDecimal rawModelProbability;

    @Column(precision = 7, scale = 6)
    private BigDecimal calibratedProbability;

    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal tunedProbability;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PredictionConfidenceBand confidenceBand;

    private Integer modelQualitySampleSize;

    @Column(precision = 7, scale = 6)
    private BigDecimal modelQualityCalibrationError;

    @Column(precision = 7, scale = 6)
    private BigDecimal dataQualityScore;

    @Column(length = 32)
    private String calibrationStatus;

    @Column(precision = 10, scale = 4)
    private BigDecimal decimalOdds;

    @Column(precision = 7, scale = 6)
    private BigDecimal bookmakerImpliedProbability;

    @Column(precision = 8, scale = 6)
    private BigDecimal probabilityEdge;

    @Column(precision = 10, scale = 6)
    private BigDecimal expectedValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ValueRating valueRating;

    @Column(precision = 9, scale = 6)
    private BigDecimal rankingScore;

    @Column(length = 500)
    private String reason;

    @Column(length = 80)
    private String modelVersion;

    @Column(nullable = false)
    private OffsetDateTime generatedAt;
}
