package com.recipetree.jeiexport112;

import java.io.IOException;

/** Validated, user-facing modpack identity published with an export. */
final class PackIdentity {
    static final int MAX_NAME_CODE_POINTS = 120;
    static final int MAX_VERSION_CODE_POINTS = 80;

    final String name;
    final String version;
    final String source;

    PackIdentity(String name, String version, String source) throws IOException {
        this.name = validatedText(name, "packName", MAX_NAME_CODE_POINTS);
        this.version = version == null ? null
                : validatedText(version, "packVersion", MAX_VERSION_CODE_POINTS);
        this.source = source;
    }

    static String validatedText(String value, String field, int maximumCodePoints)
            throws IOException {
        if (value == null) {
            throw new IOException(field + " must be a string");
        }
        validateCodePoints(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IOException(field + " must not be blank");
        }
        if (normalized.codePointCount(0, normalized.length()) > maximumCodePoints) {
            throw new IOException(field + " must contain at most " + maximumCodePoints
                    + " Unicode code points");
        }
        return normalized;
    }

    private static void validateCodePoints(String value, String field) throws IOException {
        for (int offset = 0; offset < value.length();) {
            char unit = value.charAt(offset);
            if (Character.isSurrogate(unit)) {
                if (!Character.isHighSurrogate(unit) || offset + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(offset + 1))) {
                    throw new IOException(field + " must not contain an unpaired Unicode surrogate");
                }
            }
            int codePoint = value.codePointAt(offset);
            if (Character.isISOControl(codePoint) || isUnsafeFormattingCodePoint(codePoint)) {
                throw new IOException(field
                        + " must not contain control, bidirectional, or zero-width formatting characters");
            }
            offset += Character.charCount(codePoint);
        }
    }

    private static boolean isUnsafeFormattingCodePoint(int codePoint) {
        return codePoint == 0x061c
                || (codePoint >= 0x200b && codePoint <= 0x200f)
                || (codePoint >= 0x202a && codePoint <= 0x202e)
                || (codePoint >= 0x2060 && codePoint <= 0x2069)
                || codePoint == 0xfeff;
    }
}
