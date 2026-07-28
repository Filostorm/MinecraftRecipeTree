import assert from 'node:assert/strict';
import test from 'node:test';
import {
  parseCollapsedRecipeCategories,
  toggleCollapsedRecipeCategory,
} from './recipeCategoryPreferences.ts';

test('parses a versioned set of collapsed recipe categories', () => {
  assert.deepEqual(
    [...parseCollapsedRecipeCategories(JSON.stringify({version: 1, collapsed: ['jei:a', 'jei:b']}))],
    ['jei:a', 'jei:b'],
  );
});

test('toggles category ids without mutating the prior setting', () => {
  const initial = new Set(['jei:a']);
  const expanded = toggleCollapsedRecipeCategory(initial, 'jei:a');
  const collapsed = toggleCollapsedRecipeCategory(expanded, 'jei:b');
  assert.deepEqual([...initial], ['jei:a']);
  assert.deepEqual([...expanded], []);
  assert.deepEqual([...collapsed], ['jei:b']);
});

test('rejects malformed and unversioned category settings', () => {
  assert.throws(() => parseCollapsedRecipeCategories('{"collapsed":[]}'), /storage contract/);
  assert.throws(
    () => parseCollapsedRecipeCategories(JSON.stringify({version: 1, collapsed: ['']})),
    /storage contract/,
  );
});
