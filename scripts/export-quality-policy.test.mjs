import assert from 'node:assert/strict';
import test from 'node:test';
import {
  EXPORT_QUALITY_PROFILE_IDS,
  exportQualityIssues,
  GENERIC_JEI_120_PROFILE,
  GTNH_1710_PROFILE,
  MEATBALLCRAFT_112_PROFILE,
  MULTIBLOCK_MADNESS_112_PROFILE,
  MULTIBLOCK_MADNESS_2_118_PROFILE,
  qualityProfileRequirementsFor,
  resolveQualityProfile,
} from './export-quality-policy.mjs';

const validManifest = {
  format: 1,
  minecraft: '1.12.2',
  aborted: false,
  settings: {iconScale: 3, recipeScale: 2},
  diagnostics: {failureEvents: 2, failureEventsOmitted: 0},
};

test('accepts the exact MeatballCraft 1.12.2 exporter contract', () => {
  assert.deepEqual(
    exportQualityIssues(
      {manifest: validManifest, failures: ['item icon x', 'recipe image y'], semanticErrorRecipes: 0},
      MEATBALLCRAFT_112_PROFILE,
    ),
    [],
  );
});

test('registers explicit immutable requirements for all production pack profiles', () => {
  assert.deepEqual(EXPORT_QUALITY_PROFILE_IDS, [
    GENERIC_JEI_120_PROFILE,
    MEATBALLCRAFT_112_PROFILE,
    MULTIBLOCK_MADNESS_112_PROFILE,
    MULTIBLOCK_MADNESS_2_118_PROFILE,
    GTNH_1710_PROFILE,
  ]);
  assert.deepEqual(qualityProfileRequirementsFor(GENERIC_JEI_120_PROFILE), {
    id: GENERIC_JEI_120_PROFILE,
    label: 'Generic JEI 1.20.1',
    minecraft: '1.20.1',
    format: 1,
    iconScale: 4,
    recipeScale: 2,
    recipeViewer: 'JEI',
    corpus: 'dynamic-complete',
    requiresPackIdentity: true,
  });
  assert.deepEqual(qualityProfileRequirementsFor(MULTIBLOCK_MADNESS_112_PROFILE), {
    id: MULTIBLOCK_MADNESS_112_PROFILE,
    label: 'Multiblock Madness',
    minecraft: '1.12.2',
    format: 1,
    iconScale: 1,
    recipeScale: 2,
    recipeViewer: 'HEI',
    corpus: 'dynamic-complete',
  });
  assert.deepEqual(qualityProfileRequirementsFor(MULTIBLOCK_MADNESS_2_118_PROFILE), {
    id: MULTIBLOCK_MADNESS_2_118_PROFILE,
    label: 'Multiblock Madness 2',
    minecraft: '1.18.2',
    format: 1,
    iconScale: 1,
    recipeScale: 2,
    recipeViewer: 'REI',
    corpus: 'dynamic-complete',
  });
  assert.deepEqual(qualityProfileRequirementsFor(GTNH_1710_PROFILE), {
    id: GTNH_1710_PROFILE,
    label: 'GT New Horizons',
    minecraft: '1.7.10',
    format: 1,
    iconScale: 1,
    recipeScale: 2,
    recipeViewer: 'NEI',
    corpus: 'dynamic-complete',
    provenance: {
      profile: GTNH_1710_PROFILE,
      forge: '10.13.4.1614',
      nei: '2.8.44-GTNH',
    },
    packIdentity: {
      name: 'GT New Horizons',
      version: '2.8.4',
      identitySource: 'explicit-request',
    },
  });
});

function validGenericJeiManifest() {
  return {
    format: 1,
    minecraft: '1.20.1',
    pack: {
      name: 'Example Modern Pack',
      version: '4.2.0',
      identitySource: 'explicit-request',
    },
    aborted: false,
    settings: {iconScale: 4, recipeScale: 2, mobCanvas: 256},
    counts: {items: 3, recipes: 2, categories: 1, mobs: 0, blockDrops: 0, failures: 1},
    diagnostics: {failureEvents: 1, failureEventsOmitted: 0},
  };
}

