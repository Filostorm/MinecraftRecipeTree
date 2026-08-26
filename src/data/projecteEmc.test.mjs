import assert from 'node:assert/strict';
import test from 'node:test';
import {
  PROJECTE_EMC_CATEGORY_ID,
  PROJECTE_EMC_KEY,
  projecteEmcValue,
} from './projecteEmc.ts';

test('reads EMC only from the synthetic source for the requested output', () => {
  const recipe = {
    in: [[[PROJECTE_EMC_KEY, 8192]]],
    out: [[['item|fixture:target', 1]]],
  };

  assert.equal(PROJECTE_EMC_CATEGORY_ID, 'projecte:emc_transmutation');
  assert.equal(projecteEmcValue(recipe, 'item|fixture:target'), 8192);
  assert.equal(projecteEmcValue(recipe, 'item|fixture:other'), null);
  assert.equal(
    projecteEmcValue({...recipe, in: [[[PROJECTE_EMC_KEY, 0]]]}, 'item|fixture:target'),
    null,
  );
});
