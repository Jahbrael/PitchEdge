package com.betai.domain.source;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "league_season_coverage",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_league_season_coverage", columnNames = {"league_id", "season_label"})
        },
        indexes = {
                @Index(name = "idx_league_season_coverage_lookup", columnList = "league_id, season_label")
        }
)
public class LeagueSeasonCoverage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false, length = 64)
    private String seasonLabel;

    @Column(nullable = false)
    private boolean hasFixtures;
    @Column(nullable = false)
    private boolean hasResults;
    @Column(nullable = false)
    private boolean hasTeamStatistics;
    @Column(nullable = false)
    private boolean hasEventStatistics;
    @Column(nullable = false)
    private boolean hasLineups;
    @Column(nullable = false)
    private boolean hasTimeline;
    @Column(nullable = false)
    private boolean hasPlayerStatistics;
    @Column(nullable = false)
    private boolean hasGoals;
    @Column(nullable = false)
    private boolean hasAssists;
    @Column(nullable = false)
    private boolean hasCards;
    @Column(nullable = false)
    private boolean hasCorners;
    @Column(nullable = false)
    private boolean hasShots;
    @Column(nullable = false)
    private boolean hasShotsOnTarget;
    @Column(nullable = false)
    private boolean hasPasses;
    @Column(nullable = false)
    private boolean hasSaves;
    @Column(nullable = false)
    private boolean hasXg;

    @Column(nullable = false)
    private int completedEventsChecked;
    @Column(nullable = false)
    private int eventsWithStatistics;
    @Column(nullable = false)
    private int eventsWithLineups;
    @Column(nullable = false)
    private int eventsWithTimeline;
    @Column(nullable = false)
    private int playersWithStatistics;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal coveragePercentage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CoverageLevel statisticsCoverageLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CoverageLevel cornersCoverageLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CoverageLevel cardsCoverageLevel;

    @Column(nullable = false)
    private OffsetDateTime lastVerifiedAt;
}
