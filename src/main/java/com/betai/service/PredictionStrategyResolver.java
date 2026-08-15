package com.betai.service;

import com.betai.api.dto.PredictionRequest;
import com.betai.api.dto.RankingMode;
import com.betai.api.dto.SelectionStrategy;
import com.betai.api.dto.ValueMode;
import com.betai.config.PredictionProperties;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.exception.InvalidRequestException;

import java.math.BigDecimal;
import java.util.List;

final class PredictionStrategyResolver {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal DEFAULT_BATCH_DIFFERENCE = new BigDecimal("0.40");
    private static final BigDecimal DEFAULT_CORRELATION_TOLERANCE = new BigDecimal("0.50");
    private static final BigDecimal DEFAULT_LOWER_RISK_ALLOCATION = new BigDecimal("0.60");
    private static final BigDecimal DEFAULT_BALANCED_ALLOCATION = new BigDecimal("0.30");
    private static final BigDecimal DEFAULT_LONGSHOT_ALLOCATION = new BigDecimal("0.10");

    private PredictionStrategyResolver() {
    }

    static ResolvedPredictionRequest resolve(PredictionRequest request, PredictionProperties properties) {
        validateCommonRequest(request, properties);

        SelectionStrategy strategy = resolveStrategy(request);
        int minimumSelections = firstNonNull(request.minimumSelections(), request.selectionsPerBatch(), 1);
        int maximumSelections = firstNonNull(request.maximumSelections(), request.selectionsPerBatch(), minimumSelections);
        int numberOfBatches = firstNonNull(request.numberOfBatches(), request.batchCount(), 1);
        BigDecimal correlationTolerance = defaulted(request.correlationTolerance(), DEFAULT_CORRELATION_TOLERANCE);

        validateSelectionRange(minimumSelections, maximumSelections, numberOfBatches, request, properties);

        boolean allowMultipleSelectionsFromSameMatch = defaulted(request.allowMultipleSelectionsFromSameMatch(), false);
        int strategyMaximumPerMatch = defaultSettings(strategy, request).maximumSelectionsPerMatch();
        int maximumSelectionsPerMatch = firstNonNull(request.maximumSelectionsPerMatch(), strategyMaximumPerMatch);
        boolean requireMultipleLeagues = defaulted(request.requireMultipleLeagues(), true);
        int minimumDistinctLeagues = firstNonNull(request.minimumDistinctLeagues(), requireMultipleLeagues ? 2 : 1);
        boolean requireDifferentMarketGroups = defaulted(request.requireDifferentMarketGroups(), false);
        boolean avoidCorrelatedSelections = defaulted(request.avoidCorrelatedSelections(), true);
        boolean allowRepeatSelectionsAcrossBatches = defaulted(request.allowRepeatSelectionsAcrossBatches(), false);
        BigDecimal minimumBatchDifferencePercentage = defaulted(
                request.minimumBatchDifferencePercentage(),
                DEFAULT_BATCH_DIFFERENCE
        );

        if (!allowMultipleSelectionsFromSameMatch && maximumSelectionsPerMatch > 1) {
            maximumSelectionsPerMatch = 1;
        }
        if (minimumDistinctLeagues > request.leagueCodes().size()) {
            throw new InvalidRequestException("minimumDistinctLeagues cannot exceed the number of selected leagues.");
        }
        if (minimumBatchDifferencePercentage.compareTo(ZERO) < 0
                || minimumBatchDifferencePercentage.compareTo(ONE) > 0) {
            throw new InvalidRequestException("minimumBatchDifferencePercentage must be between 0 and 1.");
        }
        if (correlationTolerance.compareTo(ZERO) < 0 || correlationTolerance.compareTo(ONE) > 0) {
            throw new InvalidRequestException("correlationTolerance must be between 0 and 1.");
        }

        boolean legacyValueModeActive = request.strategy() == null
                && request.valueMode() != null
                && request.valueMode() != ValueMode.ALL;

        return new ResolvedPredictionRequest(
                resolveSport(request.sport()),
                strategy,
                minimumSelections,
                maximumSelections,
                numberOfBatches,
                resolveSettings(request, strategy),
                legacyValueModeActive,
                defaulted(request.valueMode(), ValueMode.ALL),
                allowMultipleSelectionsFromSameMatch,
                maximumSelectionsPerMatch,
                request.maximumSelectionsPerTeam(),
                request.maximumSelectionsPerLeague(),
                requireMultipleLeagues,
                minimumDistinctLeagues,
                requireDifferentMarketGroups,
                avoidCorrelatedSelections,
                allowRepeatSelectionsAcrossBatches,
                minimumBatchDifferencePercentage,
                correlationTolerance
        );
    }

