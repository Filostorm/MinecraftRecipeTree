import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import test from 'node:test';
import {
  PREVIEW_UPLOAD_BASE_PATH,
  handlePreviewAssetUpload,
} from './previewAssetUpload.ts';
import {
  computePreviewAssetSetId,
  PREVIEW_CATEGORY_ROUTE,
  PREVIEW_PACK_INDEX_ROUTE,
  PREVIEW_PACK_ROUTE,
  requireContentAddressedPreviewManifest,
  requirePairedPublicationPolicy,
  requirePreviewManifest,
} from './previewAssetContract.ts';

// Independent known vector for the builder's framed content-address algorithm over fixture().
const ASSET_SET = '9b2bb8df647dcb22a5e755968bec7c0dcd46aae97b939e0cd47501a55a54e298';
const SERVING_ASSET_SET = 'c'.repeat(64);
const DATASET = 'b'.repeat(64);
const TOKEN = 'upload-token-'.padEnd(48, 'x');
const ORIGIN = 'https://viewer.example';

async function dataOnlyManifestFixture(overrides = {}) {
  const manifest = {
    format: 'mrt-recipe-preview-sidecar-v2',
    publicationPolicy: 'gtnh-structured-data-only-v1',
    exclusionReason: 'third-party-artwork-rights-not-cleared',
    assetSetId: '0'.repeat(64),
    datasetPublicationId: DATASET,
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
      categories: 674,
      recipes: 359215,
      previews: 0,
      missing: 359215,
      uniqueImages: 0,
      duplicates: 0,
      packs: 0,
      inputBytes: 0,
      hostedOmittedPngBytes: 123456789,
      encodedBytes: 0,
      storedBytes: 0,
      packIndexBytes: 0,
    },
    packs: [],
    mapping: {documents: 0, parts: 0, bytes: 0},
    categoryDocuments: [],
    ...overrides,
  };
  if (overrides.assetSetId === undefined) {
    manifest.assetSetId = await computePreviewAssetSetId(manifest);
  }
  return manifest;
}

test('preview object routes accept canonical indices beyond three digits', () => {
  assert.equal(PREVIEW_PACK_ROUTE.test('assets/pack-999.bin'), true);
  assert.equal(PREVIEW_PACK_ROUTE.test('assets/pack-1000.bin'), true);
  assert.equal(PREVIEW_PACK_INDEX_ROUTE.test('indexes/pack-1000.bin'), true);
  assert.equal(PREVIEW_CATEGORY_ROUTE.test('categories/1000.json'), true);
  assert.equal(PREVIEW_CATEGORY_ROUTE.test('categories/1000/part-1000.json'), true);
  assert.equal(PREVIEW_PACK_ROUTE.test('assets/pack-99.bin'), false);
  assert.equal(PREVIEW_CATEGORY_ROUTE.test('categories/099/part-99.json'), false);
  for (const noncanonical of [
    'assets/pack-0000.bin',
    'assets/pack-0123.bin',
    'indexes/pack-00000.bin',
    'indexes/pack-0123.bin',
    'categories/0000.json',
    'categories/0123.json',
    'categories/1000/part-0000.json',
    'categories/0123/part-1000.json',
  ]) {
    assert.equal(
      PREVIEW_PACK_ROUTE.test(noncanonical) ||
        PREVIEW_PACK_INDEX_ROUTE.test(noncanonical) ||
        PREVIEW_CATEGORY_ROUTE.test(noncanonical),
      false,
      noncanonical,
    );
  }
});

