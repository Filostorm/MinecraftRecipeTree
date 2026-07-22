import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {renameSync, symlinkSync} from 'node:fs';
import {
  access,
  chmod,
  lstat,
  mkdtemp,
  mkdir,
  readFile,
  rm,
  symlink,
  writeFile,
} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, join, relative, sep} from 'node:path';
import test from 'node:test';
import {promisify} from 'node:util';
import {
  importExportData as importExportDataImplementation,
  importWorkspaceRootForDestination,
  publishTransactional,
} from './import-export-data.mjs';
import {
  GTNH_DATA_ATTRIBUTION,
  GTNH_284_HANDLER_POLICIES,
  GTNH_1710_PROFILE,
  GTNH_KNOWLEDGE_POLICY,
  MEATBALLCRAFT_112_PROFILE,
  MULTIBLOCK_MADNESS_112_PROFILE,
  MULTIBLOCK_MADNESS_2_118_PROFILE,
} from './export-quality-policy.mjs';
import {PUBLICATION_ID_PATTERN} from './publication-id.mjs';
import {
  configureMultiblockExportFixture,
  createRawExportFixture,
  readJson,
  writeSyntheticExporterBuildIdentity,
  writeJson,
  writeNonUniformImage,
} from './test-export-fixture.mjs';
import {validateExportData} from './validate-export-data.mjs';

const execFileAsync = promisify(execFile);
const PRODUCTION_RENDER_SETTINGS = Object.freeze({iconScale: 3, recipeScale: 2});

