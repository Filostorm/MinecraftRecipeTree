import assert from 'node:assert/strict';
import test from 'node:test';
import {isFluidContainerTransferRecipe} from './recipeVisibility.ts';

const items = new Map([
  ['item|test:tank:empty', {k: 'item|test:tank:empty', id: 'test:tank', n: 'Empty Tank', m: 'test'}],
  ['item|test:tank:water', {k: 'item|test:tank:water', id: 'test:tank', n: 'Water Tank', m: 'test'}],
  ['item|minecraft:bucket', {k: 'item|minecraft:bucket', id: 'minecraft:bucket', n: 'Bucket', m: 'minecraft'}],
  ['item|minecraft:water_bucket', {k: 'item|minecraft:water_bucket', id: 'minecraft:water_bucket', n: 'Water Bucket', m: 'minecraft'}],
  ['item|test:wood', {k: 'item|test:wood', id: 'test:wood', n: 'Wood', m: 'test'}],
  ['item|test:treated_wood', {k: 'item|test:treated_wood', id: 'test:treated_wood', n: 'Treated Wood', m: 'test'}],
  ['item|test:fruit', {k: 'item|test:fruit', id: 'test:fruit', n: 'Fruit', m: 'test'}],
  ['item|test:mulch', {k: 'item|test:mulch', id: 'test:mulch', n: 'Mulch', m: 'test'}],
  ['item|minecraft:sponge:dry', {k: 'item|minecraft:sponge:dry', id: 'minecraft:sponge', n: 'Sponge', m: 'minecraft'}],
  ['item|minecraft:sponge:wet', {k: 'item|minecraft:sponge:wet', id: 'minecraft:sponge', n: 'Wet Sponge', m: 'minecraft'}],
  ['item|test:frame', {k: 'item|test:frame', id: 'test:frame', n: 'Empty Frame', m: 'test'}],
  ['item|test:water_source', {k: 'item|test:water_source', id: 'test:water_source', n: 'Infinite Water Source', m: 'test'}],
  ['item|test:duct:empty', {k: 'item|test:duct:empty', id: 'test:duct', n: 'Fluxduct (Empty)', m: 'test'}],
  ['item|test:duct:charged', {k: 'item|test:duct:charged', id: 'test:duct', n: 'Fluxduct', m: 'test'}],
]);

test('classifies filling and emptying of fluid containers', () => {
  assert.equal(
    isFluidContainerTransferRecipe(
      {
        in: [[['item|minecraft:bucket', 1]], [['fluid|fluid:water', 1000]]],
        out: [[['item|minecraft:water_bucket', 1]]],
      },
      items,
    ),
    true,
  );
  assert.equal(
    isFluidContainerTransferRecipe(
      {
        in: [[['item|test:tank:water', 1]]],
        out: [[['item|test:tank:empty', 1]], [['fluid|fluid:water', 1000]]],
      },
      items,
    ),
    true,
  );
});

test('does not hide legitimate fluid infusion or extraction recipes', () => {
  assert.equal(
    isFluidContainerTransferRecipe(
      {
        in: [[['item|test:wood', 1]], [['fluid|fluid:creosote', 100]]],
        out: [[['item|test:treated_wood', 1]]],
      },
      items,
    ),
    false,
  );
  assert.equal(
    isFluidContainerTransferRecipe(
      {
        in: [[['item|test:fruit', 1]]],
        out: [[['item|test:mulch', 1]], [['fluid|fluid:juice', 250]]],
      },
      items,
    ),
    false,
  );
  assert.equal(
    isFluidContainerTransferRecipe(
      {
        in: [[['item|test:frame', 1]], [['fluid|fluid:water', 2000]]],
        out: [[['item|test:water_source', 1]]],
      },
      items,
    ),
    false,
  );
  assert.equal(
    isFluidContainerTransferRecipe(
      {
        in: [[['item|test:duct:empty', 1]], [['fluid|fluid:redstone', 200]]],
        out: [[['item|test:duct:charged', 1]]],
      },
      items,
    ),
    false,
  );
});

test('recognizes sponge fluid-state transitions', () => {
  assert.equal(
    isFluidContainerTransferRecipe(
      {
        in: [[['item|minecraft:sponge:wet', 1]]],
        out: [[['item|minecraft:sponge:dry', 1]], [['fluid|fluid:water', 1000]]],
      },
      items,
    ),
    true,
  );
});

test('rejects multi-material, gas, and failed recipes', () => {
  assert.equal(
    isFluidContainerTransferRecipe({in: [[['item|minecraft:bucket', 1]]], out: [[['gasstack|test:steam', 1000]]]}, items),
    false,
  );
  assert.equal(isFluidContainerTransferRecipe({err: true}, items), false);
});
