import {createHash} from 'node:crypto';
import {posix} from 'node:path';

export const RECIPE_IMAGE_INVENTORY_FORMAT = 'mrt-recipe-image-inventory-v1';
export const RECIPE_IMAGE_INVENTORY_SHA256_PATTERN = /^[a-f0-9]{64}$/;
export const RECIPE_IMAGE_RGBA_PIPELINE = 'rgba8-srgb-transparent-rgb-zero-v1';

function isSafeRelativePath(value) {
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

function assertNonNegativeInteger(value, label) {
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new Error(`${label} must be a non-negative safe integer.`);
  }
}

function assertPositiveInteger(value, label) {
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new Error(`${label} must be a positive safe integer.`);
  }
}

function updateFramed(hash, value) {
  const bytes = Buffer.isBuffer(value) ? value : Buffer.from(String(value), 'utf8');
  const length = Buffer.allocUnsafe(8);
  length.writeBigUInt64BE(BigInt(bytes.length));
  hash.update(length).update(bytes);
}

export function normalizedLogicalRecipePngPath(categoryDirectory, imageReference) {
  if (!isSafeRelativePath(categoryDirectory)) {
    throw new Error(
      `Recipe-image inventory category directory is unsafe: ${JSON.stringify(categoryDirectory)}.`,
    );
  }
  if (!isSafeRelativePath(imageReference)) {
    throw new Error(
      `Recipe-image inventory image reference is unsafe: ${JSON.stringify(imageReference)}.`,
    );
  }
  let pngReference;
  if (imageReference.endsWith('.png')) pngReference = imageReference;
  else if (imageReference.endsWith('.webp')) {
    pngReference = `${imageReference.slice(0, -5)}.png`;
  } else {
    throw new Error(
      'Recipe-image inventory references must end in .png or optimized .webp: ' +
        `${JSON.stringify(imageReference)}.`,
    );
  }
  const logicalPath = posix.join(categoryDirectory, pngReference);
  if (!isSafeRelativePath(logicalPath)) {
    throw new Error(`Normalized recipe-image inventory path is unsafe: ${logicalPath}.`);
  }
  return logicalPath;
}

export function decodedRgbaSha256(width, height, pixels) {
  assertPositiveInteger(width, 'Decoded RGBA width');
  assertPositiveInteger(height, 'Decoded RGBA height');
  if (!Buffer.isBuffer(pixels) || pixels.length !== width * height * 4) {
    throw new Error(
      `Decoded RGBA pixels must contain exactly ${width * height * 4} bytes for ` +
        `${width}×${height}.`,
    );
  }
  let canonicalPixels = pixels;
  for (let offset = 0; offset < pixels.length; offset += 4) {
    if (
      pixels[offset + 3] === 0 &&
      (pixels[offset] !== 0 || pixels[offset + 1] !== 0 || pixels[offset + 2] !== 0)
    ) {
      canonicalPixels = Buffer.from(pixels);
      for (let inner = offset; inner < canonicalPixels.length; inner += 4) {
        if (canonicalPixels[inner + 3] === 0) {
          canonicalPixels[inner] = 0;
          canonicalPixels[inner + 1] = 0;
          canonicalPixels[inner + 2] = 0;
        }
      }
      break;
    }
  }
  const hash = createHash('sha256');
  hash.update(`${RECIPE_IMAGE_RGBA_PIPELINE}\0`);
  updateFramed(hash, width);
  updateFramed(hash, height);
  updateFramed(hash, canonicalPixels);
  return hash.digest('hex');
}