test('accepts the strict generic JEI 1.20.1 manifest telemetry and pack identity', () => {
  assert.deepEqual(
    exportQualityIssues(
      {
        manifest: validGenericJeiManifest(),
        failures: ['mob example:missing_renderer: renderer unavailable'],
        semanticErrorRecipes: 0,
      },
      GENERIC_JEI_120_PROFILE,
    ),
    [],
  );
});

test('generic JEI profile rejects drifted diagnostics, counts, and identity', () => {
  const manifest = validGenericJeiManifest();
  manifest.counts.futureCounter = 1;
  manifest.diagnostics.futureCounter = 1;
  manifest.diagnostics.failureEvents = 0;
  manifest.pack.identitySource = 'untrusted-launcher';
  const issues = exportQualityIssues(
    {manifest, failures: [], semanticErrorRecipes: 0},
    GENERIC_JEI_120_PROFILE,
  );
  assert.match(issues.join('\n'), /manifest\.counts must contain exactly/);
  assert.match(issues.join('\n'), /manifest\.diagnostics must contain exactly/);
  assert.match(issues.join('\n'), /failureEvents \(0\).*counts\.failures \(1\)/);
  assert.match(issues.join('\n'), /identitySource/);
});

function validGtnhManifest() {
  return {
    format: 1,
    minecraft: '1.7.10',
    profile: GTNH_1710_PROFILE,
    forge: '10.13.4.1614',
    nei: '2.8.44-GTNH',
    pack: {
      name: 'GT New Horizons',
      version: '2.8.4',
      identitySource: 'explicit-request',
    },
    aborted: false,
    settings: {iconScale: 1, recipeScale: 2},
    counts: {items: 2, recipes: 3, categories: 1, mobs: 0, blockDrops: 0, failures: 0},
    diagnostics: {
      failureEvents: 0,
      failureEventsOmitted: 0,
      nei: {
        itemListLoaded: true,
        registeredCraftingHandlers: 1,
        loadedCategories: 1,
        recipesEnumerated: 3,
        recipeWidgetsRendered: 3,
        itemIconsRendered: 2,
        unloadedHandlerCategories: 0,
        ambiguousHandlerCategories: 0,
        duplicateHandlerCategories: 0,
      },
    },
  };
}

test('accepts only a complete zero-failure GTNH NEI export contract', () => {
  assert.deepEqual(
    exportQualityIssues(
      {manifest: validGtnhManifest(), failures: [], semanticErrorRecipes: 0},
      GTNH_1710_PROFILE,
    ),
    [],
  );
});

