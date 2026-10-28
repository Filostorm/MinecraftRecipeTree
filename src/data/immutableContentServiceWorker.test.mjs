import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const ORIGIN = 'https://viewer.example';
const PUBLICATION = 'a'.repeat(64);

async function serviceWorkerHarness({networkResponses, failCachePut = false, beforePut, beforeKeys} = {}) {
  const source = await readFile(new URL('../../public/local-pack-sw.js', import.meta.url), 'utf8');
  const listeners = new Map();
  const errors = [];
  let networkRequests = 0;
  const cachesByName = new Map();
  const background = [];
  let cleanupScans = 0;
  const timers = new Map();
  let nextTimer = 0;
  function cacheFor(name) {
    let entries = cachesByName.get(name);
    if (!entries) {
      entries = new Map();
      cachesByName.set(name, entries);
    }
    return {
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
        await beforePut?.(response.clone());
        if (failCachePut) throw new Error('The cache storage quota was exceeded.');
        entries.set(typeof request === 'string' ? request : request.url, response.clone());
      },
    };
  }
  const worker = {
    addEventListener(type, listener) {
      listeners.set(type, listener);
    },
    clients: {async claim() {}},
    location: {origin: ORIGIN},
    skipWaiting() {},
  };
  vm.runInNewContext(source, {
    Map,
    Number,
    Promise,
    Request,
    Response,
    URL,
    Uint8Array,
    AbortController,
    setTimeout(callback) {const id = ++nextTimer; timers.set(id, callback); return id;},
    clearTimeout(id) {timers.delete(id);},
    caches: {async open(name) { return cacheFor(name); }},
    console: {
      error(...values) { errors.push(values); },
    },
    fetch: async (request, options) => {
      networkRequests += 1;
      const url = typeof request === 'string' ? request : request.url;
      const respond = networkResponses?.(url, options, networkRequests);
      if (respond) return respond;
      return new Response('payload', {
        status: 200,
        headers: {'Content-Length': '7', 'Content-Type': 'text/plain'},
      });
    },
    self: worker,
  });

  async function dispatch(path, init) {
    let response;
    listeners.get('fetch')({
      request: new Request(`${ORIGIN}${path}`, init),
      respondWith(value) { response = Promise.resolve(value); },
      waitUntil(value) { background.push(value); void value.catch(() => {}); },
    });
    return response;
  }

  return {
    dispatch,
    errors,
    flush: () => Promise.all(background),
    cleanupScans: () => cleanupScans,
    cacheSize: name => cachesByName.get(name)?.size ?? 0,
    networkRequests: () => networkRequests,
    expireDownloads() {for (const callback of [...timers.values()]) callback();},
    activeTimers: () => timers.size,
  };
}

test('a stalled shared document request expires and a retry creates a new network request', async () => {
  const started = Promise.withResolvers();
  const harness = await serviceWorkerHarness({networkResponses: (_url, options, attempt) => {
    if (attempt !== 1) return new Response('recipe');
    started.resolve();
    return new Promise((_resolve, reject) => {
      options.signal.addEventListener('abort', () => reject(options.signal.reason), {once: true});
    });
  }});
  const path = `/dataset/publications/${PUBLICATION}/exports/recipes/example/recipes.json?dataset=${PUBLICATION}`;
  const stalled = harness.dispatch(path);
  const rejected = assert.rejects(stalled, /abort/i);
  await started.promise;
  harness.expireDownloads();
  await rejected;
  assert.equal(harness.activeTimers(), 0);
  const retried = await harness.dispatch(path);
  assert.equal(await retried.text(), 'recipe');
  assert.equal(harness.networkRequests(), 2);
  assert.equal(harness.errors.length, 2);
  assert.ok(harness.errors.some(entry => /new request can retry/.test(entry[0])));
});

test('a revalidated retry does not reuse a response stalled after HTTP headers', async () => {
  const harness = await serviceWorkerHarness({
    beforePut: response => response.arrayBuffer(),
    networkResponses: (_url, options, attempt) => {
      if (attempt !== 1) return new Response('fresh recipe');
      return new Response(new ReadableStream({start(controller) {
        options.signal.addEventListener('abort', () => controller.error(options.signal.reason), {once: true});
        controller.enqueue(new TextEncoder().encode('partial'));
      }}));
    },
  });
  const path = `/dataset/publications/${PUBLICATION}/exports/recipes/example/recipes.json?dataset=${PUBLICATION}`;
  const partial = await harness.dispatch(path);
  const rejected = assert.rejects(partial.text(), /abort/i);
  const retried = await harness.dispatch(path, {cache: 'reload', headers: {'Cache-Control': 'no-cache'}});
  assert.equal(await retried.text(), 'fresh recipe');
  assert.equal(harness.networkRequests(), 2);
  harness.expireDownloads();
  await rejected;
  await harness.flush();
  assert.equal(harness.activeTimers(), 0);
  assert.equal(await (await harness.dispatch(path)).text(), 'fresh recipe');
  assert.equal(harness.networkRequests(), 2);
});

