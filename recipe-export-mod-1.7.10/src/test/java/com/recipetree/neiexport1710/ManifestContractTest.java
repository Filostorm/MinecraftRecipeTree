package com.recipetree.neiexport1710;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ManifestContractTest {
    @Test
    public void acceptsExactPublishedContract() throws Exception {
        JsonObject manifest = validManifest();
        assertEquals(102, manifest.getAsJsonObject("diagnostics")
                .getAsJsonObject("nei").entrySet().size());
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingItemListUniqueIdentityTelemetry() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .remove("itemListRetainedUniqueIdentities");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsFinalUnionCatalogSmallerThanRetainedUniqueItemList() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("counts").addProperty("items", 55990);
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("itemIconsRendered", 55990);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsScalarPackIdentity() throws Exception {
        JsonObject manifest = validManifest();
        manifest.addProperty("pack", "2.8.4");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingAttribution() throws Exception {
        JsonObject manifest = validManifest();
        manifest.remove("attribution");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsAttributionLicenseDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("attribution")
                .addProperty("licenseIdentifier", "CC BY 4.0");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsUncoordinatedAttributionKeys() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("attribution")
                .addProperty("allModArtworkCovered", true);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsUncoordinatedDiagnosticKeys() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").addProperty("silentFallbacks", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsCoverageMismatch() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("recipeWidgetsRendered", 6);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsAe2fcCatalogPlaceholderCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("excludedAe2fcFluidDropItemListPlaceholders", 2);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsBloodMagicCatalogHelperCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("excludedBloodMagicSpectralContainerItemListHelpers", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsArchitectureCraftCatalogPlaceholderCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("excludedArchitectureCraftCladdingItemListPlaceholders", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsAvaritiaCatalogPlaceholderCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("excludedAvaritiaEmptyMatterClusterItemListPlaceholders", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsDreamcraftNothingSentinelCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingDreamcraftNothingSentinelCardinality() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .remove("excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsLittleTilesCarrierCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingLittleTilesCarrierCardinality() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .remove("excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMalisisDoorsCarrierCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingMalisisDoorsCarrierCardinality() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .remove("excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMalisisDoorsRecipeGraphReference() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("malisisDoorsUnconfiguredCustomDoorRecipeReferences", 1);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMalisisDoorsQuestGraphReference() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("malisisDoorsUnconfiguredCustomDoorQuestReferences", 1);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMalisisDoorsMixedBlockCarrierCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingMalisisDoorsMixedBlockCarrierCardinality() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .remove("excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMalisisDoorsMixedBlockRecipeGraphReference() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("malisisDoorsUnconfiguredMixedBlockRecipeReferences", 1);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMalisisDoorsMixedBlockQuestGraphReference() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("malisisDoorsUnconfiguredMixedBlockQuestReferences", 1);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsCarpentersDoorInternalBlockCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("excludedCarpentersDoorInternalBlockItemListEntries", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsCarpentersBedInternalBlockCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("excludedCarpentersBedInternalBlockItemListEntries", 2);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsStevesCartsPlaceholderCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedStevesCartsUnconfiguredModularCartItemListPlaceholders", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsTConstructBattleSignInternalBlockCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedTConstructBattleSignInternalBlockItemListEntries", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsTConstructHeldItemInternalBlockCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedTConstructHeldItemInternalBlockItemListEntries", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsThaumcraftBlockHoleInternalBlockCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedThaumcraftBlockHoleInternalBlockItemListEntries", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsThaumcraftEldritchPortalInternalBlockCardinalityDrift()
            throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedThaumcraftEldritchPortalInternalBlockItemListEntries", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsThaumicHorizonsSolarLightInternalBlockCardinalityDrift()
            throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedThaumicHorizonsSolarLightInternalBlockItemListEntries", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsThaumicHorizonsBaseLightInternalBlockCardinalityDrift()
            throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedThaumicHorizonsBaseLightInternalBlockItemListEntries", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsTwilightForestExperiment115InternalBlockCardinalityDrift()
            throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedTwilightForestExperiment115InternalBlockItemListEntries", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsWitchingGadgetsCustomAirInternalBlockCardinalityDrift()
            throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedWitchingGadgetsCustomAirInternalBlockItemListEntries", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingWitchingGadgetsCustomAirInternalBlockCardinality()
            throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .remove("excludedWitchingGadgetsCustomAirInternalBlockItemListEntries");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsAe2CableBusInternalBlockCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("excludedAe2CableBusInternalBlockItemListEntries", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingAe2CableBusInternalBlockCardinality() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .remove("excludedAe2CableBusInternalBlockItemListEntries");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsAe2MatrixFrameInternalBlockCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("excludedAe2MatrixFrameInternalBlockItemListEntries", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingAe2MatrixFrameInternalBlockCardinality() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .remove("excludedAe2MatrixFrameInternalBlockItemListEntries");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsBotaniaWorldStateCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("excludedBotaniaBifrostItemListWorldStateEntries", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsBotaniaBuriedPetalMetadataMaskDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask", 0x7fff);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsBotaniaStructureLibPlaceholderCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsBotaniaCocoonIconAdapterCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedBotaniaCocoonItemIcons", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingBotaniaCocoonRecipeWidgetAdapterCoverage() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedBotaniaCocoonRecipeWidgetRenderInvocations", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsBotaniaPrismIconAdapterCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedBotaniaPrismItemIcons", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingBotaniaPrismRecipeWidgetAdapterCoverage() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedBotaniaPrismRecipeWidgetRenderInvocations", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsGalacticraftFlagIconAdapterCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedGalacticraftFlagItemIcons", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingGalacticraftFlagRecipeWidgetAdapterCoverage() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedGalacticraftFlagRecipeWidgetRenderInvocations", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsWrcbeTriangulatorIconAdapterCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedWrcbeTriangulatorItemIcons", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingWrcbeTriangulatorRecipeWidgetAdapterCoverage() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsModernMarkingsCrossingIconAdapterCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedModernMarkingsCrossingItemIcons", 5);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsThaumcraftRunedStoneIconAdapterCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedThaumcraftRunedStoneItemIcons", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsForestryScannedSaplingNameAdapterCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedForestryScannedSaplingDisplayNames", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsGregTechForestryScannedSaplingOccurrenceCardinalityDrift()
            throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("gregTechForestryScannedSaplingRecipeOccurrences", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsForestryScannedPollenNameAdapterCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedForestryScannedPollenDisplayNames", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsGregTechForestryScannedPollenOccurrenceCardinalityDrift()
            throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("gregTechForestryScannedPollenRecipeOccurrences", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsProjectBlueControlPanelIconAdapterCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedProjectBlueControlPanelItemIcons", 2);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsProjectBlueControlPanelWidgetAdapterCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty(
                        "adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations", 2);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsBuildCraftPhasedFacadeIconAdapterCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("adaptedBuildCraftPhasedFacadeItemIcons", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingTcnaAspectCostOccurrences() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("normalizedTcnaAspectCostInputOccurrences", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingTcnaAspectCostDistinctKeys() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("normalizedTcnaAspectCostDistinctKeys", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsTcnaAspectCostHandlerCoverageDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("normalizedTcnaAspectCostHandlerCategories", 3);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsItemAspectCatalogCardinalityDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("knowledgeIndependentAspectNames", 68);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsTcnaAspectRecipeSemanticPolicyDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("knowledgePolicy")
                .addProperty("itemAspectRecipeSemantics", "drifted-policy");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsForestryScannedSaplingSourceBindingPolicyDrift()
            throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("knowledgePolicy")
                .addProperty("forestryScannedSaplingSourceBinding", "drifted-policy");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsForestryScannedPollenDisplayNamePolicyDrift()
            throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("knowledgePolicy")
                .addProperty("forestryScannedPollenDisplayName", "drifted-policy");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsForestryScannedPollenSourceBindingPolicyDrift()
            throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("knowledgePolicy")
                .addProperty("forestryScannedPollenSourceBinding", "drifted-policy");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsGregTechOutputlessSemanticPolicyDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("knowledgePolicy")
                .addProperty("gregTechOutputlessRecipeSemantics", "drifted-policy");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsGregTechStaleDoorExclusionPolicyDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("knowledgePolicy")
                .addProperty("gregTechStaleDoorRecyclingExclusion", "drifted-policy");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsGregTechStaleDoorExclusionTelemetryDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("excludedGregTechUnregisteredDoorRecyclingRows",
                        GregTechOutputlessSemanticPreflight
                                .EXPECTED_UNREGISTERED_DOOR_RECYCLING_ROWS - 1);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsUnregisteredGregTechMachineCatalystTelemetryDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("excludedUnregisteredGregTechMachineCatalysts", 0);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsGregTechOutputlessRecipeTelemetryDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("gregTechOutputlessSemanticRecipes",
                        GregTechOutputlessSemanticPreflight.EXPECTED_SEMANTIC_RECIPES - 1);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsGregTechFuelRecipeTelemetryDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("gregTechFuelSinkRecipes",
                        GregTechOutputlessSemanticPreflight.EXPECTED_FUEL_SINK_RECIPES - 1);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsGregTechSemanticCategoryTelemetryDrift() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("diagnostics").getAsJsonObject("nei")
                .addProperty("gregTechOutputlessSemanticCategories",
                        GregTechOutputlessSemanticPreflight.EXPECTED_SEMANTIC_CATEGORIES + 1);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsResearchMutation() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("knowledgePolicy")
                .addProperty("playerResearchMutated", true);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingPinnedHandlerPolicy() throws Exception {
        JsonObject manifest = validManifest();
        JsonArray complete = manifest.getAsJsonArray("handlerPolicies");
        JsonArray incomplete = new JsonArray();
        for (int index = 1; index < complete.size(); index++) {
            incomplete.add(complete.get(index));
        }
        manifest.add("handlerPolicies", incomplete);
        ManifestContract.validatePublished(manifest);
    }

    @Test
    public void acceptsRepeatedRuntimeClassWithDistinctHandlerIds() throws Exception {
        JsonObject manifest = validManifest();
        JsonArray policies = manifest.getAsJsonArray("handlerPolicies");
        Set<String> rocketHandlerIds = new HashSet<String>();
        for (int index = 0; index < policies.size(); index++) {
            JsonObject policy = policies.get(index).getAsJsonObject();
            if (CompleteCategoryAdapters.GALAXYSPACE_ROCKET_HANDLER.equals(
                    policy.get("handlerClass").getAsString())) {
                rocketHandlerIds.add(policy.get("handlerId").getAsString());
            }
        }
        assertEquals(8, rocketHandlerIds.size());
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsLegacyClassEqualHandlerIdForRepeatedRuntimeClass() throws Exception {
        JsonObject manifest = validManifest();
        JsonObject policy = firstPolicyForClass(manifest,
                CompleteCategoryAdapters.GALAXYSPACE_ROCKET_HANDLER);
        policy.addProperty("handlerId", CompleteCategoryAdapters.GALAXYSPACE_ROCKET_HANDLER);
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsDuplicateCompositeHandlerPolicy() throws Exception {
        JsonObject manifest = validManifest();
        JsonArray policies = manifest.getAsJsonArray("handlerPolicies");
        policies.add(policies.get(0));
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsLegacyCropCacheContract() throws Exception {
        JsonObject manifest = validManifest();
        policy(manifest, CompleteCategoryAdapters.CROP_HANDLER)
                .addProperty("contract", "adapter:ic2-crop-complete-breeder-cache-v1");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsLoreContaminatedCropGraphContract() throws Exception {
        JsonObject manifest = validManifest();
        policy(manifest, CompleteCategoryAdapters.CROP_HANDLER)
                .addProperty("contract", "adapter:ic2-crop-lore-stack-graph-v1");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingMobCanvasContract() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("settings").remove("mobCanvas");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsBlankModDisplayName() throws Exception {
        JsonObject manifest = validManifest();
        manifest.getAsJsonObject("mods").addProperty("gregtech", "  ");
        ManifestContract.validatePublished(manifest);
    }

    @Test(expected = IOException.class)
    public void rejectsBlankActiveModTextBeforeSerialization() throws Exception {
        ExportContext.requiredModText("\t", "display name for gregtech");
    }

    private static JsonObject validManifest() {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", 2);
        manifest.addProperty("profile", "gtnh-1.7.10");
        manifest.addProperty("aborted", false);
        manifest.addProperty("minecraft", "1.7.10");
        manifest.addProperty("forge", "10.13.4.1614");
        manifest.addProperty("nei", "2.8.44-GTNH");

        JsonObject pack = new JsonObject();
        pack.addProperty("name", "GT New Horizons");
        pack.addProperty("version", "2.8.4");
        pack.addProperty("identitySource", "explicit-request");
        manifest.add("pack", pack);

        JsonObject attribution = new JsonObject();
        attribution.addProperty("sourceUrl", ManifestContract.ATTRIBUTION_SOURCE_URL);
        attribution.addProperty("projectUrl", ManifestContract.ATTRIBUTION_PROJECT_URL);
        attribution.addProperty(
                "licenseIdentifier", ManifestContract.ATTRIBUTION_LICENSE_IDENTIFIER);
        attribution.addProperty("licenseUrl", ManifestContract.ATTRIBUTION_LICENSE_URL);
        manifest.add("attribution", attribution);

        JsonObject settings = new JsonObject();
        settings.addProperty("iconScale", 3);
        settings.addProperty("recipeScale", 2);
        settings.addProperty("mobCanvas", 256);
        manifest.add("settings", settings);

        JsonArray handlerPolicies = new JsonArray();
        for (CompleteCategoryAdapters.Policy policy
                : CompleteCategoryAdapters.expectedPoliciesForContract()) {
            addPolicy(handlerPolicies, policy.handlerClass, policy.handlerId,
                    policy.action, policy.contract);
        }
        manifest.add("handlerPolicies", handlerPolicies);

        JsonObject knowledgePolicy = new JsonObject();
        knowledgePolicy.addProperty("playerResearchMutated", false);
        knowledgePolicy.addProperty("thaumcraftLockedRecipes", "required-by-pinned-config");
        knowledgePolicy.addProperty("itemAspectDisplayNames", "nbt-aspect-registry-v1");
        knowledgePolicy.addProperty("forestryScannedSaplingDisplayName",
                DisplayNameResolver.FORESTRY_SCANNED_SAPLING_NAME_CONTRACT);
        knowledgePolicy.addProperty("forestryScannedSaplingSourceBinding",
                GregTechForestryScannedSaplingPreflight.CONTRACT);
        knowledgePolicy.addProperty("forestryScannedPollenDisplayName",
                DisplayNameResolver.FORESTRY_SCANNED_POLLEN_NAME_CONTRACT);
        knowledgePolicy.addProperty("forestryScannedPollenSourceBinding",
                GregTechForestryScannedPollenPreflight.CONTRACT);
        knowledgePolicy.addProperty("itemAspectRecipeSemantics",
                TcnaAspectCostSemanticNormalizer.CONTRACT);
        knowledgePolicy.addProperty("gregTechOutputlessRecipeSemantics",
                GregTechOutputlessSemanticPreflight.CONTRACT);
        knowledgePolicy.addProperty("gregTechStaleDoorRecyclingExclusion",
                GregTechOutputlessSemanticPreflight
                        .UNREGISTERED_DOOR_RECYCLING_CONTRACT);
        knowledgePolicy.addProperty("ownerInternalFurnaceFuelRowExclusion",
                CatalogExcludedFuelPreflight.CONTRACT);
        knowledgePolicy.addProperty("ae2InternalFacadeRecipeExclusion",
                Ae2InternalFacadeRecipePreflight.CONTRACT);
        knowledgePolicy.addProperty("gendustryMachineRecipeSemantics",
                GendustryMachineSemanticAdapter.CONTRACT);
        manifest.add("knowledgePolicy", knowledgePolicy);

        JsonObject counts = new JsonObject();
        counts.addProperty("items", 55991);
        counts.addProperty("recipes", 7);
        counts.addProperty("categories", 2);
        counts.addProperty("mobs", 0);
        counts.addProperty("blockDrops", 0);
        counts.addProperty("failures", 0);
        manifest.add("counts", counts);

        JsonObject diagnostics = new JsonObject();
        diagnostics.addProperty("failureEvents", 0);
        diagnostics.addProperty("failureEventsOmitted", 0);
        JsonObject nei = new JsonObject();
        nei.addProperty("itemListLoaded", true);
        nei.addProperty("itemListRawEntries", 56038);
        nei.addProperty("itemListExcludedEntries", 46);
        nei.addProperty("itemListRetainedEntries", 55992);
        nei.addProperty("itemListRetainedUniqueIdentities", 55991);
        nei.addProperty("registeredCraftingHandlers", 45);
        nei.addProperty("exportableCraftingHandlers", 2);
        nei.addProperty("adaptedHandlerCategories", 45);
        nei.addProperty("excludedNonRecipeHandlers", 20);
        nei.addProperty("excludedEmptyRecipeHandlers", 22);
        nei.addProperty("excludedUnboundTemplateRecipeHandlers", 1);
        nei.addProperty("excludedAe2fcFluidDropItemListPlaceholders", 1);
        nei.addProperty("excludedAe2fcFluidPacketItemListPlaceholders", 1);
        nei.addProperty("excludedAe2CableBusInternalBlockItemListEntries", 1);
        nei.addProperty("excludedAe2MatrixFrameInternalBlockItemListEntries", 1);
        nei.addProperty("excludedBloodMagicBloodLightItemListHelpers", 1);
        nei.addProperty("excludedBloodMagicSpectralContainerItemListHelpers", 1);
        nei.addProperty("excludedArchitectureCraftCladdingItemListPlaceholders", 1);
        nei.addProperty("excludedAvaritiaEmptyMatterClusterItemListPlaceholders", 1);
        nei.addProperty(
                "excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders", 1);
        nei.addProperty(
                "excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries", 1);
        nei.addProperty(
                "excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders", 1);
        nei.addProperty("malisisDoorsUnconfiguredCustomDoorRecipeReferences", 0);
        nei.addProperty("malisisDoorsUnconfiguredCustomDoorQuestReferences", 0);
        nei.addProperty(
                "excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders", 1);
        nei.addProperty("malisisDoorsUnconfiguredMixedBlockRecipeReferences", 0);
        nei.addProperty("malisisDoorsUnconfiguredMixedBlockQuestReferences", 0);
        nei.addProperty("excludedBotaniaBifrostItemListWorldStateEntries", 1);
        nei.addProperty("excludedBotaniaBuriedPetalsItemListWorldStateVariants", 16);
        nei.addProperty(
                "excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask", 0xffff);
        nei.addProperty("excludedBotaniaCacophoniumBlockItemListWorldStateEntries", 1);
        nei.addProperty("excludedBotaniaEnchanterItemListWorldStateEntries", 1);
        nei.addProperty("excludedBotaniaFakeAirItemListWorldStateEntries", 1);
        nei.addProperty("excludedBotaniaManaFlameItemListWorldStateEntries", 1);
        nei.addProperty("excludedBotaniaSolidVineItemListWorldStateEntries", 1);
        nei.addProperty(
                "excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders", 1);
        nei.addProperty("excludedCarpentersBedInternalBlockItemListEntries", 1);
        nei.addProperty("excludedCarpentersDoorInternalBlockItemListEntries", 1);
        nei.addProperty(
                "excludedStevesCartsUnconfiguredModularCartItemListPlaceholders", 1);
        nei.addProperty("excludedTConstructBattleSignInternalBlockItemListEntries", 1);
        nei.addProperty("excludedTConstructHeldItemInternalBlockItemListEntries", 1);
        nei.addProperty("excludedThaumcraftBlockHoleInternalBlockItemListEntries", 1);
        nei.addProperty("excludedThaumcraftEldritchPortalInternalBlockItemListEntries", 1);
        nei.addProperty("excludedThaumicHorizonsBaseLightInternalBlockItemListEntries", 1);
        nei.addProperty("excludedThaumicHorizonsSolarLightInternalBlockItemListEntries", 1);
        nei.addProperty(
                "excludedTwilightForestExperiment115InternalBlockItemListEntries", 1);
        nei.addProperty(
                "excludedWitchingGadgetsCustomAirInternalBlockItemListEntries", 1);
        nei.addProperty("adaptedBotaniaCocoonItemIcons", 1);
        nei.addProperty("adaptedBotaniaCocoonRecipeWidgetRenderInvocations", 3);
        nei.addProperty("adaptedBotaniaPrismItemIcons", 1);
        nei.addProperty("adaptedBotaniaPrismRecipeWidgetRenderInvocations", 2);
        nei.addProperty("adaptedGalacticraftFlagItemIcons", 1);
        nei.addProperty("adaptedGalacticraftFlagRecipeWidgetRenderInvocations", 4);
        nei.addProperty("adaptedWrcbeTriangulatorItemIcons", 1);
        nei.addProperty("adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations", 5);
        nei.addProperty("adaptedModernMarkingsCrossingItemIcons", 6);
        nei.addProperty("adaptedThaumcraftRunedStoneItemIcons", 1);
        nei.addProperty("adaptedForestryScannedSaplingDisplayNames", 1);
        nei.addProperty("gregTechForestryScannedSaplingRecipeOccurrences", 1);
        nei.addProperty("adaptedForestryScannedPollenDisplayNames", 1);
        nei.addProperty("gregTechForestryScannedPollenRecipeOccurrences", 1);
        nei.addProperty("adaptedProjectBlueControlPanelItemIcons", 3);
        nei.addProperty(
                "adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations", 3);
        nei.addProperty("adaptedIc2FluidCannerRecipeWidgetRenderInvocations", 5);
        nei.addProperty("adaptedBuildCraftPhasedFacadeItemIcons", 4);
        nei.addProperty("adaptedMobsInfoInfernalPreviewOutputIcons", 58);
        nei.addProperty("adaptedMobsInfoPreviewSlotIcons",
                MobsInfoSemanticAdapter.EXPECTED_PREVIEW_SLOT_ICONS);
        nei.addProperty("adaptedDraconicMobSoulItemIcons", 399);
        nei.addProperty("normalizedTcnaAspectCostInputOccurrences", 7);
        nei.addProperty("normalizedTcnaAspectCostDistinctKeys", 3);
        nei.addProperty("normalizedTcnaAspectCostHandlerCategories", 4);
        nei.addProperty("adaptedGendustryLiquifierRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_LIQUIFIER_PAGES);
        nei.addProperty("adaptedGendustryMutagenProducerRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_MUTAGEN_PRODUCER_PAGES);
        nei.addProperty("adaptedGendustryExtractorRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_EXTRACTOR_PAGES);
        nei.addProperty("adaptedGendustryReplicatorRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_REPLICATOR_PAGES);
        nei.addProperty("adaptedGendustryTransposerRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_TRANSPOSER_PAGES);
        nei.addProperty("adaptedGendustryMutatronRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_MUTATRON_PAGES);
        nei.addProperty("adaptedGendustrySamplerRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_SAMPLER_PAGES);
        nei.addProperty("adaptedGendustryImprinterRecipes",
                GendustryMachineSemanticAdapter.EXPECTED_IMPRINTER_PAGES);
        nei.addProperty("loadedCategories", 2);
        nei.addProperty("recipesEnumerated", 7);
        nei.addProperty("recipeWidgetsRendered", 7);
        nei.addProperty("itemIconsRendered", 55991);
        nei.addProperty("informationalEmptyOutputRecipes", 513);
        nei.addProperty("gregTechFuelSinkRecipes",
                GregTechOutputlessSemanticPreflight.EXPECTED_FUEL_SINK_RECIPES);
        nei.addProperty("gregTechFuelSinkCategories",
                GregTechOutputlessSemanticPreflight.EXPECTED_FUEL_SINK_CATEGORIES);
        nei.addProperty("gregTechLargeBoilerFuelSinkRecipes",
                GregTechOutputlessSemanticPreflight.EXPECTED_LARGE_BOILER_FUEL_SINK_RECIPES);
        nei.addProperty("gregTechLargeBoilerFuelSinkCategories",
                GregTechOutputlessSemanticPreflight
                        .EXPECTED_LARGE_BOILER_FUEL_SINK_CATEGORIES);
        nei.addProperty("gregTechRadioHatchInformationRecipes",
                GregTechOutputlessSemanticPreflight
                        .EXPECTED_RADIO_HATCH_INFORMATION_RECIPES);
        nei.addProperty("gregTechQuantumComponentInformationRecipes",
                GregTechOutputlessSemanticPreflight
                        .EXPECTED_QUANTUM_COMPONENT_INFORMATION_RECIPES);
        nei.addProperty("gregTechSpaceProjectInformationRecipes",
                GregTechOutputlessSemanticPreflight
                        .EXPECTED_SPACE_PROJECT_INFORMATION_RECIPES);
        nei.addProperty("gregTechOutputlessSemanticCategories",
                GregTechOutputlessSemanticPreflight.EXPECTED_SEMANTIC_CATEGORIES);
        nei.addProperty("gregTechOutputlessSemanticRecipes",
                GregTechOutputlessSemanticPreflight.EXPECTED_SEMANTIC_RECIPES);
        nei.addProperty("excludedGregTechLargeBoilerPresentationRows",
                GregTechOutputlessSemanticPreflight
                        .EXPECTED_EXCLUDED_LARGE_BOILER_PRESENTATION_ROWS);
        nei.addProperty("excludedGregTechUnregisteredDoorRecyclingRows",
                GregTechOutputlessSemanticPreflight
                        .EXPECTED_UNREGISTERED_DOOR_RECYCLING_ROWS);
        nei.addProperty("excludedOwnerInternalFurnaceFuelRows",
                CatalogExcludedFuelPreflight.EXPECTED_EXCLUSIONS);
        nei.addProperty("excludedAe2EnderIoInternalConduitFacadeRows",
                Ae2InternalFacadeRecipePreflight.EXPECTED_EXCLUSIONS);
        nei.addProperty("excludedUnregisteredGregTechMachineCatalysts",
                UnregisteredGregTechMachineCatalystPolicy.EXPECTED_EXCLUSIONS);
        nei.addProperty("knowledgeIndependentAspectNames", 69);
        nei.addProperty("unloadedHandlerCategories", 0);
        nei.addProperty("ambiguousHandlerCategories", 0);
        nei.addProperty("duplicateHandlerCategories", 0);
        diagnostics.add("nei", nei);
        manifest.add("diagnostics", diagnostics);

        JsonObject mods = new JsonObject();
        mods.addProperty("gregtech", "GregTech");
        manifest.add("mods", mods);
        return manifest;
    }

    private static JsonObject policy(JsonObject manifest, String handlerClass) {
        return firstPolicyForClass(manifest, handlerClass);
    }

    private static JsonObject firstPolicyForClass(JsonObject manifest, String handlerClass) {
        JsonArray policies = manifest.getAsJsonArray("handlerPolicies");
        for (int index = 0; index < policies.size(); index++) {
            JsonObject policy = policies.get(index).getAsJsonObject();
            if (handlerClass.equals(policy.get("handlerClass").getAsString())) {
                return policy;
            }
        }
        throw new AssertionError("missing handler policy fixture: " + handlerClass);
    }

    private static void addPolicy(JsonArray policies, String handlerClass,
                                  String handlerId, String action, String contract) {
        JsonObject policy = new JsonObject();
        policy.addProperty("handlerClass", handlerClass);
        policy.addProperty("handlerId", handlerId);
        policy.addProperty("action", action);
        policy.addProperty("contract", contract);
        policies.add(policy);
    }
}
