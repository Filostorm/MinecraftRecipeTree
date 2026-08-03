package com.recipetree.neiexport1710;

import codechicken.nei.ItemStackMap;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact Ender IO 2.9.28 Vat adapter for its item-plus-fluid NEI pages. */
final class EnderIoVatSemanticAdapter {
    static final String HANDLER = "crazypants.enderio.nei.VatRecipeHandler";
    static final String CACHED = HANDLER + "$InnerVatRecipe";
    static final String OPERATION = "EnderIOVat";
    static final String CONTRACT =
            "gtnh-2.8.4-enderio-2.9.28-vat-fluid-semantics-v1";
    static final String UNPROMOTED = "<unpromoted>";
    static final int EXPECTED_PAGES = 8;
    static final String EXPECTED_COUNT_VECTOR =
            "pages=8,itemInputSlots=15,fluidInputSlots=8,outputSlots=8,"
                    + "itemInputAlternatives=57,fluidInputAlternatives=27,"
                    + "outputAlternatives=27,totalInputFluid=49975,"
                    + "totalOutputFluid=23632,totalEnergy=105000";
    static final String EXPECTED_SHA256 =
            "32a0a5d2feb2e0f32bb105b36b6c3534513a87c23a2f0b26451b432f65647411";

    private static final Map<ICraftingHandler,
            List<CompleteCategoryAdapters.RecipeSemanticOverride>> SEMANTICS =
            new IdentityHashMap<ICraftingHandler,
                    List<CompleteCategoryAdapters.RecipeSemanticOverride>>();
    private static Observation observation;

    private EnderIoVatSemanticAdapter() {}

