package com.recipetree.neiexport1710;

import codechicken.nei.ItemList;
import codechicken.nei.PositionedStack;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.NEIRecipeWidget;
import codechicken.nei.recipe.RecipeCatalysts;
import codechicken.nei.recipe.RecipeHandlerRef;
import com.google.gson.stream.JsonWriter;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ExportJob {
    private static final int RECIPE_BACKGROUND_ARGB = 0xffc6c6c6;

    interface RuntimeIntegrityGate {
        void verify() throws Exception;
    }

    private static final class IngredientPair {
        final String key;
        final int amount;
        final String icon;

        IngredientPair(String key, int amount, String icon) {
            this.key = key;
            this.amount = amount;
            this.icon = icon;
        }
    }

    private static final class SlotData {
        final List<IngredientPair> alternatives = new ArrayList<IngredientPair>();
        /** Null for deterministic slots; otherwise the exact probability shared by alternatives. */
        Double probability;
    }

    private static final class RecipeData {
        final List<SlotData> inputs = new ArrayList<SlotData>();
        final List<SlotData> outputs = new ArrayList<SlotData>();
        final List<SlotData> catalysts = new ArrayList<SlotData>();
        final Set<String> usedKeys = new LinkedHashSet<String>();
        final Set<String> outputKeys = new LinkedHashSet<String>();
        String firstIcon;
        String image;
        int width;
        int height;
    }

    private final ExportRequest request;
    private final Path finalOutput;
    private final Path stagingOutput;
    private final ExportContext context;
    private final RuntimeIntegrityGate runtimeIntegrityGate;
    private TcnaAspectCostSemanticNormalizer tcnaAspectCostNormalizer;
    private GregTechOutputlessSemanticPreflight.Snapshot gregTechOutputlessSemantics;
    private GregTechForestryScannedSaplingPreflight.Snapshot
            gregTechForestryScannedSapling;
    private GregTechForestryScannedPollenPreflight.Snapshot
            gregTechForestryScannedPollen;
    private CatalogExcludedFuelPreflight.Snapshot catalogExcludedFuelRows;
    private Ae2InternalFacadeRecipePreflight.Snapshot ae2InternalFacadeRecipe;
    private final long startedNanos = System.nanoTime();
    private List<StackIdentity> initialItems = Collections.emptyList();
    private List<HandlerCategoryPlan> plans = Collections.emptyList();
    private int itemCursor;
    private int categoryCursor;
    private HandlerCategoryPlan currentPlan;
    private ICraftingHandler currentHandler;
    private ExportContext.CategoryMeta currentMeta;
    private JsonWriter currentRecipesWriter;
    private int currentCategoryIndex = -1;
    private int recipeCursor;
    private boolean complete;
    private boolean failed;

    ExportJob(ExportRequest request, List<ICraftingHandler> prototypes,
              RuntimeIntegrityGate runtimeIntegrityGate) throws IOException {
        this.request = request;
        if (runtimeIntegrityGate == null) {
            throw new IOException("Runtime integrity gate is required");
        }
        this.runtimeIntegrityGate = runtimeIntegrityGate;
        this.finalOutput = request.output;
        Path parent = finalOutput.getParent();
        Files.createDirectories(parent);
        this.stagingOutput = parent.resolve("." + finalOutput.getFileName()
                + ".staging-" + UUID.randomUUID());
        this.context = new ExportContext(stagingOutput, request);
        try {
            verifyRuntimePins();
            HandlerCategoryPlan.PlanningResult planning =
                    HandlerCategoryPlan.createPinnedGtnh(
                            new ArrayList<ICraftingHandler>(prototypes));
            plans = planning.categories;
            context.registeredCraftingHandlers = planning.observedHandlers;
            context.exportableCraftingHandlers = planning.categories.size();
            context.adaptedHandlerCategories = planning.adaptedCategories();
            context.excludedNonRecipeHandlers = planning.excludedNonRecipeHandlers();
            context.excludedEmptyRecipeHandlers = planning.excludedEmptyRecipeHandlers;
            context.excludedUnboundTemplateRecipeHandlers =
                    planning.excludedUnboundTemplateRecipeHandlers();
            context.handlerPolicies.addAll(planning.policies);
            catalogExcludedFuelRows = CatalogExcludedFuelPreflight.preflight(plans);
            ae2InternalFacadeRecipe = Ae2InternalFacadeRecipePreflight.preflight(plans);
            gregTechOutputlessSemantics =
                    GregTechOutputlessSemanticPreflight.preflight(plans);
            applyGregTechOutputlessTelemetry(gregTechOutputlessSemantics);
            gregTechForestryScannedSapling =
                    GregTechForestryScannedSaplingPreflight.preflight(plans);
            gregTechForestryScannedPollen =
                    GregTechForestryScannedPollenPreflight.preflight(plans);
            context.itemListLoaded = ItemList.loadFinished;
            initialItems = snapshotInitialItems();
            tcnaAspectCostNormalizer =
                    TcnaAspectCostSemanticNormalizer.create(initialItems);
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Started staging export {} -> {}; items={}, observed handlers={}, "
                            + "exportable categories={}, adapted={}, explicit non-recipe queries={}, "
                            + "pinned empty recipe categories={}, unbound material-template "
                            + "categories={}",
                    stagingOutput, finalOutput, initialItems.size(),
                    planning.observedHandlers, plans.size(), planning.adaptedCategories(),
                    planning.excludedNonRecipeHandlers(),
                    planning.excludedEmptyRecipeHandlers,
                    planning.excludedUnboundTemplateRecipeHandlers());
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            abort(error);
        }
    }

    private static void verifyRuntimePins() throws ExportFailure {
        PinnedRuntimePolicy.verify();
        StackIdentity.verifyPinnedCatalogRuntime();
    }

    private void applyGregTechOutputlessTelemetry(
            GregTechOutputlessSemanticPreflight.Snapshot snapshot)
            throws ExportFailure {
        GregTechOutputlessSemanticPreflight.requirePromotedSnapshot(snapshot);
        context.gregTechFuelSinkRecipes = snapshot.count(
                GregTechOutputlessSemanticPreflight.Classification.GREGTECH_FUEL_SINK);
        context.gregTechFuelSinkCategories = snapshot.distinctCategoryCount(
                GregTechOutputlessSemanticPreflight.Classification.GREGTECH_FUEL_SINK);
        context.gregTechLargeBoilerFuelSinkRecipes = snapshot.count(
                GregTechOutputlessSemanticPreflight.Classification.LARGE_BOILER_FUEL_SINK);
        context.gregTechLargeBoilerFuelSinkCategories = snapshot.distinctCategoryCount(
                GregTechOutputlessSemanticPreflight.Classification.LARGE_BOILER_FUEL_SINK);
        context.gregTechRadioHatchInformationRecipes = snapshot.count(
                GregTechOutputlessSemanticPreflight.Classification.RADIO_HATCH_INFORMATION);
        context.gregTechQuantumComponentInformationRecipes = snapshot.count(
                GregTechOutputlessSemanticPreflight.Classification.QUANTUM_COMPONENT_INFORMATION);
        context.gregTechSpaceProjectInformationRecipes = snapshot.count(
                GregTechOutputlessSemanticPreflight.Classification.SPACE_PROJECT_INFORMATION);
        context.gregTechOutputlessSemanticCategories = snapshot.distinctCategories();
        context.gregTechOutputlessSemanticRecipes = snapshot.semanticRecipes();
        context.excludedGregTechLargeBoilerPresentationRows =
                snapshot.excludedPresentationRows();
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Promoted exact GregTech outputless semantic preflight "
                        + "coverage; fuel={}/{}, largeBoiler={}/{}, radio={}, quantum={}, "
                        + "space={}, categories={}, semanticRecipes={}, excluded={}, sha256={}",
                context.gregTechFuelSinkRecipes, context.gregTechFuelSinkCategories,
                context.gregTechLargeBoilerFuelSinkRecipes,
                context.gregTechLargeBoilerFuelSinkCategories,
                context.gregTechRadioHatchInformationRecipes,
                context.gregTechQuantumComponentInformationRecipes,
                context.gregTechSpaceProjectInformationRecipes,
                context.gregTechOutputlessSemanticCategories,
                context.gregTechOutputlessSemanticRecipes,
                context.excludedGregTechLargeBoilerPresentationRows, snapshot.sha256());
    }

    private List<StackIdentity> snapshotInitialItems() throws ExportFailure {
        if (!ItemList.loadFinished || ItemList.items == null || ItemList.items.isEmpty()) {
            throw new ExportFailure("ITEM_IDENTITY", "NEI ItemList is not fully loaded");
        }
        List<StackIdentity> items = new ArrayList<StackIdentity>(ItemList.items.size());
        Set<String> retainedUniqueKeys = new java.util.HashSet<String>();
        StackIdentity.CatalogExclusionAudit exclusionAudit =
                new StackIdentity.CatalogExclusionAudit();
        int retainedThaumcraftEldritchObjectMetadataZero = 0;
        int retainedGadomancyEldritchPortalPlacerMetadataZero = 0;
        int retainedThaumicHorizonsIlluminationFocusVariants = 0;
        int retainedThaumicHorizonsIlluminationFocusMetadataMask = 0;
        int retainedTwilightForestExperiment115PublicFood = 0;
        for (int index = 0; index < ItemList.items.size(); index++) {
            ItemStack stack = ItemList.items.get(index);
            try {
                StackIdentity.CatalogExclusion exclusion =
                        StackIdentity.catalogOnlyExclusion(stack);
                if (exclusion != null) {
                    exclusionAudit.record(exclusion, stack);
                    if (exclusion == StackIdentity.AE2FC_FLUID_DROP_PLACEHOLDER) {
                        context.excludedAe2fcFluidDropItemListPlaceholders++;
                    } else if (exclusion == StackIdentity.AE2FC_FLUID_PACKET_PLACEHOLDER) {
                        context.excludedAe2fcFluidPacketItemListPlaceholders++;
                    } else if (exclusion == StackIdentity.BLOOD_MAGIC_BLOOD_LIGHT_HELPER) {
                        context.excludedBloodMagicBloodLightItemListHelpers++;
                    } else if (exclusion
                            == StackIdentity.BLOOD_MAGIC_SPECTRAL_CONTAINER_HELPER) {
                        context.excludedBloodMagicSpectralContainerItemListHelpers++;
                    } else if (exclusion
                            == StackIdentity.ARCHITECTURE_CRAFT_CLADDING_PLACEHOLDER) {
                        context.excludedArchitectureCraftCladdingItemListPlaceholders++;
                    } else if (exclusion
                            == StackIdentity.AVARITIA_EMPTY_MATTER_CLUSTER_PLACEHOLDER) {
                        context.excludedAvaritiaEmptyMatterClusterItemListPlaceholders++;
                    } else if (exclusion
                            == StackIdentity.DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL) {
                        context.excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders++;
                    } else if (exclusion
                            == StackIdentity.LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER) {
                        context.excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries++;
                    } else if (exclusion
                            == StackIdentity.MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER) {
                        context.excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders++;
                    } else if (exclusion
                            == StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER) {
                        context.excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders++;
                    } else if (exclusion
                            == StackIdentity.AE2_CABLE_BUS_INTERNAL_WORLD_ITEM_BLOCK) {
                        context.excludedAe2CableBusInternalBlockItemListEntries++;
                    } else if (exclusion
                            == StackIdentity.AE2_MATRIX_FRAME_INTERNAL_WORLD_ITEM_BLOCK) {
                        context.excludedAe2MatrixFrameInternalBlockItemListEntries++;
                    } else if (exclusion
                            == StackIdentity.BOTANIA_BIFROST_WORLD_STATE) {
                        context.excludedBotaniaBifrostItemListWorldStateEntries++;
                    } else if (exclusion
                            == StackIdentity.BOTANIA_BURIED_PETALS_WORLD_STATE_VARIANT) {
                        context.excludedBotaniaBuriedPetalsItemListWorldStateVariants++;
                        context.excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask
                                |= 1 << stack.getItemDamage();
                    } else if (exclusion
                            == StackIdentity.BOTANIA_CACOPHONIUM_BLOCK_WORLD_STATE) {
                        context.excludedBotaniaCacophoniumBlockItemListWorldStateEntries++;
                    } else if (exclusion
                            == StackIdentity.BOTANIA_ENCHANTER_WORLD_STATE) {
                        context.excludedBotaniaEnchanterItemListWorldStateEntries++;
                    } else if (exclusion
                            == StackIdentity.BOTANIA_FAKE_AIR_WORLD_STATE) {
                        context.excludedBotaniaFakeAirItemListWorldStateEntries++;
                    } else if (exclusion
                            == StackIdentity.BOTANIA_MANA_FLAME_WORLD_STATE) {
                        context.excludedBotaniaManaFlameItemListWorldStateEntries++;
                    } else if (exclusion
                            == StackIdentity.BOTANIA_SOLID_VINE_WORLD_STATE) {
                        context.excludedBotaniaSolidVineItemListWorldStateEntries++;
                    } else if (exclusion
                            == StackIdentity.BOTANIA_STRUCTURE_LIB_ANY_FLOWER_PLACEHOLDER) {
                        context.excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders++;
                    } else if (exclusion
                            == StackIdentity.CARPENTERS_BED_INTERNAL_WORLD_ITEM_BLOCK) {
                        context.excludedCarpentersBedInternalBlockItemListEntries++;
                    } else if (exclusion
                            == StackIdentity.CARPENTERS_DOOR_INTERNAL_WORLD_ITEM_BLOCK) {
                        context.excludedCarpentersDoorInternalBlockItemListEntries++;
                    } else if (exclusion
                            == StackIdentity.STEVES_CARTS_UNCONFIGURED_MODULAR_CART_PLACEHOLDER) {
                        context.excludedStevesCartsUnconfiguredModularCartItemListPlaceholders++;
                    } else if (exclusion
                            == StackIdentity.TCONSTRUCT_BATTLESIGN_INTERNAL_WORLD_ITEM_BLOCK) {
                        context.excludedTConstructBattleSignInternalBlockItemListEntries++;
                    } else if (exclusion
                            == StackIdentity.TCONSTRUCT_HELD_ITEM_INTERNAL_WORLD_ITEM_BLOCK) {
                        context.excludedTConstructHeldItemInternalBlockItemListEntries++;
                    } else if (exclusion
                            == StackIdentity.THAUMCRAFT_BLOCK_HOLE_INTERNAL_WORLD_ITEM_BLOCK) {
                        context.excludedThaumcraftBlockHoleInternalBlockItemListEntries++;
                    } else if (exclusion
                            == StackIdentity.THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_WORLD_ITEM_BLOCK) {
                        context.excludedThaumcraftEldritchPortalInternalBlockItemListEntries++;
                    } else if (exclusion
                            == StackIdentity.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK) {
                        context.excludedThaumicHorizonsBaseLightInternalBlockItemListEntries++;
                    } else if (exclusion
                            == StackIdentity.THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK) {
                        context.excludedThaumicHorizonsSolarLightInternalBlockItemListEntries++;
                    } else if (exclusion
                            == StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK) {
                        context.excludedTwilightForestExperiment115InternalBlockItemListEntries++;
                    } else if (exclusion
                            == StackIdentity.WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK) {
                        context.excludedWitchingGadgetsCustomAirInternalBlockItemListEntries++;
                    } else {
                        throw new IllegalStateException(
                                "ITEM_IDENTITY: unrecognized catalog exclusion policy "
                                        + exclusion.contract);
                    }
                    GtnhNeiExportMod.LOGGER.warn(
                            "[gtnh-nei-export] Excluding pinned global ItemList entry "
                                    + "index={} policy={} {}",
                            index, exclusion.contract, StackIdentity.describe(stack));
                    continue;
                }
                StackIdentity identity = StackIdentity.of(stack);
                items.add(identity);
                retainedUniqueKeys.add(identity.key);
                if (StackIdentity.isExactThaumcraftEldritchObjectRetainedItemListEntry(stack)) {
                    retainedThaumcraftEldritchObjectMetadataZero++;
                }
                if (StackIdentity.isExactGadomancyEldritchPortalRetainedItemListEntry(stack)) {
                    retainedGadomancyEldritchPortalPlacerMetadataZero++;
                }
                if (StackIdentity
                        .isExactThaumicHorizonsIlluminationFocusRetainedItemListEntry(stack)) {
                    retainedThaumicHorizonsIlluminationFocusVariants++;
                    retainedThaumicHorizonsIlluminationFocusMetadataMask
                            |= 1 << stack.getItemDamage();
                }
                if (StackIdentity
                        .isExactTwilightForestExperiment115PublicItemListEntry(stack)) {
                    retainedTwilightForestExperiment115PublicFood++;
                }
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                throw new ExportFailure("ITEM_IDENTITY", "global ItemList entry #" + index, error);
            }
        }
        try {
            exclusionAudit.requireExpected();
            context.itemListRawEntries = ItemList.items.size();
            context.itemListExcludedEntries = ItemList.items.size() - items.size();
            context.itemListRetainedEntries = items.size();
            context.itemListRetainedUniqueIdentities = retainedUniqueKeys.size();
            StackIdentity.requireExactGlobalItemListCardinality(
                    context.itemListRawEntries, context.itemListExcludedEntries,
                    context.itemListRetainedEntries,
                    context.itemListRetainedUniqueIdentities);
            StackIdentity.requireExactRetainedEldritchItemListCounts(
                    retainedThaumcraftEldritchObjectMetadataZero,
                    retainedGadomancyEldritchPortalPlacerMetadataZero);
            StackIdentity.requireExactThaumicHorizonsIlluminationItemListCounts(
                    exclusionAudit.count(
                            StackIdentity.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK),
                    retainedThaumicHorizonsIlluminationFocusVariants,
                    retainedThaumicHorizonsIlluminationFocusMetadataMask);
            StackIdentity.requireExactTwilightForestExperiment115ItemListCounts(
                    exclusionAudit.count(
                            StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK),
                    retainedTwilightForestExperiment115PublicFood);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "global ItemList exclusion/retention completeness contract", error);
        }
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied explicit global ItemList exclusion policy: raw={}, "
                        + "excluded={}, retained={}, retainedUnique={}, "
                        + "ae2fc:fluid_drop={}, ae2fc:fluid_packet={}, "
                        + "AWWayofTime:bloodLight={}, AWWayofTime:spectralContainer={}, "
                        + "ArchitectureCraft:cladding={}, Avaritia:Matter_Cluster={}, "
                        + "dreamcraft:item.Nothing={}, littletiles:BlockLittleTiles={}, "
                        + "malisisdoors:item.custom_door={}, malisisdoors:mixed_block={}",
                context.itemListRawEntries, context.itemListExcludedEntries,
                context.itemListRetainedEntries, context.itemListRetainedUniqueIdentities,
                exclusionAudit.count(StackIdentity.AE2FC_FLUID_DROP_PLACEHOLDER),
                exclusionAudit.count(StackIdentity.AE2FC_FLUID_PACKET_PLACEHOLDER),
                exclusionAudit.count(StackIdentity.BLOOD_MAGIC_BLOOD_LIGHT_HELPER),
                exclusionAudit.count(StackIdentity.BLOOD_MAGIC_SPECTRAL_CONTAINER_HELPER),
                exclusionAudit.count(StackIdentity.ARCHITECTURE_CRAFT_CLADDING_PLACEHOLDER),
                exclusionAudit.count(StackIdentity.AVARITIA_EMPTY_MATTER_CLUSTER_PLACEHOLDER),
                exclusionAudit.count(
                        StackIdentity.DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL),
                exclusionAudit.count(
                        StackIdentity.LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER),
                exclusionAudit.count(
                        StackIdentity.MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER),
                exclusionAudit.count(
                        StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER));
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact AE2 owner-internal ItemList exclusions: "
                        + "appliedenergistics2:tile.BlockCableBus={}, "
                        + "appliedenergistics2:tile.BlockMatrixFrame={}",
                exclusionAudit.count(StackIdentity.AE2_CABLE_BUS_INTERNAL_WORLD_ITEM_BLOCK),
                exclusionAudit.count(StackIdentity.AE2_MATRIX_FRAME_INTERNAL_WORLD_ITEM_BLOCK));
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied pinned Botania ItemList exclusions: bifrost={}, "
                        + "buriedPetals={}, buriedPetalsMetadataMask={}, cacophoniumBlock={}, "
                        + "enchanter={}, fakeAir={}, manaFlame={}, solidVine={}, "
                        + "flower_structurelib={}",
                exclusionAudit.count(StackIdentity.BOTANIA_BIFROST_WORLD_STATE),
                exclusionAudit.count(StackIdentity.BOTANIA_BURIED_PETALS_WORLD_STATE_VARIANT),
                "0x" + Integer.toHexString(exclusionAudit.metadataMask(
                        StackIdentity.BOTANIA_BURIED_PETALS_WORLD_STATE_VARIANT)),
                exclusionAudit.count(StackIdentity.BOTANIA_CACOPHONIUM_BLOCK_WORLD_STATE),
                exclusionAudit.count(StackIdentity.BOTANIA_ENCHANTER_WORLD_STATE),
                exclusionAudit.count(StackIdentity.BOTANIA_FAKE_AIR_WORLD_STATE),
                exclusionAudit.count(StackIdentity.BOTANIA_MANA_FLAME_WORLD_STATE),
                exclusionAudit.count(StackIdentity.BOTANIA_SOLID_VINE_WORLD_STATE),
                exclusionAudit.count(StackIdentity.BOTANIA_STRUCTURE_LIB_ANY_FLOWER_PLACEHOLDER));
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact internal/placeholder ItemList exclusions: "
                        + "CarpentersBlocks:bed={}, CarpentersBlocks:door={}, "
                        + "StevesCarts:ModularCart={}, TConstruct:BattleSignBlock={}, "
                        + "TConstruct:HeldItemBlock={}, Thaumcraft:blockHole={}, "
                        + "Thaumcraft:blockPortalEldritch={}, ThaumicHorizons:light={}, "
                        + "ThaumicHorizons:lightSolar={}, "
                        + "TwilightForest:tile.TFExperiment115={}, "
                        + "WitchingGadgets:WG_CustomAir={}",
                exclusionAudit.count(StackIdentity.CARPENTERS_BED_INTERNAL_WORLD_ITEM_BLOCK),
                exclusionAudit.count(StackIdentity.CARPENTERS_DOOR_INTERNAL_WORLD_ITEM_BLOCK),
                exclusionAudit.count(
                        StackIdentity.STEVES_CARTS_UNCONFIGURED_MODULAR_CART_PLACEHOLDER),
                exclusionAudit.count(
                        StackIdentity.TCONSTRUCT_BATTLESIGN_INTERNAL_WORLD_ITEM_BLOCK),
                exclusionAudit.count(
                        StackIdentity.TCONSTRUCT_HELD_ITEM_INTERNAL_WORLD_ITEM_BLOCK),
                exclusionAudit.count(
                        StackIdentity.THAUMCRAFT_BLOCK_HOLE_INTERNAL_WORLD_ITEM_BLOCK),
                exclusionAudit.count(
                        StackIdentity.THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_WORLD_ITEM_BLOCK),
                exclusionAudit.count(
                        StackIdentity.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK),
                exclusionAudit.count(
                        StackIdentity.THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK),
                exclusionAudit.count(
                        StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK),
                exclusionAudit.count(
                        StackIdentity.WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK));
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Verified exact retained Eldritch ItemList entries: "
                        + "Thaumcraft:ItemEldritchObject metadata 0={}, "
                        + "gadomancy:BlockAdditionalEldritchPortal metadata 0={}",
                retainedThaumcraftEldritchObjectMetadataZero,
                retainedGadomancyEldritchPortalPlacerMetadataZero);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Verified Thaumic Horizons illumination boundary: "
                        + "excluded base light={}, excluded solar light={}, "
                        + "retained focus variants={}, focusMetadataMask={}",
                exclusionAudit.count(
                        StackIdentity.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK),
                exclusionAudit.count(
                        StackIdentity.THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK),
                retainedThaumicHorizonsIlluminationFocusVariants,
                "0x" + Integer.toHexString(
                        retainedThaumicHorizonsIlluminationFocusMetadataMask));
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Verified Twilight Forest Experiment 115 boundary: "
                        + "excluded internal world-state ItemBlock={}, retained public food={}",
                exclusionAudit.count(
                        StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK),
                retainedTwilightForestExperiment115PublicFood);
        Collections.sort(items);
        return items;
    }

    void tick(long deadlineNanos) {
        if (complete) {
            return;
        }
        boolean first = true;
        try {
            while (!complete && (first || System.nanoTime() < deadlineNanos)) {
                first = false;
                step();
            }
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            abort(error);
        }
    }

    private void step() throws Exception {
        if (itemCursor < initialItems.size()) {
            context.catalog.ensure(initialItems.get(itemCursor++));
            if (itemCursor % 1000 == 0) {
                GtnhNeiExportMod.LOGGER.info(
                        "[gtnh-nei-export] Items {}/{}; unique={}, PNG pending={}",
                        itemCursor, initialItems.size(), context.catalog.count(), context.pngWriter.pending());
            }
            return;
        }

        if (currentHandler == null) {
            if (categoryCursor >= plans.size()) {
                finishSuccess();
                return;
            }
            int planIndex = categoryCursor;
            beginCategory(plans.get(planIndex), planIndex);
            categoryCursor++;
            return;
        }

        if (recipeCursor >= currentHandler.numRecipes()) {
            tcnaAspectCostNormalizer.endCategory();
            closeCurrentCategory();
            return;
        }
        exportRecipe(recipeCursor++);
    }

    private void beginCategory(HandlerCategoryPlan plan, int planIndex) throws Exception {
        currentPlan = plan;
        currentHandler = HandlerCategoryPlan.loadCompleteCategoryWithFailureAudit(
                plans, planIndex);
        tcnaAspectCostNormalizer.beginCategory(plan, currentHandler);
        int recipeCount = currentHandler.numRecipes();
        String title = Naming.plainText(currentHandler.getRecipeTabName());
        if (title == null || title.trim().isEmpty()) {
            title = Naming.plainText(currentHandler.getRecipeName());
        }
        if (title == null || title.trim().isEmpty()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", plan.handlerId + " returned a blank recipe title");
        }
        String directory = "recipes/" + Naming.sanitize(plan.categoryId);
        currentMeta = new ExportContext.CategoryMeta(plan.categoryId, title, directory);
        currentCategoryIndex = context.categories.size();
        context.categories.add(currentMeta);
        currentRecipesWriter = ExportContext.jsonWriter(
                context.root.resolve(directory).resolve("recipes.json"));
        currentRecipesWriter.beginArray();
        recipeCursor = 0;

        populateCategoryIconAndCatalysts(currentHandler, currentMeta);
        context.loadedCategories++;
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Category {}/{} {} rawHandlerId={} via '{}' "
                        + "recipes={} catalysts={}",
                planIndex + 1, plans.size(), plan.categoryId, plan.handlerId,
                plan.loadIdentifier,
                recipeCount, currentMeta.catalysts.size());
    }

    private void populateCategoryIconAndCatalysts(ICraftingHandler handler,
                                                   ExportContext.CategoryMeta meta) throws Exception {
        HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
        if (info == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", currentPlan.handlerId
                    + " has no NEI HandlerInfo");
        }
        ItemStack iconStack = info.getItemStack();
        if (iconStack != null) {
            meta.icon = resolveGraphAlternative(
                    iconStack, "category icon", -1, 0, 0, null).entry.icon;
        } else {
            DrawableResource image = info.getImage();
            if (image != null) {
                meta.icon = renderHandlerIcon(image, meta.directory);
            }
        }

        List<PositionedStack> catalysts;
        try {
            catalysts = RecipeCatalysts.getRecipeCatalysts(handler);
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("RECIPE_SEMANTICS", "category catalysts for "
                    + currentPlan.handlerId, error);
        }
        if (catalysts == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", "NEI returned null category catalysts for "
                    + currentPlan.handlerId);
        }
        LinkedHashSet<String> keys = new LinkedHashSet<String>();
        for (int catalystIndex = 0; catalystIndex < catalysts.size(); catalystIndex++) {
            PositionedStack catalyst = catalysts.get(catalystIndex);
            if (UnregisteredGregTechMachineCatalystPolicy.shouldExclude(
                    currentPlan, catalyst, catalystIndex)) {
                context.excludedUnregisteredGregTechMachineCatalysts++;
                GtnhNeiExportMod.LOGGER.warn(
                        "[gtnh-nei-export] Explicitly excluded exact non-addressable GregTech "
                                + "machine catalyst; contract={} categoryId={} handlerId={} "
                                + "catalystIndex={} key={}",
                        UnregisteredGregTechMachineCatalystPolicy.CONTRACT,
                        currentPlan.categoryId,
                        currentPlan.handlerId,
                        catalystIndex,
                        UnregisteredGregTechMachineCatalystPolicy.CANONICAL_KEY);
                continue;
            }
            SlotData slot = convertStack(
                    catalyst, "category catalyst", -1, catalystIndex);
            for (IngredientPair alternative : slot.alternatives) {
                if (keys.add(alternative.key)) {
                    meta.catalysts.add(alternative.key);
                }
                if (meta.icon == null) {
                    meta.icon = context.catalog.ensure(findAlternative(catalyst, alternative.key)).icon;
                }
            }
        }
    }

    private String renderHandlerIcon(final DrawableResource drawable, String directory) throws Exception {
        final int width = drawable.getWidth();
        final int height = drawable.getHeight();
        if (width <= 0 || height <= 0) {
            throw new ExportFailure("ITEM_ICON_RENDER", currentPlan.handlerId
                    + " handler icon has invalid dimensions " + width + "x" + height);
        }
        BufferedImage image = context.renderer.render(width, height, 0x00000000,
                new OffscreenRenderer.DrawCall() {
                    @Override
                    public void draw() {
                        drawable.draw(0, 0);
                    }
                });
        String unusable = RenderedImageValidation.unusableReason(image);
        if (unusable != null) {
            throw new ExportFailure("ITEM_ICON_RENDER", currentPlan.handlerId
                    + " handler icon: " + unusable);
        }
        String relative = directory + "/icon.png";
        context.submitImage(image, context.root.resolve(relative));
        return relative;
    }

    private static ItemStack findAlternative(PositionedStack positioned, String key) {
        if (positioned.items != null) {
            for (ItemStack stack : positioned.items) {
                if (stack != null && StackIdentity.of(stack).key.equals(key)) {
                    return stack;
                }
            }
        }
        throw new IllegalStateException("Converted alternative disappeared from PositionedStack: " + key);
    }

    private void exportRecipe(int sourceIndex) throws Exception {
        if (ae2InternalFacadeRecipe.verifyAndConsumeIfExact(
                currentPlan, currentHandler, sourceIndex)) {
            context.excludedAe2EnderIoInternalConduitFacadeRows++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Explicitly excluded exact AE2 facade row for "
                            + "Ender IO's owner-internal conduit render block; contract={} "
                            + "categoryId={} sourceIndex={} materialKey={}",
                    Ae2InternalFacadeRecipePreflight.CONTRACT,
                    currentPlan.categoryId, sourceIndex,
                    Ae2InternalFacadeRecipePreflight.INTERNAL_BLOCK_KEY);
            return;
        }
        CatalogExcludedFuelPreflight.Expected excludedFuelRow =
                catalogExcludedFuelRows.verifyAndConsumeIfExact(
                        currentPlan, currentHandler, sourceIndex);
        if (excludedFuelRow != null) {
            context.excludedOwnerInternalFurnaceFuelRows++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Explicitly excluded exact owner-internal synthetic "
                            + "furnace-fuel row; contract={} categoryId={} sourceIndex={} "
                            + "policy={} registryId={}",
                    CatalogExcludedFuelPreflight.CONTRACT,
                    currentPlan.categoryId, sourceIndex,
                    excludedFuelRow.policy.contract,
                    excludedFuelRow.policy.registryId);
            return;
        }
        GregTechOutputlessSemanticPreflight.GraphIdentityDecision graphExclusion =
                gregTechOutputlessSemantics.lookupGraphIdentityExclusion(
                        currentPlan.categoryId, sourceIndex);
        if (graphExclusion != null) {
            graphExclusion = gregTechOutputlessSemantics.verifyGraphIdentityExclusion(
                    currentPlan, currentHandler, sourceIndex);
            context.excludedGregTechUnregisteredDoorRecyclingRows++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Explicitly excluded exact non-addressable GregTech "
                            + "ItemDoor recycling row; contract={} categoryId={} "
                            + "sourceIndex={} map={} doorKind={} fingerprint={}",
                    GregTechOutputlessSemanticPreflight
                            .UNREGISTERED_DOOR_RECYCLING_CONTRACT,
                    graphExclusion.categoryId, graphExclusion.sourceIndex,
                    graphExclusion.mapName, graphExclusion.doorKind,
                    graphExclusion.fingerprint);
            return;
        }
        GregTechOutputlessSemanticPreflight.Decision outputlessDecision =
                gregTechOutputlessSemantics.lookup(currentPlan.categoryId, sourceIndex);
        if (outputlessDecision != null) {
            outputlessDecision = gregTechOutputlessSemantics.verify(
                    currentPlan, currentHandler, sourceIndex);
            if (outputlessDecision.excludedFromExport()) {
                GtnhNeiExportMod.LOGGER.warn(
                        "[gtnh-nei-export] Explicitly excluded pinned presentation-only "
                                + "GregTech row categoryId={} sourceIndex={} classification={} "
                                + "fingerprint={}",
                        currentPlan.categoryId, sourceIndex,
                        outputlessDecision.classification,
                        outputlessDecision.fingerprint);
                return;
            }
        }
        RecipeData data = collectSemantics(sourceIndex, outputlessDecision);
        context.recipesEnumerated++;
        renderRecipe(sourceIndex, data);
        writeRecipe(sourceIndex, data);
        int exportedIndex = currentMeta.count;
        for (String key : data.usedKeys) {
            context.index(key, false, currentCategoryIndex, exportedIndex);
        }
        for (String key : data.outputKeys) {
            context.index(key, true, currentCategoryIndex, exportedIndex);
        }
        if (currentMeta.icon == null && data.firstIcon != null) {
            currentMeta.icon = data.firstIcon;
        }
        currentMeta.count++;
        context.recipeWidgetsRendered++;
        if (context.recipesEnumerated % 1000 == 0) {
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Recipes {}; category {} {}/{}; PNG pending={}",
                    context.recipesEnumerated, currentPlan.handlerId, recipeCursor,
                    currentHandler.numRecipes(), context.pngWriter.pending());
        }
    }

    private RecipeData collectSemantics(
            int sourceIndex,
            GregTechOutputlessSemanticPreflight.Decision outputlessDecision)
            throws Exception {
        CompleteCategoryAdapters.RecipeSemanticOverride semanticOverride =
                CompleteCategoryAdapters.semanticOverride(
                        currentPlan.adapter, currentHandler, sourceIndex);
        if (semanticOverride != null) {
            return collectSemanticOverride(sourceIndex, semanticOverride);
        }
        RecipeData data = new RecipeData();
        final List<PositionedStack> ingredients;
        final List<PositionedStack> others;
        final PositionedStack result;
        try {
            ingredients = currentHandler.getIngredientStacks(sourceIndex);
            result = currentHandler.getResultStack(sourceIndex);
            others = currentHandler.getOtherStacks(sourceIndex);
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #" + sourceIndex, error);
        }
        if (ingredients == null || others == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #" + sourceIndex
                    + " returned null ingredient/other stack lists");
        }
        for (int inputIndex = 0; inputIndex < ingredients.size(); inputIndex++) {
            SlotData slot = convertStack(
                    ingredients.get(inputIndex), "input", sourceIndex, inputIndex);
            if (slot != null) {
                data.inputs.add(slot);
                addKeys(data.usedKeys, slot);
            }
        }
        if (result != null) {
            SlotData resultSlot = convertStack(result, "result", sourceIndex, 0);
            data.outputs.add(resultSlot);
            addKeys(data.outputKeys, resultSlot);
            data.firstIcon = firstIcon(resultSlot);
            for (int catalystIndex = 0; catalystIndex < others.size(); catalystIndex++) {
                SlotData catalyst = convertStack(
                        others.get(catalystIndex), "catalyst", sourceIndex,
                        catalystIndex);
                data.catalysts.add(catalyst);
                addKeys(data.usedKeys, catalyst);
            }
        } else {
            for (int outputIndex = 0; outputIndex < others.size(); outputIndex++) {
                SlotData output = convertStack(
                        others.get(outputIndex), "output", sourceIndex, outputIndex);
                data.outputs.add(output);
                addKeys(data.outputKeys, output);
                if (data.firstIcon == null) {
                    data.firstIcon = firstIcon(output);
                }
            }
        }
        if (data.outputs.isEmpty() && !currentPlan.allowsInformationalEmptyOutputs) {
            if (outputlessDecision == null) {
                outputlessDecision = gregTechOutputlessSemantics.verify(
                        currentPlan, currentHandler, sourceIndex);
            }
            if (outputlessDecision == null || outputlessDecision.excludedFromExport()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        currentPlan.handlerId + " #" + sourceIndex
                                + " has no output under the pinned NEI result/other-stack "
                                + "semantics and no accepted GregTech outputless classification");
            }
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Accepted pinned GregTech outputless semantic row "
                            + "categoryId={} sourceIndex={} classification={} fingerprint={}",
                    currentPlan.categoryId, sourceIndex,
                    outputlessDecision.classification, outputlessDecision.fingerprint);
        }
        if (data.outputs.isEmpty()) {
            if (data.inputs.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #" + sourceIndex
                        + " has neither inputs nor outputs under its informational-page policy");
            }
            if (currentPlan.allowsInformationalEmptyOutputs) {
                context.informationalEmptyOutputRecipes++;
            }
        }
        return data;
    }

    private RecipeData collectSemanticOverride(
            int sourceIndex,
            CompleteCategoryAdapters.RecipeSemanticOverride semanticOverride)
            throws Exception {
        RecipeData data = new RecipeData();
        for (int inputIndex = 0;
             inputIndex < semanticOverride.inputs.size(); inputIndex++) {
            SlotData slot = convertSemanticSlot(
                    semanticOverride.inputs.get(inputIndex), "input", sourceIndex,
                    inputIndex, semanticOverride.semanticId);
            data.inputs.add(slot);
            addKeys(data.usedKeys, slot);
        }
        for (int outputIndex = 0;
             outputIndex < semanticOverride.outputs.size(); outputIndex++) {
            SlotData slot = convertSemanticSlot(
                    semanticOverride.outputs.get(outputIndex), "output", sourceIndex,
                    outputIndex, semanticOverride.semanticId);
            data.outputs.add(slot);
            addKeys(data.outputKeys, slot);
            if (data.firstIcon == null) {
                data.firstIcon = firstIcon(slot);
            }
        }
        for (int catalystIndex = 0;
             catalystIndex < semanticOverride.catalysts.size(); catalystIndex++) {
            SlotData slot = convertSemanticSlot(
                    semanticOverride.catalysts.get(catalystIndex), "catalyst",
                    sourceIndex, catalystIndex, semanticOverride.semanticId);
            data.catalysts.add(slot);
            addKeys(data.usedKeys, slot);
        }
        if (data.outputs.isEmpty()) {
            if (!currentPlan.allowsInformationalEmptyOutputs || data.inputs.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #"
                        + sourceIndex + " semantic=" + semanticOverride.semanticId
                        + " violates its pinned informational-page policy");
            }
            context.informationalEmptyOutputRecipes++;
        }
        return data;
    }

    private SlotData convertSemanticSlot(
            CompleteCategoryAdapters.SemanticSlot semantic, String role,
            int sourceIndex, int slotIndex, String semanticId) throws Exception {
        if (semantic == null || semantic.alternatives.isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #"
                    + sourceIndex + " semantic=" + semanticId + " has no " + role
                    + " alternatives in its full semantic override");
        }
        SlotData slot = new SlotData();
        if (semantic.probability != null) {
            if ("catalyst".equals(role)) {
                throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #"
                        + sourceIndex + " semantic=" + semanticId
                        + " assigns consumption probability to a catalyst slot");
            }
            slot.probability = semantic.probability;
        }
        for (int alternativeIndex = 0;
             alternativeIndex < semantic.alternatives.size(); alternativeIndex++) {
            CompleteCategoryAdapters.SemanticAlternative alternative =
                    semantic.alternatives.get(alternativeIndex);
            if (alternative == null || alternative.stack == null
                    || alternative.stack.getItem() == null) {
                throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #"
                        + sourceIndex + " semantic=" + semanticId + " has a null " + role
                        + " alternative #" + alternativeIndex);
            }
            if (alternative.amount < 0) {
                throw new ExportFailure("QUANTITY_INVALID", currentPlan.handlerId + " #"
                        + sourceIndex + " semantic=" + semanticId + " " + role
                        + " alternative #" + alternativeIndex + " has amount "
                        + alternative.amount);
            }
            rejectMalisisDoorsUnconfiguredCarrierGraphReference(
                    alternative.stack, role, sourceIndex, alternativeIndex, semanticId);
            StackIdentity identity = canonicalizeGraphStack(
                    alternative.stack, role, sourceIndex, slotIndex,
                    alternativeIndex, semanticId);
            if (identity.amount != alternative.amount) {
                throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #"
                        + sourceIndex + " semantic=" + semanticId + " " + role
                        + " alternative #" + alternativeIndex
                        + " changed stack amount after the full semantic snapshot; expected "
                        + alternative.amount + ", got " + identity.amount);
            }
            String currentCanonical = CompleteCategoryAdapters.canonicalStackIdentity(
                    identity, alternative.amount);
            if (!alternative.canonicalIdentity.equals(currentCanonical)) {
                throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #"
                        + sourceIndex + " semantic=" + semanticId + " " + role
                        + " alternative #" + alternativeIndex
                        + " changed after the full semantic snapshot");
            }
            ResolvedGraphAlternative resolved = resolveGraphAlternative(
                    alternative.stack, role, sourceIndex, slotIndex,
                    alternativeIndex, semanticId);
            ItemCatalog.Entry item = resolved.entry;
            slot.alternatives.add(new IngredientPair(
                    item.identity.key, alternative.amount, item.icon));
        }
        return slot;
    }

    private SlotData convertStack(
            PositionedStack positioned, String role, int sourceIndex, int slotIndex)
            throws Exception {
        if (positioned == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #" + sourceIndex
                    + " has null " + role + " PositionedStack");
        }
        if (positioned.items == null || positioned.items.length == 0) {
            try {
                positioned.generatePermutations();
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #" + sourceIndex
                        + " could not generate " + role + " alternatives", error);
            }
        }
        if (positioned.items == null || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #" + sourceIndex
                    + " has no " + role + " alternatives");
        }
        SlotData slot = new SlotData();
        for (int alternativeIndex = 0; alternativeIndex < positioned.items.length; alternativeIndex++) {
            ItemStack alternative = positioned.items[alternativeIndex];
            if (alternative == null || alternative.getItem() == null) {
                throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #" + sourceIndex
                        + " has null " + role + " alternative #" + alternativeIndex);
            }
            ResolvedGraphAlternative resolved = resolveGraphAlternative(
                    alternative, role, sourceIndex, slotIndex, alternativeIndex, null);
            if (resolved == null) {
                continue;
            }
            StackIdentity identity = resolved.identity;
            int amount = identity.amount;
            if (amount < 0) {
                throw new ExportFailure("QUANTITY_INVALID", currentPlan.handlerId + " #" + sourceIndex
                        + " " + role + " alternative #" + alternativeIndex + " has amount " + amount);
            }
            ItemCatalog.Entry item = resolved.entry;
            slot.alternatives.add(new IngredientPair(item.identity.key, amount, item.icon));
        }
        if (slot.alternatives.isEmpty()) {
            if (!"input".equals(role)) {
                throw new ExportFailure("RECIPE_SEMANTICS", currentPlan.handlerId + " #"
                        + sourceIndex + " excluded every " + role + " alternative in slot #"
                        + slotIndex);
            }
            return null;
        }
        return slot;
    }

    private static final class ResolvedGraphAlternative {
        final StackIdentity identity;
        final ItemCatalog.Entry entry;

        ResolvedGraphAlternative(StackIdentity identity, ItemCatalog.Entry entry) {
            this.identity = identity;
            this.entry = entry;
        }
    }

    private ResolvedGraphAlternative resolveGraphAlternative(
            ItemStack stack, String role, int sourceIndex, int slotIndex,
            int alternativeIndex, String semanticId) throws Exception {
        rejectMalisisDoorsUnconfiguredCarrierGraphReference(
                stack, role, sourceIndex, alternativeIndex, semanticId);
        String handlerClass = currentHandler == null
                ? null : currentHandler.getClass().getName();
        TcnaAspectCostSemanticNormalizer.Result normalization =
                tcnaAspectCostNormalizer.normalize(
                        stack, handlerClass, role, sourceIndex, slotIndex,
                        alternativeIndex);
        if (normalization.excluded) {
            return null;
        }
        StackIdentity identity = normalization.normalized
                ? normalization.identity : canonicalizeGraphStack(
                        stack, role, sourceIndex, slotIndex,
                        alternativeIndex, semanticId);
        GregTechForestryScannedSaplingPreflight.DisplayNameAuthorization
                forestryScannedSaplingAuthorization =
                gregTechForestryScannedSapling.authorizeIfExact(
                        currentPlan, currentHandler, sourceIndex, role, slotIndex,
                        alternativeIndex, identity);
        GregTechForestryScannedPollenPreflight.DisplayNameAuthorization
                forestryScannedPollenAuthorization =
                gregTechForestryScannedPollen.authorizeIfExact(
                        currentPlan, currentHandler, sourceIndex, role, slotIndex,
                        alternativeIndex, identity);
        if (normalization.normalized && (forestryScannedSaplingAuthorization != null
                || forestryScannedPollenAuthorization != null)) {
            throw new ExportFailure(
                    "ITEM_IDENTITY",
                    "Forestry scanner source-bound display-name policy cannot authorize a "
                            + "semantically normalized graph identity");
        }
        ItemCatalog.Entry entry = normalization.normalized
                ? context.catalog.requireExisting(identity)
                : context.catalog.ensure(
                        identity,
                        forestryScannedSaplingAuthorization,
                        forestryScannedPollenAuthorization);
        if (forestryScannedSaplingAuthorization != null) {
            context.gregTechForestryScannedSaplingRecipeOccurrences++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Consumed exact GregTech/Forestry Scanned Sapling "
                            + "recipe occurrence; contract={} categoryId={} sourceIndex={} "
                            + "role={} slotIndex={} alternativeIndex={} key={}",
                    GregTechForestryScannedSaplingPreflight.CONTRACT,
                    currentPlan.categoryId, sourceIndex, role, slotIndex,
                    alternativeIndex, identity.key);
        }
        if (forestryScannedPollenAuthorization != null) {
            context.gregTechForestryScannedPollenRecipeOccurrences++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Consumed exact GregTech/Forestry Scanned Pollen "
                            + "recipe occurrence; contract={} categoryId={} sourceIndex={} "
                            + "role={} slotIndex={} alternativeIndex={} key={}",
                    GregTechForestryScannedPollenPreflight.CONTRACT,
                    currentPlan.categoryId, sourceIndex, role, slotIndex,
                    alternativeIndex, identity.key);
        }
        context.normalizedTcnaAspectCostInputOccurrences =
                tcnaAspectCostNormalizer.normalizedReferences();
        context.normalizedTcnaAspectCostDistinctKeys =
                tcnaAspectCostNormalizer.normalizedDistinctKeys();
        context.normalizedTcnaAspectCostHandlerCategories =
                tcnaAspectCostNormalizer.completedHandlerCategories();
        return new ResolvedGraphAlternative(identity, entry);
    }

    private StackIdentity canonicalizeGraphStack(
            ItemStack stack, String role, int sourceIndex, int slotIndex,
            int alternativeIndex, String semanticId) throws ExportFailure {
        try {
            return StackIdentity.of(stack);
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw graphAlternativeIdentityFailure(
                    currentPlan == null ? null : currentPlan.categoryId,
                    currentMeta == null ? null : currentMeta.title,
                    currentPlan == null ? null : currentPlan.handlerId,
                    currentHandler == null ? null : currentHandler.getClass().getName(),
                    sourceIndex, role, slotIndex, alternativeIndex, semanticId,
                    stack, error);
        }
    }

    static ExportFailure graphAlternativeIdentityFailure(
            String categoryId, String categoryTitle, String handlerId,
            String handlerClass, int sourceIndex, String role, int slotIndex,
            int alternativeIndex, String semanticId, ItemStack stack,
            Throwable cause) {
        String causeMessage = cause == null ? null : Naming.plainText(cause.getMessage());
        String causeType = cause == null ? null : cause.getClass().getName();
        return new ExportFailure("ITEM_IDENTITY",
                "recipe graph alternative canonicalization failed; categoryId="
                        + diagnosticValue(categoryId) + "; categoryTitle="
                        + diagnosticValue(categoryTitle) + "; handlerId="
                        + diagnosticValue(handlerId) + "; handlerClass="
                        + diagnosticValue(handlerClass) + "; sourceIndex=" + sourceIndex
                        + "; role=" + diagnosticValue(role) + "; slotIndex=" + slotIndex
                        + "; alternativeIndex=" + alternativeIndex + "; semanticId="
                        + diagnosticValue(semanticId) + "; stack={"
                        + StackIdentity.describe(stack) + "}; causeType="
                        + diagnosticValue(causeType) + "; causeMessage="
                        + diagnosticValue(causeMessage),
                cause);
    }

    private static String diagnosticValue(String value) {
        String plain = Naming.plainText(value);
        return plain == null || plain.trim().isEmpty() ? "<none>" : plain;
    }

    private void rejectMalisisDoorsUnconfiguredCarrierGraphReference(
            ItemStack stack, String role, int sourceIndex, int alternativeIndex,
            String semanticId) throws ExportFailure {
        boolean customDoor =
                StackIdentity.isExactMalisisDoorsUnconfiguredCustomDoorCarrier(stack);
        boolean mixedBlock =
                StackIdentity.isExactMalisisDoorsUnconfiguredMixedBlockCarrier(stack);
        if (!customDoor && !mixedBlock) {
            return;
        }
        boolean questReference = currentPlan != null
                && currentPlan.adapter == CompleteCategoryAdapters.Adapter.BETTER_QUESTING;
        if (customDoor && questReference) {
            context.malisisDoorsUnconfiguredCustomDoorQuestReferences++;
        } else if (customDoor) {
            context.malisisDoorsUnconfiguredCustomDoorRecipeReferences++;
        } else if (questReference) {
            context.malisisDoorsUnconfiguredMixedBlockQuestReferences++;
        } else {
            context.malisisDoorsUnconfiguredMixedBlockRecipeReferences++;
        }
        String graph = questReference ? "quest" : "recipe";
        String semantic = semanticId == null ? "" : "; semantic=" + semanticId;
        String carrier = customDoor ? "custom-door" : "mixed-block";
        String registryId = customDoor
                ? "malisisdoors:item.custom_door" : "malisisdoors:mixed_block";
        GtnhNeiExportMod.LOGGER.error(
                "[gtnh-nei-export] Rejecting exact bare MalisisDoors {} carrier "
                        + "during {} graph traversal: handler={}, sourceIndex={}, role={}, "
                        + "alternativeIndex={}{} {}",
                carrier, graph, currentPlan == null ? "<none>" : currentPlan.handlerId,
                sourceIndex, role, alternativeIndex, semantic, StackIdentity.describe(stack));
        throw new ExportFailure("ITEM_IDENTITY", "exact bare "
                + registryId + " appeared in post-discovery " + graph
                + " graph traversal; handler="
                + (currentPlan == null ? "<none>" : currentPlan.handlerId)
                + ", sourceIndex=" + sourceIndex + ", role=" + role
                + ", alternativeIndex=" + alternativeIndex + semantic);
    }

    private String firstIcon(SlotData slot) throws ExportFailure {
        if (slot.alternatives.isEmpty()) {
            return null;
        }
        return slot.alternatives.get(0).icon;
    }

    private static void addKeys(Set<String> target, SlotData slot) {
        for (IngredientPair alternative : slot.alternatives) {
            target.add(alternative.key);
        }
    }

    private void renderRecipe(final int sourceIndex, RecipeData data) throws Exception {
        final boolean outputsBotaniaCocoon =
                data.outputKeys.contains(StackIdentity.BOTANIA_COCOON_CANONICAL_KEY);
        final boolean outputsBotaniaPrism =
                data.outputKeys.contains(StackIdentity.BOTANIA_PRISM_CANONICAL_KEY);
        final boolean outputsGalacticraftFlag =
                data.outputKeys.contains(StackIdentity.GALACTICRAFT_FLAG_CANONICAL_KEY);
        final boolean usesWrcbeTriangulator =
                data.outputKeys.contains(StackIdentity.WRCBE_TRIANGULATOR_CANONICAL_KEY)
                || data.outputKeys.contains(
                        StackIdentity.WRCBE_TRIANGULATOR_WILDCARD_CANONICAL_KEY)
                || data.usedKeys.contains(StackIdentity.WRCBE_TRIANGULATOR_CANONICAL_KEY)
                || data.usedKeys.contains(
                        StackIdentity.WRCBE_TRIANGULATOR_WILDCARD_CANONICAL_KEY);
        final boolean usesProjectBlueMalformedControlPanel =
                ProjectBlueControlPanelIconRenderer.containsPinnedCanonicalKey(
                        data.usedKeys)
                || ProjectBlueControlPanelIconRenderer.containsPinnedCanonicalKey(
                        data.outputKeys);
        final boolean usesMobsInfoInfernalDeterministicPreview =
                MobsInfoInfernalSemanticAdapter.HANDLER.equals(currentPlan.handlerId);
        final boolean usesMobsInfoDeterministicPreview =
                MobsInfoSemanticAdapter.HANDLER.equals(currentPlan.handlerId);
        final boolean usesMobsInfoVillagerDeterministicPreview =
                MobsInfoVillagerTradeSemanticAdapter.HANDLER.equals(currentPlan.handlerId);
        final boolean usesGalacticraftCircuitFabricatorStablePreview =
                GalacticraftCircuitFabricatorSemanticAdapter.HANDLER.equals(
                        currentPlan.handlerId);
        final BotaniaCocoonIconRenderer cocoonRenderer =
                context.catalog.requireBotaniaCocoonIconRenderer();
        final BotaniaPrismIconRenderer prismRenderer =
                context.catalog.requireBotaniaPrismIconRenderer();
        final GalacticraftFlagIconRenderer flagRenderer =
                context.catalog.requireGalacticraftFlagIconRenderer();
        context.catalog.requireWrcbeTriangulatorIconRenderer();
        final ProjectBlueControlPanelIconRenderer projectBlueRenderer =
                usesProjectBlueMalformedControlPanel
                        ? context.catalog.requireProjectBlueControlPanelIconRenderer()
                        : null;
        final long[] cocoonAdapterInvocations = new long[1];
        final BotaniaPrismIconRenderer.AdapterCounts[] renderItemAdapterCounts =
                new BotaniaPrismIconRenderer.AdapterCounts[1];
        final long[] flagAdapterInvocations = new long[1];
        final long[] projectBlueAdapterInvocations = new long[1];
        final int[] mobsInfoInfernalPreviewOutputs = new int[1];
        final int[] mobsInfoPreviewSlotIcons = new int[1];
        final long[] galacticraftCircuitPreviewDraws = new long[1];
        final long[] ic2FluidCannerScreenDraws = new long[1];
        final NEIRecipeWidget widget;
        try {
            widget = RecipeHandlerRef.of(currentHandler, sourceIndex).getRecipeWidget();
            widget.showAsWidget(true);
            widget.update();
            widget.setLocation(0, 0);
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                    + " could not construct the NEI widget", error);
        }
        final int logicalWidth = widget.w;
        final int logicalHeight = widget.h;
        if (logicalWidth <= 0 || logicalHeight <= 0) {
            throw new ExportFailure("RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                    + " has invalid widget dimensions " + logicalWidth + "x" + logicalHeight);
        }
        final int targetWidth = Math.multiplyExact(logicalWidth, ExportRequest.RECIPE_SCALE);
        final int targetHeight = Math.multiplyExact(logicalHeight, ExportRequest.RECIPE_SCALE);
        if (targetWidth > context.renderer.maxTextureSize() || targetHeight > context.renderer.maxTextureSize()) {
            throw new ExportFailure("RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                    + " widget at required scale 2 exceeds GL_MAX_TEXTURE_SIZE: "
                    + targetWidth + "x" + targetHeight);
        }
        final BufferedImage image;
        try {
            image = context.renderer.render(targetWidth, targetHeight, RECIPE_BACKGROUND_ARGB,
                    new OffscreenRenderer.DrawCall() {
                        @Override
                        public void draw() throws Exception {
                            OffscreenRenderer.DrawCall sharedAdapterDraw =
                                    new OffscreenRenderer.DrawCall() {
                                @Override
                                public void draw() throws Exception {
                                    flagAdapterInvocations[0] = flagRenderer.drawAndCount(
                                            new OffscreenRenderer.DrawCall() {
                                @Override
                                public void draw() throws Exception {
                                    renderItemAdapterCounts[0] = prismRenderer.drawAndCountAll(
                                            new OffscreenRenderer.DrawCall() {
                                        @Override
                                        public void draw() throws Exception {
                                            cocoonAdapterInvocations[0] =
                                                    cocoonRenderer.drawAndCount(
                                                            new OffscreenRenderer.DrawCall() {
                                                @Override
                                                public void draw() throws Exception {
                                                    GL11.glPushMatrix();
                                                    try {
                                                        GL11.glScalef(
                                                                ExportRequest.RECIPE_SCALE,
                                                                ExportRequest.RECIPE_SCALE, 1.0F);
                                                        if (usesMobsInfoDeterministicPreview) {
                                                            mobsInfoPreviewSlotIcons[0] =
                                                                    MobsInfoSemanticAdapter
                                                                            .drawDeterministicPreview(
                                                                                    currentHandler,
                                                                                    sourceIndex,
                                                                                    logicalWidth,
                                                                                    logicalHeight);
                                                        } else if (usesMobsInfoVillagerDeterministicPreview) {
                                                            MobsInfoVillagerTradeSemanticAdapter
                                                                    .drawDeterministicPreview(
                                                                            currentHandler,
                                                                            sourceIndex,
                                                                            logicalWidth,
                                                                            logicalHeight);
                                                        } else if (usesMobsInfoInfernalDeterministicPreview) {
                                                            mobsInfoInfernalPreviewOutputs[0] =
                                                                    MobsInfoInfernalSemanticAdapter
                                                                            .drawDeterministicPreview(
                                                                                    currentHandler,
                                                                                    sourceIndex,
                                                                                    logicalWidth,
                                                                                    logicalHeight);
                                                        } else if (usesGalacticraftCircuitFabricatorStablePreview) {
                                                            galacticraftCircuitPreviewDraws[0] =
                                                                    GalacticraftCircuitFabricatorSemanticAdapter
                                                                            .drawVisibleResult(
                                                                                    currentHandler,
                                                                                    sourceIndex,
                                                                                    new OffscreenRenderer.DrawCall() {
                                                                                        @Override
                                                                                        public void draw() {
                                                                                            widget.draw(-10000, -10000);
                                                                                        }
                                                                                    });
                                                        } else if (Ic2FluidCannerWidgetScreenAdapter
                                                                .matches(
                                                                        currentHandler,
                                                                        currentPlan.loadIdentifier)) {
                                                            Ic2FluidCannerWidgetScreenAdapter.draw(
                                                                    currentHandler,
                                                                    currentPlan.loadIdentifier,
                                                                    sourceIndex,
                                                                    new OffscreenRenderer.DrawCall() {
                                                                        @Override
                                                                        public void draw() {
                                                                            widget.draw(-10000, -10000);
                                                                        }
                                                                    });
                                                            ic2FluidCannerScreenDraws[0] = 1L;
                                                        } else {
                                                            widget.draw(-10000, -10000);
                                                        }
                                                    } finally {
                                                        GL11.glPopMatrix();
                                                    }
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                                }
                            };
                            if (projectBlueRenderer == null) {
                                sharedAdapterDraw.draw();
                            } else {
                                projectBlueAdapterInvocations[0] =
                                        projectBlueRenderer.drawAndCount(sharedAdapterDraw);
                            }
                        }
                    });
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex, error);
        }
        String unusable = RenderedImageValidation.unusableReason(image);
        if (unusable != null) {
            throw new ExportFailure("RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                    + ": " + unusable);
        }
        if (outputsBotaniaCocoon && cocoonAdapterInvocations[0] == 0L) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + " outputs the pinned Botania cocoon, but its NEI widget did not "
                            + "invoke the finite-time TESR adapter");
        }
        if (renderItemAdapterCounts[0] == null) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + " did not return shared RenderItem adapter telemetry");
        }
        if (Ic2FluidCannerWidgetScreenAdapter.matches(
                currentHandler, currentPlan.loadIdentifier)) {
            if (ic2FluidCannerScreenDraws[0] != 1L) {
                throw new ExportFailure(
                        "RECIPE_WIDGET_RENDER",
                        currentPlan.handlerId + " #" + sourceIndex
                                + " did not invoke the pinned IC2 fluid-canner screen adapter");
            }
            context.adaptedIc2FluidCannerRecipeWidgetRenderInvocations = Math.addExact(
                    context.adaptedIc2FluidCannerRecipeWidgetRenderInvocations,
                    ic2FluidCannerScreenDraws[0]);
        }
        final long prismAdapterInvocations =
                renderItemAdapterCounts[0].botaniaPrism;
        final long wrcbeAdapterInvocations =
                renderItemAdapterCounts[0].wrcbeTriangulator;
        if (outputsBotaniaPrism && prismAdapterInvocations == 0L) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + " outputs the pinned Botania prism, but its NEI widget did not "
                            + "invoke the base-level mip adapter");
        }
        if (outputsGalacticraftFlag && flagAdapterInvocations[0] == 0L) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + " outputs the pinned Galacticraft flag, but its NEI widget did not "
                            + "invoke the deterministic owner-renderer adapter");
        }
        if (usesWrcbeTriangulator && wrcbeAdapterInvocations == 0L) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + "uses the pinned WR-CBE triangulator, but its NEI widget "
                            + "did not invoke the owner slot-zero atlas refresh adapter");
        }
        if (usesProjectBlueMalformedControlPanel
                && projectBlueAdapterInvocations[0] != 1L) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + " uses one of the three pinned malformed ProjectBlue control "
                            + "panels, but its widget invoked the exact cached-face adapter "
                            + projectBlueAdapterInvocations[0] + " times instead of once");
        }
        if (usesMobsInfoInfernalDeterministicPreview
                && mobsInfoInfernalPreviewOutputs[0] != 58) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + " rendered " + mobsInfoInfernalPreviewOutputs[0]
                            + " deterministic preview outputs instead of 58");
        }
        context.adaptedMobsInfoInfernalPreviewOutputIcons = Math.addExact(
                context.adaptedMobsInfoInfernalPreviewOutputIcons,
                mobsInfoInfernalPreviewOutputs[0]);
        if (usesMobsInfoDeterministicPreview && mobsInfoPreviewSlotIcons[0] <= 1) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + " rendered an incomplete deterministic mob preview");
        }
        context.adaptedMobsInfoPreviewSlotIcons = Math.addExact(
                context.adaptedMobsInfoPreviewSlotIcons,
                mobsInfoPreviewSlotIcons[0]);
        if (usesGalacticraftCircuitFabricatorStablePreview
                && galacticraftCircuitPreviewDraws[0] != 1L) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + " did not render exactly one stable-result preview");
        }
        if (cocoonAdapterInvocations[0] > Integer.MAX_VALUE) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + " exceeded the Botania cocoon adapter invocation counter range");
        }
        context.adaptedBotaniaCocoonRecipeWidgetRenderInvocations = Math.addExact(
                context.adaptedBotaniaCocoonRecipeWidgetRenderInvocations,
                (int) cocoonAdapterInvocations[0]);
        if (prismAdapterInvocations > Integer.MAX_VALUE) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + " exceeded the Botania prism adapter invocation counter range");
        }
        context.adaptedBotaniaPrismRecipeWidgetRenderInvocations = Math.addExact(
                context.adaptedBotaniaPrismRecipeWidgetRenderInvocations,
                (int) prismAdapterInvocations);
        if (flagAdapterInvocations[0] > Integer.MAX_VALUE) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + " exceeded the Galacticraft flag adapter invocation counter range");
        }
        context.adaptedGalacticraftFlagRecipeWidgetRenderInvocations = Math.addExact(
                context.adaptedGalacticraftFlagRecipeWidgetRenderInvocations,
                (int) flagAdapterInvocations[0]);
        if (wrcbeAdapterInvocations > Integer.MAX_VALUE) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + " exceeded the WR-CBE triangulator adapter invocation counter "
                            + "range");
        }
        context.adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations = Math.addExact(
                context.adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations,
                (int) wrcbeAdapterInvocations);
        if (projectBlueAdapterInvocations[0] > Integer.MAX_VALUE) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER", currentPlan.handlerId + " #" + sourceIndex
                            + " exceeded the ProjectBlue adapter invocation counter range");
        }
        context.adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations = Math.addExact(
                context.adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations,
                (int) projectBlueAdapterInvocations[0]);
        String imageName = "r" + currentMeta.count + ".png";
        context.submitImage(image, context.root.resolve(currentMeta.directory).resolve(imageName));
        data.image = imageName;
        data.width = logicalWidth;
        data.height = logicalHeight;
    }

    private void writeRecipe(int sourceIndex, RecipeData data) throws IOException {
        currentRecipesWriter.beginObject();
        currentRecipesWriter.name("id").value(currentPlan.categoryId + "#" + sourceIndex);
        currentRecipesWriter.name("img").value(data.image);
        currentRecipesWriter.name("w").value(data.width);
        currentRecipesWriter.name("h").value(data.height);
        writeSlots("in", data.inputs);
        writeSlots("out", data.outputs);
        if (!data.catalysts.isEmpty()) {
            writeSlots("cat", data.catalysts);
        }
        currentRecipesWriter.endObject();
    }

    private void writeSlots(String name, List<SlotData> slots) throws IOException {
        currentRecipesWriter.name(name).beginArray();
        for (SlotData slot : slots) {
            currentRecipesWriter.beginArray();
            for (IngredientPair alternative : slot.alternatives) {
                currentRecipesWriter.beginArray()
                        .value(alternative.key)
                        .value(alternative.amount);
                if (slot.probability != null) {
                    // Field three is the optional logical ingredient identity. Preserve its
                    // tuple position explicitly so field four can carry exact transition
                    // probability for stochastic input consumption or output production.
                    currentRecipesWriter.nullValue().value(slot.probability.doubleValue());
                }
                currentRecipesWriter.endArray();
            }
            currentRecipesWriter.endArray();
        }
        currentRecipesWriter.endArray();
    }

    private void closeCurrentCategory() throws IOException {
        if (currentRecipesWriter != null) {
            currentRecipesWriter.endArray();
            currentRecipesWriter.close();
            currentRecipesWriter = null;
        }
        currentPlan = null;
        currentHandler = null;
        currentMeta = null;
        currentCategoryIndex = -1;
        recipeCursor = 0;
    }

    private void finishSuccess() throws Exception {
        closeCurrentCategory();
        GendustryMachineSemanticAdapter.applyDiagnostics(context);
        tcnaAspectCostNormalizer.verifyComplete();
        gregTechOutputlessSemantics.requireAllGraphIdentityExclusionsConsumed();
        gregTechForestryScannedSapling.requireConsumedExactlyOnce();
        gregTechForestryScannedPollen.requireConsumedExactlyOnce();
        catalogExcludedFuelRows.requireAllConsumed();
        ae2InternalFacadeRecipe.requireConsumedExactlyOnce();
        context.normalizedTcnaAspectCostInputOccurrences =
                tcnaAspectCostNormalizer.normalizedReferences();
        context.normalizedTcnaAspectCostDistinctKeys =
                tcnaAspectCostNormalizer.normalizedDistinctKeys();
        context.normalizedTcnaAspectCostHandlerCategories =
                tcnaAspectCostNormalizer.completedHandlerCategories();
        if (context.failureCount() != 0) {
            throw new ExportFailure("RECIPE_SEMANTICS", "refusing publication after "
                    + context.failureCount() + " recorded failure events");
        }
        try {
            StackIdentity.requireNoMalisisDoorsUnconfiguredCustomDoorGraphReferences(
                    context.malisisDoorsUnconfiguredCustomDoorRecipeReferences,
                    context.malisisDoorsUnconfiguredCustomDoorQuestReferences);
            StackIdentity.requireNoMalisisDoorsUnconfiguredMixedBlockGraphReferences(
                    context.malisisDoorsUnconfiguredMixedBlockRecipeReferences,
                    context.malisisDoorsUnconfiguredMixedBlockQuestReferences);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "MalisisDoors post-discovery recipe/quest graph invariant", error);
        }
        DraconicMobSoulIconRenderer.requireCompleteCoverage(
                context.adaptedDraconicMobSoulItemIcons,
                MobsInfoSemanticAdapter.requirePromotedCorpus()
                        .uniqueDraconicMobSoulIdentities);
        if (!context.itemListLoaded
                || context.itemListRawEntries != StackIdentity.PINNED_RAW_ITEM_LIST_COUNT
                || context.itemListExcludedEntries
                        != StackIdentity.PINNED_CATALOG_EXCLUSION_COUNT
                || context.itemListRetainedEntries
                        != StackIdentity.PINNED_RETAINED_ITEM_LIST_COUNT
                || context.itemListRetainedUniqueIdentities
                        != StackIdentity.PINNED_RETAINED_UNIQUE_ITEM_LIST_IDENTITY_COUNT
                || context.itemListRawEntries - context.itemListExcludedEntries
                        != context.itemListRetainedEntries
                || context.itemListRetainedEntries
                        - context.itemListRetainedUniqueIdentities != 1
                || itemListExclusionTelemetryTotal() != context.itemListExcludedEntries
                || context.catalog.count() < context.itemListRetainedUniqueIdentities
                || context.registeredCraftingHandlers != 330
                || context.exportableCraftingHandlers != 287
                || context.registeredCraftingHandlers
                != context.exportableCraftingHandlers + context.excludedNonRecipeHandlers
                        + context.excludedEmptyRecipeHandlers
                        + context.excludedUnboundTemplateRecipeHandlers
                || context.exportableCraftingHandlers != context.loadedCategories
                || context.adaptedHandlerCategories != 45
                || context.excludedNonRecipeHandlers != 20
                || context.excludedEmptyRecipeHandlers != 22
                || context.excludedUnboundTemplateRecipeHandlers != 1
                || context.excludedAe2fcFluidDropItemListPlaceholders != 1
                || context.excludedAe2fcFluidPacketItemListPlaceholders != 1
                || context.excludedBloodMagicBloodLightItemListHelpers != 1
                || context.excludedBloodMagicSpectralContainerItemListHelpers != 1
                || context.excludedArchitectureCraftCladdingItemListPlaceholders != 1
                || context.excludedAvaritiaEmptyMatterClusterItemListPlaceholders != 1
                || context.excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders != 1
                || context.excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries != 1
                || context.excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders != 1
                || context.excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders != 1
                || context.excludedAe2CableBusInternalBlockItemListEntries != 1
                || context.excludedAe2MatrixFrameInternalBlockItemListEntries != 1
                || context.excludedBotaniaBifrostItemListWorldStateEntries != 1
                || context.excludedBotaniaBuriedPetalsItemListWorldStateVariants != 16
                || context.excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask != 0xffff
                || context.excludedBotaniaCacophoniumBlockItemListWorldStateEntries != 1
                || context.excludedBotaniaEnchanterItemListWorldStateEntries != 1
                || context.excludedBotaniaFakeAirItemListWorldStateEntries != 1
                || context.excludedBotaniaManaFlameItemListWorldStateEntries != 1
                || context.excludedBotaniaSolidVineItemListWorldStateEntries != 1
                || context.excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders
                != 1
                || context.excludedCarpentersBedInternalBlockItemListEntries != 1
                || context.excludedCarpentersDoorInternalBlockItemListEntries != 1
                || context.excludedStevesCartsUnconfiguredModularCartItemListPlaceholders != 1
                || context.excludedTConstructBattleSignInternalBlockItemListEntries != 1
                || context.excludedTConstructHeldItemInternalBlockItemListEntries != 1
                || context.excludedThaumcraftBlockHoleInternalBlockItemListEntries != 1
                || context.excludedThaumcraftEldritchPortalInternalBlockItemListEntries != 1
                || context.excludedThaumicHorizonsBaseLightInternalBlockItemListEntries != 1
                || context.excludedThaumicHorizonsSolarLightInternalBlockItemListEntries != 1
                || context.excludedTwilightForestExperiment115InternalBlockItemListEntries != 1
                || context.excludedWitchingGadgetsCustomAirInternalBlockItemListEntries != 1
                || context.adaptedBotaniaCocoonItemIcons != 1
                || context.adaptedBotaniaCocoonRecipeWidgetRenderInvocations <= 0
                || context.adaptedBotaniaPrismItemIcons != 1
                || context.adaptedBotaniaPrismRecipeWidgetRenderInvocations <= 0
                || context.adaptedGalacticraftFlagItemIcons != 1
                || context.adaptedGalacticraftFlagRecipeWidgetRenderInvocations <= 0
                || context.adaptedWrcbeTriangulatorItemIcons != 1
                || context.adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations <= 0
                || context.adaptedIc2FluidCannerRecipeWidgetRenderInvocations != 5
                || context.adaptedModernMarkingsCrossingItemIcons
                != ModernMarkingsCrossingIconRenderer.EXPECTED_ITEM_ICONS
                || context.adaptedThaumcraftRunedStoneItemIcons
                != ThaumcraftRunedStoneIconRenderer.EXPECTED_ITEM_ICONS
                || context.adaptedForestryScannedSaplingDisplayNames
                != DisplayNameResolver.EXPECTED_FORESTRY_SCANNED_SAPLING_NAMES
                || context.gregTechForestryScannedSaplingRecipeOccurrences
                != GregTechForestryScannedSaplingPreflight.EXPECTED_RECIPE_OCCURRENCES
                || context.adaptedForestryScannedPollenDisplayNames
                != DisplayNameResolver.EXPECTED_FORESTRY_SCANNED_POLLEN_NAMES
                || context.gregTechForestryScannedPollenRecipeOccurrences
                != GregTechForestryScannedPollenPreflight.EXPECTED_RECIPE_OCCURRENCES
                || context.adaptedProjectBlueControlPanelItemIcons
                != ProjectBlueControlPanelIconRenderer.EXPECTED_TARGETS
                || context.adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations
                != ProjectBlueControlPanelIconRenderer.EXPECTED_TARGETS
                || context.adaptedBuildCraftPhasedFacadeItemIcons
                != BuildCraftPhasedFacadeIconRenderer.EXPECTED_ITEM_ICONS
                || context.adaptedMobsInfoInfernalPreviewOutputIcons != 58
                || context.adaptedMobsInfoPreviewSlotIcons != 6093
                || !GalacticraftCircuitFabricatorSemanticAdapter.completedContract()
                || context.normalizedTcnaAspectCostInputOccurrences <= 0
                || context.normalizedTcnaAspectCostDistinctKeys <= 0
                || context.normalizedTcnaAspectCostHandlerCategories
                != TcnaAspectCostSemanticNormalizer.EXPECTED_HANDLER_CATEGORIES
                || context.adaptedGendustryLiquifierRecipes
                != GendustryMachineSemanticAdapter.EXPECTED_LIQUIFIER_PAGES
                || context.adaptedGendustryMutagenProducerRecipes
                != GendustryMachineSemanticAdapter.EXPECTED_MUTAGEN_PRODUCER_PAGES
                || context.adaptedGendustryExtractorRecipes
                != GendustryMachineSemanticAdapter.EXPECTED_EXTRACTOR_PAGES
                || context.adaptedGendustryReplicatorRecipes
                != GendustryMachineSemanticAdapter.EXPECTED_REPLICATOR_PAGES
                || context.adaptedGendustryTransposerRecipes
                != GendustryMachineSemanticAdapter.EXPECTED_TRANSPOSER_PAGES
                || context.adaptedGendustryMutatronRecipes
                != GendustryMachineSemanticAdapter.EXPECTED_MUTATRON_PAGES
                || context.adaptedGendustrySamplerRecipes
                != GendustryMachineSemanticAdapter.EXPECTED_SAMPLER_PAGES
                || context.adaptedGendustryImprinterRecipes
                != GendustryMachineSemanticAdapter.EXPECTED_IMPRINTER_PAGES
                || context.informationalEmptyOutputRecipes != 513
                || context.gregTechFuelSinkRecipes
                != GregTechOutputlessSemanticPreflight.EXPECTED_FUEL_SINK_RECIPES
                || context.gregTechFuelSinkCategories
                != GregTechOutputlessSemanticPreflight.EXPECTED_FUEL_SINK_CATEGORIES
                || context.gregTechLargeBoilerFuelSinkRecipes
                != GregTechOutputlessSemanticPreflight.EXPECTED_LARGE_BOILER_FUEL_SINK_RECIPES
                || context.gregTechLargeBoilerFuelSinkCategories
                != GregTechOutputlessSemanticPreflight
                        .EXPECTED_LARGE_BOILER_FUEL_SINK_CATEGORIES
                || context.gregTechRadioHatchInformationRecipes
                != GregTechOutputlessSemanticPreflight
                        .EXPECTED_RADIO_HATCH_INFORMATION_RECIPES
                || context.gregTechQuantumComponentInformationRecipes
                != GregTechOutputlessSemanticPreflight
                        .EXPECTED_QUANTUM_COMPONENT_INFORMATION_RECIPES
                || context.gregTechSpaceProjectInformationRecipes
                != GregTechOutputlessSemanticPreflight
                        .EXPECTED_SPACE_PROJECT_INFORMATION_RECIPES
                || context.gregTechOutputlessSemanticCategories
                != GregTechOutputlessSemanticPreflight.EXPECTED_SEMANTIC_CATEGORIES
                || context.gregTechOutputlessSemanticRecipes
                != GregTechOutputlessSemanticPreflight.EXPECTED_SEMANTIC_RECIPES
                || context.excludedGregTechLargeBoilerPresentationRows
                != GregTechOutputlessSemanticPreflight
                        .EXPECTED_EXCLUDED_LARGE_BOILER_PRESENTATION_ROWS
                || context.excludedGregTechUnregisteredDoorRecyclingRows
                != GregTechOutputlessSemanticPreflight
                        .EXPECTED_UNREGISTERED_DOOR_RECYCLING_ROWS
                || context.excludedOwnerInternalFurnaceFuelRows
                != CatalogExcludedFuelPreflight.EXPECTED_EXCLUSIONS
                || context.excludedAe2EnderIoInternalConduitFacadeRows
                != Ae2InternalFacadeRecipePreflight.EXPECTED_EXCLUSIONS
                || context.excludedUnregisteredGregTechMachineCatalysts
                != UnregisteredGregTechMachineCatalystPolicy.EXPECTED_EXCLUSIONS
                || context.knowledgeIndependentAspectNames
                != TcnaAspectCostSemanticNormalizer.EXPECTED_CATALOG_ASPECT_IDENTITIES
                || context.loadedCategories != context.categories.size()
                || context.recipesEnumerated != context.recipeWidgetsRendered) {
            throw new ExportFailure("HANDLER_UNLOADED", "coverage invariant failed: itemListLoaded="
                    + context.itemListLoaded + ", registered="
                    + context.registeredCraftingHandlers + ", itemListRaw="
                    + context.itemListRawEntries + ", itemListExcluded="
                    + context.itemListExcludedEntries + ", itemListRetained="
                    + context.itemListRetainedEntries + ", itemListRetainedUnique="
                    + context.itemListRetainedUniqueIdentities + ", itemListExclusionSum="
                    + itemListExclusionTelemetryTotal() + ", unionCatalogItems="
                    + context.catalog.count() + ", exportable="
                    + context.exportableCraftingHandlers + ", adapted="
                    + context.adaptedHandlerCategories + ", excludedNonRecipe="
                    + context.excludedNonRecipeHandlers + ", loaded=" + context.loadedCategories
                    + ", excludedEmptyRecipe=" + context.excludedEmptyRecipeHandlers
                    + ", excludedUnboundTemplateRecipe="
                    + context.excludedUnboundTemplateRecipeHandlers
                    + ", excludedAe2fcFluidDropPlaceholder="
                    + context.excludedAe2fcFluidDropItemListPlaceholders
                    + ", excludedAe2fcFluidPacketPlaceholder="
                    + context.excludedAe2fcFluidPacketItemListPlaceholders
                    + ", excludedBloodMagicBloodLightHelper="
                    + context.excludedBloodMagicBloodLightItemListHelpers
                    + ", excludedBloodMagicSpectralContainerHelper="
                    + context.excludedBloodMagicSpectralContainerItemListHelpers
                    + ", excludedArchitectureCraftCladdingPlaceholder="
                    + context.excludedArchitectureCraftCladdingItemListPlaceholders
                    + ", excludedAvaritiaEmptyMatterClusterPlaceholder="
                    + context.excludedAvaritiaEmptyMatterClusterItemListPlaceholders
                    + ", excludedDreamcraftNothingLegacyLootBagSentinel="
                    + context.excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders
                    + ", excludedLittleTilesUnparameterizedMicrotileCarrier="
                    + context.excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries
                    + ", excludedMalisisDoorsUnconfiguredCustomDoor="
                    + context.excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders
                    + ", malisisDoorsRecipeReferences="
                    + context.malisisDoorsUnconfiguredCustomDoorRecipeReferences
                    + ", malisisDoorsQuestReferences="
                    + context.malisisDoorsUnconfiguredCustomDoorQuestReferences
                    + ", excludedMalisisDoorsUnconfiguredMixedBlock="
                    + context.excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders
                    + ", malisisDoorsMixedBlockRecipeReferences="
                    + context.malisisDoorsUnconfiguredMixedBlockRecipeReferences
                    + ", malisisDoorsMixedBlockQuestReferences="
                    + context.malisisDoorsUnconfiguredMixedBlockQuestReferences
                    + ", excludedAe2CableBusInternalBlock="
                    + context.excludedAe2CableBusInternalBlockItemListEntries
                    + ", excludedAe2MatrixFrameInternalBlock="
                    + context.excludedAe2MatrixFrameInternalBlockItemListEntries
                    + ", excludedBotaniaBifrostWorldState="
                    + context.excludedBotaniaBifrostItemListWorldStateEntries
                    + ", excludedBotaniaBuriedPetalsWorldStateVariants="
                    + context.excludedBotaniaBuriedPetalsItemListWorldStateVariants
                    + ", excludedBotaniaBuriedPetalsMetadataMask=0x"
                    + Integer.toHexString(
                            context.excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask)
                    + ", excludedBotaniaCacophoniumBlockWorldState="
                    + context.excludedBotaniaCacophoniumBlockItemListWorldStateEntries
                    + ", excludedBotaniaEnchanterWorldState="
                    + context.excludedBotaniaEnchanterItemListWorldStateEntries
                    + ", excludedBotaniaFakeAirWorldState="
                    + context.excludedBotaniaFakeAirItemListWorldStateEntries
                    + ", excludedBotaniaManaFlameWorldState="
                    + context.excludedBotaniaManaFlameItemListWorldStateEntries
                    + ", excludedBotaniaSolidVineWorldState="
                    + context.excludedBotaniaSolidVineItemListWorldStateEntries
                    + ", excludedBotaniaStructureLibAnyFlowerPlaceholder="
                    + context.excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders
                    + ", excludedCarpentersBedInternalBlock="
                    + context.excludedCarpentersBedInternalBlockItemListEntries
                    + ", excludedCarpentersDoorInternalBlock="
                    + context.excludedCarpentersDoorInternalBlockItemListEntries
                    + ", excludedStevesCartsUnconfiguredModularCartPlaceholder="
                    + context.excludedStevesCartsUnconfiguredModularCartItemListPlaceholders
                    + ", excludedTConstructBattleSignInternalBlock="
                    + context.excludedTConstructBattleSignInternalBlockItemListEntries
                    + ", excludedTConstructHeldItemInternalBlock="
                    + context.excludedTConstructHeldItemInternalBlockItemListEntries
                    + ", excludedThaumcraftBlockHoleInternalBlock="
                    + context.excludedThaumcraftBlockHoleInternalBlockItemListEntries
                    + ", excludedThaumcraftEldritchPortalInternalBlock="
                    + context.excludedThaumcraftEldritchPortalInternalBlockItemListEntries
                    + ", excludedThaumicHorizonsBaseLightInternalBlock="
                    + context.excludedThaumicHorizonsBaseLightInternalBlockItemListEntries
                    + ", excludedThaumicHorizonsSolarLightInternalBlock="
                    + context.excludedThaumicHorizonsSolarLightInternalBlockItemListEntries
                    + ", excludedTwilightForestExperiment115InternalBlock="
                    + context.excludedTwilightForestExperiment115InternalBlockItemListEntries
                    + ", excludedWitchingGadgetsCustomAirInternalBlock="
                    + context.excludedWitchingGadgetsCustomAirInternalBlockItemListEntries
                    + ", adaptedBotaniaCocoonItemIcons="
                    + context.adaptedBotaniaCocoonItemIcons
                    + ", adaptedBotaniaCocoonRecipeWidgetRenderInvocations="
                    + context.adaptedBotaniaCocoonRecipeWidgetRenderInvocations
                    + ", adaptedBotaniaPrismItemIcons="
                    + context.adaptedBotaniaPrismItemIcons
                    + ", adaptedBotaniaPrismRecipeWidgetRenderInvocations="
                    + context.adaptedBotaniaPrismRecipeWidgetRenderInvocations
                    + ", adaptedGalacticraftFlagItemIcons="
                    + context.adaptedGalacticraftFlagItemIcons
                    + ", adaptedGalacticraftFlagRecipeWidgetRenderInvocations="
                    + context.adaptedGalacticraftFlagRecipeWidgetRenderInvocations
                    + ", adaptedWrcbeTriangulatorItemIcons="
                    + context.adaptedWrcbeTriangulatorItemIcons
                    + ", adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations="
                    + context.adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations
                    + ", adaptedModernMarkingsCrossingItemIcons="
                    + context.adaptedModernMarkingsCrossingItemIcons
                    + ", adaptedThaumcraftRunedStoneItemIcons="
                    + context.adaptedThaumcraftRunedStoneItemIcons
                    + ", adaptedForestryScannedSaplingDisplayNames="
                    + context.adaptedForestryScannedSaplingDisplayNames
                    + ", gregTechForestryScannedSaplingRecipeOccurrences="
                    + context.gregTechForestryScannedSaplingRecipeOccurrences
                    + ", adaptedForestryScannedPollenDisplayNames="
                    + context.adaptedForestryScannedPollenDisplayNames
                    + ", gregTechForestryScannedPollenRecipeOccurrences="
                    + context.gregTechForestryScannedPollenRecipeOccurrences
                    + ", adaptedProjectBlueControlPanelItemIcons="
                    + context.adaptedProjectBlueControlPanelItemIcons
                    + ", adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations="
                    + context.adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations
                    + ", adaptedBuildCraftPhasedFacadeItemIcons="
                    + context.adaptedBuildCraftPhasedFacadeItemIcons
                    + ", adaptedIc2FluidCannerRecipeWidgetRenderInvocations="
                    + context.adaptedIc2FluidCannerRecipeWidgetRenderInvocations
                    + ", adaptedMobsInfoInfernalPreviewOutputIcons="
                    + context.adaptedMobsInfoInfernalPreviewOutputIcons
                    + ", adaptedMobsInfoPreviewSlotIcons="
                    + context.adaptedMobsInfoPreviewSlotIcons
                    + ", adaptedDraconicMobSoulItemIcons="
                    + context.adaptedDraconicMobSoulItemIcons
                    + ", adaptedGendustryLiquifierRecipes="
                    + context.adaptedGendustryLiquifierRecipes
                    + ", adaptedGendustryMutagenProducerRecipes="
                    + context.adaptedGendustryMutagenProducerRecipes
                    + ", adaptedGendustryExtractorRecipes="
                    + context.adaptedGendustryExtractorRecipes
                    + ", adaptedGendustryReplicatorRecipes="
                    + context.adaptedGendustryReplicatorRecipes
                    + ", adaptedGendustryTransposerRecipes="
                    + context.adaptedGendustryTransposerRecipes
                    + ", adaptedGendustryMutatronRecipes="
                    + context.adaptedGendustryMutatronRecipes
                    + ", adaptedGendustrySamplerRecipes="
                    + context.adaptedGendustrySamplerRecipes
                    + ", adaptedGendustryImprinterRecipes="
                    + context.adaptedGendustryImprinterRecipes
                    + ", normalizedTcnaAspectCostInputOccurrences="
                    + context.normalizedTcnaAspectCostInputOccurrences
                    + ", normalizedTcnaAspectCostDistinctKeys="
                    + context.normalizedTcnaAspectCostDistinctKeys
                    + ", normalizedTcnaAspectCostHandlerCategories="
                    + context.normalizedTcnaAspectCostHandlerCategories
                    + ", categories=" + context.categories.size() + ", recipes="
                    + context.recipesEnumerated + ", widgets=" + context.recipeWidgetsRendered
                    + ", informationalEmptyOutputs=" + context.informationalEmptyOutputRecipes
                    + ", gregTechFuelSinks=" + context.gregTechFuelSinkRecipes
                    + ", gregTechFuelSinkCategories=" + context.gregTechFuelSinkCategories
                    + ", gregTechLargeBoilerFuelSinks="
                    + context.gregTechLargeBoilerFuelSinkRecipes
                    + ", gregTechLargeBoilerFuelSinkCategories="
                    + context.gregTechLargeBoilerFuelSinkCategories
                    + ", gregTechRadioHatchInformation="
                    + context.gregTechRadioHatchInformationRecipes
                    + ", gregTechQuantumComponentInformation="
                    + context.gregTechQuantumComponentInformationRecipes
                    + ", gregTechSpaceProjectInformation="
                    + context.gregTechSpaceProjectInformationRecipes
                    + ", gregTechOutputlessSemanticCategories="
                    + context.gregTechOutputlessSemanticCategories
                    + ", gregTechOutputlessSemanticRecipes="
                    + context.gregTechOutputlessSemanticRecipes
                    + ", excludedGregTechLargeBoilerPresentationRows="
                    + context.excludedGregTechLargeBoilerPresentationRows
                    + ", excludedGregTechUnregisteredDoorRecyclingRows="
                    + context.excludedGregTechUnregisteredDoorRecyclingRows
                    + ", excludedOwnerInternalFurnaceFuelRows="
                    + context.excludedOwnerInternalFurnaceFuelRows
                    + ", excludedAe2EnderIoInternalConduitFacadeRows="
                    + context.excludedAe2EnderIoInternalConduitFacadeRows
                    + ", excludedUnregisteredGregTechMachineCatalysts="
                    + context.excludedUnregisteredGregTechMachineCatalysts
                    + ", knowledgeIndependentAspectNames="
                    + context.knowledgeIndependentAspectNames);
        }
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact AE2 CableBus internal multipart world-host "
                        + "ItemList exclusion: count={}; public cables, parts, and facades remain "
                        + "cataloged; later bare-host appearances remain fail-closed",
                context.excludedAe2CableBusInternalBlockItemListEntries);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact AE2 Matrix Frame internal spatial-storage "
                        + "world-substrate ItemList exclusion: count={}; later recipe appearances "
                        + "remain fail-closed",
                context.excludedAe2MatrixFrameInternalBlockItemListEntries);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact Dreamcraft Nothing orphaned legacy loot-bag "
                        + "empty-reward sentinel ItemList exclusion: count={}; same-item stack "
                        + "shape drift and later recipe appearances remain fail-closed",
                context.excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact LittleTiles unparameterized microtile-carrier "
                        + "ItemList exclusion: count={}; every NBT-parameterized microtile remains "
                        + "cataloged and later bare recipe appearances remain fail-closed",
                context.excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact MalisisDoors unconfigured custom-door carrier "
                        + "ItemList exclusion: count={}; every NBT-bearing configured door remains "
                        + "cataloged; verified post-discovery recipeRefs={} and questRefs={}",
                context.excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders,
                context.malisisDoorsUnconfiguredCustomDoorRecipeReferences,
                context.malisisDoorsUnconfiguredCustomDoorQuestReferences);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact MalisisDoors unconfigured mixed-block carrier "
                        + "ItemList exclusion: count={}; every four-key NBT-configured mixed block "
                        + "remains cataloged; verified post-discovery recipeRefs={} and "
                        + "questRefs={}",
                context.excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders,
                context.malisisDoorsUnconfiguredMixedBlockRecipeReferences,
                context.malisisDoorsUnconfiguredMixedBlockQuestReferences);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact Carpenter's Blocks internal multipart ItemList "
                        + "exclusions: bed={}, door={}; public placement items remain cataloged",
                context.excludedCarpentersBedInternalBlockItemListEntries,
                context.excludedCarpentersDoorInternalBlockItemListEntries);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact Steve's Carts unconfigured ModularCart "
                        + "global ItemList placeholder exclusion: count={}; configured NBT carts "
                        + "remain cataloged",
                context.excludedStevesCartsUnconfiguredModularCartItemListPlaceholders);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact TConstruct internal BattleSignBlock ItemList "
                        + "exclusion: count={}; public InfiTool-NBT battlesigns remain cataloged",
                context.excludedTConstructBattleSignInternalBlockItemListEntries);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact TConstruct internal HeldItemBlock ItemList "
                        + "exclusion: count={}; public InfiTool-NBT frying pans remain cataloged",
                context.excludedTConstructHeldItemInternalBlockItemListEntries);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact Thaumcraft internal blockHole ItemList "
                        + "exclusion: count={}; metadata-15 compound-diagram sentinel and public "
                        + "FocusPortableHole remain cataloged",
                context.excludedThaumcraftBlockHoleInternalBlockItemListEntries);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact Thaumcraft internal blockPortalEldritch "
                        + "ItemList exclusion: count={}; public ItemEldritchObject metadata 0 "
                        + "and distinct add-on portal items remain cataloged",
                context.excludedThaumcraftEldritchPortalInternalBlockItemListEntries);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact Thaumic Horizons internal light ItemList "
                        + "exclusions: base={}, solar={}; all 16 public focusIllumination color "
                        + "variants remain cataloged",
                context.excludedThaumicHorizonsBaseLightInternalBlockItemListEntries,
                context.excludedThaumicHorizonsSolarLightInternalBlockItemListEntries);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact Twilight Forest internal Experiment 115 "
                        + "ItemList exclusion: count={}; the distinct public food remains "
                        + "cataloged",
                context.excludedTwilightForestExperiment115InternalBlockItemListEntries);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact Witching Gadgets temporary-light ItemList "
                        + "exclusion: count={}; later recipe appearances remain fail-closed",
                context.excludedWitchingGadgetsCustomAirInternalBlockItemListEntries);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact Botania cocoon finite-time TESR policy: "
                        + "catalogIcons={}, recipeWidgetInvocations={}",
                context.adaptedBotaniaCocoonItemIcons,
                context.adaptedBotaniaCocoonRecipeWidgetRenderInvocations);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact Botania prism base-level mip policy: "
                        + "catalogIcons={}, recipeWidgetInvocations={}",
                context.adaptedBotaniaPrismItemIcons,
                context.adaptedBotaniaPrismRecipeWidgetRenderInvocations);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact Galacticraft flag deterministic owner-renderer "
                        + "policy: catalogIcons={}, recipeWidgetInvocations={}",
                context.adaptedGalacticraftFlagItemIcons,
                context.adaptedGalacticraftFlagRecipeWidgetRenderInvocations);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact WR-CBE triangulator owner slot-zero atlas "
                        + "refresh policy: catalogIcons={}, recipeWidgetInvocations={}",
                context.adaptedWrcbeTriangulatorItemIcons,
                context.adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact ModernMarkings four-corner owner-atlas "
                        + "face-on catalog policy: catalogIcons={}",
                context.adaptedModernMarkingsCrossingItemIcons);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact Thaumcraft Runed Stone metadata-10 "
                        + "owner-atlas face-on catalog policy: catalogIcons={}",
                context.adaptedThaumcraftRunedStoneItemIcons);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact GregTech/Forestry synthetic Scanned Sapling "
                        + "display-name policy: catalogNames={}, sourceBoundRecipeOccurrences={}",
                context.adaptedForestryScannedSaplingDisplayNames,
                context.gregTechForestryScannedSaplingRecipeOccurrences);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact GregTech/Forestry synthetic Scanned Pollen "
                        + "display-name policy: catalogNames={}, sourceBoundRecipeOccurrences={}",
                context.adaptedForestryScannedPollenDisplayNames,
                context.gregTechForestryScannedPollenRecipeOccurrences);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact ProjectBlue ForgeMultipart cached-face "
                        + "owner-renderer policy: catalogIcons={}, recipeWidgetInvocations={}",
                context.adaptedProjectBlueControlPanelItemIcons,
                context.adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact BuildCraft phased-facade primary-material "
                        + "catalog policy: catalogIcons={}",
                context.adaptedBuildCraftPhasedFacadeItemIcons);
        GtnhNeiExportMod.LOGGER.warn(
                "[gtnh-nei-export] Applied exact AE2/Ender IO internal facade recipe "
                        + "exclusion: rows={}",
                context.excludedAe2EnderIoInternalConduitFacadeRows);
        context.finishResources();
        runtimeIntegrityGate.verify();
        context.writeFinalMetadata(false, elapsedMillis());
        runtimeIntegrityGate.verify();
        ManifestContract.validatePublished(stagingOutput.resolve("manifest.json"));
        runtimeIntegrityGate.verify();
        TransactionalPublisher.publish(stagingOutput, finalOutput);
        complete = true;
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Export complete: items={}, recipes={}, categories={} -> {}",
                context.catalog.count(), context.recipesEnumerated, context.categories.size(), finalOutput);
    }

    private int itemListExclusionTelemetryTotal() {
        return context.excludedAe2fcFluidDropItemListPlaceholders
                + context.excludedAe2fcFluidPacketItemListPlaceholders
                + context.excludedAe2CableBusInternalBlockItemListEntries
                + context.excludedAe2MatrixFrameInternalBlockItemListEntries
                + context.excludedBloodMagicBloodLightItemListHelpers
                + context.excludedBloodMagicSpectralContainerItemListHelpers
                + context.excludedArchitectureCraftCladdingItemListPlaceholders
                + context.excludedAvaritiaEmptyMatterClusterItemListPlaceholders
                + context.excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders
                + context.excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries
                + context.excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders
                + context.excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders
                + context.excludedBotaniaBifrostItemListWorldStateEntries
                + context.excludedBotaniaBuriedPetalsItemListWorldStateVariants
                + context.excludedBotaniaCacophoniumBlockItemListWorldStateEntries
                + context.excludedBotaniaEnchanterItemListWorldStateEntries
                + context.excludedBotaniaFakeAirItemListWorldStateEntries
                + context.excludedBotaniaManaFlameItemListWorldStateEntries
                + context.excludedBotaniaSolidVineItemListWorldStateEntries
                + context.excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders
                + context.excludedCarpentersBedInternalBlockItemListEntries
                + context.excludedCarpentersDoorInternalBlockItemListEntries
                + context.excludedStevesCartsUnconfiguredModularCartItemListPlaceholders
                + context.excludedTConstructBattleSignInternalBlockItemListEntries
                + context.excludedTConstructHeldItemInternalBlockItemListEntries
                + context.excludedThaumcraftBlockHoleInternalBlockItemListEntries
                + context.excludedThaumcraftEldritchPortalInternalBlockItemListEntries
                + context.excludedThaumicHorizonsBaseLightInternalBlockItemListEntries
                + context.excludedThaumicHorizonsSolarLightInternalBlockItemListEntries
                + context.excludedTwilightForestExperiment115InternalBlockItemListEntries
                + context.excludedWitchingGadgetsCustomAirInternalBlockItemListEntries;
    }

    void abort(Throwable cause) {
        if (complete) {
            return;
        }
        failed = true;
        context.failure(cause);
        try {
            closeCurrentCategory();
        } catch (Throwable closeFailure) {
            FatalErrors.rethrowIfFatal(closeFailure);
            context.failure("RECIPE_SEMANTICS: closing failed category: " + closeFailure);
        }
        try {
            context.finishResources();
        } catch (Throwable resourceFailure) {
            FatalErrors.rethrowIfFatal(resourceFailure);
            context.failure(resourceFailure instanceof ExportFailure
                    ? resourceFailure.getMessage()
                    : "PNG_WRITE: " + resourceFailure);
        }
        try {
            context.writeFinalMetadata(true, elapsedMillis());
        } catch (Throwable metadataFailure) {
            FatalErrors.rethrowIfFatal(metadataFailure);
            GtnhNeiExportMod.LOGGER.error(
                    "[gtnh-nei-export] Could not write aborted staging diagnostics {}",
                    stagingOutput, metadataFailure);
        }
        complete = true;
        GtnhNeiExportMod.LOGGER.error(
                "[gtnh-nei-export] Export aborted; final output was not modified. Staging remains at {}",
                stagingOutput, cause);
    }

    private long elapsedMillis() {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    boolean isComplete() {
        return complete;
    }

    boolean isFailed() {
        return failed;
    }

    Path output() {
        return finalOutput;
    }

    String progress() {
        if (itemCursor < initialItems.size()) {
            return "items " + itemCursor + "/" + initialItems.size() + ", unique="
                    + context.catalog.count() + ", PNG pending=" + context.pngWriter.pending();
        }
        if (currentHandler != null) {
            return "recipes " + currentPlan.handlerId + " " + recipeCursor + "/"
                    + currentHandler.numRecipes() + ", total=" + context.recipesEnumerated
                    + ", PNG pending=" + context.pngWriter.pending();
        }
        return "categories " + categoryCursor + "/" + plans.size();
    }
}
