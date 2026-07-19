import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {chmod, mkdir, mkdtemp, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {
  coreDatasetContentRecords,
  coreDatasetPublicationManifestBytes,
  requireCoreDatasetPublicationManifest,
} from './core-dataset-publication-contract.mjs';
import {encodePackedImageAuthorizationIndex} from './packed-image-authorization.mjs';
import {
  readCoreDatasetIngestToken,
  uploadCoreDatasetPublication,
} from './upload-core-dataset-publication.mjs';

const BASE_URL = 'http://fixture.test/api/admin/core-datasets';
const TOKEN = 'core-dataset-test-token-that-is-long-enough';
const PUBLICATION_ID = 'a'.repeat(64);

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function loggerCapture() {
  const entries = [];
  return {
    entries,
    logger: {
      info: (...values) => entries.push(['info', values.join(' ')]),
      warn: (...values) => entries.push(['warn', values.join(' ')]),
      error: (...values) => entries.push(['error', values.join(' ')]),
    },
  };
}

async function fixture() {
  const root = await mkdtemp(join(tmpdir(), 'core-publication-upload-test-'));
  const exportRoot = join(root, 'exports');
  const bundleRoot = join(root, 'bundle');
  await mkdir(join(exportRoot, 'assets'), {recursive: true});
  await mkdir(join(bundleRoot, 'indexes'), {recursive: true});
  const documentBytes = Buffer.from(`${JSON.stringify({publicationId: PUBLICATION_ID})}\n`);
  const packBytes = Buffer.from('immutable-packed-image-bytes');
  const indexBytes = encodePackedImageAuthorizationIndex({
    packNumber: 0,
    packBytes: packBytes.length,
    entries: [[0, packBytes.length]],
  });
  const files = new Map([
    ['manifest.json', documentBytes],
    ['assets/pack-000.bin', packBytes],
    ['indexes/pack-000.bin', indexBytes],
  ]);
  for (const [path, bytes] of files) {
    const base = path.startsWith('indexes/') ? bundleRoot : exportRoot;
    const target = join(base, ...path.split('/'));
    await mkdir(join(target, '..'), {recursive: true});
    await writeFile(target, bytes);
  }
  const manifest = requireCoreDatasetPublicationManifest({
    format: 'mrt-core-dataset-publication-v1',
    publicationId: PUBLICATION_ID,
    maxDocumentBytes: 8 * 1024 * 1024,
    maxPackBytes: 1024 * 1024,
    packIndexFormat: 'mrt-packed-image-authorization-index-v1',
    maxPackIndexBytes: 512 * 1024,
    counts: {
      documents: 1,
      packs: 1,
      packedImages: 1,
      documentBytes: documentBytes.length,
      packBytes: packBytes.length,
      packIndexBytes: indexBytes.length,
      objects: 3,
      storedBytes: documentBytes.length + packBytes.length + indexBytes.length,
    },
    documents: [{path: 'manifest.json', bytes: documentBytes.length, sha256: sha256(documentBytes)}],
    packs: [{
      path: 'assets/pack-000.bin',
      bytes: packBytes.length,
      sha256: sha256(packBytes),
      index: {
        path: 'indexes/pack-000.bin',
        bytes: indexBytes.length,
        sha256: sha256(indexBytes),
        entries: 1,
      },
    }],
  });
  const manifestBytes = coreDatasetPublicationManifestBytes(manifest);
  const publicationPath = join(bundleRoot, 'publication.json');
  await writeFile(publicationPath, manifestBytes);
  const records = coreDatasetContentRecords(manifest).map(record => ({
    ...record,
    localPath: join(
      record.path.startsWith('indexes/') ? bundleRoot : exportRoot,
      ...record.path.split('/'),
    ),
  }));
  return {
    root,
    exportRoot,
    bundleRoot,
    publicationPath,
    manifest,
    manifestBytes,
    manifestSha256: sha256(manifestBytes),
    records,
    files,
    localValidator: async () => ({manifest, manifestBytes, records}),
  };
}

function createIngestionApi(data, options = {}) {
  const state = {
    staged: false,
    committed: false,
    objects: new Map(options.objects ?? []),
    events: [],
    headConflicts: new Map(options.headConflicts ?? []),
    registrationFailures: options.failRegistrationOnce ? 1 : 0,
  };
  const fetchImpl = async (input, init) => {
    const url = new URL(input);
    const headers = new Headers(init.headers);
    assert.equal(headers.get('authorization'), `Bearer ${TOKEN}`);
    assert.equal(headers.get('x-mrt-dataset-publication-id'), PUBLICATION_ID);
    const suffix = url.pathname.slice('/api/admin/core-datasets/'.length);
    const event = {method: init.method, operation: suffix};
    state.events.push(event);

    if (suffix === 'begin') {
      const body = Buffer.from(init.body);
      assert.deepEqual(body, data.manifestBytes);
      assert.equal(headers.get('x-mrt-content-sha256'), data.manifestSha256);
      assert.equal(headers.get('content-length'), String(data.manifestBytes.length));
      state.staged = true;
      return Response.json({state: state.committed ? 'published' : 'staging'}, {
        status: state.committed ? 200 : 201,
      });
    }
    if (suffix === 'status') {
      if (!state.staged) return new Response(null, {status: 404});
      return new Response(null, {
        status: 200,
        headers: {
          'x-mrt-content-sha256': data.manifestSha256,
          'x-mrt-manifest-bytes': String(data.manifestBytes.length),
          'x-mrt-dataset-publication-id': PUBLICATION_ID,
          'x-mrt-publication-state': state.committed ? 'committed' : 'staged',
        },
      });
    }
    if (suffix === 'commit') {
      assert.equal(headers.get('content-length'), '0');
      assert.equal(headers.get('x-mrt-content-sha256'), data.manifestSha256);
      assert.equal(state.objects.size, data.records.length);
      state.committed = true;
      if (state.registrationFailures > 0) {
        state.registrationFailures -= 1;
        return Response.json({error: 'D1 registration failed after R2 commit'}, {status: 503});
      }
      return Response.json({state: 'published'}, {status: 200});
    }
    if (!suffix.startsWith('object/')) return new Response(null, {status: 404});
    const path = suffix
      .slice('object/'.length)
      .split('/')
      .map(decodeURIComponent)
      .join('/');
    event.path = path;
    const declared = data.records.find(record => record.path === path);
    assert.ok(declared, path);
    if (init.method === 'HEAD') {
      const conflicts = state.headConflicts.get(path) ?? 0;
      if (conflicts > 0) {
        state.headConflicts.set(path, conflicts - 1);
        return new Response(null, {status: 409});
      }
      const stored = state.objects.get(path);
      if (!stored) return new Response(null, {status: 404});
      return new Response(null, {
        status: 200,
        headers: {
          'content-length': String(stored.bytes.length),
          'x-mrt-content-sha256': stored.sha256,
          'x-mrt-dataset-publication-id': PUBLICATION_ID,
        },
      });
    }
    assert.equal(init.method, 'PUT');
    assert.equal(headers.get('if-none-match'), '*');
    assert.equal(headers.get('content-length'), String(declared.bytes));
    assert.equal(headers.get('x-mrt-content-sha256'), declared.sha256);
    const bytes = Buffer.from(init.body);
    assert.equal(sha256(bytes), declared.sha256);
    if (options.dropPutPath === path) {
      return new Response(null, {status: 201});
    }
    if (options.racePath === path && !state.objects.has(path)) {
      state.objects.set(path, {bytes, sha256: declared.sha256});
      return new Response(null, {status: 412});
    }
    state.objects.set(path, {bytes, sha256: declared.sha256});
    return new Response(null, {status: 201});
  };
  return {state, fetchImpl};
}

async function runUpload(data, api, overrides = {}) {
  const capture = loggerCapture();
  const result = await uploadCoreDatasetPublication({
    exportRoot: data.exportRoot,
    publication: data.publicationPath,
    ingestBaseUrl: BASE_URL,
    token: TOKEN,
    concurrency: 2,
    logger: capture.logger,
    fetchImpl: api.fetchImpl,
    localValidator: data.localValidator,
    allowHttpForTests: true,
    sleepImpl: async () => {},
    ...overrides,
  });
  return {result, capture};
}

test('fresh upload verifies every immutable object and commits publication.json last', async () => {
  const data = await fixture();
  try {
    const api = createIngestionApi(data);
    const {result} = await runUpload(data, api);
    assert.deepEqual(result, {
      publicationId: PUBLICATION_ID,
      manifestSha256: data.manifestSha256,
      objects: 3,
      uploaded: 3,
      reused: 0,
      committed: true,
    });
    assert.equal(api.state.committed, true);
    assert.deepEqual([...api.state.objects.keys()].sort(), data.records.map(record => record.path).sort());
    const begin = api.state.events.findIndex(event => event.operation === 'begin');
    const firstPut = api.state.events.findIndex(event => event.method === 'PUT');
    const commit = api.state.events.findIndex(event => event.operation === 'commit');
    const lastHead = api.state.events.reduce(
      (last, event, index) => event.method === 'HEAD' && event.path ? index : last,
      -1,
    );
    assert.ok(begin >= 0 && begin < firstPut);
    assert.ok(lastHead < commit);
    assert.equal(api.state.events.at(-1).operation, 'status');
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('committed publication skips objects but replays commit for D1 reconciliation', async () => {
  const data = await fixture();
  try {
    const api = createIngestionApi(data);
    await runUpload(data, api);
    const boundary = api.state.events.length;
    const {result} = await runUpload(data, api);
    assert.equal(result.uploaded, 0);
    assert.equal(result.reused, 3);
    assert.deepEqual(
      api.state.events.slice(boundary).map(event => `${event.method}:${event.operation}`),
      ['POST:begin', 'HEAD:status', 'POST:commit', 'HEAD:status'],
    );
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('retry reconciles D1 after R2 marker succeeded but the first commit response failed', async () => {
  const data = await fixture();
  try {
    const api = createIngestionApi(data, {failRegistrationOnce: true});
    await assert.rejects(runUpload(data, api), /unexpected HTTP 503/);
    assert.equal(api.state.committed, true);
    const boundary = api.state.events.length;
    const {result} = await runUpload(data, api);
    assert.equal(result.committed, true);
    assert.equal(result.uploaded, 0);
    assert.equal(result.reused, 3);
    assert.deepEqual(
      api.state.events.slice(boundary).map(event => `${event.method}:${event.operation}`),
      ['POST:begin', 'HEAD:status', 'POST:commit', 'HEAD:status'],
    );
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('partial staging reuses exact objects and bounded retries resolve transient 409/412 races', async () => {
  const data = await fixture();
  try {
    const first = data.records[0];
    const race = data.records[1];
    const api = createIngestionApi(data, {
      objects: [[first.path, {bytes: data.files.get(first.path), sha256: first.sha256}]],
      headConflicts: [[first.path, 2]],
      racePath: race.path,
    });
    const delays = [];
    const {result, capture} = await runUpload(data, api, {
      sleepImpl: async delay => delays.push(delay),
    });
    assert.equal(result.uploaded, 1);
    assert.equal(result.reused, 2);
    assert.deepEqual(delays, [250, 500]);
    assert.ok(capture.entries.some(([level, message]) => level === 'warn' && message.includes('409')));
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('persistent consistency conflict exhausts bounded retries without PUT or commit', async () => {
  const data = await fixture();
  try {
    const target = data.records[0];
    const api = createIngestionApi(data, {headConflicts: [[target.path, 10]]});
    const delays = [];
    await assert.rejects(
      runUpload(data, api, {sleepImpl: async delay => delays.push(delay)}),
      /unexpected HTTP 409/,
    );
    assert.deepEqual(delays, [250, 500, 1_000, 2_000]);
    assert.equal(api.state.events.some(event => event.method === 'PUT'), false);
    assert.equal(api.state.events.some(event => event.operation === 'commit'), false);
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('persistent post-PUT absence exhausts bounded retries and never commits', async () => {
  const data = await fixture();
  try {
    const target = data.records[0];
    const api = createIngestionApi(data, {dropPutPath: target.path});
    const delays = [];
    await assert.rejects(
      runUpload(data, api, {sleepImpl: async delay => delays.push(delay)}),
      /succeeded or raced, but the exact object is still absent/,
    );
    assert.deepEqual(delays, [250, 500, 1_000, 2_000]);
    assert.equal(api.state.events.some(event => event.operation === 'commit'), false);
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('status metadata mismatch and credential-bearing URLs fail closed before content writes', async () => {
  const data = await fixture();
  try {
    const api = createIngestionApi(data);
    const originalFetch = api.fetchImpl;
    api.fetchImpl = async (url, init) => {
      const response = await originalFetch(url, init);
      if (new URL(url).pathname.endsWith('/status')) {
        const headers = new Headers(response.headers);
        headers.set('x-mrt-content-sha256', 'b'.repeat(64));
        return new Response(null, {status: response.status, headers});
      }
      return response;
    };
    await assert.rejects(runUpload(data, api), /x-mrt-content-sha256/);
    assert.equal(api.state.events.some(event => event.method === 'PUT'), false);
    assert.equal(api.state.events.some(event => event.operation === 'commit'), false);

    await assert.rejects(
      uploadCoreDatasetPublication({
        exportRoot: data.exportRoot,
        publication: data.publicationPath,
        ingestBaseUrl: 'https://operator:secret@fixture.test/api/admin/core-datasets',
        token: TOKEN,
        localValidator: data.localValidator,
      }),
      /must not contain credentials/,
    );
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('local object mutation aborts before that PUT and before commit', async () => {
  const data = await fixture();
  try {
    const target = data.records[0];
    const api = createIngestionApi(data);
    const originalFetch = api.fetchImpl;
    let changed = false;
    api.fetchImpl = async (url, init) => {
      const parsed = new URL(url);
      if (!changed && init.method === 'HEAD' && parsed.pathname.endsWith(target.path)) {
        const response = await originalFetch(url, init);
        await writeFile(target.localPath, 'changed after validation\n');
        changed = true;
        return response;
      }
      return originalFetch(url, init);
    };
    await assert.rejects(runUpload(data, api), /changed after validation/);
    assert.equal(api.state.events.some(event => event.operation === 'commit'), false);
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('transport failures redact bearer tokens from errors and logs', async () => {
  const data = await fixture();
  try {
    const capture = loggerCapture();
    await assert.rejects(
      uploadCoreDatasetPublication({
        exportRoot: data.exportRoot,
        publication: data.publicationPath,
        ingestBaseUrl: BASE_URL,
        token: TOKEN,
        logger: capture.logger,
        fetchImpl: async () => {
          throw new Error(`failed request Authorization: Bearer ${TOKEN}`);
        },
        localValidator: data.localValidator,
        allowHttpForTests: true,
      }),
      error => {
        assert.doesNotMatch(error.message, new RegExp(TOKEN));
        assert.match(error.message, /\[REDACTED\]/);
        assert.equal(error.cause, undefined);
        return true;
      },
    );
    assert.doesNotMatch(JSON.stringify(capture.entries), new RegExp(TOKEN));
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('token reader enforces explicit environment input and private plain token files', async () => {
  assert.equal(
    await readCoreDatasetIngestToken({env: {CORE_DATASET_UPLOAD_TOKEN: TOKEN}}),
    TOKEN,
  );
  await assert.rejects(readCoreDatasetIngestToken({env: {}}), /CORE_DATASET_UPLOAD_TOKEN/);
  const root = await mkdtemp(join(tmpdir(), 'core-publication-token-test-'));
  try {
    const path = join(root, 'token');
    await writeFile(path, `${TOKEN}\n`, {mode: 0o600});
    assert.equal(await readCoreDatasetIngestToken({tokenFile: path, env: {}}), TOKEN);
    if (process.platform !== 'win32') {
      await chmod(path, 0o644);
      await assert.rejects(readCoreDatasetIngestToken({tokenFile: path}), /group\/other/);
    }
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
