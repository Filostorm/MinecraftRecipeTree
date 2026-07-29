import {isRecord} from './export-data-utils.mjs';
import {requirePackIdentity} from './pack-identity.mjs';

export const GENERIC_JEI_120_PROFILE = 'generic-jei-1.20.1';
export const MEATBALLCRAFT_112_PROFILE = 'meatballcraft-1.12.2';
export const MULTIBLOCK_MADNESS_112_PROFILE = 'multiblock-madness-1.12.2';
export const MULTIBLOCK_MADNESS_2_118_PROFILE = 'multiblock-madness-2-1.18.2';
export const GTNH_1710_PROFILE = 'gtnh-1.7.10';

export const MULTIBLOCK_MADNESS_112_WARNING_PREFIXES = Object.freeze([
  'ZERO_PREREQUISITE',
  'ZERO_ABSENT_OUTPUT',
  'ZERO_ABSENT_ALTERNATIVE',
  'UPSTREAM_BLANK_DISPLAY_NAME',
  'NATIVE_ICON_OVERSCAN_RECOVERY_APPLIED',
  'UPSTREAM_NATIVE_ICON_UNAVAILABLE',
]);

export const GTNH_NEI_DIAGNOSTIC_KEYS = Object.freeze([
  'itemListLoaded',
  'itemListRawEntries',
  'itemListExcludedEntries',
  'itemListRetainedEntries',
  'itemListRetainedUniqueIdentities',
  'registeredCraftingHandlers',
  'exportableCraftingHandlers',
  'adaptedHandlerCategories',
  'excludedNonRecipeHandlers',
  'excludedEmptyRecipeHandlers',
  'excludedUnboundTemplateRecipeHandlers',
  'excludedAe2fcFluidDropItemListPlaceholders',
  'excludedAe2fcFluidPacketItemListPlaceholders',
  'excludedAe2CableBusInternalBlockItemListEntries',
  'excludedAe2MatrixFrameInternalBlockItemListEntries',
  'excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders',
  'excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries',
  'excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders',
  'malisisDoorsUnconfiguredCustomDoorRecipeReferences',
  'malisisDoorsUnconfiguredCustomDoorQuestReferences',
  'excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders',
  'malisisDoorsUnconfiguredMixedBlockRecipeReferences',
  'malisisDoorsUnconfiguredMixedBlockQuestReferences',
  'excludedBloodMagicBloodLightItemListHelpers',
  'excludedBloodMagicSpectralContainerItemListHelpers',
  'excludedArchitectureCraftCladdingItemListPlaceholders',
  'excludedAvaritiaEmptyMatterClusterItemListPlaceholders',
  'excludedCarpentersBedInternalBlockItemListEntries',
  'excludedCarpentersDoorInternalBlockItemListEntries',
  'excludedStevesCartsUnconfiguredModularCartItemListPlaceholders',
  'excludedTConstructBattleSignInternalBlockItemListEntries',
  'excludedTConstructHeldItemInternalBlockItemListEntries',
  'excludedThaumcraftBlockHoleInternalBlockItemListEntries',
  'excludedThaumcraftEldritchPortalInternalBlockItemListEntries',
  'excludedThaumicHorizonsBaseLightInternalBlockItemListEntries',
  'excludedThaumicHorizonsSolarLightInternalBlockItemListEntries',
  'excludedTwilightForestExperiment115InternalBlockItemListEntries',
  'excludedWitchingGadgetsCustomAirInternalBlockItemListEntries',
  'excludedBotaniaBifrostItemListWorldStateEntries',
  'excludedBotaniaBuriedPetalsItemListWorldStateVariants',
  'excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask',
  'excludedBotaniaCacophoniumBlockItemListWorldStateEntries',
  'excludedBotaniaEnchanterItemListWorldStateEntries',
  'excludedBotaniaFakeAirItemListWorldStateEntries',
  'excludedBotaniaManaFlameItemListWorldStateEntries',
  'excludedBotaniaSolidVineItemListWorldStateEntries',
  'excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders',
  'adaptedBotaniaCocoonItemIcons',
  'adaptedBotaniaCocoonRecipeWidgetRenderInvocations',
  'adaptedBotaniaPrismItemIcons',
  'adaptedBotaniaPrismRecipeWidgetRenderInvocations',
  'adaptedGalacticraftFlagItemIcons',
  'adaptedGalacticraftFlagRecipeWidgetRenderInvocations',
  'adaptedWrcbeTriangulatorItemIcons',
  'adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations',
  'adaptedModernMarkingsCrossingItemIcons',
  'adaptedThaumcraftRunedStoneItemIcons',
  'adaptedForestryScannedSaplingDisplayNames',
  'gregTechForestryScannedSaplingRecipeOccurrences',
  'adaptedForestryScannedPollenDisplayNames',
  'gregTechForestryScannedPollenRecipeOccurrences',
  'adaptedProjectBlueControlPanelItemIcons',
  'adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations',
  'adaptedIc2FluidCannerRecipeWidgetRenderInvocations',
  'adaptedBuildCraftPhasedFacadeItemIcons',
  'adaptedMobsInfoInfernalPreviewOutputIcons',
  'adaptedMobsInfoPreviewSlotIcons',
  'adaptedDraconicMobSoulItemIcons',
  'adaptedGendustryLiquifierRecipes',
  'adaptedGendustryMutagenProducerRecipes',
  'adaptedGendustryExtractorRecipes',
  'adaptedGendustryReplicatorRecipes',
  'adaptedGendustryTransposerRecipes',
  'adaptedGendustryMutatronRecipes',
  'adaptedGendustrySamplerRecipes',
  'adaptedGendustryImprinterRecipes',
  'normalizedTcnaAspectCostInputOccurrences',
  'normalizedTcnaAspectCostDistinctKeys',
  'normalizedTcnaAspectCostHandlerCategories',
  'gregTechFuelSinkRecipes',
  'gregTechFuelSinkCategories',
  'gregTechLargeBoilerFuelSinkRecipes',
  'gregTechLargeBoilerFuelSinkCategories',
  'gregTechRadioHatchInformationRecipes',
  'gregTechQuantumComponentInformationRecipes',
  'gregTechSpaceProjectInformationRecipes',
  'gregTechOutputlessSemanticCategories',
  'gregTechOutputlessSemanticRecipes',
  'excludedGregTechLargeBoilerPresentationRows',
  'excludedGregTechUnregisteredDoorRecyclingRows',
  'excludedOwnerInternalFurnaceFuelRows',
  'excludedAe2EnderIoInternalConduitFacadeRows',
  'excludedUnregisteredGregTechMachineCatalysts',
  'loadedCategories',
  'recipesEnumerated',
  'recipeWidgetsRendered',
  'itemIconsRendered',
  'informationalEmptyOutputRecipes',
  'knowledgeIndependentAspectNames',
  'unloadedHandlerCategories',
  'ambiguousHandlerCategories',
  'duplicateHandlerCategories',
]);

const GTNH_ITEM_LIST_EXCLUSION_DIAGNOSTIC_KEYS = Object.freeze([
  'excludedAe2fcFluidDropItemListPlaceholders',
  'excludedAe2fcFluidPacketItemListPlaceholders',
  'excludedAe2CableBusInternalBlockItemListEntries',
  'excludedAe2MatrixFrameInternalBlockItemListEntries',
  'excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders',
  'excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries',
  'excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders',
  'excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders',
  'excludedBloodMagicBloodLightItemListHelpers',
  'excludedBloodMagicSpectralContainerItemListHelpers',
  'excludedArchitectureCraftCladdingItemListPlaceholders',
  'excludedAvaritiaEmptyMatterClusterItemListPlaceholders',
  'excludedCarpentersBedInternalBlockItemListEntries',
  'excludedCarpentersDoorInternalBlockItemListEntries',
  'excludedStevesCartsUnconfiguredModularCartItemListPlaceholders',
  'excludedTConstructBattleSignInternalBlockItemListEntries',
  'excludedTConstructHeldItemInternalBlockItemListEntries',
  'excludedBotaniaBifrostItemListWorldStateEntries',
  'excludedBotaniaBuriedPetalsItemListWorldStateVariants',
  'excludedBotaniaCacophoniumBlockItemListWorldStateEntries',
  'excludedBotaniaEnchanterItemListWorldStateEntries',
  'excludedBotaniaFakeAirItemListWorldStateEntries',
  'excludedBotaniaManaFlameItemListWorldStateEntries',
  'excludedBotaniaSolidVineItemListWorldStateEntries',
  'excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders',
  'excludedThaumcraftBlockHoleInternalBlockItemListEntries',
  'excludedThaumcraftEldritchPortalInternalBlockItemListEntries',
  'excludedThaumicHorizonsBaseLightInternalBlockItemListEntries',
  'excludedThaumicHorizonsSolarLightInternalBlockItemListEntries',
  'excludedTwilightForestExperiment115InternalBlockItemListEntries',
  'excludedWitchingGadgetsCustomAirInternalBlockItemListEntries',
]);

