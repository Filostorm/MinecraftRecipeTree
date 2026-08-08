import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {chmod, mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, join} from 'node:path';
import test from 'node:test';
import {
  readPreviewIngestToken,
  uploadRecipePreviewSidecar,
} from './upload-recipe-preview-sidecar.mjs';

const ASSET_SET_ID = 'a'.repeat(64);
const DATASET_PUBLICATION_ID = 'b'.repeat(64);
const TOKEN = 'operator-token-'.repeat(4);
const BASE_URL = 'http://preview.test/api/admin/preview-assets';
const SHA256_HEADER = 'x-mrt-content-sha256';
const DATASET_HEADER = 'x-mrt-dataset-publication-id';
const CACHE_CONTROL = 'public, max-age=31536000, immutable, no-transform';

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function dataOnlyAssetSetId() {
  const hash = createHash('sha256');
  hash.update('mrt-recipe-preview-sidecar-v2\0');
  const datasetBytes = Buffer.from(DATASET_PUBLICATION_ID, 'utf8');
  const length = Buffer.alloc(8);
  length.writeBigUInt64BE(BigInt(datasetBytes.length));
  hash.update(length).update(datasetBytes);
  return hash.digest('hex');
}

function record(path, bytes) {
  return {path, bytes: bytes.length, sha256: sha256(bytes)};
}

async function fixture() {
  const root = await mkdtemp(join(tmpdir(), 'mrt-preview-upload-'));
  const files = new Map([
    ['assets/pack-000.bin', Buffer.from([1, 2, 3, 4])],
    ['indexes/pack-000.bin', Buffer.from([5, 6, 7])],
    ['categories/000.json', Buffer.from('{"previews":[]}\n')],
  ]);
  for (const [path, bytes] of files) {
    const target = join(root, ...path.split('/'));
    await mkdir(dirname(target), {recursive: true});
    await writeFile(target, bytes);
  }
  const pack = {
    ...record('assets/pack-000.bin', files.get('assets/pack-000.bin')),
    index: {
      ...record('indexes/pack-000.bin', files.get('indexes/pack-000.bin')),
      entries: 1,
    },
  };
  const manifest = {
    assetSetId: ASSET_SET_ID,
    datasetPublicationId: DATASET_PUBLICATION_ID,
    packs: [pack],
    categoryDocuments: [record('categories/000.json', files.get('categories/000.json'))],
  };
  const manifestBytes = Buffer.from(`${JSON.stringify(manifest)}\n`);
  await writeFile(join(root, 'manifest.json'), manifestBytes);
  const records = [
    {...pack, index: undefined},
    pack.index,
    ...manifest.categoryDocuments,
  ].map(({path, bytes, sha256: digest}) => ({path, bytes, sha256: digest}));
  return {
    root,
    files,
    records,
    manifest,
    manifestBytes,
    localValidator: async () => ({root, manifest, manifestBytes}),
  };
}

async function dataOnlyFixture() {
  const root = await mkdtemp(join(tmpdir(), 'mrt-preview-upload-data-only-'));
  const manifest = {
    format: 'mrt-recipe-preview-sidecar-v2',
    publicationPolicy: 'gtnh-structured-data-only-v1',
    exclusionReason: 'third-party-artwork-rights-not-cleared',
    assetSetId: dataOnlyAssetSetId(),
    datasetPublicationId: DATASET_PUBLICATION_ID,
    counts: {
      categories: 1,
      recipes: 10,
      previews: 0,
      missing: 10,
      uniqueImages: 0,
      duplicates: 0,
      packs: 0,
      inputBytes: 0,
      hostedOmittedPngBytes: 500,
      encodedBytes: 0,
      storedBytes: 0,
      packIndexBytes: 0,
    },
    packs: [],
    mapping: {documents: 0, parts: 0, bytes: 0},
    categoryDocuments: [],
  };
  const manifestBytes = Buffer.from(`${JSON.stringify(manifest)}\n`);
  await writeFile(join(root, 'manifest.json'), manifestBytes);
  return {
    root,
    files: new Map(),
    records: [],
    manifest,
    manifestBytes,
    localValidator: async () => ({root, manifest, manifestBytes}),
  };
}

