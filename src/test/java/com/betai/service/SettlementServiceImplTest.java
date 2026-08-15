package com.betai.service;

import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.Match;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.domain.statistics.MatchStatistics;
import com.betai.domain.team.Team;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementServiceImplTest {

    private final SettlementServiceImpl service = new SettlementServiceImpl(null, null, null, null, null, null);

    @Test
    void settlesDrawNoBetAsVoidOnDraw() {
        assertThat(settle(selection(MarketCode.HOME_DRAW_NO_BET, 1, 1, null))).isEqualTo(PredictionOutcome.VOID);
        assertThat(settle(selection(MarketCode.AWAY_DRAW_NO_BET, 2, 1, null))).isEqualTo(PredictionOutcome.LOST);
        assertThat(settle(selection(MarketCode.AWAY_DRAW_NO_BET, 1, 2, null))).isEqualTo(PredictionOutcome.WON);
    }

    @Test
    void settlesExpandedGoalMarketsAndCleanSheets() {
        assertThat(settle(selection(MarketCode.HOME_OR_DRAW, 1, 1, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.HOME_OR_AWAY, 1, 1, null))).isEqualTo(PredictionOutcome.LOST);
        assertThat(settle(selection(MarketCode.OVER_4_5_GOALS, 3, 2, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.OVER_7_5_GOALS, 5, 3, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.UNDER_7_5_GOALS, 4, 3, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.UNDER_0_5_GOALS, 0, 0, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.HOME_TEAM_OVER_2_5_GOALS, 3, 1, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.HOME_TEAM_UNDER_2_5_GOALS, 2, 3, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.AWAY_TEAM_OVER_2_5_GOALS, 1, 3, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.AWAY_TEAM_UNDER_2_5_GOALS, 3, 2, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.HOME_TEAM_OVER_4_5_GOALS, 5, 1, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.HOME_TEAM_UNDER_4_5_GOALS, 4, 5, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.AWAY_TEAM_OVER_4_5_GOALS, 1, 5, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.AWAY_TEAM_UNDER_4_5_GOALS, 5, 4, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.HOME_TEAM_CLEAN_SHEET, 2, 0, null))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.AWAY_TEAM_CLEAN_SHEET, 1, 0, null))).isEqualTo(PredictionOutcome.LOST);
    }

    @Test
    void settlesCornerCardAndRedCardMarketsFromValidatedStatistics() {
        MatchStatistics stats = new MatchStatistics()
                .setHomeCorners(6)
                .setAwayCorners(4)
                .setHomeYellowCards(2)
                .setAwayYellowCards(2)
                .setHomeRedCards(0)
                .setAwayRedCards(0);

        assertThat(settle(selection(MarketCode.CORNERS_OVER_9_5, 0, 0, stats))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.CORNERS_UNDER_9_5, 0, 0, stats))).isEqualTo(PredictionOutcome.LOST);
        assertThat(settle(selection(MarketCode.HOME_TEAM_CORNERS_OVER_4_5, 0, 0, stats))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.YELLOW_CARDS_UNDER_4_5, 0, 0, stats))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.RED_CARD_NO, 0, 0, stats))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.RED_CARD_YES, 0, 0, stats))).isEqualTo(PredictionOutcome.LOST);
    }

    @Test
    void settlesExpandedCornerAndCardLinesIncludingPartialTeamStats() {
        MatchStatistics stats = new MatchStatistics()
                .setHomeCorners(6)
                .setAwayCorners(4)
                .setHomeYellowCards(3)
                .setAwayYellowCards(3);

        assertThat(settle(selection(MarketCode.CORNERS_OVER_13_5, 0, 0, stats))).isEqualTo(PredictionOutcome.LOST);
        assertThat(settle(selection(MarketCode.CORNERS_UNDER_13_5, 0, 0, stats))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.HOME_TEAM_CORNERS_OVER_5_5, 0, 0, stats))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.AWAY_TEAM_CORNERS_UNDER_5_5, 0, 0, stats))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.YELLOW_CARDS_OVER_5_5, 0, 0, stats))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.YELLOW_CARDS_UNDER_5_5, 0, 0, stats))).isEqualTo(PredictionOutcome.LOST);

        MatchStatistics partial = new MatchStatistics().setHomeCorners(6);
        assertThat(settle(selection(MarketCode.HOME_TEAM_CORNERS_OVER_5_5, 0, 0, partial))).isEqualTo(PredictionOutcome.WON);
        assertThat(settle(selection(MarketCode.AWAY_TEAM_CORNERS_OVER_2_5, 0, 0, partial))).isEqualTo(PredictionOutcome.VOID);
    }

    @Test
    void periodAndEventMarketsSettleVoidWithoutReliableData() {
        assertThat(settle(selection(MarketCode.FIRST_HALF_OVER_0_5_GOALS, 2, 1, null))).isEqualTo(PredictionOutcome.VOID);
        assertThat(settle(selection(MarketCode.HOME_TEAM_TO_SCORE_FIRST, 2, 1, null))).isEqualTo(PredictionOutcome.VOID);
    }

    private PredictionOutcome settle(PredictionSelection selection) {
        return ReflectionTestUtils.invokeMethod(service, "settleSelection", selection);
    }

    private PredictionSelection selection(MarketCode marketCode, int homeScore, int awayScore, MatchStatistics statistics) {
        Match match = match(homeScore, awayScore);
        if (statistics != null) {
            statistics.setMatch(match);
            match.setStatistics(statistics);
        }
        PredictionSelection selection = new PredictionSelection()
                .setMatch(match)
                .setMarketDefinition(market(marketCode))
                .setPredictedValue(marketCode.getSelectionValue())
                .setProbability(new BigDecimal("0.600000"))
                .setRawProbability(new BigDecimal("0.600000"))
                .setModelVersion("test-model")
                .setCorrelationGroupKey("test")
                .setGeneratedAt(OffsetDateTime.parse("2026-06-14T10:00:00Z"))
                .setOutcome(PredictionOutcome.PENDING);
        selection.setId(UUID.randomUUID());
        return selection;
    }

    private Match match(int homeScore, int awayScore) {
        League league = new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1);
        Team home = new Team().setCanonicalName("Home").setShortName("H").setLeague(league).setCountry("England").setExternalKey("home");
        Team away = new Team().setCanonicalName("Away").setShortName("A").setLeague(league).setCountry("England").setExternalKey("away");
        Match match = new Match()
                .setLeague(league)
                .setHomeTeam(home)
                .setAwayTeam(away)
                .setHomeScore(homeScore)
                .setAwayScore(awayScore)
                .setMatchDate(LocalDate.parse("2026-06-14"))
                .setKickoffAt(OffsetDateTime.parse("2026-06-14T15:00:00Z"))
                .setSeasonLabel("2026")
                .setSourceFixtureKey("fixture");
        match.setId(UUID.randomUUID());
        home.setId(UUID.randomUUID());
        away.setId(UUID.randomUUID());
        return match;
    }

    private MarketDefinition market(MarketCode code) {
        MarketDefinition market = new MarketDefinition()
                .setCode(code)
                .setDisplayName(code.getDisplayName())
                .setMarketType(code.getMarketType())
                .setMarketFamily(code.getMarketType())
                .setDirection(code.getDirection())
                .setSelectionValue(code.getSelectionValue())
                .setThreshold(code.getThreshold())
                .setPeriod(code.getPeriod())
                .setTeamScope(code.getTeamScope())
                .setTargetType(code.getTargetType())
                .setEnabled(code.isEnabled())
                .setActive(code.isEnabled())
                .setMinimumSampleSize(code.getMinimumSampleSize())
                .setSettlementDescription(code.getSettlementDescription());
        market.setId(UUID.randomUUID());
        return market;
    }
}
