package com.betai.automation;

import com.betai.api.dto.ModelQualityGenerationRequest;
import com.betai.api.dto.ModelQualityGenerationResponse;
import com.betai.api.dto.PredictionGenerationRequest;
import com.betai.api.dto.PredictionGenerationResponse;
import com.betai.api.dto.PredictionGenerationRunResponse;
import com.betai.config.AutomationProperties;
import com.betai.config.PredictionProperties;
import com.betai.domain.automation.AutomationRun;
import com.betai.domain.automation.AutomationRunStatus;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.prediction.PredictionGenerationStatus;
import com.betai.integration.thesportsdb.dto.TheSportsDbPipelineRefreshSummary;
import com.betai.integration.thesportsdb.service.TheSportsDbPipelineRefreshService;
import com.betai.repository.AutomationRunRepository;
import com.betai.repository.LeagueRepository;
import com.betai.service.BacktestService;
import com.betai.service.DailyRefreshService;
import com.betai.service.ExtractionService;
import com.betai.service.FeatureEngineeringService;
import com.betai.service.FixtureDiscoveryService;
import com.betai.service.ModelQualityService;
import com.betai.service.PreMatchOddsRefreshService;
import com.betai.service.PredictionGenerationService;
import com.betai.service.SettlementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyPipelineSchedulerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-16T08:00:00Z"), ZoneOffset.UTC);
    private static final String MODEL_VERSION = "test-model";

    @Mock
    private DailyRefreshService dailyRefreshService;
    @Mock
    private ExtractionService extractionService;
    @Mock
    private FixtureDiscoveryService fixtureDiscoveryService;
    @Mock
    private PreMatchOddsRefreshService preMatchOddsRefreshService;
    @Mock
    private FeatureEngineeringService featureEngineeringService;
    @Mock
    private PredictionGenerationService predictionGenerationService;
    @Mock
    private SettlementService settlementService;
    @Mock
    private ModelQualityService modelQualityService;
    @Mock
    private BacktestService backtestService;
    @Mock
    private TheSportsDbPipelineRefreshService theSportsDbPipelineRefreshService;
    @Mock
    private AutomationRunRepository automationRunRepository;
    @Mock
    private LeagueRepository leagueRepository;
    private AtomicReference<AutomationRun> storedRun;

    @BeforeEach
    void setUp() {
        storedRun = new AtomicReference<>();
        when(automationRunRepository.save(any(AutomationRun.class))).thenAnswer(invocation -> {
            AutomationRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            storedRun.set(run);
            return run;
        });
        when(automationRunRepository.findById(any(UUID.class))).thenAnswer(invocation -> Optional.ofNullable(storedRun.get()));
        when(modelQualityService.generateQualitySnapshots(any(ModelQualityGenerationRequest.class)))
                .thenReturn(new ModelQualityGenerationResponse(
                        UUID.randomUUID(),
                        OffsetDateTime.now(CLOCK),
                        List.of(),
                        List.of()
                ));
        lenient().when(theSportsDbPipelineRefreshService.refresh(Set.of(LeagueCode.PREMIER_LEAGUE), null))
                .thenReturn(new TheSportsDbPipelineRefreshSummary(
                        1, 1, 0, 1, 0, 0,
                        1, 1, 0, 0,
                        1, 0, 0,
                        1, 0, 0,
                        3,
                        List.of(),
                        List.of()
                ));
    }

    @Test
    void passesConfiguredModelQualityMinimumSampleSize() {
        var scheduler = scheduler(42);

        scheduler.runDailyPipeline();

        ArgumentCaptor<ModelQualityGenerationRequest> captor = ArgumentCaptor.forClass(ModelQualityGenerationRequest.class);
        verify(modelQualityService, timeout(2000)).generateQualitySnapshots(captor.capture());
        assertThat(captor.getValue().minimumSampleSize()).isEqualTo(42);
        assertThat(captor.getValue().qualityDate()).isEqualTo(java.time.LocalDate.of(2026, 6, 16));
    }

    @Test
    void clampsModelQualityMinimumSampleSizeToValidationMinimum() {
        var scheduler = scheduler(0);

        scheduler.runDailyPipeline();

        ArgumentCaptor<ModelQualityGenerationRequest> captor = ArgumentCaptor.forClass(ModelQualityGenerationRequest.class);
        verify(modelQualityService, timeout(2000)).generateQualitySnapshots(captor.capture());
        assertThat(captor.getValue().minimumSampleSize()).isEqualTo(1);
    }

    @Test
    void usesEveryActiveImportEnabledLeagueWhenNoAutomationScopeIsConfigured() {
        Set<LeagueCode> activeLeagueCodes = Set.of(LeagueCode.MLS, LeagueCode.ARGENTINE_PRIMERA_DIVISION);
        when(leagueRepository.findByActiveTrueAndScrapeEnabledTrueOrderByNameAsc()).thenReturn(List.of(
                new League().setCode(LeagueCode.MLS),
                new League().setCode(LeagueCode.ARGENTINE_PRIMERA_DIVISION)
        ));
        when(theSportsDbPipelineRefreshService.refresh(activeLeagueCodes, null))
                .thenReturn(new TheSportsDbPipelineRefreshSummary(
                        2, 2, 0, 2, 0, 0,
                        2, 2, 0, 0,
                        2, 0, 0,
                        2, 0, 0,
                        6,
                        List.of(),
                        List.of()
                ));
        var scheduler = scheduler(42, false, 7_200_000L, Set.of());

        scheduler.runDailyPipeline();

        verify(theSportsDbPipelineRefreshService, timeout(2_000)).refresh(activeLeagueCodes, null);
        assertThat(storedRun.get().getLeagueCodes())
                .contains(LeagueCode.MLS.name())
                .contains(LeagueCode.ARGENTINE_PRIMERA_DIVISION.name());
    }

    @Test
    void generatesUpcomingPredictionsBeforeModelQualityAndBacktest() {
        when(predictionGenerationService.generatePredictions(any(PredictionGenerationRequest.class)))
                .thenReturn(predictionGenerationResponse());
        var scheduler = scheduler(42, true);

        scheduler.runDailyPipeline();

        ArgumentCaptor<PredictionGenerationRequest> predictionCaptor = ArgumentCaptor.forClass(PredictionGenerationRequest.class);
        verify(predictionGenerationService, timeout(2000).atLeastOnce()).generatePredictions(predictionCaptor.capture());
        PredictionGenerationRequest scheduledRequest = predictionCaptor.getAllValues().stream()
                .filter(request -> request.matchStatuses() != null && request.matchStatuses().contains(MatchStatus.SCHEDULED))
                .findFirst()
                .orElseThrow();

        assertThat(scheduledRequest.fixtureDateFrom()).isEqualTo(java.time.LocalDate.of(2026, 6, 16));
        assertThat(scheduledRequest.fixtureDateTo()).isEqualTo(java.time.LocalDate.of(2026, 6, 29));
        verify(modelQualityService, timeout(2000)).generateQualitySnapshots(any(ModelQualityGenerationRequest.class));
        var order = inOrder(predictionGenerationService, modelQualityService);
        order.verify(predictionGenerationService).generatePredictions(any(PredictionGenerationRequest.class));
        order.verify(modelQualityService).generateQualitySnapshots(any(ModelQualityGenerationRequest.class));
    }

    @Test
    void doesNotReportFullyCompletedWhenEveryPredictionRunIsSkipped() throws Exception {
        when(predictionGenerationService.generatePredictions(any(PredictionGenerationRequest.class)))
                .thenReturn(predictionGenerationResponse(PredictionGenerationStatus.SKIPPED, 0, 0, 0));
        var scheduler = scheduler(42, true);

        scheduler.runDailyPipeline();

        for (int index = 0; index < 40
                && (storedRun.get() == null || storedRun.get().getRunStatus() == AutomationRunStatus.RUNNING);
             index++) {
            Thread.sleep(50L);
        }
        assertThat(storedRun.get().getRunStatus()).isEqualTo(AutomationRunStatus.PARTIAL_SUCCESS);
        assertThat(storedRun.get().getFailureReason())
                .contains("PREDICTIONS")
                .contains("all prediction runs were skipped")
                .contains("matchesEvaluated=0");
    }

    @Test
    void timesOutHungStepAndContinuesToUpcomingPredictions() throws Exception {
        when(theSportsDbPipelineRefreshService.refresh(Set.of(LeagueCode.PREMIER_LEAGUE), null))
                .thenAnswer(invocation -> {
                    Thread.sleep(5_000L);
                    return null;
                });
        when(predictionGenerationService.generatePredictions(any(PredictionGenerationRequest.class)))
                .thenReturn(predictionGenerationResponse());
        var scheduler = scheduler(42, true, 1L);

        scheduler.runDailyPipeline();

        verify(predictionGenerationService, timeout(4_000).atLeastOnce())
                .generatePredictions(any(PredictionGenerationRequest.class));
        verify(modelQualityService, timeout(4_000))
                .generateQualitySnapshots(any(ModelQualityGenerationRequest.class));
        for (int i = 0; i < 40 && (storedRun.get() == null || storedRun.get().getFailureReason() == null); i++) {
            Thread.sleep(100L);
        }
        assertThat(storedRun.get().getFailureReason()).contains("timed out");
    }

    @Test
    void persistsRealCurrentStepThenCompletesAllPipelineSteps() throws Exception {
        CountDownLatch stepStarted = new CountDownLatch(1);
        CountDownLatch releaseStep = new CountDownLatch(1);
        when(theSportsDbPipelineRefreshService.refresh(Set.of(LeagueCode.PREMIER_LEAGUE), null))
                .thenAnswer(invocation -> {
                    stepStarted.countDown();
                    releaseStep.await(2, TimeUnit.SECONDS);
                    return new TheSportsDbPipelineRefreshSummary(
                            1, 1, 0, 1, 0, 0,
                            1, 1, 0, 0,
                            1, 0, 0,
                            1, 0, 0,
                            3,
                            List.of(),
                            List.of()
                    );
                });
        var scheduler = scheduler(42);

        assertThat(scheduler.triggerPipeline(com.betai.domain.automation.AutomationTriggerType.MANUAL_ADMIN_TRIGGER)).isTrue();
        assertThat(stepStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(storedRun.get().getRunStatus()).isEqualTo(AutomationRunStatus.RUNNING);
        assertThat(storedRun.get().getCurrentStep()).isEqualTo("THESPORTSDB_REFRESH");
        assertThat(storedRun.get().getCompletedSteps()).isZero();
        assertThat(storedRun.get().getTotalSteps()).isEqualTo(8);

        releaseStep.countDown();
        for (int index = 0; index < 40 && storedRun.get().getRunStatus() == AutomationRunStatus.RUNNING; index++) {
            Thread.sleep(50L);
        }
        assertThat(storedRun.get().getRunStatus()).isEqualTo(AutomationRunStatus.SUCCESS);
        assertThat(storedRun.get().getCompletedSteps()).isEqualTo(8);
        assertThat(storedRun.get().getCurrentStep()).isEqualTo("BACKTEST_TUNING");
    }

    private DailyPipelineScheduler scheduler(int modelQualityMinimumSampleSize) {
        return scheduler(modelQualityMinimumSampleSize, false, 7_200_000L);
    }

    private DailyPipelineScheduler scheduler(int modelQualityMinimumSampleSize, boolean runPredictions) {
        return scheduler(modelQualityMinimumSampleSize, runPredictions, 7_200_000L);
    }

    private DailyPipelineScheduler scheduler(int modelQualityMinimumSampleSize, boolean runPredictions, long stepTimeoutMs) {
        return scheduler(modelQualityMinimumSampleSize, runPredictions, stepTimeoutMs, Set.of(LeagueCode.PREMIER_LEAGUE));
    }

    private DailyPipelineScheduler scheduler(
            int modelQualityMinimumSampleSize,
            boolean runPredictions,
            long stepTimeoutMs,
            Set<LeagueCode> leagueCodes
    ) {
        return new DailyPipelineScheduler(
                automationProperties(modelQualityMinimumSampleSize, runPredictions, stepTimeoutMs, leagueCodes),
                new PredictionProperties(MODEL_VERSION, 20, 20, 14, List.of(MatchStatus.SCHEDULED), 3, 10, com.betai.domain.feature.InsufficientSeasonPolicy.USE_MAX_AVAILABLE, 10),
                dailyRefreshService,
                extractionService,
                fixtureDiscoveryService,
                preMatchOddsRefreshService,
                featureEngineeringService,
                predictionGenerationService,
                settlementService,
                modelQualityService,
                backtestService,
                theSportsDbPipelineRefreshService,
                automationRunRepository,
                leagueRepository,
                new ObjectMapper(),
                CLOCK
        );
    }

    private AutomationProperties automationProperties(
            int modelQualityMinimumSampleSize,
            boolean runPredictions,
            long stepTimeoutMs,
            Set<LeagueCode> leagueCodes
    ) {
        return new AutomationProperties(
                true,
                "0 0 */5 * * *",
                "UTC",
                14,
                3,
                365,
                365,
                30,
                modelQualityMinimumSampleSize,
                1,
                0,
                stepTimeoutMs,
                leagueCodes,
                Set.of(MatchStatus.SCHEDULED),
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                false,
                false,
                runPredictions,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                null
        );
    }

    private PredictionGenerationResponse predictionGenerationResponse() {
        return predictionGenerationResponse(PredictionGenerationStatus.SUCCESS, 1, 1, 0);
    }

    private PredictionGenerationResponse predictionGenerationResponse(
            PredictionGenerationStatus status,
            int matchesEvaluated,
            int selectionsGenerated,
            int selectionsSkipped
    ) {
        return new PredictionGenerationResponse(
                UUID.randomUUID(),
                OffsetDateTime.now(CLOCK),
                List.of(new PredictionGenerationRunResponse(
                        UUID.randomUUID(),
                        LeagueCode.PREMIER_LEAGUE.name(),
                        MODEL_VERSION,
                        "2026",
                        java.time.LocalDate.of(2026, 6, 16),
                        java.time.LocalDate.of(2026, 6, 16),
                        java.time.LocalDate.of(2026, 6, 29),
                        "SCHEDULED",
                        status,
                        OffsetDateTime.now(CLOCK),
                        OffsetDateTime.now(CLOCK),
                        0L,
                        matchesEvaluated,
                        selectionsGenerated,
                        selectionsSkipped,
                        null,
                        false
                ))
        );
    }
}
