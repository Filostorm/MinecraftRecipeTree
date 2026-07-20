import {availableParallelism} from 'node:os';
import {createHash, randomUUID} from 'node:crypto';
import {
  lstat,
  mkdir,
  readFile,
  realpath,
  rename,
  rm,
  writeFile,
} from 'node:fs/promises';
import {basename, dirname, join, posix, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {isDeepStrictEqual} from 'node:util';
import sharp from 'sharp';
import {
  EXPORT_QUALITY_PROFILE_IDS,
  exportQualityIssues,
  GENERIC_JEI_120_PROFILE,
  GTNH_DATA_ATTRIBUTION,
  GTNH_HANDLER_POLICIES,
  GTNH_1710_PROFILE,
  GTNH_KNOWLEDGE_POLICY,
  gtnhManifestQualityIssues,
  MEATBALLCRAFT_112_PROFILE,
  MULTIBLOCK_MADNESS_112_PROFILE,
  MULTIBLOCK_MADNESS_2_118_PROFILE,
  qualityProfileRequirementsFor,
  resolveQualityProfile,
} from './export-quality-policy.mjs';
import {parsePackedImagePath} from './packed-assets.mjs';
import {requirePackIdentity} from './pack-identity.mjs';
import {computePublicationId} from './publication-id.mjs';
import {
  createRecipeImageInventory,
  decodedRgbaSha256,
  normalizedLogicalRecipePngPath,
  requireRecipeImageInventory,
} from './recipe-image-inventory.mjs';
import {MAX_SHARD_BYTES, SHARDED_JSON_FORMAT} from './sharded-documents.mjs';

export const RECIPE_PREVIEW_SIDECAR_FORMAT = 'mrt-recipe-preview-sidecar-v1';
export const RECIPE_PREVIEW_CATEGORY_FORMAT = 'mrt-recipe-preview-category-v1';
export const RECIPE_PREVIEW_PACK_INDEX_FORMAT = 'mrt-recipe-preview-pack-index-v1';
export const MAX_PACK_BYTES = 1024 * 1024;
export const MAX_CATEGORY_BYTES = 256 * 1024;
export const MAX_PACK_INDEX_BYTES = 512 * 1024;
export const IMAGE_FORMAT = 'lossless-webp';
export const DATASET_PUBLICATION_ID_PATTERN = /^[a-f0-9]{64}$/;

const PNG_SIGNATURE = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
const MAX_IMAGE_DIMENSION = 8192;
const MAX_IMAGE_PIXELS = MAX_IMAGE_DIMENSION * MAX_IMAGE_DIMENSION;
const WEBP_EFFORT = 4;
const PACK_INDEX_MAGIC = Buffer.from('MRPI', 'ascii');
const PACK_INDEX_VERSION = 1;
const PACK_INDEX_HEADER_BYTES = 20;
const PACK_INDEX_ENTRY_BYTES = 8;
const DATASET_COUNT_KEYS = Object.freeze([
  'items',
  'recipes',
  'categories',
  'mobs',
  'blockDrops',
  'failures',
]);
const DATASET_DIAGNOSTIC_KEYS = Object.freeze([
  'failureEvents',
  'failureEventsOmitted',
]);
const MM1_DATASET_DIAGNOSTIC_KEYS = Object.freeze([
  ...DATASET_DIAGNOSTIC_KEYS,
  'warningEvents',
  'warningEventsOmitted',
]);
const MM2_DATASET_COUNT_KEYS = Object.freeze([
  ...DATASET_COUNT_KEYS,
  'nativeIconCorrections',
]);
const MM2_DATASET_DIAGNOSTIC_KEYS = Object.freeze([
  ...DATASET_DIAGNOSTIC_KEYS,
  'nativeIconCorrections',
  'transparentIcons',
]);
const GTNH_DATASET_DIAGNOSTIC_KEYS = Object.freeze([
  ...DATASET_DIAGNOSTIC_KEYS,
  'nei',
]);
const GTNH_PROVENANCE_KEYS = Object.freeze(['profile', 'forge', 'nei']);
const RESOURCE_LOCATION_PATTERN = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const HOSTED_WEB_CONTRACT = Object.freeze({
  format: 2,
  packedImages: 'coordinate-v1',
  maxPackBytes: 1024 * 1024,
  shardedJson: 'mrt-sharded-json-v1',
  maxShardBytes: 8 * 1024 * 1024,
});

export const MEATBALLCRAFT_CONTRACT = Object.freeze({
  format: 1,
  minecraft: '1.12.2',
  settings: Object.freeze({
    iconScale: 3,
    recipeScale: 2,
    mobCanvas: 256,
    worldStartupOptimization: Object.freeze({
      enabled: true,
      policy: 'dimension-0-plus-should-load-spawn',
      applied: true,
      originalDimensions: 93,
      selectedDimensions: 4,
      skippedDimensions: 89,
    }),
  }),
  counts: Object.freeze({
    items: 196161,
    recipes: 359215,
    categories: 674,
    mobs: 0,
    blockDrops: 0,
    failures: 130,
  }),
  diagnostics: Object.freeze({failureEvents: 130, failureEventsOmitted: 0}),
  recipeImages: Object.freeze({previews: 359215, missing: 0}),
  hostedWeb: HOSTED_WEB_CONTRACT,
  repairProvenance: Object.freeze({
    format: 'mrt-recipe-preview-repair-overlay-v1',
    method: 'canonical-deep-equality-sample-overlay',
    repairedRecipePreviews: 27,
    compatibilityDiagnostics: Object.freeze({
      'zmaster587.AR.chemicalReactor': 25,
      'buildcraft:category_heatable': 1,
      'buildcraft:category_coolable': 1,
    }),
    hashAlgorithm: 'sha256',
    treeHashFormat: 'mrt-plain-content-tree-sha256-v1',
    canonicalSha256: '11b9cbf2a8b7b1a65995612fa804dbeaf6c2d36ed1b16318783cd4d9064c4af4',
  }),
});

function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function profileQualityFailureMessage(profile, label, issues) {
  const requirements = qualityProfileRequirementsFor(profile);
  return (
    `${label} failed the ${requirements.label} quality gate with ${issues.length} issue(s):\n` +
    issues.map(issue => `- ${issue}`).join('\n')
  );
}

function manifestCountKeysForProfile(profile) {
  return profile === MULTIBLOCK_MADNESS_2_118_PROFILE
    ? MM2_DATASET_COUNT_KEYS
    : DATASET_COUNT_KEYS;
}

function manifestDiagnosticKeysForProfile(profile) {
  if (profile === MULTIBLOCK_MADNESS_112_PROFILE) return MM1_DATASET_DIAGNOSTIC_KEYS;
  if (profile === MULTIBLOCK_MADNESS_2_118_PROFILE) return MM2_DATASET_DIAGNOSTIC_KEYS;
  if (profile === GTNH_1710_PROFILE) return GTNH_DATASET_DIAGNOSTIC_KEYS;
  return DATASET_DIAGNOSTIC_KEYS;
}

function requireNonNegativeSafeInteger(value, label) {
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new Error(`${label} must be a non-negative safe integer.`);
  }
  return value;
}

function validateMm1QualitySample(value, manifest, label) {
  if (!hasExactKeys(value, ['enabled', 'recipeTargets', 'selectorCounts'])) {
    throw new Error(
      `${label} must contain exactly enabled, recipeTargets, and selectorCounts.`,
    );
  }
  if (value.enabled !== true) throw new Error(`${label}.enabled must be true.`);
  const recipeTargets = requireNonNegativeSafeInteger(
    value.recipeTargets,
    `${label}.recipeTargets`,
  );
  if (recipeTargets <= 0) throw new Error(`${label}.recipeTargets must be positive.`);
  if (!hasExactKeys(value.selectorCounts, ['recipeId', 'sourceIndex'])) {
    throw new Error(`${label}.selectorCounts must contain exactly recipeId and sourceIndex.`);
  }
  const recipeId = requireNonNegativeSafeInteger(
    value.selectorCounts.recipeId,
    `${label}.selectorCounts.recipeId`,
  );
  const sourceIndex = requireNonNegativeSafeInteger(
    value.selectorCounts.sourceIndex,
    `${label}.selectorCounts.sourceIndex`,
  );
  if (recipeId + sourceIndex !== recipeTargets) {
    throw new Error(`${label}.selectorCounts must sum to recipeTargets.`);
  }
  if (recipeTargets !== manifest.counts.recipes) {
    throw new Error(
      `${label}.recipeTargets must equal manifest.counts.recipes for a sampled export.`,
    );
  }
  return Object.freeze({
    enabled: true,
    recipeTargets,
    selectorCounts: Object.freeze({recipeId, sourceIndex}),
  });
}

function validateMm2QualitySample(value, manifest, label) {
  if (!hasExactKeys(value, ['selectorCounts', 'requested'])) {
    throw new Error(`${label} must contain exactly selectorCounts and requested.`);
  }
  if (!hasExactKeys(value.selectorCounts, ['recipeId', 'sourceIndex'])) {
    throw new Error(`${label}.selectorCounts must contain exactly recipeId and sourceIndex.`);
  }
  const recipeId = requireNonNegativeSafeInteger(
    value.selectorCounts.recipeId,
    `${label}.selectorCounts.recipeId`,
  );
  const sourceIndex = requireNonNegativeSafeInteger(
    value.selectorCounts.sourceIndex,
    `${label}.selectorCounts.sourceIndex`,
  );
  if (recipeId !== 0) {
    throw new Error(`${label}.selectorCounts.recipeId must be 0 for the REI exporter.`);
  }
  if (!Array.isArray(value.requested) || value.requested.length === 0 || value.requested.length > 32) {
    throw new Error(`${label}.requested must contain 1..32 selectors.`);
  }
  if (sourceIndex !== value.requested.length) {
    throw new Error(`${label}.selectorCounts.sourceIndex must equal requested.length.`);
  }
  if (value.requested.length !== manifest.counts.recipes) {
    throw new Error(
      `${label}.requested.length must equal manifest.counts.recipes for a sampled export.`,
    );
  }
  const seen = new Set();
  const requested = value.requested.map((selector, index) => {
    const selectorLabel = `${label}.requested[${index}]`;
    if (!hasExactKeys(selector, ['categoryId', 'sourceIndex'])) {
      throw new Error(`${selectorLabel} must contain exactly categoryId and sourceIndex.`);
    }
    if (
      typeof selector.categoryId !== 'string' ||
      !RESOURCE_LOCATION_PATTERN.test(selector.categoryId)
    ) {
      throw new Error(`${selectorLabel}.categoryId must be a canonical resource location.`);
    }
    const selectorSourceIndex = requireNonNegativeSafeInteger(
      selector.sourceIndex,
      `${selectorLabel}.sourceIndex`,
    );
    const identity = `${selector.categoryId}\u0000${selectorSourceIndex}`;
    if (seen.has(identity)) throw new Error(`${selectorLabel} duplicates an earlier selector.`);
    seen.add(identity);
    return Object.freeze({
      categoryId: selector.categoryId,
      sourceIndex: selectorSourceIndex,
    });
  });
  return Object.freeze({
    selectorCounts: Object.freeze({recipeId, sourceIndex}),
    requested: Object.freeze(requested),
  });
}

