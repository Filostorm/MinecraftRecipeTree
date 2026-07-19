import {isRecord} from './export-data-utils.mjs';
import {requirePackIdentity} from './pack-identity.mjs';

export const GENERIC_JEI_120_PROFILE = 'generic-jei-1.20.1';
export const MEATBALLCRAFT_112_PROFILE = 'meatballcraft-1.12.2';
export const MULTIBLOCK_MADNESS_112_PROFILE = 'multiblock-madness-1.12.2';
export const MULTIBLOCK_MADNESS_2_118_PROFILE = 'multiblock-madness-2-1.18.2';
export const GTNH_1710_PROFILE = 'gtnh-1.7.10';

export const GTNH_NEI_DIAGNOSTIC_KEYS = Object.freeze([
  'itemListLoaded',
  'registeredCraftingHandlers',
  'loadedCategories',
  'recipesEnumerated',
  'recipeWidgetsRendered',
  'itemIconsRendered',
  'unloadedHandlerCategories',
  'ambiguousHandlerCategories',
  'duplicateHandlerCategories',
]);

const STANDARD_COUNT_KEYS = Object.freeze([
  'items',
  'recipes',
  'categories',
  'mobs',
  'blockDrops',
  'failures',
]);
const GTNH_DIAGNOSTIC_KEYS = Object.freeze([
  'failureEvents',
  'failureEventsOmitted',
  'nei',
]);

const profileRequirements = new Map([
  [
    GENERIC_JEI_120_PROFILE,
    Object.freeze({
      id: GENERIC_JEI_120_PROFILE,
      label: 'Generic JEI 1.20.1',
      minecraft: '1.20.1',
      format: 1,
      iconScale: 4,
      recipeScale: 2,
      recipeViewer: 'JEI',
      corpus: 'dynamic-complete',
      requiresPackIdentity: true,
    }),
  ],
  [
    MEATBALLCRAFT_112_PROFILE,
    Object.freeze({
      id: MEATBALLCRAFT_112_PROFILE,
      label: 'MeatballCraft',
      minecraft: '1.12.2',
      format: 1,
      iconScale: 3,
      recipeScale: 2,
      recipeViewer: 'HEI',
      corpus: 'exact',
    }),
  ],
  [
    MULTIBLOCK_MADNESS_112_PROFILE,
    Object.freeze({
      id: MULTIBLOCK_MADNESS_112_PROFILE,
      label: 'Multiblock Madness',
      minecraft: '1.12.2',
      format: 1,
      iconScale: 1,
      recipeScale: 2,
      recipeViewer: 'HEI',
      corpus: 'dynamic-complete',
    }),
  ],
  [
    MULTIBLOCK_MADNESS_2_118_PROFILE,
    Object.freeze({
      id: MULTIBLOCK_MADNESS_2_118_PROFILE,
      label: 'Multiblock Madness 2',
      minecraft: '1.18.2',
      format: 1,
      iconScale: 1,
      recipeScale: 2,
      recipeViewer: 'REI',
      corpus: 'dynamic-complete',
    }),
  ],
  [
    GTNH_1710_PROFILE,
    Object.freeze({
      id: GTNH_1710_PROFILE,
      label: 'GT New Horizons',
      minecraft: '1.7.10',
      format: 1,
      iconScale: 1,
      recipeScale: 2,
      recipeViewer: 'NEI',
      corpus: 'dynamic-complete',
      provenance: Object.freeze({
        profile: GTNH_1710_PROFILE,
        forge: '10.13.4.1614',
        nei: '2.8.44-GTNH',
      }),
      packIdentity: Object.freeze({
        name: 'GT New Horizons',
        version: '2.8.4',
        identitySource: 'explicit-request',
      }),
    }),
  ],
]);

export const EXPORT_QUALITY_PROFILE_IDS = Object.freeze([...profileRequirements.keys()]);

