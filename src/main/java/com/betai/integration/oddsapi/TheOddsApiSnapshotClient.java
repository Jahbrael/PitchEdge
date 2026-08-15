package com.betai.integration.oddsapi;

import com.betai.config.OddsProviderProperties;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.source.SourceTarget;
import com.betai.exception.InvalidRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class TheOddsApiSnapshotClient {

    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final HttpClient httpClient;
    private final OddsProviderProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TheOddsApiFetchResult fetch(SourceTarget sourceTarget, LocalDate refreshDate) {
        URI uri = render(sourceTarget, refreshDate);
        String sourceUrl = redactSecrets(uri.toString());
        OffsetDateTime startedAt = OffsetDateTime.now(clock);

        if (!properties.enabled() || !StringUtils.hasText(properties.apiKey())) {
            return failure(sourceUrl, startedAt, "The Odds API integration is disabled or the API key is missing.");
        }
        if (!isTheOddsApiTarget(sourceTarget, uri)) {
            return failure(sourceUrl, startedAt, "Only The Odds API source targets may be refreshed by the odds API client.");
        }

        try {
            HttpResponse<byte[]> response = httpClient.send(request(sourceTarget, uri), HttpResponse.BodyHandlers.ofByteArray());
            OffsetDateTime fetchedAt = OffsetDateTime.now(clock);
            byte[] body = response.body() == null ? new byte[0] : response.body();
            String rawPayload = new String(body, StandardCharsets.UTF_8);
            String contentType = response.headers().firstValue("Content-Type").orElse(null);
            ScrapeStatus status = response.statusCode() >= 200 && response.statusCode() < 300
                    ? ScrapeStatus.SUCCESS
                    : ScrapeStatus.FAILED;
            return new TheOddsApiFetchResult(
                    sourceUrl,
                    status,
                    response.statusCode(),
                    fetchedAt,
                    Duration.between(startedAt, fetchedAt).toMillis(),
                    sha256(body),
                    contentType,
                    (long) body.length,
                    headersJson(response),
                    rawPayload,
                    status == ScrapeStatus.SUCCESS ? null : "The Odds API returned HTTP " + response.statusCode() + "."
            );
        } catch (IOException exception) {
            return failure(sourceUrl, startedAt, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(sourceUrl, startedAt, "The Odds API request was interrupted.");
        }
    }

    private HttpRequest request(SourceTarget sourceTarget, URI uri) {
        int timeoutMs = sourceTarget.getTimeoutMs() <= 0
                ? 30000
                : sourceTarget.getTimeoutMs();
        String userAgent = StringUtils.hasText(sourceTarget.getUserAgent())
                ? sourceTarget.getUserAgent()
                : "BetAI-TheOddsAPI/1.0";
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private URI render(SourceTarget sourceTarget, LocalDate refreshDate) {
        String season = sourceTarget.getLeague().getCurrentSeason().replace('/', '-');
        String seasonLabel = sourceTarget.getTargetSeasonLabel() == null
                ? season
                : sourceTarget.getTargetSeasonLabel().replace('/', '-');
        String seasonToken = sourceTarget.getSourceSeasonToken() == null
                ? sourceSeasonToken(sourceTarget.getLeague().getCurrentSeason())
                : sourceTarget.getSourceSeasonToken();
        String url = sourceTarget.getUrlTemplate()
                .replace("{leagueCode}", sourceTarget.getLeague().getCode().name())
                .replace("{date}", refreshDate.toString())
                .replace("{yyyyMMdd}", COMPACT_DATE.format(refreshDate))
                .replace("{season}", season)
                .replace("{seasonLabel}", seasonLabel)
                .replace("{seasonToken}", seasonToken)
                .replace("{theOddsApiBaseUrl}", trimTrailingSlash(properties.baseUrl()))
                .replace("{theOddsApiKey}", encode(properties.apiKey()))
                .replace("{theOddsApiRegions}", encode(properties.regions()))
                .replace("{theOddsApiMarkets}", encode(properties.markets()))
                .replace("{theOddsApiOddsFormat}", encode(properties.oddsFormat()))
                .replace("{theOddsApiDateFormat}", encode(properties.dateFormat()));
        try {
            return new URI(url);
        } catch (URISyntaxException exception) {
            throw new InvalidRequestException("Rendered The Odds API URL is invalid for target "
                    + sourceTarget.getName() + ": " + exception.getMessage());
        }
    }

    private boolean isTheOddsApiTarget(SourceTarget sourceTarget, URI uri) {
        String name = sourceTarget.getName() == null ? "" : sourceTarget.getName();
        String host = uri.getHost() == null ? "" : uri.getHost();
        String url = sourceTarget.getUrlTemplate() == null ? "" : sourceTarget.getUrlTemplate();
        return name.contains("The Odds API")
                || host.contains("the-odds-api.com")
                || url.contains("{theOddsApiBaseUrl}")
                || url.contains("the-odds-api.com");
    }

    private TheOddsApiFetchResult failure(String sourceUrl, OffsetDateTime startedAt, String message) {
        OffsetDateTime failedAt = OffsetDateTime.now(clock);
        return new TheOddsApiFetchResult(
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

    private String headersJson(HttpResponse<byte[]> response) {
        try {
            return objectMapper.writeValueAsString(response.headers().map());
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"response headers could not be serialized\"}";
        }
    }

    private String sourceSeasonToken(String seasonLabel) {
        String[] parts = seasonLabel.split("/");
        if (parts.length == 2 && parts[0].length() >= 2 && parts[1].length() >= 2) {
            return parts[0].substring(parts[0].length() - 2) + parts[1].substring(parts[1].length() - 2);
        }
        return seasonLabel.replace("/", "").replace("-", "");
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
    }

    private String redactSecrets(String sourceUrl) {
        if (!StringUtils.hasText(properties.apiKey())) {
            return sourceUrl;
        }
        return sourceUrl.replace(encode(properties.apiKey()), "REDACTED");
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
