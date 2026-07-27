import assert from 'node:assert/strict';
import test from 'node:test';
import {
  RADIAL_ITEM_SIZE,
  layoutRadialTree,
  planStaggeredRadialRows,
} from './radialLayout.ts';

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

test('stagger planner uses multiple concentric rows for high-cardinality levels', () => {
  const itemCount = 112;
  const angles = Array.from({length: itemCount}, (_, index) =>
    Math.PI * 2 * ((index + 0.5) / itemCount),
  );
  const diameters = Array.from(
    {length: itemCount},
    () => Math.hypot(RADIAL_ITEM_SIZE, RADIAL_ITEM_SIZE),
  );
  const first = planStaggeredRadialRows(angles, diameters, 180);
  const second = planStaggeredRadialRows(angles, diameters, 180);

  assert.deepEqual(second, first);
  assert.ok(first.rowCount > 1);
  assert.equal(new Set(first.radiusByIndex).size, first.rowCount);
  assert.equal(first.radiusByIndex.length, itemCount);
});

test('places a large initial ingredient set around the centered recipe without overlaps', () => {
  const graph = layoutRadialTree(highFanout(112));
  const root = graph.nodes.find(node => node.item.id === 'root');
  const ingredients = graph.nodes.filter(node => node.depth === 1);

  assert.ok(root);
  assert.equal(root.x + root.w / 2, 0);
  assert.equal(root.y + root.h / 2, 0);
  assert.equal(ingredients.length, 112);
  assert.equal(new Set(ingredients.map(node => node.id)).size, 112);
  assert.ok(new Set(ingredients.map(node => Math.round(Math.hypot(
    node.x + node.w / 2,
    node.y + node.h / 2,
  )))).size > 1);
  assert.ok(ingredients.some(node => node.x < 0));
  assert.ok(ingredients.some(node => node.x > 0));
  assert.ok(ingredients.some(node => node.y < 0));
  assert.ok(ingredients.some(node => node.y > 0));

  for (let leftIndex = 0; leftIndex < graph.nodes.length; leftIndex += 1) {
    for (let rightIndex = leftIndex + 1; rightIndex < graph.nodes.length; rightIndex += 1) {
      assert.equal(
        rectanglesOverlap(graph.nodes[leftIndex], graph.nodes[rightIndex]),
        false,
        `${graph.nodes[leftIndex].id} overlaps ${graph.nodes[rightIndex].id}`,
      );
    }
  }
});

test('preserves hierarchy while dependency generations move outward', () => {
  const root = sourceNode('root', [
    sourceNode('left', [item('left-a'), item('left-b')]),
    sourceNode('middle', [item('middle-a'), item('middle-b'), item('middle-c')]),
    sourceNode('right', [sourceNode('right-deep', [item('right-leaf')])]),
  ]);
  const graph = layoutRadialTree(root);
  const radiiByDepth = new Map();
  graph.nodes.forEach(node => {
    const radius = Math.hypot(node.x + node.w / 2, node.y + node.h / 2);
    const radii = radiiByDepth.get(node.depth) ?? [];
    radii.push(radius);
    radiiByDepth.set(node.depth, radii);
  });

  for (let depth = 1; depth < radiiByDepth.size; depth += 1) {
    assert.ok(
      Math.min(...radiiByDepth.get(depth)) > Math.max(...radiiByDepth.get(depth - 1)),
    );
  }
  assert.equal(graph.edges.length, graph.nodes.length - 1);
  assert.ok(
    graph.edges.every(edge =>
      [edge.x, edge.y, edge.w, edge.h, edge.angle].every(Number.isFinite),
    ),
  );
});

test('lays out a 10,000-node radial chain without recursion or non-finite geometry', () => {
  const graph = layoutRadialTree(deepChain(10_000), true);
  assert.equal(graph.nodes.length, 10_000);
  assert.equal(graph.edges.length, 9_999);
  assert.ok(
    graph.nodes.every(node =>
      [node.x, node.y, node.w, node.h].every(Number.isFinite),
    ),
  );
});

test('produces stable radial geometry across repeated layouts', () => {
  const root = sourceNode('root', [
    sourceNode('left', [item('left-a'), item('left-b')]),
    ...Array.from({length: 30}, (_, index) => item(`middle-${index}`)),
    sourceNode('right', [item('right-a'), item('right-b'), item('right-c')]),
  ]);
  assert.deepEqual(layoutRadialTree(root), layoutRadialTree(root));
});
