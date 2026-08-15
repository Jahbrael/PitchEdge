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
import com.betai.domain.statistics.MatchStatistics;
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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorldCupExtractionServiceTest {

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
    void importsWorldCupFixtureJsonWithNeutralVenueAndCanonicalTeamAlias() {
        League league = league();
        SourceTarget sourceTarget = sourceTarget(league);
        RawSnapshot snapshot = snapshot(league, sourceTarget, """
                {
                  "fixtures": [
                    {
                      "date": "2026-06-11",
                      "kickoffUtc": "2026-06-12T02:00:00Z",
                      "stage": "group-stage",
                      "group": "A",
                      "homeTeam": "Korea Republic",
                      "awayTeam": "Czechia",
                      "stadium": "Estadio Akron",
                      "hostCity": "guadalajara"
                    }
                  ]
                }
                """);
        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);

        when(rawSnapshotRepository.findById(snapshot.getId())).thenReturn(Optional.of(snapshot));
        when(extractionRunRepository.save(any(ExtractionRun.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(extractionValidationErrorRepository.findTop100ByExtractionRun_IdOrderByRowNumberAsc(any())).thenReturn(java.util.List.of());
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
        assertThat(saved.getHomeTeam().getCanonicalName()).isEqualTo("South Korea");
        assertThat(saved.getAwayTeam().getCanonicalName()).isEqualTo("Czech Republic");
        assertThat(saved.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
        assertThat(saved.getMatchDate()).isEqualTo(LocalDate.of(2026, 6, 11));
        assertThat(saved.getKickoffAt()).isEqualTo(OffsetDateTime.parse("2026-06-12T02:00:00Z"));
        assertThat(saved.getVenue()).isEqualTo("Estadio Akron, guadalajara");
    }

    @Test
    void importsWorldCupWorkbookFinalsWithTournamentSeasonAndStableKey() throws IOException {
        League league = league();
        SourceTarget sourceTarget = sourceTarget(
                league,
                SourceType.RESULTS,
                "Football-Data World Cup Historical Results Workbook",
                "{\"format\":\"football-data-world-cup-workbook\"}"
        ).setTargetSeasonLabel("2026");
        RawSnapshot snapshot = snapshot(league, sourceTarget, workbookPayload("WorldCup2022"));
        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);

        when(rawSnapshotRepository.findById(snapshot.getId())).thenReturn(Optional.of(snapshot));
        when(extractionRunRepository.save(any(ExtractionRun.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(extractionValidationErrorRepository.findTop100ByExtractionRun_IdOrderByRowNumberAsc(any())).thenReturn(java.util.List.of());
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(any(), any())).thenReturn(Optional.empty());
        when(teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(any(), any())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(matchRepository.findByLeague_CodeAndSourceFixtureKeySafely(any(), any())).thenReturn(Optional.empty());
        when(matchRepository.findByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndKickoffAtSafely(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(matchRepository.findByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndMatchDateSafely(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(matchRepository.save(matchCaptor.capture())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(matchStatisticsRepository.findByMatch_Id(any())).thenReturn(Optional.empty());
        when(matchStatisticsRepository.save(any(MatchStatistics.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        var response = service.extractRawSnapshot(snapshot.getId(), true);

        assertThat(response.status()).isEqualTo(ExtractionStatus.SUCCESS);
        assertThat(response.rowsAccepted()).isEqualTo(1);
        Match saved = matchCaptor.getValue();
        assertThat(saved.getSeasonLabel()).isEqualTo("2022");
        assertThat(saved.getRoundLabel()).isEqualTo("WorldCup2022");
        assertThat(saved.getSourceFixtureKey())
                .isEqualTo("WC-HIST:FIFA_WORLD_CUP_2026:WorldCup2022:2022-11-20:qatar:ecuador");
    }

    private League league() {
        League league = new League()
                .setCode(LeagueCode.FIFA_WORLD_CUP_2026)
                .setName("FIFA World Cup 2026")
                .setCountry("International")
                .setTier(1)
                .setCurrentSeason("2026");
        return withId(league);
    }

    private SourceTarget sourceTarget(League league) {
        return sourceTarget(
                league,
                SourceType.FIXTURES,
                "TheStatsAPI FIFA World Cup 2026 Fixtures JSON",
                "{\"format\":\"world-cup-2026-fixtures-json\"}"
        );
    }

    private SourceTarget sourceTarget(League league, SourceType sourceType, String name, String selectorsJson) {
        SourceTarget sourceTarget = new SourceTarget()
                .setLeague(league)
                .setSourceType(sourceType)
                .setName(name)
                .setUrlTemplate("https://www.thestatsapi.com/world-cup/data/fixtures.json")
                .setRenderMode(RenderMode.STATIC_HTML)
                .setSelectorsJson(selectorsJson);
        return withId(sourceTarget);
    }

    private String workbookPayload(String sheetName) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Date");
            header.createCell(1).setCellValue("Time");
            header.createCell(2).setCellValue("Home");
            header.createCell(3).setCellValue("Away");
            header.createCell(4).setCellValue("HG");
            header.createCell(5).setCellValue("AG");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("2022-11-20");
            row.createCell(1).setCellValue("16:00");
            row.createCell(2).setCellValue("Qatar");
            row.createCell(3).setCellValue("Ecuador");
            row.createCell(4).setCellValue(0);
            row.createCell(5).setCellValue(2);

            workbook.write(output);
            return SnapshotPayloads.encodeBinary(output.toByteArray());
        }
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
