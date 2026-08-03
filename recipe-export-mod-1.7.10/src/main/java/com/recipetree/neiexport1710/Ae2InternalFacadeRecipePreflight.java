package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Exact exclusion for AE2's facade recipe over Ender IO's internal world-render proxy. */
final class Ae2InternalFacadeRecipePreflight {
    static final String CONTRACT =
            "ae2-695-enderio-2.9.28-internal-conduit-facade-row-exclusion-v1";
    static final String CATEGORY_ID = "gtnh:fd6585e6f9e733adc268bdfffe1da807";
    static final String HANDLER_CLASS =
            "appeng.integration.modules.NEIHelpers.NEIFacadeRecipeHandler";
    static final String HANDLER_ID = HANDLER_CLASS;
    static final String OPERATION = "crafting";
    static final int CATEGORY_RECIPE_COUNT = 4364;
    static final int SOURCE_INDEX = 869;
    static final int MATERIAL_SLOT_INDEX = 2;
    static final String INTERNAL_BLOCK_KEY =
            "item|EnderIO:blockConduitFacade|meta=0|nbt=-";
    static final String ANCHOR_KEY =
            "item|appliedenergistics2:item.ItemMultiPart|meta=120|nbt=-";
    static final String INTERNAL_BLOCK_CLASS =
            "crazypants.enderio.conduit.facade.BlockConduitFacade";
    static final String RESULT_ITEM_CLASS = "appeng.items.parts.ItemFacade";
    static final int EXPECTED_EXCLUSIONS = 1;

    static final class Snapshot {
        private final HandlerCategoryPlan expectedPlan;
        private boolean reloadedCorpusVerified;
        private boolean consumed;

        Snapshot(HandlerCategoryPlan expectedPlan) {
            this.expectedPlan = expectedPlan;
        }

        boolean verifyAndConsumeIfExact(
                HandlerCategoryPlan plan, ICraftingHandler loaded, int sourceIndex)
                throws ExportFailure {
            if (plan == null || !CATEGORY_ID.equals(plan.categoryId)
                    || sourceIndex != SOURCE_INDEX) return false;
            if (plan != expectedPlan) {
                throw failure("preflighted plan identity changed before export");
            }
            requireLoadedIdentity(plan, loaded);
            if (!reloadedCorpusVerified) {
                requireExactCorpus(loaded, "reloaded corpus");
                reloadedCorpusVerified = true;
            }
            requireExactTargetRow(loaded, sourceIndex);
            if (consumed) throw failure("pinned row was consumed twice");
            consumed = true;
            return true;
        }

        void requireConsumedExactlyOnce() throws ExportFailure {
            if (!reloadedCorpusVerified || !consumed) {
                throw failure("consumption incomplete; reloadedCorpusVerified="
                        + reloadedCorpusVerified + ", consumed=" + consumed);
            }
        }
    }

    private Ae2InternalFacadeRecipePreflight() {}

