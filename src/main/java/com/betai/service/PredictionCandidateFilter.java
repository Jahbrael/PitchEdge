package com.betai.service;

import com.betai.api.dto.RankingMode;
import com.betai.api.dto.SelectionStrategy;
import com.betai.api.dto.ValueMode;
import com.betai.domain.odds.ValueRating;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.domain.quality.ModelQualitySnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class PredictionCandidateFilter {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    PredictionCandidateFilterResult filterAndRank(
            List<PredictionSelection> selections,
            ResolvedPredictionRequest request
    ) {
        if (request.strategy() == SelectionStrategy.MIXED_PORTFOLIO) {
            return filterMixedPortfolio(selections, request);
        }

        List<PredictionCandidate> candidates = selections.stream()
                .map(selection -> toCandidate(selection, request.strategySettings().getFirst(), request))
                .flatMap(List::stream)
                .sorted(candidateComparator())
                .toList();
        return new PredictionCandidateFilterResult(candidates, candidates.size());
    }

    private PredictionCandidateFilterResult filterMixedPortfolio(
            List<PredictionSelection> selections,
            ResolvedPredictionRequest request
    ) {
        Map<UUID, PredictionCandidate> selected = new LinkedHashMap<>();
        List<PredictionCandidate> allQualified = new ArrayList<>();

        for (PredictionStrategySettings settings : request.strategySettings()) {
            List<PredictionCandidate> segmentCandidates = selections.stream()
                    .map(selection -> toCandidate(selection, settings, request))
                    .flatMap(List::stream)
                    .sorted(candidateComparator())
                    .toList();
            allQualified.addAll(segmentCandidates);

            int segmentLimit = Math.max(
                    1,
                    settings.allocationPercentage()
                            .multiply(BigDecimal.valueOf(request.maximumSelections()))
                            .setScale(0, RoundingMode.CEILING)
                            .intValue()
            );
            segmentCandidates.stream()
                    .limit(segmentLimit)
                    .forEach(candidate -> selected.merge(
                            candidate.selection().getId(),
                            candidate,
                            (existing, replacement) -> replacement.rankingScore().compareTo(existing.rankingScore()) > 0
                                    ? replacement
                                    : existing
                    ));
        }

        allQualified.stream()
                .sorted(candidateComparator())
                .forEach(candidate -> {
                    if (selected.size() < request.maximumSelections()) {
                        selected.putIfAbsent(candidate.selection().getId(), candidate);
                    }
                });

        List<PredictionCandidate> candidates = selected.values().stream()
                .sorted(candidateComparator())
                .toList();
        long distinctQualified = allQualified.stream()
                .map(candidate -> candidate.selection().getId())
                .distinct()
                .count();
        return new PredictionCandidateFilterResult(candidates, Math.toIntExact(distinctQualified));
    }

    private List<PredictionCandidate> toCandidate(
            PredictionSelection selection,
            PredictionStrategySettings settings,
            ResolvedPredictionRequest request
    ) {
        CandidateMetrics metrics = metrics(selection);
        if (!eligible(selection, settings, request, metrics)) {
            return List.of();
        }

        BigDecimal rankingScore = score(selection, settings, metrics);
        String reason = reason(selection, settings);
        return List.of(new PredictionCandidate(
                selection,
                settings.strategy(),
                settings.rankingMode(),
                rankingScore,
                metrics.confidenceScore(),
                metrics.dataQualityScore(),
                metrics.calibrationQualityScore(),
                metrics.marketReliabilityScore(),
                metrics.calibratedProbability(),
                metrics.calibrationStatus(),
                reason
        ));
    }

    private boolean eligible(
            PredictionSelection selection,
            PredictionStrategySettings settings,
            ResolvedPredictionRequest request,
            CandidateMetrics metrics
    ) {
        BigDecimal tunedModelProbability = selection.getProbability();
        if (tunedModelProbability == null) {
            return false;
        }

        BigDecimal minProb = settings.minimumModelProbability();
        BigDecimal maxProb = settings.maximumModelProbability();

        if (settings.marketProbabilityRanges() != null && selection.getMarketDefinition() != null
                && settings.marketProbabilityRanges().containsKey(selection.getMarketDefinition().getCode())) {
            com.betai.api.dto.ProbabilityRange customRange = settings.marketProbabilityRanges().get(selection.getMarketDefinition().getCode());
            if (customRange.min() != null) minProb = customRange.min();
            if (customRange.max() != null) maxProb = customRange.max();
        }

        if (minProb != null && tunedModelProbability.compareTo(minProb) < 0) {
            return false;
        }
        if (maxProb != null && tunedModelProbability.compareTo(maxProb) > 0) {
            return false;
        }
        if (!meetsConfidence(selection.getConfidenceBand(), settings.minimumConfidence(), settings.allowUnratedPredictions())) {
            return false;
        }
        if (settings.minimumDataQuality() != null
                && metrics.dataQualityScore().compareTo(settings.minimumDataQuality()) < 0) {
            return false;
        }
        if (settings.requireCalibration()
                && !settings.allowUnratedPredictions()
                && !"CALIBRATED".equals(metrics.calibrationStatus())) {
            return false;
        }
        if (settings.minimumHistoricalSample() > 0
                && metrics.historicalSampleSize() < settings.minimumHistoricalSample()) {
            return false;
        }
        if (settings.usesOddsForQualification() && !meetsOddsRequirements(selection, settings, request)) {
            return false;
        }
        return meetsLegacyValueMode(selection, request);
    }

    private boolean meetsOddsRequirements(
            PredictionSelection selection,
            PredictionStrategySettings settings,
            ResolvedPredictionRequest request
    ) {
        if (settings.minimumDecimalOdds() != null
                && (selection.getBestDecimalOdds() == null
                || selection.getBestDecimalOdds().compareTo(settings.minimumDecimalOdds()) < 0)) {
            return false;
        }
        if (settings.maximumDecimalOdds() != null
                && (selection.getBestDecimalOdds() == null
                || selection.getBestDecimalOdds().compareTo(settings.maximumDecimalOdds()) > 0)) {
            return false;
        }
        if (settings.minimumExpectedValue() != null
                && (selection.getExpectedValue() == null
                || selection.getExpectedValue().compareTo(settings.minimumExpectedValue()) < 0)) {
            return false;
        }
        if (settings.minimumProbabilityEdge() != null
                && (selection.getValueEdge() == null
                || selection.getValueEdge().compareTo(settings.minimumProbabilityEdge()) < 0)) {
            return false;
        }
        if (request.legacyValueModeActive() && request.legacyValueMode() == ValueMode.STRONG_VALUE_ONLY) {
            return selection.getValueRating() == ValueRating.STRONG_VALUE;
        }
        return true;
    }

    private boolean meetsLegacyValueMode(PredictionSelection selection, ResolvedPredictionRequest request) {
        if (!request.legacyValueModeActive()) {
            return true;
        }
        if (request.legacyValueMode() == ValueMode.POSITIVE_VALUE_ONLY) {
            return selection.getExpectedValue() != null && selection.getExpectedValue().signum() > 0;
        }
        if (request.legacyValueMode() == ValueMode.STRONG_VALUE_ONLY) {
            return selection.getValueRating() == ValueRating.STRONG_VALUE;
        }
        return true;
    }

    private CandidateMetrics metrics(PredictionSelection selection) {
        ModelQualitySnapshot quality = selection.getModelQualitySnapshot();
        BigDecimal confidenceScore = confidenceScore(selection.getConfidenceBand());
        BigDecimal calibratedProbability = inferCalibratedProbability(selection);
        if (quality == null) {
            return new CandidateMetrics(
                    confidenceScore,
                    ZERO,
                    ZERO,
                    ZERO,
                    calibratedProbability,
                    0,
                    "MISSING"
            );
        }

        BigDecimal calibrationQualityScore = oneMinus(quality.getCalibrationError());
        BigDecimal brierQualityScore = oneMinus(quality.getBrierScore());
        BigDecimal sampleQualityScore = BigDecimal.valueOf(Math.min(quality.getSampleSize(), 100))
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        BigDecimal dataQualityScore = calibrationQualityScore.multiply(new BigDecimal("0.45"))
                .add(brierQualityScore.multiply(new BigDecimal("0.35")))
                .add(sampleQualityScore.multiply(new BigDecimal("0.20")))
                .multiply(historicalDepthScore(selection));
        int marketMinimumSampleSize = quality.getMarketDefinition() == null
                ? selection.getMarketDefinition().getMinimumSampleSize()
                : quality.getMarketDefinition().getMinimumSampleSize();
        int marketReliabilityDenominator = Math.max(marketMinimumSampleSize * 10, 1);
        BigDecimal marketReliabilityScore = BigDecimal.valueOf(Math.min(quality.getSampleSize(), marketReliabilityDenominator))
                .divide(BigDecimal.valueOf(marketReliabilityDenominator), 8, RoundingMode.HALF_UP);

        return new CandidateMetrics(
                confidenceScore,
                clamp(dataQualityScore),
                calibrationQualityScore,
                clamp(marketReliabilityScore),
                calibratedProbability,
                quality.getSampleSize(),
                calibrationStatus(selection, quality)
        );
    }

    private BigDecimal historicalDepthScore(PredictionSelection selection) {
        Integer requested = selection.getRequestedSeasonCount();
        Integer actual = selection.getActualSeasonCountUsed();
        if (requested == null || requested <= 0 || actual == null || actual >= requested) {
            return ONE;
        }
        if (actual <= 0) {
            return ZERO;
        }
        return BigDecimal.valueOf(actual)
                .divide(BigDecimal.valueOf(requested), 8, RoundingMode.HALF_UP);
    }

    private String calibrationStatus(PredictionSelection selection, ModelQualitySnapshot quality) {
        if (quality == null) {
            return "MISSING";
        }
        if (selection.getConfidenceBand() == null
                || selection.getConfidenceBand() == PredictionConfidenceBand.UNRATED
                || quality.getConfidenceBand() == PredictionConfidenceBand.UNRATED) {
            return "UNRATED";
        }
        if (quality.getSampleSize() <= 0) {
            return "INSUFFICIENT_SAMPLE";
        }
        if (quality.getCalibrationError() == null || quality.getBrierScore() == null) {
            return "INCOMPLETE";
        }
        return "CALIBRATED";
    }

    private BigDecimal score(PredictionSelection selection, PredictionStrategySettings settings, CandidateMetrics metrics) {
        RankingMode mode = settings.rankingMode();
        if (mode == RankingMode.COMPOSITE_SCORE || mode == RankingMode.DIVERSIFIED_SCORE) {
            return compositeScore(selection, settings, metrics);
        }
        return switch (mode) {
            case MODEL_PROBABILITY -> scaled(selection.getProbability());
            case CONFIDENCE -> scaled(metrics.confidenceScore());
            case DATA_QUALITY -> scaled(metrics.dataQualityScore());
            case EXPECTED_VALUE -> scaled(normalizePositive(selection.getExpectedValue(), new BigDecimal("0.50")));
            case PROBABILITY_EDGE -> scaled(normalizePositive(selection.getValueEdge(), new BigDecimal("0.25")));
            case DIVERSIFIED_SCORE, COMPOSITE_SCORE -> compositeScore(selection, settings, metrics);
        };
    }

    private BigDecimal compositeScore(
            PredictionSelection selection,
            PredictionStrategySettings settings,
            CandidateMetrics metrics
    ) {
        BigDecimal modelProbability = nullSafe(selection.getProbability());
        BigDecimal expectedValueScore = normalizePositive(selection.getExpectedValue(), new BigDecimal("0.50"));
        BigDecimal probabilityEdgeScore = normalizePositive(selection.getValueEdge(), new BigDecimal("0.25"));

        BigDecimal score = switch (settings.strategy()) {
            case VALUE -> expectedValueScore.multiply(new BigDecimal("0.30"))
                    .add(probabilityEdgeScore.multiply(new BigDecimal("0.25")))
                    .add(metrics.confidenceScore().multiply(new BigDecimal("0.20")))
                    .add(metrics.dataQualityScore().multiply(new BigDecimal("0.15")))
                    .add(metrics.marketReliabilityScore().multiply(new BigDecimal("0.10")));
            case LONGSHOT -> probabilityEdgeScore.multiply(new BigDecimal("0.40"))
                    .add(expectedValueScore.multiply(new BigDecimal("0.35")))
                    .add(metrics.confidenceScore().multiply(new BigDecimal("0.15")))
                    .add(metrics.dataQualityScore().multiply(new BigDecimal("0.10")));
            case HIGH_CONFIDENCE -> modelProbability.multiply(new BigDecimal("0.40"))
                    .add(metrics.confidenceScore().multiply(new BigDecimal("0.25")))
                    .add(metrics.calibrationQualityScore().multiply(new BigDecimal("0.20")))
                    .add(metrics.dataQualityScore().multiply(new BigDecimal("0.15")));
            case BALANCED, MIXED_PORTFOLIO, CUSTOM -> modelProbability.multiply(new BigDecimal("0.40"))
                    .add(metrics.confidenceScore().multiply(new BigDecimal("0.20")))
                    .add(metrics.dataQualityScore().multiply(new BigDecimal("0.20")))
                    .add(metrics.calibrationQualityScore().multiply(new BigDecimal("0.10")))
                    .add(metrics.marketReliabilityScore().multiply(new BigDecimal("0.10")));
            case LOWER_RISK -> modelProbability;
        };
        return scaled(clamp(score));
    }

    private boolean meetsConfidence(
            PredictionConfidenceBand actual,
            PredictionConfidenceBand minimum,
            boolean allowUnratedPredictions
    ) {
        if (actual == null || actual == PredictionConfidenceBand.UNRATED) {
            return allowUnratedPredictions;
        }
        if (minimum == null) {
            return true;
        }
        return confidenceRank(actual) >= confidenceRank(minimum);
    }

    private BigDecimal confidenceScore(PredictionConfidenceBand band) {
        if (band == null) {
            return ZERO;
        }
        return switch (band) {
            case VERY_HIGH -> ONE;
            case HIGH -> new BigDecimal("0.85");
            case MEDIUM -> new BigDecimal("0.65");
            case LOW -> new BigDecimal("0.35");
            case UNRATED -> ZERO;
        };
    }

    private int confidenceRank(PredictionConfidenceBand band) {
        return switch (band) {
            case UNRATED -> 0;
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case VERY_HIGH -> 4;
        };
    }

    private BigDecimal inferCalibratedProbability(PredictionSelection selection) {
        if (selection.getProbability() == null) {
            return null;
        }
        if (selection.getTuningAdjustment() == null) {
            return selection.getProbability();
        }
        return clamp(selection.getProbability().subtract(selection.getTuningAdjustment()));
    }

    private BigDecimal oneMinus(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return clamp(ONE.subtract(value.abs()));
    }

    private BigDecimal normalizePositive(BigDecimal value, BigDecimal expectedMaximum) {
        if (value == null || value.signum() <= 0) {
            return ZERO;
        }
        return clamp(value.divide(expectedMaximum, 8, RoundingMode.HALF_UP));
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? ZERO : clamp(value);
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        if (value.compareTo(ZERO) < 0) {
            return ZERO;
        }
        if (value.compareTo(ONE) > 0) {
            return ONE;
        }
        return value;
    }

    private BigDecimal scaled(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private String reason(PredictionSelection selection, PredictionStrategySettings settings) {
        String probability = selection.getProbability() == null ? "n/a" : selection.getProbability().toPlainString();
        if (settings.usesOddsForQualification()) {
            return "Qualified for " + settings.strategy() + " using tuned model probability "
                    + probability + ", expected value, probability edge, and odds requirements.";
        }
        return "Qualified for " + settings.strategy() + " using tuned model probability " + probability + ".";
    }

    private Comparator<PredictionCandidate> candidateComparator() {
        return Comparator.comparing(PredictionCandidate::rankingScore, Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.selection().getProbability(), Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.selection().getMatch().getKickoffAt());
    }

    private record CandidateMetrics(
            BigDecimal confidenceScore,
            BigDecimal dataQualityScore,
            BigDecimal calibrationQualityScore,
            BigDecimal marketReliabilityScore,
            BigDecimal calibratedProbability,
            int historicalSampleSize,
            String calibrationStatus
    ) {
    }
}
