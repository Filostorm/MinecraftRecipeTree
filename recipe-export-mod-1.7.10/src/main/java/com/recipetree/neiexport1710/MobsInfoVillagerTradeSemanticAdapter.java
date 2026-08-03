package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact informational semantics for MobsInfo 0.5.6's grouped villager-trade pages. */
final class MobsInfoVillagerTradeSemanticAdapter {
    static final String HANDLER = "com.kuba6000.mobsinfo.nei.VillagerTradesHandler";
    static final String CACHED = HANDLER + "$VillagerCachedRecipe";
    static final String POSITIONED = CACHED + "$PositionedTradeItem";
    static final String TRADE = "com.kuba6000.mobsinfo.api.VillagerTrade";
    static final String TRADE_ITEM = TRADE + "$TradeItem";
    static final String OPERATION = "mobsinfo.villagertradeshandler";
    static final String CONTRACT =
            "gtnh-2.8.4-mobsinfo-0.5.6-villager-trade-information-v2";
    static final String UNPROMOTED = "<unpromoted>";
    private static final double PROBABILITY_BOUNDARY_EPSILON = 1.0e-12d;
    static final int EXPECTED_PAGES = 15;
    static final String EXPECTED_COUNT_VECTOR =
            "sourcePages=15,exportedPages=13,excludedBlankPages=2,"
                    + "blankPageFingerprint=dc75d7d630a88f135eb168430bf96bfb6a79e279f3232b7edc41ac30b26ba779,"
                    + "trades=489,inputSlots=512,outputSlots=489,alternatives=1713,"
                    + "secondInputs=23,variableSizeItems=184,enchantableItems=9,"
                    + "excludedNonPositiveSizes=4";
    static final String EXPECTED_SHA256 =
            "5907f485ea0154f5508c527d2f0115bac86cc3ebc853294d8f66330fe1f8e1e9";

    private static final Map<ICraftingHandler,
            List<CompleteCategoryAdapters.RecipeSemanticOverride>> SEMANTICS =
            new IdentityHashMap<ICraftingHandler,
                    List<CompleteCategoryAdapters.RecipeSemanticOverride>>();
    private static Observation observation;

    private MobsInfoVillagerTradeSemanticAdapter() {}

