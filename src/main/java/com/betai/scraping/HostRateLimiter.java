package com.betai.scraping;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class HostRateLimiter {

    private final Clock clock;
    private final Map<String, Instant> nextAllowedAtByHost = new HashMap<>();

    public HostRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void throttle(URI uri, int rateLimitPerMinute) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        long intervalMillis = Math.max(1L, 60_000L / rateLimitPerMinute);
        Instant now = Instant.now(clock);
        Instant nextAllowedAt = nextAllowedAtByHost.get(host);

        if (nextAllowedAt != null && now.isBefore(nextAllowedAt)) {
            sleep(Duration.between(now, nextAllowedAt));
            now = Instant.now(clock);
        }

        nextAllowedAtByHost.put(host, now.plusMillis(intervalMillis));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Scrape rate limiter was interrupted.", exception);
        }
    }
}
