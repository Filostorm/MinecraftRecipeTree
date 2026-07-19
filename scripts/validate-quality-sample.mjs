import {createHash} from 'node:crypto';
import {lstat, readFile} from 'node:fs/promises';
import {performance} from 'node:perf_hooks';
import {join, posix, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {isDeepStrictEqual} from 'node:util';
import sharp from 'sharp';
import {collectFiles} from './export-data-utils.mjs';
import {decodedRgbaSha256} from './recipe-image-inventory.mjs';

const MINECRAFT_VERSION = '1.12.2';
const LOGICAL_ITEM_GRID = 16;
const ICON_SCALE = 3;
const RECIPE_SCALE = 2;
const ICON_WEBP_EFFORT = 6;
const RECIPE_WEBP_EFFORT = 4;
const MAX_IMAGE_PIXELS = 16_384 * 16_384;
const ICON_P95_BUDGET_BYTES = 16 * 1024;
const COLD_MODAL_BUDGET_BYTES = 1024 * 1024;
const RECIPE_FILL_RGBA = Object.freeze([198, 198, 198, 255]);
const RECIPE_CHROME_MIN_MATCH_RATIO = 0.98;
const WORLD_STARTUP_POLICY = 'dimension-0-plus-should-load-spawn';
const MEATBALLCRAFT_WORLD_COUNTS = Object.freeze({
  originalDimensions: 93,
  selectedDimensions: 4,
  skippedDimensions: 89,
});
const RECIPE_INPUT_GRID = Object.freeze({
  x: 4,
  y: 4,
  columns: 3,
  rows: 3,
  slotSize: 18,
});

function includesInputSlotPerimeter(x, y) {
  const relativeX = x - RECIPE_INPUT_GRID.x;
  const relativeY = y - RECIPE_INPUT_GRID.y;
  if (
    relativeX < 0 ||
    relativeY < 0 ||
    relativeX >= RECIPE_INPUT_GRID.columns * RECIPE_INPUT_GRID.slotSize ||
    relativeY >= RECIPE_INPUT_GRID.rows * RECIPE_INPUT_GRID.slotSize
  ) {
    return false;
  }
  const slotX = relativeX % RECIPE_INPUT_GRID.slotSize;
  const slotY = relativeY % RECIPE_INPUT_GRID.slotSize;
  return (
    slotX === 0 ||
    slotY === 0 ||
    slotX === RECIPE_INPUT_GRID.slotSize - 1 ||
    slotY === RECIPE_INPUT_GRID.slotSize - 1
  );
}

const RECIPE_CHROME_REGIONS = Object.freeze([
  Object.freeze({
    label: 'outer JEI frame',
    includes: (x, y, width, height) =>
      x < 4 || y < 4 || x >= width - 4 || y >= height - 4,
  }),
  Object.freeze({
    label: 'all nine 3x3 input-slot frame perimeters',
    includes: includesInputSlotPerimeter,
  }),
  Object.freeze({
    label: 'JEI crafting arrow',
    includes: (x, y) => x >= 62 && x < 84 && y >= 20 && y < 42,
  }),
  Object.freeze({
    label: 'JEI output-slot frame',
    includes: (x, y) =>
      x >= 94 &&
      x < 120 &&
      y >= 18 &&
      y < 44 &&
      (x < 97 || x >= 117 || y < 21 || y >= 41),
  }),
]);

const MACHINE_FRAME_KEY = 'item|thermalexpansion:frame:0';
const MACHINE_FRAME_ICON = 'icons/item/thermalexpansion/frame_d5baf740.png';

const TARGETS = Object.freeze([
  Object.freeze({
    label: 'Crafting Machine Frame',
    categoryId: 'minecraft.crafting',
    categoryTitle: 'Crafting',
    categoryDirectory: 'recipes/minecraft.crafting',
    baselineImage: 'recipes/minecraft.crafting/r31319.png',
    baselineSourceIndex: 31_319,
    sampleImage: 'r0.png',
    semantic: Object.freeze({
      id: 'crafttweaker:ct_shaped-557966710',
      in: [
        [["item|twilightforest:fiery_ingot", 1]],
        [["item|minecraft:glass", 1]],
        [["item|twilightforest:fiery_ingot", 1]],
        [["item|minecraft:glass", 1]],
        [["item|thermalfoundation:material:257", 1]],
        [["item|minecraft:glass", 1]],
        [["item|twilightforest:fiery_ingot", 1]],
        [["item|minecraft:glass", 1]],
        [["item|twilightforest:fiery_ingot", 1]],
      ],
      out: [[[MACHINE_FRAME_KEY, 4]]],
    }),
  }),
  Object.freeze({
    label: 'Basic Crafting Machine Frame',
    categoryId: 'extendedcrafting:table_crafting_3x3',
    categoryTitle: 'Basic Crafting',
    categoryDirectory: 'recipes/extendedcrafting_table_crafting_3x3',
    baselineImage: 'recipes/extendedcrafting_table_crafting_3x3/r131.png',
    baselineSourceIndex: 131,
    sampleImage: 'r0.png',
    semantic: Object.freeze({
      in: [
        [["item|minecraft:iron_ingot", 1]],
        [["item|minecraft:glass", 1]],
        [["item|minecraft:iron_ingot", 1]],
        [["item|minecraft:glass", 1]],
        [["item|thermalfoundation:material:257", 1]],
        [["item|minecraft:glass", 1]],
        [["item|minecraft:iron_ingot", 1]],
        [["item|minecraft:glass", 1]],
        [["item|minecraft:iron_ingot", 1]],
      ],
      out: [[[MACHINE_FRAME_KEY, 1]]],
    }),
  }),
]);

export const DEFAULT_BASELINE_DIGESTS = Object.freeze({
  [MACHINE_FRAME_ICON]: '4e551ba2074665e72b34b055efcd5c2784bab32469c1740ca5044c2ad1a618ed',
  [TARGETS[0].baselineImage]: '9e20ba1cdd5590f7c66afe317a6ed2101c5c2eefedae6c26984bf7f15e2fc163',
  [TARGETS[1].baselineImage]: 'fd02b715d1b00e7f169b2d16c079179f213d6a61cac584c6464a2a7ef563a049',
});

sharp.cache(false);
sharp.concurrency(2);

function fail(message) {
  throw new Error(`MeatballCraft quality sample failed: ${message}`);
}

function expect(condition, message) {
  if (!condition) fail(message);
}

function expectEqual(actual, expected, label) {
  if (!isDeepStrictEqual(actual, expected)) {
    fail(
      `${label} did not match the audited contract. Expected ${JSON.stringify(expected)}, ` +
        `received ${JSON.stringify(actual)}.`,
    );
  }
}

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function safeRelativePath(value) {
  return (
    typeof value === 'string' &&
    value.length > 0 &&
    !value.startsWith('/') &&
    !value.includes('\\') &&
    posix.normalize(value) === value &&
    value !== '..' &&
    !value.startsWith('../')
  );
}

function relativeKey(root, path) {
  return relative(root, path).split(sep).join('/');
}

async function requirePlainDirectory(path, label) {
  let info;
  try {
    info = await lstat(path);
  } catch (error) {
    fail(`${label} is unavailable at ${path}: ${error.message}`);
  }
  expect(!info.isSymbolicLink() && info.isDirectory(), `${label} must be a plain directory: ${path}`);
}

async function requirePlainFile(root, relativePath, label) {
  expect(safeRelativePath(relativePath), `${label} has an unsafe relative path: ${relativePath}`);
  const segments = relativePath.split('/');
  let parent = root;
  for (const segment of segments.slice(0, -1)) {
    parent = join(parent, segment);
    let parentInfo;
    try {
      parentInfo = await lstat(parent);
    } catch (error) {
      fail(`${label} parent directory is unavailable at ${parent}: ${error.message}`);
    }
    expect(
      !parentInfo.isSymbolicLink() && parentInfo.isDirectory(),
      `${label} parent must be a plain directory: ${parent}`,
    );
  }
  const path = join(parent, segments.at(-1));
  let info;
  try {
    info = await lstat(path);
  } catch (error) {
    fail(`${label} is unavailable at ${path}: ${error.message}`);
  }
  expect(!info.isSymbolicLink() && info.isFile(), `${label} must be a plain file: ${path}`);
  return path;
}

async function readJson(root, relativePath, label = relativePath) {
  const path = await requirePlainFile(root, relativePath, label);
  let source;
  try {
    source = await readFile(path, 'utf8');
  } catch (error) {
    fail(`${label} could not be read: ${error.message}`);
  }
  try {
    return JSON.parse(source);
  } catch (error) {
    fail(`${label} is not valid JSON: ${error.message}`);
  }
}

function canonicalizeTransparentRgb(pixels) {
  let canonical = pixels;
  for (let offset = 0; offset < pixels.length; offset += 4) {
    if (
      pixels[offset + 3] === 0 &&
      (pixels[offset] !== 0 || pixels[offset + 1] !== 0 || pixels[offset + 2] !== 0)
    ) {
      canonical = Buffer.from(pixels);
      for (let inner = offset; inner < canonical.length; inner += 4) {
        if (canonical[inner + 3] === 0) {
          canonical[inner] = 0;
          canonical[inner + 1] = 0;
          canonical[inner + 2] = 0;
        }
      }
      break;
    }
  }
  return canonical;
}

async function decodePng(root, relativePath, label, memory) {
  const path = await requirePlainFile(root, relativePath, label);
  const source = await readFile(path);
  let metadata;
  let decoded;
  try {
    const image = sharp(source, {failOn: 'error', limitInputPixels: MAX_IMAGE_PIXELS});
    metadata = await image.metadata();
    decoded = await image
      .toColourspace('srgb')
      .ensureAlpha()
      .raw()
      .toBuffer({resolveWithObject: true});
  } catch (error) {
    fail(`${label} could not be decoded as a PNG: ${error.message}`);
  }
  expect(metadata.format === 'png', `${label} must be PNG, not ${String(metadata.format)}.`);
  expect((metadata.pages ?? 1) === 1, `${label} must contain exactly one image page.`);
  expect(decoded.info.channels === 4, `${label} did not normalize to RGBA.`);
  memory.observe();
  const pixels = canonicalizeTransparentRgb(decoded.data);
  return {
    label,
    relativePath,
    source,
    pngBytes: source.length,
    width: decoded.info.width,
    height: decoded.info.height,
    pixels,
    rgbaSha256: decodedRgbaSha256(decoded.info.width, decoded.info.height, pixels),
  };
}

function requireDimensions(image, width, height) {
  expect(
    image.width === width && image.height === height,
    `${image.label} decoded as ${image.width}x${image.height}; expected ${width}x${height}.`,
  );
}

async function nearestNeighborPixels(source, targetWidth, targetHeight, memory) {
  const pixels = await sharp(source.pixels, {
    raw: {width: source.width, height: source.height, channels: 4},
  })
    .resize(targetWidth, targetHeight, {kernel: sharp.kernel.nearest})
    .raw()
    .toBuffer();
  memory.observe();
  return canonicalizeTransparentRgb(pixels);
}

function differentPixelCount(left, right) {
  expect(left.length === right.length, 'internal pixel comparison received unequal buffer lengths.');
  let pixels = 0;
  for (let offset = 0; offset < left.length; offset += 4) {
    if (
      left[offset] !== right[offset] ||
      left[offset + 1] !== right[offset + 1] ||
      left[offset + 2] !== right[offset + 2] ||
      left[offset + 3] !== right[offset + 3]
    ) {
      pixels += 1;
    }
  }
  return pixels;
}

export function withinLogicalCellRefinement(pixels, width, height, scale) {
  expect(Number.isSafeInteger(scale) && scale > 1, 'refinement scale must be an integer above one.');
  expect(width % scale === 0 && height % scale === 0, 'physical image is not aligned to its logical grid.');
  expect(pixels.length === width * height * 4, 'refinement input is not width x height RGBA.');
  let refinedCells = 0;
  const logicalWidth = width / scale;
  const logicalHeight = height / scale;
  for (let logicalY = 0; logicalY < logicalHeight; logicalY += 1) {
    for (let logicalX = 0; logicalX < logicalWidth; logicalX += 1) {
      const first = ((logicalY * scale) * width + logicalX * scale) * 4;
      let refined = false;
      for (let innerY = 0; innerY < scale && !refined; innerY += 1) {
        for (let innerX = 0; innerX < scale; innerX += 1) {
          const offset = ((logicalY * scale + innerY) * width + logicalX * scale + innerX) * 4;
          if (
            pixels[offset] !== pixels[first] ||
            pixels[offset + 1] !== pixels[first + 1] ||
            pixels[offset + 2] !== pixels[first + 2] ||
            pixels[offset + 3] !== pixels[first + 3]
          ) {
            refined = true;
            break;
          }
        }
      }
      if (refined) refinedCells += 1;
    }
  }
  return {refinedCells, logicalCells: logicalWidth * logicalHeight};
}

async function requireGenuineRerender(baseline, sample, scale, memory) {
  requireDimensions(sample, baseline.width * scale, baseline.height * scale);
  const nearest = await nearestNeighborPixels(baseline, sample.width, sample.height, memory);
  const differingPixels = differentPixelCount(nearest, sample.pixels);
  expect(
    differingPixels > 0,
    `${sample.label} is only a nearest-neighbor ${scale}x enlargement of the low-resolution baseline.`,
  );
  const refinement = withinLogicalCellRefinement(sample.pixels, sample.width, sample.height, scale);
  expect(
    refinement.refinedCells > 0,
    `${sample.label} contains no sub-cell detail within its ${scale}x logical pixel cells.`,
  );
  return {
    label: sample.label,
    scale,
    differingPixels,
    totalPixels: sample.width * sample.height,
    differingPercent: Number(((differingPixels / (sample.width * sample.height)) * 100).toFixed(3)),
    ...refinement,
  };
}

function pixelEquals(pixels, offset, rgba) {
  return (
    pixels[offset] === rgba[0] &&
    pixels[offset + 1] === rgba[1] &&
    pixels[offset + 2] === rgba[2] &&
    pixels[offset + 3] === rgba[3]
  );
}

/**
 * Proves that the integer-scaled render retained HEI's static layout chrome. Item sprites alone
 * are not sufficient: a depth-buffer regression can leave every ingredient visible while
 * silently occluding the frame, slot background, and arrow drawn at GUI z=0.
 */
export function requireRecipeChrome(baseline, sample, scale) {
  requireDimensions(sample, baseline.width * scale, baseline.height * scale);
  const regions = [];
  for (const region of RECIPE_CHROME_REGIONS) {
    let auditedLogicalPixels = 0;
    let matchingPhysicalPixels = 0;
    let auditedPhysicalPixels = 0;
    for (let y = 0; y < baseline.height; y += 1) {
      for (let x = 0; x < baseline.width; x += 1) {
        if (!region.includes(x, y, baseline.width, baseline.height)) continue;
        const baselineOffset = (y * baseline.width + x) * 4;
        if (pixelEquals(baseline.pixels, baselineOffset, RECIPE_FILL_RGBA)) continue;
        auditedLogicalPixels += 1;
        for (let innerY = 0; innerY < scale; innerY += 1) {
          for (let innerX = 0; innerX < scale; innerX += 1) {
            const sampleOffset =
              ((y * scale + innerY) * sample.width + x * scale + innerX) * 4;
            auditedPhysicalPixels += 1;
            if (
              sample.pixels[sampleOffset] === baseline.pixels[baselineOffset] &&
              sample.pixels[sampleOffset + 1] === baseline.pixels[baselineOffset + 1] &&
              sample.pixels[sampleOffset + 2] === baseline.pixels[baselineOffset + 2] &&
              sample.pixels[sampleOffset + 3] === baseline.pixels[baselineOffset + 3]
            ) {
              matchingPhysicalPixels += 1;
            }
          }
        }
      }
    }
    expect(
      auditedLogicalPixels >= 32,
      `${baseline.label} ${region.label} has too few audited non-fill pixels (${auditedLogicalPixels}).`,
    );
    const matchRatio = matchingPhysicalPixels / auditedPhysicalPixels;
    expect(
      matchRatio >= RECIPE_CHROME_MIN_MATCH_RATIO,
      `${sample.label} retained only ${(matchRatio * 100).toFixed(2)}% of the audited ` +
        `${region.label} pixels; expected at least ` +
        `${(RECIPE_CHROME_MIN_MATCH_RATIO * 100).toFixed(0)}%. The JEI layout chrome is missing or corrupted.`,
    );
    regions.push({
      label: region.label,
      auditedLogicalPixels,
      auditedPhysicalPixels,
      matchingPhysicalPixels,
      matchRatio: Number(matchRatio.toFixed(4)),
    });
  }
  return {label: sample.label, scale, regions};
}

async function encodeLosslessWebp(image, effort, kind, memory) {
  const started = performance.now();
  let webp;
  try {
    webp = await sharp(image.pixels, {
      raw: {width: image.width, height: image.height, channels: 4},
    })
      .webp({lossless: true, effort})
      .toBuffer();
  } catch (error) {
    fail(`${image.label} could not be encoded as lossless WebP: ${error.message}`);
  }
  const encodeMs = performance.now() - started;
  let metadata;
  let decoded;
  try {
    const roundTrip = sharp(webp, {failOn: 'error', limitInputPixels: MAX_IMAGE_PIXELS});
    metadata = await roundTrip.metadata();
    decoded = await roundTrip
      .toColourspace('srgb')
      .ensureAlpha()
      .raw()
      .toBuffer({resolveWithObject: true});
  } catch (error) {
    fail(`${image.label} lossless WebP could not be decoded: ${error.message}`);
  }
  memory.observe();
  expect(metadata.format === 'webp', `${image.label} did not produce a WebP payload.`);
  expect(
    decoded.info.width === image.width &&
      decoded.info.height === image.height &&
      decoded.info.channels === 4,
    `${image.label} WebP round trip changed its dimensions or channel count.`,
  );
  const roundTripPixels = canonicalizeTransparentRgb(decoded.data);
  expect(
    image.pixels.equals(roundTripPixels),
    `${image.label} lossless WebP round trip changed canonical RGBA pixels.`,
  );
  return {
    kind,
    path: image.relativePath,
    width: image.width,
    height: image.height,
    pngBytes: image.pngBytes,
    losslessWebpBytes: webp.length,
    webpToPngRatio: Number((webp.length / image.pngBytes).toFixed(4)),
    webpEffort: effort,
    encodeMs: Number(encodeMs.toFixed(3)),
    rgbaSha256: image.rgbaSha256,
  };
}

function recipeSemantics(recipe) {
  const semantics = {};
  for (const key of ['id', 'err', 'in', 'out', 'cat']) {
    if (recipe[key] !== undefined) semantics[key] = recipe[key];
  }
  return semantics;
}

function percentile95(values) {
  expect(values.length > 0, 'cannot calculate an icon p95 from an empty sample.');
  const ordered = [...values].sort((left, right) => left - right);
  return ordered[Math.ceil(ordered.length * 0.95) - 1];
}

function sum(values) {
  return values.reduce((total, value) => total + value, 0);
}

function createMemoryTracker() {
  const start = process.memoryUsage();
  let peakRss = start.rss;
  let peakHeapUsed = start.heapUsed;
  return {
    observe() {
      const current = process.memoryUsage();
      peakRss = Math.max(peakRss, current.rss);
      peakHeapUsed = Math.max(peakHeapUsed, current.heapUsed);
    },
    report() {
      this.observe();
      const end = process.memoryUsage();
      return {
        startRssBytes: start.rss,
        endRssBytes: end.rss,
        peakObservedRssBytes: peakRss,
        peakObservedHeapUsedBytes: peakHeapUsed,
        rssDeltaBytes: end.rss - start.rss,
      };
    },
  };
}

async function validateManifest(root, sample, requireWorldStartupOptimization = false) {
  const manifest = await readJson(root, 'manifest.json', `${sample ? 'sample' : 'baseline'} manifest`);
  expect(isRecord(manifest), `${sample ? 'sample' : 'baseline'} manifest must be an object.`);
  expect(manifest.format === 1, 'manifest.format must be 1.');
  expect(
    typeof manifest.generatedAt === 'string' && Number.isFinite(Date.parse(manifest.generatedAt)),
    'manifest.generatedAt must be a parseable timestamp.',
  );
  expect(
    typeof manifest.durationMs === 'number' &&
      Number.isFinite(manifest.durationMs) &&
      manifest.durationMs >= 0,
    'manifest.durationMs must be finite and non-negative.',
  );
  expect(manifest.minecraft === MINECRAFT_VERSION, `manifest.minecraft must be ${MINECRAFT_VERSION}.`);
  expect(manifest.aborted === false, 'manifest.aborted must be false.');
  expect(isRecord(manifest.settings), 'manifest.settings must be an object.');
  if (sample) {
    expect(manifest.settings.iconScale === ICON_SCALE, `sample iconScale must be ${ICON_SCALE}.`);
    expect(manifest.settings.recipeScale === RECIPE_SCALE, `sample recipeScale must be ${RECIPE_SCALE}.`);
    expect(manifest.settings.mobCanvas === 256, 'sample mobCanvas must be 256.');
    const worldStartup = manifest.settings.worldStartupOptimization;
    expect(
      isRecord(worldStartup),
      'sample settings.worldStartupOptimization must be an object.',
    );
    expect(
      worldStartup.policy === WORLD_STARTUP_POLICY,
      `sample world-startup policy must be ${WORLD_STARTUP_POLICY}.`,
    );
    if (requireWorldStartupOptimization) {
      expect(
        worldStartup.enabled === true && worldStartup.applied === true,
        'sample must enable and apply the fail-closed world-startup optimization.',
      );
      for (const [name, expected] of Object.entries(MEATBALLCRAFT_WORLD_COUNTS)) {
        expect(
          worldStartup[name] === expected,
          `sample world-startup ${name} must be ${expected}; received ${String(worldStartup[name])}.`,
        );
      }
    } else {
      expect(
        worldStartup.enabled === false && worldStartup.applied === false,
        'control sample must explicitly report world-startup optimization enabled=false and applied=false.',
      );
      for (const name of Object.keys(MEATBALLCRAFT_WORLD_COUNTS)) {
        expect(
          worldStartup[name] === undefined,
          `control sample must not report ${name} when the optimization was not applied.`,
        );
      }
    }
    expectEqual(
      manifest.qualitySample,
      {
        enabled: true,
        recipeTargets: 2,
        selectorCounts: {recipeId: 1, sourceIndex: 1},
      },
      'manifest.qualitySample',
    );
    expect(isRecord(manifest.counts), 'sample manifest.counts must be an object.');
    for (const [name, expected] of Object.entries({recipes: 2, categories: 2, mobs: 0, blockDrops: 0, failures: 0})) {
      expect(manifest.counts[name] === expected, `sample manifest.counts.${name} must be ${expected}.`);
    }
    expect(manifest.diagnostics?.failureEvents === 0, 'sample diagnostics.failureEvents must be zero.');
    expect(manifest.diagnostics?.failureEventsOmitted === 0, 'sample diagnostics.failureEventsOmitted must be zero.');
  } else {
    expect(manifest.settings.iconScale === 1, 'baseline iconScale must be 1.');
    expect(manifest.settings.recipeScale === 1, 'baseline recipeScale must be 1.');
    expect(manifest.settings.mobCanvas === 256, 'baseline mobCanvas must be 256.');
  }
  return manifest;
}

async function validateBaselineArtifacts(root, expectedDigests, memory) {
  const definitions = [
    {path: MACHINE_FRAME_ICON, label: 'baseline Machine Frame icon', width: 16, height: 16},
    ...TARGETS.map(target => ({
      path: target.baselineImage,
      label: `baseline ${target.label} preview`,
      width: 124,
      height: 62,
    })),
  ];
  const images = new Map();
  for (const definition of definitions) {
    const image = await decodePng(root, definition.path, definition.label, memory);
    requireDimensions(image, definition.width, definition.height);
    const digest = createHash('sha256').update(image.source).digest('hex');
    expect(
      digest === expectedDigests[definition.path],
      `${definition.label} SHA-256 ${digest} does not match audited baseline ` +
        `${String(expectedDigests[definition.path])}.`,
    );
    images.set(definition.path, image);
  }
  return images;
}

async function validateSampleDocuments(root, manifest) {
  const itemsDocument = await readJson(root, 'items.json');
  const categoriesDocument = await readJson(root, 'categories.json');
  const failures = await readJson(root, 'failures.json');
  const mobs = await readJson(root, 'mobs.json');
  const blockDrops = await readJson(root, 'blockdrops.json');
  const reverseIndex = await readJson(root, 'index.json');

  expect(isRecord(itemsDocument) && Array.isArray(itemsDocument.items), 'items.json must contain an items array.');
  expect(
    manifest.counts.items === itemsDocument.items.length,
    `manifest.counts.items ${String(manifest.counts.items)} does not match items.json length ` +
      `${itemsDocument.items.length}.`,
  );
  expect(isRecord(categoriesDocument) && Array.isArray(categoriesDocument.categories), 'categories.json must contain a categories array.');
  expectEqual(failures, [], 'failures.json');
  expectEqual(mobs, {mobs: []}, 'mobs.json');
  expectEqual(blockDrops, {blocks: {}}, 'blockdrops.json');
  expect(isRecord(reverseIndex), 'index.json must contain an object.');

  const itemsByKey = new Map();
  for (const [index, item] of itemsDocument.items.entries()) {
    expect(isRecord(item), `items[${index}] must be an object.`);
    expect(typeof item.k === 'string' && item.k.length > 0, `items[${index}].k must be a non-empty string.`);
    expect(!itemsByKey.has(item.k), `items.json contains duplicate key ${item.k}.`);
    if (item.icon !== undefined) {
      expect(safeRelativePath(item.icon), `item ${item.k} has an unsafe icon path.`);
    }
    itemsByKey.set(item.k, item);
  }
  const machineFrame = itemsByKey.get(MACHINE_FRAME_KEY);
  expect(machineFrame, `items.json is missing ${MACHINE_FRAME_KEY}.`);
  expect(machineFrame.id === 'thermalexpansion:frame', 'Machine Frame item id drifted.');
  expect(machineFrame.n === 'Machine Frame', 'Machine Frame item name drifted.');
  expect(machineFrame.m === 'thermalexpansion', 'Machine Frame item mod id drifted.');
  expect(machineFrame.icon === MACHINE_FRAME_ICON, 'Machine Frame icon path drifted.');

  expect(categoriesDocument.categories.length === TARGETS.length, 'sample must contain exactly two categories.');
  const categoriesById = new Map(categoriesDocument.categories.map((category, index) => [category.id, {category, index}]));
  expect(categoriesById.size === TARGETS.length, 'sample category ids must be unique.');
  const selectedRecipes = [];
  for (const target of TARGETS) {
    const located = categoriesById.get(target.categoryId);
    expect(located, `sample is missing category ${target.categoryId}.`);
    const {category, index: categoryIndex} = located;
    expect(category.title === target.categoryTitle, `${target.label} category title drifted.`);
    expect(category.dir === target.categoryDirectory, `${target.label} category directory drifted.`);
    expect(category.count === 1, `${target.label} category must contain exactly one recipe.`);
    const recipes = await readJson(root, `${category.dir}/recipes.json`, `${target.label} recipes`);
    expect(Array.isArray(recipes) && recipes.length === 1, `${target.label} recipes.json must contain one recipe.`);
    const recipe = recipes[0];
    expect(isRecord(recipe), `${target.label} recipe must be an object.`);
    expect(recipe.img === target.sampleImage, `${target.label} image must be r0.png.`);
    expect(recipe.w === 124 && recipe.h === 62, `${target.label} logical dimensions must remain 124x62.`);
    expectEqual(recipeSemantics(recipe), target.semantic, `${target.label} recipe semantics`);
    selectedRecipes.push({target, category, categoryIndex, recipe});
  }

  for (const {target, recipe} of selectedRecipes) {
    for (const [role, slots] of [['input', recipe.in], ['output', recipe.out]]) {
      for (const variants of slots) {
        for (const [key] of variants) {
          expect(itemsByKey.has(key), `${target.label} ${role} references missing item ${key}.`);
        }
      }
    }
  }

  const expectedProducedRefs = selectedRecipes
    .map(({categoryIndex}) => [categoryIndex, 0])
    .sort((left, right) => left[0] - right[0]);
  const targetIndex = reverseIndex[MACHINE_FRAME_KEY];
  expect(isRecord(targetIndex), `index.json is missing ${MACHINE_FRAME_KEY}.`);
  const actualProducedRefs = [...(targetIndex.p ?? [])].sort((left, right) => left[0] - right[0]);
  expectEqual(actualProducedRefs, expectedProducedRefs, 'Machine Frame produced-recipe index');

  return {items: itemsDocument.items, itemsByKey, selectedRecipes};
}

function modalItemKeys(selectedRecipes) {
  const keys = new Set([MACHINE_FRAME_KEY]);
  for (const {recipe} of selectedRecipes) {
    for (const slots of [recipe.in, recipe.out, recipe.cat ?? []]) {
      for (const variants of slots) {
        if (variants[0]) keys.add(variants[0][0]);
      }
    }
  }
  return keys;
}

async function compareReferenceSample(sampleRoot, referenceRoot, memory) {
  await requirePlainDirectory(referenceRoot, 'reference sample root');
  const [sampleFiles, referenceFiles] = await Promise.all([
    collectFiles(sampleRoot),
    collectFiles(referenceRoot),
  ]);
  const samplePngs = sampleFiles
    .map(path => relativeKey(sampleRoot, path))
    .filter(path => path.endsWith('.png'))
    .sort();
  const referencePngs = referenceFiles
    .map(path => relativeKey(referenceRoot, path))
    .filter(path => path.endsWith('.png'))
    .sort();
  expectEqual(samplePngs, referencePngs, 'optimized/reference sample PNG path inventory');
  const started = performance.now();
  let decodedRgbaBytes = 0;
  for (const path of samplePngs) {
    const sample = await decodePng(sampleRoot, path, `optimized sample ${path}`, memory);
    const reference = await decodePng(referenceRoot, path, `reference sample ${path}`, memory);
    expect(
      sample.width === reference.width && sample.height === reference.height,
      `optimized sample ${path} dimensions differ from the reference sample.`,
    );
    expect(
      sample.pixels.equals(reference.pixels),
      `optimized sample ${path} canonical RGBA differs from the reference sample.`,
    );
    decodedRgbaBytes += sample.pixels.length;
  }
  const sampleJsons = sampleFiles
    .map(path => relativeKey(sampleRoot, path))
    .filter(path => path.endsWith('.json') && path !== 'manifest.json')
    .sort();
  const referenceJsons = referenceFiles
    .map(path => relativeKey(referenceRoot, path))
    .filter(path => path.endsWith('.json') && path !== 'manifest.json')
    .sort();
  expectEqual(sampleJsons, referenceJsons, 'optimized/reference sample JSON path inventory');
  let jsonBytesCompared = 0;
  for (const path of sampleJsons) {
    const [samplePath, referencePath] = await Promise.all([
      requirePlainFile(sampleRoot, path, `optimized sample ${path}`),
      requirePlainFile(referenceRoot, path, `reference sample ${path}`),
    ]);
    const [sampleSource, referenceSource] = await Promise.all([
      readFile(samplePath),
      readFile(referencePath),
    ]);
    expect(
      sampleSource.equals(referenceSource),
      `optimized sample ${path} bytes differ from the reference sample.`,
    );
    jsonBytesCompared += sampleSource.length;
  }
  return {
    enabled: true,
    pngFilesCompared: samplePngs.length,
    decodedRgbaBytes,
    jsonFilesCompared: sampleJsons.length,
    jsonBytesCompared,
    comparisonMs: Number((performance.now() - started).toFixed(3)),
  };
}

export async function validateQualitySample({
  baselineRoot,
  sampleRoot,
  referenceSampleRoot,
  requireWorldStartupOptimization = false,
  expectedBaselineDigests = DEFAULT_BASELINE_DIGESTS,
} = {}) {
  expect(typeof baselineRoot === 'string' && baselineRoot.length > 0, '--baseline-root is required.');
  expect(typeof sampleRoot === 'string' && sampleRoot.length > 0, '--sample-root is required.');
  const baseline = resolve(baselineRoot);
  const sample = resolve(sampleRoot);
  const reference = referenceSampleRoot ? resolve(referenceSampleRoot) : null;
  expect(baseline !== sample, 'baseline and sample roots must be different directories.');
  if (reference) expect(reference !== sample, 'reference and sample roots must be different directories.');
  await Promise.all([
    requirePlainDirectory(baseline, 'baseline root'),
    requirePlainDirectory(sample, 'sample root'),
  ]);

  const started = performance.now();
  const memory = createMemoryTracker();
  const [baselineManifest, sampleManifest] = await Promise.all([
    validateManifest(baseline, false),
    validateManifest(sample, true, requireWorldStartupOptimization),
  ]);
  const baselineImages = await validateBaselineArtifacts(baseline, expectedBaselineDigests, memory);
  const sampleData = await validateSampleDocuments(sample, sampleManifest);

  const sampleCriticalImages = new Map();
  const machineFrameImage = await decodePng(sample, MACHINE_FRAME_ICON, 'sample Machine Frame icon', memory);
  requireDimensions(machineFrameImage, LOGICAL_ITEM_GRID * ICON_SCALE, LOGICAL_ITEM_GRID * ICON_SCALE);
  sampleCriticalImages.set(MACHINE_FRAME_ICON, machineFrameImage);
  const rerenderComparisons = [
    await requireGenuineRerender(
      baselineImages.get(MACHINE_FRAME_ICON),
      machineFrameImage,
      ICON_SCALE,
      memory,
    ),
  ];
  const recipeChromeComparisons = [];
  for (const {target, category, recipe} of sampleData.selectedRecipes) {
    const path = posix.join(category.dir, recipe.img);
    const image = await decodePng(sample, path, `sample ${target.label} preview`, memory);
    requireDimensions(image, recipe.w * RECIPE_SCALE, recipe.h * RECIPE_SCALE);
    sampleCriticalImages.set(path, image);
    rerenderComparisons.push(
      await requireGenuineRerender(
        baselineImages.get(target.baselineImage),
        image,
        RECIPE_SCALE,
        memory,
      ),
    );
    recipeChromeComparisons.push(
      requireRecipeChrome(baselineImages.get(target.baselineImage), image, RECIPE_SCALE),
    );
  }

  const uniqueIconPaths = [...new Set(sampleData.items.map(item => item.icon).filter(Boolean))].sort();
  expect(uniqueIconPaths.length > 0, 'sample contains no item icon assets.');
  const modalKeys = modalItemKeys(sampleData.selectedRecipes);
  const modalIconPaths = new Set();
  for (const key of modalKeys) {
    const item = sampleData.itemsByKey.get(key);
    expect(item?.icon, `cold Machine Frame modal item ${key} has no icon.`);
    modalIconPaths.add(item.icon);
  }

  const assetMetrics = [];
  const metricsByPath = new Map();
  for (const path of uniqueIconPaths) {
    let image = sampleCriticalImages.get(path);
    if (!image) image = await decodePng(sample, path, `sample item icon ${path}`, memory);
    requireDimensions(image, LOGICAL_ITEM_GRID * ICON_SCALE, LOGICAL_ITEM_GRID * ICON_SCALE);
    const metric = await encodeLosslessWebp(image, ICON_WEBP_EFFORT, 'item-icon', memory);
    assetMetrics.push(metric);
    metricsByPath.set(path, metric);
  }
  for (const {category, recipe, target} of sampleData.selectedRecipes) {
    const path = posix.join(category.dir, recipe.img);
    const image = sampleCriticalImages.get(path);
    const metric = await encodeLosslessWebp(image, RECIPE_WEBP_EFFORT, 'recipe-preview', memory);
    metric.label = target.label;
    assetMetrics.push(metric);
    metricsByPath.set(path, metric);
  }

  const iconMetrics = assetMetrics.filter(metric => metric.kind === 'item-icon');
  const previewMetrics = assetMetrics.filter(metric => metric.kind === 'recipe-preview');
  const coldPaths = new Set([
    ...modalIconPaths,
    ...sampleData.selectedRecipes.map(({category, recipe}) => posix.join(category.dir, recipe.img)),
  ]);
  const coldMetrics = [...coldPaths].map(path => {
    const metric = metricsByPath.get(path);
    expect(metric, `cold modal asset ${path} was not measured.`);
    return metric;
  });
  const iconP95WebpBytes = percentile95(iconMetrics.map(metric => metric.losslessWebpBytes));
  const coldModalWebpBytes = sum(coldMetrics.map(metric => metric.losslessWebpBytes));

  const referenceComparison = reference
    ? await compareReferenceSample(sample, reference, memory)
    : {enabled: false};
  const elapsedMs = performance.now() - started;

  return {
    passed: true,
    roots: {baseline, sample, ...(reference ? {referenceSample: reference} : {})},
    contract: {
      minecraft: MINECRAFT_VERSION,
      nativeLogicalItemGrid: `${LOGICAL_ITEM_GRID}x${LOGICAL_ITEM_GRID}`,
      itemRaster: `${LOGICAL_ITEM_GRID * ICON_SCALE}x${LOGICAL_ITEM_GRID * ICON_SCALE}`,
      iconScale: ICON_SCALE,
      recipeScale: RECIPE_SCALE,
      logicalRecipeLayout: '124x62',
      physicalRecipePreview: '248x124',
      recipes: TARGETS.map(target => ({
        category: target.categoryId,
        baselineSourceIndex: target.baselineSourceIndex,
        outputAmount: target.semantic.out[0][0][1],
      })),
      worldStartupOptimization: sampleManifest.settings.worldStartupOptimization,
    },
    fidelity: {
      rerenderComparisons,
      recipeChromeComparisons,
      referenceComparison,
    },
    storage: {
      assets: assetMetrics,
      itemIcons: {
        count: iconMetrics.length,
        pngBytes: sum(iconMetrics.map(metric => metric.pngBytes)),
        losslessWebpBytes: sum(iconMetrics.map(metric => metric.losslessWebpBytes)),
        p95PngBytes: percentile95(iconMetrics.map(metric => metric.pngBytes)),
        p95LosslessWebpBytes: iconP95WebpBytes,
      },
      recipePreviews: {
        count: previewMetrics.length,
        pngBytes: sum(previewMetrics.map(metric => metric.pngBytes)),
        losslessWebpBytes: sum(previewMetrics.map(metric => metric.losslessWebpBytes)),
      },
      coldMachineFrameModal: {
        assetCount: coldMetrics.length,
        paths: [...coldPaths].sort(),
        pngBytes: sum(coldMetrics.map(metric => metric.pngBytes)),
        losslessWebpBytes: coldModalWebpBytes,
      },
      optimizationSignals: {
        iconP95BudgetBytes: ICON_P95_BUDGET_BYTES,
        iconP95WithinBudget: iconP95WebpBytes <= ICON_P95_BUDGET_BYTES,
        coldModalBudgetBytes: COLD_MODAL_BUDGET_BYTES,
        coldModalWithinBudget: coldModalWebpBytes <= COLD_MODAL_BUDGET_BYTES,
        responsiveIconVariantsRecommended:
          iconP95WebpBytes > ICON_P95_BUDGET_BYTES || coldModalWebpBytes > COLD_MODAL_BUDGET_BYTES,
      },
    },
    performance: {
      validationMs: Number(elapsedMs.toFixed(3)),
      baselineExportDurationMs: baselineManifest.durationMs,
      sampleExportDurationMs: sampleManifest.durationMs,
      losslessWebpEncodeMs: Number(
        sum(assetMetrics.map(metric => metric.encodeMs)).toFixed(3),
      ),
      memory: memory.report(),
    },
  };
}

export function parseQualitySampleArguments(args) {
  const values = {};
  let help = false;
  let requireWorldStartupOptimization = false;
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === '--help' || argument === '-h') {
      help = true;
      continue;
    }
    if (argument === '--require-world-startup-optimization') {
      if (requireWorldStartupOptimization) {
        throw new Error('--require-world-startup-optimization may be provided only once.');
      }
      requireWorldStartupOptimization = true;
      continue;
    }
    const names = {
      '--baseline-root': 'baselineRoot',
      '--sample-root': 'sampleRoot',
      '--reference-sample-root': 'referenceSampleRoot',
    };
    const name = names[argument];
    if (!name) throw new Error(`Unknown quality-sample argument: ${argument}`);
    if (values[name] !== undefined) throw new Error(`${argument} may be provided only once.`);
    const value = args[++index];
    if (!value || value.startsWith('-')) throw new Error(`${argument} requires a directory path.`);
    values[name] = value;
  }
  return {help, requireWorldStartupOptimization, ...values};
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath && fileURLToPath(import.meta.url) === invokedPath) {
  let parsed;
  try {
    parsed = parseQualitySampleArguments(process.argv.slice(2));
    if (parsed.help) {
      console.log(
        'Usage: node scripts/validate-quality-sample.mjs --baseline-root <raw-scale-1-export> ' +
          '--sample-root <quality-sample-export> ' +
          '[--reference-sample-root <prior-high-resolution-sample>] ' +
          '[--require-world-startup-optimization]',
      );
    } else {
      const report = await validateQualitySample(parsed);
      console.log(JSON.stringify(report, null, 2));
    }
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  }
}
