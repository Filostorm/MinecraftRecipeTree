package com.recipetree.neiexport1710;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PinnedUnboundTemplateRecipeHandlersTest {
    @Test
    public void exactPolicyIdentityAndDiscoveryFingerprintsRemainPinned() {
        assertEquals("com.rwtema.extrautils.nei.MicroBlocksHandler",
                PinnedUnboundTemplateRecipeHandlers.HANDLER_CLASS);
        assertEquals("gtnh:16bf5c3541c3232fb78604ee77484702",
                PinnedUnboundTemplateRecipeHandlers.CATEGORY_ID);
        assertEquals("xu_microblocks_crafting",
                PinnedUnboundTemplateRecipeHandlers.OPERATION);
        assertEquals("excluded-unbound-template-category",
                PinnedUnboundTemplateRecipeHandlers.ACTION);
        assertEquals(
                "unbound-template:gtnh-2.8.4-extrautilities-microblocks-material-v3",
                PinnedUnboundTemplateRecipeHandlers.CONTRACT);
        assertEquals(PinnedUnboundTemplateRecipeHandlers.CATEGORY_ID,
                PinnedUnboundTemplateRecipeHandlers.derivedCategoryId());
        assertEquals(
                "9bb53158234fe43fbe5abb223968e65a7c709f4d9b70db032bd5d421b7a0cd6c",
                PinnedUnboundTemplateRecipeHandlers.EXPECTED_SOURCE_FINGERPRINT);
        assertEquals(
                "91302b62ad13d2fca735dbba4c4aa657a7ae4c0c3488a1f011765c702b4d7df0",
                PinnedUnboundTemplateRecipeHandlers.EXPECTED_PROTOTYPE_FINGERPRINT);
    }

    @Test
    public void neiCachedRecipeOffsetRemainsTheExactVolatileFinalLongField() throws Exception {
        Class<?> cachedRecipe = Class.forName(
                "codechicken.nei.recipe.TemplateRecipeHandler$CachedRecipe");
        Field offset = cachedRecipe.getDeclaredField("offset");
        int modifiers = offset.getModifiers();

        assertEquals(cachedRecipe, offset.getDeclaringClass());
        assertEquals(long.class, offset.getType());
        assertFalse(Modifier.isStatic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));
        assertFalse(Modifier.isTransient(modifiers));
        assertFalse(offset.isSynthetic());
    }

    @Test
    public void materiallessnessRequiresNonnegativeIntegerPlaceholdersAndNoBinding() {
        Object[] unbound = new Object[] {
                Integer.valueOf(1), null, Integer.valueOf(4), "fixed ingredient"};

        assertEquals(2,
                PinnedUnboundTemplateRecipeHandlers.materialPlaceholderCount(unbound));
        assertTrue(PinnedUnboundTemplateRecipeHandlers.isUnboundMaterialTemplate(
                unbound, false));
        assertFalse(PinnedUnboundTemplateRecipeHandlers.isUnboundMaterialTemplate(
                unbound, true));
        assertFalse(PinnedUnboundTemplateRecipeHandlers.isUnboundMaterialTemplate(
                new Object[] {"fixed ingredient"}, false));
        assertFalse(PinnedUnboundTemplateRecipeHandlers.isUnboundMaterialTemplate(
                new Object[] {Integer.valueOf(-1)}, false));
    }

    @Test
    public void exactOwnerPredicateKeepsXuOutputsDistinctFromForgeMicroblockPlaceholders() {
        assertTrue(PinnedUnboundTemplateRecipeHandlers.expectedOwnerMaterialTag(
                false, 0));
        assertTrue(PinnedUnboundTemplateRecipeHandlers.expectedOwnerMaterialTag(
                true, 1));
        assertFalse(PinnedUnboundTemplateRecipeHandlers.expectedOwnerMaterialTag(
                true, 0));

        assertEquals(8, PinnedUnboundTemplateRecipeHandlers.projectedSourceStackSize(
                8, true, true));
        assertEquals(1, PinnedUnboundTemplateRecipeHandlers.projectedSourceStackSize(
                1, true, false));
        assertEquals(8, PinnedUnboundTemplateRecipeHandlers.projectedSourceStackSize(
                8, false, false));
        try {
            PinnedUnboundTemplateRecipeHandlers.projectedSourceStackSize(
                    8, true, false);
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("not a singleton"));
            return;
        }
        throw new AssertionError("non-singleton ForgeMicroblock placeholder was accepted");
    }

    @Test
    public void exactEmptyLedgerNoLongerOwnsTheUnboundTemplateHandler() {
        for (PinnedEmptyRecipeHandlers.Spec spec
                : PinnedEmptyRecipeHandlers.specsForTest()) {
            assertFalse(PinnedUnboundTemplateRecipeHandlers.HANDLER_CLASS.equals(
                    spec.handlerClass));
        }
        assertEquals(20, PinnedEmptyRecipeHandlers.specsForTest().size());
    }
}
