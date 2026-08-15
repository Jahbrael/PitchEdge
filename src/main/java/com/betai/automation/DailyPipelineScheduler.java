package com.betai.automation;

import com.betai.api.dto.DailyExtractionRequest;
import com.betai.api.dto.DailyFeatureGenerationRequest;
import com.betai.api.dto.DailyRefreshRequest;
import com.betai.api.dto.BacktestRequest;
import com.betai.api.dto.FixtureDiscoveryRequest;
import com.betai.api.dto.ModelQualityGenerationRequest;
import com.betai.api.dto.PreMatchOddsRefreshRequest;
import com.betai.api.dto.PredictionGenerationRequest;
import com.betai.api.dto.SettlementRequest;
import com.betai.config.AutomationProperties;
import com.betai.config.PredictionProperties;
import com.betai.domain.automation.AutomationRun;
import com.betai.domain.automation.AutomationRunStatus;
import com.betai.domain.automation.AutomationTriggerType;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.prediction.PredictionGenerationStatus;
import com.betai.repository.AutomationRunRepository;
import com.betai.repository.LeagueRepository;
import com.betai.service.BacktestService;
import com.betai.service.DailyRefreshService;
import com.betai.service.ExtractionService;
import com.betai.service.FeatureEngineeringService;
import com.betai.service.FixtureDiscoveryService;
import com.betai.service.PredictionGenerationService;
import com.betai.service.SettlementService;
import com.betai.service.ModelQualityService;
import com.betai.service.PreMatchOddsRefreshService;
import com.betai.integration.thesportsdb.service.TheSportsDbPipelineRefreshService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "bet-ai.automation", name = "enabled", havingValue = "true")
public class DailyPipelineScheduler {

    private static final int MIN_WINDOW_DAYS = 1;
    private static final int MAX_WINDOW_DAYS = 30;
    private static final int MIN_SETTLEMENT_LOOKBACK_DAYS = 1;
    private static final int MAX_SETTLEMENT_LOOKBACK_DAYS = 30;
    private static final int MIN_HISTORICAL_PREDICTION_LOOKBACK_DAYS = 1;
    private static final int MAX_HISTORICAL_PREDICTION_LOOKBACK_DAYS = 730;
    private static final int MIN_BACKTEST_LOOKBACK_DAYS = 30;
    private static final int MAX_BACKTEST_LOOKBACK_DAYS = 730;
    private static final int MIN_BACKTEST_SAMPLE_SIZE = 0;
    private static final int MAX_BACKTEST_SAMPLE_SIZE = 500;
    private static final int MIN_MODEL_QUALITY_SAMPLE_SIZE = 1;
    private static final int MAX_MODEL_QUALITY_SAMPLE_SIZE = 500;
    private static final long MIN_STEP_TIMEOUT_MS = 1_000L;
    private static final long MAX_STEP_TIMEOUT_MS = 6L * 60L * 60L * 1000L;
    private static final List<String> PIPELINE_STEPS = List.of(
            "THESPORTSDB_REFRESH",
            "ODDS_EXTRACTION",
            "SETTLEMENT",
            "FEATURES",
            "PREDICTIONS",
            "HISTORICAL_PREDICTIONS",
            "MODEL_QUALITY",
            "BACKTEST_TUNING"
    );

