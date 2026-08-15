package com.betai.domain.settlement;

public record SettlementCounters(
        int evaluated,
        int won,
        int lost,
        int voided,
        int skipped
) {
    public static SettlementCounters empty() {
        return new SettlementCounters(0, 0, 0, 0, 0);
    }
}
