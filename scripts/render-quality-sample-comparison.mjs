import {randomUUID} from 'node:crypto';
import {lstat, mkdir, rename, rm} from 'node:fs/promises';
import {basename, dirname, isAbsolute, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import sharp from 'sharp';

const MACHINE_FRAME_ICON = 'icons/item/thermalexpansion/frame_d5baf740.png';
const CRAFTING_BASELINE = 'recipes/minecraft.crafting/r31319.png';
const CRAFTING_SAMPLE = 'recipes/minecraft.crafting/r0.png';
const BASIC_BASELINE = 'recipes/extendedcrafting_table_crafting_3x3/r131.png';
const BASIC_SAMPLE = 'recipes/extendedcrafting_table_crafting_3x3/r0.png';

export const QUALITY_COMPARISON_LAYOUT = Object.freeze({
  width: 1120,
  height: 1100,
  baselineX: 40,
  sampleX: 584,
  columnWidth: 496,
  item: Object.freeze({
    y: 184,
    width: 144,
    height: 144,
    baselineX: 216,
    sampleX: 760,
  }),
  crafting: Object.freeze({y: 420, width: 496, height: 248}),
  basicCrafting: Object.freeze({y: 764, width: 496, height: 248}),
});

const SOURCE_ASSETS = Object.freeze([
  Object.freeze({
    key: 'baselineItem',
    root: 'baseline',
    relativePath: MACHINE_FRAME_ICON,
    label: 'baseline Machine Frame item render',
    expectedWidth: 16,
    expectedHeight: 16,
    outputWidth: QUALITY_COMPARISON_LAYOUT.item.width,
    outputHeight: QUALITY_COMPARISON_LAYOUT.item.height,
    left: QUALITY_COMPARISON_LAYOUT.item.baselineX,
    top: QUALITY_COMPARISON_LAYOUT.item.y,
  }),
  Object.freeze({
    key: 'sampleItem',
    root: 'sample',
    relativePath: MACHINE_FRAME_ICON,
    label: 'sample Machine Frame item render',
    expectedWidth: 48,
    expectedHeight: 48,
    outputWidth: QUALITY_COMPARISON_LAYOUT.item.width,
    outputHeight: QUALITY_COMPARISON_LAYOUT.item.height,
    left: QUALITY_COMPARISON_LAYOUT.item.sampleX,
    top: QUALITY_COMPARISON_LAYOUT.item.y,
  }),
  Object.freeze({
    key: 'baselineCrafting',
    root: 'baseline',
    relativePath: CRAFTING_BASELINE,
    label: 'baseline Crafting JEI preview for crafttweaker:ct_shaped-557966710',
    expectedWidth: 124,
    expectedHeight: 62,
    outputWidth: QUALITY_COMPARISON_LAYOUT.crafting.width,
    outputHeight: QUALITY_COMPARISON_LAYOUT.crafting.height,
    left: QUALITY_COMPARISON_LAYOUT.baselineX,
    top: QUALITY_COMPARISON_LAYOUT.crafting.y,
  }),
  Object.freeze({
    key: 'sampleCrafting',
    root: 'sample',
    relativePath: CRAFTING_SAMPLE,
    label: 'sample Crafting JEI preview exported as r0',
    expectedWidth: 248,
    expectedHeight: 124,
    outputWidth: QUALITY_COMPARISON_LAYOUT.crafting.width,
    outputHeight: QUALITY_COMPARISON_LAYOUT.crafting.height,
    left: QUALITY_COMPARISON_LAYOUT.sampleX,
    top: QUALITY_COMPARISON_LAYOUT.crafting.y,
  }),
  Object.freeze({
    key: 'baselineBasic',
    root: 'baseline',
    relativePath: BASIC_BASELINE,
    label: 'baseline Basic Crafting JEI preview source #131',
    expectedWidth: 124,
    expectedHeight: 62,
    outputWidth: QUALITY_COMPARISON_LAYOUT.basicCrafting.width,
    outputHeight: QUALITY_COMPARISON_LAYOUT.basicCrafting.height,
    left: QUALITY_COMPARISON_LAYOUT.baselineX,
    top: QUALITY_COMPARISON_LAYOUT.basicCrafting.y,
  }),
  Object.freeze({
    key: 'sampleBasic',
    root: 'sample',
    relativePath: BASIC_SAMPLE,
    label: 'sample Basic Crafting JEI preview exported as r0',
    expectedWidth: 248,
    expectedHeight: 124,
    outputWidth: QUALITY_COMPARISON_LAYOUT.basicCrafting.width,
    outputHeight: QUALITY_COMPARISON_LAYOUT.basicCrafting.height,
    left: QUALITY_COMPARISON_LAYOUT.sampleX,
    top: QUALITY_COMPARISON_LAYOUT.basicCrafting.y,
  }),
]);

function parseArguments(args) {
  const options = {baseline: null, sample: null, output: null, showHelp: false};
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === '--help' || argument === '-h') {
      options.showHelp = true;
      continue;
    }
    const key =
      argument === '--baseline'
        ? 'baseline'
        : argument === '--sample'
          ? 'sample'
          : argument === '--output'
            ? 'output'
            : null;
    if (key === null) {
      throw new Error(`Unknown quality-comparison argument: ${argument}`);
    }
    if (options[key] !== null) {
      throw new Error(`Quality-comparison argument ${argument} was provided more than once.`);
    }
    const value = args[++index];
    if (!value || value.startsWith('--')) {
      throw new Error(`${argument} requires a path.`);
    }
    options[key] = resolve(value);
  }
  if (!options.showHelp) {
    for (const key of ['baseline', 'sample', 'output']) {
      if (options[key] === null) {
        throw new Error(`Quality comparison requires --${key} <path>.`);
      }
    }
  }
  return options;
}

