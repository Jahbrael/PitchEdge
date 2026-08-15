package com.betai.service;

import com.betai.api.dto.PredictionBatchResponse;
import com.betai.api.dto.PredictionResponseStatus;
import com.betai.api.dto.PredictionSelectionResponse;
import com.betai.api.dto.RiskBand;
import com.betai.api.dto.SelectionStrategy;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.domain.team.Team;
import com.betai.repository.MatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixtureCardIndicatorServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Test
    void buildsIndicatorsOnlyFromCompletedLocalMatchesAndPersistedCoverage() {
        League league = league(LeagueCode.PREMIER_LEAGUE);
        Team home = team("Arsenal", league);
        Team away = team("Chelsea", league);
        Team other = team("Everton", league);
        Match fixture = match(league, home, away, "2026/2027", "2026-08-10T15:00:00Z", null, null, MatchStatus.SCHEDULED);

        Match h2h = match(league, home, away, "2026/2027", "2026-08-01T15:00:00Z", 2, 0, MatchStatus.FINISHED);
        Match homeDraw = match(league, home, other, "2026/2027", "2026-08-03T15:00:00Z", 1, 1, MatchStatus.FINISHED);
        Match awayWin = match(league, away, other, "2026/2027", "2026-08-05T15:00:00Z", 3, 0, MatchStatus.FINISHED);
        PredictionSelection selection = selection(fixture, "RESULTS:PARTIAL");

        when(matchRepository.findAllForFixtureIndicators(any())).thenReturn(List.of(fixture));
        when(matchRepository.findFinishedMatchesForFixtureIndicators(any(), any()))
                .thenReturn(List.of(awayWin, homeDraw, h2h));
        when(matchRepository.findFinishedLeagueMatchesForFixtureIndicators(any(), any(), any()))
                .thenReturn(List.of(h2h, homeDraw, awayWin));

        var indicators = new FixtureCardIndicatorService(matchRepository)
                .build(List.of(batch(PredictionSelectionResponse.from(selection))))
                .get(selection.getId());

        assertThat(indicators).isNotNull();
        assertThat(indicators.h2hAvailable()).isTrue();
        assertThat(indicators.h2hMatchCount()).isEqualTo(1);
        assertThat(indicators.homeLeaguePosition()).isEqualTo(1);
        assertThat(indicators.awayLeaguePosition()).isEqualTo(2);
        assertThat(indicators.leagueTableTeamCount()).isEqualTo(3);
        assertThat(indicators.partialSeasonData()).isTrue();
        assertThat(indicators.partialSeasonCoverage()).isEqualTo("RESULTS:PARTIAL");
        assertThat(indicators.homeRecentFormPercentage()).isEqualTo(67);
        assertThat(indicators.awayRecentFormPercentage()).isEqualTo(50);
        assertThat(indicators.homeRecentFormSampleSize()).isEqualTo(2);
        assertThat(indicators.awayRecentFormSampleSize()).isEqualTo(2);
    }

    @Test
    void suppressesPositionsForCupCompetitionsAndUnavailableEvidence() {
        League league = league(LeagueCode.FA_CUP);
        Team home = team("Home", league);
        Team away = team("Away", league);
        Match fixture = match(league, home, away, "2026/2027", "2026-08-10T15:00:00Z", null, null, MatchStatus.SCHEDULED);
        PredictionSelection selection = selection(fixture, "RESULTS:FULL");

        when(matchRepository.findAllForFixtureIndicators(any())).thenReturn(List.of(fixture));
        when(matchRepository.findFinishedMatchesForFixtureIndicators(any(), any())).thenReturn(List.of());

        var indicators = new FixtureCardIndicatorService(matchRepository)
                .build(List.of(batch(PredictionSelectionResponse.from(selection))))
                .get(selection.getId());

        assertThat(LeagueCode.FA_CUP.isLeagueCompetition()).isFalse();
        assertThat(indicators.h2hAvailable()).isFalse();
        assertThat(indicators.h2hMatchCount()).isNull();
        assertThat(indicators.homeLeaguePosition()).isNull();
        assertThat(indicators.awayLeaguePosition()).isNull();
        assertThat(indicators.homeRecentFormPercentage()).isNull();
        assertThat(indicators.awayRecentFormPercentage()).isNull();
        assertThat(indicators.partialSeasonData()).isFalse();
    }

    private PredictionBatchResponse batch(PredictionSelectionResponse selection) {
        return new PredictionBatchResponse(
                1,
                SelectionStrategy.BALANCED,
                1,
                1,
                1,
                1,
                new BigDecimal("0.650000"),
                new BigDecimal("0.650000"),
                RiskBand.LOW,
                1,
                1,
                List.of(),
                PredictionResponseStatus.COMPLETE,
                null,
                1,
                null,
                List.of(selection)
        );
    }

    private PredictionSelection selection(Match fixture, String coverage) {
        MarketDefinition market = new MarketDefinition()
                .setCode(MarketCode.HOME_WIN)
                .setDisplayName("Home Win")
                .setMarketType(MarketCode.HOME_WIN.getMarketType())
                .setMarketFamily(MarketCode.HOME_WIN.getMarketType())
                .setPeriod(MarketCode.HOME_WIN.getPeriod())
                .setDirection(MarketCode.HOME_WIN.getDirection())
                .setTeamScope(MarketCode.HOME_WIN.getTeamScope());
        market.setId(UUID.randomUUID());
        PredictionSelection selection = new PredictionSelection()
                .setMatch(fixture)
                .setMarketDefinition(market)
                .setPredictedValue("HOME")
                .setProbability(new BigDecimal("0.650000"))
                .setRawProbability(new BigDecimal("0.650000"))
                .setModelVersion("test-model")
                .setGeneratedAt(OffsetDateTime.parse("2026-08-09T10:00:00Z"))
                .setCorrelationGroupKey("test")
                .setOutcome(PredictionOutcome.PENDING)
                .setMarketSpecificDataCoverage(coverage);
        selection.setId(UUID.randomUUID());
        return selection;
    }

    private League league(LeagueCode code) {
        League league = new League()
                .setCode(code)
                .setName(code.getDisplayName())
                .setCountry(code.getCountry())
                .setTier(code.getTier());
        league.setId(UUID.randomUUID());
        return league;
    }

    private Team team(String name, League league) {
        Team team = new Team()
                .setCanonicalName(name)
                .setShortName(name)
                .setCountry(league.getCountry())
                .setLeague(league)
                .setExternalKey(name.toLowerCase());
        team.setId(UUID.randomUUID());
        return team;
    }

    private Match match(
            League league,
            Team home,
            Team away,
            String season,
            String kickoff,
            Integer homeScore,
            Integer awayScore,
            MatchStatus status
    ) {
        Match match = new Match()
                .setLeague(league)
                .setHomeTeam(home)
                .setAwayTeam(away)
                .setMatchDate(LocalDate.parse(kickoff.substring(0, 10)))
                .setKickoffAt(OffsetDateTime.parse(kickoff))
                .setSeasonLabel(season)
                .setSourceFixtureKey(UUID.randomUUID().toString())
                .setStatus(status)
                .setHomeScore(homeScore)
                .setAwayScore(awayScore);
        match.setId(UUID.randomUUID());
        return match;
    }
}
