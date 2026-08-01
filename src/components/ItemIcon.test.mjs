import assert from 'node:assert/strict';
import test from 'node:test';
import {
  LOGICAL_ITEM_ICON_GRID_SIZE,
  RADIAL_ROOT_ITEM_ICON_SIZE,
  RECIPE_HISTORY_ITEM_ICON_SIZE,
  ROOT_QUICK_ACTION_ITEM_ICON_SIZE,
  isPixelGridAlignedItemIconSize,
} from './itemIconSizing.ts';

test('accepts only positive integer multiples of the logical JEI icon grid', () => {
  assert.equal(LOGICAL_ITEM_ICON_GRID_SIZE, 16);
  assert.equal(isPixelGridAlignedItemIconSize(16), true);
  assert.equal(isPixelGridAlignedItemIconSize(32), true);
  assert.equal(isPixelGridAlignedItemIconSize(48), true);
  assert.equal(isPixelGridAlignedItemIconSize(44), false);
  assert.equal(isPixelGridAlignedItemIconSize(18), false);
  assert.equal(isPixelGridAlignedItemIconSize(0), false);
  assert.equal(isPixelGridAlignedItemIconSize(16.5), false);
});

test('keeps recipe-history icons aligned to the logical pixel grid', () => {
  assert.equal(RECIPE_HISTORY_ITEM_ICON_SIZE, 32);
  assert.equal(isPixelGridAlignedItemIconSize(RECIPE_HISTORY_ITEM_ICON_SIZE), true);
});

test('keeps the starting-item quick-control icon aligned to the logical pixel grid', () => {
  assert.equal(ROOT_QUICK_ACTION_ITEM_ICON_SIZE, 32);
  assert.equal(isPixelGridAlignedItemIconSize(ROOT_QUICK_ACTION_ITEM_ICON_SIZE), true);
});

test('keeps the enlarged radial root icon aligned to the logical pixel grid', () => {
  assert.equal(RADIAL_ROOT_ITEM_ICON_SIZE, 48);
  assert.equal(isPixelGridAlignedItemIconSize(RADIAL_ROOT_ITEM_ICON_SIZE), true);
});
