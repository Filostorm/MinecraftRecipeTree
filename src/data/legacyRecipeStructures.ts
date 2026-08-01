import type {Category, Recipe} from '../types';
import {requireRecipeStructure} from './recipeStructure';

const MULTIBLOCK_MADNESS_323_PUBLICATION =
  'f38dac61a05a21c661b38398844d7d140627650f430f5b7b2bbee9c812b6a01e';
const MULTIBLOCK_MADNESS_2_100_PUBLICATION =
  '2bd0bebe754d3889d401083517249b722869b802b31adc208598fe3bb41e1109';
const MEATBALLCRAFT_0186_PUBLICATION =
  '04c674ab74eeeaea151c9b985191f09e2be42156a879bb0493e2e29f94f3d46a';
const MODULAR_MACHINERY_PREVIEW_CATEGORY = 'modularmachinery.preview';
const MULTIBLOCKED_PREVIEW_CATEGORY = 'multiblocked:multiblock_info';

function primaryOutputKey(recipe: Recipe): string | undefined {
  return recipe.out?.[0]?.[0]?.[0];
}

/**
 * A few active publications predate structured multiblock export. Lazy-load exact, source-pinned
 * machine definitions only after their preview category is opened; normal app startup and every
 * unrelated modpack remain unaffected.
 */
export async function applyLegacyRecipeStructures(
  recipes: Recipe[],
  publicationId: string,
  category: Category,
): Promise<Recipe[]> {
  if (recipes.every(recipe => recipe.structure !== undefined)) return recipes;

  let structures: Record<string, Recipe['structure']>;
  let lookup: (recipe: Recipe, index: number) => string | undefined;
  let label: string;
  let allowMissing = false;
  if (
    publicationId === MULTIBLOCK_MADNESS_323_PUBLICATION &&
    category.id === MODULAR_MACHINERY_PREVIEW_CATEGORY
  ) {
    ({default: structures} = await import('./legacyMultiblockMadness323Structures'));
    lookup = primaryOutputKey;
    label = 'Multiblock Madness 3.2.3';
  } else if (
    publicationId === MULTIBLOCK_MADNESS_2_100_PUBLICATION &&
    category.id === MULTIBLOCKED_PREVIEW_CATEGORY
  ) {
    ({default: structures} = await import('./legacyMm2Structures'));
    lookup = (_recipe, index) => String(index);
    label = 'Multiblock Madness 2 1.0.0';
    // The category also contains four built-in Multiblocked documentation displays rather than
    // pack controller previews, so only those intentional entries lack a structure snapshot.
    allowMissing = true;
  } else if (
    publicationId === MEATBALLCRAFT_0186_PUBLICATION &&
    category.id === MODULAR_MACHINERY_PREVIEW_CATEGORY
  ) {
    ({default: structures} = await import('./legacyMeatballCraft0186Structures'));
    lookup = (_recipe, index) => String(index);
    label = 'MeatballCraft 0.18.6';
  } else {
    return recipes;
  }

  return recipes.map((recipe, index) => {
    if (recipe.structure !== undefined) return recipe;
    const key = lookup(recipe, index);
    const structure = key === undefined ? undefined : structures[key];
    if (structure === undefined) {
      if (allowMissing) return recipe;
      throw new Error(
        `Legacy ${label} structure recipe ${index} has no pinned machine definition.`,
      );
    }
    return {
      ...recipe,
      structure: requireRecipeStructure(
        structure,
        `legacy ${label} structure recipe ${index}`,
      ),
    };
  });
}
