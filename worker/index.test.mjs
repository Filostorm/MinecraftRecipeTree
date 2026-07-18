import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {registerHooks} from 'node:module';
import test from 'node:test';

const vinextStub = `data:text/javascript,${encodeURIComponent(`
  export default {
    async fetch(request, env) {
      return env.ASSETS.fetch(request);
    }
  };
`)}`;

registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier === 'vinext/server/app-router-entry') {
      return {url: vinextStub, shortCircuit: true};
    }
    if (
      specifier === '../src/data/datasetIdentity' &&
      context.parentURL?.endsWith('/worker/index.ts')
    ) {
      return {
        url: new URL('../src/data/datasetIdentity.ts', context.parentURL).href,
        shortCircuit: true,
      };
    }
    return nextResolve(specifier, context);
  },
});

const {default: worker} = await import('./index.ts');

const PUBLICATION_A = 'a'.repeat(64);
const PUBLICATION_B = 'b'.repeat(64);
const PUBLICATION_C = 'c'.repeat(64);
const PREVIEW_SET_A = 'd'.repeat(64);
const PREVIEW_UPLOAD_TOKEN = 'preview-upload-test-token-'.padEnd(48, 'x');

const edgeCacheEntries = new Map();
globalThis.caches = {
  default: {
    async match(request) {
      const response = edgeCacheEntries.get(new Request(request).url);
      return response?.clone();
    },
    async put(request, response) {
      edgeCacheEntries.set(new Request(request).url, response.clone());
    },
  },
};

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function jsonBytes(value) {
  return new TextEncoder().encode(`${JSON.stringify(value)}\n`);
}

function previewIndexBytes(packNumber, packBytes, entries) {
  const bytes = new Uint8Array(20 + entries.length * 8);
  const view = new DataView(bytes.buffer);
  bytes.set(new TextEncoder().encode('MRPI'), 0);
  view.setUint16(4, 1);
  view.setUint16(6, 20);
  view.setUint32(8, packNumber);
  view.setUint32(12, packBytes);
  view.setUint32(16, entries.length);
  for (const [index, [offset, length]] of entries.entries()) {
    view.setUint32(20 + index * 8, offset);
    view.setUint32(24 + index * 8, length);
  }
  return bytes;
}

function manifest(publicationId) {
  return {
    publicationId,
    format: 1,
    generatedAt: '2026-07-18T12:34:56.789Z',
    durationMs: 100,
    aborted: false,
    minecraft: '1.12.2',
    counts: {items: 1, recipes: 1, categories: 1, mobs: 0, failures: 0},
    mods: {minecraft: 'Minecraft'},
  };
}

function createAssetEnvironment(initialPublicationId) {
  const state = {
    publicationId: initialPublicationId,
    packs: new Map([[0, new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8])]]),
  };
  const calls = [];
  const env = {
    ASSETS: {
      async fetch(request) {
        const url = new URL(request.url);
        calls.push({
          pathname: url.pathname,
          dataset: url.searchParams.get('dataset'),
          cache: request.cache,
          range: request.headers.get('range'),
        });
        if (url.pathname.endsWith('/manifest.json')) {
          return Response.json(manifest(state.publicationId));
        }
        if (url.pathname.endsWith('/items.json')) {
          return Response.json({items: [{k: 'item|minecraft:stone'}]});
        }
        const packMatch = url.pathname.match(/\/assets\/pack-(\d+)\.bin$/);
        if (packMatch) {
          const bytes = state.packs.get(Number(packMatch[1]));
          return bytes
            ? new Response(bytes, {status: 200})
            : new Response('not found', {status: 404});
        }
        return new Response('not found', {status: 404});
      },
    },
  };
  return {state, calls, env};
}

async function requestWithEnv(path, env) {
  const namespace = /^\/([a-z0-9-]+)\/(exports|previews)(\/.*)$/.exec(path);
  const host = namespace ? `${namespace[1]}.example.test` : 'example.test';
  const normalizedPath = namespace ? `/dataset/${namespace[2]}${namespace[3]}` : path;
  const pending = [];
  const response = await worker.fetch(new Request(`https://${host}${normalizedPath}`), env, {
    waitUntil(operation) {
      pending.push(Promise.resolve(operation));
    },
  });
  await Promise.all(pending);
  return response;
}

