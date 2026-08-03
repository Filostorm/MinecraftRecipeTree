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
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Exact MobsInfo 0.5.6 informational-page adapter for GTNH 2.8.4.
 *
 * <p>MobsInfo deliberately exposes mob drops through {@code MobCachedRecipe.getOutputs()},
 * while NEI's generic result/other-stack methods are empty. Its ingredient is only a mob
 * selector (spawner, soul vial, and related representations), not a consumed material.
 * Consequently this category is exported as informational references and excluded from
 * executable material graphs. Exact pages containing neither a selector nor a drop are
 * explicitly removed because they carry no item-addressable information.
 */
final class MobsInfoSemanticAdapter {
    static final String HANDLER = "com.kuba6000.mobsinfo.nei.MobHandler";
    static final String CACHED = HANDLER + "$MobCachedRecipe";
    static final String OUTPUT = HANDLER + "$MobPositionedStack";
    static final String OPERATION = "mobsinfo.mobhandler";
    static final String CONTRACT =
            "gtnh-2.8.4-mobsinfo-0.5.6-item-reference-semantics-v2";
    static final String UNPROMOTED = "<unpromoted>";
    static final int EXPECTED_SOURCE_PAGES = 401;
    static final String EXPECTED_COUNT_VECTOR =
            "sourcePages=401,exportedPages=401,excludedBlankPages=0,inputPages=401,"
                    + "outputPages=401,emptyOutputPages=0,inputSlots=401,outputSlots=5692,"
                    + "alternatives=7025";
    static final String EXPECTED_SHA256 =
            "9ed2e0e28aa06e1dece8763d652996d8bb476f73f22be4cdc312acb8c473ae70";

    private static final Map<ICraftingHandler,
            List<CompleteCategoryAdapters.RecipeSemanticOverride>> SEMANTICS =
            new IdentityHashMap<ICraftingHandler,
                    List<CompleteCategoryAdapters.RecipeSemanticOverride>>();
    private static Observation observation;

