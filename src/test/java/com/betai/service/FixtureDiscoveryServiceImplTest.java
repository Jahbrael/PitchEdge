package com.betai.service;

import com.betai.api.dto.DailyExtractionResponse;
import com.betai.api.dto.DailyRefreshResponse;
import com.betai.api.dto.FixtureDiscoveryRequest;
import com.betai.api.dto.FootballDataFixtureSourceRegistrationRequest;
import com.betai.api.dto.FootballDataFixtureSourceRegistrationResponse;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.MatchStatus;
import com.betai.exception.InvalidRequestException;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixtureDiscoveryServiceImplTest {

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private FootballDataFixtureSourceService footballDataFixtureSourceService;
    @Mock
    private DailyRefreshService dailyRefreshService;
    @Mock
    private ExtractionService extractionService;
    @Mock
    private PendingSlateGenerationService pendingSlateGenerationService;

    private FixtureDiscoveryServiceImpl service;
    private League league;

    @BeforeEach
    void setUp() {
        service = new FixtureDiscoveryServiceImpl(
                leagueRepository,
                matchRepository,
                footballDataFixtureSourceService,
                dailyRefreshService,
                extractionService,
                pendingSlateGenerationService,
                Clock.fixed(Instant.parse("2026-06-07T10:00:00Z"), ZoneOffset.UTC)
        );

        league = new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1)
                .setActive(true)
                .setScrapeEnabled(true)
                .setCurrentSeason("2025/2026");
        league.setId(UUID.randomUUID());
    }

    @Test
    void autoRegistersCurrentSeasonSourcesAndSkipsSlateWhenNoFixturesAreDiscovered() {
        when(leagueRepository.findByCodeInAndActiveTrue(Set.of(LeagueCode.PREMIER_LEAGUE))).thenReturn(List.of(league));
        when(footballDataFixtureSourceService.registerLatestFixtureSources(any()))
                .thenReturn(new FootballDataFixtureSourceRegistrationResponse(
                        UUID.randomUUID(),
                        OffsetDateTime.parse("2026-06-07T10:00:00Z"),
                        "https://www.football-data.co.uk/fixtures.csv",
                        List.of()
                ));
        when(dailyRefreshService.triggerDailyRefresh(any()))
                .thenReturn(new DailyRefreshResponse(UUID.randomUUID(), OffsetDateTime.parse("2026-06-07T10:00:00Z"), List.of()));
        when(extractionService.extractDailySnapshots(any()))
                .thenReturn(new DailyExtractionResponse(UUID.randomUUID(), OffsetDateTime.parse("2026-06-07T10:00:00Z"), List.of()));
        when(matchRepository.findCandidateFixtures(
                anyCollection(),
                eq(LocalDate.parse("2026-06-07")),
                eq(LocalDate.parse("2026-07-06")),
                eq(List.of(MatchStatus.SCHEDULED))
        )).thenReturn(List.of());

        var response = service.discoverFixtures(new FixtureDiscoveryRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE),
                null,
                LocalDate.parse("2026-06-07"),
                null,
                null,
                false,
                false,
                true,
                true,
                "phase5-deterministic-v1",
                false
        ));

        ArgumentCaptor<FootballDataFixtureSourceRegistrationRequest> registrationCaptor =
                ArgumentCaptor.forClass(FootballDataFixtureSourceRegistrationRequest.class);
        verify(footballDataFixtureSourceService).registerLatestFixtureSources(registrationCaptor.capture());
        verify(pendingSlateGenerationService, never()).generatePendingSlate(any());

        assertThat(registrationCaptor.getValue().targetSeasonLabel()).isEqualTo("2026/2027");
        assertThat(response.targetSeasonLabel()).isEqualTo("2026/2027");
        assertThat(response.fixtureDateFrom()).isEqualTo(LocalDate.parse("2026-06-07"));
        assertThat(response.fixtureDateTo()).isEqualTo(LocalDate.parse("2026-07-06"));
        assertThat(response.discoveredFixtures()).isEmpty();
        assertThat(response.pendingSlateGeneration()).isNull();
        assertThat(response.warnings()).hasSize(3);
        assertThat(response.warnings()).anyMatch(warning -> warning.contains("Only 0 scheduled fixture(s) were discovered for PREMIER_LEAGUE"));
    }

    @Test
    void rejectsDiscoveryWindowLongerThanOneYear() {
        assertThatThrownBy(() -> service.discoverFixtures(new FixtureDiscoveryRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE),
                null,
                LocalDate.parse("2026-06-07"),
                LocalDate.parse("2026-06-07"),
                LocalDate.parse("2027-06-13"),
                false,
                false,
                false,
                false,
                null,
                false
        ))).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cannot exceed 370 days");
    }
}
