import assert from 'node:assert/strict';
import test from 'node:test';
import {starterItem} from './starterItem.ts';

test('starter selects a known item from this pack, independent of catalog order', () => {
  const furnace = {id: 'minecraft:furnace', k: 'furnace'};
  const table = {id: 'minecraft:crafting_table', k: 'table'};
  assert.equal(starterItem([furnace, table]), table);
  assert.equal(starterItem([furnace]), furnace);
  assert.equal(starterItem([{id: 'other:unknown'}]), undefined);
  assert.equal(starterItem([]), undefined);
});
