import assert from 'node:assert/strict';
import test from 'node:test';
import {
  isExportManifestPath,
  isIgnoredArchiveMetadataPath,
  requireLocalPackManifest,
  requireSafeArchivePath,
} from './localPackArchive.ts';
import {
  isLocalPackDescriptor,
  localDatasetSource,
} from './localPackStorage.ts';

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
  assert.match(summary.findings.join('\n'), /stopped before it finished/);
  assert.match(summary.findings.join('\n'), /small test/);
  assert.match(summary.findings.join('\n'), /exporter recorded 4 issues/);
});

test('allows a completed export with reported failures to load with a warning', () => {
  const summary = requireLocalPackManifest(manifest({
    counts: {
      items: 10,
      recipes: 8,
      categories: 2,
      mobs: 0,
      blockDrops: 0,
      failures: 3,
    },
  }));
  assert.equal(summary.readyForHandoff, true);
  assert.match(summary.findings.join('\n'), /rest of the pack can still be opened/);
  assert.match(summary.findings.join('\n'), /failure report will be sent automatically/);
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

test('accepts only a root or one-folder exporter manifest and canonicalizes safe ZIP paths', () => {
  assert.equal(isExportManifestPath('manifest.json'), true);
  assert.equal(isExportManifestPath('jei-export/manifest.json'), true);
  assert.equal(isExportManifestPath('outer/jei-export/manifest.json'), false);
  assert.equal(isExportManifestPath('MANIFEST.JSON'), false);
  assert.equal(requireSafeArchivePath('jei-export/'), 'jei-export/');
  assert.equal(requireSafeArchivePath('./'), '');
  assert.equal(requireSafeArchivePath('./jei-export/manifest.json'), 'jei-export/manifest.json');
  assert.equal(requireSafeArchivePath('jei-export/./items.json'), 'jei-export/items.json');
});

test('names the ZIP entry and reason when an archive path is unsafe', () => {
  assert.throws(
    () => requireSafeArchivePath('../manifest.json'),
    /ZIP entry "\.\.\/manifest\.json".*tries to leave the export folder/,
  );
  assert.throws(
    () => requireSafeArchivePath('folder\\manifest.json'),
    /ZIP entry "folder\\\\manifest\.json".*Windows path separator/,
  );
  assert.throws(
    () => requireSafeArchivePath('/manifest.json'),
    /ZIP entry "\/manifest\.json".*absolute path/,
  );
});

test('recognizes Finder metadata without hiding legitimate exporter files', () => {
  assert.equal(isIgnoredArchiveMetadataPath('jei-exports/._items.json'), true);
  assert.equal(isIgnoredArchiveMetadataPath('__MACOSX/jei-exports/items.json'), true);
  assert.equal(isIgnoredArchiveMetadataPath('jei-exports/.DS_Store'), true);
  assert.equal(isIgnoredArchiveMetadataPath('jei-exports/items.json'), false);
  assert.equal(isIgnoredArchiveMetadataPath('jei-exports/images/item.png'), false);
});

test('maps an installed device-local pack to its isolated viewer route', () => {
  const publicationId = 'a'.repeat(64);
  const descriptor = {
    slug: 'local-aaaaaaaaaaaaaaaa',
    displayName: 'Local Test Pack',
    minecraftVersion: '1.21.1',
    packVersion: '1.0.0',
    publicationId,
    previewAssetSetId: publicationId,
    isDefault: false,
  };
  assert.equal(isLocalPackDescriptor(descriptor), true);
  assert.deepEqual(localDatasetSource(descriptor), {
    descriptor,
    base: `/__local-packs/${publicationId}/exports`,
    previewBase: '',
  });
});
