import assert from 'node:assert/strict';
import test from 'node:test';
import {pixelArtDisplaySize, pixelArtImageStyle} from './pixelArtSizing.ts';

test('uses the largest fitting integer pixel-art scale', () => {
  assert.deepEqual(pixelArtDisplaySize(124, 62, 360, 280), {w: 248, h: 124});
  assert.deepEqual(pixelArtDisplaySize(184, 117, 360, 280), {w: 184, h: 117});
  assert.deepEqual(pixelArtDisplaySize(16, 16, 48, 48, 3), {w: 48, h: 48});
});

test('fractionally constrains only source art that is already oversized', () => {
  assert.deepEqual(pixelArtDisplaySize(670, 17, 360, 280), {w: 360, h: 9});
  assert.throws(() => pixelArtDisplaySize(0, 16, 48, 48), /positive finite/);
});

test('maps picker preview dimensions to React Native width and height style keys', () => {
  assert.deepEqual(pixelArtImageStyle(124, 62, 280, 128), {width: 248, height: 124});
  assert.deepEqual(Object.keys(pixelArtImageStyle(160, 60, 280, 128)).sort(), [
    'height',
    'width',
  ]);
});
