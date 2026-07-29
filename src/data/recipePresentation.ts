import type {Recipe} from '../types';

export type RecipePresentationKind = 'failure' | 'image' | 'structured';

/** Keep export tombstones distinct from valid recipes whose optional JEI screenshot is absent. */
export function recipePresentationKind(
  recipe: Pick<Recipe, 'err' | 'img'>,
): RecipePresentationKind {
  if (recipe.err === true) return 'failure';
  return typeof recipe.img === 'string' && recipe.img.length > 0 ? 'image' : 'structured';
}
