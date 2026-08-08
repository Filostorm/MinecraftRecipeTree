package com.recipetree.jeiexport112;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit, non-production recipe selection used to validate render quality before a full export. */
final class QualitySamplePlan {
    private static final int MAX_SOURCE_INDEX = 10_000_000;

    private final Map<String, List<RecipeTarget>> recipesByCategory;
    private final int recipeCount;
    private final int sourceIndexSelectorCount;
    private final int recipeIdSelectorCount;
    private final boolean scanAllItems;

    private QualitySamplePlan(Map<String, List<RecipeTarget>> recipesByCategory, int recipeCount,
                              int sourceIndexSelectorCount, int recipeIdSelectorCount,
                              boolean scanAllItems) {
        this.recipesByCategory = recipesByCategory;
        this.recipeCount = recipeCount;
        this.sourceIndexSelectorCount = sourceIndexSelectorCount;
        this.recipeIdSelectorCount = recipeIdSelectorCount;
        this.scanAllItems = scanAllItems;
    }

    static QualitySamplePlan parse(JsonElement value) throws IOException {
        if (value == null) {
            return null;
        }
        if (value.isJsonNull()) {
            throw new IOException("qualitySample must not be null; omit the field for a full export");
        }
        if (!value.isJsonObject()) {
            throw new IOException("qualitySample must be an object");
        }
        JsonObject object = value.getAsJsonObject();
        Set<String> sampleKeys = new HashSet<String>();
        sampleKeys.add("recipes");
        sampleKeys.add("scanAllItems");
        requireOnlyKeys(object, sampleKeys, "qualitySample");
        boolean scanAllItems = optionalBoolean(object, "scanAllItems", false);
        JsonElement recipesElement = object.get("recipes");
        if (recipesElement == null || !recipesElement.isJsonArray()) {
            throw new IOException("qualitySample.recipes must be a non-empty array");
        }
        JsonArray recipes = recipesElement.getAsJsonArray();
        if (recipes.size() == 0) {
            throw new IOException("qualitySample.recipes must be a non-empty array");
        }

        Map<String, List<RecipeTarget>> selections =
                new LinkedHashMap<String, List<RecipeTarget>>();
        Set<String> identities = new HashSet<String>();
        int sourceIndexCount = 0;
        int recipeIdCount = 0;
        for (int i = 0; i < recipes.size(); i++) {
            JsonElement entryElement = recipes.get(i);
            if (!entryElement.isJsonObject()) {
                throw new IOException("qualitySample.recipes[" + i + "] must be an object");
            }
            JsonObject entry = entryElement.getAsJsonObject();
            Set<String> allowed = new HashSet<String>();
            allowed.add("category");
            allowed.add("sourceIndex");
            allowed.add("recipeId");
            requireOnlyKeys(entry, allowed, "qualitySample.recipes[" + i + "]");

            String category = requiredString(entry, "category", i);
            boolean hasSourceIndex = entry.has("sourceIndex");
            boolean hasRecipeId = entry.has("recipeId");
            if (hasSourceIndex == hasRecipeId) {
                throw new IOException("qualitySample.recipes[" + i +
                        "] must contain exactly one of sourceIndex or recipeId");
            }

            RecipeTarget target;
            String identity;
            if (hasSourceIndex) {
                int sourceIndex = requiredSourceIndex(entry, i);
                target = RecipeTarget.forSourceIndex(sourceIndex);
                identity = category + '\u0000' + "sourceIndex" + '\u0000' + sourceIndex;
                sourceIndexCount++;
            } else {
                String recipeId = requiredRecipeId(entry, i);
                target = RecipeTarget.forRecipeId(recipeId);
                identity = category + '\u0000' + "recipeId" + '\u0000' + recipeId;
                recipeIdCount++;
            }
            if (!identities.add(identity)) {
                throw new IOException("qualitySample contains duplicate selector " + category +
                        " " + target.description());
            }
            List<RecipeTarget> targets = selections.get(category);
            if (targets == null) {
                targets = new ArrayList<RecipeTarget>();
                selections.put(category, targets);
            }
            targets.add(target);
        }
        for (Map.Entry<String, List<RecipeTarget>> entry : selections.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        return new QualitySamplePlan(Collections.unmodifiableMap(selections), recipes.size(),
                sourceIndexCount, recipeIdCount, scanAllItems);
    }

    private static boolean optionalBoolean(JsonObject object, String name, boolean fallback)
            throws IOException {
        JsonElement value = object.get(name);
        if (value == null) {
            return fallback;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IOException("qualitySample." + name + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static void requireOnlyKeys(JsonObject object, Set<String> allowed, String label)
            throws IOException {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw new IOException(label + " contains unsupported field " + entry.getKey());
            }
        }
    }

    private static String requiredString(JsonObject object, String name, int entryIndex)
            throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() ||
                !value.getAsJsonPrimitive().isString()) {
            throw new IOException("qualitySample.recipes[" + entryIndex + "]." + name +
                    " must be a non-empty string");
        }
        String result = value.getAsString().trim();
        if (result.isEmpty()) {
            throw new IOException("qualitySample.recipes[" + entryIndex + "]." + name +
                    " must be a non-empty string");
        }
        return result;
    }

