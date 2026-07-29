import assert from 'node:assert/strict';
import test from 'node:test';
import {
  fetchPublishingCatalog,
  preflightIngestionEndpoints,
  requirePublishingCatalog,
  resolveChannelExpectation,
} from './publication-upload-preflight.mjs';

const PUBLICATION = 'a'.repeat(64);
const NEXT_PUBLICATION = 'b'.repeat(64);
const PREVIEW = 'c'.repeat(64);
const TOKEN = 'operator-token-'.padEnd(48, 'x');

function descriptor(overrides = {}) {
  return {
    slug: 'example-pack',
    displayName: 'Example Pack',
    minecraftVersion: '1.20.1',
    packVersion: '1.0.0',
    publicationId: PUBLICATION,
    previewAssetSetId: PREVIEW,
    isDefault: true,
    ...overrides,
  };
}

function plan(overrides = {}) {
  return {
    slug: 'example-pack',
    pack: {name: 'Example Pack'},
    minecraftVersion: '1.20.1',
    publicationId: NEXT_PUBLICATION,
    ...overrides,
  };
}

const quietLogger = Object.freeze({info() {}, warn() {}, error() {}});

test('catalog validation and explicit channel action produce safe CAS expectations', () => {
  const datasets = requirePublishingCatalog({datasets: [descriptor()]});
  assert.deepEqual(
    resolveChannelExpectation({datasets, action: 'update', plan: plan()}),
    {expectedPreviousPublicationId: PUBLICATION, current: descriptor()},
  );
  assert.throws(
    () => resolveChannelExpectation({datasets, action: 'create', plan: plan()}),
    /already exists/,
  );
  assert.deepEqual(
    resolveChannelExpectation({
      datasets,
      action: 'create',
      plan: plan({slug: 'new-pack', pack: {name: 'New Pack'}}),
    }),
    {expectedPreviousPublicationId: null, current: null},
  );
  assert.throws(
    () => resolveChannelExpectation({
      datasets,
      action: 'update',
      plan: plan({pack: {name: 'Claimed Pack'}}),
    }),
    /belongs to Example Pack/,
  );
});

test('publishing catalog identity bounds use code points and reject invisible controls', () => {
  assert.equal(
    requirePublishingCatalog({datasets: [descriptor({displayName: '🧱'.repeat(120)})]})[0]
      .displayName,
    '🧱'.repeat(120),
  );
  assert.throws(
    () => requirePublishingCatalog({datasets: [descriptor({displayName: '🧱'.repeat(121)})]}),
    /violates the exact contract/,
  );
  assert.throws(
    () => requirePublishingCatalog({datasets: [descriptor({packVersion: '1.0\u2060.0'})]}),
    /violates the exact contract/,
  );
});

test('publishing catalog fetch is bounded and exact', async () => {
  const value = {datasets: [descriptor()]};
  const observed = [];
  const result = await fetchPublishingCatalog({
    appOrigin: 'http://viewer.test',
    allowHttpForTests: true,
    fetchImpl: async (url, init) => {
      observed.push({url, init});
      return new Response(`${JSON.stringify(value)}\n`, {
        status: 200,
        headers: {
          'Cache-Control': 'no-store',
          'Content-Type': 'application/json; charset=utf-8',
        },
      });
    },
  });
  assert.deepEqual(result, value.datasets);
  assert.equal(observed[0].url, 'http://viewer.test/api/datasets');
  assert.equal(observed[0].init.redirect, 'error');

  await assert.rejects(
    fetchPublishingCatalog({
      appOrigin: 'http://viewer.test',
      allowHttpForTests: true,
      fetchImpl: async () => new Response(JSON.stringify(value), {
        status: 200,
        headers: {'Content-Type': 'application/json'},
      }),
    }),
    /Cache-Control: no-store/,
  );
});

test('both authenticated ingestion targets are checked before bulk upload', async () => {
  const requests = [];
  const result = await preflightIngestionEndpoints({
    appOrigin: 'http://viewer.test',
    publicationId: PUBLICATION,
    previewAssetSetId: PREVIEW,
    coreToken: TOKEN,
    previewToken: TOKEN,
    allowHttpForTests: true,
    logger: quietLogger,
    fetchImpl: async (url, init) => {
      requests.push({url, init});
      return new Response(null, {status: 404, headers: {'Cache-Control': 'no-store'}});
    },
  });
  assert.deepEqual(result, {coreState: 'absent', previewState: 'absent'});
  assert.deepEqual(requests.map(entry => entry.url), [
    'http://viewer.test/api/admin/core-datasets/status',
    `http://viewer.test/api/admin/preview-assets/${PREVIEW}/status`,
  ]);
  assert.equal(new Headers(requests[0].init.headers).get('authorization'), `Bearer ${TOKEN}`);
  assert.equal(
    new Headers(requests[0].init.headers).get('x-mrt-dataset-publication-id'),
    PUBLICATION,
  );
});

test('preview target mismatch is rejected instead of being mistaken for an empty target', async () => {
  await assert.rejects(
    preflightIngestionEndpoints({
      appOrigin: 'http://viewer.test',
      publicationId: PUBLICATION,
      previewAssetSetId: PREVIEW,
      coreToken: TOKEN,
      previewToken: TOKEN,
      allowHttpForTests: true,
      logger: quietLogger,
      fetchImpl: async url =>
        url.includes('/core-datasets/')
          ? new Response(null, {status: 404, headers: {'Cache-Control': 'no-store'}})
          : new Response('{"error":"not configured"}\n', {
              status: 404,
              headers: {
                'Cache-Control': 'no-store',
                'Content-Type': 'application/json; charset=utf-8',
              },
            }),
    }),
    /configured-target error/,
  );
});

test('an existing remote status must bind to the exact publication identity', async () => {
  await assert.rejects(
    preflightIngestionEndpoints({
      appOrigin: 'http://viewer.test',
      publicationId: PUBLICATION,
      previewAssetSetId: PREVIEW,
      coreToken: TOKEN,
      previewToken: TOKEN,
      allowHttpForTests: true,
      logger: quietLogger,
      fetchImpl: async () =>
        new Response(null, {
          status: 200,
          headers: {
            'Cache-Control': 'no-store',
            'X-MRT-Dataset-Publication-ID': NEXT_PUBLICATION,
            'X-MRT-Publication-State': 'staged',
          },
        }),
    }),
    /conflicting publication identity/,
  );
});
