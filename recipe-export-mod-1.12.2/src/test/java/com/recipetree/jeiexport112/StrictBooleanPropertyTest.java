package com.recipetree.jeiexport112;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class StrictBooleanPropertyTest {
    private static final String PROPERTY = "jeiexport.test.strictBoolean";

    @After
    public void clearProperty() {
        System.clearProperty(PROPERTY);
    }

    @Test
    public void absentValueUsesTheExplicitDefault() {
        assertFalse(StrictBooleanProperty.read(PROPERTY, false));
        assertTrue(StrictBooleanProperty.read(PROPERTY, true));
    }

    @Test
    public void canonicalValuesAreAccepted() {
        System.setProperty(PROPERTY, "true");
        assertTrue(StrictBooleanProperty.read(PROPERTY, false));
        System.setProperty(PROPERTY, "false");
        assertFalse(StrictBooleanProperty.read(PROPERTY, true));
    }

    @Test
    public void everyNonCanonicalValueIsRejected() {
        for (String value : new String[]{"TRUE", "False", "1", "yes", "", " true "}) {
            try {
                StrictBooleanProperty.parse(PROPERTY, value);
                fail("Expected rejection for " + value);
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains(PROPERTY));
                assertTrue(expected.getMessage().contains("exactly true or false"));
            }
        }
    }
}
