package com.betai.config;

import com.betai.domain.match.MatchStatus;
import com.betai.domain.feature.InsufficientSeasonPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "bet-ai.prediction")
public record PredictionProperties(
        String defaultModelVersion,
        int maxBatches,
        int maxSelectionsPerBatch,
        int maxDateRangeDays,
        List<MatchStatus> formMatchStatuses,
        int defaultSeasonCount,
        int maximumSeasonCount,
        InsufficientSeasonPolicy insufficientSeasonPolicy,
        int minimumCompletedMatchesPerUsableSeason
) {
}