    private static String requiredRecipeId(JsonObject object, int entryIndex) throws IOException {
        JsonElement value = object.get("recipeId");
        if (value == null || !value.isJsonPrimitive() ||
                !value.getAsJsonPrimitive().isString()) {
            throw invalidRecipeId(entryIndex);
        }
        String result = value.getAsString();
        int separator = result.indexOf(':');
        if (result.trim().isEmpty() || !result.equals(result.trim()) ||
                separator <= 0 || separator == result.length() - 1) {
            throw invalidRecipeId(entryIndex);
        }
        try {
            ResourceLocation resourceLocation = new ResourceLocation(result);
            if (!result.equals(resourceLocation.toString())) {
                throw invalidRecipeId(entryIndex);
            }
        } catch (RuntimeException error) {
            throw new IOException("qualitySample.recipes[" + entryIndex +
                    "].recipeId must be a canonical ResourceLocation", error);
        }
        return result;
    }

    private static IOException invalidRecipeId(int entryIndex) {
        return new IOException("qualitySample.recipes[" + entryIndex +
                "].recipeId must be a canonical ResourceLocation");
    }

    private static int requiredSourceIndex(JsonObject object, int entryIndex) throws IOException {
        JsonElement value = object.get("sourceIndex");
        if (value == null || !value.isJsonPrimitive() ||
                !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException("qualitySample.recipes[" + entryIndex +
                    "].sourceIndex must be an integer");
        }
        final int result;
        try {
            result = value.getAsInt();
        } catch (RuntimeException error) {
            throw new IOException("qualitySample.recipes[" + entryIndex +
                    "].sourceIndex must be an integer", error);
        }
        if (result < 0 || result > MAX_SOURCE_INDEX || value.getAsDouble() != result) {
            throw new IOException("qualitySample.recipes[" + entryIndex +
                    "].sourceIndex must be an integer in [0, " + MAX_SOURCE_INDEX + "]");
        }
        return result;
    }

    boolean includesCategory(String categoryUid) {
        return recipesByCategory.containsKey(categoryUid);
    }

    boolean requiresRecipeIds(String categoryUid) {
        List<RecipeTarget> targets = recipesByCategory.get(categoryUid);
        if (targets == null) {
            return false;
        }
        for (RecipeTarget target : targets) {
            if (target.recipeId != null) {
                return true;
            }
        }
        return false;
    }

    /** Resolves selectors in request order and refuses aliases that select the same source twice. */
    List<Integer> resolveSourceIndexes(String categoryUid, int sourceCount,
                                       List<String> sourceRecipeIds) throws IOException {
        List<RecipeTarget> targets = recipesByCategory.get(categoryUid);
        if (targets == null) {
            return Collections.emptyList();
        }
        if (sourceCount < 0 || (sourceRecipeIds != null && sourceRecipeIds.size() != sourceCount)) {
            throw new IllegalArgumentException("Invalid HEI recipe source metadata for " + categoryUid);
        }
        if (requiresRecipeIds(categoryUid) && sourceRecipeIds == null) {
            throw new IllegalArgumentException("Recipe registry names are required for " + categoryUid);
        }

        List<Integer> resolved = new ArrayList<Integer>(targets.size());
        Set<Integer> unique = new LinkedHashSet<Integer>();
        for (RecipeTarget target : targets) {
            final int sourceIndex;
            if (target.recipeId == null) {
                sourceIndex = target.sourceIndex;
                if (sourceIndex >= sourceCount) {
                    throw new IOException("Quality sample recipe " + categoryUid + " #" + sourceIndex +
                            " is outside the HEI source range 0.." + Math.max(-1, sourceCount - 1));
                }
            } else {
                int match = -1;
                int matches = 0;
                for (int i = 0; i < sourceRecipeIds.size(); i++) {
                    if (target.recipeId.equals(sourceRecipeIds.get(i))) {
                        match = i;
                        matches++;
                    }
                }
                if (matches == 0) {
                    throw new IOException("Quality sample recipeId " + target.recipeId +
                            " did not resolve in HEI category " + categoryUid);
                }
                if (matches != 1) {
                    throw new IOException("Quality sample recipeId " + target.recipeId +
                            " resolved to " + matches + " recipes in HEI category " + categoryUid);
                }
                sourceIndex = match;
            }
            if (!unique.add(sourceIndex)) {
                throw new IOException("Quality sample selectors for " + categoryUid +
                        " resolve to duplicate HEI source recipe #" + sourceIndex);
            }
            resolved.add(sourceIndex);
        }
        return Collections.unmodifiableList(resolved);
    }

    Set<String> categoryUids() {
        return recipesByCategory.keySet();
    }

    int recipeCount() {
        return recipeCount;
    }

    int sourceIndexSelectorCount() {
        return sourceIndexSelectorCount;
    }

    int recipeIdSelectorCount() {
        return recipeIdSelectorCount;
    }

    boolean scansAllItems() {
        return scanAllItems;
    }

    private static final class RecipeTarget {
        final int sourceIndex;
        final String recipeId;

        private RecipeTarget(int sourceIndex, String recipeId) {
            this.sourceIndex = sourceIndex;
            this.recipeId = recipeId;
        }

        static RecipeTarget forSourceIndex(int sourceIndex) {
            return new RecipeTarget(sourceIndex, null);
        }

        static RecipeTarget forRecipeId(String recipeId) {
            return new RecipeTarget(-1, recipeId);
        }

        String description() {
            return recipeId == null ? "sourceIndex #" + sourceIndex : "recipeId " + recipeId;
        }
    }
}
