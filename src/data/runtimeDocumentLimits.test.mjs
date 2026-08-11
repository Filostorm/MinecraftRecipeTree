import assert from 'node:assert/strict';
import test from 'node:test';

import {
  MAX_LEGACY_LOCAL_DOCUMENT_BYTES,
  MAX_NETWORK_DOCUMENT_BYTES,
  isLocalPackExportUrl,
  runtimeDocumentByteLimit,
} from './runtimeDocumentLimits.ts';

const publicationId = '9646b38577be1950e0a17564394c359017e0604508a27102cc7cfe0b1049a421';

test('allows bounded legacy inline documents from an installed local pack', () => {
  const url = `/__local-packs/${publicationId}/exports/index.json?dataset=${publicationId}`;
  assert.equal(isLocalPackExportUrl(url), true);
  assert.equal(runtimeDocumentByteLimit(url), MAX_LEGACY_LOCAL_DOCUMENT_BYTES);
  assert.equal(MAX_LEGACY_LOCAL_DOCUMENT_BYTES, 32 * 1024 * 1024);
});

test('recognizes durable native local-pack files', () => {
  const url =
    `file:///mobile/Documents/minecraft-recipe-tree/local-packs/${publicationId}` +
    `/exports/index.json?dataset=${publicationId}`;
  assert.equal(isLocalPackExportUrl(url), true);
  assert.equal(runtimeDocumentByteLimit(url), MAX_LEGACY_LOCAL_DOCUMENT_BYTES);
});

test('keeps published documents and malformed local paths on the strict network limit', () => {
  assert.equal(
    runtimeDocumentByteLimit(`/api/publications/${publicationId}/exports/index.json`),
    MAX_NETWORK_DOCUMENT_BYTES,
  );
  assert.equal(
    runtimeDocumentByteLimit('/__local-packs/not-a-publication/exports/index.json'),
    MAX_NETWORK_DOCUMENT_BYTES,
  );
  assert.equal(MAX_NETWORK_DOCUMENT_BYTES, 8 * 1024 * 1024);
});
