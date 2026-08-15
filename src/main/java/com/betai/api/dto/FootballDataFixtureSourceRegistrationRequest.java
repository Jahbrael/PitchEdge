package com.betai.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FootballDataFixtureSourceRegistrationRequest(
        @NotBlank @Size(max = 32) String targetSeasonLabel,
        Boolean active,
        Boolean robotsTxtRequired,
        @Min(1) @Max(120) Integer rateLimitPerMinute,
        @Min(1000) @Max(60000) Integer timeoutMs
) {
}
