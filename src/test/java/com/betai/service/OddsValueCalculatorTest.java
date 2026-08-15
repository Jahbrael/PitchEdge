package com.betai.service;

import com.betai.domain.odds.ValueRating;
import com.betai.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OddsValueCalculatorTest {

    private final OddsValueCalculator calculator = new OddsValueCalculator();

    @Test
    void calculatesImpliedProbabilityFromDecimalOdds() {
        assertThat(calculator.impliedProbability(new BigDecimal("2.5000")))
                .isEqualByComparingTo("0.400000");
    }

    @Test
    void ratesStrongValueWhenExpectedValueAndEdgeAreHigh() {
        OddsValueAssessment assessment = calculator.assess(
                new BigDecimal("0.500000"),
                new BigDecimal("2.3000")
        );

        assertThat(assessment.impliedProbability()).isEqualByComparingTo("0.434783");
        assertThat(assessment.valueEdge()).isEqualByComparingTo("0.065217");
        assertThat(assessment.expectedValue()).isEqualByComparingTo("0.150000");
        assertThat(assessment.valueRating()).isEqualTo(ValueRating.STRONG_VALUE);
        assertThat(assessment.note()).contains("tuned model probability 0.500000");
    }

    @Test
    void ratesNegativeValueWhenOddsAreBelowBreakEvenPrice() {
        OddsValueAssessment assessment = calculator.assess(
                new BigDecimal("0.500000"),
                new BigDecimal("1.8000")
        );

        assertThat(assessment.impliedProbability()).isEqualByComparingTo("0.555556");
        assertThat(assessment.valueEdge()).isEqualByComparingTo("-0.055556");
        assertThat(assessment.expectedValue()).isEqualByComparingTo("-0.100000");
        assertThat(assessment.valueRating()).isEqualTo(ValueRating.NEGATIVE_VALUE);
    }

    @Test
    void rejectsInvalidDecimalOdds() {
        assertThatThrownBy(() -> calculator.impliedProbability(BigDecimal.ONE))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("decimalOdds");
    }
}
