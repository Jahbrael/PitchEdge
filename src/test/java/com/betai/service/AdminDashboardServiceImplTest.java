package com.betai.service;

import com.betai.domain.automation.AutomationRunStatus;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.pipeline.PipelineRun;
import com.betai.domain.pipeline.PipelineStatus;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.refresh.DataRefreshLog;
import com.betai.domain.refresh.RefreshStatus;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.repository.AutomationRunRepository;
import com.betai.repository.BacktestRunRepository;
import com.betai.repository.BookmakerRepository;
import com.betai.repository.DataRefreshLogRepository;
import com.betai.repository.ExtractionRunRepository;
import com.betai.repository.FeatureGenerationRunRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.ModelAccuracyDailyRepository;
import com.betai.repository.ModelTuningProfileRepository;
import com.betai.repository.OddsExtractionRunRepository;
import com.betai.repository.OddsSnapshotRepository;
import com.betai.repository.PipelineRunRepository;
import com.betai.repository.PredictionGenerationRunRepository;
import com.betai.repository.PredictionSelectionRepository;
import com.betai.repository.SettlementRunRepository;
import com.betai.repository.SourceTargetRepository;
import com.betai.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-18T08:00:00Z"), ZoneOffset.UTC);
    private static final String DUPLICATE_SUCCESS_FAILURE = """
            could not execute statement [ERROR: duplicate key value violates unique constraint \
            "ux_data_refresh_logs_one_success_per_league_date" Detail: Key (league_id, refresh_date) already exists.]
            """;

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private SourceTargetRepository sourceTargetRepository;
    @Mock
    private DataRefreshLogRepository dataRefreshLogRepository;
    @Mock
    private ExtractionRunRepository extractionRunRepository;
    @Mock
    private FeatureGenerationRunRepository featureGenerationRunRepository;
    @Mock
    private PredictionGenerationRunRepository predictionGenerationRunRepository;
    @Mock
    private SettlementRunRepository settlementRunRepository;
    @Mock
    private PredictionSelectionRepository predictionSelectionRepository;
    @Mock
    private MarketDefinitionRepository marketDefinitionRepository;
    @Mock
    private ModelAccuracyDailyRepository modelAccuracyDailyRepository;
    @Mock
    private BookmakerRepository bookmakerRepository;
    @Mock
    private OddsSnapshotRepository oddsSnapshotRepository;
    @Mock
    private ModelTuningProfileRepository modelTuningProfileRepository;
    @Mock
    private OddsExtractionRunRepository oddsExtractionRunRepository;
    @Mock
    private PipelineRunRepository pipelineRunRepository;
    @Mock
    private BacktestRunRepository backtestRunRepository;
    @Mock
    private AutomationRunRepository automationRunRepository;

    private AdminDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardServiceImpl(
                CLOCK,
                leagueRepository,
                teamRepository,
                matchRepository,
                sourceTargetRepository,
                dataRefreshLogRepository,
                extractionRunRepository,
                featureGenerationRunRepository,
                predictionGenerationRunRepository,
                settlementRunRepository,
                predictionSelectionRepository,
                marketDefinitionRepository,
                modelAccuracyDailyRepository,
                bookmakerRepository,
                oddsSnapshotRepository,
                modelTuningProfileRepository,
                oddsExtractionRunRepository,
                pipelineRunRepository,
                backtestRunRepository,
                automationRunRepository,
                new CompetitionHistoryPolicyService()
        );
    }

    @Test
    void successfulRefreshWithTooLittleImportedDataIsFlaggedAsLowCoverage() {
        League league = league(LeagueCode.MEISTRILIIGA);
        DataRefreshLog refreshLog = new DataRefreshLog()
                .setLeague(league)
                .setRefreshDate(LocalDate.of(2026, 6, 18))
                .setRefreshStatus(RefreshStatus.SUCCESS)
                .setStartedAt(OffsetDateTime.parse("2026-06-18T06:15:00Z"))
                .setFinishedAt(OffsetDateTime.parse("2026-06-18T06:15:04Z"))
                .setDurationMs(4_000L);

        when(leagueRepository.findAll()).thenReturn(List.of(league));
        when(sourceTargetRepository.findTop50ByActiveTrueOrderByConsecutiveFailuresDescLastFailureAtDescNameAsc())
                .thenReturn(List.of(disabledSgoddsSource(league)));
        when(sourceTargetRepository.count()).thenReturn(2L);
        when(sourceTargetRepository.countByActiveTrue()).thenReturn(2L);
        when(sourceTargetRepository.countByLeague_Code(LeagueCode.MEISTRILIIGA)).thenReturn(2L);
        when(sourceTargetRepository.countByLeague_CodeAndActiveTrue(LeagueCode.MEISTRILIIGA)).thenReturn(2L);
        when(dataRefreshLogRepository.findFirstByLeague_CodeOrderByStartedAtDesc(LeagueCode.MEISTRILIIGA))
                .thenReturn(Optional.of(refreshLog));
        when(dataRefreshLogRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(extractionRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(oddsExtractionRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(featureGenerationRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(predictionGenerationRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(settlementRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(pipelineRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(backtestRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(automationRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(teamRepository.countByLeague_Code(LeagueCode.MEISTRILIIGA)).thenReturn(10L);
        when(matchRepository.countByLeague_Code(LeagueCode.MEISTRILIIGA)).thenReturn(16L);
        when(matchRepository.countByLeague_CodeAndStatus(LeagueCode.MEISTRILIIGA, MatchStatus.SCHEDULED)).thenReturn(1L);
        when(matchRepository.countByLeague_CodeAndStatus(LeagueCode.MEISTRILIIGA, MatchStatus.FINISHED)).thenReturn(15L);
        when(matchRepository.summarizeSeasonsByLeagueCode(LeagueCode.MEISTRILIIGA)).thenReturn(List.of(
                seasonSummary("2026", 10, 9, 1, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 17)),
                seasonSummary("2025", 6, 6, 0, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 11, 30))
        ));
        when(matchRepository.count()).thenReturn(16L);
        when(matchRepository.countByStatus(MatchStatus.SCHEDULED)).thenReturn(1L);
        when(matchRepository.countByStatus(MatchStatus.FINISHED)).thenReturn(15L);
        when(predictionSelectionRepository.countByOutcome(PredictionOutcome.PENDING)).thenReturn(0L);
        when(predictionSelectionRepository.countByOutcome(PredictionOutcome.WON)).thenReturn(0L);
        when(predictionSelectionRepository.countByOutcome(PredictionOutcome.LOST)).thenReturn(0L);
        when(predictionSelectionRepository.countByOutcome(PredictionOutcome.VOID)).thenReturn(0L);
        when(predictionSelectionRepository.countByBestOddsSnapshotIsNotNull()).thenReturn(0L);
        when(predictionSelectionRepository.countByExpectedValueGreaterThan(BigDecimal.ZERO)).thenReturn(0L);
        when(bookmakerRepository.countByActiveTrue()).thenReturn(0L);
        when(modelTuningProfileRepository.countByActiveTrue()).thenReturn(0L);
        when(automationRunRepository.countByRunStatus(AutomationRunStatus.FAILED)).thenReturn(0L);

        var overview = service.getOverview();
        var status = overview.leagues().getFirst();

        assertThat(status.latestRefreshStatus()).isEqualTo("SUCCESS");
        assertThat(status.dataCoverageStatus()).isEqualTo("LOW_HISTORY_AND_FIXTURES");
        assertThat(status.dataCoverageMessage()).contains("Only 15 finished match(es)", "1 scheduled fixture(s)", "30");
        assertThat(status.historyPolicy()).isEqualTo("LEAGUE_SEASONS");
        assertThat(status.requiredSeasonCount()).isEqualTo(3);
        assertThat(status.importedSeasonLabels()).containsExactly("2026", "2025");
        assertThat(status.seasonBreakdowns()).hasSize(2);
        assertThat(status.seasonBreakdowns().getFirst().matches()).isEqualTo(10);
        assertThat(overview.sources()).isEmpty();
        assertThat(overview.alerts()).contains(
                "1 active league(s) have too little imported history for reliable calibration: MEISTRILIIGA.",
                "1 active league(s) have very low scheduled fixture coverage: MEISTRILIIGA."
        );
    }

    @Test
    void leagueWithImportedMatchesUsesSuccessfulPipelineRunWhenRefreshLogIsMissing() {
        League league = league(LeagueCode.COPA_DEL_REY);
        PipelineRun pipelineRun = new PipelineRun()
                .setLeagueCodes("COPA_DEL_REY,COPA_LIBERTADORES")
                .setPipelineStatus(PipelineStatus.SUCCESS)
                .setStartedAt(OffsetDateTime.parse("2026-07-02T20:57:23Z"))
                .setFinishedAt(OffsetDateTime.parse("2026-07-02T21:18:47Z"))
                .setDurationMs(1_283_000L)
                .setStepSummaryJson("[{\"step\":\"THESPORTSDB_REFRESH\",\"status\":\"SUCCESS\"}]");

        when(leagueRepository.findAll()).thenReturn(List.of(league));
        when(sourceTargetRepository.findTop50ByActiveTrueOrderByConsecutiveFailuresDescLastFailureAtDescNameAsc())
                .thenReturn(List.of());
        when(sourceTargetRepository.count()).thenReturn(2L);
        when(sourceTargetRepository.countByActiveTrue()).thenReturn(2L);
        when(sourceTargetRepository.countByLeague_Code(LeagueCode.COPA_DEL_REY)).thenReturn(2L);
        when(sourceTargetRepository.countByLeague_CodeAndActiveTrue(LeagueCode.COPA_DEL_REY)).thenReturn(2L);
        when(dataRefreshLogRepository.findFirstByLeague_CodeOrderByStartedAtDesc(LeagueCode.COPA_DEL_REY))
                .thenReturn(Optional.empty());
        when(dataRefreshLogRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(extractionRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(oddsExtractionRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(featureGenerationRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(predictionGenerationRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(settlementRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(pipelineRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of(pipelineRun));
        when(backtestRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(automationRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(teamRepository.countByLeague_Code(LeagueCode.COPA_DEL_REY)).thenReturn(120L);
        when(matchRepository.countByLeague_Code(LeagueCode.COPA_DEL_REY)).thenReturn(367L);
        when(matchRepository.countByLeague_CodeAndStatus(LeagueCode.COPA_DEL_REY, MatchStatus.SCHEDULED)).thenReturn(0L);
        when(matchRepository.countByLeague_CodeAndStatus(LeagueCode.COPA_DEL_REY, MatchStatus.FINISHED)).thenReturn(367L);
        when(matchRepository.summarizeSeasonsByLeagueCode(LeagueCode.COPA_DEL_REY)).thenReturn(List.of(
                seasonSummary("2025/2026", 123, 123, 0, LocalDate.of(2025, 8, 1), LocalDate.of(2026, 5, 30)),
                seasonSummary("2024/2025", 122, 122, 0, LocalDate.of(2024, 8, 1), LocalDate.of(2025, 5, 30)),
                seasonSummary("2023/2024", 122, 122, 0, LocalDate.of(2023, 8, 1), LocalDate.of(2024, 5, 30))
        ));
        when(matchRepository.count()).thenReturn(367L);
        when(matchRepository.countByStatus(MatchStatus.SCHEDULED)).thenReturn(0L);
        when(matchRepository.countByStatus(MatchStatus.FINISHED)).thenReturn(367L);
        when(predictionSelectionRepository.countByOutcome(PredictionOutcome.PENDING)).thenReturn(0L);
        when(predictionSelectionRepository.countByOutcome(PredictionOutcome.WON)).thenReturn(0L);
        when(predictionSelectionRepository.countByOutcome(PredictionOutcome.LOST)).thenReturn(0L);
        when(predictionSelectionRepository.countByOutcome(PredictionOutcome.VOID)).thenReturn(0L);
        when(predictionSelectionRepository.countByBestOddsSnapshotIsNotNull()).thenReturn(0L);
        when(predictionSelectionRepository.countByExpectedValueGreaterThan(BigDecimal.ZERO)).thenReturn(0L);
        when(bookmakerRepository.countByActiveTrue()).thenReturn(0L);
        when(modelTuningProfileRepository.countByActiveTrue()).thenReturn(0L);
        when(automationRunRepository.countByRunStatus(AutomationRunStatus.FAILED)).thenReturn(0L);

        var overview = service.getOverview();
        var status = overview.leagues().getFirst();

        assertThat(status.historyStatus()).isEqualTo("COMPLETE");
        assertThat(status.latestRefreshStatus()).isEqualTo("SUCCESS");
        assertThat(status.latestRefreshStartedAt()).isEqualTo(OffsetDateTime.parse("2026-07-02T20:57:23Z"));
        assertThat(status.latestRefreshDurationMs()).isEqualTo(1_283_000L);
    }

    @Test
    void duplicateSuccessRefreshFailureDoesNotOverrideExistingSuccessfulRefresh() {
        League league = league(LeagueCode.PREMIER_LEAGUE);
        LocalDate refreshDate = LocalDate.of(2026, 6, 20);
        DataRefreshLog duplicateFailure = new DataRefreshLog()
                .setLeague(league)
                .setRefreshDate(refreshDate)
                .setRefreshStatus(RefreshStatus.FAILED)
                .setStartedAt(OffsetDateTime.parse("2026-06-20T08:00:00Z"))
                .setFinishedAt(OffsetDateTime.parse("2026-06-20T08:00:01Z"))
                .setDurationMs(1_000L)
                .setFailureReason(DUPLICATE_SUCCESS_FAILURE);
        DataRefreshLog successfulRefresh = new DataRefreshLog()
                .setLeague(league)
                .setRefreshDate(refreshDate)
                .setRefreshStatus(RefreshStatus.SUCCESS)
                .setStartedAt(OffsetDateTime.parse("2026-06-20T07:58:00Z"))
                .setFinishedAt(OffsetDateTime.parse("2026-06-20T07:58:04Z"))
                .setDurationMs(4_000L);

        when(leagueRepository.findAll()).thenReturn(List.of(league));
        when(sourceTargetRepository.findTop50ByActiveTrueOrderByConsecutiveFailuresDescLastFailureAtDescNameAsc())
                .thenReturn(List.of());
        when(sourceTargetRepository.count()).thenReturn(1L);
        when(sourceTargetRepository.countByActiveTrue()).thenReturn(1L);
        when(sourceTargetRepository.countByLeague_Code(LeagueCode.PREMIER_LEAGUE)).thenReturn(1L);
        when(sourceTargetRepository.countByLeague_CodeAndActiveTrue(LeagueCode.PREMIER_LEAGUE)).thenReturn(1L);
        when(dataRefreshLogRepository.findFirstByLeague_CodeOrderByStartedAtDesc(LeagueCode.PREMIER_LEAGUE))
                .thenReturn(Optional.of(duplicateFailure));
        when(dataRefreshLogRepository.findFirstByLeague_CodeAndRefreshDateAndRefreshStatusOrderByStartedAtDesc(
                LeagueCode.PREMIER_LEAGUE,
                refreshDate,
                RefreshStatus.SUCCESS
        )).thenReturn(Optional.of(successfulRefresh));
        when(dataRefreshLogRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of(duplicateFailure));
        when(extractionRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(oddsExtractionRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(featureGenerationRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(predictionGenerationRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(settlementRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(pipelineRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(backtestRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(automationRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
        when(teamRepository.countByLeague_Code(LeagueCode.PREMIER_LEAGUE)).thenReturn(20L);
        when(matchRepository.countByLeague_Code(LeagueCode.PREMIER_LEAGUE)).thenReturn(100L);
        when(matchRepository.countByLeague_CodeAndStatus(LeagueCode.PREMIER_LEAGUE, MatchStatus.SCHEDULED)).thenReturn(5L);
        when(matchRepository.countByLeague_CodeAndStatus(LeagueCode.PREMIER_LEAGUE, MatchStatus.FINISHED)).thenReturn(95L);
        when(matchRepository.summarizeSeasonsByLeagueCode(LeagueCode.PREMIER_LEAGUE)).thenReturn(List.of());
        when(matchRepository.count()).thenReturn(100L);
        when(matchRepository.countByStatus(MatchStatus.SCHEDULED)).thenReturn(5L);
        when(matchRepository.countByStatus(MatchStatus.FINISHED)).thenReturn(95L);
        when(predictionSelectionRepository.countByOutcome(PredictionOutcome.PENDING)).thenReturn(0L);
        when(predictionSelectionRepository.countByOutcome(PredictionOutcome.WON)).thenReturn(0L);
        when(predictionSelectionRepository.countByOutcome(PredictionOutcome.LOST)).thenReturn(0L);
        when(predictionSelectionRepository.countByOutcome(PredictionOutcome.VOID)).thenReturn(0L);
        when(predictionSelectionRepository.countByBestOddsSnapshotIsNotNull()).thenReturn(0L);
        when(predictionSelectionRepository.countByExpectedValueGreaterThan(BigDecimal.ZERO)).thenReturn(0L);
        when(bookmakerRepository.countByActiveTrue()).thenReturn(0L);
        when(modelTuningProfileRepository.countByActiveTrue()).thenReturn(0L);
        when(automationRunRepository.countByRunStatus(AutomationRunStatus.FAILED)).thenReturn(0L);

        var overview = service.getOverview();
        var status = overview.leagues().getFirst();

        assertThat(status.latestRefreshStatus()).isEqualTo("SUCCESS");
        assertThat(status.latestRefreshFailureReason()).isNull();
        assertThat(overview.alerts()).noneMatch(alert -> alert.contains("ux_data_refresh_logs_one_success_per_league_date"));
        assertThat(overview.alerts()).noneMatch(alert -> alert.contains("Latest refresh failed for PREMIER_LEAGUE"));
        assertThat(overview.alerts()).noneMatch(alert -> alert.contains("REFRESH run failed for PREMIER_LEAGUE"));
    }

    private League league(LeagueCode code) {
        return new League()
                .setCode(code)
                .setName(code.getDisplayName())
                .setCountry(code.getCountry())
                .setTier(code.getTier())
                .setCurrentSeason("2026")
                .setActive(true)
                .setScrapeEnabled(true);
    }

    private SourceTarget disabledSgoddsSource(League league) {
        return new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.ODDS_REFERENCE)
                .setName("Sgodds Opening Odds K League CSV")
                .setUrlTemplate("https://sgodds.example.invalid/k-league.csv")
                .setActive(false)
                .setSystemDisabled(true)
                .setConsecutiveFailures(2)
                .setLastFailureAt(OffsetDateTime.parse("2026-06-18T02:19:00Z"))
                .setLastFailureReason("Seeded Sgodds K League CSV URL returned HTTP 404.");
    }

    private MatchRepository.LeagueSeasonMatchSummary seasonSummary(
            String seasonLabel,
            long matches,
            long finished,
            long scheduled,
            LocalDate firstMatchDate,
            LocalDate lastMatchDate
    ) {
        return new MatchRepository.LeagueSeasonMatchSummary() {
            @Override
            public String getSeasonLabel() {
                return seasonLabel;
            }

            @Override
            public long getMatchCount() {
                return matches;
            }

            @Override
            public long getFinishedCount() {
                return finished;
            }

            @Override
            public long getScheduledCount() {
                return scheduled;
            }

            @Override
            public LocalDate getFirstMatchDate() {
                return firstMatchDate;
            }

            @Override
            public LocalDate getLastMatchDate() {
                return lastMatchDate;
            }
        };
    }
}
