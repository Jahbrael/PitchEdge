package com.betai.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/artwork")
public class ArtworkProxyController {

    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "r2.thesportsdb.com",
            "www.thesportsdb.com",
            "thesportsdb.com"
    );

    private final HttpClient httpClient;

    public ArtworkProxyController(@Qualifier("theSportsDbHttpClient") HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @GetMapping("/proxy")
    public ResponseEntity<byte[]> proxy(@RequestParam String url) throws IOException, InterruptedException {
        URI uri = parseAllowedUri(url);
        if (uri == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        byte[] body = response.body() == null ? new byte[0] : response.body();
        if (body.length == 0 || body.length > MAX_IMAGE_BYTES) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        MediaType mediaType = response.headers()
                .firstValue(HttpHeaders.CONTENT_TYPE)
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith("image/"))
                .map(MediaType::parseMediaType)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .contentType(mediaType)
                .body(body);
    }

    private URI parseAllowedUri(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            if (!ALLOWED_HOSTS.contains(host)) {
                return null;
            }
            if (!path.startsWith("/images/")) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
