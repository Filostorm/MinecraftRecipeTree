package com.recipetree.neiexport1710;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Exact BuildCraft Compat 7.1.18 Refinery adapter for its fluid-only NEI pages. */
final class BuildcraftRefinerySemanticAdapter {
    static final String HANDLER = "buildcraft.compat.nei.RecipeHandlerRefinery";
    static final String CACHED = HANDLER + "$CachedRefineryRecipe";
    static final String TANK = "buildcraft.compat.nei.PositionedFluidTank";
    static final String OPERATION = "buildcraft.refinery";
    static final String CONTRACT =
            "gtnh-2.8.4-buildcraft-compat-7.1.18-refinery-fluid-semantics-v1";
    static final String UNPROMOTED = "<unpromoted>";
    static final int EXPECTED_PAGES = 2;
    static final String EXPECTED_COUNT_VECTOR =
            "pages=2,inputSlots=2,outputSlots=2,inputAlternatives=2,"
                    + "outputAlternatives=2,dualInputPages=0,totalEnergyPerTick=220,"
                    + "totalTicks=2";
    static final String EXPECTED_SHA256 =
            "3850db979470796bf503e0a09fd185d4dd7df254135c8d3bae13229a6640179c";

    private static final Rectangle FIRST_INPUT = new Rectangle(33, 23, 16, 16);
    private static final Rectangle SECOND_INPUT = new Rectangle(121, 23, 16, 16);
    private static final Rectangle OUTPUT = new Rectangle(77, 23, 16, 16);

    private static final Map<ICraftingHandler,
            List<CompleteCategoryAdapters.RecipeSemanticOverride>> SEMANTICS =
            new IdentityHashMap<ICraftingHandler,
                    List<CompleteCategoryAdapters.RecipeSemanticOverride>>();
    private static Observation observation;

    private BuildcraftRefinerySemanticAdapter() {}

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
            requirePublicField(cached, "tanks", List.class);
            requirePublicField(cached, "energy", int.class);
            requirePublicField(cached, "time", long.class);
            requirePublicMethod(cached, "getFluidTanks", List.class);
            requirePublicMethod(cached, "getResult",
                    Class.forName("codechicken.nei.PositionedStack", false, loader));

