import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {writeFileSync} from 'node:fs';
import {
  access,
  mkdir,
  mkdtemp,
  readFile,
  readdir,
  rm,
  stat,
  symlink,
  unlink,
  writeFile,
} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, relative} from 'node:path';
import test from 'node:test';
import sharp from 'sharp';
import {
  buildRecipePreviewSidecar,
  DATASET_PUBLICATION_ID_PATTERN,
  MAX_CATEGORY_BYTES,
  MAX_PACK_BYTES,
  MAX_PACK_INDEX_BYTES,
  MEATBALLCRAFT_CONTRACT,
  recipePreviewContractForProfile,
  RECIPE_PREVIEW_CATEGORY_FORMAT,
  RECIPE_PREVIEW_PACK_INDEX_FORMAT,
  RECIPE_PREVIEW_SIDECAR_DATA_ONLY_FORMAT,
  RECIPE_PREVIEW_SIDECAR_FORMAT,
} from './build-recipe-preview-sidecar.mjs';
import {
  GTNH_DATA_ATTRIBUTION,
  GTNH_284_HANDLER_POLICIES,
  GTNH_1710_PROFILE,
  GTNH_KNOWLEDGE_POLICY,
  MEATBALLCRAFT_112_PROFILE,
  MULTIBLOCK_MADNESS_112_PROFILE,
  MULTIBLOCK_MADNESS_2_118_PROFILE,
} from './export-quality-policy.mjs';
import {
  requireRecipePreviewCategory,
  requireRecipePreviewManifest,
} from '../src/data/previewAssets.ts';
import {computePublicationId} from './publication-id.mjs';
import {
  createRecipeImageInventory,
  decodedRgbaSha256,
  normalizedLogicalRecipePngPath,
  RECIPE_IMAGE_INVENTORY_FORMAT,
} from './recipe-image-inventory.mjs';
import {SHARDED_JSON_FORMAT} from './sharded-documents.mjs';

const FIXTURE_RECIPE_COUNT = 24;
const MULTIBLOCK_MADNESS_PACK_IDENTITY = Object.freeze({
  name: 'Multiblock Madness',
  version: '3.2.3',
  identitySource: 'explicit-request',
});
const MULTIBLOCK_MADNESS_2_PACK_IDENTITY = Object.freeze({
  name: 'Multiblock Madness 2',
  version: '1.0.0',
  identitySource: 'explicit-request',
});
const GTNH_PACK_IDENTITY = Object.freeze({
  name: 'GT New Horizons',
  version: '2.8.4',
  identitySource: 'explicit-request',
});
const FIXTURE_CONTRACT = Object.freeze({
  format: 1,
  minecraft: '1.12.2',
  settings: Object.freeze({iconScale: 1, recipeScale: 1, mobCanvas: 256}),
  counts: Object.freeze({
    items: 2,
    recipes: FIXTURE_RECIPE_COUNT,
    categories: 1,
    mobs: 0,
    blockDrops: 0,
    failures: 0,
  }),
  diagnostics: Object.freeze({failureEvents: 0, failureEventsOmitted: 0}),
  recipeImages: Object.freeze({previews: FIXTURE_RECIPE_COUNT - 1, missing: 1}),
});
const SCALED_FIXTURE_CONTRACT = Object.freeze({
  ...FIXTURE_CONTRACT,
  settings: Object.freeze({
    iconScale: 3,
    recipeScale: 2,
    mobCanvas: 256,
    worldStartupOptimization: Object.freeze({
      enabled: true,
      policy: 'dimension-0-plus-should-load-spawn',
      applied: true,
      originalDimensions: 93,
      selectedDimensions: 4,
      skippedDimensions: 89,
    }),
  }),
});

function qualitySampleForProfile(profile, recipeCount = FIXTURE_RECIPE_COUNT) {
  if (profile === MULTIBLOCK_MADNESS_112_PROFILE) {
    return {
      enabled: true,
      recipeTargets: recipeCount,
      selectorCounts: {recipeId: 0, sourceIndex: recipeCount},
    };
  }
  if (profile === MULTIBLOCK_MADNESS_2_118_PROFILE) {
    return {
      selectorCounts: {recipeId: 0, sourceIndex: recipeCount},
      requested: Array.from({length: recipeCount}, (_, sourceIndex) => ({
        categoryId: 'fixture:category',
        sourceIndex,
      })),
    };
  }
  return undefined;
}

function completeProfileFixtureContract(
  profile,
  minecraft,
  failures = 0,
  {qualitySample, nativeIconCorrections = 2, transparentIcons = 0, warnings = []} = {},
) {
  const mm1 = profile === MULTIBLOCK_MADNESS_112_PROFILE;
  const mm2 = profile === MULTIBLOCK_MADNESS_2_118_PROFILE;
  const gtnh = profile === GTNH_1710_PROFILE;
  const pack = gtnh
    ? GTNH_PACK_IDENTITY
    : mm2
      ? MULTIBLOCK_MADNESS_2_PACK_IDENTITY
      : profile === MULTIBLOCK_MADNESS_112_PROFILE
        ? MULTIBLOCK_MADNESS_PACK_IDENTITY
        : undefined;
  return recipePreviewContractForProfile(
    profile,
    {
      format: gtnh ? 2 : 1,
      minecraft,
      ...(pack === undefined ? {} : {pack}),
      ...(gtnh
        ? {
            profile: GTNH_1710_PROFILE,
            forge: '10.13.4.1614',
            nei: '2.8.44-GTNH',
            handlerPolicies: GTNH_284_HANDLER_POLICIES,
            knowledgePolicy: GTNH_KNOWLEDGE_POLICY,
            attribution: GTNH_DATA_ATTRIBUTION,
          }
        : {}),
      aborted: false,
      settings: {iconScale: 3, recipeScale: 2, mobCanvas: 256},
      counts: {
        items: 2,
        recipes: FIXTURE_RECIPE_COUNT,
        categories: 1,
        mobs: 0,
        blockDrops: 0,
        ...(mm2 ? {nativeIconCorrections} : {}),
        failures,
      },
      diagnostics: {
        failureEvents: failures,
        failureEventsOmitted: 0,
        ...(mm1
          ? {warningEvents: warnings.length, warningEventsOmitted: 0}
          : {}),
        ...(mm2 ? {nativeIconCorrections, transparentIcons} : {}),
        ...(gtnh
          ? {
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
                recipesEnumerated: FIXTURE_RECIPE_COUNT,
                recipeWidgetsRendered: FIXTURE_RECIPE_COUNT,
                itemIconsRendered: 2,
                informationalEmptyOutputRecipes: 513,
                knowledgeIndependentAspectNames: 69,
                unloadedHandlerCategories: 0,
                ambiguousHandlerCategories: 0,
                duplicateHandlerCategories: 0,
              },
            }
          : {}),
      },
      ...(qualitySample === undefined ? {} : {qualitySample}),
    },
    mm1 ? warnings : undefined,
  );
}

const quietLogger = Object.freeze({
  info() {},
  warn() {},
  error() {},
});

test('production contract pins the repaired complete MeatballCraft corpus', () => {
  assert.deepEqual(MEATBALLCRAFT_CONTRACT, {
    format: 1,
    minecraft: '1.12.2',
    settings: {
      iconScale: 3,
      recipeScale: 2,
      mobCanvas: 256,
      worldStartupOptimization: {
        enabled: true,
        policy: 'dimension-0-plus-should-load-spawn',
        applied: true,
        originalDimensions: 93,
        selectedDimensions: 4,
        skippedDimensions: 89,
      },
    },
    counts: {
      items: 196161,
      recipes: 359215,
      categories: 674,
      mobs: 0,
      blockDrops: 0,
      failures: 130,
    },
    diagnostics: {failureEvents: 130, failureEventsOmitted: 0},
    recipeImages: {previews: 359215, missing: 0},
    hostedWeb: {
      format: 2,
      packedImages: 'coordinate-v1',
      maxPackBytes: 1024 * 1024,
      shardedJson: 'mrt-sharded-json-v1',
      maxShardBytes: 8 * 1024 * 1024,
    },
    repairProvenance: {
      format: 'mrt-recipe-preview-repair-overlay-v1',
      method: 'canonical-deep-equality-sample-overlay',
      repairedRecipePreviews: 27,
      compatibilityDiagnostics: {
        'zmaster587.AR.chemicalReactor': 25,
        'buildcraft:category_heatable': 1,
        'buildcraft:category_coolable': 1,
      },
      hashAlgorithm: 'sha256',
      treeHashFormat: 'mrt-plain-content-tree-sha256-v1',
      canonicalSha256: '11b9cbf2a8b7b1a65995612fa804dbeaf6c2d36ed1b16318783cd4d9064c4af4',
    },
  });
});

