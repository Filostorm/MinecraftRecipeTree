package com.recipetree.reiexport118;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RegistryCensusTest {
    @Test
    void categoryDigestIsOrderIndependentAndCountSensitive() {
        Map<String, Integer> forward = new LinkedHashMap<>();
        forward.put("jeresources:plant", 49);
        forward.put("jeresources:mob", 129);
        forward.put("jeresources:dungeon", 21);

        Map<String, Integer> reverse = new LinkedHashMap<>();
        reverse.put("jeresources:dungeon", 21);
        reverse.put("jeresources:mob", 129);
        reverse.put("jeresources:plant", 49);

        assertEquals(
                RegistryCensus.digestCategoryCounts(forward),
                RegistryCensus.digestCategoryCounts(reverse));
        assertNotEquals(
                RegistryCensus.digestCategoryCounts(forward),
                RegistryCensus.digestCategoryCounts(Map.of(
                        "jeresources:plant", 49,
                        "jeresources:mob", 128,
                        "jeresources:dungeon", 21)));
        assertEquals(
                "a0901c8fde4b86532766b4d648a8328d8796673791ae375ec8566fd77234f7be",
                RegistryCensus.digestCategoryCounts(forward),
                "The accepted MRT_REI_CATEGORY_COUNTS_V1 contract must not change");
    }

    @Test
    void entryDigestIsOrderIndependentButPreservesMultiplicityAndIdentity() {
        RegistryCensus.EntryIdentity itemA = RegistryCensus.EntryIdentity.serialized("item:a");
        RegistryCensus.EntryIdentity itemB = RegistryCensus.EntryIdentity.serialized("item:b");
        RegistryCensus.EntryIdentity itemC = RegistryCensus.EntryIdentity.serialized("item:c");
        String digest = RegistryCensus.digestEntryIdentities(List.of(itemA, itemB));

        assertEquals(
                digest,
                RegistryCensus.digestEntryIdentities(List.of(itemB, itemA)));
        assertNotEquals(
                digest,
                RegistryCensus.digestEntryIdentities(List.of(itemA, itemA, itemB)));
        assertNotEquals(
                digest,
                RegistryCensus.digestEntryIdentities(List.of(itemA, itemC)));
        assertEquals(
                "17e3c3c1813fe5e237cfd611ec9688674575f263c709320afdac4d3d49ac89fd",
                digest,
                "The accepted MRT_REI_ENTRY_IDENTITIES_V2 contract must not change");
    }

    @Test
    void entryDigestRepresentsEveryEmptySentinelWithoutCollidingWithSerializedEntries() {
        RegistryCensus.EntryIdentity empty = RegistryCensus.EntryIdentity.empty();
        RegistryCensus.EntryIdentity serializedEmptyLabel =
                RegistryCensus.EntryIdentity.serialized("EMPTY");

        assertNotEquals(
                RegistryCensus.digestEntryIdentities(List.of(serializedEmptyLabel)),
                RegistryCensus.digestEntryIdentities(List.of(empty)));
        assertNotEquals(
                RegistryCensus.digestEntryIdentities(List.of(serializedEmptyLabel, empty)),
                RegistryCensus.digestEntryIdentities(List.of(serializedEmptyLabel, empty, empty)));
    }

    @Test
    void deepSummaryReportsTheExplicitEmptySentinelCount() {
        RegistryCensus.Counts counts = new RegistryCensus.Counts(
                10, 20, 1, "0".repeat(64), Map.of("minecraft:plugins/crafting", 20));
        RegistryCensus.Deep deep = new RegistryCensus.Deep(counts, 2, "1".repeat(64));

        assertTrue(deep.summary().contains("emptyEntries=2"));
    }

    @Test
    void namespaceSummaryKeepsOnlyTheRequestedCategoryCorpus() {
        RegistryCensus.Counts counts = new RegistryCensus.Counts(
                10,
                20,
                3,
                "0".repeat(64),
                Map.of(
                        "jeresources:mob", 129,
                        "jeresources:plant", 49,
                        "minecraft:plugins/crafting", 100));

        assertEquals(
                Map.of("jeresources:mob", 129, "jeresources:plant", 49),
                counts.namespaceCounts("jeresources"));
    }

    @Test
    void diagnosticIdentityCorpusIsOrderIndependentAndPreservesMultiplicity() {
        RegistryCensus.ObservedIdentity first = RegistryCensus.ObservedIdentity.serialized(
                "minecraft:item", "example:widget", "canonical-a");
        RegistryCensus.ObservedIdentity second = RegistryCensus.ObservedIdentity.serialized(
                "minecraft:item", "example:widget", "canonical-b");

        List<RegistryCensus.DiagnosticIdentity> forward = RegistryCensus.diagnosticIdentities(
                List.of(first, second, first));
        List<RegistryCensus.DiagnosticIdentity> reverse = RegistryCensus.diagnosticIdentities(
                List.of(first, first, second));

        assertEquals(forward, reverse);
        assertEquals(2, forward.size());
        assertEquals(3, forward.stream()
                .mapToInt(RegistryCensus.DiagnosticIdentity::multiplicity)
                .sum());
        assertTrue(forward.stream().anyMatch(identity -> identity.multiplicity() == 2));
    }

    @Test
    void diagnosticIdentityHashLocalizesCanonicalMutationToTheSameEntryKey() {
        RegistryCensus.ObservedIdentity before = RegistryCensus.ObservedIdentity.serialized(
                "minecraft:item", "example:widget", "canonical-nbt-before");
        RegistryCensus.ObservedIdentity after = RegistryCensus.ObservedIdentity.serialized(
                "minecraft:item", "example:widget", "canonical-nbt-after");

        RegistryCensus.DiagnosticIdentity beforeDiagnostic =
                RegistryCensus.diagnosticIdentities(List.of(before)).get(0);
        RegistryCensus.DiagnosticIdentity afterDiagnostic =
                RegistryCensus.diagnosticIdentities(List.of(after)).get(0);

        assertEquals(beforeDiagnostic.key(), afterDiagnostic.key());
        assertNotEquals(beforeDiagnostic.identitySha256(), afterDiagnostic.identitySha256());
    }

    @Test
    void diagnosticEmptySentinelCannotCollideWithSerializedEmptyLabel() {
        RegistryCensus.DiagnosticIdentity empty = RegistryCensus.diagnosticIdentities(
                List.of(RegistryCensus.ObservedIdentity.empty())).get(0);
        RegistryCensus.DiagnosticIdentity serialized = RegistryCensus.diagnosticIdentities(
                List.of(RegistryCensus.ObservedIdentity.serialized(
                        "minecraft:item", "example:empty", "EMPTY"))).get(0);

        assertNotEquals(empty.key(), serialized.key());
        assertNotEquals(empty.identitySha256(), serialized.identitySha256());
    }

    @Test
    void diagnosticSnapshotSortsAndDefensivelyCopiesItsIdentityCorpus() {
        RegistryCensus.ObservedIdentity first = RegistryCensus.ObservedIdentity.serialized(
                "minecraft:item", "example:a", "a");
        RegistryCensus.ObservedIdentity second = RegistryCensus.ObservedIdentity.serialized(
                "minecraft:item", "example:b", "b");
        List<RegistryCensus.DiagnosticIdentity> mutable = new ArrayList<>(
                RegistryCensus.diagnosticIdentities(List.of(second, first)));
        RegistryCensus.Counts counts = new RegistryCensus.Counts(
                2, 1, 1, RegistryCensus.digestCategoryCounts(Map.of("example:test", 1)),
                Map.of("example:test", 1));

        RegistryCensus.DiagnosticSnapshot snapshot = new RegistryCensus.DiagnosticSnapshot(
                counts, 0, mutable);
        mutable.clear();

        assertEquals(2, snapshot.identities().size());
        assertEquals("example:a", snapshot.identities().get(0).key().identifier());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.identities().clear());
    }
}
