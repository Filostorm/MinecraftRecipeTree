package com.recipetree.jeiexport112.compat;

import com.recipetree.jeiexport112.StrictBooleanProperty;

/** Strict launch-property parser for the export-only world-start optimization. */
public final class WorldStartupConfiguration {
    public static final String ENABLE_PROPERTY = "jeiexport.optimizeWorldStartup";

    private WorldStartupConfiguration() {
    }

    public static boolean isEnabled() {
        return StrictBooleanProperty.read(ENABLE_PROPERTY, false);
    }
}
