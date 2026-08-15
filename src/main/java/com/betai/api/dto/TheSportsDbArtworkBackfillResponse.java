package com.betai.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record TheSportsDbArtworkBackfillResponse(
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        int checkedLeagues,
        int updatedLeagues,
        int unchangedLeagues,
        int skippedLeagues,
        int failedLeagues,
        int checkedTeams,
        int updatedTeams,
        int unchangedTeams,
        int skippedTeams,
        int failedTeams,
        List<String> skipReasons,
        List<String> failureReasons
) {
}