test('profile contract resolution preserves MeatballCraft and derives only new-pack counts', () => {
  assert.equal(
    recipePreviewContractForProfile(MEATBALLCRAFT_112_PROFILE, {counts: {recipes: 1}}),
    MEATBALLCRAFT_CONTRACT,
  );

  const first = completeProfileFixtureContract(
    MULTIBLOCK_MADNESS_112_PROFILE,
    '1.12.2',
  );
  const second = completeProfileFixtureContract(
    MULTIBLOCK_MADNESS_2_118_PROFILE,
    '1.18.2',
  );
  const gtnh = completeProfileFixtureContract(GTNH_1710_PROFILE, '1.7.10');
  for (const contract of [first, second, gtnh]) {
    assert.deepEqual(contract.settings, {iconScale: 3, recipeScale: 2, mobCanvas: 256});
    assert.deepEqual(contract.recipeImages, {previews: FIXTURE_RECIPE_COUNT, missing: 0});
    assert.equal(contract.counts.recipes, FIXTURE_RECIPE_COUNT);
    assert.equal(contract.hostedWeb.packedImages, 'coordinate-v1');
  }
  assert.equal(first.minecraft, '1.12.2');
  assert.deepEqual(first.diagnostics, {
    failureEvents: 0,
    failureEventsOmitted: 0,
    warningEvents: 0,
    warningEventsOmitted: 0,
  });
  assert.equal(second.minecraft, '1.18.2');
  assert.equal(second.counts.nativeIconCorrections, 2);
  assert.equal(second.diagnostics.nativeIconCorrections, 2);
  assert.equal(second.diagnostics.transparentIcons, 0);
  assert.equal(gtnh.minecraft, '1.7.10');
  assert.equal(gtnh.format, 2);
  assert.equal(gtnh.profile, GTNH_1710_PROFILE);
  assert.equal(gtnh.forge, '10.13.4.1614');
  assert.equal(gtnh.nei, '2.8.44-GTNH');
  assert.deepEqual(gtnh.pack, GTNH_PACK_IDENTITY);
  assert.deepEqual(gtnh.handlerPolicies, GTNH_284_HANDLER_POLICIES);
  assert.deepEqual(gtnh.knowledgePolicy, GTNH_KNOWLEDGE_POLICY);
  assert.deepEqual(gtnh.attribution, GTNH_DATA_ATTRIBUTION);
  assert.deepEqual(gtnh.diagnostics.nei, {
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
    recipesEnumerated: FIXTURE_RECIPE_COUNT,
    recipeWidgetsRendered: FIXTURE_RECIPE_COUNT,
    itemIconsRendered: 2,
    informationalEmptyOutputRecipes: 513,
    knowledgeIndependentAspectNames: 69,
    unloadedHandlerCategories: 0,
    ambiguousHandlerCategories: 0,
    duplicateHandlerCategories: 0,
  });
});

test('GTNH profile contract rejects NEI schema, handler, render, and pack-identity drift', () => {
  const contract = completeProfileFixtureContract(GTNH_1710_PROFILE, '1.7.10');
  const {recipeImages: _recipeImages, hostedWeb: _hostedWeb, ...rawManifest} = contract;
  const manifest = {...rawManifest, aborted: false};

  assert.throws(
    () =>
      recipePreviewContractForProfile(GTNH_1710_PROFILE, {
        ...manifest,
        diagnostics: {
          ...manifest.diagnostics,
          nei: {...manifest.diagnostics.nei, unloadedHandlerCategories: 1},
        },
      }),
    /unloadedHandlerCategories.*0/,
  );
  assert.throws(
    () =>
      recipePreviewContractForProfile(GTNH_1710_PROFILE, {
        ...manifest,
        diagnostics: {
          ...manifest.diagnostics,
          nei: {...manifest.diagnostics.nei, futureHandlerFailure: 0},
        },
      }),
    /diagnostics\.nei must contain exactly/,
  );
  assert.throws(
    () =>
      recipePreviewContractForProfile(GTNH_1710_PROFILE, {
        ...manifest,
        diagnostics: {
          ...manifest.diagnostics,
          nei: {...manifest.diagnostics.nei, recipeWidgetsRendered: FIXTURE_RECIPE_COUNT - 1},
        },
      }),
    /recipeWidgetsRendered.*counts\.recipes/,
  );
  assert.throws(
    () =>
      recipePreviewContractForProfile(GTNH_1710_PROFILE, {
        ...manifest,
        pack: {...manifest.pack, version: '2.8.3'},
      }),
    /pack\.version "2\.8\.4"|exact GTNH identity/,
  );
  assert.throws(
    () =>
      recipePreviewContractForProfile(GTNH_1710_PROFILE, {
        ...manifest,
        handlerPolicies: manifest.handlerPolicies.slice(0, -1),
      }),
    /handlerPolicies must contain exactly 66 pinned handler policies/,
  );
  assert.throws(
    () =>
      recipePreviewContractForProfile(GTNH_1710_PROFILE, {
        ...manifest,
        knowledgePolicy: {...manifest.knowledgePolicy, playerResearchMutated: true},
      }),
    /knowledgePolicy\.playerResearchMutated/,
  );
  assert.throws(
    () =>
      recipePreviewContractForProfile(GTNH_1710_PROFILE, {
        ...manifest,
        attribution: {...manifest.attribution, licenseIdentifier: 'CC BY 4.0'},
      }),
    /attribution\.licenseIdentifier/,
  );
});

test('profile contracts accept only their exact sampled-export telemetry', () => {
  const mm1Sample = qualitySampleForProfile(MULTIBLOCK_MADNESS_112_PROFILE);
  const mm1 = completeProfileFixtureContract(
    MULTIBLOCK_MADNESS_112_PROFILE,
    '1.12.2',
    0,
    {qualitySample: mm1Sample},
  );
  assert.deepEqual(mm1.qualitySample, mm1Sample);

  const mm2Sample = qualitySampleForProfile(MULTIBLOCK_MADNESS_2_118_PROFILE);
  const mm2 = completeProfileFixtureContract(
    MULTIBLOCK_MADNESS_2_118_PROFILE,
    '1.18.2',
    0,
    {qualitySample: mm2Sample},
  );
  assert.deepEqual(mm2.qualitySample, mm2Sample);
  const rawManifestFor = contract => {
    const {recipeImages: _recipeImages, hostedWeb: _hostedWeb, ...manifest} = contract;
    return {...manifest, aborted: false};
  };

  assert.throws(
    () =>
      completeProfileFixtureContract(
        MULTIBLOCK_MADNESS_112_PROFILE,
        '1.12.2',
        0,
        {qualitySample: {...mm1Sample, unexpected: true}},
      ),
    /must contain exactly enabled, recipeTargets, and selectorCounts/,
  );
  assert.throws(
    () =>
      completeProfileFixtureContract(
        MULTIBLOCK_MADNESS_2_118_PROFILE,
        '1.18.2',
        0,
        {qualitySample: {...mm2Sample, requested: [...mm2Sample.requested, mm2Sample.requested[0]]}},
      ),
    /sourceIndex must equal requested\.length|requested\.length must equal manifest\.counts\.recipes/,
  );
  assert.throws(
    () =>
      completeProfileFixtureContract(
        MULTIBLOCK_MADNESS_2_118_PROFILE,
        '1.18.2',
        0,
        {nativeIconCorrections: 2, transparentIcons: 1},
      ),
    /diagnostics\.transparentIcons to be 0/,
  );
  assert.throws(
    () =>
      recipePreviewContractForProfile(
        MULTIBLOCK_MADNESS_112_PROFILE,
        {
          ...rawManifestFor(mm1),
          counts: {...mm1.counts, nativeIconCorrections: 0},
        },
        [],
      ),
    /manifest\.counts must contain exactly items, recipes, categories, mobs, blockDrops, failures/,
  );
  assert.throws(
    () =>
      recipePreviewContractForProfile(MULTIBLOCK_MADNESS_2_118_PROFILE, {
        ...rawManifestFor(mm2),
        diagnostics: {...mm2.diagnostics, nativeIconCorrections: 1},
      }),
    /native-icon correction counts disagree/,
  );
  assert.throws(
    () =>
      recipePreviewContractForProfile(MULTIBLOCK_MADNESS_2_118_PROFILE, {
        ...rawManifestFor(mm2),
        diagnostics: {...mm2.diagnostics, unexpected: 0},
      }),
    /manifest\.diagnostics.*contain exactly/,
  );
});

