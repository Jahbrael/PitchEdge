package com.betai.service;

import com.betai.domain.tuning.ModelTuningProfile;

import java.math.BigDecimal;

public record ModelTuningResult(
        BigDecimal inputProbability,
        BigDecimal tunedProbability,
        BigDecimal appliedAdjustment,
        ModelTuningProfile modelTuningProfile,
        String tuningNote
) {
}
