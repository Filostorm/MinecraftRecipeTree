import assert from 'node:assert/strict';
import test from 'node:test';
import {
  generatedMobPlaceholderColor,
  generatedMobPlaceholderLabel,
} from './mobPlaceholder.ts';

test('mob placeholder color is stable and identifier-derived', () => {
  assert.equal(
    generatedMobPlaceholderColor('Thaumcraft.EldritchGolem'),
    '#4c568f',
  );
  assert.notEqual(
    generatedMobPlaceholderColor('Thaumcraft.EldritchGolem'),
    generatedMobPlaceholderColor('minecraft:zombie'),
  );
});

test('mob placeholder labels are deterministic, compact, and explicit for blank names', () => {
  assert.equal(generatedMobPlaceholderLabel('Eldritch Guardian'), 'EG');
  assert.equal(generatedMobPlaceholderLabel('Hydra'), 'HY');
  assert.equal(generatedMobPlaceholderLabel('  '), '?');
});
