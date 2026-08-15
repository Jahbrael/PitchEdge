package com.betai.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record ProbabilityRange(
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal min,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal max
) {}
