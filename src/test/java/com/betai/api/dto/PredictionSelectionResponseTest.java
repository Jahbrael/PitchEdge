package com.betai.api.dto;

import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.Match;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.domain.team.Team;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionSelectionResponseTest {

    @Test
    void mapsMarketMetadataAndOddsValueFields() {
        PredictionSelection selection = new PredictionSelection()
                .setMatch(match())
                .setMarketDefinition(market(MarketCode.HOME_TEAM_OVER_1_5_GOALS))
                .setPredictedValue("OVER")
                .setRawProbability(new BigDecimal("0.620000"))
                .setProbability(new BigDecimal("0.640000"))
                .setBestDecimalOdds(new BigDecimal("1.9000"))
                .setBestImpliedProbability(new BigDecimal("0.526316"))
                .setValueEdge(new BigDecimal("0.113684"))
                .setExpectedValue(new BigDecimal("0.216000"))
                .setModelVersion("test-model")
                .setGeneratedAt(OffsetDateTime.parse("2026-06-14T10:00:00Z"))
                .setCorrelationGroupKey("test")
                .setOutcome(PredictionOutcome.PENDING);
        selection.setId(UUID.randomUUID());

        PredictionSelectionResponse response = PredictionSelectionResponse.from(selection);

        assertThat(response.marketCode()).isEqualTo("HOME_TEAM_OVER_1_5_GOALS");
        assertThat(response.marketFamily()).isEqualTo("TEAM_TOTAL_GOALS");
        assertThat(response.period()).isEqualTo("FULL_TIME");
        assertThat(response.direction()).isEqualTo("OVER");
        assertThat(response.threshold()).isEqualByComparingTo("1.50");
        assertThat(response.teamScope()).isEqualTo("HOME_TEAM");
        assertThat(response.rawModelProbability()).isEqualByComparingTo("0.620000");
        assertThat(response.tunedModelProbability()).isEqualByComparingTo("0.640000");
        assertThat(response.decimalOdds()).isEqualByComparingTo("1.9000");
        assertThat(response.expectedValue()).isEqualByComparingTo("0.216000");
        assertThat(response.leagueBadgeUrl()).isEqualTo("https://cdn.test/league-badge.png");
        assertThat(response.leagueLogoUrl()).isEqualTo("https://cdn.test/league-logo.png");
        assertThat(response.homeTeamBadgeUrl()).isEqualTo("https://cdn.test/home-badge.png");
        assertThat(response.homeTeamLogoUrl()).isEqualTo("https://cdn.test/home-logo.png");
        assertThat(response.awayTeamBadgeUrl()).isEqualTo("https://cdn.test/away-badge.png");
        assertThat(response.awayTeamLogoUrl()).isEqualTo("https://cdn.test/away-logo.png");
    }

    private Match match() {
        League league = new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1)
                .setBadgeUrl("https://cdn.test/league-badge.png")
                .setLogoUrl("https://cdn.test/league-logo.png");
        Team home = new Team().setCanonicalName("Home").setShortName("H").setLeague(league).setCountry("England").setExternalKey("home")
                .setBadgeUrl("https://cdn.test/home-badge.png")
                .setLogoUrl("https://cdn.test/home-logo.png");
        Team away = new Team().setCanonicalName("Away").setShortName("A").setLeague(league).setCountry("England").setExternalKey("away")
                .setBadgeUrl("https://cdn.test/away-badge.png")
                .setLogoUrl("https://cdn.test/away-logo.png");
        Match match = new Match()
                .setLeague(league)
                .setHomeTeam(home)
                .setAwayTeam(away)
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
