package com.recipetree.neiexport1710;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Exact GTNH 2.8.4 query-closure loaders for registered handlers whose empty
 * zero-argument NEI clone is not a complete category.
 *
 * <p>The adapters deliberately derive a finite query universe from each mod's
 * authoritative recipe/variant registry. They never scan the mutable NEI item
 * list and never accept a zero-page result. Source and loaded-page corpora are
 * independently canonicalized and fingerprinted before the fresh handler is
 * returned.</p>
 */
final class QueryClosureCategoryAdapters {
    static final String PROJECT_BLUE_HANDLER =
            "gcewing.projectblue.nei.NEIRecipeHandler";
    static final String PROJECT_RED_SHAPED_HANDLER =
            "mrtjp.projectred.core.libmc.recipe.PRShapedRecipeHandler";
    static final String PROJECT_RED_SHAPELESS_HANDLER =
            "mrtjp.projectred.core.libmc.recipe.PRShapelessRecipeHandler";
    static final String GENDUSTRY_TEMPLATE_HANDLER =
            "net.bdew.gendustry.nei.TemplateCraftingHandler";
    static final String BOTANIA_FLOATING_FLOWER_HANDLER =
            "vazkii.botania.client.integration.nei.recipe.RecipeHandlerFloatingFlowers";

    static final String PROJECT_BLUE_CONTRACT =
            "adapter:projectblue-control-panel-registry-query-closure-v1";
    static final String PROJECT_RED_SHAPED_CONTRACT =
            "adapter:projectred-shaped-builder-registry-query-closure-v2";
    static final String PROJECT_RED_SHAPELESS_CONTRACT =
            "adapter:projectred-shapeless-builder-registry-query-closure-v2";
    static final String GENDUSTRY_CONTRACT =
            "adapter:gendustry-template-crafting-exact-item-query-v1";
    static final String BOTANIA_CONTRACT =
            "adapter:botania-floating-special-flower-variant-query-closure-v1";

    private static final String PROJECT_BLUE_RECIPES =
            "gcewing.projectblue.ControlPanelRecipes";
    private static final String PROJECT_BLUE_RECIPE_BASE =
            PROJECT_BLUE_RECIPES + "$RecipeBase";
    private static final String[] PROJECT_BLUE_GENERATORS = {
        PROJECT_BLUE_RECIPES + "$CraftControlPanel",
        PROJECT_BLUE_RECIPES + "$CraftMiniatureItem",
        PROJECT_BLUE_RECIPES + "$PaintControl"
    };
    private static final String PROJECT_BLUE_MATERIAL =
            "gcewing.projectblue.ControlPanelMaterial";
    private static final String PROJECT_BLUE_OWNER = "gcewing.projectblue.ProjectBlue";
    private static final String MICRO_MATERIAL_REGISTRY =
            "codechicken.microblock.MicroMaterialRegistry";
    private static final String MICRO_MATERIAL_INTERFACE =
            MICRO_MATERIAL_REGISTRY + "$IMicroMaterial";

    private static final String PROJECT_RED_SHAPED_RECIPE =
            "mrtjp.projectred.core.libmc.recipe.ShapedBuilderRecipe";
    private static final String PROJECT_RED_SHAPELESS_RECIPE =
            "mrtjp.projectred.core.libmc.recipe.ShapelessBuilderRecipe";
    private static final String PROJECT_RED_SHAPED_BUILDER =
            "mrtjp.projectred.core.libmc.recipe.ShapedRecipeBuilder";
    private static final String PROJECT_RED_SHAPELESS_BUILDER =
            "mrtjp.projectred.core.libmc.recipe.ShapelessRecipeBuilder";
    private static final String PROJECT_RED_INPUT =
            "mrtjp.projectred.core.libmc.recipe.Input";
    private static final String PROJECT_RED_OUTPUT =
            "mrtjp.projectred.core.libmc.recipe.Output";
    private static final String[] PROJECT_RED_INPUT_IMPLEMENTATIONS = {
        "mrtjp.projectred.core.libmc.recipe.ItemIn",
        "mrtjp.projectred.core.libmc.recipe.MicroIn",
        "mrtjp.projectred.core.libmc.recipe.OreIn"
    };
    private static final String PROJECT_RED_OUTPUT_IMPLEMENTATION =
            "mrtjp.projectred.core.libmc.recipe.ItemOut";
    private static final String SCALA_SEQ = "scala.collection.Seq";
    private static final String SCALA_IMMUTABLE_MAP =
            "scala.collection.immutable.Map";
    private static final String SCALA_OPTION = "scala.Option";
    private static final String PROJECT_RED_SHAPED_CACHED =
            PROJECT_RED_SHAPED_HANDLER + "$CachedShapedRecipe";
    private static final String PROJECT_RED_SHAPELESS_CACHED =
            PROJECT_RED_SHAPELESS_HANDLER + "$CachedShapelessRecipe";
    private static final String PROJECT_RED_SHAPED_SOURCE_FIELD =
            "mrtjp$projectred$core$libmc$recipe$PRShapedRecipeHandler$"
                    + "CachedShapedRecipe$$r";
    private static final String PROJECT_RED_SHAPELESS_SOURCE_FIELD =
            "mrtjp$projectred$core$libmc$recipe$PRShapelessRecipeHandler$"
                    + "CachedShapelessRecipe$$r";

    private static final String GENDUSTRY_TEMPLATE_ITEM =
            "net.bdew.gendustry.items.GeneTemplate$";
    private static final String GENDUSTRY_SAMPLE_ITEM =
            "net.bdew.gendustry.items.GeneSample$";

    private static final String BOTANIA_API = "vazkii.botania.api.BotaniaAPI";
    private static final String BOTANIA_BLOCKS = "vazkii.botania.common.block.ModBlocks";
    private static final String BOTANIA_SPECIAL_ITEM =
            "vazkii.botania.common.item.block.ItemBlockSpecialFlower";

    /** Exact GTNH 2.8.4 source/loaded corpus pins promoted from exporter 1.0.53. */
    private static final Map<String, Promotion> PROMOTIONS;
    private static final Map<String, String> CONTRACTS;
    private static final IdentityHashMap<ICraftingHandler, Capture> DISCOVERED =
            new IdentityHashMap<ICraftingHandler, Capture>();

    static {
        Map<String, String> contracts = new LinkedHashMap<String, String>();
        contracts.put(PROJECT_BLUE_HANDLER, PROJECT_BLUE_CONTRACT);
        contracts.put(PROJECT_RED_SHAPED_HANDLER, PROJECT_RED_SHAPED_CONTRACT);
        contracts.put(PROJECT_RED_SHAPELESS_HANDLER, PROJECT_RED_SHAPELESS_CONTRACT);
        contracts.put(GENDUSTRY_TEMPLATE_HANDLER, GENDUSTRY_CONTRACT);
        contracts.put(BOTANIA_FLOATING_FLOWER_HANDLER, BOTANIA_CONTRACT);
        CONTRACTS = Collections.unmodifiableMap(contracts);

        Map<String, Promotion> promotions = new LinkedHashMap<String, Promotion>();
        promotions.put(PROJECT_BLUE_HANDLER, new Promotion(
                7357,
                "5e0f358246d3d4780f48e8a591b248864fe3f399a1de41cd88699050368d0e91",
                7357,
                "5033685bf19ab87f41262a104a2e5e161eb014f2c4951b2826f2e87eaa4c3723"));
        promotions.put(PROJECT_RED_SHAPED_HANDLER, new Promotion(
                2,
                "ff3ff344a5a428125af41db0f77e545de893109b6a1160c067b8024fd58718c8",
                2,
                "a1e9609ef004b9c1b465f9be96a4453da45f5e0a87211a8e94998143052a5006"));
        promotions.put(PROJECT_RED_SHAPELESS_HANDLER, new Promotion(
                1,
                "b62b0181ce81e6ea6db44135cbd9f622f93c1d71443b37d427966c193461f4de",
                1,
                "e8cb1a76003cb24a701795eac64dfac9815491677203d47ff55898f7142da631"));
        promotions.put(GENDUSTRY_TEMPLATE_HANDLER, new Promotion(
                1,
                "6f3af73521057201c9d72113fd4122c60f492fd2fbb5ebe52eee63d8b1e2cf32",
                1,
                "cf2397f8610d83968d9a19069f46be23dd1d623dbde16ec4d1ecdb8f29bc5ec7"));
        promotions.put(BOTANIA_FLOATING_FLOWER_HANDLER, new Promotion(
                61,
                "38d9115db74936bdf0afb1618da9be14d914634de2a7742a4a705a604187613c",
                61,
                "46e4a96b068bbc1679b1cbd99a23908a73586938d2dfc0e0f66e04a6e10df8e3"));
        PROMOTIONS = Collections.unmodifiableMap(promotions);
    }

    private QueryClosureCategoryAdapters() {
    }

    static boolean supports(String runtimeClass) {
        return CONTRACTS.containsKey(runtimeClass);
    }

