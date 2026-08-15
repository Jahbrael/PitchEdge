package com.betai.service;

import com.betai.domain.feature.FeatureGroup;
import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.league.League;

import java.time.LocalDate;
import java.util.Set;

public interface HistoricalSeasonWindowService {

    HistoricalSeasonWindow resolveWindow(
            League league,
            LocalDate cutoffDate,
            Integer requestedSeasonCount,
            SeasonSelectionMode seasonSelectionMode,
            Set<String> customSeasonIds,
            FeatureGroup featureGroup
    );
}
