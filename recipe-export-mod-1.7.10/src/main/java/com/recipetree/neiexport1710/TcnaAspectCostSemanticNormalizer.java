package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Canonicalizes TCNEIAdditions' invisible metadata-1 aspect-cost input proxy to the
 * pre-existing metadata-0 ItemList identity.
 *
 * <p>The four pinned handlers use {@code stackSize} as the recipe's required aspect
 * quantity and metadata 1 only to suppress ItemAspectRenderer while the recipe widget
 * draws the glyph separately through {@code UtilsFX.drawTag}. The metadata therefore
 * belongs to presentation state, not item identity. This adapter changes only a defensive
 * copy's metadata; quantity and byte-equivalent NBT are preserved.</p>
 */
final class TcnaAspectCostSemanticNormalizer {
    static final String CONTRACT =
            "thaumcraft-nei-aspect-cost-meta1-to-meta0-semantic-proxy-v1";
    static final String ZERO_COST_CONTRACT =
            "thaumcraft-nei-zero-cost-aspect-input-exclusion-v1";
    static final String ITEM_ASPECT_REGISTRY_ID = "thaumcraftneiplugin:Aspect";
    static final String ITEM_ASPECT_CLASS =
            "com.djgiannuzz.thaumcraftneiplugin.items.ItemAspect";
    static final String ITEM_ASPECT_RENDERER_CLASS =
            "com.djgiannuzz.thaumcraftneiplugin.renderer.ItemAspectRenderer";
    static final String MOD_ITEMS_CLASS =
            "com.djgiannuzz.thaumcraftneiplugin.ModItems";
    static final String ASPECT_CLASS = "thaumcraft.api.aspects.Aspect";
    static final int EXPECTED_CATALOG_ASPECT_IDENTITIES = 69;
    static final int EXPECTED_HANDLER_CATEGORIES = 4;

