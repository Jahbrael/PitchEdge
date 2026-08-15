package com.betai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequest(
        @NotBlank @Size(min = 3, max = 128) String username,
        @NotBlank @Size(min = 6, max = 256) String password
) {}
