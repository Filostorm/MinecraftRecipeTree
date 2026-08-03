package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidContainerItem;

import java.awt.Rectangle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact Forestry 4.10.17 graph-semantic adapter for fluid-aware NEI categories.
 *
 * <p>Forestry renders fluids in {@code PositionedFluidTank}; NEI's normal
 * result/ingredient APIs do not expose those tanks as item stacks. This adapter reads the
 * exact pinned cached-recipe shapes, converts fluids to GregTech fluid-display proxies, and
 * attaches a graph-only semantic page to every rendered page. Reflection is intentional:
 * Forestry is a runtime-pinned pack component, not an exporter compile dependency.
 *
 * <p>The first runtime build must leave the corpus constants unpromoted. It will report the
 * complete count vector and SHA-256 and fail before rendering when the parent calls
 * {@link #requirePromotedCorpus()}. Both reviewed constants must then be promoted together.
 */
final class ForestryFluidSemanticAdapter {
    static final String CONTRACT =
            "gtnh-2.8.4-forestry-4.10.17-fluid-graph-semantics-v1";
    static final String UNPROMOTED = "<unpromoted>";

    // Promoted together after two byte-identical complete captures from exporter 1.0.62.
    static final String EXPECTED_COUNT_VECTOR =
            "Bottler{pages=2055,mainPages=2055,supplementalPages=0,expandedSourcePages=0,expandedPages=0,inputSlots=4110,outputSlots=2055,itemAlternatives=4110,fluidAlternatives=2055,zeroAmountFluidAlternatives=6,dynamicInputs=6,dynamicOutputs=0,planPrerequisites=0,previewOnlyItemSlots=0,supplementalPreviewCandidates=0,fuelRecords=0,probabilisticOutputs=0,zeroProbabilityPreviewOutputs=0,positiveChanceWithoutRemnantRows=0,dynamicBottlerInputs=6,excludedBottlerZeroCapacityPages=30,excludedBottlerPositiveDeltaZeroCapacityPages=29,fixedBottlerDeltaCapacityMismatches=0,normalizedBottlerContainerQuantityRows=1}" +
            ";Carpenter{pages=434,mainPages=434,supplementalPages=0,expandedSourcePages=0,expandedPages=0,inputSlots=3201,outputSlots=434,itemAlternatives=6092,fluidAlternatives=312,zeroAmountFluidAlternatives=0,dynamicInputs=0,dynamicOutputs=0,planPrerequisites=0,previewOnlyItemSlots=0,supplementalPreviewCandidates=0,fuelRecords=0,probabilisticOutputs=0,zeroProbabilityPreviewOutputs=0,positiveChanceWithoutRemnantRows=0,dynamicBottlerInputs=0,excludedBottlerZeroCapacityPages=0,excludedBottlerPositiveDeltaZeroCapacityPages=0,fixedBottlerDeltaCapacityMismatches=0,normalizedBottlerContainerQuantityRows=0}" +
            ";Fabricator{pages=100,mainPages=93,supplementalPages=7,expandedSourcePages=0,expandedPages=0,inputSlots=694,outputSlots=100,itemAlternatives=822,fluidAlternatives=100,zeroAmountFluidAlternatives=0,dynamicInputs=21,dynamicOutputs=0,planPrerequisites=21,previewOnlyItemSlots=93,supplementalPreviewCandidates=651,fuelRecords=0,probabilisticOutputs=0,zeroProbabilityPreviewOutputs=0,positiveChanceWithoutRemnantRows=0,dynamicBottlerInputs=0,excludedBottlerZeroCapacityPages=0,excludedBottlerPositiveDeltaZeroCapacityPages=0,fixedBottlerDeltaCapacityMismatches=0,normalizedBottlerContainerQuantityRows=0}" +
            ";Fermenter{pages=707,mainPages=707,supplementalPages=0,expandedSourcePages=0,expandedPages=0,inputSlots=2121,outputSlots=707,itemAlternatives=2933,fluidAlternatives=1414,zeroAmountFluidAlternatives=0,dynamicInputs=707,dynamicOutputs=0,planPrerequisites=0,previewOnlyItemSlots=0,supplementalPreviewCandidates=0,fuelRecords=3,probabilisticOutputs=0,zeroProbabilityPreviewOutputs=0,positiveChanceWithoutRemnantRows=0,dynamicBottlerInputs=0,excludedBottlerZeroCapacityPages=0,excludedBottlerPositiveDeltaZeroCapacityPages=0,fixedBottlerDeltaCapacityMismatches=0,normalizedBottlerContainerQuantityRows=0}" +
            ";Moistener{pages=49,mainPages=49,supplementalPages=0,expandedSourcePages=0,expandedPages=0,inputSlots=147,outputSlots=98,itemAlternatives=196,fluidAlternatives=49,zeroAmountFluidAlternatives=49,dynamicInputs=98,dynamicOutputs=49,planPrerequisites=0,previewOnlyItemSlots=0,supplementalPreviewCandidates=0,fuelRecords=7,probabilisticOutputs=0,zeroProbabilityPreviewOutputs=0,positiveChanceWithoutRemnantRows=0,dynamicBottlerInputs=0,excludedBottlerZeroCapacityPages=0,excludedBottlerPositiveDeltaZeroCapacityPages=0,fixedBottlerDeltaCapacityMismatches=0,normalizedBottlerContainerQuantityRows=0}" +
            ";Squeezer{pages=857,mainPages=275,supplementalPages=0,expandedSourcePages=2,expandedPages=582,inputSlots=862,outputSlots=1567,itemAlternatives=1572,fluidAlternatives=857,zeroAmountFluidAlternatives=0,dynamicInputs=0,dynamicOutputs=0,planPrerequisites=0,previewOnlyItemSlots=0,supplementalPreviewCandidates=0,fuelRecords=0,probabilisticOutputs=694,zeroProbabilityPreviewOutputs=0,positiveChanceWithoutRemnantRows=7,dynamicBottlerInputs=0,excludedBottlerZeroCapacityPages=0,excludedBottlerPositiveDeltaZeroCapacityPages=0,fixedBottlerDeltaCapacityMismatches=0,normalizedBottlerContainerQuantityRows=0}" +
            ";Still{pages=3,mainPages=3,supplementalPages=0,expandedSourcePages=0,expandedPages=0,inputSlots=3,outputSlots=3,itemAlternatives=0,fluidAlternatives=6,zeroAmountFluidAlternatives=0,dynamicInputs=0,dynamicOutputs=0,planPrerequisites=0,previewOnlyItemSlots=0,supplementalPreviewCandidates=0,fuelRecords=0,probabilisticOutputs=0,zeroProbabilityPreviewOutputs=0,positiveChanceWithoutRemnantRows=0,dynamicBottlerInputs=0,excludedBottlerZeroCapacityPages=0,excludedBottlerPositiveDeltaZeroCapacityPages=0,fixedBottlerDeltaCapacityMismatches=0,normalizedBottlerContainerQuantityRows=0}" +
            ";totalPages=4205";
    static final String EXPECTED_SHA256 =
            "5730d5e0d08163edf928f73b2965cecb095fad55bae79f32d2a3602da516acad";

    static final String BOTTLER =
            "forestry.factory.recipes.nei.NEIHandlerBottler";
    static final String CARPENTER =
            "forestry.factory.recipes.nei.NEIHandlerCarpenter";
    static final String FABRICATOR =
            "forestry.factory.recipes.nei.NEIHandlerFabricator";
    static final String FERMENTER =
            "forestry.factory.recipes.nei.NEIHandlerFermenter";
    static final String MOISTENER =
            "forestry.factory.recipes.nei.NEIHandlerMoistener";
    static final String SQUEEZER =
            "forestry.factory.recipes.nei.NEIHandlerSqueezer";
    static final String STILL =
            "forestry.factory.recipes.nei.NEIHandlerStill";

    private static final String RECIPE_HANDLER_BASE =
            "forestry.core.recipes.nei.RecipeHandlerBase";
    private static final String CACHED_BASE_RECIPE =
            RECIPE_HANDLER_BASE + "$CachedBaseRecipe";
    private static final String POSITIONED_FLUID_TANK =
            "forestry.core.recipes.nei.PositionedFluidTank";
    private static final String POSITIONED_STACK_ADV =
            "forestry.core.recipes.nei.PositionedStackAdv";
    private static final String FUEL_MANAGER = "forestry.api.fuels.FuelManager";
    private static final String FERMENTER_FUEL =
            "forestry.api.fuels.FermenterFuel";
    private static final String MOISTENER_FUEL =
            "forestry.api.fuels.MoistenerFuel";
    private static final String FABRICATOR_SMELTING_RECIPE_MANAGER =
            "forestry.factory.recipes.FabricatorSmeltingRecipeManager";
    private static final String FABRICATOR_SMELTING_RECIPE =
            "forestry.api.recipes.IFabricatorSmeltingRecipe";
    private static final String SQUEEZER_RECIPE_MANAGER =
            "forestry.factory.recipes.SqueezerRecipeManager";
    private static final String SQUEEZER_CONTAINER_RECIPE =
            "forestry.factory.recipes.ISqueezerContainerRecipe";
    private static final String SQUEEZER_RECIPE =
            "forestry.api.recipes.ISqueezerRecipe";
    private static final String RECIPE_MANAGERS =
            "forestry.api.recipes.RecipeManagers";
    private static final String BOTTLER_RECIPE =
            "forestry.factory.recipes.BottlerRecipe";
    private static final String FLUID_HELPER =
            "forestry.core.fluids.FluidHelper";
    private static final String SQUEEZER_MANAGER =
            "forestry.api.recipes.ISqueezerManager";

    private static final Map<String, HandlerSpec> SPECS;
    private static final List<String> HANDLER_ORDER;
    private static final Map<ICraftingHandler,
            List<CompleteCategoryAdapters.RecipeSemanticOverride>> SEMANTICS =
            new IdentityHashMap<ICraftingHandler,
                    List<CompleteCategoryAdapters.RecipeSemanticOverride>>();
    private static final Map<String, HandlerObservation> OBSERVATIONS =
            new LinkedHashMap<String, HandlerObservation>();

    static {
        Map<String, HandlerSpec> specs = new LinkedHashMap<String, HandlerSpec>();
        addSpec(specs, BOTTLER, "forestry.bottler", "CachedBottlerRecipe");
        addSpec(specs, CARPENTER, "forestry.carpenter", "CachedCarpenterRecipe");
        addSpec(specs, FABRICATOR, "forestry.fabricator", "CachedFabricatorRecipe");
        addSpec(specs, FERMENTER, "forestry.fermenter", "CachedFermenterRecipe");
        addSpec(specs, MOISTENER, "forestry.moistener", "CachedMoistenerRecipe");
        addSpec(specs, SQUEEZER, "forestry.squeezer", "CachedSqueezerRecipe");
        addSpec(specs, STILL, "forestry.still", "CachedStillRecipe");
        SPECS = Collections.unmodifiableMap(specs);
        HANDLER_ORDER = Collections.unmodifiableList(
                new ArrayList<String>(specs.keySet()));
    }

    private ForestryFluidSemanticAdapter() {
    }

    static boolean supports(String handlerClass) {
        return handlerClass != null && SPECS.containsKey(handlerClass);
    }

    static Set<String> supportedHandlerClasses() {
        return Collections.unmodifiableSet(new HashSet<String>(SPECS.keySet()));
    }

    /** Cheap exact class/member pins used during the parent's prototype audit. */
    static void validatePrototype(ICraftingHandler prototype) throws ExportFailure {
        if (prototype == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Forestry structural validation received a null prototype");
        }
        String handlerClass = prototype.getClass().getName();
        HandlerSpec spec = SPECS.get(handlerClass);
        if (spec == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "no pinned Forestry fluid adapter exists for " + handlerClass);
        }
        try {
            if (!(prototype instanceof TemplateRecipeHandler)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerClass
                        + " is no longer a TemplateRecipeHandler");
            }
            requireExactClass(prototype, handlerClass);
            if (prototype.numRecipes() != 0) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerClass
                        + " prototype must have zero loaded recipes; got "
                        + prototype.numRecipes());
            }
            ClassLoader loader = prototype.getClass().getClassLoader();
            Class<?> base = Class.forName(RECIPE_HANDLER_BASE, false, loader);
            if (prototype.getClass().getSuperclass() != base
                    || base.getSuperclass() != TemplateRecipeHandler.class) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerClass
                        + " exact RecipeHandlerBase hierarchy drifted");
            }
            Method recipeId = exactDeclaredMethod(
                    prototype.getClass(), "getRecipeID", String.class);
            String observedRecipeId = (String) recipeId.invoke(prototype);
            if (!spec.recipeId.equals(observedRecipeId)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerClass
                        + " recipe ID drifted; expected " + spec.recipeId
                        + ", got " + observedRecipeId);
            }
            exactDeclaredMethod(prototype.getClass(), "loadAllRecipes", void.class);
            validateSharedTankShape(loader);
            validateCachedShape(spec, loader);
            if (FABRICATOR.equals(handlerClass)) {
                validateFabricatorSources(loader);
            } else if (FERMENTER.equals(handlerClass)
                    || MOISTENER.equals(handlerClass)) {
                validateFuelSources(loader);
            } else if (SQUEEZER.equals(handlerClass)) {
                validateSqueezerSources(loader);
            }
        } catch (ExportFailure failure) {
            logFailure("prototype validation", handlerClass, failure);
            throw failure;
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            FatalErrors.rethrowIfFatal(cause);
            ExportFailure failure = new ExportFailure("HANDLER_UNLOADED",
                    handlerClass + " exact Forestry 4.10.17 structural validation failed",
                    cause);
            logFailure("prototype validation", handlerClass, failure);
            throw failure;
        }
    }

    /**
     * Loads one complete NEI category and attaches one graph override per rendered page.
     * Derived Fabricator-smelting and Moistener-fuel conversions append deterministic
     * supplemental pages backed by relevant upstream cached pages.
     */
    static ICraftingHandler loadCompleteCategory(ICraftingHandler prototype)
            throws ExportFailure {
        validatePrototype(prototype);
        String handlerClass = prototype.getClass().getName();
        HandlerSpec spec = SPECS.get(handlerClass);
        try {
            ICraftingHandler loaded = prototype.getRecipeHandler(spec.recipeId);
            requireExactClass(loaded, handlerClass);
            if (!(loaded instanceof TemplateRecipeHandler)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerClass
                        + " complete-category query returned a non-template handler");
            }
            TemplateRecipeHandler target = (TemplateRecipeHandler) loaded;
            if (target.numRecipes() <= 0) {
                throw new ExportFailure("HANDLER_UNLOADED", handlerClass
                        + " complete-category query returned no pages");
            }

            BuildResult result;
            if (BOTTLER.equals(handlerClass)) {
                result = buildBottler(target, spec);
            } else if (CARPENTER.equals(handlerClass)) {
                result = buildCarpenter(target, spec);
            } else if (FABRICATOR.equals(handlerClass)) {
                result = buildFabricator(target, spec);
            } else if (FERMENTER.equals(handlerClass)) {
                result = buildFermenter(target, spec);
            } else if (MOISTENER.equals(handlerClass)) {
                result = buildMoistener(target, spec);
            } else if (SQUEEZER.equals(handlerClass)) {
                result = buildSqueezer(target, spec);
            } else if (STILL.equals(handlerClass)) {
                result = buildStill(target, spec);
            } else {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "unreachable Forestry adapter dispatch for " + handlerClass);
            }

            if (target.numRecipes() != result.pages.size()) {
                throw new ExportFailure("RECIPE_SEMANTICS", handlerClass
                        + " preview/graph page counts diverged; previews="
                        + target.numRecipes() + ", graph=" + result.pages.size());
            }
            HandlerObservation observation = result.finish(handlerClass);
            synchronized (ForestryFluidSemanticAdapter.class) {
                SEMANTICS.put(target, Collections.unmodifiableList(
                        new ArrayList<CompleteCategoryAdapters.RecipeSemanticOverride>(
                                result.pages)));
                HandlerObservation previous = OBSERVATIONS.put(
                        handlerClass, observation);
                if (previous != null
                        && (!previous.countVector.equals(observation.countVector)
                        || !previous.fingerprint.equals(observation.fingerprint))) {
                    throw new ExportFailure("HANDLER_UNLOADED", handlerClass
                            + " changed across two complete-category captures in one boot; "
                            + "first=" + previous.countVector + "/" + previous.fingerprint
                            + ", second=" + observation.countVector + "/"
                            + observation.fingerprint);
                }
            }
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Forestry fluid semantic adapter ready: "
                            + "handler={}, countVector={}, fingerprint={}",
                    handlerClass, observation.countVector, observation.fingerprint);
            return target;
        } catch (ExportFailure failure) {
            logFailure("complete-category load", handlerClass, failure);
            throw failure;
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            FatalErrors.rethrowIfFatal(cause);
            ExportFailure failure = new ExportFailure("HANDLER_UNLOADED",
                    handlerClass + " exact Forestry fluid-semantic adapter failed", cause);
            logFailure("complete-category load", handlerClass, failure);
            throw failure;
        }
    }

    static synchronized CompleteCategoryAdapters.RecipeSemanticOverride semanticOverride(
            ICraftingHandler loadedHandler, int recipeIndex) throws ExportFailure {
        if (loadedHandler == null || !supports(loadedHandler.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Forestry semantic lookup received an unsupported handler");
        }
        List<CompleteCategoryAdapters.RecipeSemanticOverride> pages =
                SEMANTICS.get(loadedHandler);
        if (pages == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    loadedHandler.getClass().getName()
                            + " has no attached Forestry graph-semantic corpus");
        }
        if (pages.size() != loadedHandler.numRecipes()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    loadedHandler.getClass().getName()
                            + " preview/semantic corpus changed after attachment");
        }
        if (recipeIndex < 0 || recipeIndex >= pages.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    loadedHandler.getClass().getName()
                            + " semantic index is out of bounds: " + recipeIndex);
        }
        return pages.get(recipeIndex);
    }

    static synchronized List<CompleteCategoryAdapters.RecipeSemanticOverride>
            semanticOverrides(ICraftingHandler loadedHandler) throws ExportFailure {
        semanticOverride(loadedHandler, 0);
        return SEMANTICS.get(loadedHandler);
    }

    static boolean requiresDiscovery() {
        return !hasCompletePromotion(EXPECTED_COUNT_VECTOR, EXPECTED_SHA256);
    }

    /** Parent pre-render hook: requires all seven captures and enforces the promotion gate. */
    static synchronized CorpusObservation requirePromotedCorpus() throws ExportFailure {
        CorpusObservation observed = corpusObservation(OBSERVATIONS);
        requirePromotion(observed, EXPECTED_COUNT_VECTOR, EXPECTED_SHA256);
        return observed;
    }

    static void requirePromotionForTest(CorpusObservation observed,
                                        String expectedCountVector,
                                        String expectedSha256)
            throws ExportFailure {
        requirePromotion(observed, expectedCountVector, expectedSha256);
    }

    static void requireNoFluidTagForTest(NBTTagCompound tag)
            throws ExportFailure {
        rejectTaggedFluid(tag, "test fluid");
    }

    static int rawChanceBitsForTest(float chance) {
        return Float.floatToRawIntBits(chance);
    }

    /*
     * Forestry 4.10.17 builds five of these NEI categories from identity-hashed
     * HashSet/HashMap stores. Their presentation order can change between JVM boots even
     * when the exact semantic page multiset is unchanged. Promotion therefore binds a
     * length-framed, sorted multiset while live overrides remain aligned to arecipes by
     * their original source index. Sorting a copy preserves duplicate multiplicity.
     */
    static String stablePageMultisetFingerprint(
            String handlerClass, String countVector,
            List<String> pageCanonicals, String extraCanonical)
            throws ExportFailure {
        if (handlerClass == null || handlerClass.trim().isEmpty()
                || countVector == null || countVector.trim().isEmpty()
                || pageCanonicals == null || extraCanonical == null) {
            throw new ExportFailure("INTERNAL_ERROR",
                    "Forestry page-multiset fingerprint received incomplete state");
        }
        List<String> sorted = new ArrayList<String>(pageCanonicals.size());
        for (int index = 0; index < pageCanonicals.size(); index++) {
            String canonical = pageCanonicals.get(index);
            if (canonical == null) {
                throw new ExportFailure("INTERNAL_ERROR",
                        "Forestry page-multiset fingerprint received null page #" + index);
            }
            sorted.add(canonical);
        }
        Collections.sort(sorted);
        StringBuilder basis = new StringBuilder(
                Math.max(512, sorted.size() * 256));
        appendField(basis, CONTRACT);
        appendField(basis, handlerClass);
        appendField(basis, countVector);
        appendField(basis, Integer.toString(sorted.size()));
        for (String canonical : sorted) appendField(basis, canonical);
        appendField(basis, extraCanonical);
        return sha256(basis.toString());
    }

    static void requireLegacyFermenterResourceNullForTest(Object legacyResource)
            throws ExportFailure {
        requireLegacyFermenterResourceNull(legacyResource);
    }

    static String classifySqueezerRemnantForTest(
            boolean hasRemnant, float chance) throws ExportFailure {
        return classifySqueezerRemnant(
                hasRemnant, chance, "test Squeezer source").name();
    }

    static String classifyBottlerFlowForTest(
            boolean dynamicContainer, int representativeDelta,
            int runtimeCapacity) throws ExportFailure {
        return classifyBottlerFlow(dynamicContainer, representativeDelta,
                runtimeCapacity, "test Bottler source").name();
    }

    static List<String> deterministicCrossWalkForTest(
            List<String> cachedShapes, List<String> sourceShapes)
            throws ExportFailure {
        Map<String, List<Object>> buckets =
                new LinkedHashMap<String, List<Object>>();
        for (String cached : cachedShapes) {
            addShapeBucket(buckets, cached, cached);
        }
        List<String> matched = new ArrayList<String>();
        for (String source : sourceShapes) {
            matched.add((String) takeShapeMatch(
                    buckets, source, "test source shape"));
        }
        requireEmptyShapeBuckets(buckets, "test cached shapes");
        Collections.sort(matched);
        return matched;
    }

    private static BuildResult buildBottler(TemplateRecipeHandler target,
                                             HandlerSpec spec) throws Exception {
        BuildResult result = new BuildResult();
        List<BottlerPage> pages = crossWalkBottlerPages(target, spec);
        Class<?> helper = Class.forName(FLUID_HELPER, false,
                target.getClass().getClassLoader());
        Method getFluidCapacity = exactPublicStaticMethod(
                helper, "getFluidCapacity", int.class,
                Fluid.class, ItemStack.class);
        Method getFilledContainer = exactPublicStaticMethod(
                helper, "getFilledContainer", ItemStack.class,
                FluidStack.class, ItemStack.class);
        List<Object> retained = new ArrayList<Object>(pages.size());
        List<String> excluded = new ArrayList<String>();
        List<String> fixedDeltaCapacityMismatches = new ArrayList<String>();
        List<String> normalizedContainerQuantities = new ArrayList<String>();
        for (BottlerPage source : pages) {
            Object cached = source.cached;
            Object tank = requiredTank(cached, "fluid", 48, 6, 16, 58,
                    10000, true);
            PositionedStack input = requiredPositioned(cached, "input", 111, 8);
            PositionedStack output = requiredPositioned(cached, "output", 111, 44);
            ItemStack inputItem = onlyPositionedItem(input, "Bottler container input");
            ItemStack outputItem = onlyPositionedItem(output,
                    "Bottler filled-container output");
            boolean dynamicContainer =
                    inputItem.getItem() instanceof IFluidContainerItem;
            int representativeDelta = source.input.amount;
            // FluidHelper.fillContainers always consumes one input container and
            // produces one output container. Registry stack sizes are lookup metadata;
            // some GTNH registrations publish zero-sized representatives.
            ItemStack runtimeInput = inputItem.copy();
            runtimeInput.stackSize = 1;
            int runtimeCapacity = ((Integer) getFluidCapacity.invoke(
                    null, source.input.getFluid(), runtimeInput)).intValue();
            BottlerFlowDisposition disposition = classifyBottlerFlow(
                    dynamicContainer, representativeDelta,
                    runtimeCapacity, "Bottler source");
            if (disposition
                    == BottlerFlowDisposition.EXCLUDED_ZERO_CAPACITY_PAGE) {
                result.excludedBottlerZeroCapacityPages++;
                if (representativeDelta > 0) {
                    result.excludedBottlerPositiveDeltaZeroCapacityPages++;
                }
                excluded.add(source.canonical);
                continue;
            }

            if (disposition == BottlerFlowDisposition.EXACT_FIXED_FLOW) {
                Object runtimeFilled = getFilledContainer.invoke(null,
                        new FluidStack(source.input.getFluid(), runtimeCapacity),
                        runtimeInput.copy());
                if (!(runtimeFilled instanceof ItemStack)
                        || !itemCanonical((ItemStack) runtimeFilled, 1).equals(
                        itemCanonical(outputItem, 1))) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "Bottler fixed-container runtime fill result diverged from "
                                    + "the pinned source/cached output");
                }
                if (representativeDelta != runtimeCapacity) {
                    result.fixedBottlerDeltaCapacityMismatches++;
                    fixedDeltaCapacityMismatches.add(source.canonical
                            + "|runtimeCapacity=" + runtimeCapacity);
                }
            }
            if (inputItem.stackSize != 1 || outputItem.stackSize != 1) {
                result.normalizedBottlerContainerQuantityRows++;
                normalizedContainerQuantities.add(source.canonical);
            }

            List<CompleteCategoryAdapters.SemanticSlot> inputs =
                    new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
            int fluidAmountOverride = disposition
                    == BottlerFlowDisposition.DYNAMIC_UNKNOWN_FLOW
                    ? 0 : runtimeCapacity;
            inputs.add(fluidSlot(tank, fluidAmountOverride, result,
                    "Bottler fluid input"));
            inputs.add(itemSlot(input, 1, result, "Bottler container input"));
            List<CompleteCategoryAdapters.SemanticSlot> outputs =
                    Collections.singletonList(itemSlot(output, 1, result,
                            "Bottler filled-container output"));
            String metadata;
            if (disposition == BottlerFlowDisposition.DYNAMIC_UNKNOWN_FLOW) {
                result.dynamicInputs++;
                result.dynamicBottlerInputs++;
                metadata = "flow=dynamic-interface;representativeDelta="
                        + representativeDelta + ";runtimeCapacity="
                        + runtimeCapacity + ";sourceInputQuantity="
                        + inputItem.stackSize + ";sourceOutputQuantity="
                        + outputItem.stackSize + ';';
            } else {
                metadata = "flow=exact-fixed;representativeDelta="
                        + representativeDelta + ";runtimeCapacity="
                        + runtimeCapacity + ";sourceInputQuantity="
                        + inputItem.stackSize + ";sourceOutputQuantity="
                        + outputItem.stackSize + ';';
            }
            result.addPage(page("bottler", inputs, outputs, result, metadata));
            retained.add(cached);
        }
        replaceCachedObjects(target, retained);
        result.mainPages = retained.size();
        if (result.dynamicBottlerInputs > 0) {
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Bottler stateful IFluidContainerItem rows "
                            + "retained with explicit ZERO_UNKNOWN_FLOW inputs: rows={}; "
                            + "representative deltas remain fingerprinted",
                    result.dynamicBottlerInputs);
        }
        if (!fixedDeltaCapacityMismatches.isEmpty()) {
            Collections.sort(fixedDeltaCapacityMismatches);
            StringBuilder mismatchBasis = new StringBuilder();
            for (String canonical : fixedDeltaCapacityMismatches) {
                result.extraCanonical.append("BD|").append(canonical).append('\n');
                mismatchBasis.append(canonical).append('\n');
            }
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Bottler fixed-container source deltas differed "
                            + "from executable runtime capacities; runtime capacity won "
                            + "after exact filled-output validation: rows={}, "
                            + "canonicalSha256={}",
                    fixedDeltaCapacityMismatches.size(),
                    sha256(mismatchBasis.toString()));
        }
        if (!normalizedContainerQuantities.isEmpty()) {
            Collections.sort(normalizedContainerQuantities);
            StringBuilder quantityBasis = new StringBuilder();
            for (String canonical : normalizedContainerQuantities) {
                result.extraCanonical.append("BQ|").append(canonical).append('\n');
                quantityBasis.append(canonical).append('\n');
            }
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Bottler registry container quantities were "
                            + "normalized to the executable one-container transition: "
                            + "rows={}, canonicalSha256={}",
                    normalizedContainerQuantities.size(),
                    sha256(quantityBasis.toString()));
        }
        if (!excluded.isEmpty()) {
            Collections.sort(excluded);
            StringBuilder excludedBasis = new StringBuilder();
            for (String canonical : excluded) {
                result.extraCanonical.append("BX|").append(canonical).append('\n');
                excludedBasis.append(canonical).append('\n');
            }
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Bottler non-executable zero-capacity registry "
                            + "pages were removed from preview and graph output: "
                            + "rows={}, positiveDeltaRows={}, canonicalSha256={}",
                    excluded.size(),
                    result.excludedBottlerPositiveDeltaZeroCapacityPages,
                    sha256(excludedBasis.toString()));
        }
        return result;
    }

    private static List<BottlerPage> crossWalkBottlerPages(
            TemplateRecipeHandler target, HandlerSpec spec) throws Exception {
        List<?> cachedPages = exactCachedPages(target, spec);
        Map<String, List<Object>> cachedByShape =
                new LinkedHashMap<String, List<Object>>();
        for (Object cached : cachedPages) {
            Object tank = requiredTank(cached, "fluid", 48, 6, 16, 58,
                    10000, true);
            ItemStack input = onlyPositionedItem(
                    requiredPositioned(cached, "input", 111, 8),
                    "Bottler cached input");
            ItemStack output = onlyPositionedItem(
                    requiredPositioned(cached, "output", 111, 44),
                    "Bottler cached output");
            String shape = bottlerCanonical(onlyFluid(tank,
                    "Bottler cached fluid"), input, output);
            addShapeBucket(cachedByShape, shape, cached);
        }

        ClassLoader loader = target.getClass().getClassLoader();
        Class<?> recipeType = Class.forName(BOTTLER_RECIPE, false, loader);
        Field inputField = exactPublicField(recipeType, "input", FluidStack.class);
        Field emptyField = exactPublicField(recipeType, "empty", ItemStack.class);
        Field filledField = exactPublicField(recipeType, "filled", ItemStack.class);
        if (!Modifier.isFinal(inputField.getModifiers())
                || !Modifier.isFinal(emptyField.getModifiers())
                || !Modifier.isFinal(filledField.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    BOTTLER_RECIPE + " source tuple fields are no longer final");
        }
        Field recipesField = exactDeclaredField(
                target.getClass(), "recipes", List.class);
        if (!Modifier.isStatic(recipesField.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    BOTTLER + ".recipes is no longer static");
        }
        Object raw = recipesField.get(null);
        if (!(raw instanceof List)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    BOTTLER + ".recipes did not expose a List at runtime");
        }
        List<?> sources = (List<?>) raw;
        if (sources.size() != cachedPages.size()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Bottler source/cached counts diverged; sources="
                            + sources.size() + ", cached=" + cachedPages.size());
        }
        List<BottlerPage> pages = new ArrayList<BottlerPage>(sources.size());
        for (Object source : sources) {
            if (source == null || source.getClass() != recipeType) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "Bottler source list contains an unexpected recipe class");
            }
            FluidStack input = (FluidStack) inputField.get(source);
            ItemStack empty = (ItemStack) emptyField.get(source);
            ItemStack filled = (ItemStack) filledField.get(source);
            String canonical = bottlerCanonical(input, empty, filled);
            Object cached = takeShapeMatch(cachedByShape, canonical,
                    "Bottler source tuple");
            pages.add(new BottlerPage(cached, input.copy(), canonical));
        }
        requireEmptyShapeBuckets(cachedByShape,
                "unmatched Bottler cached pages");
        Collections.sort(pages, new Comparator<BottlerPage>() {
            @Override
            public int compare(BottlerPage left, BottlerPage right) {
                return left.canonical.compareTo(right.canonical);
            }
        });
        return pages;
    }

    private static String bottlerCanonical(
            FluidStack input, ItemStack empty, ItemStack filled)
            throws ExportFailure {
        if (input == null || input.getFluid() == null || input.amount < 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Bottler source contains an invalid representative FluidStack");
        }
        rejectTaggedFluid(input.tag, "Bottler source fluid ("
                + FluidRegistry.getFluidName(input) + ")");
        String fluidName = FluidRegistry.getFluidName(input);
        if (fluidName == null || fluidName.trim().isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Bottler source contains an unregistered fluid");
        }
        StringBuilder canonical = new StringBuilder();
        appendField(canonical, "fluid|" + fluidName + "|amount=" + input.amount);
        appendField(canonical, itemCanonical(empty, -1));
        appendField(canonical, itemCanonical(filled, -1));
        return canonical.toString();
    }

    private static ItemStack onlyPositionedItem(
            PositionedStack positioned, String label) throws ExportFailure {
        if (positioned == null || positioned.items == null
                || positioned.items.length != 1
                || positioned.items[0] == null
                || positioned.items[0].getItem() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " must expose one exact item state");
        }
        return positioned.items[0];
    }

    private static BuildResult buildCarpenter(TemplateRecipeHandler target,
                                               HandlerSpec spec) throws Exception {
        BuildResult result = new BuildResult();
        List<?> cachedPages = exactCachedPages(target, spec);
        for (Object cached : cachedPages) {
            List<CompleteCategoryAdapters.SemanticSlot> inputs = itemSlots(
                    requiredPositionedList(cached, "inputs"), -1, result,
                    "Carpenter item input");
            Object tank = field(spec.cachedClass(target.getClass().getClassLoader()),
                    "tank").get(cached);
            if (tank != null) {
                inputs.add(fluidSlot(requireTank(tank, 145, 3, 16, 58,
                        10000, true, "Carpenter fluid input"), -1, result,
                        "Carpenter fluid input"));
            }
            List<CompleteCategoryAdapters.SemanticSlot> outputs =
                    Collections.singletonList(itemSlot(
                            requiredPositioned(cached, "output", 75, 37),
                            -1, result, "Carpenter output"));
            result.addPage(page("carpenter", inputs, outputs, result));
        }
        result.mainPages = cachedPages.size();
        return result;
    }

    private static BuildResult buildFabricator(TemplateRecipeHandler target,
                                                HandlerSpec spec) throws Exception {
        BuildResult result = new BuildResult();
        List<?> cachedPages = exactCachedPages(target, spec);
        List<Object> mainCachedPages = new ArrayList<Object>(cachedPages.size());
        Map<Object, String> mainSemanticIds =
                new IdentityHashMap<Object, String>();
        for (Object cached : cachedPages) {
            mainCachedPages.add(cached);
            List<CompleteCategoryAdapters.SemanticSlot> inputs =
                    new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
            List<PositionedStack> itemInputs = requiredPositionedList(cached, "inputs");
            for (PositionedStack positioned : itemInputs) {
                boolean plan = positioned.relx == 134 && positioned.rely == 6;
                if (!plan && !(positioned.relx >= 62 && positioned.relx <= 98
                        && positioned.rely >= 6 && positioned.rely <= 42)) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "Fabricator item-input position drifted: x=" + positioned.relx
                                    + ", y=" + positioned.rely);
                }
                inputs.add(itemSlot(positioned, plan ? 0 : -1, result,
                        plan ? "Fabricator reusable/mutable plan prerequisite"
                                : "Fabricator crafting-grid input"));
                if (plan) {
                    result.dynamicInputs++;
                    result.planPrerequisites++;
                }
            }
            Object tank = field(spec.cachedClass(target.getClass().getClassLoader()),
                    "tank").get(cached);
            List<PositionedStack> smeltingPreview =
                    requiredPositionedList(cached, "smeltingInput");
            if (tank != null) {
                inputs.add(fluidSlot(requireTank(tank, 21, 37, 16, 16,
                        2000, true, "Fabricator liquid input"), -1, result,
                        "Fabricator liquid input"));
                if (smeltingPreview.size() != 1
                        || smeltingPreview.get(0).relx != 21
                        || smeltingPreview.get(0).rely != 10) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "Fabricator liquid page lost its one smelting-source preview slot");
                }
                result.previewOnlyItemSlots += smeltingPreview.size();
            } else if (!smeltingPreview.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "Fabricator exposes smelting-source previews without a liquid tank");
            }
            List<CompleteCategoryAdapters.SemanticSlot> outputs =
                    Collections.singletonList(itemSlot(
                            requiredPositioned(cached, "output", 134, 42),
                            -1, result, "Fabricator output"));
            CompleteCategoryAdapters.RecipeSemanticOverride mainPage =
                    page("fabricator-main", inputs, outputs, result);
            result.addPage(mainPage);
            mainSemanticIds.put(cached, mainPage.semanticId);
        }
        result.mainPages = cachedPages.size();

        List<FabricatorSmeltingRecord> smelting =
                captureFabricatorSmeltingRecords(target.getClass().getClassLoader());
        for (FabricatorSmeltingRecord record : smelting) {
            Object preview = relevantFabricatorPreview(
                    mainCachedPages, mainSemanticIds, spec, record, result);
            List<CompleteCategoryAdapters.SemanticSlot> inputs =
                    Collections.singletonList(singleItemSlot(
                            record.resource, -1, result,
                            "Fabricator smelting resource"));
            List<CompleteCategoryAdapters.SemanticSlot> outputs =
                    Collections.singletonList(singleFluidSlot(
                            record.product, -1, result,
                            "Fabricator smelting product"));
            String metadata = "meltingPoint=" + record.meltingPoint + ';';
            result.addSupplementalPage(target, preview,
                    page("fabricator-smelting", inputs, outputs, result, metadata));
            result.extraCanonical.append("FS|").append(record.canonical).append('\n');
        }
        result.supplementalPages = smelting.size();
        return result;
    }

    private static BuildResult buildFermenter(TemplateRecipeHandler target,
                                               HandlerSpec spec) throws Exception {
        BuildResult result = new BuildResult();
        ClassLoader loader = target.getClass().getClassLoader();
        FuelCorpus fuels = captureFermenterFuels(loader);
        List<?> cachedPages = exactCachedPages(target, spec);
        for (Object cached : cachedPages) {
            List<PositionedStack> itemInputs =
                    requiredPositionedList(cached, "inputItems");
            if (itemInputs.size() != 2
                    || itemInputs.get(0).relx != 80 || itemInputs.get(0).rely != 8
                    || itemInputs.get(1).relx != 70 || itemInputs.get(1).rely != 42) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "Fermenter resource/fuel input topology drifted");
            }
            Object legacyResource = field(
                    spec.cachedClass(target.getClass().getClassLoader()),
                    "resource").get(cached);
            requireLegacyFermenterResourceNull(legacyResource);
            requireAlternativeSet(itemInputs.get(1), fuels.itemCanonicals,
                    "Fermenter amortized fuel alternatives");

            List<?> tanks = requiredListField(cached, "tanks");
            Object inputTank = uniqueTankAt(tanks, 30, 4, "Fermenter input");
            Object outputTank = uniqueTankAt(tanks, 120, 4, "Fermenter output");
            if (tanks.size() != 2) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "Fermenter must expose exactly input/output fluid tanks; got "
                                + tanks.size());
            }
            List<CompleteCategoryAdapters.SemanticSlot> inputs =
                    new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
            inputs.add(itemSlot(itemInputs.get(0), -1, result,
                    "Fermenter resource input"));
            inputs.add(itemSlot(itemInputs.get(1), 0, result,
                    "Fermenter amortized fuel prerequisite"));
            result.dynamicInputs++;
            inputs.add(fluidSlot(requireTank(inputTank, 30, 4, 16, 58,
                    10000, true, "Fermenter fluid input"), -1, result,
                    "Fermenter fluid input"));
            List<CompleteCategoryAdapters.SemanticSlot> outputs =
                    Collections.singletonList(fluidSlot(requireTank(
                            outputTank, 120, 4, 16, 58, 10000, true,
                            "Fermenter fluid output"), -1, result,
                            "Fermenter fluid output"));
            result.addPage(page("fermenter", inputs, outputs, result));
        }
        result.mainPages = cachedPages.size();
        result.extraCanonical.append(fuels.canonical);
        result.fuelRecords = fuels.records;
        return result;
    }

    private static BuildResult buildMoistener(TemplateRecipeHandler target,
                                               HandlerSpec spec) throws Exception {
        BuildResult result = new BuildResult();
        ClassLoader loader = target.getClass().getClassLoader();
        MoistenerFuelCorpus fuels = captureMoistenerFuels(loader);
        List<?> cachedPages = exactCachedPages(target, spec);
        for (Object cached : cachedPages) {
            List<PositionedStack> fuelPair = requiredPositionedList(cached, "fuels");
            if (fuelPair.size() != 2
                    || fuelPair.get(0).relx != 34 || fuelPair.get(0).rely != 47
                    || fuelPair.get(1).relx != 100 || fuelPair.get(1).rely != 26) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "Moistener selected fuel/product topology drifted");
            }
            MoistenerFuelRecord fuel = fuels.requirePair(
                    fuelPair.get(0), fuelPair.get(1));
            Object tank = requiredTank(cached, "tank", 11, 5, 16, 58,
                    10000, false);
            FluidStack water = onlyFluid(tank, "Moistener water tank");
            if (water.getFluid() != FluidRegistry.WATER) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "Moistener dynamic tank is no longer Forge water");
            }
            List<CompleteCategoryAdapters.SemanticSlot> inputs =
                    new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
            inputs.add(itemSlot(requiredPositioned(cached, "input", 138, 8),
                    -1, result, "Moistener primary resource"));
            inputs.add(fluidSlot(tank, 0, result,
                    "Moistener unknown dynamic water flow"));
            inputs.add(itemSlot(fuelPair.get(0), 0, result,
                    "Moistener unknown amortized fuel flow"));
            result.dynamicInputs += 2;
            List<CompleteCategoryAdapters.SemanticSlot> outputs =
                    new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
            outputs.add(itemSlot(requiredPositioned(cached, "output", 138, 44),
                    -1, result, "Moistener primary product"));
            outputs.add(itemSlot(fuelPair.get(1), 0, result,
                    "Moistener unknown amortized fuel product flow"));
            result.dynamicOutputs++;
            String metadata = "moistenerValue=" + fuel.moistenerValue
                    + ";stage=" + fuel.stage + ';';
            result.addPage(page("moistener", inputs, outputs, result, metadata));
        }
        int primaryRecipeCount = captureMoistenerPrimaryRecipeCount(loader,
                result.extraCanonical);
        long expectedRows = (long) primaryRecipeCount * fuels.records.size();
        if (cachedPages.size() != expectedRows) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Moistener R×F page contract drifted; recipes=" + primaryRecipeCount
                            + ", fuels=" + fuels.records.size() + ", expectedRows="
                            + expectedRows + ", loadedRows=" + cachedPages.size());
        }
        result.mainPages = cachedPages.size();
        result.fuelRecords = fuels.records.size();
        result.extraCanonical.append(fuels.canonical);
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Moistener ZERO_UNKNOWN_FLOW roles retained: "
                        + "loadedRows={} (recipes={} x fuels={}); waterInputs={}, "
                        + "fuelInputs={}, fuelProductOutputs={}",
                cachedPages.size(), primaryRecipeCount, fuels.records.size(),
                cachedPages.size(), cachedPages.size(), cachedPages.size());
        return result;
    }

    private static BuildResult buildSqueezer(TemplateRecipeHandler target,
                                              HandlerSpec spec) throws Exception {
        BuildResult result = new BuildResult();
        SqueezerExpansion expansion = expandSqueezerPages(target, spec, result);
        for (SqueezerPage source : expansion.pages) {
            Object cached = source.cached;
            List<CompleteCategoryAdapters.SemanticSlot> inputs = itemSlots(
                    requiredPositionedList(cached, "inputs"), -1, result,
                    "Squeezer item input");
            Object tank = requiredTank(cached, "tank", 117, 7, 16, 58,
                    10000, true);
            List<CompleteCategoryAdapters.SemanticSlot> outputs =
                    new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
            outputs.add(fluidSlot(tank, -1, result, "Squeezer fluid output"));
            Object remnants = field(spec.cachedClass(
                    target.getClass().getClassLoader()), "remnants").get(cached);
            float chance = source.remnantsChance;
            SqueezerRemnantDisposition disposition = classifySqueezerRemnant(
                    remnants != null, chance, "Squeezer page");
            result.extraCanonical.append("SR|")
                    .append(Float.floatToRawIntBits(chance)).append('|')
                    .append(remnants == null ? "none" : positionedCanonical(
                            (PositionedStack) remnants, -1)).append('\n');
            if (disposition == SqueezerRemnantDisposition.ABSENT_IDENTITY) {
                if (chance > 0.0f) {
                    // Forestry retains a default/source chance on some recipes that have
                    // no remnant identity. A probability alone cannot form an output slot;
                    // retain its raw bits in the corpus fingerprint and count the omission.
                    result.positiveChanceWithoutRemnantRows++;
                }
            } else if (disposition == SqueezerRemnantDisposition.STOCHASTIC
                    || disposition == SqueezerRemnantDisposition.DETERMINISTIC) {
                CompleteCategoryAdapters.SemanticSlot remnant = itemSlot(
                        (PositionedStack) remnants, -1, result,
                        "Squeezer stochastic remnant");
                if (disposition == SqueezerRemnantDisposition.STOCHASTIC) {
                    remnant = new CompleteCategoryAdapters.SemanticSlot(
                            remnant.alternatives, (double) chance);
                    result.probabilisticOutputs++;
                }
                outputs.add(remnant);
            } else if (disposition
                    == SqueezerRemnantDisposition.ZERO_PROBABILITY_PREVIEW) {
                result.zeroProbabilityPreviewOutputs++;
            } else {
                throw new ExportFailure("INTERNAL_ERROR",
                        "unreachable Squeezer remnant disposition " + disposition);
            }
            result.addPage(page("squeezer", inputs, outputs, result,
                    "sourceKind=" + source.sourceKind
                            + ";remnantsChanceBits="
                            + Float.floatToRawIntBits(chance) + ';'));
        }
        result.mainPages = expansion.regularPages;
        result.expandedSourcePages = expansion.containerSourcePages;
        result.expandedPages = expansion.expandedContainerPages;
        if (result.positiveChanceWithoutRemnantRows > 0) {
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Squeezer rows retained positive remnant chance "
                            + "metadata without a remnant output identity: rows={}; "
                            + "no output slot was synthesized and raw chance bits remain "
                            + "in the deterministic corpus fingerprint",
                    result.positiveChanceWithoutRemnantRows);
        }
        return result;
    }

    private static BuildResult buildStill(TemplateRecipeHandler target,
                                           HandlerSpec spec) throws Exception {
        BuildResult result = new BuildResult();
        List<?> cachedPages = exactCachedPages(target, spec);
        for (Object cached : cachedPages) {
            List<?> tanks = requiredListField(cached, "tanks");
            if (tanks.size() != 2) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "Still must expose exactly input/output fluid tanks; got "
                                + tanks.size());
            }
            Object inputTank = uniqueTankAt(tanks, 30, 4, "Still input");
            Object outputTank = uniqueTankAt(tanks, 120, 4, "Still output");
            List<CompleteCategoryAdapters.SemanticSlot> inputs =
                    Collections.singletonList(fluidSlot(requireTank(
                            inputTank, 30, 4, 16, 58, 10000, true,
                            "Still fluid input"), -1, result,
                            "Still fluid input"));
            List<CompleteCategoryAdapters.SemanticSlot> outputs =
                    Collections.singletonList(fluidSlot(requireTank(
                            outputTank, 120, 4, 16, 58, 10000, true,
                            "Still fluid output"), -1, result,
                            "Still fluid output"));
            result.addPage(page("still", inputs, outputs, result));
        }
        result.mainPages = cachedPages.size();
        return result;
    }

    private static CompleteCategoryAdapters.RecipeSemanticOverride page(
            String kind,
            List<CompleteCategoryAdapters.SemanticSlot> inputs,
            List<CompleteCategoryAdapters.SemanticSlot> outputs,
            BuildResult result) throws ExportFailure {
        return page(kind, inputs, outputs, result, "");
    }

    private static CompleteCategoryAdapters.RecipeSemanticOverride page(
            String kind,
            List<CompleteCategoryAdapters.SemanticSlot> inputs,
            List<CompleteCategoryAdapters.SemanticSlot> outputs,
            BuildResult result,
            String metadata) throws ExportFailure {
        if (inputs == null || inputs.isEmpty() || outputs == null || outputs.isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Forestry " + kind + " page must expose nonempty inputs and outputs");
        }
        String canonical = semanticCanonical(kind, inputs, outputs, metadata);
        String semanticId = "forestry:" + kind + ':' + Naming.sha256(canonical);
        result.pageCanonicals.add(canonical);
        result.inputSlots += inputs.size();
        result.outputSlots += outputs.size();
        return new CompleteCategoryAdapters.RecipeSemanticOverride(
                semanticId, inputs, outputs);
    }

    private static String semanticCanonical(
            String kind,
            List<CompleteCategoryAdapters.SemanticSlot> inputs,
            List<CompleteCategoryAdapters.SemanticSlot> outputs,
            String metadata) {
        StringBuilder canonical = new StringBuilder(512);
        appendField(canonical, kind);
        appendField(canonical, metadata);
        appendSlots(canonical, 'I', inputs);
        appendSlots(canonical, 'O', outputs);
        canonical.append('\n');
        return canonical.toString();
    }

    private static void appendSlots(
            StringBuilder canonical, char role,
            List<CompleteCategoryAdapters.SemanticSlot> slots) {
        canonical.append(role).append(slots.size()).append(';');
        for (CompleteCategoryAdapters.SemanticSlot slot : slots) {
            canonical.append('S').append(slot.alternatives.size()).append(';');
            canonical.append('P');
            if (slot.probability == null) {
                canonical.append('-');
            } else {
                canonical.append(Double.doubleToLongBits(
                        slot.probability.doubleValue()));
            }
            canonical.append(';');
            for (CompleteCategoryAdapters.SemanticAlternative alternative
                    : slot.alternatives) {
                appendField(canonical, alternative.canonicalIdentity);
            }
        }
    }

    private static List<CompleteCategoryAdapters.SemanticSlot> itemSlots(
            List<PositionedStack> positioned,
            int amountOverride,
            BuildResult result,
            String label) throws ExportFailure {
        List<CompleteCategoryAdapters.SemanticSlot> slots =
                new ArrayList<CompleteCategoryAdapters.SemanticSlot>(positioned.size());
        for (int index = 0; index < positioned.size(); index++) {
            slots.add(itemSlot(positioned.get(index), amountOverride, result,
                    label + " #" + index));
        }
        return slots;
    }

    private static CompleteCategoryAdapters.SemanticSlot itemSlot(
            PositionedStack positioned,
            int amountOverride,
            BuildResult result,
            String label) throws ExportFailure {
        if (positioned == null || positioned.items == null
                || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " has no PositionedStack alternatives");
        }
        List<CompleteCategoryAdapters.SemanticAlternative> alternatives =
                new ArrayList<CompleteCategoryAdapters.SemanticAlternative>(
                        positioned.items.length);
        Set<String> seen = new HashSet<String>();
        for (int index = 0; index < positioned.items.length; index++) {
            ItemStack original = positioned.items[index];
            if (original == null || original.getItem() == null) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        label + " has null item alternative #" + index);
            }
            int amount = amountOverride >= 0 ? amountOverride : original.stackSize;
            if (amount < 0 || (amountOverride < 0 && amount == 0)) {
                throw new ExportFailure("QUANTITY_INVALID",
                        label + " has invalid exact amount " + amount);
            }
            ItemStack copy = original.copy();
            copy.stackSize = amount;
            StackIdentity identity = StackIdentity.of(copy);
            if (identity.amount != amount) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        label + " identity quantity drifted; expected " + amount
                                + ", got " + identity.amount);
            }
            String canonical = CompleteCategoryAdapters.canonicalStackIdentity(
                    identity, amount);
            if (seen.add(canonical)) {
                alternatives.add(new CompleteCategoryAdapters.SemanticAlternative(
                        copy, amount, canonical));
            }
        }
        if (alternatives.isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " lost every alternative after exact deduplication");
        }
        sortAlternatives(alternatives);
        result.itemAlternatives += alternatives.size();
        return new CompleteCategoryAdapters.SemanticSlot(alternatives);
    }

    private static CompleteCategoryAdapters.SemanticSlot singleItemSlot(
            ItemStack stack, int amountOverride, BuildResult result, String label)
            throws ExportFailure {
        if (stack == null || stack.getItem() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", label + " is null/empty");
        }
        int amount = amountOverride >= 0 ? amountOverride : stack.stackSize;
        ItemStack copy = stack.copy();
        copy.stackSize = amount;
        StackIdentity identity = StackIdentity.of(copy);
        CompleteCategoryAdapters.SemanticAlternative alternative =
                new CompleteCategoryAdapters.SemanticAlternative(
                        copy, amount,
                        CompleteCategoryAdapters.canonicalStackIdentity(identity, amount));
        result.itemAlternatives++;
        return new CompleteCategoryAdapters.SemanticSlot(
                Collections.singletonList(alternative));
    }

    private static CompleteCategoryAdapters.SemanticSlot fluidSlot(
            Object positionedTank, int amountOverride, BuildResult result, String label)
            throws Exception {
        List<FluidStack> fluids = tankFluids(positionedTank, label);
        if (fluids.size() != 1) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " must have exactly one fluid permutation after correlation expansion; got "
                    + fluids.size());
        }
        return singleFluidSlot(fluids.get(0), amountOverride, result, label);
    }

    private static CompleteCategoryAdapters.SemanticSlot singleFluidSlot(
            FluidStack original, int amountOverride, BuildResult result, String label)
            throws ExportFailure {
        if (original == null || original.getFluid() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " contains a null/unregistered fluid");
        }
        rejectTaggedFluid(original.tag, label + " ("
                + FluidRegistry.getFluidName(original) + ")");
        int amount = amountOverride >= 0 ? amountOverride : original.amount;
        if (amount < 0 || (amountOverride < 0 && amount == 0)) {
            throw new ExportFailure("QUANTITY_INVALID",
                    label + " has invalid exact fluid amount " + amount);
        }
        FluidStack fluid = original.copy();
        fluid.amount = amount;
        ItemStack proxy = GTUtility.getFluidDisplayStack(fluid, true, true);
        if (proxy == null || proxy.getItem() == null) {
            throw new ExportFailure("ITEM_IDENTITY", label
                    + " could not create the pinned GregTech fluid-display proxy");
        }
        StackIdentity identity;
        try {
            identity = StackIdentity.of(proxy);
        } catch (RuntimeException error) {
            throw new ExportFailure("ITEM_IDENTITY", label
                    + " GregTech fluid-display proxy could not be decoded", error);
        }
        String expectedKey = "fluid|fluid:" + FluidRegistry.getFluidName(fluid);
        if (!identity.isFluid() || !expectedKey.equals(identity.key)
                || identity.amount != amount || identity.canonicalNbt != null) {
            throw new ExportFailure("ITEM_IDENTITY", label
                    + " GregTech proxy changed fluid identity/amount; expectedKey="
                    + expectedKey + ", expectedAmount=" + amount + ", observedKey="
                    + identity.key + ", observedAmount=" + identity.amount);
        }
        String canonical = CompleteCategoryAdapters.canonicalStackIdentity(
                identity, amount);
        CompleteCategoryAdapters.SemanticAlternative alternative =
                new CompleteCategoryAdapters.SemanticAlternative(
                        proxy, amount, canonical);
        result.fluidAlternatives++;
        if (amount == 0) {
            result.zeroAmountFluidAlternatives++;
        }
        return new CompleteCategoryAdapters.SemanticSlot(
                Collections.singletonList(alternative));
    }

    private static void sortAlternatives(
            List<CompleteCategoryAdapters.SemanticAlternative> alternatives) {
        Collections.sort(alternatives,
                new Comparator<CompleteCategoryAdapters.SemanticAlternative>() {
                    @Override
                    public int compare(
                            CompleteCategoryAdapters.SemanticAlternative left,
                            CompleteCategoryAdapters.SemanticAlternative right) {
                        return left.canonicalIdentity.compareTo(right.canonicalIdentity);
                    }
                });
    }

    private static String positionedCanonical(PositionedStack stack, int amountOverride)
            throws ExportFailure {
        if (stack == null || stack.items == null || stack.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "cannot canonicalize an empty Forestry PositionedStack");
        }
        List<String> canonical = new ArrayList<String>(stack.items.length);
        for (ItemStack item : stack.items) {
            if (item == null || item.getItem() == null) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "cannot canonicalize a null Forestry PositionedStack alternative");
            }
            ItemStack copy = item.copy();
            int amount = amountOverride >= 0 ? amountOverride : item.stackSize;
            copy.stackSize = amount;
            canonical.add(CompleteCategoryAdapters.canonicalStackIdentity(
                    StackIdentity.of(copy), amount));
        }
        Collections.sort(canonical);
        return canonical.toString();
    }

    private static Object requiredTank(Object cached, String fieldName,
                                       int x, int y, int width, int height,
                                       int capacity, boolean showAmount)
            throws Exception {
        Field field = field(cached.getClass(), fieldName);
        Object tank = field.get(cached);
        if (tank == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    cached.getClass().getName() + '.' + fieldName + " is null");
        }
        return requireTank(tank, x, y, width, height, capacity, showAmount,
                cached.getClass().getName() + '.' + fieldName);
    }

    private static Object requireTank(Object positionedTank,
                                      int x, int y, int width, int height,
                                      int capacity, boolean showAmount,
                                      String label) throws Exception {
        if (positionedTank == null
                || !POSITIONED_FLUID_TANK.equals(
                positionedTank.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label
                    + " is not the exact Forestry PositionedFluidTank");
        }
        Class<?> type = positionedTank.getClass();
        Rectangle position = (Rectangle) field(type, "position").get(positionedTank);
        if (position == null || position.x != x || position.y != y
                || position.width != width || position.height != height) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label
                    + " position drifted; expected="
                    + new Rectangle(x, y, width, height) + ", got=" + position);
        }
        if (field(type, "showAmount").getBoolean(positionedTank) != showAmount) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label
                    + " showAmount drifted; expected " + showAmount);
        }
        if (field(type, "perTick").getBoolean(positionedTank)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label
                    + " unexpectedly became a per-tick presentation tank");
        }
        FluidTank[] tanks = (FluidTank[]) field(type, "tanks").get(positionedTank);
        if (tanks == null || tanks.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " has no fluid permutations");
        }
        Object current = field(type, "tank").get(positionedTank);
        if (current != tanks[0]) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " current tank changed before the immutable semantic snapshot");
        }
        for (int index = 0; index < tanks.length; index++) {
            FluidTank tank = tanks[index];
            if (tank == null || tank.getCapacity() != capacity
                    || tank.getFluid() == null || tank.getFluid().getFluid() == null) {
                throw new ExportFailure("RECIPE_SEMANTICS", label
                        + " has invalid tank permutation #" + index
                        + "; expectedCapacity=" + capacity);
            }
        }
        return positionedTank;
    }

    private static List<FluidStack> tankFluids(Object positionedTank, String label)
            throws Exception {
        FluidTank[] tanks = (FluidTank[]) field(
                positionedTank.getClass(), "tanks").get(positionedTank);
        if (tanks == null || tanks.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " contains no fluid tanks");
        }
        List<FluidStack> fluids = new ArrayList<FluidStack>(tanks.length);
        for (int index = 0; index < tanks.length; index++) {
            FluidStack fluid = tanks[index] == null ? null : tanks[index].getFluid();
            if (fluid == null || fluid.getFluid() == null) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        label + " has empty fluid permutation #" + index);
            }
            fluids.add(fluid.copy());
        }
        return fluids;
    }

    private static FluidStack onlyFluid(Object positionedTank, String label)
            throws Exception {
        List<FluidStack> fluids = tankFluids(positionedTank, label);
        if (fluids.size() != 1) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " must contain one exact fluid; got " + fluids.size());
        }
        return fluids.get(0);
    }

    private static Object uniqueTankAt(List<?> tanks, int x, int y, String label)
            throws Exception {
        Object match = null;
        for (Object candidate : tanks) {
            if (candidate == null
                    || !POSITIONED_FLUID_TANK.equals(candidate.getClass().getName())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        label + " tank list contains a non-PositionedFluidTank");
            }
            Rectangle position = (Rectangle) field(
                    candidate.getClass(), "position").get(candidate);
            if (position != null && position.x == x && position.y == y) {
                if (match != null) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            label + " has duplicate tanks at " + x + ',' + y);
                }
                match = candidate;
            }
        }
        if (match == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    label + " has no tank at " + x + ',' + y);
        }
        return match;
    }

    private static PositionedStack requiredPositioned(
            Object cached, String fieldName, int x, int y) throws Exception {
        Object value = field(cached.getClass(), fieldName).get(cached);
        if (!(value instanceof PositionedStack)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    cached.getClass().getName() + '.' + fieldName
                            + " is not a PositionedStack");
        }
        PositionedStack positioned = (PositionedStack) value;
        if (positioned.relx != x || positioned.rely != y) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    cached.getClass().getName() + '.' + fieldName
                            + " position drifted; expected=" + x + ',' + y
                            + ", got=" + positioned.relx + ',' + positioned.rely);
        }
        return positioned;
    }

    private static List<PositionedStack> requiredPositionedList(
            Object cached, String fieldName) throws Exception {
        List<?> raw = requiredListField(cached, fieldName);
        List<PositionedStack> positioned =
                new ArrayList<PositionedStack>(raw.size());
        for (Object entry : raw) {
            if (!(entry instanceof PositionedStack)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        cached.getClass().getName() + '.' + fieldName
                                + " contains a non-PositionedStack");
            }
            positioned.add((PositionedStack) entry);
        }
        return positioned;
    }

    private static List<?> requiredListField(Object owner, String fieldName)
            throws Exception {
        Object value = field(owner.getClass(), fieldName).get(owner);
        if (!(value instanceof List)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    owner.getClass().getName() + '.' + fieldName + " is not a List");
        }
        return (List<?>) value;
    }

    private static List<?> exactCachedPages(TemplateRecipeHandler target,
                                             HandlerSpec spec)
            throws ExportFailure {
        List<?> pages = new ArrayList<Object>(target.arecipes);
        Class<?> cached;
        try {
            cached = spec.cachedClass(target.getClass().getClassLoader());
        } catch (ClassNotFoundException error) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    spec.cachedClassName + " could not be loaded", error);
        }
        for (int index = 0; index < pages.size(); index++) {
            Object page = pages.get(index);
            if (page == null || page.getClass() != cached) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", spec.handlerClass
                        + " cached page #" + index + " has class "
                        + (page == null ? "<null>" : page.getClass().getName())
                        + ", expected " + cached.getName());
            }
        }
        return pages;
    }

    private static void requireAlternativeSet(PositionedStack positioned,
                                              Set<String> expected,
                                              String label)
            throws ExportFailure {
        Set<String> observed = itemAlternativeCanonicals(positioned, -1);
        if (!expected.equals(observed)) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " drifted; expected=" + expected + ", observed=" + observed);
        }
    }

    private static Set<String> itemAlternativeCanonicals(
            PositionedStack positioned, int amountOverride) throws ExportFailure {
        if (positioned == null || positioned.items == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "cannot inspect null PositionedStack alternatives");
        }
        Set<String> canonical = new HashSet<String>();
        for (ItemStack item : positioned.items) {
            if (item == null || item.getItem() == null) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "PositionedStack contains a null item alternative");
            }
            ItemStack copy = item.copy();
            int amount = amountOverride >= 0 ? amountOverride : item.stackSize;
            copy.stackSize = amount;
            canonical.add(CompleteCategoryAdapters.canonicalStackIdentity(
                    StackIdentity.of(copy), amount));
        }
        return canonical;
    }

    private static List<FabricatorSmeltingRecord> captureFabricatorSmeltingRecords(
            ClassLoader loader) throws Exception {
        Class<?> manager = Class.forName(
                FABRICATOR_SMELTING_RECIPE_MANAGER, false, loader);
        Class<?> recipeType = Class.forName(
                FABRICATOR_SMELTING_RECIPE, false, loader);
        Field recipesField = exactDeclaredField(manager, "recipes", Set.class);
        if (!Modifier.isStatic(recipesField.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    FABRICATOR_SMELTING_RECIPE_MANAGER + ".recipes is no longer static");
        }
        Object raw = recipesField.get(null);
        if (!(raw instanceof Set)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    FABRICATOR_SMELTING_RECIPE_MANAGER + ".recipes is not a Set");
        }
        Method getResource = exactPublicMethod(
                recipeType, "getResource", ItemStack.class);
        Method getProduct = exactPublicMethod(
                recipeType, "getProduct", FluidStack.class);
        Method getMeltingPoint = exactPublicMethod(
                recipeType, "getMeltingPoint", int.class);
        Map<String, FabricatorSmeltingRecord> deduplicated =
                new LinkedHashMap<String, FabricatorSmeltingRecord>();
        for (Object recipe : (Set<?>) raw) {
            if (recipe == null || !recipeType.isInstance(recipe)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "Fabricator smelting manager contains an unexpected recipe class");
            }
            ItemStack resource = (ItemStack) getResource.invoke(recipe);
            FluidStack product = (FluidStack) getProduct.invoke(recipe);
            int meltingPoint = ((Integer) getMeltingPoint.invoke(recipe)).intValue();
            FabricatorSmeltingRecord record = new FabricatorSmeltingRecord(
                    resource, product, meltingPoint);
            FabricatorSmeltingRecord previous = deduplicated.put(
                    record.canonical, record);
            if (previous != null) {
                // Exact duplicate source-manager rows intentionally collapse to one graph row.
                GtnhNeiExportMod.LOGGER.info(
                        "[gtnh-nei-export] Deduplicated exact Forestry Fabricator "
                                + "smelting conversion {}", record.canonical);
            }
        }
        List<FabricatorSmeltingRecord> records =
                new ArrayList<FabricatorSmeltingRecord>(deduplicated.values());
        Collections.sort(records, new Comparator<FabricatorSmeltingRecord>() {
            @Override
            public int compare(FabricatorSmeltingRecord left,
                               FabricatorSmeltingRecord right) {
                return left.canonical.compareTo(right.canonical);
            }
        });
        if (records.isEmpty()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Fabricator smelting conversion corpus is empty");
        }
        return records;
    }

    private static Object relevantFabricatorPreview(
            List<Object> mainCachedPages, Map<Object, String> mainSemanticIds,
            HandlerSpec spec,
            FabricatorSmeltingRecord record,
            BuildResult result) throws Exception {
        String resourceCanonical = itemCanonical(record.resource, -1);
        List<String> matchingSemanticIds = new ArrayList<String>();
        Map<String, Object> previewBySemanticId =
                new LinkedHashMap<String, Object>();
        for (Object cached : mainCachedPages) {
            Object tank = field(spec.cachedClass(
                    cached.getClass().getClassLoader()), "tank").get(cached);
            if (tank == null || !sameFluidType(onlyFluid(tank,
                    "Fabricator supplemental preview tank"), record.product)) {
                continue;
            }
            List<PositionedStack> previews =
                    requiredPositionedList(cached, "smeltingInput");
            if (previews.size() != 1) {
                continue;
            }
            Set<String> alternatives = itemAlternativeCanonicals(previews.get(0), -1);
            if (alternatives.contains(resourceCanonical)) {
                String semanticId = mainSemanticIds.get(cached);
                if (semanticId == null || semanticId.trim().isEmpty()) {
                    throw new ExportFailure("INTERNAL_ERROR",
                            "Fabricator main page lost its semantic identity during "
                                    + "supplemental-preview selection");
                }
                matchingSemanticIds.add(semanticId);
                if (!previewBySemanticId.containsKey(semanticId)) {
                    previewBySemanticId.put(semanticId, cached);
                }
            }
        }
        if (matchingSemanticIds.isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Fabricator exact smelting conversion has no relevant cached NEI page: "
                            + record.canonical);
        }
        String matchSemanticId = uniqueMinimumSemanticId(
                matchingSemanticIds,
                "Fabricator supplemental preview for " + record.canonical);
        Object match = previewBySemanticId.get(matchSemanticId);
        if (match == null) {
            throw new ExportFailure("INTERNAL_ERROR",
                    "Fabricator deterministic supplemental preview lookup lost its row");
        }
        result.supplementalPreviewCandidates += matchingSemanticIds.size();
        result.extraCanonical.append("FP|").append(record.canonical)
                .append('|').append(matchSemanticId).append('\n');
        return match;
    }

    static String uniqueMinimumSemanticId(
            List<String> semanticIds, String label) throws ExportFailure {
        if (semanticIds == null || semanticIds.isEmpty()
                || label == null || label.trim().isEmpty()) {
            throw new ExportFailure("INTERNAL_ERROR",
                    "Forestry representative selection received incomplete state");
        }
        List<String> sorted = new ArrayList<String>(semanticIds.size());
        for (int index = 0; index < semanticIds.size(); index++) {
            String semanticId = semanticIds.get(index);
            if (semanticId == null || semanticId.trim().isEmpty()) {
                throw new ExportFailure("INTERNAL_ERROR", label
                        + " contains an invalid semantic ID at index " + index);
            }
            sorted.add(semanticId);
        }
        Collections.sort(sorted);
        if (sorted.size() > 1 && sorted.get(0).equals(sorted.get(1))) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " has multiple render candidates sharing minimum semantic ID "
                    + sorted.get(0));
        }
        return sorted.get(0);
    }

    private static FuelCorpus captureFermenterFuels(ClassLoader loader)
            throws Exception {
        Class<?> manager = Class.forName(FUEL_MANAGER, false, loader);
        Class<?> fuelType = Class.forName(FERMENTER_FUEL, false, loader);
        Field mapField = exactPublicField(manager, "fermenterFuel", java.util.HashMap.class);
        Object raw = mapField.get(null);
        if (!(raw instanceof Map)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "FuelManager.fermenterFuel is not initialized");
        }
        Field itemField = exactPublicField(fuelType, "item", ItemStack.class);
        Field perCycleField = exactPublicField(fuelType, "fermentPerCycle", int.class);
        Field durationField = exactPublicField(fuelType, "burnDuration", int.class);
        Map<String, String> records = new LinkedHashMap<String, String>();
        Set<String> items = new HashSet<String>();
        for (Object fuel : ((Map<?, ?>) raw).values()) {
            if (fuel == null || fuel.getClass() != fuelType) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "FuelManager.fermenterFuel contains an unexpected value class");
            }
            ItemStack item = (ItemStack) itemField.get(fuel);
            int perCycle = perCycleField.getInt(fuel);
            int duration = durationField.getInt(fuel);
            if (perCycle <= 0 || duration <= 0) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "Fermenter fuel has nonpositive amortization fields");
            }
            String itemCanonical = itemCanonical(item, -1);
            String canonical = "FF|" + itemCanonical + '|'
                    + perCycle + '|' + duration;
            records.put(canonical, canonical);
            items.add(itemCanonical);
        }
        List<String> sorted = new ArrayList<String>(records.keySet());
        Collections.sort(sorted);
        if (sorted.isEmpty()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Fermenter fuel corpus is empty");
        }
        StringBuilder canonical = new StringBuilder();
        for (String record : sorted) canonical.append(record).append('\n');
        return new FuelCorpus(items, sorted.size(), canonical.toString());
    }

    private static MoistenerFuelCorpus captureMoistenerFuels(ClassLoader loader)
            throws Exception {
        Class<?> manager = Class.forName(FUEL_MANAGER, false, loader);
        Class<?> fuelType = Class.forName(MOISTENER_FUEL, false, loader);
        Field mapField = exactPublicField(
                manager, "moistenerResource", java.util.HashMap.class);
        Object raw = mapField.get(null);
        if (!(raw instanceof Map)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "FuelManager.moistenerResource is not initialized");
        }
        Field itemField = exactPublicField(fuelType, "item", ItemStack.class);
        Field productField = exactPublicField(fuelType, "product", ItemStack.class);
        Field valueField = exactPublicField(fuelType, "moistenerValue", int.class);
        Field stageField = exactPublicField(fuelType, "stage", int.class);
        Map<String, MoistenerFuelRecord> records =
                new LinkedHashMap<String, MoistenerFuelRecord>();
        for (Object fuel : ((Map<?, ?>) raw).values()) {
            if (fuel == null || fuel.getClass() != fuelType) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "FuelManager.moistenerResource contains an unexpected value class");
            }
            MoistenerFuelRecord record = new MoistenerFuelRecord(
                    (ItemStack) itemField.get(fuel),
                    (ItemStack) productField.get(fuel),
                    valueField.getInt(fuel), stageField.getInt(fuel));
            MoistenerFuelRecord previous = records.put(record.pairKey, record);
            if (previous != null && !previous.canonical.equals(record.canonical)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "Moistener maps one fuel/product pair to divergent runtime fields");
            }
        }
        List<MoistenerFuelRecord> sorted =
                new ArrayList<MoistenerFuelRecord>(records.values());
        Collections.sort(sorted, new Comparator<MoistenerFuelRecord>() {
            @Override
            public int compare(MoistenerFuelRecord left, MoistenerFuelRecord right) {
                return left.canonical.compareTo(right.canonical);
            }
        });
        if (sorted.isEmpty()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Moistener fuel corpus is empty");
        }
        StringBuilder canonical = new StringBuilder();
        for (MoistenerFuelRecord record : sorted) {
            canonical.append("MF|").append(record.canonical).append('\n');
        }
        return new MoistenerFuelCorpus(sorted, canonical.toString());
    }

    private static int captureMoistenerPrimaryRecipeCount(
            ClassLoader loader, StringBuilder canonicalOut) throws Exception {
        Class<?> managers = Class.forName(RECIPE_MANAGERS, false, loader);
        Field managerField = exactPublicFieldByName(
                managers, "moistenerManager",
                "forestry.api.recipes.IMoistenerManager", loader);
        Object manager = managerField.get(null);
        if (manager == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "RecipeManagers.moistenerManager is null");
        }
        Class<?> managerType = Class.forName(
                "forestry.api.recipes.IMoistenerManager", false, loader);
        Method recipes = exactPublicMethod(
                managerType, "recipes", Collection.class, true);
        Object raw = recipes.invoke(manager);
        if (!(raw instanceof Collection)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Moistener manager recipes() did not return a Collection");
        }
        Class<?> recipeType = Class.forName(
                "forestry.api.recipes.IMoistenerRecipe", false, loader);
        Method resource = exactPublicMethod(recipeType, "getResource", ItemStack.class);
        Method product = exactPublicMethod(recipeType, "getProduct", ItemStack.class);
        Method time = exactPublicMethod(recipeType, "getTimePerItem", int.class);
        List<String> rows = new ArrayList<String>();
        for (Object recipe : (Collection<?>) raw) {
            if (recipe == null || !recipeType.isInstance(recipe)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "Moistener manager contains an unexpected recipe class");
            }
            rows.add("MR|" + itemCanonical(
                    (ItemStack) resource.invoke(recipe), -1) + '|'
                    + itemCanonical((ItemStack) product.invoke(recipe), -1) + '|'
                    + ((Integer) time.invoke(recipe)).intValue());
        }
        Collections.sort(rows);
        for (String row : rows) canonicalOut.append(row).append('\n');
        return rows.size();
    }

    private static String itemCanonical(ItemStack item, int amountOverride)
            throws ExportFailure {
        if (item == null || item.getItem() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Forestry source manager returned a null/empty ItemStack");
        }
        ItemStack copy = item.copy();
        int amount = amountOverride >= 0 ? amountOverride : item.stackSize;
        copy.stackSize = amount;
        return CompleteCategoryAdapters.canonicalStackIdentity(
                StackIdentity.of(copy), amount);
    }

    private static String rawFluidCanonical(FluidStack fluid)
            throws ExportFailure {
        if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Forestry source manager returned an invalid FluidStack");
        }
        rejectTaggedFluid(fluid.tag, "Forestry source fluid ("
                + FluidRegistry.getFluidName(fluid) + ")");
        return "fluid|" + FluidRegistry.getFluidName(fluid)
                + "|amount=" + fluid.amount;
    }

    private static void rejectTaggedFluid(NBTTagCompound tag, String label)
            throws ExportFailure {
        if (tag != null) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " contains tagged fluid data; GregTech 5.09.51.482 "
                    + "fluid-display proxies do not preserve FluidStack.tag, so this exact "
                    + "adapter intentionally fails and tagged identities are never silently "
                    + "collapsed");
        }
    }

    private static boolean sameFluidType(FluidStack left, FluidStack right)
            throws ExportFailure {
        rawFluidCanonical(left);
        rawFluidCanonical(right);
        return left.getFluid() == right.getFluid();
    }

    private static SqueezerExpansion expandSqueezerPages(
            TemplateRecipeHandler target, HandlerSpec spec, BuildResult result)
            throws Exception {
        ClassLoader loader = target.getClass().getClassLoader();
        Class<?> recipeType = Class.forName(SQUEEZER_RECIPE, false, loader);
        Class<?> containerType = Class.forName(
                SQUEEZER_CONTAINER_RECIPE, false, loader);
        Class<?> cachedType = spec.cachedClass(loader);
        List<?> original = exactCachedPages(target, spec);
        List<Object> regularSources = captureRegularSqueezerSources(loader, recipeType);
        List<Object> containerSources = captureContainerSqueezerSources(
                loader, containerType);
        if (original.size() != regularSources.size() + containerSources.size()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Squeezer source/cached page partition drifted; cached="
                            + original.size() + ", regularSources="
                            + regularSources.size() + ", containerSources="
                            + containerSources.size());
        }

        Constructor<?> regularConstructor = cachedType.getConstructor(
                target.getClass(), recipeType, boolean.class);
        Constructor<?> containerConstructor = cachedType.getConstructor(
                target.getClass(), containerType, boolean.class);
        Method getChance = exactPublicMethod(
                recipeType, "getRemnantsChance", float.class);
        Method getContainerChance = exactPublicMethod(
                containerType, "getRemnantsChance", float.class);
        Method getSqueezerRecipe = exactPublicMethod(
                containerType, "getSqueezerRecipe", recipeType, ItemStack.class);
        Class<?> managerClass = Class.forName(
                SQUEEZER_RECIPE_MANAGER, false, loader);
        Method findContainer = exactPublicStaticMethod(
                managerClass, "findMatchingContainerRecipe", containerType,
                ItemStack.class);
        Class<?> helper = Class.forName(
                "forestry.core.fluids.FluidHelper", false, loader);
        Method decodeContainer = exactPublicStaticMethod(
                helper, "getFluidStackInContainer", FluidStack.class,
                ItemStack.class);

        // The two upstream backing stores are HashSet/ItemStackMap based. Their iteration
        // order is not an identity contract. Cross-walk the independently loaded cached
        // rows to reconstructed source rows by canonical shape, then sort the fixed pages
        // by their full semantic/probability key before assigning source indexes.
        Map<String, List<Object>> regularCachedByShape =
                new LinkedHashMap<String, List<Object>>();
        Map<String, List<Object>> containerCachedByShape =
                new LinkedHashMap<String, List<Object>>();
        for (Object cached : original) {
            boolean container = field(cachedType, "containerRecipe").getBoolean(cached);
            String shape = container
                    ? squeezerContainerCorrelationShape(cached, decodeContainer)
                    : squeezerCachedCrossWalkShape(cached);
            addShapeBucket(container ? containerCachedByShape : regularCachedByShape,
                    shape, cached);
        }

        List<SqueezerPage> expanded = new ArrayList<SqueezerPage>();
        for (Object source : regularSources) {
            Object reconstructed = regularConstructor.newInstance(
                    target, source, Boolean.TRUE);
            String crossWalkShape = squeezerCachedCrossWalkShape(reconstructed);
            Object cached = takeShapeMatch(regularCachedByShape, crossWalkShape,
                    "regular Squeezer source");
            requireSameSqueezerCachedShape(cached, reconstructed,
                    "canonical regular Squeezer source cross-walk");
            float chance = ((Float) getChance.invoke(source)).floatValue();
            validateSqueezerSourceChance(chance,
                    "canonical regular Squeezer source");
            String sortKey = "regular|" + squeezerCachedShape(reconstructed)
                    + "|remnantsChanceBits="
                    + Float.floatToRawIntBits(chance);
            expanded.add(new SqueezerPage(cached, chance, "regular", sortKey));
        }

        int expandedContainers = 0;
        for (Object source : containerSources) {
            Object reconstructed = containerConstructor.newInstance(
                    target, source, Boolean.TRUE);
            String sourceShape = squeezerContainerCorrelationShape(
                    reconstructed, decodeContainer);
            Object originalCached = takeShapeMatch(
                    containerCachedByShape, sourceShape,
                    "container Squeezer source");
            String loadedShape = squeezerContainerCorrelationShape(
                    originalCached, decodeContainer);
            if (!sourceShape.equals(loadedShape)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "canonical container Squeezer cross-walk changed shape");
            }

            List<PositionedStack> inputs =
                    requiredPositionedList(originalCached, "inputs");
            if (inputs.size() != 1 || inputs.get(0).items == null
                    || inputs.get(0).items.length == 0) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "Squeezer container source must expose one nonempty "
                                + "correlated input slot");
            }
            Object originalTank = requiredTank(originalCached, "tank",
                    117, 7, 16, 58, 10000, true);
            List<FluidStack> correlatedFluids = tankFluids(
                    originalTank, "Squeezer container correlated outputs");
            ItemStack[] filledContainers = inputs.get(0).items;
            if (filledContainers.length != correlatedFluids.size()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "Squeezer container correlation length drifted; inputs="
                                + filledContainers.length + ", fluids="
                                + correlatedFluids.size());
            }
            float containerChance = ((Float) getContainerChance.invoke(source)).floatValue();
            for (int permutation = 0; permutation < filledContainers.length;
                 permutation++) {
                ItemStack filled = filledContainers[permutation];
                if (filled == null || filled.getItem() == null) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "Squeezer container correlation has null input #" + permutation);
                }
                FluidStack independentlyDecoded = (FluidStack) decodeContainer.invoke(
                        null, filled.copy());
                String decodedCanonical = rawFluidCanonical(independentlyDecoded);
                String tankCanonical = rawFluidCanonical(
                        correlatedFluids.get(permutation));
                if (!decodedCanonical.equals(tankCanonical)) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "Squeezer container input/tank correlation drifted at permutation="
                                    + permutation
                                    + "; decoded=" + decodedCanonical
                                    + ", tank=" + tankCanonical);
                }
                Object managerMatch = findContainer.invoke(null, filled.copy());
                if (managerMatch != source) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "Squeezer container manager identity changed for correlated "
                                    + "permutation=" + permutation);
                }
                Object exactRecipe = getSqueezerRecipe.invoke(source, filled.copy());
                if (exactRecipe == null || !recipeType.isInstance(exactRecipe)) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "Squeezer container source returned no exact recipe at "
                                    + "permutation=" + permutation);
                }
                Object fixedCached = regularConstructor.newInstance(
                        target, exactRecipe, Boolean.TRUE);
                requireFixedSqueezerCorrelation(fixedCached, filled,
                        correlatedFluids.get(permutation),
                        "Squeezer container permutation=" + permutation);
                float exactChance = ((Float) getChance.invoke(exactRecipe)).floatValue();
                if (Float.floatToRawIntBits(exactChance)
                        != Float.floatToRawIntBits(containerChance)) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "Squeezer container-derived remnants chance drifted at "
                                    + "permutation=" + permutation);
                }
                validateSqueezerSourceChance(exactChance,
                        "Squeezer correlated container page");
                String sortKey = "container|"
                        + squeezerCachedShape(fixedCached)
                        + "|remnantsChanceBits="
                        + Float.floatToRawIntBits(exactChance);
                expanded.add(new SqueezerPage(
                        fixedCached, exactChance, "container", sortKey));
                expandedContainers++;
            }
        }

        requireEmptyShapeBuckets(regularCachedByShape,
                "unmatched regular Squeezer cached pages");
        requireEmptyShapeBuckets(containerCachedByShape,
                "unmatched container Squeezer cached pages");
        Collections.sort(expanded, new Comparator<SqueezerPage>() {
            @Override
            public int compare(SqueezerPage left, SqueezerPage right) {
                return left.sortKey.compareTo(right.sortKey);
            }
        });
        for (SqueezerPage page : expanded) {
            result.extraCanonical.append("SP|")
                    .append(page.sortKey).append('\n');
        }

        replaceCachedPages(target, expanded);
        if (target.numRecipes() != expanded.size()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Squeezer expanded preview list did not retain every fixed page");
        }
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Expanded correlated Squeezer container pages: "
                        + "regularPages={}, containerSourcePages={}, fixedContainerPages={}",
                regularSources.size(), containerSources.size(), expandedContainers);
        return new SqueezerExpansion(expanded, regularSources.size(),
                containerSources.size(), expandedContainers);
    }

    private static List<Object> captureRegularSqueezerSources(
            ClassLoader loader, Class<?> recipeType) throws Exception {
        Class<?> managers = Class.forName(RECIPE_MANAGERS, false, loader);
        Field managerField = exactPublicFieldByName(
                managers, "squeezerManager", SQUEEZER_MANAGER, loader);
        Object manager = managerField.get(null);
        if (manager == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "RecipeManagers.squeezerManager is null");
        }
        Class<?> managerType = Class.forName(SQUEEZER_MANAGER, false, loader);
        Method recipes = exactPublicMethod(
                managerType, "recipes", Collection.class, true);
        Object raw = recipes.invoke(manager);
        if (!(raw instanceof Collection)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "ISqueezerManager.recipes() did not return a Collection");
        }
        List<Object> sources = new ArrayList<Object>();
        for (Object source : (Collection<?>) raw) {
            if (source == null || !recipeType.isInstance(source)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "Squeezer manager contains an unexpected source class");
            }
            sources.add(source);
        }
        return sources;
    }

    private static List<Object> captureContainerSqueezerSources(
            ClassLoader loader, Class<?> containerType) throws Exception {
        Class<?> manager = Class.forName(
                SQUEEZER_RECIPE_MANAGER, false, loader);
        Field containerRecipes = exactPublicFieldByName(
                manager, "containerRecipes",
                "forestry.core.utils.datastructures.ItemStackMap", loader);
        Object map = containerRecipes.get(null);
        if (map == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "SqueezerRecipeManager.containerRecipes is null");
        }
        Method values = exactPublicMethod(
                map.getClass(), "values", Collection.class, true);
        Object raw = values.invoke(map);
        if (!(raw instanceof Collection)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Squeezer container recipe values() did not return a Collection");
        }
        List<Object> sources = new ArrayList<Object>();
        for (Object source : (Collection<?>) raw) {
            if (source == null || !containerType.isInstance(source)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "Squeezer container map contains an unexpected source class");
            }
            sources.add(source);
        }
        return sources;
    }

    private static void requireFixedSqueezerCorrelation(
            Object cached, ItemStack expectedInput, FluidStack expectedFluid,
            String label) throws Exception {
        if (field(cached.getClass(), "containerRecipe").getBoolean(cached)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " remained a cycling container page");
        }
        List<PositionedStack> inputs = requiredPositionedList(cached, "inputs");
        if (inputs.size() != 1 || inputs.get(0).items == null
                || inputs.get(0).items.length != 1
                || !itemCanonical(expectedInput, -1).equals(
                itemCanonical(inputs.get(0).items[0], -1))) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " did not retain one exact filled-container input");
        }
        FluidStack observed = onlyFluid(requiredTank(cached, "tank",
                117, 7, 16, 58, 10000, true), label + " output tank");
        if (!rawFluidCanonical(expectedFluid).equals(rawFluidCanonical(observed))) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " did not retain its index-correlated fluid output");
        }
    }

    private static void requireSameSqueezerCachedShape(
            Object observed, Object reconstructed, String label) throws Exception {
        String observedShape = squeezerCachedCrossWalkShape(observed);
        String reconstructedShape = squeezerCachedCrossWalkShape(reconstructed);
        if (!observedShape.equals(reconstructedShape)) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " no longer cross-walks 1:1 to the cached NEI row; observed="
                    + observedShape + ", reconstructed=" + reconstructedShape);
        }
    }

    private static String squeezerCachedShape(Object cached) throws Exception {
        StringBuilder shape = new StringBuilder();
        shape.append("container=")
                .append(field(cached.getClass(), "containerRecipe").getBoolean(cached))
                .append(";time=")
                .append(field(cached.getClass(), "processingTime").getInt(cached));
        List<PositionedStack> inputs = requiredPositionedList(cached, "inputs");
        shape.append(";inputs=").append(inputs.size());
        for (PositionedStack input : inputs) {
            shape.append('|').append(input.relx).append(',').append(input.rely)
                    .append(':').append(positionedCanonical(input, -1));
        }
        Object tank = requiredTank(cached, "tank", 117, 7, 16, 58,
                10000, true);
        shape.append(";fluids=");
        for (FluidStack fluid : tankFluids(tank, "Squeezer shape tank")) {
            shape.append(rawFluidCanonical(fluid)).append(',');
        }
        Object remnant = field(cached.getClass(), "remnants").get(cached);
        shape.append(";remnant=").append(remnant == null ? "-"
                : positionedCanonical((PositionedStack) remnant, -1));
        return shape.toString();
    }

    private static String squeezerCachedCrossWalkShape(Object cached)
            throws Exception {
        Object remnant = field(cached.getClass(), "remnants").get(cached);
        return squeezerCachedShape(cached) + ";remnantTooltip="
                + remnantTooltipCanonical(remnant);
    }

    private static String squeezerContainerCorrelationShape(
            Object cached, Method decodeContainer) throws Exception {
        if (!field(cached.getClass(), "containerRecipe").getBoolean(cached)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "container correlation shape received a regular Squeezer row");
        }
        List<PositionedStack> inputs = requiredPositionedList(cached, "inputs");
        if (inputs.size() != 1 || inputs.get(0).items == null
                || inputs.get(0).items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "container correlation shape requires one nonempty input slot");
        }
        Object tank = requiredTank(cached, "tank", 117, 7, 16, 58,
                10000, true);
        List<FluidStack> fluids = tankFluids(
                tank, "Squeezer container correlation shape");
        if (inputs.get(0).items.length != fluids.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "container correlation shape has divergent item/fluid lengths");
        }
        List<String> pairs = new ArrayList<String>(fluids.size());
        for (int index = 0; index < fluids.size(); index++) {
            ItemStack filled = inputs.get(0).items[index];
            FluidStack decoded = (FluidStack) decodeContainer.invoke(
                    null, filled.copy());
            String decodedCanonical = rawFluidCanonical(decoded);
            String tankCanonical = rawFluidCanonical(fluids.get(index));
            if (!decodedCanonical.equals(tankCanonical)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "container correlation shape found a false item/fluid pair");
            }
            pairs.add(itemCanonical(filled, -1) + "->" + tankCanonical);
        }
        Collections.sort(pairs);
        Object remnant = field(cached.getClass(), "remnants").get(cached);
        StringBuilder shape = new StringBuilder();
        shape.append("container=true;time=")
                .append(field(cached.getClass(), "processingTime").getInt(cached))
                .append(";correlations=").append(pairs.size());
        for (String pair : pairs) shape.append('|').append(pair);
        shape.append(";remnant=").append(remnant == null ? "-"
                : positionedCanonical((PositionedStack) remnant, -1));
        shape.append(";remnantTooltip=")
                .append(remnantTooltipCanonical(remnant));
        return shape.toString();
    }

    private static String remnantTooltipCanonical(Object remnant) throws Exception {
        if (remnant == null) return "-";
        if (!POSITIONED_STACK_ADV.equals(remnant.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Squeezer remnant lost exact PositionedStackAdv class");
        }
        Object raw = field(remnant.getClass(), "tooltip").get(remnant);
        if (!(raw instanceof List)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Squeezer remnant tooltip is no longer a List");
        }
        StringBuilder canonical = new StringBuilder();
        for (Object entry : (List<?>) raw) {
            if (!(entry instanceof String)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "Squeezer remnant tooltip contains a non-String value");
            }
            appendField(canonical, (String) entry);
        }
        return canonical.toString();
    }

    private static void addShapeBucket(
            Map<String, List<Object>> buckets, String shape, Object cached)
            throws ExportFailure {
        if (shape == null || cached == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Squeezer canonical cross-walk received null state");
        }
        List<Object> matches = buckets.get(shape);
        if (matches == null) {
            matches = new ArrayList<Object>();
            buckets.put(shape, matches);
        }
        matches.add(cached);
    }

    private static Object takeShapeMatch(
            Map<String, List<Object>> buckets, String shape, String label)
            throws ExportFailure {
        List<Object> matches = buckets.get(shape);
        if (matches == null || matches.isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " has no canonical cached-page match; shape=" + shape);
        }
        Object match = matches.remove(matches.size() - 1);
        if (matches.isEmpty()) buckets.remove(shape);
        return match;
    }

    private static void requireEmptyShapeBuckets(
            Map<String, List<Object>> buckets, String label) throws ExportFailure {
        if (!buckets.isEmpty()) {
            int unmatched = 0;
            for (List<Object> values : buckets.values()) unmatched += values.size();
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " remain after canonical source cross-walk; unmatched="
                    + unmatched + ", shapes=" + buckets.keySet());
        }
    }

    private static void validateSqueezerSourceChance(
            float chance, String label) throws ExportFailure {
        if (!Float.isFinite(chance) || chance < 0.0f || chance > 1.0f) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " has invalid remnants chance " + chance);
        }
    }

    private static SqueezerRemnantDisposition classifySqueezerRemnant(
            boolean hasRemnant, float chance, String label) throws ExportFailure {
        validateSqueezerSourceChance(chance, label);
        if (!hasRemnant) return SqueezerRemnantDisposition.ABSENT_IDENTITY;
        if (chance <= 0.0f) {
            return SqueezerRemnantDisposition.ZERO_PROBABILITY_PREVIEW;
        }
        if (chance < 1.0f) return SqueezerRemnantDisposition.STOCHASTIC;
        return SqueezerRemnantDisposition.DETERMINISTIC;
    }

    private static BottlerFlowDisposition classifyBottlerFlow(
            boolean dynamicContainer, int representativeDelta,
            int runtimeCapacity, String label)
            throws ExportFailure {
        if (representativeDelta < 0) {
            throw new ExportFailure("QUANTITY_INVALID",
                    label + " has negative representative fluid delta "
                            + representativeDelta);
        }
        if (runtimeCapacity <= 0) {
            return BottlerFlowDisposition.EXCLUDED_ZERO_CAPACITY_PAGE;
        }
        if (dynamicContainer) {
            return BottlerFlowDisposition.DYNAMIC_UNKNOWN_FLOW;
        }
        return BottlerFlowDisposition.EXACT_FIXED_FLOW;
    }

    private static void requireLegacyFermenterResourceNull(Object legacyResource)
            throws ExportFailure {
        if (legacyResource != null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Forestry 4.10.17 CachedFermenterRecipe.resource is a dead "
                            + "legacy field and must remain null; inputItems[0] is "
                            + "the authoritative correlated resource slot");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void replaceCachedPages(
            TemplateRecipeHandler target, List<SqueezerPage> pages) {
        List raw = target.arecipes;
        raw.clear();
        for (SqueezerPage page : pages) raw.add(page.cached);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void replaceCachedObjects(
            TemplateRecipeHandler target, List<Object> pages) {
        List raw = target.arecipes;
        raw.clear();
        raw.addAll(pages);
    }

    private static void validateSharedTankShape(ClassLoader loader) throws Exception {
        Class<?> tank = Class.forName(POSITIONED_FLUID_TANK, false, loader);
        exactPublicField(tank, "tanks", FluidTank[].class);
        exactPublicField(tank, "tank", FluidTank.class);
        exactPublicField(tank, "position", Rectangle.class);
        exactPublicField(tank, "overlayTexture", String.class);
        exactPublicField(tank, "flowingTexture", boolean.class);
        exactPublicField(tank, "showAmount", boolean.class);
        exactPublicField(tank, "perTick", boolean.class);
        exactPublicMethod(tank, "getPermutationCount", int.class);
        exactPublicMethod(tank, "setPermutationToRender", void.class, int.class);
    }

    private static void validateCachedShape(HandlerSpec spec, ClassLoader loader)
            throws Exception {
        Class<?> cached = spec.cachedClass(loader);
        Class<?> base = Class.forName(CACHED_BASE_RECIPE, false, loader);
        Class<?> tank = Class.forName(POSITIONED_FLUID_TANK, false, loader);
        if (cached.getSuperclass() != base) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", cached.getName()
                    + " exact CachedBaseRecipe superclass drifted");
        }
        if (BOTTLER.equals(spec.handlerClass)) {
            exactPublicField(cached, "fluid", tank);
            exactPublicField(cached, "input", PositionedStack.class);
            exactPublicField(cached, "output", PositionedStack.class);
        } else if (CARPENTER.equals(spec.handlerClass)) {
            exactPublicField(cached, "inputs", List.class);
            exactPublicField(cached, "tank", tank);
            exactPublicField(cached, "output", PositionedStack.class);
        } else if (FABRICATOR.equals(spec.handlerClass)) {
            exactPublicField(cached, "smeltingInput", List.class);
            exactPublicField(cached, "tank", tank);
            exactPublicField(cached, "inputs", List.class);
            exactPublicField(cached, "output", PositionedStack.class);
        } else if (FERMENTER.equals(spec.handlerClass)) {
            exactPublicField(cached, "tanks", List.class);
            exactPublicField(cached, "resource", PositionedStack.class);
            exactPublicField(cached, "inputItems", List.class);
        } else if (MOISTENER.equals(spec.handlerClass)) {
            exactPublicField(cached, "tank", tank);
            exactPublicField(cached, "fuels", List.class);
            exactPublicField(cached, "input", PositionedStack.class);
            exactPublicField(cached, "output", PositionedStack.class);
        } else if (SQUEEZER.equals(spec.handlerClass)) {
            Class<?> advanced = Class.forName(POSITIONED_STACK_ADV, false, loader);
            exactPublicField(cached, "inputs", List.class);
            exactPublicField(cached, "tank", tank);
            exactPublicField(cached, "remnants", advanced);
            exactPublicField(cached, "processingTime", int.class);
            exactPublicField(cached, "containerRecipe", boolean.class);
        } else if (STILL.equals(spec.handlerClass)) {
            exactPublicField(cached, "tanks", List.class);
        }
    }

    private static void validateFabricatorSources(ClassLoader loader) throws Exception {
        Class<?> recipe = Class.forName(FABRICATOR_SMELTING_RECIPE, false, loader);
        exactPublicMethod(recipe, "getResource", ItemStack.class);
        exactPublicMethod(recipe, "getProduct", FluidStack.class);
        exactPublicMethod(recipe, "getMeltingPoint", int.class);
        Class<?> manager = Class.forName(
                FABRICATOR_SMELTING_RECIPE_MANAGER, false, loader);
        exactDeclaredField(manager, "recipes", Set.class);
    }

    private static void validateFuelSources(ClassLoader loader) throws Exception {
        Class<?> manager = Class.forName(FUEL_MANAGER, false, loader);
        exactPublicField(manager, "fermenterFuel", java.util.HashMap.class);
        exactPublicField(manager, "moistenerResource", java.util.HashMap.class);
        Class<?> fermenter = Class.forName(FERMENTER_FUEL, false, loader);
        exactPublicField(fermenter, "item", ItemStack.class);
        exactPublicField(fermenter, "fermentPerCycle", int.class);
        exactPublicField(fermenter, "burnDuration", int.class);
        Class<?> moistener = Class.forName(MOISTENER_FUEL, false, loader);
        exactPublicField(moistener, "item", ItemStack.class);
        exactPublicField(moistener, "product", ItemStack.class);
        exactPublicField(moistener, "moistenerValue", int.class);
        exactPublicField(moistener, "stage", int.class);
    }

    private static void validateSqueezerSources(ClassLoader loader) throws Exception {
        Class<?> recipe = Class.forName(SQUEEZER_RECIPE, false, loader);
        exactPublicMethod(recipe, "getRemnantsChance", float.class);
        Class<?> container = Class.forName(
                SQUEEZER_CONTAINER_RECIPE, false, loader);
        exactPublicMethod(container, "getRemnantsChance", float.class);
        exactPublicMethod(container, "getSqueezerRecipe", recipe, ItemStack.class);
    }

    private static CorpusObservation corpusObservation(
            Map<String, HandlerObservation> observations) throws ExportFailure {
        List<String> missing = new ArrayList<String>();
        for (String handler : HANDLER_ORDER) {
            if (!observations.containsKey(handler)) missing.add(handler);
        }
        if (!missing.isEmpty() || observations.size() != HANDLER_ORDER.size()) {
            throw new ExportFailure("HANDLER_UNLOADED", CONTRACT
                    + " requires all seven exact handler captures; missing=" + missing
                    + ", observed=" + observations.keySet());
        }
        StringBuilder counts = new StringBuilder();
        StringBuilder fingerprintBasis = new StringBuilder();
        int totalPages = 0;
        for (String handler : HANDLER_ORDER) {
            HandlerObservation observation = observations.get(handler);
            if (counts.length() > 0) counts.append(';');
            String shortName = handler.substring(handler.lastIndexOf('.') + 1)
                    .replace("NEIHandler", "");
            counts.append(shortName).append('{').append(observation.countVector)
                    .append('}');
            totalPages += observation.pages;
            fingerprintBasis.append(handler).append('|')
                    .append(observation.countVector).append('|')
                    .append(observation.fingerprint).append('\n');
        }
        counts.append(";totalPages=").append(totalPages);
        return new CorpusObservation(counts.toString(),
                sha256(fingerprintBasis.toString()));
    }

    private static void requirePromotion(CorpusObservation observed,
                                         String expectedCountVector,
                                         String expectedSha256)
            throws ExportFailure {
        if (observed == null || observed.countVector == null
                || observed.countVector.trim().isEmpty()
                || !isLowerHexSha256(observed.fingerprint)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    CONTRACT + " received an invalid corpus observation");
        }
        boolean countsUnpromoted = UNPROMOTED.equals(expectedCountVector);
        boolean hashUnpromoted = UNPROMOTED.equals(expectedSha256);
        if (countsUnpromoted != hashUnpromoted) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", CONTRACT
                    + " is partially promoted; count vector and SHA-256 must be "
                    + "promoted together");
        }
        if (countsUnpromoted) {
            throw new ExportFailure("HANDLER_UNLOADED", CONTRACT
                    + " is intentionally unpromoted; observedCountVector="
                    + observed.countVector + "; observedSha256="
                    + observed.fingerprint + "; export must abort before rendering. "
                    + "Promote only the reviewed exact count vector and SHA-256 together.");
        }
        if (expectedCountVector == null || expectedCountVector.trim().isEmpty()
                || !isLowerHexSha256(expectedSha256)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", CONTRACT
                    + " has malformed promotion constants");
        }
        if (!expectedCountVector.equals(observed.countVector)
                || !expectedSha256.equals(observed.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED", CONTRACT
                    + " drifted; expectedCountVector=" + expectedCountVector
                    + "; observedCountVector=" + observed.countVector
                    + "; expectedSha256=" + expectedSha256
                    + "; observedSha256=" + observed.fingerprint);
        }
    }

    private static boolean hasCompletePromotion(String countVector, String sha256) {
        return countVector != null && !countVector.trim().isEmpty()
                && !UNPROMOTED.equals(countVector) && isLowerHexSha256(sha256);
    }

    private static boolean isLowerHexSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) return false;
        }
        return true;
    }

    private static String sha256(String text) throws ExportFailure {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : bytes) {
                hex.append(Character.forDigit((value >>> 4) & 0xf, 16));
                hex.append(Character.forDigit(value & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new ExportFailure("INTERNAL_ERROR",
                    CONTRACT + " requires JVM SHA-256 support", error);
        }
    }

    private static void addSpec(Map<String, HandlerSpec> specs,
                                String handlerClass, String recipeId,
                                String cachedSimpleName) {
        HandlerSpec previous = specs.put(handlerClass, new HandlerSpec(
                handlerClass, recipeId, handlerClass + '$' + cachedSimpleName));
        if (previous != null) {
            throw new IllegalStateException(
                    "duplicate Forestry handler spec " + handlerClass);
        }
    }

    private static void requireExactClass(Object value, String expected)
            throws ExportFailure {
        if (value == null || !expected.equals(value.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", "expected " + expected
                    + ", got " + (value == null ? "<null>"
                    : value.getClass().getName()));
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Field exactDeclaredField(
            Class<?> type, String name, Class<?> expectedType) throws Exception {
        Field field = type.getDeclaredField(name);
        if (field.getType() != expectedType) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName() + '.' + name
                    + " type drifted; expected " + expectedType.getName()
                    + ", got " + field.getType().getName());
        }
        field.setAccessible(true);
        return field;
    }

    private static Field exactPublicField(
            Class<?> type, String name, Class<?> expectedType) throws Exception {
        Field field = type.getField(name);
        if (field.getDeclaringClass() != type || field.getType() != expectedType
                || !Modifier.isPublic(field.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName() + '.' + name
                    + " exact public field contract drifted");
        }
        return field;
    }

    private static Field exactPublicFieldByName(
            Class<?> type, String name, String expectedTypeName, ClassLoader loader)
            throws Exception {
        return exactPublicField(type, name,
                Class.forName(expectedTypeName, false, loader));
    }

    private static Method exactDeclaredMethod(
            Class<?> type, String name, Class<?> returnType, Class<?>... parameters)
            throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        if (method.getReturnType() != returnType
                || !Modifier.isPublic(method.getModifiers())
                || Modifier.isStatic(method.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName() + '.' + name
                    + " exact public instance method contract drifted");
        }
        method.setAccessible(true);
        return method;
    }

    private static Method exactPublicMethod(
            Class<?> type, String name, Class<?> returnType, Class<?>... parameters)
            throws Exception {
        return exactPublicMethod(type, name, returnType, false, parameters);
    }

    private static Method exactPublicMethod(
            Class<?> type, String name, Class<?> returnType,
            boolean allowInherited, Class<?>... parameters) throws Exception {
        Method method = type.getMethod(name, parameters);
        if (method.getReturnType() != returnType
                || !Modifier.isPublic(method.getModifiers())
                || Modifier.isStatic(method.getModifiers())
                || (!allowInherited && method.getDeclaringClass() != type)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName() + '.' + name
                    + " exact public instance method contract drifted");
        }
        return method;
    }

    private static Method exactPublicStaticMethod(
            Class<?> type, String name, Class<?> returnType, Class<?>... parameters)
            throws Exception {
        Method method = type.getMethod(name, parameters);
        if (method.getDeclaringClass() != type || method.getReturnType() != returnType
                || !Modifier.isPublic(method.getModifiers())
                || !Modifier.isStatic(method.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName() + '.' + name
                    + " exact public static method contract drifted");
        }
        return method;
    }

    private static void appendField(StringBuilder target, String value) {
        if (value == null) {
            target.append("-1:");
        } else {
            target.append(value.length()).append(':').append(value);
        }
        target.append(';');
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void logFailure(String operation, String handler,
                                   Throwable failure) {
        GtnhNeiExportMod.LOGGER.error(
                "[gtnh-nei-export] Forestry fluid adapter {} failed closed for {}",
                operation, handler, failure);
    }

    static final class CorpusObservation {
        final String countVector;
        final String fingerprint;

        CorpusObservation(String countVector, String fingerprint) {
            this.countVector = countVector;
            this.fingerprint = fingerprint;
        }
    }

    private static final class HandlerSpec {
        final String handlerClass;
        final String recipeId;
        final String cachedClassName;

        HandlerSpec(String handlerClass, String recipeId, String cachedClassName) {
            this.handlerClass = handlerClass;
            this.recipeId = recipeId;
            this.cachedClassName = cachedClassName;
        }

        Class<?> cachedClass(ClassLoader loader) throws ClassNotFoundException {
            return Class.forName(cachedClassName, false, loader);
        }
    }

    private static final class HandlerObservation {
        final int pages;
        final String countVector;
        final String fingerprint;

        HandlerObservation(int pages, String countVector, String fingerprint) {
            this.pages = pages;
            this.countVector = countVector;
            this.fingerprint = fingerprint;
        }
    }

    private enum SqueezerRemnantDisposition {
        ABSENT_IDENTITY,
        ZERO_PROBABILITY_PREVIEW,
        STOCHASTIC,
        DETERMINISTIC
    }

    private enum BottlerFlowDisposition {
        EXACT_FIXED_FLOW,
        DYNAMIC_UNKNOWN_FLOW,
        EXCLUDED_ZERO_CAPACITY_PAGE
    }

    private static final class BuildResult {
        final List<CompleteCategoryAdapters.RecipeSemanticOverride> pages =
                new ArrayList<CompleteCategoryAdapters.RecipeSemanticOverride>();
        final List<String> pageCanonicals = new ArrayList<String>();
        final StringBuilder extraCanonical = new StringBuilder();
        int mainPages;
        int supplementalPages;
        int expandedSourcePages;
        int expandedPages;
        int inputSlots;
        int outputSlots;
        long itemAlternatives;
        long fluidAlternatives;
        long zeroAmountFluidAlternatives;
        int dynamicInputs;
        int dynamicOutputs;
        int planPrerequisites;
        int previewOnlyItemSlots;
        int supplementalPreviewCandidates;
        int fuelRecords;
        int probabilisticOutputs;
        int zeroProbabilityPreviewOutputs;
        int positiveChanceWithoutRemnantRows;
        int dynamicBottlerInputs;
        int excludedBottlerZeroCapacityPages;
        int excludedBottlerPositiveDeltaZeroCapacityPages;
        int fixedBottlerDeltaCapacityMismatches;
        int normalizedBottlerContainerQuantityRows;

        void addPage(CompleteCategoryAdapters.RecipeSemanticOverride page) {
            pages.add(page);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        void addSupplementalPage(
                TemplateRecipeHandler target, Object cached,
                CompleteCategoryAdapters.RecipeSemanticOverride page)
                throws ExportFailure {
            if (cached == null) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "supplemental Forestry page has no relevant preview row");
            }
            List raw = target.arecipes;
            raw.add(cached);
            pages.add(page);
        }

        HandlerObservation finish(String handlerClass) throws ExportFailure {
            String countVector = "pages=" + pages.size()
                    + ",mainPages=" + mainPages
                    + ",supplementalPages=" + supplementalPages
                    + ",expandedSourcePages=" + expandedSourcePages
                    + ",expandedPages=" + expandedPages
                    + ",inputSlots=" + inputSlots
                    + ",outputSlots=" + outputSlots
                    + ",itemAlternatives=" + itemAlternatives
                    + ",fluidAlternatives=" + fluidAlternatives
                    + ",zeroAmountFluidAlternatives=" + zeroAmountFluidAlternatives
                    + ",dynamicInputs=" + dynamicInputs
                    + ",dynamicOutputs=" + dynamicOutputs
                    + ",planPrerequisites=" + planPrerequisites
                    + ",previewOnlyItemSlots=" + previewOnlyItemSlots
                    + ",supplementalPreviewCandidates="
                    + supplementalPreviewCandidates
                    + ",fuelRecords=" + fuelRecords
                    + ",probabilisticOutputs=" + probabilisticOutputs
                    + ",zeroProbabilityPreviewOutputs="
                    + zeroProbabilityPreviewOutputs
                    + ",positiveChanceWithoutRemnantRows="
                    + positiveChanceWithoutRemnantRows
                    + ",dynamicBottlerInputs=" + dynamicBottlerInputs
                    + ",excludedBottlerZeroCapacityPages="
                    + excludedBottlerZeroCapacityPages
                    + ",excludedBottlerPositiveDeltaZeroCapacityPages="
                    + excludedBottlerPositiveDeltaZeroCapacityPages
                    + ",fixedBottlerDeltaCapacityMismatches="
                    + fixedBottlerDeltaCapacityMismatches
                    + ",normalizedBottlerContainerQuantityRows="
                    + normalizedBottlerContainerQuantityRows;
            if (pageCanonicals.size() != pages.size()) {
                throw new ExportFailure("INTERNAL_ERROR", handlerClass
                        + " page-canonical/override cardinality diverged; canonicals="
                        + pageCanonicals.size() + ", overrides=" + pages.size());
            }
            return new HandlerObservation(pages.size(), countVector,
                    stablePageMultisetFingerprint(handlerClass, countVector,
                            pageCanonicals, extraCanonical.toString()));
        }
    }

    private static final class BottlerPage {
        final Object cached;
        final FluidStack input;
        final String canonical;

        BottlerPage(Object cached, FluidStack input, String canonical) {
            this.cached = cached;
            this.input = input;
            this.canonical = canonical;
        }
    }

    private static final class FabricatorSmeltingRecord {
        final ItemStack resource;
        final FluidStack product;
        final int meltingPoint;
        final String canonical;

        FabricatorSmeltingRecord(ItemStack resource, FluidStack product,
                                 int meltingPoint) throws ExportFailure {
            if (meltingPoint <= 0) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "Fabricator smelting recipe has nonpositive melting point");
            }
            this.resource = resource == null ? null : resource.copy();
            this.product = product == null ? null : product.copy();
            this.meltingPoint = meltingPoint;
            this.canonical = itemCanonical(this.resource, -1) + '|'
                    + rawFluidCanonical(this.product) + '|' + meltingPoint;
        }
    }

    private static final class FuelCorpus {
        final Set<String> itemCanonicals;
        final int records;
        final String canonical;

        FuelCorpus(Set<String> itemCanonicals, int records, String canonical) {
            this.itemCanonicals = Collections.unmodifiableSet(
                    new HashSet<String>(itemCanonicals));
            this.records = records;
            this.canonical = canonical;
        }
    }

    private static final class MoistenerFuelRecord {
        final ItemStack item;
        final ItemStack product;
        final int moistenerValue;
        final int stage;
        final String pairKey;
        final String canonical;

        MoistenerFuelRecord(ItemStack item, ItemStack product,
                            int moistenerValue, int stage) throws ExportFailure {
            if (moistenerValue <= 0 || stage < 0) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "Moistener fuel has invalid work/stage fields");
            }
            this.item = item == null ? null : item.copy();
            this.product = product == null ? null : product.copy();
            this.moistenerValue = moistenerValue;
            this.stage = stage;
            this.pairKey = itemCanonical(this.item, -1) + "->"
                    + itemCanonical(this.product, -1);
            this.canonical = pairKey + '|' + moistenerValue + '|' + stage;
        }
    }

    private static final class MoistenerFuelCorpus {
        final List<MoistenerFuelRecord> records;
        final Map<String, MoistenerFuelRecord> byPair;
        final String canonical;

        MoistenerFuelCorpus(List<MoistenerFuelRecord> records, String canonical) {
            this.records = Collections.unmodifiableList(
                    new ArrayList<MoistenerFuelRecord>(records));
            Map<String, MoistenerFuelRecord> pairs =
                    new LinkedHashMap<String, MoistenerFuelRecord>();
            for (MoistenerFuelRecord record : records) {
                pairs.put(record.pairKey, record);
            }
            this.byPair = Collections.unmodifiableMap(pairs);
            this.canonical = canonical;
        }

        MoistenerFuelRecord requirePair(PositionedStack item, PositionedStack product)
                throws ExportFailure {
            String key = onlyPositionedCanonical(item) + "->"
                    + onlyPositionedCanonical(product);
            MoistenerFuelRecord record = byPair.get(key);
            if (record == null) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "Moistener cached fuel/product pair is absent from FuelManager: "
                                + key);
            }
            return record;
        }
    }

    private static String onlyPositionedCanonical(PositionedStack positioned)
            throws ExportFailure {
        if (positioned == null || positioned.items == null
                || positioned.items.length != 1) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "expected one exact positioned fuel/product alternative");
        }
        return itemCanonical(positioned.items[0], -1);
    }

    private static final class SqueezerPage {
        final Object cached;
        final float remnantsChance;
        final String sourceKind;
        final String sortKey;

        SqueezerPage(Object cached, float remnantsChance,
                     String sourceKind, String sortKey) {
            this.cached = cached;
            this.remnantsChance = remnantsChance;
            this.sourceKind = sourceKind;
            this.sortKey = sortKey;
        }
    }

    private static final class SqueezerExpansion {
        final List<SqueezerPage> pages;
        final int regularPages;
        final int containerSourcePages;
        final int expandedContainerPages;

        SqueezerExpansion(List<SqueezerPage> pages, int regularPages,
                          int containerSourcePages, int expandedContainerPages) {
            this.pages = Collections.unmodifiableList(
                    new ArrayList<SqueezerPage>(pages));
            this.regularPages = regularPages;
            this.containerSourcePages = containerSourcePages;
            this.expandedContainerPages = expandedContainerPages;
        }
    }
}
