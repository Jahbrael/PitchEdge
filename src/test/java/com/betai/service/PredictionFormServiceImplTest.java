package com.betai.service;

import com.betai.api.dto.PredictionRequest;
import com.betai.api.dto.SelectionStrategy;
import com.betai.config.PredictionProperties;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.repository.DataRefreshLogRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.PredictionGenerationRunRepository;
import com.betai.repository.PredictionSelectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictionFormServiceImplTest {

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private MarketDefinitionRepository marketDefinitionRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private PredictionSelectionRepository predictionSelectionRepository;
    @Mock
    private PredictionGenerationRunRepository predictionGenerationRunRepository;
    @Mock
    private DataRefreshLogRepository dataRefreshLogRepository;
    @Mock
    private HistoricalPredictionService historicalPredictionService;
    @Mock
    private FixtureCardIndicatorService fixtureCardIndicatorService;

    @Test
    void rejectsInactiveOrUnsupportedMarketsBeforeCandidateLookup() {
        PredictionFormServiceImpl service = service(new PredictionCandidateFilter(), new BatchBuilder());
        PredictionRequest request = request(Set.of(MarketCode.FIRST_HALF_OVER_0_5_GOALS));

        when(leagueRepository.findByCodeInAndActiveTrueAndScrapeEnabledTrue(request.leagueCodes())).thenReturn(List.of(league()));
        when(marketDefinitionRepository.findByCodeInAndEnabledTrue(request.marketCodes())).thenReturn(List.of());

        assertThatThrownBy(() -> service.generatePredictions(request))
                .isInstanceOf(ReferenceDataNotFoundException.class)
                .hasMessageContaining("Unsupported or disabled markets");
    }

    @Test
    void rejectsImportPendingLeaguesBeforeCandidateLookup() {
        PredictionFormServiceImpl service = service(new PredictionCandidateFilter(), new BatchBuilder());
        PredictionRequest request = request(Set.of(MarketCode.HOME_WIN));

        when(leagueRepository.findByCodeInAndActiveTrueAndScrapeEnabledTrue(request.leagueCodes())).thenReturn(List.of());

        assertThatThrownBy(() -> service.generatePredictions(request))
                .isInstanceOf(ReferenceDataNotFoundException.class)
                .hasMessageContaining("import-pending leagues");
    }

    @Test
    void passesOnlyRequestedMarketsToCandidateSelectionLookup() {
        PredictionCandidateFilter filter = new PredictionCandidateFilter();
        BatchBuilder batchBuilder = new BatchBuilder();
        PredictionFormServiceImpl service = service(filter, batchBuilder);
        Set<MarketCode> selectedMarkets = Set.of(MarketCode.HOME_WIN, MarketCode.CORNERS_OVER_9_5);
        PredictionRequest request = request(selectedMarkets);

        when(leagueRepository.findByCodeInAndActiveTrueAndScrapeEnabledTrue(request.leagueCodes())).thenReturn(List.of(league()));
        when(marketDefinitionRepository.findByCodeInAndEnabledTrue(selectedMarkets))
                .thenReturn(selectedMarkets.stream().map(this::market).toList());
        when(matchRepository.findCandidateFixtures(eq(request.leagueCodes()), eq(request.fixtureDateFrom()), eq(request.fixtureDateTo()), any()))
                .thenReturn(List.of());
        when(predictionSelectionRepository.findCandidateSelectionsForModelAndOutcomes(
                eq(request.leagueCodes()),
                any(),
                eq(request.fixtureDateFrom()),
                eq(request.fixtureDateTo()),
                any(),
                any(),
                eq("test-model")
        )).thenReturn(List.of());
        when(dataRefreshLogRepository.findFirstByLeague_CodeAndRefreshDateAndRefreshStatusOrderByStartedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(predictionGenerationRunRepository.findFirstByLeague_CodeAndModelVersionAndGenerationStatusAndFixtureDateFromLessThanEqualAndFixtureDateToGreaterThanEqualOrderByStartedAtDesc(
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(Optional.empty());

        service.generatePredictions(request);

        ArgumentCaptor<Set<MarketCode>> marketCaptor = ArgumentCaptor.forClass(Set.class);
        verify(predictionSelectionRepository).findCandidateSelectionsForModelAndOutcomes(
                eq(request.leagueCodes()),
                marketCaptor.capture(),
                eq(request.fixtureDateFrom()),
                eq(request.fixtureDateTo()),
                any(),
                eq(List.of(PredictionOutcome.PENDING)),
                eq("test-model")
        );
        assertThat(marketCaptor.getValue()).containsExactlyInAnyOrderElementsOf(selectedMarkets);
    }

    private PredictionFormServiceImpl service(PredictionCandidateFilter filter, BatchBuilder batchBuilder) {
        return new PredictionFormServiceImpl(
                new PredictionProperties("test-model", 20, 20, 14, List.of(MatchStatus.SCHEDULED), 3, 10, com.betai.domain.feature.InsufficientSeasonPolicy.USE_MAX_AVAILABLE, 10),
                leagueRepository,
                marketDefinitionRepository,
                matchRepository,
                predictionSelectionRepository,
                predictionGenerationRunRepository,
                dataRefreshLogRepository,
                historicalPredictionService,
                filter,
                batchBuilder,
                fixtureCardIndicatorService,
                Clock.fixed(Instant.parse("2026-06-14T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    private PredictionRequest request(Set<MarketCode> marketCodes) {
        return new PredictionRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE),
                marketCodes,
                LocalDate.parse("2026-06-15"),
                LocalDate.parse("2026-06-16"),
                null,
                null,
                null,
                "FOOTBALL",
                SelectionStrategy.CUSTOM,
                1,
                5,
                1,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                null,
                null,
                null,
                BigDecimal.ZERO,
                0,
                null,
                null,
                false,
                true,
                false,
                1,
                null,
                null,
                false,
                1,
                false,
                true,
                false,
                BigDecimal.ZERO,
                new BigDecimal("0.50"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private League league() {
        return new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1);
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
}
