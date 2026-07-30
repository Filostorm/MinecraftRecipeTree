package com.recipetree.jeiexport112;

import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.recipe.IIngredientType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

final class ItemPhase implements ExportPhase {
    private final ExportContext context;
    private final ItemCatalog catalog;
    private final List<IIngredientType<?>> types;
    private int typeIndex;
    private IIngredientType<?> currentType;
    private Iterator<?> current = Collections.emptyList().iterator();
    private int total;
    private int done;

    ItemPhase(ExportContext context, IIngredientRegistry registry) throws IOException {
        this.context = context;
        this.catalog = context.catalog(registry);
        this.types = new ArrayList<IIngredientType<?>>();
        for (IIngredientType type : registry.getRegisteredIngredientTypes()) {
            this.types.add(type);
        }
        for (IIngredientType<?> type : types) {
            try {
                total += sizeOf(registry, type);
            } catch (Throwable throwable) {
                FatalErrors.rethrowIfFatal(throwable);
                context.failure("count ingredients for " + type.getIngredientClass().getName() + ": " + throwable);
            }
        }
        JeiExportMod.LOGGER.info("[jeiexport] Item phase: {} HEI ingredient types, approximately {} ingredients",
                types.size(), total);
    }

    @Override
    public boolean step() throws IOException {
        while (!current.hasNext()) {
            if (typeIndex >= types.size()) {
                return true;
            }
            currentType = types.get(typeIndex++);
            try {
                current = allOf(catalog.registry, currentType).iterator();
            } catch (Throwable throwable) {
                FatalErrors.rethrowIfFatal(throwable);
                context.failure("list ingredients for " + currentType.getIngredientClass().getName() + ": " + throwable);
                current = Collections.emptyList().iterator();
            }
        }

        Object ingredient = current.next();
        try {
            ensure(catalog, currentType, ingredient);
        } catch (IOException e) {
            throw e;
        } catch (Throwable throwable) {
            FatalErrors.rethrowIfFatal(throwable);
            context.failure("item " + currentType.getIngredientClass().getName() + " #" + done + ": " + throwable);
        }
        done++;
        if (done % 1000 == 0) {
            JeiExportMod.LOGGER.info("[jeiexport] Items progress: {}/{} (PNG pending {})",
                    done, total, context.pngWriter.getPending());
        }
        return false;
    }

    private static <T> int sizeOf(IIngredientRegistry registry, IIngredientType<T> type) {
        return registry.getAllIngredients(type).size();
    }

    private static <T> Collection<T> allOf(IIngredientRegistry registry, IIngredientType<T> type) {
        return registry.getAllIngredients(type);
    }

    @SuppressWarnings("unchecked")
    private static <T> void ensure(ItemCatalog catalog, IIngredientType<?> type, Object ingredient) throws IOException {
        catalog.ensure((IIngredientType<T>) type, (T) ingredient);
    }

    @Override
    public String label() {
        return "items";
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
    public void close() {
        // The catalog remains open so recipes can add ingredients absent from HEI's global list.
    }
}
