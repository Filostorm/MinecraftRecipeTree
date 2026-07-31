package com.recipetree.jeiexport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Small client-local state. Cloud sync can replace this store without changing the planner UI. */
public final class RecipeTreeProgress {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("recipe-tree-plans.json");
    private static RecipeTreeProgress instance;

    private Set<String> discoveredItems = new HashSet<>();
    private Set<String> manuallyAvailableMachines = new HashSet<>();
    private Map<String, SavedPlan> plans = new HashMap<>();
    private boolean progressionEnabled = true;

    public record SavedPlan(long amount, double minutes, double cycleSeconds, String recipeKey) {
    }

    private RecipeTreeProgress() {
    }

    public static synchronized RecipeTreeProgress get() {
        if (instance == null) instance = load();
        return instance;
    }

    public boolean observe(Inventory inventory) {
        boolean changed = false;
        for (ItemStack stack : inventory.items) changed |= observe(stack);
        for (ItemStack stack : inventory.armor) changed |= observe(stack);
        for (ItemStack stack : inventory.offhand) changed |= observe(stack);
        if (changed) save();
        return changed;
    }

    private boolean observe(ItemStack stack) {
        return !stack.isEmpty() && discoveredItems.add(itemKey(stack));
    }

    public boolean isDiscovered(ItemStack stack) {
        return !stack.isEmpty() && discoveredItems.contains(itemKey(stack));
    }

    public boolean isMachineManuallyAvailable(ItemStack stack) {
        return !stack.isEmpty() && manuallyAvailableMachines.contains(itemKey(stack));
    }

    public void setMachineManuallyAvailable(ItemStack stack, boolean available) {
        if (stack.isEmpty()) return;
        if (available) manuallyAvailableMachines.add(itemKey(stack));
        else manuallyAvailableMachines.remove(itemKey(stack));
        save();
    }

    public boolean isProgressionEnabled() {
        return progressionEnabled;
    }

    public void setProgressionEnabled(boolean enabled) {
        progressionEnabled = enabled;
        save();
    }

    public SavedPlan plan(ItemStack target) {
        return target.isEmpty() ? null : plans.get(itemKey(target));
    }

    public void savePlan(ItemStack target, SavedPlan plan) {
        if (target.isEmpty()) return;
        plans.put(itemKey(target), plan);
        save();
    }

    public static String itemKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static RecipeTreeProgress load() {
        if (!Files.isRegularFile(FILE)) return new RecipeTreeProgress();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            RecipeTreeProgress loaded = GSON.fromJson(reader, RecipeTreeProgress.class);
            if (loaded == null) return new RecipeTreeProgress();
            if (loaded.discoveredItems == null) loaded.discoveredItems = new HashSet<>();
            if (loaded.manuallyAvailableMachines == null) loaded.manuallyAvailableMachines = new HashSet<>();
            if (loaded.plans == null) loaded.plans = new HashMap<>();
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
