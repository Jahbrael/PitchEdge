package com.betai.integration.thesportsdb.client;

import com.betai.integration.thesportsdb.TheSportsDbProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

@Component
public class TheSportsDbRateLimiter {

    private final TheSportsDbProperties properties;
    private final Clock clock;
    private final Deque<Instant> requestTimes = new ArrayDeque<>();

    public TheSportsDbRateLimiter(TheSportsDbProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized void acquire() {
        int maxRequests = Math.max(1, Math.min(properties.requestsPerMinute(), 80));
        Instant now = Instant.now(clock);
        prune(now);
        if (requestTimes.size() >= maxRequests) {
            Instant oldest = requestTimes.peekFirst();
            long waitMillis = Math.max(1L, Duration.between(now, oldest.plus(Duration.ofMinutes(1))).toMillis());
            sleep(waitMillis);
            now = Instant.now(clock);
            prune(now);
        }
        requestTimes.addLast(now);
    }

    private void prune(Instant now) {
        Instant threshold = now.minus(Duration.ofMinutes(1));
        while (!requestTimes.isEmpty() && requestTimes.peekFirst().isBefore(threshold)) {
            requestTimes.removeFirst();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TheSportsDbClientException("TheSportsDB request limiter was interrupted.", 0);
        }
    }
}
