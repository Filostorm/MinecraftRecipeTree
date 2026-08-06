import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {
  DEFAULT_INTERFACE_ZOOM,
  MAXIMUM_INTERFACE_ZOOM,
  MINIMUM_INTERFACE_ZOOM,
  normalizeInterfaceZoom,
  uniformPickerRecipePreviewSize,
} from './interfaceZoom.ts';

const appSource = await readFile(new URL('../../App.tsx', import.meta.url), 'utf8');

test('interface zoom accepts every bounded five-percent slider stop', () => {
  assert.equal(normalizeInterfaceZoom(DEFAULT_INTERFACE_ZOOM), 1);
  assert.equal(normalizeInterfaceZoom(MINIMUM_INTERFACE_ZOOM), 0.75);
  assert.equal(normalizeInterfaceZoom(1.05), 1.05);
  assert.equal(normalizeInterfaceZoom(1.45), 1.45);
  assert.equal(normalizeInterfaceZoom(MAXIMUM_INTERFACE_ZOOM), 1.5);
});

test('interface zoom rejects out-of-range and off-step values instead of silently approximating', () => {
  assert.throws(() => normalizeInterfaceZoom(0.7), /outside the supported slider range/);
  assert.throws(() => normalizeInterfaceZoom(1.09), /outside the supported slider range/);
  assert.throws(() => normalizeInterfaceZoom(1.55), /outside the supported slider range/);
  assert.throws(() => normalizeInterfaceZoom(Number.NaN), /finite number/);
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
  assert.deepEqual(uniformPickerRecipePreviewSize(80, 40, 1.05), {
    width: 84,
    height: 42,
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
    /outside the supported slider range/,
  );
  assert.throws(
    () => uniformPickerRecipePreviewSize(0, 60, 1.5),
    /positive finite numbers/,
  );
});

test('interface zoom does not apply browser CSS zoom to the graph workspace', () => {
  assert.doesNotMatch(appSource, /scaledWorkspaceStyle/u);
  assert.match(appSource, /const scaledMobsWorkspaceStyle[\s\S]*?zoom:\s*interfaceZoom/u);
  const graphPane = appSource.slice(
    appSource.indexOf('{data.indexStatus ==='),
    appSource.indexOf('{data.capabilities.mobs'),
  );
  assert.doesNotMatch(graphPane, /scaledMobsWorkspaceStyle|zoom:\s*interfaceZoom/u);
});
