import assert from 'node:assert/strict';
import test from 'node:test';
import {preferredSourceTargets} from './preferencePropagation.ts';

function item(id, key, options = {}) {
  return {
    id,
    key,
    ancestors: [],
    ...options,
  };
}

test('collects both collapsed and expanded instances of the preferred item', () => {
  const collapsed = item('root.s.0', 'item|test:shared');
  const expanded = item('root.s.1', 'item|test:shared', {
    source: {
      id: 'root.s.1.s',
      kind: 'recipe',
      inputs: [item('root.s.1.s.0', 'item|test:ingredient')],
    },
  });
  const root = item('root', 'item|test:result', {
    source: {
      id: 'root.s',
      kind: 'recipe',
      inputs: [collapsed, expanded],
    },
  });

  assert.deepEqual(
    preferredSourceTargets(root, collapsed).map(node => node.id),
    ['root.s.0', 'root.s.1'],
  );
});

test('excludes cyclic and currently loading instances from preference propagation', () => {
  const selected = item('root.s.0', 'item|test:shared');
  const cyclic = item('root.s.1', 'item|test:shared', {cyclic: true});
  const loading = item('root.s.2', 'item|test:shared', {loading: true});
  const root = item('root', 'item|test:result', {
    source: {
      id: 'root.s',
      kind: 'recipe',
      inputs: [selected, cyclic, loading],
    },
  });

  assert.deepEqual(
    preferredSourceTargets(root, selected).map(node => node.id),
    ['root.s.0'],
  );
});
