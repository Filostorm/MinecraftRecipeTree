package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.item.ItemStack;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Exact read-only contracts for GTNH 2.8.4 handlers which NEI registers as crafting handlers,
 * but which do not expose an executable complete recipe category.
 *
 * <p>The validators in this class deliberately never invoke {@code newInstance}, an item-query
 * loader, {@code getRecipeHandler}, or {@code getUsageHandler}. Those APIs construct and mutate
 * query-local UI state. Instead, the pinned class bytes and reflective topology establish the
 * query/presentation contract, while the live registered prototype and backing registries are
 * inspected without mutation. Every accepted prototype emits an explicit source-state row.</p>
 */
final class PinnedNonRecipeHandlers {
    static final String GT_MULTIBLOCK_HANDLER =
            "blockrenderer6343.integration.gregtech.GTNEIMultiblockHandler";
    static final String STRUCTURELIB_MULTIBLOCK_HANDLER =
            "blockrenderer6343.integration.structurelib.StructureCompatNEIHandler";
    static final String INFORMATION_HANDLER =
            "codechicken.nei.recipe.InformationHandler";

    static final String READ_ONLY_VALIDATION_MODE =
            "read-only:no-item-query-or-cache-population-v1";

    private static final String MULTIBLOCK_HANDLER =
            "blockrenderer6343.integration.nei.MultiblockHandler";
    private static final String MULTIBLOCK_CACHE = MULTIBLOCK_HANDLER + "$RecipeCacher";
    private static final String GUI_MULTIBLOCK_HANDLER =
            "blockrenderer6343.integration.nei.GuiMultiblockHandler";
    private static final String GT_GUI_HANDLER =
            "blockrenderer6343.integration.gregtech.GTGuiMultiblockHandler";
    private static final String STRUCTURELIB_GUI_HANDLER =
            "blockrenderer6343.integration.structurelib.StructureCompatGuiHandler";
    private static final String CONSTRUCTABLE =
            "com.gtnewhorizon.structurelib.alignment.constructable.IConstructable";
    private static final String MULTIBLOCK_INFO_CONTAINER =
            "com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer";
    private static final String LONG_OBJECT_MAP =
            "it.unimi.dsi.fastutil.longs.Long2ObjectMap";
    private static final String OBJECT_OBJECT_MAP =
            "it.unimi.dsi.fastutil.objects.Object2ObjectMap";
    private static final String OBJECT_SET =
            "it.unimi.dsi.fastutil.objects.ObjectSet";
    private static final String INFORMATION_PAGE =
            INFORMATION_HANDLER + "$InformationPage";
    private static final String CACHED_INFORMATION_PAGE =
            INFORMATION_HANDLER + "$CachedInfoPage";

    // These hashes bind the semantic method bodies as well as the reflective signatures. They
    // were computed from the exact GTNH 2.8.4 BlockRenderer 1.3.17 and pinned NEI 2.8.44 jars.
    private static final String GT_MULTIBLOCK_CLASS_SHA256 =
            "0a36e526ad8aafa5b47a2dd93752311965675edd1fbb087d45cae0ccb7927baf";
    private static final String STRUCTURELIB_MULTIBLOCK_CLASS_SHA256 =
            "594556aaf33d4374a9d36d19380eb0372c7bcd5a3489931922c235bff81cd8d1";
    private static final String MULTIBLOCK_HANDLER_CLASS_SHA256 =
            "b963843be1711132f96914588c76acb1de8f882f9ea61687a9222d36463d0a29";
    private static final String MULTIBLOCK_CACHE_CLASS_SHA256 =
            "09e7a1fea6f47f29570124400e618f7e95a6d832c0f1913e4599c666414d64fb";
    private static final String INFORMATION_HANDLER_CLASS_SHA256 =
            "22fa8f40d61450aa51cabcfd8f36d90a44f76e3f994409ba8d473a90b37e248e";
    private static final String INFORMATION_PAGE_CLASS_SHA256 =
            "11b51ed8a790963a6f2117e08c05e23009ffb449d5fb1e6985a1d16a1f4f084d";
    private static final String CACHED_INFORMATION_PAGE_CLASS_SHA256 =
            "47caee69f696e429d92a63de85698745fff94be617fea76fe4cff2e3b7118ba2";
    private static final String TEMPLATE_HANDLER_CLASS_SHA256 =
            "9fdf4828a1ec20e5c089531e7f9494a6f88eba768fe12168053dae4f9848392d";

    enum Disposition {
        QUERY_ONLY("excluded-non-recipe-query"),
        PRESENTATION_ONLY("excluded-non-recipe-presentation");

        final String action;

        Disposition(String action) {
            this.action = action;
        }
    }

    /** Minimal immutable seam consumed by {@link CompleteCategoryAdapters}. */
    static final class PolicyEntry {
        final String handlerClass;
        final String handlerId;
        final String expectedOverlay;
        final Disposition disposition;
        final String action;
        final String contract;
        final String sourceContract;
        final String handlerClassSha256;

