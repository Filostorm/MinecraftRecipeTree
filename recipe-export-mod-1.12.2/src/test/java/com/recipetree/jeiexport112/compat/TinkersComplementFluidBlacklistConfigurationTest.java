package com.recipetree.jeiexport112.compat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TinkersComplementFluidBlacklistConfigurationTest {
    private String previous;

    @Before
    public void saveProperty() {
        previous = System.getProperty(
                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY
        );
        System.clearProperty(TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY);
    }

    @After
    public void restoreProperty() {
        if (previous == null) {
            System.clearProperty(TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY);
        } else {
            System.setProperty(
                    TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY, previous
            );
        }
    }

    @Test
    public void absentAndExactFalseAreDisabled() {
        assertFalse(TinkersComplementFluidBlacklistConfiguration.isEnabled());
        System.setProperty(
                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY, "false"
        );
        assertFalse(TinkersComplementFluidBlacklistConfiguration.isEnabled());
    }

    @Test
    public void exactTrueIsEnabled() {
        System.setProperty(
                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY, "true"
        );
        assertTrue(TinkersComplementFluidBlacklistConfiguration.isEnabled());
    }

    @Test(expected = IllegalStateException.class)
    public void nonCanonicalBooleanIsRejected() {
        System.setProperty(
                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY, "TRUE"
        );
        TinkersComplementFluidBlacklistConfiguration.isEnabled();
    }

    @Test(expected = IllegalStateException.class)
    public void emptyValueIsRejected() {
        System.setProperty(
                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY, ""
        );
        TinkersComplementFluidBlacklistConfiguration.isEnabled();
    }
}
