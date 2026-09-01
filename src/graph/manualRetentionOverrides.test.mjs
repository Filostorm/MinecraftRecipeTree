import assert from 'node:assert/strict';
import test from 'node:test';
import {
  loadManualRetentionOverrides,
  manualRetentionOverrideFor,
  manualRetentionOverrideKey,
  persistManualRetentionOverrides,
} from './manualRetentionOverrides.ts';

const descriptor = {
  slug: 'meatballcraft',
  publicationId: 'a'.repeat(64),
};

test('manual reusable corrections persist per immutable pack version and recipe input', () => {
  const values = new Map();
  const original = globalThis.localStorage;
  globalThis.localStorage = {
    getItem(key) {
      return values.get(key) ?? null;
    },
    setItem(key, value) {
      values.set(key, value);
    },
  };
  try {
    const key = manualRetentionOverrideKey([4, 12], 'item|projecte:philosophers_stone');
    persistManualRetentionOverrides(descriptor, {[key]: true});
    const loaded = loadManualRetentionOverrides(descriptor);
    assert.equal(
      manualRetentionOverrideFor(
        loaded,
        [4, 12],
        'item|projecte:philosophers_stone',
      ),
      true,
    );
    assert.equal(manualRetentionOverrideFor(loaded, [4, 13], 'item|projecte:philosophers_stone'), undefined);
  } finally {
    globalThis.localStorage = original;
  }
});

test('manual consumed corrections remain explicit instead of being treated as absent', () => {
  const key = manualRetentionOverrideKey([1, 2], 'item|test:tool');
  assert.equal(manualRetentionOverrideFor({[key]: false}, [1, 2], 'item|test:tool'), false);
});
