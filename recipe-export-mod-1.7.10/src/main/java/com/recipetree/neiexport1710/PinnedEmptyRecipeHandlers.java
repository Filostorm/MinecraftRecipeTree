package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTBase;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Immutable GTNH 2.8.4 exclusions for exact, source-backed empty NEI recipe categories.
 *
 * <p>Exporters 1.0.53 and 1.0.68 captured the complete 20-handler promotion inventory before
 * rendering.
 * Every exclusion below re-reads the authoritative source, registered prototype cache, and a
 * freshly loaded complete-category cache, then requires the exact reviewed counts and
 * fingerprints. This is a narrow exception at planning time only: the ordinary category loader
 * still rejects every zero-recipe result.</p>
 */
final class PinnedEmptyRecipeHandlers {
    static final String CONTRACT = "gtnh-2.8.4-pinned-empty-source-discovery-v1";
    static final String POLICY_ACTION = "excluded-empty-category";
    static final String POLICY_CONTRACT =
            "empty-category:gtnh-2.8.4-source-backed-exact-zero-v1";
    static final String EXPECTED_INVENTORY_SHA256 =
            "d465e91be9006c774a15a306ba1acd9563f398d857dabc9dccc3b66f04a628b1";
    private static final String SOURCE_FINGERPRINT_DOMAIN =
            CONTRACT + "/source-multiset-v1";
    private static final String CACHE_FINGERPRINT_DOMAIN =
            CONTRACT + "/nei-cache-multiset-v1";
    private static final String INVENTORY_FINGERPRINT_DOMAIN =
            CONTRACT + "/promotion-inventory-v1";
    private static final int EXPECTED_HANDLER_COUNT = 20;
    private static final int MAX_CANONICAL_DEPTH = 20;

    enum SourceKind {
        ADV_SOLAR_TRANSFORMER,
        CREATIVECORE_RECIPE_INFO,
        EXTRAUTILITIES_MICROBLOCKS,
        AMUN_RA_CIRCUIT_FABRICATOR,
        AVARITIA_COMPRESSION,
        GALAXYSPACE_ASSEMBLY,
        IC2_ADV_SHAPELESS,
        IC2_MACHINE_MAP,
        LOGISTICS_PIPES_SOLDERING,
        FORESTRY_BUTTERFLY_MUTATIONS,
        WCT_AE_SHAPED,
        RAILCRAFT_ROCK_CRUSHER,
        RAILCRAFT_ROLLING_SHAPELESS
    }

    private enum CraftingSourceFilter {
        TYPE_ONLY,
        IC2_ADV_SHAPELESS,
        WCT_SHAPED
    }

    static final class Spec {
        final String handlerClass;
        final String categoryId;
        final String operation;
        final SourceKind sourceKind;
        final String sourceContract;
        final Promotion promotion;

        private Spec(String handlerClass, String categoryId, String operation,
                     SourceKind sourceKind, String sourceContract,
                     Promotion promotion) {
            this.handlerClass = handlerClass;
            this.categoryId = categoryId;
            this.operation = operation;
            this.sourceKind = sourceKind;
            this.sourceContract = sourceContract;
            this.promotion = promotion;
        }

        String contractRow() {
            return handlerClass + "|" + categoryId + "|" + operation + "|"
                    + sourceContract;
        }

        String promotedEvidenceRow() {
            Observation observation = new Observation(this);
            observation.handlerId = handlerClass;
            observation.adapter = CompleteCategoryAdapters.Adapter.STANDARD.name();
            observation.operationSource = HandlerCategoryPlan.OPERATION_SOURCE_TRANSFER_RECT;
            promotion.populate(observation);
            return observation.row();
        }
    }

    /** Exact reviewed evidence from the exporter 1.0.53 discovery row. */
    static final class Promotion {
        final int sourceRegistryCount;
        final int rawSourceCount;
        final String sourceFingerprint;
        final String sourceEligibilityContract;
        final String sourceEligibilityTelemetry;
        final int eligibleSourceCount;
        final String eligibleSourceFingerprint;
        final int boundPrototypeSourceCount;
        final String boundPrototypeSourceFingerprint;
        final int prototypeCount;
        final String prototypeFingerprint;
        final int eligibleCount;
        final String eligibleFingerprint;

        private Promotion(
                int sourceRegistryCount, int rawSourceCount, String sourceFingerprint,
                String sourceEligibilityContract, String sourceEligibilityTelemetry,
                int eligibleSourceCount, String eligibleSourceFingerprint,
                int boundPrototypeSourceCount, String boundPrototypeSourceFingerprint,
                int prototypeCount, String prototypeFingerprint,
                int eligibleCount, String eligibleFingerprint) {
            this.sourceRegistryCount = sourceRegistryCount;
            this.rawSourceCount = rawSourceCount;
            this.sourceFingerprint = sourceFingerprint;
            this.sourceEligibilityContract = sourceEligibilityContract;
            this.sourceEligibilityTelemetry = sourceEligibilityTelemetry;
            this.eligibleSourceCount = eligibleSourceCount;
            this.eligibleSourceFingerprint = eligibleSourceFingerprint;
            this.boundPrototypeSourceCount = boundPrototypeSourceCount;
            this.boundPrototypeSourceFingerprint = boundPrototypeSourceFingerprint;
            this.prototypeCount = prototypeCount;
            this.prototypeFingerprint = prototypeFingerprint;
            this.eligibleCount = eligibleCount;
            this.eligibleFingerprint = eligibleFingerprint;
        }

        private void populate(Observation observation) {
            observation.sourceRegistryCount = sourceRegistryCount;
            observation.rawSourceCount = rawSourceCount;
            observation.sourceFingerprint = sourceFingerprint;
            observation.sourceEligibilityContract = sourceEligibilityContract;
            observation.sourceEligibilityTelemetry = sourceEligibilityTelemetry;
            observation.eligibleSourceCount = eligibleSourceCount;
            observation.eligibleSourceFingerprint = eligibleSourceFingerprint;
            observation.boundPrototypeSourceCount = boundPrototypeSourceCount;
            observation.boundPrototypeSourceFingerprint = boundPrototypeSourceFingerprint;
            observation.prototypeCount = prototypeCount;
            observation.prototypeFingerprint = prototypeFingerprint;
            observation.eligibleCount = eligibleCount;
            observation.eligibleFingerprint = eligibleFingerprint;
        }

        private void addMismatches(Observation observed) {
            mismatch(observed, "sourceRegistryCount", sourceRegistryCount,
                    observed.sourceRegistryCount);
            mismatch(observed, "rawSourceCount", rawSourceCount,
                    observed.rawSourceCount);
            mismatch(observed, "sourceFingerprint", sourceFingerprint,
                    observed.sourceFingerprint);
            mismatch(observed, "sourceEligibilityContract", sourceEligibilityContract,
                    observed.sourceEligibilityContract);
            mismatch(observed, "sourceEligibilityTelemetry", sourceEligibilityTelemetry,
                    observed.sourceEligibilityTelemetry);
            mismatch(observed, "eligibleSourceCount", eligibleSourceCount,
                    observed.eligibleSourceCount);
            mismatch(observed, "eligibleSourceFingerprint", eligibleSourceFingerprint,
                    observed.eligibleSourceFingerprint);
            mismatch(observed, "boundPrototypeSourceCount", boundPrototypeSourceCount,
                    observed.boundPrototypeSourceCount);
            mismatch(observed, "boundPrototypeSourceFingerprint",
                    boundPrototypeSourceFingerprint,
                    observed.boundPrototypeSourceFingerprint);
            mismatch(observed, "prototypeCount", prototypeCount,
                    observed.prototypeCount);
            mismatch(observed, "prototypeFingerprint", prototypeFingerprint,
                    observed.prototypeFingerprint);
            mismatch(observed, "eligibleCount", eligibleCount,
                    observed.eligibleCount);
            mismatch(observed, "eligibleFingerprint", eligibleFingerprint,
                    observed.eligibleFingerprint);
        }

        private static void mismatch(
                Observation observation, String field, int expected, int actual) {
            if (expected != actual) {
                observation.issues.add(field + " drifted; expected " + expected
                        + ", got " + actual);
            }
        }

        private static void mismatch(
                Observation observation, String field, String expected, String actual) {
            if (expected == null ? actual != null : !expected.equals(actual)) {
                observation.issues.add(field + " drifted; expected " + quoted(expected)
                        + ", got " + quoted(actual));
            }
        }
    }

    private static final class SourceSnapshot {
        final int registryCount;
        final List<Object> rawRows;
        final List<Object> eligibleRows;
        final String eligibilityContract;
        final String eligibilityTelemetry;
        final List<Object> boundPrototypeRows;

        SourceSnapshot(int registryCount, List<Object> rawRows) {
            this(registryCount, rawRows, null, "<not-audited>",
                    "<not-audited>", null);
        }

        SourceSnapshot(int registryCount, List<Object> rawRows,
                       List<Object> boundPrototypeRows) {
            this(registryCount, rawRows, null, "<not-audited>",
                    "<not-audited>", boundPrototypeRows);
        }

