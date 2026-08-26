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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small client-local state. Cloud sync can replace this store without changing the planner UI. */
public final class RecipeTreeProgress {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("recipe-tree-plans.json");
    private static final Path DISCOVERIES_FILE = FMLPaths.CONFIGDIR.get()
            .resolve("recipe-tree-discoveries.json");
    private static RecipeTreeProgress instance;

    private Map<String, SavedPlan> plans = new HashMap<>();
    private Map<String, String> favoriteRecipes = new HashMap<>();
    private Map<String, Boolean> collapsedRecipeTypes = new HashMap<>();
    private List<RecipeHistoryEntry> recipeHistory = new ArrayList<>();
    private RecipeHistoryEntry lastViewedRecipeTree;
    private Set<String> discoveredItems = new HashSet<>();
    private boolean recipeBookMode;

    public record SavedPlan(long amount, String recipeKey) {
    }

    public record RecipeHistoryEntry(
            String itemKey,
            String recipeKey,
            long amount,
            boolean compactMode,
            int treeDepth,
            List<RecipeHistoryRoot> roots,
            List<RecipeHistorySelection> selections,
            boolean snapshot) {
        public RecipeHistoryEntry(
                String itemKey,
                String recipeKey,
                long amount,
                boolean compactMode,
                int treeDepth,
                List<RecipeHistorySelection> selections,
                boolean snapshot) {
            this(itemKey, recipeKey, amount, compactMode, treeDepth, null, selections, snapshot);
        }
    }

    /** One independently planned output at the leading edge of a saved graph. */
    public record RecipeHistoryRoot(
            String ingredientKey,
            String ingredientName,
            String recipeKey,
            long amount) {
    }

    /** A complete, path-addressed node choice so history can restore and compare whole trees. */
    public record RecipeHistorySelection(
            int rootIndex,
            List<Integer> path,
            String ingredientKey,
            String ingredientName,
            String recipeKey,
            String recipeType) {
        public RecipeHistorySelection(
                List<Integer> path,
                String ingredientKey,
                String ingredientName,
                String recipeKey,
                String recipeType) {
            this(0, path, ingredientKey, ingredientName, recipeKey, recipeType);
        }
    }

    private record StateFile(
            Map<String, SavedPlan> plans,
            Map<String, String> favoriteRecipes,
            Map<String, Boolean> collapsedRecipeTypes,
            List<RecipeHistoryEntry> recipeHistory,
            RecipeHistoryEntry lastViewedRecipeTree,
            boolean recipeBookMode) {
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
        if (plan.equals(plans.put(itemKey(target), plan))) return;
        save();
    }

    public String favoriteRecipe(ItemStack output) {
        return output.isEmpty() ? null : favoriteRecipes.get(itemKey(output));
    }

    public String favoriteRecipe(String ingredientKey) {
        return ingredientKey == null || ingredientKey.isBlank()
                ? null
                : favoriteRecipes.get(ingredientKey);
    }

    public void saveFavoriteRecipe(ItemStack output, String recipeKey) {
        if (output.isEmpty() || recipeKey == null || recipeKey.isBlank()) return;
        if (recipeKey.equals(favoriteRecipes.put(itemKey(output), recipeKey))) return;
        save();
    }

    public void saveFavoriteRecipe(String ingredientKey, String recipeKey) {
        if (ingredientKey == null || ingredientKey.isBlank()
                || recipeKey == null || recipeKey.isBlank()) return;
        if (recipeKey.equals(favoriteRecipes.put(ingredientKey, recipeKey))) return;
        save();
    }

    public void clearFavoriteRecipe(ItemStack output) {
        if (output.isEmpty()) return;
        if (favoriteRecipes.remove(itemKey(output)) != null) save();
    }

    public void clearFavoriteRecipe(String ingredientKey) {
        if (ingredientKey == null || ingredientKey.isBlank()) return;
        if (favoriteRecipes.remove(ingredientKey) != null) save();
    }

    public boolean isRecipeTypeCollapsed(String recipeType) {
        return recipeType != null && Boolean.TRUE.equals(collapsedRecipeTypes.get(recipeType));
    }

    public void setRecipeTypeCollapsed(String recipeType, boolean collapsed) {
        if (recipeType == null || recipeType.isBlank()) return;
        if (collapsed) {
            if (Boolean.TRUE.equals(collapsedRecipeTypes.put(recipeType, true))) return;
        } else {
            if (collapsedRecipeTypes.remove(recipeType) == null) return;
        }
        save();
    }

    public List<RecipeHistoryEntry> recipeHistory() {
        return List.copyOf(recipeHistory);
    }

    public void replaceRecipeHistory(List<RecipeHistoryEntry> history) {
        replaceRecipeHistory(history, lastViewedRecipeTree);
    }

    public RecipeHistoryEntry lastViewedRecipeTree() {
        return lastViewedRecipeTree;
    }

    public void replaceRecipeHistory(
            List<RecipeHistoryEntry> history,
            RecipeHistoryEntry lastViewed) {
        int first = Math.max(0, history.size() - 32);
        List<RecipeHistoryEntry> replacement = new ArrayList<>(history.subList(first, history.size()));
        if (recipeHistory.equals(replacement)
                && java.util.Objects.equals(lastViewedRecipeTree, lastViewed)) return;
        recipeHistory = replacement;
        lastViewedRecipeTree = lastViewed;
        save();
    }

    public boolean recipeBookMode() {
        return recipeBookMode;
    }

