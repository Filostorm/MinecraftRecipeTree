import assert from 'node:assert/strict';
import test from 'node:test';
import {
  MAX_RECIPE_HISTORY_ENTRIES,
  mergeRecipeHistory,
  parseRecipeHistory,
  recipeHistoryStorageKey,
} from './recipeHistory.ts';

const scope = {slug: 'meatballcraft', publicationId: 'a'.repeat(64)};
const entry = (index, openedAt = index + 1, direction = undefined) => ({
  itemKey: `item|example:item_${index}`,
  ref: [2, index],
  title: `Machine ${index}`,
  recipeId: `example:recipe_${index}`,
  openedAt,
  ...(direction ? {direction} : {}),
});

test('isolates recipe history by pack publication', () => {
  assert.equal(
    recipeHistoryStorageKey(scope),
    `recipeHistory:v1:meatballcraft:${'a'.repeat(64)}`,
  );
  assert.notEqual(
    recipeHistoryStorageKey(scope),
    recipeHistoryStorageKey({...scope, slug: 'gtnh'}),
  );
  assert.notEqual(
    recipeHistoryStorageKey(scope),
    recipeHistoryStorageKey({...scope, publicationId: 'b'.repeat(64)}),
  );
});

test('moves repeated recipes to the front and retains the newest timestamp', () => {
  const initial = [entry(1, 10), entry(2, 20)];
  const repeated = entry(1, 30);
  assert.deepEqual(mergeRecipeHistory(initial, repeated), [repeated, entry(2, 20)]);
});

test('caps recipe history without silently dropping the newest recipe', () => {
  const initial = Array.from({length: MAX_RECIPE_HISTORY_ENTRIES}, (_, index) => entry(index));
  const newest = entry(MAX_RECIPE_HISTORY_ENTRIES, 999);
  const merged = mergeRecipeHistory(initial, newest);
  assert.equal(merged.length, MAX_RECIPE_HISTORY_ENTRIES);
  assert.deepEqual(merged[0], newest);
  assert.equal(merged.some(candidate => candidate.itemKey === entry(49).itemKey), false);
});

test('rejects malformed or unversioned stored history', () => {
  assert.throws(() => parseRecipeHistory('{"entries":[]}'), /versioned storage contract/);
  assert.throws(
    () =>
      parseRecipeHistory(
        JSON.stringify({version: 1, entries: [{...entry(1), ref: [-1, 2]}]}),
      ),
    /storage contract/,
  );
});

test('treats legacy entries as input trees and preserves explicit output trees', () => {
  const parsed = parseRecipeHistory(
    JSON.stringify({
      version: 1,
      entries: [entry(1, 10), entry(2, 20, 'outputs')],
    }),
  );
  assert.equal(parsed[0].direction, 'inputs');
  assert.equal(parsed[1].direction, 'outputs');
});

test('keeps input- and output-directed trees as distinct history entries', () => {
  const inputTree = entry(1, 10, 'inputs');
  const outputTree = entry(1, 20, 'outputs');
  assert.deepEqual(mergeRecipeHistory([inputTree], outputTree), [outputTree, inputTree]);
});
