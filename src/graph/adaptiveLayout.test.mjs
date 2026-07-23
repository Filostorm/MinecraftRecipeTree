import assert from 'node:assert/strict';
import test from 'node:test';
import {
  PACKED_INPUT_THRESHOLD,
  PACKED_ITEM_SIZE,
  layoutAdaptiveTree,
  planPackedInputFan,
} from './adaptiveLayout.ts';
import {layoutTree} from './layout.ts';

function item(id, options = {}) {
  return {
    id,
    key: `item|test:${id}`,
    amount: 1,
    ancestors: [],
    ...options,
  };
}

function sourceNode(id, inputs) {
  const key = `item|test:${id}`;
  return item(id, {
    source: {
      id: `${id}.source`,
      kind: 'recipe',
      recipe: {out: [[[key, 1]]]},
      inputs,
    },
  });
}

function highFanout(inputCount) {
  return sourceNode(
    'root',
    Array.from({length: inputCount}, (_, index) => item(`input-${index}`)),
  );
}

function deepChain(nodeCount) {
  let root = item(`node-${nodeCount - 1}`);
  for (let index = nodeCount - 2; index >= 0; index -= 1) {
    root = sourceNode(`node-${index}`, [root]);
  }
  return root;
}

function rectanglesOverlap(left, right) {
  return !(
    left.x + left.w <= right.x ||
    right.x + right.w <= left.x ||
    left.y + left.h <= right.y ||
    right.y + right.h <= left.y
  );
}

test('plans deterministic concentric fan rings without overlapping packed icons', () => {
  const first = planPackedInputFan(200);
  const second = planPackedInputFan(200);
  assert.deepEqual(second, first);
  assert.equal(first.members.length, 200);
  assert.ok(first.ringRadii.length > 1);

  for (let leftIndex = 0; leftIndex < first.members.length; leftIndex += 1) {
    const left = {
      x: first.members[leftIndex].x - 4,
      y: first.members[leftIndex].y - 4,
      w: PACKED_ITEM_SIZE + 8,
      h: PACKED_ITEM_SIZE + 8,
    };
    for (let rightIndex = leftIndex + 1; rightIndex < first.members.length; rightIndex += 1) {
      const right = {
        x: first.members[rightIndex].x - 4,
        y: first.members[rightIndex].y - 4,
        w: PACKED_ITEM_SIZE + 8,
        h: PACKED_ITEM_SIZE + 8,
      };
      assert.equal(
        rectanglesOverlap(left, right),
        false,
        `packed members ${leftIndex} and ${rightIndex} overlap`,
      );
    }
  }
});

test('packs at the threshold while preserving the classic layout below it', () => {
  const below = layoutAdaptiveTree(highFanout(PACKED_INPUT_THRESHOLD - 1));
  const atThreshold = layoutAdaptiveTree(highFanout(PACKED_INPUT_THRESHOLD));
  assert.equal(below.clusters.length, 0);
  assert.equal(atThreshold.clusters.length, 1);
  assert.equal(atThreshold.clusters[0].itemCount, PACKED_INPUT_THRESHOLD);
});

test('radial fan substantially reduces high-arity recipe width and edge elements', () => {
  const root = highFanout(64);
  const classic = layoutTree(root);
  const adaptive = layoutAdaptiveTree(root);
  const classicWidth = classic.maxX - classic.minX;
  const adaptiveWidth = adaptive.maxX - adaptive.minX;

  assert.equal(adaptive.nodes.length, classic.nodes.length);
  assert.equal(new Set(adaptive.nodes.map(node => node.id)).size, adaptive.nodes.length);
  assert.equal(adaptive.clusters.length, 1);
  assert.equal(adaptive.nodes.filter(node => node.packed).length, 64);
  assert.ok(adaptiveWidth < classicWidth * 0.2, `${adaptiveWidth} is not <20% of ${classicWidth}`);
  assert.ok(adaptive.edges.length < classic.edges.length * 0.1);
});

test('keeps the compact 112-input fan inside the area budget while removing most width', () => {
  const root = highFanout(112);
  const classic = layoutTree(root, true);
  const adaptive = layoutAdaptiveTree(root, true);
  const classicWidth = classic.maxX - classic.minX;
  const classicHeight = classic.maxY - classic.minY;
  const adaptiveWidth = adaptive.maxX - adaptive.minX;
  const adaptiveHeight = adaptive.maxY - adaptive.minY;

  assert.ok(adaptiveWidth < classicWidth * 0.2);
  assert.ok(adaptiveWidth * adaptiveHeight < classicWidth * classicHeight * 1.25);
});

