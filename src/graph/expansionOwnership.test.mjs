import assert from 'node:assert/strict';
import test from 'node:test';
import {
  deferredRecipeExpansionNodes,
  duplicateRecipeExpansions,
  findRecipeExpansionOwner,
  recipeExpansionIdentity,
} from './expansionOwnership.ts';

function item(id, key, source) {
  return {
    id,
    key,
    ancestors: [],
    ...(source ? {source} : {}),
  };
}

function recipeSource(id, ref, inputs = []) {
  return {id, kind: 'recipe', ref, inputs};
}

test('recipe identity includes anchor item, direction, and recipe reference', () => {
  assert.notEqual(
    recipeExpansionIdentity('item|test:a', 'inputs', {ref: [1, 2]}),
    recipeExpansionIdentity('item|test:b', 'inputs', {ref: [1, 2]}),
  );
  assert.notEqual(
    recipeExpansionIdentity('item|test:a', 'inputs', {ref: [1, 2]}),
    recipeExpansionIdentity('item|test:a', 'outputs', {ref: [1, 2]}),
  );
});

test('keeps the first pre-order recipe occurrence and identifies later duplicates', () => {
  const first = item(
    'root.s.0',
    'item|test:shared',
    recipeSource('root.s.0.s', [3, 4]),
  );
  const second = item(
    'root.s.1',
    'item|test:shared',
    recipeSource('root.s.1.s', [3, 4]),
  );
  const differentRecipe = item(
    'root.s.2',
    'item|test:shared',
    recipeSource('root.s.2.s', [3, 5]),
  );
  const root = item(
    'root',
    'item|test:result',
    recipeSource('root.s', [0, 0], [first, second, differentRecipe]),
  );

  assert.deepEqual(
    duplicateRecipeExpansions(root, 'inputs').map(entry => entry.node.id),
    ['root.s.1'],
  );
  assert.equal(
    findRecipeExpansionOwner(root, second.key, 'inputs', {ref: [3, 4]}),
    first,
  );
});

test('does not return descendants of a duplicate branch that will be detached', () => {
  const nestedDuplicate = item(
    'root.s.1.s.0',
    'item|test:nested',
    recipeSource('root.s.1.s.0.s', [8, 8]),
  );
  const firstNested = item(
    'root.s.0.s.0',
    'item|test:nested',
    recipeSource('root.s.0.s.0.s', [8, 8]),
  );
  const first = item(
    'root.s.0',
    'item|test:shared',
    recipeSource('root.s.0.s', [3, 4], [firstNested]),
  );
  const second = item(
    'root.s.1',
    'item|test:shared',
    recipeSource('root.s.1.s', [3, 4], [nestedDuplicate]),
  );
  const root = item(
    'root',
    'item|test:result',
    recipeSource('root.s', [0, 0], [first, second]),
  );

  assert.deepEqual(
    duplicateRecipeExpansions(root, 'inputs').map(entry => entry.node.id),
    ['root.s.1'],
  );
});

test('collects reachable deferred recipe nodes for restoration', () => {
  const deferred = item('root.s.0', 'item|test:shared');
  deferred.deferredRecipeExpansion = {ref: [3, 4]};
  const root = item(
    'root',
    'item|test:result',
    recipeSource('root.s', [0, 0], [deferred]),
  );
  assert.deepEqual(deferredRecipeExpansionNodes(root), [deferred]);
});
