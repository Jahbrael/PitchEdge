package com.betai.domain.market;

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

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "market_definitions",
        indexes = {
                @Index(name = "idx_market_definitions_enabled_type", columnList = "enabled, market_type")
        }
)
public class MarketDefinition extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 64)
    private MarketCode code;

    @Column(nullable = false, length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private MarketType marketType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private MarketType marketFamily;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MarketDirection direction;

    @Column(nullable = false, length = 32)
    private String selectionValue;

    @Column(precision = 6, scale = 2)
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MarketPeriod period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MarketTeamScope teamScope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MarketTargetType targetType;

    @Column(nullable = false)
    private boolean requiresTeamData = true;

    @Column(nullable = false)
    private boolean requiresPlayerData;

    @Column(nullable = false)
    private boolean requiresHalfTimeData;

    @Column(nullable = false)
    private boolean requiresEventData;

    @Column(nullable = false)
    private boolean requiresOdds;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int minimumSampleSize;

    @Column(nullable = false, length = 300)
    private String settlementDescription;
}
