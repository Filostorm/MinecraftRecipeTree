package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IndustrialForegoingRecipeListOrderContractTest {
    private static final List<String> LASER_IDS = List.of(
            "forge:kjs_3djb1afxcix2dwl1vq5g6zp4o",
            "forge:kjs_4a8itksrc9jhr3xyhudw3azn5",
            "forge:kjs_5xiwc6cw3rjmklczygjstpdii",
            "forge:kjs_5ytx6jn4vpqb95k8veyafki22",
            "forge:kjs_ci5hnwiwkivkrlujaldux3iq5",
            "forge:kjs_f0q9rkyx5mn0k5us5fy8ersx9");

    @Test
    void exactSixCallDomainAndTitaniumPreflightArePinned() {
        assertEquals(6,
                IndustrialForegoingRecipeListOrderContract.EXPECTED_GET_RECIPES_CALLS);
        assertEquals(6,
                IndustrialForegoingRecipeListOrderContract.EXPECTED_RECIPE_LISTS.size());
        assertEquals(127,
                IndustrialForegoingRecipeListOrderContract.EXPECTED_RECIPE_LISTS.stream()
                        .mapToInt(IndustrialForegoingRecipeListOrderContract
                                .RecipeListExpectation::recipeCount)
                        .sum());
        assertEquals(List.of(
                        "industrialforegoing:fluid_extractor",
                        "industrialforegoing:dissolution_chamber",
                        "industrialforegoing:laser_drill_ore",
                        "industrialforegoing:laser_drill_fluid",
                        "industrialforegoing:stonework_generate",
                        "industrialforegoing:stonework_generate"),
                IndustrialForegoingRecipeListOrderContract.EXPECTED_RECIPE_LISTS.stream()
                        .map(IndustrialForegoingRecipeListOrderContract
                        .RecipeListExpectation::recipeTypeId)
                        .toList());
        assertEquals(List.of(
                        "fabea695795b1bff38d1f5a86d719fe97891c25009787e21b0340b20abecc775",
                        "d27881f6a0c1d2aecbc23efadf297ce3f64ede2e2e07ab0d4e864c40939a60a8",
                        "042bb102c7673c5fed795be5c1aa214dedfd35de5a2626aec427bec61762d095",
                        "fc42b6e58243f96229855ddacd3e628927bc88b52400ecc463d12d77e102de2b",
                        "011a71fb43188cefa80dd517179b59a83dd7215d0414ef0801f91fb5041970fe",
                        "011a71fb43188cefa80dd517179b59a83dd7215d0414ef0801f91fb5041970fe"),
                IndustrialForegoingRecipeListOrderContract.EXPECTED_RECIPE_LISTS.stream()
                        .map(IndustrialForegoingRecipeListOrderContract
                                .RecipeListExpectation::orderedIdSha256)
                        .toList());
        assertEquals("3.5.11", Mm2DeterminismContract.TITANIUM.version());
        assertTrue(Mm2DeterminismContract.LIFECYCLE_SIGNATURE.contains(
                Mm2DeterminismContract.TITANIUM));
        assertTrue(Mm2DeterminismContract.CLASS_PINS.contains(
                Mm2DeterminismContract.TITANIUM_RECIPE_UTIL));
    }

    @Test
    void everyPermutationCanonicalizesWithoutMutatingItsSource() {
        for (int seed = 0; seed < 100; seed++) {
            List<String> source = new ArrayList<>(LASER_IDS);
            Collections.shuffle(source, new Random(seed));
            List<String> snapshot = List.copyOf(source);
            IndustrialForegoingRecipeListOrderContract.CanonicalRecipeOrder<String> result =
                    IndustrialForegoingRecipeListOrderContract.canonicalize(
                            source,
                            Function.identity());

            assertEquals(LASER_IDS, result.values(), "seed=" + seed);
            assertEquals(LASER_IDS, result.orderedIds(), "seed=" + seed);
            assertEquals(snapshot, source, "source list was mutated at seed=" + seed);
            assertEquals(
                    IndustrialForegoingRecipeListOrderContract.EXPECTED_LASER_ORE_IDS_SHA256,
                    result.orderedIdSha256());
            assertEquals(snapshot.equals(LASER_IDS), result.inputAlreadyCanonical());
            assertThrows(UnsupportedOperationException.class,
                    () -> result.values().add("forge:forbidden"));
        }
    }

    @Test
    void invalidEntriesAndDuplicateIdsFailClosed() {
        assertThrows(IllegalStateException.class,
                () -> IndustrialForegoingRecipeListOrderContract.canonicalize(
                        null, Function.identity()));
        assertThrows(IllegalStateException.class,
                () -> IndustrialForegoingRecipeListOrderContract.canonicalize(
                        LASER_IDS, null));

        List<String> withNull = new ArrayList<>(LASER_IDS);
        withNull.add(null);
        assertThrows(IllegalStateException.class,
                () -> IndustrialForegoingRecipeListOrderContract.canonicalize(
                        withNull, Function.identity()));

        List<String> withBlank = new ArrayList<>(LASER_IDS);
        withBlank.add(" ");
        assertThrows(IllegalStateException.class,
                () -> IndustrialForegoingRecipeListOrderContract.canonicalize(
                        withBlank, Function.identity()));

        List<String> duplicate = new ArrayList<>(LASER_IDS);
        duplicate.add(LASER_IDS.get(0));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> IndustrialForegoingRecipeListOrderContract.canonicalize(
                        duplicate, Function.identity()));
        assertTrue(failure.getMessage().contains("duplicate ID="));
    }

    @Test
    void canonicalInputIsRecognizedAndReverseInputIsNot() {
        IndustrialForegoingRecipeListOrderContract.CanonicalRecipeOrder<String> canonical =
                IndustrialForegoingRecipeListOrderContract.canonicalize(
                        LASER_IDS, Function.identity());
        assertTrue(canonical.inputAlreadyCanonical());

        List<String> reverse = new ArrayList<>(LASER_IDS);
        Collections.reverse(reverse);
        IndustrialForegoingRecipeListOrderContract.CanonicalRecipeOrder<String> reordered =
                IndustrialForegoingRecipeListOrderContract.canonicalize(
                        reverse, Function.identity());
        assertFalse(reordered.inputAlreadyCanonical());
        assertEquals(canonical.orderedIdSha256(), reordered.orderedIdSha256());
    }
}
