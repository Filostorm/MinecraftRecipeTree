import assert from 'node:assert/strict';
import test from 'node:test';
import structures from './legacyMultiblockMadness323Structures.ts';
import {requireRecipeStructure} from './recipeStructure.ts';

test('pins all 12 Multiblock Madness 3.2.3 structures to valid counted geometry', () => {
  assert.equal(Object.keys(structures).length, 12);
  for (const [blueprint, structure] of Object.entries(structures)) {
    assert.match(
      blueprint,
      /^item\|modularmachinery:itemblueprint:modularmachinery:/,
    );
    assert.equal(requireRecipeStructure(structure, blueprint), structure);
  }
  assert.equal(
    structures[
      'item|modularmachinery:itemblueprint:modularmachinery:lowgravitydepositionchamber'
    ].total,
    530,
  );
});
