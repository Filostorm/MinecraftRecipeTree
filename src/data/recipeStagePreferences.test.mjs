import assert from 'node:assert/strict';
import test from 'node:test';
import {
  parseHiddenRecipeStages,
  toggleHiddenRecipeStage,
} from './recipeStagePreferences.ts';

test('parses a versioned hidden recipe-stage set', () => {
  assert.deepEqual(
    [...parseHiddenRecipeStages(JSON.stringify({version: 1, hidden: ['hardmode', 'sedna']}))],
    ['hardmode', 'sedna'],
  );
});

test('toggles recipe stages without mutating the previous set', () => {
  const initial = new Set(['hardmode']);
  const shown = toggleHiddenRecipeStage(initial, 'hardmode');
  const hidden = toggleHiddenRecipeStage(shown, 'sedna');
  assert.deepEqual([...initial], ['hardmode']);
  assert.deepEqual([...shown], []);
  assert.deepEqual([...hidden], ['sedna']);
});

test('rejects malformed stage identifiers and unversioned settings', () => {
  assert.throws(() => parseHiddenRecipeStages('{"hidden":[]}'), /storage contract/);
  assert.throws(
    () => parseHiddenRecipeStages(JSON.stringify({version: 1, hidden: ['bad stage']})),
    /storage contract/,
  );
  assert.throws(() => toggleHiddenRecipeStage(new Set(), 'bad stage'), /invalid recipe stage/);
});