function responseHeadersForObject(object) {
  return {
    'content-length': String(object.bytes.length),
    [SHA256_HEADER]: object.sha256,
    [DATASET_HEADER]: DATASET_PUBLICATION_ID,
  };
}

function createIngestionApi(fixtureState, options = {}) {
  const state = {
    stagedManifest: null,
    committed: false,
    objects: new Map(options.objects ?? []),
    events: [],
  };
  const expected = new Map(fixtureState.records.map(value => [value.path, value]));

  async function fetchImpl(url, init) {
    const parsed = new URL(url);
    const method = init.method ?? 'GET';
    const headers = new Headers(init.headers);
    const segments = parsed.pathname
      .slice('/api/admin/preview-assets/'.length)
      .split('/')
      .map(decodeURIComponent);
    const [assetSetId, operation, ...tail] = segments;
    state.events.push({method, operation, path: tail.join('/'), headers});
    assert.equal(init.redirect, 'error');
    assert.equal(init.cache, 'no-store');
    if (headers.get('authorization') !== `Bearer ${TOKEN}`) {
      return new Response(null, {status: 401});
    }
    if (assetSetId !== fixtureState.manifest.assetSetId) return new Response(null, {status: 404});

    if (operation === 'begin' && method === 'POST') {
      const bytes = Buffer.from(await new Response(init.body).arrayBuffer());
      if (
        headers.get(SHA256_HEADER) !== sha256(bytes) ||
        headers.get(DATASET_HEADER) !== DATASET_PUBLICATION_ID ||
        headers.get('content-length') !== String(bytes.length)
      ) {
        return new Response(null, {status: 422});
      }
      if (state.stagedManifest && !state.stagedManifest.equals(bytes)) {
        return new Response(null, {status: 409});
      }
      const resumed = !!state.stagedManifest;
      state.stagedManifest = bytes;
      return new Response(null, {status: resumed ? 200 : 201});
    }

    if (operation === 'status' && method === 'HEAD') {
      if (!state.stagedManifest) return new Response(null, {status: 404});
      return new Response(null, {
        status: 200,
        headers: {
          [SHA256_HEADER]: options.statusSha256 ?? sha256(state.stagedManifest),
          'x-mrt-manifest-bytes': String(state.stagedManifest.length),
          [DATASET_HEADER]: DATASET_PUBLICATION_ID,
          'x-mrt-publication-state': state.committed ? 'committed' : 'staged',
        },
      });
    }

    if (operation === 'objects' && tail.length > 0) {
      const path = tail.join('/');
      const stored = state.objects.get(path);
      if (method === 'HEAD') {
        const responseHeaders = stored ? responseHeadersForObject(stored) : null;
        if (responseHeaders && options.cloudflareHeadNormalization) {
          responseHeaders['content-length'] = '0';
          responseHeaders['x-mrt-content-bytes'] = String(stored.bytes.length);
        }
        return stored
          ? new Response(null, {status: 200, headers: responseHeaders})
          : new Response(null, {status: 404});
      }
      if (method === 'PUT') {
        assert.equal(headers.get('if-none-match'), '*');
        assert.equal(headers.get('cache-control'), CACHE_CONTROL);
        if (stored) return new Response(null, {status: options.raceStatus ?? 409});
        const declared = expected.get(path);
        if (!declared) return new Response(null, {status: 400});
        const bytes = Buffer.from(await new Response(init.body).arrayBuffer());
        const digest = sha256(bytes);
        if (
          bytes.length !== declared.bytes ||
          digest !== declared.sha256 ||
          headers.get('content-length') !== String(declared.bytes) ||
          headers.get(SHA256_HEADER) !== declared.sha256 ||
          headers.get(DATASET_HEADER) !== DATASET_PUBLICATION_ID
        ) {
          return new Response(null, {status: 422});
        }
        state.objects.set(path, {bytes, sha256: digest});
        return new Response(null, {status: 201});
      }
    }

    if (operation === 'commit' && method === 'POST') {
      if (init.body !== undefined && init.body !== null) return new Response(null, {status: 400});
      if (
        headers.get('content-length') !== '0' ||
        [...expected].some(([path, declared]) => {
          const stored = state.objects.get(path);
          return !stored || stored.bytes.length !== declared.bytes || stored.sha256 !== declared.sha256;
        })
      ) {
        return new Response(null, {status: 409});
      }
      const resumed = state.committed;
      state.committed = true;
      return new Response(null, {status: resumed ? 200 : 201});
    }

    return new Response(null, {status: 404});
  }

  return {state, fetchImpl};
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

function deferred() {
  let resolvePromise;
  const promise = new Promise(resolve => {
    resolvePromise = resolve;
  });
  return {promise, resolve: resolvePromise};
}

async function waitForCondition(predicate, label) {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    if (predicate()) return;
    await new Promise(resolve => setImmediate(resolve));
  }
  assert.fail(`Timed out waiting for ${label}.`);
}

