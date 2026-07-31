package com.recipetree.reiexport118.compat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Exact MM2 contract for the six unordered Titanium recipe-list reads made by IF's JEI plugin. */
public final class IndustrialForegoingRecipeListOrderContract {
    public static final String TARGET_CLASS = IndustrialForegoingOreTagOrderContract.TARGET_CLASS;
    public static final String TARGET_RESOURCE =
            IndustrialForegoingOreTagOrderContract.TARGET_RESOURCE;
    public static final String TARGET_CLASS_SHA256 =
            IndustrialForegoingOreTagOrderContract.TARGET_CLASS_SHA256;
    public static final String REGISTER_RECIPES =
            IndustrialForegoingOreTagOrderContract.REGISTER_RECIPES;
    public static final String GET_RECIPES_TARGET =
            "Lcom/hrznstudio/titanium/util/RecipeUtil;getRecipes("
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/item/crafting/RecipeType;)Ljava/util/List;";
    public static final int EXPECTED_GET_RECIPES_CALLS = 6;

    /** Exact bytecode invocation order and pack-specific source cardinalities. */
    public static final List<RecipeListExpectation> EXPECTED_RECIPE_LISTS = List.of(
            new RecipeListExpectation(
                    "industrialforegoing:fluid_extractor", 7,
                    "fabea695795b1bff38d1f5a86d719fe97891c25009787e21b0340b20abecc775"),
            new RecipeListExpectation(
                    "industrialforegoing:dissolution_chamber", 99,
                    "d27881f6a0c1d2aecbc23efadf297ce3f64ede2e2e07ab0d4e864c40939a60a8"),
            new RecipeListExpectation(
                    "industrialforegoing:laser_drill_ore", 6,
                    "042bb102c7673c5fed795be5c1aa214dedfd35de5a2626aec427bec61762d095"),
            new RecipeListExpectation(
                    "industrialforegoing:laser_drill_fluid", 3,
                    "fc42b6e58243f96229855ddacd3e628927bc88b52400ecc463d12d77e102de2b"),
            new RecipeListExpectation(
                    "industrialforegoing:stonework_generate", 6,
                    "011a71fb43188cefa80dd517179b59a83dd7215d0414ef0801f91fb5041970fe"),
            new RecipeListExpectation(
                    "industrialforegoing:stonework_generate", 6,
                    "011a71fb43188cefa80dd517179b59a83dd7215d0414ef0801f91fb5041970fe"));
    public static final String EXPECTED_LASER_ORE_IDS_SHA256 =
            EXPECTED_RECIPE_LISTS.get(2).orderedIdSha256();

    private IndustrialForegoingRecipeListOrderContract() {
    }

    /** Copies and sorts without mutating Minecraft's RecipeManager-owned source list. */
    public static <T> CanonicalRecipeOrder<T> canonicalize(
            List<T> source,
            Function<? super T, String> idExtractor
    ) {
        if (source == null) {
            throw new IllegalStateException("Industrial Foregoing recipe source is null");
        }
        if (idExtractor == null) {
            throw new IllegalStateException("Industrial Foregoing recipe-ID extractor is null");
        }

        List<KeyedValue<T>> keyed = new ArrayList<>(source.size());
        Set<String> uniqueIds = new HashSet<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            T value = source.get(index);
            if (value == null) {
                throw new IllegalStateException(
                        "Industrial Foregoing recipe source contains null at index=" + index);
            }
            String id = idExtractor.apply(value);
            if (id == null || id.isBlank()) {
                throw new IllegalStateException(
                        "Industrial Foregoing recipe ID is blank at index=" + index);
            }
            if (!uniqueIds.add(id)) {
                throw new IllegalStateException(
                        "Industrial Foregoing recipe source contains duplicate ID=" + id);
            }
            keyed.add(new KeyedValue<>(id, value));
        }

        List<KeyedValue<T>> ordered = new ArrayList<>(keyed);
        ordered.sort(Comparator.comparing(KeyedValue::id));
        List<T> orderedValues = new ArrayList<>(ordered.size());
        List<String> orderedIds = new ArrayList<>(ordered.size());
        boolean inputAlreadyCanonical = true;
        for (int index = 0; index < ordered.size(); index++) {
            KeyedValue<T> entry = ordered.get(index);
            orderedValues.add(entry.value());
            orderedIds.add(entry.id());
            if (!entry.id().equals(keyed.get(index).id())) {
                inputAlreadyCanonical = false;
            }
            if (index > 0 && orderedIds.get(index - 1).compareTo(entry.id()) >= 0) {
                throw new IllegalStateException(
                        "Industrial Foregoing canonical recipe IDs are not strictly increasing"
                                + " at index=" + index);
            }
        }
        return new CanonicalRecipeOrder<>(
                List.copyOf(orderedValues),
                List.copyOf(orderedIds),
                inputAlreadyCanonical,
                orderedIdSha256(orderedIds));
    }

    /** SHA-256 over UTF-8 recipe IDs delimited by NUL, matching other MM2 sequence contracts. */
    public static String orderedIdSha256(List<String> orderedIds) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String id : orderedIds) {
                digest.update(id.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record KeyedValue<T>(String id, T value) {
    }

    public record RecipeListExpectation(
            String recipeTypeId,
            int recipeCount,
            String orderedIdSha256
    ) {
    }

    public record CanonicalRecipeOrder<T>(
            List<T> values,
            List<String> orderedIds,
            boolean inputAlreadyCanonical,
            String orderedIdSha256
    ) {
    }
}
