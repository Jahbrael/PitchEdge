package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalMappingStatus;
import com.betai.domain.source.ExternalSourceMapping;
import com.betai.domain.source.ExternalSourceType;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.domain.statistics.EventStatistic;
import com.betai.domain.statistics.MatchStatistics;
import com.betai.domain.team.Team;
import com.betai.integration.thesportsdb.TheSportsDbProperties;
import com.betai.integration.thesportsdb.client.TheSportsDbClient;
import com.betai.integration.thesportsdb.client.TheSportsDbClientResponse;
import com.betai.integration.thesportsdb.client.TheSportsDbEndpoint;
import com.betai.integration.thesportsdb.mapper.TheSportsDbMapper;
import com.betai.repository.EventStatisticRepository;
import com.betai.repository.ExternalSourceMappingRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.MatchStatisticsRepository;
import com.betai.repository.SourceTargetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TheSportsDbEventEnrichmentServiceImplTest {

    @Mock
    private TheSportsDbClient client;
    @Mock
    private TheSportsDbSnapshotService snapshotService;
    @Mock
    private ExternalSourceMappingRepository externalSourceMappingRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private SourceTargetRepository sourceTargetRepository;
    @Mock
    private EventStatisticRepository eventStatisticRepository;
    @Mock
    private MatchStatisticsRepository matchStatisticsRepository;

    private TheSportsDbEventEnrichmentServiceImpl service;

    @BeforeEach
    void setUp() {
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
        service = new TheSportsDbEventEnrichmentServiceImpl(
                client,
                new TheSportsDbMapper(new ObjectMapper()),
                snapshotService,
                externalSourceMappingRepository,
                matchRepository,
                sourceTargetRepository,
                eventStatisticRepository,
                matchStatisticsRepository,
                properties
        );
    }

    @Test
    void importsGenericEventStatsAndUpdatesFixedMatchStatistics() {
        Match match = match();
        ExternalSourceMapping mapping = new ExternalSourceMapping()
                .setSourceType(ExternalSourceType.THESPORTSDB)
                .setEntityType(ExternalEntityType.EVENT)
                .setExternalEntityId("E1")
                .setInternalEntityId(match.getId())
                .setStatus(ExternalMappingStatus.RESOLVED);
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.EVENT,
                "E1"
        )).thenReturn(Optional.of(mapping));
        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));
        when(sourceTargetRepository.findByLeague_CodeAndSourceTypeAndName(any(), any(SourceType.class), any()))
                .thenReturn(Optional.empty());
        when(sourceTargetRepository.save(any(SourceTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.lookupEventStats("E1")).thenReturn(new TheSportsDbClientResponse(
                TheSportsDbEndpoint.LOOKUP_EVENT_STATS,
                "lookup/event_stats/E1",
                200,
                OffsetDateTime.parse("2026-06-20T10:00:00Z"),
                """
                        {"eventstats":[
                          {"strStat":"Shots on Target","intHome":"5","intAway":"2"},
                          {"strStat":"Corners","intHome":"7","intAway":"3"},
                          {"strStat":"Mystery Pressure","intHome":"","intAway":null}
                        ]}
                        """
        ));
        when(snapshotService.persist(any(TheSportsDbClientResponse.class), any(TheSportsDbSnapshotMetadata.class)))
                .thenReturn(new RawSnapshot());
        when(eventStatisticRepository.findTheSportsDbStatistic(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(eventStatisticRepository.save(any(EventStatistic.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchStatisticsRepository.findByMatch_Id(match.getId())).thenReturn(Optional.empty());
        when(matchStatisticsRepository.save(any(MatchStatistics.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var summary = service.importEventStatistics("E1");

        ArgumentCaptor<EventStatistic> statisticCaptor = ArgumentCaptor.forClass(EventStatistic.class);
        org.mockito.Mockito.verify(eventStatisticRepository, org.mockito.Mockito.times(6)).save(statisticCaptor.capture());
        ArgumentCaptor<MatchStatistics> fixedCaptor = ArgumentCaptor.forClass(MatchStatistics.class);
        org.mockito.Mockito.verify(matchStatisticsRepository).save(fixedCaptor.capture());

        assertThat(summary.statisticsImported()).isEqualTo(6);
        assertThat(summary.fixedMatchStatisticsUpdated()).isEqualTo(1);
        assertThat(statisticCaptor.getAllValues())
                .filteredOn(stat -> stat.getStatisticCode().equals("SOURCE_MYSTERY_PRESSURE"))
                .allSatisfy(stat -> assertThat(stat.getNumericValue()).isNull());
        MatchStatistics fixed = fixedCaptor.getValue();
        assertThat(fixed.getHomeShotsOnTarget()).isEqualTo(5);
        assertThat(fixed.getAwayShotsOnTarget()).isEqualTo(2);
        assertThat(fixed.getHomeCorners()).isEqualTo(7);
        assertThat(fixed.getAwayCorners()).isEqualTo(3);
    }

    @Test
    void importsEventStatsForKnownMatchWhenEventMappingDoesNotExistYet() {
        Match match = match();
        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.EVENT,
                "E1"
        )).thenReturn(Optional.empty());
        when(externalSourceMappingRepository.save(any(ExternalSourceMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sourceTargetRepository.findByLeague_CodeAndSourceTypeAndName(any(), any(SourceType.class), any()))
                .thenReturn(Optional.empty());
        when(sourceTargetRepository.save(any(SourceTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.lookupEventStats("E1")).thenReturn(new TheSportsDbClientResponse(
                TheSportsDbEndpoint.LOOKUP_EVENT_STATS,
                "lookup/event_stats/E1",
                200,
                OffsetDateTime.parse("2026-06-20T10:00:00Z"),
                """
                        {"eventstats":[
                          {"strStat":"Shots on Target","intHome":"4","intAway":"1"},
                          {"strStat":"Possession","intHome":"61","intAway":"39"}
                        ]}
                        """
        ));
        when(snapshotService.persist(any(TheSportsDbClientResponse.class), any(TheSportsDbSnapshotMetadata.class)))
                .thenReturn(new RawSnapshot());
        when(eventStatisticRepository.findTheSportsDbStatistic(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(eventStatisticRepository.save(any(EventStatistic.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchStatisticsRepository.findByMatch_Id(match.getId())).thenReturn(Optional.empty());
        when(matchStatisticsRepository.save(any(MatchStatistics.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var summary = service.importEventStatisticsForMatch(match.getId(), "E1");

        ArgumentCaptor<ExternalSourceMapping> mappingCaptor = ArgumentCaptor.forClass(ExternalSourceMapping.class);
        org.mockito.Mockito.verify(externalSourceMappingRepository).save(mappingCaptor.capture());
        ArgumentCaptor<MatchStatistics> fixedCaptor = ArgumentCaptor.forClass(MatchStatistics.class);
        org.mockito.Mockito.verify(matchStatisticsRepository).save(fixedCaptor.capture());

        assertThat(summary.statisticsImported()).isEqualTo(4);
        assertThat(summary.fixedMatchStatisticsUpdated()).isEqualTo(1);
        ExternalSourceMapping savedMapping = mappingCaptor.getValue();
        assertThat(savedMapping.getExternalEntityId()).isEqualTo("E1");
        assertThat(savedMapping.getInternalEntityId()).isEqualTo(match.getId());
        assertThat(savedMapping.getStatus()).isEqualTo(ExternalMappingStatus.RESOLVED);
        MatchStatistics fixed = fixedCaptor.getValue();
        assertThat(fixed.getHomeShotsOnTarget()).isEqualTo(4);
        assertThat(fixed.getAwayShotsOnTarget()).isEqualTo(1);
        assertThat(fixed.getHomePossession()).isEqualTo(61);
        assertThat(fixed.getAwayPossession()).isEqualTo(39);
    }

    private Match match() {
        League league = new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1)
                .setCurrentSeason("2026");
        league.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Team home = team(league, "00000000-0000-0000-0000-000000000002", "Home FC");
        Team away = team(league, "00000000-0000-0000-0000-000000000003", "Away FC");
        Match match = new Match()
                .setLeague(league)
                .setHomeTeam(home)
                .setAwayTeam(away)
                .setMatchDate(LocalDate.parse("2026-06-20"))
                .setKickoffAt(OffsetDateTime.parse("2026-06-20T10:00:00Z"))
                .setStatus(MatchStatus.FINISHED)
                .setSeasonLabel("2026")
                .setSourceFixtureKey("TSD:E1");
        match.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        return match;
    }

    private Team team(League league, String id, String name) {
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
}
