package com.recipetree.reiexport118;

import com.recipetree.reiexport118.compat.Mm2EntryCanonicalization;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A deterministic census of the complete REI registry surface used by an export.
 *
 * <p>The cheap category-count vector is safe to sample every client tick. The entry identity
 * digest is intentionally computed only twice: when a new stability candidate is observed and
 * again after the request has been claimed atomically. This detects equal-total category swaps
 * and equal-count entry mutations without serializing roughly 27,000 entries for all 200
 * readiness ticks.</p>
 */
final class RegistryCensus {
    private static final byte[] CATEGORY_DIGEST_DOMAIN =
            "MRT_REI_CATEGORY_COUNTS_V1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENTRY_DIGEST_DOMAIN =
            "MRT_REI_ENTRY_IDENTITIES_V2".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENTRY_DIAGNOSTIC_IDENTITY_DOMAIN =
            "MRT_REI_ENTRY_DIAGNOSTIC_IDENTITY_V1".getBytes(StandardCharsets.UTF_8);

    enum EntryIdentityKind {
        EMPTY(0),
        SERIALIZED(1);

        private final int digestTag;

        EntryIdentityKind(int digestTag) {
            this.digestTag = digestTag;
        }
    }

    record EntryKey(EntryIdentityKind kind, String typeId, String identifier) {
        EntryKey {
            Objects.requireNonNull(kind, "REI diagnostic entry kind is required");
            if (kind == EntryIdentityKind.EMPTY && (typeId != null || identifier != null)) {
                throw new IllegalArgumentException("An empty REI diagnostic entry cannot have a type or identifier");
            }
            if (kind == EntryIdentityKind.SERIALIZED
                    && (typeId == null || typeId.isBlank() || identifier == null || identifier.isBlank())) {
                throw new IllegalArgumentException(
                        "A serialized REI diagnostic entry requires a type and identifier");
            }
        }

        static EntryKey empty() {
            return new EntryKey(EntryIdentityKind.EMPTY, null, null);
        }

        static EntryKey serialized(String typeId, String identifier) {
            return new EntryKey(EntryIdentityKind.SERIALIZED, typeId, identifier);
        }

        String summary() {
            return kind == EntryIdentityKind.EMPTY ? "<empty>" : typeId + " " + identifier;
        }
    }

    record ObservedIdentity(EntryKey key, EntryIdentity identity) {
        ObservedIdentity {
            Objects.requireNonNull(key, "REI diagnostic entry key is required");
            Objects.requireNonNull(identity, "REI diagnostic identity is required");
            if (key.kind() != identity.kind()) {
                throw new IllegalArgumentException("REI diagnostic entry kind disagrees with its identity kind");
            }
        }

        static ObservedIdentity empty() {
            return new ObservedIdentity(EntryKey.empty(), EntryIdentity.empty());
        }

        static ObservedIdentity serialized(
                String typeId, String identifier, String canonicalIdentity) {
            return new ObservedIdentity(
                    EntryKey.serialized(typeId, identifier),
                    EntryIdentity.serialized(canonicalIdentity));
        }
    }

