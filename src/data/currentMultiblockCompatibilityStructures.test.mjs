import assert from 'node:assert/strict';
import test from 'node:test';
import meatballCraftStructures from './legacyMeatballCraft0186Structures.ts';
import mm2Structures from './legacyMm2Structures.ts';
import {requireRecipeStructure} from './recipeStructure.ts';

function uniqueStructures(structures) {
  return [...new Set(Object.values(structures))];
}

test('pins all 38 MM2 Multiblocked controller previews to valid placed-block geometry', () => {
  assert.equal(Object.keys(mm2Structures).length, 38);
  const unique = uniqueStructures(mm2Structures);
  assert.equal(unique.length, 38);
  for (const [recipeIndex, structure] of Object.entries(mm2Structures)) {
    assert.match(recipeIndex, /^\d+$/);
    assert.equal(requireRecipeStructure(structure, `MM2 recipe ${recipeIndex}`), structure);
  }
  assert.ok(unique.some(structure => structure.total > 400));
});

test('pins all 260 MeatballCraft MMCE previews to valid placed-block geometry', () => {
  const unique = uniqueStructures(meatballCraftStructures);
  assert.equal(unique.length, 260);
  for (const [lookupKey, structure] of Object.entries(meatballCraftStructures)) {
    assert.equal(
      requireRecipeStructure(structure, `MeatballCraft recipe ${lookupKey}`),
      structure,
    );
  }
  assert.ok(unique.some(structure => structure.total > 8_000));
});
