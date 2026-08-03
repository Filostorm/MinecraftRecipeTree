package com.recipetree.neiexport1710;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class Naming {
    private Naming() {
    }

    static String plainText(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\u00a7') {
                if (index + 1 < value.length()) {
                    index++;
                }
            } else if (current >= 0x20 || current == '\t') {
                result.append(current);
            }
        }
        return result.toString();
    }

    static String sanitize(String value) {
        String lower = value == null ? "unknown" : value.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(Math.min(120, lower.length()));
        boolean separator = false;
        for (int index = 0; index < lower.length() && result.length() < 120; index++) {
            char current = lower.charAt(index);
            if ((current >= 'a' && current <= 'z') || (current >= '0' && current <= '9')
                    || current == '-' || current == '.') {
                result.append(current);
                separator = false;
            } else if (!separator) {
                result.append('_');
                separator = true;
            }
        }
        while (result.length() > 0 && result.charAt(result.length() - 1) == '_') {
            result.setLength(result.length() - 1);
        }
        return result.length() == 0 ? "unknown" : result.toString();
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte part : digest) {
                result.append(String.format(Locale.ROOT, "%02x", part & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("The JVM does not provide SHA-256", impossible);
        }
    }
}
