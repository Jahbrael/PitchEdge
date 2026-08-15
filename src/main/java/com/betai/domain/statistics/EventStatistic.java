package com.betai.domain.statistics;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.match.Match;
import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.source.ExternalSourceType;
import com.betai.domain.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "event_statistics",
        indexes = {
                @Index(name = "idx_event_statistics_match_code", columnList = "match_id, statistic_code"),
                @Index(name = "idx_event_statistics_team_code", columnList = "team_id, statistic_code")
        }
)
public class EventStatistic extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_snapshot_id")
    private RawSnapshot rawSnapshot;

    @Column(nullable = false, length = 64)
    private String statisticCode;

    @Column(nullable = false, length = 120)
    private String statisticName;

    @Column(precision = 12, scale = 4)
    private BigDecimal numericValue;

    @Column(length = 240)
    private String textValue;

    @Column(length = 32)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private ExternalSourceType sourceType;

    @Column(nullable = false, length = 160)
    private String sourceStatisticName;

    @Column(nullable = false)
    private OffsetDateTime retrievedAt;
}
