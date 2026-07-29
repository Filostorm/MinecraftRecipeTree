import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {
  MEATBALLCRAFT_RECIPE_STAGES,
  MEATBALLCRAFT_STAGE_COMPATIBILITY_PUBLICATION_ID,
} from '../src/data/recipeStages.ts';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_OUTPUT = path.resolve(
  SCRIPT_DIR,
  '../src/data/meatballcraftRecipeStageIndex.generated.ts',
);

function optionValue(argv, name) {
  const index = argv.indexOf(name);
  if (index < 0 || index === argv.length - 1 || argv[index + 1].startsWith('--')) {
    throw new Error(`${name} requires a value.`);
  }
  return argv[index + 1];
}

function parseOptions(argv) {
  const allowed = new Set(['--dataset', '--output']);
  for (let index = 0; index < argv.length; index += 2) {
    if (!allowed.has(argv[index])) {
      throw new Error(`Unsupported option ${JSON.stringify(argv[index])}.`);
    }
    if (index === argv.length - 1) {
      throw new Error(`${argv[index]} requires a value.`);
    }
  }
  const dataset = optionValue(argv, '--dataset');
  const outputIndex = argv.indexOf('--output');
  const output = outputIndex < 0 ? DEFAULT_OUTPUT : optionValue(argv, '--output');
  return {
    dataset: path.resolve(dataset),
    output: path.resolve(output),
  };
}

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch (error) {
    throw new Error(`Could not read validated JSON from ${filePath}.`, {cause: error});
  }
}

function requireDatasetIdentity(datasetRoot) {
  const manifest = readJson(path.join(datasetRoot, 'manifest.json'));
  if (manifest.publicationId !== MEATBALLCRAFT_STAGE_COMPATIBILITY_PUBLICATION_ID) {
    throw new Error(
      `Recipe-stage indexing requires MeatballCraft publication ` +
        `${MEATBALLCRAFT_STAGE_COMPATIBILITY_PUBLICATION_ID}; received ` +
        `${JSON.stringify(manifest.publicationId)}.`,
    );
  }
}

function requireCategories(datasetRoot) {
  const document = readJson(path.join(datasetRoot, 'categories.json'));
  if (
    !document ||
    typeof document !== 'object' ||
    !Array.isArray(document.categories) ||
    document.categories.some(
      category =>
        !category ||
        typeof category !== 'object' ||
        typeof category.dir !== 'string' ||
        !Number.isSafeInteger(category.count) ||
        category.count < 0,
    )
  ) {
    throw new Error('Dataset categories do not satisfy the expected export contract.');
  }
  return document.categories;
}

function recipeParts(datasetRoot, category) {
  const documentPath = path.join(datasetRoot, category.dir, 'recipes.json');
  const document = readJson(documentPath);
  if (Array.isArray(document)) {
    if (document.length !== category.count) {
      throw new Error(`${documentPath} contains ${document.length}, expected ${category.count}.`);
    }
    return [{start: 0, recipes: document}];
  }
  if (
    !document ||
    document.format !== 'mrt-sharded-json-v1' ||
    document.kind !== 'array' ||
    document.count !== category.count ||
    !Array.isArray(document.parts)
  ) {
    throw new Error(`${documentPath} is not a supported recipe document.`);
  }
  return document.parts.map(part => {
    if (
      !part ||
      typeof part.path !== 'string' ||
      !Number.isSafeInteger(part.start) ||
      part.start < 0 ||
      !Number.isSafeInteger(part.count) ||
      part.count < 0
    ) {
      throw new Error(`${documentPath} contains a malformed recipe shard descriptor.`);
    }
    const recipes = readJson(path.join(datasetRoot, part.path));
    if (!Array.isArray(recipes) || recipes.length !== part.count) {
      throw new Error(
        `${part.path} contains ${Array.isArray(recipes) ? recipes.length : 'non-array data'}, ` +
          `expected ${part.count}.`,
      );
    }
    return {start: part.start, recipes};
  });
}

function recipeOutputKeys(recipe, recipeId) {
  if (!Array.isArray(recipe.out)) {
    throw new Error(`Staged recipe ${recipeId} has no output slots.`);
  }
  const outputKeys = new Set();
  for (const slot of recipe.out) {
    if (!Array.isArray(slot)) {
      throw new Error(`Staged recipe ${recipeId} has a malformed output slot.`);
    }
    for (const entry of slot) {
      if (!Array.isArray(entry) || typeof entry[0] !== 'string' || entry[0].length === 0) {
        throw new Error(`Staged recipe ${recipeId} has a malformed output entry.`);
      }
      outputKeys.add(entry[0]);
    }
  }
  if (outputKeys.size === 0) {
    throw new Error(`Staged recipe ${recipeId} has no indexed output items.`);
  }
  return [...outputKeys].sort();
}

function buildIndex(datasetRoot) {
  requireDatasetIdentity(datasetRoot);
  const categories = requireCategories(datasetRoot);
  const missing = new Set(Object.keys(MEATBALLCRAFT_RECIPE_STAGES));
  const entries = new Map();

  for (let categoryIndex = 0; categoryIndex < categories.length && missing.size > 0; categoryIndex++) {
    const category = categories[categoryIndex];
    for (const part of recipeParts(datasetRoot, category)) {
      for (let offset = 0; offset < part.recipes.length; offset++) {
        const recipe = part.recipes[offset];
        if (!recipe || typeof recipe !== 'object' || !missing.has(recipe.id)) continue;
        if (entries.has(recipe.id)) {
          throw new Error(`Staged recipe ID ${recipe.id} appeared more than once.`);
        }
        entries.set(recipe.id, {
          ref: [categoryIndex, part.start + offset],
          outputKeys: recipeOutputKeys(recipe, recipe.id),
        });
        missing.delete(recipe.id);
      }
    }
  }

  if (missing.size > 0) {
    throw new Error(
      `Dataset omitted ${missing.size} staged recipe IDs: ${[...missing].sort().join(', ')}`,
    );
  }
  return [...entries.entries()].sort(([left], [right]) => left.localeCompare(right));
}

function render(entries) {
  const lines = [
    '// Generated by scripts/generate-meatballcraft-recipe-stage-index.mjs.',
    '// Do not edit by hand; regenerate from the immutable publication named below.',
    '',
    'export const MEATBALLCRAFT_RECIPE_STAGE_INDEX = {',
  ];
  for (const [recipeId, entry] of entries) {
    lines.push(
      `  ${JSON.stringify(recipeId)}: {ref: ${JSON.stringify(entry.ref)}, ` +
        `outputKeys: ${JSON.stringify(entry.outputKeys)}},`,
    );
  }
  lines.push('} as const;', '');
  return lines.join('\n');
}

const options = parseOptions(process.argv.slice(2));
const entries = buildIndex(options.dataset);
fs.writeFileSync(options.output, render(entries), 'utf8');
console.info('Generated the MeatballCraft RecipeStages high-level index.', {
  output: options.output,
  recipeCount: entries.length,
});