function validateProfileManifestExtensions(manifest, profile, label) {
  const qualitySample = manifest.qualitySample;
  let normalizedQualitySample;
  if (qualitySample !== undefined) {
    if (profile === MULTIBLOCK_MADNESS_112_PROFILE) {
      normalizedQualitySample = validateMm1QualitySample(
        qualitySample,
        manifest,
        `${label}.qualitySample`,
      );
    } else if (profile === MULTIBLOCK_MADNESS_2_118_PROFILE) {
      normalizedQualitySample = validateMm2QualitySample(
        qualitySample,
        manifest,
        `${label}.qualitySample`,
      );
    } else {
      throw new Error(`${label}.qualitySample is not permitted by this profile contract.`);
    }
  }

  if (profile === MULTIBLOCK_MADNESS_2_118_PROFILE) {
    const count = requireNonNegativeSafeInteger(
      manifest.counts.nativeIconCorrections,
      `${label}.counts.nativeIconCorrections`,
    );
    const diagnostic = requireNonNegativeSafeInteger(
      manifest.diagnostics.nativeIconCorrections,
      `${label}.diagnostics.nativeIconCorrections`,
    );
    const transparentIcons = requireNonNegativeSafeInteger(
      manifest.diagnostics.transparentIcons,
      `${label}.diagnostics.transparentIcons`,
    );
    if (count !== diagnostic) {
      throw new Error(
        `${label} native-icon correction counts disagree: counts=${count}, diagnostics=${diagnostic}.`,
      );
    }
    if (count > manifest.counts.items) {
      throw new Error(
        `${label}.counts.nativeIconCorrections cannot exceed manifest.counts.items.`,
      );
    }
    if (transparentIcons !== 0) {
      throw new Error(
        `${label}.diagnostics.transparentIcons must be 0 for publication; received ${transparentIcons}.`,
      );
    }
  }
  if (profile === MULTIBLOCK_MADNESS_112_PROFILE) {
    requireNonNegativeSafeInteger(
      manifest.diagnostics.warningEvents,
      `${label}.diagnostics.warningEvents`,
    );
    const warningEventsOmitted = requireNonNegativeSafeInteger(
      manifest.diagnostics.warningEventsOmitted,
      `${label}.diagnostics.warningEventsOmitted`,
    );
    if (warningEventsOmitted !== 0) {
      throw new Error(
        `${label}.diagnostics.warningEventsOmitted must be 0 for publication; received ` +
          `${warningEventsOmitted}.`,
      );
    }
  }
  if (profile === GENERIC_JEI_120_PROFILE) {
    requirePackIdentity(manifest.pack, `${label}.pack`);
  }
  if (profile === GTNH_1710_PROFILE) {
    const issues = gtnhManifestQualityIssues(manifest, label);
    if (issues.length > 0) {
      throw new Error(
        `${label} failed the strict GTNH NEI telemetry contract with ` +
          `${issues.length} issue(s):\n${issues.map(issue => `- ${issue}`).join('\n')}`,
      );
    }
    const requirements = qualityProfileRequirementsFor(profile);
    for (const [name, expected] of Object.entries(requirements.provenance)) {
      if (manifest[name] !== expected) {
        throw new Error(
          `${label}.${name} must be ${JSON.stringify(expected)}; received ` +
            `${JSON.stringify(manifest[name])}.`,
        );
      }
    }
    const pack = requirePackIdentity(manifest.pack, `${label}.pack`);
    if (!isDeepStrictEqual(pack, requirements.packIdentity)) {
      throw new Error(
        `${label}.pack must be the exact GTNH identity ${JSON.stringify(
          requirements.packIdentity,
        )}; received ${JSON.stringify(pack)}.`,
      );
    }
  }
  return normalizedQualitySample;
}

function assertProfileQuality(input, profile, label) {
  if (profile === null) return;
  const issues = exportQualityIssues(input, profile);
  if (issues.length > 0) throw new Error(profileQualityFailureMessage(profile, label, issues));
}

/**
 * Resolve the sidecar's audited corpus contract. MeatballCraft remains pinned
 * to its immutable known-good counts and repair provenance. New pack profiles
 * derive counts from the validated raw manifest, but require every declared
 * recipe to have a preview and retain exact hosted/raw identity checks.
 */
