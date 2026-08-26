import assert from 'node:assert/strict';
import test from 'node:test';
import {
  materialInputSummary,
  recipeChildrenForDirection,
  recipeProducesItem,
  recipeUsesItem,
  usageGraphStart,
} from './direction.ts';

const recipe = {
  in: [
    [['item|test:anchor', 2]],
    [['item|test:catalyst', 1]],
  ],
  cat: [[['item|test:mold', 1]]],
  out: [
    [['item|test:result', 3, null, 0.75]],
    [['item|test:byproduct', 1]],
  ],
};

test('input traversal includes consumed materials and retained prerequisites', () => {
  assert.deepEqual(
    recipeChildrenForDirection(recipe, 'inputs').map(child => ({
      key: child.key,
      amount: child.amount,
      nonConsumed: child.nonConsumed,
      probabilityRole: child.probabilityRole,
    })),
    [
      {
        key: 'item|test:anchor',
        amount: 2,
        nonConsumed: false,
        probabilityRole: 'consume',
      },
      {
        key: 'item|test:catalyst',
        amount: 1,
        nonConsumed: false,
        probabilityRole: 'consume',
      },
      {
        key: 'item|test:mold',
        amount: 1,
        nonConsumed: true,
        probabilityRole: 'consume',
      },
    ],
  );
});

test('retained metadata distinguishes reusable catalysts from durability tools', () => {
  const retainedRecipe = {
    cat: [
      [['item|test:stone', 1]],
      [['item|test:hammer', 1]],
    ],
    retained: {
      'item|test:stone': {mode: 'reusable'},
      'item|test:hammer': {mode: 'durability', uses: 64},
    },
  };

  assert.deepEqual(
    recipeChildrenForDirection(retainedRecipe, 'inputs').map(child => ({
      key: child.key,
      mode: child.retentionMode,
      uses: child.retentionUses,
    })),
    [
      {key: 'item|test:stone', mode: 'reusable', uses: undefined},
      {key: 'item|test:hammer', mode: 'durability', uses: 64},
    ],
  );
});

test('output traversal expands every recipe output with production probability', () => {
  const children = recipeChildrenForDirection(recipe, 'outputs');
  assert.deepEqual(
    children.map(child => ({
      key: child.key,
      amount: child.amount,
      probability: child.probability,
      nonConsumed: child.nonConsumed,
      probabilityRole: child.probabilityRole,
    })),
    [
      {
        key: 'item|test:result',
        amount: 3,
        probability: 0.75,
        nonConsumed: false,
        probabilityRole: 'produce',
      },
      {
        key: 'item|test:byproduct',
        amount: 1,
        probability: undefined,
        nonConsumed: false,
        probabilityRole: 'produce',
      },
    ],
  );
});

test('detects whether the modal item participates on either recipe side', () => {
  assert.equal(recipeUsesItem(recipe, 'item|test:anchor'), true);
  assert.equal(recipeUsesItem(recipe, 'item|test:mold'), true);
  assert.equal(recipeUsesItem(recipe, 'item|test:result'), false);
  assert.equal(recipeProducesItem(recipe, 'item|test:result'), true);
  assert.equal(recipeProducesItem(recipe, 'item|test:anchor'), false);
});

test('uses an explicit Mekanism chemical flow without duplicating its tank carrier slot', () => {
  const chemicalRecipe = {
    in: [
      [['item|test:ore', 1]],
      [['mekanism/jei_plugin_jei_compat_gasstack|mekanism:oxygen|gas', 200]],
      [
        ['item|mekanism:basic_chemical_tank|filled', 1],
        ['item|test:chemical_source', 1],
      ],
    ],
    out: [[['item|test:processed_ore', 1]]],
  };

  assert.deepEqual(
    materialInputSummary(chemicalRecipe).map(input => [input.key, input.amount]),
    [
      ['item|test:ore', 1],
      ['mekanism/jei_plugin_jei_compat_gasstack|mekanism:oxygen|gas', 200],
    ],
  );
});

test('retains a chemical tank input when the recipe produces a tank', () => {
  const fillingRecipe = {
    in: [
      [['item|mekanism:basic_chemical_tank|empty', 1]],
      [['mekanism/jei_plugin_jei_compat_gasstack|mekanism:oxygen|gas', 200]],
    ],
    out: [[['item|mekanism:basic_chemical_tank|filled', 1]]],
  };

  assert.deepEqual(
    materialInputSummary(fillingRecipe).map(input => input.key),
    [
      'item|mekanism:basic_chemical_tank|empty',
      'mekanism/jei_plugin_jei_compat_gasstack|mekanism:oxygen|gas',
    ],
  );
});

test('promotes the primary usage product instead of a later byproduct', () => {
  assert.deepEqual(
    usageGraphStart(recipe),
    {
      rootKey: 'item|test:result',
      direction: 'inputs',
    },
  );
  assert.equal(usageGraphStart({...recipe, out: []}), null);
});
