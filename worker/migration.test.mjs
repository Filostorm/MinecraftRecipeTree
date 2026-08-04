import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import test from 'node:test';
import {MIGRATION_BASE_PATH, handleStorageMigration} from './migration.ts';

const ORIGIN = 'https://migration.example';
const EXPORT_TOKEN = 'export-token-'.padEnd(48, 'x');
const IMPORT_TOKEN = 'import-token-'.padEnd(48, 'y');

function digest(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function checksumBuffer(hex) {
  return Uint8Array.fromHex(hex).buffer;
}

function encodeMetadata(value) {
  return Buffer.from(JSON.stringify(value)).toString('base64url');
}

async function request(path, runtime, init = {}) {
  const url = new URL(`${ORIGIN}${MIGRATION_BASE_PATH}${path}`);
  return handleStorageMigration(new Request(url, init), runtime, url);
}

function authorized(token, headers = {}) {
  return {Authorization: `Bearer ${token}`, ...headers};
}

class MemoryStatement {
  values = [];

  constructor(database, sql) {
    this.database = database;
    this.sql = sql.replace(/\s+/gu, ' ').trim();
  }

  bind(...values) {
    this.values = values;
    return this;
  }

  async first() {
    const match = /^SELECT COUNT\(\*\) AS count FROM ([a-z_]+)$/u.exec(this.sql);
    if (!match) throw new Error(`Unexpected first SQL: ${this.sql}`);
    return {count: this.database.tables[match[1]].length};
  }

  async all() {
    const match = /^SELECT .+ FROM ([a-z_]+) WHERE ([a-z_]+) > \? ORDER BY \2 LIMIT \?$/u.exec(this.sql);
    if (!match) throw new Error(`Unexpected all SQL: ${this.sql}`);
    const [after, limit] = this.values;
    const rows = this.database.tables[match[1]]
      .filter(row => row[match[2]] > after)
      .sort((left, right) => left[match[2]].localeCompare(right[match[2]]))
      .slice(0, limit);
    return {success: true, results: structuredClone(rows)};
  }
}

class MemoryD1 {
  tables = {
    dataset_publications: [{publication_id: 'publication-a', manifest_sha256: 'a'.repeat(64), object_count: 2, stored_bytes: 12, committed_at: '2026-08-04T00:00:00.000Z'}],
    dataset_channels: [{slug: 'pack', display_name: 'Pack', minecraft_version: '1.20.1', pack_version: '1.0.0', publication_id: 'publication-a', preview_asset_set_id: null, is_default: 1, revision: 1, activated_at: '2026-08-04T00:00:00.000Z'}],
    modpacks: [], feedback_reports: [], export_failure_reports: [],
  };

  prepare(sql) {
    return new MemoryStatement(this, sql);
  }
}

function objectView(key, stored, withBody = false) {
  return {
    key,
    size: stored.bytes.byteLength,
    etag: stored.sha256.slice(0, 32),
    customMetadata: structuredClone(stored.customMetadata),
    httpMetadata: structuredClone(stored.httpMetadata),
    storageClass: stored.storageClass,
    checksums: {sha256: checksumBuffer(stored.sha256)},
    ...(withBody ? {body: new Blob([stored.bytes]).stream()} : {}),
  };
}

class MemoryR2 {
  objects = new Map();
  puts = 0;

  async head(key) {
    const stored = this.objects.get(key);
    return stored ? objectView(key, stored) : null;
  }

  async get(key) {
    const stored = this.objects.get(key);
    return stored ? objectView(key, stored, true) : null;
  }

  async put(key, body, options) {
    if (this.objects.has(key) && options.onlyIf?.etagDoesNotMatch === '*') return null;
    const bytes = Buffer.from(await new Response(body).arrayBuffer());
    const sha256 = digest(bytes);
    if (sha256 !== options.sha256) throw new Error('checksum mismatch');
    const stored = {
      bytes, sha256,
      customMetadata: structuredClone(options.customMetadata),
      httpMetadata: structuredClone(options.httpMetadata),
      storageClass: options.storageClass ?? 'Standard',
    };
    this.objects.set(key, stored);
    this.puts += 1;
    return objectView(key, stored);
  }

  async list({limit, cursor}) {
    const keys = [...this.objects.keys()].sort();
    const start = cursor ? Number(cursor) : 0;
    const selected = keys.slice(start, start + limit);
    const next = start + selected.length;
    return {
      objects: selected.map(key => objectView(key, this.objects.get(key))),
      truncated: next < keys.length,
      ...(next < keys.length ? {cursor: String(next)} : {}),
    };
  }

  seed(key, body, options = {}) {
    const bytes = Buffer.from(body);
    this.objects.set(key, {
      bytes,
      sha256: digest(bytes),
      customMetadata: structuredClone(options.customMetadata ?? {}),
      httpMetadata: structuredClone(options.httpMetadata ?? {}),
      storageClass: options.storageClass ?? 'Standard',
    });
  }
}

function runtime(overrides = {}) {
  return {
    DB: new MemoryD1(), PREVIEW_ASSETS: new MemoryR2(),
    MIGRATION_EXPORT_TOKEN: EXPORT_TOKEN,
    MIGRATION_IMPORT_TOKEN: IMPORT_TOKEN,
    ...overrides,
  };
}

test('migration routes fail closed when a token is absent or incorrect', async () => {
  const disabled = await request('database-summary', runtime({MIGRATION_EXPORT_TOKEN: undefined}));
  assert.equal(disabled.status, 503);
  const unauthorized = await request('database-summary', runtime(), {
    headers: authorized('incorrect-token-'.padEnd(48, 'z')),
  });
  assert.equal(unauthorized.status, 401);
  assert.match(unauthorized.headers.get('www-authenticate'), /storage-migration-export/u);
});

test('database summary and allowlisted keyset pages export deterministic rows', async () => {
  const env = runtime();
  const summary = await request('database-summary', env, {headers: authorized(EXPORT_TOKEN)});
  assert.equal(summary.status, 200);
  assert.deepEqual((await summary.json()).counts, {
    dataset_publications: 1, dataset_channels: 1, modpacks: 0,
    feedback_reports: 0, export_failure_reports: 0,
  });
  const page = await request('database?table=dataset_publications', env, {
    headers: authorized(EXPORT_TOKEN),
  });
  assert.equal(page.status, 200);
  const payload = await page.json();
  assert.equal(payload.rows[0].publication_id, 'publication-a');
  assert.equal(payload.nextAfter, null);
  const rejected = await request('database?table=dataset_publications&table=modpacks', env, {
    headers: authorized(EXPORT_TOKEN),
  });
  assert.equal(rejected.status, 400);
});

test('object inventory and downloads preserve R2 migration metadata', async () => {
  const env = runtime();
  env.PREVIEW_ASSETS.seed('datasets/a/manifest.json', 'manifest', {
    customMetadata: {publication: 'a'},
    httpMetadata: {contentType: 'application/json', cacheControl: 'public, max-age=31536000'},
    storageClass: 'InfrequentAccess',
  });
  const list = await request('objects', env, {headers: authorized(EXPORT_TOKEN)});
  const inventory = await list.json();
  assert.equal(inventory.objects[0].key, 'datasets/a/manifest.json');
  assert.deepEqual(inventory.objects[0].customMetadata, {publication: 'a'});
  const response = await request('object?key=datasets%2Fa%2Fmanifest.json', env, {
    headers: authorized(EXPORT_TOKEN),
  });
  assert.equal(await response.text(), 'manifest');
  assert.equal(response.headers.get('x-mrt-migration-bytes'), '8');
  assert.deepEqual(
    JSON.parse(Buffer.from(response.headers.get('x-mrt-migration-http-metadata'), 'base64url')),
    {contentType: 'application/json', cacheControl: 'public, max-age=31536000'},
  );
});

test('object imports enforce SHA-256 and resume without rewriting identical data', async () => {
  const env = runtime();
  const body = Buffer.from('immutable object');
  const sha256 = digest(body);
  const headers = authorized(IMPORT_TOKEN, {
    'Content-Length': String(body.byteLength),
    'x-mrt-migration-bytes': String(body.byteLength),
    'x-mrt-migration-sha256': sha256,
    'x-mrt-migration-custom-metadata': encodeMetadata({publication: 'a'}),
    'x-mrt-migration-http-metadata': encodeMetadata({contentType: 'application/octet-stream'}),
    'x-mrt-migration-storage-class': 'Standard',
  });
  const first = await request('object?key=datasets%2Fa%2Fpart.bin', env, {
    method: 'PUT', headers, body,
  });
  assert.equal(first.status, 201);
  assert.equal((await first.json()).reused, false);
  const resumed = await request('object?key=datasets%2Fa%2Fpart.bin', env, {
    method: 'PUT', headers, body,
  });
  assert.equal(resumed.status, 200);
  assert.equal((await resumed.json()).reused, true);
  assert.equal(env.PREVIEW_ASSETS.puts, 1);
  const conflictBody = Buffer.from('different body');
  const conflict = await request('object?key=datasets%2Fa%2Fpart.bin', env, {
    method: 'PUT',
    headers: {...headers, 'Content-Length': String(conflictBody.byteLength), 'x-mrt-migration-bytes': String(conflictBody.byteLength), 'x-mrt-migration-sha256': digest(conflictBody)},
    body: conflictBody,
  });
  assert.equal(conflict.status, 409);
});
