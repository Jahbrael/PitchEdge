package com.betai.service;

import com.betai.api.dto.UpcomingFixtureImportItem;
import com.betai.api.dto.UpcomingFixtureImportRequest;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.team.Team;
import com.betai.domain.team.TeamAlias;
import com.betai.exception.InvalidRequestException;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.TeamAliasRepository;
import com.betai.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpcomingFixtureImportServiceImplTest {

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamAliasRepository teamAliasRepository;
    @Mock
    private MatchRepository matchRepository;

    private UpcomingFixtureImportServiceImpl service;
    private League league;

    @BeforeEach
    void setUp() {
        service = new UpcomingFixtureImportServiceImpl(
                leagueRepository,
                teamRepository,
                teamAliasRepository,
                matchRepository,
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
    void importsStructuredFixtureAsScheduledMatch() {
        when(leagueRepository.findByCode(LeagueCode.PREMIER_LEAGUE)).thenReturn(Optional.of(league));
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(eq(LeagueCode.PREMIER_LEAGUE), anyString()))
                .thenReturn(Optional.empty());
        when(teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(eq(LeagueCode.PREMIER_LEAGUE), anyString()))
                .thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team team = invocation.getArgument(0);
            team.setId(UUID.randomUUID());
            return team;
        });
        when(teamAliasRepository.save(any(TeamAlias.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.findByLeague_CodeAndSourceFixtureKeySafely(eq(LeagueCode.PREMIER_LEAGUE), anyString()))
                .thenReturn(Optional.empty());
        when(matchRepository.findByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndKickoffAtSafely(
                eq(LeagueCode.PREMIER_LEAGUE),
                any(UUID.class),
                any(UUID.class),
                any()
        )).thenReturn(Optional.empty());
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match match = invocation.getArgument(0);
            match.setId(UUID.randomUUID());
            return match;
        });

        var response = service.importFixtures(new UpcomingFixtureImportRequest(
                LeagueCode.PREMIER_LEAGUE,
                "2026/2027",
                List.of(new UpcomingFixtureImportItem(
                        "Arsenal",
                        "Chelsea",
                        LocalDate.parse("2026-08-15"),
                        LocalTime.parse("15:00"),
                        "Matchweek 1",
                        "Emirates Stadium",
                        "OFFICIAL:EPL:2026-08-15:ARS-CHE"
                ))
        ));

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        Match savedMatch = matchCaptor.getValue();

        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.updatedCount()).isZero();
        assertThat(response.fixtures()).hasSize(1);
        assertThat(savedMatch.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
        assertThat(savedMatch.getHomeScore()).isNull();
        assertThat(savedMatch.getAwayScore()).isNull();
        assertThat(savedMatch.getSeasonLabel()).isEqualTo("2026/2027");
        assertThat(savedMatch.getRoundLabel()).isEqualTo("Matchweek 1");
        assertThat(savedMatch.getVenue()).isEqualTo("Emirates Stadium");
        assertThat(savedMatch.getSourceFixtureKey()).isEqualTo("OFFICIAL:EPL:2026-08-15:ARS-CHE");
    }

    @Test
    void rejectsSameTeamFixture() {
        when(leagueRepository.findByCode(LeagueCode.PREMIER_LEAGUE)).thenReturn(Optional.of(league));

        var request = new UpcomingFixtureImportRequest(
                LeagueCode.PREMIER_LEAGUE,
                "2026/2027",
                List.of(new UpcomingFixtureImportItem(
                        "Arsenal",
                        "Arsenal",
                        LocalDate.parse("2026-08-15"),
                        LocalTime.parse("15:00"),
                        null,
                        null,
                        null
                ))
        );

        assertThatThrownBy(() -> service.importFixtures(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("homeTeam and awayTeam cannot refer to the same team");
    }
}
