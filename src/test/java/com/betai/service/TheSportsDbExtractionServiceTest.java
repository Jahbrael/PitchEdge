package com.betai.service;

import com.betai.domain.extraction.ExtractionRun;
import com.betai.domain.extraction.ExtractionStatus;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.domain.team.Team;
import com.betai.repository.ExtractionRunRepository;
import com.betai.repository.ExtractionValidationErrorRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.MatchStatisticsRepository;
import com.betai.repository.RawSnapshotRepository;
import com.betai.repository.TeamAliasRepository;
import com.betai.repository.TeamRepository;
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
class TheSportsDbExtractionServiceTest {

    @Mock
    private RawSnapshotRepository rawSnapshotRepository;
    @Mock
    private ExtractionRunRepository extractionRunRepository;
    @Mock
    private ExtractionValidationErrorRepository extractionValidationErrorRepository;
    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamAliasRepository teamAliasRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchStatisticsRepository matchStatisticsRepository;

    private FootballDataCsvExtractionService service;

    @BeforeEach
    void setUp() {
        service = new FootballDataCsvExtractionService(
                rawSnapshotRepository,
                extractionRunRepository,
                extractionValidationErrorRepository,
                leagueRepository,
                teamRepository,
                teamAliasRepository,
                matchRepository,
                matchStatisticsRepository,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-06-14T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void importsTheSportsDbSoccerEventAsFinishedMatch() {
        League league = league(LeagueCode.BESTA_DEILD);
        SourceTarget sourceTarget = sourceTarget(league);
        RawSnapshot snapshot = snapshot(league, sourceTarget, """
                {
                  "events": [
                    {
                      "idEvent": "2419597",
                      "strSport": "Soccer",
                      "strHomeTeam": "Víkingur Reykjavík",
                      "strAwayTeam": "Breiðablik",
                      "dateEvent": "2026-05-31",
                      "strTime": "19:15:00",
                      "intHomeScore": "2",
                      "intAwayScore": "1",
                      "intRound": "9",
                      "strVenue": "Víkingsvöllur"
                    }
                  ]
                }
                """);
        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);

        when(rawSnapshotRepository.findById(snapshot.getId())).thenReturn(Optional.of(snapshot));
        when(extractionRunRepository.save(any(ExtractionRun.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(extractionValidationErrorRepository.findTop100ByExtractionRun_IdOrderByRowNumberAsc(any())).thenReturn(List.of());
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(any(), any())).thenReturn(Optional.empty());
        when(teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(any(), any())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(matchRepository.findByLeague_CodeAndSourceFixtureKeySafely(any(), any())).thenReturn(Optional.empty());
        when(matchRepository.save(matchCaptor.capture())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        var response = service.extractRawSnapshot(snapshot.getId(), true);

        assertThat(response.status()).isEqualTo(ExtractionStatus.SUCCESS);
        assertThat(response.rowsSeen()).isEqualTo(1);
        assertThat(response.rowsAccepted()).isEqualTo(1);
        Match saved = matchCaptor.getValue();
        assertThat(saved.getHomeTeam().getCanonicalName()).isEqualTo("Víkingur Reykjavík");
        assertThat(saved.getAwayTeam().getCanonicalName()).isEqualTo("Breiðablik");
        assertThat(saved.getStatus()).isEqualTo(MatchStatus.FINISHED);
        assertThat(saved.getHomeScore()).isEqualTo(2);
        assertThat(saved.getAwayScore()).isEqualTo(1);
        assertThat(saved.getMatchDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(saved.getKickoffAt()).isEqualTo(OffsetDateTime.parse("2026-05-31T19:15:00Z"));
        assertThat(saved.getSourceFixtureKey()).isEqualTo("TSDB:BESTA_DEILD:2419597");
    }

    @Test
    void importsTheSportsDbUpcomingEventAsScheduledMatch() {
        League league = league(LeagueCode.CANADIAN_PREMIER_LEAGUE);
        SourceTarget sourceTarget = sourceTarget(league)
                .setSourceType(SourceType.FIXTURES)
                .setUrlTemplate("https://www.thesportsdb.com/api/v1/json/123/eventsnextleague.php?id=4820");
        RawSnapshot snapshot = snapshot(league, sourceTarget, """
                {
                  "events": [
                    {
                      "idEvent": "300001",
                      "strSport": "Soccer",
                      "strHomeTeam": "Atletico Ottawa",
                      "strAwayTeam": "York United",
                      "dateEvent": "2026-06-20",
                      "strTimestamp": "2026-06-20T20:00:00+00:00",
                      "intHomeScore": null,
                      "intAwayScore": null,
                      "intRound": "12",
                      "strVenue": "TD Place Stadium"
                    }
                  ]
                }
                """);
        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);

        when(rawSnapshotRepository.findById(snapshot.getId())).thenReturn(Optional.of(snapshot));
        when(extractionRunRepository.save(any(ExtractionRun.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(extractionValidationErrorRepository.findTop100ByExtractionRun_IdOrderByRowNumberAsc(any())).thenReturn(List.of());
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(any(), any())).thenReturn(Optional.empty());
        when(teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(any(), any())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(matchRepository.findByLeague_CodeAndSourceFixtureKeySafely(any(), any())).thenReturn(Optional.empty());
        when(matchRepository.save(matchCaptor.capture())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        var response = service.extractRawSnapshot(snapshot.getId(), true);

        assertThat(response.status()).isEqualTo(ExtractionStatus.SUCCESS);
        Match saved = matchCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
        assertThat(saved.getHomeScore()).isNull();
        assertThat(saved.getAwayScore()).isNull();
        assertThat(saved.getMatchDate()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(saved.getKickoffAt()).isEqualTo(OffsetDateTime.parse("2026-06-20T20:00:00Z"));
        assertThat(saved.getSourceFixtureKey()).isEqualTo("TSDB:CANADIAN_PREMIER_LEAGUE:300001");
    }

    @Test
    void treatsTheSportsDbNullEventsAsSkippedNotFailed() {
        League league = league(LeagueCode.K_LEAGUE_1);
        SourceTarget sourceTarget = sourceTarget(league);
        RawSnapshot snapshot = snapshot(league, sourceTarget, "{\"events\":null}");

        when(rawSnapshotRepository.findById(snapshot.getId())).thenReturn(Optional.of(snapshot));
        when(extractionRunRepository.save(any(ExtractionRun.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(extractionValidationErrorRepository.findTop100ByExtractionRun_IdOrderByRowNumberAsc(any())).thenReturn(List.of());

        var response = service.extractRawSnapshot(snapshot.getId(), true);

        assertThat(response.status()).isEqualTo(ExtractionStatus.SKIPPED);
        assertThat(response.rowsSeen()).isZero();
        assertThat(response.rowsAccepted()).isZero();
    }

    private League league(LeagueCode code) {
        League league = new League()
                .setCode(code)
                .setName(code.getDisplayName())
                .setCountry(code.getCountry())
                .setTier(code.getTier())
                .setCurrentSeason("2026");
        return withId(league);
    }

    private SourceTarget sourceTarget(League league) {
        SourceTarget sourceTarget = new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.RESULTS)
                .setName("TheSportsDB 2026 " + league.getName() + " Events JSON RESULTS")
                .setUrlTemplate("https://www.thesportsdb.com/api/v1/json/123/eventsseason.php?id=4642&s=2026")
                .setRenderMode(RenderMode.STATIC_HTML)
                .setSelectorsJson("{\"format\":\"thesportsdb-events-json\"}");
        return withId(sourceTarget);
    }

    private RawSnapshot snapshot(League league, SourceTarget sourceTarget, String payload) {
        RawSnapshot snapshot = new RawSnapshot()
                .setLeague(league)
                .setSourceTarget(sourceTarget)
                .setSnapshotDate(LocalDate.of(2026, 6, 14))
                .setSourceUrl(sourceTarget.getUrlTemplate())
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
