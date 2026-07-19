import {mkdir, readFile, writeFile} from 'node:fs/promises';
import {join} from 'node:path';
import sharp from 'sharp';

export async function writeJson(path, value) {
  await writeFile(path, `${JSON.stringify(value)}\n`);
}

export async function writeNonUniformImage(path, size = 16) {
  const pixels = Buffer.alloc(size * size * 4, 255);
  pixels[0] = 0;
  pixels[1] = 80;
  pixels[2] = 160;
  await sharp(pixels, {raw: {width: size, height: size, channels: 4}}).png().toFile(path);
}

export async function writeUniformVisibleImage(path) {
  const pixels = Buffer.alloc(16 * 16 * 4);
  for (let offset = 0; offset < pixels.length; offset += 4) {
    pixels[offset] = 24;
    pixels[offset + 1] = 48;
    pixels[offset + 2] = 72;
    pixels[offset + 3] = 10;
  }
  await sharp(pixels, {raw: {width: 16, height: 16, channels: 4}}).png().toFile(path);
}

export async function writeTransparentImage(path) {
  const pixels = Buffer.alloc(16 * 16 * 4);
  await sharp(pixels, {raw: {width: 16, height: 16, channels: 4}}).png().toFile(path);
}

export async function createRawExportFixture(
  root,
  {iconScale = 1, recipeScale = 1} = {},
) {
  await mkdir(join(root, 'icons'), {recursive: true});
  await mkdir(join(root, 'recipes', 'minecraft_crafting'), {recursive: true});
  await writeNonUniformImage(join(root, 'icons', 'stone.png'), 16 * iconScale);
  await writeNonUniformImage(
    join(root, 'recipes', 'minecraft_crafting', 'icon.png'),
    16 * iconScale,
  );
  await writeJson(join(root, 'manifest.json'), {
    format: 1,
    generatedAt: '2026-07-18T00:00:00Z',
    durationMs: 1,
    aborted: false,
    minecraft: '1.12.2',
    settings: {iconScale, recipeScale, mobCanvas: 256},
    counts: {items: 1, recipes: 0, categories: 1, mobs: 0, blockDrops: 0, failures: 0},
    diagnostics: {failureEvents: 0, failureEventsOmitted: 0},
    mods: {minecraft: 'Minecraft'},
  });
  await writeJson(join(root, 'items.json'), {
    items: [
      {
        k: 'minecraft:stone',
        id: 'minecraft:stone',
        n: 'Stone',
        m: 'minecraft',
        icon: 'icons/stone.png',
      },
    ],
  });
  await writeJson(join(root, 'categories.json'), {
    categories: [
      {
        id: 'minecraft.crafting',
        title: 'Crafting',
        dir: 'recipes/minecraft_crafting',
        count: 0,
        icon: 'recipes/minecraft_crafting/icon.png',
        catalysts: [],
      },
    ],
  });
  await writeJson(join(root, 'recipes', 'minecraft_crafting', 'recipes.json'), []);
  await writeJson(join(root, 'index.json'), {'minecraft:stone': {}});
  await writeJson(join(root, 'mobs.json'), {mobs: []});
  await writeJson(join(root, 'blockdrops.json'), {blocks: {}});
  await writeJson(join(root, 'failures.json'), []);
  return root;
}

export async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}
