import assert from 'node:assert/strict';
import test from 'node:test';
import {checkForAppUpdate, ensureCurrentAppBuild} from './appUpdate.ts';

test('native builds skip web version checks', async t => {
  t.mock.method(globalThis, 'fetch', () => assert.fail('Native must not fetch a web version'));
  assert.equal(await checkForAppUpdate(), false);
  await ensureCurrentAppBuild();
});

test('version checks distinguish current and newer builds without caching the marker', async t => {
  let calls = 0;
  t.mock.method(globalThis, 'fetch', async (url, init) => {
    assert.equal(url, '/app-version.json');
    assert.equal(init.cache, 'no-store');
    calls += 1;
    return Response.json({buildId: calls === 1 ? 'current' : 'newer'});
  });
  assert.equal(await checkForAppUpdate('current'), false);
  assert.equal(await checkForAppUpdate('current'), true);
  assert.equal(calls, 2);
});

test('simultaneous focus and graph checks share one bounded request', async t => {
  const response = Promise.withResolvers();
  let calls = 0;
  t.mock.method(globalThis, 'fetch', () => {calls += 1; return response.promise;});
  const focus = checkForAppUpdate('current');
  const graph = checkForAppUpdate('current');
  assert.equal(focus, graph);
  response.resolve(Response.json({buildId: 'newer'}));
  assert.equal(await focus, true);
  assert.equal(calls, 1);
});

test('a failed or malformed version check is not reported as an update and can retry', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('unavailable', {status: 503}));
  await assert.rejects(checkForAppUpdate('current'), /503/);
  t.mock.method(globalThis, 'fetch', async () => Response.json({buildId: ''}));
  await assert.rejects(checkForAppUpdate('current'), /valid buildId/);
  t.mock.method(globalThis, 'fetch', async () => Response.json({buildId: 'current'}));
  assert.equal(await checkForAppUpdate('current'), false);
});
