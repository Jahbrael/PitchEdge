package com.betai.service;

import com.betai.api.dto.OddsImportRequest;
import com.betai.api.dto.OddsImportResponse;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.odds.OddsExtractionRun;
import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.repository.LeagueRepository;
import com.betai.repository.OddsExtractionRunRepository;
import com.betai.repository.RawSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharpApiOddsSourceExtractionServiceTest {

    @Mock
    private RawSnapshotRepository rawSnapshotRepository;
    @Mock
    private OddsExtractionRunRepository oddsExtractionRunRepository;
    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private OddsImportService oddsImportService;

    private OddsSourceExtractionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OddsSourceExtractionServiceImpl(
                rawSnapshotRepository,
                oddsExtractionRunRepository,
                leagueRepository,
                oddsImportService,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-03T21:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void mapsSharpApiCurrentRowShapeUsingRowLevelFixtureFields() {
        League league = league();
        SourceTarget sourceTarget = sourceTarget(league);
        String payload = """
                {
                  "events": [],
                  "data": [
                    {
                      "event_id": "usa_-_major_league_soccer_montreal_toronto_2026-07-16_b3",
                      "home_team": "CF Montréal",
                      "away_team": "Toronto FC",
                      "event_start_time": "2026-07-16T23:30Z",
                      "market_type": "moneyline",
                      "selection": "CF Montreal",
                      "selection_type": "home",
                      "sportsbook": "fanduel",
                      "sportsbook_ref": {"label": "FanDuel"},
                      "odds_decimal": 1.714,
                      "timestamp": "2026-07-03T23:07:28Z"
                    },
                    {
                      "event_id": "usa_-_major_league_soccer_montreal_toronto_2026-07-16_b3",
                      "home_team": "CF Montréal",
                      "away_team": "Toronto FC",
                      "event_start_time": "2026-07-16T23:30Z",
                      "market_type": "moneyline",
                      "selection": "Draw",
                      "selection_type": "draw",
                      "sportsbook": "fanduel",
                      "sportsbook_ref": {"label": "FanDuel"},
                      "odds_decimal": 4.0,
                      "timestamp": "2026-07-03T23:07:28Z"
                    },
                    {
                      "event_id": "usa_-_major_league_soccer_montreal_toronto_2026-07-16_b3",
                      "home_team": "CF Montréal",
                      "away_team": "Toronto FC",
                      "event_start_time": "2026-07-16T23:30Z",
                      "market_type": "moneyline",
                      "selection": "Toronto FC",
                      "selection_type": "away",
                      "sportsbook": "fanduel",
                      "sportsbook_ref": {"label": "FanDuel"},
                      "odds_decimal": 3.6,
                      "timestamp": "2026-07-03T23:07:28Z"
                    },
                    {
                      "event_id": "usa_-_major_league_soccer_montreal_toronto_2026-07-16_b3",
                      "home_team": "CF Montréal",
                      "away_team": "Toronto FC",
                      "event_start_time": "2026-07-16T23:30Z",
                      "market_type": "total_goals",
                      "selection": "Over 2.5",
                      "selection_type": "over",
                      "line": 2.5,
                      "sportsbook": "fanduel",
                      "sportsbook_ref": {"label": "FanDuel"},
                      "odds_decimal": 1.91,
                      "timestamp": "2026-07-03T23:07:28Z"
                    },
                    {
                      "event_id": "usa_-_major_league_soccer_montreal_toronto_2026-07-16_b3",
                      "home_team": "CF Montréal",
                      "away_team": "Toronto FC",
                      "event_start_time": "2026-07-16T23:30Z",
                      "market_type": "total_goals",
                      "selection": "Under 2.5",
                      "selection_type": "under",
                      "line": 2.5,
                      "sportsbook": "fanduel",
                      "sportsbook_ref": {"label": "FanDuel"},
                      "odds_decimal": 1.89,
                      "timestamp": "2026-07-03T23:07:28Z"
                    }
                  ]
                }
                """;
        RawSnapshot snapshot = snapshot(league, sourceTarget, payload);
        ArgumentCaptor<OddsImportRequest> requestCaptor = ArgumentCaptor.forClass(OddsImportRequest.class);

        when(rawSnapshotRepository.findById(snapshot.getId())).thenReturn(Optional.of(snapshot));
        when(oddsExtractionRunRepository.save(any(OddsExtractionRun.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(oddsImportService.importOdds(requestCaptor.capture())).thenAnswer(invocation -> {
            OddsImportRequest request = invocation.getArgument(0);
            return new OddsImportResponse(
                    UUID.randomUUID(),
                    OffsetDateTime.parse("2026-07-03T21:00:00Z"),
                    request.odds().size(),
                    request.odds().size(),
                    0,
                    request.odds().size(),
                    List.of(),
                    List.of()
            );
        });

        var response = service.extractRawSnapshot(snapshot.getId(), true, true);

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.rowsSeen()).isEqualTo(5);
        assertThat(response.snapshotsImported()).isEqualTo(5);
        OddsImportRequest request = requestCaptor.getValue();
        assertThat(request.odds()).hasSize(5);
        assertThat(request.odds()).extracting("leagueCode").containsOnly(LeagueCode.MLS);
        assertThat(request.odds()).extracting("matchDate").containsOnly(LocalDate.parse("2026-07-16"));
        assertThat(request.odds()).extracting("homeTeam").containsOnly("CF Montréal");
        assertThat(request.odds()).extracting("awayTeam").containsOnly("Toronto FC");
        assertThat(request.odds()).extracting("bookmakerName").containsOnly("FanDuel");
        assertThat(request.odds()).extracting("marketCode")
                .containsExactly(
                        MarketCode.HOME_WIN,
                        MarketCode.DRAW,
                        MarketCode.AWAY_WIN,
                        MarketCode.OVER_2_5_GOALS,
                        MarketCode.UNDER_2_5_GOALS
                );
    }

    private League league() {
        League league = new League()
                .setCode(LeagueCode.MLS)
                .setName("Major League Soccer")
                .setCountry("United States")
                .setTier(1)
                .setCurrentSeason("2026");
        return withId(league);
    }

    private SourceTarget sourceTarget(League league) {
        SourceTarget sourceTarget = new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.ODDS_REFERENCE)
                .setName("SharpAPI Upcoming Odds Major League Soccer JSON")
                .setUrlTemplate("{sharpApiBaseUrl}/odds?league=usa_-_major_league_soccer&limit=200")
                .setRenderMode(RenderMode.STATIC_HTML)
                .setSelectorsJson("{\"format\":\"sharpapi-odds-json\","
                        + "\"sportKey\":\"usa_-_major_league_soccer\","
                        + "\"includeOneXTwo\":true,"
                        + "\"includeOverUnder25\":true}");
        return withId(sourceTarget);
    }

    private RawSnapshot snapshot(League league, SourceTarget sourceTarget, String payload) {
        RawSnapshot snapshot = new RawSnapshot()
                .setLeague(league)
                .setSourceTarget(sourceTarget)
                .setSnapshotDate(LocalDate.of(2026, 7, 3))
                .setSourceUrl("https://api.sharpapi.io/api/v1/odds?league=usa_-_major_league_soccer")
                .setScrapeStatus(ScrapeStatus.SUCCESS)
                .setRawPayload(payload)
                .setFetchedAt(OffsetDateTime.parse("2026-07-03T21:00:00Z"));
        return withId(snapshot);
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
