package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Exact MobsInfo 0.5.6 informational Infernal Mobs drop-table adapter. */
final class MobsInfoInfernalSemanticAdapter {
    static final String HANDLER = "com.kuba6000.mobsinfo.nei.MobHandlerInfernal";
    static final String CACHED = HANDLER + "$InfernalRecipe";
    static final String OUTPUT = HANDLER + "$InfernalPositionedStack";
    static final String OPERATION = "mobsinfo.mobhandlerinfernal";
    static final String CONTRACT =
            "gtnh-2.8.4-mobsinfo-0.5.6-infernal-drop-information-v1";
    static final String UNPROMOTED = "<unpromoted>";
    static final String EXPECTED_COUNT_VECTOR =
            "pages=1,eliteOutputs=24,ultraOutputs=19,infernoOutputs=15,"
                    + "outputSlots=58,stochasticOutputs=58";
    static final String EXPECTED_SHA256 =
            "5d1d7246a4b943795150dda229eda23c9a3dfb27483b42cd9222151ec8cf416d";

    private static final Map<ICraftingHandler,
            List<CompleteCategoryAdapters.RecipeSemanticOverride>> SEMANTICS =
            new IdentityHashMap<ICraftingHandler,
                    List<CompleteCategoryAdapters.RecipeSemanticOverride>>();
    private static Observation observation;

