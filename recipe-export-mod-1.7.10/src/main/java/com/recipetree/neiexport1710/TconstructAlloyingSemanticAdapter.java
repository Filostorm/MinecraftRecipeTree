package com.recipetree.neiexport1710;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Exact TConstruct 1.13.57-GTNH alloying adapter for its fluid-only NEI pages. */
final class TconstructAlloyingSemanticAdapter {
    static final String HANDLER = "tconstruct.plugins.nei.RecipeHandlerAlloying";
    static final String CACHED = HANDLER + "$CachedAlloyingRecipe";
    static final String TANK = "tconstruct.plugins.nei.RecipeHandlerBase$FluidTankElement";
    static final String OPERATION = "tconstruct.smeltery.alloying";
    static final String CONTRACT =
            "gtnh-2.8.4-tconstruct-1.13.57-alloying-fluid-semantics-v1";
    static final String UNPROMOTED = "<unpromoted>";
    static final int EXPECTED_PAGES = 8;
    static final String EXPECTED_COUNT_VECTOR =
            "pages=8,inputSlots=19,outputSlots=8,inputAlternatives=19,"
                    + "outputAlternatives=8,maxInputs=3";
    static final String EXPECTED_SHA256 =
            "9884e8d1d458f32f4db939a4c5f2c975634266c96215ef6fe6909c58809c7b8d";

    private static final Map<ICraftingHandler,
            List<CompleteCategoryAdapters.RecipeSemanticOverride>> SEMANTICS =
            new IdentityHashMap<ICraftingHandler,
                    List<CompleteCategoryAdapters.RecipeSemanticOverride>>();
    private static Observation observation;

    private TconstructAlloyingSemanticAdapter() {}