function createPreviewEnvironment(publicationId, label = 'default') {
  const base = createAssetEnvironment(publicationId);
  const previewState = {
    datasetPublicationId: publicationId,
    pack: new Uint8Array([10, 11, 12, 13, 14, 15, 16, 17]),
    reportedPackBytes: null,
    reportedRange: null,
    rangeBody: null,
    corruptIndex: false,
    missingObjects: new Set(),
  };
  const index = previewIndexBytes(0, previewState.pack.length, [[0, 2], [2, 4], [6, 2]]);
  const category = jsonBytes({
    format: 'mrt-recipe-preview-category-v1',
    categoryIndex: 0,
    categoryId: 'minecraft.crafting',
    count: 1,
    previews: [[0, 2, 4, 124, 62]],
  });
  const previewCalls = [];
  base.env.PREVIEW_ASSET_SET_ID = PREVIEW_SET_A;
  base.env.PREVIEW_ASSETS = {
    async get(key, options) {
      const range = options?.range
        ? {offset: options.range.offset, length: options.range.length}
        : undefined;
      previewCalls.push({key, options: range ? {range} : undefined});
      const namespace = `${PREVIEW_SET_A}/`;
      if (!key.startsWith(namespace)) return null;
      const objectKey = key.slice(namespace.length);
      if (previewState.missingObjects.has(objectKey)) return null;

      let bytes;
      if (objectKey === 'manifest.json') {
        bytes = jsonBytes({
          format: 'mrt-recipe-preview-sidecar-v1',
          assetSetId: PREVIEW_SET_A,
          datasetPublicationId: previewState.datasetPublicationId,
          maxPackBytes: 1024 * 1024,
          packIndexFormat: 'mrt-recipe-preview-pack-index-v1',
          maxPackIndexBytes: 512 * 1024,
          counts: {uniqueImages: 3, packIndexBytes: index.length},
          packs: [
            {
              path: 'assets/pack-000.bin',
              bytes: previewState.pack.length,
              sha256: 'e'.repeat(64),
              index: {
                path: 'indexes/pack-000.bin',
                bytes: index.length,
                sha256: sha256(index),
                entries: 3,
              },
            },
          ],
          categoryDocuments: [
            {
              path: 'categories/000.json',
              bytes: category.length,
              sha256: sha256(category),
            },
          ],
        });
      } else if (objectKey === 'categories/000.json') {
        bytes = category;
      } else if (objectKey === 'indexes/pack-000.bin') {
        bytes = previewState.corruptIndex
          ? new Uint8Array(index.map((value, offset) => offset === 20 ? value ^ 1 : value))
          : index;
      } else if (objectKey === 'assets/pack-000.bin') {
        if (!range) throw new Error(`${label}: packed preview reads must use an exact R2 range`);
        bytes = previewState.rangeBody ??
          previewState.pack.slice(range.offset, range.offset + range.length);
        const returnedRange = previewState.reportedRange ?? range;
        return {
          key,
          size: previewState.reportedPackBytes ?? previewState.pack.length,
          range: {...returnedRange},
          async arrayBuffer() {
            return bytes.slice().buffer;
          },
        };
      } else {
        return null;
      }

      return {
        key,
        size: bytes.length,
        async arrayBuffer() {
          return bytes.slice().buffer;
        },
      };
    },
  };
  return {...base, previewState, previewCalls};
}

