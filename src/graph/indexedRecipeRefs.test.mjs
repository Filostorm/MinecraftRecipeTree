import assert from 'node:assert/strict';
import test from 'node:test';
import {indexedRecipeRefs} from './indexedRecipeRefs.ts';

test('unions and deduplicates recipes for every ore dictionary member', () => {
  const index = {
    'item|test:copper_a': {p: [[1, 2]], u: [[3, 4]]},
    'item|test:copper_b': {p: [[1, 2], [5, 6]], u: [[7, 8]]},
  };
  assert.deepEqual(
    indexedRecipeRefs(index, 'item|test:copper_a', [
      'item|test:copper_a',
      'item|test:copper_b',
    ], 'p'),
    [[1, 2], [5, 6]],
  );
  assert.deepEqual(
    indexedRecipeRefs(index, 'item|test:copper_a', [
      'item|test:copper_b',
    ], 'u'),
    [[3, 4], [7, 8]],
  );
});
