import assert from 'node:assert/strict';
import test from 'node:test';
import {
  coreDatasetPublicationManifestBytes,
  requireCanonicalCoreDatasetPublicationBytes,
  requireCoreDatasetPublicationManifest,
} from './core-dataset-publication-contract.mjs';
import {
  encodePackedImageAuthorizationIndex,
  parsePackedImageAuthorizationIndex,
} from './packed-image-authorization.mjs';
import {
  canonicalCoreDatasetPublicationBytes as workerManifestBytes,
  requireCoreDatasetPublicationManifest as requireWorkerManifest,
} from '../worker/coreDatasetContract.ts';

const DIGEST = 'a'.repeat(64);

function manifestFixture() {
  return {
    format: 'mrt-core-dataset-publication-v1',
    publicationId: DIGEST,
    maxDocumentBytes: 8 * 1024 * 1024,
    maxPackBytes: 1024 * 1024,
    packIndexFormat: 'mrt-packed-image-authorization-index-v1',
    maxPackIndexBytes: 512 * 1024,
    counts: {
      documents: 1,
      packs: 1,
      packedImages: 2,
      documentBytes: 10,
      packBytes: 30,
      packIndexBytes: 36,
      objects: 3,
      storedBytes: 76,
    },
    documents: [{path: 'manifest.json', bytes: 10, sha256: DIGEST}],
    packs: [{
      path: 'assets/pack-000.bin',
      bytes: 30,
      sha256: DIGEST,
      index: {path: 'indexes/pack-000.bin', bytes: 36, sha256: DIGEST, entries: 2},
    }],
  };
}

test('MRPI v1 binary round-trips only exact contiguous full-pack boundaries', () => {
  const bytes = encodePackedImageAuthorizationIndex({
    packNumber: 7,
    packBytes: 30,
    entries: [[0, 11], [11, 19]],
  });
  assert.equal(bytes.subarray(0, 4).toString('ascii'), 'MRPI');
  assert.deepEqual(
    parsePackedImageAuthorizationIndex(bytes, {expectedPackNumber: 7, expectedPackBytes: 30}),
    {packNumber: 7, packBytes: 30, entries: [[0, 11], [11, 19]]},
  );
  assert.throws(
    () => encodePackedImageAuthorizationIndex({
      packNumber: 7,
      packBytes: 30,
      entries: [[0, 11], [12, 18]],
    }),
    /not the canonical contiguous range/,
  );
  assert.throws(
    () => encodePackedImageAuthorizationIndex({
      packNumber: 7,
      packBytes: 30,
      entries: [[0, 29]],
    }),
    /cover 29\/30/,
  );
});

test('MRPI parser rejects altered magic, version, framing, entries, and expected pack metadata', () => {
  const original = encodePackedImageAuthorizationIndex({
    packNumber: 0,
    packBytes: 30,
    entries: [[0, 11], [11, 19]],
  });
  const cases = [
    [0, 0, /invalid magic/],
    [5, 2, /unsupported version/],
    [7, 21, /non-canonical header/],
    [19, 3, /declares 3 entries/],
    [27, 12, /not a canonical contiguous/],
  ];
  for (const [offset, value, pattern] of cases) {
    const changed = Buffer.from(original);
    changed[offset] = value;
    assert.throws(() => parsePackedImageAuthorizationIndex(changed), pattern);
  }
  assert.throws(
    () => parsePackedImageAuthorizationIndex(original, {expectedPackNumber: 1}),
    /targets pack 0; expected 1/,
  );
  assert.throws(
    () => parsePackedImageAuthorizationIndex(original, {expectedPackBytes: 31}),
    /declares 30 pack bytes; expected 31/,
  );
});

test('control manifest has one canonical byte representation and exact aggregate counts', () => {
  const manifest = requireCoreDatasetPublicationManifest(manifestFixture(), DIGEST);
  const bytes = coreDatasetPublicationManifestBytes(manifest);
  assert.equal(bytes.at(-1), 10);
  assert.equal(requireCanonicalCoreDatasetPublicationBytes(bytes, DIGEST).publicationId, DIGEST);
  const pretty = Buffer.from(`${JSON.stringify(manifest, null, 2)}\n`);
  assert.throws(
    () => requireCanonicalCoreDatasetPublicationBytes(pretty, DIGEST),
    /not in canonical byte form/,
  );
  assert.throws(
    () => requireCoreDatasetPublicationManifest({
      ...manifest,
      counts: {...manifest.counts, storedBytes: 77},
    }),
    /count storedBytes is 77; expected 76/,
  );
});

test('local builder and Worker ingestion retain byte-exact control-manifest parity', () => {
  const manifest = requireCoreDatasetPublicationManifest(manifestFixture(), DIGEST);
  assert.deepEqual(requireWorkerManifest(manifest, DIGEST).manifest, manifest);
  assert.deepEqual(
    Buffer.from(workerManifestBytes(manifest)),
    coreDatasetPublicationManifestBytes(manifest),
  );
});

test('control manifest refuses alternate paths, ordering, and undeclared fields', () => {
  const manifest = manifestFixture();
  assert.throws(
    () => requireCoreDatasetPublicationManifest({
      ...manifest,
      documents: [
        {path: 'z.json', bytes: 1, sha256: DIGEST},
        {path: 'manifest.json', bytes: 9, sha256: DIGEST},
      ],
      counts: {
        ...manifest.counts,
        documents: 2,
        documentBytes: 10,
        objects: 4,
      },
    }),
    /strictly sorted canonical paths/,
  );
  assert.throws(
    () => requireCoreDatasetPublicationManifest({
      ...manifest,
      packs: [{...manifest.packs[0], unexpected: true}],
    }),
    /unsupported or missing fields/,
  );
  assert.throws(
    () => requireCoreDatasetPublicationManifest({
      ...manifest,
      documents: [{...manifest.documents[0], path: '../manifest.json'}],
    }),
    /not a canonical bounded content record/,
  );
  for (const path of ['publication.json', 'indexes/manifest.json']) {
    assert.throws(
      () => requireCoreDatasetPublicationManifest({
        ...manifest,
        documents: [{...manifest.documents[0], path}],
      }),
      /reserved publication namespace/,
    );
  }
});