test('profile contract resolution rejects unsupported scale and zero-failure drift', () => {
  const manifest = {
    format: 1,
    minecraft: '1.18.2',
    pack: MULTIBLOCK_MADNESS_2_PACK_IDENTITY,
    aborted: false,
    settings: {iconScale: 1, recipeScale: 2, mobCanvas: 256},
    counts: {
      items: 2,
      recipes: 3,
      categories: 1,
      mobs: 0,
      blockDrops: 0,
      nativeIconCorrections: 0,
      failures: 1,
    },
    diagnostics: {
      failureEvents: 0,
      failureEventsOmitted: 0,
      nativeIconCorrections: 0,
      transparentIcons: 0,
    },
  };
  assert.throws(
    () => recipePreviewContractForProfile(MULTIBLOCK_MADNESS_2_118_PROFILE, manifest),
    /48×48 item canvases/,
  );
  manifest.settings.iconScale = 3;
  assert.throws(
    () => recipePreviewContractForProfile(MULTIBLOCK_MADNESS_2_118_PROFILE, manifest),
    /manifest\.counts\.failures to be 0/,
  );
});

function json(value) {
  return `${JSON.stringify(value)}\n`;
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function canonicalJson(value) {
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map(key => `${JSON.stringify(key)}:${canonicalJson(value[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
}

function rgba(width, height, [red, green, blue, alpha]) {
  const pixels = Buffer.alloc(width * height * 4);
  for (let index = 0; index < pixels.length; index += 4) {
    pixels[index] = red;
    pixels[index + 1] = green;
    pixels[index + 2] = blue;
    pixels[index + 3] = alpha;
  }
  return pixels;
}

async function writePng(path, pixels, width, height, compressionLevel) {
  await sharp(pixels, {raw: {width, height, channels: 4}})
    .png({compressionLevel, adaptiveFiltering: compressionLevel > 0})
    .toFile(path);
}

async function createFixture(
  root,
  contract = FIXTURE_CONTRACT,
  {
    repairProvenance,
    missingRecipeIndex = 2,
    failures = null,
    warnings = null,
    hostedWarnings = warnings,
  } = {},
) {
  const rawRoot = join(root, 'raw');
  const categoryRoot = join(rawRoot, 'recipes', 'fixture.category');
  const hostedRoot = join(root, 'hosted');
  const hostedCategoryRoot = join(hostedRoot, 'recipes', 'fixture.category');
  const hostedPartsRoot = join(hostedCategoryRoot, 'parts');
  const hostedManifestPath = join(hostedRoot, 'manifest.json');
  await Promise.all([
    mkdir(categoryRoot, {recursive: true}),
    mkdir(hostedPartsRoot, {recursive: true}),
  ]);

  const logicalWidth = 8;
  const logicalHeight = 8;
  const physicalWidth = logicalWidth * contract.settings.recipeScale;
  const physicalHeight = logicalHeight * contract.settings.recipeScale;
  const redPixels = rgba(physicalWidth, physicalHeight, [210, 20, 30, 255]);
  const bluePixels = rgba(physicalWidth, physicalHeight, [15, 70, 220, 190]);
  const recipes = [];
  let sourcePngBytes = 0;
  for (let index = 0; index < FIXTURE_RECIPE_COUNT; index += 1) {
    if (index === missingRecipeIndex) {
      recipes.push({id: `fixture:${index}`, in: [], out: []});
      continue;
    }
    const path = join(categoryRoot, `r${index}.png`);
    const pixels = index === 3 || (index >= 4 && index % 2 === 1) ? bluePixels : redPixels;
    await writePng(path, pixels, physicalWidth, physicalHeight, index % 10);
    sourcePngBytes += (await stat(path)).size;
    recipes.push({
      id: `fixture:${index}`,
      img: `r${index}.png`,
      w: logicalWidth,
      h: logicalHeight,
      in: [],
      out: [],
    });
  }
  assert.notDeepEqual(
    await readFile(join(categoryRoot, 'r0.png')),
    await readFile(join(categoryRoot, 'r1.png')),
    'fixture duplicates must have different PNG encodings',
  );
  await writeFile(join(categoryRoot, 'recipes.json'), json(recipes));
  const rawCategory = {
    id: 'fixture.category',
    title: 'Fixture category',
    dir: 'recipes/fixture.category',
    count: FIXTURE_RECIPE_COUNT,
    icon: 'recipes/fixture.category/icon.png',
    catalysts: [],
  };
  await writeFile(join(rawRoot, 'categories.json'), json({categories: [rawCategory]}));
  await writeFile(
    join(hostedRoot, 'categories.json'),
    json({categories: [{...rawCategory, icon: 'assets/s/000-0-100.webp'}]}),
  );

  const hostedRecipes = recipes.map(recipe => {
    if (!Object.prototype.hasOwnProperty.call(recipe, 'img')) return recipe;
    const {img: _image, w: _width, h: _height, ...structuredRecipe} = recipe;
    return structuredRecipe;
  });
  const hostedGroups = [hostedRecipes.slice(0, 11), hostedRecipes.slice(11)];
  const hostedParts = [];
  let hostedStart = 0;
  for (const [partIndex, values] of hostedGroups.entries()) {
    const relativePath =
      `recipes/fixture.category/parts/part-${String(partIndex).padStart(3, '0')}.json`;
    const source = json(values);
    await writeFile(join(hostedRoot, ...relativePath.split('/')), source);
    hostedParts.push({
      path: relativePath,
      start: hostedStart,
      count: values.length,
      bytes: Buffer.byteLength(source),
    });
    hostedStart += values.length;
  }
  await writeFile(
    join(hostedCategoryRoot, 'recipes.json'),
    json({
      format: SHARDED_JSON_FORMAT,
      kind: 'array',
      count: hostedRecipes.length,
      parts: hostedParts,
    }),
  );

  const recipeImageInventory = createRecipeImageInventory();
  recipeImageInventory.beginCategory({
    categoryIndex: 0,
    categoryId: rawCategory.id,
    recipeCount: recipes.length,
  });
  for (const [recipeIndex, recipe] of recipes.entries()) {
    if (!Object.prototype.hasOwnProperty.call(recipe, 'img')) {
      recipeImageInventory.addMissing({
        categoryIndex: 0,
        categoryId: rawCategory.id,
        recipeIndex,
      });
      continue;
    }
    const {data, info} = await sharp(join(categoryRoot, recipe.img))
      .toColourspace('srgb')
      .ensureAlpha()
      .raw()
      .toBuffer({resolveWithObject: true});
    recipeImageInventory.addPreview({
      categoryIndex: 0,
      categoryId: rawCategory.id,
      recipeIndex,
      logicalPngPath: normalizedLogicalRecipePngPath(rawCategory.dir, recipe.img),
      declaredWidth: recipe.w,
      declaredHeight: recipe.h,
      decodedWidth: info.width,
      decodedHeight: info.height,
      rgbaSha256: decodedRgbaSha256(info.width, info.height, data),
    });
  }
  const hostedRecipeImageInventory = recipeImageInventory.finish();

  const commonManifest = {
    format: contract.format,
    generatedAt: '2026-07-18T12:04:02.055Z',
    durationMs: 12,
    aborted: false,
    minecraft: contract.minecraft,
    ...Object.fromEntries(
      ['profile', 'forge', 'nei'].flatMap(name =>
        contract[name] === undefined ? [] : [[name, contract[name]]],
      ),
    ),
    ...(contract.pack === undefined ? {} : {pack: structuredClone(contract.pack)}),
    ...(contract.handlerPolicies === undefined
      ? {}
      : {handlerPolicies: structuredClone(contract.handlerPolicies)}),
    ...(contract.knowledgePolicy === undefined
      ? {}
      : {knowledgePolicy: structuredClone(contract.knowledgePolicy)}),
    ...(contract.attribution === undefined
      ? {}
      : {attribution: structuredClone(contract.attribution)}),
    settings: {...contract.settings},
    counts: {...contract.counts},
    diagnostics: {...contract.diagnostics},
    ...(contract.qualitySample === undefined
      ? {}
      : {qualitySample: structuredClone(contract.qualitySample)}),
    mods: {minecraft: 'Minecraft'},
    ...(repairProvenance === undefined ? {} : {repairProvenance}),
  };
  await writeFile(join(rawRoot, 'manifest.json'), json(commonManifest));
  if (failures !== null) {
    await writeFile(join(rawRoot, 'failures.json'), json(failures));
  }
  if (Object.hasOwn(contract.diagnostics, 'warningEvents')) {
    const rawWarnings = warnings ?? [];
    const publishedWarnings = hostedWarnings ?? [];
    await writeFile(join(rawRoot, 'warnings.json'), json(rawWarnings));
    await writeFile(join(hostedRoot, 'warnings.json'), json(publishedWarnings));
  }
  const previewCount = recipes.filter(recipe => Object.hasOwn(recipe, 'img')).length;
  const hostedManifest = {
    ...commonManifest,
    web: {
      ...(contract.hostedWeb ?? {}),
      recipeImages: {
        mode: 'omitted',
        reason: 'hosting-archive-budget',
        references: previewCount,
        files: previewCount,
        encoding: 'png',
        bytes: sourcePngBytes,
        inventory: hostedRecipeImageInventory,
      },
    },
  };
  await writeFile(hostedManifestPath, json(hostedManifest));
  await writeFile(join(hostedRoot, 'publication-note.json'), json({fixture: true}));
  const publicationId = await computePublicationId(hostedRoot);
  await writeFile(hostedManifestPath, json({...hostedManifest, publicationId}));
  return {
    rawRoot,
    categoryRoot,
    hostedRoot,
    hostedCategoryRoot,
    hostedParts,
    hostedManifestPath,
    publicationId,
    hostedRecipeImageInventory,
    redPixels,
    bluePixels,
    sourcePngBytes,
    logicalWidth,
    logicalHeight,
    physicalWidth,
    physicalHeight,
  };
}

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

async function readCategoryPreviews(outputRoot, categoryIndex = 0) {
  const root = await readJson(
    join(outputRoot, 'categories', `${String(categoryIndex).padStart(3, '0')}.json`),
  );
  assert.equal(root.format, RECIPE_PREVIEW_CATEGORY_FORMAT);
  if (Array.isArray(root.previews)) return {root, previews: root.previews};
  assert.ok(Array.isArray(root.parts));
  const previews = [];
  for (const part of root.parts) {
    assert.equal(part.start, previews.length);
    const values = await readJson(join(outputRoot, ...part.path.split('/')));
    assert.equal(values.length, part.count);
    assert.equal((await stat(join(outputRoot, ...part.path.split('/')))).size, part.bytes);
    previews.push(...values);
  }
  return {root, previews};
}

async function collectRelativeFiles(root, current = root, files = []) {
  const entries = await readdir(current, {withFileTypes: true});
  for (const entry of entries) {
    const path = join(current, entry.name);
    if (entry.isDirectory()) await collectRelativeFiles(root, path, files);
    else if (entry.isFile()) files.push(relative(root, path).replaceAll('\\', '/'));
    else throw new Error(`Unexpected fixture output entry: ${path}`);
  }
  return files.sort();
}

async function pathIsMissing(path) {
  try {
    await access(path);
    return false;
  } catch (error) {
    if (error?.code === 'ENOENT') return true;
    throw error;
  }
}

test('decoded RGBA inventory canonicalizes only fully transparent hidden RGB', () => {
  const transparentRed = Buffer.from([255, 0, 0, 0]);
  const transparentBlack = Buffer.from([0, 0, 0, 0]);
  assert.equal(
    decodedRgbaSha256(1, 1, transparentRed),
    decodedRgbaSha256(1, 1, transparentBlack),
    'RGB hidden behind alpha=0 is not stable across lossless PNG/WebP conversion',
  );
  assert.deepEqual(transparentRed, Buffer.from([255, 0, 0, 0]), 'hashing must not mutate pixels');
  assert.notEqual(
    decodedRgbaSha256(1, 1, Buffer.from([255, 0, 0, 1])),
    decodedRgbaSha256(1, 1, Buffer.from([0, 0, 0, 1])),
    'partially transparent RGB remains visually meaningful',
  );
  assert.notEqual(
    decodedRgbaSha256(1, 1, Buffer.from([255, 0, 0, 255])),
    decodedRgbaSha256(1, 1, Buffer.from([0, 0, 0, 255])),
    'opaque RGB mutations must remain provenance-visible',
  );
});

test('recipe-image inventory enforces contiguous categories and recipes including empty categories', () => {
  const inventory = createRecipeImageInventory();
  inventory.beginCategory({categoryIndex: 0, categoryId: 'fixture.empty', recipeCount: 0});
  inventory.beginCategory({categoryIndex: 1, categoryId: 'fixture.one', recipeCount: 1});
  assert.throws(
    () =>
      inventory.addMissing({
        categoryIndex: 1,
        categoryId: 'fixture.one',
        recipeIndex: 1,
      }),
    /recipeIndex must be contiguous at 0/,
  );
  inventory.addMissing({
    categoryIndex: 1,
    categoryId: 'fixture.one',
    recipeIndex: 0,
  });
  const result = inventory.finish();
  assert.deepEqual(
    {entries: result.entries, previews: result.previews, missing: result.missing},
    {entries: 1, previews: 0, missing: 1},
  );
});

test('builder deduplicates decoded pixels, preserves nulls, bounds coordinates, shards mappings, and is deterministic', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-test-'));
  try {
    const fixture = await createFixture(root);
    const firstOutput = join(root, 'sidecar-a');
    const secondOutput = join(root, 'sidecar-b');
    const buildOptions = {
      source: fixture.rawRoot,
      datasetManifest: fixture.hostedManifestPath,
      contract: FIXTURE_CONTRACT,
      maxCategoryBytes: 400,
      concurrency: 4,
      logger: quietLogger,
    };
    const firstManifest = await buildRecipePreviewSidecar({...buildOptions, output: firstOutput});
    const secondManifest = await buildRecipePreviewSidecar({
      ...buildOptions,
      output: secondOutput,
      concurrency: 1,
    });

    assert.deepEqual(secondManifest, firstManifest);
    assert.deepEqual(await readJson(join(firstOutput, 'manifest.json')), firstManifest);
    assert.equal(firstManifest.format, RECIPE_PREVIEW_SIDECAR_FORMAT);
    assert.equal(firstManifest.datasetPublicationId, fixture.publicationId);
    assert.equal(
      fixture.hostedRecipeImageInventory.format,
      RECIPE_IMAGE_INVENTORY_FORMAT,
    );
    assert.equal(DATASET_PUBLICATION_ID_PATTERN.test(firstManifest.assetSetId), true);
    assert.equal(firstManifest.maxPackBytes, MAX_PACK_BYTES);
    assert.equal(firstManifest.packIndexFormat, RECIPE_PREVIEW_PACK_INDEX_FORMAT);
    assert.equal(firstManifest.maxPackIndexBytes, MAX_PACK_INDEX_BYTES);
    assert.equal(firstManifest.imageFormat, 'lossless-webp');
    assert.equal(firstManifest.categoryFormat, RECIPE_PREVIEW_CATEGORY_FORMAT);
    assert.equal(
      requireRecipePreviewManifest(firstManifest, fixture.publicationId).assetSetId,
      firstManifest.assetSetId,
    );
    assert.deepEqual(firstManifest.settings, {
      itemIconPixels: 16,
      recipeScale: 1,
      webpEffort: 4,
      maxCategoryBytes: 400,
    });
    assert.equal(firstManifest.counts.categories, 1);
    assert.equal(firstManifest.counts.recipes, FIXTURE_RECIPE_COUNT);
    assert.equal(firstManifest.counts.previews, FIXTURE_RECIPE_COUNT - 1);
    assert.equal(firstManifest.counts.missing, 1);
    assert.equal(firstManifest.counts.uniqueImages, 2);
    assert.equal(firstManifest.counts.duplicates, FIXTURE_RECIPE_COUNT - 3);
    assert.equal(firstManifest.counts.packs, 1);
    assert.ok(firstManifest.counts.inputBytes > 0);
    assert.equal(firstManifest.counts.inputBytes, fixture.sourcePngBytes);
    assert.equal(firstManifest.counts.hostedOmittedPngBytes, fixture.sourcePngBytes);
    assert.ok(firstManifest.counts.encodedBytes > firstManifest.counts.storedBytes);
    assert.equal(
      firstManifest.counts.storedBytes,
      firstManifest.packs.reduce((sum, pack) => sum + pack.bytes, 0),
    );
    assert.equal(
      firstManifest.counts.packIndexBytes,
      firstManifest.packs.reduce((sum, pack) => sum + pack.index.bytes, 0),
    );
    assert.equal(firstManifest.mapping.documents, 2);
    assert.equal(firstManifest.mapping.parts, 1);
    assert.equal(
      firstManifest.mapping.bytes,
      firstManifest.categoryDocuments.reduce((sum, document) => sum + document.bytes, 0),
    );

    const {root: categoryDocument, previews} = await readCategoryPreviews(firstOutput);
    requireRecipePreviewCategory(
      categoryDocument,
      0,
      'fixture.category',
      FIXTURE_RECIPE_COUNT,
      'fixture category mapping',
      firstManifest.packs,
    );
    assert.equal('previews' in categoryDocument, false, 'fixture must exercise category sharding');
    assert.equal(categoryDocument.parts.length, 1);
    assert.equal(previews.length, FIXTURE_RECIPE_COUNT);
    assert.deepEqual(previews[0], previews[1], 'distinct PNG byte streams have identical pixels');
    assert.equal(previews[2], null, 'a recipe with no img reference retains an explicit null');
    assert.notDeepEqual(previews[0], previews[3]);

    for (const [recipeIndex, coordinate] of previews.entries()) {
      if (recipeIndex === 2) continue;
      assert.ok(Array.isArray(coordinate));
      assert.equal(coordinate.length, 5);
      const [pack, offset, length, width, height] = coordinate;
      const packRecord = firstManifest.packs[pack];
      assert.ok(packRecord);
      assert.ok(offset >= 0);
      assert.ok(length > 0);
      assert.ok(offset + length <= packRecord.bytes);
      assert.equal(width, 8);
      assert.equal(height, 8);
    }

    for (const pack of firstManifest.packs) {
      const bytes = await readFile(join(firstOutput, ...pack.path.split('/')));
      assert.equal(bytes.length, pack.bytes);
      assert.equal(sha256(bytes), pack.sha256);
      assert.ok(bytes.length <= MAX_PACK_BYTES);
      const indexBytes = await readFile(join(firstOutput, ...pack.index.path.split('/')));
      assert.equal(indexBytes.length, pack.index.bytes);
      assert.equal(sha256(indexBytes), pack.index.sha256);
      assert.ok(indexBytes.length <= MAX_PACK_INDEX_BYTES);
      assert.equal(indexBytes.subarray(0, 4).toString('ascii'), 'MRPI');
      assert.equal(indexBytes.readUInt16BE(4), 1);
      assert.equal(indexBytes.readUInt16BE(6), 20);
      assert.equal(indexBytes.readUInt32BE(12), pack.bytes);
      assert.equal(indexBytes.readUInt32BE(16), pack.index.entries);
    }
    for (const document of firstManifest.categoryDocuments) {
      const bytes = await readFile(join(firstOutput, ...document.path.split('/')));
      assert.equal(bytes.length, document.bytes);
      assert.equal(sha256(bytes), document.sha256);
      assert.ok(bytes.length <= 400);
    }

    const [redPack, redOffset, redLength] = previews[0];
    const redPackBytes = await readFile(
      join(firstOutput, ...firstManifest.packs[redPack].path.split('/')),
    );
    const redWebp = redPackBytes.subarray(redOffset, redOffset + redLength);
    assert.equal((await sharp(redWebp).metadata()).format, 'webp');
    assert.deepEqual(
      await sharp(redWebp).ensureAlpha().raw().toBuffer(),
      fixture.redPixels,
      'lossless WebP payload must preserve the decoded RGBA pixels exactly',
    );

    const firstFiles = await collectRelativeFiles(firstOutput);
    const secondFiles = await collectRelativeFiles(secondOutput);
    assert.deepEqual(secondFiles, firstFiles);
    for (const file of firstFiles) {
      assert.deepEqual(
        await readFile(join(secondOutput, ...file.split('/'))),
        await readFile(join(firstOutput, ...file.split('/'))),
        `output file must be deterministic: ${file}`,
      );
    }
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder decodes scaled physical pixels while retaining logical recipe coordinates', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-scaled-test-'));
  try {
    const fixture = await createFixture(root, SCALED_FIXTURE_CONTRACT);
    const output = join(root, 'sidecar-scaled');
    const manifest = await buildRecipePreviewSidecar({
      source: fixture.rawRoot,
      datasetManifest: fixture.hostedManifestPath,
      output,
      contract: SCALED_FIXTURE_CONTRACT,
      logger: quietLogger,
    });

    assert.deepEqual(manifest.settings, {
      itemIconPixels: 48,
      recipeScale: 2,
      webpEffort: 4,
      maxCategoryBytes: MAX_CATEGORY_BYTES,
    });
    const {previews} = await readCategoryPreviews(output);
    const [packNumber, offset, length, logicalWidth, logicalHeight] = previews[0];
    assert.equal(logicalWidth, fixture.logicalWidth);
    assert.equal(logicalHeight, fixture.logicalHeight);
    const pack = await readFile(
      join(output, ...manifest.packs[packNumber].path.split('/')),
    );
    const metadata = await sharp(pack.subarray(offset, offset + length)).metadata();
    assert.equal(metadata.width, fixture.physicalWidth);
    assert.equal(metadata.height, fixture.physicalHeight);

    await writePng(
      join(fixture.categoryRoot, 'r0.png'),
      rgba(fixture.logicalWidth, fixture.logicalHeight, [210, 20, 30, 255]),
      fixture.logicalWidth,
      fixture.logicalHeight,
      6,
    );
    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: join(root, 'sidecar-wrong-physical-size'),
        contract: SCALED_FIXTURE_CONTRACT,
        logger: quietLogger,
      }),
      /decoded as 8×8×4, but the recipe requires a 16×16 physical image/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder accepts dynamic complete pack corpora with explicit profiles', async () => {
  for (const [profile, minecraft] of [
    [MULTIBLOCK_MADNESS_112_PROFILE, '1.12.2'],
    [MULTIBLOCK_MADNESS_2_118_PROFILE, '1.18.2'],
    [GTNH_1710_PROFILE, '1.7.10'],
  ]) {
    const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-profile-test-'));
    try {
      const fixtureContract = completeProfileFixtureContract(profile, minecraft, 0, {
        qualitySample: qualitySampleForProfile(profile),
      });
      const fixture = await createFixture(root, fixtureContract, {
        missingRecipeIndex: null,
        failures: [],
      });
      const output = join(root, 'sidecar');
      const manifest = await buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output,
        profile,
        concurrency: 3,
        logger: quietLogger,
      });

      assert.deepEqual(manifest.settings, {
        itemIconPixels: 48,
        recipeScale: 2,
        webpEffort: 4,
        maxCategoryBytes: MAX_CATEGORY_BYTES,
      });
      assert.equal(manifest.counts.recipes, FIXTURE_RECIPE_COUNT);
      assert.equal(manifest.counts.previews, FIXTURE_RECIPE_COUNT);
      assert.equal(manifest.counts.missing, 0);
    } finally {
      await rm(root, {recursive: true, force: true});
    }
  }
});

test('GTNH data-only policy emits a manifest-only v2 sidecar without reading recipe rasters', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-gtnh-data-only-test-'));
  try {
    const contract = completeProfileFixtureContract(GTNH_1710_PROFILE, '1.7.10');
    const fixture = await createFixture(root, contract, {
      missingRecipeIndex: null,
      failures: [],
    });
    const hostedManifest = await readJson(fixture.hostedManifestPath);
    hostedManifest.publicationPolicy = 'gtnh-structured-data-only-v1';
    delete hostedManifest.web.maxPackBytes;
    delete hostedManifest.web.packedImages;
    const hostedCategoriesPath = join(fixture.hostedRoot, 'categories.json');
    const hostedCategories = await readJson(hostedCategoriesPath);
    delete hostedCategories.categories[0].icon;
    await writeFile(hostedCategoriesPath, json(hostedCategories));
    hostedManifest.web.visualAssets = {
      format: 'mrt-visual-assets-policy-v1',
      mode: 'structured-data-only',
      policy: 'gtnh-structured-data-only-v1',
      itemIcons: 0,
      categoryIcons: 0,
      recipePreviews: 0,
      mobSprites: 0,
      packedImageFiles: 0,
    };
    hostedManifest.web.recipeImages.reason = 'third-party-artwork-rights-not-cleared';
    hostedManifest.web.recipeImages.policy = 'gtnh-structured-data-only-v1';
    await writeFile(fixture.hostedManifestPath, json(hostedManifest));
    const publicationId = await computePublicationId(fixture.hostedRoot);
    await writeFile(
      fixture.hostedManifestPath,
      json({...hostedManifest, publicationId}),
    );

    for (const name of await readdir(fixture.categoryRoot)) {
      if (name.endsWith('.png')) await unlink(join(fixture.categoryRoot, name));
    }
    const logged = [];
    const output = join(root, 'sidecar');
    const manifest = await buildRecipePreviewSidecar({
      source: fixture.rawRoot,
      datasetManifest: fixture.hostedManifestPath,
      output,
      profile: GTNH_1710_PROFILE,
      logger: {
        info(message) { logged.push(['info', message]); },
        warn(message) { logged.push(['warn', message]); },
        error(message) { logged.push(['error', message]); },
      },
    });
    assert.equal(manifest.format, RECIPE_PREVIEW_SIDECAR_DATA_ONLY_FORMAT);
    assert.equal(manifest.publicationPolicy, 'gtnh-structured-data-only-v1');
    assert.equal(manifest.exclusionReason, 'third-party-artwork-rights-not-cleared');
    assert.equal(manifest.datasetPublicationId, publicationId);
    assert.deepEqual(manifest.packs, []);
    assert.deepEqual(manifest.categoryDocuments, []);
    assert.deepEqual(manifest.mapping, {documents: 0, parts: 0, bytes: 0});
    assert.equal(manifest.counts.categories, 1);
    assert.equal(manifest.counts.recipes, FIXTURE_RECIPE_COUNT);
    assert.equal(manifest.counts.previews, 0);
    assert.equal(manifest.counts.missing, FIXTURE_RECIPE_COUNT);
    assert.equal(manifest.counts.inputBytes, 0);
    assert.equal(manifest.counts.storedBytes, 0);
    assert.equal(manifest.counts.packIndexBytes, 0);
    assert.equal(manifest.counts.hostedOmittedPngBytes, fixture.sourcePngBytes);
    assert.deepEqual(await collectRelativeFiles(output), ['manifest.json']);
    assert.equal(
      logged.some(([level, message]) =>
        level === 'warn' && message.includes('No raster file will be read')),
      true,
    );

    const driftedHostedManifest = await readJson(fixture.hostedManifestPath);
    driftedHostedManifest.web.recipeImages.reason = 'hosting-archive-budget';
    await writeFile(fixture.hostedManifestPath, json(driftedHostedManifest));
    const driftedPublicationId = await computePublicationId(fixture.hostedRoot);
    await writeFile(
      fixture.hostedManifestPath,
      json({...driftedHostedManifest, publicationId: driftedPublicationId}),
    );
    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: join(root, 'sidecar-reason-drift'),
        profile: GTNH_1710_PROFILE,
        logger: quietLogger,
      }),
      /exact rights-exclusion reason/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('MM1 sidecar requires warnings.json to survive core publication byte-for-byte', async () => {
  const warnings = [
    'NATIVE_ICON_OVERSCAN_RECOVERY_APPLIED ingredient item|fixture:overscan recovered',
    'ZERO_ABSENT_ALTERNATIVE recipe output nuclearcraft_centrifuge #0 slot 3',
  ];
  const contract = completeProfileFixtureContract(
    MULTIBLOCK_MADNESS_112_PROFILE,
    '1.12.2',
    0,
    {qualitySample: qualitySampleForProfile(MULTIBLOCK_MADNESS_112_PROFILE), warnings},
  );

  const acceptedRoot = await mkdtemp(join(tmpdir(), 'recipe-preview-mm1-warnings-test-'));
  try {
    const fixture = await createFixture(acceptedRoot, contract, {
      missingRecipeIndex: null,
      failures: [],
      warnings,
    });
    await buildRecipePreviewSidecar({
      source: fixture.rawRoot,
      datasetManifest: fixture.hostedManifestPath,
      output: join(acceptedRoot, 'sidecar'),
      profile: MULTIBLOCK_MADNESS_112_PROFILE,
      logger: quietLogger,
    });
  } finally {
    await rm(acceptedRoot, {recursive: true, force: true});
  }

  const driftRoot = await mkdtemp(join(tmpdir(), 'recipe-preview-mm1-warning-drift-test-'));
  try {
    const fixture = await createFixture(driftRoot, contract, {
      missingRecipeIndex: null,
      failures: [],
      warnings,
      hostedWarnings: [
        warnings[0],
        'ZERO_ABSENT_ALTERNATIVE recipe output nuclearcraft_centrifuge #0 slot 4',
      ],
    });
    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: join(driftRoot, 'sidecar'),
        profile: MULTIBLOCK_MADNESS_112_PROFILE,
        logger: quietLogger,
      }),
      /warnings\.json does not exactly match the hosted dataset publication/,
    );
  } finally {
    await rm(driftRoot, {recursive: true, force: true});
  }
});

test('builder requires explicit profile or explicit programmatic contract', async () => {
  await assert.rejects(
    buildRecipePreviewSidecar({
      source: '/unused',
      datasetManifest: '/unused',
      output: '/unused',
      logger: quietLogger,
    }),
    /explicit recipe-preview quality profile is required/i,
  );
});

test('dynamic pack profiles reject missing previews and nonzero failure diagnostics', async () => {
  const missingRoot = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-profile-missing-test-'));
  try {
    const contract = completeProfileFixtureContract(
      MULTIBLOCK_MADNESS_112_PROFILE,
      '1.12.2',
    );
    const fixture = await createFixture(missingRoot, contract, {failures: []});
    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: join(missingRoot, 'sidecar'),
        profile: MULTIBLOCK_MADNESS_112_PROFILE,
        logger: quietLogger,
      }),
      /web\.recipeImages must match the audited preview contract/,
    );
  } finally {
    await rm(missingRoot, {recursive: true, force: true});
  }

  assert.throws(
    () =>
      completeProfileFixtureContract(
        MULTIBLOCK_MADNESS_2_118_PROFILE,
        '1.18.2',
        1,
      ),
    /counts\.failures to be 0|failureEvents to be 0/,
  );
});

test('builder rejects world-startup optimization policy drift before reading recipe PNGs', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-world-policy-test-'));
  try {
    const fixture = await createFixture(root, SCALED_FIXTURE_CONTRACT);
    const rawManifestPath = join(fixture.rawRoot, 'manifest.json');
    const rawManifest = await readJson(rawManifestPath);
    rawManifest.settings.worldStartupOptimization.selectedDimensions = 5;
    await writeFile(rawManifestPath, json(rawManifest));
    const output = join(root, 'sidecar');

    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output,
        contract: SCALED_FIXTURE_CONTRACT,
        logger: quietLogger,
      }),
      /worldStartupOptimization\.selectedDimensions must be 4/,
    );
    assert.equal(await pathIsMissing(output), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder enforces the public 256 KiB category-document contract', async () => {
  await assert.rejects(
    buildRecipePreviewSidecar({
      source: '/unused',
      datasetManifest: '/unused',
      output: '/unused',
      contract: FIXTURE_CONTRACT,
      maxCategoryBytes: MAX_CATEGORY_BYTES + 1,
      logger: quietLogger,
    }),
    /maxCategoryBytes must be a safe integer within 256\.\.262144 bytes/,
  );
});

test('builder refuses output and staging paths inside either provenance input root', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-path-isolation-test-'));
  try {
    const fixture = await createFixture(root);
    const hostedOutput = join(fixture.hostedRoot, 'sidecar');
    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: hostedOutput,
        contract: FIXTURE_CONTRACT,
        logger: quietLogger,
      }),
      /output path must be outside the local hosted publication root/,
    );
    assert.equal(await pathIsMissing(hostedOutput), true);

    const rawOutput = join(fixture.rawRoot, 'sidecar');
    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: rawOutput,
        contract: FIXTURE_CONTRACT,
        logger: quietLogger,
      }),
      /output path must be outside the raw export root/,
    );
    assert.equal(await pathIsMissing(rawOutput), true);

    const hostedAlias = join(root, 'hosted-alias');
    await symlink(fixture.hostedRoot, hostedAlias, 'dir');
    const aliasedOutput = join(hostedAlias, 'sidecar');
    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: aliasedOutput,
        contract: FIXTURE_CONTRACT,
        logger: quietLogger,
      }),
      /output path must be outside the canonical local hosted publication root/,
    );
    assert.equal(await pathIsMissing(aliasedOutput), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('assetSetId binds identical preview content to one dataset publication', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-publication-test-'));
  try {
    const fixture = await createFixture(root);
    const firstManifest = await buildRecipePreviewSidecar({
      source: fixture.rawRoot,
      datasetManifest: fixture.hostedManifestPath,
      output: join(root, 'sidecar-a'),
      contract: FIXTURE_CONTRACT,
      concurrency: 4,
      logger: quietLogger,
    });
    await writeFile(join(fixture.hostedRoot, 'publication-note.json'), json({fixture: 'changed'}));
    const hostedManifest = await readJson(fixture.hostedManifestPath);
    const nextPublicationId = await computePublicationId(fixture.hostedRoot);
    await writeFile(
      fixture.hostedManifestPath,
      json({...hostedManifest, publicationId: nextPublicationId}),
    );
    const secondManifest = await buildRecipePreviewSidecar({
      source: fixture.rawRoot,
      datasetManifest: fixture.hostedManifestPath,
      output: join(root, 'sidecar-b'),
      contract: FIXTURE_CONTRACT,
      concurrency: 4,
      logger: quietLogger,
    });

    assert.equal(firstManifest.datasetPublicationId, fixture.publicationId);
    assert.equal(secondManifest.datasetPublicationId, nextPublicationId);
    assert.notEqual(secondManifest.assetSetId, firstManifest.assetSetId);
    assert.deepEqual(secondManifest.packs, firstManifest.packs);
    assert.deepEqual(secondManifest.categoryDocuments, firstManifest.categoryDocuments);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder rejects a syntactically valid but false local publicationId', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-false-publication-test-'));
  try {
    const fixture = await createFixture(root);
    const hostedManifest = await readJson(fixture.hostedManifestPath);
    await writeFile(
      fixture.hostedManifestPath,
      json({...hostedManifest, publicationId: 'f'.repeat(64)}),
    );

    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: join(root, 'sidecar'),
        contract: FIXTURE_CONTRACT,
        concurrency: 2,
        logger: quietLogger,
      }),
      /does not match canonical local publication content hash/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder rejects recipe-image inventory counts that diverge from omission metadata', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-inventory-count-test-'));
  try {
    const fixture = await createFixture(root);
    const hostedManifest = await readJson(fixture.hostedManifestPath);
    hostedManifest.web.recipeImages.inventory.previews -= 1;
    hostedManifest.web.recipeImages.inventory.missing += 1;
    await writeFile(fixture.hostedManifestPath, json(hostedManifest));
    hostedManifest.publicationId = await computePublicationId(fixture.hostedRoot);
    await writeFile(fixture.hostedManifestPath, json(hostedManifest));

    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: join(root, 'sidecar'),
        contract: FIXTURE_CONTRACT,
        logger: quietLogger,
      }),
      /web\.recipeImages inventory\/counts disagree/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder fails closed when the audited contract requires zero missing previews', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-completeness-test-'));
  try {
    const fixture = await createFixture(root);
    const output = join(root, 'sidecar');
    const completeContract = Object.freeze({
      ...FIXTURE_CONTRACT,
      recipeImages: Object.freeze({previews: FIXTURE_RECIPE_COUNT, missing: 0}),
    });

    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output,
        contract: completeContract,
        logger: quietLogger,
      }),
      /web\.recipeImages must match the audited preview contract/,
    );
    assert.equal(await pathIsMissing(output), true);
    assert.deepEqual(
      (await readdir(root)).filter(name => name.includes('.staging-')),
      [],
      'failed completeness validation must remove transaction staging',
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder pins the complete canonical repair provenance and rejects nested drift', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-repair-provenance-test-'));
  try {
    const repairProvenance = {
      format: 'fixture-repair-overlay-v1',
      method: 'fixture-canonical-overlay',
      repairedRecipePreviews: 1,
      compatibilityDiagnostics: {'fixture.category': 1},
      hashAlgorithm: 'sha256',
      treeHashFormat: 'fixture-tree-sha256-v1',
      source: {treeSha256: 'a'.repeat(64), missingRecipeImages: 1},
      sample: {treeSha256: 'b'.repeat(64), recipes: 1},
      repaired: {missingRecipeImages: 0, previewPngs: [{sourceIndex: 2}]},
    };
    const repairedContract = Object.freeze({
      ...FIXTURE_CONTRACT,
      repairProvenance: Object.freeze({
        format: repairProvenance.format,
        method: repairProvenance.method,
        repairedRecipePreviews: repairProvenance.repairedRecipePreviews,
        compatibilityDiagnostics: Object.freeze({...repairProvenance.compatibilityDiagnostics}),
        hashAlgorithm: repairProvenance.hashAlgorithm,
        treeHashFormat: repairProvenance.treeHashFormat,
        canonicalSha256: sha256(Buffer.from(canonicalJson(repairProvenance), 'utf8')),
      }),
    });
    const fixture = await createFixture(root, repairedContract, {repairProvenance});
    await buildRecipePreviewSidecar({
      source: fixture.rawRoot,
      datasetManifest: fixture.hostedManifestPath,
      output: join(root, 'sidecar-valid'),
      contract: repairedContract,
      logger: quietLogger,
    });

    const rawManifestPath = join(fixture.rawRoot, 'manifest.json');
    const rawManifest = await readJson(rawManifestPath);
    rawManifest.repairProvenance.source.treeSha256 = 'c'.repeat(64);
    await writeFile(rawManifestPath, json(rawManifest));
    const rejectedOutput = join(root, 'sidecar-rejected');
    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: rejectedOutput,
        contract: repairedContract,
        logger: quietLogger,
      }),
      /repairProvenance canonical SHA-256 is [a-f0-9]{64}; expected [a-f0-9]{64}/,
    );
    assert.equal(await pathIsMissing(rejectedOutput), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder requires hosted omission bytes to equal the original raw PNG corpus', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-source-bytes-test-'));
  try {
    const fixture = await createFixture(root);
    const hostedManifest = await readJson(fixture.hostedManifestPath);
    hostedManifest.web.recipeImages.bytes += 1;
    await writeFile(fixture.hostedManifestPath, json(hostedManifest));
    hostedManifest.publicationId = await computePublicationId(fixture.hostedRoot);
    await writeFile(fixture.hostedManifestPath, json(hostedManifest));

    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: join(root, 'sidecar'),
        contract: FIXTURE_CONTRACT,
        logger: quietLogger,
      }),
      /Raw recipe PNG bytes do not match hosted manifest web\.recipeImages accounting/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder rechecks the hosted publication hash immediately before commit', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-toctou-test-'));
  try {
    const fixture = await createFixture(root);
    const output = join(root, 'sidecar');
    let mutated = false;
    const logger = {
      info(message) {
        if (!mutated && String(message).includes('Processed 1/1 categories')) {
          mutated = true;
          writeFileSync(
            join(fixture.hostedRoot, 'publication-note.json'),
            json({fixture: 'concurrently-mutated'}),
          );
        }
      },
      warn() {},
      error() {},
    };

    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output,
        contract: FIXTURE_CONTRACT,
        concurrency: 4,
        logger,
      }),
      /does not match canonical local publication content hash/,
    );
    assert.equal(mutated, true);
    assert.equal(await pathIsMissing(output), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder rejects same-dimension recipe preview pixel swaps', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-pixel-swap-test-'));
  try {
    const fixture = await createFixture(root);
    const firstPath = join(fixture.categoryRoot, 'r0.png');
    const secondPath = join(fixture.categoryRoot, 'r3.png');
    const [firstBytes, secondBytes] = await Promise.all([
      readFile(firstPath),
      readFile(secondPath),
    ]);
    await Promise.all([writeFile(firstPath, secondBytes), writeFile(secondPath, firstBytes)]);

    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: join(root, 'sidecar'),
        contract: FIXTURE_CONTRACT,
        concurrency: 4,
        logger: quietLogger,
      }),
      /Raw recipe preview pixels\/path ordering do not match the hosted publication inventory/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder rejects same-dimension recipe preview pixel mutations', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-pixel-mutation-test-'));
  try {
    const fixture = await createFixture(root);
    await writePng(
      join(fixture.categoryRoot, 'r0.png'),
      rgba(8, 8, [25, 220, 80, 255]),
      8,
      8,
      4,
    );

    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: join(root, 'sidecar'),
        contract: FIXTURE_CONTRACT,
        concurrency: 4,
        logger: quietLogger,
      }),
      /Raw recipe preview pixels\/path ordering do not match the hosted publication inventory/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder rejects reordered raw recipes despite identical manifest timestamps and counts', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-reordered-test-'));
  try {
    const fixture = await createFixture(root);
    const recipesPath = join(fixture.categoryRoot, 'recipes.json');
    const recipes = await readJson(recipesPath);
    [recipes[0], recipes[1]] = [recipes[1], recipes[0]];
    await writeFile(recipesPath, json(recipes));

    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: join(root, 'sidecar'),
        contract: FIXTURE_CONTRACT,
        concurrency: 2,
        logger: quietLogger,
      }),
      /Raw category "fixture\.category" recipe 0 does not match the hosted publication at the same index/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder rejects modified raw recipe semantics despite identical manifest timestamps and counts', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-modified-test-'));
  try {
    const fixture = await createFixture(root);
    const recipesPath = join(fixture.categoryRoot, 'recipes.json');
    const recipes = await readJson(recipesPath);
    recipes[5].out = [{key: 'item|fixture:mutated', n: 1}];
    await writeFile(recipesPath, json(recipes));

    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: join(root, 'sidecar'),
        contract: FIXTURE_CONTRACT,
        concurrency: 2,
        logger: quietLogger,
      }),
      /Raw category "fixture\.category" recipe 5 does not match the hosted publication at the same index/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder rejects modified raw category semantics despite identical manifest timestamps and counts', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-category-test-'));
  try {
    const fixture = await createFixture(root);
    const categoriesPath = join(fixture.rawRoot, 'categories.json');
    const categories = await readJson(categoriesPath);
    categories.categories[0].title = 'Mutated fixture category';
    await writeFile(categoriesPath, json(categories));

    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: join(root, 'sidecar'),
        contract: FIXTURE_CONTRACT,
        concurrency: 2,
        logger: quietLogger,
      }),
      /Raw category 0 \("fixture\.category"\) does not match the hosted publication category at the same index/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder rejects a non-contiguous hosted recipe shard descriptor', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-shard-test-'));
  try {
    const fixture = await createFixture(root);
    const descriptorPath = join(fixture.hostedCategoryRoot, 'recipes.json');
    const descriptor = await readJson(descriptorPath);
    descriptor.parts[1].start += 1;
    await writeFile(descriptorPath, json(descriptor));
    const hostedManifest = await readJson(fixture.hostedManifestPath);
    const republishedId = await computePublicationId(fixture.hostedRoot);
    await writeFile(
      fixture.hostedManifestPath,
      json({...hostedManifest, publicationId: republishedId}),
    );

    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output: join(root, 'sidecar'),
        contract: FIXTURE_CONTRACT,
        concurrency: 2,
        logger: quietLogger,
      }),
      /parts\[1\]\.start must be the contiguous offset 11/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('a referenced missing PNG fails explicitly and leaves no published or staging directory', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-preview-sidecar-failure-test-'));
  try {
    const fixture = await createFixture(root);
    await unlink(join(fixture.categoryRoot, 'r0.png'));
    const output = join(root, 'sidecar');
    const errors = [];
    const logger = {
      info() {},
      warn() {},
      error(...values) {
        errors.push(values.map(String).join(' '));
      },
    };
    await assert.rejects(
      buildRecipePreviewSidecar({
        source: fixture.rawRoot,
        datasetManifest: fixture.hostedManifestPath,
        output,
        contract: FIXTURE_CONTRACT,
        concurrency: 2,
        logger,
      }),
      /Category "fixture\.category" recipe 0 could not be inspected/,
    );
    assert.equal(await pathIsMissing(output), true);
    assert.ok(errors.some(message => message.includes('removing staging directory')));
    assert.equal(
      (await readdir(root)).some(name => name.startsWith('.sidecar.staging-')),
      false,
      'failed transaction staging directory must be removed',
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
