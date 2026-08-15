package com.betai.scraping;

import com.betai.domain.league.League;
import com.betai.domain.refresh.DataRefreshLog;
import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.source.SourceTarget;
import com.betai.repository.RawSnapshotRepository;
import com.betai.repository.SourceTargetRepository;
import com.betai.service.SourceRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SourceRefreshServiceImpl implements SourceRefreshService {

    private static final int QUARANTINE_FAILURE_THRESHOLD = 3;
    private static final int QUARANTINE_HOURS = 6;

    private final SourceTargetRepository sourceTargetRepository;
    private final RawSnapshotRepository rawSnapshotRepository;
    private final StaticHttpSourceScraper staticHttpSourceScraper;
    private final HashingService hashingService;
    private final Clock clock;

    @Override
    public ScrapeRunSummary refreshLeagueSources(League league, DataRefreshLog refreshLog, LocalDate refreshDate) {
        OffsetDateTime referenceTime = OffsetDateTime.now(clock);
        List<SourceTarget> activeTargets = sourceTargetRepository.findActiveByLeagueCode(league.getCode()).stream()
                .filter(target -> !target.isSystemDisabled())
                .toList();
        List<SourceTarget> targets = eligibleTargets(activeTargets, referenceTime);
        int successCount = 0;
        int failedCount = 0;
        int robotsBlockedCount = 0;
        int unsupportedRenderCount = 0;
        List<UUID> snapshotIds = new ArrayList<>();
        List<String> checksums = new ArrayList<>();

        for (SourceTarget target : targets) {
            ScrapeOutcome outcome = staticHttpSourceScraper.scrape(target, refreshDate);
            RawSnapshot snapshot = persistSnapshot(target, refreshLog, refreshDate, outcome);
            snapshotIds.add(snapshot.getId());
            checksums.add(snapshot.getChecksumSha256());
            updateTargetHealth(target, outcome);

            switch (outcome.scrapeStatus()) {
                case SUCCESS -> successCount++;
                case ROBOTS_BLOCKED -> robotsBlockedCount++;
                case UNSUPPORTED_RENDER_MODE -> unsupportedRenderCount++;
                case FAILED -> failedCount++;
            }
        }

        return new ScrapeRunSummary(
                activeTargets.size(),
                successCount,
                failedCount,
                robotsBlockedCount,
                unsupportedRenderCount,
                List.copyOf(snapshotIds),
                hashingService.aggregate(checksums)
        );
    }

    private List<SourceTarget> eligibleTargets(List<SourceTarget> activeTargets, OffsetDateTime referenceTime) {
        List<SourceTarget> eligible = activeTargets.stream()
                .filter(target -> !target.isTemporarilyQuarantined(referenceTime))
                .sorted(sourceOrdering())
                .toList();
        if (!eligible.isEmpty()) {
            return eligible;
        }
        return activeTargets.stream()
                .sorted(sourceOrdering())
                .toList();
    }

    private Comparator<SourceTarget> sourceOrdering() {
        return Comparator.comparingInt(SourceTarget::getFallbackPriority)
                .thenComparingInt(SourceTarget::getConsecutiveFailures)
                .thenComparing(SourceTarget::getReliabilityScore, Comparator.reverseOrder())
                .thenComparing(SourceTarget::getName);
    }

    private RawSnapshot persistSnapshot(
            SourceTarget target,
            DataRefreshLog refreshLog,
            LocalDate refreshDate,
            ScrapeOutcome outcome
    ) {
        return rawSnapshotRepository
                .findBySourceTarget_IdAndSnapshotDateAndChecksumSha256(
                        target.getId(),
                        refreshDate,
                        outcome.checksumSha256()
                )
                .orElseGet(() -> rawSnapshotRepository.save(new RawSnapshot()
                        .setSourceTarget(target)
                        .setLeague(target.getLeague())
                        .setDataRefreshLog(refreshLog)
                        .setSnapshotDate(refreshDate)
                        .setSourceUrl(outcome.sourceUrl())
                        .setScrapeStatus(outcome.scrapeStatus())
                        .setHttpStatusCode(outcome.httpStatusCode())
                        .setFetchedAt(outcome.fetchedAt())
                        .setDurationMs(outcome.durationMs())
                        .setChecksumSha256(outcome.checksumSha256())
                        .setContentType(outcome.contentType())
                        .setContentLength(outcome.contentLength())
                        .setResponseHeadersJson(outcome.responseHeadersJson())
                        .setRawPayload(outcome.rawPayload())
                        .setExtractedText(outcome.extractedText())
                        .setErrorMessage(truncate(outcome.errorMessage(), 1000))));
    }

    private void updateTargetHealth(SourceTarget target, ScrapeOutcome outcome) {
        OffsetDateTime finishedAt = OffsetDateTime.now(clock);
        if (outcome.scrapeStatus() == ScrapeStatus.SUCCESS) {
            target.recordSuccess(finishedAt);
            target.setReliabilityScore(target.getReliabilityScore()
                    .add(new java.math.BigDecimal("1.00"))
                    .min(new java.math.BigDecimal("99.00")));
        } else {
            target.recordFailure(finishedAt, truncate(outcome.errorMessage(), 1000));
            target.setReliabilityScore(target.getReliabilityScore()
                    .subtract(new java.math.BigDecimal("5.00"))
                    .max(new java.math.BigDecimal("1.00")));
            if (target.getConsecutiveFailures() >= QUARANTINE_FAILURE_THRESHOLD) {
                target.setQuarantinedUntil(finishedAt.plusHours(QUARANTINE_HOURS));
                target.setHealthNote("Temporarily quarantined until " + target.getQuarantinedUntil()
                        + " after " + target.getConsecutiveFailures() + " consecutive failures.");
            }
        }
        sourceTargetRepository.save(target);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
