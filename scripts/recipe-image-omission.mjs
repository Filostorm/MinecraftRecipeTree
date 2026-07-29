import {basename, posix, relative, sep} from 'node:path';
import {readJsonDocument} from './export-data-utils.mjs';
import {normalizedLogicalRecipePngPath} from './recipe-image-inventory.mjs';

function relativeKey(root, path) {
  return relative(root, path).split(sep).join('/');
}

function sampleList(values) {
  return values.slice(0, 20).map(value => `- ${value}`).join('\n');
}

export async function collectDeclaredRecipePngOmissions(exportRoot, recipeMetadataPaths) {
  const keys = new Set();
  let references = 0;
  for (const sourcePath of [...recipeMetadataPaths].sort()) {
    if (basename(sourcePath) !== 'recipes.json') continue;
    const documentKey = relativeKey(exportRoot, sourcePath);
    const document = await readJsonDocument(sourcePath, documentKey);
    if (!Array.isArray(document)) {
      throw new Error(`${documentKey} must contain a recipe array before image omission.`);
    }
    const categoryDirectory = posix.dirname(documentKey);
    for (const [recipeIndex, recipe] of document.entries()) {
      if (!recipe || typeof recipe !== 'object' || Array.isArray(recipe) || !('img' in recipe)) {
        continue;
      }
      if (typeof recipe.img !== 'string' || recipe.img.length === 0) {
        throw new Error(
          `${documentKey}[${recipeIndex}].img must be a non-empty string before image omission.`,
        );
      }
      if (!recipe.img.endsWith('.png')) {
        throw new Error(
          `${documentKey}[${recipeIndex}].img must reference its original PNG for omission; ` +
            `received ${JSON.stringify(recipe.img)}. No optimized-image fallback was attempted.`,
        );
      }
      const assetKey = normalizedLogicalRecipePngPath(categoryDirectory, recipe.img);
      if (keys.has(assetKey)) {
        throw new Error(
          `Recipe-image omission requires a one-to-one reference/file inventory, but ${assetKey} ` +
            `is referenced more than once (duplicate at ${documentKey}[${recipeIndex}]).`,
        );
      }
      keys.add(assetKey);
      references += 1;
    }
  }
  return {keys, references};
}

export function compareExactRecipePngOmissionSet(exportRoot, pngPaths, declaredKeys) {
  const actualKeys = new Set(pngPaths.map(path => relativeKey(exportRoot, path)));
  const missing = [...declaredKeys].filter(key => !actualKeys.has(key)).sort();
  const unexpected = [...actualKeys].filter(key => !declaredKeys.has(key)).sort();
  return {actualKeys, missing, unexpected};
}

export function exactRecipePngOmissionError({missing, unexpected}) {
  const sections = [];
  if (missing.length > 0) {
    sections.push(
      `Missing declared recipe PNG omission target(s) (${missing.length}):\n${sampleList(missing)}`,
    );
  }
  if (unexpected.length > 0) {
    sections.push(
      `Unexpected PNG(s) outside the declared recipe omission set (${unexpected.length}):\n` +
        sampleList(unexpected),
    );
  }
  return new Error(
    'Recipe-image omission PNG inventory is not an exact match; publication was aborted.\n' +
      sections.join('\n'),
  );
}
