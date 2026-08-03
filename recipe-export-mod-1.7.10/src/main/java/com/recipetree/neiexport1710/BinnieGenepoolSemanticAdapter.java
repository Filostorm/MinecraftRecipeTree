package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact fluid graph semantics for Binnie Genetics 2.5.24's Genepool. */
final class BinnieGenepoolSemanticAdapter {
    static final String HANDLER = "binnie.genetics.nei.GenepoolRecipeHandler";
    static final String OPERATION = "genetics.genepool";
    static final String CONTRACT =
            "gtnh-2.8.4-binnie-genetics-2.5.24-genepool-fluid-semantics-v1";
    static final int EXPECTED_PAGES = 12;
    static final String UNPROMOTED = "<unpromoted>";
    static final String EXPECTED_COUNT_VECTOR =
            "pages=12,inputSlots=36,outputSlots=12,itemAlternatives=2163,"
                    + "inputFluids=12,outputFluids=12";
    static final String EXPECTED_SHA256 =
            "0e08a310697a1eb021380fbbd52ee38de1db4d5c0f5e617b746126ed9e6d51ca";

    private static final String BASE_HANDLER = "binnie.core.nei.RecipeHandlerBase";
    private static final String CACHED = HANDLER + "$CachedGenepoolRecipe";
    private static final String POSITIONED_TANK = "binnie.core.nei.PositionedFluidTank";
    private static final Map<ICraftingHandler,
            List<CompleteCategoryAdapters.RecipeSemanticOverride>> SEMANTICS =
            new IdentityHashMap<ICraftingHandler,
                    List<CompleteCategoryAdapters.RecipeSemanticOverride>>();
    private static Observation observation;

    private BinnieGenepoolSemanticAdapter() {}

