import assert from 'node:assert/strict';
import test from 'node:test';
import {findTreeTotalTarget} from './treeTotalTargets.ts';

function item(id, key, options = {}) {
  return {id, key, ancestors: [], ...options};
}

test('resolves an input total to its matching leaf graph node', () => {
  const flux = item('root.s.1', 'item|sonarcore:material_100');
  const root = item('root', 'item|test:result', {
    source: {
      id: 'root.s',
      kind: 'recipe',
      recipe: {in: [], out: []},
      inputs: [item('root.s.0', 'item|test:other'), flux],
    },
  });

  assert.equal(
    findTreeTotalTarget(
      root,
      {key: flux.key, amount: 2, variants: 1},
      'input',
    ),
    flux,
  );
});

test('matches logical ingredient tags instead of selecting a different variant row', () => {
  const first = item('root.s.0', 'item|test:copper', {tag: 'forge:ingots/copper'});
  const second = item('root.s.1', 'item|test:copper', {tag: 'custom:conductors'});
  const root = item('root', 'item|test:result', {
    source: {
      id: 'root.s',
      kind: 'recipe',
      recipe: {in: [], out: []},
      inputs: [first, second],
    },
  });

  assert.equal(
    findTreeTotalTarget(
      root,
      {
        key: 'item|test:copper',
        amount: 1,
        variants: 2,
        tag: 'custom:conductors',
      },
      'input',
    ),
    second,
  );
});

test('resolves retained prerequisites even when their source is expanded', () => {
  const mold = item('root.s.0', 'item|test:mold', {
    nonConsumed: true,
    source: {
      id: 'root.s.0.s',
      kind: 'recipe',
      recipe: {in: [], out: []},
      inputs: [item('root.s.0.s.0', 'item|test:clay')],
    },
  });
  const root = item('root', 'item|test:result', {
    source: {
      id: 'root.s',
      kind: 'recipe',
      recipe: {in: [], out: []},
      inputs: [mold],
    },
  });

  assert.equal(
    findTreeTotalTarget(
      root,
      {key: mold.key, amount: 1, variants: 1},
      'prerequisite',
    ),
    mold,
  );
});

test('does not resolve recipe-expanded consumed nodes that are absent from input totals', () => {
  const expanded = item('root.s.0', 'item|test:intermediate', {
    source: {
      id: 'root.s.0.s',
      kind: 'recipe',
      recipe: {in: [], out: []},
      inputs: [item('root.s.0.s.0', 'item|test:ore')],
    },
  });

  assert.equal(
    findTreeTotalTarget(
      expanded,
      {key: expanded.key, amount: 1, variants: 1},
      'input',
    ),
    null,
  );
});
