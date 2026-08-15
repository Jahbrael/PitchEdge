package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SourceTargetRequest(
        @NotNull LeagueCode leagueCode,
        @NotNull SourceType sourceType,
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 1000) String urlTemplate,
        @Size(max = 32) String sourceSeasonToken,
        @Size(max = 32) String targetSeasonLabel,
        @NotNull RenderMode renderMode,
        Boolean active,
        Boolean robotsTxtRequired,
        @Size(max = 160) String userAgent,
        @Min(1) @Max(120) Integer rateLimitPerMinute,
        @Min(1000) @Max(60000) Integer timeoutMs,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal reliabilityScore,
        @Min(1) @Max(1000) Integer fallbackPriority,
        JsonNode selectorsJson
) {
}
