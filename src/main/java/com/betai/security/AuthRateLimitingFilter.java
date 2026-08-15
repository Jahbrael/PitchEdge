package com.betai.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class AuthRateLimitingFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int CLEANUP_INTERVAL_REQUESTS = 1_000;
    private static final int AUTH_LIMIT_PER_MINUTE = 5;

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !(path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        cleanupOccasionally();

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String username = extractUsername(cachedRequest.getCachedBody());
        String normalizedUsername = username != null ? username.trim().toLowerCase() : "unknown";

        String key = "auth:" + clientIp(request) + ":" + normalizedUsername;
        long now = clock.millis();
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now));
        RateDecision decision = counter.tryAcquire(now, AUTH_LIMIT_PER_MINUTE);
        if (!decision.allowed()) {
            reject(response, decision.retryAfterSeconds(), "Rate limit exceeded for authentication attempts.");
            return;
        }

        response.setHeader("X-Auth-RateLimit-Limit", Integer.toString(AUTH_LIMIT_PER_MINUTE));
        response.setHeader("X-Auth-RateLimit-Remaining", Integer.toString(decision.remaining()));
        filterChain.doFilter(cachedRequest, response);
    }

    private String extractUsername(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.readTree(body);
            if (tree.has("username") && tree.get("username").isTextual()) {
                return tree.get("username").asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds, String message) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(Math.max(1, retryAfterSeconds)));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"" + message + "\"}");
    }

    private void cleanupOccasionally() {
        if (requestCounter.incrementAndGet() % CLEANUP_INTERVAL_REQUESTS != 0) {
            return;
        }
        long cutoff = clock.millis() - WINDOW_MILLIS * 2;
        Iterator<Map.Entry<String, WindowCounter>> iterator = counters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, WindowCounter> entry = iterator.next();
            if (entry.getValue().windowStartedAtMillis() < cutoff) {
                iterator.remove();
            }
        }
    }

    private static final class WindowCounter {
        private long windowStartedAtMillis;
        private int count;

        private WindowCounter(long windowStartedAtMillis) {
            this.windowStartedAtMillis = windowStartedAtMillis;
        }

        private synchronized RateDecision tryAcquire(long now, int limit) {
            if (now - windowStartedAtMillis >= WINDOW_MILLIS) {
                windowStartedAtMillis = now;
                count = 0;
            }
            if (count >= limit) {
                long retryAfterMillis = WINDOW_MILLIS - (now - windowStartedAtMillis);
                long retryAfterSeconds = Math.max(1, (retryAfterMillis + 999) / 1000);
                return new RateDecision(false, 0, retryAfterSeconds);
            }
            count++;
            return new RateDecision(true, limit - count, 0);
        }

        private synchronized long windowStartedAtMillis() {
            return windowStartedAtMillis;
        }
    }

    private record RateDecision(boolean allowed, int remaining, long retryAfterSeconds) {
    }

    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
        }

        public byte[] getCachedBody() {
            return this.cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new CachedBodyServletInputStream(this.cachedBody);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
            return new BufferedReader(new InputStreamReader(byteArrayInputStream));
        }

        private static class CachedBodyServletInputStream extends ServletInputStream {
            private final ByteArrayInputStream buffer;

            public CachedBodyServletInputStream(byte[] contents) {
                this.buffer = new ByteArrayInputStream(contents);
            }

            @Override
            public int read() throws IOException {
                return buffer.read();
            }

            @Override
            public boolean isFinished() {
                return buffer.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException();
            }
        }
    }
}
