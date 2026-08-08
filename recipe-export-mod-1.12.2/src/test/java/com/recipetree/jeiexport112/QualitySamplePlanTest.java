package com.recipetree.jeiexport112;

import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class QualitySamplePlanTest {
    @Test
    public void absentPlanKeepsTheFullExportPath() throws Exception {
        assertNull(QualitySamplePlan.parse(null));
    }

    @Test
    public void selectionsRetainRequestAndFirstCategoryOrder() throws Exception {
        QualitySamplePlan plan = parse("{\"recipes\":[" +
                "{\"category\":\"minecraft.crafting\",\"sourceIndex\":31319}," +
                "{\"category\":\"extendedcrafting:table_crafting_3x3\",\"sourceIndex\":131}," +
                "{\"category\":\"minecraft.crafting\",\"sourceIndex\":5}]}");

        assertEquals(3, plan.recipeCount());
        assertEquals(3, plan.sourceIndexSelectorCount());
        assertEquals(0, plan.recipeIdSelectorCount());
        assertFalse(plan.scansAllItems());
        assertTrue(plan.includesCategory("minecraft.crafting"));
        assertFalse(plan.includesCategory("missing"));
        assertEquals(Arrays.asList("minecraft.crafting", "extendedcrafting:table_crafting_3x3"),
                new ArrayList<String>(plan.categoryUids()));
        assertEquals(Arrays.asList(31319, 5), plan.resolveSourceIndexes(
                "minecraft.crafting", 40000, null));
        assertEquals(Arrays.asList(131), plan.resolveSourceIndexes(
                "extendedcrafting:table_crafting_3x3", 200, null));
    }

    @Test
    public void diagnosticSampleMayReproduceFullItemCatalogSideEffects() throws Exception {
        QualitySamplePlan plan = parse("{\"scanAllItems\":true,\"recipes\":[{" +
                "\"category\":\"zmaster587.AR.precisionAssembler\",\"sourceIndex\":0}]}");

        assertTrue(plan.scansAllItems());
        assertEquals(1, plan.recipeCount());
    }

    @Test
    public void recipeIdResolvesExactlyOnceAndKeepsTargetOrder() throws Exception {
        QualitySamplePlan plan = parse("{\"recipes\":[" +
                "{\"category\":\"minecraft.crafting\"," +
                "\"recipeId\":\"crafttweaker:ct_shaped-557966710\"}," +
                "{\"category\":\"minecraft.crafting\",\"sourceIndex\":0}]}");

        assertEquals(1, plan.sourceIndexSelectorCount());
        assertEquals(1, plan.recipeIdSelectorCount());
        assertTrue(plan.requiresRecipeIds("minecraft.crafting"));
        assertFalse(plan.requiresRecipeIds("missing"));
        assertEquals(Arrays.asList(2, 0), plan.resolveSourceIndexes("minecraft.crafting", 4,
                Arrays.asList("minecraft:first", null,
                        "crafttweaker:ct_shaped-557966710", "minecraft:last")));
    }

    @Test
    public void recipeIdMustResolveToExactlyOneRecipeInItsCategory() throws Exception {
        QualitySamplePlan plan = parse("{\"recipes\":[{" +
                "\"category\":\"minecraft.crafting\"," +
                "\"recipeId\":\"crafttweaker:ct_shaped-557966710\"}]}");

        assertResolutionRejected(plan, Collections.singletonList("minecraft:other"),
                "did not resolve");
        assertResolutionRejected(plan, Arrays.asList(
                        "crafttweaker:ct_shaped-557966710",
                        "crafttweaker:ct_shaped-557966710"),
                "resolved to 2 recipes");
    }

    @Test
    public void differentSelectorsCannotResolveToTheSameRecipe() throws Exception {
        QualitySamplePlan plan = parse("{\"recipes\":[" +
                "{\"category\":\"minecraft.crafting\",\"sourceIndex\":1}," +
                "{\"category\":\"minecraft.crafting\",\"recipeId\":\"minecraft:same\"}]}");

        assertResolutionRejected(plan, Arrays.asList("minecraft:other", "minecraft:same"),
                "duplicate HEI source recipe #1");
    }

    @Test
    public void sourceIndexRangeStillFailsExplicitlyAtResolution() throws Exception {
        QualitySamplePlan plan = parse("{\"recipes\":[{" +
                "\"category\":\"minecraft.crafting\",\"sourceIndex\":3}]}");

        try {
            plan.resolveSourceIndexes("minecraft.crafting", 3, null);
            fail("Expected out-of-range source index to be rejected");
        } catch (IOException error) {
            assertTrue(error.getMessage(), error.getMessage().contains("outside the HEI source range"));
        }
    }

    @Test
    public void malformedOrAmbiguousPlansFailExplicitly() throws Exception {
        assertRejected("null", "must not be null");
        assertRejected("{}", "non-empty array");
        assertRejected("{\"recipes\":[]}", "non-empty array");
        assertRejected("{\"recipes\":[{\"category\":\"minecraft.crafting\"}]}",
                "exactly one of sourceIndex or recipeId");
        assertRejected("{\"recipes\":[{\"category\":\"minecraft.crafting\"," +
                "\"sourceIndex\":1,\"recipeId\":\"minecraft:test\"}]}",
                "exactly one of sourceIndex or recipeId");
        assertRejected("{\"recipes\":[{\"category\":\"minecraft.crafting\",\"sourceIndex\":1," +
                "\"typo\":true}]}", "unsupported field typo");
        assertRejected("{\"recipes\":[{\"category\":\"minecraft.crafting\",\"sourceIndex\":1}," +
                "{\"category\":\"minecraft.crafting\",\"sourceIndex\":1}]}",
                "duplicate selector");
        assertRejected("{\"recipes\":[{\"category\":\"minecraft.crafting\",\"sourceIndex\":1.5}]}",
                "must be an integer");
        assertRejected("{\"scanAllItems\":\"yes\",\"recipes\":[{" +
                "\"category\":\"minecraft.crafting\",\"sourceIndex\":1}]}",
                "scanAllItems must be a boolean");
    }

    @Test
    public void recipeIdMustBeAnExactCanonicalResourceLocation() throws Exception {
        assertRejected(recipeIdEntry("stone"), "canonical ResourceLocation");
        assertRejected(recipeIdEntry("Minecraft:stone"), "canonical ResourceLocation");
        assertRejected(recipeIdEntry("minecraft:Stone"), "canonical ResourceLocation");
        assertRejected(recipeIdEntry(" minecraft:stone"), "canonical ResourceLocation");
        assertRejected(recipeIdEntry("minecraft:stone "), "canonical ResourceLocation");
        assertRejected(recipeIdEntry("minecraft:"), "canonical ResourceLocation");
        assertRejected(recipeIdEntry(null), "canonical ResourceLocation");
    }

    private static String recipeIdEntry(String recipeId) {
        String encoded = recipeId == null ? "null" : "\"" + recipeId + "\"";
        return "{\"recipes\":[{\"category\":\"minecraft.crafting\",\"recipeId\":" +
                encoded + "}]}";
    }

    private static QualitySamplePlan parse(String json) throws IOException {
        return QualitySamplePlan.parse(new JsonParser().parse(json));
    }

    private static void assertResolutionRejected(QualitySamplePlan plan,
                                                 java.util.List<String> sourceRecipeIds,
                                                 String message) throws Exception {
        try {
            plan.resolveSourceIndexes("minecraft.crafting", sourceRecipeIds.size(), sourceRecipeIds);
            fail("Expected quality sample resolution to be rejected");
        } catch (IOException error) {
            assertTrue(error.getMessage(), error.getMessage().contains(message));
        }
    }

    private static void assertRejected(String json, String message) throws Exception {
        try {
            parse(json);
            fail("Expected quality sample plan to be rejected");
        } catch (IOException error) {
            assertTrue(error.getMessage(), error.getMessage().contains(message));
        }
    }
}
