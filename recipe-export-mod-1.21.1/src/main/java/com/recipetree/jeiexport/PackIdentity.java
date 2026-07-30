package com.recipetree.jeiexport;

import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable human-readable identity for the modpack that produced an export.
 *
 * <p>The validation lives on the value object so every identity written to a
 * manifest has the same bounds, regardless of which launcher supplied it.</p>
 */
public record PackIdentity(String name, @Nullable String version, String identitySource) {
    static final int MAX_NAME_CODE_POINTS = 120;
    static final int MAX_VERSION_CODE_POINTS = 80;
    private static final Pattern SOURCE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    public PackIdentity {
        name = normalizeAndValidate(name, "pack name", MAX_NAME_CODE_POINTS);
        if (version != null) {
            version = normalizeAndValidate(version, "pack version", MAX_VERSION_CODE_POINTS);
        }
        identitySource = Objects.requireNonNull(identitySource, "identitySource");
        if (!SOURCE_PATTERN.matcher(identitySource).matches()) {
            throw new IllegalArgumentException("identitySource must be a lowercase ASCII identifier");
        }
    }

    static String normalizeAndValidate(String value, String label, int maximumCodePoints) {
        Objects.requireNonNull(value, label);
        validateCodePoints(value, label);
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        if (normalized.isEmpty() || normalized.codePoints()
                .allMatch(codePoint -> Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint))) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints > maximumCodePoints) {
            throw new IllegalArgumentException(label + " exceeds " + maximumCodePoints + " Unicode characters");
        }
        validateCodePoints(normalized, label);
        return normalized;
    }

    private static void validateCodePoints(String value, String label) {
        value.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR
                    || type == Character.SURROGATE
                    || isUnsafeFormattingCodePoint(codePoint)) {
                throw new IllegalArgumentException(
                        label + " contains a control, bidirectional, or zero-width formatting character");
            }
        });
    }

    private static boolean isUnsafeFormattingCodePoint(int codePoint) {
        return codePoint == 0x061C
                || (codePoint >= 0x200B && codePoint <= 0x200F)
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2060 && codePoint <= 0x2069)
                || codePoint == 0xFEFF;
    }
}