test('GTNH rejects unloaded, unknown, inconsistent, or drifted NEI diagnostics', () => {
  const manifest = validGtnhManifest();
  manifest.diagnostics.nei.unloadedHandlerCategories = 1;
  manifest.diagnostics.nei.recipeWidgetsRendered = 2;
  manifest.diagnostics.nei.unexpectedHandlerCategories = 1;
  manifest.pack.version = '2.8.3';
  const issues = exportQualityIssues(
    {manifest, failures: [], semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(issues.join('\n'), /diagnostics\.nei must contain exactly/);
  assert.match(issues.join('\n'), /unloadedHandlerCategories.*0/);
  assert.match(issues.join('\n'), /recipeWidgetsRendered.*counts\.recipes/);
  assert.match(issues.join('\n'), /manifest\.pack\.version "2\.8\.4"/);
});

test('GTNH rejects every serialized failure and classifies exporter failure prefixes', () => {
  const manifest = validGtnhManifest();
  const failures = [
    'HANDLER_UNLOADED: nei.handler',
    'RECIPE_SEMANTICS: nei.handler #4',
    'QUANTITY_INVALID: nei.handler #4 input 0',
    'RECIPE_WIDGET_RENDER: nei.handler #4',
    'UNRECOGNIZED_FUTURE_FAILURE: must remain fail-closed',
  ];
  manifest.counts.failures = failures.length;
  manifest.diagnostics.failureEvents = failures.length;
  const issues = exportQualityIssues(
    {manifest, failures, semanticErrorRecipes: 0},
    GTNH_1710_PROFILE,
  );
  assert.match(issues.join('\n'), /counts\.failures to be 0/);
  assert.match(issues.join('\n'), /failureEvents to be 0/);
  assert.match(issues.join('\n'), /failures\.json to be empty/);
  assert.match(issues.join('\n'), /category failure/);
  assert.match(issues.join('\n'), /ingredient-semantics/);
  assert.match(issues.join('\n'), /ingredient-quantity/);
  assert.match(issues.join('\n'), /recipe-preview render\/write/);
});

test('accepts dynamic complete Multiblock Madness profiles only at 16px icons and 2x layouts', () => {
  for (const [profile, minecraft] of [
    [MULTIBLOCK_MADNESS_112_PROFILE, '1.12.2'],
    [MULTIBLOCK_MADNESS_2_118_PROFILE, '1.18.2'],
  ]) {
    assert.deepEqual(
      exportQualityIssues(
        {
          manifest: {
            ...validManifest,
            minecraft,
            settings: {iconScale: 1, recipeScale: 2},
          },
          failures: [],
          semanticErrorRecipes: 0,
        },
        profile,
      ),
      [],
    );
  }
});

test('rejects Multiblock Madness version, scale, semantic, and unclassified-zero drift', () => {
  const issues = exportQualityIssues(
    {
      manifest: {
        ...validManifest,
        minecraft: '1.12.2',
        settings: {iconScale: 3, recipeScale: 1},
      },
      failures: [
        'recipe output ingredient rei.machine #2: ZERO_UNCLASSIFIED no exact semantic adapter exists',
      ],
      semanticErrorRecipes: 1,
    },
    MULTIBLOCK_MADNESS_2_118_PROFILE,
  );
  assert.match(issues.join('\n'), /manifest\.minecraft "1\.18\.2"/);
  assert.match(issues.join('\n'), /16×16 item canvases/);
  assert.match(issues.join('\n'), /REI layouts rendered at 2×/);
  assert.match(issues.join('\n'), /unclassified zero-quantity/);
  assert.match(issues.join('\n'), /ingredient-semantics/);
  assert.match(issues.join('\n'), /err=true/);
});

test('rejects version, abort, omitted, category, quantity, catalog, and semantic defects', () => {
  const issues = exportQualityIssues(
    {
      manifest: {
        format: 2,
        minecraft: '1.20.1',
        aborted: true,
        settings: {iconScale: 1, recipeScale: 1},
        diagnostics: {failureEvents: 5, failureEventsOmitted: 1},
      },
      failures: [
        'category recipes mod.machine: failure',
        'ingredient amount type mod.FluidStack has no recognized numeric amount/count accessor; using quantity 1 for this type',
        'list ingredients for mod.CustomIngredient: failure',
        'recipe input ingredient mod.machine #2: failure',
      ],
      semanticErrorRecipes: 1,
    },
    MEATBALLCRAFT_112_PROFILE,
  );

  assert.equal(issues.length, 11);
  assert.match(issues.join('\n'), /format 1/);
  assert.match(issues.join('\n'), /1\.12\.2/);
  assert.match(issues.join('\n'), /aborted/);
  assert.match(issues.join('\n'), /48×48 item canvases/);
  assert.match(issues.join('\n'), /2× physical resolution/);
  assert.match(issues.join('\n'), /omitted 1/);
  assert.match(issues.join('\n'), /category failure/);
  assert.match(issues.join('\n'), /ingredient-quantity/);
  assert.match(issues.join('\n'), /incomplete ingredient-catalog/);
  assert.match(issues.join('\n'), /ingredient-semantics/);
  assert.match(issues.join('\n'), /err=true/);
});

test('blocks missing-item catalog entries without rejecting image-only diagnostics', () => {
  const missingItemIssues = exportQualityIssues(
    {
      manifest: validManifest,
      failures: ['item mod.CustomIngredient #17: java.lang.IllegalStateException: missing item'],
      semanticErrorRecipes: 0,
    },
    MEATBALLCRAFT_112_PROFILE,
  );
  assert.match(missingItemIssues.join('\n'), /incomplete ingredient-catalog/);

  assert.deepEqual(
    exportQualityIssues(
      {
        manifest: validManifest,
        failures: ['ingredient icon item|minecraft:stone: framebuffer unavailable'],
        semanticErrorRecipes: 0,
      },
      MEATBALLCRAFT_112_PROFILE,
    ),
    [],
  );
});

test('blocks ItemCatalog identity, extraction, and resource-id fallbacks', () => {
  const failures = [
    'ingredient resource id mod.CustomIngredient: java.lang.IllegalStateException; deferring to the unique id',
    'ingredient display name mod.CustomIngredient: java.lang.IllegalStateException; deferring to the unique id',
    'ingredient unique id mod.CustomIngredient: java.lang.IllegalStateException; using a deterministic resource/name identity',
    'ingredient unique id mod.CustomIngredient was null/blank; using logged deterministic fallback jeiexport-fallback:12345678',
    'ingredient resource id custom|jeiexport-fallback:12345678 was empty; using unique id',
  ];
  const issues = exportQualityIssues(
    {manifest: validManifest, failures, semanticErrorRecipes: 0},
    MEATBALLCRAFT_112_PROFILE,
  );

  assert.equal(issues.length, 1);
  assert.match(issues[0], /5 incomplete ingredient-catalog failure/);
  assert.match(issues[0], /ingredient resource id mod\.CustomIngredient/);
});

test('allows an explicitly logged blank cosmetic label to use the exact unique ID', () => {
  assert.deepEqual(
    exportQualityIssues(
      {
        manifest: validManifest,
        failures: [
          'ingredient display name item|tombstone:grave_plate was null/blank; using unique id',
          'ingredient display name item|example:format_only was null/blank after formatting-code removal; using unique id',
        ],
        semanticErrorRecipes: 0,
      },
      MEATBALLCRAFT_112_PROFILE,
    ),
    [],
  );
});

test('allows icon and mod-namespace fallbacks that do not replace catalog identity', () => {
  assert.deepEqual(
    exportQualityIssues(
      {
        manifest: validManifest,
        failures: [
          'ingredient icon custom|stable-id: framebuffer unavailable',
          'ingredient icon item|example:invisible: rendered image is fully transparent; omitting the PNG and JSON icon reference so the viewer uses its named fallback',
          'ingredient mod id custom|stable-id: helper failed; deriving namespace',
        ],
        semanticErrorRecipes: 0,
      },
      MEATBALLCRAFT_112_PROFILE,
    ),
    [],
  );
});

test('allows progress-count diagnostics when complete ingredient listing still succeeds', () => {
  assert.deepEqual(
    exportQualityIssues(
      {
        manifest: validManifest,
        failures: ['count ingredients for mod.CustomIngredient: approximate size unavailable'],
        semanticErrorRecipes: 0,
      },
      MEATBALLCRAFT_112_PROFILE,
    ),
    [],
  );
});

test('allows classified zero semantics but blocks every unclassified zero context', () => {
  const classified = [
    'ZERO_PREREQUISITE recipe input EIOTank #64767 type net.minecraftforge.fluids.FluidStack publishedAmount=20; XP Juice reservoir requirement',
    'ZERO_THRESHOLD recipe input modularmachinery.recipes.berserker_forge #0 type example.DemonWill publishedAmount=1; matching Will threshold',
    'ZERO_UNKNOWN_FLOW recipe input hatchery.generator.recipe #0 type net.minecraftforge.fluids.FluidStack publishedAmount=0; dynamic runtime flow',
    'ZERO_ABSENT_OUTPUT recipe output thermalexpansion.centrifuge_mobs #35 type net.minecraftforge.fluids.FluidStack publishedAmount=none; no XP fluid yield',
    'ZERO_INVALID_RECIPE recipe input EIOWC #523 type example.EnergyIngredient publishedAmount=none; invalid no-op row excluded',
  ];
  assert.deepEqual(
    exportQualityIssues(
      {manifest: validManifest, failures: classified, semanticErrorRecipes: 0},
      MEATBALLCRAFT_112_PROFILE,
    ),
    [],
  );

  const issues = exportQualityIssues(
    {
      manifest: validManifest,
      failures: [
        'recipe input ingredient newmod.machine #0 type newmod.Zero: java.lang.IllegalArgumentException: ZERO_UNCLASSIFIED no exact semantic adapter exists',
      ],
      semanticErrorRecipes: 0,
    },
    MEATBALLCRAFT_112_PROFILE,
  );
  assert.match(issues.join('\n'), /ingredient-semantics/);
});

test('rejects unknown profiles instead of silently using generic validation', () => {
  assert.throws(() => resolveQualityProfile('unknown-profile'), /Unknown export quality profile/);
});