    private static final String[] HANDLER_CLASSES = {
        "ru.timeconqueror.tcneiadditions.nei.TCNACrucibleRecipeHandler",
        "ru.timeconqueror.tcneiadditions.nei.TCNAInfusionRecipeHandler",
        "ru.timeconqueror.tcneiadditions.nei.arcaneworkbench.ArcaneCraftingShapedHandler",
        "ru.timeconqueror.tcneiadditions.nei.arcaneworkbench.ArcaneCraftingShapelessHandler"
    };
    private static final Set<String> HANDLERS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(HANDLER_CLASSES)));

    interface AspectResolver {
        boolean isExactRegisteredAspect(String tag) throws Exception;
    }

    static final class Result {
        final ItemStack stack;
        final StackIdentity identity;
        final boolean normalized;
        final boolean excluded;

        private Result(
                ItemStack stack, StackIdentity identity, boolean normalized,
                boolean excluded) {
            this.stack = stack;
            this.identity = identity;
            this.normalized = normalized;
            this.excluded = excluded;
        }

        static Result unchanged(ItemStack stack) {
            return new Result(stack, null, false, false);
        }

        static Result normalized(ItemStack stack, StackIdentity identity) {
            return new Result(stack, identity, true, false);
        }

        static Result excluded(StackIdentity identity) {
            return new Result(null, identity, false, true);
        }
    }

    /** Exact expected/observed location ledger; exposed package-private for unit tests. */
    static final class ReferenceAudit {
        private final Map<String, String> expected = new LinkedHashMap<String, String>();
        private final Set<String> consumed = new LinkedHashSet<String>();

        void expect(String location, String fingerprint) throws ExportFailure {
            if (location == null || location.trim().isEmpty()
                    || fingerprint == null || fingerprint.trim().isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "TCNA aspect-cost preflight produced a blank reference identity");
            }
            String previous = expected.put(location, fingerprint);
            if (previous != null) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "duplicate TCNA aspect-cost preflight location " + location);
            }
        }

        void consume(String location, String fingerprint) throws ExportFailure {
            String pinned = expected.get(location);
            if (pinned == null) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "unexpected TCNA aspect-cost reference at " + location);
            }
            if (!pinned.equals(fingerprint)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "TCNA aspect-cost reference changed after preflight at " + location);
            }
            if (!consumed.add(location)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "TCNA aspect-cost reference was consumed twice at " + location);
            }
        }

        void requireExhausted(String handlerClass) throws ExportFailure {
            if (consumed.size() == expected.size()) {
                return;
            }
            TreeSet<String> missing = new TreeSet<String>(expected.keySet());
            missing.removeAll(consumed);
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TCNA aspect-cost traversal did not exhaust the preflight snapshot for "
                            + handlerClass + "; expected=" + expected.size()
                            + ", consumed=" + consumed.size() + ", firstMissing="
                            + (missing.isEmpty() ? "<none>" : missing.first()));
        }

        int size() {
            return expected.size();
        }

        List<String> sortedFingerprints() {
            List<String> values = new ArrayList<String>(expected.values());
            Collections.sort(values);
            return values;
        }
    }

    private final Item pinnedItem;
    private final AspectResolver aspectResolver;
    private final Map<String, Integer> initialKeyCardinality;
    private final Set<String> initialAspectKeys;
    private final Set<String> preflightedHandlers = new LinkedHashSet<String>();
    private final Set<String> completedHandlers = new LinkedHashSet<String>();
    private final Set<String> normalizedKeys = new LinkedHashSet<String>();
    private final List<String> expectedFingerprints = new ArrayList<String>();
    private final List<String> observedFingerprints = new ArrayList<String>();
    private final Set<String> expectedZeroCostLocations = new LinkedHashSet<String>();

    private String currentHandlerClass;
    private ReferenceAudit currentAudit;
    private int expectedReferences;
    private int normalizedReferences;
    private int excludedZeroCostReferences;

    private TcnaAspectCostSemanticNormalizer(
            Item pinnedItem,
            AspectResolver aspectResolver,
            Map<String, Integer> initialKeyCardinality,
            Set<String> initialAspectKeys) {
        this.pinnedItem = pinnedItem;
        this.aspectResolver = aspectResolver;
        this.initialKeyCardinality = initialKeyCardinality;
        this.initialAspectKeys = initialAspectKeys;
    }

    static TcnaAspectCostSemanticNormalizer create(List<StackIdentity> initialItems)
            throws ExportFailure {
        try {
            final Item item = GameRegistry.findItem("thaumcraftneiplugin", "Aspect");
            if (item == null || !ITEM_ASPECT_CLASS.equals(item.getClass().getName())) {
                throw new ExportFailure("ITEM_IDENTITY",
                        "pinned ItemAspect registry binding is absent or has the wrong class");
            }
            GameRegistry.UniqueIdentifier identifier =
                    GameRegistry.findUniqueIdentifierFor(item);
            String registryId = identifier == null ? null
                    : identifier.modId + ":" + identifier.name;
            if (!ITEM_ASPECT_REGISTRY_ID.equals(registryId)) {
                throw new ExportFailure("ITEM_IDENTITY",
                        "pinned ItemAspect registry ID drifted; got " + registryId);
            }
            if (!item.getHasSubtypes() || item.getMaxDamage() != 0
                    || item.getItemStackLimit() != 64) {
                throw new ExportFailure("ITEM_IDENTITY",
                        "pinned ItemAspect item semantics drifted; subtypes="
                                + item.getHasSubtypes() + ", maxDamage="
                                + item.getMaxDamage() + ", stackLimit="
                                + item.getItemStackLimit());
            }

            ClassLoader loader = item.getClass().getClassLoader();
            Class<?> modItemsClass = Class.forName(MOD_ITEMS_CLASS, false, loader);
            Field itemAspectField = modItemsClass.getField("itemAspect");
            if (!Modifier.isPublic(itemAspectField.getModifiers())
                    || !Modifier.isStatic(itemAspectField.getModifiers())
                    || itemAspectField.getType() != Item.class
                    || itemAspectField.get(null) != item) {
                throw new ExportFailure("ITEM_IDENTITY",
                        "pinned ModItems.itemAspect binding drifted");
            }

            Class<?> rendererClass = Class.forName(
                    ITEM_ASPECT_RENDERER_CLASS, false, loader);
            IItemRenderer metadataZeroRenderer = MinecraftForgeClient.getItemRenderer(
                    new ItemStack(item, 1, 0), IItemRenderer.ItemRenderType.INVENTORY);
            IItemRenderer metadataOneRenderer = MinecraftForgeClient.getItemRenderer(
                    new ItemStack(item, 1, 1), IItemRenderer.ItemRenderType.INVENTORY);
            if (metadataZeroRenderer == null
                    || metadataZeroRenderer.getClass() != rendererClass
                    || metadataOneRenderer != metadataZeroRenderer
                    || !metadataZeroRenderer.handleRenderType(
                            new ItemStack(item, 1, 0),
                            IItemRenderer.ItemRenderType.INVENTORY)
                    || !metadataZeroRenderer.handleRenderType(
                            new ItemStack(item, 1, 1),
                            IItemRenderer.ItemRenderType.INVENTORY)) {
                throw new ExportFailure("ITEM_ICON_RENDER",
                        "pinned ItemAspect inventory-renderer binding drifted");
            }

            final Class<?> aspectClass = Class.forName(ASPECT_CLASS, false, loader);
            final Method getAspect = aspectClass.getMethod("getAspect", String.class);
            final Method getTag = aspectClass.getMethod("getTag");
            final Field aspectsField = aspectClass.getField("aspects");
            if (!Modifier.isStatic(getAspect.getModifiers())
                    || getAspect.getReturnType() != aspectClass
                    || Modifier.isStatic(getTag.getModifiers())
                    || getTag.getReturnType() != String.class
                    || !Modifier.isPublic(aspectsField.getModifiers())
                    || !Modifier.isStatic(aspectsField.getModifiers())
                    || !Map.class.isAssignableFrom(aspectsField.getType())) {
                throw new ExportFailure("ITEM_IDENTITY",
                        "pinned Thaumcraft Aspect registry reflection contract drifted");
            }
            final Object rawRegistry = aspectsField.get(null);
            if (!(rawRegistry instanceof Map)
                    || ((Map<?, ?>) rawRegistry).size()
                    != EXPECTED_CATALOG_ASPECT_IDENTITIES) {
                throw new ExportFailure("ITEM_IDENTITY",
                        "pinned Thaumcraft Aspect registry cardinality drifted; got "
                                + (rawRegistry instanceof Map
                                ? ((Map<?, ?>) rawRegistry).size() : "non-map"));
            }
            AspectResolver resolver = new AspectResolver() {
                @Override
                public boolean isExactRegisteredAspect(String tag) throws Exception {
                    Object aspect = getAspect.invoke(null, tag);
                    String reportedTag = aspect == null
                            ? null : (String) getTag.invoke(aspect);
                    return isCanonicalAspectRegistryBinding(
                            aspectClass, (Map<?, ?>) rawRegistry, tag, aspect,
                            reportedTag);
                }
            };

            Map<String, Integer> cardinality = new HashMap<String, Integer>();
            Set<String> aspectKeys = new LinkedHashSet<String>();
            int aspectEntries = 0;
            for (StackIdentity identityValue : initialItems) {
                Integer count = cardinality.get(identityValue.key);
                cardinality.put(identityValue.key, count == null ? 1 : count + 1);
                ItemStack stack = identityValue.stack;
                if (stack != null && stack.getItem() != null
                        && ITEM_ASPECT_CLASS.equals(stack.getItem().getClass().getName())) {
                    aspectEntries++;
                    if (stack.getItem() != item || stack.getItemDamage() != 0
                            || stack.stackSize != 1) {
                        throw new ExportFailure("ITEM_IDENTITY",
                                "global ItemList leaked a noncanonical ItemAspect entry: "
                                        + StackIdentity.describe(stack));
                    }
                    validateExactProxyPayload(stack, item, registryId, 0, resolver);
                    if (!aspectKeys.add(identityValue.key)) {
                        throw new ExportFailure("ITEM_IDENTITY",
                                "duplicate metadata-0 ItemAspect catalog identity "
                                        + identityValue.key);
                    }
                }
            }
            if (aspectEntries != EXPECTED_CATALOG_ASPECT_IDENTITIES
                    || aspectKeys.size() != EXPECTED_CATALOG_ASPECT_IDENTITIES) {
                throw new ExportFailure("ITEM_IDENTITY",
                        "metadata-0 ItemAspect catalog cardinality drifted; entries="
                                + aspectEntries + ", unique=" + aspectKeys.size());
            }
            return new TcnaAspectCostSemanticNormalizer(
                    item, resolver,
                    Collections.unmodifiableMap(cardinality),
                    Collections.unmodifiableSet(aspectKeys));
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_IDENTITY",
                    "TCNA aspect-cost runtime preflight failed", error);
        }
    }

    void beginCategory(HandlerCategoryPlan plan, ICraftingHandler handler)
            throws ExportFailure {
        if (currentHandlerClass != null || currentAudit != null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TCNA aspect-cost category preflight overlapped another category");
        }
        String runtimeClass = handler.getClass().getName();
        if (!isPinnedHandler(runtimeClass)) {
            return;
        }
        if (!runtimeClass.equals(plan.prototype.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "TCNA aspect-cost handler identity drifted; runtime=" + runtimeClass
                            + ", rawHandlerId=" + plan.handlerId + ", prototype="
                            + plan.prototype.getClass().getName());
        }
        if (!preflightedHandlers.add(runtimeClass)) {
            throw new ExportFailure("HANDLER_DUPLICATE",
                    "TCNA aspect-cost handler category repeated: " + runtimeClass);
        }

        currentHandlerClass = runtimeClass;
        currentAudit = new ReferenceAudit();
        try {
            for (int sourceIndex = 0; sourceIndex < handler.numRecipes(); sourceIndex++) {
                List<PositionedStack> ingredients = handler.getIngredientStacks(sourceIndex);
                PositionedStack result = handler.getResultStack(sourceIndex);
                List<PositionedStack> others = handler.getOtherStacks(sourceIndex);
                if (ingredients == null || others == null) {
                    throw new ExportFailure("RECIPE_SEMANTICS", runtimeClass + " #"
                            + sourceIndex + " returned null input/other stacks during TCNA "
                            + "aspect-cost preflight");
                }
                inspectPreflightStacks(
                        ingredients, "input", sourceIndex, currentAudit, runtimeClass);
                if (result != null) {
                    inspectPreflightStacks(
                            Collections.singletonList(result), "result", sourceIndex,
                            currentAudit, runtimeClass);
                }
                inspectPreflightStacks(
                        others, result == null ? "output" : "catalyst", sourceIndex,
                        currentAudit, runtimeClass);
            }
            if (currentAudit.size() <= 0) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "pinned TCNA aspect-cost handler exposed no metadata-1 input proxies: "
                                + runtimeClass);
            }
            expectedReferences = Math.addExact(expectedReferences, currentAudit.size());
            expectedFingerprints.addAll(currentAudit.sortedFingerprints());
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Preflighted exact TCNA aspect-cost semantic policy {} "
                            + "for handler={} references={}",
                    CONTRACT, runtimeClass, currentAudit.size());
        } catch (ExportFailure failure) {
            resetCurrentCategory();
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            resetCurrentCategory();
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TCNA aspect-cost category preflight failed for " + runtimeClass, error);
        }
    }

    Result normalize(ItemStack source, String handlerClass, String role, int sourceIndex,
                     int slotIndex, int alternativeIndex) throws ExportFailure {
        if (source == null || source.getItem() == null) {
            return Result.unchanged(source);
        }
        Item actualItem = source.getItem();
        boolean exactClass = ITEM_ASPECT_CLASS.equals(actualItem.getClass().getName());
        if (!exactClass && actualItem != pinnedItem) {
            return Result.unchanged(source);
        }
        if (!exactClass || actualItem != pinnedItem) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "ItemAspect runtime instance/class drifted in recipe graph: "
                            + StackIdentity.describe(source));
        }
        int metadata = source.getItemDamage();
        if (metadata == 0) {
            return Result.unchanged(source);
        }
        if (metadata != 1) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "unsupported ItemAspect metadata in recipe graph: "
                            + StackIdentity.describe(source));
        }
        if (!"input".equals(role) || !isPinnedHandler(handlerClass)
                || currentHandlerClass == null
                || !currentHandlerClass.equals(handlerClass)) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "metadata-1 ItemAspect presentation proxy escaped its four pinned "
                            + "TCNA input scopes; handler="
                            + (handlerClass == null ? "<unscoped>" : handlerClass)
                            + ", role=" + role + ", sourceIndex=" + sourceIndex
                            + ", slotIndex=" + slotIndex + ", alternativeIndex="
                            + alternativeIndex);
        }

        String location = location(
                handlerClass, sourceIndex, role, slotIndex, alternativeIndex);
        final ItemStack normalized;
        try {
            normalized = source.stackSize == 0
                    ? normalizeZeroCostProxy(
                            source, pinnedItem, ITEM_ASPECT_REGISTRY_ID, aspectResolver)
                    : normalizeExactProxy(
                            source, pinnedItem, ITEM_ASPECT_REGISTRY_ID, aspectResolver);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_IDENTITY",
                    "could not normalize TCNA aspect-cost input at " + location, error);
        }
        StackIdentity identity = StackIdentity.of(normalized);
        Integer cardinality = initialKeyCardinality.get(identity.key);
        if (cardinality == null || cardinality != 1
                || !initialAspectKeys.contains(identity.key)) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "normalized TCNA aspect-cost identity has no unique pre-existing "
                            + "metadata-0 ItemList sibling: " + identity.key);
        }
        String fingerprint = fingerprint(location, source, identity);
        currentAudit.consume(location, fingerprint);
        if (source.stackSize == 0) {
            if (!expectedZeroCostLocations.contains(location)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "unrecognized zero-cost TCNA aspect input at " + location);
            }
            excludedZeroCostReferences = Math.addExact(
                    excludedZeroCostReferences, 1);
            observedFingerprints.add(fingerprint);
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Explicitly excluded exact zero-cost TCNA aspect "
                            + "input; contract={} location={} targetKey={}",
                    ZERO_COST_CONTRACT, location, identity.key);
            return Result.excluded(identity);
        }
        normalizedReferences = Math.addExact(normalizedReferences, 1);
        normalizedKeys.add(identity.key);
        observedFingerprints.add(fingerprint);
        return Result.normalized(normalized, identity);
    }

    void endCategory() throws ExportFailure {
        if (currentHandlerClass == null) {
            return;
        }
        String handlerClass = currentHandlerClass;
        ReferenceAudit audit = currentAudit;
        try {
            audit.requireExhausted(handlerClass);
            if (!completedHandlers.add(handlerClass)) {
                throw new ExportFailure("HANDLER_DUPLICATE",
                        "TCNA aspect-cost handler completed twice: " + handlerClass);
            }
        } finally {
            resetCurrentCategory();
        }
    }

    void verifyComplete() throws ExportFailure {
        if (currentHandlerClass != null || currentAudit != null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TCNA aspect-cost verification ran with an open category");
        }
        if (!preflightedHandlers.equals(HANDLERS)
                || !completedHandlers.equals(HANDLERS)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "TCNA aspect-cost handler coverage drifted; expected="
                            + new TreeSet<String>(HANDLERS) + ", preflighted="
                            + new TreeSet<String>(preflightedHandlers) + ", completed="
                            + new TreeSet<String>(completedHandlers));
        }
        if (expectedReferences <= 0
                || normalizedReferences + excludedZeroCostReferences != expectedReferences
                || excludedZeroCostReferences <= 0
                || normalizedKeys.isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TCNA aspect-cost normalization cardinality drifted; expected="
                            + expectedReferences + ", normalized=" + normalizedReferences
                            + ", excludedZeroCost=" + excludedZeroCostReferences
                            + ", distinctKeys=" + normalizedKeys.size());
        }
        List<String> expected = new ArrayList<String>(expectedFingerprints);
        List<String> observed = new ArrayList<String>(observedFingerprints);
        Collections.sort(expected);
        Collections.sort(observed);
        String expectedFingerprint = Naming.sha256(joinLines(expected));
        String observedFingerprint = Naming.sha256(joinLines(observed));
        if (!expectedFingerprint.equals(observedFingerprint)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "TCNA aspect-cost reference fingerprint changed after preflight; expected="
                            + expectedFingerprint + ", observed=" + observedFingerprint);
        }
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact TCNA aspect-cost semantic policy {}; "
                        + "zero-cost exclusion policy {}: normalizedReferences={}, "
                        + "excludedZeroCostReferences={}, distinctKeys={}, handlers={}, "
                        + "fingerprint={}",
                CONTRACT, ZERO_COST_CONTRACT, normalizedReferences,
                excludedZeroCostReferences, normalizedKeys.size(),
                completedHandlers.size(), observedFingerprint);
    }

    int normalizedReferences() {
        return normalizedReferences;
    }

    int normalizedDistinctKeys() {
        return normalizedKeys.size();
    }

    int completedHandlerCategories() {
        return completedHandlers.size();
    }

    int excludedZeroCostReferences() {
        return excludedZeroCostReferences;
    }

    static boolean isPinnedHandler(String handlerClass) {
        return HANDLERS.contains(handlerClass);
    }

    static boolean isCanonicalAspectRegistryBinding(
            Class<?> aspectBaseClass, Map<?, ?> registry, String tag,
            Object aspect, String reportedTag) {
        return aspectBaseClass != null
                && registry != null
                && tag != null
                && aspect != null
                && aspectBaseClass.isInstance(aspect)
                && registry.get(tag) == aspect
                && tag.equals(reportedTag);
    }

    static ItemStack normalizeExactProxy(
            ItemStack source, Item pinnedItem, String registryId,
            AspectResolver resolver) throws Exception {
        validateExactProxyPayload(source, pinnedItem, registryId, 1, resolver);
        int originalAmount = source.stackSize;
        int originalMetadata = source.getItemDamage();
        NBTTagCompound originalNbt = source.getTagCompound();
        String canonicalNbt = NbtCanonicalizer.canonical(originalNbt);

        ItemStack normalized = source.copy();
        normalized.setItemDamage(0);
        if (source.stackSize != originalAmount || source.getItemDamage() != originalMetadata
                || source.getTagCompound() != originalNbt
                || !canonicalNbt.equals(NbtCanonicalizer.canonical(originalNbt))) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TCNA aspect-cost normalization mutated its source stack");
        }
        if (normalized == source || normalized.getItem() != pinnedItem
                || normalized.stackSize != originalAmount
                || normalized.getItemDamage() != 0
                || normalized.getTagCompound() == null
                || normalized.getTagCompound() == originalNbt
                || !canonicalNbt.equals(
                        NbtCanonicalizer.canonical(normalized.getTagCompound()))) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TCNA aspect-cost normalization did not preserve quantity/NBT exactly");
        }
        return normalized;
    }

    static ItemStack normalizeZeroCostProxy(
            ItemStack source, Item pinnedItem, String registryId,
            AspectResolver resolver) throws Exception {
        if (source == null || source.stackSize != 0) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "zero-cost TCNA aspect proxy requires stackSize=0; got "
                            + (source == null ? "<null>" : source.stackSize));
        }
        validateExactProxyPayload(
                source, pinnedItem, registryId, 1, 0, resolver);
        ItemStack normalized = source.copy();
        normalized.stackSize = 1;
        normalized.setItemDamage(0);
        validateExactProxyPayload(
                normalized, pinnedItem, registryId, 0, 1, resolver);
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static String validateExactProxyPayload(
            ItemStack stack, Item pinnedItem, String registryId, int expectedMetadata,
            AspectResolver resolver) throws Exception {
        return validateExactProxyPayload(
                stack, pinnedItem, registryId, expectedMetadata, 1, resolver);
    }

    @SuppressWarnings("unchecked")
    private static String validateExactProxyPayload(
            ItemStack stack, Item pinnedItem, String registryId, int expectedMetadata,
            int minimumStackSize, AspectResolver resolver) throws Exception {
        if (stack == null || stack.getItem() == null || pinnedItem == null
                || stack.getItem() != pinnedItem) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TCNA aspect-cost proxy does not use the pinned ItemAspect instance");
        }
        if (!ITEM_ASPECT_REGISTRY_ID.equals(registryId)) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TCNA aspect-cost proxy registry ID drifted; got " + registryId);
        }
        if (stack.getItemDamage() != expectedMetadata
                || stack.stackSize < minimumStackSize) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TCNA aspect-cost proxy requires metadata=" + expectedMetadata
                            + " and stackSize >= " + minimumStackSize + "; got metadata="
                            + stack.getItemDamage() + ", stackSize=" + stack.stackSize);
        }
        NBTTagCompound root = stack.getTagCompound();
        if (root == null || !root.hasKey("Aspects", 9)) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TCNA aspect-cost proxy requires an Aspects list");
        }
        Set<String> rootKeys = (Set<String>) root.func_150296_c();
        if (rootKeys.size() != 1 || !rootKeys.contains("Aspects")) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TCNA aspect-cost proxy root NBT keys drifted: "
                            + new TreeSet<String>(rootKeys));
        }
        NBTTagList aspects = root.getTagList("Aspects", 10);
        if (aspects.tagCount() != 1) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TCNA aspect-cost proxy requires exactly one aspect; got "
                            + aspects.tagCount());
        }
        NBTTagCompound entry = aspects.getCompoundTagAt(0);
        Set<String> entryKeys = (Set<String>) entry.func_150296_c();
        if (entryKeys.size() != 2 || !entryKeys.contains("amount")
                || !entryKeys.contains("key")
                || !entry.hasKey("amount", 3) || !entry.hasKey("key", 8)) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TCNA aspect-cost proxy entry NBT keys/types drifted: "
                            + new TreeSet<String>(entryKeys));
        }
        if (entry.getInteger("amount") != 2) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TCNA aspect-cost proxy payload amount must remain the owner sentinel 2; got "
                            + entry.getInteger("amount"));
        }
        String tag = entry.getString("key");
        if (tag == null || tag.trim().isEmpty() || !tag.equals(tag.trim())
                || resolver == null || !resolver.isExactRegisteredAspect(tag)) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "TCNA aspect-cost proxy references an unknown/noncanonical aspect tag "
                            + String.valueOf(tag));
        }
        return tag;
    }

    private void inspectPreflightStacks(
            List<PositionedStack> stacks, String role, int sourceIndex,
            ReferenceAudit audit, String handlerClass) throws Exception {
        for (int slotIndex = 0; slotIndex < stacks.size(); slotIndex++) {
            PositionedStack positioned = stacks.get(slotIndex);
            if (positioned == null) {
                throw new ExportFailure("RECIPE_SEMANTICS", handlerClass + " #"
                        + sourceIndex + " has a null " + role + " stack during TCNA preflight");
            }
            if (positioned.items == null || positioned.items.length == 0) {
                positioned.generatePermutations();
            }
            if (positioned.items == null || positioned.items.length == 0) {
                throw new ExportFailure("RECIPE_SEMANTICS", handlerClass + " #"
                        + sourceIndex + " has no " + role
                        + " alternatives during TCNA preflight");
            }
            for (int alternativeIndex = 0;
                 alternativeIndex < positioned.items.length; alternativeIndex++) {
                ItemStack stack = positioned.items[alternativeIndex];
                if (stack == null || stack.getItem() == null) {
                    throw new ExportFailure("RECIPE_SEMANTICS", handlerClass + " #"
                            + sourceIndex + " has a null " + role + " alternative #"
                            + alternativeIndex + " during TCNA preflight");
                }
                if (stack.getItem() != pinnedItem
                        && !ITEM_ASPECT_CLASS.equals(stack.getItem().getClass().getName())) {
                    continue;
                }
                if (stack.getItem() != pinnedItem
                        || !ITEM_ASPECT_CLASS.equals(stack.getItem().getClass().getName())) {
                    throw new ExportFailure("ITEM_IDENTITY",
                            "ItemAspect instance/class drifted during TCNA preflight");
                }
                int metadata = stack.getItemDamage();
                if (metadata == 0) {
                    continue;
                }
                if (metadata != 1 || !"input".equals(role)) {
                    throw new ExportFailure("ITEM_IDENTITY",
                            "ItemAspect presentation metadata escaped TCNA input semantics; "
                                    + "handler=" + handlerClass + ", role=" + role
                                    + ", sourceIndex=" + sourceIndex + ", slotIndex="
                                    + slotIndex + ", alternativeIndex=" + alternativeIndex
                                    + ", metadata=" + metadata);
                }
                ItemStack normalized = stack.stackSize == 0
                        ? normalizeZeroCostProxy(
                                stack, pinnedItem, ITEM_ASPECT_REGISTRY_ID,
                                aspectResolver)
                        : normalizeExactProxy(
                                stack, pinnedItem, ITEM_ASPECT_REGISTRY_ID,
                                aspectResolver);
                StackIdentity identity = StackIdentity.of(normalized);
                Integer cardinality = initialKeyCardinality.get(identity.key);
                if (cardinality == null || cardinality != 1
                        || !initialAspectKeys.contains(identity.key)) {
                    throw new ExportFailure("ITEM_IDENTITY",
                            "TCNA preflight found no unique metadata-0 catalog sibling for "
                                    + identity.key);
                }
                String location = location(
                        handlerClass, sourceIndex, role, slotIndex, alternativeIndex);
                if (stack.stackSize == 0
                        && !expectedZeroCostLocations.add(location)) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "duplicate zero-cost TCNA aspect input at " + location);
                }
                audit.expect(location, fingerprint(location, stack, identity));
            }
        }
    }

    private static String location(
            String handlerClass, int sourceIndex, String role,
            int slotIndex, int alternativeIndex) {
        return handlerClass + "#" + sourceIndex + "/" + role + "/"
                + slotIndex + "/" + alternativeIndex;
    }

    private static String fingerprint(
            String location, ItemStack source, StackIdentity normalizedIdentity) {
        return location + "\u0000amount=" + source.stackSize
                + "\u0000sourceMeta=" + source.getItemDamage()
                + "\u0000nbt=" + NbtCanonicalizer.canonical(source.getTagCompound())
                + "\u0000target=" + normalizedIdentity.key;
    }

    private static String joinLines(List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            joined.append(value.length()).append(':').append(value).append('\n');
        }
        return joined.toString();
    }

    private void resetCurrentCategory() {
        currentHandlerClass = null;
        currentAudit = null;
    }
}
