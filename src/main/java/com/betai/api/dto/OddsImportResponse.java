package com.betai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OddsImportResponse(
        UUID requestId,
        OffsetDateTime importedAt,
        int itemsReceived,
        int snapshotsImported,
        int rejected,
        int selectionsUpdated,
        List<OddsImportItemResponse> results,
        List<String> warnings
) {
}