test('the only unversioned export request is a no-store bootstrap manifest', async () => {
  const {env, calls} = createAssetEnvironment(PUBLICATION_A);
  const response = await requestWithEnv('/bootstrap/exports/manifest.json', env);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.equal(calls.length, 1);

  const missing = await requestWithEnv('/bootstrap/exports/items.json', env);
  assert.equal(missing.status, 400);
  assert.match(await missing.text(), /publication identity/);

  const duplicate = await requestWithEnv(
    `/bootstrap/exports/items.json?dataset=${PUBLICATION_A}&dataset=${PUBLICATION_A}`,
    env,
  );
  assert.equal(duplicate.status, 400);

  const malformed = await requestWithEnv(
    `/bootstrap/exports/items.json?dataset=${PUBLICATION_A.toUpperCase()}`,
    env,
  );
  assert.equal(malformed.status, 400);
});

test('the publication gate covers JSON, versioned manifests, and packed images', async () => {
  const {calls, env} = createAssetEnvironment(PUBLICATION_A);
  const wrongJson = await requestWithEnv(
    `/coverage/exports/items.json?dataset=${PUBLICATION_C}`,
    env,
  );
  assert.equal(wrongJson.status, 409);

  const items = await requestWithEnv(
    `/coverage/exports/items.json?dataset=${PUBLICATION_A}`,
    env,
  );
  assert.equal(items.status, 200);
  assert.equal(items.headers.get('cache-control'), 'public, max-age=31536000, immutable');

  const versionedManifest = await requestWithEnv(
    `/coverage/exports/manifest.json?dataset=${PUBLICATION_A}`,
    env,
  );
  assert.equal(versionedManifest.status, 200);

  const image = await requestWithEnv(
    `/coverage/exports/assets/s/000-0-4.webp?dataset=${PUBLICATION_A}`,
    env,
  );
  assert.equal(image.status, 200);
  assert.equal(image.headers.get('content-type'), 'image/webp');
  assert.deepEqual([...new Uint8Array(await image.arrayBuffer())], [1, 2, 3, 4]);
  assert.equal(
    calls.some(call => call.pathname.startsWith('/coverage/exports/')),
    false,
    'the Worker must map virtual client routes to the physical /exports asset tree',
  );
  assert.ok(calls.some(call => call.pathname === '/exports/items.json'));

  const missingJson = await requestWithEnv(
    `/coverage/exports/missing.json?dataset=${PUBLICATION_A}`,
    env,
  );
  assert.equal(missingJson.status, 404);
  assert.equal(missingJson.headers.get('cache-control'), 'no-store');
});

test('the publication gate rejects doubly encoded traversal before physical asset lookup', async () => {
  const {calls, env} = createAssetEnvironment(PUBLICATION_A);
  const response = await requestWithEnv(
    `/dataset/exports/%252e%252e/items.json?dataset=${PUBLICATION_A}`,
    env,
  );
  assert.equal(response.status, 400);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.equal(calls.some(call => call.pathname === '/items.json'), false);
});

test('an immutable Worker version shares one validated metadata snapshot', async () => {
  const {calls, env} = createAssetEnvironment(PUBLICATION_A);
  const [items, image] = await Promise.all([
    requestWithEnv(`/immutable/exports/items.json?dataset=${PUBLICATION_A}`, env),
    requestWithEnv(`/immutable/exports/assets/s/000-0-4.webp?dataset=${PUBLICATION_A}`, env),
  ]);
  assert.equal(items.status, 200);
  assert.equal(image.status, 200);

  const metadataCalls = calls.filter(call => call.pathname.endsWith('/manifest.json'));
  assert.equal(
    metadataCalls.length,
    1,
    'concurrent requests must share one publication-identity load',
  );
  assert.ok(metadataCalls.every(call => call.cache === 'no-store'));
  assert.equal(calls.some(call => call.pathname.endsWith('/assets-index.json')), false);
});

