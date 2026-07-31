package com.recipetree.jeiexport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationOptionsTest {
    private static final String[] PROPERTIES = {
            "jeiexport.createWorld",
            "jeiexport.exitOnComplete",
            "jeiexport.worldFolder",
            "jeiexport.worldName"
    };

    @AfterEach
    void clearProperties() {
        for (String property : PROPERTIES) {
            System.clearProperty(property);
        }
    }

    @Test
    void booleanOptionsAreStrictAndOptIn() {
        assertFalse(AutomationOptions.exitOnCompleteEnabled());
        System.setProperty("jeiexport.exitOnComplete", "true");
        assertTrue(AutomationOptions.exitOnCompleteEnabled());
        System.setProperty("jeiexport.exitOnComplete", "yes");
        assertThrows(IllegalArgumentException.class, AutomationOptions::exitOnCompleteEnabled);
    }

    @Test
    void worldFolderMustBeOnePortableComponent() {
        assertEquals("RecipeTree-Exporter-Automation", AutomationOptions.worldFolder());
        System.setProperty("jeiexport.worldFolder", "nested/world");
        assertThrows(IllegalArgumentException.class, AutomationOptions::worldFolder);
        System.setProperty("jeiexport.worldFolder", "..");
        assertThrows(IllegalArgumentException.class, AutomationOptions::worldFolder);
    }

    @Test
    void worldNameRejectsInvisibleFormattingCharacters() {
        assertEquals("Recipe Tree Export", AutomationOptions.worldName());
        System.setProperty("jeiexport.worldName", "Recipe\u0000Tree");
        assertThrows(IllegalArgumentException.class, AutomationOptions::worldName);
    }
}
