import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {cp, mkdir, mkdtemp, readFile, readdir, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import sharp from 'sharp';
import {
  parseQualitySampleArguments,
  validateQualitySample,
  withinLogicalCellRefinement,
} from './validate-quality-sample.mjs';

const FRAME_KEY = 'item|thermalexpansion:frame:0';
const FRAME_ICON = 'icons/item/thermalexpansion/frame_d5baf740.png';
const CRAFTING_IMAGE = 'recipes/minecraft.crafting/r31319.png';
const BASIC_IMAGE = 'recipes/extendedcrafting_table_crafting_3x3/r131.png';

const CRAFTING_SEMANTICS = {
  id: 'crafttweaker:ct_shaped-557966710',
  in: [
    [['item|twilightforest:fiery_ingot', 1]],
    [['item|minecraft:glass', 1]],
    [['item|twilightforest:fiery_ingot', 1]],
    [['item|minecraft:glass', 1]],
    [['item|thermalfoundation:material:257', 1]],
    [['item|minecraft:glass', 1]],
    [['item|twilightforest:fiery_ingot', 1]],
    [['item|minecraft:glass', 1]],
    [['item|twilightforest:fiery_ingot', 1]],
  ],
  out: [[[FRAME_KEY, 4]]],
};

const BASIC_SEMANTICS = {
  in: [
    [['item|minecraft:iron_ingot', 1]],
    [['item|minecraft:glass', 1]],
    [['item|minecraft:iron_ingot', 1]],
    [['item|minecraft:glass', 1]],
    [['item|thermalfoundation:material:257', 1]],
    [['item|minecraft:glass', 1]],
    [['item|minecraft:iron_ingot', 1]],
    [['item|minecraft:glass', 1]],
    [['item|minecraft:iron_ingot', 1]],
  ],
  out: [[[FRAME_KEY, 1]]],
};

async function writeJson(path, value) {
  await mkdir(join(path, '..'), {recursive: true});
  await writeFile(path, `${JSON.stringify(value)}\n`);
}

function logicalPixels(width, height, seed) {
  const pixels = Buffer.alloc(width * height * 4);
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const offset = (y * width + x) * 4;
      pixels[offset] = (x * 11 + seed) % 256;
      pixels[offset + 1] = (y * 17 + seed * 3) % 256;
      pixels[offset + 2] = (x * 3 + y * 5 + seed * 7) % 256;
      pixels[offset + 3] = 255;
    }
  }
  return pixels;
}

function highResolutionPixels(
  logical,
  width,
  height,
  scale,
  refine = true,
  refineCell = () => true,
) {
  const physicalWidth = width * scale;
  const physicalHeight = height * scale;
  const pixels = Buffer.alloc(physicalWidth * physicalHeight * 4);
  for (let y = 0; y < physicalHeight; y += 1) {
    for (let x = 0; x < physicalWidth; x += 1) {
      const source = (Math.floor(y / scale) * width + Math.floor(x / scale)) * 4;
      const target = (y * physicalWidth + x) * 4;
      logical.copy(pixels, target, source, source + 4);
      if (
        refine &&
        refineCell(Math.floor(x / scale), Math.floor(y / scale), width, height) &&
        x % scale === scale - 1 &&
        y % scale === scale - 1
      ) {
        pixels[target] = (pixels[target] + 1) % 256;
      }
    }
  }
  return pixels;
}

function isRecipeChromeCell(x, y, width, height) {
  const inputGridX = x - 4;
  const inputGridY = y - 4;
  const inInputGrid = inputGridX >= 0 && inputGridY >= 0 && inputGridX < 54 && inputGridY < 54;
  const onInputSlotPerimeter =
    inInputGrid &&
    (inputGridX % 18 === 0 ||
      inputGridY % 18 === 0 ||
      inputGridX % 18 === 17 ||
      inputGridY % 18 === 17);
  return (
    x < 4 ||
    y < 4 ||
    x >= width - 4 ||
    y >= height - 4 ||
    onInputSlotPerimeter ||
    (x >= 62 && x < 84 && y >= 20 && y < 42) ||
    (x >= 94 &&
      x < 120 &&
      y >= 18 &&
      y < 44 &&
      (x < 97 || x >= 117 || y < 21 || y >= 41))
  );
}

