import assert from 'node:assert/strict';
import test from 'node:test';
import {
  createDefaultRootProductionPlan,
  DEFAULT_ROOT_AMOUNT,
  MAX_ROOT_AMOUNT,
  normalizeRootAmount,
  rootAmountWheelStep,
} from './rootAmount.ts';

test('new recipe trees start with a planning target of one', () => {
  assert.deepEqual(createDefaultRootProductionPlan(), {
    amount: DEFAULT_ROOT_AMOUNT,
    windowSeconds: 1,
  });
});

test('root amounts clamp to positive whole planning targets', () => {
  assert.equal(normalizeRootAmount(-3), DEFAULT_ROOT_AMOUNT);
  assert.equal(normalizeRootAmount(4.9), 4);
  assert.equal(normalizeRootAmount(MAX_ROOT_AMOUNT + 1), MAX_ROOT_AMOUNT);
  assert.throws(() => normalizeRootAmount(Number.NaN), /finite number/);
});

test('wheel-up increments and wheel-down decrements the root amount', () => {
  assert.equal(rootAmountWheelStep(-1), 1);
  assert.equal(rootAmountWheelStep(1), -1);
  assert.equal(rootAmountWheelStep(0), 0);
  assert.throws(() => rootAmountWheelStep(Number.NaN), /finite/);
});
