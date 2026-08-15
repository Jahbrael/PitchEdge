package com.betai.scraping;

import com.betai.config.ScrapingProperties;
import com.betai.config.ApiFootballProperties;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceTarget;
import com.betai.util.SnapshotPayloads;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StaticHttpSourceScraper {

    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([^;]+)", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final UrlTemplateRenderer urlTemplateRenderer;
    private final RobotsTxtService robotsTxtService;
    private final HostRateLimiter hostRateLimiter;
    private final HashingService hashingService;
    private final ObjectMapper objectMapper;
    private final ScrapingProperties scrapingProperties;
    private final ApiFootballProperties apiFootballProperties;
    private final Clock clock;

    public ScrapeOutcome scrape(SourceTarget sourceTarget, LocalDate refreshDate) {
        URI sourceUri = urlTemplateRenderer.render(sourceTarget, refreshDate);
        URI payloadUri = sourceUri;
        String persistedSourceUrl = redactSecrets(sourceUri.toString());
        OffsetDateTime startedAt = OffsetDateTime.now(clock);

        if (sourceTarget.getRenderMode() != RenderMode.STATIC_HTML) {
            return failure(
                    persistedSourceUrl,
                    ScrapeStatus.UNSUPPORTED_RENDER_MODE,
                    startedAt,
                    "Render mode " + sourceTarget.getRenderMode() + " requires a browser scraping worker."
            );
        }

        if (sourceTarget.isRobotsTxtRequired() && !robotsTxtService.isAllowed(sourceUri, sourceTarget.getUserAgent())) {
            return failure(
                    persistedSourceUrl,
                    ScrapeStatus.ROBOTS_BLOCKED,
                    startedAt,
                    "robots.txt does not allow this target for the configured user agent."
            );
        }

        hostRateLimiter.throttle(sourceUri, sourceTarget.getRateLimitPerMinute());

        try {
            HttpResponse<byte[]> response = sendRequest(sourceTarget, sourceUri);
            if (response.statusCode() >= 200 && response.statusCode() < 300 && isSgoddsLeagueDownloadPageTarget(sourceTarget)) {
                Optional<URI> downloadUri = resolveSgoddsCsvDownloadUri(sourceTarget, sourceUri, response);
                if (downloadUri.isEmpty()) {
                    return failure(
                            persistedSourceUrl,
                            ScrapeStatus.FAILED,
                            startedAt,
                            "Sgodds data page did not contain a CSV download link for league "
                                    + selectorText(sourceTarget, "leagueName").orElse("unknown") + "."
                    );
                }
                URI csvUri = downloadUri.get();
                if (sourceTarget.isRobotsTxtRequired() && !robotsTxtService.isAllowed(csvUri, sourceTarget.getUserAgent())) {
                    return failure(
                            redactSecrets(csvUri.toString()),
                            ScrapeStatus.ROBOTS_BLOCKED,
                            startedAt,
                            "robots.txt does not allow the resolved Sgodds CSV download for the configured user agent."
                    );
                }
                hostRateLimiter.throttle(csvUri, sourceTarget.getRateLimitPerMinute());
                response = sendRequest(sourceTarget, csvUri);
                payloadUri = csvUri;
                persistedSourceUrl = redactSecrets(csvUri.toString());
            }
            OffsetDateTime fetchedAt = OffsetDateTime.now(clock);
            byte[] body = response.body() == null ? new byte[0] : response.body();
            String contentType = response.headers().firstValue("Content-Type").orElse(null);
            long durationMs = Duration.between(startedAt, fetchedAt).toMillis();

            if (body.length > scrapingProperties.maxPayloadBytes()) {
                return new ScrapeOutcome(
                        persistedSourceUrl,
                        ScrapeStatus.FAILED,
                        response.statusCode(),
                        fetchedAt,
                        durationMs,
                        hashingService.sha256(sourceUri + "|payload-too-large|" + fetchedAt),
                        contentType,
                        (long) body.length,
                        headersJson(response),
                        null,
                        null,
                        "Payload length " + body.length + " exceeds configured maximum "
                                + scrapingProperties.maxPayloadBytes() + " bytes."
                );
            }

            boolean binaryPayload = SnapshotPayloads.shouldStoreAsBase64(payloadUri, contentType);
            Charset charset = charsetFrom(contentType);
            String rawPayload = binaryPayload
                    ? SnapshotPayloads.encodeBinary(body)
                    : new String(body, charset);
            String extractedText = binaryPayload
                    ? null
                    : Jsoup.parse(rawPayload, payloadUri.toString()).text();
            ScrapeStatus status = response.statusCode() >= 200 && response.statusCode() < 300
                    ? ScrapeStatus.SUCCESS
                    : ScrapeStatus.FAILED;

            return new ScrapeOutcome(
                    persistedSourceUrl,
                    status,
                    response.statusCode(),
                    fetchedAt,
                    durationMs,
                    hashingService.sha256(body),
                    contentType,
                    (long) body.length,
                    headersJson(response),
                    rawPayload,
                    extractedText,
                    status == ScrapeStatus.SUCCESS ? null : "Source returned HTTP " + response.statusCode() + "."
            );
        } catch (IOException exception) {
            return failure(persistedSourceUrl, ScrapeStatus.FAILED, startedAt, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(persistedSourceUrl, ScrapeStatus.FAILED, startedAt, "Scrape request was interrupted.");
        }
    }

    private HttpResponse<byte[]> sendRequest(SourceTarget sourceTarget, URI sourceUri) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(sourceUri)
                .timeout(Duration.ofMillis(sourceTarget.getTimeoutMs()))
                .header("User-Agent", sourceTarget.getUserAgent())
                .header("Accept", "application/json,text/csv,text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.8")
                .GET();
        applyProviderHeaders(requestBuilder, sourceUri);
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private ScrapeOutcome failure(String sourceUrl, ScrapeStatus status, OffsetDateTime startedAt, String message) {
        OffsetDateTime failedAt = OffsetDateTime.now(clock);
        return new ScrapeOutcome(
                sourceUrl,
                status,
                null,
                failedAt,
                Duration.between(startedAt, failedAt).toMillis(),
                hashingService.sha256(sourceUrl + "|" + status + "|" + failedAt + "|" + message),
                null,
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

    private Charset charsetFrom(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (!matcher.find()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(matcher.group(1).trim().replace("\"", "").toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            return StandardCharsets.UTF_8;
        }
    }

    private void applyProviderHeaders(HttpRequest.Builder requestBuilder, URI sourceUri) {
        if (!isApiFootballRequest(sourceUri)) {
            return;
        }
        if (StringUtils.hasText(apiFootballProperties.apiKey())) {
            requestBuilder.header("x-apisports-key", apiFootballProperties.apiKey().trim());
        }
    }

    private boolean isApiFootballRequest(URI sourceUri) {
        String baseUrl = apiFootballProperties.baseUrl();
        if (!StringUtils.hasText(baseUrl) || sourceUri == null || sourceUri.getHost() == null) {
            return false;
        }
        try {
            URI baseUri = URI.create(baseUrl.trim());
            return baseUri.getHost() != null && baseUri.getHost().equalsIgnoreCase(sourceUri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isSgoddsLeagueDownloadPageTarget(SourceTarget sourceTarget) {
        return selectorText(sourceTarget, "downloadPageFormat")
                .filter("sgodds-league-download-page"::equalsIgnoreCase)
                .isPresent();
    }

    private Optional<URI> resolveSgoddsCsvDownloadUri(
            SourceTarget sourceTarget,
            URI pageUri,
            HttpResponse<byte[]> pageResponse
    ) {
        String leagueName = selectorText(sourceTarget, "leagueName").orElse(null);
        if (!StringUtils.hasText(leagueName)) {
            return Optional.empty();
        }
        String contentType = pageResponse.headers().firstValue("Content-Type").orElse(null);
        String html = new String(pageResponse.body() == null ? new byte[0] : pageResponse.body(), charsetFrom(contentType));
        Document document = Jsoup.parse(html, pageUri.toString());
        String leagueSlug = leagueName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");

        for (Element link : document.select("a[href$=.csv]")) {
            Element row = link.closest("tr");
            String rowText = row == null ? link.text() : row.text();
            String href = link.attr("href");
            if (containsIgnoreCase(rowText, leagueName) || href.toLowerCase(Locale.ROOT).contains(leagueSlug)) {
                String absoluteUrl = link.absUrl("href");
                if (StringUtils.hasText(absoluteUrl)) {
                    return Optional.of(URI.create(absoluteUrl));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> selectorText(SourceTarget sourceTarget, String fieldName) {
        if (!StringUtils.hasText(sourceTarget.getSelectorsJson())) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(sourceTarget.getSelectorsJson());
            JsonNode value = root.path(fieldName);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) {
                return Optional.of(value.asText().trim());
            }
            return Optional.empty();
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private boolean containsIgnoreCase(String value, String expected) {
        return StringUtils.hasText(value)
                && StringUtils.hasText(expected)
                && value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private String redactSecrets(String sourceUrl) {
        if (sourceUrl == null) {
            return null;
        }
        return sourceUrl.replaceAll("(?i)(apiKey=)[^&]+", "$1REDACTED");
    }
}
