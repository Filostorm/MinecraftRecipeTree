import type {Recipe} from '../types';

export type RecipePresentationKind = 'failure' | 'image' | 'structured';

/** Keep export tombstones distinct from valid recipes whose optional JEI screenshot is absent. */
export function recipePresentationKind(
  recipe: Pick<Recipe, 'err' | 'img'>,
): RecipePresentationKind {
  if (recipe.err === true) return 'failure';
  return typeof recipe.img === 'string' && recipe.img.length > 0 ? 'image' : 'structured';
}

/** Multiblocks have their own placed-block preview and exact material list. */
export function recipeHasStructurePreview(
  recipe: Pick<Recipe, 'structure'>,
): boolean {
  return recipe.structure !== undefined;
}

/** A structure export is a complete preview even when it has no legacy JEI screenshot. */
export function recipeNeedsLayoutPreviewUnavailableNotice(
  recipe: Pick<Recipe, 'img' | 'structure'>,
): boolean {
  return !recipe.img && !recipeHasStructurePreview(recipe);
}
