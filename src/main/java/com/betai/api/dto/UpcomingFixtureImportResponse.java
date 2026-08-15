package com.betai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UpcomingFixtureImportResponse(
        UUID requestId,
        OffsetDateTime importedAt,
        String leagueCode,
        String seasonLabel,
        int createdCount,
        int updatedCount,
        List<UpcomingFixtureResponse> fixtures
) {
}
