package com.recipetree.jeiexport112.compat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WorldStartupConfigurationTest {
    private String previous;

    @Before
    public void saveProperty() {
        previous = System.getProperty(WorldStartupConfiguration.ENABLE_PROPERTY);
        System.clearProperty(WorldStartupConfiguration.ENABLE_PROPERTY);
    }

    @After
    public void restoreProperty() {
        if (previous == null) {
            System.clearProperty(WorldStartupConfiguration.ENABLE_PROPERTY);
        } else {
            System.setProperty(WorldStartupConfiguration.ENABLE_PROPERTY, previous);
        }
    }

    @Test
    public void missingAndExplicitFalseLeaveVanillaBytecodeUntouched() {
        assertFalse(WorldStartupConfiguration.isEnabled());
        System.setProperty(WorldStartupConfiguration.ENABLE_PROPERTY, "false");
        assertFalse(WorldStartupConfiguration.isEnabled());
    }

    @Test
    public void exactTrueEnablesTheTransformer() {
        System.setProperty(WorldStartupConfiguration.ENABLE_PROPERTY, "true");
        assertTrue(WorldStartupConfiguration.isEnabled());
    }

    @Test(expected = IllegalStateException.class)
    public void malformedValueFailsInsteadOfSilentlyDisabling() {
        System.setProperty(WorldStartupConfiguration.ENABLE_PROPERTY, "TRUE");
        WorldStartupConfiguration.isEnabled();
    }

    @Test(expected = IllegalStateException.class)
    public void emptyValueFailsInsteadOfSilentlyDisabling() {
        System.setProperty(WorldStartupConfiguration.ENABLE_PROPERTY, "");
        WorldStartupConfiguration.isEnabled();
    }
}