test('contour compaction interlocks ragged subtrees without changing their hierarchy', () => {
  const leaves = prefix =>
    Array.from({length: PACKED_INPUT_THRESHOLD - 1}, (_, index) => item(`${prefix}-${index}`));
  const root = sourceNode('root', [
    sourceNode('left', [sourceNode('left-deep', leaves('left-leaf'))]),
    sourceNode('right', leaves('right-leaf')),
  ]);
  const classic = layoutTree(root);
  const adaptive = layoutAdaptiveTree(root);

  assert.equal(adaptive.clusters.length, 0);
  assert.equal(adaptive.nodes.length, classic.nodes.length);
  assert.ok(
    adaptive.maxX - adaptive.minX < (classic.maxX - classic.minX) * 0.85,
  );
});

test('keeps variable-width nodes separated on every compacted rank', () => {
  const variableSource = (id, width, inputs) => {
    const node = sourceNode(id, inputs);
    node.source.recipe.w = width;
    node.source.recipe.h = 80;
    return node;
  };
  const root = variableSource('root', 280, [
    variableSource('left', 96, [
      item('left-a'),
      variableSource('left-b', 240, [item('left-b-a'), item('left-b-b')]),
    ]),
    variableSource('middle', 320, [
      item('middle-a'),
      item('middle-b'),
      item('middle-c'),
      item('middle-d'),
    ]),
    variableSource('right', 128, [
      variableSource('right-a', 280, [item('right-a-a')]),
      item('right-b'),
    ]),
  ]);
  const graph = layoutAdaptiveTree(root);
  const rows = new Map();
  for (const node of graph.nodes) {
    const row = rows.get(node.y) ?? [];
    row.push(node);
    rows.set(node.y, row);
  }
  for (const row of rows.values()) {
    for (let leftIndex = 0; leftIndex < row.length; leftIndex += 1) {
      for (let rightIndex = leftIndex + 1; rightIndex < row.length; rightIndex += 1) {
        assert.equal(
          rectanglesOverlap(row[leftIndex], row[rightIndex]),
          false,
          `${row[leftIndex].id} overlaps ${row[rightIndex].id}`,
        );
      }
    }
  }
});

test('promotes an expanded fan member into an external branch without changing its identity', () => {
  const collapsed = Array.from({length: 30}, (_, index) => item(`collapsed-${index}`));
  const promoted = sourceNode('promoted', [item('promoted-leaf')]);
  const root = sourceNode('root', [...collapsed.slice(0, 15), promoted, ...collapsed.slice(15)]);
  const graph = layoutAdaptiveTree(root);

  assert.equal(graph.clusters.length, 1);
  assert.equal(graph.clusters[0].itemCount, 30);
  const promotedLayout = graph.nodes.find(node => node.item === promoted);
  assert.ok(promotedLayout);
  assert.equal(promotedLayout.packed, undefined);
  assert.equal(promotedLayout.kind, 'source');

  const cluster = graph.clusters[0];
  assert.equal(rectanglesOverlap(promotedLayout, cluster), false);
  const promotedLeaf = graph.nodes.find(node => node.item.id === 'promoted-leaf');
  assert.ok(promotedLeaf.y >= cluster.y + cluster.h);
});

test('lays out a 10,000-node adaptive chain without recursion or non-finite geometry', () => {
  const graph = layoutAdaptiveTree(deepChain(10_000), true);
  assert.equal(graph.nodes.length, 10_000);
  assert.equal(graph.edges.length, 29_997);
  assert.equal(graph.clusters.length, 0);
  assert.equal(graph.maxX - graph.minX, 52);
  assert.ok(graph.maxY > 900_000);
  assert.ok(
    graph.nodes.every(node =>
      [node.x, node.y, node.w, node.h].every(Number.isFinite),
    ),
  );
});

test('produces stable adaptive geometry across repeated layouts', () => {
  const root = sourceNode('root', [
    sourceNode('left', [item('left-a'), item('left-b')]),
    ...Array.from({length: 20}, (_, index) => item(`middle-${index}`)),
    sourceNode('right', [item('right-a'), item('right-b'), item('right-c')]),
  ]);
  const first = layoutAdaptiveTree(root);
  const second = layoutAdaptiveTree(root);
  assert.deepEqual(second, first);
});
