package com.betai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DailyRefreshResponse(
        UUID requestId,
        OffsetDateTime triggeredAt,
        List<RefreshLogResponse> refreshLogs
) {
}
