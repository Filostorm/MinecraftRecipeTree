import {createHash} from 'node:crypto';
import {availableParallelism} from 'node:os';
import {
  mkdir,
  open,
  readFile,
  rename,
  rm,
  stat,
  writeFile,
} from 'node:fs/promises';
import {basename, dirname, extname, join, posix, relative, resolve, sep} from 'node:path';
import {
  collectFiles,
  optionalDirectory,
  pathKind,
  readJsonDocument,
  requireDirectory,
} from './export-data-utils.mjs';
import {validateExportData} from './validate-export-data.mjs';
import {
  EXPORT_QUALITY_PROFILE_IDS,
  resolveQualityProfile,
} from './export-quality-policy.mjs';
import {writePublicationId} from './publication-id.mjs';
import {
  MAX_SHARD_BYTES,
  SHARDED_JSON_FORMAT,
  shardArrayDocument,
  shardObjectDocument,
} from './sharded-documents.mjs';
import {
  MAX_PACK_BYTES,
  PACKED_IMAGE_FORMAT,
  packedImagePath,
} from './packed-assets.mjs';
import {
  collectDeclaredRecipePngOmissions,
  compareExactRecipePngOmissionSet,
  exactRecipePngOmissionError,
} from './recipe-image-omission.mjs';

const defaultExportRoot = join(process.cwd(), 'public', 'exports');

function usage() {
  return (
    'Usage: node scripts/pack-export-assets.mjs [--root <directory>] ' +
    `[--profile <${EXPORT_QUALITY_PROFILE_IDS.join('|')}>] [--omit-recipe-images]`
  );
}

function parseArguments(args) {
  let exportRoot = defaultExportRoot;
  let profile = null;
  let omitRecipeImages = false;
  let rootSeen = false;
  let showHelp = false;
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === '--help' || argument === '-h') {
      showHelp = true;
    } else if (argument === '--root') {
      const value = args[++index];
      if (!value) throw new Error('--root requires a directory path.');
      exportRoot = value;
      rootSeen = true;
    } else if (argument === '--profile') {
      const value = args[++index];
      if (!value) throw new Error('--profile requires a profile name.');
      profile = resolveQualityProfile(value);
    } else if (argument === '--omit-recipe-images') {
      omitRecipeImages = true;
    } else if (!argument.startsWith('-') && !rootSeen) {
      exportRoot = argument;
      rootSeen = true;
    } else {
      throw new Error(`Unknown asset-packing argument: ${argument}`);
    }
  }
  return {exportRoot: resolve(exportRoot), profile, omitRecipeImages, showHelp};
}

function relativeKey(root, path) {
  return relative(root, path).split(sep).join('/');
}

async function writeJson(path, value, label, {bounded = true} = {}) {
  const bytes = Buffer.from(`${JSON.stringify(value)}\n`, 'utf8');
  if (bounded && bytes.length > MAX_SHARD_BYTES) {
    throw new Error(
      `${label} is ${bytes.length} bytes after packing, above the ` +
        `${MAX_SHARD_BYTES}-byte web-document limit.`,
    );
  }
  await mkdir(dirname(path), {recursive: true});
  await writeFile(path, bytes, {flag: 'wx'});
}

function rewritePackedImageReferences(value, documentKey, coordinateByAsset, rewrittenAssets) {
  if (typeof value === 'string') {
    let assetKey = coordinateByAsset.has(value) ? value : null;
    if (!assetKey && documentKey.startsWith('recipes/')) {
      const candidate = posix.join(posix.dirname(documentKey), value);
      if (coordinateByAsset.has(candidate)) assetKey = candidate;
    }
    if (!assetKey) return value;
    rewrittenAssets.add(assetKey);
    return coordinateByAsset.get(assetKey);
  }
  if (Array.isArray(value)) {
    return value.map(entry =>
      rewritePackedImageReferences(entry, documentKey, coordinateByAsset, rewrittenAssets),
    );
  }
  if (value && typeof value === 'object') {
    const rewritten = {};
    for (const [key, entry] of Object.entries(value)) {
      rewritten[key] = rewritePackedImageReferences(
        entry,
        documentKey,
        coordinateByAsset,
        rewrittenAssets,
      );
    }
    return rewritten;
  }
  return value;
}