    private static void validateCommonRequest(PredictionRequest request, PredictionProperties properties) {
        validateProbability("minimumModelProbability", request.minimumModelProbability());
        validateProbability("maximumModelProbability", request.maximumModelProbability());
        validateProbability("minimumDataQuality", request.minimumDataQuality());
        validateProbability("minimumProbabilityEdge", request.minimumProbabilityEdge());

        if (request.minimumModelProbability() != null
                && request.maximumModelProbability() != null
                && request.maximumModelProbability().compareTo(request.minimumModelProbability()) < 0) {
            throw new InvalidRequestException("maximumModelProbability must be greater than or equal to minimumModelProbability.");
        }
        if (request.minimumDecimalOdds() != null
                && request.maximumDecimalOdds() != null
                && request.maximumDecimalOdds().compareTo(request.minimumDecimalOdds()) < 0) {
            throw new InvalidRequestException("maximumDecimalOdds must be greater than or equal to minimumDecimalOdds.");
        }
        if (request.minimumExpectedValue() != null && request.minimumExpectedValue().signum() < 0) {
            throw new InvalidRequestException("minimumExpectedValue cannot be negative.");
        }
        if (request.maximumSelectionsPerMatch() != null && request.maximumSelectionsPerMatch() < 1) {
            throw new InvalidRequestException("maximumSelectionsPerMatch must be at least 1.");
        }
        if (request.numberOfBatches() != null && request.numberOfBatches() > properties.maxBatches()) {
            throw new InvalidRequestException("numberOfBatches cannot exceed " + properties.maxBatches() + ".");
        }
        if (request.batchCount() != null && request.batchCount() > properties.maxBatches()) {
            throw new InvalidRequestException("batchCount cannot exceed " + properties.maxBatches() + ".");
        }
        String sport = resolveSport(request.sport());
        if (!"FOOTBALL".equals(sport)) {
            throw new InvalidRequestException("sport must be FOOTBALL for the current prediction form.");
        }
    }

    private static void validateSelectionRange(
            int minimumSelections,
            int maximumSelections,
            int numberOfBatches,
            PredictionRequest request,
            PredictionProperties properties
    ) {
        if (minimumSelections <= 0) {
            throw new InvalidRequestException("minimumSelections must be greater than zero.");
        }
        if (maximumSelections < minimumSelections) {
            throw new InvalidRequestException("maximumSelections must be greater than or equal to minimumSelections.");
        }
        if (numberOfBatches <= 0) {
            throw new InvalidRequestException("numberOfBatches must be greater than zero.");
        }
        if (maximumSelections > properties.maxSelectionsPerBatch()) {
            throw new InvalidRequestException("maximumSelections cannot exceed "
                    + properties.maxSelectionsPerBatch() + ".");
        }
        if (request.selectionsPerBatch() != null
                && request.selectionsPerBatch() > properties.maxSelectionsPerBatch()) {
            throw new InvalidRequestException("selectionsPerBatch cannot exceed "
                    + properties.maxSelectionsPerBatch() + ".");
        }
    }

