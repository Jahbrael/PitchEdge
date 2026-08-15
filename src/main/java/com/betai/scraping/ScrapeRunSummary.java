package com.betai.scraping;

import java.util.List;
import java.util.UUID;

public record ScrapeRunSummary(
        int targetCount,
        int successCount,
        int failedCount,
        int robotsBlockedCount,
        int unsupportedRenderCount,
        List<UUID> rawSnapshotIds,
        String aggregateChecksum
) {
    public int rejectedCount() {
        return failedCount + robotsBlockedCount + unsupportedRenderCount;
    }

    public String rawPayloadReference() {
        if (rawSnapshotIds.isEmpty()) {
            return null;
        }
        return "raw_snapshots count=" + rawSnapshotIds.size() + "; first=" + rawSnapshotIds.getFirst();
    }
}
