package com.betai.service;

import com.betai.api.dto.DailyOddsExtractionRequest;
import com.betai.api.dto.PreMatchOddsRefreshRequest;
import com.betai.api.dto.PreMatchOddsRefreshResponse;
import com.betai.api.dto.PreMatchOddsSourceRefreshResponse;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.integration.sharpapi.SharpApiFetchResult;
import com.betai.integration.sharpapi.SharpApiSnapshotClient;
import com.betai.repository.LeagueRepository;
import com.betai.repository.RawSnapshotRepository;
import com.betai.repository.SourceTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PreMatchOddsRefreshServiceImpl implements PreMatchOddsRefreshService {

    private static final int QUARANTINE_FAILURE_THRESHOLD = 3;
    private static final int QUARANTINE_HOURS = 6;

    private final LeagueRepository leagueRepository;
    private final SourceTargetRepository sourceTargetRepository;
    private final RawSnapshotRepository rawSnapshotRepository;
    private final SharpApiSnapshotClient sharpApiSnapshotClient;
    private final OddsSourceExtractionService oddsSourceExtractionService;
    private final Clock clock;

    @Override
    public PreMatchOddsRefreshResponse refreshPreMatchOdds(PreMatchOddsRefreshRequest request) {
        OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
        LocalDate refreshDate = request.refreshDate() == null ? LocalDate.now(clock) : request.refreshDate();
        boolean forceScrape = Boolean.TRUE.equals(request.forceScrape());
        boolean forceReprocess = Boolean.TRUE.equals(request.forceReprocess());
        boolean recalculateExistingSelections = request.recalculateExistingSelections() == null
                || request.recalculateExistingSelections();
        List<League> leagues = resolveLeagues(request.leagueCodes());
        Set<LeagueCode> leagueCodes = leagues.stream()
                .map(League::getCode)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(LeagueCode.class)));

        List<PreMatchOddsSourceRefreshResponse> sourceRefreshes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        long lastProviderRequestStartedAtMillis = -1L;

        for (League league : leagues) {
            List<SourceTarget> targets = sourceTargetRepository
                    .findActiveByLeagueCodeAndSourceType(league.getCode(), SourceType.ODDS_REFERENCE)
                    .stream()
                    .filter(target -> !target.isSystemDisabled())
                    .sorted(sourceOrdering())
                    .toList();
            if (targets.isEmpty()) {
                warnings.add("No active ODDS_REFERENCE source targets are configured for " + league.getCode() + ".");
                continue;
            }
            for (SourceTarget target : eligibleTargets(targets)) {
                lastProviderRequestStartedAtMillis = waitForProviderRateLimit(
                        lastProviderRequestStartedAtMillis,
                        target.getRateLimitPerMinute()
                );
                sourceRefreshes.add(refreshSource(target, refreshDate, forceScrape));
            }
        }

        var extraction = oddsSourceExtractionService.extractDailyOddsSnapshots(new DailyOddsExtractionRequest(
                leagueCodes,
                refreshDate,
                forceReprocess,
                recalculateExistingSelections
        ));
        warnings.addAll(extraction.warnings());

        int successfulSnapshots = (int) sourceRefreshes.stream()
                .filter(response -> response.scrapeStatus() == ScrapeStatus.SUCCESS)
                .count();
        int cacheReusedSnapshots = (int) sourceRefreshes.stream()
                .filter(PreMatchOddsSourceRefreshResponse::cacheReused)
                .count();
        int failedSnapshots = sourceRefreshes.size() - successfulSnapshots;

        return new PreMatchOddsRefreshResponse(
                UUID.randomUUID(),
                triggeredAt,
                refreshDate,
                sourceRefreshes.size(),
                successfulSnapshots,
                cacheReusedSnapshots,
                failedSnapshots,
                List.copyOf(sourceRefreshes),
                extraction,
                List.copyOf(warnings)
        );
    }

    private long waitForProviderRateLimit(long lastRequestStartedAtMillis, int requestsPerMinute) {
        if (lastRequestStartedAtMillis < 0) {
            return clock.millis();
        }
        int effectiveRateLimit = Math.max(1, requestsPerMinute);
        long minimumIntervalMs = Math.ceilDiv(60_000L, effectiveRateLimit);
        long elapsedMs = Math.max(0L, clock.millis() - lastRequestStartedAtMillis);
        long waitMs = minimumIntervalMs - elapsedMs;
        if (waitMs > 0L) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for SharpAPI rate limit.", exception);
            }
        }
        return clock.millis();
    }

    private PreMatchOddsSourceRefreshResponse refreshSource(SourceTarget target, LocalDate refreshDate, boolean forceScrape) {
        if (!forceScrape) {
            var cached = rawSnapshotRepository
                    .findFirstBySourceTarget_IdAndSnapshotDateAndScrapeStatusOrderByFetchedAtDescCreatedAtDesc(
                            target.getId(),
                            refreshDate,
                            ScrapeStatus.SUCCESS
                    );
            if (cached.isPresent()) {
                RawSnapshot snapshot = cached.get();
                return new PreMatchOddsSourceRefreshResponse(
                        target.getId(),
                        snapshot.getId(),
                        target.getLeague().getCode().name(),
                        target.getName(),
                        snapshot.getSourceUrl(),
                        snapshot.getScrapeStatus(),
                        snapshot.getHttpStatusCode(),
                        snapshot.getFetchedAt(),
                        snapshot.getDurationMs(),
                        true,
                        "Cached same-day odds snapshot reused for source/date."
                );
            }
        }

        SharpApiFetchResult result = sharpApiSnapshotClient.fetch(target, refreshDate);
        RawSnapshot snapshot = persistSnapshot(target, refreshDate, result);
        updateTargetHealth(target, result);
        return new PreMatchOddsSourceRefreshResponse(
                target.getId(),
                snapshot.getId(),
                target.getLeague().getCode().name(),
                target.getName(),
                result.sourceUrl(),
                result.status(),
                result.httpStatusCode(),
                result.fetchedAt(),
                result.durationMs(),
                false,
                result.status() == ScrapeStatus.SUCCESS
                        ? "SharpAPI JSON fetched and stored as a raw snapshot."
                        : truncate(result.errorMessage(), 1000)
        );
    }

    private RawSnapshot persistSnapshot(SourceTarget target, LocalDate refreshDate, SharpApiFetchResult result) {
        return rawSnapshotRepository
                .findBySourceTarget_IdAndSnapshotDateAndChecksumSha256(
                        target.getId(),
                        refreshDate,
                        result.checksumSha256()
                )
                .orElseGet(() -> rawSnapshotRepository.save(new RawSnapshot()
                        .setSourceTarget(target)
                        .setLeague(target.getLeague())
                        .setDataRefreshLog(null)
                        .setSnapshotDate(refreshDate)
                        .setSourceUrl(result.sourceUrl())
                        .setScrapeStatus(result.status())
                        .setHttpStatusCode(result.httpStatusCode())
                        .setFetchedAt(result.fetchedAt())
                        .setDurationMs(result.durationMs())
                        .setChecksumSha256(result.checksumSha256())
                        .setContentType(result.contentType())
                        .setContentLength(result.contentLength())
                        .setResponseHeadersJson(result.responseHeadersJson())
                        .setRawPayload(result.rawPayload())
                        .setExtractedText(null)
                        .setErrorMessage(truncate(result.errorMessage(), 1000))));
    }

    private void updateTargetHealth(SourceTarget target, SharpApiFetchResult result) {
        OffsetDateTime finishedAt = OffsetDateTime.now(clock);
        if (result.status() == ScrapeStatus.SUCCESS) {
            target.recordSuccess(finishedAt);
            target.setReliabilityScore(target.getReliabilityScore()
                    .add(new BigDecimal("1.00"))
                    .min(new BigDecimal("99.00")));
        } else {
            target.recordFailure(finishedAt, truncate(result.errorMessage(), 1000));
            target.setReliabilityScore(target.getReliabilityScore()
                    .subtract(new BigDecimal("5.00"))
                    .max(new BigDecimal("1.00")));
            if (target.getConsecutiveFailures() >= QUARANTINE_FAILURE_THRESHOLD) {
                target.setQuarantinedUntil(finishedAt.plusHours(QUARANTINE_HOURS));
                target.setHealthNote("Temporarily quarantined until " + target.getQuarantinedUntil()
                        + " after " + target.getConsecutiveFailures() + " consecutive failures.");
            }
        }
        sourceTargetRepository.save(target);
    }

    private List<SourceTarget> eligibleTargets(List<SourceTarget> targets) {
        OffsetDateTime referenceTime = OffsetDateTime.now(clock);
        List<SourceTarget> eligible = targets.stream()
                .filter(target -> !target.isTemporarilyQuarantined(referenceTime))
                .toList();
        return eligible.isEmpty() ? targets : eligible;
    }

    private Comparator<SourceTarget> sourceOrdering() {
        return Comparator.comparingInt(SourceTarget::getFallbackPriority)
                .thenComparingInt(SourceTarget::getConsecutiveFailures)
                .thenComparing(SourceTarget::getReliabilityScore, Comparator.reverseOrder())
                .thenComparing(SourceTarget::getName);
    }

    private List<League> resolveLeagues(Set<LeagueCode> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            List<League> leagues = leagueRepository.findByActiveTrueAndScrapeEnabledTrueOrderByNameAsc();
            if (leagues.isEmpty()) {
                throw new ReferenceDataNotFoundException("No active leagues are configured.");
            }
            return leagues;
        }

        List<League> leagues = leagueRepository.findByCodeInAndActiveTrue(requestedCodes);
        Set<LeagueCode> activeCodes = leagues.stream().map(League::getCode).collect(Collectors.toSet());
        EnumSet<LeagueCode> missing = EnumSet.copyOf(requestedCodes);
        missing.removeAll(activeCodes);
        if (!missing.isEmpty()) {
            throw new ReferenceDataNotFoundException("Unsupported or inactive leagues: " + missing + ".");
        }
        return leagues;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
