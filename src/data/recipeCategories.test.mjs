import assert from 'node:assert/strict';
import test from 'node:test';
import {
  compareRecipeCategories,
  GTNH_AE2_WORLD_CRAFTING_INFORMATION_CATEGORY_ID,
  GTNH_BETTERQUESTING_INFORMATION_CATEGORY_ID,
  isMetaRecipeCategory,
  isRepairRecipeCategory,
  isSecondaryRecipeCategory,
  isStandardCraftingCategory,
} from './recipeCategories.ts';

const category = (id, title = id) => ({id, title, dir: id, count: 1, catalysts: []});

test('recognizes modern JEI and legacy HEI crafting and workbench category IDs', () => {
  for (const id of [
    'minecraft:crafting',
    'minecraft.crafting',
    'minecraft:workbench',
    'minecraft.workbench',
    'minecraft:crafting/workbench',
    'minecraft.crafting.workbench',
  ]) {
    assert.equal(isStandardCraftingCategory(category(id)), true, id);
  }

  for (const id of ['minecraft:smelting', 'create:crafting', 'minecraft:crafting_grid']) {
    assert.equal(isStandardCraftingCategory(category(id)), false, id);
  }
});

test('sorts either standard crafting UID form before other recipe categories', () => {
  const furnace = category('minecraft:furnace', 'Furnace');
  assert.ok(compareRecipeCategories(category('minecraft.crafting', 'Crafting'), furnace) < 0);
  assert.ok(compareRecipeCategories(category('minecraft:crafting', 'Crafting'), furnace) < 0);
});

test('classifies anvil categories from any namespace as secondary repairs', () => {
  for (const id of [
    'minecraft.anvil',
    'minecraft:anvil',
    'tconstruct.anvil.repair',
    'tconstruct:anvil/repair',
    'anvil',
  ]) {
    const value = category(id);
    assert.equal(isSecondaryRecipeCategory(value), true, id);
    assert.equal(isRepairRecipeCategory(value), true, id);
  }

  for (const id of [
    'minecraft:smithing',
    'minecraft.smithing',
    'custommod:smithing/upgrade',
    'custommod.smithing.upgrade',
    'smithing',
  ]) {
    const value = category(id);
    assert.equal(isSecondaryRecipeCategory(value), true, id);
    assert.equal(isRepairRecipeCategory(value), false, id);
  }

  for (const id of ['custommod:super_anvil', 'custommod:smithing_press', 'anvilworks:smelting']) {
    const value = category(id);
    assert.equal(isSecondaryRecipeCategory(value), false, id);
    assert.equal(isRepairRecipeCategory(value), false, id);
  }
});

test('excludes both JEI and HEI information and description meta-categories', () => {
  for (const id of [
    'jei:information',
    'jei.information',
    'jei:description',
    'jei.description',
    'jei:information/item',
    'jei.description.item',
    'jei:tag_recipes/item',
    'jei.tag_recipes.item',
    'hei:tag_recipes/fluid',
    'hei.tag_recipes.fluid',
    'minecraft:plugins/tag',
    'roughlyenoughitems:plugins/tag/item',
    'minecraft.plugins.tag',
  ]) {
    assert.equal(isMetaRecipeCategory(category(id)), true, id);
  }

  for (const id of [
    'minecraft.crafting',
    'jei:information_processing',
    'jei:description_recipe',
    'custommod:description',
    'custommod:plugins/tag_processor',
    'custommod:plugins/tagged',
  ]) {
    assert.equal(isMetaRecipeCategory(category(id)), false, id);
  }
});

test('classifies exact GTNH AE2 and BetterQuesting item-reference pages as informational', () => {
  for (const id of [
    GTNH_AE2_WORLD_CRAFTING_INFORMATION_CATEGORY_ID,
    GTNH_BETTERQUESTING_INFORMATION_CATEGORY_ID,
  ]) {
    assert.equal(isMetaRecipeCategory(category(id)), true, id);
    assert.equal(isMetaRecipeCategory(category(`${id}.execution`)), false, `${id}.execution`);
  }
});