export const GTNH_HANDLER_POLICIES = Object.freeze([{"handlerClass":"appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler","handlerId":"appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler","action":"adapted-informational-category","contract":"adapter:ae2-in-world-crafting-wildcard-query-closure-v1"},{"handlerClass":"binnie.genetics.nei.AcclimatiserRecipeHandler","handlerId":"binnie.genetics.nei.AcclimatiserRecipeHandler","action":"adapted-informational-category","contract":"adapter:binnie-genetics-2.5.24-acclimatiser-in-place-tolerance-information-v2"},{"handlerClass":"binnie.genetics.nei.AnalyserRecipeHandler","handlerId":"binnie.genetics.nei.AnalyserRecipeHandler","action":"adapted-informational-category","contract":"adapter:binnie-genetics-2.5.24-analyser-in-place-genetic-information-v1"},{"handlerClass":"binnie.genetics.nei.GenepoolRecipeHandler","handlerId":"binnie.genetics.nei.GenepoolRecipeHandler","action":"adapted-complete-category","contract":"gtnh-2.8.4-binnie-genetics-2.5.24-genepool-fluid-semantics-v1"},{"handlerClass":"binnie.genetics.nei.IncubatorRecipeHandler","handlerId":"binnie.genetics.nei.IncubatorRecipeHandler","action":"adapted-complete-category","contract":"adapter:binnie-genetics-2.5.24-incubator-fluid-semantics-v1"},{"handlerClass":"blockrenderer6343.integration.gregtech.GTNEIMultiblockHandler","handlerId":"blockrenderer6343.integration.gregtech.GTNEIMultiblockHandler","action":"excluded-non-recipe-query","contract":"query-only:blockrenderer-gregtech-multiblock-item-query-ui-state-v1"},{"handlerClass":"blockrenderer6343.integration.structurelib.StructureCompatNEIHandler","handlerId":"blockrenderer6343.integration.structurelib.StructureCompatNEIHandler","action":"excluded-non-recipe-query","contract":"query-only:blockrenderer-structurelib-multiblock-item-query-ui-state-v1"},{"handlerClass":"bq_standard.integration.nei.QuestRecipeHandler","handlerId":"bq_standard.integration.nei.QuestRecipeHandler","action":"adapted-informational-category","contract":"adapter:betterquesting-complete-item-reference-pages-v1"},{"handlerClass":"buildcraft.compat.nei.RecipeHandlerRefinery","handlerId":"buildcraft.compat.nei.RecipeHandlerRefinery","action":"adapted-complete-category","contract":"adapter:buildcraft-compat-7.1.18-refinery-fluid-semantics-v1"},{"handlerClass":"codechicken.nei.recipe.InformationHandler","handlerId":"codechicken.nei.recipe.InformationHandler","action":"excluded-non-recipe-presentation","contract":"presentation-only:nei-item-filter-text-information-pages-v1"},{"handlerClass":"codechicken.nei.recipe.ProfilerRecipeHandler","handlerId":"codechicken.nei.recipe.ProfilerRecipeHandler","action":"excluded-non-recipe-debug","contract":"debug-only:nei-recipe-handler-timing-profiler-v1"},{"handlerClass":"com.github.dcysteine.neicustomdiagram.api.diagram.CustomDiagramGroup","handlerId":"neicustomdiagram.diagramgroup.enderstorage.chestoverview","action":"excluded-non-recipe-query","contract":"query-only:neicustomdiagram-enderstorage-live-chest-contents-v1"},{"handlerClass":"com.github.dcysteine.neicustomdiagram.api.diagram.CustomDiagramGroup","handlerId":"neicustomdiagram.diagramgroup.enderstorage.tankoverview","action":"excluded-non-recipe-query","contract":"query-only:neicustomdiagram-enderstorage-live-tank-contents-v1"},{"handlerClass":"com.github.dcysteine.neicustomdiagram.api.diagram.CustomDiagramGroup","handlerId":"neicustomdiagram.diagramgroup.gregtech.circuits","action":"excluded-non-recipe-presentation","contract":"presentation-only:neicustomdiagram-gregtech-circuit-line-overview-v1"},{"handlerClass":"com.github.dcysteine.neicustomdiagram.api.diagram.DiagramGroup","handlerId":"neicustomdiagram.diagramgroup.gregtech.lenses","action":"excluded-non-recipe-presentation","contract":"presentation-only:neicustomdiagram-gregtech-lens-colour-recipe-overview-v1"},{"handlerClass":"com.github.dcysteine.neicustomdiagram.api.diagram.DiagramGroup","handlerId":"neicustomdiagram.diagramgroup.gregtech.materialparts","action":"excluded-non-recipe-presentation","contract":"presentation-only:neicustomdiagram-gregtech-material-parts-catalog-v1"},{"handlerClass":"com.github.dcysteine.neicustomdiagram.api.diagram.DiagramGroup","handlerId":"neicustomdiagram.diagramgroup.gregtech.materialtools","action":"excluded-non-recipe-presentation","contract":"presentation-only:neicustomdiagram-gregtech-material-tools-catalog-v1"},{"handlerClass":"com.github.dcysteine.neicustomdiagram.api.diagram.DiagramGroup","handlerId":"neicustomdiagram.diagramgroup.gregtech.oreprocessing","action":"excluded-non-recipe-presentation","contract":"presentation-only:neicustomdiagram-gregtech-ore-processing-flow-v1"},{"handlerClass":"com.kuba6000.mobsinfo.nei.MobHandler","handlerId":"com.kuba6000.mobsinfo.nei.MobHandler","action":"adapted-informational-category","contract":"adapter:mobsinfo-0.5.6-item-reference-semantics-v2"},{"handlerClass":"com.kuba6000.mobsinfo.nei.MobHandlerInfernal","handlerId":"com.kuba6000.mobsinfo.nei.MobHandlerInfernal","action":"adapted-informational-category","contract":"adapter:mobsinfo-0.5.6-infernal-drop-information-v1"},{"handlerClass":"com.kuba6000.mobsinfo.nei.VillagerTradesHandler","handlerId":"com.kuba6000.mobsinfo.nei.VillagerTradesHandler","action":"adapted-informational-category","contract":"gtnh-2.8.4-mobsinfo-0.5.6-villager-trade-information-v2"},{"handlerClass":"com.rwtema.extrautils.nei.InfoHandler","handlerId":"com.rwtema.extrautils.nei.InfoHandler","action":"excluded-non-recipe-query","contract":"query-only:extrautilities-item-documentation-v1"},{"handlerClass":"com.rwtema.extrautils.nei.MicroBlocksHandler","handlerId":"com.rwtema.extrautils.nei.MicroBlocksHandler","action":"excluded-unbound-template-category","contract":"unbound-template:gtnh-2.8.4-extrautilities-microblocks-material-v3"},{"handlerClass":"com.rwtema.extrautils.nei.SoulHandler","handlerId":"com.rwtema.extrautils.nei.SoulHandler","action":"adapted-complete-category","contract":"adapter:extrautilities-soul-crafting-item-query-defensive-positioned-copy-v2"},{"handlerClass":"de.katzenpapst.amunra.nei.recipehandler.ARNasaWorkbenchShuttle","handlerId":"de.katzenpapst.amunra.nei.recipehandler.ARNasaWorkbenchShuttle","action":"adapted-complete-category","contract":"adapter:amunra-shuttle-canonical-set-query-v1"},{"handlerClass":"forestry.factory.recipes.nei.NEIHandlerBottler","handlerId":"forestry.factory.recipes.nei.NEIHandlerBottler","action":"adapted-complete-category","contract":"adapter:forestry-4.10.17-fluid-semantics-v1/bottler"},{"handlerClass":"forestry.factory.recipes.nei.NEIHandlerCarpenter","handlerId":"forestry.factory.recipes.nei.NEIHandlerCarpenter","action":"adapted-complete-category","contract":"adapter:forestry-4.10.17-fluid-semantics-v1/carpenter"},{"handlerClass":"forestry.factory.recipes.nei.NEIHandlerFabricator","handlerId":"forestry.factory.recipes.nei.NEIHandlerFabricator","action":"adapted-complete-category","contract":"adapter:forestry-4.10.17-fluid-semantics-v1/fabricator"},{"handlerClass":"forestry.factory.recipes.nei.NEIHandlerFermenter","handlerId":"forestry.factory.recipes.nei.NEIHandlerFermenter","action":"adapted-complete-category","contract":"adapter:forestry-4.10.17-fluid-semantics-v1/fermenter"},{"handlerClass":"forestry.factory.recipes.nei.NEIHandlerMoistener","handlerId":"forestry.factory.recipes.nei.NEIHandlerMoistener","action":"adapted-complete-category","contract":"adapter:forestry-4.10.17-fluid-semantics-v1/moistener"},{"handlerClass":"forestry.factory.recipes.nei.NEIHandlerSqueezer","handlerId":"forestry.factory.recipes.nei.NEIHandlerSqueezer","action":"adapted-complete-category","contract":"adapter:forestry-4.10.17-fluid-semantics-v1/squeezer"},{"handlerClass":"forestry.factory.recipes.nei.NEIHandlerStill","handlerId":"forestry.factory.recipes.nei.NEIHandlerStill","action":"adapted-complete-category","contract":"adapter:forestry-4.10.17-fluid-semantics-v1/still"},{"handlerClass":"galaxyspace.core.nei.RocketRecipeHandler","handlerId":"galaxyspace.core.nei.rocket.RocketT1RecipeHandler","action":"adapted-complete-category","contract":"adapter:galaxyspace-rocket-t1-canonical-set-query-v1"},{"handlerClass":"galaxyspace.core.nei.RocketRecipeHandler","handlerId":"galaxyspace.core.nei.rocket.RocketT2RecipeHandler","action":"adapted-complete-category","contract":"adapter:galaxyspace-rocket-t2-canonical-set-query-v1"},{"handlerClass":"galaxyspace.core.nei.RocketRecipeHandler","handlerId":"galaxyspace.core.nei.rocket.RocketT3RecipeHandler","action":"adapted-complete-category","contract":"adapter:galaxyspace-rocket-t3-canonical-set-query-v1"},{"handlerClass":"galaxyspace.core.nei.RocketRecipeHandler","handlerId":"galaxyspace.core.nei.rocket.RocketT4RecipeHandler","action":"adapted-complete-category","contract":"adapter:galaxyspace-rocket-t4-canonical-set-query-v1"},{"handlerClass":"galaxyspace.core.nei.RocketRecipeHandler","handlerId":"galaxyspace.core.nei.rocket.RocketT5RecipeHandler","action":"adapted-complete-category","contract":"adapter:galaxyspace-rocket-t5-canonical-set-query-v1"},{"handlerClass":"galaxyspace.core.nei.RocketRecipeHandler","handlerId":"galaxyspace.core.nei.rocket.RocketT6RecipeHandler","action":"adapted-complete-category","contract":"adapter:galaxyspace-rocket-t6-canonical-set-query-v1"},{"handlerClass":"galaxyspace.core.nei.RocketRecipeHandler","handlerId":"galaxyspace.core.nei.rocket.RocketT7RecipeHandler","action":"adapted-complete-category","contract":"adapter:galaxyspace-rocket-t7-canonical-set-query-v1"},{"handlerClass":"galaxyspace.core.nei.RocketRecipeHandler","handlerId":"galaxyspace.core.nei.rocket.RocketT8RecipeHandler","action":"adapted-complete-category","contract":"adapter:galaxyspace-rocket-t8-canonical-set-query-v1"},{"handlerClass":"gcewing.projectblue.nei.NEIRecipeHandler","handlerId":"gcewing.projectblue.nei.NEIRecipeHandler","action":"adapted-complete-category","contract":"adapter:projectblue-control-panel-registry-query-closure-v1"},{"handlerClass":"hellfirepvp.beebetteratbees.client.gui.BBABGuiRecipeTreeHandler","handlerId":"hellfirepvp.beebetteratbees.client.gui.BBABGuiRecipeTreeHandler","action":"excluded-non-recipe-query","contract":"query-only:bee-breeding-recursive-lineage-visualization-v1"},{"handlerClass":"ic2.neiIntegration.core.recipehandler.LatheRecipeHandler","handlerId":"ic2.neiIntegration.core.recipehandler.LatheRecipeHandler","action":"excluded-non-recipe-query","contract":"query-only:ic2-lathe-interactive-workpiece-state-v1"},{"handlerClass":"micdoodle8.mods.galacticraft.core.nei.CircuitFabricatorRecipeHandler","handlerId":"micdoodle8.mods.galacticraft.core.nei.CircuitFabricatorRecipeHandler","action":"adapted-complete-category","contract":"adapter:galacticraft-3.3.13-gtnh-circuit-fabricator-stable-result-v1"},{"handlerClass":"mrtjp.projectred.core.libmc.recipe.PRShapedRecipeHandler","handlerId":"mrtjp.projectred.core.libmc.recipe.PRShapedRecipeHandler","action":"adapted-complete-category","contract":"adapter:projectred-shaped-builder-registry-query-closure-v2"},{"handlerClass":"mrtjp.projectred.core.libmc.recipe.PRShapelessRecipeHandler","handlerId":"mrtjp.projectred.core.libmc.recipe.PRShapelessRecipeHandler","action":"adapted-complete-category","contract":"adapter:projectred-shapeless-builder-registry-query-closure-v2"},{"handlerClass":"net.bdew.gendustry.nei.ExtractorHandler","handlerId":"net.bdew.gendustry.nei.ExtractorHandler","action":"adapted-complete-category","contract":"adapter:gendustry-1.9.4-machine-semantics-v1/extractor"},{"handlerClass":"net.bdew.gendustry.nei.ImprinterHandler","handlerId":"net.bdew.gendustry.nei.ImprinterHandler","action":"adapted-complete-category","contract":"adapter:gendustry-1.9.4-machine-semantics-v1/imprinter"},{"handlerClass":"net.bdew.gendustry.nei.LiquifierHandler","handlerId":"net.bdew.gendustry.nei.LiquifierHandler","action":"adapted-complete-category","contract":"adapter:gendustry-1.9.4-machine-semantics-v1/liquifier"},{"handlerClass":"net.bdew.gendustry.nei.MutagenProducerHandler","handlerId":"net.bdew.gendustry.nei.MutagenProducerHandler","action":"adapted-complete-category","contract":"adapter:gendustry-1.9.4-machine-semantics-v1/mutagen-producer"},{"handlerClass":"net.bdew.gendustry.nei.MutatronHandler","handlerId":"net.bdew.gendustry.nei.MutatronHandler","action":"adapted-complete-category","contract":"adapter:gendustry-1.9.4-machine-semantics-v1/mutatron"},{"handlerClass":"net.bdew.gendustry.nei.ReplicatorHandler","handlerId":"net.bdew.gendustry.nei.ReplicatorHandler","action":"adapted-complete-category","contract":"adapter:gendustry-1.9.4-machine-semantics-v1/replicator"},{"handlerClass":"net.bdew.gendustry.nei.SamplerHandler","handlerId":"net.bdew.gendustry.nei.SamplerHandler","action":"adapted-complete-category","contract":"adapter:gendustry-1.9.4-machine-semantics-v1/sampler"},{"handlerClass":"net.bdew.gendustry.nei.TemplateCraftingHandler","handlerId":"net.bdew.gendustry.nei.TemplateCraftingHandler","action":"adapted-complete-category","contract":"adapter:gendustry-template-crafting-exact-item-query-v1"},{"handlerClass":"net.bdew.gendustry.nei.TransposerHandler","handlerId":"net.bdew.gendustry.nei.TransposerHandler","action":"adapted-complete-category","contract":"adapter:gendustry-1.9.4-machine-semantics-v1/transposer"},{"handlerClass":"ru.timeconqueror.tcneiadditions.nei.AspectCombinationHandler","handlerId":"ru.timeconqueror.tcneiadditions.nei.AspectCombinationHandler","action":"excluded-non-recipe-query","contract":"query-only:player-discovered-aspect-combination-view-v1"},{"handlerClass":"ru.timeconqueror.tcneiadditions.nei.AspectFromItemStackHandler","handlerId":"ru.timeconqueror.tcneiadditions.nei.AspectFromItemStackHandler","action":"excluded-non-recipe-query","contract":"query-only:player-scanned-item-aspect-view-v1"},{"handlerClass":"speiger.src.crops.prediction.NEIPlugin","handlerId":"speiger.src.crops.prediction.NEIPlugin","action":"adapted-complete-category","contract":"adapter:ic2-crop-deterministic-query-bucket-closure-nei-presentation-v2"},{"handlerClass":"tconstruct.plugins.nei.RecipeHandlerAlloying","handlerId":"tconstruct.plugins.nei.RecipeHandlerAlloying","action":"adapted-complete-category","contract":"adapter:tconstruct-1.13.57-alloying-fluid-semantics-v1"},{"handlerClass":"tconstruct.plugins.nei.RecipeHandlerMelting","handlerId":"tconstruct.plugins.nei.RecipeHandlerMelting","action":"adapted-complete-category","contract":"adapter:tconstruct-1.13.57-melting-fluid-semantics-v1"},{"handlerClass":"tconstruct.plugins.nei.RecipeHandlerToolMaterials","handlerId":"tconstruct.plugins.nei.RecipeHandlerToolMaterials","action":"excluded-non-recipe-presentation","contract":"presentation-only:tconstruct-tool-material-statistics-v1"},{"handlerClass":"tonius.neiintegration.mods.mcforge.RecipeHandlerFluidRegistry","handlerId":"tonius.neiintegration.mods.mcforge.RecipeHandlerFluidRegistry","action":"excluded-non-recipe-presentation","contract":"presentation-only:forge-fluid-registry-browser-v1"},{"handlerClass":"tonius.neiintegration.mods.mcforge.RecipeHandlerOreDictionary","handlerId":"tonius.neiintegration.mods.mcforge.RecipeHandlerOreDictionary","action":"excluded-non-recipe-presentation","contract":"presentation-only:forge-ore-dictionary-equivalence-browser-v1"},{"handlerClass":"vazkii.botania.client.integration.nei.recipe.RecipeHandlerFloatingFlowers","handlerId":"vazkii.botania.client.integration.nei.recipe.RecipeHandlerFloatingFlowers","action":"adapted-complete-category","contract":"adapter:botania-floating-special-flower-variant-query-closure-v1"},{"handlerClass":"vazkii.botania.client.integration.nei.recipe.RecipeHandlerLexicaBotania","handlerId":"vazkii.botania.client.integration.nei.recipe.RecipeHandlerLexicaBotania","action":"excluded-non-recipe-presentation","contract":"presentation-only:botania-lexica-cross-reference-v1"}].map(policy => Object.freeze(policy)));
/** GTNH 2.8.4 policy set after promoting Ender IO Vat semantics. */
export const GTNH_284_HANDLER_POLICIES = Object.freeze([
  ...GTNH_HANDLER_POLICIES.slice(0, 24),
  Object.freeze({
    handlerClass: 'crazypants.enderio.nei.VatRecipeHandler',
    handlerId: 'crazypants.enderio.nei.VatRecipeHandler',
    action: 'adapted-complete-category',
    contract: 'adapter:enderio-2.9.28-vat-fluid-semantics-v1',
  }),
  ...GTNH_HANDLER_POLICIES.slice(24),
]);

