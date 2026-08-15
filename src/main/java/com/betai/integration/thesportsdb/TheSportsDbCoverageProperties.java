package com.betai.integration.thesportsdb;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "bet-ai.integrations.thesportsdb.coverage")
public record TheSportsDbCoverageProperties(
        BigDecimal fullThreshold,
        BigDecimal partialThreshold
) {
}
