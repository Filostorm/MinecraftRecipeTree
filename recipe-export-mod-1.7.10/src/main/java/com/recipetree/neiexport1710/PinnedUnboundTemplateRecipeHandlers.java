package com.recipetree.neiexport1710;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import codechicken.nei.PositionedStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Exact GTNH 2.8.4 exclusion for NEI categories whose loaded rows are unbound templates rather
 * than exportable concrete recipes.
 *
 * <p>Extra Utilities stores four {@code RecipeMicroBlocks} material families in
 * {@link CraftingManager}. Their material is not present in the source recipe. The owner NEI
 * handler binds a rotating material only from its render permutation, after it has loaded the
 * cached row. Reading those cached stacks as ordinary recipe graph data would therefore publish
 * unconfigured microblock carriers. Constructing the fresh handler invokes that owner-controlled
 * render permutation internally. This policy validates the exact selected material and then
 * normalizes only the proven wall-clock offset and rotating material binding before comparing the
 * loaded rows with a source-relative semantic projection. Every other field remains fail-closed.
 * The policy also proves that CraftingManager and the registered prototype remain byte-for-byte
 * equivalent afterward.</p>
 */
final class PinnedUnboundTemplateRecipeHandlers {
    static final String HANDLER_CLASS = "com.rwtema.extrautils.nei.MicroBlocksHandler";
    static final String HANDLER_ID = HANDLER_CLASS;
    static final String CATEGORY_ID = "gtnh:16bf5c3541c3232fb78604ee77484702";
    static final String OVERLAY = "crafting";
    static final String OPERATION = "xu_microblocks_crafting";
    static final String ACTION = "excluded-unbound-template-category";
    static final String CONTRACT =
            "unbound-template:gtnh-2.8.4-extrautilities-microblocks-material-v3";

    private static final String RECIPE_CLASS =
            "com.rwtema.extrautils.multipart.microblock.RecipeMicroBlocks";
    private static final String CACHED_RECIPE_CLASS =
            HANDLER_CLASS + "$MicroblockCachedRecipe";
    private static final String MICROBLOCK_POSITIONED_STACK_CLASS =
            CACHED_RECIPE_CLASS + "$MicroblockPositionedStack";
    private static final String CACHED_RECIPE_BASE_CLASS =
            "codechicken.nei.recipe.TemplateRecipeHandler$CachedRecipe";
    private static final String VOLATILE_CACHE_OFFSET_FIELD = "offset";
    private static final String RECIPE_OUTPUT_ITEM_ID = "ExtraUtilities:microblocks";
    private static final String OWNER_MICROBLOCK_ITEM_ID = "ForgeMicroblock:microblock";
    private static final String OWNER_MICROBLOCK_ACCESS_CLASS =
            "com.rwtema.extrautils.multipart.FMPBase";
    private static final String OWNER_MICROBLOCK_ACCESS_METHOD = "getMicroBlockItemId";
    private static final String LEGACY_GENERIC_ADAPTER_CONTRACT =
            "generic:getRecipeHandler-zero-arguments-v1";
    private static final int EXPECTED_SOURCE_REGISTRY_COUNT = 56609;
    private static final int EXPECTED_SOURCE_COUNT = 4;
    private static final int EXPECTED_PROTOTYPE_COUNT = 0;
    private static final int EXPECTED_LOADED_COUNT = 4;
    private static final List<Integer> EXPECTED_PLACEHOLDER_COUNTS =
            Collections.unmodifiableList(Arrays.asList(7, 7, 8, 8));

    // The source and empty-prototype domains deliberately retain the discovery contract that
    // produced the reviewed 1.0.70 evidence. The loaded-cache domain is v3: v2 excluded
    // CachedRecipe.offset but incorrectly conflated XU's recipe-output item with the distinct
    // ForgeMicroblock carrier that Integer placeholders resolve through FMPBase.
    private static final String SOURCE_FINGERPRINT_DOMAIN =
            PinnedEmptyRecipeHandlers.CONTRACT + "/source-multiset-v1/raw/"
                    + HANDLER_CLASS + "/"
                    + PinnedEmptyRecipeHandlers.SourceKind.EXTRAUTILITIES_MICROBLOCKS.name();
    private static final String PROTOTYPE_FINGERPRINT_DOMAIN =
            PinnedEmptyRecipeHandlers.CONTRACT + "/nei-cache-multiset-v1/prototype/"
                    + HANDLER_CLASS;
    private static final String LOADED_FINGERPRINT_DOMAIN =
            CONTRACT + "/semantic-cache-multiset-v3/" + HANDLER_CLASS;

    static final String EXPECTED_SOURCE_FINGERPRINT =
            "9bb53158234fe43fbe5abb223968e65a7c709f4d9b70db032bd5d421b7a0cd6c";
    static final String EXPECTED_PROTOTYPE_FINGERPRINT =
            "91302b62ad13d2fca735dbba4c4aa657a7ae4c0c3488a1f011765c702b4d7df0";
    private PinnedUnboundTemplateRecipeHandlers() {
    }

    static boolean supports(String handlerId) {
        return HANDLER_ID.equals(handlerId);
    }

