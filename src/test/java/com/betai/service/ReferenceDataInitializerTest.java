package com.betai.service;

import com.betai.config.SharpApiProperties;
import com.betai.config.ApiFootballProperties;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.SourceTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceDataInitializerTest {

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private MarketDefinitionRepository marketDefinitionRepository;
    @Mock
    private SourceTargetRepository sourceTargetRepository;

    private ReferenceDataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new ReferenceDataInitializer(
                leagueRepository,
                marketDefinitionRepository,
                sourceTargetRepository,
                new SharpApiProperties(true, "testKey", "https://test.sharpapi.io/api/v1"),
                new ApiFootballProperties(
                        true,
                        "api-football-test-key",
                        "https://v3.football.api-sports.io",
                        "UTC"
                )
        );

        when(leagueRepository.findByCode(any())).thenAnswer(invocation ->
                Optional.of(league(invocation.getArgument(0))));
        when(marketDefinitionRepository.findByCode(any())).thenReturn(Optional.empty());
        when(sourceTargetRepository.findByLeague_CodeAndSourceTypeAndName(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(sourceTargetRepository.save(any(SourceTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(marketDefinitionRepository.save(any(MarketDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leagueRepository.save(any(League.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void theSportsDbRegistersSeasonFixturesNextFixturesAndBackfillResults() throws Exception {
        ArgumentCaptor<SourceTarget> captor = ArgumentCaptor.forClass(SourceTarget.class);

        initializer.run(null);

        verify(sourceTargetRepository, atLeastOnce()).save(captor.capture());
        List<SourceTarget> sourceTargets = captor.getAllValues();

        SourceTarget currentSeasonFixtures = singleSource(
                sourceTargets,
                LeagueCode.BESTA_DEILD,
                SourceType.FIXTURES,
                "TheSportsDB 2026 Besta deild Events JSON FIXTURES"
        );
        assertThat(currentSeasonFixtures.getUrlTemplate())
                .contains("eventsseason.php", "id=4642", "s=2026");
        assertThat(currentSeasonFixtures.getFallbackPriority()).isEqualTo(36);
        assertThat(currentSeasonFixtures.isActive()).isTrue();

        SourceTarget nextFixtures = singleSource(
                sourceTargets,
                LeagueCode.BESTA_DEILD,
                SourceType.FIXTURES,
                "TheSportsDB Next Besta deild Events JSON FIXTURES"
        );
        assertThat(nextFixtures.getUrlTemplate()).contains("eventsnextleague.php", "id=4642");
        assertThat(nextFixtures.getFallbackPriority()).isEqualTo(75);
        assertThat(nextFixtures.isActive()).isTrue();

        SourceTarget backfillResults = singleSource(
                sourceTargets,
                LeagueCode.BESTA_DEILD,
                SourceType.RESULTS,
                "TheSportsDB 2025 Besta deild Events JSON RESULTS"
        );
        assertThat(backfillResults.getUrlTemplate())
                .contains("eventsseason.php", "id=4642", "s=2025");
        assertThat(backfillResults.isActive()).isTrue();
    }

    @Test
    void theSportsDbRegistersVerifiedExpandedLeagueTargets() throws Exception {
        ArgumentCaptor<SourceTarget> captor = ArgumentCaptor.forClass(SourceTarget.class);

        initializer.run(null);

        verify(sourceTargetRepository, atLeastOnce()).save(captor.capture());
        SourceTarget currentSeasonFixtures = singleSource(
                captor.getAllValues(),
                LeagueCode.UEFA_CHAMPIONS_LEAGUE,
                SourceType.FIXTURES,
                "TheSportsDB 2026/2027 UEFA Champions League Events JSON FIXTURES"
        );

        assertThat(currentSeasonFixtures.getUrlTemplate())
                .contains("eventsseason.php", "id=4480", "s=2026-2027");
        assertThat(currentSeasonFixtures.getSelectorsJson()).contains("\"leagueId\":\"4480\"");
        assertThat(currentSeasonFixtures.isActive()).isTrue();
    }

    @Test
    void sgoddsSourcesAreNotRegisteredInAuthoritativeApiArchitecture() throws Exception {
        ArgumentCaptor<SourceTarget> captor = ArgumentCaptor.forClass(SourceTarget.class);

        initializer.run(null);

        verify(sourceTargetRepository, atLeastOnce()).save(captor.capture());
        List<SourceTarget> sourceTargets = captor.getAllValues();

        assertThat(sourceTargets)
                .noneMatch(sourceTarget -> sourceTarget.getName().contains("Sgodds"));
    }

    @Test
    void sharpApiSourcesAreActivatedWhenContainerReceivesApiKey() throws Exception {
        ArgumentCaptor<SourceTarget> captor = ArgumentCaptor.forClass(SourceTarget.class);

        initializer.run(null);

        verify(sourceTargetRepository, atLeastOnce()).save(captor.capture());
        SourceTarget source = singleSource(
                captor.getAllValues(),
                LeagueCode.BRAZILIAN_SERIE_B,
                SourceType.ODDS_REFERENCE,
                "SharpAPI Upcoming Odds Brazilian Serie B JSON"
        );

        assertThat(source.isActive()).isTrue();
        assertThat(source.getUrlTemplate()).contains("brazil_-_serie_b");
        assertThat(source.getRateLimitPerMinute()).isEqualTo(6);
    }

    @Test
    void sharpApiSourcesIncludeConfirmedExpandedLeagueMappings() throws Exception {
        ArgumentCaptor<SourceTarget> captor = ArgumentCaptor.forClass(SourceTarget.class);

        initializer.run(null);

        verify(sourceTargetRepository, atLeastOnce()).save(captor.capture());
        SourceTarget source = singleSource(
                captor.getAllValues(),
                LeagueCode.UEFA_CHAMPIONS_LEAGUE,
                SourceType.ODDS_REFERENCE,
                "SharpAPI Upcoming Odds UEFA Champions League JSON"
        );

        assertThat(source.isActive()).isTrue();
        assertThat(source.getUrlTemplate()).contains("uefa_-_champions_league");
        assertThat(source.getSelectorsJson()).contains("\"sportKey\":\"uefa_-_champions_league\"");
        assertThat(source.getRateLimitPerMinute()).isEqualTo(6);
    }

    @Test
    void sharpApiSourcesIncludeConfirmedExtraExpansionMappings() throws Exception {
        ArgumentCaptor<SourceTarget> captor = ArgumentCaptor.forClass(SourceTarget.class);

        initializer.run(null);

        verify(sourceTargetRepository, atLeastOnce()).save(captor.capture());
        SourceTarget russianPremierLeague = singleSource(
                captor.getAllValues(),
                LeagueCode.RUSSIAN_FOOTBALL_PREMIER_LEAGUE,
                SourceType.ODDS_REFERENCE,
                "SharpAPI Upcoming Odds Russian Football Premier League JSON"
        );
        SourceTarget copaDoBrasil = singleSource(
                captor.getAllValues(),
                LeagueCode.COPA_DO_BRASIL,
                SourceType.ODDS_REFERENCE,
                "SharpAPI Upcoming Odds Copa do Brasil JSON"
        );

        assertThat(russianPremierLeague.isActive()).isTrue();
        assertThat(russianPremierLeague.getUrlTemplate()).contains("russia_-_premier_league");
        assertThat(russianPremierLeague.getSelectorsJson()).contains("\"sportKey\":\"russia_-_premier_league\"");
        assertThat(copaDoBrasil.isActive()).isTrue();
        assertThat(copaDoBrasil.getUrlTemplate()).contains("brazil_-_copa_do_brasil");
        assertThat(copaDoBrasil.getRateLimitPerMinute()).isEqualTo(6);
    }

    @Test
    void sharpApiSourcesDoNotGuessUnsupportedExpandedLeagueMappings() throws Exception {
        ArgumentCaptor<SourceTarget> captor = ArgumentCaptor.forClass(SourceTarget.class);

        initializer.run(null);

        verify(sourceTargetRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .noneMatch(sourceTarget -> sourceTarget.getLeague().getCode() == LeagueCode.COPA_DEL_REY
                        && sourceTarget.getSourceType() == SourceType.ODDS_REFERENCE
                        && sourceTarget.getName().contains("SharpAPI"));
        assertThat(captor.getAllValues())
                .noneMatch(sourceTarget -> sourceTarget.getLeague().getCode() == LeagueCode.CLUB_FRIENDLIES
                        && sourceTarget.getSourceType() == SourceType.ODDS_REFERENCE
                        && sourceTarget.getName().contains("SharpAPI"));
    }

    @Test
    void apiFootballSourcesAreNotRegisteredInAuthoritativeApiArchitecture() throws Exception {
        ArgumentCaptor<SourceTarget> captor = ArgumentCaptor.forClass(SourceTarget.class);

        initializer.run(null);

        verify(sourceTargetRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .noneMatch(sourceTarget -> sourceTarget.getName().contains("API-Football"));
    }

    private SourceTarget singleSource(
            List<SourceTarget> sourceTargets,
            LeagueCode leagueCode,
            SourceType sourceType,
            String name
    ) {
        return sourceTargets.stream()
                .filter(sourceTarget -> sourceTarget.getLeague().getCode() == leagueCode)
                .filter(sourceTarget -> sourceTarget.getSourceType() == sourceType)
                .filter(sourceTarget -> sourceTarget.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private League league(LeagueCode code) {
        League league = new League()
                .setCode(code)
                .setName(code.getDisplayName())
                .setCountry(code.getCountry())
                .setTier(code.getTier())
                .setCurrentSeason(code == LeagueCode.BELGIAN_PRO_LEAGUE ? "2025/2026" :
                        code == LeagueCode.UEFA_CHAMPIONS_LEAGUE ? "2026/2027" : "2026")
                .setActive(true)
                .setScrapeEnabled(true);
        league.setId(UUID.randomUUID());
        return league;
    }
}
