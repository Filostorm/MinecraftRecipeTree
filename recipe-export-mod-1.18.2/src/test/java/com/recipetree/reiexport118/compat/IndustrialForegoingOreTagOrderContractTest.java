package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IndustrialForegoingOreTagOrderContractTest {
    @Test
    void exactMm2RawDomainIsPinnedAndLexicographicallyOrdered() {
        List<String> expected =
                IndustrialForegoingOreTagOrderContract.EXPECTED_VALID_RAW_TAG_IDS;
        assertEquals(46, expected.size());
        assertEquals("forge:raw_materials/adamantium", expected.get(0));
        assertEquals("forge:raw_materials/zinc", expected.get(expected.size() - 1));

        List<String> sorted = expected.stream().sorted().toList();
        assertEquals(sorted, expected);
        assertEquals(expected.size(), expected.stream().distinct().count());
    }

    @Test
    void unorderedFullTagSnapshotBecomesCanonicalWithoutDroppingUnrelatedTags() {
        List<String> source = new ArrayList<>(
                IndustrialForegoingOreTagOrderContract.EXPECTED_VALID_RAW_TAG_IDS);
        source.add("minecraft:wool");
        source.add("forge:raw_materials/no_matching_dust");
        source.add("forge:ingots/iron");
        Collections.reverse(source);

        IndustrialForegoingOreTagOrderContract.CanonicalOrder<String> canonical =
                IndustrialForegoingOreTagOrderContract.canonicalize(
                        source,
                        Function.identity(),
                        IndustrialForegoingOreTagOrderContract.EXPECTED_VALID_RAW_TAG_IDS
                                ::contains);

        assertEquals(source.stream().sorted().toList(), canonical.values());
        assertEquals(
                IndustrialForegoingOreTagOrderContract.EXPECTED_VALID_RAW_TAG_IDS,
                canonical.validRawTagIds());
        assertFalse(canonical.inputAlreadyCanonical());
        assertEquals(source.size(), canonical.values().size());
    }

    @Test
    void alreadyCanonicalSnapshotIsReportedWithoutChangingItsOrder() {
        List<String> source = new ArrayList<>(
                IndustrialForegoingOreTagOrderContract.EXPECTED_VALID_RAW_TAG_IDS);
        source.add("minecraft:wool");
        source.sort(String::compareTo);

        IndustrialForegoingOreTagOrderContract.CanonicalOrder<String> canonical =
                canonicalizeExact(source);

        assertEquals(source, canonical.values());
        assertTrue(canonical.inputAlreadyCanonical());
    }

    @Test
    void missingAndExtraValidRawTagsFailClosed() {
        List<String> missing = new ArrayList<>(
                IndustrialForegoingOreTagOrderContract.EXPECTED_VALID_RAW_TAG_IDS);
        missing.remove("forge:raw_materials/iron");
        IllegalStateException missingFailure = assertThrows(
                IllegalStateException.class,
                () -> canonicalizeExact(missing));
        assertTrue(missingFailure.getMessage().contains(
                "missing=[forge:raw_materials/iron]"));

        List<String> extra = new ArrayList<>(
                IndustrialForegoingOreTagOrderContract.EXPECTED_VALID_RAW_TAG_IDS);
        extra.add("forge:raw_materials/unexpected");
        IllegalStateException extraFailure = assertThrows(
                IllegalStateException.class,
                () -> IndustrialForegoingOreTagOrderContract.canonicalize(
                        extra,
                        Function.identity(),
                        id -> id.startsWith(
                                IndustrialForegoingOreTagOrderContract.RAW_MATERIAL_PREFIX)));
        assertTrue(extraFailure.getMessage().contains(
                "extra=[forge:raw_materials/unexpected]"));
    }

    @Test
    void nullBlankAndDuplicateSourceEntriesFailClosed() {
        List<String> exact =
                IndustrialForegoingOreTagOrderContract.EXPECTED_VALID_RAW_TAG_IDS;
        assertThrows(
                IllegalStateException.class,
                () -> IndustrialForegoingOreTagOrderContract.canonicalize(
                        null, Function.identity(), exact::contains));

        List<String> withNull = new ArrayList<>(exact);
        withNull.add(null);
        assertThrows(
                IllegalStateException.class,
                () -> IndustrialForegoingOreTagOrderContract.canonicalize(
                        withNull, Function.identity(), exact::contains));

        List<String> withBlank = new ArrayList<>(exact);
        withBlank.add(" ");
        assertThrows(
                IllegalStateException.class,
                () -> IndustrialForegoingOreTagOrderContract.canonicalize(
                        withBlank, Function.identity(), exact::contains));

        List<String> duplicate = new ArrayList<>(exact);
        duplicate.add(exact.get(0));
        IllegalStateException duplicateFailure = assertThrows(
                IllegalStateException.class,
                () -> IndustrialForegoingOreTagOrderContract.canonicalize(
                        duplicate, Function.identity(), exact::contains));
        assertTrue(duplicateFailure.getMessage().contains("duplicate ID="));
    }

    private static IndustrialForegoingOreTagOrderContract.CanonicalOrder<String>
            canonicalizeExact(List<String> source) {
        return IndustrialForegoingOreTagOrderContract.canonicalize(
                source,
                Function.identity(),
                IndustrialForegoingOreTagOrderContract.EXPECTED_VALID_RAW_TAG_IDS::contains);
    }
}
