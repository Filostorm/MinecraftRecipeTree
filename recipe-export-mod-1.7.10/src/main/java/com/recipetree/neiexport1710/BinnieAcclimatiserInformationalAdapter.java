package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.item.ItemStack;

/** Exact non-material semantics for Binnie Genetics 2.5.24's Acclimatiser pages. */
final class BinnieAcclimatiserInformationalAdapter {
    static final String HANDLER =
            "binnie.genetics.nei.AcclimatiserRecipeHandler";
    static final String CACHED = HANDLER + "$CachedAcclimatiserRecipe";
    static final String OPERATION = "genetics.acclimatiser";
    static final String CONTRACT =
            "adapter:binnie-genetics-2.5.24-acclimatiser-in-place-tolerance-information-v2";
    static final int EXPECTED_PAGES = 12;
    static final int EXPECTED_TEMPERATURE_PAGES = 7;
    static final int EXPECTED_HUMIDITY_PAGES = 5;

    private BinnieAcclimatiserInformationalAdapter() {}

    static void validatePrototype(ICraftingHandler prototype) throws ExportFailure {
        requireExactClass(prototype, "prototype");
        if (!(prototype instanceof TemplateRecipeHandler) || prototype.numRecipes() != 0) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Binnie Acclimatiser prototype shape drifted");
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
                        "Binnie Acclimatiser page count drifted; expected="
                                + EXPECTED_PAGES + ", observed=" + loaded.numRecipes());
            }
            List<Page> pages = new ArrayList<Page>(EXPECTED_PAGES);
            int temperaturePages = 0;
            int humidityPages = 0;
            for (int index = 0; index < EXPECTED_PAGES; index++) {
                TemplateRecipeHandler.CachedRecipe cached = loaded.arecipes.get(index);
                if (cached == null || !CACHED.equals(cached.getClass().getName())) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "Binnie Acclimatiser cached page #" + index + " class drifted");
                }
                Class<?> type = cached.getClass();
                Field targetField = exactField(type, "target", List.class);
                Field resourceField = exactField(type, "resource", PositionedStack.class);
                Field effectField = exactField(type, "effect", float.class);
                Field toleranceField = exactField(type, "type",
                        Class.forName(
                                "binnie.genetics.machine.acclimatiser.ToleranceType",
                                false, type.getClassLoader()));
                List<?> targets = (List<?>) targetField.get(cached);
                PositionedStack resource = (PositionedStack) resourceField.get(cached);
                float effect = effectField.getFloat(cached);
                Object tolerance = toleranceField.get(cached);
                String toleranceName = tolerance == null ? "<null>" : tolerance.toString();
                if (targets == null || targets.size() != 10 || resource == null
                        || !Float.isFinite(effect) || effect == 0.0F
                        || !("Temperature".equals(toleranceName)
                        || "Humidity".equals(toleranceName))) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "Binnie Acclimatiser page #" + index
                                    + " in-place tolerance topology drifted; targets="
                                    + (targets == null ? -1 : targets.size()) + ", effect="
                                    + effect + ", tolerance=" + toleranceName);
                }
                if ("Temperature".equals(toleranceName)) {
                    temperaturePages++;
                } else {
                    humidityPages++;
                }
                List<PositionedStack> ingredients = loaded.getIngredientStacks(index);
                List<PositionedStack> others = loaded.getOtherStacks(index);
                if (ingredients == null || ingredients.size() != 11
                        || loaded.getResultStack(index) != null
                        || others == null || !others.isEmpty()) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "Binnie Acclimatiser page #" + index
                                    + " must expose ten target alternatives plus one resource, "
                                    + "with no material output");
                }
                StringBuilder canonical = new StringBuilder(toleranceName)
                        .append('|').append(Float.floatToIntBits(effect)).append('|');
                for (PositionedStack ingredient : ingredients) {
                    appendPositioned(canonical, ingredient, index);
                }
                pages.add(new Page(cached, canonical.toString()));
            }
            if (temperaturePages != EXPECTED_TEMPERATURE_PAGES
                    || humidityPages != EXPECTED_HUMIDITY_PAGES) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "Binnie Acclimatiser tolerance-page split drifted; expected="
                                + EXPECTED_TEMPERATURE_PAGES + "/"
                                + EXPECTED_HUMIDITY_PAGES + ", observed="
                                + temperaturePages + "/" + humidityPages);
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
                            "Binnie Acclimatiser contains duplicate informational pages");
                }
            }
            loaded.arecipes.clear();
            for (Page page : pages) {
                loaded.arecipes.add(page.cached);
            }
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Loaded exact Binnie Acclimatiser in-place "
                            + "tolerance informational adapter; pages={}, temperature={}, "
                            + "humidity={}, contract={}", EXPECTED_PAGES, temperaturePages,
                    humidityPages, CONTRACT);
            return loaded;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Binnie Acclimatiser informational adapter failed", error);
        }
    }

    private static void appendPositioned(
            StringBuilder canonical, PositionedStack positioned, int page)
            throws ExportFailure {
        if (positioned == null || positioned.items == null
                || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Binnie Acclimatiser page #" + page
                            + " has an empty positioned input");
        }
        List<String> alternatives = new ArrayList<String>();
        for (ItemStack stack : positioned.items) {
            if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
                throw new ExportFailure("QUANTITY_INVALID",
                        "Binnie Acclimatiser page #" + page
                                + " has an invalid positioned input");
            }
            StackIdentity identity = StackIdentity.of(stack);
            alternatives.add(CompleteCategoryAdapters.canonicalStackIdentity(
                    identity, stack.stackSize));
        }
        Collections.sort(alternatives);
        canonical.append('[');
        for (String alternative : alternatives) {
            canonical.append(alternative).append(';');
        }
        canonical.append(']');
    }

    private static Field exactField(Class<?> owner, String name, Class<?> expected)
            throws Exception {
        Field field = owner.getField(name);
        if (field.getType() != expected) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Binnie Acclimatiser field " + name + " type drifted");
        }
        return field;
    }

    private static void requireExactClass(Object value, String label)
            throws ExportFailure {
        if (value == null || !HANDLER.equals(value.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Binnie Acclimatiser " + label + " class drifted; observed="
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