    private MobsInfoSemanticAdapter() {}

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
            requirePublicField(cached, "mOutputs", List.class);
            requirePublicField(cached, "mobname", String.class);
            requirePublicField(cached, "localizedName", String.class);
            requirePublicField(cached, "normalOutputsCount", int.class);
            requirePublicField(cached, "rareOutputsCount", int.class);
            requirePublicField(cached, "additionalOutputsCount", int.class);
            requirePublicField(cached, "infernalOutputsCount", int.class);
            requirePublicMethod(cached, "getIngredient", PositionedStack.class);
            requirePublicMethod(cached, "getResult", PositionedStack.class);
            requirePublicMethod(cached, "getOtherStacks", List.class);
            Class<?> output = Class.forName(OUTPUT, false, loader);
            requirePublicField(output, "chance", int.class);
            requirePublicField(output, "type",
                    Class.forName("com.kuba6000.mobsinfo.api.MobDrop$DropType", false, loader));
        } catch (ExportFailure failure) {
            GtnhNeiExportMod.LOGGER.error(
                    "[gtnh-nei-export] MobsInfo structural validation failed closed", failure);
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            ExportFailure failure = new ExportFailure("HANDLER_UNLOADED",
                    HANDLER + " exact structural validation failed", error);
            GtnhNeiExportMod.LOGGER.error(
                    "[gtnh-nei-export] MobsInfo structural validation failed closed", failure);
            throw failure;
        }
    }

    static ICraftingHandler loadCompleteCategory(ICraftingHandler prototype)
            throws ExportFailure {
        validatePrototype(prototype);
        try {
            ICraftingHandler loaded = prototype.getRecipeHandler(OPERATION);
            requireExactClass(loaded, HANDLER);
            TemplateRecipeHandler target = (TemplateRecipeHandler) loaded;
            if (target.numRecipes() != EXPECTED_SOURCE_PAGES) {
                throw new ExportFailure("HANDLER_UNLOADED", HANDLER
                        + " source cardinality drifted; expected " + EXPECTED_SOURCE_PAGES
                        + ", got " + target.numRecipes());
            }

            BuildResult result = new BuildResult();
            for (Iterator<TemplateRecipeHandler.CachedRecipe> iterator =
                    target.arecipes.iterator(); iterator.hasNext();) {
                TemplateRecipeHandler.CachedRecipe cached = iterator.next();
                Page page = buildPage(cached, result.sourcePages);
                result.sourceCanonicals.add(page.canonical);
                result.sourcePages++;
                if (page.blank) {
                    iterator.remove();
                    result.excludedBlankPages++;
                    continue;
                }
                result.pages.add(page.semantic);
            }
            Observation current = result.finish();
            synchronized (MobsInfoSemanticAdapter.class) {
                if (observation != null
                        && (!observation.countVector.equals(current.countVector)
                        || !observation.fingerprint.equals(current.fingerprint))) {
                    throw new ExportFailure("HANDLER_UNLOADED",
                            "MobsInfo corpus changed across captures in one boot; first="
                                    + observation.countVector + '/' + observation.fingerprint
                                    + ", second=" + current.countVector + '/'
                                    + current.fingerprint);
                }
                observation = current;
                SEMANTICS.put(target, Collections.unmodifiableList(new ArrayList<
                        CompleteCategoryAdapters.RecipeSemanticOverride>(result.pages)));
            }
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] MobsInfo informational adapter explicitly excluded "
                            + "{} item-unaddressable blank pages; countVector={}, "
                            + "semanticFingerprint={}, previewDiagnosticFingerprint={}, "
                            + "contract={}",
                    result.excludedBlankPages, current.countVector,
                    current.fingerprint, current.previewDiagnosticFingerprint, CONTRACT);
            return target;
        } catch (ExportFailure failure) {
            GtnhNeiExportMod.LOGGER.error(
                    "[gtnh-nei-export] MobsInfo semantic adapter failed closed", failure);
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            ExportFailure failure = new ExportFailure("HANDLER_UNLOADED",
                    HANDLER + " exact informational adapter failed", error);
            GtnhNeiExportMod.LOGGER.error(
                    "[gtnh-nei-export] MobsInfo semantic adapter failed closed", failure);
            throw failure;
        }
    }

    static synchronized CompleteCategoryAdapters.RecipeSemanticOverride semanticOverride(
            ICraftingHandler loaded, int recipeIndex) throws ExportFailure {
        requireExactClass(loaded, HANDLER);
        List<CompleteCategoryAdapters.RecipeSemanticOverride> pages = SEMANTICS.get(loaded);
        if (pages == null || pages.size() != loaded.numRecipes()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    HANDLER + " has no intact attached informational corpus");
        }
        if (recipeIndex < 0 || recipeIndex >= pages.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    HANDLER + " semantic index is out of bounds: " + recipeIndex);
        }
        return pages.get(recipeIndex);
    }

    /**
     * Draws every semantic input/output slot without MobsInfo's screen-owned scrollbar.
     * The first canonical alternative represents each cycling NEI slot in a deterministic
     * still image; the exported graph retains every alternative.
     */
    static synchronized int drawDeterministicPreview(ICraftingHandler loaded,
                                                     int recipeIndex,
                                                     int width,
                                                     int height)
            throws ExportFailure {
        CompleteCategoryAdapters.RecipeSemanticOverride semantic =
                semanticOverride(loaded, recipeIndex);
        if (semantic.inputs.size() != 1 || semantic.outputs.isEmpty()
                || width <= 0 || height <= 0) {
            throw new ExportFailure("RECIPE_WIDGET_RENDER",
                    "MobsInfo deterministic preview topology drifted at page "
                            + recipeIndex);
        }
        Gui.drawRect(1, 1, Math.max(2, width - 1), Math.max(2, height - 1),
                0xffd8d8d8);
        Minecraft.getMinecraft().fontRenderer.drawString(
                "Mob Drops", 6, 5, 0xff303030, false);
        ItemStack selector = firstPreviewStack(semantic.inputs.get(0), "input", recipeIndex);
        GuiContainerManager.drawItem(6, 17, selector);

        int columns = Math.max(1, ((width - 31) * 2) / 18);
        int rows = Math.max(1, ((height - 33) * 2) / 18);
        if (semantic.outputs.size() > Math.multiplyExact(columns, rows)) {
            throw new ExportFailure("RECIPE_WIDGET_RENDER",
                    "MobsInfo deterministic preview cannot fit "
                            + semantic.outputs.size() + " output slots in "
                            + columns + "x" + rows + " at page " + recipeIndex);
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(27.0F, 17.0F, 0.0F);
            GL11.glScalef(0.5F, 0.5F, 1.0F);
            for (int index = 0; index < semantic.outputs.size(); index++) {
                GuiContainerManager.drawItem(
                        (index % columns) * 18,
                        (index / columns) * 18,
                        firstPreviewStack(semantic.outputs.get(index), "output", recipeIndex));
            }
        } finally {
            GL11.glPopMatrix();
        }
        Minecraft.getMinecraft().fontRenderer.drawString(
                semantic.outputs.size() + " drop slots", 6, Math.max(38, height - 11),
                0xff505050, false);
        int slotIcons = Math.addExact(semantic.inputs.size(), semantic.outputs.size());
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Rendered exact MobsInfo exporter-owned preview; "
                        + "page={}/{}, slotIcons={}, contract={}",
                recipeIndex + 1, EXPECTED_SOURCE_PAGES, slotIcons, CONTRACT);
        return slotIcons;
    }

    private static ItemStack firstPreviewStack(
            CompleteCategoryAdapters.SemanticSlot slot,
            String role,
            int recipeIndex) throws ExportFailure {
        if (slot.alternatives.isEmpty() || slot.alternatives.get(0).stack == null
                || slot.alternatives.get(0).stack.getItem() == null) {
            throw new ExportFailure("RECIPE_WIDGET_RENDER",
                    "MobsInfo deterministic preview " + role
                            + " alternative drifted at page " + recipeIndex);
        }
        return slot.alternatives.get(0).stack.copy();
    }

    static synchronized Observation requirePromotedCorpus() throws ExportFailure {
        if (observation == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "MobsInfo corpus was not captured before promotion validation");
        }
        if (UNPROMOTED.equals(EXPECTED_COUNT_VECTOR)
                || UNPROMOTED.equals(EXPECTED_SHA256)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "MobsInfo informational corpus is unpromoted; observed countVector="
                            + observation.countVector + ", sha256=" + observation.fingerprint);
        }
        if (!EXPECTED_COUNT_VECTOR.equals(observation.countVector)
                || !EXPECTED_SHA256.equals(observation.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "MobsInfo informational corpus drifted; expected="
                            + EXPECTED_COUNT_VECTOR + '/' + EXPECTED_SHA256 + ", observed="
                            + observation.countVector + '/' + observation.fingerprint);
        }
        return observation;
    }

    private static Page buildPage(Object cached, int index) throws Exception {
        if (cached == null || !CACHED.equals(cached.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "MobsInfo cached page #" + index + " class drifted");
        }
        Class<?> type = cached.getClass();
        String mobName = (String) accessibleField(type, "mobname").get(cached);
        String localized = (String) accessibleField(type, "localizedName").get(cached);
        if (mobName == null || mobName.trim().isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "MobsInfo page #" + index + " has no stable entity registry name");
        }
        PositionedStack ingredient = (PositionedStack)
                accessibleMethod(type, "getIngredient").invoke(cached);
        List<?> rawOutputs = (List<?>) accessibleField(type, "mOutputs").get(cached);
        if (rawOutputs == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "MobsInfo page #" + index + " has null output list");
        }

        List<CompleteCategoryAdapters.SemanticSlot> inputs =
                new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
        List<CompleteCategoryAdapters.SemanticSlot> outputs =
                new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
        if (ingredient != null) inputs.add(slot(ingredient, "selector", index));
        StringBuilder canonical = new StringBuilder();
        canonical.append(mobName).append('|')
                .append(localized).append('|');
        appendPositioned(canonical, ingredient);
        canonical.append('|');
        List<String> outputFacts = new ArrayList<String>();
        for (Object rawOutput : rawOutputs) {
            if (!(rawOutput instanceof PositionedStack)
                    || !OUTPUT.equals(rawOutput.getClass().getName())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "MobsInfo page #" + index + " output class drifted");
            }
            PositionedStack positioned = (PositionedStack) rawOutput;
            outputs.add(slot(positioned, "drop", index));
            Field chance = accessibleField(rawOutput.getClass(), "chance");
            Field dropType = accessibleField(rawOutput.getClass(), "type");
            StringBuilder outputFact = new StringBuilder();
            outputFact.append(dropType.get(rawOutput)).append('@')
                    .append(chance.getInt(rawOutput)).append(':');
            appendPositioned(outputFact, positioned);
            outputFacts.add(outputFact.toString());
        }
        Collections.sort(outputFacts);
        for (String outputFact : outputFacts) canonical.append(outputFact).append(';');
        Collections.sort(outputs,
                new java.util.Comparator<CompleteCategoryAdapters.SemanticSlot>() {
                    @Override
                    public int compare(CompleteCategoryAdapters.SemanticSlot left,
                                       CompleteCategoryAdapters.SemanticSlot right) {
                        return slotKey(left).compareTo(slotKey(right));
                    }
                });
        canonical.append('|')
                .append(accessibleField(type, "normalOutputsCount").getInt(cached)).append(',')
                .append(accessibleField(type, "rareOutputsCount").getInt(cached)).append(',')
                .append(accessibleField(type, "additionalOutputsCount").getInt(cached)).append(',')
                .append(accessibleField(type, "infernalOutputsCount").getInt(cached));
        boolean blank = inputs.isEmpty() && outputs.isEmpty();
        return new Page(blank, canonical.toString(), blank ? null :
                new CompleteCategoryAdapters.RecipeSemanticOverride(
                        // HEE localizes two mob names nondeterministically during boot. The
                        // registry name is the stable semantic identity; localized text remains
                        // in the separately logged preview diagnostic fingerprint above.
                        "mobsinfo:" + Naming.sha256(mobName),
                        inputs, outputs));
    }

    private static CompleteCategoryAdapters.SemanticSlot slot(
            PositionedStack positioned, String role, int page) throws ExportFailure {
        if (positioned.items == null || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "MobsInfo " + role + " page #" + page + " has no alternatives");
        }
        List<CompleteCategoryAdapters.SemanticAlternative> alternatives =
                new ArrayList<CompleteCategoryAdapters.SemanticAlternative>();
        for (ItemStack original : positioned.items) {
            if (original == null || original.getItem() == null || original.stackSize <= 0) {
                throw new ExportFailure("QUANTITY_INVALID",
                        "MobsInfo " + role + " page #" + page + " has invalid stack");
            }
            ItemStack copy = original.copy();
            StackIdentity identity = StackIdentity.of(copy);
            alternatives.add(new CompleteCategoryAdapters.SemanticAlternative(
                    copy, copy.stackSize,
                    CompleteCategoryAdapters.canonicalStackIdentity(
                            identity, copy.stackSize)));
        }
        Collections.sort(alternatives,
                new java.util.Comparator<CompleteCategoryAdapters.SemanticAlternative>() {
                    @Override
                    public int compare(
                            CompleteCategoryAdapters.SemanticAlternative left,
                            CompleteCategoryAdapters.SemanticAlternative right) {
                        return left.canonicalIdentity.compareTo(right.canonicalIdentity);
                    }
                });
        return new CompleteCategoryAdapters.SemanticSlot(alternatives);
    }

    private static void appendPositioned(StringBuilder canonical, PositionedStack positioned)
            throws ExportFailure {
        if (positioned == null) {
            canonical.append('-');
            return;
        }
        if (positioned.items == null || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "MobsInfo corpus contains empty PositionedStack");
        }
        List<String> identities = new ArrayList<String>();
        for (ItemStack stack : positioned.items) {
            if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
                throw new ExportFailure("QUANTITY_INVALID",
                        "MobsInfo corpus contains invalid stack");
            }
            identities.add(CompleteCategoryAdapters.canonicalStackIdentity(
                    StackIdentity.of(stack), stack.stackSize));
        }
        Collections.sort(identities);
        for (String identity : identities) canonical.append(identity).append(',');
    }

    private static String slotKey(CompleteCategoryAdapters.SemanticSlot slot) {
        StringBuilder key = new StringBuilder();
        for (CompleteCategoryAdapters.SemanticAlternative alternative : slot.alternatives) {
            key.append(alternative.canonicalIdentity).append('\0');
        }
        return key.toString();
    }

    private static void requireExactClass(Object value, String expected)
            throws ExportFailure {
        if (value == null || !expected.equals(value.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", "expected exact " + expected
                    + ", got " + (value == null ? "<null>" : value.getClass().getName()));
        }
    }

    private static void requirePublicField(Class<?> type, String name, Class<?> fieldType)
            throws Exception {
        Field field = type.getField(name);
        if (field.getType() != fieldType || !Modifier.isPublic(field.getModifiers())
                || Modifier.isStatic(field.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + '.' + name + " field topology drifted");
        }
    }

    private static void requirePublicMethod(Class<?> type, String name, Class<?> returnType)
            throws Exception {
        Method method = type.getMethod(name);
        if (method.getReturnType() != returnType
                || !Modifier.isPublic(method.getModifiers())
                || Modifier.isStatic(method.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + '.' + name + " method topology drifted");
        }
    }

    private static Field accessibleField(Class<?> type, String name) throws Exception {
        Field field = type.getField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method accessibleMethod(Class<?> type, String name) throws Exception {
        Method method = type.getMethod(name);
        method.setAccessible(true);
        return method;
    }

    static final class Observation {
        final String countVector;
        final String fingerprint;
        final String previewDiagnosticFingerprint;
        final int uniqueDraconicMobSoulIdentities;

        Observation(String countVector, String fingerprint,
                    String previewDiagnosticFingerprint,
                    int uniqueDraconicMobSoulIdentities) {
            this.countVector = countVector;
            this.fingerprint = fingerprint;
            this.previewDiagnosticFingerprint = previewDiagnosticFingerprint;
            this.uniqueDraconicMobSoulIdentities = uniqueDraconicMobSoulIdentities;
        }
    }

    private static final class Page {
        final boolean blank;
        final String canonical;
        final CompleteCategoryAdapters.RecipeSemanticOverride semantic;

        Page(boolean blank, String canonical,
             CompleteCategoryAdapters.RecipeSemanticOverride semantic) {
            this.blank = blank;
            this.canonical = canonical;
            this.semantic = semantic;
        }
    }

    private static final class BuildResult {
        final List<CompleteCategoryAdapters.RecipeSemanticOverride> pages =
                new ArrayList<CompleteCategoryAdapters.RecipeSemanticOverride>();
        final List<String> sourceCanonicals = new ArrayList<String>();
        int sourcePages;
        int excludedBlankPages;

        Observation finish() {
            // Re-canonicalize from the attached immutable semantic projections so the digest
            // cannot accidentally depend on reflection iteration order.
            int inputPages = 0;
            int outputPages = 0;
            int emptyOutputPages = 0;
            int inputSlots = 0;
            int outputSlots = 0;
            int alternatives = 0;
            List<String> semanticRows = new ArrayList<String>();
            java.util.Set<String> draconicMobSoulKeys =
                    new java.util.HashSet<String>();
            for (CompleteCategoryAdapters.RecipeSemanticOverride page : pages) {
                if (!page.inputs.isEmpty()) inputPages++;
                if (!page.outputs.isEmpty()) outputPages++;
                else emptyOutputPages++;
                inputSlots += page.inputs.size();
                outputSlots += page.outputs.size();
                StringBuilder semanticRow = new StringBuilder();
                semanticRow.append(page.semanticId).append('|');
                alternatives += appendSlots(semanticRow, page.inputs);
                semanticRow.append('|');
                alternatives += appendSlots(semanticRow, page.outputs);
                for (CompleteCategoryAdapters.SemanticSlot slot : page.inputs) {
                    for (CompleteCategoryAdapters.SemanticAlternative alternative
                            : slot.alternatives) {
                        StackIdentity identity = StackIdentity.of(alternative.stack);
                        if (DraconicMobSoulIconRenderer.isPinnedTarget(identity)) {
                            draconicMobSoulKeys.add(identity.key);
                        }
                    }
                }
                for (CompleteCategoryAdapters.SemanticSlot slot : page.outputs) {
                    for (CompleteCategoryAdapters.SemanticAlternative alternative
                            : slot.alternatives) {
                        StackIdentity identity = StackIdentity.of(alternative.stack);
                        if (DraconicMobSoulIconRenderer.isPinnedTarget(identity)) {
                            draconicMobSoulKeys.add(identity.key);
                        }
                    }
                }
                semanticRows.add(semanticRow.toString());
            }
            Collections.sort(semanticRows);
            StringBuilder canonical = new StringBuilder(CONTRACT + "\n");
            for (String semanticRow : semanticRows) {
                canonical.append(semanticRow).append('\n');
            }
            String vector = "sourcePages=" + sourcePages
                    + ",exportedPages=" + pages.size()
                    + ",excludedBlankPages=" + excludedBlankPages
                    + ",inputPages=" + inputPages
                    + ",outputPages=" + outputPages
                    + ",emptyOutputPages=" + emptyOutputPages
                    + ",inputSlots=" + inputSlots
                    + ",outputSlots=" + outputSlots
                    + ",alternatives=" + alternatives;
            canonical.insert(0, vector + '\n');
            List<String> sortedSources = new ArrayList<String>(sourceCanonicals);
            Collections.sort(sortedSources);
            StringBuilder previewDiagnostic = new StringBuilder(
                    "gtnh-2.8.4-mobsinfo-0.5.6-preview-diagnostic-v1\n");
            for (String sourceCanonical : sortedSources) {
                previewDiagnostic.append(sourceCanonical).append('\n');
            }
            return new Observation(vector, Naming.sha256(canonical.toString()),
                    Naming.sha256(previewDiagnostic.toString()),
                    draconicMobSoulKeys.size());
        }

        private static int appendSlots(StringBuilder target,
                List<CompleteCategoryAdapters.SemanticSlot> slots) {
            int alternatives = 0;
            for (CompleteCategoryAdapters.SemanticSlot slot : slots) {
                target.append('[');
                for (CompleteCategoryAdapters.SemanticAlternative alternative
                        : slot.alternatives) {
                    target.append(alternative.canonicalIdentity).append(',');
                    alternatives++;
                }
                target.append(']');
            }
            return alternatives;
        }
    }
}
