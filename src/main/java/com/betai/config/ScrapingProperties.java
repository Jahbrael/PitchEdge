package com.betai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bet-ai.scraping")
public record ScrapingProperties(
        long maxPayloadBytes,
        int robotsTimeoutMs
) {
}
