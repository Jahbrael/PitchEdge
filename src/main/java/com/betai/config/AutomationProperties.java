package com.betai.config;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.match.MatchStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "bet-ai.automation")
public record AutomationProperties(
        boolean enabled,
        String dailyCron,
        String zone,
        int predictionWindowDays,
        int settlementLookbackDays,
        int historicalPredictionLookbackDays,
        int backtestLookbackDays,
        int backtestMinimumSampleSize,
        int modelQualityMinimumSampleSize,
        int maxStepAttempts,
        long retryBackoffMs,
        long stepTimeoutMs,
        Set<LeagueCode> leagueCodes,
        Set<MatchStatus> predictionMatchStatuses,
        boolean runRefresh,
        boolean runExtraction,
        boolean runOddsExtraction,
        boolean runFixtureDiscovery,
        boolean fixtureDiscoveryAutoRegisterFootballDataSources,
        boolean fixtureDiscoveryGeneratePendingSlate,
        String fixtureDiscoveryTargetSeasonLabel,
        boolean runFeatures,
        boolean runHistoricalPredictions,
        boolean runPredictions,
        boolean runSettlement,
        boolean runModelQuality,
        boolean runBacktest,
        boolean forceRefresh,
        boolean forceReprocess,
        boolean forceRegenerateFeatures,
        boolean forceRegeneratePredictions,
        boolean forceResettle,
        Integer requestedSeasonCount,
        SeasonSelectionMode seasonSelectionMode,
        Set<String> customSeasonIds
) {
}
