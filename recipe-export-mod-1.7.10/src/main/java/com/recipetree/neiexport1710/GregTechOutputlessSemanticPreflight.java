package com.recipetree.neiexport1710;

import bartworks.API.recipe.RadioHatchFrontend;
import codechicken.nei.ItemList;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.recipe.NEIRecipeProperties;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapFrontend;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.recipe.maps.FuelBackend;
import gregtech.api.recipe.maps.LargeBoilerFuelBackend;
import gregtech.api.recipe.maps.LargeBoilerFuelFrontend;
import gregtech.api.recipe.maps.QuantumComputerFrontend;
import gregtech.api.recipe.maps.SpaceProjectFrontend;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.recipe.QuantumComputerRecipeData;
import gregtech.api.util.recipe.Sievert;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gregtech.nei.GTNEIDefaultHandler;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Full, pre-render audit of outputless rows exposed by the pinned GregTech NEI handler.
 *
 * <p>The snapshot is deliberately tied to {@code categoryId + sourceIndex} for same-run
 * export-time verification, so a row cannot change between discovery and rendering. The promoted
 * corpus fingerprint is a separate, order-independent multiset digest: source indices are runtime
 * bindings, not semantic facts. No class-name or reflective fallback is used.</p>
 */
final class GregTechOutputlessSemanticPreflight {
    static final String CONTRACT = "gregtech-outputless-semantic-preflight-v2";

    static final int EXPECTED_SCANNED_GREGTECH_CATEGORIES = 148;
    static final int EXPECTED_SCANNED_GREGTECH_RECIPES = 162842;
    static final int EXPECTED_FUEL_SINK_RECIPES = 289;
    static final int EXPECTED_FUEL_SINK_CATEGORIES = 14;
    static final int EXPECTED_LARGE_BOILER_FUEL_SINK_RECIPES = 49;
    static final int EXPECTED_LARGE_BOILER_FUEL_SINK_CATEGORIES = 1;
    static final int EXPECTED_RADIO_HATCH_INFORMATION_RECIPES = 104;
    static final int EXPECTED_QUANTUM_COMPONENT_INFORMATION_RECIPES = 27;
    static final int EXPECTED_SPACE_PROJECT_INFORMATION_RECIPES = 2;
    static final int EXPECTED_SEMANTIC_CATEGORIES = 18;
    static final int EXPECTED_SEMANTIC_RECIPES = 471;
    static final int EXPECTED_EXCLUDED_LARGE_BOILER_PRESENTATION_ROWS = 1;
    static final int EXPECTED_RECORDED_ROWS = 472;
    static final String EXPECTED_SHA256 =
            "7950c0741cb841a857428e327f407d0c8303954b0d6aa7a36a9189e30ea350f9";

    static final String UNREGISTERED_DOOR_RECYCLING_CONTRACT =
            "gregtech-unregistered-itemdoor-recycling-exclusion-v1";
    static final int EXPECTED_UNREGISTERED_DOOR_RECYCLING_ROWS = 5;
    static final int EXPECTED_UNREGISTERED_DOOR_RECYCLING_CATEGORIES = 3;
    static final String EXPECTED_UNREGISTERED_DOOR_RECYCLING_SHA256 =
            "9724fc0858ae37da34cfd09c87fee0507c7588f794ecf0bc8c2f0cda9dd48815";

    private static final String RADIO_HATCH_MAP = "bw.recipe.radhatch";
    private static final String QUANTUM_COMPUTER_MAP = "gt.recipe.quantumcomputer";
    private static final String SPACE_PROJECT_MAP = "gt.recipe.fakespaceprojects";
    private static final String LARGE_BOILER_MAP = "gt.recipe.largeboilerfakefuels";

    enum Classification {
        GREGTECH_FUEL_SINK(false),
        LARGE_BOILER_FUEL_SINK(false),
        LARGE_BOILER_PRESENTATION_EXCLUDED(true),
        RADIO_HATCH_INFORMATION(false),
        QUANTUM_COMPONENT_INFORMATION(false),
        SPACE_PROJECT_INFORMATION(false);

        final boolean excludedFromExport;

        Classification(boolean excludedFromExport) {
            this.excludedFromExport = excludedFromExport;
        }
    }

    /** Immutable result for one outputless source row. */
    static final class Decision {
        final String categoryId;
        final int sourceIndex;
        final Classification classification;
        final String fingerprint;
        private final String canonicalFacts;

        private Decision(String categoryId, int sourceIndex,
                         Classification classification, String canonicalFacts) {
            this.categoryId = categoryId;
            this.sourceIndex = sourceIndex;
            this.classification = classification;
            this.canonicalFacts = canonicalFacts;
            this.fingerprint = Naming.sha256(canonicalFacts);
        }

        boolean excludedFromExport() {
            return classification.excludedFromExport;
        }
    }

    /** One non-addressable recycling row deliberately omitted from the public recipe graph. */
    static final class GraphIdentityDecision {
        final String categoryId;
        final int sourceIndex;
        final String mapName;
        final String doorKind;
        final String fingerprint;
        private final String canonicalFacts;

        private GraphIdentityDecision(
                String categoryId, int sourceIndex, String mapName,
                String doorKind, String canonicalFacts) {
            this.categoryId = categoryId;
            this.sourceIndex = sourceIndex;
            this.mapName = mapName;
            this.doorKind = doorKind;
            this.canonicalFacts = canonicalFacts;
            this.fingerprint = Naming.sha256(canonicalFacts);
        }
    }

    private static final class DoorReplacementEvidence {
        final String canonicalFacts;

        DoorReplacementEvidence(String canonicalFacts) {
            this.canonicalFacts = canonicalFacts;
        }
    }

    /** Immutable output of the complete preflight. */
    static final class Snapshot {
        private final SortedMap<SourceKey, Decision> decisions;
        private final EnumMap<Classification, Integer> counts;
        private final EnumMap<Classification, Integer> categoryCounts;
        private final int scannedGregTechCategories;
        private final int scannedGregTechRecipes;
        private final int distinctCategories;
        private final String fingerprint;
        private final SortedMap<SourceKey, GraphIdentityDecision> graphIdentityExclusions;
        private final String graphIdentityExclusionFingerprint;
        private final int graphIdentityExclusionCategories;
        /** Client-thread-only cache; one O(map recipes) identity index per loaded category. */
        private final IdentityHashMap<ICraftingHandler, CategoryBinding> exportBindings =
                new IdentityHashMap<ICraftingHandler, CategoryBinding>();
        private final Set<SourceKey> consumedGraphIdentityExclusions =
                new HashSet<SourceKey>();

        private Snapshot(SortedMap<SourceKey, Decision> decisions,
                         SortedMap<SourceKey, GraphIdentityDecision> graphIdentityExclusions,
                         List<CategoryAudit> categories,
                         int scannedGregTechCategories,
                         int scannedGregTechRecipes) {
            this.decisions = Collections.unmodifiableSortedMap(
                    new TreeMap<SourceKey, Decision>(decisions));
            this.scannedGregTechCategories = scannedGregTechCategories;
            this.scannedGregTechRecipes = scannedGregTechRecipes;
            this.graphIdentityExclusions = Collections.unmodifiableSortedMap(
                    new TreeMap<SourceKey, GraphIdentityDecision>(graphIdentityExclusions));
            Set<String> graphCategoryIds = new HashSet<String>();
            List<String> graphTokens = new ArrayList<String>(graphIdentityExclusions.size());
            for (GraphIdentityDecision exclusion : graphIdentityExclusions.values()) {
                graphCategoryIds.add(exclusion.categoryId);
                graphTokens.add(exclusion.canonicalFacts);
            }
            this.graphIdentityExclusionCategories = graphCategoryIds.size();
            this.graphIdentityExclusionFingerprint =
                    stableGraphIdentityExclusionFingerprint(graphTokens);
            this.counts = new EnumMap<Classification, Integer>(Classification.class);
            this.categoryCounts = new EnumMap<Classification, Integer>(Classification.class);

            EnumMap<Classification, Set<String>> categoryIds =
                    new EnumMap<Classification, Set<String>>(Classification.class);
            Set<String> allCategoryIds = new HashSet<String>();
            for (Classification classification : Classification.values()) {
                counts.put(classification, 0);
                categoryIds.put(classification, new HashSet<String>());
            }
            for (Decision decision : decisions.values()) {
                counts.put(decision.classification, counts.get(decision.classification) + 1);
                categoryIds.get(decision.classification).add(decision.categoryId);
                allCategoryIds.add(decision.categoryId);
            }
            for (Classification classification : Classification.values()) {
                categoryCounts.put(classification, categoryIds.get(classification).size());
            }
            distinctCategories = allCategoryIds.size();

            List<String> categoryTokens = new ArrayList<String>(categories.size());
            for (CategoryAudit category : categories) {
                categoryTokens.add(categoryAuditToken(category));
            }
            List<String> semanticRowTokens = new ArrayList<String>(decisions.size());
            for (Decision decision : decisions.values()) {
                semanticRowTokens.add(corpusSemanticRowToken(decision));
            }
            StringBuilder canonical = new StringBuilder(16384);
            frame(canonical, "contract", CONTRACT);
            frame(canonical, "scannedGregTechCategories", scannedGregTechCategories);
            frame(canonical, "scannedGregTechRecipes", scannedGregTechRecipes);
            frame(canonical, "categoryAuditMultisetSha256", stableMultisetFingerprint(
                    "gregtech-category-audits-v2", categoryTokens));
            frame(canonical, "decisionCount", decisions.size());
            frame(canonical, "semanticRowMultisetSha256", stableMultisetFingerprint(
                    "gregtech-semantic-rows-v2", semanticRowTokens));
            fingerprint = Naming.sha256(canonical.toString());
        }

        Decision lookup(String categoryId, int sourceIndex) {
            if (categoryId == null || sourceIndex < 0) {
                return null;
            }
            return decisions.get(new SourceKey(categoryId, sourceIndex));
        }

        GraphIdentityDecision lookupGraphIdentityExclusion(
                String categoryId, int sourceIndex) {
            if (categoryId == null || sourceIndex < 0) {
                return null;
            }
            return graphIdentityExclusions.get(new SourceKey(categoryId, sourceIndex));
        }

        /**
         * Rebinds a source row to the loaded handler and compares its current classification and
         * canonical facts with the preflight snapshot. Returns {@code null} for an ordinary
         * output-bearing row.
         */
        Decision verify(HandlerCategoryPlan plan, ICraftingHandler loaded, int sourceIndex)
                throws ExportFailure {
            if (plan == null || loaded == null || sourceIndex < 0) {
                throw failure("invalid export-time source binding: plan="
                        + (plan == null ? "<null>" : plan.categoryId)
                        + ", handler=" + (loaded == null ? "<null>"
                        : loaded.getClass().getName()) + ", sourceIndex=" + sourceIndex);
            }
            SourceKey key = new SourceKey(plan.categoryId, sourceIndex);
            Decision expected = decisions.get(key);
            if (plan.prototype == null
                    || plan.prototype.getClass() != GTNEIDefaultHandler.class) {
                if (expected != null) {
                    throw failure("snapshotted GregTech source rebound to a non-GregTech plan: "
                            + key);
                }
                return null;
            }
            try {
                CategoryBinding category = exportBinding(plan, loaded);
                RowObservation row = observeRow(category, sourceIndex);
                if (!row.neiOutputless) {
                    if (expected != null) {
                        throw failure("snapshotted outputless source became output-bearing: "
                                + key + "; expected=" + expected.classification);
                    }
                    return null;
                }
                Classification currentClassification = classify(category, row);
                Decision current = decision(category, row, currentClassification);
                if (expected == null) {
                    throw failure("new outputless GregTech source appeared after preflight: "
                            + key + "; classification=" + currentClassification
                            + ", fingerprint=" + current.fingerprint);
                }
                if (expected.classification != current.classification) {
                    throw failure("outputless GregTech classification drifted for " + key
                            + "; expected=" + expected.classification
                            + ", current=" + current.classification);
                }
                if (!expected.fingerprint.equals(current.fingerprint)
                        || !expected.canonicalFacts.equals(current.canonicalFacts)) {
                    throw failure("outputless GregTech semantic fingerprint drifted for " + key
                            + "; expected=" + expected.fingerprint
                            + ", current=" + current.fingerprint);
                }
                return expected;
            } catch (ExportFailure failure) {
                throw failure;
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "GregTech outputless export-time verification for " + key, error);
            }
        }

