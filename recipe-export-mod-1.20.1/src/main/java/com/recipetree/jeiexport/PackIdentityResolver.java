package com.recipetree.jeiexport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Resolves a pack identity without trusting unbounded launcher metadata. */
public final class PackIdentityResolver {
    static final String PACK_NAME_PROPERTY = "jeiexport.packName";
    static final String PACK_VERSION_PROPERTY = "jeiexport.packVersion";
    static final long MAX_METADATA_BYTES = 256L * 1024L;

    private PackIdentityResolver() {
    }

    public static PackIdentity resolve(Path gameDirectory) throws IOException {
        Diagnostics diagnostics = new Diagnostics() {
            @Override
            public void info(String message) {
                JeiExportMod.LOGGER.info("[jeiexport] {}", message);
            }

            @Override
            public void warn(String message) {
                JeiExportMod.LOGGER.warn("[jeiexport] {}", message);
            }
        };
        return resolve(gameDirectory, System::getProperty, diagnostics);
    }

    static PackIdentity resolve(Path gameDirectory, PropertySource properties, Diagnostics diagnostics) throws IOException {
        Path normalizedGameDirectory = gameDirectory.toAbsolutePath().normalize();
        PackIdentity explicit = resolveExplicit(properties);

        List<MetadataCandidate> candidates = new ArrayList<>();
        probeJsonCandidates(normalizedGameDirectory, "minecraftinstance.json", PackIdentityResolver::readCurseForge,
                diagnostics, candidates);

        Path parent = normalizedGameDirectory.getParent();
        if (parent != null) {
            probe(parent.resolve("instance.cfg"), PackIdentityResolver::readPrism, diagnostics, candidates);
        }

        probeJsonCandidates(normalizedGameDirectory, "modrinth.index.json", PackIdentityResolver::readModrinth,
                diagnostics, candidates);

        if (explicit != null) {
            logConflicts(explicit, "JVM properties", candidates, diagnostics);
            diagnostics.info("Using explicit modpack identity from -D" + PACK_NAME_PROPERTY + ".");
            logMissingVersion(explicit, diagnostics);
            return explicit;
        }

        if (!candidates.isEmpty()) {
            MetadataCandidate selected = candidates.get(0);
            logConflicts(selected.identity(), selected.path().toString(), candidates.subList(1, candidates.size()), diagnostics);
            diagnostics.info("Using modpack identity from " + selected.path() + ".");
            logMissingVersion(selected.identity(), diagnostics);
            return selected.identity();
        }

        PackIdentity fallback = gameDirectoryFallback(normalizedGameDirectory);
        diagnostics.warn("No valid launcher metadata was found; using the game-directory name '"
                + fallback.name() + "'. Set -D" + PACK_NAME_PROPERTY
                + " and optionally -D" + PACK_VERSION_PROPERTY + " for deterministic publication metadata.");
        logMissingVersion(fallback, diagnostics);
        return fallback;
    }

    @Nullable
    private static PackIdentity resolveExplicit(PropertySource properties) {
        final String name;
        final String version;
        try {
            name = properties.get(PACK_NAME_PROPERTY);
            version = properties.get(PACK_VERSION_PROPERTY);
        } catch (SecurityException e) {
            throw new IllegalArgumentException("JVM security policy prevented reading explicit modpack identity", e);
        }

        if (name == null && version == null) {
            return null;
        }
        if (name == null) {
            throw new IllegalArgumentException("-D" + PACK_VERSION_PROPERTY + " requires -D" + PACK_NAME_PROPERTY);
        }
        // PackIdentity deliberately rejects blank/oversized/control-character values.
        return new PackIdentity(name, version, "explicit-request");
    }

    private static void probeJsonCandidates(Path gameDirectory, String fileName, MetadataReader reader,
                                            Diagnostics diagnostics, List<MetadataCandidate> candidates) {
        Set<Path> paths = new LinkedHashSet<>();
        paths.add(gameDirectory.resolve(fileName));
        if (isDotMinecraftDirectory(gameDirectory) && gameDirectory.getParent() != null) {
            paths.add(gameDirectory.getParent().resolve(fileName));
        }
        for (Path path : paths) {
            probe(path, reader, diagnostics, candidates);
        }
    }

    private static void probe(Path path, MetadataReader reader, Diagnostics diagnostics,
                              List<MetadataCandidate> candidates) {
        try {
            Optional<PackIdentity> identity = reader.read(path, diagnostics);
            if (identity.isPresent()) {
                candidates.add(new MetadataCandidate(identity.get(), path));
            } else {
                diagnostics.warn("Ignoring " + path + ": it does not contain a supported, valid pack name.");
            }
        } catch (NoSuchFileException e) {
            // The launcher does not use this metadata format; absence is expected.
        } catch (IOException | RuntimeException e) {
            diagnostics.warn("Ignoring unsafe or malformed launcher metadata " + path + ": " + describe(e));
        } catch (StackOverflowError e) {
            // Gson can otherwise overflow on maliciously deep JSON despite the byte limit.
            diagnostics.warn("Ignoring excessively nested launcher metadata " + path + ".");
        }
    }

