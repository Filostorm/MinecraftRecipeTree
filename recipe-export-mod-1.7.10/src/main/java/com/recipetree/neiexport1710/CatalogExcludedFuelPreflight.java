package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Source-bound exclusions for owner-internal world blocks leaked by NEI's furnace-fuel scan.
 *
 * <p>NEI builds these pages from vanilla burn-time discovery, so placed/TESR helper blocks can
 * appear even when their bare ItemBlock is not an obtainable inventory identity. The same exact
 * stacks are excluded from the completed global ItemList. This contract scans the entire fuel
 * corpus before export, rescans it after the handler is reloaded, and consumes only the five
 * pinned source loci. It is not a registry-key or render-failure fallback.</p>
 */
final class CatalogExcludedFuelPreflight {
    static final String CONTRACT =
            "nei-furnace-fuel-owner-internal-world-state-row-exclusion-v1";
    static final String CATEGORY_ID = "gtnh:6f4faecf936866ebc248ba0dd14040a8";
    static final String HANDLER_CLASS = "codechicken.nei.recipe.FuelRecipeHandler";
    static final String HANDLER_ID = "codechicken.nei.recipe.FuelRecipeHandler";
    static final String OPERATION = "fuel";
    static final int CATEGORY_RECIPE_COUNT = 3744;
    static final int EXPECTED_EXCLUSIONS = 5;

    private static final Expected[] EXPECTED = {
            new Expected(1264, StackIdentity.BOTANIA_CACOPHONIUM_BLOCK_WORLD_STATE),
            new Expected(1292, StackIdentity.CARPENTERS_BED_INTERNAL_WORLD_ITEM_BLOCK),
            new Expected(1295, StackIdentity.CARPENTERS_DOOR_INTERNAL_WORLD_ITEM_BLOCK),
            new Expected(2795, StackIdentity.TCONSTRUCT_HELD_ITEM_INTERNAL_WORLD_ITEM_BLOCK),
            new Expected(2796, StackIdentity.TCONSTRUCT_BATTLESIGN_INTERNAL_WORLD_ITEM_BLOCK)
    };

    static final class Expected {
        final int sourceIndex;
        final StackIdentity.CatalogExclusion policy;

        Expected(int sourceIndex, StackIdentity.CatalogExclusion policy) {
            this.sourceIndex = sourceIndex;
            this.policy = policy;
        }

        String diagnostic() {
            return "sourceIndex=" + sourceIndex + ",policy=" + policy.contract
                    + ",registryId=" + policy.registryId;
        }
    }

    private static final class Observation implements Comparable<Observation> {
        final int sourceIndex;
        final int slotIndex;
        final int alternativeIndex;
        final StackIdentity.CatalogExclusion policy;
        final String stackDescription;

        Observation(int sourceIndex, int slotIndex, int alternativeIndex,
                    StackIdentity.CatalogExclusion policy, ItemStack stack) {
            this.sourceIndex = sourceIndex;
            this.slotIndex = slotIndex;
            this.alternativeIndex = alternativeIndex;
            this.policy = policy;
            this.stackDescription = StackIdentity.describe(stack);
        }

        boolean matches(Expected expected) {
            return sourceIndex == expected.sourceIndex
                    && slotIndex == 0
                    && alternativeIndex == 0
                    && policy == expected.policy;
        }

        String diagnostic() {
            return "sourceIndex=" + sourceIndex + ",slotIndex=" + slotIndex
                    + ",alternativeIndex=" + alternativeIndex + ",policy="
                    + policy.contract + ",stack={" + stackDescription + "}";
        }

        @Override
        public int compareTo(Observation other) {
            int result = Integer.compare(sourceIndex, other.sourceIndex);
            if (result != 0) return result;
            result = Integer.compare(slotIndex, other.slotIndex);
            if (result != 0) return result;
            return Integer.compare(alternativeIndex, other.alternativeIndex);
        }
    }

