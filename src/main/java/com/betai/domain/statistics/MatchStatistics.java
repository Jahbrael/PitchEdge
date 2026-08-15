package com.betai.domain.statistics;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.match.Match;
import com.betai.domain.snapshot.RawSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "match_statistics",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_match_statistics_match", columnNames = "match_id")
        },
        indexes = {
                @Index(name = "idx_match_statistics_snapshot", columnList = "raw_snapshot_id")
        }
)
public class MatchStatistics extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raw_snapshot_id", nullable = false)
    private RawSnapshot rawSnapshot;

    @Column
    private Integer homeShots;

    @Column
    private Integer awayShots;

    @Column
    private Integer homeShotsOnTarget;

    @Column
    private Integer awayShotsOnTarget;

    @Column
    private Integer homeFouls;

    @Column
    private Integer awayFouls;

    @Column
    private Integer homeCorners;

    @Column
    private Integer awayCorners;

    @Column
    private Integer homeYellowCards;

    @Column
    private Integer awayYellowCards;

    @Column
    private Integer homeRedCards;

    @Column
    private Integer awayRedCards;

    @Column(precision = 5, scale = 2)
    private java.math.BigDecimal homeExpectedGoals;

    @Column(precision = 5, scale = 2)
    private java.math.BigDecimal awayExpectedGoals;

    @Column
    private Integer homePossession;

    @Column
    private Integer awayPossession;
}