        GraphIdentityDecision verifyGraphIdentityExclusion(
                HandlerCategoryPlan plan, ICraftingHandler loaded, int sourceIndex)
                throws ExportFailure {
            if (plan == null || loaded == null || sourceIndex < 0) {
                throw graphFailure("invalid export-time exclusion binding: plan="
                        + (plan == null ? "<null>" : plan.categoryId)
                        + ", handler=" + className(loaded)
                        + ", sourceIndex=" + sourceIndex);
            }
            SourceKey key = new SourceKey(plan.categoryId, sourceIndex);
            GraphIdentityDecision expected = graphIdentityExclusions.get(key);
            if (expected == null) {
                return null;
            }
            try {
                CategoryBinding category = exportBinding(plan, loaded);
                RowObservation row = observeRow(category, sourceIndex);
                GraphIdentityAudit audit = new GraphIdentityAudit(false);
                validateVisibleGraphIdentities(category, row, audit);
                GraphIdentityDecision current = audit.exclusions.get(key);
                if (audit.failureCount != 0 || audit.exclusions.size() != 1
                        || current == null) {
                    throw graphFailure("excluded source no longer has exactly one approved "
                            + "unregistered ItemDoor input: " + key
                            + "; unclassified=" + audit.failureCount
                            + "; approved=" + audit.exclusions.size());
                }
                if (!expected.fingerprint.equals(current.fingerprint)
                        || !expected.canonicalFacts.equals(current.canonicalFacts)) {
                    throw graphFailure("excluded recycling row fingerprint drifted for " + key
                            + "; expected=" + expected.fingerprint
                            + ", current=" + current.fingerprint);
                }
                if (!consumedGraphIdentityExclusions.add(key)) {
                    throw graphFailure("excluded recycling row was consumed twice: " + key);
                }
                return expected;
            } catch (ExportFailure failure) {
                throw failure;
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                throw new ExportFailure("ITEM_IDENTITY",
                        "GregTech unregistered-door export-time verification for " + key,
                        error);
            }
        }

        private CategoryBinding exportBinding(
                HandlerCategoryPlan plan, ICraftingHandler loaded) throws ExportFailure {
            CategoryBinding existing = exportBindings.get(loaded);
            if (existing == null) {
                CategoryBinding created = bindCategory(plan, loaded);
                exportBindings.put(loaded, created);
                return created;
            }
            if (existing.loaded != loaded || existing.plan != plan
                    || !existing.plan.categoryId.equals(plan.categoryId)
                    || existing.prototype != plan.prototype) {
                throw failure("export-time loaded-handler identity was reused across category "
                        + "bindings; cached=" + existing.plan.categoryId
                        + ", requested=" + plan.categoryId);
            }
            return existing;
        }

        int count(Classification classification) {
            Integer value = counts.get(classification);
            return value == null ? 0 : value;
        }

        int distinctCategoryCount(Classification classification) {
            Integer value = categoryCounts.get(classification);
            return value == null ? 0 : value;
        }

        int semanticRecipes() {
            return decisions.size() - count(Classification.LARGE_BOILER_PRESENTATION_EXCLUDED);
        }

        int excludedPresentationRows() {
            return count(Classification.LARGE_BOILER_PRESENTATION_EXCLUDED);
        }

        int recordedRows() {
            return decisions.size();
        }

        int distinctCategories() {
            return distinctCategories;
        }

        int scannedGregTechCategories() {
            return scannedGregTechCategories;
        }

        int scannedGregTechRecipes() {
            return scannedGregTechRecipes;
        }

        String sha256() {
            return fingerprint;
        }

        int excludedUnregisteredDoorRecyclingRows() {
            return graphIdentityExclusions.size();
        }

        int excludedUnregisteredDoorRecyclingCategories() {
            return graphIdentityExclusionCategories;
        }

        String unregisteredDoorRecyclingSha256() {
            return graphIdentityExclusionFingerprint;
        }

