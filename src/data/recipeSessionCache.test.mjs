import assert from 'node:assert/strict';
import test from 'node:test';

import {RecipeSessionCache, recipeSessionCacheKey} from './recipeSessionCache.ts';

function recipe(id) {
  return {id, in: [], out: []};
}

test('keeps recently resolved recipes available synchronously', () => {
  const cache = new RecipeSessionCache(2);
  const first = recipe('first');
  cache.set([3, 7], first);

  assert.equal(recipeSessionCacheKey([3, 7]), '3:7');
  assert.equal(cache.peek([3, 7]), first);
  assert.equal(cache.get([3, 7]), first);
});

test('evicts the least recently used resolved recipe at its bound', () => {
  const cache = new RecipeSessionCache(2);
  const first = recipe('first');
  const second = recipe('second');
  const third = recipe('third');
  cache.set([0, 0], first);
  cache.set([0, 1], second);

  assert.equal(cache.get([0, 0]), first);
  cache.set([0, 2], third);

  assert.equal(cache.peek([0, 0]), first);
  assert.equal(cache.peek([0, 1]), undefined);
  assert.equal(cache.peek([0, 2]), third);
});

test('clears recipes when the active dataset changes', () => {
  const cache = new RecipeSessionCache();
  cache.set([1, 2], recipe('cached'));
  cache.clear();
  assert.equal(cache.peek([1, 2]), undefined);
});
