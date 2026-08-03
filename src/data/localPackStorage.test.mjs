import assert from 'node:assert/strict';
import {File} from 'node:buffer';
import test from 'node:test';
import {strToU8, zipSync} from 'fflate';

const {installLocalPackArchive} = await import('./localPackStorage.ts');

test('reports file-saving and finalization after archive reading reaches 100%', async () => {
  const originalWindow = Object.getOwnPropertyDescriptor(globalThis, 'window');
  const originalNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator');
  const originalCaches = Object.getOwnPropertyDescriptor(globalThis, 'caches');
  const responses = new Map();
  const cache = {
    async match(request) {
      return responses.get(request.url)?.clone();
    },
    async put(request, response) {
      await Promise.resolve();
      responses.set(request.url, response.clone());
    },
    async keys() {
      throw new DOMException('Operation too large.', 'QuotaExceededError');
    },
    async delete(request) {
      return responses.delete(request.url);
    },
  };

  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: {location: {origin: 'https://viewer.example'}, clearTimeout, setTimeout},
  });
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: {
      serviceWorker: {
        controller: {},
        ready: Promise.resolve(),
        async register() {},
      },
    },
  });
  Object.defineProperty(globalThis, 'caches', {
    configurable: true,
    value: {async open() { return cache; }},
  });

  try {
    const manifest = {
      format: 'jei-export-v2',
      minecraft: '1.20.1',
      pack: {name: 'Progress Pack', version: '1.0.0'},
    };
    const manifestBytes = strToU8(JSON.stringify(manifest));
    const archive = zipSync({
      'manifest.json': manifestBytes,
      'items.json': strToU8('[]'),
      'categories.json': strToU8('[]'),
      'index.json': strToU8('{}'),
    });
    const file = new File([archive], 'progress-pack.zip', {type: 'application/zip'});
    const updates = [];

    await installLocalPackArchive(
      file,
      'manifest.json',
      manifestBytes,
      manifest,
      {
        packName: 'Progress Pack',
        packVersion: '1.0.0',
        minecraftVersion: '1.20.1',
        readyForHandoff: true,
        findings: [],
        counts: {items: 0, recipes: 0, categories: 0, failures: 0},
      },
      update => updates.push(update),
    );

    assert.deepEqual(updates[0], {phase: 'reading', fraction: 1});
    const saving = updates.filter(update => update.phase === 'saving');
    assert.deepEqual(saving[0], {
      phase: 'saving',
      fraction: 0,
      completedFiles: 0,
      totalFiles: 3,
    });
    assert.deepEqual(saving.at(-1), {
      phase: 'saving',
      fraction: 1,
      completedFiles: 3,
      totalFiles: 3,
    });
    assert.deepEqual(updates.at(-1), {phase: 'finalizing'});

    const firstInventoryUrl = [...responses.keys()].find(url => url.endsWith('/inventory.json'));
    assert.ok(firstInventoryUrl);
    responses.delete(firstInventoryUrl);

    const updatedManifest = {
      ...manifest,
      pack: {name: 'Progress Pack', version: '1.0.1'},
    };
    const updatedManifestBytes = strToU8(JSON.stringify(updatedManifest));
    const updatedArchive = zipSync({
      'manifest.json': updatedManifestBytes,
      'items.json': strToU8('[]'),
      'categories.json': strToU8('[]'),
      'index.json': strToU8('{}'),
    });
    const updatedFile = new File([updatedArchive], 'progress-pack-updated.zip', {
      type: 'application/zip',
    });

    const installed = await installLocalPackArchive(
      updatedFile,
      'manifest.json',
      updatedManifestBytes,
      updatedManifest,
      {
        packName: 'Progress Pack',
        packVersion: '1.0.1',
        minecraftVersion: '1.20.1',
        readyForHandoff: true,
        findings: [],
        counts: {items: 0, recipes: 0, categories: 0, failures: 0},
      },
      () => {},
    );

    assert.equal(installed.descriptor.packVersion, '1.0.1');
    const catalog = await responses
      .get('https://viewer.example/__local-packs/catalog.json')
      ?.clone()
      .json();
    assert.deepEqual(catalog.packs.map(pack => pack.packVersion), ['1.0.1']);
  } finally {
    if (originalWindow) Object.defineProperty(globalThis, 'window', originalWindow);
    else delete globalThis.window;
    if (originalNavigator) Object.defineProperty(globalThis, 'navigator', originalNavigator);
    else delete globalThis.navigator;
    if (originalCaches) Object.defineProperty(globalThis, 'caches', originalCaches);
    else delete globalThis.caches;
  }
});
