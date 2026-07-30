package com.recipetree.jeiexport112;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RecipeLayoutCompatibilityPolicyTest {
    @Test
    public void exactIdentityTriplesSelectOnlyTheirCompatibilityMode() {
        assertEquals(RecipeLayoutCompatibilityPolicy.Kind.
                        ADVANCED_ROCKETRY_EMPTY_WILDCARD_INPUT,
                classify(RecipeLayoutCompatibilityPolicy.ADVANCED_ROCKETRY_UID,
                        RecipeLayoutCompatibilityPolicy.ADVANCED_ROCKETRY_CATEGORY,
                        RecipeLayoutCompatibilityPolicy.ADVANCED_ROCKETRY_WRAPPER));
        assertEquals(RecipeLayoutCompatibilityPolicy.Kind.BUILDCRAFT_HEATABLE_ABSENT_OUTPUT,
                classify(RecipeLayoutCompatibilityPolicy.BUILDCRAFT_HEATABLE_UID,
                        RecipeLayoutCompatibilityPolicy.BUILDCRAFT_HEATABLE_CATEGORY,
                        RecipeLayoutCompatibilityPolicy.BUILDCRAFT_HEATABLE_WRAPPER));
        assertEquals(RecipeLayoutCompatibilityPolicy.Kind.BUILDCRAFT_COOLABLE_ABSENT_OUTPUT,
                classify(RecipeLayoutCompatibilityPolicy.BUILDCRAFT_COOLABLE_UID,
                        RecipeLayoutCompatibilityPolicy.BUILDCRAFT_COOLABLE_CATEGORY,
                        RecipeLayoutCompatibilityPolicy.BUILDCRAFT_COOLABLE_WRAPPER));
        assertEquals(RecipeLayoutCompatibilityPolicy.Kind.NONE,
                classify("minecraft.crafting", "example.Category", "example.Wrapper"));
    }

    @Test
    public void everyKnownIdentitySignalFailsClosedWhenEitherPeerDrifts() {
        String[][] exact = {
                {RecipeLayoutCompatibilityPolicy.ADVANCED_ROCKETRY_UID,
                        RecipeLayoutCompatibilityPolicy.ADVANCED_ROCKETRY_CATEGORY,
                        RecipeLayoutCompatibilityPolicy.ADVANCED_ROCKETRY_WRAPPER},
                {RecipeLayoutCompatibilityPolicy.BUILDCRAFT_HEATABLE_UID,
                        RecipeLayoutCompatibilityPolicy.BUILDCRAFT_HEATABLE_CATEGORY,
                        RecipeLayoutCompatibilityPolicy.BUILDCRAFT_HEATABLE_WRAPPER},
                {RecipeLayoutCompatibilityPolicy.BUILDCRAFT_COOLABLE_UID,
                        RecipeLayoutCompatibilityPolicy.BUILDCRAFT_COOLABLE_CATEGORY,
                        RecipeLayoutCompatibilityPolicy.BUILDCRAFT_COOLABLE_WRAPPER}
        };
        for (String[] identity : exact) {
            assertIdentityDrift("new:uid", identity[1], identity[2]);
            assertIdentityDrift(identity[0], "new.Category", identity[2]);
            assertIdentityDrift(identity[0], identity[1], "new.Wrapper");
            assertIdentityDrift(identity[0], null, null);
        }
        assertIdentityDrift(RecipeLayoutCompatibilityPolicy.ADVANCED_ROCKETRY_UID,
                RecipeLayoutCompatibilityPolicy.BUILDCRAFT_HEATABLE_CATEGORY,
                RecipeLayoutCompatibilityPolicy.BUILDCRAFT_COOLABLE_WRAPPER);
    }

    @Test
    public void advancedRocketryPatchIsLimitedToTheExactKnownEmptyWildcardShape() {
        List<List<RecipeLayoutCompatibilityPolicy.StackRef>> inputs =
                advancedRocketryInputs(false);
        List<List<RecipeLayoutCompatibilityPolicy.StackRef>> outputs =
                singletonStack("aether_legacy:zanite_helmet", 1);

        assertTrue(RecipeLayoutCompatibilityPolicy.requiresAdvancedRocketryPatch(
                inputs, outputs, 0, 0));

        List<List<RecipeLayoutCompatibilityPolicy.StackRef>> ordinary =
                new ArrayList<List<RecipeLayoutCompatibilityPolicy.StackRef>>(inputs);
        ordinary.set(0, stack("minecraft:iron_helmet", 1));
        assertFalse(RecipeLayoutCompatibilityPolicy.requiresAdvancedRocketryPatch(
                ordinary, outputs, 0, 0));

        List<List<RecipeLayoutCompatibilityPolicy.StackRef>> wrongEmptySlot =
                new ArrayList<List<RecipeLayoutCompatibilityPolicy.StackRef>>(ordinary);
        wrongEmptySlot.set(1, Collections.<RecipeLayoutCompatibilityPolicy.StackRef>emptyList());
        assertShapeDrift(() -> RecipeLayoutCompatibilityPolicy.requiresAdvancedRocketryPatch(
                wrongEmptySlot, outputs, 0, 0), "moved from slot 0");

        assertShapeDrift(() -> RecipeLayoutCompatibilityPolicy.requiresAdvancedRocketryPatch(
                inputs, singletonStack("example:unknown_helmet", 1), 0, 0),
                "unrecognized output");
    }

    @Test
    public void advancedRocketryChestplateRequiresItsExactTankAndCompanionInputs() {
        List<List<RecipeLayoutCompatibilityPolicy.StackRef>> chestplate =
                advancedRocketryInputs(true);
        assertTrue(RecipeLayoutCompatibilityPolicy.requiresAdvancedRocketryPatch(
                chestplate, singletonStack("aether_legacy:phoenix_chestplate", 1), 0, 0));

        List<List<RecipeLayoutCompatibilityPolicy.StackRef>> wrongTank =
                new ArrayList<List<RecipeLayoutCompatibilityPolicy.StackRef>>(chestplate);
        wrongTank.set(3, stack("advancedrocketry:pressuretank", 2));
        assertShapeDrift(() -> RecipeLayoutCompatibilityPolicy.requiresAdvancedRocketryPatch(
                wrongTank, singletonStack("aether_legacy:phoenix_chestplate", 1), 0, 0),
                "pressure-tank");

        assertShapeDrift(() -> RecipeLayoutCompatibilityPolicy.requiresAdvancedRocketryPatch(
                advancedRocketryInputs(false),
                singletonStack("aether_legacy:phoenix_chestplate", 1), 0, 0),
                "expected 4 item-input slots");
    }

    @Test
    public void buildCraftPatchAcceptsOnlyTheTwoExactAbsentOutputs() {
        List<List<RecipeLayoutCompatibilityPolicy.FluidRef>> noOutputs =
                Collections.emptyList();
        assertTrue(RecipeLayoutCompatibilityPolicy.requiresBuildCraftPatch(
                RecipeLayoutCompatibilityPolicy.Kind.BUILDCRAFT_HEATABLE_ABSENT_OUTPUT,
                0, 0, singletonFluid("water", 10), noOutputs));
        assertTrue(RecipeLayoutCompatibilityPolicy.requiresBuildCraftPatch(
                RecipeLayoutCompatibilityPolicy.Kind.BUILDCRAFT_COOLABLE_ABSENT_OUTPUT,
                0, 0, singletonFluid("lava", 5), noOutputs));

        assertFalse(RecipeLayoutCompatibilityPolicy.requiresBuildCraftPatch(
                RecipeLayoutCompatibilityPolicy.Kind.BUILDCRAFT_HEATABLE_ABSENT_OUTPUT,
                0, 0, singletonFluid("water", 100), singletonFluid("steam", 100)));

        assertShapeDrift(() -> RecipeLayoutCompatibilityPolicy.requiresBuildCraftPatch(
                RecipeLayoutCompatibilityPolicy.Kind.BUILDCRAFT_HEATABLE_ABSENT_OUTPUT,
                0, 0, singletonFluid("water", 11), noOutputs), "expected 10 mB of water");
        assertShapeDrift(() -> RecipeLayoutCompatibilityPolicy.requiresBuildCraftPatch(
                RecipeLayoutCompatibilityPolicy.Kind.BUILDCRAFT_COOLABLE_ABSENT_OUTPUT,
                1, 0, singletonFluid("lava", 5), noOutputs), "unexpectedly contains item slots");
    }

    @Test
    public void normalizedViewsAreImmutableAndDoNotMutateRecordedShape() {
        List<List<String>> source = new ArrayList<List<String>>();
        source.add(new ArrayList<String>());
        source.add(new ArrayList<String>(Arrays.asList("pipe-sealer")));

        List<List<String>> normalized =
                RecipeLayoutCompatibilityPolicy.replaceFirstEmptySlot(source, "empty-sentinel");
        assertTrue(source.get(0).isEmpty());
        assertEquals(Collections.singletonList("empty-sentinel"), normalized.get(0));
        assertEquals(Collections.singletonList("pipe-sealer"), normalized.get(1));

        try {
            normalized.add(Collections.singletonList("mutation"));
            fail("Expected normalized outer list to be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        try {
            normalized.get(1).add("mutation");
            fail("Expected normalized inner lists to be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }

        List<List<String>> absentOutput =
                RecipeLayoutCompatibilityPolicy.singletonEmptySlot();
        assertEquals(1, absentOutput.size());
        assertTrue(absentOutput.get(0).isEmpty());
    }

    private static RecipeLayoutCompatibilityPolicy.Kind classify(
            String uid, String categoryClass, String wrapperClass) {
        return RecipeLayoutCompatibilityPolicy.classify(uid, categoryClass, wrapperClass);
    }

    private static void assertIdentityDrift(String uid, String categoryClass, String wrapperClass) {
        assertShapeDrift(() -> classify(uid, categoryClass, wrapperClass), "identity drift");
    }

    private static void assertShapeDrift(Runnable action, String expectedMessage) {
        try {
            action.run();
            fail("Expected compatibility policy drift");
        } catch (IllegalStateException error) {
            assertTrue(error.getMessage(), error.getMessage().contains("RECIPE_LAYOUT_COMPAT_DRIFT"));
            assertTrue(error.getMessage(), error.getMessage().contains(expectedMessage));
        }
    }

    private static List<List<RecipeLayoutCompatibilityPolicy.StackRef>>
    advancedRocketryInputs(boolean chestplate) {
        List<List<RecipeLayoutCompatibilityPolicy.StackRef>> inputs =
                new ArrayList<List<RecipeLayoutCompatibilityPolicy.StackRef>>();
        inputs.add(Collections.<RecipeLayoutCompatibilityPolicy.StackRef>emptyList());
        inputs.add(stack("advancedrocketry:pipesealer", 1));
        inputs.add(stack("advancedrocketry:productsheet", 4));
        if (chestplate) {
            inputs.add(stack("advancedrocketry:pressuretank", 1));
        }
        return inputs;
    }

    private static List<RecipeLayoutCompatibilityPolicy.StackRef> stack(String id, int count) {
        return Collections.singletonList(new RecipeLayoutCompatibilityPolicy.StackRef(id, count));
    }

    private static List<List<RecipeLayoutCompatibilityPolicy.StackRef>> singletonStack(
            String id, int count) {
        return Collections.singletonList(stack(id, count));
    }

    private static List<List<RecipeLayoutCompatibilityPolicy.FluidRef>> singletonFluid(
            String id, int amount) {
        return Collections.singletonList(Collections.singletonList(
                new RecipeLayoutCompatibilityPolicy.FluidRef(id, amount)));
    }
}