test('manifest-only v2 is content addressed and restricted to the exact GTNH rights policy', async () => {
  const manifest = await dataOnlyManifestFixture();
  const state = await requireContentAddressedPreviewManifest(manifest, manifest.assetSetId);
  assert.equal(state.contentRecordsByPath.size, 0);
  assert.equal(state.categoryDocumentsByPath.size, 0);
  assert.equal(state.manifest.counts.missing, state.manifest.counts.recipes);
  requirePairedPublicationPolicy(
    {publicationPolicy: 'gtnh-structured-data-only-v1'},
    state.manifest,
  );

  const drifts = [
    {publicationPolicy: 'lookalike-policy'},
    {exclusionReason: 'hosting-archive-budget'},
    {counts: {...manifest.counts, previews: 1, missing: manifest.counts.recipes - 1}},
    {counts: {...manifest.counts, packs: 1}},
    {mapping: {documents: 1, parts: 0, bytes: 0}},
    {categoryDocuments: [{path: 'categories/000.json', bytes: 1, sha256: 'f'.repeat(64)}]},
  ];
  for (const drift of drifts) {
    const candidate = structuredClone({...manifest, ...drift});
    await assert.rejects(
      requireContentAddressedPreviewManifest(candidate, manifest.assetSetId),
      /structured-data-only v2 contract|content address/,
    );
  }

  const ordinaryEmpty = structuredClone(manifest);
  ordinaryEmpty.format = 'mrt-recipe-preview-sidecar-v1';
  delete ordinaryEmpty.publicationPolicy;
  delete ordinaryEmpty.exclusionReason;
  assert.throws(
    () => requirePreviewManifest(ordinaryEmpty, ordinaryEmpty.assetSetId),
    /does not satisfy the sidecar contract/,
  );
  assert.throws(
    () => requirePairedPublicationPolicy({}, manifest),
    /paired publication-policy contract/,
  );
  assert.throws(
    () => requirePairedPublicationPolicy(
      {publicationPolicy: 'gtnh-structured-data-only-v1'},
      fixture().manifest,
    ),
    /paired publication-policy contract/,
  );
});

test('preview ingestion commits an exact data-only v2 manifest without creating content objects', async () => {
  const manifest = await dataOnlyManifestFixture();
  const manifestBytes = Buffer.from(`${JSON.stringify(manifest)}\n`);
  const bucket = new MemoryR2();
  const env = runtime(bucket, {PREVIEW_UPLOAD_ASSET_SET_ID: manifest.assetSetId});
  const endpointFor = action =>
    `${ORIGIN}${PREVIEW_UPLOAD_BASE_PATH}${manifest.assetSetId}/${action}`;
  const begin = await handlePreviewAssetUpload(
    new Request(endpointFor('begin'), {
      method: 'POST',
      headers: authorizedHeaders({
        'Content-Length': String(manifestBytes.byteLength),
        'Content-Type': 'application/json',
        'X-MRT-Content-SHA256': sha256(manifestBytes),
        'X-MRT-Dataset-Publication-ID': DATASET,
      }),
      body: manifestBytes,
    }),
    env,
  );
  assert.equal(begin.status, 201);
  const commit = await handlePreviewAssetUpload(
    new Request(endpointFor('commit'), {
      method: 'POST',
      headers: authorizedHeaders({'Content-Length': '0'}),
    }),
    env,
  );
  assert.equal(commit.status, 201);
  assert.deepEqual([...bucket.objects.keys()], [`${manifest.assetSetId}/manifest.json`]);
  assert.equal((await commit.json()).objects, 0);
});

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function fixture() {
  const bodies = new Map([
    ['assets/pack-000.bin', Buffer.from('pack-body')],
    ['indexes/pack-000.bin', Buffer.alloc(28, 7)],
    ['categories/000.json', Buffer.from('{"recipes":[]}\n')],
  ]);
  const manifest = {
    format: 'mrt-recipe-preview-sidecar-v1',
    assetSetId: ASSET_SET,
    datasetPublicationId: DATASET,
    maxPackBytes: 1024 * 1024,
    packIndexFormat: 'mrt-recipe-preview-pack-index-v1',
    maxPackIndexBytes: 512 * 1024,
    counts: {
      uniqueImages: 1,
      packIndexBytes: bodies.get('indexes/pack-000.bin').byteLength,
      packs: 1,
      storedBytes: bodies.get('assets/pack-000.bin').byteLength,
    },
    packs: [
      {
        path: 'assets/pack-000.bin',
        bytes: bodies.get('assets/pack-000.bin').byteLength,
        sha256: sha256(bodies.get('assets/pack-000.bin')),
        index: {
          path: 'indexes/pack-000.bin',
          bytes: bodies.get('indexes/pack-000.bin').byteLength,
          sha256: sha256(bodies.get('indexes/pack-000.bin')),
          entries: 1,
        },
      },
    ],
    categoryDocuments: [
      {
        path: 'categories/000.json',
        bytes: bodies.get('categories/000.json').byteLength,
        sha256: sha256(bodies.get('categories/000.json')),
      },
    ],
  };
  const manifestBytes = Buffer.from(`${JSON.stringify(manifest)}\n`);
  return {bodies, manifest, manifestBytes};
}