        PolicyEntry(String handlerClass, String handlerId, String expectedOverlay,
                    Disposition disposition, String contract, String sourceContract,
                    String handlerClassSha256) {
            this.handlerClass = handlerClass;
            this.handlerId = handlerId;
            this.expectedOverlay = expectedOverlay;
            this.disposition = disposition;
            this.action = disposition.action;
            this.contract = contract;
            this.sourceContract = sourceContract;
            this.handlerClassSha256 = handlerClassSha256;
        }

        /**
         * Both transfer-selector and populated-transfer-rectangle pins are intentionally null.
         * The registered prototype exposes only its item-query overlay discriminator.
         */
        String expectedTransferSelector() {
            return null;
        }

        String expectedTransferRect() {
            return null;
        }

        String contractRow() {
            return handlerClass + "|" + handlerId + "|" + expectedOverlay + "|"
                    + disposition.name() + "|" + action + "|" + contract + "|"
                    + sourceContract + "|" + handlerClassSha256;
        }
    }

    /** Deterministically rendered, explicitly named source/cache telemetry. */
    static final class SourceState {
        final String handlerId;
        final String contract;
        private final Map<String, String> metrics;

        SourceState(String handlerId, String contract, Map<String, String> metrics) {
            if (handlerId == null || handlerId.trim().isEmpty()
                    || contract == null || contract.trim().isEmpty()
                    || metrics == null || metrics.isEmpty()) {
                throw new IllegalArgumentException(
                        "source-state telemetry requires a handler, contract, and metrics");
            }
            TreeMap<String, String> sorted = new TreeMap<String, String>();
            for (Map.Entry<String, String> metric : metrics.entrySet()) {
                if (metric.getKey() == null || metric.getKey().trim().isEmpty()
                        || metric.getValue() == null || metric.getValue().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "source-state telemetry contains a blank key or value");
                }
                if (sorted.put(metric.getKey(), metric.getValue()) != null) {
                    throw new IllegalArgumentException(
                            "source-state telemetry contains duplicate metric "
                                    + metric.getKey());
                }
            }
            this.handlerId = handlerId;
            this.contract = contract;
            this.metrics = Collections.unmodifiableMap(sorted);
        }

        Map<String, String> metrics() {
            return metrics;
        }

