import assert from 'node:assert/strict';
import test from 'node:test';
import {isRecursiveItemNode} from './model.ts';

test('blocks an item that already exists in its own ancestor path', () => {
  assert.equal(
    isRecursiveItemNode({
      id: 'root.s.0.s.0',
      key: 'fluid|cloudy_oil',
      ancestors: ['item|root', 'fluid|cloudy_oil'],
    }),
    true,
  );
});

test('honors an explicitly detected cycle boundary', () => {
  assert.equal(
    isRecursiveItemNode({
      id: 'root.s.0',
      key: 'item|shared',
      ancestors: ['item|root'],
      cyclic: true,
    }),
    true,
  );
});

test('allows the same item in an unrelated branch', () => {
  assert.equal(
    isRecursiveItemNode({
      id: 'root.s.1',
      key: 'item|shared',
      ancestors: ['item|root', 'item|other'],
    }),
    false,
  );
});
