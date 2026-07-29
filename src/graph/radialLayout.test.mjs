import assert from 'node:assert/strict';
import test from 'node:test';
import {
  RADIAL_ITEM_SIZE,
  RADIAL_ROOT_DIAMOND_SIZE,
  RADIAL_ROOT_SIZE,
  layoutRadialTree,
  planStaggeredRadialRows,
} from './radialLayout.ts';
import {
  COMPACT_LABEL_HEIGHT,
  COMPACT_LABEL_WIDTH,
} from './layout.ts';

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

function nodeCenter(node) {
  return {x: node.x + node.w / 2, y: node.y + node.h / 2};
}

function distanceBetween(graph, leftId, rightId) {
  const left = graph.nodes.find(node => node.id === leftId);
  const right = graph.nodes.find(node => node.id === rightId);
  assert.ok(left, `missing radial node ${leftId}`);
  assert.ok(right, `missing radial node ${rightId}`);
  const leftCenter = nodeCenter(left);
  const rightCenter = nodeCenter(right);
  return Math.hypot(leftCenter.x - rightCenter.x, leftCenter.y - rightCenter.y);
}

function angleBetweenChildren(graph, parentId, leftId, rightId) {
  const parent = graph.nodes.find(node => node.id === parentId);
  const left = graph.nodes.find(node => node.id === leftId);
  const right = graph.nodes.find(node => node.id === rightId);
  assert.ok(parent && left && right);
  const parentCenter = nodeCenter(parent);
  const leftCenter = nodeCenter(left);
  const rightCenter = nodeCenter(right);
  const leftAngle = Math.atan2(
    leftCenter.y - parentCenter.y,
    leftCenter.x - parentCenter.x,
  );
  const rightAngle = Math.atan2(
    rightCenter.y - parentCenter.y,
    rightCenter.x - parentCenter.x,
  );
  const delta = Math.abs(leftAngle - rightAngle) % (Math.PI * 2);
  return Math.min(delta, Math.PI * 2 - delta);
}

function assertNoNodeOverlaps(graph) {
  for (let leftIndex = 0; leftIndex < graph.nodes.length; leftIndex += 1) {
    for (let rightIndex = leftIndex + 1; rightIndex < graph.nodes.length; rightIndex += 1) {
      assert.equal(
        rectanglesOverlap(graph.nodes[leftIndex], graph.nodes[rightIndex]),
        false,
        `${graph.nodes[leftIndex].id} overlaps ${graph.nodes[rightIndex].id}`,
      );
    }
  }
}

function assertNoCompactLabelOverlaps(graph) {
  const collisionRects = graph.nodes.map(node => {
    const width = Math.max(node.w, COMPACT_LABEL_WIDTH);
    return {
      id: node.id,
      x: node.x + node.w / 2 - width / 2,
      y: node.y,
      w: width,
      h: node.h + COMPACT_LABEL_HEIGHT,
    };
  });
  for (let leftIndex = 0; leftIndex < collisionRects.length; leftIndex += 1) {
    for (let rightIndex = leftIndex + 1; rightIndex < collisionRects.length; rightIndex += 1) {
      assert.equal(
        rectanglesOverlap(collisionRects[leftIndex], collisionRects[rightIndex]),
        false,
        `${collisionRects[leftIndex].id} label overlaps ${collisionRects[rightIndex].id}`,
      );
    }
  }
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

  assertNoNodeOverlaps(graph);
});

test('gives the compact radial root a larger collision-safe footprint than its ingredients', () => {
  const graph = layoutRadialTree(highFanout(12), true);
  const root = graph.nodes.find(node => node.item.id === 'root');
  const ingredients = graph.nodes.filter(node => node.depth === 1);

  assert.ok(root);
  assert.equal(RADIAL_ROOT_SIZE, Math.ceil(RADIAL_ROOT_DIAMOND_SIZE * Math.SQRT2));
  assert.equal(root.w, RADIAL_ROOT_SIZE);
  assert.equal(root.h, RADIAL_ROOT_SIZE);
  assert.ok(ingredients.every(node => node.w === RADIAL_ITEM_SIZE));
  assert.ok(ingredients.every(node => node.h === RADIAL_ITEM_SIZE));
  assert.ok(
    ingredients.every(node =>
      Math.hypot(node.x + node.w / 2, node.y + node.h / 2) >
        Math.hypot(root.w, root.h) / 2,
    ),
  );
});

