import assert from 'node:assert/strict';
import test from 'node:test';
import {ExportHttpError, fetchDatasetText, isPublicationConflict, loadCachedDocument} from './datasetRequest.ts';

test('ordinary recipe failures remain local; only an identity conflict invalidates the pack', () => {
  assert.equal(isPublicationConflict(new ExportHttpError(409, '/dataset')), true);
  for (const error of [new ExportHttpError(503, '/recipe'), new ExportHttpError(404, '/recipe'),
    new Error('Request timed out'), new Error('Invalid recipe JSON')]) {
    assert.equal(isPublicationConflict(error), false);
  }
});

test('concurrent document reads share work, and a rejected read can be retried', async () => {
  const cache = new Map();
  const failed = Promise.withResolvers();
  let calls = 0;
  const loader = () => { calls += 1; return failed.promise; };
  const first = loadCachedDocument(cache, 0, loader);
  const second = loadCachedDocument(cache, 0, loader);
  assert.equal(first, second);
  failed.reject(new Error('Network interrupted'));
  await assert.rejects(first, /Network interrupted/);
  assert.equal(calls, 1);
  assert.equal(cache.size, 0);
  const retried = await loadCachedDocument(cache, 0, async () => 'recipe');
  assert.equal(retried, 'recipe');
  assert.equal(await loadCachedDocument(cache, 0, () => assert.fail('must reuse success')), 'recipe');
});

test('a rejected old request cannot remove a newer document load', async () => {
  const cache = new Map();
  const old = Promise.withResolvers();
  const pending = loadCachedDocument(cache, 0, () => old.promise);
  cache.clear();
  const newer = loadCachedDocument(cache, 0, async () => 'newer');
  old.reject(new Error('Old request failed'));
  await assert.rejects(pending, /Old request failed/);
  assert.equal(cache.get(0), newer);
});

test('dataset request deadlines abort both stuck headers and stuck response bodies', async t => {
  for (const phase of ['headers', 'body']) {
    await t.test(phase, async t => {
      const errors = [];
      t.mock.method(console, 'error', (...args) => errors.push(args));
      const warning = t.mock.method(console, 'warn', () => {});
      let signal;
      t.mock.method(globalThis, 'fetch', async (_url, init) => {
        signal = init.signal;
        const stalled = () => new Promise((_resolve, reject) => {
          signal.addEventListener('abort', () => reject(signal.reason), {once: true});
        });
        if (phase === 'headers') return stalled();
        return {ok: true, headers: new Headers(), text: stalled};
      });
      await assert.rejects(fetchDatasetText('https://example.test/recipe.json', undefined, 5),
        /did not complete within/);
      assert.equal(signal.aborted, true);
      assert.equal(errors.length, 1);
      t.mock.method(globalThis, 'fetch', async (_url, init) => {
        assert.equal(init.cache, 'reload');
        assert.equal(init.headers.get('Cache-Control'), 'no-cache');
        return new Response('recipe');
      });
      assert.equal(await fetchDatasetText('https://example.test/recipe.json'), 'recipe');
      assert.equal(warning.mock.callCount(), 1);
    });
  }
});

test('dataset reads preserve HTTP status errors, fetch options, and caller cancellation', async t => {
  t.mock.method(console, 'error', () => {});
  let observed;
  t.mock.method(globalThis, 'fetch', async (_url, init) => {
    observed = init;
    return new Response('unavailable', {status: 404});
  });
  await assert.rejects(fetchDatasetText('https://example.test/recipe.json', {cache: 'no-store'}),
    error => error instanceof ExportHttpError && error.status === 404);
  assert.equal(observed.cache, 'no-store');
  const controller = new AbortController();
  t.mock.method(globalThis, 'fetch', async (_url, init) => new Promise((_resolve, reject) => {
    init.signal.addEventListener('abort', () => reject(init.signal.reason), {once: true});
  }));
  const pending = fetchDatasetText('https://example.test/recipe.json', {signal: controller.signal});
  controller.abort(new Error('User cancelled'));
  await assert.rejects(pending, /User cancelled/);
});

test('an empty cached HTTP 200 is logged and revalidated exactly once', async t => {
  const errors = t.mock.method(console, 'error', () => {});
  const warnings = t.mock.method(console, 'warn', () => {});
  let calls = 0;
  t.mock.method(globalThis, 'fetch', async (_url, init) => {
    calls += 1;
    if (calls === 1) return new Response('', {headers: {'X-MRT-Stored-Bytes': '6'}});
    assert.equal(init.headers.get('Cache-Control'), 'no-cache');
    return new Response('recipe', {headers: {'X-MRT-Stored-Bytes': '6'}});
  });
  assert.equal(await fetchDatasetText('https://example.test/empty.json'), 'recipe');
  assert.equal(calls, 2);
  assert.equal(errors.mock.callCount(), 1);
  assert.equal(warnings.mock.callCount(), 1);
});

test('persistent incomplete bodies fail visibly instead of looping or being accepted', async t => {
  t.mock.method(console, 'error', () => {});
  t.mock.method(console, 'warn', () => {});
  const request = t.mock.method(globalThis, 'fetch', async () =>
    new Response('partial', {headers: {'X-MRT-Stored-Bytes': '100'}}),
  );
  await assert.rejects(fetchDatasetText('https://example.test/incomplete.json'), /declared 100 bytes/);
  assert.equal(request.mock.callCount(), 2);
});