export function resolveQualityProfile(profile) {
  if (profile === undefined || profile === null) return null;
  if (!profileRequirements.has(profile)) {
    throw new Error(
      `Unknown export quality profile ${JSON.stringify(profile)}. Supported profiles: ` +
        EXPORT_QUALITY_PROFILE_IDS.join(', '),
    );
  }
  return profile;
}

export function qualityProfileRequirementsFor(profile) {
  const resolvedProfile = resolveQualityProfile(profile);
  return resolvedProfile === null ? null : profileRequirements.get(resolvedProfile);
}

function isCategoryFailure(message) {
  return /^(?:HANDLER_(?:UNLOADED|AMBIGUOUS|DUPLICATE):|category\s|reading category id while filtering:)/i.test(
    message.trim(),
  );
}

function isSemanticFailure(message) {
  return /^(?:RECIPE_SEMANTICS:|recipe semantics\s|recipe (?:input|output) ingredient\s)/i.test(
    message.trim(),
  );
}

function isQuantityFallback(message) {
  return /^(?:QUANTITY_INVALID:|ingredient amount type\s.+\susing quantity 1(?:\s|$))/i.test(
    message.trim(),
  );
}

function isRecipePreviewFailure(message) {
  return /^(?:RECIPE_WIDGET_RENDER:|PNG_WRITE:)/i.test(message.trim());
}

function isUnclassifiedZeroQuantity(message) {
  const normalized = message.trim();
  if (/\bZERO_UNCLASSIFIED\b/.test(normalized)) return true;
  if (!/^ZERO_[A-Z_]+\b/.test(normalized)) return false;
  return !/^(?:ZERO_PREREQUISITE|ZERO_THRESHOLD|ZERO_UNKNOWN_FLOW|ZERO_ABSENT_OUTPUT|ZERO_INVALID_RECIPE)\b/.test(
    normalized,
  );
}

function isLoggedBlankDisplayLabelSubstitution(message) {
  return /^ingredient display name\s.+\swas null\/blank(?: after formatting-code removal)?; using unique id$/i.test(
    message.trim(),
  );
}

