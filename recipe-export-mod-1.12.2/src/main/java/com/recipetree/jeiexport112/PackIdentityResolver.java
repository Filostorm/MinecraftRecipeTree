package com.recipetree.jeiexport112;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Resolves launcher metadata without depending on Minecraft or Forge runtime classes. */
final class PackIdentityResolver {
    private static final long JSON_METADATA_LIMIT = 16L * 1024L * 1024L;
    private static final long PRISM_METADATA_LIMIT = 256L * 1024L;

    private PackIdentityResolver() {
    }

    static PackIdentity resolve(Path gameDirectory, String explicitName, String explicitVersion)
            throws IOException {
        Path gameRoot = gameDirectory.toAbsolutePath().normalize();
        if (explicitVersion != null && explicitName == null) {
            throw new IOException("packVersion requires packName so the explicit identity is unambiguous");
        }
        if (explicitName != null) {
            return new PackIdentity(explicitName, explicitVersion, "explicit-request");
        }

        PackIdentity curseForge = resolveCurseForge(gameRoot);
        if (curseForge != null) {
            return curseForge;
        }
        PackIdentity prism = resolvePrism(gameRoot);
        if (prism != null) {
            return prism;
        }
        PackIdentity modrinth = resolveModrinth(gameRoot);
        if (modrinth != null) {
            return modrinth;
        }

        Path identityDirectory = gameRoot;
        Path fileName = identityDirectory.getFileName();
        if (fileName != null && ".minecraft".equalsIgnoreCase(fileName.toString())
                && identityDirectory.getParent() != null) {
            identityDirectory = identityDirectory.getParent();
            fileName = identityDirectory.getFileName();
        }
        if (fileName == null) {
            throw new IOException("Could not derive a modpack name from game directory " + gameRoot
                    + "; set packName explicitly");
        }
        return new PackIdentity(fileName.toString(), null, "game-directory");
    }

    private static PackIdentity resolveCurseForge(Path gameRoot) throws IOException {
        for (Path candidate : candidates(gameRoot, "minecraftinstance.json")) {
            if (!existsWithoutFollowingLinks(candidate)) {
                continue;
            }
            JsonObject root = readJsonObject(candidate, JSON_METADATA_LIMIT, "CurseForge metadata");
            String name = requiredMetadataString(root, "name", candidate);
            String version = optionalNestedMetadataString(root, "manifest", "version", candidate);
            if (version == null) {
                version = optionalCurseForgeVersion(root, candidate);
            }
            if (version == null) {
                version = optionalMetadataString(root, "modpackVersion", candidate);
            }
            if (version == null && root.has("installedModpack")
                    && root.get("installedModpack").isJsonObject()) {
                version = optionalMetadataString(
                        root.getAsJsonObject("installedModpack"), "version", candidate);
            }
            return new PackIdentity(name, version, "curseforge");
        }
        return null;
    }

    private static String optionalNestedMetadataString(
            JsonObject root, String objectField, String field, Path path) throws IOException {
        if (!root.has(objectField) || root.get(objectField).isJsonNull()) {
            return null;
        }
        if (!root.get(objectField).isJsonObject()) {
            throw new IOException("Launcher metadata field " + objectField
                    + " must be an object: " + path);
        }
        return optionalMetadataString(root.getAsJsonObject(objectField), field, path);
    }

    private static String optionalCurseForgeVersion(JsonObject root, Path path) throws IOException {
        if (!root.has("installedModpackVersion") || root.get("installedModpackVersion").isJsonNull()) {
            return null;
        }
        JsonElement value = root.get("installedModpackVersion");
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String version = value.getAsString().trim();
            return version.isEmpty() ? null : version;
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            String version = optionalMetadataString(object, "version", path);
            if (version == null) {
                version = optionalMetadataString(object, "name", path);
            }
            return version;
        }
        throw new IOException("Launcher metadata field installedModpackVersion must be a string "
                + "or object: " + path);
    }

    private static PackIdentity resolvePrism(Path gameRoot) throws IOException {
        for (Path candidate : candidates(gameRoot, "instance.cfg")) {
            if (!existsWithoutFollowingLinks(candidate)) {
                continue;
            }
            Map<String, String> settings = readPrismSettings(candidate);
            String name = firstNonBlank(settings.get("ManagedPackName"), settings.get("name"));
            if (name == null) {
                throw new IOException("Prism metadata has no ManagedPackName or name: " + candidate);
            }
            String version = firstNonBlank(
                    settings.get("ManagedPackVersionName"), settings.get("ManagedPackVersionID"));
            return new PackIdentity(name, version, "prism");
        }
        return null;
    }

    private static PackIdentity resolveModrinth(Path gameRoot) throws IOException {
        for (Path candidate : candidates(gameRoot, "modrinth.index.json")) {
            if (!existsWithoutFollowingLinks(candidate)) {
                continue;
            }
            JsonObject root = readJsonObject(candidate, JSON_METADATA_LIMIT, "Modrinth metadata");
            String name = requiredMetadataString(root, "name", candidate);
            String version = optionalMetadataString(root, "versionId", candidate);
            return new PackIdentity(name, version, "modrinth-index");
        }
        return null;
    }

    private static Set<Path> candidates(Path gameRoot, String name) {
        Set<Path> result = new LinkedHashSet<Path>();
        result.add(gameRoot.resolve(name).normalize());
        if (gameRoot.getParent() != null) {
            result.add(gameRoot.getParent().resolve(name).normalize());
        }
        return result;
    }

    private static boolean existsWithoutFollowingLinks(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static JsonObject readJsonObject(Path path, long maximumBytes, String description)
            throws IOException {
        String source = readBoundedUtf8(path, maximumBytes, description);
        final JsonElement parsed;
        try {
            parsed = new JsonParser().parse(source);
        } catch (RuntimeException exception) {
            throw new IOException(description + " is not valid JSON: " + path, exception);
        }
        if (!parsed.isJsonObject()) {
            throw new IOException(description + " root must be a JSON object: " + path);
        }
        return parsed.getAsJsonObject();
    }

    private static Map<String, String> readPrismSettings(Path path) throws IOException {
        String source = readBoundedUtf8(path, PRISM_METADATA_LIMIT, "Prism metadata");
        Map<String, String> values = new LinkedHashMap<String, String>();
        String[] lines = source.split("\\r?\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator < 1) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (values.put(key, value) != null) {
                throw new IOException("Prism metadata repeats key " + key + " at line "
                        + (index + 1) + ": " + path);
            }
        }
        return values;
    }

    private static String readBoundedUtf8(Path path, long maximumBytes, String description)
            throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || Files.isSymbolicLink(path)) {
            throw new IOException(description + " must be a regular non-symlink file: " + path);
        }
        if (attributes.size() > maximumBytes) {
            throw new IOException(description + " exceeds its " + maximumBytes
                    + "-byte limit: " + path);
        }
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length > maximumBytes) {
            throw new IOException(description + " grew beyond its " + maximumBytes
                    + "-byte limit while being read: " + path);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IOException(description + " is not valid UTF-8: " + path, exception);
        }
    }

    private static String requiredMetadataString(JsonObject object, String field, Path path)
            throws IOException {
        String result = optionalMetadataString(object, field, path);
        if (result == null) {
            throw new IOException("Launcher metadata has no string " + field + ": " + path);
        }
        return result;
    }

    private static String optionalMetadataString(JsonObject object, String field, Path path)
            throws IOException {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        JsonElement value = object.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Launcher metadata field " + field + " must be a string: " + path);
        }
        String result = value.getAsString().trim();
        return result.isEmpty() ? null : result;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return null;
    }
}