    static Snapshot preflight(List<HandlerCategoryPlan> plans) throws ExportFailure {
        HandlerCategoryPlan target = null;
        for (HandlerCategoryPlan plan : plans) {
            if (CATEGORY_ID.equals(plan.categoryId)) {
                if (target != null) throw failure("duplicate pinned category");
                target = plan;
            }
        }
        if (target == null) throw failure("pinned category is absent");
        requirePlanIdentity(target);
        ICraftingHandler loaded = target.loadCompleteCategory();
        requireLoadedIdentity(target, loaded);
        requireExactCorpus(loaded, "preflight corpus");
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Promoted exact AE2/Ender IO internal facade row "
                        + "exclusion preflight; contract={} categoryId={} recipes={} "
                        + "sourceIndex={} materialKey={}",
                CONTRACT, CATEGORY_ID, CATEGORY_RECIPE_COUNT, SOURCE_INDEX,
                INTERNAL_BLOCK_KEY);
        return new Snapshot(target);
    }

    private static void requirePlanIdentity(HandlerCategoryPlan plan) throws ExportFailure {
        if (!HANDLER_CLASS.equals(plan.prototype.getClass().getName())
                || !HANDLER_ID.equals(plan.handlerId)
                || !OPERATION.equals(plan.loadIdentifier)
                || plan.adapter != CompleteCategoryAdapters.Adapter.STANDARD) {
            throw failure("pinned plan identity drifted");
        }
    }

    private static void requireLoadedIdentity(
            HandlerCategoryPlan plan, ICraftingHandler loaded) throws ExportFailure {
        requirePlanIdentity(plan);
        if (loaded == null
                || !HANDLER_CLASS.equals(loaded.getClass().getName())
                || !HANDLER_ID.equals(loaded.getHandlerId())
                || loaded.numRecipes() != CATEGORY_RECIPE_COUNT) {
            throw failure("loaded handler identity/count drifted; actualClass="
                    + (loaded == null ? "<null>" : loaded.getClass().getName())
                    + ", actualHandlerId="
                    + (loaded == null ? "<null>" : loaded.getHandlerId())
                    + ", actualRecipes="
                    + (loaded == null ? -1 : loaded.numRecipes()));
        }
    }

    private static void requireExactCorpus(ICraftingHandler loaded, String phase)
            throws ExportFailure {
        List<String> observations = new ArrayList<String>();
        for (int sourceIndex = 0; sourceIndex < loaded.numRecipes(); sourceIndex++) {
            List<PositionedStack> ingredients = ingredients(loaded, sourceIndex);
            for (int slotIndex = 0; slotIndex < ingredients.size(); slotIndex++) {
                ItemStack[] values = alternatives(
                        ingredients.get(slotIndex), sourceIndex, slotIndex);
                for (int alternativeIndex = 0;
                     alternativeIndex < values.length; alternativeIndex++) {
                    if (INTERNAL_BLOCK_KEY.equals(identity(
                            values[alternativeIndex], sourceIndex, slotIndex,
                            alternativeIndex).key)) {
                        observations.add("sourceIndex=" + sourceIndex + ",slotIndex="
                                + slotIndex + ",alternativeIndex=" + alternativeIndex);
                    }
                }
            }
        }
        String expected = "sourceIndex=" + SOURCE_INDEX + ",slotIndex="
                + MATERIAL_SLOT_INDEX + ",alternativeIndex=0";
        if (observations.size() != EXPECTED_EXCLUSIONS
                || !expected.equals(observations.get(0))) {
            throw failure(phase + " internal-block references drifted; expected=["
                    + expected + "], observed=" + observations);
        }
        requireExactTargetRow(loaded, SOURCE_INDEX);
    }

    private static void requireExactTargetRow(ICraftingHandler loaded, int sourceIndex)
            throws ExportFailure {
        List<PositionedStack> ingredients = ingredients(loaded, sourceIndex);
        if (ingredients.size() != 5) {
            throw failure("ingredient count drifted; expected=5, observed="
                    + ingredients.size());
        }
        ItemStack material = null;
        for (int slotIndex = 0; slotIndex < ingredients.size(); slotIndex++) {
            ItemStack[] values = alternatives(ingredients.get(slotIndex), sourceIndex, slotIndex);
            if (values.length != 1) {
                throw failure("slot alternative count drifted; slot=" + slotIndex
                        + ", observed=" + values.length);
            }
            StackIdentity identity = identity(values[0], sourceIndex, slotIndex, 0);
            String expected = slotIndex == MATERIAL_SLOT_INDEX
                    ? INTERNAL_BLOCK_KEY : ANCHOR_KEY;
            if (!expected.equals(identity.key) || values[0].stackSize != 1) {
                throw failure("ingredient drifted; slot=" + slotIndex + ", expected="
                        + expected + "@1, observed=" + identity.key + "@"
                        + values[0].stackSize);
            }
            if (slotIndex == MATERIAL_SLOT_INDEX) material = values[0];
        }
        requireInternalBlockTopology(material);

        final PositionedStack result;
        final List<PositionedStack> others;
        try {
            result = loaded.getResultStack(sourceIndex);
            others = loaded.getOtherStacks(sourceIndex);
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_IDENTITY",
                    "AE2 internal facade preflight could not read outputs", error);
        }
        if (result == null || others == null || !others.isEmpty()) {
            throw failure("result/other-stack topology drifted");
        }
        ItemStack[] results = alternatives(result, sourceIndex, -1);
        if (results.length != 1 || results[0].stackSize != 4
                || results[0].getItem() == null
                || !RESULT_ITEM_CLASS.equals(results[0].getItem().getClass().getName())) {
            throw failure("AE2 facade result drifted");
        }
        StackIdentity texture = identity(textureItem(results[0]), sourceIndex, -1, 0);
        if (!INTERNAL_BLOCK_KEY.equals(texture.key)) {
            throw failure("AE2 facade result texture drifted; observed=" + texture.key);
        }
    }

    private static void requireInternalBlockTopology(ItemStack material)
            throws ExportFailure {
        if (material == null || material.getItem() == null) {
            throw failure("internal material is null");
        }
        Block block = Block.getBlockFromItem(material.getItem());
        if (block == null || !INTERNAL_BLOCK_CLASS.equals(block.getClass().getName())
                || block.getCreativeTabToDisplayOn() != null) {
            throw failure("Ender IO internal block topology drifted; blockClass="
                    + (block == null ? "<null>" : block.getClass().getName())
                    + ", hasCreativeTab="
                    + (block != null && block.getCreativeTabToDisplayOn() != null));
        }
    }

    private static ItemStack textureItem(ItemStack facade) throws ExportFailure {
        try {
            Method method = facade.getItem().getClass()
                    .getMethod("getTextureItem", ItemStack.class);
            Object value = method.invoke(facade.getItem(), facade);
            if (!(value instanceof ItemStack)) {
                throw failure("getTextureItem returned "
                        + (value == null ? "<null>" : value.getClass().getName()));
            }
            return (ItemStack) value;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_IDENTITY",
                    "AE2 internal facade preflight could not resolve texture item", error);
        }
    }

    private static List<PositionedStack> ingredients(
            ICraftingHandler loaded, int sourceIndex) throws ExportFailure {
        try {
            List<PositionedStack> values = loaded.getIngredientStacks(sourceIndex);
            if (values == null) throw failure("null ingredients at " + sourceIndex);
            return values;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_IDENTITY",
                    "AE2 internal facade preflight could not read row " + sourceIndex,
                    error);
        }
    }

    private static ItemStack[] alternatives(
            PositionedStack positioned, int sourceIndex, int slotIndex)
            throws ExportFailure {
        if (positioned == null) {
            throw failure("null positioned stack at row=" + sourceIndex
                    + ", slot=" + slotIndex);
        }
        if (positioned.items == null || positioned.items.length == 0) {
            positioned.generatePermutations();
        }
        if (positioned.items == null || positioned.items.length == 0) {
            throw failure("empty positioned stack at row=" + sourceIndex
                    + ", slot=" + slotIndex);
        }
        return positioned.items;
    }

    private static StackIdentity identity(
            ItemStack stack, int sourceIndex, int slotIndex, int alternativeIndex)
            throws ExportFailure {
        if (stack == null || stack.getItem() == null) {
            throw failure("null stack at row=" + sourceIndex + ", slot=" + slotIndex
                    + ", alternative=" + alternativeIndex);
        }
        try {
            return StackIdentity.of(stack);
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_IDENTITY",
                    "AE2 internal facade preflight could not canonicalize row="
                            + sourceIndex + ", slot=" + slotIndex + ", alternative="
                            + alternativeIndex,
                    error);
        }
    }

    private static ExportFailure failure(String message) {
        return new ExportFailure("ITEM_IDENTITY",
                "AE2 internal facade exclusion preflight: " + message);
    }
}
