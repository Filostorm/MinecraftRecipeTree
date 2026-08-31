import assert from 'node:assert/strict';
import test from 'node:test';
import {
  PROJECTE_EMC_CATEGORY_ID,
  PROJECTE_EMC_KEY,
  PROJECTE_TRANSMUTATION_TABLE_KEY,
  projecteEmcIconItemKey,
  projecteEmcTransmutation,
  projecteEmcValue,
} from './projecteEmc.ts';

test('reads EMC only from the synthetic source for the requested output', () => {
  const recipe = {
    in: [[[PROJECTE_EMC_KEY, 8192]]],
    out: [[['item|fixture:target', 1]]],
  };

  assert.equal(PROJECTE_EMC_CATEGORY_ID, 'projecte:emc_transmutation');
  assert.equal(PROJECTE_TRANSMUTATION_TABLE_KEY, 'item|projecte:transmutation_table');
  assert.equal(projecteEmcIconItemKey(PROJECTE_EMC_KEY), PROJECTE_TRANSMUTATION_TABLE_KEY);
  assert.equal(projecteEmcIconItemKey('item|fixture:target'), 'item|fixture:target');
  assert.equal(projecteEmcValue(recipe, 'item|fixture:target'), 8192);
  assert.equal(projecteEmcValue(recipe, 'item|fixture:other'), null);
  assert.equal(
    projecteEmcValue({...recipe, in: [[[PROJECTE_EMC_KEY, 0]]]}, 'item|fixture:target'),
    null,
  );
});

test('parses a synthetic EMC to item recipe for generated previews', () => {
  const recipe = {
    id: 'projecte:emc/fixture-target',
    in: [[[PROJECTE_EMC_KEY, 22_200_000]]],
    out: [[['item|fixture:target', 1]]],
  };

  assert.deepEqual(projecteEmcTransmutation(recipe), {
    emc: 22_200_000,
    outputItemKey: 'item|fixture:target',
    outputAmount: 1,
  });
  assert.equal(projecteEmcTransmutation({...recipe, id: 'fixture:crafting'}), null);
  assert.equal(
    projecteEmcTransmutation({...recipe, in: [[[PROJECTE_EMC_KEY, 0]]]}),
    null,
  );
});