test('whole-pack reads are coalesced and sliced without Range requests', async () => {
  const {calls, env} = createAssetEnvironment(PUBLICATION_A);
  const [first, second, head] = await Promise.all([
    requestWithEnv(`/whole-pack/exports/assets/s/000-0-4.webp?dataset=${PUBLICATION_A}`, env),
    requestWithEnv(`/whole-pack/exports/assets/s/000-4-4.webp?dataset=${PUBLICATION_A}`, env),
    worker.fetch(
      new Request(
        `https://whole-pack.example.test/dataset/exports/assets/s/000-2-2.webp?dataset=${PUBLICATION_A}`,
        {method: 'HEAD'},
      ),
      env,
      {},
    ),
  ]);
  assert.deepEqual([...new Uint8Array(await first.arrayBuffer())], [1, 2, 3, 4]);
  assert.deepEqual([...new Uint8Array(await second.arrayBuffer())], [5, 6, 7, 8]);
  assert.equal(head.status, 200);
  assert.equal(head.headers.get('content-length'), '2');
  assert.equal((await head.arrayBuffer()).byteLength, 0);
  const packCalls = calls.filter(call => call.pathname.endsWith('/assets/pack-000.bin'));
  assert.equal(packCalls.length, 1, 'concurrent coordinates must share one whole-pack fetch');
  assert.ok(packCalls.every(call => call.range === null));
});

test('malformed coordinate routes fail closed before pack retrieval', async () => {
  const {calls, env} = createAssetEnvironment(PUBLICATION_A);
  const malformedPaths = [
    'assets/s/0-0-4.webp',
    'assets/s/000-00-4.webp',
    'assets/s/000-0-0.webp',
    'assets/s/000-1048575-2.webp',
    'icons/example.webp',
  ];
  for (const path of malformedPaths) {
    const response = await requestWithEnv(
      `/malformed-coordinate/exports/${path}?dataset=${PUBLICATION_A}`,
      env,
    );
    assert.equal(response.status, 400, path);
    assert.match(await response.text(), /coordinate/i);
  }
  assert.equal(calls.some(call => /\/assets\/pack-\d+\.bin$/.test(call.pathname)), false);
});

test('coordinates and packs are verified against physical pack bounds', async () => {
  const {state, env} = createAssetEnvironment(PUBLICATION_A);
  state.packs.set(0, new Uint8Array([1, 2, 3]));
  const outOfBounds = await requestWithEnv(
    `/physical-bounds/exports/assets/s/000-1-3.webp?dataset=${PUBLICATION_A}`,
    env,
  );
  assert.equal(outOfBounds.status, 502);
  assert.match(await outOfBounds.text(), /out of bounds/i);

  const missing = await requestWithEnv(
    `/missing-pack/exports/assets/s/001-0-1.webp?dataset=${PUBLICATION_A}`,
    env,
  );
  assert.equal(missing.status, 502);
  assert.match(await missing.text(), /pack unavailable/i);
});

test('whole-pack cache is bounded and evicts least-recently-used packs', async () => {
  const {state, calls, env} = createAssetEnvironment(PUBLICATION_A);
  for (let packNumber = 0; packNumber <= 16; packNumber += 1) {
    state.packs.set(packNumber, new Uint8Array([packNumber]));
    const coordinate = `${String(packNumber).padStart(3, '0')}-0-1.webp`;
    const response = await requestWithEnv(
      `/bounded-pack-cache/exports/assets/s/${coordinate}?dataset=${PUBLICATION_A}`,
      env,
    );
    assert.equal(response.status, 200);
  }
  const revisited = await requestWithEnv(
    `/bounded-pack-cache/exports/assets/s/000-0-1.webp?dataset=${PUBLICATION_A}`,
    env,
  );
  assert.equal(revisited.status, 200);
  assert.equal(
    calls.filter(call => call.pathname.endsWith('/assets/pack-000.bin')).length,
    2,
    'the seventeenth pack must evict the least-recently-used first pack',
  );
});

