package com.betai.service;

import com.betai.api.dto.DailyRefreshRequest;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.refresh.DataRefreshLog;
import com.betai.domain.refresh.RefreshStatus;
import com.betai.repository.DataRefreshLogRepository;
import com.betai.repository.LeagueRepository;
import com.betai.scraping.ScrapeRunSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyRefreshServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-20T08:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate REFRESH_DATE = LocalDate.parse("2026-06-20");

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private DataRefreshLogRepository dataRefreshLogRepository;
    @Mock
    private SourceRefreshService sourceRefreshService;

    private DailyRefreshServiceImpl service;
    private League league;

    @BeforeEach
    void setUp() {
        service = new DailyRefreshServiceImpl(
                leagueRepository,
                dataRefreshLogRepository,
                sourceRefreshService,
                new NoOpTransactionManager(),
                CLOCK
        );
        league = new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1)
                .setCurrentSeason("2025/2026");
        league.setId(UUID.randomUUID());
        when(leagueRepository.findByCodeInAndActiveTrue(Set.of(LeagueCode.PREMIER_LEAGUE))).thenReturn(List.of(league));
    }

    @Test
    void activeRunningRefreshIsReusedInsteadOfStartingDuplicateRun() {
        DataRefreshLog running = log(RefreshStatus.RUNNING, OffsetDateTime.parse("2026-06-20T07:59:00Z"));
        when(dataRefreshLogRepository.findByLeagueDateForUpdate(LeagueCode.PREMIER_LEAGUE, REFRESH_DATE))
                .thenReturn(List.of(running));

        var response = service.triggerDailyRefresh(new DailyRefreshRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE),
                REFRESH_DATE,
                true
        ));

        var refresh = response.refreshLogs().getFirst();
        assertThat(refresh.status()).isEqualTo(RefreshStatus.RUNNING);
        assertThat(refresh.cacheReused()).isTrue();
        assertThat(refresh.message()).isEqualTo("Refresh already running for league/date.");
        verify(sourceRefreshService, never()).refreshLeagueSources(any(), any(), any());
        verify(dataRefreshLogRepository, never()).saveAndFlush(any());
    }

    @Test
    void completionSkipsCurrentRunWhenAnotherSuccessAlreadyExistsForLeagueDate() {
        AtomicReference<DataRefreshLog> startedLog = new AtomicReference<>();
        DataRefreshLog existingSuccess = log(RefreshStatus.SUCCESS, OffsetDateTime.parse("2026-06-20T07:55:00Z"));

        when(dataRefreshLogRepository.findByLeagueDateForUpdate(LeagueCode.PREMIER_LEAGUE, REFRESH_DATE))
                .thenReturn(List.of())
                .thenReturn(List.of(existingSuccess));
        when(leagueRepository.findByCode(LeagueCode.PREMIER_LEAGUE)).thenReturn(Optional.of(league));
        when(dataRefreshLogRepository.saveAndFlush(any(DataRefreshLog.class))).thenAnswer(invocation -> {
            DataRefreshLog log = invocation.getArgument(0);
            log.setId(UUID.randomUUID());
            startedLog.set(log);
            return log;
        });
        when(dataRefreshLogRepository.findById(any(UUID.class))).thenAnswer(invocation -> Optional.of(startedLog.get()));
        when(dataRefreshLogRepository.save(any(DataRefreshLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sourceRefreshService.refreshLeagueSources(eq(league), any(DataRefreshLog.class), eq(REFRESH_DATE)))
                .thenReturn(new ScrapeRunSummary(1, 1, 0, 0, 0, List.of(UUID.randomUUID()), "checksum"));

        var response = service.triggerDailyRefresh(new DailyRefreshRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE),
                REFRESH_DATE,
                true
        ));

        var refresh = response.refreshLogs().getFirst();
        assertThat(refresh.status()).isEqualTo(RefreshStatus.SKIPPED);
        assertThat(refresh.cacheReused()).isTrue();
        assertThat(refresh.message()).isEqualTo("Another successful refresh already exists for this league/date.");
        assertThat(startedLog.get().getRefreshStatus()).isEqualTo(RefreshStatus.SKIPPED);
        assertThat(startedLog.get().getFailureReason()).isEqualTo("Another successful refresh already exists for this league/date.");
    }

    private DataRefreshLog log(RefreshStatus status, OffsetDateTime startedAt) {
        DataRefreshLog log = new DataRefreshLog()
                .setLeague(league)
                .setRefreshDate(REFRESH_DATE)
                .setRefreshStatus(status)
                .setStartedAt(startedAt)
                .setSourceCount(1)
                .setRecordsIngested(1L)
                .setRecordsRejected(0L);
        log.setId(UUID.randomUUID());
        return log;
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
