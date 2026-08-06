import assert from 'node:assert/strict';
import test from 'node:test';
import {materialInputSummary, recipeChildrenForDirection} from '../graph/direction.ts';
import {calculateTreeTotals} from '../graph/treeTotals.ts';
import {prerequisiteSummary} from './slotSummary.ts';
import {promoteReturnedRecipeIngredients} from './returnedRecipeIngredients.ts';

test('requires one returned mold for any number of recipe runs', () => {
  const recipe = {
    in: [[['item|test:mold', 1]], [['item|test:molten_metal', 4]]],
    out: [[['item|test:plate', 2]], [['item|test:mold', 1]]],
  };

  const normalized = promoteReturnedRecipeIngredients(recipe);

  assert.equal(materialInputSummary(normalized).length, 1);
  assert.equal(materialInputSummary(normalized)[0].key, 'item|test:molten_metal');
  assert.equal(prerequisiteSummary(normalized.cat)[0].key, 'item|test:mold');
  assert.equal(prerequisiteSummary(normalized.cat)[0].amount, 1);
  assert.deepEqual(normalized.out, [[['item|test:plate', 2]]]);

  const children = recipeChildrenForDirection(normalized, 'inputs').map((child, index) => ({
    id: `root.s.${index}`,
    key: child.key,
    amount: child.amount,
    alternatives: child.alternatives,
    variantCount: child.variants,
    tag: child.tag,
    nonConsumed: child.nonConsumed,
    ancestors: [],
  }));
  const totals = calculateTreeTotals({
    id: 'root',
    key: 'item|test:plate',
    amount: 2,
    productionPlan: {amount: 200, windowSeconds: 60},
    ancestors: [],
    source: {
      id: 'root.s',
      kind: 'recipe',
      recipe: normalized,
      inputs: children,
    },
  });
  assert.equal(totals.prerequisites[0].key, 'item|test:mold');
  assert.equal(totals.prerequisites[0].amount, 1);
});

test('does not reinterpret an increased output as a returned ingredient', () => {
  const recipe = {
    in: [[['item|test:seed', 1]]],
    out: [[['item|test:seed', 2]]],
  };

  assert.equal(promoteReturnedRecipeIngredients(recipe), recipe);
});

test('matches an unchanged alternative slot regardless of member order', () => {
  const recipe = {
    in: [[['item|test:mold_a', 1], ['item|test:mold_b', 1]], [['item|test:dust', 1]]],
    out: [[['item|test:result', 1]], [['item|test:mold_b', 1], ['item|test:mold_a', 1]]],
  };

  const normalized = promoteReturnedRecipeIngredients(recipe);

  assert.equal(normalized.in.length, 1);
  assert.equal(normalized.out.length, 1);
  assert.equal(normalized.cat.length, 1);
  assert.equal(prerequisiteSummary(normalized.cat)[0].amount, 1);
});
