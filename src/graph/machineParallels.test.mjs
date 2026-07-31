import assert from 'node:assert/strict';
import test from 'node:test';
import {
  defaultRecipeCycleSeconds,
  estimateParallelMachines,
  recipeCycleSeconds,
  selectedRecipeOutput,
} from './machineParallels.ts';

const recipe = {
  durationTicks: 200,
  out: [[['item|test:plate', 2]]],
};

test('uses exported recipe duration and selected output yield', () => {
  assert.equal(recipeCycleSeconds(recipe, 'mod:pressing'), 10);
  assert.equal(selectedRecipeOutput(recipe, 'item|test:plate'), 2);
  assert.deepEqual(
    estimateParallelMachines(recipe, 'item|test:plate', 'mod:pressing', {
      amount: 100,
      windowSeconds: 60,
    }),
    {
      machines: 9,
      cyclesRequired: 50,
      cyclesPerMachine: 6,
      outputPerCycle: 2,
      cycleSeconds: 10,
    },
  );
});

test('counts only cycles completed before the deadline', () => {
  const estimate = estimateParallelMachines(recipe, 'item|test:plate', 'mod:pressing', {
    amount: 10,
    windowSeconds: 15,
  });
  assert.equal(estimate?.machines, 5);
  assert.equal(estimate?.cyclesPerMachine, 1);
});

test('supports exact vanilla fallbacks and explicit modded duration overrides', () => {
  assert.equal(defaultRecipeCycleSeconds('minecraft:blasting'), 5);
  assert.equal(defaultRecipeCycleSeconds('mod:crusher'), null);
  assert.equal(recipeCycleSeconds({out: []}, 'mod:crusher', 2.5), 2.5);
});

test('does not fabricate recommendations from unknown or stochastic outputs', () => {
  assert.equal(
    estimateParallelMachines(
      {durationTicks: 20, out: [[['item|test:dust', 1, null, 0.5]]]},
      'item|test:dust',
      'mod:crusher',
      {amount: 10, windowSeconds: 60},
    ),
    null,
  );
  assert.equal(
    estimateParallelMachines({out: recipe.out}, 'item|test:plate', 'mod:crusher', {
      amount: 10,
      windowSeconds: 60,
    }),
    null,
  );
});
