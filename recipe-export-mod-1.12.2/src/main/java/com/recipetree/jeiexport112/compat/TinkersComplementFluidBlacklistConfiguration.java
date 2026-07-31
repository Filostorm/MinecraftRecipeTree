package com.recipetree.jeiexport112.compat;

import com.recipetree.jeiexport112.StrictBooleanProperty;

/** Strict launch-property parser for the exact Tinkers' Complement 0.4.3 JEI repair. */
public final class TinkersComplementFluidBlacklistConfiguration {
    public static final String ENABLE_PROPERTY =
            "jeiexport.skipTinkersComplementUnboundBlacklistFluid";

    private TinkersComplementFluidBlacklistConfiguration() {
    }

    public static boolean isEnabled() {
        return StrictBooleanProperty.read(ENABLE_PROPERTY, false);
    }
}
