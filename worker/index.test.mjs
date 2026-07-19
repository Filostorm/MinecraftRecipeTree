import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {registerHooks} from 'node:module';
import test from 'node:test';
import {
  canonicalCoreDatasetPublicationBytes,
} from './coreDatasetContract.ts';
import {computePreviewAssetSetId} from './previewAssetContract.ts';

const vinextStub = `data:text/javascript,${encodeURIComponent(`
  export default { async fetch(request, env) { return env.ASSETS.fetch(request); } };
`)}`;

registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier === 'vinext/server/app-router-entry') {
      return {url: vinextStub, shortCircuit: true};
    }
    return nextResolve(specifier, context);
  },
});

const {default: worker} = await import('./index.ts');

const ORIGIN = 'https://viewer.example';
const PUBLICATION = 'a'.repeat(64);
const OTHER_PUBLICATION = 'b'.repeat(64);
const TOKEN = 'core-dataset-upload-token-'.padEnd(48, 'x');

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function jsonBytes(value) {
  return Buffer.from(`${JSON.stringify(value)}\n`);
}

async function bodyBytes(value) {
  if (value instanceof ReadableStream) return Buffer.from(await new Response(value).arrayBuffer());
  if (value instanceof ArrayBuffer) return Buffer.from(value);
  return Buffer.from(value.buffer, value.byteOffset, value.byteLength);
}

function objectView(key, stored, withBody, range) {
  const body = range
    ? stored.bytes.subarray(range.offset, range.offset + range.length)
    : stored.bytes;
  return {
    key,
    size: stored.bytes.byteLength,
    etag: stored.digest.slice(0, 32),
    customMetadata: {...stored.customMetadata},
    ...(range ? {range: {...range}} : {}),
    ...(withBody
      ? {
          async arrayBuffer() {
            return body.buffer.slice(body.byteOffset, body.byteOffset + body.byteLength);
          },
        }
      : {}),
  };
}

class MemoryR2 {
  objects = new Map();
  writes = [];
  reads = [];

  async head(key) {
    const stored = this.objects.get(key);
    return stored ? objectView(key, stored, false) : null;
  }

  async get(key, options) {
    const stored = this.objects.get(key);
    const range = options?.range ? {...options.range} : undefined;
    this.reads.push({key, range});
    return stored ? objectView(key, stored, true, range) : null;
  }

  async put(key, value, options) {
    if (this.objects.has(key) && options.onlyIf?.etagDoesNotMatch === '*') return null;
    const bytes = await bodyBytes(value);
    const digest = sha256(bytes);
    if (digest !== options.sha256) throw new Error(`R2 checksum mismatch for ${key}`);
    const stored = {bytes, digest, customMetadata: {...options.customMetadata}};
    this.objects.set(key, stored);
    this.writes.push(key);
    return objectView(key, stored, false);
  }

  async list({prefix, limit, cursor}) {
    const keys = [...this.objects.keys()].filter(key => key.startsWith(prefix)).sort();
    const start = cursor ? Number(cursor) : 0;
    const selected = keys.slice(start, start + limit);
    const next = start + selected.length;
    return {
      objects: selected.map(key => objectView(key, this.objects.get(key), false)),
      truncated: next < keys.length,
      ...(next < keys.length ? {cursor: String(next)} : {}),
    };
  }

  async delete(key) {
    this.objects.delete(key);
  }

  seed(key, bytes, customMetadata) {
    const body = Buffer.from(bytes);
    this.objects.set(key, {bytes: body, digest: sha256(body), customMetadata: {...customMetadata}});
  }
}

class MemoryD1Statement {
  constructor(database, sql) {
    this.database = database;
    this.sql = sql.replace(/\s+/g, ' ').trim();
    this.values = [];
  }

  bind(...values) {
    this.values = values;
    return this;
  }

  async run() {
    return this.database.executeRun(this.sql, this.values);
  }

  async first() {
    return this.database.executeFirst(this.sql, this.values);
  }

  async all() {
    return this.database.executeAll(this.sql, this.values);
  }
}

class MemoryD1 {
  publications = new Map();
  channels = new Map();
  modpacks = [];
  batches = [];

  prepare(sql) {
    return new MemoryD1Statement(this, sql);
  }

  async batch(statements) {
    const publications = structuredClone(this.publications);
    const channels = structuredClone(this.channels);
    try {
      const results = [];
      for (const statement of statements) results.push(await statement.run());
      this.batches.push(statements.map(statement => statement.sql));
      return results;
    } catch (error) {
      this.publications = publications;
      this.channels = channels;
      throw error;
    }
  }

