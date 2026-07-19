import assert from 'node:assert/strict';
import {cp, mkdtemp, mkdir, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import sharp from 'sharp';
import {
  createPlainTreeInventory,
  repairClonedPreviewTree,
  repairPreviewOverlayTransaction,
} from './repair-missing-recipe-previews.mjs';

const FIXTURE_CONTRACT = Object.freeze({
  minecraft: '1.12.2',
  iconScale: 3,
  recipeScale: 2,
  totalRecipes: 4,
  totalCategories: 2,
  recipePngsBefore: 1,
  expectedFailureCount: 3,
  categories: Object.freeze([
    Object.freeze({
      id: 'fixture:alpha',
      directory: 'recipes/fixture_alpha',
      sourceCount: 3,
      exportedCount: 3,
      logicalWidth: 10,
      logicalHeight: 5,
      physicalWidth: 20,
      physicalHeight: 10,
      sourceIndexes: Object.freeze([1, 2]),
    }),
    Object.freeze({
      id: 'fixture:beta',
      directory: 'recipes/fixture_beta',
      sourceCount: 1,
      exportedCount: 1,
      logicalWidth: 8,
      logicalHeight: 6,
      physicalWidth: 16,
      physicalHeight: 12,
      sourceIndexes: Object.freeze([0]),
    }),
  ]),
});

const MODS = Object.freeze({minecraft: 'Minecraft', fixture: 'Fixture Mod'});

function historicalFailure(categoryId, sourceIndex) {
  return (
    `recipe image ${categoryId} #${sourceIndex}: java.lang.NullPointerException: ` +
    'Recipe layout crashed during creation, see log.'
  );
}

async function writeJson(path, value, pretty = false) {
  await mkdir(join(path, '..'), {recursive: true});
  await writeFile(path, pretty ? JSON.stringify(value, null, 2) : JSON.stringify(value));
}

async function writePng(path, width, height, color) {
  await mkdir(join(path, '..'), {recursive: true});
  await sharp({
    create: {width, height, channels: 4, background: color},
  }).png({compressionLevel: 9}).toFile(path);
}

function alphaTargetOne() {
  return {
    in: [[['item|fixture:plate', 1]], [['fluid|fixture:oxygen', 250]]],
    out: [[['item|fixture:helmet', 1]]],
  };
}

function alphaTargetTwo() {
  return {
    in: [[['item|fixture:plate', 2]], [['fluid|fixture:oxygen', 500]]],
    out: [[['item|fixture:chestplate', 1]]],
  };
}

function betaTarget() {
  return {
    in: [[['fluid|fixture:cold', 1_000]]],
    out: [[['fluid|fixture:hot', 1_000]]],
  };
}

function manifest({sample = false} = {}) {
  const value = {
    format: 1,
    generatedAt: sample ? '2026-07-19T02:00:00.000Z' : '2026-07-19T01:00:00.000Z',
    durationMs: sample ? 200 : 1_000,
    aborted: false,
    minecraft: '1.12.2',
    settings: {iconScale: 3, recipeScale: 2, mobCanvas: 256},
    counts: {
      items: sample ? 6 : 20,
      recipes: sample ? 3 : 4,
      categories: 2,
      mobs: 0,
      blockDrops: 0,
      failures: sample ? 0 : 3,
    },
    diagnostics: {failureEvents: sample ? 0 : 3, failureEventsOmitted: 0},
    mods: MODS,
  };
  if (sample) {
    value.qualitySample = {
      enabled: true,
      recipeTargets: 3,
      selectorCounts: {recipeId: 0, sourceIndex: 3},
    };
    // Keep exporter field order: qualitySample precedes counts.
    const ordered = {};
    for (const key of ['format', 'generatedAt', 'durationMs', 'aborted', 'minecraft', 'settings']) {
      ordered[key] = value[key];
    }
    ordered.qualitySample = value.qualitySample;
    for (const key of ['counts', 'diagnostics', 'mods']) ordered[key] = value[key];
    return ordered;
  }
  return value;
}

async function createFixture(root) {
  const full = join(root, 'full');
  const sample = join(root, 'sample');
  await mkdir(full);
  await mkdir(sample);
  const categories = {
    categories: [
      {id: 'fixture:alpha', title: 'Alpha', dir: 'recipes/fixture_alpha', count: 3},
      {id: 'fixture:beta', title: 'Beta', dir: 'recipes/fixture_beta', count: 1},
    ],
  };
  await writeJson(join(full, 'manifest.json'), manifest(), true);
  await writeJson(join(full, 'categories.json'), categories);
  await writeJson(join(full, 'failures.json'), [
    historicalFailure('fixture:alpha', 1),
    historicalFailure('fixture:alpha', 2),
    historicalFailure('fixture:beta', 0),
  ]);
  await writeJson(join(full, 'recipes/fixture_alpha/recipes.json'), [
    {
      img: 'r0.png',
      w: 10,
      h: 5,
      in: [[['item|fixture:raw', 1]]],
      out: [[['item|fixture:existing', 1]]],
    },
    alphaTargetOne(),
    alphaTargetTwo(),
  ]);
  await writeJson(join(full, 'recipes/fixture_beta/recipes.json'), [betaTarget()]);
  await writePng(join(full, 'recipes/fixture_alpha/r0.png'), 20, 10, {r: 10, g: 20, b: 30, alpha: 1});

  await writeJson(join(sample, 'manifest.json'), manifest({sample: true}), true);
  await writeJson(join(sample, 'categories.json'), {
    categories: [
      {id: 'fixture:alpha', title: 'Alpha', dir: 'recipes/fixture_alpha', count: 2},
      {id: 'fixture:beta', title: 'Beta', dir: 'recipes/fixture_beta', count: 1},
    ],
  });
  await writeJson(join(sample, 'failures.json'), []);
  // Deliberately reverse the two alpha targets. Semantic equality, not array
  // position, must map r0 -> full r2 and r1 -> full r1.
  await writeJson(join(sample, 'recipes/fixture_alpha/recipes.json'), [
    {img: 'r0.png', w: 10, h: 5, ...alphaTargetTwo()},
    {img: 'r1.png', w: 10, h: 5, ...alphaTargetOne()},
  ]);
  await writeJson(join(sample, 'recipes/fixture_beta/recipes.json'), [
    {img: 'r0.png', w: 8, h: 6, ...betaTarget()},
  ]);
  await writePng(join(sample, 'recipes/fixture_alpha/r0.png'), 20, 10, {r: 200, g: 30, b: 40, alpha: 1});
  await writePng(join(sample, 'recipes/fixture_alpha/r1.png'), 20, 10, {r: 30, g: 200, b: 40, alpha: 1});
  await writePng(join(sample, 'recipes/fixture_beta/r0.png'), 16, 12, {r: 30, g: 40, b: 200, alpha: 1});
  return {full, sample};
}

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

test('repairs a reversed compatibility sample by unique canonical semantics and is byte deterministic', async () => {
  const root = await mkdtemp(join(tmpdir(), 'preview-repair-core-test-'));
  try {
    const {full, sample} = await createFixture(root);
    const first = join(root, 'first-staging');
    const second = join(root, 'second-staging');
    await cp(full, first, {recursive: true, force: false, errorOnExist: true});
    await cp(full, second, {recursive: true, force: false, errorOnExist: true});

    const firstResult = await repairClonedPreviewTree({
      stagingRoot: first,
      sampleRoot: sample,
      contract: FIXTURE_CONTRACT,
    });
    const secondResult = await repairClonedPreviewTree({
      stagingRoot: second,
      sampleRoot: sample,
      contract: FIXTURE_CONTRACT,
    });

    assert.equal(firstResult.repaired, 3);
    assert.equal(firstResult.normalizedTreeSha256, secondResult.normalizedTreeSha256);
    assert.equal((await createPlainTreeInventory(first)).sha256, (await createPlainTreeInventory(second)).sha256);
    const alpha = await readJson(join(first, 'recipes/fixture_alpha/recipes.json'));
    assert.deepEqual(alpha[1], {img: 'r1.png', w: 10, h: 5, ...alphaTargetOne()});
    assert.deepEqual(alpha[2], {img: 'r2.png', w: 10, h: 5, ...alphaTargetTwo()});
    assert.deepEqual(
      await readFile(join(first, 'recipes/fixture_alpha/r2.png')),
      await readFile(join(sample, 'recipes/fixture_alpha/r0.png')),
    );
    assert.deepEqual(
      await readFile(join(first, 'recipes/fixture_alpha/r1.png')),
      await readFile(join(sample, 'recipes/fixture_alpha/r1.png')),
    );
    assert.equal(Object.hasOwn((await readJson(join(full, 'recipes/fixture_alpha/recipes.json')))[1], 'img'), false);
    const repairedManifest = await readJson(join(first, 'manifest.json'));
    assert.equal(repairedManifest.counts.failures, 0);
    assert.equal(repairedManifest.diagnostics.failureEvents, 0);
    assert.equal(repairedManifest.repairProvenance.repaired.previewPngs.length, 3);
    assert.deepEqual(
      repairedManifest.repairProvenance.repaired.previewPngs.map(entry => [entry.category, entry.sourceIndex]),
      [['fixture:alpha', 1], ['fixture:alpha', 2], ['fixture:beta', 0]],
    );
    assert.deepEqual(await readJson(join(first, 'failures.json')), []);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects an ambiguous semantic match before adding any preview', async () => {
  const root = await mkdtemp(join(tmpdir(), 'preview-repair-ambiguous-test-'));
  try {
    const {full, sample} = await createFixture(root);
    const staging = join(root, 'staging');
    await cp(full, staging, {recursive: true});
    const alphaPath = join(staging, 'recipes/fixture_alpha/recipes.json');
    const alpha = await readJson(alphaPath);
    alpha[2] = structuredClone(alpha[1]);
    await writeJson(alphaPath, alpha);
    const sampleAlphaPath = join(sample, 'recipes/fixture_alpha/recipes.json');
    const sampleAlpha = await readJson(sampleAlphaPath);
    sampleAlpha[0] = {img: 'r0.png', w: 10, h: 5, ...alphaTargetOne()};
    await writeJson(sampleAlphaPath, sampleAlpha);
    const before = await createPlainTreeInventory(staging);
    await assert.rejects(
      repairClonedPreviewTree({stagingRoot: staging, sampleRoot: sample, contract: FIXTURE_CONTRACT}),
      /canonically matches 2 image-less full entries/,
    );
    assert.equal((await createPlainTreeInventory(staging)).sha256, before.sha256);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects extra sample PNGs, nonzero sample failures, and wrong physical dimensions', async t => {
  await t.test('extra PNG', async () => {
    const root = await mkdtemp(join(tmpdir(), 'preview-repair-extra-png-test-'));
    try {
      const {full, sample} = await createFixture(root);
      const staging = join(root, 'staging');
      await cp(full, staging, {recursive: true});
      await writePng(join(sample, 'recipes/fixture_alpha/r99.png'), 20, 10, {r: 1, g: 2, b: 3, alpha: 1});
      await assert.rejects(
        repairClonedPreviewTree({stagingRoot: staging, sampleRoot: sample, contract: FIXTURE_CONTRACT}),
        /unreferenced.*recipe PNG|must contain exactly 3 recipe PNGs/,
      );
    } finally {
      await rm(root, {recursive: true, force: true});
    }
  });

  await t.test('sample failure', async () => {
    const root = await mkdtemp(join(tmpdir(), 'preview-repair-sample-failure-test-'));
    try {
      const {full, sample} = await createFixture(root);
      const staging = join(root, 'staging');
      await cp(full, staging, {recursive: true});
      const sampleManifest = await readJson(join(sample, 'manifest.json'));
      sampleManifest.counts.failures = 1;
      sampleManifest.diagnostics.failureEvents = 1;
      await writeJson(join(sample, 'manifest.json'), sampleManifest, true);
      await writeJson(join(sample, 'failures.json'), ['not clean']);
      await assert.rejects(
        repairClonedPreviewTree({stagingRoot: staging, sampleRoot: sample, contract: FIXTURE_CONTRACT}),
        /sample manifest failures must be zero/,
      );
    } finally {
      await rm(root, {recursive: true, force: true});
    }
  });

  await t.test('physical dimensions', async () => {
    const root = await mkdtemp(join(tmpdir(), 'preview-repair-dimension-test-'));
    try {
      const {full, sample} = await createFixture(root);
      const staging = join(root, 'staging');
      await cp(full, staging, {recursive: true});
      await writePng(join(sample, 'recipes/fixture_alpha/r0.png'), 19, 10, {r: 1, g: 2, b: 3, alpha: 1});
      await assert.rejects(
        repairClonedPreviewTree({stagingRoot: staging, sampleRoot: sample, contract: FIXTURE_CONTRACT}),
        /physical width must be 20/,
      );
    } finally {
      await rm(root, {recursive: true, force: true});
    }
  });
});

test('rejects any historical-failure mismatch', async () => {
  const root = await mkdtemp(join(tmpdir(), 'preview-repair-failure-contract-test-'));
  try {
    const {full, sample} = await createFixture(root);
    const staging = join(root, 'staging');
    await cp(full, staging, {recursive: true});
    const failures = await readJson(join(staging, 'failures.json'));
    failures[1] = `${failures[1]} changed`;
    await writeJson(join(staging, 'failures.json'), failures);
    await assert.rejects(
      repairClonedPreviewTree({stagingRoot: staging, sampleRoot: sample, contract: FIXTURE_CONTRACT}),
      /historical compatibility strings exactly once/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('removes only the repair-target failure strings and retains unrelated diagnostics in order', async () => {
  const root = await mkdtemp(join(tmpdir(), 'preview-repair-retained-failures-test-'));
  try {
    const {full, sample} = await createFixture(root);
    const staging = join(root, 'staging');
    await cp(full, staging, {recursive: true});
    const unrelated = [
      'count ingredients for fixture.OptionalIngredient: approximate size unavailable',
      'item fixture.OptionalIngredient #1: diagnostic retained',
    ];
    const failures = await readJson(join(staging, 'failures.json'));
    failures.splice(1, 0, unrelated[0]);
    failures.push(unrelated[1]);
    await writeJson(join(staging, 'failures.json'), failures);
    const fullManifest = await readJson(join(staging, 'manifest.json'));
    fullManifest.counts.failures = failures.length;
    fullManifest.diagnostics.failureEvents = failures.length;
    await writeJson(join(staging, 'manifest.json'), fullManifest, true);

    await repairClonedPreviewTree({
      stagingRoot: staging,
      sampleRoot: sample,
      contract: FIXTURE_CONTRACT,
    });
    assert.deepEqual(await readJson(join(staging, 'failures.json')), unrelated);
    const repairedManifest = await readJson(join(staging, 'manifest.json'));
    assert.equal(repairedManifest.counts.failures, 2);
    assert.equal(repairedManifest.diagnostics.failureEvents, 2);
    assert.equal(repairedManifest.repairProvenance.source.failureEvents, 5);
    assert.equal(repairedManifest.repairProvenance.repaired.remainingFailureEvents, 2);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('APFS transaction publishes only a fresh sibling and rolls back invalid staging', {
  skip: process.platform !== 'darwin',
}, async () => {
  const root = await mkdtemp(join(tmpdir(), 'preview-repair-transaction-test-'));
  try {
    const {full, sample} = await createFixture(root);
    const output = join(root, 'repaired');
    await repairPreviewOverlayTransaction(
      {fullRoot: full, sampleRoot: sample, outputRoot: output},
      FIXTURE_CONTRACT,
    );
    assert.equal((await readJson(join(output, 'manifest.json'))).repairProvenance.repairedRecipePreviews, 3);
    await assert.rejects(
      repairPreviewOverlayTransaction(
        {fullRoot: full, sampleRoot: sample, outputRoot: output},
        FIXTURE_CONTRACT,
      ),
      /fresh repair output already exists/,
    );

    const badSample = join(root, 'bad-sample');
    await cp(sample, badSample, {recursive: true});
    await writePng(join(badSample, 'recipes/fixture_alpha/r0.png'), 19, 10, {r: 1, g: 1, b: 1, alpha: 1});
    const rejectedOutput = join(root, 'must-not-publish');
    await assert.rejects(
      repairPreviewOverlayTransaction(
        {fullRoot: full, sampleRoot: badSample, outputRoot: rejectedOutput},
        FIXTURE_CONTRACT,
      ),
      /physical width must be 20/,
    );
    await assert.rejects(readFile(rejectedOutput), /ENOENT/);
    const rootEntries = await import('node:fs/promises').then(module => module.readdir(root));
    assert.equal(rootEntries.some(name => name.includes('must-not-publish.repair-staging')), false);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
