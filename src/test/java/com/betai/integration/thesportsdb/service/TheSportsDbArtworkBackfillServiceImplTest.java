package com.betai.integration.thesportsdb.service;

import com.betai.api.dto.TheSportsDbArtworkBackfillRequest;
import com.betai.api.dto.TheSportsDbArtworkBackfillResponse;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalMappingStatus;
import com.betai.domain.source.ExternalSourceType;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.domain.team.Team;
import com.betai.integration.thesportsdb.client.TheSportsDbClient;
import com.betai.integration.thesportsdb.client.TheSportsDbClientResponse;
import com.betai.integration.thesportsdb.client.TheSportsDbEndpoint;
import com.betai.integration.thesportsdb.mapper.TheSportsDbMapper;
import com.betai.repository.ExternalSourceMappingRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.SourceTargetRepository;
import com.betai.repository.TeamAliasRepository;
import com.betai.repository.TeamRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TheSportsDbArtworkBackfillServiceImplTest {

    @Mock
    private TheSportsDbClient client;
    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamAliasRepository teamAliasRepository;
    @Mock
    private ExternalSourceMappingRepository externalSourceMappingRepository;
    @Mock
    private SourceTargetRepository sourceTargetRepository;

    private TheSportsDbArtworkBackfillServiceImpl service;
    private League league;
    private Team team;

    @BeforeEach
    void setUp() {
        league = new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1)
                .setCurrentSeason("2025-2026");
        league.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        team = new Team()
                .setLeague(league)
                .setCanonicalName("Arsenal")
                .setCountry("England")
                .setActive(true)
                .setBadgeUrl("https://cdn.test/existing-badge.png");
        team.setId(UUID.fromString("00000000-0000-0000-0000-000000000101"));

        service = new TheSportsDbArtworkBackfillServiceImpl(
                client,
                new TheSportsDbMapper(new ObjectMapper()),
                leagueRepository,
                teamRepository,
                teamAliasRepository,
                externalSourceMappingRepository,
                sourceTargetRepository,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-06-28T09:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void backfillsArtworkFromSourceTargetsWithoutOverwritingExistingUrlsWithBlanks() {
        SourceTarget sourceTarget = new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.MATCH_DATA)
                .setName("TheSportsDB Premium")
                .setUrlTemplate("https://www.thesportsdb.com/league/4328")
                .setSelectorsJson("{\"source\":\"thesportsdb\",\"leagueId\":\"4328\"}")
                .setActive(true);

        when(leagueRepository.findByCodeInAndActiveTrue(Set.of(LeagueCode.PREMIER_LEAGUE)))
                .thenReturn(List.of(league));
        when(sourceTargetRepository.findByLeague_CodeOrderBySourceTypeAscNameAsc(LeagueCode.PREMIER_LEAGUE))
                .thenReturn(List.of(sourceTarget));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndLeague_IdAndStatus(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.LEAGUE,
                league.getId(),
                ExternalMappingStatus.RESOLVED
        )).thenReturn(List.of());
        when(client.lookupLeague("4328")).thenReturn(response(TheSportsDbEndpoint.LOOKUP_LEAGUE, """
                {"list":[{"idLeague":"4328","strLeague":"English Premier League","strBadge":"https://cdn.test/league-badge.png","strLogo":"https://cdn.test/league-logo.png","strPoster":"https://cdn.test/league-poster.png"}]}
                """));
        when(client.listTeams("4328")).thenReturn(response(TheSportsDbEndpoint.LIST_TEAMS, """
                {"list":[{"idTeam":"133604","strTeam":"Arsenal","strAlternate":"Arsenal FC","strBadge":"","strLogo":"https://cdn.test/arsenal-logo.png","strEquipment":"https://cdn.test/arsenal-kit.png"}]}
                """));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.TEAM,
                "133604"
        )).thenReturn(Optional.empty());
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(eq(LeagueCode.PREMIER_LEAGUE), any()))
                .thenReturn(Optional.empty());
        when(teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(LeagueCode.PREMIER_LEAGUE, "Arsenal"))
                .thenReturn(Optional.of(team));

        TheSportsDbArtworkBackfillResponse response = service.backfillArtwork(Set.of(LeagueCode.PREMIER_LEAGUE));

        assertThat(response.checkedLeagues()).isEqualTo(1);
        assertThat(response.updatedLeagues()).isEqualTo(1);
        assertThat(response.checkedTeams()).isEqualTo(1);
        assertThat(response.updatedTeams()).isEqualTo(1);
        assertThat(response.failedLeagues()).isZero();
        assertThat(response.failedTeams()).isZero();
        assertThat(league.getBadgeUrl()).isEqualTo("https://cdn.test/league-badge.png");
        assertThat(league.getLogoUrl()).isEqualTo("https://cdn.test/league-logo.png");
        assertThat(league.getPosterUrl()).isEqualTo("https://cdn.test/league-poster.png");
        assertThat(team.getBadgeUrl()).isEqualTo("https://cdn.test/existing-badge.png");
        assertThat(team.getLogoUrl()).isEqualTo("https://cdn.test/arsenal-logo.png");
        assertThat(team.getEquipmentUrl()).isEqualTo("https://cdn.test/arsenal-kit.png");
        verify(leagueRepository).save(league);
        verify(teamRepository).save(team);
    }

    @Test
    void backfillsArtworkWithSafeControlsDryRun() {
        SourceTarget sourceTarget = new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.MATCH_DATA)
                .setName("TheSportsDB Premium")
                .setUrlTemplate("https://www.thesportsdb.com/league/4328")
                .setSelectorsJson("{\"source\":\"thesportsdb\",\"leagueId\":\"4328\"}")
                .setActive(true);

        when(leagueRepository.findByCodeInAndActiveTrue(Set.of(LeagueCode.PREMIER_LEAGUE)))
                .thenReturn(List.of(league));
        when(sourceTargetRepository.findByLeague_CodeOrderBySourceTypeAscNameAsc(LeagueCode.PREMIER_LEAGUE))
                .thenReturn(List.of(sourceTarget));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndLeague_IdAndStatus(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.LEAGUE,
                league.getId(),
                ExternalMappingStatus.RESOLVED
        )).thenReturn(List.of());
        when(client.listTeams("4328")).thenReturn(response(TheSportsDbEndpoint.LIST_TEAMS, """
                {"list":[{"idTeam":"133604","strTeam":"Arsenal","strAlternate":"Arsenal FC","strBadge":"","strLogo":"https://cdn.test/arsenal-logo.png","strEquipment":"https://cdn.test/arsenal-kit.png"}]}
                """));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.TEAM,
                "133604"
        )).thenReturn(Optional.empty());
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(eq(LeagueCode.PREMIER_LEAGUE), any()))
                .thenReturn(Optional.empty());
        when(teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(LeagueCode.PREMIER_LEAGUE, "Arsenal"))
                .thenReturn(Optional.of(team));

        TheSportsDbArtworkBackfillRequest request = new TheSportsDbArtworkBackfillRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE), null, 5, "Arsenal", true, false, false
        );
        TheSportsDbArtworkBackfillResponse response = service.backfillArtwork(request);

        assertThat(response.checkedLeagues()).isEqualTo(1);
        assertThat(response.updatedLeagues()).isZero();
        assertThat(response.checkedTeams()).isEqualTo(1);
        assertThat(response.updatedTeams()).isEqualTo(1);
        assertThat(team.getLogoUrl()).isNull();
        verify(leagueRepository, never()).save(any());
        verify(teamRepository, never()).save(any());
    }

    @Test
    void leaguesOnlyUsesLeagueLookupAndDoesNotFetchTeams() {
        SourceTarget sourceTarget = sourceTarget();
        when(leagueRepository.findByCodeInAndActiveTrue(Set.of(LeagueCode.PREMIER_LEAGUE)))
                .thenReturn(List.of(league));
        when(sourceTargetRepository.findByLeague_CodeOrderBySourceTypeAscNameAsc(LeagueCode.PREMIER_LEAGUE))
                .thenReturn(List.of(sourceTarget));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndLeague_IdAndStatus(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.LEAGUE,
                league.getId(),
                ExternalMappingStatus.RESOLVED
        )).thenReturn(List.of());
        when(client.lookupLeague("4328")).thenReturn(response(TheSportsDbEndpoint.LOOKUP_LEAGUE, """
                {"list":[{"idLeague":"4328","strLeague":"English Premier League","strBadge":"https://cdn.test/league-badge.png","strLogo":"https://cdn.test/league-logo.png","strPoster":"https://cdn.test/league-poster.png"}]}
                """));

        TheSportsDbArtworkBackfillRequest request = new TheSportsDbArtworkBackfillRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE), null, 5, null, false, false, true
        );
        TheSportsDbArtworkBackfillResponse response = service.backfillArtwork(request);

        assertThat(response.checkedLeagues()).isEqualTo(1);
        assertThat(response.updatedLeagues()).isEqualTo(1);
        assertThat(response.checkedTeams()).isZero();
        assertThat(league.getBadgeUrl()).isEqualTo("https://cdn.test/league-badge.png");
        verify(client, never()).listTeams("4328");
        verify(leagueRepository).save(league);
    }

    @Test
    void teamsOnlyHonorsLimitAndDoesNotLookupLeagueArtwork() {
        SourceTarget sourceTarget = sourceTarget();
        when(leagueRepository.findByCodeInAndActiveTrue(Set.of(LeagueCode.PREMIER_LEAGUE)))
                .thenReturn(List.of(league));
        when(sourceTargetRepository.findByLeague_CodeOrderBySourceTypeAscNameAsc(LeagueCode.PREMIER_LEAGUE))
                .thenReturn(List.of(sourceTarget));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndLeague_IdAndStatus(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.LEAGUE,
                league.getId(),
                ExternalMappingStatus.RESOLVED
        )).thenReturn(List.of());
        when(client.listTeams("4328")).thenReturn(response(TheSportsDbEndpoint.LIST_TEAMS, """
                {"list":[
                    {"idTeam":"133604","strTeam":"Arsenal","strAlternate":"Arsenal FC","strBadge":"","strLogo":"https://cdn.test/arsenal-logo.png","strEquipment":"https://cdn.test/arsenal-kit.png"},
                    {"idTeam":"133605","strTeam":"Chelsea","strLogo":"https://cdn.test/chelsea-logo.png"}
                ]}
                """));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.TEAM,
                "133604"
        )).thenReturn(Optional.empty());
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(eq(LeagueCode.PREMIER_LEAGUE), any()))
                .thenReturn(Optional.empty());
        when(teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(LeagueCode.PREMIER_LEAGUE, "Arsenal"))
                .thenReturn(Optional.of(team));

        TheSportsDbArtworkBackfillRequest request = new TheSportsDbArtworkBackfillRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE), null, 1, null, false, true, false
        );
        TheSportsDbArtworkBackfillResponse response = service.backfillArtwork(request);

        assertThat(response.checkedLeagues()).isEqualTo(1);
        assertThat(response.updatedLeagues()).isZero();
        assertThat(response.checkedTeams()).isEqualTo(1);
        assertThat(response.updatedTeams()).isEqualTo(1);
        verify(client, never()).lookupLeague("4328");
        verify(teamRepository).save(team);
    }

    private SourceTarget sourceTarget() {
        return new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.MATCH_DATA)
                .setName("TheSportsDB Premium")
                .setUrlTemplate("https://www.thesportsdb.com/league/4328")
                .setSelectorsJson("{\"source\":\"thesportsdb\",\"leagueId\":\"4328\"}")
                .setActive(true);
    }

    private TheSportsDbClientResponse response(TheSportsDbEndpoint endpoint, String rawJson) {
        return new TheSportsDbClientResponse(
                endpoint,
                endpoint.path(),
                200,
                OffsetDateTime.parse("2026-06-28T09:00:00Z"),
                rawJson
        );
    }
}
