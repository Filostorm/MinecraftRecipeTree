import assert from 'node:assert/strict';
import test from 'node:test';
import {inputSlotSummary, prerequisiteSummary, slotSummary} from './slotSummary.ts';

const ENDER_IO_ENERGY =
  'custom_crazypants.enderio.base.integration.jei.energy.energyingredient_4926a629|enderio:energy';

test('input summaries exclude Ender IO JEI energy pseudo-resources without hiding outputs', () => {
  const infos = [];
  const originalInfo = console.info;
  console.info = (...parts) => infos.push(parts);
  try {
    assert.deepEqual(inputSlotSummary([[[ENDER_IO_ENERGY, 2000]]]), []);
  } finally {
    console.info = originalInfo;
  }
  assert.equal(infos.length, 1);
  assert.match(String(infos[0][0]), /excluded from recipe material inputs/);
  assert.equal(slotSummary([[[ENDER_IO_ENERGY, 2000]]])[0].amount, 2000);
});

test('input summaries ceil discrete counts to at least one while preserving bulk quantities', () => {
  assert.equal(inputSlotSummary([[['item|example:dust', 0.01]]])[0].amount, 1);
  assert.equal(inputSlotSummary([[['item|example:dust', 1.01]]])[0].amount, 2);
  assert.equal(inputSlotSummary([[['fluid|example:water', 0.25]]])[0].amount, 0.25);
  assert.equal(prerequisiteSummary([[['item|example:mold', 0.01]]])[0].amount, 1);
  assert.equal(slotSummary([[['item|example:result', 0.01]]])[0].amount, 0.01);
});

test('preserves one exact quantity when every slot alternative agrees', () => {
  assert.deepEqual(
    slotSummary([[['item|example:first', 3], ['item|example:second', 3]]]),
    [
      {
        key: 'item|example:first',
        amount: 3,
        variableAmount: false,
        variants: 2,
        alternatives: ['item|example:first', 'item|example:second'],
        tag: undefined,
      },
    ],
  );
});

test('does not merge distinct singleton positions that only share tag provenance', () => {
  assert.deepEqual(
    slotSummary([
      [['item|example:first_food', 1, 'ore:listAllFood']],
      [['item|example:second_food', 1, 'ore:listAllFood']],
      [['item|example:first_food', 1, 'ore:listAllFood']],
    ]),
    [
      {
        key: 'item|example:first_food',
        amount: 2,
        variableAmount: false,
        variants: 1,
        alternatives: ['item|example:first_food'],
        tag: 'ore:listAllFood',
      },
      {
        key: 'item|example:second_food',
        amount: 1,
        variableAmount: false,
        variants: 1,
        alternatives: ['item|example:second_food'],
        tag: 'ore:listAllFood',
      },
    ],
  );
});

test('marks heterogeneous alternative quantities unknown and logs the loss of one aggregate', () => {
  const warnings = [];
  const originalWarn = console.warn;
  console.warn = (...parts) => warnings.push(parts);
  try {
    assert.deepEqual(
      slotSummary([[['item|example:first', 1], ['item|example:second', 64]]]),
      [
        {
          key: 'item|example:first',
          amount: null,
          variableAmount: true,
          variants: 2,
          alternatives: ['item|example:first', 'item|example:second'],
          tag: undefined,
        },
      ],
    );
  } finally {
    console.warn = originalWarn;
  }
  assert.equal(warnings.length, 1);
  assert.match(String(warnings[0][0]), /different quantities/);
});

test('preserves one stochastic occurrence probability shared by all alternatives', () => {
  assert.deepEqual(
    slotSummary([
      [
        ['item|example:first', 3, null, 0.125],
        ['item|example:second', 3, null, 0.125],
      ],
    ]),
    [
      {
        key: 'item|example:first',
        amount: 3,
        probability: 0.125,
        variableAmount: false,
        variants: 2,
        alternatives: ['item|example:first', 'item|example:second'],
        tag: undefined,
      },
    ],
  );
});

test('marks conflicting alternative probabilities unknown and logs the semantic conflict', () => {
  const errors = [];
  const originalError = console.error;
  console.error = (...parts) => errors.push(parts);
  try {
    assert.deepEqual(
      slotSummary([
        [
          ['item|example:first', 1, null, 0.25],
          ['item|example:second', 1, null, 0.5],
        ],
      ]),
      [
        {
          key: 'item|example:first',
          amount: 1,
          probability: null,
          variableAmount: false,
          variants: 2,
          alternatives: ['item|example:first', 'item|example:second'],
          tag: undefined,
        },
      ],
    );
  } finally {
    console.error = originalError;
  }
  assert.equal(errors.length, 1);
  assert.match(String(errors[0][0]), /conflicting occurrence probabilities/);
});
