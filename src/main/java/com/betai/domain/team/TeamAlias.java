package com.betai.domain.team;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.league.League;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "team_aliases",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_team_aliases_league_alias", columnNames = {"league_id", "alias_normalized"})
        },
        indexes = {
                @Index(name = "idx_team_aliases_team", columnList = "team_id")
        }
)
public class TeamAlias extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 160)
    private String alias;

    @Column(nullable = false, length = 180)
    private String aliasNormalized;

    @Column(nullable = false, length = 160)
    private String sourceName;
}
