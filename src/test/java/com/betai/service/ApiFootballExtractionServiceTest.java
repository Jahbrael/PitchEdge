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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiFootballExtractionServiceTest {

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
                Clock.fixed(Instant.parse("2026-06-18T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void importsApiFootballFinishedAndScheduledFixturesAsMatchDataOnly() {
        League league = league();
        SourceTarget sourceTarget = sourceTarget(league);
        RawSnapshot snapshot = snapshot(league, sourceTarget, """
                {
                  "response": [
                    {
                      "fixture": {
                        "id": 991001,
                        "date": "2026-06-19T17:00:00+00:00",
                        "venue": {"name": "Skonto Stadium"}
                      },
                      "league": {"round": "Regular Season - 19"},
                      "teams": {
                        "home": {"name": "Riga FC"},
                        "away": {"name": "FK Liepaja"}
                      },
                      "goals": {"home": null, "away": null},
                      "score": {"fulltime": {"home": null, "away": null}}
                    },
                    {
                      "fixture": {
                        "id": 991002,
                        "date": "2026-06-15T16:00:00+00:00",
                        "venue": {"name": "Daugava Stadium"}
                      },
                      "league": {"round": "Regular Season - 18"},
                      "teams": {
                        "home": {"name": "Valmiera"},
                        "away": {"name": "RFS"}
                      },
                      "goals": {"home": 1, "away": 2},
                      "score": {"fulltime": {"home": 1, "away": 2}}
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
        assertThat(response.rowsSeen()).isEqualTo(2);
        assertThat(response.rowsAccepted()).isEqualTo(2);
        assertThat(response.statsUpserted()).isZero();
        List<Match> savedMatches = matchCaptor.getAllValues();
        assertThat(savedMatches).hasSize(2);
        assertThat(savedMatches.get(0).getStatus()).isEqualTo(MatchStatus.SCHEDULED);
        assertThat(savedMatches.get(0).getSourceFixtureKey()).isEqualTo("APIF:LATVIAN_VIRSLIGA:991001");
        assertThat(savedMatches.get(0).getKickoffAt()).isEqualTo(OffsetDateTime.parse("2026-06-19T17:00:00Z"));
        assertThat(savedMatches.get(1).getStatus()).isEqualTo(MatchStatus.FINISHED);
        assertThat(savedMatches.get(1).getHomeScore()).isEqualTo(1);
        assertThat(savedMatches.get(1).getAwayScore()).isEqualTo(2);
        verify(matchStatisticsRepository, never()).save(any());
    }

    private League league() {
        League league = new League()
                .setCode(LeagueCode.LATVIAN_VIRSLIGA)
                .setName("Latvian Virsliga")
                .setCountry("Latvia")
                .setTier(1)
                .setCurrentSeason("2026");
        return withId(league);
    }

    private SourceTarget sourceTarget(League league) {
        SourceTarget sourceTarget = new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.MATCH_DATA)
                .setName("API-Football 2026 Latvian Virsliga Match Data JSON")
                .setUrlTemplate("https://v3.football.api-sports.io/fixtures?league=365&season=2026")
                .setTargetSeasonLabel("2026")
                .setRenderMode(RenderMode.STATIC_HTML)
                .setSelectorsJson("{\"format\":\"api-football-fixtures-json\"}");
        return withId(sourceTarget);
    }

    private RawSnapshot snapshot(League league, SourceTarget sourceTarget, String payload) {
        RawSnapshot snapshot = new RawSnapshot()
                .setLeague(league)
                .setSourceTarget(sourceTarget)
                .setSnapshotDate(LocalDate.of(2026, 6, 18))
                .setSourceUrl(sourceTarget.getUrlTemplate())
                .setScrapeStatus(ScrapeStatus.SUCCESS)
                .setRawPayload(payload)
                .setFetchedAt(OffsetDateTime.parse("2026-06-18T10:00:00Z"));
        return withId(snapshot);
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