async function moveIfPresent(move) {
  const liveKind = await pathKind(move.live);
  if (move.requiredKind && liveKind !== move.requiredKind) {
    throw new Error(`${move.name} is ${liveKind}; expected ${move.requiredKind}: ${move.live}`);
  }
  if (
    !move.requiredKind &&
    liveKind !== 'missing' &&
    liveKind !== move.allowedKind
  ) {
    throw new Error(`${move.name} is ${liveKind}; expected ${move.allowedKind}: ${move.live}`);
  }
  if (liveKind !== 'missing') {
    await rename(move.live, move.backup);
    move.moved = true;
  }
  const replacementKind = await pathKind(move.replacement);
  if (replacementKind !== 'missing') {
    if (replacementKind !== move.allowedKind) {
      throw new Error(
        `${move.name} replacement is ${replacementKind}; expected ${move.allowedKind}: ` +
          move.replacement,
      );
    }
    await rename(move.replacement, move.live);
    move.published = true;
  } else if (move.replacementRequired) {
    throw new Error(`${move.name} replacement is missing: ${move.replacement}`);
  }
}

async function rollbackMove(move, rollbackErrors) {
  if (move.published) {
    try {
      await rm(move.live, {recursive: true, force: true});
      move.published = false;
    } catch (error) {
      rollbackErrors.push(error);
      console.error(`Packing rollback could not remove new ${move.name}.`, error);
    }
  }
  if (move.moved) {
    try {
      await rename(move.backup, move.live);
      move.moved = false;
    } catch (error) {
      rollbackErrors.push(error);
      console.error(`Packing rollback could not restore ${move.name}.`, error);
    }
  }
}

const {exportRoot, profile, omitRecipeImages, showHelp} = parseArguments(process.argv.slice(2));
if (showHelp) {
  console.log(usage());
  process.exit(0);
}

const validationOptions = profile ? {profile} : {};
const exportParent = dirname(exportRoot);
const iconsRoot = join(exportRoot, 'icons');
const recipesRoot = join(exportRoot, 'recipes');
const mobsRoot = join(exportRoot, 'mobs');
const packRoot = join(exportRoot, 'assets');
const dataRoot = join(exportRoot, 'data');
const legacyAssetIndexPath = join(exportRoot, 'assets-index.json');

const stagingRoot = join(exportParent, `.exports-packing-${process.pid}`);
const stagingPackRoot = join(stagingRoot, 'assets');
const stagingDocumentsRoot = join(stagingRoot, 'documents');
const backupRoot = join(exportParent, `.exports-packing-backup-${process.pid}`);

await requireDirectory(iconsRoot, 'item icon images');
await requireDirectory(recipesRoot, 'recipe images');
const hasMobImages = await optionalDirectory(mobsRoot, 'mob images');

// This is the corruption gate: every raw image is fully decoded and the
// recipe/index graph is proven complete before any source path is moved.
const preflightValidation = await validateExportData(exportRoot, {
  assetMode: 'raw',
  computeRecipeImageInventory: omitRecipeImages,
  ...validationOptions,
});
const omittedRecipeImageInventory = omitRecipeImages
  ? preflightValidation.recipeImageInventory
  : null;
if (omitRecipeImages && !omittedRecipeImageInventory) {
  throw new Error(
    'Recipe-image omission preflight did not produce its required decoded-pixel inventory.',
  );
}

const imageRoots = [iconsRoot, recipesRoot, ...(hasMobImages ? [mobsRoot] : [])];
const filesByRoot = new Map();
for (const root of imageRoots) filesByRoot.set(root, await collectFiles(root));
const imageFiles = [...filesByRoot.values()].flat();
const pngImages = imageFiles.filter(path => extname(path).toLowerCase() === '.png');
const recipeMetadata = filesByRoot
  .get(recipesRoot)
  .filter(path => extname(path).toLowerCase() === '.json')
  .sort();
