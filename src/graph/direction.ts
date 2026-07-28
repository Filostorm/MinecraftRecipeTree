import type {Recipe} from '../types.ts';
import {
  inputSlotSummary,
  prerequisiteSummary,
  slotSummary,
} from '../data/slotSummary.ts';
import type {SlotSummary} from '../data/slotSummary.ts';

export type GraphDirection = 'inputs' | 'outputs';

export interface DirectedRecipeChild extends SlotSummary {
  nonConsumed: boolean;
  probabilityRole: 'consume' | 'produce';
}

export function recipeChildrenForDirection(
  recipe: Recipe,
  direction: GraphDirection,
): DirectedRecipeChild[] {
  if (direction === 'outputs') {
    return slotSummary(recipe.out).map(output => ({
      ...output,
      nonConsumed: false,
      probabilityRole: 'produce' as const,
    }));
  }
  return [
    ...inputSlotSummary(recipe.in).map(input => ({
      ...input,
      nonConsumed: false,
      probabilityRole: 'consume' as const,
    })),
    ...prerequisiteSummary(recipe.cat).map(input => ({
      ...input,
      nonConsumed: true,
      probabilityRole: 'consume' as const,
    })),
  ];
}

export function recipeUsesItem(recipe: Recipe, itemKey: string): boolean {
  return [...inputSlotSummary(recipe.in), ...prerequisiteSummary(recipe.cat)].some(
    input => input.key === itemKey || input.alternatives.includes(itemKey),
  );
}

export function recipeProducesItem(recipe: Recipe, itemKey: string): boolean {
  return slotSummary(recipe.out).some(
    output => output.key === itemKey || output.alternatives.includes(itemKey),
  );
}

export interface UsageGraphStart {
  rootKey: string;
  direction: 'inputs';
}

/**
 * Converts a selected usage into a new ingredient-tree root.
 *
 * Recipe output order is authoritative: later slots may be secondary products or byproducts.
 */
export function usageGraphStart(recipe: Recipe): UsageGraphStart | null {
  const primaryOutput = slotSummary(recipe.out)[0];
  return primaryOutput
    ? {rootKey: primaryOutput.key, direction: 'inputs'}
    : null;
}
