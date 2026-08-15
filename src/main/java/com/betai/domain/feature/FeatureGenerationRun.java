package com.betai.domain.feature;

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
        name = "feature_generation_runs",
        indexes = {
                @Index(name = "idx_feature_runs_league_date_status", columnList = "league_id, calculation_date, feature_status"),
                @Index(name = "idx_feature_runs_started_at", columnList = "started_at")
        }
)
public class FeatureGenerationRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false)
    private LocalDate calculationDate;

    @Column(nullable = false, length = 128)
    private String seasonLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FeatureGenerationStatus featureStatus = FeatureGenerationStatus.RUNNING;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    @Column
    private OffsetDateTime finishedAt;

    @Column
    private Long durationMs;

    @Column(nullable = false)
    private int matchesSampled;

    @Column(nullable = false)
    private int teamFeaturesGenerated;

    @Column(nullable = false)
    private int leagueBaselinesGenerated;

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
            FeatureGenerationStatus status,
            int matchesSampled,
            int teamFeaturesGenerated,
            int leagueBaselinesGenerated,
            String failureReason
    ) {
        this.finishedAt = finishedAt;
        this.durationMs = Duration.between(startedAt, finishedAt).toMillis();
        this.featureStatus = status;
        this.matchesSampled = matchesSampled;
        this.teamFeaturesGenerated = teamFeaturesGenerated;
        this.leagueBaselinesGenerated = leagueBaselinesGenerated;
        this.failureReason = failureReason;
    }
}
