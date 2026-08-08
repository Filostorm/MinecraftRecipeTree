import {spawn} from 'node:child_process';
import {createHash, randomUUID} from 'node:crypto';
import {constants as fsConstants, createReadStream} from 'node:fs';
import {
  chmod,
  copyFile,
  lstat,
  mkdir,
  mkdtemp,
  readFile,
  readdir,
  realpath,
  rename,
  rm,
  stat,
  statfs,
  writeFile,
} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {basename, dirname, join, posix, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {isDeepStrictEqual} from 'node:util';
import sharp from 'sharp';
import {decodedRgbaSha256} from './recipe-image-inventory.mjs';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const TREE_HASH_FORMAT = 'mrt-plain-content-tree-sha256-v1';
const REPAIR_FORMAT = 'mrt-recipe-preview-repair-overlay-v1';
const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const RECIPE_IMAGE_PATH_PATTERN = /^recipes\/[^/]+\/r[0-9]+\.png$/;
const HASH_CONCURRENCY = 16;

function compareCodeUnits(left, right) {
  return left < right ? -1 : left > right ? 1 : 0;
}

function deepFreeze(value) {
  if (value && typeof value === 'object' && !Object.isFrozen(value)) {
    Object.freeze(value);
    for (const child of Object.values(value)) deepFreeze(child);
  }
  return value;
}

export const MEATBALLCRAFT_PREVIEW_REPAIR_CONTRACT = deepFreeze({
  minecraft: '1.12.2',
  iconScale: 3,
  recipeScale: 2,
  totalRecipes: 359_215,
  totalCategories: 674,
  recipePngsBefore: 359_188,
  expectedFailureCount: 27,
  categories: [
    {
      id: 'zmaster587.AR.chemicalReactor',
      directory: 'recipes/zmaster587.ar.chemicalreactor',
      sourceCount: 1_534,
      exportedCount: 1_534,
      logicalWidth: 171,
      logicalHeight: 63,
      physicalWidth: 342,
      physicalHeight: 126,
      sourceIndexes: Array.from({length: 25}, (_, index) => 1_254 + index),
    },
    {
      id: 'buildcraft:category_heatable',
      directory: 'recipes/buildcraft_category_heatable',
      sourceCount: 21,
      exportedCount: 21,
      logicalWidth: 160,
      logicalHeight: 27,
      physicalWidth: 320,
      physicalHeight: 54,
      sourceIndexes: [20],
    },
    {
      id: 'buildcraft:category_coolable',
      directory: 'recipes/buildcraft_category_coolable',
      sourceCount: 21,
      exportedCount: 21,
      logicalWidth: 160,
      logicalHeight: 25,
      physicalWidth: 320,
      physicalHeight: 50,
      sourceIndexes: [20],
    },
  ],
});

sharp.cache(false);
sharp.concurrency(2);

function fail(message) {
  throw new Error(`Recipe-preview repair overlay failed: ${message}`);
}

function expect(condition, message) {
  if (!condition) fail(message);
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

function containsPath(parent, child) {
  const value = relative(parent, child);
  return value !== '' && value !== '..' && !value.startsWith(`..${sep}`) && !value.startsWith(sep);
}

function assertDisjoint(left, leftLabel, right, rightLabel) {
  if (left === right || containsPath(left, right) || containsPath(right, left)) {
    fail(`${leftLabel} and ${rightLabel} must be disjoint: ${left} and ${right}.`);
  }
}

async function pathKindNoFollow(path) {
  try {
    const info = await lstat(path);
    if (info.isSymbolicLink()) return 'symlink';
    if (info.isDirectory()) return 'directory';
    if (info.isFile()) return 'file';
    return 'other';
  } catch (error) {
    if (error?.code === 'ENOENT') return 'missing';
    throw error;
  }
}

async function requirePlainRoot(path, label) {
  const kind = await pathKindNoFollow(path);
  expect(kind === 'directory', `${label} must be a plain directory, not ${kind}: ${path}`);
  return realpath(path);
}

async function requirePlainFile(root, relativePath, label = relativePath) {
  expect(safeRelativePath(relativePath), `${label} has an unsafe relative path: ${relativePath}`);
  let current = root;
  const segments = relativePath.split('/');
  for (const segment of segments) {
    current = join(current, segment);
    const info = await lstat(current).catch(error => {
      fail(`${label} is unavailable at ${current}: ${error.message}`);
    });
    const last = segment === segments.at(-1) && current === join(root, ...segments);
    expect(!info.isSymbolicLink(), `${label} traverses a symbolic link: ${current}`);
    expect(last ? info.isFile() : info.isDirectory(), `${label} has the wrong filesystem type: ${current}`);
  }
  return current;
}

async function readJsonFile(root, relativePath, label = relativePath) {
  const path = await requirePlainFile(root, relativePath, label);
  const source = await readFile(path);
  let value;
  try {
    value = JSON.parse(source.toString('utf8'));
  } catch (error) {
    fail(`${label} is not valid JSON: ${error.message}`);
  }
  return {path, source, value};
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function updateFramed(hash, value) {
  const bytes = Buffer.isBuffer(value) ? value : Buffer.from(String(value), 'utf8');
  const length = Buffer.allocUnsafe(8);
  length.writeBigUInt64BE(BigInt(bytes.length));
  hash.update(length).update(bytes);
}

async function sha256File(path) {
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(path)) hash.update(chunk);
  return hash.digest('hex');
}

function digestInventoryRecords(records) {
  const hash = createHash('sha256');
  hash.update(`${TREE_HASH_FORMAT}\0`);
  for (const record of records) {
    updateFramed(hash, record.path);
    updateFramed(hash, record.size);
    updateFramed(hash, record.mode);
    updateFramed(hash, record.sha256);
  }
  return hash.digest('hex');
}

function finalizeInventory(records) {
  const ordered = [...records].sort((left, right) => compareCodeUnits(left.path, right.path));
  return {
    format: TREE_HASH_FORMAT,
    sha256: digestInventoryRecords(ordered),
    records: ordered,
    byPath: new Map(ordered.map(record => [record.path, record])),
  };
}

/**
 * Hashes every regular file and rejects links/special entries. The bounded worker
 * pool keeps the stress-test scan deterministic without opening hundreds of
 * thousands of PNGs simultaneously.
 */
export async function createPlainTreeInventory(root) {
  const rootReal = await requirePlainRoot(resolve(root), 'Tree root');
  const pending = [{absolute: rootReal, relative: ''}];
  const files = [];
  while (pending.length > 0) {
    const current = pending.pop();
    const currentInfo = await lstat(current.absolute);
    expect(
      !currentInfo.isSymbolicLink() && currentInfo.isDirectory(),
      `tree contains a non-plain directory: ${current.absolute}`,
    );
    const entries = await readdir(current.absolute, {withFileTypes: true});
    entries.sort((left, right) => compareCodeUnits(left.name, right.name));
    for (const entry of entries) {
      const absolute = join(current.absolute, entry.name);
      const childRelative = current.relative ? `${current.relative}/${entry.name}` : entry.name;
      const info = await lstat(absolute);
      if (info.isSymbolicLink()) fail(`tree contains a symbolic link: ${absolute}`);
      if (info.isDirectory()) pending.push({absolute, relative: childRelative});
      else if (info.isFile()) {
        files.push({absolute, path: childRelative, size: info.size, mode: info.mode & 0o7777});
      } else fail(`tree contains a special filesystem entry: ${absolute}`);
    }
  }
  files.sort((left, right) => compareCodeUnits(left.path, right.path));
  const records = new Array(files.length);
  let cursor = 0;
  const workers = Array.from({length: Math.min(HASH_CONCURRENCY, files.length)}, async () => {
    for (;;) {
      const index = cursor++;
      if (index >= files.length) return;
      const file = files[index];
      records[index] = {
        path: file.path,
        size: file.size,
        mode: file.mode,
        sha256: await sha256File(file.absolute),
      };
    }
  });
  await Promise.all(workers);
  return finalizeInventory(records);
}

function withReplacedInventoryRecord(inventory, path, bytes, mode) {
  const records = inventory.records.map(record =>
    record.path === path
      ? {path, size: bytes.length, mode, sha256: sha256(bytes)}
      : record,
  );
  expect(records.some(record => record.path === path), `inventory is missing ${path}`);
  return finalizeInventory(records);
}

function compareInventoryDiff(before, after, expectedAdded, expectedChanged) {
  const added = [];
  const removed = [];
  const changed = [];
  for (const [path, record] of after.byPath) {
    const previous = before.byPath.get(path);
    if (!previous) added.push(path);
    else if (
      previous.sha256 !== record.sha256 ||
      previous.size !== record.size ||
      previous.mode !== record.mode
    ) changed.push(path);
  }
  for (const path of before.byPath.keys()) {
    if (!after.byPath.has(path)) removed.push(path);
  }
  for (const values of [added, removed, changed]) values.sort(compareCodeUnits);
  const expectedAddedOrdered = [...expectedAdded].sort(compareCodeUnits);
  const expectedChangedOrdered = [...expectedChanged].sort(compareCodeUnits);
  expect(
    isDeepStrictEqual(added, expectedAddedOrdered),
    `pre/post file-addition whitelist mismatch; expected ${JSON.stringify(expectedAddedOrdered)}, received ${JSON.stringify(added)}`,
  );
  expect(removed.length === 0, `pre/post whitelist forbids removed files: ${JSON.stringify(removed)}`);
  expect(
    isDeepStrictEqual(changed, expectedChangedOrdered),
    `pre/post changed-file whitelist mismatch; expected ${JSON.stringify(expectedChangedOrdered)}, received ${JSON.stringify(changed)}`,
  );
  return {added, changed, removed};
}

function canonicalJson(value) {
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  if (isRecord(value)) {
    const keys = Object.keys(value).sort(compareCodeUnits);
    return `{${keys.map(key => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

function stripImageFields(recipe) {
  const stripped = {};
  for (const [key, value] of Object.entries(recipe)) {
    if (key !== 'img' && key !== 'w' && key !== 'h') stripped[key] = value;
  }
  return stripped;
}

function addImageFields(recipe, {image, width, height}) {
  const patched = {};
  let inserted = false;
  for (const [key, value] of Object.entries(recipe)) {
    if (!inserted && key === 'in') {
      patched.img = image;
      patched.w = width;
      patched.h = height;
      inserted = true;
    }
    patched[key] = value;
  }
  if (!inserted) {
    patched.img = image;
    patched.w = width;
    patched.h = height;
  }
  return patched;
}

function targetCount(contract) {
  return contract.categories.reduce((sum, category) => sum + category.sourceIndexes.length, 0);
}

function contractTargets(contract) {
  return contract.categories.flatMap(category =>
    category.sourceIndexes.map(sourceIndex => ({category, sourceIndex})),
  );
}

function historicalFailure(categoryId, sourceIndex) {
  return (
    `recipe image ${categoryId} #${sourceIndex}: java.lang.NullPointerException: ` +
    'Recipe layout crashed during creation, see log.'
  );
}

function validateContract(contract) {
  expect(isRecord(contract), 'repair contract must be an object');
  expect(Array.isArray(contract.categories) && contract.categories.length > 0, 'repair contract needs categories');
  expect(Number.isSafeInteger(contract.totalRecipes) && contract.totalRecipes > 0, 'contract totalRecipes is invalid');
  expect(Number.isSafeInteger(contract.recipePngsBefore), 'contract recipePngsBefore is invalid');
  const seenCategories = new Set();
  const seenTargets = new Set();
  for (const category of contract.categories) {
    expect(typeof category.id === 'string' && category.id, 'contract category id is invalid');
    expect(!seenCategories.has(category.id), `contract repeats category ${category.id}`);
    seenCategories.add(category.id);
    expect(safeRelativePath(category.directory), `contract category directory is unsafe: ${category.directory}`);
    expect(Array.isArray(category.sourceIndexes) && category.sourceIndexes.length > 0, `${category.id} has no targets`);
    expect(category.physicalWidth === category.logicalWidth * contract.recipeScale, `${category.id} width scale contract is inconsistent`);
    expect(category.physicalHeight === category.logicalHeight * contract.recipeScale, `${category.id} height scale contract is inconsistent`);
    for (const sourceIndex of category.sourceIndexes) {
      expect(Number.isSafeInteger(sourceIndex) && sourceIndex >= 0, `${category.id} target index is invalid`);
      const key = `${category.id}\0${sourceIndex}`;
      expect(!seenTargets.has(key), `contract repeats target ${category.id} #${sourceIndex}`);
      seenTargets.add(key);
    }
  }
  expect(targetCount(contract) === contract.expectedFailureCount, 'contract target/failure counts disagree');
  expect(contract.recipePngsBefore + targetCount(contract) === contract.totalRecipes, 'contract image arithmetic disagrees');
}

function validateManifestBase(manifest, contract, label, qualitySample) {
  expect(isRecord(manifest), `${label} manifest must be an object`);
  expect(manifest.aborted === false, `${label} manifest is aborted`);
  expect(manifest.minecraft === contract.minecraft, `${label} Minecraft version must be ${contract.minecraft}`);
  expect(manifest.settings?.iconScale === contract.iconScale, `${label} iconScale must be ${contract.iconScale}`);
  expect(manifest.settings?.recipeScale === contract.recipeScale, `${label} recipeScale must be ${contract.recipeScale}`);
  if (qualitySample) {
    expect(manifest.qualitySample?.enabled === true, 'sample qualitySample.enabled must be true');
    expect(manifest.qualitySample?.recipeTargets === targetCount(contract), 'sample recipeTargets is not exact');
    expect(manifest.qualitySample?.selectorCounts?.sourceIndex === targetCount(contract), 'sample must use exactly the compatibility source-index selectors');
    expect(manifest.qualitySample?.selectorCounts?.recipeId === 0, 'sample must not contain recipe-id selectors');
  } else {
    expect(!Object.hasOwn(manifest, 'qualitySample'), 'full export must not be a quality sample');
    expect(!Object.hasOwn(manifest, 'repairProvenance'), 'full export is already repaired');
  }
}

function categoryMap(document, label) {
  expect(isRecord(document) && Array.isArray(document.categories), `${label} categories.json is invalid`);
  const result = new Map();
  for (const category of document.categories) {
    expect(isRecord(category) && typeof category.id === 'string', `${label} has an invalid category`);
    expect(!result.has(category.id), `${label} repeats category ${category.id}`);
    expect(safeRelativePath(category.dir), `${label} category ${category.id} has an unsafe directory`);
    result.set(category.id, category);
  }
  return result;
}

async function scanRecipeDocuments(root, categoriesDocument, inventory, contract, phase) {
  const targetCategoryIds = new Set(contract.categories.map(category => category.id));
  const targetDocuments = new Map();
  const missing = [];
  const referencedImages = new Set();
  let recipes = 0;
  for (const category of categoriesDocument.categories) {
    const recipesRelative = posix.join(category.dir, 'recipes.json');
    const document = await readJsonFile(root, recipesRelative, `${phase}/${recipesRelative}`);
    expect(Array.isArray(document.value), `${phase}/${recipesRelative} must contain an array`);
    expect(category.count === document.value.length, `${phase} category ${category.id} count does not match recipes.json`);
    recipes += document.value.length;
    if (targetCategoryIds.has(category.id)) targetDocuments.set(category.id, document);
    for (let recipeIndex = 0; recipeIndex < document.value.length; recipeIndex += 1) {
      const recipe = document.value[recipeIndex];
      expect(isRecord(recipe), `${phase} recipe ${category.id} #${recipeIndex} is not an object`);
      const hasImage = Object.hasOwn(recipe, 'img');
      const hasWidth = Object.hasOwn(recipe, 'w');
      const hasHeight = Object.hasOwn(recipe, 'h');
      if (!hasImage || !hasWidth || !hasHeight) {
        expect(!hasImage && !hasWidth && !hasHeight, `${phase} recipe ${category.id} #${recipeIndex} has partial image metadata`);
        missing.push({categoryId: category.id, directory: category.dir, recipeIndex, recipe});
        continue;
      }
      expect(typeof recipe.img === 'string' && /^r[0-9]+\.png$/.test(recipe.img), `${phase} recipe ${category.id} #${recipeIndex} has an invalid raw PNG reference`);
      expect(recipe.img === `r${recipeIndex}.png`, `${phase} recipe ${category.id} #${recipeIndex} image name is not canonical`);
      expect(Number.isSafeInteger(recipe.w) && recipe.w > 0, `${phase} recipe ${category.id} #${recipeIndex} width is invalid`);
      expect(Number.isSafeInteger(recipe.h) && recipe.h > 0, `${phase} recipe ${category.id} #${recipeIndex} height is invalid`);
      const imagePath = posix.join(category.dir, recipe.img);
      expect(!referencedImages.has(imagePath), `${phase} repeats recipe image ${imagePath}`);
      referencedImages.add(imagePath);
      expect(inventory.byPath.has(imagePath), `${phase} recipe image is missing: ${imagePath}`);
    }
  }
  const actualRecipePngs = inventory.records.filter(record => RECIPE_IMAGE_PATH_PATTERN.test(record.path));
  expect(actualRecipePngs.length === referencedImages.size, `${phase} contains unreferenced or multiply referenced recipe PNG files`);
  for (const record of actualRecipePngs) {
    expect(referencedImages.has(record.path), `${phase} contains an unreferenced recipe PNG: ${record.path}`);
  }
  return {recipes, missing, referencedImages, recipePngCount: actualRecipePngs.length, targetDocuments};
}

async function decodeStrictPng(root, relativePath, expected, label) {
  const absolute = await requirePlainFile(root, relativePath, label);
  const source = await readFile(absolute);
  let metadata;
  let decoded;
  try {
    const pipeline = sharp(source, {limitInputPixels: 16_384 * 16_384, failOn: 'warning'});
    metadata = await pipeline.metadata();
    decoded = await sharp(source, {limitInputPixels: 16_384 * 16_384, failOn: 'warning'})
      .ensureAlpha()
      .raw()
      .toBuffer({resolveWithObject: true});
  } catch (error) {
    fail(`${label} cannot be decoded strictly: ${error.message}`);
  }
  expect(metadata.format === 'png', `${label} must be a PNG, not ${metadata.format}`);
  expect(metadata.pages === undefined || metadata.pages === 1, `${label} must contain one image`);
  expect(metadata.width === expected.physicalWidth, `${label} physical width must be ${expected.physicalWidth}, received ${metadata.width}`);
  expect(metadata.height === expected.physicalHeight, `${label} physical height must be ${expected.physicalHeight}, received ${metadata.height}`);
  expect(decoded.info.width === expected.physicalWidth && decoded.info.height === expected.physicalHeight, `${label} decoded dimensions disagree with metadata`);
  expect(decoded.data.length === expected.physicalWidth * expected.physicalHeight * 4, `${label} decoded RGBA byte count is invalid`);
  return {
    bytes: source,
    pngSha256: sha256(source),
    rgbaSha256: decodedRgbaSha256(decoded.info.width, decoded.info.height, decoded.data),
  };
}

async function atomicWrite(path, bytes, mode) {
  const temporary = join(dirname(path), `.${basename(path)}.repair-${process.pid}-${randomUUID()}.tmp`);
  let created = false;
  try {
    await writeFile(temporary, bytes, {flag: 'wx', mode});
    created = true;
    await chmod(temporary, mode);
    await rename(temporary, path);
    created = false;
  } finally {
    if (created) await rm(temporary, {force: true});
  }
}

function jsonBytes(value, pretty = false) {
  return Buffer.from(pretty ? JSON.stringify(value, null, 2) : JSON.stringify(value), 'utf8');
}

function expectedTargetKeys(contract) {
  return new Set(contractTargets(contract).map(({category, sourceIndex}) => `${category.id}\0${sourceIndex}`));
}

function validateExactMissingSet(missing, contract, phase) {
  const actual = new Set(missing.map(entry => `${entry.categoryId}\0${entry.recipeIndex}`));
  const expected = expectedTargetKeys(contract);
  expect(actual.size === missing.length, `${phase} repeats a missing recipe identity`);
  expect(isDeepStrictEqual([...actual].sort(), [...expected].sort()), `${phase} missing-image set is not the exact compatibility target set`);
}

async function validateFullInput(root, inventory, contract) {
  const manifestDocument = await readJsonFile(root, 'manifest.json', 'full/manifest.json');
  const categoriesDocument = await readJsonFile(root, 'categories.json', 'full/categories.json');
  const failuresDocument = await readJsonFile(root, 'failures.json', 'full/failures.json');
  const manifest = manifestDocument.value;
  validateManifestBase(manifest, contract, 'full', false);
  expect(manifest.counts?.recipes === contract.totalRecipes, `full manifest recipe count must be ${contract.totalRecipes}`);
  expect(manifest.counts?.categories === contract.totalCategories, `full manifest category count must be ${contract.totalCategories}`);
  expect(manifest.diagnostics?.failureEventsOmitted === 0, 'full export must not omit failure diagnostics');
  expect(Array.isArray(failuresDocument.value), 'full failures.json must be an array');
  const expectedFailures = contractTargets(contract).map(({category, sourceIndex}) => historicalFailure(category.id, sourceIndex));
  expect(
    manifest.counts?.failures === failuresDocument.value.length,
    'full manifest counts.failures must equal the serialized failure array length',
  );
  expect(
    manifest.diagnostics?.failureEvents === failuresDocument.value.length,
    'full diagnostic failureEvents must equal the complete, non-omitted failure array length',
  );
  const expectedFailureSet = new Set(expectedFailures);
  const foundExpectedFailures = failuresDocument.value.filter(failure => expectedFailureSet.has(failure));
  expect(
    foundExpectedFailures.length === expectedFailures.length &&
      new Set(foundExpectedFailures).size === expectedFailures.length,
    `full failures.json must contain each of the ${expectedFailures.length} historical compatibility strings exactly once`,
  );
  const remainingFailures = failuresDocument.value.filter(failure => !expectedFailureSet.has(failure));
  const categories = categoryMap(categoriesDocument.value, 'full');
  expect(categories.size === contract.totalCategories, `full categories.json must contain ${contract.totalCategories} categories`);
  for (const expected of contract.categories) {
    const category = categories.get(expected.id);
    expect(category, `full export is missing category ${expected.id}`);
    expect(category.dir === expected.directory, `full category ${expected.id} directory changed`);
    expect(category.count === expected.exportedCount, `full category ${expected.id} exported count must be ${expected.exportedCount}`);
  }
  const scan = await scanRecipeDocuments(root, categoriesDocument.value, inventory, contract, 'full');
  expect(scan.recipes === contract.totalRecipes, `full recipes.json documents contain ${scan.recipes}, expected ${contract.totalRecipes}`);
  expect(scan.recipePngCount === contract.recipePngsBefore, `full raw export has ${scan.recipePngCount} recipe PNGs, expected ${contract.recipePngsBefore}`);
  expect(scan.missing.length === targetCount(contract), `full export has ${scan.missing.length} image-less recipes, expected ${targetCount(contract)}`);
  validateExactMissingSet(scan.missing, contract, 'full');
  for (const expected of contract.categories) {
    const document = scan.targetDocuments.get(expected.id);
    expect(document?.value.length === expected.sourceCount, `full category ${expected.id} source count must be ${expected.sourceCount}`);
  }
  return {
    manifestDocument,
    categoriesDocument,
    failuresDocument,
    expectedFailures,
    remainingFailures,
    scan,
  };
}

async function validateSampleInput(root, inventory, contract, fullManifest) {
  const manifestDocument = await readJsonFile(root, 'manifest.json', 'sample/manifest.json');
  const categoriesDocument = await readJsonFile(root, 'categories.json', 'sample/categories.json');
  const failuresDocument = await readJsonFile(root, 'failures.json', 'sample/failures.json');
  const manifest = manifestDocument.value;
  validateManifestBase(manifest, contract, 'sample', true);
  expect(isDeepStrictEqual(manifest.mods, fullManifest.mods), 'sample mod identity does not exactly match the full export');
  expect(manifest.counts?.recipes === targetCount(contract), `sample recipe count must be ${targetCount(contract)}`);
  expect(manifest.counts?.categories === contract.categories.length, `sample category count must be ${contract.categories.length}`);
  expect(manifest.counts?.failures === 0, 'sample manifest failures must be zero');
  expect(manifest.diagnostics?.failureEvents === 0, 'sample failureEvents must be zero');
  expect(manifest.diagnostics?.failureEventsOmitted === 0, 'sample failureEventsOmitted must be zero');
  expect(isDeepStrictEqual(failuresDocument.value, []), 'sample failures.json must be exactly empty');
  const categories = categoryMap(categoriesDocument.value, 'sample');
  expect(categories.size === contract.categories.length, `sample must contain exactly ${contract.categories.length} categories`);
  for (const expected of contract.categories) {
    const category = categories.get(expected.id);
    expect(category, `sample is missing compatibility category ${expected.id}`);
    expect(category.dir === expected.directory, `sample category ${expected.id} directory changed`);
    expect(category.count === expected.sourceIndexes.length, `sample compatibility diagnostic for ${expected.id} must be ${expected.sourceIndexes.length}`);
  }
  const scan = await scanRecipeDocuments(root, categoriesDocument.value, inventory, contract, 'sample');
  expect(scan.recipes === targetCount(contract), `sample recipes.json documents contain ${scan.recipes}, expected ${targetCount(contract)}`);
  expect(scan.missing.length === 0, 'sample must not contain image-less recipes');
  expect(scan.recipePngCount === targetCount(contract), `sample must contain exactly ${targetCount(contract)} recipe PNGs`);
  return {manifestDocument, categoriesDocument, failuresDocument, scan};
}

async function planSemanticOverlay(stagingRoot, sampleRoot, full, sample, contract) {
  const missingByCategory = new Map();
  for (const missing of full.scan.missing) {
    const signature = canonicalJson(stripImageFields(missing.recipe));
    const category = missingByCategory.get(missing.categoryId) ?? new Map();
    const matches = category.get(signature) ?? [];
    matches.push(missing);
    category.set(signature, matches);
    missingByCategory.set(missing.categoryId, category);
  }
  const plan = [];
  const matchedTargets = new Set();
  for (const expected of contract.categories) {
    const sampleDocument = sample.scan.targetDocuments.get(expected.id);
    expect(sampleDocument, `sample recipe document is missing for ${expected.id}`);
    const semanticCandidates = missingByCategory.get(expected.id) ?? new Map();
    for (let sampleIndex = 0; sampleIndex < sampleDocument.value.length; sampleIndex += 1) {
      const sampleRecipe = sampleDocument.value[sampleIndex];
      const strippedSample = stripImageFields(sampleRecipe);
      const signature = canonicalJson(strippedSample);
      const matches = (semanticCandidates.get(signature) ?? []).filter(candidate =>
        isDeepStrictEqual(stripImageFields(candidate.recipe), strippedSample),
      );
      expect(matches.length === 1, `sample recipe ${expected.id} #${sampleIndex} canonically matches ${matches.length} image-less full entries; exactly one is required`);
      const match = matches[0];
      const targetKey = `${expected.id}\0${match.recipeIndex}`;
      expect(expected.sourceIndexes.includes(match.recipeIndex), `semantic match for sample ${expected.id} #${sampleIndex} resolved outside the audited source-index set: ${match.recipeIndex}`);
      expect(!matchedTargets.has(targetKey), `multiple sample recipes resolve to full target ${expected.id} #${match.recipeIndex}`);
      matchedTargets.add(targetKey);
      expect(sampleRecipe.w === expected.logicalWidth, `sample ${expected.id} #${sampleIndex} logical width must be ${expected.logicalWidth}`);
      expect(sampleRecipe.h === expected.logicalHeight, `sample ${expected.id} #${sampleIndex} logical height must be ${expected.logicalHeight}`);
      const sampleImageRelative = posix.join(expected.directory, sampleRecipe.img);
      const decoded = await decodeStrictPng(sampleRoot, sampleImageRelative, expected, `sample/${sampleImageRelative}`);
      const outputImageName = `r${match.recipeIndex}.png`;
      const outputImageRelative = posix.join(expected.directory, outputImageName);
      expect(!full.scan.referencedImages.has(outputImageRelative), `repair output image is already referenced: ${outputImageRelative}`);
      plan.push({
        category: expected,
        sourceIndex: match.recipeIndex,
        fullRecipe: match.recipe,
        sampleRecipe,
        sampleImageRelative,
        outputImageName,
        outputImageRelative,
        decoded,
      });
    }
  }
  plan.sort((left, right) => {
    const categoryOrder = contract.categories.findIndex(category => category.id === left.category.id) -
      contract.categories.findIndex(category => category.id === right.category.id);
    return categoryOrder || left.sourceIndex - right.sourceIndex;
  });
  expect(plan.length === targetCount(contract), `semantic overlay planned ${plan.length} previews, expected ${targetCount(contract)}`);
  expect(isDeepStrictEqual(matchedTargets, expectedTargetKeys(contract)), 'semantic overlay did not resolve the exact audited target set');
  return plan;
}

function buildPatchedCategoryDocuments(full, plan, contract) {
  const result = new Map();
  for (const expected of contract.categories) {
    const sourceDocument = full.scan.targetDocuments.get(expected.id);
    const recipes = structuredClone(sourceDocument.value);
    for (const entry of plan.filter(item => item.category.id === expected.id)) {
      const current = recipes[entry.sourceIndex];
      expect(canonicalJson(stripImageFields(current)) === canonicalJson(stripImageFields(entry.fullRecipe)), `staged target changed before patching: ${expected.id} #${entry.sourceIndex}`);
      recipes[entry.sourceIndex] = addImageFields(current, {
        image: entry.outputImageName,
        width: expected.logicalWidth,
        height: expected.logicalHeight,
      });
      expect(isDeepStrictEqual(stripImageFields(recipes[entry.sourceIndex]), current), `patch altered recipe semantics for ${expected.id} #${entry.sourceIndex}`);
    }
    result.set(expected.id, {sourceDocument, recipes});
  }
  return result;
}

function manifestWithoutProvenance(fullManifest) {
  const manifest = structuredClone(fullManifest);
  delete manifest.repairProvenance;
  return manifest;
}

function buildProvenance({contract, full, sample, beforeInventory, sampleInventory, normalizedInventory, plan}) {
  return {
    format: REPAIR_FORMAT,
    method: 'canonical-deep-equality-sample-overlay',
    repairedRecipePreviews: targetCount(contract),
    compatibilityDiagnostics: Object.fromEntries(
      contract.categories.map(category => [category.id, category.sourceIndexes.length]),
    ),
    hashAlgorithm: 'sha256',
    treeHashFormat: TREE_HASH_FORMAT,
    source: {
      minecraft: full.manifestDocument.value.minecraft,
      generatedAt: full.manifestDocument.value.generatedAt,
      manifestSha256: sha256(full.manifestDocument.source),
      treeSha256: beforeInventory.sha256,
      recipes: contract.totalRecipes,
      recipePngs: contract.recipePngsBefore,
      missingRecipeImages: targetCount(contract),
      failureEvents: full.failuresDocument.value.length,
    },
    sample: {
      generatedAt: sample.manifestDocument.value.generatedAt,
      manifestSha256: sha256(sample.manifestDocument.source),
      treeSha256: sampleInventory.sha256,
      recipes: targetCount(contract),
      failures: 0,
    },
    repaired: {
      normalizedTreeSha256: normalizedInventory.sha256,
      normalization: 'manifest.repairProvenance omitted; all other final paths, modes, sizes, and bytes included',
      recipes: contract.totalRecipes,
      recipePngs: contract.totalRecipes,
      missingRecipeImages: 0,
      remainingFailureEvents: full.remainingFailures.length,
      previewPngs: plan.map(entry => ({
        category: entry.category.id,
        sourceIndex: entry.sourceIndex,
        sampleImage: entry.sampleImageRelative,
        outputImage: entry.outputImageRelative,
        logicalWidth: entry.category.logicalWidth,
        logicalHeight: entry.category.logicalHeight,
        physicalWidth: entry.category.physicalWidth,
        physicalHeight: entry.category.physicalHeight,
        pngSha256: entry.decoded.pngSha256,
        decodedRgbaSha256: entry.decoded.rgbaSha256,
      })),
    },
  };
}

async function verifyRepairedOutput(
  root,
  inventory,
  contract,
  expectedManifest,
  expectedManifestBytes,
  expectedFailures,
  expectedCategories,
  plan,
) {
  const manifestDocument = await readJsonFile(root, 'manifest.json', 'repaired/manifest.json');
  const manifest = manifestDocument.value;
  expect(manifestDocument.source.equals(expectedManifestBytes), 'repaired manifest bytes are not deterministic');
  expect(isDeepStrictEqual(manifest, expectedManifest), 'repaired manifest differs from the exact whitelisted result');
  const failures = (await readJsonFile(root, 'failures.json', 'repaired/failures.json')).value;
  expect(
    isDeepStrictEqual(failures, expectedFailures),
    'repaired failures.json does not exactly retain the non-repair diagnostics',
  );
  const categories = (await readJsonFile(root, 'categories.json', 'repaired/categories.json')).value;
  const scan = await scanRecipeDocuments(root, categories, inventory, contract, 'repaired');
  expect(scan.recipes === contract.totalRecipes, 'repaired recipe count changed');
  expect(scan.missing.length === 0, `repaired tree still has ${scan.missing.length} image-less recipes`);
  expect(scan.recipePngCount === contract.totalRecipes, `repaired tree has ${scan.recipePngCount} recipe PNGs, expected ${contract.totalRecipes}`);
  for (const expected of contract.categories) {
    expect(
      isDeepStrictEqual(scan.targetDocuments.get(expected.id)?.value, expectedCategories.get(expected.id).recipes),
      `repaired category ${expected.id} differs outside the exact img/w/h additions`,
    );
  }
  for (const entry of plan) {
    const output = await decodeStrictPng(root, entry.outputImageRelative, entry.category, `repaired/${entry.outputImageRelative}`);
    expect(output.pngSha256 === entry.decoded.pngSha256, `copied PNG bytes changed for ${entry.outputImageRelative}`);
    expect(output.rgbaSha256 === entry.decoded.rgbaSha256, `copied PNG decoded pixels changed for ${entry.outputImageRelative}`);
  }
}

/** Repairs an already cloned staging tree. Exported for small-fixture tests; the CLI always wraps it in the required APFS clone and atomic publication. */
export async function repairClonedPreviewTree({stagingRoot, sampleRoot, contract = MEATBALLCRAFT_PREVIEW_REPAIR_CONTRACT}) {
  validateContract(contract);
  const stagingReal = await requirePlainRoot(resolve(stagingRoot), 'Cloned staging root');
  const sampleReal = await requirePlainRoot(resolve(sampleRoot), 'Compatibility sample root');
  assertDisjoint(stagingReal, 'Cloned staging root', sampleReal, 'compatibility sample root');

  console.log('[repair-previews] Hashing the cloned full tree and strict compatibility sample.');
  const [beforeInventory, sampleInventory] = await Promise.all([
    createPlainTreeInventory(stagingReal),
    createPlainTreeInventory(sampleReal),
  ]);
  const full = await validateFullInput(stagingReal, beforeInventory, contract);
  const sample = await validateSampleInput(sampleReal, sampleInventory, contract, full.manifestDocument.value);
  const plan = await planSemanticOverlay(stagingReal, sampleReal, full, sample, contract);
  const patchedCategories = buildPatchedCategoryDocuments(full, plan, contract);
  // The complete reference set is needed only through planning. Releasing it
  // before the second 359k-recipe scan materially lowers peak heap usage.
  full.scan.referencedImages.clear();

  const expectedAdded = new Set(plan.map(entry => entry.outputImageRelative));
  for (const path of expectedAdded) expect(!beforeInventory.byPath.has(path), `repair PNG already exists in full tree: ${path}`);
  const expectedChanged = new Set([
    'manifest.json',
    'failures.json',
    ...contract.categories.map(category => posix.join(category.directory, 'recipes.json')),
  ]);

  for (const entry of plan) {
    const destination = join(stagingReal, ...entry.outputImageRelative.split('/'));
    await copyFile(join(sampleReal, ...entry.sampleImageRelative.split('/')), destination, fsConstants.COPYFILE_EXCL);
    const copiedHash = await sha256File(destination);
    expect(copiedHash === entry.decoded.pngSha256, `byte-exact PNG copy failed for ${entry.outputImageRelative}`);
  }
  for (const expected of contract.categories) {
    const document = patchedCategories.get(expected.id);
    const info = await lstat(document.sourceDocument.path);
    await atomicWrite(document.sourceDocument.path, jsonBytes(document.recipes), info.mode & 0o7777);
  }
  const failuresInfo = await lstat(full.failuresDocument.path);
  await atomicWrite(
    full.failuresDocument.path,
    jsonBytes(full.remainingFailures),
    failuresInfo.mode & 0o7777,
  );

  const normalizedManifest = manifestWithoutProvenance(full.manifestDocument.value);
  normalizedManifest.counts.failures = full.remainingFailures.length;
  normalizedManifest.diagnostics.failureEvents = full.remainingFailures.length;
  normalizedManifest.diagnostics.failureEventsOmitted = 0;
  const manifestInfo = await lstat(full.manifestDocument.path);
  const normalizedManifestBytes = jsonBytes(normalizedManifest, true);
  await atomicWrite(full.manifestDocument.path, normalizedManifestBytes, manifestInfo.mode & 0o7777);

  console.log('[repair-previews] Verifying the exact pre/provisional-post file whitelist.');
  const normalizedInventory = await createPlainTreeInventory(stagingReal);
  compareInventoryDiff(beforeInventory, normalizedInventory, expectedAdded, expectedChanged);
  const provenance = buildProvenance({
    contract,
    full,
    sample,
    beforeInventory,
    sampleInventory,
    normalizedInventory,
    plan,
  });
  expect(provenance.repaired.previewPngs.length === targetCount(contract), 'provenance must contain every exact PNG hash');
  expect(provenance.repaired.previewPngs.every(entry => SHA256_PATTERN.test(entry.pngSha256)), 'provenance contains an invalid PNG hash');
  const finalManifest = {...normalizedManifest, repairProvenance: provenance};
  const finalManifestBytes = jsonBytes(finalManifest, true);
  await atomicWrite(full.manifestDocument.path, finalManifestBytes, manifestInfo.mode & 0o7777);
  const finalInventory = withReplacedInventoryRecord(
    normalizedInventory,
    'manifest.json',
    finalManifestBytes,
    manifestInfo.mode & 0o7777,
  );
  compareInventoryDiff(beforeInventory, finalInventory, expectedAdded, expectedChanged);
  const normalizedRecomputed = withReplacedInventoryRecord(
    finalInventory,
    'manifest.json',
    normalizedManifestBytes,
    manifestInfo.mode & 0o7777,
  );
  expect(normalizedRecomputed.sha256 === provenance.repaired.normalizedTreeSha256, 'normalized repaired-tree hash is not reproducible');
  await verifyRepairedOutput(
    stagingReal,
    finalInventory,
    contract,
    finalManifest,
    finalManifestBytes,
    full.remainingFailures,
    patchedCategories,
    plan,
  );

  console.log(
    `[repair-previews] Repaired and verified ${plan.length} previews; missing-image count is zero. ` +
      `Normalized tree SHA-256: ${normalizedInventory.sha256}`,
  );
  return {
    repaired: plan.length,
    sourceTreeSha256: beforeInventory.sha256,
    sampleTreeSha256: sampleInventory.sha256,
    normalizedTreeSha256: normalizedInventory.sha256,
    finalTreeSha256: finalInventory.sha256,
    previewPngs: provenance.repaired.previewPngs,
  };
}

function runCommand(label, executable, args) {
  console.log(`[repair-previews] Starting ${label}.`);
  return new Promise((resolveCommand, rejectCommand) => {
    const child = spawn(executable, args, {stdio: ['ignore', 'pipe', 'pipe']});
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', chunk => { stdout += chunk; });
    child.stderr.on('data', chunk => { stderr += chunk; });
    child.once('error', error => rejectCommand(new Error(`${label} could not start: ${error.message}`, {cause: error})));
    child.once('exit', (code, signal) => {
      if (stdout.trim()) console.log(stdout.trim());
      if (stderr.trim()) console.error(stderr.trim());
      if (signal) rejectCommand(new Error(`${label} was terminated by ${signal}.`));
      else if (code !== 0) rejectCommand(new Error(`${label} failed with exit code ${code}.`));
      else resolveCommand();
    });
  });
}

async function requireApfs(path) {
  expect(process.platform === 'darwin', 'APFS copy-on-write repair requires macOS; no full-copy fallback was attempted');
  // Darwin's public mount-type enum assigns APFS value 26. The subsequent
  // COPYFILE_CLONE_FORCE call is the authoritative clone-capability gate.
  const filesystem = await statfs(path);
  expect(filesystem.type === 26, `required sibling filesystem type is ${filesystem.type}, not APFS (26); no copy fallback was attempted`);
}

async function cloneTreeRequired(source, destination) {
  await requireApfs(dirname(destination));
  const helperRoot = await mkdtemp(join(tmpdir(), 'mrt-preview-repair-helper-'));
  try {
    const executable = join(helperRoot, 'darwin-clone-tree');
    await runCommand('clonefile helper compilation', '/usr/bin/xcrun', [
      'clang',
      '-std=c11',
      '-Wall',
      '-Wextra',
      '-Werror',
      join(scriptDirectory, 'darwin-clone-tree.c'),
      '-o',
      executable,
    ]);
    await runCommand('required APFS copy-on-write sibling clone', executable, [source, destination]);
  } finally {
    await rm(helperRoot, {recursive: true, force: false});
  }
}

async function publishDirectoryExclusive(staging, output) {
  const helperRoot = await mkdtemp(join(tmpdir(), 'mrt-preview-publish-helper-'));
  try {
    const executable = join(helperRoot, 'darwin-publish-directory');
    await runCommand('exclusive atomic-publish helper compilation', '/usr/bin/xcrun', [
      'clang',
      '-std=c11',
      '-Wall',
      '-Wextra',
      '-Werror',
      join(scriptDirectory, 'darwin-publish-directory.c'),
      '-o',
      executable,
    ]);
    await runCommand('exclusive atomic fresh-output publication', executable, [staging, output]);
  } finally {
    await rm(helperRoot, {recursive: true, force: false});
  }
}

/**
 * Runs the forced-clone transaction for an explicit contract. The CLI never
 * accepts a contract override; this export exists so small fixtures can test
 * publication and rollback without fabricating a 359,096-recipe corpus.
 */
export async function repairPreviewOverlayTransaction(
  {fullRoot, sampleRoot, outputRoot},
  contract = MEATBALLCRAFT_PREVIEW_REPAIR_CONTRACT,
) {
  validateContract(contract);
  const full = await requirePlainRoot(resolve(fullRoot), 'Completed full raw export');
  const sample = await requirePlainRoot(resolve(sampleRoot), 'Strict compatibility sample');
  const requestedOutput = resolve(outputRoot);
  const outputParent = await requirePlainRoot(dirname(requestedOutput), 'Repair output parent');
  const output = join(outputParent, basename(requestedOutput));
  expect((await pathKindNoFollow(output)) === 'missing', `fresh repair output already exists: ${output}`);
  expect(dirname(full) === outputParent, `full export and fresh output must be APFS siblings under ${outputParent}`);
  assertDisjoint(full, 'Full raw export', sample, 'compatibility sample');
  assertDisjoint(full, 'Full raw export', output, 'repair output');
  assertDisjoint(sample, 'Compatibility sample', output, 'repair output');
  const [fullDevice, outputDevice] = await Promise.all([stat(full), stat(outputParent)]);
  expect(fullDevice.dev === outputDevice.dev, 'full export and output are on different filesystems; copy-on-write is unavailable');
  await requireApfs(outputParent);

  const sourceManifestHash = sha256((await readJsonFile(full, 'manifest.json', 'source/manifest.json')).source);
  const staging = join(outputParent, `.${basename(output)}.repair-staging-${process.pid}-${randomUUID()}`);
  let result = null;
  let primaryError = null;
  try {
    expect((await pathKindNoFollow(staging)) === 'missing', `owned staging path unexpectedly exists: ${staging}`);
    console.log(`[repair-previews] Creating required copy-on-write sibling staging tree: ${staging}`);
    await cloneTreeRequired(full, staging);
    const clonedManifestHash = sha256((await readJsonFile(staging, 'manifest.json', 'cloned/manifest.json')).source);
    expect(clonedManifestHash === sourceManifestHash, 'source manifest changed while the sibling clone was created');
    result = await repairClonedPreviewTree({stagingRoot: staging, sampleRoot: sample, contract});
    expect((await pathKindNoFollow(output)) === 'missing', `fresh output appeared during repair; refusing to overwrite it: ${output}`);
    await publishDirectoryExclusive(staging, output);
    console.log(`[repair-previews] Atomically published the validated repair overlay at ${output}.`);
  } catch (error) {
    primaryError = error;
    console.error('[repair-previews] Repair did not publish; the full export and any existing paths were not modified.', error);
  }

  let cleanupError = null;
  if ((await pathKindNoFollow(staging)) !== 'missing') {
    try {
      await rm(staging, {recursive: true, force: false});
      console.log(`[repair-previews] Rolled back unpublished staging: ${staging}`);
    } catch (error) {
      cleanupError = error;
      console.error(`[repair-previews] Rollback cleanup failed; staging remains at ${staging}.`, error);
    }
  }
  if (primaryError && cleanupError) {
    throw new AggregateError(
      [primaryError, cleanupError],
      `Repair failed and rollback cleanup also failed; staging remains at ${staging}.`,
    );
  }
  if (primaryError) throw primaryError;
  if (cleanupError) throw cleanupError;
  return result;
}

/** Production entry point: strict MeatballCraft contract, forced APFS clone, validate-before-rename publication. */
export async function repairMissingRecipePreviews(options) {
  return repairPreviewOverlayTransaction(options, MEATBALLCRAFT_PREVIEW_REPAIR_CONTRACT);
}

function usage() {
  return (
    'Usage: node scripts/repair-missing-recipe-previews.mjs ' +
    '--full <completed-full-raw-export> --sample <strict-27-target-sample> --output <fresh-sibling-output>'
  );
}

function parseArguments(args) {
  const options = {fullRoot: null, sampleRoot: null, outputRoot: null, help: false};
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === '--help' || argument === '-h') options.help = true;
    else if (argument === '--full') options.fullRoot = args[++index];
    else if (argument === '--sample') options.sampleRoot = args[++index];
    else if (argument === '--output') options.outputRoot = args[++index];
    else throw new Error(`Unknown repair argument: ${argument}`);
  }
  if (!options.help && (!options.fullRoot || !options.sampleRoot || !options.outputRoot)) {
    throw new Error(`All of --full, --sample, and --output are required.\n${usage()}`);
  }
  return options;
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath && invokedPath === fileURLToPath(import.meta.url)) {
  try {
    const options = parseArguments(process.argv.slice(2));
    if (options.help) console.log(usage());
    else await repairMissingRecipePreviews(options);
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  }
}