    public void setRecipeBookMode(boolean enabled) {
        if (recipeBookMode == enabled) return;
        recipeBookMode = enabled;
        save();
    }

    public boolean hasDiscovered(ItemStack stack) {
        return !stack.isEmpty() && discoveredItems.contains(itemKey(stack));
    }

    public int discoverItems(Collection<ItemStack> stacks) {
        int discovered = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            if (discoveredItems.add(itemKey(stack))) discovered++;
        }
        if (discovered > 0) saveDiscoveries();
        return discovered;
    }

    public int discoverItemKeys(Collection<String> itemKeys) {
        int discovered = 0;
        for (String itemKey : itemKeys) {
            if (itemKey == null || itemKey.isBlank()) continue;
            if (discoveredItems.add(itemKey)) discovered++;
        }
        if (discovered > 0) saveDiscoveries();
        return discovered;
    }

    public static RecipeHistoryEntry historyEntry(
            ItemStack target,
            String recipeKey,
            long amount,
            boolean compactMode,
            int treeDepth,
            List<RecipeHistorySelection> selections) {
        return historyEntry(target, recipeKey, amount, compactMode, treeDepth, null, selections);
    }

    public static RecipeHistoryEntry historyEntry(
            ItemStack target,
            String recipeKey,
            long amount,
            boolean compactMode,
            int treeDepth,
            List<RecipeHistoryRoot> roots,
            List<RecipeHistorySelection> selections) {
        if (target.isEmpty()) throw new IllegalArgumentException("History target cannot be empty");
        return new RecipeHistoryEntry(
                itemKey(target),
                recipeKey,
                Math.min(RecipeQuantityMath.MAX_REQUESTED_AMOUNT, Math.max(1, amount)),
                compactMode,
                Math.max(1, treeDepth),
                roots == null ? null : List.copyOf(roots),
                selections == null ? List.of() : List.copyOf(selections),
                false);
    }

    private static String itemKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static RecipeTreeProgress load() {
        RecipeTreeProgress loaded = new RecipeTreeProgress();
        if (Files.isRegularFile(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                RecipeTreeProgress stored = GSON.fromJson(reader, RecipeTreeProgress.class);
                if (stored != null) loaded = stored;
            } catch (Exception error) {
                JeiExportMod.LOGGER.warn("Could not load local recipe-tree plans from {}", FILE, error);
            }
        }
        if (loaded.plans == null) loaded.plans = new HashMap<>();
        if (loaded.favoriteRecipes == null) loaded.favoriteRecipes = new HashMap<>();
        if (loaded.collapsedRecipeTypes == null) loaded.collapsedRecipeTypes = new HashMap<>();
        if (loaded.recipeHistory == null) loaded.recipeHistory = new ArrayList<>();
        loaded.recipeHistory = loaded.recipeHistory.stream()
                .filter(java.util.Objects::nonNull)
                .map(RecipeTreeProgress::normalizeHistoryEntry)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (loaded.lastViewedRecipeTree != null) {
            loaded.lastViewedRecipeTree = normalizeHistoryEntry(loaded.lastViewedRecipeTree);
        }
        if (loaded.discoveredItems == null) loaded.discoveredItems = new HashSet<>();
        if (loaded.recipeHistory.size() > 32) {
            loaded.recipeHistory = new ArrayList<>(loaded.recipeHistory.subList(
                    loaded.recipeHistory.size() - 32,
                    loaded.recipeHistory.size()));
        }

        boolean legacyDiscoveries = !loaded.discoveredItems.isEmpty();
        if (Files.isRegularFile(DISCOVERIES_FILE)) {
            try (Reader reader = Files.newBufferedReader(DISCOVERIES_FILE)) {
                String[] stored = GSON.fromJson(reader, String[].class);
                if (stored == null) {
                    throw new IllegalStateException("Discovery file did not contain an item ID array");
                }
                for (String itemKey : stored) {
                    if (itemKey != null && !itemKey.isBlank()) loaded.discoveredItems.add(itemKey);
                }
            } catch (Exception error) {
                JeiExportMod.LOGGER.warn(
                        "Could not load recipe-tree item discoveries from {}",
                        DISCOVERIES_FILE,
                        error);
            }
        } else if (legacyDiscoveries) {
            loaded.saveDiscoveries();
        }
        return loaded;
    }

    private static RecipeHistoryEntry normalizeHistoryEntry(RecipeHistoryEntry entry) {
        return entry.selections() == null
                ? new RecipeHistoryEntry(
                        entry.itemKey(),
                        entry.recipeKey(),
                        entry.amount(),
                        entry.compactMode(),
                        entry.treeDepth(),
                        entry.roots(),
                        List.of(),
                        entry.snapshot())
                : entry;
    }

    private synchronized void save() {
        writeAtomically(FILE, new StateFile(
                plans,
                favoriteRecipes,
                collapsedRecipeTypes,
                recipeHistory,
                lastViewedRecipeTree,
                recipeBookMode), "local recipe-tree plans");
    }

    private synchronized void saveDiscoveries() {
        List<String> ordered = discoveredItems.stream().sorted().toList();
        writeAtomically(DISCOVERIES_FILE, ordered, "recipe-tree item discoveries");
    }

    private static void writeAtomically(Path destination, Object value, String description) {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try {
            Files.createDirectories(destination.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(value, writer);
            }
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            JeiExportMod.LOGGER.warn("Could not save {} to {}", description, destination, error);
        }
    }
}