    static void validatePrototype(ICraftingHandler prototype) throws ExportFailure {
        try {
            requireExactClass(prototype, HANDLER);
            if (!(prototype instanceof TemplateRecipeHandler)
                    || prototype.numRecipes() != 0
                    || !OPERATION.equals(prototype.getOverlayIdentifier())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        HANDLER + " prototype topology drifted");
            }
            ClassLoader loader = prototype.getClass().getClassLoader();
            Class<?> cached = Class.forName(CACHED, false, loader);
            requirePrivateField(cached, "inputs", List.class);
            requirePrivateField(cached, "firstItemMultiplier", ItemStackMap.class);
            requirePrivateField(cached, "secondItemMultiplier", ItemStackMap.class);
            requirePrivateField(cached, "fluidMultiplier", Map.class);
            requirePrivateField(cached, "energy", int.class);
            requirePrivateField(cached, "result", FluidStack.class);
            requirePrivateField(cached, "inFluid", FluidStack.class);
            requirePublicMethod(cached, "getIngredients", List.class);
            requirePublicMethod(cached, "getInputFluidAmount", int.class);
            requirePublicMethod(cached, "getResultFluidAmount", int.class);
            requirePublicMethod(cached, "getResult", PositionedStack.class);
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
            synchronized (EnderIoVatSemanticAdapter.class) {
                if (observation != null
                        && (!observation.countVector.equals(current.countVector)
                        || !observation.fingerprint.equals(current.fingerprint))) {
                    throw new ExportFailure("HANDLER_UNLOADED",
                            "Ender IO Vat corpus changed across captures in one boot; first="
                                    + observation.countVector + '/' + observation.fingerprint
                                    + ", second=" + current.countVector + '/'
                                    + current.fingerprint);
                }
                observation = current;
                SEMANTICS.put(target, Collections.unmodifiableList(new ArrayList<
                        CompleteCategoryAdapters.RecipeSemanticOverride>(result.pages)));
            }
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Ender IO Vat fluid adapter captured "
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
                    "Ender IO Vat corpus was not captured before promotion validation");
        }
        if (UNPROMOTED.equals(EXPECTED_COUNT_VECTOR)
                || UNPROMOTED.equals(EXPECTED_SHA256)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Ender IO Vat corpus is unpromoted; observed countVector="
                            + observation.countVector + ", sha256=" + observation.fingerprint);
        }
        if (!EXPECTED_COUNT_VECTOR.equals(observation.countVector)
                || !EXPECTED_SHA256.equals(observation.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Ender IO Vat corpus drifted; expected="
                            + EXPECTED_COUNT_VECTOR + '/' + EXPECTED_SHA256 + ", observed="
                            + observation.countVector + '/' + observation.fingerprint);
        }
        return observation;
    }

    private static CompleteCategoryAdapters.RecipeSemanticOverride buildPage(
            Object cached, int index, BuildResult result) throws Exception {
        if (cached == null || !CACHED.equals(cached.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Ender IO Vat page #" + index + " class drifted");
        }
        TemplateRecipeHandler.CachedRecipe generic =
                (TemplateRecipeHandler.CachedRecipe) cached;
        if (generic.getResult() != null || !generic.getOtherStacks().isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Ender IO Vat page #" + index
                            + " generic result/other-stack topology drifted");
        }
        Class<?> type = cached.getClass();
        List<?> rawInputs = (List<?>) privateField(type, "inputs").get(cached);
        if (rawInputs == null || rawInputs.isEmpty() || rawInputs.size() > 2) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Ender IO Vat page #" + index + " item-input topology drifted");
        }
        List<CompleteCategoryAdapters.SemanticSlot> inputs =
                new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
        List<PositionedStack> positioned = new ArrayList<PositionedStack>();
        for (int inputIndex = 0; inputIndex < rawInputs.size(); inputIndex++) {
            Object raw = rawInputs.get(inputIndex);
            if (!(raw instanceof PositionedStack)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "Ender IO Vat page #" + index + " input class drifted");
            }
            PositionedStack value = (PositionedStack) raw;
            int expectedX = inputIndex == 0 ? 51 : 100;
            if (value.relx != expectedX || value.rely != 1) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "Ender IO Vat page #" + index + " input position drifted");
            }
            positioned.add(value);
            CompleteCategoryAdapters.SemanticSlot slot = itemSlot(value, index, inputIndex);
            inputs.add(slot);
            result.itemInputSlots++;
            result.itemInputAlternatives += slot.alternatives.size();
        }

        FluidStack inFluid = (FluidStack) privateField(type, "inFluid").get(cached);
        FluidStack outFluid = (FluidStack) privateField(type, "result").get(cached);
        int energy = privateField(type, "energy").getInt(cached);
        if (inFluid == null || inFluid.getFluid() == null
                || outFluid == null || outFluid.getFluid() == null || energy <= 0) {
            throw new ExportFailure("QUANTITY_INVALID",
                    "Ender IO Vat page #" + index + " fluid/energy topology drifted");
        }
        @SuppressWarnings("unchecked")
        ItemStackMap<Float> first = (ItemStackMap<Float>)
                privateField(type, "firstItemMultiplier").get(cached);
        @SuppressWarnings("unchecked")
        ItemStackMap<Float> second = (ItemStackMap<Float>)
                privateField(type, "secondItemMultiplier").get(cached);
        @SuppressWarnings("unchecked")
        Map<FluidStack, Float> fluidMultipliers = (Map<FluidStack, Float>)
                privateField(type, "fluidMultiplier").get(cached);
        if (first == null || second == null || fluidMultipliers == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Ender IO Vat page #" + index + " multiplier maps are missing");
        }
        float fluidMultiplier = multiplier(fluidMultipliers.get(inFluid));
        Map<Integer, Integer> quantities = new LinkedHashMap<Integer, Integer>();
        ItemStack[] firstItems = positioned.get(0).items;
        ItemStack[] secondItems = positioned.size() == 1
                ? new ItemStack[] { null } : positioned.get(1).items;
        for (ItemStack firstItem : firstItems) {
            float firstMultiplier = multiplier(first.getOrDefault(firstItem, Float.valueOf(1F)));
            for (ItemStack secondItem : secondItems) {
                float secondMultiplier = secondItem == null ? 1F
                        : multiplier(second.getOrDefault(secondItem, Float.valueOf(1F)));
                int inputAmount = Math.round(1000F * firstMultiplier * secondMultiplier);
                int outputAmount = Math.round(inputAmount * fluidMultiplier);
                if (inputAmount <= 0 || outputAmount <= 0) {
                    throw new ExportFailure("QUANTITY_INVALID",
                            "Ender IO Vat page #" + index
                                    + " derived a non-positive fluid quantity");
                }
                Integer prior = quantities.put(Integer.valueOf(inputAmount),
                        Integer.valueOf(outputAmount));
                if (prior != null && prior.intValue() != outputAmount) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "Ender IO Vat page #" + index
                                    + " has inconsistent output for input amount "
                                    + inputAmount);
                }
            }
        }
        List<Integer> amounts = new ArrayList<Integer>(quantities.keySet());
        Collections.sort(amounts);
        CompleteCategoryAdapters.SemanticSlot fluidInput =
                fluidSlot(inFluid, amounts, index, "input");
        List<Integer> outputAmounts = new ArrayList<Integer>();
        for (Integer amount : amounts) outputAmounts.add(quantities.get(amount));
        Collections.sort(outputAmounts);
        outputAmounts = deduplicate(outputAmounts);
        CompleteCategoryAdapters.SemanticSlot fluidOutput =
                fluidSlot(outFluid, outputAmounts, index, "output");
        inputs.add(fluidInput);
        List<CompleteCategoryAdapters.SemanticSlot> outputs =
                Collections.singletonList(fluidOutput);

        String canonical = CONTRACT + '|' + energy + '|'
                + slotsCanonical(inputs) + '|' + slotsCanonical(outputs);
        result.canonicals.add(canonical);
        result.fluidInputAlternatives += fluidInput.alternatives.size();
        result.outputAlternatives += fluidOutput.alternatives.size();
        result.totalEnergy += energy;
        for (Integer amount : amounts) result.totalInputFluid += amount.intValue();
        for (Integer amount : outputAmounts) result.totalOutputFluid += amount.intValue();
        return new CompleteCategoryAdapters.RecipeSemanticOverride(
                "enderio-vat:" + Naming.sha256(canonical), inputs, outputs);
    }

    private static CompleteCategoryAdapters.SemanticSlot itemSlot(
            PositionedStack positioned, int page, int slotIndex) throws ExportFailure {
        if (positioned.items == null || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Ender IO Vat page #" + page + " slot #" + slotIndex
                            + " has no item alternatives");
        }
        List<CompleteCategoryAdapters.SemanticAlternative> alternatives =
                new ArrayList<CompleteCategoryAdapters.SemanticAlternative>();
        for (ItemStack original : positioned.items) {
            if (original == null || original.getItem() == null || original.stackSize != 1) {
                throw new ExportFailure("QUANTITY_INVALID",
                        "Ender IO Vat page #" + page + " slot #" + slotIndex
                                + " has an invalid item alternative");
            }
            ItemStack copy = original.copy();
            StackIdentity identity = StackIdentity.of(copy);
            alternatives.add(new CompleteCategoryAdapters.SemanticAlternative(
                    copy, 1, CompleteCategoryAdapters.canonicalStackIdentity(identity, 1)));
        }
        Collections.sort(alternatives, ALTERNATIVE_ORDER);
        return new CompleteCategoryAdapters.SemanticSlot(alternatives);
    }

    private static CompleteCategoryAdapters.SemanticSlot fluidSlot(
            FluidStack original, List<Integer> amounts, int page, String role)
            throws ExportFailure {
        if (amounts == null || amounts.isEmpty()) {
            throw new ExportFailure("QUANTITY_INVALID",
                    "Ender IO Vat page #" + page + " has no " + role
                            + " fluid quantities");
        }
        List<CompleteCategoryAdapters.SemanticAlternative> alternatives =
                new ArrayList<CompleteCategoryAdapters.SemanticAlternative>();
        for (Integer amount : amounts) {
            FluidStack copy = original.copy();
            copy.amount = amount.intValue();
            ItemStack proxy = GTUtility.getFluidDisplayStack(copy, true, true);
            if (proxy == null || proxy.getItem() == null) {
                throw new ExportFailure("ITEM_IDENTITY",
                        "Ender IO Vat page #" + page + " could not create " + role
                                + " fluid-display proxy");
            }
            StackIdentity identity = StackIdentity.of(proxy);
            String expectedKey = "fluid|fluid:" + FluidRegistry.getFluidName(copy);
            if (!identity.isFluid() || !expectedKey.equals(identity.key)
                    || identity.amount != copy.amount || identity.canonicalNbt != null) {
                throw new ExportFailure("ITEM_IDENTITY",
                        "Ender IO Vat page #" + page + ' ' + role
                                + " fluid proxy drifted");
            }
            alternatives.add(new CompleteCategoryAdapters.SemanticAlternative(
                    proxy, copy.amount,
                    CompleteCategoryAdapters.canonicalStackIdentity(identity, copy.amount)));
        }
        Collections.sort(alternatives, ALTERNATIVE_ORDER);
        return new CompleteCategoryAdapters.SemanticSlot(alternatives);
    }

    private static float multiplier(Float value) throws ExportFailure {
        float result = value == null ? 1F : value.floatValue();
        if (Float.isNaN(result) || Float.isInfinite(result) || result <= 0F) {
            throw new ExportFailure("QUANTITY_INVALID",
                    "Ender IO Vat multiplier is not finite and positive: " + result);
        }
        return result;
    }

    private static List<Integer> deduplicate(List<Integer> sorted) {
        List<Integer> result = new ArrayList<Integer>();
        Integer previous = null;
        for (Integer value : sorted) {
            if (!value.equals(previous)) result.add(value);
            previous = value;
        }
        return result;
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
                                            Class<?> fieldType) throws Exception {
        Field field = type.getDeclaredField(name);
        if (field.getType() != fieldType || !Modifier.isPrivate(field.getModifiers())
                || Modifier.isStatic(field.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + '.' + name + " field topology drifted");
        }
    }

    private static Field privateField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
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

    private static final Comparator<CompleteCategoryAdapters.SemanticAlternative>
            ALTERNATIVE_ORDER =
            new Comparator<CompleteCategoryAdapters.SemanticAlternative>() {
                @Override
                public int compare(CompleteCategoryAdapters.SemanticAlternative left,
                                   CompleteCategoryAdapters.SemanticAlternative right) {
                    return left.canonicalIdentity.compareTo(right.canonicalIdentity);
                }
            };

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
        int itemInputSlots;
        int itemInputAlternatives;
        int fluidInputAlternatives;
        int outputAlternatives;
        long totalInputFluid;
        long totalOutputFluid;
        long totalEnergy;

        Observation finish() {
            List<String> sorted = new ArrayList<String>(canonicals);
            Collections.sort(sorted);
            String vector = "pages=" + pages.size()
                    + ",itemInputSlots=" + itemInputSlots
                    + ",fluidInputSlots=" + pages.size()
                    + ",outputSlots=" + pages.size()
                    + ",itemInputAlternatives=" + itemInputAlternatives
                    + ",fluidInputAlternatives=" + fluidInputAlternatives
                    + ",outputAlternatives=" + outputAlternatives
                    + ",totalInputFluid=" + totalInputFluid
                    + ",totalOutputFluid=" + totalOutputFluid
                    + ",totalEnergy=" + totalEnergy;
            StringBuilder canonical = new StringBuilder(vector).append('\n')
                    .append(CONTRACT).append('\n');
            for (String row : sorted) canonical.append(row).append('\n');
            return new Observation(vector, Naming.sha256(canonical.toString()));
        }
    }
}
