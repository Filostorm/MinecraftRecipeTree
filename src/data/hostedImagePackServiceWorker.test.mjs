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

async function serviceWorkerHarness() {
  const source = await readFile(new URL('../../public/local-pack-sw.js', import.meta.url), 'utf8');
  const listeners = new Map();
  const entries = new Map();
  const errors = [];
  const pack = Uint8Array.from([1, 2, 3, 4, 5, 6, 7, 8]);
  let networkRequests = 0;
  const cache = {
    async delete(request) {
      return entries.delete(typeof request === 'string' ? request : request.url);
    },
    async keys() {
      return [...entries.keys()].map(url => new Request(url));
    },
    async match(request) {
      return entries.get(typeof request === 'string' ? request : request.url)?.clone();
    },
    async put(request, response) {
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
    caches: {async open() { return cache; }},
    console: {
      error(...values) { errors.push(values); },
    },
    fetch: async request => {
      networkRequests += 1;
      const url = typeof request === 'string' ? request : request.url;
      assert.match(url, /\/assets\/pack-000\.bin\?/u);
      return new Response(pack, {
        status: 200,
        headers: {
          'Content-Length': String(pack.byteLength),
          'Content-Type': 'application/octet-stream',
          'X-MRT-Pack-SHA256': sha256(pack),
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
    });
    return response;
  }

  return {
    dispatch,
    entries,
    errors,
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
  assert.equal(harness.entries.size, 1);
  assert.equal(harness.errors.length, 0);

  const repeated = await harness.dispatch(`${base}/000-0-4.webp${query}`);
  assert.deepEqual([...new Uint8Array(await repeated.arrayBuffer())], [1, 2, 3, 4]);
  assert.equal(harness.networkRequests(), 1);
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
