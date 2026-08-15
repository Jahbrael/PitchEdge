package com.betai.service;

import com.betai.api.dto.DailyRefreshRequest;
import com.betai.api.dto.DailyRefreshResponse;
import com.betai.api.dto.RefreshLogResponse;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.refresh.DataRefreshLog;
import com.betai.domain.refresh.RefreshStatus;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.repository.DataRefreshLogRepository;
import com.betai.repository.LeagueRepository;
import com.betai.scraping.ScrapeRunSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyRefreshServiceImpl implements DailyRefreshService {

    private static final Duration STALE_RUNNING_REFRESH_AFTER = Duration.ofHours(2);

    private final LeagueRepository leagueRepository;
    private final DataRefreshLogRepository dataRefreshLogRepository;
    private final SourceRefreshService sourceRefreshService;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Override
    public DailyRefreshResponse triggerDailyRefresh(DailyRefreshRequest request) {
        OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
        LocalDate refreshDate = request.refreshDate() == null ? LocalDate.now(clock) : request.refreshDate();
        List<League> leagues = resolveLeagues(request.leagueCodes());

        List<RefreshLogResponse> logs = leagues.stream()
                .sorted(Comparator.comparing(league -> league.getCode().name()))
                .map(league -> refreshLeague(league, refreshDate, request.forceRefresh(), triggeredAt))
                .toList();

        return new DailyRefreshResponse(UUID.randomUUID(), triggeredAt, logs);
    }

    private List<League> resolveLeagues(Set<LeagueCode> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            List<League> scrapeEnabledLeagues = leagueRepository.findByActiveTrueAndScrapeEnabledTrueOrderByNameAsc();
            if (scrapeEnabledLeagues.isEmpty()) {
                throw new ReferenceDataNotFoundException("No active scrape-enabled leagues are configured.");
            }
            return scrapeEnabledLeagues;
        }

        List<League> leagues = leagueRepository.findByCodeInAndActiveTrue(requestedCodes);
        Set<LeagueCode> activeCodes = leagues.stream()
                .map(League::getCode)
                .collect(Collectors.toSet());
        EnumSet<LeagueCode> missing = EnumSet.copyOf(requestedCodes);
        missing.removeAll(activeCodes);
        if (!missing.isEmpty()) {
            throw new ReferenceDataNotFoundException("Unsupported or inactive leagues: " + missing + ".");
        }
        return leagues;
    }

    private RefreshLogResponse refreshLeague(League league, LocalDate refreshDate, boolean forceRefresh, OffsetDateTime startedAt) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        RefreshStart refreshStart = transactionTemplate.execute(status -> startRefresh(league, refreshDate, forceRefresh, startedAt));
        if (refreshStart.reusedResponse() != null) {
            return refreshStart.reusedResponse();
        }

        try {
            ScrapeRunSummary summary = sourceRefreshService.refreshLeagueSources(league, refreshStart.refreshLog(), refreshDate);
            return transactionTemplate.execute(status -> completeRefresh(refreshStart.refreshLog().getId(), summary));
        } catch (Exception exception) {
            return transactionTemplate.execute(status -> failRefresh(refreshStart.refreshLog().getId(), exception));
        }
    }

    private RefreshStart startRefresh(League league, LocalDate refreshDate, boolean forceRefresh, OffsetDateTime startedAt) {
        List<DataRefreshLog> existingLogs = dataRefreshLogRepository.findByLeagueDateForUpdate(league.getCode(), refreshDate);
        var activeRunning = existingLogs.stream()
                .filter(log -> log.getRefreshStatus() == RefreshStatus.RUNNING)
                .filter(log -> !staleRunning(log, startedAt))
                .findFirst();
        if (activeRunning.isPresent()) {
            return RefreshStart.reused(RefreshLogResponse.from(
                    activeRunning.get(),
                    true,
                    "Refresh already running for league/date."
            ));
        }

        existingLogs.stream()
                .filter(log -> log.getRefreshStatus() == RefreshStatus.RUNNING)
                .filter(log -> staleRunning(log, startedAt))
                .forEach(log -> {
                    log.markFailed(startedAt, "Previous refresh was abandoned before completion.");
                    dataRefreshLogRepository.save(log);
                });
        if (!existingLogs.isEmpty()) {
            dataRefreshLogRepository.flush();
        }

        var existingSuccess = existingLogs.stream()
                .filter(log -> log.getRefreshStatus() == RefreshStatus.SUCCESS)
                .findFirst();

        if (existingSuccess.isPresent() && !forceRefresh) {
            return RefreshStart.reused(RefreshLogResponse.from(
                    existingSuccess.get(),
                    true,
                    "Cached daily refresh reused for league/date."
            ));
        }

        existingSuccess.ifPresent(log -> {
            log.markSuperseded();
            dataRefreshLogRepository.save(log);
            dataRefreshLogRepository.flush();
        });

        League managedLeague = leagueRepository.findByCode(league.getCode())
                .orElseThrow(() -> new ReferenceDataNotFoundException("League is not configured: " + league.getCode() + "."));

        DataRefreshLog log = new DataRefreshLog()
                .setLeague(managedLeague)
                .setRefreshDate(refreshDate)
                .setRefreshStatus(RefreshStatus.RUNNING)
                .setStartedAt(startedAt)
                .setSourceCount(0)
                .setRecordsIngested(0L)
                .setRecordsRejected(0L);

        return RefreshStart.started(dataRefreshLogRepository.saveAndFlush(log));
    }

    private RefreshLogResponse completeRefresh(UUID refreshLogId, ScrapeRunSummary summary) {
        DataRefreshLog log = dataRefreshLogRepository.findById(refreshLogId)
                .orElseThrow(() -> new ReferenceDataNotFoundException("Refresh log not found: " + refreshLogId + "."));
        OffsetDateTime finishedAt = OffsetDateTime.now(clock);
        List<DataRefreshLog> existingLogs = dataRefreshLogRepository.findByLeagueDateForUpdate(
                log.getLeague().getCode(),
                log.getRefreshDate()
        );

        if (summary.targetCount() == 0) {
            log.setSourceCount(0);
            log.setRecordsIngested(0L);
            log.setRecordsRejected(0L);
            log.markSkipped(finishedAt, "No active source targets are configured for this league.");
            return RefreshLogResponse.from(dataRefreshLogRepository.save(log), false, "No active source targets are configured for this league.");
        }

        if (summary.successCount() == 0 && summary.rejectedCount() > 0) {
            log.setSourceCount(summary.targetCount());
            log.setRecordsIngested(0L);
            log.setRecordsRejected((long) summary.rejectedCount());
            log.markFailed(finishedAt, "All configured source targets failed, were blocked by robots.txt, or require unsupported rendering.");
            return RefreshLogResponse.from(dataRefreshLogRepository.save(log), false, "All source targets failed or were blocked.");
        }

        var existingSuccess = existingLogs.stream()
                .filter(existing -> existing.getRefreshStatus() == RefreshStatus.SUCCESS)
                .filter(existing -> !existing.getId().equals(log.getId()))
                .findFirst();
        if (existingSuccess.isPresent()) {
            log.markSkipped(finishedAt, "Another successful refresh already exists for this league/date.");
            return RefreshLogResponse.from(
                    dataRefreshLogRepository.save(log),
                    true,
                    "Another successful refresh already exists for this league/date."
            );
        }

        log.markSucceeded(
                finishedAt,
                summary.targetCount(),
                summary.successCount(),
                summary.rejectedCount(),
                summary.rawPayloadReference(),
                summary.aggregateChecksum()
        );

        return RefreshLogResponse.from(
                dataRefreshLogRepository.save(log),
                false,
                "Daily source refresh completed. Raw snapshots are stored before any extraction or normalization."
        );
    }

    private RefreshLogResponse failRefresh(UUID refreshLogId, Exception exception) {
        DataRefreshLog log = dataRefreshLogRepository.findById(refreshLogId)
                .orElseThrow(() -> new ReferenceDataNotFoundException("Refresh log not found: " + refreshLogId + "."));
        log.markFailed(OffsetDateTime.now(clock), truncate(exception.getMessage(), 1000));
        return RefreshLogResponse.from(dataRefreshLogRepository.save(log), false, "Daily source refresh failed.");
    }

    private boolean staleRunning(DataRefreshLog log, OffsetDateTime now) {
        return log.getStartedAt() != null
                && Duration.between(log.getStartedAt(), now).compareTo(STALE_RUNNING_REFRESH_AFTER) > 0;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record RefreshStart(DataRefreshLog refreshLog, RefreshLogResponse reusedResponse) {

        static RefreshStart started(DataRefreshLog refreshLog) {
            return new RefreshStart(refreshLog, null);
        }

        static RefreshStart reused(RefreshLogResponse response) {
            return new RefreshStart(null, response);
        }
    }
}
