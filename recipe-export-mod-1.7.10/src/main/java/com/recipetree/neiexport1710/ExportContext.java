package com.recipetree.neiexport1710;

import com.google.gson.stream.JsonWriter;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class ExportContext {
    private static final int MAX_FAILURE_SAMPLES = 5000;

    static final class CategoryMeta {
        final String id;
        final String title;
        final String directory;
        final List<String> catalysts = new ArrayList<String>();
        int count;
        String icon;

        CategoryMeta(String id, String title, String directory) {
            this.id = id;
            this.title = title;
            this.directory = directory;
        }

        void write(JsonWriter writer) throws IOException {
            writer.beginObject();
            writer.name("id").value(id);
            writer.name("title").value(title);
            writer.name("dir").value(directory);
            writer.name("count").value(count);
            if (icon != null) {
                writer.name("icon").value(icon);
            }
            writer.name("catalysts").beginArray();
            for (String catalyst : catalysts) {
                writer.value(catalyst);
            }
            writer.endArray();
            writer.endObject();
        }
    }

    final Path root;
    final ExportRequest request;
    final ExporterBuildIdentity exporterBuildIdentity;
    final OffscreenRenderer renderer;
    final PngWriter pngWriter;
    final ItemCatalog catalog;
    final List<CategoryMeta> categories = new ArrayList<CategoryMeta>();
    final Map<String, PrimitiveRefs> reverseIndex = new LinkedHashMap<String, PrimitiveRefs>();
    final List<String> failures = Collections.synchronizedList(new ArrayList<String>());
    final List<CompleteCategoryAdapters.Policy> handlerPolicies =
            new ArrayList<CompleteCategoryAdapters.Policy>();

    boolean itemListLoaded;
    int itemListRawEntries;
    int itemListExcludedEntries;
    int itemListRetainedEntries;
    int itemListRetainedUniqueIdentities;
    int registeredCraftingHandlers;
    int exportableCraftingHandlers;
    int adaptedHandlerCategories;
    int excludedNonRecipeHandlers;
    int excludedEmptyRecipeHandlers;
    int excludedUnboundTemplateRecipeHandlers;
    int excludedAe2fcFluidDropItemListPlaceholders;
    int excludedAe2fcFluidPacketItemListPlaceholders;
    int excludedAe2CableBusInternalBlockItemListEntries;
    int excludedAe2MatrixFrameInternalBlockItemListEntries;
    int excludedBloodMagicBloodLightItemListHelpers;
    int excludedBloodMagicSpectralContainerItemListHelpers;
    int excludedArchitectureCraftCladdingItemListPlaceholders;
    int excludedAvaritiaEmptyMatterClusterItemListPlaceholders;
    int excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders;
    int excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries;
    int excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders;
    int malisisDoorsUnconfiguredCustomDoorRecipeReferences;
    int malisisDoorsUnconfiguredCustomDoorQuestReferences;
    int excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders;
    int malisisDoorsUnconfiguredMixedBlockRecipeReferences;
    int malisisDoorsUnconfiguredMixedBlockQuestReferences;
    int excludedBotaniaBifrostItemListWorldStateEntries;
    int excludedBotaniaBuriedPetalsItemListWorldStateVariants;
    int excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask;
    int excludedBotaniaCacophoniumBlockItemListWorldStateEntries;
    int excludedBotaniaEnchanterItemListWorldStateEntries;
    int excludedBotaniaFakeAirItemListWorldStateEntries;
    int excludedBotaniaManaFlameItemListWorldStateEntries;
    int excludedBotaniaSolidVineItemListWorldStateEntries;
    int excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders;
    int excludedCarpentersBedInternalBlockItemListEntries;
    int excludedCarpentersDoorInternalBlockItemListEntries;
    int excludedStevesCartsUnconfiguredModularCartItemListPlaceholders;
    int excludedTConstructBattleSignInternalBlockItemListEntries;
    int excludedTConstructHeldItemInternalBlockItemListEntries;
    int excludedThaumcraftBlockHoleInternalBlockItemListEntries;
    int excludedThaumcraftEldritchPortalInternalBlockItemListEntries;
    int excludedThaumicHorizonsBaseLightInternalBlockItemListEntries;
    int excludedThaumicHorizonsSolarLightInternalBlockItemListEntries;
    int excludedTwilightForestExperiment115InternalBlockItemListEntries;
    int excludedWitchingGadgetsCustomAirInternalBlockItemListEntries;
    int adaptedBotaniaCocoonItemIcons;
    int adaptedBotaniaCocoonRecipeWidgetRenderInvocations;
    int adaptedBotaniaPrismItemIcons;
    int adaptedBotaniaPrismRecipeWidgetRenderInvocations;
    int adaptedGalacticraftFlagItemIcons;
    int adaptedGalacticraftFlagRecipeWidgetRenderInvocations;
    int adaptedWrcbeTriangulatorItemIcons;
    int adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations;
    int adaptedModernMarkingsCrossingItemIcons;
    int adaptedThaumcraftRunedStoneItemIcons;
    int adaptedForestryScannedSaplingDisplayNames;
    int gregTechForestryScannedSaplingRecipeOccurrences;
    int adaptedForestryScannedPollenDisplayNames;
    int gregTechForestryScannedPollenRecipeOccurrences;
    int adaptedProjectBlueControlPanelItemIcons;
    int adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations;
    long adaptedIc2FluidCannerRecipeWidgetRenderInvocations;
    int adaptedBuildCraftPhasedFacadeItemIcons;
    int adaptedMobsInfoInfernalPreviewOutputIcons;
    int adaptedMobsInfoPreviewSlotIcons;
    int adaptedDraconicMobSoulItemIcons;
    int normalizedTcnaAspectCostInputOccurrences;
    int normalizedTcnaAspectCostDistinctKeys;
    int normalizedTcnaAspectCostHandlerCategories;
    int adaptedGendustryLiquifierRecipes;
    int adaptedGendustryMutagenProducerRecipes;
    int adaptedGendustryExtractorRecipes;
    int adaptedGendustryReplicatorRecipes;
    int adaptedGendustryTransposerRecipes;
    int adaptedGendustryMutatronRecipes;
    int adaptedGendustrySamplerRecipes;
    int adaptedGendustryImprinterRecipes;
    int loadedCategories;
    int recipesEnumerated;
    int recipeWidgetsRendered;
    int itemIconsRendered;
    int informationalEmptyOutputRecipes;
    int gregTechFuelSinkRecipes;
    int gregTechFuelSinkCategories;
    int gregTechLargeBoilerFuelSinkRecipes;
    int gregTechLargeBoilerFuelSinkCategories;
    int gregTechRadioHatchInformationRecipes;
    int gregTechQuantumComponentInformationRecipes;
    int gregTechSpaceProjectInformationRecipes;
    int gregTechOutputlessSemanticCategories;
    int gregTechOutputlessSemanticRecipes;
    int excludedGregTechLargeBoilerPresentationRows;
    int excludedGregTechUnregisteredDoorRecyclingRows;
    int excludedOwnerInternalFurnaceFuelRows;
    int excludedAe2EnderIoInternalConduitFacadeRows;
    int excludedUnregisteredGregTechMachineCatalysts;
    int knowledgeIndependentAspectNames;
    int unloadedHandlerCategories;
    int ambiguousHandlerCategories;
    int duplicateHandlerCategories;

    private final AtomicInteger failureEvents = new AtomicInteger();
    private final AtomicInteger omittedFailureEvents = new AtomicInteger();
    private boolean resourcesClosed;

    ExportContext(Path root, ExportRequest request) throws IOException {
        this.root = root;
        this.request = request;
        this.exporterBuildIdentity = ExporterBuildIdentity.loadRuntime();
        Files.createDirectories(root);
        renderer = new OffscreenRenderer();
        pngWriter = new PngWriter(request.pngThreads, request.pngQueueCapacity);
        catalog = new ItemCatalog(this);
    }

    void submitImage(BufferedImage image, Path file) throws IOException {
        pngWriter.submit(image, file);
    }

    void index(String key, boolean output, int category, int recipe) {
        PrimitiveRefs refs = reverseIndex.get(key);
        if (refs == null) {
            refs = new PrimitiveRefs();
            reverseIndex.put(key, refs);
        }
        refs.add(output, category, recipe);
    }

    void failure(Throwable error) {
        String message = error == null ? "UNKNOWN: null failure" : error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = String.valueOf(error);
        }
        if (!hasFailureCode(message)) {
            message = "RECIPE_SEMANTICS: unexpected exporter failure: " + message;
        }
        failure(message);
    }

    private static boolean hasFailureCode(String message) {
        return message.startsWith("HANDLER_UNLOADED:")
                || message.startsWith("HANDLER_AMBIGUOUS:")
                || message.startsWith("HANDLER_DUPLICATE:")
                || message.startsWith("RECIPE_VISIBILITY_GATED:")
                || message.startsWith("RECIPE_SEMANTICS:")
                || message.startsWith("ITEM_IDENTITY:")
                || message.startsWith("QUANTITY_INVALID:")
                || message.startsWith("ITEM_ICON_RENDER:")
                || message.startsWith("RECIPE_WIDGET_RENDER:")
                || message.startsWith("PNG_WRITE:");
    }

    void failure(String message) {
        String safe = message == null ? "UNKNOWN: null failure" : message;
        if (safe.length() > 4000) {
            safe = safe.substring(0, 4000) + "…";
        }
        failureEvents.incrementAndGet();
        if (safe.startsWith("HANDLER_UNLOADED:")) {
            unloadedHandlerCategories++;
        } else if (safe.startsWith("HANDLER_AMBIGUOUS:")) {
            ambiguousHandlerCategories++;
        } else if (safe.startsWith("HANDLER_DUPLICATE:")) {
            duplicateHandlerCategories++;
        }
        synchronized (failures) {
            if (failures.size() < MAX_FAILURE_SAMPLES) {
                failures.add(safe);
            } else {
                omittedFailureEvents.incrementAndGet();
            }
        }
        GtnhNeiExportMod.LOGGER.error("[gtnh-nei-export] {}", safe);
    }

    int failureCount() {
        return failureEvents.get();
    }

    void finishResources() throws IOException {
        if (resourcesClosed) {
            return;
        }
        resourcesClosed = true;
        IOException failure = null;
        try {
            catalog.close();
        } catch (IOException error) {
            failure = error;
        }
        renderer.close();
        try {
            pngWriter.finish();
        } catch (IOException error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    void writeFinalMetadata(boolean aborted, long durationMillis) throws IOException {
        exporterBuildIdentity.writeTo(root);
        writeCategories();
        writeIndex();
        writeEmptyDatasets();
        writeFailures();
        writeManifest(aborted, durationMillis);
    }

    private void writeCategories() throws IOException {
        try (JsonWriter writer = jsonWriter(root.resolve("categories.json"))) {
            writer.beginObject().name("categories").beginArray();
            for (CategoryMeta category : categories) {
                category.write(writer);
            }
            writer.endArray().endObject();
        }
    }

    private void writeIndex() throws IOException {
        try (JsonWriter writer = jsonWriter(root.resolve("index.json"))) {
            writer.beginObject();
            for (Map.Entry<String, PrimitiveRefs> entry : reverseIndex.entrySet()) {
                writer.name(entry.getKey()).beginObject();
                entry.getValue().write(writer);
                writer.endObject();
            }
            writer.endObject();
        }
    }

    private void writeEmptyDatasets() throws IOException {
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] mobs.json and blockdrops.json are explicitly empty; this module exports NEI items/recipes");
        try (JsonWriter writer = jsonWriter(root.resolve("mobs.json"))) {
            writer.beginObject().name("mobs").beginArray().endArray().endObject();
        }
        try (JsonWriter writer = jsonWriter(root.resolve("blockdrops.json"))) {
            writer.beginObject().name("blocks").beginObject().endObject().endObject();
        }
    }

    private void writeFailures() throws IOException {
        List<String> copy;
        synchronized (failures) {
            copy = new ArrayList<String>(failures);
        }
        try (JsonWriter writer = jsonWriter(root.resolve("failures.json"))) {
            writer.beginArray();
            for (String failure : copy) {
                writer.value(failure);
            }
            writer.endArray();
        }
    }

    private void writeManifest(boolean aborted, long durationMillis) throws IOException {
        try (JsonWriter writer = jsonWriter(root.resolve("manifest.json"))) {
            writer.setIndent("  ");
            writer.beginObject();
            // GTNH format 2 extends output slot tuples with an optional fourth probability
            // field. Other exporters and already-published datasets remain on format 1.
            writer.name("format").value(2);
            writer.name("profile").value("gtnh-1.7.10");
            writer.name("generatedAt").value(Instant.now().toString());
            writer.name("durationMs").value(durationMillis);
            writer.name("aborted").value(aborted);
            writer.name("minecraft").value("1.7.10");
            writer.name("forge").value("10.13.4.1614");
            writer.name("nei").value("2.8.44-GTNH");
            writer.name("pack").beginObject();
            writer.name("name").value(ExportRequest.PACK_NAME);
            writer.name("version").value(ExportRequest.PACK_VERSION);
            writer.name("identitySource").value("explicit-request");
            writer.endObject();
            writer.name("attribution").beginObject();
            writer.name("sourceUrl").value(ManifestContract.ATTRIBUTION_SOURCE_URL);
            writer.name("projectUrl").value(ManifestContract.ATTRIBUTION_PROJECT_URL);
            writer.name("licenseIdentifier").value(
                    ManifestContract.ATTRIBUTION_LICENSE_IDENTIFIER);
            writer.name("licenseUrl").value(ManifestContract.ATTRIBUTION_LICENSE_URL);
            writer.endObject();
            writer.name("settings").beginObject();
            writer.name("iconScale").value(ExportRequest.ICON_SCALE);
            writer.name("recipeScale").value(ExportRequest.RECIPE_SCALE);
            writer.name("mobCanvas").value(ExportRequest.MOB_CANVAS);
            writer.endObject();
            writer.name("handlerPolicies").beginArray();
            for (CompleteCategoryAdapters.Policy policy : handlerPolicies) {
                policy.write(writer);
            }
            writer.endArray();
            writer.name("knowledgePolicy").beginObject();
            writer.name("playerResearchMutated").value(false);
            writer.name("thaumcraftLockedRecipes").value("required-by-pinned-config");
            writer.name("itemAspectDisplayNames").value("nbt-aspect-registry-v1");
            writer.name("forestryScannedSaplingDisplayName").value(
                    DisplayNameResolver.FORESTRY_SCANNED_SAPLING_NAME_CONTRACT);
            writer.name("forestryScannedSaplingSourceBinding").value(
                    GregTechForestryScannedSaplingPreflight.CONTRACT);
            writer.name("forestryScannedPollenDisplayName").value(
                    DisplayNameResolver.FORESTRY_SCANNED_POLLEN_NAME_CONTRACT);
            writer.name("forestryScannedPollenSourceBinding").value(
                    GregTechForestryScannedPollenPreflight.CONTRACT);
            writer.name("itemAspectRecipeSemantics").value(
                    TcnaAspectCostSemanticNormalizer.CONTRACT);
            writer.name("gregTechOutputlessRecipeSemantics").value(
                    GregTechOutputlessSemanticPreflight.CONTRACT);
            writer.name("gregTechStaleDoorRecyclingExclusion").value(
                    GregTechOutputlessSemanticPreflight
                            .UNREGISTERED_DOOR_RECYCLING_CONTRACT);
            writer.name("ownerInternalFurnaceFuelRowExclusion").value(
                    CatalogExcludedFuelPreflight.CONTRACT);
            writer.name("ae2InternalFacadeRecipeExclusion").value(
                    Ae2InternalFacadeRecipePreflight.CONTRACT);
            writer.name("gendustryMachineRecipeSemantics").value(
                    GendustryMachineSemanticAdapter.CONTRACT);
            writer.endObject();
            writer.name("counts").beginObject();
            writer.name("items").value(catalog.count());
            writer.name("recipes").value(recipesEnumerated);
            writer.name("categories").value(categories.size());
            writer.name("mobs").value(0);
            writer.name("blockDrops").value(0);
            writer.name("failures").value(failures.size());
            writer.endObject();
            writer.name("diagnostics").beginObject();
            writer.name("failureEvents").value(failureEvents.get());
            writer.name("failureEventsOmitted").value(omittedFailureEvents.get());
            writer.name("nei").beginObject();
            writer.name("itemListLoaded").value(itemListLoaded);
            writer.name("itemListRawEntries").value(itemListRawEntries);
            writer.name("itemListExcludedEntries").value(itemListExcludedEntries);
            writer.name("itemListRetainedEntries").value(itemListRetainedEntries);
            writer.name("itemListRetainedUniqueIdentities")
                    .value(itemListRetainedUniqueIdentities);
            writer.name("registeredCraftingHandlers").value(registeredCraftingHandlers);
            writer.name("exportableCraftingHandlers").value(exportableCraftingHandlers);
            writer.name("adaptedHandlerCategories").value(adaptedHandlerCategories);
            writer.name("excludedNonRecipeHandlers").value(excludedNonRecipeHandlers);
            writer.name("excludedEmptyRecipeHandlers").value(excludedEmptyRecipeHandlers);
            writer.name("excludedUnboundTemplateRecipeHandlers")
                    .value(excludedUnboundTemplateRecipeHandlers);
            writer.name("excludedAe2fcFluidDropItemListPlaceholders")
                    .value(excludedAe2fcFluidDropItemListPlaceholders);
            writer.name("excludedAe2fcFluidPacketItemListPlaceholders")
                    .value(excludedAe2fcFluidPacketItemListPlaceholders);
            writer.name("excludedAe2CableBusInternalBlockItemListEntries")
                    .value(excludedAe2CableBusInternalBlockItemListEntries);
            writer.name("excludedAe2MatrixFrameInternalBlockItemListEntries")
                    .value(excludedAe2MatrixFrameInternalBlockItemListEntries);
            writer.name("excludedBloodMagicBloodLightItemListHelpers")
                    .value(excludedBloodMagicBloodLightItemListHelpers);
            writer.name("excludedBloodMagicSpectralContainerItemListHelpers")
                    .value(excludedBloodMagicSpectralContainerItemListHelpers);
            writer.name("excludedArchitectureCraftCladdingItemListPlaceholders")
                    .value(excludedArchitectureCraftCladdingItemListPlaceholders);
            writer.name("excludedAvaritiaEmptyMatterClusterItemListPlaceholders")
                    .value(excludedAvaritiaEmptyMatterClusterItemListPlaceholders);
            writer.name("excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders")
                    .value(excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders);
            writer.name("excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries")
                    .value(excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries);
            writer.name("excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders")
                    .value(excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders);
            writer.name("malisisDoorsUnconfiguredCustomDoorRecipeReferences")
                    .value(malisisDoorsUnconfiguredCustomDoorRecipeReferences);
            writer.name("malisisDoorsUnconfiguredCustomDoorQuestReferences")
                    .value(malisisDoorsUnconfiguredCustomDoorQuestReferences);
            writer.name("excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders")
                    .value(excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders);
            writer.name("malisisDoorsUnconfiguredMixedBlockRecipeReferences")
                    .value(malisisDoorsUnconfiguredMixedBlockRecipeReferences);
            writer.name("malisisDoorsUnconfiguredMixedBlockQuestReferences")
                    .value(malisisDoorsUnconfiguredMixedBlockQuestReferences);
            writer.name("excludedBotaniaBifrostItemListWorldStateEntries")
                    .value(excludedBotaniaBifrostItemListWorldStateEntries);
            writer.name("excludedBotaniaBuriedPetalsItemListWorldStateVariants")
                    .value(excludedBotaniaBuriedPetalsItemListWorldStateVariants);
            writer.name("excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask")
                    .value(excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask);
            writer.name("excludedBotaniaCacophoniumBlockItemListWorldStateEntries")
                    .value(excludedBotaniaCacophoniumBlockItemListWorldStateEntries);
            writer.name("excludedBotaniaEnchanterItemListWorldStateEntries")
                    .value(excludedBotaniaEnchanterItemListWorldStateEntries);
            writer.name("excludedBotaniaFakeAirItemListWorldStateEntries")
                    .value(excludedBotaniaFakeAirItemListWorldStateEntries);
            writer.name("excludedBotaniaManaFlameItemListWorldStateEntries")
                    .value(excludedBotaniaManaFlameItemListWorldStateEntries);
            writer.name("excludedBotaniaSolidVineItemListWorldStateEntries")
                    .value(excludedBotaniaSolidVineItemListWorldStateEntries);
            writer.name("excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders")
                    .value(excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders);
            writer.name("excludedCarpentersBedInternalBlockItemListEntries")
                    .value(excludedCarpentersBedInternalBlockItemListEntries);
            writer.name("excludedCarpentersDoorInternalBlockItemListEntries")
                    .value(excludedCarpentersDoorInternalBlockItemListEntries);
            writer.name("excludedStevesCartsUnconfiguredModularCartItemListPlaceholders")
                    .value(excludedStevesCartsUnconfiguredModularCartItemListPlaceholders);
            writer.name("excludedTConstructBattleSignInternalBlockItemListEntries")
                    .value(excludedTConstructBattleSignInternalBlockItemListEntries);
            writer.name("excludedTConstructHeldItemInternalBlockItemListEntries")
                    .value(excludedTConstructHeldItemInternalBlockItemListEntries);
            writer.name("excludedThaumcraftBlockHoleInternalBlockItemListEntries")
                    .value(excludedThaumcraftBlockHoleInternalBlockItemListEntries);
            writer.name("excludedThaumcraftEldritchPortalInternalBlockItemListEntries")
                    .value(excludedThaumcraftEldritchPortalInternalBlockItemListEntries);
            writer.name("excludedThaumicHorizonsBaseLightInternalBlockItemListEntries")
                    .value(excludedThaumicHorizonsBaseLightInternalBlockItemListEntries);
            writer.name("excludedThaumicHorizonsSolarLightInternalBlockItemListEntries")
                    .value(excludedThaumicHorizonsSolarLightInternalBlockItemListEntries);
            writer.name("excludedTwilightForestExperiment115InternalBlockItemListEntries")
                    .value(excludedTwilightForestExperiment115InternalBlockItemListEntries);
            writer.name("excludedWitchingGadgetsCustomAirInternalBlockItemListEntries")
                    .value(excludedWitchingGadgetsCustomAirInternalBlockItemListEntries);
            writer.name("adaptedBotaniaCocoonItemIcons")
                    .value(adaptedBotaniaCocoonItemIcons);
            writer.name("adaptedBotaniaCocoonRecipeWidgetRenderInvocations")
                    .value(adaptedBotaniaCocoonRecipeWidgetRenderInvocations);
            writer.name("adaptedBotaniaPrismItemIcons")
                    .value(adaptedBotaniaPrismItemIcons);
            writer.name("adaptedBotaniaPrismRecipeWidgetRenderInvocations")
                    .value(adaptedBotaniaPrismRecipeWidgetRenderInvocations);
            writer.name("adaptedGalacticraftFlagItemIcons")
                    .value(adaptedGalacticraftFlagItemIcons);
            writer.name("adaptedGalacticraftFlagRecipeWidgetRenderInvocations")
                    .value(adaptedGalacticraftFlagRecipeWidgetRenderInvocations);
            writer.name("adaptedWrcbeTriangulatorItemIcons")
                    .value(adaptedWrcbeTriangulatorItemIcons);
            writer.name("adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations")
                    .value(adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations);
            writer.name("adaptedModernMarkingsCrossingItemIcons")
                    .value(adaptedModernMarkingsCrossingItemIcons);
            writer.name("adaptedThaumcraftRunedStoneItemIcons")
                    .value(adaptedThaumcraftRunedStoneItemIcons);
            writer.name("adaptedForestryScannedSaplingDisplayNames")
                    .value(adaptedForestryScannedSaplingDisplayNames);
            writer.name("gregTechForestryScannedSaplingRecipeOccurrences")
                    .value(gregTechForestryScannedSaplingRecipeOccurrences);
            writer.name("adaptedForestryScannedPollenDisplayNames")
                    .value(adaptedForestryScannedPollenDisplayNames);
            writer.name("gregTechForestryScannedPollenRecipeOccurrences")
                    .value(gregTechForestryScannedPollenRecipeOccurrences);
            writer.name("adaptedProjectBlueControlPanelItemIcons")
                    .value(adaptedProjectBlueControlPanelItemIcons);
            writer.name("adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations")
                    .value(adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations);
            writer.name("adaptedIc2FluidCannerRecipeWidgetRenderInvocations")
                    .value(adaptedIc2FluidCannerRecipeWidgetRenderInvocations);
            writer.name("adaptedBuildCraftPhasedFacadeItemIcons")
                    .value(adaptedBuildCraftPhasedFacadeItemIcons);
            writer.name("adaptedMobsInfoInfernalPreviewOutputIcons")
                    .value(adaptedMobsInfoInfernalPreviewOutputIcons);
            writer.name("adaptedMobsInfoPreviewSlotIcons")
                    .value(adaptedMobsInfoPreviewSlotIcons);
            writer.name("adaptedDraconicMobSoulItemIcons")
                    .value(adaptedDraconicMobSoulItemIcons);
            writer.name("normalizedTcnaAspectCostInputOccurrences")
                    .value(normalizedTcnaAspectCostInputOccurrences);
            writer.name("normalizedTcnaAspectCostDistinctKeys")
                    .value(normalizedTcnaAspectCostDistinctKeys);
            writer.name("normalizedTcnaAspectCostHandlerCategories")
                    .value(normalizedTcnaAspectCostHandlerCategories);
            writer.name("adaptedGendustryLiquifierRecipes")
                    .value(adaptedGendustryLiquifierRecipes);
            writer.name("adaptedGendustryMutagenProducerRecipes")
                    .value(adaptedGendustryMutagenProducerRecipes);
            writer.name("adaptedGendustryExtractorRecipes")
                    .value(adaptedGendustryExtractorRecipes);
            writer.name("adaptedGendustryReplicatorRecipes")
                    .value(adaptedGendustryReplicatorRecipes);
            writer.name("adaptedGendustryTransposerRecipes")
                    .value(adaptedGendustryTransposerRecipes);
            writer.name("adaptedGendustryMutatronRecipes")
                    .value(adaptedGendustryMutatronRecipes);
            writer.name("adaptedGendustrySamplerRecipes")
                    .value(adaptedGendustrySamplerRecipes);
            writer.name("adaptedGendustryImprinterRecipes")
                    .value(adaptedGendustryImprinterRecipes);
            writer.name("loadedCategories").value(loadedCategories);
            writer.name("recipesEnumerated").value(recipesEnumerated);
            writer.name("recipeWidgetsRendered").value(recipeWidgetsRendered);
            writer.name("itemIconsRendered").value(itemIconsRendered);
            writer.name("informationalEmptyOutputRecipes").value(informationalEmptyOutputRecipes);
            writer.name("gregTechFuelSinkRecipes").value(gregTechFuelSinkRecipes);
            writer.name("gregTechFuelSinkCategories").value(gregTechFuelSinkCategories);
            writer.name("gregTechLargeBoilerFuelSinkRecipes")
                    .value(gregTechLargeBoilerFuelSinkRecipes);
            writer.name("gregTechLargeBoilerFuelSinkCategories")
                    .value(gregTechLargeBoilerFuelSinkCategories);
            writer.name("gregTechRadioHatchInformationRecipes")
                    .value(gregTechRadioHatchInformationRecipes);
            writer.name("gregTechQuantumComponentInformationRecipes")
                    .value(gregTechQuantumComponentInformationRecipes);
            writer.name("gregTechSpaceProjectInformationRecipes")
                    .value(gregTechSpaceProjectInformationRecipes);
            writer.name("gregTechOutputlessSemanticCategories")
                    .value(gregTechOutputlessSemanticCategories);
            writer.name("gregTechOutputlessSemanticRecipes")
                    .value(gregTechOutputlessSemanticRecipes);
            writer.name("excludedGregTechLargeBoilerPresentationRows")
                    .value(excludedGregTechLargeBoilerPresentationRows);
            writer.name("excludedGregTechUnregisteredDoorRecyclingRows")
                    .value(excludedGregTechUnregisteredDoorRecyclingRows);
            writer.name("excludedOwnerInternalFurnaceFuelRows")
                    .value(excludedOwnerInternalFurnaceFuelRows);
            writer.name("excludedAe2EnderIoInternalConduitFacadeRows")
                    .value(excludedAe2EnderIoInternalConduitFacadeRows);
            writer.name("excludedUnregisteredGregTechMachineCatalysts")
                    .value(excludedUnregisteredGregTechMachineCatalysts);
            writer.name("knowledgeIndependentAspectNames").value(knowledgeIndependentAspectNames);
            writer.name("unloadedHandlerCategories").value(unloadedHandlerCategories);
            writer.name("ambiguousHandlerCategories").value(ambiguousHandlerCategories);
            writer.name("duplicateHandlerCategories").value(duplicateHandlerCategories);
            writer.endObject();
            writer.endObject();
            writer.name("mods").beginObject();
            java.util.HashSet<String> seenModIds = new java.util.HashSet<String>();
            for (ModContainer mod : Loader.instance().getActiveModList()) {
                String modId = requiredModText(mod.getModId(), "mod ID");
                String modName = requiredModText(mod.getName(), "display name for " + modId);
                if (!seenModIds.add(modId)) {
                    throw new IOException("Duplicate active mod ID in manifest: " + modId);
                }
                writer.name(modId).value(modName);
            }
            writer.endObject();
            writer.endObject();
        }
    }

    static String requiredModText(String value, String field) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("Active mod " + field + " must be non-blank");
        }
        return value;
    }

    static JsonWriter jsonWriter(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        return new JsonWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8));
    }
}