const omission = omitRecipeImages
  ? await collectDeclaredRecipePngOmissions(exportRoot, recipeMetadata)
  : null;
if (omitRecipeImages) {
  const comparison = compareExactRecipePngOmissionSet(exportRoot, pngImages, omission.keys);
  if (comparison.missing.length > 0 || comparison.unexpected.length > 0) {
    const error = exactRecipePngOmissionError(comparison);
    console.error('Asset packing rejected the omission-aware PNG inventory.', error);
    throw error;
  }
  console.info(
    `Asset packing matched all ${omission.references} declared recipe-image reference(s) to ` +
      'an exact one-to-one set of original PNG files; retained assets are WebP.',
  );
} else if (pngImages.length > 0) {
  throw new Error(
    `Asset packing refused ${pngImages.length} unoptimized PNG image(s); run npm run optimize-data first.`,
  );
}

const unexpectedFiles = [];
for (const [root, files] of filesByRoot) {
  for (const path of files) {
    const extension = extname(path).toLowerCase();
    const allowed =
      extension === '.webp' ||
      (root === recipesRoot &&
        (extension === '.json' || (omitRecipeImages && extension === '.png')));
    if (!allowed) unexpectedFiles.push(path);
  }
}
if (unexpectedFiles.length > 0) {
  console.error('Asset packing found files it cannot safely classify.', unexpectedFiles.slice(0, 20));
  throw new Error(
    `Asset packing refused ${unexpectedFiles.length} unexpected file(s) under raw image roots.`,
  );
}

const allImages = imageFiles
  .filter(path => extname(path).toLowerCase() === '.webp')
  .sort();
if (allImages.length === 0) {
  throw new Error('Asset packing failed: no WebP export assets were found.');
}

const omittedRecipeImageKeys = omission?.keys ?? new Set();
const omittedRecipeImageReferences = omission?.references ?? 0;
let omittedRecipeImageBytes = 0;
if (omitRecipeImages) {
  let nextOmittedIndex = 0;
  const omittedPaths = pngImages;
  async function accountNextOmittedPng() {
    while (nextOmittedIndex < omittedPaths.length) {
      const path = omittedPaths[nextOmittedIndex++];
      const size = (await stat(path)).size;
      if (!Number.isSafeInteger(size) || size <= 0) {
        throw new Error(`Omitted original PNG has an invalid byte size (${size}): ${path}`);
      }
      omittedRecipeImageBytes += size;
    }
  }
  const accountingConcurrency = Math.max(1, Math.min(16, availableParallelism()));
  await Promise.all(Array.from({length: accountingConcurrency}, accountNextOmittedPng));
}
const images = allImages;
if (images.length === 0) {
  throw new Error('Asset packing failed: the publication policy omitted every WebP export asset.');
}

for (const path of [stagingRoot, backupRoot]) {
  if ((await pathKind(path)) !== 'missing') {
    throw new Error(`Packing recovery path already exists; refusing to overwrite it: ${path}`);
  }
}
await mkdir(stagingPackRoot, {recursive: true});
await mkdir(stagingDocumentsRoot, {recursive: true});

const coordinateByAsset = new Map();
const deduplicationCandidates = new Map();
let packNumber = -1;
let packOffset = 0;
let packHandle;
let packedBytes = 0;
let uniqueImages = 0;
let duplicateImages = 0;
let duplicateBytesSaved = 0;
let completed = 0;

async function openNextPack() {
  await packHandle?.close();
  packNumber += 1;
  packOffset = 0;
  const name = `pack-${String(packNumber).padStart(3, '0')}.bin`;
  packHandle = await open(join(stagingPackRoot, name), 'wx');
}

async function writeAll(handle, bytes, position) {
  let written = 0;
  while (written < bytes.length) {
    const result = await handle.write(bytes, written, bytes.length - written, position + written);
    if (result.bytesWritten <= 0) throw new Error('Pack file write made no forward progress.');
    written += result.bytesWritten;
  }
}

