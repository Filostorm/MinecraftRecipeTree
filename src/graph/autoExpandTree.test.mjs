import assert from 'node:assert/strict';
import test from 'node:test';
import {autoExpandPreferredNodes} from './autoExpandTree.ts';

function item(id, key, options = {}) {
  return {id, key, ancestors: [], ...options};
}

test('auto expand reports completed recipes and follows their newly revealed inputs', async () => {
  const existingLeaf = item('root.s.0', 'item|test:existing-leaf');
  const root = item('root', 'item|test:root', {
    source: {id: 'root.s', kind: 'recipe', inputs: [existingLeaf]},
  });
  const choices = new Map([
    ['item|test:existing-leaf', 'first'],
    ['item|test:new-leaf', 'second'],
  ]);

  const expanded = await autoExpandPreferredNodes(
    root,
    node => choices.get(node.key) ?? null,
    async (node, choice) => {
      node.source = {
        id: `${node.id}.s`,
        kind: 'recipe',
        catTitle: choice,
        inputs:
          choice === 'first'
            ? [item(`${node.id}.s.0`, 'item|test:new-leaf')]
            : [],
      };
    },
  );

  assert.deepEqual(
    expanded.map(node => [node.key, node.source?.catTitle]),
    [
      ['item|test:existing-leaf', 'first'],
      ['item|test:new-leaf', 'second'],
    ],
  );
});

test('auto expand skips cyclic, loading, deferred, and unpreferred nodes', async () => {
  const children = [
    item('root.s.0', 'item|test:cyclic', {cyclic: true}),
    item('root.s.1', 'item|test:loading', {loading: true}),
    item('root.s.2', 'item|test:deferred', {
      deferredRecipeExpansion: {ref: [1, 2]},
    }),
    item('root.s.3', 'item|test:no-preference'),
  ];
  const root = item('root', 'item|test:root', {
    source: {id: 'root.s', kind: 'recipe', inputs: children},
  });
  const attempted = [];

  const expanded = await autoExpandPreferredNodes(
    root,
    node => (node.key === 'item|test:no-preference' ? null : 'recipe'),
    async node => {
      attempted.push(node.key);
    },
  );

  assert.deepEqual(expanded, []);
  assert.deepEqual(attempted, []);
});

test('auto expand does not report recipes that failed to attach a source', async () => {
  const root = item('root', 'item|test:root');
  const expanded = await autoExpandPreferredNodes(
    root,
    () => 'unavailable-recipe',
    async () => {},
  );

  assert.deepEqual(expanded, []);
});
