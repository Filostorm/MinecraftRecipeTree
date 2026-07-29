import assert from 'node:assert/strict';
import test from 'node:test';
import {
  MEATBALLCRAFT_RECIPE_STAGES,
  MEATBALLCRAFT_STAGE_COMPATIBILITY_PUBLICATION_ID,
  applyRecipeStageMetadata,
  isRecipeVisibleForStages,
} from './recipeStages.ts';

const descriptor = {
  slug: 'meatballcraft',
  displayName: 'MeatballCraft',
  minecraftVersion: '1.12.2',
  packVersion: '0.18.6',
  publicationId: MEATBALLCRAFT_STAGE_COMPATIBILITY_PUBLICATION_ID,
  previewAssetSetId: 'a'.repeat(64),
  isDefault: true,
};

test('pins every verified MeatballCraft 0.18.6 RecipeStages assignment', () => {
  assert.equal(Object.keys(MEATBALLCRAFT_RECIPE_STAGES).length, 119);
  assert.equal(new Set(Object.values(MEATBALLCRAFT_RECIPE_STAGES)).size, 102);
  assert.equal(MEATBALLCRAFT_RECIPE_STAGES['crafttweaker:modular_controller'], 'modularstage');
  assert.equal(MEATBALLCRAFT_RECIPE_STAGES['crafttweaker:ezpzwandsbbynos'], 'hardmode');
  assert.equal(
    MEATBALLCRAFT_RECIPE_STAGES['draconicevolution:fusion_crafting_core'],
    'draconicstage',
  );
});

test('adds compatibility stage metadata only to its immutable publication', () => {
  assert.deepEqual(
    applyRecipeStageMetadata({id: 'crafttweaker:modular_controller'}, descriptor),
    {id: 'crafttweaker:modular_controller', stage: 'modularstage'},
  );
  assert.deepEqual(
    applyRecipeStageMetadata(
      {id: 'crafttweaker:modular_controller'},
      {...descriptor, publicationId: 'b'.repeat(64)},
    ),
    {id: 'crafttweaker:modular_controller'},
  );
});

test('accepts native stage metadata and fails closed on invalid or conflicting values', () => {
  const native = {id: 'example:recipe', stage: 'native_stage'};
  assert.equal(applyRecipeStageMetadata(native, descriptor), native);
  assert.throws(
    () => applyRecipeStageMetadata({id: 'example:recipe', stage: 'bad stage'}, descriptor),
    /invalid stage identifier/,
  );
  assert.throws(
    () =>
      applyRecipeStageMetadata(
        {id: 'crafttweaker:modular_controller', stage: 'different_stage'},
        descriptor,
      ),
    /compatibility manifest declares/,
  );
});

test('applies one stage-visibility predicate to native and compatibility metadata', () => {
  const hidden = new Set(['hardmode']);
  assert.equal(isRecipeVisibleForStages({id: 'example:plain'}, hidden), true);
  assert.equal(isRecipeVisibleForStages({id: 'example:hard', stage: 'hardmode'}, hidden), false);
  assert.equal(isRecipeVisibleForStages(undefined, hidden), false);
});
