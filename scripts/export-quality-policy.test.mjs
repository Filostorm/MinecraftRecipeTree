import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import test from 'node:test';
import {
  EXPORT_QUALITY_PROFILE_IDS,
  exportQualityIssues,
  GENERIC_JEI_120_PROFILE,
  GTNH_DATA_ATTRIBUTION,
  GTNH_284_HANDLER_POLICIES,
  GTNH_NEI_DIAGNOSTIC_KEYS,
  GTNH_1710_PROFILE,
  GTNH_KNOWLEDGE_POLICY,
  MEATBALLCRAFT_112_PROFILE,
  MULTIBLOCK_MADNESS_112_PROFILE,
  MULTIBLOCK_MADNESS_112_WARNING_PREFIXES,
  MULTIBLOCK_MADNESS_2_118_PROFILE,
  MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNING_PREFIX,
  MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNINGS,
  MULTIBLOCK_MADNESS_2_118_ICON_CORRECTION_WARNING_PREFIXES,
  MULTIBLOCK_MADNESS_2_118_ICON_OMISSION_IDS,
  MULTIBLOCK_MADNESS_2_118_ICON_OMISSIONS,
  MULTIBLOCK_MADNESS_2_118_WARNING_PREFIXES,
  qualityProfileRequirementsFor,
  resolveQualityProfile,
} from './export-quality-policy.mjs';

const validManifest = {
  format: 1,
  minecraft: '1.12.2',
  aborted: false,
  settings: {iconScale: 3, recipeScale: 2},
  diagnostics: {failureEvents: 2, failureEventsOmitted: 0},
};

function validMm2Warnings(
  nativeIconCorrections = 2,
  categoricalWarnings = MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNINGS,
) {
  return [
    ...MULTIBLOCK_MADNESS_2_118_ICON_OMISSIONS.map(
      ({id, type, valueClass, itemClass, blockClass}) =>
        `UPSTREAM_NATIVE_ICON_UNAVAILABLE id=${id} type=${type} ` +
        `valueClass=${valueClass} itemClass=${itemClass ?? '<none>'} ` +
        `blockClass=${blockClass ?? '<none>'} visiblePixels=0 ` +
        'contract=audited fixture omission; exact native 16x16 render has zero visible ' +
        'pixels; omitted PNG/icon field; named UI fallback used',
    ),
    ...Array.from(
      {length: nativeIconCorrections},
      (_, index) =>
        `${MULTIBLOCK_MADNESS_2_118_ICON_CORRECTION_WARNING_PREFIXES[
          index % MULTIBLOCK_MADNESS_2_118_ICON_CORRECTION_WARNING_PREFIXES.length
        ]} ` +
        `entry=fixture:corrected_${index}`,
    ),
    ...categoricalWarnings,
  ];
}

test('accepts the exact MeatballCraft 1.12.2 exporter contract', () => {
  assert.deepEqual(
    exportQualityIssues(
      {manifest: validManifest, failures: ['item icon x', 'recipe image y'], semanticErrorRecipes: 0},
      MEATBALLCRAFT_112_PROFILE,
    ),
    [],
  );
});

test('registers explicit immutable requirements for all production pack profiles', () => {
  assert.deepEqual(EXPORT_QUALITY_PROFILE_IDS, [
    GENERIC_JEI_120_PROFILE,
    MEATBALLCRAFT_112_PROFILE,
    MULTIBLOCK_MADNESS_112_PROFILE,
    MULTIBLOCK_MADNESS_2_118_PROFILE,
    GTNH_1710_PROFILE,
  ]);
  assert.deepEqual(qualityProfileRequirementsFor(GENERIC_JEI_120_PROFILE), {
    id: GENERIC_JEI_120_PROFILE,
    label: 'Generic JEI 1.20.1',
    minecraft: '1.20.1',
    format: 1,
    iconScale: 4,
    recipeScale: 2,
    recipeViewer: 'JEI',
    corpus: 'dynamic-complete',
    requiresPackIdentity: true,
  });
  assert.deepEqual(qualityProfileRequirementsFor(MEATBALLCRAFT_112_PROFILE), {
    id: MEATBALLCRAFT_112_PROFILE,
    label: 'MeatballCraft',
    minecraft: '1.12.2',
    format: 1,
    iconScale: 3,
    recipeScale: 2,
    recipeViewer: 'HEI',
    corpus: 'exact',
    requiresExporterBuildIdentity: true,
  });
  assert.deepEqual(qualityProfileRequirementsFor(MULTIBLOCK_MADNESS_112_PROFILE), {
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
    diagnosticKeys: [
      'failureEvents',
      'failureEventsOmitted',
      'warningEvents',
      'warningEventsOmitted',
    ],
    packIdentity: {
      name: 'Multiblock Madness',
      version: '3.2.3',
      identitySource: 'explicit-request',
    },
  });
  assert.deepEqual(qualityProfileRequirementsFor(MULTIBLOCK_MADNESS_2_118_PROFILE), {
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
    diagnosticKeys: [
      'failureEvents',
      'failureEventsOmitted',
      'nativeIconCorrections',
      'transparentIcons',
    ],
    packIdentity: {
      name: 'Multiblock Madness 2',
      version: '1.0.0',
      identitySource: 'explicit-request',
    },
  });
  assert.deepEqual(qualityProfileRequirementsFor(GTNH_1710_PROFILE), {
    id: GTNH_1710_PROFILE,
    label: 'GT New Horizons',
    minecraft: '1.7.10',
    format: 2,
    iconScale: 3,
    recipeScale: 2,
    recipeViewer: 'NEI',
    corpus: 'dynamic-complete',
    requiresExporterBuildIdentity: true,
    provenance: {
      profile: GTNH_1710_PROFILE,
      forge: '10.13.4.1614',
      nei: '2.8.44-GTNH',
    },
    packIdentity: {
      name: 'GT New Horizons',
      version: '2.8.4',
      identitySource: 'explicit-request',
    },
    attribution: GTNH_DATA_ATTRIBUTION,
  });
});

function validGenericJeiManifest() {
  return {
    format: 1,
    minecraft: '1.20.1',
    pack: {
      name: 'Example Modern Pack',
      version: '4.2.0',
      identitySource: 'explicit-request',
    },
    aborted: false,
    settings: {iconScale: 4, recipeScale: 2, mobCanvas: 256},
    counts: {items: 3, recipes: 2, categories: 1, mobs: 0, blockDrops: 0, failures: 1},
    diagnostics: {failureEvents: 1, failureEventsOmitted: 0},
  };
}

test('accepts the strict generic JEI 1.20.1 manifest telemetry and pack identity', () => {
  assert.deepEqual(
    exportQualityIssues(
      {
        manifest: validGenericJeiManifest(),
        failures: ['mob example:missing_renderer: renderer unavailable'],
        semanticErrorRecipes: 0,
      },
      GENERIC_JEI_120_PROFILE,
    ),
    [],
  );
});

test('generic JEI profile rejects drifted diagnostics, counts, and identity', () => {
  const manifest = validGenericJeiManifest();
  manifest.counts.futureCounter = 1;
  manifest.diagnostics.futureCounter = 1;
  manifest.diagnostics.failureEvents = 0;
  manifest.pack.identitySource = 'untrusted-launcher';
  const issues = exportQualityIssues(
    {manifest, failures: [], semanticErrorRecipes: 0},
    GENERIC_JEI_120_PROFILE,
  );
  assert.match(issues.join('\n'), /manifest\.counts must contain exactly/);
  assert.match(issues.join('\n'), /manifest\.diagnostics must contain exactly/);
  assert.match(issues.join('\n'), /failureEvents \(0\).*counts\.failures \(1\)/);
  assert.match(issues.join('\n'), /identitySource/);
});

