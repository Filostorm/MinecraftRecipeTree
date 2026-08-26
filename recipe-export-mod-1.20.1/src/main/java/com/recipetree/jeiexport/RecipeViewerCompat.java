package com.recipetree.jeiexport;

import mezz.jei.api.runtime.IJeiRuntime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Keeps optional recipe-viewer integrations out of JEI-only class loading. */
final class RecipeViewerCompat {
    private static final String REI_RECIPE_MANAGER =
            "me.shedaniel.rei.jeicompat.wrap.JEIRecipeManager";
    private static final String REI_ADAPTER =
            "com.recipetree.jeiexport.rei.ReiRuntimeAdapter";

    private RecipeViewerCompat() {
    }

    static IJeiRuntime wrap(IJeiRuntime runtime) {
        if (!runtime.getRecipeManager().getClass().getName().equals(REI_RECIPE_MANAGER)) {
            return runtime;
        }
        try {
            Class<?> adapterClass = Class.forName(REI_ADAPTER);
            Method wrap = adapterClass.getMethod("wrap", IJeiRuntime.class);
            return (IJeiRuntime) wrap.invoke(null, runtime);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException error) {
            JeiExportMod.LOGGER.error("REI was detected but its Recipe Tree adapter could not load", error);
            return runtime;
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            JeiExportMod.LOGGER.error("REI was detected but its Recipe Tree adapter failed", cause);
            return runtime;
        }
    }
}