    /** Immutable preflight binding plus same-run consumption accounting. */
    static final class Snapshot {
        private final HandlerCategoryPlan expectedPlan;
        private final boolean[] consumed = new boolean[EXPECTED.length];
        private boolean reloadedCorpusVerified;

        Snapshot(HandlerCategoryPlan expectedPlan) {
            this.expectedPlan = expectedPlan;
        }

        Expected verifyAndConsumeIfExact(
                HandlerCategoryPlan plan, ICraftingHandler loaded, int sourceIndex)
                throws ExportFailure {
            if (plan == null || !CATEGORY_ID.equals(plan.categoryId)) return null;
            int expectedIndex = expectedIndex(sourceIndex);
            if (expectedIndex < 0) return null;
            if (plan != expectedPlan) {
                throw failure("preflighted fuel plan identity changed before export");
            }
            requireLoadedIdentity(plan, loaded);
            if (!reloadedCorpusVerified) {
                requireExactCorpus(scan(loaded), "reloaded fuel corpus");
                reloadedCorpusVerified = true;
            }
            List<Observation> row = scanRow(loaded, sourceIndex);
            Expected expected = EXPECTED[expectedIndex];
            if (row.size() != 1 || !row.get(0).matches(expected)) {
                throw failure("pinned fuel row drifted before consumption; expected="
                        + expected.diagnostic() + ", observed=" + diagnostics(row));
            }
            if (consumed[expectedIndex]) {
                throw failure("pinned fuel row was consumed twice; " + expected.diagnostic());
            }
            consumed[expectedIndex] = true;
            return expected;
        }

        void requireAllConsumed() throws ExportFailure {
            List<String> missing = new ArrayList<String>();
            for (int index = 0; index < EXPECTED.length; index++) {
                if (!consumed[index]) missing.add(EXPECTED[index].diagnostic());
            }
            if (!reloadedCorpusVerified || !missing.isEmpty()) {
                throw failure("fuel exclusion consumption incomplete; reloadedCorpusVerified="
                        + reloadedCorpusVerified + ", missing=" + missing);
            }
        }
    }

    private CatalogExcludedFuelPreflight() {
    }

