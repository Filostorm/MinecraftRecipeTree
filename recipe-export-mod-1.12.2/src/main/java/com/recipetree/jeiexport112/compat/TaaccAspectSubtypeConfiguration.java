package com.recipetree.jeiexport112.compat;

import com.recipetree.jeiexport112.StrictBooleanProperty;

/** Strict launch-property parser for the exact TAACC 0.0.3 subtype repair. */
public final class TaaccAspectSubtypeConfiguration {
    public static final String ENABLE_PROPERTY = "jeiexport.normalizeTaaccMissingAspect";

    private TaaccAspectSubtypeConfiguration() {
    }

    public static boolean isEnabled() {
        return StrictBooleanProperty.read(ENABLE_PROPERTY, false);
    }
}
