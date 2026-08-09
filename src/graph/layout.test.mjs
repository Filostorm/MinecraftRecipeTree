import assert from 'node:assert/strict';
import test from 'node:test';
import {
  COMPACT_LABEL_HEIGHT,
  COMPACT_ROOT_DIAMOND_SIZE,
  COMPACT_ROOT_SIZE,
  ROOT_SOURCE_ACTIONS_HEIGHT,
  ROOT_SOURCE_ACTIONS_WIDTH,
  ROOT_ATTACHED_ACTIONS_HEIGHT,
  ROOT_ATTACHED_ACTIONS_WIDTH,
  attachedRootVisualX,
  layoutTree,
  shouldShowCompactQuantity,
} from './layout.ts';

test('hides compact quantities wider than half of the item icon', () => {
  assert.equal(shouldShowCompactQuantity('×1'), true);
  assert.equal(shouldShowCompactQuantity('×12'), true);
  assert.equal(shouldShowCompactQuantity('×123'), false);
  assert.equal(shouldShowCompactQuantity('1 mB'), false);
  assert.equal(shouldShowCompactQuantity('×123', 48), true);
  assert.throws(
    () => shouldShowCompactQuantity('×1', 0),
    /positive finite number/,
  );
});

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
  assert.equal(graph.maxX - graph.minX, COMPACT_ROOT_SIZE);
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

test('reserves node space for starting-item controls instead of overlaying the tree', () => {
  const root = {
    id: 'root',
    key: 'item|test:root',
    ancestors: [],
    source: {
      id: 'root.source',
      kind: 'recipe',
      recipe: {w: 160, h: 60, out: [[['item|test:root', 1]]]},
      inputs: [{id: 'input', key: 'item|test:input', ancestors: []}],
    },
  };

  const closed = layoutTree(root, false, true, false);
  const open = layoutTree(root, false, true, true);
  assert.equal(open.nodes[0].w, closed.nodes[0].w + ROOT_SOURCE_ACTIONS_WIDTH);
  assert.equal(open.nodes[0].h, closed.nodes[0].h + ROOT_SOURCE_ACTIONS_HEIGHT);
  assert.ok(open.nodes[1].y >= closed.nodes[1].y + ROOT_SOURCE_ACTIONS_HEIGHT);
});

test('reserves standalone control space around an open compact starting item', () => {
  const root = {
    id: 'root',
    key: 'item|test:root',
    ancestors: [],
    source: {
      id: 'root.source',
      kind: 'recipe',
      inputs: [{id: 'input', key: 'item|test:input', ancestors: []}],
    },
  };

  const closed = layoutTree(root, true, true, false);
  const open = layoutTree(root, true, true, true);
  assert.equal(open.nodes[0].w, ROOT_ATTACHED_ACTIONS_WIDTH);
  assert.equal(
    open.nodes[0].h,
    COMPACT_ROOT_SIZE + ROOT_ATTACHED_ACTIONS_HEIGHT,
  );
  assert.ok(open.nodes[1].y > closed.nodes[1].y);
});

test('keeps radial starting item anchored when its attached controls open', () => {
  const radialX = -51;
  assert.equal(
    attachedRootVisualX(radialX, 102, true, true),
    radialX,
  );
  assert.equal(
    attachedRootVisualX(0, ROOT_ATTACHED_ACTIONS_WIDTH, false, true),
    (ROOT_ATTACHED_ACTIONS_WIDTH - COMPACT_ROOT_SIZE) / 2,
  );
});

test('reserves horizontal and export space for persistent compact item names', () => {
  const root = {
    id: 'root',
    key: 'item|test:root',
    ancestors: [],
    source: {
      id: 'root.source',
      kind: 'recipe',
      inputs: [
        {id: 'left', key: 'item|test:left', ancestors: []},
        {id: 'right', key: 'item|test:right', ancestors: []},
      ],
    },
  };
  const graph = layoutTree(root, true, true);
  const [left, right] = graph.nodes.slice(1);
  assert.ok(right.x + right.w / 2 - (left.x + left.w / 2) >= 96);
  assert.ok(graph.maxY >= right.y + right.h + COMPACT_LABEL_HEIGHT);
});

test('reserves a larger collision-safe footprint for the compact starting item', () => {
  const root = {
    id: 'root',
    key: 'fluid|test:root',
    ancestors: [],
  };

  const graph = layoutTree(root, true);
  assert.equal(COMPACT_ROOT_SIZE, Math.ceil(COMPACT_ROOT_DIAMOND_SIZE * Math.SQRT2));
  assert.equal(graph.nodes[0].w, COMPACT_ROOT_SIZE);
  assert.equal(graph.nodes[0].h, COMPACT_ROOT_SIZE);
  assert.equal(graph.maxX - graph.minX, COMPACT_ROOT_SIZE);
  assert.equal(graph.maxY - graph.minY, COMPACT_ROOT_SIZE);
});

test('keeps immediate compact inputs close when one sibling owns a wide descendant fan', () => {
  const leaf = id => ({id, key: `item|test:${id}`, ancestors: []});
  const wideBranch = {
    id: 'wide-branch',
    key: 'item|test:wide-branch',
    ancestors: [],
    source: {
      id: 'wide-branch.source',
      kind: 'recipe',
      recipe: {out: [[['item|test:wide-branch', 1]]]},
      inputs: Array.from({length: 10}, (_, index) => leaf(`wide-leaf-${index}`)),
    },
  };
  const root = {
    id: 'root',
    key: 'item|test:root',
    ancestors: [],
    source: {
      id: 'root.source',
      kind: 'recipe',
      recipe: {out: [[['item|test:root', 1]]]},
      inputs: [leaf('left'), wideBranch, leaf('right')],
    },
  };

  const graph = layoutTree(root, true);
  const immediateCenters = ['left', 'wide-branch', 'right'].map(id => {
    const node = graph.nodes.find(candidate => candidate.item.id === id);
    assert.ok(node, `Missing laid node ${id}`);
    return node.x + node.w / 2;
  });

  assert.deepEqual(
    immediateCenters.slice().sort((a, b) => a - b),
    immediateCenters,
  );
  assert.ok(immediateCenters[1] - immediateCenters[0] <= 70);
  assert.ok(immediateCenters[2] - immediateCenters[1] <= 70);

  const rows = new Map();
  for (const node of graph.nodes) {
    const row = rows.get(node.y) ?? [];
    row.push(node);
    rows.set(node.y, row);
  }
  for (const row of rows.values()) {
    const ordered = row.slice().sort((a, b) => a.x - b.x);
    for (let index = 1; index < ordered.length; index += 1) {
      assert.ok(
        ordered[index - 1].x + ordered[index - 1].w <= ordered[index].x,
        `${ordered[index - 1].id} overlaps ${ordered[index].id}`,
      );
    }
  }
});
