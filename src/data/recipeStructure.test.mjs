import assert from 'node:assert/strict';
import test from 'node:test';
import {requireRecipeStructure} from './recipeStructure.ts';

const valid = {
  size: [2, 1, 1],
  total: 2,
  controller: 'item|controller',
  blocks: [
    ['item|controller', 1],
    ['item|casing', 1],
  ],
  cells: [
    [0, 0, 0, 'item|controller'],
    [1, 0, 0, 'item|casing'],
  ],
};

test('accepts an exact counted structure payload', () => {
  assert.equal(requireRecipeStructure(valid, 'fixture'), valid);
});

test('rejects mismatched counts and duplicate positions', () => {
  assert.throws(
    () => requireRecipeStructure({...valid, blocks: [['item|controller', 2]]}, 'fixture'),
    /does not match|does not account/,
  );
  assert.throws(
    () =>
      requireRecipeStructure(
        {...valid, cells: [[0, 0, 0, 'item|controller'], [0, 0, 0, 'item|casing']]},
        'fixture',
      ),
    /repeats position/,
  );
});
