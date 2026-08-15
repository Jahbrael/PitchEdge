package com.betai.integration.thesportsdb;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "bet-ai.integrations.thesportsdb")
public record TheSportsDbProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        int requestsPerMinute,
        Duration connectionTimeout,
        Duration readTimeout,
        boolean liveScoresEnabled,
        boolean liveStatsEnabled
) {
}