            Class<?> tank = Class.forName(TANK, false, loader);
            requirePublicField(tank, "tank", FluidTank.class);
            requirePublicField(tank, "position", Rectangle.class);
            requirePublicField(tank, "overlayTexture", String.class);
            requirePublicField(tank, "overlayTexturePos", Point.class);
            requirePublicField(tank, "flowingTexture", boolean.class);
            requirePublicField(tank, "showAmount", boolean.class);
            requirePublicField(tank, "perTick", boolean.class);
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
                                + ", got numRecipes=" + target.numRecipes()
                                + ", arecipes=" + target.arecipes.size());
            }
            BuildResult result = new BuildResult();
            for (int index = 0; index < target.arecipes.size(); index++) {
                result.pages.add(buildPage(target.arecipes.get(index), index, result));
            }
            Observation current = result.finish();
            synchronized (BuildcraftRefinerySemanticAdapter.class) {
                if (observation != null
                        && (!observation.countVector.equals(current.countVector)
                        || !observation.fingerprint.equals(current.fingerprint))) {
                    throw new ExportFailure("HANDLER_UNLOADED",
                            "BuildCraft Refinery corpus changed across captures in one boot; first="
                                    + observation.countVector + '/' + observation.fingerprint
                                    + ", second=" + current.countVector + '/'
                                    + current.fingerprint);
                }
                observation = current;
                SEMANTICS.put(target, Collections.unmodifiableList(new ArrayList<
                        CompleteCategoryAdapters.RecipeSemanticOverride>(result.pages)));
            }
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] BuildCraft Refinery fluid adapter captured "
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
                    "BuildCraft Refinery corpus was not captured before promotion validation");
        }
        if (UNPROMOTED.equals(EXPECTED_COUNT_VECTOR)
                || UNPROMOTED.equals(EXPECTED_SHA256)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "BuildCraft Refinery corpus is unpromoted; observed countVector="
                            + observation.countVector + ", sha256=" + observation.fingerprint);
        }
        if (!EXPECTED_COUNT_VECTOR.equals(observation.countVector)
                || !EXPECTED_SHA256.equals(observation.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "BuildCraft Refinery corpus drifted; expected="
                            + EXPECTED_COUNT_VECTOR + '/' + EXPECTED_SHA256 + ", observed="
                            + observation.countVector + '/' + observation.fingerprint);
        }
        return observation;
    }

    private static CompleteCategoryAdapters.RecipeSemanticOverride buildPage(
            Object cached, int index, BuildResult result) throws Exception {
        if (cached == null || !CACHED.equals(cached.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "BuildCraft Refinery page #" + index + " class drifted");
        }
        TemplateRecipeHandler.CachedRecipe generic =
                (TemplateRecipeHandler.CachedRecipe) cached;
        if (generic.getResult() != null || !generic.getIngredients().isEmpty()
                || !generic.getOtherStacks().isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "BuildCraft Refinery page #" + index
                            + " generic item topology drifted");
        }
        List<?> tanks = (List<?>) cached.getClass()
                .getMethod("getFluidTanks").invoke(cached);
        if (tanks == null || (tanks.size() != 2 && tanks.size() != 3)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "BuildCraft Refinery page #" + index
                            + " must expose two or three positioned fluid tanks");
        }
        int energy = cached.getClass().getField("energy").getInt(cached);
        long time = cached.getClass().getField("time").getLong(cached);
        if (energy <= 0 || time <= 0) {
            throw new ExportFailure("QUANTITY_INVALID",
                    "BuildCraft Refinery page #" + index
                            + " has invalid energy/time " + energy + '/' + time);
        }

        CompleteCategoryAdapters.SemanticSlot firstInput = null;
        CompleteCategoryAdapters.SemanticSlot secondInput = null;
        CompleteCategoryAdapters.SemanticSlot output = null;
        for (Object positionedTank : tanks) {
            TankCapture capture = captureTank(positionedTank, index);
            if (FIRST_INPUT.equals(capture.position) && firstInput == null) {
                firstInput = capture.slot;
            } else if (SECOND_INPUT.equals(capture.position) && secondInput == null) {
                secondInput = capture.slot;
            } else if (OUTPUT.equals(capture.position) && output == null) {
                output = capture.slot;
            } else {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "BuildCraft Refinery page #" + index
                                + " has an unknown or duplicate tank at " + capture.position);
            }
        }
        if (firstInput == null || output == null
                || (tanks.size() == 2) != (secondInput == null)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "BuildCraft Refinery page #" + index + " tank roles drifted");
        }
        List<CompleteCategoryAdapters.SemanticSlot> inputs =
                new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
        inputs.add(firstInput);
        if (secondInput != null) inputs.add(secondInput);
        List<CompleteCategoryAdapters.SemanticSlot> outputs =
                Collections.singletonList(output);
        String canonical = CONTRACT + '|' + energy + '|' + time + '|'
                + slotsCanonical(inputs) + '|' + slotsCanonical(outputs);
        result.canonicals.add(canonical);
        result.inputSlots += inputs.size();
        result.inputAlternatives += inputs.size();
        if (secondInput != null) result.dualInputPages++;
        result.totalEnergyPerTick += energy;
        result.totalTicks += time;
        return new CompleteCategoryAdapters.RecipeSemanticOverride(
                "buildcraft-refinery:" + Naming.sha256(canonical), inputs, outputs);
    }

    private static TankCapture captureTank(Object value, int page) throws Exception {
        if (value == null || !TANK.equals(value.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "BuildCraft Refinery page #" + page + " tank class drifted");
        }
        Class<?> type = value.getClass();
        FluidTank tank = (FluidTank) type.getField("tank").get(value);
        Rectangle position = (Rectangle) type.getField("position").get(value);
        if (tank == null || position == null
                || type.getField("overlayTexture").get(value) != null
                || type.getField("overlayTexturePos").get(value) != null
                || type.getField("flowingTexture").getBoolean(value)
                || !type.getField("showAmount").getBoolean(value)
                || type.getField("perTick").getBoolean(value)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "BuildCraft Refinery page #" + page + " positioned tank topology drifted");
        }
        FluidStack fluid = tank.getFluid();
        if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0
                || tank.getCapacity() != fluid.amount
                || (fluid.tag != null && !fluid.tag.hasNoTags())) {
            throw new ExportFailure("QUANTITY_INVALID",
                    "BuildCraft Refinery page #" + page + " has an invalid fluid tank");
        }
        return new TankCapture(new Rectangle(position), fluidSlot(fluid, page));
    }

    private static CompleteCategoryAdapters.SemanticSlot fluidSlot(
            FluidStack original, int index) throws ExportFailure {
        FluidStack copy = original.copy();
        ItemStack proxy = GTUtility.getFluidDisplayStack(copy, true, true);
        if (proxy == null || proxy.getItem() == null) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "BuildCraft Refinery page #" + index
                            + " could not create a fluid-display proxy");
        }
        StackIdentity identity = StackIdentity.of(proxy);
        String expectedKey = "fluid|fluid:" + FluidRegistry.getFluidName(copy);
        if (!identity.isFluid() || !expectedKey.equals(identity.key)
                || identity.amount != copy.amount || identity.canonicalNbt != null) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "BuildCraft Refinery page #" + index + " fluid proxy drifted");
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

    private static final class TankCapture {
        final Rectangle position;
        final CompleteCategoryAdapters.SemanticSlot slot;

        TankCapture(Rectangle position, CompleteCategoryAdapters.SemanticSlot slot) {
            this.position = position;
            this.slot = slot;
        }
    }

    private static final class BuildResult {
        final List<CompleteCategoryAdapters.RecipeSemanticOverride> pages =
                new ArrayList<CompleteCategoryAdapters.RecipeSemanticOverride>();
        final List<String> canonicals = new ArrayList<String>();
        int inputSlots;
        int inputAlternatives;
        int dualInputPages;
        long totalEnergyPerTick;
        long totalTicks;

        Observation finish() {
            List<String> sorted = new ArrayList<String>(canonicals);
            Collections.sort(sorted);
            String vector = "pages=" + pages.size()
                    + ",inputSlots=" + inputSlots
                    + ",outputSlots=" + pages.size()
                    + ",inputAlternatives=" + inputAlternatives
                    + ",outputAlternatives=" + pages.size()
                    + ",dualInputPages=" + dualInputPages
                    + ",totalEnergyPerTick=" + totalEnergyPerTick
                    + ",totalTicks=" + totalTicks;
            StringBuilder canonical = new StringBuilder(vector).append('\n')
                    .append(CONTRACT).append('\n');
            for (String row : sorted) canonical.append(row).append('\n');
            return new Observation(vector, Naming.sha256(canonical.toString()));
        }
    }
}
