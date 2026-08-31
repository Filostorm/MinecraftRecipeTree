import assert from 'node:assert/strict';
import test from 'node:test';
import {
  assertPortableTreePackMatches,
  buildPortableTree,
  parsePortableTree,
  portableSelectionAsStored,
  resolveConnectedPortableSelections,
} from './portableTree.ts';

const descriptor = {
  slug: 'test-pack',
  displayName: 'Test Pack',
  minecraftVersion: '1.20.1',
  packVersion: '1.0.0',
  publicationId: 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
  previewAssetSetId: 'sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
  isDefault: false,
};

test('round-trips a portable recipe tree with stable JEI recipe identities', () => {
  const session = {
    version: 2,
    rootKey: 'item|minecraft:stone',
    direction: 'inputs',
    productionPlan: {amount: 64, windowSeconds: 1},
    selections: [
      {
        path: [],
        itemKey: 'item|minecraft:stone',
        source: {kind: 'recipe', ref: [3, 7]},
      },
    ],
  };
  const share = buildPortableTree(
    session,
    descriptor,
    new Map([['3:7', 'minecraft:crafting|minecraft:stone']]),
    '2026-08-01T00:00:00.000Z',
  );
  const parsed = parsePortableTree(JSON.stringify(share));
  assert.equal(parsed.selections[0].source.recipeKey, 'minecraft:crafting|minecraft:stone');
  assert.deepEqual(portableSelectionAsStored(parsed.selections[0], [3, 7]), session.selections[0]);
});

test('rejects oversized and unsupported payloads', () => {
  assert.throws(() => parsePortableTree('{}'), /not a supported/);
  assert.throws(() => parsePortableTree('x'.repeat(1_048_577)), /1 MiB/);
});

test('accepts the ref-free payload emitted by the in-game JEI viewer', () => {
  const parsed = parsePortableTree(JSON.stringify({
    format: 'minecraft-recipe-tree',
    version: 1,
    createdAt: '2026-08-01T00:00:00Z',
    pack: {minecraftVersion: '1.20.1', name: 'In-game JEI'},
    rootKey: 'item|minecraft:stone',
    direction: 'inputs',
    productionPlan: {amount: 64, windowSeconds: 1},
    selections: [{
      path: [],
      itemKey: 'item|minecraft:stone',
      source: {kind: 'recipe', recipeKey: 'minecraft:crafting|minecraft:stone'},
    }],
  }));
  assert.equal(parsed.selections[0].source.ref, undefined);
  assert.deepEqual(
    portableSelectionAsStored(parsed.selections[0], [3, 7]).source,
    {kind: 'recipe', ref: [3, 7]},
  );
});

test('requires a shared history to match the selected pack publication and version', () => {
  const history = parsePortableTree(JSON.stringify({
    format: 'minecraft-recipe-tree',
    version: 1,
    createdAt: '2026-08-20T00:00:00Z',
    pack: {
      minecraftVersion: '1.20.1',
      name: 'Test Pack',
      version: '1.0.0',
      slug: 'test-pack',
      publicationId: descriptor.publicationId,
    },
    rootKey: 'item|minecraft:stone',
    direction: 'inputs',
    selections: [],
  }));
  assert.doesNotThrow(() => assertPortableTreePackMatches(history, descriptor));
  assert.throws(
    () => assertPortableTreePackMatches(history, {...descriptor, packVersion: '2.0.0'}),
    /pack version 1\.0\.0/,
  );
  assert.throws(
    () => assertPortableTreePackMatches(history, {...descriptor, displayName: 'A Different Pack'}),
    /selected pack is A Different Pack/,
  );
  assert.throws(
    () => assertPortableTreePackMatches(history, {
      ...descriptor,
      publicationId: 'sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
    }),
    /different publication/,
  );
});

test('keeps valid sibling recipes while removing unavailable branches and their descendants', async () => {
  const recipe = (path, itemKey, recipeKey) => ({
    path,
    itemKey,
    source: {kind: 'recipe', recipeKey},
  });
  const selections = [
    recipe([], 'item|root', 'test|root'),
    recipe([0], 'item|missing', 'test|missing'),
    recipe([0, 0], 'item|orphan', 'test|orphan'),
    recipe([1], 'item|sibling', 'test|sibling'),
  ];
  const resolvedKeys = [];
  const result = await resolveConnectedPortableSelections(
    selections,
    async selection => {
      resolvedKeys.push(selection.itemKey);
      if (selection.itemKey === 'item|missing') {
        throw new Error('Recipe is unavailable.');
      }
      return portableSelectionAsStored(selection, [1, resolvedKeys.length]);
    },
  );

  assert.deepEqual(
    result.selections.map(selection => selection.itemKey),
    ['item|root', 'item|sibling'],
  );
  assert.deepEqual(resolvedKeys, ['item|root', 'item|missing', 'item|sibling']);
  assert.deepEqual(
    result.skipped.map(skipped => [skipped.selection.itemKey, skipped.reason]),
    [
      ['item|missing', 'unavailable'],
      ['item|orphan', 'dependent'],
    ],
  );
});