try {
  await openNextPack();
  for (const imagePath of images) {
    const size = (await stat(imagePath)).size;
    if (size <= 0) throw new Error(`Cannot pack an empty image asset: ${imagePath}`);
    if (size > MAX_PACK_BYTES) {
      throw new Error(
        `Image asset exceeds the ${MAX_PACK_BYTES}-byte pack limit (${size} bytes): ${imagePath}`,
      );
    }

    const key = relativeKey(exportRoot, imagePath);
    const bytes = await readFile(imagePath);
    if (bytes.length !== size) {
      throw new Error(
        `Image asset changed while it was being packed (expected ${size} bytes, read ` +
          `${bytes.length}): ${imagePath}`,
      );
    }
    const digest = createHash('sha256').update(bytes).digest('hex');
    const candidateKey = `${bytes.length}:${digest}`;
    const candidate = deduplicationCandidates.get(candidateKey);
    if (candidate) {
      const candidateBytes = await readFile(candidate.sourcePath);
      if (!bytes.equals(candidateBytes)) {
        throw new Error(
          `SHA-256 deduplication collision for ${imagePath} and ${candidate.sourcePath}; ` +
            'the length and digest matched but their bytes differed, so packing was aborted.',
        );
      }
      coordinateByAsset.set(key, candidate.coordinate);
      duplicateImages += 1;
      duplicateBytesSaved += bytes.length;
      completed += 1;
      if (completed % 5000 === 0 || completed === images.length) {
        console.log(`Packed ${completed}/${images.length} WebP assets.`);
      }
      continue;
    }

    if (packOffset > 0 && packOffset + size > MAX_PACK_BYTES) await openNextPack();
    await writeAll(packHandle, bytes, packOffset);
    const coordinate = packedImagePath(packNumber, packOffset, bytes.length);
    coordinateByAsset.set(key, coordinate);
    deduplicationCandidates.set(candidateKey, {sourcePath: imagePath, coordinate});
    packOffset += bytes.length;
    packedBytes += bytes.length;
    uniqueImages += 1;
    completed += 1;
    if (completed % 5000 === 0 || completed === images.length) {
      console.log(`Packed ${completed}/${images.length} WebP assets.`);
    }
  }
  await packHandle?.close();
  packHandle = undefined;

  const rewrittenAssets = new Set();
  const manifest = await readJsonDocument(join(exportRoot, 'manifest.json'), 'manifest.json');
  const packedManifest = {
    ...manifest,
    web: {
      format: 2,
      packedImages: PACKED_IMAGE_FORMAT,
      maxPackBytes: MAX_PACK_BYTES,
      shardedJson: SHARDED_JSON_FORMAT,
      maxShardBytes: MAX_SHARD_BYTES,
      recipeImages: omitRecipeImages
        ? {
            mode: 'omitted',
            reason: 'hosting-archive-budget',
            references: omittedRecipeImageReferences,
            files: omittedRecipeImageKeys.size,
            encoding: 'png',
            bytes: omittedRecipeImageBytes,
            inventory: omittedRecipeImageInventory,
          }
        : {mode: 'included'},
    },
  };
  delete packedManifest.publicationId;
  await writeJson(
    join(stagingDocumentsRoot, 'manifest.json'),
    packedManifest,
    'packed manifest.json',
  );

  const itemsDocument = await readJsonDocument(join(exportRoot, 'items.json'), 'items.json');
  const rewrittenItems = rewritePackedImageReferences(
    itemsDocument.items,
    'items.json',
    coordinateByAsset,
    rewrittenAssets,
  );
  const packedItems = await shardArrayDocument(
    rewrittenItems,
    stagingDocumentsRoot,
    'data/items',
    'items.json items',
  );
  await writeJson(
    join(stagingDocumentsRoot, 'items.json'),
    Array.isArray(packedItems) ? {...itemsDocument, items: packedItems} : packedItems,
    'packed items.json',
  );

  const categoriesDocument = await readJsonDocument(
    join(exportRoot, 'categories.json'),
    'categories.json',
  );
  const packedCategories = rewritePackedImageReferences(
    categoriesDocument,
    'categories.json',
    coordinateByAsset,
    rewrittenAssets,
  );
  await writeJson(
    join(stagingDocumentsRoot, 'categories.json'),
    packedCategories,
    'packed categories.json',
  );

  const indexDocument = await readJsonDocument(join(exportRoot, 'index.json'), 'index.json');
  const packedIndex = await shardObjectDocument(
    indexDocument,
    stagingDocumentsRoot,
    'data/index',
    'index.json',
  );
  await writeJson(
    join(stagingDocumentsRoot, 'index.json'),
    packedIndex,
    'packed index.json',
  );

  if ((await pathKind(join(exportRoot, 'mobs.json'))) === 'file') {
    const mobsDocument = await readJsonDocument(join(exportRoot, 'mobs.json'), 'mobs.json');
    const packedMobs = rewritePackedImageReferences(
      mobsDocument,
      'mobs.json',
      coordinateByAsset,
      rewrittenAssets,
    );
    await writeJson(join(stagingDocumentsRoot, 'mobs.json'), packedMobs, 'packed mobs.json');
  }

  let strippedRecipeImageReferences = 0;
  for (const sourcePath of recipeMetadata) {
    const documentKey = relativeKey(exportRoot, sourcePath);
    let document = await readJsonDocument(sourcePath, documentKey);
    if (omitRecipeImages && basename(sourcePath) === 'recipes.json') {
      if (!Array.isArray(document)) {
        throw new Error(`${documentKey} must contain a recipe array before image omission.`);
      }
      document = document.map(recipe => {
        if (!recipe || typeof recipe !== 'object' || Array.isArray(recipe) || !('img' in recipe)) {
          return recipe;
        }
        const {img: _image, w: _width, h: _height, ...structuredRecipe} = recipe;
        strippedRecipeImageReferences += 1;
        return structuredRecipe;
      });
    }
    const rewritten = rewritePackedImageReferences(
      document,
      documentKey,
      coordinateByAsset,
      rewrittenAssets,
    );
    let packedDocument = rewritten;
    if (basename(sourcePath) === 'recipes.json') {
      if (!Array.isArray(rewritten)) {
        throw new Error(`${documentKey} must contain a recipe array before sharding.`);
      }
      packedDocument = await shardArrayDocument(
        rewritten,
        stagingDocumentsRoot,
        posix.join(posix.dirname(documentKey), 'parts'),
        documentKey,
      );
    }
    await writeJson(
      join(stagingDocumentsRoot, ...documentKey.split('/')),
      packedDocument,
      `packed ${documentKey}`,
    );
  }

  if (
    omitRecipeImages &&
    strippedRecipeImageReferences !== omittedRecipeImageReferences
  ) {
    throw new Error(
      `Recipe-image omission stripped ${strippedRecipeImageReferences} references after ` +
        `preflighting ${omittedRecipeImageReferences}; publication was aborted.`,
    );
  }

  if (rewrittenAssets.size !== coordinateByAsset.size) {
    const missing = [...coordinateByAsset.keys()].filter(key => !rewrittenAssets.has(key));
    console.error('Packed metadata did not consume every image coordinate.', missing.slice(0, 20));
    throw new Error(
      `Packed metadata omitted ${coordinateByAsset.size - rewrittenAssets.size} image coordinate(s).`,
    );
  }
  console.log(
    `Rewrote ${rewrittenAssets.size} image reference(s) to validated coordinate URLs.`,
  );
} catch (error) {
  console.error('Export packing failed before publication.', error);
  await packHandle?.close().catch(closeError =>
    console.error('The failed staging pack could not be closed cleanly.', closeError),
  );
  await rm(stagingRoot, {recursive: true, force: true});
  throw error;
} finally {
  await packHandle?.close();
}