    static void validatePrototype(ICraftingHandler prototype) throws ExportFailure {
        try {
            requireExactClass(prototype, HANDLER);
            if (!(prototype instanceof TemplateRecipeHandler)
                    || prototype.numRecipes() != 0
                    || prototype.getOverlayIdentifier() != null) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        HANDLER + " prototype topology drifted");
            }
            ClassLoader loader = prototype.getClass().getClassLoader();
            Class<?> cached = Class.forName(CACHED, false, loader);
            requirePrivateField(cached, "fluidTanks", List.class, true);
            requirePrivateField(cached, "minAmount", int.class, false);
            requirePublicMethod(cached, "getIngredient",
                    Class.forName("codechicken.nei.PositionedStack", false, loader));
            requirePublicMethod(cached, "getResult",
                    Class.forName("codechicken.nei.PositionedStack", false, loader));
            requirePublicMethod(cached, "getFluidTanks", List.class);
            Class<?> tank = Class.forName(TANK, false, loader);
            requirePublicField(tank, "position", Rectangle.class);
            requirePublicField(tank, "fluid", FluidStack.class);
            requirePublicField(tank, "capacity", int.class);
            requirePublicField(tank, "flowingTexture", boolean.class);
            Field output = prototype.getClass().getField("OUTPUT_TANK");
            if (!Modifier.isPublic(output.getModifiers())
                    || !Modifier.isStatic(output.getModifiers())
                    || !Modifier.isFinal(output.getModifiers())
                    || output.getType() != Rectangle.class
                    || !new Rectangle(118, 9, 18, 32).equals(output.get(null))) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        HANDLER + ".OUTPUT_TANK topology drifted");
            }
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    HANDLER + " exact structural validation failed", error);
        }
    }

    static ICraftingHandler loadCompleteCategory(ICraftingHandler prototype)
            throws ExportFailure {
        validatePrototype(prototype);
        try {
            ICraftingHandler loaded = prototype.getRecipeHandler(OPERATION);
            requireExactClass(loaded, HANDLER);
            TemplateRecipeHandler target = (TemplateRecipeHandler) loaded;
            if (target.numRecipes() != EXPECTED_PAGES
                    || target.arecipes.size() != EXPECTED_PAGES) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        HANDLER + " page count drifted; expected " + EXPECTED_PAGES
                                + ", got " + target.numRecipes());
            }
            Rectangle outputTank = (Rectangle) loaded.getClass()
                    .getField("OUTPUT_TANK").get(null);
            BuildResult result = new BuildResult();
            for (int index = 0; index < target.arecipes.size(); index++) {
                result.pages.add(buildPage(target.arecipes.get(index), outputTank,
                        index, result));
            }
            Observation current = result.finish();
            synchronized (TconstructAlloyingSemanticAdapter.class) {
                if (observation != null
                        && (!observation.countVector.equals(current.countVector)
                        || !observation.fingerprint.equals(current.fingerprint))) {
                    throw new ExportFailure("HANDLER_UNLOADED",
                            "TConstruct alloying corpus changed across captures in one boot");
                }
                observation = current;
                SEMANTICS.put(target, Collections.unmodifiableList(new ArrayList<
                        CompleteCategoryAdapters.RecipeSemanticOverride>(result.pages)));
            }
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] TConstruct alloying fluid adapter captured "
                            + "countVector={}, fingerprint={}, contract={}",
                    current.countVector, current.fingerprint, CONTRACT);
            return target;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    HANDLER + " exact fluid adapter failed", error);
        }
    }

    static synchronized CompleteCategoryAdapters.RecipeSemanticOverride semanticOverride(
            ICraftingHandler loaded, int recipeIndex) throws ExportFailure {
        requireExactClass(loaded, HANDLER);
        List<CompleteCategoryAdapters.RecipeSemanticOverride> pages = SEMANTICS.get(loaded);
        if (pages == null || pages.size() != loaded.numRecipes()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    HANDLER + " has no intact attached fluid corpus");
        }
        if (recipeIndex < 0 || recipeIndex >= pages.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    HANDLER + " semantic index is out of bounds: " + recipeIndex);
        }
        return pages.get(recipeIndex);
    }

    static synchronized Observation requirePromotedCorpus() throws ExportFailure {
        if (observation == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "TConstruct alloying corpus was not captured before promotion validation");
        }
        if (UNPROMOTED.equals(EXPECTED_COUNT_VECTOR)
                || UNPROMOTED.equals(EXPECTED_SHA256)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "TConstruct alloying corpus is unpromoted; observed countVector="
                            + observation.countVector + ", sha256=" + observation.fingerprint);
        }
        if (!EXPECTED_COUNT_VECTOR.equals(observation.countVector)
                || !EXPECTED_SHA256.equals(observation.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "TConstruct alloying corpus drifted; expected="
                            + EXPECTED_COUNT_VECTOR + '/' + EXPECTED_SHA256 + ", observed="
                            + observation.countVector + '/' + observation.fingerprint);
        }
        return observation;
    }

    private static CompleteCategoryAdapters.RecipeSemanticOverride buildPage(
            Object cached, Rectangle outputTank, int index, BuildResult result)
            throws Exception {
        if (cached == null || !CACHED.equals(cached.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "TConstruct alloying page #" + index + " class drifted");
        }
        TemplateRecipeHandler.CachedRecipe generic =
                (TemplateRecipeHandler.CachedRecipe) cached;
        if (generic.getIngredient() != null || generic.getResult() != null
                || !generic.getIngredients().isEmpty()
                || !generic.getOtherStacks().isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TConstruct alloying page #" + index
                            + " generic item topology drifted");
        }
        List<?> tanks = (List<?>) cached.getClass().getMethod("getFluidTanks").invoke(cached);
        if (tanks == null || tanks.size() < 3) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TConstruct alloying page #" + index
                            + " must expose one output and at least two inputs");
        }
        int inputCount = tanks.size() - 1;
        int width = 36 / inputCount;
        List<CompleteCategoryAdapters.SemanticSlot> inputs =
                new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
        CompleteCategoryAdapters.SemanticSlot output = null;
        int minAmount = Integer.MAX_VALUE;
        int maxInputAmount = 0;
        int sharedCapacity = -1;
        for (int tankIndex = 0; tankIndex < tanks.size(); tankIndex++) {
            Object tank = tanks.get(tankIndex);
            if (tank == null || !TANK.equals(tank.getClass().getName())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "TConstruct alloying page #" + index + " tank class drifted");
            }
            Rectangle position = (Rectangle) tank.getClass().getField("position").get(tank);
            FluidStack fluid = (FluidStack) tank.getClass().getField("fluid").get(tank);
            int capacity = tank.getClass().getField("capacity").getInt(tank);
            boolean flowing = tank.getClass().getField("flowingTexture").getBoolean(tank);
            if (position == null || fluid == null || fluid.getFluid() == null
                    || fluid.amount <= 0 || capacity <= 0 || flowing
                    || (fluid.tag != null && !fluid.tag.hasNoTags())) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "TConstruct alloying page #" + index + " tank #" + tankIndex
                                + " topology drifted; fluid="
                                + (fluid == null ? "<null>" : FluidRegistry.getFluidName(fluid))
                                + ", amount=" + (fluid == null ? -1 : fluid.amount)
                                + ", capacity=" + capacity + ", flowing=" + flowing
                                + ", position=" + position);
            }
            if (sharedCapacity < 0) {
                sharedCapacity = capacity;
            } else if (capacity != sharedCapacity) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "TConstruct alloying page #" + index + " tank #" + tankIndex
                                + " capacity drifted; expected shared capacity "
                                + sharedCapacity + ", got " + capacity);
            }
            CompleteCategoryAdapters.SemanticSlot slot = fluidSlot(fluid, index);
            if (tankIndex == 0) {
                if (position != outputTank) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "TConstruct alloying page #" + index
                                    + " output tank identity drifted");
                }
                output = slot;
            } else {
                int inputIndex = tankIndex - 1;
                Rectangle expected = new Rectangle(21 + width * inputIndex, 9,
                        inputIndex == inputCount - 1 ? 36 - width * inputIndex : width,
                        32);
                if (!expected.equals(position)) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "TConstruct alloying page #" + index
                                    + " input tank #" + inputIndex + " position drifted");
                }
                minAmount = Math.min(minAmount, fluid.amount);
                maxInputAmount = Math.max(maxInputAmount, fluid.amount);
                inputs.add(slot);
            }
        }
        int cachedMin = privateField(cached.getClass(), "minAmount").getInt(cached);
        if (cachedMin != minAmount) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TConstruct alloying page #" + index + " minAmount drifted");
        }
        // TConstruct uses the maximum input amount as the common GUI tank scale.
        // The produced amount can legitimately exceed that scale, so comparing the
        // output quantity to capacity would reject valid alloy recipes.
        if (sharedCapacity != maxInputAmount) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TConstruct alloying page #" + index
                            + " shared tank capacity drifted; expected maximum input "
                            + maxInputAmount + ", got " + sharedCapacity);
        }
        List<CompleteCategoryAdapters.SemanticSlot> outputs =
                Collections.singletonList(output);
        String canonical = CONTRACT + '|' + cachedMin + '|' + maxInputAmount + '|'
                + slotsCanonical(inputs) + '|' + slotsCanonical(outputs);
        result.canonicals.add(canonical);
        result.inputSlots += inputs.size();
        result.maxInputs = Math.max(result.maxInputs, inputs.size());
        return new CompleteCategoryAdapters.RecipeSemanticOverride(
                "tconstruct-alloying:" + Naming.sha256(canonical), inputs, outputs);
    }

    private static CompleteCategoryAdapters.SemanticSlot fluidSlot(
            FluidStack original, int index) throws ExportFailure {
        FluidStack copy = original.copy();
        ItemStack proxy = GTUtility.getFluidDisplayStack(copy, true, true);
        if (proxy == null || proxy.getItem() == null) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TConstruct alloying page #" + index
                            + " could not create a fluid-display proxy");
        }
        StackIdentity identity = StackIdentity.of(proxy);
        String expectedKey = "fluid|fluid:" + FluidRegistry.getFluidName(copy);
        if (!identity.isFluid() || !expectedKey.equals(identity.key)
                || identity.amount != copy.amount || identity.canonicalNbt != null) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TConstruct alloying page #" + index + " fluid proxy drifted");
        }
        CompleteCategoryAdapters.SemanticAlternative alternative =
                new CompleteCategoryAdapters.SemanticAlternative(proxy, copy.amount,
                        CompleteCategoryAdapters.canonicalStackIdentity(
                                identity, copy.amount));
        return new CompleteCategoryAdapters.SemanticSlot(
                Collections.singletonList(alternative));
    }

    private static String slotsCanonical(
            List<CompleteCategoryAdapters.SemanticSlot> slots) {
        StringBuilder value = new StringBuilder();
        for (CompleteCategoryAdapters.SemanticSlot slot : slots) {
            value.append('[');
            for (CompleteCategoryAdapters.SemanticAlternative alternative
                    : slot.alternatives) {
                value.append(alternative.canonicalIdentity).append('|');
            }
            value.append(']');
        }
        return value.toString();
    }

    private static void requireExactClass(Object value, String expected)
            throws ExportFailure {
        if (value == null || !expected.equals(value.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", "expected exact " + expected
                    + ", got " + (value == null ? "<null>" : value.getClass().getName()));
        }
    }

    private static void requirePrivateField(Class<?> type, String name,
                                            Class<?> fieldType, boolean requireFinal)
            throws Exception {
        Field field = type.getDeclaredField(name);
        int modifiers = field.getModifiers();
        if (field.getType() != fieldType || !Modifier.isPrivate(modifiers)
                || Modifier.isStatic(modifiers)
                || requireFinal != Modifier.isFinal(modifiers)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + '.' + name + " field topology drifted");
        }
    }

    private static Field privateField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void requirePublicField(Class<?> type, String name,
                                           Class<?> fieldType) throws Exception {
        Field field = type.getField(name);
        if (field.getType() != fieldType || !Modifier.isPublic(field.getModifiers())
                || Modifier.isStatic(field.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + '.' + name + " field topology drifted");
        }
    }

    private static void requirePublicMethod(Class<?> type, String name,
                                            Class<?> returnType) throws Exception {
        Method method = type.getMethod(name);
        if (method.getReturnType() != returnType
                || !Modifier.isPublic(method.getModifiers())
                || Modifier.isStatic(method.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + '.' + name + " method topology drifted");
        }
    }

    static final class Observation {
        final String countVector;
        final String fingerprint;

        Observation(String countVector, String fingerprint) {
            this.countVector = countVector;
            this.fingerprint = fingerprint;
        }
    }

    private static final class BuildResult {
        final List<CompleteCategoryAdapters.RecipeSemanticOverride> pages =
                new ArrayList<CompleteCategoryAdapters.RecipeSemanticOverride>();
        final List<String> canonicals = new ArrayList<String>();
        int inputSlots;
        int maxInputs;

        Observation finish() {
            List<String> sorted = new ArrayList<String>(canonicals);
            Collections.sort(sorted);
            String vector = "pages=" + pages.size()
                    + ",inputSlots=" + inputSlots
                    + ",outputSlots=" + pages.size()
                    + ",inputAlternatives=" + inputSlots
                    + ",outputAlternatives=" + pages.size()
                    + ",maxInputs=" + maxInputs;
            StringBuilder canonical = new StringBuilder(vector).append('\n')
                    .append(CONTRACT).append('\n');
            for (String row : sorted) canonical.append(row).append('\n');
            return new Observation(vector, Naming.sha256(canonical.toString()));
        }
    }
}