    private MobsInfoInfernalSemanticAdapter() {}

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
            requirePublicFinalField(cached, "eliteCount", int.class);
            requirePublicFinalField(cached, "ultraCount", int.class);
            requirePublicFinalField(cached, "infernoCount", int.class);
            requirePublicFinalField(cached, "eliteChance", double.class);
            requirePublicFinalField(cached, "ultraChance", double.class);
            requirePublicFinalField(cached, "infernoChance", double.class);
            requireFinalListField(cached, "all");
            requireFinalListField(cached, "elite");
            requireFinalListField(cached, "ultra");
            requireFinalListField(cached, "inferno");
            requirePublicMethod(cached, "getResult", PositionedStack.class);
            requirePublicMethod(cached, "getOutputs", List.class);
            requirePublicMethod(cached, "getOtherStacks", List.class);
            Class<?> output = Class.forName(OUTPUT, false, loader);
            requirePrivateFinalField(output, "chance", double.class);
            requirePrivateFinalField(output, "chanceAlways", double.class);
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
            if (target.numRecipes() != 1 || target.arecipes.size() != 1) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        HANDLER + " page count drifted; expected 1, got "
                                + target.numRecipes());
            }
            BuildResult result = buildPage(target.arecipes.get(0));
            Observation current = result.observation;
            synchronized (MobsInfoInfernalSemanticAdapter.class) {
                if (observation != null
                        && (!observation.countVector.equals(current.countVector)
                        || !observation.fingerprint.equals(current.fingerprint))) {
                    throw new ExportFailure("HANDLER_UNLOADED",
                            "MobsInfo Infernal corpus changed across captures in one boot");
                }
                observation = current;
                SEMANTICS.put(target, Collections.singletonList(result.semantic));
            }
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] MobsInfo Infernal informational adapter captured "
                            + "countVector={}, fingerprint={}, contract={}",
                    current.countVector, current.fingerprint, CONTRACT);
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
        if (pages == null || pages.size() != 1 || loaded.numRecipes() != 1) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    HANDLER + " has no intact attached informational corpus");
        }
        if (recipeIndex != 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    HANDLER + " semantic index is out of bounds: " + recipeIndex);
        }
        return pages.get(0);
    }

    /**
     * Draws the exact 58-output corpus without MobsInfo's screen-owned scrollbar.
     * Its upstream drawBackground dereferences Minecraft.currentScreen as GuiRecipe,
     * which is invalid for an exporter-owned NEIRecipeWidget.
     */
    static synchronized int drawDeterministicPreview(ICraftingHandler loaded,
                                                     int recipeIndex,
                                                     int width,
                                                     int height)
            throws ExportFailure {
        CompleteCategoryAdapters.RecipeSemanticOverride semantic =
                semanticOverride(loaded, recipeIndex);
        if (semantic.outputs.size() != 58 || width <= 0 || height <= 0) {
            throw new ExportFailure("RECIPE_WIDGET_RENDER",
                    "MobsInfo Infernal deterministic preview topology drifted");
        }
        Gui.drawRect(1, 1, Math.max(2, width - 1), Math.max(2, height - 1),
                0xffd8d8d8);
        Minecraft.getMinecraft().fontRenderer.drawString(
                "Infernal Mobs - Drop Table", 6, 5, 0xff303030, false);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(7.0F, 17.0F, 0.0F);
            GL11.glScalef(0.5F, 0.5F, 1.0F);
            for (int index = 0; index < semantic.outputs.size(); index++) {
                CompleteCategoryAdapters.SemanticSlot slot = semantic.outputs.get(index);
                if (slot.alternatives.size() != 1) {
                    throw new ExportFailure("RECIPE_WIDGET_RENDER",
                            "MobsInfo Infernal preview output alternative count drifted at "
                                    + index);
                }
                ItemStack stack = slot.alternatives.get(0).stack.copy();
                GuiContainerManager.drawItem(
                        (index % 16) * 18,
                        (index / 16) * 18,
                        stack);
            }
        } finally {
            GL11.glPopMatrix();
        }
        Minecraft.getMinecraft().fontRenderer.drawString(
                "58 stochastic outputs", 6, Math.max(17, height - 11),
                0xff505050, false);
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Rendered exact MobsInfo Infernal exporter-owned preview; "
                        + "outputs=58, contract={}", CONTRACT);
        return semantic.outputs.size();
    }

    static synchronized Observation requirePromotedCorpus() throws ExportFailure {
        if (observation == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "MobsInfo Infernal corpus was not captured before promotion validation");
        }
        if (UNPROMOTED.equals(EXPECTED_COUNT_VECTOR)
                || UNPROMOTED.equals(EXPECTED_SHA256)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "MobsInfo Infernal corpus is unpromoted; observed countVector="
                            + observation.countVector + ", sha256=" + observation.fingerprint);
        }
        if (!EXPECTED_COUNT_VECTOR.equals(observation.countVector)
                || !EXPECTED_SHA256.equals(observation.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "MobsInfo Infernal corpus drifted; expected="
                            + EXPECTED_COUNT_VECTOR + '/' + EXPECTED_SHA256 + ", observed="
                            + observation.countVector + '/' + observation.fingerprint);
        }
        return observation;
    }

    private static BuildResult buildPage(Object cached) throws Exception {
        if (cached == null || !CACHED.equals(cached.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "MobsInfo Infernal cached page class drifted");
        }
        TemplateRecipeHandler.CachedRecipe generic =
                (TemplateRecipeHandler.CachedRecipe) cached;
        Method getOutputs = cached.getClass().getMethod("getOutputs");
        // InfernalRecipe is package-private even though this method is public.
        // Java access checks therefore require an explicit accessibility lease.
        getOutputs.setAccessible(true);
        List<?> all = (List<?>) getOutputs.invoke(cached);
        List<?> elite = listField(cached, "elite");
        List<?> ultra = listField(cached, "ultra");
        List<?> inferno = listField(cached, "inferno");
        if (generic.getIngredient() != null || generic.getResult() != null
                || !generic.getIngredients().isEmpty()
                || !generic.getOtherStacks().isEmpty()
                || all == null || all.isEmpty()
                || all.size() != elite.size() + ultra.size() + inferno.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "MobsInfo Infernal informational page topology drifted");
        }
        int eliteCount = intField(cached, "eliteCount");
        int ultraCount = intField(cached, "ultraCount");
        int infernoCount = intField(cached, "infernoCount");
        if (eliteCount != elite.size() || ultraCount != ultra.size()
                || infernoCount != inferno.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "MobsInfo Infernal tier counts drifted");
        }

        List<CompleteCategoryAdapters.SemanticSlot> outputs =
                new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
        List<String> rows = new ArrayList<String>();
        appendTier("elite", elite, outputs, rows);
        appendTier("ultra", ultra, outputs, rows);
        appendTier("inferno", inferno, outputs, rows);
        Collections.sort(rows);
        StringBuilder canonical = new StringBuilder(CONTRACT).append('|')
                .append(eliteCount).append(',').append(ultraCount).append(',')
                .append(infernoCount).append('|')
                .append(doubleField(cached, "eliteChance")).append(',')
                .append(doubleField(cached, "ultraChance")).append(',')
                .append(doubleField(cached, "infernoChance")).append('|');
        for (String row : rows) canonical.append(row).append(';');
        String vector = "pages=1,eliteOutputs=" + eliteCount
                + ",ultraOutputs=" + ultraCount
                + ",infernoOutputs=" + infernoCount
                + ",outputSlots=" + outputs.size()
                + ",stochasticOutputs=" + outputs.size();
        String fingerprint = Naming.sha256(vector + '\n' + canonical);
        CompleteCategoryAdapters.RecipeSemanticOverride semantic =
                new CompleteCategoryAdapters.RecipeSemanticOverride(
                        "mobsinfo-infernal:" + Naming.sha256(canonical.toString()),
                        Collections.<CompleteCategoryAdapters.SemanticSlot>emptyList(),
                        outputs);
        return new BuildResult(semantic, new Observation(vector, fingerprint));
    }

    private static void appendTier(String tier, List<?> values,
                                   List<CompleteCategoryAdapters.SemanticSlot> outputs,
                                   List<String> rows) throws Exception {
        for (Object value : values) {
            if (value == null || !OUTPUT.equals(value.getClass().getName())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "MobsInfo Infernal " + tier + " output class drifted");
            }
            PositionedStack positioned = (PositionedStack) value;
            if (positioned.items == null || positioned.items.length != 1
                    || positioned.items[0] == null
                    || positioned.items[0].getItem() == null
                    || positioned.items[0].stackSize <= 0) {
                throw new ExportFailure("QUANTITY_INVALID",
                        "MobsInfo Infernal " + tier + " output stack drifted");
            }
            // MobsInfo's names mirror its tooltips: `chance` is the ordinary
            // per-kill probability, while `chanceAlways` is conditional on the
            // mob being forced infernal by pack/configuration state.
            double unconditional = privateDouble(value, "chance");
            double conditional = privateDouble(value, "chanceAlways");
            if (!Double.isFinite(conditional) || conditional <= 0.0d
                    || conditional >= 1.0d || !Double.isFinite(unconditional)
                    || unconditional <= 0.0d || unconditional >= 1.0d
                    || unconditional > conditional) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "MobsInfo Infernal " + tier + " probability drifted");
            }
            ItemStack copy = positioned.items[0].copy();
            String identity = CompleteCategoryAdapters.canonicalStackIdentity(
                    StackIdentity.of(copy), copy.stackSize);
            CompleteCategoryAdapters.SemanticAlternative alternative =
                    new CompleteCategoryAdapters.SemanticAlternative(
                            copy, copy.stackSize, identity);
            outputs.add(new CompleteCategoryAdapters.SemanticSlot(
                    Collections.singletonList(alternative), unconditional));
            rows.add(tier + '|' + identity + '|' + conditional + '|' + unconditional);
        }
    }

    private static List<?> listField(Object value, String name) throws Exception {
        Field field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (List<?>) field.get(value);
    }

    private static int intField(Object value, String name) throws Exception {
        Field field = value.getClass().getField(name);
        // The declaring nested class is package-private in MobsInfo 0.5.6.
        field.setAccessible(true);
        return field.getInt(value);
    }

    private static double doubleField(Object value, String name) throws Exception {
        Field field = value.getClass().getField(name);
        field.setAccessible(true);
        return field.getDouble(value);
    }

    private static double privateDouble(Object value, String name) throws Exception {
        Field field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(value);
    }

    private static void requireExactClass(Object value, String expected)
            throws ExportFailure {
        if (value == null || !expected.equals(value.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", "expected exact " + expected
                    + ", got " + (value == null ? "<null>" : value.getClass().getName()));
        }
    }

    private static void requirePublicFinalField(Class<?> type, String name,
                                                Class<?> fieldType) throws Exception {
        Field field = type.getField(name);
        int modifiers = field.getModifiers();
        if (field.getType() != fieldType || !Modifier.isPublic(modifiers)
                || !Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + '.' + name + " field topology drifted");
        }
    }

    private static void requireFinalListField(Class<?> type, String name)
            throws Exception {
        Field field = type.getDeclaredField(name);
        int modifiers = field.getModifiers();
        if (field.getType() != List.class || !Modifier.isFinal(modifiers)
                || Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + '.' + name + " field topology drifted");
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
        final CompleteCategoryAdapters.RecipeSemanticOverride semantic;
        final Observation observation;

        BuildResult(CompleteCategoryAdapters.RecipeSemanticOverride semantic,
                    Observation observation) {
            this.semantic = semantic;
            this.observation = observation;
        }
    }
}
