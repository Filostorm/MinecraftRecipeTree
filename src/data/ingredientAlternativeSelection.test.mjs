import assert from 'node:assert/strict';
import test from 'node:test';
import {
  applyIngredientSelections,
  selectSlotAlternative,
} from './ingredientAlternativeSelection.ts';
import {inputSlotSummary} from './slotSummary.ts';

const first = 'item|test:first';
const second = 'item|test:second';
const third = 'item|test:third';

test('selects and remembers a displayed ingredient alternative', () => {
  const summary = inputSlotSummary([[[first, 1], [second, 1], [third, 1]]])[0];
  const selected = selectSlotAlternative(summary, second);
  const selectedAgain = selectSlotAlternative(selected, third);

  assert.equal(selectedAgain.key, third);
  assert.equal(selectedAgain.selectionKey, first);
  assert.deepEqual(selectedAgain.alternatives, [first, second, third]);
});

test('reorders only the selected immutable recipe input slot', () => {
  const recipe = {
    in: [
      [[first, 1], [second, 2]],
      [['item|test:fixed', 4]],
    ],
    out: [[['item|test:result', 1]]],
  };
  const selected = applyIngredientSelections(recipe, {[first]: second});

  assert.notEqual(selected, recipe);
  assert.deepEqual(selected.in?.[0], [[second, 2], [first, 1]]);
  assert.deepEqual(selected.in?.[1], recipe.in[1]);
  assert.equal(recipe.in[0][0][0], first);
});

test('rejects a selection that is not in the displayed slot', () => {
  const summary = inputSlotSummary([[[first, 1], [second, 1]]])[0];
  assert.throws(
    () => selectSlotAlternative(summary, 'item|test:missing'),
    /is not a member/,
  );
});