        String canonical() {
            StringBuilder row = new StringBuilder(512);
            row.append("handlerId=").append(quoted(handlerId))
                    .append(" contract=").append(quoted(contract));
            for (Map.Entry<String, String> metric : metrics.entrySet()) {
                row.append(' ').append(metric.getKey()).append('=')
                        .append(quoted(metric.getValue()));
            }
            return row.toString();
        }
    }

    private static final class PrototypeSnapshot {
        final Object recipes;
        final int recipeCacheSize;
        final Object transferRects;
        final int transferRectSize;

        PrototypeSnapshot(TemplateRecipeHandler prototype) throws ExportFailure {
            if (prototype.arecipes == null || prototype.transferRects == null) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        prototype.getClass().getName()
                                + " has null inherited NEI prototype collections");
            }
            this.recipes = prototype.arecipes;
            this.recipeCacheSize = prototype.arecipes.size();
            this.transferRects = prototype.transferRects;
            this.transferRectSize = prototype.transferRects.size();
        }

        void requireUnchanged(TemplateRecipeHandler prototype) throws ExportFailure {
            if (prototype.arecipes != recipes
                    || prototype.arecipes.size() != recipeCacheSize
                    || prototype.transferRects != transferRects
                    || prototype.transferRects.size() != transferRectSize) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        prototype.getClass().getName()
                                + " read-only non-recipe validation mutated NEI prototype state");
            }
        }
    }

    private static final List<PolicyEntry> POLICIES = createPolicies();
    private static final Map<String, PolicyEntry> POLICIES_BY_ID = indexPolicies(POLICIES);

    private PinnedNonRecipeHandlers() {
    }

    static boolean supports(String handlerId) {
        return POLICIES_BY_ID.containsKey(handlerId);
    }

    static PolicyEntry policyFor(String handlerId) throws ExportFailure {
        PolicyEntry policy = POLICIES_BY_ID.get(handlerId);
        if (policy == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "no pinned non-recipe contract exists for " + handlerId);
        }
        return policy;
    }

    static List<PolicyEntry> policyEntries() {
        return POLICIES;
    }

    /**
     * Validates one live registered prototype without constructing or executing any item query.
     * The returned state is also logged so exclusion never becomes a silent fallback.
     */
    static SourceState validatePrototype(String handlerId, ICraftingHandler prototype)
            throws ExportFailure {
        PolicyEntry policy = policyFor(handlerId);
        if (prototype == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    handlerId + " registered prototype is null");
        }
        if (!policy.handlerClass.equals(prototype.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                    + " runtime class drifted; expected " + policy.handlerClass
                    + ", got " + prototype.getClass().getName());
        }
        if (!(prototype instanceof TemplateRecipeHandler)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                    + " is no longer a TemplateRecipeHandler");
        }

        TemplateRecipeHandler template = (TemplateRecipeHandler) prototype;
        PrototypeSnapshot before = new PrototypeSnapshot(template);
        final SourceState state;
        try {
            String overlay = prototype.getOverlayIdentifier();
            if (!policy.expectedOverlay.equals(overlay)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                        + " overlay drifted; expected " + policy.expectedOverlay
                        + ", got " + overlay);
            }
            if (before.transferRectSize != 0) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                        + " unexpectedly exposes " + before.transferRectSize
                        + " transfer operation(s)");
            }
            if (GT_MULTIBLOCK_HANDLER.equals(handlerId)
                    || STRUCTURELIB_MULTIBLOCK_HANDLER.equals(handlerId)) {
                state = validateBlockRenderer(policy, template, before);
            } else if (INFORMATION_HANDLER.equals(handlerId)) {
                state = validateInformationHandler(policy, template, before);
            } else {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "no structural validator exists for " + handlerId);
            }
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED", handlerId
                    + " exact non-recipe structural/source validation failed", error);
        } finally {
            before.requireUnchanged(template);
        }

        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Pinned non-recipe source state {}",
                state.canonical());
        return state;
    }

    private static SourceState validateBlockRenderer(
            PolicyEntry policy, TemplateRecipeHandler prototype,
            PrototypeSnapshot prototypeState) throws Exception {
        Class<?> runtimeClass = prototype.getClass();
        ClassLoader loader = runtimeClass.getClassLoader();
        Class<?> multiblock = exactClass(loader, MULTIBLOCK_HANDLER);
        Class<?> cacheClass = exactClass(loader, MULTIBLOCK_CACHE);
        Class<?> guiClass = exactClass(loader, GUI_MULTIBLOCK_HANDLER);
        Class<?> constructable = exactClass(loader, CONSTRUCTABLE);
        Class<?> objectSet = exactClass(loader, OBJECT_SET);
        Class<?> longObjectMap = exactClass(loader, LONG_OBJECT_MAP);

        requireDirectSuperclass(runtimeClass, multiblock, policy.handlerId);
        requireDirectSuperclass(multiblock, TemplateRecipeHandler.class, MULTIBLOCK_HANDLER);
        requirePublicNoArgConstructor(runtimeClass, policy.handlerId);
        requireClassDigest(runtimeClass, policy.handlerClassSha256, policy.handlerId);
        requireClassDigest(multiblock, MULTIBLOCK_HANDLER_CLASS_SHA256,
                MULTIBLOCK_HANDLER);
        requireClassDigest(cacheClass, MULTIBLOCK_CACHE_CLASS_SHA256, MULTIBLOCK_CACHE);
        requireClassDigest(TemplateRecipeHandler.class, TEMPLATE_HANDLER_CLASS_SHA256,
                TemplateRecipeHandler.class.getName());

        requireDeclaredMethod(runtimeClass, "newInstance", TemplateRecipeHandler.class,
                Modifier.PUBLIC, Modifier.STATIC);
        requireDeclaredMethod(runtimeClass, "getConstructableStack", ItemStack.class,
                Modifier.PUBLIC, Modifier.STATIC, constructable);
        requireDeclaredMethod(runtimeClass, "tryLoadingMultiblocks", objectSet,
                Modifier.PROTECTED, Modifier.PUBLIC | Modifier.STATIC, ItemStack.class);
        requireDeclaredMethod(multiblock, "loadCraftingRecipes", void.class,
                Modifier.PUBLIC, Modifier.STATIC, ItemStack.class);
        requireDeclaredMethod(multiblock, "loadUsageRecipes", void.class,
                Modifier.PUBLIC, Modifier.STATIC, ItemStack.class);
        requireDeclaredMethod(multiblock, "loadRecipes", void.class,
                Modifier.PRIVATE, Modifier.PUBLIC | Modifier.PROTECTED | Modifier.STATIC,
                ItemStack.class);
        requireDeclaredMethod(multiblock, "numRecipes", int.class,
                Modifier.PUBLIC, Modifier.STATIC);
        requireDeclaredMethod(multiblock, "getIngredientStacks", List.class,
                Modifier.PUBLIC, Modifier.STATIC, int.class);
        requireDeclaredMethod(multiblock, "getResultStack", PositionedStack.class,
                Modifier.PUBLIC, Modifier.STATIC, int.class);
        requireDeclaredMethod(multiblock, "getOtherStacks", List.class,
                Modifier.PUBLIC, Modifier.STATIC, int.class);

        Class<?> constructableArray = Array.newInstance(constructable, 0).getClass();
        Field current = exactDeclaredField(
                multiblock, "currentMultiblocks", constructableArray,
                Modifier.PROTECTED, Modifier.PUBLIC | Modifier.PRIVATE | Modifier.STATIC);
        Field recipeCacher = exactDeclaredField(
                multiblock, "recipeCacher", cacheClass,
                Modifier.PROTECTED, Modifier.PUBLIC | Modifier.PRIVATE | Modifier.STATIC);
        Field guiHandler = exactDeclaredField(
                multiblock, "guiHandler", guiClass,
                Modifier.PROTECTED, Modifier.PUBLIC | Modifier.PRIVATE | Modifier.STATIC);
        Field dummyField = exactDeclaredField(
                multiblock, "DUMMY_STACK", PositionedStack.class,
                Modifier.PROTECTED | Modifier.STATIC | Modifier.FINAL,
                Modifier.PUBLIC | Modifier.PRIVATE);
        exactDeclaredField(multiblock, "lastStack", ItemStack.class,
                Modifier.PRIVATE | Modifier.STATIC,
                Modifier.PUBLIC | Modifier.PROTECTED | Modifier.FINAL);

        if (prototype.numRecipes() != 0 || prototypeState.recipeCacheSize != 0
                || current.get(prototype) != null) {
            throw new ExportFailure("RECIPE_SEMANTICS", policy.handlerId
                    + " registered prototype must retain zero pages and no item-query selection");
        }
        Object cache = recipeCacher.get(prototype);
        if (cache == null || cache.getClass() != cacheClass) {
            throw new ExportFailure("HANDLER_UNLOADED", policy.handlerId
                    + " has no exact BlockRenderer UI candidate cache");
        }
        Field positionedResults = exactDeclaredField(
                cacheClass, "positionedResults", List.class,
                Modifier.PRIVATE | Modifier.FINAL,
                Modifier.PUBLIC | Modifier.PROTECTED | Modifier.STATIC);
        Field cacheOwner = exactDeclaredField(
                cacheClass, "this$0", multiblock,
                Modifier.FINAL, Modifier.PUBLIC | Modifier.PRIVATE
                        | Modifier.PROTECTED | Modifier.STATIC);
        Object rawCandidates = positionedResults.get(cache);
        if (!(rawCandidates instanceof List) || !((List<?>) rawCandidates).isEmpty()
                || cacheOwner.get(cache) != prototype) {
            throw new ExportFailure("RECIPE_SEMANTICS", policy.handlerId
                    + " prototype candidate cache is not empty and owner-bound");
        }
        requireDirectSuperclass(cacheClass,
                TemplateRecipeHandler.CachedRecipe.class, MULTIBLOCK_CACHE);
        requireDeclaredMethod(cacheClass, "setResults", void.class,
                Modifier.PUBLIC, Modifier.STATIC, List.class);
        requireDeclaredMethod(cacheClass, "getResult", PositionedStack.class,
                Modifier.PUBLIC, Modifier.STATIC);
        requireDeclaredMethod(cacheClass, "getOtherStacks", List.class,
                Modifier.PUBLIC, Modifier.STATIC);

        Object dummy = dummyField.get(null);
        if (!(dummy instanceof PositionedStack)
                || prototype.getResultStack(0) != dummy
                || ((PositionedStack) dummy).relx != 0
                || ((PositionedStack) dummy).rely != 9999
                || ((PositionedStack) dummy).items == null
                || ((PositionedStack) dummy).items.length != 1
                || ((PositionedStack) dummy).item == null
                || prototype.getIngredientStacks(0) == null
                || !prototype.getIngredientStacks(0).isEmpty()
                || prototype.getOtherStacks(0) == null
                || !prototype.getOtherStacks(0).isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS", policy.handlerId
                    + " no longer exposes empty ingredients, an inert dummy result, and "
                    + "an empty pre-query candidate UI cache");
        }

        Class<?> expectedGui = exactClass(loader,
                GT_MULTIBLOCK_HANDLER.equals(policy.handlerId)
                        ? GT_GUI_HANDLER : STRUCTURELIB_GUI_HANDLER);
        Field baseHandler = exactDeclaredField(
                runtimeClass, "baseHandler", expectedGui,
                Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL,
                Modifier.PUBLIC | Modifier.PROTECTED);
        Object base = baseHandler.get(null);
        if (base == null || guiHandler.get(prototype) != base) {
            throw new ExportFailure("HANDLER_UNLOADED", policy.handlerId
                    + " registered prototype is not bound to its exact shared UI controller");
        }

        Map<String, String> metrics = baseMetrics(
                policy, prototypeState, policy.handlerClassSha256);
        metrics.put("currentItemQuerySelection", "none");
        metrics.put("dummyResult", "inert@(0,9999)");
        metrics.put("ingredientSemantics", "empty");
        metrics.put("uiCandidateCache", "empty");

        Field componentField = exactDeclaredField(
                runtimeClass, "multiBlockComponents", longObjectMap,
                Modifier.PRIVATE | Modifier.STATIC,
                Modifier.PUBLIC | Modifier.PROTECTED | Modifier.FINAL);
        MapCardinality components = inspectComponentMap(
                componentField.get(null), longObjectMap, constructable, policy.handlerId);
        metrics.put("componentCacheState", components.state);
        metrics.put("componentKeyCount", Integer.toString(components.keys));
        metrics.put("componentRelationCount", Integer.toString(components.relations));

        if (GT_MULTIBLOCK_HANDLER.equals(policy.handlerId)) {
            Field listField = exactDeclaredField(
                    runtimeClass, "multiblocksList", List.class,
                    Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL,
                    Modifier.PRIVATE | Modifier.PROTECTED);
            Object rawList = listField.get(null);
            int count = requireConstructableCollection(
                    rawList, constructable, policy.handlerId + " constructable registry");
            metrics.put("constructableRegistryCount", Integer.toString(count));
            metrics.put("constructableRegistryType", rawList.getClass().getName());
        } else {
            Class<?> objectObjectMap = exactClass(loader, OBJECT_OBJECT_MAP);
            Field stacksField = exactDeclaredField(
                    runtimeClass, "stacks", objectObjectMap,
                    Modifier.PRIVATE | Modifier.STATIC,
                    Modifier.PUBLIC | Modifier.PROTECTED | Modifier.FINAL);
            MapCardinality stacks = inspectStackMap(
                    stacksField.get(null), objectObjectMap, constructable, policy.handlerId);
            metrics.put("constructableStackCacheState", stacks.state);
            metrics.put("constructableStackCount", Integer.toString(stacks.keys));

            Class<?> infoContainer = exactClass(loader, MULTIBLOCK_INFO_CONTAINER);
            Field registryField = exactDeclaredField(
                    infoContainer, "MULTIBLOCK_MAP", java.util.HashMap.class,
                    Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL,
                    Modifier.PRIVATE | Modifier.PROTECTED);
            Object rawRegistry = registryField.get(null);
            if (!(rawRegistry instanceof Map)) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        policy.handlerId + " StructureLib registry is unavailable");
            }
            int registryCount = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawRegistry).entrySet()) {
                if (!(entry.getKey() instanceof String)
                        || ((String) entry.getKey()).trim().isEmpty()
                        || entry.getValue() == null
                        || !infoContainer.isInstance(entry.getValue())) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", policy.handlerId
                            + " StructureLib registry contains a noncanonical entry");
                }
                registryCount++;
            }
            metrics.put("structureLibRegistryCount", Integer.toString(registryCount));
        }
        return new SourceState(policy.handlerId, policy.contract, metrics);
    }

    private static SourceState validateInformationHandler(
            PolicyEntry policy, TemplateRecipeHandler prototype,
            PrototypeSnapshot prototypeState) throws Exception {
        Class<?> runtimeClass = prototype.getClass();
        ClassLoader loader = runtimeClass.getClassLoader();
        Class<?> informationPage = exactClass(loader, INFORMATION_PAGE);
        Class<?> cachedPage = exactClass(loader, CACHED_INFORMATION_PAGE);
        Class<?> itemFilter = exactClass(loader, "codechicken.nei.api.ItemFilter");

        requireDirectSuperclass(runtimeClass, TemplateRecipeHandler.class, policy.handlerId);
        requirePublicNoArgConstructor(runtimeClass, policy.handlerId);
        requireClassDigest(runtimeClass, INFORMATION_HANDLER_CLASS_SHA256,
                INFORMATION_HANDLER);
        requireClassDigest(informationPage, INFORMATION_PAGE_CLASS_SHA256,
                INFORMATION_PAGE);
        requireClassDigest(cachedPage, CACHED_INFORMATION_PAGE_CLASS_SHA256,
                CACHED_INFORMATION_PAGE);
        requireClassDigest(TemplateRecipeHandler.class, TEMPLATE_HANDLER_CLASS_SHA256,
                TemplateRecipeHandler.class.getName());

        Method inheritedNewInstance = runtimeClass.getMethod("newInstance");
        if (inheritedNewInstance.getDeclaringClass() != TemplateRecipeHandler.class
                || inheritedNewInstance.getReturnType() != TemplateRecipeHandler.class
                || !Modifier.isPublic(inheritedNewInstance.getModifiers())
                || Modifier.isStatic(inheritedNewInstance.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", policy.handlerId
                    + " no longer uses TemplateRecipeHandler's no-argument constructor "
                    + "newInstance contract");
        }
        requireDeclaredMethod(runtimeClass, "loadCraftingRecipes", void.class,
                Modifier.PUBLIC, Modifier.STATIC, ItemStack.class);
        requireDeclaredMethod(runtimeClass, "loadUsageRecipes", void.class,
                Modifier.PUBLIC, Modifier.STATIC, ItemStack.class);
        requireDeclaredMethod(runtimeClass, "getOverlayIdentifier", String.class,
                Modifier.PUBLIC, Modifier.STATIC);
        requireDeclaredMethod(runtimeClass, "addInformationPage", void.class,
                Modifier.PUBLIC | Modifier.STATIC, 0, String.class, String.class);
        requireDeclaredMethod(runtimeClass, "populateStacks", void.class,
                Modifier.PUBLIC | Modifier.STATIC, 0, ItemStack.class);
        requireDeclaredMethod(runtimeClass, "clearCache", void.class,
                Modifier.PUBLIC | Modifier.STATIC, 0);

        Field itemInfo = exactDeclaredField(
                runtimeClass, "ITEM_INFO", List.class,
                Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL,
                Modifier.PUBLIC | Modifier.PROTECTED);
        Field pageItems = exactDeclaredField(
                informationPage, "items", List.class,
                Modifier.FINAL, Modifier.PUBLIC | Modifier.PRIVATE
                        | Modifier.PROTECTED | Modifier.STATIC);
        Field pageInfo = exactDeclaredField(
                informationPage, "info", String.class,
                Modifier.FINAL, Modifier.PUBLIC | Modifier.PRIVATE
                        | Modifier.PROTECTED | Modifier.STATIC);
        Field pageFilter = exactDeclaredField(
                informationPage, "filter", itemFilter,
                Modifier.FINAL, Modifier.PUBLIC | Modifier.PRIVATE
                        | Modifier.PROTECTED | Modifier.STATIC);

        requireDirectSuperclass(
                cachedPage, TemplateRecipeHandler.CachedRecipe.class, CACHED_INFORMATION_PAGE);
        exactDeclaredField(cachedPage, "stack", PositionedStack.class,
                Modifier.PRIVATE | Modifier.FINAL,
                Modifier.PUBLIC | Modifier.PROTECTED | Modifier.STATIC);
        exactDeclaredField(cachedPage, "lines", List.class,
                Modifier.PRIVATE | Modifier.FINAL,
                Modifier.PUBLIC | Modifier.PROTECTED | Modifier.STATIC);
        requireDeclaredMethod(cachedPage, "getResult", PositionedStack.class,
                Modifier.PUBLIC, Modifier.STATIC);
        requireDeclaredMethod(cachedPage, "getIngredients", List.class,
                Modifier.PUBLIC, Modifier.STATIC);
        requireDeclaredMethod(cachedPage, "getLines", List.class,
                Modifier.PUBLIC, Modifier.STATIC);

        if (prototype.numRecipes() != 0 || prototypeState.recipeCacheSize != 0) {
            throw new ExportFailure("RECIPE_SEMANTICS", policy.handlerId
                    + " registered prototype must remain an unqueried zero-page text browser");
        }
        Object rawPages = itemInfo.get(null);
        if (rawPages == null || rawPages.getClass() != ArrayList.class) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", policy.handlerId
                    + " ITEM_INFO must remain the exact mutable ArrayList registry");
        }

        int pages = 0;
        int populatedItemReferences = 0;
        int pagesWithoutPopulatedItems = 0;
        int informationCharacters = 0;
        Set<Object> pageIdentities =
                Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
        for (Object page : (List<?>) rawPages) {
            if (page == null || page.getClass() != informationPage
                    || !pageIdentities.add(page)) {
                throw new ExportFailure("HANDLER_DUPLICATE", policy.handlerId
                        + " ITEM_INFO contains a null, duplicate, or non-exact page");
            }
            Object rawItems = pageItems.get(page);
            Object rawInfo = pageInfo.get(page);
            Object rawFilter = pageFilter.get(page);
            if (rawItems == null || rawItems.getClass() != ArrayList.class
                    || !(rawInfo instanceof String)
                    || ((String) rawInfo).trim().isEmpty()
                    || !itemFilter.isInstance(rawFilter)) {
                throw new ExportFailure("RECIPE_SEMANTICS", policy.handlerId
                        + " ITEM_INFO page lost its exact filter/text/item-list shape");
            }
            List<?> items = (List<?>) rawItems;
            for (Object item : items) {
                if (!(item instanceof ItemStack)) {
                    throw new ExportFailure("RECIPE_SEMANTICS", policy.handlerId
                            + " ITEM_INFO page contains a non-ItemStack display reference");
                }
            }
            pages++;
            populatedItemReferences += items.size();
            if (items.isEmpty()) {
                pagesWithoutPopulatedItems++;
            }
            informationCharacters += ((String) rawInfo).length();
        }

        Map<String, String> metrics = baseMetrics(
                policy, prototypeState, INFORMATION_HANDLER_CLASS_SHA256);
        metrics.put("informationPageCount", Integer.toString(pages));
        metrics.put("informationCharacters", Integer.toString(informationCharacters));
        metrics.put("itemFilterCount", Integer.toString(pages));
        metrics.put("populatedItemReferenceCount",
                Integer.toString(populatedItemReferences));
        metrics.put("pagesWithoutPopulatedItems",
                Integer.toString(pagesWithoutPopulatedItems));
        metrics.put("cachedPageResultContract", "null-result");
        metrics.put("cachedPageIngredientContract", "matched-items-for-text-page");
        return new SourceState(policy.handlerId, policy.contract, metrics);
    }

    private static Map<String, String> baseMetrics(
            PolicyEntry policy, PrototypeSnapshot prototype, String classSha256) {
        Map<String, String> metrics = new LinkedHashMap<String, String>();
        metrics.put("action", policy.action);
        metrics.put("classSha256", classSha256);
        metrics.put("prototypeRecipeCacheCount",
                Integer.toString(prototype.recipeCacheSize));
        metrics.put("prototypeTransferRectCount",
                Integer.toString(prototype.transferRectSize));
        metrics.put("sourceContract", policy.sourceContract);
        metrics.put("validationMode", READ_ONLY_VALIDATION_MODE);
        return metrics;
    }

    private static final class MapCardinality {
        final String state;
        final int keys;
        final int relations;

        MapCardinality(String state, int keys, int relations) {
            this.state = state;
            this.keys = keys;
            this.relations = relations;
        }
    }

    private static MapCardinality inspectComponentMap(
            Object raw, Class<?> expectedMap, Class<?> constructable, String label)
            throws ExportFailure {
        if (raw == null) {
            return new MapCardinality("pending-null", 0, 0);
        }
        if (!expectedMap.isInstance(raw) || !(raw instanceof Map)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    label + " component cache has the wrong runtime type");
        }
        int keys = 0;
        int relations = 0;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (!(entry.getKey() instanceof Long)
                    || !(entry.getValue() instanceof Collection)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        label + " component cache contains a noncanonical row");
            }
            for (Object value : (Collection<?>) entry.getValue()) {
                if (!constructable.isInstance(value)) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            label + " component cache contains a non-constructable value");
                }
                relations++;
            }
            keys++;
        }
        return new MapCardinality("ready", keys, relations);
    }

    private static MapCardinality inspectStackMap(
            Object raw, Class<?> expectedMap, Class<?> constructable, String label)
            throws ExportFailure {
        if (raw == null) {
            return new MapCardinality("pending-null", 0, 0);
        }
        if (!expectedMap.isInstance(raw) || !(raw instanceof Map)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    label + " constructable-stack cache has the wrong runtime type");
        }
        int entries = 0;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (!constructable.isInstance(entry.getKey())
                    || !(entry.getValue() instanceof ItemStack)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        label + " constructable-stack cache contains a noncanonical row");
            }
            entries++;
        }
        return new MapCardinality("ready", entries, entries);
    }

    private static int requireConstructableCollection(
            Object raw, Class<?> constructable, String label) throws ExportFailure {
        if (!(raw instanceof Collection)) {
            throw new ExportFailure("HANDLER_UNLOADED", label + " is unavailable");
        }
        int count = 0;
        for (Object value : (Collection<?>) raw) {
            if (!constructable.isInstance(value)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        label + " contains a non-constructable value");
            }
            count++;
        }
        return count;
    }

    private static void requirePublicNoArgConstructor(Class<?> type, String label)
            throws ExportFailure {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        label + " no-argument constructor is no longer public");
            }
        } catch (NoSuchMethodException error) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    label + " lost its exact public no-argument constructor", error);
        }
    }

    private static void requireDirectSuperclass(
            Class<?> type, Class<?> expected, String label) throws ExportFailure {
        if (type.getSuperclass() != expected) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label
                    + " superclass drifted; expected " + expected.getName() + ", got "
                    + (type.getSuperclass() == null
                    ? "<none>" : type.getSuperclass().getName()));
        }
    }

    private static Method requireDeclaredMethod(
            Class<?> owner, String name, Class<?> returnType,
            int requiredModifiers, int forbiddenModifiers, Class<?>... parameters)
            throws ExportFailure {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            int modifiers = method.getModifiers();
            if (method.getReturnType() != returnType
                    || (modifiers & requiredModifiers) != requiredModifiers
                    || (modifiers & forbiddenModifiers) != 0) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", owner.getName() + "." + name
                        + " signature/modifiers drifted");
            }
            return method;
        } catch (NoSuchMethodException error) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    owner.getName() + " lost exact method " + name, error);
        }
    }

    private static Field exactDeclaredField(
            Class<?> owner, String name, Class<?> type,
            int requiredModifiers, int forbiddenModifiers) throws ExportFailure {
        try {
            Field field = owner.getDeclaredField(name);
            int modifiers = field.getModifiers();
            if (field.getType() != type
                    || (modifiers & requiredModifiers) != requiredModifiers
                    || (modifiers & forbiddenModifiers) != 0) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", owner.getName() + "." + name
                        + " type/modifiers drifted");
            }
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException error) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    owner.getName() + " lost exact field " + name, error);
        }
    }

    private static Class<?> exactClass(ClassLoader loader, String className)
            throws ExportFailure {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException error) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "required pinned class is unavailable: " + className, error);
        }
    }

    private static void requireClassDigest(
            Class<?> type, String expectedSha256, String label) throws ExportFailure {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        InputStream input = type.getResourceAsStream(resource);
        if (input == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    label + " class bytes are unavailable at " + resource);
        }
        try {
            String actual = sha256(input);
            if (!expectedSha256.equals(actual)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", label
                        + " class-byte digest drifted; expected " + expectedSha256
                        + ", got " + actual);
            }
        } catch (IOException error) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    label + " class-byte digest could not be read", error);
        } finally {
            try {
                input.close();
            } catch (IOException error) {
                GtnhNeiExportMod.LOGGER.error(
                        "[gtnh-nei-export] Failed closing class-byte stream for " + label,
                        error);
            }
        }
    }

    private static String sha256(InputStream input) throws IOException, ExportFailure {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new ExportFailure("INTERNAL_ERROR", "SHA-256 is unavailable", error);
        }
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count > 0) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(String.format("%02x", value & 0xff));
        }
        return hex.toString();
    }

    private static List<PolicyEntry> createPolicies() {
        List<PolicyEntry> policies = new ArrayList<PolicyEntry>();
        policies.add(new PolicyEntry(
                GT_MULTIBLOCK_HANDLER,
                GT_MULTIBLOCK_HANDLER,
                GT_MULTIBLOCK_HANDLER,
                Disposition.QUERY_ONLY,
                "query-only:blockrenderer-gregtech-multiblock-item-query-ui-state-v1",
                "GTNEIMultiblockHandler.multiblocksList+multiBlockComponents",
                GT_MULTIBLOCK_CLASS_SHA256));
        policies.add(new PolicyEntry(
                STRUCTURELIB_MULTIBLOCK_HANDLER,
                STRUCTURELIB_MULTIBLOCK_HANDLER,
                STRUCTURELIB_MULTIBLOCK_HANDLER,
                Disposition.QUERY_ONLY,
                "query-only:blockrenderer-structurelib-multiblock-item-query-ui-state-v1",
                "IMultiblockInfoContainer.MULTIBLOCK_MAP+"
                        + "StructureCompatNEIHandler.stacks+multiBlockComponents",
                STRUCTURELIB_MULTIBLOCK_CLASS_SHA256));
        policies.add(new PolicyEntry(
                INFORMATION_HANDLER,
                INFORMATION_HANDLER,
                "information",
                Disposition.PRESENTATION_ONLY,
                "presentation-only:nei-item-filter-text-information-pages-v1",
                "InformationHandler.ITEM_INFO[filter,info,items]",
                INFORMATION_HANDLER_CLASS_SHA256));
        Collections.sort(policies, new Comparator<PolicyEntry>() {
            @Override
            public int compare(PolicyEntry left, PolicyEntry right) {
                return left.handlerId.compareTo(right.handlerId);
            }
        });
        return Collections.unmodifiableList(policies);
    }

    private static Map<String, PolicyEntry> indexPolicies(List<PolicyEntry> policies) {
        Map<String, PolicyEntry> indexed = new LinkedHashMap<String, PolicyEntry>();
        for (PolicyEntry policy : policies) {
            indexed.put(policy.handlerId, policy);
        }
        return Collections.unmodifiableMap(indexed);
    }

    static List<String> validateSpecLedgerForTest(List<PolicyEntry> policies) {
        List<String> issues = new ArrayList<String>();
        Set<String> ids = new HashSet<String>();
        Set<String> classes = new HashSet<String>();
        if (policies == null) {
            return Collections.singletonList("policy ledger is null");
        }
        for (int index = 0; index < policies.size(); index++) {
            PolicyEntry policy = policies.get(index);
            if (policy == null) {
                issues.add("policy #" + index + " is null");
                continue;
            }
            if (!ids.add(policy.handlerId)) {
                issues.add("duplicate handler ID " + policy.handlerId);
            }
            if (!classes.add(policy.handlerClass)) {
                issues.add("duplicate handler class " + policy.handlerClass);
            }
            if (!policy.handlerClass.equals(policy.handlerId)) {
                issues.add(policy.handlerId + " handler class/ID mismatch");
            }
            if (policy.expectedOverlay == null || policy.expectedOverlay.trim().isEmpty()
                    || policy.action == null || policy.action.trim().isEmpty()
                    || policy.contract == null || policy.contract.trim().isEmpty()
                    || policy.sourceContract == null || policy.sourceContract.trim().isEmpty()) {
                issues.add(policy.handlerId + " contains blank contract state");
            }
            if (policy.disposition == null
                    || !policy.disposition.action.equals(policy.action)) {
                issues.add(policy.handlerId + " disposition/action mismatch");
            }
            if (policy.handlerClassSha256 == null
                    || !policy.handlerClassSha256.matches("[0-9a-f]{64}")) {
                issues.add(policy.handlerId + " has an invalid class digest");
            }
            if (policy.expectedTransferSelector() != null
                    || policy.expectedTransferRect() != null) {
                issues.add(policy.handlerId + " unexpectedly exposes transfer state");
            }
        }
        Collections.sort(issues);
        return Collections.unmodifiableList(issues);
    }

    private static String quoted(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
