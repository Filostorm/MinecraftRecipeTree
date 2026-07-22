import assert from 'node:assert/strict';
import {mkdtemp, mkdir, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {encodePackedImageAuthorizationIndex} from './packed-image-authorization.mjs';
import {verifyPublicCoreDatasetPublication} from './verify-core-dataset-publication-remote.mjs';

const PUBLICATION = 'a'.repeat(64);
const BASE_URL = 'http://public.example/dataset/publications';

function responseHeaders(bytes, contentType, image = false, omitContentLength = false) {
  return {
    'Cache-Control': image
      ? 'public, max-age=31536000, immutable, no-transform'
      : 'public, max-age=31536000, immutable',
    ...(omitContentLength ? {} : {'Content-Length': String(bytes)}),
    'Content-Type': contentType,
    'X-MRT-Stored-Bytes': String(bytes),
  };
}

async function fixture() {
  const root = await mkdtemp(join(tmpdir(), 'public-core-verifier-test-'));
  const exportRoot = join(root, 'exports');
  const bundleRoot = join(root, 'bundle');
  await mkdir(join(exportRoot, 'assets'), {recursive: true});
  await mkdir(join(bundleRoot, 'indexes'), {recursive: true});
  const manifestBytes = Buffer.from(`${JSON.stringify({publicationId: PUBLICATION})}\n`);
  const itemsBytes = Buffer.from(`${JSON.stringify({items: []})}\n`);
  const packBytes = Buffer.from([1, 2, 3, 4, 5, 6, 7, 8, 9]);
  const indexBytes = encodePackedImageAuthorizationIndex({
    packNumber: 0,
    packBytes: packBytes.length,
    entries: [[0, 3], [3, 4], [7, 2]],
  });
  const paths = {
    manifest: join(exportRoot, 'manifest.json'),
    items: join(exportRoot, 'items.json'),
    pack: join(exportRoot, 'assets', 'pack-000.bin'),
    index: join(bundleRoot, 'indexes', 'pack-000.bin'),
  };
  await Promise.all([
    writeFile(paths.manifest, manifestBytes),
    writeFile(paths.items, itemsBytes),
    writeFile(paths.pack, packBytes),
    writeFile(paths.index, indexBytes),
  ]);
  const documents = [
    {path: 'items.json', bytes: itemsBytes.length, sha256: 'b'.repeat(64)},
    {path: 'manifest.json', bytes: manifestBytes.length, sha256: 'c'.repeat(64)},
  ];
  const pack = {
    path: 'assets/pack-000.bin',
    bytes: packBytes.length,
    sha256: 'd'.repeat(64),
    index: {
      path: 'indexes/pack-000.bin',
      bytes: indexBytes.length,
      sha256: 'e'.repeat(64),
      entries: 3,
    },
  };
  const state = {
    publicationId: PUBLICATION,
    manifest: {documents, packs: [pack]},
    records: [
      {...documents[0], localPath: paths.items},
      {...documents[1], localPath: paths.manifest},
      {path: pack.path, bytes: pack.bytes, sha256: pack.sha256, localPath: paths.pack},
      {...pack.index, localPath: paths.index},
    ],
  };
  return {root, exportRoot, bundleRoot, manifestBytes, itemsBytes, packBytes, state};
}

function silentLogger() {
  return {info() {}, warn() {}, error() {}};
}

test('public verifier checks every JSON object and first/middle/last authorized image bytes', async () => {
  const data = await fixture();
  const calls = [];
  try {
    const result = await verifyPublicCoreDatasetPublication({
      exportRoot: data.exportRoot,
      publication: join(data.bundleRoot, 'publication.json'),
      baseUrl: BASE_URL,
      allowHttpForTests: true,
      logger: silentLogger(),
      localValidator: async () => data.state,
      async fetchImpl(url, init) {
        const parsed = new URL(url);
        calls.push({url: parsed, method: init.method});
        assert.equal(parsed.search, `?dataset=${PUBLICATION}`);
        const prefix = `/dataset/publications/${PUBLICATION}/exports/`;
        assert.ok(parsed.pathname.startsWith(prefix));
        const path = parsed.pathname.slice(prefix.length);
        if (init.method === 'HEAD') {
          const bytes = path === 'manifest.json' ? data.manifestBytes.length : data.itemsBytes.length;
          return new Response(null, {
            status: 200,
            headers: responseHeaders(bytes, 'application/json; charset=utf-8'),
          });
        }
        if (path === 'manifest.json') {
          return new Response(data.manifestBytes, {
            status: 200,
            headers: responseHeaders(data.manifestBytes.length, 'application/json; charset=utf-8'),
          });
        }
        const match = /^assets\/s\/000-(\d+)-(\d+)\.webp$/.exec(path);
        assert.ok(match, `unexpected public verifier path ${path}`);
        const offset = Number(match[1]);
        const length = Number(match[2]);
        return new Response(data.packBytes.subarray(offset, offset + length), {
          status: 200,
          headers: responseHeaders(length, 'image/webp', true),
        });
      },
    });
    assert.deepEqual(result, {publicationId: PUBLICATION, documents: 2, packs: 1, imageSamples: 3});
    assert.equal(calls.filter(call => call.method === 'HEAD').length, 2);
    assert.equal(calls.filter(call => call.url.pathname.includes('/assets/s/')).length, 3);
    assert.equal(calls.some(call => /pack-000\.bin|indexes\//.test(call.url.pathname)), false);
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('public verifier accepts Cloudflare HEAD responses with explicit stored bytes and no Content-Length', async () => {
  const data = await fixture();
  try {
    const result = await verifyPublicCoreDatasetPublication({
      exportRoot: data.exportRoot,
      publication: join(data.bundleRoot, 'publication.json'),
      baseUrl: BASE_URL,
      allowHttpForTests: true,
      logger: silentLogger(),
      localValidator: async () => data.state,
      async fetchImpl(url, init) {
        const path = new URL(url).pathname;
        if (init.method === 'HEAD') {
          const bytes = path.endsWith('/manifest.json') ? data.manifestBytes.length : data.itemsBytes.length;
          return new Response(null, {
            status: 200,
            headers: responseHeaders(bytes, 'application/json', false, true),
          });
        }
        if (path.endsWith('/manifest.json')) {
          return new Response(data.manifestBytes, {
            status: 200,
            headers: responseHeaders(data.manifestBytes.length, 'application/json'),
          });
        }
        const match = /\/assets\/s\/000-(\d+)-(\d+)\.webp$/.exec(path);
        const offset = Number(match[1]);
        const length = Number(match[2]);
        return new Response(data.packBytes.subarray(offset, offset + length), {
          status: 200,
          headers: responseHeaders(length, 'image/webp', true),
        });
      },
    });
    assert.equal(result.documents, 2);
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('public verifier fails closed when one authorized remote image differs from the local pack', async () => {
  const data = await fixture();
  try {
    await assert.rejects(
      verifyPublicCoreDatasetPublication({
        exportRoot: data.exportRoot,
        publication: join(data.bundleRoot, 'publication.json'),
        baseUrl: BASE_URL,
        allowHttpForTests: true,
        logger: silentLogger(),
        localValidator: async () => data.state,
        async fetchImpl(url, init) {
          const path = new URL(url).pathname;
          if (init.method === 'HEAD') {
            const bytes = path.endsWith('/manifest.json') ? data.manifestBytes.length : data.itemsBytes.length;
            return new Response(null, {status: 200, headers: responseHeaders(bytes, 'application/json')});
          }
          if (path.endsWith('/manifest.json')) {
            return new Response(data.manifestBytes, {
              status: 200,
              headers: responseHeaders(data.manifestBytes.length, 'application/json'),
            });
          }
          const match = /\/assets\/s\/000-(\d+)-(\d+)\.webp$/.exec(path);
          const length = Number(match[2]);
          return new Response(Buffer.alloc(length, 0xff), {
            status: 200,
            headers: responseHeaders(length, 'image/webp', true),
          });
        },
      }),
      /differs from the local pack range/,
    );
  } finally {
    await rm(data.root, {recursive: true, force: true});
  }
});

test('public verifier refuses credential-bearing or noncanonical base URLs before local validation', async () => {
  let validated = false;
  await assert.rejects(
    verifyPublicCoreDatasetPublication({
      exportRoot: '/unused',
      publication: '/unused/publication.json',
      baseUrl: 'https://user:secret@public.example/dataset/publications',
      logger: silentLogger(),
      localValidator: async () => { validated = true; },
    }),
    /must not contain credentials/,
  );
  assert.equal(validated, false);
});
