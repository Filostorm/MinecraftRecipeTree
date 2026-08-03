package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
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
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Exact TConstruct 1.13.57-GTNH melting adapter for its fluid-only NEI results. */
final class TconstructMeltingSemanticAdapter {
    static final String HANDLER = "tconstruct.plugins.nei.RecipeHandlerMelting";
    static final String CACHED = HANDLER + "$CachedMeltingRecipe";
    static final String TANK =
            "tconstruct.plugins.nei.RecipeHandlerBase$FluidTankElement";
    static final String OPERATION = "tconstruct.smeltery.melting";
    static final String CONTRACT =
            "gtnh-2.8.4-tconstruct-1.13.57-melting-fluid-semantics-v1";
    static final String UNPROMOTED = "<unpromoted>";
    static final int EXPECTED_PAGES = 940;
    static final String EXPECTED_COUNT_VECTOR =
            "pages=940,inputSlots=940,outputSlots=940,inputAlternatives=940,"
                    + "outputAlternatives=940";
    static final String EXPECTED_SHA256 =
            "9806a1a11ed1fd3e7c4ec0eb3b505bbc9b67e5ff935717d810b9f8292e41e962";

    private static final Map<ICraftingHandler,
            List<CompleteCategoryAdapters.RecipeSemanticOverride>> SEMANTICS =
            new IdentityHashMap<ICraftingHandler,
                    List<CompleteCategoryAdapters.RecipeSemanticOverride>>();
    private static Observation observation;

