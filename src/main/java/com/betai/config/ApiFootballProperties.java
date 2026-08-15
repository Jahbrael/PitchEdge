package com.betai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bet-ai.sources.api-football")
public record ApiFootballProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String timezone
) {
}
