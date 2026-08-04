import assert from 'node:assert/strict';
import test from 'node:test';
import {proxyBetaDatasetRequest} from './betaDataProxy.ts';

const PRODUCTION_ORIGIN = 'https://minecraftrecipetree.craftsmannsoftware.com';

test('beta data proxy is inactive without an explicit origin and ignores non-dataset routes', async () => {
  const request = new Request('https://beta.example/api/datasets');
  assert.equal(
    await proxyBetaDatasetRequest(request, {}, new URL(request.url), async () => {
      throw new Error('upstream fetch must not run');
    }),
    null,
  );

  const feedback = new Request('https://beta.example/api/feedback');
  assert.equal(
    await proxyBetaDatasetRequest(
      feedback,
      {BETA_DATA_ORIGIN: PRODUCTION_ORIGIN},
      new URL(feedback.url),
      async () => {
        throw new Error('upstream fetch must not run');
      },
    ),
    null,
  );
});

test('beta data proxy forwards only read requests and a bounded header allowlist', async () => {
  const request = new Request(
    'https://beta.example/dataset/publications/abc/exports/items.json?dataset=abc',
    {
      headers: {
        accept: 'application/json',
        authorization: 'Bearer must-not-leave-beta',
        cookie: 'session=must-not-leave-beta',
        'if-none-match': '"dataset-etag"',
        range: 'bytes=0-31',
      },
    },
  );
  let forwarded;
  const response = await proxyBetaDatasetRequest(
    request,
    {BETA_DATA_ORIGIN: PRODUCTION_ORIGIN},
    new URL(request.url),
    async upstreamRequest => {
      forwarded = upstreamRequest;
      return new Response('dataset', {
        headers: {
          'cache-control': 'public, max-age=31536000, immutable',
          'set-cookie': 'must-not-reach-beta=1',
          'www-authenticate': 'Bearer',
        },
      });
    },
  );

  assert.ok(forwarded);
  assert.equal(
    forwarded.url,
    `${PRODUCTION_ORIGIN}/dataset/publications/abc/exports/items.json?dataset=abc`,
  );
  assert.equal(forwarded.method, 'GET');
  assert.equal(forwarded.headers.get('accept'), 'application/json');
  assert.equal(forwarded.headers.get('if-none-match'), '"dataset-etag"');
  assert.equal(forwarded.headers.get('range'), 'bytes=0-31');
  assert.equal(forwarded.headers.get('authorization'), null);
  assert.equal(forwarded.headers.get('cookie'), null);
  assert.equal(await response.text(), 'dataset');
  assert.equal(response.headers.get('set-cookie'), null);
  assert.equal(response.headers.get('www-authenticate'), null);
  assert.equal(response.headers.get('x-mrt-beta-data-origin'), PRODUCTION_ORIGIN);
});

test('beta data proxy refuses mutations without contacting production', async () => {
  const request = new Request('https://beta.example/api/modpacks', {method: 'POST'});
  const response = await proxyBetaDatasetRequest(
    request,
    {BETA_DATA_ORIGIN: PRODUCTION_ORIGIN},
    new URL(request.url),
    async () => {
      throw new Error('upstream fetch must not run');
    },
  );
  assert.equal(response.status, 405);
  assert.equal(response.headers.get('allow'), 'GET, HEAD');
});

test('beta catalog responses are locally reframed after an upstream Worker', async () => {
  const request = new Request('https://beta.example/api/datasets');
  const response = await proxyBetaDatasetRequest(
    request,
    {BETA_DATA_ORIGIN: PRODUCTION_ORIGIN},
    new URL(request.url),
    async () => new Response('{"datasets":[]}', {
      headers: {
        'content-encoding': 'gzip',
        'content-length': '999',
        'content-type': 'application/json',
      },
    }),
  );
  assert.equal(await response.text(), '{"datasets":[]}');
  assert.equal(response.headers.get('content-encoding'), null);
  assert.equal(response.headers.get('content-length'), '15');
});

test('beta data proxy rejects an unsafe or path-bearing origin', async () => {
  for (const configuredOrigin of [
    'http://minecraftrecipetree.craftsmannsoftware.com',
    'https://user:secret@example.com',
    'https://example.com/data',
  ]) {
    const request = new Request('https://beta.example/api/datasets');
    await assert.rejects(
      proxyBetaDatasetRequest(
        request,
        {BETA_DATA_ORIGIN: configuredOrigin},
        new URL(request.url),
      ),
      /BETA_DATA_ORIGIN/,
    );
  }
});
