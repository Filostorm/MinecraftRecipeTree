import assert from 'node:assert/strict';
import test from 'node:test';
import {
  LOW_DETAIL_RASTER_ICON_SIZE,
  lowDetailRasterGeometry,
} from './lowDetailRaster.ts';

test('projects far-zoom nodes and icons directly into viewport pixels', () => {
  assert.deepEqual(
    lowDetailRasterGeometry(
      {x: 1_000, y: 500, w: 200, h: 100},
      {x: -25, y: 10, scale: 0.1},
    ),
    {
      left: 75,
      top: 60,
      width: 20,
      height: 10,
      iconLeft: 83.4,
      iconTop: 63.4,
      iconSize: LOW_DETAIL_RASTER_ICON_SIZE * 0.1,
    },
  );
});

test('keeps far-zoom geometry visible below one screen pixel', () => {
  const geometry = lowDetailRasterGeometry(
    {x: 0, y: 0, w: 16, h: 16},
    {x: 0, y: 0, scale: 0.001},
  );
  assert.equal(geometry.width, 1);
  assert.equal(geometry.height, 1);
  assert.equal(geometry.iconSize, 1);
});

test('rejects invalid far-zoom raster geometry', () => {
  assert.throws(
    () =>
      lowDetailRasterGeometry(
        {x: 0, y: 0, w: 0, h: 16},
        {x: 0, y: 0, scale: 1},
      ),
    /positive finite dimensions and scale/,
  );
  assert.throws(
    () =>
      lowDetailRasterGeometry(
        {x: 0, y: 0, w: 16, h: 16},
        {x: 0, y: 0, scale: Number.NaN},
      ),
    /positive finite dimensions and scale/,
  );
});