    static void validatePrototype(ICraftingHandler prototype) throws ExportFailure {
        try {
            requireExactClass(prototype, HANDLER);
            Class<?> superclass = prototype.getClass().getSuperclass();
            if (superclass == null || !BASE_HANDLER.equals(superclass.getName())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        HANDLER + " superclass drifted; expected " + BASE_HANDLER);
            }
            if (prototype.numRecipes() != 0 || !OPERATION.equals(
                    prototype.getOverlayIdentifier())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        HANDLER + " prototype shape/overlay drifted");
            }
            ClassLoader loader = prototype.getClass().getClassLoader();
            Class<?> cached = Class.forName(CACHED, false, loader);
            requirePublicField(cached, "tanks", List.class);
            requirePublicField(cached, "ingredients", List.class);
            requirePublicField(cached, "input", PositionedStack.class);
            requirePublicField(cached, "enzyme", PositionedStack.class);
            Class<?> tank = Class.forName(POSITIONED_TANK, false, loader);
            requirePublicField(tank, "tank", FluidTank.class);
            requirePublicField(tank, "position", Rectangle.class);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    HANDLER + " exact Binnie structural validation failed", error);
        }
    }

    static ICraftingHandler loadCompleteCategory(ICraftingHandler prototype)
            throws ExportFailure {
        validatePrototype(prototype);
        try {
            ICraftingHandler loaded = prototype.getRecipeHandler(OPERATION);
            requireExactClass(loaded, HANDLER);
            if (!(loaded instanceof TemplateRecipeHandler)
                    || loaded.numRecipes() != EXPECTED_PAGES) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        HANDLER + " page cardinality drifted; expected "
                                + EXPECTED_PAGES + ", got " + loaded.numRecipes());
            }
            TemplateRecipeHandler target = (TemplateRecipeHandler) loaded;
            List<CompleteCategoryAdapters.RecipeSemanticOverride> pages =
                    new ArrayList<CompleteCategoryAdapters.RecipeSemanticOverride>();
            List<String> canonicals = new ArrayList<String>();
            int inputSlots = 0;
            int outputSlots = 0;
            int itemAlternatives = 0;
            List<String> pageFacts = new ArrayList<String>();
            for (int index = 0; index < EXPECTED_PAGES; index++) {
                Object cached = target.arecipes.get(index);
                if (cached == null || !CACHED.equals(cached.getClass().getName())) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            HANDLER + " cached page #" + index + " class drifted");
                }
                @SuppressWarnings("unchecked")
                List<PositionedStack> items = (List<PositionedStack>)
                        cached.getClass().getField("ingredients").get(cached);
                if (items == null || items.size() != 2
                        || items.get(0) != cached.getClass().getField("enzyme").get(cached)
                        || items.get(1) != cached.getClass().getField("input").get(cached)) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            HANDLER + " cached item topology drifted at page " + index);
                }
                @SuppressWarnings("unchecked")
                List<Object> tanks = (List<Object>)
                        cached.getClass().getField("tanks").get(cached);
                if (tanks == null || tanks.size() != 2) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            HANDLER + " cached tank cardinality drifted at page " + index);
                }
                List<CompleteCategoryAdapters.SemanticSlot> inputs =
                        new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
                for (int item = 0; item < items.size(); item++) {
                    CompleteCategoryAdapters.SemanticSlot slot = itemSlot(
                            items.get(item), "page " + index + " item " + item);
                    inputs.add(slot);
                    itemAlternatives += slot.alternatives.size();
                }
                inputs.add(fluidSlot(tanks.get(0), new Rectangle(38, 6, 16, 58),
                        "page " + index + " ethanol input"));
                List<CompleteCategoryAdapters.SemanticSlot> outputs =
                        Collections.singletonList(fluidSlot(
                                tanks.get(1), new Rectangle(119, 6, 16, 58),
                                "page " + index + " raw-DNA output"));
                inputSlots += inputs.size();
                outputSlots += outputs.size();
                String canonical = canonicalPage(inputs, outputs);
                canonicals.add(canonical);
                pageFacts.add(index + ":" + items.get(0).items.length + "+"
                        + items.get(1).items.length + ":" + sha256(canonical));
                pages.add(new CompleteCategoryAdapters.RecipeSemanticOverride(
                        "binnie-genepool:" + sha256(canonical), inputs, outputs,
                        Collections.<CompleteCategoryAdapters.SemanticSlot>emptyList()));
            }
            String countVector = "pages=" + pages.size() + ",inputSlots=" + inputSlots
                    + ",outputSlots=" + outputSlots + ",itemAlternatives="
                    + itemAlternatives + ",inputFluids=" + pages.size()
                    + ",outputFluids=" + pages.size();
            String fingerprint = sha256(join(canonicals));
            String diagnostic = join(pageFacts);
            Observation current = new Observation(countVector, fingerprint, diagnostic);
            synchronized (BinnieGenepoolSemanticAdapter.class) {
                if (observation != null && (!observation.countVector.equals(countVector)
                        || !observation.fingerprint.equals(fingerprint))) {
                    throw new ExportFailure("HANDLER_UNLOADED",
                            HANDLER + " corpus changed across captures in one boot; first="
                                    + observation.diagnostic + ", second=" + diagnostic);
                }
                observation = current;
                SEMANTICS.put(loaded, Collections.unmodifiableList(pages));
            }
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Binnie Genepool semantic adapter ready: "
                            + "countVector={}, fingerprint={}, pageFacts={}, contract={}",
                    countVector, fingerprint, diagnostic, CONTRACT);
            return loaded;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    HANDLER + " exact Binnie semantic adapter failed", error);
        }
    }

    static synchronized CompleteCategoryAdapters.RecipeSemanticOverride semanticOverride(
            ICraftingHandler loaded, int index) throws ExportFailure {
        requireExactClass(loaded, HANDLER);
        List<CompleteCategoryAdapters.RecipeSemanticOverride> pages = SEMANTICS.get(loaded);
        if (pages == null || index < 0 || index >= pages.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    HANDLER + " has no attached semantic page " + index);
        }
        return pages.get(index);
    }

    static synchronized Observation requirePromotedCorpus() throws ExportFailure {
        if (observation == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Binnie Genepool corpus was not captured before validation");
        }
        if (UNPROMOTED.equals(EXPECTED_COUNT_VECTOR)
                || UNPROMOTED.equals(EXPECTED_SHA256)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Binnie Genepool semantic corpus is unpromoted; observed countVector="
                            + observation.countVector + ", sha256=" + observation.fingerprint);
        }
        if (!EXPECTED_COUNT_VECTOR.equals(observation.countVector)
                || !EXPECTED_SHA256.equals(observation.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Binnie Genepool semantic corpus drifted; expected="
                            + EXPECTED_COUNT_VECTOR + '/' + EXPECTED_SHA256 + ", observed="
                            + observation.countVector + '/' + observation.fingerprint
                            + ", pageFacts=" + observation.diagnostic);
        }
        return observation;
    }

    private static CompleteCategoryAdapters.SemanticSlot itemSlot(
            PositionedStack positioned, String label) throws ExportFailure {
        if (positioned == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", label + " is null");
        }
        positioned.generatePermutations();
        if (positioned.items == null || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS", label + " has no alternatives");
        }
        List<CompleteCategoryAdapters.SemanticAlternative> alternatives =
                new ArrayList<CompleteCategoryAdapters.SemanticAlternative>();
        Set<String> seen = new HashSet<String>();
        for (ItemStack original : positioned.items) {
            if (original == null || original.getItem() == null || original.stackSize <= 0) {
                throw new ExportFailure("QUANTITY_INVALID", label + " has invalid item");
            }
            ItemStack copy = original.copy();
            StackIdentity identity = StackIdentity.of(copy);
            String canonical = CompleteCategoryAdapters.canonicalStackIdentity(
                    identity, copy.stackSize);
            if (seen.add(canonical)) {
                alternatives.add(new CompleteCategoryAdapters.SemanticAlternative(
                        copy, copy.stackSize, canonical));
            }
        }
        Collections.sort(alternatives,
                new Comparator<CompleteCategoryAdapters.SemanticAlternative>() {
                    @Override
                    public int compare(CompleteCategoryAdapters.SemanticAlternative a,
                                       CompleteCategoryAdapters.SemanticAlternative b) {
                        return a.canonicalIdentity.compareTo(b.canonicalIdentity);
                    }
                });
        return new CompleteCategoryAdapters.SemanticSlot(alternatives);
    }

    private static CompleteCategoryAdapters.SemanticSlot fluidSlot(
            Object positioned, Rectangle expected, String label) throws Exception {
        if (positioned == null || !POSITIONED_TANK.equals(positioned.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label + " tank class drifted");
        }
        Rectangle actual = (Rectangle) positioned.getClass().getField("position").get(positioned);
        if (!expected.equals(actual)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " position drifted; expected=" + expected + ", got=" + actual);
        }
        FluidTank tank = (FluidTank) positioned.getClass().getField("tank").get(positioned);
        FluidStack fluid = tank == null ? null : tank.getFluid();
        if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0
                || (fluid.tag != null && !fluid.tag.hasNoTags())) {
            throw new ExportFailure("QUANTITY_INVALID", label + " fluid is invalid");
        }
        FluidStack copy = fluid.copy();
        ItemStack proxy = GTUtility.getFluidDisplayStack(copy, true, true);
        if (proxy == null || proxy.getItem() == null) {
            throw new ExportFailure("ITEM_IDENTITY", label + " has no fluid proxy");
        }
        StackIdentity identity = StackIdentity.of(proxy);
        String expectedKey = "fluid|fluid:" + FluidRegistry.getFluidName(copy);
        if (!identity.isFluid() || !expectedKey.equals(identity.key)
                || identity.amount != copy.amount || identity.canonicalNbt != null) {
            throw new ExportFailure("ITEM_IDENTITY", label + " fluid identity drifted");
        }
        CompleteCategoryAdapters.SemanticAlternative alternative =
                new CompleteCategoryAdapters.SemanticAlternative(
                        proxy, copy.amount,
                        CompleteCategoryAdapters.canonicalStackIdentity(
                                identity, copy.amount));
        return new CompleteCategoryAdapters.SemanticSlot(
                Collections.singletonList(alternative));
    }

    private static String canonicalPage(
            List<CompleteCategoryAdapters.SemanticSlot> inputs,
            List<CompleteCategoryAdapters.SemanticSlot> outputs) {
        StringBuilder value = new StringBuilder(CONTRACT).append(';');
        appendSlots(value, 'I', inputs);
        appendSlots(value, 'O', outputs);
        return value.toString();
    }

    private static void appendSlots(StringBuilder value, char role,
                                    List<CompleteCategoryAdapters.SemanticSlot> slots) {
        value.append(role).append(slots.size()).append(';');
        for (CompleteCategoryAdapters.SemanticSlot slot : slots) {
            for (CompleteCategoryAdapters.SemanticAlternative alternative
                    : slot.alternatives) {
                value.append(alternative.canonicalIdentity.length()).append(':')
                        .append(alternative.canonicalIdentity);
            }
            value.append(';');
        }
    }

    private static String join(List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            joined.append(value.length()).append(':').append(value);
        }
        return joined.toString();
    }

    private static String sha256(String value) throws ExportFailure {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte item : bytes) hex.append(String.format("%02x", item & 0xff));
            return hex.toString();
        } catch (Exception error) {
            throw new ExportFailure("RECIPE_SEMANTICS", "SHA-256 unavailable", error);
        }
    }

    private static void requireExactClass(Object value, String expected)
            throws ExportFailure {
        if (value == null || !expected.equals(value.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", expected + " class drifted");
        }
    }

    private static void requirePublicField(Class<?> owner, String name, Class<?> type)
            throws Exception {
        Field field = owner.getField(name);
        if (field.getType() != type) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    owner.getName() + '.' + name + " type drifted");
        }
    }

    static final class Observation {
        final String countVector;
        final String fingerprint;
        final String diagnostic;

        Observation(String countVector, String fingerprint, String diagnostic) {
            this.countVector = countVector;
            this.fingerprint = fingerprint;
            this.diagnostic = diagnostic;
        }
    }
}
