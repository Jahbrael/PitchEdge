package com.betai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserSavedBatchResponse(
        UUID id,
        String batchName,
        OffsetDateTime createdAt,
        List<UserSavedBatchItemResponse> items
) {
}
