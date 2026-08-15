package com.betai.service;

import com.betai.api.dto.PredictionRequest;
import com.betai.api.dto.RankingMode;
import com.betai.api.dto.SelectionStrategy;
import com.betai.api.dto.ValueMode;
import com.betai.config.PredictionProperties;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.odds.ValueRating;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.domain.quality.ModelQualitySnapshot;
import com.betai.domain.team.Team;
import com.betai.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PredictionCandidateFilterTest {

    private final PredictionCandidateFilter filter = new PredictionCandidateFilter();
    private final PredictionProperties properties = new PredictionProperties(
            "test-model",
            20,
            20,
            14,
            List.of(MatchStatus.SCHEDULED),
            3,
            10,
            com.betai.domain.feature.InsufficientSeasonPolicy.USE_MAX_AVAILABLE,
            10
    );

    @Test
    void filtersByFinalTunedModelProbabilityRange() {
        PredictionSelection inside = selection(1, LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, "0.820000");
        PredictionSelection below = selection(2, LeagueCode.LA_LIGA, MarketCode.HOME_WIN, "0.790000");
        PredictionSelection above = selection(3, LeagueCode.SERIE_A, MarketCode.HOME_WIN, "0.860000");
        ResolvedPredictionRequest request = resolve(request(
                SelectionStrategy.LOWER_RISK,
                1,
                5,
                1,
                new BigDecimal("0.80"),
                new BigDecimal("0.85"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        PredictionCandidateFilterResult result = filter.filterAndRank(List.of(inside, below, above), request);

        assertThat(result.candidates()).extracting(candidate -> candidate.selection().getId())
                .containsExactly(inside.getId());
    }

    @Test
    void lowerRiskIgnoresExcellentBookmakerValueDuringQualification() {
        PredictionSelection eightyTwoPercent = selection(1, LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, "0.820000");
        PredictionSelection fiftyFivePercentExcellentValue = selection(2, LeagueCode.LA_LIGA, MarketCode.HOME_WIN, "0.550000")
                .setBestDecimalOdds(new BigDecimal("4.0000"))
                .setBestImpliedProbability(new BigDecimal("0.250000"))
                .setValueEdge(new BigDecimal("0.300000"))
                .setExpectedValue(new BigDecimal("1.200000"))
                .setValueRating(ValueRating.STRONG_VALUE);
        ResolvedPredictionRequest request = resolve(request(SelectionStrategy.LOWER_RISK));

        PredictionCandidateFilterResult result = filter.filterAndRank(
                List.of(fiftyFivePercentExcellentValue, eightyTwoPercent),
                request
        );

        assertThat(result.candidates()).extracting(candidate -> candidate.selection().getId())
                .containsExactly(eightyTwoPercent.getId());
    }

    @Test
    void lowerRiskIgnoresExplicitOddsFiltersDuringQualification() {
        PredictionSelection unpricedStrongProbability = selection(1, LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, "0.830000");
        PredictionSelection pricedStrongProbability = selection(2, LeagueCode.LA_LIGA, MarketCode.HOME_WIN, "0.810000")
                .setBestDecimalOdds(new BigDecimal("3.5000"))
                .setBestImpliedProbability(new BigDecimal("0.285714"))
                .setValueEdge(new BigDecimal("0.524286"))
                .setExpectedValue(new BigDecimal("1.835000"))
                .setValueRating(ValueRating.STRONG_VALUE);
        ResolvedPredictionRequest request = resolve(request(
                SelectionStrategy.LOWER_RISK,
                1,
                5,
                1,
                new BigDecimal("0.80"),
                new BigDecimal("0.85"),
                new BigDecimal("2.00"),
                null,
                null,
                null,
                null,
                new BigDecimal("0.50"),
                new BigDecimal("0.10"),
                RankingMode.EXPECTED_VALUE
        ));

        PredictionCandidateFilterResult result = filter.filterAndRank(
                List.of(pricedStrongProbability, unpricedStrongProbability),
                request
        );

        assertThat(request.strategySettings().getFirst().usesOddsForQualification()).isFalse();
        assertThat(request.strategySettings().getFirst().rankingMode()).isEqualTo(RankingMode.MODEL_PROBABILITY);
        assertThat(result.candidates()).extracting(candidate -> candidate.selection().getId())
                .containsExactly(unpricedStrongProbability.getId(), pricedStrongProbability.getId());
    }

    @Test
    void valueStrategyAllowsSameFiftyFivePercentCandidateWhenValueRequirementsPass() {
        PredictionSelection fiftyFivePercentExcellentValue = selection(1, LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, "0.550000")
                .setBestDecimalOdds(new BigDecimal("4.0000"))
                .setBestImpliedProbability(new BigDecimal("0.250000"))
                .setValueEdge(new BigDecimal("0.300000"))
                .setExpectedValue(new BigDecimal("1.200000"))
                .setValueRating(ValueRating.STRONG_VALUE);
        PredictionSelection sixtyFivePercentSmallValue = selection(2, LeagueCode.LA_LIGA, MarketCode.HOME_WIN, "0.650000")
                .setBestDecimalOdds(new BigDecimal("1.7000"))
                .setBestImpliedProbability(new BigDecimal("0.588235"))
                .setValueEdge(new BigDecimal("0.061765"))
                .setExpectedValue(new BigDecimal("0.105000"))
                .setValueRating(ValueRating.VALUE);
        ResolvedPredictionRequest request = resolve(request(SelectionStrategy.VALUE));

        PredictionCandidateFilterResult result = filter.filterAndRank(
                List.of(sixtyFivePercentSmallValue, fiftyFivePercentExcellentValue),
                request
        );

        assertThat(result.candidates()).extracting(candidate -> candidate.selection().getId())
                .containsExactly(fiftyFivePercentExcellentValue.getId(), sixtyFivePercentSmallValue.getId());
    }

    @Test
    void longshotRejectsHighOddsWithWeakStatisticalSupport() {
        PredictionSelection highOddsWeakSupport = selection(1, LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, "0.350000")
                .setConfidenceBand(PredictionConfidenceBand.LOW)
                .setModelQualitySnapshot(quality(LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, 8, "0.300000", "0.300000", PredictionConfidenceBand.LOW))
                .setBestDecimalOdds(new BigDecimal("10.0000"))
                .setBestImpliedProbability(new BigDecimal("0.100000"))
                .setValueEdge(new BigDecimal("0.250000"))
                .setExpectedValue(new BigDecimal("2.500000"))
                .setValueRating(ValueRating.STRONG_VALUE);
        ResolvedPredictionRequest request = resolve(request(SelectionStrategy.LONGSHOT));

        PredictionCandidateFilterResult result = filter.filterAndRank(List.of(highOddsWeakSupport), request);

        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void highConfidenceRejectsUnratedPredictions() {
        PredictionSelection unrated = selection(1, LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, "0.880000")
                .setConfidenceBand(PredictionConfidenceBand.UNRATED)
                .setModelQualitySnapshot(quality(LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, 150, "0.030000", "0.040000", PredictionConfidenceBand.UNRATED));
        ResolvedPredictionRequest request = resolve(request(SelectionStrategy.HIGH_CONFIDENCE));

        PredictionCandidateFilterResult result = filter.filterAndRank(List.of(unrated), request);

        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void customStrategyUsesUserOverrides() {
        PredictionSelection customValue = selection(1, LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, "0.350000")
                .setBestDecimalOdds(new BigDecimal("3.2000"))
                .setBestImpliedProbability(new BigDecimal("0.312500"))
                .setValueEdge(new BigDecimal("0.037500"))
                .setExpectedValue(new BigDecimal("0.120000"))
                .setValueRating(ValueRating.VALUE);
        ResolvedPredictionRequest request = resolve(request(
                SelectionStrategy.CUSTOM,
                1,
                5,
                1,
                new BigDecimal("0.30"),
                new BigDecimal("0.40"),
                new BigDecimal("2.00"),
                null,
                PredictionConfidenceBand.MEDIUM,
                new BigDecimal("0.50"),
                10,
                new BigDecimal("0.05"),
                new BigDecimal("0.02"),
                RankingMode.EXPECTED_VALUE
        ));

        PredictionCandidateFilterResult result = filter.filterAndRank(List.of(customValue), request);

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().getFirst().rankingMode()).isEqualTo(RankingMode.EXPECTED_VALUE);
    }

    @Test
    void doesNotWeakenDataQualityToFillRequestedMinimum() {
        PredictionSelection lowQualityHighProbability = selection(1, LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, "0.840000")
                .setModelQualitySnapshot(quality(LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, 80, "0.400000", "0.400000", PredictionConfidenceBand.HIGH));
        PredictionSelection qualified = selection(2, LeagueCode.LA_LIGA, MarketCode.HOME_WIN, "0.820000");
        ResolvedPredictionRequest request = resolve(request(
                SelectionStrategy.LOWER_RISK,
                2,
                5,
                1,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("0.90"),
                null,
                null,
                null,
                null
        ));

        PredictionCandidateFilterResult result = filter.filterAndRank(List.of(lowQualityHighProbability, qualified), request);

        assertThat(result.candidates()).extracting(candidate -> candidate.selection().getId())
                .containsExactly(qualified.getId());
        assertThat(result.qualifiedSelectionsFound()).isEqualTo(1);
    }

    @Test
    void reducedHistoricalDepthLowersDataQualityEligibility() {
        PredictionSelection reducedDepth = selection(1, LeagueCode.PREMIER_LEAGUE, MarketCode.HOME_WIN, "0.840000")
                .setRequestedSeasonCount(3)
                .setActualSeasonCountUsed(1)
                .setFallbackApplied(true);
        PredictionSelection fullDepth = selection(2, LeagueCode.LA_LIGA, MarketCode.HOME_WIN, "0.820000")
                .setRequestedSeasonCount(3)
                .setActualSeasonCountUsed(3)
                .setFallbackApplied(false);
        ResolvedPredictionRequest request = resolve(request(SelectionStrategy.LOWER_RISK));

        PredictionCandidateFilterResult result = filter.filterAndRank(List.of(reducedDepth, fullDepth), request);

        assertThat(result.candidates()).extracting(candidate -> candidate.selection().getId())
                .containsExactly(fullDepth.getId());
    }

    @Test
    void validationRejectsInvalidSelectionRangesAndLeagueDiversity() {
        assertThatThrownBy(() -> resolve(request(
                SelectionStrategy.BALANCED,
                5,
                4,
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ))).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("maximumSelections must be greater than or equal to minimumSelections");

        PredictionRequest tooManyDistinctLeagues = new PredictionRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE),
                Set.of(MarketCode.HOME_WIN),
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-03"),
                null,
                null,
                ValueMode.ALL,
                "FOOTBALL",
                SelectionStrategy.BALANCED,
                1,
                5,
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                1,
                null,
                null,
                true,
                2,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> resolve(tooManyDistinctLeagues))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("minimumDistinctLeagues cannot exceed the number of selected leagues");
    }

    private ResolvedPredictionRequest resolve(PredictionRequest request) {
        return PredictionStrategyResolver.resolve(request, properties);
    }

    private PredictionRequest request(SelectionStrategy strategy) {
        return request(strategy, 1, 5, 1, null, null, null, null, null, null, null, null, null, null);
    }

    private PredictionRequest request(
            SelectionStrategy strategy,
            int minimumSelections,
            int maximumSelections,
            int numberOfBatches,
            BigDecimal minimumModelProbability,
            BigDecimal maximumModelProbability,
            BigDecimal minimumDecimalOdds,
            BigDecimal maximumDecimalOdds,
            PredictionConfidenceBand minimumConfidence,
            BigDecimal minimumDataQuality,
            Integer minimumHistoricalSample,
            BigDecimal minimumExpectedValue,
            BigDecimal minimumProbabilityEdge,
            RankingMode rankingMode
    ) {
        return new PredictionRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE, LeagueCode.LA_LIGA, LeagueCode.SERIE_A),
                Set.of(MarketCode.HOME_WIN, MarketCode.OVER_2_5_GOALS, MarketCode.BTTS_YES),
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-03"),
                null,
                null,
                ValueMode.ALL,
                "FOOTBALL",
                strategy,
                minimumSelections,
                maximumSelections,
                numberOfBatches,
                minimumModelProbability,
                maximumModelProbability,
                minimumDecimalOdds,
                maximumDecimalOdds,
                minimumConfidence,
                minimumDataQuality,
                minimumHistoricalSample,
                minimumExpectedValue,
                minimumProbabilityEdge,
                null,
                null,
                false,
                1,
                null,
                null,
                true,
                2,
                false,
                true,
                false,
                new BigDecimal("0.40"),
                new BigDecimal("0.50"),
                rankingMode,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private PredictionSelection selection(int index, LeagueCode leagueCode, MarketCode marketCode, String probability) {
        PredictionSelection selection = new PredictionSelection()
                .setMatch(match(index, leagueCode))
                .setMarketDefinition(market(marketCode))
                .setPredictedValue(marketCode.getSelectionValue())
                .setProbability(new BigDecimal(probability))
                .setRawProbability(new BigDecimal(probability))
                .setModelVersion("test-model")
                .setCorrelationGroupKey("group-" + index + "-" + marketCode)
                .setConfidenceBand(PredictionConfidenceBand.HIGH)
                .setModelQualitySnapshot(quality(leagueCode, marketCode, 120, "0.050000", "0.050000", PredictionConfidenceBand.HIGH))
                .setOutcome(PredictionOutcome.PENDING);
        selection.setId(UUID.randomUUID());
        return selection;
    }

    private ModelQualitySnapshot quality(
            LeagueCode leagueCode,
            MarketCode marketCode,
            int sampleSize,
            String calibrationError,
            String brierScore,
            PredictionConfidenceBand confidenceBand
    ) {
        ModelQualitySnapshot quality = new ModelQualitySnapshot()
                .setLeague(league(leagueCode))
                .setMarketDefinition(market(marketCode))
                .setModelVersion("test-model")
                .setQualityDate(LocalDate.parse("2026-07-31"))
                .setSampleSize(sampleSize)
                .setWonCount(60)
                .setLostCount(40)
                .setVoidCount(0)
                .setObservedWinRate(new BigDecimal("0.600000"))
                .setAverageRawProbability(new BigDecimal("0.600000"))
                .setBrierScore(new BigDecimal(brierScore))
                .setCalibrationError(new BigDecimal(calibrationError))
                .setProbabilityAdjustment(BigDecimal.ZERO)
                .setConfidenceBand(confidenceBand);
        quality.setId(UUID.randomUUID());
        return quality;
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

    private Match match(int index, LeagueCode leagueCode) {
        League league = league(leagueCode);
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
        match.setId(UUID.nameUUIDFromBytes(("match-" + index + "-" + leagueCode).getBytes()));
        return match;
    }

    private League league(LeagueCode leagueCode) {
        League league = new League()
                .setCode(leagueCode)
                .setName(leagueCode.getDisplayName())
                .setCountry(leagueCode.getCountry())
                .setTier(leagueCode.getTier())
                .setActive(true)
                .setScrapeEnabled(true)
                .setCurrentSeason("2026/2027");
        league.setId(UUID.nameUUIDFromBytes(("league-" + leagueCode).getBytes()));
        return league;
    }

    private Team team(League league, String name, String shortName) {
        Team team = new Team()
                .setLeague(league)
                .setCanonicalName(name)
                .setShortName(shortName)
                .setCountry(league.getCountry())
                .setExternalKey("team-" + name)
                .setActive(true);
        team.setId(UUID.nameUUIDFromBytes((league.getCode() + "-" + name).getBytes()));
        return team;
    }
}
