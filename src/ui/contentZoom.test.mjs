import assert from 'node:assert/strict';
import test from 'node:test';
import {
  DEFAULT_CONTENT_ZOOM,
  MAXIMUM_CONTENT_ZOOM,
  MINIMUM_CONTENT_ZOOM,
  normalizeContentZoom,
} from './contentZoom.ts';

test('recipe and item zoom supports twice the former maximum size', () => {
  assert.equal(normalizeContentZoom(DEFAULT_CONTENT_ZOOM), 1);
  assert.equal(normalizeContentZoom(MINIMUM_CONTENT_ZOOM), 0.75);
  assert.equal(normalizeContentZoom(1.5), 1.5);
  assert.equal(normalizeContentZoom(2.25), 2.25);
  assert.equal(normalizeContentZoom(MAXIMUM_CONTENT_ZOOM), 3);
});

test('recipe and item zoom rejects invalid values', () => {
  assert.throws(() => normalizeContentZoom(0.7), /outside the supported slider range/);
  assert.throws(() => normalizeContentZoom(3.05), /outside the supported slider range/);
  assert.throws(() => normalizeContentZoom(1.09), /outside the supported slider range/);
  assert.throws(() => normalizeContentZoom(Number.NaN), /finite number/);
});