test('a stale client fails closed when routed to a newer immutable Worker version', async () => {
  const {env: oldEnv} = createAssetEnvironment(PUBLICATION_A);
  const oldJson = await requestWithEnv(
    `/old-version/exports/items.json?dataset=${PUBLICATION_A}`,
    oldEnv,
  );
  assert.equal(oldJson.status, 200);

  const {env: newEnv} = createAssetEnvironment(PUBLICATION_B);
  const staleJson = await requestWithEnv(
    `/new-version/exports/items.json?dataset=${PUBLICATION_A}`,
    newEnv,
  );
  assert.equal(staleJson.status, 409);
  const staleImage = await requestWithEnv(
    `/new-version/exports/assets/s/000-0-4.webp?dataset=${PUBLICATION_A}`,
    newEnv,
  );
  assert.equal(staleImage.status, 409);
  const currentJson = await requestWithEnv(
    `/new-version/exports/items.json?dataset=${PUBLICATION_B}`,
    newEnv,
  );
  assert.equal(currentJson.status, 200);
  const currentImage = await requestWithEnv(
    `/new-version/exports/assets/s/000-0-4.webp?dataset=${PUBLICATION_B}`,
    newEnv,
  );
  assert.equal(currentImage.status, 200);
});

test('missing ASSETS binding fails explicitly instead of recursing through the origin', async () => {
  const response = await requestWithEnv(
    `/missing-binding/exports/items.json?dataset=${PUBLICATION_A}`,
    {},
  );
  assert.equal(response.status, 502);
  assert.match(await response.text(), /identity unavailable/i);
});

test('the admin preview path dispatches to authenticated R2 ingestion before the app router', async () => {
  const {env, calls} = createAssetEnvironment(PUBLICATION_A);
  const r2Calls = [];
  env.PREVIEW_ASSET_SET_ID = PREVIEW_SET_A;
  env.PREVIEW_UPLOAD_ENABLED = 'true';
  env.PREVIEW_UPLOAD_TOKEN = PREVIEW_UPLOAD_TOKEN;
  env.PREVIEW_ASSETS = {
    async get(key) {
      r2Calls.push(key);
      return null;
    },
  };
  const response = await worker.fetch(
    new Request(
      `https://example.test/api/admin/preview-assets/${PREVIEW_SET_A}/status`,
      {
        method: 'HEAD',
        headers: {Authorization: `Bearer ${PREVIEW_UPLOAD_TOKEN}`},
      },
    ),
    env,
    {},
  );
  assert.equal(response.status, 404);
  assert.deepEqual(r2Calls, [
    `${PREVIEW_SET_A}/manifest.json`,
    `_staging/${PREVIEW_SET_A}/manifest.json`,
  ]);
  assert.equal(calls.length, 0, 'the Vinext/static-asset router must not receive admin uploads');
});

test('preview metadata and images are gated by the matching dataset publication', async () => {
  const {env, previewCalls} = createPreviewEnvironment(PUBLICATION_A, 'gated');
  const previewManifest = await requestWithEnv(
    `/dataset/previews/manifest.json?dataset=${PUBLICATION_A}`,
    env,
  );
  assert.equal(previewManifest.status, 200);
  assert.equal(previewManifest.headers.get('cache-control'), 'no-store');
  assert.equal((await previewManifest.json()).datasetPublicationId, PUBLICATION_A);

  const category = await requestWithEnv(
    `/dataset/previews/categories/000.json?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}`,
    env,
  );
  assert.equal(category.status, 200);
  assert.equal(category.headers.get('x-mrt-preview-cache'), 'MISS');
  assert.match(category.headers.get('cache-control') ?? '', /immutable/);
  assert.equal((await category.json()).previews.length, 1);
  const cachedCategory = await requestWithEnv(
    `/dataset/previews/categories/000.json?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}`,
    env,
  );
  assert.equal(cachedCategory.status, 200);
  assert.equal(cachedCategory.headers.get('x-mrt-preview-cache'), 'HIT');
  assert.equal((await cachedCategory.json()).previews.length, 1);
  assert.equal(
    previewCalls.filter(call => call.key.endsWith('/categories/000.json')).length,
    1,
    'a validated immutable category response should be served from edge cache on repeat',
  );

  const image = await requestWithEnv(
    `/dataset/previews/assets/s/000-2-4.webp?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}`,
    env,
  );
  assert.equal(image.status, 200);
  assert.equal(image.headers.get('content-type'), 'image/webp');
  assert.deepEqual([...new Uint8Array(await image.arrayBuffer())], [12, 13, 14, 15]);
  const fullObjectCalls = previewCalls.filter(call =>
    call.key.endsWith('/manifest.json') ||
    call.key.endsWith('/categories/000.json') ||
    call.key.endsWith('/indexes/pack-000.bin'),
  );
  assert.ok(fullObjectCalls.length >= 3);
  assert.ok(
    fullObjectCalls.every(call => call.options === undefined),
    'manifests, category documents, and MRPI indexes must use full R2 object reads',
  );
  const packCalls = previewCalls.filter(call => call.key.endsWith('/assets/pack-000.bin'));
  assert.equal(packCalls.length, 1);
  assert.deepEqual(packCalls[0].options, {range: {offset: 2, length: 4}});
});

