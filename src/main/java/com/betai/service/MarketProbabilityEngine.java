package com.betai.service;

import com.betai.domain.feature.LeagueBaseline;
import com.betai.domain.feature.TeamFeatureSnapshot;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDirection;
import com.betai.domain.market.MarketTargetType;
import com.betai.domain.market.MarketTeamScope;
import com.betai.domain.match.Match;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

@Component
public class MarketProbabilityEngine {

    private static final int MAX_SCORELINE_GOALS = 10;
    private static final double DIXON_COLES_RHO = -0.08;

    public PredictionScores score(Match match, TeamFeatureSnapshot home, TeamFeatureSnapshot away, LeagueBaseline baseline) {
        ExpectedProfile expectedProfile = expectedProfile(home, away, baseline);
        ScorelineModel scorelineModel = scorelineModel(expectedProfile);
        Map<MarketCode, BigDecimal> probabilities = new EnumMap<>(MarketCode.class);
        ResultProbabilities strengthProbabilities = resultProbabilities(home, away, baseline, expectedProfile);
        ResultProbabilities blendedResultProbabilities = blendResults(scorelineModel.resultProbabilities(), strengthProbabilities);
        double bttsYes = blend(scorelineModel.btts(), bttsProbability(home, away, baseline, expectedProfile), 0.75);
        double redCardYes = redCardProbability(home, away, baseline);

        for (MarketCode marketCode : MarketCode.values()) {
            if (!marketCode.isEnabled()) {
                continue;
            }
            Double probability = probabilityForMarket(
                    marketCode,
                    scorelineModel,
                    blendedResultProbabilities,
                    bttsYes,
                    redCardYes,
                    expectedProfile
            );
            if (probability != null) {
                probabilities.put(marketCode, probability(probability));
            }
        }

        return new PredictionScores(probabilities, expectedProfile);
    }

    private Double probabilityForMarket(
            MarketCode marketCode,
            ScorelineModel scorelineModel,
            ResultProbabilities resultProbabilities,
            double bttsYes,
            double redCardYes,
            ExpectedProfile expectedProfile
    ) {
        return switch (marketCode.getMarketType()) {
            case MATCH_RESULT -> resultProbability(marketCode, resultProbabilities);
            case DOUBLE_CHANCE -> doubleChanceProbability(marketCode, resultProbabilities);
            case DRAW_NO_BET -> drawNoBetProbability(marketCode, resultProbabilities);
            case TOTAL_GOALS -> marketCode.getTargetType() == MarketTargetType.GOALS
                    ? scorelineModel.totalGoalsProbability(marketCode.getDirection(), marketCode.getThreshold())
                    : null;
            case TEAM_TOTAL_GOALS -> scorelineModel.teamGoalsProbability(
                    marketCode.getTeamScope(),
                    marketCode.getDirection(),
                    marketCode.getThreshold()
            );
            case BOTH_TEAMS_TO_SCORE -> marketCode.getDirection() == MarketDirection.YES ? bttsYes : 1.0 - bttsYes;
            case CLEAN_SHEET -> scorelineModel.cleanSheetProbability(marketCode.getTeamScope());
            case TOTAL_CORNERS -> thresholdProbability(expectedProfile.totalCorners(), marketCode.getDirection(), marketCode.getThreshold());
            case TEAM_CORNERS -> thresholdProbability(teamCornersLambda(expectedProfile, marketCode.getTeamScope()), marketCode.getDirection(), marketCode.getThreshold());
            case TOTAL_YELLOW_CARDS -> thresholdProbability(expectedProfile.totalYellowCards(), marketCode.getDirection(), marketCode.getThreshold());
            case RED_CARD -> marketCode.getDirection() == MarketDirection.YES ? redCardYes : 1.0 - redCardYes;
            case TEAM_TO_SCORE_FIRST, GOAL_PERIOD, TEAM_TO_WIN_PERIOD -> null;
        };
    }

