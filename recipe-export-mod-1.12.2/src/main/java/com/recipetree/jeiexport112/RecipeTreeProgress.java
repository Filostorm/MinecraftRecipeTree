package com.recipetree.jeiexport112;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraftforge.fml.common.Loader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Versioned client-local persistence for the 1.12.2 recipe tree.
 *
 * <p>This store contains only portable descriptors and string identities. It deliberately has no
 * dependency on live Minecraft items, JEI wrappers, or recipe layouts, so an identity that cannot
 * be resolved in the current runtime is retained for a later compatible runtime.</p>
 */
public final class RecipeTreeProgress {
    static final int SCHEMA_VERSION = 1;
    static final String MINECRAFT_VERSION = "1.12.2";
    static final int INGREDIENT_IDENTITY_VERSION = 1;
    static final int MAX_HISTORY = 32;

    private static final String FILE_NAME = "recipe-tree-plans.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RecipeTreeProgress instance;

    private final Path file;
    private final boolean writesEnabled;
    private StateData data;
    private String activeWorldKey;

    RecipeTreeProgress(Path file, StateData data, boolean writesEnabled) {
        this.file = file;
        this.data = copyData(data);
        this.writesEnabled = writesEnabled;
    }

    /** Loads the singleton from Forge's configured config directory. */
    public static synchronized RecipeTreeProgress get() {
        if (instance == null) {
            Path file = Loader.instance().getConfigDir().toPath().resolve(FILE_NAME);
            instance = load(file);
        }
        return instance;
    }

    public synchronized SavedPlan plan(String ingredientIdentity) {
        return data.plans.get(ingredientIdentity);
    }

    public synchronized void savePlan(String ingredientIdentity, SavedPlan plan) {
        if (ingredientIdentity == null || plan == null) return;
        SavedPlan previous = data.plans.put(ingredientIdentity, copyPlan(plan));
        if (!plan.equals(previous)) save();
    }

    public synchronized void clearPlan(String ingredientIdentity) {
        if (ingredientIdentity != null && data.plans.remove(ingredientIdentity) != null) save();
    }

    public synchronized String favoriteRecipe(String ingredientIdentity) {
        return data.favoriteRecipes.get(ingredientIdentity);
    }

    public synchronized void saveFavoriteRecipe(String ingredientIdentity, String recipeIdentity) {
        if (ingredientIdentity == null || recipeIdentity == null) return;
        String previous = data.favoriteRecipes.put(ingredientIdentity, recipeIdentity);
        if (!recipeIdentity.equals(previous)) save();
    }

    public synchronized void clearFavoriteRecipe(String ingredientIdentity) {
        if (ingredientIdentity != null && data.favoriteRecipes.remove(ingredientIdentity) != null) {
            save();
        }
    }

    public synchronized boolean isRecipeTypeCollapsed(String recipeTypeIdentity) {
        return Boolean.TRUE.equals(data.collapsedRecipeTypes.get(recipeTypeIdentity));
    }

    public synchronized void setRecipeTypeCollapsed(String recipeTypeIdentity, boolean collapsed) {
        if (recipeTypeIdentity == null) return;
        boolean changed;
        if (collapsed) {
            changed = !Boolean.TRUE.equals(data.collapsedRecipeTypes.put(recipeTypeIdentity, true));
        } else {
            changed = data.collapsedRecipeTypes.remove(recipeTypeIdentity) != null;
        }
        if (changed) save();
    }

    public synchronized boolean isReusableInput(
            String recipeIdentity,
            String ingredientIdentity) {
        return data.reusableInputs.contains(reusableInputKey(recipeIdentity, ingredientIdentity));
    }

    public synchronized void setReusableInputs(
            String recipeIdentity,
            Collection<String> ingredientIdentities,
            boolean reusable) {
        if (recipeIdentity == null || ingredientIdentities == null) return;
        boolean changed = false;
        for (String ingredientIdentity : ingredientIdentities) {
            if (ingredientIdentity == null) continue;
            String key = reusableInputKey(recipeIdentity, ingredientIdentity);
            changed |= reusable ? data.reusableInputs.add(key) : data.reusableInputs.remove(key);
        }
        if (changed) save();
    }

