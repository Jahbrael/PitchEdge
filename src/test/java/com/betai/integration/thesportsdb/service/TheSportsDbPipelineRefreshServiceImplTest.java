package com.betai.integration.thesportsdb.service;

import com.betai.config.PredictionProperties;
import com.betai.domain.feature.InsufficientSeasonPolicy;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalMappingStatus;
import com.betai.domain.source.ExternalSourceMapping;
import com.betai.domain.source.ExternalSourceType;
import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.integration.thesportsdb.TheSportsDbProperties;
import com.betai.integration.thesportsdb.dto.TheSportsDbImportSummary;
import com.betai.repository.ExternalSourceMappingRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.SourceTargetRepository;
import com.betai.service.CompetitionHistoryPolicyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TheSportsDbPipelineRefreshServiceImplTest {

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private ExternalSourceMappingRepository externalSourceMappingRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private SourceTargetRepository sourceTargetRepository;
    @Mock
    private TheSportsDbLeagueSeasonImportService leagueSeasonImportService;
    @Mock
    private TheSportsDbCoverageService coverageService;

    private TheSportsDbPipelineRefreshServiceImpl service;
    private League league;

    @BeforeEach
    void setUp() {
        service = new TheSportsDbPipelineRefreshServiceImpl(
                new TheSportsDbProperties(
                        true,
                        "https://www.thesportsdb.com/api/v2/json",
                        "test-key",
                        80,
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        false,
                        false
                ),
                new PredictionProperties(
                        "test-model",
                        20,
                        20,
                        14,
                        List.of(MatchStatus.SCHEDULED),
                        3,
                        10,
                        InsufficientSeasonPolicy.USE_MAX_AVAILABLE,
                        10
                ),
                leagueRepository,
                externalSourceMappingRepository,
                matchRepository,
                sourceTargetRepository,
                leagueSeasonImportService,
                coverageService,
                new CompetitionHistoryPolicyService(),
                new ObjectMapper()
        );
        league = new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1)
                .setCurrentSeason("2025/2026");
        league.setId(UUID.randomUUID());
    }

    @Test
    void importsSelectedRecentSeasonsEvenWhenPartialRowsAlreadyExist() {
        when(leagueRepository.findByCode(LeagueCode.PREMIER_LEAGUE)).thenReturn(Optional.of(league));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndLeague_IdAndStatus(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.LEAGUE,
                league.getId(),
                ExternalMappingStatus.RESOLVED
        )).thenReturn(List.of(mapping(ExternalEntityType.LEAGUE, "4328", null)));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndLeague_Id(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.SEASON,
                league.getId()
        )).thenReturn(List.of(
                mapping(ExternalEntityType.SEASON, "4328:2025/2026", "2025/2026"),
                mapping(ExternalEntityType.SEASON, "4328:2024/2025", "2024/2025"),
                mapping(ExternalEntityType.SEASON, "4328:2023/2024", "2023/2024"),
                mapping(ExternalEntityType.SEASON, "4328:2022/2023", "2022/2023")
        ));
        when(leagueSeasonImportService.importLeagueSeason(LeagueCode.PREMIER_LEAGUE, "4328", "2025/2026"))
                .thenReturn(summary("2025/2026"));
        when(leagueSeasonImportService.importLeagueSeason(LeagueCode.PREMIER_LEAGUE, "4328", "2024/2025"))
                .thenReturn(summary("2024/2025"));
        when(leagueSeasonImportService.importLeagueSeason(LeagueCode.PREMIER_LEAGUE, "4328", "2023/2024"))
                .thenReturn(summary("2023/2024"));

        var summary = service.refresh(Set.of(LeagueCode.PREMIER_LEAGUE), 3);

        assertThat(summary.refreshedLeagues()).isEqualTo(1);
        verify(leagueSeasonImportService).importLeagueSeason(LeagueCode.PREMIER_LEAGUE, "4328", "2025/2026");
        verify(leagueSeasonImportService).importLeagueSeason(LeagueCode.PREMIER_LEAGUE, "4328", "2024/2025");
        verify(leagueSeasonImportService).importLeagueSeason(LeagueCode.PREMIER_LEAGUE, "4328", "2023/2024");
        verify(leagueSeasonImportService, never()).importLeagueSeason(LeagueCode.PREMIER_LEAGUE, "4328", "2022/2023");
        verify(coverageService).recalculate(LeagueCode.PREMIER_LEAGUE, "2025/2026");
        verify(coverageService).recalculate(LeagueCode.PREMIER_LEAGUE, "2024/2025");
        verify(coverageService).recalculate(LeagueCode.PREMIER_LEAGUE, "2023/2024");
    }

    @Test
    void fallsBackToActiveTheSportsDbSourceTargetAndCreatesLeagueMappingWhenMappingsAreEmpty() {
        when(leagueRepository.findByCode(LeagueCode.PREMIER_LEAGUE)).thenReturn(Optional.of(league));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndLeague_IdAndStatus(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.LEAGUE,
                league.getId(),
                ExternalMappingStatus.RESOLVED
        )).thenReturn(List.of());
        when(sourceTargetRepository.findByLeague_CodeOrderBySourceTypeAscNameAsc(LeagueCode.PREMIER_LEAGUE))
                .thenReturn(List.of(theSportsDbSourceTarget("4328")));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.LEAGUE,
                "4328"
        )).thenReturn(Optional.empty());
        when(externalSourceMappingRepository.save(any(ExternalSourceMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndLeague_Id(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.SEASON,
                league.getId()
        )).thenReturn(List.of(mapping(ExternalEntityType.SEASON, "4328:2025/2026", "2025/2026")));
        when(leagueSeasonImportService.importLeagueSeason(LeagueCode.PREMIER_LEAGUE, "4328", "2025/2026"))
                .thenReturn(summary("2025/2026"));

        var summary = service.refresh(Set.of(LeagueCode.PREMIER_LEAGUE), 1);

        assertThat(summary.resolvedLeagues()).isEqualTo(1);
        assertThat(summary.skippedLeagues()).isZero();
        ArgumentCaptor<ExternalSourceMapping> captor = ArgumentCaptor.forClass(ExternalSourceMapping.class);
        verify(externalSourceMappingRepository).save(captor.capture());
        assertThat(captor.getValue().getExternalEntityId()).isEqualTo("4328");
        assertThat(captor.getValue().getInternalEntityId()).isEqualTo(league.getId());
        assertThat(captor.getValue().getStatus()).isEqualTo(ExternalMappingStatus.RESOLVED);
        verify(leagueSeasonImportService).importLeagueSeason(LeagueCode.PREMIER_LEAGUE, "4328", "2025/2026");
    }

    @Test
    void skipsLeagueWithExplicitReasonWhenNoMappingOrSourceTargetLeagueIdExists() {
        when(leagueRepository.findByCode(LeagueCode.PREMIER_LEAGUE)).thenReturn(Optional.of(league));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndLeague_IdAndStatus(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.LEAGUE,
                league.getId(),
                ExternalMappingStatus.RESOLVED
        )).thenReturn(List.of());
        when(sourceTargetRepository.findByLeague_CodeOrderBySourceTypeAscNameAsc(LeagueCode.PREMIER_LEAGUE))
                .thenReturn(List.of());

        var summary = service.refresh(Set.of(LeagueCode.PREMIER_LEAGUE), 3);

        assertThat(summary.resolvedLeagues()).isZero();
        assertThat(summary.unresolvedLeagues()).isEqualTo(1);
        assertThat(summary.skippedLeagues()).isEqualTo(1);
        assertThat(summary.leagueSkipReasons()).containsExactly(
                "PREMIER_LEAGUE: no active TheSportsDB league mapping or source target leagueId."
        );
        verify(leagueSeasonImportService, never()).importLeagueSeason(any(), any(), any());
    }

    @Test
    void worldCupUsesInternationalFourYearWindowWithPreservedRequestedYears() {
        league = new League()
                .setCode(LeagueCode.FIFA_WORLD_CUP_2026)
                .setName("FIFA World Cup 2026")
                .setCountry("International")
                .setTier(1)
                .setCurrentSeason("2026");
        league.setId(UUID.randomUUID());
        when(leagueRepository.findByCode(LeagueCode.FIFA_WORLD_CUP_2026)).thenReturn(Optional.of(league));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndLeague_IdAndStatus(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.LEAGUE,
                league.getId(),
                ExternalMappingStatus.RESOLVED
        )).thenReturn(List.of(mapping(ExternalEntityType.LEAGUE, "4429", null)));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                eq(ExternalSourceType.THESPORTSDB),
                eq(ExternalEntityType.LEAGUE),
                anyString()
        )).thenReturn(Optional.empty());
        when(externalSourceMappingRepository.save(any(ExternalSourceMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leagueSeasonImportService.importLeagueSeason(
                eq(LeagueCode.FIFA_WORLD_CUP_2026),
                anyString(),
                anyString(),
                eq(TheSportsDbLeagueSeasonImportService.SeasonLabelStrategy.PRESERVE_REQUESTED_SEASON)
        )).thenAnswer(invocation -> new TheSportsDbImportSummary(
                LeagueCode.FIFA_WORLD_CUP_2026,
                invocation.getArgument(1),
                invocation.getArgument(2),
                0,
                1,
                0,
                0,
                1,
                0,
                0
        ));

        var summary = service.refresh(Set.of(LeagueCode.FIFA_WORLD_CUP_2026), 3);

        assertThat(summary.refreshedLeagues()).isEqualTo(1);
        assertThat(summary.requestedSeasons()).isEqualTo(48);
        verify(leagueSeasonImportService).importLeagueSeason(
                LeagueCode.FIFA_WORLD_CUP_2026,
                "4562",
                "2025",
                TheSportsDbLeagueSeasonImportService.SeasonLabelStrategy.PRESERVE_REQUESTED_SEASON
        );
        verify(leagueSeasonImportService, never()).importLeagueSeason(
                eq(LeagueCode.FIFA_WORLD_CUP_2026),
                anyString(),
                anyString()
        );
    }

    private ExternalSourceMapping mapping(ExternalEntityType entityType, String externalId, String season) {
        ExternalSourceMapping mapping = new ExternalSourceMapping()
                .setSourceType(ExternalSourceType.THESPORTSDB)
                .setEntityType(entityType)
                .setExternalEntityId(externalId)
                .setInternalEntityId(entityType == ExternalEntityType.LEAGUE ? league.getId() : null)
                .setLeague(league)
                .setSeason(season)
                .setStatus(ExternalMappingStatus.RESOLVED);
        mapping.setId(UUID.randomUUID());
        return mapping;
    }

    private TheSportsDbImportSummary summary(String season) {
        return new TheSportsDbImportSummary(
                LeagueCode.PREMIER_LEAGUE,
                "4328",
                season,
                0,
                1,
                0,
                0,
                1,
                0,
                0
        );
    }

    private SourceTarget theSportsDbSourceTarget(String leagueId) {
        return new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.RESULTS)
                .setName("TheSportsDB " + league.getName() + " Events JSON")
                .setUrlTemplate("https://www.thesportsdb.com/api/v1/json/123/eventsseason.php?id=" + leagueId + "&s=2025-2026")
                .setSourceSeasonToken("2025/2026")
                .setTargetSeasonLabel("2025/2026")
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(false)
                .setActive(true)
                .setSelectorsJson("{\"format\":\"thesportsdb-events-json\",\"leagueId\":\"" + leagueId + "\"}");
    }
}
