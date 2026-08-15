package com.betai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "bet-ai.security")
public record SecurityProperties(
        String adminApiKey,
        String adminHeaderName,
        List<String> corsAllowedOrigins,
        int publicRequestsPerMinute,
        int adminRequestsPerMinute
) {
}