    private static List<PredictionStrategySettings> resolveSettings(PredictionRequest request, SelectionStrategy strategy) {
        if (strategy == SelectionStrategy.MIXED_PORTFOLIO) {
            BigDecimal lowerRiskAllocation = defaulted(
                    request.lowerRiskAllocationPercentage(),
                    DEFAULT_LOWER_RISK_ALLOCATION
            );
            BigDecimal balancedAllocation = defaulted(
                    request.balancedAllocationPercentage(),
                    DEFAULT_BALANCED_ALLOCATION
            );
            BigDecimal longshotAllocation = defaulted(
                    request.longshotAllocationPercentage(),
                    DEFAULT_LONGSHOT_ALLOCATION
            );
            BigDecimal allocationTotal = lowerRiskAllocation.add(balancedAllocation).add(longshotAllocation);
            if (allocationTotal.compareTo(ZERO) <= 0) {
                throw new InvalidRequestException("Mixed portfolio allocation percentages must sum to more than zero.");
            }
            return List.of(
                    withOverrides(request, defaultSettings(SelectionStrategy.LOWER_RISK, request), lowerRiskAllocation.divide(allocationTotal, 8, java.math.RoundingMode.HALF_UP)),
                    withOverrides(request, defaultSettings(SelectionStrategy.BALANCED, request), balancedAllocation.divide(allocationTotal, 8, java.math.RoundingMode.HALF_UP)),
                    withOverrides(request, defaultSettings(SelectionStrategy.LONGSHOT, request), longshotAllocation.divide(allocationTotal, 8, java.math.RoundingMode.HALF_UP))
            );
        }
        return List.of(withOverrides(request, defaultSettings(strategy, request), ONE));
    }

    private static PredictionStrategySettings defaultSettings(SelectionStrategy strategy, PredictionRequest request) {
        return switch (strategy) {
            case LOWER_RISK -> new PredictionStrategySettings(
                    SelectionStrategy.LOWER_RISK,
                    RankingMode.MODEL_PROBABILITY,
                    new BigDecimal("0.75"),
                    new BigDecimal("0.92"),
                    null,
                    null,
                    PredictionConfidenceBand.HIGH,
                    new BigDecimal("0.80"),
                    50,
                    null,
                    null,
                    true,
                    false,
                    false,
                    1,
                    ONE,
                    null
            );
            case BALANCED, MIXED_PORTFOLIO -> new PredictionStrategySettings(
                    SelectionStrategy.BALANCED,
                    RankingMode.COMPOSITE_SCORE,
                    new BigDecimal("0.60"),
                    new BigDecimal("0.78"),
                    null,
                    null,
                    PredictionConfidenceBand.MEDIUM,
                    new BigDecimal("0.70"),
                    30,
                    null,
                    null,
                    true,
                    false,
                    false,
                    1,
                    ONE,
                    null
            );
            case VALUE -> new PredictionStrategySettings(
                    SelectionStrategy.VALUE,
                    RankingMode.EXPECTED_VALUE,
                    new BigDecimal("0.45"),
                    new BigDecimal("0.75"),
                    null,
                    null,
                    PredictionConfidenceBand.MEDIUM,
                    new BigDecimal("0.70"),
                    30,
                    new BigDecimal("0.05"),
                    new BigDecimal("0.03"),
                    true,
                    false,
                    true,
                    firstNonNull(request.maximumSelectionsPerMatch(), 1),
                    ONE,
                    null
            );
            case LONGSHOT -> new PredictionStrategySettings(
                    SelectionStrategy.LONGSHOT,
                    RankingMode.PROBABILITY_EDGE,
                    new BigDecimal("0.20"),
                    new BigDecimal("0.50"),
                    new BigDecimal("2.50"),
                    null,
                    PredictionConfidenceBand.MEDIUM,
                    new BigDecimal("0.75"),
                    30,
                    new BigDecimal("0.08"),
                    null,
                    true,
                    false,
                    true,
                    firstNonNull(request.maximumSelectionsPerMatch(), 1),
                    ONE,
                    null
            );
            case HIGH_CONFIDENCE -> new PredictionStrategySettings(
                    SelectionStrategy.HIGH_CONFIDENCE,
                    RankingMode.COMPOSITE_SCORE,
                    new BigDecimal("0.80"),
                    new BigDecimal("0.95"),
                    null,
                    null,
                    PredictionConfidenceBand.VERY_HIGH,
                    new BigDecimal("0.90"),
                    100,
                    null,
                    null,
                    true,
                    false,
                    false,
                    1,
                    ONE,
                    null
            );
            case CUSTOM -> new PredictionStrategySettings(
                    SelectionStrategy.CUSTOM,
                    defaulted(request.rankingMode(), RankingMode.COMPOSITE_SCORE),
                    ZERO,
                    ONE,
                    null,
                    null,
                    null,
                    ZERO,
                    0,
                    null,
                    null,
                    false,
                    false,
                    customUsesOdds(request),
                    firstNonNull(request.maximumSelectionsPerMatch(), 1),
                    ONE,
                    null
            );
        };
    }

