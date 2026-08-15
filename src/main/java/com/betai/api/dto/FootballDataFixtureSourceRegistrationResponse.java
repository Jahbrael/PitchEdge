package com.betai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FootballDataFixtureSourceRegistrationResponse(
        UUID requestId,
        OffsetDateTime registeredAt,
        String sourceUrl,
        List<SourceTargetResponse> sourceTargets
) {
}
