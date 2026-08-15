package com.betai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bet-ai.odds.sharpapi")
public record SharpApiProperties(
        boolean enabled,
        String apiKey,
        String baseUrl
) {
}
