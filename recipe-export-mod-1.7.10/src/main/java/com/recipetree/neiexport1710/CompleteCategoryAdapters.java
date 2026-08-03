package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.IUsageHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.google.gson.stream.JsonWriter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Exact, version-pinned adapters and semantic policies for GTNH-specific NEI handlers. */
final class CompleteCategoryAdapters {
    static final String AE2_WORLD_CRAFTING_HANDLER =
            "appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler";
    static final String GALAXYSPACE_ROCKET_HANDLER =
            "galaxyspace.core.nei.RocketRecipeHandler";
    static final String AMUNRA_SHUTTLE_HANDLER =
            "de.katzenpapst.amunra.nei.recipehandler.ARNasaWorkbenchShuttle";
    static final String BETTER_QUESTING_HANDLER =
            "bq_standard.integration.nei.QuestRecipeHandler";
    static final String CROP_HANDLER =
            "speiger.src.crops.prediction.NEIPlugin";
    static final String FORESTRY_BOTTLER_HANDLER =
            "forestry.factory.recipes.nei.NEIHandlerBottler";
    static final String FORESTRY_CARPENTER_HANDLER =
            "forestry.factory.recipes.nei.NEIHandlerCarpenter";
    static final String FORESTRY_FABRICATOR_HANDLER =
            "forestry.factory.recipes.nei.NEIHandlerFabricator";
    static final String FORESTRY_FERMENTER_HANDLER =
            "forestry.factory.recipes.nei.NEIHandlerFermenter";
    static final String FORESTRY_MOISTENER_HANDLER =
            "forestry.factory.recipes.nei.NEIHandlerMoistener";
    static final String FORESTRY_SQUEEZER_HANDLER =
            "forestry.factory.recipes.nei.NEIHandlerSqueezer";
    static final String FORESTRY_STILL_HANDLER =
            "forestry.factory.recipes.nei.NEIHandlerStill";
    static final String GENDUSTRY_LIQUIFIER_HANDLER =
            GendustryMachineSemanticAdapter.LIQUIFIER;
    static final String GENDUSTRY_MUTAGEN_PRODUCER_HANDLER =
            GendustryMachineSemanticAdapter.MUTAGEN_PRODUCER;
    static final String GENDUSTRY_EXTRACTOR_HANDLER =
            GendustryMachineSemanticAdapter.EXTRACTOR;
    static final String GENDUSTRY_REPLICATOR_HANDLER =
            GendustryMachineSemanticAdapter.REPLICATOR;
    static final String GENDUSTRY_TRANSPOSER_HANDLER =
            GendustryMachineSemanticAdapter.TRANSPOSER;
    static final String GENDUSTRY_MUTATRON_HANDLER =
            GendustryMachineSemanticAdapter.MUTATRON;
    static final String GENDUSTRY_SAMPLER_HANDLER =
            GendustryMachineSemanticAdapter.SAMPLER;
    static final String GENDUSTRY_IMPRINTER_HANDLER =
            GendustryMachineSemanticAdapter.IMPRINTER;
    static final String BINNIE_INCUBATOR_HANDLER =
            BinnieIncubatorSemanticAdapter.HANDLER;
    static final String BINNIE_GENEPOOL_HANDLER =
            BinnieGenepoolSemanticAdapter.HANDLER;
    static final String BINNIE_ACCLIMATISER_HANDLER =
            BinnieAcclimatiserInformationalAdapter.HANDLER;
    static final String BINNIE_ANALYSER_HANDLER =
            BinnieAnalyserInformationalAdapter.HANDLER;
    static final String MOBSINFO_MOB_HANDLER =
            MobsInfoSemanticAdapter.HANDLER;
    static final String MOBSINFO_INFERNAL_HANDLER =
            MobsInfoInfernalSemanticAdapter.HANDLER;
    static final String MOBSINFO_VILLAGER_TRADES_HANDLER =
            MobsInfoVillagerTradeSemanticAdapter.HANDLER;
    static final String TCONSTRUCT_MELTING_HANDLER =
            TconstructMeltingSemanticAdapter.HANDLER;
    static final String TCONSTRUCT_ALLOYING_HANDLER =
            TconstructAlloyingSemanticAdapter.HANDLER;
    static final String BUILDCRAFT_REFINERY_HANDLER =
            BuildcraftRefinerySemanticAdapter.HANDLER;
    static final String ENDERIO_VAT_HANDLER =
            EnderIoVatSemanticAdapter.HANDLER;
    static final String GALACTICRAFT_CIRCUIT_FABRICATOR_HANDLER =
            GalacticraftCircuitFabricatorSemanticAdapter.HANDLER;
    static final String IC2_LATHE_HANDLER =
            "ic2.neiIntegration.core.recipehandler.LatheRecipeHandler";
    static final String BBAB_RECIPE_TREE_HANDLER =
            "hellfirepvp.beebetteratbees.client.gui.BBABGuiRecipeTreeHandler";
    static final String FLUID_REGISTRY_INFORMATION_HANDLER =
            "tonius.neiintegration.mods.mcforge.RecipeHandlerFluidRegistry";
    static final String ORE_DICTIONARY_INFORMATION_HANDLER =
            "tonius.neiintegration.mods.mcforge.RecipeHandlerOreDictionary";
    static final String BOTANIA_LEXICA_INFORMATION_HANDLER =
            "vazkii.botania.client.integration.nei.recipe.RecipeHandlerLexicaBotania";
    static final String TCONSTRUCT_TOOL_MATERIAL_INFORMATION_HANDLER =
            "tconstruct.plugins.nei.RecipeHandlerToolMaterials";
    static final String EXTRAUTILITIES_INFO_HANDLER =
            "com.rwtema.extrautils.nei.InfoHandler";
    static final String EXTRAUTILITIES_SOUL_HANDLER =
            "com.rwtema.extrautils.nei.SoulHandler";
    static final String ASPECT_FROM_STACK_HANDLER =
            "ru.timeconqueror.tcneiadditions.nei.AspectFromItemStackHandler";
    static final String ASPECT_COMBINATION_HANDLER =
            "ru.timeconqueror.tcneiadditions.nei.AspectCombinationHandler";
    static final String NEI_CUSTOM_DIAGRAM_GROUP =
            "com.github.dcysteine.neicustomdiagram.api.diagram.DiagramGroup";
    static final String NEI_CUSTOM_CUSTOM_DIAGRAM_GROUP =
            "com.github.dcysteine.neicustomdiagram.api.diagram.CustomDiagramGroup";
    static final String ENDER_STORAGE_CHEST_DIAGRAM_HANDLER =
            "neicustomdiagram.diagramgroup.enderstorage.chestoverview";
    static final String ENDER_STORAGE_TANK_DIAGRAM_HANDLER =
            "neicustomdiagram.diagramgroup.enderstorage.tankoverview";
    static final String ENDER_STORAGE_CHEST_DIAGRAM_DESCRIPTION =
            "This diagram displays ender chest used frequencies and contents.\n"
                    + "Unfortunately, it doesn't work well on servers.";
    static final String ENDER_STORAGE_TANK_DIAGRAM_DESCRIPTION =
            "This diagram displays ender tank used frequencies and contents.\n"
                    + "Unfortunately, it doesn't work on servers.";
    static final String GT_CIRCUIT_DIAGRAM_HANDLER =
            "neicustomdiagram.diagramgroup.gregtech.circuits";
    static final String GT_LENS_DIAGRAM_HANDLER =
            "neicustomdiagram.diagramgroup.gregtech.lenses";
    static final String GT_MATERIAL_PARTS_DIAGRAM_HANDLER =
            "neicustomdiagram.diagramgroup.gregtech.materialparts";
    static final String GT_MATERIAL_TOOLS_DIAGRAM_HANDLER =
            "neicustomdiagram.diagramgroup.gregtech.materialtools";
    static final String GT_ORE_PROCESSING_DIAGRAM_HANDLER =
            "neicustomdiagram.diagramgroup.gregtech.oreprocessing";
    static final String NEI_PROFILER_HANDLER =
            "codechicken.nei.recipe.ProfilerRecipeHandler";

    private static final String QUEST_DATABASE_CLASS =
            "betterquesting.questing.QuestDatabase";
    private static final String AE2_API_CLASS = "appeng.api.AEApi";
    private static final String AE2_API_INTERFACE = "appeng.api.IAppEngApi";
    private static final String AE2_DEFINITIONS_INTERFACE =
            "appeng.api.definitions.IDefinitions";
    private static final String AE2_MATERIALS_INTERFACE =
            "appeng.api.definitions.IMaterials";
    private static final String AE2_ITEM_DEFINITION_INTERFACE =
            "appeng.api.definitions.IItemDefinition";
    private static final String AE2_OPTIONAL_CLASS = "com.google.common.base.Optional";
    private static final String EXTRAUTILITIES_CLASS =
            "com.rwtema.extrautils.ExtraUtils";
    private static final String EXTRAUTILITIES_SOUL_RECIPE_CLASS =
            "com.rwtema.extrautils.crafting.RecipeSoul";
    private static final String DIAGRAM_GROUP_INFO_CLASS =
            "com.github.dcysteine.neicustomdiagram.api.diagram.DiagramGroupInfo";
    private static final String DIAGRAM_MATCHER_CLASS =
            "com.github.dcysteine.neicustomdiagram.api.diagram.matcher.DiagramMatcher";
    private static final String CUSTOM_DIAGRAM_MATCHER_CLASS =
            "com.github.dcysteine.neicustomdiagram.api.diagram.matcher.CustomDiagramMatcher";
    private static final String AE2_MATERIAL_REGISTRY_ID =
            "appliedenergistics2:item.ItemMultiMaterial";
    private static final int EXPECTED_AE2_INFORMATION_PAGES = 8;
    private static final String QUEST_CACHED_CLASS =
            BETTER_QUESTING_HANDLER + "$CachedQuestRecipe";
    private static final String QUEST_INTERFACE_CLASS =
            "betterquesting.api.questing.IQuest";
    private static final String QUEST_DATABASE_API_CLASS =
            "betterquesting.api2.storage.IDatabase";
    private static final String QUEST_DATABASE_NBT_API_CLASS =
            "betterquesting.api2.storage.IDatabaseNBT";
    private static final String QUEST_DATABASE_ENTRY_CLASS =
            "betterquesting.api2.storage.DBEntry";
    private static final String QUEST_TASK_ITEM_INPUT_CLASS =
            "bq_standard.tasks.ITaskItemInput";
    private static final String QUEST_REWARD_ITEM_OUTPUT_CLASS =
            "bq_standard.rewards.IRewardItemOutput";
    private static final String QUEST_REWARD_CHOICE_CLASS =
            "bq_standard.rewards.RewardChoice";
    private static final String QUEST_OPTIONAL_RETRIEVAL_CLASS =
            "bq_standard.tasks.TaskOptionalRetrieval";
    private static final String QUEST_BIG_ITEM_STACK_CLASS =
            "betterquesting.api.utils.BigItemStack";
    private static final String QUEST_ORE_INGREDIENT_CLASS =
            "betterquesting.api2.utils.OreIngredient";
    private static final String QUEST_NATIVE_PROPERTIES_CLASS =
            "betterquesting.api.properties.NativeProps";
    private static final String QUEST_PROPERTY_TYPE_CLASS =
            "betterquesting.api.properties.IPropertyType";
    private static final String QUEST_PROPERTY_CONTAINER_CLASS =
            "betterquesting.api.properties.IPropertyContainer";
    private static final String QUEST_LOGIC_ENUM_CLASS =
            "betterquesting.api.enums.EnumLogic";
    private static final int EXPECTED_QUEST_DATABASE_ENTRIES = 3739;
    private static final int EXPECTED_QUEST_ITEM_PAGES = 3632;
    private static final int EXPECTED_QUEST_BOTH = 2984;
    private static final int EXPECTED_QUEST_INPUT_ONLY = 488;
    private static final int EXPECTED_QUEST_OUTPUT_ONLY = 160;
    private static final int EXPECTED_QUEST_NO_ITEM_SEMANTICS = 107;
    private static final int EXPECTED_QUEST_INPUT_SLOTS = 8987;
    private static final int EXPECTED_QUEST_OUTPUT_SLOTS = 7213;
    private static final int EXPECTED_QUEST_CHOICE_PROVIDERS = 957;
    private static final int EXPECTED_QUEST_CHOICE_ENTRIES = 3062;
    private static final int EXPECTED_QUEST_REGULAR_REWARD_ENTRIES = 6256;
    private static final int EXPECTED_QUEST_FLAT_REWARD_ENTRIES = 9318;
    private static final int EXPECTED_QUEST_TASK_LOGIC_OR = 136;
    private static final int EXPECTED_QUEST_OPTIONAL_RETRIEVAL_TASKS = 1267;
    private static final int EXPECTED_QUEST_OVER_16_INPUT_PAGES = 18;
    private static final int EXPECTED_QUEST_OVER_16_OUTPUT_PAGES = 0;
    private static final int EXPECTED_QUEST_MAX_INPUT_SLOTS = 55;
    private static final int EXPECTED_QUEST_MAX_OUTPUT_SLOTS = 11;
    private static final String EXPECTED_QUEST_UUID_SHA256 =
            "0bf4f81be17f1f3068adfd39bfc752b1ec90c185671d18111901e590a9ea8294";
    // Two independent pinned GTNH 2.8.4 client boots produced this exact
    // informational-corpus fingerprint before it was promoted.
    private static final String EXPECTED_QUEST_SEMANTICS_SHA256 =
            "307743e619df7d62fdfbced37eba8216bdd1aa64030d892b9f1522b90576fdfb";

    private static final String CROP_BREEDER_CLASS =
            "speiger.src.crops.prediction.Breeder";
    private static final String CROP_RESULT_CLASS =
            "speiger.src.crops.prediction.BreedResult";
    private static final String CROP_CACHED_CLASS = CROP_HANDLER + "$BreedRecipe";
    private static final String CROP_MOD_CLASS = "speiger.src.crops.IC2NeiPlugin";
    private static final String CROP_RATIO_CLASS =
            "speiger.src.crops.inventory.GuiBreeding";
    private static final int EXPECTED_CROP_CARD_COUNT = 159;
    private static final String ORIGINAL_CROP_WORKER_NAME = "Crop Calulator Watcher";
    private static final String EXPORTER_CROP_WORKER_NAME =
            "Recipe Tree GTNH 2.8.4 Crop Cache Recompute";
    // Two independent pinned GTNH 2.8.4 client boots produced this exact final
    // deterministic graph-corpus fingerprint. The upstream cache fingerprint is
    // intentionally not promoted because its identity-hash/tree-bin behavior is
    // nondeterministic.
    private static final String EXPECTED_CROP_REPLAY_SHA256 =
            "e0a9cb22e8f73e699f5d06fb29b72b452311279856dd06bd7c6911a62d349a2a";

    private static final Map<String, PolicySpec> POLICY_SPECS;
    private static final Map<String, SpaceRecipeSpec> SPACE_RECIPE_SPECS;
    private static final Map<String, DiagramPolicySpec> DIAGRAM_POLICY_SPECS;

    private static volatile Thread exporterCropWorker;
    private static volatile Throwable exporterCropFailure;
    private static volatile Object exporterCropBreeder;
    private static volatile boolean exporterCropRecomputeStarted;
    /**
     * First immutable deterministic-replay snapshot captured after the exporter-owned worker
     * terminates. Normal readiness polling/category construction reuse it; explicit integrity
     * gates revalidate the compact derivation basis and upstream diagnostic cache.
     */
    private static volatile CropSnapshot completedCropSnapshot;
    private static volatile Object completedQuestDatabase;
    private static volatile QuestSemanticCorpus completedQuestSemanticCorpus;
    private static volatile SpaceRecipeSnapshot completedSpaceRecipeSnapshot;
    private static final Map<ICraftingHandler, List<RecipeSemanticOverride>>
            SEMANTIC_OVERRIDES_BY_HANDLER =
            new IdentityHashMap<ICraftingHandler, List<RecipeSemanticOverride>>();