function serializedErrorChain(error) {
  const entries = [];
  const seen = new Set();
  let current = error;
  while (current !== undefined && current !== null && !seen.has(current)) {
    seen.add(current);
    if (current instanceof Error) {
      entries.push({name: current.name, message: current.message, stack: current.stack});
      current = current.cause;
    } else {
      entries.push({value: String(current)});
      break;
    }
  }
  return JSON.stringify(entries);
}

async function runUpload(fixtureState, api, overrides = {}) {
  return uploadRecipePreviewSidecar({
    local: fixtureState.root,
    ingestBaseUrl: BASE_URL,
    token: TOKEN,
    concurrency: 2,
    logger: loggerCapture().logger,
    fetchImpl: api.fetchImpl,
    localValidator: fixtureState.localValidator,
    allowHttpForTests: true,
    ...overrides,
  });
}

test('fresh upload stages the manifest, verifies immutable objects, and commits manifest last', async () => {
  const data = await fixture();
  try {
    const api = createIngestionApi(data);
    const result = await runUpload(data, api);
    assert.deepEqual(result, {
      assetSetId: ASSET_SET_ID,
      datasetPublicationId: DATASET_PUBLICATION_ID,
      objects: 3,
      uploaded: 3,
      reused: 0,
      committed: true,
    });
    assert.equal(api.state.committed, true);
    assert.deepEqual([...api.state.objects.keys()].sort(), data.records.map(value => value.path).sort());
    assert.equal(api.state.objects.has('manifest.json'), false);
    const begin = api.state.events.findIndex(event => event.operation === 'begin');
    const firstPut = api.state.events.findIndex(event => event.method === 'PUT');
    const commit = api.state.events.findIndex(event => event.operation === 'commit');
    const lastObjectHead = api.state.events.reduce(
      (last, event, index) => event.operation === 'objects' && event.method === 'HEAD' ? index : last,
      -1,
    );
    assert.ok(begin >= 0 && begin < firstPut);
    assert.ok(lastObjectHead < commit);
    assert.equal(api.state.events.at(-1).operation, 'status');
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('bounded workers stop dequeuing on the first failure and drain active secondary failures', async () => {
  const data = await fixture();
  const gates = [];
  let uploadPromise = Promise.resolve();
  try {
    const api = createIngestionApi(data);
    const originalFetch = api.fetchImpl;
    const initialHeads = new Set();
    let active = 0;
    let peak = 0;
    api.fetchImpl = async (input, init) => {
      const url = new URL(input);
      const encodedPath = url.pathname.split('/objects/')[1];
      if (init.method === 'HEAD' && encodedPath) {
        const path = encodedPath.split('/').map(decodeURIComponent).join('/');
        if (!initialHeads.has(path)) {
          initialHeads.add(path);
          const gate = {...deferred(), failure: null, path};
          gates.push(gate);
          active += 1;
          peak = Math.max(peak, active);
          try {
            await gate.promise;
            if (gate.failure) throw gate.failure;
          } finally {
            active -= 1;
          }
        }
      }
      return originalFetch(input, init);
    };

    const capture = loggerCapture();
    let settled = false;
    let outcome;
    uploadPromise = runUpload(data, api, {logger: capture.logger}).then(
      value => {
        settled = true;
        outcome = {value};
      },
      error => {
        settled = true;
        outcome = {error};
      },
    );

    await waitForCondition(() => gates.length === 2, 'two active preview upload workers');
    assert.equal(data.records.length > 2, true);
    assert.equal(active, 2);
    assert.equal(peak, 2);

    gates[0].failure = new Error('primary preview worker failure');
    gates[0].resolve();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(settled, false, 'the uploader must wait for the other active worker');
    assert.equal(active, 1);
    assert.equal(gates.length, 2, 'the queued third record must not start');

    gates[1].failure = new Error('secondary preview worker failure');
    gates[1].resolve();
    await uploadPromise;

    assert.match(outcome.error?.message ?? '', /primary preview worker failure$/);
    assert.doesNotMatch(outcome.error?.message ?? '', /secondary preview worker failure/);
    assert.equal(active, 0);
    assert.equal(peak, 2);
    assert.equal(gates.length, 2);
    assert.equal(api.state.committed, false);
    assert.equal(api.state.events.some(event => event.operation === 'commit'), false);
    const errors = capture.entries
      .filter(([level]) => level === 'error')
      .map(([, message]) => message);
    assert.equal(errors.some(message => message.includes('drained 1 secondary failure')), true);
    assert.equal(errors.some(message => message.includes('secondary preview worker failure')), true);
  } finally {
    for (const gate of gates) gate.resolve();
    await uploadPromise;
    await rm(data.root, {recursive: true, force: true});
  }
});

test('preview upload rejects concurrency outside 1..32 before validation or network access', async () => {
  const data = await fixture();
  try {
    let validations = 0;
    let requests = 0;
    for (const concurrency of [0, 33]) {
      await assert.rejects(
        uploadRecipePreviewSidecar({
          local: data.root,
          ingestBaseUrl: BASE_URL,
          token: TOKEN,
          concurrency,
          localValidator: async () => {
            validations += 1;
            return data.localValidator();
          },
          fetchImpl: async () => {
            requests += 1;
            throw new Error('network must not be reached');
          },
          allowHttpForTests: true,
        }),
        /concurrency must be within 1\.\.32/,
      );
    }
    assert.equal(validations, 0);
    assert.equal(requests, 0);
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('data-only upload commits exactly one manifest and logs the explicit rights exclusion', async () => {
  const data = await dataOnlyFixture();
  try {
    const api = createIngestionApi(data);
    const capture = loggerCapture();
    const result = await runUpload(data, api, {logger: capture.logger});
    assert.deepEqual(result, {
      assetSetId: data.manifest.assetSetId,
      datasetPublicationId: DATASET_PUBLICATION_ID,
      objects: 0,
      uploaded: 0,
      reused: 0,
      committed: true,
    });
    assert.equal(api.state.objects.size, 0);
    assert.equal(
      capture.entries.some(([level, message]) =>
        level === 'warn' && message.includes('excludes all recipe preview objects')),
      true,
    );

    const ordinary = structuredClone(data.manifest);
    ordinary.format = 'mrt-recipe-preview-sidecar-v1';
    delete ordinary.publicationPolicy;
    delete ordinary.exclusionReason;
    await assert.rejects(
      runUpload({...data, manifest: ordinary, localValidator: async () => ({
        root: data.root,
        manifest: ordinary,
        manifestBytes: data.manifestBytes,
      })}, createIngestionApi(data)),
      /Ordinary v1 preview sidecars cannot use the manifest-only data-only upload branch/,
    );

    const drifted = structuredClone(data.manifest);
    drifted.counts.uniqueImages = 1;
    await assert.rejects(
      runUpload({...data, manifest: drifted, localValidator: async () => ({
        root: data.root,
        manifest: drifted,
        manifestBytes: data.manifestBytes,
      })}, createIngestionApi(data)),
      /drifted from the exact manifest-only GTNH rights-exclusion contract/,
    );
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('a committed exact publication resumes without object or commit requests', async () => {
  const data = await fixture();
  try {
    const api = createIngestionApi(data);
    await runUpload(data, api);
    const boundary = api.state.events.length;
    const result = await runUpload(data, api);
    assert.equal(result.uploaded, 0);
    assert.equal(result.reused, 3);
    const resumedEvents = api.state.events.slice(boundary);
    assert.deepEqual(resumedEvents.map(event => `${event.method}:${event.operation}`), [
      'POST:begin',
      'HEAD:status',
    ]);
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('partial staging skips exact objects and accepts a conditional 412 race only after HEAD verification', async () => {
  const data = await fixture();
  try {
    const first = data.records[0];
    const preseeded = new Map([[first.path, {
      bytes: data.files.get(first.path),
      sha256: first.sha256,
    }]]);
    const api = createIngestionApi(data, {
      objects: preseeded,
      raceStatus: 412,
      cloudflareHeadNormalization: true,
    });
    const originalFetch = api.fetchImpl;
    let injectedRace = false;
    api.fetchImpl = async (url, init) => {
      const parsed = new URL(url);
      const path = parsed.pathname.split('/objects/')[1];
      if (!injectedRace && init.method === 'PUT' && path) {
        const decoded = path.split('/').map(decodeURIComponent).join('/');
        const declared = data.records.find(value => value.path === decoded);
        const bytes = data.files.get(decoded);
        api.state.objects.set(decoded, {bytes, sha256: declared.sha256});
        injectedRace = true;
      }
      return originalFetch(url, init);
    };
    const result = await runUpload(data, api);
    assert.equal(result.uploaded, 1);
    assert.equal(result.reused, 2);
    assert.equal(result.committed, true);
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('resume HEAD retries transient 409 responses with an explicit bounded backoff', async () => {
  const data = await fixture();
  try {
    const preseeded = new Map(data.records.map(declared => [declared.path, {
      bytes: data.files.get(declared.path),
      sha256: declared.sha256,
    }]));
    const api = createIngestionApi(data, {objects: preseeded});
    const originalFetch = api.fetchImpl;
    const target = data.records[0].path;
    const transientStatuses = [409, 409];
    api.fetchImpl = async (url, init) => {
      const parsed = new URL(url);
      const encodedPath = parsed.pathname.split('/objects/')[1];
      if (init.method === 'HEAD' && encodedPath) {
        const path = encodedPath.split('/').map(decodeURIComponent).join('/');
        if (path === target && transientStatuses.length > 0) {
          return new Response(null, {status: transientStatuses.shift()});
        }
      }
      return originalFetch(url, init);
    };
    const delays = [];
    const capture = loggerCapture();
    const result = await runUpload(data, api, {
      concurrency: 1,
      logger: capture.logger,
      sleepImpl: async delayMs => delays.push(delayMs),
    });
    assert.equal(result.uploaded, 0);
    assert.equal(result.reused, 3);
    assert.deepEqual(delays, [250, 500]);
    assert.equal(api.state.events.some(event => event.method === 'PUT'), false);
    const warnings = capture.entries
      .filter(([level]) => level === 'warn')
      .map(([, message]) => message);
    assert.deepEqual(warnings, [
      `Preview object HEAD ${target} returned transient HTTP 409; ` +
        'retrying exact verification in 250 ms (attempt 2/5).',
      `Preview object HEAD ${target} returned transient HTTP 409; ` +
        'retrying exact verification in 500 ms (attempt 3/5).',
    ]);
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('post-PUT HEAD retries transient 409 and 404 responses before exact verification', async () => {
  const data = await fixture();
  try {
    const api = createIngestionApi(data);
    const originalFetch = api.fetchImpl;
    const target = data.records[0].path;
    const transientStatuses = [409, 404];
    let targetPutCompleted = false;
    api.fetchImpl = async (url, init) => {
      const parsed = new URL(url);
      const encodedPath = parsed.pathname.split('/objects/')[1];
      const path = encodedPath
        ? encodedPath.split('/').map(decodeURIComponent).join('/')
        : null;
      if (
        init.method === 'HEAD' &&
        path === target &&
        targetPutCompleted &&
        transientStatuses.length > 0
      ) {
        return new Response(null, {status: transientStatuses.shift()});
      }
      const response = await originalFetch(url, init);
      if (init.method === 'PUT' && path === target && response.status === 201) {
        targetPutCompleted = true;
      }
      return response;
    };
    const delays = [];
    const capture = loggerCapture();
    const result = await runUpload(data, api, {
      concurrency: 1,
      logger: capture.logger,
      sleepImpl: async delayMs => delays.push(delayMs),
    });
    assert.equal(result.uploaded, 3);
    assert.equal(result.reused, 0);
    assert.deepEqual(delays, [250, 500]);
    const warnings = capture.entries
      .filter(([level]) => level === 'warn')
      .map(([, message]) => message);
    assert.deepEqual(warnings, [
      `Preview object HEAD ${target} returned transient HTTP 409; ` +
        'retrying exact verification in 250 ms (attempt 2/5).',
      `Preview object HEAD ${target} returned transient HTTP 404; ` +
        'retrying exact verification in 500 ms (attempt 3/5).',
    ]);
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('a persistent HEAD conflict exhausts the retry window and fails before PUT or commit', async () => {
  const data = await fixture();
  try {
    const api = createIngestionApi(data);
    const originalFetch = api.fetchImpl;
    const target = data.records[0].path;
    api.fetchImpl = async (url, init) => {
      const parsed = new URL(url);
      const encodedPath = parsed.pathname.split('/objects/')[1];
      const path = encodedPath
        ? encodedPath.split('/').map(decodeURIComponent).join('/')
        : null;
      if (init.method === 'HEAD' && path === target) {
        return new Response(null, {status: 409});
      }
      return originalFetch(url, init);
    };
    const delays = [];
    const capture = loggerCapture();
    await assert.rejects(
      runUpload(data, api, {
        concurrency: 1,
        logger: capture.logger,
        sleepImpl: async delayMs => delays.push(delayMs),
      }),
      new RegExp(`Preview object HEAD ${target} returned unexpected HTTP 409`),
    );
    assert.deepEqual(delays, [250, 500, 1_000, 2_000]);
    assert.equal(api.state.events.some(event => event.method === 'PUT'), false);
    assert.equal(api.state.events.some(event => event.operation === 'commit'), false);
    assert.equal(
      capture.entries.filter(([level, message]) =>
        level === 'warn' && message.includes('returned transient HTTP 409')).length,
      4,
    );
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('a persistent post-PUT 404 exhausts the retry window and fails before commit', async () => {
  const data = await fixture();
  try {
    const api = createIngestionApi(data);
    const originalFetch = api.fetchImpl;
    const target = data.records[0].path;
    let targetPutCompleted = false;
    api.fetchImpl = async (url, init) => {
      const parsed = new URL(url);
      const encodedPath = parsed.pathname.split('/objects/')[1];
      const path = encodedPath
        ? encodedPath.split('/').map(decodeURIComponent).join('/')
        : null;
      if (init.method === 'HEAD' && path === target && targetPutCompleted) {
        return new Response(null, {status: 404});
      }
      const response = await originalFetch(url, init);
      if (init.method === 'PUT' && path === target && response.status === 201) {
        targetPutCompleted = true;
      }
      return response;
    };
    const delays = [];
    const capture = loggerCapture();
    await assert.rejects(
      runUpload(data, api, {
        concurrency: 1,
        logger: capture.logger,
        sleepImpl: async delayMs => delays.push(delayMs),
      }),
      new RegExp(`Preview object PUT ${target} succeeded or raced, but the exact object is still absent`),
    );
    assert.deepEqual(delays, [250, 500, 1_000, 2_000]);
    assert.equal(
      api.state.events.some(event => event.method === 'PUT' && event.path === target),
      true,
    );
    assert.equal(api.state.events.some(event => event.operation === 'commit'), false);
    assert.equal(
      capture.entries.filter(([level, message]) =>
        level === 'warn' && message.includes('returned transient HTTP 404')).length,
      4,
    );
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('conflicting remote metadata fails explicitly before commit', async () => {
  const data = await fixture();
  try {
    const first = data.records[0];
    const api = createIngestionApi(data, {
      objects: new Map([[first.path, {
        bytes: data.files.get(first.path),
        sha256: 'f'.repeat(64),
      }]]),
    });
    await assert.rejects(runUpload(data, api), /x-mrt-content-sha256=.*expected/);
    assert.equal(api.state.events.some(event => event.operation === 'commit'), false);
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('local mutation after validation cannot be uploaded or committed', async () => {
  const data = await fixture();
  try {
    const changedPath = join(data.root, 'assets', 'pack-000.bin');
    await writeFile(changedPath, Buffer.from([9, 9, 9, 9]));
    const api = createIngestionApi(data);
    await assert.rejects(runUpload(data, api, {concurrency: 1}), /changed after validation/);
    assert.equal(api.state.events.some(event => event.method === 'PUT'), false);
    assert.equal(api.state.events.some(event => event.operation === 'commit'), false);
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('status digest mismatch and insecure production URLs fail closed without logging the token', async () => {
  const data = await fixture();
  try {
    const api = createIngestionApi(data, {statusSha256: 'c'.repeat(64)});
    const capture = loggerCapture();
    await assert.rejects(
      runUpload(data, api, {logger: capture.logger}),
      /x-mrt-content-sha256=.*expected/,
    );
    assert.equal(capture.entries.flat().join('\n').includes(TOKEN), false);
    const leakingFetch = async () => {
      throw new Error(`transport accidentally included ${TOKEN}`, {
        cause: new Error(`nested socket diagnostic included ${TOKEN}`),
      });
    };
    const networkError = await assert.rejects(
      runUpload(data, api, {logger: capture.logger, fetchImpl: leakingFetch}),
      /\[REDACTED\]/,
    );
    assert.equal(networkError?.message?.includes(TOKEN) ?? false, false);
    assert.equal(networkError?.stack?.includes(TOKEN) ?? false, false);
    assert.equal(networkError?.cause, undefined);
    assert.equal(serializedErrorChain(networkError).includes(TOKEN), false);
    assert.equal(capture.entries.flat().join('\n').includes(TOKEN), false);
    await assert.rejects(
      uploadRecipePreviewSidecar({
        local: data.root,
        ingestBaseUrl: 'http://preview.test/api/admin/preview-assets',
        token: TOKEN,
        logger: capture.logger,
        fetchImpl: api.fetchImpl,
        localValidator: data.localValidator,
      }),
      /requires HTTPS/,
    );
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('token files require private permissions and accept one trailing newline', async () => {
  const root = await mkdtemp(join(tmpdir(), 'mrt-preview-token-'));
  try {
    assert.equal(
      await readPreviewIngestToken({env: {PREVIEW_UPLOAD_TOKEN: TOKEN}}),
      TOKEN,
    );
    const path = join(root, 'token');
    await writeFile(path, `${TOKEN}\n`, {mode: 0o600});
    await chmod(path, 0o600);
    assert.equal(await readPreviewIngestToken({tokenFile: path, env: {}}), TOKEN);
    if (process.platform !== 'win32') {
      await chmod(path, 0o644);
      await assert.rejects(
        readPreviewIngestToken({tokenFile: path, env: {}}),
        /must not be readable or writable by group\/other/,
      );
    }
    assert.equal((await readFile(path, 'utf8')).includes(TOKEN), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