function isPathInside(root, candidate) {
  const path = relative(root, candidate);
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..' && !isAbsolute(path));
}

async function requirePlainDirectory(path, label) {
  let info;
  try {
    info = await lstat(path);
  } catch (error) {
    if (error?.code === 'ENOENT') {
      throw new Error(`Required ${label} directory is missing: ${path}`, {cause: error});
    }
    throw new Error(`Required ${label} directory could not be inspected: ${path}`, {cause: error});
  }
  if (info.isSymbolicLink() || !info.isDirectory()) {
    throw new Error(`Required ${label} must be a plain directory: ${path}`);
  }
}

async function requirePng(path, asset) {
  let info;
  try {
    info = await lstat(path);
  } catch (error) {
    if (error?.code === 'ENOENT') {
      throw new Error(`Required ${asset.label} is missing: ${path}`, {cause: error});
    }
    throw new Error(`Required ${asset.label} could not be inspected: ${path}`, {cause: error});
  }
  if (info.isSymbolicLink() || !info.isFile()) {
    throw new Error(`Required ${asset.label} must be a plain PNG file: ${path}`);
  }

  let metadata;
  try {
    metadata = await sharp(path, {failOn: 'error'}).metadata();
  } catch (error) {
    throw new Error(`Required ${asset.label} could not be decoded: ${path}`, {cause: error});
  }
  if (metadata.format !== 'png') {
    throw new Error(
      `Required ${asset.label} must be PNG; decoded ${metadata.format ?? 'an unknown format'}: ${path}`,
    );
  }
  if ((metadata.pages ?? 1) !== 1) {
    throw new Error(
      `Required ${asset.label} must contain exactly one image page; decoded ${metadata.pages} pages: ${path}`,
    );
  }
  if (metadata.width !== asset.expectedWidth || metadata.height !== asset.expectedHeight) {
    throw new Error(
      `Required ${asset.label} must be exactly ${asset.expectedWidth}×${asset.expectedHeight}; ` +
        `decoded ${metadata.width ?? 'unknown'}×${metadata.height ?? 'unknown'}: ${path}`,
    );
  }
  if (
    asset.outputWidth % asset.expectedWidth !== 0 ||
    asset.outputHeight % asset.expectedHeight !== 0 ||
    asset.outputWidth / asset.expectedWidth !== asset.outputHeight / asset.expectedHeight
  ) {
    throw new Error(
      `Internal comparison layout would fractionally scale ${asset.label}; ` +
        `${asset.expectedWidth}×${asset.expectedHeight} cannot map to ` +
        `${asset.outputWidth}×${asset.outputHeight}.`,
    );
  }
}