    private TconstructMeltingSemanticAdapter() {}

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
            requirePrivateFinalField(cached, "input", PositionedStack.class);
            requirePrivateFinalField(cached, "temperature", int.class);
            requirePrivateFinalField(cached, "output",
                    Class.forName(TANK, false, loader));
            requirePublicMethod(cached, "getIngredient", PositionedStack.class);
            requirePublicMethod(cached, "getResult", PositionedStack.class);
            requirePublicMethod(cached, "getFluidTanks", List.class);
            Class<?> tank = Class.forName(TANK, false, loader);
            requirePublicField(tank, "position", Rectangle.class);
            requirePublicField(tank, "fluid", FluidStack.class);
            requirePublicField(tank, "capacity", int.class);
            requirePublicField(tank, "flowingTexture", boolean.class);
            Field moltenTank = prototype.getClass().getField("MOLTEN_TANK");
            if (!Modifier.isPublic(moltenTank.getModifiers())
                    || !Modifier.isStatic(moltenTank.getModifiers())
                    || moltenTank.getType() != Rectangle.class
                    || moltenTank.get(null) == null) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        HANDLER + ".MOLTEN_TANK topology drifted");
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
            if (target.numRecipes() != EXPECTED_PAGES) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        HANDLER + " page count drifted; expected " + EXPECTED_PAGES
                                + ", got " + target.numRecipes());
            }
            Rectangle moltenTank = (Rectangle) loaded.getClass()
                    .getField("MOLTEN_TANK").get(null);
            BuildResult result = new BuildResult();
            for (int index = 0; index < target.arecipes.size(); index++) {
                result.pages.add(buildPage(target.arecipes.get(index), moltenTank,
                        index, result));
            }
            Observation current = result.finish();
            synchronized (TconstructMeltingSemanticAdapter.class) {
                if (observation != null
                        && (!observation.countVector.equals(current.countVector)
                        || !observation.fingerprint.equals(current.fingerprint))) {
                    throw new ExportFailure("HANDLER_UNLOADED",
                            "TConstruct melting corpus changed across captures in one boot; first="
                                    + observation.countVector + '/' + observation.fingerprint
                                    + ", second=" + current.countVector + '/'
                                    + current.fingerprint);
                }
                observation = current;
                SEMANTICS.put(target, Collections.unmodifiableList(new ArrayList<
                        CompleteCategoryAdapters.RecipeSemanticOverride>(result.pages)));
            }
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] TConstruct melting fluid adapter captured "
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
                    "TConstruct melting corpus was not captured before promotion validation");
        }
        if (UNPROMOTED.equals(EXPECTED_COUNT_VECTOR)
                || UNPROMOTED.equals(EXPECTED_SHA256)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "TConstruct melting corpus is unpromoted; observed countVector="
                            + observation.countVector + ", sha256=" + observation.fingerprint);
        }
        if (!EXPECTED_COUNT_VECTOR.equals(observation.countVector)
                || !EXPECTED_SHA256.equals(observation.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "TConstruct melting corpus drifted; expected="
                            + EXPECTED_COUNT_VECTOR + '/' + EXPECTED_SHA256 + ", observed="
                            + observation.countVector + '/' + observation.fingerprint);
        }
        return observation;
    }

    private static CompleteCategoryAdapters.RecipeSemanticOverride buildPage(
            Object cached, Rectangle moltenTank, int index, BuildResult result)
            throws Exception {
        if (cached == null || !CACHED.equals(cached.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "TConstruct melting page #" + index + " class drifted");
        }
        PositionedStack ingredient = (PositionedStack)
                cached.getClass().getMethod("getIngredient").invoke(cached);
        Object genericResult = cached.getClass().getMethod("getResult").invoke(cached);
        List<?> genericOthers = ((TemplateRecipeHandler.CachedRecipe) cached).getOtherStacks();
        if (genericResult != null || genericOthers == null || !genericOthers.isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TConstruct melting page #" + index
                            + " generic result topology drifted");
        }
        List<?> tanks = (List<?>) cached.getClass()
                .getMethod("getFluidTanks").invoke(cached);
        if (tanks == null || tanks.size() != 1) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TConstruct melting page #" + index
                            + " must expose exactly one fluid tank");
        }
        Object tank = tanks.get(0);
        if (tank == null || !TANK.equals(tank.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "TConstruct melting page #" + index + " tank class drifted");
        }
        Field positionField = tank.getClass().getField("position");
        Field fluidField = tank.getClass().getField("fluid");
        Field capacityField = tank.getClass().getField("capacity");
        Field flowingField = tank.getClass().getField("flowingTexture");
        FluidStack fluid = (FluidStack) fluidField.get(tank);
        if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0
                || (fluid.tag != null && !fluid.tag.hasNoTags())) {
            throw new ExportFailure("QUANTITY_INVALID",
                    "TConstruct melting page #" + index + " has invalid output fluid");
        }
        if (positionField.get(tank) != moltenTank
                || capacityField.getInt(tank) != fluid.amount
                || flowingField.getBoolean(tank)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TConstruct melting page #" + index + " tank topology drifted");
        }
        int temperature = privateField(cached.getClass(), "temperature").getInt(cached);
        if (temperature <= 0) {
            throw new ExportFailure("QUANTITY_INVALID",
                    "TConstruct melting page #" + index + " has invalid temperature "
                            + temperature);
        }
        CompleteCategoryAdapters.SemanticSlot input = itemSlot(ingredient, index);
        CompleteCategoryAdapters.SemanticSlot output = fluidSlot(fluid, index);
        String canonical = CONTRACT + '|' + temperature + '|'
                + slotCanonical(input) + '|' + slotCanonical(output);
        result.canonicals.add(canonical);
        result.inputAlternatives += input.alternatives.size();
        return new CompleteCategoryAdapters.RecipeSemanticOverride(
                "tconstruct-melting:" + Naming.sha256(canonical),
                Collections.singletonList(input), Collections.singletonList(output));
    }

    private static CompleteCategoryAdapters.SemanticSlot itemSlot(
            PositionedStack positioned, int index) throws ExportFailure {
        if (positioned == null || positioned.items == null
                || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TConstruct melting page #" + index + " has no input alternatives");
        }
        List<CompleteCategoryAdapters.SemanticAlternative> alternatives =
                new ArrayList<CompleteCategoryAdapters.SemanticAlternative>();
        for (ItemStack original : positioned.items) {
            if (original == null || original.getItem() == null || original.stackSize <= 0) {
                throw new ExportFailure("QUANTITY_INVALID",
                        "TConstruct melting page #" + index + " has invalid input");
            }
            ItemStack copy = original.copy();
            StackIdentity identity = StackIdentity.of(copy);
            alternatives.add(new CompleteCategoryAdapters.SemanticAlternative(
                    copy, copy.stackSize,
                    CompleteCategoryAdapters.canonicalStackIdentity(
                            identity, copy.stackSize)));
        }
        Collections.sort(alternatives,
                new Comparator<CompleteCategoryAdapters.SemanticAlternative>() {
                    @Override
                    public int compare(
                            CompleteCategoryAdapters.SemanticAlternative left,
                            CompleteCategoryAdapters.SemanticAlternative right) {
                        return left.canonicalIdentity.compareTo(right.canonicalIdentity);
                    }
                });
        return new CompleteCategoryAdapters.SemanticSlot(alternatives);
    }

    private static CompleteCategoryAdapters.SemanticSlot fluidSlot(
            FluidStack original, int index) throws ExportFailure {
        FluidStack copy = original.copy();
        ItemStack proxy = GTUtility.getFluidDisplayStack(copy, true, true);
        if (proxy == null || proxy.getItem() == null) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TConstruct melting page #" + index
                            + " could not create a fluid-display proxy");
        }
        StackIdentity identity = StackIdentity.of(proxy);
        String expectedKey = "fluid|fluid:" + FluidRegistry.getFluidName(copy);
        if (!identity.isFluid() || !expectedKey.equals(identity.key)
                || identity.amount != copy.amount || identity.canonicalNbt != null) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TConstruct melting page #" + index + " fluid proxy drifted");
        }
        CompleteCategoryAdapters.SemanticAlternative alternative =
                new CompleteCategoryAdapters.SemanticAlternative(proxy, copy.amount,
                        CompleteCategoryAdapters.canonicalStackIdentity(
                                identity, copy.amount));
        return new CompleteCategoryAdapters.SemanticSlot(
                Collections.singletonList(alternative));
    }

    private static String slotCanonical(CompleteCategoryAdapters.SemanticSlot slot) {
        StringBuilder value = new StringBuilder();
        for (CompleteCategoryAdapters.SemanticAlternative alternative : slot.alternatives) {
            value.append(alternative.canonicalIdentity).append('|');
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

    private static void requirePrivateFinalField(Class<?> type, String name,
                                                  Class<?> fieldType) throws Exception {
        Field field = type.getDeclaredField(name);
        int modifiers = field.getModifiers();
        if (field.getType() != fieldType || !Modifier.isPrivate(modifiers)
                || !Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers)) {
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
        int inputAlternatives;

        Observation finish() {
            List<String> sorted = new ArrayList<String>(canonicals);
            Collections.sort(sorted);
            String vector = "pages=" + pages.size()
                    + ",inputSlots=" + pages.size()
                    + ",outputSlots=" + pages.size()
                    + ",inputAlternatives=" + inputAlternatives
                    + ",outputAlternatives=" + pages.size();
            StringBuilder canonical = new StringBuilder(vector).append('\n')
                    .append(CONTRACT).append('\n');
            for (String row : sorted) canonical.append(row).append('\n');
            return new Observation(vector, Naming.sha256(canonical.toString()));
        }
    }
}