  async executeRun(sql, values) {
    if (sql.startsWith('CREATE TABLE') || sql.startsWith('CREATE UNIQUE INDEX')) {
      return {success: true};
    }
    if (sql.startsWith('INSERT INTO dataset_publications')) {
      const [publicationId, manifestSha256, objectCount, storedBytes, committedAt] = values;
      if (this.publications.has(publicationId)) throw new Error('publication primary-key conflict');
      this.publications.set(publicationId, {
        publication_id: publicationId,
        manifest_sha256: manifestSha256,
        object_count: objectCount,
        stored_bytes: storedBytes,
        committed_at: committedAt,
      });
      return {success: true, meta: {changes: 1}};
    }
    if (sql.startsWith('UPDATE dataset_channels SET is_default = 0')) {
      const [targetSlug, guardedSlug, expectedPublicationId] = values;
      const target = this.channels.get(guardedSlug);
      const expectationMatches = values.length === 2
        ? !target
        : target?.publication_id === expectedPublicationId;
      let changes = 0;
      if (expectationMatches) {
        for (const row of this.channels.values()) {
          if (row.is_default === 1 && row.slug !== targetSlug) {
            row.is_default = 0;
            changes += 1;
          }
        }
      }
      return {success: true, meta: {changes}};
    }
    if (sql.startsWith('INSERT INTO dataset_channels')) {
      const [
        slug,
        displayName,
        minecraftVersion,
        packVersion,
        publicationId,
        previewId,
        isDefault,
        now,
        guardedSlug,
        guardedDefault,
        defaultExclusionSlug,
      ] = values;
      const hasOtherDefault = [...this.channels.values()].some(
        row => row.is_default === 1 && row.slug !== defaultExclusionSlug,
      );
      if (this.channels.has(guardedSlug) || (guardedDefault !== 1 && !hasOtherDefault)) {
        return {success: true, meta: {changes: 0}};
      }
      for (const [otherSlug, row] of this.channels) {
        if (otherSlug !== slug && row.publication_id === publicationId) throw new Error('publication unique conflict');
        if (otherSlug !== slug && row.preview_asset_set_id === previewId) throw new Error('preview unique conflict');
        if (otherSlug !== slug && isDefault === 1 && row.is_default === 1) throw new Error('default unique conflict');
      }
      this.channels.set(slug, {
        slug,
        display_name: displayName,
        minecraft_version: minecraftVersion,
        pack_version: packVersion,
        publication_id: publicationId,
        preview_asset_set_id: previewId,
        is_default: isDefault,
        revision: 1,
        activated_at: now,
      });
      return {success: true, meta: {changes: 1}};
    }
    if (sql.startsWith('UPDATE dataset_channels SET display_name = ?')) {
      const [
        displayName,
        minecraftVersion,
        packVersion,
        publicationId,
        previewId,
        isDefault,
        now,
        slug,
        expectedPublicationId,
        guardedDefault,
        defaultExclusionSlug,
      ] = values;
      const previous = this.channels.get(slug);
      const hasOtherDefault = [...this.channels.values()].some(
        row => row.is_default === 1 && row.slug !== defaultExclusionSlug,
      );
      if (
        !previous ||
        previous.publication_id !== expectedPublicationId ||
        (guardedDefault !== 1 && !hasOtherDefault)
      ) {
        return {success: true, meta: {changes: 0}};
      }
      for (const [otherSlug, row] of this.channels) {
        if (otherSlug !== slug && row.publication_id === publicationId) throw new Error('publication unique conflict');
        if (otherSlug !== slug && row.preview_asset_set_id === previewId) throw new Error('preview unique conflict');
        if (otherSlug !== slug && isDefault === 1 && row.is_default === 1) throw new Error('default unique conflict');
      }
      this.channels.set(slug, {
        slug,
        display_name: displayName,
        minecraft_version: minecraftVersion,
        pack_version: packVersion,
        publication_id: publicationId,
        preview_asset_set_id: previewId,
        is_default: isDefault,
        revision: (previous.revision ?? 0) + 1,
        activated_at: now,
      });
      return {success: true, meta: {changes: 1}};
    }
    if (sql.startsWith('DELETE FROM dataset_channels WHERE slug = ? AND is_default = 0')) {
      const [slug, publicationId, previewAssetSetId] = values;
      const row = this.channels.get(slug);
      if (
        !row ||
        row.is_default !== 0 ||
        row.publication_id !== publicationId ||
        row.preview_asset_set_id !== previewAssetSetId
      ) {
        return {success: true, meta: {changes: 0}};
      }
      this.channels.delete(values[0]);
      return {success: true, meta: {changes: 1}};
    }
    throw new Error(`Unhandled D1 run SQL: ${sql}`);
  }

  async executeFirst(sql, values) {
    if (sql.startsWith('SELECT publication_id FROM dataset_publications WHERE publication_id = ?')) {
      const row = this.publications.get(values[0]);
      return row ? {publication_id: row.publication_id} : null;
    }
    if (sql.startsWith('SELECT publication_id, manifest_sha256, object_count, stored_bytes')) {
      return this.publications.get(values[0]) ?? null;
    }
    if (sql.startsWith('SELECT slug FROM dataset_channels WHERE is_default = 1')) {
      for (const row of this.channels.values()) {
        if (row.is_default === 1 && row.slug !== values[0]) return {slug: row.slug};
      }
      return null;
    }
    if (sql.startsWith('SELECT slug, publication_id, preview_asset_set_id, is_default')) {
      const row = this.channels.get(values[0]);
      return row
        ? {
            slug: row.slug,
            publication_id: row.publication_id,
            preview_asset_set_id: row.preview_asset_set_id,
            is_default: row.is_default,
          }
        : null;
    }
    throw new Error(`Unhandled D1 first SQL: ${sql}`);
  }