test('includes persistent radial item-name labels in graph bounds', () => {
  const withoutLabels = layoutRadialTree(highFanout(12), false, () => false, false);
  const withLabels = layoutRadialTree(highFanout(12), false, () => false, true);
  assert.ok(withLabels.maxY > withoutLabels.maxY);
  assert.ok(withLabels.maxX - withLabels.minX >= withoutLabels.maxX - withoutLabels.minX);
});

test('preserves hierarchy while each dependency moves outward from its parent', () => {
  const root = sourceNode('root', [
    sourceNode('left', [item('left-a'), item('left-b')]),
    sourceNode('middle', [item('middle-a'), item('middle-b'), item('middle-c')]),
    sourceNode('right', [sourceNode('right-deep', [item('right-leaf')])]),
  ]);
  const graph = layoutRadialTree(root);
  const radiusById = new Map(
    graph.nodes.map(node => {
      const center = nodeCenter(node);
      return [node.id, Math.hypot(center.x, center.y)];
    }),
  );
  const dependencyEdges = [
    ['root.source', 'left.source'],
    ['root.source', 'middle.source'],
    ['root.source', 'right.source'],
    ['left.source', 'left-a'],
    ['left.source', 'left-b'],
    ['middle.source', 'middle-a'],
    ['middle.source', 'middle-b'],
    ['middle.source', 'middle-c'],
    ['right.source', 'right-deep.source'],
    ['right-deep.source', 'right-leaf'],
  ];

  for (const [parentId, childId] of dependencyEdges) {
    assert.ok(radiusById.get(childId) > radiusById.get(parentId));
  }
  assert.equal(graph.edges.length, graph.nodes.length - 1);
  assert.ok(
    graph.edges.every(edge =>
      [edge.x, edge.y, edge.w, edge.h, edge.angle].every(Number.isFinite),
    ),
  );
  assertNoNodeOverlaps(graph);
});

test('keeps a sparse branch near the center when another branch needs a dense annulus', () => {
  const denseInputs = Array.from({length: 112}, (_, index) => item(`dense-${index}`));
  const root = sourceNode('root', [
    sourceNode('dense-branch', denseInputs),
    sourceNode('sparse-branch', [
      sourceNode('sparse-middle', [
        sourceNode('sparse-inner', [item('sparse-leaf')]),
      ]),
    ]),
  ]);
  const graph = layoutRadialTree(root);
  const sparseMiddleRadius = distanceBetween(graph, 'root.source', 'sparse-middle.source');
  const denseRadii = denseInputs.map(input =>
    distanceBetween(graph, 'root.source', input.id),
  );

  assert.ok(sparseMiddleRadius < Math.max(...denseRadii));
  assert.ok(
    distanceBetween(graph, 'sparse-branch.source', 'sparse-middle.source') < 320,
  );
  assert.ok(
    distanceBetween(graph, 'sparse-middle.source', 'sparse-inner.source') < 320,
  );
  assertNoNodeOverlaps(graph);
});

test('keeps a dense recipe input fan within one staggered row gap', () => {
  const branchInputs = Array.from({length: 10}, (_, index) => item(`branch-input-${index}`));
  const root = sourceNode('root', [
    sourceNode('branch', branchInputs),
    sourceNode('side-branch', [item('side-input')]),
    item('root-input'),
  ]);
  const graph = layoutRadialTree(root);
  const distances = branchInputs.map(input =>
    distanceBetween(graph, 'branch.source', input.id),
  );

  assert.ok(Math.max(...distances) - Math.min(...distances) < 100);
  assertNoNodeOverlaps(graph);
});

