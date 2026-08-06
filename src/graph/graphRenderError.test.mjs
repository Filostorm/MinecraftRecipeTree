import assert from 'node:assert/strict';
import test from 'node:test';
import {graphRenderRecovery} from './graphRenderError.ts';

test('classifies browser resource exhaustion separately from graph data failures', () => {
  const recovery = graphRenderRecovery(new RangeError('Maximum call stack size exceeded'));
  assert.equal(recovery.kind, 'resources');
  assert.match(recovery.title, /drawing limit/u);
});

test('classifies invalid item imagery separately from layout failures', () => {
  const recovery = graphRenderRecovery(
    new Error('ItemIcon size 44px is not aligned to the logical 16px pixel grid.'),
  );
  assert.equal(recovery.kind, 'assets');
  assert.match(recovery.title, /item preview/u);
});

test('classifies graph layout invariants with layout recovery copy', () => {
  const recovery = graphRenderRecovery(
    new Error('Graph contour apportionment could not find the leftmost sibling.'),
  );
  assert.equal(recovery.kind, 'layout');
  assert.match(recovery.title, /layout/u);
});

test('classifies stale saved-tree references as modpack data mismatches', () => {
  const recovery = graphRenderRecovery(
    new Error('Saved graph recipe reference is unavailable in this pack.'),
  );
  assert.equal(recovery.kind, 'data');
  assert.match(recovery.title, /modpack/u);
});

test('keeps unexpected rendering exceptions generic and recovery-safe', () => {
  const recovery = graphRenderRecovery(new TypeError('Unexpected component failure'));
  assert.equal(recovery.kind, 'unknown');
  assert.match(recovery.message, /saved trees are still available/u);
});
