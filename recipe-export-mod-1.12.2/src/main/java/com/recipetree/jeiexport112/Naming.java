package com.recipetree.jeiexport112;

import net.minecraft.util.text.TextFormatting;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class Naming {
    private Naming() {
    }

    static String sanitize(String value) {
        String lower = value == null ? "unknown" : value.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(Math.min(lower.length(), 120));
        boolean underscore = false;
        for (int i = 0; i < lower.length() && result.length() < 120; i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '.') {
                result.append(c);
                underscore = false;
            } else if (!underscore) {
                result.append('_');
                underscore = true;
            }
        }
        while (result.length() > 0 && result.charAt(result.length() - 1) == '_') {
            result.setLength(result.length() - 1);
        }
        return result.length() == 0 ? "unknown" : result.toString();
    }

    /** Removes legacy section-sign formatting from user-facing labels without altering identity strings. */
    static String plainText(String value) {
        if (value == null) {
            return null;
        }
        String vanillaPlain = TextFormatting.getTextWithoutFormattingCodes(value);
        int firstResidualMarker = vanillaPlain.indexOf('\u00a7');
        if (firstResidualMarker < 0) {
            return vanillaPlain;
        }

        // BuildCraft 8 prefixes many colored names with its non-vanilla "\u00a7z" marker.
        // Minecraft's helper intentionally leaves unknown codes intact, so remove any residual
        // marker/control pair at this cosmetic boundary. A dangling marker is removed as well.
        StringBuilder result = new StringBuilder(vanillaPlain.length());
        result.append(vanillaPlain, 0, firstResidualMarker);
        for (int i = firstResidualMarker; i < vanillaPlain.length(); i++) {
            char current = vanillaPlain.charAt(i);
            if (current == '\u00a7') {
                if (i + 1 < vanillaPlain.length()) {
                    i++;
                }
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    static String hash8(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                result.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM is missing SHA-256", e);
        }
    }
}