await mkdir(backupRoot);
const moves = [
  {
    name: 'asset packs',
    live: packRoot,
    backup: join(backupRoot, 'assets'),
    replacement: stagingPackRoot,
    allowedKind: 'directory',
    replacementRequired: true,
  },
  {
    name: 'raw icons',
    live: iconsRoot,
    backup: join(backupRoot, 'icons'),
    replacement: join(stagingRoot, 'no-icons'),
    allowedKind: 'directory',
    requiredKind: 'directory',
  },
  {
    name: 'recipe documents and images',
    live: recipesRoot,
    backup: join(backupRoot, 'recipes'),
    replacement: join(stagingDocumentsRoot, 'recipes'),
    allowedKind: 'directory',
    requiredKind: 'directory',
    replacementRequired: true,
  },
  ...(hasMobImages
    ? [
        {
          name: 'raw mob images',
          live: mobsRoot,
          backup: join(backupRoot, 'mobs'),
          replacement: join(stagingRoot, 'no-mobs'),
          allowedKind: 'directory',
          requiredKind: 'directory',
        },
      ]
    : []),
  {
    name: 'sharded data',
    live: dataRoot,
    backup: join(backupRoot, 'data'),
    replacement: join(stagingDocumentsRoot, 'data'),
    allowedKind: 'directory',
  },
  {
    name: 'legacy asset index',
    live: legacyAssetIndexPath,
    backup: join(backupRoot, 'assets-index.json'),
    replacement: join(stagingRoot, 'no-assets-index.json'),
    allowedKind: 'file',
  },
  ...['manifest.json', 'items.json', 'categories.json', 'index.json', 'mobs.json'].map(name => ({
    name,
    live: join(exportRoot, name),
    backup: join(backupRoot, name),
    replacement: join(stagingDocumentsRoot, name),
    allowedKind: 'file',
    requiredKind: name === 'mobs.json' ? undefined : 'file',
    replacementRequired: name !== 'mobs.json',
  })),
].map(move => ({...move, moved: false, published: false}));