    private static String reusableInputKey(String recipeIdentity, String ingredientIdentity) {
        if (recipeIdentity == null || ingredientIdentity == null) return "";
        return recipeIdentity.length() + ":" + recipeIdentity + ingredientIdentity;
    }

    public synchronized List<RecipeHistoryEntry> recipeHistory() {
        WorldHistoryData worldHistory = activeWorldHistory();
        return worldHistory == null
                ? Collections.<RecipeHistoryEntry>emptyList()
                : copyHistory(worldHistory.recipeHistory);
    }

    public synchronized RecipeHistoryEntry lastViewedRecipeTree() {
        WorldHistoryData worldHistory = activeWorldHistory();
        return worldHistory == null ? null : copyHistoryEntry(worldHistory.lastViewedRecipeTree);
    }

    public synchronized void replaceRecipeHistory(List<RecipeHistoryEntry> history) {
        WorldHistoryData worldHistory = activeWorldHistory();
        replaceRecipeHistory(
                history,
                worldHistory == null ? null : worldHistory.lastViewedRecipeTree);
    }

    public synchronized void replaceRecipeHistory(
            List<RecipeHistoryEntry> history,
            RecipeHistoryEntry lastViewed) {
        if (activeWorldKey == null) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] Refusing to persist Recipe Tree history without an active "
                            + "world scope; this prevents one world's recent trees from leaking "
                            + "into another world");
            return;
        }
        List<RecipeHistoryEntry> replacement = boundedHistory(history);
        RecipeHistoryEntry replacementLastViewed = copyHistoryEntry(lastViewed);
        WorldHistoryData current = activeWorldHistory();
        if (current != null
                && current.recipeHistory.equals(replacement)
                && Objects.equals(current.lastViewedRecipeTree, replacementLastViewed)) {
            return;
        }
        data.worldHistories.put(
                activeWorldKey,
                new WorldHistoryData(replacement, replacementLastViewed));
        save();
    }

    /** Selects the save/server whose recent trees are exposed by the history API. */
    synchronized void setActiveWorld(String worldKey) {
        String normalized = normalizedWorldKey(worldKey);
        if (Objects.equals(activeWorldKey, normalized)) return;
        activeWorldKey = normalized;
        if (normalized == null) {
            JeiExportMod.LOGGER.debug("[jeiexport] Cleared the active Recipe Tree world scope");
        } else {
            JeiExportMod.LOGGER.debug(
                    "[jeiexport] Recipe Tree history is now scoped to {}",
                    normalized);
        }
    }

    private WorldHistoryData activeWorldHistory() {
        if (activeWorldKey == null) return null;
        return data.worldHistories.get(activeWorldKey);
    }

    private static String normalizedWorldKey(String worldKey) {
        if (worldKey == null) return null;
        String normalized = worldKey.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public synchronized boolean recipeBookMode() {
        return data.recipeBookMode;
    }

    public synchronized void setRecipeBookMode(boolean enabled) {
        if (data.recipeBookMode == enabled) return;
        data.recipeBookMode = enabled;
        save();
    }

    public synchronized boolean hasDiscovered(String discoveryIdentity) {
        return data.discoveries.contains(discoveryIdentity);
    }

    public synchronized Set<String> discoveries() {
        return new LinkedHashSet<String>(data.discoveries);
    }

    public synchronized int discover(Collection<String> discoveryIdentities) {
        if (discoveryIdentities == null) return 0;
        int added = 0;
        for (String discoveryIdentity : discoveryIdentities) {
            if (discoveryIdentity != null && data.discoveries.add(discoveryIdentity)) added++;
        }
        if (added > 0) save();
        return added;
    }

    private synchronized void save() {
        if (!writesEnabled) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] Recipe-tree progress changed in memory but cannot be written to {} "
                            + "because its existing state could not be loaded or migrated safely",
                    file);
            return;
        }
        writeAtomically(file, data);
    }

    private static RecipeTreeProgress load(Path file) {
        if (!Files.exists(file)) {
            return new RecipeTreeProgress(file, emptyData(), true);
        }
        if (!Files.isRegularFile(file)) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] Could not load or migrate recipe-tree progress from {}: "
                            + "the path is not a regular file; disk writes are disabled",
                    file);
            return new RecipeTreeProgress(file, emptyData(), false);
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new RecipeTreeProgress(file, deserialize(reader), true);
        } catch (Exception error) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] Could not load or migrate recipe-tree progress from {}; "
                            + "the original file is preserved and disk writes are disabled",
                    file,
                    error);
            return new RecipeTreeProgress(file, emptyData(), false);
        }
    }

    /** Package-private pure helper used by compatibility tests without touching Forge config. */
    static String serialize(StateData state) {
        StateEnvelope envelope = new StateEnvelope(
                SCHEMA_VERSION,
                MINECRAFT_VERSION,
                INGREDIENT_IDENTITY_VERSION,
                copyData(state));
        return GSON.toJson(envelope);
    }

    /** Package-private pure helper used by compatibility tests without touching Forge config. */
    static StateData deserialize(String json) throws IOException {
        if (json == null) throw new IOException("Recipe-tree progress JSON cannot be null");
        try {
            StateEnvelope envelope = GSON.fromJson(json, StateEnvelope.class);
            return validateEnvelope(envelope);
        } catch (JsonParseException error) {
            throw new IOException("Recipe-tree progress is not valid JSON", error);
        } catch (RuntimeException error) {
            throw new IOException("Recipe-tree progress is malformed", error);
        }
    }

    private static StateData deserialize(Reader reader) throws IOException {
        try {
            StateEnvelope envelope = GSON.fromJson(reader, StateEnvelope.class);
            return validateEnvelope(envelope);
        } catch (JsonParseException error) {
            throw new IOException("Recipe-tree progress is not valid JSON", error);
        } catch (RuntimeException error) {
            throw new IOException("Recipe-tree progress is malformed", error);
        }
    }

    private static StateData validateEnvelope(StateEnvelope envelope) throws IOException {
        if (envelope == null) throw new IOException("Recipe-tree progress root cannot be null");
        if (envelope.schemaVersion != SCHEMA_VERSION) {
            throw new IOException("Unsupported recipe-tree progress schemaVersion "
                    + envelope.schemaVersion + "; expected " + SCHEMA_VERSION);
        }
        if (!MINECRAFT_VERSION.equals(envelope.minecraftVersion)) {
            throw new IOException("Recipe-tree progress targets Minecraft "
                    + envelope.minecraftVersion + "; expected " + MINECRAFT_VERSION);
        }
        if (envelope.ingredientIdentityVersion != INGREDIENT_IDENTITY_VERSION) {
            throw new IOException("Unsupported recipe-tree ingredientIdentityVersion "
                    + envelope.ingredientIdentityVersion + "; expected "
                    + INGREDIENT_IDENTITY_VERSION);
        }
        if (envelope.data == null) {
            throw new IOException("Recipe-tree progress envelope does not contain data");
        }
        return copyData(envelope.data);
    }

    private static void writeAtomically(Path destination, StateData state) {
        Path parent = destination.getParent();
        Path temporary = destination.resolveSibling(destination.getFileName().toString() + ".tmp");
        try {
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(
                    temporary,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                writer.write(serialize(state));
            }
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                JeiExportMod.LOGGER.warn(
                        "[jeiexport] Atomic replacement is not supported for recipe-tree progress "
                                + "at {}; replacing it non-atomically",
                        destination,
                        unsupported);
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception error) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] Could not write recipe-tree progress to {}; the previous complete "
                            + "state is preserved when the filesystem permits",
                    destination,
                    error);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupError) {
                JeiExportMod.LOGGER.warn(
                        "[jeiexport] Could not remove failed recipe-tree progress temporary file {}",
                        temporary,
                        cleanupError);
            }
        }
    }

    private static StateData emptyData() {
        return new StateData(
                Collections.<String, SavedPlan>emptyMap(),
                Collections.<String, String>emptyMap(),
                Collections.<String, Boolean>emptyMap(),
                Collections.<RecipeHistoryEntry>emptyList(),
                null,
                false,
                Collections.<String>emptySet(),
                Collections.<String>emptySet(),
                Collections.<String, WorldHistoryData>emptyMap());
    }

    private static StateData copyData(StateData source) {
        if (source == null) return emptyData();
        return new StateData(
                copyPlans(source.plans),
                copyStringMap(source.favoriteRecipes),
                copyCollapsedTypes(source.collapsedRecipeTypes),
                boundedHistory(source.recipeHistory),
                copyHistoryEntry(source.lastViewedRecipeTree),
                source.recipeBookMode,
                copyStrings(source.discoveries),
                copyStrings(source.reusableInputs),
                copyWorldHistories(source.worldHistories));
    }

    private static Map<String, WorldHistoryData> copyWorldHistories(
            Map<String, WorldHistoryData> source) {
        Map<String, WorldHistoryData> copy = new LinkedHashMap<String, WorldHistoryData>();
        if (source == null) return copy;
        for (String key : sortedKeys(source)) {
            WorldHistoryData value = source.get(key);
            if (value != null) copy.put(key, copyWorldHistory(value));
        }
        return copy;
    }

    private static WorldHistoryData copyWorldHistory(WorldHistoryData source) {
        return source == null
                ? null
                : new WorldHistoryData(source.recipeHistory, source.lastViewedRecipeTree);
    }

    private static Map<String, SavedPlan> copyPlans(Map<String, SavedPlan> source) {
        Map<String, SavedPlan> copy = new LinkedHashMap<String, SavedPlan>();
        if (source == null) return copy;
        List<String> keys = sortedKeys(source);
        for (String key : keys) {
            SavedPlan value = source.get(key);
            if (value != null) copy.put(key, copyPlan(value));
        }
        return copy;
    }

    private static SavedPlan copyPlan(SavedPlan plan) {
        return plan == null ? null : new SavedPlan(plan.amount, plan.recipeIdentity);
    }

    private static Map<String, String> copyStringMap(Map<String, String> source) {
        Map<String, String> copy = new LinkedHashMap<String, String>();
        if (source == null) return copy;
        List<String> keys = sortedKeys(source);
        for (String key : keys) copy.put(key, source.get(key));
        return copy;
    }

    private static Map<String, Boolean> copyCollapsedTypes(Map<String, Boolean> source) {
        Map<String, Boolean> copy = new LinkedHashMap<String, Boolean>();
        if (source == null) return copy;
        List<String> keys = sortedKeys(source);
        for (String key : keys) {
            if (Boolean.TRUE.equals(source.get(key))) copy.put(key, true);
        }
        return copy;
    }

    private static Set<String> copyStrings(Collection<String> source) {
        Set<String> copy = new LinkedHashSet<String>();
        if (source == null) return copy;
        List<String> ordered = new ArrayList<String>();
        for (String value : source) if (value != null) ordered.add(value);
        Collections.sort(ordered);
        copy.addAll(ordered);
        return copy;
    }

    private static <V> List<String> sortedKeys(Map<String, V> source) {
        List<String> keys = new ArrayList<String>();
        for (String key : source.keySet()) if (key != null) keys.add(key);
        Collections.sort(keys);
        return keys;
    }

    private static List<RecipeHistoryEntry> boundedHistory(List<RecipeHistoryEntry> history) {
        List<RecipeHistoryEntry> copy = copyHistory(history);
        if (copy.size() <= MAX_HISTORY) return copy;
        return new ArrayList<RecipeHistoryEntry>(
                copy.subList(copy.size() - MAX_HISTORY, copy.size()));
    }

    private static List<RecipeHistoryEntry> copyHistory(List<RecipeHistoryEntry> history) {
        List<RecipeHistoryEntry> copy = new ArrayList<RecipeHistoryEntry>();
        if (history == null) return copy;
        for (RecipeHistoryEntry entry : history) {
            if (entry != null) copy.add(copyHistoryEntry(entry));
        }
        return copy;
    }

    private static RecipeHistoryEntry copyHistoryEntry(RecipeHistoryEntry entry) {
        return entry == null ? null : new RecipeHistoryEntry(
                entry.itemIdentity,
                entry.recipeIdentity,
                entry.amount,
                entry.compactMode,
                entry.treeDepth,
                entry.roots,
                entry.selections,
                entry.snapshot);
    }

    /** Last plan saved for one exact persistent ingredient identity. */
    public static final class SavedPlan {
        private final long amount;
        private final String recipeIdentity;

        public SavedPlan(long amount, String recipeIdentity) {
            this.amount = amount;
            this.recipeIdentity = recipeIdentity;
        }

        public long getAmount() {
            return amount;
        }

        public String getRecipeIdentity() {
            return recipeIdentity;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SavedPlan)) return false;
            SavedPlan that = (SavedPlan) other;
            return amount == that.amount && Objects.equals(recipeIdentity, that.recipeIdentity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(amount, recipeIdentity);
        }
    }

    /** One independently planned output at the leading edge of a saved graph. */
    public static final class RecipeHistoryRoot {
        private final String ingredientIdentity;
        private final String ingredientName;
        private final String recipeIdentity;
        private final long amount;

        public RecipeHistoryRoot(
                String ingredientIdentity,
                String ingredientName,
                String recipeIdentity,
                long amount) {
            this.ingredientIdentity = ingredientIdentity;
            this.ingredientName = ingredientName;
            this.recipeIdentity = recipeIdentity;
            this.amount = amount;
        }

        public String getIngredientIdentity() {
            return ingredientIdentity;
        }

        public String getIngredientName() {
            return ingredientName;
        }

        public String getRecipeIdentity() {
            return recipeIdentity;
        }

        public long getAmount() {
            return amount;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RecipeHistoryRoot)) return false;
            RecipeHistoryRoot that = (RecipeHistoryRoot) other;
            return amount == that.amount
                    && Objects.equals(ingredientIdentity, that.ingredientIdentity)
                    && Objects.equals(ingredientName, that.ingredientName)
                    && Objects.equals(recipeIdentity, that.recipeIdentity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ingredientIdentity, ingredientName, recipeIdentity, amount);
        }
    }

    /** Complete path-addressed node choice used to restore or compare a saved tree. */
    public static final class RecipeHistorySelection {
        private final int rootIndex;
        private final List<Integer> path;
        private final String ingredientIdentity;
        private final String ingredientName;
        private final String recipeIdentity;
        private final String recipeTypeIdentity;
        private final boolean reusableInput;

        public RecipeHistorySelection(
                int rootIndex,
                List<Integer> path,
                String ingredientIdentity,
                String ingredientName,
                String recipeIdentity,
                String recipeTypeIdentity) {
            this(rootIndex, path, ingredientIdentity, ingredientName, recipeIdentity,
                    recipeTypeIdentity, false);
        }

        public RecipeHistorySelection(
                int rootIndex,
                List<Integer> path,
                String ingredientIdentity,
                String ingredientName,
                String recipeIdentity,
                String recipeTypeIdentity,
                boolean reusableInput) {
            this.rootIndex = rootIndex;
            this.path = path == null
                    ? Collections.<Integer>emptyList()
                    : Collections.unmodifiableList(new ArrayList<Integer>(path));
            this.ingredientIdentity = ingredientIdentity;
            this.ingredientName = ingredientName;
            this.recipeIdentity = recipeIdentity;
            this.recipeTypeIdentity = recipeTypeIdentity;
            this.reusableInput = reusableInput;
        }

        public int getRootIndex() {
            return rootIndex;
        }

        public List<Integer> getPath() {
            return new ArrayList<Integer>(path);
        }

        public String getIngredientIdentity() {
            return ingredientIdentity;
        }

        public String getIngredientName() {
            return ingredientName;
        }

        public String getRecipeIdentity() {
            return recipeIdentity;
        }

        public String getRecipeTypeIdentity() {
            return recipeTypeIdentity;
        }

        public boolean isReusableInput() {
            return reusableInput;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RecipeHistorySelection)) return false;
            RecipeHistorySelection that = (RecipeHistorySelection) other;
            return rootIndex == that.rootIndex
                    && reusableInput == that.reusableInput
                    && Objects.equals(path, that.path)
                    && Objects.equals(ingredientIdentity, that.ingredientIdentity)
                    && Objects.equals(ingredientName, that.ingredientName)
                    && Objects.equals(recipeIdentity, that.recipeIdentity)
                    && Objects.equals(recipeTypeIdentity, that.recipeTypeIdentity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    rootIndex,
                    path,
                    ingredientIdentity,
                    ingredientName,
                    recipeIdentity,
                    recipeTypeIdentity,
                    reusableInput);
        }
    }

    /** Complete descriptor for one history item, including multi-root and snapshot state. */
    public static final class RecipeHistoryEntry {
        private final String itemIdentity;
        private final String recipeIdentity;
        private final long amount;
        private final boolean compactMode;
        private final int treeDepth;
        private final List<RecipeHistoryRoot> roots;
        private final List<RecipeHistorySelection> selections;
        private final boolean snapshot;

        public RecipeHistoryEntry(
                String itemIdentity,
                String recipeIdentity,
                long amount,
                boolean compactMode,
                int treeDepth,
                List<RecipeHistoryRoot> roots,
                List<RecipeHistorySelection> selections,
                boolean snapshot) {
            this.itemIdentity = itemIdentity;
            this.recipeIdentity = recipeIdentity;
            this.amount = amount;
            this.compactMode = compactMode;
            this.treeDepth = treeDepth;
            this.roots = roots == null
                    ? Collections.<RecipeHistoryRoot>emptyList()
                    : Collections.unmodifiableList(new ArrayList<RecipeHistoryRoot>(roots));
            this.selections = selections == null
                    ? Collections.<RecipeHistorySelection>emptyList()
                    : Collections.unmodifiableList(
                            new ArrayList<RecipeHistorySelection>(selections));
            this.snapshot = snapshot;
        }

        public String getItemIdentity() {
            return itemIdentity;
        }

        public String getRecipeIdentity() {
            return recipeIdentity;
        }

        public long getAmount() {
            return amount;
        }

        public boolean isCompactMode() {
            return compactMode;
        }

        public int getTreeDepth() {
            return treeDepth;
        }

        public List<RecipeHistoryRoot> getRoots() {
            return new ArrayList<RecipeHistoryRoot>(roots);
        }

        public List<RecipeHistorySelection> getSelections() {
            return new ArrayList<RecipeHistorySelection>(selections);
        }

        public boolean isSnapshot() {
            return snapshot;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RecipeHistoryEntry)) return false;
            RecipeHistoryEntry that = (RecipeHistoryEntry) other;
            return amount == that.amount
                    && compactMode == that.compactMode
                    && treeDepth == that.treeDepth
                    && snapshot == that.snapshot
                    && Objects.equals(itemIdentity, that.itemIdentity)
                    && Objects.equals(recipeIdentity, that.recipeIdentity)
                    && Objects.equals(roots, that.roots)
                    && Objects.equals(selections, that.selections);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    itemIdentity,
                    recipeIdentity,
                    amount,
                    compactMode,
                    treeDepth,
                    roots,
                    selections,
                    snapshot);
        }
    }

    /** Package-private DTO exposed only to serialization tests in this package. */
    static final class StateData {
        private Map<String, SavedPlan> plans;
        private Map<String, String> favoriteRecipes;
        private Map<String, Boolean> collapsedRecipeTypes;
        private List<RecipeHistoryEntry> recipeHistory;
        private RecipeHistoryEntry lastViewedRecipeTree;
        private boolean recipeBookMode;
        private Set<String> discoveries;
        private Set<String> reusableInputs;
        private Map<String, WorldHistoryData> worldHistories;

        StateData(
                Map<String, SavedPlan> plans,
                Map<String, String> favoriteRecipes,
                Map<String, Boolean> collapsedRecipeTypes,
                List<RecipeHistoryEntry> recipeHistory,
                RecipeHistoryEntry lastViewedRecipeTree,
                boolean recipeBookMode,
                Set<String> discoveries,
                Set<String> reusableInputs) {
            this(
                    plans,
                    favoriteRecipes,
                    collapsedRecipeTypes,
                    recipeHistory,
                    lastViewedRecipeTree,
                    recipeBookMode,
                    discoveries,
                    reusableInputs,
                    Collections.<String, WorldHistoryData>emptyMap());
        }

        StateData(
                Map<String, SavedPlan> plans,
                Map<String, String> favoriteRecipes,
                Map<String, Boolean> collapsedRecipeTypes,
                List<RecipeHistoryEntry> recipeHistory,
                RecipeHistoryEntry lastViewedRecipeTree,
                boolean recipeBookMode,
                Set<String> discoveries,
                Set<String> reusableInputs,
                Map<String, WorldHistoryData> worldHistories) {
            this.plans = copyPlans(plans);
            this.favoriteRecipes = copyStringMap(favoriteRecipes);
            this.collapsedRecipeTypes = copyCollapsedTypes(collapsedRecipeTypes);
            this.recipeHistory = boundedHistory(recipeHistory);
            this.lastViewedRecipeTree = copyHistoryEntry(lastViewedRecipeTree);
            this.recipeBookMode = recipeBookMode;
            this.discoveries = copyStrings(discoveries);
            this.reusableInputs = copyStrings(reusableInputs);
            this.worldHistories = copyWorldHistories(worldHistories);
        }

        Map<String, SavedPlan> plans() {
            return copyPlans(plans);
        }

        Map<String, String> favoriteRecipes() {
            return copyStringMap(favoriteRecipes);
        }

        Map<String, Boolean> collapsedRecipeTypes() {
            return copyCollapsedTypes(collapsedRecipeTypes);
        }

        List<RecipeHistoryEntry> recipeHistory() {
            return copyHistory(recipeHistory);
        }

        RecipeHistoryEntry lastViewedRecipeTree() {
            return copyHistoryEntry(lastViewedRecipeTree);
        }

        boolean recipeBookMode() {
            return recipeBookMode;
        }

        Set<String> discoveries() {
            return copyStrings(discoveries);
        }

        Set<String> reusableInputs() {
            return copyStrings(reusableInputs);
        }

        Map<String, WorldHistoryData> worldHistories() {
            return copyWorldHistories(worldHistories);
        }
    }

    /** Recent Recipe Tree state belonging to one singleplayer save or multiplayer server. */
    static final class WorldHistoryData {
        private List<RecipeHistoryEntry> recipeHistory;
        private RecipeHistoryEntry lastViewedRecipeTree;

        WorldHistoryData(
                List<RecipeHistoryEntry> recipeHistory,
                RecipeHistoryEntry lastViewedRecipeTree) {
            this.recipeHistory = boundedHistory(recipeHistory);
            this.lastViewedRecipeTree = copyHistoryEntry(lastViewedRecipeTree);
        }

        List<RecipeHistoryEntry> recipeHistory() {
            return copyHistory(recipeHistory);
        }

        RecipeHistoryEntry lastViewedRecipeTree() {
            return copyHistoryEntry(lastViewedRecipeTree);
        }
    }

    private static final class StateEnvelope {
        private final int schemaVersion;
        private final String minecraftVersion;
        private final int ingredientIdentityVersion;
        private final StateData data;

        private StateEnvelope(
                int schemaVersion,
                String minecraftVersion,
                int ingredientIdentityVersion,
                StateData data) {
            this.schemaVersion = schemaVersion;
            this.minecraftVersion = minecraftVersion;
            this.ingredientIdentityVersion = ingredientIdentityVersion;
            this.data = data;
        }
    }
}
