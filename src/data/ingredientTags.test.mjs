import assert from 'node:assert/strict';
import test from 'node:test';
import {displayIngredientName, inferIngredientTag} from './ingredientTags.ts';

test('uses an explicit 1.12 OreDictionary identity for resolved alternatives', () => {
  const slot = [
    ['item|thermalfoundation:material@64', 1, 'ore:ingotCopper'],
    ['item|immersiveengineering:metal@0', 1, 'ore:ingotCopper'],
  ];
  assert.equal(inferIngredientTag(slot), 'ore:ingotCopper');
});

test('does not silently choose between conflicting exported identities', () => {
  const originalError = console.error;
  const errors = [];
  console.error = (...args) => errors.push(args);
  try {
    assert.equal(
      inferIngredientTag([
        ['item|example:copper', 1, 'ore:ingotCopper'],
        ['item|example:tin', 1, 'ore:ingotTin'],
      ]),
      undefined,
    );
  } finally {
    console.error = originalError;
  }
  assert.equal(errors.length, 1);
});

test('presents Forge 1.12 OreDictionary ingredients by their selected item name', () => {
  assert.equal(
    displayIngredientName('Nickel Ingot', 'ore:ingotNickel', '1.12.2'),
    'Nickel Ingot',
  );
  assert.equal(
    displayIngredientName('Nickel Ingot', 'ore:ingotNickel', '1.7.10'),
    '#ore:ingotNickel',
  );
  assert.equal(
    displayIngredientName('Oak Planks', 'minecraft:planks', '1.20.1'),
    '#minecraft:planks',
  );
});
