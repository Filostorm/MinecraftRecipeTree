import {availableParallelism} from 'node:os';
import {readFile, stat, unlink, writeFile} from 'node:fs/promises';
import {extname, join, posix, relative, resolve, sep} from 'node:path';
import sharp from 'sharp';
import {
  assertRequiredExportDocuments,
  collectFiles,
  optionalDirectory,
  requireDirectory,
} from './export-data-utils.mjs';
import {
  collectDeclaredRecipePngOmissions,
  compareExactRecipePngOmissionSet,
  exactRecipePngOmissionError,
} from './recipe-image-omission.mjs';

const defaultExportRoot = join(process.cwd(), 'public', 'exports');

function parseArguments(args) {
  let exportRoot = defaultExportRoot;
  let rootSeen = false;
  let omitRecipeImages = false;
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
    } else if (argument === '--omit-recipe-images') {
      omitRecipeImages = true;
    } else if (!argument.startsWith('-') && !rootSeen) {
      exportRoot = argument;
      rootSeen = true;
    } else {
      throw new Error(`Unknown image-optimization argument: ${argument}`);
    }
  }
  return {exportRoot: resolve(exportRoot), omitRecipeImages, showHelp};
}

const {exportRoot, omitRecipeImages, showHelp} = parseArguments(process.argv.slice(2));
if (showHelp) {
  console.log(
    'Usage: node scripts/optimize-export-assets.mjs [--root <directory>] ' +
      '[--omit-recipe-images]',
  );
  process.exit(0);
}
const requiredImageRoots = [
  ['icons', 'item icon images'],
  ['recipes', 'recipe images'],
];
const optionalImageRoots = [['mobs', 'mob images']];

await assertRequiredExportDocuments(exportRoot);

const imageRoots = [];
for (const [name, label] of requiredImageRoots) {
  const path = join(exportRoot, name);
  await requireDirectory(path, label);
  imageRoots.push(path);
}
for (const [name, label] of optionalImageRoots) {
  const path = join(exportRoot, name);
  if (await optionalDirectory(path, label)) imageRoots.push(path);
}

function relativeKey(root, path) {
  return relative(root, path).split(sep).join('/');
}

function referencedImageKey(value, documentKey, convertedImageKeys, omittedRecipeImageKeys) {
  if (convertedImageKeys.has(value) || omittedRecipeImageKeys.has(value)) return value;
  if (documentKey.startsWith('recipes/')) {
    const candidate = posix.join(posix.dirname(documentKey), value);
    if (convertedImageKeys.has(candidate) || omittedRecipeImageKeys.has(candidate)) {
      return candidate;
    }
  }
  return null;
}

function rewritePngReferences(
  value,
  documentKey,
  convertedImageKeys,
  omittedRecipeImageKeys,
) {
  if (typeof value === 'string') {
    if (!value.endsWith('.png')) return value;
    const assetKey = referencedImageKey(
      value,
      documentKey,
      convertedImageKeys,
      omittedRecipeImageKeys,
    );
    if (!assetKey || omittedRecipeImageKeys.has(assetKey)) return value;
    return convertedImageKeys.has(assetKey) ? `${value.slice(0, -4)}.webp` : value;
  }
  if (Array.isArray(value)) {
    for (let index = 0; index < value.length; index += 1) {
      value[index] = rewritePngReferences(
        value[index],
        documentKey,
        convertedImageKeys,
        omittedRecipeImageKeys,
      );
    }
    return value;
  }
  if (value && typeof value === 'object') {
    for (const key of Object.keys(value)) {
      value[key] = rewritePngReferences(
        value[key],
        documentKey,
        convertedImageKeys,
        omittedRecipeImageKeys,
      );
    }
  }
  return value;
}

const imageFiles = (await Promise.all(imageRoots.map(collectFiles))).flat();
const pngImages = imageFiles.filter(path => extname(path).toLowerCase() === '.png');
const existingWebpImages = imageFiles.filter(path => extname(path).toLowerCase() === '.webp');
const recipeMetadata = imageFiles.filter(
  path =>
    relativeKey(exportRoot, path).startsWith('recipes/') &&
    extname(path).toLowerCase() === '.json',
);
const omission = omitRecipeImages
  ? await collectDeclaredRecipePngOmissions(exportRoot, recipeMetadata)
  : {keys: new Set(), references: 0};
