package com.betai.security;

import com.betai.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int CLEANUP_INTERVAL_REQUESTS = 1_000;

    private final SecurityProperties securityProperties;
    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || path.startsWith("/actuator/")
                || path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/artwork/")
                || !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        cleanupOccasionally();

        String scope = request.getRequestURI().startsWith("/api/v1/admin/") ? "admin" : "public";
        int limit = "admin".equals(scope)
                ? securityProperties.adminRequestsPerMinute()
                : securityProperties.publicRequestsPerMinute();

        if (limit <= 0) {
            reject(response, 60, "Rate limiting is misconfigured for " + scope + " routes.");
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String identity = (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser"))
                ? authenticatedIdentity(authentication)
                : "ip:" + clientIp(request);
                
        String key = scope + ":" + identity;
        long now = clock.millis();
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now));
        RateDecision decision = counter.tryAcquire(now, limit);
        if (!decision.allowed()) {
            reject(response, decision.retryAfterSeconds(), "Rate limit exceeded for " + scope + " routes.");
            return;
        }

        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String authenticatedIdentity(Authentication authentication) {
        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return "user-id:" + userDetails.getId();
        }
        return "principal:" + authentication.getName();
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
}
