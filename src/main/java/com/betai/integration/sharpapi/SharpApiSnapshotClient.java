package com.betai.integration.sharpapi;

import com.betai.config.SharpApiProperties;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.source.SourceTarget;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;

@Slf4j
@Component
@RequiredArgsConstructor
public class SharpApiSnapshotClient {

    private final HttpClient httpClient;
    private final SharpApiProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SharpApiFetchResult fetch(SourceTarget sourceTarget, LocalDate refreshDate) {
        String baseUrl = trimTrailingSlash(properties.baseUrl());
        String oddsUrl = sourceTarget.getUrlTemplate().replace("{sharpApiBaseUrl}", baseUrl);
        
        String sourceUrl = redactSecrets(oddsUrl);
        OffsetDateTime startedAt = OffsetDateTime.now(clock);

        if (!properties.enabled() || !StringUtils.hasText(properties.apiKey())) {
            return failure(sourceUrl, startedAt, "SharpAPI integration is disabled or the API key is missing.");
        }
        if (!isSharpApiTarget(sourceTarget, oddsUrl)) {
            return failure(sourceUrl, startedAt, "Only SharpAPI source targets may be refreshed by the SharpAPI client.");
        }

        try {
            // Extract league query param to use for events fetch
            String leagueParam = extractQueryParam(oddsUrl, "league");
            String eventsUrlStr = baseUrl + "/events" + (StringUtils.hasText(leagueParam) ? "?league=" + leagueParam : "");
            
            ArrayNode eventsData = fetchAllPages(eventsUrlStr);
            ArrayNode oddsData = fetchAllPages(oddsUrl);

            OffsetDateTime fetchedAt = OffsetDateTime.now(clock);
            ObjectNode combinedRoot = objectMapper.createObjectNode();
            combinedRoot.set("events", eventsData);
            combinedRoot.set("data", oddsData);
            byte[] combinedPayload = objectMapper.writeValueAsBytes(combinedRoot);
            
            return new SharpApiFetchResult(
                    sourceUrl,
                    ScrapeStatus.SUCCESS,
                    200,
                    fetchedAt,
                    Duration.between(startedAt, fetchedAt).toMillis(),
                    sha256(combinedPayload),
                    "application/json",
                    (long) combinedPayload.length,
                    "{}",
                    new String(combinedPayload, StandardCharsets.UTF_8),
                    null
            );

        } catch (IOException | URISyntaxException exception) {
            return failure(sourceUrl, startedAt, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(sourceUrl, startedAt, "SharpAPI request was interrupted.");
        }
    }

    private ArrayNode fetchAllPages(String initialUrl) throws IOException, URISyntaxException, InterruptedException {
        ArrayNode allData = objectMapper.createArrayNode();
        String currentUrl = initialUrl;
        int pageCount = 0;

        while (StringUtils.hasText(currentUrl) && pageCount < 10) {
            URI uri = new URI(currentUrl);
            HttpResponse<byte[]> response = httpClient.send(request(uri), HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (pageCount == 0) {
                    throw new IOException("SharpAPI returned HTTP " + response.statusCode());
                } else {
                    log.warn("SharpAPI pagination failed at page {} with HTTP {}", pageCount, response.statusCode());
                    break;
                }
            }

            byte[] body = response.body() == null ? new byte[0] : response.body();
            JsonNode root = objectMapper.readTree(body);
            
            if (root.has("data") && root.get("data").isArray()) {
                allData.addAll((ArrayNode) root.get("data"));
            } else if (root.isArray()) {
                allData.addAll((ArrayNode) root);
            }

            String nextCursor = root.path("meta").path("next_cursor").asText(null);
            if (StringUtils.hasText(nextCursor)) {
                currentUrl = appendOrReplaceQueryParam(initialUrl, "cursor", encode(nextCursor));
                pageCount++;
            } else {
                currentUrl = null;
            }
        }
        return allData;
    }

    private HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(30000))
                .header("User-Agent", "BetAI-SharpAPI/1.0")
                .header("Accept", "application/json")
                .header("X-API-Key", properties.apiKey())
                .GET()
                .build();
    }

    private boolean isSharpApiTarget(SourceTarget sourceTarget, String initialUrl) {
        String name = sourceTarget.getName() == null ? "" : sourceTarget.getName();
        return name.contains("SharpAPI") || initialUrl.contains("sharpapi.io");
    }

    private SharpApiFetchResult failure(String sourceUrl, OffsetDateTime startedAt, String message) {
        OffsetDateTime failedAt = OffsetDateTime.now(clock);
        return new SharpApiFetchResult(
                sourceUrl,
                ScrapeStatus.FAILED,
                null,
                failedAt,
                Duration.between(startedAt, failedAt).toMillis(),
                sha256((sourceUrl + "|" + failedAt + "|" + message).getBytes(StandardCharsets.UTF_8)),
                null,
                null,
                null,
                null,
                message
        );
    }

    private String extractQueryParam(String url, String param) {
        if (!url.contains(param + "=")) return null;
        String[] parts = url.split(param + "=");
        if (parts.length < 2) return null;
        return parts[1].split("&")[0];
    }

    private String appendOrReplaceQueryParam(String url, String param, String value) {
        if (url.contains("?" + param + "=") || url.contains("&" + param + "=")) {
            return url.replaceAll("(" + param + "=)[^&]*", "$1" + value);
        }
        return url + (url.contains("?") ? "&" : "?") + param + "=" + value;
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) return "";
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
    }

    private String redactSecrets(String sourceUrl) {
        return sourceUrl;
    }

    private String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 hashing is not available.", exception);
        }
    }
}