        void requireAllGraphIdentityExclusionsConsumed() throws ExportFailure {
            if (consumedGraphIdentityExclusions.size() == graphIdentityExclusions.size()
                    && consumedGraphIdentityExclusions.containsAll(
                            graphIdentityExclusions.keySet())) {
                return;
            }
            Set<SourceKey> missing = new HashSet<SourceKey>(
                    graphIdentityExclusions.keySet());
            missing.removeAll(consumedGraphIdentityExclusions);
            throw graphFailure("export traversal did not consume every promoted exclusion; "
                    + "expected=" + graphIdentityExclusions.size()
                    + ", consumed=" + consumedGraphIdentityExclusions.size()
                    + ", missing=" + missing);
        }
    }

    static final class SourceKey implements Comparable<SourceKey> {
        final String categoryId;
        final int sourceIndex;

        SourceKey(String categoryId, int sourceIndex) {
            if (categoryId == null) {
                throw new IllegalArgumentException("categoryId is required");
            }
            this.categoryId = categoryId;
            this.sourceIndex = sourceIndex;
        }

        @Override
        public int compareTo(SourceKey other) {
            int categoryOrder = categoryId.compareTo(other.categoryId);
            return categoryOrder != 0 ? categoryOrder
                    : Integer.compare(sourceIndex, other.sourceIndex);
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof SourceKey)) {
                return false;
            }
            SourceKey other = (SourceKey) value;
            return sourceIndex == other.sourceIndex && categoryId.equals(other.categoryId);
        }

        @Override
        public int hashCode() {
            return 31 * categoryId.hashCode() + sourceIndex;
        }

        @Override
        public String toString() {
            return categoryId + "#" + sourceIndex;
        }
    }

    private static final class CategoryAudit {
        final String categoryId;
        final String fingerprint;

        CategoryAudit(String categoryId, String fingerprint) {
            this.categoryId = categoryId;
            this.fingerprint = fingerprint;
        }
    }

    private static final class CategoryBinding {
        final HandlerCategoryPlan plan;
        final GTNEIDefaultHandler prototype;
        final GTNEIDefaultHandler loaded;
        final RecipeMap<?> map;
        final RecipeMapBackend backend;
        final RecipeMapFrontend frontend;
        final NEIRecipeProperties neiProperties;
        final IdentityHashMap<GTRecipe, Boolean> mapRecipes;

        CategoryBinding(HandlerCategoryPlan plan,
                        GTNEIDefaultHandler prototype,
                        GTNEIDefaultHandler loaded,
                        RecipeMap<?> map,
                        RecipeMapBackend backend,
                        RecipeMapFrontend frontend,
                        NEIRecipeProperties neiProperties,
                        IdentityHashMap<GTRecipe, Boolean> mapRecipes) {
            this.plan = plan;
            this.prototype = prototype;
            this.loaded = loaded;
            this.map = map;
            this.backend = backend;
            this.frontend = frontend;
            this.neiProperties = neiProperties;
            this.mapRecipes = mapRecipes;
        }
    }

    private static final class RowObservation {
        final int sourceIndex;
        final GTNEIDefaultHandler.CachedDefaultRecipe cached;
        final GTRecipe recipe;
        final List<PositionedStack> ingredients;
        final List<PositionedStack> otherStacks;
        final PositionedStack result;
        final boolean neiOutputless;
        final int rawItemInputs;
        final int rawFluidInputs;
        final int rawItemOutputs;
        final int rawFluidOutputs;
        final List<String> visibleInputFacts;

        RowObservation(int sourceIndex,
                       GTNEIDefaultHandler.CachedDefaultRecipe cached,
                       GTRecipe recipe,
                       List<PositionedStack> ingredients,
                       List<PositionedStack> otherStacks,
                       PositionedStack result,
                       int rawItemInputs,
                       int rawFluidInputs,
                       int rawItemOutputs,
                       int rawFluidOutputs,
                       List<String> visibleInputFacts) {
            this.sourceIndex = sourceIndex;
            this.cached = cached;
            this.recipe = recipe;
            this.ingredients = ingredients;
            this.otherStacks = otherStacks;
            this.result = result;
            this.neiOutputless = result == null && otherStacks.isEmpty();
            this.rawItemInputs = rawItemInputs;
            this.rawFluidInputs = rawFluidInputs;
            this.rawItemOutputs = rawItemOutputs;
            this.rawFluidOutputs = rawFluidOutputs;
            this.visibleInputFacts = visibleInputFacts;
        }

        int rawInputs() {
            return rawItemInputs + rawFluidInputs;
        }

        boolean hasRawAndNeiInputs() {
            return rawInputs() > 0 && !ingredients.isEmpty();
        }

        boolean hasNoRawOutputs() {
            return rawItemOutputs == 0 && rawFluidOutputs == 0;
        }
    }

    /**
     * Bounded fail-closed inventory of graph identities which cannot be canonicalized.
     *
     * <p>The outputless audit already walks every exact GregTech NEI row before any catalog or
     * PNG work. Reusing that traversal makes identity failures deterministic and cheap to
     * diagnose instead of discovering them late in a multi-gigabyte render.</p>
     */
    private static final class GraphIdentityAudit {
        private static final int MAX_RECORDED_FAILURES = 128;

        final List<String> failures = new ArrayList<String>();
        final SortedMap<SourceKey, GraphIdentityDecision> exclusions =
                new TreeMap<SourceKey, GraphIdentityDecision>();
        final boolean log;
        int failureCount;

        GraphIdentityAudit(boolean log) {
            this.log = log;
        }

        void record(String message) {
            failureCount++;
            if (failures.size() < MAX_RECORDED_FAILURES) {
                failures.add(message);
            }
            if (log) {
                GtnhNeiExportMod.LOGGER.error(
                        "[gtnh-nei-export] GregTech recipe graph identity preflight failure {}",
                        message);
            }
        }

        void record(CategoryBinding category, RowObservation row, String role,
                    int slotIndex, int alternativeIndex, ItemStack stack, Throwable error) {
            try {
                GraphIdentityDecision exclusion = classifyUnregisteredDoorRecyclingExclusion(
                        category, row, role, slotIndex, alternativeIndex, stack, error);
                if (exclusion == null) {
                    record(graphIdentityFailureFacts(
                            category, row, role, slotIndex, alternativeIndex, stack, error));
                    return;
                }
                SourceKey key = new SourceKey(exclusion.categoryId, exclusion.sourceIndex);
                if (exclusions.put(key, exclusion) != null) {
                    record("duplicate approved graph-identity exclusion binding " + key);
                    return;
                }
                if (log) {
                    GtnhNeiExportMod.LOGGER.warn(
                            "[gtnh-nei-export] Discovered exact non-addressable GregTech "
                                    + "ItemDoor recycling row categoryId={} sourceIndex={} "
                                    + "fingerprint={} canonicalFacts={} diagnosticFacts={}",
                            exclusion.categoryId, exclusion.sourceIndex,
                            exclusion.fingerprint,
                            exclusion.canonicalFacts,
                            graphIdentityFailureFacts(
                                    category, row, role, slotIndex,
                                    alternativeIndex, stack, error));
                }
            } catch (Throwable classificationError) {
                FatalErrors.rethrowIfFatal(classificationError);
                record(graphIdentityFailureFacts(
                        category, row, role, slotIndex, alternativeIndex, stack,
                        classificationError));
            }
        }

        void requireClean() throws ExportFailure {
            if (failureCount == 0) {
                return;
            }
            int omitted = failureCount - failures.size();
            StringBuilder message = new StringBuilder(4096);
            message.append("GregTech recipe graph identity preflight found ")
                    .append(failureCount).append(" noncanonical alternative(s); recorded=")
                    .append(failures.size()).append("; omitted=").append(omitted);
            for (int index = 0; index < failures.size(); index++) {
                message.append("; failure[").append(index).append("]={")
                        .append(failures.get(index)).append('}');
            }
            throw new ExportFailure("ITEM_IDENTITY", message.toString());
        }

        void requirePromotedExclusions() throws ExportFailure {
            Set<String> categories = new HashSet<String>();
            List<String> tokens = new ArrayList<String>(exclusions.size());
            int macerator = 0;
            int arcFurnace = 0;
            int fluidExtractor = 0;
            int woodenDoor = 0;
            int ironDoor = 0;
            for (GraphIdentityDecision exclusion : exclusions.values()) {
                categories.add(exclusion.categoryId);
                tokens.add(exclusion.canonicalFacts);
                if ("gt.recipe.macerator".equals(exclusion.mapName)) {
                    macerator++;
                } else if ("gt.recipe.arcfurnace".equals(exclusion.mapName)) {
                    arcFurnace++;
                } else if ("gt.recipe.fluidextractor".equals(exclusion.mapName)) {
                    fluidExtractor++;
                }
                if ("wood".equals(exclusion.doorKind)) {
                    woodenDoor++;
                } else if ("iron".equals(exclusion.doorKind)) {
                    ironDoor++;
                }
            }
            String sha256 = stableGraphIdentityExclusionFingerprint(tokens);
            if (exclusions.size() != EXPECTED_UNREGISTERED_DOOR_RECYCLING_ROWS
                    || categories.size()
                    != EXPECTED_UNREGISTERED_DOOR_RECYCLING_CATEGORIES
                    || macerator != 2 || arcFurnace != 2 || fluidExtractor != 1
                    || woodenDoor != 2 || ironDoor != 3) {
                throw graphFailure("unregistered ItemDoor recycling coverage drifted; rows="
                        + exclusions.size() + ", categories=" + categories.size()
                        + ", maps=macerator:" + macerator + "/arcFurnace:" + arcFurnace
                        + "/fluidExtractor:" + fluidExtractor
                        + ", doors=wood:" + woodenDoor + "/iron:" + ironDoor
                        + ", sha256=" + sha256);
            }
            if (!EXPECTED_UNREGISTERED_DOOR_RECYCLING_SHA256.equals(sha256)) {
                throw graphFailure("unregistered ItemDoor recycling corpus is unpromoted or "
                        + "drifted; expectedSha256="
                        + EXPECTED_UNREGISTERED_DOOR_RECYCLING_SHA256
                        + ", observedSha256=" + sha256 + ", rows=" + exclusions.size()
                        + ", categories=" + categories.size());
            }
        }
    }

    private GregTechOutputlessSemanticPreflight() {
    }

    static Snapshot preflight(List<HandlerCategoryPlan> plans) throws ExportFailure {
        if (plans == null || plans.isEmpty()) {
            throw failure("no category plans were supplied");
        }
        SortedMap<SourceKey, Decision> decisions = new TreeMap<SourceKey, Decision>();
        List<CategoryAudit> categoryAudits = new ArrayList<CategoryAudit>();
        GraphIdentityAudit graphIdentityAudit = new GraphIdentityAudit(true);
        int scannedCategories = 0;
        int scannedRecipes = 0;

        for (int planIndex = 0; planIndex < plans.size(); planIndex++) {
            HandlerCategoryPlan plan = plans.get(planIndex);
            if (plan == null || plan.prototype == null
                    || plan.prototype.getClass() != GTNEIDefaultHandler.class) {
                continue;
            }
            try {
                ICraftingHandler loaded = plan.loadCompleteCategory();
                CategoryBinding category = bindCategory(plan, loaded);
                int recipeCount = category.loaded.arecipes.size();
                int outputlessCount = 0;
                int legendCount = 0;
                EnumMap<Classification, Integer> categoryCounts =
                        new EnumMap<Classification, Integer>(Classification.class);
                for (Classification classification : Classification.values()) {
                    categoryCounts.put(classification, 0);
                }
                StringBuilder categoryCanonical = new StringBuilder(1024);
                appendCategoryFacts(categoryCanonical, category, recipeCount);
                List<String> semanticRowTokens = new ArrayList<String>();

                for (int sourceIndex = 0; sourceIndex < recipeCount; sourceIndex++) {
                    RowObservation row = observeRow(category, sourceIndex);
                    validateVisibleGraphIdentities(category, row, graphIdentityAudit);
                    if (!row.neiOutputless) {
                        continue;
                    }
                    outputlessCount++;
                    Classification classification = classify(category, row);
                    Decision decision = decision(category, row, classification);
                    SourceKey key = new SourceKey(plan.categoryId, sourceIndex);
                    if (decisions.put(key, decision) != null) {
                        throw failure("duplicate deterministic GregTech source binding " + key);
                    }
                    categoryCounts.put(classification, categoryCounts.get(classification) + 1);
                    if (classification == Classification.LARGE_BOILER_PRESENTATION_EXCLUDED) {
                        legendCount++;
                    }
                    semanticRowTokens.add(categorySemanticRowToken(decision));
                }

                if (category.backend.getClass() == LargeBoilerFuelBackend.class
                        && legendCount != 1) {
                    throw failure(plan.categoryId + " map=" + category.map.unlocalizedName
                            + " must expose exactly one shaped large-boiler presentation row; got "
                            + legendCount);
                }
                frame(categoryCanonical, "semanticRowMultisetSha256", stableMultisetFingerprint(
                        "gregtech-category-semantic-rows-v2", semanticRowTokens));
                frame(categoryCanonical, "outputlessRows", outputlessCount);
                String categoryFingerprint = Naming.sha256(categoryCanonical.toString());
                categoryAudits.add(new CategoryAudit(plan.categoryId, categoryFingerprint));
                scannedCategories++;
                scannedRecipes += recipeCount;

                GtnhNeiExportMod.LOGGER.info(
                        "[gtnh-nei-export] GregTech outputless semantic category summary "
                                + "plan={}/{} categoryId={} map={} recipes={} outputless={} "
                                + "fuel={} largeBoilerFuel={} largeBoilerExcluded={} radio={} "
                                + "quantum={} space={} fingerprint={}",
                        planIndex + 1, plans.size(), plan.categoryId,
                        category.map.unlocalizedName, recipeCount, outputlessCount,
                        categoryCounts.get(Classification.GREGTECH_FUEL_SINK),
                        categoryCounts.get(Classification.LARGE_BOILER_FUEL_SINK),
                        categoryCounts.get(Classification.LARGE_BOILER_PRESENTATION_EXCLUDED),
                        categoryCounts.get(Classification.RADIO_HATCH_INFORMATION),
                        categoryCounts.get(Classification.QUANTUM_COMPONENT_INFORMATION),
                        categoryCounts.get(Classification.SPACE_PROJECT_INFORMATION),
                        categoryFingerprint);
            } catch (ExportFailure failure) {
                GtnhNeiExportMod.LOGGER.error(
                        "[gtnh-nei-export] GregTech outputless semantic category preflight failed "
                                + "categoryId={} planIndex={}/{}: {}",
                        plan.categoryId, planIndex + 1, plans.size(), failure.getMessage());
                throw failure;
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                GtnhNeiExportMod.LOGGER.error(
                        "[gtnh-nei-export] GregTech outputless semantic category preflight failed "
                                + "categoryId={} planIndex={}/{}",
                        plan.categoryId, planIndex + 1, plans.size(), error);
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "GregTech outputless category preflight for " + plan.categoryId, error);
            }
        }

        if (scannedCategories == 0) {
            throw failure("no exact " + GTNEIDefaultHandler.class.getName()
                    + " category plans were observed");
        }
        if (decisions.isEmpty()) {
            throw failure("the exact GregTech handlers exposed no outputless rows");
        }
        graphIdentityAudit.requireClean();
        graphIdentityAudit.requirePromotedExclusions();
        Snapshot snapshot = new Snapshot(
                decisions, graphIdentityAudit.exclusions,
                categoryAudits, scannedCategories, scannedRecipes);
        requirePromotedSnapshot(snapshot);
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] GregTech outputless semantic preflight complete; "
                        + "contract={} scannedCategories={} scannedRecipes={} semanticCategories={} "
                        + "semanticRecipes={} excludedLargeBoilerPresentation={} recordedRows={} "
                        + "fuel={} fuelCategories={} largeBoilerFuel={} "
                        + "largeBoilerFuelCategories={} radio={} quantum={} space={} sha256={}",
                CONTRACT, snapshot.scannedGregTechCategories(),
                snapshot.scannedGregTechRecipes(), snapshot.distinctCategories(),
                snapshot.semanticRecipes(), snapshot.excludedPresentationRows(),
                snapshot.recordedRows(),
                snapshot.count(Classification.GREGTECH_FUEL_SINK),
                snapshot.distinctCategoryCount(Classification.GREGTECH_FUEL_SINK),
                snapshot.count(Classification.LARGE_BOILER_FUEL_SINK),
                snapshot.distinctCategoryCount(Classification.LARGE_BOILER_FUEL_SINK),
                snapshot.count(Classification.RADIO_HATCH_INFORMATION),
                snapshot.count(Classification.QUANTUM_COMPONENT_INFORMATION),
                snapshot.count(Classification.SPACE_PROJECT_INFORMATION), snapshot.sha256());
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Promoted exact non-addressable GregTech ItemDoor "
                        + "recycling exclusions; contract={} rows={} categories={} sha256={}",
                UNREGISTERED_DOOR_RECYCLING_CONTRACT,
                snapshot.excludedUnregisteredDoorRecyclingRows(),
                snapshot.excludedUnregisteredDoorRecyclingCategories(),
                snapshot.unregisteredDoorRecyclingSha256());
        return snapshot;
    }

    static void requirePromotedSnapshot(Snapshot snapshot) throws ExportFailure {
        if (snapshot == null) {
            throw failure("promoted GregTech outputless semantic snapshot is null");
        }
        boolean exactCoverage = snapshot.scannedGregTechCategories()
                == EXPECTED_SCANNED_GREGTECH_CATEGORIES
                && snapshot.scannedGregTechRecipes() == EXPECTED_SCANNED_GREGTECH_RECIPES
                && snapshot.count(Classification.GREGTECH_FUEL_SINK)
                == EXPECTED_FUEL_SINK_RECIPES
                && snapshot.distinctCategoryCount(Classification.GREGTECH_FUEL_SINK)
                == EXPECTED_FUEL_SINK_CATEGORIES
                && snapshot.count(Classification.LARGE_BOILER_FUEL_SINK)
                == EXPECTED_LARGE_BOILER_FUEL_SINK_RECIPES
                && snapshot.distinctCategoryCount(Classification.LARGE_BOILER_FUEL_SINK)
                == EXPECTED_LARGE_BOILER_FUEL_SINK_CATEGORIES
                && snapshot.count(Classification.RADIO_HATCH_INFORMATION)
                == EXPECTED_RADIO_HATCH_INFORMATION_RECIPES
                && snapshot.distinctCategoryCount(Classification.RADIO_HATCH_INFORMATION) == 1
                && snapshot.count(Classification.QUANTUM_COMPONENT_INFORMATION)
                == EXPECTED_QUANTUM_COMPONENT_INFORMATION_RECIPES
                && snapshot.distinctCategoryCount(
                        Classification.QUANTUM_COMPONENT_INFORMATION) == 1
                && snapshot.count(Classification.SPACE_PROJECT_INFORMATION)
                == EXPECTED_SPACE_PROJECT_INFORMATION_RECIPES
                && snapshot.distinctCategoryCount(Classification.SPACE_PROJECT_INFORMATION) == 1
                && snapshot.distinctCategories() == EXPECTED_SEMANTIC_CATEGORIES
                && snapshot.semanticRecipes() == EXPECTED_SEMANTIC_RECIPES
                && snapshot.excludedPresentationRows()
                == EXPECTED_EXCLUDED_LARGE_BOILER_PRESENTATION_ROWS
                && snapshot.recordedRows() == EXPECTED_RECORDED_ROWS;
        if (!exactCoverage) {
            throw failure("promoted GregTech outputless semantic coverage drifted before v2 "
                    + "fingerprint validation: " + coverageSummary(snapshot));
        }
        if (!EXPECTED_SHA256.equals(snapshot.sha256())) {
            throw failure("promoted GregTech outputless semantic corpus drifted: expectedSha256="
                    + EXPECTED_SHA256 + ", " + coverageSummary(snapshot));
        }
        if (snapshot.excludedUnregisteredDoorRecyclingRows()
                != EXPECTED_UNREGISTERED_DOOR_RECYCLING_ROWS
                || snapshot.excludedUnregisteredDoorRecyclingCategories()
                != EXPECTED_UNREGISTERED_DOOR_RECYCLING_CATEGORIES
                || !EXPECTED_UNREGISTERED_DOOR_RECYCLING_SHA256.equals(
                        snapshot.unregisteredDoorRecyclingSha256())) {
            throw graphFailure("promoted exclusion snapshot drifted; rows="
                    + snapshot.excludedUnregisteredDoorRecyclingRows()
                    + ", categories="
                    + snapshot.excludedUnregisteredDoorRecyclingCategories()
                    + ", expectedSha256="
                    + EXPECTED_UNREGISTERED_DOOR_RECYCLING_SHA256
                    + ", observedSha256="
                    + snapshot.unregisteredDoorRecyclingSha256());
        }
    }

    private static String coverageSummary(Snapshot snapshot) {
        return "scannedCategories=" + snapshot.scannedGregTechCategories()
                + ", scannedRecipes=" + snapshot.scannedGregTechRecipes()
                + ", fuel=" + snapshot.count(Classification.GREGTECH_FUEL_SINK)
                + "/" + snapshot.distinctCategoryCount(Classification.GREGTECH_FUEL_SINK)
                + ", largeBoiler="
                + snapshot.count(Classification.LARGE_BOILER_FUEL_SINK)
                + "/" + snapshot.distinctCategoryCount(
                        Classification.LARGE_BOILER_FUEL_SINK)
                + ", radio=" + snapshot.count(Classification.RADIO_HATCH_INFORMATION)
                + ", quantum=" + snapshot.count(Classification.QUANTUM_COMPONENT_INFORMATION)
                + ", space=" + snapshot.count(Classification.SPACE_PROJECT_INFORMATION)
                + ", semanticCategories=" + snapshot.distinctCategories()
                + ", semanticRecipes=" + snapshot.semanticRecipes()
                + ", excludedLargeBoilerPresentation=" + snapshot.excludedPresentationRows()
                + ", recordedRows=" + snapshot.recordedRows()
                + ", sha256=" + snapshot.sha256();
    }

    /*
     * Legacy v1 made the promoted digest depend on arecipes source indices. The exact runtime
     * binding still uses SourceKey, but semantic promotion must be invariant to equivalent row
     * insertion order across client launches.
     */
    static String stableMultisetFingerprint(String domain, List<String> tokens) {
        return stableMultisetFingerprint(CONTRACT, domain, tokens);
    }

    static String stableGraphIdentityExclusionFingerprint(List<String> tokens) {
        return stableMultisetFingerprint(
                UNREGISTERED_DOOR_RECYCLING_CONTRACT,
                "gregtech-unregistered-itemdoor-recycling-rows-v1",
                tokens);
    }

    private static String stableMultisetFingerprint(
            String aggregateContract, String domain, List<String> tokens) {
        if (aggregateContract == null || aggregateContract.trim().isEmpty()) {
            throw new IllegalArgumentException("semantic multiset contract is required");
        }
        if (domain == null || domain.trim().isEmpty()) {
            throw new IllegalArgumentException("semantic multiset domain is required");
        }
        if (tokens == null) {
            throw new IllegalArgumentException("semantic multiset tokens are required");
        }
        List<String> sorted = new ArrayList<String>(tokens.size());
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (token == null) {
                throw new IllegalArgumentException(
                        "semantic multiset token #" + index + " is null");
            }
            sorted.add(token);
        }
        Collections.sort(sorted);
        StringBuilder canonical = new StringBuilder(Math.max(256, sorted.size() * 96));
        frame(canonical, "contract", aggregateContract);
        frame(canonical, "domain", domain);
        frame(canonical, "tokenCount", sorted.size());
        for (String token : sorted) {
            frame(canonical, "token", token);
        }
        return Naming.sha256(canonical.toString());
    }

    private static String categoryAuditToken(CategoryAudit category) {
        if (category == null || category.categoryId == null || category.fingerprint == null) {
            throw new IllegalArgumentException("complete category audit token is required");
        }
        StringBuilder canonical = new StringBuilder(192);
        frame(canonical, "categoryId", category.categoryId);
        frame(canonical, "categoryFingerprint", category.fingerprint);
        return canonical.toString();
    }

    private static String categorySemanticRowToken(Decision decision) {
        if (decision == null || decision.classification == null || decision.fingerprint == null) {
            throw new IllegalArgumentException("complete category semantic row is required");
        }
        StringBuilder canonical = new StringBuilder(192);
        frame(canonical, "classification", decision.classification.name());
        frame(canonical, "rowFingerprint", decision.fingerprint);
        return canonical.toString();
    }

    private static String corpusSemanticRowToken(Decision decision) {
        if (decision == null || decision.categoryId == null) {
            throw new IllegalArgumentException("complete corpus semantic row is required");
        }
        StringBuilder canonical = new StringBuilder(256);
        frame(canonical, "categoryId", decision.categoryId);
        frame(canonical, "categorySemanticRow", categorySemanticRowToken(decision));
        return canonical.toString();
    }

    private static CategoryBinding bindCategory(
            HandlerCategoryPlan plan, ICraftingHandler loadedHandler) throws ExportFailure {
        if (plan.prototype.getClass() != GTNEIDefaultHandler.class
                || loadedHandler.getClass() != GTNEIDefaultHandler.class) {
            throw failure(plan.categoryId + " did not bind exact handler class "
                    + GTNEIDefaultHandler.class.getName());
        }
        GTNEIDefaultHandler prototype = (GTNEIDefaultHandler) plan.prototype;
        GTNEIDefaultHandler loaded = (GTNEIDefaultHandler) loadedHandler;
        RecipeMap<?> map = loaded.getRecipeMap();
        if (map == null || map.getClass() != RecipeMap.class) {
            throw failure(plan.categoryId + " did not bind exact RecipeMap; got "
                    + className(map));
        }
        if (prototype.getRecipeMap() != map) {
            throw failure(plan.categoryId
                    + " loaded handler changed the registered prototype RecipeMap identity");
        }
        RecipeMapBackend backend = map.getBackend();
        RecipeMapFrontend frontend = map.getFrontend();
        if (backend == null || frontend == null) {
            throw failure(plan.categoryId + " has a null GregTech backend/frontend binding");
        }
        NEIRecipeProperties neiProperties = frontend.getNEIProperties();
        if (neiProperties == null || !neiProperties.registerNEI) {
            throw failure(plan.categoryId + " map=" + map.unlocalizedName
                    + " is not registered for NEI");
        }
        if (loaded.arecipes == null || loaded.numRecipes() != loaded.arecipes.size()) {
            throw failure(plan.categoryId + " loaded arecipes/numRecipes binding drifted");
        }
        if (map.unlocalizedName == null || map.unlocalizedName.trim().isEmpty()) {
            throw failure(plan.categoryId + " bound a blank RecipeMap unlocalized name");
        }

        IdentityHashMap<GTRecipe, Boolean> mapRecipes =
                new IdentityHashMap<GTRecipe, Boolean>();
        for (GTRecipe recipe : map.getAllRecipes()) {
            if (recipe == null) {
                throw failure(plan.categoryId + " map=" + map.unlocalizedName
                        + " contains a null raw recipe");
            }
            mapRecipes.put(recipe, Boolean.TRUE);
        }
        return new CategoryBinding(
                plan, prototype, loaded, map, backend, frontend, neiProperties, mapRecipes);
    }

    private static RowObservation observeRow(CategoryBinding category, int sourceIndex)
            throws ExportFailure {
        if (sourceIndex < 0 || sourceIndex >= category.loaded.arecipes.size()) {
            throw failure(category.plan.categoryId + " invalid sourceIndex=" + sourceIndex
                    + "/" + category.loaded.arecipes.size());
        }
        TemplateRecipeHandler.CachedRecipe source =
                category.loaded.arecipes.get(sourceIndex);
        if (source == null
                || source.getClass() != GTNEIDefaultHandler.CachedDefaultRecipe.class) {
            throw failure(category.plan.categoryId + " #" + sourceIndex
                    + " did not bind exact CachedDefaultRecipe; got " + className(source));
        }
        if (category.loaded.arecipes.get(sourceIndex) != source) {
            throw failure(category.plan.categoryId + " #" + sourceIndex
                    + " changed arecipes source identity during lookup");
        }
        GTNEIDefaultHandler.CachedDefaultRecipe cached =
                (GTNEIDefaultHandler.CachedDefaultRecipe) source;
        GTRecipe recipe = cached.mRecipe;
        if (recipe == null || !category.mapRecipes.containsKey(recipe)) {
            throw failure(category.plan.categoryId + " #" + sourceIndex
                    + " cached raw recipe is not the identical RecipeMap-owned instance");
        }
        if (cached.mInputs == null || cached.mOutputs == null) {
            throw failure(category.plan.categoryId + " #" + sourceIndex
                    + " has null cached input/output lists");
        }

        List<PositionedStack> ingredients = category.loaded.getIngredientStacks(sourceIndex);
        PositionedStack result = category.loaded.getResultStack(sourceIndex);
        List<PositionedStack> otherStacks = category.loaded.getOtherStacks(sourceIndex);
        if (ingredients == null || otherStacks == null) {
            throw failure(category.plan.categoryId + " #" + sourceIndex
                    + " returned a null NEI ingredient/other-stack list");
        }
        if (ingredients != cached.mInputs
                || result != cached.getResult()
                || otherStacks != cached.mOutputs
                || otherStacks != cached.getOtherStacks()) {
            throw failure(category.plan.categoryId + " #" + sourceIndex
                    + " handler accessors are not identity-bound to its exact arecipes row");
        }

        // GregTech permits sparse raw arrays in ordinary recipes (for example, an optional
        // Primitive Blast Furnace input). Those rows are outside this outputless semantic
        // contract. Preserve the exact NEI identity checks above, but only demand dense raw
        // semantic evidence after the row is proven to be outputless in NEI.
        if (result != null || !otherStacks.isEmpty()) {
            return new RowObservation(
                    sourceIndex, cached, recipe, ingredients, otherStacks, result,
                    0, 0, 0, 0, Collections.<String>emptyList());
        }

        int rawItemInputs = requireDenseItems(
                recipe.mInputs, category, sourceIndex, "raw item inputs");
        int rawFluidInputs = requireDenseFluids(
                recipe.mFluidInputs, category, sourceIndex, "raw fluid inputs");
        int rawItemOutputs = requireDenseItems(
                recipe.mOutputs, category, sourceIndex, "raw item outputs");
        int rawFluidOutputs = requireDenseFluids(
                recipe.mFluidOutputs, category, sourceIndex, "raw fluid outputs");
        List<String> visibleInputFacts = visibleInputFacts(
                ingredients, category.plan.categoryId, sourceIndex);
        return new RowObservation(
                sourceIndex, cached, recipe, ingredients, otherStacks, result,
                rawItemInputs, rawFluidInputs, rawItemOutputs, rawFluidOutputs,
                visibleInputFacts);
    }

    private static Classification classify(CategoryBinding category, RowObservation row)
            throws ExportFailure {
        requireSemanticCategoryBinding(category, row);
        String mapName = category.map.unlocalizedName;

        if (RADIO_HATCH_MAP.equals(mapName)) {
            requireExactClasses(category, RecipeMapBackend.class, RadioHatchFrontend.class, row);
            Sievert sievert = row.recipe.getMetadata(GTRecipeConstants.SIEVERT);
            Integer mass = row.recipe.getMetadata(GTRecipeConstants.MASS);
            if (!row.recipe.mFakeRecipe || !row.hasRawAndNeiInputs()
                    || !row.hasNoRawOutputs()
                    || row.recipe.mDuration != 0 || row.recipe.mEUt != 0
                    || sievert == null || sievert.sievert <= 0
                    || mass == null || mass.intValue() <= 0) {
                throw unclassified(category, row,
                        "radio-hatch information contract did not match");
            }
            return Classification.RADIO_HATCH_INFORMATION;
        }

        if (QUANTUM_COMPUTER_MAP.equals(mapName)) {
            requireExactClasses(
                    category, RecipeMapBackend.class, QuantumComputerFrontend.class, row);
            QuantumComputerRecipeData data =
                    row.recipe.getMetadata(GTRecipeConstants.QUANTUM_COMPUTER_DATA);
            if (!row.recipe.mFakeRecipe || !row.hasRawAndNeiInputs()
                    || !row.hasNoRawOutputs()
                    || row.recipe.mDuration != 0 || row.recipe.mEUt != 0
                    || data == null) {
                throw unclassified(category, row,
                        "quantum-component information contract did not match");
            }
            return Classification.QUANTUM_COMPONENT_INFORMATION;
        }

        if (SPACE_PROJECT_MAP.equals(mapName)) {
            requireExactClasses(category, RecipeMapBackend.class, SpaceProjectFrontend.class, row);
            if (row.recipe.getClass()
                    != SpaceProjectManager.FakeSpaceProjectRecipe.class
                    || !row.hasRawAndNeiInputs() || !row.hasNoRawOutputs()
                    || !row.recipe.mEnabled || row.recipe.mHidden) {
                throw unclassified(category, row,
                        "space-project information contract did not match");
            }
            return Classification.SPACE_PROJECT_INFORMATION;
        }

        if (LARGE_BOILER_MAP.equals(mapName)
                || category.backend.getClass() == LargeBoilerFuelBackend.class) {
            requireExactClasses(
                    category, LargeBoilerFuelBackend.class,
                    LargeBoilerFuelFrontend.class, row);
            if (!LARGE_BOILER_MAP.equals(mapName)) {
                throw unclassified(category, row,
                        "LargeBoilerFuelBackend is bound to an unknown RecipeMap");
            }
            if (isInputOnlyFuelSink(row) || isLargeBoilerSolidFuelSink(row)) {
                return Classification.LARGE_BOILER_FUEL_SINK;
            }
            if (isLargeBoilerPresentationRow(row)) {
                return Classification.LARGE_BOILER_PRESENTATION_EXCLUDED;
            }
            throw unclassified(category, row,
                    "large-boiler fuel/presentation contracts did not match");
        }

        if (category.backend.getClass() == FuelBackend.class) {
            requireExactClasses(category, FuelBackend.class, RecipeMapFrontend.class, row);
            if (!isInputOnlyFuelSink(row)) {
                throw unclassified(category, row,
                        "FuelBackend input-only sink contract did not match");
            }
            return Classification.GREGTECH_FUEL_SINK;
        }

        throw unclassified(category, row,
                "no approved outputless GregTech semantic contract exists");
    }

    private static void requireSemanticCategoryBinding(
            CategoryBinding category, RowObservation row) throws ExportFailure {
        String overlay = category.loaded.getOverlayIdentifier();
        if (overlay == null || !overlay.equals(category.map.unlocalizedName)
                || !overlay.equals(category.plan.overlayIdentifier)
                || !category.map.unlocalizedName.equals(category.plan.loadIdentifier)) {
            throw unclassified(category, row,
                    "overlay/load operation is not the exact RecipeMap unlocalized name");
        }
        if (!category.neiProperties.registerNEI) {
            throw unclassified(category, row, "RecipeMap is not registered with NEI");
        }
    }

    private static void requireExactClasses(
            CategoryBinding category,
            Class<? extends RecipeMapBackend> backendClass,
            Class<? extends RecipeMapFrontend> frontendClass,
            RowObservation row) throws ExportFailure {
        if (category.backend.getClass() != backendClass
                || category.frontend.getClass() != frontendClass) {
            throw unclassified(category, row,
                    "expected backend/frontend=" + backendClass.getName() + "/"
                            + frontendClass.getName() + ", got "
                            + category.backend.getClass().getName() + "/"
                            + category.frontend.getClass().getName());
        }
    }

    private static boolean isInputOnlyFuelSink(RowObservation row) {
        // Duration, EU/t, fake-recipe state, and formatter are intentionally not pinned.
        return row.hasRawAndNeiInputs()
                && row.hasNoRawOutputs()
                && row.result == null
                && row.otherStacks.isEmpty()
                && row.recipe.mSpecialValue > 0
                && row.recipe.mSpecialItems == null
                && row.recipe.mEnabled
                && !row.recipe.mHidden;
    }

    private static boolean isLargeBoilerSolidFuelSink(RowObservation row) {
        if (row.recipe.mInputs == null || row.recipe.mInputs.length != 1) {
            return false;
        }
        String[] description = row.recipe.getNeiDesc();
        boolean allDescriptionLinesNonblank = description != null;
        if (description != null) {
            for (String line : description) {
                String plain = Naming.plainText(line);
                if (plain == null || plain.trim().isEmpty()) {
                    allDescriptionLinesNonblank = false;
                    break;
                }
            }
        }
        int furnaceFuelValue = GTModHandler.getFuelValue(row.recipe.mInputs[0]);
        return GregTechFuelInformationalContract.isCanonicalLargeBoilerSolidFuelRow(
                new GregTechFuelInformationalContract.LargeBoilerSolidObservation(
                        row.rawItemInputs,
                        row.rawFluidInputs,
                        row.ingredients.size(),
                        row.rawItemOutputs,
                        row.rawFluidOutputs,
                        row.result != null,
                        row.otherStacks.size(),
                        row.recipe.mDuration,
                        row.recipe.mEUt,
                        row.recipe.mSpecialValue,
                        furnaceFuelValue,
                        row.recipe.mSpecialItems != null,
                        row.recipe.mFakeRecipe,
                        row.recipe.mEnabled,
                        row.recipe.mHidden,
                        description == null ? 0 : description.length,
                        allDescriptionLinesNonblank));
    }

    private static boolean isLargeBoilerPresentationRow(RowObservation row) {
        if (row.rawInputs() != 0 || !row.ingredients.isEmpty()
                || !row.hasNoRawOutputs() || row.result != null || !row.otherStacks.isEmpty()
                || row.recipe.mDuration != 1 || row.recipe.mEUt != 1
                || row.recipe.mSpecialValue != 1 || row.recipe.mSpecialItems != null
                || !row.recipe.mEnabled || row.recipe.mHidden) {
            return false;
        }
        String[] description = row.recipe.getNeiDesc();
        if (description == null || description.length == 0) {
            return false;
        }
        for (String line : description) {
            String plain = Naming.plainText(line);
            if (plain == null || plain.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static Decision decision(
            CategoryBinding category, RowObservation row, Classification classification)
            throws ExportFailure {
        StringBuilder canonical = new StringBuilder(4096);
        frame(canonical, "contract", CONTRACT);
        frame(canonical, "categoryId", category.plan.categoryId);
        frame(canonical, "classification", classification.name());
        appendCategoryFacts(canonical, category, category.loaded.arecipes.size());
        appendRowFacts(canonical, row);
        return new Decision(
                category.plan.categoryId, row.sourceIndex, classification,
                canonical.toString());
    }

    private static void appendCategoryFacts(
            StringBuilder canonical, CategoryBinding category, int recipeCount) {
        frame(canonical, "handlerClass", category.loaded.getClass().getName());
        frame(canonical, "handlerId", category.plan.handlerId);
        frame(canonical, "overlay", category.loaded.getOverlayIdentifier());
        frame(canonical, "loadIdentifier", category.plan.loadIdentifier);
        frame(canonical, "mapClass", category.map.getClass().getName());
        frame(canonical, "mapUnlocalizedName", category.map.unlocalizedName);
        frame(canonical, "backendClass", category.backend.getClass().getName());
        frame(canonical, "frontendClass", category.frontend.getClass().getName());
        frame(canonical, "registerNEI", category.neiProperties.registerNEI);
        frame(canonical, "recipeCount", recipeCount);
    }

    private static void appendRowFacts(StringBuilder canonical, RowObservation row)
            throws ExportFailure {
        GTRecipe recipe = row.recipe;
        frame(canonical, "cachedClass", row.cached.getClass().getName());
        frame(canonical, "recipeClass", recipe.getClass().getName());
        frame(canonical, "rawItemInputs", row.rawItemInputs);
        frame(canonical, "rawFluidInputs", row.rawFluidInputs);
        frame(canonical, "rawItemOutputs", row.rawItemOutputs);
        frame(canonical, "rawFluidOutputs", row.rawFluidOutputs);
        frame(canonical, "neiInputs", row.ingredients.size());
        frame(canonical, "neiResultPresent", row.result != null);
        frame(canonical, "neiOtherStacks", row.otherStacks.size());
        frame(canonical, "duration", recipe.mDuration);
        frame(canonical, "eut", recipe.mEUt);
        frame(canonical, "specialValue", recipe.mSpecialValue);
        frame(canonical, "specialItems", recipe.mSpecialItems == null
                ? "<null>" : recipe.mSpecialItems.getClass().getName());
        frame(canonical, "enabled", recipe.mEnabled);
        frame(canonical, "hidden", recipe.mHidden);
        frame(canonical, "fakeRecipe", recipe.mFakeRecipe);
        frame(canonical, "canBeBuffered", recipe.mCanBeBuffered);
        frame(canonical, "needsEmptyOutput", recipe.mNeedsEmptyOutput);
        frame(canonical, "nbtSensitive", recipe.isNBTSensitive);
        if (recipe.mChances == null) {
            frame(canonical, "chanceCount", -1);
        } else {
            frame(canonical, "chanceCount", recipe.mChances.length);
            for (int index = 0; index < recipe.mChances.length; index++) {
                frame(canonical, "chance[" + index + "]", recipe.mChances[index]);
            }
        }
        String[] description = recipe.getNeiDesc();
        if (description == null) {
            frame(canonical, "descriptionCount", -1);
        } else {
            frame(canonical, "descriptionCount", description.length);
            for (int index = 0; index < description.length; index++) {
                frame(canonical, "description[" + index + "]", description[index]);
            }
        }
        frame(canonical, "visibleInputFactCount", row.visibleInputFacts.size());
        for (String fact : row.visibleInputFacts) {
            frame(canonical, "visibleInput", fact);
        }
        appendMetadataFacts(canonical, recipe);
        if (recipe.getClass() == SpaceProjectManager.FakeSpaceProjectRecipe.class) {
            SpaceProjectManager.FakeSpaceProjectRecipe project =
                    (SpaceProjectManager.FakeSpaceProjectRecipe) recipe;
            frame(canonical, "spaceProjectName", project.projectName);
        }
    }

    private static void appendMetadataFacts(StringBuilder canonical, GTRecipe recipe)
            throws ExportFailure {
        List<Map.Entry<RecipeMetadataKey<?>, Object>> entries =
                new ArrayList<Map.Entry<RecipeMetadataKey<?>, Object>>(
                        recipe.getMetadataStorage().getEntries());
        Collections.sort(entries,
                new Comparator<Map.Entry<RecipeMetadataKey<?>, Object>>() {
                    @Override
                    public int compare(Map.Entry<RecipeMetadataKey<?>, Object> left,
                                       Map.Entry<RecipeMetadataKey<?>, Object> right) {
                        return metadataKey(left).compareTo(metadataKey(right));
                    }
                });
        frame(canonical, "metadataCount", entries.size());
        for (Map.Entry<RecipeMetadataKey<?>, Object> entry : entries) {
            String key = metadataKey(entry);
            frame(canonical, "metadataKey", key);
            frame(canonical, "metadataValue", canonicalMetadataValue(entry.getValue(), key));
        }
    }

    private static String metadataKey(Map.Entry<RecipeMetadataKey<?>, Object> entry) {
        return entry == null || entry.getKey() == null
                ? "<null>" : entry.getKey().toString();
    }

    private static String canonicalMetadataValue(Object value, String key)
            throws ExportFailure {
        if (value == null) {
            return "null";
        }
        Class<?> type = value.getClass();
        if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Boolean || value instanceof String) {
            return type.getName() + ":" + value;
        }
        if (value instanceof Float) {
            return type.getName() + ":bits="
                    + Integer.toHexString(Float.floatToIntBits((Float) value));
        }
        if (value instanceof Double) {
            return type.getName() + ":bits="
                    + Long.toHexString(Double.doubleToLongBits((Double) value));
        }
        if (value instanceof Character) {
            return type.getName() + ":codepoint=" + (int) ((Character) value).charValue();
        }
        if (value instanceof Enum<?>) {
            return type.getName() + ":" + ((Enum<?>) value).name();
        }
        if (value instanceof Sievert) {
            Sievert sievert = (Sievert) value;
            return type.getName() + ":sievert=" + sievert.sievert
                    + ",exact=" + sievert.isExact;
        }
        if (value instanceof QuantumComputerRecipeData) {
            QuantumComputerRecipeData data = (QuantumComputerRecipeData) value;
            return type.getName()
                    + ":heat=" + Integer.toHexString(Float.floatToIntBits(data.heatConstant))
                    + ",cool=" + Integer.toHexString(Float.floatToIntBits(data.coolConstant))
                    + ",computation="
                    + Integer.toHexString(Float.floatToIntBits(data.computation))
                    + ",maxHeat=" + Integer.toHexString(Float.floatToIntBits(data.maxHeat))
                    + ",subZero=" + data.subZero;
        }
        if (value instanceof ItemStack) {
            StackIdentity identity = StackIdentity.of((ItemStack) value);
            return type.getName() + ":" + identity.key + ":amount=" + identity.amount;
        }
        if (value instanceof FluidStack) {
            FluidStack fluid = (FluidStack) value;
            if (fluid.getFluid() == null) {
                throw failure("metadata " + key + " contains a FluidStack with null fluid");
            }
            String tag = fluid.tag == null ? "-"
                    : Naming.sha256(NbtCanonicalizer.canonical(fluid.tag));
            return type.getName() + ":" + fluid.getFluid().getName()
                    + ":amount=" + fluid.amount + ":nbt=" + tag;
        }
        throw failure("metadata " + key + " uses unsupported non-primitive value class "
                + type.getName());
    }

    private static int requireDenseItems(ItemStack[] stacks, CategoryBinding category,
                                         int sourceIndex, String role) throws ExportFailure {
        if (stacks == null) {
            throw failure(category.plan.categoryId + " #" + sourceIndex
                    + " has null " + role + " array");
        }
        for (int index = 0; index < stacks.length; index++) {
            if (stacks[index] == null || stacks[index].getItem() == null) {
                throw failure(category.plan.categoryId + " #" + sourceIndex
                        + " has null/empty " + role + " entry #" + index);
            }
        }
        return stacks.length;
    }

    private static int requireDenseFluids(FluidStack[] stacks, CategoryBinding category,
                                          int sourceIndex, String role) throws ExportFailure {
        if (stacks == null) {
            throw failure(category.plan.categoryId + " #" + sourceIndex
                    + " has null " + role + " array");
        }
        for (int index = 0; index < stacks.length; index++) {
            if (stacks[index] == null || stacks[index].getFluid() == null) {
                throw failure(category.plan.categoryId + " #" + sourceIndex
                        + " has null/empty " + role + " entry #" + index);
            }
        }
        return stacks.length;
    }

    private static void validateVisibleGraphIdentities(
            CategoryBinding category, RowObservation row, GraphIdentityAudit audit) {
        validatePositionedList(category, row, row.ingredients, "input", audit);
        if (row.result != null) {
            validatePositionedStack(category, row, row.result, "result", 0, audit);
            validatePositionedList(category, row, row.otherStacks, "catalyst", audit);
        } else {
            validatePositionedList(category, row, row.otherStacks, "output", audit);
        }
    }

    private static void validatePositionedList(
            CategoryBinding category, RowObservation row, List<PositionedStack> positioned,
            String role, GraphIdentityAudit audit) {
        if (positioned == null) {
            audit.record(graphIdentityFailureFacts(
                    category, row, role, -1, -1, null,
                    new IllegalArgumentException("visible positioned-stack list is null")));
            return;
        }
        for (int slotIndex = 0; slotIndex < positioned.size(); slotIndex++) {
            validatePositionedStack(
                    category, row, positioned.get(slotIndex), role, slotIndex, audit);
        }
    }

    private static void validatePositionedStack(
            CategoryBinding category, RowObservation row, PositionedStack positioned,
            String role, int slotIndex, GraphIdentityAudit audit) {
        if (positioned == null) {
            audit.record(graphIdentityFailureFacts(
                    category, row, role, slotIndex, -1, null,
                    new IllegalArgumentException("visible PositionedStack is null")));
            return;
        }
        try {
            if (positioned.items == null || positioned.items.length == 0) {
                positioned.generatePermutations();
            }
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            audit.record(graphIdentityFailureFacts(
                    category, row, role, slotIndex, -1, null, error));
            return;
        }
        if (positioned.items == null || positioned.items.length == 0) {
            audit.record(graphIdentityFailureFacts(
                    category, row, role, slotIndex, -1, null,
                    new IllegalArgumentException(
                            "visible PositionedStack has no generated alternatives")));
            return;
        }
        for (int alternativeIndex = 0;
             alternativeIndex < positioned.items.length; alternativeIndex++) {
            ItemStack stack = positioned.items[alternativeIndex];
            try {
                StackIdentity.of(stack);
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                audit.record(
                        category, row, role, slotIndex, alternativeIndex, stack, error);
            }
        }
    }

    private static GraphIdentityDecision classifyUnregisteredDoorRecyclingExclusion(
            CategoryBinding category, RowObservation row, String role,
            int slotIndex, int alternativeIndex, ItemStack stack, Throwable error)
            throws ExportFailure {
        if (!(error instanceof IllegalArgumentException)
                || error.getMessage() == null
                || !error.getMessage().startsWith(
                        "ITEM_IDENTITY: item is absent from the namespaced item registry")
                || !"input".equals(role) || slotIndex != 0 || alternativeIndex != 0
                || stack == null || stack.getItem() == null
                || stack.getItem().getClass() != net.minecraft.item.ItemDoor.class
                || net.minecraft.item.Item.itemRegistry.getNameForObject(
                        stack.getItem()) != null) {
            return null;
        }

        String mapName = category.map.unlocalizedName;
        String expectedOverlay;
        if ("gt.recipe.macerator".equals(mapName)) {
            expectedOverlay = "gt.recipe.category.macerator_recycling";
        } else if ("gt.recipe.arcfurnace".equals(mapName)) {
            expectedOverlay = "gt.recipe.category.arc_furnace_recycling";
        } else if ("gt.recipe.fluidextractor".equals(mapName)) {
            expectedOverlay = "gt.recipe.category.fluid_extractor_recycling";
        } else {
            return null;
        }
        gregtech.api.recipe.RecipeCategory recipeCategory =
                row.recipe.getRecipeCategory();
        if (!expectedOverlay.equals(category.loaded.getOverlayIdentifier())
                || recipeCategory == null
                || !expectedOverlay.equals(recipeCategory.unlocalizedName)) {
            return null;
        }

        String unlocalizedName = stack.getUnlocalizedName();
        String doorKind;
        int expectedRawMetadata;
        if ("item.doorWood".equals(unlocalizedName)) {
            doorKind = "wood";
            expectedRawMetadata = Short.MAX_VALUE;
        } else if ("item.doorIron".equals(unlocalizedName)) {
            doorKind = "iron";
            expectedRawMetadata = 0;
        } else {
            return null;
        }
        requireUnregisteredDoorRecyclingStructure(row, stack, mapName);
        if (row.recipe.mInputs[0].getItemDamage() != expectedRawMetadata) {
            throw graphFailure("stale " + doorKind
                    + " ItemDoor raw metadata drifted; expected=" + expectedRawMetadata
                    + ", observed=" + row.recipe.mInputs[0].getItemDamage());
        }

        DoorReplacementEvidence replacementEvidence = requireDoorReplacementEvidence(
                row.recipe, row.recipe.mInputs[0], stack.getItem(), doorKind);

        String canonicalFacts = canonicalGraphIdentityExclusionFacts(
                category, row, role, slotIndex, alternativeIndex,
                stack, doorKind, replacementEvidence);
        return new GraphIdentityDecision(
                category.plan.categoryId, row.sourceIndex,
                mapName, doorKind, canonicalFacts);
    }

    private static void requireUnregisteredDoorRecyclingStructure(
            RowObservation row, ItemStack stack, String mapName) throws ExportFailure {
        List<String> drift = new ArrayList<String>();
        if (stack.stackSize != 1) {
            drift.add("visible-stack-size=" + stack.stackSize);
        }
        if (stack.getItemDamage() != 0) {
            drift.add("visible-metadata=" + stack.getItemDamage());
        }
        if (stack.getTagCompound() != null) {
            drift.add("visible-nbt-present");
        }
        if (stack.getItem().getHasSubtypes()) {
            drift.add("stale-item-has-subtypes");
        }
        if (stack.getItem().getMaxDamage() != 0) {
            drift.add("stale-item-max-damage=" + stack.getItem().getMaxDamage());
        }
        if (stack.getItem().getItemStackLimit() != 16) {
            drift.add("stale-item-stack-limit=" + stack.getItem().getItemStackLimit());
        }
        if (row.recipe.getClass() != GTRecipe.class) {
            drift.add("recipe-class=" + row.recipe.getClass().getName());
        }
        int expectedVisibleInputs = "gt.recipe.arcfurnace".equals(mapName) ? 2 : 1;
        if (row.ingredients == null || row.ingredients.size() != expectedVisibleInputs) {
            drift.add("visible-input-count="
                    + (row.ingredients == null ? -1 : row.ingredients.size()));
        } else {
            PositionedStack input = row.ingredients.get(0);
            if (input == null || input.items == null || input.items.length != 1) {
                drift.add("visible-input-alternative-count="
                        + (input == null || input.items == null ? -1 : input.items.length));
            } else if (input.items[0] != stack) {
                drift.add("visible-input-stack-identity-mismatch");
            }
        }
        if (row.result != null) {
            drift.add("unexpected-primary-result");
        }
        if (row.otherStacks == null || row.otherStacks.size() != 1) {
            drift.add("visible-output-count="
                    + (row.otherStacks == null ? -1 : row.otherStacks.size()));
        }
        if (row.recipe.mInputs == null || row.recipe.mInputs.length != 1
                || row.recipe.mInputs[0] == null) {
            drift.add("raw-input-count="
                    + (row.recipe.mInputs == null ? -1 : row.recipe.mInputs.length));
        } else {
            ItemStack rawInput = row.recipe.mInputs[0];
            if (rawInput.getItem() != stack.getItem()) {
                drift.add("raw-input-item-identity-mismatch");
            }
            if (rawInput.stackSize != 1) {
                drift.add("raw-input-stack-size=" + rawInput.stackSize);
            }
            if (rawInput.getTagCompound() != null) {
                drift.add("raw-input-nbt-present");
            }
        }
        int expectedFluidInputs = "gt.recipe.arcfurnace".equals(mapName) ? 1 : 0;
        int fluidInputs = row.recipe.mFluidInputs == null
                ? 0 : row.recipe.mFluidInputs.length;
        if (fluidInputs != expectedFluidInputs) {
            drift.add("raw-fluid-input-count=" + fluidInputs);
        }
        int itemOutputs = row.recipe.mOutputs == null ? 0 : row.recipe.mOutputs.length;
        int fluidOutputs = row.recipe.mFluidOutputs == null
                ? 0 : row.recipe.mFluidOutputs.length;
        int expectedItemOutputs = "gt.recipe.fluidextractor".equals(mapName) ? 0 : 1;
        int expectedFluidOutputs = "gt.recipe.fluidextractor".equals(mapName) ? 1 : 0;
        if (itemOutputs != expectedItemOutputs) {
            drift.add("raw-item-output-count=" + itemOutputs);
        }
        if (fluidOutputs != expectedFluidOutputs) {
            drift.add("raw-fluid-output-count=" + fluidOutputs);
        }
        if (!denseItemArray(row.recipe.mOutputs)) {
            drift.add("sparse-raw-item-outputs");
        }
        if (!denseFluidArray(row.recipe.mFluidOutputs)) {
            drift.add("sparse-raw-fluid-outputs");
        }
        if ("gt.recipe.arcfurnace".equals(mapName) && fluidInputs == 1) {
            FluidStack oxygen = row.recipe.mFluidInputs[0];
            if (oxygen == null || oxygen.getFluid() == null
                    || !"oxygen".equals(oxygen.getFluid().getName())
                    || !"GalacticraftMars:oxygen".equals(
                            net.minecraftforge.fluids.FluidRegistry.getDefaultFluidName(
                                    oxygen.getFluid()))
                    || oxygen.getFluid().getClass()
                            != net.minecraftforge.fluids.Fluid.class
                    || oxygen.amount != row.recipe.mDuration
                    || oxygen.tag != null) {
                drift.add("arc-furnace-oxygen-semantics");
            }
        }
        if ("gt.recipe.fluidextractor".equals(mapName) && fluidOutputs == 1) {
            FluidStack moltenIron = row.recipe.mFluidOutputs[0];
            if (moltenIron == null || moltenIron.getFluid() == null
                    || !"molten.iron".equals(moltenIron.getFluid().getName())
                    || !"gregtech:molten.iron".equals(
                            net.minecraftforge.fluids.FluidRegistry.getDefaultFluidName(
                                    moltenIron.getFluid()))
                    || !"gregtech.common.fluid.GTFluid".equals(
                            moltenIron.getFluid().getClass().getName())
                    || moltenIron.amount != 864 || moltenIron.tag != null) {
                drift.add("fluid-extractor-molten-iron-semantics");
            }
        }
        if (row.recipe.mSpecialItems != null) {
            drift.add("special-items-present");
        }
        if (row.recipe.owners != null) {
            drift.add("owners-present");
        }
        if (row.recipe.mFakeRecipe) {
            drift.add("fake-recipe");
        }
        if (!row.recipe.mEnabled) {
            drift.add("disabled-recipe");
        }
        if (row.recipe.mHidden) {
            drift.add("hidden-recipe");
        }
        if (row.recipe.mDuration <= 0) {
            drift.add("duration=" + row.recipe.mDuration);
        }
        if (row.recipe.mEUt <= 0) {
            drift.add("eut=" + row.recipe.mEUt);
        }
        if (!drift.isEmpty()) {
            throw graphFailure("stale ItemDoor recycling candidate structure drifted; drift="
                    + drift + "; evidence={" + graphIdentityCandidateFacts(row, stack) + "}");
        }
    }

    private static boolean denseItemArray(ItemStack[] stacks) {
        if (stacks == null) {
            return true;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItem() == null) {
                return false;
            }
        }
        return true;
    }

    private static boolean denseFluidArray(FluidStack[] stacks) {
        if (stacks == null) {
            return true;
        }
        for (FluidStack stack : stacks) {
            if (stack == null || stack.getFluid() == null) {
                return false;
            }
        }
        return true;
    }

    private static String graphIdentityCandidateFacts(RowObservation row, ItemStack stack)
            throws ExportFailure {
        StringBuilder facts = new StringBuilder(4096);
        frame(facts, "visibleStack", graphStackDescription(stack));
        frame(facts, "itemHasSubtypes", stack.getItem().getHasSubtypes());
        frame(facts, "itemMaxDamage", stack.getItem().getMaxDamage());
        frame(facts, "itemStackLimit", stack.getItem().getItemStackLimit());
        frame(facts, "visibleInputs", positionedListFacts(row.ingredients));
        frame(facts, "visibleResult", positionedStackFacts(row.result));
        frame(facts, "visibleOthers", positionedListFacts(row.otherStacks));
        frame(facts, "rawInputs", canonicalItemArrayFacts(row.recipe.mInputs));
        frame(facts, "rawOutputs", canonicalItemArrayFacts(row.recipe.mOutputs));
        frame(facts, "rawFluidInputs", rawFluidArrayFacts(row.recipe.mFluidInputs));
        frame(facts, "rawFluidOutputs", rawFluidArrayFacts(row.recipe.mFluidOutputs));
        frame(facts, "specialItems", className(row.recipe.mSpecialItems));
        frame(facts, "owners", recipeOwnerFacts(row.recipe));
        frame(facts, "duration", row.recipe.mDuration);
        frame(facts, "eut", row.recipe.mEUt);
        frame(facts, "fake", row.recipe.mFakeRecipe);
        frame(facts, "enabled", row.recipe.mEnabled);
        frame(facts, "hidden", row.recipe.mHidden);
        return facts.toString();
    }

    private static DoorReplacementEvidence requireDoorReplacementEvidence(
            GTRecipe recipe, ItemStack rawInput, net.minecraft.item.Item staleItem,
            String doorKind) throws ExportFailure {
        try {
            String itemName = "wood".equals(doorKind) ? "wooden_door" : "iron_door";
            net.minecraft.item.Item registered =
                    cpw.mods.fml.common.registry.GameRegistry.findItem("minecraft", itemName);
            net.minecraft.item.Item staticDoor = "wood".equals(doorKind)
                    ? net.minecraft.init.Items.wooden_door
                    : net.minecraft.init.Items.iron_door;
            if (registered == null || registered != staticDoor || registered == staleItem
                    || !"net.malisis.doors.door.item.DoorItem".equals(
                            registered.getClass().getName())) {
                throw graphFailure("Malisis door replacement binding drifted for " + doorKind
                        + "; registered=" + className(registered)
                        + ", staticIdentityMatch=" + (registered == staticDoor)
                        + ", staleIdentityMatch=" + (registered == staleItem));
            }
            cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier identifier =
                    StackIdentity.requireForgeRegistryIdentifier(
                            new ItemStack(registered, 1, 0));
            String registryId = identifier.modId + ":" + identifier.name;
            String expectedRegistryId = "minecraft:" + itemName;
            if (!expectedRegistryId.equals(registryId)) {
                throw graphFailure("registered Malisis door ID drifted; expected="
                        + expectedRegistryId + ", observed=" + registryId);
            }

            Class<?> replacementTool = Class.forName(
                    "net.malisis.core.util.replacement.ReplacementTool", false,
                    registered.getClass().getClassLoader());
            java.lang.reflect.Method originalItem = replacementTool.getMethod(
                    "originalItem", net.minecraft.item.Item.class);
            int modifiers = originalItem.getModifiers();
            if (!java.lang.reflect.Modifier.isPublic(modifiers)
                    || !java.lang.reflect.Modifier.isStatic(modifiers)
                    || originalItem.getReturnType() != net.minecraft.item.Item.class) {
                throw graphFailure("Malisis ReplacementTool.originalItem reflection contract "
                        + "drifted");
            }
            Object original = originalItem.invoke(null, registered);
            if (original != staleItem) {
                throw graphFailure("Malisis ReplacementTool no longer maps the registered "
                        + doorKind + " door to the stale recipe ItemDoor identity");
            }

            boolean staleMatches = recipe.isRecipeInputEqual(
                    false, copiedMatcherFluids(recipe.mFluidInputs), rawInput.copy());
            boolean registeredMatches = recipe.isRecipeInputEqual(
                    false, copiedMatcherFluids(recipe.mFluidInputs),
                    new ItemStack(registered, 1, 0));
            if (!staleMatches || registeredMatches) {
                throw graphFailure("GregTech matcher proof drifted for stale " + doorKind
                        + " door; staleMatches=" + staleMatches
                        + ", registeredMatches=" + registeredMatches);
            }

            gregtech.api.objects.ItemData association =
                    gregtech.api.util.GTOreDictUnificator.getAssociation(rawInput);
            boolean validPrefixMaterial = association != null
                    && association.hasValidPrefixMaterialData();
            if (validPrefixMaterial) {
                throw graphFailure("stale " + doorKind
                        + " door unexpectedly gained a valid OreDictionary prefix/material "
                        + "alias");
            }

            if (!ItemList.loadFinished || ItemList.items == null) {
                throw graphFailure("NEI ItemList is unavailable during stale-door proof");
            }
            int staleCatalogEntries = 0;
            int registeredCatalogEntries = 0;
            for (ItemStack catalogStack : ItemList.items) {
                if (catalogStack == null) {
                    continue;
                }
                if (catalogStack.getItem() == staleItem) {
                    staleCatalogEntries++;
                }
                if (catalogStack.getItem() == registered) {
                    registeredCatalogEntries++;
                }
            }
            if (staleCatalogEntries != 0) {
                throw graphFailure("stale " + doorKind
                        + " ItemDoor unexpectedly became addressable through NEI ItemList; "
                        + "entries=" + staleCatalogEntries);
            }

            StringBuilder canonical = new StringBuilder(768);
            frame(canonical, "registeredRegistryId", registryId);
            frame(canonical, "registeredClass", registered.getClass().getName());
            frame(canonical, "registeredStaticIdentityMatch", registered == staticDoor);
            frame(canonical, "registeredDiffersFromStale", registered != staleItem);
            frame(canonical, "replacementOriginalIdentityMatch", original == staleItem);
            frame(canonical, "staleMatcherPositiveControl", staleMatches);
            frame(canonical, "registeredMatcherNegativeControl", registeredMatches);
            frame(canonical, "staleOreAssociationClass", className(association));
            frame(canonical, "staleOreValidPrefixMaterial", validPrefixMaterial);
            frame(canonical, "staleCatalogEntries", staleCatalogEntries);
            frame(canonical, "registeredCatalogEntries", registeredCatalogEntries);
            return new DoorReplacementEvidence(canonical.toString());
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw graphFailure("could not prove exact Malisis stale-door replacement semantics",
                    error);
        }
    }

    private static FluidStack[] copiedMatcherFluids(FluidStack[] fluids)
            throws ExportFailure {
        if (fluids == null || fluids.length == 0) {
            return new FluidStack[0];
        }
        FluidStack[] copies = new FluidStack[fluids.length];
        for (int index = 0; index < fluids.length; index++) {
            if (fluids[index] == null || fluids[index].getFluid() == null) {
                throw graphFailure("stale-door matcher proof received a sparse fluid input at #"
                        + index);
            }
            copies[index] = fluids[index].copy();
        }
        return copies;
    }

    private static String canonicalGraphIdentityExclusionFacts(
            CategoryBinding category, RowObservation row, String role,
            int slotIndex, int alternativeIndex, ItemStack stack, String doorKind,
            DoorReplacementEvidence replacementEvidence)
            throws ExportFailure {
        GTRecipe recipe = row.recipe;
        StringBuilder canonical = new StringBuilder(8192);
        frame(canonical, "contract", UNREGISTERED_DOOR_RECYCLING_CONTRACT);
        frame(canonical, "categoryId", category.plan.categoryId);
        frame(canonical, "handlerClass", category.loaded.getClass().getName());
        frame(canonical, "handlerId", category.plan.handlerId);
        frame(canonical, "loadIdentifier", category.plan.loadIdentifier);
        frame(canonical, "map", category.map.unlocalizedName);
        frame(canonical, "overlay", category.loaded.getOverlayIdentifier());
        frame(canonical, "backendClass", category.backend.getClass().getName());
        frame(canonical, "frontendClass", category.frontend.getClass().getName());
        frame(canonical, "categoryRecipeCount", category.loaded.arecipes.size());
        frame(canonical, "recipeClass", recipe.getClass().getName());
        frame(canonical, "recipeCategory", recipe.getRecipeCategory().unlocalizedName);
        frame(canonical, "doorKind", doorKind);
        frame(canonical, "doorReplacementEvidence", replacementEvidence.canonicalFacts);
        frame(canonical, "role", role);
        frame(canonical, "slotIndex", slotIndex);
        frame(canonical, "alternativeIndex", alternativeIndex);
        frame(canonical, "offendingVisibleStack", graphStackDescription(stack));
        frame(canonical, "visibleInputs", positionedListFacts(row.ingredients));
        frame(canonical, "visibleResult", positionedStackFacts(row.result));
        frame(canonical, "visibleOthers", positionedListFacts(row.otherStacks));
        frame(canonical, "rawInputs", canonicalItemArrayFacts(recipe.mInputs));
        frame(canonical, "rawOutputs", canonicalItemArrayFacts(recipe.mOutputs));
        frame(canonical, "rawFluidInputs", rawFluidArrayFacts(recipe.mFluidInputs));
        frame(canonical, "rawFluidOutputs", rawFluidArrayFacts(recipe.mFluidOutputs));
        frame(canonical, "oreDictionaryAlternatives",
                allCanonicalOreDictAlternativeFacts(recipe));
        frame(canonical, "chances", intArrayFacts(recipe.mChances));
        frame(canonical, "specialItems", recipe.mSpecialItems instanceof ItemStack
                ? graphStackDescription((ItemStack) recipe.mSpecialItems)
                : className(recipe.mSpecialItems));
        frame(canonical, "duration", recipe.mDuration);
        frame(canonical, "eut", recipe.mEUt);
        frame(canonical, "specialValue", recipe.mSpecialValue);
        frame(canonical, "enabled", recipe.mEnabled);
        frame(canonical, "hidden", recipe.mHidden);
        frame(canonical, "fake", recipe.mFakeRecipe);
        frame(canonical, "canBeBuffered", recipe.mCanBeBuffered);
        frame(canonical, "needsEmptyOutput", recipe.mNeedsEmptyOutput);
        frame(canonical, "nbtSensitive", recipe.isNBTSensitive);
        frame(canonical, "owners", recipeOwnerFacts(recipe));
        String[] description = recipe.getNeiDesc();
        frame(canonical, "descriptionCount", description == null ? -1 : description.length);
        if (description != null) {
            for (int index = 0; index < description.length; index++) {
                frame(canonical, "description[" + index + "]", description[index]);
            }
        }
        appendMetadataFacts(canonical, recipe);
        return canonical.toString();
    }

    private static String positionedListFacts(List<PositionedStack> positioned)
            throws ExportFailure {
        if (positioned == null) {
            return "<null>";
        }
        StringBuilder facts = new StringBuilder(1024);
        facts.append("count=").append(positioned.size()).append('[');
        for (int index = 0; index < positioned.size(); index++) {
            if (index > 0) {
                facts.append(';');
            }
            facts.append(index).append("={")
                    .append(positionedStackFacts(positioned.get(index))).append('}');
        }
        return facts.append(']').toString();
    }

    private static String positionedStackFacts(PositionedStack positioned)
            throws ExportFailure {
        if (positioned == null) {
            return "<null>";
        }
        if (positioned.items == null || positioned.items.length == 0) {
            try {
                positioned.generatePermutations();
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                throw graphFailure("could not generate PositionedStack alternatives while "
                        + "canonicalizing an exclusion", error);
            }
        }
        if (positioned.items == null || positioned.items.length == 0) {
            throw graphFailure("PositionedStack has no alternatives while canonicalizing "
                    + "an exclusion");
        }
        StringBuilder facts = new StringBuilder(512);
        facts.append("x=").append(positioned.relx)
                .append(",y=").append(positioned.rely)
                .append(",count=").append(positioned.items.length).append('[');
        for (int index = 0; index < positioned.items.length; index++) {
            if (index > 0) {
                facts.append(';');
            }
            facts.append(index).append("={")
                    .append(graphStackDescription(positioned.items[index])).append('}');
        }
        return facts.append(']').toString();
    }

    private static String rawFluidArrayFacts(FluidStack[] stacks) {
        if (stacks == null) {
            return "<null>";
        }
        StringBuilder facts = new StringBuilder(Math.max(64, stacks.length * 128));
        facts.append("count=").append(stacks.length).append('[');
        for (int index = 0; index < stacks.length; index++) {
            if (index > 0) {
                facts.append(';');
            }
            FluidStack stack = stacks[index];
            if (stack == null || stack.getFluid() == null) {
                facts.append(index).append("={<null>}");
                continue;
            }
            String nbt = stack.tag == null ? "absent"
                    : "sha256:" + Naming.sha256(NbtCanonicalizer.canonical(stack.tag));
            facts.append(index).append("={name=")
                    .append(stack.getFluid().getName())
                    .append(",defaultName=")
                    .append(net.minecraftforge.fluids.FluidRegistry.getDefaultFluidName(
                            stack.getFluid()))
                    .append(",class=").append(stack.getFluid().getClass().getName())
                    .append(",amount=").append(stack.amount)
                    .append(",nbt=").append(nbt).append('}');
        }
        return facts.append(']').toString();
    }

    private static String allCanonicalOreDictAlternativeFacts(GTRecipe recipe) {
        if (!(recipe instanceof GTRecipe.GTRecipe_WithAlt)) {
            return "<not-applicable>";
        }
        ItemStack[][] alternatives = ((GTRecipe.GTRecipe_WithAlt) recipe).mOreDictAlt;
        if (alternatives == null) {
            return "<null>";
        }
        StringBuilder facts = new StringBuilder(1024);
        facts.append("slots=").append(alternatives.length).append('[');
        for (int index = 0; index < alternatives.length; index++) {
            if (index > 0) {
                facts.append(';');
            }
            facts.append(index).append("={")
                    .append(canonicalItemArrayFacts(alternatives[index])).append('}');
        }
        return facts.append(']').toString();
    }

    private static String intArrayFacts(int[] values) {
        if (values == null) {
            return "<null>";
        }
        StringBuilder facts = new StringBuilder(values.length * 12 + 16);
        facts.append("count=").append(values.length).append('[');
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                facts.append(',');
            }
            facts.append(values[index]);
        }
        return facts.append(']').toString();
    }

    private static String graphIdentityFailureFacts(
            CategoryBinding category, RowObservation row, String role,
            int slotIndex, int alternativeIndex, ItemStack stack, Throwable error) {
        GTRecipe recipe = row.recipe;
        gregtech.api.recipe.RecipeCategory recipeCategory = recipe.getRecipeCategory();
        StringBuilder facts = new StringBuilder(2048);
        frame(facts, "categoryId", category.plan.categoryId);
        frame(facts, "handlerId", category.plan.handlerId);
        frame(facts, "map", category.map.unlocalizedName);
        frame(facts, "overlay", category.loaded.getOverlayIdentifier());
        frame(facts, "sourceIndex", row.sourceIndex);
        frame(facts, "role", role);
        frame(facts, "slotIndex", slotIndex);
        frame(facts, "alternativeIndex", alternativeIndex);
        frame(facts, "stack", graphStackDescription(stack));
        frame(facts, "cachedClass", row.cached.getClass().getName());
        frame(facts, "recipeClass", recipe.getClass().getName());
        frame(facts, "recipeCategory", recipeCategory == null
                ? "<null>" : recipeCategory.unlocalizedName);
        frame(facts, "owners", recipeOwnerFacts(recipe));
        frame(facts, "rawInputs", rawItemArrayFacts(recipe.mInputs));
        frame(facts, "rawOutputs", rawItemArrayFacts(recipe.mOutputs));
        frame(facts, "relevantOreDictAlternatives",
                relevantOreDictAlternativeFacts(recipe, role, slotIndex));
        frame(facts, "specialItems", recipe.mSpecialItems instanceof ItemStack
                ? graphStackDescription((ItemStack) recipe.mSpecialItems)
                : className(recipe.mSpecialItems));
        frame(facts, "duration", recipe.mDuration);
        frame(facts, "eut", recipe.mEUt);
        frame(facts, "specialValue", recipe.mSpecialValue);
        frame(facts, "fake", recipe.mFakeRecipe);
        frame(facts, "enabled", recipe.mEnabled);
        frame(facts, "hidden", recipe.mHidden);
        frame(facts, "causeType", className(error));
        frame(facts, "causeMessage", error == null
                ? "<null>" : Naming.plainText(error.getMessage()));
        return facts.toString();
    }

    private static String graphStackDescription(ItemStack stack) {
        StringBuilder description = new StringBuilder(StackIdentity.describe(stack));
        if (stack != null && stack.getItem() != null) {
            try {
                description.append(", unlocalizedName=")
                        .append(Naming.plainText(stack.getUnlocalizedName()));
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                description.append(", unlocalizedName=<error:")
                        .append(error.getClass().getName()).append('>');
            }
            if (stack.getItem() instanceof net.minecraft.item.ItemBlock) {
                net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromItem(
                        stack.getItem());
                Object blockName = block == null ? null
                        : net.minecraft.block.Block.blockRegistry.getNameForObject(block);
                description.append(", itemBlockBlockRegistryId=")
                        .append(blockName == null ? "<unregistered>" : blockName)
                        .append(", itemBlockBlockClass=")
                        .append(block == null ? "<null>" : block.getClass().getName());
            }
        }
        return description.toString();
    }

    private static String recipeOwnerFacts(GTRecipe recipe) {
        if (recipe.owners == null) {
            return "<null>";
        }
        List<String> owners = new ArrayList<String>(recipe.owners.size());
        for (int index = 0; index < recipe.owners.size(); index++) {
            if (recipe.owners.get(index) == null) {
                owners.add("<null>");
            } else {
                owners.add(recipe.owners.get(index).getModId());
            }
        }
        return owners.toString();
    }

    private static String relevantOreDictAlternativeFacts(
            GTRecipe recipe, String role, int slotIndex) {
        if (!(recipe instanceof GTRecipe.GTRecipe_WithAlt) || !"input".equals(role)) {
            return "<not-applicable>";
        }
        ItemStack[][] alternatives = ((GTRecipe.GTRecipe_WithAlt) recipe).mOreDictAlt;
        if (alternatives == null || slotIndex < 0 || slotIndex >= alternatives.length) {
            return "<missing-slot>";
        }
        return rawItemArrayFacts(alternatives[slotIndex]);
    }

    private static String canonicalItemArrayFacts(ItemStack[] stacks) {
        if (stacks == null) {
            return "<null>";
        }
        StringBuilder facts = new StringBuilder(Math.max(64, stacks.length * 128));
        facts.append("count=").append(stacks.length).append('[');
        for (int index = 0; index < stacks.length; index++) {
            if (index > 0) {
                facts.append(';');
            }
            facts.append(index).append("={")
                    .append(graphStackDescription(stacks[index])).append('}');
        }
        return facts.append(']').toString();
    }

    private static String rawItemArrayFacts(ItemStack[] stacks) {
        if (stacks == null) {
            return "<null>";
        }
        final int limit = 64;
        StringBuilder facts = new StringBuilder(Math.min(4096, stacks.length * 128));
        facts.append("count=").append(stacks.length).append('[');
        int recorded = Math.min(stacks.length, limit);
        for (int index = 0; index < recorded; index++) {
            if (index > 0) {
                facts.append(';');
            }
            facts.append(index).append("={")
                    .append(graphStackDescription(stacks[index])).append('}');
        }
        if (stacks.length > recorded) {
            facts.append(";omitted=").append(stacks.length - recorded);
        }
        return facts.append(']').toString();
    }

    private static List<String> visibleInputFacts(
            List<PositionedStack> ingredients, String categoryId, int sourceIndex)
            throws ExportFailure {
        List<String> facts = new ArrayList<String>();
        for (int slotIndex = 0; slotIndex < ingredients.size(); slotIndex++) {
            PositionedStack positioned = ingredients.get(slotIndex);
            if (positioned == null) {
                throw failure(categoryId + " #" + sourceIndex
                        + " has null visible input slot #" + slotIndex);
            }
            if (positioned.items == null || positioned.items.length == 0) {
                positioned.generatePermutations();
            }
            if (positioned.items == null || positioned.items.length == 0) {
                throw failure(categoryId + " #" + sourceIndex
                        + " has no alternatives in visible input slot #" + slotIndex);
            }
            List<String> alternatives = new ArrayList<String>(positioned.items.length);
            for (int alternativeIndex = 0;
                 alternativeIndex < positioned.items.length; alternativeIndex++) {
                ItemStack stack = positioned.items[alternativeIndex];
                if (stack == null || stack.getItem() == null) {
                    throw failure(categoryId + " #" + sourceIndex
                            + " has null visible input alternative slot=" + slotIndex
                            + " alternative=" + alternativeIndex);
                }
                try {
                    StackIdentity identity = StackIdentity.of(stack);
                    alternatives.add(identity.key + "|amount=" + identity.amount);
                } catch (Throwable error) {
                    FatalErrors.rethrowIfFatal(error);
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "GregTech visible input identity categoryId=" + categoryId
                                    + " sourceIndex=" + sourceIndex + " slot=" + slotIndex
                                    + " alternative=" + alternativeIndex, error);
                }
            }
            Collections.sort(alternatives);
            StringBuilder slot = new StringBuilder(256);
            frame(slot, "slotIndex", slotIndex);
            frame(slot, "x", positioned.relx);
            frame(slot, "y", positioned.rely);
            frame(slot, "alternativeCount", alternatives.size());
            for (String alternative : alternatives) {
                frame(slot, "stack", alternative);
            }
            facts.add(slot.toString());
        }
        return facts;
    }

    private static ExportFailure unclassified(
            CategoryBinding category, RowObservation row, String detail) {
        GTRecipe recipe = row.recipe;
        return failure(category.plan.categoryId + " #" + row.sourceIndex
                + " map=" + category.map.unlocalizedName
                + " backend=" + category.backend.getClass().getName()
                + " frontend=" + category.frontend.getClass().getName()
                + " recipe=" + recipe.getClass().getName()
                + " rawInputs=" + row.rawItemInputs + "+" + row.rawFluidInputs
                + " neiInputs=" + row.ingredients.size()
                + " rawOutputs=" + row.rawItemOutputs + "+" + row.rawFluidOutputs
                + " neiResult=" + (row.result != null)
                + " neiOther=" + row.otherStacks.size()
                + " duration=" + recipe.mDuration + " eut=" + recipe.mEUt
                + " special=" + recipe.mSpecialValue
                + " fake=" + recipe.mFakeRecipe
                + " enabled=" + recipe.mEnabled + " hidden=" + recipe.mHidden
                + ": " + detail);
    }

    private static ExportFailure failure(String message) {
        return new ExportFailure("RECIPE_SEMANTICS",
                "GregTech outputless semantic preflight: " + message);
    }

    private static ExportFailure graphFailure(String message) {
        return new ExportFailure("ITEM_IDENTITY",
                "GregTech stale vanilla-door recycling preflight: " + message);
    }

    private static ExportFailure graphFailure(String message, Throwable cause) {
        return new ExportFailure("ITEM_IDENTITY",
                "GregTech stale vanilla-door recycling preflight: " + message, cause);
    }

    private static String className(Object value) {
        return value == null ? "<null>" : value.getClass().getName();
    }

    private static void frame(StringBuilder target, String label, Object value) {
        appendFrame(target, label);
        appendFrame(target, value == null ? "<null>" : String.valueOf(value));
    }

    private static void appendFrame(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }
}
