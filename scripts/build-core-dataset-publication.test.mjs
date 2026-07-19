import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, join} from 'node:path';
import test from 'node:test';
import {
  buildCoreDatasetPublication,
  validateLocalCoreDatasetPublication,
} from './build-core-dataset-publication.mjs';
import {
  coreDatasetPublicationManifestBytes,
  requireCanonicalCoreDatasetPublicationBytes,
} from './core-dataset-publication-contract.mjs';
import {parsePackedImageAuthorizationIndex} from './packed-image-authorization.mjs';
import {packedImagePath} from './packed-assets.mjs';
import {writePublicationId} from './publication-id.mjs';

function fakeWebp(label) {
  const payload = Buffer.from(`VP8L${label.padEnd(8, '.')}`, 'ascii');
  const bytes = Buffer.alloc(12 + payload.length);
  bytes.write('RIFF', 0, 'ascii');
  bytes.writeUInt32LE(bytes.length - 8, 4);
  bytes.write('WEBP', 8, 'ascii');
  payload.copy(bytes, 12);
  return bytes;
}

async function writeJson(path, value) {
  await mkdir(dirname(path), {recursive: true});
  await writeFile(path, `${JSON.stringify(value)}\n`);
}

async function fixture(parent) {
  const exportRoot = join(parent, 'exports');
  await mkdir(join(exportRoot, 'assets'), {recursive: true});
  const first = fakeWebp('first');
  const second = fakeWebp('second');
  const third = fakeWebp('third');
  await writeFile(join(exportRoot, 'assets', 'pack-000.bin'), Buffer.concat([first, second]));
  await writeFile(join(exportRoot, 'assets', 'pack-001.bin'), third);
  const coordinates = {
    first: packedImagePath(0, 0, first.length),
    second: packedImagePath(0, first.length, second.length),
    third: packedImagePath(1, 0, third.length),
  };
  await writeJson(join(exportRoot, 'manifest.json'), {
    format: 1,
    generatedAt: '2026-07-19T00:00:00.000Z',
    aborted: false,
  });
  await writeJson(join(exportRoot, 'items.json'), {
    items: [
      {k: 'item|fixture:first', icon: coordinates.first},
      {k: 'item|fixture:second', icon: coordinates.second},
    ],
  });
  await writeJson(join(exportRoot, 'categories.json'), {
    categories: [{id: 'fixture', icon: coordinates.third}],
  });
  await writeJson(join(exportRoot, 'recipes', 'fixture', 'recipes.json'), [
    {id: 'fixture:duplicate', output: coordinates.first},
  ]);
  const publicationId = await writePublicationId(exportRoot);
  return {exportRoot, publicationId, coordinates, payloads: {first, second, third}};
}

function silentLogger() {
  return {info() {}, warn() {}, error() {}};
}

