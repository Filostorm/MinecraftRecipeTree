import assert from 'node:assert/strict';
import test from 'node:test';
import {
  graphSessionStorageKey,
  parseGraphSession,
  serializeGraphSession,
} from './graphSession.ts';

const scope = {slug: 'meatballcraft', publicationId: 'a'.repeat(64)};

test('isolates the saved graph by immutable modpack publication', () => {
  assert.equal(
    graphSessionStorageKey(scope),
    `graphSession:v1:meatballcraft:${'a'.repeat(64)}`,
  );
  assert.notEqual(
    graphSessionStorageKey(scope),
    graphSessionStorageKey({...scope, publicationId: 'b'.repeat(64)}),
  );
});

test('serializes only expanded source identities and tree paths', () => {
  const root = {
    id: 'root',
    key: 'item|example:machine',
    ancestors: [],
    source: {
      id: 'root.s',
      kind: 'recipe',
      ref: [2, 4],
      allowFluidTransfer: true,
      ingredientSelections: {
        'item|example:copper': 'item|example:annealed-copper',
      },
      inputs: [
        {
          id: 'root.s.0',
          key: 'item|example:ore',
          ancestors: ['item|example:machine'],
          source: {
            id: 'root.s.0.s',
            kind: 'block',
            blockKey: 'block|example:ore',
            inputs: [],
          },
        },
        {
          id: 'root.s.1',
          key: 'item|example:catalyst',
          ancestors: ['item|example:machine'],
        },
      ],
    },
  };
  assert.deepEqual(serializeGraphSession(root, 'inputs'), {
    version: 1,
    rootKey: 'item|example:machine',
    direction: 'inputs',
    selections: [
      {
        path: [],
        itemKey: 'item|example:machine',
        source: {
          kind: 'recipe',
          ref: [2, 4],
          allowFluidTransfer: true,
          ingredientSelections: {
            'item|example:copper': 'item|example:annealed-copper',
          },
        },
      },
      {
        path: [0],
        itemKey: 'item|example:ore',
        source: {kind: 'block', blockKey: 'block|example:ore'},
      },
    ],
  });
});

test('parses a validated output-directed saved graph', () => {
  const session = {
    version: 1,
    rootKey: 'item|example:input',
    direction: 'outputs',
    selections: [
      {
        path: [],
        itemKey: 'item|example:input',
        source: {kind: 'recipe', ref: [3, 7]},
      },
      {
        path: [0],
        itemKey: 'item|example:output',
        source: {kind: 'mob', mobId: 'example:mob'},
      },
    ],
  };
  assert.deepEqual(parseGraphSession(JSON.stringify(session)), session);
});

test('rejects malformed, duplicated, and orphaned expansion paths', () => {
  assert.throws(
    () => parseGraphSession('{"version":1,"rootKey":"x","direction":"inputs","selections":{} }'),
    /storage contract/,
  );
  const root = {
    path: [],
    itemKey: 'item|example:root',
    source: {kind: 'recipe', ref: [0, 0]},
  };
  assert.throws(
    () =>
      parseGraphSession(
        JSON.stringify({
          version: 1,
          rootKey: 'item|example:root',
          direction: 'inputs',
          selections: [root, root],
        }),
      ),
    /repeats an expanded node path/,
  );
  assert.throws(
    () =>
      parseGraphSession(
        JSON.stringify({
          version: 1,
          rootKey: 'item|example:root',
          direction: 'inputs',
          selections: [{...root, path: [1]}],
        }),
      ),
    /expanded parent/,
  );
});
