package com.betai.scraping;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

@Component
public class HashingService {

    public String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    public String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this JVM.", exception);
        }
    }

    public String aggregate(Collection<String> checksums) {
        if (checksums.isEmpty()) {
            return null;
        }
        return sha256(checksums.stream().sorted().reduce("", (left, right) -> left + right));
    }
}