function comparisonBackdrop() {
  const layout = QUALITY_COMPARISON_LAYOUT;
  return Buffer.from(`
    <svg xmlns="http://www.w3.org/2000/svg" width="${layout.width}" height="${layout.height}"
         viewBox="0 0 ${layout.width} ${layout.height}" shape-rendering="crispEdges">
      <defs>
        <pattern id="checker" width="24" height="24" patternUnits="userSpaceOnUse">
          <rect width="24" height="24" fill="#293548"/>
          <rect width="12" height="12" fill="#344258"/>
          <rect x="12" y="12" width="12" height="12" fill="#344258"/>
        </pattern>
      </defs>
      <rect width="1120" height="1100" fill="#0b1020"/>
      <rect x="24" y="94" width="528" height="966" rx="12" fill="#151d2b" stroke="#354158" stroke-width="2"/>
      <rect x="568" y="94" width="528" height="966" rx="12" fill="#151d2b" stroke="#47785b" stroke-width="2"/>
      <rect x="216" y="184" width="144" height="144" fill="url(#checker)" stroke="#718096" stroke-width="2"/>
      <rect x="760" y="184" width="144" height="144" fill="url(#checker)" stroke="#5bc583" stroke-width="2"/>
      <rect x="40" y="420" width="496" height="248" fill="#111827" stroke="#718096" stroke-width="2"/>
      <rect x="584" y="420" width="496" height="248" fill="#111827" stroke="#5bc583" stroke-width="2"/>
      <rect x="40" y="764" width="496" height="248" fill="#111827" stroke="#718096" stroke-width="2"/>
      <rect x="584" y="764" width="496" height="248" fill="#111827" stroke="#5bc583" stroke-width="2"/>
      <g fill="#f7fafc" font-family="Arial, Helvetica, sans-serif">
        <text x="40" y="43" font-size="30" font-weight="700">MeatballCraft render-quality mini test</text>
        <text x="40" y="73" font-size="16" fill="#aab6c8">Nearest-neighbor integer display scaling · source pixels are never smoothed</text>
        <text x="288" y="128" text-anchor="middle" font-size="22" font-weight="700">Baseline export · scale 1</text>
        <text x="832" y="128" text-anchor="middle" font-size="22" font-weight="700" fill="#79d99b">High-resolution sample · 3× / 2×</text>
        <text x="40" y="168" font-size="18" font-weight="700">Machine Frame item render</text>
        <text x="288" y="354" text-anchor="middle" font-size="15" fill="#aab6c8">16×16 source → 144×144 display (9×)</text>
        <text x="832" y="354" text-anchor="middle" font-size="15" fill="#a8e6bd">48×48 source → 144×144 display (3×)</text>
        <text x="40" y="404" font-size="18" font-weight="700">JEI Crafting · stable recipeId ct_shaped-557966710</text>
        <text x="288" y="693" text-anchor="middle" font-size="15" fill="#aab6c8">124×62 source → 496×248 display (4×)</text>
        <text x="832" y="693" text-anchor="middle" font-size="15" fill="#a8e6bd">248×124 source → 496×248 display (2×)</text>
        <text x="40" y="748" font-size="18" font-weight="700">JEI Basic Crafting · source #131</text>
        <text x="288" y="1037" text-anchor="middle" font-size="15" fill="#aab6c8">124×62 source → 496×248 display (4×)</text>
        <text x="832" y="1037" text-anchor="middle" font-size="15" fill="#a8e6bd">248×124 source → 496×248 display (2×)</text>
      </g>
    </svg>
  `);
}

