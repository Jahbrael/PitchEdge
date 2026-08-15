package com.betai.service;

import com.betai.domain.feature.LeagueBaseline;
import com.betai.domain.feature.TeamFeatureSnapshot;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MarketProbabilityEngineTest {

    private final MarketProbabilityEngine engine = new MarketProbabilityEngine();

    @Test
    void producesNormalizedResultProbabilitiesAndMonotonicGoalMarkets() {
        var scores = engine.score(null, strongHomeFeature(), weakerAwayFeature(), baseline());

        BigDecimal homeWin = scores.probabilities().get(MarketCode.HOME_WIN);
        BigDecimal draw = scores.probabilities().get(MarketCode.DRAW);
        BigDecimal awayWin = scores.probabilities().get(MarketCode.AWAY_WIN);

        assertThat(scores.probabilities().keySet())
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(MarketCode.values())
                        .filter(MarketCode::isEnabled)
                        .toList());
        assertThat(homeWin.add(draw).add(awayWin)).isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
        assertThat(homeWin).isGreaterThan(awayWin);
        assertThat(scores.probabilities().get(MarketCode.OVER_1_5_GOALS))
                .isGreaterThanOrEqualTo(scores.probabilities().get(MarketCode.OVER_2_5_GOALS));
        assertThat(scores.probabilities().get(MarketCode.OVER_2_5_GOALS)
                .add(scores.probabilities().get(MarketCode.UNDER_2_5_GOALS)))
                .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
        assertThat(scores.probabilities().get(MarketCode.BTTS_YES))
                .isBetween(new BigDecimal("0.020000"), new BigDecimal("0.980000"));
        assertThat(scores.probabilities().get(MarketCode.BTTS_YES)
                .add(scores.probabilities().get(MarketCode.BTTS_NO)))
                .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
    }

    @Test
    void catalogueKeepsHistoricalCodesButDisablesUnsupportedAndRetiredMarkets() {
        assertThat(MarketCode.values()).hasSize(100);
        assertThat(Arrays.stream(MarketCode.values()).map(Enum::name).distinct()).hasSize(100);
        assertThat(Arrays.stream(MarketCode.values()).filter(MarketCode::isEnabled)).hasSize(74);
        assertThat(Arrays.stream(MarketCode.values()).filter(market -> !market.isEnabled()))
                .extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "UNDER_0_5_GOALS",
                        "OVER_5_5_GOALS",
                        "UNDER_5_5_GOALS",
                        "OVER_6_5_GOALS",
                        "UNDER_6_5_GOALS",
                        "OVER_7_5_GOALS",
                        "UNDER_7_5_GOALS",
                        "HOME_TEAM_OVER_3_5_GOALS",
                        "HOME_TEAM_OVER_4_5_GOALS",
                        "HOME_TEAM_UNDER_3_5_GOALS",
                        "HOME_TEAM_UNDER_4_5_GOALS",
                        "AWAY_TEAM_OVER_3_5_GOALS",
                        "AWAY_TEAM_OVER_4_5_GOALS",
                        "AWAY_TEAM_UNDER_3_5_GOALS",
                        "AWAY_TEAM_UNDER_4_5_GOALS",
                        "FIRST_HALF_OVER_0_5_GOALS",
                        "FIRST_HALF_OVER_1_5_GOALS",
                        "FIRST_HALF_UNDER_1_5_GOALS",
                        "SECOND_HALF_OVER_0_5_GOALS",
                        "SECOND_HALF_OVER_1_5_GOALS",
                        "SECOND_HALF_UNDER_1_5_GOALS",
                        "HOME_TEAM_TO_SCORE_FIRST",
                        "AWAY_TEAM_TO_SCORE_FIRST",
                        "GOAL_IN_BOTH_HALVES",
                        "HOME_TEAM_TO_WIN_EITHER_HALF",
                        "AWAY_TEAM_TO_WIN_EITHER_HALF"
                );
    }

    @Test
    void calculatesDoubleChanceAndDrawNoBetFromResultProbabilities() {
        var scores = engine.score(null, strongHomeFeature(), weakerAwayFeature(), baseline());
        var probabilities = scores.probabilities();

        assertClose(probabilities.get(MarketCode.HOME_OR_DRAW), probabilities.get(MarketCode.HOME_WIN).add(probabilities.get(MarketCode.DRAW)));
        assertClose(probabilities.get(MarketCode.AWAY_OR_DRAW), probabilities.get(MarketCode.AWAY_WIN).add(probabilities.get(MarketCode.DRAW)));
        assertClose(probabilities.get(MarketCode.HOME_OR_AWAY), probabilities.get(MarketCode.HOME_WIN).add(probabilities.get(MarketCode.AWAY_WIN)));
        assertThat(probabilities.get(MarketCode.HOME_DRAW_NO_BET).add(probabilities.get(MarketCode.AWAY_DRAW_NO_BET)))
                .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
    }

    @Test
    void calculatesGoalTeamGoalAndCleanSheetThresholdsFromScorelineMatrix() {
        var probabilities = engine.score(null, strongHomeFeature(), weakerAwayFeature(), baseline()).probabilities();

        assertNonIncreasing(probabilities,
                MarketCode.OVER_0_5_GOALS,
                MarketCode.OVER_1_5_GOALS,
                MarketCode.OVER_2_5_GOALS,
                MarketCode.OVER_3_5_GOALS,
                MarketCode.OVER_4_5_GOALS);
        assertNonIncreasing(probabilities,
                MarketCode.HOME_TEAM_OVER_0_5_GOALS,
                MarketCode.HOME_TEAM_OVER_1_5_GOALS,
                MarketCode.HOME_TEAM_OVER_2_5_GOALS);
        assertNonIncreasing(probabilities,
                MarketCode.AWAY_TEAM_OVER_0_5_GOALS,
                MarketCode.AWAY_TEAM_OVER_1_5_GOALS,
                MarketCode.AWAY_TEAM_OVER_2_5_GOALS);
        assertThat(probabilities.get(MarketCode.OVER_4_5_GOALS).add(probabilities.get(MarketCode.UNDER_4_5_GOALS)))
                .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
        assertThat(probabilities.get(MarketCode.HOME_TEAM_OVER_1_5_GOALS)
                .add(probabilities.get(MarketCode.HOME_TEAM_UNDER_1_5_GOALS)))
                .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
        assertThat(probabilities.get(MarketCode.HOME_TEAM_OVER_1_5_GOALS))
                .isGreaterThan(probabilities.get(MarketCode.HOME_TEAM_OVER_2_5_GOALS));
        assertThat(probabilities.get(MarketCode.HOME_TEAM_OVER_2_5_GOALS)
                .add(probabilities.get(MarketCode.HOME_TEAM_UNDER_2_5_GOALS)))
                .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
        assertThat(probabilities.get(MarketCode.AWAY_TEAM_OVER_1_5_GOALS)
                .add(probabilities.get(MarketCode.AWAY_TEAM_UNDER_1_5_GOALS)))
                .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
        assertThat(probabilities.get(MarketCode.AWAY_TEAM_OVER_1_5_GOALS))
                .isGreaterThan(probabilities.get(MarketCode.AWAY_TEAM_OVER_2_5_GOALS));
        assertThat(probabilities.get(MarketCode.AWAY_TEAM_OVER_2_5_GOALS)
                .add(probabilities.get(MarketCode.AWAY_TEAM_UNDER_2_5_GOALS)))
                .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
        assertThat(probabilities.get(MarketCode.HOME_TEAM_CLEAN_SHEET)
                .add(probabilities.get(MarketCode.AWAY_TEAM_OVER_0_5_GOALS)))
                .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
        assertThat(probabilities.get(MarketCode.AWAY_TEAM_CLEAN_SHEET)
                .add(probabilities.get(MarketCode.HOME_TEAM_OVER_0_5_GOALS)))
                .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
    }

    @Test
    void calculatesCornerCardAndRedCardThresholdsWithoutOdds() {
        var probabilities = engine.score(null, strongHomeFeature(), weakerAwayFeature(), baseline()).probabilities();

        assertNonIncreasing(probabilities,
                MarketCode.CORNERS_OVER_5_5,
                MarketCode.CORNERS_OVER_6_5,
                MarketCode.CORNERS_OVER_7_5,
                MarketCode.CORNERS_OVER_8_5,
                MarketCode.CORNERS_OVER_9_5,
                MarketCode.CORNERS_OVER_10_5,
                MarketCode.CORNERS_OVER_11_5,
                MarketCode.CORNERS_OVER_12_5,
                MarketCode.CORNERS_OVER_13_5);
        assertNonIncreasing(probabilities,
                MarketCode.HOME_TEAM_CORNERS_OVER_2_5,
                MarketCode.HOME_TEAM_CORNERS_OVER_3_5,
                MarketCode.HOME_TEAM_CORNERS_OVER_4_5,
                MarketCode.HOME_TEAM_CORNERS_OVER_5_5);
        assertNonIncreasing(probabilities,
                MarketCode.AWAY_TEAM_CORNERS_OVER_2_5,
                MarketCode.AWAY_TEAM_CORNERS_OVER_3_5,
                MarketCode.AWAY_TEAM_CORNERS_OVER_4_5,
                MarketCode.AWAY_TEAM_CORNERS_OVER_5_5);
        assertNonIncreasing(probabilities,
                MarketCode.YELLOW_CARDS_OVER_2_5,
                MarketCode.YELLOW_CARDS_OVER_3_5,
                MarketCode.YELLOW_CARDS_OVER_4_5,
                MarketCode.YELLOW_CARDS_OVER_5_5);
        assertThat(probabilities.get(MarketCode.CORNERS_OVER_9_5).add(probabilities.get(MarketCode.CORNERS_UNDER_9_5)))
                .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
        assertThat(probabilities.get(MarketCode.YELLOW_CARDS_OVER_4_5).add(probabilities.get(MarketCode.YELLOW_CARDS_UNDER_4_5)))
                .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
        assertThat(probabilities.get(MarketCode.RED_CARD_YES).add(probabilities.get(MarketCode.RED_CARD_NO)))
                .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
        assertThat(probabilities.get(MarketCode.HOME_WIN))
                .isEqualByComparingTo(engine.score(null, strongHomeFeature(), weakerAwayFeature(), baseline()).probabilities().get(MarketCode.HOME_WIN));
    }

    @Test
    void everyCompleteOverUnderThresholdPairIsComplementary() {
        var probabilities = engine.score(null, strongHomeFeature(), weakerAwayFeature(), baseline()).probabilities();
        int pairs = 0;

        for (MarketCode over : MarketCode.values()) {
            if (!over.isEnabled() || over.getDirection() != MarketDirection.OVER || over.getThreshold() == null) {
                continue;
            }
            MarketCode under = Arrays.stream(MarketCode.values())
                    .filter(MarketCode::isEnabled)
                    .filter(candidate -> candidate.getDirection() == MarketDirection.UNDER)
                    .filter(candidate -> candidate.getMarketType() == over.getMarketType())
                    .filter(candidate -> candidate.getPeriod() == over.getPeriod())
                    .filter(candidate -> candidate.getTeamScope() == over.getTeamScope())
                    .filter(candidate -> over.getThreshold().compareTo(candidate.getThreshold()) == 0)
                    .findFirst()
                    .orElse(null);
            if (under == null) {
                continue;
            }
            assertThat(probabilities.get(over).add(probabilities.get(under)))
                    .as("%s + %s", over, under)
                    .isBetween(new BigDecimal("0.999000"), new BigDecimal("1.001000"));
            pairs++;
        }

        assertThat(pairs).isEqualTo(28);
    }

    @Test
    void unsupportedAndRetiredMarketsDoNotGenerateFakeProbabilities() {
        var probabilities = engine.score(null, strongHomeFeature(), weakerAwayFeature(), baseline()).probabilities();

        assertThat(probabilities)
                .doesNotContainKeys(
                        MarketCode.FIRST_HALF_OVER_0_5_GOALS,
                        MarketCode.SECOND_HALF_OVER_1_5_GOALS,
                        MarketCode.HOME_TEAM_TO_SCORE_FIRST,
                        MarketCode.GOAL_IN_BOTH_HALVES,
                        MarketCode.UNDER_0_5_GOALS,
                        MarketCode.OVER_5_5_GOALS,
                        MarketCode.HOME_TEAM_OVER_3_5_GOALS,
                        MarketCode.AWAY_TEAM_UNDER_4_5_GOALS
                );
    }

    private TeamFeatureSnapshot strongHomeFeature() {
        return baseFeature()
                .setPointsPerMatch(new BigDecimal("2.1000"))
                .setLast5PointsPerMatch(new BigDecimal("2.4000"))
                .setLast10PointsPerMatch(new BigDecimal("2.2000"))
                .setGoalsForPerMatch(new BigDecimal("1.9000"))
                .setGoalsAgainstPerMatch(new BigDecimal("0.9000"))
                .setHomeGoalsForPerMatch(new BigDecimal("2.1000"))
                .setHomeGoalsAgainstPerMatch(new BigDecimal("0.8000"))
                .setFormScore(new BigDecimal("0.7600"));
    }

    private TeamFeatureSnapshot weakerAwayFeature() {
        return baseFeature()
                .setPointsPerMatch(new BigDecimal("1.0000"))
                .setLast5PointsPerMatch(new BigDecimal("0.8000"))
                .setLast10PointsPerMatch(new BigDecimal("0.9000"))
                .setGoalsForPerMatch(new BigDecimal("1.0000"))
                .setGoalsAgainstPerMatch(new BigDecimal("1.7000"))
                .setAwayGoalsForPerMatch(new BigDecimal("0.9000"))
                .setAwayGoalsAgainstPerMatch(new BigDecimal("1.8000"))
                .setFormScore(new BigDecimal("0.3200"));
    }

    private TeamFeatureSnapshot baseFeature() {
        return new TeamFeatureSnapshot()
                .setBttsRate(new BigDecimal("0.520000"))
                .setCornersForPerMatch(new BigDecimal("5.2000"))
                .setCornersAgainstPerMatch(new BigDecimal("4.8000"))
                .setYellowCardsForPerMatch(new BigDecimal("2.1000"))
                .setYellowCardsAgainstPerMatch(new BigDecimal("2.0000"))
                .setRedCardRate(new BigDecimal("0.090000"));
    }

    private LeagueBaseline baseline() {
        return new LeagueBaseline()
                .setAvgHomeGoals(new BigDecimal("1.5200"))
                .setAvgAwayGoals(new BigDecimal("1.1800"))
                .setAvgTotalGoals(new BigDecimal("2.7000"))
                .setHomeWinRate(new BigDecimal("0.440000"))
                .setDrawRate(new BigDecimal("0.260000"))
                .setAwayWinRate(new BigDecimal("0.300000"))
                .setBttsRate(new BigDecimal("0.540000"))
                .setAvgTotalCorners(new BigDecimal("10.1000"))
                .setAvgTotalYellowCards(new BigDecimal("4.1000"))
                .setRedCardRate(new BigDecimal("0.120000"));
    }

    private void assertClose(BigDecimal actual, BigDecimal expected) {
        assertThat(actual.subtract(expected).abs()).isLessThanOrEqualTo(new BigDecimal("0.000010"));
    }

    private void assertNonIncreasing(java.util.Map<MarketCode, BigDecimal> probabilities, MarketCode... markets) {
        for (int index = 1; index < markets.length; index++) {
            assertThat(probabilities.get(markets[index - 1]))
                    .as("%s should be at least %s", markets[index - 1], markets[index])
                    .isGreaterThanOrEqualTo(probabilities.get(markets[index]));
        }
    }
}
