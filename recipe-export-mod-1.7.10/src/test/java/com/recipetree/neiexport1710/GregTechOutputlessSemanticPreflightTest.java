package com.recipetree.neiexport1710;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public final class GregTechOutputlessSemanticPreflightTest {
    private static final String DOMAIN = "gregtech-test-semantic-rows-v2";

    @Test
    public void promotedMultisetIsInvariantToPermutationAndSourceReindexing() {
        String fuelRow = "classification=GREGTECH_FUEL_SINK;fingerprint=fuel-a";
        String radioRow = "classification=RADIO_HATCH_INFORMATION;fingerprint=radio-b";
        String spaceRow = "classification=SPACE_PROJECT_INFORMATION;fingerprint=space-c";

        SortedMap<GregTechOutputlessSemanticPreflight.SourceKey, String> firstBindings =
                new TreeMap<GregTechOutputlessSemanticPreflight.SourceKey, String>();
        firstBindings.put(source("gtnh:fuel", 2), fuelRow);
        firstBindings.put(source("gtnh:fuel", 9), radioRow);
        firstBindings.put(source("gtnh:fuel", 14), spaceRow);

        SortedMap<GregTechOutputlessSemanticPreflight.SourceKey, String> reindexedBindings =
                new TreeMap<GregTechOutputlessSemanticPreflight.SourceKey, String>();
        reindexedBindings.put(source("gtnh:fuel", 2), spaceRow);
        reindexedBindings.put(source("gtnh:fuel", 9), fuelRow);
        reindexedBindings.put(source("gtnh:fuel", 14), radioRow);

        assertEquals(
                GregTechOutputlessSemanticPreflight.stableMultisetFingerprint(
                        DOMAIN, new ArrayList<String>(firstBindings.values())),
                GregTechOutputlessSemanticPreflight.stableMultisetFingerprint(
                        DOMAIN, new ArrayList<String>(reindexedBindings.values())));

        // SourceKey remains an exact same-run lookup binding even though it is absent from the
        // promoted digest.
        assertEquals(fuelRow, firstBindings.get(source("gtnh:fuel", 2)));
        assertEquals(spaceRow, reindexedBindings.get(source("gtnh:fuel", 2)));
        assertNull(firstBindings.get(source("gtnh:fuel", 3)));
        assertNotEquals(source("gtnh:fuel", 2), source("gtnh:fuel", 9));
    }

    @Test
    public void promotedMultisetChangesWhenOneSemanticRowChanges() {
        List<String> baseline = Arrays.asList(
                "classification=GREGTECH_FUEL_SINK;fingerprint=fuel-a",
                "classification=RADIO_HATCH_INFORMATION;fingerprint=radio-b");
        List<String> mutated = Arrays.asList(
                "classification=GREGTECH_FUEL_SINK;fingerprint=fuel-a",
                "classification=RADIO_HATCH_INFORMATION;fingerprint=radio-mutated");

        assertNotEquals(
                GregTechOutputlessSemanticPreflight.stableMultisetFingerprint(DOMAIN, baseline),
                GregTechOutputlessSemanticPreflight.stableMultisetFingerprint(DOMAIN, mutated));
    }

    @Test
    public void promotedMultisetPreservesDuplicateMultiplicity() {
        List<String> once = Arrays.asList("row-a", "row-b");
        List<String> duplicated = Arrays.asList("row-a", "row-b", "row-a");
        List<String> permutedDuplicate = Arrays.asList("row-a", "row-a", "row-b");

        String onceFingerprint =
                GregTechOutputlessSemanticPreflight.stableMultisetFingerprint(DOMAIN, once);
        String duplicateFingerprint =
                GregTechOutputlessSemanticPreflight.stableMultisetFingerprint(DOMAIN, duplicated);
        assertNotEquals(onceFingerprint, duplicateFingerprint);
        assertEquals(
                duplicateFingerprint,
                GregTechOutputlessSemanticPreflight.stableMultisetFingerprint(
                        DOMAIN, permutedDuplicate));
    }

    @Test
    public void graphExclusionMultisetIsOrderIndependentAndPolicyIsolated() {
        List<String> first = Arrays.asList("door-row-a", "door-row-b", "door-row-a");
        List<String> permuted = Arrays.asList("door-row-a", "door-row-a", "door-row-b");
        List<String> withoutDuplicate = Arrays.asList("door-row-a", "door-row-b");

        String graphFingerprint = GregTechOutputlessSemanticPreflight
                .stableGraphIdentityExclusionFingerprint(first);
        assertEquals(graphFingerprint, GregTechOutputlessSemanticPreflight
                .stableGraphIdentityExclusionFingerprint(permuted));
        assertNotEquals(graphFingerprint, GregTechOutputlessSemanticPreflight
                .stableGraphIdentityExclusionFingerprint(withoutDuplicate));
        assertNotEquals(graphFingerprint,
                GregTechOutputlessSemanticPreflight.stableMultisetFingerprint(
                        "gregtech-unregistered-itemdoor-recycling-rows-v1", first));
    }

    @Test
    public void immutableReleasePinsPromotedV2CorpusDigest() {
        assertEquals(
                "gregtech-outputless-semantic-preflight-v2",
                GregTechOutputlessSemanticPreflight.CONTRACT);
        assertEquals(
                "7950c0741cb841a857428e327f407d0c8303954b0d6aa7a36a9189e30ea350f9",
                GregTechOutputlessSemanticPreflight.EXPECTED_SHA256);
        assertNotEquals(
                "0000000000000000000000000000000000000000000000000000000000000000",
                GregTechOutputlessSemanticPreflight.EXPECTED_SHA256);
    }

    @Test
    public void immutableReleasePinsExactUnregisteredDoorRecyclingCorpus() {
        assertEquals(
                "gregtech-unregistered-itemdoor-recycling-exclusion-v1",
                GregTechOutputlessSemanticPreflight.UNREGISTERED_DOOR_RECYCLING_CONTRACT);
        assertEquals(
                5,
                GregTechOutputlessSemanticPreflight.EXPECTED_UNREGISTERED_DOOR_RECYCLING_ROWS);
        assertEquals(
                3,
                GregTechOutputlessSemanticPreflight.EXPECTED_UNREGISTERED_DOOR_RECYCLING_CATEGORIES);
        assertEquals(
                "9724fc0858ae37da34cfd09c87fee0507c7588f794ecf0bc8c2f0cda9dd48815",
                GregTechOutputlessSemanticPreflight.EXPECTED_UNREGISTERED_DOOR_RECYCLING_SHA256);
        assertNotEquals(
                "0000000000000000000000000000000000000000000000000000000000000000",
                GregTechOutputlessSemanticPreflight.EXPECTED_UNREGISTERED_DOOR_RECYCLING_SHA256);
        assertNotEquals(
                "UNPROMOTED",
                GregTechOutputlessSemanticPreflight.EXPECTED_UNREGISTERED_DOOR_RECYCLING_SHA256);
    }

    private static GregTechOutputlessSemanticPreflight.SourceKey source(
            String categoryId, int sourceIndex) {
        return new GregTechOutputlessSemanticPreflight.SourceKey(categoryId, sourceIndex);
    }
}
