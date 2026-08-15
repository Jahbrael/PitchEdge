package com.betai.service;

import com.betai.api.dto.OddsImportItem;
import com.betai.api.dto.OddsImportRequest;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.odds.Bookmaker;
import com.betai.domain.odds.OddsSnapshot;
import com.betai.domain.team.Team;
import com.betai.repository.BookmakerRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.OddsSnapshotRepository;
import com.betai.repository.TeamAliasRepository;
import com.betai.repository.TeamRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OddsImportServiceImplTest {

    private final OddsImportServiceImpl service = new OddsImportServiceImpl(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

    @Mock
    private BookmakerRepository bookmakerRepository;
    @Mock
    private OddsSnapshotRepository oddsSnapshotRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MarketDefinitionRepository marketDefinitionRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamAliasRepository teamAliasRepository;
    @Mock
    private OddsValueService oddsValueService;
    @Mock
    private EntityManager entityManager;

    @Test
    void canonicalizesTheOddsApiAllsvenskanAliasesForMatchResolution() {
        assertThat(canonicalize(LeagueCode.ALLSVENSKAN, "IK Sirius")).isEqualTo("Sirius");
        assertThat(canonicalize(LeagueCode.ALLSVENSKAN, "Mjällby AIF")).isEqualTo("Mjallby");
        assertThat(canonicalize(LeagueCode.ALLSVENSKAN, "Halmstads BK")).isEqualTo("Halmstad");
        assertThat(canonicalize(LeagueCode.ALLSVENSKAN, "Västerås SK")).isEqualTo("Vasteras SK");
        assertThat(canonicalize(LeagueCode.ALLSVENSKAN, "BK Hacken")).isEqualTo("Hacken");
        assertThat(canonicalize(LeagueCode.ALLSVENSKAN, "IF Brommapojkarna")).isEqualTo("Brommapojkarna");
    }

    @Test
    void canonicalizesTheOddsApiEliteserienAliasesForMatchResolution() {
        assertThat(canonicalize(LeagueCode.ELITESERIEN, "Viking FK")).isEqualTo("Viking");
        assertThat(canonicalize(LeagueCode.ELITESERIEN, "Bodo/Glimt")).isEqualTo("Bodo/Glimt");
        assertThat(canonicalize(LeagueCode.ELITESERIEN, "Lillestrom SK")).isEqualTo("Lillestrom");
        assertThat(canonicalize(LeagueCode.ELITESERIEN, "Tromso IL")).isEqualTo("Tromso");
        assertThat(canonicalize(LeagueCode.ELITESERIEN, "Sarpsborg 08 FF")).isEqualTo("Sarpsborg 08");
    }

    @Test
    void resolvesCupMatchByFixtureTeamNamesWhenTeamsBelongToDomesticLeague() {
        OddsImportServiceImpl importService = new OddsImportServiceImpl(
                bookmakerRepository,
                oddsSnapshotRepository,
                matchRepository,
                marketDefinitionRepository,
                teamRepository,
                teamAliasRepository,
                new OddsValueCalculator(),
                oddsValueService,
                entityManager,
                Clock.fixed(Instant.parse("2026-07-08T04:20:00Z"), ZoneOffset.UTC)
        );
        Team homeTeam = team("Independiente Rivadavia");
        Team awayTeam = team("Tigre");
        Match match = withId(new Match()
                .setHomeTeam(homeTeam)
                .setAwayTeam(awayTeam)
                .setMatchDate(LocalDate.parse("2026-07-12"))
                .setKickoffAt(OffsetDateTime.parse("2026-07-12T19:15:00Z"))
                .setStatus(MatchStatus.SCHEDULED)
                .setSeasonLabel("2026")
                .setSourceFixtureKey("copa-argentina-1"));
        MarketDefinition market = withId(new MarketDefinition()
                .setCode(MarketCode.HOME_WIN)
                .setDisplayName("Home Win")
                .setEnabled(true)
                .setActive(true));
        Bookmaker bookmaker = withId(new Bookmaker()
                .setCode("FANDUEL")
                .setDisplayName("FanDuel")
                .setActive(true));

        when(entityManager.getFlushMode()).thenReturn(FlushModeType.COMMIT);
        when(teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(
                LeagueCode.COPA_ARGENTINA,
                "Independiente Rivadavia"
        )).thenReturn(Optional.empty());
        when(teamAliasRepository.findByLeague_CodeAndAliasNormalized(
                LeagueCode.COPA_ARGENTINA,
                "independiente-rivadavia"
        )).thenReturn(Optional.empty());
        when(matchRepository.findByLeague_CodeAndMatchDateBetweenOrderByKickoffAtAsc(
                LeagueCode.COPA_ARGENTINA,
                LocalDate.parse("2026-07-11"),
                LocalDate.parse("2026-07-13")
        )).thenReturn(List.of(match));
        when(marketDefinitionRepository.findByCode(MarketCode.HOME_WIN)).thenReturn(Optional.of(market));
        when(bookmakerRepository.findByCode("FANDUEL")).thenReturn(Optional.empty());
        when(bookmakerRepository.save(any(Bookmaker.class))).thenReturn(bookmaker);
        when(oddsSnapshotRepository.save(any(OddsSnapshot.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        var response = importService.importOdds(new OddsImportRequest(List.of(new OddsImportItem(
                null,
                LeagueCode.COPA_ARGENTINA,
                LocalDate.parse("2026-07-12"),
                "Independiente Rivadavia",
                "Tigre",
                MarketCode.HOME_WIN,
                "fanduel",
                "FanDuel",
                new BigDecimal("2.40"),
                OffsetDateTime.parse("2026-07-08T04:15:00Z"),
                "SharpAPI Upcoming Odds Copa Argentina JSON",
                "https://api.sharpapi.io/api/v1/odds?league=argentina_-_copa_argentina",
                "test"
        )), true));

        assertThat(response.snapshotsImported()).isEqualTo(1);
        assertThat(response.rejected()).isZero();
        assertThat(response.results()).singleElement()
                .satisfies(result -> assertThat(result.matchId()).isEqualTo(match.getId()));
    }

    @Test
    void looseFixtureNameMatchingHandlesWomenSuffixesAndProviderShortNames() {
        assertThat(compatible("Racing Louisville", "Racing Louisville FC (W)")).isTrue();
        assertThat(compatible("Orlando Pride", "Orlando Pride SC (W)")).isTrue();
        assertThat(compatible("Kansas City Current", "Kansas City (W)")).isTrue();
        assertThat(compatible("Chicago Stars", "Chicago Red Stars (W)")).isTrue();
    }

    private String canonicalize(LeagueCode leagueCode, String teamName) {
        return ReflectionTestUtils.invokeMethod(service, "canonicalizeTeamName", leagueCode, teamName);
    }

    private boolean compatible(String localName, String providerName) {
        String providerKey = ReflectionTestUtils.invokeMethod(service, "normalizeKey", providerName);
        Boolean compatible = ReflectionTestUtils.invokeMethod(service, "teamNamesCompatible", localName, providerKey);
        return Boolean.TRUE.equals(compatible);
    }

    private Team team(String name) {
        return withId(new Team()
                .setCanonicalName(name)
                .setShortName(name)
                .setCountry("Argentina")
                .setExternalKey("test:" + name)
                .setActive(true));
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