test('preview cache keys require exact canonical query strings before any R2 read', async () => {
  const {env, previewCalls, calls} = createPreviewEnvironment(PUBLICATION_A, 'canonical-query');
  const paths = [
    `/canonical-query/previews/manifest.json?dataset=${PUBLICATION_A}&extra=1`,
    `/canonical-query/previews/manifest.json?extra=1&dataset=${PUBLICATION_A}`,
    `/canonical-query/previews/categories/000.json?preview=${PREVIEW_SET_A}&dataset=${PUBLICATION_A}`,
    `/canonical-query/previews/categories/000.json?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}&extra=1`,
    `/canonical-query/previews/categories/000.json?dataset=${PUBLICATION_A}&extra=1&preview=${PREVIEW_SET_A}`,
  ];
  for (const path of paths) {
    const response = await requestWithEnv(path, env);
    assert.equal(response.status, 400, path);
    assert.match(await response.text(), /exact canonical form/i);
  }
  assert.equal(previewCalls.length, 0, 'noncanonical cache keys must not reach R2');
  assert.equal(calls.length, 0, 'noncanonical cache keys must not load local dataset metadata');
});

test('only MRPI-authorized image boundaries can trigger exact R2 range reads', async () => {
  const {env, previewCalls} = createPreviewEnvironment(PUBLICATION_A, 'coordinate-membership');
  const unauthorized = await requestWithEnv(
    `/coordinate-membership/previews/assets/s/000-0-4.webp?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}`,
    env,
  );
  assert.equal(unauthorized.status, 400);
  assert.match(await unauthorized.text(), /not published/i);
  assert.equal(
    previewCalls.some(call => call.key.endsWith('/assets/pack-000.bin')),
    false,
  );

  for (let attempt = 0; attempt < 2; attempt += 1) {
    const valid = await requestWithEnv(
      `/coordinate-membership/previews/assets/s/000-2-4.webp?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}`,
      env,
    );
    assert.equal(valid.status, 200);
    assert.equal(
      valid.headers.get('x-mrt-preview-cache'),
      attempt === 0 ? 'MISS' : 'HIT',
    );
  }
  assert.equal(
    previewCalls.filter(call => call.key.endsWith('/indexes/pack-000.bin')).length,
    1,
    'one immutable, digest-verified index should authorize all requests for its pack',
  );
  assert.equal(
    previewCalls.filter(call => call.key.endsWith('/assets/pack-000.bin')).length,
    1,
    'a validated immutable image should avoid a second R2 range request',
  );
  const indexCall = previewCalls.find(call => call.key.endsWith('/indexes/pack-000.bin'));
  const packCall = previewCalls.find(call => call.key.endsWith('/assets/pack-000.bin'));
  assert.ok(indexCall);
  assert.ok(packCall);
  assert.equal(indexCall.options, undefined);
  assert.deepEqual(packCall.options, {range: {offset: 2, length: 4}});
  assert.ok(
    previewCalls.indexOf(indexCall) < previewCalls.indexOf(packCall),
    'the full MRPI authorization read must complete before any packed range read',
  );
});