    static void validatePrototype(String handlerId, ICraftingHandler prototype)
            throws ExportFailure {
        if (!supports(handlerId) || prototype == null
                || !HANDLER_CLASS.equals(prototype.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "unbound-template policy received the wrong handler binding: id="
                            + handlerId + ", class="
                            + (prototype == null ? "null" : prototype.getClass().getName()));
        }
        try {
            requireEquals("handler ID", HANDLER_ID, prototype.getHandlerId());
            requireEquals("overlay identifier", OVERLAY, prototype.getOverlayIdentifier());
            requireEquals("legacy generic category ID", CATEGORY_ID, derivedCategoryId());

            CacheObservation prototypeCache = inspectEmptyPrototypeCache(
                    prototype, PROTOTYPE_FINGERPRINT_DOMAIN);
            requireEquals("registered prototype cache count", EXPECTED_PROTOTYPE_COUNT,
                    prototypeCache.count);
            requireEquals("registered prototype cache fingerprint",
                    EXPECTED_PROTOTYPE_FINGERPRINT, prototypeCache.fingerprint);

            SourceObservation sourceBefore = inspectSource(prototype.getClass().getClassLoader());
            requireSourcePromotion(sourceBefore);
            OwnerStaticState staticsBefore = inspectOwnerStatics(
                    prototype.getClass(), sourceBefore.rows, false);

            // This is the owner's ordinary complete-category construction. XU calls
            // computeVisuals() internally, which selects one time-indexed micro-material even
            // though this policy invokes no public ingredient/result accessor or renderer. The
            // v2 projection below proves and reverses only that exact owner mutation.
            long loadStartedAtMillis = System.currentTimeMillis();
            ICraftingHandler loaded = prototype.getRecipeHandler(OPERATION);
            long loadFinishedAtMillis = System.currentTimeMillis();
            if (loadFinishedAtMillis < loadStartedAtMillis) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        HANDLER_ID + " wall clock moved backward while constructing the "
                                + "owner cache; start=" + loadStartedAtMillis
                                + ", finish=" + loadFinishedAtMillis);
            }
            if (loaded == null || loaded.getClass() != prototype.getClass()) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        HANDLER_ID + " returned "
                                + (loaded == null ? "null" : loaded.getClass().getName())
                                + " for exact operation " + OPERATION);
            }
            requireEquals("loaded handler ID", HANDLER_ID, loaded.getHandlerId());
            requireEquals("loaded overlay identifier", OVERLAY,
                    loaded.getOverlayIdentifier());
            CacheObservation loadedCache = inspectLoadedCache(
                    loaded, sourceBefore.rows, prototype.getClass().getClassLoader(),
                    staticsBefore, loadStartedAtMillis, loadFinishedAtMillis);
            requireEquals("loaded cache count", EXPECTED_LOADED_COUNT, loadedCache.count);

            CacheObservation prototypeAfter = inspectEmptyPrototypeCache(
                    prototype, PROTOTYPE_FINGERPRINT_DOMAIN);
            requireEquals("post-load registered prototype cache count",
                    EXPECTED_PROTOTYPE_COUNT, prototypeAfter.count);
            requireEquals("post-load registered prototype cache fingerprint",
                    EXPECTED_PROTOTYPE_FINGERPRINT, prototypeAfter.fingerprint);
            if (prototypeCache.count != prototypeAfter.count
                    || !prototypeCache.fingerprint.equals(prototypeAfter.fingerprint)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        HANDLER_ID + " registered prototype changed while the owner constructed "
                                + "its fresh unbound-template cache");
            }

            SourceObservation sourceAfter = inspectSource(prototype.getClass().getClassLoader());
            requireSourcePromotion(sourceAfter);
            if (!sourceBefore.fingerprint.equals(sourceAfter.fingerprint)
                    || !sourceBefore.placeholderCounts.equals(sourceAfter.placeholderCounts)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        HANDLER_ID + " source recipes changed while the read-only exclusion "
                                + "policy observed the fresh owner cache");
            }

            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Excluding exact unbound material-template category "
                            + "class={} categoryId={} operation={} sourceRegistryCount={} "
                            + "sourceCount={} sourceFingerprint={} placeholderCounts={} "
                            + "prototypeCount={} prototypeFingerprint={} loadedCount={} "
                            + "loadedFingerprint={} sourceProjectionFingerprint={} "
                            + "excludedVolatileField={} offsetRange=[{},{}] cycleTicks={} "
                            + "loadClockWindow=[{},{}] "
                            + "selectedMaterialIndex={} selectedMaterial={} "
                            + "normalizedRowFingerprints={} "
                            + "recipesCacheInitializedBefore={} materialsCacheInitializedBefore={} "
                            + "contract={}",
                    HANDLER_CLASS, CATEGORY_ID, OPERATION, sourceAfter.registryCount,
                    sourceAfter.rows.size(), sourceAfter.fingerprint,
                    sourceAfter.placeholderCounts, prototypeCache.count,
                    prototypeCache.fingerprint, loadedCache.count,
                    loadedCache.fingerprint, loadedCache.expectedFingerprint,
                    CACHED_RECIPE_BASE_CLASS + "." + VOLATILE_CACHE_OFFSET_FIELD,
                    loadedCache.minimumOffset, loadedCache.maximumOffset,
                    loadedCache.cycleTicks,
                    loadedCache.loadStartedAtMillis, loadedCache.loadFinishedAtMillis,
                    loadedCache.selectedMaterialIndex,
                    loadedCache.selectedMaterial,
                    loadedCache.rowFingerprints,
                    staticsBefore.recipesInitialized,
                    staticsBefore.materialsInitialized,
                    CONTRACT);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    HANDLER_ID + " exact unbound-template policy validation failed", error);
        }
    }

    private static SourceObservation inspectSource(ClassLoader loader) throws Exception {
        List<?> liveRegistry = CraftingManager.getInstance().getRecipeList();
        if (liveRegistry == null) {
            throw new IllegalStateException("CraftingManager recipe list is null");
        }
        List<?> registry = new ArrayList<Object>(liveRegistry);
        Class<?> recipeClass = Class.forName(RECIPE_CLASS, false, loader);
        PinnedEmptyRecipeHandlers.FilteredRows partition =
                PinnedEmptyRecipeHandlers.partitionAssignableRows(
                        registry, recipeClass, null);
        List<Object> rows = partition.rawRows;
        String fingerprint = PinnedEmptyRecipeHandlers.fingerprintObjects(
                SOURCE_FINGERPRINT_DOMAIN, rows,
                PinnedEmptyRecipeHandlers.SourceKind.EXTRAUTILITIES_MICROBLOCKS);
        List<Integer> placeholderCounts = inspectMateriallessness(recipeClass, rows);
        return new SourceObservation(
                registry.size(), rows, fingerprint, placeholderCounts);
    }

    private static List<Integer> inspectMateriallessness(
            Class<?> recipeClass, List<Object> rows) throws Exception {
        Field width = exactPublicFinalField(recipeClass, "recipeWidth", int.class);
        Field height = exactPublicFinalField(recipeClass, "recipeHeight", int.class);
        Field items = exactPublicFinalField(recipeClass, "recipeItems", Object[].class);
        Field outputItem = exactPublicFinalField(
                recipeClass, "recipeOutputItemID", Item.class);
        Field output = recipeClass.getDeclaredField("recipeOutput");
        if (output.getType() != ItemStack.class || Modifier.isStatic(output.getModifiers())) {
            throw new IllegalStateException(RECIPE_CLASS
                    + ".recipeOutput field contract drifted");
        }
        output.setAccessible(true);

        List<Integer> placeholderCounts = new ArrayList<Integer>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            Object row = rows.get(index);
            if (row == null || row.getClass() != recipeClass) {
                throw new IllegalStateException("source row #" + index
                        + " is not the exact " + RECIPE_CLASS + " class");
            }
            int recipeWidth = width.getInt(row);
            int recipeHeight = height.getInt(row);
            Object[] recipeItems = (Object[]) items.get(row);
            ItemStack recipeOutput = (ItemStack) output.get(row);
            Item declaredOutputItem = (Item) outputItem.get(row);
            if (recipeWidth != 3 || recipeHeight != 3 || recipeItems == null
                    || recipeItems.length != recipeWidth * recipeHeight) {
                throw new IllegalStateException("source row #" + index
                        + " lost its exact 3x3 template shape");
            }
            if (recipeOutput == null || recipeOutput.getItem() == null
                    || declaredOutputItem != recipeOutput.getItem()) {
                throw new IllegalStateException("source row #" + index
                        + " lost its exact microblock output binding");
            }
            String registryId = String.valueOf(
                    Item.itemRegistry.getNameForObject(recipeOutput.getItem()));
            if (!RECIPE_OUTPUT_ITEM_ID.equals(registryId)) {
                throw new IllegalStateException("source row #" + index
                        + " output registry ID drifted to " + registryId);
            }
            boolean materialBound = recipeOutput.hasTagCompound()
                    && recipeOutput.getTagCompound() != null
                    && !recipeOutput.getTagCompound().getString("mat").isEmpty();
            int placeholders = materialPlaceholderCount(recipeItems);
            if (!isUnboundMaterialTemplate(recipeItems, materialBound)) {
                throw new IllegalStateException("source row #" + index
                        + " is no longer an unbound material template; placeholders="
                        + placeholders + ", materialBound=" + materialBound);
            }
            placeholderCounts.add(Integer.valueOf(placeholders));
        }
        Collections.sort(placeholderCounts);
        return Collections.unmodifiableList(placeholderCounts);
    }

    private static Field exactPublicFinalField(
            Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        Field field = owner.getField(name);
        int modifiers = field.getModifiers();
        if (field.getDeclaringClass() != owner || field.getType() != type
                || !Modifier.isPublic(modifiers) || !Modifier.isFinal(modifiers)
                || Modifier.isStatic(modifiers)) {
            throw new IllegalStateException(owner.getName() + "." + name
                    + " field contract drifted");
        }
        return field;
    }

    private static CacheObservation inspectEmptyPrototypeCache(
            ICraftingHandler handler, String fingerprintDomain) throws ExportFailure {
        if (!(handler instanceof TemplateRecipeHandler)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    HANDLER_ID + " is no longer a TemplateRecipeHandler");
        }
        TemplateRecipeHandler template = (TemplateRecipeHandler) handler;
        if (template.arecipes == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    HANDLER_ID + " exposed a null NEI recipe cache");
        }
        int reported = handler.numRecipes();
        if (reported < 0 || reported != template.arecipes.size()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    HANDLER_ID + " numRecipes/arecipes binding drifted; reported="
                            + reported + ", cache=" + template.arecipes.size());
        }
        String fingerprint = PinnedEmptyRecipeHandlers.fingerprintObjects(
                fingerprintDomain, new ArrayList<Object>(template.arecipes), null);
        return new CacheObservation(reported, fingerprint, fingerprint);
    }

    private static OwnerStaticState inspectOwnerStatics(
            Class<?> handlerClass, List<Object> sourceRows, boolean requireInitialized)
            throws Exception {
        Field recipesField = exactPublicStaticField(handlerClass, "recipes", Set.class);
        Field materialsField = exactPublicStaticField(
                handlerClass, "currentMaterials", String[].class);
        Field blocksField = exactPublicStaticField(
                handlerClass, "currentBlocks", ItemStack[].class);

        Object recipesValue = recipesField.get(null);
        boolean recipesInitialized = recipesValue != null;
        if (requireInitialized && !recipesInitialized) {
            throw new IllegalStateException("owner recipes cache was not initialized");
        }
        if (recipesInitialized) {
            if (recipesValue.getClass() != HashSet.class) {
                throw new IllegalStateException("owner recipes cache class drifted to "
                        + recipesValue.getClass().getName());
            }
            Set<?> recipes = (Set<?>) recipesValue;
            if (recipes.size() != sourceRows.size()) {
                throw new IllegalStateException("owner recipes identity-set size drifted; expected "
                        + sourceRows.size() + ", got " + recipes.size());
            }
            IdentityHashMap<Object, Boolean> expected =
                    new IdentityHashMap<Object, Boolean>();
            for (Object source : sourceRows) {
                expected.put(source, Boolean.TRUE);
            }
            for (Object recipe : recipes) {
                if (!expected.containsKey(recipe)) {
                    throw new IllegalStateException(
                            "owner recipes cache contains a non-source object identity");
                }
            }
        }

        String[] materials = (String[]) materialsField.get(null);
        ItemStack[] blocks = (ItemStack[]) blocksField.get(null);
        if ((materials == null) != (blocks == null)) {
            throw new IllegalStateException(
                    "owner material name/block caches are only partially initialized");
        }
        boolean materialsInitialized = materials != null;
        if (requireInitialized && !materialsInitialized) {
            throw new IllegalStateException("owner rotating-material caches were not initialized");
        }
        String materialFingerprint = null;
        if (materialsInitialized) {
            MaterialRegistrySnapshot registry = inspectMaterialRegistry(
                    handlerClass.getClassLoader());
            if (materials.length == 0 || materials.length != blocks.length) {
                throw new IllegalStateException("owner rotating-material cache cardinality drifted; "
                        + "names=" + materials.length + ", blocks=" + blocks.length);
            }
            if (materials.length != registry.names.size()) {
                throw new IllegalStateException("owner rotating-material cache no longer covers "
                        + "the exact MicroMaterialRegistry order; owner=" + materials.length
                        + ", registry=" + registry.names.size());
            }
            List<String> rows = new ArrayList<String>(materials.length);
            Set<String> materialNames = new HashSet<String>();
            for (int index = 0; index < materials.length; index++) {
                if (materials[index] == null || materials[index].isEmpty()
                        || blocks[index] == null || blocks[index].getItem() == null) {
                    throw new IllegalStateException("owner rotating-material cache contains an "
                            + "invalid entry at index " + index);
                }
                if (!materialNames.add(materials[index])) {
                    throw new IllegalStateException("owner rotating-material cache contains a "
                            + "duplicate material name at index " + index + ": "
                            + materials[index]);
                }
                if (!registry.names.get(index).equals(materials[index])) {
                    throw new IllegalStateException("owner rotating-material cache order drifted "
                            + "at index " + index + "; expected "
                            + registry.names.get(index) + ", got " + materials[index]);
                }
                requireStackIdentity(
                        "owner rotating-material cache entry #" + index,
                        registry.blocks.get(index),
                        blocks[index]);
                StringBuilder row = new StringBuilder();
                appendFrame(row, Integer.toString(index));
                appendFrame(row, materials[index]);
                appendFrame(row, StackIdentity.describe(blocks[index]));
                rows.add(row.toString());
            }
            materialFingerprint = PinnedEmptyRecipeHandlers.stableMultisetFingerprint(
                    CONTRACT + "/owner-material-cache-v2", rows);
        }
        return new OwnerStaticState(
                recipesInitialized, materialsInitialized,
                materials == null ? null : materials.clone(),
                blocks == null ? null : blocks.clone(), materialFingerprint);
    }

    private static CacheObservation inspectLoadedCache(
            ICraftingHandler handler, List<Object> sourceRows, ClassLoader loader,
            OwnerStaticState staticsBefore, long loadStartedAtMillis,
            long loadFinishedAtMillis)
            throws ExportFailure {
        if (!(handler instanceof TemplateRecipeHandler)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    HANDLER_ID + " is no longer a TemplateRecipeHandler");
        }
        TemplateRecipeHandler template = (TemplateRecipeHandler) handler;
        if (template.arecipes == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    HANDLER_ID + " exposed a null loaded NEI recipe cache");
        }
        int reported = handler.numRecipes();
        if (reported < 0 || reported != template.arecipes.size()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    HANDLER_ID + " loaded numRecipes/arecipes binding drifted; reported="
                            + reported + ", cache=" + template.arecipes.size());
        }

        String phase = "class-contracts";
        try {
            Class<?> recipeClass = Class.forName(RECIPE_CLASS, false, loader);
            Class<?> cachedClass = Class.forName(CACHED_RECIPE_CLASS, false, loader);
            Class<?> positionedClass = Class.forName(
                    MICROBLOCK_POSITIONED_STACK_CLASS, false, loader);
            Class<?> cachedBase = Class.forName(CACHED_RECIPE_BASE_CLASS, false, loader);
            if (cachedClass.getSuperclass() != cachedBase
                    || positionedClass.getSuperclass() != PositionedStack.class) {
                throw new IllegalStateException(
                        "Extra Utilities cached-row class hierarchy drifted");
            }
            Field volatileOffset = validateVolatileOffsetField(cachedBase);

            Field sourceItems = exactPublicFinalField(
                    recipeClass, "recipeItems", Object[].class);
            Field sourceOutput = exactPrivateInstanceField(
                    recipeClass, "recipeOutput", ItemStack.class);
            Field cachedIngredients = exactPublicInstanceField(
                    cachedClass, "ingredients", ArrayList.class);
            Field cachedResult = exactPublicInstanceField(
                    cachedClass, "result", positionedClass);
            Field materialTag = exactPrivateInstanceField(
                    positionedClass, "materialTag", boolean.class);
            Field permutated = exactPrivateInstanceField(
                    PositionedStack.class, "permutated", boolean.class);

            phase = "owner-static-registry-parity";
            OwnerStaticState staticsAfter = inspectOwnerStatics(
                    handler.getClass(), sourceRows, true);
            if (staticsBefore.materialsInitialized
                    && !staticsBefore.materialFingerprint.equals(
                    staticsAfter.materialFingerprint)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        HANDLER_ID + " owner material cache changed during category load");
            }
            phase = "owner-selected-material";
            int cycleTicks = template.cycleticks;
            if (cycleTicks < 0) {
                throw new IllegalStateException(
                        "owner cycle tick seed is negative: " + cycleTicks);
            }
            int selectedMaterialIndex = (cycleTicks / 20)
                    % staticsAfter.materials.length;
            String selectedMaterial = staticsAfter.materials[selectedMaterialIndex];
            ItemStack selectedBlock = staticsAfter.blocks[selectedMaterialIndex];
            ItemStack resolvedBlock = resolveMaterialBlock(loader, selectedMaterial);
            requireStackIdentity(
                    "owner selected material block", selectedBlock, resolvedBlock);

            phase = "owner-reset-state";
            Field ownerMaterial = exactPublicInstanceField(
                    handler.getClass(), "currentMaterial", String.class);
            Field ownerBlock = exactPublicInstanceField(
                    handler.getClass(), "currentBlock", ItemStack.class);
            Field ownerScroll = exactPublicInstanceField(
                    handler.getClass(), "scroll", boolean.class);
            if (!"".equals(ownerMaterial.get(handler))
                    || ownerBlock.get(handler) != null
                    || !ownerScroll.getBoolean(handler)) {
                throw new IllegalStateException(
                        "owner did not restore its post-load rotating-material state");
            }

            phase = "source-semantic-projection";
            if (sourceRows.isEmpty()) {
                throw new IllegalStateException("source projection unexpectedly has no rows");
            }
            ItemStack firstOutput = (ItemStack) sourceOutput.get(sourceRows.get(0));
            if (firstOutput == null || firstOutput.getItem() == null) {
                throw new IllegalStateException("source projection has no microblock output item");
            }
            Item recipeOutputItem = firstOutput.getItem();
            String recipeOutputRegistryId = String.valueOf(
                    Item.itemRegistry.getNameForObject(recipeOutputItem));
            if (!RECIPE_OUTPUT_ITEM_ID.equals(recipeOutputRegistryId)) {
                throw new IllegalStateException("source projection recipe-output item drifted to "
                        + recipeOutputRegistryId);
            }
            Item ownerMicroblockItem = resolveOwnerMicroblockItem(loader);
            if (ownerMicroblockItem == recipeOutputItem) {
                throw new IllegalStateException("owner ForgeMicroblock carrier unexpectedly "
                        + "aliases the distinct Extra Utilities recipe-output item");
            }

            List<String> expectedRows = new ArrayList<String>(sourceRows.size());
            for (int index = 0; index < sourceRows.size(); index++) {
                Object source = sourceRows.get(index);
                if (source == null || source.getClass() != recipeClass) {
                    throw new IllegalStateException("source projection row #" + index
                            + " is not the exact " + RECIPE_CLASS + " class");
                }
                ItemStack rowOutput = (ItemStack) sourceOutput.get(source);
                if (rowOutput == null || rowOutput.getItem() != recipeOutputItem) {
                    throw new IllegalStateException("source projection row #" + index
                            + " recipe-output item identity drifted");
                }
                expectedRows.add(expectedCachedRow(
                        (Object[]) sourceItems.get(source),
                        rowOutput, positionedClass, ownerMicroblockItem));
            }

            phase = "loaded-semantic-projection";
            List<String> actualRows = new ArrayList<String>(template.arecipes.size());
            long minimumOffset = Long.MAX_VALUE;
            long maximumOffset = Long.MIN_VALUE;
            for (int index = 0; index < template.arecipes.size(); index++) {
                Object cached = template.arecipes.get(index);
                if (cached == null || cached.getClass() != cachedClass) {
                    throw new IllegalStateException("loaded cache row #" + index
                            + " is not the exact " + CACHED_RECIPE_CLASS + " class");
                }
                Object ingredientValue = cachedIngredients.get(cached);
                if (ingredientValue == null
                        || ingredientValue.getClass() != ArrayList.class) {
                    throw new IllegalStateException("loaded cache row #" + index
                            + " ingredients are not an exact java.util.ArrayList");
                }
                Object resultValue = cachedResult.get(cached);
                if (resultValue == null || resultValue.getClass() != positionedClass) {
                    throw new IllegalStateException("loaded cache row #" + index
                            + " result is not the exact microblock positioned-stack class");
                }
                long offset = volatileOffset.getLong(cached);
                if (offset < loadStartedAtMillis || offset > loadFinishedAtMillis) {
                    throw new IllegalStateException("loaded cache row #" + index
                            + " offset is outside its exact wall-clock construction interval; "
                            + "offset=" + offset + ", interval=[" + loadStartedAtMillis
                            + "," + loadFinishedAtMillis + "]");
                }
                minimumOffset = Math.min(minimumOffset, offset);
                maximumOffset = Math.max(maximumOffset, offset);
                actualRows.add(actualCachedRow(
                        (List<?>) ingredientValue, (PositionedStack) resultValue,
                        positionedClass, materialTag, permutated,
                        ownerMicroblockItem, recipeOutputItem,
                        selectedMaterial, selectedBlock));
            }

            phase = "source-loaded-fingerprint-comparison";
            String expectedFingerprint = PinnedEmptyRecipeHandlers.stableMultisetFingerprint(
                    LOADED_FINGERPRINT_DOMAIN, expectedRows);
            String actualFingerprint = PinnedEmptyRecipeHandlers.stableMultisetFingerprint(
                    LOADED_FINGERPRINT_DOMAIN, actualRows);
            if (!expectedFingerprint.equals(actualFingerprint)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        HANDLER_ID + " loaded semantic cache drifted from its exact pinned "
                                + "source projection; expected " + expectedFingerprint
                                + ", got " + actualFingerprint);
            }
            return new CacheObservation(
                    reported, actualFingerprint, expectedFingerprint,
                    minimumOffset, maximumOffset, cycleTicks,
                    selectedMaterialIndex, selectedMaterial,
                    rowFingerprints(actualRows), loadStartedAtMillis,
                    loadFinishedAtMillis);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            String causeMessage = error.getMessage();
            String causeSummary = error.getClass().getName()
                    + (causeMessage == null || causeMessage.trim().isEmpty()
                    ? "" : ": " + causeMessage);
            GtnhNeiExportMod.LOGGER.error(
                    "[gtnh-nei-export] Extra Utilities semantic cache projection failed at "
                            + "phase=" + phase + "; cause=" + causeSummary,
                    error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    HANDLER_ID + " deterministic semantic cache projection failed at phase="
                            + phase + "; cause=" + causeSummary, error);
        }
    }

    private static String expectedCachedRow(
            Object[] recipeItems, ItemStack recipeOutput, Class<?> positionedClass,
            Item ownerMicroblockItem) {
        if (recipeItems == null || recipeItems.length != 9
                || recipeOutput == null || recipeOutput.getItem() == null) {
            throw new IllegalStateException("source projection lost its exact 3x3/output shape");
        }
        List<String> ingredients = new ArrayList<String>();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                Object ingredient = recipeItems[y * 3 + x];
                if (ingredient == null) {
                    continue;
                }
                ItemStack stack;
                if (ingredient instanceof Integer) {
                    int damage = ((Integer) ingredient).intValue();
                    if (damage < 0) {
                        throw new IllegalStateException(
                                "source projection contains a negative material placeholder");
                    }
                    stack = new ItemStack(ownerMicroblockItem, 1, damage);
                } else if (ingredient instanceof ItemStack) {
                    stack = ((ItemStack) ingredient).copy();
                } else {
                    throw new IllegalStateException(
                            "source projection contains unsupported ingredient "
                                    + ingredient.getClass().getName());
                }
                if (stack.stackSize > 1) {
                    stack.stackSize = 1;
                }
                boolean microblockPositioned = stack.getItem() == ownerMicroblockItem;
                ingredients.add(expectedPositionedStack(
                        stack, 25 + x * 18, 6 + y * 18,
                        microblockPositioned ? positionedClass.getName()
                                : PositionedStack.class.getName(),
                        microblockPositioned,
                        expectedOwnerMaterialTag(
                                microblockPositioned, stack.getItemDamage())));
            }
        }
        String result = expectedPositionedStack(
                recipeOutput, 119, 24, positionedClass.getName(), true,
                expectedOwnerMaterialTag(
                        recipeOutput.getItem() == ownerMicroblockItem,
                        recipeOutput.getItemDamage()));
        return semanticCachedRow(ingredients, result);
    }

    static boolean expectedOwnerMaterialTag(
            boolean isOwnerMicroblockCarrier, int itemDamage) {
        return !isOwnerMicroblockCarrier || itemDamage != 0;
    }

    private static String actualCachedRow(
            List<?> ingredientValues, PositionedStack result, Class<?> positionedClass,
            Field materialTag, Field permutated, Item ownerMicroblockItem,
            Item recipeOutputItem,
            String selectedMaterial, ItemStack selectedBlock) throws IllegalAccessException {
        List<String> ingredients = new ArrayList<String>(ingredientValues.size());
        for (int index = 0; index < ingredientValues.size(); index++) {
            Object value = ingredientValues.get(index);
            if (!(value instanceof PositionedStack)) {
                throw new IllegalStateException("loaded ingredient #" + index
                        + " is not a PositionedStack");
            }
            ingredients.add(actualPositionedStack(
                    (PositionedStack) value, positionedClass, materialTag, permutated,
                    ownerMicroblockItem, ownerMicroblockItem,
                    selectedMaterial, selectedBlock));
        }
        return semanticCachedRow(
                ingredients, actualPositionedStack(
                        result, positionedClass, materialTag, permutated,
                        recipeOutputItem, ownerMicroblockItem,
                        selectedMaterial, selectedBlock));
    }

    private static String expectedPositionedStack(
            ItemStack stack, int x, int y, String className,
            boolean hasMaterialTagField, boolean materialTag) {
        requireUnboundStack(stack, "source projection");
        ItemStack semanticStack = stack;
        int ownerVisibleStackSize = projectedSourceStackSize(
                stack.stackSize, hasMaterialTagField, materialTag);
        if (ownerVisibleStackSize != stack.stackSize) {
            semanticStack = stack.copy();
            semanticStack.stackSize = ownerVisibleStackSize;
        }
        String descriptor = StackIdentity.describe(semanticStack);
        StringBuilder row = new StringBuilder("positioned-stack-v2{");
        appendFrame(row, className);
        appendFrame(row, Integer.toString(x));
        appendFrame(row, Integer.toString(y));
        appendFrame(row, hasMaterialTagField ? Boolean.toString(materialTag) : "not-applicable");
        appendFrame(row, descriptor);
        appendFrame(row, descriptor);
        return row.append('}').toString();
    }

    /**
     * XU replaces only a damage-zero {@code ForgeMicroblock:microblock} placeholder with
     * {@code currentBlock.copy()}. The exact GTNH source placeholders are already singletons.
     * Extra Utilities recipe outputs use the distinct {@code ExtraUtilities:microblocks} item,
     * so their {@code materialTag} is true and their source count (including the 8-item result)
     * must be preserved. A non-singleton false branch is therefore semantic drift, not a value to
     * normalize silently.
     */
    static int projectedSourceStackSize(
            int sourceStackSize, boolean hasMaterialTagField, boolean materialTag) {
        if (hasMaterialTagField && !materialTag && sourceStackSize != 1) {
            throw new IllegalStateException("damage-zero ForgeMicroblock source placeholder "
                    + "is not a singleton: " + sourceStackSize);
        }
        return sourceStackSize;
    }

    private static String actualPositionedStack(
            PositionedStack stack, Class<?> positionedClass, Field materialTag,
            Field permutated, Item exactPositionedCarrierItem,
            Item ownerMicroblockItem, String selectedMaterial,
            ItemStack selectedBlock)
            throws IllegalAccessException {
        Class<?> runtimeClass = stack.getClass();
        boolean hasMaterialTagField;
        String materialTagValue;
        boolean boundMaterialTag = false;
        if (runtimeClass == positionedClass) {
            hasMaterialTagField = true;
            boundMaterialTag = materialTag.getBoolean(stack);
            materialTagValue = Boolean.toString(boundMaterialTag);
        } else if (runtimeClass == PositionedStack.class) {
            hasMaterialTagField = false;
            materialTagValue = "not-applicable";
        } else {
            throw new IllegalStateException("loaded positioned stack class drifted to "
                    + runtimeClass.getName());
        }
        if (stack.items == null || stack.items.length != 1 || stack.items[0] == null
                || stack.item == null) {
            throw new IllegalStateException("loaded positioned stack lost its exact singleton "
                    + "item/items representation");
        }
        if (!permutated.getBoolean(stack)) {
            throw new IllegalStateException(
                    "loaded positioned stack did not complete computeVisuals permutation");
        }
        if (!StackIdentity.describe(stack.item).equals(
                StackIdentity.describe(stack.items[0]))) {
            throw new IllegalStateException(
                    "loaded positioned stack item/items selection is not semantically coherent");
        }

        String itemsDescriptor;
        String selectedDescriptor;
        if (hasMaterialTagField && boundMaterialTag) {
            if (stack.items[0].getItem() != exactPositionedCarrierItem) {
                throw new IllegalStateException("material-tagged loaded stack carrier drifted; "
                        + "expected=" + Item.itemRegistry.getNameForObject(
                                exactPositionedCarrierItem)
                        + ", got=" + Item.itemRegistry.getNameForObject(
                                stack.items[0].getItem()));
            }
            ItemStack normalizedItems = removeExactMaterialBinding(
                    stack.items[0], selectedMaterial, "loaded items[0]");
            ItemStack normalizedSelected = removeExactMaterialBinding(
                    stack.item, selectedMaterial, "loaded selected item");
            requireTrueMaterialTagPredicate(
                    normalizedItems, ownerMicroblockItem, "loaded items[0]");
            requireTrueMaterialTagPredicate(
                    normalizedSelected, ownerMicroblockItem, "loaded selected item");
            itemsDescriptor = StackIdentity.describe(normalizedItems);
            selectedDescriptor = StackIdentity.describe(normalizedSelected);
        } else if (hasMaterialTagField) {
            requireStackIdentity(
                    "loaded material-block placeholder items[0]",
                    selectedBlock, stack.items[0]);
            requireStackIdentity(
                    "loaded material-block placeholder selected item",
                    selectedBlock, stack.item);
            ItemStack normalized = new ItemStack(ownerMicroblockItem, 1, 0);
            itemsDescriptor = StackIdentity.describe(normalized);
            selectedDescriptor = itemsDescriptor;
        } else {
            requireUnboundStack(stack.items[0], "loaded plain items[0]");
            requireUnboundStack(stack.item, "loaded plain selected item");
            itemsDescriptor = StackIdentity.describe(stack.items[0]);
            selectedDescriptor = StackIdentity.describe(stack.item);
        }
        StringBuilder row = new StringBuilder("positioned-stack-v2{");
        appendFrame(row, runtimeClass.getName());
        appendFrame(row, Integer.toString(stack.relx));
        appendFrame(row, Integer.toString(stack.rely));
        appendFrame(row, hasMaterialTagField ? materialTagValue : "not-applicable");
        appendFrame(row, itemsDescriptor);
        appendFrame(row, selectedDescriptor);
        return row.append('}').toString();
    }

    private static void requireTrueMaterialTagPredicate(
            ItemStack normalized, Item ownerMicroblockItem, String label) {
        if (!expectedOwnerMaterialTag(
                normalized.getItem() == ownerMicroblockItem,
                normalized.getItemDamage())) {
            throw new IllegalStateException(label + " contradicts the exact owner materialTag "
                    + "predicate after removing the injected material binding");
        }
    }

    private static ItemStack removeExactMaterialBinding(
            ItemStack stack, String selectedMaterial, String label) {
        if (stack == null || !stack.hasTagCompound() || stack.getTagCompound() == null) {
            throw new IllegalStateException(label + " has no owner-injected material binding");
        }
        String actual = stack.getTagCompound().getString("mat");
        if (!selectedMaterial.equals(actual)) {
            throw new IllegalStateException(label + " material binding drifted; expected "
                    + selectedMaterial + ", got " + actual);
        }
        ItemStack normalized = stack.copy();
        normalized.getTagCompound().removeTag("mat");
        if (normalized.getTagCompound().hasNoTags()) {
            normalized.setTagCompound(null);
        }
        return normalized;
    }

    private static void requireStackIdentity(
            String label, ItemStack expected, ItemStack actual) {
        if (expected == null || actual == null
                || !StackIdentity.describe(expected).equals(StackIdentity.describe(actual))) {
            throw new IllegalStateException(label + " identity drifted; expected "
                    + (expected == null ? "null" : StackIdentity.describe(expected))
                    + ", got "
                    + (actual == null ? "null" : StackIdentity.describe(actual)));
        }
    }

    private static ItemStack resolveMaterialBlock(
            ClassLoader loader, String materialName) throws Exception {
        Class<?> registryClass = Class.forName(
                "codechicken.microblock.MicroMaterialRegistry", false, loader);
        Class<?> materialClass = Class.forName(
                "codechicken.microblock.MicroMaterialRegistry$IMicroMaterial", false, loader);
        Method getMaterial = registryClass.getMethod("getMaterial", String.class);
        int getMaterialModifiers = getMaterial.getModifiers();
        if (getMaterial.getDeclaringClass() != registryClass
                || getMaterial.getReturnType() != materialClass
                || !Modifier.isPublic(getMaterialModifiers)
                || !Modifier.isStatic(getMaterialModifiers)
                || getMaterial.isSynthetic()) {
            throw new IllegalStateException(
                    "MicroMaterialRegistry.getMaterial(String) contract drifted");
        }
        Object material = getMaterial.invoke(null, materialName);
        if (material == null || !materialClass.isInstance(material)) {
            throw new IllegalStateException("selected micro-material did not resolve: "
                    + materialName);
        }

        return materialItem(materialClass, material,
                "selected micro-material " + materialName);
    }

    private static Item resolveOwnerMicroblockItem(ClassLoader loader) throws Exception {
        Class<?> accessClass = Class.forName(
                OWNER_MICROBLOCK_ACCESS_CLASS, false, loader);
        Method accessor = accessClass.getMethod(OWNER_MICROBLOCK_ACCESS_METHOD);
        int modifiers = accessor.getModifiers();
        if (accessor.getDeclaringClass() != accessClass
                || accessor.getReturnType() != Item.class
                || accessor.getParameterTypes().length != 0
                || !Modifier.isPublic(modifiers)
                || !Modifier.isStatic(modifiers)
                || accessor.isSynthetic()) {
            throw new IllegalStateException(OWNER_MICROBLOCK_ACCESS_CLASS + "."
                    + OWNER_MICROBLOCK_ACCESS_METHOD + "() contract drifted");
        }
        Object value = accessor.invoke(null);
        if (!(value instanceof Item)) {
            throw new IllegalStateException("owner ForgeMicroblock carrier accessor returned "
                    + (value == null ? "null" : value.getClass().getName()));
        }
        Item item = (Item) value;
        String registryId = String.valueOf(Item.itemRegistry.getNameForObject(item));
        if (!OWNER_MICROBLOCK_ITEM_ID.equals(registryId)) {
            throw new IllegalStateException("owner ForgeMicroblock carrier registry ID drifted; "
                    + "expected=" + OWNER_MICROBLOCK_ITEM_ID + ", got=" + registryId);
        }
        return item;
    }

    private static ItemStack materialItem(
            Class<?> materialClass, Object material, String label) throws Exception {
        Method getItem = materialClass.getMethod("getItem");
        int getItemModifiers = getItem.getModifiers();
        if (getItem.getDeclaringClass() != materialClass
                || getItem.getReturnType() != ItemStack.class
                || getItem.getParameterTypes().length != 0
                || !Modifier.isPublic(getItemModifiers)
                || !Modifier.isAbstract(getItemModifiers)
                || Modifier.isStatic(getItemModifiers)
                || getItem.isSynthetic()) {
            throw new IllegalStateException(
                    "IMicroMaterial.getItem() contract drifted");
        }
        ItemStack resolved = (ItemStack) getItem.invoke(material);
        if (resolved == null || resolved.getItem() == null) {
            throw new IllegalStateException(label + " resolved to an invalid item");
        }
        return resolved;
    }

    private static MaterialRegistrySnapshot inspectMaterialRegistry(ClassLoader loader)
            throws Exception {
        Class<?> registryClass = Class.forName(
                "codechicken.microblock.MicroMaterialRegistry", false, loader);
        Class<?> materialClass = Class.forName(
                "codechicken.microblock.MicroMaterialRegistry$IMicroMaterial", false, loader);
        Class<?> tupleClass = Class.forName("scala.Tuple2", false, loader);
        Method getIdMap = registryClass.getMethod("getIdMap");
        int getIdMapModifiers = getIdMap.getModifiers();
        Class<?> returnType = getIdMap.getReturnType();
        if (getIdMap.getDeclaringClass() != registryClass
                || getIdMap.getParameterTypes().length != 0
                || !returnType.isArray()
                || returnType.getComponentType() != tupleClass
                || !Modifier.isPublic(getIdMapModifiers)
                || !Modifier.isStatic(getIdMapModifiers)
                || getIdMap.isSynthetic()) {
            throw new IllegalStateException(
                    "MicroMaterialRegistry.getIdMap() contract drifted");
        }
        Method first = tupleClass.getMethod("_1");
        Method second = tupleClass.getMethod("_2");
        if (first.getDeclaringClass() != tupleClass
                || second.getDeclaringClass() != tupleClass
                || first.getReturnType() != Object.class
                || second.getReturnType() != Object.class
                || first.getParameterTypes().length != 0
                || second.getParameterTypes().length != 0
                || !Modifier.isPublic(first.getModifiers())
                || !Modifier.isPublic(second.getModifiers())
                || Modifier.isStatic(first.getModifiers())
                || Modifier.isStatic(second.getModifiers())
                || first.isSynthetic() || second.isSynthetic()) {
            throw new IllegalStateException("scala.Tuple2 accessor contract drifted");
        }

        Object mapValue = getIdMap.invoke(null);
        if (mapValue == null || mapValue.getClass() != returnType) {
            throw new IllegalStateException(
                    "MicroMaterialRegistry.getIdMap() returned the wrong array type");
        }
        Object[] entries = (Object[]) mapValue;
        if (entries.length == 0) {
            throw new IllegalStateException("MicroMaterialRegistry is unexpectedly empty");
        }
        List<String> names = new ArrayList<String>(entries.length);
        List<ItemStack> blocks = new ArrayList<ItemStack>(entries.length);
        Set<String> uniqueNames = new HashSet<String>();
        for (int index = 0; index < entries.length; index++) {
            Object entry = entries[index];
            if (entry == null || entry.getClass() != tupleClass) {
                throw new IllegalStateException("MicroMaterialRegistry row #" + index
                        + " is not an exact scala.Tuple2");
            }
            Object nameValue = first.invoke(entry);
            Object material = second.invoke(entry);
            if (nameValue == null || nameValue.getClass() != String.class
                    || ((String) nameValue).isEmpty()
                    || material == null || !materialClass.isInstance(material)) {
                throw new IllegalStateException("MicroMaterialRegistry row #" + index
                        + " has an invalid key/material binding");
            }
            String name = (String) nameValue;
            if (!uniqueNames.add(name)) {
                throw new IllegalStateException(
                        "MicroMaterialRegistry contains duplicate key " + name);
            }
            ItemStack directBlock = materialItem(
                    materialClass, material, "MicroMaterialRegistry row #" + index);
            ItemStack resolvedBlock = resolveMaterialBlock(loader, name);
            requireStackIdentity("MicroMaterialRegistry keyed row #" + index,
                    directBlock, resolvedBlock);
            names.add(name);
            blocks.add(directBlock.copy());
        }
        return new MaterialRegistrySnapshot(names, blocks);
    }

    private static List<String> rowFingerprints(List<String> rows) {
        List<String> fingerprints = new ArrayList<String>(rows.size());
        for (String row : rows) {
            fingerprints.add(Naming.sha256(LOADED_FINGERPRINT_DOMAIN + "/row/" + row));
        }
        Collections.sort(fingerprints);
        return Collections.unmodifiableList(fingerprints);
    }

    private static String semanticCachedRow(List<String> ingredients, String result) {
        StringBuilder row = new StringBuilder("microblock-cached-recipe-v2{");
        appendFrame(row, result);
        row.append(ingredients.size()).append(':');
        for (String ingredient : ingredients) {
            appendFrame(row, ingredient);
        }
        return row.append('}').toString();
    }

    private static void requireUnboundStack(ItemStack stack, String label) {
        if (stack == null || stack.getItem() == null) {
            throw new IllegalStateException(label + " stack is null or itemless");
        }
        if (stack.hasTagCompound() && stack.getTagCompound() != null
                && !stack.getTagCompound().getString("mat").isEmpty()) {
            throw new IllegalStateException(label + " unexpectedly contains a material binding");
        }
    }

    private static Field validateVolatileOffsetField(Class<?> cachedBase)
            throws NoSuchFieldException {
        Field offset = cachedBase.getDeclaredField(VOLATILE_CACHE_OFFSET_FIELD);
        int modifiers = offset.getModifiers();
        if (offset.getDeclaringClass() != cachedBase || offset.getType() != long.class
                || Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)
                || Modifier.isTransient(modifiers) || offset.isSynthetic()) {
            throw new IllegalStateException(CACHED_RECIPE_BASE_CLASS + "."
                    + VOLATILE_CACHE_OFFSET_FIELD + " volatile-field contract drifted");
        }
        offset.setAccessible(true);
        return offset;
    }

    private static Field exactPublicStaticField(
            Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        Field field = owner.getField(name);
        int modifiers = field.getModifiers();
        if (field.getDeclaringClass() != owner || field.getType() != type
                || !Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)
                || Modifier.isTransient(modifiers) || field.isSynthetic()) {
            throw new IllegalStateException(owner.getName() + "." + name
                    + " public-static-field contract drifted");
        }
        return field;
    }

    private static Field exactPublicInstanceField(
            Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        Field field = owner.getField(name);
        int modifiers = field.getModifiers();
        if (field.getDeclaringClass() != owner || field.getType() != type
                || !Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)
                || Modifier.isTransient(modifiers) || field.isSynthetic()) {
            throw new IllegalStateException(owner.getName() + "." + name
                    + " public-field contract drifted");
        }
        return field;
    }

    private static Field exactPrivateInstanceField(
            Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        int modifiers = field.getModifiers();
        if (field.getDeclaringClass() != owner || field.getType() != type
                || Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)
                || field.isSynthetic()) {
            throw new IllegalStateException(owner.getName() + "." + name
                    + " declared-field contract drifted");
        }
        field.setAccessible(true);
        return field;
    }

    private static void appendFrame(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private static void requireSourcePromotion(SourceObservation source)
            throws ExportFailure {
        requireEquals("CraftingManager registry count", EXPECTED_SOURCE_REGISTRY_COUNT,
                source.registryCount);
        requireEquals("raw source count", EXPECTED_SOURCE_COUNT, source.rows.size());
        requireEquals("raw source fingerprint", EXPECTED_SOURCE_FINGERPRINT,
                source.fingerprint);
        if (!EXPECTED_PLACEHOLDER_COUNTS.equals(source.placeholderCounts)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    HANDLER_ID + " material-placeholder vector drifted; expected "
                            + EXPECTED_PLACEHOLDER_COUNTS + ", got "
                            + source.placeholderCounts);
        }
    }

    static boolean isUnboundMaterialTemplate(
            Object[] recipeItems, boolean outputHasMaterialBinding) {
        return !outputHasMaterialBinding && materialPlaceholderCount(recipeItems) > 0;
    }

    static int materialPlaceholderCount(Object[] recipeItems) {
        if (recipeItems == null) {
            return 0;
        }
        int placeholders = 0;
        for (Object ingredient : recipeItems) {
            if (ingredient instanceof Integer) {
                if (((Integer) ingredient).intValue() < 0) {
                    return 0;
                }
                placeholders++;
            }
        }
        return placeholders;
    }

    static String derivedCategoryId() {
        String key = HandlerCategoryPlan.buildCategoryKey(
                HANDLER_CLASS, HANDLER_ID, OVERLAY,
                "output-id:" + OPERATION, LEGACY_GENERIC_ADAPTER_CONTRACT);
        return "gtnh:" + Naming.sha256(key).substring(0, 32);
    }

    private static void requireEquals(String label, int expected, int actual)
            throws ExportFailure {
        if (expected != actual) {
            throw new ExportFailure("HANDLER_UNLOADED", HANDLER_ID + " " + label
                    + " drifted; expected " + expected + ", got " + actual);
        }
    }

    private static void requireEquals(String label, String expected, String actual)
            throws ExportFailure {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new ExportFailure("HANDLER_UNLOADED", HANDLER_ID + " " + label
                    + " drifted; expected " + expected + ", got " + actual);
        }
    }

    private static final class SourceObservation {
        final int registryCount;
        final List<Object> rows;
        final String fingerprint;
        final List<Integer> placeholderCounts;

        SourceObservation(int registryCount, List<Object> rows, String fingerprint,
                          List<Integer> placeholderCounts) {
            this.registryCount = registryCount;
            this.rows = Collections.unmodifiableList(new ArrayList<Object>(rows));
            this.fingerprint = fingerprint;
            this.placeholderCounts = placeholderCounts;
        }
    }

    private static final class CacheObservation {
        final int count;
        final String fingerprint;
        final String expectedFingerprint;
        final long minimumOffset;
        final long maximumOffset;
        final int cycleTicks;
        final int selectedMaterialIndex;
        final String selectedMaterial;
        final List<String> rowFingerprints;
        final long loadStartedAtMillis;
        final long loadFinishedAtMillis;

        CacheObservation(int count, String fingerprint, String expectedFingerprint) {
            this(count, fingerprint, expectedFingerprint,
                    -1L, -1L, -1, -1, "not-applicable",
                    Collections.<String>emptyList(), -1L, -1L);
        }

        CacheObservation(int count, String fingerprint, String expectedFingerprint,
                         long minimumOffset, long maximumOffset, int cycleTicks,
                         int selectedMaterialIndex, String selectedMaterial,
                         List<String> rowFingerprints, long loadStartedAtMillis,
                         long loadFinishedAtMillis) {
            this.count = count;
            this.fingerprint = fingerprint;
            this.expectedFingerprint = expectedFingerprint;
            this.minimumOffset = minimumOffset;
            this.maximumOffset = maximumOffset;
            this.cycleTicks = cycleTicks;
            this.selectedMaterialIndex = selectedMaterialIndex;
            this.selectedMaterial = selectedMaterial;
            this.rowFingerprints = rowFingerprints;
            this.loadStartedAtMillis = loadStartedAtMillis;
            this.loadFinishedAtMillis = loadFinishedAtMillis;
        }
    }

    private static final class OwnerStaticState {
        final boolean recipesInitialized;
        final boolean materialsInitialized;
        final String[] materials;
        final ItemStack[] blocks;
        final String materialFingerprint;

        OwnerStaticState(boolean recipesInitialized, boolean materialsInitialized,
                         String[] materials, ItemStack[] blocks,
                         String materialFingerprint) {
            this.recipesInitialized = recipesInitialized;
            this.materialsInitialized = materialsInitialized;
            this.materials = materials;
            this.blocks = blocks;
            this.materialFingerprint = materialFingerprint;
        }
    }

    private static final class MaterialRegistrySnapshot {
        final List<String> names;
        final List<ItemStack> blocks;

        MaterialRegistrySnapshot(List<String> names, List<ItemStack> blocks) {
            this.names = Collections.unmodifiableList(new ArrayList<String>(names));
            this.blocks = Collections.unmodifiableList(new ArrayList<ItemStack>(blocks));
        }
    }
}
