package com.betai.domain.source;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.league.League;
import com.betai.domain.market.MarketCode;
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

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "league_season_market_availability",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_league_season_market_availability",
                        columnNames = {"league_id", "season_label", "market_code"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_league_season_market_availability_lookup",
                        columnList = "league_id, season_label, market_code, available"
                )
        }
)
public class LeagueSeasonMarketAvailability extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false, length = 64)
    private String seasonLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private MarketCode marketCode;

    @Column(nullable = false)
    private boolean available;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CoverageLevel coverageLevel;

    @Column(length = 1000)
    private String reason;

    @Column(nullable = false)
    private OffsetDateTime lastVerifiedAt;
}