function importExportData(options) {
  return importExportDataImplementation({profile: MEATBALLCRAFT_112_PROFILE, ...options});
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

function isContained(parent, child) {
  const value = relative(parent, child);
  return value === '' || (!value.startsWith(`..${sep}`) && value !== '..');
}

async function addSingleRecipePreview(source, {stochastic = false} = {}) {
  const categoryRoot = join(source, 'recipes', 'minecraft_crafting');
  const previewPath = join(categoryRoot, 'r0.png');
  // Production recipeScale=2 requires a 32×32 physical preview for a 16×16 logical layout.
  await writeNonUniformImage(previewPath, 32);
  const physicalPreview = await readFile(previewPath);
  await writeJson(join(categoryRoot, 'recipes.json'), [
    {
      id: 'minecraft:test',
      img: 'r0.png',
      w: 16,
      h: 16,
      in: [[stochastic ? ['minecraft:stone', 1, null, 0.5] : ['minecraft:stone', 1]]],
      out: [[stochastic ? ['minecraft:stone', 1, null, 0.25] : ['minecraft:stone', 1]]],
      ...(stochastic ? {cat: [[['minecraft:stone', 1]]]} : {}),
    },
  ]);
  const manifest = await readJson(join(source, 'manifest.json'));
  manifest.counts.recipes = 1;
  await writeJson(join(source, 'manifest.json'), manifest);
  const categories = await readJson(join(source, 'categories.json'));
  categories.categories[0].count = 1;
  await writeJson(join(source, 'categories.json'), categories);
  await writeJson(join(source, 'index.json'), {
    'minecraft:stone': {p: [[0, 0]], u: [[0, 0]]},
  });
  return {categoryRoot, previewPath, physicalPreview};
}

test('requires an explicit quality profile before inspecting source or creating work data', async () => {
  await assert.rejects(
    importExportDataImplementation({source: '/unused', dryRun: true}),
    /explicit export quality profile is required/i,
  );
});

test('imports both Multiblock Madness versions with dynamic counts and production scales', async () => {
  for (const [profile, minecraft] of [
    [MULTIBLOCK_MADNESS_112_PROFILE, '1.12.2'],
    [MULTIBLOCK_MADNESS_2_118_PROFILE, '1.18.2'],
  ]) {
    const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-profile-test-'));
    try {
      const source = join(root, 'raw-source');
      const destination = join(root, 'public', 'exports');
      await createRawExportFixture(source, {iconScale: 3, recipeScale: 2});
      const manifest = await configureMultiblockExportFixture(source, profile);
      assert.equal(manifest.minecraft, minecraft);
      await mkdir(dirname(destination), {recursive: true});

      await importExportDataImplementation({source, destination, profile, dryRun: true});
      assert.equal(await pathIsMissing(destination), true);
      assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
    } finally {
      await rm(root, {recursive: true, force: true});
    }
  }
});

async function configureGtnhFixture(source) {
  await addSingleRecipePreview(source, {stochastic: true});
  const manifestPath = join(source, 'manifest.json');
  const manifest = await readJson(manifestPath);
  manifest.format = 2;
  manifest.minecraft = '1.7.10';
  manifest.profile = GTNH_1710_PROFILE;
  manifest.forge = '10.13.4.1614';
  manifest.nei = '2.8.44-GTNH';
  manifest.pack = {
    name: 'GT New Horizons',
    version: '2.8.4',
    identitySource: 'explicit-request',
  };
  manifest.handlerPolicies = structuredClone(GTNH_284_HANDLER_POLICIES);
  manifest.knowledgePolicy = {...GTNH_KNOWLEDGE_POLICY};
  manifest.attribution = {...GTNH_DATA_ATTRIBUTION};
  manifest.diagnostics.nei = {
    itemListLoaded: true,
    itemListRawEntries: 48,
    itemListExcludedEntries: 46,
    itemListRetainedEntries: 2,
    itemListRetainedUniqueIdentities: 1,
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
    recipesEnumerated: 1,
    recipeWidgetsRendered: 1,
    itemIconsRendered: 1,
    informationalEmptyOutputRecipes: 513,
    knowledgeIndependentAspectNames: 69,
    unloadedHandlerCategories: 0,
    ambiguousHandlerCategories: 0,
    duplicateHandlerCategories: 0,
  };
  await writeJson(manifestPath, manifest);
  await writeSyntheticExporterBuildIdentity(source, GTNH_1710_PROFILE);
  return manifest;
}

test('imports GTNH 2.8.4 with runtime-rendered visuals and no bundled source artwork', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-gtnh-profile-test-'));
  try {
    const source = join(root, 'raw-source');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source, {iconScale: 3, recipeScale: 2});
    await configureGtnhFixture(source);
    const rawRecipePreview = await readFile(
      join(source, 'recipes', 'minecraft_crafting', 'r0.png'),
    );
    await mkdir(dirname(destination), {recursive: true});

    await importExportDataImplementation({
      source,
      destination,
      profile: GTNH_1710_PROFILE,
    });
    const manifest = await readJson(join(destination, 'manifest.json'));
    assert.equal(manifest.publicationPolicy, undefined);
    assert.equal(manifest.web.visualAssets, undefined);
    assert.equal(manifest.web.recipeImages.mode, 'included');
    const item = (await readJson(join(destination, 'items.json'))).items[0];
    const category = (await readJson(join(destination, 'categories.json'))).categories[0];
    const recipe = (
      await readJson(join(destination, 'recipes', 'minecraft_crafting', 'recipes.json'))
    )[0];
    assert.match(item.icon, /^assets\/s\/000-\d+-\d+\.webp$/);
    assert.match(category.icon, /^assets\/s\/000-\d+-\d+\.webp$/);
    assert.match(recipe.img, /^assets\/s\/000-\d+-\d+\.webp$/);
    assert.equal(recipe.w, 16);
    assert.equal(recipe.h, 16);
    assert.deepEqual(recipe.in, [[['minecraft:stone', 1, null, 0.5]]]);
    assert.deepEqual(recipe.out, [[['minecraft:stone', 1, null, 0.25]]]);
    assert.deepEqual(recipe.cat, [[['minecraft:stone', 1]]]);
    assert.equal(await pathIsMissing(join(destination, 'icons')), true);
    assert.equal(await pathIsMissing(join(destination, 'mobs')), true);
    assert.equal(await pathIsMissing(join(destination, 'assets', 'pack-000.bin')), false);
    const validation = await validateExportData(destination, {
      profile: GTNH_1710_PROFILE,
      requirePublicationId: true,
      verifyPublicationId: true,
    });
    assert.equal(validation.imageReferences, 2);
    assert.deepEqual(
      await readFile(join(source, 'recipes', 'minecraft_crafting', 'r0.png')),
      rawRecipePreview,
      'the runtime-rendered visual pipeline must never mutate the raw source',
    );
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('GTNH import rejects unloaded NEI categories before creating staging data', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-gtnh-handler-test-'));
  try {
    const source = join(root, 'raw-source');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source, {iconScale: 3, recipeScale: 2});
    const manifest = await configureGtnhFixture(source);
    manifest.diagnostics.nei.unloadedHandlerCategories = 1;
    await writeJson(join(source, 'manifest.json'), manifest);
    await mkdir(dirname(destination), {recursive: true});

    await assert.rejects(
      importExportDataImplementation({
        source,
        destination,
        profile: GTNH_1710_PROFILE,
        dryRun: true,
      }),
      /unloadedHandlerCategories.*0/,
    );
    assert.equal(await pathIsMissing(destination), true);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('GTNH import rejects policy drift before creating staging data', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-gtnh-policy-test-'));
  try {
    const source = join(root, 'raw-source');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source, {iconScale: 3, recipeScale: 2});
    const manifest = await configureGtnhFixture(source);
    manifest.knowledgePolicy.playerResearchMutated = true;
    await writeJson(join(source, 'manifest.json'), manifest);
    await mkdir(dirname(destination), {recursive: true});

    await assert.rejects(
      importExportDataImplementation({
        source,
        destination,
        profile: GTNH_1710_PROFILE,
        dryRun: true,
      }),
      /knowledgePolicy\.playerResearchMutated/,
    );
    assert.equal(await pathIsMissing(destination), true);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('GTNH import rejects attribution drift before creating staging data', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-gtnh-attribution-test-'));
  try {
    const source = join(root, 'raw-source');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source, {iconScale: 3, recipeScale: 2});
    const manifest = await configureGtnhFixture(source);
    manifest.attribution.sourceUrl =
      'https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/tree/2.8.3';
    await writeJson(join(source, 'manifest.json'), manifest);
    await mkdir(dirname(destination), {recursive: true});

    await assert.rejects(
      importExportDataImplementation({
        source,
        destination,
        profile: GTNH_1710_PROFILE,
        dryRun: true,
      }),
      /attribution\.sourceUrl/,
    );
    assert.equal(await pathIsMissing(destination), true);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('GTNH import rejects a recipe record without its rendered NEI preview', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-gtnh-preview-test-'));
  try {
    const source = join(root, 'raw-source');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source, {iconScale: 3, recipeScale: 2});
    await configureGtnhFixture(source);
    const recipePath = join(source, 'recipes', 'minecraft_crafting', 'recipes.json');
    const recipes = await readJson(recipePath);
    delete recipes[0].img;
    delete recipes[0].w;
    delete recipes[0].h;
    await writeJson(recipePath, recipes);
    await rm(join(source, 'recipes', 'minecraft_crafting', 'r0.png'));
    await mkdir(dirname(destination), {recursive: true});

    await assert.rejects(
      importExportDataImplementation({
        source,
        destination,
        profile: GTNH_1710_PROFILE,
        dryRun: true,
        omitRecipeImages: true,
      }),
      /requires one recipe preview per recipe.*missing.*1/i,
    );
    assert.equal(await pathIsMissing(destination), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('Multiblock import rejects a dynamically counted corpus with a missing recipe preview', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-missing-preview-test-'));
  try {
    const source = join(root, 'raw-source');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source, {iconScale: 3, recipeScale: 2});
    await configureMultiblockExportFixture(source, MULTIBLOCK_MADNESS_112_PROFILE);
    await writeJson(join(source, 'recipes', 'minecraft_crafting', 'recipes.json'), [
      {
        id: 'minecraft:missing_preview',
        in: [[['minecraft:stone', 1]]],
        out: [[['minecraft:stone', 1]]],
      },
    ]);
    const manifestPath = join(source, 'manifest.json');
    const manifest = await readJson(manifestPath);
    manifest.counts.recipes = 1;
    await writeJson(manifestPath, manifest);
    const categoriesPath = join(source, 'categories.json');
    const categories = await readJson(categoriesPath);
    categories.categories[0].count = 1;
    await writeJson(categoriesPath, categories);
    await writeJson(join(source, 'index.json'), {
      'minecraft:stone': {p: [[0, 0]], u: [[0, 0]]},
    });
    await mkdir(dirname(destination), {recursive: true});

    await assert.rejects(
      importExportDataImplementation({
        source,
        destination,
        profile: MULTIBLOCK_MADNESS_112_PROFILE,
        dryRun: true,
        omitRecipeImages: true,
      }),
      /requires one recipe preview per recipe.*missing.*1/i,
    );
    assert.equal(await pathIsMissing(destination), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('dry run stages outside public and leaves the destination unchanged', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-test-'));
  const originalLog = console.log;
  const logs = [];
  try {
    const source = join(root, 'raw-source');
    const destinationParent = join(root, 'public');
    const destination = join(destinationParent, 'exports');
    await createRawExportFixture(source, PRODUCTION_RENDER_SETTINGS);
    await writeSyntheticExporterBuildIdentity(source, MEATBALLCRAFT_112_PROFILE);
    await mkdir(destination, {recursive: true});
    await writeFile(join(destination, 'sentinel.txt'), 'unchanged\n');

    console.log = (...args) => {
      logs.push(args.map(String).join(' '));
      originalLog(...args);
    };
    await importExportData({source, destination, dryRun: true});

    assert.equal(await readFile(join(destination, 'sentinel.txt'), 'utf8'), 'unchanged\n');
    const stagingLog = logs.find(line => line.includes('out-of-public staging:'));
    assert.ok(stagingLog, 'expected an explicit staging-path log');
    const stagingPath = stagingLog.slice(stagingLog.indexOf('staging:') + 'staging:'.length).trim();
    assert.equal(isContained(destinationParent, stagingPath), false);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    console.log = originalLog;
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects a source-root symlink before creating import work data', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-symlink-test-'));
  try {
    const source = join(root, 'raw-source');
    const sourceLink = join(root, 'raw-source-link');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source, PRODUCTION_RENDER_SETTINGS);
    await symlink(source, sourceLink, 'dir');
    await mkdir(dirname(destination), {recursive: true});

    await assert.rejects(
      importExportData({source: sourceLink, destination, dryRun: true}),
      /source is symlink|source-root symlinks are refused/i,
    );
    assert.equal((await lstat(sourceLink)).isSymbolicLink(), true);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects a symlinked top-level image root before staging or optimization', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-image-root-link-test-'));
  try {
    const source = join(root, 'raw-source');
    const externalIcons = join(root, 'external-icons');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source, PRODUCTION_RENDER_SETTINGS);
    await mkdir(externalIcons);
    await writeNonUniformImage(join(externalIcons, 'stone.png'));
    await rm(join(source, 'icons'), {recursive: true});
    await symlink(externalIcons, join(source, 'icons'), 'dir');
    await mkdir(dirname(destination), {recursive: true});

    await assert.rejects(
      importExportData({source, destination, dryRun: true}),
      /unsupported filesystem entry.*icons|symlinks and special files are refused/i,
    );
    assert.equal(await pathIsMissing(join(externalIcons, 'stone.webp')), true);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects a nested directory symlink before staging or optimization', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-nested-link-test-'));
  try {
    const source = join(root, 'raw-source');
    const externalDirectory = join(root, 'external-recipes');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source, PRODUCTION_RENDER_SETTINGS);
    await mkdir(externalDirectory);
    await symlink(externalDirectory, join(source, 'recipes', 'linked-recipes'), 'dir');
    await mkdir(dirname(destination), {recursive: true});

    await assert.rejects(
      importExportData({source, destination, dryRun: true}),
      /unsupported filesystem entry.*linked-recipes|symlinks and special files are refused/i,
    );
    assert.equal(await pathIsMissing(join(source, 'icons', 'stone.webp')), true);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects a special filesystem entry before staging or optimization', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-special-entry-test-'));
  try {
    const source = join(root, 'raw-source');
    const destination = join(root, 'public', 'exports');
    const fifoPath = join(source, 'export-control.fifo');
    await createRawExportFixture(source, PRODUCTION_RENDER_SETTINGS);
    await execFileAsync('/usr/bin/mkfifo', [fifoPath]);
    await mkdir(dirname(destination), {recursive: true});

    await assert.rejects(
      importExportData({source, destination, dryRun: true}),
      /unsupported filesystem entry.*export-control\.fifo|symlinks and special files are refused/i,
    );
    assert.equal(await pathIsMissing(join(source, 'icons', 'stone.webp')), true);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects a source that becomes unsafe while the clone traversal is starting', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-staging-recheck-test-'));
  const originalLog = console.log;
  let sourceMutated = false;
  try {
    const source = join(root, 'raw-source');
    const externalIcons = join(root, 'external-icons');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source, PRODUCTION_RENDER_SETTINGS);
    await mkdir(dirname(destination), {recursive: true});

    console.log = (...args) => {
      const line = args.map(String).join(' ');
      originalLog(...args);
      if (!sourceMutated && line.includes('Source filesystem preflight accepted')) {
        renameSync(join(source, 'icons'), externalIcons);
        symlinkSync(externalIcons, join(source, 'icons'), 'dir');
        sourceMutated = true;
      }
    };

    await assert.rejects(
      importExportData({source, destination, dryRun: true}),
      /required copy-on-write raw export clone failed|symlinks and special files are refused/i,
    );
    assert.equal(sourceMutated, true);
    assert.equal(await pathIsMissing(join(externalIcons, 'stone.webp')), true);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    console.log = originalLog;
    await rm(root, {recursive: true, force: true});
  }
});