    static String contractFor(String runtimeClass) throws ExportFailure {
        String contract = CONTRACTS.get(runtimeClass);
        if (contract == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "no query-closure contract exists for " + runtimeClass);
        }
        return contract;
    }

    static List<String> supportedHandlerClasses() {
        List<String> classes = new ArrayList<String>(CONTRACTS.keySet());
        Collections.sort(classes);
        return Collections.unmodifiableList(classes);
    }

    /** Cheap prototype-only validation for the structural planning pass. */
    static void validatePrototype(ICraftingHandler prototype) throws ExportFailure {
        if (prototype == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "query-closure prototype is null");
        }
        String runtimeClass = prototype.getClass().getName();
        contractFor(runtimeClass);
        requirePrototypeIdentity(prototype, runtimeClass);
        requireFreshConstructorShape(prototype.getClass());
        requireDirectItemQueryMethod(prototype.getClass());
    }

    /**
     * Captures every pinned query closure before rendering. All handler-local
     * failures and all unpromoted rows are accumulated so one boot reveals the
     * complete promotion inventory instead of stopping at the first category.
     */
    static synchronized DiscoveryInventory discoverPinnedInventory(
            List<ICraftingHandler> prototypes) throws ExportFailure {
        Map<String, ICraftingHandler> byClass = new HashMap<String, ICraftingHandler>();
        List<String> issues = new ArrayList<String>();
        if (prototypes == null) {
            issues.add("registered prototype list is null");
        } else {
            for (ICraftingHandler prototype : prototypes) {
                if (prototype == null || !supports(prototype.getClass().getName())) {
                    continue;
                }
                String runtimeClass = prototype.getClass().getName();
                ICraftingHandler previous = byClass.put(runtimeClass, prototype);
                if (previous != null) {
                    issues.add(runtimeClass + ": registered more than once");
                }
            }
        }

        List<AuditRow> rows = new ArrayList<AuditRow>();
        IdentityHashMap<ICraftingHandler, Capture> completed =
                new IdentityHashMap<ICraftingHandler, Capture>();
        for (String runtimeClass : supportedHandlerClasses()) {
            ICraftingHandler prototype = byClass.get(runtimeClass);
            if (prototype == null) {
                issues.add(runtimeClass + ": missing registered prototype");
                continue;
            }
            try {
                Capture capture = capture(prototype);
                rows.add(capture.row);
                completed.put(prototype, capture);
                logAudit(capture.row);
                String mismatch = PROMOTIONS.get(runtimeClass).mismatch(capture.row);
                if (mismatch != null) {
                    issues.add(runtimeClass + ": " + mismatch);
                }
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                issues.add(runtimeClass + ": " + diagnostic(error));
            }
        }
        Collections.sort(rows, AuditRow.ORDER);
        String aggregate = fingerprint("gtnh-query-closure-inventory-v1", auditLines(rows));
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Query-closure inventory probe completed: "
                        + "handlers={}, aggregateFingerprint={}",
                rows.size(), aggregate);
        if (!issues.isEmpty()) {
            Collections.sort(issues);
            StringBuilder message = new StringBuilder(2048);
            message.append("GTNH 2.8.4 query-closure inventory has ")
                    .append(issues.size()).append(" issue(s); every observed row was logged");
            for (String issue : issues) {
                message.append("\n- ").append(issue);
            }
            throw new ExportFailure("HANDLER_UNLOADED", message.toString());
        }
        DISCOVERED.clear();
        DISCOVERED.putAll(completed);
        return new DiscoveryInventory(rows, aggregate);
    }

    /** Strict category-load path; never returns an unpromoted corpus. */
    static synchronized ICraftingHandler load(ICraftingHandler prototype)
            throws ExportFailure {
        validatePrototype(prototype);
        Capture capture = DISCOVERED.remove(prototype);
        if (capture == null) {
            capture = capture(prototype);
            logAudit(capture.row);
        } else {
            requirePrototypeIdentity(prototype, capture.row.handlerClass);
        }
        String mismatch = PROMOTIONS.get(capture.row.handlerClass).mismatch(capture.row);
        if (mismatch != null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    capture.row.handlerClass + " " + mismatch);
        }
        return capture.loaded;
    }

    private static Capture capture(ICraftingHandler prototype) throws ExportFailure {
        validatePrototype(prototype);
        String runtimeClass = prototype.getClass().getName();
        if (PROJECT_BLUE_HANDLER.equals(runtimeClass)) {
            return captureProjectBlue((TemplateRecipeHandler) prototype);
        }
        if (PROJECT_RED_SHAPED_HANDLER.equals(runtimeClass)) {
            return captureProjectRed((TemplateRecipeHandler) prototype, true);
        }
        if (PROJECT_RED_SHAPELESS_HANDLER.equals(runtimeClass)) {
            return captureProjectRed((TemplateRecipeHandler) prototype, false);
        }
        if (GENDUSTRY_TEMPLATE_HANDLER.equals(runtimeClass)) {
            return captureGendustry((TemplateRecipeHandler) prototype);
        }
        if (BOTANIA_FLOATING_FLOWER_HANDLER.equals(runtimeClass)) {
            return captureBotania((TemplateRecipeHandler) prototype);
        }
        throw new ExportFailure("HANDLER_AMBIGUOUS",
                "no query-closure loader exists for " + runtimeClass);
    }

    private static Capture captureProjectBlue(TemplateRecipeHandler prototype)
            throws ExportFailure {
        String label = "ProjectBlue control-panel recipes";
        try {
            ClassLoader loader = prototype.getClass().getClassLoader();
            Class<?> recipesClass = Class.forName(PROJECT_BLUE_RECIPES, false, loader);
            Class<?> recipeBase = Class.forName(PROJECT_BLUE_RECIPE_BASE, false, loader);
            Field recipesField = exactPublicField(recipesClass, "recipes", List.class);
            requireStatic(recipesField, PROJECT_BLUE_RECIPES + ".recipes");
            Object rawRecipes = recipesField.get(null);
            if (rawRecipes == null || rawRecipes.getClass() != ArrayList.class) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        label + " registry must remain the exact ArrayList instance");
            }
            List<?> recipes = (List<?>) rawRecipes;
            if (recipes.size() != PROJECT_BLUE_GENERATORS.length) {
                throw new ExportFailure("HANDLER_UNLOADED", label
                        + " generator count drifted; expected "
                        + PROJECT_BLUE_GENERATORS.length + ", got " + recipes.size());
            }
            Set<Object> generatorIdentities =
                    Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            for (int index = 0; index < PROJECT_BLUE_GENERATORS.length; index++) {
                Object generator = recipes.get(index);
                if (generator == null
                        || !PROJECT_BLUE_GENERATORS[index].equals(generator.getClass().getName())
                        || !recipeBase.isInstance(generator)
                        || !generatorIdentities.add(generator)) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", label
                            + " generator vector drifted at index " + index);
                }
                int craftingRegistrations = 0;
                for (Object registered : craftingRecipes()) {
                    if (registered == generator) {
                        craftingRegistrations++;
                    }
                }
                if (craftingRegistrations != 1) {
                    throw new ExportFailure("HANDLER_UNLOADED", label
                            + " generator is not identity-registered exactly once: "
                            + generator.getClass().getName());
                }
            }

            Class<?> materialClass = Class.forName(PROJECT_BLUE_MATERIAL, false, loader);
            Method forName = exactPublicMethod(
                    materialClass, "forName", materialClass, String.class);
            requireStatic(forName, PROJECT_BLUE_MATERIAL + ".forName");
            Method materialPanel = exactPublicMethod(
                    materialClass, "newStack", ItemStack.class);
            Method materialItem = exactPublicMethod(
                    materialClass, "newStack", ItemStack.class, Item.class);

            Class<?> owner = Class.forName(PROJECT_BLUE_OWNER, false, loader);
            Item controlPanel = publicStaticItem(owner, "controlPanelItem");
            Item miniatureLever = publicStaticItem(owner, "miniatureLever");
            Item miniatureButton = publicStaticItem(owner, "miniatureButton");
            Item miniatureLamp = publicStaticItem(owner, "miniatureLamp");
            Item miniatureCover = publicStaticItem(owner, "miniatureCover");
            Item stoneSaw = publicStaticItem(owner, "itemStoneSaw");
            Field stoneSawStackField = exactPublicField(
                    owner, "stackStoneSaw", ItemStack.class);
            requireStatic(stoneSawStackField, PROJECT_BLUE_OWNER + ".stackStoneSaw");
            ItemStack stoneSawStack = requireStack(
                    stoneSawStackField.get(null), label + " shared stone saw");
            requireItem(stoneSawStack, stoneSaw, label + " shared stone saw");
            if (stoneSawStack.stackSize != 1) {
                throw new ExportFailure("RECIPE_SEMANTICS", label
                        + " shared stone saw must have amount 1 before NEI construction");
            }
            String stoneSawBefore = stackCanonical(
                    stoneSawStack, label + " shared stone saw before queries");

            Class<?> microRegistry = Class.forName(MICRO_MATERIAL_REGISTRY, false, loader);
            Class<?> microMaterial = Class.forName(MICRO_MATERIAL_INTERFACE, false, loader);
            if (!microMaterial.isInterface()) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        MICRO_MATERIAL_INTERFACE + " is no longer an interface");
            }
            Method getIdMap = exactPublicArrayMethod(microRegistry, "getIdMap", "scala.Tuple2");
            requireStatic(getIdMap, MICRO_MATERIAL_REGISTRY + ".getIdMap");
            Method getMaterial = exactPublicMethod(
                    microRegistry, "getMaterial", microMaterial, String.class);
            requireStatic(getMaterial, MICRO_MATERIAL_REGISTRY + ".getMaterial");
            Method microItem = exactPublicMethod(microMaterial, "getItem", ItemStack.class);

            Object idMap = getIdMap.invoke(null);
            int materialCount = idMap == null ? -1 : Array.getLength(idMap);
            if (materialCount <= 0) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        label + " saw no registered micro materials");
            }
            List<QueryTarget> targets = new ArrayList<QueryTarget>(materialCount * 2 + 51);
            List<String> registryRows = new ArrayList<String>(materialCount);
            Set<String> materialNames = new HashSet<String>();
            for (int index = 0; index < materialCount; index++) {
                Object tuple = Array.get(idMap, index);
                if (tuple == null || !"scala.Tuple2".equals(tuple.getClass().getName())) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", label
                            + " ID map contains a non-Tuple2 entry at " + index);
                }
                Method first = exactPublicMethod(tuple.getClass(), "_1", Object.class);
                Method second = exactPublicMethod(tuple.getClass(), "_2", Object.class);
                Object rawName = first.invoke(tuple);
                Object rawMaterial = second.invoke(tuple);
                if (!(rawName instanceof String) || ((String) rawName).trim().isEmpty()
                        || !microMaterial.isInstance(rawMaterial)
                        || !materialNames.add((String) rawName)
                        || getMaterial.invoke(null, rawName) != rawMaterial) {
                    throw new ExportFailure("HANDLER_DUPLICATE", label
                            + " material ID map drifted at index " + index);
                }
                String name = (String) rawName;
                ItemStack sourceStack = requireStack(
                        microItem.invoke(rawMaterial), label + " material " + name);
                Object panelMaterial = forName.invoke(null, name);
                if (panelMaterial == null || panelMaterial.getClass() != materialClass) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", label
                            + " material adapter type drifted for " + name);
                }
                ItemStack panel = requireStack(
                        materialPanel.invoke(panelMaterial), label + " panel " + name);
                ItemStack cover = requireStack(
                        materialItem.invoke(panelMaterial, miniatureCover),
                        label + " miniature cover " + name);
                requireItem(panel, controlPanel, label + " panel " + name);
                requireItem(cover, miniatureCover, label + " cover " + name);
                String registryRow = "material|" + framed(name)
                        + framed(stackCanonical(sourceStack, label + " source " + name));
                registryRows.add(registryRow);
                targets.add(new QueryTarget("panel|" + framed(name), panel));
                targets.add(new QueryTarget("cover|" + framed(name), cover));
            }
            for (int metadata = 0; metadata <= 16; metadata++) {
                targets.add(new QueryTarget("lever|" + metadata,
                        new ItemStack(miniatureLever, 1, metadata)));
            }
            for (int metadata = 0; metadata <= 17; metadata++) {
                targets.add(new QueryTarget("button|" + metadata,
                        new ItemStack(miniatureButton, 1, metadata)));
            }
            for (int metadata = 0; metadata < 16; metadata++) {
                targets.add(new QueryTarget("lamp|" + metadata,
                        new ItemStack(miniatureLamp, 1, metadata)));
            }
            if (targets.size() != materialCount * 2 + 51) {
                throw new ExportFailure("INTERNAL_ERROR",
                        label + " finite query-universe formula was violated");
            }
            canonicalizeTargets(targets, label);

            TemplateRecipeHandler loaded = freshExact(prototype, PROJECT_BLUE_HANDLER);
            for (QueryTarget target : targets) {
                int before = loaded.numRecipes();
                loaded.loadCraftingRecipes(target.stack.copy());
                if (loaded.numRecipes() != before + 1) {
                    throw new ExportFailure("HANDLER_UNLOADED", label
                            + " target did not add exactly one page: " + target.sourceRow
                            + "; before=" + before + ", after=" + loaded.numRecipes());
                }
                requireResultIdentity(loaded, before, target.stack,
                        label + " target " + target.sourceRow);
            }
            requireCount(label + " post-query prototype pages", 0, prototype.numRecipes());
            PageCorpus pages = canonicalizeLoadedPages(loaded, label);
            List<String> sourceRows = new ArrayList<String>(registryRows);
            sourceRows.add("generators|" + Arrays.toString(PROJECT_BLUE_GENERATORS));
            Object stoneSawAfterRaw = stoneSawStackField.get(null);
            if (stoneSawAfterRaw != stoneSawStack) {
                throw new ExportFailure("RECIPE_SEMANTICS", label
                        + " replaced its shared stone-saw stack during NEI queries");
            }
            String stoneSawAfter = stackCanonical(
                    requireStack(stoneSawAfterRaw, label + " shared stone saw after queries"),
                    label + " shared stone saw after queries");
            if (!stoneSawBefore.equals(stoneSawAfter)) {
                throw new ExportFailure("RECIPE_SEMANTICS", label
                        + " mutated its shared stone-saw stack during NEI queries");
            }
            sourceRows.add("shared-stone-saw|" + stoneSawBefore);
            for (QueryTarget target : targets) {
                sourceRows.add("target|" + target.sourceRow + "|"
                        + stackCanonical(target.stack, label + " target source"));
            }
            AuditRow row = new AuditRow(PROJECT_BLUE_HANDLER, PROJECT_BLUE_CONTRACT,
                    targets.size(), fingerprint(PROJECT_BLUE_CONTRACT + ":source", sourceRows),
                    pages.rows.size(), pages.fingerprint,
                    "generators=3,microMaterials=" + materialCount
                            + ",fixedTargets=51");
            requireSourceLoadedParity(row, label);
            return new Capture(loaded, row);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    label + " exact query-closure capture failed", unwrap(error));
        }
    }

    private static Capture captureProjectRed(
            TemplateRecipeHandler prototype, boolean shaped) throws ExportFailure {
        String handlerClass = shaped
                ? PROJECT_RED_SHAPED_HANDLER : PROJECT_RED_SHAPELESS_HANDLER;
        String recipeClassName = shaped
                ? PROJECT_RED_SHAPED_RECIPE : PROJECT_RED_SHAPELESS_RECIPE;
        String cachedClassName = shaped
                ? PROJECT_RED_SHAPED_CACHED : PROJECT_RED_SHAPELESS_CACHED;
        String sourceFieldName = shaped
                ? PROJECT_RED_SHAPED_SOURCE_FIELD : PROJECT_RED_SHAPELESS_SOURCE_FIELD;
        String contract = shaped
                ? PROJECT_RED_SHAPED_CONTRACT : PROJECT_RED_SHAPELESS_CONTRACT;
        String label = shaped ? "ProjectRed shaped recipes" : "ProjectRed shapeless recipes";
        try {
            ClassLoader loader = prototype.getClass().getClassLoader();
            Class<?> recipeClass = Class.forName(recipeClassName, false, loader);
            Class<?> cachedClass = Class.forName(cachedClassName, false, loader);
            ProjectRedSourceContract sourceContract =
                    new ProjectRedSourceContract(loader, recipeClass, shaped, label);
            Field sourceField = exactDeclaredField(cachedClass, sourceFieldName, recipeClass);
            if (!Modifier.isPublic(sourceField.getModifiers())
                    || !Modifier.isFinal(sourceField.getModifiers())
                    || Modifier.isStatic(sourceField.getModifiers())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", label
                        + " cached-page source field modifier contract drifted");
            }

            List<Object> sources = new ArrayList<Object>();
            List<String> sourceRows = new ArrayList<String>();
            for (Object raw : craftingRecipes()) {
                if (!recipeClass.isInstance(raw)) {
                    continue;
                }
                if (raw.getClass() != recipeClass || !(raw instanceof IRecipe)) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", label
                            + " registry contains an unexpected recipe subclass "
                            + raw.getClass().getName());
                }
                ItemStack output = requireStack(((IRecipe) raw).getRecipeOutput(),
                        label + " source output");
                sources.add(raw);
                sourceRows.add(sourceContract.canonical(
                        raw, output, label + " source #" + sources.size()));
            }
            if (sources.isEmpty()) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        label + " authoritative CraftingManager source is empty");
            }

            List<RecipeQueryGroup> groups = groupProjectRedQueries(sources, label);
            TemplateRecipeHandler loaded = freshExact(prototype, handlerClass);
            IdentityHashMap<Object, Integer> coverage =
                    new IdentityHashMap<Object, Integer>();
            for (RecipeQueryGroup group : groups) {
                int before = loaded.numRecipes();
                loaded.loadCraftingRecipes(group.target.copy());
                int delta = loaded.numRecipes() - before;
                if (delta != group.sources.size()) {
                    throw new ExportFailure("HANDLER_UNLOADED", label
                            + " public item query loaded " + delta + " pages for "
                            + group.sources.size() + " matching source recipes: "
                            + group.targetCanonical);
                }
                Set<Object> expected = Collections.newSetFromMap(
                        new IdentityHashMap<Object, Boolean>());
                expected.addAll(group.sources);
                for (int index = before; index < loaded.numRecipes(); index++) {
                    Object cached = loaded.arecipes.get(index);
                    if (cached == null || cached.getClass() != cachedClass) {
                        throw new ExportFailure("HANDLER_AMBIGUOUS", label
                                + " query constructed an unexpected cached-page class");
                    }
                    Object source = sourceField.get(cached);
                    if (!expected.contains(source)) {
                        throw new ExportFailure("HANDLER_AMBIGUOUS", label
                                + " query page is not backed by its matching registry group");
                    }
                    Integer prior = coverage.get(source);
                    coverage.put(source, Integer.valueOf(prior == null ? 1 : prior + 1));
                }
            }
            for (Object source : sources) {
                Integer count = coverage.get(source);
                if (count == null || count.intValue() != 1) {
                    throw new ExportFailure("HANDLER_DUPLICATE", label
                            + " did not cover one registry recipe exactly once");
                }
            }
            requireCount(label + " loaded page count", sources.size(), loaded.numRecipes());
            requireCount(label + " post-query prototype pages", 0, prototype.numRecipes());
            PageCorpus pages = canonicalizeLoadedPages(loaded, label);
            List<String> afterQueryRows = new ArrayList<String>(sources.size());
            for (int index = 0; index < sources.size(); index++) {
                Object source = sources.get(index);
                ItemStack output = requireStack(((IRecipe) source).getRecipeOutput(),
                        label + " post-query source output #" + index);
                afterQueryRows.add(sourceContract.canonical(
                        source, output, label + " post-query source #" + index));
            }
            if (!sourceRows.equals(afterQueryRows)) {
                throw new ExportFailure("RECIPE_SEMANTICS", label
                        + " authoritative builder corpus mutated during NEI queries");
            }
            AuditRow row = new AuditRow(handlerClass, contract,
                    sources.size(), fingerprint(contract + ":source", sourceRows),
                    pages.rows.size(), pages.fingerprint,
                    "craftingManagerMatches=" + sources.size()
                            + ",queryEquivalenceGroups=" + groups.size());
            requireSourceLoadedParity(row, label);
            return new Capture(loaded, row);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    label + " exact query-closure capture failed", unwrap(error));
        }
    }

    private static Capture captureGendustry(TemplateRecipeHandler prototype)
            throws ExportFailure {
        String label = "Gendustry template crafting";
        try {
            ClassLoader loader = prototype.getClass().getClassLoader();
            Item template = scalaModuleItem(loader, GENDUSTRY_TEMPLATE_ITEM);
            Item sample = scalaModuleItem(loader, GENDUSTRY_SAMPLE_ITEM);
            Method addRecipe = exactPublicMethod(
                    prototype.getClass(), "addRecipe", void.class);
            if (Modifier.isStatic(addRecipe.getModifiers())
                    || addRecipe.getDeclaringClass() != prototype.getClass()) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        label + " addRecipe() declaration drifted");
            }
            ItemStack target = new ItemStack(template, 1, 0);
            TemplateRecipeHandler loaded = freshExact(prototype, GENDUSTRY_TEMPLATE_HANDLER);
            loaded.loadCraftingRecipes(target.copy());
            requireCount(label + " loaded page count", 1, loaded.numRecipes());
            requireCount(label + " post-query prototype pages", 0, prototype.numRecipes());
            requireResultIdentity(loaded, 0, target, label);
            List<PositionedStack> ingredients = positionedList(
                    loaded.getIngredientStacks(0), label + " ingredients");
            int templates = 0;
            int samples = 0;
            for (PositionedStack positioned : ingredients) {
                if (positioned.items == null || positioned.items.length != 1) {
                    throw new ExportFailure("RECIPE_SEMANTICS", label
                            + " ingredients must each retain one exact item alternative");
                }
                Item item = requireStack(positioned.items[0], label + " ingredient").getItem();
                templates += item == template ? 1 : 0;
                samples += item == sample ? 1 : 0;
                if (item != template && item != sample) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            label + " contains an unexpected ingredient item");
                }
            }
            if (ingredients.size() != 3 || templates != 1 || samples != 2) {
                throw new ExportFailure("RECIPE_SEMANTICS", label
                        + " ingredient multiplicity drifted; expected template=1,sample=2; got "
                        + "template=" + templates + ",sample=" + samples);
            }
            PageCorpus pages = canonicalizeLoadedPages(loaded, label);
            List<String> sources = Arrays.asList(
                    "template|" + stackCanonical(target, label + " template"),
                    "sample|" + stackCanonical(
                            new ItemStack(sample, 1, 0), label + " sample"),
                    "multiplicity|template=1|sample=2");
            AuditRow row = new AuditRow(
                    GENDUSTRY_TEMPLATE_HANDLER, GENDUSTRY_CONTRACT,
                    1, fingerprint(GENDUSTRY_CONTRACT + ":source", sources),
                    pages.rows.size(), pages.fingerprint,
                    "syntheticRecipes=1,ingredients=3");
            requireSourceLoadedParity(row, label);
            return new Capture(loaded, row);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    label + " exact item-query capture failed", unwrap(error));
        }
    }

    private static Capture captureBotania(TemplateRecipeHandler prototype)
            throws ExportFailure {
        String label = "Botania floating special flowers";
        try {
            ClassLoader loader = prototype.getClass().getClassLoader();
            Class<?> api = Class.forName(BOTANIA_API, false, loader);
            Field creativeField = exactPublicField(api, "subtilesForCreativeMenu", Set.class);
            Field modsField = exactPublicField(api, "subTileMods", Map.class);
            requireStatic(creativeField, BOTANIA_API + ".subtilesForCreativeMenu");
            requireStatic(modsField, BOTANIA_API + ".subTileMods");
            Field miniField = api.getField("miniFlowers");
            if (!"com.google.common.collect.BiMap".equals(miniField.getType().getName())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        BOTANIA_API + ".miniFlowers type drifted");
            }
            requireStatic(miniField, BOTANIA_API + ".miniFlowers");
            Method allMethod = exactPublicMethod(api, "getAllSubTiles", Set.class);
            Method mappingMethod = exactPublicMethod(
                    api, "getSubTileMapping", Class.class, String.class);
            requireStatic(allMethod, BOTANIA_API + ".getAllSubTiles");
            requireStatic(mappingMethod, BOTANIA_API + ".getSubTileMapping");

            Set<String> creative = stringSet(
                    creativeField.get(null), label + " creative registry", false);
            Set<String> all = stringSet(
                    allMethod.invoke(null), label + " complete registry", true);
            @SuppressWarnings("unchecked")
            Map<Object, Object> miniRaw = (Map<Object, Object>) miniField.get(null);
            @SuppressWarnings("unchecked")
            Map<Object, Object> modsRaw = (Map<Object, Object>) modsField.get(null);
            if (miniRaw == null || modsRaw == null || creative.isEmpty() || all.isEmpty()) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        label + " variant registries are empty or null");
            }
            TreeMap<String, String> mini = new TreeMap<String, String>();
            for (Map.Entry<Object, Object> entry : miniRaw.entrySet()) {
                if (!(entry.getKey() instanceof String)
                        || !(entry.getValue() instanceof String)
                        || ((String) entry.getKey()).trim().isEmpty()
                        || ((String) entry.getValue()).trim().isEmpty()
                        || !creative.contains(entry.getKey())) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", label
                            + " mini-flower map contains a non-creative or malformed mapping");
                }
                mini.put((String) entry.getKey(), (String) entry.getValue());
            }
            TreeSet<String> variants = new TreeSet<String>(creative);
            variants.addAll(mini.values());
            if (!all.containsAll(variants)) {
                throw new ExportFailure("HANDLER_UNLOADED", label
                        + " displayed variant set is not covered by getAllSubTiles()");
            }

            Class<?> blocks = Class.forName(BOTANIA_BLOCKS, false, loader);
            Block special = publicStaticBlock(blocks, "specialFlower");
            Block floatingSpecial = publicStaticBlock(blocks, "floatingSpecialFlower");
            Block floatingBase = publicStaticBlock(blocks, "floatingFlower");
            Class<?> specialItemClass = Class.forName(BOTANIA_SPECIAL_ITEM, false, loader);
            Method ofType = exactPublicMethod(
                    specialItemClass, "ofType", ItemStack.class,
                    ItemStack.class, String.class);
            requireStatic(ofType, BOTANIA_SPECIAL_ITEM + ".ofType");

            List<QueryTarget> targets = new ArrayList<QueryTarget>(variants.size());
            List<String> sourceRows = new ArrayList<String>();
            for (String name : new TreeSet<String>(all)) {
                Object mapping = mappingMethod.invoke(null, name);
                if (!(mapping instanceof Class)) {
                    throw new ExportFailure("HANDLER_UNLOADED", label
                            + " has no class mapping for subtype " + framed(name));
                }
                Object mod = modsRaw.get(name);
                if (!(mod instanceof String) || ((String) mod).trim().isEmpty()) {
                    throw new ExportFailure("HANDLER_UNLOADED", label
                            + " has no owning mod for subtype " + framed(name));
                }
                sourceRows.add("subtile|" + framed(name)
                        + framed(((Class<?>) mapping).getName()) + framed((String) mod)
                        + "creative=" + creative.contains(name));
            }
            for (Map.Entry<String, String> entry : mini.entrySet()) {
                sourceRows.add("mini|" + framed(entry.getKey()) + framed(entry.getValue()));
            }
            for (String name : variants) {
                ItemStack target = requireStack(ofType.invoke(
                        null, new ItemStack(floatingSpecial), name),
                        label + " output " + name);
                requireItem(target, Item.getItemFromBlock(floatingSpecial),
                        label + " output " + name);
                targets.add(new QueryTarget("variant|" + framed(name), target));
            }
            canonicalizeTargets(targets, label);
            TemplateRecipeHandler loaded = freshExact(
                    prototype, BOTANIA_FLOATING_FLOWER_HANDLER);
            for (QueryTarget target : targets) {
                int before = loaded.numRecipes();
                loaded.loadCraftingRecipes(target.stack.copy());
                if (loaded.numRecipes() != before + 1) {
                    throw new ExportFailure("HANDLER_UNLOADED", label
                            + " variant query did not add exactly one page: "
                            + target.sourceRow);
                }
                requireResultIdentity(loaded, before, target.stack,
                        label + " " + target.sourceRow);
                requireBotaniaPage(loaded, before, special, floatingBase,
                        target.stack, label + " " + target.sourceRow);
                sourceRows.add("target|" + target.sourceRow + "|"
                        + stackCanonical(target.stack, label + " target"));
            }
            requireCount(label + " post-query prototype pages", 0, prototype.numRecipes());
            PageCorpus pages = canonicalizeLoadedPages(loaded, label);
            AuditRow row = new AuditRow(
                    BOTANIA_FLOATING_FLOWER_HANDLER, BOTANIA_CONTRACT,
                    targets.size(), fingerprint(BOTANIA_CONTRACT + ":source", sourceRows),
                    pages.rows.size(), pages.fingerprint,
                    "allSubTiles=" + all.size() + ",creative=" + creative.size()
                            + ",miniMappings=" + mini.size()
                            + ",displayedVariants=" + variants.size());
            requireSourceLoadedParity(row, label);
            return new Capture(loaded, row);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    label + " exact variant-query capture failed", unwrap(error));
        }
    }

    /**
     * Reflection-only, read-only canonicalizer for ProjectRed's authoritative
     * builder state. The exporter deliberately does not compile against
     * ProjectRed or Scala; every reflected declaration is therefore checked
     * against the exact pinned 4.12.6-GTNH API before any recipe is accepted.
     */
    private static final class ProjectRedSourceContract {
        private final boolean shaped;
        private final String label;
        private final Class<?> builderClass;
        private final Class<?> inputClass;
        private final Class<?> outputClass;
        private final Class<?> sequenceClass;
        private final Class<?> mapClass;
        private final Class<?> optionClass;
        private final Set<Class<?>> inputImplementations;
        private final Class<?> outputImplementation;
        private final Method builderMethod;
        private final Method inputResultMethod;
        private final Method outputResultMethod;
        private final Method sequenceSizeMethod;
        private final Method sequenceApplyMethod;
        private final Method inputAlternativesMethod;
        private final Method inputIdMethod;
        private final Method outputCreateMethod;
        private final Method outputIdMethod;
        private final Method shapedSizeMethod;
        private final Method shapedMapMethod;
        private final Method shapedInputMapMethod;
        private final Method mapSizeMethod;
        private final Method mapGetMethod;
        private final Method optionDefinedMethod;
        private final Method optionGetMethod;

        ProjectRedSourceContract(
                ClassLoader loader, Class<?> recipeClass, boolean shaped, String label)
                throws Exception {
            this.shaped = shaped;
            this.label = label;
            String builderName = shaped
                    ? PROJECT_RED_SHAPED_BUILDER : PROJECT_RED_SHAPELESS_BUILDER;
            this.builderClass = Class.forName(builderName, false, loader);
            this.inputClass = Class.forName(PROJECT_RED_INPUT, false, loader);
            this.outputClass = Class.forName(PROJECT_RED_OUTPUT, false, loader);
            this.sequenceClass = Class.forName(SCALA_SEQ, false, loader);
            this.mapClass = Class.forName(SCALA_IMMUTABLE_MAP, false, loader);
            this.optionClass = Class.forName(SCALA_OPTION, false, loader);
            if (!inputClass.isInterface() || !outputClass.isInterface()
                    || !sequenceClass.isInterface() || !mapClass.isInterface()
                    || !Modifier.isAbstract(optionClass.getModifiers())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", label
                        + " ProjectRed/Scala interface shape drifted");
            }

            Set<Class<?>> exactInputs = new HashSet<Class<?>>();
            for (String implementation : PROJECT_RED_INPUT_IMPLEMENTATIONS) {
                Class<?> type = Class.forName(implementation, false, loader);
                if (!inputClass.isAssignableFrom(type) || !exactInputs.add(type)) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", label
                            + " input implementation contract drifted: " + implementation);
                }
            }
            this.inputImplementations = Collections.unmodifiableSet(exactInputs);
            this.outputImplementation = Class.forName(
                    PROJECT_RED_OUTPUT_IMPLEMENTATION, false, loader);
            if (!outputClass.isAssignableFrom(outputImplementation)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", label
                        + " output implementation contract drifted");
            }

            this.builderMethod = exactPublicMethod(
                    recipeClass, "builder", builderClass);
            requireDirectInstanceMethod(builderMethod, recipeClass,
                    recipeClass.getName() + ".builder");
            this.inputResultMethod = exactPublicMethod(
                    builderClass, "inResult", sequenceClass);
            this.outputResultMethod = exactPublicMethod(
                    builderClass, "outResult", sequenceClass);
            requireDirectInstanceMethod(inputResultMethod, builderClass,
                    builderClass.getName() + ".inResult");
            requireDirectInstanceMethod(outputResultMethod, builderClass,
                    builderClass.getName() + ".outResult");

            this.sequenceSizeMethod = exactPublicMethod(
                    sequenceClass, "size", int.class);
            this.sequenceApplyMethod = exactPublicMethod(
                    sequenceClass, "apply", Object.class, int.class);
            this.inputAlternativesMethod = exactPublicMethod(
                    inputClass, "matchingInputs", sequenceClass);
            this.inputIdMethod = exactPublicMethod(
                    inputClass, "id", String.class);
            this.outputCreateMethod = exactPublicMethod(
                    outputClass, "createOutput", ItemStack.class);
            this.outputIdMethod = exactPublicMethod(
                    outputClass, "id", String.class);
            requireDirectInstanceMethod(inputAlternativesMethod, inputClass,
                    PROJECT_RED_INPUT + ".matchingInputs");
            requireDirectInstanceMethod(outputCreateMethod, outputClass,
                    PROJECT_RED_OUTPUT + ".createOutput");

            if (shaped) {
                this.shapedSizeMethod = exactPublicMethod(
                        builderClass, "size", int.class);
                this.shapedMapMethod = exactPublicMethod(
                        builderClass, "map", String.class);
                this.shapedInputMapMethod = exactPublicMethod(
                        builderClass, "inputMap", mapClass);
                requireDirectInstanceMethod(shapedSizeMethod, builderClass,
                        builderClass.getName() + ".size");
                requireDirectInstanceMethod(shapedMapMethod, builderClass,
                        builderClass.getName() + ".map");
                requireDirectInstanceMethod(shapedInputMapMethod, builderClass,
                        builderClass.getName() + ".inputMap");
                this.mapSizeMethod = exactPublicMethod(mapClass, "size", int.class);
                this.mapGetMethod = exactPublicMethod(
                        mapClass, "get", optionClass, Object.class);
                this.optionDefinedMethod = exactPublicMethod(
                        optionClass, "isDefined", boolean.class);
                this.optionGetMethod = exactPublicMethod(
                        optionClass, "get", Object.class);
            } else {
                this.shapedSizeMethod = null;
                this.shapedMapMethod = null;
                this.shapedInputMapMethod = null;
                this.mapSizeMethod = null;
                this.mapGetMethod = null;
                this.optionDefinedMethod = null;
                this.optionGetMethod = null;
            }
        }

        String canonical(Object recipe, ItemStack recipeOutput, String recipeLabel)
                throws Exception {
            Object builder = builderMethod.invoke(recipe);
            if (builder == null || builder.getClass() != builderClass) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", recipeLabel
                        + " did not retain its exact builder instance class");
            }

            Object rawInputs = inputResultMethod.invoke(builder);
            List<Object> inputs = sequenceElements(
                    rawInputs, recipeLabel + " declared inputs");
            if (inputs.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS", recipeLabel
                        + " has no declared inputs");
            }
            List<String> inputRows = new ArrayList<String>(inputs.size());
            Set<Object> inputIdentities = Collections.newSetFromMap(
                    new IdentityHashMap<Object, Boolean>());
            for (int index = 0; index < inputs.size(); index++) {
                Object input = inputs.get(index);
                inputIdentities.add(input);
                inputRows.add(inputCanonical(
                        input, recipeLabel + " declared input #" + index));
            }

            List<Object> outputs = sequenceElements(
                    outputResultMethod.invoke(builder), recipeLabel + " declared outputs");
            if (outputs.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS", recipeLabel
                        + " has no declared outputs");
            }
            List<String> outputRows = new ArrayList<String>(outputs.size());
            for (int index = 0; index < outputs.size(); index++) {
                Object output = outputs.get(index);
                outputRows.add(outputCanonical(
                        output, recipeLabel + " declared output #" + index));
            }
            String authoritativeOutput = stackCanonical(
                    recipeOutput, recipeLabel + " IRecipe output");
            String firstCreatedOutput = stackCanonical(
                    requireStack(outputCreateMethod.invoke(outputs.get(0)),
                            recipeLabel + " first builder output"),
                    recipeLabel + " first builder output");
            if (!authoritativeOutput.equals(firstCreatedOutput)) {
                throw new ExportFailure("RECIPE_SEMANTICS", recipeLabel
                        + " IRecipe output differs from the first builder output");
            }

            StringBuilder canonical = new StringBuilder(2048);
            canonical.append("projectred-source-recipe-v2")
                    .append(framed(recipe.getClass().getName()))
                    .append(framed(builderClass.getName()));
            if (shaped) {
                appendShapedState(canonical, builder, inputRows, inputIdentities,
                        recipeLabel);
            } else {
                Collections.sort(inputRows);
                appendRows(canonical, "shapeless-input-multiset", inputRows);
            }
            appendRows(canonical, "declared-outputs", outputRows);
            canonical.append("irecipe-output")
                    .append(framed(authoritativeOutput));
            return canonical.toString();
        }

        private void appendShapedState(
                StringBuilder canonical, Object builder, List<String> inputRows,
                Set<Object> inputIdentities, String recipeLabel) throws Exception {
            int size = ((Integer) shapedSizeMethod.invoke(builder)).intValue();
            Object rawMap = shapedMapMethod.invoke(builder);
            if (!(rawMap instanceof String) || size <= 0 || size > 3) {
                throw new ExportFailure("RECIPE_SEMANTICS", recipeLabel
                        + " has an invalid shaped grid size/map");
            }
            String map = (String) rawMap;
            int slotCount = size * size;
            if (map.isEmpty() || map.length() > slotCount) {
                throw new ExportFailure("RECIPE_SEMANTICS", recipeLabel
                        + " shaped map length is outside its grid; size="
                        + size + ", mapLength=" + map.length());
            }
            Object inputMap = shapedInputMapMethod.invoke(builder);
            if (inputMap == null || !mapClass.isInstance(inputMap)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", recipeLabel
                        + " shaped inputMap is not the exact Scala Map interface");
            }
            List<String> slotRows = new ArrayList<String>(slotCount);
            int definedSlots = 0;
            for (int slot = 0; slot < slotCount; slot++) {
                Object option = mapGetMethod.invoke(inputMap, Integer.valueOf(slot));
                if (option == null || !optionClass.isInstance(option)) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", recipeLabel
                            + " inputMap.get returned a non-Option at slot " + slot);
                }
                boolean defined = ((Boolean) optionDefinedMethod.invoke(option))
                        .booleanValue();
                String value = "empty";
                if (defined) {
                    if (slot >= map.length()) {
                        throw new ExportFailure("HANDLER_AMBIGUOUS", recipeLabel
                                + " inputMap defines a trailing slot outside the map string");
                    }
                    Object input = optionGetMethod.invoke(option);
                    if (!inputIdentities.contains(input)) {
                        throw new ExportFailure("HANDLER_AMBIGUOUS", recipeLabel
                                + " inputMap slot is not backed by a declared Input identity");
                    }
                    value = inputCanonical(
                            input, recipeLabel + " mapped input slot #" + slot);
                    definedSlots++;
                }
                String symbol = slot < map.length()
                        ? map.substring(slot, slot + 1) : "<trailing-grid-slot>";
                slotRows.add(slot + "|symbol=" + framed(symbol)
                        + "|" + value);
            }
            int mapSize = ((Integer) mapSizeMethod.invoke(inputMap)).intValue();
            if (mapSize != definedSlots) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", recipeLabel
                        + " inputMap contains keys outside the shaped slot domain; mapSize="
                        + mapSize + ", definedSlots=" + definedSlots);
            }
            canonical.append("grid-size=").append(size)
                    .append(";map=").append(framed(map));
            appendRows(canonical, "declared-input-order", inputRows);
            appendRows(canonical, "mapped-slots", slotRows);
        }

        private String inputCanonical(Object raw, String inputLabel) throws Exception {
            if (raw == null || !inputImplementations.contains(raw.getClass())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", inputLabel
                        + " is not one of the three exact pinned Input implementations");
            }
            Object rawId = inputIdMethod.invoke(raw);
            if (!(rawId instanceof String)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", inputLabel
                        + " has a null or non-String builder ID");
            }
            List<Object> alternatives = sequenceElements(
                    inputAlternativesMethod.invoke(raw), inputLabel + " alternatives");
            if (alternatives.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS", inputLabel
                        + " has no matching input alternatives");
            }
            List<String> rows = new ArrayList<String>(alternatives.size());
            for (int index = 0; index < alternatives.size(); index++) {
                ItemStack alternative = requireStack(alternatives.get(index),
                        inputLabel + " alternative #" + index);
                if (alternative.stackSize != 1) {
                    throw new ExportFailure("RECIPE_SEMANTICS", inputLabel
                            + " alternative #" + index + " has amount "
                            + alternative.stackSize + "; NEI construction would mutate it");
                }
                rows.add(stackCanonical(
                        alternative, inputLabel + " alternative #" + index));
            }
            Collections.sort(rows);
            StringBuilder canonical = new StringBuilder(512);
            canonical.append("input-v1")
                    .append(framed(raw.getClass().getName()))
                    .append(framed((String) rawId));
            appendRows(canonical, "matching-alternatives", rows);
            return canonical.toString();
        }

        private String outputCanonical(Object raw, String outputLabel) throws Exception {
            if (raw == null || raw.getClass() != outputImplementation) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", outputLabel
                        + " is not the exact pinned ItemOut implementation");
            }
            Object rawId = outputIdMethod.invoke(raw);
            if (!(rawId instanceof String)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", outputLabel
                        + " has a null or non-String builder ID");
            }
            ItemStack stack = requireStack(
                    outputCreateMethod.invoke(raw), outputLabel + " created stack");
            return "output-v1" + framed(raw.getClass().getName())
                    + framed((String) rawId)
                    + framed(stackCanonical(stack, outputLabel + " created stack"));
        }

        private List<Object> sequenceElements(Object raw, String sequenceLabel)
                throws Exception {
            if (raw == null || !sequenceClass.isInstance(raw)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", sequenceLabel
                        + " is not the exact Scala Seq interface");
            }
            int size = ((Integer) sequenceSizeMethod.invoke(raw)).intValue();
            if (size < 0) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", sequenceLabel
                        + " reported a negative size");
            }
            List<Object> elements = new ArrayList<Object>(size);
            for (int index = 0; index < size; index++) {
                Object element = sequenceApplyMethod.invoke(raw, Integer.valueOf(index));
                if (element == null) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", sequenceLabel
                            + " contains null at index " + index);
                }
                elements.add(element);
            }
            return elements;
        }
    }

    private static List<RecipeQueryGroup> groupProjectRedQueries(
            List<Object> sources, String label) throws ExportFailure {
        List<RecipeQueryGroup> groups = new ArrayList<RecipeQueryGroup>();
        for (Object source : sources) {
            ItemStack output = requireStack(((IRecipe) source).getRecipeOutput(),
                    label + " source output");
            RecipeQueryGroup match = null;
            for (RecipeQueryGroup group : groups) {
                if (NEIServerUtils.areStacksSameTypeCrafting(output, group.target)) {
                    if (match != null) {
                        throw new ExportFailure("HANDLER_AMBIGUOUS", label
                                + " crafting-match relation joined two query groups");
                    }
                    match = group;
                }
            }
            if (match == null) {
                match = new RecipeQueryGroup(output.copy(),
                        stackCanonical(output, label + " query target"));
                groups.add(match);
            }
            match.sources.add(source);
        }
        Collections.sort(groups, new Comparator<RecipeQueryGroup>() {
            @Override
            public int compare(RecipeQueryGroup left, RecipeQueryGroup right) {
                return left.targetCanonical.compareTo(right.targetCanonical);
            }
        });
        for (int left = 0; left < groups.size(); left++) {
            for (int right = left + 1; right < groups.size(); right++) {
                if (NEIServerUtils.areStacksSameTypeCrafting(
                        groups.get(left).target, groups.get(right).target)) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", label
                            + " query grouping is not disjoint");
                }
            }
        }
        return groups;
    }

    private static void requireBotaniaPage(
            TemplateRecipeHandler handler, int index, Block special, Block floatingBase,
            ItemStack expectedResult, String label) throws ExportFailure {
        List<PositionedStack> inputs = positionedList(
                handler.getIngredientStacks(index), label + " ingredients");
        if (inputs.size() != 2) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " must contain exactly two ingredients");
        }
        boolean sawFloatingBase = false;
        boolean sawSpecial = false;
        for (PositionedStack positioned : inputs) {
            if (positioned.items == null || positioned.items.length == 0) {
                throw new ExportFailure("RECIPE_SEMANTICS", label
                        + " ingredient has no alternatives");
            }
            boolean allFloating = true;
            boolean hasExpectedSpecial = false;
            for (ItemStack alternative : positioned.items) {
                ItemStack stack = requireStack(alternative, label + " ingredient");
                allFloating &= stack.getItem() == Item.getItemFromBlock(floatingBase);
                hasExpectedSpecial |= stack.getItem() == Item.getItemFromBlock(special)
                        && stack.getTagCompound() != null
                        && expectedResult.getTagCompound() != null
                        && NbtCanonicalizer.canonical(stack.getTagCompound()).equals(
                        NbtCanonicalizer.canonical(expectedResult.getTagCompound()));
            }
            sawFloatingBase |= allFloating;
            sawSpecial |= hasExpectedSpecial;
        }
        if (!sawFloatingBase || !sawSpecial) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " lost its floating-island or exact special-flower input");
        }
    }

    private static void canonicalizeTargets(List<QueryTarget> targets, String label)
            throws ExportFailure {
        TreeMap<String, QueryTarget> byIdentity = new TreeMap<String, QueryTarget>();
        for (QueryTarget target : targets) {
            String identity = stackCanonical(target.stack, label + " target");
            QueryTarget previous = byIdentity.put(identity, target);
            if (previous != null) {
                throw new ExportFailure("HANDLER_DUPLICATE", label
                        + " produced duplicate query targets " + previous.sourceRow
                        + " and " + target.sourceRow + ": " + identity);
            }
        }
        targets.clear();
        targets.addAll(byIdentity.values());
    }

    private static PageCorpus canonicalizeLoadedPages(
            TemplateRecipeHandler handler, String label) throws ExportFailure {
        TreeMap<String, Object> ordered = new TreeMap<String, Object>();
        for (int index = 0; index < handler.numRecipes(); index++) {
            String key = pageCanonical(handler, index, label + " page #" + index);
            Object previous = ordered.put(key, handler.arecipes.get(index));
            if (previous != null) {
                throw new ExportFailure("HANDLER_DUPLICATE", label
                        + " contains two indistinguishable canonical pages");
            }
        }
        handler.arecipes.clear();
        for (Object page : ordered.values()) {
            addRawPage(handler, page);
        }
        List<String> rows = new ArrayList<String>(ordered.keySet());
        return new PageCorpus(rows, fingerprint(label + ":loaded-pages-v1", rows));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addRawPage(TemplateRecipeHandler handler, Object page) {
        ((List) handler.arecipes).add(page);
    }

    private static String pageCanonical(
            TemplateRecipeHandler handler, int index, String label) throws ExportFailure {
        List<String> inputs = positionedRole(
                handler.getIngredientStacks(index), label + " inputs");
        List<String> others = positionedRole(
                handler.getOtherStacks(index), label + " other stacks");
        PositionedStack result = handler.getResultStack(index);
        if (result == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", label + " has no result");
        }
        StringBuilder canonical = new StringBuilder(2048);
        canonical.append("page-v1");
        appendRows(canonical, "inputs", inputs);
        appendRows(canonical, "others", others);
        canonical.append("result").append(framed(positionedCanonical(result,
                label + " result")));
        return canonical.toString();
    }

    private static List<String> positionedRole(Object raw, String label)
            throws ExportFailure {
        List<PositionedStack> positioned = positionedList(raw, label);
        List<String> rows = new ArrayList<String>(positioned.size());
        for (int index = 0; index < positioned.size(); index++) {
            rows.add(positionedCanonical(positioned.get(index), label + " #" + index));
        }
        Collections.sort(rows);
        return rows;
    }

    private static List<PositionedStack> positionedList(Object raw, String label)
            throws ExportFailure {
        if (!(raw instanceof List)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label + " is not a List");
        }
        List<PositionedStack> result = new ArrayList<PositionedStack>();
        for (Object value : (List<?>) raw) {
            if (!(value instanceof PositionedStack)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", label
                        + " contains a non-PositionedStack value");
            }
            result.add((PositionedStack) value);
        }
        return result;
    }

    private static String positionedCanonical(PositionedStack positioned, String label)
            throws ExportFailure {
        if (positioned == null || positioned.items == null
                || positioned.items.length == 0 || positioned.item == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " has no complete PositionedStack state");
        }
        List<String> alternatives = new ArrayList<String>(positioned.items.length);
        boolean currentIsAlternative = false;
        String current = stackCanonical(positioned.item, label + " current");
        for (int index = 0; index < positioned.items.length; index++) {
            String alternative = stackCanonical(
                    positioned.items[index], label + " alternative #" + index);
            alternatives.add(alternative);
            currentIsAlternative |= current.equals(alternative);
        }
        if (!currentIsAlternative) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " current item is absent from its alternatives");
        }
        Collections.sort(alternatives);
        StringBuilder canonical = new StringBuilder(512);
        canonical.append(positioned.relx).append(',').append(positioned.rely);
        appendRows(canonical, "alternatives", alternatives);
        return canonical.toString();
    }

    private static String stackCanonical(ItemStack stack, String label)
            throws ExportFailure {
        try {
            ItemStack checked = requireStack(stack, label);
            StackIdentity identity = StackIdentity.of(checked);
            return identity.key + "|amount=" + identity.amount;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " has no canonical stack identity", error);
        }
    }

    private static TemplateRecipeHandler freshExact(
            TemplateRecipeHandler prototype, String expectedClass) throws ExportFailure {
        requirePrototypeIdentity(prototype, expectedClass);
        try {
            Constructor<?> constructor = requireFreshConstructorShape(prototype.getClass());
            Object raw = constructor.newInstance();
            if (!(raw instanceof TemplateRecipeHandler)
                    || raw == prototype || raw.getClass() != prototype.getClass()) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", expectedClass
                        + " public constructor did not create one exact fresh handler");
            }
            TemplateRecipeHandler fresh = (TemplateRecipeHandler) raw;
            requireCount(expectedClass + " fresh recipe count", 0, fresh.numRecipes());
            if (fresh.arecipes == prototype.arecipes
                    || !prototype.getHandlerId().equals(fresh.getHandlerId())
                    || !sameNullable(prototype.getOverlayIdentifier(),
                    fresh.getOverlayIdentifier())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", expectedClass
                        + " fresh handler identity/storage contract drifted");
            }
            return fresh;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    expectedClass + " fresh construction failed", unwrap(error));
        }
    }

    private static Constructor<?> requireFreshConstructorShape(Class<?> type)
            throws ExportFailure {
        try {
            Constructor<?> constructor = type.getConstructor();
            if (constructor.getDeclaringClass() != type
                    || !Modifier.isPublic(constructor.getModifiers())
                    || constructor.isSynthetic()) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName()
                        + " must retain one direct public non-synthetic no-arg constructor");
            }
            return constructor;
        } catch (NoSuchMethodException error) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName()
                    + " has no public no-arg constructor", error);
        }
    }

    private static void requireDirectItemQueryMethod(Class<?> type) throws ExportFailure {
        try {
            Method method = type.getMethod("loadCraftingRecipes", ItemStack.class);
            if (method.getDeclaringClass() != type
                    || method.getReturnType() != void.class
                    || Modifier.isStatic(method.getModifiers())
                    || !Modifier.isPublic(method.getModifiers())
                    || method.isBridge() || method.isSynthetic()) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName()
                        + ".loadCraftingRecipes(ItemStack) declaration drifted");
            }
        } catch (NoSuchMethodException error) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName()
                    + " has no exact public item-query method", error);
        }
    }

    private static void requirePrototypeIdentity(
            ICraftingHandler prototype, String expectedClass) throws ExportFailure {
        if (prototype == null || !expectedClass.equals(prototype.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", "expected exact "
                    + expectedClass + " prototype, got "
                    + (prototype == null ? "null" : prototype.getClass().getName()));
        }
        requireCount(expectedClass + " prototype recipe count", 0, prototype.numRecipes());
        String handlerId = prototype.getHandlerId();
        if (!expectedClass.equals(handlerId)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", expectedClass
                    + " handler ID drifted to " + handlerId);
        }
    }

    private static void requireResultIdentity(
            TemplateRecipeHandler handler, int index, ItemStack expected, String label)
            throws ExportFailure {
        PositionedStack result = handler.getResultStack(index);
        if (result == null || result.item == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", label + " has no result stack");
        }
        StackIdentity expectedIdentity = StackIdentity.of(requireStack(expected, label));
        StackIdentity actual = StackIdentity.of(requireStack(result.item, label + " result"));
        if (!expectedIdentity.sameLogicalIdentity(actual)) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " result does not match its authoritative query target");
        }
    }

    private static List<?> craftingRecipes() throws ExportFailure {
        Object raw = CraftingManager.getInstance().getRecipeList();
        if (!(raw instanceof List)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "CraftingManager recipe registry is not a List");
        }
        return (List<?>) raw;
    }

    private static Item scalaModuleItem(ClassLoader loader, String className)
            throws Exception {
        Class<?> type = Class.forName(className, false, loader);
        Field module = exactPublicField(type, "MODULE$", type);
        if (!Modifier.isStatic(module.getModifiers())
                || !Modifier.isFinal(module.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    className + ".MODULE$ modifier contract drifted");
        }
        Object value = module.get(null);
        if (!(value instanceof Item) || value.getClass() != type) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    className + ".MODULE$ is not the exact registered Item singleton");
        }
        return (Item) value;
    }

    private static Item publicStaticItem(Class<?> owner, String name) throws Exception {
        Field field = exactPublicField(owner, name, Item.class);
        requireStatic(field, owner.getName() + "." + name);
        Object value = field.get(null);
        if (!(value instanceof Item)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    owner.getName() + "." + name + " is null or not an Item");
        }
        return (Item) value;
    }

    private static Block publicStaticBlock(Class<?> owner, String name) throws Exception {
        Field field = exactPublicField(owner, name, Block.class);
        requireStatic(field, owner.getName() + "." + name);
        Object value = field.get(null);
        if (!(value instanceof Block)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    owner.getName() + "." + name + " is null or not a Block");
        }
        return (Block) value;
    }

    private static Set<String> stringSet(Object raw, String label, boolean allowEmptyString)
            throws ExportFailure {
        if (!(raw instanceof Set)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label + " is not a Set");
        }
        TreeSet<String> result = new TreeSet<String>();
        for (Object value : (Set<?>) raw) {
            if (!(value instanceof String)
                    || (!allowEmptyString && ((String) value).trim().isEmpty())
                    || !result.add((String) value)) {
                throw new ExportFailure("HANDLER_DUPLICATE", label
                        + " contains a malformed or duplicate value");
            }
        }
        return result;
    }

    private static ItemStack requireStack(Object raw, String label) throws ExportFailure {
        if (!(raw instanceof ItemStack) || ((ItemStack) raw).getItem() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " is null or not an initialized ItemStack");
        }
        return (ItemStack) raw;
    }

    private static void requireItem(ItemStack stack, Item expected, String label)
            throws ExportFailure {
        if (expected == null || requireStack(stack, label).getItem() != expected) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " changed its exact item binding");
        }
    }

    private static void requireSourceLoadedParity(AuditRow row, String label)
            throws ExportFailure {
        if (row.sourceCount <= 0 || row.sourceCount != row.loadedCount) {
            throw new ExportFailure("HANDLER_UNLOADED", label
                    + " source/loaded cardinality mismatch; source=" + row.sourceCount
                    + ", loaded=" + row.loadedCount);
        }
    }

    private static void requireCount(String label, int expected, int actual)
            throws ExportFailure {
        if (actual != expected) {
            throw new ExportFailure("HANDLER_UNLOADED", label
                    + " drifted; expected " + expected + ", got " + actual);
        }
    }

    private static Field exactPublicField(Class<?> owner, String name, Class<?> type)
            throws Exception {
        Field field = owner.getField(name);
        if (field.getDeclaringClass() != owner || field.getType() != type
                || !Modifier.isPublic(field.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", owner.getName() + "." + name
                    + " public field contract drifted");
        }
        return field;
    }

    private static Field exactDeclaredField(Class<?> owner, String name, Class<?> type)
            throws Exception {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != type) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", owner.getName() + "." + name
                    + " field type drifted");
        }
        field.setAccessible(true);
        return field;
    }

    private static Method exactPublicMethod(
            Class<?> owner, String name, Class<?> returnType, Class<?>... parameters)
            throws Exception {
        Method method = owner.getMethod(name, parameters);
        if (method.getReturnType() != returnType
                || !Modifier.isPublic(method.getModifiers())
                || method.isBridge() || method.isSynthetic()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", owner.getName() + "." + name
                    + " public method contract drifted");
        }
        return method;
    }

    private static Method exactPublicArrayMethod(
            Class<?> owner, String name, String componentName) throws Exception {
        Method method = owner.getMethod(name);
        Class<?> returned = method.getReturnType();
        if (!returned.isArray()
                || !componentName.equals(returned.getComponentType().getName())
                || !Modifier.isPublic(method.getModifiers())
                || method.isBridge() || method.isSynthetic()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", owner.getName() + "." + name
                    + " array-return contract drifted");
        }
        return method;
    }

    private static void requireStatic(Field field, String label) throws ExportFailure {
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label + " is no longer static");
        }
    }

    private static void requireStatic(Method method, String label) throws ExportFailure {
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label + " is no longer static");
        }
    }

    private static void requireDirectInstanceMethod(
            Method method, Class<?> owner, String label) throws ExportFailure {
        if (method.getDeclaringClass() != owner
                || Modifier.isStatic(method.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label
                    + " is no longer a direct instance declaration");
        }
    }

    private static boolean sameNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String fingerprint(String domain, Collection<String> rows) {
        List<String> sorted = new ArrayList<String>(rows);
        Collections.sort(sorted);
        StringBuilder canonical = new StringBuilder(domain.length() + sorted.size() * 128);
        canonical.append(framed(domain)).append(sorted.size()).append(';');
        for (String row : sorted) {
            canonical.append(framed(row));
        }
        return Naming.sha256(canonical.toString());
    }

    private static List<String> auditLines(List<AuditRow> rows) {
        List<String> lines = new ArrayList<String>(rows.size());
        for (AuditRow row : rows) {
            lines.add(row.canonical());
        }
        return lines;
    }

    private static void appendRows(
            StringBuilder canonical, String role, List<String> rows) {
        canonical.append(framed(role)).append(rows.size()).append(';');
        for (String row : rows) {
            canonical.append(framed(row));
        }
    }

    private static String framed(String value) {
        if (value == null) {
            throw new IllegalArgumentException("canonical value must not be null");
        }
        return value.length() + ":" + value;
    }

    private static void logAudit(AuditRow row) {
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Query-closure audit: handler={}, contract={}, "
                        + "sourceCount={}, sourceFingerprint={}, loadedCount={}, "
                        + "loadedFingerprint={}, registryState={}",
                row.handlerClass, row.contract, row.sourceCount, row.sourceFingerprint,
                row.loadedCount, row.loadedFingerprint, row.registryState);
    }

    private static String diagnostic(Throwable error) {
        Throwable unwrapped = unwrap(error);
        String message = unwrapped.getMessage();
        return message == null ? unwrapped.toString() : message;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException
                && ((InvocationTargetException) current).getCause() != null) {
            current = ((InvocationTargetException) current).getCause();
        }
        return current;
    }

    static final class DiscoveryInventory {
        final List<AuditRow> rows;
        final String fingerprint;

        DiscoveryInventory(List<AuditRow> rows, String fingerprint) {
            this.rows = Collections.unmodifiableList(new ArrayList<AuditRow>(rows));
            this.fingerprint = fingerprint;
        }
    }

    static final class AuditRow {
        static final Comparator<AuditRow> ORDER = new Comparator<AuditRow>() {
            @Override
            public int compare(AuditRow left, AuditRow right) {
                return left.handlerClass.compareTo(right.handlerClass);
            }
        };

        final String handlerClass;
        final String contract;
        final int sourceCount;
        final String sourceFingerprint;
        final int loadedCount;
        final String loadedFingerprint;
        final String registryState;

        AuditRow(String handlerClass, String contract,
                 int sourceCount, String sourceFingerprint,
                 int loadedCount, String loadedFingerprint,
                 String registryState) {
            this.handlerClass = handlerClass;
            this.contract = contract;
            this.sourceCount = sourceCount;
            this.sourceFingerprint = sourceFingerprint;
            this.loadedCount = loadedCount;
            this.loadedFingerprint = loadedFingerprint;
            this.registryState = registryState;
        }

        String canonical() {
            return framed(handlerClass) + framed(contract)
                    + sourceCount + ';' + framed(sourceFingerprint)
                    + loadedCount + ';' + framed(loadedFingerprint)
                    + framed(registryState);
        }
    }

    static final class Promotion {
        final int sourceCount;
        final String sourceFingerprint;
        final int loadedCount;
        final String loadedFingerprint;

        Promotion(int sourceCount, String sourceFingerprint,
                  int loadedCount, String loadedFingerprint) {
            this.sourceCount = sourceCount;
            this.sourceFingerprint = sourceFingerprint;
            this.loadedCount = loadedCount;
            this.loadedFingerprint = loadedFingerprint;
        }

        static Promotion unpromoted() {
            return new Promotion(-1, null, -1, null);
        }

        String mismatch(AuditRow observed) {
            if (sourceCount == observed.sourceCount
                    && loadedCount == observed.loadedCount
                    && sourceFingerprint != null
                    && sourceFingerprint.equals(observed.sourceFingerprint)
                    && loadedFingerprint != null
                    && loadedFingerprint.equals(observed.loadedFingerprint)) {
                return null;
            }
            return "corpus is changed or unpromoted; observed sourceCount="
                    + observed.sourceCount + ", sourceFingerprint="
                    + observed.sourceFingerprint + ", loadedCount="
                    + observed.loadedCount + ", loadedFingerprint="
                    + observed.loadedFingerprint + "; expected sourceCount="
                    + sourceCount + ", sourceFingerprint=" + sourceFingerprint
                    + ", loadedCount=" + loadedCount + ", loadedFingerprint="
                    + loadedFingerprint;
        }
    }

    private static final class Capture {
        final TemplateRecipeHandler loaded;
        final AuditRow row;

        Capture(TemplateRecipeHandler loaded, AuditRow row) {
            this.loaded = loaded;
            this.row = row;
        }
    }

    private static final class QueryTarget {
        final String sourceRow;
        final ItemStack stack;

        QueryTarget(String sourceRow, ItemStack stack) {
            this.sourceRow = sourceRow;
            this.stack = stack;
        }
    }

    private static final class RecipeQueryGroup {
        final ItemStack target;
        final String targetCanonical;
        final List<Object> sources = new ArrayList<Object>();

        RecipeQueryGroup(ItemStack target, String targetCanonical) {
            this.target = target;
            this.targetCanonical = targetCanonical;
        }
    }

    private static final class PageCorpus {
        final List<String> rows;
        final String fingerprint;

        PageCorpus(List<String> rows, String fingerprint) {
            this.rows = rows;
            this.fingerprint = fingerprint;
        }
    }

    // Focused pure seams: keep corpus ordering/promotion behavior testable without
    // placing the five third-party mod jars on the unit-test runtime classpath.
    static String fingerprintForTesting(String domain, Collection<String> rows) {
        return fingerprint(domain, rows);
    }

    static List<String> uniqueSortedForTesting(Collection<String> rows)
            throws ExportFailure {
        TreeSet<String> sorted = new TreeSet<String>();
        for (String row : rows) {
            if (row == null || !sorted.add(row)) {
                throw new ExportFailure("HANDLER_DUPLICATE",
                        "test canonical row set contains a null or duplicate row");
            }
        }
        return new ArrayList<String>(sorted);
    }

    static Promotion promotionForTesting(String handlerClass) {
        return PROMOTIONS.get(handlerClass);
    }

}
