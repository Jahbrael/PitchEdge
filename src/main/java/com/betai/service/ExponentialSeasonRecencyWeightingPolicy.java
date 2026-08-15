package com.betai.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class ExponentialSeasonRecencyWeightingPolicy implements SeasonRecencyWeightingPolicy {

    private static final BigDecimal DECAY = new BigDecimal("0.65");
    private static final int SCALE = 8;

    @Override
    public String version() {
        return "exponential-decay-0.65-v1";
    }

    @Override
    public List<BigDecimal> weights(int seasonCount) {
        if (seasonCount <= 0) {
            return List.of();
        }
        List<BigDecimal> raw = new ArrayList<>(seasonCount);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal current = BigDecimal.ONE;
        for (int index = 0; index < seasonCount; index++) {
            raw.add(current);
            total = total.add(current);
            current = current.multiply(DECAY);
        }
        BigDecimal denominator = total;
        return raw.stream()
                .map(value -> value.divide(denominator, SCALE, RoundingMode.HALF_UP))
                .toList();
    }
}
