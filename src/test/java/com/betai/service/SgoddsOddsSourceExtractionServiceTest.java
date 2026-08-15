package com.betai.service;

import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.odds.OddsExtractionRun;
import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.repository.LeagueRepository;
import com.betai.repository.OddsExtractionRunRepository;
import com.betai.repository.RawSnapshotRepository;
import com.betai.util.SnapshotPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SgoddsOddsSourceExtractionServiceTest {

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
    void skipsSgoddsKLeagueSnapshotBecauseTheOddsApiIsAuthoritative() {
        League league = league();
        SourceTarget sourceTarget = sourceTarget(league);
        String csv = """
                ID,Match,"Start Time",League,Live Bet,Result,Ft1X2_01,Ft1X2_02,Ft1X2_03,Ou_hcap,Ou_01,Ou_02
                4185,"Gangwon vs Ulsan","2026-05-17 18:00:00","K League",0,"HT:2-0, FT:2-0",2.20,3.05,2.95,2.5,2.07,1.65
                """;
        RawSnapshot snapshot = snapshot(league, sourceTarget, SnapshotPayloads.encodeBinary(csv.getBytes(StandardCharsets.UTF_8)));

        when(rawSnapshotRepository.findById(snapshot.getId())).thenReturn(Optional.of(snapshot));
        when(oddsExtractionRunRepository.save(any(OddsExtractionRun.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        var response = service.extractRawSnapshot(snapshot.getId(), true, true);

        assertThat(response.status()).isEqualTo("SKIPPED");
        assertThat(response.failureReason()).isEqualTo("Only supported API odds snapshots are processed by the active odds pipeline.");
        verifyNoInteractions(oddsImportService);
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
                .setSourceType(SourceType.ODDS_REFERENCE)
                .setName("Sgodds Opening Odds K League CSV")
                .setUrlTemplate("https://sgodds.com/downloads/sgodds-1781398081-k-league.csv")
                .setRenderMode(RenderMode.STATIC_HTML)
                .setSelectorsJson("{\"format\":\"sgodds-opening-odds-csv\","
                        + "\"leagueName\":\"K League\","
                        + "\"bookmakerCode\":\"SGODDS\","
                        + "\"bookmakerName\":\"Sgodds Opening Odds\","
                        + "\"includeOneXTwo\":true,"
                        + "\"includeOverUnder25\":true}");
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
