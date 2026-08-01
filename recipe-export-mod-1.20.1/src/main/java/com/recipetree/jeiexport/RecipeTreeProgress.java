package com.recipetree.jeiexport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/** Small client-local state. Cloud sync can replace this store without changing the planner UI. */
public final class RecipeTreeProgress {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("recipe-tree-plans.json");
    private static RecipeTreeProgress instance;

    private Map<String, SavedPlan> plans = new HashMap<>();
    private Map<String, String> favoriteRecipes = new HashMap<>();
    private Map<String, Boolean> collapsedRecipeTypes = new HashMap<>();

    public record SavedPlan(long amount, String recipeKey) {
    }

    private RecipeTreeProgress() {
    }

    public static synchronized RecipeTreeProgress get() {
        if (instance == null) instance = load();
        return instance;
    }

    public SavedPlan plan(ItemStack target) {
        return target.isEmpty() ? null : plans.get(itemKey(target));
    }

    public void savePlan(ItemStack target, SavedPlan plan) {
        if (target.isEmpty()) return;
        plans.put(itemKey(target), plan);
        save();
    }

    public String favoriteRecipe(ItemStack output) {
        return output.isEmpty() ? null : favoriteRecipes.get(itemKey(output));
    }

    public void saveFavoriteRecipe(ItemStack output, String recipeKey) {
        if (output.isEmpty() || recipeKey == null || recipeKey.isBlank()) return;
        favoriteRecipes.put(itemKey(output), recipeKey);
        save();
    }

    public void clearFavoriteRecipe(ItemStack output) {
        if (output.isEmpty()) return;
        if (favoriteRecipes.remove(itemKey(output)) != null) save();
    }

    public boolean isRecipeTypeCollapsed(String recipeType) {
        return recipeType != null && Boolean.TRUE.equals(collapsedRecipeTypes.get(recipeType));
    }

    public void setRecipeTypeCollapsed(String recipeType, boolean collapsed) {
        if (recipeType == null || recipeType.isBlank()) return;
        if (collapsed) {
            collapsedRecipeTypes.put(recipeType, true);
        } else {
            collapsedRecipeTypes.remove(recipeType);
        }
        save();
    }

    private static String itemKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static RecipeTreeProgress load() {
        if (!Files.isRegularFile(FILE)) return new RecipeTreeProgress();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            RecipeTreeProgress loaded = GSON.fromJson(reader, RecipeTreeProgress.class);
            if (loaded == null) return new RecipeTreeProgress();
            if (loaded.plans == null) loaded.plans = new HashMap<>();
            if (loaded.favoriteRecipes == null) loaded.favoriteRecipes = new HashMap<>();
            if (loaded.collapsedRecipeTypes == null) loaded.collapsedRecipeTypes = new HashMap<>();
            return loaded;
        } catch (Exception error) {
            JeiExportMod.LOGGER.warn("Could not load local recipe-tree plans from {}", FILE, error);
            return new RecipeTreeProgress();
        }
    }

    private synchronized void save() {
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(this, writer);
            }
            try {
                Files.move(temporary, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            JeiExportMod.LOGGER.warn("Could not save local recipe-tree plans to {}", FILE, error);
        }
    }
}
