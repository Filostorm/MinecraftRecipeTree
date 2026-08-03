import assert from 'node:assert/strict';
import {cp, link, mkdtemp, mkdir, readFile, rm, symlink, writeFile} from 'node:fs/promises';
import {writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import sharp from 'sharp';
import {compareDeterministicExports} from './compare-deterministic-exports.mjs';
import {MULTIBLOCK_MADNESS_112_PROFILE} from './export-quality-policy.mjs';
import {
  configureMultiblockExportFixture,
  createRawExportFixture,
  readJson,
  writeJson,
  writeNonUniformImage,
} from './test-export-fixture.mjs';

const quietLogger = Object.freeze({info() {}, warn() {}, error() {}});

async function createComparableRoots(t) {
  const parent = await mkdtemp(join(tmpdir(), 'mrt-deterministic-exports-test-'));
  t.after(() => rm(parent, {recursive: true, force: true}));
  const left = join(parent, 'left');
  const right = join(parent, 'right');
  await mkdir(left);
  await createRawExportFixture(left, {iconScale: 3, recipeScale: 2});
  await writeNonUniformImage(
    join(left, 'recipes', 'minecraft_crafting', 'recipe-0.png'),
    32,
  );
  const manifestPath = join(left, 'manifest.json');
  const manifest = await readJson(manifestPath);
  manifest.counts.recipes = 1;
  await writeJson(manifestPath, manifest);
  const categoriesPath = join(left, 'categories.json');
  const categories = await readJson(categoriesPath);
  categories.categories[0].count = 1;
  await writeJson(categoriesPath, categories);
  await writeJson(join(left, 'recipes', 'minecraft_crafting', 'recipes.json'), [
    {
      id: 'fixture:recipe',
      in: [[['minecraft:stone', 1]]],
      out: [[['minecraft:stone', 1]]],
      img: 'recipe-0.png',
      w: 16,
      h: 16,
    },
  ]);
  await writeJson(join(left, 'index.json'), {
    'minecraft:stone': {p: [[0, 0]], u: [[0, 0]]},
  });
  await configureMultiblockExportFixture(left, MULTIBLOCK_MADNESS_112_PROFILE);
  await writeFile(join(left, 'export-audit.bin'), Buffer.from('stable-audit'));
  await cp(left, right, {recursive: true, force: false, errorOnExist: true});
  const rightManifestPath = join(right, 'manifest.json');
  const rightManifest = await readJson(rightManifestPath);
  rightManifest.generatedAt = '2026-07-20T01:02:03.000Z';
  rightManifest.durationMs = 98_765;
  await writeJson(rightManifestPath, rightManifest);
  return {left, right};
}

test('accepts only volatile root-manifest drift and compares decoded-RGBA preview inventory', async t => {
  const {left, right} = await createComparableRoots(t);
  const result = await compareDeterministicExports(left, right, {
    profile: MULTIBLOCK_MADNESS_112_PROFILE,
    compareRecipeImageInventory: true,
    logger: quietLogger,
  });
  assert.equal(result.profile, MULTIBLOCK_MADNESS_112_PROFILE);
  assert.equal(result.files > 0, true);
  assert.equal(result.nonManifestFiles, result.files - 1);
  assert.deepEqual(result.recipeImageInventory, {
    format: 'mrt-recipe-image-inventory-v1',
    sha256: result.recipeImageInventory.sha256,
    entries: 1,
    previews: 1,
    missing: 0,
  });
  assert.match(result.recipeImageInventory.sha256, /^[a-f0-9]{64}$/);
});

test('reports a valid PNG whose exact encoded bytes differ', async t => {
  const {left, right} = await createComparableRoots(t);
  const pixels = Buffer.alloc(32 * 32 * 4, 255);
  pixels[0] = 200;
  pixels[1] = 40;
  pixels[2] = 80;
  await sharp(pixels, {raw: {width: 32, height: 32, channels: 4}})
    .png()
    .toFile(join(right, 'recipes', 'minecraft_crafting', 'recipe-0.png'));
  await assert.rejects(
    compareDeterministicExports(left, right, {
      profile: MULTIBLOCK_MADNESS_112_PROFILE,
      logger: quietLogger,
    }),
    /recipe-0\.png: bytes differ/,
  );
});

test('rejects path-inventory drift and nonvolatile manifest drift', async t => {
  const pathRoots = await createComparableRoots(t);
  await writeFile(join(pathRoots.right, 'right-only.txt'), 'unexpected\n');
  await assert.rejects(
    compareDeterministicExports(pathRoots.left, pathRoots.right, {
      profile: MULTIBLOCK_MADNESS_112_PROFILE,
      logger: quietLogger,
    }),
    /right-only\.txt: present only in the right export/,
  );

  const manifestRoots = await createComparableRoots(t);
  const manifestPath = join(manifestRoots.right, 'manifest.json');
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
  manifest.mods.minecraft = 'Not Minecraft';
  await writeJson(manifestPath, manifest);
  await assert.rejects(
    compareDeterministicExports(manifestRoots.left, manifestRoots.right, {
      profile: MULTIBLOCK_MADNESS_112_PROFILE,
      logger: quietLogger,
    }),
    /Root manifests differ.*manifest\.mods\.minecraft/s,
  );
});

test('refuses symlinks and hard links before comparison', async t => {
  if (process.platform === 'win32') {
    t.skip('Symlink creation requires an elevated Windows test environment.');
    return;
  }
  const symlinkRoots = await createComparableRoots(t);
  const outside = join(symlinkRoots.left, '..', 'outside.txt');
  await writeFile(outside, 'outside\n');
  await symlink(outside, join(symlinkRoots.left, 'linked.txt'));
  await assert.rejects(
    compareDeterministicExports(symlinkRoots.left, symlinkRoots.right, {
      profile: MULTIBLOCK_MADNESS_112_PROFILE,
      logger: quietLogger,
    }),
    /symlink or special filesystem entry/,
  );

  const hardlinkRoots = await createComparableRoots(t);
  await link(
    join(hardlinkRoots.left, 'export-audit.bin'),
    join(hardlinkRoots.left, 'export-audit-copy.bin'),
  );
  await assert.rejects(
    compareDeterministicExports(hardlinkRoots.left, hardlinkRoots.right, {
      profile: MULTIBLOCK_MADNESS_112_PROFILE,
      logger: quietLogger,
    }),
    /non-hard-linked regular file/,
  );
});

test('rejects mutation after the pre-validation inventory was captured', async t => {
  const {left, right} = await createComparableRoots(t);
  let mutated = false;
  const mutatingLogger = {
    info(message) {
      if (!mutated && message.includes('Securely hashing') && message.includes(left)) {
        mutated = true;
        writeFileSync(join(left, 'export-audit.bin'), 'mutated-audit');
      }
    },
    warn() {},
    error() {},
  };
  await assert.rejects(
    compareDeterministicExports(left, right, {
      profile: MULTIBLOCK_MADNESS_112_PROFILE,
      logger: mutatingLogger,
    }),
    /changed or is not a plain, non-hard-linked regular file/,
  );
  assert.equal(mutated, true);
});

test('requires a quality profile and two distinct roots', async () => {
  await assert.rejects(
    compareDeterministicExports('/tmp/left', '/tmp/right', {logger: quietLogger}),
    /requires --profile/,
  );
  await assert.rejects(
    compareDeterministicExports('/tmp/same', '/tmp/same', {
      profile: MULTIBLOCK_MADNESS_112_PROFILE,
      logger: quietLogger,
    }),
    /two distinct export roots/,
  );
});
