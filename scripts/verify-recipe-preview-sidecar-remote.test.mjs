import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdir, mkdtemp, rm, writeFile} from 'node:fs/promises';
import {createServer} from 'node:http';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {verifyRemoteRecipePreviewSidecar} from './verify-recipe-preview-sidecar-remote.mjs';

const DATASET_PUBLICATION_ID = 'a'.repeat(64);
const PUBLIC_BASE_PATH = '/dataset/preview-sets';

function quietLogger() {
  return {info() {}, warn() {}, error() {}};
}

function recordingLogger() {
  const messages = {info: [], warn: [], error: []};
  return {
    messages,
    info(message) { messages.info.push(message); },
    warn(message) { messages.warn.push(message); },
    error(message) { messages.error.push(message); },
  };
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function framedHashUpdate(hash, bytes) {
  const buffer = Buffer.isBuffer(bytes) ? bytes : Buffer.from(bytes, 'utf8');
  const length = Buffer.allocUnsafe(8);
  length.writeBigUInt64BE(BigInt(buffer.length));
  hash.update(length).update(buffer);
}

function assetSetId(records, format = 'mrt-recipe-preview-sidecar-v1') {
  const hash = createHash('sha256');
  hash.update(`${format}\0`);
  framedHashUpdate(hash, DATASET_PUBLICATION_ID);
  for (const record of [...records].sort((left, right) =>
    left.path < right.path ? -1 : left.path > right.path ? 1 : 0,
  )) {
    framedHashUpdate(hash, record.path);
    framedHashUpdate(hash, Buffer.from(record.sha256, 'hex'));
  }
  return hash.digest('hex');
}

async function createDataOnlySidecarFixture(root, overrides = {}) {
  const local = join(root, 'sidecar');
  await mkdir(local, {recursive: true});
  const manifest = {
    format: 'mrt-recipe-preview-sidecar-v2',
    publicationPolicy: 'gtnh-structured-data-only-v1',
    exclusionReason: 'third-party-artwork-rights-not-cleared',
    assetSetId: assetSetId([], 'mrt-recipe-preview-sidecar-v2'),
    datasetPublicationId: DATASET_PUBLICATION_ID,
    maxPackBytes: 1024 * 1024,
    packIndexFormat: 'mrt-recipe-preview-pack-index-v1',
    maxPackIndexBytes: 512 * 1024,
    imageFormat: 'lossless-webp',
    categoryFormat: 'mrt-recipe-preview-category-v1',
    settings: {
      itemIconPixels: 16,
      recipeScale: 2,
      webpEffort: 4,
      maxCategoryBytes: 256 * 1024,
    },
    counts: {
      categories: 42,
      recipes: 1000,
      previews: 0,
      missing: 1000,
      uniqueImages: 0,
      duplicates: 0,
      packs: 0,
      inputBytes: 0,
      hostedOmittedPngBytes: 987654,
      encodedBytes: 0,
      storedBytes: 0,
      packIndexBytes: 0,
    },
    packs: [],
    mapping: {documents: 0, parts: 0, bytes: 0},
    categoryDocuments: [],
    ...overrides,
  };
  const manifestBytes = jsonBytes(manifest);
  await writeFile(join(local, 'manifest.json'), manifestBytes);
  return {
    local,
    manifest,
    manifestBytes,
    packBytes: Buffer.alloc(0),
    indexBytes: Buffer.alloc(0),
    categoryBytes: Buffer.alloc(0),
    entries: [],
  };
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

function publicHeaders(bytes, contentType, {image = false, omitNoTransform = false} = {}) {
  const directives = ['public', 'max-age=31536000', 'immutable'];
  if (image && !omitNoTransform) directives.push('no-transform');
  return {
    'cache-control': directives.join(', '),
    'content-length': String(bytes),
    'content-type': contentType,
  };
}

async function createSidecarFixture(
  root,
  {itemIconPixels = 16, recipeScale = 1} = {},
) {
  const local = join(root, 'sidecar');
  await mkdir(join(local, 'assets'), {recursive: true});
  await mkdir(join(local, 'categories'), {recursive: true});
  await mkdir(join(local, 'indexes'), {recursive: true});
  const packBytes = Buffer.from(
    Array.from({length: 64}, (_, index) => (index * 37 + 11) % 256),
  );
  const entries = [[0, 17], [17, 23], [40, 24]];
  const indexBytes = packIndexBytes(0, packBytes.length, entries);
  const categoryBytes = jsonBytes({
    format: 'mrt-recipe-preview-category-v1',
    categoryIndex: 0,
    categoryId: 'fixture.category',
    count: 4,
    previews: [
      [0, 0, 17, 8, 8],
      [0, 17, 23, 9, 7],
      [0, 40, 24, 10, 6],
      null,
    ],
  });
  const index = {
    path: 'indexes/pack-000.bin',
    bytes: indexBytes.length,
    sha256: sha256(indexBytes),
    entries: entries.length,
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
      recipes: 4,
      previews: 3,
      missing: 1,
      uniqueImages: 3,
      duplicates: 0,
      packs: 1,
      inputBytes: packBytes.length,
      hostedOmittedPngBytes: packBytes.length,
      encodedBytes: packBytes.length,
      storedBytes: packBytes.length,
      packIndexBytes: indexBytes.length,
    },
    packs: [pack],
    mapping: {documents: 1, parts: 0, bytes: categoryBytes.length},
    categoryDocuments: [category],
  };
  const manifestBytes = jsonBytes(manifest);
  await Promise.all([
    writeFile(join(local, 'assets', 'pack-000.bin'), packBytes),
    writeFile(join(local, 'indexes', 'pack-000.bin'), indexBytes),
    writeFile(join(local, 'categories', '000.json'), categoryBytes),
    writeFile(join(local, 'manifest.json'), manifestBytes),
  ]);
  return {local, manifest, manifestBytes, packBytes, indexBytes, categoryBytes, entries};
}

async function startPublicServer(fixture, options = {}) {
  const requests = [];
  const prefix = `${PUBLIC_BASE_PATH}/${fixture.manifest.assetSetId}/`;
  const expectedSearch =
    `?dataset=${fixture.manifest.datasetPublicationId}&preview=${fixture.manifest.assetSetId}`;
  const server = createServer((request, response) => {
    const url = new URL(request.url, 'http://fixture.test');
    requests.push({
      method: request.method,
      path: url.pathname,
      search: url.search,
      range: request.headers.range,
    });
    if (url.search !== expectedSearch) {
      response.writeHead(400, {'cache-control': 'no-store', 'content-length': '0'});
      response.end();
      return;
    }
    if (/\/assets\/pack-\d+\.bin$|\/indexes\//.test(url.pathname)) {
      response.writeHead(500, {'cache-control': 'no-store', 'content-length': '0'});
      response.end();
      return;
    }

    let bytes;
    let contentType;
    let image = false;
    if (url.pathname === `${prefix}manifest.json`) {
      if (options.manifestPresent === false) {
        response.writeHead(404, {'cache-control': 'no-store', 'content-length': '0'});
        response.end();
        return;
      }
      bytes = fixture.manifestBytes;
      contentType = 'application/json; charset=utf-8';
    } else if (url.pathname === `${prefix}categories/000.json`) {
      bytes = options.corruptCategory
        ? Buffer.from(fixture.categoryBytes.map((value, index) => index === 0 ? value ^ 1 : value))
        : fixture.categoryBytes;
      contentType = 'application/json; charset=utf-8';
    } else {
      const match = new RegExp(
        `^${prefix.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}assets/s/000-(\\d+)-(\\d+)\\.webp$`,
      ).exec(url.pathname);
      if (!match) {
        response.writeHead(404, {'cache-control': 'no-store', 'content-length': '0'});
        response.end();
        return;
      }
      const offset = Number(match[1]);
      const length = Number(match[2]);
      if (!fixture.entries.some(entry => entry[0] === offset && entry[1] === length)) {
        response.writeHead(400, {'cache-control': 'no-store', 'content-length': '0'});
        response.end();
        return;
      }
      if (options.missingImageOffset === offset) {
        response.writeHead(404, {'cache-control': 'no-store', 'content-length': '0'});
        response.end();
        return;
      }
      bytes = Buffer.from(fixture.packBytes.subarray(offset, offset + length));
      if (options.corruptImageOffset === offset) bytes[0] ^= 1;
      contentType = options.imageContentType ?? 'image/webp';
      image = true;
    }

    const headers = publicHeaders(bytes.length, contentType, {
      image,
      omitNoTransform: image && options.omitNoTransform,
    });
    if (request.method === 'HEAD') {
      response.writeHead(200, headers);
      response.end();
      return;
    }
    if (request.method !== 'GET') {
      response.writeHead(405, {'cache-control': 'no-store', 'content-length': '0'});
      response.end();
      return;
    }
    response.writeHead(200, headers);
    response.end(bytes);
  });
  await new Promise((resolveListen, rejectListen) => {
    server.once('error', rejectListen);
    server.listen(0, '127.0.0.1', resolveListen);
  });
  const address = server.address();
  return {
    baseUrl: `http://127.0.0.1:${address.port}${PUBLIC_BASE_PATH}`,
    expectedSearch,
    requests,
    close: () => new Promise((resolveClose, rejectClose) => {
      server.closeAllConnections();
      server.close(error => (error ? rejectClose(error) : resolveClose()));
    }),
  };
}

test('remote verifier accepts only the exact content-addressed manifest-only v2 branch', async () => {
  const root = await mkdtemp(join(tmpdir(), 'preview-sidecar-data-only-remote-test-'));
  let server;
  try {
    const fixture = await createDataOnlySidecarFixture(root);
    server = await startPublicServer(fixture);
    const capture = recordingLogger();
    const result = await verifyRemoteRecipePreviewSidecar({
      local: fixture.local,
      baseUrl: server.baseUrl,
      mode: 'committed',
      logger: capture,
      allowHttpForTests: true,
    });
    assert.deepEqual(result, {
      mode: 'committed',
      assetSetId: fixture.manifest.assetSetId,
      datasetPublicationId: DATASET_PUBLICATION_ID,
      packs: 0,
      categoryDocuments: 0,
      imageSamples: 0,
    });
    assert.deepEqual(
      server.requests.map(request => `${request.method}:${request.path}`),
      [`GET:${PUBLIC_BASE_PATH}/${fixture.manifest.assetSetId}/manifest.json`],
    );
    assert.equal(
      capture.messages.warn.some(message => message.includes('explicitly excludes 1000 recipe previews')),
      true,
    );

    await server.close();
    server = undefined;
    const driftRoot = join(root, 'drift');
    const drift = await createDataOnlySidecarFixture(driftRoot, {
      counts: {...fixture.manifest.counts, packs: 1},
    });
    await assert.rejects(
      verifyRemoteRecipePreviewSidecar({
        local: drift.local,
        baseUrl: 'https://example.test/dataset/preview-sets',
        mode: 'committed',
        logger: quietLogger(),
      }),
      /manifest-only with zero previews\/packs\/mappings/,
    );
  } finally {
    if (server) await server.close();
    await rm(root, {recursive: true, force: true});
  }
});

async function withFixture(operation, settings) {
  const root = await mkdtemp(join(tmpdir(), 'remote-preview-verifier-test-'));
  try {
    await operation(await createSidecarFixture(root, settings), root);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
}

async function verify(fixture, server, mode, logger = quietLogger()) {
  return verifyRemoteRecipePreviewSidecar({
    local: fixture.local,
    baseUrl: server.baseUrl,
    mode,
    concurrency: 3,
    timeoutMs: 5_000,
    logger,
    allowHttpForTests: true,
  });
}

test('committed verification reads only public category documents and authorized coordinates', async () => {
  await withFixture(async fixture => {
    const server = await startPublicServer(fixture, {manifestPresent: true});
    try {
      const result = await verify(fixture, server, 'committed');
      assert.deepEqual(result, {
        mode: 'committed',
        assetSetId: fixture.manifest.assetSetId,
        datasetPublicationId: DATASET_PUBLICATION_ID,
        packs: 1,
        categoryDocuments: 1,
        imageSamples: 3,
      });
      assert.ok(server.requests.every(request => request.search === server.expectedSearch));
      assert.ok(server.requests.some(request =>
        request.method === 'GET' && request.path.endsWith('/manifest.json')));
      assert.ok(server.requests.some(request =>
        request.method === 'GET' && request.path.endsWith('/categories/000.json')));
      const imageRequests = server.requests.filter(request => request.path.includes('/assets/s/'));
      assert.equal(imageRequests.length, 3);
      assert.ok(imageRequests.every(request => request.method === 'GET' && request.range === undefined));
      assert.equal(
        server.requests.some(request => /\/assets\/pack-\d+\.bin$|\/indexes\//.test(request.path)),
        false,
        'public verification must never request private raw packs or MRPI indexes',
      );
    } finally {
      await server.close();
    }
  });
});

test('precommit verifies only that the public commit marker is absent', async () => {
  await withFixture(async fixture => {
    const server = await startPublicServer(fixture, {manifestPresent: false});
    const logger = recordingLogger();
    try {
      const result = await verify(fixture, server, 'precommit', logger);
      assert.deepEqual(result, {
        mode: 'precommit',
        assetSetId: fixture.manifest.assetSetId,
        datasetPublicationId: DATASET_PUBLICATION_ID,
        packs: 1,
        categoryDocuments: 0,
        imageSamples: 0,
      });
      assert.deepEqual(
        server.requests.map(request => ({method: request.method, path: request.path})),
        [{
          method: 'HEAD',
          path: `${PUBLIC_BASE_PATH}/${fixture.manifest.assetSetId}/manifest.json`,
        }],
      );
      assert.match(logger.messages.info.join('\n'), /staging verifies private packs and indexes/);
    } finally {
      await server.close();
    }
  });
});

test('verifier accepts grid-aligned high-resolution render settings', async () => {
  await withFixture(async fixture => {
    const server = await startPublicServer(fixture, {manifestPresent: true});
    try {
      const result = await verify(fixture, server, 'committed');
      assert.equal(result.imageSamples, 3);
      assert.deepEqual(
        {
          itemIconPixels: fixture.manifest.settings.itemIconPixels,
          recipeScale: fixture.manifest.settings.recipeScale,
        },
        {itemIconPixels: 48, recipeScale: 2},
      );
    } finally {
      await server.close();
    }
  }, {itemIconPixels: 48, recipeScale: 2});
});

test('precommit refuses an already-published manifest commit marker', async () => {
  await withFixture(async fixture => {
    const server = await startPublicServer(fixture, {manifestPresent: true});
    try {
      await assert.rejects(
        verify(fixture, server, 'precommit'),
        /requires remote manifest\.json to be absent.*HTTP 200/,
      );
    } finally {
      await server.close();
    }
  });
});

test('committed mode detects a byte-different manifest even when the length matches', async () => {
  await withFixture(async fixture => {
    const server = await startPublicServer(fixture, {manifestPresent: true});
    fixture.manifestBytes[fixture.manifestBytes.length - 2] ^= 1;
    try {
      await assert.rejects(verify(fixture, server, 'committed'), /not byte-for-byte identical/);
    } finally {
      await server.close();
    }
  });
});

test('committed mode fails closed and logs when a public category document is corrupt', async () => {
  await withFixture(async fixture => {
    const server = await startPublicServer(fixture, {manifestPresent: true, corruptCategory: true});
    const logger = recordingLogger();
    try {
      await assert.rejects(
        verify(fixture, server, 'committed', logger),
        /exact remote SHA-256\/byte comparison/,
      );
      assert.match(logger.messages.error.join('\n'), /verification failed closed/);
    } finally {
      await server.close();
    }
  });
});

test('committed mode rejects an authorized image that differs from the local pack range', async () => {
  await withFixture(async fixture => {
    const server = await startPublicServer(fixture, {manifestPresent: true, corruptImageOffset: 17});
    try {
      await assert.rejects(
        verify(fixture, server, 'committed'),
        /differs from the local MRPI-authorized pack range/,
      );
    } finally {
      await server.close();
    }
  });
});

test('committed mode rejects a missing authorized public image coordinate', async () => {
  await withFixture(async fixture => {
    const server = await startPublicServer(fixture, {manifestPresent: true, missingImageOffset: 40});
    try {
      await assert.rejects(
        verify(fixture, server, 'committed'),
        /GET assets\/s\/000-40-24\.webp returned HTTP 404/,
      );
    } finally {
      await server.close();
    }
  });
});

test('committed mode requires WebP content type and the no-transform cache directive', async () => {
  await withFixture(async fixture => {
    const wrongType = await startPublicServer(fixture, {
      manifestPresent: true,
      imageContentType: 'image/png',
    });
    try {
      await assert.rejects(
        verify(fixture, wrongType, 'committed'),
        /Content-Type.*expected image\/webp/,
      );
    } finally {
      await wrongType.close();
    }

    const transformed = await startPublicServer(fixture, {
      manifestPresent: true,
      omitNoTransform: true,
    });
    try {
      await assert.rejects(
        verify(fixture, transformed, 'committed'),
        /omitted the required no-transform image directive/,
      );
    } finally {
      await transformed.close();
    }
  });
});

test('local validation and the exact credential-free public route are fail-closed', async () => {
  await withFixture(async fixture => {
    await writeFile(join(fixture.local, 'assets', 'pack-000.bin'), Buffer.alloc(64, 7));
    await assert.rejects(
      verifyRemoteRecipePreviewSidecar({
        local: fixture.local,
        baseUrl: 'https://example.test/dataset/preview-sets',
        mode: 'committed',
        logger: quietLogger(),
      }),
      /failed its local SHA-256 digest/,
    );
    await assert.rejects(
      verifyRemoteRecipePreviewSidecar({
        local: fixture.local,
        baseUrl: 'http://example.test/dataset/preview-sets',
        mode: 'committed',
        logger: quietLogger(),
      }),
      /requires HTTPS/,
    );
    await assert.rejects(
      verifyRemoteRecipePreviewSidecar({
        local: fixture.local,
        baseUrl: 'https://user:secret@example.test/dataset/preview-sets',
        mode: 'committed',
        logger: quietLogger(),
      }),
      /must not contain credentials/,
    );
    await assert.rejects(
      verifyRemoteRecipePreviewSidecar({
        local: fixture.local,
        baseUrl: 'https://example.test/public/previews',
        mode: 'committed',
        logger: quietLogger(),
      }),
      /exact \/dataset\/preview-sets route/,
    );
  });
});
