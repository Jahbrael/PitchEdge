package com.betai.integration.thesportsdb.client;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TheSportsDbClientMetrics {

    private final Clock clock;
    private final AtomicReference<OffsetDateTime> lastSuccessfulRequestAt = new AtomicReference<>();
    private final AtomicReference<OffsetDateTime> currentMinute = new AtomicReference<>();
    private final AtomicInteger requestsInCurrentMinute = new AtomicInteger();
    private final AtomicInteger tooManyRequestsCount = new AtomicInteger();

    public TheSportsDbClientMetrics(Clock clock) {
        this.clock = clock;
    }

    public void recordRequest() {
        OffsetDateTime minute = OffsetDateTime.now(clock)
                .withSecond(0)
                .withNano(0);
        OffsetDateTime existing = currentMinute.get();
        if (!minute.equals(existing) && currentMinute.compareAndSet(existing, minute)) {
            requestsInCurrentMinute.set(0);
        }
        requestsInCurrentMinute.incrementAndGet();
    }

    public void recordSuccess() {
        lastSuccessfulRequestAt.set(OffsetDateTime.now(clock));
    }

    public void recordTooManyRequests() {
        tooManyRequestsCount.incrementAndGet();
    }

    public OffsetDateTime lastSuccessfulRequestAt() {
        return lastSuccessfulRequestAt.get();
    }

    public int requestsInCurrentMinute() {
        recordRequestWindowOnly();
        return requestsInCurrentMinute.get();
    }

    public int tooManyRequestsCount() {
        return tooManyRequestsCount.get();
    }

    private void recordRequestWindowOnly() {
        OffsetDateTime minute = OffsetDateTime.now(clock)
                .withSecond(0)
                .withNano(0);
        OffsetDateTime existing = currentMinute.get();
        if (!minute.equals(existing) && currentMinute.compareAndSet(existing, minute)) {
            requestsInCurrentMinute.set(0);
        }
    }
}
