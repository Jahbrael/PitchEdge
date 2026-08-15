package com.betai.integration.thesportsdb.client;

import com.betai.integration.thesportsdb.TheSportsDbProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TheSportsDbSecretRedactor {

    private final TheSportsDbProperties properties;

    public TheSportsDbSecretRedactor(TheSportsDbProperties properties) {
        this.properties = properties;
    }

    public String redact(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String redacted = value;
        if (StringUtils.hasText(properties.apiKey())) {
            redacted = redacted.replace(properties.apiKey().trim(), "REDACTED");
        }
        return redacted.replaceAll("(?i)(X-API-KEY[:=]\\s*)[^,\\s]+", "$1REDACTED")
                .replaceAll("(?i)(apikey=)[^&\\s]+", "$1REDACTED")
                .replaceAll("(?i)(api_key=)[^&\\s]+", "$1REDACTED");
    }
}
