import assert from 'node:assert/strict';
import test from 'node:test';

import {requireLocalPackDelta} from './localPackDelta.ts';

const BASE = 'a'.repeat(64);
const RESULT = 'b'.repeat(64);
const FILE_HASH = 'c'.repeat(64);

function delta(overrides = {}) {
  return {
    format: 'mrt-export-delta-v1',
    createdAt: '2026-08-03T00:00:00Z',
    basePublicationId: BASE,
    resultPublicationId: RESULT,
    minecraft: '1.20.1',
    pack: {name: 'Update Pack', baseVersion: '1.0.0', resultVersion: '1.0.1'},
    files: [
      {path: 'manifest.json', size: 10, sha256: RESULT},
      {path: 'recipes/example/recipes.json', size: 20, sha256: FILE_HASH},
    ],
    deletedPaths: ['recipes/example/old.png'],
    counts: {
      changedFiles: 2,
      deletedFiles: 1,
      unchangedFiles: 3,
      resultFiles: 5,
      changedBytes: 30,
      resultBytes: 100,
    },
    ...overrides,
  };
}

test('accepts a bounded, internally consistent single-base delta', () => {
  const parsed = requireLocalPackDelta(delta());
  assert.equal(parsed.basePublicationId, BASE);
  assert.equal(parsed.resultPublicationId, RESULT);
  assert.equal(parsed.counts.resultFiles, 5);
  assert.deepEqual(parsed.deletedPaths, ['recipes/example/old.png']);
});

test('requires the result manifest hash and exact inventory counts', () => {
  assert.throws(
    () => requireLocalPackDelta(delta({
      files: [{path: 'manifest.json', size: 10, sha256: FILE_HASH}],
      counts: {
        changedFiles: 1,
        deletedFiles: 1,
        unchangedFiles: 3,
        resultFiles: 4,
        changedBytes: 10,
        resultBytes: 100,
      },
    })),
    /result manifest/u,
  );
  assert.throws(
    () => requireLocalPackDelta(delta({counts: {...delta().counts, changedBytes: 29}})),
    /counts do not match/u,
  );
});

test('rejects overlapping changed and deleted paths', () => {
  assert.throws(
    () => requireLocalPackDelta(delta({deletedPaths: ['manifest.json']})),
    /both changes and deletes/u,
  );
});
