package com.recipetree.neiexport1710;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Final gate shared by automation and unit tests; malformed output never receives a complete marker. */
final class ManifestContract {
    static final String ATTRIBUTION_SOURCE_URL =
            "https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/tree/2.8.4";
    static final String ATTRIBUTION_PROJECT_URL = "https://www.gtnewhorizons.com/";
    static final String ATTRIBUTION_LICENSE_IDENTIFIER = "CC BY-NC-SA 4.0";
    static final String ATTRIBUTION_LICENSE_URL =
            "https://creativecommons.org/licenses/by-nc-sa/4.0/";

    private ManifestContract() {
    }

    static void validatePublished(Path manifestFile) throws IOException {
        if (!Files.isRegularFile(manifestFile)) {
            throw new IOException("published manifest is missing: " + manifestFile);
        }
        JsonElement parsed;
        try (Reader reader = Files.newBufferedReader(manifestFile, StandardCharsets.UTF_8)) {
            parsed = new JsonParser().parse(reader);
        }
        if (!parsed.isJsonObject()) {
            throw new IOException("published manifest root is not an object");
        }
        validatePublished(parsed.getAsJsonObject());
    }

    static void validatePublished(JsonObject manifest) throws IOException {
        requireInt(manifest, "format", 2);
        requireString(manifest, "profile", "gtnh-1.7.10");
        requireBoolean(manifest, "aborted", false);
        requireString(manifest, "minecraft", "1.7.10");
        requireString(manifest, "forge", "10.13.4.1614");
        requireString(manifest, "nei", "2.8.44-GTNH");

        JsonObject pack = object(manifest, "pack");
        requireExactKeys(pack, "pack", "name", "version", "identitySource");
        requireString(pack, "name", ExportRequest.PACK_NAME);
        requireString(pack, "version", ExportRequest.PACK_VERSION);
        requireString(pack, "identitySource", "explicit-request");

        JsonObject attribution = object(manifest, "attribution");
        requireExactKeys(attribution, "attribution",
                "sourceUrl", "projectUrl", "licenseIdentifier", "licenseUrl");
        requireString(attribution, "sourceUrl", ATTRIBUTION_SOURCE_URL);
        requireString(attribution, "projectUrl", ATTRIBUTION_PROJECT_URL);
        requireString(attribution, "licenseIdentifier", ATTRIBUTION_LICENSE_IDENTIFIER);
        requireString(attribution, "licenseUrl", ATTRIBUTION_LICENSE_URL);

        JsonObject settings = object(manifest, "settings");
        requireExactKeys(settings, "settings", "iconScale", "recipeScale", "mobCanvas");
        requireInt(settings, "iconScale", ExportRequest.ICON_SCALE);
        requireInt(settings, "recipeScale", ExportRequest.RECIPE_SCALE);
        requireInt(settings, "mobCanvas", ExportRequest.MOB_CANVAS);

        validateHandlerPolicies(manifest);
        JsonObject knowledge = object(manifest, "knowledgePolicy");
        requireExactKeys(knowledge, "knowledgePolicy",
                "playerResearchMutated", "thaumcraftLockedRecipes", "itemAspectDisplayNames",
                "forestryScannedSaplingDisplayName", "forestryScannedSaplingSourceBinding",
                "forestryScannedPollenDisplayName", "forestryScannedPollenSourceBinding",
                "itemAspectRecipeSemantics", "gregTechOutputlessRecipeSemantics",
                "gregTechStaleDoorRecyclingExclusion",
                "ownerInternalFurnaceFuelRowExclusion",
                "ae2InternalFacadeRecipeExclusion",
                "gendustryMachineRecipeSemantics");
        requireBoolean(knowledge, "playerResearchMutated", false);
        requireString(knowledge, "thaumcraftLockedRecipes", "required-by-pinned-config");
        requireString(knowledge, "itemAspectDisplayNames", "nbt-aspect-registry-v1");
        requireString(knowledge, "forestryScannedSaplingDisplayName",
                DisplayNameResolver.FORESTRY_SCANNED_SAPLING_NAME_CONTRACT);
        requireString(knowledge, "forestryScannedSaplingSourceBinding",
                GregTechForestryScannedSaplingPreflight.CONTRACT);
        requireString(knowledge, "forestryScannedPollenDisplayName",
                DisplayNameResolver.FORESTRY_SCANNED_POLLEN_NAME_CONTRACT);
        requireString(knowledge, "forestryScannedPollenSourceBinding",
                GregTechForestryScannedPollenPreflight.CONTRACT);
        requireString(knowledge, "itemAspectRecipeSemantics",
                TcnaAspectCostSemanticNormalizer.CONTRACT);
        requireString(knowledge, "gregTechOutputlessRecipeSemantics",
                GregTechOutputlessSemanticPreflight.CONTRACT);
        requireString(knowledge, "gregTechStaleDoorRecyclingExclusion",
                GregTechOutputlessSemanticPreflight
                        .UNREGISTERED_DOOR_RECYCLING_CONTRACT);
        requireString(knowledge, "ownerInternalFurnaceFuelRowExclusion",
                CatalogExcludedFuelPreflight.CONTRACT);
        requireString(knowledge, "ae2InternalFacadeRecipeExclusion",
                Ae2InternalFacadeRecipePreflight.CONTRACT);
        requireString(knowledge, "gendustryMachineRecipeSemantics",
                GendustryMachineSemanticAdapter.CONTRACT);

        JsonObject counts = object(manifest, "counts");
        int items = positive(counts, "items");
        int recipes = positive(counts, "recipes");
        int categories = positive(counts, "categories");
        requireInt(counts, "mobs", 0);
        requireInt(counts, "blockDrops", 0);
        requireInt(counts, "failures", 0);

        JsonObject diagnostics = object(manifest, "diagnostics");
        requireExactKeys(diagnostics, "diagnostics",
                "failureEvents", "failureEventsOmitted", "nei");
        requireInt(diagnostics, "failureEvents", 0);
        requireInt(diagnostics, "failureEventsOmitted", 0);
        JsonObject nei = object(diagnostics, "nei");
        requireExactKeys(nei, "diagnostics.nei",
                "itemListLoaded", "itemListRawEntries", "itemListExcludedEntries",
                "itemListRetainedEntries", "itemListRetainedUniqueIdentities",
                "registeredCraftingHandlers", "exportableCraftingHandlers",
                "adaptedHandlerCategories", "excludedNonRecipeHandlers",
                "excludedEmptyRecipeHandlers",
                "excludedUnboundTemplateRecipeHandlers",
                "excludedAe2fcFluidDropItemListPlaceholders",
                "excludedAe2fcFluidPacketItemListPlaceholders",
                "excludedAe2CableBusInternalBlockItemListEntries",
                "excludedAe2MatrixFrameInternalBlockItemListEntries",
                "excludedBloodMagicBloodLightItemListHelpers",
                "excludedBloodMagicSpectralContainerItemListHelpers",
                "excludedArchitectureCraftCladdingItemListPlaceholders",
                "excludedAvaritiaEmptyMatterClusterItemListPlaceholders",
                "excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders",
                "excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries",
                "excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders",
                "malisisDoorsUnconfiguredCustomDoorRecipeReferences",
                "malisisDoorsUnconfiguredCustomDoorQuestReferences",
                "excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders",
                "malisisDoorsUnconfiguredMixedBlockRecipeReferences",
                "malisisDoorsUnconfiguredMixedBlockQuestReferences",
                "excludedBotaniaBifrostItemListWorldStateEntries",
                "excludedBotaniaBuriedPetalsItemListWorldStateVariants",
                "excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask",
                "excludedBotaniaCacophoniumBlockItemListWorldStateEntries",
                "excludedBotaniaEnchanterItemListWorldStateEntries",
                "excludedBotaniaFakeAirItemListWorldStateEntries",
                "excludedBotaniaManaFlameItemListWorldStateEntries",
                "excludedBotaniaSolidVineItemListWorldStateEntries",
                "excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders",
                "excludedCarpentersBedInternalBlockItemListEntries",
                "excludedCarpentersDoorInternalBlockItemListEntries",
                "excludedStevesCartsUnconfiguredModularCartItemListPlaceholders",
                "excludedTConstructBattleSignInternalBlockItemListEntries",
                "excludedTConstructHeldItemInternalBlockItemListEntries",
                "excludedThaumcraftBlockHoleInternalBlockItemListEntries",
                "excludedThaumcraftEldritchPortalInternalBlockItemListEntries",
                "excludedThaumicHorizonsBaseLightInternalBlockItemListEntries",
                "excludedThaumicHorizonsSolarLightInternalBlockItemListEntries",
                "excludedTwilightForestExperiment115InternalBlockItemListEntries",
                "excludedWitchingGadgetsCustomAirInternalBlockItemListEntries",
                "adaptedBotaniaCocoonItemIcons",
                "adaptedBotaniaCocoonRecipeWidgetRenderInvocations",
                "adaptedBotaniaPrismItemIcons",
                "adaptedBotaniaPrismRecipeWidgetRenderInvocations",
                "adaptedGalacticraftFlagItemIcons",
                "adaptedGalacticraftFlagRecipeWidgetRenderInvocations",
                "adaptedWrcbeTriangulatorItemIcons",
                "adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations",
                "adaptedModernMarkingsCrossingItemIcons",
                "adaptedThaumcraftRunedStoneItemIcons",
                "adaptedForestryScannedSaplingDisplayNames",
                "gregTechForestryScannedSaplingRecipeOccurrences",
                "adaptedForestryScannedPollenDisplayNames",
                "gregTechForestryScannedPollenRecipeOccurrences",
                "adaptedProjectBlueControlPanelItemIcons",
                "adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations",
                "adaptedIc2FluidCannerRecipeWidgetRenderInvocations",
                "adaptedBuildCraftPhasedFacadeItemIcons",
                "adaptedMobsInfoInfernalPreviewOutputIcons",
                "adaptedMobsInfoPreviewSlotIcons",
                "adaptedDraconicMobSoulItemIcons",
                "normalizedTcnaAspectCostInputOccurrences",
                "normalizedTcnaAspectCostDistinctKeys",
                "normalizedTcnaAspectCostHandlerCategories",
                "adaptedGendustryLiquifierRecipes",
                "adaptedGendustryMutagenProducerRecipes",
                "adaptedGendustryExtractorRecipes",
                "adaptedGendustryReplicatorRecipes",
                "adaptedGendustryTransposerRecipes",
                "adaptedGendustryMutatronRecipes",
                "adaptedGendustrySamplerRecipes",
                "adaptedGendustryImprinterRecipes",
                "loadedCategories",
                "recipesEnumerated", "recipeWidgetsRendered", "itemIconsRendered",
                "informationalEmptyOutputRecipes",
                "gregTechFuelSinkRecipes", "gregTechFuelSinkCategories",
                "gregTechLargeBoilerFuelSinkRecipes",
                "gregTechLargeBoilerFuelSinkCategories",
                "gregTechRadioHatchInformationRecipes",
                "gregTechQuantumComponentInformationRecipes",
                "gregTechSpaceProjectInformationRecipes",
                "gregTechOutputlessSemanticCategories",
                "gregTechOutputlessSemanticRecipes",
                "excludedGregTechLargeBoilerPresentationRows",
                "excludedGregTechUnregisteredDoorRecyclingRows",
                "excludedOwnerInternalFurnaceFuelRows",
                "excludedAe2EnderIoInternalConduitFacadeRows",
                "excludedUnregisteredGregTechMachineCatalysts",
                "knowledgeIndependentAspectNames",
                "unloadedHandlerCategories", "ambiguousHandlerCategories",
                "duplicateHandlerCategories");
        requireBoolean(nei, "itemListLoaded", true);
        requireInt(nei, "itemListRawEntries", StackIdentity.PINNED_RAW_ITEM_LIST_COUNT);
        requireInt(nei, "itemListExcludedEntries",
                StackIdentity.PINNED_CATALOG_EXCLUSION_COUNT);
        requireInt(nei, "itemListRetainedEntries",
                StackIdentity.PINNED_RETAINED_ITEM_LIST_COUNT);
        requireInt(nei, "itemListRetainedUniqueIdentities",
                StackIdentity.PINNED_RETAINED_UNIQUE_ITEM_LIST_IDENTITY_COUNT);
        if (requiredInt(nei, "itemListRawEntries")
                        - requiredInt(nei, "itemListExcludedEntries")
                != requiredInt(nei, "itemListRetainedEntries")
                || requiredInt(nei, "itemListRetainedEntries")
                        - requiredInt(nei, "itemListRetainedUniqueIdentities") != 1
                || items < requiredInt(nei, "itemListRetainedUniqueIdentities")) {
            throw new IOException(
                    "ItemList raw/excluded/retained/unique reconciliation or final union "
                            + "catalog containment drifted");
        }
        requireInt(nei, "registeredCraftingHandlers", categories + 20 + 22 + 1);
        requireInt(nei, "exportableCraftingHandlers", categories);
        requireInt(nei, "adaptedHandlerCategories", 45);
        requireInt(nei, "excludedNonRecipeHandlers", 20);
        requireInt(nei, "excludedEmptyRecipeHandlers", 22);
        requireInt(nei, "excludedUnboundTemplateRecipeHandlers", 1);
        requireInt(nei, "excludedAe2fcFluidDropItemListPlaceholders", 1);
        requireInt(nei, "excludedAe2fcFluidPacketItemListPlaceholders", 1);
        requireInt(nei, "excludedAe2CableBusInternalBlockItemListEntries", 1);
        requireInt(nei, "excludedAe2MatrixFrameInternalBlockItemListEntries", 1);
        requireInt(nei, "excludedBloodMagicBloodLightItemListHelpers", 1);
        requireInt(nei, "excludedBloodMagicSpectralContainerItemListHelpers", 1);
        requireInt(nei, "excludedArchitectureCraftCladdingItemListPlaceholders", 1);
        requireInt(nei, "excludedAvaritiaEmptyMatterClusterItemListPlaceholders", 1);
        requireInt(
                nei, "excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders", 1);
        requireInt(
                nei, "excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries", 1);
        requireInt(
                nei, "excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders", 1);
        requireInt(nei, "malisisDoorsUnconfiguredCustomDoorRecipeReferences", 0);
        requireInt(nei, "malisisDoorsUnconfiguredCustomDoorQuestReferences", 0);
        requireInt(
                nei, "excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders", 1);
        requireInt(nei, "malisisDoorsUnconfiguredMixedBlockRecipeReferences", 0);
        requireInt(nei, "malisisDoorsUnconfiguredMixedBlockQuestReferences", 0);
        requireInt(nei, "excludedBotaniaBifrostItemListWorldStateEntries", 1);
        requireInt(nei, "excludedBotaniaBuriedPetalsItemListWorldStateVariants", 16);
        requireInt(nei, "excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask", 0xffff);
        requireInt(nei, "excludedBotaniaCacophoniumBlockItemListWorldStateEntries", 1);
        requireInt(nei, "excludedBotaniaEnchanterItemListWorldStateEntries", 1);
        requireInt(nei, "excludedBotaniaFakeAirItemListWorldStateEntries", 1);
        requireInt(nei, "excludedBotaniaManaFlameItemListWorldStateEntries", 1);
        requireInt(nei, "excludedBotaniaSolidVineItemListWorldStateEntries", 1);
        requireInt(
                nei, "excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders", 1);
        requireInt(nei, "excludedCarpentersBedInternalBlockItemListEntries", 1);
        requireInt(nei, "excludedCarpentersDoorInternalBlockItemListEntries", 1);
        requireInt(
                nei, "excludedStevesCartsUnconfiguredModularCartItemListPlaceholders", 1);
        requireInt(nei, "excludedTConstructBattleSignInternalBlockItemListEntries", 1);
        requireInt(nei, "excludedTConstructHeldItemInternalBlockItemListEntries", 1);
        requireInt(nei, "excludedThaumcraftBlockHoleInternalBlockItemListEntries", 1);
        requireInt(nei, "excludedThaumcraftEldritchPortalInternalBlockItemListEntries", 1);
        requireInt(nei, "excludedThaumicHorizonsBaseLightInternalBlockItemListEntries", 1);
        requireInt(nei, "excludedThaumicHorizonsSolarLightInternalBlockItemListEntries", 1);
        requireInt(nei, "excludedTwilightForestExperiment115InternalBlockItemListEntries", 1);
        requireInt(nei, "excludedWitchingGadgetsCustomAirInternalBlockItemListEntries", 1);
        int exclusionTelemetry = sumInts(nei,
                "excludedAe2fcFluidDropItemListPlaceholders",
                "excludedAe2fcFluidPacketItemListPlaceholders",
                "excludedAe2CableBusInternalBlockItemListEntries",
                "excludedAe2MatrixFrameInternalBlockItemListEntries",
                "excludedBloodMagicBloodLightItemListHelpers",
                "excludedBloodMagicSpectralContainerItemListHelpers",
                "excludedArchitectureCraftCladdingItemListPlaceholders",
                "excludedAvaritiaEmptyMatterClusterItemListPlaceholders",
                "excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders",
                "excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries",
                "excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders",
                "excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders",
                "excludedBotaniaBifrostItemListWorldStateEntries",
                "excludedBotaniaBuriedPetalsItemListWorldStateVariants",
                "excludedBotaniaCacophoniumBlockItemListWorldStateEntries",
                "excludedBotaniaEnchanterItemListWorldStateEntries",
                "excludedBotaniaFakeAirItemListWorldStateEntries",
                "excludedBotaniaManaFlameItemListWorldStateEntries",
                "excludedBotaniaSolidVineItemListWorldStateEntries",
                "excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders",
                "excludedCarpentersBedInternalBlockItemListEntries",
                "excludedCarpentersDoorInternalBlockItemListEntries",
                "excludedStevesCartsUnconfiguredModularCartItemListPlaceholders",
                "excludedTConstructBattleSignInternalBlockItemListEntries",
                "excludedTConstructHeldItemInternalBlockItemListEntries",
                "excludedThaumcraftBlockHoleInternalBlockItemListEntries",
                "excludedThaumcraftEldritchPortalInternalBlockItemListEntries",
                "excludedThaumicHorizonsBaseLightInternalBlockItemListEntries",
                "excludedThaumicHorizonsSolarLightInternalBlockItemListEntries",
                "excludedTwilightForestExperiment115InternalBlockItemListEntries",
                "excludedWitchingGadgetsCustomAirInternalBlockItemListEntries");
        if (exclusionTelemetry != requiredInt(nei, "itemListExcludedEntries")) {
            throw new IOException(
                    "ItemList exclusion telemetry must sum to itemListExcludedEntries; got "
                            + exclusionTelemetry + " versus "
                            + requiredInt(nei, "itemListExcludedEntries"));
        }
        requireInt(nei, "adaptedBotaniaCocoonItemIcons", 1);
        positive(nei, "adaptedBotaniaCocoonRecipeWidgetRenderInvocations");
        requireInt(nei, "adaptedBotaniaPrismItemIcons", 1);
        positive(nei, "adaptedBotaniaPrismRecipeWidgetRenderInvocations");
        requireInt(nei, "adaptedGalacticraftFlagItemIcons", 1);
        positive(nei, "adaptedGalacticraftFlagRecipeWidgetRenderInvocations");
        requireInt(nei, "adaptedWrcbeTriangulatorItemIcons", 1);
        positive(nei, "adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations");
        requireInt(nei, "adaptedModernMarkingsCrossingItemIcons", 6);
        requireInt(nei, "adaptedThaumcraftRunedStoneItemIcons",
                ThaumcraftRunedStoneIconRenderer.EXPECTED_ITEM_ICONS);
        requireInt(nei, "adaptedForestryScannedSaplingDisplayNames",
                DisplayNameResolver.EXPECTED_FORESTRY_SCANNED_SAPLING_NAMES);
        requireInt(nei, "gregTechForestryScannedSaplingRecipeOccurrences",
                GregTechForestryScannedSaplingPreflight.EXPECTED_RECIPE_OCCURRENCES);
        requireInt(nei, "adaptedForestryScannedPollenDisplayNames",
                DisplayNameResolver.EXPECTED_FORESTRY_SCANNED_POLLEN_NAMES);
        requireInt(nei, "gregTechForestryScannedPollenRecipeOccurrences",
                GregTechForestryScannedPollenPreflight.EXPECTED_RECIPE_OCCURRENCES);
        requireInt(nei, "adaptedProjectBlueControlPanelItemIcons",
                ProjectBlueControlPanelIconRenderer.EXPECTED_TARGETS);
        requireInt(nei, "adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations",
                ProjectBlueControlPanelIconRenderer.EXPECTED_TARGETS);
        requireInt(nei, "adaptedIc2FluidCannerRecipeWidgetRenderInvocations", 5);
        requireInt(nei, "adaptedBuildCraftPhasedFacadeItemIcons",
                BuildCraftPhasedFacadeIconRenderer.EXPECTED_ITEM_ICONS);
        requireInt(nei, "adaptedMobsInfoInfernalPreviewOutputIcons", 58);
        requireInt(nei, "adaptedMobsInfoPreviewSlotIcons",
                MobsInfoSemanticAdapter.EXPECTED_PREVIEW_SLOT_ICONS);
        requireIntRange(nei, "adaptedDraconicMobSoulItemIcons", 1, 401);
        positive(nei, "normalizedTcnaAspectCostInputOccurrences");
        positive(nei, "normalizedTcnaAspectCostDistinctKeys");
        requireInt(nei, "normalizedTcnaAspectCostHandlerCategories",
                TcnaAspectCostSemanticNormalizer.EXPECTED_HANDLER_CATEGORIES);
        requireInt(nei, "adaptedGendustryLiquifierRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_LIQUIFIER_PAGES);
        requireInt(nei, "adaptedGendustryMutagenProducerRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_MUTAGEN_PRODUCER_PAGES);
        requireInt(nei, "adaptedGendustryExtractorRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_EXTRACTOR_PAGES);
        requireInt(nei, "adaptedGendustryReplicatorRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_REPLICATOR_PAGES);
        requireInt(nei, "adaptedGendustryTransposerRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_TRANSPOSER_PAGES);
        requireInt(nei, "adaptedGendustryMutatronRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_MUTATRON_PAGES);
        requireInt(nei, "adaptedGendustrySamplerRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_SAMPLER_PAGES);
        requireInt(nei, "adaptedGendustryImprinterRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_IMPRINTER_PAGES);
        requireInt(nei, "loadedCategories", categories);
        requireInt(nei, "recipesEnumerated", recipes);
        requireInt(nei, "recipeWidgetsRendered", recipes);
        requireInt(nei, "itemIconsRendered", items);
        requireInt(nei, "informationalEmptyOutputRecipes", 513);
        requireInt(nei, "gregTechFuelSinkRecipes",
                GregTechOutputlessSemanticPreflight.EXPECTED_FUEL_SINK_RECIPES);
        requireInt(nei, "gregTechFuelSinkCategories",
                GregTechOutputlessSemanticPreflight.EXPECTED_FUEL_SINK_CATEGORIES);
        requireInt(nei, "gregTechLargeBoilerFuelSinkRecipes",
                GregTechOutputlessSemanticPreflight.EXPECTED_LARGE_BOILER_FUEL_SINK_RECIPES);
        requireInt(nei, "gregTechLargeBoilerFuelSinkCategories",
                GregTechOutputlessSemanticPreflight
                        .EXPECTED_LARGE_BOILER_FUEL_SINK_CATEGORIES);
        requireInt(nei, "gregTechRadioHatchInformationRecipes",
                GregTechOutputlessSemanticPreflight
                        .EXPECTED_RADIO_HATCH_INFORMATION_RECIPES);
        requireInt(nei, "gregTechQuantumComponentInformationRecipes",
                GregTechOutputlessSemanticPreflight
                        .EXPECTED_QUANTUM_COMPONENT_INFORMATION_RECIPES);
        requireInt(nei, "gregTechSpaceProjectInformationRecipes",
                GregTechOutputlessSemanticPreflight
                        .EXPECTED_SPACE_PROJECT_INFORMATION_RECIPES);
        requireInt(nei, "gregTechOutputlessSemanticCategories",
                GregTechOutputlessSemanticPreflight.EXPECTED_SEMANTIC_CATEGORIES);
        requireInt(nei, "gregTechOutputlessSemanticRecipes",
                GregTechOutputlessSemanticPreflight.EXPECTED_SEMANTIC_RECIPES);
        requireInt(nei, "excludedGregTechLargeBoilerPresentationRows",
                GregTechOutputlessSemanticPreflight
                        .EXPECTED_EXCLUDED_LARGE_BOILER_PRESENTATION_ROWS);
        requireInt(nei, "excludedGregTechUnregisteredDoorRecyclingRows",
                GregTechOutputlessSemanticPreflight
                        .EXPECTED_UNREGISTERED_DOOR_RECYCLING_ROWS);
        requireInt(nei, "excludedOwnerInternalFurnaceFuelRows",
                CatalogExcludedFuelPreflight.EXPECTED_EXCLUSIONS);
        requireInt(nei, "excludedAe2EnderIoInternalConduitFacadeRows",
                Ae2InternalFacadeRecipePreflight.EXPECTED_EXCLUSIONS);
        requireInt(nei, "excludedUnregisteredGregTechMachineCatalysts",
                UnregisteredGregTechMachineCatalystPolicy.EXPECTED_EXCLUSIONS);
        requireInt(nei, "knowledgeIndependentAspectNames",
                TcnaAspectCostSemanticNormalizer.EXPECTED_CATALOG_ASPECT_IDENTITIES);
        requireInt(nei, "unloadedHandlerCategories", 0);
        requireInt(nei, "ambiguousHandlerCategories", 0);
        requireInt(nei, "duplicateHandlerCategories", 0);

        JsonObject mods = object(manifest, "mods");
        if (mods.entrySet().isEmpty()) {
            throw new IOException("mods must contain at least one active mod");
        }
        for (java.util.Map.Entry<String, JsonElement> entry : mods.entrySet()) {
            if (entry.getKey().trim().isEmpty()
                    || !entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isString()
                    || entry.getValue().getAsString().trim().isEmpty()) {
                throw new IOException("mods must map non-blank mod IDs to non-blank display names");
            }
        }
    }

    private static void validateHandlerPolicies(JsonObject manifest) throws IOException {
        JsonElement element = manifest.get("handlerPolicies");
        if (element == null || !element.isJsonArray()) {
            throw new IOException("handlerPolicies must be an array");
        }
        JsonArray policies = element.getAsJsonArray();
        MapBuilder expected = new MapBuilder();
        List<CompleteCategoryAdapters.Policy> expectedPolicies =
                CompleteCategoryAdapters.expectedPoliciesForContract();
        int expectedAdapted = 0;
        int expectedExcluded = 0;
        for (CompleteCategoryAdapters.Policy policy : expectedPolicies) {
            expected.add(policy.handlerClass, policy.handlerId,
                    policy.action, policy.contract);
            if (CompleteCategoryAdapters.isExcludedFromCategoryExport(policy.adapter)) {
                expectedExcluded++;
            } else {
                expectedAdapted++;
            }
        }
        if (expectedPolicies.size() != 66
                || expectedAdapted != 45
                || expectedExcluded != 21) {
            throw new IOException("compiled handler policy contract must contain exactly "
                    + "66 policies (45 adapted, 21 excluded); got "
                    + expectedPolicies.size() + " (" + expectedAdapted
                    + " adapted, " + expectedExcluded + " excluded)");
        }
        Set<String> observed = new HashSet<String>();
        for (int index = 0; index < policies.size(); index++) {
            JsonElement raw = policies.get(index);
            if (!raw.isJsonObject()) {
                throw new IOException("handlerPolicies[" + index + "] must be an object");
            }
            JsonObject policy = raw.getAsJsonObject();
            requireExactKeys(policy, "handlerPolicies[" + index + "]",
                    "handlerClass", "handlerId", "action", "contract");
            String handlerClass = requiredString(policy, "handlerClass");
            String handlerId = requiredString(policy, "handlerId");
            String identity = policyIdentity(handlerClass, handlerId);
            ExpectedPolicy expectedValue = expected.values.get(identity);
            if (expectedValue == null) {
                throw new IOException("unrecognized pinned handler policy for class="
                        + handlerClass + ", handlerId=" + handlerId);
            }
            String action = requiredString(policy, "action");
            String contract = requiredString(policy, "contract");
            if (!expectedValue.action.equals(action)
                    || !expectedValue.contract.equals(contract)) {
                throw new IOException("handler policy drift for class=" + handlerClass
                        + ", handlerId=" + handlerId);
            }
            if (!observed.add(identity)) {
                throw new IOException("duplicate handler policy for class=" + handlerClass
                        + ", handlerId=" + handlerId);
            }
        }
        if (!observed.equals(expected.values.keySet())) {
            throw new IOException("handlerPolicies must cover exactly the compiled set of "
                    + expected.values.size() + " composite class/handler-ID identities; got "
                    + observed.size());
        }
    }

    private static final class MapBuilder {
        final Map<String, ExpectedPolicy> values =
                new LinkedHashMap<String, ExpectedPolicy>();

        MapBuilder add(String handlerClass, String handlerId,
                       String action, String contract) throws IOException {
            String identity = policyIdentity(handlerClass, handlerId);
            ExpectedPolicy previous = values.put(identity,
                    new ExpectedPolicy(action, contract));
            if (previous != null) {
                throw new IOException("compiled handler policy contract contains duplicate "
                        + "class/handler-ID identity: class=" + handlerClass
                        + ", handlerId=" + handlerId);
            }
            return this;
        }
    }

    private static final class ExpectedPolicy {
        final String action;
        final String contract;

        ExpectedPolicy(String action, String contract) {
            this.action = action;
            this.contract = contract;
        }
    }

    /** Length framing makes the composite identity injective without delimiter assumptions. */
    private static String policyIdentity(String handlerClass, String handlerId) {
        return handlerClass.length() + ":" + handlerClass
                + handlerId.length() + ":" + handlerId;
    }

    private static int positive(JsonObject object, String name) throws IOException {
        JsonElement element = object.get(name);
        final int value;
        try {
            value = element == null ? -1 : element.getAsInt();
        } catch (RuntimeException error) {
            throw new IOException(name + " must be a positive integer", error);
        }
        if (value <= 0) {
            throw new IOException(name + " must be positive; got " + value);
        }
        return value;
    }

    private static JsonObject object(JsonObject parent, String name) throws IOException {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonObject()) {
            throw new IOException(name + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static String requiredString(JsonObject object, String name) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().trim().isEmpty()) {
            throw new IOException(name + " must be a non-blank string");
        }
        return value.getAsString();
    }

    private static void requireString(JsonObject object, String name, String expected) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || !expected.equals(value.getAsString())) {
            throw new IOException(name + " must equal '" + expected + "'");
        }
    }

    private static void requireBoolean(JsonObject object, String name, boolean expected) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()
                || value.getAsBoolean() != expected) {
            throw new IOException(name + " must equal " + expected);
        }
    }

    private static int requiredInt(JsonObject object, String name) throws IOException {
        JsonElement value = object.get(name);
        final int actual;
        try {
            actual = value == null ? Integer.MIN_VALUE : value.getAsInt();
        } catch (RuntimeException error) {
            throw new IOException(name + " must be an integer", error);
        }
        if (actual == Integer.MIN_VALUE) {
            throw new IOException(name + " must be an integer");
        }
        return actual;
    }

    private static void requireInt(JsonObject object, String name, int expected) throws IOException {
        int actual = requiredInt(object, name);
        if (actual != expected) {
            throw new IOException(name + " must equal " + expected + "; got " + actual);
        }
    }

    private static void requireIntRange(JsonObject object, String name, int minimum,
                                        int maximum) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException(name + " must be an integer");
        }
        int actual;
        try {
            actual = value.getAsInt();
        } catch (RuntimeException error) {
            throw new IOException(name + " must be an integer", error);
        }
        if (actual < minimum || actual > maximum) {
            throw new IOException(name + " must be in [" + minimum + ", " + maximum + "]");
        }
    }

    private static int sumInts(JsonObject object, String... names) throws IOException {
        int total = 0;
        for (String name : names) {
            try {
                total = Math.addExact(total, requiredInt(object, name));
            } catch (ArithmeticException error) {
                throw new IOException("integer overflow while summing " + name, error);
            }
        }
        return total;
    }

    private static void requireExactKeys(JsonObject object, String path, String... names) throws IOException {
        Set<String> expected = new HashSet<String>(Arrays.asList(names));
        Set<String> actual = new HashSet<String>();
        for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
            actual.add(entry.getKey());
        }
        if (!expected.equals(actual)) {
            throw new IOException(path + " keys must be exactly " + expected + "; got " + actual);
        }
    }
}
