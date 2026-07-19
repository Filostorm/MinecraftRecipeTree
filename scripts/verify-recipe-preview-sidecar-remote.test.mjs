import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdir, mkdtemp, rm, writeFile} from 'node:fs/promises';
import {createServer} from 'node:http';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {verifyRemoteRecipePreviewSidecar} from './verify-recipe-preview-sidecar-remote.mjs';

const DATASET_PUBLICATION_ID = 'a'.repeat(64);
const quietLogger = Object.freeze({
  info() {},
  warn() {},
  error() {},
});

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function framedHashUpdate(hash, bytes) {
  const buffer = Buffer.isBuffer(bytes) ? bytes : Buffer.from(bytes, 'utf8');
  const length = Buffer.allocUnsafe(8);
  length.writeBigUInt64BE(BigInt(buffer.length));
  hash.update(length).update(buffer);
}

function assetSetId(records) {
  const hash = createHash('sha256');
  hash.update('mrt-recipe-preview-sidecar-v1\0');
  framedHashUpdate(hash, DATASET_PUBLICATION_ID);
  for (const record of [...records].sort((left, right) =>
    left.path < right.path ? -1 : left.path > right.path ? 1 : 0,
  )) {
    framedHashUpdate(hash, record.path);
    framedHashUpdate(hash, Buffer.from(record.sha256, 'hex'));
  }
  return hash.digest('hex');
}

function jsonBytes(value) {
  return Buffer.from(`${JSON.stringify(value)}\n`, 'utf8');
}

function packIndexBytes(packNumber, packBytes, entries) {
  const bytes = Buffer.alloc(20 + entries.length * 8);
  bytes.write('MRPI', 0, 'ascii');
  bytes.writeUInt16BE(1, 4);
  bytes.writeUInt16BE(20, 6);
  bytes.writeUInt32BE(packNumber, 8);
  bytes.writeUInt32BE(packBytes, 12);
  bytes.writeUInt32BE(entries.length, 16);
  for (const [entryIndex, [offset, length]] of entries.entries()) {
    bytes.writeUInt32BE(offset, 20 + entryIndex * 8);
    bytes.writeUInt32BE(length, 24 + entryIndex * 8);
  }
  return bytes;
}

async function createSidecarFixture(
  root,
  {itemIconPixels = 16, recipeScale = 1} = {},
) {
  const local = join(root, 'sidecar');
  await mkdir(join(local, 'assets'), {recursive: true});
  await mkdir(join(local, 'categories'), {recursive: true});
  const packBytes = Buffer.from(
    Array.from({length: 64}, (_, index) => (index * 37 + 11) % 256),
  );
  const indexBytes = packIndexBytes(0, packBytes.length, [[0, packBytes.length]]);
  const categoryBytes = jsonBytes({
    format: 'mrt-recipe-preview-category-v1',
    categoryIndex: 0,
    categoryId: 'fixture.category',
    count: 2,
    previews: [[0, 0, 64, 8, 8], null],
  });
  const index = {
    path: 'indexes/pack-000.bin',
    bytes: indexBytes.length,
    sha256: sha256(indexBytes),
    entries: 1,
  };
  const pack = {
    path: 'assets/pack-000.bin',
    bytes: packBytes.length,
    sha256: sha256(packBytes),
    index,
  };
  const category = {
    path: 'categories/000.json',
    bytes: categoryBytes.length,
    sha256: sha256(categoryBytes),
  };
  const records = [pack, index, category];
  const manifest = {
    format: 'mrt-recipe-preview-sidecar-v1',
    assetSetId: assetSetId(records),
    datasetPublicationId: DATASET_PUBLICATION_ID,
    maxPackBytes: 1024 * 1024,
    packIndexFormat: 'mrt-recipe-preview-pack-index-v1',
    maxPackIndexBytes: 512 * 1024,
    imageFormat: 'lossless-webp',
    categoryFormat: 'mrt-recipe-preview-category-v1',
    settings: {
      itemIconPixels,
      recipeScale,
      webpEffort: 4,
      maxCategoryBytes: 256 * 1024,
    },
    counts: {
      categories: 1,
      recipes: 2,
      previews: 1,
      missing: 1,
      uniqueImages: 1,
      duplicates: 0,
      packs: 1,
      inputBytes: 64,
      hostedOmittedPngBytes: 64,
      encodedBytes: 64,
      storedBytes: 64,
      packIndexBytes: indexBytes.length,
    },
    packs: [pack],
    mapping: {documents: 1, parts: 0, bytes: categoryBytes.length},
    categoryDocuments: [category],
  };
  const manifestBytes = jsonBytes(manifest);
  await Promise.all([
    writeFile(join(local, 'assets', 'pack-000.bin'), packBytes),
    mkdir(join(local, 'indexes'), {recursive: true}),
  ]);
  await Promise.all([
    writeFile(join(local, 'indexes', 'pack-000.bin'), indexBytes),
    writeFile(join(local, 'categories', '000.json'), categoryBytes),
    writeFile(join(local, 'manifest.json'), manifestBytes),
  ]);
  return {local, manifest, manifestBytes, packBytes, indexBytes, categoryBytes};
}