async function bodyBytes(value) {
  if (value instanceof ReadableStream) return Buffer.from(await new Response(value).arrayBuffer());
  if (value instanceof ArrayBuffer) return Buffer.from(value);
  return Buffer.from(value.buffer, value.byteOffset, value.byteLength);
}

function r2Object(key, stored, withBody) {
  return {
    key,
    size: stored.bytes.byteLength,
    etag: stored.digest.slice(0, 32),
    customMetadata: {...stored.customMetadata},
    ...(withBody
      ? {arrayBuffer: async () => stored.bytes.buffer.slice(
          stored.bytes.byteOffset,
          stored.bytes.byteOffset + stored.bytes.byteLength,
        )}
      : {}),
  };
}

class MemoryR2 {
  objects = new Map();

  async head(key) {
    const stored = this.objects.get(key);
    return stored ? r2Object(key, stored, false) : null;
  }

  async get(key) {
    const stored = this.objects.get(key);
    return stored ? r2Object(key, stored, true) : null;
  }

  async put(key, value, options) {
    if (this.objects.has(key) && options.onlyIf?.etagDoesNotMatch === '*') return null;
    const bytes = await bodyBytes(value);
    const digest = sha256(bytes);
    if (digest !== options.sha256) throw new Error('checksum mismatch');
    const stored = {
      bytes,
      digest,
      customMetadata: {...options.customMetadata},
    };
    this.objects.set(key, stored);
    return r2Object(key, stored, false);
  }

  async list({prefix, limit, cursor}) {
    const keys = [...this.objects.keys()].filter(key => key.startsWith(prefix)).sort();
    const start = cursor ? Number(cursor) : 0;
    const selected = keys.slice(start, start + limit);
    const next = start + selected.length;
    return {
      objects: selected.map(key => r2Object(key, this.objects.get(key), false)),
      truncated: next < keys.length,
      ...(next < keys.length ? {cursor: String(next)} : {}),
    };
  }

  async delete(key) {
    this.objects.delete(key);
  }
}

class PathologicalPaginationR2 extends MemoryR2 {
  listCalls = 0;

  async list() {
    this.listCalls += 1;
    return {
      objects: [],
      truncated: true,
      cursor: String(this.listCalls),
    };
  }
}

function runtime(bucket = new MemoryR2(), overrides = {}) {
  return {
    PREVIEW_ASSETS: bucket,
    PREVIEW_ASSET_SET_ID: SERVING_ASSET_SET,
    PREVIEW_UPLOAD_ASSET_SET_ID: ASSET_SET,
    PREVIEW_UPLOAD_ENABLED: 'true',
    PREVIEW_UPLOAD_TOKEN: TOKEN,
    ...overrides,
  };
}

function endpoint(action) {
  return `${ORIGIN}${PREVIEW_UPLOAD_BASE_PATH}${ASSET_SET}/${action}`;
}

function authorizedHeaders(extra = {}) {
  return {Authorization: `Bearer ${TOKEN}`, ...extra};
}

