import assert from 'node:assert/strict';
import test from 'node:test';
import {
  DEFAULT_INTERFACE_ZOOM,
  INTERFACE_ZOOM_LEVELS,
  adjacentInterfaceZoom,
  uniformPickerRecipePreviewSize,
} from './interfaceZoom.ts';

test('interface zoom advances through bounded layout-safe levels', () => {
  assert.equal(adjacentInterfaceZoom(DEFAULT_INTERFACE_ZOOM, 1), 1.15);
  assert.equal(adjacentInterfaceZoom(1.15, 1), 1.3);
  assert.equal(adjacentInterfaceZoom(1.3, -1), 1.15);
  assert.equal(adjacentInterfaceZoom(INTERFACE_ZOOM_LEVELS[0], -1), 0.75);
  assert.equal(
    adjacentInterfaceZoom(INTERFACE_ZOOM_LEVELS.at(-1), 1),
    1.5,
  );
});

test('interface zoom rejects invalid current values instead of silently approximating them', () => {
  assert.throws(() => adjacentInterfaceZoom(1.09, 1), /not a supported zoom level/);
  assert.throws(() => adjacentInterfaceZoom(Number.NaN, 1), /finite number/);
});

test('picker applies one uniform recipe scale before proportional fit constraints', () => {
  assert.deepEqual(uniformPickerRecipePreviewSize(160, 60, 1), {
    width: 160,
    height: 60,
  });
  assert.deepEqual(uniformPickerRecipePreviewSize(80, 40, 1.5), {
    width: 120,
    height: 60,
  });
  assert.deepEqual(uniformPickerRecipePreviewSize(160, 60, 1.5), {
    width: 240,
    height: 90,
  });
  assert.deepEqual(uniformPickerRecipePreviewSize(300, 100, 1.5), {
    width: 375,
    height: 125,
  });
  assert.throws(
    () => uniformPickerRecipePreviewSize(160, 60, 1.09),
    /not a supported zoom level/,
  );
  assert.throws(
    () => uniformPickerRecipePreviewSize(0, 60, 1.5),
    /positive finite numbers/,
  );
});
