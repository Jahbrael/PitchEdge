package com.betai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bet-ai.odds.the-odds-api")
public record OddsProviderProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String regions,
        String markets,
        String oddsFormat,
        String dateFormat
) {
}