let publicationAttempted = false;
try {
  for (const move of moves) await moveIfPresent(move);
  publicationAttempted = true;
  await writePublicationId(exportRoot);
  await validateExportData(exportRoot, {
    ...validationOptions,
    requirePublicationId: true,
    verifyPublicationId: true,
  });
} catch (error) {
  console.error('Packed publication or validation failed; restoring the raw export.', error);
  const rollbackErrors = [];
  for (const move of [...moves].reverse()) await rollbackMove(move, rollbackErrors);
  if (rollbackErrors.length > 0) {
    console.error(
      `Packing rollback retained recovery data after ${rollbackErrors.length} error(s): ${backupRoot}`,
    );
    throw new AggregateError(
      [error, ...rollbackErrors],
      'Packed publication failed and its rollback was incomplete.',
    );
  }
  await rm(stagingRoot, {recursive: true, force: true});
  await rm(backupRoot, {recursive: true, force: true});
  throw error;
}

try {
  await rm(backupRoot, {recursive: true, force: false});
  await rm(stagingRoot, {recursive: true, force: true});
} catch (error) {
  console.error(
    `The packed publication${publicationAttempted ? ' is valid, but' : ''} recovery cleanup failed.`,
    error,
  );
  throw error;
}

console.log(
  `Packed and validated ${images.length} WebP assets (${uniqueImages} unique, ` +
    `${duplicateImages} duplicate) into ${packNumber + 1} one-MiB files ` +
    `(${(packedBytes / (1024 * 1024)).toFixed(1)} MiB); exact-content deduplication saved ` +
    `${duplicateBytesSaved} bytes (${(duplicateBytesSaved / (1024 * 1024)).toFixed(1)} MiB) ` +
    'without an asset index.',
);
if (omitRecipeImages) {
  console.warn(
    `Publication policy omitted ${omittedRecipeImageReferences} composite recipe-image ` +
      `reference(s) across ${omittedRecipeImageKeys.size} exhaustively validated original PNG ` +
      `file(s) (${omittedRecipeImageBytes} PNG bytes) without redundant WebP encoding; ` +
      'structured recipe data and packed item/category assets remain.',
  );
}
