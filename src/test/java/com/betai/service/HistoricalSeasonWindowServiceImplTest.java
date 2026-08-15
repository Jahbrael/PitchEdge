package com.betai.service;

import com.betai.config.PredictionProperties;
import com.betai.domain.feature.FeatureGroup;
import com.betai.domain.feature.HistoricalDepthStatus;
import com.betai.domain.feature.InsufficientSeasonPolicy;
import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.source.CoverageLevel;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalSourceType;
import com.betai.domain.source.LeagueSeasonCoverage;
import com.betai.exception.InvalidRequestException;
import com.betai.repository.ExternalSourceMappingRepository;
import com.betai.repository.LeagueSeasonCoverageRepository;
import com.betai.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalSeasonWindowServiceImplTest {

    private static final LocalDate CUTOFF = LocalDate.parse("2026-06-20");

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private LeagueSeasonCoverageRepository coverageRepository;
    @Mock
    private ExternalSourceMappingRepository externalSourceMappingRepository;

    private HistoricalSeasonWindowServiceImpl service;
    private League league;

    @BeforeEach
    void setUp() {
        service = new HistoricalSeasonWindowServiceImpl(
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
                matchRepository,
                coverageRepository,
                externalSourceMappingRepository,
                new ExponentialSeasonRecencyWeightingPolicy(),
                new CompetitionHistoryPolicyService()
        );
        league = new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1)
                .setCurrentSeason("2025/2026");
        league.setId(UUID.randomUUID());

        lenient().when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndLeague_Id(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.SEASON,
                league.getId()
        )).thenReturn(List.of());
    }

    @Test
    void fiveSeasonRequestUsesFiveMostRecentUsableSeasonsWhenAvailable() {
        stubImportedSeasons("2025/2026", "2024/2025", "2023/2024", "2022/2023", "2021/2022", "2020/2021");
        stubCompletedMatches(Map.of(
                "2025/2026", 30,
                "2024/2025", 38,
                "2023/2024", 38,
                "2022/2023", 38,
                "2021/2022", 38,
                "2020/2021", 38
        ));

        HistoricalSeasonWindow window = service.resolveWindow(
                league,
                CUTOFF,
                5,
                SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE,
                null,
                FeatureGroup.RESULTS
        );

        assertThat(window.requestedSeasonCount()).isEqualTo(5);
        assertThat(window.actualSeasonCountUsed()).isEqualTo(5);
        assertThat(window.selectedSeasonIds()).containsExactly(
                "2025/2026", "2024/2025", "2023/2024", "2022/2023", "2021/2022"
        );
        assertThat(window.fallbackApplied()).isFalse();
        assertThat(window.historicalDepthStatus()).isEqualTo(HistoricalDepthStatus.FULL_REQUESTED_DEPTH);
    }

    @Test
    void fiveSeasonRequestFallsBackToThreeUsableSeasonsWhenOnlyThreeExist() {
        stubImportedSeasons("2025/2026", "2024/2025", "2023/2024", "2022/2023", "2021/2022");
        stubCompletedMatches(Map.of(
                "2025/2026", 30,
                "2024/2025", 38,
                "2023/2024", 38,
                "2022/2023", 0,
                "2021/2022", 0
        ));

        HistoricalSeasonWindow window = service.resolveWindow(
                league,
                CUTOFF,
                5,
                SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE,
                null,
                FeatureGroup.RESULTS
        );

        assertThat(window.actualSeasonCountUsed()).isEqualTo(3);
        assertThat(window.selectedSeasonIds()).containsExactly("2025/2026", "2024/2025", "2023/2024");
        assertThat(window.fallbackApplied()).isTrue();
        assertThat(window.historicalDepthStatus()).isEqualTo(HistoricalDepthStatus.REDUCED_AVAILABLE_DEPTH);
    }

    @Test
    void unusableSeasonsAreNotCountedTowardActualDepth() {
        stubImportedSeasons("2025/2026", "2024/2025", "2023/2024");
        stubCompletedMatches(Map.of(
                "2025/2026", 30,
                "2024/2025", 9,
                "2023/2024", 38
        ));

        HistoricalSeasonWindow window = service.resolveWindow(
                league,
                CUTOFF,
                3,
                SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE,
                null,
                FeatureGroup.RESULTS
        );

        assertThat(window.actualSeasonCountUsed()).isEqualTo(2);
        assertThat(window.selectedSeasonIds()).containsExactly("2025/2026", "2023/2024");
        assertThat(window.usableSeasonsFound()).isEqualTo(2);
    }

    @Test
    void currentSeasonUsesOnlyMatchesCompletedBeforePredictionCutoff() {
        stubImportedSeasons("2025/2026", "2024/2025");
        stubCompletedMatches(Map.of(
                "2025/2026", 5,
                "2024/2025", 38
        ));

        HistoricalSeasonWindow window = service.resolveWindow(
                league,
                CUTOFF,
                2,
                SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE,
                null,
                FeatureGroup.RESULTS
        );

        assertThat(window.selectedSeasonIds()).containsExactly("2024/2025");
        assertThat(window.currentSeasonIncluded()).isFalse();
        verify(matchRepository, atLeastOnce()).countByLeague_CodeAndSeasonLabelAndStatusAndMatchDateLessThanEqual(
                LeagueCode.PREMIER_LEAGUE,
                "2025/2026",
                MatchStatus.FINISHED,
                CUTOFF
        );
    }

    @Test
    void marketSpecificSeasonUsabilityCanDifferByCoverage() {
        stubImportedSeasons("2025/2026", "2024/2025", "2023/2024");
        stubCompletedMatches(Map.of(
                "2025/2026", 30,
                "2024/2025", 38,
                "2023/2024", 38
        ));
        when(coverageRepository.findByLeague_CodeAndSeasonLabel(eq(LeagueCode.PREMIER_LEAGUE), anyString()))
                .thenAnswer(invocation -> {
                    String season = invocation.getArgument(1);
                    CoverageLevel corners = switch (season) {
                        case "2025/2026" -> CoverageLevel.FULL;
                        case "2024/2025" -> CoverageLevel.SPARSE;
                        default -> CoverageLevel.UNAVAILABLE;
                    };
                    return Optional.of(coverage(corners));
                });

        HistoricalSeasonWindow goalsWindow = service.resolveWindow(
                league,
                CUTOFF,
                3,
                SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE,
                null,
                FeatureGroup.GOALS
        );
        HistoricalSeasonWindow cornersWindow = service.resolveWindow(
                league,
                CUTOFF,
                3,
                SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE,
                null,
                FeatureGroup.CORNERS
        );

        assertThat(goalsWindow.actualSeasonCountUsed()).isEqualTo(3);
        assertThat(cornersWindow.actualSeasonCountUsed()).isEqualTo(1);
        assertThat(cornersWindow.marketSpecificDataCoverage()).isEqualTo("CORNERS:FULL");
    }

    @Test
    void requestedSeasonCountAboveMaximumIsRejected() {
        assertThatThrownBy(() -> service.resolveWindow(
                league,
                CUTOFF,
                11,
                SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE,
                null,
                FeatureGroup.RESULTS
        )).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("requestedSeasonCount must be less than or equal to 10");
    }

    @Test
    void worldCupUsesRollingYearWindowInsteadOfSeasonLabels() {
        League worldCup = new League()
                .setCode(LeagueCode.FIFA_WORLD_CUP_2026)
                .setName("FIFA World Cup 2026")
                .setCountry("International")
                .setTier(1)
                .setCurrentSeason("2026");
        worldCup.setId(UUID.randomUUID());
        when(matchRepository.countFinishedMatchesByLeagueCodeAndMatchDateBetween(
                LeagueCode.FIFA_WORLD_CUP_2026,
                LocalDate.parse("2022-06-20"),
                CUTOFF
        )).thenReturn(1778L);

        HistoricalSeasonWindow window = service.resolveWindow(
                worldCup,
                CUTOFF,
                null,
                SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE,
                null,
                FeatureGroup.RESULTS
        );

        assertThat(window.actualSeasonCountUsed()).isEqualTo(4);
        assertThat(window.selectedSeasonIds())
                .containsExactly("ROLLING_YEARS_4Y_2022-06-20_2026-06-20");
        assertThat(window.oldestDataDate()).isEqualTo(LocalDate.parse("2022-06-20"));
        assertThat(window.newestDataDate()).isEqualTo(CUTOFF);
        assertThat(window.completedMatchesUsed()).isEqualTo(1778);
        assertThat(window.historicalDepthStatus()).isEqualTo(HistoricalDepthStatus.FULL_REQUESTED_DEPTH);
        assertThat(window.seasonWindowKey()).contains("ROLLING_YEARS_4Y_2022-06-20_2026-06-20");
    }

    private void stubImportedSeasons(String... seasons) {
        when(matchRepository.findDistinctSeasonLabelsByLeagueCode(LeagueCode.PREMIER_LEAGUE))
                .thenReturn(List.of(seasons));
        when(matchRepository.findFinishedMatchesForFeatureGenerationWindow(
                eq(LeagueCode.PREMIER_LEAGUE),
                any(),
                eq(CUTOFF)
        )).thenReturn(List.of());
    }

    private void stubCompletedMatches(Map<String, Integer> countsBySeason) {
        when(matchRepository.countByLeague_CodeAndSeasonLabelAndStatusAndMatchDateLessThanEqual(
                eq(LeagueCode.PREMIER_LEAGUE),
                anyString(),
                eq(MatchStatus.FINISHED),
                eq(CUTOFF)
        )).thenAnswer(invocation -> countsBySeason.getOrDefault(invocation.getArgument(1), 0).longValue());
    }

    private LeagueSeasonCoverage coverage(CoverageLevel cornersLevel) {
        LeagueSeasonCoverage coverage = new LeagueSeasonCoverage()
                .setLeague(league)
                .setSeasonLabel("2025/2026")
                .setHasFixtures(true)
                .setHasResults(true)
                .setHasCorners(cornersLevel == CoverageLevel.FULL || cornersLevel == CoverageLevel.PARTIAL)
                .setHasCards(true)
                .setCompletedEventsChecked(30)
                .setEventsWithStatistics(30)
                .setCoveragePercentage(new BigDecimal("100.00"))
                .setStatisticsCoverageLevel(CoverageLevel.FULL)
                .setCornersCoverageLevel(cornersLevel)
                .setCardsCoverageLevel(CoverageLevel.FULL)
                .setLastVerifiedAt(OffsetDateTime.parse("2026-06-20T00:00:00Z"));
        coverage.setId(UUID.randomUUID());
        return coverage;
    }
}