async function writePng(path, pixels, width, height) {
  await mkdir(join(path, '..'), {recursive: true});
  await sharp(pixels, {raw: {width, height, channels: 4}}).png().toFile(path);
}

async function fileDigest(path) {
  return createHash('sha256').update(await readFile(path)).digest('hex');
}

async function listTree(root, prefix = '') {
  const paths = [];
  for (const entry of await readdir(join(root, prefix), {withFileTypes: true})) {
    const path = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (entry.isDirectory()) paths.push(...await listTree(root, path));
    else paths.push(path);
  }
  return paths.sort();
}

function item(k, id, n, m, icon) {
  return {k, id, n, m, icon};
}

async function createFixture({nearestIcon = false, optimizeWorldStartup = false} = {}) {
  const temporary = await mkdtemp(join(tmpdir(), 'mrt-quality-gate-'));
  const baseline = join(temporary, 'baseline');
  const sample = join(temporary, 'sample');
  const reference = join(temporary, 'reference');
  await Promise.all([mkdir(baseline), mkdir(sample)]);

  await writeJson(join(baseline, 'manifest.json'), {
    format: 1,
    generatedAt: '2026-07-18T00:00:00Z',
    durationMs: 900,
    aborted: false,
    minecraft: '1.12.2',
    settings: {iconScale: 1, recipeScale: 1, mobCanvas: 256},
    counts: {items: 1, recipes: 2, categories: 2, mobs: 0, blockDrops: 0, failures: 0},
    diagnostics: {failureEvents: 0, failureEventsOmitted: 0},
    mods: {thermalexpansion: 'Thermal Expansion'},
  });

  const baselineIcon = logicalPixels(16, 16, 1);
  const baselineCrafting = logicalPixels(124, 62, 2);
  const baselineBasic = logicalPixels(124, 62, 3);
  await Promise.all([
    writePng(join(baseline, FRAME_ICON), baselineIcon, 16, 16),
    writePng(join(baseline, CRAFTING_IMAGE), baselineCrafting, 124, 62),
    writePng(join(baseline, BASIC_IMAGE), baselineBasic, 124, 62),
  ]);
  const expectedBaselineDigests = {
    [FRAME_ICON]: await fileDigest(join(baseline, FRAME_ICON)),
    [CRAFTING_IMAGE]: await fileDigest(join(baseline, CRAFTING_IMAGE)),
    [BASIC_IMAGE]: await fileDigest(join(baseline, BASIC_IMAGE)),
  };

  const items = [
    item(FRAME_KEY, 'thermalexpansion:frame', 'Machine Frame', 'thermalexpansion', FRAME_ICON),
    item('item|twilightforest:fiery_ingot', 'twilightforest:fiery_ingot', 'Fiery Ingot', 'twilightforest', 'icons/item/twilightforest/fiery_ingot.png'),
    item('item|minecraft:glass', 'minecraft:glass', 'Glass', 'minecraft', 'icons/item/minecraft/glass.png'),
    item('item|thermalfoundation:material:257', 'thermalfoundation:material', 'Tin Gear', 'thermalfoundation', 'icons/item/thermalfoundation/tin_gear.png'),
    item('item|minecraft:iron_ingot', 'minecraft:iron_ingot', 'Iron Ingot', 'minecraft', 'icons/item/minecraft/iron_ingot.png'),
  ];
  await writeJson(join(sample, 'manifest.json'), {
    format: 1,
    generatedAt: '2026-07-18T00:01:00Z',
    durationMs: 1200,
    aborted: false,
    minecraft: '1.12.2',
    settings: {
      iconScale: 3,
      recipeScale: 2,
      mobCanvas: 256,
      worldStartupOptimization: optimizeWorldStartup
        ? {
            enabled: true,
            policy: 'dimension-0-plus-should-load-spawn',
            applied: true,
            originalDimensions: 93,
            selectedDimensions: 4,
            skippedDimensions: 89,
          }
        : {
            enabled: false,
            policy: 'dimension-0-plus-should-load-spawn',
            applied: false,
          },
    },
    qualitySample: {
      enabled: true,
      recipeTargets: 2,
      selectorCounts: {recipeId: 1, sourceIndex: 1},
    },
    counts: {items: items.length, recipes: 2, categories: 2, mobs: 0, blockDrops: 0, failures: 0},
    diagnostics: {failureEvents: 0, failureEventsOmitted: 0},
    mods: {thermalexpansion: 'Thermal Expansion'},
  });
  await writeJson(join(sample, 'items.json'), {items});
  await writeJson(join(sample, 'categories.json'), {categories: [
    {id: 'minecraft.crafting', title: 'Crafting', dir: 'recipes/minecraft.crafting', count: 1, catalysts: []},
    {id: 'extendedcrafting:table_crafting_3x3', title: 'Basic Crafting', dir: 'recipes/extendedcrafting_table_crafting_3x3', count: 1, catalysts: []},
  ]});
  await writeJson(join(sample, 'recipes/minecraft.crafting/recipes.json'), [{
    ...CRAFTING_SEMANTICS,
    img: 'r0.png',
    w: 124,
    h: 62,
  }]);
  await writeJson(join(sample, 'recipes/extendedcrafting_table_crafting_3x3/recipes.json'), [{
    ...BASIC_SEMANTICS,
    img: 'r0.png',
    w: 124,
    h: 62,
  }]);
  await writeJson(join(sample, 'failures.json'), []);
  await writeJson(join(sample, 'mobs.json'), {mobs: []});
  await writeJson(join(sample, 'blockdrops.json'), {blocks: {}});
  await writeJson(join(sample, 'index.json'), {[FRAME_KEY]: {p: [[0, 0], [1, 0]], u: []}});

  for (const [index, catalogItem] of items.entries()) {
    const logical = index === 0 ? baselineIcon : logicalPixels(16, 16, 10 + index);
    await writePng(
      join(sample, catalogItem.icon),
      highResolutionPixels(logical, 16, 16, 3, index !== 0 || !nearestIcon),
      48,
      48,
    );
  }
  await Promise.all([
    writePng(
      join(sample, 'recipes/minecraft.crafting/r0.png'),
      highResolutionPixels(
        baselineCrafting,
        124,
        62,
        2,
        true,
        (x, y, width, height) => !isRecipeChromeCell(x, y, width, height),
      ),
      248,
      124,
    ),
    writePng(
      join(sample, 'recipes/extendedcrafting_table_crafting_3x3/r0.png'),
      highResolutionPixels(
        baselineBasic,
        124,
        62,
        2,
        true,
        (x, y, width, height) => !isRecipeChromeCell(x, y, width, height),
      ),
      248,
      124,
    ),
  ]);
  await cp(sample, reference, {recursive: true});
  return {temporary, baseline, sample, reference, expectedBaselineDigests};
}