function validGtnhManifest() {
  return {
    format: 2,
    minecraft: '1.7.10',
    profile: GTNH_1710_PROFILE,
    forge: '10.13.4.1614',
    nei: '2.8.44-GTNH',
    pack: {
      name: 'GT New Horizons',
      version: '2.8.4',
      identitySource: 'explicit-request',
    },
    attribution: {...GTNH_DATA_ATTRIBUTION},
    handlerPolicies: structuredClone(GTNH_284_HANDLER_POLICIES),
    knowledgePolicy: {...GTNH_KNOWLEDGE_POLICY},
    aborted: false,
    settings: {iconScale: 3, recipeScale: 2},
    counts: {items: 2, recipes: 3, categories: 1, mobs: 0, blockDrops: 0, failures: 0},
    diagnostics: {
      failureEvents: 0,
      failureEventsOmitted: 0,
      nei: {
        itemListLoaded: true,
        itemListRawEntries: 49,
        itemListExcludedEntries: 46,
        itemListRetainedEntries: 3,
        itemListRetainedUniqueIdentities: 2,
        registeredCraftingHandlers: 44,
        exportableCraftingHandlers: 1,
        adaptedHandlerCategories: 45,
        excludedNonRecipeHandlers: 20,
        excludedEmptyRecipeHandlers: 22,
        excludedUnboundTemplateRecipeHandlers: 1,
        excludedAe2fcFluidDropItemListPlaceholders: 1,
        excludedAe2fcFluidPacketItemListPlaceholders: 1,
        excludedAe2CableBusInternalBlockItemListEntries: 1,
        excludedAe2MatrixFrameInternalBlockItemListEntries: 1,
        excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders: 1,
        excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries: 1,
        excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders: 1,
        malisisDoorsUnconfiguredCustomDoorRecipeReferences: 0,
        malisisDoorsUnconfiguredCustomDoorQuestReferences: 0,
        excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders: 1,
        malisisDoorsUnconfiguredMixedBlockRecipeReferences: 0,
        malisisDoorsUnconfiguredMixedBlockQuestReferences: 0,
        excludedBloodMagicBloodLightItemListHelpers: 1,
        excludedBloodMagicSpectralContainerItemListHelpers: 1,
        excludedArchitectureCraftCladdingItemListPlaceholders: 1,
        excludedAvaritiaEmptyMatterClusterItemListPlaceholders: 1,
        excludedCarpentersBedInternalBlockItemListEntries: 1,
        excludedCarpentersDoorInternalBlockItemListEntries: 1,
        excludedStevesCartsUnconfiguredModularCartItemListPlaceholders: 1,
        excludedTConstructBattleSignInternalBlockItemListEntries: 1,
        excludedTConstructHeldItemInternalBlockItemListEntries: 1,
        excludedThaumcraftBlockHoleInternalBlockItemListEntries: 1,
        excludedThaumcraftEldritchPortalInternalBlockItemListEntries: 1,
        excludedThaumicHorizonsBaseLightInternalBlockItemListEntries: 1,
        excludedThaumicHorizonsSolarLightInternalBlockItemListEntries: 1,
        excludedTwilightForestExperiment115InternalBlockItemListEntries: 1,
        excludedWitchingGadgetsCustomAirInternalBlockItemListEntries: 1,
        excludedBotaniaBifrostItemListWorldStateEntries: 1,
        excludedBotaniaBuriedPetalsItemListWorldStateVariants: 16,
        excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask: 65535,
        excludedBotaniaCacophoniumBlockItemListWorldStateEntries: 1,
        excludedBotaniaEnchanterItemListWorldStateEntries: 1,
        excludedBotaniaFakeAirItemListWorldStateEntries: 1,
        excludedBotaniaManaFlameItemListWorldStateEntries: 1,
        excludedBotaniaSolidVineItemListWorldStateEntries: 1,
        excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders: 1,
        adaptedBotaniaCocoonItemIcons: 1,
        adaptedBotaniaCocoonRecipeWidgetRenderInvocations: 3,
        adaptedBotaniaPrismItemIcons: 1,
        adaptedBotaniaPrismRecipeWidgetRenderInvocations: 3,
        adaptedGalacticraftFlagItemIcons: 1,
        adaptedGalacticraftFlagRecipeWidgetRenderInvocations: 3,
        adaptedWrcbeTriangulatorItemIcons: 1,
        adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations: 3,
        adaptedModernMarkingsCrossingItemIcons: 6,
        adaptedThaumcraftRunedStoneItemIcons: 1,
        adaptedForestryScannedSaplingDisplayNames: 1,
        gregTechForestryScannedSaplingRecipeOccurrences: 1,
        adaptedForestryScannedPollenDisplayNames: 1,
        gregTechForestryScannedPollenRecipeOccurrences: 1,
        adaptedProjectBlueControlPanelItemIcons: 3,
        adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations: 3,
        adaptedIc2FluidCannerRecipeWidgetRenderInvocations: 5,
        adaptedBuildCraftPhasedFacadeItemIcons: 4,
        adaptedMobsInfoInfernalPreviewOutputIcons: 58,
        adaptedMobsInfoPreviewSlotIcons: 6093,
        adaptedDraconicMobSoulItemIcons: 363,
        adaptedGendustryLiquifierRecipes: 40,
        adaptedGendustryMutagenProducerRecipes: 15,
        adaptedGendustryExtractorRecipes: 1578,
        adaptedGendustryReplicatorRecipes: 3,
        adaptedGendustryTransposerRecipes: 8,
        adaptedGendustryMutatronRecipes: 705,
        adaptedGendustrySamplerRecipes: 9216,
        adaptedGendustryImprinterRecipes: 1,
        normalizedTcnaAspectCostInputOccurrences: 2,
        normalizedTcnaAspectCostDistinctKeys: 1,
        normalizedTcnaAspectCostHandlerCategories: 4,
        gregTechFuelSinkRecipes: 289,
        gregTechFuelSinkCategories: 14,
        gregTechLargeBoilerFuelSinkRecipes: 49,
        gregTechLargeBoilerFuelSinkCategories: 1,
        gregTechRadioHatchInformationRecipes: 104,
        gregTechQuantumComponentInformationRecipes: 27,
        gregTechSpaceProjectInformationRecipes: 2,
        gregTechOutputlessSemanticCategories: 18,
        gregTechOutputlessSemanticRecipes: 471,
        excludedGregTechLargeBoilerPresentationRows: 1,
        excludedGregTechUnregisteredDoorRecyclingRows: 5,
        excludedOwnerInternalFurnaceFuelRows: 5,
        excludedAe2EnderIoInternalConduitFacadeRows: 1,
        excludedUnregisteredGregTechMachineCatalysts: 1,
        loadedCategories: 1,
        recipesEnumerated: 3,
        recipeWidgetsRendered: 3,
        itemIconsRendered: 2,
        informationalEmptyOutputRecipes: 513,
        knowledgeIndependentAspectNames: 69,
        unloadedHandlerCategories: 0,
        ambiguousHandlerCategories: 0,
        duplicateHandlerCategories: 0,
      },
    },
  };
}