    private Double resultProbability(MarketCode marketCode, ResultProbabilities resultProbabilities) {
        return switch (marketCode) {
            case HOME_WIN -> resultProbabilities.homeWin();
            case DRAW -> resultProbabilities.draw();
            case AWAY_WIN -> resultProbabilities.awayWin();
            default -> null;
        };
    }

    private double doubleChanceProbability(MarketCode marketCode, ResultProbabilities resultProbabilities) {
        return switch (marketCode) {
            case HOME_OR_DRAW -> resultProbabilities.homeWin() + resultProbabilities.draw();
            case AWAY_OR_DRAW -> resultProbabilities.awayWin() + resultProbabilities.draw();
            case HOME_OR_AWAY -> resultProbabilities.homeWin() + resultProbabilities.awayWin();
            default -> 0.0;
        };
    }

    private double drawNoBetProbability(MarketCode marketCode, ResultProbabilities resultProbabilities) {
        double denominator = resultProbabilities.homeWin() + resultProbabilities.awayWin();
        if (denominator <= 0.0) {
            return 0.5;
        }
        return switch (marketCode) {
            case HOME_DRAW_NO_BET -> resultProbabilities.homeWin() / denominator;
            case AWAY_DRAW_NO_BET -> resultProbabilities.awayWin() / denominator;
            default -> 0.0;
        };
    }

    private ResultProbabilities resultProbabilities(
            TeamFeatureSnapshot home,
            TeamFeatureSnapshot away,
            LeagueBaseline baseline,
            ExpectedProfile expectedProfile
    ) {
        double formDiff = scaled(home.getFormScore()) - scaled(away.getFormScore());
        double goalStrengthDiff = scaled(home.getGoalsForPerMatch().subtract(home.getGoalsAgainstPerMatch()))
                - scaled(away.getGoalsForPerMatch().subtract(away.getGoalsAgainstPerMatch()));
        double expectedGoalDiff = expectedProfile.homeGoals() - expectedProfile.awayGoals();

        double homeLogit = Math.log(clamp(rate(baseline.getHomeWinRate()), 0.01, 0.98))
                + 0.42 * formDiff
                + 0.28 * goalStrengthDiff
                + 0.22 * expectedGoalDiff;
        double awayLogit = Math.log(clamp(rate(baseline.getAwayWinRate()), 0.01, 0.98))
                - 0.42 * formDiff
                - 0.28 * goalStrengthDiff
                - 0.22 * expectedGoalDiff;
        double drawLogit = Math.log(clamp(rate(baseline.getDrawRate()), 0.01, 0.98))
                - 0.20 * Math.abs(formDiff)
                - 0.18 * Math.abs(expectedGoalDiff);

        double max = Math.max(homeLogit, Math.max(drawLogit, awayLogit));
        double homeExp = Math.exp(homeLogit - max);
        double drawExp = Math.exp(drawLogit - max);
        double awayExp = Math.exp(awayLogit - max);
        double total = homeExp + drawExp + awayExp;

        return new ResultProbabilities(homeExp / total, drawExp / total, awayExp / total);
    }

    private ResultProbabilities blendResults(ResultProbabilities scoreline, ResultProbabilities strength) {
        double home = blend(scoreline.homeWin(), strength.homeWin(), 0.70);
        double draw = blend(scoreline.draw(), strength.draw(), 0.70);
        double away = blend(scoreline.awayWin(), strength.awayWin(), 0.70);
        double total = home + draw + away;
        return new ResultProbabilities(home / total, draw / total, away / total);
    }

    private double blend(double primary, double secondary, double primaryWeight) {
        return primaryWeight * primary + (1.0 - primaryWeight) * secondary;
    }