async function startBucketServer(fixture, options = {}) {
  const requests = [];
  const prefix = `/bucket/${fixture.manifest.assetSetId}/`;
  const objects = new Map([
    [`${prefix}manifest.json`, fixture.manifestBytes],
    [`${prefix}assets/pack-000.bin`, fixture.packBytes],
    [`${prefix}indexes/pack-000.bin`, fixture.indexBytes],
    [`${prefix}categories/000.json`, fixture.categoryBytes],
  ]);
  const server = createServer((request, response) => {
    const url = new URL(request.url, 'http://fixture.test');
    requests.push({method: request.method, path: url.pathname, range: request.headers.range});
    const isManifest = url.pathname === `${prefix}manifest.json`;
    if (isManifest && options.manifestPresent === false) {
      response.writeHead(404, {'content-length': '0'});
      response.end();
      return;
    }
    let bytes = objects.get(url.pathname);
    if (!bytes) {
      response.writeHead(404, {'content-length': '0'});
      response.end();
      return;
    }
    if (url.pathname.endsWith('/categories/000.json') && options.corruptCategory) {
      bytes = Buffer.from(bytes);
      bytes[0] ^= 1;
    }
    if (url.pathname.endsWith('/assets/pack-000.bin') && options.corruptPack) {
      bytes = Buffer.from(bytes);
      bytes[45] ^= 1;
    }
    if (url.pathname.endsWith('/indexes/pack-000.bin') && options.corruptIndex) {
      bytes = Buffer.from(bytes);
      bytes[20] ^= 1;
    }
    if (request.method === 'HEAD') {
      response.writeHead(200, {'content-length': String(bytes.length)});
      response.end();
      return;
    }
    if (request.method !== 'GET') {
      response.writeHead(405, {'content-length': '0'});
      response.end();
      return;
    }
    if (request.headers.range && url.pathname.endsWith('/assets/pack-000.bin')) {
      if (options.ignoreRange) {
        response.writeHead(200, {'content-length': String(bytes.length)});
        response.end(bytes);
        return;
      }
      const match = /^bytes=(\d+)-(\d+)$/.exec(request.headers.range);
      if (!match) {
        response.writeHead(416, {'content-length': '0'});
        response.end();
        return;
      }
      const start = Number(match[1]);
      const end = Number(match[2]);
      const body = bytes.subarray(start, end + 1);
      response.writeHead(206, {
        'content-length': String(body.length),
        'content-range': `bytes ${start}-${end}/${bytes.length}`,
      });
      response.end(body);
      return;
    }
    response.writeHead(200, {'content-length': String(bytes.length)});
    response.end(bytes);
  });
  await new Promise((resolveListen, rejectListen) => {
    server.once('error', rejectListen);
    server.listen(0, '127.0.0.1', resolveListen);
  });
  const address = server.address();
  return {
    baseUrl: `http://127.0.0.1:${address.port}/bucket`,
    requests,
    close: () => new Promise((resolveClose, rejectClose) => {
      server.close(error => (error ? rejectClose(error) : resolveClose()));
      server.closeAllConnections();
    }),
  };
}