  async executeAll(sql) {
    if (sql.startsWith('SELECT c.slug,')) {
      const results = [...this.channels.values()]
        .filter(row => this.publications.has(row.publication_id))
        .sort((left, right) =>
          right.is_default - left.is_default ||
          left.display_name.localeCompare(right.display_name) ||
          left.slug.localeCompare(right.slug),
        );
      return {success: true, results: structuredClone(results)};
    }
    if (sql.startsWith('SELECT * FROM modpacks')) {
      return {success: true, results: structuredClone(this.modpacks)};
    }
    throw new Error(`Unhandled D1 all SQL: ${sql}`);
  }
}

function mrpi(packNumber, packBytes, entries) {
  const bytes = Buffer.alloc(20 + entries.length * 8);
  bytes.write('MRPI', 0, 'ascii');
  bytes.writeUInt16BE(1, 4);
  bytes.writeUInt16BE(20, 6);
  bytes.writeUInt32BE(packNumber, 8);
  bytes.writeUInt32BE(packBytes, 12);
  bytes.writeUInt32BE(entries.length, 16);
  for (const [index, [offset, length]] of entries.entries()) {
    bytes.writeUInt32BE(offset, 20 + index * 8);
    bytes.writeUInt32BE(length, 24 + index * 8);
  }
  return bytes;
}

function coreFixture() {
  const documents = new Map([
    ['items.json', jsonBytes({items: [{k: 'item|minecraft:stone'}]})],
    ['manifest.json', jsonBytes({publicationId: PUBLICATION, minecraft: '1.12.2', aborted: false})],
  ]);
  const pack = Buffer.from([1, 2, 3, 4, 5, 6, 7, 8]);
  const index = mrpi(0, pack.length, [[0, 4], [4, 4]]);
  const documentRecords = [...documents]
    .map(([path, bytes]) => ({path, bytes: bytes.length, sha256: sha256(bytes)}))
    .sort((left, right) => left.path.localeCompare(right.path));
  const manifest = {
    format: 'mrt-core-dataset-publication-v1',
    publicationId: PUBLICATION,
    maxDocumentBytes: 8 * 1024 * 1024,
    maxPackBytes: 1024 * 1024,
    packIndexFormat: 'mrt-packed-image-authorization-index-v1',
    maxPackIndexBytes: 512 * 1024,
    counts: {
      documents: 2,
      packs: 1,
      packedImages: 2,
      documentBytes: documentRecords.reduce((sum, record) => sum + record.bytes, 0),
      packBytes: pack.length,
      packIndexBytes: index.length,
      objects: 4,
      storedBytes:
        documentRecords.reduce((sum, record) => sum + record.bytes, 0) + pack.length + index.length,
    },
    documents: documentRecords,
    packs: [
      {
        path: 'assets/pack-000.bin',
        bytes: pack.length,
        sha256: sha256(pack),
        index: {
          path: 'indexes/pack-000.bin',
          bytes: index.length,
          sha256: sha256(index),
          entries: 2,
        },
      },
    ],
  };
  const publicationBytes = Buffer.from(canonicalCoreDatasetPublicationBytes(manifest));
  return {
    manifest,
    publicationBytes,
    publicationDigest: sha256(publicationBytes),
    objects: new Map([...documents, ['assets/pack-000.bin', pack], ['indexes/pack-000.bin', index]]),
  };
}

async function previewFixture(datasetPublicationId = PUBLICATION) {
  const pack = Buffer.from([21, 22, 23, 24, 25, 26, 27, 28]);
  const index = mrpi(0, pack.length, [[0, 4], [4, 4]]);
  const category = jsonBytes({
    format: 'mrt-recipe-preview-category-v1',
    categoryIndex: 0,
    categoryId: 'minecraft.crafting',
    count: 1,
    previews: [[0, 0, 4, 100, 60]],
  });
  const manifest = {
    format: 'mrt-recipe-preview-sidecar-v1',
    assetSetId: '0'.repeat(64),
    datasetPublicationId,
    maxPackBytes: 1024 * 1024,
    packIndexFormat: 'mrt-recipe-preview-pack-index-v1',
    maxPackIndexBytes: 512 * 1024,
    counts: {uniqueImages: 2, packIndexBytes: index.length, packs: 1, storedBytes: pack.length},
    packs: [
      {
        path: 'assets/pack-000.bin',
        bytes: pack.length,
        sha256: sha256(pack),
        index: {
          path: 'indexes/pack-000.bin',
          bytes: index.length,
          sha256: sha256(index),
          entries: 2,
        },
      },
    ],
    categoryDocuments: [
      {path: 'categories/000.json', bytes: category.length, sha256: sha256(category)},
    ],
  };
  manifest.assetSetId = await computePreviewAssetSetId(manifest);
  const manifestBytes = jsonBytes(manifest);
  return {
    assetSetId: manifest.assetSetId,
    manifest,
    objects: new Map([
      ['manifest.json', manifestBytes],
      ['assets/pack-000.bin', pack],
      ['indexes/pack-000.bin', index],
      ['categories/000.json', category],
    ]),
  };
}

