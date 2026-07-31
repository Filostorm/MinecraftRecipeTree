package com.recipetree.reiexport118;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

final class Naming {
    private Naming() {
    }

    static String sanitize(String value) {
        String sanitized = value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._/-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[_./-]+|[_./-]+$", "");
        return sanitized.isEmpty() ? "unnamed" : sanitized;
    }

    static String hash128(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(Arrays.copyOf(digest, 16));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The JVM does not provide SHA-256.", exception);
        }
    }
}
