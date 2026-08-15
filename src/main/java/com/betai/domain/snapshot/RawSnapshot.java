package com.betai.domain.snapshot;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.league.League;
import com.betai.domain.refresh.DataRefreshLog;
import com.betai.domain.source.SourceTarget;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "raw_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_raw_snapshots_target_date_checksum",
                        columnNames = {"source_target_id", "snapshot_date", "checksum_sha256"}
                )
        },
        indexes = {
                @Index(name = "idx_raw_snapshots_target_date", columnList = "source_target_id, snapshot_date"),
                @Index(name = "idx_raw_snapshots_league_date_status", columnList = "league_id, snapshot_date, scrape_status"),
                @Index(name = "idx_raw_snapshots_refresh_log", columnList = "data_refresh_log_id")
        }
)
public class RawSnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_target_id", nullable = false)
    private SourceTarget sourceTarget;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_refresh_log_id")
    private DataRefreshLog dataRefreshLog;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false, length = 1200)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScrapeStatus scrapeStatus;

    @Column
    private Integer httpStatusCode;

    @Column
    private OffsetDateTime fetchedAt;

    @Column
    private Long durationMs;

    @Column(nullable = false, length = 64)
    private String checksumSha256;

    @Column(length = 160)
    private String contentType;

    @Column
    private Long contentLength;

    @Column(columnDefinition = "text")
    private String responseHeadersJson;

    @Column(columnDefinition = "text")
    private String rawPayload;

    @Column(columnDefinition = "text")
    private String extractedText;

    @Column(length = 1000)
    private String errorMessage;

    @Column(length = 120)
    private String endpointName;

    @Column(columnDefinition = "text")
    private String requestParametersJson;

    @Column(length = 160)
    private String externalEntityId;

    @Column(length = 160)
    private String externalLeagueId;

    @Column(length = 64)
    private String sourceSeason;

    @Column(length = 160)
    private String externalFixtureId;

    @Column(length = 160)
    private String externalEventId;

    @Column(length = 80)
    private String parserVersion;

    @Column(length = 40)
    private String processingStatus;

    @Column(length = 1000)
    private String processingErrorSummary;
}
