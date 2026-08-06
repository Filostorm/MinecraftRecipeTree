import assert from 'node:assert/strict';
import test from 'node:test';
import {reconstructLegacyReplaceableInputs} from './legacyReplaceableInputs.ts';
import {materialInputSummary} from '../graph/direction.ts';

const category = {
  id: 'ie.workbench',
  title: "Engineer's Workbench",
  dir: 'recipes/ie.workbench',
  count: 1,
  catalysts: [],
};

function catalog(entries) {
  return new Map(entries.map(([k, n]) => [k, {k, n, m: k.split('|')[1].split(':')[0]}]));
}

test('collapses flattened glass and plate alternatives into single graph inputs', () => {
  const items = catalog([
    ['item|minecraft:glass', 'Glass'],
    ['item|minecraft:stained_glass:6', 'Pink Stained Glass'],
    ['item|chisel:glassdyedlime:0', 'Lime Stained Glass'],
    ['item|thermalfoundation:nickel_plate', 'Nickel Plate'],
    ['item|techreborn:nickel_plate', 'Nickel Plate'],
    ['item|immersiveengineering:copper_wire', 'Copper Wire'],
    ['item|minecraft:redstone', 'Redstone'],
    ['item|immersiveengineering:vacuum_tube', 'Vacuum Tube'],
  ]);
  const recipe = {
    in: [
      [['item|minecraft:glass', 1]],
      [['item|minecraft:stained_glass:6', 1]],
      [['item|chisel:glassdyedlime:0', 1]],
      [['item|thermalfoundation:nickel_plate', 1]],
      [['item|techreborn:nickel_plate', 1]],
      [['item|immersiveengineering:copper_wire', 1]],
      [['item|minecraft:redstone', 1]],
    ],
    out: [[['item|immersiveengineering:vacuum_tube', 3]]],
  };

  const repaired = reconstructLegacyReplaceableInputs(
    recipe,
    category,
    '1.12.2',
    items,
  );

  assert.equal(repaired.in.length, 4);
  assert.deepEqual(
    repaired.in[0].map(([key]) => key),
    [
      'item|minecraft:glass',
      'item|minecraft:stained_glass:6',
      'item|chisel:glassdyedlime:0',
    ],
  );
  assert.deepEqual(
    repaired.in[1].map(([key]) => key),
    ['item|thermalfoundation:nickel_plate', 'item|techreborn:nickel_plate'],
  );
  assert.deepEqual(
    materialInputSummary(repaired).map(input => input.key),
    [
      'item|minecraft:glass',
      'item|thermalfoundation:nickel_plate',
      'item|immersiveengineering:copper_wire',
      'item|minecraft:redstone',
    ],
  );
});

test('preserves repeated required inputs while merging each replaceable set', () => {
  const items = catalog([
    ['item|first:steel', 'Steel Ingot'],
    ['item|second:steel', 'Steel Ingot'],
    ['item|first:copper', 'Copper Ingot'],
    ['item|second:copper', 'Copper Ingot'],
    ['item|third:copper', 'Copper Ingot'],
    ['item|test:result', 'Result'],
  ]);
  const recipe = {
    in: [
      [['item|first:steel', 1]],
      [['item|second:steel', 1]],
      [['item|first:steel', 1]],
      [['item|second:steel', 1]],
      [['item|first:copper', 1]],
      [['item|second:copper', 1]],
      [['item|third:copper', 1]],
    ],
    out: [[['item|test:result', 1]]],
  };

  const repaired = reconstructLegacyReplaceableInputs(
    recipe,
    category,
    '1.12.2',
    items,
  );

  assert.equal(repaired.in.length, 3);
  assert.deepEqual(
    repaired.in.map(slot => slot.map(([key]) => key)),
    [
      ['item|first:steel', 'item|second:steel'],
      ['item|first:steel', 'item|second:steel'],
      ['item|first:copper', 'item|second:copper', 'item|third:copper'],
    ],
  );
  assert.equal(materialInputSummary(repaired)[0].amount, 2);
});

test('collapses MeatballCraft crucible ore variants into one choose-one input', () => {
  const items = catalog([
    ['item|minecraft:iron_ore', 'Iron Ore'],
    ['item|abyssalcraft:abyiroore', 'Abyssal Iron Ore'],
    ['item|cyclicmagic:nether_iron_ore', 'Nether Iron Ore'],
    ['item|cyclicmagic:end_iron_ore', 'End Iron Ore'],
    ['item|erebus:ore_iron', 'Erebus Iron Ore'],
    ['custom|aspect:metallum', 'Metallum'],
    ['item|thaumcraft:cluster:0', 'Native Iron Cluster'],
  ]);
  const crucible = {...category, id: 'THAUMCRAFT_CRUCIBLE', title: 'Crucible'};
  const recipe = {
    in: [
      [['item|minecraft:iron_ore', 1]],
      [['item|abyssalcraft:abyiroore', 1]],
      [['item|cyclicmagic:nether_iron_ore', 1]],
      [['item|cyclicmagic:end_iron_ore', 1]],
      [['item|erebus:ore_iron', 1]],
      [['custom|aspect:metallum', 5]],
    ],
    out: [[['item|thaumcraft:cluster:0', 1]]],
  };

  const repaired = reconstructLegacyReplaceableInputs(recipe, crucible, '1.12.2', items);

  assert.equal(repaired.in.length, 2);
  assert.deepEqual(
    repaired.in[0].map(([key]) => key),
    [
      'item|minecraft:iron_ore',
      'item|abyssalcraft:abyiroore',
      'item|cyclicmagic:nether_iron_ore',
      'item|cyclicmagic:end_iron_ore',
      'item|erebus:ore_iron',
    ],
  );
  assert.equal(materialInputSummary(repaired)[0].amount, 1);
});

test('logs and leaves a workbench recipe unchanged when catalog proof is unavailable', () => {
  const recipe = {
    in: [[['item|missing:first', 1]], [['item|missing:second', 1]]],
    out: [[['item|test:result', 1]]],
  };
  const errors = [];
  const originalError = console.error;
  console.error = (...parts) => errors.push(parts);
  try {
    assert.equal(
      reconstructLegacyReplaceableInputs(recipe, category, '1.12.2', new Map()),
      recipe,
    );
  } finally {
    console.error = originalError;
  }
  assert.equal(errors.length, 1);
  assert.match(String(errors[0][0]), /left unchanged/);
});

test('does not reinterpret ordinary crafting recipes', () => {
  const recipe = {
    in: [[['item|minecraft:glass', 1]], [['item|minecraft:stained_glass:6', 1]]],
  };
  const items = catalog([
    ['item|minecraft:glass', 'Glass'],
    ['item|minecraft:stained_glass:6', 'Pink Stained Glass'],
  ]);
  assert.equal(
    reconstructLegacyReplaceableInputs(
      recipe,
      {...category, id: 'minecraft.crafting'},
      '1.12.2',
      items,
    ),
    recipe,
  );
});
