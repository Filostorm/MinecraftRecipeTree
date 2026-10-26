import assert from 'node:assert/strict';
import {createHash, webcrypto} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const ORIGIN = 'https://viewer.example';
const PUBLICATION = 'a'.repeat(64);
const PREVIEW = 'b'.repeat(64);

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

async function serviceWorkerHarness({beforePut, beforeKeys, failCachePut = false, corruptPack = false, stallFirstDownload = false} = {}) {
  const source = await readFile(new URL('../../public/local-pack-sw.js', import.meta.url), 'utf8');
  const listeners = new Map();
  const entries = new Map();
  const errors = [];
  const pack = Uint8Array.from([1, 2, 3, 4, 5, 6, 7, 8]);
  let networkRequests = 0;
  let cleanupScans = 0;
  const background = [];
  const timers = new Map();
  let nextTimer = 0;
  const fetchStarted = Promise.withResolvers();
  const cache = {
    async delete(request) {
      return entries.delete(typeof request === 'string' ? request : request.url);
    },
    async keys() {
      cleanupScans += 1;
      await beforeKeys?.();
      return [...entries.keys()].map(url => new Request(url));
    },
    async match(request) {
      return entries.get(typeof request === 'string' ? request : request.url)?.clone();
    },
    async put(request, response) {
      await beforePut?.();
      if (failCachePut) throw new Error('The cache storage quota was exceeded.');
      entries.set(typeof request === 'string' ? request : request.url, response.clone());
    },
  };
  const worker = {
    addEventListener(type, listener) {
      listeners.set(type, listener);
    },
    clients: {async claim() {}},
    crypto: webcrypto,
    location: {origin: ORIGIN},
    skipWaiting() {},
  };
  vm.runInNewContext(source, {
    Map,
    Promise,
    Request,
    Response,
    URL,
    Uint8Array,
    AbortController,
    setTimeout(callback) {const id = ++nextTimer; timers.set(id, callback); return id;},
    clearTimeout(id) {timers.delete(id);},
    caches: {async open() { return cache; }},
    console: {
      error(...values) { errors.push(values); },
    },
    fetch: async (request, options) => {
      networkRequests += 1;
      const url = typeof request === 'string' ? request : request.url;
      assert.match(url, /\/assets\/pack-000\.bin\?/u);
      fetchStarted.resolve();
      if (stallFirstDownload && networkRequests === 1) {
        await new Promise((_resolve, reject) => options.signal.addEventListener('abort', () =>
          reject(new DOMException('Image download aborted.', 'AbortError')),
        ));
      }
      return new Response(pack, {
        status: 200,
        headers: {
          'Content-Length': String(pack.byteLength),
          'Content-Type': 'application/octet-stream',
          'X-MRT-Pack-SHA256': corruptPack ? '0'.repeat(64) : sha256(pack),
          'X-MRT-Stored-Bytes': String(pack.byteLength),
        },
      });
    },
    self: worker,
  });

  async function dispatch(path) {
    let response;
    listeners.get('fetch')({
      request: new Request(`${ORIGIN}${path}`),
      respondWith(value) { response = Promise.resolve(value); },
      waitUntil(value) { background.push(value); },
    });
    return response;
  }

  return {
    dispatch,
    entries,
    errors,
    flush: () => Promise.all(background),
    cleanupScans: () => cleanupScans,
    fetchStarted: fetchStarted.promise,
    expireDownloads() {for (const callback of [...timers.values()]) callback();},
    activeTimers: () => timers.size,
    networkRequests: () => networkRequests,
  };
}

test('coalesces concurrent core image coordinates into one verified pack request', async () => {
  const harness = await serviceWorkerHarness();
  const base = `/dataset/publications/${PUBLICATION}/exports/assets/s`;
  const query = `?dataset=${PUBLICATION}`;
  const [first, second] = await Promise.all([
    harness.dispatch(`${base}/000-0-4.webp${query}`),
    harness.dispatch(`${base}/000-4-4.webp${query}`),
  ]);

  assert.equal(harness.networkRequests(), 1);
  assert.deepEqual([...new Uint8Array(await first.arrayBuffer())], [1, 2, 3, 4]);
  assert.deepEqual([...new Uint8Array(await second.arrayBuffer())], [5, 6, 7, 8]);
  assert.equal(first.headers.get('x-mrt-image-pack-cache'), 'local');
  await harness.flush();
  assert.equal(harness.entries.size, 1);
  assert.equal(harness.errors.length, 0);

  const repeated = await harness.dispatch(`${base}/000-0-4.webp${query}`);
  assert.deepEqual([...new Uint8Array(await repeated.arrayBuffer())], [1, 2, 3, 4]);
  assert.equal(harness.networkRequests(), 1);
});

