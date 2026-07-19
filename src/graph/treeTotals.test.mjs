import assert from 'node:assert/strict';
import test from 'node:test';
import {calculateTreeTotals} from './treeTotals.ts';

const item = (id, key, amount, options = {}) => ({
  id,
  key,
  amount,
  ancestors: [],
  ...options,
});

test('retained prerequisites use the maximum requirement instead of consumption', () => {
  const catalystA = item('root.s.0', 'item|test:catalyst', 2, {nonConsumed: true});
  const catalystB = item('root.s.1', 'item|test:catalyst', 5, {nonConsumed: true});
  const root = item('root', 'item|test:output', 10, {
    source: {
      id: 'root.s',
      kind: 'recipe',
      recipe: {out: [[['item|test:output', 1]]]},
      inputs: [catalystA, catalystB],
    },
  });

  const totals = calculateTreeTotals(root);
  assert.deepEqual(totals.inputs, []);
  assert.equal(totals.prerequisites.length, 1);
  assert.equal(totals.prerequisites[0].amount, 5);
});

test('byproduct credits reduce consumed inputs and leave residual outputs', () => {
  const root = item('root', 'item|test:machine', 1, {
    source: {
      id: 'root.s',
      kind: 'recipe',
      recipe: {
        out: [
          [['item|test:machine', 1]],
          [['item|test:dust', 3]],
        ],
      },
      inputs: [item('root.s.0', 'item|test:dust', 5)],
    },
  });

  const withoutCredits = calculateTreeTotals(root, false);
  assert.equal(withoutCredits.inputs[0].amount, 5);
  assert.equal(withoutCredits.byproducts[0].amount, 3);

  const withCredits = calculateTreeTotals(root, true);
  assert.equal(withCredits.inputs[0].amount, 2);
  assert.equal(withCredits.byproductCredits[0].amount, 3);
  assert.deepEqual(withCredits.byproducts, []);
});

test('byproduct credits require an exact logical ingredient identity', () => {
  const root = item('root', 'item|test:machine', 1, {
    source: {
      id: 'root.s',
      kind: 'recipe',
      recipe: {
        out: [
          [['item|test:machine', 1]],
          [['item|test:copper_a', 2, 'ore:ingotCopper']],
        ],
      },
      inputs: [
        item('root.s.0', 'item|test:copper_b', 4, {
          tag: 'ore:ingotCopper',
          alternatives: ['item|test:copper_a', 'item|test:copper_b'],
          variantCount: 2,
        }),
      ],
    },
  });

  const totals = calculateTreeTotals(root, true);
  assert.equal(totals.inputs[0].amount, 2);
  assert.equal(totals.inputs[0].tag, 'ore:ingotCopper');
  assert.equal(totals.byproductCredits[0].amount, 2);
});