    private static PredictionStrategySettings withOverrides(
            PredictionRequest request,
            PredictionStrategySettings defaults,
            BigDecimal allocationPercentage
    ) {
        boolean strategyAllowsOdds = defaults.strategy() == SelectionStrategy.VALUE
                || defaults.strategy() == SelectionStrategy.LONGSHOT
                || defaults.strategy() == SelectionStrategy.CUSTOM;
        RankingMode resolvedRankingMode = defaults.strategy() == SelectionStrategy.CUSTOM
                ? defaulted(request.rankingMode(), defaults.rankingMode())
                : defaults.rankingMode();
        BigDecimal minimumDecimalOdds = strategyAllowsOdds
                ? defaulted(request.minimumDecimalOdds(), defaults.minimumDecimalOdds())
                : null;
        BigDecimal maximumDecimalOdds = strategyAllowsOdds
                ? defaulted(request.maximumDecimalOdds(), defaults.maximumDecimalOdds())
                : null;
        BigDecimal minimumExpectedValue = strategyAllowsOdds
                ? defaulted(request.minimumExpectedValue(), defaults.minimumExpectedValue())
                : null;
        BigDecimal minimumProbabilityEdge = strategyAllowsOdds
                ? defaulted(request.minimumProbabilityEdge(), defaults.minimumProbabilityEdge())
                : null;
        boolean usesOdds = strategyAllowsOdds
                && (defaults.usesOddsForQualification()
                || minimumDecimalOdds != null
                || maximumDecimalOdds != null
                || minimumExpectedValue != null
                || minimumProbabilityEdge != null
                || resolvedRankingMode == RankingMode.EXPECTED_VALUE
                || resolvedRankingMode == RankingMode.PROBABILITY_EDGE);

        return new PredictionStrategySettings(
                defaults.strategy(),
                resolvedRankingMode,
                defaulted(request.minimumModelProbability(), defaults.minimumModelProbability()),
                defaulted(request.maximumModelProbability(), defaults.maximumModelProbability()),
                minimumDecimalOdds,
                maximumDecimalOdds,
                defaulted(request.minimumConfidence(), defaults.minimumConfidence()),
                defaulted(request.minimumDataQuality(), defaults.minimumDataQuality()),
                firstNonNull(request.minimumHistoricalSample(), defaults.minimumHistoricalSample()),
                minimumExpectedValue,
                minimumProbabilityEdge,
                defaulted(request.requireCalibration(), defaults.requireCalibration()),
                defaulted(request.allowUnratedPredictions(), defaults.allowUnratedPredictions()),
                usesOdds,
                firstNonNull(request.maximumSelectionsPerMatch(), defaults.maximumSelectionsPerMatch()),
                allocationPercentage,
                request.marketProbabilityRanges()
        );
    }

    private static boolean customUsesOdds(PredictionRequest request) {
        return request.minimumDecimalOdds() != null
                || request.maximumDecimalOdds() != null
                || request.minimumExpectedValue() != null
                || request.minimumProbabilityEdge() != null
                || request.rankingMode() == RankingMode.EXPECTED_VALUE
                || request.rankingMode() == RankingMode.PROBABILITY_EDGE;
    }

    private static SelectionStrategy resolveStrategy(PredictionRequest request) {
        if (request.strategy() != null) {
            return request.strategy();
        }
        if (request.valueMode() == ValueMode.POSITIVE_VALUE_ONLY
                || request.valueMode() == ValueMode.STRONG_VALUE_ONLY) {
            return SelectionStrategy.VALUE;
        }
        return SelectionStrategy.BALANCED;
    }

    private static void validateProbability(String field, BigDecimal value) {
        if (value == null) {
            return;
        }
        if (value.compareTo(ZERO) < 0 || value.compareTo(ONE) > 0) {
            throw new InvalidRequestException(field + " must be between 0 and 1.");
        }
    }

    private static String resolveSport(String sport) {
        if (sport == null || sport.isBlank()) {
            return "FOOTBALL";
        }
        return sport.trim().toUpperCase();
    }

    private static <T> T defaulted(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static int firstNonNull(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static int firstNonNull(Integer first, Integer second, int defaultValue) {
        if (first != null) {
            return first;
        }
        return second == null ? defaultValue : second;
    }
}
