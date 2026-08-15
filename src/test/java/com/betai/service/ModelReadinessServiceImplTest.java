package com.betai.service;

import com.betai.api.dto.ModelReadinessStatus;
import com.betai.config.PredictionProperties;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.quality.ModelQualitySnapshot;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.domain.tuning.ModelTuningProfile;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.ModelQualitySnapshotRepository;
import com.betai.repository.ModelTuningProfileRepository;
import com.betai.repository.PredictionSelectionRepository;
import com.betai.repository.SourceTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelReadinessServiceImplTest {

    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 6, 13);
    private static final String MODEL_VERSION = "test-model";

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private MarketDefinitionRepository marketDefinitionRepository;
    @Mock
    private SourceTargetRepository sourceTargetRepository;
    @Mock
    private PredictionSelectionRepository predictionSelectionRepository;
    @Mock
    private ModelQualitySnapshotRepository modelQualitySnapshotRepository;
    @Mock
    private ModelTuningProfileRepository modelTuningProfileRepository;

    private ModelReadinessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ModelReadinessServiceImpl(
                new PredictionProperties(MODEL_VERSION, 20, 20, 14, List.of(MatchStatus.SCHEDULED), 3, 10, com.betai.domain.feature.InsufficientSeasonPolicy.USE_MAX_AVAILABLE, 10),
                leagueRepository,
                marketDefinitionRepository,
                sourceTargetRepository,
                predictionSelectionRepository,
                modelQualitySnapshotRepository,
                modelTuningProfileRepository,
                Clock.fixed(Instant.parse("2026-06-13T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void optimizedProbabilityReadinessDoesNotRequireOddsReadiness() {
        League league = league(LeagueCode.PREMIER_LEAGUE);
        MarketDefinition market = market(MarketCode.HOME_WIN);
        ModelQualitySnapshot quality = quality(league, market, 80, PredictionConfidenceBand.HIGH);
        ModelTuningProfile tuningProfile = tuningProfile(league, market, 80);
        stubReferenceData(league, market);
        when(sourceTargetRepository.findActiveByLeagueCodes(Set.of(LeagueCode.PREMIER_LEAGUE)))
                .thenReturn(List.of(source(league, SourceType.RESULTS), source(league, SourceType.FIXTURES)));
        when(predictionSelectionRepository.countResolvedSelectionsForReadiness(
                LeagueCode.PREMIER_LEAGUE,
                MarketCode.HOME_WIN,
                MODEL_VERSION
        )).thenReturn(80L);
        when(predictionSelectionRepository.countPricedSelectionsForReadiness(
                LeagueCode.PREMIER_LEAGUE,
                MarketCode.HOME_WIN,
                MODEL_VERSION
        )).thenReturn(0L);
        when(modelQualitySnapshotRepository
                .findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndQualityDateLessThanEqualOrderByQualityDateDesc(
                        LeagueCode.PREMIER_LEAGUE,
                        MarketCode.HOME_WIN,
                        MODEL_VERSION,
                        AS_OF_DATE
                )).thenReturn(Optional.of(quality));
        when(modelTuningProfileRepository
                .findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndSegmentKeyAndProfileDateLessThanEqualAndActiveTrueOrderByProfileDateDesc(
                        LeagueCode.PREMIER_LEAGUE,
                        MarketCode.HOME_WIN,
                        MODEL_VERSION,
                        TuningSegment.GLOBAL,
                        AS_OF_DATE
                )).thenReturn(Optional.of(tuningProfile));

        var response = service.getReadiness(
                Set.of(LeagueCode.PREMIER_LEAGUE),
                Set.of(MarketCode.HOME_WIN),
                null,
                AS_OF_DATE
        ).getFirst();

        assertThat(response.optimizedProbabilityReady()).isTrue();
        assertThat(response.valueStrategyDataReady()).isFalse();
        assertThat(response.status()).isEqualTo(ModelReadinessStatus.READY);
        assertThat(response.missingSteps())
                .contains("Configure an ODDS_REFERENCE source before using value-based strategies for PREMIER_LEAGUE.");
    }

    @Test
    void matchDataSourceSatisfiesBaseResultsAndFixtureReadiness() {
        League league = league(LeagueCode.LATVIAN_VIRSLIGA);
        MarketDefinition market = market(MarketCode.HOME_WIN);
        ModelQualitySnapshot quality = quality(league, market, 80, PredictionConfidenceBand.HIGH);
        ModelTuningProfile tuningProfile = tuningProfile(league, market, 80);
        stubReferenceData(league, market);
        when(sourceTargetRepository.findActiveByLeagueCodes(Set.of(LeagueCode.LATVIAN_VIRSLIGA)))
                .thenReturn(List.of(source(league, SourceType.MATCH_DATA)));
        when(predictionSelectionRepository.countResolvedSelectionsForReadiness(
                LeagueCode.LATVIAN_VIRSLIGA,
                MarketCode.HOME_WIN,
                MODEL_VERSION
        )).thenReturn(80L);
        when(predictionSelectionRepository.countPricedSelectionsForReadiness(
                LeagueCode.LATVIAN_VIRSLIGA,
                MarketCode.HOME_WIN,
                MODEL_VERSION
        )).thenReturn(0L);
        when(modelQualitySnapshotRepository
                .findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndQualityDateLessThanEqualOrderByQualityDateDesc(
                        LeagueCode.LATVIAN_VIRSLIGA,
                        MarketCode.HOME_WIN,
                        MODEL_VERSION,
                        AS_OF_DATE
                )).thenReturn(Optional.of(quality));
        when(modelTuningProfileRepository
                .findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndSegmentKeyAndProfileDateLessThanEqualAndActiveTrueOrderByProfileDateDesc(
                        LeagueCode.LATVIAN_VIRSLIGA,
                        MarketCode.HOME_WIN,
                        MODEL_VERSION,
                        TuningSegment.GLOBAL,
                        AS_OF_DATE
                )).thenReturn(Optional.of(tuningProfile));

        var response = service.getReadiness(
                Set.of(LeagueCode.LATVIAN_VIRSLIGA),
                Set.of(MarketCode.HOME_WIN),
                null,
                AS_OF_DATE
        ).getFirst();

        assertThat(response.hasResultsSource()).isTrue();
        assertThat(response.hasFixtureSource()).isTrue();
        assertThat(response.optimizedProbabilityReady()).isTrue();
    }

    @Test
    void missingHistoricalQualityAndTuningKeepLeagueMarketNotReady() {
        League league = league(LeagueCode.LA_LIGA);
        MarketDefinition market = market(MarketCode.UNDER_2_5_GOALS);
        stubReferenceData(league, market);
        when(sourceTargetRepository.findActiveByLeagueCodes(Set.of(LeagueCode.LA_LIGA)))
                .thenReturn(List.of(source(league, SourceType.ODDS_REFERENCE)));
        when(predictionSelectionRepository.countResolvedSelectionsForReadiness(
                LeagueCode.LA_LIGA,
                MarketCode.UNDER_2_5_GOALS,
                MODEL_VERSION
        )).thenReturn(0L);
        when(predictionSelectionRepository.countPricedSelectionsForReadiness(
                LeagueCode.LA_LIGA,
                MarketCode.UNDER_2_5_GOALS,
                MODEL_VERSION
        )).thenReturn(0L);
        when(modelQualitySnapshotRepository
                .findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndQualityDateLessThanEqualOrderByQualityDateDesc(
                        LeagueCode.LA_LIGA,
                        MarketCode.UNDER_2_5_GOALS,
                        MODEL_VERSION,
                        AS_OF_DATE
                )).thenReturn(Optional.empty());
        when(modelTuningProfileRepository
                .findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndSegmentKeyAndProfileDateLessThanEqualAndActiveTrueOrderByProfileDateDesc(
                        LeagueCode.LA_LIGA,
                        MarketCode.UNDER_2_5_GOALS,
                        MODEL_VERSION,
                        TuningSegment.GLOBAL,
                        AS_OF_DATE
                )).thenReturn(Optional.empty());

        var response = service.getReadiness(
                Set.of(LeagueCode.LA_LIGA),
                Set.of(MarketCode.UNDER_2_5_GOALS),
                null,
                AS_OF_DATE
        ).getFirst();

        assertThat(response.optimizedProbabilityReady()).isFalse();
        assertThat(response.status()).isEqualTo(ModelReadinessStatus.NOT_READY);
        assertThat(response.missingSteps()).contains(
                "Configure and import an active RESULTS source for LA_LIGA.",
                "Configure and import an active FIXTURES source for LA_LIGA.",
                "Generate a model quality snapshot for LA_LIGA/UNDER_2_5_GOALS.",
                "Run backtesting for LA_LIGA/UNDER_2_5_GOALS to create an active GLOBAL tuning profile."
        );
    }

    private void stubReferenceData(League league, MarketDefinition market) {
        when(leagueRepository.findByCodeInAndActiveTrue(Set.of(league.getCode()))).thenReturn(List.of(league));
        when(marketDefinitionRepository.findByCodeInAndEnabledTrue(Set.of(market.getCode()))).thenReturn(List.of(market));
    }

    private League league(LeagueCode code) {
        return new League()
                .setCode(code)
                .setName(code.getDisplayName())
                .setCountry(code.getCountry())
                .setTier(code.getTier());
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

    private SourceTarget source(League league, SourceType sourceType) {
        return new SourceTarget()
                .setLeague(league)
                .setSourceType(sourceType)
                .setName(sourceType.name())
                .setUrlTemplate("https://example.test/" + sourceType.name().toLowerCase());
    }

    private ModelQualitySnapshot quality(
            League league,
            MarketDefinition market,
            int sampleSize,
            PredictionConfidenceBand confidenceBand
    ) {
        return new ModelQualitySnapshot()
                .setLeague(league)
                .setMarketDefinition(market)
                .setModelVersion(MODEL_VERSION)
                .setQualityDate(AS_OF_DATE.minusDays(1))
                .setSampleSize(sampleSize)
                .setWonCount(42)
                .setLostCount(38)
                .setVoidCount(0)
                .setObservedWinRate(new BigDecimal("0.525000"))
                .setAverageRawProbability(new BigDecimal("0.520000"))
                .setBrierScore(new BigDecimal("0.190000"))
                .setCalibrationError(new BigDecimal("0.005000"))
                .setProbabilityAdjustment(new BigDecimal("0.005000"))
                .setConfidenceBand(confidenceBand);
    }

    private ModelTuningProfile tuningProfile(League league, MarketDefinition market, int sampleSize) {
        return new ModelTuningProfile()
                .setLeague(league)
                .setMarketDefinition(market)
                .setModelVersion(MODEL_VERSION)
                .setProfileDate(AS_OF_DATE.minusDays(1))
                .setSegmentKey(TuningSegment.GLOBAL)
                .setSampleSize(sampleSize)
                .setRecommendedProbabilityAdjustment(new BigDecimal("0.010000"))
                .setAppliedProbabilityAdjustment(new BigDecimal("0.005000"))
                .setActive(true);
    }
}