test('publishes the packed dataset atomically and removes rollback work data', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-publish-test-'));
  try {
    const source = join(root, 'raw-source');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source, PRODUCTION_RENDER_SETTINGS);
    await writeSyntheticExporterBuildIdentity(source, MEATBALLCRAFT_112_PROFILE);
    await mkdir(destination, {recursive: true});
    await writeFile(join(destination, 'sentinel.txt'), 'old dataset\n');

    await importExportData({source, destination});

    assert.equal(await pathIsMissing(join(destination, 'sentinel.txt')), true);
    assert.equal(await pathIsMissing(join(destination, 'icons')), true);
    assert.equal(await pathIsMissing(join(destination, 'assets-index.json')), true);
    assert.equal(await pathIsMissing(join(destination, 'assets', 'pack-000.bin')), false);
    const manifest = await readJson(join(destination, 'manifest.json'));
    assert.match(manifest.publicationId, PUBLICATION_ID_PATTERN);
    assert.equal(manifest.web?.packedImages, 'coordinate-v1');
    assert.equal(manifest.web?.maxPackBytes, 1024 * 1024);
    const items = await readJson(join(destination, 'items.json'));
    const categories = await readJson(join(destination, 'categories.json'));
    assert.match(items.items[0].icon, /^assets\/s\/000-\d+-\d+\.webp$/);
    assert.match(categories.categories[0].icon, /^assets\/s\/000-\d+-\d+\.webp$/);

    const itemShardSource = `${JSON.stringify(items.items)}\n`;
    const itemShardPath = join(destination, 'data', 'items', 'part-000.json');
    await mkdir(dirname(itemShardPath), {recursive: true});
    await writeFile(itemShardPath, itemShardSource);
    await writeJson(join(destination, 'items.json'), {
      format: 'mrt-sharded-json-v1',
      kind: 'array',
      count: items.items.length,
      parts: [
        {
          path: 'data/items/part-000.json',
          start: 0,
          count: items.items.length,
          bytes: Buffer.byteLength(itemShardSource),
        },
      ],
    });
    assert.equal((await validateExportData(destination)).items, 1);

    const orphanShardPath = join(destination, 'data', 'items', 'part-999.json');
    await writeFile(orphanShardPath, '[]\n');
    await assert.rejects(validateExportData(destination), /Unreferenced JSON shard/);
    await rm(orphanShardPath);

    const coordinateMatch = categories.categories[0].icon.match(
      /^(assets\/s\/\d+-)(\d+)(-\d+\.webp)$/,
    );
    assert.ok(coordinateMatch);
    categories.categories[0].icon =
      coordinateMatch[1] + String(Number(coordinateMatch[2]) + 1) + coordinateMatch[3];
    await writeJson(join(destination, 'categories.json'), categories);
    await assert.rejects(validateExportData(destination), /gap or overlap/);
    assert.equal(await pathIsMissing(importWorkspaceRootForDestination(destination)), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('omit-recipe-images imports retain original PNGs only through validation and publish no recipe raster', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-omission-test-'));
  try {
    const source = join(root, 'raw-source');
    const destination = join(root, 'public', 'exports');
    await createRawExportFixture(source, PRODUCTION_RENDER_SETTINGS);
    await writeSyntheticExporterBuildIdentity(source, MEATBALLCRAFT_112_PROFILE);
    const {previewPath, physicalPreview} = await addSingleRecipePreview(source);
    await mkdir(dirname(destination), {recursive: true});

    await importExportData({source, destination, omitRecipeImages: true});

    assert.deepEqual(
      await readFile(previewPath),
      physicalPreview,
      'copy-on-write staging must not mutate the raw sidecar source PNG',
    );
    const manifest = await readJson(join(destination, 'manifest.json'));
    assert.deepEqual(
      {
        mode: manifest.web.recipeImages.mode,
        references: manifest.web.recipeImages.references,
        files: manifest.web.recipeImages.files,
        encoding: manifest.web.recipeImages.encoding,
        bytes: manifest.web.recipeImages.bytes,
      },
      {
        mode: 'omitted',
        references: 1,
        files: 1,
        encoding: 'png',
        bytes: physicalPreview.length,
      },
    );
    const recipe = (
      await readJson(join(destination, 'recipes', 'minecraft_crafting', 'recipes.json'))
    )[0];
    assert.equal('img' in recipe, false);
    assert.equal('w' in recipe, false);
    assert.equal('h' in recipe, false);
    assert.equal(
      await pathIsMissing(
        join(destination, 'recipes', 'minecraft_crafting', 'r0.png'),
      ),
      true,
    );
    assert.equal(
      await pathIsMissing(
        join(destination, 'recipes', 'minecraft_crafting', 'r0.webp'),
      ),
      true,
    );
    await validateExportData(destination, {
      requirePublicationId: true,
      verifyPublicationId: true,
    });
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('restores the prior live dataset when the real staging rename fails', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-import-rollback-test-'));
  const stagingParent = join(root, 'staging-parent');
  try {
    const staging = join(stagingParent, 'staging');
    const destinationParent = join(root, 'public');
    const destination = join(destinationParent, 'exports');
    const backup = join(destinationParent, '.rollback-backup');
    await mkdir(staging, {recursive: true});
    await mkdir(destination, {recursive: true});
    await writeFile(join(staging, 'new.txt'), 'new dataset\n');
    await writeFile(join(destination, 'old.txt'), 'old dataset\n');

    // The backup rename uses destinationParent and succeeds. Removing staging
    // from its read-only parent then fails, exercising the actual rollback path.
    await chmod(stagingParent, 0o555);
    await assert.rejects(
      publishTransactional(staging, destination, backup),
      /EACCES|EPERM|permission denied|operation not permitted/i,
    );
    await chmod(stagingParent, 0o755);

    assert.equal(await readFile(join(destination, 'old.txt'), 'utf8'), 'old dataset\n');
    assert.equal(await readFile(join(staging, 'new.txt'), 'utf8'), 'new dataset\n');
    assert.equal(await pathIsMissing(backup), true);
  } finally {
    try {
      await chmod(stagingParent, 0o755);
    } catch (error) {
      if (error?.code !== 'ENOENT') {
        console.error(`Test cleanup could not restore permissions on ${stagingParent}.`, error);
      }
    }
    await rm(root, {recursive: true, force: true});
  }
});
