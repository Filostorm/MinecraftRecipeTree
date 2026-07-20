import {isRecord} from './export-data-utils.mjs';
import {requirePackIdentity} from './pack-identity.mjs';

export const GENERIC_JEI_120_PROFILE = 'generic-jei-1.20.1';
export const MEATBALLCRAFT_112_PROFILE = 'meatballcraft-1.12.2';
export const MULTIBLOCK_MADNESS_112_PROFILE = 'multiblock-madness-1.12.2';
export const MULTIBLOCK_MADNESS_2_118_PROFILE = 'multiblock-madness-2-1.18.2';
export const GTNH_1710_PROFILE = 'gtnh-1.7.10';

export const MULTIBLOCK_MADNESS_112_WARNING_PREFIXES = Object.freeze([
  'ZERO_PREREQUISITE',
  'ZERO_ABSENT_OUTPUT',
  'ZERO_ABSENT_ALTERNATIVE',
  'UPSTREAM_BLANK_DISPLAY_NAME',
  'NATIVE_ICON_OVERSCAN_RECOVERY_APPLIED',
  'UPSTREAM_NATIVE_ICON_UNAVAILABLE',
]);

export const GTNH_NEI_DIAGNOSTIC_KEYS = Object.freeze([
  'itemListLoaded',
  'registeredCraftingHandlers',
  'exportableCraftingHandlers',
  'adaptedHandlerCategories',
  'excludedNonRecipeHandlers',
  'loadedCategories',
  'recipesEnumerated',
  'recipeWidgetsRendered',
  'itemIconsRendered',
  'informationalEmptyOutputRecipes',
  'knowledgeIndependentAspectNames',
  'unloadedHandlerCategories',
  'ambiguousHandlerCategories',
  'duplicateHandlerCategories',
]);

