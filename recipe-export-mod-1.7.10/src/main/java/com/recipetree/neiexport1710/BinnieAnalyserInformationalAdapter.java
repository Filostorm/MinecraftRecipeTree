package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.item.ItemStack;

/** Exact non-material semantics for Binnie Genetics 2.5.24's Analyser pages. */
final class BinnieAnalyserInformationalAdapter {
    static final String HANDLER = "binnie.genetics.nei.AnalyserRecipeHandler";
    static final String CACHED = HANDLER + "$CachedAnalyser";
    static final String OPERATION = "genetics.analyser";
    static final String CONTRACT =
            "adapter:binnie-genetics-2.5.24-analyser-in-place-genetic-information-v1";
    static final int EXPECTED_PAGES = 13;

    private BinnieAnalyserInformationalAdapter() {}

    static void validatePrototype(ICraftingHandler prototype) throws ExportFailure {
        requireExactClass(prototype, "prototype");
        if (!(prototype instanceof TemplateRecipeHandler) || prototype.numRecipes() != 0) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Binnie Analyser prototype shape drifted");
        }
    }

    static ICraftingHandler loadCompleteCategory(ICraftingHandler prototype)
            throws ExportFailure {
        validatePrototype(prototype);
        try {
            ICraftingHandler queried = prototype.getRecipeHandler(OPERATION);
            requireExactClass(queried, "operation query");
            TemplateRecipeHandler loaded = (TemplateRecipeHandler) queried;
            if (loaded.numRecipes() != EXPECTED_PAGES
                    || loaded.arecipes.size() != EXPECTED_PAGES) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "Binnie Analyser page count drifted; expected="
                                + EXPECTED_PAGES + ", observed=" + loaded.numRecipes());
            }

            List<Page> pages = new ArrayList<Page>(EXPECTED_PAGES);
            String dyeIdentity = null;
            int analysableAlternatives = 0;
            for (int index = 0; index < EXPECTED_PAGES; index++) {
                TemplateRecipeHandler.CachedRecipe cached = loaded.arecipes.get(index);
                if (cached == null || !CACHED.equals(cached.getClass().getName())) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "Binnie Analyser cached page #" + index + " class drifted");
                }
                List<PositionedStack> ingredients = loaded.getIngredientStacks(index);
                List<PositionedStack> others = loaded.getOtherStacks(index);
                if (ingredients == null || ingredients.size() != 2
                        || loaded.getResultStack(index) != null
                        || others == null || !others.isEmpty()) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "Binnie Analyser page #" + index
                                    + " must expose one analysable input plus DNA dye, "
                                    + "with no material output");
                }
                PositionedStack analysable = ingredients.get(0);
                PositionedStack dye = ingredients.get(1);
                requirePosition(analysable, 75, 25, "analysable", index);
                requirePosition(dye, 75, 56, "DNA dye", index);
                if (dye.items.length != 1) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "Binnie Analyser page #" + index
                                    + " DNA dye alternatives drifted; observed="
                                    + dye.items.length);
                }

                StringBuilder canonical = new StringBuilder();
                appendPositioned(canonical, analysable, index, "analysable");
                canonical.append('|');
                appendPositioned(canonical, dye, index, "DNA dye");
                String observedDye = CompleteCategoryAdapters.canonicalStackIdentity(
                        StackIdentity.of(dye.items[0]), dye.items[0].stackSize);
                if (dyeIdentity == null) {
                    dyeIdentity = observedDye;
                } else if (!dyeIdentity.equals(observedDye)) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "Binnie Analyser DNA dye identity drifted between pages");
                }
                analysableAlternatives += analysable.items.length;
                pages.add(new Page(cached, canonical.toString()));
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
                            "Binnie Analyser contains duplicate informational pages");
                }
            }
            loaded.arecipes.clear();
            for (Page page : pages) {
                loaded.arecipes.add(page.cached);
            }
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Loaded exact Binnie Analyser in-place genetic "
                            + "informational adapter; pages={}, analysableAlternatives={}, "
                            + "contract={}", EXPECTED_PAGES, analysableAlternatives, CONTRACT);
            return loaded;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Binnie Analyser informational adapter failed", error);
        }
    }

    private static void requirePosition(
            PositionedStack positioned, int x, int y, String label, int page)
            throws ExportFailure {
        if (positioned == null || positioned.relx != x || positioned.rely != y
                || positioned.items == null || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Binnie Analyser page #" + page + " " + label
                            + " positioned-stack topology drifted");
        }
    }

    private static void appendPositioned(
            StringBuilder canonical, PositionedStack positioned, int page, String label)
            throws ExportFailure {
        List<String> alternatives = new ArrayList<String>();
        for (ItemStack stack : positioned.items) {
            if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
                throw new ExportFailure("QUANTITY_INVALID",
                        "Binnie Analyser page #" + page + " has an invalid " + label);
            }
            alternatives.add(CompleteCategoryAdapters.canonicalStackIdentity(
                    StackIdentity.of(stack), stack.stackSize));
        }
        Collections.sort(alternatives);
        canonical.append('[');
        for (String alternative : alternatives) {
            canonical.append(alternative).append(';');
        }
        canonical.append(']');
    }

    private static void requireExactClass(Object value, String label)
            throws ExportFailure {
        if (value == null || !HANDLER.equals(value.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Binnie Analyser " + label + " class drifted; observed="
                            + (value == null ? "<null>" : value.getClass().getName()));
        }
    }

    private static final class Page {
        final TemplateRecipeHandler.CachedRecipe cached;
        final String canonical;

        Page(TemplateRecipeHandler.CachedRecipe cached, String canonical) {
            this.cached = cached;
            this.canonical = canonical;
        }
    }
}
