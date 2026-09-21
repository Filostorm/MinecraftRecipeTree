import assert from 'node:assert/strict';
import test from 'node:test';
import {popularityOrder} from './popularityOrder.ts';
test('community ranking moves existing keys first and leaves unranked catalog order stable', () => {
  const items = ['a', 'b', 'c', 'd'].map(k => ({k}));
  assert.deepEqual(popularityOrder(items, ['c', 'gone', 'a', 'c']).map(item => item.k), ['c', 'a', 'b', 'd']);
  assert.deepEqual(popularityOrder(items, []), items);
  assert.deepEqual(items.map(item => item.k), ['a', 'b', 'c', 'd']);
});
