import {isRecord} from './export-data-utils.mjs';

export const MEATBALLCRAFT_112_PROFILE = 'meatballcraft-1.12.2';
export const MULTIBLOCK_MADNESS_112_PROFILE = 'multiblock-madness-1.12.2';
export const MULTIBLOCK_MADNESS_2_118_PROFILE = 'multiblock-madness-2-1.18.2';

const profileRequirements = new Map([
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
  return /^(?:category\s|reading category id while filtering:)/i.test(message.trim());
}

function isSemanticFailure(message) {
  return /^(?:recipe semantics\s|recipe (?:input|output) ingredient\s)/i.test(message.trim());
}

function isQuantityFallback(message) {
  return /^ingredient amount type\s.+\susing quantity 1(?:\s|$)/i.test(message.trim());
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
    /^(?:list ingredients\b|item\s.+\s#\d+:)/i.test(normalized) ||
    /^ingredient (?:unique id|display name|resource id)\b/i.test(normalized) ||
    /\bmissing item\b/i.test(normalized)
  );
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

  if (!Array.isArray(failures)) {
    issues.push(`${label} requires failures.json to contain an array.`);
    return issues;
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
