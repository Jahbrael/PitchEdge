package com.betai.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ExponentialSeasonRecencyWeightingPolicyTest {

    private final ExponentialSeasonRecencyWeightingPolicy policy = new ExponentialSeasonRecencyWeightingPolicy();

    @Test
    void weightsDescendAndSumToOneForFiveSeasons() {
        var weights = policy.weights(5);

        assertThat(weights).hasSize(5);
        assertThat(weights.get(0)).isGreaterThan(weights.get(1));
        assertThat(weights.get(1)).isGreaterThan(weights.get(2));
        assertThat(weights.get(2)).isGreaterThan(weights.get(3));
        assertThat(weights.get(3)).isGreaterThan(weights.get(4));
        assertThat(sum(weights)).isEqualByComparingTo("1.00000000");
    }

    @Test
    void weightsAreRenormalisedWhenOnlyThreeSeasonsAreUsable() {
        var weights = policy.weights(3);

        assertThat(weights).hasSize(3);
        assertThat(sum(weights)).isEqualByComparingTo("1.00000000");
    }

    private BigDecimal sum(Iterable<BigDecimal> weights) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal weight : weights) {
            total = total.add(weight);
        }
        return total;
    }
}
