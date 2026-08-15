package com.betai.service;

import com.betai.api.dto.details.FixturePredictionDetailsResponse;
import java.util.UUID;

public interface FixturePredictionDetailsService {
    default FixturePredictionDetailsResponse getFixtureDetails(UUID matchId, String modelVersion, String recommendedMarketCode) {
        return getFixtureDetails(matchId, modelVersion, recommendedMarketCode, null, null);
    }
    default FixturePredictionDetailsResponse getFixtureDetails(UUID matchId, String modelVersion, String recommendedMarketCode, UUID runId) {
        return getFixtureDetails(matchId, modelVersion, recommendedMarketCode, runId, null);
    }
    FixturePredictionDetailsResponse getFixtureDetails(UUID matchId, String modelVersion, String recommendedMarketCode, UUID runId, UUID selectionId);
}