export const GTNH_HANDLER_POLICIES = Object.freeze([
  Object.freeze({
    handlerClass: 'appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler',
    handlerId: 'appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler',
    action: 'adapted-informational-category',
    contract: 'adapter:ae2-in-world-crafting-wildcard-query-closure-v1',
  }),
  Object.freeze({
    handlerClass: 'bq_standard.integration.nei.QuestRecipeHandler',
    handlerId: 'bq_standard.integration.nei.QuestRecipeHandler',
    action: 'adapted-informational-category',
    contract: 'adapter:betterquesting-complete-item-reference-pages-v1',
  }),
  Object.freeze({
    handlerClass: 'com.rwtema.extrautils.nei.InfoHandler',
    handlerId: 'com.rwtema.extrautils.nei.InfoHandler',
    action: 'excluded-non-recipe-query',
    contract: 'query-only:extrautilities-item-documentation-v1',
  }),
  Object.freeze({
    handlerClass: 'com.rwtema.extrautils.nei.SoulHandler',
    handlerId: 'com.rwtema.extrautils.nei.SoulHandler',
    action: 'adapted-complete-category',
    contract: 'adapter:extrautilities-soul-crafting-item-query-v1',
  }),
  Object.freeze({
    handlerClass: 'hellfirepvp.beebetteratbees.client.gui.BBABGuiRecipeTreeHandler',
    handlerId: 'hellfirepvp.beebetteratbees.client.gui.BBABGuiRecipeTreeHandler',
    action: 'excluded-non-recipe-query',
    contract: 'query-only:bee-breeding-recursive-lineage-visualization-v1',
  }),
  Object.freeze({
    handlerClass: 'ic2.neiIntegration.core.recipehandler.LatheRecipeHandler',
    handlerId: 'ic2.neiIntegration.core.recipehandler.LatheRecipeHandler',
    action: 'excluded-non-recipe-query',
    contract: 'query-only:ic2-lathe-interactive-workpiece-state-v1',
  }),
  Object.freeze({
    handlerClass:
      'micdoodle8.mods.galacticraft.core.nei.ElectricIngotCompressorRecipeHandler',
    handlerId:
      'micdoodle8.mods.galacticraft.core.nei.ElectricIngotCompressorRecipeHandler',
    action: 'adapted-complete-category',
    contract: 'public-recipe-id:galacticraft.electricingotcompressor-v1',
  }),
  Object.freeze({
    handlerClass: 'micdoodle8.mods.galacticraft.core.nei.IngotCompressorRecipeHandler',
    handlerId: 'micdoodle8.mods.galacticraft.core.nei.IngotCompressorRecipeHandler',
    action: 'adapted-complete-category',
    contract: 'public-recipe-id:galacticraft.ingotcompressor-v1',
  }),
  Object.freeze({
    handlerClass:
      'micdoodle8.mods.galacticraft.planets.mars.nei.GasLiquefierRecipeHandler',
    handlerId:
      'micdoodle8.mods.galacticraft.planets.mars.nei.GasLiquefierRecipeHandler',
    action: 'adapted-complete-category',
    contract: 'public-recipe-id:galacticraft.liquefier-v1',
  }),
  Object.freeze({
    handlerClass:
      'micdoodle8.mods.galacticraft.planets.mars.nei.MethaneSynthesizerRecipeHandler',
    handlerId:
      'micdoodle8.mods.galacticraft.planets.mars.nei.MethaneSynthesizerRecipeHandler',
    action: 'adapted-complete-category',
    contract: 'public-recipe-id:galacticraft.synthesizer-v1',
  }),
  Object.freeze({
    handlerClass: 'ru.timeconqueror.tcneiadditions.nei.AspectCombinationHandler',
    handlerId: 'ru.timeconqueror.tcneiadditions.nei.AspectCombinationHandler',
    action: 'excluded-non-recipe-query',
    contract: 'query-only:player-discovered-aspect-combination-view-v1',
  }),
  Object.freeze({
    handlerClass: 'ru.timeconqueror.tcneiadditions.nei.AspectFromItemStackHandler',
    handlerId: 'ru.timeconqueror.tcneiadditions.nei.AspectFromItemStackHandler',
    action: 'excluded-non-recipe-query',
    contract: 'query-only:player-scanned-item-aspect-view-v1',
  }),
  Object.freeze({
    handlerClass: 'speiger.src.crops.prediction.NEIPlugin',
    handlerId: 'speiger.src.crops.prediction.NEIPlugin',
    action: 'adapted-complete-category',
    contract: 'adapter:ic2-crop-deterministic-query-bucket-closure-v1',
  }),
  Object.freeze({
    handlerClass: 'tconstruct.plugins.nei.RecipeHandlerToolMaterials',
    handlerId: 'tconstruct.plugins.nei.RecipeHandlerToolMaterials',
    action: 'excluded-non-recipe-presentation',
    contract: 'presentation-only:tconstruct-tool-material-statistics-v1',
  }),
  Object.freeze({
    handlerClass: 'tonius.neiintegration.mods.mcforge.RecipeHandlerFluidRegistry',
    handlerId: 'tonius.neiintegration.mods.mcforge.RecipeHandlerFluidRegistry',
    action: 'excluded-non-recipe-presentation',
    contract: 'presentation-only:forge-fluid-registry-browser-v1',
  }),
  Object.freeze({
    handlerClass: 'tonius.neiintegration.mods.mcforge.RecipeHandlerOreDictionary',
    handlerId: 'tonius.neiintegration.mods.mcforge.RecipeHandlerOreDictionary',
    action: 'excluded-non-recipe-presentation',
    contract: 'presentation-only:forge-ore-dictionary-equivalence-browser-v1',
  }),
  Object.freeze({
    handlerClass:
      'vazkii.botania.client.integration.nei.recipe.RecipeHandlerLexicaBotania',
    handlerId:
      'vazkii.botania.client.integration.nei.recipe.RecipeHandlerLexicaBotania',
    action: 'excluded-non-recipe-presentation',
    contract: 'presentation-only:botania-lexica-cross-reference-v1',
  }),
]);

export const GTNH_KNOWLEDGE_POLICY = Object.freeze({
  playerResearchMutated: false,
  thaumcraftLockedRecipes: 'required-by-pinned-config',
  itemAspectDisplayNames: 'nbt-aspect-registry-v1',
});

