package com.betai.service;

import com.betai.api.dto.details.FixturePredictionDetailsResponse;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.market.MarketType;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.statistics.MatchStatistics;
import com.betai.domain.team.Team;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.PredictionSelectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixturePredictionDetailsServiceImplTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private PredictionSelectionRepository predictionSelectionRepository;

    @Mock
    private MarketDefinitionRepository marketDefinitionRepository;

    @Mock
    private PredictionRunCacheService predictionRunCacheService;

    @InjectMocks
    private FixturePredictionDetailsServiceImpl service;

    private UUID matchId;
    private League league;
    private Team home;
    private Team away;
    private Match match;
    private MarketDefinition homeWinMarket;
    private MarketDefinition awayWinMarket;

    @BeforeEach
    void setUp() {
        matchId = UUID.randomUUID();

        league = new League().setCode(LeagueCode.PREMIER_LEAGUE).setName("Premier League")
                .setCountry("England")
                .setTier(1)
                .setCurrentSeason("2025/2026")
                .setBadgeUrl("https://cdn.test/premier-league-badge.png")
                .setLogoUrl("https://cdn.test/premier-league-logo.png");
        league.setId(UUID.randomUUID());
        home = team("Arsenal")
                .setBadgeUrl("https://cdn.test/arsenal-badge.png")
                .setLogoUrl("https://cdn.test/arsenal-logo.png");
        away = team("Chelsea")
                .setBadgeUrl("https://cdn.test/chelsea-badge.png")
                .setLogoUrl("https://cdn.test/chelsea-logo.png");

        match = new Match();
        match.setId(matchId);
        match.setLeague(league);
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setMatchDate(LocalDate.of(2026, 7, 4));
        match.setKickoffAt(OffsetDateTime.of(match.getMatchDate(), LocalTime.of(15, 0), ZoneOffset.UTC));
        match.setStatus(MatchStatus.SCHEDULED);
        match.setSeasonLabel("2025/2026");
        match.setSourceFixtureKey("fixture-" + matchId);
        match.setVenue("Emirates");

        homeWinMarket = new MarketDefinition();
        homeWinMarket.setId(UUID.randomUUID());
        homeWinMarket.setCode(MarketCode.HOME_WIN);
        homeWinMarket.setDisplayName("Home Win");
        homeWinMarket.setMarketFamily(MarketType.MATCH_RESULT);

        awayWinMarket = new MarketDefinition();
        awayWinMarket.setId(UUID.randomUUID());
        awayWinMarket.setCode(MarketCode.AWAY_WIN);
        awayWinMarket.setDisplayName("Away Win");
        awayWinMarket.setMarketFamily(MarketType.MATCH_RESULT);
    }

    @Test
    void shouldReturnFixtureDetailsWithAllCalculatedMarketsAndMissingMarkets() {
        PredictionSelection selection = new PredictionSelection();
        selection.setMatch(match);
        selection.setMarketDefinition(homeWinMarket);
        selection.setProbability(new BigDecimal("0.55"));
        selection.setRawProbability(new BigDecimal("0.55"));
        selection.setConfidenceBand(PredictionConfidenceBand.HIGH);
        selection.setGeneratedAt(OffsetDateTime.now());
        selection.setModelVersion("v1");

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(marketDefinitionRepository.findByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of(homeWinMarket, awayWinMarket));
        when(predictionSelectionRepository.findByMatch_IdAndMarketDefinition_EnabledTrueOrderByProbabilityDesc(matchId))
                .thenReturn(List.of(selection));
        List<Match> homeRecent = List.of(
                finishedMatch(home, team("Manchester City"), 2, 0, LocalDate.of(2026, 6, 28)),
                finishedMatch(team("Tottenham"), home, 1, 1, LocalDate.of(2026, 6, 21)),
                finishedMatch(home, away, 3, 1, LocalDate.of(2026, 6, 14)),
                finishedMatch(team("Liverpool"), home, 2, 0, LocalDate.of(2026, 6, 7)),
                finishedMatch(home, team("Everton"), 1, 0, LocalDate.of(2026, 5, 31))
        );
        List<Match> awayRecent = List.of(
                finishedMatch(away, team("West Ham"), 1, 0, LocalDate.of(2026, 6, 27)),
                finishedMatch(team("Brighton"), away, 2, 2, LocalDate.of(2026, 6, 20)),
                finishedMatch(home, away, 3, 1, LocalDate.of(2026, 6, 14)),
                finishedMatch(away, team("Fulham"), 0, 0, LocalDate.of(2026, 6, 6)),
                finishedMatch(team("Aston Villa"), away, 1, 2, LocalDate.of(2026, 5, 30))
        );
        List<Match> h2h = List.of(
                finishedMatch(home, away, 3, 1, LocalDate.of(2026, 6, 14)),
                finishedMatch(away, home, 0, 0, LocalDate.of(2026, 1, 12))
        );
        match.setStatus(MatchStatus.LIVE);
        match.setLiveMinute("64'");
        match.setScoreRefreshedAt(OffsetDateTime.of(2026, 7, 4, 16, 4, 0, 0, ZoneOffset.UTC));
        match.setStatistics(new MatchStatistics()
                .setMatch(match)
                .setHomePossession(58)
                .setAwayPossession(42)
                .setHomeShots(11)
                .setAwayShots(6)
                .setHomeShotsOnTarget(5)
                .setAwayShotsOnTarget(2)
                .setHomeCorners(4)
                .setAwayCorners(1)
                .setHomeYellowCards(1)
                .setAwayYellowCards(3)
        );
        List<Match> seasonMatches = new ArrayList<>();
        seasonMatches.addAll(homeRecent);
        seasonMatches.addAll(awayRecent);
        seasonMatches.add(finishedMatch(home, team("Newcastle"), 2, 1, LocalDate.of(2026, 5, 16)));
        seasonMatches.add(finishedMatch(away, team("Leeds"), 1, 2, LocalDate.of(2026, 5, 15)));
        when(matchRepository.findRecentFinishedMatchesByTeamId(eq(home.getId()), eq(match.getMatchDate()))).thenReturn(homeRecent);
        when(matchRepository.findRecentFinishedMatchesByTeamId(eq(away.getId()), eq(match.getMatchDate()))).thenReturn(awayRecent);
        when(matchRepository.findHeadToHeadMatches(eq(home.getId()), eq(away.getId()), eq(match.getMatchDate()))).thenReturn(h2h);
        when(matchRepository.findFinishedMatchesForFeatureGeneration(eq(LeagueCode.PREMIER_LEAGUE), eq("2025/2026"), eq(match.getMatchDate())))
                .thenReturn(seasonMatches);

        FixturePredictionDetailsResponse response = service.getFixtureDetails(matchId, "v1", "HOME_WIN");

        assertNotNull(response);
        assertEquals("Arsenal", response.fixture().homeTeam());
        assertEquals("https://cdn.test/arsenal-badge.png", response.fixture().homeTeamBadgeUrl());
        assertEquals("https://cdn.test/arsenal-logo.png", response.fixture().homeTeamLogoUrl());
        assertEquals("https://cdn.test/chelsea-badge.png", response.fixture().awayTeamBadgeUrl());
        assertEquals("https://cdn.test/chelsea-logo.png", response.fixture().awayTeamLogoUrl());
        assertEquals("https://cdn.test/premier-league-badge.png", response.fixture().leagueBadgeUrl());
        assertEquals("https://cdn.test/premier-league-logo.png", response.fixture().leagueLogoUrl());
        assertEquals(1, response.markets().size());
        assertEquals("HOME_WIN", response.markets().get(0).marketCode());
        assertTrue(response.markets().get(0).qualified()); // it is recommended

        assertEquals(1, response.unavailableMarkets().size());
        assertEquals("AWAY_WIN", response.unavailableMarkets().get(0).marketCode());
        assertFalse(response.unavailableMarkets().get(0).available());
        assertEquals(5, response.homeLast5().size());
        assertEquals(5, response.awayLast5().size());
        assertFalse(response.homeLast5Home().isEmpty());
        assertTrue(response.homeLast5Home().stream().allMatch(recentMatch -> "HOME".equals(recentMatch.homeOrAway())));
        assertFalse(response.awayLast5Away().isEmpty());
        assertTrue(response.awayLast5Away().stream().allMatch(recentMatch -> "AWAY".equals(recentMatch.homeOrAway())));
        assertNotNull(response.ranking());
        assertTrue(response.ranking().available());
        assertEquals("Calculated from local finished matches", response.ranking().sourceLabel());
        assertTrue(response.ranking().rows().stream().anyMatch(FixturePredictionDetailsResponse.TeamStandingDto::currentFixtureTeam));
        assertNotNull(response.preMatchStats());
        assertEquals(8, response.preMatchStats().overUnderGoals().size());
        assertEquals("2.5", response.preMatchStats().overUnderGoals().get(2).line());
        assertEquals("7.5", response.preMatchStats().overUnderGoals().get(7).line());
        assertTrue(response.preMatchStats().homeBttsYes().sampleSize() > 0);
        assertFalse(response.trends().isEmpty());
        assertTrue(response.trends().stream().anyMatch(trend -> "BTTS".equals(trend.category())));
        assertNotNull(response.matchPreview());
        assertTrue(response.matchPreview().text().contains("Arsenal"));
        assertEquals(2, response.headToHead().totalMatches());
        assertEquals(2.0, response.headToHead().avgGoals());
        assertEquals(new FixturePredictionDetailsResponse.H2hOccurrenceDto(1, 2), response.headToHead().over15());
        assertEquals(new FixturePredictionDetailsResponse.H2hOccurrenceDto(1, 2), response.headToHead().over35());
        assertEquals(new FixturePredictionDetailsResponse.H2hOccurrenceDto(1, 2), response.headToHead().under35());
        assertEquals(new FixturePredictionDetailsResponse.H2hOccurrenceDto(2, 2), response.headToHead().under45());
        assertEquals(new FixturePredictionDetailsResponse.H2hOccurrenceDto(1, 2), response.headToHead().homeScored());
        assertEquals(new FixturePredictionDetailsResponse.H2hOccurrenceDto(1, 2), response.headToHead().awayScored());
        assertEquals(new FixturePredictionDetailsResponse.H2hOccurrenceDto(1, 2), response.headToHead().under25());
        assertEquals(new FixturePredictionDetailsResponse.H2hOccurrenceDto(1, 2), response.headToHead().noCleanSheet());
        assertNotNull(response.liveStats());
        assertTrue(response.liveStats().available());
        assertEquals("Live 64'", response.liveStats().statusLabel());
        assertTrue(response.liveStats().rows().stream().anyMatch(row ->
                "POSSESSION".equals(row.code()) && "58%".equals(row.homeValue()) && "42%".equals(row.awayValue())
        ));
        assertTrue(response.liveStats().rows().stream().anyMatch(row ->
                "SHOTS_ON_TARGET".equals(row.code()) && "5".equals(row.homeValue()) && "2".equals(row.awayValue())
        ));
    }

    private Team team(String name) {
        Team team = new Team()
                .setLeague(league)
                .setCanonicalName(name)
                .setShortName(name)
                .setCountry("England")
                .setExternalKey("team-" + name.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + UUID.randomUUID());
        team.setId(UUID.randomUUID());
        return team;
    }

    private Match finishedMatch(Team homeTeam, Team awayTeam, int homeScore, int awayScore, LocalDate date) {
        Match finished = new Match();
        finished.setId(UUID.randomUUID());
        finished.setLeague(league);
        finished.setHomeTeam(homeTeam);
        finished.setAwayTeam(awayTeam);
        finished.setHomeScore(homeScore);
        finished.setAwayScore(awayScore);
        finished.setStatus(MatchStatus.FINISHED);
        finished.setSeasonLabel("2025/2026");
        finished.setMatchDate(date);
        finished.setKickoffAt(OffsetDateTime.of(date, LocalTime.of(15, 0), ZoneOffset.UTC));
        finished.setSourceFixtureKey("finished-" + UUID.randomUUID());
        return finished;
    }
}
