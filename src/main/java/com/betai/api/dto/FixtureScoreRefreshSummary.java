package com.betai.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FixtureScoreRefreshSummary(
        LocalDate dateRefreshed,
        int fixturesChecked,
        int fixturesUpdated,
        int fixturesUnchanged,
        int liveFixturesUpdated,
        int finishedFixturesUpdated,
        int failedFixtures,
        int skippedFixtures,
        OffsetDateTime lastRefreshedTime,
        String safeFailureReason
) {}
