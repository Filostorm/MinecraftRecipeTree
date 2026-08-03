package com.recipetree.neiexport1710;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ThaumcraftRecipeVisibilityPolicyTest {
    private static final String ASPECT_FROM_STACK =
            "ru.timeconqueror.tcneiadditions.nei.AspectFromItemStackHandler";
    private static final String ASPECT_COMBINATION =
            "ru.timeconqueror.tcneiadditions.nei.AspectCombinationHandler";
    private static final String ARCANE_SHAPED =
            "ru.timeconqueror.tcneiadditions.nei.arcaneworkbench.ArcaneCraftingShapedHandler";
    private static final String ARCANE_SHAPELESS =
            "ru.timeconqueror.tcneiadditions.nei.arcaneworkbench.ArcaneCraftingShapelessHandler";
    private static final String CRUCIBLE =
            "ru.timeconqueror.tcneiadditions.nei.TCNACrucibleRecipeHandler";
    private static final String INFUSION =
            "ru.timeconqueror.tcneiadditions.nei.TCNAInfusionRecipeHandler";

    @Test
    public void acceptsExactReplacementSetsAcrossRegularAndSerialRegistries()
            throws Exception {
        ThaumcraftRecipeVisibilityPolicy.Snapshot snapshot =
                ThaumcraftRecipeVisibilityPolicy.evaluate(
                        Arrays.asList(ASPECT_FROM_STACK, ASPECT_COMBINATION, ARCANE_SHAPED),
                        Arrays.asList(ARCANE_SHAPELESS, CRUCIBLE, INFUSION),
                        Arrays.asList(ASPECT_COMBINATION, ARCANE_SHAPED),
                        Arrays.asList(ARCANE_SHAPELESS, CRUCIBLE, INFUSION),
                        true);

        assertEquals("crafting=3+3, usage=2+3", snapshot.registrySummary());
    }

    @Test
    public void fingerprintIsIndependentOfRegistryIterationOrder() throws Exception {
        List<String> crafting = craftingReplacements();
        crafting.add("example.OtherCraftingHandler");
        List<String> usage = usageReplacements();
        usage.add("example.OtherUsageHandler");
        ThaumcraftRecipeVisibilityPolicy.Snapshot first =
                ThaumcraftRecipeVisibilityPolicy.evaluate(
                        crafting, Collections.<String>emptyList(),
                        usage, Collections.<String>emptyList(), true);

        Collections.reverse(crafting);
        Collections.reverse(usage);
        ThaumcraftRecipeVisibilityPolicy.Snapshot reordered =
                ThaumcraftRecipeVisibilityPolicy.evaluate(
                        crafting, Collections.<String>emptyList(),
                        usage, Collections.<String>emptyList(), true);

        assertEquals(first.fingerprint, reordered.fingerprint);
    }

    @Test
    public void fingerprintDetectsRegularSerialPartitionDrift() throws Exception {
        List<String> crafting = craftingReplacements();
        ThaumcraftRecipeVisibilityPolicy.Snapshot regular =
                ThaumcraftRecipeVisibilityPolicy.evaluate(
                        crafting, Collections.<String>emptyList(),
                        usageReplacements(), Collections.<String>emptyList(), true);

        String moved = crafting.remove(0);
        ThaumcraftRecipeVisibilityPolicy.Snapshot serial =
                ThaumcraftRecipeVisibilityPolicy.evaluate(
                        crafting, Collections.singletonList(moved),
                        usageReplacements(), Collections.<String>emptyList(), true);

        assertNotEquals(regular.fingerprint, serial.fingerprint);
    }

    @Test
    public void rejectsDisabledLockedRecipeVisibility() throws Exception {
        assertVisibilityGate(craftingReplacements(), usageReplacements(), false);
    }

    @Test
    public void rejectsMissingReplacement() throws Exception {
        List<String> crafting = craftingReplacements();
        crafting.remove(INFUSION);
        assertVisibilityGate(crafting, usageReplacements(), true);
    }

    @Test
    public void rejectsDuplicateReplacement() throws Exception {
        List<String> crafting = craftingReplacements();
        crafting.add(INFUSION);
        assertVisibilityGate(crafting, usageReplacements(), true);
    }

    @Test
    public void rejectsUnexpectedTcneiadditionsReplacement() throws Exception {
        List<String> usage = usageReplacements();
        usage.add("ru.timeconqueror.tcneiadditions.nei.UnreviewedHandler");
        assertVisibilityGate(craftingReplacements(), usage, true);
    }

    @Test
    public void rejectsAnyLegacyThaumcraftNeiHandler() throws Exception {
        List<String> crafting = craftingReplacements();
        crafting.add("com.djgiannuzz.thaumcraftneiplugin.nei.recipehandler.AspectRecipeHandler");
        assertVisibilityGate(crafting, usageReplacements(), true);
    }

    private static void assertVisibilityGate(List<String> crafting,
                                             List<String> usage,
                                             boolean showLockedRecipes) throws Exception {
        try {
            ThaumcraftRecipeVisibilityPolicy.evaluate(
                    crafting, Collections.<String>emptyList(),
                    usage, Collections.<String>emptyList(), showLockedRecipes);
        } catch (ExportFailure failure) {
            assertEquals(ThaumcraftRecipeVisibilityPolicy.FAILURE_CODE, failure.code);
            return;
        }
        throw new AssertionError("Expected RECIPE_VISIBILITY_GATED");
    }

    private static List<String> craftingReplacements() {
        return new ArrayList<String>(Arrays.asList(
                ASPECT_FROM_STACK,
                ASPECT_COMBINATION,
                ARCANE_SHAPED,
                ARCANE_SHAPELESS,
                CRUCIBLE,
                INFUSION));
    }

    private static List<String> usageReplacements() {
        return new ArrayList<String>(Arrays.asList(
                ASPECT_COMBINATION,
                ARCANE_SHAPED,
                ARCANE_SHAPELESS,
                CRUCIBLE,
                INFUSION));
    }
}
