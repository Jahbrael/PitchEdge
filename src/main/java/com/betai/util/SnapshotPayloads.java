package com.betai.util;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public final class SnapshotPayloads {

    public static final String BASE64_PREFIX = "base64:";

    private SnapshotPayloads() {
    }

    public static boolean shouldStoreAsBase64(URI uri, String contentType) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String path = uri == null || uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".csv")) {
            return false;
        }
        return path.endsWith(".xlsx")
                || normalizedContentType.contains("spreadsheetml")
                || normalizedContentType.contains("application/vnd.ms-excel")
                || normalizedContentType.contains("application/octet-stream");
    }

    public static String encodeBinary(byte[] bytes) {
        return BASE64_PREFIX + Base64.getEncoder().encodeToString(bytes == null ? new byte[0] : bytes);
    }

    public static boolean isBinary(String payload) {
        return payload != null && payload.startsWith(BASE64_PREFIX);
    }

    public static byte[] bytes(String payload) {
        if (!StringUtils.hasText(payload)) {
            return new byte[0];
        }
        if (isBinary(payload)) {
            return Base64.getDecoder().decode(payload.substring(BASE64_PREFIX.length()));
        }
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    public static String text(String payload) {
        if (!StringUtils.hasText(payload)) {
            return "";
        }
        if (isBinary(payload)) {
            throw new IllegalArgumentException("Snapshot payload is binary and cannot be read as text.");
        }
        return payload;
    }
}