    private ScorelineModel scorelineModel(ExpectedProfile expectedProfile) {
        double[][] matrix = new double[MAX_SCORELINE_GOALS + 1][MAX_SCORELINE_GOALS + 1];
        double totalMass = 0.0;
        for (int homeGoals = 0; homeGoals <= MAX_SCORELINE_GOALS; homeGoals++) {
            double homeProbability = poissonProbability(expectedProfile.homeGoals(), homeGoals);
            for (int awayGoals = 0; awayGoals <= MAX_SCORELINE_GOALS; awayGoals++) {
                double awayProbability = poissonProbability(expectedProfile.awayGoals(), awayGoals);
                double adjusted = Math.max(0.0, homeProbability * awayProbability
                        * dixonColesLowScoreAdjustment(homeGoals, awayGoals, expectedProfile.homeGoals(), expectedProfile.awayGoals()));
                matrix[homeGoals][awayGoals] = adjusted;
                totalMass += adjusted;
            }
        }

        double homeWin = 0.0;
        double draw = 0.0;
        double awayWin = 0.0;
        double btts = 0.0;
        double[][] normalizedMatrix = new double[MAX_SCORELINE_GOALS + 1][MAX_SCORELINE_GOALS + 1];

        for (int homeGoals = 0; homeGoals <= MAX_SCORELINE_GOALS; homeGoals++) {
            for (int awayGoals = 0; awayGoals <= MAX_SCORELINE_GOALS; awayGoals++) {
                double normalized = matrix[homeGoals][awayGoals] / totalMass;
                normalizedMatrix[homeGoals][awayGoals] = normalized;
                if (homeGoals > awayGoals) {
                    homeWin += normalized;
                } else if (homeGoals == awayGoals) {
                    draw += normalized;
                } else {
                    awayWin += normalized;
                }
                if (homeGoals > 0 && awayGoals > 0) {
                    btts += normalized;
                }
            }
        }

        return new ScorelineModel(
                new ResultProbabilities(homeWin, draw, awayWin),
                normalizedMatrix,
                btts
        );
    }

    private double dixonColesLowScoreAdjustment(int homeGoals, int awayGoals, double homeLambda, double awayLambda) {
        if (homeGoals == 0 && awayGoals == 0) {
            return 1.0 - homeLambda * awayLambda * DIXON_COLES_RHO;
        }
        if (homeGoals == 0 && awayGoals == 1) {
            return 1.0 + homeLambda * DIXON_COLES_RHO;
        }
        if (homeGoals == 1 && awayGoals == 0) {
            return 1.0 + awayLambda * DIXON_COLES_RHO;
        }
        if (homeGoals == 1 && awayGoals == 1) {
            return 1.0 - DIXON_COLES_RHO;
        }
        return 1.0;
    }

    private ExpectedProfile expectedProfile(TeamFeatureSnapshot home, TeamFeatureSnapshot away, LeagueBaseline baseline) {
        double homeGoals = weighted(
                valueOr(home.getHomeGoalsForPerMatch(), home.getGoalsForPerMatch()),
                valueOr(away.getAwayGoalsAgainstPerMatch(), away.getGoalsAgainstPerMatch()),
                baseline.getAvgHomeGoals(),
                0.42,
                0.38
        );
        double awayGoals = weighted(
                valueOr(away.getAwayGoalsForPerMatch(), away.getGoalsForPerMatch()),
                valueOr(home.getHomeGoalsAgainstPerMatch(), home.getGoalsAgainstPerMatch()),
                baseline.getAvgAwayGoals(),
                0.42,
                0.38
        );
        double corners = weightedNullable(
                sumNullable(home.getCornersForPerMatch(), away.getCornersAgainstPerMatch()),
                sumNullable(away.getCornersForPerMatch(), home.getCornersAgainstPerMatch()),
                baseline.getAvgTotalCorners(),
                0.35,
                0.35,
                9.0
        );
        double yellows = weightedNullable(
                sumNullable(home.getYellowCardsForPerMatch(), away.getYellowCardsAgainstPerMatch()),
                sumNullable(away.getYellowCardsForPerMatch(), home.getYellowCardsAgainstPerMatch()),
                baseline.getAvgTotalYellowCards(),
                0.35,
                0.35,
                3.5
        );
        double homeCorners = weightedNullable(
                doubleValue(home.getCornersForPerMatch()),
                doubleValue(away.getCornersAgainstPerMatch()),
                halve(baseline.getAvgTotalCorners()),
                0.45,
                0.35,
                corners / 2.0
        );
        double awayCorners = weightedNullable(
                doubleValue(away.getCornersForPerMatch()),
                doubleValue(home.getCornersAgainstPerMatch()),
                halve(baseline.getAvgTotalCorners()),
                0.45,
                0.35,
                corners / 2.0
        );

        return new ExpectedProfile(
                clamp(homeGoals, 0.15, 4.50),
                clamp(awayGoals, 0.15, 4.50),
                clamp(homeGoals + awayGoals, 0.30, 7.00),
                clamp(corners, 3.00, 18.00),
                clamp(homeCorners, 1.00, 12.00),
                clamp(awayCorners, 1.00, 12.00),
                clamp(yellows, 0.50, 9.00)
        );
    }

