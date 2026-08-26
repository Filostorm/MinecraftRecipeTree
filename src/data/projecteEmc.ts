import type {Recipe} from '../types';

export const PROJECTE_EMC_CATEGORY_ID = 'projecte:emc_transmutation';
export const PROJECTE_EMC_KEY = 'emc|projecte:emc';

/** Read the exact ProjectE value from one synthetic EMC source recipe. */
export function projecteEmcValue(recipe: Recipe, outputItemKey: string): number | null {
  const producesItem = (recipe.out ?? []).some(slot =>
    slot.some(([key, amount]) => key === outputItemKey && amount > 0),
  );
  if (!producesItem) return null;

  for (const slot of recipe.in ?? []) {
    for (const [key, amount] of slot) {
      if (key === PROJECTE_EMC_KEY && Number.isFinite(amount) && amount > 0) {
        return amount;
      }
    }
  }
  return null;
}