async function nearestNeighborBuffer(path, asset) {
  try {
    return await sharp(path, {failOn: 'error'})
      .resize(asset.outputWidth, asset.outputHeight, {
        fit: 'fill',
        kernel: sharp.kernel.nearest,
      })
      .png({compressionLevel: 9, adaptiveFiltering: false, palette: false})
      .toBuffer();
  } catch (error) {
    throw new Error(`Could not integer-scale ${asset.label}: ${path}`, {cause: error});
  }
}

export async function renderQualitySampleComparison({baseline, sample, output}) {
  const roots = {baseline: resolve(baseline), sample: resolve(sample)};
  const outputPath = resolve(output);
  if (roots.baseline === roots.sample) {
    throw new Error('Baseline and sample roots must be different directories.');
  }
  if (!outputPath.toLowerCase().endsWith('.png')) {
    throw new Error(`Quality-comparison output must end in .png: ${outputPath}`);
  }
  for (const [label, root] of Object.entries(roots)) {
    await requirePlainDirectory(root, `${label} export`);
    if (isPathInside(root, outputPath)) {
      throw new Error(
        `Quality-comparison output must be outside the ${label} export so validation is not ` +
          `contaminated: ${outputPath}`,
      );
    }
  }

  const resolvedAssets = SOURCE_ASSETS.map(asset => ({
    ...asset,
    path: resolve(roots[asset.root], asset.relativePath),
  }));
  for (const asset of resolvedAssets) {
    if (!isPathInside(roots[asset.root], asset.path)) {
      throw new Error(`Required ${asset.label} resolves outside its export root: ${asset.path}`);
    }
    await requirePng(asset.path, asset);
  }

  const composites = await Promise.all(
    resolvedAssets.map(async asset => ({
      input: await nearestNeighborBuffer(asset.path, asset),
      left: asset.left,
      top: asset.top,
      blend: 'over',
    })),
  );

  await mkdir(dirname(outputPath), {recursive: true});
  const temporary = resolve(
    dirname(outputPath),
    `.${basename(outputPath)}.staging-${randomUUID()}.png`,
  );
  try {
    await sharp(comparisonBackdrop())
      .composite(composites)
      .png({compressionLevel: 9, adaptiveFiltering: false, palette: false})
      .toFile(temporary);
    const outputInfo = await sharp(temporary, {failOn: 'error'}).metadata();
    if (
      outputInfo.format !== 'png' ||
      outputInfo.width !== QUALITY_COMPARISON_LAYOUT.width ||
      outputInfo.height !== QUALITY_COMPARISON_LAYOUT.height
    ) {
      throw new Error(
        `Rendered comparison is ${outputInfo.format ?? 'unknown'} ` +
          `${outputInfo.width ?? 'unknown'}×${outputInfo.height ?? 'unknown'}; expected PNG ` +
          `${QUALITY_COMPARISON_LAYOUT.width}×${QUALITY_COMPARISON_LAYOUT.height}.`,
      );
    }
    await rename(temporary, outputPath);
  } catch (error) {
    await rm(temporary, {force: true});
    throw error;
  }

  const result = {
    output: outputPath,
    width: QUALITY_COMPARISON_LAYOUT.width,
    height: QUALITY_COMPARISON_LAYOUT.height,
    sources: resolvedAssets.map(asset => ({
      label: asset.label,
      path: asset.path,
      width: asset.expectedWidth,
      height: asset.expectedHeight,
      displayScale: asset.outputWidth / asset.expectedWidth,
    })),
  };
  console.info(
    `Rendered lossless pixel-art comparison ${result.width}×${result.height} to ${result.output}`,
  );
  return result;
}

const invokedAsScript =
  process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url));
if (invokedAsScript) {
  let options;
  try {
    options = parseArguments(process.argv.slice(2));
    if (options.showHelp) {
      console.log(
        'Usage: node scripts/render-quality-sample-comparison.mjs ' +
          '--baseline <scale-1-export> --sample <quality-sample-export> --output <comparison.png>',
      );
    } else {
      await renderQualitySampleComparison(options);
    }
  } catch (error) {
    console.error('Quality comparison render failed:', error);
    process.exitCode = 1;
  }
}
