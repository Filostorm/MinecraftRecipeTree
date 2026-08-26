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
  retentionMode?: 'reusable' | 'durability';
  retentionUses?: number;
  probabilityRole: 'consume' | 'produce';
}

function retainedDetails(
  recipe: Recipe,
  input: SlotSummary,
): Pick<DirectedRecipeChild, 'retentionMode' | 'retentionUses'> {
  const details = [input.key, ...input.alternatives]
    .map(key => recipe.retained?.[key])
    .filter(detail => detail !== undefined);
  const durabilityUses = details
    .filter(detail => detail.mode === 'durability')
    .map(detail => detail.uses)
    .filter(uses => Number.isSafeInteger(uses) && uses > 0);
  if (durabilityUses.length > 0) {
    return {
      retentionMode: 'durability',
      // Alternative tools can have different durability. Use the least durable member so
      // an unresolved tag never understates how many tools the plan may require.
      retentionUses: Math.min(...durabilityUses),
    };
  }
  return {retentionMode: 'reusable'};
}

function isMekanismChemicalStack(key: string): boolean {
  const type = key.split('|', 1)[0];
  return (
    type.startsWith('mekanism/jei_plugin_jei_compat_') &&
    type.endsWith('stack')
  );
}

function isMekanismChemicalTank(key: string): boolean {
  return /^item\|mekanism:(?:basic|advanced|elite|ultimate|creative)_chemical_tank(?:\||$)/.test(
    key,
  );
}

/**
 * Material inputs exclude JEI presentation-only chemical carriers.
 *
 * Mekanism exports an explicit chemical stack plus a second slot describing
 * possible tank/raw-material sources for that same chemical. Counting both
 * duplicates the material flow. Tank-producing recipes retain their tank input.
 */
export function materialInputSummary(recipe: Recipe): SlotSummary[] {
  const inputSlots = recipe.in ?? [];
  const hasExplicitChemicalFlow = inputSlots.some(slot =>
    slot.some(([key]) => isMekanismChemicalStack(key)),
  );
  const producesChemicalTank = (recipe.out ?? []).some(slot =>
    slot.some(([key]) => isMekanismChemicalTank(key)),
  );
  const materialSlots =
    hasExplicitChemicalFlow && !producesChemicalTank
      ? inputSlots.filter(
          slot => !slot.some(([key]) => isMekanismChemicalTank(key)),
        )
      : inputSlots;
  return inputSlotSummary(materialSlots);
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
    ...materialInputSummary(recipe).map(input => ({
      ...input,
      nonConsumed: false,
      probabilityRole: 'consume' as const,
    })),
    ...prerequisiteSummary(recipe.cat).map(input => ({
      ...input,
      nonConsumed: true,
      ...retainedDetails(recipe, input),
      probabilityRole: 'consume' as const,
    })),
  ];
}

export function recipeUsesItem(recipe: Recipe, itemKey: string): boolean {
  return recipeChildrenForDirection(recipe, 'inputs').some(
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
