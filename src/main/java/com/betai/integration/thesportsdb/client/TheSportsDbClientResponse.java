package com.betai.integration.thesportsdb.client;

import java.time.OffsetDateTime;

public record TheSportsDbClientResponse(
        TheSportsDbEndpoint endpoint,
        String path,
        int statusCode,
        OffsetDateTime retrievedAt,
        String rawJson
) {
}
