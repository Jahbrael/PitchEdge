package com.betai.service;

import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.Match;
import com.betai.domain.odds.Bookmaker;
import com.betai.domain.odds.OddsSnapshot;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.domain.team.Team;
import com.betai.repository.OddsSnapshotRepository;
import com.betai.repository.PredictionSelectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OddsValueServiceImplTest {

    @Mock
    private OddsSnapshotRepository oddsSnapshotRepository;
    @Mock
    private PredictionSelectionRepository predictionSelectionRepository;

    @Test
    void applyingOddsDoesNotChangeRawOrTunedModelProbability() {
        MarketDefinition market = market(MarketCode.HOME_WIN);
        Match match = match();
        PredictionSelection selection = new PredictionSelection()
                .setMatch(match)
                .setMarketDefinition(market)
                .setPredictedValue("HOME")
                .setRawProbability(new BigDecimal("0.550000"))
                .setProbability(new BigDecimal("0.570000"))
                .setModelVersion("test-model")
                .setGeneratedAt(OffsetDateTime.parse("2026-06-14T10:00:00Z"))
                .setCorrelationGroupKey("test")
                .setOutcome(PredictionOutcome.PENDING);
        selection.setId(UUID.randomUUID());
        OddsSnapshot oddsSnapshot = new OddsSnapshot()
                .setMatch(match)
                .setMarketDefinition(market)
                .setBookmaker(new Bookmaker().setCode("BET365").setDisplayName("Bet365"))
                .setDecimalOdds(new BigDecimal("4.0000"))
                .setImpliedProbability(new BigDecimal("0.250000"))
                .setCapturedAt(OffsetDateTime.parse("2026-06-14T09:00:00Z"))
                .setSourceName("test");

        when(oddsSnapshotRepository.findCurrentBookmakerQuotes(match.getId(), market.getId(), PageRequest.of(0, 1)))
                .thenReturn(List.of(oddsSnapshot));

        OddsValueServiceImpl service = new OddsValueServiceImpl(
                oddsSnapshotRepository,
                predictionSelectionRepository,
                new OddsValueCalculator(),
                Clock.fixed(Instant.parse("2026-06-14T11:00:00Z"), ZoneOffset.UTC)
        );

        service.applyBestOdds(selection);

        assertThat(selection.getRawProbability()).isEqualByComparingTo("0.550000");
        assertThat(selection.getProbability()).isEqualByComparingTo("0.570000");
        assertThat(selection.getBestDecimalOdds()).isEqualByComparingTo("4.0000");
        assertThat(selection.getExpectedValue()).isEqualByComparingTo("1.280000");
    }

    private Match match() {
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
