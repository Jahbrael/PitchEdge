package com.betai.service;

import com.betai.api.dto.RiskBand;
import com.betai.api.dto.PredictionResponseStatus;
import com.betai.api.dto.RankingMode;
import com.betai.api.dto.SelectionStrategy;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.odds.ValueRating;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.domain.team.Team;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BatchBuilderTest {

    private final BatchBuilder batchBuilder = new BatchBuilder();

    @Test
    void calculatesAccumulatorProbabilityAsProductOfIndividualProbabilities() {
        List<PredictionSelection> selections = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            selections.add(selection(index, MarketCode.OVER_2_5_GOALS, "OVER", "0.800000", "group-" + index));
        }

        var batches = batchBuilder.build(selections, 1, 20);

        assertThat(batches).hasSize(1);
        assertThat(batches.getFirst().risk().jointProbability()).isEqualByComparingTo(new BigDecimal("0.011529"));
        assertThat(batches.getFirst().risk().averageIndividualProbability()).isEqualByComparingTo(new BigDecimal("0.800000"));
        assertThat(batches.getFirst().risk().riskBand()).isEqualTo(RiskBand.EXTREME);
        assertThat(batches.getFirst().risk().varianceWarning())
                .contains("20 selections")
                .contains("80.00%")
                .contains("1.15%");
    }

    @Test
    void skipsSelectionsFromTheSameMatchWithinOneBatch() {
        PredictionSelection strongest = selection(1, MarketCode.HOME_WIN, "HOME", "0.900000", "match-result-1");
        PredictionSelection sameMatch = selection(1, MarketCode.DRAW, "DRAW", "0.890000", "match-result-1-alt");
        PredictionSelection differentMatch = selection(2, MarketCode.OVER_1_5_GOALS, "OVER", "0.880000", "goals-2");

        var batches = batchBuilder.build(List.of(strongest, sameMatch, differentMatch), 1, 2);

        assertThat(batches).hasSize(1);
        assertThat(batches.getFirst().selections())
                .extracting("selectionId")
                .containsExactly(strongest.getId(), differentMatch.getId());
    }

    @Test
    void returnsInsufficientStatusWhenFewerThanMinimumSelectionsQualify() {
        var batches = batchBuilder.build(
                List.of(selection(1, MarketCode.BTTS_YES, "YES", "0.700000", "btts-1")),
                1,
                2
        );

        assertThat(batches).hasSize(1);
        assertThat(batches.getFirst().status()).isEqualTo(PredictionResponseStatus.INSUFFICIENT_QUALIFIED_SELECTIONS);
        assertThat(batches.getFirst().returnedSelections()).isEqualTo(1);
    }

    @Test
    void usesProvidedRankingScoreWhenBuildingBatches() {
        PredictionSelection noOddsHighProbability = selection(1, MarketCode.HOME_WIN, "HOME", "0.900000", "result-1");
        PredictionSelection valueSelection = selection(2, MarketCode.OVER_2_5_GOALS, "OVER", "0.700000", "goals-2")
                .setBestDecimalOdds(new BigDecimal("1.6500"))
                .setExpectedValue(new BigDecimal("0.155000"))
                .setValueRating(ValueRating.STRONG_VALUE);

        var batches = batchBuilder.build(
                List.of(candidate(noOddsHighProbability, "0.500000"), candidate(valueSelection, "0.900000")),
                buildRequest(1, 2, 2)
        );

        assertThat(batches).hasSize(1);
        assertThat(batches.getFirst().selections())
                .extracting("selectionId")
                .containsExactly(valueSelection.getId(), noOddsHighProbability.getId());
        assertThat(batches.getFirst().risk().pricedSelectionCount()).isEqualTo(1);
        assertThat(batches.getFirst().risk().positiveValueSelectionCount()).isEqualTo(1);
        assertThat(batches.getFirst().risk().averageExpectedValue()).isEqualByComparingTo("0.155000");
    }

    @Test
    void capsReturnedSelectionsAtMaximum() {
        List<PredictionCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            candidates.add(candidate(selection(index, MarketCode.HOME_WIN, "HOME", "0.800000", "result-" + index), "0.800000"));
        }

        var batches = batchBuilder.build(candidates, buildRequest(1, 2, 3));

        assertThat(batches).hasSize(1);
        assertThat(batches.getFirst().returnedSelections()).isEqualTo(3);
        assertThat(batches.getFirst().status()).isEqualTo(PredictionResponseStatus.COMPLETE);
    }

    @Test
    void enforcesMaximumSelectionsPerLeague() {
        PredictionCandidate englandOne = candidate(selection(1, LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, "HOME", "0.900000", "england-1"), "0.900000");
        PredictionCandidate englandTwo = candidate(selection(2, LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, "HOME", "0.890000", "england-2"), "0.890000");
        PredictionCandidate englandThree = candidate(selection(3, LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, "HOME", "0.880000", "england-3"), "0.880000");
        PredictionCandidate spain = candidate(selection(4, LeagueCode.LA_LIGA, MarketCode.HOME_WIN, "HOME", "0.700000", "spain-4"), "0.700000");

        var batches = batchBuilder.build(
                List.of(englandOne, englandTwo, englandThree, spain),
                new PredictionBatchBuildRequest(
                        SelectionStrategy.BALANCED,
                        1,
                        3,
                        4,
                        4,
                        false,
                        1,
                        null,
                        2,
                        false,
                        1,
                        false,
                        true,
                        false,
                        BigDecimal.ZERO,
                        new BigDecimal("0.50")
                )
        );

        assertThat(batches.getFirst().selections())
                .extracting("selectionId")
                .doesNotContain(englandThree.selection().getId())
                .contains(spain.selection().getId());
    }

    @Test
    void rejectsHardConflictingMarketsFromSameMatch() {
        PredictionCandidate homeWin = candidate(selection(1, MarketCode.HOME_WIN, "HOME", "0.900000", "result-1"), "0.900000");
        PredictionCandidate awayWin = candidate(selection(1, MarketCode.AWAY_WIN, "AWAY", "0.880000", "result-1-away"), "0.880000");
        PredictionCandidate other = candidate(selection(2, MarketCode.OVER_2_5_GOALS, "OVER", "0.700000", "goals-2"), "0.700000");

        var batches = batchBuilder.build(
                List.of(homeWin, awayWin, other),
                new PredictionBatchBuildRequest(
                        SelectionStrategy.BALANCED,
                        1,
                        2,
                        2,
                        3,
                        true,
                        2,
                        null,
                        null,
                        false,
                        1,
                        false,
                        true,
                        false,
                        BigDecimal.ZERO,
                        new BigDecimal("0.50")
                )
        );

        assertThat(batches.getFirst().selections())
                .extracting("selectionId")
                .containsExactly(homeWin.selection().getId(), other.selection().getId());
    }

    @Test
    void rejectsExpandedHardConflictingMarketsFromSameMatch() {
        PredictionCandidate homeOrDraw = candidate(selection(1, MarketCode.HOME_OR_DRAW, "HOME", "0.900000", "result-1-double"), "0.900000");
        PredictionCandidate awayWin = candidate(selection(1, MarketCode.AWAY_WIN, "AWAY", "0.880000", "result-1-away"), "0.880000");
        PredictionCandidate homeDnb = candidate(selection(2, MarketCode.HOME_DRAW_NO_BET, "HOME", "0.870000", "result-2-home-dnb"), "0.870000");
        PredictionCandidate awayDnb = candidate(selection(2, MarketCode.AWAY_DRAW_NO_BET, "AWAY", "0.860000", "result-2-away-dnb"), "0.860000");
        PredictionCandidate redYes = candidate(selection(3, MarketCode.RED_CARD_YES, "YES", "0.850000", "cards-3-red-yes"), "0.850000");
        PredictionCandidate redNo = candidate(selection(3, MarketCode.RED_CARD_NO, "NO", "0.840000", "cards-3-red-no"), "0.840000");

        var batches = batchBuilder.build(
                List.of(homeOrDraw, awayWin, homeDnb, awayDnb, redYes, redNo),
                new PredictionBatchBuildRequest(
                        SelectionStrategy.BALANCED,
                        1,
                        3,
                        6,
                        6,
                        true,
                        2,
                        null,
                        null,
                        false,
                        1,
                        false,
                        true,
                        false,
                        BigDecimal.ZERO,
                        new BigDecimal("0.50")
                )
        );

        assertThat(batches.getFirst().selections())
                .extracting("selectionId")
                .contains(homeOrDraw.selection().getId(), homeDnb.selection().getId(), redYes.selection().getId())
                .doesNotContain(awayWin.selection().getId(), awayDnb.selection().getId(), redNo.selection().getId());
    }

    @Test
    void softCorrelationPenaltyLetsUncorrelatedSelectionRankAhead() {
        PredictionCandidate homeWin = candidate(selection(1, MarketCode.HOME_WIN, "HOME", "0.900000", "result-1"), "0.900000");
        PredictionCandidate correlatedOver = candidate(selection(1, MarketCode.OVER_2_5_GOALS, "OVER", "0.850000", "goals-1"), "0.850000");
        PredictionCandidate uncorrelated = candidate(selection(2, MarketCode.BTTS_YES, "YES", "0.800000", "btts-2"), "0.800000");

        var batches = batchBuilder.build(
                List.of(homeWin, correlatedOver, uncorrelated),
                new PredictionBatchBuildRequest(
                        SelectionStrategy.BALANCED,
                        1,
                        2,
                        2,
                        3,
                        true,
                        2,
                        null,
                        null,
                        false,
                        1,
                        false,
                        true,
                        false,
                        BigDecimal.ZERO,
                        new BigDecimal("0.50")
                )
        );

        assertThat(batches.getFirst().selections())
                .extracting("selectionId")
                .containsExactly(homeWin.selection().getId(), uncorrelated.selection().getId());
    }

    @Test
    void softCorrelationPenaltyCoversAllExpandedTeamGoalLines() {
        PredictionCandidate homeWin = candidate(selection(1, MarketCode.HOME_WIN, "HOME", "0.900000", "result-1"), "0.900000");
        PredictionCandidate correlatedTeamTotal = candidate(selection(1, MarketCode.HOME_TEAM_OVER_4_5_GOALS, "OVER", "0.850000", "home-goals-1"), "0.850000");
        PredictionCandidate uncorrelated = candidate(selection(2, MarketCode.BTTS_YES, "YES", "0.800000", "btts-2"), "0.800000");

        var batches = batchBuilder.build(
                List.of(homeWin, correlatedTeamTotal, uncorrelated),
                new PredictionBatchBuildRequest(
                        SelectionStrategy.BALANCED,
                        1,
                        2,
                        2,
                        3,
                        true,
                        2,
                        null,
                        null,
                        false,
                        1,
                        false,
                        true,
                        false,
                        BigDecimal.ZERO,
                        new BigDecimal("0.50")
                )
        );

        assertThat(batches.getFirst().selections())
                .extracting("selectionId")
                .containsExactly(homeWin.selection().getId(), uncorrelated.selection().getId());
    }

    @Test
    void appliesSoftCorrelationPenaltyForRelatedThresholdMarkets() {
        PredictionCandidate corners85 = candidate(selection(1, MarketCode.CORNERS_OVER_8_5, "OVER", "0.900000", "corners-1-85"), "0.900000");
        PredictionCandidate corners95 = candidate(selection(1, MarketCode.CORNERS_UNDER_9_5, "UNDER", "0.850000", "corners-1-under-95"), "0.850000");
        PredictionCandidate unrelated = candidate(selection(2, MarketCode.BTTS_YES, "YES", "0.800000", "btts-2"), "0.800000");

        var batches = batchBuilder.build(
                List.of(corners85, corners95, unrelated),
                new PredictionBatchBuildRequest(
                        SelectionStrategy.BALANCED,
                        1,
                        2,
                        2,
                        3,
                        true,
                        2,
                        null,
                        null,
                        false,
                        1,
                        false,
                        true,
                        false,
                        BigDecimal.ZERO,
                        new BigDecimal("0.50")
                )
        );

        assertThat(batches.getFirst().selections())
                .extracting("selectionId")
                .containsExactly(corners85.selection().getId(), unrelated.selection().getId());
    }

    @Test
    void rejectsImpossibleCrossLineThresholdCombinations() {
        PredictionCandidate over85 = candidate(selection(1, MarketCode.CORNERS_OVER_8_5, "OVER", "0.900000", "corners-over-85"), "0.900000");
        PredictionCandidate under75 = candidate(selection(1, MarketCode.CORNERS_UNDER_7_5, "UNDER", "0.890000", "corners-under-75"), "0.890000");
        PredictionCandidate unrelated = candidate(selection(2, MarketCode.BTTS_YES, "YES", "0.800000", "btts-2"), "0.800000");

        var batches = batchBuilder.build(
                List.of(over85, under75, unrelated),
                new PredictionBatchBuildRequest(
                        SelectionStrategy.BALANCED,
                        1,
                        2,
                        2,
                        3,
                        true,
                        2,
                        null,
                        null,
                        false,
                        1,
                        false,
                        true,
                        false,
                        BigDecimal.ZERO,
                        new BigDecimal("0.50")
                )
        );

        assertThat(batches.getFirst().selections())
                .extracting("selectionId")
                .containsExactly(over85.selection().getId(), unrelated.selection().getId())
                .doesNotContain(under75.selection().getId());
    }

    @Test
    void generatesMultipleDiverseBatchesWithoutRepeatingSelections() {
        List<PredictionCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            candidates.add(candidate(selection(index, MarketCode.HOME_WIN, "HOME", "0.800000", "result-" + index), "0.800000"));
        }

        var batches = batchBuilder.build(
                candidates,
                new PredictionBatchBuildRequest(
                        SelectionStrategy.BALANCED,
                        2,
                        3,
                        3,
                        6,
                        false,
                        1,
                        null,
                        null,
                        false,
                        1,
                        false,
                        true,
                        false,
                        new BigDecimal("0.40"),
                        new BigDecimal("0.50")
                )
        );

        assertThat(batches).hasSize(2);
        assertThat(batches.getFirst().selections())
                .extracting("selectionId")
                .doesNotContainAnyElementsOf(batches.get(1).selections().stream()
                        .map(com.betai.api.dto.PredictionSelectionResponse::selectionId)
                        .toList());
    }

    @Test
    void returnsFewerBatchesWhenDiversityCannotBeAchieved() {
        List<PredictionCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            candidates.add(candidate(selection(index, MarketCode.HOME_WIN, "HOME", "0.800000", "result-" + index), "0.800000"));
        }

        var batches = batchBuilder.build(
                candidates,
                new PredictionBatchBuildRequest(
                        SelectionStrategy.BALANCED,
                        2,
                        3,
                        3,
                        3,
                        false,
                        1,
                        null,
                        null,
                        false,
                        1,
                        false,
                        true,
                        true,
                        new BigDecimal("0.40"),
                        new BigDecimal("0.50")
                )
        );

        assertThat(batches).hasSize(1);
    }

    private PredictionSelection selection(
            int matchIndex,
            MarketCode marketCode,
            String predictedValue,
            String probability,
            String correlationGroup
    ) {
        return selection(matchIndex, LeagueCode.PREMIER_LEAGUE, marketCode, predictedValue, probability, correlationGroup);
    }

    private PredictionSelection selection(
            int matchIndex,
            LeagueCode leagueCode,
            MarketCode marketCode,
            String predictedValue,
            String probability,
            String correlationGroup
    ) {
        PredictionSelection selection = new PredictionSelection()
                .setMatch(match(matchIndex, leagueCode))
                .setMarketDefinition(market(marketCode))
                .setPredictedValue(predictedValue)
                .setProbability(new BigDecimal(probability))
                .setModelVersion("test-model")
                .setCorrelationGroupKey(correlationGroup)
                .setOutcome(PredictionOutcome.PENDING);
        selection.setId(UUID.randomUUID());
        return selection;
    }

    private PredictionCandidate candidate(PredictionSelection selection, String rankingScore) {
        return new PredictionCandidate(
                selection,
                SelectionStrategy.BALANCED,
                RankingMode.COMPOSITE_SCORE,
                new BigDecimal(rankingScore),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                selection.getProbability(),
                "CALIBRATED",
                "test candidate"
        );
    }

    private PredictionBatchBuildRequest buildRequest(int batchCount, int minimumSelections, int maximumSelections) {
        return new PredictionBatchBuildRequest(
                SelectionStrategy.BALANCED,
                batchCount,
                minimumSelections,
                maximumSelections,
                20,
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
        );
    }

    private MarketDefinition market(MarketCode marketCode) {
        MarketDefinition market = new MarketDefinition()
                .setCode(marketCode)
                .setDisplayName(marketCode.getDisplayName())
                .setMarketType(marketCode.getMarketType())
                .setMarketFamily(marketCode.getMarketType())
                .setDirection(marketCode.getDirection())
                .setSelectionValue(marketCode.getSelectionValue())
                .setThreshold(marketCode.getThreshold())
                .setPeriod(marketCode.getPeriod())
                .setTeamScope(marketCode.getTeamScope())
                .setTargetType(marketCode.getTargetType())
                .setEnabled(true)
                .setActive(true)
                .setMinimumSampleSize(marketCode.getMinimumSampleSize())
                .setSettlementDescription(marketCode.getSettlementDescription());
        market.setId(UUID.randomUUID());
        return market;
    }

    private Match match(int index) {
        return match(index, LeagueCode.PREMIER_LEAGUE);
    }

    private Match match(int index, LeagueCode leagueCode) {
        League league = new League()
                .setCode(leagueCode)
                .setName(leagueCode.getDisplayName())
                .setCountry(leagueCode.getCountry())
                .setTier(1)
                .setActive(true)
                .setScrapeEnabled(true)
                .setCurrentSeason("2026/2027");
        league.setId(UUID.randomUUID());

        Team homeTeam = team(league, "Home " + index, "H" + index);
        Team awayTeam = team(league, "Away " + index, "A" + index);
        OffsetDateTime kickoffAt = OffsetDateTime.parse("2026-08-01T15:00:00Z").plusDays(index);

        Match match = new Match()
                .setLeague(league)
                .setHomeTeam(homeTeam)
                .setAwayTeam(awayTeam)
                .setMatchDate(kickoffAt.toLocalDate())
                .setKickoffAt(kickoffAt)
                .setStatus(MatchStatus.SCHEDULED)
                .setSeasonLabel("2026/2027")
                .setSourceFixtureKey("fixture-" + index);
        match.setId(UUID.nameUUIDFromBytes(("match-" + leagueCode + "-" + index).getBytes()));
        return match;
    }

    private Team team(League league, String name, String shortName) {
        Team team = new Team()
                .setLeague(league)
                .setCanonicalName(name)
                .setShortName(shortName)
                .setCountry(league.getCountry())
                .setExternalKey("team-" + name)
                .setActive(true);
        team.setId(UUID.randomUUID());
        return team;
    }
}