async function beginUpload(env, manifestBytes, datasetPublicationId = DATASET) {
  return handlePreviewAssetUpload(
    new Request(endpoint('begin'), {
      method: 'POST',
      headers: authorizedHeaders({
        'Content-Length': String(manifestBytes.byteLength),
        'Content-Type': 'application/json',
        'X-MRT-Content-SHA256': sha256(manifestBytes),
        'X-MRT-Dataset-Publication-ID': datasetPublicationId,
      }),
      body: manifestBytes,
    }),
    env,
  );
}

async function putObject(env, path, bytes, digest = sha256(bytes)) {
  return handlePreviewAssetUpload(
    new Request(endpoint(`objects/${path}`), {
      method: 'PUT',
      headers: authorizedHeaders({
        'Content-Length': String(bytes.byteLength),
        'If-None-Match': '*',
        'X-MRT-Content-SHA256': digest,
        'X-MRT-Dataset-Publication-ID': DATASET,
      }),
      body: bytes,
    }),
    env,
  );
}

test('preview ingestion requires both the explicit feature gate and a strong token', async () => {
  const disabled = await handlePreviewAssetUpload(
    new Request(endpoint('status'), {method: 'HEAD'}),
    runtime(new MemoryR2(), {PREVIEW_UPLOAD_ENABLED: undefined}),
  );
  assert.equal(disabled.status, 503);

  const missing = await handlePreviewAssetUpload(
    new Request(endpoint('status'), {method: 'HEAD'}),
    runtime(new MemoryR2(), {PREVIEW_UPLOAD_TOKEN: undefined}),
  );
  assert.equal(missing.status, 503);

  const unauthorized = await handlePreviewAssetUpload(
    new Request(endpoint('status'), {method: 'HEAD'}),
    runtime(),
  );
  assert.equal(unauthorized.status, 401);
  assert.match(unauthorized.headers.get('www-authenticate'), /^Bearer /);
});

test('preview ingestion requires a distinct canonical upload target and never falls back to the serving identity', async () => {
  const missingTargetBucket = new MemoryR2();
  const missingTarget = await handlePreviewAssetUpload(
    new Request(endpoint('status'), {method: 'HEAD', headers: authorizedHeaders()}),
    runtime(missingTargetBucket, {PREVIEW_UPLOAD_ASSET_SET_ID: undefined}),
  );
  assert.equal(missingTarget.status, 503);
  assert.equal(missingTargetBucket.objects.size, 0);

  const malformedTargetBucket = new MemoryR2();
  const malformedTarget = await handlePreviewAssetUpload(
    new Request(endpoint('status'), {method: 'HEAD', headers: authorizedHeaders()}),
    runtime(malformedTargetBucket, {PREVIEW_UPLOAD_ASSET_SET_ID: 'not-a-sha256'}),
  );
  assert.equal(malformedTarget.status, 503);
  assert.equal(malformedTargetBucket.objects.size, 0);

  const servingTarget = await handlePreviewAssetUpload(
    new Request(
      `${ORIGIN}${PREVIEW_UPLOAD_BASE_PATH}${SERVING_ASSET_SET}/status`,
      {method: 'HEAD', headers: authorizedHeaders()},
    ),
    runtime(),
  );
  assert.equal(servingTarget.status, 404);
});

