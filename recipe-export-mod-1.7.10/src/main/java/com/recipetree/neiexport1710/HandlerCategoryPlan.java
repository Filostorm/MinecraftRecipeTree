package com.recipetree.neiexport1710;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.recipe.RecipeMap;
import gregtech.nei.GTNEIDefaultHandler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Converts registered NEI prototypes into explicit complete-category operations. */
final class HandlerCategoryPlan {
    private static final String CATEGORY_KEY_VERSION = "gtnh-category-key-v1";
    private static final String GENERIC_ADAPTER_CONTRACT =
            "generic:getRecipeHandler-zero-arguments-v1";
    private static final Set<String> PINNED_EMPTY_GREGTECH_CATEGORY_IDENTITIES;

    static {
        Set<String> identities = new TreeSet<String>();
        identities.add(emptyCategoryIdentity(
                "gt.recipe.entropic-processing",
                "gtnh:f3a25a72c53a1f1c494b208f5e99ffd0"));
        identities.add(emptyCategoryIdentity(
                "gt.recipe.spaceResearch",
                "gtnh:bbc9b803242009c80d39be2aaad5786d"));
        PINNED_EMPTY_GREGTECH_CATEGORY_IDENTITIES =
                Collections.unmodifiableSet(identities);
    }

    static final String OPERATION_SOURCE_TRANSFER_RECT = "transfer-rect";
    static final String OPERATION_SOURCE_OVERLAY = "overlay-identifier";
    static final String OPERATION_SOURCE_SELECTOR = "transfer-selector";
    static final String OPERATION_SOURCE_OVERLAY_AND_SELECTOR =
            "overlay-and-transfer-selector";
    static final String OPERATION_SOURCE_ADAPTER = "exact-adapter";
    private static final String OPERATION_SOURCE_EXCLUDED = "excluded-policy";

    static final class PlanningResult {
        final int observedHandlers;
        final List<HandlerCategoryPlan> categories;
        final List<CompleteCategoryAdapters.Policy> policies;
        final int excludedEmptyRecipeHandlers;
        final int excludedUnboundTemplateRecipeHandlers;

        PlanningResult(int observedHandlers, List<HandlerCategoryPlan> categories,
                       List<CompleteCategoryAdapters.Policy> policies,
                       int excludedEmptyRecipeHandlers,
                       int excludedUnboundTemplateRecipeHandlers) {
            this.observedHandlers = observedHandlers;
            this.categories = categories;
            this.policies = policies;
            this.excludedEmptyRecipeHandlers = excludedEmptyRecipeHandlers;
            this.excludedUnboundTemplateRecipeHandlers =
                    excludedUnboundTemplateRecipeHandlers;
        }

        int adaptedCategories() {
            int count = 0;
            for (HandlerCategoryPlan category : categories) {
                if (category.adapter != CompleteCategoryAdapters.Adapter.STANDARD) {
                    count++;
                }
            }
            return count;
        }

        int excludedNonRecipeHandlers() {
            return observedHandlers - categories.size() - excludedEmptyRecipeHandlers
                    - excludedUnboundTemplateRecipeHandlers;
        }

        int excludedUnboundTemplateRecipeHandlers() {
            return excludedUnboundTemplateRecipeHandlers;
        }
    }

    /** One exact public transfer-button operation exposed by NEI. */
    static final class TransferOperation {
        final String outputId;
        final int resultArity;

        TransferOperation(String outputId, int resultArity) {
            this.outputId = outputId;
            this.resultArity = resultArity;
        }

        String matrixValue() {
            return "{outputId=" + escaped(outputId) + ",arity=" + resultArity + "}";
        }
    }

    /** The exact zero-argument operation selected for a generic handler. */
    static final class ResolvedOperation {
        final String outputId;
        final String source;

        ResolvedOperation(String outputId, String source) {
            this.outputId = outputId;
            this.source = source;
        }
    }

    private static final class BaseInspection {
        final ICraftingHandler prototype;
        final String runtimeClass;
        final String handlerId;
        final String overlay;
        final String selector;
        final List<TransferOperation> transferOperations;
        final String classifierTransferIdentifier;

        BaseInspection(ICraftingHandler prototype, String runtimeClass, String handlerId,
                       String overlay, String selector,
                       List<TransferOperation> transferOperations,
                       String classifierTransferIdentifier) {
            this.prototype = prototype;
            this.runtimeClass = runtimeClass;
            this.handlerId = handlerId;
            this.overlay = overlay;
            this.selector = selector;
            this.transferOperations = transferOperations;
            this.classifierTransferIdentifier = classifierTransferIdentifier;
        }
    }

    final ICraftingHandler prototype;
    /** Raw NEI lineage identifier. It is deliberately not assumed to be category-unique. */
    final String handlerId;
    /** UI/category discriminator returned by the registered prototype. */
    final String overlayIdentifier;
    /** TemplateRecipeHandler's optional public selector, independent of overlay identity. */
    final String transferIdentifier;
    /** Generic outputId, or the exact adapter contract for adapter-backed categories. */
    final String loadIdentifier;
    final CompleteCategoryAdapters.Adapter adapter;
    final boolean allowsInformationalEmptyOutputs;
    /** Stable public category identifier used by the exported corpus. */
    final String categoryId;
    /** Length-framed, unhashed semantic identity from which {@link #categoryId} is derived. */
    final String categoryKey;
    /** Auditable source of the selected complete-category operation. */
    final String operationSource;
    final String operationMarker;
    final String adapterContract;

