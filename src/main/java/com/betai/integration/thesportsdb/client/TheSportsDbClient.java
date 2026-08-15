package com.betai.integration.thesportsdb.client;

import com.betai.integration.thesportsdb.TheSportsDbProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class TheSportsDbClient {

    private static final int MAX_ATTEMPTS = 3;

    private final HttpClient httpClient;
    private final TheSportsDbProperties properties;
    private final TheSportsDbRateLimiter rateLimiter;
    private final TheSportsDbSecretRedactor secretRedactor;
    private final TheSportsDbClientMetrics metrics;
    private final Clock clock;

    public TheSportsDbClient(
            @Qualifier("theSportsDbHttpClient") HttpClient httpClient,
            TheSportsDbProperties properties,
            TheSportsDbRateLimiter rateLimiter,
            TheSportsDbSecretRedactor secretRedactor,
            TheSportsDbClientMetrics metrics,
            Clock clock
    ) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.secretRedactor = secretRedactor;
        this.metrics = metrics;
        this.clock = clock;
    }

    public TheSportsDbClientResponse allLeagues() {
        return get(TheSportsDbEndpoint.ALL_LEAGUES, Map.of());
    }

    public TheSportsDbClientResponse lookupLeague(String leagueId) {
        return get(TheSportsDbEndpoint.LOOKUP_LEAGUE, Map.of(), leagueId);
    }

    public TheSportsDbClientResponse listTeams(String leagueId) {
        return get(TheSportsDbEndpoint.LIST_TEAMS, Map.of(), leagueId);
    }

    public TheSportsDbClientResponse listSeasons(String leagueId) {
        return get(TheSportsDbEndpoint.LIST_SEASONS, Map.of(), leagueId);
    }

    public TheSportsDbClientResponse scheduleLeague(String leagueId, String season) {
        return get(TheSportsDbEndpoint.SCHEDULE_LEAGUE, Map.of(), leagueId, season);
    }

    public TheSportsDbClientResponse lookupEvent(String eventId) {
        return get(TheSportsDbEndpoint.LOOKUP_EVENT, Map.of(), eventId);
    }

    public TheSportsDbClientResponse lookupEventStats(String eventId) {
        return get(TheSportsDbEndpoint.LOOKUP_EVENT_STATS, Map.of(), eventId);
    }

    public TheSportsDbClientResponse lookupEventLineup(String eventId) {
        return get(TheSportsDbEndpoint.LOOKUP_EVENT_LINEUP, Map.of(), eventId);
    }

    public TheSportsDbClientResponse lookupEventTimeline(String eventId) {
        return get(TheSportsDbEndpoint.LOOKUP_EVENT_TIMELINE, Map.of(), eventId);
    }

    public TheSportsDbClientResponse lookupEventResults(String eventId) {
        return get(TheSportsDbEndpoint.LOOKUP_EVENT_RESULTS, Map.of(), eventId);
    }

    public TheSportsDbClientResponse lookupPlayer(String playerId) {
        return get(TheSportsDbEndpoint.LOOKUP_PLAYER, Map.of(), playerId);
    }

    public TheSportsDbClientResponse lookupPlayerStats(String playerId) {
        return get(TheSportsDbEndpoint.LOOKUP_PLAYER_STATS, Map.of(), playerId);
    }

    public TheSportsDbClientResponse lookupPlayerResults(String playerId) {
        return get(TheSportsDbEndpoint.LOOKUP_PLAYER_RESULTS, Map.of(), playerId);
    }

    public TheSportsDbClientResponse liveScoreSoccer() {
        return get(TheSportsDbEndpoint.LIVESCORE_SOCCER, Map.of());
    }

    public TheSportsDbClientResponse liveScoreLeague(String leagueId) {
        return get(TheSportsDbEndpoint.LIVESCORE_LEAGUE, Map.of(), leagueId);
    }

    public TheSportsDbClientResponse get(TheSportsDbEndpoint endpoint, Map<String, String> queryParams, Object... pathArgs) {
        if (!properties.enabled()) {
            throw new TheSportsDbClientException("TheSportsDB integration is disabled.", 0);
        }
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new TheSportsDbClientException("TheSportsDB API key is not configured.", 0);
        }

        String path = endpoint.path(pathArgs);
        URI uri = uri(path, queryParams);
        int attempt = 1;
        while (true) {
            try {
                rateLimiter.acquire();
                metrics.recordRequest();
                HttpResponse<String> response = httpClient.send(request(uri), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    metrics.recordSuccess();
                    return new TheSportsDbClientResponse(endpoint, path, response.statusCode(), OffsetDateTime.now(clock), response.body());
                }
                if (response.statusCode() == 429) {
                    metrics.recordTooManyRequests();
                }
                if (!retryable(response.statusCode()) || attempt >= MAX_ATTEMPTS) {
                    throw new TheSportsDbClientException(
                            "TheSportsDB request failed for " + endpoint.name() + " at /" + path
                                    + " with HTTP " + response.statusCode() + ".",
                            response.statusCode()
                    );
                }
                sleep(retryDelay(response, attempt));
                attempt++;
            } catch (IOException exception) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new TheSportsDbClientException(
                            "TheSportsDB request failed for " + endpoint.name() + " at /" + path
                                    + ": " + secretRedactor.redact(exception.getMessage()) + ".",
                            0
                    );
                }
                sleep(backoff(attempt));
                attempt++;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new TheSportsDbClientException("TheSportsDB request was interrupted for " + endpoint.name() + ".", 0);
            }
        }
    }

    private HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(properties.readTimeout())
                .header("Accept", "application/json")
                .header("X-API-KEY", properties.apiKey().trim())
                .GET()
                .build();
    }

    private URI uri(String path, Map<String, String> queryParams) {
        String baseUrl = StringUtils.hasText(properties.baseUrl())
                ? properties.baseUrl().trim().replaceAll("/+$", "")
                : "https://www.thesportsdb.com/api/v2/json";
        StringBuilder builder = new StringBuilder(baseUrl).append('/').append(path);
        if (queryParams != null && !queryParams.isEmpty()) {
            String query = queryParams.entrySet().stream()
                    .filter(entry -> StringUtils.hasText(entry.getValue()))
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .reduce((left, right) -> left + "&" + right)
                    .orElse("");
            if (StringUtils.hasText(query)) {
                builder.append('?').append(query);
            }
        }
        return URI.create(builder.toString());
    }

    private boolean retryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private Duration retryDelay(HttpResponse<?> response, int attempt) {
        Optional<String> retryAfter = response.headers().firstValue("Retry-After");
        if (retryAfter.isPresent()) {
            try {
                long seconds = Long.parseLong(retryAfter.get().trim());
                return Duration.ofSeconds(Math.max(0L, seconds));
            } catch (NumberFormatException ignored) {
            }
        }
        return backoff(attempt);
    }

    private Duration backoff(int attempt) {
        long baseMillis = 250L * (1L << Math.max(0, attempt - 1));
        long jitterMillis = ThreadLocalRandom.current().nextLong(25L, 125L);
        return Duration.ofMillis(baseMillis + jitterMillis);
    }

    private void sleep(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TheSportsDbClientException("TheSportsDB retry sleep was interrupted.", 0);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
