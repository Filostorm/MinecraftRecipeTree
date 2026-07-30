package com.recipetree.reiexport118;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QualityItemCandidateIndexTest {
    @Test
    void resolvesInSelectorOrderAndIgnoresScanOrder() {
        QualityItemCandidateIndex<String> index = index(
                selector("minecraft:fluid", "thermal:redstone"),
                selector("minecraft:item", "ae2:cable_bus"));

        index.accept("minecraft:item", "ae2:cable_bus", "{id:cable}", "cable");
        index.accept("minecraft:fluid", "thermal:redstone", "{id:redstone}", "redstone");

        assertEquals(List.of("redstone", "cable"), index.resolveExactlyOnce());
        assertEquals(2, index.selectorCount());
        assertEquals(2, index.acceptedOccurrences());
        assertEquals(2, index.distinctIdentityCount());
    }

    @Test
    void deduplicatesRepeatedOccurrencesOfExactCanonicalIdentity() {
        QualityItemCandidateIndex<String> index = index(
                selector("minecraft:item", "ae2:cable_bus"));

        index.accept("minecraft:item", "ae2:cable_bus", "{same:1}", "first");
        index.accept("minecraft:item", "ae2:cable_bus", "{same:1}", "second");

        assertEquals(List.of("first"), index.resolveExactlyOnce());
        assertEquals(2, index.acceptedOccurrences());
        assertEquals(1, index.distinctIdentityCount());
    }

    @Test
    void rejectsSelectorMissingFromEntireCanonicalDomain() {
        QualityItemCandidateIndex<String> index = index(
                selector("minecraft:item", "ae2:cable_bus"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                index::resolveExactlyOnce);

        assertTrue(failure.getMessage().contains("minecraft:item ae2:cable_bus"));
        assertTrue(failure.getMessage().contains("matches=0"));
    }

    @Test
    void rejectsMultipleDistinctCanonicalVariantsOfSamePair() {
        QualityItemCandidateIndex<String> index = index(
                selector("minecraft:item", "example:variant"));
        index.accept("minecraft:item", "example:variant", "{variant:1}", "one");
        index.accept("minecraft:item", "example:variant", "{variant:2}", "two");
        index.accept("minecraft:item", "example:variant", "{variant:2}", "two-repeat");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                index::resolveExactlyOnce);

        assertTrue(failure.getMessage().contains("matches=2"));
        assertTrue(failure.getMessage().contains("matchedOccurrences=3"));
    }

    @Test
    void rejectsCrossedAndDuplicateSelectorsExplicitly() {
        QualityItemCandidateIndex<String> index = index(
                selector("minecraft:item", "ae2:cable_bus"));
        assertThrows(
                IllegalArgumentException.class,
                () -> index.accept(
                        "minecraft:item", "ae2:paint", "{}", "paint"));

        IllegalArgumentException duplicate = assertThrows(
                IllegalArgumentException.class,
                () -> index(
                        selector("minecraft:item", "ae2:cable_bus"),
                        selector("minecraft:item", "ae2:cable_bus")));
        assertTrue(duplicate.getMessage().contains("repeats selector"));
    }

    @SafeVarargs
    private static QualityItemCandidateIndex<String> index(
            ExportRequest.ItemSample... selectors) {
        return new QualityItemCandidateIndex<>(List.of(selectors));
    }

    private static ExportRequest.ItemSample selector(String typeId, String identifier) {
        return new ExportRequest.ItemSample(typeId, identifier);
    }
}