        SourceSnapshot(int registryCount, List<Object> rawRows,
                       List<Object> eligibleRows, String eligibilityContract,
                       String eligibilityTelemetry) {
            this(registryCount, rawRows, eligibleRows, eligibilityContract,
                    eligibilityTelemetry, null);
        }

        private SourceSnapshot(int registryCount, List<Object> rawRows,
                               List<Object> eligibleRows, String eligibilityContract,
                               String eligibilityTelemetry,
                               List<Object> boundPrototypeRows) {
            this.registryCount = registryCount;
            this.rawRows = Collections.unmodifiableList(new ArrayList<Object>(rawRows));
            this.eligibleRows = eligibleRows == null ? null
                    : Collections.unmodifiableList(new ArrayList<Object>(eligibleRows));
            this.eligibilityContract = eligibilityContract;
            this.eligibilityTelemetry = eligibilityTelemetry;
            this.boundPrototypeRows = boundPrototypeRows == null ? null
                    : Collections.unmodifiableList(
                            new ArrayList<Object>(boundPrototypeRows));
        }
    }

    interface EligibilityProbe {
        boolean isEligible(Object row) throws Exception;
    }

    static final class FilteredRows {
        final List<Object> rawRows;
        final List<Object> eligibleRows;

        FilteredRows(List<Object> rawRows, List<Object> eligibleRows) {
            this.rawRows = Collections.unmodifiableList(new ArrayList<Object>(rawRows));
            this.eligibleRows = Collections.unmodifiableList(
                    new ArrayList<Object>(eligibleRows));
        }
    }

    private static final class CacheSnapshot {
        final int count;
        final String fingerprint;

        CacheSnapshot(int count, String fingerprint) {
            this.count = count;
            this.fingerprint = fingerprint;
        }
    }

    private static final class Observation {
        final Spec spec;
        final List<String> issues = new ArrayList<String>();
        String handlerId = "<unavailable>";
        String adapter = "<unavailable>";
        String operationSource = "<unavailable>";
        int sourceRegistryCount = -1;
        int rawSourceCount = -1;
        String sourceFingerprint = "<unavailable>";
        String sourceEligibilityContract = "<not-audited>";
        String sourceEligibilityTelemetry = "<not-audited>";
        int eligibleSourceCount = -1;
        String eligibleSourceFingerprint = "<not-audited>";
        int boundPrototypeSourceCount = -1;
        String boundPrototypeSourceFingerprint = "<not-applicable>";
        int prototypeCount = -1;
        String prototypeFingerprint = "<unavailable>";
        int eligibleCount = -1;
        String eligibleFingerprint = "<unavailable>";

        Observation(Spec spec) {
            this.spec = spec;
        }

        String row() {
            Collections.sort(issues);
            StringBuilder value = new StringBuilder(1024);
            value.append("contract=").append(quoted(CONTRACT))
                    .append(" class=").append(quoted(spec.handlerClass))
                    .append(" categoryId=").append(spec.categoryId)
                    .append(" operation=").append(quoted(spec.operation))
                    .append(" handlerId=").append(quoted(handlerId))
                    .append(" adapter=").append(quoted(adapter))
                    .append(" operationSource=").append(quoted(operationSource))
                    .append(" sourceContract=").append(quoted(spec.sourceContract))
                    .append(" sourceRegistryCount=").append(sourceRegistryCount)
                    .append(" rawSourceCount=").append(rawSourceCount)
                    .append(" sourceFingerprint=").append(sourceFingerprint)
                    .append(" sourceEligibilityContract=")
                    .append(quoted(sourceEligibilityContract))
                    .append(" sourceEligibilityTelemetry=")
                    .append(quoted(sourceEligibilityTelemetry))
                    .append(" eligibleSourceCount=").append(eligibleSourceCount)
                    .append(" eligibleSourceFingerprint=")
                    .append(eligibleSourceFingerprint)
                    .append(" boundPrototypeSourceCount=").append(boundPrototypeSourceCount)
                    .append(" boundPrototypeSourceFingerprint=")
                    .append(boundPrototypeSourceFingerprint)
                    .append(" prototypeCount=").append(prototypeCount)
                    .append(" prototypeFingerprint=").append(prototypeFingerprint)
                    .append(" eligibleCount=").append(eligibleCount)
                    .append(" eligibleFingerprint=").append(eligibleFingerprint)
                    .append(" status=").append(issues.isEmpty() ? "observed" : "failure")
                    .append(" issues=[");
            for (int index = 0; index < issues.size(); index++) {
                if (index > 0) {
                    value.append(',');
                }
                value.append(quoted(issues.get(index)));
            }
            return value.append(']').toString();
        }
    }

    private static final Map<String, Promotion> PROMOTIONS_BY_CLASS = createPromotions();
    private static final List<Spec> SPECS = createSpecs();
    private static final Map<String, Spec> SPECS_BY_CLASS = indexSpecs(SPECS);

    private PinnedEmptyRecipeHandlers() {
    }

