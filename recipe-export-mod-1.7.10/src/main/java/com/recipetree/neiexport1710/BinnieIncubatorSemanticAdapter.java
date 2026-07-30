package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact Binnie Genetics 2.5.24 Incubator graph adapter for GTNH 2.8.4.
 *
 * <p>The Binnie cached page exposes items through NEI's ordinary APIs but keeps both
 * authoritative fluids in {@code PositionedFluidTank}. It also labels the probability that
 * the required incubator item is lost. This adapter binds every rendered cache page to the
 * same-index {@code Incubator.RECIPES} source, exports the fluids, and models item loss as a
 * consumed input plus a probabilistic returned-item output. {@code getChance()} controls
 * process timing and therefore is audited but deliberately does not alter recipe yield.
 */
final class BinnieIncubatorSemanticAdapter {
    static final String HANDLER = "binnie.genetics.nei.IncubatorRecipeHandler";
    static final String CONTRACT =
            "gtnh-2.8.4-binnie-genetics-2.5.24-incubator-fluid-semantics-v1";
    static final String OPERATION = "genetics.incubator";
    static final String UNPROMOTED = "<unpromoted>";
    static final int EXPECTED_PAGES = 160;
    static final String EXPECTED_COUNT_VECTOR =
            "pages=160,inputFluids=160,zeroInputFluids=3,outputFluids=7,"
                    + "inputItems=160,outputItems=153,catalystItems=0,"
                    + "probabilisticReturns=8";
    static final String EXPECTED_SHA256 =
            "d778d372e59279cc3358c8dd9126839c3ef575ae26769c4fcf41558c59cc8bcb";

    private static final String BASE_HANDLER = "binnie.core.nei.RecipeHandlerBase";
    private static final String CACHED = HANDLER + "$CachedIncubatorRecipe";
    private static final String ZERO_INPUT_TANK = CACHED + "$1";
    private static final String API = "binnie.genetics.api.IIncubatorRecipe";
    private static final String INCUBATOR =
            "binnie.genetics.machine.incubator.Incubator";
    private static final String POSITIONED_TANK = "binnie.core.nei.PositionedFluidTank";

    private static final Map<ICraftingHandler,
            List<CompleteCategoryAdapters.RecipeSemanticOverride>> SEMANTICS =
            new IdentityHashMap<ICraftingHandler,
                    List<CompleteCategoryAdapters.RecipeSemanticOverride>>();
    private static Observation observation;

    private BinnieIncubatorSemanticAdapter() {}