test('corrupt authorization indexes and undeclared metadata fail before R2 content reads', async () => {
  const corrupt = createPreviewEnvironment(PUBLICATION_A, 'corrupt-index');
  corrupt.previewState.corruptIndex = true;
  const image = await requestWithEnv(
    `/corrupt-index/previews/assets/s/000-2-4.webp?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}`,
    corrupt.env,
  );
  assert.equal(image.status, 502);
  assert.match(await image.text(), /authorization index unavailable/i);
  assert.equal(
    corrupt.previewCalls.some(call => call.key.endsWith('/assets/pack-000.bin')),
    false,
  );

  const undeclared = createPreviewEnvironment(PUBLICATION_A, 'undeclared-category');
  const category = await requestWithEnv(
    `/undeclared-category/previews/categories/999.json?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}`,
    undeclared.env,
  );
  assert.equal(category.status, 400);
  assert.equal(
    undeclared.previewCalls.some(call => call.key.endsWith('/categories/999.json')),
    false,
  );
});

test('preview delivery rejects stale datasets and mismatched sidecars', async () => {
  const {env} = createPreviewEnvironment(PUBLICATION_A, 'stale-requests');
  const stale = await requestWithEnv(
    `/stale/previews/manifest.json?dataset=${PUBLICATION_B}`,
    env,
  );
  assert.equal(stale.status, 409);

  const missingAssetSet = await requestWithEnv(
    `/mismatch/previews/categories/000.json?dataset=${PUBLICATION_A}`,
    env,
  );
  assert.equal(missingAssetSet.status, 400);
  const staleAssetSet = await requestWithEnv(
    `/mismatch/previews/categories/000.json?dataset=${PUBLICATION_A}&preview=${PUBLICATION_C}`,
    env,
  );
  assert.equal(staleAssetSet.status, 409);

  const mismatchEnvironment = createPreviewEnvironment(PUBLICATION_A, 'dataset-mismatch');
  mismatchEnvironment.previewState.datasetPublicationId = PUBLICATION_B;
  const mismatch = await requestWithEnv(
    `/mismatch/previews/manifest.json?dataset=${PUBLICATION_A}`,
    mismatchEnvironment.env,
  );
  assert.equal(mismatch.status, 409);
  assert.match(await mismatch.text(), /dataset mismatch/i);
});

test('missing R2 objects fail explicitly and never bypass MRPI authorization', async () => {
  const missingManifest = createPreviewEnvironment(PUBLICATION_A, 'missing-r2-manifest');
  missingManifest.previewState.missingObjects.add('manifest.json');
  const manifestResponse = await requestWithEnv(
    `/missing-r2-manifest/previews/manifest.json?dataset=${PUBLICATION_A}`,
    missingManifest.env,
  );
  assert.equal(manifestResponse.status, 503);
  assert.match(await manifestResponse.text(), /storage unavailable/i);

  const missingCategory = createPreviewEnvironment(PUBLICATION_A, 'missing-r2-category');
  missingCategory.previewState.missingObjects.add('categories/000.json');
  const categoryResponse = await requestWithEnv(
    `/missing-r2-category/previews/categories/000.json?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}`,
    missingCategory.env,
  );
  assert.equal(categoryResponse.status, 502);
  assert.match(await categoryResponse.text(), /metadata unavailable/i);

  const missingIndex = createPreviewEnvironment(PUBLICATION_A, 'missing-r2-index');
  missingIndex.previewState.missingObjects.add('indexes/pack-000.bin');
  const indexResponse = await requestWithEnv(
    `/missing-r2-index/previews/assets/s/000-2-4.webp?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}`,
    missingIndex.env,
  );
  assert.equal(indexResponse.status, 502);
  assert.match(await indexResponse.text(), /authorization index unavailable/i);
  assert.equal(
    missingIndex.previewCalls.some(call => call.key.endsWith('/assets/pack-000.bin')),
    false,
    'a missing MRPI index must fail before the packed object is read',
  );

  const missingPack = createPreviewEnvironment(PUBLICATION_A, 'missing-r2-pack');
  missingPack.previewState.missingObjects.add('assets/pack-000.bin');
  const packResponse = await requestWithEnv(
    `/missing-r2-pack/previews/assets/s/000-2-4.webp?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}`,
    missingPack.env,
  );
  assert.equal(packResponse.status, 502);
  assert.match(await packResponse.text(), /asset unavailable/i);
});