test('preview ingestion independently rejects a false content address before staging and on reload', async () => {
  const data = fixture();
  const tamperedManifest = structuredClone(data.manifest);
  tamperedManifest.packs[0].sha256 = 'f'.repeat(64);
  const tamperedBytes = Buffer.from(`${JSON.stringify(tamperedManifest)}\n`);

  const changedDatasetManifest = structuredClone(data.manifest);
  changedDatasetManifest.datasetPublicationId = 'd'.repeat(64);
  const changedDatasetBytes = Buffer.from(`${JSON.stringify(changedDatasetManifest)}\n`);
  for (const [label, manifestBytes, datasetPublicationId] of [
    ['record digest changed', tamperedBytes, DATASET],
    ['dataset publication changed', changedDatasetBytes, changedDatasetManifest.datasetPublicationId],
  ]) {
    const freshBucket = new MemoryR2();
    const rejectedBegin = await beginUpload(
      runtime(freshBucket),
      manifestBytes,
      datasetPublicationId,
    );
    assert.equal(rejectedBegin.status, 422, label);
    assert.equal(
      freshBucket.objects.size,
      0,
      `${label}: a false content address must not write staging state`,
    );
  }

  for (const manifestKey of [
    `_staging/${ASSET_SET}/manifest.json`,
    `${ASSET_SET}/manifest.json`,
  ]) {
    const bucket = new MemoryR2();
    const digest = sha256(tamperedBytes);
    bucket.objects.set(manifestKey, {
      bytes: tamperedBytes,
      digest,
      customMetadata: {'mrt-sha256': digest},
    });
    const status = await handlePreviewAssetUpload(
      new Request(endpoint('status'), {method: 'HEAD', headers: authorizedHeaders()}),
      runtime(bucket),
    );
    assert.equal(status.status, 502, manifestKey);
  }
});

test('preview ingestion stages, verifies, and commits the manifest last', async () => {
  const data = fixture();
  const bucket = new MemoryR2();
  const env = runtime(bucket);

  const begun = await beginUpload(env, data.manifestBytes);
  assert.equal(begun.status, 201);
  assert.equal(bucket.objects.has(`${ASSET_SET}/manifest.json`), false);

  const staged = await handlePreviewAssetUpload(
    new Request(endpoint('status'), {method: 'HEAD', headers: authorizedHeaders()}),
    env,
  );
  assert.equal(staged.status, 200);
  assert.equal(staged.headers.get('x-mrt-publication-state'), 'staged');
  const cloudflareNormalizedStatus = await handlePreviewAssetUpload(
    new Request(endpoint('status'), {method: 'GET', headers: authorizedHeaders()}),
    env,
  );
  assert.equal(cloudflareNormalizedStatus.status, 200);
  assert.equal(cloudflareNormalizedStatus.headers.get('x-mrt-publication-state'), 'staged');

  const missing = await handlePreviewAssetUpload(
    new Request(endpoint('objects/assets/pack-000.bin'), {
      method: 'HEAD',
      headers: authorizedHeaders(),
    }),
    env,
  );
  assert.equal(missing.status, 404);
  const cloudflareNormalizedMissingObject = await handlePreviewAssetUpload(
    new Request(endpoint('objects/assets/pack-000.bin'), {
      method: 'GET',
      headers: authorizedHeaders(),
    }),
    env,
  );
  assert.equal(cloudflareNormalizedMissingObject.status, 404);

  const incompleteCommit = await handlePreviewAssetUpload(
    new Request(endpoint('commit'), {
      method: 'POST',
      headers: authorizedHeaders({'Content-Length': '0'}),
    }),
    env,
  );
  assert.equal(incompleteCommit.status, 409);

  for (const [path, bytes] of data.bodies) {
    const uploaded = await putObject(env, path, bytes);
    assert.equal(uploaded.status, 201, path);
    const verified = await handlePreviewAssetUpload(
      new Request(endpoint(`objects/${path}`), {
        method: 'HEAD',
        headers: authorizedHeaders(),
      }),
      env,
    );
    assert.equal(verified.status, 200, path);
    assert.equal(verified.headers.get('x-mrt-content-sha256'), sha256(bytes));
  }
  const cloudflareNormalizedStoredObject = await handlePreviewAssetUpload(
    new Request(endpoint('objects/assets/pack-000.bin'), {
      method: 'GET',
      headers: authorizedHeaders(),
    }),
    env,
  );
  assert.equal(cloudflareNormalizedStoredObject.status, 200);

  const committed = await handlePreviewAssetUpload(
    new Request(endpoint('commit'), {
      method: 'POST',
      headers: authorizedHeaders({'Content-Length': '0'}),
    }),
    env,
  );
  assert.equal(committed.status, 201);
  assert.equal(bucket.objects.has(`${ASSET_SET}/manifest.json`), true);
  assert.equal(bucket.objects.has(`_staging/${ASSET_SET}/manifest.json`), false);

  const status = await handlePreviewAssetUpload(
    new Request(endpoint('status'), {method: 'HEAD', headers: authorizedHeaders()}),
    env,
  );
  assert.equal(status.status, 200);
  assert.equal(status.headers.get('x-mrt-publication-state'), 'committed');
  assert.equal(status.headers.get('x-mrt-content-sha256'), sha256(data.manifestBytes));

  const repeated = await handlePreviewAssetUpload(
    new Request(endpoint('commit'), {
      method: 'POST',
      headers: authorizedHeaders({'Content-Length': '0'}),
    }),
    env,
  );
  assert.equal(repeated.status, 200);
});

