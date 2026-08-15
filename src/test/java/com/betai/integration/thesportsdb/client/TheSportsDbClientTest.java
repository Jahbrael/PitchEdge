package com.betai.integration.thesportsdb.client;

import com.betai.integration.thesportsdb.TheSportsDbProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TheSportsDbClientTest {

    private static final String API_KEY = "test-thesportsdb-api-key";

    @Mock
    private HttpClient httpClient;

    private TheSportsDbClient client;
    private TheSportsDbClientMetrics metrics;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-20T10:00:00Z"), ZoneOffset.UTC);
        TheSportsDbProperties properties = properties(API_KEY);
        metrics = new TheSportsDbClientMetrics(clock);
        client = new TheSportsDbClient(
                httpClient,
                properties,
                new TheSportsDbRateLimiter(properties, clock),
                new TheSportsDbSecretRedactor(properties),
                metrics,
                clock
        );
    }

    @Test
    void sendsApiKeyAsHeaderAndNeverAsUrlParameter() throws Exception {
        whenSend().thenReturn(response(200, Map.of(), "{\"teams\":[]}"));

        TheSportsDbClientResponse response = client.listTeams("4328");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();

        assertThat(request.headers().firstValue("X-API-KEY")).contains(API_KEY);
        assertThat(request.uri().toString()).isEqualTo("https://example.test/api/v2/json/list/teams/4328");
        assertThat(request.uri().toString()).doesNotContain(API_KEY);
        assertThat(response.endpoint()).isEqualTo(TheSportsDbEndpoint.LIST_TEAMS);
        assertThat(response.path()).isEqualTo("list/teams/4328");
        assertThat(response.rawJson()).isEqualTo("{\"teams\":[]}");
    }

    @Test
    void looksUpLeagueArtworkWithoutPuttingApiKeyInUrl() throws Exception {
        whenSend().thenReturn(response(200, Map.of(), "{\"league\":[]}"));

        TheSportsDbClientResponse response = client.lookupLeague("4328");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();

        assertThat(request.headers().firstValue("X-API-KEY")).contains(API_KEY);
        assertThat(request.uri().toString()).isEqualTo("https://example.test/api/v2/json/lookup/league/4328");
        assertThat(request.uri().toString()).doesNotContain(API_KEY);
        assertThat(response.endpoint()).isEqualTo(TheSportsDbEndpoint.LOOKUP_LEAGUE);
        assertThat(response.path()).isEqualTo("lookup/league/4328");
    }

    @Test
    void doesNotRetryOrdinaryClientErrors() throws Exception {
        whenSend().thenReturn(response(403, Map.of(), "{\"error\":\"forbidden\"}"));

        assertThatThrownBy(() -> client.allLeagues())
                .isInstanceOf(TheSportsDbClientException.class)
                .hasMessageContaining("HTTP 403");

        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void retriesTooManyRequestsAndTracksMetric() throws Exception {
        whenSend()
                .thenReturn(response(429, Map.of("Retry-After", List.of("0")), "{\"error\":\"rate limit\"}"))
                .thenReturn(response(429, Map.of("Retry-After", List.of("0")), "{\"error\":\"rate limit\"}"))
                .thenReturn(response(200, Map.of(), "{\"events\":[]}"));

        TheSportsDbClientResponse response = client.scheduleLeague("4328", "2026");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(metrics.tooManyRequestsCount()).isEqualTo(2);
        verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void stopsRetryingTooManyRequestsAfterBoundedAttempts() throws Exception {
        whenSend()
                .thenReturn(response(429, Map.of("Retry-After", List.of("0")), "{}"))
                .thenReturn(response(429, Map.of("Retry-After", List.of("0")), "{}"))
                .thenReturn(response(429, Map.of("Retry-After", List.of("0")), "{}"));

        assertThatThrownBy(() -> client.allLeagues())
                .isInstanceOf(TheSportsDbClientException.class)
                .hasMessageContaining("HTTP 429");

        assertThat(metrics.tooManyRequestsCount()).isEqualTo(3);
        verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void redactsApiKeyAndHeaderNamesFromMessages() {
        TheSportsDbSecretRedactor redactor = new TheSportsDbSecretRedactor(properties(API_KEY));

        String redacted = redactor.redact("failed with X-API-KEY: " + API_KEY + " and api_key=" + API_KEY);

        assertThat(redacted).doesNotContain(API_KEY);
        assertThat(redacted).contains("X-API-KEY: REDACTED");
        assertThat(redacted).contains("api_key=REDACTED");
    }

    private TheSportsDbProperties properties(String apiKey) {
        return new TheSportsDbProperties(
                true,
                "https://example.test/api/v2/json",
                apiKey,
                80,
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                false,
                false
        );
    }

    @SuppressWarnings("unchecked")
    private org.mockito.stubbing.OngoingStubbing<HttpResponse<String>> whenSend() throws IOException, InterruptedException {
        return when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)));
    }

    private HttpResponse<String> response(int statusCode, Map<String, List<String>> headers, String body) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (left, right) -> true);
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("https://example.test/api/v2/json");
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_2;
            }
        };
    }
}
