import assert from 'node:assert/strict';
import test from 'node:test';
import {
  expectedIntentionalPreviewlessRecipes,
  isIntentionalProjecteEmcRecipe,
} from './intentional-previewless-recipes.mjs';

const emcRecipe = {
  id: 'projecte:emc/abc123',
  in: [[['emc|projecte:emc', 64]]],
  out: [[['item|minecraft:diamond', 1]]],
};

test('recognizes only structured ProjectE EMC synthetic recipes', () => {
  assert.equal(isIntentionalProjecteEmcRecipe(emcRecipe, 'projecte:emc_transmutation'), true);
  assert.equal(isIntentionalProjecteEmcRecipe({...emcRecipe, img: 'r0.png'}, 'projecte:emc_transmutation'), false);
  assert.equal(isIntentionalProjecteEmcRecipe(emcRecipe, 'minecraft.crafting'), false);
  assert.equal(
    isIntentionalProjecteEmcRecipe({...emcRecipe, in: [[['item|minecraft:coal', 1]]]}, 'projecte:emc_transmutation'),
    false,
  );
});

test('allows the exact accepted omission count only for MeatballCraft 0.18.6.4', () => {
  assert.equal(
    expectedIntentionalPreviewlessRecipes('meatballcraft-1.12.2', {
      pack: {name: 'MeatballCraft', version: 'prerelease-0.18.6.4'},
      counts: {recipes: 376299},
    }),
    11106,
  );
  assert.equal(
    expectedIntentionalPreviewlessRecipes('meatballcraft-1.12.2', {
      pack: {name: 'MeatballCraft', version: 'prerelease-0.18.6.5'},
      counts: {recipes: 376299},
    }),
    0,
  );
});