function isCatalogCompletenessFailure(message) {
  const normalized = message.trim();
  // HEI's exact unique ID remains the catalog identity. A few legacy mods
  // return a blank cosmetic label; the exporter records that defect and uses
  // the exact ID as visible text. Keep extraction exceptions and every actual
  // identity/resource fallback fail-closed.
  if (isLoggedBlankDisplayLabelSubstitution(normalized)) return false;
  return (
    /^ITEM_IDENTITY:/i.test(normalized) ||
    /^(?:list ingredients\b|item\s.+\s#\d+:)/i.test(normalized) ||
    /^ingredient (?:unique id|display name|resource id)\b/i.test(normalized) ||
    /\bmissing item\b/i.test(normalized)
  );
}

function hasExactKeys(value, expected) {
  if (!isRecord(value)) return false;
  const actual = Object.keys(value).sort();
  const wanted = [...expected].sort();
  return actual.length === wanted.length && actual.every((key, index) => key === wanted[index]);
}

function isNonNegativeSafeInteger(value) {
  return Number.isSafeInteger(value) && value >= 0;
}

function genericJeiManifestQualityIssues(manifest, label) {
  const issues = [];
  const counts = manifest?.counts;
  if (!hasExactKeys(counts, STANDARD_COUNT_KEYS)) {
    issues.push(
      `${label} manifest.counts must contain exactly ${STANDARD_COUNT_KEYS.join(', ')}.`,
    );
  }
  for (const name of STANDARD_COUNT_KEYS) {
    if (!isNonNegativeSafeInteger(counts?.[name])) {
      issues.push(`${label} requires a non-negative manifest.counts.${name} value.`);
    }
  }
  for (const name of ['items', 'recipes', 'categories']) {
    if (isNonNegativeSafeInteger(counts?.[name]) && counts[name] === 0) {
      issues.push(`${label} requires manifest.counts.${name} to be positive.`);
    }
  }

  const diagnostics = manifest?.diagnostics;
  if (!hasExactKeys(diagnostics, ['failureEvents', 'failureEventsOmitted'])) {
    issues.push(
      `${label} manifest.diagnostics must contain exactly failureEvents, failureEventsOmitted.`,
    );
  }
  if (
    isNonNegativeSafeInteger(diagnostics?.failureEvents) &&
    isNonNegativeSafeInteger(counts?.failures) &&
    diagnostics.failureEvents !== counts.failures
  ) {
    issues.push(
      `${label} diagnostics.failureEvents (${diagnostics.failureEvents}) must equal ` +
        `manifest.counts.failures (${counts.failures}).`,
    );
  }

  try {
    requirePackIdentity(manifest?.pack, `${label} manifest.pack`);
  } catch (error) {
    issues.push(error.message);
  }
  return issues;
}

/**
 * Validate the fail-closed NEI extraction telemetry emitted by the GTNH
 * exporter. These checks intentionally reject schema drift: an unknown
 * handler/category counter must be added to this contract before publication,
 * never ignored by an older viewer.
 */
export function gtnhManifestQualityIssues(manifest, label = 'GT New Horizons') {
  const issues = [];
  const counts = manifest?.counts;
  if (!hasExactKeys(counts, STANDARD_COUNT_KEYS)) {
    issues.push(
      `${label} manifest.counts must contain exactly ${STANDARD_COUNT_KEYS.join(', ')}.`,
    );
  }
  for (const name of STANDARD_COUNT_KEYS) {
    if (!isNonNegativeSafeInteger(counts?.[name])) {
      issues.push(`${label} requires a non-negative manifest.counts.${name} value.`);
    }
  }
  for (const name of ['items', 'recipes', 'categories']) {
    if (isNonNegativeSafeInteger(counts?.[name]) && counts[name] === 0) {
      issues.push(`${label} requires manifest.counts.${name} to be positive.`);
    }
  }
  if (isNonNegativeSafeInteger(counts?.failures) && counts.failures !== 0) {
    issues.push(
      `${label} requires manifest.counts.failures to be 0; received ${counts.failures}.`,
    );
  }

  const diagnostics = manifest?.diagnostics;
  if (!hasExactKeys(diagnostics, GTNH_DIAGNOSTIC_KEYS)) {
    issues.push(
      `${label} manifest.diagnostics must contain exactly ${GTNH_DIAGNOSTIC_KEYS.join(', ')}.`,
    );
  }
  if (
    isNonNegativeSafeInteger(diagnostics?.failureEvents) &&
    diagnostics.failureEvents !== 0
  ) {
    issues.push(
      `${label} requires diagnostics.failureEvents to be 0; received ` +
        `${diagnostics.failureEvents}.`,
    );
  }
  if (
    isNonNegativeSafeInteger(diagnostics?.failureEvents) &&
    isNonNegativeSafeInteger(counts?.failures) &&
    diagnostics.failureEvents !== counts.failures
  ) {
    issues.push(
      `${label} diagnostics.failureEvents (${diagnostics.failureEvents}) must equal ` +
        `manifest.counts.failures (${counts.failures}).`,
    );
  }

  const nei = diagnostics?.nei;
  if (!hasExactKeys(nei, GTNH_NEI_DIAGNOSTIC_KEYS)) {
    issues.push(
      `${label} diagnostics.nei must contain exactly ${GTNH_NEI_DIAGNOSTIC_KEYS.join(', ')}.`,
    );
  }
  if (nei?.itemListLoaded !== true) {
    issues.push(`${label} requires diagnostics.nei.itemListLoaded to be true.`);
  }
  const numericKeys = GTNH_NEI_DIAGNOSTIC_KEYS.filter(name => name !== 'itemListLoaded');
  for (const name of numericKeys) {
    if (!isNonNegativeSafeInteger(nei?.[name])) {
      issues.push(`${label} requires diagnostics.nei.${name} to be a non-negative safe integer.`);
    }
  }
  for (const name of [
    'unloadedHandlerCategories',
    'ambiguousHandlerCategories',
    'duplicateHandlerCategories',
  ]) {
    if (isNonNegativeSafeInteger(nei?.[name]) && nei[name] !== 0) {
      issues.push(
        `${label} requires diagnostics.nei.${name} to be 0; received ${nei[name]}.`,
      );
    }
  }

  const compareTelemetry = (leftName, rightValue, rightLabel) => {
    const leftValue = nei?.[leftName];
    if (
      isNonNegativeSafeInteger(leftValue) &&
      isNonNegativeSafeInteger(rightValue) &&
      leftValue !== rightValue
    ) {
      issues.push(
        `${label} diagnostics.nei.${leftName} (${leftValue}) must equal ` +
          `${rightLabel} (${rightValue}).`,
      );
    }
  };
  compareTelemetry(
    'registeredCraftingHandlers',
    counts?.categories,
    'manifest.counts.categories',
  );
  compareTelemetry('loadedCategories', counts?.categories, 'manifest.counts.categories');
  compareTelemetry('recipesEnumerated', counts?.recipes, 'manifest.counts.recipes');
  compareTelemetry('recipeWidgetsRendered', counts?.recipes, 'manifest.counts.recipes');
  compareTelemetry('itemIconsRendered', counts?.items, 'manifest.counts.items');

  return issues;
}

/**
 * Return profile-specific publication blockers. General graph and asset
 * integrity is enforced by validate-export-data; this policy covers the
 * explicit pack exporter contracts that generic datasets do not share.
 */
export function exportQualityIssues({manifest, failures, semanticErrorRecipes = 0}, profile) {
  const resolvedProfile = resolveQualityProfile(profile);
  if (resolvedProfile === null) return [];
  const requirements = profileRequirements.get(resolvedProfile);
  const {label} = requirements;

  const issues = [];
  if (manifest?.format !== requirements.format) {
    issues.push(
      `${label} requires manifest.format ${requirements.format}; received ${String(
        manifest?.format,
      )}.`,
    );
  }
  if (manifest?.minecraft !== requirements.minecraft) {
    issues.push(
      `${label} requires manifest.minecraft ${JSON.stringify(requirements.minecraft)}; ` +
        `received ${JSON.stringify(manifest?.minecraft)}.`,
    );
  }
  if (manifest?.aborted !== false) {
    issues.push(`${label} requires manifest.aborted to be false.`);
  }
  if (manifest?.settings?.iconScale !== requirements.iconScale) {
    const itemCanvas = 16 * requirements.iconScale;
    issues.push(
      `${label} requires 16×16 source textures rendered into ${itemCanvas}×${itemCanvas} ` +
        `item canvases (settings.iconScale ${requirements.iconScale}); received ${String(
        manifest?.settings?.iconScale,
      )}.`,
    );
  }
  if (manifest?.settings?.recipeScale !== requirements.recipeScale) {
    issues.push(
      `${label} requires ${requirements.recipeViewer} layouts rendered at ` +
        `${requirements.recipeScale}× physical resolution ` +
        `(settings.recipeScale ${requirements.recipeScale}); received ${String(
        manifest?.settings?.recipeScale,
      )}.`,
    );
  }
  for (const [name, expected] of Object.entries(requirements.provenance ?? {})) {
    if (manifest?.[name] !== expected) {
      issues.push(
        `${label} requires manifest.${name} ${JSON.stringify(expected)}; received ` +
          `${JSON.stringify(manifest?.[name])}.`,
      );
    }
  }
  if (requirements.packIdentity !== undefined) {
    if (!hasExactKeys(manifest?.pack, Object.keys(requirements.packIdentity))) {
      issues.push(
        `${label} requires manifest.pack to contain exactly ` +
          `${Object.keys(requirements.packIdentity).join(', ')}.`,
      );
    } else {
      for (const [name, expected] of Object.entries(requirements.packIdentity)) {
        if (manifest.pack[name] !== expected) {
          issues.push(
            `${label} requires manifest.pack.${name} ${JSON.stringify(expected)}; received ` +
              `${JSON.stringify(manifest.pack[name])}.`,
          );
        }
      }
    }
  }

  const diagnostics = manifest?.diagnostics;
  if (!isRecord(diagnostics)) {
    issues.push(`${label} requires manifest.diagnostics from its exporter.`);
  } else {
    if (!Number.isSafeInteger(diagnostics.failureEvents) || diagnostics.failureEvents < 0) {
      issues.push(`${label} requires a non-negative diagnostics.failureEvents count.`);
    }
    if (
      !Number.isSafeInteger(diagnostics.failureEventsOmitted) ||
      diagnostics.failureEventsOmitted < 0
    ) {
      issues.push(`${label} requires a non-negative diagnostics.failureEventsOmitted count.`);
    } else if (diagnostics.failureEventsOmitted !== 0) {
      issues.push(
        `${label} export omitted ${diagnostics.failureEventsOmitted} failure event(s); ` +
          'publication requires the complete diagnostics set.',
      );
    }
  }

  if (resolvedProfile === GTNH_1710_PROFILE) {
    issues.push(...gtnhManifestQualityIssues(manifest, label));
  } else if (resolvedProfile === GENERIC_JEI_120_PROFILE) {
    issues.push(...genericJeiManifestQualityIssues(manifest, label));
  }

  if (!Array.isArray(failures)) {
    issues.push(`${label} requires failures.json to contain an array.`);
    return issues;
  }

  if (resolvedProfile === GTNH_1710_PROFILE && failures.length !== 0) {
    issues.push(
      `${label} requires failures.json to be empty; received ${failures.length} ` +
        `failure event(s); first: ${String(failures[0])}`,
    );
  }
  const categoryFailures = failures.filter(
    message => typeof message === 'string' && isCategoryFailure(message),
  );
  if (categoryFailures.length > 0) {
    issues.push(
      `${label} export contains ${categoryFailures.length} category failure(s); first: ` +
        categoryFailures[0],
    );
  }

  const quantityFallbacks = failures.filter(
    message => typeof message === 'string' && isQuantityFallback(message),
  );
  if (quantityFallbacks.length > 0) {
    issues.push(
      `${label} export contains ${quantityFallbacks.length} ingredient-quantity ` +
        `fallback(s); first: ${quantityFallbacks[0]}`,
    );
  }

  const recipePreviewFailures = failures.filter(
    message => typeof message === 'string' && isRecipePreviewFailure(message),
  );
  if (recipePreviewFailures.length > 0) {
    issues.push(
      `${label} export contains ${recipePreviewFailures.length} recipe-preview render/write ` +
        `failure(s); first: ${recipePreviewFailures[0]}`,
    );
  }

  const unclassifiedZeros = failures.filter(
    message => typeof message === 'string' && isUnclassifiedZeroQuantity(message),
  );
  if (unclassifiedZeros.length > 0) {
    issues.push(
      `${label} export contains ${unclassifiedZeros.length} unclassified zero-quantity ` +
        `context(s); first: ${unclassifiedZeros[0]}`,
    );
  }

  const catalogFailures = failures.filter(
    message => typeof message === 'string' && isCatalogCompletenessFailure(message),
  );
  if (catalogFailures.length > 0) {
    issues.push(
      `${label} export contains ${catalogFailures.length} incomplete ingredient-catalog ` +
        `failure(s); first: ${catalogFailures[0]}`,
    );
  }

  const semanticFailures = failures.filter(
    message => typeof message === 'string' && isSemanticFailure(message),
  );
  if (semanticFailures.length > 0) {
    issues.push(
      `${label} export contains ${semanticFailures.length} ingredient-semantics ` +
        `failure(s); first: ${semanticFailures[0]}`,
    );
  }
  if (!Number.isSafeInteger(semanticErrorRecipes) || semanticErrorRecipes < 0) {
    issues.push(`${label} semantic-error recipe count is invalid.`);
  } else if (semanticErrorRecipes > 0) {
    issues.push(
      `${label} export contains ${semanticErrorRecipes} recipe(s) marked err=true; ` +
        'publication requires complete input/output semantics.',
    );
  }

  return issues;
}
