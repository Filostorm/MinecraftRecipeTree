package com.recipetree.reiexport118.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Resolves whether this MM2-only artifact was launched for its exact export identity. */
public final class Mm2ExportRequestScope {
    public static final String REQUEST_NAME = "reiexport-request.json";
    public static final String PROFILE = "multiblock-madness-2-1.18.2";
    public static final String PACK_NAME = "Multiblock Madness 2";
    private static final long MAX_REQUEST_BYTES = 1024L * 1024L;

    public enum State {
        ABSENT,
        EXACT_MM2
    }

    public record Inspection(State state, Path requestPath) {
        public Inspection {
            if (state == null || requestPath == null || !requestPath.isAbsolute()) {
                throw new IllegalArgumentException("MM2 request-scope inspection is incomplete");
            }
        }

        public boolean isExactMm2() {
            return state == State.EXACT_MM2;
        }
    }

    private Mm2ExportRequestScope() {
    }

    public static Inspection inspect(Path gameDirectory) {
        if (gameDirectory == null) {
            throw new IllegalStateException(
                    "Forge did not expose an authoritative game directory for MM2 request scoping");
        }
        Path normalizedGameDirectory = gameDirectory.toAbsolutePath().normalize();
        Path request = normalizedGameDirectory.resolve(REQUEST_NAME);
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(
                    request, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException exception) {
            return new Inspection(State.ABSENT, request);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not inspect MM2 exporter request scope at " + request, exception);
        }
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new IllegalStateException(
                    "MM2 exporter request must be a plain regular file: " + request);
        }
        if (attributes.size() > MAX_REQUEST_BYTES) {
            throw new IllegalStateException(
                    "MM2 exporter request exceeds the 1 MiB preflight limit: " + request);
        }

        JsonObject root;
        try {
            String source = Files.readString(request, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(source);
            if (!parsed.isJsonObject()) {
                throw new IllegalStateException(
                        "MM2 exporter request root must be a JSON object: " + request);
            }
            root = parsed.getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw new IllegalStateException(
                    "Could not parse MM2 exporter request identity at " + request, exception);
        }

        String profile = requiredIdentityString(root, "profile", request);
        String packName = requiredIdentityString(root, "packName", request);
        if (!PROFILE.equals(profile) || !PACK_NAME.equals(packName)) {
            throw new IllegalStateException(
                    "MM2-only exporter request identity mismatch at " + request
                            + ": expected profile=" + PROFILE + ", packName=" + PACK_NAME
                            + "; actual profile=" + bounded(profile)
                            + ", packName=" + bounded(packName));
        }
        return new Inspection(State.EXACT_MM2, request);
    }

    private static String requiredIdentityString(JsonObject root, String name, Path request) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalStateException(
                    "MM2-only exporter request requires string identity field " + name
                            + ": " + request);
        }
        return value.getAsString();
    }

    private static String bounded(String value) {
        int end = Math.min(value.length(), 160);
        return '"' + value.substring(0, end) + (end < value.length() ? "...\"" : "\"");
    }
}
