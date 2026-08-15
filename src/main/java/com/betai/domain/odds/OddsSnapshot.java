package com.betai.domain.odds;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.Match;
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
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "odds_snapshots",
        indexes = {
                @Index(name = "idx_odds_snapshots_match_market_captured", columnList = "match_id, market_definition_id, captured_at"),
                @Index(name = "idx_odds_snapshots_bookmaker_captured", columnList = "bookmaker_id, captured_at"),
                @Index(name = "idx_odds_snapshots_source_name", columnList = "source_name")
        }
)
public class OddsSnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_definition_id", nullable = false)
    private MarketDefinition marketDefinition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookmaker_id", nullable = false)
    private Bookmaker bookmaker;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal decimalOdds;

    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal impliedProbability;

    @Column(nullable = false)
    private OffsetDateTime capturedAt;

    @Column(nullable = false, length = 160)
    private String sourceName;

    @Column(length = 500)
    private String sourceUrl;

    @Column(length = 500)
    private String rawPayloadReference;
}