test('quality sample gate proves resolution, rerendering, lossless WebP, semantics, and reference parity', async t => {
  const fixture = await createFixture();
  t.after(() => rm(fixture.temporary, {recursive: true, force: true}));
  const before = await listTree(fixture.sample);
  const report = await validateQualitySample({
    baselineRoot: fixture.baseline,
    sampleRoot: fixture.sample,
    referenceSampleRoot: fixture.reference,
    expectedBaselineDigests: fixture.expectedBaselineDigests,
  });
  assert.equal(report.passed, true);
  assert.equal(report.contract.nativeLogicalItemGrid, '16x16');
  assert.equal(report.contract.itemRaster, '48x48');
  assert.equal(report.contract.physicalRecipePreview, '248x124');
  assert.equal(report.fidelity.rerenderComparisons.length, 3);
  assert.ok(report.fidelity.rerenderComparisons.every(entry => entry.refinedCells > 0));
  assert.equal(report.fidelity.recipeChromeComparisons.length, 2);
  assert.ok(
    report.fidelity.recipeChromeComparisons.every(comparison =>
      comparison.regions.every(region => region.matchRatio >= 0.98)),
  );
  assert.ok(
    report.fidelity.recipeChromeComparisons.every(comparison =>
      comparison.regions.some(region =>
        region.label === 'all nine 3x3 input-slot frame perimeters' &&
        region.auditedLogicalPixels === 612 &&
        region.auditedPhysicalPixels === 2448 &&
        region.matchingPhysicalPixels === 2448)),
  );
  assert.equal(report.fidelity.referenceComparison.pngFilesCompared, 7);
  assert.equal(report.fidelity.referenceComparison.jsonFilesCompared, 8);
  assert.equal(report.storage.itemIcons.count, 5);
  assert.equal(report.storage.recipePreviews.count, 2);
  assert.equal(report.storage.coldMachineFrameModal.assetCount, 7);
  assert.equal(report.storage.optimizationSignals.responsiveIconVariantsRecommended, false);
  assert.deepEqual(await listTree(fixture.sample), before, 'gate must not write into the sample export');
  assert.equal((await listTree(fixture.sample)).some(path => path.endsWith('.webp')), false);
});

