import assert from 'node:assert/strict';
import test from 'node:test';
import {
  DENSE_GRAPH_LOW_DETAIL_SCALE,
  DENSE_GRAPH_NODE_THRESHOLD,
  LOW_DETAIL_RECIPE_HOVER_MAX_MAGNIFICATION,
  LOW_DETAIL_RECIPE_HOVER_TARGET_SCALE,
  NODE_AMOUNT_LABEL_MIN_SCALE,
  lowDetailRecipeHoverNodeId,
  lowDetailRecipeHoverMagnification,
  shouldRequireUniqueRecipes,
  shouldShowNodeAmounts,
  shouldUseLowDetailGraph,
} from './renderDetail.ts';

test('simplifies only dense graphs at unreadably low zoom', () => {
  assert.equal(
    shouldUseLowDetailGraph(DENSE_GRAPH_LOW_DETAIL_SCALE, DENSE_GRAPH_NODE_THRESHOLD),
    true,
  );
  assert.equal(shouldUseLowDetailGraph(0.8, 2_000), false);
  assert.equal(shouldUseLowDetailGraph(0.2, 40), false);
  assert.equal(shouldUseLowDetailGraph(Number.NaN, 2_000), false);
});

test('dense graphs require unique recipes at the shared node threshold', () => {
  assert.equal(shouldRequireUniqueRecipes(DENSE_GRAPH_NODE_THRESHOLD - 1), false);
  assert.equal(shouldRequireUniqueRecipes(DENSE_GRAPH_NODE_THRESHOLD), true);
  assert.equal(shouldRequireUniqueRecipes(DENSE_GRAPH_NODE_THRESHOLD + 1), true);
  assert.equal(shouldRequireUniqueRecipes(Number.NaN), false);
});

test('hides graph amounts at distant zoom while retaining them in exports', () => {
  assert.equal(shouldShowNodeAmounts(NODE_AMOUNT_LABEL_MIN_SCALE), true);
  assert.equal(shouldShowNodeAmounts(NODE_AMOUNT_LABEL_MIN_SCALE - 0.01), false);
  assert.equal(shouldShowNodeAmounts(0.12, true), true);
  assert.equal(shouldShowNodeAmounts(Number.NaN), false);
});

test('magnifies one hovered low-detail recipe to a readable bounded scale', () => {
  assert.equal(
    lowDetailRecipeHoverMagnification(0.4),
    LOW_DETAIL_RECIPE_HOVER_TARGET_SCALE / 0.4,
  );
  assert.equal(
    lowDetailRecipeHoverMagnification(0.01),
    LOW_DETAIL_RECIPE_HOVER_MAX_MAGNIFICATION,
  );
  assert.equal(lowDetailRecipeHoverMagnification(1), 1);
  assert.equal(lowDetailRecipeHoverMagnification(Number.NaN), 1);
});

test('resolves far-zoom recipe hover with a stable screen-space target', () => {
  const nodes = [
    {id: 'item', x: 100, y: 100, w: 100, h: 80},
    {id: 'recipe-a', x: 1_000, y: 1_000, w: 200, h: 120, source: {kind: 'recipe'}},
    {id: 'recipe-b', x: 1_600, y: 1_000, w: 200, h: 120, source: {kind: 'recipe'}},
  ];
  const transform = {x: 0, y: 0, scale: 0.05};

  assert.equal(
    lowDetailRecipeHoverNodeId(nodes, transform, {x: 46, y: 53}, null),
    'recipe-a',
  );
  assert.equal(
    lowDetailRecipeHoverNodeId(nodes, transform, {x: 68, y: 53}, 'recipe-a'),
    'recipe-a',
  );
  assert.equal(
    lowDetailRecipeHoverNodeId(nodes, transform, {x: 80, y: 53}, 'recipe-a'),
    'recipe-b',
  );
  assert.equal(lowDetailRecipeHoverNodeId(nodes, transform, {x: 500, y: 500}, null), null);
});

test('rejects invalid low-detail hover geometry', () => {
  const nodes = [{id: 'recipe', x: 0, y: 0, w: 100, h: 100, source: {kind: 'recipe'}}];
  assert.throws(
    () => lowDetailRecipeHoverNodeId(nodes, {x: 0, y: 0, scale: 0}, {x: 0, y: 0}, null),
    /geometry is invalid/,
  );
  assert.throws(
    () =>
      lowDetailRecipeHoverNodeId(
        nodes,
        {x: 0, y: 0, scale: 1},
        {x: 0, y: 0},
        null,
        20,
        10,
      ),
    /geometry is invalid/,
  );
});
