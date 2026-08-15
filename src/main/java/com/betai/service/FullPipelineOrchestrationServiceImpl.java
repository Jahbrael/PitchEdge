package com.betai.service;

import com.betai.api.dto.DailyExtractionRequest;
import com.betai.api.dto.DailyFeatureGenerationRequest;
import com.betai.api.dto.DailyRefreshRequest;
import com.betai.api.dto.FixtureDiscoveryRequest;
import com.betai.api.dto.FullPipelineRequest;
import com.betai.api.dto.FullPipelineResponse;
import com.betai.api.dto.ModelQualityGenerationRequest;
import com.betai.api.dto.PipelineStepResponse;
import com.betai.api.dto.PreMatchOddsRefreshRequest;
import com.betai.api.dto.PredictionGenerationRequest;
import com.betai.api.dto.SettlementRequest;
import com.betai.api.dto.BacktestRequest;
import com.betai.config.PredictionProperties;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.pipeline.PipelineRun;
import com.betai.domain.pipeline.PipelineStatus;
import com.betai.domain.prediction.PredictionGenerationStatus;
import com.betai.domain.settlement.SettlementStatus;
import com.betai.exception.InvalidRequestException;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.integration.thesportsdb.service.TheSportsDbPipelineRefreshService;
import com.betai.repository.LeagueRepository;
import com.betai.repository.PipelineRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FullPipelineOrchestrationServiceImpl implements FullPipelineOrchestrationService {

    private static final int DEFAULT_SETTLEMENT_LOOKBACK_DAYS = 3;
    private static final int DEFAULT_MODEL_QUALITY_MINIMUM_SAMPLE_SIZE = 30;
    private static final int DEFAULT_BACKTEST_LOOKBACK_DAYS = 365;

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
    private final PredictionProperties predictionProperties;
    private final LeagueRepository leagueRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public FullPipelineResponse runPipeline(FullPipelineRequest request) {
        OffsetDateTime startedAt = OffsetDateTime.now(clock);
        LocalDate pipelineDate = request.pipelineDate() == null ? LocalDate.now(clock) : request.pipelineDate();
        List<League> leagues = resolveLeagues(request.leagueCodes());
        Set<LeagueCode> leagueCodes = leagues.stream()
                .map(League::getCode)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(LeagueCode.class)));
        String modelVersion = resolveModelVersion(request.modelVersion());
        LocalDate fixtureDateFrom = request.fixtureDateFrom() == null ? pipelineDate : request.fixtureDateFrom();
        LocalDate fixtureDateTo = request.fixtureDateTo() == null ? fixtureDateFrom.plusDays(predictionProperties.maxDateRangeDays() - 1L) : request.fixtureDateTo();
        LocalDate settlementTo = request.settlementMatchDateTo() == null ? pipelineDate.minusDays(1) : request.settlementMatchDateTo();
        LocalDate settlementFrom = request.settlementMatchDateFrom() == null
                ? settlementTo.minusDays(DEFAULT_SETTLEMENT_LOOKBACK_DAYS - 1L)
                : request.settlementMatchDateFrom();
        LocalDate backtestTo = request.backtestMatchDateTo() == null ? pipelineDate.minusDays(1) : request.backtestMatchDateTo();
        LocalDate backtestFrom = request.backtestMatchDateFrom() == null
                ? backtestTo.minusDays(DEFAULT_BACKTEST_LOOKBACK_DAYS - 1L)
                : request.backtestMatchDateFrom();
        int backtestMinimumSampleSize = request.backtestMinimumSampleSize() == null
                ? DEFAULT_MODEL_QUALITY_MINIMUM_SAMPLE_SIZE
                : request.backtestMinimumSampleSize();
        Set<MatchStatus> predictionStatuses = resolvePredictionStatuses(request.predictionMatchStatuses());

        validateDateRange(fixtureDateFrom, fixtureDateTo, "fixture");
        validateDateRange(settlementFrom, settlementTo, "settlement");
        validateDateRange(backtestFrom, backtestTo, "backtest");

        if (!pipelineRunRepository.findByPipelineStatus(PipelineStatus.RUNNING).isEmpty()) {
            throw new IllegalStateException("A pipeline is already running!");
        }

        PipelineRun run = pipelineRunRepository.save(new PipelineRun()
                .setPipelineDate(pipelineDate)
                .setLeagueCodes(leagueCodesCsv(leagueCodes))
                .setModelVersion(modelVersion)
                .setPipelineStatus(PipelineStatus.RUNNING)
                .setStartedAt(startedAt));

        List<PipelineStepResponse> steps = new ArrayList<>();
        steps.add(runTheSportsDbRefreshStep(leagueCodes, request.requestedSeasonCount()));
        if (enabled(request.runOddsExtraction())) {
            steps.add(runStep("ODDS_EXTRACTION", () -> {
                var response = preMatchOddsRefreshService.refreshPreMatchOdds(new PreMatchOddsRefreshRequest(
                        leagueCodes,
                        pipelineDate,
                        truthy(request.forceRefresh()),
                        truthy(request.forceReprocess()),
                        true
                ));
                return "sourcesConsidered=" + response.sourcesConsidered()
                        + ", successfulSnapshots=" + response.successfulSnapshots()
                        + ", cacheReusedSnapshots=" + response.cacheReusedSnapshots()
                        + ", oddsExtractionRuns=" + response.extraction().oddsExtractionRuns().size()
                        + ", warnings=" + response.warnings().size();
            }));
        }
        if (enabled(request.runSettlement())) {
            steps.add(runStep("SETTLEMENT", () -> {
                var response = settlementService.settlePredictions(new SettlementRequest(
                        leagueCodes,
                        pipelineDate,
                        settlementFrom,
                        settlementTo,
                        modelVersion,
                        truthy(request.forceResettle())
                ));
                return "settlementRuns=" + response.settlementRuns().size();
            }));
        }
        if (enabled(request.runFeatures()) || enabled(request.runHistoricalPredictions())) {
            steps.add(runStep("FEATURES", () -> {
                var response = featureEngineeringService.generateFeatures(new DailyFeatureGenerationRequest(
                        leagueCodes,
                        pipelineDate,
                        truthy(request.forceRegenerateFeatures()),
                        request.requestedSeasonCount(),
                        request.seasonSelectionMode(),
                        request.customSeasonIds()
                ));
                return "featureRuns=" + response.featureGenerationRuns().size();
            }));
        }
        PipelineStepResponse historicalPredictionStep = null;
        if (enabled(request.runHistoricalPredictions())) {
            historicalPredictionStep = runHistoricalPredictionsStep(
                    leagueCodes,
                    pipelineDate,
                    backtestFrom,
                    backtestTo,
                    modelVersion,
                    truthy(request.forceRegeneratePredictions()),
                    request.requestedSeasonCount(),
                    request.seasonSelectionMode(),
                    request.customSeasonIds()
            );
            steps.add(historicalPredictionStep);
        }
        boolean historicalPredictionsFailed = stepFailed(historicalPredictionStep);
        PipelineStepResponse modelQualityStep = null;
        if (enabled(request.runModelQuality())) {
            if (historicalPredictionsFailed) {
                modelQualityStep = skippedStep(
                        "MODEL_QUALITY",
                        "Skipped because HISTORICAL_PREDICTIONS failed; model quality requires settled historical predictions."
                );
            } else {
                modelQualityStep = runStep("MODEL_QUALITY", () -> {
                    var response = modelQualityService.generateQualitySnapshots(new ModelQualityGenerationRequest(
                            leagueCodes,
                            modelVersion,
                            pipelineDate,
                            DEFAULT_MODEL_QUALITY_MINIMUM_SAMPLE_SIZE
                    ));
                    return "qualitySnapshots=" + response.qualitySnapshots().size()
                            + ", warnings=" + response.warnings().size();
                });
            }
            steps.add(modelQualityStep);
        }
        boolean modelQualityUnavailable = historicalPredictionsFailed || qualitySnapshotCount(modelQualityStep) == 0;
        if (enabled(request.runBacktest())) {
            if (historicalPredictionsFailed) {
                steps.add(skippedStep(
                        "BACKTEST_TUNING",
                        "Skipped because HISTORICAL_PREDICTIONS failed; backtest/tuning requires settled historical predictions."
                ));
            } else {
                steps.add(runStep("BACKTEST_TUNING", () -> {
                    var response = backtestService.runBacktest(new BacktestRequest(
                            leagueCodes,
                            modelVersion,
                            pipelineDate,
                            backtestFrom,
                            backtestTo,
                            backtestMinimumSampleSize
                    ));
                    return "status=" + response.status()
                            + ", totalSelections=" + response.totalSelections()
                            + ", totalPriced=" + response.totalPriced()
                            + ", marketSummaries=" + response.marketSummaries().size();
                }));
            }
        }
        if (enabled(request.runPredictions())) {
            if (truthy(request.forceRegeneratePredictions()) && modelQualityUnavailable) {
                steps.add(skippedStep(
                        "PREDICTIONS",
                        "Skipped because forceRegeneratePredictions=true but model quality/tuning inputs are unavailable; preserving existing rated selections."
                ));
            } else {
                steps.add(runStep("PREDICTIONS", () -> {
                    var response = predictionGenerationService.generatePredictions(new PredictionGenerationRequest(
                            leagueCodes,
                            pipelineDate,
                            null,
                            fixtureDateFrom,
                            fixtureDateTo,
                            predictionStatuses,
                            modelVersion,
                            null,
                            truthy(request.forceRegeneratePredictions()),
                            request.requestedSeasonCount(),
                            request.seasonSelectionMode(),
                            request.customSeasonIds()
                    ));
                    return "predictionRuns=" + response.predictionGenerationRuns().size();
                }));
            }
        }

        OffsetDateTime finishedAt = OffsetDateTime.now(clock);
        PipelineStatus status = pipelineStatus(steps);
        String failureReason = steps.stream()
                .filter(step -> "FAILED".equals(step.status()))
                .map(step -> step.step() + ": " + step.failureReason())
                .collect(Collectors.joining("; "));
        run.finish(
                finishedAt,
                status,
                stepSummaryJson(steps),
                StringUtils.hasText(failureReason) ? truncate(failureReason, 1000) : null
        );
        PipelineRun saved = pipelineRunRepository.save(run);
        return response(saved, steps, leagueCodes);
    }

    private PipelineStepResponse runStep(String stepName, Supplier<String> operation) {
        OffsetDateTime startedAt = OffsetDateTime.now(clock);
        try {
            String summary = operation.get();
            OffsetDateTime finishedAt = OffsetDateTime.now(clock);
            return new PipelineStepResponse(
                    stepName,
                    "SUCCESS",
                    startedAt,
                    finishedAt,
                    Duration.between(startedAt, finishedAt).toMillis(),
                    summary,
                    null
            );
        } catch (Exception exception) {
            OffsetDateTime finishedAt = OffsetDateTime.now(clock);
            return new PipelineStepResponse(
                    stepName,
                    "FAILED",
                    startedAt,
                    finishedAt,
                    Duration.between(startedAt, finishedAt).toMillis(),
                    null,
                    truncate(exception.getMessage(), 1000)
            );
        }
    }

    private PipelineStepResponse runTheSportsDbRefreshStep(Set<LeagueCode> leagueCodes, Integer requestedSeasonCount) {
        OffsetDateTime startedAt = OffsetDateTime.now(clock);
        try {
            var summary = theSportsDbPipelineRefreshService.refresh(leagueCodes, requestedSeasonCount);
            OffsetDateTime finishedAt = OffsetDateTime.now(clock);
            String status;
            String failureReason = null;
            if (summary.requestedLeagues() > 0 && summary.resolvedLeagues() == 0) {
                status = "FAILED";
                failureReason = "No requested leagues resolved to TheSportsDB league IDs.";
            } else if (summary.failedLeagues() > 0 && summary.refreshedLeagues() == 0) {
                status = "FAILED";
                failureReason = "All resolved TheSportsDB league refreshes failed.";
            } else if (summary.failedLeagues() > 0 || summary.skippedLeagues() > 0) {
                status = "PARTIAL_SUCCESS";
                failureReason = truncate(summary.summary(), 1000);
            } else {
                status = "SUCCESS";
            }
            return new PipelineStepResponse(
                    "THESPORTSDB_REFRESH",
                    status,
                    startedAt,
                    finishedAt,
                    Duration.between(startedAt, finishedAt).toMillis(),
                    summary.summary(),
                    failureReason
            );
        } catch (Exception exception) {
            OffsetDateTime finishedAt = OffsetDateTime.now(clock);
            return new PipelineStepResponse(
                    "THESPORTSDB_REFRESH",
                    "FAILED",
                    startedAt,
                    finishedAt,
                    Duration.between(startedAt, finishedAt).toMillis(),
                    null,
                    truncate(exception.getMessage(), 1000)
            );
        }
    }

    private PipelineStepResponse runHistoricalPredictionsStep(
            Set<LeagueCode> leagueCodes,
            LocalDate pipelineDate,
            LocalDate backtestFrom,
            LocalDate backtestTo,
            String modelVersion,
            boolean forceRegeneratePredictions,
            Integer requestedSeasonCount,
            com.betai.domain.feature.SeasonSelectionMode seasonSelectionMode,
            Set<String> customSeasonIds
    ) {
        OffsetDateTime startedAt = OffsetDateTime.now(clock);
        int predictionRuns = 0;
        int settlementRuns = 0;
        int generated = 0;
        int skipped = 0;
        int settled = 0;
        List<String> failedLeagues = new ArrayList<>();

        for (LeagueCode leagueCode : leagueCodes.stream().sorted(Comparator.comparing(LeagueCode::name)).toList()) {
            try {
                Set<LeagueCode> singleLeague = EnumSet.of(leagueCode);
                var predictionResponse = predictionGenerationService.generatePredictions(new PredictionGenerationRequest(
                        singleLeague,
                        pipelineDate,
                        null,
                        backtestFrom,
                        backtestTo,
                        EnumSet.of(MatchStatus.FINISHED),
                        modelVersion,
                        null,
                        forceRegeneratePredictions,
                        requestedSeasonCount,
                        seasonSelectionMode,
                        customSeasonIds
                ));
                predictionRuns += predictionResponse.predictionGenerationRuns().size();
                generated += predictionResponse.predictionGenerationRuns().stream()
                        .mapToInt(predictionRun -> predictionRun.selectionsGenerated())
                        .sum();
                skipped += predictionResponse.predictionGenerationRuns().stream()
                        .mapToInt(predictionRun -> predictionRun.selectionsSkipped())
                        .sum();
                boolean generationFailed = predictionResponse.predictionGenerationRuns().stream()
                        .anyMatch(predictionRun -> predictionRun.status() == PredictionGenerationStatus.FAILED);

                var settlementResponse = settlementService.settlePredictions(new SettlementRequest(
                        singleLeague,
                        pipelineDate,
                        backtestFrom,
                        backtestTo,
                        modelVersion,
                        true
                ));
                settlementRuns += settlementResponse.settlementRuns().size();
                settled += settlementResponse.settlementRuns().stream()
                        .mapToInt(run -> run.wonCount() + run.lostCount() + run.voidCount())
                        .sum();
                boolean settlementFailed = settlementResponse.settlementRuns().stream()
                        .anyMatch(run -> run.status() == SettlementStatus.FAILED);

                if (generationFailed || settlementFailed) {
                    failedLeagues.add(leagueCode.name());
                }
            } catch (Exception exception) {
                failedLeagues.add(leagueCode.name() + "=" + truncate(exception.getMessage(), 200));
            }
        }

        OffsetDateTime finishedAt = OffsetDateTime.now(clock);
        String summary = "predictionRuns=" + predictionRuns
                + ", selectionsGenerated=" + generated
                + ", selectionsSkipped=" + skipped
                + ", settlementRuns=" + settlementRuns
                + ", settledSelections=" + settled
                + ", failedLeagues=" + failedLeagues.size()
                + (failedLeagues.isEmpty() ? "" : " " + failedLeagues);
        boolean anyProgress = predictionRuns > 0 || settlementRuns > 0 || generated > 0 || settled > 0;
        String status = failedLeagues.isEmpty()
                ? "SUCCESS"
                : anyProgress ? "PARTIAL_SUCCESS" : "FAILED";
        return new PipelineStepResponse(
                "HISTORICAL_PREDICTIONS",
                status,
                startedAt,
                finishedAt,
                Duration.between(startedAt, finishedAt).toMillis(),
                summary,
                "FAILED".equals(status) ? "All historical prediction rebuild attempts failed." : null
        );
    }

    private PipelineStepResponse skippedStep(String stepName, String summary) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return new PipelineStepResponse(
                stepName,
                "SKIPPED",
                now,
                now,
                0L,
                summary,
                null
        );
    }

    private boolean stepFailed(PipelineStepResponse step) {
        return step != null && "FAILED".equals(step.status());
    }

    private int qualitySnapshotCount(PipelineStepResponse step) {
        if (step == null || !"SUCCESS".equals(step.status()) || !StringUtils.hasText(step.summary())) {
            return -1;
        }
        String prefix = "qualitySnapshots=";
        for (String token : step.summary().split(",")) {
            String trimmed = token.trim();
            if (trimmed.startsWith(prefix)) {
                try {
                    return Integer.parseInt(trimmed.substring(prefix.length()));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private PipelineStatus pipelineStatus(List<PipelineStepResponse> steps) {
        if (steps.isEmpty()) {
            return PipelineStatus.FAILED;
        }
        long failed = steps.stream().filter(step -> "FAILED".equals(step.status())).count();
        long skipped = steps.stream().filter(step -> "SKIPPED".equals(step.status())).count();
        long partial = steps.stream().filter(step -> "PARTIAL_SUCCESS".equals(step.status())).count();
        if (failed == 0 && skipped == 0 && partial == 0) {
            return PipelineStatus.SUCCESS;
        }
        if (failed == steps.size()) {
            return PipelineStatus.FAILED;
        }
        return PipelineStatus.PARTIAL_SUCCESS;
    }

    private FullPipelineResponse response(PipelineRun run, List<PipelineStepResponse> steps, Set<LeagueCode> leagueCodes) {
        return new FullPipelineResponse(
                run.getId(),
                run.getPipelineDate(),
                run.getPipelineStatus().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                run.getModelVersion(),
                leagueCodes.stream().map(Enum::name).sorted().toList(),
                List.copyOf(steps),
                run.getFailureReason()
        );
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
        return leagues.stream()
                .sorted(Comparator.comparing(league -> league.getCode().name()))
                .toList();
    }

    private Set<MatchStatus> resolvePredictionStatuses(Set<MatchStatus> requestedStatuses) {
        if (requestedStatuses == null || requestedStatuses.isEmpty()) {
            return EnumSet.of(MatchStatus.SCHEDULED);
        }
        EnumSet<MatchStatus> statuses = EnumSet.copyOf(requestedStatuses);
        statuses.remove(MatchStatus.CANCELLED);
        statuses.remove(MatchStatus.ABANDONED);
        if (statuses.isEmpty()) {
            throw new InvalidRequestException("At least one usable prediction match status is required.");
        }
        return statuses;
    }

    private String resolveModelVersion(String requestedModelVersion) {
        String modelVersion = StringUtils.hasText(requestedModelVersion)
                ? requestedModelVersion.trim()
                : predictionProperties.defaultModelVersion();
        if (!StringUtils.hasText(modelVersion)) {
            throw new InvalidRequestException("modelVersion is required.");
        }
        return truncate(modelVersion, 80);
    }

    private boolean enabled(Boolean value) {
        return value == null || value;
    }

    private boolean truthy(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void validateDateRange(LocalDate from, LocalDate to, String label) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException(label + " date from must be on or before date to.");
        }
    }

    private String leagueCodesCsv(Set<LeagueCode> leagueCodes) {
        return leagueCodes.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private String stepSummaryJson(List<PipelineStepResponse> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
