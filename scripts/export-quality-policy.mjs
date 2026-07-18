import {isRecord} from './export-data-utils.mjs';

export const MEATBALLCRAFT_112_PROFILE = 'meatballcraft-1.12.2';

const profiles = new Set([MEATBALLCRAFT_112_PROFILE]);

export function resolveQualityProfile(profile) {
  if (profile === undefined || profile === null) return null;
  if (!profiles.has(profile)) {
    throw new Error(
      `Unknown export quality profile ${JSON.stringify(profile)}. Supported profiles: ` +
        [...profiles].join(', '),
    );
  }
  return profile;
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
 * MeatballCraft exporter contract that generic datasets do not share.
 */
export function exportQualityIssues({manifest, failures, semanticErrorRecipes = 0}, profile) {
  const resolvedProfile = resolveQualityProfile(profile);
  if (resolvedProfile === null) return [];

  const issues = [];
  if (manifest?.format !== 1) {
    issues.push(`MeatballCraft requires manifest.format 1; received ${String(manifest?.format)}.`);
  }
  if (manifest?.minecraft !== '1.12.2') {
    issues.push(
      `MeatballCraft requires manifest.minecraft "1.12.2"; received ${JSON.stringify(
        manifest?.minecraft,
      )}.`,
    );
  }
  if (manifest?.aborted !== false) {
    issues.push('MeatballCraft requires manifest.aborted to be false.');
  }
  if (manifest?.settings?.iconScale !== 1) {
    issues.push(
      `MeatballCraft requires native 16×16 item renders (settings.iconScale 1); received ${String(
        manifest?.settings?.iconScale,
      )}.`,
    );
  }
  if (manifest?.settings?.recipeScale !== 1) {
    issues.push(
      `MeatballCraft requires native JEI layout resolution (settings.recipeScale 1); received ${String(
        manifest?.settings?.recipeScale,
      )}.`,
    );
  }

  const diagnostics = manifest?.diagnostics;
  if (!isRecord(diagnostics)) {
    issues.push('MeatballCraft requires manifest.diagnostics from the 1.12.2 exporter.');
  } else {
    if (!Number.isSafeInteger(diagnostics.failureEvents) || diagnostics.failureEvents < 0) {
      issues.push('MeatballCraft requires a non-negative diagnostics.failureEvents count.');
    }
    if (
      !Number.isSafeInteger(diagnostics.failureEventsOmitted) ||
      diagnostics.failureEventsOmitted < 0
    ) {
      issues.push('MeatballCraft requires a non-negative diagnostics.failureEventsOmitted count.');
    } else if (diagnostics.failureEventsOmitted !== 0) {
      issues.push(
        `MeatballCraft export omitted ${diagnostics.failureEventsOmitted} failure event(s); ` +
          'publication requires the complete diagnostics set.',
      );
    }
  }

  if (!Array.isArray(failures)) {
    issues.push('MeatballCraft requires failures.json to contain an array.');
    return issues;
  }

  const categoryFailures = failures.filter(
    message => typeof message === 'string' && isCategoryFailure(message),
  );
  if (categoryFailures.length > 0) {
    issues.push(
      `MeatballCraft export contains ${categoryFailures.length} category failure(s); first: ` +
        categoryFailures[0],
    );
  }

  const quantityFallbacks = failures.filter(
    message => typeof message === 'string' && isQuantityFallback(message),
  );
  if (quantityFallbacks.length > 0) {
    issues.push(
      `MeatballCraft export contains ${quantityFallbacks.length} ingredient-quantity ` +
        `fallback(s); first: ${quantityFallbacks[0]}`,
    );
  }

  const unclassifiedZeros = failures.filter(
    message => typeof message === 'string' && isUnclassifiedZeroQuantity(message),
  );
  if (unclassifiedZeros.length > 0) {
    issues.push(
      `MeatballCraft export contains ${unclassifiedZeros.length} unclassified zero-quantity ` +
        `context(s); first: ${unclassifiedZeros[0]}`,
    );
  }

  const catalogFailures = failures.filter(
    message => typeof message === 'string' && isCatalogCompletenessFailure(message),
  );
  if (catalogFailures.length > 0) {
    issues.push(
      `MeatballCraft export contains ${catalogFailures.length} incomplete ingredient-catalog ` +
        `failure(s); first: ${catalogFailures[0]}`,
    );
  }

  const semanticFailures = failures.filter(
    message => typeof message === 'string' && isSemanticFailure(message),
  );
  if (semanticFailures.length > 0) {
    issues.push(
      `MeatballCraft export contains ${semanticFailures.length} ingredient-semantics ` +
        `failure(s); first: ${semanticFailures[0]}`,
    );
  }
  if (!Number.isSafeInteger(semanticErrorRecipes) || semanticErrorRecipes < 0) {
    issues.push('MeatballCraft semantic-error recipe count is invalid.');
  } else if (semanticErrorRecipes > 0) {
    issues.push(
      `MeatballCraft export contains ${semanticErrorRecipes} recipe(s) marked err=true; ` +
        'publication requires complete input/output semantics.',
    );
  }

  return issues;
}