test('quality sample gate rejects a nominal 48px icon that is only nearest-neighbor enlargement', async t => {
  const fixture = await createFixture({nearestIcon: true});
  t.after(() => rm(fixture.temporary, {recursive: true, force: true}));
  await assert.rejects(
    validateQualitySample({
      baselineRoot: fixture.baseline,
      sampleRoot: fixture.sample,
      expectedBaselineDigests: fixture.expectedBaselineDigests,
    }),
    /only a nearest-neighbor 3x enlargement/,
  );
});

test('quality sample gate rejects visible item sprites when the outer JEI frame is missing', async t => {
  const fixture = await createFixture();
  t.after(() => rm(fixture.temporary, {recursive: true, force: true}));
  const path = join(fixture.sample, 'recipes/minecraft.crafting/r0.png');
  const {data, info} = await sharp(path).ensureAlpha().raw().toBuffer({resolveWithObject: true});
  const scale = 2;
  for (let y = 0; y < info.height; y += 1) {
    for (let x = 0; x < info.width; x += 1) {
      const logicalX = Math.floor(x / scale);
      const logicalY = Math.floor(y / scale);
      if (
        logicalX >= 4 &&
        logicalY >= 4 &&
        logicalX < info.width / scale - 4 &&
        logicalY < info.height / scale - 4
      ) {
        continue;
      }
      const offset = (y * info.width + x) * 4;
      data[offset] = 198;
      data[offset + 1] = 198;
      data[offset + 2] = 198;
      data[offset + 3] = 255;
    }
  }
  await sharp(data, {raw: info}).png().toFile(path);
  await assert.rejects(
    validateQualitySample({
      baselineRoot: fixture.baseline,
      sampleRoot: fixture.sample,
      expectedBaselineDigests: fixture.expectedBaselineDigests,
    }),
    /outer JEI frame pixels.*layout chrome is missing or corrupted/,
  );
});

test('quality sample gate explicitly rejects missing 3x3 input-slot frame perimeters', async t => {
  const fixture = await createFixture();
  t.after(() => rm(fixture.temporary, {recursive: true, force: true}));
  const path = join(fixture.sample, 'recipes/minecraft.crafting/r0.png');
  const {data, info} = await sharp(path).ensureAlpha().raw().toBuffer({resolveWithObject: true});
  const scale = 2;
  for (let y = 0; y < info.height; y += 1) {
    for (let x = 0; x < info.width; x += 1) {
      const inputGridX = Math.floor(x / scale) - 4;
      const inputGridY = Math.floor(y / scale) - 4;
      if (
        inputGridX < 0 ||
        inputGridY < 0 ||
        inputGridX >= 54 ||
        inputGridY >= 54 ||
        (inputGridX % 18 !== 0 &&
          inputGridY % 18 !== 0 &&
          inputGridX % 18 !== 17 &&
          inputGridY % 18 !== 17)
      ) {
        continue;
      }
      const offset = (y * info.width + x) * 4;
      data[offset] = 198;
      data[offset + 1] = 198;
      data[offset + 2] = 198;
      data[offset + 3] = 255;
    }
  }
  await sharp(data, {raw: info}).png().toFile(path);
  await assert.rejects(
    validateQualitySample({
      baselineRoot: fixture.baseline,
      sampleRoot: fixture.sample,
      expectedBaselineDigests: fixture.expectedBaselineDigests,
    }),
    /all nine 3x3 input-slot frame perimeters pixels.*layout chrome is missing or corrupted/,
  );
});

