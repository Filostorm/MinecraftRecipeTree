package com.recipetree.neiexport1710;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class ExportRequest {
    static final String PACK_NAME = "GT New Horizons";
    static final String PACK_VERSION = "2.8.4";
    static final int MAX_PACK_NAME_CODE_POINTS = 120;
    static final int MAX_PACK_VERSION_CODE_POINTS = 80;
    static final int ICON_SCALE = 3;
    static final int RECIPE_SCALE = 2;
    static final int MOB_CANVAS = 256;

    final Path output;
    final int maxMillisPerTick;
    final int pngThreads;
    final int pngQueueCapacity;
    final int readinessTimeoutTicks;
    final int handlerStableTicks;
    final boolean bootstrapIntegratedWorld;
    final boolean exitOnComplete;
    Path runningMarker;

    private ExportRequest(Path output, int maxMillisPerTick, int pngThreads,
                          int pngQueueCapacity, int readinessTimeoutTicks,
                          int handlerStableTicks, boolean bootstrapIntegratedWorld,
                          boolean exitOnComplete) {
        this.output = output;
        this.maxMillisPerTick = maxMillisPerTick;
        this.pngThreads = pngThreads;
        this.pngQueueCapacity = pngQueueCapacity;
        this.readinessTimeoutTicks = readinessTimeoutTicks;
        this.handlerStableTicks = handlerStableTicks;
        this.bootstrapIntegratedWorld = bootstrapIntegratedWorld;
        this.exitOnComplete = exitOnComplete;
    }

    static ExportRequest fromFile(Path file, Minecraft minecraft) throws IOException {
        JsonElement parsed;
        try (Reader reader = Files.newBufferedReader(file)) {
            parsed = new JsonParser().parse(reader);
        }
        if (!parsed.isJsonObject()) {
            throw new IOException("Request root must be a JSON object");
        }
        return fromJson(parsed.getAsJsonObject(), minecraft.mcDataDir.toPath());
    }

    static ExportRequest fromJson(JsonObject json, Path gameDirectory) throws IOException {
        requirePackIdentity(json);
        int iconScale = integer(json, "iconScale", ICON_SCALE);
        int recipeScale = integer(json, "recipeScale", RECIPE_SCALE);
        int mobCanvas = integer(json, "mobCanvas", MOB_CANVAS);
        if (iconScale != ICON_SCALE || recipeScale != RECIPE_SCALE
                || mobCanvas != MOB_CANVAS) {
            throw new IOException(
                    "GTNH snapshot contract requires iconScale=3, recipeScale=2, and "
                            + "mobCanvas=256; got " + iconScale + "/" + recipeScale + "/"
                            + mobCanvas);
        }
        if (!bool(json, "bootstrapIntegratedWorld", false)) {
            throw new IOException("GTNH runtime export requires explicit "
                    + "bootstrapIntegratedWorld=true authorization");
        }

        Path normalizedGame = gameDirectory.toAbsolutePath().normalize();
        Path output = Paths.get(string(json, "output", "recipe-tree-gtnh-2.8.4"));
        if (!output.isAbsolute()) {
            output = normalizedGame.resolve(output);
        }
        output = output.toAbsolutePath().normalize();
        if (output.equals(normalizedGame)) {
            throw new IOException("Refusing to replace the Minecraft game directory with an export");
        }
        if (output.getParent() == null || output.getFileName() == null) {
            throw new IOException("Output must have a parent and file name: " + output);
        }

        return new ExportRequest(
                output,
                bounded(json, "maxMillisPerTick", 30, 1, 250),
                bounded(json, "pngThreads", 2, 1, 4),
                bounded(json, "pngQueueCapacity", 128, 8, 4096),
                bounded(json, "readinessTimeoutTicks", 12000, 200, 144000),
                bounded(json, "handlerStableTicks", 100, 20, 1200),
                true,
                bool(json, "exitOnComplete", false));
    }

    private static void requirePackIdentity(JsonObject json) throws IOException {
        JsonElement packElement = json.get("pack");
        if (packElement == null || !packElement.isJsonObject()) {
            throw new IOException("Request must explicitly pin pack{name,version}");
        }
        JsonObject pack = packElement.getAsJsonObject();
        String name = validatedPackIdentityText(
                string(pack, "name", null), "pack.name", MAX_PACK_NAME_CODE_POINTS);
        String version = validatedPackIdentityText(
                string(pack, "version", null), "pack.version", MAX_PACK_VERSION_CODE_POINTS);
        if (!PACK_NAME.equals(name) || !PACK_VERSION.equals(version)) {
            throw new IOException("This release only exports " + PACK_NAME + " " + PACK_VERSION
                    + "; request identified " + name + " " + version);
        }
    }

    static String validatedPackIdentityText(String value, String field, int maximumCodePoints)
            throws IOException {
        if (value.codePointCount(0, value.length()) > maximumCodePoints) {
            throw new IOException(field + " must contain at most " + maximumCodePoints
                    + " Unicode code points");
        }
        for (int offset = 0; offset < value.length();) {
            char unit = value.charAt(offset);
            if (Character.isSurrogate(unit)
                    && (!Character.isHighSurrogate(unit)
                    || offset + 1 >= value.length()
                    || !Character.isLowSurrogate(value.charAt(offset + 1)))) {
                throw new IOException(field + " must not contain an unpaired Unicode surrogate");
            }
            int codePoint = value.codePointAt(offset);
            if (isUnsafePackIdentityCodePoint(codePoint)) {
                throw new IOException(field
                        + " must not contain control, bidirectional, or zero-width formatting characters");
            }
            offset += Character.charCount(codePoint);
        }
        return value;
    }

    private static boolean isUnsafePackIdentityCodePoint(int codePoint) {
        return (codePoint >= 0x0000 && codePoint <= 0x001f)
                || (codePoint >= 0x007f && codePoint <= 0x009f)
                || codePoint == 0x061c
                || (codePoint >= 0x200b && codePoint <= 0x200f)
                || (codePoint >= 0x202a && codePoint <= 0x202e)
                || (codePoint >= 0x2060 && codePoint <= 0x2069)
                || codePoint == 0xfeff;
    }

    private static String string(JsonObject json, String name, String defaultValue) throws IOException {
        JsonElement element = json.get(name);
        if (element == null || element.isJsonNull()) {
            if (defaultValue == null) {
                throw new IOException(name + " is required and must be a string");
            }
            return defaultValue;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IOException(name + " must be a string");
        }
        return element.getAsString();
    }

    private static boolean bool(JsonObject json, String name, boolean defaultValue) throws IOException {
        JsonElement element = json.get(name);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IOException(name + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static int integer(JsonObject json, String name, int defaultValue) throws IOException {
        JsonElement element = json.get(name);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException error) {
            throw new IOException(name + " must be an integer", error);
        }
    }

    private static int bounded(JsonObject json, String name, int defaultValue, int minimum, int maximum)
            throws IOException {
        int value = integer(json, name, defaultValue);
        if (value < minimum || value > maximum) {
            throw new IOException(name + " must be in [" + minimum + ", " + maximum + "]");
        }
        return value;
    }
}
