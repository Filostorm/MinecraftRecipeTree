package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;

/**
 * Exact GTNH 2.8.4 adapter for Galacticraft's animation-gated circuit-fabricator results.
 */
final class GalacticraftCircuitFabricatorSemanticAdapter {
    static final String HANDLER =
            "micdoodle8.mods.galacticraft.core.nei.CircuitFabricatorRecipeHandler";
    static final String CACHED = HANDLER + "$CachedCircuitRecipe";
    static final String OPERATION = "galacticraft.circuits";
    static final String CONTRACT =
            "adapter:galacticraft-3.3.13-gtnh-circuit-fabricator-stable-result-v1";
    static final int EXPECTED_PAGES = 3;
    private static final int VISIBLE_TICKS_PASSED = 51;

    private static final Map<ICraftingHandler,
            List<CompleteCategoryAdapters.RecipeSemanticOverride>> SEMANTICS =
            new IdentityHashMap<ICraftingHandler,
                    List<CompleteCategoryAdapters.RecipeSemanticOverride>>();
    private static int deterministicPreviewDraws;

    private GalacticraftCircuitFabricatorSemanticAdapter() {}

    static void validatePrototype(ICraftingHandler prototype) throws ExportFailure {
        requireExactClass(prototype, HANDLER, "prototype");
        if (!(prototype instanceof TemplateRecipeHandler) || prototype.numRecipes() != 0) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Galacticraft circuit-fabricator prototype shape drifted");
        }
    }

    static synchronized ICraftingHandler loadCompleteCategory(ICraftingHandler prototype)
            throws ExportFailure {
        validatePrototype(prototype);
        try {
            ICraftingHandler queried = prototype.getRecipeHandler(OPERATION);
            requireExactClass(queried, HANDLER, "operation query");
            TemplateRecipeHandler loaded = (TemplateRecipeHandler) queried;
            if (loaded.numRecipes() != EXPECTED_PAGES
                    || loaded.arecipes.size() != EXPECTED_PAGES) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "Galacticraft circuit-fabricator page count drifted; expected="
                                + EXPECTED_PAGES + ", observed=" + loaded.numRecipes());
            }

            List<Page> pages = new ArrayList<Page>(EXPECTED_PAGES);
            for (int index = 0; index < EXPECTED_PAGES; index++) {
                TemplateRecipeHandler.CachedRecipe cached = loaded.arecipes.get(index);
                if (cached == null || !CACHED.equals(cached.getClass().getName())) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "Galacticraft circuit-fabricator cached page #" + index
                                    + " class drifted");
                }
                List<PositionedStack> ingredients = loaded.getIngredientStacks(index);
                PositionedStack stableResult = cached.getResult();
                List<PositionedStack> others = loaded.getOtherStacks(index);
                if (ingredients == null || ingredients.size() != 5
                        || stableResult == null || others == null || !others.isEmpty()) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "Galacticraft circuit-fabricator page #" + index
                                    + " role topology drifted");
                }
                List<CompleteCategoryAdapters.SemanticSlot> inputs =
                        new ArrayList<CompleteCategoryAdapters.SemanticSlot>(5);
                StringBuilder canonical = new StringBuilder();
                for (int input = 0; input < ingredients.size(); input++) {
                    CompleteCategoryAdapters.SemanticSlot slot =
                            itemSlot(ingredients.get(input), "input", index, input);
                    inputs.add(slot);
                    appendSlot(canonical, slot);
                }
                CompleteCategoryAdapters.SemanticSlot output =
                        itemSlot(stableResult, "output", index, 0);
                requirePinnedOutput(output, index);
                canonical.append("->");
                appendSlot(canonical, output);
                pages.add(new Page(cached, canonical.toString(),
                        new CompleteCategoryAdapters.RecipeSemanticOverride(
                                CONTRACT + ":" + index, inputs,
                                Collections.singletonList(output))));
            }
            Collections.sort(pages, new Comparator<Page>() {
                @Override
                public int compare(Page left, Page right) {
                    return left.canonical.compareTo(right.canonical);
                }
            });
            for (int index = 1; index < pages.size(); index++) {
                if (pages.get(index - 1).canonical.equals(pages.get(index).canonical)) {
                    throw new ExportFailure("HANDLER_DUPLICATE",
                            "Galacticraft circuit-fabricator contains duplicate pages");
                }
            }
            loaded.arecipes.clear();
            List<CompleteCategoryAdapters.RecipeSemanticOverride> ordered =
                    new ArrayList<CompleteCategoryAdapters.RecipeSemanticOverride>(pages.size());
            for (Page page : pages) {
                loaded.arecipes.add(page.cached);
                ordered.add(page.semantics);
            }
            SEMANTICS.put(loaded, Collections.unmodifiableList(ordered));
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Loaded exact Galacticraft circuit-fabricator "
                            + "stable-result adapter; pages={}, contract={}",
                    EXPECTED_PAGES, CONTRACT);
            return loaded;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Galacticraft circuit-fabricator adapter failed", error);
        }
    }

    static synchronized CompleteCategoryAdapters.RecipeSemanticOverride semanticOverride(
            ICraftingHandler loaded, int recipeIndex) throws ExportFailure {
        requireExactClass(loaded, HANDLER, "loaded handler");
        List<CompleteCategoryAdapters.RecipeSemanticOverride> pages = SEMANTICS.get(loaded);
        if (pages == null || loaded.numRecipes() != pages.size()
                || recipeIndex < 0 || recipeIndex >= pages.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Galacticraft circuit-fabricator semantic snapshot is unavailable or drifted");
        }
        return pages.get(recipeIndex);
    }

    static synchronized long drawVisibleResult(
            ICraftingHandler loaded, int recipeIndex, OffscreenRenderer.DrawCall draw)
            throws Exception {
        requireExactClass(loaded, HANDLER, "preview handler");
        if (recipeIndex < 0 || recipeIndex >= EXPECTED_PAGES || draw == null) {
            throw new ExportFailure("RECIPE_WIDGET_RENDER",
                    "Galacticraft circuit-fabricator preview request drifted");
        }
        Field ticks = loaded.getClass().getDeclaredField("ticksPassed");
        if (ticks.getType() != int.class) {
            throw new ExportFailure("RECIPE_WIDGET_RENDER",
                    "Galacticraft circuit-fabricator ticksPassed field drifted");
        }
        ticks.setAccessible(true);
        int original = ticks.getInt(loaded);
        try {
            ticks.setInt(loaded, VISIBLE_TICKS_PASSED);
            draw.draw();
        } finally {
            ticks.setInt(loaded, original);
        }
        if (ticks.getInt(loaded) != original) {
            throw new ExportFailure("RECIPE_WIDGET_RENDER",
                    "Galacticraft circuit-fabricator preview clock was not restored");
        }
        deterministicPreviewDraws++;
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Rendered exact Galacticraft circuit-fabricator "
                        + "visible-result preview; page={}/{}, contract={}",
                recipeIndex + 1, EXPECTED_PAGES, CONTRACT);
        return 1L;
    }

    static synchronized boolean completedContract() throws ExportFailure {
        if (deterministicPreviewDraws != EXPECTED_PAGES) {
            throw new ExportFailure("RECIPE_WIDGET_RENDER",
                    "Galacticraft circuit-fabricator deterministic preview count drifted; "
                            + "expected=" + EXPECTED_PAGES + ", observed="
                            + deterministicPreviewDraws);
        }
        return true;
    }

    private static CompleteCategoryAdapters.SemanticSlot itemSlot(
            PositionedStack positioned, String role, int page, int slot)
            throws ExportFailure {
        if (positioned == null || positioned.items == null
                || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Galacticraft circuit-fabricator page #" + page + " " + role
                            + " slot #" + slot + " has no alternatives");
        }
        List<CompleteCategoryAdapters.SemanticAlternative> alternatives =
                new ArrayList<CompleteCategoryAdapters.SemanticAlternative>();
        for (ItemStack original : positioned.items) {
            if (original == null || original.getItem() == null || original.stackSize <= 0) {
                throw new ExportFailure("QUANTITY_INVALID",
                        "Galacticraft circuit-fabricator page #" + page + " " + role
                                + " slot #" + slot + " has an invalid alternative");
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

    private static void requirePinnedOutput(
            CompleteCategoryAdapters.SemanticSlot output, int page) throws ExportFailure {
        if (output.alternatives.size() != 1) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Galacticraft circuit-fabricator page #" + page
                            + " output alternatives drifted");
        }
        CompleteCategoryAdapters.SemanticAlternative alternative =
                output.alternatives.get(0);
        StackIdentity identity = StackIdentity.of(alternative.stack);
        if (!identity.key.startsWith("item|GalacticraftCore:item.basicItem|meta=")
                || identity.canonicalNbt != null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Galacticraft circuit-fabricator output identity drifted: "
                            + identity.key);
        }
        int metadata = alternative.stack.getItemDamage();
        int expectedAmount;
        if (metadata == 12) {
            expectedAmount = 9;
        } else if (metadata == 13) {
            expectedAmount = 3;
        } else if (metadata == 14) {
            expectedAmount = 1;
        } else {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Galacticraft circuit-fabricator output metadata drifted: " + metadata);
        }
        if (alternative.amount != expectedAmount) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Galacticraft circuit-fabricator output quantity drifted for metadata "
                            + metadata + "; expected=" + expectedAmount + ", observed="
                            + alternative.amount);
        }
    }

    private static void appendSlot(
            StringBuilder canonical, CompleteCategoryAdapters.SemanticSlot slot) {
        canonical.append('[');
        for (CompleteCategoryAdapters.SemanticAlternative alternative : slot.alternatives) {
            canonical.append(alternative.canonicalIdentity).append(';');
        }
        canonical.append(']');
    }

    private static void requireExactClass(
            Object value, String expected, String label) throws ExportFailure {
        if (value == null || !expected.equals(value.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Galacticraft circuit-fabricator " + label + " class drifted; expected="
                            + expected + ", observed="
                            + (value == null ? "<null>" : value.getClass().getName()));
        }
    }

    private static final class Page {
        final TemplateRecipeHandler.CachedRecipe cached;
        final String canonical;
        final CompleteCategoryAdapters.RecipeSemanticOverride semantics;

        Page(TemplateRecipeHandler.CachedRecipe cached, String canonical,
             CompleteCategoryAdapters.RecipeSemanticOverride semantics) {
            this.cached = cached;
            this.canonical = canonical;
            this.semantics = semantics;
        }
    }
}
