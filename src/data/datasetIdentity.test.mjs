import assert from 'node:assert/strict';
import test from 'node:test';
import {
  datasetIdentityFromManifest,
  isDatasetPublicationId,
  versionExportUrl,
} from './datasetIdentity.ts';

const PUBLICATION_A = 'a'.repeat(64);
const PUBLICATION_B = 'b'.repeat(64);

const manifest = {
  publicationId: PUBLICATION_A,
  format: 1,
  generatedAt: '2026-07-18T12:34:56.789Z',
  durationMs: 1234,
  minecraft: '1.12.2',
  counts: {
    items: 20,
    recipes: 30,
    categories: 4,
    mobs: 0,
    blockDrops: 0,
    failures: 0,
  },
};

test('publicationId is the authoritative post-transform dataset identity', () => {
  assert.equal(datasetIdentityFromManifest(manifest), PUBLICATION_A);
  assert.equal(
    datasetIdentityFromManifest({
      ...manifest,
      generatedAt: '2099-01-01T00:00:00Z',
      counts: {...manifest.counts, recipes: 999999},
    }),
    PUBLICATION_A,
  );
  assert.equal(
    datasetIdentityFromManifest({...manifest, publicationId: PUBLICATION_B}),
    PUBLICATION_B,
  );
});

test('publication identities must be lowercase 64-character SHA-256 hexadecimal digests', () => {
  assert.equal(isDatasetPublicationId(PUBLICATION_A), true);
  assert.equal(isDatasetPublicationId(PUBLICATION_A.toUpperCase()), false);
  assert.equal(isDatasetPublicationId('a'.repeat(63)), false);
  assert.equal(isDatasetPublicationId(`${'a'.repeat(63)}g`), false);
  assert.throws(() => datasetIdentityFromManifest({}), /manifest\.publicationId/);
  assert.throws(
    () => datasetIdentityFromManifest({...manifest, publicationId: PUBLICATION_A.toUpperCase()}),
    /lowercase 64-character SHA-256/,
  );
});

test('legacy metadata tuples are rejected instead of becoming a silent identity fallback', () => {
  const {publicationId: _omitted, ...legacyManifest} = manifest;
  assert.throws(() => datasetIdentityFromManifest(legacyManifest), /manifest\.publicationId/);
});

test('versionExportUrl attaches and validates the publication identity', () => {
  const versioned = versionExportUrl('exports/items.json', PUBLICATION_A);
  const parsed = new URL(versioned, 'https://example.test/');
  assert.equal(parsed.searchParams.get('dataset'), PUBLICATION_A);
  assert.equal(parsed.pathname, '/exports/items.json');
  assert.throws(
    () => versionExportUrl('exports/items.json', 'legacy-metadata-tuple'),
    /invalid publication identity/,
  );
});