export const GTNH_DATA_ATTRIBUTION = Object.freeze({
  sourceUrl:
    'https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/tree/2.8.4',
  projectUrl: 'https://www.gtnewhorizons.com/',
  licenseIdentifier: 'CC BY-NC-SA 4.0',
  licenseUrl: 'https://creativecommons.org/licenses/by-nc-sa/4.0/',
});

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
      requiresExporterBuildIdentity: true,
      requiresZeroFailures: true,
      diagnosticKeys: Object.freeze([
        'failureEvents',
        'failureEventsOmitted',
        'warningEvents',
        'warningEventsOmitted',
      ]),
      packIdentity: Object.freeze({
        name: 'Multiblock Madness',
        version: '3.2.3',
        identitySource: 'explicit-request',
      }),
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
      requiresExporterBuildIdentity: true,
      requiresZeroFailures: true,
      diagnosticKeys: Object.freeze([
        'failureEvents',
        'failureEventsOmitted',
        'nativeIconCorrections',
        'transparentIcons',
      ]),
      packIdentity: Object.freeze({
        name: 'Multiblock Madness 2',
        version: '1.0.0',
        identitySource: 'explicit-request',
      }),
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
      requiresExporterBuildIdentity: true,
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
      attribution: GTNH_DATA_ATTRIBUTION,
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

function exactRecordIssues(value, expected, label) {
  if (!hasExactKeys(value, Object.keys(expected))) {
    return [
      `${label} must contain exactly ${Object.keys(expected).join(', ')}.`,
    ];
  }
  const issues = [];
  for (const [name, expectedValue] of Object.entries(expected)) {
    if (value[name] !== expectedValue) {
      issues.push(
        `${label}.${name} must be ${JSON.stringify(expectedValue)}; received ` +
          `${JSON.stringify(value[name])}.`,
      );
    }
  }
  return issues;
}

function gtnhPolicyIssues(manifest, label) {
  const issues = [];
  if (!Array.isArray(manifest?.handlerPolicies)) {
    issues.push(`${label} manifest.handlerPolicies must be an array.`);
  } else if (manifest.handlerPolicies.length !== GTNH_HANDLER_POLICIES.length) {
    issues.push(
      `${label} manifest.handlerPolicies must contain exactly ` +
        `${GTNH_HANDLER_POLICIES.length} pinned handler policies; received ` +
        `${manifest.handlerPolicies.length}.`,
    );
  } else {
    for (let index = 0; index < GTNH_HANDLER_POLICIES.length; index += 1) {
      issues.push(
        ...exactRecordIssues(
          manifest.handlerPolicies[index],
          GTNH_HANDLER_POLICIES[index],
          `${label} manifest.handlerPolicies[${index}]`,
        ),
      );
    }
  }
  issues.push(
    ...exactRecordIssues(
      manifest?.knowledgePolicy,
      GTNH_KNOWLEDGE_POLICY,
      `${label} manifest.knowledgePolicy`,
    ),
  );
  issues.push(
    ...exactRecordIssues(
      manifest?.attribution,
      GTNH_DATA_ATTRIBUTION,
      `${label} manifest.attribution`,
    ),
  );
  return issues;
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
  issues.push(...gtnhPolicyIssues(manifest, label));
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
    isNonNegativeSafeInteger(counts?.categories) ? counts.categories + 9 : undefined,
    'manifest.counts.categories + 9 excluded non-recipe handlers',
  );
  compareTelemetry(
    'exportableCraftingHandlers',
    counts?.categories,
    'manifest.counts.categories',
  );
  compareTelemetry('loadedCategories', counts?.categories, 'manifest.counts.categories');
  compareTelemetry('recipesEnumerated', counts?.recipes, 'manifest.counts.recipes');
  compareTelemetry('recipeWidgetsRendered', counts?.recipes, 'manifest.counts.recipes');
  compareTelemetry('itemIconsRendered', counts?.items, 'manifest.counts.items');

  for (const [name, expected] of [
    ['adaptedHandlerCategories', 8],
    ['excludedNonRecipeHandlers', 9],
    ['informationalEmptyOutputRecipes', 488],
  ]) {
    if (isNonNegativeSafeInteger(nei?.[name]) && nei[name] !== expected) {
      issues.push(
        `${label} requires diagnostics.nei.${name} to be ${expected}; received ${nei[name]}.`,
      );
    }
  }
  if (
    isNonNegativeSafeInteger(nei?.knowledgeIndependentAspectNames) &&
    nei.knowledgeIndependentAspectNames === 0
  ) {
    issues.push(
      `${label} requires diagnostics.nei.knowledgeIndependentAspectNames to be positive.`,
    );
  }

  return issues;
}

/**
 * Return profile-specific publication blockers. General graph and asset
 * integrity is enforced by validate-export-data; this policy covers the
 * explicit pack exporter contracts that generic datasets do not share.
 */