export function createRecipeImageInventory() {
  const hash = createHash('sha256');
  hash.update(`${RECIPE_IMAGE_INVENTORY_FORMAT}\0`);
  let entries = 0;
  let previews = 0;
  let missing = 0;
  let finalized = false;
  let nextCategoryIndex = 0;
  let currentCategory = null;

  function addEntryHeader(event, {categoryIndex, categoryId, recipeIndex}) {
    if (finalized) throw new Error('Recipe-image inventory has already been finalized.');
    if (!currentCategory) {
      throw new Error('Recipe-image inventory entries require beginCategory first.');
    }
    if (
      categoryIndex !== currentCategory.categoryIndex ||
      categoryId !== currentCategory.categoryId
    ) {
      throw new Error('Recipe-image inventory entry does not match the current category.');
    }
    if (recipeIndex !== currentCategory.nextRecipeIndex) {
      throw new Error(
        `Recipe-image inventory recipeIndex must be contiguous at ` +
          `${currentCategory.nextRecipeIndex}; received ${JSON.stringify(recipeIndex)}.`,
      );
    }
    if (recipeIndex >= currentCategory.recipeCount) {
      throw new Error('Recipe-image inventory entry exceeds the current category recipe count.');
    }
    updateFramed(hash, event);
    updateFramed(hash, categoryIndex);
    updateFramed(hash, categoryId);
    updateFramed(hash, recipeIndex);
    currentCategory.nextRecipeIndex += 1;
    entries += 1;
  }

  function assertCurrentCategoryComplete() {
    if (
      currentCategory &&
      currentCategory.nextRecipeIndex !== currentCategory.recipeCount
    ) {
      throw new Error(
        `Recipe-image inventory category ${JSON.stringify(currentCategory.categoryId)} contains ` +
          `${currentCategory.nextRecipeIndex}/${currentCategory.recipeCount} ordered entries.`,
      );
    }
  }

  return {
    beginCategory({categoryIndex, categoryId, recipeCount}) {
      if (finalized) throw new Error('Recipe-image inventory has already been finalized.');
      assertCurrentCategoryComplete();
      if (categoryIndex !== nextCategoryIndex) {
        throw new Error(
          `Recipe-image inventory categoryIndex must be contiguous at ${nextCategoryIndex}; ` +
            `received ${JSON.stringify(categoryIndex)}.`,
        );
      }
      if (typeof categoryId !== 'string' || categoryId.length === 0) {
        throw new Error('Recipe-image inventory categoryId must be a non-empty string.');
      }
      assertNonNegativeInteger(recipeCount, 'Recipe-image inventory category recipeCount');
      updateFramed(hash, 'category');
      updateFramed(hash, categoryIndex);
      updateFramed(hash, categoryId);
      updateFramed(hash, recipeCount);
      currentCategory = {categoryIndex, categoryId, recipeCount, nextRecipeIndex: 0};
      nextCategoryIndex += 1;
    },

    addMissing(entry) {
      addEntryHeader('missing', entry);
      missing += 1;
    },

    addPreview({
      categoryIndex,
      categoryId,
      recipeIndex,
      logicalPngPath,
      declaredWidth,
      declaredHeight,
      decodedWidth,
      decodedHeight,
      rgbaSha256,
    }) {
      if (!isSafeRelativePath(logicalPngPath) || !logicalPngPath.endsWith('.png')) {
        throw new Error(
          `Recipe-image inventory logical PNG path is invalid: ${JSON.stringify(logicalPngPath)}.`,
        );
      }
      assertPositiveInteger(declaredWidth, 'Recipe-image inventory declaredWidth');
      assertPositiveInteger(declaredHeight, 'Recipe-image inventory declaredHeight');
      assertPositiveInteger(decodedWidth, 'Recipe-image inventory decodedWidth');
      assertPositiveInteger(decodedHeight, 'Recipe-image inventory decodedHeight');
      if (!RECIPE_IMAGE_INVENTORY_SHA256_PATTERN.test(rgbaSha256)) {
        throw new Error('Recipe-image inventory RGBA digest must be a lowercase SHA-256 digest.');
      }
      addEntryHeader('preview', {categoryIndex, categoryId, recipeIndex});
      updateFramed(hash, logicalPngPath);
      updateFramed(hash, declaredWidth);
      updateFramed(hash, declaredHeight);
      updateFramed(hash, decodedWidth);
      updateFramed(hash, decodedHeight);
      updateFramed(hash, rgbaSha256);
      previews += 1;
    },

    finish() {
      if (finalized) throw new Error('Recipe-image inventory has already been finalized.');
      assertCurrentCategoryComplete();
      finalized = true;
      updateFramed(hash, 'inventory-footer');
      updateFramed(hash, entries);
      updateFramed(hash, previews);
      updateFramed(hash, missing);
      return {
        format: RECIPE_IMAGE_INVENTORY_FORMAT,
        sha256: hash.digest('hex'),
        entries,
        previews,
        missing,
      };
    },
  };
}

export function requireRecipeImageInventory(
  value,
  label = 'Recipe-image inventory',
  expectedEntries,
) {
  if (
    !value ||
    typeof value !== 'object' ||
    Array.isArray(value) ||
    Object.keys(value).length !== 5 ||
    value.format !== RECIPE_IMAGE_INVENTORY_FORMAT ||
    !RECIPE_IMAGE_INVENTORY_SHA256_PATTERN.test(value.sha256 ?? '') ||
    !Number.isSafeInteger(value.entries) ||
    value.entries < 0 ||
    !Number.isSafeInteger(value.previews) ||
    value.previews < 0 ||
    !Number.isSafeInteger(value.missing) ||
    value.missing < 0 ||
    value.entries !== value.previews + value.missing ||
    (expectedEntries !== undefined && value.entries !== expectedEntries)
  ) {
    throw new Error(
      `${label} must contain exactly format ${RECIPE_IMAGE_INVENTORY_FORMAT}, a lowercase ` +
        'SHA-256 digest, and non-negative entries/previews/missing counts satisfying ' +
        `entries = previews + missing${
          expectedEntries === undefined ? '' : ` = ${expectedEntries}`
        }.`,
    );
  }
  return value;
}
