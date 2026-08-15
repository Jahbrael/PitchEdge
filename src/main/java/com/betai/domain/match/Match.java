package com.betai.domain.match;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.league.League;
import com.betai.domain.statistics.MatchStatistics;
import com.betai.domain.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "FootballMatch")
@Table(
        name = "matches",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_matches_league_teams_kickoff",
                        columnNames = {"league_id", "home_team_id", "away_team_id", "kickoff_at"}
                )
        },
        indexes = {
                @Index(name = "idx_matches_league_date_status", columnList = "league_id, match_date, status"),
                @Index(name = "idx_matches_home_team_date", columnList = "home_team_id, match_date"),
                @Index(name = "idx_matches_away_team_date", columnList = "away_team_id, match_date")
        }
)
public class Match extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @Column(nullable = false)
    private LocalDate matchDate;

    @Column(nullable = false)
    private OffsetDateTime kickoffAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MatchStatus status = MatchStatus.SCHEDULED;

    @Column
    private Integer homeScore;

    @Column
    private Integer awayScore;

    @Column
    private Integer homeHalfTimeScore;

    @Column
    private Integer awayHalfTimeScore;

    @Column(length = 32)
    private String liveMinute;

    @Column
    private OffsetDateTime scoreRefreshedAt;

    @Column(nullable = false, length = 32)
    private String seasonLabel;

    @Column(length = 64)
    private String roundLabel;

    @Column(length = 160)
    private String venue;

    @Column(nullable = false, length = 180)
    private String sourceFixtureKey;

    @Column(length = 160)
    private String referee;

    @OneToOne(mappedBy = "match", fetch = FetchType.LAZY)
    private MatchStatistics statistics;
}
