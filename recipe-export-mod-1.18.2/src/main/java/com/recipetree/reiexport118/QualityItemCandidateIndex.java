package com.recipetree.reiexport118;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Retains only requested quality-item candidates and resolves them by exact
 * canonical catalog identity.
 *
 * <p>A type/identifier pair is not necessarily a unique catalog entry because
 * REI can expose multiple semantic NBT variants of the same item. Repeated
 * occurrences of the same canonical serialized identity are harmless and are
 * deduplicated; two distinct serialized identities are deliberately
 * ambiguous.</p>
 */
final class QualityItemCandidateIndex<T> {
    private record Pair(String typeId, String identifier) {
        private Pair {
            Objects.requireNonNull(typeId, "typeId");
            Objects.requireNonNull(identifier, "identifier");
        }

        String display() {
            return typeId + " " + identifier;
        }
    }

    private static final class Bucket<T> {
        private final Pair pair;
        private final Map<String, T> valueByCanonicalIdentity = new LinkedHashMap<>();
        private int matchedOccurrences;

        private Bucket(Pair pair) {
            this.pair = pair;
        }

        void accept(String canonicalIdentity, T value) {
            matchedOccurrences++;
            valueByCanonicalIdentity.putIfAbsent(canonicalIdentity, value);
        }
    }

    private final Map<Pair, Bucket<T>> buckets = new LinkedHashMap<>();
    private int acceptedOccurrences;

    QualityItemCandidateIndex(List<ExportRequest.ItemSample> selectors) {
        Objects.requireNonNull(selectors, "selectors");
        for (ExportRequest.ItemSample selector : selectors) {
            Objects.requireNonNull(selector, "qualityItemSample selector");
            Pair pair = new Pair(selector.typeId(), selector.identifier());
            if (buckets.putIfAbsent(pair, new Bucket<>(pair)) != null) {
                throw new IllegalArgumentException(
                        "qualityItemSample repeats selector " + pair.display());
            }
        }
        if (buckets.isEmpty()) {
            throw new IllegalArgumentException(
                    "QualityItemCandidateIndex requires at least one selector");
        }
    }

    boolean requests(String typeId, String identifier) {
        return buckets.containsKey(new Pair(typeId, identifier));
    }

    void accept(
            String typeId,
            String identifier,
            String canonicalIdentity,
            T value) {
        Bucket<T> bucket = buckets.get(new Pair(typeId, identifier));
        if (bucket == null) {
            throw new IllegalArgumentException(
                    "Candidate does not match a requested qualityItemSample selector: "
                            + typeId + " " + identifier);
        }
        Objects.requireNonNull(canonicalIdentity, "canonicalIdentity");
        Objects.requireNonNull(value, "value");
        bucket.accept(canonicalIdentity, value);
        acceptedOccurrences++;
    }

    List<T> resolveExactlyOnce() {
        List<T> resolved = new ArrayList<>(buckets.size());
        for (Bucket<T> bucket : buckets.values()) {
            int distinct = bucket.valueByCanonicalIdentity.size();
            if (distinct != 1) {
                throw new IllegalStateException(
                        "qualityItemSample selector must resolve to exactly one distinct canonical "
                                + "catalog identity: " + bucket.pair.display()
                                + "; matches=" + distinct
                                + "; matchedOccurrences=" + bucket.matchedOccurrences);
            }
            resolved.add(bucket.valueByCanonicalIdentity.values().iterator().next());
        }
        return List.copyOf(resolved);
    }

    int selectorCount() {
        return buckets.size();
    }

    int acceptedOccurrences() {
        return acceptedOccurrences;
    }

    int distinctIdentityCount() {
        return buckets.values().stream()
                .mapToInt(bucket -> bucket.valueByCanonicalIdentity.size())
                .sum();
    }
}
