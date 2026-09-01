package com.recipetree.jeiexport112;

import com.google.gson.stream.JsonWriter;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.VanillaTypes;
import net.minecraft.item.ItemStack;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Adds a synthetic EMC source for every unique exported item with a positive ProjectE value. */
final class ProjectEEmcPhase implements ExportPhase {
    static final String EMC_KEY = "emc|projecte:emc";
    private final ExportContext context;
    private final ItemCatalog catalog;
    private final Iterator<ItemStack> stacks;
    private final int total;
    private final Set<String> seen = new HashSet<String>();
    private final ProjectEEmcSupport emc;
    private JsonWriter recipesWriter;
    private ExportContext.CategoryMeta category;
    private int categoryIndex = -1;
    private int done;
    private int exported;
    private boolean emcFailed;

    ProjectEEmcPhase(ExportContext context, IIngredientRegistry registry) throws IOException {
        this.context = context;
        this.catalog = context.catalog(registry);
        Collection<ItemStack> allStacks = registry.getAllIngredients(VanillaTypes.ITEM);
        this.stacks = allStacks.iterator();
        this.total = allStacks.size();
        ProjectEEmcSupport loaded = null;
        try {
            loaded = ProjectEEmcSupport.load();
        } catch (IOException exception) {
            context.failure("ProjectE EMC sources were skipped because its optional API could not be opened: "
                    + exception);
        }
        this.emc = loaded;
        JeiExportMod.LOGGER.info(
                "[jeiexport] ProjectE EMC phase: checking {} HEI item identities for positive EMC values",
                total);
    }

    static boolean isAvailable() {
        return ProjectEEmcSupport.isAvailable();
    }

    static boolean shouldExport(boolean hasValue, long value) {
        return ProjectEEmcSupport.isUsableValue(hasValue, value);
    }

    @Override
    public boolean step() throws IOException {
        if (emc == null || emcFailed) {
            return true;
        }
        if (!stacks.hasNext()) {
            return true;
        }
        ItemStack stack = stacks.next();
        done++;
        try {
            ItemCatalog.ResolvedIngredient<ItemStack> resolved = catalog.resolve(VanillaTypes.ITEM, stack);
            String key = resolved.canonicalKey();
            boolean hasValue = emc.hasValue(stack);
            if (!hasValue) {
                return !stacks.hasNext();
            }
            long value = emc.value(stack);
            if (!shouldExport(true, value)) {
                if (value > ProjectEEmcSupport.MAX_SAFE_INTEGER) {
                    context.warning("EMC_VALUE_TOO_LARGE ingredient " + key + " has " + value
                            + " EMC, above the viewer's exact integer limit; source omitted");
                }
                return !stacks.hasNext();
            }
            if (!seen.add(key)) {
                return !stacks.hasNext();
            }
            catalog.ensureResolved(resolved);
            ensureCategory();
            writeRecipe(key, value);
        } catch (ReflectiveOperationException exception) {
            emcFailed = true;
            context.failure("ProjectE EMC lookup failed and remaining EMC sources were skipped: " + exception);
        } catch (IOException exception) {
            throw exception;
        } catch (Throwable throwable) {
            FatalErrors.rethrowIfFatal(throwable);
            context.failure("ProjectE EMC item #" + done + ": " + throwable);
        }
        return !stacks.hasNext();
    }

    private void ensureCategory() throws IOException {
        if (recipesWriter != null) {
            return;
        }
        catalog.ensureSynthetic(EMC_KEY, "projecte:emc", "EMC", "projecte", "emc");
        String directory = context.uniqueCategoryDirectory("projecte:emc_transmutation");
        category = new ExportContext.CategoryMeta(
                "projecte:emc_transmutation", "EMC Transmutation", directory, 0);
        categoryIndex = context.addCategory(category);
        recipesWriter = ExportContext.jsonWriter(context.root.resolve(directory).resolve("recipes.json"));
        recipesWriter.beginArray();
    }

    private void writeRecipe(String outputKey, long value) throws IOException {
        int recipeIndex = exported++;
        recipesWriter.beginObject();
        recipesWriter.name("id").value("projecte:emc/" + Naming.hash8(outputKey));
        recipesWriter.name("in").beginArray()
                .beginArray().beginArray().value(EMC_KEY).value(value).endArray().endArray()
                .endArray();
        recipesWriter.name("out").beginArray()
                .beginArray().beginArray().value(outputKey).value(1).endArray().endArray()
                .endArray();
        recipesWriter.endObject();
        context.index(EMC_KEY, false, categoryIndex, recipeIndex);
        context.index(outputKey, true, categoryIndex, recipeIndex);
        category.count++;
        context.recipeCount++;
    }

    @Override
    public String label() {
        return "EMC sources";
    }

    @Override
    public int done() {
        return done;
    }

    @Override
    public int total() {
        return total;
    }

    @Override
    public void close() throws IOException {
        if (recipesWriter != null) {
            recipesWriter.endArray();
            recipesWriter.close();
            recipesWriter = null;
        }
        JeiExportMod.LOGGER.info(
                "[jeiexport] ProjectE EMC phase complete: checked={}, exported={} EMC sources",
                done, exported);
    }

}
