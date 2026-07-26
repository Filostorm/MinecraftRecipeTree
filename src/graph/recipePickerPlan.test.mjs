import assert from 'node:assert/strict';
import test from 'node:test';
import {
  isDeferredGraphRecipeCategory,
  planRecipePickerChoices,
} from './recipePickerPlan.ts';

const categories = [
  {id: 'large', title: 'Mixer'},
  {id: 'small', title: 'Shapeless Crafting'},
  {id: 'recycling', title: 'Macerator Recycling'},
  {id: 'tiny', title: 'Chemical Reactor'},
];

function choices(categoryIndex, count) {
  return Array.from({length: count}, (_, recipeIndex) => ({
    ref: [categoryIndex, recipeIndex],
  }));
}

test('initial picker plan represents every recipe type before loading variants', () => {
  const plan = planRecipePickerChoices(
    [
      ...choices(0, 50),
      ...choices(1, 2),
      ...choices(2, 3),
      ...choices(3, 1),
    ],
    categories,
    6,
  );

  assert.deepEqual(
    plan.groups.map(group => [group.categoryIndex, group.choices.length]),
    [
      [3, 1],
      [1, 2],
      [0, 50],
      [2, 3],
    ],
  );
  assert.deepEqual(
    new Set(plan.initialChoices.map(choice => choice.ref[0])),
    new Set([0, 1, 2, 3]),
  );
  assert.deepEqual(
    plan.initialChoices.map(choice => choice.ref),
    [
      [3, 0],
      [1, 0],
      [0, 0],
      [2, 0],
      [1, 1],
      [0, 1],
    ],
  );
});

test('category completeness can exceed the normal initial choice target', () => {
  const manySingleRecipeTypes = Array.from({length: 45}, (_, categoryIndex) => ({
    ref: [categoryIndex, 0],
  }));
  const manyCategories = Array.from({length: 45}, (_, categoryIndex) => ({
    id: `category-${categoryIndex}`,
    title: `Category ${categoryIndex}`,
  }));

  const plan = planRecipePickerChoices(manySingleRecipeTypes, manyCategories, 40);
  assert.equal(plan.initialChoices.length, 45);
  assert.equal(new Set(plan.initialChoices.map(choice => choice.ref[0])).size, 45);
});

test('packaging and recycling categories are explicitly deferred', () => {
  assert.equal(isDeferredGraphRecipeCategory({id: 'a', title: 'Packager'}), true);
  assert.equal(isDeferredGraphRecipeCategory({id: 'b', title: 'Arc Furnace Recycling'}), true);
  assert.equal(isDeferredGraphRecipeCategory({id: 'c', title: 'Mixer'}), false);
});
