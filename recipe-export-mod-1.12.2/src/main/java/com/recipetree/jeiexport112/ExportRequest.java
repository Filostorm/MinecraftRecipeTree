package com.recipetree.jeiexport112;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class ExportRequest {
    static final long MAX_REQUEST_BYTES = 256L * 1024L;
    private static final int MAX_WORLD_FOLDER_CODE_POINTS = 64;
    private static final int MAX_WORLD_NAME_CODE_POINTS = 120;
    private static final int MAX_OUTPUT_CODE_POINTS = 4096;
    private static final Set<String> ALLOWED_KEYS = new HashSet<String>(Arrays.asList(
            "packName", "packVersion", "output", "iconScale", "recipeScale",
            "maxMillisPerTick", "pngThreads", "pngQueueCapacity", "exitOnComplete",
            "requireWorld", "createWorld", "waitAfterWorldTicks", "worldTimeoutTicks",
            "worldFolder", "worldName", "qualitySample"));

    final PackIdentity pack;
    final Path output;
    final int iconScale;
    final int recipeScale;
    final int maxMillisPerTick;
    final int pngThreads;
    final int pngQueueCapacity;
    final boolean exitOnComplete;
    final boolean requireWorld;
    final boolean createWorld;
    final int waitAfterWorldTicks;
    final int worldTimeoutTicks;
    final String worldFolder;
    final String worldName;
    final QualitySamplePlan qualitySample;
    Path runningMarker;

    private ExportRequest(PackIdentity pack, Path output, int iconScale, int recipeScale,
                          int maxMillisPerTick,
                          int pngThreads, int pngQueueCapacity, boolean exitOnComplete,
                          boolean requireWorld, boolean createWorld, int waitAfterWorldTicks,
                          int worldTimeoutTicks, String worldFolder, String worldName,
                          QualitySamplePlan qualitySample) {
        this.pack = pack;
        this.output = output;
        this.iconScale = iconScale;
        this.recipeScale = recipeScale;
        this.maxMillisPerTick = maxMillisPerTick;
        this.pngThreads = pngThreads;
        this.pngQueueCapacity = pngQueueCapacity;
        this.exitOnComplete = exitOnComplete;
        this.requireWorld = requireWorld || createWorld;
        this.createWorld = createWorld;
        this.waitAfterWorldTicks = waitAfterWorldTicks;
        this.worldTimeoutTicks = worldTimeoutTicks;
        this.worldFolder = worldFolder;
        this.worldName = worldName;
        this.qualitySample = qualitySample;
    }

    static ExportRequest fromFile(Path file, Minecraft minecraft) throws IOException {
        JsonElement parsed = new JsonParser().parse(readBoundedUtf8(file));
        if (!parsed.isJsonObject()) {
            throw new IOException("Request root must be a JSON object");
        }
        ExportRequest request = fromJson(parsed.getAsJsonObject(),
                minecraft.gameDir.toPath().toAbsolutePath().normalize());
        request.logPackIdentity("request file");
        return request;
    }

    static ExportRequest fromCommand(String outputArg, Minecraft minecraft) throws IOException {
        JsonObject json = new JsonObject();
        if (outputArg != null && !outputArg.trim().isEmpty()) {
            json.addProperty("output", outputArg.trim());
        }
        ExportRequest request = fromJson(json,
                minecraft.gameDir.toPath().toAbsolutePath().normalize());
        request.logPackIdentity("client command");
        return request;
    }

    static ExportRequest fromSystemProperties(Minecraft minecraft) throws IOException {
        JsonObject json = new JsonObject();
        putProperty(json, "packName", "jeiexport.packName");
        putProperty(json, "packVersion", "jeiexport.packVersion");
        putProperty(json, "output", "jeiexport.output");
        putIntegerProperty(json, "iconScale", "jeiexport.iconScale");
        putIntegerProperty(json, "recipeScale", "jeiexport.recipeScale");
        putIntegerProperty(json, "maxMillisPerTick", "jeiexport.maxMillisPerTick");
        putIntegerProperty(json, "pngThreads", "jeiexport.pngThreads");
        putIntegerProperty(json, "pngQueueCapacity", "jeiexport.pngQueueCapacity");
        putBooleanProperty(json, "exitOnComplete", "jeiexport.exitOnComplete");
        putBooleanProperty(json, "requireWorld", "jeiexport.requireWorld");
        putBooleanProperty(json, "createWorld", "jeiexport.createWorld");
        putIntegerProperty(json, "waitAfterWorldTicks", "jeiexport.waitAfterWorldTicks");
        putIntegerProperty(json, "worldTimeoutTicks", "jeiexport.worldTimeoutTicks");
        putProperty(json, "worldFolder", "jeiexport.worldFolder");
        putProperty(json, "worldName", "jeiexport.worldName");
        ExportRequest request = fromJson(json,
                minecraft.gameDir.toPath().toAbsolutePath().normalize());
        request.logPackIdentity("JVM one-shot properties");
        return request;
    }

    private static void putProperty(JsonObject json, String jsonName, String propertyName) {
        String value = System.getProperty(propertyName);
        if (value != null) {
            json.addProperty(jsonName, value);
        }
    }

    private static void putIntegerProperty(JsonObject json, String jsonName, String propertyName) throws IOException {
        String value = System.getProperty(propertyName);
        if (value != null) {
            try {
                json.addProperty(jsonName, Integer.parseInt(value));
            } catch (NumberFormatException e) {
                throw new IOException("Invalid integer system property " + propertyName + "=" + value, e);
            }
        }
    }

    private static void putBooleanProperty(JsonObject json, String jsonName, String propertyName)
            throws IOException {
        String value = System.getProperty(propertyName);
        if (value != null) {
            try {
                json.addProperty(jsonName, StrictBooleanProperty.parse(propertyName, value));
            } catch (IllegalStateException invalid) {
                throw new IOException(invalid.getMessage(), invalid);
            }
        }
    }

    static ExportRequest fromJson(JsonObject json, Path gameDirectory) throws IOException {
        rejectUnknownKeys(json);
        gameDirectory = gameDirectory.toAbsolutePath().normalize();
        String packName = optionalPackString(json, "packName");
        String packVersion = optionalPackString(json, "packVersion");
        PackIdentity pack = PackIdentityResolver.resolve(gameDirectory, packName, packVersion);
        String outputText = PackIdentity.validatedText(
                string(json, "output", "jei-exports"), "output", MAX_OUTPUT_CODE_POINTS);
        final Path outputPath;
        try {
            outputPath = Paths.get(outputText);
        } catch (InvalidPathException invalidPath) {
            throw new IOException("output is not a valid filesystem path: " + invalidPath.getReason(),
                    invalidPath);
        }
        Path output = outputPath;
        if (!output.isAbsolute()) {
            output = gameDirectory.resolve(output);
        }
        output = OutputPathPolicy.validate(gameDirectory, output);

        int iconScale = boundedInteger(json, "iconScale", 4, 1, 8);
        int recipeScale = boundedInteger(json, "recipeScale", 2, 1, 4);
        int maxMillis = boundedInteger(json, "maxMillisPerTick", 30, 1, 250);
        int pngThreads = boundedInteger(json, "pngThreads", 2, 1, 4);
        int pngQueue = boundedInteger(json, "pngQueueCapacity", 128, 8, 4096);
        int waitAfterWorld = boundedInteger(json, "waitAfterWorldTicks", 200, 0, 72000);
        int worldTimeout = boundedInteger(json, "worldTimeoutTicks", 12000, 200, 144000);
        boolean exit = bool(json, "exitOnComplete", false);
        boolean requireWorld = bool(json, "requireWorld", false);
        boolean createWorld = bool(json, "createWorld", false);
        String worldFolder = PackIdentity.validatedText(
                string(json, "worldFolder", "JEI-Export-Automation"),
                "worldFolder", MAX_WORLD_FOLDER_CODE_POINTS);
        String worldName = PackIdentity.validatedText(
                string(json, "worldName", "JEI Export Automation"),
                "worldName", MAX_WORLD_NAME_CODE_POINTS);
        QualitySamplePlan qualitySample = QualitySamplePlan.parse(json.get("qualitySample"));
        if (".".equals(worldFolder) || "..".equals(worldFolder)
                || containsPortablePathMetacharacter(worldFolder)
                || isWindowsReservedFileName(worldFolder)
                || worldFolder.endsWith(".") || worldFolder.endsWith(" ")) {
            throw new IOException(
                    "worldFolder must be a portable save-folder name without path metacharacters, " +
                            "a trailing dot, or a trailing space"
            );
        }
        return new ExportRequest(pack, output, iconScale, recipeScale, maxMillis, pngThreads, pngQueue,
                exit, requireWorld, createWorld, waitAfterWorld, worldTimeout, worldFolder, worldName,
                qualitySample);
    }

    static String readBoundedUtf8(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IOException("Request must be a regular, non-symbolic-link file: " + file);
        }
        if (attributes.size() > MAX_REQUEST_BYTES) {
            throw new IOException("Request exceeds the " + MAX_REQUEST_BYTES + "-byte limit: " + file);
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) attributes.size());
        byte[] buffer = new byte[8192];
        long total = 0L;
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_REQUEST_BYTES) {
                    throw new IOException(
                            "Request grew beyond the " + MAX_REQUEST_BYTES +
                                    "-byte limit while being read: " + file
                    );
                }
                bytes.write(buffer, 0, read);
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString();
        } catch (CharacterCodingException invalidUtf8) {
            throw new IOException("Request is not valid UTF-8: " + file, invalidUtf8);
        }
    }

    private static boolean containsPortablePathMetacharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            switch (value.charAt(index)) {
                case '<':
                case '>':
                case ':':
                case '"':
                case '/':
                case '\\':
                case '|':
                case '?':
                case '*':
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    private static boolean isWindowsReservedFileName(String value) {
        String uppercase = value.toUpperCase(java.util.Locale.ROOT);
        int dot = uppercase.indexOf('.');
        String base = dot < 0 ? uppercase : uppercase.substring(0, dot);
        if ("CON".equals(base) || "PRN".equals(base) || "AUX".equals(base)
                || "NUL".equals(base)) {
            return true;
        }
        if (base.length() == 4) {
            char suffix = base.charAt(3);
            return suffix >= '1' && suffix <= '9'
                    && (base.startsWith("COM") || base.startsWith("LPT"));
        }
        return false;
    }

    private void logPackIdentity(String requestSource) {
        if ("game-directory".equals(pack.source)) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] No explicit or supported launcher metadata supplied a modpack identity for {}. "
                            + "Using game-directory name '{}' with no pack version; set packName/packVersion "
                            + "in jeiexport-request.json or jeiexport.packName/jeiexport.packVersion JVM properties.",
                    requestSource, pack.name);
        } else {
            JeiExportMod.LOGGER.info(
                    "[jeiexport] Resolved modpack identity for {} from {}: name='{}', version={}",
                    requestSource, pack.source, pack.name,
                    pack.version == null ? "(not supplied)" : "'" + pack.version + "'");
        }
    }

    static void rejectUnknownKeys(JsonObject json) throws IOException {
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IOException("Unsupported exporter request field: " + key);
            }
        }
    }

    private static String optionalPackString(JsonObject json, String name) throws IOException {
        if (!json.has(name)) {
            return null;
        }
        JsonElement value = json.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IOException(name + " must be a string when supplied");
        }
        return value.getAsString();
    }

    private static String string(JsonObject json, String name, String defaultValue) throws IOException {
        JsonElement value = json.get(name);
        if (value == null || value.isJsonNull()) {
            return defaultValue;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException(name + " must be a string");
        }
        return value.getAsString();
    }

    private static boolean bool(JsonObject json, String name, boolean defaultValue) throws IOException {
        JsonElement value = json.get(name);
        if (value == null || value.isJsonNull()) {
            return defaultValue;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IOException(name + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static int boundedInteger(JsonObject json, String name, int defaultValue, int min, int max)
            throws IOException {
        JsonElement value = json.get(name);
        if (value == null || value.isJsonNull()) {
            return defaultValue;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException(name + " must be an integer");
        }
        final int result;
        try {
            result = new BigDecimal(value.getAsString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IOException(name + " must be an integer", e);
        }
        if (result < min || result > max) {
            throw new IOException(name + " must be in [" + min + ", " + max + "]");
        }
        return result;
    }
}
