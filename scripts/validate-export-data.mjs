import {availableParallelism} from 'node:os';
import {readFile, stat} from 'node:fs/promises';
import {extname, join, posix, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import sharp from 'sharp';
import {collectFiles, isRecord, pathKind, readJsonDocument} from './export-data-utils.mjs';
import {
  collectIconlessItemIds,
  EXPORT_QUALITY_PROFILE_IDS,
  exportQualityIssues,
  MULTIBLOCK_MADNESS_112_PROFILE,
  MULTIBLOCK_MADNESS_2_118_PROFILE,
  qualityProfileRequirementsFor,
  resolveQualityProfile,
} from './export-quality-policy.mjs';
import {
  EXPORTER_BUILD_EXPORT_PATH,
  parseExporterBuildIdentityBytes,
} from './exporter-artifact-provenance.mjs';
import {computePublicationId, PUBLICATION_ID_PATTERN} from './publication-id.mjs';
import {requirePackIdentity} from './pack-identity.mjs';
import {
  createRecipeImageInventory,
  decodedRgbaSha256,
  normalizedLogicalRecipePngPath,
  requireRecipeImageInventory,
} from './recipe-image-inventory.mjs';
import {
  MAX_PACK_BYTES,
  PACKED_IMAGE_FORMAT,
  packFileKey,
  packedImagePath,
  parsePackedImagePath,
} from './packed-assets.mjs';
import {
  MAX_SHARD_BYTES,
  SHARDED_JSON_FORMAT,
  isShardedDocument,
  readArrayDocument,
  readObjectDocument,
} from './sharded-documents.mjs';
import {
  GTNH_RECIPE_IMAGE_OMISSION_REASON,
  GTNH_STRUCTURED_DATA_ONLY_POLICY_ID,
  hasExactGtnhStructuredDataOnlyVisualAssets,
  usesStructuredDataOnlyPublication,
} from './visual-assets-rights-policy.mjs';

const defaultExportRoot = join(process.cwd(), 'public', 'exports');
const MAX_REPORTED_ERRORS = 100;
const SHARP_CONCURRENCY = Math.max(1, Math.min(2, availableParallelism()));
const MM2_MISSINGNO_RGBA_SHA256 = Object.freeze([
  '386c06ecfb9a401bf0abe1d92a04a23a06a424dd39e33df2c7dcae19bc6ec31a',
  '1e76e32423f2e15298af791e370c640b8e62dd7e36efc1cee260ba1430432b44',
]);

// Validation touches every image exactly once. Disabling libvips' operation
// cache prevents decoded pixels from accumulating across a six-figure export.
sharp.cache(false);
sharp.concurrency(SHARP_CONCURRENCY);

function relativeKey(root, path) {
  return relative(root, path).split(sep).join('/');
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

function countValue(value) {
  return Number.isSafeInteger(value) && value >= 0 ? value : null;
}

export async function validateExportData(exportRoot = defaultExportRoot, options = {}) {
  const root = resolve(exportRoot);
  const forceRawAssets = options.assetMode === 'raw';
  const allowLegacyRecipeImageAccounting =
    options.allowLegacyRecipeImageAccounting === true;
  const qualityProfile = resolveQualityProfile(options.profile);
  const qualityRequirements = qualityProfileRequirementsFor(qualityProfile);
  const profileRequiresStructuredDataOnly =
    usesStructuredDataOnlyPublication(qualityProfile);
  let computedRecipeImageInventory = null;
  const errors = [];
  let suppressedErrors = 0;
  const fail = message => {
    if (errors.length < MAX_REPORTED_ERRORS) errors.push(message);
    else suppressedErrors += 1;
  };

  if ((await pathKind(root)) !== 'directory') {
    throw new Error(`Export root is missing or is not a directory: ${root}`);
  }

  const allFiles = await collectFiles(root);
  const fileKeys = new Set(allFiles.map(path => relativeKey(root, path)));
  if (fileKeys.has(EXPORTER_BUILD_EXPORT_PATH)) {
    try {
      parseExporterBuildIdentityBytes(
        await readFile(join(root, EXPORTER_BUILD_EXPORT_PATH)),
        EXPORTER_BUILD_EXPORT_PATH,
      );
    } catch (error) {
      fail(
        `${EXPORTER_BUILD_EXPORT_PATH} failed its canonical identity contract: ${
          error instanceof Error ? error.message : String(error)
        }`,
      );
    }
  } else if (qualityRequirements?.requiresExporterBuildIdentity) {
    fail(
      `Export quality profile ${qualityProfile} requires root ${EXPORTER_BUILD_EXPORT_PATH} from its exact exporter JAR.`,
    );
  }
  const manifest = await readJsonDocument(join(root, 'manifest.json'), 'manifest.json');
  const itemsDoc = await readJsonDocument(join(root, 'items.json'), 'items.json');
  const categoriesDoc = await readJsonDocument(join(root, 'categories.json'), 'categories.json');
  const referencedShardFiles = new Set();
  const registerShardPaths = (paths, pattern, label) => {
    for (const path of paths) {
      referencedShardFiles.add(path);
      const matches = typeof pattern === 'function' ? pattern(path) : pattern.test(path);
      if (!matches) fail(`${label} references a shard outside its namespace: ${path}`);
    }
  };

  if (!isRecord(manifest)) fail('manifest.json must contain an object.');
  if (!Number.isSafeInteger(manifest?.format)) fail('manifest.format must be an integer.');
  if (
    typeof manifest?.generatedAt !== 'string' ||
    !manifest.generatedAt ||
    !Number.isFinite(Date.parse(manifest.generatedAt))
  ) {
    fail('manifest.generatedAt must be a non-empty, parseable timestamp.');
  }
  if (
    typeof manifest?.durationMs !== 'number' ||
    !Number.isFinite(manifest.durationMs) ||
    manifest.durationMs < 0
  ) {
    fail('manifest.durationMs must be a finite, non-negative number.');
  }
  if (typeof manifest?.minecraft !== 'string' || !manifest.minecraft) {
    fail('manifest.minecraft must be a non-empty string.');
  }
  if (manifest?.pack === undefined) {
    if (options.requirePackIdentity === true) {
      fail('manifest.pack is required for a new hosted publication.');
    } else {
      console.warn(
        '[validate-data] manifest.pack is absent. This legacy export remains readable, but the ' +
          'publisher will require a new identity-bearing export.',
      );
    }
  } else {
    try {
      requirePackIdentity(manifest.pack);
    } catch (error) {
      fail(error instanceof Error ? error.message : String(error));
    }
  }
  if (manifest?.aborted !== false) {
    fail('manifest.aborted must be false; partial exports cannot be published.');
  }
  if (!isRecord(manifest?.counts)) fail('manifest.counts must contain an object.');
  for (const countName of ['items', 'recipes', 'categories', 'mobs']) {
    if (countValue(manifest?.counts?.[countName]) === null) {
      fail(`manifest.counts.${countName} must be a non-negative safe integer.`);
    }
  }
  if (!isRecord(manifest?.settings)) {
    fail('manifest.settings must contain an object.');
  } else {
    for (const settingName of ['iconScale', 'recipeScale', 'mobCanvas']) {
      const setting = manifest.settings[settingName];
      if (!Number.isSafeInteger(setting) || setting <= 0) {
        fail(`manifest.settings.${settingName} must be a positive safe integer.`);
      }
    }
  }
  if (!isRecord(manifest?.mods)) {
    fail('manifest.mods must contain an object.');
  } else if (Object.values(manifest.mods).some(name => typeof name !== 'string')) {
    fail('manifest.mods values must all be strings.');
  }
  const publicationPolicy = manifest?.publicationPolicy;
  const exactStructuredDataOnlyVisualAssets =
    hasExactGtnhStructuredDataOnlyVisualAssets(manifest?.web?.visualAssets);
  const structuredDataOnly =
    !forceRawAssets &&
    publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_POLICY_ID &&
    exactStructuredDataOnlyVisualAssets;
  if (forceRawAssets && publicationPolicy !== undefined) {
    fail(
      'Raw exports must not declare manifest.publicationPolicy; publication rights transforms run only after exhaustive raw validation.',
    );
  }
  if (!forceRawAssets && profileRequiresStructuredDataOnly) {
    if (publicationPolicy !== GTNH_STRUCTURED_DATA_ONLY_POLICY_ID) {
      fail(
        `Export quality profile ${qualityProfile} requires manifest.publicationPolicy to be ` +
          `${GTNH_STRUCTURED_DATA_ONLY_POLICY_ID}.`,
      );
    }
    if (!exactStructuredDataOnlyVisualAssets) {
      fail(
        `Export quality profile ${qualityProfile} requires the exact ` +
          'manifest.web.visualAssets structured-data-only contract.',
      );
    }
  } else if (
    !forceRawAssets &&
    qualityProfile !== null &&
    (publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_POLICY_ID ||
      manifest?.web?.visualAssets?.policy === GTNH_STRUCTURED_DATA_ONLY_POLICY_ID)
  ) {
    fail(
      `${GTNH_STRUCTURED_DATA_ONLY_POLICY_ID} is reserved for export quality profile ` +
        'gtnh-1.7.10.',
    );
  }
  if (
    !forceRawAssets &&
    (publicationPolicy !== undefined || manifest?.web?.visualAssets !== undefined) &&
    !structuredDataOnly
  ) {
    fail(
      'manifest.publicationPolicy and manifest.web.visualAssets must form the exact supported structured-data-only contract.',
    );
  }

  const validateRecipeImageOmission = requireRightsPolicy => {
    try {
      const recipeImages = manifest?.web?.recipeImages;
      if (recipeImages?.mode !== 'omitted') {
        fail('manifest.web.recipeImages.mode must be omitted for this publication contract.');
        return;
      }
      const inventory = requireRecipeImageInventory(
        recipeImages.inventory,
        'manifest.web.recipeImages.inventory',
        manifest.counts?.recipes,
      );
      const currentPngAccounting = recipeImages.encoding === 'png';
      const explicitLegacyAccounting =
        !requireRightsPolicy &&
        recipeImages.encoding === undefined &&
        allowLegacyRecipeImageAccounting;
      if (
        countValue(recipeImages.references) === null ||
        countValue(recipeImages.files) === null ||
        countValue(recipeImages.bytes) === null ||
        (!currentPngAccounting && !explicitLegacyAccounting) ||
        inventory.previews !== recipeImages.references ||
        inventory.missing !== manifest.counts.recipes - recipeImages.references ||
        recipeImages.files !== recipeImages.references
      ) {
        fail(
          'manifest.web.recipeImages inventory/counts must satisfy previews = references = ' +
            'files, encoding = png, and missing = manifest.counts.recipes - references. ' +
            'A missing encoding field is accepted only by the explicit legacy-read validation mode.',
        );
      } else if (explicitLegacyAccounting) {
        console.warn(
          '[legacy-read] Explicit compatibility mode accepted the omitted-WebP byte ' +
            'accounting contract. This mode is only for validating the existing production ' +
            'dataset during zero-downtime migration; new imports and final publications must ' +
            'use encoding="png" and original PNG bytes.',
        );
      }
      if (
        requireRightsPolicy &&
        (recipeImages.policy !== GTNH_STRUCTURED_DATA_ONLY_POLICY_ID ||
          recipeImages.reason !== GTNH_RECIPE_IMAGE_OMISSION_REASON)
      ) {
        fail(
          'manifest.web.recipeImages must bind its omission accounting to the exact GTNH publication rights policy.',
        );
      }
    } catch (error) {
      fail(error.message);
    }
  };

  const coordinatePacked =
    !forceRawAssets && manifest?.web?.packedImages === PACKED_IMAGE_FORMAT;
  if (coordinatePacked) {
    if (manifest.web.format !== 2) {
      fail('manifest.web.format must be 2 for coordinate-packed exports.');
    }
    if (manifest.web.maxPackBytes !== MAX_PACK_BYTES) {
      fail(`manifest.web.maxPackBytes must be exactly ${MAX_PACK_BYTES}.`);
    }
    if (manifest.web.shardedJson !== SHARDED_JSON_FORMAT) {
      fail(`manifest.web.shardedJson must be ${SHARDED_JSON_FORMAT}.`);
    }
    if (manifest.web.maxShardBytes !== MAX_SHARD_BYTES) {
      fail(`manifest.web.maxShardBytes must be exactly ${MAX_SHARD_BYTES}.`);
    }
    if (manifest.web.recipeImages?.mode === 'omitted') validateRecipeImageOmission(false);
  } else if (structuredDataOnly) {
    if (manifest.web.format !== 2) {
      fail('manifest.web.format must be 2 for structured-data-only exports.');
    }
    if (manifest.web.shardedJson !== SHARDED_JSON_FORMAT) {
      fail(`manifest.web.shardedJson must be ${SHARDED_JSON_FORMAT}.`);
    }
    if (manifest.web.maxShardBytes !== MAX_SHARD_BYTES) {
      fail(`manifest.web.maxShardBytes must be exactly ${MAX_SHARD_BYTES}.`);
    }
    if (manifest.web.packedImages !== undefined || manifest.web.maxPackBytes !== undefined) {
      fail('Structured-data-only exports must not declare packed-image storage.');
    }
    validateRecipeImageOmission(true);
  } else if (!forceRawAssets && manifest?.web !== undefined) {
    fail(
      `manifest.web declares an unsupported packed-image format: ${String(
        manifest?.web?.packedImages,
      )}.`,
    );
  }
  const publicationId = manifest?.publicationId;
  const publicationRequired = options.requirePublicationId || options.verifyPublicationId;
  if (publicationId === undefined) {
    if (publicationRequired) fail('manifest.publicationId is required for a final published dataset.');
  } else if (typeof publicationId !== 'string' || !PUBLICATION_ID_PATTERN.test(publicationId)) {
    fail('manifest.publicationId must be a lowercase hexadecimal SHA-256 digest.');
  }
  let failureDiagnostics = null;
  if (manifest?.diagnostics !== undefined) {
    if (!isRecord(manifest.diagnostics)) {
      fail('manifest.diagnostics must contain an object when present.');
    } else {
      const failureEvents = countValue(manifest.diagnostics.failureEvents);
      const failureEventsOmitted = countValue(manifest.diagnostics.failureEventsOmitted);
      if (failureEvents === null) {
        fail('manifest.diagnostics.failureEvents must be a non-negative safe integer.');
      }
      if (failureEventsOmitted === null) {
        fail('manifest.diagnostics.failureEventsOmitted must be a non-negative safe integer.');
      }
      if (failureEvents !== null && failureEventsOmitted !== null) {
        failureDiagnostics = {failureEvents, failureEventsOmitted};
        if (failureEventsOmitted > failureEvents) {
          fail('manifest diagnostics omit more failure events than the exporter recorded.');
        }
      }
    }
  }
  if (!isRecord(categoriesDoc) || !Array.isArray(categoriesDoc.categories)) {
    fail('categories.json must contain an object with a categories array.');
  }

  let items = [];
  if (isRecord(itemsDoc) && Array.isArray(itemsDoc.items)) {
    items = itemsDoc.items;
  } else if (!forceRawAssets && isShardedDocument(itemsDoc, 'array')) {
    try {
      const resolvedItems = await readArrayDocument(root, itemsDoc, 'items.json');
      items = resolvedItems.value;
      registerShardPaths(
        resolvedItems.shardPaths,
        /^data\/items\/part-\d+\.json$/,
        'items.json',
      );
    } catch (error) {
      fail(`items.json shards could not be read: ${error.message}`);
    }
  } else {
    fail(
      forceRawAssets
        ? 'items.json must contain an object with an items array.'
        : 'items.json must contain an items array or a valid array shard descriptor.',
    );
  }
  const categories = Array.isArray(categoriesDoc?.categories) ? categoriesDoc.categories : [];
  const itemKeys = new Set();
  const itemKeyIds = new Map();
  const itemKeysById = [];
  const assetReferences = new Set();
  const expectedAssetDimensions = new Map();
  const itemIconIdentitiesByAssetPath = new Map();
  const iconScale = countValue(manifest?.settings?.iconScale);
  const recipeScale = countValue(manifest?.settings?.recipeScale);
  const mobCanvas = countValue(manifest?.settings?.mobCanvas);

  const referenceAsset = (assetPath, expected, location) => {
    assetReferences.add(assetPath);
    if (!expected) return;
    const previous = expectedAssetDimensions.get(assetPath);
    if (previous && (previous.width !== expected.width || previous.height !== expected.height)) {
      fail(
        `${location} expects ${assetPath} at ${expected.width}x${expected.height}, conflicting with ` +
          `${previous.width}x${previous.height}.`,
      );
      return;
    }
    expectedAssetDimensions.set(assetPath, expected);
  };

  for (const [itemIndex, item] of items.entries()) {
    if (!isRecord(item)) {
      fail(`items[${itemIndex}] must be an object.`);
      continue;
    }
    for (const field of ['k', 'id', 'n', 'm']) {
      if (typeof item[field] !== 'string' || !item[field]) {
        fail(`items[${itemIndex}].${field} must be a non-empty string.`);
      }
    }
    if (typeof item.k === 'string') {
      if (itemKeys.has(item.k)) fail(`Duplicate catalog key: ${item.k}`);
      else {
        itemKeyIds.set(item.k, itemKeyIds.size);
        itemKeysById.push(item.k);
        itemKeys.add(item.k);
      }
    }
    if (item.icon !== undefined) {
      if (structuredDataOnly) {
        fail(
          `items[${itemIndex}].icon is forbidden by ${GTNH_STRUCTURED_DATA_ONLY_POLICY_ID}.`,
        );
      } else if (safeRelativePath(item.icon)) {
        referenceAsset(
          item.icon,
          iconScale && iconScale > 0 ? {width: 16 * iconScale, height: 16 * iconScale} : null,
          `items[${itemIndex}]`,
        );
        itemIconIdentitiesByAssetPath.set(
          item.icon,
          `${typeof item.id === 'string' ? item.id : '(invalid id)'} ` +
            `(${typeof item.k === 'string' ? item.k : `items[${itemIndex}]`})`,
        );
      }
      else fail(`Unsafe or invalid item icon path at items[${itemIndex}]: ${String(item.icon)}`);
    }
  }

  const categoryIds = new Set();
  const categoryDirs = new Set();
  const recipeCounts = new Array(categories.length).fill(0);
  const recipeImageInventoryEntries = options.computeRecipeImageInventory ? [] : null;
  const recipeImageInventoryCategories = options.computeRecipeImageInventory ? [] : null;
  const recipeImageEntryIndexesByAssetKey = options.computeRecipeImageInventory
    ? new Map()
    : null;
  let totalRecipes = 0;
  let semanticErrorRecipes = 0;

  const validateSlots = (slots, location, role) => {
    if (slots === undefined) return;
    if (!Array.isArray(slots)) {
      fail(`${location} must be an array.`);
      return;
    }
    for (const [slotIndex, variants] of slots.entries()) {
      if (!Array.isArray(variants)) {
        fail(`${location}[${slotIndex}] must be an array.`);
        continue;
      }
      for (const [variantIndex, entry] of variants.entries()) {
        if (
          !Array.isArray(entry) ||
          (entry.length !== 2 && entry.length !== 3 && entry.length !== 4) ||
          typeof entry[0] !== 'string' ||
          typeof entry[1] !== 'number' ||
          !Number.isFinite(entry[1]) ||
          (entry.length === 3 &&
            (typeof entry[2] !== 'string' || !/^ore:\S+$/.test(entry[2]))) ||
          (entry.length === 4 &&
            !(
              (entry[2] === null ||
                (typeof entry[2] === 'string' && /^ore:\S+$/.test(entry[2]))) &&
              typeof entry[3] === 'number' &&
              Number.isFinite(entry[3]) &&
              entry[3] > 0 &&
              entry[3] < 1
            ))
        ) {
          fail(
            `${location}[${slotIndex}][${variantIndex}] must be ` +
              '[catalog key, amount, optional logical ingredient id, optional occurrence probability].',
          );
          continue;
        }
        if (entry.length === 4 && role === 'cat') {
          fail(
            `${location}[${slotIndex}][${variantIndex}] may declare an occurrence probability only in recipe.in or recipe.out.`,
          );
        }
        if (entry.length === 4 && manifest?.format !== 2) {
          fail(
            `${location}[${slotIndex}][${variantIndex}] uses the stochastic-occurrence tuple introduced by manifest format 2.`,
          );
        }
        if (!itemKeys.has(entry[0])) {
          fail(`${location}[${slotIndex}][${variantIndex}] references unknown key ${entry[0]}.`);
        }
      }
      const logicalIdentities = variants.map(entry => entry?.[2]);
      const exportedIdentityCount = logicalIdentities.filter(Boolean).length;
      if (
        exportedIdentityCount > 0 &&
        (exportedIdentityCount !== variants.length || new Set(logicalIdentities).size !== 1)
      ) {
        fail(`${location}[${slotIndex}] must use one logical ingredient id for every variant.`);
      }
      const probabilities = variants.map(entry =>
        Array.isArray(entry) && entry.length === 4 ? entry[3] : undefined,
      );
      const exportedProbabilityCount = probabilities.filter(
        probability => probability !== undefined,
      ).length;
      if (
        exportedProbabilityCount > 0 &&
        (exportedProbabilityCount !== variants.length || new Set(probabilities).size !== 1)
      ) {
        fail(`${location}[${slotIndex}] must use one occurrence probability for every variant.`);
      }
    }
  };

  const readRecipes = async category => {
    const documentKey = `${category.dir}/recipes.json`;
    const physicalValue = await readJsonDocument(
      join(root, ...category.dir.split('/'), 'recipes.json'),
      documentKey,
    );
    if (forceRawAssets && !Array.isArray(physicalValue)) {
      throw new Error(`${documentKey} must contain an array in a raw export.`);
    }
    const resolvedDocument = await readArrayDocument(root, physicalValue, documentKey);
    const expectedPrefix = `${category.dir}/parts/`;
    registerShardPaths(
      resolvedDocument.shardPaths,
      path =>
        path.startsWith(expectedPrefix) &&
        /^part-\d+\.json$/.test(path.slice(expectedPrefix.length)),
      documentKey,
    );
    return resolvedDocument.value;
  };

  for (const [categoryIndex, category] of categories.entries()) {
    if (!isRecord(category)) {
      fail(`categories[${categoryIndex}] must be an object.`);
      continue;
    }
    if (typeof category.id !== 'string' || !category.id) {
      fail(`categories[${categoryIndex}].id must be a non-empty string.`);
    } else if (categoryIds.has(category.id)) {
      fail(`Duplicate category id: ${category.id}`);
    } else {
      categoryIds.add(category.id);
    }
    if (!safeRelativePath(category.dir)) {
      fail(`Unsafe or invalid category directory: ${String(category.dir)}`);
      continue;
    }
    if (categoryDirs.has(category.dir)) fail(`Duplicate category directory: ${category.dir}`);
    categoryDirs.add(category.dir);
    if (!Number.isSafeInteger(category.count) || category.count < 0) {
      fail(`Category ${category.id ?? categoryIndex} has an invalid count.`);
    }
    if (!Array.isArray(category.catalysts)) {
      fail(`Category ${category.id ?? categoryIndex} catalysts must be an array.`);
    } else {
      for (const catalyst of category.catalysts) {
        if (typeof catalyst !== 'string' || !itemKeys.has(catalyst)) {
          fail(`Category ${category.id ?? categoryIndex} references unknown catalyst ${String(catalyst)}.`);
        }
      }
    }
    if (category.icon !== undefined) {
      if (structuredDataOnly) {
        fail(
          `categories[${categoryIndex}].icon is forbidden by ` +
            `${GTNH_STRUCTURED_DATA_ONLY_POLICY_ID}.`,
        );
      } else if (safeRelativePath(category.icon)) {
        // Category renderers choose their own canvas size; exhaustive decode
        // below still enforces positive, bounded dimensions and visible pixels.
        referenceAsset(category.icon, null, `category ${category.id ?? categoryIndex}`);
      }
      else fail(`Category ${category.id ?? categoryIndex} has an invalid icon path.`);
    }

    let recipes;
    try {
      recipes = await readRecipes(category);
    } catch (error) {
      fail(`Category ${category.id ?? categoryIndex} recipes could not be read: ${error.message}`);
      continue;
    }
    if (!Array.isArray(recipes)) {
      fail(`${category.dir}/recipes.json must contain an array.`);
      continue;
    }
    recipeCounts[categoryIndex] = recipes.length;
    totalRecipes += recipes.length;
    if (category.count !== recipes.length) {
      fail(
        `Category ${category.id ?? categoryIndex} declares ${String(category.count)} recipes but contains ${recipes.length}.`,
      );
    }
    recipeImageInventoryCategories?.push({
      categoryIndex,
      categoryId: category.id,
      recipeCount: recipes.length,
      start: recipeImageInventoryEntries.length,
    });
    for (const [recipeIndex, recipe] of recipes.entries()) {
      const location = `${category.id ?? categoryIndex} recipe ${recipeIndex}`;
      if (!isRecord(recipe)) {
        fail(`${location} must be an object.`);
        continue;
      }
      if (recipe.err !== undefined && typeof recipe.err !== 'boolean') {
        fail(`${location}.err must be a boolean when present.`);
      } else if (recipe.err === true) {
        semanticErrorRecipes += 1;
      }
      if (structuredDataOnly) {
        for (const field of ['img', 'w', 'h']) {
          if (field in recipe) {
            fail(`${location}.${field} is forbidden by ${GTNH_STRUCTURED_DATA_ONLY_POLICY_ID}.`);
          }
        }
      }
      if (recipe.img !== undefined) {
        if (structuredDataOnly) {
          // The policy violation above is sufficient; do not register a surviving
          // coordinate or raw path as an allowed visual asset.
        } else if (typeof recipe.img !== 'string' || !safeRelativePath(recipe.img)) {
          fail(`${location} has an invalid image path.`);
        } else {
          const width = Number.isFinite(recipe.w) && recipe.w > 0 ? recipe.w : null;
          const height = Number.isFinite(recipe.h) && recipe.h > 0 ? recipe.h : null;
          referenceAsset(
            parsePackedImagePath(recipe.img)
              ? recipe.img
              : posix.join(category.dir, recipe.img),
            width && height && recipeScale && recipeScale > 0
              ? {width: width * recipeScale, height: height * recipeScale}
              : null,
            location,
          );
          if (recipeImageInventoryEntries) {
            try {
              const assetKey = posix.join(category.dir, recipe.img);
              normalizedLogicalRecipePngPath(category.dir, recipe.img);
              const entryIndex = recipeImageInventoryEntries.length;
              recipeImageInventoryEntries.push({
                kind: 'preview',
                assetKey,
                declaredWidth: recipe.w,
                declaredHeight: recipe.h,
              });
              const existingIndexes = recipeImageEntryIndexesByAssetKey.get(assetKey);
              if (existingIndexes === undefined) {
                recipeImageEntryIndexesByAssetKey.set(assetKey, entryIndex);
              } else if (Array.isArray(existingIndexes)) {
                existingIndexes.push(entryIndex);
              } else {
                recipeImageEntryIndexesByAssetKey.set(assetKey, [existingIndexes, entryIndex]);
              }
            } catch (error) {
              fail(`${location} cannot enter the recipe-image inventory: ${error.message}`);
            }
          }
        }
      } else if (recipeImageInventoryEntries) {
        recipeImageInventoryEntries.push({kind: 'missing'});
      }
      validateSlots(recipe.in, `${location}.in`, 'in');
      validateSlots(recipe.out, `${location}.out`, 'out');
      validateSlots(recipe.cat, `${location}.cat`, 'cat');
    }
  }

  let mobs = [];
  if (fileKeys.has('mobs.json')) {
    const mobsDoc = await readJsonDocument(join(root, 'mobs.json'), 'mobs.json');
    if (!isRecord(mobsDoc) || !Array.isArray(mobsDoc.mobs)) {
      fail('mobs.json must contain an object with a mobs array when present.');
    } else {
      mobs = mobsDoc.mobs;
      for (const [mobIndex, mob] of mobs.entries()) {
        if (!isRecord(mob)) {
          fail(`mobs[${mobIndex}] must contain an object.`);
        } else if (structuredDataOnly) {
          for (const field of ['icon', 'frames', 'fps']) {
            if (field in mob) {
              fail(
                `mobs[${mobIndex}].${field} is forbidden by ` +
                  `${GTNH_STRUCTURED_DATA_ONLY_POLICY_ID}.`,
              );
            }
          }
        } else if (typeof mob.icon !== 'string' || !safeRelativePath(mob.icon)) {
          fail(`mobs[${mobIndex}] must contain a valid relative icon path.`);
        } else {
          const frames = Number.isSafeInteger(mob.frames) && mob.frames > 0 ? mob.frames : 1;
          referenceAsset(
            mob.icon,
            mobCanvas && mobCanvas > 0
              ? {width: mobCanvas * frames, height: mobCanvas}
              : null,
            `mobs[${mobIndex}]`,
          );
        }
      }
    }
  } else {
    console.info('Optional mobs.json is absent; validating the export without mob data.');
  }

  let blockDropCount = 0;
  if (fileKeys.has('blockdrops.json')) {
    const blockDropsDoc = await readJsonDocument(join(root, 'blockdrops.json'), 'blockdrops.json');
    if (!isRecord(blockDropsDoc) || !isRecord(blockDropsDoc.blocks)) {
      fail('blockdrops.json must contain an object with a blocks map when present.');
    } else {
      blockDropCount = Object.keys(blockDropsDoc.blocks).length;
    }
  } else {
    console.info('Optional blockdrops.json is absent; validating the export without block drops.');
  }

  let failureCount = 0;
  let failures = [];
  if (fileKeys.has('failures.json')) {
    const value = await readJsonDocument(join(root, 'failures.json'), 'failures.json');
    if (!Array.isArray(value)) fail('failures.json must contain an array when present.');
    else {
      failures = value;
      failureCount = failures.length;
      for (const [failureIndex, failure] of failures.entries()) {
        if (typeof failure !== 'string' || !failure) {
          fail(`failures.json[${failureIndex}] must be a non-empty string.`);
        }
      }
    }
  } else if (countValue(manifest?.counts?.failures) > 0) {
    fail('manifest reports failures but failures.json is absent.');
  } else {
    console.info('Optional failures.json is absent and the manifest reports no failures.');
  }
  if (qualityProfile && !fileKeys.has('failures.json')) {
    fail(
      `Export quality profile ${qualityProfile} requires failures.json even when it contains no entries.`,
    );
  }
  const declaredFailureCount = countValue(manifest?.counts?.failures);
  if (declaredFailureCount === null) {
    fail('manifest.counts.failures must be a non-negative safe integer.');
  } else if (declaredFailureCount !== failureCount) {
    fail(
      `manifest.counts.failures is ${declaredFailureCount} but failures.json contains ${failureCount} entries.`,
    );
  }
  if (failureDiagnostics) {
    const {failureEvents, failureEventsOmitted} = failureDiagnostics;
    const retainedFailureEvents = failureEvents - failureEventsOmitted;
    const expectedSerializedFailures =
      retainedFailureEvents + (failureEventsOmitted > 0 ? 1 : 0);
    if (expectedSerializedFailures !== failureCount) {
      fail(
        `manifest diagnostics imply ${expectedSerializedFailures} serialized failures, but ` +
          `failures.json contains ${failureCount}.`,
      );
    }
    if (failureEventsOmitted > 0) {
      const marker = failures[failures.length - 1];
      if (
        typeof marker !== 'string' ||
        !marker.includes(`${failureEventsOmitted} additional failures omitted`)
      ) {
        fail('failures.json is missing the bounded-sample omission marker declared by diagnostics.');
      }
    }
  }

  let warnings;
  if (
    qualityProfile === MULTIBLOCK_MADNESS_112_PROFILE ||
    qualityProfile === MULTIBLOCK_MADNESS_2_118_PROFILE
  ) {
    if (fileKeys.has('warnings.json')) {
      warnings = await readJsonDocument(join(root, 'warnings.json'), 'warnings.json');
    } else {
      warnings = null;
    }
  }

  for (const issue of exportQualityIssues(
    {
      manifest,
      failures,
      warnings,
      iconlessItemIds:
        qualityProfile === MULTIBLOCK_MADNESS_2_118_PROFILE
          ? collectIconlessItemIds({items}, 'items.json')
          : undefined,
      semanticErrorRecipes,
    },
    qualityProfile,
  )) {
    fail(issue);
  }

  const indexDocument = await readJsonDocument(join(root, 'index.json'), 'index.json');
  let index = null;
  if (forceRawAssets && isShardedDocument(indexDocument, 'object')) {
    fail('index.json must contain a single object in a raw export.');
  } else {
    try {
      const resolvedIndex = await readObjectDocument(root, indexDocument, 'index.json');
      index = resolvedIndex.value;
      registerShardPaths(
        resolvedIndex.shardPaths,
        /^data\/index\/part-\d+\.json$/,
        'index.json',
      );
    } catch (error) {
      fail(`index.json shards could not be read: ${error.message}`);
    }
  }
  if (!isRecord(index)) {
    fail('index.json must contain an object keyed by catalog key.');
  } else {
    const categoryRefCounts = new Uint32Array(categories.length);
    const visitValidRefs = (refs, location, visitor, reportErrors = true) => {
      if (refs === undefined) return;
      if (!Array.isArray(refs)) {
        if (reportErrors) fail(`${location} must be an array.`);
        return;
      }
      const seen = reportErrors ? new Set() : null;
      for (const [refIndex, ref] of refs.entries()) {
        if (
          !Array.isArray(ref) ||
          ref.length !== 2 ||
          !Number.isSafeInteger(ref[0]) ||
          !Number.isSafeInteger(ref[1]) ||
          ref[0] < 0 ||
          ref[0] >= categories.length ||
          ref[1] < 0 ||
          ref[1] >= (recipeCounts[ref[0]] ?? 0)
        ) {
          if (reportErrors) fail(`${location}[${refIndex}] is outside the exported recipe table.`);
          continue;
        }
        const refKey = `${ref[0]}:${ref[1]}`;
        if (seen?.has(refKey)) fail(`${location} contains duplicate recipe reference ${refKey}.`);
        seen?.add(refKey);
        visitor(ref[0], ref[1]);
      }
    };

    for (const [key, entry] of Object.entries(index)) {
      const keyId = itemKeyIds.get(key);
      if (keyId === undefined) fail(`index.json contains unknown catalog key ${key}.`);
      if (!isRecord(entry)) {
        fail(`index.json entry ${key} must be an object.`);
        continue;
      }
      visitValidRefs(
        entry.p,
        `index[${JSON.stringify(key)}].p`,
        categoryIndex => {
          categoryRefCounts[categoryIndex] += 1;
        },
      );
      visitValidRefs(
        entry.u,
        `index[${JSON.stringify(key)}].u`,
        categoryIndex => {
          categoryRefCounts[categoryIndex] += 1;
        },
      );
    }

    // Store reverse references as compact integer pairs while validating their
    // semantics one category at a time. This avoids retaining every recipe
    // document or allocating one object per reference on very large exports.
    const compactRefs = Array.from(
      categoryRefCounts,
      count => new Int32Array(count * 2),
    );
    const writeOffsets = new Uint32Array(categories.length);
    for (const [key, entry] of Object.entries(index)) {
      const keyId = itemKeyIds.get(key);
      if (keyId === undefined || !isRecord(entry)) continue;
      const storeRef = usage => (categoryIndex, recipeIndex) => {
        const offset = writeOffsets[categoryIndex] * 2;
        compactRefs[categoryIndex][offset] = recipeIndex;
        compactRefs[categoryIndex][offset + 1] = usage ? -keyId - 1 : keyId;
        writeOffsets[categoryIndex] += 1;
      };
      visitValidRefs(entry.p, '', storeRef(false), false);
      visitValidRefs(entry.u, '', storeRef(true), false);
    }

    const slotKeys = slots => {
      const keys = new Set();
      if (!Array.isArray(slots)) return keys;
      for (const variants of slots) {
        if (!Array.isArray(variants)) continue;
        for (const entry of variants) {
          if (Array.isArray(entry) && typeof entry[0] === 'string') keys.add(entry[0]);
        }
      }
      return keys;
    };
    let semanticRefsChecked = 0;
    for (const [categoryIndex, encodedRefs] of compactRefs.entries()) {
      if (encodedRefs.length === 0) continue;
      const category = categories[categoryIndex];
      if (!isRecord(category) || !safeRelativePath(category.dir)) continue;
      let recipes;
      try {
        recipes = await readRecipes(category);
      } catch (error) {
        fail(`Reverse-index semantics could not be checked for ${category.id}: ${error.message}`);
        continue;
      }
      if (!Array.isArray(recipes)) continue;
      const semanticCache = new Map();
      const indexedByRecipe = new Map();
      for (let offset = 0; offset < encodedRefs.length; offset += 2) {
        const recipeIndex = encodedRefs[offset];
        const encodedKey = encodedRefs[offset + 1];
        const usage = encodedKey < 0;
        const keyId = usage ? -encodedKey - 1 : encodedKey;
        const key = itemKeysById[keyId];
        const recipe = recipes[recipeIndex];
        if (!isRecord(recipe) || key === undefined) continue;
        let semantics = semanticCache.get(recipeIndex);
        if (!semantics) {
          const inputs = slotKeys(recipe.in);
          for (const catalyst of slotKeys(recipe.cat)) inputs.add(catalyst);
          semantics = {produced: slotKeys(recipe.out), used: inputs};
          semanticCache.set(recipeIndex, semantics);
        }
        const expectedKeys = usage ? semantics.used : semantics.produced;
        if (!expectedKeys.has(key)) {
          fail(
            `index[${JSON.stringify(key)}].${usage ? 'u' : 'p'} points to ` +
              `${category.id ?? categoryIndex} recipe ${recipeIndex}, which does not ${
                usage ? 'use' : 'produce'
              } that key.`,
          );
        }
        let indexedKeys = indexedByRecipe.get(recipeIndex);
        if (!indexedKeys) {
          indexedKeys = {produced: new Set(), used: new Set()};
          indexedByRecipe.set(recipeIndex, indexedKeys);
        }
        (usage ? indexedKeys.used : indexedKeys.produced).add(key);
        semanticRefsChecked += 1;
      }
      for (const [recipeIndex, recipe] of recipes.entries()) {
        if (!isRecord(recipe)) continue;
        const produced = slotKeys(recipe.out);
        const used = slotKeys(recipe.in);
        for (const catalyst of slotKeys(recipe.cat)) used.add(catalyst);
        const indexedKeys = indexedByRecipe.get(recipeIndex) ?? {
          produced: new Set(),
          used: new Set(),
        };
        for (const key of produced) {
          if (!indexedKeys.produced.has(key)) {
            fail(
              `${category.id ?? categoryIndex} recipe ${recipeIndex} produces ${key}, but ` +
                'index.json has no matching p reference.',
            );
          }
        }
        for (const key of used) {
          if (!indexedKeys.used.has(key)) {
            fail(
              `${category.id ?? categoryIndex} recipe ${recipeIndex} uses ${key}, but ` +
                'index.json has no matching u reference.',
            );
          }
        }
      }
    }
    console.log(`Validated semantic direction for ${semanticRefsChecked} reverse-index references.`);
    if (semanticRefsChecked === 0 && totalRecipes > 0) {
      fail('Reverse index contains no valid recipe references for a non-empty recipe export.');
    }
  }

  const expectedCounts = [
    ['items', items.length],
    ['categories', categories.length],
    ['recipes', totalRecipes],
    ['mobs', mobs.length],
    ['blockDrops', blockDropCount],
  ];
  for (const [name, actual] of expectedCounts) {
    const declared = manifest?.counts?.[name];
    const required = name === 'items' || name === 'recipes' || name === 'categories' || name === 'mobs';
    if ((required || declared !== undefined) && countValue(declared) !== actual) {
      fail(`manifest.counts.${name} is ${String(declared)} but validated ${actual}.`);
    }
  }

  for (const key of fileKeys) {
    const inShardNamespace =
      key.startsWith('data/items/') ||
      key.startsWith('data/index/') ||
      (key.startsWith('recipes/') && key.includes('/parts/'));
    if (inShardNamespace && !referencedShardFiles.has(key)) {
      fail(`Unreferenced JSON shard is present: ${key}`);
    }
  }

  if (!forceRawAssets && fileKeys.has('assets-index.json')) {
    fail(
      'Legacy assets-index.json is not permitted; coordinate-packed exports encode ranges in image URLs.',
    );
  } else if (forceRawAssets && fileKeys.has('assets-index.json')) {
    console.info(
      'Raw export validation is ignoring the previous packed-asset index during transactional repacking.',
    );
  }
  const rasterExtensions = new Set([
    '.png',
    '.webp',
    '.jpg',
    '.jpeg',
    '.gif',
    '.bmp',
    '.apng',
    '.avif',
  ]);
  const allRasterFiles = allFiles.filter(path =>
    rasterExtensions.has(extname(path).toLowerCase()),
  );
  const rawImageFiles = allFiles.filter(path => {
    const key = relativeKey(root, path);
    const extension = extname(path).toLowerCase();
    return (
      (extension === '.png' || extension === '.webp') &&
      (key.startsWith('icons/') || key.startsWith('recipes/') || key.startsWith('mobs/'))
    );
  });
  if (structuredDataOnly) {
    for (const directoryName of ['icons', 'mobs']) {
      const kind = await pathKind(join(root, directoryName));
      if (kind !== 'missing') {
        fail(
          `${GTNH_STRUCTURED_DATA_ONLY_POLICY_ID} requires ${directoryName}/ to be omitted; ` +
            `found ${kind}.`,
        );
      }
    }
    if (allRasterFiles.length > 0) {
      fail(
        `${GTNH_STRUCTURED_DATA_ONLY_POLICY_ID} forbids every raster file; found ` +
          `${allRasterFiles.length}, including ${relativeKey(root, allRasterFiles[0])}.`,
      );
    }
    const forbiddenPackFiles = [...fileKeys]
      .filter(key => /(?:^|\/)pack-\d+\.bin$/.test(key))
      .sort();
    if (forbiddenPackFiles.length > 0) {
      fail(
        `${GTNH_STRUCTURED_DATA_ONLY_POLICY_ID} forbids packed-image files; found ` +
          `${forbiddenPackFiles.length}, including ${forbiddenPackFiles[0]}.`,
      );
    }
    for (const jsonPath of allFiles.filter(path => extname(path).toLowerCase() === '.json')) {
      const documentKey = relativeKey(root, jsonPath);
      const document = await readJsonDocument(jsonPath, documentKey);
      const pending = [document];
      let packedCoordinate = null;
      while (pending.length > 0 && packedCoordinate === null) {
        const value = pending.pop();
        if (typeof value === 'string') {
          packedCoordinate = /^assets\/s\/\d+-\d+-\d+\.webp$/.test(value) ? value : null;
        } else if (Array.isArray(value)) {
          for (const entry of value) pending.push(entry);
        } else if (isRecord(value)) {
          for (const entry of Object.values(value)) pending.push(entry);
        }
      }
      if (packedCoordinate) {
        fail(
          `${GTNH_STRUCTURED_DATA_ONLY_POLICY_ID} forbids packed coordinates; ` +
            `${documentKey} contains ${packedCoordinate}.`,
        );
      }
    }
  }
  const packSizes = new Map();
  const packIntervals = new Map();
  if (coordinatePacked) {
    for (const assetKey of assetReferences) {
      const record = parsePackedImagePath(assetKey);
      if (!record) {
        fail(`Packed image reference is not a valid coordinate URL: ${assetKey}`);
        continue;
      }
      const {packNumber, offset, length} = record;
      if (packedImagePath(packNumber, offset, length) !== assetKey) {
        fail(`Packed image reference is not canonically encoded: ${assetKey}`);
      }
      const end = offset + length;
      if (!Number.isSafeInteger(end)) {
        fail(`Packed image coordinate exceeds JavaScript's safe integer range: ${assetKey}`);
        continue;
      }
      const packKey = packFileKey(packNumber);
      let packSize = packSizes.get(packKey);
      if (packSize === undefined) {
        if (!fileKeys.has(packKey)) {
          fail(`Packed asset ${assetKey} references missing ${packKey}.`);
          continue;
        }
        packSize = (await stat(join(root, ...packKey.split('/')))).size;
        packSizes.set(packKey, packSize);
        if (packSize <= 0 || packSize > MAX_PACK_BYTES) {
          fail(
            `${packKey} is ${packSize} bytes; coordinate packs must be between 1 and ` +
              `${MAX_PACK_BYTES} bytes.`,
          );
        }
      }
      if (end > packSize) {
        fail(`Packed asset ${assetKey} extends beyond ${packKey}.`);
      }
      const intervals = packIntervals.get(packKey) ?? [];
      intervals.push({offset, length, assetKey});
      packIntervals.set(packKey, intervals);
    }
    for (const [packKey, intervals] of packIntervals) {
      intervals.sort((left, right) => left.offset - right.offset);
      let expectedOffset = 0;
      for (const interval of intervals) {
        if (interval.offset !== expectedOffset) {
          fail(
            `${packKey} has a gap or overlap before ${interval.assetKey}: expected offset ` +
              `${expectedOffset}, found ${interval.offset}.`,
          );
        }
        expectedOffset = Math.max(expectedOffset, interval.offset + interval.length);
      }
      const packSize = packSizes.get(packKey);
      if (packSize !== undefined && expectedOffset !== packSize) {
        fail(`${packKey} indexes ${expectedOffset} bytes but the file contains ${packSize}.`);
      }
    }
    for (const key of fileKeys) {
      if (/^assets\/pack-\d+\.bin$/.test(key) && !packIntervals.has(key)) {
        fail(`Unreferenced packed-asset file is present: ${key}`);
      }
    }
    if (rawImageFiles.length > 0) {
      fail(
        `Packed export still contains ${rawImageFiles.length} raw image file(s); the packing stage is incomplete.`,
      );
    }
  } else if (
    !forceRawAssets &&
    [...fileKeys].some(key => /^assets\/pack-\d+\.bin$/.test(key))
  ) {
    fail('Packed-asset files are present without the coordinate-v1 manifest contract.');
  }

  for (const assetPath of assetReferences) {
    if (coordinatePacked) {
      if (!parsePackedImagePath(assetPath)) {
        fail(`Referenced image is not a coordinate-packed URL: ${assetPath}`);
      }
    } else if (!fileKeys.has(assetPath)) {
      fail(`Referenced raw image asset is missing: ${assetPath}`);
    }
  }

  if (!coordinatePacked) {
    const rawImageKeys = new Set(rawImageFiles.map(path => relativeKey(root, path)));
    for (const rawImageKey of rawImageKeys) {
      if (!assetReferences.has(rawImageKey)) {
        fail(`Unreferenced raw image is present: ${rawImageKey}`);
      }
    }

    let nextImageIndex = 0;
    let decodedImages = 0;
    const decodeConcurrency = Math.max(1, Math.min(4, availableParallelism()));
    async function validateNextRawImage() {
      while (nextImageIndex < rawImageFiles.length) {
        const imagePath = rawImageFiles[nextImageIndex++];
        const assetKey = relativeKey(root, imagePath);
        try {
          const image = sharp(imagePath, {failOn: 'error'});
          const metadata = await image.metadata();
          const expectedFormat = extname(imagePath).toLowerCase() === '.png' ? 'png' : 'webp';
          if (metadata.format !== expectedFormat) {
            fail(
              `Raw image ${assetKey} has ${String(metadata.format)} content behind its ` +
                `${expectedFormat} filename; original-encoding accounting requires an exact match.`,
            );
            continue;
          }
          if ((metadata.pages ?? 1) !== 1) {
            fail(
              `Raw image ${assetKey} contains ${metadata.pages} animation/pages; ` +
                'inventory hashing requires exactly one image page.',
            );
            continue;
          }
          const {data, info} = await image
            .toColourspace('srgb')
            .ensureAlpha()
            .raw()
            .toBuffer({resolveWithObject: true});
          if (
            !Number.isSafeInteger(info.width) ||
            !Number.isSafeInteger(info.height) ||
            info.width <= 0 ||
            info.height <= 0 ||
            info.width > 16384 ||
            info.height > 16384
          ) {
            fail(
              `Raw image ${assetKey} has invalid dimensions ${String(info.width)}x${String(info.height)}.`,
            );
            continue;
          }
          const expected = expectedAssetDimensions.get(assetKey);
          if (
            expected &&
            (info.width !== expected.width || info.height !== expected.height)
          ) {
            fail(
              `Raw image ${assetKey} is ${info.width}x${info.height}; expected ` +
                `${expected.width}x${expected.height}.`,
            );
          }
          const channels = info.channels;
          const alphaChannel = channels - 1;
          let hasVisiblePixel = false;
          const itemIconIdentity = itemIconIdentitiesByAssetPath.get(assetKey);
          const inspectMissingTexture =
            qualityProfile === MULTIBLOCK_MADNESS_2_118_PROFILE &&
            itemIconIdentity !== undefined;
          const missingCheckerHalfWidth = info.width / 2;
          const missingCheckerHalfHeight = info.height / 2;
          let exactMissingCheckerPhaseA =
            inspectMissingTexture &&
            expected !== undefined &&
            info.width === expected.width &&
            info.height === expected.height &&
            Number.isInteger(missingCheckerHalfWidth) &&
            Number.isInteger(missingCheckerHalfHeight);
          let exactMissingCheckerPhaseB = exactMissingCheckerPhaseA;
          for (let offset = 0; offset < data.length; offset += channels) {
            const alpha = data[offset + alphaChannel];
            if (alpha !== 0) hasVisiblePixel = true;
            if (!inspectMissingTexture) {
              if (hasVisiblePixel) break;
              continue;
            }
            const red = data[offset];
            const green = data[offset + 1];
            const blue = data[offset + 2];
            const black = red === 0 && green === 0 && blue === 0 && alpha === 255;
            const missingMagenta =
              red === 248 && green === 0 && blue === 248 && alpha === 255;
            if (exactMissingCheckerPhaseA || exactMissingCheckerPhaseB) {
              const pixelIndex = offset / channels;
              const x = pixelIndex % info.width;
              const y = Math.floor(pixelIndex / info.width);
              const phaseAMagenta =
                (x < missingCheckerHalfWidth) !== (y < missingCheckerHalfHeight);
              exactMissingCheckerPhaseA &&=
                phaseAMagenta ? missingMagenta : black;
              exactMissingCheckerPhaseB &&=
                phaseAMagenta ? black : missingMagenta;
            }
            if (
              hasVisiblePixel &&
              !exactMissingCheckerPhaseA &&
              !exactMissingCheckerPhaseB
            ) {
              break;
            }
          }
          if (!hasVisiblePixel) fail(`Raw image ${assetKey} is fully transparent.`);
          const exactMissingChecker =
            exactMissingCheckerPhaseA || exactMissingCheckerPhaseB;
          // Both audited digests are the two checker phases. Hash only a full
          // checker candidate so normal MM2 icons incur no extra buffer pass.
          const itemIconRgbaSha256 = exactMissingChecker
            ? decodedRgbaSha256(info.width, info.height, data)
            : null;
          const auditedMissingnoDigest =
            itemIconRgbaSha256 !== null &&
            MM2_MISSINGNO_RGBA_SHA256.includes(itemIconRgbaSha256);
          if (inspectMissingTexture && (auditedMissingnoDigest || exactMissingChecker)) {
            const matchedSignatures = [
              ...(auditedMissingnoDigest
                ? [`decoded RGBA SHA-256 ${itemIconRgbaSha256}`]
                : []),
              ...(exactMissingChecker
                ? ['exact black/magenta checker scaled from 8×8 source quadrants']
                : []),
            ];
            fail(
              `Raw item icon ${assetKey} for ${itemIconIdentity} matches the canonical ` +
                `Minecraft missing-texture signature (${matchedSignatures.join(' and ')}); ` +
                'the Multiblock Madness 2 profile has no missing-texture allowlist entry.',
            );
          }
          const inventoryEntryIndexes = recipeImageEntryIndexesByAssetKey?.get(assetKey);
          if (inventoryEntryIndexes !== undefined) {
            const decodedInventory = {
              decodedWidth: info.width,
              decodedHeight: info.height,
              rgbaSha256: decodedRgbaSha256(info.width, info.height, data),
            };
            const indexes = Array.isArray(inventoryEntryIndexes)
              ? inventoryEntryIndexes
              : [inventoryEntryIndexes];
            for (const entryIndex of indexes) {
              Object.assign(recipeImageInventoryEntries[entryIndex], decodedInventory);
            }
            recipeImageEntryIndexesByAssetKey.delete(assetKey);
          }
        } catch (error) {
          fail(
            `Raw image ${assetKey} could not be decoded completely: ${
              error instanceof Error ? error.message : String(error)
            }`,
          );
        } finally {
          decodedImages += 1;
          if (decodedImages % 5000 === 0 || decodedImages === rawImageFiles.length) {
            console.log(`Decoded and inspected ${decodedImages}/${rawImageFiles.length} raw images.`);
          }
        }
      }
    }
    await Promise.all(Array.from({length: decodeConcurrency}, validateNextRawImage));

    if (recipeImageInventoryEntries) {
      const inventory = createRecipeImageInventory();
      for (const category of recipeImageInventoryCategories) {
        inventory.beginCategory(category);
        for (let recipeIndex = 0; recipeIndex < category.recipeCount; recipeIndex += 1) {
          const entry = recipeImageInventoryEntries[category.start + recipeIndex];
          const header = {
            categoryIndex: category.categoryIndex,
            categoryId: category.categoryId,
            recipeIndex,
          };
          if (entry?.kind === 'missing') {
            inventory.addMissing(header);
            continue;
          }
          if (!entry?.rgbaSha256) {
            fail(
              `Recipe-image inventory could not find decoded pixels for ` +
                `${entry?.assetKey ?? `entry ${category.start + recipeIndex}`}.`,
            );
            // Preserve canonical call order so validation can continue collecting errors. The
            // accumulated error prevents this placeholder from ever becoming a publication.
            inventory.addMissing(header);
            continue;
          }
          try {
            inventory.addPreview({
              ...header,
              logicalPngPath: entry.assetKey.endsWith('.webp')
                ? `${entry.assetKey.slice(0, -5)}.png`
                : entry.assetKey,
              declaredWidth: entry.declaredWidth,
              declaredHeight: entry.declaredHeight,
              decodedWidth: entry.decodedWidth,
              decodedHeight: entry.decodedHeight,
              rgbaSha256: entry.rgbaSha256,
            });
          } catch (error) {
            fail(`Recipe-image inventory rejected ${entry.assetKey}: ${error.message}`);
            inventory.addMissing(header);
          }
        }
      }
      if (
        recipeImageInventoryEntries.length === totalRecipes &&
        recipeImageInventoryCategories.length === categories.length &&
        recipeImageEntryIndexesByAssetKey.size === 0
      ) {
        computedRecipeImageInventory = inventory.finish();
      } else {
        fail(
          `Recipe-image inventory contains ${recipeImageInventoryEntries.length} entries and ` +
            `${recipeImageInventoryCategories.length} categories for ${totalRecipes} recipes ` +
            `and ${categories.length} categories; ${recipeImageEntryIndexesByAssetKey.size} ` +
            'referenced image paths were not decoded.',
        );
      }
    }
  }

  if (
    options.verifyPublicationId &&
    typeof publicationId === 'string' &&
    PUBLICATION_ID_PATTERN.test(publicationId)
  ) {
    try {
      const computedPublicationId = await computePublicationId(root);
      if (computedPublicationId !== publicationId) {
        fail(
          `manifest.publicationId is ${publicationId}, but the final dataset hashes to ${computedPublicationId}.`,
        );
      }
    } catch (error) {
      fail(
        `The final dataset publication ID could not be verified: ${
          error instanceof Error ? error.message : String(error)
        }`,
      );
    }
  }

  if (errors.length > 0 || suppressedErrors > 0) {
    const details = errors.map(message => `- ${message}`).join('\n');
    const suffix = suppressedErrors > 0 ? `\n- ...and ${suppressedErrors} additional errors.` : '';
    throw new Error(`Export validation failed with ${errors.length + suppressedErrors} error(s):\n${details}${suffix}`);
  }

  const summary = {
    root,
    items: items.length,
    categories: categories.length,
    recipes: totalRecipes,
    mobs: mobs.length,
    blockDrops: blockDropCount,
    failures: failureCount,
    ...((qualityProfile === MULTIBLOCK_MADNESS_112_PROFILE ||
      qualityProfile === MULTIBLOCK_MADNESS_2_118_PROFILE) &&
    Array.isArray(warnings)
      ? {warnings: warnings.length}
      : {}),
    semanticErrorRecipes,
    imageReferences: assetReferences.size,
    packedAssets: coordinatePacked ? assetReferences.size : 0,
    ...(options.computeRecipeImageInventory
      ? {recipeImageInventory: computedRecipeImageInventory}
      : {}),
  };
  console.log(
    `Validated export: ${summary.items} items, ${summary.recipes} recipes in ${summary.categories} categories, ` +
      `${summary.mobs} mobs, ${summary.imageReferences} referenced images.`,
  );
  return summary;
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath && fileURLToPath(import.meta.url) === invokedPath) {
  const args = process.argv.slice(2);
  let exportRoot = defaultExportRoot;
  let profile = null;
  let positionalRootSeen = false;
  let showHelp = false;
  let requirePublicationId = false;
  let verifyPublicationId = false;
  let allowLegacyRecipeImageAccounting = false;
  let requirePackIdentity = false;
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === '--help' || argument === '-h') {
      showHelp = true;
    } else if (argument === '--root') {
      const value = args[++index];
      if (!value) throw new Error('--root requires a directory path.');
      exportRoot = value;
      positionalRootSeen = true;
    } else if (argument === '--profile') {
      const value = args[++index];
      if (!value) throw new Error('--profile requires a profile name.');
      profile = value;
    } else if (argument === '--require-publication-id') {
      requirePublicationId = true;
    } else if (argument === '--verify-publication-id') {
      requirePublicationId = true;
      verifyPublicationId = true;
    } else if (argument === '--allow-legacy-recipe-image-accounting') {
      allowLegacyRecipeImageAccounting = true;
    } else if (argument === '--require-pack-identity') {
      requirePackIdentity = true;
    } else if (!argument.startsWith('-') && !positionalRootSeen) {
      exportRoot = argument;
      positionalRootSeen = true;
    } else {
      throw new Error(`Unknown validation argument: ${argument}`);
    }
  }

  if (showHelp) {
    console.log(
      'Usage: node scripts/validate-export-data.mjs [--root <directory>] ' +
        `[--profile <${EXPORT_QUALITY_PROFILE_IDS.join('|')}>] ` +
        '[--require-publication-id] [--verify-publication-id] ' +
        '[--require-pack-identity] [--allow-legacy-recipe-image-accounting]',
    );
  } else {
    validateExportData(exportRoot, {
      profile,
      requirePublicationId,
      verifyPublicationId,
      requirePackIdentity,
      allowLegacyRecipeImageAccounting,
    }).catch(error => {
      console.error(error instanceof Error ? error.message : error);
      process.exitCode = 1;
    });
  }
}
