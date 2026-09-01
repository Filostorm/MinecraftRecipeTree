package com.recipetree.jeiexport112;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class RecipeTreeProgressTest {
    @Test
    public void roundTripPreservesCompleteStateAndUnresolvedIdentities() throws Exception {
        String unresolvedIngredient = "custom:unresolved|meta=7|nbt=opaque";
        String unresolvedRecipe = "custom.machine|semantic:unresolved-recipe";

        Map<String, RecipeTreeProgress.SavedPlan> plans =
                new LinkedHashMap<String, RecipeTreeProgress.SavedPlan>();
        plans.put(unresolvedIngredient, new RecipeTreeProgress.SavedPlan(144, unresolvedRecipe));

        Map<String, String> favorites = new LinkedHashMap<String, String>();
        favorites.put(unresolvedIngredient, unresolvedRecipe);

        Map<String, Boolean> collapsedTypes = new LinkedHashMap<String, Boolean>();
        collapsedTypes.put("custom.machine", true);

        List<Integer> mutablePath = new ArrayList<Integer>(Arrays.asList(1, 3, 2));
        RecipeTreeProgress.RecipeHistorySelection selection =
                new RecipeTreeProgress.RecipeHistorySelection(
                        1,
                        mutablePath,
                        unresolvedIngredient,
                        "Unresolved Ingredient",
                        unresolvedRecipe,
                        "custom.machine",
                        true);
        List<RecipeTreeProgress.RecipeHistoryRoot> roots = Arrays.asList(
                new RecipeTreeProgress.RecipeHistoryRoot(
                        "minecraft:stone|meta=0", "Stone", "minecraft:crafting|stone", 64),
                new RecipeTreeProgress.RecipeHistoryRoot(
                        unresolvedIngredient, "Unknown", unresolvedRecipe, 144));
        RecipeTreeProgress.RecipeHistoryEntry snapshot =
                new RecipeTreeProgress.RecipeHistoryEntry(
                        unresolvedIngredient,
                        unresolvedRecipe,
                        144,
                        true,
                        7,
                        roots,
                        Collections.singletonList(selection),
                        true);

        Set<String> discoveries = new LinkedHashSet<String>();
        discoveries.add("minecraft:stone");
        discoveries.add("custom:unresolved-discovery");
        Set<String> reusableInputs = new LinkedHashSet<String>();
        reusableInputs.add("test-recipe-input-pair");
        Map<String, RecipeTreeProgress.WorldHistoryData> worldHistories =
                new LinkedHashMap<String, RecipeTreeProgress.WorldHistoryData>();
        worldHistories.put(
                "singleplayer:test-save",
                new RecipeTreeProgress.WorldHistoryData(
                        Collections.singletonList(snapshot),
                        snapshot));

        RecipeTreeProgress.StateData original = new RecipeTreeProgress.StateData(
                plans,
                favorites,
                collapsedTypes,
                Collections.singletonList(snapshot),
                snapshot,
                true,
                discoveries,
                reusableInputs,
                worldHistories);

        mutablePath.set(0, 99);
        String json = RecipeTreeProgress.serialize(original);
        RecipeTreeProgress.StateData restored = RecipeTreeProgress.deserialize(json);

        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"minecraftVersion\": \"1.12.2\""));
        assertTrue(json.contains("\"ingredientIdentityVersion\": 1"));
        assertEquals(new RecipeTreeProgress.SavedPlan(144, unresolvedRecipe),
                restored.plans().get(unresolvedIngredient));
        assertEquals(unresolvedRecipe, restored.favoriteRecipes().get(unresolvedIngredient));
        assertEquals(Boolean.TRUE, restored.collapsedRecipeTypes().get("custom.machine"));
        assertTrue(restored.recipeBookMode());
        assertTrue(restored.discoveries().contains("custom:unresolved-discovery"));
        assertTrue(restored.reusableInputs().contains("test-recipe-input-pair"));
        assertEquals(
                snapshot,
                restored.worldHistories()
                        .get("singleplayer:test-save")
                        .lastViewedRecipeTree());
        assertEquals(snapshot, restored.recipeHistory().get(0));
        assertEquals(snapshot, restored.lastViewedRecipeTree());
        assertEquals(Arrays.asList(1, 3, 2),
                restored.recipeHistory().get(0).getSelections().get(0).getPath());
        assertTrue(restored.recipeHistory().get(0).getSelections().get(0).isReusableInput());

        Map<String, String> returnedFavorites = restored.favoriteRecipes();
        returnedFavorites.clear();
        List<RecipeTreeProgress.RecipeHistoryEntry> returnedHistory = restored.recipeHistory();
        returnedHistory.clear();
        Set<String> returnedDiscoveries = restored.discoveries();
        returnedDiscoveries.clear();
        assertFalse(restored.favoriteRecipes().isEmpty());
        assertFalse(restored.recipeHistory().isEmpty());
        assertFalse(restored.discoveries().isEmpty());
    }

    @Test
    public void deserializationKeepsOnlyNewestThirtyTwoHistoryEntries() throws Exception {
        JsonObject envelope = compatibleEnvelope();
        JsonArray history = new JsonArray();
        for (int index = 0; index < 40; index++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("itemIdentity", "test:item-" + index);
            entry.addProperty("recipeIdentity", "test:recipe-" + index);
            entry.addProperty("amount", index + 1);
            entry.addProperty("compactMode", index % 2 == 0);
            entry.addProperty("treeDepth", index + 2);
            entry.add("roots", new JsonArray());
            entry.add("selections", new JsonArray());
            entry.addProperty("snapshot", index == 39);
            history.add(entry);
        }
        envelope.getAsJsonObject("data").add("recipeHistory", history);

        RecipeTreeProgress.StateData restored =
                RecipeTreeProgress.deserialize(envelope.toString());

        assertEquals(RecipeTreeProgress.MAX_HISTORY, restored.recipeHistory().size());
        assertEquals("test:item-8", restored.recipeHistory().get(0).getItemIdentity());
        assertEquals("test:item-39", restored.recipeHistory().get(31).getItemIdentity());
        assertTrue(restored.recipeHistory().get(31).isSnapshot());
    }

    @Test
    public void recentTreesAreIsolatedBetweenWorlds() {
        RecipeTreeProgress progress = new RecipeTreeProgress(
                null,
                new RecipeTreeProgress.StateData(
                        Collections.<String, RecipeTreeProgress.SavedPlan>emptyMap(),
                        Collections.<String, String>emptyMap(),
                        Collections.<String, Boolean>emptyMap(),
                        Collections.<RecipeTreeProgress.RecipeHistoryEntry>emptyList(),
                        null,
                        false,
                        Collections.<String>emptySet(),
                        Collections.<String>emptySet()),
                false);
        RecipeTreeProgress.RecipeHistoryEntry first = historyEntry("test:first");
        RecipeTreeProgress.RecipeHistoryEntry second = historyEntry("test:second");

        progress.setActiveWorld("singleplayer:first-save");
        progress.replaceRecipeHistory(Collections.singletonList(first), first);
        assertEquals(Collections.singletonList(first), progress.recipeHistory());
        assertEquals(first, progress.lastViewedRecipeTree());

        progress.setActiveWorld("singleplayer:second-save");
        assertTrue(progress.recipeHistory().isEmpty());
        assertNull(progress.lastViewedRecipeTree());
        progress.replaceRecipeHistory(Collections.singletonList(second), second);

        progress.setActiveWorld("singleplayer:first-save");
        assertEquals(Collections.singletonList(first), progress.recipeHistory());
        assertEquals(first, progress.lastViewedRecipeTree());
        progress.setActiveWorld("singleplayer:second-save");
        assertEquals(Collections.singletonList(second), progress.recipeHistory());
        assertEquals(second, progress.lastViewedRecipeTree());
    }

    @Test
    public void worldScopeUsesSaveFolderOrNormalizedServerAddress() {
        assertEquals(
                "singleplayer:World One",
                RecipeTreeClient.worldScopeKey(true, " World One ", null));
        assertEquals(
                "multiplayer:example.org:25565",
                RecipeTreeClient.worldScopeKey(
                        false,
                        null,
                        " Example.ORG:25565 "));
        assertNull(RecipeTreeClient.worldScopeKey(true, null, null));
        assertNull(RecipeTreeClient.worldScopeKey(false, null, null));
    }

    @Test
    public void rejectsEveryIncompatibleEnvelopeVersion() throws Exception {
        JsonObject wrongSchema = compatibleEnvelope();
        wrongSchema.addProperty("schemaVersion", RecipeTreeProgress.SCHEMA_VERSION + 1);
        expectFailure(wrongSchema.toString(), "schemaVersion");

        JsonObject wrongMinecraft = compatibleEnvelope();
        wrongMinecraft.addProperty("minecraftVersion", "1.20.1");
        expectFailure(wrongMinecraft.toString(), "expected 1.12.2");

        JsonObject wrongIdentity = compatibleEnvelope();
        wrongIdentity.addProperty(
                "ingredientIdentityVersion",
                RecipeTreeProgress.INGREDIENT_IDENTITY_VERSION + 1);
        expectFailure(wrongIdentity.toString(), "ingredientIdentityVersion");
    }

    private static JsonObject compatibleEnvelope() {
        JsonObject data = new JsonObject();
        data.add("plans", new JsonObject());
        data.add("favoriteRecipes", new JsonObject());
        data.add("collapsedRecipeTypes", new JsonObject());
        data.add("recipeHistory", new JsonArray());
        data.add("lastViewedRecipeTree", null);
        data.addProperty("recipeBookMode", false);
        data.add("discoveries", new JsonArray());

        JsonObject envelope = new JsonObject();
        envelope.addProperty("schemaVersion", RecipeTreeProgress.SCHEMA_VERSION);
        envelope.addProperty("minecraftVersion", RecipeTreeProgress.MINECRAFT_VERSION);
        envelope.addProperty(
                "ingredientIdentityVersion",
                RecipeTreeProgress.INGREDIENT_IDENTITY_VERSION);
        envelope.add("data", data);
        return envelope;
    }

    private static RecipeTreeProgress.RecipeHistoryEntry historyEntry(String identity) {
        return new RecipeTreeProgress.RecipeHistoryEntry(
                identity,
                "test:recipe|" + identity,
                1,
                true,
                2,
                Collections.<RecipeTreeProgress.RecipeHistoryRoot>emptyList(),
                Collections.<RecipeTreeProgress.RecipeHistorySelection>emptyList(),
                false);
    }

    private static void expectFailure(String json, String messageFragment) throws Exception {
        try {
            RecipeTreeProgress.deserialize(json);
            fail("Expected IOException containing " + messageFragment);
        } catch (IOException expected) {
            assertTrue(
                    "Expected '" + messageFragment + "' in '" + expected.getMessage() + "'",
                    expected.getMessage().contains(messageFragment));
        }
    }
}
