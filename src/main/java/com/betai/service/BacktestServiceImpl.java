package com.betai.service;

import com.betai.api.dto.BacktestMarketSummaryResponse;
import com.betai.api.dto.BacktestRequest;
import com.betai.api.dto.BacktestResponse;
import com.betai.config.PredictionProperties;
import com.betai.domain.backtest.BacktestMarketSummary;
import com.betai.domain.backtest.BacktestRun;
import com.betai.domain.backtest.BacktestStatus;
import com.betai.domain.backtest.TuningRecommendation;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.domain.tuning.ModelTuningProfile;
import com.betai.exception.InvalidRequestException;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.repository.BacktestMarketSummaryRepository;
import com.betai.repository.BacktestRunRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.ModelTuningProfileRepository;
import com.betai.repository.PredictionSelectionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BacktestServiceImpl implements BacktestService {

    private static final int DEFAULT_MINIMUM_SAMPLE_SIZE = 30;
    private static final MathContext MATH_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal MAX_RECOMMENDED_ADJUSTMENT = new BigDecimal("0.100000");
    private static final BigDecimal MAX_APPLIED_ADJUSTMENT = new BigDecimal("0.050000");
    private static final BigDecimal TUNING_SMOOTHING = new BigDecimal("0.500000");
    private static final BigDecimal RECOMMENDATION_THRESHOLD = new BigDecimal("0.030000");

    private final PredictionProperties predictionProperties;
    private final LeagueRepository leagueRepository;
    private final PredictionSelectionRepository predictionSelectionRepository;
    private final BacktestRunRepository backtestRunRepository;
    private final BacktestMarketSummaryRepository backtestMarketSummaryRepository;
    private final ModelTuningProfileRepository modelTuningProfileRepository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public BacktestResponse runBacktest(BacktestRequest request) {
        OffsetDateTime startedAt = OffsetDateTime.now(clock);
        LocalDate backtestDate = request.backtestDate() == null ? LocalDate.now(clock) : request.backtestDate();
        LocalDate matchDateTo = request.matchDateTo() == null ? backtestDate.minusDays(1) : request.matchDateTo();
        LocalDate matchDateFrom = request.matchDateFrom() == null ? matchDateTo.minusDays(89) : request.matchDateFrom();
        int minimumSampleSize = request.minimumSampleSize() == null ? DEFAULT_MINIMUM_SAMPLE_SIZE : request.minimumSampleSize();
        String modelVersion = resolveModelVersion(request.modelVersion());
        List<League> leagues = resolveLeagues(request.leagueCodes());
        Set<LeagueCode> leagueCodes = leagues.stream()
                .map(League::getCode)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(LeagueCode.class)));
        validateDateRange(matchDateFrom, matchDateTo);

        FlushModeType previousFlushMode = entityManager.getFlushMode();
        entityManager.setFlushMode(FlushModeType.COMMIT);
        try {
            BacktestRun run = backtestRunRepository.save(new BacktestRun()
                    .setLeagueCodes(leagueCodesCsv(leagueCodes))
                    .setModelVersion(modelVersion)
                    .setBacktestDate(backtestDate)
                    .setMatchDateFrom(matchDateFrom)
                    .setMatchDateTo(matchDateTo)
                    .setMinimumSampleSize(minimumSampleSize)
                    .setStartedAt(startedAt));

            try {
                List<PredictionSelection> selections = predictionSelectionRepository.findSettledSelectionsForBacktest(
                        leagueCodes,
                        matchDateFrom,
                        matchDateTo,
                        modelVersion
                );
                if (selections.isEmpty()) {
                    run.finish(OffsetDateTime.now(clock), BacktestStatus.SKIPPED, "No settled predictions matched the backtest filters.");
                    BacktestRun saved = backtestRunRepository.save(run);
                    return response(saved, List.of());
                }

                BacktestAccumulator total = new BacktestAccumulator(null, null, TuningSegment.GLOBAL);
                Map<LeagueMarketKey, BacktestAccumulator> byLeagueMarket = new LinkedHashMap<>();
                Map<LeagueMarketSegmentKey, BacktestAccumulator> byLeagueMarketSegment = new LinkedHashMap<>();
                for (PredictionSelection selection : selections) {
                    total.add(selection);
                    LeagueMarketKey key = new LeagueMarketKey(
                            selection.getMatch().getLeague().getCode(),
                            selection.getMarketDefinition().getCode()
                    );
                    byLeagueMarket.computeIfAbsent(
                            key,
                            ignored -> new BacktestAccumulator(selection.getMatch().getLeague(), selection.getMarketDefinition(), TuningSegment.GLOBAL)
                    ).add(selection);

                    LeagueMarketSegmentKey segmentKey = new LeagueMarketSegmentKey(
                            selection.getMatch().getLeague().getCode(),
                            selection.getMarketDefinition().getCode(),
                            TuningSegment.probabilityBand(selection.getProbability())
                    );
                    byLeagueMarketSegment.computeIfAbsent(
                            segmentKey,
                            ignored -> new BacktestAccumulator(
                                    selection.getMatch().getLeague(),
                                    selection.getMarketDefinition(),
                                    segmentKey.segmentKey()
                            )
                    ).add(selection);
                }

                total.applyToRun(run);
                run.finish(
                        OffsetDateTime.now(clock),
                        BacktestStatus.SUCCESS,
                        "Backtest evaluated " + total.totalCount() + " settled selections across "
                                + byLeagueMarket.size() + " league/market groups."
                );
                BacktestRun savedRun = backtestRunRepository.save(run);

                List<BacktestMarketSummary> summaries = byLeagueMarket.values().stream()
                        .sorted(Comparator
                                .comparing((BacktestAccumulator accumulator) -> accumulator.league.getCode().name())
                                .thenComparing(accumulator -> accumulator.marketDefinition.getCode().name()))
                        .map(accumulator -> accumulator.toSummary(savedRun, minimumSampleSize))
                        .map(backtestMarketSummaryRepository::save)
                        .toList();
                summaries.forEach(summary -> upsertTuningProfile(savedRun, summary, TuningSegment.GLOBAL));
                byLeagueMarketSegment.values().forEach(accumulator ->
                        upsertTuningProfile(savedRun, accumulator.toSummary(savedRun, minimumSampleSize), accumulator.segmentKey)
                );

                return response(savedRun, summaries);
            } catch (Exception exception) {
                run.finish(OffsetDateTime.now(clock), BacktestStatus.FAILED, truncate(exception.getMessage(), 1000));
                return response(backtestRunRepository.save(run), List.of());
            }
        } finally {
            entityManager.setFlushMode(previousFlushMode);
        }
    }

    private BacktestResponse response(BacktestRun run, List<BacktestMarketSummary> summaries) {
        return new BacktestResponse(
                run.getId(),
                run.getStatus().name(),
                run.getModelVersion(),
                run.getBacktestDate(),
                run.getMatchDateFrom(),
                run.getMatchDateTo(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                run.getTotalSelections(),
                run.getTotalWon(),
                run.getTotalLost(),
                run.getTotalVoid(),
                run.getTotalPriced(),
                run.getObservedWinRate(),
                run.getAverageProbability(),
                run.getBrierScore(),
                run.getCalibrationError(),
                run.getAverageExpectedValue(),
                run.getRealizedRoi(),
                run.getSummary(),
                summaries.stream().map(BacktestMarketSummaryResponse::from).toList()
        );
    }

    private String resolveModelVersion(String requestedModelVersion) {
        String modelVersion = StringUtils.hasText(requestedModelVersion)
                ? requestedModelVersion.trim()
                : predictionProperties.defaultModelVersion();
        if (!StringUtils.hasText(modelVersion)) {
            throw new InvalidRequestException("modelVersion is required.");
        }
        if (modelVersion.length() > 80) {
            throw new InvalidRequestException("modelVersion cannot exceed 80 characters.");
        }
        return modelVersion;
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

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException("matchDateFrom must be on or before matchDateTo.");
        }
    }

    private String leagueCodesCsv(Set<LeagueCode> leagueCodes) {
        return leagueCodes.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private BigDecimal safeRate(int numerator, int denominator) {
        if (denominator == 0) {
            return ZERO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal safeAverage(BigDecimal numerator, int denominator) {
        if (denominator == 0) {
            return ZERO;
        }
        return numerator.divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal clampAdjustment(BigDecimal adjustment) {
        if (adjustment.compareTo(MAX_RECOMMENDED_ADJUSTMENT) > 0) {
            return MAX_RECOMMENDED_ADJUSTMENT;
        }
        BigDecimal minimum = MAX_RECOMMENDED_ADJUSTMENT.negate();
        if (adjustment.compareTo(minimum) < 0) {
            return minimum;
        }
        return adjustment.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal appliedAdjustment(BacktestMarketSummary summary) {
        if (summary.getTuningRecommendation() == TuningRecommendation.INSUFFICIENT_SAMPLE
                || summary.getTuningRecommendation() == TuningRecommendation.HOLD) {
            return ZERO;
        }
        BigDecimal smoothed = summary.getRecommendedProbabilityAdjustment().multiply(TUNING_SMOOTHING, MATH_CONTEXT);
        if (smoothed.compareTo(MAX_APPLIED_ADJUSTMENT) > 0) {
            return MAX_APPLIED_ADJUSTMENT.setScale(6, RoundingMode.HALF_UP);
        }
        BigDecimal minimum = MAX_APPLIED_ADJUSTMENT.negate();
        if (smoothed.compareTo(minimum) < 0) {
            return minimum.setScale(6, RoundingMode.HALF_UP);
        }
        return smoothed.setScale(6, RoundingMode.HALF_UP);
    }

    private void upsertTuningProfile(BacktestRun run, BacktestMarketSummary summary, String segmentKey) {
        ModelTuningProfile profile = modelTuningProfileRepository
                .findByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndProfileDateAndSegmentKey(
                        summary.getLeague().getCode(),
                        summary.getMarketDefinition().getCode(),
                        run.getModelVersion(),
                        run.getBacktestDate(),
                        segmentKey
                )
                .orElseGet(ModelTuningProfile::new);
        BigDecimal appliedAdjustment = appliedAdjustment(summary);
        boolean active = summary.getTuningRecommendation() != TuningRecommendation.INSUFFICIENT_SAMPLE;
        profile.setLeague(summary.getLeague())
                .setMarketDefinition(summary.getMarketDefinition())
                .setSourceBacktestRun(run)
                .setModelVersion(run.getModelVersion())
                .setProfileDate(run.getBacktestDate())
                .setSegmentKey(segmentKey)
                .setSampleSize(summary.getSampleSize())
                .setRecommendedProbabilityAdjustment(summary.getRecommendedProbabilityAdjustment())
                .setAppliedProbabilityAdjustment(appliedAdjustment)
                .setTuningRecommendation(summary.getTuningRecommendation())
                .setActive(active)
                .setNote("Auto-generated " + TuningSegment.noteLabel(segmentKey) + " tuning from backtest " + run.getId()
                        + ". Recommended adjustment " + summary.getRecommendedProbabilityAdjustment()
                        + "; applied bounded adjustment " + appliedAdjustment + ".");
        modelTuningProfileRepository.save(profile);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record LeagueMarketKey(LeagueCode leagueCode, MarketCode marketCode) {
    }

    private record LeagueMarketSegmentKey(LeagueCode leagueCode, MarketCode marketCode, String segmentKey) {
    }

    private final class BacktestAccumulator {
        private final League league;
        private final MarketDefinition marketDefinition;
        private final String segmentKey;
        private int won;
        private int lost;
        private int voided;
        private int priced;
        private BigDecimal probabilitySum = BigDecimal.ZERO;
        private BigDecimal brierSum = BigDecimal.ZERO;
        private BigDecimal expectedValueSum = BigDecimal.ZERO;
        private BigDecimal realizedProfit = BigDecimal.ZERO;

        private BacktestAccumulator(League league, MarketDefinition marketDefinition, String segmentKey) {
            this.league = league;
            this.marketDefinition = marketDefinition;
            this.segmentKey = segmentKey;
        }

        private void add(PredictionSelection selection) {
            if (selection.getOutcome() == PredictionOutcome.VOID) {
                voided++;
                return;
            }

            BigDecimal actual = selection.getOutcome() == PredictionOutcome.WON ? ONE : ZERO;
            BigDecimal probability = selection.getProbability();
            BigDecimal error = probability.subtract(actual, MATH_CONTEXT);
            probabilitySum = probabilitySum.add(probability, MATH_CONTEXT);
            brierSum = brierSum.add(error.multiply(error, MATH_CONTEXT), MATH_CONTEXT);

            if (selection.getOutcome() == PredictionOutcome.WON) {
                won++;
            } else {
                lost++;
            }

            if (selection.getBestDecimalOdds() != null) {
                priced++;
                if (selection.getExpectedValue() != null) {
                    expectedValueSum = expectedValueSum.add(selection.getExpectedValue(), MATH_CONTEXT);
                }
                BigDecimal profit = selection.getOutcome() == PredictionOutcome.WON
                        ? selection.getBestDecimalOdds().subtract(BigDecimal.ONE, MATH_CONTEXT)
                        : BigDecimal.ONE.negate();
                realizedProfit = realizedProfit.add(profit, MATH_CONTEXT);
            }
        }

        private int settledCount() {
            return won + lost;
        }

        private int totalCount() {
            return won + lost + voided;
        }

        private BigDecimal observedWinRate() {
            return safeRate(won, settledCount());
        }

        private BigDecimal averageProbability() {
            return safeAverage(probabilitySum, settledCount());
        }

        private BigDecimal brierScore() {
            return safeAverage(brierSum, settledCount());
        }

        private BigDecimal calibrationError() {
            return averageProbability().subtract(observedWinRate()).abs().setScale(6, RoundingMode.HALF_UP);
        }

        private BigDecimal averageExpectedValue() {
            return priced == 0 ? null : expectedValueSum.divide(BigDecimal.valueOf(priced), 6, RoundingMode.HALF_UP);
        }

        private BigDecimal realizedRoi() {
            return priced == 0 ? null : realizedProfit.divide(BigDecimal.valueOf(priced), 6, RoundingMode.HALF_UP);
        }

        private BigDecimal recommendedAdjustment() {
            return clampAdjustment(observedWinRate().subtract(averageProbability()));
        }

        private TuningRecommendation recommendation(int minimumSampleSize) {
            if (settledCount() < minimumSampleSize) {
                return TuningRecommendation.INSUFFICIENT_SAMPLE;
            }
            BigDecimal adjustment = recommendedAdjustment();
            if (adjustment.compareTo(RECOMMENDATION_THRESHOLD) >= 0) {
                return TuningRecommendation.INCREASE_PROBABILITY;
            }
            if (adjustment.compareTo(RECOMMENDATION_THRESHOLD.negate()) <= 0) {
                return TuningRecommendation.DECREASE_PROBABILITY;
            }
            return TuningRecommendation.HOLD;
        }

        private void applyToRun(BacktestRun run) {
            run.setTotalSelections(totalCount())
                    .setTotalWon(won)
                    .setTotalLost(lost)
                    .setTotalVoid(voided)
                    .setTotalPriced(priced)
                    .setObservedWinRate(observedWinRate())
                    .setAverageProbability(averageProbability())
                    .setBrierScore(brierScore())
                    .setCalibrationError(calibrationError())
                    .setAverageExpectedValue(averageExpectedValue())
                    .setRealizedRoi(realizedRoi());
        }

        private BacktestMarketSummary toSummary(BacktestRun run, int minimumSampleSize) {
            return new BacktestMarketSummary()
                    .setBacktestRun(run)
                    .setLeague(league)
                    .setMarketDefinition(marketDefinition)
                    .setSampleSize(settledCount())
                    .setWonCount(won)
                    .setLostCount(lost)
                    .setVoidCount(voided)
                    .setPricedCount(priced)
                    .setObservedWinRate(observedWinRate())
                    .setAverageProbability(averageProbability())
                    .setBrierScore(brierScore())
                    .setCalibrationError(calibrationError())
                    .setAverageExpectedValue(averageExpectedValue())
                    .setRealizedRoi(realizedRoi())
                    .setRecommendedProbabilityAdjustment(recommendedAdjustment())
                    .setTuningRecommendation(recommendation(minimumSampleSize));
        }
    }
}
