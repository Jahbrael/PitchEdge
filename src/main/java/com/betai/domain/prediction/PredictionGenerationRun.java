package com.betai.domain.prediction;

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
        name = "prediction_generation_runs",
        indexes = {
                @Index(
                        name = "idx_prediction_runs_league_model_feature_dates_status",
                        columnList = "league_id, model_version, feature_season_label, calculation_date, fixture_date_from, fixture_date_to, match_statuses, generation_status"
                ),
                @Index(name = "idx_prediction_runs_started_at", columnList = "started_at")
        }
)
public class PredictionGenerationRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false, length = 80)
    private String modelVersion;

    @Column(nullable = false, length = 128)
    private String featureSeasonLabel;

    @Column(nullable = false)
    private LocalDate calculationDate;

    @Column(nullable = false)
    private LocalDate fixtureDateFrom;

    @Column(nullable = false)
    private LocalDate fixtureDateTo;

    @Column(nullable = false, length = 160)
    private String matchStatuses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PredictionGenerationStatus generationStatus = PredictionGenerationStatus.RUNNING;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    @Column
    private OffsetDateTime finishedAt;

    @Column
    private Long durationMs;

    @Column(nullable = false)
    private int matchesEvaluated;

    @Column(nullable = false)
    private int selectionsGenerated;

    @Column(nullable = false)
    private int selectionsSkipped;

    @Column(length = 1000)
    private String failureReason;

    @Column
    private Integer requestedSeasonCount;

    @Column
    private Integer actualSeasonCountUsed;

    @Column(length = 40)
    private String seasonSelectionMode;

    @Column(columnDefinition = "text")
    private String selectedSeasonIds;

    @Column(length = 220)
    private String seasonWindowKey;

    @Column
    private Boolean fallbackApplied;

    public void finish(
            OffsetDateTime finishedAt,
            PredictionGenerationStatus status,
            int matchesEvaluated,
            int selectionsGenerated,
            int selectionsSkipped,
            String failureReason
    ) {
        this.finishedAt = finishedAt;
        this.durationMs = Duration.between(startedAt, finishedAt).toMillis();
        this.generationStatus = status;
        this.matchesEvaluated = matchesEvaluated;
        this.selectionsGenerated = selectionsGenerated;
        this.selectionsSkipped = selectionsSkipped;
        this.failureReason = failureReason;
    }
}
