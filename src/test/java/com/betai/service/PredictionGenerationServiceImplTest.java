package com.betai.service;

import com.betai.api.dto.PredictionGenerationRequest;
import com.betai.config.PredictionProperties;
import com.betai.domain.feature.FeatureGroup;
import com.betai.domain.feature.HistoricalDepthStatus;
import com.betai.domain.feature.LeagueBaseline;
import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.feature.TeamFeatureSnapshot;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.prediction.PredictionGenerationRun;
import com.betai.domain.prediction.PredictionGenerationStatus;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.domain.team.Team;
import com.betai.repository.LeagueBaselineRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.ModelQualitySnapshotRepository;
import com.betai.repository.ModelTuningProfileRepository;
import com.betai.repository.PredictionGenerationRunRepository;
import com.betai.repository.PredictionSelectionRepository;
import com.betai.repository.TeamFeatureSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictionGenerationServiceImplTest {

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MarketDefinitionRepository marketDefinitionRepository;
    @Mock
    private LeagueBaselineRepository leagueBaselineRepository;
    @Mock
    private TeamFeatureSnapshotRepository teamFeatureSnapshotRepository;
    @Mock
    private PredictionSelectionRepository predictionSelectionRepository;
    @Mock
    private PredictionGenerationRunRepository predictionGenerationRunRepository;
    @Mock
    private ModelQualitySnapshotRepository modelQualitySnapshotRepository;
    @Mock
    private ModelTuningProfileRepository modelTuningProfileRepository;
    @Mock
    private MarketProbabilityEngine marketProbabilityEngine;
    @Mock
    private ProbabilityCalibrationService probabilityCalibrationService;
    @Mock
    private ModelTuningService modelTuningService;
    @Mock
    private OddsValueService oddsValueService;
    @Mock
    private HistoricalSeasonWindowService historicalSeasonWindowService;
    @Mock
    private EntityManager entityManager;

    private PredictionGenerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PredictionGenerationServiceImpl(
                new PredictionProperties("test-model", 20, 20, 14, List.of(MatchStatus.SCHEDULED), 3, 10, com.betai.domain.feature.InsufficientSeasonPolicy.USE_MAX_AVAILABLE, 10),
                leagueRepository,
                matchRepository,
                marketDefinitionRepository,
                leagueBaselineRepository,
                teamFeatureSnapshotRepository,
                predictionSelectionRepository,
                predictionGenerationRunRepository,
                modelQualitySnapshotRepository,
                modelTuningProfileRepository,
                marketProbabilityEngine,
                probabilityCalibrationService,
                modelTuningService,
                oddsValueService,
                (leagueCode, seasonLabel, marketCode) -> true,
                historicalSeasonWindowService,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-06-18T10:00:00Z"), ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        when(entityManager.getFlushMode()).thenReturn(FlushModeType.AUTO);
        when(predictionGenerationRunRepository.save(any(PredictionGenerationRun.class))).thenAnswer(invocation -> {
            PredictionGenerationRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            return run;
        });
    }

    @Test
    void forcedRegenerationDoesNotOverwriteRatedSelectionWithUnratedResultWhenQualityIsMissing() {
        League league = withId(new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1)
                .setCurrentSeason("2025/2026"));
        Team home = team(league, "Home");
        Team away = team(league, "Away");
        Match match = withId(new Match()
                .setLeague(league)
                .setHomeTeam(home)
                .setAwayTeam(away)
                .setMatchDate(LocalDate.parse("2026-06-20"))
                .setKickoffAt(OffsetDateTime.parse("2026-06-20T15:00:00Z"))
                .setStatus(MatchStatus.SCHEDULED)
                .setSeasonLabel("2025/2026")
                .setSourceFixtureKey("fixture-1"));
        MarketDefinition market = withId(market(MarketCode.HOME_WIN));
        PredictionSelection existing = new PredictionSelection()
                .setMatch(match)
                .setMarketDefinition(market)
                .setPredictedValue("HOME")
                .setRawProbability(new BigDecimal("0.810000"))
                .setProbability(new BigDecimal("0.820000"))
                .setModelVersion("test-model")
                .setGeneratedAt(OffsetDateTime.parse("2026-06-17T10:00:00Z"))
                .setCorrelationGroupKey("match:" + match.getId() + ":result")
                .setConfidenceBand(PredictionConfidenceBand.HIGH);
        existing.setId(UUID.randomUUID());

        Map<MarketCode, BigDecimal> probabilities = new EnumMap<>(MarketCode.class);
        probabilities.put(MarketCode.HOME_WIN, new BigDecimal("0.550000"));

        when(leagueRepository.findByCodeInAndActiveTrue(Set.of(LeagueCode.PREMIER_LEAGUE))).thenReturn(List.of(league));
        when(historicalSeasonWindowService.resolveWindow(
                eq(league),
                eq(LocalDate.parse("2026-06-18")),
                any(),
                any(),
                any(),
                eq(FeatureGroup.RESULTS)
        )).thenReturn(window());
        when(marketDefinitionRepository.findByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of(market));
        when(leagueBaselineRepository.findByLeague_CodeAndCalculationDateAndSeasonWindowKey(
                LeagueCode.PREMIER_LEAGUE,
                LocalDate.parse("2026-06-18"),
                "window"
        )).thenReturn(Optional.of(baseline(league)));
        when(teamFeatureSnapshotRepository.findByLeague_CodeAndCalculationDateAndSeasonWindowKeyOrderByTeam_CanonicalNameAsc(
                LeagueCode.PREMIER_LEAGUE,
                LocalDate.parse("2026-06-18"),
                "window"
        )).thenReturn(List.of(feature(league, home), feature(league, away)));
        when(matchRepository.findMatchesForPredictionGeneration(
                eq(LeagueCode.PREMIER_LEAGUE),
                eq(LocalDate.parse("2026-06-20")),
                eq(LocalDate.parse("2026-06-20")),
                eq(Set.of(MatchStatus.SCHEDULED))
        )).thenReturn(List.of(match));
        when(predictionSelectionRepository.findExistingForMatchesAndModel(List.of(match.getId()), "test-model"))
                .thenReturn(List.of(existing));
        when(marketProbabilityEngine.score(any(), any(), any(), any()))
                .thenReturn(new MarketProbabilityEngine.PredictionScores(
                        probabilities,
                        new MarketProbabilityEngine.ExpectedProfile(1.4, 1.1, 2.5, 9.0, 5.0, 4.0, 4.0)
                ));
        var response = service.generatePredictions(new PredictionGenerationRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE),
                LocalDate.parse("2026-06-18"),
                null,
                LocalDate.parse("2026-06-20"),
                LocalDate.parse("2026-06-20"),
                Set.of(MatchStatus.SCHEDULED),
                "test-model",
                null,
                true,
                null,
                null,
                null
        ));

        assertThat(response.predictionGenerationRuns()).hasSize(1);
        assertThat(response.predictionGenerationRuns().getFirst().status()).isEqualTo(PredictionGenerationStatus.SKIPPED);
        assertThat(response.predictionGenerationRuns().getFirst().selectionsGenerated()).isZero();
        assertThat(response.predictionGenerationRuns().getFirst().selectionsSkipped()).isEqualTo(1);
        assertThat(existing.getConfidenceBand()).isEqualTo(PredictionConfidenceBand.HIGH);
        assertThat(existing.getProbability()).isEqualByComparingTo("0.820000");

        ArgumentCaptor<List<PredictionSelection>> captor = ArgumentCaptor.forClass(List.class);
        verify(predictionSelectionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    private LeagueBaseline baseline(League league) {
        return withId(new LeagueBaseline()
                .setLeague(league)
                .setSeasonLabel("2025/2026")
                .setCalculationDate(LocalDate.parse("2026-06-18"))
                .setMatchesSampled(200)
                .setAvgHomeGoals(new BigDecimal("1.5000"))
                .setAvgAwayGoals(new BigDecimal("1.2000"))
                .setAvgTotalGoals(new BigDecimal("2.7000"))
                .setHomeWinRate(new BigDecimal("0.450000"))
                .setDrawRate(new BigDecimal("0.270000"))
                .setAwayWinRate(new BigDecimal("0.280000"))
                .setBttsRate(new BigDecimal("0.550000"))
                .setOver15Rate(new BigDecimal("0.700000"))
                .setOver25Rate(new BigDecimal("0.520000"))
                .setUnder35Rate(new BigDecimal("0.760000"))
                .setAvgTotalCorners(new BigDecimal("9.0000"))
                .setAvgTotalYellowCards(new BigDecimal("4.0000"))
                .setRedCardRate(new BigDecimal("0.120000")));
    }

    private HistoricalSeasonWindow window() {
        return new HistoricalSeasonWindow(
                3,
                true,
                3,
                3,
                3,
                3,
                SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE,
                List.of("2025/2026"),
                List.of("2025/2026"),
                true,
                false,
                LocalDate.parse("2025-08-01"),
                LocalDate.parse("2026-06-18"),
                200,
                FeatureGroup.RESULTS,
                3,
                "RESULTS:FULL",
                HistoricalDepthStatus.FULL_REQUESTED_DEPTH,
                "test-recency-v1",
                List.of(BigDecimal.ONE),
                "window"
        );
    }

    private TeamFeatureSnapshot feature(League league, Team team) {
        return withId(new TeamFeatureSnapshot()
                .setLeague(league)
                .setTeam(team)
                .setSeasonLabel("2025/2026")
                .setCalculationDate(LocalDate.parse("2026-06-18"))
                .setMatchesPlayed(20)
                .setHomeMatches(10)
                .setAwayMatches(10)
                .setLast5Matches(5)
                .setLast10Matches(10)
                .setPointsPerMatch(new BigDecimal("1.6000"))
                .setLast5PointsPerMatch(new BigDecimal("1.8000"))
                .setLast10PointsPerMatch(new BigDecimal("1.7000"))
                .setGoalsForPerMatch(new BigDecimal("1.4000"))
                .setGoalsAgainstPerMatch(new BigDecimal("1.1000"))
                .setHomeGoalsForPerMatch(new BigDecimal("1.5000"))
                .setHomeGoalsAgainstPerMatch(new BigDecimal("1.0000"))
                .setAwayGoalsForPerMatch(new BigDecimal("1.3000"))
                .setAwayGoalsAgainstPerMatch(new BigDecimal("1.2000"))
                .setCleanSheetRate(new BigDecimal("0.300000"))
                .setFailedToScoreRate(new BigDecimal("0.200000"))
                .setBttsRate(new BigDecimal("0.550000"))
                .setOver15Rate(new BigDecimal("0.700000"))
                .setOver25Rate(new BigDecimal("0.500000"))
                .setUnder35Rate(new BigDecimal("0.750000"))
                .setCornersForPerMatch(new BigDecimal("5.0000"))
                .setCornersAgainstPerMatch(new BigDecimal("4.0000"))
                .setYellowCardsForPerMatch(new BigDecimal("2.0000"))
                .setYellowCardsAgainstPerMatch(new BigDecimal("2.0000"))
                .setRedCardRate(new BigDecimal("0.100000"))
                .setFormScore(new BigDecimal("1.7000")));
    }

    private MarketDefinition market(MarketCode code) {
        return new MarketDefinition()
                .setCode(code)
                .setDisplayName(code.getDisplayName())
                .setMarketType(code.getMarketType())
                .setMarketFamily(code.getMarketType())
                .setDirection(code.getDirection())
                .setSelectionValue(code.getSelectionValue())
                .setThreshold(code.getThreshold())
                .setPeriod(code.getPeriod())
                .setTeamScope(code.getTeamScope())
                .setTargetType(code.getTargetType())
                .setEnabled(code.isEnabled())
                .setActive(code.isEnabled())
                .setMinimumSampleSize(code.getMinimumSampleSize())
                .setSettlementDescription(code.getSettlementDescription());
    }

    private Team team(League league, String name) {
        return withId(new Team()
                .setLeague(league)
                .setCanonicalName(name)
                .setShortName(name)
                .setCountry("England")
                .setExternalKey(name.toLowerCase()));
    }

    private <T extends com.betai.domain.common.BaseEntity> T withId(T entity) {
        entity.setId(UUID.randomUUID());
        return entity;
    }
}
