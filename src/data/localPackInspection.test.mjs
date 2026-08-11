import assert from 'node:assert/strict';
import test from 'node:test';
import {strToU8, zipSync} from 'fflate';
import {inspectLocalPackArchive} from './localPackInspection.ts';

function exporterManifest() {
  return {
    format: 1,
    generatedAt: '2026-08-11T12:00:00.000Z',
    aborted: false,
    minecraft: '1.21.1',
    pack: {name: 'Mobile Test Pack', version: '1.0.0', identitySource: 'game-directory'},
    counts: {
      items: 1,
      recipes: 1,
      categories: 1,
      mobs: 0,
      blockDrops: 0,
      failures: 0,
    },
    diagnostics: {warningEvents: 0},
  };
}

function archiveFile(files) {
  const bytes = zipSync(
    Object.fromEntries(Object.entries(files).map(([path, value]) => [path, strToU8(value)])),
  );
  const blob = new Blob([bytes]);
  return {
    name: 'export.zip',
    size: blob.size,
    slice: (start, end) => blob.slice(start, end),
  };
}

test('inspects a one-folder exporter ZIP for the native local importer', async () => {
  const progress = [];
  const inspected = await inspectLocalPackArchive(
    archiveFile({
      'jei-exports/manifest.json': JSON.stringify(exporterManifest()),
      'jei-exports/items.json': '{"items":[]}',
    }),
    fraction => progress.push(fraction),
  );

  assert.equal(inspected.manifestPath, 'jei-exports/manifest.json');
  assert.equal(inspected.summary.packName, 'Mobile Test Pack');
  assert.equal(inspected.summary.readyForHandoff, true);
  assert.equal(inspected.delta, null);
  assert.equal(progress.at(-1), 1);
});

test('rejects a ZIP without an exporter manifest', async () => {
  await assert.rejects(
    inspectLocalPackArchive(archiveFile({'items.json': '{"items":[]}'}), () => {}),
    /No exporter manifest\.json was found/,
  );
});