    private final AutomationProperties automationProperties;
    private final PredictionProperties predictionProperties;
    private final DailyRefreshService dailyRefreshService;
    private final ExtractionService extractionService;
    private final FixtureDiscoveryService fixtureDiscoveryService;
    private final PreMatchOddsRefreshService preMatchOddsRefreshService;
    private final FeatureEngineeringService featureEngineeringService;
    private final PredictionGenerationService predictionGenerationService;
    private final SettlementService settlementService;
    private final ModelQualityService modelQualityService;
    private final BacktestService backtestService;
    private final TheSportsDbPipelineRefreshService theSportsDbPipelineRefreshService;
    private final AutomationRunRepository automationRunRepository;
    private final LeagueRepository leagueRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "${bet-ai.automation.daily-cron}", zone = "${bet-ai.automation.zone}")
    public void runDailyPipeline() {
        triggerPipeline(AutomationTriggerType.SCHEDULED);
    }

    public boolean triggerPipeline(AutomationTriggerType triggerType) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Skipping automated daily pipeline because a previous run is still active.");
            return false;
        }

        try {
            LocalDate today = LocalDate.now(clock);
            Set<LeagueCode> leagueCodes = resolveLeagueCodes();
            String modelVersion = resolveModelVersion();
            AutomationRun automationRun = automationRunRepository.save(new AutomationRun()
                    .setAutomationDate(today)
                    .setTriggerType(triggerType)
                    .setLeagueCodes(leagueCodesCsv(leagueCodes))
                    .setModelVersion(modelVersion)
                    .setRunStatus(AutomationRunStatus.RUNNING)
                    .setStartedAt(OffsetDateTime.now(clock))
                    .setAttemptCount(0)
                    .setWarningCount(0)
                    .setStepSummaryJson("[]")
                    .setCurrentStep(PIPELINE_STEPS.getFirst())
                    .setCompletedSteps(0)
                    .setTotalSteps(PIPELINE_STEPS.size()));
            java.util.concurrent.CompletableFuture.runAsync(
                    () -> executePipeline(automationRun.getId(), today, leagueCodes, modelVersion)
            );
        } catch (RuntimeException exception) {
            running.set(false);
            throw exception;
        }
        return true;
    }

    private void executePipeline(UUID automationRunId, LocalDate today, Set<LeagueCode> leagueCodes, String modelVersion) {
        List<AutomationStepResult> stepResults = new ArrayList<>();

        try {
            log.info("Starting automated daily pipeline for {} on {}.", leagueCodes, today);
            runAndRecordStep(automationRunId, stepResults, "THESPORTSDB_REFRESH", () -> runTheSportsDbRefresh(leagueCodes));
            runAndRecordStep(automationRunId, stepResults, "ODDS_EXTRACTION", () -> runOddsExtraction(today, leagueCodes));
            runAndRecordStep(automationRunId, stepResults, "SETTLEMENT", () -> runSettlement(today, leagueCodes, modelVersion));
            runAndRecordStep(automationRunId, stepResults, "FEATURES", () -> runFeatures(today, leagueCodes));
            runAndRecordStep(automationRunId, stepResults, "PREDICTIONS", () -> runPredictions(today, leagueCodes, modelVersion));
            runAndRecordStep(automationRunId, stepResults, "HISTORICAL_PREDICTIONS", () -> runHistoricalPredictions(today, leagueCodes, modelVersion));
            runAndRecordStep(automationRunId, stepResults, "MODEL_QUALITY", () -> runModelQuality(today, leagueCodes, modelVersion));
            runAndRecordStep(automationRunId, stepResults, "BACKTEST_TUNING", () -> runBacktest(today, leagueCodes, modelVersion));
            finishAutomationRun(automationRunId, stepResults, null);
            log.info("Automated daily pipeline completed for {} on {}.", leagueCodes, today);
        } catch (Exception exception) {
            finishAutomationRun(automationRunId, stepResults, truncate(exception.getMessage(), 1000));
            log.error("Automated daily pipeline failed for {} on {}: {}", leagueCodes, today, exception.getMessage(), exception);
        } finally {
            running.set(false);
        }
    }

    private void runAndRecordStep(
            UUID automationRunId,
            List<AutomationStepResult> stepResults,
            String stepName,
            Supplier<StepOutcome> operation
    ) {
        persistAutomationProgress(automationRunId, stepResults, stepName);
        stepResults.add(executeStep(stepName, operation));
        persistAutomationProgress(automationRunId, stepResults, stepName);
    }

    private void persistAutomationProgress(UUID automationRunId, List<AutomationStepResult> stepResults, String currentStep) {
        AutomationRun automationRun = automationRunRepository.findById(automationRunId)
                .orElseThrow(() -> new IllegalStateException("Automation run not found: " + automationRunId + "."));
        automationRun.setAttemptCount(stepResults.stream().mapToInt(AutomationStepResult::attempts).sum());
        automationRun.setWarningCount(stepResults.stream().mapToInt(AutomationStepResult::warningCount).sum());
        automationRun.setStepSummaryJson(stepSummaryJson(stepResults));
        automationRun.setCurrentStep(currentStep);
        automationRun.setCompletedSteps(stepResults.size());
        automationRun.setTotalSteps(PIPELINE_STEPS.size());
        automationRunRepository.save(automationRun);
    }

    private StepOutcome runTheSportsDbRefresh(Set<LeagueCode> leagueCodes) {
        var summary = theSportsDbPipelineRefreshService.refresh(leagueCodes, automationProperties.requestedSeasonCount());
        if (summary.requestedLeagues() > 0 && summary.resolvedLeagues() == 0) {
            return new StepOutcome("FAILED", summary.summary(), Math.max(1, summary.skippedLeagues()));
        }
        if (summary.failedLeagues() > 0 && summary.refreshedLeagues() == 0) {
            return new StepOutcome("FAILED", summary.summary(), Math.max(1, summary.failedLeagues()));
        }
        if (summary.failedLeagues() > 0 || summary.skippedLeagues() > 0) {
            return new StepOutcome("PARTIAL_SUCCESS", summary.summary(), summary.failedLeagues() + summary.skippedLeagues());
        }
        return StepOutcome.fromCounts(
                summary.refreshedLeagues(),
                summary.failedLeagues(),
                summary.summary(),
                summary.failedLeagues()
        );
    }

    private StepOutcome runRefresh(LocalDate today, Set<LeagueCode> leagueCodes) {
        if (!automationProperties.runRefresh()) {
            log.info("Automated refresh step disabled.");
            return StepOutcome.skipped("Refresh step disabled.");
        }
        var response = dailyRefreshService.triggerDailyRefresh(new DailyRefreshRequest(
                leagueCodes,
                today,
                automationProperties.forceRefresh()
        ));
        long failed = response.refreshLogs().stream().filter(log -> "FAILED".equals(log.status())).count();
        long skipped = response.refreshLogs().stream().filter(log -> "SKIPPED".equals(log.status())).count();
        log.info("Automated refresh recorded {} league runs.", response.refreshLogs().size());
        return StepOutcome.fromCounts(
                response.refreshLogs().size(),
                failed,
                "refreshLogs=" + response.refreshLogs().size() + ", failed=" + failed + ", skipped=" + skipped,
                (int) failed
        );
    }

    private StepOutcome runExtraction(LocalDate today, Set<LeagueCode> leagueCodes) {
        if (!automationProperties.runExtraction()) {
            log.info("Automated extraction step disabled.");
            return StepOutcome.skipped("Extraction step disabled.");
        }
        var response = extractionService.extractDailySnapshots(new DailyExtractionRequest(
                leagueCodes,
                today,
                automationProperties.forceReprocess()
        ));
        long failed = response.extractionRuns().stream().filter(run -> "FAILED".equals(run.status())).count();
        long skipped = response.extractionRuns().stream().filter(run -> "SKIPPED".equals(run.status())).count();
        log.info("Automated extraction recorded {} runs.", response.extractionRuns().size());
        return StepOutcome.fromCounts(
                response.extractionRuns().size(),
                failed,
                "extractionRuns=" + response.extractionRuns().size() + ", failed=" + failed + ", skipped=" + skipped,
                (int) failed
        );
    }

    private StepOutcome runOddsExtraction(LocalDate today, Set<LeagueCode> leagueCodes) {
        if (!automationProperties.runOddsExtraction()) {
            log.info("Automated odds extraction step disabled.");
            return StepOutcome.skipped("Odds extraction step disabled.");
        }
        var response = preMatchOddsRefreshService.refreshPreMatchOdds(new PreMatchOddsRefreshRequest(
                leagueCodes,
                today,
                automationProperties.forceRefresh(),
                automationProperties.forceReprocess(),
                true
        ));
        long failed = response.extraction().oddsExtractionRuns().stream().filter(run -> "FAILED".equals(run.status())).count();
        log.info(
                "Automated pre-match odds refresh considered {} sources, recorded {} extraction runs, and produced {} warnings.",
                response.sourcesConsidered(),
                response.extraction().oddsExtractionRuns().size(),
                response.warnings().size()
        );
        return StepOutcome.fromCounts(
                response.sourcesConsidered() + response.extraction().oddsExtractionRuns().size(),
                failed,
                "sourcesConsidered=" + response.sourcesConsidered()
                        + ", successfulSnapshots=" + response.successfulSnapshots()
                        + ", cacheReusedSnapshots=" + response.cacheReusedSnapshots()
                        + ", oddsExtractionRuns=" + response.extraction().oddsExtractionRuns().size()
                        + ", failed=" + failed
                        + ", warnings=" + response.warnings().size(),
                response.warnings().size() + (int) failed
        );
    }

    private StepOutcome runFixtureDiscovery(LocalDate today, Set<LeagueCode> leagueCodes, String modelVersion) {
        if (!automationProperties.runFixtureDiscovery()) {
            log.info("Automated fixture discovery step disabled.");
            return StepOutcome.skipped("Fixture discovery step disabled.");
        }
        int windowDays = clamp(
                automationProperties.predictionWindowDays(),
                MIN_WINDOW_DAYS,
                MAX_WINDOW_DAYS,
                "fixture discovery window days"
        );
        LocalDate fixtureDateTo = today.plusDays(windowDays - 1L);
        var response = fixtureDiscoveryService.discoverFixtures(new FixtureDiscoveryRequest(
                leagueCodes,
                normalizeBlank(automationProperties.fixtureDiscoveryTargetSeasonLabel()),
                today,
                today,
                fixtureDateTo,
                automationProperties.forceRefresh(),
                automationProperties.forceReprocess(),
                automationProperties.fixtureDiscoveryAutoRegisterFootballDataSources(),
                automationProperties.fixtureDiscoveryGeneratePendingSlate(),
                modelVersion,
                automationProperties.forceRegeneratePredictions()
        ));
        log.info(
                "Automated fixture discovery found {} scheduled fixtures across {} refresh logs and {} extraction runs.",
                response.discoveredFixtures().size(),
                response.refresh().refreshLogs().size(),
                response.extraction().extractionRuns().size()
        );
        if (!response.warnings().isEmpty()) {
            log.warn("Automated fixture discovery warnings: {}", response.warnings());
        }
        int failedRefreshes = (int) response.refresh().refreshLogs().stream()
                .filter(log -> "FAILED".equals(log.status()))
                .count();
        int failedExtractions = (int) response.extraction().extractionRuns().stream()
                .filter(run -> "FAILED".equals(run.status()))
                .count();
        return StepOutcome.fromCounts(
                response.refresh().refreshLogs().size() + response.extraction().extractionRuns().size(),
                failedRefreshes + failedExtractions,
                "discoveredFixtures=" + response.discoveredFixtures().size()
                        + ", refreshLogs=" + response.refresh().refreshLogs().size()
                        + ", extractionRuns=" + response.extraction().extractionRuns().size()
                        + ", warnings=" + response.warnings().size(),
                response.warnings().size() + failedRefreshes + failedExtractions
        );
    }

    private StepOutcome runFeatures(LocalDate today, Set<LeagueCode> leagueCodes) {
        if (!automationProperties.runFeatures()) {
            log.info("Automated feature generation step disabled.");
            return StepOutcome.skipped("Feature generation step disabled.");
        }
        var response = featureEngineeringService.generateFeatures(new DailyFeatureGenerationRequest(
                leagueCodes,
                today,
                automationProperties.forceRegenerateFeatures(),
                automationProperties.requestedSeasonCount(),
                automationProperties.seasonSelectionMode(),
                automationProperties.customSeasonIds()
        ));
        long failed = response.featureGenerationRuns().stream().filter(run -> "FAILED".equals(run.status())).count();
        log.info("Automated feature generation recorded {} runs.", response.featureGenerationRuns().size());
        return StepOutcome.fromCounts(
                response.featureGenerationRuns().size(),
                failed,
                "featureRuns=" + response.featureGenerationRuns().size() + ", failed=" + failed,
                (int) failed
        );
    }

    private StepOutcome runPredictions(LocalDate today, Set<LeagueCode> leagueCodes, String modelVersion) {
        if (!automationProperties.runPredictions()) {
            log.info("Automated prediction generation step disabled.");
            return StepOutcome.skipped("Prediction generation step disabled.");
        }
        int windowDays = clamp(
                automationProperties.predictionWindowDays(),
                MIN_WINDOW_DAYS,
                MAX_WINDOW_DAYS,
                "prediction window days"
        );
        LocalDate fixtureDateTo = today.plusDays(windowDays - 1L);
        var response = predictionGenerationService.generatePredictions(new PredictionGenerationRequest(
                leagueCodes,
                today,
                null,
                today,
                fixtureDateTo,
                resolvePredictionMatchStatuses(),
                modelVersion,
                null,
                automationProperties.forceRegeneratePredictions(),
                automationProperties.requestedSeasonCount(),
                automationProperties.seasonSelectionMode(),
                automationProperties.customSeasonIds()
        ));
        var runs = response.predictionGenerationRuns();
        long failed = runs.stream().filter(run -> run.status() == PredictionGenerationStatus.FAILED).count();
        long skipped = runs.stream().filter(run -> run.status() == PredictionGenerationStatus.SKIPPED).count();
        int matchesEvaluated = runs.stream().mapToInt(run -> run.matchesEvaluated()).sum();
        int selectionsGenerated = runs.stream().mapToInt(run -> run.selectionsGenerated()).sum();
        int selectionsSkipped = runs.stream().mapToInt(run -> run.selectionsSkipped()).sum();
        String summary = "predictionRuns=" + runs.size()
                + ", failed=" + failed
                + ", skipped=" + skipped
                + ", matchesEvaluated=" + matchesEvaluated
                + ", selectionsGenerated=" + selectionsGenerated
                + ", selectionsSkipped=" + selectionsSkipped;
        log.info("Automated prediction generation recorded {} runs.", runs.size());
        if (runs.isEmpty()) {
            return new StepOutcome("FAILED", summary + ", reason=no prediction runs were created", 1);
        }
        if (skipped == runs.size()) {
            return new StepOutcome(
                    "FAILED",
                    summary + ", reason=all prediction runs were skipped; no eligible scheduled fixtures were evaluated",
                    Math.max(1, (int) skipped)
            );
        }
        if (failed > 0 || skipped > 0) {
            return new StepOutcome(
                    "PARTIAL_SUCCESS",
                    summary,
                    Math.max(1, (int) (failed + skipped))
            );
        }
        return StepOutcome.success(summary, selectionsSkipped);
    }

    private StepOutcome runHistoricalPredictions(LocalDate today, Set<LeagueCode> leagueCodes, String modelVersion) {
        if (!automationProperties.runHistoricalPredictions()) {
            log.info("Automated historical prediction generation step disabled.");
            return StepOutcome.skipped("Historical prediction generation step disabled.");
        }
        int lookbackDays = clamp(
                automationProperties.historicalPredictionLookbackDays(),
                MIN_HISTORICAL_PREDICTION_LOOKBACK_DAYS,
                MAX_HISTORICAL_PREDICTION_LOOKBACK_DAYS,
                "historical prediction lookback days"
        );
        LocalDate matchDateTo = today.minusDays(1);
        LocalDate matchDateFrom = matchDateTo.minusDays(lookbackDays - 1L);
        var predictionResponse = predictionGenerationService.generatePredictions(new PredictionGenerationRequest(
                leagueCodes,
                today,
                null,
                matchDateFrom,
                matchDateTo,
                EnumSet.of(MatchStatus.FINISHED),
                modelVersion,
                null,
                automationProperties.forceRegeneratePredictions(),
                automationProperties.requestedSeasonCount(),
                automationProperties.seasonSelectionMode(),
                automationProperties.customSeasonIds()
        ));
        var settlementResponse = settlementService.settlePredictions(new SettlementRequest(
                leagueCodes,
                today,
                matchDateFrom,
                matchDateTo,
                modelVersion,
                true
        ));
        int failedPredictionRuns = (int) predictionResponse.predictionGenerationRuns().stream()
                .filter(run -> run.status().name().equals("FAILED"))
                .count();
        int failedSettlementRuns = (int) settlementResponse.settlementRuns().stream()
                .filter(run -> "FAILED".equals(run.status()))
                .count();
        int generated = predictionResponse.predictionGenerationRuns().stream()
                .mapToInt(run -> run.selectionsGenerated())
                .sum();
        int skipped = predictionResponse.predictionGenerationRuns().stream()
                .mapToInt(run -> run.selectionsSkipped())
                .sum();
        return StepOutcome.fromCounts(
                predictionResponse.predictionGenerationRuns().size() + settlementResponse.settlementRuns().size(),
                failedPredictionRuns + failedSettlementRuns,
                "predictionRuns=" + predictionResponse.predictionGenerationRuns().size()
                        + ", selectionsGenerated=" + generated
                        + ", selectionsSkipped=" + skipped
                        + ", settlementRuns=" + settlementResponse.settlementRuns().size()
                        + ", lookbackDays=" + lookbackDays,
                failedPredictionRuns + failedSettlementRuns
        );
    }

    private StepOutcome runSettlement(LocalDate today, Set<LeagueCode> leagueCodes, String modelVersion) {
        if (!automationProperties.runSettlement()) {
            log.info("Automated settlement step disabled.");
            return StepOutcome.skipped("Settlement step disabled.");
        }
        int lookbackDays = clamp(
                automationProperties.settlementLookbackDays(),
                MIN_SETTLEMENT_LOOKBACK_DAYS,
                MAX_SETTLEMENT_LOOKBACK_DAYS,
                "settlement lookback days"
        );
        LocalDate matchDateTo = today.minusDays(1);
        LocalDate matchDateFrom = matchDateTo.minusDays(lookbackDays - 1L);
        var response = settlementService.settlePredictions(new SettlementRequest(
                leagueCodes,
                today,
                matchDateFrom,
                matchDateTo,
                modelVersion,
                automationProperties.forceResettle()
        ));
        long failed = response.settlementRuns().stream().filter(run -> "FAILED".equals(run.status())).count();
        log.info("Automated settlement recorded {} runs.", response.settlementRuns().size());
        return StepOutcome.fromCounts(
                response.settlementRuns().size(),
                failed,
                "settlementRuns=" + response.settlementRuns().size() + ", failed=" + failed,
                (int) failed
        );
    }

    private StepOutcome runModelQuality(LocalDate today, Set<LeagueCode> leagueCodes, String modelVersion) {
        if (!automationProperties.runModelQuality()) {
            log.info("Automated model-quality step disabled.");
            return StepOutcome.skipped("Model-quality step disabled.");
        }
        int minimumSampleSize = clamp(
                automationProperties.modelQualityMinimumSampleSize(),
                MIN_MODEL_QUALITY_SAMPLE_SIZE,
                MAX_MODEL_QUALITY_SAMPLE_SIZE,
                "model-quality minimum sample size"
        );
        var response = modelQualityService.generateQualitySnapshots(new ModelQualityGenerationRequest(
                leagueCodes,
                modelVersion,
                today,
                minimumSampleSize
        ));
        log.info(
                "Automated model-quality generation produced {} snapshots and {} warnings.",
                response.qualitySnapshots().size(),
                response.warnings().size()
        );
        return StepOutcome.success(
                "qualitySnapshots=" + response.qualitySnapshots().size()
                        + ", warnings=" + response.warnings().size()
                        + ", minimumSampleSize=" + minimumSampleSize,
                response.warnings().size()
        );
    }

    private StepOutcome runBacktest(LocalDate today, Set<LeagueCode> leagueCodes, String modelVersion) {
        if (!automationProperties.runBacktest()) {
            log.info("Automated backtest/tuning step disabled.");
            return StepOutcome.skipped("Backtest/tuning step disabled.");
        }
        int lookbackDays = clamp(
                automationProperties.backtestLookbackDays(),
                MIN_BACKTEST_LOOKBACK_DAYS,
                MAX_BACKTEST_LOOKBACK_DAYS,
                "backtest lookback days"
        );
        int minimumSampleSize = clamp(
                automationProperties.backtestMinimumSampleSize(),
                MIN_BACKTEST_SAMPLE_SIZE,
                MAX_BACKTEST_SAMPLE_SIZE,
                "backtest minimum sample size"
        );
        LocalDate matchDateTo = today.minusDays(1);
        LocalDate matchDateFrom = matchDateTo.minusDays(lookbackDays - 1L);
        var response = backtestService.runBacktest(new BacktestRequest(
                leagueCodes,
                modelVersion,
                today,
                matchDateFrom,
                matchDateTo,
                minimumSampleSize
        ));
        log.info(
                "Automated backtest/tuning finished with status {} across {} selections and {} market summaries.",
                response.status(),
                response.totalSelections(),
                response.marketSummaries().size()
        );
        int failed = "FAILED".equals(response.status()) ? 1 : 0;
        int warnings = "SKIPPED".equals(response.status()) ? 1 : failed;
        return StepOutcome.fromCounts(
                1,
                failed,
                "status=" + response.status()
                        + ", selections=" + response.totalSelections()
                        + ", priced=" + response.totalPriced()
                        + ", marketSummaries=" + response.marketSummaries().size()
                        + ", lookbackDays=" + lookbackDays
                        + ", minimumSampleSize=" + minimumSampleSize,
                warnings
        );
    }

    private AutomationStepResult executeStep(String stepName, Supplier<StepOutcome> operation) {
        int maxAttempts = Math.max(1, automationProperties.maxStepAttempts());
        long backoffMs = Math.max(0L, automationProperties.retryBackoffMs());
        List<String> failures = new ArrayList<>();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                StepOutcome outcome = runStepAttemptWithTimeout(stepName, attempt, operation);
                if ("FAILED".equals(outcome.status()) && attempt < maxAttempts) {
                    failures.add(truncate(outcome.summary(), 500));
                    sleep(backoffMs);
                    continue;
                }
                return new AutomationStepResult(
                        stepName,
                        outcome.status(),
                        attempt,
                        outcome.summary(),
                        outcome.warningCount(),
                        "FAILED".equals(outcome.status()) ? outcome.summary() : null
                );
            } catch (AutomationStepTimeoutException exception) {
                String message = truncate(exception.getMessage(), 500);
                log.warn("Automated step {} attempt {} timed out: {}", stepName, attempt, message);
                return new AutomationStepResult(
                        stepName,
                        "FAILED",
                        attempt,
                        null,
                        1,
                        message
                );
            } catch (Exception exception) {
                failures.add(truncate(exception.getMessage(), 500));
                if (attempt < maxAttempts) {
                    sleep(backoffMs);
                }
            }
        }

        return new AutomationStepResult(
                stepName,
                "FAILED",
                maxAttempts,
                null,
                maxAttempts,
                String.join(" | ", failures)
        );
    }

    private StepOutcome runStepAttemptWithTimeout(String stepName, int attempt, Supplier<StepOutcome> operation) {
        long timeoutMs = stepTimeoutMs();
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "betai-automation-" + sanitizeThreadName(stepName) + "-" + attempt);
            thread.setDaemon(true);
            return thread;
        });
        Future<StepOutcome> future = executor.submit(operation::get);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new AutomationStepTimeoutException(
                    "Step " + stepName + " timed out after " + timeoutMs + "ms."
            );
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Automation step " + stepName + " was interrupted.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Automation step " + stepName + " failed.", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private long stepTimeoutMs() {
        long configured = automationProperties.stepTimeoutMs();
        if (configured < MIN_STEP_TIMEOUT_MS) {
            log.warn(
                    "Configured automation step timeout {}ms is below {}; using {}ms.",
                    configured,
                    MIN_STEP_TIMEOUT_MS,
                    MIN_STEP_TIMEOUT_MS
            );
            return MIN_STEP_TIMEOUT_MS;
        }
        if (configured > MAX_STEP_TIMEOUT_MS) {
            log.warn(
                    "Configured automation step timeout {}ms is above {}; using {}ms.",
                    configured,
                    MAX_STEP_TIMEOUT_MS,
                    MAX_STEP_TIMEOUT_MS
            );
            return MAX_STEP_TIMEOUT_MS;
        }
        return configured;
    }

    private String sanitizeThreadName(String stepName) {
        return stepName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private void finishAutomationRun(UUID automationRunId, List<AutomationStepResult> stepResults, String hardFailure) {
        AutomationRun automationRun = automationRunRepository.findById(automationRunId)
                .orElseThrow(() -> new IllegalStateException("Automation run not found: " + automationRunId + "."));
        int attempts = stepResults.stream().mapToInt(AutomationStepResult::attempts).sum();
        int warnings = stepResults.stream().mapToInt(AutomationStepResult::warningCount).sum();
        String failureReason = StringUtils.hasText(hardFailure)
                ? hardFailure
                : stepResults.stream()
                .filter(step -> "FAILED".equals(step.status()) || "PARTIAL_SUCCESS".equals(step.status()))
                .map(step -> step.step() + ": " + (StringUtils.hasText(step.failureReason())
                        ? step.failureReason()
                        : step.summary()))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("; "));
        automationRun.finish(
                OffsetDateTime.now(clock),
                automationStatus(stepResults, hardFailure),
                attempts,
                warnings,
                stepSummaryJson(stepResults),
                StringUtils.hasText(failureReason) ? truncate(failureReason, 1000) : null
        );
        automationRun.setCompletedSteps(stepResults.size());
        automationRun.setTotalSteps(PIPELINE_STEPS.size());
        if (!stepResults.isEmpty()) {
            automationRun.setCurrentStep(stepResults.getLast().step());
        }
        automationRunRepository.save(automationRun);
    }

    private AutomationRunStatus automationStatus(List<AutomationStepResult> stepResults, String hardFailure) {
        if (StringUtils.hasText(hardFailure) || stepResults.isEmpty()) {
            return AutomationRunStatus.FAILED;
        }
        long failed = stepResults.stream().filter(step -> "FAILED".equals(step.status())).count();
        boolean partial = stepResults.stream().anyMatch(step -> "PARTIAL_SUCCESS".equals(step.status()));
        if (failed == 0) {
            return partial ? AutomationRunStatus.PARTIAL_SUCCESS : AutomationRunStatus.SUCCESS;
        }
        if (failed == stepResults.size()) {
            return AutomationRunStatus.FAILED;
        }
        return AutomationRunStatus.PARTIAL_SUCCESS;
    }

    private void sleep(long backoffMs) {
        if (backoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Automation retry sleep was interrupted.", exception);
        }
    }

    private String stepSummaryJson(List<AutomationStepResult> stepResults) {
        try {
            return objectMapper.writeValueAsString(stepResults);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private Set<LeagueCode> resolveLeagueCodes() {
        if (automationProperties.leagueCodes() == null || automationProperties.leagueCodes().isEmpty()) {
            Set<LeagueCode> activeCodes = leagueRepository.findByActiveTrueAndScrapeEnabledTrueOrderByNameAsc()
                    .stream()
                    .map(league -> league.getCode())
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(LeagueCode.class)));
            return activeCodes.isEmpty() ? EnumSet.allOf(LeagueCode.class) : activeCodes;
        }
        return EnumSet.copyOf(automationProperties.leagueCodes());
    }

    private Set<MatchStatus> resolvePredictionMatchStatuses() {
        if (automationProperties.predictionMatchStatuses() == null || automationProperties.predictionMatchStatuses().isEmpty()) {
            return EnumSet.of(MatchStatus.SCHEDULED);
        }
        EnumSet<MatchStatus> statuses = EnumSet.copyOf(automationProperties.predictionMatchStatuses());
        statuses.remove(MatchStatus.CANCELLED);
        statuses.remove(MatchStatus.ABANDONED);
        return statuses.isEmpty() ? EnumSet.of(MatchStatus.SCHEDULED) : statuses;
    }

    private String resolveModelVersion() {
        String modelVersion = predictionProperties.defaultModelVersion();
        if (!StringUtils.hasText(modelVersion)) {
            throw new IllegalStateException("bet-ai.prediction.default-model-version must be configured for automation.");
        }
        return modelVersion.trim();
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String leagueCodesCsv(Set<LeagueCode> leagueCodes) {
        return leagueCodes.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private int clamp(int value, int min, int max, String label) {
        if (value < min) {
            log.warn("Configured {} {} is below {}; using {}.", label, value, min, min);
            return min;
        }
        if (value > max) {
            log.warn("Configured {} {} is above {}; using {}.", label, value, max, max);
            return max;
        }
        return value;
    }

    private record StepOutcome(String status, String summary, int warningCount) {

        static StepOutcome success(String summary, int warningCount) {
            return new StepOutcome("SUCCESS", summary, warningCount);
        }

        static StepOutcome skipped(String summary) {
            return new StepOutcome("SKIPPED", summary, 0);
        }

        static StepOutcome fromCounts(long total, long failed, String summary, int warningCount) {
            if (total > 0 && failed == total) {
                return new StepOutcome("FAILED", summary, Math.max(warningCount, (int) failed));
            }
            if (failed > 0) {
                return new StepOutcome("PARTIAL_SUCCESS", summary, Math.max(warningCount, (int) failed));
            }
            return success(summary, warningCount);
        }
    }

    private record AutomationStepResult(
            String step,
            String status,
            int attempts,
            String summary,
            int warningCount,
            String failureReason
    ) {
    }

    private static final class AutomationStepTimeoutException extends RuntimeException {
        private AutomationStepTimeoutException(String message) {
            super(message);
        }
    }
}