    static {
        Map<String, PolicySpec> specs = new LinkedHashMap<String, PolicySpec>();
        // AE2 exposes these pages only for an item-targeted query. Preserve the
        // complete wildcard-query closure as Info pages; the viewer excludes this
        // category from executable recipe graphs and material totals.
        putPolicy(specs, AE2_WORLD_CRAFTING_HANDLER, AE2_WORLD_CRAFTING_HANDLER,
                Adapter.AE2_WORLD_CRAFTING,
                "adapted-informational-category",
                "adapter:ae2-in-world-crafting-wildcard-query-closure-v1",
                null, null, null);
        // These are complete item references for browsing, not executable quest
        // recipes: AND/OR logic, optionality, consumption, and choice selection
        // cannot be represented as v1 material-tree semantics.
        putPolicy(specs, BETTER_QUESTING_HANDLER, BETTER_QUESTING_HANDLER,
                Adapter.BETTER_QUESTING,
                "adapted-informational-category",
                "adapter:betterquesting-complete-item-reference-pages-v1",
                "bq_quest", null, "bq_quest");
        putPolicy(specs, CROP_HANDLER, CROP_HANDLER, Adapter.IC2_CROP_BREEDING,
                "adapted-complete-category",
                "adapter:ic2-crop-deterministic-query-bucket-closure-nei-presentation-v2",
                null, null, null);

        // Forestry's seven fluid-aware factory handlers keep authoritative fluid slots outside
        // ICraftingHandler's item-only result/ingredient API. The shared adapter snapshots and
        // validates each handler's exact role topology, expands correlated Squeezer container
        // variants, and represents dynamic/amortized machine flows explicitly with amount zero.
        putForestryFluidPolicy(specs, FORESTRY_BOTTLER_HANDLER, "forestry.bottler", "bottler");
        putForestryFluidPolicy(specs, FORESTRY_CARPENTER_HANDLER, "forestry.carpenter", "carpenter");
        putForestryFluidPolicy(specs, FORESTRY_FABRICATOR_HANDLER, "forestry.fabricator", "fabricator");
        putForestryFluidPolicy(specs, FORESTRY_FERMENTER_HANDLER, "forestry.fermenter", "fermenter");
        putForestryFluidPolicy(specs, FORESTRY_MOISTENER_HANDLER, "forestry.moistener", "moistener");
        putForestryFluidPolicy(specs, FORESTRY_SQUEEZER_HANDLER, "forestry.squeezer", "squeezer");
        putForestryFluidPolicy(specs, FORESTRY_STILL_HANDLER, "forestry.still", "still");

        // Gendustry's machine handlers use a shared Scala cached-recipe family. Five keep
        // authoritative fluid transitions outside NEI's generic item APIs; the family also
        // marks reusable templates and conditional labware only through executable machine
        // code/tooltips. One exact adapter binds all eight roles without inventing fluids for
        // the three genuinely item-only handlers.
        putGendustryMachinePolicy(specs, GENDUSTRY_LIQUIFIER_HANDLER);
        putGendustryMachinePolicy(specs, GENDUSTRY_MUTAGEN_PRODUCER_HANDLER);
        putGendustryMachinePolicy(specs, GENDUSTRY_EXTRACTOR_HANDLER);
        putGendustryMachinePolicy(specs, GENDUSTRY_REPLICATOR_HANDLER);
        putGendustryMachinePolicy(specs, GENDUSTRY_TRANSPOSER_HANDLER);
        putGendustryMachinePolicy(specs, GENDUSTRY_MUTATRON_HANDLER);
        putGendustryMachinePolicy(specs, GENDUSTRY_SAMPLER_HANDLER);
        putGendustryMachinePolicy(specs, GENDUSTRY_IMPRINTER_HANDLER);
        putPolicy(specs, BINNIE_INCUBATOR_HANDLER, BINNIE_INCUBATOR_HANDLER,
                Adapter.BINNIE_INCUBATOR_SEMANTICS,
                "adapted-complete-category",
                "adapter:binnie-genetics-2.5.24-incubator-fluid-semantics-v1",
                BinnieIncubatorSemanticAdapter.OPERATION, null,
                BinnieIncubatorSemanticAdapter.OPERATION);
        putPolicy(specs, BINNIE_GENEPOOL_HANDLER, BINNIE_GENEPOOL_HANDLER,
                Adapter.BINNIE_GENEPOOL_SEMANTICS,
                "adapted-complete-category",
                BinnieGenepoolSemanticAdapter.CONTRACT,
                BinnieGenepoolSemanticAdapter.OPERATION, null,
                BinnieGenepoolSemanticAdapter.OPERATION);
        // The Acclimatiser changes a live organism's tolerance property in place. Its NEI
        // pages intentionally have target/resource inputs and no ItemStack result; preserve
        // them for browsing without inventing a craftable output or material-graph edge.
        putPolicy(specs, BINNIE_ACCLIMATISER_HANDLER, BINNIE_ACCLIMATISER_HANDLER,
                Adapter.BINNIE_ACCLIMATISER_INFORMATIONAL,
                "adapted-informational-category",
                BinnieAcclimatiserInformationalAdapter.CONTRACT,
                BinnieAcclimatiserInformationalAdapter.OPERATION, null,
                BinnieAcclimatiserInformationalAdapter.OPERATION);
        // The Analyser consumes DNA dye while mutating an organism's analysed-state metadata
        // in place. Preserve its exact supported-target inventory for browsing, but do not
        // fabricate a material output or dependency-graph edge.
        putPolicy(specs, BINNIE_ANALYSER_HANDLER, BINNIE_ANALYSER_HANDLER,
                Adapter.BINNIE_ANALYSER_INFORMATIONAL,
                "adapted-informational-category",
                BinnieAnalyserInformationalAdapter.CONTRACT,
                BinnieAnalyserInformationalAdapter.OPERATION, null,
                BinnieAnalyserInformationalAdapter.OPERATION);
        // MobsInfo exposes authoritative drops through a custom getOutputs() API and uses
        // the ordinary ingredient only as a mob selector. Preserve those item references
        // for browsing, but exclude this informational category from executable graphs.
        putPolicy(specs, MOBSINFO_MOB_HANDLER, MOBSINFO_MOB_HANDLER,
                Adapter.MOBSINFO_INFORMATIONAL_SEMANTICS,
                "adapted-informational-category",
                "adapter:mobsinfo-0.5.6-item-reference-semantics-v2",
                MobsInfoSemanticAdapter.OPERATION, null,
                MobsInfoSemanticAdapter.OPERATION);
        // Infernal Mobs exposes one tiered, probabilistic drop table through its custom
        // getOutputs() API. Preserve the item references and unconditional probabilities
        // for browsing while keeping the informational category out of material graphs.
        putPolicy(specs, MOBSINFO_INFERNAL_HANDLER, MOBSINFO_INFERNAL_HANDLER,
                Adapter.MOBSINFO_INFERNAL_INFORMATIONAL_SEMANTICS,
                "adapted-informational-category",
                "adapter:mobsinfo-0.5.6-infernal-drop-information-v1",
                MobsInfoInfernalSemanticAdapter.OPERATION, null, null);
        // One NEI page groups a profession's independent trades and exposes them only through
        // custom getInputs()/getOutputs() methods. Preserve every correlated source row as
        // browsable item references, but do not misrepresent the whole page as one executable
        // material transformation in dependency graphs.
        putPolicy(specs, MOBSINFO_VILLAGER_TRADES_HANDLER,
                MOBSINFO_VILLAGER_TRADES_HANDLER,
                Adapter.MOBSINFO_VILLAGER_INFORMATIONAL_SEMANTICS,
                "adapted-informational-category",
                MobsInfoVillagerTradeSemanticAdapter.CONTRACT,
                MobsInfoVillagerTradeSemanticAdapter.OPERATION, null,
                MobsInfoVillagerTradeSemanticAdapter.OPERATION);
        // TConstruct renders melting outputs only through its typed FluidTankElement list;
        // ICraftingHandler.getResult()/getOtherStacks() are intentionally empty.
        putPolicy(specs, TCONSTRUCT_MELTING_HANDLER, TCONSTRUCT_MELTING_HANDLER,
                Adapter.TCONSTRUCT_MELTING_FLUID_SEMANTICS,
                "adapted-complete-category",
                "adapter:tconstruct-1.13.57-melting-fluid-semantics-v1",
                null, null, TconstructMeltingSemanticAdapter.OPERATION);
        putPolicy(specs, TCONSTRUCT_ALLOYING_HANDLER, TCONSTRUCT_ALLOYING_HANDLER,
                Adapter.TCONSTRUCT_ALLOYING_FLUID_SEMANTICS,
                "adapted-complete-category",
                "adapter:tconstruct-1.13.57-alloying-fluid-semantics-v1",
                null, null, TconstructAlloyingSemanticAdapter.OPERATION);
        // BuildCraft's Refinery keeps both input and output fluids in positioned tanks;
        // its ordinary CachedRecipe item result and ingredient APIs are intentionally empty.
        putPolicy(specs, BUILDCRAFT_REFINERY_HANDLER, BUILDCRAFT_REFINERY_HANDLER,
                Adapter.BUILDCRAFT_REFINERY_FLUID_SEMANTICS,
                "adapted-complete-category",
                "adapter:buildcraft-compat-7.1.18-refinery-fluid-semantics-v1",
                null, null, BuildcraftRefinerySemanticAdapter.OPERATION);
        // Ender IO's Vat keeps its real result and input in private FluidStack fields;
        // CachedRecipe.getResult() is intentionally null. Preserve every multiplier-derived
        // fluid quantity alongside the two item-alternative slots.
        putPolicy(specs, ENDERIO_VAT_HANDLER, ENDERIO_VAT_HANDLER,
                Adapter.ENDERIO_VAT_FLUID_SEMANTICS,
                "adapted-complete-category",
                "adapter:enderio-2.9.28-vat-fluid-semantics-v1",
                EnderIoVatSemanticAdapter.OPERATION, null,
                EnderIoVatSemanticAdapter.OPERATION);
        // Galacticraft deliberately hides the result for the first 51 ticks of each
        // 70-tick animation cycle. Read the immutable CachedCircuitRecipe result for graph
        // semantics and pin the preview to its visible phase; wall-clock timing must not
        // decide whether the exported recipe has an output.
        putPolicy(specs, GALACTICRAFT_CIRCUIT_FABRICATOR_HANDLER,
                GALACTICRAFT_CIRCUIT_FABRICATOR_HANDLER,
                Adapter.GALACTICRAFT_CIRCUIT_FABRICATOR_SEMANTICS,
                "adapted-complete-category",
                GalacticraftCircuitFabricatorSemanticAdapter.CONTRACT,
                null, null, GalacticraftCircuitFabricatorSemanticAdapter.OPERATION);

        // Galacticraft ships four dormant handler classes and stale HandlerInfo CSV rows,
        // but GTNH 2.8.4 never registers those classes as crafting prototypes. Do not
        // synthesize categories outside NEI's authoritative live registry. GalaxySpace and
        // Amun-Ra do register complete NASA-workbench corpora backed by identity-ordered Sets;
        // their exact adapters canonicalize page order before source indexes are assigned.
        Map<String, SpaceRecipeSpec> spaceSpecs =
                new LinkedHashMap<String, SpaceRecipeSpec>();
        for (int tier = 1; tier <= 8; tier++) {
            String handlerId = "galaxyspace.core.nei.rocket.RocketT" + tier
                    + "RecipeHandler";
            String queryId = "galacticraft.rocketT" + tier;
            String contract = "adapter:galaxyspace-rocket-t" + tier
                    + "-canonical-set-query-v1";
            spaceSpecs.put(handlerId, new SpaceRecipeSpec(
                    GALAXYSPACE_ROCKET_HANDLER, queryId, 4, tier));
            putPolicy(specs, handlerId, GALAXYSPACE_ROCKET_HANDLER,
                    Adapter.PINNED_SPACE_RECIPE_ID, "adapted-complete-category",
                    contract, null, null, null);
        }
        spaceSpecs.put(AMUNRA_SHUTTLE_HANDLER, new SpaceRecipeSpec(
                AMUNRA_SHUTTLE_HANDLER, "amunra.rocketShuttle", 27, 0));
        putPolicy(specs, AMUNRA_SHUTTLE_HANDLER, AMUNRA_SHUTTLE_HANDLER,
                Adapter.PINNED_SPACE_RECIPE_ID, "adapted-complete-category",
                "adapter:amunra-shuttle-canonical-set-query-v1",
                null, null, null);
        SPACE_RECIPE_SPECS = Collections.unmodifiableMap(spaceSpecs);

        // These five registered handlers expose their real finite corpus only through typed
        // item queries. Their inherited zero-argument string loader is a no-op, so each exact
        // adapter closes over the authoritative mod registry and cross-checks the loaded pages.
        putPolicy(specs, QueryClosureCategoryAdapters.PROJECT_BLUE_HANDLER,
                QueryClosureCategoryAdapters.PROJECT_BLUE_HANDLER,
                Adapter.QUERY_CLOSURE, "adapted-complete-category",
                QueryClosureCategoryAdapters.PROJECT_BLUE_CONTRACT,
                "crafting", null, "crafting");
        putPolicy(specs, QueryClosureCategoryAdapters.PROJECT_RED_SHAPED_HANDLER,
                QueryClosureCategoryAdapters.PROJECT_RED_SHAPED_HANDLER,
                Adapter.QUERY_CLOSURE, "adapted-complete-category",
                QueryClosureCategoryAdapters.PROJECT_RED_SHAPED_CONTRACT,
                "crafting", null, "crafting");
        putPolicy(specs, QueryClosureCategoryAdapters.PROJECT_RED_SHAPELESS_HANDLER,
                QueryClosureCategoryAdapters.PROJECT_RED_SHAPELESS_HANDLER,
                Adapter.QUERY_CLOSURE, "adapted-complete-category",
                QueryClosureCategoryAdapters.PROJECT_RED_SHAPELESS_CONTRACT,
                "crafting", null, "crafting");
        putPolicy(specs, QueryClosureCategoryAdapters.GENDUSTRY_TEMPLATE_HANDLER,
                QueryClosureCategoryAdapters.GENDUSTRY_TEMPLATE_HANDLER,
                Adapter.QUERY_CLOSURE, "adapted-complete-category",
                QueryClosureCategoryAdapters.GENDUSTRY_CONTRACT,
                "crafting", null, null);
        putPolicy(specs, QueryClosureCategoryAdapters.BOTANIA_FLOATING_FLOWER_HANDLER,
                QueryClosureCategoryAdapters.BOTANIA_FLOATING_FLOWER_HANDLER,
                Adapter.QUERY_CLOSURE, "adapted-complete-category",
                QueryClosureCategoryAdapters.BOTANIA_CONTRACT,
                null, null, "crafting");

        // NEI registers these item-query UI/presentation views through the crafting-handler
        // registry, but none represents an executable complete recipe category. Their helper
        // pins class bytes, reflective topology, live source state, and zero prototype rows.
        for (PinnedNonRecipeHandlers.PolicyEntry entry
                : PinnedNonRecipeHandlers.policyEntries()) {
            Adapter exclusion = entry.disposition
                    == PinnedNonRecipeHandlers.Disposition.QUERY_ONLY
                    ? Adapter.EXCLUDED_QUERY_ONLY
                    : Adapter.EXCLUDED_PRESENTATION_ONLY;
            putPolicy(specs, entry.handlerId, entry.handlerClass,
                    exclusion, entry.action, entry.contract,
                    entry.expectedOverlay, entry.expectedTransferSelector(),
                    entry.expectedTransferRect());
        }

        // This is an interactive workpiece-state viewer: the queried mutable
        // workpiece is reported as the result while the lathe tool and one-step
        // preview appear in otherStacks. Treating those stacks as a static recipe
        // would invert NEI's semantics.
        putPolicy(specs, IC2_LATHE_HANDLER, IC2_LATHE_HANDLER,
                Adapter.EXCLUDED_QUERY_ONLY,
                "excluded-non-recipe-query",
                "query-only:ic2-lathe-interactive-workpiece-state-v1",
                null, null, null);
        // A queried bee expands into a recursive ancestry tree. Treating every
        // displayed ancestor as a simultaneous input would duplicate and corrupt
        // NEIAddons' authoritative pairwise bee-breeding recipes.
        putPolicy(specs, BBAB_RECIPE_TREE_HANDLER, BBAB_RECIPE_TREE_HANDLER,
                Adapter.EXCLUDED_QUERY_ONLY,
                "excluded-non-recipe-query",
                "query-only:bee-breeding-recursive-lineage-visualization-v1",
                null, null, null);
        putPolicy(specs, FLUID_REGISTRY_INFORMATION_HANDLER,
                FLUID_REGISTRY_INFORMATION_HANDLER, Adapter.EXCLUDED_PRESENTATION_ONLY,
                "excluded-non-recipe-presentation",
                "presentation-only:forge-fluid-registry-browser-v1",
                null, null, "forge.fluidRegistry");
        putPolicy(specs, ORE_DICTIONARY_INFORMATION_HANDLER,
                ORE_DICTIONARY_INFORMATION_HANDLER, Adapter.EXCLUDED_PRESENTATION_ONLY,
                "excluded-non-recipe-presentation",
                "presentation-only:forge-ore-dictionary-equivalence-browser-v1",
                null, null, "forge.oreDictionary");
        putPolicy(specs, BOTANIA_LEXICA_INFORMATION_HANDLER,
                BOTANIA_LEXICA_INFORMATION_HANDLER, Adapter.EXCLUDED_PRESENTATION_ONLY,
                "excluded-non-recipe-presentation",
                "presentation-only:botania-lexica-cross-reference-v1",
                null, null, "botania.lexica");
        putPolicy(specs, TCONSTRUCT_TOOL_MATERIAL_INFORMATION_HANDLER,
                TCONSTRUCT_TOOL_MATERIAL_INFORMATION_HANDLER,
                Adapter.EXCLUDED_PRESENTATION_ONLY,
                "excluded-non-recipe-presentation",
                "presentation-only:tconstruct-tool-material-statistics-v1",
                null, null, "tconstruct.tools.materials");
        putPolicy(specs, EXTRAUTILITIES_INFO_HANDLER, EXTRAUTILITIES_INFO_HANDLER,
                Adapter.EXCLUDED_QUERY_ONLY,
                "excluded-non-recipe-query",
                "query-only:extrautilities-item-documentation-v1",
                null, null, null);
        putPolicy(specs, EXTRAUTILITIES_SOUL_HANDLER, EXTRAUTILITIES_SOUL_HANDLER,
                Adapter.EXTRAUTILITIES_SOUL,
                "adapted-complete-category",
                "adapter:extrautilities-soul-crafting-item-query-defensive-positioned-copy-v2",
                null, null, null);
        putPolicy(specs, PinnedUnboundTemplateRecipeHandlers.HANDLER_ID,
                PinnedUnboundTemplateRecipeHandlers.HANDLER_CLASS,
                Adapter.EXCLUDED_UNBOUND_TEMPLATE,
                PinnedUnboundTemplateRecipeHandlers.ACTION,
                PinnedUnboundTemplateRecipeHandlers.CONTRACT,
                PinnedUnboundTemplateRecipeHandlers.OVERLAY, null,
                PinnedUnboundTemplateRecipeHandlers.OPERATION);
        putPolicy(specs, ASPECT_FROM_STACK_HANDLER, ASPECT_FROM_STACK_HANDLER,
                Adapter.EXCLUDED_QUERY_ONLY,
                "excluded-non-recipe-query",
                "query-only:player-scanned-item-aspect-view-v1",
                null, null, null);
        putPolicy(specs, ASPECT_COMBINATION_HANDLER, ASPECT_COMBINATION_HANDLER,
                Adapter.EXCLUDED_QUERY_ONLY,
                "excluded-non-recipe-query",
                "query-only:player-discovered-aspect-combination-view-v1",
                null, null, null);

        Map<String, DiagramPolicySpec> diagrams =
                new LinkedHashMap<String, DiagramPolicySpec>();
        addDiagramPolicy(specs, diagrams, ENDER_STORAGE_CHEST_DIAGRAM_HANDLER,
                NEI_CUSTOM_CUSTOM_DIAGRAM_GROUP, Adapter.EXCLUDED_QUERY_ONLY,
                "excluded-non-recipe-query",
                "query-only:neicustomdiagram-enderstorage-live-chest-contents-v1",
                ENDER_STORAGE_CHEST_DIAGRAM_DESCRIPTION,
                4, true, new String[] {
                        ENDER_STORAGE_CHEST_DIAGRAM_HANDLER + "-global",
                        ENDER_STORAGE_CHEST_DIAGRAM_HANDLER + "-personal"});
        addDiagramPolicy(specs, diagrams, ENDER_STORAGE_TANK_DIAGRAM_HANDLER,
                NEI_CUSTOM_CUSTOM_DIAGRAM_GROUP, Adapter.EXCLUDED_QUERY_ONLY,
                "excluded-non-recipe-query",
                "query-only:neicustomdiagram-enderstorage-live-tank-contents-v1",
                ENDER_STORAGE_TANK_DIAGRAM_DESCRIPTION,
                2, true, new String[] {
                        ENDER_STORAGE_TANK_DIAGRAM_HANDLER + "-global",
                        ENDER_STORAGE_TANK_DIAGRAM_HANDLER + "-personal"});
        addDiagramPolicy(specs, diagrams, GT_CIRCUIT_DIAGRAM_HANDLER,
                NEI_CUSTOM_CUSTOM_DIAGRAM_GROUP, Adapter.EXCLUDED_PRESENTATION_ONLY,
                "excluded-non-recipe-presentation",
                "presentation-only:neicustomdiagram-gregtech-circuit-line-overview-v1",
                "This diagram displays GregTech circuit lines and recipes.",
                1, false, new String[] {GT_CIRCUIT_DIAGRAM_HANDLER});
        addDiagramPolicy(specs, diagrams, GT_LENS_DIAGRAM_HANDLER,
                NEI_CUSTOM_DIAGRAM_GROUP, Adapter.EXCLUDED_PRESENTATION_ONLY,
                "excluded-non-recipe-presentation",
                "presentation-only:neicustomdiagram-gregtech-lens-colour-recipe-overview-v1",
                "This diagram displays GregTech lens colours and recipes.",
                1, false, null);
        addDiagramPolicy(specs, diagrams, GT_MATERIAL_PARTS_DIAGRAM_HANDLER,
                NEI_CUSTOM_DIAGRAM_GROUP, Adapter.EXCLUDED_PRESENTATION_ONLY,
                "excluded-non-recipe-presentation",
                "presentation-only:neicustomdiagram-gregtech-material-parts-catalog-v1",
                "This diagram displays GregTech crafting items for each GregTech material.",
                1, false, null);
        addDiagramPolicy(specs, diagrams, GT_MATERIAL_TOOLS_DIAGRAM_HANDLER,
                NEI_CUSTOM_DIAGRAM_GROUP, Adapter.EXCLUDED_PRESENTATION_ONLY,
                "excluded-non-recipe-presentation",
                "presentation-only:neicustomdiagram-gregtech-material-tools-catalog-v1",
                "This diagram displays craftable GregTech tools for each GregTech material.",
                1, false, null);
        addDiagramPolicy(specs, diagrams, GT_ORE_PROCESSING_DIAGRAM_HANDLER,
                NEI_CUSTOM_DIAGRAM_GROUP, Adapter.EXCLUDED_PRESENTATION_ONLY,
                "excluded-non-recipe-presentation",
                "presentation-only:neicustomdiagram-gregtech-ore-processing-flow-v1",
                "This diagram displays GregTech ore processing products.",
                1, false, null);
        DIAGRAM_POLICY_SPECS = Collections.unmodifiableMap(diagrams);

        putPolicy(specs, NEI_PROFILER_HANDLER, NEI_PROFILER_HANDLER,
                Adapter.EXCLUDED_PRESENTATION_ONLY, "excluded-non-recipe-debug",
                "debug-only:nei-recipe-handler-timing-profiler-v1",
                null, null, null);

        POLICY_SPECS = Collections.unmodifiableMap(specs);
    }

    enum Adapter {
        STANDARD,
        AE2_WORLD_CRAFTING,
        BETTER_QUESTING,
        IC2_CROP_BREEDING,
        EXTRAUTILITIES_SOUL,
        PINNED_SPACE_RECIPE_ID,
        QUERY_CLOSURE,
        FORESTRY_FLUID_SEMANTICS,
        GENDUSTRY_MACHINE_SEMANTICS,
        BINNIE_INCUBATOR_SEMANTICS,
        BINNIE_GENEPOOL_SEMANTICS,
        BINNIE_ACCLIMATISER_INFORMATIONAL,
        BINNIE_ANALYSER_INFORMATIONAL,
        MOBSINFO_INFORMATIONAL_SEMANTICS,
        MOBSINFO_INFERNAL_INFORMATIONAL_SEMANTICS,
        MOBSINFO_VILLAGER_INFORMATIONAL_SEMANTICS,
        TCONSTRUCT_MELTING_FLUID_SEMANTICS,
        TCONSTRUCT_ALLOYING_FLUID_SEMANTICS,
        BUILDCRAFT_REFINERY_FLUID_SEMANTICS,
        ENDERIO_VAT_FLUID_SEMANTICS,
        GALACTICRAFT_CIRCUIT_FABRICATOR_SEMANTICS,
        EXCLUDED_UNBOUND_TEMPLATE,
        EXCLUDED_QUERY_ONLY,
        EXCLUDED_PRESENTATION_ONLY;

        boolean allowsInformationalEmptyOutputs() {
            return this == BETTER_QUESTING
                    || this == BINNIE_ACCLIMATISER_INFORMATIONAL
                    || this == BINNIE_ANALYSER_INFORMATIONAL;
        }
    }

    static final class Policy {
        final String handlerClass;
        final String handlerId;
        final String action;
        final String contract;
        final Adapter adapter;

        Policy(String handlerClass, String handlerId, String action,
               String contract, Adapter adapter) {
            this.handlerClass = handlerClass;
            this.handlerId = handlerId;
            this.action = action;
            this.contract = contract;
            this.adapter = adapter;
        }

        void write(JsonWriter writer) throws IOException {
            writer.beginObject();
            writer.name("handlerClass").value(handlerClass);
            writer.name("handlerId").value(handlerId);
            writer.name("action").value(action);
            writer.name("contract").value(contract);
            writer.endObject();
        }
    }

    static final class RuntimeReadiness {
        final boolean ready;
        final String fingerprint;
        final String state;

        RuntimeReadiness(boolean ready, String fingerprint, String state) {
            this.ready = ready;
            this.fingerprint = fingerprint;
            this.state = state;
        }
    }

    /** One exact stack alternative in the graph-only semantic override. */
    static final class SemanticAlternative {
        final ItemStack stack;
        final int amount;
        final String canonicalIdentity;

        SemanticAlternative(ItemStack stack, int amount, String canonicalIdentity) {
            this.stack = stack;
            this.amount = amount;
            this.canonicalIdentity = canonicalIdentity;
        }
    }

    /** One graph input/output slot; alternatives remain grouped as one v1 slot. */
    static final class SemanticSlot {
        final List<SemanticAlternative> alternatives;
        /** Ordered source-BigItemStack boundaries used by preview linkage/fingerprints. */
        final List<Integer> previewGroupSizes;
        /** Optional stochastic transition probability. Null means a deterministic slot. */
        final Double probability;

        SemanticSlot(List<SemanticAlternative> alternatives) {
            this(alternatives, Collections.singletonList(
                    Integer.valueOf(alternatives == null ? 0 : alternatives.size())), null);
        }

        SemanticSlot(List<SemanticAlternative> alternatives, double probability) {
            this(alternatives, Collections.singletonList(
                    Integer.valueOf(alternatives == null ? 0 : alternatives.size())),
                    Double.valueOf(probability));
        }

        SemanticSlot(List<SemanticAlternative> alternatives,
                     List<Integer> previewGroupSizes) {
            this(alternatives, previewGroupSizes, null);
        }

        private SemanticSlot(List<SemanticAlternative> alternatives,
                             List<Integer> previewGroupSizes,
                             Double probability) {
            if (alternatives == null || alternatives.isEmpty()) {
                throw new IllegalArgumentException("semantic slot alternatives must be nonempty");
            }
            if (previewGroupSizes == null || previewGroupSizes.isEmpty()) {
                throw new IllegalArgumentException("semantic slot preview groups must be nonempty");
            }
            int groupedAlternatives = 0;
            for (Integer size : previewGroupSizes) {
                if (size == null || size.intValue() <= 0) {
                    throw new IllegalArgumentException(
                            "semantic slot preview group sizes must be positive");
                }
                groupedAlternatives += size.intValue();
            }
            if (groupedAlternatives != alternatives.size()) {
                throw new IllegalArgumentException("semantic slot preview groups cover "
                        + groupedAlternatives + " alternatives, expected "
                        + alternatives.size());
            }
            if (probability != null
                    && (!Double.isFinite(probability.doubleValue())
                    || probability.doubleValue() <= 0.0d
                    || probability.doubleValue() >= 1.0d)) {
                throw new IllegalArgumentException(
                        "stochastic semantic-slot probability must be finite and strictly between 0 and 1");
            }
            this.alternatives = Collections.unmodifiableList(
                    new ArrayList<SemanticAlternative>(alternatives));
            this.previewGroupSizes = Collections.unmodifiableList(
                    new ArrayList<Integer>(previewGroupSizes));
            this.probability = probability;
        }
    }

    /** Graph-only semantics paired by index with one independently rendered NEI page. */
    static final class RecipeSemanticOverride {
        final String semanticId;
        final List<SemanticSlot> inputs;
        final List<SemanticSlot> outputs;
        final List<SemanticSlot> catalysts;

        RecipeSemanticOverride(UUID questId, List<SemanticSlot> inputs,
                               List<SemanticSlot> outputs) {
            this(questId == null ? null : questId.toString(), inputs, outputs);
        }

        RecipeSemanticOverride(String semanticId, List<SemanticSlot> inputs,
                               List<SemanticSlot> outputs) {
            this(semanticId, inputs, outputs,
                    Collections.<SemanticSlot>emptyList());
        }

        RecipeSemanticOverride(String semanticId, List<SemanticSlot> inputs,
                               List<SemanticSlot> outputs,
                               List<SemanticSlot> catalysts) {
            if (semanticId == null || semanticId.trim().isEmpty()) {
                throw new IllegalArgumentException("semanticId must be nonblank");
            }
            if (inputs == null || outputs == null || catalysts == null) {
                throw new IllegalArgumentException(
                        "semantic override role lists must be nonnull");
            }
            this.semanticId = semanticId;
            this.inputs = Collections.unmodifiableList(new ArrayList<SemanticSlot>(inputs));
            this.outputs = Collections.unmodifiableList(new ArrayList<SemanticSlot>(outputs));
            this.catalysts = Collections.unmodifiableList(
                    new ArrayList<SemanticSlot>(catalysts));
        }
    }

    private static final class PolicySpec {
        final String handlerClass;
        final Adapter adapter;
        final String action;
        final String contract;
        final String expectedOverlay;
        final String expectedTransferSelector;
        final String expectedTransferRect;

        PolicySpec(String handlerClass, Adapter adapter, String action, String contract,
                   String expectedOverlay, String expectedTransferSelector,
                   String expectedTransferRect) {
            this.handlerClass = handlerClass;
            this.adapter = adapter;
            this.action = action;
            this.contract = contract;
            this.expectedOverlay = expectedOverlay;
            this.expectedTransferSelector = expectedTransferSelector;
            this.expectedTransferRect = expectedTransferRect;
        }
    }

    private static final class SpaceRecipeSpec {
        final String handlerClass;
        final String queryId;
        final int recipeCount;
        final int galaxyTier;

        SpaceRecipeSpec(String handlerClass, String queryId, int recipeCount,
                        int galaxyTier) {
            this.handlerClass = handlerClass;
            this.queryId = queryId;
            this.recipeCount = recipeCount;
            this.galaxyTier = galaxyTier;
        }
    }

    private static final class DiagramPolicySpec {
        final String handlerClass;
        final String description;
        final int diagramsPerPage;
        final boolean requireEmptyCustomMatcher;
        final Set<String> expectedCustomBehaviorKeys;

        DiagramPolicySpec(String handlerClass, String description, int diagramsPerPage,
                          boolean requireEmptyCustomMatcher,
                          String[] expectedCustomBehaviorKeys) {
            this.handlerClass = handlerClass;
            this.description = description;
            this.diagramsPerPage = diagramsPerPage;
            this.requireEmptyCustomMatcher = requireEmptyCustomMatcher;
            Set<String> keys = new HashSet<String>();
            if (expectedCustomBehaviorKeys != null) {
                Collections.addAll(keys, expectedCustomBehaviorKeys);
            }
            this.expectedCustomBehaviorKeys = Collections.unmodifiableSet(keys);
        }
    }

    private static final class SpaceRecipeSnapshot {
        final Map<String, ICraftingHandler> prototypes;
        final Map<String, Object> backingCollections;
        final Map<String, List<String>> pageKeys;
        final String fingerprint;

        SpaceRecipeSnapshot(Map<String, ICraftingHandler> prototypes,
                            Map<String, Object> backingCollections,
                            Map<String, List<String>> pageKeys,
                            String fingerprint) {
            this.prototypes = Collections.unmodifiableMap(
                    new LinkedHashMap<String, ICraftingHandler>(prototypes));
            this.backingCollections = Collections.unmodifiableMap(
                    new LinkedHashMap<String, Object>(backingCollections));
            Map<String, List<String>> immutablePageKeys =
                    new LinkedHashMap<String, List<String>>();
            for (Map.Entry<String, List<String>> entry : pageKeys.entrySet()) {
                immutablePageKeys.put(entry.getKey(), Collections.unmodifiableList(
                        new ArrayList<String>(entry.getValue())));
            }
            this.pageKeys = Collections.unmodifiableMap(immutablePageKeys);
            this.fingerprint = fingerprint;
        }
    }

    private static void putPolicy(Map<String, PolicySpec> specs,
                                  String handlerId, String handlerClass,
                                  Adapter adapter, String action, String contract,
                                  String expectedOverlay,
                                  String expectedTransferSelector,
                                  String expectedTransferRect) {
        PolicySpec previous = specs.put(handlerId, new PolicySpec(
                handlerClass, adapter, action, contract, expectedOverlay,
                expectedTransferSelector, expectedTransferRect));
        if (previous != null) {
            throw new IllegalStateException("duplicate pinned handler policy " + handlerId);
        }
    }

    private static void putForestryFluidPolicy(
            Map<String, PolicySpec> specs, String handlerClass,
            String operationId, String semanticKind) {
        putPolicy(specs, handlerClass, handlerClass,
                Adapter.FORESTRY_FLUID_SEMANTICS,
                "adapted-complete-category",
                "adapter:forestry-4.10.17-fluid-semantics-v1/" + semanticKind,
                operationId, null, operationId);
    }

    private static void putGendustryMachinePolicy(
            Map<String, PolicySpec> specs, String handlerClass) {
        String operationId = GendustryMachineSemanticAdapter.operationId(handlerClass);
        String semanticKind = GendustryMachineSemanticAdapter.semanticKind(handlerClass);
        if (operationId == null || semanticKind == null) {
            throw new IllegalStateException(
                    "missing Gendustry machine policy spec " + handlerClass);
        }
        putPolicy(specs, handlerClass, handlerClass,
                Adapter.GENDUSTRY_MACHINE_SEMANTICS,
                "adapted-complete-category",
                "adapter:gendustry-1.9.4-machine-semantics-v1/" + semanticKind,
                null, null, operationId);
    }

    private static void addDiagramPolicy(
            Map<String, PolicySpec> specs,
            Map<String, DiagramPolicySpec> diagrams,
            String handlerId, String handlerClass, Adapter adapter,
            String action, String contract, String description,
            int diagramsPerPage, boolean requireEmptyCustomMatcher,
            String[] expectedCustomBehaviorKeys) {
        putPolicy(specs, handlerId, handlerClass, adapter, action, contract,
                null, null, null);
        DiagramPolicySpec previous = diagrams.put(handlerId, new DiagramPolicySpec(
                handlerClass, description, diagramsPerPage,
                requireEmptyCustomMatcher, expectedCustomBehaviorKeys));
        if (previous != null) {
            throw new IllegalStateException("duplicate diagram policy " + handlerId);
        }
    }

    private static final class CropResultRecord implements CropCacheViewContract.Record {
        final Object result;
        final String resultId;
        final List<String> inputIds;
        final List<Object> inputCrops;
        final int points;
        final float chance;
        final CropGraphSemanticContract.GraphRecipe cleanGraph;
        final RecipeSemanticOverride graphSemantics;
        final String canonicalLine;

        CropResultRecord(CropGraphSemanticContract.GraphRecipe graph) {
            this.result = graph.breedResult;
            this.resultId = graph.output.cropId;
            List<String> ids = new ArrayList<String>(2);
            List<Object> crops = new ArrayList<Object>(2);
            List<SemanticSlot> semanticInputs = new ArrayList<SemanticSlot>(2);
            for (CropGraphSemanticContract.GraphStack input : graph.inputs) {
                ids.add(input.cropId);
                crops.add(input.crop);
                semanticInputs.add(singleStackSlot(input));
            }
            this.inputIds = Collections.unmodifiableList(ids);
            this.inputCrops = Collections.unmodifiableList(crops);
            this.points = graph.points;
            this.chance = graph.chance;
            this.cleanGraph = graph;
            List<SemanticSlot> semanticOutputs = Collections.singletonList(
                    singleStackSlot(graph.output));
            String semanticId = "crop-breed:" + Naming.sha256(graph.canonical);
            this.graphSemantics = new RecipeSemanticOverride(
                    semanticId, semanticInputs, semanticOutputs);
            this.canonicalLine = graph.canonical + '\n';
        }

        private static SemanticSlot singleStackSlot(
                CropGraphSemanticContract.GraphStack graphStack) {
            SemanticAlternative alternative = new SemanticAlternative(
                    graphStack.stack, graphStack.amount, graphStack.stackCanonical);
            return new SemanticSlot(Collections.singletonList(alternative));
        }

        @Override
        public Object rawResult() {
            return result;
        }

        @Override
        public String semanticCanonical() {
            return cleanGraph.canonical;
        }

        @Override
        public String diagnosticId() {
            return graphSemantics.semanticId;
        }

        @Override
        public Object resultCrop() {
            return cleanGraph.output.crop;
        }

        @Override
        public String resultCropId() {
            return resultId;
        }

        @Override
        public List<Object> inputCrops() {
            return inputCrops;
        }

        @Override
        public List<String> inputCropIds() {
            return inputIds;
        }
    }

    private static final class CropSnapshot {
        final Object breeder;
        final Map<?, ?> rawCraftCache;
        final Map<?, ?> rawUsageCache;
        final List<Object> exactCropUniverse;
        final List<CropResultRecord> records;
        final List<CropResultRecord> auditRepresentatives;
        final Map<String, String> cleanGraphStacksByCropId;
        final DeterministicCropMatrixContract.Snapshot matrix;
        final int cropCount;
        final int pairCount;
        final long simulatorCandidateCount;
        final int canonicalGlobalMatchKeyCount;
        final int craftWinnerCount;
        final int usageWinnerIdentityCount;
        final int usageOnlyWinnerCount;
        final int usageOccurrenceCount;
        final int rawUnionResultIdentities;
        final int rawCraftResultIdentities;
        final int rawUsageResultIdentities;
        final int rawUsageOnlyResultIdentities;
        final int rawUsageOccurrences;
        final int rawConflatedCraftRepresentatives;
        final String rawDiagnosticFingerprint;
        final String fingerprint;

