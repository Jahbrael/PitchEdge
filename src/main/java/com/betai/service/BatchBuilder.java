package com.betai.service;

import com.betai.api.dto.BatchRiskMetricsResponse;
import com.betai.api.dto.PredictionBatchResponse;
import com.betai.api.dto.PredictionResponseStatus;
import com.betai.api.dto.PredictionSelectionResponse;
import com.betai.api.dto.RiskBand;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.market.MarketDirection;
import com.betai.domain.market.MarketTeamScope;
import com.betai.domain.market.MarketType;
import com.betai.domain.prediction.PredictionSelection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class BatchBuilder {

    private static final MathContext PROBABILITY_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal SOFT_CORRELATION_BASE_PENALTY = new BigDecimal("0.20");

    public List<PredictionBatchResponse> build(List<PredictionSelection> candidates, int batchCount, int selectionsPerBatch) {
        List<PredictionCandidate> rankedCandidates = candidates.stream()
                .map(selection -> new PredictionCandidate(
                        selection,
                        com.betai.api.dto.SelectionStrategy.BALANCED,
                        com.betai.api.dto.RankingMode.MODEL_PROBABILITY,
                        selection.getProbability(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        selection.getProbability(),
                        null,
                        "Qualified by legacy batch builder."
                ))
                .toList();
        return build(rankedCandidates, new PredictionBatchBuildRequest(
                com.betai.api.dto.SelectionStrategy.BALANCED,
                batchCount,
                selectionsPerBatch,
                selectionsPerBatch,
                rankedCandidates.size(),
                false,
                1,
                null,
                null,
                false,
                1,
                false,
                true,
                false,
                BigDecimal.ZERO,
                new BigDecimal("0.50")
        ));
    }

    public List<PredictionBatchResponse> build(
            List<PredictionCandidate> candidates,
            PredictionBatchBuildRequest request
    ) {
        List<PredictionCandidate> sortedCandidates = candidates.stream()
                .sorted(candidateComparator())
                .toList();

        List<PredictionBatchResponse> batches = new ArrayList<>();
        Set<UUID> globallyUsedSelections = new HashSet<>();
        boolean enforceNoRepeatForDiversity = request.minimumBatchDifferencePercentage().compareTo(BigDecimal.ZERO) > 0;

        for (int batchNumber = 1; batchNumber <= request.numberOfBatches(); batchNumber++) {
            List<PredictionCandidate> batchSelections = new ArrayList<>();
            List<String> correlationWarnings = new ArrayList<>();

            while (batchSelections.size() < request.maximumSelections()) {
                PredictionCandidate next = nextBestCandidate(
                        sortedCandidates,
                        batchSelections,
                        globallyUsedSelections,
                        request,
                        correlationWarnings,
                        enforceNoRepeatForDiversity
                );
                if (next == null) {
                    break;
                }
                batchSelections.add(next);
            }

            if (batchSelections.isEmpty()) {
                break;
            }

            PredictionBatchResponse batch = toBatchResponse(batchNumber, batchSelections, request, correlationWarnings);
            if (!diverseEnough(batch, batches, request)) {
                break;
            }

            if (!request.allowRepeatSelectionsAcrossBatches() || enforceNoRepeatForDiversity) {
                batchSelections.forEach(candidate -> globallyUsedSelections.add(candidate.selection().getId()));
            }
            batches.add(batch);
        }

        return List.copyOf(batches);
    }

    private PredictionCandidate nextBestCandidate(
            List<PredictionCandidate> sortedCandidates,
            List<PredictionCandidate> currentBatch,
            Set<UUID> globallyUsedSelections,
            PredictionBatchBuildRequest request,
            List<String> correlationWarnings,
            boolean enforceNoRepeatForDiversity
    ) {
        PredictionCandidate bestCandidate = null;
        BigDecimal bestAdjustedScore = null;
        String bestWarning = null;

        for (PredictionCandidate candidate : sortedCandidates) {
            UUID candidateId = candidate.selection().getId();
            if ((!request.allowRepeatSelectionsAcrossBatches() || enforceNoRepeatForDiversity)
                    && globallyUsedSelections.contains(candidateId)) {
                continue;
            }
            if (currentBatch.stream().anyMatch(existing -> existing.selection().getId().equals(candidateId))) {
                continue;
            }

            CandidateFit fit = evaluateFit(currentBatch, candidate, request);
            if (!fit.allowed()) {
                continue;
            }
            BigDecimal adjustedScore = candidate.rankingScore().subtract(fit.penalty());
            if (needsLeagueDiversity(currentBatch, candidate, request)) {
                adjustedScore = adjustedScore.add(new BigDecimal("0.050000"));
            }
            if (bestCandidate == null
                    || adjustedScore.compareTo(bestAdjustedScore) > 0
                    || (adjustedScore.compareTo(bestAdjustedScore) == 0
                    && candidateComparator().compare(candidate, bestCandidate) < 0)) {
                bestCandidate = candidate;
                bestAdjustedScore = adjustedScore;
                bestWarning = fit.warning();
            }
        }

        if (bestWarning != null && !correlationWarnings.contains(bestWarning)) {
            correlationWarnings.add(bestWarning);
        }
        return bestCandidate;
    }

    private CandidateFit evaluateFit(
            List<PredictionCandidate> currentBatch,
            PredictionCandidate candidate,
            PredictionBatchBuildRequest request
    ) {
        PredictionSelection candidateSelection = candidate.selection();
        UUID candidateMatchId = candidateSelection.getMatch().getId();
        MarketCode candidateMarket = candidateSelection.getMarketDefinition().getCode();

        if (countMatchSelections(currentBatch, candidateMatchId) >= request.maximumSelectionsPerMatch()) {
            return CandidateFit.rejected();
        }
        if (!request.allowMultipleSelectionsFromSameMatch()
                && countMatchSelections(currentBatch, candidateMatchId) > 0) {
            return CandidateFit.rejected();
        }
        if (request.maximumSelectionsPerLeague() != null
                && countLeagueSelections(currentBatch, candidateSelection) >= request.maximumSelectionsPerLeague()) {
            return CandidateFit.rejected();
        }
        if (request.maximumSelectionsPerTeam() != null
                && exceedsTeamLimit(currentBatch, candidateSelection, request.maximumSelectionsPerTeam())) {
            return CandidateFit.rejected();
        }
        if (request.requireDifferentMarketGroups()
                && currentBatch.stream().anyMatch(existing -> sameMarketGroup(existing.selection(), candidateSelection))) {
            return CandidateFit.rejected();
        }

        BigDecimal penalty = BigDecimal.ZERO;
        String warning = null;
        for (PredictionCandidate existingCandidate : currentBatch) {
            PredictionSelection existing = existingCandidate.selection();
            boolean sameMatch = existing.getMatch().getId().equals(candidateMatchId);
            if (sameMatch && hardMarketConflict(existing, candidateSelection)) {
                return CandidateFit.rejected();
            }
            if (sameMatch
                    && existing.getCorrelationGroupKey() != null
                    && existing.getCorrelationGroupKey().equals(candidateSelection.getCorrelationGroupKey())) {
                return CandidateFit.rejected();
            }
            if (request.avoidCorrelatedSelections() && softCorrelation(existing, candidateSelection)) {
                if (request.correlationTolerance().compareTo(new BigDecimal("0.25")) <= 0) {
                    return CandidateFit.rejected();
                }
                BigDecimal softPenalty = SOFT_CORRELATION_BASE_PENALTY
                        .multiply(BigDecimal.ONE.subtract(request.correlationTolerance()));
                penalty = penalty.add(softPenalty);
                warning = "Soft correlation allowed with ranking penalty: "
                        + existing.getMarketDefinition().getCode() + " and " + candidateMarket
                        + " for " + candidateSelection.getMatch().getHomeTeam().getCanonicalName()
                        + " vs " + candidateSelection.getMatch().getAwayTeam().getCanonicalName() + ".";
            }
        }

        return new CandidateFit(true, penalty, warning);
    }

    private boolean hardMarketConflict(PredictionSelection leftSelection, PredictionSelection rightSelection) {
        MarketCode left = leftSelection.getMarketDefinition().getCode();
        MarketCode right = rightSelection.getMarketDefinition().getCode();
        if (isMutuallyExclusiveResultMarket(left, right)) {
            return true;
        }
        if (isDoubleChanceConflict(left, right)) {
            return true;
        }
        if ((left == MarketCode.HOME_DRAW_NO_BET && right == MarketCode.AWAY_DRAW_NO_BET)
                || (left == MarketCode.AWAY_DRAW_NO_BET && right == MarketCode.HOME_DRAW_NO_BET)) {
            return true;
        }
        if ((left == MarketCode.BTTS_YES && right == MarketCode.BTTS_NO)
                || (left == MarketCode.BTTS_NO && right == MarketCode.BTTS_YES)) {
            return true;
        }
        if ((left == MarketCode.RED_CARD_YES && right == MarketCode.RED_CARD_NO)
                || (left == MarketCode.RED_CARD_NO && right == MarketCode.RED_CARD_YES)) {
            return true;
        }
        return incompatibleOppositeThresholds(leftSelection, rightSelection);
    }

    private boolean isMutuallyExclusiveResultMarket(MarketCode left, MarketCode right) {
        Set<MarketCode> resultMarkets = Set.of(MarketCode.HOME_WIN, MarketCode.DRAW, MarketCode.AWAY_WIN);
        return resultMarkets.contains(left) && resultMarkets.contains(right) && left != right;
    }

    private boolean isDoubleChanceConflict(MarketCode left, MarketCode right) {
        return (left == MarketCode.HOME_OR_DRAW && right == MarketCode.AWAY_WIN)
                || (left == MarketCode.AWAY_WIN && right == MarketCode.HOME_OR_DRAW)
                || (left == MarketCode.AWAY_OR_DRAW && right == MarketCode.HOME_WIN)
                || (left == MarketCode.HOME_WIN && right == MarketCode.AWAY_OR_DRAW)
                || (left == MarketCode.HOME_OR_AWAY && right == MarketCode.DRAW)
                || (left == MarketCode.DRAW && right == MarketCode.HOME_OR_AWAY);
    }

    private boolean incompatibleOppositeThresholds(PredictionSelection leftSelection, PredictionSelection rightSelection) {
        MarketDefinition left = leftSelection.getMarketDefinition();
        MarketDefinition right = rightSelection.getMarketDefinition();
        if (!sameThresholdFamily(left, right)
                || left.getThreshold() == null
                || right.getThreshold() == null
                || !isOppositeDirection(left.getDirection(), right.getDirection())) {
            return false;
        }
        MarketDefinition over = left.getDirection() == MarketDirection.OVER ? left : right;
        MarketDefinition under = left.getDirection() == MarketDirection.UNDER ? left : right;
        if (over.getDirection() != MarketDirection.OVER || under.getDirection() != MarketDirection.UNDER) {
            return left.getThreshold().compareTo(right.getThreshold()) == 0;
        }
        return over.getThreshold().compareTo(under.getThreshold()) >= 0;
    }

    private boolean sameThresholdFamily(MarketDefinition left, MarketDefinition right) {
        return left.getMarketType() == right.getMarketType()
                && left.getPeriod() == right.getPeriod()
                && left.getTeamScope() == right.getTeamScope();
    }

    private boolean isOppositeDirection(MarketDirection left, MarketDirection right) {
        return (left == MarketDirection.OVER && right == MarketDirection.UNDER)
                || (left == MarketDirection.UNDER && right == MarketDirection.OVER)
                || (left == MarketDirection.YES && right == MarketDirection.NO)
                || (left == MarketDirection.NO && right == MarketDirection.YES);
    }

    private PredictionBatchResponse toBatchResponse(
            int batchNumber,
            List<PredictionCandidate> candidates,
            PredictionBatchBuildRequest request,
            List<String> correlationWarnings
    ) {
        List<PredictionSelection> selections = candidates.stream()
                .map(PredictionCandidate::selection)
                .toList();
        List<PredictionSelectionResponse> selectionResponses = candidates.stream()
                .map(candidate -> PredictionSelectionResponse.from(
                        candidate.selection(),
                        candidate.rankingScore(),
                        candidate.reason(),
                        candidate.dataQualityScore(),
                        candidate.calibratedProbability(),
                        candidate.calibrationStatus()
                ))
                .toList();
        BatchRiskMetricsResponse risk = calculateRisk(selections);
        List<String> warnings = new ArrayList<>(correlationWarnings);
        if (request.requireMultipleLeagues() && distinctLeagueCount(selections) < request.minimumDistinctLeagues()) {
            warnings.add("Minimum distinct league requirement could not be fully satisfied for this batch.");
        }
        PredictionResponseStatus status = batchStatus(selections.size(), request, warnings);
        String warningMessage = warningMessage(selections.size(), request, warnings);
        return new PredictionBatchResponse(
                batchNumber,
                request.strategy(),
                request.minimumSelections(),
                request.maximumSelections(),
                request.qualifiedSelectionsFound(),
                selections.size(),
                risk.averageIndividualProbability(),
                risk.jointProbability(),
                risk.riskBand(),
                distinctLeagueCount(selections),
                distinctMarketGroupCount(selections),
                List.copyOf(warnings),
                status,
                warningMessage,
                selections.size(),
                risk,
                selectionResponses
        );
    }

    private PredictionResponseStatus batchStatus(
            int selectionCount,
            PredictionBatchBuildRequest request,
            List<String> warnings
    ) {
        if (selectionCount < request.minimumSelections()) {
            return PredictionResponseStatus.INSUFFICIENT_QUALIFIED_SELECTIONS;
        }
        if (!warnings.isEmpty()) {
            return PredictionResponseStatus.PARTIAL;
        }
        return PredictionResponseStatus.COMPLETE;
    }

    private String warningMessage(int selectionCount, PredictionBatchBuildRequest request, List<String> warnings) {
        if (selectionCount < request.minimumSelections()) {
            return "Returned " + selectionCount + " selections because only "
                    + request.qualifiedSelectionsFound() + " qualified selections were available after configured requirements.";
        }
        if (!warnings.isEmpty()) {
            return String.join(" ", warnings);
        }
        return null;
    }

    private BatchRiskMetricsResponse calculateRisk(List<PredictionSelection> selections) {
        BigDecimal jointProbability = BigDecimal.ONE;
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = BigDecimal.ONE;
        BigDecimal max = BigDecimal.ZERO;
        BigDecimal expectedValueSum = BigDecimal.ZERO;
        BigDecimal minExpectedValue = null;
        BigDecimal maxExpectedValue = null;
        BigDecimal aggregateDecimalOdds = BigDecimal.ONE;
        int pricedSelectionCount = 0;
        int positiveValueSelectionCount = 0;

        for (PredictionSelection selection : selections) {
            BigDecimal probability = selection.getProbability();
            jointProbability = jointProbability.multiply(probability, PROBABILITY_CONTEXT);
            sum = sum.add(probability, PROBABILITY_CONTEXT);
            min = min.min(probability);
            max = max.max(probability);
            if (selection.getExpectedValue() != null && selection.getBestDecimalOdds() != null) {
                pricedSelectionCount++;
                expectedValueSum = expectedValueSum.add(selection.getExpectedValue(), PROBABILITY_CONTEXT);
                minExpectedValue = minExpectedValue == null
                        ? selection.getExpectedValue()
                        : minExpectedValue.min(selection.getExpectedValue());
                maxExpectedValue = maxExpectedValue == null
                        ? selection.getExpectedValue()
                        : maxExpectedValue.max(selection.getExpectedValue());
                aggregateDecimalOdds = aggregateDecimalOdds.multiply(selection.getBestDecimalOdds(), PROBABILITY_CONTEXT);
                if (selection.getExpectedValue().compareTo(BigDecimal.ZERO) > 0) {
                    positiveValueSelectionCount++;
                }
            }
        }

        BigDecimal average = sum.divide(BigDecimal.valueOf(selections.size()), 6, RoundingMode.HALF_UP);
        BigDecimal scaledJoint = jointProbability.setScale(6, RoundingMode.HALF_UP);
        BigDecimal averageExpectedValue = pricedSelectionCount == 0
                ? null
                : expectedValueSum.divide(BigDecimal.valueOf(pricedSelectionCount), 6, RoundingMode.HALF_UP);
        boolean allSelectionsPriced = pricedSelectionCount == selections.size();
        BigDecimal scaledAggregateOdds = allSelectionsPriced
                ? aggregateDecimalOdds.setScale(4, RoundingMode.HALF_UP)
                : null;
        BigDecimal accumulatorExpectedValue = allSelectionsPriced
                ? scaledJoint.multiply(aggregateDecimalOdds, PROBABILITY_CONTEXT)
                        .subtract(BigDecimal.ONE, PROBABILITY_CONTEXT)
                        .setScale(6, RoundingMode.HALF_UP)
                : null;

        return new BatchRiskMetricsResponse(
                scaledJoint,
                average,
                min.setScale(6, RoundingMode.HALF_UP),
                max.setScale(6, RoundingMode.HALF_UP),
                pricedSelectionCount,
                positiveValueSelectionCount,
                averageExpectedValue,
                minExpectedValue == null ? null : minExpectedValue.setScale(6, RoundingMode.HALF_UP),
                maxExpectedValue == null ? null : maxExpectedValue.setScale(6, RoundingMode.HALF_UP),
                scaledAggregateOdds,
                accumulatorExpectedValue,
                riskBand(scaledJoint),
                varianceWarning(selections.size(), average, scaledJoint, pricedSelectionCount, accumulatorExpectedValue)
        );
    }

    private RiskBand riskBand(BigDecimal jointProbability) {
        if (jointProbability.compareTo(new BigDecimal("0.500000")) >= 0) {
            return RiskBand.LOW;
        }
        if (jointProbability.compareTo(new BigDecimal("0.250000")) >= 0) {
            return RiskBand.MODERATE;
        }
        if (jointProbability.compareTo(new BigDecimal("0.100000")) >= 0) {
            return RiskBand.HIGH;
        }
        return RiskBand.EXTREME;
    }

    private String varianceWarning(
            int selectionCount,
            BigDecimal averageProbability,
            BigDecimal jointProbability,
            int pricedSelectionCount,
            BigDecimal accumulatorExpectedValue
    ) {
        BigDecimal averagePercent = averageProbability.multiply(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP);
        BigDecimal jointPercent = jointProbability.multiply(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP);
        String warning = "Under independent-event accumulator math, " + selectionCount
                + " selections averaging " + averagePercent + "% produce a full-batch probability of "
                + jointPercent + "%.";
        if (pricedSelectionCount < selectionCount) {
            return warning + " Odds/value metrics are partial because only " + pricedSelectionCount
                    + " selections have imported odds.";
        }
        return warning + " Full-batch EV from available odds is "
                + accumulatorExpectedValue.setScale(6, RoundingMode.HALF_UP) + " per unit stake.";
    }

    private Comparator<PredictionCandidate> candidateComparator() {
        return Comparator.comparing(PredictionCandidate::rankingScore, Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.selection().getProbability(), Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.selection().getMatch().getKickoffAt());
    }

    private int countMatchSelections(List<PredictionCandidate> currentBatch, UUID matchId) {
        return (int) currentBatch.stream()
                .filter(candidate -> candidate.selection().getMatch().getId().equals(matchId))
                .count();
    }

    private int countLeagueSelections(List<PredictionCandidate> currentBatch, PredictionSelection candidateSelection) {
        return (int) currentBatch.stream()
                .filter(candidate -> candidate.selection().getMatch().getLeague().getCode()
                        == candidateSelection.getMatch().getLeague().getCode())
                .count();
    }

    private boolean exceedsTeamLimit(
            List<PredictionCandidate> currentBatch,
            PredictionSelection candidateSelection,
            int maximumSelectionsPerTeam
    ) {
        UUID candidateHome = candidateSelection.getMatch().getHomeTeam().getId();
        UUID candidateAway = candidateSelection.getMatch().getAwayTeam().getId();
        return countTeamSelections(currentBatch, candidateHome) >= maximumSelectionsPerTeam
                || countTeamSelections(currentBatch, candidateAway) >= maximumSelectionsPerTeam;
    }

    private int countTeamSelections(List<PredictionCandidate> currentBatch, UUID teamId) {
        return (int) currentBatch.stream()
                .filter(candidate -> candidate.selection().getMatch().getHomeTeam().getId().equals(teamId)
                        || candidate.selection().getMatch().getAwayTeam().getId().equals(teamId))
                .count();
    }

    private boolean sameMarketGroup(PredictionSelection left, PredictionSelection right) {
        return left.getMarketDefinition().getMarketType() == right.getMarketDefinition().getMarketType();
    }

    private boolean softCorrelation(PredictionSelection left, PredictionSelection right) {
        if (!left.getMatch().getId().equals(right.getMatch().getId())) {
            return false;
        }
        MarketType leftType = left.getMarketDefinition().getMarketType();
        MarketType rightType = right.getMarketDefinition().getMarketType();
        MarketCode leftCode = left.getMarketDefinition().getCode();
        MarketCode rightCode = right.getMarketDefinition().getCode();
        return samePositiveResultFamily(leftCode, rightCode)
                || relatedThresholdMarkets(left.getMarketDefinition(), right.getMarketDefinition())
                || resultWithTeamSupportMarket(left.getMarketDefinition(), right.getMarketDefinition())
                || (isResultFamily(leftType) && rightType == MarketType.TOTAL_GOALS)
                || (isResultFamily(rightType) && leftType == MarketType.TOTAL_GOALS)
                || (leftType == MarketType.TOTAL_GOALS && rightType == MarketType.BOTH_TEAMS_TO_SCORE)
                || (rightType == MarketType.TOTAL_GOALS && leftType == MarketType.BOTH_TEAMS_TO_SCORE)
                || (leftType == MarketType.CLEAN_SHEET && rightCode == MarketCode.BTTS_NO)
                || (rightType == MarketType.CLEAN_SHEET && leftCode == MarketCode.BTTS_NO);
    }

    private boolean isResultFamily(MarketType marketType) {
        return marketType == MarketType.MATCH_RESULT
                || marketType == MarketType.DOUBLE_CHANCE
                || marketType == MarketType.DRAW_NO_BET;
    }

    private boolean samePositiveResultFamily(MarketCode left, MarketCode right) {
        boolean homeFamily = Set.of(MarketCode.HOME_WIN, MarketCode.HOME_OR_DRAW, MarketCode.HOME_DRAW_NO_BET).contains(left)
                && Set.of(MarketCode.HOME_WIN, MarketCode.HOME_OR_DRAW, MarketCode.HOME_DRAW_NO_BET).contains(right);
        boolean awayFamily = Set.of(MarketCode.AWAY_WIN, MarketCode.AWAY_OR_DRAW, MarketCode.AWAY_DRAW_NO_BET).contains(left)
                && Set.of(MarketCode.AWAY_WIN, MarketCode.AWAY_OR_DRAW, MarketCode.AWAY_DRAW_NO_BET).contains(right);
        return left != right && (homeFamily || awayFamily);
    }

    private boolean relatedThresholdMarkets(MarketDefinition left, MarketDefinition right) {
        if (left.getMarketType() != right.getMarketType()
                || left.getPeriod() != right.getPeriod()
                || left.getTeamScope() != right.getTeamScope()
                || left.getThreshold() == null
                || right.getThreshold() == null) {
            return false;
        }
        return left.getMarketType() == MarketType.TOTAL_GOALS
                || left.getMarketType() == MarketType.TEAM_TOTAL_GOALS
                || left.getMarketType() == MarketType.TOTAL_CORNERS
                || left.getMarketType() == MarketType.TEAM_CORNERS
                || left.getMarketType() == MarketType.TOTAL_YELLOW_CARDS;
    }

    private boolean resultWithTeamSupportMarket(MarketDefinition left, MarketDefinition right) {
        return homeResultWithSupport(left, right) || homeResultWithSupport(right, left)
                || awayResultWithSupport(left, right) || awayResultWithSupport(right, left);
    }

    private boolean homeResultWithSupport(MarketDefinition result, MarketDefinition support) {
        return Set.of(MarketCode.HOME_WIN, MarketCode.HOME_OR_DRAW, MarketCode.HOME_DRAW_NO_BET).contains(result.getCode())
                && (isTeamGoalsOver(support, MarketTeamScope.HOME_TEAM)
                || support.getCode() == MarketCode.HOME_TEAM_CLEAN_SHEET);
    }

    private boolean awayResultWithSupport(MarketDefinition result, MarketDefinition support) {
        return Set.of(MarketCode.AWAY_WIN, MarketCode.AWAY_OR_DRAW, MarketCode.AWAY_DRAW_NO_BET).contains(result.getCode())
                && (isTeamGoalsOver(support, MarketTeamScope.AWAY_TEAM)
                || support.getCode() == MarketCode.AWAY_TEAM_CLEAN_SHEET);
    }

    private boolean isTeamGoalsOver(MarketDefinition market, MarketTeamScope teamScope) {
        return market.getMarketType() == MarketType.TEAM_TOTAL_GOALS
                && market.getTeamScope() == teamScope
                && market.getDirection() == MarketDirection.OVER;
    }

    private boolean needsLeagueDiversity(
            List<PredictionCandidate> currentBatch,
            PredictionCandidate candidate,
            PredictionBatchBuildRequest request
    ) {
        if (!request.requireMultipleLeagues() || currentBatch.isEmpty()) {
            return false;
        }
        long distinctLeagues = currentBatch.stream()
                .map(existing -> existing.selection().getMatch().getLeague().getCode())
                .distinct()
                .count();
        boolean candidateAddsLeague = currentBatch.stream()
                .noneMatch(existing -> existing.selection().getMatch().getLeague().getCode()
                        == candidate.selection().getMatch().getLeague().getCode());
        return distinctLeagues < request.minimumDistinctLeagues() && candidateAddsLeague;
    }

    private int distinctLeagueCount(List<PredictionSelection> selections) {
        return (int) selections.stream()
                .map(selection -> selection.getMatch().getLeague().getCode())
                .distinct()
                .count();
    }

    private int distinctMarketGroupCount(List<PredictionSelection> selections) {
        return (int) selections.stream()
                .map(selection -> selection.getMarketDefinition().getMarketType())
                .distinct()
                .count();
    }

    private boolean diverseEnough(
            PredictionBatchResponse candidateBatch,
            List<PredictionBatchResponse> existingBatches,
            PredictionBatchBuildRequest request
    ) {
        if (existingBatches.isEmpty() || request.minimumBatchDifferencePercentage().compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        Set<UUID> candidateIds = selectionIds(candidateBatch);
        for (PredictionBatchResponse existingBatch : existingBatches) {
            Set<UUID> existingIds = selectionIds(existingBatch);
            int common = (int) candidateIds.stream().filter(existingIds::contains).count();
            int comparisonSize = Math.max(candidateIds.size(), existingIds.size());
            BigDecimal difference = BigDecimal.valueOf(comparisonSize - common)
                    .divide(BigDecimal.valueOf(comparisonSize), 8, RoundingMode.HALF_UP);
            if (difference.compareTo(request.minimumBatchDifferencePercentage()) < 0) {
                return false;
            }
        }
        return true;
    }

    private Set<UUID> selectionIds(PredictionBatchResponse batch) {
        Set<UUID> ids = new HashSet<>();
        batch.selections().forEach(selection -> ids.add(selection.selectionId()));
        return ids;
    }

    private record CandidateFit(boolean allowed, BigDecimal penalty, String warning) {

        static CandidateFit rejected() {
            return new CandidateFit(false, BigDecimal.ZERO, null);
        }
    }
}