test('keeps multiple labeled ingredient fans local to their recipes', () => {
  const branchCount = 12;
  const inputsPerBranch = 8;
  const root = sourceNode(
    'root',
    Array.from({length: branchCount}, (_, branchIndex) =>
      sourceNode(
        `branch-${branchIndex}`,
        Array.from({length: inputsPerBranch}, (_, inputIndex) =>
          item(`branch-${branchIndex}-input-${inputIndex}`),
        ),
      ),
    ),
  );
  const graph = layoutRadialTree(root, true, () => true, true);
  const distances = [];
  for (let branchIndex = 0; branchIndex < branchCount; branchIndex += 1) {
    for (let inputIndex = 0; inputIndex < inputsPerBranch; inputIndex += 1) {
      distances.push(
        distanceBetween(
          graph,
          `branch-${branchIndex}.source`,
          `branch-${branchIndex}-input-${inputIndex}`,
        ),
      );
    }
  }

  assert.ok(Math.max(...distances) < 1_000);
  assert.ok(
    distances.reduce((sum, distance) => sum + distance, 0) / distances.length < 600,
  );
  assertNoCompactLabelOverlaps(graph);
});

test('compacts descendant inputs instead of inheriting a large root sector', () => {
  const root = sourceNode('root', [
    sourceNode('branch', [item('branch-left'), item('branch-right')]),
    item('side-input'),
  ]);
  const graph = layoutRadialTree(root);

  assert.ok(
    angleBetweenChildren(
      graph,
      'branch.source',
      'branch-left',
      'branch-right',
    ) < 0.8,
  );
  assertNoNodeOverlaps(graph);
});

test('pulls terminal outputs toward their parent without moving expandable branches', () => {
  const root = sourceNode('root', [
    item('terminal-left'),
    sourceNode('branch', [item('terminal-deep')]),
    item('expandable-collapsed'),
    item('terminal-right'),
  ]);
  const baseline = layoutRadialTree(root);
  const compacted = layoutRadialTree(
    root,
    false,
    node => node.id.startsWith('terminal-'),
  );
  const terminalPairs = [
    ['terminal-left', 'root.source'],
    ['terminal-right', 'root.source'],
    ['terminal-deep', 'branch.source'],
  ];
  const reductions = terminalPairs.map(([terminalId, parentId]) =>
    distanceBetween(baseline, terminalId, parentId) -
    distanceBetween(compacted, terminalId, parentId),
  );

  assert.ok(reductions.every(reduction => reduction >= -0.001));
  assert.ok(reductions.some(reduction => reduction >= 24));
  assert.deepEqual(
    compacted.nodes.find(node => node.id === 'branch.source'),
    baseline.nodes.find(node => node.id === 'branch.source'),
  );
  assert.deepEqual(
    compacted.nodes.find(node => node.id === 'expandable-collapsed'),
    baseline.nodes.find(node => node.id === 'expandable-collapsed'),
  );
  assertNoNodeOverlaps(compacted);
  assert.ok(compacted.edges.every(edge => edge.w > 0));
});

test('keeps a collision-bound dense terminal fan no farther out than the standard fan', () => {
  const root = highFanout(112);
  const baseline = layoutRadialTree(root);
  const first = layoutRadialTree(root, false, () => true);
  const second = layoutRadialTree(root, false, () => true);
  const meanRadius = graph => {
    const outputs = graph.nodes.filter(node => node.depth === 1);
    return outputs.reduce((sum, node) => {
      const center = nodeCenter(node);
      return sum + Math.hypot(center.x, center.y);
    }, 0) / outputs.length;
  };

  assert.ok(meanRadius(first) <= meanRadius(baseline) + 0.001);
  assertNoNodeOverlaps(first);
  assert.deepEqual(second, first);
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