test('verified images display while persistent cache writes and cleanup are blocked', async () => {
  for (const phase of ['beforePut', 'beforeKeys']) {
    const started = Promise.withResolvers();
    const gate = Promise.withResolvers();
    const harness = await serviceWorkerHarness({[phase]: () => {
      started.resolve();
      return gate.promise;
    }});
    let response;
    const request = harness.dispatch(
      `/dataset/publications/${PUBLICATION}/exports/assets/s/000-0-4.webp?dataset=${PUBLICATION}`,
    ).then(value => { response = value; });
    try {
      await started.promise;
      await new Promise(setImmediate);
      assert.equal(response?.status, 200, `${phase} must not hold up a valid image`);
      assert.deepEqual([...new Uint8Array(await response.arrayBuffer())], [1, 2, 3, 4]);
      // An icon requested during the write still reuses the verified in-memory bytes.
      const repeated = await harness.dispatch(
        `/dataset/publications/${PUBLICATION}/exports/assets/s/000-4-4.webp?dataset=${PUBLICATION}`,
      );
      assert.equal(repeated.status, 200);
      assert.equal(harness.networkRequests(), 1);
    } finally {
      gate.resolve();
      await request;
      await harness.flush();
    }
    assert.equal(harness.entries.size, 1);
    assert.equal(harness.errors.length, 0);
  }
});

test('cache quota failures are logged without discarding verified image bytes', async () => {
  const harness = await serviceWorkerHarness({failCachePut: true});
  const response = await harness.dispatch(
    `/dataset/publications/${PUBLICATION}/exports/assets/s/000-0-4.webp?dataset=${PUBLICATION}`,
  );
  await harness.flush();
  assert.equal(response.status, 200);
  assert.equal(harness.entries.size, 0);
  assert.equal(harness.errors.length, 1);
  assert.match(harness.errors[0][0], /verified image pack could not be cached/);
});

test('corrupt packs remain blocked before any icon bytes are served or stored', async () => {
  const harness = await serviceWorkerHarness({corruptPack: true});
  const response = await harness.dispatch(
    `/dataset/publications/${PUBLICATION}/exports/assets/s/000-0-4.webp?dataset=${PUBLICATION}`,
  );
  await harness.flush();
  assert.equal(response.status, 502);
  assert.equal(harness.entries.size, 0);
  assert.equal(harness.errors.length, 1);
});

test('a stalled shared download is aborted so a later icon request can recover', async () => {
  const harness = await serviceWorkerHarness({stallFirstDownload: true});
  const path = `/dataset/publications/${PUBLICATION}/exports/assets/s/000-0-4.webp?dataset=${PUBLICATION}`;
  const stalled = harness.dispatch(path);
  await harness.fetchStarted;
  harness.expireDownloads();
  assert.equal((await stalled).status, 502);
  assert.equal(harness.activeTimers(), 0);
  const retried = await harness.dispatch(path);
  assert.equal(retried.status, 200);
  await harness.flush();
  assert.equal(harness.activeTimers(), 0);
  assert.equal(harness.networkRequests(), 2);
  assert.equal(harness.errors.length, 1);
});

test('coalesces preview coordinates and ignores noncanonical immutable queries', async () => {
  const harness = await serviceWorkerHarness();
  const base = `/dataset/preview-sets/${PREVIEW}/assets/s`;
  const query = `?dataset=${PUBLICATION}&preview=${PREVIEW}`;
  const preview = await harness.dispatch(`${base}/000-4-4.webp${query}`);
  assert.deepEqual([...new Uint8Array(await preview.arrayBuffer())], [5, 6, 7, 8]);
  assert.equal(harness.networkRequests(), 1);

  const ignored = await harness.dispatch(
    `${base}/000-0-4.webp?preview=${PREVIEW}&dataset=${PUBLICATION}`,
  );
  assert.equal(ignored, undefined);
  assert.equal(harness.networkRequests(), 1);
});
