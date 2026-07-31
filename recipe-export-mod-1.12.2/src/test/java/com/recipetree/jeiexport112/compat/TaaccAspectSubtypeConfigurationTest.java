package com.recipetree.jeiexport112.compat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TaaccAspectSubtypeConfigurationTest {
    private String previous;

    @Before
    public void saveProperty() {
        previous = System.getProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY);
        System.clearProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY);
    }

    @After
    public void restoreProperty() {
        if (previous == null) {
            System.clearProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY);
        } else {
            System.setProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY, previous);
        }
    }

    @Test
    public void absentAndExactFalseAreDisabled() {
        assertFalse(TaaccAspectSubtypeConfiguration.isEnabled());
        System.setProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY, "false");
        assertFalse(TaaccAspectSubtypeConfiguration.isEnabled());
    }

    @Test
    public void exactTrueIsEnabled() {
        System.setProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY, "true");
        assertTrue(TaaccAspectSubtypeConfiguration.isEnabled());
    }

    @Test(expected = IllegalStateException.class)
    public void nonCanonicalBooleanIsRejected() {
        System.setProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY, "TRUE");
        TaaccAspectSubtypeConfiguration.isEnabled();
    }

    @Test(expected = IllegalStateException.class)
    public void emptyValueIsRejected() {
        System.setProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY, "");
        TaaccAspectSubtypeConfiguration.isEnabled();
    }
}