export const GTNH_KNOWLEDGE_POLICY = Object.freeze({
  playerResearchMutated: false,
  thaumcraftLockedRecipes: 'required-by-pinned-config',
  itemAspectDisplayNames: 'nbt-aspect-registry-v1',
  forestryScannedSaplingDisplayName:
    'gregtech-forestry-scanned-sapling-explicit-custom-name-v1',
  forestryScannedSaplingSourceBinding:
    'gregtech-forestry-scanned-sapling-source-bound-display-name-v1',
  forestryScannedPollenDisplayName:
    'gregtech-forestry-scanned-pollen-explicit-custom-name-v1',
  forestryScannedPollenSourceBinding:
    'gregtech-forestry-scanned-pollen-source-bound-display-name-v1',
  itemAspectRecipeSemantics: 'thaumcraft-nei-aspect-cost-meta1-to-meta0-semantic-proxy-v1',
  gregTechOutputlessRecipeSemantics: 'gregtech-outputless-semantic-preflight-v2',
  gregTechStaleDoorRecyclingExclusion:
    'gregtech-unregistered-itemdoor-recycling-exclusion-v1',
  ownerInternalFurnaceFuelRowExclusion:
    'nei-furnace-fuel-owner-internal-world-state-row-exclusion-v1',
  ae2InternalFacadeRecipeExclusion:
    'ae2-695-enderio-2.9.28-internal-conduit-facade-row-exclusion-v1',
  gendustryMachineRecipeSemantics:
    'gtnh-2.8.4-gendustry-1.9.4-machine-graph-semantics-v1',
});