    static void validatePrototype(ICraftingHandler prototype) throws ExportFailure {
        try {
            requireExactClass(prototype, HANDLER);
            Class<?> superclass = prototype.getClass().getSuperclass();
            if (superclass == null || !BASE_HANDLER.equals(superclass.getName())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        HANDLER + " superclass drifted; expected " + BASE_HANDLER);
            }
            if (prototype.numRecipes() != 0) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        HANDLER + " prototype unexpectedly contains "
                                + prototype.numRecipes() + " pages");
            }
            if (!OPERATION.equals(prototype.getOverlayIdentifier())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        HANDLER + " overlay drifted; expected " + OPERATION);
            }
            ClassLoader loader = prototype.getClass().getClassLoader();
            Class<?> api = Class.forName(API, false, loader);
            requireMethod(api, "getInput", FluidStack.class);
            requireMethod(api, "getOutput", FluidStack.class);
            requireMethod(api, "getInputStack", ItemStack.class);
            requireMethod(api, "getExpectedOutput", ItemStack.class);
            requireMethod(api, "getChance", float.class);
            requireMethod(api, "getLossChance", float.class);
            Class<?> incubator = Class.forName(INCUBATOR, false, loader);
            Field recipes = incubator.getField("RECIPES");
            if (!Modifier.isStatic(recipes.getModifiers())
                    || !List.class.isAssignableFrom(recipes.getType())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        INCUBATOR + ".RECIPES topology drifted");
            }
        } catch (ExportFailure failure) {
            logFailure("prototype validation", failure);
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            ExportFailure failure = new ExportFailure("HANDLER_UNLOADED",
                    HANDLER + " exact Binnie structural validation failed", error);
            logFailure("prototype validation", failure);
            throw failure;
        }
    }

    static ICraftingHandler loadCompleteCategory(ICraftingHandler prototype)
            throws ExportFailure {
        validatePrototype(prototype);
        try {
            ICraftingHandler loaded = prototype.getRecipeHandler(OPERATION);
            requireExactClass(loaded, HANDLER);
            if (!(loaded instanceof TemplateRecipeHandler)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        HANDLER + " complete query is not a TemplateRecipeHandler");
            }
            TemplateRecipeHandler target = (TemplateRecipeHandler) loaded;
            if (target.numRecipes() != EXPECTED_PAGES) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        HANDLER + " page cardinality drifted; expected " + EXPECTED_PAGES
                                + ", got " + target.numRecipes());
            }

            List<?> sources = backingRecipes(target.getClass().getClassLoader());
            if (sources.size() != target.numRecipes()) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "Binnie Incubator source/cache cardinality diverged; sources="
                                + sources.size() + ", cached=" + target.numRecipes());
            }

            BuildResult result = new BuildResult();
            for (int index = 0; index < sources.size(); index++) {
                Object source = sources.get(index);
                Object cached = target.arecipes.get(index);
                result.pages.add(buildPage(source, cached, index, result));
            }
            Observation current = result.finish();
            synchronized (BinnieIncubatorSemanticAdapter.class) {
                if (observation != null
                        && (!observation.countVector.equals(current.countVector)
                        || !observation.fingerprint.equals(current.fingerprint))) {
                    throw new ExportFailure("HANDLER_UNLOADED",
                            "Binnie Incubator corpus changed across captures in one boot; first="
                                    + observation.countVector + '/' + observation.fingerprint
                                    + ", second=" + current.countVector + '/'
                                    + current.fingerprint);
                }
                observation = current;
                SEMANTICS.put(target, Collections.unmodifiableList(new ArrayList<
                        CompleteCategoryAdapters.RecipeSemanticOverride>(result.pages)));
            }
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Binnie Incubator semantic adapter ready: "
                            + "countVector={}, fingerprint={}, contract={}",
                    current.countVector, current.fingerprint, CONTRACT);
            return target;
        } catch (ExportFailure failure) {
            logFailure("complete-category load", failure);
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            ExportFailure failure = new ExportFailure("HANDLER_UNLOADED",
                    HANDLER + " exact Binnie semantic adapter failed", error);
            logFailure("complete-category load", failure);
            throw failure;
        }
    }

    static synchronized CompleteCategoryAdapters.RecipeSemanticOverride semanticOverride(
            ICraftingHandler loaded, int recipeIndex) throws ExportFailure {
        requireExactClass(loaded, HANDLER);
        List<CompleteCategoryAdapters.RecipeSemanticOverride> pages = SEMANTICS.get(loaded);
        if (pages == null || pages.size() != loaded.numRecipes()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    HANDLER + " has no intact attached semantic corpus");
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
                    "Binnie Incubator corpus was not captured before promotion validation");
        }
        if (UNPROMOTED.equals(EXPECTED_COUNT_VECTOR)
                || UNPROMOTED.equals(EXPECTED_SHA256)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Binnie Incubator semantic corpus is unpromoted; observed countVector="
                            + observation.countVector + ", sha256=" + observation.fingerprint);
        }
        if (!EXPECTED_COUNT_VECTOR.equals(observation.countVector)
                || !EXPECTED_SHA256.equals(observation.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Binnie Incubator semantic corpus drifted; expected="
                            + EXPECTED_COUNT_VECTOR + '/' + EXPECTED_SHA256 + ", observed="
                            + observation.countVector + '/' + observation.fingerprint);
        }
        return observation;
    }

    private static CompleteCategoryAdapters.RecipeSemanticOverride buildPage(
            Object source, Object cached, int index, BuildResult result) throws Exception {
        if (source == null || !implementsInterface(source.getClass(), API)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Binnie Incubator source #" + index + " does not implement " + API);
        }
        if (cached == null || !CACHED.equals(cached.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Binnie Incubator cached page #" + index + " class drifted");
        }

        FluidStack inputFluid = (FluidStack) invoke(source, "getInput");
        FluidStack outputFluid = (FluidStack) invoke(source, "getOutput");
        ItemStack inputItem = (ItemStack) invoke(source, "getInputStack");
        ItemStack expectedItem = (ItemStack) invoke(source, "getExpectedOutput");
        float tickChance = ((Number) invoke(source, "getChance")).floatValue();
        float lossChance = ((Number) invoke(source, "getLossChance")).floatValue();
        requireProbability(tickChance, "tickChance", index, false);
        requireProbability(lossChance, "lossChance", index, true);

        auditCachedPage(cached, inputFluid, outputFluid, inputItem, expectedItem,
                lossChance, index);

        List<CompleteCategoryAdapters.SemanticSlot> inputs =
                new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
        List<CompleteCategoryAdapters.SemanticSlot> outputs =
                new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
        List<CompleteCategoryAdapters.SemanticSlot> catalysts =
                new ArrayList<CompleteCategoryAdapters.SemanticSlot>();

        if (inputFluid != null) {
            inputs.add(fluidSlot(inputFluid, "input fluid #" + index));
            result.inputFluids++;
            if (inputFluid.amount == 0) result.zeroInputFluids++;
        }
        if (inputItem != null) {
            CompleteCategoryAdapters.SemanticSlot item = itemSlot(inputItem,
                    "incubator item #" + index);
            if (lossChance == 0.0f) {
                catalysts.add(item);
                result.catalystItems++;
            } else {
                inputs.add(item);
                result.inputItems++;
                if (lossChance < 1.0f) {
                    outputs.add(probabilisticItemSlot(inputItem,
                            1.0d - lossChance, "returned incubator item #" + index));
                    result.probabilisticReturns++;
                }
            }
        }
        if (outputFluid != null) {
            outputs.add(fluidSlot(outputFluid, "output fluid #" + index));
            result.outputFluids++;
        }
        if (expectedItem != null) {
            outputs.add(itemSlot(expectedItem, "expected output item #" + index));
            result.outputItems++;
        }
        if (outputs.isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Binnie Incubator source #" + index + " has no semantic output");
        }

        String canonical = pageCanonical(source.getClass().getName(), tickChance,
                lossChance, inputs, outputs, catalysts);
        result.pageCanonicals.add(canonical);
        return new CompleteCategoryAdapters.RecipeSemanticOverride(
                "binnie-incubator:" + sha256(canonical), inputs, outputs, catalysts);
    }

    private static void auditCachedPage(Object cached, FluidStack inputFluid,
                                        FluidStack outputFluid, ItemStack inputItem,
                                        ItemStack expectedItem, float lossChance, int index)
            throws Exception {
        PositionedStack cachedInput = (PositionedStack) invoke(cached, "getIngredient");
        PositionedStack cachedOutput = (PositionedStack) invoke(cached, "getResult");
        requirePositionedIdentity(cachedInput, inputItem, "cached input", index);
        requirePositionedIdentity(cachedOutput, expectedItem, "cached output", index);

        Object rawTanks = invoke(cached, "getFluidTanks");
        if (!(rawTanks instanceof List<?>)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Binnie Incubator cached page #" + index + " tanks are not a List");
        }
        List<?> tanks = (List<?>) rawTanks;
        int expectedTanks = (inputFluid == null ? 0 : 1) + (outputFluid == null ? 0 : 1);
        if (tanks.size() != expectedTanks) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Binnie Incubator cached page #" + index + " tank count drifted; expected "
                            + expectedTanks + ", got " + tanks.size());
        }
        int cursor = 0;
        if (inputFluid != null) {
            FluidStack display = tankFluid(tanks.get(cursor++), index,
                    inputFluid.amount == 0);
            requireFluidIdentity(display, inputFluid,
                    inputFluid.amount == 0 ? 1 : inputFluid.amount,
                    "cached input tank", index);
        }
        if (outputFluid != null) {
            requireFluidIdentity(tankFluid(tanks.get(cursor), index, false), outputFluid,
                    outputFluid.amount, "cached output tank", index);
        }
        Field loss = cached.getClass().getField("lossChance");
        String expectedLoss = Float.toString(lossChance * 100.0f) + "%";
        if (!expectedLoss.equals(loss.get(cached))) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Binnie Incubator cached page #" + index + " loss label drifted");
        }
    }

    private static List<?> backingRecipes(ClassLoader loader) throws Exception {
        Class<?> incubator = Class.forName(INCUBATOR, false, loader);
        Object raw = incubator.getField("RECIPES").get(null);
        if (!(raw instanceof List<?>)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    INCUBATOR + ".RECIPES is not a live List");
        }
        return new ArrayList<Object>((List<?>) raw);
    }

    private static CompleteCategoryAdapters.SemanticSlot itemSlot(
            ItemStack original, String label) throws ExportFailure {
        if (original == null || original.getItem() == null || original.stackSize <= 0) {
            throw new ExportFailure("QUANTITY_INVALID", label + " is invalid");
        }
        ItemStack copy = original.copy();
        StackIdentity identity = StackIdentity.of(copy);
        CompleteCategoryAdapters.SemanticAlternative alternative =
                new CompleteCategoryAdapters.SemanticAlternative(copy, copy.stackSize,
                        CompleteCategoryAdapters.canonicalStackIdentity(
                                identity, copy.stackSize));
        return new CompleteCategoryAdapters.SemanticSlot(
                Collections.singletonList(alternative));
    }

    private static CompleteCategoryAdapters.SemanticSlot probabilisticItemSlot(
            ItemStack item, double probability, String label) throws ExportFailure {
        CompleteCategoryAdapters.SemanticSlot deterministic = itemSlot(item, label);
        return new CompleteCategoryAdapters.SemanticSlot(
                deterministic.alternatives, probability);
    }

    private static CompleteCategoryAdapters.SemanticSlot fluidSlot(
            FluidStack original, String label) throws ExportFailure {
        if (original == null || original.getFluid() == null || original.amount < 0) {
            throw new ExportFailure("QUANTITY_INVALID", label + " is invalid");
        }
        if (original.tag != null && !original.tag.hasNoTags()) {
            throw new ExportFailure("ITEM_IDENTITY",
                    label + " carries unsupported fluid NBT: " + original.tag);
        }
        FluidStack copy = original.copy();
        ItemStack proxy = GTUtility.getFluidDisplayStack(copy, true, true);
        if (proxy == null || proxy.getItem() == null) {
            throw new ExportFailure("ITEM_IDENTITY",
                    label + " could not create a GregTech fluid-display proxy");
        }
        StackIdentity identity = StackIdentity.of(proxy);
        String expectedKey = "fluid|fluid:" + FluidRegistry.getFluidName(copy);
        if (!identity.isFluid() || !expectedKey.equals(identity.key)
                || identity.amount != copy.amount || identity.canonicalNbt != null) {
            throw new ExportFailure("ITEM_IDENTITY",
                    label + " proxy identity drifted; expected " + expectedKey + '@'
                            + copy.amount + ", got " + identity.key + '@' + identity.amount);
        }
        CompleteCategoryAdapters.SemanticAlternative alternative =
                new CompleteCategoryAdapters.SemanticAlternative(proxy, copy.amount,
                        CompleteCategoryAdapters.canonicalStackIdentity(
                                identity, copy.amount));
        return new CompleteCategoryAdapters.SemanticSlot(
                Collections.singletonList(alternative));
    }

    private static void requirePositionedIdentity(PositionedStack positioned,
                                                   ItemStack expected, String label,
                                                   int index) throws ExportFailure {
        if (expected == null) {
            if (positioned != null) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "Binnie Incubator " + label + " #" + index
                                + " exists without a source item");
            }
            return;
        }
        if (positioned == null || positioned.items == null
                || positioned.items.length != 1 || positioned.items[0] == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Binnie Incubator " + label + " #" + index
                            + " is not one exact PositionedStack");
        }
        StackIdentity left = StackIdentity.of(positioned.items[0]);
        StackIdentity right = StackIdentity.of(expected);
        if (!left.sameLogicalIdentity(right) || left.amount != right.amount) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Binnie Incubator " + label + " #" + index
                            + " diverges from its source item");
        }
    }

    private static FluidStack tankFluid(Object positioned, int index,
                                        boolean zeroInputProxy) throws Exception {
        String expectedClass = zeroInputProxy ? ZERO_INPUT_TANK : POSITIONED_TANK;
        if (positioned == null || !expectedClass.equals(positioned.getClass().getName())
                || (zeroInputProxy
                && (positioned.getClass().getSuperclass() == null
                || !POSITIONED_TANK.equals(
                        positioned.getClass().getSuperclass().getName())))) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Binnie Incubator cached tank #" + index + " class drifted; expected "
                            + expectedClass);
        }
        Object raw = positioned.getClass().getField("tank").get(positioned);
        if (!(raw instanceof FluidTank)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Binnie Incubator cached tank #" + index + " topology drifted");
        }
        FluidStack fluid = ((FluidTank) raw).getFluid();
        if (fluid == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Binnie Incubator cached tank #" + index + " is empty");
        }
        return fluid;
    }

    private static void requireFluidIdentity(FluidStack actual, FluidStack expected,
                                             int expectedAmount, String label, int index)
            throws ExportFailure {
        String actualName = FluidRegistry.getFluidName(actual);
        String expectedName = FluidRegistry.getFluidName(expected);
        if (actual == null || expected == null || actual.getFluid() == null
                || expected.getFluid() == null || !expectedName.equals(actualName)
                || actual.amount != expectedAmount
                || (actual.tag != null && !actual.tag.hasNoTags())) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Binnie Incubator " + label + " #" + index
                            + " diverges from its source fluid");
        }
    }

    private static String pageCanonical(
            String sourceClass, float tickChance, float lossChance,
            List<CompleteCategoryAdapters.SemanticSlot> inputs,
            List<CompleteCategoryAdapters.SemanticSlot> outputs,
            List<CompleteCategoryAdapters.SemanticSlot> catalysts) {
        StringBuilder value = new StringBuilder(512);
        append(value, CONTRACT);
        append(value, sourceClass);
        value.append(Float.floatToRawIntBits(tickChance)).append(';')
                .append(Float.floatToRawIntBits(lossChance)).append(';');
        appendSlots(value, 'I', inputs);
        appendSlots(value, 'O', outputs);
        appendSlots(value, 'C', catalysts);
        return value.toString();
    }

    private static void appendSlots(StringBuilder value, char role,
                                    List<CompleteCategoryAdapters.SemanticSlot> slots) {
        value.append(role).append(slots.size()).append(';');
        for (CompleteCategoryAdapters.SemanticSlot slot : slots) {
            value.append(slot.probability == null ? "-" : Long.toString(
                    Double.doubleToLongBits(slot.probability.doubleValue()))).append(';');
            for (CompleteCategoryAdapters.SemanticAlternative alternative
                    : slot.alternatives) {
                append(value, alternative.canonicalIdentity);
            }
        }
    }

    private static void append(StringBuilder value, String field) {
        value.append(field.length()).append(':').append(field).append(';');
    }

    private static String sha256(String value) throws ExportFailure {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte part : digest) hex.append(String.format("%02x", part & 0xff));
            return hex.toString();
        } catch (Exception error) {
            throw new ExportFailure("INTERNAL_ERROR", "SHA-256 is unavailable", error);
        }
    }

    private static Object invoke(Object owner, String method) throws Exception {
        return owner.getClass().getMethod(method).invoke(owner);
    }

    private static void requireMethod(Class<?> owner, String name, Class<?> result,
                                      Class<?>... parameters) throws ExportFailure {
        try {
            Method method = owner.getMethod(name, parameters);
            if (method.getReturnType() != result) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        owner.getName() + '.' + name + " return type drifted");
            }
        } catch (NoSuchMethodException error) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    owner.getName() + '.' + name + " is missing", error);
        }
    }

    private static boolean implementsInterface(Class<?> type, String expected) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> candidate : current.getInterfaces()) {
                if (expected.equals(candidate.getName())) return true;
            }
        }
        return false;
    }

    private static void requireExactClass(Object value, String expected)
            throws ExportFailure {
        if (value == null || !expected.equals(value.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "expected exact class " + expected + ", got "
                            + (value == null ? "null" : value.getClass().getName()));
        }
    }

    private static void requireProbability(float value, String label, int index,
                                           boolean allowZero) throws ExportFailure {
        if (!Float.isFinite(value) || value > 1.0f || (allowZero ? value < 0.0f
                : value <= 0.0f)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Binnie Incubator " + label + " #" + index
                            + " is outside its pinned probability domain: " + value);
        }
    }

    private static void logFailure(String phase, ExportFailure failure) {
        GtnhNeiExportMod.LOGGER.error(
                "[gtnh-nei-export] Binnie Incubator adapter {} failed: {}: {}",
                phase, failure.code, failure.getMessage(), failure);
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
        final List<String> pageCanonicals = new ArrayList<String>();
        int inputFluids;
        int zeroInputFluids;
        int outputFluids;
        int inputItems;
        int outputItems;
        int catalystItems;
        int probabilisticReturns;

        Observation finish() throws ExportFailure {
            String countVector = "pages=" + pages.size()
                    + ",inputFluids=" + inputFluids
                    + ",zeroInputFluids=" + zeroInputFluids
                    + ",outputFluids=" + outputFluids
                    + ",inputItems=" + inputItems
                    + ",outputItems=" + outputItems
                    + ",catalystItems=" + catalystItems
                    + ",probabilisticReturns=" + probabilisticReturns;
            List<String> stable = new ArrayList<String>(pageCanonicals);
            Collections.sort(stable, Comparator.naturalOrder());
            StringBuilder basis = new StringBuilder(stable.size() * 512);
            append(basis, CONTRACT);
            append(basis, countVector);
            for (String page : stable) append(basis, page);
            return new Observation(countVector, sha256(basis.toString()));
        }
    }
}
