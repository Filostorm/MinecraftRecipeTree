import assert from 'node:assert/strict';
import test from 'node:test';
import {
  EXPORT_QUALITY_PROFILE_IDS,
  exportQualityIssues,
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
    MEATBALLCRAFT_112_PROFILE,
    MULTIBLOCK_MADNESS_112_PROFILE,
    MULTIBLOCK_MADNESS_2_118_PROFILE,
  ]);
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
