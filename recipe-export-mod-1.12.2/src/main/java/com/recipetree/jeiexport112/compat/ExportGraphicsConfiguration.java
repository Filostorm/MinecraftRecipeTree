package com.recipetree.jeiexport112.compat;

import com.recipetree.jeiexport112.StrictBooleanProperty;

/** Strict launch-property parser for the audited export graphics transformer bundle. */
public final class ExportGraphicsConfiguration {
    public static final String ENABLE_PROPERTY = "jeiexport.disableStencil";

    private ExportGraphicsConfiguration() {
    }

    public static boolean isEnabled() {
        return StrictBooleanProperty.read(ENABLE_PROPERTY, false);
    }
}