export function recipePreviewContractForProfile(profile, rawManifest, warnings) {
  const resolvedProfile = resolveQualityProfile(profile);
  if (resolvedProfile === null) {
    throw new Error(
      `An explicit recipe-preview quality profile is required. Supported profiles: ` +
        EXPORT_QUALITY_PROFILE_IDS.join(', '),
    );
  }
  if (resolvedProfile === MEATBALLCRAFT_112_PROFILE) {
    if (rawManifest?.pack === undefined) return MEATBALLCRAFT_CONTRACT;
    return Object.freeze({
      ...MEATBALLCRAFT_CONTRACT,
      pack: requirePackIdentity(rawManifest.pack, 'MeatballCraft manifest.pack'),
    });
  }

  const requirements = qualityProfileRequirementsFor(resolvedProfile);
  assertProfileQuality(
    {manifest: rawManifest, failures: [], warnings, semanticErrorRecipes: 0},
    resolvedProfile,
    'Raw export manifest',
  );
  const countKeys = manifestCountKeysForProfile(resolvedProfile);
  const diagnosticKeys = manifestDiagnosticKeysForProfile(resolvedProfile);
  if (!isRecord(rawManifest?.settings)) {
    throw new Error(`${requirements.label} manifest.settings must be an object.`);
  }
  if (!hasExactKeys(rawManifest?.counts, countKeys)) {
    throw new Error(
      `${requirements.label} manifest.counts must contain exactly ` +
        `${countKeys.join(', ')}.`,
    );
  }
  if (!hasExactKeys(rawManifest?.diagnostics, diagnosticKeys)) {
    throw new Error(
      `${requirements.label} manifest.diagnostics must contain exactly ` +
        `${diagnosticKeys.join(', ')}.`,
    );
  }
  if (rawManifest.diagnostics.failureEvents !== rawManifest.counts.failures) {
    throw new Error(
      `${requirements.label} manifest diagnostics/counts disagree: ` +
        `failureEvents=${String(rawManifest.diagnostics.failureEvents)}, ` +
        `counts.failures=${String(rawManifest.counts.failures)}.`,
    );
  }
  const qualitySample = validateProfileManifestExtensions(
    rawManifest,
    resolvedProfile,
    `${requirements.label} manifest`,
  );

  const diagnostics = Object.fromEntries(
    diagnosticKeys.map(name => [name, rawManifest.diagnostics[name]]),
  );
  if (resolvedProfile === GTNH_1710_PROFILE) {
    diagnostics.nei = Object.freeze({...diagnostics.nei});
  }

  return Object.freeze({
    format: requirements.format,
    minecraft: requirements.minecraft,
    ...(requirements.provenance ?? {}),
    ...(resolvedProfile === GTNH_1710_PROFILE
      ? {
          handlerPolicies: GTNH_HANDLER_POLICIES,
          knowledgePolicy: GTNH_KNOWLEDGE_POLICY,
          attribution: GTNH_DATA_ATTRIBUTION,
        }
      : {}),
    ...(rawManifest.pack === undefined
      ? {}
      : {pack: requirePackIdentity(rawManifest.pack, `${requirements.label} manifest.pack`)}),
    settings: Object.freeze({
      ...rawManifest.settings,
      ...(isRecord(rawManifest.settings.worldStartupOptimization)
        ? {
            worldStartupOptimization: Object.freeze({
              ...rawManifest.settings.worldStartupOptimization,
            }),
          }
        : {}),
    }),
    counts: Object.freeze(
      Object.fromEntries(countKeys.map(name => [name, rawManifest.counts[name]])),
    ),
    diagnostics: Object.freeze(diagnostics),
    ...(qualitySample === undefined ? {} : {qualitySample}),
    recipeImages: Object.freeze({previews: rawManifest.counts.recipes, missing: 0}),
    hostedWeb: HOSTED_WEB_CONTRACT,
  });
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function jsonBytes(value) {
  return Buffer.from(`${JSON.stringify(value)}\n`, 'utf8');
}

function indexName(index) {
  return String(index).padStart(3, '0');
}

function hasExactKeys(value, expected) {
  if (!isRecord(value)) return false;
  const actual = Object.keys(value).sort();
  const wanted = [...expected].sort();
  return actual.length === wanted.length && actual.every((key, index) => key === wanted[index]);
}

function assertExactRecord(value, expected, label) {
  if (!hasExactKeys(value, Object.keys(expected))) {
    throw new Error(
      `${label} must contain exactly ${Object.keys(expected).join(', ')}; received ` +
        `${isRecord(value) ? Object.keys(value).join(', ') : typeof value}.`,
    );
  }
  for (const [key, expectedValue] of Object.entries(expected)) {
    if (isRecord(expectedValue)) {
      assertExactRecord(value[key], expectedValue, `${label}.${key}`);
    } else if (!isDeepStrictEqual(value[key], expectedValue)) {
      throw new Error(
        `${label}.${key} must be ${JSON.stringify(expectedValue)}; received ` +
          `${JSON.stringify(value[key])}.`,
      );
    }
  }
}

function canonicalJson(value) {
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  if (isRecord(value)) {
    return `{${Object.keys(value)
      .sort()
      .map(key => `${JSON.stringify(key)}:${canonicalJson(value[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
}

function canonicalJsonSha256(value) {
  return sha256(Buffer.from(canonicalJson(value), 'utf8'));
}

function validateWorldStartupOptimization(value, label) {
  if (!hasExactKeys(value, [
    'enabled',
    'policy',
    'applied',
    'originalDimensions',
    'selectedDimensions',
    'skippedDimensions',
  ])) {
    throw new Error(
      `${label} must contain the exact audited dimension-selection fields.`,
    );
  }
  if (value.enabled !== true || value.applied !== true) {
    throw new Error(`${label}.enabled and ${label}.applied must both be true.`);
  }
  if (value.policy !== 'dimension-0-plus-should-load-spawn') {
    throw new Error(
      `${label}.policy must be "dimension-0-plus-should-load-spawn".`,
    );
  }
  for (const name of ['originalDimensions', 'selectedDimensions', 'skippedDimensions']) {
    if (!Number.isSafeInteger(value[name]) || value[name] < 0) {
      throw new Error(`${label}.${name} must be a non-negative safe integer.`);
    }
  }
  if (value.selectedDimensions + value.skippedDimensions !== value.originalDimensions) {
    throw new Error(`${label} selected plus skipped dimensions must equal original dimensions.`);
  }
}

function validateRepairProvenance(manifest, contract, label) {
  const expected = contract.repairProvenance;
  const present = Object.hasOwn(manifest, 'repairProvenance');
  if (expected === undefined) {
    if (present) {
      throw new Error(`${label}.repairProvenance is present but the dataset contract forbids it.`);
    }
    return;
  }
  if (!present || !isRecord(manifest.repairProvenance)) {
    throw new Error(`${label}.repairProvenance must be the audited repair record.`);
  }
  const provenance = manifest.repairProvenance;
  const summary = {
    format: provenance.format,
    method: provenance.method,
    repairedRecipePreviews: provenance.repairedRecipePreviews,
    compatibilityDiagnostics: provenance.compatibilityDiagnostics,
    hashAlgorithm: provenance.hashAlgorithm,
    treeHashFormat: provenance.treeHashFormat,
  };
  const expectedSummary = {...expected};
  delete expectedSummary.canonicalSha256;
  assertExactRecord(summary, expectedSummary, `${label}.repairProvenance summary`);
  const actualDigest = canonicalJsonSha256(provenance);
  if (actualDigest !== expected.canonicalSha256) {
    throw new Error(
      `${label}.repairProvenance canonical SHA-256 is ${actualDigest}; expected ` +
        `${expected.canonicalSha256}.`,
    );
  }
}

function validateContract(contract, profile = null) {
  if (!isRecord(contract)) throw new Error('Expected dataset contract must be an object.');
  const countKeys = manifestCountKeysForProfile(profile);
  const diagnosticKeys = manifestDiagnosticKeysForProfile(profile);
  if (!Number.isSafeInteger(contract.format)) {
    throw new Error('Expected dataset contract format must be a safe integer.');
  }
  if (typeof contract.minecraft !== 'string' || contract.minecraft.length === 0) {
    throw new Error('Expected dataset contract minecraft version must be a non-empty string.');
  }
  if (contract.pack !== undefined) {
    requirePackIdentity(contract.pack, 'Expected dataset contract.pack');
  }
  if (
    !isRecord(contract.settings) ||
    !hasExactKeys(contract.counts, countKeys) ||
    !hasExactKeys(contract.diagnostics, diagnosticKeys) ||
    !hasExactKeys(contract.recipeImages, ['previews', 'missing'])
  ) {
    throw new Error(
      'Expected dataset contract must contain settings/counts plus exact diagnostics and ' +
        `recipeImages fields; counts must contain exactly ${countKeys.join(', ')} and ` +
        `diagnostics exactly ${diagnosticKeys.join(', ')}.`,
    );
  }
  const worldStartupOptimization = contract.settings.worldStartupOptimization;
  const expectedSettingKeys = ['iconScale', 'recipeScale', 'mobCanvas'];
  if (worldStartupOptimization !== undefined) expectedSettingKeys.push('worldStartupOptimization');
  if (!hasExactKeys(contract.settings, expectedSettingKeys)) {
    throw new Error('Expected dataset settings contain unsupported or missing fields.');
  }
  for (const name of ['iconScale', 'recipeScale', 'mobCanvas']) {
    const value = contract.settings[name];
    if (!Number.isSafeInteger(value) || value <= 0) {
      throw new Error(`Expected dataset setting ${name} must be a positive safe integer.`);
    }
  }
  if (worldStartupOptimization !== undefined) {
    validateWorldStartupOptimization(
      worldStartupOptimization,
      'Expected dataset setting worldStartupOptimization',
    );
  }
  for (const [name, value] of Object.entries(contract.counts)) {
    if (!Number.isSafeInteger(value) || value < 0) {
      throw new Error(`Expected dataset count ${name} must be a non-negative safe integer.`);
    }
  }
  for (const [name, value] of Object.entries(contract.diagnostics)) {
    if (profile === GTNH_1710_PROFILE && name === 'nei') continue;
    if (!Number.isSafeInteger(value) || value < 0) {
      throw new Error(`Expected dataset diagnostic ${name} must be a non-negative safe integer.`);
    }
  }
  for (const [name, value] of Object.entries(contract.recipeImages)) {
    if (!Number.isSafeInteger(value) || value < 0) {
      throw new Error(
        `Expected recipe-image count ${name} must be a non-negative safe integer.`,
      );
    }
  }
  if (contract.recipeImages.previews + contract.recipeImages.missing !== contract.counts.recipes) {
    throw new Error(
      'Expected recipe-image previews plus missing must equal the audited recipe count.',
    );
  }
  if (
    contract.diagnostics.failureEvents !== contract.counts.failures ||
    contract.diagnostics.failureEventsOmitted !== 0
  ) {
    throw new Error(
      'Expected diagnostics must serialize every audited failure event without omission.',
    );
  }
  validateProfileManifestExtensions(contract, profile, 'Expected dataset contract');
  if (contract.hostedWeb !== undefined && !isRecord(contract.hostedWeb)) {
    throw new Error('Expected hostedWeb contract must be an object when provided.');
  }
  if (contract.repairProvenance !== undefined) {
    if (
      !hasExactKeys(contract.repairProvenance, [
        'format',
        'method',
        'repairedRecipePreviews',
        'compatibilityDiagnostics',
        'hashAlgorithm',
        'treeHashFormat',
        'canonicalSha256',
      ]) ||
      !DATASET_PUBLICATION_ID_PATTERN.test(contract.repairProvenance.canonicalSha256)
    ) {
      throw new Error('Expected repairProvenance contract is malformed.');
    }
  }
}

function validateDatasetManifest(manifest, contract, label, {hosted = false} = {}) {
  if (!isRecord(manifest)) throw new Error(`${label} must be a JSON object.`);
  const expectedManifestKeys = [
    'aborted',
    'counts',
    'diagnostics',
    'durationMs',
    'format',
    'generatedAt',
    'minecraft',
    'mods',
    'settings',
  ];
  if (contract.repairProvenance !== undefined) expectedManifestKeys.push('repairProvenance');
  if (contract.qualitySample !== undefined) expectedManifestKeys.push('qualitySample');
  if (contract.handlerPolicies !== undefined) expectedManifestKeys.push('handlerPolicies');
  if (contract.knowledgePolicy !== undefined) expectedManifestKeys.push('knowledgePolicy');
  if (contract.attribution !== undefined) expectedManifestKeys.push('attribution');
  for (const name of GTNH_PROVENANCE_KEYS) {
    if (contract[name] !== undefined) expectedManifestKeys.push(name);
  }
  if (contract.pack !== undefined) expectedManifestKeys.push('pack');
  if (hosted) expectedManifestKeys.push('publicationId', 'web');
  if (!hasExactKeys(manifest, expectedManifestKeys)) {
    throw new Error(
      `${label} must contain exactly the audited manifest fields: ` +
        `${expectedManifestKeys.sort().join(', ')}.`,
    );
  }
  if (manifest.format !== contract.format) {
    throw new Error(
      `${label}.format must be ${contract.format}; received ${JSON.stringify(manifest.format)}.`,
    );
  }
  if (manifest.minecraft !== contract.minecraft) {
    throw new Error(
      `${label}.minecraft must be ${JSON.stringify(contract.minecraft)}; received ` +
        `${JSON.stringify(manifest.minecraft)}.`,
    );
  }
  if (manifest.aborted !== false) {
    throw new Error(`${label}.aborted must be false; partial exports are not accepted.`);
  }
  if (!Number.isSafeInteger(manifest.durationMs) || manifest.durationMs < 0) {
    throw new Error(`${label}.durationMs must be a non-negative safe integer.`);
  }
  if (!isRecord(manifest.mods)) throw new Error(`${label}.mods must be an object.`);
  if (
    typeof manifest.generatedAt !== 'string' ||
    manifest.generatedAt.length === 0 ||
    !Number.isFinite(Date.parse(manifest.generatedAt))
  ) {
    throw new Error(`${label}.generatedAt must be a valid timestamp.`);
  }
  assertExactRecord(manifest.settings, contract.settings, `${label}.settings`);
  for (const name of GTNH_PROVENANCE_KEYS) {
    if (contract[name] !== undefined && manifest[name] !== contract[name]) {
      throw new Error(
        `${label}.${name} must be ${JSON.stringify(contract[name])}; received ` +
          `${JSON.stringify(manifest[name])}.`,
      );
    }
  }
  if (contract.pack !== undefined) {
    assertExactRecord(manifest.pack, contract.pack, `${label}.pack`);
  }
  if (contract.qualitySample !== undefined) {
    assertExactRecord(manifest.qualitySample, contract.qualitySample, `${label}.qualitySample`);
  }
  if (contract.handlerPolicies !== undefined) {
    if (!isDeepStrictEqual(manifest.handlerPolicies, contract.handlerPolicies)) {
      throw new Error(
        `${label}.handlerPolicies must equal the exact pinned GTNH handler-policy array.`,
      );
    }
  }
  if (contract.knowledgePolicy !== undefined) {
    assertExactRecord(
      manifest.knowledgePolicy,
      contract.knowledgePolicy,
      `${label}.knowledgePolicy`,
    );
  }
  if (contract.attribution !== undefined) {
    assertExactRecord(manifest.attribution, contract.attribution, `${label}.attribution`);
  }
  assertExactRecord(manifest.counts, contract.counts, `${label}.counts`);
  assertExactRecord(manifest.diagnostics, contract.diagnostics, `${label}.diagnostics`);
  validateRepairProvenance(manifest, contract, label);

  if (hosted) {
    if (!DATASET_PUBLICATION_ID_PATTERN.test(manifest.publicationId ?? '')) {
      throw new Error(
        `${label}.publicationId must be an exact lowercase SHA-256 digest; received ` +
          `${JSON.stringify(manifest.publicationId)}.`,
      );
    }
    if (!isRecord(manifest.web)) throw new Error(`${label}.web must be an object.`);
    const expectedWebKeys = ['recipeImages', ...Object.keys(contract.hostedWeb ?? {})];
    if (!hasExactKeys(manifest.web, expectedWebKeys)) {
      throw new Error(
        `${label}.web must contain exactly ${expectedWebKeys.sort().join(', ')}.`,
      );
    }
    for (const [name, expected] of Object.entries(contract.hostedWeb ?? {})) {
      if (!isDeepStrictEqual(manifest.web[name], expected)) {
        throw new Error(
          `${label}.web.${name} must be ${JSON.stringify(expected)}; received ` +
            `${JSON.stringify(manifest.web[name])}.`,
        );
      }
    }
    const recipeImages = manifest.web.recipeImages;
    if (
      !isRecord(recipeImages) ||
      !hasExactKeys(recipeImages, [
        'mode',
        'reason',
        'references',
        'files',
        'encoding',
        'bytes',
        'inventory',
      ]) ||
      recipeImages.mode !== 'omitted' ||
      recipeImages.reason !== 'hosting-archive-budget' ||
      !Number.isSafeInteger(recipeImages.references) ||
      recipeImages.references < 0 ||
      !Number.isSafeInteger(recipeImages.files) ||
      recipeImages.files < 0 ||
      recipeImages.encoding !== 'png' ||
      !Number.isSafeInteger(recipeImages.bytes) ||
      recipeImages.bytes < 0
    ) {
      throw new Error(
        `${label}.web.recipeImages must describe the omitted recipe images with non-negative ` +
        'references, files, and bytes plus encoding="png".',
      );
    }
    const inventory = requireRecipeImageInventory(
      recipeImages.inventory,
      `${label}.web.recipeImages.inventory`,
      contract.counts.recipes,
    );
    if (
      inventory.previews !== recipeImages.references ||
      inventory.missing !== contract.counts.recipes - recipeImages.references ||
      recipeImages.files !== recipeImages.references
    ) {
      throw new Error(
        `${label}.web.recipeImages inventory/counts disagree: previews=${inventory.previews}, ` +
          `missing=${inventory.missing}, references=${recipeImages.references}, ` +
          `files=${recipeImages.files}, recipes=${contract.counts.recipes}.`,
      );
    }
    if (
      recipeImages.references !== contract.recipeImages.previews ||
      recipeImages.files !== contract.recipeImages.previews ||
      inventory.previews !== contract.recipeImages.previews ||
      inventory.missing !== contract.recipeImages.missing
    ) {
      throw new Error(
        `${label}.web.recipeImages must match the audited preview contract: ` +
          `expected previews/files=${contract.recipeImages.previews}/${contract.recipeImages.previews} ` +
          `and missing=${contract.recipeImages.missing}; received ` +
          `references/files=${recipeImages.references}/${recipeImages.files}, ` +
          `inventory previews/missing=${inventory.previews}/${inventory.missing}.`,
      );
    }
  }
}

function assertRawAndHostedIdentity(rawManifest, hostedManifest) {
  const normalizedHostedManifest = {...hostedManifest};
  delete normalizedHostedManifest.publicationId;
  delete normalizedHostedManifest.web;
  if (!isDeepStrictEqual(rawManifest, normalizedHostedManifest)) {
    throw new Error(
      'Raw export and hosted manifests differ outside the hosted-only publicationId/web fields; ' +
        'refusing to attach previews to a different dataset publication.',
    );
  }
}

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

function resolveInside(root, relativePath, label) {
  if (!isSafeRelativePath(relativePath)) {
    throw new Error(`${label} is not a safe relative POSIX path: ${JSON.stringify(relativePath)}.`);
  }
  const path = resolve(root, ...relativePath.split('/'));
  const prefix = root.endsWith(sep) ? root : `${root}${sep}`;
  if (!path.startsWith(prefix)) {
    throw new Error(`${label} resolves outside the raw export root: ${relativePath}.`);
  }
  return path;
}

function isPathInsideOrEqual(root, candidate) {
  const prefix = root.endsWith(sep) ? root : `${root}${sep}`;
  return candidate === root || candidate.startsWith(prefix);
}

function localHostedRootForManifest(input) {
  if (typeof input !== 'string' || input.length === 0) return null;
  try {
    new URL(input);
    return null;
  } catch {
    return dirname(resolve(input));
  }
}

function assertSidecarPathsOutsideRoot(outputRoot, stagingRoot, protectedRoot, label) {
  for (const [kind, path] of [
    ['output', outputRoot],
    ['staging', stagingRoot],
  ]) {
    if (isPathInsideOrEqual(protectedRoot, path)) {
      throw new Error(
        `Recipe preview sidecar ${kind} path must be outside the ${label}: ${path}.`,
      );
    }
  }
}

async function canonicalProspectivePath(path) {
  let cursor = path;
  const missingSegments = [];
  while (true) {
    try {
      return resolve(await realpath(cursor), ...missingSegments);
    } catch (error) {
      if (error?.code !== 'ENOENT') {
        throw new Error(`Sidecar path could not be canonicalized at ${cursor}: ${error.message}`, {
          cause: error,
        });
      }
      const parent = dirname(cursor);
      if (parent === cursor) {
        throw new Error(`Sidecar path has no existing ancestor that can be canonicalized: ${path}.`);
      }
      missingSegments.unshift(basename(cursor));
      cursor = parent;
    }
  }
}

async function assertPlainFile(path, label) {
  let info;
  try {
    info = await lstat(path);
  } catch (error) {
    throw new Error(`${label} could not be inspected at ${path}: ${error.message}`, {cause: error});
  }
  if (info.isSymbolicLink() || !info.isFile()) {
    throw new Error(`${label} must be a plain file (symlinks and special files are refused): ${path}`);
  }
  return info;
}

async function assertPlainDirectory(path, label) {
  let info;
  try {
    info = await lstat(path);
  } catch (error) {
    throw new Error(`${label} could not be inspected at ${path}: ${error.message}`, {cause: error});
  }
  if (info.isSymbolicLink() || !info.isDirectory()) {
    throw new Error(
      `${label} must be a plain directory (symlinks and special files are refused): ${path}`,
    );
  }
}

function parseJsonBytes(bytes, label, location) {
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch (error) {
    throw new Error(`${label} contains invalid JSON at ${location}: ${error.message}`, {
      cause: error,
    });
  }
}

function assertDocumentByteLength(length, label, {expectedBytes, maxBytes} = {}) {
  if (!Number.isSafeInteger(length) || length < 0) {
    throw new Error(`${label} has an invalid byte length ${JSON.stringify(length)}.`);
  }
  if (expectedBytes !== undefined && length !== expectedBytes) {
    throw new Error(`${label} declares ${expectedBytes} bytes but contains ${length}.`);
  }
  if (maxBytes !== undefined && length > maxBytes) {
    throw new Error(`${label} is ${length} bytes, above the ${maxBytes}-byte safety bound.`);
  }
}

async function readJsonFileRecord(path, label, limits) {
  const info = await assertPlainFile(path, label);
  assertDocumentByteLength(info.size, label, limits);
  let bytes;
  try {
    bytes = await readFile(path);
  } catch (error) {
    throw new Error(`${label} could not be read at ${path}: ${error.message}`, {cause: error});
  }
  assertDocumentByteLength(bytes.length, label, limits);
  if (bytes.length !== info.size) {
    throw new Error(
      `${label} changed while it was being read at ${path}: inspected ${info.size} bytes, ` +
        `read ${bytes.length}.`,
    );
  }
  return {value: parseJsonBytes(bytes, label, path), bytes: bytes.length};
}

async function readJsonFile(path, label) {
  return (await readJsonFileRecord(path, label)).value;
}

async function readHostedPublication(input) {
  if (typeof input !== 'string' || input.length === 0) {
    throw new Error('datasetManifest must be a non-empty local hosted manifest path.');
  }

  let url;
  try {
    url = new URL(input);
  } catch {
    url = null;
  }
  if (url) {
    if (url.protocol !== 'https:') {
      throw new Error(`Hosted dataset manifest URL must use HTTPS; received ${url.protocol}.`);
    }
    throw new Error(
      'HTTPS dataset manifests are disabled for sidecar builds because their self-asserted ' +
        'publicationId cannot be recomputed locally. Use a complete local hosted publication.',
    );
  }

  const manifestPath = resolve(input);
  const publicationRoot = dirname(manifestPath);
  const manifest = (
    await readJsonFileRecord(manifestPath, 'Hosted dataset manifest', {
      maxBytes: MAX_SHARD_BYTES,
    })
  ).value;
  return {
    kind: 'local',
    manifest,
    publicationRoot,
    async readDocument(relativePath, label, limits = {}) {
      const path = resolveInside(publicationRoot, relativePath, `${label} path`);
      return readJsonFileRecord(path, label, {maxBytes: MAX_SHARD_BYTES, ...limits});
    },
  };
}

async function verifyHostedPublicationId(hostedPublication, logger) {
  if (hostedPublication.kind === 'https') {
    throw new Error(
      'HTTPS dataset manifests are disabled for sidecar builds because their self-asserted ' +
        'publicationId cannot be recomputed locally. Use a complete local hosted publication.',
    );
  }
  if (hostedPublication.kind !== 'local' || typeof hostedPublication.publicationRoot !== 'string') {
    throw new Error('Hosted publication reader has an invalid provenance-verification mode.');
  }
  const computedPublicationId = await computePublicationId(hostedPublication.publicationRoot);
  if (computedPublicationId !== hostedPublication.manifest.publicationId) {
    throw new Error(
      `Hosted dataset manifest publicationId ${hostedPublication.manifest.publicationId} does ` +
        `not match canonical local publication content hash ${computedPublicationId}.`,
    );
  }
  logger.info(`Verified canonical local dataset publication hash ${computedPublicationId}.`);
}

function validateCategories(document, expectedCount, expectedRecipes) {
  if (!isRecord(document) || !Array.isArray(document.categories)) {
    throw new Error('Raw categories.json must be an object with a categories array.');
  }
  if (document.categories.length !== expectedCount) {
    throw new Error(
      `Raw categories.json contains ${document.categories.length} categories; expected ` +
        `${expectedCount}.`,
    );
  }
  const seenIds = new Set();
  const seenDirs = new Set();
  let recipeCount = 0;
  for (const [index, category] of document.categories.entries()) {
    if (!isRecord(category)) throw new Error(`Category ${index} must be an object.`);
    if (typeof category.id !== 'string' || category.id.length === 0) {
      throw new Error(`Category ${index}.id must be a non-empty string.`);
    }
    if (seenIds.has(category.id)) throw new Error(`Category id is duplicated: ${category.id}.`);
    seenIds.add(category.id);
    if (!isSafeRelativePath(category.dir)) {
      throw new Error(`Category ${index}.dir is not a safe relative path: ${category.dir}.`);
    }
    if (seenDirs.has(category.dir)) {
      throw new Error(`Category directory is duplicated: ${category.dir}.`);
    }
    seenDirs.add(category.dir);
    if (!Number.isSafeInteger(category.count) || category.count < 0) {
      throw new Error(`Category ${index}.count must be a non-negative safe integer.`);
    }
    recipeCount += category.count;
    if (!Number.isSafeInteger(recipeCount)) {
      throw new Error('Raw category recipe count exceeds the safe integer range.');
    }
  }
  if (recipeCount !== expectedRecipes) {
    throw new Error(
      `Raw category recipe counts sum to ${recipeCount}; expected ${expectedRecipes}.`,
    );
  }
  return document.categories;
}

const NORMALIZED_CATEGORY_ICON = 'mrt-normalized-category-icon';

function hasOwn(value, key) {
  return Object.prototype.hasOwnProperty.call(value, key);
}

function normalizeRawCategory(category, index) {
  if (!isRecord(category)) throw new Error(`Raw category ${index} must be an object.`);
  if (!hasOwn(category, 'icon')) return category;
  if (!isSafeRelativePath(category.icon)) {
    throw new Error(
      `Raw category ${index} icon is not a safe non-empty relative path: ` +
        `${JSON.stringify(category.icon)}.`,
    );
  }
  return {...category, icon: NORMALIZED_CATEGORY_ICON};
}

function normalizeHostedCategory(category, rawCategory, index) {
  if (!isRecord(category)) throw new Error(`Hosted category ${index} must be an object.`);
  const rawHasIcon = hasOwn(rawCategory, 'icon');
  const hostedHasIcon = hasOwn(category, 'icon');
  if (rawHasIcon !== hostedHasIcon) {
    throw new Error(
      `Raw category ${index} (${JSON.stringify(rawCategory.id)}) and hosted publication ` +
        'category at the same index disagree on category.icon presence.',
    );
  }
  if (!hostedHasIcon) return category;
  if (parsePackedImagePath(category.icon) === null) {
    throw new Error(
      `Hosted category ${index} (${JSON.stringify(rawCategory.id)}) icon is not a valid ` +
        `packed-image coordinate: ${JSON.stringify(category.icon)}.`,
    );
  }
  return {...category, icon: NORMALIZED_CATEGORY_ICON};
}

function assertRawAndHostedCategoryIdentity(rawCategories, hostedDocument) {
  if (!isRecord(hostedDocument) || !Array.isArray(hostedDocument.categories)) {
    throw new Error('Hosted categories.json must be an object with a categories array.');
  }
  const hostedCategories = hostedDocument.categories;
  if (hostedCategories.length !== rawCategories.length) {
    throw new Error(
      `Hosted categories.json contains ${hostedCategories.length} categories; raw export ` +
        `contains ${rawCategories.length}.`,
    );
  }
  for (let index = 0; index < rawCategories.length; index += 1) {
    const rawCategory = rawCategories[index];
    const normalizedRaw = normalizeRawCategory(rawCategory, index);
    const normalizedHosted = normalizeHostedCategory(hostedCategories[index], rawCategory, index);
    if (!isDeepStrictEqual(normalizedHosted, normalizedRaw)) {
      throw new Error(
        `Raw category ${index} (${JSON.stringify(rawCategory.id)}) does not match the hosted ` +
          'publication category at the same index after normalizing only category.icon.',
      );
    }
  }
}

function normalizeRawRecipe(recipe, categoryId, recipeIndex) {
  const label = `Raw category ${JSON.stringify(categoryId)} recipe ${recipeIndex}`;
  if (!isRecord(recipe)) throw new Error(`${label} must be an object.`);
  if (!hasOwn(recipe, 'img')) return recipe;
  const {img: _image, w: _width, h: _height, ...structuredRecipe} = recipe;
  return structuredRecipe;
}

function assertRecipeIdentity(rawRecipe, hostedRecipe, categoryId, recipeIndex) {
  const label = `category ${JSON.stringify(categoryId)} recipe ${recipeIndex}`;
  if (!isRecord(hostedRecipe)) {
    throw new Error(`Hosted ${label} must be an object.`);
  }
  const rawHasImage = isRecord(rawRecipe) && hasOwn(rawRecipe, 'img');
  if (rawHasImage && ['img', 'w', 'h'].some(key => hasOwn(hostedRecipe, key))) {
    throw new Error(
      `Hosted ${label} retains img, w, or h after the declared recipe-preview omission transform.`,
    );
  }
  const normalizedRaw = normalizeRawRecipe(rawRecipe, categoryId, recipeIndex);
  if (!isDeepStrictEqual(hostedRecipe, normalizedRaw)) {
    throw new Error(
      `Raw ${label} does not match the hosted publication at the same index after normalizing ` +
        'only img/w/h for a raw recipe that has img.',
    );
  }
}

async function assertRawAndHostedRecipeIdentity(
  rawRecipes,
  hostedDocument,
  hostedPublication,
  category,
) {
  const label = `Hosted category ${JSON.stringify(category.id)} recipes.json`;
  if (Array.isArray(hostedDocument)) {
    if (hostedDocument.length !== rawRecipes.length) {
      throw new Error(
        `${label} contains ${hostedDocument.length} recipes; raw category contains ` +
          `${rawRecipes.length}.`,
      );
    }
    for (let index = 0; index < rawRecipes.length; index += 1) {
      assertRecipeIdentity(rawRecipes[index], hostedDocument[index], category.id, index);
    }
    return;
  }

  if (
    !isRecord(hostedDocument) ||
    !hasExactKeys(hostedDocument, ['format', 'kind', 'count', 'parts']) ||
    hostedDocument.format !== SHARDED_JSON_FORMAT ||
    hostedDocument.kind !== 'array'
  ) {
    throw new Error(
      `${label} must be an inline array or an exact ${SHARDED_JSON_FORMAT} array descriptor.`,
    );
  }
  if (!Number.isSafeInteger(hostedDocument.count) || hostedDocument.count !== rawRecipes.length) {
    throw new Error(
      `${label} descriptor count must equal raw category count ${rawRecipes.length}; received ` +
        `${JSON.stringify(hostedDocument.count)}.`,
    );
  }
  if (
    !Array.isArray(hostedDocument.parts) ||
    (hostedDocument.count > 0 && hostedDocument.parts.length === 0)
  ) {
    throw new Error(`${label} descriptor must enumerate every non-empty shard.`);
  }

  const seenPaths = new Set();
  let expectedStart = 0;
  for (const [partIndex, part] of hostedDocument.parts.entries()) {
    const partLabel = `${label}.parts[${partIndex}]`;
    if (
      !isRecord(part) ||
      !hasExactKeys(part, ['path', 'start', 'count', 'bytes']) ||
      !isSafeRelativePath(part.path)
    ) {
      throw new Error(`${partLabel} must contain an exact safe array-shard record.`);
    }
    if (seenPaths.has(part.path)) {
      throw new Error(`${label} repeats shard path ${part.path}.`);
    }
    seenPaths.add(part.path);
    if (!Number.isSafeInteger(part.start) || part.start !== expectedStart) {
      throw new Error(`${partLabel}.start must be the contiguous offset ${expectedStart}.`);
    }
    if (!Number.isSafeInteger(part.count) || part.count <= 0) {
      throw new Error(`${partLabel}.count must be a positive safe integer.`);
    }
    if (
      !Number.isSafeInteger(part.bytes) ||
      part.bytes <= 0 ||
      part.bytes > MAX_SHARD_BYTES
    ) {
      throw new Error(
        `${partLabel}.bytes must be a positive safe integer no greater than ${MAX_SHARD_BYTES}.`,
      );
    }
    if (expectedStart + part.count > hostedDocument.count) {
      throw new Error(`${partLabel} extends beyond descriptor count ${hostedDocument.count}.`);
    }
    const shard = (
      await hostedPublication.readDocument(part.path, `${label} shard ${part.path}`, {
        expectedBytes: part.bytes,
      })
    ).value;
    if (!Array.isArray(shard) || shard.length !== part.count) {
      throw new Error(`${label} shard ${part.path} does not match its declared array count.`);
    }
    for (let offset = 0; offset < shard.length; offset += 1) {
      const recipeIndex = expectedStart + offset;
      assertRecipeIdentity(rawRecipes[recipeIndex], shard[offset], category.id, recipeIndex);
    }
    expectedStart += part.count;
  }
  if (expectedStart !== hostedDocument.count) {
    throw new Error(
      `${label} shards contain ${expectedStart} recipes; expected ${hostedDocument.count}.`,
    );
  }
}

function assertPng(bytes, label) {
  if (bytes.length < PNG_SIGNATURE.length || !bytes.subarray(0, 8).equals(PNG_SIGNATURE)) {
    throw new Error(`${label} is not a PNG file.`);
  }
}

function pixelDigest(width, height, pixels) {
  const dimensions = Buffer.allocUnsafe(8);
  dimensions.writeUInt32BE(width, 0);
  dimensions.writeUInt32BE(height, 4);
  return createHash('sha256')
    .update('mrt-rgba-pixels-v1\0')
    .update(dimensions)
    .update(pixels)
    .digest('base64url');
}

async function decodePng({
  path,
  physicalWidth,
  physicalHeight,
  logicalWidth,
  logicalHeight,
  label,
}) {
  await assertPlainFile(path, label);
  let source;
  try {
    source = await readFile(path);
  } catch (error) {
    throw new Error(`${label} could not be read at ${path}: ${error.message}`, {cause: error});
  }
  assertPng(source, label);

  let pixels;
  let info;
  try {
    const image = sharp(source, {
      failOn: 'error',
      limitInputPixels: MAX_IMAGE_PIXELS,
    });
    const metadata = await image.metadata();
    if ((metadata.pages ?? 1) !== 1) {
      throw new Error(
        `${label} contains ${metadata.pages} animation/pages; exactly one PNG page is required.`,
      );
    }
    ({data: pixels, info} = await image
      .toColourspace('srgb')
      .ensureAlpha()
      .raw()
      .toBuffer({resolveWithObject: true}));
  } catch (error) {
    throw new Error(`${label} could not be decoded as PNG: ${error.message}`, {cause: error});
  }
  if (info.width !== physicalWidth || info.height !== physicalHeight || info.channels !== 4) {
    throw new Error(
      `${label} decoded as ${info.width}×${info.height}×${info.channels}, but the recipe ` +
        `requires a ${physicalWidth}×${physicalHeight} physical image for its ` +
        `${logicalWidth}×${logicalHeight} logical layout and RGBA normalization requires ` +
        '4 channels.',
    );
  }

  return {
    path,
    label,
    sourceBytes: source.length,
    width: info.width,
    height: info.height,
    logicalWidth,
    logicalHeight,
    pixels,
    digest: pixelDigest(info.width, info.height, pixels),
    rgbaSha256: decodedRgbaSha256(info.width, info.height, pixels),
  };
}

async function encodeLosslessWebp(decoded) {
  let webp;
  try {
    webp = await sharp(decoded.pixels, {
      raw: {width: decoded.width, height: decoded.height, channels: 4},
    })
      .webp({lossless: true, effort: WEBP_EFFORT})
      .toBuffer();
  } catch (error) {
    throw new Error(`${decoded.label} could not be encoded as lossless WebP: ${error.message}`, {
      cause: error,
    });
  }
  if (webp.length === 0) throw new Error(`${decoded.label} produced an empty WebP payload.`);
  return webp;
}

function validateRecipeImage(recipe, category, recipeIndex, rawRoot, recipeScale) {
  const label = `Category ${JSON.stringify(category.id)} recipe ${recipeIndex}`;
  if (!isRecord(recipe)) throw new Error(`${label} must be an object.`);
  if (recipe.img === undefined) {
    if (recipe.w !== undefined || recipe.h !== undefined) {
      throw new Error(`${label} omits img but retains w or h; missing previews must omit all three.`);
    }
    return null;
  }
  const expectedName = `r${recipeIndex}.png`;
  if (recipe.img !== expectedName) {
    throw new Error(
      `${label}.img must be the deterministic exporter filename ${expectedName}; received ` +
        `${JSON.stringify(recipe.img)}.`,
    );
  }
  for (const dimension of ['w', 'h']) {
    if (
      !Number.isSafeInteger(recipe[dimension]) ||
      recipe[dimension] <= 0 ||
      recipe[dimension] > MAX_IMAGE_DIMENSION
    ) {
      throw new Error(
        `${label}.${dimension} must be a positive safe integer no greater than ` +
          `${MAX_IMAGE_DIMENSION}.`,
      );
    }
  }
  const physicalWidth = recipe.w * recipeScale;
  const physicalHeight = recipe.h * recipeScale;
  if (
    !Number.isSafeInteger(physicalWidth) ||
    !Number.isSafeInteger(physicalHeight) ||
    physicalWidth > MAX_IMAGE_DIMENSION ||
    physicalHeight > MAX_IMAGE_DIMENSION ||
    physicalWidth * physicalHeight > MAX_IMAGE_PIXELS
  ) {
    throw new Error(
      `${label} ${recipe.w}×${recipe.h} logical layout at recipeScale ${recipeScale} exceeds ` +
        `the ${MAX_IMAGE_DIMENSION}×${MAX_IMAGE_DIMENSION} physical decode limit.`,
    );
  }
  const categoryRoot = resolveInside(rawRoot, category.dir, `Category ${category.id}.dir`);
  return {
    label,
    path: resolveInside(categoryRoot, recipe.img, `${label}.img`),
    physicalWidth,
    physicalHeight,
    logicalWidth: recipe.w,
    logicalHeight: recipe.h,
  };
}

async function writeOutputDocument(root, relativePath, bytes) {
  const path = resolveInside(root, relativePath, 'Generated output path');
  await mkdir(dirname(path), {recursive: true});
  await writeFile(path, bytes, {flag: 'wx'});
  return {path: relativePath, bytes: bytes.length, sha256: sha256(bytes)};
}

function packIndexBytes(packNumber, packBytes, entries) {
  const totalBytes = PACK_INDEX_HEADER_BYTES + entries.length * PACK_INDEX_ENTRY_BYTES;
  if (totalBytes > MAX_PACK_INDEX_BYTES) {
    throw new Error(
      `Pack ${packNumber} authorization index is ${totalBytes} bytes, above the ` +
        `${MAX_PACK_INDEX_BYTES}-byte security bound.`,
    );
  }
  const output = Buffer.allocUnsafe(totalBytes);
  PACK_INDEX_MAGIC.copy(output, 0);
  output.writeUInt16BE(PACK_INDEX_VERSION, 4);
  output.writeUInt16BE(PACK_INDEX_HEADER_BYTES, 6);
  output.writeUInt32BE(packNumber, 8);
  output.writeUInt32BE(packBytes, 12);
  output.writeUInt32BE(entries.length, 16);
  let cursor = 0;
  for (const [entryIndex, entry] of entries.entries()) {
    if (
      !Array.isArray(entry) ||
      entry.length !== 2 ||
      !entry.every(Number.isSafeInteger) ||
      entry[0] !== cursor ||
      entry[1] <= 0 ||
      cursor + entry[1] > packBytes
    ) {
      throw new Error(
        `Pack ${packNumber} authorization entry ${entryIndex} is not a canonical contiguous range.`,
      );
    }
    const offset = PACK_INDEX_HEADER_BYTES + entryIndex * PACK_INDEX_ENTRY_BYTES;
    output.writeUInt32BE(entry[0], offset);
    output.writeUInt32BE(entry[1], offset + 4);
    cursor += entry[1];
  }
  if (entries.length === 0 || cursor !== packBytes) {
    throw new Error(
      `Pack ${packNumber} authorization ranges cover ${cursor}/${packBytes} bytes.`,
    );
  }
  return output;
}

class PackWriter {
  constructor(root, maxPackBytes) {
    this.root = root;
    this.maxPackBytes = maxPackBytes;
    this.chunks = [];
    this.indexEntries = [];
    this.bytes = 0;
    this.records = [];
  }

  async add(payload, width, height) {
    if (!Buffer.isBuffer(payload) || payload.length <= 0) {
      throw new Error('Packed preview payload must be a non-empty Buffer.');
    }
    if (payload.length > this.maxPackBytes) {
      throw new Error(
        `One lossless WebP preview is ${payload.length} bytes, above the ` +
          `${this.maxPackBytes}-byte pack limit.`,
      );
    }
    if (this.bytes > 0 && this.bytes + payload.length > this.maxPackBytes) {
      await this.flush();
    }
    const pack = this.records.length;
    const offset = this.bytes;
    this.chunks.push(payload);
    this.indexEntries.push([offset, payload.length]);
    this.bytes += payload.length;
    return [pack, offset, payload.length, width, height];
  }

  async flush() {
    if (this.bytes === 0) return;
    const index = this.records.length;
    const relativePath = `assets/pack-${indexName(index)}.bin`;
    const bytes = Buffer.concat(this.chunks, this.bytes);
    if (bytes.length <= 0 || bytes.length > this.maxPackBytes) {
      throw new Error(`Generated pack ${relativePath} has invalid length ${bytes.length}.`);
    }
    const packRecord = await writeOutputDocument(this.root, relativePath, bytes);
    const indexPath = `indexes/pack-${indexName(index)}.bin`;
    const indexPayload = packIndexBytes(index, bytes.length, this.indexEntries);
    const indexRecord = await writeOutputDocument(this.root, indexPath, indexPayload);
    this.records.push({
      ...packRecord,
      index: {...indexRecord, entries: this.indexEntries.length},
    });
    this.chunks = [];
    this.indexEntries = [];
    this.bytes = 0;
  }

  async finish() {
    await this.flush();
    return this.records;
  }

  bytesForPack(index) {
    if (index < this.records.length) return this.records[index].bytes;
    if (index === this.records.length && this.bytes > 0) return this.bytes;
    return null;
  }
}

class VisualCatalog {
  constructor(packWriter, maxPreviews) {
    this.packWriter = packWriter;
    // The complete dimensions+RGBA SHA-256 is the content address. Retaining raw pixels for every
    // unique preview would make heap usage proportional to the decoded corpus instead of its index.
    this.byDigest = new Map();
    // Five uint32 values per possible preview bound the coordinate catalog to ~7.2 MiB for MBC.
    this.coordinateValues = new Uint32Array(maxPreviews * 5);
    this.uniquePreviews = 0;
    this.writtenCoordinates = 0;
    this.deduplicatedPreviews = 0;
  }

  readCoordinate(index) {
    if (index < 0 || index >= this.writtenCoordinates) {
      throw new Error(`Preview coordinate ${index} was read before deterministic pack insertion.`);
    }
    const start = index * 5;
    return [
      this.coordinateValues[start],
      this.coordinateValues[start + 1],
      this.coordinateValues[start + 2],
      this.coordinateValues[start + 3],
      this.coordinateValues[start + 4],
    ];
  }

  writeCoordinate(index, coordinate) {
    if (index !== this.writtenCoordinates) {
      throw new Error(
        `Preview coordinate insertion order diverged at ${index}; expected ` +
          `${this.writtenCoordinates}.`,
      );
    }
    this.coordinateValues.set(coordinate, index * 5);
    this.writtenCoordinates += 1;
  }

  async coordinatesFor(decodedBatch) {
    const entries = new Array(decodedBatch.length);
    const uniqueEntries = [];
    for (const [batchIndex, decoded] of decodedBatch.entries()) {
      if (decoded === null) {
        entries[batchIndex] = null;
        continue;
      }
      const existingIndex = this.byDigest.get(decoded.digest);
      if (existingIndex !== undefined) {
        entries[batchIndex] = {decoded, index: existingIndex, unique: false};
        continue;
      }
      if (this.uniquePreviews >= this.coordinateValues.length / 5) {
        throw new Error('Unique preview count exceeds the validated recipe-count bound.');
      }
      const index = this.uniquePreviews;
      this.uniquePreviews += 1;
      this.byDigest.set(decoded.digest, index);
      const entry = {decoded, index, unique: true, webp: null};
      entries[batchIndex] = entry;
      uniqueEntries.push(entry);
    }

    await Promise.all(
      uniqueEntries.map(async entry => {
        entry.webp = await encodeLosslessWebp(entry.decoded);
      }),
    );

    const coordinates = new Array(entries.length);
    let logicalEncodedBytes = 0;
    for (const [batchIndex, entry] of entries.entries()) {
      if (entry === null) {
        coordinates[batchIndex] = null;
        continue;
      }
      let coordinate;
      if (entry.unique) {
        coordinate = await this.packWriter.add(
          entry.webp,
          entry.decoded.logicalWidth,
          entry.decoded.logicalHeight,
        );
        this.writeCoordinate(entry.index, coordinate);
      } else {
        coordinate = this.readCoordinate(entry.index);
        this.deduplicatedPreviews += 1;
      }
      coordinates[batchIndex] = coordinate;
      logicalEncodedBytes += coordinate[2];
    }
    return {coordinates, logicalEncodedBytes};
  }
}

function serializedArrayBytes(entries) {
  if (entries.length === 0) return 3;
  return 3 + entries.reduce((sum, entry, index) => {
    return sum + Buffer.byteLength(entry, 'utf8') + (index > 0 ? 1 : 0);
  }, 0);
}

function partitionPreviewEntries(entries, maxBytes, label) {
  const groups = [];
  let current = [];
  let currentBytes = 3;
  for (const entry of entries) {
    const entryBytes = Buffer.byteLength(entry, 'utf8');
    if (3 + entryBytes > maxBytes) {
      throw new Error(`${label} contains a coordinate larger than the ${maxBytes}-byte shard limit.`);
    }
    const added = entryBytes + (current.length > 0 ? 1 : 0);
    if (current.length > 0 && currentBytes + added > maxBytes) {
      groups.push(current);
      current = [];
      currentBytes = 3;
    }
    current.push(entry);
    currentBytes += entryBytes + (current.length > 1 ? 1 : 0);
  }
  if (current.length > 0) groups.push(current);
  return groups;
}

async function writeCategoryMapping({
  root,
  category,
  categoryIndex,
  previews,
  maxCategoryBytes,
}) {
  const name = indexName(categoryIndex);
  const rootPath = `categories/${name}.json`;
  const header = {
    format: RECIPE_PREVIEW_CATEGORY_FORMAT,
    categoryIndex,
    categoryId: category.id,
    count: previews.length,
  };
  const inline = {...header, previews};
  const inlineBytes = jsonBytes(inline);
  if (inlineBytes.length <= maxCategoryBytes) {
    return {
      documents: [await writeOutputDocument(root, rootPath, inlineBytes)],
      partCount: 0,
    };
  }

  const entries = previews.map(value => JSON.stringify(value));
  const groups = partitionPreviewEntries(
    entries,
    maxCategoryBytes,
    `Category ${JSON.stringify(category.id)} preview mapping`,
  );
  const documents = [];
  const parts = [];
  let start = 0;
  for (const [partIndex, group] of groups.entries()) {
    const partPath = `categories/${name}/part-${indexName(partIndex)}.json`;
    const bytes = Buffer.from(`[${group.join(',')}]\n`, 'utf8');
    if (bytes.length !== serializedArrayBytes(group) || bytes.length > maxCategoryBytes) {
      throw new Error(`Generated category mapping shard ${partPath} has invalid size ${bytes.length}.`);
    }
    documents.push(await writeOutputDocument(root, partPath, bytes));
    parts.push({path: partPath, start, count: group.length, bytes: bytes.length});
    start += group.length;
  }
  if (start !== previews.length) {
    throw new Error(`Category ${category.id} mapping shards cover ${start}/${previews.length} recipes.`);
  }
  const descriptorBytes = jsonBytes({...header, parts});
  if (descriptorBytes.length > maxCategoryBytes) {
    throw new Error(
      `Category ${category.id} mapping descriptor is ${descriptorBytes.length} bytes, above the ` +
        `${maxCategoryBytes}-byte limit.`,
    );
  }
  documents.push(await writeOutputDocument(root, rootPath, descriptorBytes));
  return {documents, partCount: parts.length};
}

function framedHashUpdate(hash, bytes) {
  const buffer = Buffer.isBuffer(bytes) ? bytes : Buffer.from(bytes, 'utf8');
  const length = Buffer.allocUnsafe(8);
  length.writeBigUInt64BE(BigInt(buffer.length));
  hash.update(length).update(buffer);
}

function computeAssetSetId(records, datasetPublicationId) {
  const hash = createHash('sha256');
  hash.update(`${RECIPE_PREVIEW_SIDECAR_FORMAT}\0`);
  framedHashUpdate(hash, datasetPublicationId);
  for (const record of [...records].sort((a, b) =>
    a.path < b.path ? -1 : a.path > b.path ? 1 : 0,
  )) {
    framedHashUpdate(hash, record.path);
    framedHashUpdate(hash, Buffer.from(record.sha256, 'hex'));
  }
  return hash.digest('hex');
}

async function verifyGeneratedFiles(
  root,
  records,
  {maxPackBytes, maxPackIndexBytes, maxCategoryBytes},
) {
  const seen = new Set();
  for (const record of records) {
    if (seen.has(record.path)) throw new Error(`Generated file record is duplicated: ${record.path}.`);
    seen.add(record.path);
    const path = resolveInside(root, record.path, 'Generated file record path');
    await assertPlainFile(path, `Generated file ${record.path}`);
    const bytes = await readFile(path);
    if (bytes.length !== record.bytes || sha256(bytes) !== record.sha256) {
      throw new Error(`Generated file digest verification failed for ${record.path}.`);
    }
    if (record.path.startsWith('assets/') && (bytes.length <= 0 || bytes.length > maxPackBytes)) {
      throw new Error(`Generated asset pack ${record.path} violates the pack-size bound.`);
    }
    if (
      record.path.startsWith('indexes/') &&
      (bytes.length < PACK_INDEX_HEADER_BYTES + PACK_INDEX_ENTRY_BYTES ||
        bytes.length > maxPackIndexBytes)
    ) {
      throw new Error(`Generated pack index ${record.path} violates the index-size bound.`);
    }
    if (record.path.startsWith('categories/') && bytes.length > maxCategoryBytes) {
      throw new Error(`Generated category document ${record.path} violates the mapping-size bound.`);
    }
  }
}

function assertCoordinateBounds(previews, packWriter, categoryId) {
  for (const [recipeIndex, coordinate] of previews.entries()) {
    if (coordinate === null) continue;
    if (
      !Array.isArray(coordinate) ||
      coordinate.length !== 5 ||
      !coordinate.every(Number.isSafeInteger)
    ) {
      throw new Error(`Category ${categoryId} recipe ${recipeIndex} has an invalid coordinate.`);
    }
    const [pack, offset, length, width, height] = coordinate;
    const packBytes = packWriter.bytesForPack(pack);
    if (
      pack < 0 ||
      packBytes === null ||
      offset < 0 ||
      length <= 0 ||
      width <= 0 ||
      height <= 0 ||
      offset + length > packBytes
    ) {
      throw new Error(
        `Category ${categoryId} recipe ${recipeIndex} coordinate is outside pack bounds.`,
      );
    }
  }
}

async function targetKind(path) {
  try {
    const info = await lstat(path);
    if (info.isDirectory()) return 'directory';
    if (info.isFile()) return 'file';
    if (info.isSymbolicLink()) return 'symlink';
    return 'special entry';
  } catch (error) {
    if (error?.code === 'ENOENT') return 'missing';
    throw error;
  }
}

function validateBuildOptions({maxPackBytes, maxCategoryBytes, concurrency}) {
  if (maxPackBytes !== MAX_PACK_BYTES) {
    throw new Error(`Recipe preview packs must use the exact ${MAX_PACK_BYTES}-byte limit.`);
  }
  if (
    !Number.isSafeInteger(maxCategoryBytes) ||
    maxCategoryBytes < 256 ||
    maxCategoryBytes > MAX_CATEGORY_BYTES
  ) {
    throw new Error(
      `maxCategoryBytes must be a safe integer within 256..${MAX_CATEGORY_BYTES} bytes.`,
    );
  }
  if (!Number.isSafeInteger(concurrency) || concurrency <= 0 || concurrency > 64) {
    throw new Error('concurrency must be a positive safe integer no greater than 64.');
  }
}

export async function buildRecipePreviewSidecar({
  source,
  datasetManifest,
  output,
  profile,
  contract,
  maxPackBytes = MAX_PACK_BYTES,
  maxCategoryBytes = MAX_CATEGORY_BYTES,
  concurrency = Math.max(1, Math.min(8, availableParallelism())),
  logger = console,
}) {
  const resolvedProfile = profile === undefined ? null : resolveQualityProfile(profile);
  if (resolvedProfile === null && contract === undefined) {
    throw new Error(
      `An explicit recipe-preview quality profile is required. Supported profiles: ` +
        EXPORT_QUALITY_PROFILE_IDS.join(', '),
    );
  }
  if (resolvedProfile !== null && contract !== undefined) {
    throw new Error('profile and an explicit contract are mutually exclusive.');
  }
  if (contract !== undefined) validateContract(contract);
  validateBuildOptions({maxPackBytes, maxCategoryBytes, concurrency});
  if (typeof source !== 'string' || source.length === 0) {
    throw new Error('source must be a non-empty raw export directory path.');
  }
  if (typeof output !== 'string' || output.length === 0) {
    throw new Error('output must be a non-empty new directory path.');
  }
  if (!isRecord(logger) || !['info', 'warn', 'error'].every(name => typeof logger[name] === 'function')) {
    throw new Error('logger must provide info, warn, and error functions.');
  }

  const rawRoot = resolve(source);
  const outputRoot = resolve(output);
  const stagingRoot = join(
    dirname(outputRoot),
    `.${outputRoot.split(sep).at(-1)}.staging-${process.pid}-${randomUUID()}`,
  );
  const localHostedRoot = localHostedRootForManifest(datasetManifest);
  assertSidecarPathsOutsideRoot(outputRoot, stagingRoot, rawRoot, 'raw export root');
  if (localHostedRoot) {
    assertSidecarPathsOutsideRoot(
      outputRoot,
      stagingRoot,
      localHostedRoot,
      'local hosted publication root',
    );
  }
  await assertPlainDirectory(rawRoot, 'Raw export root');
  const [canonicalRawRoot, canonicalOutputRoot, canonicalStagingRoot] = await Promise.all([
    realpath(rawRoot),
    canonicalProspectivePath(outputRoot),
    canonicalProspectivePath(stagingRoot),
  ]);
  assertSidecarPathsOutsideRoot(
    canonicalOutputRoot,
    canonicalStagingRoot,
    canonicalRawRoot,
    'canonical raw export root',
  );
  if (localHostedRoot) {
    const canonicalHostedRoot = await realpath(localHostedRoot);
    assertSidecarPathsOutsideRoot(
      canonicalOutputRoot,
      canonicalStagingRoot,
      canonicalHostedRoot,
      'canonical local hosted publication root',
    );
  }
  const existingKind = await targetKind(outputRoot);
  if (existingKind !== 'missing') {
    throw new Error(
      `Transactional sidecar output must be a new directory; ${outputRoot} is ${existingKind}.`,
    );
  }
  await mkdir(dirname(outputRoot), {recursive: true});
  await mkdir(stagingRoot, {recursive: false});

  logger.info(
    `Building recipe preview sidecar from ${rawRoot} with ` +
      `${resolvedProfile ?? 'an explicit programmatic contract'}.`,
  );
  logger.info(`Writing transaction staging directory ${stagingRoot}.`);

  try {
    const [rawManifest, hostedPublication, categoriesDocument, failures, warnings] =
      await Promise.all([
        readJsonFile(join(rawRoot, 'manifest.json'), 'Raw export manifest'),
        readHostedPublication(datasetManifest),
        readJsonFile(join(rawRoot, 'categories.json'), 'Raw export categories'),
        resolvedProfile === null
          ? Promise.resolve(null)
          : readJsonFile(join(rawRoot, 'failures.json'), 'Raw export failures'),
        resolvedProfile === MULTIBLOCK_MADNESS_112_PROFILE
          ? readJsonFile(join(rawRoot, 'warnings.json'), 'Raw export warnings')
          : Promise.resolve(undefined),
      ]);
    const datasetContract =
      contract ?? recipePreviewContractForProfile(resolvedProfile, rawManifest, warnings);
    validateContract(datasetContract, resolvedProfile);
    if (resolvedProfile !== null) {
      if (!Array.isArray(failures)) {
        throw new Error('Raw export failures.json must contain an array.');
      }
      const malformedFailureIndex = failures.findIndex(
        failure => typeof failure !== 'string' || failure.length === 0,
      );
      if (malformedFailureIndex >= 0) {
        throw new Error(
          `Raw export failures.json[${malformedFailureIndex}] must be a non-empty string.`,
        );
      }
      if (failures.length !== rawManifest?.counts?.failures) {
        throw new Error(
          `Raw export failures.json contains ${failures.length} entries, but ` +
            `manifest.counts.failures is ${String(rawManifest?.counts?.failures)}.`,
        );
      }
      assertProfileQuality(
        {manifest: rawManifest, failures, warnings, semanticErrorRecipes: 0},
        resolvedProfile,
        'Raw export metadata',
      );
    }
    const hostedManifest = hostedPublication.manifest;
    validateDatasetManifest(rawManifest, datasetContract, 'Raw export manifest');
    validateDatasetManifest(hostedManifest, datasetContract, 'Hosted dataset manifest', {
      hosted: true,
    });
    assertRawAndHostedIdentity(rawManifest, hostedManifest);
    await verifyHostedPublicationId(hostedPublication, logger);
    if (resolvedProfile === MULTIBLOCK_MADNESS_112_PROFILE) {
      const hostedWarnings = (
        await hostedPublication.readDocument('warnings.json', 'Hosted warnings.json')
      ).value;
      if (!isDeepStrictEqual(warnings, hostedWarnings)) {
        throw new Error(
          'Raw export warnings.json does not exactly match the hosted dataset publication; ' +
            'refusing to build a preview sidecar for unaudited warning metadata.',
        );
      }
      logger.info(
        `Validated ${warnings.length} audited Multiblock Madness warning event(s) against ` +
          'the hosted warnings.json document.',
      );
    }
    const categories = validateCategories(
      categoriesDocument,
      datasetContract.counts.categories,
      datasetContract.counts.recipes,
    );
    const hostedCategoriesDocument = (
      await hostedPublication.readDocument('categories.json', 'Hosted categories.json')
    ).value;
    assertRawAndHostedCategoryIdentity(categories, hostedCategoriesDocument);
    logger.info(
      `Validated dataset publication ${hostedManifest.publicationId} manifest and exact ` +
        `category identity for ${datasetContract.counts.categories} categories.`,
    );

    const packWriter = new PackWriter(stagingRoot, maxPackBytes);
    const visualCatalog = new VisualCatalog(packWriter, datasetContract.counts.recipes);
    const rawRecipeImageInventory = createRecipeImageInventory();
    const categoryDocuments = [];
    let categoryPartCount = 0;
    let recipeCount = 0;
    let previewReferences = 0;
    let sourcePngBytes = 0;
    let encodedWebpBytes = 0;
    let semanticErrorRecipes = 0;

    for (const [categoryIndex, category] of categories.entries()) {
      const categoryRoot = resolveInside(rawRoot, category.dir, `Category ${category.id}.dir`);
      await assertPlainDirectory(categoryRoot, `Category ${category.id} directory`);
      const recipesPath = join(categoryRoot, 'recipes.json');
      const hostedRecipesPath = posix.join(category.dir, 'recipes.json');
      const [recipes, hostedRecipesDocument] = await Promise.all([
        readJsonFile(recipesPath, `Category ${category.id} recipes`),
        hostedPublication.readDocument(
          hostedRecipesPath,
          `Hosted category ${JSON.stringify(category.id)} recipes.json`,
        ),
      ]);
      if (!Array.isArray(recipes) || recipes.length !== category.count) {
        throw new Error(
          `Category ${category.id} recipes.json must be an array of exactly ` +
            `${category.count} recipes; received ${Array.isArray(recipes) ? recipes.length : typeof recipes}.`,
        );
      }
      await assertRawAndHostedRecipeIdentity(
        recipes,
        hostedRecipesDocument.value,
        hostedPublication,
        category,
      );
      semanticErrorRecipes += recipes.reduce(
        (count, recipe) => count + (isRecord(recipe) && recipe.err === true ? 1 : 0),
        0,
      );
      rawRecipeImageInventory.beginCategory({
        categoryIndex,
        categoryId: category.id,
        recipeCount: recipes.length,
      });

      const previews = new Array(recipes.length);
      for (let batchStart = 0; batchStart < recipes.length; batchStart += concurrency) {
        const batchEnd = Math.min(recipes.length, batchStart + concurrency);
        const jobs = [];
        for (let recipeIndex = batchStart; recipeIndex < batchEnd; recipeIndex += 1) {
          const sourceImage = validateRecipeImage(
            recipes[recipeIndex],
            category,
            recipeIndex,
            rawRoot,
            datasetContract.settings.recipeScale,
          );
          if (sourceImage === null) {
            jobs.push(Promise.resolve(null));
          } else {
            jobs.push(
              decodePng(sourceImage),
            );
          }
        }
        const decodedBatch = await Promise.all(jobs);
        const {coordinates, logicalEncodedBytes} =
          await visualCatalog.coordinatesFor(decodedBatch);
        encodedWebpBytes += logicalEncodedBytes;
        for (const [batchIndex, decoded] of decodedBatch.entries()) {
          const recipeIndex = batchStart + batchIndex;
          if (decoded === null) {
            rawRecipeImageInventory.addMissing({
              categoryIndex,
              categoryId: category.id,
              recipeIndex,
            });
            previews[recipeIndex] = null;
            continue;
          }
          const recipe = recipes[recipeIndex];
          rawRecipeImageInventory.addPreview({
            categoryIndex,
            categoryId: category.id,
            recipeIndex,
            logicalPngPath: normalizedLogicalRecipePngPath(category.dir, recipe.img),
            declaredWidth: recipe.w,
            declaredHeight: recipe.h,
            decodedWidth: decoded.width,
            decodedHeight: decoded.height,
            rgbaSha256: decoded.rgbaSha256,
          });
          previewReferences += 1;
          sourcePngBytes += decoded.sourceBytes;
          previews[recipeIndex] = coordinates[batchIndex];
        }
      }

      recipeCount += recipes.length;
      assertCoordinateBounds(previews, packWriter, category.id);
      const mapping = await writeCategoryMapping({
        root: stagingRoot,
        category,
        categoryIndex,
        previews,
        maxCategoryBytes,
      });
      categoryDocuments.push(...mapping.documents);
      categoryPartCount += mapping.partCount;
      if ((categoryIndex + 1) % 25 === 0 || categoryIndex + 1 === categories.length) {
        logger.info(
          `Processed ${categoryIndex + 1}/${categories.length} categories and ` +
            `${recipeCount}/${datasetContract.counts.recipes} recipes.`,
        );
      }
    }

    if (recipeCount !== datasetContract.counts.recipes) {
      throw new Error(
        `Processed ${recipeCount} recipes; expected ${datasetContract.counts.recipes}.`,
      );
    }
    if (resolvedProfile !== null) {
      assertProfileQuality(
        {manifest: rawManifest, failures, warnings, semanticErrorRecipes},
        resolvedProfile,
        'Raw export corpus',
      );
    }
    const omittedRecipeImages = hostedManifest.web.recipeImages;
    const computedRecipeImageInventory = rawRecipeImageInventory.finish();
    if (!isDeepStrictEqual(computedRecipeImageInventory, omittedRecipeImages.inventory)) {
      throw new Error(
        'Raw recipe preview pixels/path ordering do not match the hosted publication inventory: ' +
          `raw=${computedRecipeImageInventory.sha256}, ` +
          `hosted=${omittedRecipeImages.inventory.sha256}.`,
      );
    }
    if (
      omittedRecipeImages.references !== previewReferences ||
      omittedRecipeImages.files !== previewReferences
    ) {
      throw new Error(
        'Raw recipe image inventory does not match hosted manifest web.recipeImages: ' +
          `processed references/files=${previewReferences}/${previewReferences}, ` +
          `hosted=${omittedRecipeImages.references}/${omittedRecipeImages.files}.`,
      );
    }
    if (omittedRecipeImages.bytes !== sourcePngBytes) {
      throw new Error(
        'Raw recipe PNG bytes do not match hosted manifest web.recipeImages accounting: ' +
          `raw=${sourcePngBytes}, hosted=${omittedRecipeImages.bytes}.`,
      );
    }
    logger.info(
      `Validated recipe-image inventory ${computedRecipeImageInventory.sha256} across ` +
        `${recipeCount} ordered recipes and ${previewReferences} raw PNG references ` +
        `(${sourcePngBytes} bytes), exactly matching the hosted original-PNG omission accounting.`,
    );

    const packs = await packWriter.finish();
    const sortedCategoryDocuments = [...categoryDocuments].sort((a, b) =>
      a.path < b.path ? -1 : a.path > b.path ? 1 : 0,
    );
    const packIndexRecords = packs.map(pack => pack.index);
    const allContentRecords = [...packs, ...packIndexRecords, ...sortedCategoryDocuments];
    await verifyGeneratedFiles(stagingRoot, allContentRecords, {
      maxPackBytes,
      maxPackIndexBytes: MAX_PACK_INDEX_BYTES,
      maxCategoryBytes,
    });
    const assetSetId = computeAssetSetId(allContentRecords, hostedManifest.publicationId);
    const packBytes = packs.reduce((sum, pack) => sum + pack.bytes, 0);
    const packIndexTotalBytes = packIndexRecords.reduce((sum, index) => sum + index.bytes, 0);
    const categoryBytes = sortedCategoryDocuments.reduce(
      (sum, document) => sum + document.bytes,
      0,
    );
    const missingPreviews = recipeCount - previewReferences;
    if (
      previewReferences !== datasetContract.recipeImages.previews ||
      missingPreviews !== datasetContract.recipeImages.missing
    ) {
      throw new Error(
        'Processed recipe previews do not match the audited contract: ' +
          `expected previews/missing=${datasetContract.recipeImages.previews}/` +
          `${datasetContract.recipeImages.missing}, ` +
          `received ${previewReferences}/${missingPreviews}.`,
      );
    }
    const manifest = {
      format: RECIPE_PREVIEW_SIDECAR_FORMAT,
      assetSetId,
      datasetPublicationId: hostedManifest.publicationId,
      maxPackBytes,
      packIndexFormat: RECIPE_PREVIEW_PACK_INDEX_FORMAT,
      maxPackIndexBytes: MAX_PACK_INDEX_BYTES,
      imageFormat: IMAGE_FORMAT,
      categoryFormat: RECIPE_PREVIEW_CATEGORY_FORMAT,
      settings: {
        itemIconPixels: 16 * datasetContract.settings.iconScale,
        recipeScale: datasetContract.settings.recipeScale,
        webpEffort: WEBP_EFFORT,
        maxCategoryBytes,
      },
      counts: {
        categories: categories.length,
        recipes: recipeCount,
        previews: previewReferences,
        missing: missingPreviews,
        uniqueImages: visualCatalog.uniquePreviews,
        duplicates: visualCatalog.deduplicatedPreviews,
        packs: packs.length,
        inputBytes: sourcePngBytes,
        hostedOmittedPngBytes: omittedRecipeImages.bytes,
        encodedBytes: encodedWebpBytes,
        storedBytes: packBytes,
        packIndexBytes: packIndexTotalBytes,
      },
      packs,
      mapping: {
        documents: sortedCategoryDocuments.length,
        parts: categoryPartCount,
        bytes: categoryBytes,
      },
      categoryDocuments: sortedCategoryDocuments,
    };
    const manifestRecord = await writeOutputDocument(
      stagingRoot,
      'manifest.json',
      jsonBytes(manifest),
    );
    await verifyGeneratedFiles(stagingRoot, [manifestRecord], {
      maxPackBytes,
      maxPackIndexBytes: MAX_PACK_INDEX_BYTES,
      maxCategoryBytes,
    });
    await verifyHostedPublicationId(hostedPublication, logger);
    const finalKind = await targetKind(outputRoot);
    if (finalKind !== 'missing') {
      throw new Error(
        `Transactional output target appeared during the build; refusing to replace ${outputRoot}.`,
      );
    }
    await rename(stagingRoot, outputRoot);
    logger.info(
      `Recipe preview sidecar ${assetSetId} committed to ${outputRoot}: ` +
        `${previewReferences} previews, ${visualCatalog.uniquePreviews} unique visuals, ` +
        `${packs.length} packs, ${packBytes} packed bytes.`,
    );
    return manifest;
  } catch (error) {
    logger.error(`Recipe preview sidecar build failed; removing staging directory ${stagingRoot}.`, error);
    try {
      await rm(stagingRoot, {recursive: true, force: true});
    } catch (cleanupError) {
      logger.error(`Failed to remove recipe preview staging directory ${stagingRoot}.`, cleanupError);
    }
    throw error;
  }
}

function parseCliArgs(argv) {
  const options = {};
  const names = new Map([
    ['--source', 'source'],
    ['--dataset-manifest', 'datasetManifest'],
    ['--output', 'output'],
    ['--profile', 'profile'],
    ['--concurrency', 'concurrency'],
  ]);
  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    const name = names.get(flag);
    if (!name) throw new Error(`Unknown argument ${flag}.`);
    const value = argv[index + 1];
    if (value === undefined || value.startsWith('--')) {
      throw new Error(`Argument ${flag} requires a value.`);
    }
    if (options[name] !== undefined) throw new Error(`Argument ${flag} was provided more than once.`);
    options[name] = name === 'concurrency' ? Number(value) : value;
    index += 1;
  }
  for (const required of ['source', 'datasetManifest', 'output', 'profile']) {
    if (options[required] === undefined) {
      throw new Error(
        'Usage: node scripts/build-recipe-preview-sidecar.mjs --source <raw-export> ' +
          '--dataset-manifest <local-hosted-manifest-path> --output <new-directory> ' +
          `--profile <${EXPORT_QUALITY_PROFILE_IDS.join('|')}> ` +
          '[--concurrency <1-64>]',
      );
    }
  }
  options.profile = resolveQualityProfile(options.profile);
  return options;
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath === fileURLToPath(import.meta.url)) {
  try {
    await buildRecipePreviewSidecar(parseCliArgs(process.argv.slice(2)));
  } catch (error) {
    console.error(`Recipe preview sidecar builder terminated: ${error.message}`);
    process.exitCode = 1;
  }
}