    private static Optional<PackIdentity> readCurseForge(Path path, Diagnostics diagnostics) throws IOException {
        JsonObject root = readJsonObject(path);
        List<FieldValue> names = jsonValues(root, path, diagnostics, false,
                field("name"), field("installedModpack", "name"), field("installedModpack", "displayName"));
        List<FieldValue> versions = jsonValues(root, path, diagnostics, true,
                field("version"), field("profileVersion"), field("installedModpack", "version"),
                field("installedModpack", "versionName"));
        return identityFromFields(path, names, versions, "curseforge", diagnostics);
    }

    private static Optional<PackIdentity> readModrinth(Path path, Diagnostics diagnostics) throws IOException {
        JsonObject root = readJsonObject(path);
        List<FieldValue> names = jsonValues(root, path, diagnostics, false, field("name"));
        List<FieldValue> versions = jsonValues(root, path, diagnostics, true, field("versionId"), field("version"));
        return identityFromFields(path, names, versions, "modrinth-index", diagnostics);
    }

    private static Optional<PackIdentity> readPrism(Path path, Diagnostics diagnostics) throws IOException {
        String content = readBoundedUtf8(path);
        Map<String, String> values = new LinkedHashMap<>();
        int lineNumber = 0;
        for (String rawLine : content.split("\\R", -1)) {
            lineNumber++;
            String line = lineNumber == 1 && rawLine.startsWith("\uFEFF") ? rawLine.substring(1) : rawLine;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                diagnostics.warn("Ignoring malformed line " + lineNumber + " in " + path + ".");
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            String previous = values.putIfAbsent(key, value);
            if (previous != null && !previous.equals(value)) {
                diagnostics.warn("Conflicting duplicate key '" + key + "' in " + path + "; keeping its first value.");
            }
        }
        List<FieldValue> names = fields(values, "ManagedPackName", "name");
        List<FieldValue> versions = fields(values, "ManagedPackVersionName", "ManagedPackVersionID");
        return identityFromFields(path, names, versions, "prism", diagnostics);
    }

    private static Optional<PackIdentity> identityFromFields(Path path, List<FieldValue> rawNames,
                                                             List<FieldValue> rawVersions, String source,
                                                             Diagnostics diagnostics) {
        List<FieldValue> names = validFields(path, rawNames, "pack name", PackIdentity.MAX_NAME_CODE_POINTS, diagnostics);
        if (names.isEmpty()) {
            return Optional.empty();
        }
        List<FieldValue> versions = validFields(path, rawVersions, "pack version",
                PackIdentity.MAX_VERSION_CODE_POINTS, diagnostics);
        logFieldConflicts(path, "pack name", names, diagnostics);
        logFieldConflicts(path, "pack version", versions, diagnostics);
        return Optional.of(new PackIdentity(names.get(0).value(),
                versions.isEmpty() ? null : versions.get(0).value(), source));
    }

    private static List<FieldValue> validFields(Path path, List<FieldValue> fields, String label,
                                                int maximumCodePoints, Diagnostics diagnostics) {
        List<FieldValue> valid = new ArrayList<>();
        for (FieldValue field : fields) {
            try {
                valid.add(new FieldValue(field.key(),
                        PackIdentity.normalizeAndValidate(field.value(), label, maximumCodePoints)));
            } catch (IllegalArgumentException e) {
                diagnostics.warn("Ignoring invalid " + label + " field '" + field.key() + "' in " + path
                        + ": " + e.getMessage());
            }
        }
        return valid;
    }

    private static void logFieldConflicts(Path path, String label, List<FieldValue> fields,
                                          Diagnostics diagnostics) {
        if (fields.size() < 2) {
            return;
        }
        FieldValue selected = fields.get(0);
        for (int i = 1; i < fields.size(); i++) {
            FieldValue candidate = fields.get(i);
            if (!selected.value().equals(candidate.value())) {
                diagnostics.warn("Conflicting " + label + " fields in " + path + ": selected '"
                        + selected.key() + "' and ignored '" + candidate.key() + "'.");
            }
        }
    }

    private static JsonObject readJsonObject(Path path) throws IOException {
        String json = readBoundedUtf8(path);
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IOException("expected a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IOException("invalid JSON", e);
        }
    }

