package com.recipetree.jeiexport112;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RecipeLayoutPlacementPolicyTest {
    private static final int PAD = 4;

    @Test
    public void genericLayoutKeepsHeiCreationOriginAndUsesOnlyExternalPadding() {
        RecipeLayoutPlacementPolicy.Placement placement = plan(
                "minecraft.crafting", "example.Category", "example.Wrapper",
                116, 54, 427, PAD);

        assertEquals(RecipeLayoutPlacementPolicy.Kind.DEFAULT, placement.kind);
        assertEquals(0, placement.layoutX);
        assertEquals(0, placement.layoutY);
        assertEquals(PAD, placement.translateX);
        assertEquals(PAD, placement.translateY);
        assertFalse(placement.repositionsLayout());
        assertAligned(placement, PAD);
    }

    @Test
    public void exactMultiblockedLayoutMatchesItsCachedGuiLeftAndCounterTranslates() {
        RecipeLayoutPlacementPolicy.Placement placement = multiblocked(427, PAD);

        assertEquals(RecipeLayoutPlacementPolicy.Kind.
                MULTIBLOCKED_0_8_SCREEN_CENTERED_PARENT, placement.kind);
        assertEquals(125, placement.layoutX);
        assertEquals(0, placement.layoutY);
        assertEquals(-121, placement.translateX);
        assertEquals(PAD, placement.translateY);
        assertTrue(placement.repositionsLayout());
        assertAligned(placement, PAD);
    }

    @Test
    public void multiblockedGuiLeftUsesJavaIntegerDivisionForOddRemainders() {
        assertEquals(125, multiblocked(427, PAD).layoutX);
        assertEquals(126, multiblocked(428, PAD).layoutX);
        assertEquals(0, multiblocked(176, PAD).layoutX);
    }

    @Test
    public void placementInvariantHoldsAcrossScreenWidthsAndPaddingValues() {
        for (int screenWidth = 176; screenWidth <= 1024; screenWidth++) {
            for (int padding = 0; padding <= 16; padding++) {
                assertAligned(multiblocked(screenWidth, padding), padding);
                assertAligned(plan("example:recipe", "example.Category", "example.Wrapper",
                        37, 19, screenWidth, padding), padding);
            }
        }
    }

    @Test
    public void otherMultiblockedCategoriesRemainGeneric() {
        RecipeLayoutPlacementPolicy.Placement placement = plan(
                "multiblocked:multiblock_info",
                "com.cleanroommc.multiblocked.jei.multipage.MultiblockInfoCategory",
                "com.cleanroommc.multiblocked.jei.multipage.MultiblockInfoWrapper",
                176, 166, 427, PAD);
        assertEquals(RecipeLayoutPlacementPolicy.Kind.DEFAULT, placement.kind);
        assertFalse(placement.repositionsLayout());
    }

    @Test
    public void eitherKnownClassWithoutItsExactPeerFailsClosed() {
        assertDrift(() -> plan("multiblocked:divine_altar",
                RecipeLayoutPlacementPolicy.MULTIBLOCKED_CATEGORY, "new.Wrapper",
                176, 84, 427, PAD), "identity drift");
        assertDrift(() -> plan("multiblocked:divine_altar", "new.Category",
                RecipeLayoutPlacementPolicy.MULTIBLOCKED_WRAPPER,
                176, 84, 427, PAD), "identity drift");
    }

    @Test
    public void renamedPeersCannotSilentlyClaimTheExactMultiblockedRecipeMapShape() {
        assertDrift(() -> plan("multiblocked:divine_altar", "new.Category", "new.Wrapper",
                176, 84, 427, PAD), "unaudited identity");

        RecipeLayoutPlacementPolicy.Placement otherShape = plan(
                "multiblocked:multiblock_info", "new.InfoCategory", "new.InfoWrapper",
                176, 166, 427, PAD);
        assertEquals(RecipeLayoutPlacementPolicy.Kind.DEFAULT, otherShape.kind);
    }

    @Test
    public void exactClassPairRequiresAConcreteMultiblockedUid() {
        assertDrift(() -> exact(null, 176, 84, 427, PAD), "unexpected uid");
        assertDrift(() -> exact("multiblocked:", 176, 84, 427, PAD), "unexpected uid");
        assertDrift(() -> exact("newnamespace:divine_altar", 176, 84, 427, PAD),
                "unexpected uid");
    }

    @Test
    public void exactClassPairRequiresTheAudited176By84Background() {
        assertDrift(() -> exact("multiblocked:divine_altar", 175, 84, 427, PAD),
                "changed from 176x84");
        assertDrift(() -> exact("multiblocked:divine_altar", 176, 85, 427, PAD),
                "changed from 176x84");
    }

    @Test
    public void invalidCoordinateInputsFailClosedBeforeRendering() {
        assertDrift(() -> multiblocked(175, PAD), "smaller than");
        assertDrift(() -> multiblocked(427, -1), "padding must be nonnegative");
        assertDrift(() -> plan("example:recipe", "example.Category", "example.Wrapper",
                0, 84, 427, PAD), "positive dimensions");
        assertDrift(() -> plan("example:recipe", "example.Category", "example.Wrapper",
                176, 84, 0, PAD), "screen width must be positive");
    }

    private static RecipeLayoutPlacementPolicy.Placement multiblocked(
            int scaledScreenWidth, int padding) {
        return exact("multiblocked:divine_altar", 176, 84, scaledScreenWidth, padding);
    }

    private static RecipeLayoutPlacementPolicy.Placement exact(
            String uid, int width, int height, int scaledScreenWidth, int padding) {
        return plan(uid, RecipeLayoutPlacementPolicy.MULTIBLOCKED_CATEGORY,
                RecipeLayoutPlacementPolicy.MULTIBLOCKED_WRAPPER,
                width, height, scaledScreenWidth, padding);
    }

    private static RecipeLayoutPlacementPolicy.Placement plan(
            String uid, String categoryClass, String wrapperClass,
            int width, int height, int scaledScreenWidth, int padding) {
        return RecipeLayoutPlacementPolicy.plan(uid, categoryClass, wrapperClass,
                width, height, scaledScreenWidth, padding);
    }

    private static void assertAligned(RecipeLayoutPlacementPolicy.Placement placement,
                                      int padding) {
        assertEquals(padding, placement.layoutX + placement.translateX);
        assertEquals(padding, placement.layoutY + placement.translateY);
    }

    private static void assertDrift(Runnable action, String expectedMessage) {
        try {
            action.run();
            fail("Expected recipe-layout placement drift");
        } catch (IllegalStateException error) {
            assertTrue(error.getMessage(),
                    error.getMessage().contains("RECIPE_LAYOUT_PLACEMENT_DRIFT"));
            assertTrue(error.getMessage(), error.getMessage().contains(expectedMessage));
        }
    }
}
