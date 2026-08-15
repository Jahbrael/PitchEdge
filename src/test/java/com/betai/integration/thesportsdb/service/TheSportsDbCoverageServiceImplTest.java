package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.market.MarketDirection;
import com.betai.domain.market.MarketPeriod;
import com.betai.domain.market.MarketTargetType;
import com.betai.domain.market.MarketTeamScope;
import com.betai.domain.market.MarketType;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.source.CoverageLevel;
import com.betai.domain.source.LeagueSeasonCoverage;
import com.betai.domain.source.LeagueSeasonMarketAvailability;
import com.betai.integration.thesportsdb.TheSportsDbCoverageProperties;
import com.betai.repository.EventStatisticRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.LeagueSeasonCoverageRepository;
import com.betai.repository.LeagueSeasonMarketAvailabilityRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TheSportsDbCoverageServiceImplTest {

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private EventStatisticRepository eventStatisticRepository;
    @Mock
    private MarketDefinitionRepository marketDefinitionRepository;
    @Mock
    private LeagueSeasonCoverageRepository coverageRepository;
    @Mock
    private LeagueSeasonMarketAvailabilityRepository marketAvailabilityRepository;

    private TheSportsDbCoverageServiceImpl service;
    private League league;

    @BeforeEach
    void setUp() {
        league = new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1)
                .setCurrentSeason("2026");
        league.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        service = new TheSportsDbCoverageServiceImpl(
                leagueRepository,
                matchRepository,
                eventStatisticRepository,
                marketDefinitionRepository,
                coverageRepository,
                marketAvailabilityRepository,
                new TheSportsDbCoverageProperties(new BigDecimal("0.80"), new BigDecimal("0.30")),
                Clock.fixed(Instant.parse("2026-06-20T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void sparseCornerCoverageDisablesCornerMarketsOnly() {
        when(leagueRepository.findByCode(LeagueCode.PREMIER_LEAGUE)).thenReturn(Optional.of(league));
        when(matchRepository.countByLeague_CodeAndSeasonLabel(LeagueCode.PREMIER_LEAGUE, "2026")).thenReturn(10L);
        when(matchRepository.countByLeague_CodeAndSeasonLabelAndStatus(
                LeagueCode.PREMIER_LEAGUE,
                "2026",
                MatchStatus.FINISHED
        )).thenReturn(10L);
        when(eventStatisticRepository.countDistinctMatchesWithAnyStatistic(LeagueCode.PREMIER_LEAGUE, "2026"))
                .thenReturn(10L);
        when(eventStatisticRepository.countDistinctMatchesWithStatistic(LeagueCode.PREMIER_LEAGUE, "2026", "CORNERS"))
                .thenReturn(2L);
        when(eventStatisticRepository.countDistinctMatchesWithStatistic(LeagueCode.PREMIER_LEAGUE, "2026", "YELLOW_CARDS"))
                .thenReturn(8L);
        when(eventStatisticRepository.countDistinctMatchesWithStatistic(LeagueCode.PREMIER_LEAGUE, "2026", "RED_CARDS"))
                .thenReturn(8L);
        when(coverageRepository.findByLeague_CodeAndSeasonLabel(LeagueCode.PREMIER_LEAGUE, "2026"))
                .thenReturn(Optional.empty());
        when(coverageRepository.save(any(LeagueSeasonCoverage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(marketDefinitionRepository.findByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of(
                market(MarketCode.CORNERS_OVER_8_5, MarketType.TOTAL_CORNERS),
                market(MarketCode.HOME_WIN, MarketType.MATCH_RESULT)
        ));
        when(marketAvailabilityRepository.findByLeague_CodeAndSeasonLabelAndMarketCode(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(marketAvailabilityRepository.save(any(LeagueSeasonMarketAvailability.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LeagueSeasonCoverage coverage = service.recalculate(LeagueCode.PREMIER_LEAGUE, "2026");

        ArgumentCaptor<LeagueSeasonMarketAvailability> availabilityCaptor =
                ArgumentCaptor.forClass(LeagueSeasonMarketAvailability.class);
        org.mockito.Mockito.verify(marketAvailabilityRepository, org.mockito.Mockito.times(2))
                .save(availabilityCaptor.capture());

        assertThat(coverage.getCornersCoverageLevel()).isEqualTo(CoverageLevel.SPARSE);
        LeagueSeasonMarketAvailability corners = availabilityCaptor.getAllValues().stream()
                .filter(value -> value.getMarketCode() == MarketCode.CORNERS_OVER_8_5)
                .findFirst()
                .orElseThrow();
        LeagueSeasonMarketAvailability result = availabilityCaptor.getAllValues().stream()
                .filter(value -> value.getMarketCode() == MarketCode.HOME_WIN)
                .findFirst()
                .orElseThrow();
        assertThat(corners.isAvailable()).isFalse();
        assertThat(corners.getReason()).contains("Corner coverage");
        assertThat(result.isAvailable()).isTrue();
    }

    private MarketDefinition market(MarketCode code, MarketType family) {
        return new MarketDefinition()
                .setCode(code)
                .setDisplayName(code.getDisplayName())
                .setMarketType(family)
                .setMarketFamily(family)
                .setDirection(MarketDirection.YES)
                .setSelectionValue("YES")
                .setPeriod(MarketPeriod.FULL_TIME)
                .setTeamScope(MarketTeamScope.MATCH)
                .setTargetType(MarketTargetType.RESULT)
                .setEnabled(true)
                .setActive(true)
                .setMinimumSampleSize(10)
                .setSettlementDescription("test");
    }
}
