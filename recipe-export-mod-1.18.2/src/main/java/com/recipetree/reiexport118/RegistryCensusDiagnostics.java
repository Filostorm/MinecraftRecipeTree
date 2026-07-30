package com.recipetree.reiexport118;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Persists deterministic, hashed evidence that can localize an aggregate census mismatch.
 *
 * <p>The exact export gate remains {@link RegistryCensus.Deep}. Diagnostic snapshots never
 * authorize an export: they preserve the category vector and a multiplicity-aware SHA-256 for
 * every normalized REI type/resource-id identity without storing arbitrary third-party NBT.</p>
 */
final class RegistryCensusDiagnostics {
    static final String FORMAT = "mrt-rei-registry-census-diagnostics-v1";
    private static final String ROOT_NAME = "reiexport-registry-census";
    private static final String VERSION_NAME = "v1";
    private static final long MAX_SNAPSHOT_BYTES = 64L * 1024L * 1024L;
    private static final int DEFAULT_SUMMARY_LIMIT = 24;
    private static final byte[] DIFF_ID_DOMAIN =
            "MRT_REI_CENSUS_DIAGNOSTIC_DIFF_ID_V1".getBytes(StandardCharsets.UTF_8);

    private static final Comparator<RegistryCensus.EntryKey> ENTRY_KEY_COMPARATOR = Comparator
            .comparing(RegistryCensus.EntryKey::kind)
            .thenComparing(
                    RegistryCensus.EntryKey::typeId,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(
                    RegistryCensus.EntryKey::identifier,
                    Comparator.nullsFirst(Comparator.naturalOrder()));

    record SnapshotId(String categoryCountsSha256, String entryIdentitiesSha256) {
        SnapshotId {
            requireSha256(categoryCountsSha256, "category-count snapshot digest");
            requireSha256(entryIdentitiesSha256, "entry-identity snapshot digest");
        }

        static SnapshotId from(RegistryCensus.Deep census) {
            return new SnapshotId(
                    census.counts().categoryCountsSha256(), census.entryIdentitiesSha256());
        }

        String fileName() {
            return categoryCountsSha256 + "-" + entryIdentitiesSha256 + ".json";
        }

        String summary() {
            return "categoryCountsSha256=" + categoryCountsSha256
                    + " entryIdentitiesSha256=" + entryIdentitiesSha256;
        }
    }

    record StoredSnapshot(
            SnapshotId id,
            RegistryCensus.Capture capture,
            Path path) {
        StoredSnapshot {
            Objects.requireNonNull(id, "REI diagnostic snapshot id is required");
            Objects.requireNonNull(capture, "REI diagnostic snapshot capture is required");
            path = path.toAbsolutePath().normalize();
            if (!id.equals(SnapshotId.from(capture.contract()))) {
                throw new IllegalArgumentException("REI diagnostic snapshot id disagrees with its census");
            }
        }
    }

    record CategoryDelta(String categoryId, Integer expected, Integer actual) {
        CategoryDelta {
            Objects.requireNonNull(categoryId, "REI diagnostic category id is required");
            if (expected != null && expected < 0 || actual != null && actual < 0) {
                throw new IllegalArgumentException("REI diagnostic category counts cannot be negative");
            }
            if (Objects.equals(expected, actual)) {
                throw new IllegalArgumentException("A diagnostic category delta requires distinct states");
            }
        }

        private static String state(Integer value) {
            return value == null ? "<absent>" : value.toString();
        }
    }

    record IdentityDelta(String identitySha256, int expected, int actual) {
        IdentityDelta {
            requireSha256(identitySha256, "diagnostic identity delta digest");
            if (expected < 0 || actual < 0 || expected == actual) {
                throw new IllegalArgumentException("A diagnostic identity delta requires distinct non-negative counts");
            }
        }
    }

    record EntryKeyDelta(
            RegistryCensus.EntryKey key,
            int expectedEntries,
            int actualEntries,
            List<IdentityDelta> identities) {
        EntryKeyDelta {
            Objects.requireNonNull(key, "REI diagnostic entry delta key is required");
            if (expectedEntries < 0 || actualEntries < 0) {
                throw new IllegalArgumentException("REI diagnostic entry totals cannot be negative");
            }
            identities = List.copyOf(identities);
            if (identities.isEmpty()) {
                throw new IllegalArgumentException("A diagnostic entry-key delta requires identity deltas");
            }
        }

        int addedOccurrences() {
            int added = 0;
            for (IdentityDelta identity : identities) {
                added = Math.addExact(added, Math.max(0, identity.actual() - identity.expected()));
            }
            return added;
        }

        int removedOccurrences() {
            int removed = 0;
            for (IdentityDelta identity : identities) {
                removed = Math.addExact(removed, Math.max(0, identity.expected() - identity.actual()));
            }
            return removed;
        }
    }

    record Diff(
            SnapshotId expected,
            SnapshotId actual,
            List<CategoryDelta> categories,
            List<EntryKeyDelta> entries) {
        Diff {
            Objects.requireNonNull(expected, "Expected REI diagnostic snapshot id is required");
            Objects.requireNonNull(actual, "Actual REI diagnostic snapshot id is required");
            categories = List.copyOf(categories);
            entries = List.copyOf(entries);
        }

        int deltaCount() {
            return Math.addExact(categories.size(), entries.size());
        }

        String conciseSummary(int maximumDeltas) {
            if (maximumDeltas < 0) {
                throw new IllegalArgumentException("REI diagnostic summary limit cannot be negative");
            }
            List<String> selected = new ArrayList<>(Math.min(maximumDeltas, deltaCount()));
            for (CategoryDelta category : categories) {
                if (selected.size() == maximumDeltas) {
                    break;
                }
                selected.add("category[" + category.categoryId() + " "
                        + CategoryDelta.state(category.expected())
                        + "->" + CategoryDelta.state(category.actual()) + "]");
            }
            for (EntryKeyDelta entry : entries) {
                if (selected.size() == maximumDeltas) {
                    break;
                }
                selected.add("entry[" + entry.key().summary()
                        + " entries=" + entry.expectedEntries() + "->" + entry.actualEntries()
                        + " identityOccurrences=+" + entry.addedOccurrences()
                        + "/-" + entry.removedOccurrences() + "]");
            }
            int omitted = deltaCount() - selected.size();
            return "categoryDeltas=" + categories.size()
                    + " entryKeyDeltas=" + entries.size()
                    + " selected=" + selected
                    + (omitted == 0 ? "" : " omitted=" + omitted);
        }
    }

    record MismatchEvidence(
            Path actualSnapshot,
            Path expectedSnapshot,
            Path diffPath,
            Diff diff,
            boolean baselineAvailable) {
        MismatchEvidence {
            actualSnapshot = actualSnapshot.toAbsolutePath().normalize();
            expectedSnapshot = expectedSnapshot.toAbsolutePath().normalize();
            if (diffPath != null) {
                diffPath = diffPath.toAbsolutePath().normalize();
            }
            if (baselineAvailable != (diff != null && diffPath != null)) {
                throw new IllegalArgumentException(
                        "REI mismatch evidence baseline state disagrees with its diff artifacts");
            }
        }

        String summary() {
            if (!baselineAvailable) {
                return "registry diagnostics baselineUnavailable expectedSnapshot=" + expectedSnapshot
                        + " actualSnapshot=" + actualSnapshot
                        + "; exact localization requires a captured snapshot for the expected digest pair";
            }
            return "registry diagnostics " + diff.conciseSummary(DEFAULT_SUMMARY_LIMIT)
                    + " expectedSnapshot=" + expectedSnapshot
                    + " actualSnapshot=" + actualSnapshot
                    + " diff=" + diffPath;
        }
    }

    private RegistryCensusDiagnostics() {
    }

    static StoredSnapshot publish(Path gameDirectory, RegistryCensus.Capture capture)
            throws IOException {
        Objects.requireNonNull(capture, "REI diagnostic census capture is required");
        SnapshotId id = SnapshotId.from(capture.contract());
        Path target = snapshotPath(gameDirectory, id);
        byte[] canonical = snapshotBytes(capture);
        publishCanonical(target, canonical, "REI registry diagnostic snapshot");
        return new StoredSnapshot(id, capture, target);
    }

    static Optional<StoredSnapshot> findExpected(
            Path gameDirectory, SnapshotId expected) throws IOException {
        Path path = snapshotPath(gameDirectory, expected);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        return Optional.of(readSnapshot(path, expected));
    }

    static MismatchEvidence compareExpected(
            Path gameDirectory,
            SnapshotId expectedId,
            StoredSnapshot actual) throws IOException {
        Optional<StoredSnapshot> expected = findExpected(gameDirectory, expectedId);
        Path expectedPath = snapshotPath(gameDirectory, expectedId);
        if (expected.isEmpty()) {
            return new MismatchEvidence(
                    actual.path(), expectedPath, null, null, false);
        }
        Diff diff = diff(expected.get().capture(), actual.capture());
        Path diffPath = publishDiff(gameDirectory, diff);
        return new MismatchEvidence(
                actual.path(), expected.get().path(), diffPath, diff, true);
    }

    static Diff diff(RegistryCensus.Capture expected, RegistryCensus.Capture actual) {
        List<CategoryDelta> categoryDeltas = categoryDeltas(
                expected.contract().counts().categoryCounts(),
                actual.contract().counts().categoryCounts());
        List<EntryKeyDelta> entryDeltas = entryDeltas(
                expected.diagnostics().identities(), actual.diagnostics().identities());
        return new Diff(
                SnapshotId.from(expected.contract()),
                SnapshotId.from(actual.contract()),
                categoryDeltas,
                entryDeltas);
    }

    private static List<CategoryDelta> categoryDeltas(
            Map<String, Integer> expected,
            Map<String, Integer> actual) {
        TreeSet<String> identifiers = new TreeSet<>(expected.keySet());
        identifiers.addAll(actual.keySet());
        List<CategoryDelta> deltas = new ArrayList<>();
        for (String identifier : identifiers) {
            Integer expectedCount = expected.get(identifier);
            Integer actualCount = actual.get(identifier);
            if (!Objects.equals(expectedCount, actualCount)) {
                deltas.add(new CategoryDelta(identifier, expectedCount, actualCount));
            }
        }
        return List.copyOf(deltas);
    }

    private static List<EntryKeyDelta> entryDeltas(
            List<RegistryCensus.DiagnosticIdentity> expected,
            List<RegistryCensus.DiagnosticIdentity> actual) {
        TreeMap<RegistryCensus.EntryKey, TreeMap<String, Integer>> expectedByKey =
                identityCorpus(expected);
        TreeMap<RegistryCensus.EntryKey, TreeMap<String, Integer>> actualByKey =
                identityCorpus(actual);
        TreeSet<RegistryCensus.EntryKey> keys = new TreeSet<>(ENTRY_KEY_COMPARATOR);
        keys.addAll(expectedByKey.keySet());
        keys.addAll(actualByKey.keySet());
        List<EntryKeyDelta> deltas = new ArrayList<>();
        for (RegistryCensus.EntryKey key : keys) {
            Map<String, Integer> expectedIdentities =
                    expectedByKey.getOrDefault(key, new TreeMap<>());
            Map<String, Integer> actualIdentities =
                    actualByKey.getOrDefault(key, new TreeMap<>());
            if (expectedIdentities.equals(actualIdentities)) {
                continue;
            }
            TreeSet<String> hashes = new TreeSet<>(expectedIdentities.keySet());
            hashes.addAll(actualIdentities.keySet());
            List<IdentityDelta> identityDeltas = new ArrayList<>();
            for (String hash : hashes) {
                int expectedCount = expectedIdentities.getOrDefault(hash, 0);
                int actualCount = actualIdentities.getOrDefault(hash, 0);
                if (expectedCount != actualCount) {
                    identityDeltas.add(new IdentityDelta(hash, expectedCount, actualCount));
                }
            }
            deltas.add(new EntryKeyDelta(
                    key,
                    sumMultiplicities(expectedIdentities),
                    sumMultiplicities(actualIdentities),
                    identityDeltas));
        }
        return List.copyOf(deltas);
    }

    private static TreeMap<RegistryCensus.EntryKey, TreeMap<String, Integer>> identityCorpus(
            List<RegistryCensus.DiagnosticIdentity> identities) {
        TreeMap<RegistryCensus.EntryKey, TreeMap<String, Integer>> byKey =
                new TreeMap<>(ENTRY_KEY_COMPARATOR);
        for (RegistryCensus.DiagnosticIdentity identity : identities) {
            Integer previous = byKey
                    .computeIfAbsent(identity.key(), ignored -> new TreeMap<>())
                    .put(identity.identitySha256(), identity.multiplicity());
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate REI diagnostic identity fingerprint for "
                        + identity.key().summary() + " " + identity.identitySha256());
            }
        }
        return byKey;
    }

    private static int sumMultiplicities(Map<String, Integer> identities) {
        int total = 0;
        for (int multiplicity : identities.values()) {
            total = Math.addExact(total, multiplicity);
        }
        return total;
    }

    private static Path publishDiff(Path gameDirectory, Diff diff) throws IOException {
        Path target = diffDirectory(gameDirectory).resolve(diffId(diff) + ".json");
        publishCanonical(target, diffBytes(diff), "REI registry diagnostic diff");
        return target.toAbsolutePath().normalize();
    }

    private static StoredSnapshot readSnapshot(Path path, SnapshotId expectedId) throws IOException {
        requireRegularFile(path, "REI registry diagnostic snapshot");
        long size = Files.size(path);
        if (size <= 0 || size > MAX_SNAPSHOT_BYTES) {
            throw new IOException("REI registry diagnostic snapshot has an invalid size "
                    + size + ": " + path);
        }
        byte[] source = Files.readAllBytes(path);
        RegistryCensus.Capture capture;
        try {
            capture = parseSnapshot(source, path);
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException(
                    "REI registry diagnostic snapshot contains invalid census evidence: " + path,
                    exception);
        }
        SnapshotId actualId = SnapshotId.from(capture.contract());
        if (!expectedId.equals(actualId)) {
            throw new IOException("REI registry diagnostic snapshot filename/contents disagreement: expected="
                    + expectedId.summary() + " actual=" + actualId.summary() + " path=" + path);
        }
        byte[] canonical = snapshotBytes(capture);
        if (!Arrays.equals(source, canonical)) {
            throw new IOException("REI registry diagnostic snapshot is not canonical or was modified: " + path);
        }
        return new StoredSnapshot(actualId, capture, path);
    }

    private static RegistryCensus.Capture parseSnapshot(byte[] source, Path path) throws IOException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(new String(source, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new IOException("REI registry diagnostic snapshot is not valid JSON: " + path, exception);
        }
        if (!parsed.isJsonObject()) {
            throw new IOException("REI registry diagnostic snapshot root must be an object: " + path);
        }
        JsonObject root = parsed.getAsJsonObject();
        requireExactKeys(root, Set.of(
                "format", "entries", "displays", "categories", "categoryCountsSha256",
                "emptyEntries", "entryIdentitiesSha256", "categoryCounts", "entryIdentities"),
                "REI registry diagnostic snapshot");
        if (!FORMAT.equals(requiredString(root, "format"))) {
            throw new IOException("Unsupported REI registry diagnostic snapshot format: "
                    + requiredString(root, "format"));
        }
        int entries = requiredInt(root, "entries", 1, Integer.MAX_VALUE);
        int displays = requiredInt(root, "displays", 1, Integer.MAX_VALUE);
        int categories = requiredInt(root, "categories", 1, Integer.MAX_VALUE);
        String categoryDigest = requiredSha256(root, "categoryCountsSha256");
        int emptyEntries = requiredInt(root, "emptyEntries", 0, entries);
        String entryDigest = requiredSha256(root, "entryIdentitiesSha256");

        JsonElement categoryElement = root.get("categoryCounts");
        if (categoryElement == null || !categoryElement.isJsonObject()) {
            throw new IOException("REI registry diagnostic categoryCounts must be an object");
        }
        TreeMap<String, Integer> categoryCounts = new TreeMap<>();
        long displaySum = 0;
        for (Map.Entry<String, JsonElement> entry : categoryElement.getAsJsonObject().entrySet()) {
            int count = requiredInt(entry.getValue(), "categoryCounts." + entry.getKey(), 0, Integer.MAX_VALUE);
            categoryCounts.put(entry.getKey(), count);
            displaySum += count;
        }
        if (categoryCounts.size() != categories || displaySum != displays) {
            throw new IOException("REI registry diagnostic category vector disagrees with aggregate counts: "
                    + "categories=" + categoryCounts.size() + "/" + categories
                    + " displays=" + displaySum + "/" + displays);
        }
        if (!categoryDigest.equals(RegistryCensus.digestCategoryCounts(categoryCounts))) {
            throw new IOException("REI registry diagnostic category vector digest mismatch");
        }
        RegistryCensus.Counts counts = new RegistryCensus.Counts(
                entries, displays, categories, categoryDigest, categoryCounts);

        JsonElement identityElement = root.get("entryIdentities");
        if (identityElement == null || !identityElement.isJsonArray()) {
            throw new IOException("REI registry diagnostic entryIdentities must be an array");
        }
        List<RegistryCensus.DiagnosticIdentity> identities = new ArrayList<>();
        JsonArray identityArray = identityElement.getAsJsonArray();
        for (int index = 0; index < identityArray.size(); index++) {
            JsonElement value = identityArray.get(index);
            if (!value.isJsonObject()) {
                throw new IOException("REI registry diagnostic entryIdentities[" + index + "] must be an object");
            }
            JsonObject identity = value.getAsJsonObject();
            String kind = requiredString(identity, "kind");
            RegistryCensus.EntryKey key;
            if ("empty".equals(kind)) {
                requireExactKeys(identity, Set.of("kind", "identitySha256", "multiplicity"),
                        "REI registry diagnostic empty identity");
                key = RegistryCensus.EntryKey.empty();
            } else if ("serialized".equals(kind)) {
                requireExactKeys(identity, Set.of(
                                "kind", "typeId", "identifier", "identitySha256", "multiplicity"),
                        "REI registry diagnostic serialized identity");
                key = RegistryCensus.EntryKey.serialized(
                        requiredString(identity, "typeId"), requiredString(identity, "identifier"));
            } else {
                throw new IOException("Unsupported REI registry diagnostic identity kind: " + kind);
            }
            identities.add(new RegistryCensus.DiagnosticIdentity(
                    key,
                    requiredSha256(identity, "identitySha256"),
                    requiredInt(identity, "multiplicity", 1, entries)));
        }
        RegistryCensus.DiagnosticSnapshot diagnostics = new RegistryCensus.DiagnosticSnapshot(
                counts, emptyEntries, identities);
        RegistryCensus.Deep contract = new RegistryCensus.Deep(counts, emptyEntries, entryDigest);
        return new RegistryCensus.Capture(contract, diagnostics);
    }

    private static byte[] snapshotBytes(RegistryCensus.Capture capture) throws IOException {
        StringWriter target = new StringWriter();
        try (JsonWriter writer = new JsonWriter(target)) {
            RegistryCensus.Deep contract = capture.contract();
            RegistryCensus.Counts counts = contract.counts();
            writer.beginObject();
            writer.name("format").value(FORMAT);
            writer.name("entries").value(counts.entries());
            writer.name("displays").value(counts.displays());
            writer.name("categories").value(counts.categories());
            writer.name("categoryCountsSha256").value(counts.categoryCountsSha256());
            writer.name("emptyEntries").value(contract.emptyEntries());
            writer.name("entryIdentitiesSha256").value(contract.entryIdentitiesSha256());
            writer.name("categoryCounts").beginObject();
            for (Map.Entry<String, Integer> entry : counts.categoryCounts().entrySet()) {
                writer.name(entry.getKey()).value(entry.getValue());
            }
            writer.endObject();
            writer.name("entryIdentities").beginArray();
            for (RegistryCensus.DiagnosticIdentity identity : capture.diagnostics().identities()) {
                writer.beginObject();
                if (identity.key().kind() == RegistryCensus.EntryIdentityKind.EMPTY) {
                    writer.name("kind").value("empty");
                } else {
                    writer.name("kind").value("serialized");
                    writer.name("typeId").value(identity.key().typeId());
                    writer.name("identifier").value(identity.key().identifier());
                }
                writer.name("identitySha256").value(identity.identitySha256());
                writer.name("multiplicity").value(identity.multiplicity());
                writer.endObject();
            }
            writer.endArray();
            writer.endObject();
        }
        byte[] bytes = target.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SNAPSHOT_BYTES) {
            throw new IOException("REI registry diagnostic snapshot exceeds the hard byte limit: "
                    + bytes.length + " > " + MAX_SNAPSHOT_BYTES);
        }
        return bytes;
    }

    private static byte[] diffBytes(Diff diff) throws IOException {
        StringWriter target = new StringWriter();
        try (JsonWriter writer = new JsonWriter(target)) {
            writer.beginObject();
            writer.name("format").value("mrt-rei-registry-census-diff-v1");
            writeSnapshotId(writer, "expected", diff.expected());
            writeSnapshotId(writer, "actual", diff.actual());
            writer.name("categoryDeltas").beginArray();
            for (CategoryDelta category : diff.categories()) {
                writer.beginObject();
                writer.name("categoryId").value(category.categoryId());
                writer.name("expected").value(category.expected());
                writer.name("actual").value(category.actual());
                writer.endObject();
            }
            writer.endArray();
            writer.name("entryKeyDeltas").beginArray();
            for (EntryKeyDelta entry : diff.entries()) {
                writer.beginObject();
                writeEntryKey(writer, entry.key());
                writer.name("expectedEntries").value(entry.expectedEntries());
                writer.name("actualEntries").value(entry.actualEntries());
                writer.name("identityDeltas").beginArray();
                for (IdentityDelta identity : entry.identities()) {
                    writer.beginObject();
                    writer.name("identitySha256").value(identity.identitySha256());
                    writer.name("expected").value(identity.expected());
                    writer.name("actual").value(identity.actual());
                    writer.endObject();
                }
                writer.endArray();
                writer.endObject();
            }
            writer.endArray();
            writer.endObject();
        }
        return target.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void writeSnapshotId(JsonWriter writer, String name, SnapshotId id)
            throws IOException {
        writer.name(name).beginObject();
        writer.name("categoryCountsSha256").value(id.categoryCountsSha256());
        writer.name("entryIdentitiesSha256").value(id.entryIdentitiesSha256());
        writer.endObject();
    }

    private static void writeEntryKey(JsonWriter writer, RegistryCensus.EntryKey key)
            throws IOException {
        if (key.kind() == RegistryCensus.EntryIdentityKind.EMPTY) {
            writer.name("kind").value("empty");
        } else {
            writer.name("kind").value("serialized");
            writer.name("typeId").value(key.typeId());
            writer.name("identifier").value(key.identifier());
        }
    }

    private static String diffId(Diff diff) {
        MessageDigest digest = sha256();
        digest.update(DIFF_ID_DOMAIN);
        updateString(digest, diff.expected().categoryCountsSha256());
        updateString(digest, diff.expected().entryIdentitiesSha256());
        updateString(digest, diff.actual().categoryCountsSha256());
        updateString(digest, diff.actual().entryIdentitiesSha256());
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path snapshotPath(Path gameDirectory, SnapshotId id) throws IOException {
        return snapshotDirectory(gameDirectory).resolve(id.fileName()).toAbsolutePath().normalize();
    }

    private static Path snapshotDirectory(Path gameDirectory) throws IOException {
        return requireChildDirectory(diagnosticRoot(gameDirectory), "snapshots");
    }

    private static Path diffDirectory(Path gameDirectory) throws IOException {
        return requireChildDirectory(diagnosticRoot(gameDirectory), "diffs");
    }

    private static Path diagnosticRoot(Path gameDirectory) throws IOException {
        Path normalizedGameDirectory = gameDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedGameDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Minecraft game directory is not a real directory: "
                    + normalizedGameDirectory);
        }
        Path root = requireChildDirectory(normalizedGameDirectory, ROOT_NAME);
        return requireChildDirectory(root, VERSION_NAME);
    }

    private static Path requireChildDirectory(Path parent, String name) throws IOException {
        Path child = parent.resolve(name);
        if (!Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(child);
            } catch (FileAlreadyExistsException race) {
                // Validate the concurrently published directory below.
            }
        }
        if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                    "REI registry diagnostic path component is not a real directory: " + child);
        }
        return child;
    }

    private static void publishCanonical(Path target, byte[] canonical, String description)
            throws IOException {
        Path directory = target.getParent();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(description + " directory is not a real directory: " + directory);
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            verifyCanonicalFile(target, canonical, description);
            return;
        }
        Path temporary = Files.createTempFile(directory, target.getFileName() + ".", ".tmp");
        try {
            Files.write(temporary, canonical);
            publishHardLink(temporary, target, canonical, description);
        } finally {
            Files.deleteIfExists(temporary);
        }
        verifyCanonicalFile(target, canonical, description);
    }

    /**
     * Atomically creates a new immutable name for a completely written same-filesystem file.
     *
     * <p>{@code ATOMIC_MOVE} is deliberately not used here: the JDK permits a provider to replace
     * a target that appears concurrently, even when {@code REPLACE_EXISTING} was not requested.
     * A hard link has create-if-absent semantics, so a competing target is retained and verified
     * byte-for-byte instead of ever being overwritten.</p>
     */
    static void publishHardLink(
            Path completeTemporary,
            Path target,
            byte[] canonical,
            String description
    ) throws IOException {
        try {
            Files.createLink(target, completeTemporary);
        } catch (FileAlreadyExistsException race) {
            verifyCanonicalFile(target, canonical, description);
        } catch (UnsupportedOperationException exception) {
            throw new IOException("Atomic create-if-absent " + description
                    + " publication requires same-filesystem hard-link support; no rename or copy fallback was attempted",
                    exception);
        }
    }

    private static void verifyCanonicalFile(Path path, byte[] expected, String description)
            throws IOException {
        requireRegularFile(path, description);
        long size = Files.size(path);
        if (size != expected.length || !Arrays.equals(Files.readAllBytes(path), expected)) {
            throw new IOException("Existing " + description
                    + " does not byte-match the deterministic census evidence: " + path);
        }
    }

    private static void requireRegularFile(Path path, String description) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(description + " is not a real regular file: " + path);
        }
    }

    private static void requireExactKeys(JsonObject object, Set<String> expected, String description)
            throws IOException {
        if (!object.keySet().equals(expected)) {
            throw new IOException(description + " fields are not exact: expected="
                    + new TreeSet<>(expected) + " actual=" + new TreeSet<>(object.keySet()));
        }
    }

    private static String requiredString(JsonObject object, String name) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IOException(name + " must be a JSON string");
        }
        return value.getAsString();
    }

    private static String requiredSha256(JsonObject object, String name) throws IOException {
        String value = requiredString(object, name);
        try {
            requireSha256(value, name);
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
        return value;
    }

    private static void requireSha256(String value, String description) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    description + " must be 64 lowercase hexadecimal characters");
        }
    }

    private static int requiredInt(JsonObject object, String name, int minimum, int maximum)
            throws IOException {
        JsonElement value = object.get(name);
        if (value == null) {
            throw new IOException(name + " is required");
        }
        return requiredInt(value, name, minimum, maximum);
    }

    private static int requiredInt(JsonElement value, String description, int minimum, int maximum)
            throws IOException {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException(description + " must be an integer");
        }
        String encoded = value.getAsString();
        if (!encoded.matches("0|[1-9][0-9]*")) {
            throw new IOException(description + " must be a canonical non-negative integer");
        }
        int parsed;
        try {
            parsed = Integer.parseInt(encoded);
        } catch (NumberFormatException exception) {
            throw new IOException(description + " exceeds the supported integer range", exception);
        }
        if (parsed < minimum || parsed > maximum) {
            throw new IOException(description + " is outside [" + minimum + ", " + maximum + "]");
        }
        return parsed;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The JVM does not provide SHA-256", exception);
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (encoded.length >>> 24));
        digest.update((byte) (encoded.length >>> 16));
        digest.update((byte) (encoded.length >>> 8));
        digest.update((byte) encoded.length);
        digest.update(encoded);
    }
}
