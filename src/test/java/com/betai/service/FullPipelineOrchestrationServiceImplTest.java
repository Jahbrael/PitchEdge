package com.betai.service;

import com.betai.api.dto.DailyFeatureGenerationResponse;
import com.betai.api.dto.FullPipelineRequest;
import com.betai.api.dto.ModelQualityGenerationResponse;
import com.betai.api.dto.PipelineStepResponse;
import com.betai.api.dto.PredictionGenerationRequest;
import com.betai.config.PredictionProperties;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.pipeline.PipelineRun;
import com.betai.domain.pipeline.PipelineStatus;
import com.betai.integration.thesportsdb.dto.TheSportsDbPipelineRefreshSummary;
import com.betai.integration.thesportsdb.service.TheSportsDbPipelineRefreshService;
import com.betai.repository.LeagueRepository;
import com.betai.repository.PipelineRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FullPipelineOrchestrationServiceImplTest {

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
    private LeagueRepository leagueRepository;
    @Mock
    private PipelineRunRepository pipelineRunRepository;

    private FullPipelineOrchestrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FullPipelineOrchestrationServiceImpl(
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
                new PredictionProperties("test-model", 20, 20, 14, List.of(MatchStatus.SCHEDULED), 3, 10, com.betai.domain.feature.InsufficientSeasonPolicy.USE_MAX_AVAILABLE, 10),
                leagueRepository,
                pipelineRunRepository,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-06-18T10:00:00Z"), ZoneOffset.UTC)
        );
        when(leagueRepository.findByCodeInAndActiveTrue(Set.of(LeagueCode.PREMIER_LEAGUE)))
                .thenReturn(List.of(league()));
        when(theSportsDbPipelineRefreshService.refresh(Set.of(LeagueCode.PREMIER_LEAGUE), null))
                .thenReturn(new TheSportsDbPipelineRefreshSummary(
                        1, 1, 0, 1, 0, 0,
                        1, 1, 0, 0,
                        1, 0, 0,
                        1, 0, 0,
                        3,
                        List.of(),
                        List.of()
                ));
        when(pipelineRunRepository.save(any(PipelineRun.class))).thenAnswer(invocation -> {
            PipelineRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            return run;
        });
    }

    @Test
    void skipsQualityBacktestAndForcedScheduledPredictionsWhenHistoricalPredictionsFail() {
        when(featureEngineeringService.generateFeatures(any()))
                .thenReturn(new DailyFeatureGenerationResponse(UUID.randomUUID(), OffsetDateTime.now(), List.of()));
        when(predictionGenerationService.generatePredictions(any(PredictionGenerationRequest.class)))
                .thenThrow(new IllegalStateException("Unable to rollback against JDBC Connection"));

        var response = service.runPipeline(request(true, true, true, true, true));

        Map<String, PipelineStepResponse> steps = stepsByName(response.steps());
        assertThat(response.status()).isEqualTo(PipelineStatus.PARTIAL_SUCCESS.name());
        assertThat(steps.get("HISTORICAL_PREDICTIONS").status()).isEqualTo("FAILED");
        assertThat(steps.get("MODEL_QUALITY").status()).isEqualTo("SKIPPED");
        assertThat(steps.get("BACKTEST_TUNING").status()).isEqualTo("SKIPPED");
        assertThat(steps.get("PREDICTIONS").status()).isEqualTo("SKIPPED");
        assertThat(steps.get("PREDICTIONS").summary()).contains("preserving existing rated selections");
        verify(predictionGenerationService, times(1)).generatePredictions(any(PredictionGenerationRequest.class));
        verify(modelQualityService, never()).generateQualitySnapshots(any());
        verify(backtestService, never()).runBacktest(any());
    }

    @Test
    void skipsForcedScheduledPredictionsWhenModelQualityProducesNoSnapshots() {
        when(modelQualityService.generateQualitySnapshots(any()))
                .thenReturn(new ModelQualityGenerationResponse(
                        UUID.randomUUID(),
                        OffsetDateTime.now(),
                        List.of(),
                        List.of("No settled predictions exist.")
                ));

        var response = service.runPipeline(request(false, true, false, true, true));

        Map<String, PipelineStepResponse> steps = stepsByName(response.steps());
        assertThat(response.status()).isEqualTo(PipelineStatus.PARTIAL_SUCCESS.name());
        assertThat(steps.get("MODEL_QUALITY").status()).isEqualTo("SUCCESS");
        assertThat(steps.get("MODEL_QUALITY").summary()).contains("qualitySnapshots=0");
        assertThat(steps.get("PREDICTIONS").status()).isEqualTo("SKIPPED");
        verify(predictionGenerationService, never()).generatePredictions(any(PredictionGenerationRequest.class));
        verify(backtestService, never()).runBacktest(any());
    }

    @Test
    void doesNotCallScrapingRefreshExtractionOrFixtureDiscoveryEvenWhenLegacyFlagsAreRequested() {
        var response = service.runPipeline(legacyScrapingRequest());

        assertThat(response.steps()).extracting(PipelineStepResponse::step)
                .containsExactly("THESPORTSDB_REFRESH");
        verify(fixtureDiscoveryService, never()).discoverFixtures(any());
        verify(dailyRefreshService, never()).triggerDailyRefresh(any());
        verify(extractionService, never()).extractDailySnapshots(any());
    }

    private FullPipelineRequest request(
            boolean runHistoricalPredictions,
            boolean runModelQuality,
            boolean runBacktest,
            boolean runPredictions,
            boolean forceRegeneratePredictions
    ) {
        return new FullPipelineRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE),
                LocalDate.parse("2026-06-18"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "test-model",
                false,
                false,
                false,
                null,
                false,
                false,
                false,
                false,
                runHistoricalPredictions,
                runPredictions,
                false,
                runModelQuality,
                runBacktest,
                30,
                false,
                false,
                false,
                forceRegeneratePredictions,
                false,
                null,
                null,
                null
        );
    }

    private FullPipelineRequest legacyScrapingRequest() {
        return new FullPipelineRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE),
                LocalDate.parse("2026-06-18"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "test-model",
                true,
                true,
                true,
                null,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                30,
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

    private Map<String, PipelineStepResponse> stepsByName(List<PipelineStepResponse> steps) {
        return steps.stream().collect(Collectors.toMap(PipelineStepResponse::step, Function.identity()));
    }

    private League league() {
        return new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1);
    }
}
