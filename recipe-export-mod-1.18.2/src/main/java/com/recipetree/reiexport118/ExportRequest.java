package com.recipetree.reiexport118;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ExportRequest {
    static final String ACTIVE_NAME = "reiexport-request.json";
    static final Gson GSON = new Gson();
    static final int MAX_PACK_NAME_CODE_POINTS = 120;
    static final int MAX_PACK_VERSION_CODE_POINTS = 80;

    record Sample(String categoryId, int sourceIndex) {
    }

    record ItemSample(String typeId, String identifier) {
    }

    record ExpectedRegistryCensus(
            int entries,
            int displays,
            int categories,
            String categoryCountsSha256,
            String entryIdentitiesSha256) {
        boolean matches(RegistryCensus.Deep actual) {
            RegistryCensus.Counts counts = actual.counts();
            return entries == counts.entries()
                    && displays == counts.displays()
                    && categories == counts.categories()
                    && categoryCountsSha256.equals(counts.categoryCountsSha256())
                    && entryIdentitiesSha256.equals(actual.entryIdentitiesSha256());
        }
    }

    final String profile;
    final String packName;
    final String packVersion;
    final String output;
    final String worldName;
    final boolean createWorld;
    final boolean exitOnComplete;
    final boolean failOnError;
    final int iconScale;
    final int recipeScale;
    final int tickBudgetMs;
    final int pngThreads;
    final int pngQueueCapacity;
    final List<Sample> qualitySample;
    final List<ItemSample> qualityItemSample;
    final ExpectedRegistryCensus expectedRegistryCensus;

    private ExportRequest(
            String profile,
            String packName,
            String packVersion,
            String output,
            String worldName,
            boolean createWorld,
            boolean exitOnComplete,
            boolean failOnError,
            int iconScale,
            int recipeScale,
            int tickBudgetMs,
            int pngThreads,
            int pngQueueCapacity,
            List<Sample> qualitySample,
            List<ItemSample> qualityItemSample,
            ExpectedRegistryCensus expectedRegistryCensus) {
        this.profile = profile;
        this.packName = packName;
        this.packVersion = packVersion;
        this.output = output;
        this.worldName = worldName;
        this.createWorld = createWorld;
        this.exitOnComplete = exitOnComplete;
        this.failOnError = failOnError;
        this.iconScale = iconScale;
        this.recipeScale = recipeScale;
        this.tickBudgetMs = tickBudgetMs;
        this.pngThreads = pngThreads;
        this.pngQueueCapacity = pngQueueCapacity;
        this.qualitySample = List.copyOf(qualitySample);
        this.qualityItemSample = List.copyOf(qualityItemSample);
        this.expectedRegistryCensus = expectedRegistryCensus;
    }

    boolean isQualitySample() {
        return !qualitySample.isEmpty() || !qualityItemSample.isEmpty();
    }

    static ExportRequest read(Path path) throws IOException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new IOException("Exporter request is not valid JSON: " + path, exception);
        }
        if (!parsed.isJsonObject()) {
            throw new IOException("Exporter request root must be a JSON object: " + path);
        }
        JsonObject object = parsed.getAsJsonObject();
        rejectUnknownKeys(object, Set.of(
                "profile", "packName", "packVersion", "output", "worldName", "createWorld",
                "exitOnComplete", "failOnError", "iconScale", "recipeScale", "tickBudgetMs",
                "pngThreads", "pngQueueCapacity", "qualitySample", "qualityItemSample",
                "expectedRegistryCensus"));

        String profile = requiredString(object, "profile");
        if (!"multiblock-madness-2-1.18.2".equals(profile)) {
            throw new IOException("This exporter requires profile multiblock-madness-2-1.18.2; received " + profile);
        }
        String packName = requiredPackText(
                object, "packName", MAX_PACK_NAME_CODE_POINTS);
        String packVersion = requiredPackText(
                object, "packVersion", MAX_PACK_VERSION_CODE_POINTS);
        String output = requiredString(object, "output");
        Path outputPath = Path.of(output);
        if (outputPath.isAbsolute() || outputPath.normalize().startsWith("..") || outputPath.normalize().toString().isBlank()) {
            throw new IOException("output must be a non-empty relative path contained by the game directory");
        }

        String worldName = optionalString(object, "worldName", "reiexport-world");
        if (!worldName.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IOException("worldName must match [A-Za-z0-9._-]{1,64}");
        }

        int iconScale = rangedInt(object, "iconScale", 1, 1, 4);
        int recipeScale = rangedInt(object, "recipeScale", 2, 1, 4);
        int tickBudgetMs = rangedInt(object, "tickBudgetMs", 40, 5, 100);
        int pngThreads = rangedInt(object, "pngThreads", 2, 1, 4);
        int pngQueueCapacity = rangedInt(object, "pngQueueCapacity", 32, 8, 128);
        boolean failOnError = optionalBoolean(object, "failOnError", true);
        if (!failOnError) {
            throw new IOException("profile multiblock-madness-2-1.18.2 requires failOnError=true");
        }
        boolean createWorld = optionalBoolean(object, "createWorld", true);
        if (!createWorld) {
            throw new IOException(
                    "profile multiblock-madness-2-1.18.2 requires createWorld=true because "
                            + "manual world-entry has no owned native logout protocol");
        }

        List<Sample> sample = new ArrayList<>();
        Set<String> selectors = new HashSet<>();
        if (object.has("qualitySample")) {
            JsonElement element = object.get("qualitySample");
            if (!element.isJsonArray()) {
                throw new IOException("qualitySample must be an array");
            }
            JsonArray array = element.getAsJsonArray();
            if (array.size() > 32) {
                throw new IOException("qualitySample supports at most 32 representative displays");
            }
            for (int index = 0; index < array.size(); index++) {
                JsonElement selectorElement = array.get(index);
                if (!selectorElement.isJsonObject()) {
                    throw new IOException("qualitySample[" + index + "] must be an object");
                }
                JsonObject selector = selectorElement.getAsJsonObject();
                rejectUnknownKeys(selector, Set.of("categoryId", "sourceIndex"));
                String categoryId = requiredString(selector, "categoryId");
                try {
                    new ResourceLocation(categoryId);
                } catch (RuntimeException exception) {
                    throw new IOException("qualitySample[" + index + "].categoryId is invalid: " + categoryId, exception);
                }
                int sourceIndex = rangedInt(selector, "sourceIndex", -1, 0, Integer.MAX_VALUE);
                String selectorKey = categoryId + "\0" + sourceIndex;
                if (!selectors.add(selectorKey)) {
                    throw new IOException("qualitySample repeats selector " + categoryId + " #" + sourceIndex);
                }
                sample.add(new Sample(categoryId, sourceIndex));
            }
        }

        List<ItemSample> itemSample = new ArrayList<>();
        Set<String> itemSelectors = new HashSet<>();
        if (object.has("qualityItemSample")) {
            JsonElement element = object.get("qualityItemSample");
            if (!element.isJsonArray()) {
                throw new IOException("qualityItemSample must be an array");
            }
            JsonArray array = element.getAsJsonArray();
            if (array.size() > 32) {
                throw new IOException("qualityItemSample supports at most 32 representative entries");
            }
            for (int index = 0; index < array.size(); index++) {
                JsonElement selectorElement = array.get(index);
                if (!selectorElement.isJsonObject()) {
                    throw new IOException("qualityItemSample[" + index + "] must be an object");
                }
                JsonObject selector = selectorElement.getAsJsonObject();
                rejectUnknownKeys(selector, Set.of("typeId", "identifier"));
                String typeId = requiredResourceLocation(
                        selector, "typeId", "qualityItemSample[" + index + "]");
                String identifier = requiredResourceLocation(
                        selector, "identifier", "qualityItemSample[" + index + "]");
                String selectorKey = typeId + "\0" + identifier;
                if (!itemSelectors.add(selectorKey)) {
                    throw new IOException("qualityItemSample repeats selector "
                            + typeId + " " + identifier);
                }
                itemSample.add(new ItemSample(typeId, identifier));
            }
        }

        ExpectedRegistryCensus expectedRegistryCensus = expectedRegistryCensus(object);

        return new ExportRequest(
                profile,
                packName,
                packVersion,
                outputPath.normalize().toString(),
                worldName,
                createWorld,
                optionalBoolean(object, "exitOnComplete", true),
                failOnError,
                iconScale,
                recipeScale,
                tickBudgetMs,
                pngThreads,
                pngQueueCapacity,
                sample,
                itemSample,
                expectedRegistryCensus);
    }

    void requireExpectedRegistryCensus(RegistryCensus.Deep actual) {
        if (expectedRegistryCensus != null && !expectedRegistryCensus.matches(actual)) {
            throw new IllegalStateException("REI registry census does not match the exact request contract: expected="
                    + expectedRegistryCensus + " actual=" + actual.summary());
        }
    }

    private static ExpectedRegistryCensus expectedRegistryCensus(JsonObject request)
            throws IOException {
        if (!request.has("expectedRegistryCensus")) {
            return null;
        }
        JsonElement element = request.get("expectedRegistryCensus");
        if (!element.isJsonObject()) {
            throw new IOException("expectedRegistryCensus must be an object");
        }
        JsonObject census = element.getAsJsonObject();
        rejectUnknownKeys(census, Set.of(
                "entries", "displays", "categories", "categoryCountsSha256",
                "entryIdentitiesSha256"));
        return new ExpectedRegistryCensus(
                rangedInt(census, "entries", -1, 1, Integer.MAX_VALUE),
                rangedInt(census, "displays", -1, 1, Integer.MAX_VALUE),
                rangedInt(census, "categories", -1, 1, Integer.MAX_VALUE),
                requiredSha256(census, "categoryCountsSha256"),
                requiredSha256(census, "entryIdentitiesSha256"));
    }

    private static String requiredSha256(JsonObject object, String name) throws IOException {
        String value = requiredString(object, name);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IOException(name + " must be a lowercase 64-character SHA-256 digest");
        }
        return value;
    }

    private static String requiredResourceLocation(
            JsonObject object, String name, String description) throws IOException {
        String value = requiredString(object, name);
        try {
            new ResourceLocation(value);
        } catch (RuntimeException exception) {
            throw new IOException(description + "." + name + " is invalid: " + value, exception);
        }
        return value;
    }

    private static void rejectUnknownKeys(JsonObject object, Set<String> allowed) throws IOException {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new IOException("Unsupported exporter request field: " + key);
            }
        }
    }

    private static String requiredString(JsonObject object, String name) throws IOException {
        if (!object.has(name) || !object.get(name).isJsonPrimitive() || !object.getAsJsonPrimitive(name).isString()) {
            throw new IOException(name + " must be a string");
        }
        String value = object.get(name).getAsString().trim();
        if (value.isEmpty()) {
            throw new IOException(name + " must not be blank");
        }
        return value;
    }

    private static String optionalString(JsonObject object, String name, String fallback) throws IOException {
        return object.has(name) ? requiredString(object, name) : fallback;
    }

    private static String requiredPackText(JsonObject object, String name, int maximumCodePoints)
            throws IOException {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()
                || !object.getAsJsonPrimitive(name).isString()) {
            throw new IOException(name + " must be a string");
        }
        String rawValue = object.get(name).getAsString();
        validatePackCodePoints(rawValue, name);
        String value = rawValue.trim();
        if (value.isEmpty()) {
            throw new IOException(name + " must not be blank");
        }
        if (value.codePointCount(0, value.length()) > maximumCodePoints) {
            throw new IOException(name + " must contain at most " + maximumCodePoints + " Unicode code points");
        }
        return value;
    }

    private static void validatePackCodePoints(String value, String name) throws IOException {
        for (int offset = 0; offset < value.length();) {
            char unit = value.charAt(offset);
            if (Character.isSurrogate(unit)) {
                if (!Character.isHighSurrogate(unit) || offset + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(offset + 1))) {
                    throw new IOException(name + " must not contain an unpaired Unicode surrogate");
                }
            }
            int codePoint = value.codePointAt(offset);
            if (Character.isISOControl(codePoint) || isUnsafeFormattingCodePoint(codePoint)) {
                throw new IOException(name
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

    private static boolean optionalBoolean(JsonObject object, String name, boolean fallback) throws IOException {
        if (!object.has(name)) {
            return fallback;
        }
        JsonElement element = object.get(name);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IOException(name + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static int rangedInt(JsonObject object, String name, int fallback, int minimum, int maximum) throws IOException {
        if (!object.has(name)) {
            if (fallback < minimum) {
                throw new IOException(name + " is required");
            }
            return fallback;
        }
        JsonElement element = object.get(name);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IOException(name + " must be an integer");
        }
        int value;
        try {
            value = element.getAsInt();
            if (element.getAsDouble() != value) {
                throw new NumberFormatException("fractional");
            }
        } catch (RuntimeException exception) {
            throw new IOException(name + " must be an integer", exception);
        }
        if (value < minimum || value > maximum) {
            throw new IOException(name + " must be in [" + minimum + ", " + maximum + "]");
        }
        return value;
    }
}