        CropSnapshot(Object breeder,
                     Map<?, ?> rawCraftCache,
                     Map<?, ?> rawUsageCache,
                     List<Object> exactCropUniverse,
                     List<CropResultRecord> records,
                     List<CropResultRecord> auditRepresentatives,
                     Map<String, String> cleanGraphStacksByCropId,
                     DeterministicCropMatrixContract.Snapshot matrix,
                     int cropCount,
                     int pairCount,
                     long simulatorCandidateCount,
                     int canonicalGlobalMatchKeyCount,
                     int craftWinnerCount,
                     int usageWinnerIdentityCount,
                     int usageOnlyWinnerCount,
                     int usageOccurrenceCount,
                     int rawUnionResultIdentities,
                     int rawCraftResultIdentities,
                     int rawUsageResultIdentities,
                     int rawUsageOnlyResultIdentities,
                     int rawUsageOccurrences,
                     int rawConflatedCraftRepresentatives,
                     String rawDiagnosticFingerprint,
                     String fingerprint) {
            this.breeder = breeder;
            this.rawCraftCache = rawCraftCache;
            this.rawUsageCache = rawUsageCache;
            this.exactCropUniverse = exactCropUniverse;
            this.records = records;
            this.auditRepresentatives = auditRepresentatives;
            this.cleanGraphStacksByCropId = cleanGraphStacksByCropId;
            this.matrix = matrix;
            this.cropCount = cropCount;
            this.pairCount = pairCount;
            this.simulatorCandidateCount = simulatorCandidateCount;
            this.canonicalGlobalMatchKeyCount = canonicalGlobalMatchKeyCount;
            this.craftWinnerCount = craftWinnerCount;
            this.usageWinnerIdentityCount = usageWinnerIdentityCount;
            this.usageOnlyWinnerCount = usageOnlyWinnerCount;
            this.usageOccurrenceCount = usageOccurrenceCount;
            this.rawUnionResultIdentities = rawUnionResultIdentities;
            this.rawCraftResultIdentities = rawCraftResultIdentities;
            this.rawUsageResultIdentities = rawUsageResultIdentities;
            this.rawUsageOnlyResultIdentities = rawUsageOnlyResultIdentities;
            this.rawUsageOccurrences = rawUsageOccurrences;
            this.rawConflatedCraftRepresentatives = rawConflatedCraftRepresentatives;
            this.rawDiagnosticFingerprint = rawDiagnosticFingerprint;
            this.fingerprint = fingerprint;
        }
    }

    /** Nondeterministic upstream cache telemetry retained only for structural/drift auditing. */
    private static final class RawCropCacheDiagnostics {
        final int unionResultIdentities;
        final int craftResultIdentities;
        final int usageResultIdentities;
        final int usageOnlyResultIdentities;
        final int usageOccurrences;
        final int conflatedCraftRepresentatives;
        final String fingerprint;

        RawCropCacheDiagnostics(int unionResultIdentities,
                                int craftResultIdentities,
                                int usageResultIdentities,
                                int usageOnlyResultIdentities,
                                int usageOccurrences,
                                int conflatedCraftRepresentatives,
                                String fingerprint) {
            this.unionResultIdentities = unionResultIdentities;
            this.craftResultIdentities = craftResultIdentities;
            this.usageResultIdentities = usageResultIdentities;
            this.usageOnlyResultIdentities = usageOnlyResultIdentities;
            this.usageOccurrences = usageOccurrences;
            this.conflatedCraftRepresentatives = conflatedCraftRepresentatives;
            this.fingerprint = fingerprint;
        }
    }

    private static final class QuestSemanticCorpus {
        final Object database;
        final String uuidFingerprint;
        final List<RecipeSemanticOverride> pages;
        final String fingerprint;
        final int both;
        final int inputOnly;
        final int outputOnly;
        final int noItems;
        final int inputSlots;
        final int outputSlots;
        final int choiceProviders;
        final int choiceEntries;
        final int regularRewardEntries;
        final int flatRewardEntries;
        final int taskLogicOr;
        final int optionalRetrievalTasks;
        final int expandedAlternatives;
        final int over16InputPages;
        final int over16OutputPages;
        final int maxInputSlots;
        final int maxOutputSlots;

        QuestSemanticCorpus(Object database, String uuidFingerprint,
                            List<RecipeSemanticOverride> pages, String fingerprint,
                            int both, int inputOnly, int outputOnly, int noItems,
                            int inputSlots, int outputSlots,
                            int choiceProviders, int choiceEntries,
                            int regularRewardEntries, int flatRewardEntries,
                            int taskLogicOr, int optionalRetrievalTasks,
                            int expandedAlternatives,
                            int over16InputPages, int over16OutputPages,
                            int maxInputSlots, int maxOutputSlots) {
            this.database = database;
            this.uuidFingerprint = uuidFingerprint;
            this.pages = Collections.unmodifiableList(
                    new ArrayList<RecipeSemanticOverride>(pages));
            this.fingerprint = fingerprint;
            this.both = both;
            this.inputOnly = inputOnly;
            this.outputOnly = outputOnly;
            this.noItems = noItems;
            this.inputSlots = inputSlots;
            this.outputSlots = outputSlots;
            this.choiceProviders = choiceProviders;
            this.choiceEntries = choiceEntries;
            this.regularRewardEntries = regularRewardEntries;
            this.flatRewardEntries = flatRewardEntries;
            this.taskLogicOr = taskLogicOr;
            this.optionalRetrievalTasks = optionalRetrievalTasks;
            this.expandedAlternatives = expandedAlternatives;
            this.over16InputPages = over16InputPages;
            this.over16OutputPages = over16OutputPages;
            this.maxInputSlots = maxInputSlots;
            this.maxOutputSlots = maxOutputSlots;
        }
    }

    private static final class RewardSemanticSlots {
        final List<SemanticSlot> slots;
        final int choiceProviders;
        final int choiceEntries;
        final int regularEntries;
        final String providerCanonical;

        RewardSemanticSlots(List<SemanticSlot> slots, int choiceProviders,
                            int choiceEntries, int regularEntries,
                            String providerCanonical) {
            this.slots = Collections.unmodifiableList(
                    new ArrayList<SemanticSlot>(slots));
            this.choiceProviders = choiceProviders;
            this.choiceEntries = choiceEntries;
            this.regularEntries = regularEntries;
            this.providerCanonical = providerCanonical;
        }

        int flatEntries() {
            return choiceEntries + regularEntries;
        }
    }

    private static final class QuestStackSizeSnapshot {
        final IdentityHashMap<ItemStack, Integer> originalSizes;

        QuestStackSizeSnapshot(IdentityHashMap<ItemStack, Integer> originalSizes) {
            this.originalSizes = originalSizes;
        }

