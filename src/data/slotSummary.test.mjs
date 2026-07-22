import assert from 'node:assert/strict';
import test from 'node:test';
import {slotSummary} from './slotSummary.ts';

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
