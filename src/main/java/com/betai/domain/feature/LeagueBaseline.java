package com.betai.domain.feature;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.league.League;
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
        name = "league_baselines",
        indexes = {
                @Index(name = "idx_league_baselines_league_date", columnList = "league_id, calculation_date")
        }
)
public class LeagueBaseline extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false, length = 128)
    private String seasonLabel;

    @Column(nullable = false)
    private LocalDate calculationDate;

    @Column(nullable = false)
    private int matchesSampled;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal avgHomeGoals;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal avgAwayGoals;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal avgTotalGoals;

    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal homeWinRate;

    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal drawRate;

    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal awayWinRate;

    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal bttsRate;

    @Column(name = "over_1_5_rate", nullable = false, precision = 7, scale = 6)
    private BigDecimal over15Rate;

    @Column(name = "over_2_5_rate", nullable = false, precision = 7, scale = 6)
    private BigDecimal over25Rate;

    @Column(name = "under_3_5_rate", nullable = false, precision = 7, scale = 6)
    private BigDecimal under35Rate;

    @Column(precision = 8, scale = 4)
    private BigDecimal avgTotalCorners;

    @Column(precision = 8, scale = 4)
    private BigDecimal avgTotalYellowCards;

    @Column(precision = 7, scale = 6)
    private BigDecimal redCardRate;

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
