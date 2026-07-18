import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import test from 'node:test';
import {
  PREVIEW_UPLOAD_BASE_PATH,
  handlePreviewAssetUpload,
} from './previewAssetUpload.ts';

const ASSET_SET = 'a'.repeat(64);
const DATASET = 'b'.repeat(64);
const TOKEN = 'upload-token-'.padEnd(48, 'x');
const ORIGIN = 'https://viewer.example';

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
    PREVIEW_ASSET_SET_ID: ASSET_SET,
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

async function beginUpload(env, manifestBytes) {
  return handlePreviewAssetUpload(
    new Request(endpoint('begin'), {
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

  const missing = await handlePreviewAssetUpload(
    new Request(endpoint('objects/assets/pack-000.bin'), {
      method: 'HEAD',
      headers: authorizedHeaders(),
    }),
    env,
  );
  assert.equal(missing.status, 404);

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