test('preview delivery rejects malformed R2 range metadata, full size, and body length', async () => {
  const malformedCases = [
    {
      label: 'wrong-r2-range',
      configure(state) {
        state.reportedRange = {offset: 1, length: 4};
      },
      message: /invalid range metadata/i,
    },
    {
      label: 'wrong-r2-full-size',
      configure(state) {
        state.reportedPackBytes = 9;
      },
      message: /invalid range metadata/i,
    },
    {
      label: 'wrong-r2-body-length',
      configure(state) {
        state.rangeBody = new Uint8Array([12, 13, 14]);
      },
      message: /incomplete byte range/i,
    },
  ];
  for (const malformed of malformedCases) {
    const environment = createPreviewEnvironment(PUBLICATION_A, malformed.label);
    malformed.configure(environment.previewState);
    const response = await requestWithEnv(
      `/${malformed.label}/previews/assets/s/000-2-4.webp?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}`,
      environment.env,
    );
    assert.equal(response.status, 502, malformed.label);
    assert.match(await response.text(), malformed.message, malformed.label);
    const packCalls = environment.previewCalls.filter(call =>
      call.key.endsWith('/assets/pack-000.bin'),
    );
    assert.equal(packCalls.length, 1, malformed.label);
    assert.deepEqual(packCalls[0].options, {range: {offset: 2, length: 4}});
  }
});

test('preview delivery enforces declared pack coordinates before an R2 range read', async () => {
  const outside = createPreviewEnvironment(PUBLICATION_A, 'outside-pack');
  const outsideResponse = await requestWithEnv(
    `/outside-pack/previews/assets/s/000-7-2.webp?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}`,
    outside.env,
  );
  assert.equal(outsideResponse.status, 400);
  assert.equal(
    outside.previewCalls.some(call => call.key.endsWith('/assets/pack-000.bin')),
    false,
  );
});

test('preview delivery requires the native R2 binding and never falls back to HTTP', async () => {
  const {env} = createAssetEnvironment(PUBLICATION_A);
  env.PREVIEW_ASSET_SET_ID = PREVIEW_SET_A;
  env.PREVIEW_ASSET_BASE_URL = 'https://legacy-preview-origin.invalid/';
  const originalFetch = globalThis.fetch;
  let httpCalls = 0;
  globalThis.fetch = async () => {
    httpCalls += 1;
    return new Response('legacy HTTP origin must not be used', {status: 200});
  };
  try {
    const response = await requestWithEnv(
      `/missing-preview-r2/previews/manifest.json?dataset=${PUBLICATION_A}`,
      env,
    );
    assert.equal(response.status, 503);
    assert.match(await response.text(), /storage unavailable/i);
  } finally {
    globalThis.fetch = originalFetch;
  }
  assert.equal(httpCalls, 0);
});

test('malformed preview coordinates fail before R2 is read', async () => {
  const {env, previewCalls} = createPreviewEnvironment(PUBLICATION_A, 'malformed');
  const response = await requestWithEnv(
    `/malformed-preview/previews/assets/s/0-0-4.webp?dataset=${PUBLICATION_A}&preview=${PREVIEW_SET_A}`,
    env,
  );
  assert.equal(response.status, 400);
  assert.equal(previewCalls.some(call => call.key.endsWith('/assets/pack-000.bin')), false);
});

test('unrelated application paths containing previews are not intercepted', async () => {
  const {env, previewCalls, calls} = createPreviewEnvironment(PUBLICATION_A, 'app-route');
  const response = await worker.fetch(
    new Request('https://example.test/app/previews/page'),
    env,
    {},
  );
  assert.equal(response.status, 404);
  assert.equal(previewCalls.length, 0);
  assert.equal(calls.some(call => call.pathname === '/app/previews/page'), true);
});