if (omitRecipeImages) {
  const preservedRecipePngs = pngImages.filter(path =>
    omission.keys.has(relativeKey(exportRoot, path)),
  );
  const comparison = compareExactRecipePngOmissionSet(
    exportRoot,
    preservedRecipePngs,
    omission.keys,
  );
  if (comparison.missing.length > 0) {
    const error = exactRecipePngOmissionError({missing: comparison.missing, unexpected: []});
    console.error('Omission-aware optimization found missing declared recipe PNGs.', error);
    throw error;
  }
  console.info(
    `Omission-aware optimization will preserve ${omission.references} declared recipe PNG(s) ` +
      'for exhaustive validation and the separate R2 preview sidecar; no WebP encoding fallback ' +
      'will be attempted for them.',
  );
}
const convertedPngImages = omitRecipeImages
  ? pngImages.filter(path => !omission.keys.has(relativeKey(exportRoot, path)))
  : pngImages;
const convertedImageKeys = new Set(
  convertedPngImages.map(path => relativeKey(exportRoot, path)),
);

if (pngImages.length === 0 && existingWebpImages.length === 0) {
  throw new Error('Image optimization failed: required export image directories contain no PNG or WebP assets.');
}
if (pngImages.length === 0) {
  console.info(
    `Image optimization found no PNG files; ${existingWebpImages.length} WebP assets are already optimized.`,
  );
}

let inputBytes = 0;
let outputBytes = 0;
let completed = 0;
const concurrency = Math.max(1, Math.min(8, availableParallelism()));
let nextIndex = 0;

async function convertNext() {
  while (nextIndex < convertedPngImages.length) {
    const index = nextIndex++;
    const pngPath = convertedPngImages[index];
    const webpPath = `${pngPath.slice(0, -4)}.webp`;
    try {
      inputBytes += (await stat(pngPath)).size;
      const image = sharp(pngPath, {failOn: 'error'});
      const metadata = await image.metadata();
      if ((metadata.pages ?? 1) !== 1) {
        throw new Error(
          `Animated or multi-page PNG input has ${metadata.pages} pages; exactly one page is ` +
            'required before lossless WebP conversion.',
        );
      }
      await image.webp({lossless: true, effort: 6}).toFile(webpPath);
      outputBytes += (await stat(webpPath)).size;
      await unlink(pngPath);
      completed += 1;
      if (completed % 5000 === 0 || completed === convertedPngImages.length) {
        console.log(`Optimized ${completed}/${convertedPngImages.length} retained PNG images.`);
      }
    } catch (error) {
      console.error(`Lossless image conversion failed for ${pngPath}`, error);
      throw error;
    }
  }
}

await Promise.all(Array.from({length: concurrency}, convertNext));

const jsonFiles = (await collectFiles(exportRoot)).filter(
  path => extname(path).toLowerCase() === '.json',
);
let rewrittenJsonFiles = 0;
for (const jsonPath of jsonFiles) {
  try {
    const source = await readFile(jsonPath, 'utf8');
    if (!source.includes('.png')) continue;
    const value = JSON.parse(source);
    const documentKey = relativeKey(exportRoot, jsonPath);
    await writeFile(
      jsonPath,
      `${JSON.stringify(
        rewritePngReferences(
          value,
          documentKey,
          convertedImageKeys,
          omission.keys,
        ),
      )}\n`,
    );
    rewrittenJsonFiles += 1;
  } catch (error) {
    console.error(`Asset reference rewrite failed for ${jsonPath}`, error);
    throw error;
  }
}

const savedMiB = (inputBytes - outputBytes) / (1024 * 1024);
console.log(
  `Optimized ${convertedPngImages.length} retained image(s) losslessly and rewrote ` +
    `${rewrittenJsonFiles} JSON file(s): saved ${savedMiB.toFixed(1)} MiB.` +
    (omitRecipeImages
      ? ` Preserved ${omission.references} declared original recipe PNG(s) without redundant ` +
        'WebP encoding.'
      : ''),
);
