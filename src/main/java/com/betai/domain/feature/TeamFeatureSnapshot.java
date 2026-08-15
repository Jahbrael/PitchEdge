package com.betai.domain.feature;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.league.League;
import com.betai.domain.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
        name = "team_feature_snapshots",
        indexes = {
                @Index(name = "idx_team_features_league_date", columnList = "league_id, calculation_date"),
                @Index(name = "idx_team_features_team_date", columnList = "team_id, calculation_date")
        }
)
public class TeamFeatureSnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 128)
    private String seasonLabel;

    @Column(nullable = false)
    private LocalDate calculationDate;

    @Column(nullable = false)
    private int matchesPlayed;

    @Column(nullable = false)
    private int homeMatches;

    @Column(nullable = false)
    private int awayMatches;

    @Column(name = "last_5_matches", nullable = false)
    private int last5Matches;

    @Column(name = "last_10_matches", nullable = false)
    private int last10Matches;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal pointsPerMatch;

    @Column(name = "last_5_points_per_match", nullable = false, precision = 8, scale = 4)
    private BigDecimal last5PointsPerMatch;

    @Column(name = "last_10_points_per_match", nullable = false, precision = 8, scale = 4)
    private BigDecimal last10PointsPerMatch;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal goalsForPerMatch;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal goalsAgainstPerMatch;

    @Column(precision = 8, scale = 4)
    private BigDecimal homeGoalsForPerMatch;

    @Column(precision = 8, scale = 4)
    private BigDecimal homeGoalsAgainstPerMatch;

    @Column(precision = 8, scale = 4)
    private BigDecimal awayGoalsForPerMatch;

    @Column(precision = 8, scale = 4)
    private BigDecimal awayGoalsAgainstPerMatch;

    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal cleanSheetRate;

    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal failedToScoreRate;

    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal bttsRate;

    @Column(name = "over_1_5_rate", nullable = false, precision = 7, scale = 6)
    private BigDecimal over15Rate;

    @Column(name = "over_2_5_rate", nullable = false, precision = 7, scale = 6)
    private BigDecimal over25Rate;

    @Column(name = "under_3_5_rate", nullable = false, precision = 7, scale = 6)
    private BigDecimal under35Rate;

    @Column(precision = 8, scale = 4)
    private BigDecimal cornersForPerMatch;

    @Column(precision = 8, scale = 4)
    private BigDecimal cornersAgainstPerMatch;

    @Column(precision = 8, scale = 4)
    private BigDecimal yellowCardsForPerMatch;

    @Column(precision = 8, scale = 4)
    private BigDecimal yellowCardsAgainstPerMatch;

    @Column(precision = 7, scale = 6)
    private BigDecimal redCardRate;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal formScore;

    @Column
    private Integer requestedSeasonCount;

    @Column
    private Integer actualSeasonCountUsed;

    @Column(length = 40)
    private String seasonSelectionMode;

    @Column(columnDefinition = "text")
    private String selectedSeasonIds;

    @Column(columnDefinition = "text")
    private String selectedSeasonNames;

    @Column
    private Boolean currentSeasonIncluded;

    @Column
    private Boolean fallbackApplied;

    @Column
    private LocalDate oldestDataDate;

    @Column
    private LocalDate newestDataDate;

    @Column
    private Integer completedMatchesUsed;

    @Column
    private Integer marketSpecificUsableSeasonCount;

    @Column(length = 80)
    private String recencyWeightingVersion;

    @Column(length = 220)
    private String seasonWindowKey;

    @Column(length = 40)
    private String historicalDepthStatus;

    @Column(length = 120)
    private String marketSpecificDataCoverage;
}
