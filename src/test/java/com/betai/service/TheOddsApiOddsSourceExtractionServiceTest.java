package com.betai.service;

import com.betai.api.dto.DailyOddsExtractionRequest;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TheOddsApiOddsSourceExtractionServiceTest {

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
                Clock.fixed(Instant.parse("2026-06-14T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void mapsTheOddsApiH2hAndTotalsToBetAiMarkets() {
        League league = league();
        SourceTarget sourceTarget = sourceTarget(league);
        String payload = """
                [
                  {
                    "id": "event-1",
                    "sport_key": "soccer_sweden_allsvenskan",
                    "commence_time": "2026-07-12T13:00:00Z",
                    "home_team": "GAIS",
                    "away_team": "Elfsborg",
                    "bookmakers": [
                      {
                        "key": "bet365",
                        "title": "Bet365",
                        "last_update": "2026-06-14T09:55:00Z",
                        "markets": [
                          {
                            "key": "h2h",
                            "outcomes": [
                              {"name": "GAIS", "price": 2.40},
                              {"name": "Draw", "price": 3.30},
                              {"name": "Elfsborg", "price": 2.80}
                            ]
                          },
                          {
                            "key": "totals",
                            "outcomes": [
                              {"name": "Over", "price": 1.91, "point": 2.5},
                              {"name": "Under", "price": 1.89, "point": 2.5},
                              {"name": "Over", "price": 2.55, "point": 3.5}
                            ]
                          }
                        ]
                      }
                    ]
                  }
                ]
                """;
        RawSnapshot snapshot = snapshot(league, sourceTarget, payload);
        ArgumentCaptor<OddsImportRequest> requestCaptor = ArgumentCaptor.forClass(OddsImportRequest.class);

        when(rawSnapshotRepository.findById(snapshot.getId())).thenReturn(Optional.of(snapshot));
        when(oddsExtractionRunRepository.save(any(OddsExtractionRun.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(oddsImportService.importOdds(requestCaptor.capture())).thenAnswer(invocation -> {
            OddsImportRequest request = invocation.getArgument(0);
            return new OddsImportResponse(
                    UUID.randomUUID(),
                    OffsetDateTime.parse("2026-06-14T10:00:00Z"),
                    request.odds().size(),
                    request.odds().size(),
                    0,
                    5,
                    List.of(),
                    List.of()
            );
        });

        var response = service.extractRawSnapshot(snapshot.getId(), true, true);

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.rowsSeen()).isEqualTo(1);
        assertThat(response.snapshotsImported()).isEqualTo(5);
        OddsImportRequest request = requestCaptor.getValue();
        assertThat(request.odds()).hasSize(5);
        assertThat(request.odds()).extracting("leagueCode").containsOnly(LeagueCode.ALLSVENSKAN);
        assertThat(request.odds()).extracting("matchDate").containsOnly(LocalDate.parse("2026-07-12"));
        assertThat(request.odds()).extracting("homeTeam").containsOnly("GAIS");
        assertThat(request.odds()).extracting("awayTeam").containsOnly("Elfsborg");
        assertThat(request.odds()).extracting("marketCode")
                .containsExactly(
                        MarketCode.HOME_WIN,
                        MarketCode.DRAW,
                        MarketCode.AWAY_WIN,
                        MarketCode.OVER_2_5_GOALS,
                        MarketCode.UNDER_2_5_GOALS
                );
        assertThat(request.odds().getFirst().bookmakerCode()).isEqualTo("bet365");
        assertThat(request.odds().getFirst().sourceUrl()).contains("apiKey=REDACTED");
    }

    @Test
    void dailyExtractionIgnoresNonTheOddsApiSnapshots() {
        League league = league();
        SourceTarget oldSource = new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.ODDS_REFERENCE)
                .setName("Football-Data Historical Odds Workbook")
                .setUrlTemplate("https://example.test/football-data.csv")
                .setRenderMode(RenderMode.STATIC_HTML);
        withId(oldSource);
        RawSnapshot oldSnapshot = snapshot(league, oldSource, "Date,HomeTeam,AwayTeam,FTHG,FTAG");
        when(leagueRepository.findByCodeInAndActiveTrue(Set.of(LeagueCode.ALLSVENSKAN))).thenReturn(List.of(league));
        when(rawSnapshotRepository.findByLeagueCodeDateStatusAndSourceType(
                LeagueCode.ALLSVENSKAN,
                LocalDate.of(2026, 6, 14),
                ScrapeStatus.SUCCESS,
                SourceType.ODDS_REFERENCE
        )).thenReturn(List.of(oldSnapshot));

        var response = service.extractDailyOddsSnapshots(new DailyOddsExtractionRequest(
                Set.of(LeagueCode.ALLSVENSKAN),
                LocalDate.of(2026, 6, 14),
                true,
                true
        ));

        assertThat(response.oddsExtractionRuns()).isEmpty();
        assertThat(response.warnings()).containsExactly(
                "No successful API ODDS_REFERENCE raw snapshots exist for ALLSVENSKAN on 2026-06-14."
        );
        verifyNoInteractions(oddsImportService);
    }

    private League league() {
        League league = new League()
                .setCode(LeagueCode.ALLSVENSKAN)
                .setName("Allsvenskan")
                .setCountry("Sweden")
                .setTier(1)
                .setCurrentSeason("2026");
        return withId(league);
    }

    private SourceTarget sourceTarget(League league) {
        SourceTarget sourceTarget = new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.ODDS_REFERENCE)
                .setName("The Odds API Upcoming Odds Allsvenskan JSON")
                .setUrlTemplate("https://api.the-odds-api.com/v4/sports/soccer_sweden_allsvenskan/odds")
                .setRenderMode(RenderMode.STATIC_HTML)
                .setSelectorsJson("{\"format\":\"the-odds-api-v4-json\","
                        + "\"sportKey\":\"soccer_sweden_allsvenskan\","
                        + "\"includeOneXTwo\":true,"
                        + "\"includeOverUnder25\":true}");
        return withId(sourceTarget);
    }

    private RawSnapshot snapshot(League league, SourceTarget sourceTarget, String payload) {
        RawSnapshot snapshot = new RawSnapshot()
                .setLeague(league)
                .setSourceTarget(sourceTarget)
                .setSnapshotDate(LocalDate.of(2026, 6, 14))
                .setSourceUrl("https://api.the-odds-api.com/v4/sports/soccer_sweden_allsvenskan/odds?apiKey=REDACTED")
                .setScrapeStatus(ScrapeStatus.SUCCESS)
                .setRawPayload(payload)
                .setFetchedAt(OffsetDateTime.parse("2026-06-14T10:00:00Z"));
        return withId(snapshot);
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