test('accepts only a complete zero-failure GTNH NEI export contract', () => {
  assert.equal(GTNH_284_HANDLER_POLICIES.length, 66);
  assert.equal(
    createHash('sha256').update(JSON.stringify(GTNH_284_HANDLER_POLICIES)).digest('hex'),
    '51bcb52d2ee4e5d56c45d1dbe280a77b9d958000d1f4248d4793998c34912c9c',
  );
  assert.deepEqual(
    GTNH_284_HANDLER_POLICIES.find(
      policy => policy.handlerClass === 'binnie.genetics.nei.AnalyserRecipeHandler',
    ),
    {
      handlerClass: 'binnie.genetics.nei.AnalyserRecipeHandler',
      handlerId: 'binnie.genetics.nei.AnalyserRecipeHandler',
      action: 'adapted-informational-category',
      contract: 'adapter:binnie-genetics-2.5.24-analyser-in-place-genetic-information-v1',
    },
  );
  assert.deepEqual(GTNH_284_HANDLER_POLICIES[24], {
    handlerClass: 'crazypants.enderio.nei.VatRecipeHandler',
    handlerId: 'crazypants.enderio.nei.VatRecipeHandler',
    action: 'adapted-complete-category',
    contract: 'adapter:enderio-2.9.28-vat-fluid-semantics-v1',
  });
  assert.deepEqual(GTNH_KNOWLEDGE_POLICY, {
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
  assert.deepEqual(
    exportQualityIssues(
      {manifest: validGtnhManifest(), failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ),
    [],
  );
});

test('GTNH policy identity permits shared runtime classes but rejects duplicate composites and reordering', () => {
  const galaxyPolicies = GTNH_284_HANDLER_POLICIES.filter(
    policy => policy.handlerClass === 'galaxyspace.core.nei.RocketRecipeHandler',
  );
  assert.equal(galaxyPolicies.length, 8);
  assert.equal(new Set(galaxyPolicies.map(policy => policy.handlerId)).size, 8);
  assert.equal(
    GTNH_284_HANDLER_POLICIES.some(policy =>
      policy.handlerClass.startsWith('micdoodle8.mods.galacticraft.'),
    ),
    true,
  );

  const duplicate = validGtnhManifest();
  duplicate.handlerPolicies[1] = structuredClone(duplicate.handlerPolicies[0]);
  assert.match(
    exportQualityIssues(
      {manifest: duplicate, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /duplicates composite handlerClass\/handlerId identity from index 0/,
  );

  const reordered = validGtnhManifest();
  const firstGalaxyIndex = reordered.handlerPolicies.findIndex(
    policy => policy.handlerId === 'galaxyspace.core.nei.rocket.RocketT1RecipeHandler',
  );
  [reordered.handlerPolicies[firstGalaxyIndex], reordered.handlerPolicies[firstGalaxyIndex + 1]] =
    [reordered.handlerPolicies[firstGalaxyIndex + 1], reordered.handlerPolicies[firstGalaxyIndex]];
  assert.match(
    exportQualityIssues(
      {manifest: reordered, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    new RegExp(`handlerPolicies\\[${firstGalaxyIndex}\\]\\.handlerId`),
  );
});

test('GTNH rejects handler-policy, knowledge-policy, and adapter telemetry drift', () => {
  const manifest = validGtnhManifest();
  manifest.counts.mobs = 1;
  manifest.counts.blockDrops = 1;
  manifest.handlerPolicies[1].contract = 'query-only:unreviewed-fallback';
  manifest.knowledgePolicy.playerResearchMutated = true;
  manifest.knowledgePolicy.forestryScannedPollenDisplayName =
    'unreviewed-global-display-name-fallback';
  manifest.knowledgePolicy.forestryScannedPollenSourceBinding =
    'unreviewed-source-binding';
  manifest.knowledgePolicy.itemAspectRecipeSemantics = 'unreviewed-semantic-fallback';
  manifest.knowledgePolicy.gregTechOutputlessRecipeSemantics = 'unreviewed-semantic-fallback';
  manifest.knowledgePolicy.gregTechStaleDoorRecyclingExclusion =
    'unreviewed-row-normalization';
  manifest.diagnostics.nei.registeredCraftingHandlers = 1;
  manifest.diagnostics.nei.adaptedHandlerCategories = 1;
  manifest.diagnostics.nei.excludedNonRecipeHandlers = 1;
  manifest.diagnostics.nei.excludedEmptyRecipeHandlers = 1;
  manifest.diagnostics.nei.excludedUnboundTemplateRecipeHandlers = 0;
  manifest.diagnostics.nei.excludedAe2fcFluidDropItemListPlaceholders = 2;
  manifest.diagnostics.nei.excludedAe2fcFluidPacketItemListPlaceholders = 0;
  manifest.diagnostics.nei.excludedAe2CableBusInternalBlockItemListEntries = 0;
  manifest.diagnostics.nei.excludedAe2MatrixFrameInternalBlockItemListEntries = 0;
  manifest.diagnostics.nei.excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders = 0;
  manifest.diagnostics.nei.excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries = 0;
  manifest.diagnostics.nei.excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders = 0;
  manifest.diagnostics.nei.excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders = 0;
  manifest.diagnostics.nei.excludedBloodMagicBloodLightItemListHelpers = 0;
  manifest.diagnostics.nei.excludedBloodMagicSpectralContainerItemListHelpers = 2;
  manifest.diagnostics.nei.excludedArchitectureCraftCladdingItemListPlaceholders = 0;
  manifest.diagnostics.nei.excludedAvaritiaEmptyMatterClusterItemListPlaceholders = 0;
  manifest.diagnostics.nei.excludedCarpentersBedInternalBlockItemListEntries = 0;
  manifest.diagnostics.nei.excludedCarpentersDoorInternalBlockItemListEntries = 0;
  manifest.diagnostics.nei.excludedStevesCartsUnconfiguredModularCartItemListPlaceholders = 0;
  manifest.diagnostics.nei.excludedTConstructBattleSignInternalBlockItemListEntries = 0;
  manifest.diagnostics.nei.excludedTConstructHeldItemInternalBlockItemListEntries = 0;
  manifest.diagnostics.nei.excludedThaumcraftBlockHoleInternalBlockItemListEntries = 0;
  manifest.diagnostics.nei.excludedThaumcraftEldritchPortalInternalBlockItemListEntries = 0;
  manifest.diagnostics.nei.excludedThaumicHorizonsBaseLightInternalBlockItemListEntries = 0;
  manifest.diagnostics.nei.excludedThaumicHorizonsSolarLightInternalBlockItemListEntries = 0;
  manifest.diagnostics.nei.excludedTwilightForestExperiment115InternalBlockItemListEntries = 0;
  manifest.diagnostics.nei.excludedWitchingGadgetsCustomAirInternalBlockItemListEntries = 0;
  manifest.diagnostics.nei.excludedBotaniaBifrostItemListWorldStateEntries = 0;
  manifest.diagnostics.nei.excludedBotaniaBuriedPetalsItemListWorldStateVariants = 15;
  manifest.diagnostics.nei.excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask = 65534;
  manifest.diagnostics.nei.excludedBotaniaCacophoniumBlockItemListWorldStateEntries = 0;
  manifest.diagnostics.nei.excludedBotaniaEnchanterItemListWorldStateEntries = 0;
  manifest.diagnostics.nei.excludedBotaniaFakeAirItemListWorldStateEntries = 0;
  manifest.diagnostics.nei.excludedBotaniaManaFlameItemListWorldStateEntries = 0;
  manifest.diagnostics.nei.excludedBotaniaSolidVineItemListWorldStateEntries = 0;
  manifest.diagnostics.nei.excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders =
    0;
  manifest.diagnostics.nei.adaptedBotaniaCocoonItemIcons = 0;
  manifest.diagnostics.nei.adaptedBotaniaCocoonRecipeWidgetRenderInvocations = 0;
  manifest.diagnostics.nei.adaptedBotaniaPrismItemIcons = 0;
  manifest.diagnostics.nei.adaptedBotaniaPrismRecipeWidgetRenderInvocations = 0;
  manifest.diagnostics.nei.adaptedGalacticraftFlagItemIcons = 0;
  manifest.diagnostics.nei.adaptedGalacticraftFlagRecipeWidgetRenderInvocations = 0;
  manifest.diagnostics.nei.adaptedWrcbeTriangulatorItemIcons = 0;
  manifest.diagnostics.nei.adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations = 0;
  manifest.diagnostics.nei.adaptedModernMarkingsCrossingItemIcons = 0;
  manifest.diagnostics.nei.adaptedThaumcraftRunedStoneItemIcons = 0;
  manifest.diagnostics.nei.adaptedForestryScannedSaplingDisplayNames = 0;
  manifest.diagnostics.nei.gregTechForestryScannedSaplingRecipeOccurrences = 0;
  manifest.diagnostics.nei.adaptedForestryScannedPollenDisplayNames = 0;
  manifest.diagnostics.nei.gregTechForestryScannedPollenRecipeOccurrences = 0;
  manifest.diagnostics.nei.adaptedProjectBlueControlPanelItemIcons = 0;
  manifest.diagnostics.nei.adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations = 0;
  manifest.diagnostics.nei.normalizedTcnaAspectCostInputOccurrences = 0;
  manifest.diagnostics.nei.normalizedTcnaAspectCostDistinctKeys = 0;
  manifest.diagnostics.nei.normalizedTcnaAspectCostHandlerCategories = 3;
  manifest.diagnostics.nei.gregTechFuelSinkRecipes = 0;
  manifest.diagnostics.nei.gregTechFuelSinkCategories = 13;
  manifest.diagnostics.nei.gregTechLargeBoilerFuelSinkRecipes = 0;
  manifest.diagnostics.nei.gregTechLargeBoilerFuelSinkCategories = 2;
  manifest.diagnostics.nei.gregTechRadioHatchInformationRecipes = 0;
  manifest.diagnostics.nei.gregTechQuantumComponentInformationRecipes = 26;
  manifest.diagnostics.nei.gregTechSpaceProjectInformationRecipes = 3;
  manifest.diagnostics.nei.gregTechOutputlessSemanticCategories = 17;
  manifest.diagnostics.nei.gregTechOutputlessSemanticRecipes = 470;
  manifest.diagnostics.nei.excludedGregTechLargeBoilerPresentationRows = 0;
  manifest.diagnostics.nei.excludedGregTechUnregisteredDoorRecyclingRows = 4;
  manifest.diagnostics.nei.informationalEmptyOutputRecipes = 0;
  manifest.diagnostics.nei.knowledgeIndependentAspectNames = 68;

  const issues = exportQualityIssues(
    {manifest, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(issues.join('\n'), /handlerPolicies\[1\]\.contract/);
  assert.match(issues.join('\n'), /manifest\.counts\.mobs.*0/);
  assert.match(issues.join('\n'), /manifest\.counts\.blockDrops.*0/);
  assert.match(issues.join('\n'), /knowledgePolicy\.playerResearchMutated/);
  assert.match(issues.join('\n'), /knowledgePolicy\.forestryScannedPollenDisplayName/);
  assert.match(issues.join('\n'), /knowledgePolicy\.forestryScannedPollenSourceBinding/);
  assert.match(issues.join('\n'), /knowledgePolicy\.itemAspectRecipeSemantics/);
  assert.match(issues.join('\n'), /knowledgePolicy\.gregTechOutputlessRecipeSemantics/);
  assert.match(issues.join('\n'), /knowledgePolicy\.gregTechStaleDoorRecyclingExclusion/);
  assert.match(
    issues.join('\n'),
    /registeredCraftingHandlers.*categories \+ diagnostics\.nei\.excludedNonRecipeHandlers \(20\) \+ diagnostics\.nei\.excludedEmptyRecipeHandlers \(22\) \+ diagnostics\.nei\.excludedUnboundTemplateRecipeHandlers \(1\)/,
  );
  assert.match(issues.join('\n'), /adaptedHandlerCategories.*45/);
  assert.match(issues.join('\n'), /excludedNonRecipeHandlers.*20/);
  assert.match(issues.join('\n'), /excludedEmptyRecipeHandlers.*22/);
  assert.match(issues.join('\n'), /excludedUnboundTemplateRecipeHandlers.*1/);
  assert.match(issues.join('\n'), /excludedAe2fcFluidDropItemListPlaceholders.*1/);
  assert.match(issues.join('\n'), /excludedAe2fcFluidPacketItemListPlaceholders.*1/);
  assert.match(issues.join('\n'), /excludedAe2CableBusInternalBlockItemListEntries.*1/);
  assert.match(issues.join('\n'), /excludedAe2MatrixFrameInternalBlockItemListEntries.*1/);
  assert.match(
    issues.join('\n'),
    /excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders.*1/,
  );
  assert.match(issues.join('\n'), /excludedBloodMagicBloodLightItemListHelpers.*1/);
  assert.match(
    issues.join('\n'),
    /excludedBloodMagicSpectralContainerItemListHelpers.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedArchitectureCraftCladdingItemListPlaceholders.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedAvaritiaEmptyMatterClusterItemListPlaceholders.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedCarpentersBedInternalBlockItemListEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedCarpentersDoorInternalBlockItemListEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedStevesCartsUnconfiguredModularCartItemListPlaceholders.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedTConstructBattleSignInternalBlockItemListEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedTConstructHeldItemInternalBlockItemListEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedThaumcraftBlockHoleInternalBlockItemListEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedThaumcraftEldritchPortalInternalBlockItemListEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedThaumicHorizonsBaseLightInternalBlockItemListEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedThaumicHorizonsSolarLightInternalBlockItemListEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedTwilightForestExperiment115InternalBlockItemListEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedWitchingGadgetsCustomAirInternalBlockItemListEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedBotaniaBifrostItemListWorldStateEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedBotaniaBuriedPetalsItemListWorldStateVariants.*16/,
  );
  assert.match(
    issues.join('\n'),
    /excludedBotaniaBuriedPetalsItemListWorldStateMetadataMask.*65535/,
  );
  assert.match(
    issues.join('\n'),
    /excludedBotaniaCacophoniumBlockItemListWorldStateEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedBotaniaEnchanterItemListWorldStateEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedBotaniaFakeAirItemListWorldStateEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedBotaniaManaFlameItemListWorldStateEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedBotaniaSolidVineItemListWorldStateEntries.*1/,
  );
  assert.match(
    issues.join('\n'),
    /excludedBotaniaStructureLibAnyFlowerItemListPresentationPlaceholders.*1/,
  );
  assert.match(issues.join('\n'), /adaptedBotaniaCocoonItemIcons.*1/);
  assert.match(
    issues.join('\n'),
    /adaptedBotaniaCocoonRecipeWidgetRenderInvocations.*positive/,
  );
  assert.match(issues.join('\n'), /adaptedBotaniaPrismItemIcons.*1/);
  assert.match(
    issues.join('\n'),
    /adaptedBotaniaPrismRecipeWidgetRenderInvocations.*positive/,
  );
  assert.match(issues.join('\n'), /adaptedGalacticraftFlagItemIcons.*1/);
  assert.match(
    issues.join('\n'),
    /adaptedGalacticraftFlagRecipeWidgetRenderInvocations.*positive/,
  );
  assert.match(issues.join('\n'), /adaptedWrcbeTriangulatorItemIcons.*1/);
  assert.match(
    issues.join('\n'),
    /adaptedWrcbeTriangulatorRecipeWidgetRenderInvocations.*positive/,
  );
  assert.match(issues.join('\n'), /adaptedModernMarkingsCrossingItemIcons.*6/);
  assert.match(issues.join('\n'), /adaptedThaumcraftRunedStoneItemIcons.*1/);
  assert.match(issues.join('\n'), /adaptedForestryScannedSaplingDisplayNames.*1/);
  assert.match(
    issues.join('\n'),
    /gregTechForestryScannedSaplingRecipeOccurrences.*1/,
  );
  assert.match(issues.join('\n'), /adaptedForestryScannedPollenDisplayNames.*1/);
  assert.match(
    issues.join('\n'),
    /gregTechForestryScannedPollenRecipeOccurrences.*1/,
  );
  assert.match(issues.join('\n'), /adaptedProjectBlueControlPanelItemIcons.*3/);
  assert.match(
    issues.join('\n'),
    /adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations.*3/,
  );
  assert.match(issues.join('\n'), /normalizedTcnaAspectCostInputOccurrences.*positive/);
  assert.match(issues.join('\n'), /normalizedTcnaAspectCostDistinctKeys.*positive/);
  assert.match(issues.join('\n'), /normalizedTcnaAspectCostHandlerCategories.*4/);
  assert.match(issues.join('\n'), /gregTechFuelSinkRecipes.*289/);
  assert.match(issues.join('\n'), /gregTechFuelSinkCategories.*14/);
  assert.match(issues.join('\n'), /gregTechLargeBoilerFuelSinkRecipes.*49/);
  assert.match(issues.join('\n'), /gregTechLargeBoilerFuelSinkCategories.*1/);
  assert.match(issues.join('\n'), /gregTechRadioHatchInformationRecipes.*104/);
  assert.match(issues.join('\n'), /gregTechQuantumComponentInformationRecipes.*27/);
  assert.match(issues.join('\n'), /gregTechSpaceProjectInformationRecipes.*2/);
  assert.match(issues.join('\n'), /gregTechOutputlessSemanticCategories.*18/);
  assert.match(issues.join('\n'), /gregTechOutputlessSemanticRecipes.*471/);
  assert.match(issues.join('\n'), /gregTechOutputlessSemanticRecipes.*five GregTech outputless/);
  assert.match(issues.join('\n'), /excludedGregTechLargeBoilerPresentationRows.*1/);
  assert.match(
    issues.join('\n'),
    /excludedGregTechUnregisteredDoorRecyclingRows.*5.*received 4/,
  );
  assert.match(issues.join('\n'), /informationalEmptyOutputRecipes.*513/);
  assert.match(issues.join('\n'), /knowledgeIndependentAspectNames.*69/);
});

test('GTNH fails closed when HeldItem internal-block exclusion telemetry is absent or drifted', () => {
  const missing = validGtnhManifest();
  delete missing.diagnostics.nei.excludedTConstructHeldItemInternalBlockItemListEntries;
  const missingIssues = exportQualityIssues(
    {manifest: missing, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(missingIssues.join('\n'), /diagnostics\.nei must contain exactly/);
  assert.match(
    missingIssues.join('\n'),
    /excludedTConstructHeldItemInternalBlockItemListEntries.*non-negative safe integer/,
  );

  const drifted = validGtnhManifest();
  drifted.diagnostics.nei.excludedTConstructHeldItemInternalBlockItemListEntries = 2;
  assert.match(
    exportQualityIssues(
      {manifest: drifted, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedTConstructHeldItemInternalBlockItemListEntries.*1.*received 2/,
  );
});

test('GTNH fails closed when exact ProjectBlue control-panel lease telemetry is absent or drifted', () => {
  for (const field of [
    'adaptedProjectBlueControlPanelItemIcons',
    'adaptedProjectBlueControlPanelRecipeWidgetRenderInvocations',
  ]) {
    const missing = validGtnhManifest();
    delete missing.diagnostics.nei[field];
    const missingIssues = exportQualityIssues(
      {manifest: missing, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    );
    assert.match(missingIssues.join('\n'), /diagnostics\.nei must contain exactly/);
    assert.match(
      missingIssues.join('\n'),
      new RegExp(`${field}.*non-negative safe integer`),
    );

    const drifted = validGtnhManifest();
    drifted.diagnostics.nei[field] = 4;
    assert.match(
      exportQualityIssues(
        {manifest: drifted, failures: [], semanticErrorRecipes: 0},
        GTNH_1710_PROFILE,
      ).join('\n'),
      new RegExp(`${field}.*3.*received 4`),
    );
  }
});

test('GTNH reconciles ItemList cardinality independently of the final union item catalog', () => {
  for (const field of [
    'itemListRawEntries',
    'itemListExcludedEntries',
    'itemListRetainedEntries',
    'itemListRetainedUniqueIdentities',
  ]) {
    const missing = validGtnhManifest();
    delete missing.diagnostics.nei[field];
    const issues = exportQualityIssues(
      {manifest: missing, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n');
    assert.match(issues, /diagnostics\.nei must contain exactly/);
    assert.match(issues, new RegExp(`${field}.*non-negative safe integer`));
  }

  const rawPartitionDrift = validGtnhManifest();
  rawPartitionDrift.diagnostics.nei.itemListRawEntries = 50;
  assert.match(
    exportQualityIssues(
      {manifest: rawPartitionDrift, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /itemListRawEntries \(50\).*itemListExcludedEntries \(46\).*itemListRetainedEntries \(3\)/,
  );

  const exclusionTotalDrift = validGtnhManifest();
  exclusionTotalDrift.diagnostics.nei.itemListRawEntries = 48;
  exclusionTotalDrift.diagnostics.nei.itemListExcludedEntries = 45;
  assert.match(
    exportQualityIssues(
      {manifest: exclusionTotalDrift, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /itemListExcludedEntries \(45\).*summed ItemList exclusion telemetry \(46\)/,
  );

  const retainedIdentityDrift = validGtnhManifest();
  retainedIdentityDrift.diagnostics.nei.itemListRawEntries = 50;
  retainedIdentityDrift.diagnostics.nei.itemListRetainedEntries = 4;
  assert.match(
    exportQualityIssues(
      {manifest: retainedIdentityDrift, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /itemListRetainedEntries \(4\).*itemListRetainedUniqueIdentities \(2\).*exactly 1/,
  );

  const emptyRetainedIdentitySet = validGtnhManifest();
  emptyRetainedIdentitySet.diagnostics.nei.itemListRawEntries = 47;
  emptyRetainedIdentitySet.diagnostics.nei.itemListRetainedEntries = 1;
  emptyRetainedIdentitySet.diagnostics.nei.itemListRetainedUniqueIdentities = 0;
  assert.match(
    exportQualityIssues(
      {manifest: emptyRetainedIdentitySet, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /itemListRetainedUniqueIdentities to be positive/,
  );

  const undersizedUnionCatalog = validGtnhManifest();
  undersizedUnionCatalog.counts.items = 1;
  undersizedUnionCatalog.diagnostics.nei.itemIconsRendered = 1;
  assert.match(
    exportQualityIssues(
      {manifest: undersizedUnionCatalog, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /manifest\.counts\.items \(1\).*itemListRetainedUniqueIdentities \(2\)/,
  );
});

test('GTNH requires all 102 NEI diagnostics and fails closed on catalog-exclusion telemetry drift', () => {
  assert.equal(GTNH_NEI_DIAGNOSTIC_KEYS.length, 102);

  const missingDoorRecyclingExclusion = validGtnhManifest();
  delete missingDoorRecyclingExclusion.diagnostics.nei
    .excludedGregTechUnregisteredDoorRecyclingRows;
  const missingDoorRecyclingExclusionIssues = exportQualityIssues(
    {manifest: missingDoorRecyclingExclusion, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(
    missingDoorRecyclingExclusionIssues.join('\n'),
    /diagnostics\.nei must contain exactly/,
  );
  assert.match(
    missingDoorRecyclingExclusionIssues.join('\n'),
    /excludedGregTechUnregisteredDoorRecyclingRows.*non-negative safe integer/,
  );

  const driftedDoorRecyclingExclusion = validGtnhManifest();
  driftedDoorRecyclingExclusion.diagnostics.nei
    .excludedGregTechUnregisteredDoorRecyclingRows = 6;
  assert.match(
    exportQualityIssues(
      {manifest: driftedDoorRecyclingExclusion, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedGregTechUnregisteredDoorRecyclingRows.*5.*received 6/,
  );

  const missingInternalFacadeExclusion = validGtnhManifest();
  delete missingInternalFacadeExclusion.diagnostics.nei
    .excludedAe2EnderIoInternalConduitFacadeRows;
  assert.match(
    exportQualityIssues(
      {manifest: missingInternalFacadeExclusion, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedAe2EnderIoInternalConduitFacadeRows.*non-negative safe integer/,
  );

  const driftedInternalFacadeExclusion = validGtnhManifest();
  driftedInternalFacadeExclusion.diagnostics.nei
    .excludedAe2EnderIoInternalConduitFacadeRows = 0;
  assert.match(
    exportQualityIssues(
      {manifest: driftedInternalFacadeExclusion, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedAe2EnderIoInternalConduitFacadeRows.*1.*received 0/,
  );

  const missingEmptyHandler = validGtnhManifest();
  delete missingEmptyHandler.diagnostics.nei.excludedEmptyRecipeHandlers;
  const missingEmptyHandlerIssues = exportQualityIssues(
    {manifest: missingEmptyHandler, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(missingEmptyHandlerIssues.join('\n'), /diagnostics\.nei must contain exactly/);
  assert.match(
    missingEmptyHandlerIssues.join('\n'),
    /excludedEmptyRecipeHandlers.*non-negative safe integer/,
  );

  const driftedEmptyHandler = validGtnhManifest();
  driftedEmptyHandler.diagnostics.nei.excludedEmptyRecipeHandlers = 1;
  assert.match(
    exportQualityIssues(
      {manifest: driftedEmptyHandler, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedEmptyRecipeHandlers.*22.*received 1/,
  );

  const missingUnboundTemplateHandler = validGtnhManifest();
  delete missingUnboundTemplateHandler.diagnostics.nei
    .excludedUnboundTemplateRecipeHandlers;
  const missingUnboundTemplateHandlerIssues = exportQualityIssues(
    {manifest: missingUnboundTemplateHandler, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(
    missingUnboundTemplateHandlerIssues.join('\n'),
    /diagnostics\.nei must contain exactly/,
  );
  assert.match(
    missingUnboundTemplateHandlerIssues.join('\n'),
    /excludedUnboundTemplateRecipeHandlers.*non-negative safe integer/,
  );

  const driftedUnboundTemplateHandler = validGtnhManifest();
  driftedUnboundTemplateHandler.diagnostics.nei.excludedUnboundTemplateRecipeHandlers = 0;
  assert.match(
    exportQualityIssues(
      {manifest: driftedUnboundTemplateHandler, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedUnboundTemplateRecipeHandlers.*1.*received 0/,
  );

  const omittedOneEmptyHandlerFromEquation = validGtnhManifest();
  omittedOneEmptyHandlerFromEquation.diagnostics.nei.registeredCraftingHandlers = 43;
  assert.match(
    exportQualityIssues(
      {manifest: omittedOneEmptyHandlerFromEquation, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /registeredCraftingHandlers.*excludedNonRecipeHandlers \(20\).*excludedEmptyRecipeHandlers \(22\).*excludedUnboundTemplateRecipeHandlers \(1\)/,
  );

  const productionPartition = validGtnhManifest();
  productionPartition.counts.categories = 287;
  productionPartition.diagnostics.nei.registeredCraftingHandlers = 330;
  productionPartition.diagnostics.nei.exportableCraftingHandlers = 287;
  productionPartition.diagnostics.nei.loadedCategories = 287;
  assert.deepEqual(
    exportQualityIssues(
      {manifest: productionPartition, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ),
    [],
  );

  const missing = validGtnhManifest();
  delete missing.diagnostics.nei.excludedThaumcraftBlockHoleInternalBlockItemListEntries;
  const missingIssues = exportQualityIssues(
    {manifest: missing, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(missingIssues.join('\n'), /diagnostics\.nei must contain exactly/);
  assert.match(
    missingIssues.join('\n'),
    /excludedThaumcraftBlockHoleInternalBlockItemListEntries.*non-negative safe integer/,
  );

  const drifted = validGtnhManifest();
  drifted.diagnostics.nei.excludedThaumcraftBlockHoleInternalBlockItemListEntries = 2;
  assert.match(
    exportQualityIssues(
      {manifest: drifted, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedThaumcraftBlockHoleInternalBlockItemListEntries.*1.*received 2/,
  );

  const missingPortal = validGtnhManifest();
  delete missingPortal.diagnostics.nei
    .excludedThaumcraftEldritchPortalInternalBlockItemListEntries;
  const missingPortalIssues = exportQualityIssues(
    {manifest: missingPortal, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(missingPortalIssues.join('\n'), /diagnostics\.nei must contain exactly/);
  assert.match(
    missingPortalIssues.join('\n'),
    /excludedThaumcraftEldritchPortalInternalBlockItemListEntries.*non-negative safe integer/,
  );

  const driftedPortal = validGtnhManifest();
  driftedPortal.diagnostics.nei
    .excludedThaumcraftEldritchPortalInternalBlockItemListEntries = 2;
  assert.match(
    exportQualityIssues(
      {manifest: driftedPortal, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedThaumcraftEldritchPortalInternalBlockItemListEntries.*1.*received 2/,
  );

  const missingBaseLight = validGtnhManifest();
  delete missingBaseLight.diagnostics.nei
    .excludedThaumicHorizonsBaseLightInternalBlockItemListEntries;
  const missingBaseLightIssues = exportQualityIssues(
    {manifest: missingBaseLight, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(missingBaseLightIssues.join('\n'), /diagnostics\.nei must contain exactly/);
  assert.match(
    missingBaseLightIssues.join('\n'),
    /excludedThaumicHorizonsBaseLightInternalBlockItemListEntries.*non-negative safe integer/,
  );

  const driftedBaseLight = validGtnhManifest();
  driftedBaseLight.diagnostics.nei
    .excludedThaumicHorizonsBaseLightInternalBlockItemListEntries = 0;
  assert.match(
    exportQualityIssues(
      {manifest: driftedBaseLight, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedThaumicHorizonsBaseLightInternalBlockItemListEntries.*1.*received 0/,
  );

  const driftedSolarLight = validGtnhManifest();
  driftedSolarLight.diagnostics.nei
    .excludedThaumicHorizonsSolarLightInternalBlockItemListEntries = 0;
  assert.match(
    exportQualityIssues(
      {manifest: driftedSolarLight, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedThaumicHorizonsSolarLightInternalBlockItemListEntries.*1.*received 0/,
  );

  const missingExperiment115 = validGtnhManifest();
  delete missingExperiment115.diagnostics.nei
    .excludedTwilightForestExperiment115InternalBlockItemListEntries;
  const missingExperiment115Issues = exportQualityIssues(
    {manifest: missingExperiment115, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(missingExperiment115Issues.join('\n'), /diagnostics\.nei must contain exactly/);
  assert.match(
    missingExperiment115Issues.join('\n'),
    /excludedTwilightForestExperiment115InternalBlockItemListEntries.*non-negative safe integer/,
  );

  const driftedExperiment115 = validGtnhManifest();
  driftedExperiment115.diagnostics.nei
    .excludedTwilightForestExperiment115InternalBlockItemListEntries = 0;
  assert.match(
    exportQualityIssues(
      {manifest: driftedExperiment115, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedTwilightForestExperiment115InternalBlockItemListEntries.*1.*received 0/,
  );

  const missingCustomAir = validGtnhManifest();
  delete missingCustomAir.diagnostics.nei
    .excludedWitchingGadgetsCustomAirInternalBlockItemListEntries;
  const missingCustomAirIssues = exportQualityIssues(
    {manifest: missingCustomAir, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(missingCustomAirIssues.join('\n'), /diagnostics\.nei must contain exactly/);
  assert.match(
    missingCustomAirIssues.join('\n'),
    /excludedWitchingGadgetsCustomAirInternalBlockItemListEntries.*non-negative safe integer/,
  );

  const driftedCustomAir = validGtnhManifest();
  driftedCustomAir.diagnostics.nei
    .excludedWitchingGadgetsCustomAirInternalBlockItemListEntries = 0;
  assert.match(
    exportQualityIssues(
      {manifest: driftedCustomAir, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedWitchingGadgetsCustomAirInternalBlockItemListEntries.*1.*received 0/,
  );

  const missingAe2CableBus = validGtnhManifest();
  delete missingAe2CableBus.diagnostics.nei.excludedAe2CableBusInternalBlockItemListEntries;
  const missingAe2CableBusIssues = exportQualityIssues(
    {manifest: missingAe2CableBus, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(missingAe2CableBusIssues.join('\n'), /diagnostics\.nei must contain exactly/);
  assert.match(
    missingAe2CableBusIssues.join('\n'),
    /excludedAe2CableBusInternalBlockItemListEntries.*non-negative safe integer/,
  );

  const driftedAe2CableBus = validGtnhManifest();
  driftedAe2CableBus.diagnostics.nei.excludedAe2CableBusInternalBlockItemListEntries = 0;
  assert.match(
    exportQualityIssues(
      {manifest: driftedAe2CableBus, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedAe2CableBusInternalBlockItemListEntries.*1.*received 0/,
  );

  const missingAe2MatrixFrame = validGtnhManifest();
  delete missingAe2MatrixFrame.diagnostics.nei
    .excludedAe2MatrixFrameInternalBlockItemListEntries;
  const missingAe2MatrixFrameIssues = exportQualityIssues(
    {manifest: missingAe2MatrixFrame, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(missingAe2MatrixFrameIssues.join('\n'), /diagnostics\.nei must contain exactly/);
  assert.match(
    missingAe2MatrixFrameIssues.join('\n'),
    /excludedAe2MatrixFrameInternalBlockItemListEntries.*non-negative safe integer/,
  );

  const driftedAe2MatrixFrame = validGtnhManifest();
  driftedAe2MatrixFrame.diagnostics.nei
    .excludedAe2MatrixFrameInternalBlockItemListEntries = 0;
  assert.match(
    exportQualityIssues(
      {manifest: driftedAe2MatrixFrame, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedAe2MatrixFrameInternalBlockItemListEntries.*1.*received 0/,
  );

  const missingDreamcraftNothing = validGtnhManifest();
  delete missingDreamcraftNothing.diagnostics.nei
    .excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders;
  const missingDreamcraftNothingIssues = exportQualityIssues(
    {manifest: missingDreamcraftNothing, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(missingDreamcraftNothingIssues.join('\n'), /diagnostics\.nei must contain exactly/);
  assert.match(
    missingDreamcraftNothingIssues.join('\n'),
    /excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders.*non-negative safe integer/,
  );

  const driftedDreamcraftNothing = validGtnhManifest();
  driftedDreamcraftNothing.diagnostics.nei
    .excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders = 0;
  assert.match(
    exportQualityIssues(
      {manifest: driftedDreamcraftNothing, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedDreamcraftNothingLegacyLootBagSentinelItemListPlaceholders.*1.*received 0/,
  );

  const missingLittleTilesCarrier = validGtnhManifest();
  delete missingLittleTilesCarrier.diagnostics.nei
    .excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries;
  const missingLittleTilesCarrierIssues = exportQualityIssues(
    {manifest: missingLittleTilesCarrier, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(missingLittleTilesCarrierIssues.join('\n'), /diagnostics\.nei must contain exactly/);
  assert.match(
    missingLittleTilesCarrierIssues.join('\n'),
    /excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries.*non-negative safe integer/,
  );

  const driftedLittleTilesCarrier = validGtnhManifest();
  driftedLittleTilesCarrier.diagnostics.nei
    .excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries = 0;
  assert.match(
    exportQualityIssues(
      {manifest: driftedLittleTilesCarrier, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedLittleTilesUnparameterizedMicrotileCarrierItemListEntries.*1.*received 0/,
  );

  const missingMalisisDoorsCustomDoor = validGtnhManifest();
  delete missingMalisisDoorsCustomDoor.diagnostics.nei
    .excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders;
  const missingMalisisDoorsCustomDoorIssues = exportQualityIssues(
    {manifest: missingMalisisDoorsCustomDoor, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(
    missingMalisisDoorsCustomDoorIssues.join('\n'),
    /diagnostics\.nei must contain exactly/,
  );
  assert.match(
    missingMalisisDoorsCustomDoorIssues.join('\n'),
    /excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders.*non-negative safe integer/,
  );

  const driftedMalisisDoorsCustomDoor = validGtnhManifest();
  driftedMalisisDoorsCustomDoor.diagnostics.nei
    .excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders = 0;
  assert.match(
    exportQualityIssues(
      {manifest: driftedMalisisDoorsCustomDoor, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedMalisisDoorsUnconfiguredCustomDoorItemListPlaceholders.*1.*received 0/,
  );

  const referencedMalisisDoorsCustomDoor = validGtnhManifest();
  referencedMalisisDoorsCustomDoor.diagnostics.nei
    .malisisDoorsUnconfiguredCustomDoorRecipeReferences = 1;
  assert.match(
    exportQualityIssues(
      {manifest: referencedMalisisDoorsCustomDoor, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /malisisDoorsUnconfiguredCustomDoorRecipeReferences.*0.*received 1/,
  );

  const questReferencedMalisisDoorsCustomDoor = validGtnhManifest();
  questReferencedMalisisDoorsCustomDoor.diagnostics.nei
    .malisisDoorsUnconfiguredCustomDoorQuestReferences = 1;
  assert.match(
    exportQualityIssues(
      {manifest: questReferencedMalisisDoorsCustomDoor, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /malisisDoorsUnconfiguredCustomDoorQuestReferences.*0.*received 1/,
  );

  const missingMalisisDoorsMixedBlock = validGtnhManifest();
  delete missingMalisisDoorsMixedBlock.diagnostics.nei
    .excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders;
  const missingMalisisDoorsMixedBlockIssues = exportQualityIssues(
    {manifest: missingMalisisDoorsMixedBlock, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(
    missingMalisisDoorsMixedBlockIssues.join('\n'),
    /diagnostics\.nei must contain exactly/,
  );
  assert.match(
    missingMalisisDoorsMixedBlockIssues.join('\n'),
    /excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders.*non-negative safe integer/,
  );

  const driftedMalisisDoorsMixedBlock = validGtnhManifest();
  driftedMalisisDoorsMixedBlock.diagnostics.nei
    .excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders = 0;
  assert.match(
    exportQualityIssues(
      {manifest: driftedMalisisDoorsMixedBlock, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /excludedMalisisDoorsUnconfiguredMixedBlockItemListPlaceholders.*1.*received 0/,
  );

  const referencedMalisisDoorsMixedBlock = validGtnhManifest();
  referencedMalisisDoorsMixedBlock.diagnostics.nei
    .malisisDoorsUnconfiguredMixedBlockRecipeReferences = 1;
  assert.match(
    exportQualityIssues(
      {manifest: referencedMalisisDoorsMixedBlock, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /malisisDoorsUnconfiguredMixedBlockRecipeReferences.*0.*received 1/,
  );

  const questReferencedMalisisDoorsMixedBlock = validGtnhManifest();
  questReferencedMalisisDoorsMixedBlock.diagnostics.nei
    .malisisDoorsUnconfiguredMixedBlockQuestReferences = 1;
  assert.match(
    exportQualityIssues(
      {manifest: questReferencedMalisisDoorsMixedBlock, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /malisisDoorsUnconfiguredMixedBlockQuestReferences.*0.*received 1/,
  );
});

test('GTNH requires the exact normalized-data attribution contract', () => {
  assert.deepEqual(GTNH_DATA_ATTRIBUTION, {
    sourceUrl:
      'https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/tree/2.8.4',
    projectUrl: 'https://www.gtnewhorizons.com/',
    licenseIdentifier: 'CC BY-NC-SA 4.0',
    licenseUrl: 'https://creativecommons.org/licenses/by-nc-sa/4.0/',
  });

  const missing = validGtnhManifest();
  delete missing.attribution;
  assert.match(
    exportQualityIssues(
      {manifest: missing, failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ).join('\n'),
    /manifest\.attribution must contain exactly sourceUrl, projectUrl, licenseIdentifier, licenseUrl/,
  );

  const drifted = validGtnhManifest();
  drifted.attribution.licenseIdentifier = 'CC BY 4.0';
  drifted.attribution.allModArtworkCovered = true;
  const issues = exportQualityIssues(
    {manifest: drifted, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(issues.join('\n'), /manifest\.attribution must contain exactly/);
});

test('GTNH rejects unloaded, unknown, inconsistent, or drifted NEI diagnostics', () => {
  const manifest = validGtnhManifest();
  manifest.diagnostics.nei.unloadedHandlerCategories = 1;
  manifest.diagnostics.nei.recipeWidgetsRendered = 2;
  manifest.diagnostics.nei.unexpectedHandlerCategories = 1;
  manifest.pack.version = '2.8.3';
  const issues = exportQualityIssues(
    {manifest, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(issues.join('\n'), /diagnostics\.nei must contain exactly/);
  assert.match(issues.join('\n'), /unloadedHandlerCategories.*0/);
  assert.match(issues.join('\n'), /recipeWidgetsRendered.*counts\.recipes/);
  assert.match(issues.join('\n'), /manifest\.pack\.version "2\.8\.4"/);
});

test('GTNH rejects every serialized failure and classifies exporter failure prefixes', () => {
  const manifest = validGtnhManifest();
  const failures = [
    'HANDLER_UNLOADED: nei.handler',
    'RECIPE_SEMANTICS: nei.handler #4',
    'QUANTITY_INVALID: nei.handler #4 input 0',
    'RECIPE_WIDGET_RENDER: nei.handler #4',
    'UNRECOGNIZED_FUTURE_FAILURE: must remain fail-closed',
  ];
  manifest.counts.failures = failures.length;
  manifest.diagnostics.failureEvents = failures.length;
  const issues = exportQualityIssues(
    {manifest, failures, semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(issues.join('\n'), /counts\.failures to be 0/);
  assert.match(issues.join('\n'), /failureEvents to be 0/);
  assert.match(issues.join('\n'), /failures\.json to be empty/);
  assert.match(issues.join('\n'), /category failure/);
  assert.match(issues.join('\n'), /ingredient-semantics/);
  assert.match(issues.join('\n'), /ingredient-quantity/);
  assert.match(issues.join('\n'), /recipe-preview render\/write/);
});

test('accepts dynamic complete Multiblock Madness profiles only at 48px icons and 2x layouts', () => {
  for (const [profile, minecraft, pack, diagnostics] of [
    [
      MULTIBLOCK_MADNESS_112_PROFILE,
      '1.12.2',
      {name: 'Multiblock Madness', version: '3.2.3', identitySource: 'explicit-request'},
      {
        failureEvents: 0,
        failureEventsOmitted: 0,
        warningEvents: 0,
        warningEventsOmitted: 0,
      },
    ],
    [
      MULTIBLOCK_MADNESS_2_118_PROFILE,
      '1.18.2',
      {name: 'Multiblock Madness 2', version: '1.0.0', identitySource: 'explicit-request'},
      {
        failureEvents: 0,
        failureEventsOmitted: 0,
        nativeIconCorrections: 2,
        transparentIcons: 0,
      },
    ],
  ]) {
    assert.deepEqual(
      exportQualityIssues(
        {
          manifest: {
            ...validManifest,
            minecraft,
            pack,
            settings: {iconScale: 3, recipeScale: 2},
            counts: {failures: 0},
            diagnostics,
          },
          failures: [],
          ...(profile === MULTIBLOCK_MADNESS_112_PROFILE
            ? {warnings: []}
            : {
                warnings: validMm2Warnings(2),
                iconlessItemIds: [...MULTIBLOCK_MADNESS_2_118_ICON_OMISSION_IDS],
              }),
          semanticErrorRecipes: 0,
        },
        profile,
      ),
      [],
    );
  }
});

test('Multiblock Madness 2 accepts only its 16 exact icon omissions and correction warnings', () => {
  assert.deepEqual(MULTIBLOCK_MADNESS_2_118_WARNING_PREFIXES, [
    'UPSTREAM_NATIVE_ICON_UNAVAILABLE ',
    'Corrected transparent or quantity-clipped native REI catalog icon:',
    'Canonicalized animated native REI catalog icon to its first physical source keyframe:',
    'CATEGORICAL_UNIT_CARDINALITY ',
  ]);
  assert.deepEqual(MULTIBLOCK_MADNESS_2_118_ICON_OMISSION_IDS, [
    'ae2:cable_bus',
    'ae2:matrix_frame',
    'ae2:paint',
    'ars_nouveau:debug',
    'ars_nouveau:light_block',
    'ars_nouveau:portal',
    'integrateddynamics:block_liquid_chorus',
    'integrateddynamics:block_menril_resin',
    'integrateddynamics:invisible_light',
    'mcjtylib:multipart',
    'mekanism:bounding_block',
    'mininggadgets:minerslight',
    'multiblocked:dummy_component',
    'multiblocked:symbol',
    'reliquary:cure',
    'reliquary:pacification',
  ]);
  const manifest = {
    ...validManifest,
    minecraft: '1.18.2',
    pack: {
      name: 'Multiblock Madness 2',
      version: '1.0.0',
      identitySource: 'explicit-request',
    },
    settings: {iconScale: 3, recipeScale: 2},
    counts: {failures: 0},
    diagnostics: {
      failureEvents: 0,
      failureEventsOmitted: 0,
      nativeIconCorrections: 2,
      transparentIcons: 0,
    },
  };
  assert.deepEqual(
    exportQualityIssues(
      {
        manifest,
        failures: [],
        warnings: validMm2Warnings(2),
        iconlessItemIds: [...MULTIBLOCK_MADNESS_2_118_ICON_OMISSION_IDS],
      },
      MULTIBLOCK_MADNESS_2_118_PROFILE,
    ),
    [],
  );

  const issues = exportQualityIssues(
    {
      manifest,
      failures: [],
      warnings: [
        ...validMm2Warnings(1).slice(1),
        'Canonicalized animated native REI catalog icon to its first declared frame: ' +
          'entry=fixture:near_miss',
      ],
      iconlessItemIds: [
        ...MULTIBLOCK_MADNESS_2_118_ICON_OMISSION_IDS,
        'fixture:unexpected_iconless_item',
      ],
    },
    MULTIBLOCK_MADNESS_2_118_PROFILE,
  );
  assert.match(issues.join('\n'), /unrecognized warning class/);
  assert.match(issues.join('\n'), /nativeIconCorrections \(2\).*correction warnings \(1\)/);
  assert.match(issues.join('\n'), /omission warnings must contain the exact 16 audited/);
  assert.match(issues.join('\n'), /iconless items must be exactly/);
});

test('Multiblock Madness 2 rejects class drift for an otherwise audited transparent id', () => {
  const manifest = {
    ...validManifest,
    minecraft: '1.18.2',
    pack: {
      name: 'Multiblock Madness 2',
      version: '1.0.0',
      identitySource: 'explicit-request',
    },
    settings: {iconScale: 3, recipeScale: 2},
    counts: {failures: 0},
    diagnostics: {
      failureEvents: 0,
      failureEventsOmitted: 0,
      nativeIconCorrections: 2,
      transparentIcons: 0,
    },
  };
  const warnings = validMm2Warnings(2);
  warnings[0] = warnings[0].replace(
    'itemClass=appeng.block.AEBaseBlockItem',
    'itemClass=appeng.block.UnreviewedReplacement',
  );
  const issues = exportQualityIssues(
    {
      manifest,
      failures: [],
      warnings,
      iconlessItemIds: [...MULTIBLOCK_MADNESS_2_118_ICON_OMISSION_IDS],
    },
    MULTIBLOCK_MADNESS_2_118_PROFILE,
  );
  assert.match(issues.join('\n'), /exact 16 audited type\/id\/value\/item\/block identities/);
});

test('Multiblock Madness 2 categorical warnings are exact, unique, and complete outside samples', () => {
  assert.equal(
    MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNING_PREFIX,
    'CATEGORICAL_UNIT_CARDINALITY ',
  );
  assert.equal(MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNINGS.length, 5);
  const manifest = {
    ...validManifest,
    minecraft: '1.18.2',
    pack: {
      name: 'Multiblock Madness 2',
      version: '1.0.0',
      identitySource: 'explicit-request',
    },
    settings: {iconScale: 3, recipeScale: 2},
    counts: {failures: 0},
    diagnostics: {
      failureEvents: 0,
      failureEventsOmitted: 0,
      nativeIconCorrections: 2,
      transparentIcons: 0,
    },
  };
  const input = warnings => ({
    manifest,
    failures: [],
    warnings,
    iconlessItemIds: [...MULTIBLOCK_MADNESS_2_118_ICON_OMISSION_IDS],
  });

  const missing = exportQualityIssues(
    input(validMm2Warnings(2, MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNINGS.slice(1))),
    MULTIBLOCK_MADNESS_2_118_PROFILE,
  );
  assert.match(missing.join('\n'), /full export must contain all five exact categorical/);

  const duplicateWarning = MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNINGS[0];
  const duplicate = exportQualityIssues(
    input(validMm2Warnings(2, [...MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNINGS, duplicateWarning])),
    MULTIBLOCK_MADNESS_2_118_PROFILE,
  );
  assert.match(duplicate.join('\n'), /duplicate warning detected/);

  const crossed = MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNINGS[0].replace(
    /valueClass=[^ ]+/,
    'valueClass=java.lang.Object',
  );
  const unknown = exportQualityIssues(
    input(
      validMm2Warnings(2, [
        crossed,
        ...MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNINGS.slice(1),
      ]),
    ),
    MULTIBLOCK_MADNESS_2_118_PROFILE,
  );
  assert.match(unknown.join('\n'), /exactly match one of the five audited/);

  const sample = exportQualityIssues(
    {
      ...input(validMm2Warnings(2, [MULTIBLOCK_MADNESS_2_118_CATEGORICAL_WARNINGS[2]])),
      manifest: {...manifest, qualitySample: {}},
    },
    MULTIBLOCK_MADNESS_2_118_PROFILE,
  );
  assert.deepEqual(sample, []);
});

test('Multiblock Madness 1.12 audits only the pinned complete warning classes', () => {
  assert.deepEqual(MULTIBLOCK_MADNESS_112_WARNING_PREFIXES, [
    'ZERO_PREREQUISITE',
    'ZERO_ABSENT_OUTPUT',
    'ZERO_ABSENT_ALTERNATIVE',
    'UPSTREAM_BLANK_DISPLAY_NAME',
    'NATIVE_ICON_OVERSCAN_RECOVERY_APPLIED',
    'UPSTREAM_NATIVE_ICON_UNAVAILABLE',
  ]);
  const warnings = MULTIBLOCK_MADNESS_112_WARNING_PREFIXES.map(
    prefix => `${prefix} audited fixture context`,
  );
  const manifest = {
    ...validManifest,
    pack: {
      name: 'Multiblock Madness',
      version: '3.2.3',
      identitySource: 'explicit-request',
    },
    settings: {iconScale: 3, recipeScale: 2},
    counts: {failures: 0},
    diagnostics: {
      failureEvents: 0,
      failureEventsOmitted: 0,
      warningEvents: warnings.length,
      warningEventsOmitted: 0,
    },
  };
  assert.deepEqual(
    exportQualityIssues(
      {manifest, failures: [], warnings, semanticErrorRecipes: 0},
      MULTIBLOCK_MADNESS_112_PROFILE,
    ),
    [],
  );

  const issues = exportQualityIssues(
    {
      manifest: {
        ...manifest,
        diagnostics: {...manifest.diagnostics, warningEvents: 3, warningEventsOmitted: 1},
      },
      failures: [],
      warnings: ['', 'ZERO_PREREQUISITEX future drift'],
      semanticErrorRecipes: 0,
    },
    MULTIBLOCK_MADNESS_112_PROFILE,
  );
  assert.match(issues.join('\n'), /omitted 1 warning event/);
  assert.match(issues.join('\n'), /warnings\.json\[0\].*non-empty string/);
  assert.match(issues.join('\n'), /warningEvents \(3\).*warnings\.json length \(2\)/);
  assert.match(issues.join('\n'), /unrecognized warning class/);

  assert.match(
    exportQualityIssues(
      {manifest, failures: [], semanticErrorRecipes: 0},
      MULTIBLOCK_MADNESS_112_PROFILE,
    ).join('\n'),
    /requires warnings\.json to contain an array/,
  );
});

test('rejects Multiblock Madness version, scale, semantic, and unclassified-zero drift', () => {
  const issues = exportQualityIssues(
    {
      manifest: {
        ...validManifest,
        minecraft: '1.12.2',
        pack: {
          name: 'Multiblock Madness 2',
          version: '0.9.0',
          identitySource: 'explicit-request',
        },
        settings: {iconScale: 1, recipeScale: 1},
        counts: {failures: 1},
        diagnostics: {
          failureEvents: 1,
          failureEventsOmitted: 0,
          nativeIconCorrections: 0,
          transparentIcons: 1,
          unrecognizedFutureDiagnostic: 1,
        },
      },
      failures: [
        'recipe output ingredient rei.machine #2: ZERO_UNCLASSIFIED no exact semantic adapter exists',
      ],
      semanticErrorRecipes: 1,
    },
    MULTIBLOCK_MADNESS_2_118_PROFILE,
  );
  assert.match(issues.join('\n'), /manifest\.minecraft "1\.18\.2"/);
  assert.match(issues.join('\n'), /48×48 item canvases/);
  assert.match(issues.join('\n'), /REI layouts rendered at 2×/);
  assert.match(issues.join('\n'), /manifest\.pack\.version "1\.0\.0"/);
  assert.match(issues.join('\n'), /manifest\.diagnostics to contain exactly/);
  assert.match(issues.join('\n'), /counts\.failures to be 0/);
  assert.match(issues.join('\n'), /failureEvents to be 0/);
  assert.match(issues.join('\n'), /transparentIcons to be 0/);
  assert.match(issues.join('\n'), /failures\.json to be empty/);
  assert.match(issues.join('\n'), /unclassified zero-quantity/);
  assert.match(issues.join('\n'), /ingredient-semantics/);
  assert.match(issues.join('\n'), /err=true/);
});

test('rejects version, abort, omitted, category, quantity, catalog, and semantic defects', () => {
  const issues = exportQualityIssues(
    {
      manifest: {
        format: 2,
        minecraft: '1.20.1',
        aborted: true,
        settings: {iconScale: 1, recipeScale: 1},
        diagnostics: {failureEvents: 5, failureEventsOmitted: 1},
      },
      failures: [
        'category recipes mod.machine: failure',
        'ingredient amount type mod.FluidStack has no recognized numeric amount/count accessor; using quantity 1 for this type',
        'list ingredients for mod.CustomIngredient: failure',
        'recipe input ingredient mod.machine #2: failure',
      ],
      semanticErrorRecipes: 1,
    },
    MEATBALLCRAFT_112_PROFILE,
  );

  assert.equal(issues.length, 11);
  assert.match(issues.join('\n'), /format 1/);
  assert.match(issues.join('\n'), /1\.12\.2/);
  assert.match(issues.join('\n'), /aborted/);
  assert.match(issues.join('\n'), /48×48 item canvases/);
  assert.match(issues.join('\n'), /2× physical resolution/);
  assert.match(issues.join('\n'), /omitted 1/);
  assert.match(issues.join('\n'), /category failure/);
  assert.match(issues.join('\n'), /ingredient-quantity/);
  assert.match(issues.join('\n'), /incomplete ingredient-catalog/);
  assert.match(issues.join('\n'), /ingredient-semantics/);
  assert.match(issues.join('\n'), /err=true/);
});

test('blocks missing-item catalog entries without rejecting image-only diagnostics', () => {
  const missingItemIssues = exportQualityIssues(
    {
      manifest: validManifest,
      failures: ['item mod.CustomIngredient #17: java.lang.IllegalStateException: missing item'],
      semanticErrorRecipes: 0,
    },
    MEATBALLCRAFT_112_PROFILE,
  );
  assert.match(missingItemIssues.join('\n'), /incomplete ingredient-catalog/);

  assert.deepEqual(
    exportQualityIssues(
      {
        manifest: validManifest,
        failures: ['ingredient icon item|minecraft:stone: framebuffer unavailable'],
        semanticErrorRecipes: 0,
      },
      MEATBALLCRAFT_112_PROFILE,
    ),
    [],
  );
});

test('blocks ItemCatalog identity, extraction, and resource-id fallbacks', () => {
  const failures = [
    'ingredient resource id mod.CustomIngredient: java.lang.IllegalStateException; deferring to the unique id',
    'ingredient display name mod.CustomIngredient: java.lang.IllegalStateException; deferring to the unique id',
    'ingredient unique id mod.CustomIngredient: java.lang.IllegalStateException; using a deterministic resource/name identity',
    'ingredient unique id mod.CustomIngredient was null/blank; using logged deterministic fallback jeiexport-fallback:12345678',
    'ingredient resource id custom|jeiexport-fallback:12345678 was empty; using unique id',
  ];
  const issues = exportQualityIssues(
    {manifest: validManifest, failures, semanticErrorRecipes: 0},
    MEATBALLCRAFT_112_PROFILE,
  );

  assert.equal(issues.length, 1);
  assert.match(issues[0], /5 incomplete ingredient-catalog failure/);
  assert.match(issues[0], /ingredient resource id mod\.CustomIngredient/);
});

test('allows an explicitly logged blank cosmetic label to use the exact unique ID', () => {
  assert.deepEqual(
    exportQualityIssues(
      {
        manifest: validManifest,
        failures: [
          'ingredient display name item|tombstone:grave_plate was null/blank; using unique id',
          'ingredient display name item|example:format_only was null/blank after formatting-code removal; using unique id',
        ],
        semanticErrorRecipes: 0,
      },
      MEATBALLCRAFT_112_PROFILE,
    ),
    [],
  );
});

test('allows icon and mod-namespace fallbacks that do not replace catalog identity', () => {
  assert.deepEqual(
    exportQualityIssues(
      {
        manifest: validManifest,
        failures: [
          'ingredient icon custom|stable-id: framebuffer unavailable',
          'ingredient icon item|example:invisible: rendered image is fully transparent; omitting the PNG and JSON icon reference so the viewer uses its named fallback',
          'ingredient mod id custom|stable-id: helper failed; deriving namespace',
        ],
        semanticErrorRecipes: 0,
      },
      MEATBALLCRAFT_112_PROFILE,
    ),
    [],
  );
});

test('allows progress-count diagnostics when complete ingredient listing still succeeds', () => {
  assert.deepEqual(
    exportQualityIssues(
      {
        manifest: validManifest,
        failures: ['count ingredients for mod.CustomIngredient: approximate size unavailable'],
        semanticErrorRecipes: 0,
      },
      MEATBALLCRAFT_112_PROFILE,
    ),
    [],
  );
});

test('allows classified zero semantics but blocks every unclassified zero context', () => {
  const classified = [
    'ZERO_PREREQUISITE recipe input EIOTank #64767 type net.minecraftforge.fluids.FluidStack publishedAmount=20; XP Juice reservoir requirement',
    'ZERO_THRESHOLD recipe input modularmachinery.recipes.berserker_forge #0 type example.DemonWill publishedAmount=1; matching Will threshold',
    'ZERO_UNKNOWN_FLOW recipe input hatchery.generator.recipe #0 type net.minecraftforge.fluids.FluidStack publishedAmount=0; dynamic runtime flow',
    'ZERO_ABSENT_OUTPUT recipe output thermalexpansion.centrifuge_mobs #35 type net.minecraftforge.fluids.FluidStack publishedAmount=none; no XP fluid yield',
    'ZERO_INVALID_RECIPE recipe input EIOWC #523 type example.EnergyIngredient publishedAmount=none; invalid no-op row excluded',
  ];
  assert.deepEqual(
    exportQualityIssues(
      {manifest: validManifest, failures: classified, semanticErrorRecipes: 0},
      MEATBALLCRAFT_112_PROFILE,
    ),
    [],
  );

  const issues = exportQualityIssues(
    {
      manifest: validManifest,
      failures: [
        'recipe input ingredient newmod.machine #0 type newmod.Zero: java.lang.IllegalArgumentException: ZERO_UNCLASSIFIED no exact semantic adapter exists',
      ],
      semanticErrorRecipes: 0,
    },
    MEATBALLCRAFT_112_PROFILE,
  );
  assert.match(issues.join('\n'), /ingredient-semantics/);
});

test('rejects unknown profiles instead of silently using generic validation', () => {
  assert.throws(() => resolveQualityProfile('unknown-profile'), /Unknown export quality profile/);
});
