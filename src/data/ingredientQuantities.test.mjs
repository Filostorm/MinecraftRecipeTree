import assert from 'node:assert/strict';
import test from 'node:test';
import {
  formatAmount,
  formatIngredientQuantity,
  formatIngredientQuantityPrefix,
  ingredientQuantityUnit,
  normalizeRecipeInputAmount,
  shouldShowIngredientQuantity,
} from './ingredientQuantities.ts';

test('never formats a positive sub-hundredth quantity as zero', () => {
  assert.equal(formatAmount(0), '0');
  assert.equal(formatAmount(0.004), '0.004');
  assert.equal(formatAmount(0.00048828125), '0.000488');
  assert.equal(formatAmount(0.0000004), '<0.000001');
});

test('formats EMC as a continuous named resource rather than an item stack', () => {
  assert.equal(shouldShowIngredientQuantity('emc|projecte:emc', 1), true);
  assert.equal(formatIngredientQuantity('emc|projecte:emc', 1), '1 EMC');
  assert.equal(formatIngredientQuantityPrefix('emc|projecte:emc', 1), '1');
  assert.equal(formatIngredientQuantity('emc|projecte:emc', 8192), '8192 EMC');
  assert.equal(formatIngredientQuantityPrefix('emc|projecte:emc', 8192), '8192');
  assert.equal(normalizeRecipeInputAmount('emc|projecte:emc', 12.5), 12.5);
  assert.equal(ingredientQuantityUnit('emc|projecte:emc'), 'EMC');
});

test('rounds fractional items upward while preserving bulk precision', () => {
  assert.equal(formatIngredientQuantity('item|test:dust', 0.004), '×1');
  assert.equal(formatIngredientQuantity('item|test:dust', 1.2), '×2');
  assert.equal(formatIngredientQuantity('fluid|test:essence', 0.004), '0.004 mB');
  assert.equal(formatIngredientQuantityPrefix('item|test:dust', 0.004), '1×');
});