    private HandlerCategoryPlan(ICraftingHandler prototype, String handlerId,
                                String overlayIdentifier, String transferIdentifier,
                                String loadIdentifier, CompleteCategoryAdapters.Adapter adapter,
                                boolean allowsInformationalEmptyOutputs,
                                String operationSource, String operationMarker,
                                String adapterContract) {
        this.prototype = prototype;
        this.handlerId = handlerId;
        this.overlayIdentifier = overlayIdentifier;
        this.transferIdentifier = transferIdentifier;
        this.loadIdentifier = loadIdentifier;
        this.adapter = adapter;
        this.allowsInformationalEmptyOutputs = allowsInformationalEmptyOutputs;
        this.operationSource = operationSource;
        this.operationMarker = operationMarker;
        this.adapterContract = adapterContract;
        this.categoryKey = buildCategoryKey(
                prototype.getClass().getName(), handlerId, overlayIdentifier,
                operationMarker, adapterContract);
        this.categoryId = "gtnh:" + Naming.sha256(categoryKey).substring(0, 32);
    }

    /** Test/general path: does not require GTNH-specific handlers to be present. */
    static List<HandlerCategoryPlan> create(List<ICraftingHandler> prototypes)
            throws ExportFailure {
        return createInternal(prototypes, false).categories;
    }

    /** Release path: all exact GTNH 2.8.4 special handlers must be observed and classified. */
    static PlanningResult createPinnedGtnh(List<ICraftingHandler> prototypes)
            throws ExportFailure {
        return createInternal(prototypes, true);
    }

    /**
     * Cheap fail-closed registry audit used before expensive semantic adapter discovery.
     * Every structurally unsupported handler is reported in one deterministic failure, and
     * every registry entry is logged as an auditable operation-selection matrix row.
     */
    static void validatePinnedStructuralContracts(List<ICraftingHandler> prototypes)
            throws ExportFailure {
        validateStructuralContracts(prototypes, true);
    }

    static void validateStructuralContracts(
            List<ICraftingHandler> prototypes, boolean requirePinnedPolicies)
            throws ExportFailure {
        if (prototypes == null || prototypes.isEmpty()) {
            throw new ExportFailure("HANDLER_UNLOADED", "NEI registered no crafting handlers");
        }
        List<String> issues = new ArrayList<String>();
        List<String> matrixRows = new ArrayList<String>(prototypes.size());
        Set<String> classifiedSpecialPolicyIds = new HashSet<String>();
        Map<String, String> categoryIdsByKey = new HashMap<String, String>();
        Map<String, String> categoryKeysById = new HashMap<String, String>();

        for (int index = 0; index < prototypes.size(); index++) {
            ICraftingHandler prototype = prototypes.get(index);
            BaseInspection inspected = null;
            HandlerCategoryPlan plan = null;
            CompleteCategoryAdapters.Policy policy = null;
            String className = prototype == null ? "<null>" : prototype.getClass().getName();
            try {
                if (prototype == null) {
                    throw new ExportFailure(
                            "HANDLER_UNLOADED", "registered handler is null");
                }
                inspected = inspect(prototype);
                policy = CompleteCategoryAdapters.classify(
                        inspected.runtimeClass, inspected.handlerId, inspected.overlay,
                        inspected.selector, inspected.classifierTransferIdentifier);
                if (policy != null) {
                    CompleteCategoryAdapters.validateStructuralPolicyTransferOperations(
                            policy, inspected.transferOperations);
                    CompleteCategoryAdapters.validateStructuralPolicyPrototype(
                            policy, prototype);
                    if (!classifiedSpecialPolicyIds.add(policy.handlerId)) {
                        throw new ExportFailure("HANDLER_DUPLICATE",
                                "special GTNH policy handler ID occurs more than once: "
                                        + policy.handlerId);
                    }
                    if (!CompleteCategoryAdapters.isExcludedFromCategoryExport(policy.adapter)) {
                        plan = adapterPlan(inspected, policy);
                        registerCategoryIdentity(
                                plan, categoryIdsByKey, categoryKeysById);
                    }
                } else {
                    plan = genericPlan(inspected);
                    registerCategoryIdentity(plan, categoryIdsByKey, categoryKeysById);
                }
                matrixRows.add(matrixRow(index, inspected, plan, policy, null));
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                GtnhNeiExportMod.LOGGER.error(
                        "[gtnh-nei-export] Structural policy validation failed at handler "
                                + "index=" + index + ", class=" + className,
                        error);
                String message = error.getMessage();
                issues.add("index=" + index + " class=" + className + ": "
                        + (message == null ? String.valueOf(error) : message));
                matrixRows.add(matrixRow(index, inspected, plan, policy, error));
            }
        }
        if (requirePinnedPolicies) {
            try {
                CompleteCategoryAdapters.requireAllPinnedPolicies(classifiedSpecialPolicyIds);
            } catch (ExportFailure failure) {
                issues.add("pinned-policy-set: " + failure.getMessage());
            }
        }

        logStructuralMatrix(matrixRows);
        if (!issues.isEmpty()) {
            Collections.sort(issues);
            StringBuilder message = new StringBuilder(4096);
            message.append("registered crafting-handler structural preflight found ")
                    .append(issues.size()).append(" issue(s); export was not started");
            for (String issue : issues) {
                message.append("\n- ").append(issue);
            }
            throw new ExportFailure("HANDLER_UNLOADED", message.toString());
        }
    }

