package com.betai.service;

import com.betai.api.dto.HistoricalPredictionRequest;
import com.betai.api.dto.PredictionRequest;
import com.betai.api.dto.PredictionResponseStatus;
import com.betai.api.dto.PredictionResponse;
import com.betai.config.PredictionProperties;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.odds.ValueRating;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.prediction.PredictionGenerationStatus;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.refresh.RefreshStatus;
import com.betai.exception.InvalidRequestException;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.repository.DataRefreshLogRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.PredictionGenerationRunRepository;
import com.betai.repository.PredictionSelectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PredictionFormServiceImpl implements PredictionFormService {

    private final PredictionProperties predictionProperties;
    private final LeagueRepository leagueRepository;
    private final MarketDefinitionRepository marketDefinitionRepository;
    private final MatchRepository matchRepository;
    private final PredictionSelectionRepository predictionSelectionRepository;
    private final PredictionGenerationRunRepository predictionGenerationRunRepository;
    private final DataRefreshLogRepository dataRefreshLogRepository;
    private final HistoricalPredictionService historicalPredictionService;
    private final PredictionCandidateFilter candidateFilter;
    private final BatchBuilder batchBuilder;
    private final FixtureCardIndicatorService fixtureCardIndicatorService;
    private final Clock clock;

    @Override
    @Transactional
    public PredictionResponse generatePredictions(PredictionRequest request) {
        validateRequestBounds(request);
        ResolvedPredictionRequest resolvedRequest = PredictionStrategyResolver.resolve(request, predictionProperties);
        validateLeagues(request.leagueCodes());
        validateMarkets(request.marketCodes());

        String baseModelVersion = resolveModelVersion();
        LocalDate today = LocalDate.now(clock);
        boolean historicalWindow = request.fixtureDateTo().isBefore(today);
        HistoricalReplayContext historicalReplay = historicalWindow
                ? ensureHistoricalReplay(request, baseModelVersion)
                : HistoricalReplayContext.notUsed(baseModelVersion);
        String modelVersion = historicalReplay.modelVersion();
        List<MatchStatus> candidateStatuses = historicalWindow
                ? List.of(MatchStatus.FINISHED)
                : resolveCandidateStatuses();
        List<PredictionOutcome> allowedOutcomes = historicalWindow
                ? List.of(PredictionOutcome.PENDING, PredictionOutcome.WON, PredictionOutcome.LOST, PredictionOutcome.VOID)
                : List.of(PredictionOutcome.PENDING);
        var fixtures = matchRepository.findCandidateFixtures(
                request.leagueCodes(),
                request.fixtureDateFrom(),
                request.fixtureDateTo(),
                candidateStatuses
        );
        var storedCandidates = predictionSelectionRepository.findCandidateSelectionsForModelAndOutcomes(
                request.leagueCodes(),
                request.marketCodes(),
                request.fixtureDateFrom(),
                request.fixtureDateTo(),
                candidateStatuses,
                allowedOutcomes,
                modelVersion
        );
        PredictionCandidateFilterResult filteredCandidates = candidateFilter.filterAndRank(storedCandidates, resolvedRequest);
        var batches = batchBuilder.build(
                filteredCandidates.candidates(),
                resolvedRequest.toBatchBuildRequest(filteredCandidates.qualifiedSelectionsFound())
        );
        var fixtureIndicators = fixtureCardIndicatorService.build(batches);
        int selectionsReturned = batches.stream()
                .mapToInt(batch -> batch.returnedSelections())
                .sum();
        var warnings = buildWarnings(
                request,
                resolvedRequest,
                modelVersion,
                candidateStatuses,
                fixtures.size(),
                storedCandidates.size(),
                filteredCandidates.qualifiedSelectionsFound(),
                batches.size(),
                selectionsReturned,
                storedCandidates,
                batches,
                historicalReplay.warnings()
        );
        PredictionResponseStatus status = responseStatus(resolvedRequest, filteredCandidates.qualifiedSelectionsFound(), batches);
        String warningMessage = responseWarningMessage(status, warnings);
        int requestedSeasonCount = request.requestedSeasonCount() == null
                ? predictionProperties.defaultSeasonCount()
                : request.requestedSeasonCount();
        List<String> leaguesWithFullRequestedHistory = leaguesWithFullRequestedHistory(storedCandidates, requestedSeasonCount);
        List<String> leaguesUsingFallbackHistory = leaguesUsingFallbackHistory(storedCandidates, requestedSeasonCount);

        return new PredictionResponse(
                UUID.randomUUID(),
                OffsetDateTime.now(clock),
                request,
                modelVersion,
                candidateStatuses.stream().map(Enum::name).toList(),
                fixtures.size(),
                storedCandidates.size(),
                resolvedRequest.minimumSelections(),
                resolvedRequest.maximumSelections(),
                filteredCandidates.qualifiedSelectionsFound(),
                selectionsReturned,
                requestedSeasonCount,
                request.requestedSeasonCount() == null,
                leaguesWithFullRequestedHistory,
                leaguesUsingFallbackHistory,
                status,
                warningMessage,
                selectionsReturned,
                batches,
                warnings,
                fixtureIndicators
        );
    }

    private HistoricalReplayContext ensureHistoricalReplay(PredictionRequest request, String baseModelVersion) {
        LocalDate calculationDate = request.fixtureDateFrom().minusDays(1);
        var response = historicalPredictionService.generateHistoricalPredictions(new HistoricalPredictionRequest(
                request.leagueCodes(),
                calculationDate,
                null,
                request.fixtureDateFrom(),
                request.fixtureDateTo(),
                baseModelVersion,
                null,
                false,
                false,
                true,
                request.requestedSeasonCount(),
                request.seasonSelectionMode(),
                request.customSeasonIds()
        ));
        List<String> warnings = new ArrayList<>();
        warnings.add("Historical replay was automatically run or reused using calculationDate "
                + calculationDate + " and modelVersion " + response.historicalModelVersion() + ".");
        warnings.addAll(response.warnings());
        return new HistoricalReplayContext(response.historicalModelVersion(), warnings);
    }

    private void validateRequestBounds(PredictionRequest request) {
        if (request.fixtureDateFrom().isAfter(request.fixtureDateTo())) {
            throw new InvalidRequestException("fixtureDateFrom must be on or before fixtureDateTo.");
        }
        long dateRangeDays = ChronoUnit.DAYS.between(request.fixtureDateFrom(), request.fixtureDateTo()) + 1;
        if (dateRangeDays > predictionProperties.maxDateRangeDays()) {
            throw new InvalidRequestException("Fixture date range cannot exceed "
                    + predictionProperties.maxDateRangeDays() + " days.");
        }
        if (request.requestedSeasonCount() != null && request.requestedSeasonCount() > predictionProperties.maximumSeasonCount()) {
            throw new InvalidRequestException("requestedSeasonCount must be less than or equal to "
                    + predictionProperties.maximumSeasonCount() + ".");
        }
    }

    private void validateLeagues(Set<LeagueCode> requestedCodes) {
        var activeLeagues = leagueRepository.findByCodeInAndActiveTrueAndScrapeEnabledTrue(requestedCodes);
        Set<LeagueCode> activeCodes = activeLeagues.stream()
                .map(league -> league.getCode())
                .collect(Collectors.toSet());
        EnumSet<LeagueCode> missing = EnumSet.copyOf(requestedCodes);
        missing.removeAll(activeCodes);
        if (!missing.isEmpty()) {
            throw new ReferenceDataNotFoundException("Unsupported, inactive, or import-pending leagues: " + missing + ".");
        }
    }

    private void validateMarkets(Set<MarketCode> requestedCodes) {
        var activeMarkets = marketDefinitionRepository.findByCodeInAndEnabledTrue(requestedCodes);
        Set<MarketCode> activeCodes = activeMarkets.stream()
                .map(market -> market.getCode())
                .collect(Collectors.toSet());
        EnumSet<MarketCode> missing = EnumSet.copyOf(requestedCodes);
        missing.removeAll(activeCodes);
        if (!missing.isEmpty()) {
            throw new ReferenceDataNotFoundException("Unsupported or disabled markets: " + missing + ".");
        }
    }

    private String resolveModelVersion() {
        String modelVersion = predictionProperties.defaultModelVersion();
        if (!StringUtils.hasText(modelVersion)) {
            throw new InvalidRequestException("Default prediction model version is not configured.");
        }
        return modelVersion.trim();
    }

    private List<MatchStatus> resolveCandidateStatuses() {
        List<MatchStatus> configuredStatuses = predictionProperties.formMatchStatuses();
        if (configuredStatuses == null || configuredStatuses.isEmpty()) {
            return List.of(MatchStatus.SCHEDULED);
        }
        List<MatchStatus> statuses = configuredStatuses.stream()
                .filter(status -> status != MatchStatus.CANCELLED && status != MatchStatus.ABANDONED)
                .distinct()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
        if (statuses.isEmpty()) {
            throw new InvalidRequestException("At least one usable form match status must be configured.");
        }
        return statuses;
    }

    private List<String> buildWarnings(
            PredictionRequest request,
            ResolvedPredictionRequest resolvedRequest,
            String modelVersion,
            List<MatchStatus> candidateStatuses,
            int fixtureCount,
            int candidateCount,
            int qualifiedSelectionsFound,
            int batchCount,
            int selectionsReturned,
            List<com.betai.domain.prediction.PredictionSelection> candidates,
            List<com.betai.api.dto.PredictionBatchResponse> batches,
            List<String> initialWarnings
    ) {
        List<String> warnings = new ArrayList<>(initialWarnings);
        LocalDate refreshDate = LocalDate.now(clock);
        int requestedMinimumSelections = resolvedRequest.numberOfBatches() * resolvedRequest.minimumSelections();

        for (LeagueCode leagueCode : request.leagueCodes()) {
            boolean refreshedToday = dataRefreshLogRepository
                    .findFirstByLeague_CodeAndRefreshDateAndRefreshStatusOrderByStartedAtDesc(
                            leagueCode,
                            refreshDate,
                            RefreshStatus.SUCCESS
                    )
                    .isPresent();
            if (!refreshedToday) {
                warnings.add("No successful daily refresh is recorded for " + leagueCode
                        + " on " + refreshDate + "; response uses whatever stored predictions already exist.");
            }

            boolean generatedForRange = predictionGenerationRunRepository
                    .findFirstByLeague_CodeAndModelVersionAndGenerationStatusAndFixtureDateFromLessThanEqualAndFixtureDateToGreaterThanEqualOrderByStartedAtDesc(
                            leagueCode,
                            modelVersion,
                            PredictionGenerationStatus.SUCCESS,
                            request.fixtureDateFrom(),
                            request.fixtureDateTo()
                    )
                    .isPresent();
            if (!generatedForRange) {
                warnings.add("No successful prediction generation run covers " + leagueCode
                        + " from " + request.fixtureDateFrom() + " to " + request.fixtureDateTo()
                        + " for model " + modelVersion + ".");
            }
        }
        if (request.fixtureDateTo().isBefore(refreshDate)) {
            warnings.add("The requested fixture window is historical. Treat this response as analysis/backtesting, not an actionable betting slate.");
        }
        if (candidateStatuses.contains(MatchStatus.FINISHED)) {
            warnings.add("The form status filter includes FINISHED matches for local backtesting. Set BETAI_FORM_MATCH_STATUSES=SCHEDULED for production betting mode.");
        }
        if (fixtureCount == 0) {
            warnings.add("No fixtures matched the requested leagues, date range, and form status filter " + candidateStatuses + ".");
        }
        if (resolvedRequest.legacyValueModeActive()
                && resolvedRequest.legacyValueMode() == com.betai.api.dto.ValueMode.POSITIVE_VALUE_ONLY) {
            warnings.add("Value mode POSITIVE_VALUE_ONLY excludes selections without imported odds or positive expected value.");
        }
        if (resolvedRequest.legacyValueModeActive()
                && resolvedRequest.legacyValueMode() == com.betai.api.dto.ValueMode.STRONG_VALUE_ONLY) {
            warnings.add("Value mode STRONG_VALUE_ONLY excludes selections below the STRONG_VALUE rating.");
        }
        if (candidateCount == 0) {
            warnings.add("No stored prediction selections matched the requested markets and model version. Scraping and model generation are ingestion-time jobs, not form-submit actions.");
        }
        if (qualifiedSelectionsFound == 0 && candidateCount > 0) {
            warnings.add("No stored prediction selections qualified after applying strategy probability, confidence, calibration, sample, data quality, and value requirements.");
        }
        if (qualifiedSelectionsFound > 0 && qualifiedSelectionsFound < resolvedRequest.minimumSelections()) {
            warnings.add("Only " + qualifiedSelectionsFound + " selections qualified; requested at least "
                    + resolvedRequest.minimumSelections() + " per batch.");
        }
        long unratedCandidates = candidates.stream()
                .filter(selection -> selection.getConfidenceBand() == null
                        || selection.getConfidenceBand() == PredictionConfidenceBand.UNRATED)
                .count();
        long lowConfidenceCandidates = candidates.stream()
                .filter(selection -> selection.getConfidenceBand() == PredictionConfidenceBand.LOW)
                .count();
        long unpricedCandidates = candidates.stream()
                .filter(selection -> selection.getValueRating() == null
                        || selection.getValueRating() == ValueRating.NO_ODDS)
                .count();
        long positiveValueCandidates = candidates.stream()
                .filter(selection -> selection.getExpectedValue() != null
                        && selection.getExpectedValue().signum() > 0)
                .count();
        long negativeValueCandidates = candidates.stream()
                .filter(selection -> selection.getValueRating() == ValueRating.NEGATIVE_VALUE)
                .count();
        if (unratedCandidates > 0) {
            warnings.add(unratedCandidates + " candidate selections are UNRATED because no adequate settled model-quality sample is available yet.");
        }
        if (lowConfidenceCandidates > 0) {
            warnings.add(lowConfidenceCandidates + " candidate selections have LOW confidence after calibration checks.");
        }
        if (unpricedCandidates > 0) {
            warnings.add(unpricedCandidates + " candidate selections have no imported odds, so value metrics are unavailable for those selections.");
        }
        if (positiveValueCandidates > 0) {
            warnings.add(positiveValueCandidates + " candidate selections have positive expected value against imported decimal odds.");
        }
        if (negativeValueCandidates > 0) {
            warnings.add(negativeValueCandidates + " candidate selections are negative value against imported decimal odds despite their model probability.");
        }
        batches.stream()
                .filter(batch -> batch.warningMessage() != null && !batch.warningMessage().isBlank())
                .map(batch -> "Batch " + batch.batchNumber() + ": " + batch.warningMessage())
                .forEach(warnings::add);
        if (batchCount < resolvedRequest.numberOfBatches()) {
            warnings.add("Only " + batchCount + " valid batches could be built from unconflicted stored selections.");
        }
        if (selectionsReturned < requestedMinimumSelections) {
            warnings.add("Returned " + selectionsReturned + " selections out of the requested minimum "
                    + requestedMinimumSelections + " because requirements were not weakened to fill the request.");
        }
        return List.copyOf(warnings);
    }

    private PredictionResponseStatus responseStatus(
            ResolvedPredictionRequest resolvedRequest,
            int qualifiedSelectionsFound,
            List<com.betai.api.dto.PredictionBatchResponse> batches
    ) {
        if (qualifiedSelectionsFound < resolvedRequest.minimumSelections() || batches.isEmpty()) {
            return PredictionResponseStatus.INSUFFICIENT_QUALIFIED_SELECTIONS;
        }
        boolean partial = batches.size() < resolvedRequest.numberOfBatches()
                || batches.stream().anyMatch(batch -> batch.status() != PredictionResponseStatus.COMPLETE);
        return partial ? PredictionResponseStatus.PARTIAL : PredictionResponseStatus.COMPLETE;
    }

    private String responseWarningMessage(PredictionResponseStatus status, List<String> warnings) {
        if (status == PredictionResponseStatus.COMPLETE) {
            return null;
        }
        return warnings.isEmpty() ? "Prediction response is incomplete for the requested range." : warnings.getLast();
    }

    private List<String> leaguesWithFullRequestedHistory(
            List<com.betai.domain.prediction.PredictionSelection> candidates,
            int requestedSeasonCount
    ) {
        return candidates.stream()
                .filter(selection -> selection.getActualSeasonCountUsed() != null)
                .filter(selection -> selection.getActualSeasonCountUsed() >= requestedSeasonCount)
                .filter(selection -> !Boolean.TRUE.equals(selection.getFallbackApplied()))
                .map(selection -> selection.getMatch().getLeague().getCode().name())
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> leaguesUsingFallbackHistory(
            List<com.betai.domain.prediction.PredictionSelection> candidates,
            int requestedSeasonCount
    ) {
        return candidates.stream()
                .filter(selection -> Boolean.TRUE.equals(selection.getFallbackApplied())
                        || (selection.getActualSeasonCountUsed() != null
                        && selection.getActualSeasonCountUsed() < requestedSeasonCount))
                .map(selection -> selection.getMatch().getLeague().getCode().name())
                .distinct()
                .sorted()
                .toList();
    }

    private record HistoricalReplayContext(String modelVersion, List<String> warnings) {

        static HistoricalReplayContext notUsed(String modelVersion) {
            return new HistoricalReplayContext(modelVersion, List.of());
        }
    }
}