    /**
     * Returns the exact reviewed evidence row when {@code plan} is a promoted empty category,
     * or {@code null} when the ordinary global nonempty-category invariant still applies.
     */
    static String validatePromotedPlan(HandlerCategoryPlan plan) throws ExportFailure {
        if (plan == null || plan.prototype == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "promoted empty-category validation received a null plan");
        }
        Spec spec = SPECS_BY_CLASS.get(plan.prototype.getClass().getName());
        if (spec == null) {
            return null;
        }
        Observation observation = new Observation(spec);
        observePlan(plan, observation);
        spec.promotion.addMismatches(observation);
        if (!observation.issues.isEmpty()) {
            Collections.sort(observation.issues);
            StringBuilder message = new StringBuilder(4096);
            message.append(spec.handlerClass)
                    .append(" no longer matches its immutable empty-category policy ")
                    .append(POLICY_CONTRACT);
            for (String issue : observation.issues) {
                message.append("\n- ").append(issue);
            }
            message.append("\n- observed ").append(observation.row());
            throw new ExportFailure("HANDLER_UNLOADED", message.toString());
        }
        String row = observation.row();
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Verified promoted empty-category policy class={} "
                        + "categoryId={} operation={} evidence={}",
                spec.handlerClass, spec.categoryId, spec.operation, row);
        return row;
    }

    /** Requires all 20 promoted rows exactly once and binds their discovery inventory digest. */
    static void requirePromotedInventory(
            Set<String> observedHandlerClasses, List<String> observedRows)
            throws ExportFailure {
        List<String> issues = new ArrayList<String>(validateSpecLedger(SPECS));
        Set<String> expectedClasses = new TreeSet<String>(SPECS_BY_CLASS.keySet());
        Set<String> observedClasses = observedHandlerClasses == null
                ? Collections.<String>emptySet()
                : new TreeSet<String>(observedHandlerClasses);
        if (!expectedClasses.equals(observedClasses)) {
            Set<String> missing = new TreeSet<String>(expectedClasses);
            missing.removeAll(observedClasses);
            Set<String> unexpected = new TreeSet<String>(observedClasses);
            unexpected.removeAll(expectedClasses);
            issues.add("handler inventory drifted; missing=" + missing
                    + ", unexpected=" + unexpected);
        }

        List<String> expectedRows = promotedEvidenceRows();
        List<String> actualRows = observedRows == null
                ? Collections.<String>emptyList()
                : new ArrayList<String>(observedRows);
        Collections.sort(actualRows);
        if (!expectedRows.equals(actualRows)) {
            issues.add("reviewed promotion rows differ from the live validated rows");
        }
        String inventorySha256 = stableMultisetFingerprint(
                INVENTORY_FINGERPRINT_DOMAIN, actualRows);
        if (!EXPECTED_INVENTORY_SHA256.equals(inventorySha256)) {
            issues.add("promotion inventory SHA-256 drifted; expected "
                    + EXPECTED_INVENTORY_SHA256 + ", got " + inventorySha256);
        }
        if (!issues.isEmpty()) {
            Collections.sort(issues);
            StringBuilder message = new StringBuilder(4096);
            message.append("promoted empty-category inventory has ")
                    .append(issues.size()).append(" issue(s)");
            for (String issue : issues) {
                message.append("\n- ").append(issue);
            }
            throw new ExportFailure("HANDLER_UNLOADED", message.toString());
        }
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Verified {} immutable source-backed empty-category "
                        + "policies; inventorySha256={}",
                actualRows.size(), inventorySha256);
    }

    private static void observePlan(HandlerCategoryPlan plan, Observation observation) {
        Spec spec = observation.spec;
        boolean exactBinding = true;
        Integer auditedEligibleSourceCount = null;
        try {
            observation.handlerId = plan.handlerId;
            observation.adapter = plan.adapter.name();
            observation.operationSource = plan.operationSource;
            Class<?> expectedClass = Class.forName(
                    spec.handlerClass, false, plan.prototype.getClass().getClassLoader());
            if (plan.prototype.getClass() != expectedClass) {
                observation.issues.add("prototype runtime class drifted to "
                        + plan.prototype.getClass().getName());
                exactBinding = false;
            }
            if (!spec.handlerClass.equals(plan.handlerId)) {
                observation.issues.add("raw handler lineage drifted to " + plan.handlerId);
                exactBinding = false;
            }
            if (!spec.categoryId.equals(plan.categoryId)) {
                observation.issues.add("category ID drifted to " + plan.categoryId);
                exactBinding = false;
            }
            if (!spec.operation.equals(plan.loadIdentifier)) {
                observation.issues.add("selected complete-category operation drifted to "
                        + plan.loadIdentifier);
                exactBinding = false;
            }
            if (!HandlerCategoryPlan.OPERATION_SOURCE_TRANSFER_RECT.equals(
                    plan.operationSource)) {
                observation.issues.add("selected operation source drifted to "
                        + plan.operationSource);
                exactBinding = false;
            }
            if (plan.adapter != CompleteCategoryAdapters.Adapter.STANDARD) {
                observation.issues.add("candidate unexpectedly uses adapter "
                        + plan.adapter.name());
                exactBinding = false;
            }
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            observation.issues.add("structural binding inspection failed: "
                    + failureDetail(error));
            exactBinding = false;
        }

        try {
            SourceSnapshot source = readSource(spec, plan.prototype);
            observation.sourceRegistryCount = source.registryCount;
            observation.rawSourceCount = source.rawRows.size();
            observation.sourceFingerprint = fingerprintObjects(
                    SOURCE_FINGERPRINT_DOMAIN + "/raw/" + spec.handlerClass + "/"
                            + spec.sourceKind.name(),
                    source.rawRows, spec.sourceKind);
            observation.sourceEligibilityContract = source.eligibilityContract;
            observation.sourceEligibilityTelemetry = source.eligibilityTelemetry;
            if (source.eligibleRows != null) {
                observation.eligibleSourceCount = source.eligibleRows.size();
                auditedEligibleSourceCount = Integer.valueOf(source.eligibleRows.size());
                observation.eligibleSourceFingerprint = fingerprintObjects(
                        SOURCE_FINGERPRINT_DOMAIN + "/eligible/" + spec.handlerClass + "/"
                                + spec.sourceKind.name(),
                        source.eligibleRows, spec.sourceKind);
                if (source.eligibleRows.size() > source.rawRows.size()) {
                    observation.issues.add("audited eligible source count exceeds raw source "
                            + "count: eligible=" + source.eligibleRows.size()
                            + " raw=" + source.rawRows.size());
                }
                if (!source.eligibleRows.isEmpty()) {
                    observation.issues.add("audited source eligibility is no longer empty: "
                            + source.eligibleRows.size());
                }
            }
            if (source.boundPrototypeRows != null) {
                observation.boundPrototypeSourceCount = source.boundPrototypeRows.size();
                observation.boundPrototypeSourceFingerprint = fingerprintObjects(
                        SOURCE_FINGERPRINT_DOMAIN + "/bound-prototype/"
                                + spec.handlerClass + "/" + spec.sourceKind.name(),
                        source.boundPrototypeRows, spec.sourceKind);
                if (source.rawRows.size() != source.boundPrototypeRows.size()) {
                    observation.issues.add("global source count " + source.rawRows.size()
                            + " disagrees with registered prototype-bound source count "
                            + source.boundPrototypeRows.size());
                }
            }
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            observation.issues.add("source inspection failed: " + failureDetail(error));
        }

        try {
            CacheSnapshot prototype = inspectCache(
                    plan.prototype, CACHE_FINGERPRINT_DOMAIN + "/prototype/"
                            + spec.handlerClass);
            observation.prototypeCount = prototype.count;
            observation.prototypeFingerprint = prototype.fingerprint;
            if (prototype.count != 0) {
                observation.issues.add("registered prototype cache is no longer empty: "
                        + prototype.count);
            }
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            observation.issues.add("prototype cache inspection failed: "
                    + failureDetail(error));
        }

        if (!exactBinding) {
            observation.issues.add(
                    "loaded-cache probe skipped because the exact operation binding drifted");
            return;
        }
        try {
            ICraftingHandler loaded = plan.loadCompleteCategoryAllowEmpty();
            CacheSnapshot eligible = inspectCache(
                    loaded, CACHE_FINGERPRINT_DOMAIN + "/eligible/" + spec.handlerClass);
            observation.eligibleCount = eligible.count;
            observation.eligibleFingerprint = eligible.fingerprint;
            if (auditedEligibleSourceCount != null
                    && eligible.count != auditedEligibleSourceCount.intValue()) {
                observation.issues.add("audited eligible source count "
                        + auditedEligibleSourceCount + " disagrees with loaded NEI cache count "
                        + eligible.count);
            }
            if (eligible.count != 0) {
                observation.issues.add("complete-category operation is no longer empty: "
                        + eligible.count);
            }
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            observation.issues.add("loaded cache inspection failed: "
                    + failureDetail(error));
        }
    }

    private static CacheSnapshot inspectCache(ICraftingHandler handler, String domain)
            throws ExportFailure {
        if (!(handler instanceof TemplateRecipeHandler)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "exact empty candidate is not a TemplateRecipeHandler: "
                            + handler.getClass().getName());
        }
        TemplateRecipeHandler template = (TemplateRecipeHandler) handler;
        if (template.arecipes == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "NEI arecipes cache is null for " + handler.getClass().getName());
        }
        int reported = handler.numRecipes();
        if (reported < 0 || reported != template.arecipes.size()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "numRecipes/arecipes binding drifted for " + handler.getClass().getName()
                            + ": reported=" + reported
                            + " cache=" + template.arecipes.size());
        }
        return new CacheSnapshot(
                reported,
                fingerprintObjects(domain,
                        new ArrayList<Object>(template.arecipes), null));
    }

    private static SourceSnapshot readSource(Spec spec, ICraftingHandler prototype)
            throws Exception {
        ClassLoader loader = prototype.getClass().getClassLoader();
        switch (spec.sourceKind) {
            case ADV_SOLAR_TRANSFORMER:
                return staticFieldSource(loader, "advsolar.utils.MTRecipeManager",
                        "transformerRecipes");
            case CREATIVECORE_RECIPE_INFO:
                return craftingSource(loader,
                        "com.creativemd.creativecore.common.recipe.IRecipeInfo",
                        CraftingSourceFilter.TYPE_ONLY, prototype);
            case EXTRAUTILITIES_MICROBLOCKS:
                return craftingSource(loader,
                        "com.rwtema.extrautils.multipart.microblock.RecipeMicroBlocks",
                        CraftingSourceFilter.TYPE_ONLY, prototype);
            case AMUN_RA_CIRCUIT_FABRICATOR:
                return staticMethodSource(loader,
                        "de.katzenpapst.amunra.nei.NEIAmunRaConfig",
                        "getCircuitFabricatorRecipes");
            case AVARITIA_COMPRESSION:
                return staticMethodSource(loader,
                        "fox.spiteful.avaritia.crafting.CompressorManager", "getRecipes");
            case GALAXYSPACE_ASSEMBLY:
                return galaxySpaceSource(loader, prototype);
            case IC2_ADV_SHAPELESS:
                return craftingSource(loader, "ic2.core.AdvShapelessRecipe",
                        CraftingSourceFilter.IC2_ADV_SHAPELESS, prototype);
            case IC2_MACHINE_MAP:
                return instanceMethodSource(prototype, "getRecipeList");
            case LOGISTICS_PIPES_SOLDERING:
                return staticMethodSource(loader,
                        "logisticspipes.recipes.SolderingStationRecipes", "getRecipes");
            case FORESTRY_BUTTERFLY_MUTATIONS:
                return butterflySource(loader, prototype);
            case WCT_AE_SHAPED:
                return craftingSource(loader,
                        "net.p455w0rd.wirelesscraftingterminal.api.recipes.game.ShapedRecipe",
                        CraftingSourceFilter.WCT_SHAPED, prototype);
            case RAILCRAFT_ROCK_CRUSHER:
                return railcraftManagerSource(loader, "rockCrusher",
                        "mods.railcraft.api.crafting.IRockCrusherCraftingManager",
                        "getRecipes", false);
            case RAILCRAFT_ROLLING_SHAPELESS:
                return railcraftManagerSource(loader, "rollingMachine",
                        "mods.railcraft.api.crafting.IRollingMachineCraftingManager",
                        "getRecipeList", true);
            default:
                throw new IllegalStateException("unhandled source kind " + spec.sourceKind);
        }
    }

    private static SourceSnapshot craftingSource(
            ClassLoader loader, String recipeTypeName, CraftingSourceFilter filter,
            final ICraftingHandler prototype)
            throws Exception {
        List<?> registry = CraftingManager.getInstance().getRecipeList();
        if (registry == null) {
            throw new IllegalStateException("CraftingManager recipe list is null");
        }
        final Class<?> recipeType = Class.forName(recipeTypeName, false, loader);
        if (filter == CraftingSourceFilter.TYPE_ONLY) {
            FilteredRows rows = partitionAssignableRows(registry, recipeType, null);
            return new SourceSnapshot(registry.size(), rows.rawRows);
        }
        if (filter == CraftingSourceFilter.IC2_ADV_SHAPELESS) {
            final Method canShow = requireBooleanZeroArgumentMethod(recipeType, "canShow");
            final Method createCachedRecipe = prototype.getClass().getMethod(
                    "createCachedRecipe", recipeType);
            if (Modifier.isStatic(createCachedRecipe.getModifiers())
                    || createCachedRecipe.getParameterTypes().length != 1
                    || createCachedRecipe.getParameterTypes()[0] != recipeType) {
                throw new IllegalStateException(prototype.getClass().getName()
                        + ".createCachedRecipe contract drifted");
            }
            final int[] canShowAccepted = {0};
            final int[] cacheRejected = {0};
            FilteredRows rows = partitionAssignableRows(
                    registry, recipeType, new EligibilityProbe() {
                        @Override
                        public boolean isEligible(Object row) throws Exception {
                            if (!invokeBoolean(canShow, row)) {
                                return false;
                            }
                            canShowAccepted[0]++;
                            if (invoke(createCachedRecipe, prototype, row) == null) {
                                cacheRejected[0]++;
                                return false;
                            }
                            return true;
                        }
                    });
            return new SourceSnapshot(
                    registry.size(), rows.rawRows, rows.eligibleRows,
                    "instanceof ic2.core.AdvShapelessRecipe && canShow() "
                            + "&& createCachedRecipe()!=null",
                    "raw=" + rows.rawRows.size()
                            + ",canShowAccepted=" + canShowAccepted[0]
                            + ",canShowRejected="
                            + (rows.rawRows.size() - canShowAccepted[0])
                            + ",cacheRejected=" + cacheRejected[0]
                            + ",eligible=" + rows.eligibleRows.size());
        }
        if (filter == CraftingSourceFilter.WCT_SHAPED) {
            final Method isEnabled = requireBooleanZeroArgumentMethod(
                    recipeType, "isEnabled");
            FilteredRows rows = partitionAssignableRows(
                    registry, recipeType, new EligibilityProbe() {
                        @Override
                        public boolean isEligible(Object row) throws Exception {
                            return invokeBoolean(isEnabled, row);
                        }
                    });
            return new SourceSnapshot(
                    registry.size(), rows.rawRows, rows.eligibleRows,
                    "instanceof WCT ShapedRecipe && isEnabled()",
                    "raw=" + rows.rawRows.size()
                            + ",isEnabledAccepted=" + rows.eligibleRows.size()
                            + ",isEnabledRejected="
                            + (rows.rawRows.size() - rows.eligibleRows.size())
                            + ",eligible=" + rows.eligibleRows.size());
        }
        throw new IllegalStateException("unhandled crafting source filter " + filter);
    }

    static FilteredRows partitionAssignableRows(
            List<?> registry, Class<?> rowType, EligibilityProbe eligibility)
            throws Exception {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        if (rowType == null) {
            throw new IllegalArgumentException("rowType must not be null");
        }
        List<Object> rawRows = new ArrayList<Object>();
        List<Object> eligibleRows = new ArrayList<Object>();
        for (Object row : registry) {
            // This is intentionally Class.isInstance (the reflective form of instanceof).
            // Exact-class comparison would diverge from all three pinned handler bytecodes.
            if (!rowType.isInstance(row)) {
                continue;
            }
            rawRows.add(row);
            if (eligibility == null || eligibility.isEligible(row)) {
                eligibleRows.add(row);
            }
        }
        return new FilteredRows(rawRows, eligibleRows);
    }

    private static Method requireBooleanZeroArgumentMethod(
            Class<?> owner, String methodName) throws NoSuchMethodException {
        Method method = owner.getMethod(methodName);
        if (Modifier.isStatic(method.getModifiers())
                || method.getParameterTypes().length != 0
                || method.getReturnType() != boolean.class) {
            throw new IllegalStateException(owner.getName() + "." + methodName
                    + " is no longer an instance boolean zero-argument method");
        }
        return method;
    }

    private static boolean invokeBoolean(Method method, Object target) throws Exception {
        Object value = invoke(method, target);
        if (!(value instanceof Boolean)) {
            throw new IllegalStateException(method.getDeclaringClass().getName() + "."
                    + method.getName() + " returned non-boolean "
                    + (value == null ? "null" : value.getClass().getName()));
        }
        return ((Boolean) value).booleanValue();
    }

    private static SourceSnapshot staticFieldSource(
            ClassLoader loader, String className, String fieldName) throws Exception {
        Class<?> owner = Class.forName(className, false, loader);
        Field field = owner.getDeclaredField(fieldName);
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new IllegalStateException(className + "." + fieldName + " is not static");
        }
        field.setAccessible(true);
        List<Object> rows = requireRows(
                field.get(null), className + "." + fieldName);
        return new SourceSnapshot(rows.size(), rows);
    }

    private static SourceSnapshot staticMethodSource(
            ClassLoader loader, String className, String methodName) throws Exception {
        Class<?> owner = Class.forName(className, false, loader);
        Method method = owner.getDeclaredMethod(methodName);
        if (!Modifier.isStatic(method.getModifiers()) || method.getParameterTypes().length != 0) {
            throw new IllegalStateException(className + "." + methodName
                    + " is not a static zero-argument method");
        }
        method.setAccessible(true);
        List<Object> rows = requireRows(
                invoke(method, null), className + "." + methodName + "()");
        return new SourceSnapshot(rows.size(), rows);
    }

    private static SourceSnapshot instanceMethodSource(
            Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        if (Modifier.isStatic(method.getModifiers()) || method.getParameterTypes().length != 0) {
            throw new IllegalStateException(target.getClass().getName() + "." + methodName
                    + " is not an instance zero-argument method");
        }
        List<Object> rows = requireRows(
                invoke(method, target), target.getClass().getName() + "." + methodName + "()");
        return new SourceSnapshot(rows.size(), rows);
    }

    private static SourceSnapshot galaxySpaceSource(
            ClassLoader loader, ICraftingHandler prototype) throws Exception {
        SourceSnapshot global = staticMethodSource(loader,
                "galaxyspace.core.recipe.AssemblyRecipes", "getRecipeList");
        Field recipes = prototype.getClass().getDeclaredField("recipes");
        if (Modifier.isStatic(recipes.getModifiers())
                || !Set.class.isAssignableFrom(recipes.getType())) {
            throw new IllegalStateException(prototype.getClass().getName()
                    + ".recipes is no longer an instance Set");
        }
        recipes.setAccessible(true);
        List<Object> bound = requireRows(
                recipes.get(prototype), prototype.getClass().getName() + ".recipes");
        return new SourceSnapshot(global.registryCount, global.rawRows, bound);
    }

    private static SourceSnapshot butterflySource(
            ClassLoader loader, ICraftingHandler prototype) throws Exception {
        Class<?> base = Class.forName(
                "net.bdew.neiaddons.forestry.BaseBreedingRecipeHandler", false, loader);
        if (!base.isInstance(prototype)) {
            throw new IllegalStateException("butterfly prototype no longer extends "
                    + base.getName());
        }
        Field speciesRoot = base.getDeclaredField("speciesRoot");
        if (Modifier.isStatic(speciesRoot.getModifiers())) {
            throw new IllegalStateException(base.getName() + ".speciesRoot became static");
        }
        speciesRoot.setAccessible(true);
        Object root = speciesRoot.get(prototype);
        if (root == null) {
            throw new IllegalStateException(base.getName() + ".speciesRoot is null");
        }
        Class<?> rootInterface = Class.forName(
                "forestry.api.genetics.ISpeciesRoot", false, loader);
        if (!rootInterface.isInstance(root)) {
            throw new IllegalStateException("speciesRoot runtime class no longer implements "
                    + rootInterface.getName());
        }
        Method mutations = rootInterface.getMethod("getMutations", boolean.class);
        List<Object> rows = requireRows(
                invoke(mutations, root, Boolean.FALSE),
                rootInterface.getName() + ".getMutations(false)");
        return new SourceSnapshot(rows.size(), rows);
    }

    private static SourceSnapshot railcraftManagerSource(
            ClassLoader loader, String fieldName, String interfaceName,
            String methodName, boolean shapelessOnly) throws Exception {
        Class<?> managerOwner = Class.forName(
                "mods.railcraft.api.crafting.RailcraftCraftingManager", false, loader);
        Field field = managerOwner.getDeclaredField(fieldName);
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new IllegalStateException(managerOwner.getName() + "." + fieldName
                    + " is not static");
        }
        field.setAccessible(true);
        Object manager = field.get(null);
        if (manager == null) {
            throw new IllegalStateException(managerOwner.getName() + "." + fieldName
                    + " is null");
        }
        Class<?> managerInterface = Class.forName(interfaceName, false, loader);
        if (!managerInterface.isInstance(manager)) {
            throw new IllegalStateException(fieldName + " runtime manager no longer implements "
                    + interfaceName);
        }
        Method method = managerInterface.getMethod(methodName);
        List<Object> registry = requireRows(
                invoke(method, manager), interfaceName + "." + methodName + "()");
        if (!shapelessOnly) {
            return new SourceSnapshot(registry.size(), registry);
        }
        Class<?> vanillaShapeless = Class.forName(
                "net.minecraft.item.crafting.ShapelessRecipes", false, loader);
        Class<?> oreShapeless = Class.forName(
                "net.minecraftforge.oredict.ShapelessOreRecipe", false, loader);
        Method getOreInputs = oreShapeless.getMethod("getInput");
        if (Modifier.isStatic(getOreInputs.getModifiers())
                || getOreInputs.getParameterTypes().length != 0) {
            throw new IllegalStateException(oreShapeless.getName()
                    + ".getInput contract drifted");
        }
        List<Object> rawRows = new ArrayList<Object>();
        List<Object> eligibleRows = new ArrayList<Object>();
        int vanillaAccepted = 0;
        int oreAccepted = 0;
        int oreEmptyListRejected = 0;
        for (Object recipe : registry) {
            // Preserve the handler's if/else-if order as well as its instanceof semantics.
            if (vanillaShapeless.isInstance(recipe)) {
                rawRows.add(recipe);
                eligibleRows.add(recipe);
                vanillaAccepted++;
            } else if (oreShapeless.isInstance(recipe)) {
                rawRows.add(recipe);
                Object inputs = invoke(getOreInputs, recipe);
                if (!(inputs instanceof List<?>)) {
                    throw new IllegalStateException(oreShapeless.getName()
                            + ".getInput returned "
                            + (inputs == null ? "null" : inputs.getClass().getName())
                            + " instead of List");
                }
                if (railcraftOreInputsEligible((List<?>) inputs)) {
                    eligibleRows.add(recipe);
                    oreAccepted++;
                } else {
                    oreEmptyListRejected++;
                }
            }
        }
        return new SourceSnapshot(
                registry.size(), rawRows, eligibleRows,
                "instanceof ShapelessRecipes || (instanceof ShapelessOreRecipe "
                        + "&& every List input is non-empty)",
                "raw=" + rawRows.size()
                        + ",vanillaAccepted=" + vanillaAccepted
                        + ",oreAccepted=" + oreAccepted
                        + ",oreEmptyListRejected=" + oreEmptyListRejected
                        + ",eligible=" + eligibleRows.size());
    }

    static boolean railcraftOreInputsEligible(List<?> inputs) {
        if (inputs == null) {
            throw new IllegalArgumentException("Railcraft ore inputs must not be null");
        }
        for (Object input : inputs) {
            // The pinned handler tests java.util.List specifically, not Collection.
            if (input instanceof List<?> && ((List<?>) input).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<Object> requireRows(Object value, String source) {
        if (value == null) {
            throw new IllegalStateException(source + " returned null");
        }
        List<Object> rows = new ArrayList<Object>();
        if (value instanceof Map<?, ?>) {
            rows.addAll(((Map<?, ?>) value).entrySet());
            return rows;
        }
        if (value instanceof Iterable<?>) {
            for (Object row : (Iterable<?>) value) {
                rows.add(row);
            }
            return rows;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                rows.add(Array.get(value, index));
            }
            return rows;
        }
        throw new IllegalStateException(source + " returned unsupported row container "
                + value.getClass().getName());
    }

    private static Object invoke(Method method, Object target, Object... arguments)
            throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause();
            FatalErrors.rethrowIfFatal(cause);
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw wrapper;
        }
    }

    private static Map<String, Spec> indexSpecs(List<Spec> specs) {
        Map<String, Spec> result = new LinkedHashMap<String, Spec>();
        for (Spec spec : specs) {
            Spec previous = result.put(spec.handlerClass, spec);
            if (previous != null) {
                throw new IllegalStateException(
                        "duplicate promoted empty handler " + spec.handlerClass);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<String> promotedEvidenceRows() {
        List<String> rows = new ArrayList<String>(SPECS.size());
        for (Spec spec : SPECS) {
            rows.add(spec.promotedEvidenceRow());
        }
        Collections.sort(rows);
        return Collections.unmodifiableList(rows);
    }

    private static List<String> validateSpecLedger(List<Spec> specs) {
        List<String> issues = new ArrayList<String>();
        if (specs.size() != EXPECTED_HANDLER_COUNT) {
            issues.add("expected " + EXPECTED_HANDLER_COUNT + " exact source-backed handlers; "
                    + "ledger contains " + specs.size());
        }
        Set<String> classes = new TreeSet<String>();
        Set<String> categories = new TreeSet<String>();
        String previousClass = null;
        for (Spec spec : specs) {
            if (spec == null) {
                issues.add("null specification in ledger");
                continue;
            }
            if (!classes.add(spec.handlerClass)) {
                issues.add("duplicate handler class in ledger: " + spec.handlerClass);
            }
            if (!categories.add(spec.categoryId)) {
                issues.add("duplicate category ID in ledger: " + spec.categoryId);
            }
            if (previousClass != null && previousClass.compareTo(spec.handlerClass) >= 0) {
                issues.add("ledger is not strictly class-sorted at " + spec.handlerClass);
            }
            if (spec.promotion == null) {
                issues.add("missing promotion evidence for " + spec.handlerClass);
            }
            previousClass = spec.handlerClass;
        }
        Collections.sort(issues);
        return issues;
    }

    static String fingerprintObjects(
            String domain, List<Object> values, SourceKind sourceKind) throws ExportFailure {
        List<String> rows = new ArrayList<String>(values.size());
        for (int index = 0; index < values.size(); index++) {
            try {
                Object value = values.get(index);
                rows.add(sourceKind == SourceKind.FORESTRY_BUTTERFLY_MUTATIONS
                        ? canonicalMutation(value)
                        : sourceKind == SourceKind.RAILCRAFT_ROCK_CRUSHER
                        ? canonicalRockCrusherRecipe(value)
                        : canonicalValue(value));
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "could not canonicalize discovery source row " + index
                                + " in " + domain + ": " + failureDetail(error), error);
            }
        }
        return stableMultisetFingerprint(domain, rows);
    }

    private static String canonicalMutation(Object mutation) throws Exception {
        if (mutation == null) {
            return "mutation:null";
        }
        ClassLoader loader = mutation.getClass().getClassLoader();
        Class<?> type = Class.forName("forestry.api.genetics.IMutation", false, loader);
        if (!type.isInstance(mutation)) {
            throw new IllegalStateException("mutation row no longer implements "
                    + type.getName() + ": " + mutation.getClass().getName());
        }
        StringBuilder row = new StringBuilder(512);
        row.append("mutation{");
        appendNamedMethod(row, "root", type.getMethod("getRoot"), mutation, true);
        appendNamedMethod(row, "allele0", type.getMethod("getAllele0"), mutation, true);
        appendNamedMethod(row, "allele1", type.getMethod("getAllele1"), mutation, true);
        appendNamedMethod(row, "template", type.getMethod("getTemplate"), mutation, true);
        appendNamedMethod(row, "baseChance", type.getMethod("getBaseChance"), mutation, false);
        appendNamedMethod(row, "conditions", type.getMethod("getSpecialConditions"),
                mutation, false);
        appendNamedMethod(row, "secret", type.getMethod("isSecret"), mutation, false);
        return row.append('}').toString();
    }

    private static void appendNamedMethod(
            StringBuilder target, String name, Method method, Object targetObject,
            boolean geneticsIdentity) throws Exception {
        Object value = invoke(method, targetObject);
        String canonical = geneticsIdentity ? canonicalGeneticsIdentity(value)
                : canonicalValue(value);
        appendFrame(target, name);
        appendFrame(target, canonical);
    }

    private static String canonicalGeneticsIdentity(Object value) throws Exception {
        if (value == null) {
            return "null";
        }
        if (value.getClass().isArray()) {
            StringBuilder result = new StringBuilder("alleles[");
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                appendFrame(result, canonicalGeneticsIdentity(Array.get(value, index)));
            }
            return result.append(']').toString();
        }
        ClassLoader loader = value.getClass().getClassLoader();
        Class<?> allele = Class.forName("forestry.api.genetics.IAllele", false, loader);
        if (allele.isInstance(value)) {
            Object uid = invoke(allele.getMethod("getUID"), value);
            return "allele:" + String.valueOf(uid);
        }
        Class<?> root = Class.forName("forestry.api.genetics.ISpeciesRoot", false, loader);
        if (root.isInstance(value)) {
            Object uid = invoke(root.getMethod("getUID"), value);
            return "root:" + String.valueOf(uid);
        }
        throw new IllegalStateException("unsupported Forestry genetics identity "
                + value.getClass().getName());
    }

    private static String canonicalRockCrusherRecipe(Object recipe) throws Exception {
        if (recipe == null) {
            return "rock-crusher:null";
        }
        ClassLoader loader = recipe.getClass().getClassLoader();
        Class<?> type = Class.forName(
                "mods.railcraft.api.crafting.IRockCrusherRecipe", false, loader);
        if (!type.isInstance(recipe)) {
            throw new IllegalStateException("rock-crusher row no longer implements "
                    + type.getName() + ": " + recipe.getClass().getName());
        }
        StringBuilder row = new StringBuilder("rock-crusher{");
        appendFrame(row, canonicalValue(invoke(type.getMethod("getInput"), recipe)));
        appendFrame(row, canonicalValue(invoke(type.getMethod("getOutputs"), recipe)));
        return row.append('}').toString();
    }

    private static String canonicalValue(Object value) throws Exception {
        return canonicalValue(value, new IdentityHashMap<Object, Boolean>(), 0);
    }

    private static String canonicalValue(
            Object value, IdentityHashMap<Object, Boolean> path, int depth) throws Exception {
        if (value == null) {
            return "null";
        }
        if (depth > MAX_CANONICAL_DEPTH) {
            throw new IllegalStateException("canonical source graph exceeds depth "
                    + MAX_CANONICAL_DEPTH + " at " + value.getClass().getName());
        }
        if (value instanceof String || value instanceof Boolean
                || value instanceof Character || value instanceof Number
                || value instanceof Enum<?> || value instanceof UUID) {
            return value.getClass().getName() + ":" + String.valueOf(value);
        }
        if (value instanceof Class<?>) {
            return "class:" + ((Class<?>) value).getName();
        }
        if (value instanceof ItemStack) {
            return "item-stack{" + StackIdentity.describe((ItemStack) value) + "}";
        }
        if (value instanceof PositionedStack) {
            PositionedStack stack = (PositionedStack) value;
            StringBuilder result = new StringBuilder("positioned-stack{");
            appendFrame(result, Integer.toString(stack.relx));
            appendFrame(result, Integer.toString(stack.rely));
            appendFrame(result, canonicalValue(stack.items, path, depth + 1));
            return result.append('}').toString();
        }
        if (value instanceof NBTBase) {
            return "nbt:" + NbtCanonicalizer.canonical((NBTBase) value);
        }
        if (value instanceof Item) {
            Object name = Item.itemRegistry.getNameForObject(value);
            return "item:" + String.valueOf(name) + ":" + value.getClass().getName();
        }
        if (value instanceof Block) {
            Object name = Block.blockRegistry.getNameForObject(value);
            return "block:" + String.valueOf(name) + ":" + value.getClass().getName();
        }
        if (value instanceof Fluid) {
            Fluid fluid = (Fluid) value;
            return "fluid:" + fluid.getName() + ":" + value.getClass().getName();
        }
        if (value instanceof FluidStack) {
            FluidStack fluid = (FluidStack) value;
            return "fluid-stack{" + canonicalValue(fluid.getFluid(), path, depth + 1)
                    + ",amount=" + fluid.amount + ",tag="
                    + canonicalValue(fluid.tag, path, depth + 1) + "}";
        }
        if (path.containsKey(value)) {
            return "cycle:" + value.getClass().getName();
        }
        path.put(value, Boolean.TRUE);
        try {
            Class<?> runtimeClass = value.getClass();
            if (runtimeClass.isArray()) {
                StringBuilder result = new StringBuilder("array:")
                        .append(runtimeClass.getComponentType().getName()).append('[');
                int length = Array.getLength(value);
                for (int index = 0; index < length; index++) {
                    appendFrame(result,
                            canonicalValue(Array.get(value, index), path, depth + 1));
                }
                return result.append(']').toString();
            }
            if (value instanceof Map.Entry<?, ?>) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
                return "entry{" + frame(canonicalValue(entry.getKey(), path, depth + 1))
                        + frame(canonicalValue(entry.getValue(), path, depth + 1)) + "}";
            }
            if (value instanceof Map<?, ?>) {
                List<String> entries = new ArrayList<String>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    entries.add(canonicalValue(entry, path, depth + 1));
                }
                Collections.sort(entries);
                return framedRows("map:" + runtimeClass.getName(), entries);
            }
            if (value instanceof Collection<?>) {
                List<String> elements = new ArrayList<String>();
                for (Object element : (Collection<?>) value) {
                    elements.add(canonicalValue(element, path, depth + 1));
                }
                if (!(value instanceof List<?>)) {
                    Collections.sort(elements);
                }
                return framedRows("collection:" + runtimeClass.getName(), elements);
            }
            if (value instanceof Iterable<?>) {
                List<String> elements = new ArrayList<String>();
                for (Object element : (Iterable<?>) value) {
                    elements.add(canonicalValue(element, path, depth + 1));
                }
                return framedRows("iterable:" + runtimeClass.getName(), elements);
            }

            if (runtimeClass.getName().startsWith("java.")) {
                throw new IllegalStateException("unsupported JDK source value "
                        + runtimeClass.getName());
            }
            List<Field> fields = instanceFields(runtimeClass);
            StringBuilder result = new StringBuilder("object:")
                    .append(runtimeClass.getName()).append('{');
            for (Field field : fields) {
                field.setAccessible(true);
                appendFrame(result, field.getDeclaringClass().getName() + "." + field.getName());
                appendFrame(result,
                        canonicalValue(field.get(value), path, depth + 1));
            }
            return result.append('}').toString();
        } finally {
            path.remove(value);
        }
    }

    private static List<Field> instanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> cursor = type; cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            for (Field field : cursor.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)
                        && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }
        Collections.sort(fields, new Comparator<Field>() {
            @Override
            public int compare(Field left, Field right) {
                String leftName = left.getDeclaringClass().getName() + "." + left.getName();
                String rightName = right.getDeclaringClass().getName() + "." + right.getName();
                return leftName.compareTo(rightName);
            }
        });
        return fields;
    }

    private static String framedRows(String domain, List<String> rows) {
        StringBuilder value = new StringBuilder(domain).append('{');
        for (String row : rows) {
            appendFrame(value, row);
        }
        return value.append('}').toString();
    }

    static String stableMultisetFingerprint(String domain, List<String> rows) {
        List<String> sorted = new ArrayList<String>(rows);
        Collections.sort(sorted);
        StringBuilder canonical = new StringBuilder();
        appendFrame(canonical, domain);
        canonical.append(sorted.size()).append(':');
        for (String row : sorted) {
            appendFrame(canonical, row);
        }
        return Naming.sha256(canonical.toString());
    }

    static List<Spec> specsForTest() {
        return SPECS;
    }

    static List<String> promotedEvidenceRowsForTest() {
        return promotedEvidenceRows();
    }

    static String promotedInventoryFingerprintForTest(List<String> rows) {
        return stableMultisetFingerprint(INVENTORY_FINGERPRINT_DOMAIN, rows);
    }

    static List<String> validateSpecLedgerForTest(List<Spec> specs) {
        return validateSpecLedger(specs);
    }

    private static String failureDetail(Throwable error) {
        StringBuilder detail = new StringBuilder();
        Throwable cursor = error;
        int depth = 0;
        while (cursor != null && depth < 8) {
            if (depth > 0) {
                detail.append(" <- ");
            }
            detail.append(cursor.getClass().getName());
            String message = cursor.getMessage();
            if (message != null && !message.trim().isEmpty()) {
                detail.append(": ").append(singleLine(message));
            }
            cursor = cursor.getCause();
            depth++;
        }
        return detail.toString();
    }

    private static String singleLine(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String quoted(String value) {
        if (value == null) {
            return "<null>";
        }
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + '"';
    }

    private static String frame(String value) {
        return value.length() + ":" + value;
    }

    private static void appendFrame(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private static Map<String, Promotion> createPromotions() {
        Map<String, Promotion> promotions = new LinkedHashMap<String, Promotion>();
        putPromotion(promotions,
                "advsolar.client.nei.MTRecipeHandler",
                simplePromotion(
                        0,
                        "0a17a9b742ffae65a4a23b3b7a8cfe3cfc80b40d1b98e4f323d25cf66f0a9949",
                        "eb75cd1de4163d88119a8153db1e42a18edc6870768cad1f59764a3d5b12b2e9",
                        "077a7c552282e7ccff2b617ddeb60c8b407e974785330914f32f4e457c359d23"));
        putPromotion(promotions,
                "com.creativemd.creativecore.api.nei.NEIRecipeInfoHandler",
                simplePromotion(
                        56609,
                        "afe3a30a8470629ad8a00e98fc63cc3d260e8e591bb0bebfbdb8450b63a8eda2",
                        "a351b37a2a5d630dd1128d3f0049ad148c5d153438c7f8f753869821348ae195",
                        "f8047c1460b71f1eb828336fd5fdaa985fef2671d8d0d20e98f370278efba067"));
        putPromotion(promotions,
                "de.katzenpapst.amunra.nei.recipehandler.ARCircuitFab",
                simplePromotion(
                        0,
                        "a7020504a365e58e4394b4d8158f073ddf1774faf41202c096b2e48e4196e094",
                        "784136a4261a831cd581a89d8bff651e27c5b08fe0aff4da83bf9b51c5251d9a",
                        "82e56d69cda506201d5e221935bc896490578a896aa22ebc416734ba83b29699"));
        putPromotion(promotions,
                "fox.spiteful.avaritia.compat.nei.CompressionHandler",
                simplePromotion(
                        0,
                        "f5f0028369a6f8394357ac48aa4a858238630cb8b5a01b98b20a26a825282338",
                        "fcf6491b49f41d5a8adb8f5c0c2f21f35cd9a3e3709fec8c49a883a4ab0a970a",
                        "2a179a60c892ab8e07831ab3255ab8145c606640165ef846eefb1f852feb3c54"));
        putPromotion(promotions,
                "galaxyspace.core.nei.AssemblyMachineRecipeHandler",
                boundPromotion(
                        0,
                        "b97d34ba721d5970421769e9e4f5153183f3746338dd3439b81fffbd137a0706",
                        "af5b4713659459db982a6c140d3da5adc8fcae5991623f7ef60ddb31d4c7737d",
                        "822ac11d5f6df905a8a69e709dd0e3eb175e1167fdf4642224b81e0509b469ea",
                        "0d68f0888e9178e25f7af99778118db66d457c1df330fd8db3c559739f54e150"));
        putPromotion(promotions,
                "ic2.neiIntegration.core.recipehandler.AdvShapelessRecipeHandler",
                eligiblePromotion(
                        56609,
                        "e527e0bfe50597e6c7b686eaffc26ec2ddf64e789416aaba524ae4d9fe01a39a",
                        "instanceof ic2.core.AdvShapelessRecipe && canShow() "
                                + "&& createCachedRecipe()!=null",
                        "raw=0,canShowAccepted=0,canShowRejected=0,cacheRejected=0,eligible=0",
                        "274c18959e48151d0e339ccc79a7f13a966a7ae84a51f34dd855a45f681f34c1",
                        "58f873ede226dc9e30766ce3ea0e12276bebf6d3abde3fcf75fb3fbe696f4aad",
                        "a55ab5e7d5999f52606ccf581a4b222c159cea6fab732c6ac98cceccbf5270c2"));
        putPromotion(promotions,
                "ic2.neiIntegration.core.recipehandler.BlastFurnaceRecipeHandler",
                simplePromotion(
                        0,
                        "02e33bc57d8c1d79a7c55b64a9fa86d7bf92159e364c8c0e80b40c25d121bc51",
                        "3dc3010bd79b0e6980809b78b46887f789718dfbbcb73217000ced335751d0ee",
                        "335fad8a4f66097be733f24f70d216ea60f83291c99e18b8fbaaa0b6cb66c43c"));
        putPromotion(promotions,
                "ic2.neiIntegration.core.recipehandler.BlockCutterRecipeHandler",
                simplePromotion(
                        0,
                        "f3525266935611f48045c842626c54ecb8a085629b319dc4997477df9107fe61",
                        "7097658382dad7ae259e6262e81bf0b8a777cd98de0938e4a3b0f316ed92bde6",
                        "fbc6acff31e6ea0648b8648c04084e32a073efc8983ca2c10dba2b0c8de86f26"));
        putPromotion(promotions,
                "ic2.neiIntegration.core.recipehandler.CentrifugeRecipeHandler",
                simplePromotion(
                        0,
                        "ed206195eca9e0b24525eb5adb60bc887591d2a1c6e07e7c6d49e2319b0f044a",
                        "12d810485f3217ce45564e850f219f55df7414fde520426d4184d7be8299f7b9",
                        "1f60df0aa3e54989bf9431bd47fce833009177c6217a9c6ad921052c9b0d58b8"));
        putPromotion(promotions,
                "ic2.neiIntegration.core.recipehandler.CompressorRecipeHandler",
                simplePromotion(
                        0,
                        "8b3d208d0d95e269a6b724808feae006b6bddb3ce4dc284792fc064b7c3bb065",
                        "c780a489d031bdef7cc14d34bcf47fad92e80c774e660a8ba28b564fe66f0a0d",
                        "16ad7f4a2e5d7c2b11acaf38de283f825182cecd39ceb7a012275539d73ec31a"));
        putPromotion(promotions,
                "ic2.neiIntegration.core.recipehandler.ExtractorRecipeHandler",
                simplePromotion(
                        0,
                        "0daa88e92289efe26e763e9f61086efa2b280e18ac552c721eea2b76023167c9",
                        "7681c7547d6719a65396ffe6041564d463dcba96a9daddad483d45699e8310a5",
                        "47e5b9434826d7d17281f7da90944ad0c517077b171f2dee2ec1ebe1d09ba67b"));
        putPromotion(promotions,
                "ic2.neiIntegration.core.recipehandler.MaceratorRecipeHandler",
                simplePromotion(
                        0,
                        "820d6b53a21431e95f703cd9680b4376f78bd3e4f4b688dd0cd92a1024ce2d69",
                        "910eb938ea64119b3c979651c691014e3dfeddb5d0855cf1ae810486337aefdd",
                        "1a5a9a41817ab0bf9af87ec99aeeedf2d2746e5fb1ce92f21d614dc7136defa4"));
        putPromotion(promotions,
                "ic2.neiIntegration.core.recipehandler.MetalFormerRecipeHandlerExtruding",
                simplePromotion(
                        0,
                        "4846d688cb1b937ad3896615b4e19b90e44d6a8b1200148b820966c2a34720c2",
                        "4d8b0baf335077e78e71fb11456294387cf093883c71207608b33be345d7f2f5",
                        "4e8b1d86e5e9b725e0058ec71d15cbdf6071cb01aa7e7e2898b2cec1b923f7d4"));
        putPromotion(promotions,
                "ic2.neiIntegration.core.recipehandler.MetalFormerRecipeHandlerRolling",
                simplePromotion(
                        0,
                        "0ad52ea5473e5efde55822e6ce69fc0e56f3360d820a044a3462953bc7daf77c",
                        "0de4a4783f8aaeae908d945a3d43d8ba74460c7d0c6e47284211344e38761adf",
                        "c49a14ba29b694aec989ad9578b6113d3017acf108e34b1933e4e0335da19d3c"));
        putPromotion(promotions,
                "ic2.neiIntegration.core.recipehandler.OreWashingRecipeHandler",
                simplePromotion(
                        0,
                        "2cd7aad2de281d541f95d203814a6ead875bf461b35324552ea138bcfb83ad62",
                        "cff9cb1a9c5d991be84c1dbe0e07c385f861057eb0667ac16298c191a2d9a350",
                        "17b4cdaf34f753ed18c0b2d6e7436680c41be8d7e7bd0f525388a025dab68953"));
        putPromotion(promotions,
                "logisticspipes.nei.NEISolderingStationRecipeManager",
                simplePromotion(
                        0,
                        "bf53c763c6a65804ad980122858008e700f6e4c85ac4d6c1f9bf552e82f6b533",
                        "61e4df2c93bfa199a48ff0f059c902ebb3e02701b187c1bf676640b69d39abbd",
                        "4bb768de1a7b83c45e15e171ca7e216cb16cf3e2a895c306040891669b8ae59a"));
        putPromotion(promotions,
                "net.bdew.neiaddons.forestry.butterflies.ButterflyBreedingHandler",
                simplePromotion(
                        0,
                        "dcd60bbd5dd774da3904a01488a54a40524f9c3a3530006182830534e0ce10b7",
                        "303912c158b5f14e396654d54d99240a68e577e024159e525680efa39dd72763",
                        "84480f15265d286b1cbd4444ed17daadbd163331ffdd018a9f6a552ff51029cb"));
        putPromotion(promotions,
                "net.p455w0rd.wirelesscraftingterminal.integration.modules.NEIHelpers."
                        + "NEIAEShapedRecipeHandler",
                eligiblePromotion(
                        56609,
                        "913c70fdf81c83feee83f722cf4a10bb318d46f37fb9152b62ffc179273a7be1",
                        "instanceof WCT ShapedRecipe && isEnabled()",
                        "raw=0,isEnabledAccepted=0,isEnabledRejected=0,eligible=0",
                        "96870a5a351d234f3c02896f7947ebe7fac7bf373e3b2cd5c1be1c95ff0f0f8f",
                        "3fd3896b661842acafaf0e550ede6862590a8d9933f4764ec61b7bca5b6884c5",
                        "8053acc2a547c330dc90e6d6e0137bc1fcb31c2e3aecb3fd14317998f13f587c"));
        putPromotion(promotions,
                "tonius.neiintegration.mods.railcraft.RecipeHandlerRockCrusher",
                simplePromotion(
                        0,
                        "9c983a6d625126cf49f479b78a207de62d7cc95555cb84f4beadaa4253b0046b",
                        "122993bfb617d8a4911bec159d89b84e1c43d34a359990683d0e3d71bbe843be",
                        "bee2fe498e1d4314d9ea87e3ef8c825b173d487b6dccaa56077f02505524a6d5"));
        putPromotion(promotions,
                "tonius.neiintegration.mods.railcraft.RecipeHandlerRollingMachineShapeless",
                eligiblePromotion(
                        19,
                        "b8035878f4d6cd271c60ca21fd84d708c93f46542625dafad8d6b4a01ef61cdf",
                        "instanceof ShapelessRecipes || (instanceof ShapelessOreRecipe "
                                + "&& every List input is non-empty)",
                        "raw=0,vanillaAccepted=0,oreAccepted=0,"
                                + "oreEmptyListRejected=0,eligible=0",
                        "ef5196bb191180588fd845f1e1b3363eed05e8371d09da7750db1daa4e5c5ab9",
                        "c6d342ef0bd6ef2a5a80129f7fb0ccf4fd9bc8d6d86ad6e697ec465b306eac74",
                        "6516c73f6a3772f378994b29f670e1da2ddd28b4896d6c84761249cdbd6e1d49"));
        if (promotions.size() != EXPECTED_HANDLER_COUNT) {
            throw new IllegalStateException("expected " + EXPECTED_HANDLER_COUNT
                    + " immutable promotions, got " + promotions.size());
        }
        return Collections.unmodifiableMap(promotions);
    }

    private static Promotion simplePromotion(
            int sourceRegistryCount, String sourceFingerprint,
            String prototypeFingerprint, String eligibleFingerprint) {
        return new Promotion(
                sourceRegistryCount, 0, sourceFingerprint,
                "<not-audited>", "<not-audited>",
                -1, "<not-audited>", -1, "<not-applicable>",
                0, prototypeFingerprint, 0, eligibleFingerprint);
    }

    private static Promotion boundPromotion(
            int sourceRegistryCount, String sourceFingerprint,
            String boundPrototypeSourceFingerprint,
            String prototypeFingerprint, String eligibleFingerprint) {
        return new Promotion(
                sourceRegistryCount, 0, sourceFingerprint,
                "<not-audited>", "<not-audited>",
                -1, "<not-audited>", 0, boundPrototypeSourceFingerprint,
                0, prototypeFingerprint, 0, eligibleFingerprint);
    }

    private static Promotion eligiblePromotion(
            int sourceRegistryCount, String sourceFingerprint,
            String sourceEligibilityContract, String sourceEligibilityTelemetry,
            String eligibleSourceFingerprint,
            String prototypeFingerprint, String eligibleFingerprint) {
        return new Promotion(
                sourceRegistryCount, 0, sourceFingerprint,
                sourceEligibilityContract, sourceEligibilityTelemetry,
                0, eligibleSourceFingerprint, -1, "<not-applicable>",
                0, prototypeFingerprint, 0, eligibleFingerprint);
    }

    private static void putPromotion(
            Map<String, Promotion> promotions, String handlerClass,
            Promotion promotion) {
        if (promotions.put(handlerClass, promotion) != null) {
            throw new IllegalStateException(
                    "duplicate immutable promotion for " + handlerClass);
        }
    }

    private static List<Spec> createSpecs() {
        List<Spec> specs = new ArrayList<Spec>();
        specs.add(spec(
                "advsolar.client.nei.MTRecipeHandler",
                "gtnh:b341f365ec598b1de1b29d7cb220e127",
                "Molecular Transformer", SourceKind.ADV_SOLAR_TRANSFORMER,
                "advsolar.utils.MTRecipeManager.transformerRecipes"));
        specs.add(spec(
                "com.creativemd.creativecore.api.nei.NEIRecipeInfoHandler",
                "gtnh:efee6636690a91bd6872061e56f8b9a1",
                "crafting", SourceKind.CREATIVECORE_RECIPE_INFO,
                "CraftingManager[IRecipeInfo]"));
        specs.add(spec(
                "de.katzenpapst.amunra.nei.recipehandler.ARCircuitFab",
                "gtnh:bf00eca074beaf59e38398c095134942",
                "galacticraft.circuits", SourceKind.AMUN_RA_CIRCUIT_FABRICATOR,
                "NEIAmunRaConfig.getCircuitFabricatorRecipes()"));
        specs.add(spec(
                "fox.spiteful.avaritia.compat.nei.CompressionHandler",
                "gtnh:50bed5582200627d3c47cc9f2ecbb311",
                "extreme_compression", SourceKind.AVARITIA_COMPRESSION,
                "CompressorManager.getRecipes()"));
        specs.add(spec(
                "galaxyspace.core.nei.AssemblyMachineRecipeHandler",
                "gtnh:4895de922a294ac794e2c3bc98d0961d",
                "galaxyspace.assemblymachine", SourceKind.GALAXYSPACE_ASSEMBLY,
                "AssemblyRecipes.getRecipeList()+prototype.recipes"));
        specs.add(spec(
                "ic2.neiIntegration.core.recipehandler.AdvShapelessRecipeHandler",
                "gtnh:18a3e33fafe4bbf5e9e89c826370d0b5",
                "crafting", SourceKind.IC2_ADV_SHAPELESS,
                "CraftingManager[instanceof IC2 AdvShapelessRecipe; canShow; cacheable]"));
        specs.add(spec(
                "ic2.neiIntegration.core.recipehandler.BlastFurnaceRecipeHandler",
                "gtnh:8e4b18ec28873142ed3648785ba5137b",
                "ic2.blockBlastFurnace", SourceKind.IC2_MACHINE_MAP,
                "prototype.getRecipeList()"));
        specs.add(spec(
                "ic2.neiIntegration.core.recipehandler.BlockCutterRecipeHandler",
                "gtnh:27ee7caaac8900d9e2af2432f0d6493e",
                "ic2.blockBlockCutter", SourceKind.IC2_MACHINE_MAP,
                "prototype.getRecipeList()"));
        specs.add(spec(
                "ic2.neiIntegration.core.recipehandler.CentrifugeRecipeHandler",
                "gtnh:fe3f1695a1fdb622df6bb5be69c6147f",
                "ic2.blockCentrifuge", SourceKind.IC2_MACHINE_MAP,
                "prototype.getRecipeList()"));
        specs.add(spec(
                "ic2.neiIntegration.core.recipehandler.CompressorRecipeHandler",
                "gtnh:caded561c42e8c86671f791fe4d1d1d9",
                "ic2.compressor", SourceKind.IC2_MACHINE_MAP,
                "prototype.getRecipeList()"));
        specs.add(spec(
                "ic2.neiIntegration.core.recipehandler.ExtractorRecipeHandler",
                "gtnh:93f7d8c81ca5c8713ea5a6228491372c",
                "ic2.extractor", SourceKind.IC2_MACHINE_MAP,
                "prototype.getRecipeList()"));
        specs.add(spec(
                "ic2.neiIntegration.core.recipehandler.MaceratorRecipeHandler",
                "gtnh:ed887b00833278a76ffe6de88d2d8f2c",
                "ic2.macerator", SourceKind.IC2_MACHINE_MAP,
                "prototype.getRecipeList()"));
        specs.add(spec(
                "ic2.neiIntegration.core.recipehandler.MetalFormerRecipeHandlerExtruding",
                "gtnh:72ce2165e7556b701bf8653f61e03566",
                "ic2.MetalFormer", SourceKind.IC2_MACHINE_MAP,
                "prototype.getRecipeList()"));
        specs.add(spec(
                "ic2.neiIntegration.core.recipehandler.MetalFormerRecipeHandlerRolling",
                "gtnh:5a03860bc5d9ad54422817502e026976",
                "ic2.MetalFormer", SourceKind.IC2_MACHINE_MAP,
                "prototype.getRecipeList()"));
        specs.add(spec(
                "ic2.neiIntegration.core.recipehandler.OreWashingRecipeHandler",
                "gtnh:ae358c3bf9b1e4d49c4f86bcfcd07e83",
                "ic2.blockOreWashingPlant", SourceKind.IC2_MACHINE_MAP,
                "prototype.getRecipeList()"));
        specs.add(spec(
                "logisticspipes.nei.NEISolderingStationRecipeManager",
                "gtnh:c24b6bae304e56e161dd197a989721bc",
                "solderingstation", SourceKind.LOGISTICS_PIPES_SOLDERING,
                "SolderingStationRecipes.getRecipes()"));
        specs.add(spec(
                "net.bdew.neiaddons.forestry.butterflies.ButterflyBreedingHandler",
                "gtnh:ba80b526b4e00764afa8417f9b7e9f83",
                "butterflybreeding", SourceKind.FORESTRY_BUTTERFLY_MUTATIONS,
                "speciesRoot.getMutations(false)"));
        specs.add(spec(
                "net.p455w0rd.wirelesscraftingterminal.integration.modules.NEIHelpers."
                        + "NEIAEShapedRecipeHandler",
                "gtnh:0fac080545d4857e9831dacf921ffb38",
                "crafting", SourceKind.WCT_AE_SHAPED,
                "CraftingManager[instanceof WCT ShapedRecipe; isEnabled]"));
        specs.add(spec(
                "tonius.neiintegration.mods.railcraft.RecipeHandlerRockCrusher",
                "gtnh:f234e57e943d9b9e7351bb438a080db5",
                "railcraft.rockcrusher", SourceKind.RAILCRAFT_ROCK_CRUSHER,
                "RailcraftCraftingManager.rockCrusher.getRecipes()"));
        specs.add(spec(
                "tonius.neiintegration.mods.railcraft.RecipeHandlerRollingMachineShapeless",
                "gtnh:c470269e68872e2425dff03d794a9dfc",
                "railcraft.rollingmachine", SourceKind.RAILCRAFT_ROLLING_SHAPELESS,
                "RailcraftCraftingManager.rollingMachine.getRecipeList()"
                        + "[instanceof shapeless; ore lists nonempty]"));
        return Collections.unmodifiableList(specs);
    }

    private static Spec spec(
            String handlerClass, String categoryId, String operation,
            SourceKind sourceKind, String sourceContract) {
        Promotion promotion = PROMOTIONS_BY_CLASS.get(handlerClass);
        if (promotion == null) {
            throw new IllegalStateException(
                    "missing immutable promotion evidence for " + handlerClass);
        }
        return new Spec(handlerClass, categoryId, operation, sourceKind, sourceContract,
                promotion);
    }
}