function seedPreview(bucket, fixture) {
  for (const [path, bytes] of fixture.objects) {
    bucket.seed(`${fixture.assetSetId}/${path}`, bytes, {
      'mrt-asset-set': fixture.assetSetId,
      'mrt-dataset': fixture.manifest.datasetPublicationId,
      'mrt-path': path,
      'mrt-sha256': sha256(bytes),
    });
  }
}

function environment() {
  edgeCache.clear();
  return {
    DB: new MemoryD1(),
    PREVIEW_ASSETS: new MemoryR2(),
    CORE_DATASET_UPLOAD_TOKEN: TOKEN,
    ASSETS: {async fetch() { return new Response('app not found', {status: 404}); }},
  };
}

const edgeCache = new Map();
globalThis.caches = {
  default: {
    async match(request) {
      return edgeCache.get(new Request(request).url)?.clone() ?? undefined;
    },
    async put(request, response) {
      edgeCache.set(new Request(request).url, response.clone());
    },
  },
};

async function send(env, path, init) {
  const pending = [];
  const response = await worker.fetch(new Request(`${ORIGIN}${path}`, init), env, {
    waitUntil(operation) { pending.push(Promise.resolve(operation)); },
  });
  await Promise.all(pending);
  return response;
}

function adminHeaders(extra = {}) {
  return {Authorization: `Bearer ${TOKEN}`, 'X-MRT-Dataset-Publication-ID': PUBLICATION, ...extra};
}

function channelDeletionHeaders(publicationId, previewAssetSetId, extra = {}) {
  return {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Length': '0',
    'X-MRT-Expected-Dataset-Publication-ID': publicationId,
    'X-MRT-Expected-Preview-Asset-Set-ID': previewAssetSetId,
    ...extra,
  };
}

