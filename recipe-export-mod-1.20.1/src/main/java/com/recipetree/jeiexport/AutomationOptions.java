package com.recipetree.jeiexport;

import java.util.regex.Pattern;

final class AutomationOptions {
    private static final String DEFAULT_WORLD_FOLDER = "RecipeTree-Exporter-Automation";
    private static final String DEFAULT_WORLD_NAME = "Recipe Tree Export";
    private static final Pattern PORTABLE_FOLDER =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private AutomationOptions() {
    }

    static boolean createWorldEnabled() {
        return strictBoolean("jeiexport.createWorld", System.getProperty("jeiexport.createWorld"));
    }

    static boolean exitOnCompleteEnabled() {
        return strictBoolean("jeiexport.exitOnComplete", System.getProperty("jeiexport.exitOnComplete"));
    }

    static String worldFolder() {
        String value = System.getProperty("jeiexport.worldFolder", DEFAULT_WORLD_FOLDER);
        if (".".equals(value) || "..".equals(value) || !PORTABLE_FOLDER.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "-Djeiexport.worldFolder must be one portable folder name (letters, digits, '.', '_', or '-')");
        }
        return value;
    }

    static String worldName() {
        String value = System.getProperty("jeiexport.worldName", DEFAULT_WORLD_NAME);
        int length = value.codePointCount(0, value.length());
        if (value.isBlank() || length > 80 || value.codePoints().anyMatch(AutomationOptions::isUnsafeNameCharacter)) {
            throw new IllegalArgumentException(
                    "-Djeiexport.worldName must be 1-80 visible characters without control or format characters");
        }
        return value;
    }

    private static boolean strictBoolean(String property, String value) {
        if (value == null || "false".equals(value)) {
            return false;
        }
        if ("true".equals(value)) {
            return true;
        }
        throw new IllegalArgumentException("-D" + property + " must be exactly true or false");
    }

    private static boolean isUnsafeNameCharacter(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint) || type == Character.FORMAT;
    }
}