    static Snapshot preflight(List<HandlerCategoryPlan> plans) throws ExportFailure {
        HandlerCategoryPlan target = null;
        for (HandlerCategoryPlan plan : plans) {
            if (CATEGORY_ID.equals(plan.categoryId)) {
                if (target != null) throw failure("duplicate pinned fuel category");
                target = plan;
            }
        }
        if (target == null) throw failure("pinned fuel category is absent");
        requirePlanIdentity(target);
        ICraftingHandler loaded = target.loadCompleteCategory();
        requireLoadedIdentity(target, loaded);
        List<Observation> observations = scan(loaded);
        requireExactCorpus(observations, "preflight fuel corpus");
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Promoted exact owner-internal furnace-fuel row "
                        + "exclusion preflight; contract={} categoryId={} recipes={} "
                        + "excluded={} observations={}",
                CONTRACT, CATEGORY_ID, CATEGORY_RECIPE_COUNT,
                observations.size(), diagnostics(observations));
        return new Snapshot(target);
    }

    private static void requirePlanIdentity(HandlerCategoryPlan plan) throws ExportFailure {
        if (!HANDLER_CLASS.equals(plan.prototype.getClass().getName())
                || !HANDLER_ID.equals(plan.handlerId)
                || !OPERATION.equals(plan.loadIdentifier)
                || plan.adapter != CompleteCategoryAdapters.Adapter.STANDARD) {
            throw failure("pinned fuel plan identity drifted");
        }
    }

    private static void requireLoadedIdentity(
            HandlerCategoryPlan plan, ICraftingHandler loaded) throws ExportFailure {
        requirePlanIdentity(plan);
        if (loaded == null
                || !HANDLER_CLASS.equals(loaded.getClass().getName())
                || !HANDLER_ID.equals(loaded.getHandlerId())
                || loaded.numRecipes() != CATEGORY_RECIPE_COUNT) {
            throw failure("loaded fuel handler identity/count drifted; expected class="
                    + HANDLER_CLASS + ", handlerId=" + HANDLER_ID + ", recipes="
                    + CATEGORY_RECIPE_COUNT + ", actualClass="
                    + (loaded == null ? "<null>" : loaded.getClass().getName())
                    + ", actualHandlerId="
                    + (loaded == null ? "<null>" : loaded.getHandlerId())
                    + ", actualRecipes="
                    + (loaded == null ? -1 : loaded.numRecipes()));
        }
    }

    private static List<Observation> scan(ICraftingHandler loaded) throws ExportFailure {
        List<Observation> observations = new ArrayList<Observation>();
        for (int sourceIndex = 0; sourceIndex < loaded.numRecipes(); sourceIndex++) {
            observations.addAll(scanRow(loaded, sourceIndex));
        }
        Collections.sort(observations);
        return observations;
    }

    private static List<Observation> scanRow(
            ICraftingHandler loaded, int sourceIndex) throws ExportFailure {
        List<PositionedStack> others;
        try {
            others = loaded.getOtherStacks(sourceIndex);
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_IDENTITY",
                    "fuel exclusion preflight could not read sourceIndex=" + sourceIndex,
                    error);
        }
        if (others == null) {
            throw failure("fuel sourceIndex=" + sourceIndex + " returned null other stacks");
        }
        List<Observation> observations = new ArrayList<Observation>();
        for (int slotIndex = 0; slotIndex < others.size(); slotIndex++) {
            PositionedStack positioned = others.get(slotIndex);
            if (positioned == null) {
                throw failure("fuel sourceIndex=" + sourceIndex
                        + " returned null other stack slot=" + slotIndex);
            }
            if (positioned.items == null || positioned.items.length == 0) {
                positioned.generatePermutations();
            }
            if (positioned.items == null || positioned.items.length == 0) {
                throw failure("fuel sourceIndex=" + sourceIndex
                        + " returned empty other stack slot=" + slotIndex);
            }
            for (int alternativeIndex = 0;
                 alternativeIndex < positioned.items.length; alternativeIndex++) {
                ItemStack stack = positioned.items[alternativeIndex];
                if (stack == null || stack.getItem() == null) {
                    throw failure("fuel sourceIndex=" + sourceIndex
                            + " returned null alternative at slot=" + slotIndex
                            + ", alternative=" + alternativeIndex);
                }
                StackIdentity.CatalogExclusion exclusion =
                        StackIdentity.catalogOnlyExclusion(stack);
                if (exclusion != null) {
                    observations.add(new Observation(
                            sourceIndex, slotIndex, alternativeIndex, exclusion, stack));
                }
            }
        }
        return observations;
    }

    private static void requireExactCorpus(
            List<Observation> observations, String phase) throws ExportFailure {
        if (observations.size() != EXPECTED.length) {
            throw failure(phase + " exclusion count drifted; expected=" + EXPECTED.length
                    + ", observed=" + observations.size() + ", observations="
                    + diagnostics(observations));
        }
        for (int index = 0; index < EXPECTED.length; index++) {
            if (!observations.get(index).matches(EXPECTED[index])) {
                throw failure(phase + " drifted at index=" + index + "; expected="
                        + EXPECTED[index].diagnostic() + ", observed="
                        + observations.get(index).diagnostic());
            }
        }
    }

    private static int expectedIndex(int sourceIndex) {
        for (int index = 0; index < EXPECTED.length; index++) {
            if (EXPECTED[index].sourceIndex == sourceIndex) return index;
        }
        return -1;
    }

    private static List<String> diagnostics(List<Observation> observations) {
        List<String> values = new ArrayList<String>(observations.size());
        for (Observation observation : observations) values.add(observation.diagnostic());
        return values;
    }

    private static ExportFailure failure(String message) {
        return new ExportFailure("ITEM_IDENTITY", "fuel exclusion preflight: " + message);
    }
}
