import assert from 'node:assert/strict';
import {mkdtemp, readFile, rm, stat, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {
  MAX_PACK_BYTES,
  packFileKey,
  packedImagePath,
  parsePackedImagePath,
} from './packed-assets.mjs';
import {
  MAX_SHARD_BYTES,
  isShardedDocument,
  readArrayDocument,
  readObjectDocument,
  shardArrayDocument,
  shardObjectDocument,
} from './sharded-documents.mjs';

async function withTemporaryRoot(run) {
  const root = await mkdtemp(join(tmpdir(), 'recipe-tree-shards-test-'));
  try {
    await run(root);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
}

test('coordinate image paths round-trip to canonical one-MiB pack locations', () => {
  const path = packedImagePath(7, 1234, 5678);
  assert.equal(path, 'assets/s/007-1234-5678.webp');
  assert.deepEqual(parsePackedImagePath(path), {packNumber: 7, offset: 1234, length: 5678});
  assert.equal(packFileKey(7), 'assets/pack-007.bin');
  assert.equal(MAX_PACK_BYTES, 1024 * 1024);
  assert.equal(parsePackedImagePath('assets/s/007-1234-0.webp'), null);
  assert.equal(parsePackedImagePath('../assets/s/007-1234-5678.webp'), null);
});

test('oversized arrays and objects round-trip through bounded JSON shards', async () => {
  await withTemporaryRoot(async root => {
    const payload = 'x'.repeat(1024);
    const array = Array.from({length: 8400}, (_, id) => ({id, payload}));
    const arrayDescriptor = await shardArrayDocument(
      array,
      root,
      'data/items',
      'large item catalog',
    );
    assert.equal(isShardedDocument(arrayDescriptor, 'array'), true);
    assert.ok(arrayDescriptor.parts.length > 1);
    for (const part of arrayDescriptor.parts) {
      assert.ok((await stat(join(root, ...part.path.split('/')))).size <= MAX_SHARD_BYTES);
    }
    const resolvedArray = await readArrayDocument(root, arrayDescriptor, 'large item catalog');
    assert.equal(resolvedArray.value.length, array.length);
    assert.deepEqual(resolvedArray.value[0], array[0]);
    assert.deepEqual(resolvedArray.value.at(-1), array.at(-1));

    const object = Object.fromEntries(
      Array.from({length: 8400}, (_, id) => [`item:${id}`, {id, payload}]),
    );
    const objectDescriptor = await shardObjectDocument(
      object,
      root,
      'data/index',
      'large reverse index',
    );
    assert.equal(isShardedDocument(objectDescriptor, 'object'), true);
    assert.ok(objectDescriptor.parts.length > 1);
    const resolvedObject = await readObjectDocument(root, objectDescriptor, 'large reverse index');
    assert.equal(Object.keys(resolvedObject.value).length, Object.keys(object).length);
    assert.deepEqual(resolvedObject.value['item:0'], object['item:0']);
    assert.deepEqual(resolvedObject.value['item:8399'], object['item:8399']);
  });
});

test('shard readers fail closed when a part changes after descriptor generation', async () => {
  await withTemporaryRoot(async root => {
    const values = Array.from({length: 8400}, (_, id) => ({id, payload: 'x'.repeat(1024)}));
    const descriptor = await shardArrayDocument(values, root, 'data/items', 'item catalog');
    assert.equal(isShardedDocument(descriptor, 'array'), true);
    const firstPath = join(root, ...descriptor.parts[0].path.split('/'));
    const original = await readFile(firstPath);
    await writeFile(firstPath, Buffer.concat([original, Buffer.from(' ')]));
    await assert.rejects(
      readArrayDocument(root, descriptor, 'item catalog'),
      /declares \d+ bytes but contains \d+/,
    );
  });
});

test('object readers reject malformed descriptor-shaped objects instead of treating them as data', async () => {
  await withTemporaryRoot(async root => {
    await assert.rejects(
      readObjectDocument(
        root,
        {format: 'mrt-sharded-json-v0', kind: 'object', count: 0, parts: []},
        'reverse index',
      ),
      /is not a mrt-sharded-json-v1 object descriptor/,
    );
  });
});