test('quality sample gate requires exact MeatballCraft world-start optimization provenance', async t => {
  const control = await createFixture();
  t.after(() => rm(control.temporary, {recursive: true, force: true}));
  await assert.rejects(
    validateQualitySample({
      baselineRoot: control.baseline,
      sampleRoot: control.sample,
      requireWorldStartupOptimization: true,
      expectedBaselineDigests: control.expectedBaselineDigests,
    }),
    /must enable and apply the fail-closed world-startup optimization/,
  );

  const optimized = await createFixture({optimizeWorldStartup: true});
  t.after(() => rm(optimized.temporary, {recursive: true, force: true}));
  const report = await validateQualitySample({
    baselineRoot: optimized.baseline,
    sampleRoot: optimized.sample,
    requireWorldStartupOptimization: true,
    expectedBaselineDigests: optimized.expectedBaselineDigests,
  });
  assert.deepEqual(report.contract.worldStartupOptimization, {
    enabled: true,
    policy: 'dimension-0-plus-should-load-spawn',
    applied: true,
    originalDimensions: 93,
    selectedDimensions: 4,
    skippedDimensions: 89,
  });
});

test('quality sample gate rejects any canonical RGBA drift from a reference renderer', async t => {
  const fixture = await createFixture();
  t.after(() => rm(fixture.temporary, {recursive: true, force: true}));
  const path = join(fixture.reference, 'recipes/minecraft.crafting/r0.png');
  const {data, info} = await sharp(path).ensureAlpha().raw().toBuffer({resolveWithObject: true});
  data[0] = (data[0] + 17) % 256;
  await sharp(data, {raw: info}).png().toFile(path);
  await assert.rejects(
    validateQualitySample({
      baselineRoot: fixture.baseline,
      sampleRoot: fixture.sample,
      referenceSampleRoot: fixture.reference,
      expectedBaselineDigests: fixture.expectedBaselineDigests,
    }),
    /canonical RGBA differs from the reference sample/,
  );
});

test('quality sample gate rejects any semantic JSON drift from an optimized reference run', async t => {
  const fixture = await createFixture();
  t.after(() => rm(fixture.temporary, {recursive: true, force: true}));
  const path = join(fixture.reference, 'items.json');
  const document = JSON.parse(await readFile(path, 'utf8'));
  document.items[0].n = 'Drifted Machine Frame';
  await writeJson(path, document);
  await assert.rejects(
    validateQualitySample({
      baselineRoot: fixture.baseline,
      sampleRoot: fixture.sample,
      referenceSampleRoot: fixture.reference,
      expectedBaselineDigests: fixture.expectedBaselineDigests,
    }),
    /optimized sample items\.json bytes differ from the reference sample/,
  );
});

test('quality sample CLI parser is strict and refinement detects sub-cell detail', () => {
  assert.deepEqual(
    parseQualitySampleArguments([
      '--baseline-root', 'old',
      '--sample-root', 'new',
      '--reference-sample-root', 'reference',
    ]),
    {
      help: false,
      requireWorldStartupOptimization: false,
      baselineRoot: 'old',
      sampleRoot: 'new',
      referenceSampleRoot: 'reference',
    },
  );
  assert.deepEqual(
    parseQualitySampleArguments([
      '--baseline-root', 'old',
      '--sample-root', 'new',
      '--require-world-startup-optimization',
    ]),
    {
      help: false,
      requireWorldStartupOptimization: true,
      baselineRoot: 'old',
      sampleRoot: 'new',
    },
  );
  assert.throws(() => parseQualitySampleArguments(['--sample-root']), /requires a directory path/);
  assert.throws(() => parseQualitySampleArguments(['--unknown', 'x']), /Unknown/);

  const nearest = Buffer.from([
    1, 2, 3, 255, 1, 2, 3, 255,
    1, 2, 3, 255, 1, 2, 3, 255,
  ]);
  assert.deepEqual(withinLogicalCellRefinement(nearest, 2, 2, 2), {refinedCells: 0, logicalCells: 1});
  nearest[4] = 9;
  assert.deepEqual(withinLogicalCellRefinement(nearest, 2, 2, 2), {refinedCells: 1, logicalCells: 1});
});
