import {availableParallelism} from 'node:os';
import {readFile, stat, unlink, writeFile} from 'node:fs/promises';
import {extname, join, resolve} from 'node:path';
import sharp from 'sharp';
import {
  assertRequiredExportDocuments,
  collectFiles,
  optionalDirectory,
  requireDirectory,
} from './export-data-utils.mjs';

const defaultExportRoot = join(process.cwd(), 'public', 'exports');

function parseArguments(args) {
  let exportRoot = defaultExportRoot;
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
    } else if (!argument.startsWith('-') && !rootSeen) {
      exportRoot = argument;
      rootSeen = true;
    } else {
      throw new Error(`Unknown image-optimization argument: ${argument}`);
    }
  }
  return {exportRoot: resolve(exportRoot), showHelp};
}

const {exportRoot, showHelp} = parseArguments(process.argv.slice(2));
if (showHelp) {
  console.log('Usage: node scripts/optimize-export-assets.mjs [--root <directory>]');
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

function rewritePngReferences(value) {
  if (typeof value === 'string') {
    return value.endsWith('.png') ? `${value.slice(0, -4)}.webp` : value;
  }
  if (Array.isArray(value)) {
    for (let index = 0; index < value.length; index += 1) {
      value[index] = rewritePngReferences(value[index]);
    }
    return value;
  }
  if (value && typeof value === 'object') {
    for (const key of Object.keys(value)) value[key] = rewritePngReferences(value[key]);
  }
  return value;
}

const imageFiles = (await Promise.all(imageRoots.map(collectFiles))).flat();
const pngImages = imageFiles.filter(path => extname(path).toLowerCase() === '.png');
const existingWebpImages = imageFiles.filter(path => extname(path).toLowerCase() === '.webp');

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
  while (nextIndex < pngImages.length) {
    const index = nextIndex++;
    const pngPath = pngImages[index];
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
      if (completed % 5000 === 0 || completed === pngImages.length) {
        console.log(`Optimized ${completed}/${pngImages.length} PNG images.`);
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
    await writeFile(jsonPath, `${JSON.stringify(rewritePngReferences(value))}\n`);
    rewrittenJsonFiles += 1;
  } catch (error) {
    console.error(`Asset reference rewrite failed for ${jsonPath}`, error);
    throw error;
  }
}

const savedMiB = (inputBytes - outputBytes) / (1024 * 1024);
console.log(
  `Optimized ${pngImages.length} images losslessly and rewrote ${rewrittenJsonFiles} JSON files: ` +
    `saved ${savedMiB.toFixed(1)} MiB.`,
);