    record DiagnosticIdentity(EntryKey key, String identitySha256, int multiplicity) {
        DiagnosticIdentity {
            Objects.requireNonNull(key, "REI diagnostic entry key is required");
            if (identitySha256 == null || !identitySha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "REI diagnostic identity SHA-256 must be 64 lowercase hexadecimal characters");
            }
            if (multiplicity <= 0) {
                throw new IllegalArgumentException("REI diagnostic identity multiplicity must be positive");
            }
        }
    }

    record EntryIdentity(EntryIdentityKind kind, String serialized) {
        EntryIdentity {
            if (kind == null) {
                throw new IllegalArgumentException("REI entry identity kind is required");
            }
            if (kind == EntryIdentityKind.EMPTY && serialized != null) {
                throw new IllegalArgumentException("An empty REI entry identity cannot have a payload");
            }
            if (kind == EntryIdentityKind.SERIALIZED && serialized == null) {
                throw new IllegalArgumentException("A serialized REI entry identity requires a payload");
            }
        }

        static EntryIdentity empty() {
            return new EntryIdentity(EntryIdentityKind.EMPTY, null);
        }

        static EntryIdentity serialized(String value) {
            return new EntryIdentity(EntryIdentityKind.SERIALIZED, value);
        }
    }

    static final class NullEntryStackException extends IllegalStateException {
        NullEntryStackException() {
            super("REI entry registry contains a null stack");
        }
    }

    record Counts(
            int entries,
            int displays,
            int categories,
            String categoryCountsSha256,
            Map<String, Integer> categoryCounts) {
        Counts {
            categoryCounts = Collections.unmodifiableMap(new TreeMap<>(categoryCounts));
        }

        String summary() {
            return "entries=" + entries
                    + " displays=" + displays
                    + " categories=" + categories
                    + " categoryCountsSha256=" + categoryCountsSha256
                    + " jeresources=" + namespaceCounts("jeresources");
        }

        Map<String, Integer> namespaceCounts(String namespace) {
            String prefix = namespace + ":";
            TreeMap<String, Integer> selected = new TreeMap<>();
            for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    selected.put(entry.getKey(), entry.getValue());
                }
            }
            return Collections.unmodifiableMap(selected);
        }
    }

    record Deep(Counts counts, int emptyEntries, String entryIdentitiesSha256) {
        Deep {
            if (emptyEntries < 0 || emptyEntries > counts.entries()) {
                throw new IllegalArgumentException("REI empty entry count is outside the registry census: "
                        + emptyEntries + " of " + counts.entries());
            }
        }

        String summary() {
            return counts.summary()
                    + " emptyEntries=" + emptyEntries
                    + " entryIdentitiesSha256=" + entryIdentitiesSha256;
        }
    }

    record DiagnosticSnapshot(
            Counts counts,
            int emptyEntries,
            List<DiagnosticIdentity> identities) {
        DiagnosticSnapshot {
            Objects.requireNonNull(counts, "REI diagnostic census counts are required");
            if (emptyEntries < 0 || emptyEntries > counts.entries()) {
                throw new IllegalArgumentException("REI diagnostic empty entry count is outside the registry census: "
                        + emptyEntries + " of " + counts.entries());
            }
            List<DiagnosticIdentity> sorted = new ArrayList<>(identities);
            sorted.sort(DIAGNOSTIC_IDENTITY_COMPARATOR);
            identities = List.copyOf(sorted);
            int observedEntries = 0;
            int observedEmptyEntries = 0;
            DiagnosticIdentity previous = null;
            for (DiagnosticIdentity identity : identities) {
                if (previous != null
                        && DIAGNOSTIC_IDENTITY_COMPARATOR.compare(previous, identity) == 0) {
                    throw new IllegalArgumentException(
                            "REI diagnostic snapshot contains a duplicate identity fingerprint for "
                                    + identity.key().summary() + " " + identity.identitySha256());
                }
                observedEntries = Math.addExact(observedEntries, identity.multiplicity());
                if (identity.key().kind() == EntryIdentityKind.EMPTY) {
                    observedEmptyEntries = Math.addExact(
                            observedEmptyEntries, identity.multiplicity());
                }
                previous = identity;
            }
            if (observedEntries != counts.entries()) {
                throw new IllegalArgumentException("REI diagnostic identity multiplicity disagrees with entry count: "
                        + observedEntries + " of " + counts.entries());
            }
            if (observedEmptyEntries != emptyEntries) {
                throw new IllegalArgumentException("REI diagnostic empty identity multiplicity disagrees with census: "
                        + observedEmptyEntries + " of " + emptyEntries);
            }
        }
    }

    record Capture(Deep contract, DiagnosticSnapshot diagnostics) {
        Capture {
            Objects.requireNonNull(contract, "REI deep census contract is required");
            Objects.requireNonNull(diagnostics, "REI deep census diagnostics are required");
            if (!contract.counts().equals(diagnostics.counts())) {
                throw new IllegalArgumentException("REI census contract and diagnostics use different counts");
            }
            if (contract.emptyEntries() != diagnostics.emptyEntries()) {
                throw new IllegalArgumentException("REI census contract and diagnostics use different empty counts");
            }
        }
    }

    private record DiagnosticIdentityKey(EntryKey key, String identitySha256) {
    }

    private static final Comparator<EntryKey> ENTRY_KEY_COMPARATOR = Comparator
            .comparing(EntryKey::kind)
            .thenComparing(EntryKey::typeId, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(EntryKey::identifier, Comparator.nullsFirst(Comparator.naturalOrder()));
    private static final Comparator<DiagnosticIdentity> DIAGNOSTIC_IDENTITY_COMPARATOR = Comparator
            .comparing(DiagnosticIdentity::key, ENTRY_KEY_COMPARATOR)
            .thenComparing(DiagnosticIdentity::identitySha256);
    private static final Comparator<DiagnosticIdentityKey> DIAGNOSTIC_IDENTITY_KEY_COMPARATOR = Comparator
            .comparing(DiagnosticIdentityKey::key, ENTRY_KEY_COMPARATOR)
            .thenComparing(DiagnosticIdentityKey::identitySha256);

    private RegistryCensus() {
    }

    static Counts captureCounts() {
        EntryRegistry entryRegistry = EntryRegistry.getInstance();
        DisplayRegistry displayRegistry = DisplayRegistry.getInstance();
        CategoryRegistry categoryRegistry = CategoryRegistry.getInstance();

        TreeMap<String, Integer> categoryCounts = new TreeMap<>();
        for (CategoryRegistry.CategoryConfiguration<?> configuration : categoryRegistry) {
            String id = configuration.getCategoryIdentifier().getIdentifier().toString();
            if (categoryCounts.put(id, 0) != null) {
                throw new IllegalStateException("REI registered duplicate category identifier " + id);
            }
        }
        int reportedCategoryCount = categoryRegistry.size();
        if (reportedCategoryCount != categoryCounts.size()) {
            throw new IllegalStateException("REI category iterator/size disagreement: iterator="
                    + categoryCounts.size() + " reported=" + reportedCategoryCount);
        }

        long summedDisplays = 0;
        for (Map.Entry<CategoryIdentifier<?>, List<Display>> entry
                : displayRegistry.getAll().entrySet()) {
            String id = entry.getKey().getIdentifier().toString();
            int count = entry.getValue().size();
            if (!categoryCounts.containsKey(id)) {
                if (count == 0) {
                    continue;
                }
                throw new IllegalStateException(
                        "REI exposes " + count + " displays for unregistered category " + id);
            }
            categoryCounts.put(id, count);
            summedDisplays += count;
        }
        int reportedDisplayCount = displayRegistry.displaySize();
        if (summedDisplays != reportedDisplayCount) {
            throw new IllegalStateException("REI per-category/displaySize disagreement: vector="
                    + summedDisplays + " reported=" + reportedDisplayCount);
        }
        if (summedDisplays > Integer.MAX_VALUE) {
            throw new IllegalStateException("REI display census exceeds the supported integer range: "
                    + summedDisplays);
        }

        return new Counts(
                entryRegistry.size(),
                (int) summedDisplays,
                categoryCounts.size(),
                digestCategoryCounts(categoryCounts),
                categoryCounts);
    }

    static Deep captureDeep() {
        return captureDeepWithDiagnostics().contract();
    }

    static Capture captureDeepWithDiagnostics() {
        Counts before = captureCounts();
        List<ObservedIdentity> observations = new ArrayList<>(before.entries());
        int emptyEntries = 0;
        try (var entryStacks = EntryRegistry.getInstance().getEntryStacks()) {
            var iterator = entryStacks.iterator();
            while (iterator.hasNext()) {
                ObservedIdentity observation = observeEntry(iterator.next());
                observations.add(observation);
                if (observation.identity().kind() == EntryIdentityKind.EMPTY) {
                    emptyEntries++;
                }
            }
        }
        if (observations.size() != before.entries()) {
            throw new IllegalStateException("REI entry stream/size disagreement: stream="
                    + observations.size() + " reported=" + before.entries());
        }
        Counts after = captureCounts();
        if (!before.equals(after)) {
            throw new IllegalStateException("REI registry changed while computing its deep census: "
                    + before.summary() + " -> " + after.summary());
        }
        List<EntryIdentity> identities = new ArrayList<>(observations.size());
        for (ObservedIdentity observation : observations) {
            identities.add(observation.identity());
        }
        Deep contract = new Deep(before, emptyEntries, digestEntryIdentities(identities));
        DiagnosticSnapshot diagnostics = new DiagnosticSnapshot(
                before, emptyEntries, diagnosticIdentities(observations));
        return new Capture(contract, diagnostics);
    }

    static String digestCategoryCounts(Map<String, Integer> counts) {
        MessageDigest digest = sha256();
        digest.update(CATEGORY_DIGEST_DOMAIN);
        TreeMap<String, Integer> sorted = new TreeMap<>(counts);
        updateInt(digest, sorted.size());
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            updateString(digest, entry.getKey());
            updateInt(digest, entry.getValue());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String digestEntryIdentities(Collection<EntryIdentity> identities) {
        MessageDigest digest = sha256();
        digest.update(ENTRY_DIGEST_DOMAIN);
        List<EntryIdentity> sorted = new ArrayList<>(identities);
        sorted.sort(Comparator
                .comparing(EntryIdentity::kind)
                .thenComparing(EntryIdentity::serialized, Comparator.nullsFirst(Comparator.naturalOrder())));
        updateInt(digest, sorted.size());
        for (EntryIdentity identity : sorted) {
            updateInt(digest, identity.kind().digestTag);
            if (identity.kind() == EntryIdentityKind.SERIALIZED) {
                updateString(digest, identity.serialized());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static List<DiagnosticIdentity> diagnosticIdentities(
            Collection<ObservedIdentity> observations) {
        TreeMap<DiagnosticIdentityKey, Integer> multiplicities =
                new TreeMap<>(DIAGNOSTIC_IDENTITY_KEY_COMPARATOR);
        for (ObservedIdentity observation : observations) {
            DiagnosticIdentityKey key = new DiagnosticIdentityKey(
                    observation.key(), digestDiagnosticIdentity(observation.identity()));
            multiplicities.merge(key, 1, Math::addExact);
        }
        List<DiagnosticIdentity> diagnostics = new ArrayList<>(multiplicities.size());
        for (Map.Entry<DiagnosticIdentityKey, Integer> entry : multiplicities.entrySet()) {
            diagnostics.add(new DiagnosticIdentity(
                    entry.getKey().key(),
                    entry.getKey().identitySha256(),
                    entry.getValue()));
        }
        return List.copyOf(diagnostics);
    }

    private static String digestDiagnosticIdentity(EntryIdentity identity) {
        MessageDigest digest = sha256();
        digest.update(ENTRY_DIAGNOSTIC_IDENTITY_DOMAIN);
        updateInt(digest, identity.kind().digestTag);
        if (identity.kind() == EntryIdentityKind.SERIALIZED) {
            updateString(digest, identity.serialized());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static ObservedIdentity observeEntry(EntryStack<?> original) {
        if (original == null) {
            throw new NullEntryStackException();
        }
        if (original.isEmpty()) {
            return ObservedIdentity.empty();
        }
        EntryStack<?> stack = Mm2EntryCanonicalization.canonicalIdentityCopy(original);
        if (!stack.supportSaving()) {
            throw new IllegalStateException("REI entry does not support canonical serialization: type="
                    + stack.getType().getId() + " identifier=" + stack.getIdentifier());
        }
        CompoundTag serialized = stack.saveStack();
        String typeId = stack.getType().getId().toString();
        String identifier = stack.getIdentifier().toString();
        StringBuilder identity = new StringBuilder(256);
        appendLengthPrefixed(identity, typeId);
        appendLengthPrefixed(identity, identifier);
        appendTag(identity, serialized);
        return ObservedIdentity.serialized(typeId, identifier, identity.toString());
    }

    private static void appendTag(StringBuilder target, Tag tag) {
        target.append((int) tag.getId()).append(':');
        if (tag instanceof CompoundTag compound) {
            target.append('{');
            List<String> keys = new ArrayList<>(compound.getAllKeys());
            Collections.sort(keys);
            for (String key : keys) {
                appendLengthPrefixed(target, key);
                Tag value = compound.get(key);
                if (value == null) {
                    throw new IllegalStateException("CompoundTag key disappeared during census: " + key);
                }
                appendTag(target, value);
            }
            target.append('}');
        } else if (tag instanceof ListTag list) {
            target.append('[').append(list.size()).append(':');
            for (int index = 0; index < list.size(); index++) {
                appendTag(target, list.get(index));
            }
            target.append(']');
        } else {
            appendLengthPrefixed(target, tag.getAsString());
        }
    }

    private static void appendLengthPrefixed(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
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
        updateInt(digest, encoded.length);
        digest.update(encoded);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }
}
