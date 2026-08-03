package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Exact source binding for GregTech's synthetic Forestry {@code Scanned Sapling} scanner row.
 *
 * <p>GregTech deliberately creates this fake row with a genome-free Forestry sapling carrying a
 * complete vanilla custom-name envelope. Forestry 4.10.17 dereferences the absent species before
 * {@link ItemStack#getDisplayName()} can apply that envelope. This preflight authorizes the custom
 * name only for the one pinned recipe graph occurrence which GregTech itself constructed. It does
 * not provide a key-only or owner-failure fallback.</p>
 */
final class GregTechForestryScannedSaplingPreflight {
    static final String CONTRACT =
            "gregtech-forestry-scanned-sapling-source-bound-display-name-v1";
    static final String CATEGORY_ID = "gtnh:29f254947fc19d609c5b58f71a881be1";
    static final String HANDLER_CLASS = "gregtech.nei.GTNEIDefaultHandler";
    static final String HANDLER_ID = "gregtech.nei.GTNEIDefaultHandler";
    static final String OPERATION = "gt.recipe.scanner";
    static final String OVERLAY = "gt.recipe.scanner";
    static final int CATEGORY_RECIPE_COUNT = 298;
    static final int SOURCE_INDEX = 3;
    static final int EXPECTED_RECIPE_OCCURRENCES = 1;
    static final String ROLE = "output";
    static final int SLOT_INDEX = 0;
    static final int ALTERNATIVE_INDEX = 0;

    static final String FORESTRY_GERMLING_CLASS =
            "forestry.arboriculture.items.ItemGermlingGE";
    static final String FORESTRY_SAPLING_REGISTRY_ID = "Forestry:sapling";
    static final String RAW_WILDCARD_INPUT_KEY =
            "item|Forestry:sapling|meta=32767|nbt=-";
    static final String SCANNED_SAPLING_NAME = "Scanned Sapling";
    static final String SCANNED_SAPLING_CANONICAL_NBT =
            "10:{1:7:display10:{1:4:Name8:17:\"Scanned Sapling\"}}";
    static final String SCANNED_SAPLING_CANONICAL_KEY =
            "item|Forestry:sapling|meta=0|nbt="
                    + "2ef7c2d8cc838349c0e3f86e385f092334f4f432cde0d20c2c29af8d6435ca31";

    static final String HONEY_FLUID_NAME = "for.honey";
    static final int HONEY_AMOUNT = 100;
    static final String VISIBLE_HONEY_KEY = "fluid|fluid:for.honey";
    static final int VISIBLE_GENETIC_INPUT_ALTERNATIVES = 132;
    static final String VISIBLE_GENETIC_INPUT_SORTED_KEY_LF_SHA256 =
            "bbb486874a1b478f140b5b7b9c5ed6b9217a67d1b1076c4dadd850eb62142da7";

    /** A one-use capability which permits only the pinned display-name identity. */
    static final class DisplayNameAuthorization {
        private boolean claimed;

        private DisplayNameAuthorization() {
        }

        String contract() {
            return CONTRACT;
        }

        String claimDisplayName(StackIdentity identity) throws ExportFailure {
            if (identity == null) {
                throw failure("display-name authorization received a null identity");
            }
            if (!SCANNED_SAPLING_CANONICAL_KEY.equals(identity.key)
                    || !FORESTRY_SAPLING_REGISTRY_ID.equals(identity.registryId)
                    || identity.metadata != 0
                    || identity.amount != 1
                    || !SCANNED_SAPLING_CANONICAL_NBT.equals(identity.canonicalNbt)
                    || identity.stack == null || identity.stack.getItem() == null
                    || !FORESTRY_GERMLING_CLASS.equals(
                            identity.stack.getItem().getClass().getName())) {
                throw failure("display-name authorization identity drifted; got "
                        + (identity == null ? "<null>" : identity.key));
            }
            return claimDisplayName(identity.key);
        }

        /** Package-visible pure-key entry point used by focused capability tests. */
        String claimDisplayName(String canonicalKey) throws ExportFailure {
            if (!SCANNED_SAPLING_CANONICAL_KEY.equals(canonicalKey)) {
                throw failure("display-name authorization key drifted; got " + canonicalKey);
            }
            if (claimed) {
                throw failure("display-name authorization token was claimed twice");
            }
            claimed = true;
            return SCANNED_SAPLING_NAME;
        }

        boolean isClaimed() {
            return claimed;
        }
    }

    /** Immutable preflight observation plus mutable same-run consumption accounting. */
    static final class Snapshot {
        private final HandlerCategoryPlan expectedPlan;
        private final String observationFingerprint;
        private final SourceAuthorizationGate authorizationGate =
                new SourceAuthorizationGate();

        private Snapshot(HandlerCategoryPlan expectedPlan, String observationFingerprint) {
            this.expectedPlan = expectedPlan;
            this.observationFingerprint = observationFingerprint;
        }

        /**
         * Returns a one-use display-name capability only at the pinned source locus.
         *
         * <p>This method must be called for every graph alternative before catalog de-duplication.
         * That ordering makes an occurrence of the canonical key in another recipe, category icon,
         * or other graph role fail explicitly even if the catalog already contains the key.</p>
         */
        DisplayNameAuthorization authorizeIfExact(
                HandlerCategoryPlan plan,
                ICraftingHandler loaded,
                int sourceIndex,
                String role,
                int slotIndex,
                int alternativeIndex,
                StackIdentity identity) throws ExportFailure {
            String categoryId = plan == null ? null : plan.categoryId;
            String canonicalKey = identity == null ? null : identity.key;
            if (!SourceAuthorizationGate.requiresDecision(
                    categoryId, sourceIndex, role, slotIndex, alternativeIndex,
                    canonicalKey)) {
                return null;
            }

            // Mismatched source/key combinations fail here without attempting to reinterpret them.
            authorizationGate.requireExactLocusAndKey(
                    categoryId, sourceIndex, role, slotIndex, alternativeIndex,
                    canonicalKey);
            if (plan != expectedPlan) {
                throw failure("preflighted scanner plan identity changed before export");
            }

            Observation current = observe(plan, loaded);
            if (!observationFingerprint.equals(current.fingerprint)) {
                throw failure("scanner row fingerprint drifted between preflight and export; "
                        + "expected=" + observationFingerprint + ", current="
                        + current.fingerprint);
            }
            if (identity == null || current.visibleOutputAlternative != identity.stack) {
                throw failure("export traversal stack is not the identity-bound scanner output "
                        + "alternative");
            }
            return authorizationGate.consumeExact();
        }

        void requireConsumedExactlyOnce() throws ExportFailure {
            authorizationGate.requireComplete();
        }

        String fingerprint() {
            return observationFingerprint;
        }
    }

    /** Pure source-locus gate, split out so duplicate/missing consumption is unit-testable. */
    static final class SourceAuthorizationGate {
        private boolean consumed;
        private DisplayNameAuthorization authorization;

        static boolean requiresDecision(
                String categoryId,
                int sourceIndex,
                String role,
                int slotIndex,
                int alternativeIndex,
                String canonicalKey) {
            return isExactLocus(
                    categoryId, sourceIndex, role, slotIndex, alternativeIndex)
                    || SCANNED_SAPLING_CANONICAL_KEY.equals(canonicalKey);
        }

        void requireExactLocusAndKey(
                String categoryId,
                int sourceIndex,
                String role,
                int slotIndex,
                int alternativeIndex,
                String canonicalKey) throws ExportFailure {
            if (!isExactLocus(categoryId, sourceIndex, role, slotIndex, alternativeIndex)) {
                throw failure("canonical Scanned Sapling appeared outside its pinned source; "
                        + locus(categoryId, sourceIndex, role, slotIndex, alternativeIndex));
            }
            if (!SCANNED_SAPLING_CANONICAL_KEY.equals(canonicalKey)) {
                throw failure("pinned Scanned Sapling source no longer exposes the exact output; "
                        + locus(categoryId, sourceIndex, role, slotIndex, alternativeIndex)
                        + ", key=" + canonicalKey);
            }
        }

        DisplayNameAuthorization authorizeForTest(
                String categoryId,
                int sourceIndex,
                String role,
                int slotIndex,
                int alternativeIndex,
                String canonicalKey) throws ExportFailure {
            requireExactLocusAndKey(
                    categoryId, sourceIndex, role, slotIndex, alternativeIndex, canonicalKey);
            return consumeExact();
        }

        private DisplayNameAuthorization consumeExact() throws ExportFailure {
            if (consumed) {
                throw failure("pinned Scanned Sapling source was consumed twice");
            }
            consumed = true;
            authorization = new DisplayNameAuthorization();
            return authorization;
        }

        void requireComplete() throws ExportFailure {
            if (!consumed) {
                throw failure("export traversal did not consume the pinned Scanned Sapling "
                        + "source exactly once");
            }
            if (authorization == null || !authorization.isClaimed()) {
                throw failure("pinned Scanned Sapling display-name authorization was not "
                        + "claimed by the item catalog");
            }
        }

        private static boolean isExactLocus(
                String categoryId,
                int sourceIndex,
                String role,
                int slotIndex,
                int alternativeIndex) {
            return CATEGORY_ID.equals(categoryId)
                    && sourceIndex == SOURCE_INDEX
                    && ROLE.equals(role)
                    && slotIndex == SLOT_INDEX
                    && alternativeIndex == ALTERNATIVE_INDEX;
        }
    }

    private static final class Observation {
        final ItemStack visibleOutputAlternative;
        final String fingerprint;

        Observation(ItemStack visibleOutputAlternative, String canonicalFacts) {
            this.visibleOutputAlternative = visibleOutputAlternative;
            this.fingerprint = Naming.sha256(canonicalFacts);
        }
    }

    private GregTechForestryScannedSaplingPreflight() {
    }

    static Snapshot preflight(List<HandlerCategoryPlan> plans) throws ExportFailure {
        if (plans == null || plans.isEmpty()) {
            throw failure("no category plans were supplied");
        }
        HandlerCategoryPlan target = null;
        for (HandlerCategoryPlan plan : plans) {
            if (plan != null && CATEGORY_ID.equals(plan.categoryId)) {
                if (target != null) {
                    throw failure("duplicate pinned scanner category plan " + CATEGORY_ID);
                }
                target = plan;
            }
        }
        if (target == null) {
            throw failure("pinned scanner category is absent: " + CATEGORY_ID);
        }

        Observation observation = observe(target, target.loadCompleteCategory());
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Preflighted exact GregTech/Forestry Scanned Sapling "
                        + "source-bound display-name policy; contract={} categoryId={} "
                        + "sourceIndex={} role={} slotIndex={} alternativeIndex={} "
                        + "fingerprint={}",
                CONTRACT, CATEGORY_ID, SOURCE_INDEX, ROLE, SLOT_INDEX,
                ALTERNATIVE_INDEX, observation.fingerprint);
        return new Snapshot(target, observation.fingerprint);
    }

    private static Observation observe(
            HandlerCategoryPlan plan, ICraftingHandler loadedHandler) throws ExportFailure {
        try {
            requireCategoryBinding(plan, loadedHandler);
            GTNEIDefaultHandler prototype = (GTNEIDefaultHandler) plan.prototype;
            GTNEIDefaultHandler loaded = (GTNEIDefaultHandler) loadedHandler;
            RecipeMap<?> map = loaded.getRecipeMap();

            int rawOutputOccurrences = requireUniqueRawOutputOccurrence(loaded, map);

            TemplateRecipeHandler.CachedRecipe source = loaded.arecipes.get(SOURCE_INDEX);
            if (source == null
                    || source.getClass() != GTNEIDefaultHandler.CachedDefaultRecipe.class
                    || loaded.arecipes.get(SOURCE_INDEX) != source) {
                throw failure("scanner row 3 did not bind one exact CachedDefaultRecipe; got "
                        + className(source));
            }
            GTNEIDefaultHandler.CachedDefaultRecipe cached =
                    (GTNEIDefaultHandler.CachedDefaultRecipe) source;
            GTRecipe recipe = cached.mRecipe;
            if (recipe == null || recipe.getClass() != GTRecipe.class
                    || !containsIdentity(map.getAllRecipes(), recipe)) {
                throw failure("scanner row 3 is not an exact RecipeMap-owned GTRecipe");
            }
            if (prototype.getRecipeMap() != map) {
                throw failure("registered and loaded scanner handlers changed RecipeMap identity");
            }

            List<PositionedStack> ingredients = loaded.getIngredientStacks(SOURCE_INDEX);
            PositionedStack result = loaded.getResultStack(SOURCE_INDEX);
            List<PositionedStack> others = loaded.getOtherStacks(SOURCE_INDEX);
            if (cached.mInputs == null || cached.mOutputs == null
                    || ingredients != cached.mInputs
                    || result != cached.getResult()
                    || others != cached.mOutputs
                    || others != cached.getOtherStacks()) {
                throw failure("scanner row accessors are not identity-bound to row 3");
            }

            StackIdentity rawInput = requireSingleRawItem(
                    recipe.mInputs, "raw item input");
            requireSaplingIdentity(rawInput, Short.MAX_VALUE, null, RAW_WILDCARD_INPUT_KEY,
                    "raw wildcard input");
            if (rawInput.amount != 1) {
                throw failure("raw wildcard input amount drifted; got " + rawInput.amount);
            }

            StackIdentity rawOutput = requireSingleRawItem(
                    recipe.mOutputs, "raw item output");
            requireScannedSaplingIdentity(rawOutput, "raw output");
            requireRawHoney(recipe.mFluidInputs);
            if (recipe.mFluidOutputs != null && recipe.mFluidOutputs.length != 0) {
                throw failure("scanner row acquired raw fluid outputs; count="
                        + recipe.mFluidOutputs.length);
            }
            if (recipe.mDuration != 500 || recipe.mEUt != 2 || !recipe.mFakeRecipe
                    || !recipe.mEnabled || recipe.mHidden) {
                throw failure("scanner fake-recipe execution flags drifted; duration="
                        + recipe.mDuration + ", eut=" + recipe.mEUt + ", fake="
                        + recipe.mFakeRecipe + ", enabled=" + recipe.mEnabled + ", hidden="
                        + recipe.mHidden);
            }

            if (ingredients == null || ingredients.size() != 2) {
                throw failure("scanner row must expose genetic-item and honey input slots; got "
                        + (ingredients == null ? -1 : ingredients.size()));
            }
            PositionedStack geneticInput = ingredients.get(0);
            ItemStack[] geneticAlternatives = requireAlternatives(
                    geneticInput, "visible genetic input slot 0");
            if (geneticAlternatives.length != VISIBLE_GENETIC_INPUT_ALTERNATIVES) {
                throw failure("visible genetic input alternative count drifted; expected="
                        + VISIBLE_GENETIC_INPUT_ALTERNATIVES + ", got="
                        + geneticAlternatives.length);
            }
            List<String> geneticKeys = new ArrayList<String>(geneticAlternatives.length);
            for (int index = 0; index < geneticAlternatives.length; index++) {
                StackIdentity identity = requireIdentity(
                        geneticAlternatives[index], "visible genetic input alternative " + index);
                requireSaplingIdentity(identity, 0, identity.canonicalNbt, identity.key,
                        "visible genetic input alternative " + index);
                if (identity.canonicalNbt == null || identity.amount != 1) {
                    throw failure("visible genetic input alternative " + index
                            + " must carry a genome and amount=1");
                }
                geneticKeys.add(identity.key);
            }
            String geneticDigest = sortedKeyLfSha256(geneticKeys);
            if (!VISIBLE_GENETIC_INPUT_SORTED_KEY_LF_SHA256.equals(geneticDigest)) {
                throw failure("visible genetic input corpus drifted; expectedSha256="
                        + VISIBLE_GENETIC_INPUT_SORTED_KEY_LF_SHA256
                        + ", observedSha256=" + geneticDigest);
            }

            ItemStack[] honeyAlternatives = requireAlternatives(
                    ingredients.get(1), "visible honey input slot 1");
            if (honeyAlternatives.length != 1) {
                throw failure("visible honey input must have one alternative; got "
                        + honeyAlternatives.length);
            }
            StackIdentity visibleHoney = requireIdentity(
                    honeyAlternatives[0], "visible honey input alternative");
            if (!VISIBLE_HONEY_KEY.equals(visibleHoney.key)
                    || visibleHoney.amount != HONEY_AMOUNT) {
                throw failure("visible honey input drifted; key=" + visibleHoney.key
                        + ", amount=" + visibleHoney.amount);
            }

            if (result != null || others == null || others.size() != 1) {
                throw failure("scanner row must expose its item through one NEI output slot; "
                        + "resultPresent=" + (result != null) + ", otherCount="
                        + (others == null ? -1 : others.size()));
            }
            PositionedStack visibleOutput = others.get(SLOT_INDEX);
            ItemStack[] outputAlternatives = requireAlternatives(
                    visibleOutput, "visible output slot 0");
            if (outputAlternatives.length != 1) {
                throw failure("visible Scanned Sapling output must have one alternative; got "
                        + outputAlternatives.length);
            }
            ItemStack outputStack = outputAlternatives[ALTERNATIVE_INDEX];
            StackIdentity visibleOutputIdentity = requireIdentity(
                    outputStack, "visible output slot 0 alternative 0");
            requireScannedSaplingIdentity(visibleOutputIdentity, "visible output");
            if (!rawOutput.sameLogicalIdentity(visibleOutputIdentity)
                    || rawOutput.amount != visibleOutputIdentity.amount) {
                throw failure("raw and visible Scanned Sapling outputs diverged");
            }

            StringBuilder canonical = new StringBuilder(2048);
            frame(canonical, "contract", CONTRACT);
            frame(canonical, "categoryId", plan.categoryId);
            frame(canonical, "handlerClass", loaded.getClass().getName());
            frame(canonical, "handlerId", loaded.getHandlerId());
            frame(canonical, "operation", plan.loadIdentifier);
            frame(canonical, "overlay", loaded.getOverlayIdentifier());
            frame(canonical, "mapClass", map.getClass().getName());
            frame(canonical, "map", map.unlocalizedName);
            frame(canonical, "backendClass", map.getBackend().getClass().getName());
            frame(canonical, "frontendClass", map.getFrontend().getClass().getName());
            frame(canonical, "categoryRecipeCount", loaded.arecipes.size());
            frame(canonical, "rawScannedSaplingOutputOccurrences", rawOutputOccurrences);
            frame(canonical, "sourceIndex", SOURCE_INDEX);
            frame(canonical, "recipeClass", recipe.getClass().getName());
            frame(canonical, "rawInput", rawInput.key + "|amount=" + rawInput.amount);
            frame(canonical, "rawFluidInput", HONEY_FLUID_NAME + "|amount=" + HONEY_AMOUNT);
            frame(canonical, "rawOutput", rawOutput.key + "|amount=" + rawOutput.amount);
            frame(canonical, "duration", recipe.mDuration);
            frame(canonical, "eut", recipe.mEUt);
            frame(canonical, "fake", recipe.mFakeRecipe);
            frame(canonical, "enabled", recipe.mEnabled);
            frame(canonical, "hidden", recipe.mHidden);
            frame(canonical, "visibleInputSlots", ingredients.size());
            frame(canonical, "visibleGeneticAlternatives", geneticAlternatives.length);
            frame(canonical, "visibleGeneticSortedKeyLfSha256", geneticDigest);
            frame(canonical, "visibleHoney", visibleHoney.key + "|amount="
                    + visibleHoney.amount);
            frame(canonical, "visibleResultPresent", false);
            frame(canonical, "visibleOutputSlots", others.size());
            frame(canonical, "role", ROLE);
            frame(canonical, "slotIndex", SLOT_INDEX);
            frame(canonical, "alternativeIndex", ALTERNATIVE_INDEX);
            frame(canonical, "visibleOutput", visibleOutputIdentity.key + "|amount="
                    + visibleOutputIdentity.amount);
            return new Observation(outputStack, canonical.toString());
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_IDENTITY",
                    "GregTech/Forestry Scanned Sapling source preflight", error);
        }
    }

    private static void requireCategoryBinding(
            HandlerCategoryPlan plan, ICraftingHandler loaded) throws ExportFailure {
        if (plan == null || loaded == null) {
            throw failure("scanner category binding received a null plan/handler");
        }
        if (!CATEGORY_ID.equals(plan.categoryId)
                || plan.prototype == null
                || plan.prototype.getClass() != GTNEIDefaultHandler.class
                || loaded.getClass() != GTNEIDefaultHandler.class
                || !HANDLER_CLASS.equals(plan.prototype.getClass().getName())
                || !HANDLER_ID.equals(plan.handlerId)
                || !HANDLER_ID.equals(loaded.getHandlerId())
                || !OPERATION.equals(plan.loadIdentifier)
                || !OVERLAY.equals(plan.overlayIdentifier)
                || !OVERLAY.equals(loaded.getOverlayIdentifier())) {
            throw failure("pinned scanner handler/category binding drifted");
        }
        GTNEIDefaultHandler handler = (GTNEIDefaultHandler) loaded;
        RecipeMap<?> map = handler.getRecipeMap();
        if (map == null || map.getClass() != RecipeMap.class
                || map != RecipeMaps.scannerFakeRecipes
                || !OPERATION.equals(map.unlocalizedName)) {
            throw failure("pinned scanner RecipeMap binding drifted; map="
                    + (map == null ? "<null>" : map.unlocalizedName));
        }
        if (handler.arecipes == null
                || handler.numRecipes() != CATEGORY_RECIPE_COUNT
                || handler.arecipes.size() != CATEGORY_RECIPE_COUNT) {
            throw failure("pinned scanner category cardinality drifted; numRecipes="
                    + handler.numRecipes() + ", arecipes="
                    + (handler.arecipes == null ? -1 : handler.arecipes.size()));
        }
    }

    private static boolean containsIdentity(
            Iterable<GTRecipe> recipes, GTRecipe expected) throws ExportFailure {
        IdentityHashMap<GTRecipe, Boolean> identities =
                new IdentityHashMap<GTRecipe, Boolean>();
        for (GTRecipe recipe : recipes) {
            if (recipe == null) {
                throw failure("scanner RecipeMap contains a null raw recipe");
            }
            identities.put(recipe, Boolean.TRUE);
        }
        return identities.containsKey(expected);
    }

    private static int requireUniqueRawOutputOccurrence(
            GTNEIDefaultHandler loaded, RecipeMap<?> map) throws ExportFailure {
        IdentityHashMap<GTRecipe, Boolean> mapRecipes =
                new IdentityHashMap<GTRecipe, Boolean>();
        for (GTRecipe recipe : map.getAllRecipes()) {
            if (recipe == null) {
                throw failure("scanner RecipeMap contains a null raw recipe");
            }
            mapRecipes.put(recipe, Boolean.TRUE);
        }

        int occurrences = 0;
        for (int sourceIndex = 0; sourceIndex < loaded.arecipes.size(); sourceIndex++) {
            TemplateRecipeHandler.CachedRecipe source = loaded.arecipes.get(sourceIndex);
            if (source == null
                    || source.getClass() != GTNEIDefaultHandler.CachedDefaultRecipe.class) {
                throw failure("scanner row " + sourceIndex
                        + " did not bind exact CachedDefaultRecipe while auditing raw outputs; "
                        + "got " + className(source));
            }
            GTRecipe recipe = ((GTNEIDefaultHandler.CachedDefaultRecipe) source).mRecipe;
            if (recipe == null || !mapRecipes.containsKey(recipe)) {
                throw failure("scanner row " + sourceIndex
                        + " is not identity-bound to a RecipeMap-owned raw recipe");
            }
            if (recipe.mOutputs == null) {
                continue;
            }
            for (int outputIndex = 0; outputIndex < recipe.mOutputs.length; outputIndex++) {
                ItemStack output = recipe.mOutputs[outputIndex];
                if (output == null || output.getItem() == null) {
                    continue;
                }
                StackIdentity identity = requireIdentity(
                        output, "scanner raw output " + sourceIndex + "/" + outputIndex);
                if (!SCANNED_SAPLING_CANONICAL_KEY.equals(identity.key)) {
                    continue;
                }
                occurrences++;
                if (sourceIndex != SOURCE_INDEX || outputIndex != 0) {
                    throw failure("exact Scanned Sapling raw output appeared outside row 3/output "
                            + "0; sourceIndex=" + sourceIndex + ", outputIndex=" + outputIndex);
                }
                requireScannedSaplingIdentity(identity,
                        "scanner raw output occurrence " + sourceIndex + "/" + outputIndex);
            }
        }
        if (occurrences != EXPECTED_RECIPE_OCCURRENCES) {
            throw failure("exact Scanned Sapling raw output occurrence count drifted; expected="
                    + EXPECTED_RECIPE_OCCURRENCES + ", observed=" + occurrences);
        }
        return occurrences;
    }

    private static StackIdentity requireSingleRawItem(
            ItemStack[] stacks, String role) throws ExportFailure {
        if (stacks == null || stacks.length != 1 || stacks[0] == null
                || stacks[0].getItem() == null) {
            throw failure(role + " must contain exactly one nonnull stack; got "
                    + (stacks == null ? -1 : stacks.length));
        }
        return requireIdentity(stacks[0], role);
    }

    private static void requireRawHoney(FluidStack[] fluids) throws ExportFailure {
        if (fluids == null || fluids.length != 1 || fluids[0] == null
                || fluids[0].getFluid() == null
                || !HONEY_FLUID_NAME.equals(fluids[0].getFluid().getName())
                || fluids[0].amount != HONEY_AMOUNT || fluids[0].tag != null) {
            throw failure("raw scanner fluid input must be exactly for.honey x100 without NBT");
        }
    }

    private static ItemStack[] requireAlternatives(
            PositionedStack positioned, String label) throws ExportFailure {
        if (positioned == null) {
            throw failure(label + " is null");
        }
        if (positioned.items == null || positioned.items.length == 0) {
            try {
                positioned.generatePermutations();
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                throw new ExportFailure("ITEM_IDENTITY",
                        label + " could not generate alternatives", error);
            }
        }
        if (positioned.items == null || positioned.items.length == 0) {
            throw failure(label + " has no alternatives");
        }
        return positioned.items;
    }

    private static StackIdentity requireIdentity(ItemStack stack, String label)
            throws ExportFailure {
        try {
            return StackIdentity.of(stack);
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_IDENTITY", label + " is not canonical", error);
        }
    }

    private static void requireSaplingIdentity(
            StackIdentity identity,
            int metadata,
            String canonicalNbt,
            String canonicalKey,
            String label) throws ExportFailure {
        if (identity == null || identity.stack == null || identity.stack.getItem() == null
                || !FORESTRY_SAPLING_REGISTRY_ID.equals(identity.registryId)
                || !FORESTRY_GERMLING_CLASS.equals(
                        identity.stack.getItem().getClass().getName())
                || identity.metadata != metadata
                || !equalNullable(canonicalNbt, identity.canonicalNbt)
                || !canonicalKey.equals(identity.key)) {
            throw failure(label + " Forestry sapling identity drifted; got "
                    + (identity == null ? "<null>" : identity.key));
        }
    }

    private static void requireScannedSaplingIdentity(
            StackIdentity identity, String label) throws ExportFailure {
        requireSaplingIdentity(
                identity, 0, SCANNED_SAPLING_CANONICAL_NBT,
                SCANNED_SAPLING_CANONICAL_KEY, label);
        if (identity.amount != 1) {
            throw failure(label + " amount drifted; expected=1, got=" + identity.amount);
        }
    }

    static String sortedKeyLfSha256(List<String> keys) {
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null");
        }
        List<String> sorted = new ArrayList<String>(keys);
        Collections.sort(sorted);
        StringBuilder canonical = new StringBuilder(sorted.size() * 96);
        for (String key : sorted) {
            if (key == null) {
                throw new IllegalArgumentException("keys must not contain null");
            }
            canonical.append(key).append('\n');
        }
        return Naming.sha256(canonical.toString());
    }

    private static void frame(StringBuilder target, String name, Object value) {
        String text = String.valueOf(value);
        target.append(name.length()).append(':').append(name)
                .append('=').append(text.length()).append(':').append(text).append(';');
    }

    private static boolean equalNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String className(Object value) {
        return value == null ? "<null>" : value.getClass().getName();
    }

    private static String locus(
            String categoryId,
            int sourceIndex,
            String role,
            int slotIndex,
            int alternativeIndex) {
        return "categoryId=" + categoryId + ", sourceIndex=" + sourceIndex
                + ", role=" + role + ", slotIndex=" + slotIndex
                + ", alternativeIndex=" + alternativeIndex;
    }

    private static ExportFailure failure(String message) {
        return new ExportFailure("ITEM_IDENTITY", CONTRACT + ": " + message);
    }
}
