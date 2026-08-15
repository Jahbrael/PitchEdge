package com.betai.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FixtureDiscoveryResponse(
        UUID requestId,
        OffsetDateTime triggeredAt,
        LocalDate discoveryDate,
        LocalDate fixtureDateFrom,
        LocalDate fixtureDateTo,
        String targetSeasonLabel,
        FootballDataFixtureSourceRegistrationResponse sourceRegistration,
        DailyRefreshResponse refresh,
        DailyExtractionResponse extraction,
        List<UpcomingFixtureResponse> discoveredFixtures,
        PredictionGenerationResponse pendingSlateGeneration,
        List<String> warnings
) {
}