async function withFixture(operation, settings) {
  const root = await mkdtemp(join(tmpdir(), 'remote-preview-verifier-test-'));
  try {
    await operation(await createSidecarFixture(root, settings), root);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
}

async function verify(fixture, bucket, mode) {
  return verifyRemoteRecipePreviewSidecar({
    local: fixture.local,
    baseUrl: bucket.baseUrl,
    mode,
    concurrency: 3,
    timeoutMs: 5_000,
    logger: quietLogger,
    allowHttpForTests: true,
  });
}

test('precommit verifies immutable objects and exact non-whole ranges while manifest is absent', async () => {
  await withFixture(async fixture => {
    const bucket = await startBucketServer(fixture, {manifestPresent: false});
    try {
      const result = await verify(fixture, bucket, 'precommit');
      assert.deepEqual(result, {
        mode: 'precommit',
        assetSetId: fixture.manifest.assetSetId,
        datasetPublicationId: DATASET_PUBLICATION_ID,
        packs: 1,
        packIndexes: 1,
        categoryDocuments: 1,
        fullyHashedPacks: 1,
        rangeSamples: 3,
      });
      assert.ok(
        bucket.requests.some(request =>
          request.method === 'HEAD' && request.path.endsWith('/manifest.json'),
        ),
      );
      assert.ok(
        bucket.requests.some(request =>
          request.method === 'GET' && request.path.endsWith('/categories/000.json'),
        ),
      );
      assert.ok(
        bucket.requests.some(request =>
          request.method === 'GET' && request.path.endsWith('/indexes/pack-000.bin'),
        ),
      );
      const fullPackGets = bucket.requests.filter(request =>
        request.method === 'GET' &&
        request.path.endsWith('/assets/pack-000.bin') &&
        request.range === undefined,
      );
      assert.equal(fullPackGets.length, 1, 'precommit must hash every complete pack exactly once');
      const rangeGets = bucket.requests.filter(request =>
        request.method === 'GET' &&
        request.path.endsWith('/assets/pack-000.bin') &&
        request.range !== undefined,
      );
      assert.equal(rangeGets.length, 3);
      assert.ok(rangeGets.every(request => /^bytes=\d+-\d+$/.test(request.range)));
      assert.ok(rangeGets.every(request => request.range !== 'bytes=0-63'));
    } finally {
      await bucket.close();
    }
  });
});

test('verifier accepts grid-aligned high-resolution render settings', async () => {
  await withFixture(async fixture => {
    const bucket = await startBucketServer(fixture, {manifestPresent: false});
    try {
      const result = await verify(fixture, bucket, 'precommit');
      assert.equal(result.assetSetId, fixture.manifest.assetSetId);
      assert.deepEqual(
        {
          itemIconPixels: fixture.manifest.settings.itemIconPixels,
          recipeScale: fixture.manifest.settings.recipeScale,
        },
        {itemIconPixels: 48, recipeScale: 2},
      );
    } finally {
      await bucket.close();
    }
  }, {itemIconPixels: 48, recipeScale: 2});
});

test('committed mode requires an exact remote manifest and repeats object verification', async () => {
  await withFixture(async fixture => {
    const bucket = await startBucketServer(fixture, {manifestPresent: true});
    try {
      const result = await verify(fixture, bucket, 'committed');
      assert.equal(result.mode, 'committed');
      assert.equal(result.fullyHashedPacks, 0);
      assert.ok(
        bucket.requests.some(request =>
          request.method === 'GET' && request.path.endsWith('/manifest.json'),
        ),
      );
      assert.ok(
        bucket.requests.some(request =>
          request.method === 'HEAD' && request.path.endsWith('/assets/pack-000.bin'),
        ),
      );
      assert.equal(
        bucket.requests.filter(request =>
          request.method === 'GET' &&
          request.path.endsWith('/assets/pack-000.bin') &&
          request.range === undefined,
        ).length,
        0,
        'committed verification must not repeat the full precommit corpus download',
      );
    } finally {
      await bucket.close();
    }
  });
});

test('precommit refuses an already-published manifest commit marker', async () => {
  await withFixture(async fixture => {
    const bucket = await startBucketServer(fixture, {manifestPresent: true});
    try {
      await assert.rejects(
        verify(fixture, bucket, 'precommit'),
        /requires remote manifest\.json to be absent.*HTTP 200/,
      );
    } finally {
      await bucket.close();
    }
  });
});

test('range verification refuses an origin that returns a whole pack with HTTP 200', async () => {
  await withFixture(async fixture => {
    const bucket = await startBucketServer(fixture, {manifestPresent: false, ignoreRange: true});
    try {
      await assert.rejects(verify(fixture, bucket, 'precommit'), /requires HTTP 206; received HTTP 200/);
    } finally {
      await bucket.close();
    }
  });
});

test('precommit streams and hashes every pack byte, including bytes outside its range probes', async () => {
  await withFixture(async fixture => {
    const bucket = await startBucketServer(fixture, {manifestPresent: false, corruptPack: true});
    try {
      await assert.rejects(
        verify(fixture, bucket, 'precommit'),
        /Full digest assets\/pack-000\.bin SHA-256.*manifest declares/,
      );
      assert.ok(
        bucket.requests.some(request =>
          request.method === 'GET' &&
          request.path.endsWith('/assets/pack-000.bin') &&
          request.range === undefined,
        ),
      );
    } finally {
      await bucket.close();
    }
  });
});

test('remote category documents are fully downloaded and digest checked', async () => {
  await withFixture(async fixture => {
    const bucket = await startBucketServer(fixture, {manifestPresent: false, corruptCategory: true});
    try {
      await assert.rejects(verify(fixture, bucket, 'precommit'), /exact remote SHA-256\/byte comparison/);
    } finally {
      await bucket.close();
    }
  });
});

test('remote pack authorization indexes are fully downloaded and digest checked', async () => {
  await withFixture(async fixture => {
    const bucket = await startBucketServer(fixture, {manifestPresent: false, corruptIndex: true});
    try {
      await assert.rejects(
        verify(fixture, bucket, 'precommit'),
        /indexes\/pack-000\.bin failed its exact remote SHA-256\/byte comparison/,
      );
      assert.equal(
        bucket.requests.some(request =>
          request.method === 'GET' && request.path.endsWith('/assets/pack-000.bin'),
        ),
        false,
        'small-object validation must reject the corrupt index before full pack downloads',
      );
    } finally {
      await bucket.close();
    }
  });
});

test('local digest validation and credential-free HTTPS are fail-closed', async () => {
  await withFixture(async fixture => {
    await writeFile(join(fixture.local, 'assets', 'pack-000.bin'), Buffer.alloc(64, 7));
    await assert.rejects(
      verifyRemoteRecipePreviewSidecar({
        local: fixture.local,
        baseUrl: 'https://example.test/public/previews',
        mode: 'precommit',
        logger: quietLogger,
      }),
      /failed its local SHA-256 digest/,
    );
    await assert.rejects(
      verifyRemoteRecipePreviewSidecar({
        local: fixture.local,
        baseUrl: 'http://example.test/public/previews',
        mode: 'precommit',
        logger: quietLogger,
      }),
      /must use credential-free HTTPS/,
    );
    await assert.rejects(
      verifyRemoteRecipePreviewSidecar({
        local: fixture.local,
        baseUrl: 'https://user:secret@example.test/public/previews',
        mode: 'precommit',
        logger: quietLogger,
      }),
      /must not contain credentials/,
    );
  });
});

test('committed mode detects a byte-different manifest even when the length matches', async () => {
  await withFixture(async fixture => {
    const bucket = await startBucketServer(fixture, {manifestPresent: true});
    // The server retained this Buffer by reference, while the local manifest is a separate file.
    fixture.manifestBytes[fixture.manifestBytes.length - 2] ^= 1;
    try {
      await assert.rejects(verify(fixture, bucket, 'committed'), /not byte-for-byte identical/);
    } finally {
      await bucket.close();
    }
  });
});
