import assert from 'node:assert/strict';
import test from 'node:test';

import {
  EXPORT_ERRORS_FILE_FORMAT,
  EXPORT_FAILURE_REPORT_FORMAT,
  buildExportFailureReport,
  shouldSendExportFailureReport,
} from './exportFailureReport.ts';

const manifest = {
  format: 1,
  generatedAt: '2026-08-02T12:00:00Z',
  minecraft: '1.20.1',
  pack: {name: 'Broken Pack', version: '4.2.0'},
  exporter: {id: 'jeiexport', version: '1.2.0-beta.23'},
};

test('failure reporting requires an explicit opt-in and at least one failure', () => {
  assert.equal(shouldSendExportFailureReport(4, false), false);
  assert.equal(shouldSendExportFailureReport(0, true), false);
  assert.equal(shouldSendExportFailureReport(4, true), true);
});

test('builds a deduplicated structured report with pack and exporter context', () => {
  const detail = {
    scope: 'recipe',
    modId: 'create',
    categoryId: 'create:mixing',
    recipeId: 'create:mixing/brass',
    recipeIndex: 12,
    recipeClass: 'com.example.BrokenRecipe',
    errorType: 'java.lang.IllegalStateException',
    message: 'JEI layout failed',
    details: 'java.lang.IllegalStateException: bad slot',
  };
  const report = buildExportFailureReport({
    manifest,
    failures: ['legacy summary', 'legacy summary'],
    exportErrors: {
      format: EXPORT_ERRORS_FILE_FORMAT,
      exporter: {id: 'jeiexport', version: '1.2.0-beta.23'},
      modVersions: {create: '6.0.8', unrelated: '1.0.0'},
      failures: [detail, detail],
    },
    exporterBuild: {exporterId: 'forge-jei-1.20.1', payloadSha256: 'a'.repeat(64)},
  });
  assert.equal(report.format, EXPORT_FAILURE_REPORT_FORMAT);
  assert.equal(report.packName, 'Broken Pack');
  assert.equal(report.exporterVersion, '1.2.0-beta.23');
  assert.equal(report.failures.length, 1);
  assert.equal(report.failures[0].modId, 'create');
  assert.equal(report.failures[0].recipeId, 'create:mixing/brass');
  assert.equal(report.exporterBuild, 'a'.repeat(64));
  assert.deepEqual(report.modVersions, {create: '6.0.8'});
});

test('derives useful recipe and mod context from legacy string failures', () => {
  const report = buildExportFailureReport({
    manifest,
    failures: [
      'recipe recipes/mekanism_crushing #17: java.lang.IllegalArgumentException: bad input',
    ],
  });
  assert.equal(report.failures[0].scope, 'recipe');
  assert.equal(report.failures[0].modId, 'mekanism');
  assert.equal(report.failures[0].categoryId, 'mekanism:crushing');
  assert.equal(report.failures[0].recipeIndex, 17);
  assert.match(report.failures[0].message, /bad input/);
  assert.deepEqual(report.modVersions, {mekanism: 'Unknown'});
});

test('prepares a shareable report for a named unversioned custom pack', () => {
  const report = buildExportFailureReport({
    manifest: {
      ...manifest,
      pack: {name: 'My Custom Tech Pack', identitySource: 'game-directory'},
    },
    failures: ['recipe recipes/mekanism_crushing #17: failed'],
  });

  assert.equal(report.packName, 'My Custom Tech Pack');
  assert.equal(report.packVersion, 'Unknown');
  assert.equal(report.failures.length, 1);
});

test('returns no report for an empty failure list', () => {
  assert.equal(buildExportFailureReport({manifest, failures: []}), null);
});

test('suppresses expected compatibility fallbacks from older exporters', () => {
  const report = buildExportFailureReport({
    manifest,
    failures: [
      'mob example_mod:invisible_helper rendered fully transparent and was omitted',
      'blockdrops another_mod:machine_casing: no standard candidate tool satisfies requiresCorrectToolForDrops; probing with a netherite pickaxe',
    ],
  });

  assert.equal(report, null);
});

test('retains actionable failures while suppressing compatibility fallbacks', () => {
  const report = buildExportFailureReport({
    manifest,
    failures: [
      'mob example_mod:invisible_helper rendered fully transparent and was omitted',
      'mob drops iceandfire:myrmex_swarmer: null loot table id',
    ],
  });

  assert.equal(report.failures.length, 1);
  assert.match(report.failures[0].message, /myrmex_swarmer/);
});
