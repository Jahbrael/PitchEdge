package com.betai.service;

import com.betai.api.dto.DailyOddsExtractionRequest;
import com.betai.api.dto.DailyOddsExtractionResponse;
import com.betai.api.dto.PreMatchOddsRefreshRequest;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.integration.sharpapi.SharpApiFetchResult;
import com.betai.integration.sharpapi.SharpApiSnapshotClient;
import com.betai.repository.LeagueRepository;
import com.betai.repository.RawSnapshotRepository;
import com.betai.repository.SourceTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreMatchOddsRefreshServiceImplTest {

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private SourceTargetRepository sourceTargetRepository;
    @Mock
    private RawSnapshotRepository rawSnapshotRepository;
    @Mock
    private SharpApiSnapshotClient sharpApiSnapshotClient;
    @Mock
    private OddsSourceExtractionService oddsSourceExtractionService;

    private PreMatchOddsRefreshServiceImpl service;
    private League league;
    private SourceTarget sourceTarget;
    private LocalDate refreshDate;

    @BeforeEach
    void setUp() {
        service = new PreMatchOddsRefreshServiceImpl(
                leagueRepository,
                sourceTargetRepository,
                rawSnapshotRepository,
                sharpApiSnapshotClient,
                oddsSourceExtractionService,
                Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC)
        );
        refreshDate = LocalDate.of(2026, 6, 20);
        league = new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1)
                .setCurrentSeason("2025/2026")
                .setActive(true);
        league.setId(UUID.randomUUID());
        sourceTarget = new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.ODDS_REFERENCE)
                .setName("SharpAPI Upcoming Odds Premier League JSON")
                .setUrlTemplate("{sharpApiBaseUrl}/odds?league=premier_league")
                .setReliabilityScore(new BigDecimal("95.00"))
                .setFallbackPriority(1)
                .setActive(true)
                .setRobotsTxtRequired(false);
        sourceTarget.setId(UUID.randomUUID());
    }

    @Test
    void refreshesOddsThroughSharpApiClientAndExtractsSnapshots() {
        when(leagueRepository.findByCodeInAndActiveTrue(Set.of(LeagueCode.PREMIER_LEAGUE)))
                .thenReturn(List.of(league));
        when(sourceTargetRepository.findActiveByLeagueCodeAndSourceType(LeagueCode.PREMIER_LEAGUE, SourceType.ODDS_REFERENCE))
                .thenReturn(List.of(sourceTarget));
        when(sharpApiSnapshotClient.fetch(sourceTarget, refreshDate)).thenReturn(new SharpApiFetchResult(
                "https://test.sharpapi.io/api/v1/odds?league=premier_league",
                ScrapeStatus.SUCCESS,
                200,
                OffsetDateTime.parse("2026-06-20T12:00:01Z"),
                1000L,
                "abc123",
                "application/json",
                2L,
                "{}",
                "[]",
                null
        ));
        when(rawSnapshotRepository.findBySourceTarget_IdAndSnapshotDateAndChecksumSha256(
                sourceTarget.getId(),
                refreshDate,
                "abc123"
        )).thenReturn(Optional.empty());
        when(rawSnapshotRepository.save(any(RawSnapshot.class))).thenAnswer(invocation -> {
            RawSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(UUID.randomUUID());
            return snapshot;
        });
        when(oddsSourceExtractionService.extractDailyOddsSnapshots(any(DailyOddsExtractionRequest.class)))
                .thenReturn(new DailyOddsExtractionResponse(UUID.randomUUID(), OffsetDateTime.now(), List.of(), List.of()));

        var response = service.refreshPreMatchOdds(new PreMatchOddsRefreshRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE),
                refreshDate,
                true,
                true,
                true
        ));

        assertThat(response.successfulSnapshots()).isEqualTo(1);
        verify(sharpApiSnapshotClient).fetch(sourceTarget, refreshDate);
        ArgumentCaptor<RawSnapshot> snapshotCaptor = ArgumentCaptor.forClass(RawSnapshot.class);
        verify(rawSnapshotRepository).save(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().getSourceUrl()).contains("sharpapi.io");
        assertThat(snapshotCaptor.getValue().getRawPayload()).isEqualTo("[]");
        ArgumentCaptor<DailyOddsExtractionRequest> extractionCaptor = ArgumentCaptor.forClass(DailyOddsExtractionRequest.class);
        verify(oddsSourceExtractionService).extractDailyOddsSnapshots(extractionCaptor.capture());
        assertThat(extractionCaptor.getValue().leagueCodes()).containsExactly(LeagueCode.PREMIER_LEAGUE);
    }
}
