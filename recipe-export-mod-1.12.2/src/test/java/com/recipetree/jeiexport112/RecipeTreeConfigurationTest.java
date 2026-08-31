package com.recipetree.jeiexport112;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecipeTreeConfigurationTest {
    @Test
    public void bookSpawnIsEnabledByDefault() {
        assertTrue(RecipeTreeConfiguration.defaults().spawnBookInNewWorlds());
    }

    @Test
    public void parsedBookSpawnSettingCanDisableTheGrant() {
        assertFalse(RecipeTreeConfiguration.fromBookSpawnSetting(false)
                .spawnBookInNewWorlds());
    }
}
