package com.betai.domain.league;

import com.betai.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
        name = "leagues",
        indexes = {
                @Index(name = "idx_leagues_active_scrape_enabled", columnList = "active, scrape_enabled")
        }
)
public class League extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 64)
    private LeagueCode code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 128)
    private String country;

    @Column(nullable = false)
    private int tier;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean scrapeEnabled = true;

    @Column(nullable = false, length = 32)
    private String currentSeason = "2026/2027";

    @Column(length = 1000)
    private String badgeUrl;

    @Column(length = 1000)
    private String logoUrl;

    @Column(length = 1000)
    private String bannerUrl;

    @Column(length = 1000)
    private String posterUrl;

    @Column(length = 1000)
    private String trophyUrl;

    @Column(length = 1000)
    private String fanartUrl;
}