export function exportQualityIssues(
  {manifest, failures, warnings, semanticErrorRecipes = 0},
  profile,
) {
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
    if (
      Array.isArray(requirements.diagnosticKeys) &&
      !hasExactKeys(diagnostics, requirements.diagnosticKeys)
    ) {
      issues.push(
        `${label} requires manifest.diagnostics to contain exactly ` +
          `${requirements.diagnosticKeys.join(', ')}.`,
      );
    }
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
    if (requirements.requiresZeroFailures && diagnostics.failureEvents !== 0) {
      issues.push(
        `${label} requires diagnostics.failureEvents to be 0; received ` +
          `${String(diagnostics.failureEvents)}.`,
      );
    }
    if (resolvedProfile === MULTIBLOCK_MADNESS_112_PROFILE) {
      if (!isNonNegativeSafeInteger(diagnostics.warningEvents)) {
        issues.push(`${label} requires a non-negative diagnostics.warningEvents count.`);
      }
      if (!isNonNegativeSafeInteger(diagnostics.warningEventsOmitted)) {
        issues.push(`${label} requires a non-negative diagnostics.warningEventsOmitted count.`);
      } else if (diagnostics.warningEventsOmitted !== 0) {
        issues.push(
          `${label} export omitted ${diagnostics.warningEventsOmitted} warning event(s); ` +
            'publication requires the complete audited warning set.',
        );
      }
    }
    if (
      resolvedProfile === MULTIBLOCK_MADNESS_2_118_PROFILE &&
      diagnostics.transparentIcons !== 0
    ) {
      issues.push(
        `${label} requires diagnostics.transparentIcons to be 0; received ` +
          `${String(diagnostics.transparentIcons)}.`,
      );
    }
    if (
      resolvedProfile === MULTIBLOCK_MADNESS_2_118_PROFILE &&
      !isNonNegativeSafeInteger(diagnostics.nativeIconCorrections)
    ) {
      issues.push(`${label} requires a non-negative diagnostics.nativeIconCorrections count.`);
    }
  }

  if (requirements.requiresZeroFailures && manifest?.counts?.failures !== 0) {
    issues.push(
      `${label} requires manifest.counts.failures to be 0; received ` +
        `${String(manifest?.counts?.failures)}.`,
    );
  }

  if (resolvedProfile === GTNH_1710_PROFILE) {
    issues.push(...gtnhManifestQualityIssues(manifest, label));
  } else if (resolvedProfile === GENERIC_JEI_120_PROFILE) {
    issues.push(...genericJeiManifestQualityIssues(manifest, label));
  }

  if (resolvedProfile === MULTIBLOCK_MADNESS_112_PROFILE) {
    if (!Array.isArray(warnings)) {
      issues.push(`${label} requires warnings.json to contain an array.`);
    } else {
      const malformedWarningIndex = warnings.findIndex(
        warning => typeof warning !== 'string' || warning.trim().length === 0,
      );
      if (malformedWarningIndex >= 0) {
        issues.push(
          `${label} warnings.json[${malformedWarningIndex}] must be a non-empty string.`,
        );
      }
      if (
        isNonNegativeSafeInteger(manifest?.diagnostics?.warningEvents) &&
        warnings.length !== manifest.diagnostics.warningEvents
      ) {
        issues.push(
          `${label} diagnostics.warningEvents (${manifest.diagnostics.warningEvents}) must ` +
            `equal warnings.json length (${warnings.length}).`,
        );
      }
      const unknownWarningIndex = warnings.findIndex(
        warning =>
          typeof warning === 'string' &&
          !MULTIBLOCK_MADNESS_112_WARNING_PREFIXES.some(
            prefix => warning === prefix || warning.startsWith(`${prefix} `),
          ),
      );
      if (unknownWarningIndex >= 0) {
        issues.push(
          `${label} warnings.json[${unknownWarningIndex}] has an unrecognized warning class: ` +
            `${JSON.stringify(warnings[unknownWarningIndex])}. Allowed prefixes: ` +
            `${MULTIBLOCK_MADNESS_112_WARNING_PREFIXES.join(', ')}.`,
        );
      }
    }
  }

  if (!Array.isArray(failures)) {
    issues.push(`${label} requires failures.json to contain an array.`);
    return issues;
  }

  if (requirements.requiresZeroFailures && failures.length !== 0) {
    issues.push(
      `${label} requires failures.json to be empty; received ${failures.length} ` +
        `failure event(s); first: ${String(failures[0])}`,
    );
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
