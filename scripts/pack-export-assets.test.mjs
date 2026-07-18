import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {access, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {promisify} from 'node:util';
import sharp from 'sharp';
import {
  createRawExportFixture,
  readJson,
  writeUniformVisibleImage,
} from './test-export-fixture.mjs';
import {validateExportData} from './validate-export-data.mjs';
import {
  RECIPE_IMAGE_INVENTORY_FORMAT,
  RECIPE_IMAGE_INVENTORY_SHA256_PATTERN,
} from './recipe-image-inventory.mjs';

const execFileAsync = promisify(execFile);

async function pathIsMissing(path) {
  try {
    await access(path);
    return false;
  } catch (error) {
    if (error?.code === 'ENOENT') return true;
    throw error;
  }
}

async function optimizeFixture(exportRoot) {
  await execFileAsync(process.execPath, [
    join(import.meta.dirname, 'optimize-export-assets.mjs'),
    '--root',
    exportRoot,
  ]);
}

async function packFixture(exportRoot, extraArguments = []) {
  return execFileAsync(process.execPath, [
    join(import.meta.dirname, 'pack-export-assets.mjs'),
    '--root',
    exportRoot,
    ...extraArguments,
  ]);
}

test('exact duplicate WebP assets share one coordinate and are written once', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-packer-dedup-test-'));
  try {
    const exportRoot = join(root, 'exports');
    await createRawExportFixture(exportRoot);
    await optimizeFixture(exportRoot);

    const iconBytes = await readFile(join(exportRoot, 'icons', 'stone.webp'));
    const categoryBytes = await readFile(
      join(exportRoot, 'recipes', 'minecraft_crafting', 'icon.webp'),
    );
    assert.deepEqual(categoryBytes, iconBytes, 'the fixture must exercise exact byte duplicates');

    const {stdout} = await packFixture(exportRoot);
    const itemCoordinate = (await readJson(join(exportRoot, 'items.json'))).items[0].icon;
    const categoryCoordinate = (await readJson(join(exportRoot, 'categories.json')))
      .categories[0].icon;
    const packBytes = await readFile(join(exportRoot, 'assets', 'pack-000.bin'));

    assert.equal(categoryCoordinate, itemCoordinate);
    assert.deepEqual(packBytes, iconBytes);
    assert.match(stdout, /2 WebP assets \(1 unique, 1 duplicate\)/);
    assert.match(stdout, new RegExp(`deduplication saved ${iconBytes.length} bytes`));
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('distinct WebP bytes retain distinct coordinates and pack storage', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-packer-distinct-test-'));
  try {
    const exportRoot = join(root, 'exports');
    await createRawExportFixture(exportRoot);
    await writeUniformVisibleImage(
      join(exportRoot, 'recipes', 'minecraft_crafting', 'icon.png'),
    );
    await optimizeFixture(exportRoot);

    const iconBytes = await readFile(join(exportRoot, 'icons', 'stone.webp'));
    const categoryBytes = await readFile(
      join(exportRoot, 'recipes', 'minecraft_crafting', 'icon.webp'),
    );
    assert.notDeepEqual(categoryBytes, iconBytes);

    const {stdout} = await packFixture(exportRoot);
    const itemCoordinate = (await readJson(join(exportRoot, 'items.json'))).items[0].icon;
    const categoryCoordinate = (await readJson(join(exportRoot, 'categories.json')))
      .categories[0].icon;
    const packBytes = await readFile(join(exportRoot, 'assets', 'pack-000.bin'));

    assert.notEqual(categoryCoordinate, itemCoordinate);
    assert.deepEqual(packBytes, Buffer.concat([iconBytes, categoryBytes]));
    assert.match(stdout, /2 WebP assets \(2 unique, 0 duplicate\)/);
    assert.match(stdout, /deduplication saved 0 bytes/);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('explicit structured-recipe policy omits only validated recipe screenshots', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-packer-structured-test-'));
  try {
    const exportRoot = join(root, 'exports');
    const categoryRoot = join(exportRoot, 'recipes', 'minecraft_crafting');
    await createRawExportFixture(exportRoot);
    await writeUniformVisibleImage(join(categoryRoot, 'r0.png'));
    await writeFile(
      join(categoryRoot, 'recipes.json'),
      `${JSON.stringify([
        {
          id: 'minecraft:test',
          img: 'r0.png',
          w: 16,
          h: 16,
          in: [[['minecraft:stone', 1]]],
          out: [[['minecraft:stone', 1]]],
        },
      ])}\n`,
    );
    const rawManifest = await readJson(join(exportRoot, 'manifest.json'));
    rawManifest.counts.recipes = 1;
    await writeFile(join(exportRoot, 'manifest.json'), `${JSON.stringify(rawManifest)}\n`);
    const rawCategories = await readJson(join(exportRoot, 'categories.json'));
    rawCategories.categories[0].count = 1;
    await writeFile(join(exportRoot, 'categories.json'), `${JSON.stringify(rawCategories)}\n`);
    await writeFile(
      join(exportRoot, 'index.json'),
      `${JSON.stringify({'minecraft:stone': {p: [[0, 0]], u: [[0, 0]]}})}\n`,
    );
    await optimizeFixture(exportRoot);

    const omittedBytes = (await readFile(join(categoryRoot, 'r0.webp'))).length;
    const {stderr} = await packFixture(exportRoot, ['--omit-recipe-images']);
    const manifest = await readJson(join(exportRoot, 'manifest.json'));
    const recipe = (await readJson(join(categoryRoot, 'recipes.json')))[0];
    const itemCoordinate = (await readJson(join(exportRoot, 'items.json'))).items[0].icon;
    const categoryCoordinate = (await readJson(join(exportRoot, 'categories.json')))
      .categories[0].icon;

    assert.deepEqual(manifest.web.recipeImages, {
      mode: 'omitted',
      reason: 'hosting-archive-budget',
      references: 1,
      files: 1,
      bytes: omittedBytes,
      inventory: {
        format: RECIPE_IMAGE_INVENTORY_FORMAT,
        sha256: manifest.web.recipeImages.inventory.sha256,
        entries: 1,
        previews: 1,
        missing: 0,
      },
    });
    assert.match(
      manifest.web.recipeImages.inventory.sha256,
      RECIPE_IMAGE_INVENTORY_SHA256_PATTERN,
    );
    assert.equal('img' in recipe, false);
    assert.equal('w' in recipe, false);
    assert.equal('h' in recipe, false);
    assert.deepEqual(recipe.in, [[['minecraft:stone', 1]]]);
    assert.deepEqual(recipe.out, [[['minecraft:stone', 1]]]);
    assert.equal(categoryCoordinate, itemCoordinate, 'category and item icons must remain packed');
    assert.equal(await pathIsMissing(join(categoryRoot, 'r0.webp')), true);
    assert.match(stderr, /Publication policy omitted 1 composite recipe-image reference/);

    const validation = await validateExportData(exportRoot, {
      assetMode: 'packed',
      requirePublicationId: true,
      verifyPublicationId: true,
    });
    assert.equal(validation.recipes, 1);
    assert.equal(validation.imageReferences, 1);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('packing rejects animated WebP inputs instead of hashing only frame zero', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-packer-animated-test-'));
  try {
    const exportRoot = join(root, 'exports');
    await createRawExportFixture(exportRoot);
    await optimizeFixture(exportRoot);
    const red = await sharp({
      create: {width: 2, height: 2, channels: 4, background: '#ff0000ff'},
    })
      .png()
      .toBuffer();
    const blue = await sharp({
      create: {width: 2, height: 2, channels: 4, background: '#0000ffff'},
    })
      .png()
      .toBuffer();
    await sharp([red, blue], {join: {animated: true}})
      .webp({lossless: true, delay: [50, 50]})
      .toFile(join(exportRoot, 'icons', 'stone.webp'));

    await assert.rejects(
      packFixture(exportRoot),
      error => {
        assert.match(error.stderr, /animation\/pages; inventory hashing requires exactly one image page/);
        return true;
      },
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('packing rollback restores raw images and metadata after a mid-publication failure', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-packer-rollback-test-'));
  try {
    const exportRoot = join(root, 'exports');
    await createRawExportFixture(exportRoot);
    await optimizeFixture(exportRoot);

    // The raw validator permits unrelated files, but the publication stage requires
    // `data` to be a directory. This fails after packs, icons, and recipes have moved.
    await writeFile(join(exportRoot, 'data'), 'intentional transaction conflict\n');
    await assert.rejects(
      packFixture(exportRoot),
      error => {
        assert.match(error.stderr, /restoring the raw export/);
        assert.match(error.stderr, /sharded data is file; expected directory/);
        return true;
      },
    );

    assert.equal(await pathIsMissing(join(exportRoot, 'assets')), true);
    assert.equal(await pathIsMissing(join(exportRoot, 'icons', 'stone.webp')), false);
    assert.equal(
      await pathIsMissing(join(exportRoot, 'recipes', 'minecraft_crafting', 'icon.webp')),
      false,
    );
    assert.equal(await readFile(join(exportRoot, 'data'), 'utf8'), 'intentional transaction conflict\n');
    assert.deepEqual(
      await readJson(join(exportRoot, 'recipes', 'minecraft_crafting', 'recipes.json')),
      [],
    );
    assert.equal((await readJson(join(exportRoot, 'items.json'))).items[0].icon, 'icons/stone.webp');
    assert.equal((await validateExportData(exportRoot, {assetMode: 'raw'})).imageReferences, 2);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
