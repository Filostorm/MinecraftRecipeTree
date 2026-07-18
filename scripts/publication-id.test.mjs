import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {
  computePublicationId,
  PUBLICATION_ID_PATTERN,
  writePublicationId,
} from './publication-id.mjs';

async function writeJson(path, value) {
  await writeFile(path, `${JSON.stringify(value)}\n`);
}

test('publication IDs are stable and change with asset or JSON content', async () => {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-publication-test-'));
  try {
    await mkdir(join(root, 'assets'));
    await writeJson(join(root, 'manifest.json'), {
      format: 1,
      generatedAt: '2026-07-18T12:00:00Z',
      durationMs: 1,
      counts: {items: 1, recipes: 0, categories: 0, mobs: 0},
    });
    await writeJson(join(root, 'items.json'), {items: [{k: 'minecraft:stone'}]});
    await writeFile(join(root, 'assets', 'pack-000.bin'), Buffer.from([1, 2, 3, 4]));

    const initial = await computePublicationId(root);
    assert.match(initial, PUBLICATION_ID_PATTERN);
    assert.equal(await computePublicationId(root), initial);

    assert.equal(await writePublicationId(root), initial);
    const publishedManifest = JSON.parse(await readFile(join(root, 'manifest.json'), 'utf8'));
    assert.equal(publishedManifest.publicationId, initial);
    assert.equal(await computePublicationId(root), initial);

    await writeFile(join(root, 'assets', 'pack-000.bin'), Buffer.from([1, 2, 3, 5]));
    const assetChanged = await computePublicationId(root);
    assert.notEqual(assetChanged, initial);

    await writeFile(join(root, 'assets', 'pack-000.bin'), Buffer.from([1, 2, 3, 4]));
    await writeJson(join(root, 'items.json'), {items: [{k: 'minecraft:dirt'}]});
    const jsonChanged = await computePublicationId(root);
    assert.notEqual(jsonChanged, initial);
    assert.notEqual(jsonChanged, assetChanged);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