    private static PlanningResult createInternal(List<ICraftingHandler> prototypes,
                                                 boolean requirePinnedPolicies)
            throws ExportFailure {
        if (prototypes == null || prototypes.isEmpty()) {
            throw new ExportFailure("HANDLER_UNLOADED", "NEI registered no crafting handlers");
        }
        if (requirePinnedPolicies) {
            validatePinnedStructuralContracts(prototypes);
        }
        List<HandlerCategoryPlan> plans = new ArrayList<HandlerCategoryPlan>(prototypes.size());
        List<CompleteCategoryAdapters.Policy> policies =
                new ArrayList<CompleteCategoryAdapters.Policy>();
        Set<String> classifiedSpecialPolicyIds = new HashSet<String>();
        Map<String, String> categoryIdsByKey = new HashMap<String, String>();
        Map<String, String> categoryKeysById = new HashMap<String, String>();
        int excludedEmptyRecipeHandlers = 0;
        int excludedUnboundTemplateRecipeHandlers = 0;
        Set<String> observedEmptyGregTechCategoryIdentities = new TreeSet<String>();
        Set<String> observedPromotedEmptyHandlerClasses = new TreeSet<String>();
        List<String> observedPromotedEmptyRows = new ArrayList<String>();

        for (int index = 0; index < prototypes.size(); index++) {
            ICraftingHandler prototype = prototypes.get(index);
            if (prototype == null) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "registered crafting handler #" + index + " is null");
            }
            final BaseInspection inspected;
            try {
                inspected = inspect(prototype);
            } catch (ExportFailure failure) {
                throw failure;
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "could not inspect public category operations for "
                                + prototype.getClass().getName(), error);
            }

            CompleteCategoryAdapters.Policy policy = CompleteCategoryAdapters.classify(
                    inspected.runtimeClass, inspected.handlerId, inspected.overlay,
                    inspected.selector, inspected.classifierTransferIdentifier);
            final HandlerCategoryPlan plan;
            if (policy != null) {
                CompleteCategoryAdapters.validateStructuralPolicyTransferOperations(
                        policy, inspected.transferOperations);
                CompleteCategoryAdapters.validateStructuralPolicyPrototype(
                        policy, prototype);
                if (!classifiedSpecialPolicyIds.add(policy.handlerId)) {
                    throw new ExportFailure("HANDLER_DUPLICATE",
                            "special GTNH policy handler ID occurs more than once: "
                                    + policy.handlerId);
                }
                policies.add(policy);
                GtnhNeiExportMod.LOGGER.info(
                        "[gtnh-nei-export] Classified special handler {} id={} as {} ({})",
                        policy.handlerClass, policy.handlerId,
                        policy.action, policy.contract);
                if (CompleteCategoryAdapters.isExcludedFromCategoryExport(policy.adapter)) {
                    if (policy.adapter
                            == CompleteCategoryAdapters.Adapter.EXCLUDED_UNBOUND_TEMPLATE) {
                        excludedUnboundTemplateRecipeHandlers++;
                    }
                    continue;
                }
                plan = adapterPlan(inspected, policy);
            } else {
                plan = genericPlan(inspected);
            }
            registerCategoryIdentity(plan, categoryIdsByKey, categoryKeysById);
            String promotedEmptyRow = requirePinnedPolicies
                    ? PinnedEmptyRecipeHandlers.validatePromotedPlan(plan) : null;
            if (promotedEmptyRow != null) {
                String promotedClass = plan.prototype.getClass().getName();
                if (!observedPromotedEmptyHandlerClasses.add(promotedClass)) {
                    throw new ExportFailure("HANDLER_DUPLICATE",
                            "promoted empty handler occurs more than once: "
                                    + promotedClass);
                }
                observedPromotedEmptyRows.add(promotedEmptyRow);
                excludedEmptyRecipeHandlers++;
                continue;
            }
            String emptyGregTechIdentity = requirePinnedPolicies
                    ? inspectExactEmptyGregTechCategory(plan) : null;
            if (emptyGregTechIdentity != null) {
                if (!observedEmptyGregTechCategoryIdentities.add(emptyGregTechIdentity)) {
                    throw new ExportFailure("HANDLER_DUPLICATE",
                            "exact empty GregTech category identity occurs more than once: "
                                    + emptyGregTechIdentity);
                }
                excludedEmptyRecipeHandlers++;
                continue;
            }
            plans.add(plan);
        }

        if (requirePinnedPolicies) {
            CompleteCategoryAdapters.requireAllPinnedPolicies(classifiedSpecialPolicyIds);
            PinnedEmptyRecipeHandlers.requirePromotedInventory(
                    observedPromotedEmptyHandlerClasses, observedPromotedEmptyRows);
            if (!observedEmptyGregTechCategoryIdentities.equals(
                    PINNED_EMPTY_GREGTECH_CATEGORY_IDENTITIES)) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "exact empty GregTech category inventory drifted; expected="
                                + PINNED_EMPTY_GREGTECH_CATEGORY_IDENTITIES
                                + ", observed="
                                + observedEmptyGregTechCategoryIdentities);
            }
        }
        Collections.sort(plans, new Comparator<HandlerCategoryPlan>() {
            @Override
            public int compare(HandlerCategoryPlan left, HandlerCategoryPlan right) {
                return left.categoryId.compareTo(right.categoryId);
            }
        });
        Collections.sort(policies, new Comparator<CompleteCategoryAdapters.Policy>() {
            @Override
            public int compare(CompleteCategoryAdapters.Policy left,
                               CompleteCategoryAdapters.Policy right) {
                int byClass = left.handlerClass.compareTo(right.handlerClass);
                return byClass != 0 ? byClass : left.handlerId.compareTo(right.handlerId);
            }
        });
        return new PlanningResult(
                prototypes.size(), Collections.unmodifiableList(plans),
                Collections.unmodifiableList(policies), excludedEmptyRecipeHandlers,
                excludedUnboundTemplateRecipeHandlers);
    }

    /** Runs every promoted corpus audit before category metadata or rendering. */
    static void runPinnedPreExportAudits(List<ICraftingHandler> prototypes)
            throws ExportFailure {
        PlanningResult planning = createPinnedGtnh(
                new ArrayList<ICraftingHandler>(prototypes));
        List<ExportFailure> failures = new ArrayList<ExportFailure>();
        for (HandlerCategoryPlan plan : planning.categories) {
            boolean soulSemanticRepair = plan.adapter
                    == CompleteCategoryAdapters.Adapter.EXTRAUTILITIES_SOUL;
            boolean cropPresentationDiscovery =
                    CompleteCategoryAdapters.requiresCropPresentationDiscovery(
                            plan.adapter);
            boolean forestryFluidSemantics = plan.adapter
                    == CompleteCategoryAdapters.Adapter.FORESTRY_FLUID_SEMANTICS;
            boolean gendustryMachineSemantics = plan.adapter
                    == CompleteCategoryAdapters.Adapter.GENDUSTRY_MACHINE_SEMANTICS;
            boolean binnieIncubatorSemantics = plan.adapter
                    == CompleteCategoryAdapters.Adapter.BINNIE_INCUBATOR_SEMANTICS;
            boolean binnieGenepoolSemantics = plan.adapter
                    == CompleteCategoryAdapters.Adapter.BINNIE_GENEPOOL_SEMANTICS;
            boolean mobsInfoSemantics = plan.adapter
                    == CompleteCategoryAdapters.Adapter.MOBSINFO_INFORMATIONAL_SEMANTICS;
            boolean mobsInfoInfernalSemantics = plan.adapter
                    == CompleteCategoryAdapters.Adapter
                    .MOBSINFO_INFERNAL_INFORMATIONAL_SEMANTICS;
            boolean mobsInfoVillagerSemantics = plan.adapter
                    == CompleteCategoryAdapters.Adapter
                    .MOBSINFO_VILLAGER_INFORMATIONAL_SEMANTICS;
            boolean tconstructMeltingSemantics = plan.adapter
                    == CompleteCategoryAdapters.Adapter.TCONSTRUCT_MELTING_FLUID_SEMANTICS;
            boolean tconstructAlloyingSemantics = plan.adapter
                    == CompleteCategoryAdapters.Adapter.TCONSTRUCT_ALLOYING_FLUID_SEMANTICS;
            boolean buildcraftRefinerySemantics = plan.adapter
                    == CompleteCategoryAdapters.Adapter.BUILDCRAFT_REFINERY_FLUID_SEMANTICS;
            boolean enderIoVatSemantics = plan.adapter
                    == CompleteCategoryAdapters.Adapter.ENDERIO_VAT_FLUID_SEMANTICS;
            if (!soulSemanticRepair && !cropPresentationDiscovery
                    && !forestryFluidSemantics && !gendustryMachineSemantics
                    && !binnieIncubatorSemantics && !binnieGenepoolSemantics
                    && !mobsInfoSemantics
                    && !mobsInfoInfernalSemantics
                    && !mobsInfoVillagerSemantics
                    && !tconstructMeltingSemantics && !tconstructAlloyingSemantics
                    && !buildcraftRefinerySemantics && !enderIoVatSemantics) {
                continue;
            }
            try {
                // The two repaired 1.0.52 adapters are loaded before any export context, GL
                // renderer, writer, or PNG worker exists. The promoted crop path skips this
                // duplicate 290k-page allocation; its immutable gate remains enforced when the
                // real category is loaded.
                plan.loadCompleteCategory();
            } catch (ExportFailure failure) {
                failures.add(new ExportFailure(failure.code,
                        "pre-render adapted category failed: handlerId=" + plan.handlerId
                                + " categoryId=" + plan.categoryId + " class="
                                + plan.prototype.getClass().getName() + ": "
                                + failure.getMessage(), failure));
            }
        }
        try {
            GregTechOutputlessSemanticPreflight.Snapshot gregTech =
                    GregTechOutputlessSemanticPreflight.preflight(planning.categories);
            GregTechOutputlessSemanticPreflight.requirePromotedSnapshot(gregTech);
        } catch (ExportFailure failure) {
            failures.add(failure);
        }
        try {
            QueryClosureCategoryAdapters.discoverPinnedInventory(prototypes);
        } catch (ExportFailure failure) {
            failures.add(failure);
        }
        try {
            ForestryFluidSemanticAdapter.requirePromotedCorpus();
        } catch (ExportFailure failure) {
            failures.add(failure);
        }
        try {
            GendustryMachineSemanticAdapter.requirePromotedCorpus();
        } catch (ExportFailure failure) {
            failures.add(failure);
        }
        try {
            BinnieIncubatorSemanticAdapter.requirePromotedCorpus();
        } catch (ExportFailure failure) {
            failures.add(failure);
        }
        try {
            BinnieGenepoolSemanticAdapter.requirePromotedCorpus();
        } catch (ExportFailure failure) {
            failures.add(failure);
        }
        try {
            MobsInfoSemanticAdapter.requirePromotedCorpus();
        } catch (ExportFailure failure) {
            failures.add(failure);
        }
        try {
            MobsInfoInfernalSemanticAdapter.requirePromotedCorpus();
        } catch (ExportFailure failure) {
            failures.add(failure);
        }
        try {
            MobsInfoVillagerTradeSemanticAdapter.requirePromotedCorpus();
        } catch (ExportFailure failure) {
            failures.add(failure);
        }
        try {
            TconstructMeltingSemanticAdapter.requirePromotedCorpus();
        } catch (ExportFailure failure) {
            failures.add(failure);
        }
        try {
            TconstructAlloyingSemanticAdapter.requirePromotedCorpus();
        } catch (ExportFailure failure) {
            failures.add(failure);
        }
        try {
            BuildcraftRefinerySemanticAdapter.requirePromotedCorpus();
        } catch (ExportFailure failure) {
            failures.add(failure);
        }
        try {
            EnderIoVatSemanticAdapter.requirePromotedCorpus();
        } catch (ExportFailure failure) {
            failures.add(failure);
        }
        if (failures.isEmpty()) {
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] All promoted GTNH pre-export audits passed; "
                            + "exportableCategories={}, excludedNonRecipe={}, "
                            + "excludedEmpty={}, excludedUnboundTemplate={}",
                    planning.categories.size(), planning.excludedNonRecipeHandlers(),
                    planning.excludedEmptyRecipeHandlers,
                    planning.excludedUnboundTemplateRecipeHandlers);
            return;
        }
        StringBuilder message = new StringBuilder(8192);
        message.append("GTNH pre-export audits collected ")
                .append(failures.size())
                .append(" independent pre-render promotion failure(s)");
        for (int index = 0; index < failures.size(); index++) {
            ExportFailure failure = failures.get(index);
            message.append("\n--- probe ").append(index + 1).append(" ---\n")
                    .append(failure.getMessage() == null
                            ? failure.getClass().getName() : failure.getMessage());
        }
        ExportFailure aggregate = new ExportFailure(
                "HANDLER_UNLOADED", message.toString(), failures.get(0));
        for (int index = 1; index < failures.size(); index++) {
            aggregate.addSuppressed(failures.get(index));
        }
        throw aggregate;
    }

    private static String inspectExactEmptyGregTechCategory(HandlerCategoryPlan plan)
            throws ExportFailure {
        if (plan.prototype.getClass() != GTNEIDefaultHandler.class) {
            return null;
        }
        GTNEIDefaultHandler prototype = (GTNEIDefaultHandler) plan.prototype;
        RecipeMap<?> map = prototype.getRecipeMap();
        if (map == null || map.getClass() != RecipeMap.class) {
            return null;
        }
        final boolean empty;
        try {
            empty = map.getAllRecipes().isEmpty();
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not inspect exact GregTech RecipeMap contents for "
                            + plan.categoryId, error);
        }
        if (!empty) {
            return null;
        }
        if (map.unlocalizedName == null
                || !map.unlocalizedName.equals(plan.loadIdentifier)
                || !map.unlocalizedName.equals(plan.overlayIdentifier)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "empty GregTech RecipeMap changed its exact map/operation identity: "
                            + "categoryId=" + plan.categoryId + " map="
                            + map.unlocalizedName + " operation=" + plan.loadIdentifier
                            + " overlay=" + plan.overlayIdentifier);
        }
        ICraftingHandler loaded = plan.loadCompleteCategoryAllowEmpty();
        if (loaded.getClass() != GTNEIDefaultHandler.class
                || ((GTNEIDefaultHandler) loaded).getRecipeMap() != map
                || loaded.numRecipes() != 0
                || ((GTNEIDefaultHandler) loaded).arecipes == null
                || !((GTNEIDefaultHandler) loaded).arecipes.isEmpty()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "pinned empty GregTech category did not preserve its exact map/empty-row "
                            + "identity: " + plan.categoryId);
        }
        String identity = emptyCategoryIdentity(map.unlocalizedName, plan.categoryId);
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Observed exact empty registered GregTech recipe category "
                        + "categoryId={} map={} rawMapRecipes=0 loadedRecipes=0",
                plan.categoryId, map.unlocalizedName);
        return identity;
    }

    private static String emptyCategoryIdentity(String mapName, String categoryId) {
        return mapName + "|" + categoryId;
    }

    private static BaseInspection inspect(ICraftingHandler prototype) throws ExportFailure {
        String runtimeClass = prototype.getClass().getName();
        String handlerId = required(
                prototype.getHandlerId(), "handler ID", prototype.getClass());
        String overlay = optional(prototype.getOverlayIdentifier());
        String selector = prototype instanceof TemplateRecipeHandler
                ? optional(((TemplateRecipeHandler) prototype).specifyTransferRect())
                : null;
        List<TransferOperation> transferOperations = prototype instanceof TemplateRecipeHandler
                ? inspectTransferOperations(
                        ((TemplateRecipeHandler) prototype).transferRects, handlerId)
                : Collections.<TransferOperation>emptyList();
        return new BaseInspection(
                prototype, runtimeClass, handlerId, overlay, selector,
                transferOperations,
                uniqueOutputIdIgnoringArity(transferOperations));
    }

    private static HandlerCategoryPlan genericPlan(BaseInspection inspected)
            throws ExportFailure {
        ResolvedOperation operation = selectGenericOperation(
                inspected.handlerId, inspected.overlay, inspected.selector,
                inspected.transferOperations);
        return new HandlerCategoryPlan(
                inspected.prototype, inspected.handlerId, inspected.overlay,
                inspected.selector, operation.outputId,
                CompleteCategoryAdapters.Adapter.STANDARD, false,
                operation.source, "output-id:" + operation.outputId,
                GENERIC_ADAPTER_CONTRACT);
    }

    private static HandlerCategoryPlan adapterPlan(
            BaseInspection inspected, CompleteCategoryAdapters.Policy policy) {
        return new HandlerCategoryPlan(
                inspected.prototype, inspected.handlerId, inspected.overlay,
                inspected.selector, policy.contract, policy.adapter,
                policy.adapter.allowsInformationalEmptyOutputs(),
                OPERATION_SOURCE_ADAPTER, "adapter:" + policy.adapter.name(),
                policy.contract);
    }

    private static void registerCategoryIdentity(
            HandlerCategoryPlan plan, Map<String, String> categoryIdsByKey,
            Map<String, String> categoryKeysById) throws ExportFailure {
        String duplicateId = categoryIdsByKey.get(plan.categoryKey);
        if (duplicateId != null) {
            throw new ExportFailure("HANDLER_DUPLICATE",
                    "semantic category key occurs more than once: categoryId="
                            + duplicateId + ", handlerId=" + plan.handlerId
                            + ", class=" + plan.prototype.getClass().getName());
        }
        String collidingKey = categoryKeysById.get(plan.categoryId);
        if (collidingKey != null && !collidingKey.equals(plan.categoryKey)) {
            throw new ExportFailure("HANDLER_DUPLICATE",
                    "truncated SHA-256 category ID collision for " + plan.categoryId
                            + "; refusing to alias distinct semantic category keys");
        }
        categoryIdsByKey.put(plan.categoryKey, plan.categoryId);
        categoryKeysById.put(plan.categoryId, plan.categoryKey);
    }

    static String resolveLoadIdentifier(String handlerId, String overlay, String selector)
            throws ExportFailure {
        return selectGenericOperation(
                handlerId, optional(overlay), optional(selector),
                Collections.<TransferOperation>emptyList()).outputId;
    }

    /**
     * Compatibility helper for one known zero-argument rectangle. A transfer operation is
     * not required to equal either UI discriminator.
     */
    static String resolveLoadIdentifier(
            String handlerId, String overlay, String selector, String transferRect)
            throws ExportFailure {
        List<TransferOperation> operations = optional(transferRect) == null
                ? Collections.<TransferOperation>emptyList()
                : Collections.singletonList(
                        new TransferOperation(optional(transferRect), 0));
        return selectGenericOperation(
                handlerId, optional(overlay), optional(selector), operations).outputId;
    }

    /** Test-visible entry point that exercises the exact reflected rectangle contract. */
    static ResolvedOperation resolveGenericOperation(
            String handlerId, String overlay, String selector, List<?> transferRects)
            throws ExportFailure {
        return selectGenericOperation(
                handlerId, optional(overlay), optional(selector),
                inspectTransferOperations(transferRects, handlerId));
    }

    private static ResolvedOperation selectGenericOperation(
            String handlerId, String overlay, String selector,
            List<TransferOperation> transferOperations) throws ExportFailure {
        String normalizedHandlerId = optional(handlerId);
        if (normalizedHandlerId == null) {
            normalizedHandlerId = "<blank-handler-id>";
        }
        if (transferOperations.isEmpty()) {
            if (overlay == null && selector == null) {
                throw new ExportFailure("HANDLER_UNLOADED", normalizedHandlerId
                        + " exposes no transfer rectangles, getOverlayIdentifier(), or "
                        + "specifyTransferRect(); there is no public complete-category "
                        + "load contract");
            }
            if (overlay != null && selector != null && !overlay.equals(selector)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", normalizedHandlerId
                        + " has no transfer rectangles and exposes conflicting zero-argument "
                        + "operations: overlay=" + overlay + ", selector=" + selector);
            }
            if (overlay != null && selector != null) {
                return new ResolvedOperation(
                        overlay, OPERATION_SOURCE_OVERLAY_AND_SELECTOR);
            }
            if (overlay != null) {
                return new ResolvedOperation(overlay, OPERATION_SOURCE_OVERLAY);
            }
            return new ResolvedOperation(selector, OPERATION_SOURCE_SELECTOR);
        }

        Set<String> zeroArgumentIds = new TreeSet<String>();
        for (TransferOperation operation : transferOperations) {
            if (operation.resultArity == 0) {
                zeroArgumentIds.add(operation.outputId);
            }
        }
        if (zeroArgumentIds.isEmpty()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", normalizedHandlerId
                    + " exposes only nonzero-argument transfer operations "
                    + transferOperationSummary(transferOperations)
                    + "; a generic category load must be zero-argument, so an exact class "
                    + "adapter is required");
        }
        if (zeroArgumentIds.size() == 1) {
            // Transfer rectangles are callable operations. Overlay/selector strings remain
            // independent UI discriminators and are not required to equal this outputId.
            return new ResolvedOperation(
                    zeroArgumentIds.iterator().next(), OPERATION_SOURCE_TRANSFER_RECT);
        }

        String overlayMatch = overlay != null && zeroArgumentIds.contains(overlay)
                ? overlay : null;
        String selectorMatch = selector != null && zeroArgumentIds.contains(selector)
                ? selector : null;
        if (overlayMatch != null && selectorMatch != null
                && !overlayMatch.equals(selectorMatch)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", normalizedHandlerId
                    + " overlay and specifyTransferRect select different zero-argument "
                    + "transfer operations: overlay=" + overlayMatch
                    + ", selector=" + selectorMatch + ", available=" + zeroArgumentIds);
        }
        if (overlayMatch != null) {
            return new ResolvedOperation(
                    overlayMatch, OPERATION_SOURCE_TRANSFER_RECT);
        }
        if (selectorMatch != null) {
            return new ResolvedOperation(
                    selectorMatch, OPERATION_SOURCE_TRANSFER_RECT);
        }
        throw new ExportFailure("HANDLER_AMBIGUOUS", normalizedHandlerId
                + " exposes multiple zero-argument transfer operations " + zeroArgumentIds
                + " and neither overlay=" + overlay + " nor selector=" + selector
                + " selects exactly one");
    }

    /**
     * GTNH's NEI fork stores transfer-rectangle output IDs and arguments in package-private
     * fields. The NEI artifact is SHA-pinned before this path runs.
     */
    private static List<TransferOperation> inspectTransferOperations(
            List<?> transferRects, String handlerId) throws ExportFailure {
        try {
            if (transferRects == null || transferRects.isEmpty()) {
                return Collections.emptyList();
            }
            Class<?> rectangleClass = Class.forName(
                    "codechicken.nei.recipe.TemplateRecipeHandler$RecipeTransferRect",
                    false, TemplateRecipeHandler.class.getClassLoader());
            Field outputId = rectangleClass.getDeclaredField("outputId");
            Field results = rectangleClass.getDeclaredField("results");
            if (outputId.getType() != String.class) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "NEI RecipeTransferRect.outputId type drifted for " + handlerId);
            }
            if (results.getType() != Object[].class) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "NEI RecipeTransferRect.results type drifted for " + handlerId);
            }
            outputId.setAccessible(true);
            results.setAccessible(true);
            List<TransferOperation> operations =
                    new ArrayList<TransferOperation>(transferRects.size());
            for (int index = 0; index < transferRects.size(); index++) {
                Object rectangle = transferRects.get(index);
                if (rectangle == null || rectangle.getClass() != rectangleClass) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                            + " has an unexpected transfer rectangle at index " + index);
                }
                String identifier = optional((String) outputId.get(rectangle));
                if (identifier == null) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                            + " has a blank transfer-rectangle output ID at index " + index);
                }
                Object[] arguments = (Object[]) results.get(rectangle);
                if (arguments == null) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                            + " transfer rectangle at index " + index
                            + " has null results; an exact class adapter is required");
                }
                operations.add(new TransferOperation(identifier, arguments.length));
            }
            return Collections.unmodifiableList(operations);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                    + " transfer-rectangle contract inspection failed", error);
        }
    }

    static String uniqueTransferRectIdentifier(
            List<?> transferRects, String handlerId) throws ExportFailure {
        return uniqueTransferRectIdentifier(transferRects, handlerId, true);
    }

    /**
     * Compatibility helper used by tests and the pinned policy classifier. Passing false
     * permits observation of a nonzero operation but never authorizes a generic category load.
     */
    static String uniqueTransferRectIdentifier(
            List<?> transferRects, String handlerId,
            boolean requireZeroArguments) throws ExportFailure {
        List<TransferOperation> operations = inspectTransferOperations(transferRects, handlerId);
        if (operations.isEmpty()) {
            return null;
        }
        if (requireZeroArguments) {
            return selectGenericOperation(handlerId, null, null, operations).outputId;
        }
        String identifier = uniqueOutputIdIgnoringArity(operations);
        if (identifier == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                    + " exposes multiple transfer-rectangle output IDs: "
                    + transferOperationSummary(operations));
        }
        return identifier;
    }

    private static String uniqueOutputIdIgnoringArity(List<TransferOperation> operations) {
        Set<String> outputIds = new HashSet<String>();
        for (TransferOperation operation : operations) {
            outputIds.add(operation.outputId);
        }
        return outputIds.size() == 1 ? outputIds.iterator().next() : null;
    }

    private static String transferOperationSummary(List<TransferOperation> operations) {
        List<String> values = new ArrayList<String>(operations.size());
        for (TransferOperation operation : operations) {
            values.add(operation.outputId + "/" + operation.resultArity);
        }
        Collections.sort(values);
        return values.toString();
    }

    static String buildCategoryKey(String runtimeClass, String handlerId, String overlay,
                                   String operationMarker, String adapterContract) {
        StringBuilder key = new StringBuilder(256);
        appendFrame(key, CATEGORY_KEY_VERSION);
        appendFrame(key, runtimeClass);
        appendFrame(key, handlerId);
        appendFrame(key, overlay == null ? "" : overlay);
        appendFrame(key, operationMarker);
        appendFrame(key, adapterContract);
        return key.toString();
    }

    private static void appendFrame(StringBuilder target, String value) {
        if (value == null) {
            throw new IllegalArgumentException("semantic category key fields must not be null");
        }
        target.append(value.length()).append(':').append(value);
    }

    ICraftingHandler loadCompleteCategory() throws ExportFailure {
        return loadCompleteCategory(true);
    }

    ICraftingHandler loadCompleteCategoryAllowEmpty() throws ExportFailure {
        return loadCompleteCategory(false);
    }

    private ICraftingHandler loadCompleteCategory(boolean requireRecipes)
            throws ExportFailure {
        final ICraftingHandler loaded;
        try {
            loaded = adapter == CompleteCategoryAdapters.Adapter.STANDARD
                    ? prototype.getRecipeHandler(loadIdentifier)
                    : CompleteCategoryAdapters.load(adapter, prototype);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED", handlerId
                    + " rejected complete-category operation " + loadIdentifier, error);
        }
        if (loaded == null) {
            throw new ExportFailure("HANDLER_UNLOADED", handlerId
                    + " returned null for complete-category operation " + loadIdentifier);
        }
        if (loaded.getClass() != prototype.getClass()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                    + " loaded runtime class " + loaded.getClass().getName()
                    + " instead of exact registered class " + prototype.getClass().getName()
                    + " for category operation " + loadIdentifier);
        }
        final String loadedId;
        final String loadedOverlay;
        final int recipeCount;
        try {
            loadedId = required(loaded.getHandlerId(), "loaded handler ID", loaded.getClass());
            loadedOverlay = optional(loaded.getOverlayIdentifier());
            recipeCount = loaded.numRecipes();
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED", handlerId
                    + " failed while validating its loaded category", error);
        }
        if (!handlerId.equals(loadedId)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                    + " loaded a different raw handler lineage " + loadedId
                    + " for category operation " + loadIdentifier);
        }
        if (!equalNullable(overlayIdentifier, loadedOverlay)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                    + " changed overlay discriminator from " + overlayIdentifier
                    + " to " + loadedOverlay + " for category operation " + loadIdentifier);
        }
        if (recipeCount < 0 || (requireRecipes && recipeCount == 0)) {
            throw new ExportFailure("HANDLER_UNLOADED", handlerId + " loaded " + recipeCount
                    + " recipes for complete-category operation " + loadIdentifier);
        }
        return loaded;
    }

    /**
     * Loads exactly one category on the successful path. If that load fails, the failure path
     * probes every remaining plan so one boot reports the complete set of loader-contract
     * failures instead of revealing them serially across retries.
     */
    static ICraftingHandler loadCompleteCategoryWithFailureAudit(
            List<HandlerCategoryPlan> plans, int planIndex) throws ExportFailure {
        if (plans == null || planIndex < 0 || planIndex >= plans.size()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "invalid category-load audit position " + planIndex + "/"
                            + (plans == null ? "null" : plans.size()));
        }
        try {
            return plans.get(planIndex).loadCompleteCategory();
        } catch (Throwable firstFailure) {
            FatalErrors.rethrowIfFatal(firstFailure);
            throw aggregateRemainingLoadFailures(plans, planIndex, firstFailure);
        }
    }

    private static ExportFailure aggregateRemainingLoadFailures(
            List<HandlerCategoryPlan> plans, int failedIndex, Throwable firstFailure) {
        List<String> issues = new ArrayList<String>();
        List<Throwable> subsequentFailures = new ArrayList<Throwable>();
        issues.add(loadFailureIssue(plans.get(failedIndex), firstFailure));
        for (int index = failedIndex + 1; index < plans.size(); index++) {
            HandlerCategoryPlan plan = plans.get(index);
            try {
                // Deliberately discard successful probes. This audit never creates category
                // metadata, renders widgets, indexes recipes, or publishes staged output.
                plan.loadCompleteCategory();
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                issues.add(loadFailureIssue(plan, error));
                subsequentFailures.add(error);
            }
        }
        Collections.sort(issues);
        StringBuilder message = new StringBuilder(4096);
        message.append("failure-only complete-category load audit found ")
                .append(issues.size()).append(" failing handler(s); export remains aborted")
                .append(" and no further category metadata or rendering was started");
        for (String issue : issues) {
            message.append("\n- ").append(issue);
        }
        ExportFailure aggregate = new ExportFailure(
                "HANDLER_UNLOADED", message.toString(), firstFailure);
        for (Throwable failure : subsequentFailures) {
            aggregate.addSuppressed(failure);
        }
        return aggregate;
    }

    private static String loadFailureIssue(HandlerCategoryPlan plan, Throwable error) {
        String detail = error.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            detail = error.getClass().getName();
        }
        return "handlerId=" + plan.handlerId
                + " categoryId=" + plan.categoryId
                + " class=" + plan.prototype.getClass().getName()
                + " operation=" + plan.loadIdentifier + ": " + detail;
    }

    private static String matrixRow(
            int index, BaseInspection inspected, HandlerCategoryPlan plan,
            CompleteCategoryAdapters.Policy policy, Throwable failure) {
        StringBuilder row = new StringBuilder(512);
        row.append("index=").append(index);
        if (inspected == null) {
            row.append(" class=<unavailable> rawHandlerId=<unavailable>")
                    .append(" overlay=<unavailable> selector=<unavailable>")
                    .append(" rects=<unavailable> selectedSource=<unavailable>")
                    .append(" selectedOperation=<unavailable> categoryId=<unavailable>");
        } else {
            row.append(" class=").append(escaped(inspected.runtimeClass))
                    .append(" rawHandlerId=").append(escaped(inspected.handlerId))
                    .append(" overlay=").append(escaped(inspected.overlay))
                    .append(" selector=").append(escaped(inspected.selector))
                    .append(" rects=").append(matrixOperations(inspected.transferOperations));
            if (plan != null) {
                row.append(" selectedSource=").append(escaped(plan.operationSource))
                        .append(" selectedOperation=").append(escaped(plan.operationMarker))
                        .append(" adapterContract=").append(escaped(plan.adapterContract))
                        .append(" categoryId=").append(plan.categoryId);
            } else if (policy != null
                    && CompleteCategoryAdapters.isExcludedFromCategoryExport(policy.adapter)) {
                row.append(" selectedSource=").append(OPERATION_SOURCE_EXCLUDED)
                        .append(" selectedOperation=").append(escaped(policy.contract))
                        .append(" adapterContract=").append(escaped(policy.contract))
                        .append(" categoryId=<excluded>");
            } else {
                row.append(" selectedSource=<unresolved> selectedOperation=<unresolved>")
                        .append(" categoryId=<unresolved>");
            }
        }
        if (failure == null) {
            row.append(" status=ok");
        } else {
            String detail = failure.getMessage();
            row.append(" status=failure detail=")
                    .append(escaped(detail == null ? failure.getClass().getName() : detail));
        }
        return row.toString();
    }

    private static String matrixOperations(List<TransferOperation> operations) {
        StringBuilder value = new StringBuilder();
        value.append('[');
        for (int index = 0; index < operations.size(); index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append(index).append(':').append(operations.get(index).matrixValue());
        }
        return value.append(']').toString();
    }

    private static void logStructuralMatrix(List<String> rows) {
        StringBuilder matrix = new StringBuilder(Math.max(4096, rows.size() * 256));
        matrix.append("[gtnh-nei-export] Deterministic crafting-handler structural matrix; rows=")
                .append(rows.size());
        for (String row : rows) {
            matrix.append("\n- ").append(row);
        }
        GtnhNeiExportMod.LOGGER.info(matrix.toString());
    }

    private static String escaped(String value) {
        if (value == null) {
            return "<none>";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        escaped.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\\' || current == '"') {
                escaped.append('\\').append(current);
            } else if (current == '\n') {
                escaped.append("\\n");
            } else if (current == '\r') {
                escaped.append("\\r");
            } else if (current == '\t') {
                escaped.append("\\t");
            } else {
                escaped.append(current);
            }
        }
        return escaped.append('"').toString();
    }

    private static String required(String value, String field, Class<?> type)
            throws ExportFailure {
        String normalized = optional(value);
        if (normalized == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + " returned a blank " + field);
        }
        return normalized;
    }

    private static String optional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean equalNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