function activateChannel(env, slug, body) {
  const serialized = JSON.stringify(body);
  return send(env, `/api/admin/dataset-channels/${slug}/activate`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${TOKEN}`,
      'Content-Length': String(Buffer.byteLength(serialized)),
      'Content-Type': 'application/json',
    },
    body: serialized,
  });
}

async function beginCore(env, fixture) {
  return send(env, '/api/admin/core-datasets/begin', {
    method: 'POST',
    headers: adminHeaders({
      'Content-Length': String(fixture.publicationBytes.length),
      'Content-Type': 'application/json',
      'X-MRT-Content-SHA256': fixture.publicationDigest,
    }),
    body: fixture.publicationBytes,
  });
}

async function putCoreObject(env, path, bytes, digest = sha256(bytes)) {
  return send(env, `/api/admin/core-datasets/object/${path}`, {
    method: 'PUT',
    headers: adminHeaders({
      'Content-Length': String(bytes.length),
      'If-None-Match': '*',
      'X-MRT-Content-SHA256': digest,
    }),
    body: bytes,
  });
}

async function commitCore(env, fixture) {
  return send(env, '/api/admin/core-datasets/commit', {
    method: 'POST',
    headers: adminHeaders({
      'Content-Length': '0',
      'X-MRT-Content-SHA256': fixture.publicationDigest,
    }),
  });
}

async function publishCore(env, fixture = coreFixture()) {
  assert.equal((await beginCore(env, fixture)).status, 201);
  for (const [path, bytes] of fixture.objects) {
    assert.equal((await putCoreObject(env, path, bytes)).status, 201, path);
  }
  assert.equal((await commitCore(env, fixture)).status, 201);
  return fixture;
}

test('core ingestion authenticates, stages exact publication.json, commits its marker last, and registers D1', async () => {
  const env = environment();
  const fixture = coreFixture();
  const missingToken = await send(env, '/api/admin/core-datasets/status', {
    method: 'HEAD',
    headers: {'X-MRT-Dataset-Publication-ID': PUBLICATION},
  });
  assert.equal(missingToken.status, 401);

  assert.equal((await beginCore(env, fixture)).status, 201);
  const staged = await send(env, '/api/admin/core-datasets/status', {
    method: 'HEAD',
    headers: adminHeaders(),
  });
  assert.equal(staged.status, 200);
  assert.equal(staged.headers.get('x-mrt-publication-state'), 'staged');
  assert.equal(staged.headers.get('x-mrt-content-sha256'), fixture.publicationDigest);
  assert.equal(staged.headers.get('x-mrt-manifest-bytes'), String(fixture.publicationBytes.length));

  for (const [path, bytes] of fixture.objects) {
    assert.equal((await putCoreObject(env, path, bytes)).status, 201, path);
  }
  const response = await commitCore(env, fixture);
  assert.equal(response.status, 201);
  assert.equal((await response.json()).state, 'committed');
  const marker = `core/${PUBLICATION}/publication.json`;
  assert.equal(env.PREVIEW_ASSETS.writes.at(-1), marker, 'publication.json must be written last');
  assert.ok(env.DB.publications.has(PUBLICATION));
  assert.equal(env.PREVIEW_ASSETS.objects.has(`_staging/core/${PUBLICATION}/publication.json`), false);

  const committed = await send(env, '/api/admin/core-datasets/status', {
    method: 'HEAD',
    headers: adminHeaders(),
  });
  assert.equal(committed.status, 200);
  assert.equal(committed.headers.get('x-mrt-publication-state'), 'committed');
  assert.equal((await commitCore(env, fixture)).status, 200, 'commit is idempotent and rechecks D1');
});

test('core ingestion refuses undeclared objects, false digests, incomplete inventory, and control-path preemption', async () => {
  const env = environment();
  const fixture = coreFixture();
  assert.equal((await beginCore(env, fixture)).status, 201);
  const undeclared = await putCoreObject(env, 'private/secret.json', Buffer.from('{}\n'));
  assert.equal(undeclared.status, 400);
  const marker = await putCoreObject(env, 'publication.json', Buffer.from('{}\n'));
  assert.equal(marker.status, 400);
  const [firstPath, firstBytes] = fixture.objects.entries().next().value;
  const falseDigest = await putCoreObject(env, firstPath, firstBytes, 'f'.repeat(64));
  assert.equal(falseDigest.status, 400);
  assert.equal((await putCoreObject(env, firstPath, firstBytes)).status, 201);
  const incomplete = await commitCore(env, fixture);
  assert.equal(incomplete.status, 409);
  assert.equal(env.PREVIEW_ASSETS.objects.has(`core/${PUBLICATION}/publication.json`), false);
  assert.equal(env.DB.publications.size, 0);
});

test('immutable core reads require exact publication queries and MRPI-authorized R2 ranges', async () => {
  const env = environment();
  await publishCore(env);
  const base = `/dataset/publications/${PUBLICATION}/exports`;
  const malformed = await send(env, `${base}/items.json`);
  assert.equal(malformed.status, 400);
  const mismatch = await send(env, `${base}/items.json?dataset=${OTHER_PUBLICATION}`);
  assert.equal(mismatch.status, 400);

  const items = await send(env, `${base}/items.json?dataset=${PUBLICATION}`);
  assert.equal(items.status, 200);
  assert.match(items.headers.get('cache-control'), /immutable/);
  assert.equal((await items.json()).items[0].k, 'item|minecraft:stone');

  const unauthorized = await send(
    env,
    `${base}/assets/s/000-2-4.webp?dataset=${PUBLICATION}`,
  );
  assert.equal(unauthorized.status, 400);
  assert.equal(
    env.PREVIEW_ASSETS.reads.some(read => read.key.endsWith('/assets/pack-000.bin')),
    false,
    'MRPI membership must be checked before the pack range read',
  );
  const image = await send(env, `${base}/assets/s/000-4-4.webp?dataset=${PUBLICATION}`);
  assert.equal(image.status, 200);
  assert.deepEqual([...new Uint8Array(await image.arrayBuffer())], [5, 6, 7, 8]);
  const rangeRead = env.PREVIEW_ASSETS.reads.find(read => read.key.endsWith('/assets/pack-000.bin'));
  assert.deepEqual(rangeRead.range, {offset: 4, length: 4});
});

test('a pre-commit read is not negatively cached and corrupted R2 metadata fails closed', async () => {
  const env = environment();
  const fixture = coreFixture();
  const base = `/dataset/publications/${PUBLICATION}/exports`;
  const beforeCommit = await send(env, `${base}/manifest.json?dataset=${PUBLICATION}`);
  assert.equal(beforeCommit.status, 404);
  await publishCore(env, fixture);
  const afterCommit = await send(env, `${base}/manifest.json?dataset=${PUBLICATION}`);
  assert.equal(afterCommit.status, 200, 'a negative lookup must not survive the commit marker');

  edgeCache.clear();
  const itemKey = `core/${PUBLICATION}/items.json`;
  env.PREVIEW_ASSETS.objects.get(itemKey).customMetadata['mrt-sha256'] = 'f'.repeat(64);
  const corrupted = await send(env, `${base}/items.json?dataset=${PUBLICATION}`);
  assert.equal(corrupted.status, 502);
  assert.match(await corrupted.text(), /unavailable|invalid/i);
});

test('preview reads derive storage identity only from the immutable route and exact paired query', async () => {
  const env = environment();
  const fixture = await previewFixture();
  seedPreview(env.PREVIEW_ASSETS, fixture);
  env.PREVIEW_ASSET_SET_ID = 'f'.repeat(64);
  const base = `/dataset/preview-sets/${fixture.assetSetId}`;

  for (const path of [
    `${base}/manifest.json`,
    `${base}/manifest.json?dataset=${PUBLICATION}`,
    `${base}/manifest.json?preview=${fixture.assetSetId}&dataset=${PUBLICATION}`,
    `${base}/manifest.json?dataset=${OTHER_PUBLICATION}&preview=${fixture.assetSetId}`,
  ]) {
    const response = await send(env, path);
    assert.ok(response.status === 400 || response.status === 409, path);
  }
  const query = `?dataset=${PUBLICATION}&preview=${fixture.assetSetId}`;
  const manifest = await send(env, `${base}/manifest.json${query}`);
  assert.equal(manifest.status, 200);
  assert.equal((await manifest.json()).assetSetId, fixture.assetSetId);
  const category = await send(env, `${base}/categories/000.json${query}`);
  assert.equal(category.status, 200);
  const unauthorized = await send(env, `${base}/assets/s/000-2-4.webp${query}`);
  assert.equal(unauthorized.status, 400);
  const image = await send(env, `${base}/assets/s/000-4-4.webp${query}`);
  assert.equal(image.status, 200);
  assert.deepEqual([...new Uint8Array(await image.arrayBuffer())], [25, 26, 27, 28]);
  assert.equal(await send(env, `/dataset/previews/manifest.json${query}`).then(r => r.status), 410);
});

test('activation verifies the committed core/preview pair and publishes the exact catalog descriptor atomically', async () => {
  const env = environment();
  await publishCore(env);
  const preview = await previewFixture();
  seedPreview(env.PREVIEW_ASSETS, preview);
  const body = {
    displayName: '🧱'.repeat(120),
    minecraftVersion: '1.12.2',
    packVersion: '3.2.2',
    publicationId: PUBLICATION,
    previewAssetSetId: preview.assetSetId,
    isDefault: true,
    expectedPreviousPublicationId: null,
  };
  const response = await activateChannel(env, 'multiblock-madness', body);
  assert.equal(response.status, 200);
  const {expectedPreviousPublicationId: _expectedPreviousPublicationId, ...descriptor} = body;
  assert.deepEqual((await response.json()).dataset, {slug: 'multiblock-madness', ...descriptor});
  assert.ok(env.DB.batches.some(batch => batch.some(sql => sql.startsWith('INSERT INTO dataset_channels'))));

  const catalog = await send(env, '/api/datasets');
  assert.equal(catalog.status, 200);
  assert.deepEqual(await catalog.json(), {datasets: [{slug: 'multiblock-madness', ...descriptor}]});
  assert.equal(catalog.headers.get('cache-control'), 'no-store');
});

test('activation rejects invisible identity controls before publication verification', async () => {
  const env = environment();
  const response = await activateChannel(env, 'unsafe-identity', {
    displayName: 'Unsafe\u200bPack',
    minecraftVersion: '1.12.2',
    packVersion: '1',
    publicationId: PUBLICATION,
    previewAssetSetId: 'c'.repeat(64),
    isDefault: true,
    expectedPreviousPublicationId: null,
  });
  assert.equal(response.status, 400);
  assert.equal(env.DB.channels.size, 0);
});

test('activation fails closed for a sidecar bound to another core and never mutates the channel', async () => {
  const env = environment();
  await publishCore(env);
  const preview = await previewFixture(OTHER_PUBLICATION);
  seedPreview(env.PREVIEW_ASSETS, preview);
  const body = {
    displayName: 'Mismatched pack',
    minecraftVersion: '1.12.2',
    packVersion: '1',
    publicationId: PUBLICATION,
    previewAssetSetId: preview.assetSetId,
    isDefault: true,
    expectedPreviousPublicationId: null,
  };
  const response = await activateChannel(env, 'mismatch', body);
  assert.equal(response.status, 409);
  assert.equal(env.DB.channels.size, 0);
});

test('activation refuses an omitted expected previous channel state', async () => {
  const env = environment();
  const response = await activateChannel(env, 'missing-expectation', {
    displayName: 'Missing expectation',
    minecraftVersion: '1.12.2',
    packVersion: '1',
    publicationId: PUBLICATION,
    previewAssetSetId: 'c'.repeat(64),
    isDefault: true,
  });
  assert.equal(response.status, 400);
  assert.equal(env.DB.channels.size, 0);
});

test('activation requires create-only absence and refuses to overwrite an existing slug', async () => {
  const env = environment();
  await publishCore(env);
  const preview = await previewFixture();
  seedPreview(env.PREVIEW_ASSETS, preview);
  env.DB.channels.set('claimed-pack', {
    slug: 'claimed-pack',
    display_name: 'Claimed pack',
    minecraft_version: '1.12.2',
    pack_version: 'old',
    publication_id: OTHER_PUBLICATION,
    preview_asset_set_id: 'c'.repeat(64),
    is_default: 1,
    revision: 7,
  });

  const response = await activateChannel(env, 'claimed-pack', {
    displayName: 'Replacement',
    minecraftVersion: '1.12.2',
    packVersion: 'new',
    publicationId: PUBLICATION,
    previewAssetSetId: preview.assetSetId,
    isDefault: true,
    expectedPreviousPublicationId: null,
  });
  assert.equal(response.status, 409);
  assert.equal(env.DB.channels.get('claimed-pack').publication_id, OTHER_PUBLICATION);
  assert.equal(env.DB.channels.get('claimed-pack').is_default, 1);
  assert.equal(env.DB.channels.get('claimed-pack').revision, 7);
});

test('activation updates only the exact expected previous publication ID', async () => {
  const env = environment();
  await publishCore(env);
  const preview = await previewFixture();
  seedPreview(env.PREVIEW_ASSETS, preview);
  env.DB.channels.set('updatable-pack', {
    slug: 'updatable-pack',
    display_name: 'Old title',
    minecraft_version: '1.12.2',
    pack_version: '1',
    publication_id: PUBLICATION,
    preview_asset_set_id: preview.assetSetId,
    is_default: 1,
    revision: 3,
  });

  const response = await activateChannel(env, 'updatable-pack', {
    displayName: 'Updated title',
    minecraftVersion: '1.12.2',
    packVersion: '2',
    publicationId: PUBLICATION,
    previewAssetSetId: preview.assetSetId,
    isDefault: true,
    expectedPreviousPublicationId: PUBLICATION,
  });
  assert.equal(response.status, 200);
  assert.equal(env.DB.channels.get('updatable-pack').display_name, 'Updated title');
  assert.equal(env.DB.channels.get('updatable-pack').pack_version, '2');
  assert.equal(env.DB.channels.get('updatable-pack').revision, 4);
});

test('activation CAS preserves the previous default when the target changes at mutation time', async () => {
  const env = environment();
  await publishCore(env);
  const preview = await previewFixture();
  seedPreview(env.PREVIEW_ASSETS, preview);
  const stablePublication = 'e'.repeat(64);
  const racedPublication = 'f'.repeat(64);
  env.DB.channels.set('stable-default', {
    slug: 'stable-default',
    display_name: 'Stable default',
    minecraft_version: '1.12.2',
    pack_version: '1',
    publication_id: stablePublication,
    preview_asset_set_id: 'c'.repeat(64),
    is_default: 1,
    revision: 1,
  });
  env.DB.channels.set('raced-pack', {
    slug: 'raced-pack',
    display_name: 'Raced pack',
    minecraft_version: '1.12.2',
    pack_version: '1',
    publication_id: OTHER_PUBLICATION,
    preview_asset_set_id: 'd'.repeat(64),
    is_default: 0,
    revision: 1,
  });
  const executeRun = env.DB.executeRun.bind(env.DB);
  let raced = false;
  env.DB.executeRun = async (sql, values) => {
    if (!raced && sql.startsWith('UPDATE dataset_channels SET is_default = 0')) {
      raced = true;
      env.DB.channels.get('raced-pack').publication_id = racedPublication;
    }
    return executeRun(sql, values);
  };

  const response = await activateChannel(env, 'raced-pack', {
    displayName: 'Would-be replacement',
    minecraftVersion: '1.12.2',
    packVersion: '2',
    publicationId: PUBLICATION,
    previewAssetSetId: preview.assetSetId,
    isDefault: true,
    expectedPreviousPublicationId: OTHER_PUBLICATION,
  });
  assert.equal(response.status, 409);
  assert.equal(raced, true);
  assert.equal(env.DB.channels.get('stable-default').is_default, 1);
  assert.equal(env.DB.channels.get('raced-pack').publication_id, racedPublication);
  assert.equal(env.DB.channels.get('raced-pack').is_default, 0);
});

test('activation cannot demote the only default through a non-default update', async () => {
  const env = environment();
  await publishCore(env);
  const preview = await previewFixture();
  seedPreview(env.PREVIEW_ASSETS, preview);
  env.DB.channels.set('only-default', {
    slug: 'only-default',
    display_name: 'Only default',
    minecraft_version: '1.12.2',
    pack_version: '1',
    publication_id: PUBLICATION,
    preview_asset_set_id: preview.assetSetId,
    is_default: 1,
    revision: 2,
  });

  const response = await activateChannel(env, 'only-default', {
    displayName: 'Only default',
    minecraftVersion: '1.12.2',
    packVersion: '2',
    publicationId: PUBLICATION,
    previewAssetSetId: preview.assetSetId,
    isDefault: false,
    expectedPreviousPublicationId: PUBLICATION,
  });
  assert.equal(response.status, 409);
  assert.equal(env.DB.channels.get('only-default').is_default, 1);
  assert.equal(env.DB.channels.get('only-default').pack_version, '1');
  assert.equal(env.DB.channels.get('only-default').revision, 2);
});

test('authenticated deletion removes a non-default channel but never its immutable publications', async () => {
  const env = environment();
  env.DB.channels.set('meatballcraft', {
    slug: 'meatballcraft',
    display_name: 'MeatballCraft',
    minecraft_version: '1.12.2',
    pack_version: '0.18.5-hotfix2',
    publication_id: PUBLICATION,
    preview_asset_set_id: 'c'.repeat(64),
    is_default: 1,
  });
  env.DB.channels.set('multiblock-madness', {
    slug: 'multiblock-madness',
    display_name: 'Multiblock Madness',
    minecraft_version: '1.12.2',
    pack_version: '3.2.3',
    publication_id: OTHER_PUBLICATION,
    preview_asset_set_id: 'd'.repeat(64),
    is_default: 0,
  });
  env.DB.publications.set(PUBLICATION, {publication_id: PUBLICATION});
  env.DB.publications.set(OTHER_PUBLICATION, {publication_id: OTHER_PUBLICATION});

  const response = await send(env, '/api/admin/dataset-channels/multiblock-madness', {
    method: 'DELETE',
    headers: channelDeletionHeaders(OTHER_PUBLICATION, 'd'.repeat(64)),
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    deleted: {
      slug: 'multiblock-madness',
      publicationId: OTHER_PUBLICATION,
      previewAssetSetId: 'd'.repeat(64),
    },
  });
  assert.equal(env.DB.channels.has('multiblock-madness'), false);
  assert.equal(env.DB.publications.has(OTHER_PUBLICATION), true);
});

test('dataset channel deletion is an exact identity CAS and preserves a concurrently repointed channel', async () => {
  const env = environment();
  const preview = 'd'.repeat(64);
  env.DB.channels.set('multiblock-madness', {
    slug: 'multiblock-madness',
    display_name: 'Multiblock Madness',
    minecraft_version: '1.12.2',
    pack_version: '3.2.3',
    publication_id: OTHER_PUBLICATION,
    preview_asset_set_id: preview,
    is_default: 0,
  });

  const stale = await send(env, '/api/admin/dataset-channels/multiblock-madness', {
    method: 'DELETE',
    headers: channelDeletionHeaders(PUBLICATION, 'c'.repeat(64)),
  });
  assert.equal(stale.status, 409);
  assert.equal(env.DB.channels.get('multiblock-madness').publication_id, OTHER_PUBLICATION);

  const replacementPublication = 'e'.repeat(64);
  const replacementPreview = 'f'.repeat(64);
  const executeRun = env.DB.executeRun.bind(env.DB);
  let raced = false;
  env.DB.executeRun = async (sql, values) => {
    if (!raced && sql.startsWith('DELETE FROM dataset_channels WHERE slug = ?')) {
      raced = true;
      const row = env.DB.channels.get('multiblock-madness');
      row.publication_id = replacementPublication;
      row.preview_asset_set_id = replacementPreview;
    }
    return executeRun(sql, values);
  };
  const response = await send(env, '/api/admin/dataset-channels/multiblock-madness', {
    method: 'DELETE',
    headers: channelDeletionHeaders(OTHER_PUBLICATION, preview),
  });
  assert.equal(response.status, 409);
  assert.equal(raced, true);
  assert.equal(env.DB.channels.get('multiblock-madness').publication_id, replacementPublication);
  assert.equal(env.DB.channels.get('multiblock-madness').preview_asset_set_id, replacementPreview);
});

test('dataset channel deletion refuses the default, missing authentication, and non-empty bodies', async () => {
  const env = environment();
  env.DB.channels.set('meatballcraft', {
    slug: 'meatballcraft',
    display_name: 'MeatballCraft',
    minecraft_version: '1.12.2',
    pack_version: '0.18.5-hotfix2',
    publication_id: PUBLICATION,
    preview_asset_set_id: 'c'.repeat(64),
    is_default: 1,
  });
  assert.equal(
    await send(env, '/api/admin/dataset-channels/meatballcraft', {
      method: 'DELETE',
      headers: channelDeletionHeaders(PUBLICATION, 'c'.repeat(64)),
    }).then(response => response.status),
    409,
  );
  assert.equal(
    await send(env, '/api/admin/dataset-channels/meatballcraft', {
      method: 'DELETE',
      headers: {
        'Content-Length': '0',
        'X-MRT-Expected-Dataset-Publication-ID': PUBLICATION,
        'X-MRT-Expected-Preview-Asset-Set-ID': 'c'.repeat(64),
      },
    }).then(response => response.status),
    401,
  );
  assert.equal(
    await send(env, '/api/admin/dataset-channels/meatballcraft', {
      method: 'DELETE',
      headers: {
        Authorization: `Bearer ${TOKEN}`,
        'Content-Length': '1',
        'Content-Type': 'text/plain',
        'X-MRT-Expected-Dataset-Publication-ID': PUBLICATION,
        'X-MRT-Expected-Preview-Asset-Set-ID': 'c'.repeat(64),
      },
      body: 'x',
    }).then(response => response.status),
    400,
  );
  assert.equal(
    await send(env, '/api/admin/dataset-channels/meatballcraft', {
      method: 'DELETE',
      headers: {Authorization: `Bearer ${TOKEN}`, 'Content-Length': '0'},
    }).then(response => response.status),
    400,
  );
  assert.equal(env.DB.channels.has('meatballcraft'), true);
});

test('legacy modpack mutations are explicitly forbidden while read-only GET remains available', async () => {
  const env = environment();
  for (const method of ['POST', 'PATCH', 'DELETE']) {
    const path = method === 'POST' ? '/api/modpacks' : '/api/modpacks/legacy-id';
    const response = await send(env, path, {method});
    assert.equal(response.status, 403, method);
  }
  const response = await send(env, '/api/modpacks');
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {modpacks: []});
});

test('dataset catalog fails closed instead of returning an empty or multi-default contract', async () => {
  const env = environment();
  assert.equal((await send(env, '/api/datasets')).status, 503);
  env.DB.publications.set(PUBLICATION, {publication_id: PUBLICATION});
  env.DB.channels.set('one', {
    slug: 'one', display_name: 'One', minecraft_version: '1.12.2', pack_version: '1',
    publication_id: PUBLICATION, preview_asset_set_id: 'c'.repeat(64), is_default: 0,
  });
  assert.equal((await send(env, '/api/datasets')).status, 503);
});
