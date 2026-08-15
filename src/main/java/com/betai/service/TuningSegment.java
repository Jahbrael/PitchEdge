package com.betai.service;

import java.math.BigDecimal;

final class TuningSegment {

    static final String GLOBAL = "GLOBAL";

    private static final BigDecimal P40 = new BigDecimal("0.400000");
    private static final BigDecimal P55 = new BigDecimal("0.550000");
    private static final BigDecimal P70 = new BigDecimal("0.700000");
    private static final BigDecimal P85 = new BigDecimal("0.850000");

    private TuningSegment() {
    }

    static String probabilityBand(BigDecimal probability) {
        if (probability == null) {
            return GLOBAL;
        }
        if (probability.compareTo(P40) < 0) {
            return "P00_40";
        }
        if (probability.compareTo(P55) < 0) {
            return "P40_55";
        }
        if (probability.compareTo(P70) < 0) {
            return "P55_70";
        }
        if (probability.compareTo(P85) < 0) {
            return "P70_85";
        }
        return "P85_100";
    }

    static String noteLabel(String segmentKey) {
        return GLOBAL.equals(segmentKey) ? "global league/market" : "probability-band " + segmentKey;
    }
}