        void restoreAndVerify() throws ExportFailure {
            for (Map.Entry<ItemStack, Integer> entry : originalSizes.entrySet()) {
                entry.getKey().stackSize = entry.getValue().intValue();
            }
            for (Map.Entry<ItemStack, Integer> entry : originalSizes.entrySet()) {
                if (entry.getKey().stackSize != entry.getValue().intValue()) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "could not restore BetterQuesting backing ItemStack quantity state");
                }
            }
        }
    }

    private CompleteCategoryAdapters() {
    }

    static Policy classify(String handlerClass, String handlerId,
                           String overlay, String transfer) throws ExportFailure {
        return classify(handlerClass, handlerId, overlay, transfer, null);
    }

    static Policy classify(String handlerClass, String handlerId,
                           String overlay, String transfer, String transferRect)
            throws ExportFailure {
        PolicySpec spec = POLICY_SPECS.get(handlerId);
        if (spec == null) {
            return null;
        }
        if (!spec.handlerClass.equals(handlerClass)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                    + " runtime class drifted; expected " + spec.handlerClass
                    + ", got " + handlerClass);
        }
        if (!sameNullable(spec.expectedOverlay, overlay)
                || !sameNullable(spec.expectedTransferSelector, transfer)
                || !sameNullable(spec.expectedTransferRect, transferRect)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                    + " pinned public category contract drifted; expected overlay="
                    + spec.expectedOverlay + ", selector=" + spec.expectedTransferSelector
                    + ", populatedTransferRects=" + spec.expectedTransferRect
                    + "; got overlay=" + overlay + ", selector=" + transfer
                    + ", populatedTransferRects=" + transferRect);
        }
        return new Policy(spec.handlerClass, handlerId,
                spec.action, spec.contract, spec.adapter);
    }

    /** Cheap exact semantic pins for special prototypes before expensive discovery begins. */
    static void validateStructuralPolicyPrototype(Policy policy, ICraftingHandler prototype)
            throws ExportFailure {
        if (policy == null || prototype == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "special-handler structural validation received null state");
        }
        DiagramPolicySpec diagram = DIAGRAM_POLICY_SPECS.get(policy.handlerId);
        if (diagram != null) {
            validateDiagramPrototype(prototype, policy.handlerId, diagram);
            return;
        }
        if (NEI_PROFILER_HANDLER.equals(policy.handlerId)) {
            validateProfilerPrototype(prototype);
            return;
        }
        if (PinnedNonRecipeHandlers.supports(policy.handlerId)) {
            PinnedNonRecipeHandlers.validatePrototype(policy.handlerId, prototype);
            return;
        }
        if (PinnedUnboundTemplateRecipeHandlers.supports(policy.handlerId)) {
            PinnedUnboundTemplateRecipeHandlers.validatePrototype(
                    policy.handlerId, prototype);
            return;
        }
        if (policy.adapter == Adapter.QUERY_CLOSURE) {
            QueryClosureCategoryAdapters.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.FORESTRY_FLUID_SEMANTICS) {
            ForestryFluidSemanticAdapter.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.GENDUSTRY_MACHINE_SEMANTICS) {
            GendustryMachineSemanticAdapter.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.BINNIE_INCUBATOR_SEMANTICS) {
            BinnieIncubatorSemanticAdapter.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.BINNIE_GENEPOOL_SEMANTICS) {
            BinnieGenepoolSemanticAdapter.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.BINNIE_ACCLIMATISER_INFORMATIONAL) {
            BinnieAcclimatiserInformationalAdapter.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.BINNIE_ANALYSER_INFORMATIONAL) {
            BinnieAnalyserInformationalAdapter.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.MOBSINFO_INFORMATIONAL_SEMANTICS) {
            MobsInfoSemanticAdapter.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.MOBSINFO_INFERNAL_INFORMATIONAL_SEMANTICS) {
            MobsInfoInfernalSemanticAdapter.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.MOBSINFO_VILLAGER_INFORMATIONAL_SEMANTICS) {
            MobsInfoVillagerTradeSemanticAdapter.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.TCONSTRUCT_MELTING_FLUID_SEMANTICS) {
            TconstructMeltingSemanticAdapter.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.TCONSTRUCT_ALLOYING_FLUID_SEMANTICS) {
            TconstructAlloyingSemanticAdapter.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.BUILDCRAFT_REFINERY_FLUID_SEMANTICS) {
            BuildcraftRefinerySemanticAdapter.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.ENDERIO_VAT_FLUID_SEMANTICS) {
            EnderIoVatSemanticAdapter.validatePrototype(prototype);
            return;
        }
        if (policy.adapter == Adapter.GALACTICRAFT_CIRCUIT_FABRICATOR_SEMANTICS) {
            GalacticraftCircuitFabricatorSemanticAdapter.validatePrototype(prototype);
            return;
        }
        SpaceRecipeSpec space = SPACE_RECIPE_SPECS.get(policy.handlerId);
        if (space != null) {
            try {
                requireExactClass(prototype, space.handlerClass);
                requireCount(policy.handlerId + " prototype recipe count", 0,
                        prototype.numRecipes());
                requireSpaceRecipeQueryId(prototype, policy.handlerId, space);
                Object backing = spaceRecipeBackingCollection(
                        prototype, policy.handlerId, space);
                requireCount(policy.handlerId + " backing recipe count",
                        space.recipeCount, ((Set<?>) backing).size());
            } catch (ExportFailure failure) {
                throw failure;
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                throw new ExportFailure("HANDLER_UNLOADED", policy.handlerId
                        + " structural space-recipe contract failed", unwrap(error));
            }
        }
    }

    /** Pins the complete transfer-operation vector, including arity, for special policies. */
    static void validateStructuralPolicyTransferOperations(
            Policy policy, List<HandlerCategoryPlan.TransferOperation> operations)
            throws ExportFailure {
        if (policy == null || operations == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "special-handler transfer validation received no policy");
        }
        PolicySpec spec = POLICY_SPECS.get(policy.handlerId);
        if (spec == null || !spec.handlerClass.equals(policy.handlerClass)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", policy.handlerId
                    + " has no exact compiled transfer-operation policy");
        }
        if (policy.adapter == Adapter.BUILDCRAFT_REFINERY_FLUID_SEMANTICS) {
            if (operations.size() != 2) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", policy.handlerId
                        + " must expose exactly two zero-argument Refinery transfer buttons; "
                        + "observed count=" + operations.size());
            }
            for (int index = 0; index < operations.size(); index++) {
                HandlerCategoryPlan.TransferOperation operation = operations.get(index);
                if (!spec.expectedTransferRect.equals(operation.outputId)
                        || operation.resultArity != 0) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", policy.handlerId
                            + " transfer button #" + index + " drifted; expected outputId="
                            + spec.expectedTransferRect + ", arity=0; got outputId="
                            + operation.outputId + ", arity=" + operation.resultArity);
                }
            }
            return;
        }
        if (policy.adapter == Adapter.BINNIE_ANALYSER_INFORMATIONAL) {
            if (operations.size() != 4) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", policy.handlerId
                        + " must expose exactly four zero-argument Analyser transfer buttons; "
                        + "observed count=" + operations.size());
            }
            for (int index = 0; index < operations.size(); index++) {
                HandlerCategoryPlan.TransferOperation operation = operations.get(index);
                if (!spec.expectedTransferRect.equals(operation.outputId)
                        || operation.resultArity != 0) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", policy.handlerId
                            + " transfer button #" + index + " drifted; expected outputId="
                            + spec.expectedTransferRect + ", arity=0; got outputId="
                            + operation.outputId + ", arity=" + operation.resultArity);
                }
            }
            return;
        }
        if (spec.expectedTransferRect == null) {
            if (!operations.isEmpty()) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", policy.handlerId
                        + " must expose no transfer operations; observed "
                        + operations.size());
            }
            return;
        }
        HandlerCategoryPlan.TransferOperation sole = operations.size() == 1
                ? operations.get(0) : null;
        if (sole == null
                || !spec.expectedTransferRect.equals(sole.outputId)
                || sole.resultArity != 0) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", policy.handlerId
                    + " must expose exactly one zero-argument transfer operation "
                    + spec.expectedTransferRect + "; observed count=" + operations.size()
                    + ", outputId=" + (sole == null ? null : sole.outputId)
                    + ", arity=" + (sole == null ? -1 : sole.resultArity));
        }
    }

    private static void validateDiagramPrototype(
            ICraftingHandler prototype, String handlerId, DiagramPolicySpec spec)
            throws ExportFailure {
        try {
            requireExactAnyCraftingHandlerClass(prototype, spec.handlerClass);
            requireCount(handlerId + " diagram prototype recipe count", 0,
                    prototype.numRecipes());
            List<PositionedStack> ingredients = positionedList(
                    prototype.getIngredientStacks(0), handlerId + " diagram ingredients");
            List<PositionedStack> others = positionedList(
                    prototype.getOtherStacks(0), handlerId + " diagram other stacks");
            if (!ingredients.isEmpty() || !others.isEmpty()
                    || prototype.getResultStack(0) != null) {
                throw new ExportFailure("RECIPE_SEMANTICS", handlerId
                        + " no longer exposes empty/null NEI item-stack semantics");
            }

            ClassLoader loader = prototype.getClass().getClassLoader();
            Class<?> diagramGroup = Class.forName(
                    NEI_CUSTOM_DIAGRAM_GROUP, false, loader);
            Class<?> infoClass = Class.forName(DIAGRAM_GROUP_INFO_CLASS, false, loader);
            Class<?> matcherClass = Class.forName(DIAGRAM_MATCHER_CLASS, false, loader);
            Class<?> immutableList = Class.forName(
                    "com.google.common.collect.ImmutableList", false, loader);
            Method infoMethod = exactPublicReturningMethod(
                    prototype.getClass(), "info", infoClass);
            Object info = infoMethod.invoke(prototype);
            if (!infoClass.isInstance(info)) {
                throw new ExportFailure("HANDLER_UNLOADED", handlerId
                        + " returned no exact DiagramGroupInfo");
            }
            Method groupId = exactPublicReturningMethod(infoClass, "groupId", String.class);
            Method description = exactPublicReturningMethod(
                    infoClass, "description", String.class);
            Method perPage = exactPublicReturningMethod(
                    infoClass, "diagramsPerPage", int.class);
            String observedGroupId = (String) groupId.invoke(info);
            String observedDescription = (String) description.invoke(info);
            int observedDiagramsPerPage = ((Integer) perPage.invoke(info)).intValue();
            if (!handlerId.equals(observedGroupId)
                    || !spec.description.equals(observedDescription)
                    || observedDiagramsPerPage != spec.diagramsPerPage) {
                throw new ExportFailure("RECIPE_SEMANTICS", handlerId
                        + " DiagramGroupInfo drifted; expected groupId="
                        + diagnosticString(handlerId) + ", description="
                        + diagnosticString(spec.description) + ", diagramsPerPage="
                        + spec.diagramsPerPage + "; observed groupId="
                        + diagnosticString(observedGroupId) + ", description="
                        + diagnosticString(observedDescription) + ", diagramsPerPage="
                        + observedDiagramsPerPage);
            }

            Field diagrams = exactDeclaredField(diagramGroup, "diagrams", immutableList);
            Object diagramList = diagrams.get(prototype);
            if (!(diagramList instanceof Collection)
                    || !((Collection<?>) diagramList).isEmpty()) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                        + " registered diagram prototype must retain an empty immutable page list");
            }
            Field matcher = exactDeclaredField(diagramGroup, "matcher", matcherClass);
            Object matcherValue = matcher.get(prototype);
            if (!matcherClass.isInstance(matcherValue)) {
                throw new ExportFailure("HANDLER_UNLOADED", handlerId
                        + " has no exact DiagramMatcher");
            }

            if (!spec.expectedCustomBehaviorKeys.isEmpty()) {
                Class<?> customGroup = Class.forName(
                        NEI_CUSTOM_CUSTOM_DIAGRAM_GROUP, false, loader);
                Class<?> immutableMap = Class.forName(
                        "com.google.common.collect.ImmutableMap", false, loader);
                Field customMap = exactDeclaredField(
                        customGroup, "customBehaviorMap", immutableMap);
                Object rawMap = customMap.get(prototype);
                if (!(rawMap instanceof Map)
                        || !spec.expectedCustomBehaviorKeys.equals(
                        ((Map<?, ?>) rawMap).keySet())) {
                    throw new ExportFailure("RECIPE_SEMANTICS", handlerId
                            + " custom diagram query-key set drifted; expected "
                            + spec.expectedCustomBehaviorKeys + ", got "
                            + (rawMap instanceof Map
                            ? ((Map<?, ?>) rawMap).keySet() : rawMap));
                }
            }
            if (spec.requireEmptyCustomMatcher) {
                Class<?> customMatcher = Class.forName(
                        CUSTOM_DIAGRAM_MATCHER_CLASS, false, loader);
                if (matcherValue.getClass() != customMatcher) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                            + " must retain the exact live-query CustomDiagramMatcher");
                }
                Method all = exactPublicReturningMethod(
                        customMatcher, "all", Collection.class);
                Object allValue = all.invoke(matcherValue);
                if (!(allValue instanceof Collection)
                        || !((Collection<?>) allValue).isEmpty()) {
                    throw new ExportFailure("RECIPE_SEMANTICS", handlerId
                            + " live-state matcher unexpectedly exposes a static full corpus");
                }
            }
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED", handlerId
                    + " exact non-recipe diagram policy validation failed", unwrap(error));
        }
    }

    private static void validateProfilerPrototype(ICraftingHandler prototype)
            throws ExportFailure {
        try {
            requireExactDirectHandlerClass(prototype, NEI_PROFILER_HANDLER);
            Field crafting = exactDeclaredField(
                    prototype.getClass(), "crafting", boolean.class);
            if (!Modifier.isPrivate(crafting.getModifiers())
                    || !Modifier.isFinal(crafting.getModifiers())
                    || !crafting.getBoolean(prototype)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "registered NEI profiler is not the exact crafting timing view");
            }
            if (prototype.getRecipeHandler("recipe-tree-identity-probe") != prototype) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "NEI crafting profiler no longer returns its identity for queries");
            }
            if (!positionedList(prototype.getIngredientStacks(0),
                    "NEI profiler ingredients").isEmpty()
                    || !positionedList(prototype.getOtherStacks(0),
                    "NEI profiler other stacks").isEmpty()
                    || prototype.getResultStack(0) != null) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "NEI crafting profiler unexpectedly exposes recipe stack semantics");
            }
            Class<?> profilerClass = Class.forName(
                    "codechicken.nei.util.AsyncTaskProfiler", false,
                    prototype.getClass().getClassLoader());
            Method getProfiler = exactPublicReturningMethod(
                    prototype.getClass(), "getProfiler", profilerClass);
            if (!Modifier.isStatic(getProfiler.getModifiers())
                    || !profilerClass.isInstance(getProfiler.invoke(null))) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "NEI crafting profiler lost its timing-profiler backing object");
            }
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    "NEI crafting profiler non-recipe policy validation failed", unwrap(error));
        }
    }

    static boolean isExcludedFromCategoryExport(Adapter adapter) {
        return adapter == Adapter.EXCLUDED_UNBOUND_TEMPLATE
                || adapter == Adapter.EXCLUDED_QUERY_ONLY
                || adapter == Adapter.EXCLUDED_PRESENTATION_ONLY;
    }

    /** Pre-render planning seam for the intentionally unpromoted crop-presentation corpus. */
    static boolean requiresCropPresentationDiscovery(Adapter adapter) {
        return CropPresentationDiscoveryGate.requiresDiscovery(adapter);
    }

    static void requireAllPinnedPolicies(Set<String> observed) throws ExportFailure {
        Set<String> missing = new HashSet<String>(POLICY_SPECS.keySet());
        missing.removeAll(observed);
        Set<String> unexpected = new HashSet<String>(observed);
        unexpected.removeAll(POLICY_SPECS.keySet());
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "pinned GTNH special-handler classification mismatch; missing="
                            + missing + ", unexpected=" + unexpected);
        }
    }

    static List<Policy> expectedPoliciesForContract() {
        List<Policy> policies = new ArrayList<Policy>();
        for (Map.Entry<String, PolicySpec> entry : POLICY_SPECS.entrySet()) {
            PolicySpec spec = entry.getValue();
            policies.add(new Policy(spec.handlerClass, entry.getKey(), spec.action,
                    spec.contract, spec.adapter));
        }
        Collections.sort(policies, new Comparator<Policy>() {
            @Override
            public int compare(Policy left, Policy right) {
                int byClass = left.handlerClass.compareTo(right.handlerClass);
                return byClass != 0 ? byClass : left.handlerId.compareTo(right.handlerId);
            }
        });
        return policies;
    }

    static ICraftingHandler load(Adapter adapter, ICraftingHandler prototype) throws ExportFailure {
        if (adapter == Adapter.AE2_WORLD_CRAFTING) {
            requireExactDirectHandlerClass(prototype, AE2_WORLD_CRAFTING_HANDLER);
            return loadAe2WorldCrafting(prototype);
        }
        if (adapter == Adapter.BETTER_QUESTING) {
            requireExactClass(prototype, BETTER_QUESTING_HANDLER);
            return loadBetterQuesting((TemplateRecipeHandler) prototype);
        }
        if (adapter == Adapter.IC2_CROP_BREEDING) {
            requireExactClass(prototype, CROP_HANDLER);
            return loadCropBreeding((TemplateRecipeHandler) prototype);
        }
        if (adapter == Adapter.EXTRAUTILITIES_SOUL) {
            requireExactClass(prototype, EXTRAUTILITIES_SOUL_HANDLER);
            return loadExtraUtilitiesSoul((TemplateRecipeHandler) prototype);
        }
        if (adapter == Adapter.PINNED_SPACE_RECIPE_ID) {
            return loadPinnedSpaceRecipeId(prototype);
        }
        if (adapter == Adapter.QUERY_CLOSURE) {
            return QueryClosureCategoryAdapters.load(prototype);
        }
        if (adapter == Adapter.FORESTRY_FLUID_SEMANTICS) {
            return ForestryFluidSemanticAdapter.loadCompleteCategory(prototype);
        }
        if (adapter == Adapter.GENDUSTRY_MACHINE_SEMANTICS) {
            return GendustryMachineSemanticAdapter.loadCompleteCategory(prototype);
        }
        if (adapter == Adapter.BINNIE_INCUBATOR_SEMANTICS) {
            return BinnieIncubatorSemanticAdapter.loadCompleteCategory(prototype);
        }
        if (adapter == Adapter.BINNIE_GENEPOOL_SEMANTICS) {
            return BinnieGenepoolSemanticAdapter.loadCompleteCategory(prototype);
        }
        if (adapter == Adapter.BINNIE_ACCLIMATISER_INFORMATIONAL) {
            return BinnieAcclimatiserInformationalAdapter.loadCompleteCategory(prototype);
        }
        if (adapter == Adapter.BINNIE_ANALYSER_INFORMATIONAL) {
            return BinnieAnalyserInformationalAdapter.loadCompleteCategory(prototype);
        }
        if (adapter == Adapter.MOBSINFO_INFORMATIONAL_SEMANTICS) {
            return MobsInfoSemanticAdapter.loadCompleteCategory(prototype);
        }
        if (adapter == Adapter.MOBSINFO_INFERNAL_INFORMATIONAL_SEMANTICS) {
            return MobsInfoInfernalSemanticAdapter.loadCompleteCategory(prototype);
        }
        if (adapter == Adapter.MOBSINFO_VILLAGER_INFORMATIONAL_SEMANTICS) {
            return MobsInfoVillagerTradeSemanticAdapter.loadCompleteCategory(prototype);
        }
        if (adapter == Adapter.TCONSTRUCT_MELTING_FLUID_SEMANTICS) {
            return TconstructMeltingSemanticAdapter.loadCompleteCategory(prototype);
        }
        if (adapter == Adapter.TCONSTRUCT_ALLOYING_FLUID_SEMANTICS) {
            return TconstructAlloyingSemanticAdapter.loadCompleteCategory(prototype);
        }
        if (adapter == Adapter.BUILDCRAFT_REFINERY_FLUID_SEMANTICS) {
            return BuildcraftRefinerySemanticAdapter.loadCompleteCategory(prototype);
        }
        if (adapter == Adapter.ENDERIO_VAT_FLUID_SEMANTICS) {
            return EnderIoVatSemanticAdapter.loadCompleteCategory(prototype);
        }
        if (adapter == Adapter.GALACTICRAFT_CIRCUIT_FABRICATOR_SEMANTICS) {
            return GalacticraftCircuitFabricatorSemanticAdapter.loadCompleteCategory(prototype);
        }
        throw new ExportFailure("HANDLER_AMBIGUOUS",
                "no complete-category adapter loader exists for " + adapter);
    }

    private static ICraftingHandler loadExtraUtilitiesSoul(TemplateRecipeHandler prototype)
            throws ExportFailure {
        try {
            int auditedQueryPages = 0;
            requireCount("Extra Utilities Soul prototype recipe count", 0,
                    prototype.numRecipes());
            ClassLoader loader = prototype.getClass().getClassLoader();
            Class<?> extraUtils = Class.forName(EXTRAUTILITIES_CLASS, false, loader);
            Field soulEnabled = exactPublicField(extraUtils, "soulEnabled", boolean.class);
            Field swordEnabled =
                    exactPublicField(extraUtils, "ethericSwordEnabled", boolean.class);
            if (!Modifier.isStatic(soulEnabled.getModifiers())
                    || !Modifier.isStatic(swordEnabled.getModifiers())
                    || !soulEnabled.getBoolean(null) || !swordEnabled.getBoolean(null)) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "Extra Utilities Soul Crafting feature vector must be enabled");
            }
            Item soul = requirePublicStaticItem(extraUtils, "soul");
            Item sword = requirePublicStaticItem(extraUtils, "ethericSword");
            ItemStack soulTarget = new ItemStack(soul, 1, 0);
            ItemStack swordTarget = new ItemStack(sword, 1, 0);
            requireExactItemIdentity(
                    soulTarget, "ExtraUtilities:mini-soul", 0, "Extra Utilities Soul");
            requireExactItemIdentity(
                    swordTarget, "ExtraUtilities:ethericsword", 0,
                    "Extra Utilities Etheric Sword");

            ICraftingHandler loaded = prototype.getRecipeHandler("item", soulTarget.copy());
            requireExactClass(loaded, EXTRAUTILITIES_SOUL_HANDLER);
            requireCount("Extra Utilities Soul Crafting query", 1, loaded.numRecipes());
            requireExtraUtilitiesSoulPage(loaded, "craft query");
            auditedQueryPages++;
            for (int metadata : new int[] {0, 1, 2, 3, OreDictionary.WILDCARD_VALUE}) {
                ICraftingHandler metadataQuery = prototype.getRecipeHandler(
                        "item", new ItemStack(soul, 1, metadata));
                requireExactClass(metadataQuery, EXTRAUTILITIES_SOUL_HANDLER);
                requireCount("Extra Utilities Soul metadata query " + metadata,
                        1, metadataQuery.numRecipes());
                requireExtraUtilitiesSoulPage(metadataQuery,
                        "craft metadata query " + metadata);
                auditedQueryPages++;
            }
            ItemStack nbtSoulQuery = new ItemStack(soul, 1, 3);
            NBTTagCompound soulProbeNbt = new NBTTagCompound();
            soulProbeNbt.setString("RecipeTreeAdapterProbe", "item-identity-query");
            nbtSoulQuery.setTagCompound(soulProbeNbt);
            ICraftingHandler nbtQuery = prototype.getRecipeHandler("item", nbtSoulQuery);
            requireExactClass(nbtQuery, EXTRAUTILITIES_SOUL_HANDLER);
            requireCount("Extra Utilities Soul NBT-bearing query", 1, nbtQuery.numRecipes());
            requireExtraUtilitiesSoulPage(nbtQuery, "craft NBT-bearing query");
            auditedQueryPages++;

            ICraftingHandler swordOutputQuery =
                    prototype.getRecipeHandler("item", swordTarget.copy());
            requireExactClass(swordOutputQuery, EXTRAUTILITIES_SOUL_HANDLER);
            requireCount("Extra Utilities Etheric Sword output query", 0,
                    swordOutputQuery.numRecipes());

            for (int metadata : new int[] {0, 1, OreDictionary.WILDCARD_VALUE}) {
                ItemStack swordUsageTarget = new ItemStack(sword, 1, metadata);
                if (metadata == 1) {
                    NBTTagCompound swordProbeNbt = new NBTTagCompound();
                    swordProbeNbt.setBoolean("RecipeTreeAdapterProbe", true);
                    swordUsageTarget.setTagCompound(swordProbeNbt);
                }
                IUsageHandler usageQuery = prototype.getUsageHandler(
                        "item", swordUsageTarget);
                requireExactClass(usageQuery, EXTRAUTILITIES_SOUL_HANDLER);
                requireCount("Extra Utilities Etheric Sword usage query " + metadata,
                        1, usageQuery.numRecipes());
                requireExtraUtilitiesSoulPage(usageQuery,
                        "usage metadata query " + metadata);
                auditedQueryPages++;
            }
            IUsageHandler soulUsageQuery = prototype.getUsageHandler(
                    "item", soulTarget.copy());
            requireExactClass(soulUsageQuery, EXTRAUTILITIES_SOUL_HANDLER);
            requireCount("Extra Utilities Soul usage query", 0,
                    soulUsageQuery.numRecipes());

            requireSingleRuntimeSoulRecipe();
            requireCount("Extra Utilities Soul audited query pages", 10, auditedQueryPages);
            requireCount("Extra Utilities Soul prototype recipe count after adapter load",
                    0, prototype.numRecipes());
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Extra Utilities Soul Crafting exact item-query "
                            + "adapter loaded one canonical NEI Etheric Sword -> Soul display "
                            + "recipe and verified its live CraftingManager registration; "
                            + "queryPages={}, positionedStacks={}, defensiveCurrentCopies={}",
                    auditedQueryPages, auditedQueryPages * 2, auditedQueryPages * 2);
            return loaded;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Extra Utilities Soul Crafting exact adapter failed", unwrap(error));
        }
    }

    private static void requireExtraUtilitiesSoulPage(
            IRecipeHandler handler, String label) throws ExportFailure {
        try {
            List<PositionedStack> ingredients = handler.getIngredientStacks(0);
            List<PositionedStack> others = handler.getOtherStacks(0);
            PositionedStack result = handler.getResultStack(0);
            if (ingredients == null || ingredients.size() != 1
                    || others == null || !others.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "Extra Utilities Soul Crafting must expose one input and no catalysts: "
                                + label);
            }
            requireSingleExactPositionedStack(
                    ingredients.get(0), 47, 3, "ExtraUtilities:ethericsword", 0,
                    "Extra Utilities Soul Crafting input (" + label + ")");
            requireSingleExactPositionedStack(
                    result, 103, 13, "ExtraUtilities:mini-soul", 0,
                    "Extra Utilities Soul Crafting canonical NEI result (" + label + ")");
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Extra Utilities Soul Crafting page inspection failed: " + label,
                    unwrap(error));
        }
    }

    private static void requireSingleExactPositionedStack(
            PositionedStack positioned, int expectedX, int expectedY,
            String registryId, int metadata, String label) throws ExportFailure {
        requireDefensivePositionedStackShape(
                positioned, expectedX, expectedY, label);
        requireExactItemIdentity(
                positioned.items[0], registryId, metadata,
                label + " serialized alternative");
        requireExactItemIdentity(
                positioned.item, registryId, metadata,
                label + " current render copy");
    }

    /** Package-private seam for the pinned NEI defensive-copy contract tests. */
    static void requireDefensivePositionedStackShape(
            PositionedStack positioned, int expectedX, int expectedY,
            String label) throws ExportFailure {
        if (positioned == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " PositionedStack is null");
        }
        if (positioned.relx != expectedX || positioned.rely != expectedY) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " coordinates drifted; expected (" + expectedX + "," + expectedY
                    + "), got (" + positioned.relx + "," + positioned.rely + ")");
        }
        if (positioned.items == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " serialized alternatives array is null");
        }
        if (positioned.items.length != 1) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " must retain exactly one serialized alternative; got "
                    + positioned.items.length);
        }
        ItemStack alternative = positioned.items[0];
        if (alternative == null || alternative.getItem() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " serialized alternative is null/empty");
        }
        ItemStack current = positioned.item;
        if (current == null || current.getItem() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " current render stack is null/empty");
        }
        if (current == alternative) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " current render stack must be NEI's defensive copy, not the "
                    + "serialized-alternative object");
        }
        NBTTagCompound alternativeTag = alternative.getTagCompound();
        NBTTagCompound currentTag = current.getTagCompound();
        if (alternativeTag != null && currentTag == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " current render copy dropped its serialized alternative's NBT");
        }
        if (alternativeTag == null && currentTag != null) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " current render copy acquired NBT absent from its serialized "
                    + "alternative");
        }
        if (alternativeTag != null && currentTag == alternativeTag) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " current render copy aliases its serialized alternative's nonnull "
                    + "NBTTagCompound instead of retaining NEI's deep defensive copy");
        }
        if (current.getItem() != alternative.getItem()
                || current.stackSize != alternative.stackSize
                || current.getItemDamage() != alternative.getItemDamage()
                || !canonicalNullableNbt(current.getTagCompound()).equals(
                canonicalNullableNbt(alternative.getTagCompound()))) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " current render copy differs from its serialized alternative; "
                    + "alternative=" + positionedStackDiagnostic(alternative)
                    + ", current=" + positionedStackDiagnostic(current));
        }
    }

    private static String canonicalNullableNbt(NBTTagCompound tag) {
        return tag == null ? "-" : NbtCanonicalizer.canonical(tag);
    }

    private static String positionedStackDiagnostic(ItemStack stack) {
        if (stack == null) {
            return "<null>";
        }
        return "itemClass="
                + (stack.getItem() == null ? "<null>" : stack.getItem().getClass().getName())
                + ",meta=" + stack.getItemDamage()
                + ",amount=" + stack.stackSize
                + ",nbt=" + canonicalNullableNbt(stack.getTagCompound());
    }

    private static void requireSingleRuntimeSoulRecipe() throws ExportFailure {
        try {
            List<?> recipes = CraftingManager.getInstance().getRecipeList();
            if (recipes == null) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "CraftingManager returned a null recipe registry");
            }
            int matching = 0;
            for (Object rawRecipe : recipes) {
                if (rawRecipe != null
                        && EXTRAUTILITIES_SOUL_RECIPE_CLASS.equals(
                                rawRecipe.getClass().getName())) {
                    matching++;
                    if (!(rawRecipe instanceof IRecipe)) {
                        throw new ExportFailure("HANDLER_AMBIGUOUS",
                                EXTRAUTILITIES_SOUL_RECIPE_CLASS
                                        + " no longer implements IRecipe");
                    }
                    IRecipe recipe = (IRecipe) rawRecipe;
                    if (recipe.getRecipeSize() != 1) {
                        throw new ExportFailure("RECIPE_SEMANTICS",
                                "Extra Utilities RecipeSoul size drifted; expected 1, got "
                                        + recipe.getRecipeSize());
                    }
                    requireExactItemIdentity(
                            recipe.getRecipeOutput(), "ExtraUtilities:mini-soul", 0,
                            "Extra Utilities RecipeSoul canonical registry output");
                }
            }
            requireCount("live Extra Utilities RecipeSoul registrations", 1, matching);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not verify the live Extra Utilities RecipeSoul registration",
                    unwrap(error));
        }
    }

    private static Item requirePublicStaticItem(Class<?> owner, String name)
            throws Exception {
        Field field = owner.getField(name);
        if (!Modifier.isPublic(field.getModifiers())
                || !Modifier.isStatic(field.getModifiers())
                || !Item.class.isAssignableFrom(field.getType())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", owner.getName() + "." + name
                    + " must remain a public static Item field");
        }
        Object value = field.get(null);
        if (!(value instanceof Item)) {
            throw new ExportFailure("HANDLER_UNLOADED", owner.getName() + "." + name
                    + " is null or not an Item");
        }
        return (Item) value;
    }

    private static void requireExactItemIdentity(
            ItemStack stack, String registryId, int metadata, String label)
            throws ExportFailure {
        StackIdentity identity = StackIdentity.of(stack);
        if (!registryId.equals(identity.registryId) || identity.metadata != metadata
                || identity.amount != 1 || identity.canonicalNbt != null) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " identity drifted; got " + identity.key + " amount=" + identity.amount);
        }
    }

    private static ICraftingHandler loadPinnedSpaceRecipeId(ICraftingHandler prototype)
            throws ExportFailure {
        String handlerId = prototype.getHandlerId();
        SpaceRecipeSpec spec = SPACE_RECIPE_SPECS.get(handlerId);
        if (spec == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", "no pinned space-recipe "
                    + "adapter contract exists for " + handlerId);
        }
        try {
            requireExactClass(prototype, spec.handlerClass);
            requireCount(handlerId + " prototype recipe count", 0, prototype.numRecipes());
            requireSpaceRecipeQueryId(prototype, handlerId, spec);
            ICraftingHandler loaded = prototype.getRecipeHandler(spec.queryId);
            requireExactClass(loaded, spec.handlerClass);
            if (!handlerId.equals(loaded.getHandlerId())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                        + " query loaded handler ID " + loaded.getHandlerId());
            }
            requireSpaceRecipeQueryId(loaded, handlerId, spec);
            requireCount(handlerId + " complete query recipe count",
                    spec.recipeCount, loaded.numRecipes());

            SpaceRecipeSnapshot snapshot = completedSpaceRecipeSnapshot;
            if (snapshot == null) {
                throw new ExportFailure("HANDLER_UNLOADED", handlerId
                        + " has no readiness-time canonical space-recipe snapshot");
            }
            List<String> expectedPages = snapshot.pageKeys.get(handlerId);
            if (expectedPages == null || expectedPages.size() != spec.recipeCount) {
                throw new ExportFailure("HANDLER_UNLOADED", handlerId
                        + " is absent or incomplete in the readiness-time space-recipe snapshot");
            }
            Object expectedBacking = snapshot.backingCollections.get(handlerId);
            Object loadedBacking = spaceRecipeBackingCollection(loaded, handlerId, spec);
            if (expectedBacking == null || loadedBacking != expectedBacking) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                        + " loaded query no longer shares the readiness-audited backing Set");
            }
            sortLoadedSpaceRecipes((TemplateRecipeHandler) loaded, handlerId, expectedPages);
            return loaded;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED", handlerId
                    + " exact canonical space-recipe adapter failed", unwrap(error));
        }
    }

    private static void requireSpaceRecipeQueryId(
            ICraftingHandler handler, String handlerId, SpaceRecipeSpec spec) throws Exception {
        Method queryMethod;
        if (spec.galaxyTier > 0) {
            queryMethod = exactDeclaredReturningMethod(
                    handler.getClass(), "getRecipeId", String.class);
            if (!Modifier.isPrivate(queryMethod.getModifiers())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                        + " GalaxySpace getRecipeId() is no longer private");
            }
            Field tier = exactDeclaredField(handler.getClass(), "tier", int.class);
            if (!Modifier.isPrivate(tier.getModifiers())
                    || !Modifier.isFinal(tier.getModifiers())
                    || tier.getInt(handler) != spec.galaxyTier) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                        + " GalaxySpace tier field drifted; expected " + spec.galaxyTier);
            }
        } else {
            queryMethod = exactPublicReturningMethod(
                    handler.getClass(), "getRecipeId", String.class);
            if (queryMethod.getDeclaringClass() != handler.getClass()) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                        + " getRecipeId() is no longer directly declared");
            }
        }
        if (Modifier.isStatic(queryMethod.getModifiers())
                || queryMethod.isBridge() || queryMethod.isSynthetic()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                    + " getRecipeId() modifier contract drifted");
        }
        Object actual = queryMethod.invoke(handler);
        if (!spec.queryId.equals(actual)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                    + " recipe query ID drifted; expected " + spec.queryId
                    + ", got " + actual);
        }
    }

    private static Object spaceRecipeBackingCollection(
            ICraftingHandler handler, String handlerId, SpaceRecipeSpec spec) throws Exception {
        final Object value;
        if (spec.galaxyTier > 0) {
            Field recipes = exactDeclaredField(handler.getClass(), "recipes", Set.class);
            if (!Modifier.isPrivate(recipes.getModifiers())
                    || Modifier.isStatic(recipes.getModifiers())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                        + " GalaxySpace recipes must remain an instance-private Set");
            }
            value = recipes.get(handler);
        } else {
            Method getRecipes = exactPublicReturningMethod(
                    handler.getClass(), "getRecipes", Set.class);
            if (getRecipes.getDeclaringClass() != handler.getClass()
                    || Modifier.isStatic(getRecipes.getModifiers())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                        + " getRecipes() declaration contract drifted");
            }
            value = getRecipes.invoke(handler);
        }
        if (!(value instanceof Set)) {
            throw new ExportFailure("HANDLER_UNLOADED", handlerId
                    + " recipe backing collection is null or not a Set");
        }
        return value;
    }

    private static void sortLoadedSpaceRecipes(
            TemplateRecipeHandler loaded, String handlerId,
            List<String> expectedPageKeys) throws ExportFailure {
        try {
            List<SpacePageOrder> pages = new ArrayList<SpacePageOrder>(loaded.numRecipes());
            for (int index = 0; index < loaded.numRecipes(); index++) {
                String key = spacePageCanonical(
                        positionedList(loaded.getIngredientStacks(index),
                                handlerId + " inputs #" + index),
                        positionedList(loaded.getOtherStacks(index),
                                handlerId + " other stacks #" + index),
                        loaded.getResultStack(index), handlerId + " page #" + index);
                pages.add(new SpacePageOrder(loaded.arecipes.get(index), key));
            }
            Collections.sort(pages, new Comparator<SpacePageOrder>() {
                @Override
                public int compare(SpacePageOrder left, SpacePageOrder right) {
                    return left.key.compareTo(right.key);
                }
            });
            List<String> actual = new ArrayList<String>(pages.size());
            String previous = null;
            for (SpacePageOrder page : pages) {
                if (previous != null && previous.equals(page.key)) {
                    throw new ExportFailure("HANDLER_DUPLICATE", handlerId
                            + " contains duplicate canonical NASA-workbench layouts");
                }
                previous = page.key;
                actual.add(page.key);
            }
            if (!expectedPageKeys.equals(actual)) {
                throw new ExportFailure("RECIPE_SEMANTICS", handlerId
                        + " loaded query pages differ from the readiness-audited canonical Set");
            }
            loaded.arecipes.clear();
            for (SpacePageOrder page : pages) {
                addRawCachedRecipe(loaded, page.cachedRecipe);
            }
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("RECIPE_SEMANTICS", handlerId
                    + " canonical page ordering failed", unwrap(error));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addRawCachedRecipe(TemplateRecipeHandler handler, Object cachedRecipe) {
        ((List) handler.arecipes).add(cachedRecipe);
    }

    private static final class SpacePageOrder {
        final Object cachedRecipe;
        final String key;

        SpacePageOrder(Object cachedRecipe, String key) {
            this.cachedRecipe = cachedRecipe;
            this.key = key;
        }
    }

    private static String spacePageCanonical(
            List<PositionedStack> inputs, List<PositionedStack> others,
            PositionedStack result, String label) throws ExportFailure {
        return spacePageCanonical(inputs, others, result, label, true);
    }

    private static String spacePageCanonical(
            List<PositionedStack> inputs, List<PositionedStack> others,
            PositionedStack result, String label, boolean includeCoordinates)
            throws ExportFailure {
        if (result == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", label + " has no result stack");
        }
        StringBuilder canonical = new StringBuilder(1024);
        appendPositionedRole(canonical, 'I', inputs, label, includeCoordinates);
        appendPositionedRole(canonical, 'O', others, label, includeCoordinates);
        canonical.append('R');
        appendCanonicalField(canonical, positionedStackCanonical(
                result, label + " result", includeCoordinates));
        return canonical.toString();
    }

    private static void appendPositionedRole(
            StringBuilder canonical, char role, List<PositionedStack> stacks,
            String label, boolean includeCoordinates) throws ExportFailure {
        List<String> values = new ArrayList<String>(stacks.size());
        for (int index = 0; index < stacks.size(); index++) {
            values.add(positionedStackCanonical(
                    stacks.get(index), label + " " + role + " #" + index,
                    includeCoordinates));
        }
        Collections.sort(values);
        canonical.append(role).append(values.size()).append(';');
        for (String value : values) {
            appendCanonicalField(canonical, value);
        }
    }

    private static String positionedStackCanonical(PositionedStack positioned, String label)
            throws ExportFailure {
        return positionedStackCanonical(positioned, label, true);
    }

    private static String positionedStackCanonical(
            PositionedStack positioned, String label, boolean includeCoordinates)
            throws ExportFailure {
        if (positioned == null || positioned.items == null || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " has no PositionedStack alternatives");
        }
        List<String> alternatives = new ArrayList<String>(positioned.items.length);
        for (int index = 0; index < positioned.items.length; index++) {
            ItemStack stack = positioned.items[index];
            if (stack == null || stack.getItem() == null) {
                throw new ExportFailure("RECIPE_SEMANTICS", label
                        + " contains a null/empty alternative #" + index);
            }
            StackIdentity identity = StackIdentity.of(stack);
            alternatives.add(canonicalStackIdentity(identity, identity.amount));
        }
        Collections.sort(alternatives);
        StringBuilder canonical = new StringBuilder(256 * alternatives.size());
        if (includeCoordinates) {
            canonical.append(positioned.rely).append(',').append(positioned.relx)
                    .append(';');
        }
        canonical.append(alternatives.size()).append(';');
        for (String alternative : alternatives) {
            appendCanonicalField(canonical, alternative);
        }
        return canonical.toString();
    }

    private static ICraftingHandler loadAe2WorldCrafting(ICraftingHandler prototype)
            throws ExportFailure {
        try {
            requireCount("AE2 in-world prototype recipe count", 0, prototype.numRecipes());
            ClassLoader loader = prototype.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(AE2_API_CLASS, false, loader);
            Class<?> apiInterface = Class.forName(AE2_API_INTERFACE, false, loader);
            Class<?> definitionsInterface =
                    Class.forName(AE2_DEFINITIONS_INTERFACE, false, loader);
            Class<?> materialsInterface =
                    Class.forName(AE2_MATERIALS_INTERFACE, false, loader);
            Class<?> definitionInterface =
                    Class.forName(AE2_ITEM_DEFINITION_INTERFACE, false, loader);
            Class<?> optionalClass = Class.forName(AE2_OPTIONAL_CLASS, false, loader);
            requireInterface(apiInterface);
            requireInterface(definitionsInterface);
            requireInterface(materialsInterface);
            requireInterface(definitionInterface);

            Method instance = exactPublicReturningMethod(
                    apiClass, "instance", apiInterface);
            if (!Modifier.isStatic(instance.getModifiers())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "AEApi.instance() is no longer static");
            }
            Object api = instance.invoke(null);
            if (!apiInterface.isInstance(api)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "AEApi.instance() did not return the exact IAppEngApi interface");
            }
            Method definitions = exactPublicReturningMethod(
                    apiInterface, "definitions", definitionsInterface);
            Object definitionsValue = definitions.invoke(api);
            if (!definitionsInterface.isInstance(definitionsValue)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "AE2 definitions() returned the wrong runtime type");
            }
            Method materials = exactPublicReturningMethod(
                    definitionsInterface, "materials", materialsInterface);
            Object materialsValue = materials.invoke(definitionsValue);
            if (!materialsInterface.isInstance(materialsValue)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "AE2 materials() returned the wrong runtime type");
            }
            Method maybeStack = exactPublicReturningMethod(
                    definitionInterface, "maybeStack", optionalClass, int.class);
            Method asSet = exactPublicReturningMethod(optionalClass, "asSet", Set.class);

            Ae2InformationSpec[] specs = new Ae2InformationSpec[] {
                    new Ae2InformationSpec("certusQuartzCrystalCharged", 1, false),
                    new Ae2InformationSpec("logicProcessorPress", 15, true),
                    new Ae2InformationSpec("calcProcessorPress", 13, true),
                    new Ae2InformationSpec("engProcessorPress", 14, true),
                    new Ae2InformationSpec("fluixCrystal", 7, true),
                    new Ae2InformationSpec("qESingularity", 48, true),
                    new Ae2InformationSpec("purifiedCertusQuartzCrystal", 10, true),
                    new Ae2InformationSpec("purifiedNetherQuartzCrystal", 11, true),
                    new Ae2InformationSpec("purifiedFluixCrystal", 12, true)
            };
            List<ItemStack> enabledTargets =
                    new ArrayList<ItemStack>(EXPECTED_AE2_INFORMATION_PAGES);
            StringBuilder canonical = new StringBuilder(1024);
            canonical.append("ae2-in-world-crafting-wildcard-query-closure-v1\n");
            for (Ae2InformationSpec spec : specs) {
                Method definition = exactPublicReturningMethod(
                        materialsInterface, spec.definitionMethod, definitionInterface);
                Object definitionValue = definition.invoke(materialsValue);
                if (!definitionInterface.isInstance(definitionValue)) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "AE2 material definition returned the wrong runtime type: "
                                    + spec.definitionMethod);
                }
                Object optional = maybeStack.invoke(definitionValue, Integer.valueOf(1));
                if (!optionalClass.isInstance(optional)) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "AE2 maybeStack returned the wrong Optional type for "
                                    + spec.definitionMethod);
                }
                Object rawSet = asSet.invoke(optional);
                if (!(rawSet instanceof Set) || ((Set<?>) rawSet).size() != 1) {
                    throw new ExportFailure("HANDLER_UNLOADED",
                            "AE2 material definition must resolve to exactly one stack: "
                                    + spec.definitionMethod);
                }
                Object rawStack = ((Set<?>) rawSet).iterator().next();
                if (!(rawStack instanceof ItemStack)) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "AE2 material definition did not resolve to an ItemStack: "
                                    + spec.definitionMethod);
                }
                ItemStack target = ((ItemStack) rawStack).copy();
                StackIdentity identity = StackIdentity.of(target);
                if (!AE2_MATERIAL_REGISTRY_ID.equals(identity.registryId)
                        || identity.metadata != spec.metadata || identity.amount != 1
                        || identity.canonicalNbt != null) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "AE2 information target drifted for " + spec.definitionMethod
                                    + "; got " + identity.key + " amount=" + identity.amount);
                }
                ICraftingHandler exactQuery =
                        prototype.getRecipeHandler("item", target.copy());
                requireExactDirectHandlerClass(exactQuery, AE2_WORLD_CRAFTING_HANDLER);
                requireCount("AE2 exact information query " + spec.definitionMethod,
                        spec.enabled ? 1 : 0, exactQuery.numRecipes());
                canonical.append(spec.definitionMethod).append('|')
                        .append(spec.enabled).append('|').append(identity.key).append('\n');
                if (spec.enabled) {
                    requireAe2InformationPage(exactQuery, 0, target, spec.definitionMethod);
                    enabledTargets.add(target);
                }
            }
            requireCount("AE2 enabled in-world information targets",
                    EXPECTED_AE2_INFORMATION_PAGES, enabledTargets.size());

            ItemStack wildcard = enabledTargets.get(0).copy();
            wildcard.setItemDamage(OreDictionary.WILDCARD_VALUE);
            ICraftingHandler closure = prototype.getRecipeHandler("item", wildcard);
            requireExactDirectHandlerClass(closure, AE2_WORLD_CRAFTING_HANDLER);
            requireCount("AE2 wildcard-query information closure",
                    EXPECTED_AE2_INFORMATION_PAGES, closure.numRecipes());
            for (int index = 0; index < enabledTargets.size(); index++) {
                requireAe2InformationPage(
                        closure, index, enabledTargets.get(index),
                        "wildcard closure #" + index);
            }
            requireCount("AE2 in-world prototype recipe count after adapter load",
                    0, prototype.numRecipes());
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] AE2 informational wildcard-query closure ready: "
                            + "pages={}, fingerprint={}",
                    closure.numRecipes(), Naming.sha256(canonical.toString()));
            return closure;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    "AE2 exact informational-category adapter failed", unwrap(error));
        }
    }

    private static void requireAe2InformationPage(
            ICraftingHandler handler, int index, ItemStack expected, String label)
            throws ExportFailure {
        try {
            List<PositionedStack> inputs = handler.getIngredientStacks(index);
            List<PositionedStack> others = handler.getOtherStacks(index);
            PositionedStack result = handler.getResultStack(index);
            if (inputs == null || !inputs.isEmpty() || others == null || !others.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "AE2 information page must have no material inputs/catalysts: " + label);
            }
            if (result == null || result.item == null || result.relx != 75 || result.rely != 4) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "AE2 information page result/position drifted: " + label);
            }
            StackIdentity expectedIdentity = StackIdentity.of(expected);
            StackIdentity resultIdentity = StackIdentity.of(result.item);
            if (!expectedIdentity.sameLogicalIdentity(resultIdentity)
                    || resultIdentity.amount != 1) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "AE2 information page result drifted: " + label);
            }
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "AE2 information page inspection failed: " + label, unwrap(error));
        }
    }

    private static final class Ae2InformationSpec {
        final String definitionMethod;
        final int metadata;
        final boolean enabled;

        Ae2InformationSpec(String definitionMethod, int metadata, boolean enabled) {
            this.definitionMethod = definitionMethod;
            this.metadata = metadata;
            this.enabled = enabled;
        }
    }

    static synchronized RecipeSemanticOverride semanticOverride(
            Adapter adapter, ICraftingHandler loadedHandler, int recipeIndex)
            throws ExportFailure {
        if (adapter == Adapter.FORESTRY_FLUID_SEMANTICS) {
            return ForestryFluidSemanticAdapter.semanticOverride(
                    loadedHandler, recipeIndex);
        }
        if (adapter == Adapter.GENDUSTRY_MACHINE_SEMANTICS) {
            return GendustryMachineSemanticAdapter.semanticOverride(
                    loadedHandler, recipeIndex);
        }
        if (adapter == Adapter.BINNIE_INCUBATOR_SEMANTICS) {
            return BinnieIncubatorSemanticAdapter.semanticOverride(
                    loadedHandler, recipeIndex);
        }
        if (adapter == Adapter.BINNIE_GENEPOOL_SEMANTICS) {
            return BinnieGenepoolSemanticAdapter.semanticOverride(
                    loadedHandler, recipeIndex);
        }
        if (adapter == Adapter.MOBSINFO_INFORMATIONAL_SEMANTICS) {
            return MobsInfoSemanticAdapter.semanticOverride(
                    loadedHandler, recipeIndex);
        }
        if (adapter == Adapter.MOBSINFO_INFERNAL_INFORMATIONAL_SEMANTICS) {
            return MobsInfoInfernalSemanticAdapter.semanticOverride(
                    loadedHandler, recipeIndex);
        }
        if (adapter == Adapter.MOBSINFO_VILLAGER_INFORMATIONAL_SEMANTICS) {
            return MobsInfoVillagerTradeSemanticAdapter.semanticOverride(
                    loadedHandler, recipeIndex);
        }
        if (adapter == Adapter.TCONSTRUCT_MELTING_FLUID_SEMANTICS) {
            return TconstructMeltingSemanticAdapter.semanticOverride(
                    loadedHandler, recipeIndex);
        }
        if (adapter == Adapter.TCONSTRUCT_ALLOYING_FLUID_SEMANTICS) {
            return TconstructAlloyingSemanticAdapter.semanticOverride(
                    loadedHandler, recipeIndex);
        }
        if (adapter == Adapter.BUILDCRAFT_REFINERY_FLUID_SEMANTICS) {
            return BuildcraftRefinerySemanticAdapter.semanticOverride(
                    loadedHandler, recipeIndex);
        }
        if (adapter == Adapter.ENDERIO_VAT_FLUID_SEMANTICS) {
            return EnderIoVatSemanticAdapter.semanticOverride(
                    loadedHandler, recipeIndex);
        }
        if (adapter == Adapter.GALACTICRAFT_CIRCUIT_FABRICATOR_SEMANTICS) {
            return GalacticraftCircuitFabricatorSemanticAdapter.semanticOverride(
                    loadedHandler, recipeIndex);
        }
        final String expectedHandler;
        final String adapterName;
        if (adapter == Adapter.BETTER_QUESTING) {
            expectedHandler = BETTER_QUESTING_HANDLER;
            adapterName = "BetterQuesting";
        } else if (adapter == Adapter.IC2_CROP_BREEDING) {
            expectedHandler = CROP_HANDLER;
            adapterName = "IC2 Crop Plugin";
        } else {
            return null;
        }
        requireExactClass(loadedHandler, expectedHandler);
        List<RecipeSemanticOverride> pages =
                SEMANTIC_OVERRIDES_BY_HANDLER.get(loadedHandler);
        if (pages == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    adapterName + " loaded handler has no graph-semantic overrides");
        }
        if (loadedHandler.numRecipes() != pages.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    adapterName + " preview/graph page counts diverged; previews="
                            + loadedHandler.numRecipes() + ", graph=" + pages.size());
        }
        if (recipeIndex < 0 || recipeIndex >= pages.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    adapterName + " graph-semantic page index is out of bounds: "
                            + recipeIndex);
        }
        return pages.get(recipeIndex);
    }

    static synchronized RuntimeReadiness inspectPinnedRuntime(List<ICraftingHandler> prototypes)
            throws ExportFailure {
        return inspectPinnedRuntime(prototypes, false);
    }

    /**
     * Performs the same readiness checks plus a fresh space-page recapture and compact crop
     * derivation-basis audit.
     */
    static synchronized RuntimeReadiness auditPinnedRuntime(List<ICraftingHandler> prototypes)
            throws ExportFailure {
        return inspectPinnedRuntime(prototypes, true);
    }

    private static int totalSpaceRecipePages(SpaceRecipeSnapshot snapshot) {
        int total = 0;
        for (List<String> pages : snapshot.pageKeys.values()) {
            total += pages.size();
        }
        return total;
    }

    private static SpaceRecipeSnapshot captureSpaceRecipeSnapshot(
            Map<String, ICraftingHandler> special) throws ExportFailure {
        try {
            Map<String, ICraftingHandler> snapshotPrototypes =
                    new LinkedHashMap<String, ICraftingHandler>();
            Map<String, Object> backingCollections =
                    new LinkedHashMap<String, Object>();
            Map<String, List<String>> pageKeys =
                    new LinkedHashMap<String, List<String>>();
            List<String> handlerIds = new ArrayList<String>(SPACE_RECIPE_SPECS.keySet());
            Collections.sort(handlerIds);
            StringBuilder canonical = new StringBuilder(64 * 1024);
            canonical.append("gtnh-space-recipe-layout-corpus-v1\n");

            for (String handlerId : handlerIds) {
                SpaceRecipeSpec spec = SPACE_RECIPE_SPECS.get(handlerId);
                ICraftingHandler prototype = special.get(handlerId);
                if (prototype == null) {
                    throw new ExportFailure("HANDLER_UNLOADED", handlerId
                            + " is absent from the space-recipe readiness registry");
                }
                requireExactClass(prototype, spec.handlerClass);
                if (!handlerId.equals(prototype.getHandlerId())) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                            + " prototype changed handler ID to "
                            + prototype.getHandlerId());
                }
                requireCount(handlerId + " prototype recipe count", 0,
                        prototype.numRecipes());
                requireSpaceRecipeQueryId(prototype, handlerId, spec);
                Object backing = spaceRecipeBackingCollection(prototype, handlerId, spec);
                requireCount(handlerId + " backing recipe count", spec.recipeCount,
                        ((Set<?>) backing).size());

                ICraftingHandler queried = prototype.getRecipeHandler(spec.queryId);
                requireExactClass(queried, spec.handlerClass);
                if (queried == prototype) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                            + " complete-category query reused its mutable prototype");
                }
                if (!handlerId.equals(queried.getHandlerId())) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                            + " query loaded handler ID " + queried.getHandlerId());
                }
                requireSpaceRecipeQueryId(queried, handlerId, spec);
                requireCount(handlerId + " complete query recipe count",
                        spec.recipeCount, queried.numRecipes());
                requireCount(handlerId + " loaded arecipes count",
                        spec.recipeCount,
                        ((TemplateRecipeHandler) queried).arecipes.size());
                Object queriedBacking = spaceRecipeBackingCollection(
                        queried, handlerId, spec);
                if (queriedBacking != backing) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                            + " complete query no longer shares the prototype backing Set");
                }
                requireCount(handlerId + " post-query prototype recipe count", 0,
                        prototype.numRecipes());

                List<String> canonicalPages = captureCanonicalSpacePages(
                        (TemplateRecipeHandler) queried, handlerId, spec);
                snapshotPrototypes.put(handlerId, prototype);
                backingCollections.put(handlerId, backing);
                pageKeys.put(handlerId, canonicalPages);

                canonical.append('H');
                appendCanonicalField(canonical, handlerId);
                appendCanonicalField(canonical, spec.handlerClass);
                appendCanonicalField(canonical, spec.queryId);
                canonical.append(spec.recipeCount).append(';');
                for (String pageKey : canonicalPages) {
                    canonical.append('P');
                    appendCanonicalField(canonical, pageKey);
                }
                canonical.append('\n');
            }
            return new SpaceRecipeSnapshot(snapshotPrototypes, backingCollections,
                    pageKeys, Naming.sha256(canonical.toString()));
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not capture the exact canonical space-recipe corpus",
                    unwrap(error));
        }
    }

    private static List<String> captureCanonicalSpacePages(
            TemplateRecipeHandler loaded, String handlerId, SpaceRecipeSpec spec)
            throws ExportFailure {
        List<String> layoutKeys = new ArrayList<String>(spec.recipeCount);
        Map<String, Integer> materialMultiplicities = new HashMap<String, Integer>();
        for (int index = 0; index < loaded.numRecipes(); index++) {
            List<PositionedStack> inputs = positionedList(
                    loaded.getIngredientStacks(index),
                    handlerId + " readiness inputs #" + index);
            List<PositionedStack> others = positionedList(
                    loaded.getOtherStacks(index),
                    handlerId + " readiness other stacks #" + index);
            PositionedStack result = loaded.getResultStack(index);
            String label = handlerId + " readiness page #" + index;
            layoutKeys.add(spacePageCanonical(inputs, others, result, label));
            String materialKey = spacePageCanonical(
                    inputs, others, result, label, false);
            Integer previous = materialMultiplicities.get(materialKey);
            materialMultiplicities.put(materialKey,
                    Integer.valueOf(previous == null ? 1 : previous.intValue() + 1));
        }
        Collections.sort(layoutKeys);
        String previous = null;
        for (String layoutKey : layoutKeys) {
            if (layoutKey.equals(previous)) {
                throw new ExportFailure("HANDLER_DUPLICATE", handlerId
                        + " contains duplicate canonical NASA-workbench layouts");
            }
            previous = layoutKey;
        }
        requireCount(handlerId + " unique canonical layout count",
                spec.recipeCount, layoutKeys.size());

        if (spec.galaxyTier == 0) {
            List<Integer> actualHistogram =
                    new ArrayList<Integer>(materialMultiplicities.values());
            Collections.sort(actualHistogram);
            List<Integer> expectedHistogram = new ArrayList<Integer>();
            int[] expected = new int[] {1, 1, 1, 3, 3, 3, 3, 3, 3, 6};
            for (int multiplicity : expected) {
                expectedHistogram.add(Integer.valueOf(multiplicity));
            }
            if (!expectedHistogram.equals(actualHistogram)) {
                throw new ExportFailure("RECIPE_SEMANTICS", handlerId
                        + " coordinate-free material/layout multiplicities drifted; expected "
                        + expectedHistogram + ", got " + actualHistogram);
            }
        }
        return layoutKeys;
    }

    private static void requireSameSpaceRecipeSnapshotIdentities(
            SpaceRecipeSnapshot baseline, Map<String, ICraftingHandler> special)
            throws ExportFailure {
        for (Map.Entry<String, SpaceRecipeSpec> entry : SPACE_RECIPE_SPECS.entrySet()) {
            String handlerId = entry.getKey();
            SpaceRecipeSpec spec = entry.getValue();
            ICraftingHandler prototype = special.get(handlerId);
            if (prototype == null || baseline.prototypes.get(handlerId) != prototype) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                        + " prototype identity changed after space-recipe readiness capture");
            }
            requireExactClass(prototype, spec.handlerClass);
            requireCount(handlerId + " prototype recipe count", 0,
                    prototype.numRecipes());
            try {
                requireSpaceRecipeQueryId(prototype, handlerId, spec);
                Object backing = spaceRecipeBackingCollection(prototype, handlerId, spec);
                if (baseline.backingCollections.get(handlerId) != backing) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                            + " backing Set identity changed after readiness capture");
                }
                requireCount(handlerId + " backing recipe count", spec.recipeCount,
                        ((Set<?>) backing).size());
                List<String> expectedPages = baseline.pageKeys.get(handlerId);
                if (expectedPages == null || expectedPages.size() != spec.recipeCount) {
                    throw new ExportFailure("HANDLER_UNLOADED", handlerId
                            + " readiness snapshot lost its complete canonical page set");
                }
            } catch (ExportFailure failure) {
                throw failure;
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                throw new ExportFailure("HANDLER_UNLOADED", handlerId
                        + " space-recipe readiness identity audit failed", unwrap(error));
            }
        }
    }

    private static RuntimeReadiness inspectPinnedRuntime(
            List<ICraftingHandler> prototypes, boolean deepCropAudit) throws ExportFailure {
        Map<String, ICraftingHandler> special = new HashMap<String, ICraftingHandler>();
        for (ICraftingHandler prototype : prototypes) {
            if (prototype == null) {
                continue;
            }
            String handlerId = prototype.getHandlerId();
            PolicySpec spec = POLICY_SPECS.get(handlerId);
            if (spec != null) {
                if (!spec.handlerClass.equals(prototype.getClass().getName())) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS", handlerId
                            + " readiness runtime class drifted; expected "
                            + spec.handlerClass + ", got "
                            + prototype.getClass().getName());
                }
                ICraftingHandler duplicate = special.put(handlerId, prototype);
                if (duplicate != null) {
                    throw new ExportFailure("HANDLER_DUPLICATE",
                            "special handler is duplicated during adapter readiness: "
                                    + handlerId + " (" + spec.handlerClass + ")");
                }
            }
        }
        requireAllPinnedPolicies(special.keySet());

        SpaceRecipeSnapshot spaceSnapshot = completedSpaceRecipeSnapshot;
        if (spaceSnapshot == null) {
            spaceSnapshot = captureSpaceRecipeSnapshot(special);
            completedSpaceRecipeSnapshot = spaceSnapshot;
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Canonical space-recipe corpus ready; "
                            + "handlers={}, pages={}, fingerprint={}",
                    spaceSnapshot.pageKeys.size(), totalSpaceRecipePages(spaceSnapshot),
                    spaceSnapshot.fingerprint);
        } else {
            requireSameSpaceRecipeSnapshotIdentities(spaceSnapshot, special);
            if (deepCropAudit) {
                SpaceRecipeSnapshot recaptured = captureSpaceRecipeSnapshot(special);
                if (!spaceSnapshot.fingerprint.equals(recaptured.fingerprint)) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "canonical space-recipe corpus changed during the supervised JVM; "
                                    + "ready=" + spaceSnapshot.fingerprint
                                    + ", audit=" + recaptured.fingerprint);
                }
            }
        }

        ICraftingHandler quest = special.get(BETTER_QUESTING_HANDLER);
        QuestEntries questEntries = questEntries(quest.getClass().getClassLoader());
        if (questEntries.entries.size() < EXPECTED_QUEST_DATABASE_ENTRIES) {
            return new RuntimeReadiness(false, null,
                    "BetterQuesting database " + questEntries.entries.size() + "/"
                            + EXPECTED_QUEST_DATABASE_ENTRIES);
        }
        requireQuestCorpus(questEntries);
        QuestSemanticCorpus questSemantics = cachedQuestSemanticCorpus(
                questEntries, quest.getClass().getClassLoader());

        ICraftingHandler crop = special.get(CROP_HANDLER);
        ClassLoader cropLoader = crop.getClass().getClassLoader();
        requireCropSticks(cropLoader);
        List<Thread> originalWorkers = liveThreadsNamed(ORIGINAL_CROP_WORKER_NAME);
        if (originalWorkers.size() > 1) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", "more than one live original crop-cache worker: "
                    + originalWorkers.size());
        }
        if (!originalWorkers.isEmpty()) {
            return new RuntimeReadiness(false, null,
                    "waiting for original " + ORIGINAL_CROP_WORKER_NAME);
        }

        Object breeder = cropBreeder(cropLoader);
        if (!exporterCropRecomputeStarted) {
            startExporterCropRecompute(breeder);
            return new RuntimeReadiness(false, null,
                    "started exporter-owned crop-cache recomputation");
        }
        if (breeder != exporterCropBreeder) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 Crop Plugin Breeder.INSTANCE changed during readiness");
        }
        Thread worker = exporterCropWorker;
        if (worker == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "exporter-owned crop recomputation lost its worker reference");
        }
        if (worker.isAlive()) {
            return new RuntimeReadiness(false, null,
                    "waiting for exporter-owned crop-cache recomputation");
        }
        if (exporterCropFailure != null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "exporter-owned crop-cache recomputation failed", exporterCropFailure);
        }

        CropSnapshot cropSnapshot = completedCropSnapshot;
        if (cropSnapshot != null && cropSnapshot.breeder != breeder) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "cached IC2 Crop Plugin snapshot belongs to another Breeder.INSTANCE");
        }
        if (requiresFreshCropSnapshot(cropSnapshot != null, deepCropAudit)) {
            CropSnapshot captured = snapshotCropReplay(cropLoader);
            cropSnapshot = captured;
            completedCropSnapshot = cropSnapshot;
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Deterministic crop replay ready after "
                            + "happens-before recomputation; crops={}, pairs={}, "
                            + "simulatorCandidates={}, queryBucketClosure={}, "
                            + "canonicalGlobalMatchKeys={}, craftWinners={}, "
                            + "usageWinnerIdentities={}, usageOnlyWinners={}, "
                            + "usageOccurrences={}, fingerprint={}",
                    cropSnapshot.cropCount, cropSnapshot.pairCount,
                    cropSnapshot.simulatorCandidateCount,
                    cropSnapshot.records.size(),
                    cropSnapshot.canonicalGlobalMatchKeyCount,
                    cropSnapshot.craftWinnerCount,
                    cropSnapshot.usageWinnerIdentityCount,
                    cropSnapshot.usageOnlyWinnerCount,
                    cropSnapshot.usageOccurrenceCount,
                    cropSnapshot.fingerprint);
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Upstream IC2 crop cache is diagnostic-only because "
                            + "its identity-hash/tree-bin membership is nondeterministic; "
                            + "rawUnionResults={}, rawCraftResults={}, rawUsageResults={}, "
                            + "rawUsageOnlyResults={}, rawUsageOccurrences={}, "
                            + "rawConflatedCraftRepresentatives={}, rawFingerprint={}",
                    cropSnapshot.rawUnionResultIdentities,
                    cropSnapshot.rawCraftResultIdentities,
                    cropSnapshot.rawUsageResultIdentities,
                    cropSnapshot.rawUsageOnlyResultIdentities,
                    cropSnapshot.rawUsageOccurrences,
                    cropSnapshot.rawConflatedCraftRepresentatives,
                    cropSnapshot.rawDiagnosticFingerprint);
            auditCropReplayBasis(cropSnapshot, cropLoader);
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Verified compact IC2 crop derivation-basis audit "
                            + "before fingerprint promotion gate; matrixFingerprint={}, "
                            + "representativeGraphs={}, queryBucketClosure={}",
                    cropSnapshot.matrix.fingerprint,
                    cropSnapshot.auditRepresentatives.size(),
                    cropSnapshot.records.size());
        } else if (deepCropAudit) {
            auditCropReplayBasis(cropSnapshot, cropLoader);
        }
        boolean questPromoted = EXPECTED_QUEST_SEMANTICS_SHA256.equals(
                questSemantics.fingerprint);
        boolean cropPromoted = EXPECTED_CROP_REPLAY_SHA256.equals(cropSnapshot.fingerprint);
        if (!questPromoted || !cropPromoted) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "GTNH 2.8.4 adapter fingerprints are not both promoted; "
                            + "BetterQuesting observed=" + questSemantics.fingerprint
                            + ", expected=" + EXPECTED_QUEST_SEMANTICS_SHA256
                            + "; IC2 Crop observed=" + cropSnapshot.fingerprint
                            + ", expected=" + EXPECTED_CROP_REPLAY_SHA256);
        }
        String fingerprint = Naming.sha256("questUuids=" + questEntries.fingerprint
                + "\nquestSemantics=" + questSemantics.fingerprint
                + "\ncrops=" + cropSnapshot.fingerprint
                + "\nspaceRecipes=" + spaceSnapshot.fingerprint + "\n");
        return new RuntimeReadiness(true, fingerprint,
                "quests=" + questEntries.entries.size() + ", questPages="
                        + questSemantics.pages.size() + ", questInputSlots="
                        + questSemantics.inputSlots + ", questOutputSlots="
                        + questSemantics.outputSlots + ", crops=" + cropSnapshot.cropCount
                        + ", cropSimulatorCandidates="
                        + cropSnapshot.simulatorCandidateCount
                        + ", cropQueryBucketClosure=" + cropSnapshot.records.size()
                        + ", cropCraftWinners=" + cropSnapshot.craftWinnerCount
                        + ", cropUsageWinnerIdentities="
                        + cropSnapshot.usageWinnerIdentityCount
                        + ", cropUsageOnlyWinners=" + cropSnapshot.usageOnlyWinnerCount
                        + ", spaceHandlers=" + spaceSnapshot.pageKeys.size()
                        + ", spacePages=" + totalSpaceRecipePages(spaceSnapshot)
                        + ", spaceFingerprint=" + spaceSnapshot.fingerprint);
    }

    /** Pure policy seam locking one full capture versus compact derivation-basis audits. */
    static boolean requiresFreshCropSnapshot(
            boolean hasCompletedSnapshot, boolean deepCropAudit) {
        return !hasCompletedSnapshot;
    }

    private static ICraftingHandler loadBetterQuesting(TemplateRecipeHandler prototype)
            throws ExportFailure {
        try {
            TemplateRecipeHandler target = prototype.newInstance();
            Class<?> handlerClass = target.getClass();
            requireClassName(handlerClass, BETTER_QUESTING_HANDLER);
            Method setTextColors = exactDeclaredMethod(handlerClass, "setTextColors");
            setTextColors.invoke(target);

            QuestEntries corpus = questEntries(handlerClass.getClassLoader());
            requireQuestCorpus(corpus);
            QuestSemanticCorpus semantics = buildQuestSemanticCorpus(
                    corpus, handlerClass.getClassLoader());
            QuestSemanticCorpus readinessSemantics = completedQuestSemanticCorpus;
            if (readinessSemantics == null
                    || readinessSemantics.database != corpus.database
                    || !readinessSemantics.fingerprint.equals(semantics.fingerprint)
                    || !EXPECTED_QUEST_SEMANTICS_SHA256.equals(semantics.fingerprint)) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "BetterQuesting informational reference corpus changed or is unpromoted between "
                                + "readiness and category load; observed=" + semantics.fingerprint
                                + ", expected=" + EXPECTED_QUEST_SEMANTICS_SHA256);
            }
            Class<?> cachedClass = Class.forName(QUEST_CACHED_CLASS, false,
                    handlerClass.getClassLoader());
            Constructor<?> constructor = cachedClass.getDeclaredConstructor(
                    handlerClass, Map.Entry.class);
            constructor.setAccessible(true);
            Field inputsField = exactDeclaredField(cachedClass, "inputs", List.class);
            Field outputsField = exactDeclaredField(cachedClass, "outputs", List.class);
            Field questIdField = exactDeclaredField(cachedClass, "questID", UUID.class);

            int semanticPageIndex = 0;
            QuestStackSizeSnapshot stackSizeSnapshot = snapshotQuestBackingStackSizes(
                    corpus, handlerClass.getClassLoader());
            try {
                for (Map.Entry<UUID, Object> entry : corpus.entries) {
                    Object cached = constructor.newInstance(target, entry);
                    Object cachedQuestId = questIdField.get(cached);
                    if (!entry.getKey().equals(cachedQuestId)) {
                        throw new ExportFailure("RECIPE_SEMANTICS",
                                "BetterQuesting cached preview changed quest UUID; expected "
                                        + entry.getKey() + ", got " + cachedQuestId);
                    }
                    List<PositionedStack> inputs = positionedList(inputsField.get(cached),
                            "BetterQuesting cached inputs");
                    List<PositionedStack> outputs = positionedList(outputsField.get(cached),
                            "BetterQuesting cached outputs");
                    if (!outputs.isEmpty()) {
                        throw new ExportFailure("RECIPE_SEMANTICS",
                                "BetterQuesting upstream reward-list contract changed; outputs began nonempty");
                    }
                    for (Iterator<PositionedStack> iterator = inputs.iterator(); iterator.hasNext();) {
                        PositionedStack stack = iterator.next();
                        SlotKind kind = questSlotKind(stack);
                        if (kind == SlotKind.REWARD) {
                            iterator.remove();
                            outputs.add(stack);
                        }
                    }

                    RecipeSemanticOverride semanticPage = null;
                    if (semanticPageIndex < semantics.pages.size()) {
                        RecipeSemanticOverride candidate = semantics.pages.get(semanticPageIndex);
                        int relation = candidate.semanticId.compareTo(entry.getKey().toString());
                        if (relation < 0) {
                            throw new ExportFailure("RECIPE_SEMANTICS",
                                    "BetterQuesting semantic page order diverged before "
                                            + entry.getKey());
                        }
                        if (relation == 0) {
                            semanticPage = candidate;
                            semanticPageIndex++;
                        }
                    }
                    if (semanticPage == null) {
                        if (!inputs.isEmpty() || !outputs.isEmpty()) {
                            throw new ExportFailure("RECIPE_SEMANTICS",
                                    "BetterQuesting preview has item stacks for a non-item-reference quest "
                                            + entry.getKey());
                        }
                        continue;
                    }
                    requirePreviewPrefix(inputs, semanticPage.inputs, "task input", entry.getKey());
                    requirePreviewPrefix(outputs, semanticPage.outputs, "reward output", entry.getKey());
                    addCached(target, cached, cachedClass);
                }
            } finally {
                stackSizeSnapshot.restoreAndVerify();
            }
            requireCount("BetterQuesting attached semantic pages",
                    semantics.pages.size(), semanticPageIndex);
            requireCount("BetterQuesting exported pages", EXPECTED_QUEST_ITEM_PAGES,
                    target.numRecipes());
            synchronized (CompleteCategoryAdapters.class) {
                completedQuestDatabase = corpus.database;
                completedQuestSemanticCorpus = semantics;
                SEMANTIC_OVERRIDES_BY_HANDLER.put(target, semantics.pages);
            }
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] BetterQuesting informational adapter loaded all {} quest entries: "
                            + "previewPages={}, referencePages={}, inputSlots={}, outputSlots={}, "
                            + "choiceProviders={}, choiceEntries={}, flatRewardEntries={}, "
                            + "expandedAlternatives={}, previewLimitPerSide=16, semanticFingerprint={}",
                    corpus.entries.size(), target.numRecipes(), semantics.pages.size(),
                    semantics.inputSlots, semantics.outputSlots,
                    semantics.choiceProviders, semantics.choiceEntries,
                    semantics.flatRewardEntries,
                    semantics.expandedAlternatives, semantics.fingerprint);
            return target;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(unwrap(error));
            throw new ExportFailure("HANDLER_UNLOADED",
                    "BetterQuesting exact informational-category adapter failed", unwrap(error));
        }
    }

    private static ICraftingHandler loadCropBreeding(TemplateRecipeHandler prototype)
            throws ExportFailure {
        try {
            TemplateRecipeHandler target = prototype.newInstance();
            requireClassName(target.getClass(), CROP_HANDLER);
            ClassLoader loader = target.getClass().getClassLoader();
            requireCropSticks(loader);
            CropSnapshot snapshot = completedCropSnapshot;
            if (!exporterCropRecomputeStarted || exporterCropWorker == null
                    || exporterCropWorker.isAlive() || exporterCropFailure != null
                    || snapshot == null) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "IC2 Crop Plugin adapter was loaded before its exact recomputed cache was ready");
            }
            if (snapshot.breeder != cropBreeder(loader)) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "IC2 Crop Plugin Breeder.INSTANCE changed between readiness and category load");
            }
            Class<?> cachedClass = Class.forName(CROP_CACHED_CLASS, false, loader);
            Class<?> resultClass = Class.forName(CROP_RESULT_CLASS, false, loader);
            CropGraphSemanticContract previewContract = CropGraphSemanticContract.load(
                    loader, CropIdentityContract.load(loader));
            Constructor<?> constructor = cachedClass.getConstructor(target.getClass(), resultClass);
            Field cachedResult = exactDeclaredField(cachedClass, "result", resultClass);
            Method addPoints = exactPublicReturningMethod(
                    cachedClass, "addPoints", ItemStack.class);
            int addPointsModifiers = addPoints.getModifiers();
            if (addPoints.getDeclaringClass() != cachedClass
                    || Modifier.isStatic(addPointsModifiers)
                    || Modifier.isAbstract(addPointsModifiers)
                    || addPoints.isBridge() || addPoints.isSynthetic()) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        CROP_CACHED_CLASS + ".addPoints must remain exact public instance "
                                + "() -> ItemStack");
            }
            List<RecipeSemanticOverride> graphSemantics =
                    new ArrayList<RecipeSemanticOverride>(snapshot.records.size());
            for (CropResultRecord record : snapshot.records) {
                previewContract.validateCanonicalInputOrder(record.result);
                Object cached = constructor.newInstance(target, record.result);
                if (cachedResult.get(cached) != record.result) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "IC2 Crop Plugin cached preview did not retain the exact BreedResult");
                }
                addCached(target, cached, cachedClass);
                graphSemantics.add(record.graphSemantics);
            }
            if (target.numRecipes() != snapshot.records.size() || target.numRecipes() <= 0) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "IC2 Crop Plugin adapter populated " + target.numRecipes()
                                + " pages for " + snapshot.records.size() + " validated results");
            }
            MessageDigest presentationDigest = newSha256Digest(
                    "IC2 crop presentation telemetry");
            CropGraphSemanticContract.PresentationDigestStream presentationStream =
                    CropGraphSemanticContract.beginPresentationDigest(
                            presentationDigest);
            long renderedAlternatives = 0L;
            long renderedGraphCropAlternatives = 0L;
            int renderedAlternativesMinimum = Integer.MAX_VALUE;
            int renderedAlternativesMaximum = Integer.MIN_VALUE;
            int cropPreservingRenderedPages = 0;
            int lossyPermutationRenderedPages = 0;
            int directRenderedPages = 0;
            int wildcardItemListRenderedPages = 0;
            int wildcardEmptyFallbackRenderedPages = 0;
            int wildcardFireFallbackRenderedPages = 0;
            long renderedInputAlternatives = 0L;
            long renderedGraphCropInputAlternatives = 0L;
            int cropPreservingInputSlots = 0;
            int lossyInputSlots = 0;
            int directInputSlots = 0;
            int wildcardItemListInputSlots = 0;
            int wildcardEmptyFallbackInputSlots = 0;
            int wildcardFireFallbackInputSlots = 0;
            int renderedInputAlternativesMinimum = Integer.MAX_VALUE;
            int renderedInputAlternativesMaximum = Integer.MIN_VALUE;
            for (int index = 0; index < snapshot.records.size(); index++) {
                CropResultRecord record = snapshot.records.get(index);
                List<PositionedStack> previewInputs = target.getIngredientStacks(index);
                PositionedStack previewOutput = target.getResultStack(index);
                List<PositionedStack> previewOthers = target.getOtherStacks(index);
                if (previewOthers == null || !previewOthers.isEmpty()) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "IC2 BreedRecipe preview must expose no catalyst/other stacks");
                }
                CropGraphSemanticContract.PreviewAudit previewAudit =
                        previewContract.validateLoreOnlyPreview(
                                previewInputs, previewOutput, record.cleanGraph, index,
                                record.graphSemantics.semanticId, presentationStream);
                renderedAlternatives += previewAudit.renderedAlternativeCount;
                renderedGraphCropAlternatives +=
                        previewAudit.renderedGraphCropAlternativeCount;
                renderedAlternativesMinimum = Math.min(
                        renderedAlternativesMinimum,
                        previewAudit.renderedAlternativeCount);
                renderedAlternativesMaximum = Math.max(
                        renderedAlternativesMaximum,
                        previewAudit.renderedAlternativeCount);
                if (previewAudit.preservesGraphCropInEveryAlternative()) {
                    cropPreservingRenderedPages++;
                } else {
                    lossyPermutationRenderedPages++;
                }
                if (previewAudit.permutationSource
                        == CropGraphSemanticContract.PermutationSource.DIRECT_STACK) {
                    directRenderedPages++;
                } else if (previewAudit.permutationSource
                        == CropGraphSemanticContract.PermutationSource.WILDCARD_ITEM_LIST) {
                    wildcardItemListRenderedPages++;
                } else if (previewAudit.permutationSource
                        == CropGraphSemanticContract.PermutationSource.WILDCARD_EMPTY_FALLBACK) {
                    wildcardEmptyFallbackRenderedPages++;
                } else if (previewAudit.permutationSource
                        == CropGraphSemanticContract.PermutationSource.WILDCARD_FIRE_FALLBACK) {
                    wildcardFireFallbackRenderedPages++;
                } else {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "IC2 crop page index=" + index + " semanticId="
                                    + record.graphSemantics.semanticId
                                    + " returned an unknown pinned NEI permutation source "
                                    + previewAudit.permutationSource);
                }
                renderedInputAlternatives +=
                        previewAudit.renderedInputAlternativeCount;
                renderedGraphCropInputAlternatives +=
                        previewAudit.renderedGraphCropInputAlternativeCount;
                cropPreservingInputSlots += previewAudit.cropPreservingInputSlots;
                lossyInputSlots += previewAudit.lossyInputSlots;
                directInputSlots += previewAudit.directInputSlots;
                wildcardItemListInputSlots +=
                        previewAudit.wildcardItemListInputSlots;
                wildcardEmptyFallbackInputSlots +=
                        previewAudit.wildcardEmptyFallbackInputSlots;
                wildcardFireFallbackInputSlots +=
                        previewAudit.wildcardFireFallbackInputSlots;
                renderedInputAlternativesMinimum = Math.min(
                        renderedInputAlternativesMinimum,
                        previewAudit.minimumInputAlternativesPerSlot);
                renderedInputAlternativesMaximum = Math.max(
                        renderedInputAlternativesMaximum,
                        previewAudit.maximumInputAlternativesPerSlot);
            }
            String presentationFingerprint = hex(presentationStream.finish());
            CropPresentationDiscoveryGate.Observation presentationObservation =
                    new CropPresentationDiscoveryGate.Observation(
                            target.numRecipes(), renderedAlternatives,
                            renderedGraphCropAlternatives,
                            cropPreservingRenderedPages,
                            lossyPermutationRenderedPages,
                            directRenderedPages,
                            wildcardItemListRenderedPages,
                            wildcardEmptyFallbackRenderedPages,
                            wildcardFireFallbackRenderedPages,
                            renderedAlternativesMinimum,
                            renderedAlternativesMaximum,
                            renderedInputAlternatives,
                            renderedGraphCropInputAlternatives,
                            cropPreservingInputSlots,
                            lossyInputSlots,
                            directInputSlots,
                            wildcardItemListInputSlots,
                            wildcardEmptyFallbackInputSlots,
                            wildcardFireFallbackInputSlots,
                            renderedInputAlternativesMinimum,
                            renderedInputAlternativesMaximum,
                            presentationFingerprint);
            CropPresentationDiscoveryGate.requirePromoted(presentationObservation);
            synchronized (CompleteCategoryAdapters.class) {
                SEMANTIC_OVERRIDES_BY_HANDLER.put(target,
                        Collections.unmodifiableList(graphSemantics));
            }
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] IC2 Crop adapter loaded {} deterministic breeding pages; "
                            + "graphStacks=clean BreedResult copies, previewStacks=NEI lore-decorated, "
                            + "simulatorCandidates={}, canonicalGlobalMatchKeys={}, "
                            + "craftWinners={}, usageWinnerIdentities={}, usageOnlyWinners={}, "
                            + "usageOccurrences={}, fingerprint={}, semanticPreviewPages={}, "
                            + "renderedCropPreservingPages={}, renderedLossyPermutationPages={}, "
                            + "renderedAlternatives={}, renderedAlternativesPerPage={}..{}, "
                            + "renderedInputAlternatives={}, "
                            + "renderedInputAlternativesPerSlot={}..{}, "
                            + "renderedCropPreservingInputSlots={}, "
                            + "renderedLossyInputSlots={}, "
                            + "presentationCountVector={}, presentationDigestDomain={}, "
                            + "presentationFingerprint={}",
                    target.numRecipes(), snapshot.simulatorCandidateCount,
                    snapshot.canonicalGlobalMatchKeyCount,
                    snapshot.craftWinnerCount,
                    snapshot.usageWinnerIdentityCount,
                    snapshot.usageOnlyWinnerCount,
                    snapshot.usageOccurrenceCount,
                    snapshot.fingerprint, target.numRecipes(),
                    cropPreservingRenderedPages, lossyPermutationRenderedPages,
                    renderedAlternatives, renderedAlternativesMinimum,
                    renderedAlternativesMaximum,
                    renderedInputAlternatives,
                    renderedInputAlternativesMinimum,
                    renderedInputAlternativesMaximum,
                    cropPreservingInputSlots,
                    lossyInputSlots,
                    presentationObservation.countVector(),
                    CropGraphSemanticContract.PRESENTATION_CORPUS_DOMAIN,
                    presentationFingerprint);
            return target;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(unwrap(error));
            throw new ExportFailure("HANDLER_UNLOADED",
                    "IC2 Crop Plugin exact complete-category adapter failed", unwrap(error));
        }
    }

    private enum SlotKind {
        TASK,
        REWARD
    }

    private static SlotKind questSlotKind(PositionedStack stack) throws ExportFailure {
        if (stack == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", "BetterQuesting cached a null positioned stack");
        }
        boolean row = stack.rely >= 29 && stack.rely <= 83 && (stack.rely - 29) % 18 == 0;
        boolean task = stack.relx >= 3 && stack.relx <= 57 && (stack.relx - 3) % 18 == 0;
        boolean reward = stack.relx >= 93 && stack.relx <= 147 && (stack.relx - 93) % 18 == 0;
        if (!row || task == reward) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "BetterQuesting cached unexpected task/reward coordinates "
                            + stack.relx + "," + stack.rely);
        }
        return reward ? SlotKind.REWARD : SlotKind.TASK;
    }

    private static void requirePreviewPrefix(
            List<PositionedStack> preview, List<SemanticSlot> complete,
            String label, UUID questId) throws ExportFailure {
        List<List<SemanticAlternative>> flatSourceGroups =
                previewAlternativeGroups(complete);
        int expectedPreviewSize = Math.min(16, flatSourceGroups.size());
        if (preview.size() != expectedPreviewSize) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "BetterQuesting pinned NEI preview changed its 16-slot " + label
                            + " limit for " + questId + "; expected " + expectedPreviewSize
                            + " from " + flatSourceGroups.size()
                            + " flat source entries across " + complete.size()
                            + " semantic slots, got " + preview.size());
        }
        for (int slotIndex = 0; slotIndex < preview.size(); slotIndex++) {
            PositionedStack positioned = preview.get(slotIndex);
            if (positioned.items == null || positioned.items.length == 0) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "BetterQuesting preview has no " + label + " alternatives for "
                                + questId + " slot " + slotIndex);
            }
            List<String> actual = new ArrayList<String>(positioned.items.length);
            for (ItemStack stack : positioned.items) {
                if (stack == null || stack.getItem() == null) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "BetterQuesting preview has a null " + label + " alternative for "
                                    + questId + " slot " + slotIndex);
                }
                actual.add(canonicalStackIdentity(StackIdentity.of(stack), stack.stackSize));
            }
            Collections.sort(actual);
            List<SemanticAlternative> expected = flatSourceGroups.get(slotIndex);
            List<String> expectedIdentities =
                    new ArrayList<String>(expected.size());
            for (SemanticAlternative alternative : expected) {
                expectedIdentities.add(alternative.canonicalIdentity);
            }
            Collections.sort(expectedIdentities);
            if (actual.size() != expectedIdentities.size()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "BetterQuesting preview/reference " + label + " alternative counts differ for "
                                + questId + " slot " + slotIndex + "; preview=" + actual.size()
                                + ", full=" + expectedIdentities.size());
            }
            for (int alternativeIndex = 0; alternativeIndex < actual.size(); alternativeIndex++) {
                if (!actual.get(alternativeIndex).equals(
                        expectedIdentities.get(alternativeIndex))) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "BetterQuesting preview/reference " + label + " identities differ for "
                                    + questId + " slot " + slotIndex + " alternative "
                                    + alternativeIndex);
                }
            }
        }
    }

    static List<List<SemanticAlternative>> previewAlternativeGroups(
            List<SemanticSlot> semanticSlots) {
        List<List<SemanticAlternative>> groups =
                new ArrayList<List<SemanticAlternative>>();
        for (SemanticSlot slot : semanticSlots) {
            int offset = 0;
            for (Integer boxedSize : slot.previewGroupSizes) {
                int size = boxedSize.intValue();
                groups.add(Collections.unmodifiableList(
                        new ArrayList<SemanticAlternative>(
                                slot.alternatives.subList(offset, offset + size))));
                offset += size;
            }
        }
        return Collections.unmodifiableList(groups);
    }

    private static final class QuestEntries {
        final Object database;
        final List<Map.Entry<UUID, Object>> entries;
        final String fingerprint;

        QuestEntries(Object database, List<Map.Entry<UUID, Object>> entries,
                     String fingerprint) {
            this.database = database;
            this.entries = entries;
            this.fingerprint = fingerprint;
        }
    }

    private static QuestEntries questEntries(ClassLoader loader) throws ExportFailure {
        try {
            Class<?> databaseClass = Class.forName(QUEST_DATABASE_CLASS, false, loader);
            Field instanceField = exactPublicField(databaseClass, "INSTANCE", databaseClass);
            Object database = instanceField.get(null);
            if (database == null) {
                throw new ExportFailure("HANDLER_UNLOADED", "QuestDatabase.INSTANCE is null");
            }
            Method entrySet = databaseClass.getMethod("entrySet");
            Object rawEntries = entrySet.invoke(database);
            if (!(rawEntries instanceof Set)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "QuestDatabase.entrySet() did not return a Set");
            }
            List<Map.Entry<UUID, Object>> entries = new ArrayList<Map.Entry<UUID, Object>>();
            for (Object value : (Set<?>) rawEntries) {
                if (!(value instanceof Map.Entry)) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "QuestDatabase entrySet contained " + String.valueOf(value));
                }
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
                if (!(entry.getKey() instanceof UUID) || entry.getValue() == null) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "QuestDatabase contained a non-UUID/null quest entry");
                }
                @SuppressWarnings("unchecked")
                Map.Entry<UUID, Object> typed = (Map.Entry<UUID, Object>) entry;
                entries.add(typed);
            }
            Collections.sort(entries, new Comparator<Map.Entry<UUID, Object>>() {
                @Override
                public int compare(Map.Entry<UUID, Object> left,
                                   Map.Entry<UUID, Object> right) {
                    return left.getKey().toString().compareTo(right.getKey().toString());
                }
            });
            StringBuilder canonical = new StringBuilder(entries.size() * 37);
            UUID previous = null;
            for (Map.Entry<UUID, Object> entry : entries) {
                if (entry.getKey().equals(previous)) {
                    throw new ExportFailure("HANDLER_DUPLICATE",
                            "QuestDatabase repeated UUID " + previous);
                }
                previous = entry.getKey();
                canonical.append(entry.getKey().toString()).append('\n');
            }
            return new QuestEntries(database, entries, Naming.sha256(canonical.toString()));
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(unwrap(error));
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not inspect exact BetterQuesting quest database", unwrap(error));
        }
    }

    private static void requireQuestCorpus(QuestEntries corpus) throws ExportFailure {
        requireCount("BetterQuesting quest database", EXPECTED_QUEST_DATABASE_ENTRIES,
                corpus.entries.size());
        if (!EXPECTED_QUEST_UUID_SHA256.equals(corpus.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "BetterQuesting quest UUID corpus drifted; expected "
                            + EXPECTED_QUEST_UUID_SHA256 + ", got " + corpus.fingerprint);
        }
    }

    private static synchronized QuestSemanticCorpus cachedQuestSemanticCorpus(
            QuestEntries entries, ClassLoader loader) throws ExportFailure {
        QuestSemanticCorpus existing = completedQuestSemanticCorpus;
        if (existing != null) {
            if (completedQuestDatabase != entries.database
                    || existing.database != entries.database
                    || !existing.uuidFingerprint.equals(entries.fingerprint)) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "BetterQuesting database identity/UUID corpus changed after semantic snapshot");
            }
            return existing;
        }
        QuestSemanticCorpus observed = buildQuestSemanticCorpus(entries, loader);
        completedQuestDatabase = entries.database;
        completedQuestSemanticCorpus = observed;
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] BetterQuesting informational item-reference corpus ready: "
                        + "quests={}, pages={}, both={}, informationalInputOnly={}, outputOnly={}, "
                        + "nonItemExcluded={}, inputSlots={}, outputSlots={}, choiceProviders={}, "
                        + "choiceEntries={}, regularRewardEntries={}, flatRewardEntries={}, "
                        + "taskLogicOR={}, optionalRetrievalTasks={}, expandedAlternatives={}, "
                        + "over16Inputs={}, over16Outputs={}, maxInputs={}, maxOutputs={}, fingerprint={}",
                entries.entries.size(), observed.pages.size(), observed.both,
                observed.inputOnly, observed.outputOnly, observed.noItems,
                observed.inputSlots, observed.outputSlots, observed.choiceProviders,
                observed.choiceEntries, observed.regularRewardEntries,
                observed.flatRewardEntries, observed.taskLogicOr,
                observed.optionalRetrievalTasks, observed.expandedAlternatives,
                observed.over16InputPages, observed.over16OutputPages,
                observed.maxInputSlots, observed.maxOutputSlots, observed.fingerprint);
        return observed;
    }

    private static QuestSemanticCorpus buildQuestSemanticCorpus(
            QuestEntries entries, ClassLoader loader) throws ExportFailure {
        try {
            Class<?> questInterface = Class.forName(QUEST_INTERFACE_CLASS, false, loader);
            Class<?> databaseApi = Class.forName(QUEST_DATABASE_API_CLASS, false, loader);
            Class<?> databaseNbtApi = Class.forName(
                    QUEST_DATABASE_NBT_API_CLASS, false, loader);
            Class<?> databaseEntry = Class.forName(
                    QUEST_DATABASE_ENTRY_CLASS, false, loader);
            Class<?> taskItemInput = Class.forName(
                    QUEST_TASK_ITEM_INPUT_CLASS, false, loader);
            Class<?> rewardItemOutput = Class.forName(
                    QUEST_REWARD_ITEM_OUTPUT_CLASS, false, loader);
            Class<?> rewardChoice = Class.forName(
                    QUEST_REWARD_CHOICE_CLASS, false, loader);
            Class<?> optionalRetrieval = Class.forName(
                    QUEST_OPTIONAL_RETRIEVAL_CLASS, false, loader);
            Class<?> bigItemStack = Class.forName(
                    QUEST_BIG_ITEM_STACK_CLASS, false, loader);
            Class<?> oreIngredient = Class.forName(
                    QUEST_ORE_INGREDIENT_CLASS, false, loader);
            Class<?> nativeProperties = Class.forName(
                    QUEST_NATIVE_PROPERTIES_CLASS, false, loader);
            Class<?> propertyType = Class.forName(
                    QUEST_PROPERTY_TYPE_CLASS, false, loader);
            Class<?> propertyContainer = Class.forName(
                    QUEST_PROPERTY_CONTAINER_CLASS, false, loader);
            Class<?> logicEnum = Class.forName(
                    QUEST_LOGIC_ENUM_CLASS, false, loader);
            requireInterface(questInterface);
            requireInterface(databaseApi);
            requireInterface(databaseNbtApi);
            requireInterface(taskItemInput);
            requireInterface(rewardItemOutput);
            requireInterface(propertyType);
            requireInterface(propertyContainer);
            if (!propertyContainer.isAssignableFrom(questInterface)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "BetterQuesting IQuest no longer implements IPropertyContainer");
            }
            if (!taskItemInput.isAssignableFrom(optionalRetrieval)
                    || optionalRetrieval.isInterface()
                    || !java.lang.reflect.Modifier.isPublic(
                    optionalRetrieval.getModifiers())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        QUEST_OPTIONAL_RETRIEVAL_CLASS
                                + " must remain a public item-input task class");
            }
            if (!logicEnum.isEnum()
                    || !java.lang.reflect.Modifier.isPublic(logicEnum.getModifiers())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        QUEST_LOGIC_ENUM_CLASS + " must remain a public enum");
            }

            Method getTasks = exactDeclaredReturningMethod(
                    questInterface, "getTasks", databaseNbtApi);
            Method getRewards = exactDeclaredReturningMethod(
                    questInterface, "getRewards", databaseNbtApi);
            Method getEntries = exactDeclaredReturningMethod(
                    databaseApi, "getEntries", List.class);
            Method entryId = exactDeclaredReturningMethod(
                    databaseEntry, "getID", int.class);
            Method entryValue = exactDeclaredReturningMethod(
                    databaseEntry, "getValue", Object.class);
            Method getItemInputs = exactDeclaredReturningMethod(
                    taskItemInput, "getItemInputs", List.class);
            Method getItemOutputs = exactDeclaredReturningMethod(
                    rewardItemOutput, "getItemOutputs", List.class);
            BetterQuestingChoiceContract choiceContract =
                    BetterQuestingChoiceContract.bind(
                            rewardChoice, QUEST_REWARD_CHOICE_CLASS,
                            rewardItemOutput, bigItemStack, getItemOutputs);
            Field logicTaskField = exactPublicField(
                    nativeProperties, "LOGIC_TASK", propertyType);
            Object logicTaskProperty = logicTaskField.get(null);
            if (logicTaskProperty == null
                    || !propertyType.isInstance(logicTaskProperty)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "BetterQuesting NativeProps.LOGIC_TASK is null or has the wrong type");
            }
            Field logicOrField = exactPublicField(logicEnum, "OR", logicEnum);
            Object logicOrValue = logicOrField.get(null);
            if (logicOrValue == null || logicOrValue.getClass() != logicEnum) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "BetterQuesting EnumLogic.OR is null or has the wrong type");
            }
            Method getProperty = exactDeclaredReturningMethod(
                    propertyContainer, "getProperty", Object.class, propertyType);
            Field stackSize = exactPublicField(bigItemStack, "stackSize", int.class);
            Method hasOreDict = exactDeclaredReturningMethod(
                    bigItemStack, "hasOreDict", boolean.class);
            Method getOreDict = exactDeclaredReturningMethod(
                    bigItemStack, "getOreDict", String.class);
            Method getOreIngredient = exactDeclaredReturningMethod(
                    bigItemStack, "getOreIngredient", oreIngredient);
            Method getBaseStack = exactDeclaredReturningMethod(
                    bigItemStack, "getBaseStack", ItemStack.class);
            Method getMatchingStacks = exactDeclaredReturningMethod(
                    oreIngredient, "getMatchingStacks", ItemStack[].class);

            List<RecipeSemanticOverride> pages =
                    new ArrayList<RecipeSemanticOverride>(EXPECTED_QUEST_ITEM_PAGES);
            StringBuilder canonical = new StringBuilder(4 * 1024 * 1024);
            canonical.append("betterquesting-informational-item-reference-pages-v1\n");
            int both = 0;
            int inputOnly = 0;
            int outputOnly = 0;
            int noItems = 0;
            int inputSlots = 0;
            int outputSlots = 0;
            int choiceProviders = 0;
            int choiceEntries = 0;
            int regularRewardEntries = 0;
            int flatRewardEntries = 0;
            int taskLogicOr = 0;
            int optionalRetrievalTasks = 0;
            int expandedAlternatives = 0;
            int over16Inputs = 0;
            int over16Outputs = 0;
            int maxInputs = 0;
            int maxOutputs = 0;

            for (Map.Entry<UUID, Object> entry : entries.entries) {
                Object quest = entry.getValue();
                if (!questInterface.isInstance(quest)) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "QuestDatabase value does not implement exact IQuest: "
                                    + quest.getClass().getName());
                }
                List<Object> tasks = databaseValues(
                        getTasks.invoke(quest), databaseApi, databaseEntry,
                        getEntries, entryId, entryValue, "quest tasks", entry.getKey());
                List<Object> rewards = databaseValues(
                        getRewards.invoke(quest), databaseApi, databaseEntry,
                        getEntries, entryId, entryValue, "quest rewards", entry.getKey());
                Object taskLogic = getProperty.invoke(quest, logicTaskProperty);
                if (taskLogic == null || taskLogic.getClass() != logicEnum) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "BetterQuesting quest " + entry.getKey()
                                    + " has a null/unexpected task-logic enum");
                }
                if (taskLogic == logicOrValue) {
                    taskLogicOr++;
                }
                for (Object task : tasks) {
                    if (optionalRetrieval.isInstance(task)) {
                        if (task.getClass() != optionalRetrieval) {
                            throw new ExportFailure("HANDLER_AMBIGUOUS",
                                    "BetterQuesting optional retrieval task subclass drifted for "
                                            + entry.getKey() + ": "
                                            + task.getClass().getName());
                        }
                        optionalRetrievalTasks++;
                    }
                }
                List<SemanticSlot> inputs = semanticSlots(
                        tasks, taskItemInput, getItemInputs, bigItemStack,
                        stackSize, hasOreDict, getOreDict, getOreIngredient,
                        getBaseStack, oreIngredient, getMatchingStacks,
                        "task input", entry.getKey());
                RewardSemanticSlots rewardSemantics = semanticRewardSlots(
                        rewards, rewardItemOutput, getItemOutputs, bigItemStack,
                        choiceContract,
                        stackSize, hasOreDict, getOreDict, getOreIngredient,
                        getBaseStack, oreIngredient, getMatchingStacks,
                        "reward output", entry.getKey());
                List<SemanticSlot> outputs = rewardSemantics.slots;

                inputSlots += inputs.size();
                outputSlots += outputs.size();
                choiceProviders += rewardSemantics.choiceProviders;
                choiceEntries += rewardSemantics.choiceEntries;
                regularRewardEntries += rewardSemantics.regularEntries;
                flatRewardEntries += rewardSemantics.flatEntries();
                maxInputs = Math.max(maxInputs, inputs.size());
                maxOutputs = Math.max(maxOutputs, outputs.size());
                if (inputs.size() > 16) over16Inputs++;
                if (outputs.size() > 16) over16Outputs++;
                for (SemanticSlot slot : inputs) {
                    expandedAlternatives += slot.alternatives.size();
                }
                for (SemanticSlot slot : outputs) {
                    expandedAlternatives += slot.alternatives.size();
                }

                RecipeSemanticOverride page = new RecipeSemanticOverride(
                        entry.getKey(), inputs, outputs);
                canonical.append('B');
                appendCanonicalField(canonical, rewardSemantics.providerCanonical);
                appendQuestSemantics(canonical, page);
                if (inputs.isEmpty() && outputs.isEmpty()) {
                    noItems++;
                    continue;
                }
                if (inputs.isEmpty()) {
                    outputOnly++;
                } else if (outputs.isEmpty()) {
                    inputOnly++;
                } else {
                    both++;
                }
                pages.add(page);
            }

            requireCount("BetterQuesting item-reference pages",
                    EXPECTED_QUEST_ITEM_PAGES, pages.size());
            requireCount("BetterQuesting both-side item-reference pages", EXPECTED_QUEST_BOTH, both);
            requireCount("BetterQuesting input-only item-reference pages",
                    EXPECTED_QUEST_INPUT_ONLY, inputOnly);
            requireCount("BetterQuesting output-only item-reference pages",
                    EXPECTED_QUEST_OUTPUT_ONLY, outputOnly);
            requireCount("BetterQuesting non-item-reference quests",
                    EXPECTED_QUEST_NO_ITEM_SEMANTICS, noItems);
            requireCount("BetterQuesting item-reference input slots",
                    EXPECTED_QUEST_INPUT_SLOTS, inputSlots);
            requireCount("BetterQuesting item-reference output slots",
                    EXPECTED_QUEST_OUTPUT_SLOTS, outputSlots);
            requireCount("BetterQuesting RewardChoice providers",
                    EXPECTED_QUEST_CHOICE_PROVIDERS, choiceProviders);
            requireCount("BetterQuesting RewardChoice selectable entries",
                    EXPECTED_QUEST_CHOICE_ENTRIES, choiceEntries);
            requireCount("BetterQuesting regular item reward entries",
                    EXPECTED_QUEST_REGULAR_REWARD_ENTRIES, regularRewardEntries);
            requireCount("BetterQuesting flat NEI reward entries",
                    EXPECTED_QUEST_FLAT_REWARD_ENTRIES, flatRewardEntries);
            requireCount("BetterQuesting OR task-logic quests",
                    EXPECTED_QUEST_TASK_LOGIC_OR, taskLogicOr);
            requireCount("BetterQuesting optional retrieval task references",
                    EXPECTED_QUEST_OPTIONAL_RETRIEVAL_TASKS,
                    optionalRetrievalTasks);
            requireCount("BetterQuesting pages over 16 inputs",
                    EXPECTED_QUEST_OVER_16_INPUT_PAGES, over16Inputs);
            requireCount("BetterQuesting pages over 16 outputs",
                    EXPECTED_QUEST_OVER_16_OUTPUT_PAGES, over16Outputs);
            requireCount("BetterQuesting maximum input slots",
                    EXPECTED_QUEST_MAX_INPUT_SLOTS, maxInputs);
            requireCount("BetterQuesting maximum output slots",
                    EXPECTED_QUEST_MAX_OUTPUT_SLOTS, maxOutputs);
            if (expandedAlternatives < inputSlots + outputSlots) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "BetterQuesting alternative expansion lost one or more semantic slots");
            }
            return new QuestSemanticCorpus(
                    entries.database, entries.fingerprint, pages,
                    Naming.sha256(canonical.toString()), both, inputOnly, outputOnly,
                    noItems, inputSlots, outputSlots,
                    choiceProviders, choiceEntries, regularRewardEntries,
                    flatRewardEntries, taskLogicOr, optionalRetrievalTasks,
                    expandedAlternatives,
                    over16Inputs, over16Outputs, maxInputs, maxOutputs);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(unwrap(error));
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not construct complete BetterQuesting informational item references",
                    unwrap(error));
        }
    }

    private static List<Object> databaseValues(
            Object database, Class<?> databaseApi, Class<?> databaseEntry,
            Method getEntries, Method entryId, Method entryValue,
            String label, UUID questId) throws Exception {
        if (database == null || !databaseApi.isInstance(database)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "BetterQuesting " + label + " database has unexpected type for " + questId);
        }
        Object raw = getEntries.invoke(database);
        if (!(raw instanceof List)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "BetterQuesting " + label + " getEntries() is not a List for " + questId);
        }
        List<Object> values = new ArrayList<Object>(((List<?>) raw).size());
        int previousId = -1;
        for (Object rawEntry : (List<?>) raw) {
            if (rawEntry == null || rawEntry.getClass() != databaseEntry) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "BetterQuesting " + label + " contains a non-DBEntry for " + questId);
            }
            int id = ((Number) entryId.invoke(rawEntry)).intValue();
            if (id < 0 || id <= previousId) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "BetterQuesting " + label
                                + " DBEntry IDs are not strictly increasing for " + questId);
            }
            previousId = id;
            Object value = entryValue.invoke(rawEntry);
            if (value == null) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "BetterQuesting " + label + " contains a null value for " + questId);
            }
            values.add(value);
        }
        return values;
    }

    private static List<SemanticSlot> semanticSlots(
            List<Object> owners, Class<?> semanticInterface, Method getBigStacks,
            Class<?> bigItemStack, Field stackSize, Method hasOreDict,
            Method getOreDict, Method getOreIngredient, Method getBaseStack,
            Class<?> oreIngredient, Method getMatchingStacks,
            String label, UUID questId) throws Exception {
        List<SemanticSlot> slots = new ArrayList<SemanticSlot>();
        for (Object owner : owners) {
            if (!semanticInterface.isInstance(owner)) {
                continue;
            }
            Object rawStacks = getBigStacks.invoke(owner);
            if (!(rawStacks instanceof List)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "BetterQuesting " + label + " provider returned a non-list for " + questId);
            }
            int providerIndex = 0;
            for (Object bigStack : (List<?>) rawStacks) {
                if (bigStack == null || bigStack.getClass() != bigItemStack) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "BetterQuesting " + label + " #" + providerIndex
                                    + " is not the exact BigItemStack class for " + questId);
                }
                slots.add(semanticSlot(
                        bigStack, stackSize, hasOreDict, getOreDict,
                        getOreIngredient, getBaseStack, oreIngredient,
                        getMatchingStacks, label, providerIndex, questId));
                providerIndex++;
            }
        }
        return slots;
    }

    private static RewardSemanticSlots semanticRewardSlots(
            List<Object> owners, Class<?> rewardItemOutput, Method getItemOutputs,
            Class<?> bigItemStack, BetterQuestingChoiceContract choiceContract,
            Field stackSize, Method hasOreDict, Method getOreDict,
            Method getOreIngredient, Method getBaseStack,
            Class<?> oreIngredient, Method getMatchingStacks,
            String label, UUID questId) throws Exception {
        List<SemanticSlot> slots = new ArrayList<SemanticSlot>();
        StringBuilder providerCanonical = new StringBuilder();
        int choiceProviders = 0;
        int choiceEntries = 0;
        int regularEntries = 0;
        for (int ownerIndex = 0; ownerIndex < owners.size(); ownerIndex++) {
            Object owner = owners.get(ownerIndex);
            if (choiceContract.isChoice(owner)) {
                List<?> choices = choiceContract.choices(owner);
                List<SemanticSlot> choiceEntrySlots =
                        new ArrayList<SemanticSlot>(choices.size());
                for (int choiceIndex = 0; choiceIndex < choices.size(); choiceIndex++) {
                    choiceEntrySlots.add(semanticSlot(
                            choices.get(choiceIndex), stackSize, hasOreDict, getOreDict,
                            getOreIngredient, getBaseStack, oreIngredient,
                            getMatchingStacks, label + " choice", choiceIndex, questId));
                }
                slots.add(groupChoiceSlots(choiceEntrySlots));
                providerCanonical.append('C').append(ownerIndex).append(':')
                        .append(choices.size()).append(';');
                choiceProviders++;
                choiceEntries += choices.size();
                continue;
            }
            if (!rewardItemOutput.isInstance(owner)) {
                continue;
            }
            Object rawStacks = getItemOutputs.invoke(owner);
            if (!(rawStacks instanceof List)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "BetterQuesting " + label + " provider returned a non-list for "
                                + questId);
            }
            List<?> regularStacks = (List<?>) rawStacks;
            providerCanonical.append('R').append(ownerIndex).append(':')
                    .append(regularStacks.size()).append(';');
            for (int providerIndex = 0; providerIndex < regularStacks.size(); providerIndex++) {
                Object bigStack = regularStacks.get(providerIndex);
                if (bigStack == null || bigStack.getClass() != bigItemStack) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "BetterQuesting " + label + " #" + providerIndex
                                    + " is not the exact BigItemStack class for " + questId);
                }
                slots.add(semanticSlot(
                        bigStack, stackSize, hasOreDict, getOreDict,
                        getOreIngredient, getBaseStack, oreIngredient,
                        getMatchingStacks, label, providerIndex, questId));
                regularEntries++;
            }
        }
        return new RewardSemanticSlots(slots, choiceProviders, choiceEntries,
                regularEntries, providerCanonical.toString());
    }

    static SemanticSlot groupChoiceSlots(List<SemanticSlot> choiceEntries)
            throws ExportFailure {
        if (choiceEntries == null || choiceEntries.isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "BetterQuesting RewardChoice contains no semantic choice entries");
        }
        List<SemanticAlternative> alternatives = new ArrayList<SemanticAlternative>();
        List<Integer> groupSizes = new ArrayList<Integer>(choiceEntries.size());
        for (int choiceIndex = 0; choiceIndex < choiceEntries.size(); choiceIndex++) {
            SemanticSlot choice = choiceEntries.get(choiceIndex);
            if (choice == null || choice.previewGroupSizes.size() != 1
                    || choice.previewGroupSizes.get(0).intValue()
                    != choice.alternatives.size()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "BetterQuesting RewardChoice entry " + choiceIndex
                                + " is not one exact source BigItemStack group");
            }
            alternatives.addAll(choice.alternatives);
            groupSizes.add(Integer.valueOf(choice.alternatives.size()));
        }
        return new SemanticSlot(alternatives, groupSizes);
    }

    private static QuestStackSizeSnapshot snapshotQuestBackingStackSizes(
            QuestEntries entries, ClassLoader loader) throws ExportFailure {
        try {
            Class<?> questInterface = Class.forName(QUEST_INTERFACE_CLASS, false, loader);
            Class<?> databaseApi = Class.forName(QUEST_DATABASE_API_CLASS, false, loader);
            Class<?> databaseNbtApi = Class.forName(
                    QUEST_DATABASE_NBT_API_CLASS, false, loader);
            Class<?> databaseEntry = Class.forName(
                    QUEST_DATABASE_ENTRY_CLASS, false, loader);
            Class<?> taskItemInput = Class.forName(
                    QUEST_TASK_ITEM_INPUT_CLASS, false, loader);
            Class<?> rewardItemOutput = Class.forName(
                    QUEST_REWARD_ITEM_OUTPUT_CLASS, false, loader);
            Class<?> rewardChoice = Class.forName(
                    QUEST_REWARD_CHOICE_CLASS, false, loader);
            Class<?> bigItemStack = Class.forName(
                    QUEST_BIG_ITEM_STACK_CLASS, false, loader);
            Class<?> oreIngredient = Class.forName(
                    QUEST_ORE_INGREDIENT_CLASS, false, loader);
            Method getTasks = exactDeclaredReturningMethod(
                    questInterface, "getTasks", databaseNbtApi);
            Method getRewards = exactDeclaredReturningMethod(
                    questInterface, "getRewards", databaseNbtApi);
            Method getEntries = exactDeclaredReturningMethod(
                    databaseApi, "getEntries", List.class);
            Method entryId = exactDeclaredReturningMethod(
                    databaseEntry, "getID", int.class);
            Method entryValue = exactDeclaredReturningMethod(
                    databaseEntry, "getValue", Object.class);
            Method getItemInputs = exactDeclaredReturningMethod(
                    taskItemInput, "getItemInputs", List.class);
            Method getItemOutputs = exactDeclaredReturningMethod(
                    rewardItemOutput, "getItemOutputs", List.class);
            BetterQuestingChoiceContract choiceContract =
                    BetterQuestingChoiceContract.bind(
                            rewardChoice, QUEST_REWARD_CHOICE_CLASS,
                            rewardItemOutput, bigItemStack, getItemOutputs);
            Method hasOreDict = exactDeclaredReturningMethod(
                    bigItemStack, "hasOreDict", boolean.class);
            Method getOreIngredient = exactDeclaredReturningMethod(
                    bigItemStack, "getOreIngredient", oreIngredient);
            Method getBaseStack = exactDeclaredReturningMethod(
                    bigItemStack, "getBaseStack", ItemStack.class);
            Method getMatchingStacks = exactDeclaredReturningMethod(
                    oreIngredient, "getMatchingStacks", ItemStack[].class);

            IdentityHashMap<ItemStack, Integer> originals =
                    new IdentityHashMap<ItemStack, Integer>();
            for (Map.Entry<UUID, Object> entry : entries.entries) {
                Object quest = entry.getValue();
                List<Object> tasks = databaseValues(
                        getTasks.invoke(quest), databaseApi, databaseEntry,
                        getEntries, entryId, entryValue, "quest tasks", entry.getKey());
                List<Object> rewards = databaseValues(
                        getRewards.invoke(quest), databaseApi, databaseEntry,
                        getEntries, entryId, entryValue, "quest rewards", entry.getKey());
                snapshotOwnerStackSizes(
                        tasks, taskItemInput, getItemInputs, null, bigItemStack,
                        hasOreDict, getOreIngredient, getBaseStack,
                        oreIngredient, getMatchingStacks, originals, entry.getKey());
                snapshotOwnerStackSizes(
                        rewards, rewardItemOutput, getItemOutputs, choiceContract, bigItemStack,
                        hasOreDict, getOreIngredient, getBaseStack,
                        oreIngredient, getMatchingStacks, originals, entry.getKey());
            }
            return new QuestStackSizeSnapshot(originals);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(unwrap(error));
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not snapshot BetterQuesting backing ItemStack quantities",
                    unwrap(error));
        }
    }

    private static void snapshotOwnerStackSizes(
            List<Object> owners, Class<?> semanticInterface, Method getBigStacks,
            BetterQuestingChoiceContract choiceContract,
            Class<?> bigItemStack, Method hasOreDict, Method getOreIngredient,
            Method getBaseStack, Class<?> oreIngredient, Method getMatchingStacks,
            IdentityHashMap<ItemStack, Integer> originals, UUID questId) throws Exception {
        for (Object owner : owners) {
            if (!semanticInterface.isInstance(owner)) {
                continue;
            }
            Object rawStacks = choiceContract != null && choiceContract.isChoice(owner)
                    ? choiceContract.choices(owner) : getBigStacks.invoke(owner);
            if (!(rawStacks instanceof List)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "BetterQuesting item provider returned a non-list while guarding "
                                + questId);
            }
            for (Object bigStack : (List<?>) rawStacks) {
                if (bigStack == null || bigStack.getClass() != bigItemStack) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "BetterQuesting item provider changed BigItemStack type while guarding "
                                    + questId);
                }
                Object base = getBaseStack.invoke(bigStack);
                if (!(base instanceof ItemStack) || ((ItemStack) base).getItem() == null) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "BetterQuesting BigItemStack has no backing item while guarding "
                                    + questId);
                }
                rememberStackSize(originals, (ItemStack) base);
                if (((Boolean) hasOreDict.invoke(bigStack)).booleanValue()) {
                    Object ingredient = getOreIngredient.invoke(bigStack);
                    if (ingredient == null || ingredient.getClass() != oreIngredient) {
                        throw new ExportFailure("HANDLER_AMBIGUOUS",
                                "BetterQuesting OreIngredient type drifted while guarding "
                                        + questId);
                    }
                    Object matching = getMatchingStacks.invoke(ingredient);
                    if (!(matching instanceof ItemStack[])) {
                        throw new ExportFailure("HANDLER_AMBIGUOUS",
                                "BetterQuesting ore alternatives changed type while guarding "
                                        + questId);
                    }
                    for (ItemStack stack : (ItemStack[]) matching) {
                        if (stack == null || stack.getItem() == null) {
                            throw new ExportFailure("RECIPE_SEMANTICS",
                                    "BetterQuesting ore alternatives contain a null item while guarding "
                                            + questId);
                        }
                        rememberStackSize(originals, stack);
                    }
                }
            }
        }
    }

    private static void rememberStackSize(
            IdentityHashMap<ItemStack, Integer> originals, ItemStack stack) {
        if (!originals.containsKey(stack)) {
            originals.put(stack, Integer.valueOf(stack.stackSize));
        }
    }

    private static SemanticSlot semanticSlot(
            Object bigStack, Field stackSize, Method hasOreDict,
            Method getOreDict, Method getOreIngredient, Method getBaseStack,
            Class<?> oreIngredient, Method getMatchingStacks,
            String label, int providerIndex, UUID questId) throws Exception {
        int amount = stackSize.getInt(bigStack);
        if (amount < 0) {
            throw new ExportFailure("QUANTITY_INVALID",
                    "BetterQuesting " + questId + " " + label + " #"
                            + providerIndex + " has amount " + amount);
        }
        List<ItemStack> rawAlternatives = new ArrayList<ItemStack>();
        boolean ore = ((Boolean) hasOreDict.invoke(bigStack)).booleanValue();
        if (ore) {
            String oreName = (String) getOreDict.invoke(bigStack);
            if (oreName == null || oreName.trim().isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "BetterQuesting " + questId + " " + label + " #"
                                + providerIndex + " reports an unnamed ore-dictionary contract");
            }
            Object ingredient = getOreIngredient.invoke(bigStack);
            if (ingredient == null || ingredient.getClass() != oreIngredient) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "BetterQuesting " + questId + " " + label + " #"
                                + providerIndex + " has an unexpected OreIngredient type");
            }
            Object matching = getMatchingStacks.invoke(ingredient);
            if (!(matching instanceof ItemStack[]) || Array.getLength(matching) == 0) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "BetterQuesting " + questId + " " + label + " #"
                                + providerIndex + " ore dictionary " + oreName
                                + " has no registered alternatives");
            }
            for (ItemStack match : (ItemStack[]) matching) {
                rawAlternatives.add(copyWithAmount(
                        match, amount, questId, label, providerIndex));
            }
        } else {
            Object base = getBaseStack.invoke(bigStack);
            if (!(base instanceof ItemStack)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "BetterQuesting " + questId + " " + label + " #"
                                + providerIndex + " has no base ItemStack");
            }
            rawAlternatives.add(copyWithAmount(
                    (ItemStack) base, amount, questId, label, providerIndex));
        }

        // This is the same pinned NEI permutation operation used by the visual
        // CachedQuestRecipe, but it is applied to copies. It expands wildcard
        // metadata without mutating BetterQuesting or the global ore lists.
        PositionedStack expanded = new PositionedStack(rawAlternatives, 0, 0);
        if (expanded.items == null || expanded.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "BetterQuesting " + questId + " " + label + " #"
                            + providerIndex + " produced no NEI alternatives");
        }
        List<SemanticAlternative> alternatives =
                new ArrayList<SemanticAlternative>(expanded.items.length);
        for (int index = 0; index < expanded.items.length; index++) {
            ItemStack stack = copyWithAmount(
                    expanded.items[index], amount, questId, label, providerIndex);
            StackIdentity identity = StackIdentity.of(stack);
            alternatives.add(new SemanticAlternative(
                    stack, amount, canonicalStackIdentity(identity, amount)));
        }
        Collections.sort(alternatives, new Comparator<SemanticAlternative>() {
            @Override
            public int compare(SemanticAlternative left, SemanticAlternative right) {
                return left.canonicalIdentity.compareTo(right.canonicalIdentity);
            }
        });
        return new SemanticSlot(alternatives);
    }

    private static ItemStack copyWithAmount(ItemStack original, int amount,
                                            UUID questId, String label,
                                            int providerIndex) throws ExportFailure {
        if (original == null || original.getItem() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "BetterQuesting " + questId + " " + label + " #"
                            + providerIndex + " contains a null/empty ItemStack alternative");
        }
        ItemStack copy = original.copy();
        copy.stackSize = amount;
        return copy;
    }

    static String canonicalStackIdentity(StackIdentity identity, int amount) {
        StringBuilder canonical = new StringBuilder(160
                + (identity.canonicalNbt == null ? 0 : identity.canonicalNbt.length()));
        appendCanonicalField(canonical, identity.key);
        appendCanonicalField(canonical, identity.canonicalNbt);
        canonical.append(amount).append(';');
        return canonical.toString();
    }

    private static void appendQuestSemantics(StringBuilder canonical,
                                             RecipeSemanticOverride page) {
        canonical.append('Q');
        appendCanonicalField(canonical, page.semanticId);
        appendSemanticSlots(canonical, 'I', page.inputs);
        appendSemanticSlots(canonical, 'O', page.outputs);
        canonical.append('\n');
    }

    private static void appendSemanticSlots(StringBuilder canonical, char role,
                                            List<SemanticSlot> slots) {
        canonical.append(role).append(slots.size()).append(';');
        for (SemanticSlot slot : slots) {
            canonical.append('S').append(slot.alternatives.size()).append(';')
                    .append('G').append(slot.previewGroupSizes.size()).append(';');
            int offset = 0;
            for (Integer boxedGroupSize : slot.previewGroupSizes) {
                int groupSize = boxedGroupSize.intValue();
                canonical.append('P').append(groupSize).append(';');
                for (int index = 0; index < groupSize; index++) {
                    appendCanonicalField(canonical,
                            slot.alternatives.get(offset + index).canonicalIdentity);
                }
                offset += groupSize;
            }
        }
    }

    static String canonicalSemanticSlotsForFingerprint(
            char role, List<SemanticSlot> slots) {
        StringBuilder canonical = new StringBuilder();
        appendSemanticSlots(canonical, role, slots);
        return canonical.toString();
    }

    private static void appendCanonicalField(StringBuilder canonical, String value) {
        if (value == null) {
            canonical.append("-1:");
        } else {
            canonical.append(value.length()).append(':').append(value);
        }
    }

    private static void requireCropSticks(ClassLoader loader) throws ExportFailure {
        try {
            Class<?> modClass = Class.forName(CROP_MOD_CLASS, false, loader);
            Field cropSticks = exactPublicField(modClass, "CROP_STICKS", int.class);
            int value = cropSticks.getInt(null);
            if (value != 2) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "IC2 Crop Plugin CROP_STICKS must equal 2; got " + value);
            }
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(unwrap(error));
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not verify IC2 Crop Plugin CROP_STICKS", unwrap(error));
        }
    }

    private static Object cropBreeder(ClassLoader loader) throws ExportFailure {
        try {
            Class<?> breederClass = Class.forName(CROP_BREEDER_CLASS, false, loader);
            Field instance = exactPublicField(breederClass, "INSTANCE", breederClass);
            Object breeder = instance.get(null);
            if (breeder == null) {
                throw new ExportFailure("HANDLER_UNLOADED", "Breeder.INSTANCE is null");
            }
            return breeder;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(unwrap(error));
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not resolve IC2 Crop Plugin Breeder.INSTANCE", unwrap(error));
        }
    }

    private static void startExporterCropRecompute(final Object breeder) throws ExportFailure {
        try {
            final Method run = breeder.getClass().getMethod("run");
            exporterCropBreeder = breeder;
            exporterCropFailure = null;
            completedCropSnapshot = null;
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        run.invoke(breeder);
                    } catch (Throwable error) {
                        exporterCropFailure = unwrap(error);
                    }
                }
            }, EXPORTER_CROP_WORKER_NAME);
            // Daemon status does not weaken the Thread-termination happens-before edge;
            // it only prevents a wedged third-party calculation from trapping the JVM
            // after the readiness timeout has already failed the export.
            worker.setDaemon(true);
            exporterCropWorker = worker;
            exporterCropRecomputeStarted = true;
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Starting exporter-owned IC2 crop-cache recomputation; "
                            + "thread termination supplies the required Java memory-model happens-before edge");
            worker.start();
        } catch (Throwable error) {
            exporterCropWorker = null;
            exporterCropBreeder = null;
            exporterCropRecomputeStarted = false;
            completedCropSnapshot = null;
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not start exporter-owned crop-cache recomputation", unwrap(error));
        }
    }

    private static CropSnapshot snapshotCropReplay(ClassLoader loader) throws ExportFailure {
        try {
            Object breeder = cropBreeder(loader);
            CropIdentityContract cropIdentities = CropIdentityContract.load(loader);
            CropGraphSemanticContract graphContract =
                    CropGraphSemanticContract.load(loader, cropIdentities);
            Field craftField = exactDeclaredField(breeder.getClass(), "craft", Map.class);
            Field usageField = exactDeclaredField(breeder.getClass(), "usage", Map.class);
            Object craftValue = craftField.get(breeder);
            Object usageValue = usageField.get(breeder);
            if (!(craftValue instanceof Map) || !(usageValue instanceof Map)) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "IC2 Crop Plugin craft/usage cache is not fully published");
            }
            Map<?, ?> craft = (Map<?, ?>) craftValue;
            Map<?, ?> usage = (Map<?, ?>) usageValue;
            if (craft.isEmpty() || usage.isEmpty()) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "IC2 Crop Plugin published empty craft/usage maps");
            }

            final Map<String, Object> cropsById = new HashMap<String, Object>();
            final IdentityHashMap<Object, String> cropIdsByIdentity =
                    new IdentityHashMap<Object, String>();
            List<Object> canonicalCropUniverse = exactCropUniverse(
                    loader, cropIdentities, cropsById, cropIdsByIdentity);
            final Class<?> resultClass = Class.forName(CROP_RESULT_CLASS, false, loader);
            RawCropCacheDiagnostics rawDiagnostics = auditRawCropCache(
                    craft, usage, resultClass, cropIdsByIdentity,
                    cropsById, graphContract);
            if (cropsById.size() != canonicalCropUniverse.size()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "diagnostic IC2 cache introduced a CropCard outside exact ALL_CROPS; "
                                + "allCrops=" + canonicalCropUniverse.size()
                                + ", canonical=" + cropsById.size());
            }

            final Class<?> cropCardClass = cropIdentities.cropCardClass();
            Method calculateRatio = exactCropRatioMethod(loader, cropCardClass);
            DeterministicCropMatrixContract.CanonicalId<Object> canonicalIds =
                    exactCropCanonicalIds(cropIdsByIdentity);
            DeterministicCropMatrixContract.Ratio<Object> ratios =
                    exactCropRatios(calculateRatio);
            DeterministicCropMatrixContract.Snapshot matrix =
                    DeterministicCropMatrixContract.audit(
                            canonicalCropUniverse, canonicalIds, ratios);
            DeterministicCropReplayContract.Snapshot<Object> replay =
                    DeterministicCropReplayContract.replay(
                            canonicalCropUniverse, canonicalIds, ratios);
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Deterministic IC2 crop derivation matrix measured; "
                            + "crops={}, pairs={}, candidates={}, candidatesPerPair={}..{}, "
                            + "totalPoints={}..{}, matrixFingerprint={}",
                    matrix.cropCount, matrix.pairCount, matrix.outcomeCount,
                    matrix.minimumOutcomesPerPair, matrix.maximumOutcomesPerPair,
                    matrix.minimumTotalPoints, matrix.maximumTotalPoints,
                    matrix.fingerprint);
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Deterministic two-parent IC2 crop replay measured; "
                            + "crops={}, pairs={}, simulatorCandidates={}, "
                            + "candidatesPerPair={}..{}, totalPoints={}..{}, "
                            + "canonicalGlobalMatchKeys={}, queryBucketClosure={}, "
                            + "craftWinners={}, usageWinnerIdentities={}, usageOccurrences={}, "
                            + "usageOnlyWinners={}, replayFingerprint={}",
                    replay.cropCount,
                    replay.pairCount,
                    replay.simulatorCandidateCount,
                    replay.minimumCandidatesPerPair,
                    replay.maximumCandidatesPerPair,
                    replay.minimumTotalPoints,
                    replay.maximumTotalPoints,
                    replay.canonicalGlobalMatchKeyCount,
                    replay.bucketLocalClosureCount,
                    replay.craftWinnerCount,
                    replay.usageWinnerIdentityCount,
                    replay.usageOccurrenceCount,
                    replay.usageOnlyWinnerCount,
                    replay.fingerprint);

            final Map<String, String> cleanGraphStacksByCropId =
                    new HashMap<String, String>();
            List<CropResultRecord> records = constructDeterministicCropRecords(
                    replay, resultClass, cropCardClass, graphContract,
                    cropsById, cleanGraphStacksByCropId);
            if (cropsById.size() != canonicalCropUniverse.size()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "deterministic IC2 BreedResult graph introduced a CropCard absent from "
                                + "exact ALL_CROPS; allCrops=" + canonicalCropUniverse.size()
                                + ", canonical=" + cropsById.size());
            }
            if (records.size() != replay.bucketLocalClosureCount || records.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "deterministic IC2 crop construction produced " + records.size()
                                + " records for replay closure "
                                + replay.bucketLocalClosureCount);
            }
            if (cleanGraphStacksByCropId.size() != EXPECTED_CROP_CARD_COUNT) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "deterministic IC2 crop closure must cover one clean display stack for "
                                + "every CropCard; stacks=" + cleanGraphStacksByCropId.size()
                                + "/" + EXPECTED_CROP_CARD_COUNT);
            }
            List<CropResultRecord> auditRepresentatives =
                    selectCropAuditRepresentatives(records, cleanGraphStacksByCropId.keySet());

            List<String> cropIds = new ArrayList<String>(cropsById.keySet());
            Collections.sort(cropIds);
            StringBuilder canonical = new StringBuilder(
                    cropIds.size() * 48 + records.size() * 96);
            canonical.append("ic2-crop-deterministic-query-bucket-closure-v1\n");
            canonical.append("replayFingerprint\t");
            appendCanonicalField(canonical, replay.fingerprint);
            canonical.append('\n');
            canonical.append("cropCards\t").append(cropIds.size()).append('\n');
            for (String cropId : cropIds) {
                canonical.append("crop\t");
                appendCanonicalField(canonical, cropId);
                canonical.append('\n');
            }
            List<String> graphCropIds =
                    new ArrayList<String>(cleanGraphStacksByCropId.keySet());
            Collections.sort(graphCropIds);
            canonical.append("cleanGraphCropStacks\t")
                    .append(graphCropIds.size()).append('\n');
            for (String cropId : graphCropIds) {
                canonical.append("cleanGraphCropStack\t");
                appendCanonicalField(canonical, cropId);
                appendCanonicalField(canonical, cleanGraphStacksByCropId.get(cropId));
                canonical.append('\n');
            }
            canonical.append("queryBucketClosureBreedResults\t")
                    .append(records.size()).append('\n');
            for (CropResultRecord record : records) {
                canonical.append(record.canonicalLine);
            }
            return new CropSnapshot(
                    breeder,
                    craft,
                    usage,
                    Collections.unmodifiableList(
                            new ArrayList<Object>(canonicalCropUniverse)),
                    Collections.unmodifiableList(
                            new ArrayList<CropResultRecord>(records)),
                    auditRepresentatives,
                    Collections.unmodifiableMap(
                            new HashMap<String, String>(cleanGraphStacksByCropId)),
                    matrix,
                    cropsById.size(),
                    replay.pairCount,
                    replay.simulatorCandidateCount,
                    replay.canonicalGlobalMatchKeyCount,
                    replay.craftWinnerCount,
                    replay.usageWinnerIdentityCount,
                    replay.usageOnlyWinnerCount,
                    replay.usageOccurrenceCount,
                    rawDiagnostics.unionResultIdentities,
                    rawDiagnostics.craftResultIdentities,
                    rawDiagnostics.usageResultIdentities,
                    rawDiagnostics.usageOnlyResultIdentities,
                    rawDiagnostics.usageOccurrences,
                    rawDiagnostics.conflatedCraftRepresentatives,
                    rawDiagnostics.fingerprint,
                    Naming.sha256(canonical.toString()));
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(unwrap(error));
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not validate the deterministic IC2 crop replay and upstream "
                            + "diagnostic cache", unwrap(error));
        }
    }

    /**
     * Revalidates the complete deterministic corpus from its compact derivation basis.
     *
     * <p>The exporter JAR and pinned dependency bytecode are immutable for the supervised JVM.
     * Therefore the repaired query-bucket closure is a pure function of the exact ALL_CROPS
     * object/ID vector, the complete public ratio matrix, and one clean display stack per
     * CropCard. Rechecking those inputs plus the diagnostic raw-cache membership proves the
     * original 290,789-record snapshot without allocating another copy of every BreedResult and
     * graph stack.</p>
     */
    private static void auditCropReplayBasis(CropSnapshot baseline, ClassLoader loader)
            throws ExportFailure {
        try {
            Object breeder = cropBreeder(loader);
            if (baseline == null || breeder != baseline.breeder) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "IC2 Crop Plugin Breeder.INSTANCE changed before compact replay audit");
            }
            Field craftField = exactDeclaredField(breeder.getClass(), "craft", Map.class);
            Field usageField = exactDeclaredField(breeder.getClass(), "usage", Map.class);
            Object craftValue = craftField.get(breeder);
            Object usageValue = usageField.get(breeder);
            if (craftValue != baseline.rawCraftCache
                    || usageValue != baseline.rawUsageCache
                    || !(craftValue instanceof Map)
                    || !(usageValue instanceof Map)) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "IC2 Crop Plugin replaced its diagnostic craft/usage cache maps");
            }
            Map<?, ?> craft = (Map<?, ?>) craftValue;
            Map<?, ?> usage = (Map<?, ?>) usageValue;
            if (craft.isEmpty() || usage.isEmpty()) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "IC2 Crop Plugin diagnostic craft/usage cache became empty");
            }

            CropIdentityContract cropIdentities = CropIdentityContract.load(loader);
            CropGraphSemanticContract graphContract =
                    CropGraphSemanticContract.load(loader, cropIdentities);
            final Map<String, Object> cropsById = new HashMap<String, Object>();
            final IdentityHashMap<Object, String> cropIdsByIdentity =
                    new IdentityHashMap<Object, String>();
            List<Object> currentUniverse = exactCropUniverse(
                    loader, cropIdentities, cropsById, cropIdsByIdentity);
            requireExactCropUniverseIdentities(baseline.exactCropUniverse, currentUniverse);

            Class<?> resultClass = Class.forName(CROP_RESULT_CLASS, false, loader);
            RawCropCacheDiagnostics raw = auditRawCropCache(
                    craft, usage, resultClass, cropIdsByIdentity,
                    cropsById, graphContract);
            requireSameRawCropDiagnostics(baseline, raw);

            Method calculateRatio = exactCropRatioMethod(
                    loader, cropIdentities.cropCardClass());
            DeterministicCropMatrixContract.Snapshot matrix =
                    DeterministicCropMatrixContract.audit(
                            currentUniverse,
                            exactCropCanonicalIds(cropIdsByIdentity),
                            exactCropRatios(calculateRatio));
            requireSameCropMatrix(baseline.matrix, matrix);

            Map<String, String> observedCleanStacks = new HashMap<String, String>();
            for (CropResultRecord representative : baseline.auditRepresentatives) {
                graphContract.validateCanonicalInputOrder(representative.result);
                CropGraphSemanticContract.GraphRecipe graph =
                        graphContract.capture(representative.result, cropsById);
                if (!representative.cleanGraph.canonical.equals(graph.canonical)) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "retained IC2 crop audit representative mutated: "
                                    + representative.graphSemantics.semanticId);
                }
                requireConsistentCleanCropStack(observedCleanStacks, graph.output);
                for (CropGraphSemanticContract.GraphStack input : graph.inputs) {
                    requireConsistentCleanCropStack(observedCleanStacks, input);
                }
            }
            if (observedCleanStacks.size() != EXPECTED_CROP_CARD_COUNT
                    || !baseline.cleanGraphStacksByCropId.equals(observedCleanStacks)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "compact IC2 crop audit clean-stack basis drifted; expected="
                                + baseline.cleanGraphStacksByCropId.size()
                                + ", observed=" + observedCleanStacks.size());
            }
            if (cropsById.size() != EXPECTED_CROP_CARD_COUNT) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "compact IC2 crop audit resolved " + cropsById.size()
                                + "/" + EXPECTED_CROP_CARD_COUNT + " CropCards");
            }
            GtnhNeiExportMod.LOGGER.debug(
                    "[gtnh-nei-export] Compact-audited deterministic IC2 crop derivation basis; "
                            + "crops={}, ratioMatrix={}x{}, representativeGraphs={}, "
                            + "queryBucketClosure={}, matrixFingerprint={}, corpusFingerprint={}",
                    matrix.cropCount, matrix.cropCount, matrix.cropCount,
                    baseline.auditRepresentatives.size(), baseline.records.size(),
                    matrix.fingerprint, baseline.fingerprint);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(unwrap(error));
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not compact-audit the deterministic IC2 crop derivation basis",
                    unwrap(error));
        }
    }

    private static Method exactCropRatioMethod(ClassLoader loader, Class<?> cropCardClass)
            throws Exception {
        Class<?> ratioClass = Class.forName(CROP_RATIO_CLASS, false, loader);
        Method calculateRatio = ratioClass.getMethod(
                "calculateRatioFor", cropCardClass, cropCardClass);
        int modifiers = calculateRatio.getModifiers();
        if (calculateRatio.getDeclaringClass() != ratioClass
                || calculateRatio.getReturnType() != int.class
                || !java.lang.reflect.Modifier.isPublic(modifiers)
                || !java.lang.reflect.Modifier.isStatic(modifiers)
                || calculateRatio.isBridge() || calculateRatio.isSynthetic()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    CROP_RATIO_CLASS
                            + ".calculateRatioFor must remain an exact public static "
                            + "(CropCard, CropCard) -> int method");
        }
        return calculateRatio;
    }

    private static DeterministicCropMatrixContract.CanonicalId<Object>
            exactCropCanonicalIds(final IdentityHashMap<Object, String> cropIdsByIdentity) {
        return new DeterministicCropMatrixContract.CanonicalId<Object>() {
            @Override
            public String canonicalId(Object crop) throws ExportFailure {
                String id = cropIdsByIdentity.get(crop);
                if (id == null) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "deterministic crop replay encountered a CropCard outside exact "
                                    + "IC2NeiPlugin.ALL_CROPS");
                }
                return id;
            }
        };
    }

    private static DeterministicCropMatrixContract.Ratio<Object> exactCropRatios(
            final Method calculateRatio) {
        return new DeterministicCropMatrixContract.Ratio<Object>() {
            @Override
            public int calculate(Object result, Object input) throws ExportFailure {
                try {
                    Object value = calculateRatio.invoke(null, result, input);
                    if (!(value instanceof Integer)) {
                        throw new ExportFailure("RECIPE_SEMANTICS",
                                "IC2 crop ratio method returned a non-integer");
                    }
                    return ((Integer) value).intValue();
                } catch (ExportFailure failure) {
                    throw failure;
                } catch (Throwable error) {
                    FatalErrors.rethrowIfFatal(unwrap(error));
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "could not evaluate the exact public IC2 crop ratio",
                            unwrap(error));
                }
            }
        };
    }

    static void requireExactCropUniverseIdentities(
            List<Object> expected, List<Object> observed) throws ExportFailure {
        if (expected == null || observed == null || expected.size() != observed.size()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "exact IC2NeiPlugin.ALL_CROPS size changed during export");
        }
        for (int index = 0; index < expected.size(); index++) {
            if (expected.get(index) != observed.get(index)) {
                throw new ExportFailure("HANDLER_UNLOADED",
                        "exact IC2NeiPlugin.ALL_CROPS identity/order changed at index "
                                + index);
            }
        }
    }

    private static void requireSameRawCropDiagnostics(
            CropSnapshot expected, RawCropCacheDiagnostics observed) throws ExportFailure {
        if (expected.rawUnionResultIdentities != observed.unionResultIdentities
                || expected.rawCraftResultIdentities != observed.craftResultIdentities
                || expected.rawUsageResultIdentities != observed.usageResultIdentities
                || expected.rawUsageOnlyResultIdentities != observed.usageOnlyResultIdentities
                || expected.rawUsageOccurrences != observed.usageOccurrences
                || expected.rawConflatedCraftRepresentatives
                != observed.conflatedCraftRepresentatives
                || !expected.rawDiagnosticFingerprint.equals(observed.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "diagnostic upstream IC2 crop cache mutated within one client boot; "
                            + "baseline=" + expected.rawDiagnosticFingerprint
                            + ", current=" + observed.fingerprint);
        }
    }

    static void requireSameCropMatrix(
            DeterministicCropMatrixContract.Snapshot expected,
            DeterministicCropMatrixContract.Snapshot observed) throws ExportFailure {
        if (expected == null || observed == null
                || expected.cropCount != observed.cropCount
                || expected.pairCount != observed.pairCount
                || expected.outcomeCount != observed.outcomeCount
                || expected.minimumOutcomesPerPair != observed.minimumOutcomesPerPair
                || expected.maximumOutcomesPerPair != observed.maximumOutcomesPerPair
                || expected.minimumTotalPoints != observed.minimumTotalPoints
                || expected.maximumTotalPoints != observed.maximumTotalPoints
                || !expected.fingerprint.equals(observed.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "deterministic IC2 crop derivation matrix drifted during export; expected="
                            + (expected == null ? "null" : expected.fingerprint)
                            + ", observed="
                            + (observed == null ? "null" : observed.fingerprint));
        }
    }

    private static List<CropResultRecord> selectCropAuditRepresentatives(
            List<CropResultRecord> records, Set<String> requiredCropIds)
            throws ExportFailure {
        Set<String> remaining = new HashSet<String>(requiredCropIds);
        List<CropResultRecord> representatives = new ArrayList<CropResultRecord>();
        for (CropResultRecord record : records) {
            boolean addsCoverage = remaining.contains(record.resultId);
            for (String inputId : record.inputIds) {
                addsCoverage |= remaining.contains(inputId);
            }
            if (!addsCoverage) {
                continue;
            }
            representatives.add(record);
            remaining.remove(record.resultId);
            remaining.removeAll(record.inputIds);
            if (remaining.isEmpty()) {
                break;
            }
        }
        if (!remaining.isEmpty() || representatives.isEmpty()
                || representatives.size() > EXPECTED_CROP_CARD_COUNT) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "could not select a bounded clean-stack audit cover for exact ALL_CROPS; "
                            + "representatives=" + representatives.size()
                            + ", missing=" + remaining.size());
        }
        return Collections.unmodifiableList(
                new ArrayList<CropResultRecord>(representatives));
    }

    private static List<Object> exactCropUniverse(
            ClassLoader loader,
            CropIdentityContract cropIdentities,
            Map<String, Object> cropsById,
            IdentityHashMap<Object, String> cropIdsByIdentity) throws Exception {
        Class<?> modClass = Class.forName(CROP_MOD_CLASS, false, loader);
        Field allCropsField = exactPublicField(modClass, "ALL_CROPS", List.class);
        int modifiers = allCropsField.getModifiers();
        if (allCropsField.getDeclaringClass() != modClass
                || !java.lang.reflect.Modifier.isPublic(modifiers)
                || !java.lang.reflect.Modifier.isStatic(modifiers)
                || java.lang.reflect.Modifier.isFinal(modifiers)
                || allCropsField.isSynthetic()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    CROP_MOD_CLASS + ".ALL_CROPS must remain an exact public static "
                            + "non-final java.util.List field");
        }
        Object value = allCropsField.get(null);
        if (value == null || value.getClass() != ArrayList.class) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    CROP_MOD_CLASS + ".ALL_CROPS must contain an exact java.util.ArrayList; got "
                            + (value == null ? "null" : value.getClass().getName()));
        }
        List<?> source = (List<?>) value;
        if (source.size() != EXPECTED_CROP_CARD_COUNT) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    CROP_MOD_CLASS + ".ALL_CROPS must contain the exact pinned "
                            + EXPECTED_CROP_CARD_COUNT + " CropCards; size=" + source.size());
        }
        List<Object> universe = new ArrayList<Object>(source.size());
        for (Object crop : source) {
            String cropId = cropIdentities.requireCanonicalId(crop, cropsById);
            if (cropIdsByIdentity.put(crop, cropId) != null) {
                throw new ExportFailure("HANDLER_DUPLICATE",
                        CROP_MOD_CLASS + ".ALL_CROPS repeats a CropCard identity");
            }
            universe.add(crop);
        }
        return Collections.unmodifiableList(universe);
    }

    private static RawCropCacheDiagnostics auditRawCropCache(
            Map<?, ?> craft,
            Map<?, ?> usage,
            Class<?> resultClass,
            final IdentityHashMap<Object, String> cropIdsByIdentity,
            final Map<String, Object> cropsById,
            final CropGraphSemanticContract graphContract) throws ExportFailure {
        final Map<String, String> rawCleanStacksByCropId =
                new HashMap<String, String>();
        CropCacheViewContract.Snapshot<CropResultRecord> raw =
                CropCacheViewContract.audit(
                        craft, usage, resultClass, cropIdsByIdentity,
                        new CropCacheViewContract.Capture<CropResultRecord>() {
                            @Override
                            public CropResultRecord capture(Object result)
                                    throws ExportFailure {
                                CropGraphSemanticContract.GraphRecipe graph =
                                        graphContract.capture(result, cropsById);
                                requireConsistentCleanCropStack(
                                        rawCleanStacksByCropId, graph.output);
                                for (CropGraphSemanticContract.GraphStack input : graph.inputs) {
                                    requireConsistentCleanCropStack(
                                            rawCleanStacksByCropId, input);
                                }
                                return new CropResultRecord(graph);
                            }
                        },
                        new CropCacheViewContract.Matcher() {
                            @Override
                            public boolean matches(Object left, Object right)
                                    throws ExportFailure {
                                return graphContract.matches(left, right);
                            }
                        });
        return new RawCropCacheDiagnostics(
                raw.records.size(),
                raw.craftResultIdentities,
                raw.usageResultIdentities,
                raw.usageOnlyResultIdentities,
                raw.usageOccurrences,
                raw.conflatedCraftRepresentatives,
                Naming.sha256("ic2-crop-upstream-diagnostic-only-v1\n"
                        + raw.membershipCanonical));
    }

    private static List<CropResultRecord> constructDeterministicCropRecords(
            DeterministicCropReplayContract.Snapshot<Object> replay,
            Class<?> resultClass,
            Class<?> cropCardClass,
            CropGraphSemanticContract graphContract,
            Map<String, Object> cropsById,
            Map<String, String> cleanGraphStacksByCropId) throws Exception {
        Class<?> cropArrayClass = Array.newInstance(cropCardClass, 0).getClass();
        Constructor<?> constructor = resultClass.getConstructor(
                cropCardClass, int.class, int.class, cropArrayClass);
        int constructorModifiers = constructor.getModifiers();
        if (constructor.getDeclaringClass() != resultClass
                || !java.lang.reflect.Modifier.isPublic(constructorModifiers)
                || !constructor.isVarArgs()
                || constructor.isSynthetic()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    CROP_RESULT_CLASS + " constructor must remain exact public "
                            + "(CropCard,int,int,CropCard...)");
        }
        Method getInput = resultClass.getMethod("getInput");
        int getInputModifiers = getInput.getModifiers();
        if (getInput.getDeclaringClass() != resultClass
                || getInput.getReturnType() != cropArrayClass
                || getInput.getParameterTypes().length != 0
                || !java.lang.reflect.Modifier.isPublic(getInputModifiers)
                || java.lang.reflect.Modifier.isStatic(getInputModifiers)
                || getInput.isBridge() || getInput.isSynthetic()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    CROP_RESULT_CLASS + ".getInput must remain exact public () -> CropCard[]");
        }

        List<CropResultRecord> records =
                new ArrayList<CropResultRecord>(replay.winners.size());
        for (DeterministicCropReplayContract.Winner<Object> winner : replay.winners) {
            Object canonicalInputs = Array.newInstance(cropCardClass, 2);
            Array.set(canonicalInputs, 0, winner.leftInput);
            Array.set(canonicalInputs, 1, winner.rightInput);
            Object result = constructor.newInstance(
                    winner.output,
                    Integer.valueOf(winner.points),
                    Integer.valueOf(winner.totalPoints),
                    canonicalInputs);
            Object retainedInputs = getInput.invoke(result);
            if (retainedInputs != canonicalInputs) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult constructor stopped retaining its exact input array");
            }
            // The pinned constructor sorts by runtime Object.hashCode. Restore the
            // canonical ID order before graph capture and NEI preview construction
            // so left/right preview pixels are reproducible across JVM boots.
            restoreCanonicalCropInputOrder(
                    retainedInputs, cropCardClass, winner.leftInput, winner.rightInput);
            graphContract.validateCanonicalInputOrder(result);

            CropGraphSemanticContract.GraphRecipe graph =
                    graphContract.capture(result, cropsById);
            requireReplayWinnerGraph(winner, graph);
            requireConsistentCleanCropStack(cleanGraphStacksByCropId, graph.output);
            for (CropGraphSemanticContract.GraphStack input : graph.inputs) {
                requireConsistentCleanCropStack(cleanGraphStacksByCropId, input);
            }
            records.add(new CropResultRecord(graph));
        }
        return records;
    }

    static void restoreCanonicalCropInputOrder(
            Object retainedInputs,
            Class<?> cropCardClass,
            Object leftInput,
            Object rightInput) throws ExportFailure {
        if (retainedInputs == null || cropCardClass == null
                || leftInput == null || rightInput == null
                || retainedInputs.getClass() != Array.newInstance(cropCardClass, 0).getClass()
                || Array.getLength(retainedInputs) != 2
                || leftInput == rightInput
                || !cropCardClass.isInstance(leftInput)
                || !cropCardClass.isInstance(rightInput)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "cannot restore canonical two-parent IC2 BreedResult input order");
        }
        Array.set(retainedInputs, 0, leftInput);
        Array.set(retainedInputs, 1, rightInput);
        if (Array.get(retainedInputs, 0) != leftInput
                || Array.get(retainedInputs, 1) != rightInput) {
            throw new ExportFailure("INTERNAL_ERROR",
                    "canonical IC2 BreedResult input order did not persist");
        }
    }

    private static void requireReplayWinnerGraph(
            DeterministicCropReplayContract.Winner<Object> winner,
            CropGraphSemanticContract.GraphRecipe graph) throws ExportFailure {
        if (graph.breedResult == null
                || graph.output.crop != winner.output
                || graph.points != winner.points
                || graph.total != winner.totalPoints
                || graph.inputs.size() != DeterministicCropReplayContract.INPUT_ARITY) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "constructed IC2 BreedResult diverged from deterministic replay winner "
                            + winner.selectionIndex);
        }
        boolean hasLeft = false;
        boolean hasRight = false;
        for (CropGraphSemanticContract.GraphStack input : graph.inputs) {
            hasLeft |= input.crop == winner.leftInput;
            hasRight |= input.crop == winner.rightInput;
        }
        if (!hasLeft || !hasRight) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "constructed IC2 BreedResult changed deterministic replay parents at winner "
                            + winner.selectionIndex);
        }
    }

    private static void requireConsistentCleanCropStack(
            Map<String, String> cleanGraphStacksByCropId,
            CropGraphSemanticContract.GraphStack graphStack) throws ExportFailure {
        String existing = cleanGraphStacksByCropId.get(graphStack.cropId);
        if (existing != null && !existing.equals(graphStack.stackCanonical)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 CropCard maps to more than one clean graph stack/amount/NBT: "
                            + graphStack.cropId);
        }
        if (existing == null) {
            cleanGraphStacksByCropId.put(
                    graphStack.cropId, graphStack.stackCanonical);
        }
    }

    private static List<Thread> liveThreadsNamed(String name) {
        List<Thread> matches = new ArrayList<Thread>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread != null && name.equals(thread.getName()) && thread.isAlive()) {
                matches.add(thread);
            }
        }
        return matches;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addCached(TemplateRecipeHandler handler, Object cached,
                                  Class<?> expectedClass) throws ExportFailure {
        if (cached == null || cached.getClass() != expectedClass) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "adapter constructed an unexpected cached-recipe class");
        }
        ((List) handler.arecipes).add(cached);
    }

    private static MessageDigest newSha256Digest(String label) throws ExportFailure {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new ExportFailure("INTERNAL_ERROR",
                    label + " requires JVM SHA-256 support", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte part : bytes) {
            value.append(Character.forDigit((part >>> 4) & 0x0f, 16));
            value.append(Character.forDigit(part & 0x0f, 16));
        }
        return value.toString();
    }

    private static List<PositionedStack> positionedList(Object value, String label)
            throws ExportFailure {
        if (!(value instanceof List)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label + " is not a List");
        }
        for (Object entry : (List<?>) value) {
            if (!(entry instanceof PositionedStack)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        label + " contains a non-PositionedStack value");
            }
        }
        @SuppressWarnings("unchecked")
        List<PositionedStack> original = (List<PositionedStack>) value;
        return original;
    }

    private static Field exactDeclaredField(Class<?> type, String name, Class<?> expectedType)
            throws Exception {
        Field field = type.getDeclaredField(name);
        if (field.getType() != expectedType) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName() + "." + name
                    + " type drifted; expected " + expectedType.getName() + ", got "
                    + field.getType().getName());
        }
        field.setAccessible(true);
        return field;
    }

    private static Field exactPublicField(Class<?> type, String name, Class<?> expectedType)
            throws Exception {
        Field field = type.getField(name);
        if (field.getType() != expectedType) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName() + "." + name
                    + " type drifted; expected " + expectedType.getName() + ", got "
                    + field.getType().getName());
        }
        return field;
    }

    private static Method exactDeclaredMethod(Class<?> type, String name, Class<?>... parameters)
            throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        if (method.getReturnType() != void.class) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName() + "." + name
                    + " must return void");
        }
        method.setAccessible(true);
        return method;
    }

    private static Method exactDeclaredReturningMethod(
            Class<?> type, String name, Class<?> expectedReturn, Class<?>... parameters)
            throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        if (method.getReturnType() != expectedReturn) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName() + "." + name
                    + " return type drifted; expected " + expectedReturn.getName() + ", got "
                    + method.getReturnType().getName());
        }
        method.setAccessible(true);
        return method;
    }

    private static Method exactPublicReturningMethod(
            Class<?> type, String name, Class<?> expectedReturn, Class<?>... parameters)
            throws Exception {
        Method method = type.getMethod(name, parameters);
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName() + "." + name
                    + " is no longer public");
        }
        if (method.getReturnType() != expectedReturn) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName() + "." + name
                    + " return type drifted; expected " + expectedReturn.getName() + ", got "
                    + method.getReturnType().getName());
        }
        return method;
    }

    private static void requireInterface(Class<?> type) throws ExportFailure {
        if (!type.isInterface()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + " is no longer an interface");
        }
    }

    private static void requireExactClass(Object value, String expected) throws ExportFailure {
        if (!(value instanceof TemplateRecipeHandler)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    expected + " is no longer a TemplateRecipeHandler");
        }
        requireClassName(value.getClass(), expected);
    }

    private static void requireExactDirectHandlerClass(Object value, String expected)
            throws ExportFailure {
        if (!(value instanceof ICraftingHandler) || value instanceof TemplateRecipeHandler) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    expected + " is no longer a direct ICraftingHandler");
        }
        requireClassName(value.getClass(), expected);
    }

    private static void requireExactAnyCraftingHandlerClass(Object value, String expected)
            throws ExportFailure {
        if (!(value instanceof ICraftingHandler)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    expected + " is no longer an ICraftingHandler");
        }
        requireClassName(value.getClass(), expected);
    }

    private static boolean sameNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String diagnosticString(String value) {
        if (value == null) return "<null>";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\"", "\\\"") + "\"";
    }

    private static void requireClassName(Class<?> type, String expected) throws ExportFailure {
        if (!expected.equals(type.getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "expected class " + expected + ", got " + type.getName());
        }
    }

    private static void requireCount(String label, int expected, int actual)
            throws ExportFailure {
        if (actual != expected) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    label + " must equal " + expected + "; got " + actual);
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof InvocationTargetException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
