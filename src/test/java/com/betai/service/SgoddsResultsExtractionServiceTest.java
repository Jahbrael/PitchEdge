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
import com.betai.util.SnapshotPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SgoddsResultsExtractionServiceTest {

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
    void importsBinarySgoddsResultCsvWithKLeagueAliases() {
        League league = league();
        SourceTarget sourceTarget = sourceTarget(league);
        String csv = """
                ID,Match,"Start Time",League,Live Bet,Result,Ft1X2_01,Ft1X2_02,Ft1X2_03
                4185,"Gangwon vs Ulsan","2026-05-17 18:00:00","K League",0,"HT:2-0, FT:2-0",2.20,3.05,2.95
                """;
        RawSnapshot snapshot = snapshot(league, sourceTarget, SnapshotPayloads.encodeBinary(csv.getBytes(StandardCharsets.UTF_8)));
        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);

        when(rawSnapshotRepository.findById(snapshot.getId())).thenReturn(Optional.of(snapshot));
        when(extractionRunRepository.save(any(ExtractionRun.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(extractionValidationErrorRepository.findTop100ByExtractionRun_IdOrderByRowNumberAsc(any())).thenReturn(List.of());
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(any(), any())).thenReturn(Optional.empty());
        when(teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(any(), any())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(matchRepository.findByLeague_CodeAndSourceFixtureKeySafely(any(), any())).thenReturn(Optional.empty());
        when(matchRepository.findByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndMatchDateSafely(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(matchRepository.save(matchCaptor.capture())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        var response = service.extractRawSnapshot(snapshot.getId(), true);

        assertThat(response.status()).isEqualTo(ExtractionStatus.SUCCESS);
        assertThat(response.rowsSeen()).isEqualTo(1);
        assertThat(response.rowsAccepted()).isEqualTo(1);
        Match saved = matchCaptor.getValue();
        assertThat(saved.getHomeTeam().getCanonicalName()).isEqualTo("Gangwon FC");
        assertThat(saved.getAwayTeam().getCanonicalName()).isEqualTo("Ulsan HD");
        assertThat(saved.getStatus()).isEqualTo(MatchStatus.FINISHED);
        assertThat(saved.getHomeScore()).isEqualTo(2);
        assertThat(saved.getAwayScore()).isEqualTo(0);
        assertThat(saved.getMatchDate()).isEqualTo(LocalDate.of(2026, 5, 17));
    }

    private League league() {
        League league = new League()
                .setCode(LeagueCode.K_LEAGUE_1)
                .setName("K League 1")
                .setCountry("South Korea")
                .setTier(1)
                .setCurrentSeason("2026");
        return withId(league);
    }

    private SourceTarget sourceTarget(League league) {
        SourceTarget sourceTarget = new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.RESULTS)
                .setName("Sgodds Results K League CSV")
                .setUrlTemplate("https://sgodds.com/downloads/sgodds-1781398081-k-league.csv")
                .setRenderMode(RenderMode.STATIC_HTML)
                .setSelectorsJson("{\"format\":\"sgodds-results-csv\",\"leagueName\":\"K League\"}");
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
