import assert from 'node:assert/strict';
import test from 'node:test';
import {
  isExportManifestPath,
  requireLocalPackManifest,
  requireSafeArchivePath,
} from './localPackArchive.ts';

function manifest(overrides = {}) {
  return {
    format: 1,
    generatedAt: '2026-07-31T12:34:56.000Z',
    aborted: false,
    minecraft: '1.21.1',
    pack: {
      name: 'Architect’s Exodus',
      version: '2.4.0',
      identitySource: 'curseforge',
    },
    counts: {
      items: 12_345,
      recipes: 67_890,
      categories: 213,
      mobs: 42,
      blockDrops: 500,
      failures: 0,
    },
    diagnostics: {
      warningEvents: 0,
    },
    ...overrides,
  };
}

test('summarizes a complete exporter manifest as ready for handoff', () => {
  const summary = requireLocalPackManifest(manifest());
  assert.equal(summary.packName, 'Architect’s Exodus');
  assert.equal(summary.packVersion, '2.4.0');
  assert.equal(summary.minecraftVersion, '1.21.1');
  assert.equal(summary.counts.recipes, 67_890);
  assert.equal(summary.readyForHandoff, true);
  assert.deepEqual(summary.findings, []);
  assert.equal(Object.isFrozen(summary), true);
});

test('keeps structurally valid incomplete exports visible with actionable findings', () => {
  const summary = requireLocalPackManifest(manifest({
    aborted: true,
    qualitySample: {enabled: true},
    pack: {
      name: 'Test Pack',
      identitySource: 'game-directory',
    },
    counts: {
      items: 1,
      recipes: 2,
      categories: 3,
      mobs: 0,
      blockDrops: 0,
      failures: 4,
    },
    diagnostics: {warningEvents: 2},
  }));
  assert.equal(summary.readyForHandoff, false);
  assert.equal(summary.findings.length, 6);
  assert.match(summary.findings.join('\n'), /aborted/);
  assert.match(summary.findings.join('\n'), /quality sample/);
  assert.match(summary.findings.join('\n'), /4 failures/);
});

test('rejects malformed manifest metadata instead of inventing fallback values', () => {
  assert.throws(
    () => requireLocalPackManifest(manifest({minecraft: '../1.21.1'})),
    /canonical Minecraft version/,
  );
  assert.throws(
    () => requireLocalPackManifest(manifest({
      counts: {
        items: 1,
        recipes: -1,
        categories: 1,
        mobs: 0,
        blockDrops: 0,
        failures: 0,
      },
    })),
    /counts\.recipes must be a non-negative safe integer/,
  );
  assert.throws(
    () => requireLocalPackManifest(manifest({
      pack: {name: 'Unsafe\u202epack', version: '1', identitySource: 'curseforge'},
    })),
    /pack\.name must be trimmed/,
  );
});

test('accepts only a root or one-folder exporter manifest and rejects unsafe ZIP paths', () => {
  assert.equal(isExportManifestPath('manifest.json'), true);
  assert.equal(isExportManifestPath('jei-export/manifest.json'), true);
  assert.equal(isExportManifestPath('outer/jei-export/manifest.json'), false);
  assert.equal(isExportManifestPath('MANIFEST.JSON'), false);
  assert.equal(requireSafeArchivePath('jei-export/'), 'jei-export/');
  assert.throws(() => requireSafeArchivePath('../manifest.json'), /unsafe file path/);
  assert.throws(() => requireSafeArchivePath('folder\\manifest.json'), /unsafe file path/);
  assert.throws(() => requireSafeArchivePath('/manifest.json'), /unsafe file path/);
});