    private double bttsProbability(
            TeamFeatureSnapshot home,
            TeamFeatureSnapshot away,
            LeagueBaseline baseline,
            ExpectedProfile expectedProfile
    ) {
        double poisson = (1.0 - Math.exp(-expectedProfile.homeGoals())) * (1.0 - Math.exp(-expectedProfile.awayGoals()));
        double teamRate = (rate(home.getBttsRate()) + rate(away.getBttsRate())) / 2.0;
        return 0.55 * poisson + 0.25 * teamRate + 0.20 * rate(baseline.getBttsRate());
    }

    private double redCardProbability(TeamFeatureSnapshot home, TeamFeatureSnapshot away, LeagueBaseline baseline) {
        double leagueRate = baseline.getRedCardRate() == null ? 0.08 : rate(baseline.getRedCardRate());
        double homeRate = home.getRedCardRate() == null ? leagueRate : rate(home.getRedCardRate());
        double awayRate = away.getRedCardRate() == null ? leagueRate : rate(away.getRedCardRate());
        return 0.50 * leagueRate + 0.25 * homeRate + 0.25 * awayRate;
    }

    private double weighted(BigDecimal first, BigDecimal second, BigDecimal baseline, double firstWeight, double secondWeight) {
        double baselineWeight = 1.0 - firstWeight - secondWeight;
        return firstWeight * scaled(first) + secondWeight * scaled(second) + baselineWeight * scaled(baseline);
    }

    private double weightedNullable(
            Double first,
            Double second,
            BigDecimal baseline,
            double firstWeight,
            double secondWeight,
            double fallback
    ) {
        double baselineValue = baseline == null ? fallback : scaled(baseline);
        double result = 0.0;
        double weight = 0.0;
        if (first != null) {
            result += firstWeight * first;
            weight += firstWeight;
        }
        if (second != null) {
            result += secondWeight * second;
            weight += secondWeight;
        }
        double baselineWeight = 1.0 - weight;
        return result + baselineWeight * baselineValue;
    }

    private BigDecimal valueOr(BigDecimal preferred, BigDecimal fallback) {
        return preferred == null ? fallback : preferred;
    }

