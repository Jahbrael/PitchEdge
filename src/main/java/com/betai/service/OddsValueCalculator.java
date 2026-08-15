package com.betai.service;

import com.betai.domain.odds.ValueRating;
import com.betai.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Component
public class OddsValueCalculator {

    private static final MathContext MATH_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal MINIMUM_DECIMAL_ODDS = new BigDecimal("1.0001");
    private static final BigDecimal STRONG_VALUE_EV = new BigDecimal("0.100000");
    private static final BigDecimal STRONG_VALUE_EDGE = new BigDecimal("0.030000");
    private static final BigDecimal VALUE_EV = new BigDecimal("0.030000");
    private static final BigDecimal VALUE_EDGE = new BigDecimal("0.010000");
    private static final BigDecimal FAIR_EV_FLOOR = new BigDecimal("-0.030000");

    public BigDecimal impliedProbability(BigDecimal decimalOdds) {
        validateDecimalOdds(decimalOdds);
        return BigDecimal.ONE.divide(decimalOdds, 6, RoundingMode.HALF_UP);
    }

    public OddsValueAssessment assess(BigDecimal modelProbability, BigDecimal decimalOdds) {
        validateProbability(modelProbability);
        BigDecimal impliedProbability = impliedProbability(decimalOdds);
        BigDecimal valueEdge = modelProbability.subtract(impliedProbability, MATH_CONTEXT)
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal expectedValue = modelProbability.multiply(decimalOdds, MATH_CONTEXT)
                .subtract(BigDecimal.ONE, MATH_CONTEXT)
                .setScale(6, RoundingMode.HALF_UP);
        ValueRating rating = rating(expectedValue, valueEdge);
        return new OddsValueAssessment(
                impliedProbability,
                valueEdge,
                expectedValue,
                rating,
                note(modelProbability, decimalOdds, impliedProbability, valueEdge, expectedValue, rating)
        );
    }

    private ValueRating rating(BigDecimal expectedValue, BigDecimal valueEdge) {
        if (expectedValue.compareTo(STRONG_VALUE_EV) >= 0 && valueEdge.compareTo(STRONG_VALUE_EDGE) >= 0) {
            return ValueRating.STRONG_VALUE;
        }
        if (expectedValue.compareTo(VALUE_EV) >= 0 && valueEdge.compareTo(VALUE_EDGE) >= 0) {
            return ValueRating.VALUE;
        }
        if (expectedValue.compareTo(FAIR_EV_FLOOR) >= 0) {
            return ValueRating.FAIR;
        }
        return ValueRating.NEGATIVE_VALUE;
    }

    private String note(
            BigDecimal modelProbability,
            BigDecimal decimalOdds,
            BigDecimal impliedProbability,
            BigDecimal valueEdge,
            BigDecimal expectedValue,
            ValueRating rating
    ) {
        return "Value uses tuned model probability " + modelProbability.setScale(6, RoundingMode.HALF_UP)
                + ", decimal odds " + decimalOdds.setScale(4, RoundingMode.HALF_UP)
                + ", implied probability " + impliedProbability
                + ", edge " + valueEdge
                + ", EV " + expectedValue
                + ", rating " + rating.name() + ".";
    }

    private void validateDecimalOdds(BigDecimal decimalOdds) {
        if (decimalOdds == null || decimalOdds.compareTo(MINIMUM_DECIMAL_ODDS) < 0) {
            throw new InvalidRequestException("decimalOdds must be greater than 1.0000.");
        }
    }

    private void validateProbability(BigDecimal probability) {
        if (probability == null
                || probability.compareTo(BigDecimal.ZERO) < 0
                || probability.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidRequestException("model probability must be between 0 and 1.");
        }
    }
}
