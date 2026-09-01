package com.recipetree.jeiexport112;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/** User configuration for the live Recipe Tree viewer. */
final class RecipeTreeConfiguration {
    static final String CATEGORY = "recipe_tree";
    static final String SPAWN_BOOK_KEY = "spawnBookInNewWorlds";

    private final boolean spawnBookInNewWorlds;

    private RecipeTreeConfiguration(boolean spawnBookInNewWorlds) {
        this.spawnBookInNewWorlds = spawnBookInNewWorlds;
    }

    static RecipeTreeConfiguration defaults() {
        return fromBookSpawnSetting(true);
    }

    static RecipeTreeConfiguration fromBookSpawnSetting(boolean enabled) {
        return new RecipeTreeConfiguration(enabled);
    }

    static RecipeTreeConfiguration load(File file) {
        if (file == null) {
            throw new IllegalArgumentException("Recipe Tree config file must not be null");
        }
        Configuration configuration = new Configuration(file);
        configuration.load();
        boolean spawnBook = configuration.getBoolean(
                SPAWN_BOOK_KEY,
                CATEGORY,
                true,
                "Give the first player a Recipe Tree guide book when creating a new "
                        + "singleplayer world.");
        if (configuration.hasChanged()) configuration.save();
        return fromBookSpawnSetting(spawnBook);
    }

    boolean spawnBookInNewWorlds() {
        return spawnBookInNewWorlds;
    }
}
