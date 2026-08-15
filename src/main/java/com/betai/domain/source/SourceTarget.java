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
        name = "source_targets",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_source_targets_league_type_name", columnNames = {"league_id", "source_type", "name"})
        },
        indexes = {
                @Index(name = "idx_source_targets_league_active", columnList = "league_id, active"),
                @Index(name = "idx_source_targets_type_active", columnList = "source_type, active"),
                @Index(name = "idx_source_targets_reliability", columnList = "reliability_score"),
                @Index(name = "idx_source_targets_target_season_active", columnList = "target_season_label, active"),
                @Index(name = "idx_source_targets_resilience", columnList = "league_id, active, system_disabled, quarantined_until, fallback_priority, reliability_score")
        }
)
public class SourceTarget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private SourceType sourceType;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 1000)
    private String urlTemplate;

    @Column(length = 32)
    private String sourceSeasonToken;

    @Column(length = 32)
    private String targetSeasonLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RenderMode renderMode = RenderMode.STATIC_HTML;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean robotsTxtRequired = true;

    @Column(nullable = false, length = 160)
    private String userAgent = "BetAIResearchBot/0.1 (+local-development)";

    @Column(nullable = false)
    private int rateLimitPerMinute = 12;

    @Column(nullable = false)
    private int timeoutMs = 10000;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal reliabilityScore = new BigDecimal("50.00");

    @Column(nullable = false)
    private int fallbackPriority = 100;

    @Column(nullable = false)
    private boolean systemDisabled;

    @Column
    private OffsetDateTime quarantinedUntil;

    @Column(length = 1000)
    private String healthNote;

    @Column(columnDefinition = "text")
    private String selectorsJson;

    @Column
    private OffsetDateTime lastSuccessAt;

    @Column
    private OffsetDateTime lastFailureAt;

    @Column(nullable = false)
    private int consecutiveFailures;

    @Column(length = 1000)
    private String lastFailureReason;

    public void recordSuccess(OffsetDateTime finishedAt) {
        this.lastSuccessAt = finishedAt;
        this.consecutiveFailures = 0;
        this.lastFailureReason = null;
        this.quarantinedUntil = null;
        this.healthNote = "Latest scrape succeeded at " + finishedAt + ".";
    }

    public void recordFailure(OffsetDateTime finishedAt, String reason) {
        this.lastFailureAt = finishedAt;
        this.consecutiveFailures = this.consecutiveFailures + 1;
        this.lastFailureReason = reason == null ? "Unknown scrape failure." : reason;
        this.healthNote = this.lastFailureReason;
    }

    public boolean isTemporarilyQuarantined(OffsetDateTime referenceTime) {
        return quarantinedUntil != null && quarantinedUntil.isAfter(referenceTime);
    }
}
