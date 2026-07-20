import {mkdir, readFile, writeFile} from 'node:fs/promises';
import {join} from 'node:path';
import sharp from 'sharp';
import {
  GTNH_1710_PROFILE,
  MULTIBLOCK_MADNESS_112_PROFILE,
  MULTIBLOCK_MADNESS_2_118_PROFILE,
  qualityProfileRequirementsFor,
} from './export-quality-policy.mjs';
import {
  EXPORTER_BUILD_ALGORITHM,
  EXPORTER_BUILD_EXPORT_PATH,
  EXPORTER_BUILD_FORMAT,
  canonicalExporterBuildIdentityBytes,
} from './exporter-artifact-provenance.mjs';

const SYNTHETIC_EXPORTER_ID_BY_PROFILE = new Map([
  [GTNH_1710_PROFILE, 'forge-nei-gtnh-1.7.10'],
  [MULTIBLOCK_MADNESS_112_PROFILE, 'forge-hei-1.12.2'],
  [MULTIBLOCK_MADNESS_2_118_PROFILE, 'forge-rei-1.18.2'],
]);

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

export async function writeSyntheticExporterBuildIdentity(root, profile) {
  const requirements = qualityProfileRequirementsFor(profile);
  const exporterId = SYNTHETIC_EXPORTER_ID_BY_PROFILE.get(profile);
  if (!requirements?.requiresExporterBuildIdentity || exporterId === undefined) {
    throw new Error(
      `No synthetic exporter build identity is pinned for quality profile ${String(profile)}.`,
    );
  }
  const identity = {
    format: EXPORTER_BUILD_FORMAT,
    exporterId,
    minecraftVersion: requirements.minecraft,
    algorithm: EXPORTER_BUILD_ALGORITHM,
    payloadSha256: '0'.repeat(64),
  };
  await writeFile(
    join(root, EXPORTER_BUILD_EXPORT_PATH),
    canonicalExporterBuildIdentityBytes(identity),
  );
  return identity;
}

export async function configureMultiblockExportFixture(root, profile) {
  if (
    profile !== MULTIBLOCK_MADNESS_112_PROFILE &&
    profile !== MULTIBLOCK_MADNESS_2_118_PROFILE
  ) {
    throw new Error(`Unsupported Multiblock Madness fixture profile: ${String(profile)}.`);
  }
  const requirements = qualityProfileRequirementsFor(profile);
  const manifestPath = join(root, 'manifest.json');
  const manifest = await readJson(manifestPath);
  manifest.minecraft = requirements.minecraft;
  manifest.pack = {...requirements.packIdentity};
  if (profile === MULTIBLOCK_MADNESS_112_PROFILE) {
    manifest.diagnostics.warningEvents = 0;
    manifest.diagnostics.warningEventsOmitted = 0;
    await writeJson(join(root, 'warnings.json'), []);
  } else if (profile === MULTIBLOCK_MADNESS_2_118_PROFILE) {
    manifest.counts.nativeIconCorrections = 0;
    manifest.diagnostics.nativeIconCorrections = 0;
    manifest.diagnostics.transparentIcons = 0;
  }
  await writeJson(manifestPath, manifest);
  await writeSyntheticExporterBuildIdentity(root, profile);
  return manifest;
}

export async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}
