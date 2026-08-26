import assert from 'node:assert/strict';
import test from 'node:test';
import {nodeContextMenuPlacement} from './nodeContextMenu.ts';

test('opens beside a pointer when there is room', () => {
  assert.deepEqual(
    nodeContextMenuPlacement({x: 120, y: 80}, {width: 900, height: 700}),
    {left: 128, top: 88, width: 300, maxHeight: 604},
  );
});

test('flips away from the right and bottom viewport edges', () => {
  const placement = nodeContextMenuPlacement(
    {x: 880, y: 680},
    {width: 900, height: 700},
  );
  assert.equal(placement.left, 572);
  assert.equal(placement.top, 252);
  assert.equal(placement.width, 300);
  assert.equal(placement.maxHeight, 440);
});

test('accounts for interface zoom and narrow viewports', () => {
  const placement = nodeContextMenuPlacement(
    {x: 360, y: 120},
    {width: 390, height: 700},
    1.5,
  );
  assert.ok(placement.width <= (390 - 16) / 1.5);
  assert.ok(placement.left >= 8);
  assert.ok(placement.left + placement.width * 1.5 <= 382);
});

test('uses the full viewport height when neither side can fit a zoomed menu', () => {
  const placement = nodeContextMenuPlacement(
    {x: 640, y: 270},
    {width: 1280, height: 560},
    1.5,
  );
  assert.equal(placement.top, 8);
  assert.ok(placement.maxHeight >= 360);
});
