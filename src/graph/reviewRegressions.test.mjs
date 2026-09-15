import assert from 'node:assert/strict';
import test from 'node:test';
import {readFileSync} from 'node:fs';
import {childRequirementId, resourceIdentity, resourceCompletionIdentity, catalystOverride} from './resourceIdentity.ts';
import {withCatalystItem, catalystItemsKey} from './catalystItems.ts';
import {resourceProgressKey} from './resourceProgress.ts';
import {calculateTreeTotals} from './treeTotals.ts';
import {resourceOutlineRows} from './resourceOutline.ts';
import {parseGraphSession, serializeGraphSession} from './graphSession.ts';
import {findRecipeExpansionOwner, createDeferredRecipeSourceResolver} from './expansionOwnership.ts';

const item = (id, key, amount = 1) => ({id, key, amount, ancestors: []});
test('restoring directly into Resources loads the graph index', () => {
  const app = readFileSync(new URL('../../App.tsx', import.meta.url), 'utf8');
  const guard = app.match(/if \(([^\n]+)\) return;\n    void data\.ensureIndex\(\)/)?.[1];
  assert.ok(guard, 'index-loading effect has a guard');
  const skips = new Function('tab', 'data', `return ${guard};`);
  for (const tab of ['graph', 'resources']) {
    assert.equal(skips(tab, {indexStatus: 'idle'}), false);
    assert.equal(skips(tab, {indexStatus: 'ready'}), true);
    assert.equal(skips(tab, {indexStatus: 'loading'}), true);
  }
  assert.equal(skips('settings', {indexStatus: 'idle'}), true);
});
function recipe(node, ref, inputs) {
  node.source = {id: `${node.id}.s`, kind: 'recipe', ref,
    recipe: {out: [[[node.key, 1]]]}, inputs};
  return node;
}

test('folded descendants keep totals, ownership and serialized recipes', () => {
  const gears = recipe(item('root.s.0', 'item|gear', 2), [0, 1], [item('root.s.0.s.0', 'item|iron', 4)]);
  const root = recipe(item('root', 'item|machine'), [0, 0], [gears]);
  root.buildId = 'independent-build';
  const before = calculateTreeTotals(root);
  gears.collapsedSource = gears.source;
  gears.source = undefined;
  assert.deepEqual(calculateTreeTotals(root), before);
  assert.equal(findRecipeExpansionOwner(root, gears.key, 'inputs', {ref: [0, 1]}), gears);
  assert.equal(createDeferredRecipeSourceResolver(root, 'inputs')({
    ...item('other', gears.key), deferredRecipeExpansion: {ref: [0, 1]},
  }), gears.collapsedSource);
  root.collapsedSource = root.source;
  root.source = undefined;
  assert.deepEqual(calculateTreeTotals(root), before);
  const stored = parseGraphSession(JSON.stringify(serializeGraphSession(root, 'inputs')));
  assert.equal(stored.buildId, 'independent-build');
  assert.equal(stored.selections.length, 2);
  assert.ok(stored.selections.every(selection => selection.collapsed));
});

test('a reused positional id cannot reuse a completion or tool decision for a different requirement', () => {
  const root = item('root', 'item|machine');
  const old = {...item('root.s.0', 'item|iron', 4), requirementId: childRequirementId(root, [0, 0], 0)};
  const changedRecipe = {...old, requirementId: childRequirementId(root, [0, 1], 0)};
  const changedItem = {...old, key: 'item|diamond'};
  for (const changed of [changedRecipe, changedItem]) {
    assert.notEqual(resourceIdentity(old), resourceIdentity(changed));
    assert.notEqual(resourceCompletionIdentity(old, 4), resourceCompletionIdentity(changed, 4));
    assert.equal(catalystOverride(new Set([resourceIdentity(old)]), changed), undefined);
  }
  assert.notEqual(resourceCompletionIdentity(old, 4), resourceCompletionIdentity(old, 64));
  assert.notEqual(resourceCompletionIdentity(old, null), resourceCompletionIdentity(old, 4));
  const scope = {slug: 'pack', publicationId: 'publication'};
  assert.notEqual(resourceProgressKey(scope, 'build-a'), resourceProgressKey(scope, 'build-b'));
  assert.notEqual(catalystItemsKey(scope, 'build-a'), catalystItemsKey(scope, 'build-b'));
});

test('treat as resource persists an explicit false through serialization and rebuild', () => {
  const node = {...item('root.s.0', 'item|hammer'), requirementId: 'recipe-path'};
  const yes = withCatalystItem(new Set(), resourceIdentity(node), true);
  const no = withCatalystItem(yes, resourceIdentity(node), false);
  const restored = new Set(JSON.parse(JSON.stringify([...no])));
  assert.equal(catalystOverride(restored, {...node}), false);
  assert.equal(catalystOverride(restored, {...node}) ?? true, false);
  assert.equal(catalystOverride(withCatalystItem(restored, resourceIdentity(node), true), node), true);
});

test('calculated unknown amounts stay unknown and sort after known requirements', () => {
  const unknown = item('root.s.0', 'item|unknown', 64);
  const known = item('root.s.1', 'item|known', 1);
  const root = recipe(item('root', 'item|machine'), [0, 0], [unknown, known]);
  const rows = resourceOutlineRows(root, {requiredByNode: new Map([[unknown.id, null], [known.id, 2]])});
  assert.equal(rows[0].key, known.key);
  assert.equal(rows[1].amount, null);
});
