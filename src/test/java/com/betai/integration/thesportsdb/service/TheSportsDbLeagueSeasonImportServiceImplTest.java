package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalMappingStatus;
import com.betai.domain.source.ExternalSourceMapping;
import com.betai.domain.source.ExternalSourceType;
import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.domain.team.Team;
import com.betai.domain.team.TeamAlias;
import com.betai.integration.thesportsdb.TheSportsDbProperties;
import com.betai.integration.thesportsdb.client.TheSportsDbClient;
import com.betai.integration.thesportsdb.client.TheSportsDbClientResponse;
import com.betai.integration.thesportsdb.client.TheSportsDbEndpoint;
import com.betai.integration.thesportsdb.dto.TheSportsDbImportSummary;
import com.betai.integration.thesportsdb.mapper.TheSportsDbMapper;
import com.betai.repository.ExternalSourceMappingRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.SourceTargetRepository;
import com.betai.repository.TeamAliasRepository;
import com.betai.repository.TeamRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class TheSportsDbLeagueSeasonImportServiceImplTest {

    @Mock
    private TheSportsDbClient client;
    @Mock
    private TheSportsDbSnapshotService snapshotService;
    @Mock
    private ExternalSourceMappingService externalSourceMappingService;
    @Mock
    private ExternalSourceMappingRepository externalSourceMappingRepository;
    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private SourceTargetRepository sourceTargetRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamAliasRepository teamAliasRepository;
    @Mock
    private MatchRepository matchRepository;

    private TheSportsDbLeagueSeasonImportServiceImpl service;
    private League league;
    private Team arsenal;
    private Team chelsea;

    @BeforeEach
    void setUp() {
        league = league();
        arsenal = team("00000000-0000-0000-0000-000000000010", "Arsenal");
        chelsea = team("00000000-0000-0000-0000-000000000011", "Chelsea");
        TheSportsDbProperties properties = new TheSportsDbProperties(
                true,
                "https://example.test/api/v2/json",
                "test-key",
                80,
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                false,
                false
        );
        service = new TheSportsDbLeagueSeasonImportServiceImpl(
                client,
                new TheSportsDbMapper(new ObjectMapper()),
                snapshotService,
                externalSourceMappingService,
                externalSourceMappingRepository,
                leagueRepository,
                sourceTargetRepository,
                teamRepository,
                teamAliasRepository,
                matchRepository,
                properties
        );
        when(leagueRepository.findByCode(LeagueCode.PREMIER_LEAGUE)).thenReturn(Optional.of(league));
        when(sourceTargetRepository.findByLeague_CodeAndSourceTypeAndName(
                any(LeagueCode.class),
                any(SourceType.class),
                any(String.class)
        )).thenReturn(Optional.empty());
        when(sourceTargetRepository.save(any(SourceTarget.class))).thenAnswer(invocation -> {
            SourceTarget target = invocation.getArgument(0);
            target.setId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
            return target;
        });
        when(snapshotService.persist(any(TheSportsDbClientResponse.class), any(TheSportsDbSnapshotMetadata.class)))
                .thenReturn(new com.betai.domain.snapshot.RawSnapshot());
        lenient().when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                any(ExternalSourceType.class),
                any(ExternalEntityType.class),
                any(String.class)
        )).thenReturn(Optional.empty());
        lenient().when(client.lookupLeague("4328")).thenReturn(response(TheSportsDbEndpoint.LOOKUP_LEAGUE, "lookup/league/4328",
                """
                        {"lookup":[{
                          "idLeague":"4328",
                          "strLeague":"English Premier League",
                          "strBadge":"https://cdn.test/premier-league-badge.png",
                          "strLogo":"https://cdn.test/premier-league-logo.png"
                        }]}
                        """));
    }

    @Test
    void resolvesExistingTeamAliasesAndUpdatesExistingFixture() {
        when(client.listSeasons("4328")).thenReturn(response(TheSportsDbEndpoint.LIST_SEASONS, "list/seasons/4328",
                "{\"list\":[{\"strSeason\":\"2025-2026\"}]}"));
        when(client.listTeams("4328")).thenReturn(response(TheSportsDbEndpoint.LIST_TEAMS, "list/teams/4328",
                "{\"list\":[{\"idTeam\":\"T1\",\"strTeam\":\"Arsenal\"},{\"idTeam\":\"T2\",\"strTeam\":\"Chelsea\"}]}"));
        when(client.scheduleLeague("4328", "2025-2026")).thenReturn(response(TheSportsDbEndpoint.SCHEDULE_LEAGUE,
                "schedule/league/4328/2025-2026",
                """
                        {"events":[{
                          "idEvent":"E1",
                          "idHomeTeam":"T1",
                          "idAwayTeam":"T2",
                          "strHomeTeam":"Arsenal",
                          "strAwayTeam":"Chelsea",
                          "dateEvent":"2026-08-01",
                          "strTime":"15:00:00",
                          "intHomeScore":"2",
                          "intAwayScore":"1",
                          "strStatus":"Match Finished"
                        }]}
                        """));
        when(teamRepository.countByLeague_Code(LeagueCode.PREMIER_LEAGUE)).thenReturn(2L);
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(LeagueCode.PREMIER_LEAGUE, "arsenal"))
                .thenReturn(Optional.of(alias(arsenal, "Arsenal")));
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(LeagueCode.PREMIER_LEAGUE, "chelsea"))
                .thenReturn(Optional.of(alias(chelsea, "Chelsea")));
        Match existing = new Match()
                .setLeague(league)
                .setHomeTeam(arsenal)
                .setAwayTeam(chelsea)
                .setMatchDate(java.time.LocalDate.parse("2026-08-01"))
                .setKickoffAt(OffsetDateTime.parse("2026-08-01T14:00:00Z"))
                .setStatus(MatchStatus.SCHEDULED)
                .setSeasonLabel("2025/2026")
                .setSourceFixtureKey("TSD:E1");
        existing.setId(UUID.fromString("00000000-0000-0000-0000-000000000030"));
        when(matchRepository.findByLeague_CodeAndSourceFixtureKeySafely(LeagueCode.PREMIER_LEAGUE, "TSD:E1"))
                .thenReturn(Optional.of(existing));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TheSportsDbImportSummary summary = service.importLeagueSeason(LeagueCode.PREMIER_LEAGUE, "4328", "2025/2026");

        assertThat(summary.season()).isEqualTo("2025/2026");
        assertThat(summary.seasonsImported()).isEqualTo(1);
        assertThat(summary.teamsResolved()).isEqualTo(2);
        assertThat(summary.teamsCreated()).isZero();
        assertThat(summary.fixturesUpdated()).isEqualTo(1);
        assertThat(existing.getKickoffAt()).isEqualTo(OffsetDateTime.parse("2026-08-01T15:00:00Z"));
        assertThat(existing.getStatus()).isEqualTo(MatchStatus.FINISHED);
        assertThat(existing.getHomeScore()).isEqualTo(2);
        assertThat(existing.getAwayScore()).isEqualTo(1);
        assertThat(league.getBadgeUrl()).isEqualTo("https://cdn.test/premier-league-badge.png");
        assertThat(league.getLogoUrl()).isEqualTo("https://cdn.test/premier-league-logo.png");
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void prefersExactFixtureIdentityWhenUpstreamEventMappingChangesTeams() {
        when(client.listSeasons("4328")).thenReturn(response(TheSportsDbEndpoint.LIST_SEASONS, "list/seasons/4328",
                "{\"list\":[{\"strSeason\":\"2026\"}]}"));
        when(client.listTeams("4328")).thenReturn(response(TheSportsDbEndpoint.LIST_TEAMS, "list/teams/4328",
                "{\"list\":[{\"idTeam\":\"T1\",\"strTeam\":\"Arsenal\"},{\"idTeam\":\"T2\",\"strTeam\":\"Chelsea\"}]}"));
        when(client.scheduleLeague("4328", "2026")).thenReturn(response(TheSportsDbEndpoint.SCHEDULE_LEAGUE,
                "schedule/league/4328/2026",
                """
                        {"events":[{
                          "idEvent":"E1",
                          "idHomeTeam":"T1",
                          "idAwayTeam":"T2",
                          "strHomeTeam":"Arsenal",
                          "strAwayTeam":"Chelsea",
                          "strTimestamp":"2026-08-01T15:00:00Z",
                          "intHomeScore":"2",
                          "intAwayScore":"1",
                          "strStatus":"Match Finished"
                        }]}
                        """));
        when(teamRepository.countByLeague_Code(LeagueCode.PREMIER_LEAGUE)).thenReturn(2L);
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(LeagueCode.PREMIER_LEAGUE, "arsenal"))
                .thenReturn(Optional.of(alias(arsenal, "Arsenal")));
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(LeagueCode.PREMIER_LEAGUE, "chelsea"))
                .thenReturn(Optional.of(alias(chelsea, "Chelsea")));

        Match mappedReverseFixture = new Match()
                .setLeague(league)
                .setHomeTeam(chelsea)
                .setAwayTeam(arsenal)
                .setMatchDate(java.time.LocalDate.parse("2026-08-01"))
                .setKickoffAt(OffsetDateTime.parse("2026-08-01T15:00:00Z"))
                .setStatus(MatchStatus.FINISHED)
                .setSeasonLabel("2026")
                .setSourceFixtureKey("TSD:E1");
        mappedReverseFixture.setId(UUID.fromString("00000000-0000-0000-0000-000000000030"));
        Match exactFixture = new Match()
                .setLeague(league)
                .setHomeTeam(arsenal)
                .setAwayTeam(chelsea)
                .setMatchDate(java.time.LocalDate.parse("2026-08-01"))
                .setKickoffAt(OffsetDateTime.parse("2026-08-01T15:00:00Z"))
                .setStatus(MatchStatus.FINISHED)
                .setSeasonLabel("2026")
                .setSourceFixtureKey("TSD:OLD-EVENT");
        exactFixture.setId(UUID.fromString("00000000-0000-0000-0000-000000000031"));
        ExternalSourceMapping staleEventMapping = new ExternalSourceMapping()
                .setSourceType(ExternalSourceType.THESPORTSDB)
                .setEntityType(ExternalEntityType.EVENT)
                .setExternalEntityId("E1")
                .setInternalEntityId(mappedReverseFixture.getId())
                .setLeague(league)
                .setStatus(ExternalMappingStatus.RESOLVED);

        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.EVENT,
                "E1"
        )).thenReturn(Optional.of(staleEventMapping));
        when(matchRepository.findById(mappedReverseFixture.getId())).thenReturn(Optional.of(mappedReverseFixture));
        when(matchRepository.findByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndKickoffAtSafely(
                LeagueCode.PREMIER_LEAGUE,
                arsenal.getId(),
                chelsea.getId(),
                OffsetDateTime.parse("2026-08-01T15:00:00Z")
        )).thenReturn(Optional.of(exactFixture));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TheSportsDbImportSummary summary = service.importLeagueSeason(
                LeagueCode.PREMIER_LEAGUE,
                "4328",
                "2026"
        );

        assertThat(summary.fixturesUpdated()).isEqualTo(1);
        assertThat(mappedReverseFixture.getHomeTeam()).isEqualTo(chelsea);
        assertThat(mappedReverseFixture.getAwayTeam()).isEqualTo(arsenal);
        assertThat(exactFixture.getSourceFixtureKey()).isEqualTo("TSD:OLD-EVENT");
        verify(matchRepository).save(exactFixture);
        verify(externalSourceMappingService).markResolved(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.EVENT,
                "E1",
                exactFixture.getId(),
                league,
                "2026",
                "Arsenal vs Chelsea"
        );
    }

    @Test
    void doesNotCreateDuplicateTeamsWhenLeagueAlreadyHasTeams() {
        when(client.listSeasons("4328")).thenReturn(response(TheSportsDbEndpoint.LIST_SEASONS, "list/seasons/4328",
                "{\"seasons\":[{\"strSeason\":\"2026\"}]}"));
        when(client.listTeams("4328")).thenReturn(response(TheSportsDbEndpoint.LIST_TEAMS, "list/teams/4328",
                "{\"teams\":[{\"idTeam\":\"UNKNOWN\",\"strTeam\":\"Unknown FC\"}]}"));
        when(client.scheduleLeague("4328", "2026")).thenReturn(response(TheSportsDbEndpoint.SCHEDULE_LEAGUE,
                "schedule/league/4328/2026",
                "{\"events\":[]}"));
        when(teamRepository.countByLeague_Code(LeagueCode.PREMIER_LEAGUE)).thenReturn(1L);
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(LeagueCode.PREMIER_LEAGUE, "unknown_fc"))
                .thenReturn(Optional.empty());
        when(teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(LeagueCode.PREMIER_LEAGUE, "Unknown FC"))
                .thenReturn(Optional.empty());

        TheSportsDbImportSummary summary = service.importLeagueSeason(LeagueCode.PREMIER_LEAGUE, "4328", "2026");

        assertThat(summary.teamsUnresolved()).isEqualTo(1);
        assertThat(summary.teamsCreated()).isZero();
        verify(teamRepository, never()).save(any(Team.class));
        verify(externalSourceMappingService).markUnresolved(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.TEAM,
                "UNKNOWN",
                league,
                null,
                "Unknown FC",
                "No canonical team or alias matched."
        );
    }

    @Test
    void updatesExistingTeamArtworkWithoutOverwritingWithBlankValues() {
        arsenal.setBadgeUrl("https://cdn.test/existing-badge.png");
        when(client.listSeasons("4328")).thenReturn(response(TheSportsDbEndpoint.LIST_SEASONS, "list/seasons/4328",
                "{\"seasons\":[{\"strSeason\":\"2026\"}]}"));
        when(client.listTeams("4328")).thenReturn(response(TheSportsDbEndpoint.LIST_TEAMS, "list/teams/4328",
                """
                        {"teams":[{
                          "idTeam":"T1",
                          "strTeam":"Arsenal",
                          "strBadge":"",
                          "strLogo":"https://cdn.test/arsenal-logo.png",
                          "strBanner":"https://cdn.test/arsenal-banner.png"
                        }]}
                        """));
        when(client.scheduleLeague("4328", "2026")).thenReturn(response(TheSportsDbEndpoint.SCHEDULE_LEAGUE,
                "schedule/league/4328/2026",
                "{\"events\":[]}"));
        when(teamRepository.countByLeague_Code(LeagueCode.PREMIER_LEAGUE)).thenReturn(1L);
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(LeagueCode.PREMIER_LEAGUE, "arsenal"))
                .thenReturn(Optional.of(alias(arsenal, "Arsenal")));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TheSportsDbImportSummary summary = service.importLeagueSeason(LeagueCode.PREMIER_LEAGUE, "4328", "2026");

        assertThat(summary.teamsResolved()).isEqualTo(1);
        assertThat(arsenal.getBadgeUrl()).isEqualTo("https://cdn.test/existing-badge.png");
        assertThat(arsenal.getLogoUrl()).isEqualTo("https://cdn.test/arsenal-logo.png");
        assertThat(arsenal.getBannerUrl()).isEqualTo("https://cdn.test/arsenal-banner.png");
        verify(teamRepository).save(argThat(team -> team == arsenal
                && "https://cdn.test/existing-badge.png".equals(team.getBadgeUrl())
                && "https://cdn.test/arsenal-logo.png".equals(team.getLogoUrl())));
    }

    @Test
    void doesNotOverwriteExistingLeagueArtworkWithBlankValues() {
        league.setBadgeUrl("https://cdn.test/existing-league-badge.png");
        when(client.lookupLeague("4328")).thenReturn(response(TheSportsDbEndpoint.LOOKUP_LEAGUE, "lookup/league/4328",
                """
                        {"lookup":[{
                          "idLeague":"4328",
                          "strLeague":"English Premier League",
                          "strBadge":"",
                          "strLogo":"https://cdn.test/new-league-logo.png"
                        }]}
                        """));
        when(client.listSeasons("4328")).thenReturn(response(TheSportsDbEndpoint.LIST_SEASONS, "list/seasons/4328",
                "{\"seasons\":[{\"strSeason\":\"2026\"}]}"));
        when(client.listTeams("4328")).thenReturn(response(TheSportsDbEndpoint.LIST_TEAMS, "list/teams/4328",
                "{\"teams\":[]}"));
        when(client.scheduleLeague("4328", "2026")).thenReturn(response(TheSportsDbEndpoint.SCHEDULE_LEAGUE,
                "schedule/league/4328/2026",
                "{\"events\":[]}"));
        when(teamRepository.countByLeague_Code(LeagueCode.PREMIER_LEAGUE)).thenReturn(1L);
        when(leagueRepository.save(any(League.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.importLeagueSeason(LeagueCode.PREMIER_LEAGUE, "4328", "2026");

        assertThat(league.getBadgeUrl()).isEqualTo("https://cdn.test/existing-league-badge.png");
        assertThat(league.getLogoUrl()).isEqualTo("https://cdn.test/new-league-logo.png");
        verify(leagueRepository).save(argThat(saved -> saved == league
                && "https://cdn.test/existing-league-badge.png".equals(saved.getBadgeUrl())
                && "https://cdn.test/new-league-logo.png".equals(saved.getLogoUrl())));
    }

    private TheSportsDbClientResponse response(TheSportsDbEndpoint endpoint, String path, String rawJson) {
        return new TheSportsDbClientResponse(endpoint, path, 200, OffsetDateTime.parse("2026-06-20T10:00:00Z"), rawJson);
    }

    private League league() {
        League league = new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1)
                .setCurrentSeason("2026");
        league.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        return league;
    }

    private Team team(String id, String name) {
        Team team = new Team()
                .setLeague(league)
                .setCanonicalName(name)
                .setShortName(name)
                .setCountry("England")
                .setExternalKey("TEST:" + name)
                .setActive(true);
        team.setId(UUID.fromString(id));
        return team;
    }

    private TeamAlias alias(Team team, String alias) {
        return new TeamAlias()
                .setLeague(league)
                .setTeam(team)
                .setAlias(alias)
                .setAliasNormalized(alias.toLowerCase())
                .setSourceName("test");
    }
}
