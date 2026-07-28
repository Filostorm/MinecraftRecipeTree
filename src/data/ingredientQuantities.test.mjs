import assert from 'node:assert/strict';
import test from 'node:test';
import {
  formatAmount,
  formatIngredientQuantity,
  formatIngredientQuantityPrefix,
} from './ingredientQuantities.ts';

test('never formats a positive sub-hundredth quantity as zero', () => {
  assert.equal(formatAmount(0), '0');
  assert.equal(formatAmount(0.004), '0.004');
  assert.equal(formatAmount(0.00048828125), '0.000488');
  assert.equal(formatAmount(0.0000004), '<0.000001');
});

test('preserves nonzero small quantities in item, bulk, and prefix notation', () => {
  assert.equal(formatIngredientQuantity('item|test:dust', 0.004), '×0.004');
  assert.equal(formatIngredientQuantity('fluid|test:essence', 0.004), '0.004 mB');
  assert.equal(formatIngredientQuantityPrefix('item|test:dust', 0.004), '0.004×');
});
