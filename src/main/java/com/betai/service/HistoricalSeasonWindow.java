package com.betai.service;

import com.betai.domain.feature.FeatureGroup;
import com.betai.domain.feature.HistoricalDepthStatus;
import com.betai.domain.feature.SeasonSelectionMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record HistoricalSeasonWindow(
        int requestedSeasonCount,
        boolean defaultSeasonCountApplied,
        int seasonsDiscovered,
        int seasonsImported,
        int usableSeasonsFound,
        int actualSeasonCountUsed,
        SeasonSelectionMode seasonSelectionMode,
        List<String> selectedSeasonIds,
        List<String> selectedSeasonNames,
        boolean currentSeasonIncluded,
        boolean fallbackApplied,
        LocalDate oldestDataDate,
        LocalDate newestDataDate,
        int completedMatchesUsed,
        FeatureGroup featureGroup,
        int marketSpecificUsableSeasonCount,
        String marketSpecificDataCoverage,
        HistoricalDepthStatus historicalDepthStatus,
        String recencyWeightingVersion,
        List<BigDecimal> recencyWeights,
        String seasonWindowKey
) {
}
