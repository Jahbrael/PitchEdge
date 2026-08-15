package com.betai.service;

import com.betai.api.dto.PredictionGenerationRequest;
import com.betai.api.dto.PredictionGenerationResponse;
import com.betai.api.dto.PredictionGenerationRunResponse;
import com.betai.config.PredictionProperties;
import com.betai.domain.feature.FeatureGroup;
import com.betai.domain.feature.LeagueBaseline;
import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.feature.TeamFeatureSnapshot;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.prediction.PredictionGenerationRun;
import com.betai.domain.prediction.PredictionGenerationStatus;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.exception.InvalidRequestException;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.repository.LeagueBaselineRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.ModelQualitySnapshotRepository;
import com.betai.repository.ModelTuningProfileRepository;
import com.betai.repository.PredictionGenerationRunRepository;
import com.betai.repository.PredictionSelectionRepository;
import com.betai.repository.TeamFeatureSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PredictionGenerationServiceImpl implements PredictionGenerationService {

    private final PredictionProperties predictionProperties;
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final MarketDefinitionRepository marketDefinitionRepository;
    private final LeagueBaselineRepository leagueBaselineRepository;
    private final TeamFeatureSnapshotRepository teamFeatureSnapshotRepository;
    private final PredictionSelectionRepository predictionSelectionRepository;
    private final PredictionGenerationRunRepository predictionGenerationRunRepository;
    private final ModelQualitySnapshotRepository modelQualitySnapshotRepository;
    private final ModelTuningProfileRepository modelTuningProfileRepository;
    private final MarketProbabilityEngine marketProbabilityEngine;
    private final ProbabilityCalibrationService probabilityCalibrationService;
    private final ModelTuningService modelTuningService;
    private final OddsValueService oddsValueService;
    private final MarketAvailabilityService marketAvailabilityService;
    private final HistoricalSeasonWindowService historicalSeasonWindowService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public PredictionGenerationResponse generatePredictions(PredictionGenerationRequest request) {
        OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
        LocalDate calculationDate = request.calculationDate() == null ? LocalDate.now(clock) : request.calculationDate();
        LocalDate fixtureDateFrom = request.fixtureDateFrom() == null ? calculationDate : request.fixtureDateFrom();
        LocalDate fixtureDateTo = request.fixtureDateTo() == null ? fixtureDateFrom : request.fixtureDateTo();
        Set<MatchStatus> statuses = resolveStatuses(request.matchStatuses());
        String modelVersion = resolveModelVersion(request.modelVersion());
        String calibrationModelVersion = resolveCalibrationModelVersion(request.calibrationModelVersion(), modelVersion);

        FlushModeType previousFlushMode = entityManager.getFlushMode();
        entityManager.setFlushMode(FlushModeType.COMMIT);
        try {
            validateDateRange(fixtureDateFrom, fixtureDateTo);
            List<League> leagues = resolveLeagues(request.leagueCodes());
            List<PredictionGenerationRunResponse> runs = new ArrayList<>();

            for (League league : leagues) {
                runs.add(generateLeaguePredictions(
                        league,
                        calculationDate,
                        fixtureDateFrom,
                        fixtureDateTo,
                        statuses,
                        modelVersion,
                        calibrationModelVersion,
                        resolveFeatureSeasonLabel(request.featureSeasonLabel(), league),
                        request.requestedSeasonCount(),
                        request.seasonSelectionMode(),
                        request.customSeasonIds(),
                        request.forceRegenerate()
                ));
            }

            return new PredictionGenerationResponse(UUID.randomUUID(), triggeredAt, List.copyOf(runs));
        } finally {
            entityManager.setFlushMode(previousFlushMode);
        }
    }

    private PredictionGenerationRunResponse generateLeaguePredictions(
            League league,
            LocalDate calculationDate,
            LocalDate fixtureDateFrom,
            LocalDate fixtureDateTo,
            Set<MatchStatus> statuses,
            String modelVersion,
            String calibrationModelVersion,
            String featureSeasonLabel,
            Integer requestedSeasonCount,
            SeasonSelectionMode seasonSelectionMode,
            Set<String> customSeasonIds,
            boolean forceRegenerate
    ) {
        String statusKey = statusKey(statuses);
        HistoricalSeasonWindow window = historicalSeasonWindowService.resolveWindow(
                league,
                calculationDate,
                requestedSeasonCount,
                seasonSelectionMode,
                customSeasonIds,
                FeatureGroup.RESULTS
        );
        String availabilitySeasonLabel = window.selectedSeasonIds().isEmpty()
                ? featureSeasonLabel
                : window.selectedSeasonIds().getFirst();
        Map<MarketCode, MarketDefinition> marketDefinitions = availableMarketDefinitions(league, availabilitySeasonLabel);
        int activeMarketCount = marketDefinitions.size();
        if (!forceRegenerate && activeMarketCount > 0) {
            var cached = predictionGenerationRunRepository
                    .findFirstByLeague_CodeAndModelVersionAndSeasonWindowKeyAndCalculationDateAndFixtureDateFromAndFixtureDateToAndMatchStatusesAndGenerationStatusOrderByStartedAtDesc(
                            league.getCode(),
                            modelVersion,
                            window.seasonWindowKey(),
                            calculationDate,
                            fixtureDateFrom,
                            fixtureDateTo,
                            statusKey,
                            PredictionGenerationStatus.SUCCESS
                    );
            if (cached.isPresent() && cacheCoversActiveMarkets(cached.get(), activeMarketCount, statuses, modelVersion)) {
                return PredictionGenerationRunResponse.from(cached.get(), true);
            }
        }

        PredictionGenerationRun run = predictionGenerationRunRepository.save(new PredictionGenerationRun()
                .setLeague(league)
                .setModelVersion(modelVersion)
                .setFeatureSeasonLabel(availabilitySeasonLabel)
                .setCalculationDate(calculationDate)
                .setFixtureDateFrom(fixtureDateFrom)
                .setFixtureDateTo(fixtureDateTo)
                .setMatchStatuses(statusKey)
                .setRequestedSeasonCount(window.requestedSeasonCount())
                .setActualSeasonCountUsed(window.actualSeasonCountUsed())
                .setSeasonSelectionMode(window.seasonSelectionMode().name())
                .setSelectedSeasonIds(String.join(",", window.selectedSeasonIds()))
                .setSeasonWindowKey(window.seasonWindowKey())
                .setFallbackApplied(window.fallbackApplied())
                .setGenerationStatus(PredictionGenerationStatus.RUNNING)
                .setStartedAt(OffsetDateTime.now(clock)));

        try {
            if (window.actualSeasonCountUsed() == 0) {
                run.finish(
                        OffsetDateTime.now(clock),
                        PredictionGenerationStatus.SKIPPED,
                        0,
                        0,
                        0,
                        "No usable historical seasons are available for prediction generation."
                );
                return PredictionGenerationRunResponse.from(predictionGenerationRunRepository.save(run), false);
            }

            LeagueBaseline baseline = leagueBaselineRepository
                    .findByLeague_CodeAndCalculationDateAndSeasonWindowKey(
                            league.getCode(),
                            calculationDate,
                            window.seasonWindowKey()
                    )
                    .orElseThrow(() -> new ReferenceDataNotFoundException("League baseline is missing for "
                            + league.getCode() + " season window " + window.seasonWindowKey()
                            + " on " + calculationDate + "."));

            List<TeamFeatureSnapshot> teamFeatures = teamFeatureSnapshotRepository
                    .findByLeague_CodeAndCalculationDateAndSeasonWindowKeyOrderByTeam_CanonicalNameAsc(
                            league.getCode(),
                            calculationDate,
                            window.seasonWindowKey()
                    );
            Map<UUID, TeamFeatureSnapshot> teamFeatureByTeamId = teamFeatures.stream()
                    .collect(Collectors.toMap(feature -> feature.getTeam().getId(), Function.identity()));

            List<Match> matches = matchRepository.findMatchesForPredictionGeneration(
                    league.getCode(),
                    fixtureDateFrom,
                    fixtureDateTo,
                    statuses
            );

            if (matches.isEmpty()) {
                run.finish(
                        OffsetDateTime.now(clock),
                        PredictionGenerationStatus.SKIPPED,
                        0,
                        0,
                        0,
                        "No matches matched the requested league/date/status filters."
                );
                return PredictionGenerationRunResponse.from(predictionGenerationRunRepository.save(run), false);
            }

            if (marketDefinitions.isEmpty()) {
                run.finish(
                        OffsetDateTime.now(clock),
                        PredictionGenerationStatus.SKIPPED,
                        matches.size(),
                        0,
                        0,
                        "No markets are available for this league/season based on data coverage."
                );
                return PredictionGenerationRunResponse.from(predictionGenerationRunRepository.save(run), false);
            }

            PredictionGenerationCounters counters = generateSelections(
                    matches,
                    teamFeatureByTeamId,
                    baseline,
                    marketDefinitions,
                    calculationDate,
                    modelVersion,
                    calibrationModelVersion,
                    modelQualitySnapshotRepository.existsByModelVersionAndQualityDateLessThanEqual(
                            calibrationModelVersion,
                            calculationDate
                    ),
                    modelTuningProfileRepository.existsByModelVersionAndProfileDateLessThanEqualAndActiveTrue(
                            calibrationModelVersion,
                            calculationDate
                    ),
                    window,
                    forceRegenerate
            );

            PredictionGenerationStatus status = counters.generated() > 0
                    ? PredictionGenerationStatus.SUCCESS
                    : PredictionGenerationStatus.SKIPPED;
            String failureReason = status == PredictionGenerationStatus.SKIPPED
                    ? "No selections were generated. Required team features may be missing, or selections already exist for this model version."
                    : null;

            run.finish(
                    OffsetDateTime.now(clock),
                    status,
                    matches.size(),
                    counters.generated(),
                    counters.skipped(),
                    failureReason
            );
            return PredictionGenerationRunResponse.from(predictionGenerationRunRepository.save(run), false);
        } catch (Exception exception) {
            run.finish(
                    OffsetDateTime.now(clock),
                    PredictionGenerationStatus.FAILED,
                    run.getMatchesEvaluated(),
                    run.getSelectionsGenerated(),
                    run.getSelectionsSkipped(),
                    truncate(exception.getMessage(), 1000)
            );
            return PredictionGenerationRunResponse.from(predictionGenerationRunRepository.save(run), false);
        }
    }

    private boolean cacheCoversActiveMarkets(
            PredictionGenerationRun cachedRun,
            int activeMarketCount,
            Set<MatchStatus> statuses,
            String modelVersion
    ) {
        if (cachedRun.getMatchesEvaluated() == 0) {
            return true;
        }
        long expectedSelectionCount = (long) cachedRun.getMatchesEvaluated() * activeMarketCount;
        long storedSelectionCount = predictionSelectionRepository.countStoredEnabledSelectionsForGenerationWindow(
                cachedRun.getLeague().getCode(),
                cachedRun.getFixtureDateFrom(),
                cachedRun.getFixtureDateTo(),
                statuses,
                modelVersion
        );
        return storedSelectionCount >= expectedSelectionCount;
    }

    private Map<MarketCode, MarketDefinition> availableMarketDefinitions(League league, String featureSeasonLabel) {
        return marketDefinitionRepository
                .findByEnabledTrueOrderByDisplayNameAsc()
                .stream()
                .filter(market -> marketAvailabilityService.isMarketAvailable(
                        league.getCode(),
                        featureSeasonLabel,
                        market.getCode()
                ))
                .collect(Collectors.toMap(MarketDefinition::getCode, Function.identity()));
    }

    private PredictionGenerationCounters generateSelections(
            List<Match> matches,
            Map<UUID, TeamFeatureSnapshot> teamFeatureByTeamId,
            LeagueBaseline baseline,
            Map<MarketCode, MarketDefinition> marketDefinitions,
            LocalDate calculationDate,
            String modelVersion,
            String calibrationModelVersion,
            boolean calibrationInputsAvailable,
            boolean tuningInputsAvailable,
            HistoricalSeasonWindow window,
            boolean forceRegenerate
    ) {
        int generated = 0;
        int skipped = 0;
        OffsetDateTime generatedAt = OffsetDateTime.now(clock);
        List<UUID> matchIds = matches.stream().map(Match::getId).toList();
        Map<String, PredictionSelection> existingByMatchMarket = new HashMap<>();
        if (!matchIds.isEmpty()) {
            predictionSelectionRepository.findExistingForMatchesAndModel(matchIds, modelVersion)
                    .forEach(selection -> existingByMatchMarket.put(
                            selectionKey(selection.getMatch().getId(), selection.getMarketDefinition().getId()),
                            selection
                    ));
        }
        List<PredictionSelection> selectionsToSave = new ArrayList<>(matches.size() * marketDefinitions.size());

        for (Match match : matches) {
            TeamFeatureSnapshot homeFeature = teamFeatureByTeamId.get(match.getHomeTeam().getId());
            TeamFeatureSnapshot awayFeature = teamFeatureByTeamId.get(match.getAwayTeam().getId());
            if (homeFeature == null || awayFeature == null) {
                skipped += marketDefinitions.size();
                continue;
            }

            var scores = marketProbabilityEngine.score(match, homeFeature, awayFeature, baseline);

            for (Map.Entry<MarketCode, BigDecimal> entry : scores.probabilities().entrySet()) {
                MarketDefinition marketDefinition = marketDefinitions.get(entry.getKey());
                if (marketDefinition == null) {
                    skipped++;
                    continue;
                }

                String selectionKey = selectionKey(match.getId(), marketDefinition.getId());
                PredictionSelection existing = existingByMatchMarket.get(selectionKey);
                if (existing != null && !forceRegenerate) {
                    skipped++;
                    continue;
                }

                ProbabilityCalibrationResult calibration = calibrate(
                        match,
                        entry,
                        calibrationModelVersion,
                        calculationDate,
                        calibrationInputsAvailable
                );
                ModelTuningResult tuning = tune(
                        match,
                        entry.getKey(),
                        calibrationModelVersion,
                        calculationDate,
                        calibration.calibratedProbability(),
                        tuningInputsAvailable
                );

                PredictionSelection selection = existing == null ? new PredictionSelection() : existing;
                if (existing != null && forceRegenerate && wouldDowngradeRatedSelection(existing, calibration, tuning)) {
                    skipped++;
                    continue;
                }
                selection.setMatch(match)
                        .setMarketDefinition(marketDefinition)
                        .setPredictedValue(marketDefinition.getSelectionValue())
                        .setRawProbability(calibration.rawProbability())
                        .setProbability(tuning.tunedProbability())
                        .setModelVersion(modelVersion)
                        .setGeneratedAt(generatedAt)
                        .setCorrelationGroupKey(correlationGroupKey(match, entry.getKey()))
                        .setFeatureSnapshotJson(featureSnapshotJson(match, homeFeature, awayFeature, baseline, scores.expectedProfile(), modelVersion, calibrationModelVersion, calibration, tuning, window))
                        .setConfidenceBand(calibration.confidenceBand())
                        .setModelQualitySnapshot(calibration.modelQualitySnapshot())
                        .setCalibrationNote(truncate(calibration.calibrationNote(), 500))
                        .setModelTuningProfile(tuning.modelTuningProfile())
                        .setTuningAdjustment(tuning.appliedAdjustment())
                        .setTuningNote(truncate(tuning.tuningNote(), 500))
                        .setRequestedSeasonCount(window.requestedSeasonCount())
                        .setActualSeasonCountUsed(window.actualSeasonCountUsed())
                        .setSelectedSeasons(String.join(",", window.selectedSeasonIds()))
                        .setCompletedMatchesUsed(window.completedMatchesUsed())
                        .setFallbackApplied(window.fallbackApplied())
                        .setHistoricalDepthStatus(window.historicalDepthStatus().name())
                        .setMarketSpecificDataCoverage(window.marketSpecificDataCoverage())
                        .setSeasonWindowKey(window.seasonWindowKey())
                        .setOutcome(PredictionOutcome.PENDING);
                if (match.getStatus() == MatchStatus.SCHEDULED) {
                    oddsValueService.applyBestOdds(selection);
                }
                selectionsToSave.add(selection);
                generated++;
            }
        }

        predictionSelectionRepository.saveAll(selectionsToSave);
        return new PredictionGenerationCounters(generated, skipped);
    }

    private ProbabilityCalibrationResult calibrate(
            Match match,
            Map.Entry<MarketCode, BigDecimal> entry,
            String calibrationModelVersion,
            LocalDate calculationDate,
            boolean calibrationInputsAvailable
    ) {
        if (!calibrationInputsAvailable) {
            BigDecimal rawProbability = scale(entry.getValue());
            return new ProbabilityCalibrationResult(
                    rawProbability,
                    rawProbability,
                    PredictionConfidenceBand.UNRATED,
                    null,
                    "No model quality snapshots exist for model " + calibrationModelVersion + " on or before " + calculationDate + "."
            );
        }
        return probabilityCalibrationService.calibrate(
                match.getLeague().getCode(),
                entry.getKey(),
                calibrationModelVersion,
                calculationDate,
                entry.getValue()
        );
    }

    private ModelTuningResult tune(
            Match match,
            MarketCode marketCode,
            String calibrationModelVersion,
            LocalDate calculationDate,
            BigDecimal calibratedProbability,
            boolean tuningInputsAvailable
    ) {
        if (!tuningInputsAvailable) {
            BigDecimal probability = scale(calibratedProbability);
            return new ModelTuningResult(
                    probability,
                    probability,
                    BigDecimal.ZERO.setScale(6),
                    null,
                    "No active model tuning profiles exist for model " + calibrationModelVersion + " on or before " + calculationDate + "."
            );
        }
        return modelTuningService.tune(
                match.getLeague().getCode(),
                marketCode,
                calibrationModelVersion,
                calculationDate,
                calibratedProbability
        );
    }

    private boolean wouldDowngradeRatedSelection(
            PredictionSelection existing,
            ProbabilityCalibrationResult calibration,
            ModelTuningResult tuning
    ) {
        boolean existingRated = existing.getConfidenceBand() != null
                && existing.getConfidenceBand() != PredictionConfidenceBand.UNRATED
                || existing.getModelQualitySnapshot() != null
                || existing.getModelTuningProfile() != null;
        if (!existingRated) {
            return false;
        }
        return calibration.confidenceBand() == PredictionConfidenceBand.UNRATED
                && calibration.modelQualitySnapshot() == null
                && tuning.modelTuningProfile() == null;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(6, java.math.RoundingMode.HALF_UP);
    }

    private String selectionKey(UUID matchId, UUID marketDefinitionId) {
        return matchId + ":" + marketDefinitionId;
    }

    private String featureSnapshotJson(
            Match match,
            TeamFeatureSnapshot homeFeature,
            TeamFeatureSnapshot awayFeature,
            LeagueBaseline baseline,
            MarketProbabilityEngine.ExpectedProfile expectedProfile,
            String modelVersion,
            String calibrationModelVersion,
            ProbabilityCalibrationResult calibration,
            ModelTuningResult tuning,
            HistoricalSeasonWindow window
    ) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("modelVersion", modelVersion);
            snapshot.put("calibrationModelVersion", calibrationModelVersion);
            snapshot.put("probabilityModel", "dixon-coles-scoreline-blended-with-form-v2");
            snapshot.put("neutralSiteAdjustment", match.getLeague().getCode() == LeagueCode.FIFA_WORLD_CUP_2026);
            snapshot.put("calculationDate", baseline.getCalculationDate().toString());
            snapshot.put("seasonLabel", baseline.getSeasonLabel());
            snapshot.put("requestedSeasonCount", window.requestedSeasonCount());
            snapshot.put("actualSeasonCountUsed", window.actualSeasonCountUsed());
            snapshot.put("seasonSelectionMode", window.seasonSelectionMode().name());
            snapshot.put("selectedSeasonIds", window.selectedSeasonIds());
            snapshot.put("currentSeasonIncluded", window.currentSeasonIncluded());
            snapshot.put("fallbackApplied", window.fallbackApplied());
            snapshot.put("oldestDataDate", window.oldestDataDate() == null ? null : window.oldestDataDate().toString());
            snapshot.put("newestDataDate", window.newestDataDate() == null ? null : window.newestDataDate().toString());
            snapshot.put("completedMatchesUsed", window.completedMatchesUsed());
            snapshot.put("marketSpecificUsableSeasonCount", window.marketSpecificUsableSeasonCount());
            snapshot.put("recencyWeightingVersion", window.recencyWeightingVersion());
            snapshot.put("recencyWeights", window.recencyWeights());
            snapshot.put("seasonWindowKey", window.seasonWindowKey());
            snapshot.put("historicalDepthStatus", window.historicalDepthStatus().name());
            snapshot.put("marketSpecificDataCoverage", window.marketSpecificDataCoverage());
            snapshot.put("matchId", match.getId().toString());
            snapshot.put("homeTeamId", homeFeature.getTeam().getId().toString());
            snapshot.put("awayTeamId", awayFeature.getTeam().getId().toString());
            snapshot.put("homeFormScore", homeFeature.getFormScore());
            snapshot.put("awayFormScore", awayFeature.getFormScore());
            snapshot.put("homeGoalsForPerMatch", homeFeature.getGoalsForPerMatch());
            snapshot.put("awayGoalsForPerMatch", awayFeature.getGoalsForPerMatch());
            snapshot.put("leagueAvgTotalGoals", baseline.getAvgTotalGoals());
            snapshot.put("expectedHomeGoals", expectedProfile.homeGoals());
            snapshot.put("expectedAwayGoals", expectedProfile.awayGoals());
            snapshot.put("expectedTotalGoals", expectedProfile.totalGoals());
            snapshot.put("expectedTotalCorners", expectedProfile.totalCorners());
            snapshot.put("expectedHomeCorners", expectedProfile.homeCorners());
            snapshot.put("expectedAwayCorners", expectedProfile.awayCorners());
            snapshot.put("expectedTotalYellowCards", expectedProfile.totalYellowCards());
            snapshot.put("rawProbability", calibration.rawProbability());
            snapshot.put("calibratedProbability", calibration.calibratedProbability());
            snapshot.put("tunedProbability", tuning.tunedProbability());
            snapshot.put("tuningAdjustment", tuning.appliedAdjustment());
            snapshot.put("confidenceBand", calibration.confidenceBand().name());
            if (calibration.modelQualitySnapshot() != null) {
                snapshot.put("modelQualitySnapshotId", calibration.modelQualitySnapshot().getId().toString());
                snapshot.put("modelQualityDate", calibration.modelQualitySnapshot().getQualityDate().toString());
                snapshot.put("modelQualitySampleSize", calibration.modelQualitySnapshot().getSampleSize());
                snapshot.put("modelQualityCalibrationError", calibration.modelQualitySnapshot().getCalibrationError());
            }
            snapshot.put("calibrationNote", calibration.calibrationNote());
            if (tuning.modelTuningProfile() != null) {
                snapshot.put("modelTuningProfileId", tuning.modelTuningProfile().getId().toString());
                snapshot.put("modelTuningProfileDate", tuning.modelTuningProfile().getProfileDate().toString());
                snapshot.put("modelTuningSampleSize", tuning.modelTuningProfile().getSampleSize());
                snapshot.put("modelTuningRecommendation", tuning.modelTuningProfile().getTuningRecommendation().name());
            }
            snapshot.put("tuningNote", tuning.tuningNote());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new InvalidRequestException("Prediction feature snapshot could not be serialized.");
        }
    }

    private String correlationGroupKey(Match match, MarketCode marketCode) {
        String group = switch (marketCode.getMarketType()) {
            case MATCH_RESULT, DOUBLE_CHANCE, DRAW_NO_BET -> "result";
            case TOTAL_GOALS, TEAM_TOTAL_GOALS, BOTH_TEAMS_TO_SCORE, CLEAN_SHEET -> "goals";
            case TEAM_TO_SCORE_FIRST, GOAL_PERIOD, TEAM_TO_WIN_PERIOD -> "events";
            case TOTAL_CORNERS, TEAM_CORNERS -> "corners";
            case TOTAL_YELLOW_CARDS, RED_CARD -> "discipline";
        };
        return "match:" + match.getId() + ":" + group;
    }

    private void validateDateRange(LocalDate fixtureDateFrom, LocalDate fixtureDateTo) {
        if (fixtureDateFrom.isAfter(fixtureDateTo)) {
            throw new InvalidRequestException("fixtureDateFrom must be on or before fixtureDateTo.");
        }
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

    private Set<MatchStatus> resolveStatuses(Set<MatchStatus> requestedStatuses) {
        if (requestedStatuses == null || requestedStatuses.isEmpty()) {
            return EnumSet.of(MatchStatus.SCHEDULED);
        }
        EnumSet<MatchStatus> statuses = EnumSet.copyOf(requestedStatuses);
        statuses.remove(MatchStatus.CANCELLED);
        statuses.remove(MatchStatus.ABANDONED);
        if (statuses.isEmpty()) {
            throw new InvalidRequestException("At least one usable match status is required.");
        }
        return statuses;
    }

    private String resolveModelVersion(String requestedModelVersion) {
        String modelVersion = StringUtils.hasText(requestedModelVersion)
                ? requestedModelVersion.trim()
                : predictionProperties.defaultModelVersion();
        if (!StringUtils.hasText(modelVersion)) {
            throw new InvalidRequestException("modelVersion is required when no default model version is configured.");
        }
        if (modelVersion.length() > 80) {
            throw new InvalidRequestException("modelVersion cannot exceed 80 characters.");
        }
        return modelVersion;
    }

    private String resolveCalibrationModelVersion(String requestedModelVersion, String fallbackModelVersion) {
        if (!StringUtils.hasText(requestedModelVersion)) {
            return fallbackModelVersion;
        }
        String modelVersion = requestedModelVersion.trim();
        if (modelVersion.length() > 80) {
            throw new InvalidRequestException("calibrationModelVersion cannot exceed 80 characters.");
        }
        return modelVersion;
    }

    private String resolveFeatureSeasonLabel(String requestedFeatureSeasonLabel, League league) {
        String featureSeasonLabel = StringUtils.hasText(requestedFeatureSeasonLabel)
                ? requestedFeatureSeasonLabel.trim()
                : league.getCurrentSeason();
        if (featureSeasonLabel.length() > 128) {
            throw new InvalidRequestException("featureSeasonLabel cannot exceed 128 characters.");
        }
        return featureSeasonLabel;
    }

    private String statusKey(Collection<MatchStatus> statuses) {
        return statuses.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record PredictionGenerationCounters(int generated, int skipped) {
    }
}
