import type {Recipe} from '../types';

export const PROJECTE_EMC_CATEGORY_ID = 'projecte:emc_transmutation';
export const PROJECTE_EMC_KEY = 'emc|projecte:emc';
export const PROJECTE_TRANSMUTATION_TABLE_KEY = 'item|projecte:transmutation_table';

/** EMC is a value rather than an item, so represent it with ProjectE's interaction surface. */
export function projecteEmcIconItemKey(itemKey: string): string {
  return itemKey === PROJECTE_EMC_KEY ? PROJECTE_TRANSMUTATION_TABLE_KEY : itemKey;
}

export interface ProjecteEmcTransmutation {
  emc: number;
  outputItemKey: string;
  outputAmount: number;
}

/** Parse the exact one-resource-to-one-item shape emitted by the ProjectE exporter. */
export function projecteEmcTransmutation(
  recipe: Recipe,
): ProjecteEmcTransmutation | null {
  if (!recipe.id?.startsWith('projecte:emc/')) return null;
  if (recipe.in?.length !== 1 || recipe.in[0]?.length !== 1) return null;
  if (recipe.out?.length !== 1 || recipe.out[0]?.length !== 1) return null;

  const [inputKey, emc] = recipe.in[0][0];
  const [outputItemKey, outputAmount] = recipe.out[0][0];
  if (
    inputKey !== PROJECTE_EMC_KEY ||
    !Number.isFinite(emc) ||
    emc <= 0 ||
    !outputItemKey ||
    !Number.isFinite(outputAmount) ||
    outputAmount <= 0
  ) {
    return null;
  }
  return {emc, outputItemKey, outputAmount};
}

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