    private Double sumNullable(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return null;
        }
        return scaled(left) + scaled(right);
    }

    private double poissonOver(double lambda, int thresholdInclusive) {
        return 1.0 - poissonAtMost(lambda, thresholdInclusive);
    }

    private double thresholdProbability(double lambda, MarketDirection direction, BigDecimal threshold) {
        if (threshold == null) {
            return 0.0;
        }
        int floor = threshold.setScale(0, RoundingMode.FLOOR).intValue();
        return switch (direction) {
            case OVER -> poissonOver(lambda, floor);
            case UNDER -> poissonAtMost(lambda, floor);
            default -> 0.0;
        };
    }

    private double teamCornersLambda(ExpectedProfile expectedProfile, MarketTeamScope teamScope) {
        return switch (teamScope) {
            case HOME_TEAM -> expectedProfile.homeCorners();
            case AWAY_TEAM -> expectedProfile.awayCorners();
            case MATCH -> expectedProfile.totalCorners();
        };
    }

    private double poissonAtMost(double lambda, int thresholdInclusive) {
        double sum = 0.0;
        for (int k = 0; k <= thresholdInclusive; k++) {
            sum += poissonProbability(lambda, k);
        }
        return sum;
    }

    private double poissonProbability(double lambda, int k) {
        double numerator = Math.pow(lambda, k) * Math.exp(-lambda);
        double denominator = 1.0;
        for (int i = 2; i <= k; i++) {
            denominator *= i;
        }
        return numerator / denominator;
    }

    private BigDecimal probability(double value) {
        return BigDecimal.valueOf(clamp(value, 0.020000, 0.980000)).setScale(6, RoundingMode.HALF_UP);
    }

    private double scaled(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private Double doubleValue(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private BigDecimal halve(BigDecimal value) {
        return value == null ? null : value.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    private double rate(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record PredictionScores(Map<MarketCode, BigDecimal> probabilities, ExpectedProfile expectedProfile) {
    }

    public record ExpectedProfile(
            double homeGoals,
            double awayGoals,
            double totalGoals,
            double totalCorners,
            double homeCorners,
            double awayCorners,
            double totalYellowCards
    ) {
    }

    private record ResultProbabilities(double homeWin, double draw, double awayWin) {
    }

    private record ScorelineModel(
            ResultProbabilities resultProbabilities,
            double[][] probabilities,
            double btts
    ) {
        private double totalGoalsProbability(MarketDirection direction, BigDecimal threshold) {
            return thresholdProbability(direction, threshold, (homeGoals, awayGoals) -> homeGoals + awayGoals);
        }

        private double teamGoalsProbability(MarketTeamScope teamScope, MarketDirection direction, BigDecimal threshold) {
            return thresholdProbability(direction, threshold, (homeGoals, awayGoals) -> switch (teamScope) {
                case HOME_TEAM -> homeGoals;
                case AWAY_TEAM -> awayGoals;
                case MATCH -> homeGoals + awayGoals;
            });
        }

        private double cleanSheetProbability(MarketTeamScope teamScope) {
            double total = 0.0;
            for (int homeGoals = 0; homeGoals < probabilities.length; homeGoals++) {
                for (int awayGoals = 0; awayGoals < probabilities[homeGoals].length; awayGoals++) {
                    if ((teamScope == MarketTeamScope.HOME_TEAM && awayGoals == 0)
                            || (teamScope == MarketTeamScope.AWAY_TEAM && homeGoals == 0)) {
                        total += probabilities[homeGoals][awayGoals];
                    }
                }
            }
            return total;
        }

        private double thresholdProbability(MarketDirection direction, BigDecimal threshold, GoalSelector selector) {
            if (threshold == null) {
                return 0.0;
            }
            double total = 0.0;
            for (int homeGoals = 0; homeGoals < probabilities.length; homeGoals++) {
                for (int awayGoals = 0; awayGoals < probabilities[homeGoals].length; awayGoals++) {
                    int selectedGoals = selector.select(homeGoals, awayGoals);
                    boolean qualifies = direction == MarketDirection.OVER
                            ? BigDecimal.valueOf(selectedGoals).compareTo(threshold) > 0
                            : BigDecimal.valueOf(selectedGoals).compareTo(threshold) < 0;
                    if (qualifies) {
                        total += probabilities[homeGoals][awayGoals];
                    }
                }
            }
            return total;
        }
    }

    private interface GoalSelector {
        int select(int homeGoals, int awayGoals);
    }
}