export const GTNH_DATA_ATTRIBUTION = Object.freeze({
  sourceUrl:
    'https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/tree/2.8.4',
  projectUrl: 'https://www.gtnewhorizons.com/',
  licenseIdentifier: 'CC BY-NC-SA 4.0',
  licenseUrl: 'https://creativecommons.org/licenses/by-nc-sa/4.0/',
});

const STANDARD_COUNT_KEYS = Object.freeze([
  'items',
  'recipes',
  'categories',
  'mobs',
  'blockDrops',
  'failures',
]);
const GTNH_DIAGNOSTIC_KEYS = Object.freeze([
  'failureEvents',
  'failureEventsOmitted',
  'nei',
]);

const profileRequirements = new Map([
  [
    GENERIC_JEI_120_PROFILE,
    Object.freeze({
      id: GENERIC_JEI_120_PROFILE,
      label: 'Generic JEI 1.20.1',
      minecraft: '1.20.1',
      format: 1,
      iconScale: 4,
      recipeScale: 2,
      recipeViewer: 'JEI',
      corpus: 'dynamic-complete',
      requiresPackIdentity: true,
    }),
  ],
  [
    MEATBALLCRAFT_112_PROFILE,
    Object.freeze({
      id: MEATBALLCRAFT_112_PROFILE,
      label: 'MeatballCraft',
      minecraft: '1.12.2',
      format: 1,
      iconScale: 3,
      recipeScale: 2,
      recipeViewer: 'HEI',
      corpus: 'exact',
      requiresExporterBuildIdentity: true,
    }),
  ],
  [
    MULTIBLOCK_MADNESS_112_PROFILE,
    Object.freeze({
      id: MULTIBLOCK_MADNESS_112_PROFILE,
      label: 'Multiblock Madness',
      minecraft: '1.12.2',
      format: 1,
      iconScale: 3,
      recipeScale: 2,
      recipeViewer: 'HEI',
      corpus: 'dynamic-complete',
      requiresExporterBuildIdentity: true,
      requiresZeroFailures: true,
      diagnosticKeys: Object.freeze([
        'failureEvents',
        'failureEventsOmitted',
        'warningEvents',
        'warningEventsOmitted',
      ]),
      packIdentity: Object.freeze({
        name: 'Multiblock Madness',
        version: '3.2.3',
        identitySource: 'explicit-request',
      }),
    }),
  ],
  [
    MULTIBLOCK_MADNESS_2_118_PROFILE,
    Object.freeze({
      id: MULTIBLOCK_MADNESS_2_118_PROFILE,
      label: 'Multiblock Madness 2',
      minecraft: '1.18.2',
      format: 1,
      iconScale: 3,
      recipeScale: 2,
      recipeViewer: 'REI',
      corpus: 'dynamic-complete',
      requiresExporterBuildIdentity: true,
      requiresZeroFailures: true,
      diagnosticKeys: Object.freeze([
        'failureEvents',
        'failureEventsOmitted',
        'nativeIconCorrections',
        'transparentIcons',
      ]),
      packIdentity: Object.freeze({
        name: 'Multiblock Madness 2',
        version: '1.0.0',
        identitySource: 'explicit-request',
      }),
    }),
  ],
  [
    GTNH_1710_PROFILE,
    Object.freeze({
      id: GTNH_1710_PROFILE,
      label: 'GT New Horizons',
      minecraft: '1.7.10',
      format: 2,
      iconScale: 3,
      recipeScale: 2,
      recipeViewer: 'NEI',
      corpus: 'dynamic-complete',
      requiresExporterBuildIdentity: true,
      provenance: Object.freeze({
        profile: GTNH_1710_PROFILE,
        forge: '10.13.4.1614',
        nei: '2.8.44-GTNH',
      }),
      packIdentity: Object.freeze({
        name: 'GT New Horizons',
        version: '2.8.4',
        identitySource: 'explicit-request',
      }),
      attribution: GTNH_DATA_ATTRIBUTION,
    }),
  ],
]);

export const EXPORT_QUALITY_PROFILE_IDS = Object.freeze([...profileRequirements.keys()]);

export function resolveQualityProfile(profile) {
  if (profile === undefined || profile === null) return null;
  if (!profileRequirements.has(profile)) {
    throw new Error(
      `Unknown export quality profile ${JSON.stringify(profile)}. Supported profiles: ` +
        EXPORT_QUALITY_PROFILE_IDS.join(', '),
    );
  }
  return profile;
}

export function qualityProfileRequirementsFor(profile) {
  const resolvedProfile = resolveQualityProfile(profile);
  return resolvedProfile === null ? null : profileRequirements.get(resolvedProfile);
}

function isCategoryFailure(message) {
  return /^(?:HANDLER_(?:UNLOADED|AMBIGUOUS|DUPLICATE):|category\s|reading category id while filtering:)/i.test(
    message.trim(),
  );
}

function isSemanticFailure(message) {
  return /^(?:RECIPE_SEMANTICS:|recipe semantics\s|recipe (?:input|output) ingredient\s)/i.test(
    message.trim(),
  );
}

