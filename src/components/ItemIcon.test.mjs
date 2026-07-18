import assert from 'node:assert/strict';
import test from 'node:test';
import {
  NATIVE_ITEM_ICON_SIZE,
  isPixelGridAlignedItemIconSize,
} from './itemIconSizing.ts';

test('accepts only positive integer multiples of the native JEI icon grid', () => {
  assert.equal(NATIVE_ITEM_ICON_SIZE, 16);
  assert.equal(isPixelGridAlignedItemIconSize(16), true);
  assert.equal(isPixelGridAlignedItemIconSize(32), true);
  assert.equal(isPixelGridAlignedItemIconSize(48), true);
  assert.equal(isPixelGridAlignedItemIconSize(44), false);
  assert.equal(isPixelGridAlignedItemIconSize(18), false);
  assert.equal(isPixelGridAlignedItemIconSize(0), false);
  assert.equal(isPixelGridAlignedItemIconSize(16.5), false);
});
