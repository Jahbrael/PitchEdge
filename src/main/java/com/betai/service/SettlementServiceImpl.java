package com.betai.service;

import com.betai.api.dto.ModelAccuracyResponse;
import com.betai.api.dto.SettlementRequest;
import com.betai.api.dto.SettlementResponse;
import com.betai.api.dto.SettlementRunResponse;
import com.betai.config.PredictionProperties;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.market.MarketDirection;
import com.betai.domain.market.MarketPeriod;
import com.betai.domain.market.MarketTeamScope;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.domain.settlement.ModelAccuracyDaily;
import com.betai.domain.settlement.SettlementCounters;
import com.betai.domain.settlement.SettlementRun;
import com.betai.domain.settlement.SettlementStatus;
import com.betai.domain.statistics.MatchStatistics;
import com.betai.exception.InvalidRequestException;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.repository.LeagueRepository;
import com.betai.repository.ModelAccuracyDailyRepository;
import com.betai.repository.PredictionSelectionRepository;
import com.betai.repository.SettlementRunRepository;
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);

    private final PredictionProperties predictionProperties;
    private final LeagueRepository leagueRepository;
    private final PredictionSelectionRepository predictionSelectionRepository;
    private final SettlementRunRepository settlementRunRepository;
    private final ModelAccuracyDailyRepository modelAccuracyDailyRepository;
    private final Clock clock;

    @Override
    @Transactional
    public SettlementResponse settlePredictions(SettlementRequest request) {
        OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
        LocalDate settlementDate = request.settlementDate() == null ? LocalDate.now(clock) : request.settlementDate();
        LocalDate matchDateTo = request.matchDateTo() == null ? settlementDate.minusDays(1) : request.matchDateTo();
        LocalDate matchDateFrom = request.matchDateFrom() == null ? matchDateTo : request.matchDateFrom();
        String modelVersion = resolveModelVersion(request.modelVersion());

        validateDateRange(matchDateFrom, matchDateTo);
        List<League> leagues = resolveLeagues(request.leagueCodes());
        List<SettlementRunResponse> runs = new ArrayList<>();

        for (League league : leagues) {
            runs.add(settleLeague(
                    league,
                    modelVersion,
                    settlementDate,
                    matchDateFrom,
                    matchDateTo,
                    request.forceResettle()
            ));
        }

        return new SettlementResponse(UUID.randomUUID(), triggeredAt, List.copyOf(runs));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModelAccuracyResponse> getAccuracy(
            LeagueCode leagueCode,
            String modelVersion,
            LocalDate accuracyDate
    ) {
        String resolvedModelVersion = resolveModelVersion(modelVersion);
        LocalDate resolvedAccuracyDate = accuracyDate == null ? LocalDate.now(clock) : accuracyDate;
        return modelAccuracyDailyRepository
                .findByLeague_CodeAndModelVersionAndAccuracyDateOrderByMarketDefinition_CodeAsc(
                        leagueCode,
                        resolvedModelVersion,
                        resolvedAccuracyDate
                )
                .stream()
                .map(ModelAccuracyResponse::from)
                .toList();
    }

    private SettlementRunResponse settleLeague(
            League league,
            String modelVersion,
            LocalDate settlementDate,
            LocalDate matchDateFrom,
            LocalDate matchDateTo,
            boolean forceResettle
    ) {
        SettlementRun run = settlementRunRepository.save(new SettlementRun()
                .setLeague(league)
                .setModelVersion(modelVersion)
                .setSettlementDate(settlementDate)
                .setMatchDateFrom(matchDateFrom)
                .setMatchDateTo(matchDateTo)
                .setSettlementStatus(SettlementStatus.RUNNING)
                .setStartedAt(OffsetDateTime.now(clock)));

        try {
            List<PredictionSelection> selections = predictionSelectionRepository.findSelectionsForSettlement(
                    league.getCode(),
                    matchDateFrom,
                    matchDateTo,
                    modelVersion,
                    forceResettle
            );
            if (selections.isEmpty()) {
                run.finish(
                        OffsetDateTime.now(clock),
                        SettlementStatus.SKIPPED,
                        SettlementCounters.empty(),
                        "No eligible finished-match predictions matched the requested filters."
                );
                return SettlementRunResponse.from(settlementRunRepository.save(run));
            }

            MutableSettlementCounters counters = new MutableSettlementCounters();
            for (PredictionSelection selection : selections) {
                PredictionOutcome outcome = settleSelection(selection);
                selection.setOutcome(outcome);
                counters.add(outcome);
            }

            predictionSelectionRepository.saveAll(selections);
            refreshAccuracy(league, modelVersion, settlementDate, matchDateTo);

            run.finish(
                    OffsetDateTime.now(clock),
                    SettlementStatus.SUCCESS,
                    counters.toImmutable(),
                    null
            );
            return SettlementRunResponse.from(settlementRunRepository.save(run));
        } catch (Exception exception) {
            run.finish(
                    OffsetDateTime.now(clock),
                    SettlementStatus.FAILED,
                    SettlementCounters.empty(),
                    truncate(exception.getMessage(), 1000)
            );
            return SettlementRunResponse.from(settlementRunRepository.save(run));
        }
    }

    private PredictionOutcome settleSelection(PredictionSelection selection) {
        Integer homeScore = selection.getMatch().getHomeScore();
        Integer awayScore = selection.getMatch().getAwayScore();
        MarketCode marketCode = selection.getMarketDefinition().getCode();
        MarketDefinition marketDefinition = selection.getMarketDefinition();

        if (marketDefinition.getPeriod() != MarketPeriod.FULL_TIME || marketDefinition.isRequiresEventData()) {
            return PredictionOutcome.VOID;
        };

        return switch (marketDefinition.getMarketType()) {
            case MATCH_RESULT -> scorePresent(homeScore, awayScore)
                    ? settleMatchResult(marketCode, homeScore, awayScore)
                    : PredictionOutcome.VOID;
            case DOUBLE_CHANCE -> scorePresent(homeScore, awayScore)
                    ? settleDoubleChance(marketCode, homeScore, awayScore)
                    : PredictionOutcome.VOID;
            case DRAW_NO_BET -> scorePresent(homeScore, awayScore)
                    ? settleDrawNoBet(marketCode, homeScore, awayScore)
                    : PredictionOutcome.VOID;
            case TOTAL_GOALS -> scorePresent(homeScore, awayScore)
                    ? settleThreshold(homeScore + awayScore, marketDefinition.getDirection(), marketDefinition.getThreshold())
                    : PredictionOutcome.VOID;
            case TEAM_TOTAL_GOALS -> scorePresent(homeScore, awayScore)
                    ? settleThreshold(teamScore(marketDefinition.getTeamScope(), homeScore, awayScore), marketDefinition.getDirection(), marketDefinition.getThreshold())
                    : PredictionOutcome.VOID;
            case BOTH_TEAMS_TO_SCORE -> scorePresent(homeScore, awayScore)
                    ? wonOrLost(marketDefinition.getDirection() == MarketDirection.YES
                    ? homeScore > 0 && awayScore > 0
                    : homeScore == 0 || awayScore == 0)
                    : PredictionOutcome.VOID;
            case CLEAN_SHEET -> scorePresent(homeScore, awayScore)
                    ? wonOrLost(marketDefinition.getTeamScope() == MarketTeamScope.HOME_TEAM ? awayScore == 0 : homeScore == 0)
                    : PredictionOutcome.VOID;
            case TOTAL_CORNERS -> settleCorners(selection);
            case TEAM_CORNERS -> settleTeamCorners(selection);
            case TOTAL_YELLOW_CARDS -> settleYellowCards(selection);
            case RED_CARD -> settleRedCard(selection);
            case TEAM_TO_SCORE_FIRST, GOAL_PERIOD, TEAM_TO_WIN_PERIOD -> PredictionOutcome.VOID;
        };
    }

    private PredictionOutcome settleMatchResult(MarketCode marketCode, int homeScore, int awayScore) {
        return switch (marketCode) {
            case HOME_WIN -> wonOrLost(homeScore > awayScore);
            case DRAW -> wonOrLost(homeScore == awayScore);
            case AWAY_WIN -> wonOrLost(awayScore > homeScore);
            default -> PredictionOutcome.VOID;
        };
    }

    private PredictionOutcome settleDoubleChance(MarketCode marketCode, int homeScore, int awayScore) {
        return switch (marketCode) {
            case HOME_OR_DRAW -> wonOrLost(homeScore >= awayScore);
            case AWAY_OR_DRAW -> wonOrLost(awayScore >= homeScore);
            case HOME_OR_AWAY -> wonOrLost(homeScore != awayScore);
            default -> PredictionOutcome.VOID;
        };
    }

    private PredictionOutcome settleDrawNoBet(MarketCode marketCode, int homeScore, int awayScore) {
        if (homeScore == awayScore) {
            return PredictionOutcome.VOID;
        }
        return switch (marketCode) {
            case HOME_DRAW_NO_BET -> wonOrLost(homeScore > awayScore);
            case AWAY_DRAW_NO_BET -> wonOrLost(awayScore > homeScore);
            default -> PredictionOutcome.VOID;
        };
    }

    private PredictionOutcome settleYellowCards(PredictionSelection selection) {
        MatchStatistics stats = selection.getMatch().getStatistics();
        if (stats == null || stats.getHomeYellowCards() == null || stats.getAwayYellowCards() == null) {
            return PredictionOutcome.VOID;
        }
        int total = stats.getHomeYellowCards() + stats.getAwayYellowCards();
        return settleThreshold(total, selection.getMarketDefinition().getDirection(), selection.getMarketDefinition().getThreshold());
    }

    private PredictionOutcome settleRedCard(PredictionSelection selection) {
        MatchStatistics stats = selection.getMatch().getStatistics();
        if (stats == null || stats.getHomeRedCards() == null || stats.getAwayRedCards() == null) {
            return PredictionOutcome.VOID;
        }
        int total = stats.getHomeRedCards() + stats.getAwayRedCards();
        return settleThreshold(total, selection.getMarketDefinition().getDirection(), selection.getMarketDefinition().getThreshold());
    }

    private PredictionOutcome settleCorners(PredictionSelection selection) {
        MatchStatistics stats = selection.getMatch().getStatistics();
        if (stats == null || stats.getHomeCorners() == null || stats.getAwayCorners() == null) {
            return PredictionOutcome.VOID;
        }
        int total = stats.getHomeCorners() + stats.getAwayCorners();
        return settleThreshold(total, selection.getMarketDefinition().getDirection(), selection.getMarketDefinition().getThreshold());
    }

    private PredictionOutcome settleTeamCorners(PredictionSelection selection) {
        MatchStatistics stats = selection.getMatch().getStatistics();
        if (stats == null) {
            return PredictionOutcome.VOID;
        }
        Integer total = selection.getMarketDefinition().getTeamScope() == MarketTeamScope.HOME_TEAM
                ? stats.getHomeCorners()
                : stats.getAwayCorners();
        if (total == null) {
            return PredictionOutcome.VOID;
        }
        return settleThreshold(total, selection.getMarketDefinition().getDirection(), selection.getMarketDefinition().getThreshold());
    }

    private PredictionOutcome settleThreshold(int actual, MarketDirection direction, BigDecimal threshold) {
        if (threshold == null) {
            return PredictionOutcome.VOID;
        }
        int comparison = BigDecimal.valueOf(actual).compareTo(threshold);
        return switch (direction) {
            case OVER, YES -> wonOrLost(comparison > 0);
            case UNDER, NO -> wonOrLost(comparison < 0);
            default -> PredictionOutcome.VOID;
        };
    }

    private int teamScore(MarketTeamScope teamScope, int homeScore, int awayScore) {
        return switch (teamScope) {
            case HOME_TEAM -> homeScore;
            case AWAY_TEAM -> awayScore;
            case MATCH -> homeScore + awayScore;
        };
    }

    private void refreshAccuracy(League league, String modelVersion, LocalDate accuracyDate, LocalDate matchDateTo) {
        List<PredictionSelection> settledSelections = predictionSelectionRepository.findSettledSelectionsForAccuracy(
                league.getCode(),
                matchDateTo,
                modelVersion
        );
        Map<MarketCode, AccuracyAccumulator> byMarket = new EnumMap<>(MarketCode.class);
        for (PredictionSelection selection : settledSelections) {
            MarketCode marketCode = selection.getMarketDefinition().getCode();
            byMarket.computeIfAbsent(
                    marketCode,
                    ignored -> new AccuracyAccumulator(league, selection.getMarketDefinition(), modelVersion, accuracyDate)
            ).add(selection);
        }

        for (AccuracyAccumulator accumulator : byMarket.values()) {
            ModelAccuracyDaily accuracy = modelAccuracyDailyRepository
                    .findByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndAccuracyDate(
                            league.getCode(),
                            accumulator.marketDefinition.getCode(),
                            modelVersion,
                            accuracyDate
                    )
                    .orElseGet(ModelAccuracyDaily::new);
            modelAccuracyDailyRepository.save(accumulator.applyTo(accuracy));
        }
    }

    private boolean scorePresent(Integer homeScore, Integer awayScore) {
        return homeScore != null && awayScore != null;
    }

    private PredictionOutcome wonOrLost(boolean won) {
        return won ? PredictionOutcome.WON : PredictionOutcome.LOST;
    }

    private void validateDateRange(LocalDate matchDateFrom, LocalDate matchDateTo) {
        if (matchDateFrom.isAfter(matchDateTo)) {
            throw new InvalidRequestException("matchDateFrom must be on or before matchDateTo.");
        }
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static final class MutableSettlementCounters {
        private int evaluated;
        private int won;
        private int lost;
        private int voided;

        private void add(PredictionOutcome outcome) {
            evaluated++;
            switch (outcome) {
                case WON -> won++;
                case LOST -> lost++;
                case VOID -> voided++;
                case PENDING -> {
                }
            }
        }

        private SettlementCounters toImmutable() {
            return new SettlementCounters(evaluated, won, lost, voided, 0);
        }
    }

    private static final class AccuracyAccumulator {
        private final League league;
        private final MarketDefinition marketDefinition;
        private final String modelVersion;
        private final LocalDate accuracyDate;
        private int won;
        private int lost;
        private int voided;
        private BigDecimal probabilitySum = BigDecimal.ZERO;
        private BigDecimal brierSum = BigDecimal.ZERO;

        private AccuracyAccumulator(
                League league,
                MarketDefinition marketDefinition,
                String modelVersion,
                LocalDate accuracyDate
        ) {
            this.league = league;
            this.marketDefinition = marketDefinition;
            this.modelVersion = modelVersion;
            this.accuracyDate = accuracyDate;
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
            } else if (selection.getOutcome() == PredictionOutcome.LOST) {
                lost++;
            }
        }

        private ModelAccuracyDaily applyTo(ModelAccuracyDaily accuracy) {
            int settled = won + lost;
            BigDecimal settledCount = BigDecimal.valueOf(settled);
            BigDecimal winRate = settled == 0 ? ZERO : BigDecimal.valueOf(won).divide(settledCount, 6, RoundingMode.HALF_UP);
            BigDecimal averageProbability = settled == 0 ? ZERO : probabilitySum.divide(settledCount, 6, RoundingMode.HALF_UP);
            BigDecimal brierScore = settled == 0 ? ZERO : brierSum.divide(settledCount, 6, RoundingMode.HALF_UP);
            BigDecimal calibrationError = averageProbability.subtract(winRate).abs().setScale(6, RoundingMode.HALF_UP);

            return accuracy.setLeague(league)
                    .setMarketDefinition(marketDefinition)
                    .setModelVersion(modelVersion)
                    .setAccuracyDate(accuracyDate)
                    .setSettledSelections(settled)
                    .setWonCount(won)
                    .setLostCount(lost)
                    .setVoidCount(voided)
                    .setWinRate(winRate)
                    .setAverageProbability(averageProbability)
                    .setBrierScore(brierScore)
                    .setCalibrationError(calibrationError);
        }
    }
}