    static void validatePrototype(ICraftingHandler prototype) throws ExportFailure {
        try {
            requireExactClass(prototype, HANDLER);
            if (!(prototype instanceof TemplateRecipeHandler)
                    || prototype.numRecipes() != 0
                    || !OPERATION.equals(prototype.getOverlayIdentifier())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        HANDLER + " prototype/overlay topology drifted");
            }
            ClassLoader loader = prototype.getClass().getClassLoader();
            Class<?> cached = Class.forName(CACHED, false, loader);
            requirePrivateFinalField(cached, "tradeList", ArrayList.class);
            requirePrivateFinalField(cached, "mInputs", ArrayList.class);
            requirePrivateFinalField(cached, "mOutputs", ArrayList.class);
            requirePrivateFinalField(cached, "professionID", int.class);
            requirePrivateFinalField(cached, "profession", String.class);
            requirePrivateFinalField(cached, "mod", String.class);
            Class<?> positioned = Class.forName(POSITIONED, false, loader);
            requirePrivateFinalField(positioned, "tradeItem",
                    Class.forName(TRADE_ITEM, false, loader));
            Class<?> trade = Class.forName(TRADE, false, loader);
            requirePublicMethod(trade, "getChance", double.class);
            Class<?> tradeItem = Class.forName(TRADE_ITEM, false, loader);
            requirePublicField(tradeItem, "stack", ItemStack.class);
            requirePublicField(tradeItem, "possibleSizes", Set.class);
            requirePublicField(tradeItem, "enchantability", Integer.class);
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
            List<String> blankPageFacts = new ArrayList<String>();
            for (int page = target.arecipes.size() - 1; page >= 0; page--) {
                String blankFact = blankPageFact(target.arecipes.get(page));
                if (blankFact != null) {
                    target.arecipes.remove(page);
                    blankPageFacts.add(blankFact);
                }
            }
            Collections.sort(blankPageFacts);
            int excludedBlankPages = blankPageFacts.size();
            if (excludedBlankPages == 0
                    || target.numRecipes() != EXPECTED_PAGES - excludedBlankPages) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        HANDLER + " blank-page filtering topology drifted; excluded="
                                + excludedBlankPages + ", exported=" + target.numRecipes());
            }
            String blankPageFingerprint = Naming.sha256(join(blankPageFacts));
            List<CompleteCategoryAdapters.RecipeSemanticOverride> pages =
                    new ArrayList<CompleteCategoryAdapters.RecipeSemanticOverride>();
            List<String> canonicals = new ArrayList<String>();
            int trades = 0;
            int inputSlots = 0;
            int outputSlots = 0;
            int alternatives = 0;
            int secondInputs = 0;
            int variableSizeItems = 0;
            int enchantableItems = 0;
            int excludedNonPositiveSizes = 0;
            for (int page = 0; page < target.numRecipes(); page++) {
                Page built = buildPage(target.arecipes.get(page), page);
                pages.add(built.semantic);
                canonicals.add(built.canonical);
                trades += built.trades;
                inputSlots += built.inputSlots;
                outputSlots += built.outputSlots;
                alternatives += built.alternatives;
                secondInputs += built.secondInputs;
                variableSizeItems += built.variableSizeItems;
                enchantableItems += built.enchantableItems;
                excludedNonPositiveSizes += built.excludedNonPositiveSizes;
            }
            Collections.sort(canonicals);
            String vector = "sourcePages=" + EXPECTED_PAGES + ",exportedPages="
                    + pages.size() + ",excludedBlankPages=" + excludedBlankPages
                    + ",blankPageFingerprint=" + blankPageFingerprint
                    + ",trades=" + trades
                    + ",inputSlots=" + inputSlots + ",outputSlots=" + outputSlots
                    + ",alternatives=" + alternatives + ",secondInputs=" + secondInputs
                    + ",variableSizeItems=" + variableSizeItems
                    + ",enchantableItems=" + enchantableItems
                    + ",excludedNonPositiveSizes=" + excludedNonPositiveSizes;
            String fingerprint = Naming.sha256(vector + '\n' + join(canonicals));
            Observation current = new Observation(vector, fingerprint, outputSlots);
            synchronized (MobsInfoVillagerTradeSemanticAdapter.class) {
                if (observation != null
                        && (!observation.countVector.equals(vector)
                        || !observation.fingerprint.equals(fingerprint))) {
                    throw new ExportFailure("HANDLER_UNLOADED",
                            "MobsInfo villager corpus changed across captures in one boot");
                }
                observation = current;
                SEMANTICS.put(target, Collections.unmodifiableList(pages));
            }
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] MobsInfo villager informational adapter ready: "
                            + "countVector={}, fingerprint={}, contract={}",
                    vector, fingerprint, CONTRACT);
            if (excludedNonPositiveSizes > 0) {
                GtnhNeiExportMod.LOGGER.warn(
                        "[gtnh-nei-export] MobsInfo villager adapter excluded {} pinned "
                                + "non-positive source quantities while retaining their "
                                + "source values in the corpus fingerprint",
                        excludedNonPositiveSizes);
            }
            return target;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    HANDLER + " exact informational adapter failed", error);
        }
    }

    static synchronized CompleteCategoryAdapters.RecipeSemanticOverride semanticOverride(
            ICraftingHandler loaded, int recipeIndex) throws ExportFailure {
        requireExactClass(loaded, HANDLER);
        List<CompleteCategoryAdapters.RecipeSemanticOverride> pages = SEMANTICS.get(loaded);
        if (pages == null || pages.size() != loaded.numRecipes()
                || recipeIndex < 0 || recipeIndex >= pages.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    HANDLER + " has no intact semantic page " + recipeIndex);
        }
        return pages.get(recipeIndex);
    }

    static synchronized int drawDeterministicPreview(
            ICraftingHandler loaded, int recipeIndex, int width, int height)
            throws ExportFailure {
        CompleteCategoryAdapters.RecipeSemanticOverride semantic =
                semanticOverride(loaded, recipeIndex);
        if (semantic.outputs.isEmpty() || width <= 0 || height <= 0) {
            throw new ExportFailure("RECIPE_WIDGET_RENDER",
                    HANDLER + " deterministic preview topology drifted");
        }
        Gui.drawRect(1, 1, Math.max(2, width - 1), Math.max(2, height - 1),
                0xffd8d8d8);
        Minecraft.getMinecraft().fontRenderer.drawString(
                "Villager Trades", 6, 5, 0xff303030, false);
        int columns = Math.max(1, ((width - 12) * 2) / 18);
        int rows = Math.max(1, ((height - 31) * 2) / 18);
        if (semantic.outputs.size() > Math.multiplyExact(columns, rows)) {
            throw new ExportFailure("RECIPE_WIDGET_RENDER",
                    HANDLER + " preview cannot fit " + semantic.outputs.size()
                            + " outputs in " + columns + "x" + rows);
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(6.0F, 17.0F, 0.0F);
            GL11.glScalef(0.5F, 0.5F, 1.0F);
            for (int index = 0; index < semantic.outputs.size(); index++) {
                CompleteCategoryAdapters.SemanticSlot slot = semantic.outputs.get(index);
                GuiContainerManager.drawItem(
                        (index % columns) * 18, (index / columns) * 18,
                        slot.alternatives.get(0).stack.copy());
            }
        } finally {
            GL11.glPopMatrix();
        }
        Minecraft.getMinecraft().fontRenderer.drawString(
                semantic.outputs.size() + " grouped trade outputs", 6,
                Math.max(17, height - 11), 0xff505050, false);
        return semantic.outputs.size();
    }

    static synchronized Observation requirePromotedCorpus() throws ExportFailure {
        if (observation == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "MobsInfo villager corpus was not captured before validation");
        }
        if (UNPROMOTED.equals(EXPECTED_COUNT_VECTOR)
                || UNPROMOTED.equals(EXPECTED_SHA256)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "MobsInfo villager corpus is unpromoted; observed countVector="
                            + observation.countVector + ", sha256=" + observation.fingerprint);
        }
        if (!EXPECTED_COUNT_VECTOR.equals(observation.countVector)
                || !EXPECTED_SHA256.equals(observation.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "MobsInfo villager corpus drifted; expected=" + EXPECTED_COUNT_VECTOR
                            + '/' + EXPECTED_SHA256 + ", observed="
                            + observation.countVector + '/' + observation.fingerprint);
        }
        return observation;
    }

    private static Page buildPage(Object cached, int page) throws Exception {
        if (cached == null || !CACHED.equals(cached.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    HANDLER + " cached page #" + page + " class drifted");
        }
        Class<?> type = cached.getClass();
        @SuppressWarnings("unchecked")
        List<Object> tradeList = (List<Object>) field(type, "tradeList").get(cached);
        @SuppressWarnings("unchecked")
        List<PositionedStack> rawInputs =
                (List<PositionedStack>) field(type, "mInputs").get(cached);
        @SuppressWarnings("unchecked")
        List<PositionedStack> rawOutputs =
                (List<PositionedStack>) field(type, "mOutputs").get(cached);
        int professionId = field(type, "professionID").getInt(cached);
        String profession = (String) field(type, "profession").get(cached);
        String mod = (String) field(type, "mod").get(cached);
        if (tradeList == null || tradeList.isEmpty() || rawInputs == null
                || rawOutputs == null || rawOutputs.size() != tradeList.size()
                || profession == null || mod == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    HANDLER + " page #" + page + " grouped topology drifted; "
                            + "professionId=" + professionId + ", profession=" + profession
                            + ", mod=" + mod + ", trades="
                            + (tradeList == null ? -1 : tradeList.size()) + ", rawInputs="
                            + (rawInputs == null ? -1 : rawInputs.size()) + ", rawOutputs="
                            + (rawOutputs == null ? -1 : rawOutputs.size()));
        }
        List<CompleteCategoryAdapters.SemanticSlot> inputs =
                new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
        List<CompleteCategoryAdapters.SemanticSlot> outputs =
                new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
        List<String> tradeRows = new ArrayList<String>();
        int secondInputs = 0;
        int variable = 0;
        int enchantable = 0;
        int alternatives = 0;
        int excludedNonPositiveSizes = 0;
        for (int index = 0; index < tradeList.size(); index++) {
            Object outer = tradeList.get(index);
            Object combined = pairLeft(outer);
            Object inputPair = pairLeft(combined);
            PositionedStack first = (PositionedStack) pairLeft(inputPair);
            PositionedStack second = (PositionedStack) pairRight(inputPair);
            PositionedStack output = (PositionedStack) pairRight(combined);
            Object trade = pairRight(outer);
            Method getChance = trade.getClass().getMethod("getChance");
            double rawChance = ((Number) getChance.invoke(trade)).doubleValue();
            double chance = normalizeProbability(rawChance);
            if (!Double.isFinite(chance) || chance <= 0.0d || chance > 1.0d) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        HANDLER + " page #" + page + " trade #" + index
                                + " chance drifted: " + rawChance);
            }
            SlotBuilt firstBuilt = slot(first, page, index, "first-input");
            SlotBuilt outputBuilt = slot(output, page, index, "output");
            inputs.add(firstBuilt.slot);
            outputs.add(outputBuilt.slot);
            alternatives += firstBuilt.alternatives + outputBuilt.alternatives;
            variable += firstBuilt.variable + outputBuilt.variable;
            enchantable += firstBuilt.enchantable + outputBuilt.enchantable;
            excludedNonPositiveSizes += firstBuilt.excludedNonPositiveSizes
                    + outputBuilt.excludedNonPositiveSizes;
            StringBuilder row = new StringBuilder(firstBuilt.canonical);
            if (second != null) {
                SlotBuilt secondBuilt = slot(second, page, index, "second-input");
                inputs.add(secondBuilt.slot);
                secondInputs++;
                alternatives += secondBuilt.alternatives;
                variable += secondBuilt.variable;
                enchantable += secondBuilt.enchantable;
                excludedNonPositiveSizes += secondBuilt.excludedNonPositiveSizes;
                row.append('+').append(secondBuilt.canonical);
            } else {
                row.append("+-");
            }
            row.append("->").append(outputBuilt.canonical).append('@').append(chance);
            tradeRows.add(row.toString());
        }
        if (rawInputs.size() != inputs.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    HANDLER + " page #" + page + " input cardinality drifted");
        }
        Collections.sort(tradeRows);
        StringBuilder canonical = new StringBuilder(CONTRACT).append('|')
                .append(professionId).append('|').append(mod).append('|')
                .append(profession).append('|');
        for (String row : tradeRows) canonical.append(row).append(';');
        return new Page(new CompleteCategoryAdapters.RecipeSemanticOverride(
                "mobsinfo-villager:" + Naming.sha256(
                        professionId + "|" + mod + "|" + profession),
                inputs, outputs), canonical.toString(), tradeList.size(), inputs.size(),
                outputs.size(), alternatives, secondInputs, variable, enchantable,
                excludedNonPositiveSizes);
    }

    private static String blankPageFact(Object cached) throws Exception {
        if (cached == null || !CACHED.equals(cached.getClass().getName())) return null;
        Class<?> type = cached.getClass();
        @SuppressWarnings("unchecked")
        List<Object> trades = (List<Object>) field(type, "tradeList").get(cached);
        @SuppressWarnings("unchecked")
        List<PositionedStack> inputs =
                (List<PositionedStack>) field(type, "mInputs").get(cached);
        @SuppressWarnings("unchecked")
        List<PositionedStack> outputs =
                (List<PositionedStack>) field(type, "mOutputs").get(cached);
        int professionId = field(type, "professionID").getInt(cached);
        String profession = (String) field(type, "profession").get(cached);
        String mod = (String) field(type, "mod").get(cached);
        boolean blank = trades != null && trades.isEmpty()
                && inputs != null && inputs.isEmpty()
                && outputs != null && outputs.isEmpty();
        if (!blank) return null;
        if (profession == null || profession.isEmpty() || mod == null || mod.isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    HANDLER + " blank profession page has incomplete identity: id="
                            + professionId + ", profession=" + profession + ", mod=" + mod);
        }
        return professionId + "|" + mod + "|" + profession;
    }

    static double normalizeProbability(double value) {
        if (value > 1.0d && value <= 1.0d + PROBABILITY_BOUNDARY_EPSILON) {
            return 1.0d;
        }
        return value;
    }

    private static SlotBuilt slot(PositionedStack positioned, int page, int trade,
                                  String role) throws Exception {
        if (positioned == null || !POSITIONED.equals(positioned.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    HANDLER + " page #" + page + " trade #" + trade + ' ' + role
                            + " class drifted");
        }
        Object tradeItem = field(positioned.getClass(), "tradeItem").get(positioned);
        ItemStack base = (ItemStack) tradeItem.getClass().getField("stack").get(tradeItem);
        @SuppressWarnings("unchecked")
        Set<Integer> possible =
                (Set<Integer>) tradeItem.getClass().getField("possibleSizes").get(tradeItem);
        Integer enchantability =
                (Integer) tradeItem.getClass().getField("enchantability").get(tradeItem);
        if (base == null || base.getItem() == null || base.stackSize <= 0) {
            throw new ExportFailure("QUANTITY_INVALID",
                    HANDLER + " page #" + page + " trade #" + trade + ' ' + role
                            + " has invalid base stack");
        }
        List<Integer> sizes = new ArrayList<Integer>();
        if (possible == null) {
            sizes.add(Integer.valueOf(base.stackSize));
        } else {
            sizes.addAll(possible);
            Collections.sort(sizes);
        }
        if (sizes.isEmpty()) {
            throw new ExportFailure("QUANTITY_INVALID",
                    HANDLER + " page #" + page + " trade #" + trade + ' ' + role
                            + " has no possible sizes");
        }
        List<CompleteCategoryAdapters.SemanticAlternative> alternatives =
                new ArrayList<CompleteCategoryAdapters.SemanticAlternative>();
        List<String> identities = new ArrayList<String>();
        int excludedNonPositiveSizes = 0;
        for (Integer size : sizes) {
            if (size == null || size.intValue() <= 0) {
                if (page == 9 && "output".equals(role)
                        && base.stackSize == 1
                        && "[-1, 0, 1, 2, 3]".equals(sizes.toString())
                        && size != null) {
                    excludedNonPositiveSizes++;
                    continue;
                }
                throw new ExportFailure("QUANTITY_INVALID",
                        HANDLER + " page #" + page + " trade #" + trade + ' ' + role
                                + " has invalid possible size " + size
                                + "; baseSize=" + base.stackSize
                                + ", possibleSizes=" + sizes);
            }
            ItemStack copy = base.copy();
            copy.stackSize = size.intValue();
            StackIdentity identity = StackIdentity.of(copy);
            String canonical = CompleteCategoryAdapters.canonicalStackIdentity(
                    identity, size.intValue());
            alternatives.add(new CompleteCategoryAdapters.SemanticAlternative(
                    copy, size.intValue(), canonical));
            identities.add(canonical);
        }
        if (alternatives.isEmpty()) {
            throw new ExportFailure("QUANTITY_INVALID",
                    HANDLER + " page #" + page + " trade #" + trade + ' ' + role
                            + " has no positive executable size; source=" + sizes);
        }
        Collections.sort(alternatives,
                new Comparator<CompleteCategoryAdapters.SemanticAlternative>() {
                    @Override
                    public int compare(CompleteCategoryAdapters.SemanticAlternative left,
                                       CompleteCategoryAdapters.SemanticAlternative right) {
                        return left.canonicalIdentity.compareTo(right.canonicalIdentity);
                    }
                });
        Collections.sort(identities);
        StringBuilder canonical = new StringBuilder();
        for (String identity : identities) canonical.append(identity).append(',');
        canonical.append("ench=").append(enchantability == null ? "-" : enchantability)
                .append(",sourceSizes=").append(sizes);
        return new SlotBuilt(new CompleteCategoryAdapters.SemanticSlot(alternatives),
                canonical.toString(), alternatives.size(), possible == null ? 0 : 1,
                enchantability == null ? 0 : 1, excludedNonPositiveSizes);
    }

    private static Object pairLeft(Object pair) throws Exception {
        if (pair == null) throw new ExportFailure("RECIPE_SEMANTICS", "null trade pair");
        return pair.getClass().getMethod("getLeft").invoke(pair);
    }

    private static Object pairRight(Object pair) throws Exception {
        if (pair == null) throw new ExportFailure("RECIPE_SEMANTICS", "null trade pair");
        return pair.getClass().getMethod("getRight").invoke(pair);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void requireExactClass(Object value, String expected)
            throws ExportFailure {
        if (value == null || !expected.equals(value.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", expected + " runtime class drifted");
        }
    }

    private static void requirePrivateFinalField(Class<?> type, String name,
                                                 Class<?> expected) throws Exception {
        Field field = type.getDeclaredField(name);
        int modifiers = field.getModifiers();
        if (field.getType() != expected || !Modifier.isPrivate(modifiers)
                || !Modifier.isFinal(modifiers)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + '.' + name + " field contract drifted");
        }
    }

    private static void requirePublicField(Class<?> type, String name,
                                           Class<?> expected) throws Exception {
        Field field = type.getField(name);
        if (field.getType() != expected || !Modifier.isPublic(field.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + '.' + name + " public field contract drifted");
        }
    }

    private static void requirePublicMethod(Class<?> type, String name,
                                            Class<?> expected) throws Exception {
        Method method = type.getMethod(name);
        if (method.getReturnType() != expected || !Modifier.isPublic(method.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + '.' + name + " method contract drifted");
        }
    }

    private static String join(List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) joined.append(value).append('\n');
        return joined.toString();
    }

    static final class Observation {
        final String countVector;
        final String fingerprint;
        final int outputSlots;

        Observation(String countVector, String fingerprint, int outputSlots) {
            this.countVector = countVector;
            this.fingerprint = fingerprint;
            this.outputSlots = outputSlots;
        }
    }

    private static final class SlotBuilt {
        final CompleteCategoryAdapters.SemanticSlot slot;
        final String canonical;
        final int alternatives;
        final int variable;
        final int enchantable;
        final int excludedNonPositiveSizes;

        SlotBuilt(CompleteCategoryAdapters.SemanticSlot slot, String canonical,
                  int alternatives, int variable, int enchantable,
                  int excludedNonPositiveSizes) {
            this.slot = slot;
            this.canonical = canonical;
            this.alternatives = alternatives;
            this.variable = variable;
            this.enchantable = enchantable;
            this.excludedNonPositiveSizes = excludedNonPositiveSizes;
        }
    }

    private static final class Page {
        final CompleteCategoryAdapters.RecipeSemanticOverride semantic;
        final String canonical;
        final int trades;
        final int inputSlots;
        final int outputSlots;
        final int alternatives;
        final int secondInputs;
        final int variableSizeItems;
        final int enchantableItems;
        final int excludedNonPositiveSizes;

        Page(CompleteCategoryAdapters.RecipeSemanticOverride semantic, String canonical,
             int trades, int inputSlots, int outputSlots, int alternatives,
             int secondInputs, int variableSizeItems, int enchantableItems,
             int excludedNonPositiveSizes) {
            this.semantic = semantic;
            this.canonical = canonical;
            this.trades = trades;
            this.inputSlots = inputSlots;
            this.outputSlots = outputSlots;
            this.alternatives = alternatives;
            this.secondInputs = secondInputs;
            this.variableSizeItems = variableSizeItems;
            this.enchantableItems = enchantableItems;
            this.excludedNonPositiveSizes = excludedNonPositiveSizes;
        }
    }
}