test('an explicit reload bypasses the immutable browser cache', async () => {
  const harness = await serviceWorkerHarness({networkResponses: (_url, _options, attempt) =>
    new Response(attempt === 1 ? 'old response' : 'fresh response'),
  });
  const path = '/assets/App-abc123.js';
  assert.equal(await (await harness.dispatch(path)).text(), 'old response');
  await harness.flush();
  assert.equal(await (await harness.dispatch(path, {cache: 'reload'})).text(), 'fresh response');
  assert.equal(harness.networkRequests(), 2);
});

test('app bundle chunks are cached and served without a repeat network request', async () => {
  const harness = await serviceWorkerHarness();
  const first = await harness.dispatch('/assets/App-abc123.js');
  assert.equal(await first.text(), 'payload');
  assert.equal(harness.networkRequests(), 1);

  const second = await harness.dispatch('/assets/App-abc123.js');
  assert.equal(await second.text(), 'payload');
  assert.equal(harness.networkRequests(), 1);
  assert.equal(harness.cacheSize('minecraft-recipe-tree-immutable-content-v1'), 1);
  assert.equal(harness.errors.length, 0);
});

test('dataset export documents are cached, but sprite coordinates and packed binaries are not', async () => {
  const harness = await serviceWorkerHarness();
  const manifestPath = `/dataset/publications/${PUBLICATION}/exports/manifest.json?dataset=${PUBLICATION}`;
  await harness.dispatch(manifestPath);
  await harness.dispatch(manifestPath);
  assert.equal(harness.networkRequests(), 1);
  assert.equal(harness.cacheSize('minecraft-recipe-tree-immutable-content-v1'), 1);

  // The sprite coordinate is handled entirely by the pre-existing packed-image path (asserted by
  // hostedImagePackServiceWorker.test.mjs); this only confirms it never touches this new cache.
  const spritePath =
    `/dataset/publications/${PUBLICATION}/exports/assets/s/000-0-4.webp?dataset=${PUBLICATION}`;
  const binPath = `/dataset/publications/${PUBLICATION}/exports/assets/pack-000.bin?dataset=${PUBLICATION}`;
  assert.ok(await harness.dispatch(spritePath));
  assert.equal(await harness.dispatch(binPath), undefined);
  assert.equal(harness.cacheSize('minecraft-recipe-tree-immutable-content-v1'), 1);
});

test('a byte budget over the limit prunes the oldest entries first', async () => {
  const harness = await serviceWorkerHarness({
    networkResponses: () =>
      new Response('x'.repeat(60 * 1024 * 1024), {
        status: 200,
        headers: {'Content-Length': String(60 * 1024 * 1024), 'Content-Type': 'text/plain'},
      }),
  });
  await harness.dispatch('/assets/one-aaa.js');
  await harness.dispatch('/assets/two-bbb.js');
  await harness.flush();
  assert.equal(harness.cacheSize('minecraft-recipe-tree-immutable-content-v1'), 1);

  const stillFresh = await harness.dispatch('/assets/two-bbb.js');
  assert.equal((await stillFresh.text()).length, 60 * 1024 * 1024);
  assert.equal(harness.networkRequests(), 2);
});

test('a cache write failure still serves the network response', async () => {
  const harness = await serviceWorkerHarness({failCachePut: true});
  const response = await harness.dispatch('/assets/App-abc123.js');
  await harness.flush();
  assert.equal(await response.text(), 'payload');
  assert.equal(harness.cacheSize('minecraft-recipe-tree-immutable-content-v1'), 0);
  assert.equal(harness.errors.length, 1);
});

test('content responses do not wait for cache storage, while concurrent reads stay coalesced', async () => {
  const started = Promise.withResolvers();
  const gate = Promise.withResolvers();
  const harness = await serviceWorkerHarness({beforePut: () => {
    started.resolve();
    return gate.promise;
  }});
  let response;
  const request = harness.dispatch('/assets/App-abc123.js').then(value => { response = value; });
  try {
    await started.promise;
    await new Promise(setImmediate);
    assert.equal(response?.status, 200);
    assert.equal(await response.text(), 'payload');
    const repeated = await harness.dispatch('/assets/App-abc123.js');
    assert.equal(await repeated.text(), 'payload');
    assert.equal(harness.networkRequests(), 1);
  } finally {
    gate.resolve();
    await request;
    await harness.flush();
  }
  assert.equal(harness.cacheSize('minecraft-recipe-tree-immutable-content-v1'), 1);
});

test('concurrent content downloads combine cache cleanup instead of rescanning per file', async () => {
  const started = Promise.withResolvers();
  const gate = Promise.withResolvers();
  const harness = await serviceWorkerHarness({beforeKeys: () => {
    started.resolve();
    return gate.promise;
  }});
  await harness.dispatch('/assets/first-aaa.js');
  await started.promise;
  try {
    await Promise.all(Array.from({length: 24}, (_, index) =>
      harness.dispatch(`/assets/chunk-${index}.js`),
    ));
    await new Promise(setImmediate);
    assert.equal(harness.cleanupScans(), 1);
  } finally {
    gate.resolve();
    await harness.flush();
  }
  assert.equal(harness.cleanupScans(), 2);
  assert.equal(harness.cacheSize('minecraft-recipe-tree-immutable-content-v1'), 25);
});
