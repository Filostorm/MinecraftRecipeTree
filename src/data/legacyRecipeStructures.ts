import type {Category, Recipe} from '../types';
import {requireRecipeStructure} from './recipeStructure';

const MULTIBLOCK_MADNESS_323_PUBLICATION =
  'f38dac61a05a21c661b38398844d7d140627650f430f5b7b2bbee9c812b6a01e';
const MODULAR_MACHINERY_PREVIEW_CATEGORY = 'modularmachinery.preview';

function primaryOutputKey(recipe: Recipe): string | undefined {
  return recipe.out?.[0]?.[0]?.[0];
}

/**
 * The active 3.2.3 publication predates structured multiblock export. Lazy-load its exact,
 * source-pinned machine definitions so users do not have to wait for the next full pack export.
 */
export async function applyLegacyRecipeStructures(
  recipes: Recipe[],
  publicationId: string,
  category: Category,
): Promise<Recipe[]> {
  if (
    publicationId !== MULTIBLOCK_MADNESS_323_PUBLICATION ||
    category.id !== MODULAR_MACHINERY_PREVIEW_CATEGORY ||
    recipes.every(recipe => recipe.structure !== undefined)
  ) {
    return recipes;
  }
  const {default: structures} = await import('./legacyMultiblockMadness323Structures');
  return recipes.map((recipe, index) => {
    if (recipe.structure !== undefined) return recipe;
    const key = primaryOutputKey(recipe);
    const structure = key === undefined ? undefined : structures[key];
    if (structure === undefined) {
      throw new Error(
        `Legacy Multiblock Madness structure recipe ${index} has no pinned machine definition.`,
      );
    }
    return {
      ...recipe,
      structure: requireRecipeStructure(
        structure,
        `legacy Multiblock Madness structure recipe ${index}`,
      ),
    };
  });
}
