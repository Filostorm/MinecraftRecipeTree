import assert from 'node:assert/strict';
import test from 'node:test';
import {recipePreviewContractForProfile} from './build-recipe-preview-sidecar.mjs';
import {
  GENERIC_JEI_120_PROFILE,
  GENERIC_JEI_121_PROFILE,
} from './export-quality-policy.mjs';

function manifest(minecraft = '1.20.1') {
  return {
    format: 1,
    generatedAt: '2026-07-19T12:00:00.000Z',
    durationMs: 1234,
    aborted: false,
    pack: {
      name: 'Example Modern Pack',
      version: '4.2.0',
      identitySource: 'explicit-request',
    },
    minecraft,
    settings: {iconScale: 4, recipeScale: 2, mobCanvas: 256},
    counts: {items: 3, recipes: 2, categories: 1, mobs: 0, blockDrops: 0, failures: 0},
    diagnostics: {failureEvents: 0, failureEventsOmitted: 0},
    mods: {minecraft: 'Minecraft'},
  };
}

test('generic JEI sidecar contract preserves pack identity and requires every recipe preview', () => {
  const rawManifest = manifest();
  const contract = recipePreviewContractForProfile(GENERIC_JEI_120_PROFILE, rawManifest);

  assert.deepEqual(contract.pack, rawManifest.pack);
  assert.notEqual(contract.pack, rawManifest.pack);
  assert.deepEqual(contract.recipeImages, {previews: 2, missing: 0});
  assert.equal(contract.settings.iconScale, 4);
  assert.equal(contract.settings.recipeScale, 2);
});

test('generic JEI 1.21.1 sidecar contract preserves pack identity and complete previews', () => {
  const rawManifest = manifest('1.21.1');
  const contract = recipePreviewContractForProfile(GENERIC_JEI_121_PROFILE, rawManifest);

  assert.deepEqual(contract.pack, rawManifest.pack);
  assert.deepEqual(contract.recipeImages, {previews: 2, missing: 0});
  assert.equal(contract.minecraft, '1.21.1');
  assert.equal(contract.settings.iconScale, 4);
  assert.equal(contract.settings.recipeScale, 2);
});

test('generic JEI sidecar contract rejects missing or schema-drifted diagnostics', () => {
  const missingDiagnostics = manifest();
  delete missingDiagnostics.diagnostics;
  assert.throws(
    () => recipePreviewContractForProfile(GENERIC_JEI_120_PROFILE, missingDiagnostics),
    /manifest\.diagnostics/,
  );

  const driftedDiagnostics = manifest();
  driftedDiagnostics.diagnostics.unrecognized = 0;
  assert.throws(
    () => recipePreviewContractForProfile(GENERIC_JEI_120_PROFILE, driftedDiagnostics),
    /manifest\.diagnostics must contain exactly/,
  );
});