function isQuantityFallback(message) {
  return /^(?:QUANTITY_INVALID:|ingredient amount type\s.+\susing quantity 1(?:\s|$))/i.test(
    message.trim(),
  );
}

function isRecipePreviewFailure(message) {
  return /^(?:RECIPE_WIDGET_RENDER:|PNG_WRITE:)/i.test(message.trim());
}

function isUnclassifiedZeroQuantity(message) {
  const normalized = message.trim();
  if (/\bZERO_UNCLASSIFIED\b/.test(normalized)) return true;
  if (!/^ZERO_[A-Z_]+\b/.test(normalized)) return false;
  return !/^(?:ZERO_PREREQUISITE|ZERO_THRESHOLD|ZERO_UNKNOWN_FLOW|ZERO_ABSENT_OUTPUT|ZERO_INVALID_RECIPE)\b/.test(
    normalized,
  );
}

function isLoggedBlankDisplayLabelSubstitution(message) {
  return /^ingredient display name\s.+\swas null\/blank(?: after formatting-code removal)?; using unique id$/i.test(
    message.trim(),
  );
}

function isCatalogCompletenessFailure(message) {
  const normalized = message.trim();
  // HEI's exact unique ID remains the catalog identity. A few legacy mods
  // return a blank cosmetic label; the exporter records that defect and uses
  // the exact ID as visible text. Keep extraction exceptions and every actual
  // identity/resource fallback fail-closed.
  if (isLoggedBlankDisplayLabelSubstitution(normalized)) return false;
  return (
    /^ITEM_IDENTITY:/i.test(normalized) ||
    /^(?:list ingredients\b|item\s.+\s#\d+:)/i.test(normalized) ||
    /^ingredient (?:unique id|display name|resource id)\b/i.test(normalized) ||
    /\bmissing item\b/i.test(normalized)
  );
}

function hasExactKeys(value, expected) {
  if (!isRecord(value)) return false;
  const actual = Object.keys(value).sort();
  const wanted = [...expected].sort();
  return actual.length === wanted.length && actual.every((key, index) => key === wanted[index]);
}

function isNonNegativeSafeInteger(value) {
  return Number.isSafeInteger(value) && value >= 0;
}

function exactRecordIssues(value, expected, label) {
  if (!hasExactKeys(value, Object.keys(expected))) {
    return [
      `${label} must contain exactly ${Object.keys(expected).join(', ')}.`,
    ];
  }
  const issues = [];
  for (const [name, expectedValue] of Object.entries(expected)) {
    if (value[name] !== expectedValue) {
      issues.push(
        `${label}.${name} must be ${JSON.stringify(expectedValue)}; received ` +
          `${JSON.stringify(value[name])}.`,
      );
    }
  }
  return issues;
}

/** Length framing keeps the runtime-class/handler-ID identity injective. */
function gtnhHandlerPolicyIdentity(policy) {
  if (
    !isRecord(policy) ||
    typeof policy.handlerClass !== 'string' ||
    typeof policy.handlerId !== 'string'
  ) {
    return null;
  }
  return (
    `${policy.handlerClass.length}:${policy.handlerClass}` +
    `${policy.handlerId.length}:${policy.handlerId}`
  );
}

function gtnhPolicyIssues(manifest, label) {
  const issues = [];
  if (!Array.isArray(manifest?.handlerPolicies)) {
    issues.push(`${label} manifest.handlerPolicies must be an array.`);
  } else {
    const seen = new Map();
    for (const [index, policy] of manifest.handlerPolicies.entries()) {
      const identity = gtnhHandlerPolicyIdentity(policy);
      if (identity === null) continue;
      const previousIndex = seen.get(identity);
      if (previousIndex !== undefined) {
        issues.push(
          `${label} manifest.handlerPolicies[${index}] duplicates composite ` +
            `handlerClass/handlerId identity from index ${previousIndex}: ` +
            `class=${JSON.stringify(policy.handlerClass)}, ` +
            `handlerId=${JSON.stringify(policy.handlerId)}.`,
        );
      } else {
        seen.set(identity, index);
      }
    }
    if (manifest.handlerPolicies.length !== GTNH_284_HANDLER_POLICIES.length) {
      issues.push(
        `${label} manifest.handlerPolicies must contain exactly ` +
          `${GTNH_284_HANDLER_POLICIES.length} pinned handler policies; received ` +
          `${manifest.handlerPolicies.length}.`,
      );
    } else {
      for (let index = 0; index < GTNH_284_HANDLER_POLICIES.length; index += 1) {
        issues.push(
          ...exactRecordIssues(
            manifest.handlerPolicies[index],
            GTNH_284_HANDLER_POLICIES[index],
            `${label} manifest.handlerPolicies[${index}]`,
          ),
        );
      }
    }
  }
  issues.push(
    ...exactRecordIssues(
      manifest?.knowledgePolicy,
      GTNH_KNOWLEDGE_POLICY,
      `${label} manifest.knowledgePolicy`,
    ),
  );
  issues.push(
    ...exactRecordIssues(
      manifest?.attribution,
      GTNH_DATA_ATTRIBUTION,
      `${label} manifest.attribution`,
    ),
  );
  return issues;
}

function genericJeiManifestQualityIssues(manifest, label) {
  const issues = [];
  const counts = manifest?.counts;
  if (!hasExactKeys(counts, STANDARD_COUNT_KEYS)) {
    issues.push(
      `${label} manifest.counts must contain exactly ${STANDARD_COUNT_KEYS.join(', ')}.`,
    );
  }
  for (const name of STANDARD_COUNT_KEYS) {
    if (!isNonNegativeSafeInteger(counts?.[name])) {
      issues.push(`${label} requires a non-negative manifest.counts.${name} value.`);
    }
  }
  for (const name of ['items', 'recipes', 'categories']) {
    if (isNonNegativeSafeInteger(counts?.[name]) && counts[name] === 0) {
      issues.push(`${label} requires manifest.counts.${name} to be positive.`);
    }
  }
  const diagnostics = manifest?.diagnostics;
  if (!hasExactKeys(diagnostics, ['failureEvents', 'failureEventsOmitted'])) {
    issues.push(
      `${label} manifest.diagnostics must contain exactly failureEvents, failureEventsOmitted.`,
    );
  }
  if (
    isNonNegativeSafeInteger(diagnostics?.failureEvents) &&
    isNonNegativeSafeInteger(counts?.failures) &&
    diagnostics.failureEvents !== counts.failures
  ) {
    issues.push(
      `${label} diagnostics.failureEvents (${diagnostics.failureEvents}) must equal ` +
        `manifest.counts.failures (${counts.failures}).`,
    );
  }

  try {
    requirePackIdentity(manifest?.pack, `${label} manifest.pack`);
  } catch (error) {
    issues.push(error.message);
  }
  return issues;
}

/**
 * Validate the fail-closed NEI extraction telemetry emitted by the GTNH
 * exporter. These checks intentionally reject schema drift: an unknown
 * handler/category counter must be added to this contract before publication,
 * never ignored by an older viewer.
 */
export function gtnhManifestQualityIssues(manifest, label = 'GT New Horizons') {
  const issues = [];
  issues.push(...gtnhPolicyIssues(manifest, label));
  const counts = manifest?.counts;
  if (!hasExactKeys(counts, STANDARD_COUNT_KEYS)) {
    issues.push(
      `${label} manifest.counts must contain exactly ${STANDARD_COUNT_KEYS.join(', ')}.`,
    );
  }
  for (const name of STANDARD_COUNT_KEYS) {
    if (!isNonNegativeSafeInteger(counts?.[name])) {
      issues.push(`${label} requires a non-negative manifest.counts.${name} value.`);
    }
  }
  for (const name of ['items', 'recipes', 'categories']) {
    if (isNonNegativeSafeInteger(counts?.[name]) && counts[name] === 0) {
      issues.push(`${label} requires manifest.counts.${name} to be positive.`);
    }
  }
  for (const name of ['mobs', 'blockDrops']) {
    if (isNonNegativeSafeInteger(counts?.[name]) && counts[name] !== 0) {
      issues.push(
        `${label} requires manifest.counts.${name} to be 0; received ${counts[name]}.`,
      );
    }
  }
  if (isNonNegativeSafeInteger(counts?.failures) && counts.failures !== 0) {
    issues.push(
      `${label} requires manifest.counts.failures to be 0; received ${counts.failures}.`,
    );
  }

  const diagnostics = manifest?.diagnostics;
  if (!hasExactKeys(diagnostics, GTNH_DIAGNOSTIC_KEYS)) {
    issues.push(
      `${label} manifest.diagnostics must contain exactly ${GTNH_DIAGNOSTIC_KEYS.join(', ')}.`,
    );
  }
  if (
    isNonNegativeSafeInteger(diagnostics?.failureEvents) &&
    diagnostics.failureEvents !== 0
  ) {
    issues.push(
      `${label} requires diagnostics.failureEvents to be 0; received ` +
        `${diagnostics.failureEvents}.`,
    );
  }
  if (
    isNonNegativeSafeInteger(diagnostics?.failureEvents) &&
    isNonNegativeSafeInteger(counts?.failures) &&
    diagnostics.failureEvents !== counts.failures
  ) {
    issues.push(
      `${label} diagnostics.failureEvents (${diagnostics.failureEvents}) must equal ` +
        `manifest.counts.failures (${counts.failures}).`,
    );
  }

  const nei = diagnostics?.nei;
  if (!hasExactKeys(nei, GTNH_NEI_DIAGNOSTIC_KEYS)) {
    issues.push(
      `${label} diagnostics.nei must contain exactly ${GTNH_NEI_DIAGNOSTIC_KEYS.join(', ')}.`,
    );
  }
  if (nei?.itemListLoaded !== true) {
    issues.push(`${label} requires diagnostics.nei.itemListLoaded to be true.`);
  }
  const numericKeys = GTNH_NEI_DIAGNOSTIC_KEYS.filter(name => name !== 'itemListLoaded');
  for (const name of numericKeys) {
    if (!isNonNegativeSafeInteger(nei?.[name])) {
      issues.push(`${label} requires diagnostics.nei.${name} to be a non-negative safe integer.`);
    }
  }
  for (const name of [
    'unloadedHandlerCategories',
    'ambiguousHandlerCategories',
    'duplicateHandlerCategories',
  ]) {
    if (isNonNegativeSafeInteger(nei?.[name]) && nei[name] !== 0) {
      issues.push(
        `${label} requires diagnostics.nei.${name} to be 0; received ${nei[name]}.`,
      );
    }
  }

  const compareTelemetry = (leftName, rightValue, rightLabel) => {
    const leftValue = nei?.[leftName];
    if (
      isNonNegativeSafeInteger(leftValue) &&
      isNonNegativeSafeInteger(rightValue) &&
      leftValue !== rightValue
    ) {
      issues.push(
        `${label} diagnostics.nei.${leftName} (${leftValue}) must equal ` +
          `${rightLabel} (${rightValue}).`,
      );
    }
  };
  compareTelemetry(
    'registeredCraftingHandlers',
    isNonNegativeSafeInteger(counts?.categories) &&
      isNonNegativeSafeInteger(nei?.excludedNonRecipeHandlers) &&
      isNonNegativeSafeInteger(nei?.excludedEmptyRecipeHandlers) &&
      isNonNegativeSafeInteger(nei?.excludedUnboundTemplateRecipeHandlers)
      ? counts.categories +
          nei.excludedNonRecipeHandlers +
          nei.excludedEmptyRecipeHandlers +
          nei.excludedUnboundTemplateRecipeHandlers
      : undefined,
    'manifest.counts.categories + diagnostics.nei.excludedNonRecipeHandlers (20) + ' +
      'diagnostics.nei.excludedEmptyRecipeHandlers (22) + ' +
      'diagnostics.nei.excludedUnboundTemplateRecipeHandlers (1)',
  );
  compareTelemetry(
    'exportableCraftingHandlers',
    counts?.categories,
    'manifest.counts.categories',
  );
  compareTelemetry('loadedCategories', counts?.categories, 'manifest.counts.categories');
  compareTelemetry('recipesEnumerated', counts?.recipes, 'manifest.counts.recipes');
  compareTelemetry('recipeWidgetsRendered', counts?.recipes, 'manifest.counts.recipes');
  compareTelemetry('itemIconsRendered', counts?.items, 'manifest.counts.items');

  const itemListExclusionTelemetryIsComplete =
    GTNH_ITEM_LIST_EXCLUSION_DIAGNOSTIC_KEYS.every(name =>
      isNonNegativeSafeInteger(nei?.[name]),
    );
  if (
    itemListExclusionTelemetryIsComplete &&
    isNonNegativeSafeInteger(nei?.itemListExcludedEntries)
  ) {
    const itemListExclusionEntryTotal = GTNH_ITEM_LIST_EXCLUSION_DIAGNOSTIC_KEYS
      .map(name => nei[name])
      .reduce((total, value) => total + value, 0);
    if (nei.itemListExcludedEntries !== itemListExclusionEntryTotal) {
      issues.push(
        `${label} diagnostics.nei.itemListExcludedEntries ` +
          `(${nei.itemListExcludedEntries}) must equal the summed ItemList exclusion ` +
          `telemetry (${itemListExclusionEntryTotal}).`,
      );
    }
  }
  if (
    isNonNegativeSafeInteger(nei?.itemListRawEntries) &&
    isNonNegativeSafeInteger(nei?.itemListExcludedEntries) &&
    isNonNegativeSafeInteger(nei?.itemListRetainedEntries) &&
    nei.itemListRawEntries - nei.itemListExcludedEntries !== nei.itemListRetainedEntries
  ) {
    issues.push(
      `${label} diagnostics.nei.itemListRawEntries (${nei.itemListRawEntries}) minus ` +
        `diagnostics.nei.itemListExcludedEntries (${nei.itemListExcludedEntries}) must equal ` +
        `diagnostics.nei.itemListRetainedEntries (${nei.itemListRetainedEntries}).`,
    );
  }
  if (
    isNonNegativeSafeInteger(nei?.itemListRetainedEntries) &&
    isNonNegativeSafeInteger(nei?.itemListRetainedUniqueIdentities) &&
    nei.itemListRetainedEntries - nei.itemListRetainedUniqueIdentities !== 1
  ) {
    issues.push(
      `${label} diagnostics.nei.itemListRetainedEntries (${nei.itemListRetainedEntries}) ` +
        `must exceed diagnostics.nei.itemListRetainedUniqueIdentities ` +
        `(${nei.itemListRetainedUniqueIdentities}) by exactly 1.`,
    );
  }
  if (
    isNonNegativeSafeInteger(nei?.itemListRetainedUniqueIdentities) &&
    nei.itemListRetainedUniqueIdentities === 0
  ) {
    issues.push(
      `${label} requires diagnostics.nei.itemListRetainedUniqueIdentities to be positive.`,
    );
  }
  if (
    isNonNegativeSafeInteger(counts?.items) &&
    isNonNegativeSafeInteger(nei?.itemListRetainedUniqueIdentities) &&
    counts.items < nei.itemListRetainedUniqueIdentities
  ) {
    issues.push(
      `${label} manifest.counts.items (${counts.items}) must be greater than or equal to ` +
        `diagnostics.nei.itemListRetainedUniqueIdentities ` +
        `(${nei.itemListRetainedUniqueIdentities}).`,
    );
  }

  for (const [name, expected] of [
    ['adaptedHandlerCategories', 45],
    ['excludedNonRecipeHandlers', 20],
    ['excludedEmptyRecipeHandlers', 22],
    ['excludedUnboundTemplateRecipeHandlers', 1],
    ['excludedAe2fcFluidDropItemListPlaceholders', 1],
    ['excludedAe2fcFluidPacketItemListPlaceholders', 1],
    ['excludedAe2CableBusInternalBlockItemListEntries', 1],
    ['excludedAe2MatrixFrameInternalBlockItemListEntries', 1],
    ['excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders', 1],
    ['excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries', 1],
    ['excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders', 1],
    ['malisisDoorsUnconfiguredCustomDoorRecipeReferences', 0],
    ['malisisDoorsUnconfiguredCustomDoorQuestReferences', 0],
    ['excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders', 1],
    ['malisisDoorsUnconfiguredMixedBlockRecipeReferences', 0],
    ['malisisDoorsUnconfiguredMixedBlockQuestReferences', 0],
    ['excludedBloodMagicBloodLightItemListHelpers', 1],
    ['excludedBloodMagicSpectralContainerItemListHelpers', 1],
    ['excludedArchitectureCraftCladdingItemListPlaceholders', 1],
    ['excludedAvaritiaEmptyMatterClusterItemListPlaceholders', 1],
    ['excludedCarpentersBedInternalBlockItemListEntries', 1],
    ['excludedCarpentersDoorInternalBlockItemListEntries', 1],
    ['excludedStevesCartsUnconfiguredModularCartItemListPlaceholders', 1],
    ['excludedTConstructBattleSignInternalBlockItemListEntries', 1],
    ['excludedTConstructHeldItemInternalBlockItemListEntries', 1],
    ['excludedThaumcraftBlockHoleInternalBlockItemListEntries', 1],
    ['excludedThaumcraftEldritchPortalInternalBlockItemListEntries', 1],
    ['excludedThaumicHorizonsBaseLightInternalBlockItemListEntries', 1],
    ['excludedThaumicHorizonsSolarLightInternalBlockItemListEntries', 1],
    ['excludedTwilightForestExperiment115InternalBlockItemListEntries', 1],
    ['excludedWitchingGadgetsCustomAirInternalBlockItemListEntries', 1],
    ['excludedBotaniaBifrostItemListWorldStateEntries', 1],
    ['excludedBotaniaBuriedPetalsItemListWorldStateVariants', 16],
    ['excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask', 65535],
    ['excludedBotaniaCacophoniumBlockItemListWorldStateEntries', 1],
    ['excludedBotaniaEnchanterItemListWorldStateEntries', 1],
    ['excludedBotaniaFakeAirItemListWorldStateEntries', 1],
    ['excludedBotaniaManaFlameItemListWorldStateEntries', 1],
    ['excludedBotaniaSolidVineItemListWorldStateEntries', 1],
    ['excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders', 1],
    ['adaptedBotaniaCocoonItemIcons', 1],
    ['adaptedBotaniaPrismItemIcons', 1],
    ['adaptedGalacticraftFlagItemIcons', 1],
    ['adaptedWrcbeTriangulatorItemIcons', 1],
    ['adaptedModernMarkingsCrossingItemIcons', 6],
    ['adaptedThaumcraftRunedStoneItemIcons', 1],
    ['adaptedForestryScannedSaplingDisplayNames', 1],
    ['gregTechForestryScannedSaplingRecipeOccurrences', 1],
    ['adaptedForestryScannedPollenDisplayNames', 1],
    ['gregTechForestryScannedPollenRecipeOccurrences', 1],
    ['adaptedProjectBlueControlPanelItemIcons', 3],
    ['adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations', 3],
    ['adaptedIc2FluidCannerRecipeWidgetRenderInvocations', 5],
    ['adaptedBuildCraftPhasedFacadeItemIcons', 4],
    ['adaptedMobsInfoInfernalPreviewOutputIcons', 58],
    ['adaptedMobsInfoPreviewSlotIcons', 6093],
    ['adaptedDraconicMobSoulItemIcons', 363],
    ['adaptedGendustryLiquifierRecipes', 40],
    ['adaptedGendustryMutagenProducerRecipes', 15],
    ['adaptedGendustryExtractorRecipes', 1578],
    ['adaptedGendustryReplicatorRecipes', 3],
    ['adaptedGendustryTransposerRecipes', 8],
    ['adaptedGendustryMutatronRecipes', 705],
    ['adaptedGendustrySamplerRecipes', 9216],
    ['adaptedGendustryImprinterRecipes', 1],
    ['normalizedTcnaAspectCostHandlerCategories', 4],
    ['gregTechFuelSinkRecipes', 289],
    ['gregTechFuelSinkCategories', 14],
    ['gregTechLargeBoilerFuelSinkRecipes', 49],
    ['gregTechLargeBoilerFuelSinkCategories', 1],
    ['gregTechRadioHatchInformationRecipes', 104],
    ['gregTechQuantumComponentInformationRecipes', 27],
    ['gregTechSpaceProjectInformationRecipes', 2],
    ['gregTechOutputlessSemanticCategories', 18],
    ['gregTechOutputlessSemanticRecipes', 471],
    ['excludedGregTechLargeBoilerPresentationRows', 1],
    ['excludedGregTechUnregisteredDoorRecyclingRows', 5],
    ['excludedOwnerInternalFurnaceFuelRows', 5],
    ['excludedAe2EnderIoInternalConduitFacadeRows', 1],
    ['excludedUnregisteredGregTechMachineCatalysts', 1],
    ['informationalEmptyOutputRecipes', 513],
  ]) {
    if (isNonNegativeSafeInteger(nei?.[name]) && nei[name] !== expected) {
      issues.push(
        `${label} requires diagnostics.nei.${name} to be ${expected}; received ${nei[name]}.`,
      );
    }
  }
  if (
    isNonNegativeSafeInteger(nei?.adaptedBotaniaCocoonRecipeWidgetRenderInvocations) &&
    nei.adaptedBotaniaCocoonRecipeWidgetRenderInvocations === 0
  ) {
    issues.push(
      `${label} requires diagnostics.nei.` +
        'adaptedBotaniaCocoonRecipeWidgetRenderInvocations to be positive.',
    );
  }
  if (
    isNonNegativeSafeInteger(nei?.adaptedBotaniaPrismRecipeWidgetRenderInvocations) &&
    nei.adaptedBotaniaPrismRecipeWidgetRenderInvocations === 0
  ) {
    issues.push(
      `${label} requires diagnostics.nei.` +
        'adaptedBotaniaPrismRecipeWidgetRenderInvocations to be positive.',
    );
  }
  if (
    isNonNegativeSafeInteger(nei?.adaptedGalacticraftFlagRecipeWidgetRenderInvocations) &&
    nei.adaptedGalacticraftFlagRecipeWidgetRenderInvocations === 0
  ) {
    issues.push(
      `${label} requires diagnostics.nei.` +
        'adaptedGalacticraftFlagRecipeWidgetRenderInvocations to be positive.',
    );
  }
  if (
    isNonNegativeSafeInteger(nei?.adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations) &&
    nei.adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations === 0
  ) {
    issues.push(
      `${label} requires diagnostics.nei.` +
        'adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations to be positive.',
    );
  }
  for (const name of [
    'normalizedTcnaAspectCostInputOccurrences',
    'normalizedTcnaAspectCostDistinctKeys',
  ]) {
    if (isNonNegativeSafeInteger(nei?.[name]) && nei[name] === 0) {
      issues.push(`${label} requires diagnostics.nei.${name} to be positive.`);
    }
  }
  const gregTechOutputlessRecipeKeys = [
    'gregTechFuelSinkRecipes',
    'gregTechLargeBoilerFuelSinkRecipes',
    'gregTechRadioHatchInformationRecipes',
    'gregTechQuantumComponentInformationRecipes',
    'gregTechSpaceProjectInformationRecipes',
  ];
  if (
    gregTechOutputlessRecipeKeys.every(name => isNonNegativeSafeInteger(nei?.[name])) &&
    isNonNegativeSafeInteger(nei?.gregTechOutputlessSemanticRecipes)
  ) {
    const expectedGregTechOutputlessSemanticRecipes = gregTechOutputlessRecipeKeys.reduce(
      (total, name) => total + nei[name],
      0,
    );
    if (nei.gregTechOutputlessSemanticRecipes !== expectedGregTechOutputlessSemanticRecipes) {
      issues.push(
        `${label} diagnostics.nei.gregTechOutputlessSemanticRecipes ` +
          `(${nei.gregTechOutputlessSemanticRecipes}) must equal the five GregTech outputless ` +
          `semantic recipe counters (${expectedGregTechOutputlessSemanticRecipes}).`,
      );
    }
  }
  if (
    isNonNegativeSafeInteger(nei?.knowledgeIndependentAspectNames) &&
    nei.knowledgeIndependentAspectNames !== 69
  ) {
    issues.push(
      `${label} requires diagnostics.nei.knowledgeIndependentAspectNames to be 69; ` +
        `received ${nei.knowledgeIndependentAspectNames}.`,
    );
  }

  return issues;
}

/**
 * Return profile-specific publication blockers. General graph and asset
 * integrity is enforced by validate-export-data; this policy covers the
 * explicit pack exporter contracts that generic datasets do not share.
 */
export function exportQualityIssues(
  {manifest, failures, warnings, semanticErrorRecipes = 0},
  profile,
) {
  const resolvedProfile = resolveQualityProfile(profile);
  if (resolvedProfile === null) return [];
  const requirements = profileRequirements.get(resolvedProfile);
  const {label} = requirements;

  const issues = [];
  if (manifest?.format !== requirements.format) {
    issues.push(
      `${label} requires manifest.format ${requirements.format}; received ${String(
        manifest?.format,
      )}.`,
    );
  }
  if (manifest?.minecraft !== requirements.minecraft) {
    issues.push(
      `${label} requires manifest.minecraft ${JSON.stringify(requirements.minecraft)}; ` +
        `received ${JSON.stringify(manifest?.minecraft)}.`,
    );
  }
  if (manifest?.aborted !== false) {
    issues.push(`${label} requires manifest.aborted to be false.`);
  }
  if (manifest?.settings?.iconScale !== requirements.iconScale) {
    const itemCanvas = 16 * requirements.iconScale;
    issues.push(
      `${label} requires 16×16 source textures rendered into ${itemCanvas}×${itemCanvas} ` +
        `item canvases (settings.iconScale ${requirements.iconScale}); received ${String(
        manifest?.settings?.iconScale,
      )}.`,
    );
  }
  if (manifest?.settings?.recipeScale !== requirements.recipeScale) {
    issues.push(
      `${label} requires ${requirements.recipeViewer} layouts rendered at ` +
        `${requirements.recipeScale}× physical resolution ` +
        `(settings.recipeScale ${requirements.recipeScale}); received ${String(
        manifest?.settings?.recipeScale,
      )}.`,
    );
  }
  for (const [name, expected] of Object.entries(requirements.provenance ?? {})) {
    if (manifest?.[name] !== expected) {
      issues.push(
        `${label} requires manifest.${name} ${JSON.stringify(expected)}; received ` +
          `${JSON.stringify(manifest?.[name])}.`,
      );
    }
  }
  if (requirements.packIdentity !== undefined) {
    if (!hasExactKeys(manifest?.pack, Object.keys(requirements.packIdentity))) {
      issues.push(
        `${label} requires manifest.pack to contain exactly ` +
          `${Object.keys(requirements.packIdentity).join(', ')}.`,
      );
    } else {
      for (const [name, expected] of Object.entries(requirements.packIdentity)) {
        if (manifest.pack[name] !== expected) {
          issues.push(
            `${label} requires manifest.pack.${name} ${JSON.stringify(expected)}; received ` +
              `${JSON.stringify(manifest.pack[name])}.`,
          );
        }
      }
    }
  }

  const diagnostics = manifest?.diagnostics;
  if (!isRecord(diagnostics)) {
    issues.push(`${label} requires manifest.diagnostics from its exporter.`);
  } else {
    if (
      Array.isArray(requirements.diagnosticKeys) &&
      !hasExactKeys(diagnostics, requirements.diagnosticKeys)
    ) {
      issues.push(
        `${label} requires manifest.diagnostics to contain exactly ` +
          `${requirements.diagnosticKeys.join(', ')}.`,
      );
    }
    if (!Number.isSafeInteger(diagnostics.failureEvents) || diagnostics.failureEvents < 0) {
      issues.push(`${label} requires a non-negative diagnostics.failureEvents count.`);
    }
    if (
      !Number.isSafeInteger(diagnostics.failureEventsOmitted) ||
      diagnostics.failureEventsOmitted < 0
    ) {
      issues.push(`${label} requires a non-negative diagnostics.failureEventsOmitted count.`);
    } else if (diagnostics.failureEventsOmitted !== 0) {
      issues.push(
        `${label} export omitted ${diagnostics.failureEventsOmitted} failure event(s); ` +
          'publication requires the complete diagnostics set.',
      );
    }
    if (requirements.requiresZeroFailures && diagnostics.failureEvents !== 0) {
      issues.push(
        `${label} requires diagnostics.failureEvents to be 0; received ` +
          `${String(diagnostics.failureEvents)}.`,
      );
    }
    if (resolvedProfile === MULTIBLOCK_MADNESS_112_PROFILE) {
      if (!isNonNegativeSafeInteger(diagnostics.warningEvents)) {
        issues.push(`${label} requires a non-negative diagnostics.warningEvents count.`);
      }
      if (!isNonNegativeSafeInteger(diagnostics.warningEventsOmitted)) {
        issues.push(`${label} requires a non-negative diagnostics.warningEventsOmitted count.`);
      } else if (diagnostics.warningEventsOmitted !== 0) {
        issues.push(
          `${label} export omitted ${diagnostics.warningEventsOmitted} warning event(s); ` +
            'publication requires the complete audited warning set.',
        );
      }
    }
    if (
      resolvedProfile === MULTIBLOCK_MADNESS_2_118_PROFILE &&
      diagnostics.transparentIcons !== 0
    ) {
      issues.push(
        `${label} requires diagnostics.transparentIcons to be 0; received ` +
          `${String(diagnostics.transparentIcons)}.`,
      );
    }
    if (
      resolvedProfile === MULTIBLOCK_MADNESS_2_118_PROFILE &&
      !isNonNegativeSafeInteger(diagnostics.nativeIconCorrections)
    ) {
      issues.push(`${label} requires a non-negative diagnostics.nativeIconCorrections count.`);
    }
  }

  if (requirements.requiresZeroFailures && manifest?.counts?.failures !== 0) {
    issues.push(
      `${label} requires manifest.counts.failures to be 0; received ` +
        `${String(manifest?.counts?.failures)}.`,
    );
  }

  if (resolvedProfile === GTNH_1710_PROFILE) {
    issues.push(...gtnhManifestQualityIssues(manifest, label));
  } else if (resolvedProfile === GENERIC_JEI_120_PROFILE) {
    issues.push(...genericJeiManifestQualityIssues(manifest, label));
  }

  if (resolvedProfile === MULTIBLOCK_MADNESS_112_PROFILE) {
    if (!Array.isArray(warnings)) {
      issues.push(`${label} requires warnings.json to contain an array.`);
    } else {
      const malformedWarningIndex = warnings.findIndex(
        warning => typeof warning !== 'string' || warning.trim().length === 0,
      );
      if (malformedWarningIndex >= 0) {
        issues.push(
          `${label} warnings.json[${malformedWarningIndex}] must be a non-empty string.`,
        );
      }
      if (
        isNonNegativeSafeInteger(manifest?.diagnostics?.warningEvents) &&
        warnings.length !== manifest.diagnostics.warningEvents
      ) {
        issues.push(
          `${label} diagnostics.warningEvents (${manifest.diagnostics.warningEvents}) must ` +
            `equal warnings.json length (${warnings.length}).`,
        );
      }
      const unknownWarningIndex = warnings.findIndex(
        warning =>
          typeof warning === 'string' &&
          !MULTIBLOCK_MADNESS_112_WARNING_PREFIXES.some(
            prefix => warning === prefix || warning.startsWith(`${prefix} `),
          ),
      );
      if (unknownWarningIndex >= 0) {
        issues.push(
          `${label} warnings.json[${unknownWarningIndex}] has an unrecognized warning class: ` +
            `${JSON.stringify(warnings[unknownWarningIndex])}. Allowed prefixes: ` +
            `${MULTIBLOCK_MADNESS_112_WARNING_PREFIXES.join(', ')}.`,
        );
      }
    }
  }

  if (!Array.isArray(failures)) {
    issues.push(`${label} requires failures.json to contain an array.`);
    return issues;
  }

  if (requirements.requiresZeroFailures && failures.length !== 0) {
    issues.push(
      `${label} requires failures.json to be empty; received ${failures.length} ` +
        `failure event(s); first: ${String(failures[0])}`,
    );
  }

  if (resolvedProfile === GTNH_1710_PROFILE && failures.length !== 0) {
    issues.push(
      `${label} requires failures.json to be empty; received ${failures.length} ` +
        `failure event(s); first: ${String(failures[0])}`,
    );
  }
  const categoryFailures = failures.filter(
    message => typeof message === 'string' && isCategoryFailure(message),
  );
  if (categoryFailures.length > 0) {
    issues.push(
      `${label} export contains ${categoryFailures.length} category failure(s); first: ` +
        categoryFailures[0],
    );
  }

  const quantityFallbacks = failures.filter(
    message => typeof message === 'string' && isQuantityFallback(message),
  );
  if (quantityFallbacks.length > 0) {
    issues.push(
      `${label} export contains ${quantityFallbacks.length} ingredient-quantity ` +
        `fallback(s); first: ${quantityFallbacks[0]}`,
    );
  }

  const recipePreviewFailures = failures.filter(
    message => typeof message === 'string' && isRecipePreviewFailure(message),
  );
  if (recipePreviewFailures.length > 0) {
    issues.push(
      `${label} export contains ${recipePreviewFailures.length} recipe-preview render/write ` +
        `failure(s); first: ${recipePreviewFailures[0]}`,
    );
  }

  const unclassifiedZeros = failures.filter(
    message => typeof message === 'string' && isUnclassifiedZeroQuantity(message),
  );
  if (unclassifiedZeros.length > 0) {
    issues.push(
      `${label} export contains ${unclassifiedZeros.length} unclassified zero-quantity ` +
        `context(s); first: ${unclassifiedZeros[0]}`,
    );
  }

  const catalogFailures = failures.filter(
    message => typeof message === 'string' && isCatalogCompletenessFailure(message),
  );
  if (catalogFailures.length > 0) {
    issues.push(
      `${label} export contains ${catalogFailures.length} incomplete ingredient-catalog ` +
        `failure(s); first: ${catalogFailures[0]}`,
    );
  }

  const semanticFailures = failures.filter(
    message => typeof message === 'string' && isSemanticFailure(message),
  );
  if (semanticFailures.length > 0) {
    issues.push(
      `${label} export contains ${semanticFailures.length} ingredient-semantics ` +
        `failure(s); first: ${semanticFailures[0]}`,
    );
  }
  if (!Number.isSafeInteger(semanticErrorRecipes) || semanticErrorRecipes < 0) {
    issues.push(`${label} semantic-error recipe count is invalid.`);
  } else if (semanticErrorRecipes > 0) {
    issues.push(
      `${label} export contains ${semanticErrorRecipes} recipe(s) marked err=true; ` +
        'publication requires complete input/output semantics.',
    );
  }

  return issues;
}
