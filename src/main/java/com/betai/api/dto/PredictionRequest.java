package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.prediction.PredictionConfidenceBand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record PredictionRequest(
        @NotEmpty @Size(max = 256) Set<LeagueCode> leagueCodes,
        @NotEmpty @Size(max = 128) Set<MarketCode> marketCodes,
        @NotNull LocalDate fixtureDateFrom,
        @NotNull LocalDate fixtureDateTo,
        @Min(1) @Max(20) Integer batchCount,
        @Min(1) @Max(20) Integer selectionsPerBatch,
        ValueMode valueMode,
        String sport,
        SelectionStrategy strategy,
        @Min(1) Integer minimumSelections,
        @Min(1) Integer maximumSelections,
        @Min(1) Integer numberOfBatches,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal minimumModelProbability,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal maximumModelProbability,
        @DecimalMin("1.0") BigDecimal minimumDecimalOdds,
        @DecimalMin("1.0") BigDecimal maximumDecimalOdds,
        PredictionConfidenceBand minimumConfidence,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal minimumDataQuality,
        @Min(0) Integer minimumHistoricalSample,
        BigDecimal minimumExpectedValue,
        @DecimalMin("-1.0") @DecimalMax("1.0") BigDecimal minimumProbabilityEdge,
        Boolean requireCalibration,
        Boolean allowUnratedPredictions,
        Boolean allowMultipleSelectionsFromSameMatch,
        @Min(1) Integer maximumSelectionsPerMatch,
        @Min(1) Integer maximumSelectionsPerTeam,
        @Min(1) Integer maximumSelectionsPerLeague,
        Boolean requireMultipleLeagues,
        @Min(1) Integer minimumDistinctLeagues,
        Boolean requireDifferentMarketGroups,
        Boolean avoidCorrelatedSelections,
        Boolean allowRepeatSelectionsAcrossBatches,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal minimumBatchDifferencePercentage,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal correlationTolerance,
        RankingMode rankingMode,
        @DecimalMin("0.0") BigDecimal lowerRiskAllocationPercentage,
        @DecimalMin("0.0") BigDecimal balancedAllocationPercentage,
        @DecimalMin("0.0") BigDecimal longshotAllocationPercentage,
        @Min(1) Integer requestedSeasonCount,
        SeasonSelectionMode seasonSelectionMode,
        @Size(max = 16) Set<String> customSeasonIds,
        java.util.Map<MarketCode, ProbabilityRange> marketProbabilityRanges
) {
}
