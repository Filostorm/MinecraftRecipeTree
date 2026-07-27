import assert from 'node:assert/strict';
import test from 'node:test';
import {
  DEFAULT_INTERFACE_ZOOM,
  INTERFACE_ZOOM_LEVELS,
  adjacentInterfaceZoom,
  webModalZoomStyle,
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

test('portal modal zoom scales its contents without exceeding its visual bounds', () => {
  assert.deepEqual(webModalZoomStyle(1, 920, 92), {
    zoom: 1,
    width: '100%',
    maxWidth: 920,
    maxHeight: '92%',
  });
  assert.deepEqual(webModalZoomStyle(1.5, 920, 92), {
    zoom: 1.5,
    width: `${100 / 1.5}%`,
    maxWidth: 920 / 1.5,
    maxHeight: `${92 / 1.5}%`,
  });
  assert.throws(() => webModalZoomStyle(1.09, 920, 92), /not a supported zoom level/);
  assert.throws(() => webModalZoomStyle(1.5, 920, 101), /within 0–100 percent/);
});