    private static String readBoundedUtf8(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("symbolic links are not accepted");
        }
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IOException("metadata path is not a regular file");
        }
        if (attributes.size() > MAX_METADATA_BYTES) {
            throw new IOException("metadata exceeds " + MAX_METADATA_BYTES + " bytes");
        }

        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) Math.min(attributes.size(), 8192L));
        try (SeekableByteChannel channel = Files.newByteChannel(path, options)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            long total = 0;
            while (channel.read(buffer) >= 0) {
                buffer.flip();
                total += buffer.remaining();
                if (total > MAX_METADATA_BYTES) {
                    throw new IOException("metadata grew beyond " + MAX_METADATA_BYTES + " bytes while reading");
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.write(chunk);
                buffer.clear();
            }
        }

        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("metadata is not valid UTF-8", e);
        }
    }

    private static List<FieldValue> jsonValues(JsonObject root, Path metadataPath, Diagnostics diagnostics,
                                               boolean acceptNumber, String[]... paths) {
        List<FieldValue> values = new ArrayList<>();
        for (String[] path : paths) {
            JsonElement element = root;
            boolean malformedParent = false;
            for (String segment : path) {
                if (element == null) {
                    break;
                }
                if (!element.isJsonObject()) {
                    diagnostics.warn("Ignoring malformed field '" + String.join(".", path) + "' in "
                            + metadataPath + ": its parent is not a JSON object.");
                    element = null;
                    malformedParent = true;
                    break;
                }
                element = element.getAsJsonObject().get(segment);
            }
            if (element == null || malformedParent) {
                continue;
            }
            String key = String.join(".", path);
            if (element.isJsonPrimitive() && (element.getAsJsonPrimitive().isString()
                    || (acceptNumber && element.getAsJsonPrimitive().isNumber()))) {
                values.add(new FieldValue(key, element.getAsString()));
            } else {
                diagnostics.warn("Ignoring malformed field '" + key + "' in " + metadataPath
                        + ": expected " + (acceptNumber ? "a string or number" : "a string") + ".");
            }
        }
        return values;
    }

    private static List<FieldValue> fields(Map<String, String> values, String... keys) {
        List<FieldValue> fields = new ArrayList<>();
        for (String key : keys) {
            if (values.containsKey(key)) {
                fields.add(new FieldValue(key, values.get(key)));
            }
        }
        return fields;
    }

    private static String[] field(String... path) {
        return path;
    }

    private static PackIdentity gameDirectoryFallback(Path gameDirectory) throws IOException {
        Path namePath = gameDirectory.getFileName();
        if (namePath != null && ".minecraft".equalsIgnoreCase(namePath.toString()) && gameDirectory.getParent() != null) {
            namePath = gameDirectory.getParent().getFileName();
        }
        if (namePath == null) {
            throw new IOException("Cannot derive a pack name from game directory " + gameDirectory
                    + "; set -D" + PACK_NAME_PROPERTY);
        }
        try {
            return new PackIdentity(namePath.toString(), null, "game-directory");
        } catch (IllegalArgumentException e) {
            throw new IOException("Game-directory pack name is invalid; set -D" + PACK_NAME_PROPERTY + ": "
                    + e.getMessage(), e);
        }
    }

    private static boolean isDotMinecraftDirectory(Path gameDirectory) {
        Path fileName = gameDirectory.getFileName();
        return fileName != null && ".minecraft".equalsIgnoreCase(fileName.toString());
    }

    private static void logConflicts(PackIdentity selected, String selectedLabel,
                                     List<MetadataCandidate> candidates, Diagnostics diagnostics) {
        for (MetadataCandidate candidate : candidates) {
            PackIdentity other = candidate.identity();
            boolean nameConflict = !selected.name().equals(other.name());
            boolean versionConflict = selected.version() != null && other.version() != null
                    && !selected.version().equals(other.version());
            if (nameConflict || versionConflict) {
                diagnostics.warn("Conflicting modpack identity metadata: selected " + selectedLabel
                        + " and ignored " + candidate.path() + ".");
            }
        }
    }

    private static void logMissingVersion(PackIdentity identity, Diagnostics diagnostics) {
        if (identity.version() == null) {
            diagnostics.warn("No modpack version was resolved for '" + identity.name()
                    + "'; manifest.pack.version will be omitted.");
        }
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    @FunctionalInterface
    interface PropertySource {
        @Nullable
        String get(String name);
    }

    interface Diagnostics {
        void info(String message);

        void warn(String message);
    }

    @FunctionalInterface
    private interface MetadataReader {
        Optional<PackIdentity> read(Path path, Diagnostics diagnostics) throws IOException;
    }

    private record MetadataCandidate(PackIdentity identity, Path path) {
    }

    private record FieldValue(String key, String value) {
    }
}