test('builder emits canonical inventory-complete publication.json and exact MRPI indexes', async () => {
  const root = await mkdtemp(join(tmpdir(), 'core-publication-builder-test-'));
  try {
    const data = await fixture(root);
    const output = join(root, 'publication');
    const state = await buildCoreDatasetPublication({
      exportRoot: data.exportRoot,
      output,
      concurrency: 2,
      logger: silentLogger(),
    });
    assert.equal(state.reused, false);
    assert.equal(state.publicationId, data.publicationId);
    assert.deepEqual(state.manifest.counts, {
      documents: 4,
      packs: 2,
      packedImages: 3,
      documentBytes: state.manifest.documents.reduce((sum, record) => sum + record.bytes, 0),
      packBytes: data.payloads.first.length + data.payloads.second.length + data.payloads.third.length,
      packIndexBytes: 20 + 2 * 8 + 20 + 1 * 8,
      objects: 8,
      storedBytes:
        state.manifest.documents.reduce((sum, record) => sum + record.bytes, 0) +
        data.payloads.first.length +
        data.payloads.second.length +
        data.payloads.third.length +
        (20 + 2 * 8 + 20 + 1 * 8),
    });
    assert.deepEqual(
      state.manifest.documents.map(record => record.path),
      ['categories.json', 'items.json', 'manifest.json', 'recipes/fixture/recipes.json'],
    );
    const manifestBytes = await readFile(join(output, 'publication.json'));
    assert.deepEqual(manifestBytes, coreDatasetPublicationManifestBytes(state.manifest));
    assert.equal(
      requireCanonicalCoreDatasetPublicationBytes(manifestBytes, data.publicationId).publicationId,
      data.publicationId,
    );
    const firstIndex = parsePackedImageAuthorizationIndex(
      await readFile(join(output, 'indexes', 'pack-000.bin')),
      {
        expectedPackNumber: 0,
        expectedPackBytes: data.payloads.first.length + data.payloads.second.length,
      },
    );
    assert.deepEqual(firstIndex.entries, [
      [0, data.payloads.first.length],
      [data.payloads.first.length, data.payloads.second.length],
    ]);
    const verified = await validateLocalCoreDatasetPublication({
      exportRoot: data.exportRoot,
      publication: join(output, 'publication.json'),
      concurrency: 2,
      logger: silentLogger(),
    });
    assert.equal(verified.records.length, 8);
    assert.ok(verified.records.every(record => typeof record.localPath === 'string'));

    const reused = await buildCoreDatasetPublication({
      exportRoot: data.exportRoot,
      output,
      concurrency: 2,
      logger: silentLogger(),
    });
    assert.equal(reused.reused, true);

    const secondOutput = join(root, 'publication-2');
    const second = await buildCoreDatasetPublication({
      exportRoot: data.exportRoot,
      output: secondOutput,
      concurrency: 1,
      logger: silentLogger(),
    });
    assert.deepEqual(second.manifestBytes, state.manifestBytes);
    assert.deepEqual(
      await readFile(join(secondOutput, 'indexes', 'pack-000.bin')),
      await readFile(join(output, 'indexes', 'pack-000.bin')),
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder rejects unindexed pack bytes instead of authorizing a broad range', async () => {
  const root = await mkdtemp(join(tmpdir(), 'core-publication-gap-test-'));
  try {
    const data = await fixture(root);
    const pack = join(data.exportRoot, 'assets', 'pack-001.bin');
    await writeFile(pack, Buffer.concat([await readFile(pack), Buffer.from('unindexed')]));
    await writePublicationId(data.exportRoot);
    await assert.rejects(
      buildCoreDatasetPublication({
        exportRoot: data.exportRoot,
        output: join(root, 'publication'),
        logger: silentLogger(),
      }),
      /authorized ranges cover .* bytes/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder rejects malformed packed coordinates, false publication IDs, and unsupported files', async () => {
  const root = await mkdtemp(join(tmpdir(), 'core-publication-fail-closed-test-'));
  try {
    const malformedParent = join(root, 'malformed');
    const malformed = await fixture(malformedParent);
    await writeJson(join(malformed.exportRoot, 'bad.json'), {icon: 'assets/s/0-0-20.webp'});
    await writePublicationId(malformed.exportRoot);
    await assert.rejects(
      buildCoreDatasetPublication({
        exportRoot: malformed.exportRoot,
        output: join(malformedParent, 'publication'),
        logger: silentLogger(),
      }),
      /non-canonical packed-image coordinate/,
    );

    const falseParent = join(root, 'false');
    const falseId = await fixture(falseParent);
    const manifestPath = join(falseId.exportRoot, 'manifest.json');
    const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
    await writeJson(manifestPath, {...manifest, publicationId: 'f'.repeat(64)});
    await assert.rejects(
      buildCoreDatasetPublication({
        exportRoot: falseId.exportRoot,
        output: join(falseParent, 'publication'),
        logger: silentLogger(),
      }),
      /does not match canonical export content hash/,
    );

    const extraParent = join(root, 'extra');
    const extra = await fixture(extraParent);
    await writeFile(join(extra.exportRoot, 'unexpected.txt'), 'not publishable\n');
    await writePublicationId(extra.exportRoot);
    await assert.rejects(
      buildCoreDatasetPublication({
        exportRoot: extra.exportRoot,
        output: join(extraParent, 'publication'),
        logger: silentLogger(),
      }),
      /refuses unsupported source object unexpected\.txt/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('builder isolates output and detects source mutation before atomic commit', async () => {
  const root = await mkdtemp(join(tmpdir(), 'core-publication-transaction-test-'));
  try {
    const inside = await fixture(join(root, 'inside'));
    await assert.rejects(
      buildCoreDatasetPublication({
        exportRoot: inside.exportRoot,
        output: join(inside.exportRoot, 'publication'),
        logger: silentLogger(),
      }),
      /output must be outside the source export root/,
    );

    const mutationParent = join(root, 'mutation');
    const mutation = await fixture(mutationParent);
    const output = join(mutationParent, 'publication');
    await assert.rejects(
      buildCoreDatasetPublication({
        exportRoot: mutation.exportRoot,
        output,
        logger: silentLogger(),
        async beforeCommit() {
          const items = join(mutation.exportRoot, 'items.json');
          await writeFile(items, Buffer.concat([await readFile(items), Buffer.from(' ')]));
        },
      }),
      /changed before publication commit|Source object changed after analysis/,
    );
    await assert.rejects(readFile(join(output, 'publication.json')), {code: 'ENOENT'});
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('local validator rejects a modified MRPI bundle explicitly', async () => {
  const root = await mkdtemp(join(tmpdir(), 'core-publication-tamper-test-'));
  try {
    const data = await fixture(root);
    const output = join(root, 'publication');
    await buildCoreDatasetPublication({
      exportRoot: data.exportRoot,
      output,
      logger: silentLogger(),
    });
    const indexPath = join(output, 'indexes', 'pack-000.bin');
    const bytes = await readFile(indexPath);
    bytes[bytes.length - 1] ^= 1;
    await writeFile(indexPath, bytes);
    await assert.rejects(
      validateLocalCoreDatasetPublication({
        exportRoot: data.exportRoot,
        publication: join(output, 'publication.json'),
        logger: silentLogger(),
      }),
      /does not match the derived MRPI index/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