test('preview ingestion does not cache an absent staged manifest across Worker bindings', async () => {
  const data = fixture();
  const firstBinding = new MemoryR2();
  const stagingBinding = new MemoryR2();
  stagingBinding.objects = firstBinding.objects;
  const firstEnv = runtime(firstBinding);
  const stagingEnv = runtime(stagingBinding);

  const beforeBegin = await handlePreviewAssetUpload(
    new Request(endpoint('status'), {method: 'HEAD', headers: authorizedHeaders()}),
    firstEnv,
  );
  assert.equal(beforeBegin.status, 404);
  assert.equal((await beginUpload(stagingEnv, data.manifestBytes)).status, 201);

  const afterBegin = await handlePreviewAssetUpload(
    new Request(endpoint('status'), {method: 'HEAD', headers: authorizedHeaders()}),
    firstEnv,
  );
  assert.equal(afterBegin.status, 200);
  assert.equal(afterBegin.headers.get('x-mrt-publication-state'), 'staged');
});

test('preview ingestion rejects body-like commits and bounds pathological R2 pagination', async () => {
  const data = fixture();
  const bucket = new PathologicalPaginationR2();
  const env = runtime(bucket);
  assert.equal((await beginUpload(env, data.manifestBytes)).status, 201);

  const bodyLike = await handlePreviewAssetUpload(
    new Request(endpoint('commit'), {
      method: 'POST',
      headers: authorizedHeaders(),
    }),
    env,
  );
  assert.equal(bodyLike.status, 400);

  const forwardedEmptyBodyRequest = new Request(endpoint('commit'), {
    method: 'POST',
    headers: authorizedHeaders({'Content-Length': '0'}),
    body: new Uint8Array(),
  });
  assert.notEqual(forwardedEmptyBodyRequest.body, null);
  const forwardedEmptyBody = await handlePreviewAssetUpload(forwardedEmptyBodyRequest, env);
  assert.equal(forwardedEmptyBody.status, 409);
  assert.equal(bucket.listCalls, 4);
  bucket.listCalls = 0;

  const paginated = await handlePreviewAssetUpload(
    new Request(endpoint('commit'), {
      method: 'POST',
      headers: authorizedHeaders({'Content-Length': '0'}),
    }),
    env,
  );
  assert.equal(paginated.status, 409);
  assert.equal(bucket.listCalls, 4);
  assert.equal(bucket.objects.has(`${ASSET_SET}/manifest.json`), false);
});

test('preview ingestion rejects undeclared paths and bad checksums without writing R2', async () => {
  const data = fixture();
  const bucket = new MemoryR2();
  const env = runtime(bucket);
  assert.equal((await beginUpload(env, data.manifestBytes)).status, 201);

  const undeclared = await putObject(env, 'assets/pack-999.bin', Buffer.from('nope'));
  assert.equal(undeclared.status, 400);
  assert.equal(bucket.objects.has(`${ASSET_SET}/assets/pack-999.bin`), false);

  const bytes = data.bodies.get('assets/pack-000.bin');
  const badDigest = await putObject(env, 'assets/pack-000.bin', bytes, 'c'.repeat(64));
  assert.equal(badDigest.status, 400);
  assert.equal(bucket.objects.has(`${ASSET_SET}/assets/pack-000.bin`), false);
});
