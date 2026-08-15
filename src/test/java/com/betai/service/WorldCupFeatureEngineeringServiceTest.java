package com.betai.service;

import com.betai.api.dto.DailyFeatureGenerationRequest;
import com.betai.domain.feature.FeatureGroup;
import com.betai.domain.feature.FeatureGenerationRun;
import com.betai.domain.feature.HistoricalDepthStatus;
import com.betai.domain.feature.LeagueBaseline;
import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.feature.TeamFeatureSnapshot;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.team.Team;
import com.betai.repository.FeatureGenerationRunRepository;
import com.betai.repository.LeagueBaselineRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.TeamFeatureSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorldCupFeatureEngineeringServiceTest {

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private LeagueBaselineRepository leagueBaselineRepository;
    @Mock
    private TeamFeatureSnapshotRepository teamFeatureSnapshotRepository;
    @Mock
    private FeatureGenerationRunRepository featureGenerationRunRepository;
    @Mock
    private HistoricalSeasonWindowService historicalSeasonWindowService;

    private FeatureEngineeringServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FeatureEngineeringServiceImpl(
                leagueRepository,
                matchRepository,
                leagueBaselineRepository,
                teamFeatureSnapshotRepository,
                featureGenerationRunRepository,
                historicalSeasonWindowService,
                new CompetitionHistoryPolicyService(),
                Clock.fixed(Instant.parse("2026-06-14T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void worldCupUsesOverallTeamRatesForListedHomeAndAwaySides() {
        League league = league();
        Team teamA = team(league, "Brazil");
        Team teamB = team(league, "Morocco");
        Team teamC = team(league, "Japan");
        Match homeMatch = match(league, teamA, teamB, 2, 0, LocalDate.of(2026, 6, 1));
        Match awayMatch = match(league, teamC, teamA, 3, 0, LocalDate.of(2026, 6, 5));
        ArgumentCaptor<TeamFeatureSnapshot> snapshotCaptor = ArgumentCaptor.forClass(TeamFeatureSnapshot.class);

        when(leagueRepository.findByCodeInAndActiveTrue(Set.of(LeagueCode.FIFA_WORLD_CUP_2026))).thenReturn(List.of(league));
        when(historicalSeasonWindowService.resolveWindow(
                eq(league),
                eq(LocalDate.of(2026, 6, 14)),
                any(),
                any(),
                any(),
                eq(FeatureGroup.RESULTS)
        )).thenReturn(window());
        when(featureGenerationRunRepository.save(any(FeatureGenerationRun.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(matchRepository.findFinishedMatchesForFeatureGenerationDateWindow(
                LeagueCode.FIFA_WORLD_CUP_2026,
                LocalDate.of(2022, 6, 14),
                LocalDate.of(2026, 6, 14)
        )).thenReturn(List.of(homeMatch, awayMatch));
        when(leagueBaselineRepository.findByLeague_CodeAndCalculationDateAndSeasonWindowKey(
                LeagueCode.FIFA_WORLD_CUP_2026,
                LocalDate.of(2026, 6, 14),
                "CURRENT_AND_RECENT_COMPLETE:4:RESULTS:ROLLING_YEARS_4Y_2022-06-14_2026-06-14"
        )).thenReturn(Optional.empty());
        when(leagueBaselineRepository.save(any(LeagueBaseline.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(teamFeatureSnapshotRepository.findByLeague_CodeAndTeam_IdAndCalculationDateAndSeasonWindowKey(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(teamFeatureSnapshotRepository.save(snapshotCaptor.capture())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        service.generateFeatures(new DailyFeatureGenerationRequest(
                Set.of(LeagueCode.FIFA_WORLD_CUP_2026),
                LocalDate.of(2026, 6, 14),
                true,
                null,
                null,
                null
        ));

        TeamFeatureSnapshot brazil = snapshotCaptor.getAllValues().stream()
                .filter(snapshot -> snapshot.getTeam().getId().equals(teamA.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(brazil.getGoalsForPerMatch()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(brazil.getHomeGoalsForPerMatch()).isEqualByComparingTo(brazil.getGoalsForPerMatch());
        assertThat(brazil.getAwayGoalsForPerMatch()).isEqualByComparingTo(brazil.getGoalsForPerMatch());
        assertThat(brazil.getHomeGoalsAgainstPerMatch()).isEqualByComparingTo(brazil.getGoalsAgainstPerMatch());
        assertThat(brazil.getAwayGoalsAgainstPerMatch()).isEqualByComparingTo(brazil.getGoalsAgainstPerMatch());
        verify(matchRepository, never()).findFinishedMatchesForFeatureGenerationWindow(any(), any(), any());
    }

    private HistoricalSeasonWindow window() {
        return new HistoricalSeasonWindow(
                4,
                true,
                1,
                1,
                4,
                4,
                SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE,
                List.of("ROLLING_YEARS_4Y_2022-06-14_2026-06-14"),
                List.of("ROLLING_YEARS_4Y_2022-06-14_2026-06-14"),
                true,
                false,
                LocalDate.of(2022, 6, 14),
                LocalDate.of(2026, 6, 14),
                2,
                FeatureGroup.RESULTS,
                4,
                "RESULTS:FULL",
                HistoricalDepthStatus.FULL_REQUESTED_DEPTH,
                "test-recency-v1",
                List.of(BigDecimal.ONE),
                "CURRENT_AND_RECENT_COMPLETE:4:RESULTS:ROLLING_YEARS_4Y_2022-06-14_2026-06-14"
        );
    }

    private League league() {
        return withId(new League()
                .setCode(LeagueCode.FIFA_WORLD_CUP_2026)
                .setName("FIFA World Cup 2026")
                .setCountry("International")
                .setTier(1)
                .setCurrentSeason("2026"));
    }

    private Team team(League league, String name) {
        return withId(new Team()
                .setLeague(league)
                .setCanonicalName(name)
                .setShortName(name)
                .setCountry(name)
                .setActive(true));
    }

    private Match match(League league, Team home, Team away, int homeScore, int awayScore, LocalDate date) {
        return withId(new Match()
                .setLeague(league)
                .setHomeTeam(home)
                .setAwayTeam(away)
                .setMatchDate(date)
                .setKickoffAt(date.atTime(18, 0).atOffset(ZoneOffset.UTC))
                .setStatus(MatchStatus.FINISHED)
                .setHomeScore(homeScore)
                .setAwayScore(awayScore)
                .setSeasonLabel("2026")
                .setSourceFixtureKey(date + ":" + home.getCanonicalName() + ":" + away.getCanonicalName()));
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
