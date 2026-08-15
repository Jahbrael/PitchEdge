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
        name = "teams",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_teams_league_canonical_name", columnNames = {"league_id", "canonical_name"}),
                @UniqueConstraint(name = "ux_teams_external_key", columnNames = "external_key")
        },
        indexes = {
                @Index(name = "idx_teams_league_active", columnList = "league_id, active")
        }
)
public class Team extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false, length = 160)
    private String canonicalName;

    @Column(nullable = false, length = 80)
    private String shortName;

    @Column(nullable = false, length = 128)
    private String country;

    @Column(nullable = false, length = 160)
    private String externalKey;

    @Column(length = 1000)
    private String badgeUrl;

    @Column(length = 1000)
    private String logoUrl;

    @Column(length = 1000)
    private String bannerUrl;

    @Column(length = 1000)
    private String equipmentUrl;

    @Column(length = 1000)
    private String fanartUrl;

    @Column(nullable = false)
    private boolean active = true;
}
