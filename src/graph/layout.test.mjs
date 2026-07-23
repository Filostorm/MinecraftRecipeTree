import assert from 'node:assert/strict';
import test from 'node:test';
import {layoutTree} from './layout.ts';

function deepChain(nodeCount) {
  let root = {
    id: `node-${nodeCount - 1}`,
    key: `item|test:node-${nodeCount - 1}`,
    amount: 1,
    ancestors: [],
  };
  for (let index = nodeCount - 2; index >= 0; index -= 1) {
    const key = `item|test:node-${index}`;
    root = {
      id: `node-${index}`,
      key,
      amount: 1,
      ancestors: [],
      source: {
        id: `node-${index}.source`,
        kind: 'recipe',
        recipe: {out: [[[key, 1]]]},
        inputs: [root],
      },
    };
  }
  return root;
}

test('lays out a 10,000-node dependency chain without recursive call-stack growth', () => {
  const graph = layoutTree(deepChain(10_000), true);
  assert.equal(graph.nodes.length, 10_000);
  assert.equal(graph.edges.length, 29_997);
  assert.equal(graph.maxX - graph.minX, 52);
  assert.ok(graph.maxY > 900_000);
});

test('preserves left-to-right preorder placement and child-to-parent edge ordering', () => {
  const left = {id: 'left', key: 'item|test:left', ancestors: []};
  const right = {id: 'right', key: 'item|test:right', ancestors: []};
  const root = {
    id: 'root',
    key: 'item|test:root',
    ancestors: [],
    source: {
      id: 'root.source',
      kind: 'recipe',
      recipe: {out: [[['item|test:root', 1]]]},
      inputs: [left, right],
    },
  };

  const graph = layoutTree(root, true);
  assert.deepEqual(
    graph.nodes.map(node => node.item.id),
    ['root', 'left', 'right'],
  );
  assert.equal(graph.edges.length, 6);
  assert.ok(graph.nodes[1].x < graph.nodes[2].x);
});
