package com.betai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ModelQualityGenerationResponse(
        UUID requestId,
        OffsetDateTime triggeredAt,
        List<ModelQualitySnapshotResponse> qualitySnapshots,
        List<String> warnings
) {
}
