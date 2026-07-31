import {slotSummary} from '../data/slotSummary.ts';
import type {Recipe} from '../types.ts';

export interface ProductionPlan {
  amount: number;
  windowSeconds: number;
  /** User override for exports/categories that do not carry an exact duration. */
  cycleSeconds?: number;
}

export interface ParallelMachineEstimate {
  machines: number;
  cyclesRequired: number;
  cyclesPerMachine: number;
  outputPerCycle: number;
  cycleSeconds: number;
}

/** Exact built-in cooking defaults used only when the export predates duration metadata. */
export function defaultRecipeCycleSeconds(categoryId?: string): number | null {
  switch (categoryId) {
    case 'minecraft:smelting':
      return 10;
    case 'minecraft:blasting':
    case 'minecraft:smoking':
      return 5;
    case 'minecraft:campfire_cooking':
      return 30;
    default:
      return null;
  }
}

export function recipeCycleSeconds(
  recipe: Recipe,
  categoryId?: string,
  overrideSeconds?: number,
): number | null {
  if (overrideSeconds !== undefined) {
    return Number.isFinite(overrideSeconds) && overrideSeconds > 0
      ? overrideSeconds
      : null;
  }
  if (
    recipe.durationTicks !== undefined &&
    Number.isFinite(recipe.durationTicks) &&
    recipe.durationTicks > 0
  ) {
    return recipe.durationTicks / 20;
  }
  return defaultRecipeCycleSeconds(categoryId);
}

export function selectedRecipeOutput(
  recipe: Recipe,
  itemKey: string,
): number | null {
  const selected = slotSummary(recipe.out).find(
    output => output.key === itemKey || output.alternatives.includes(itemKey),
  );
  if (!selected || selected.amount == null || selected.amount <= 0) return null;
  if (selected.probability !== undefined) return null;
  return selected.amount;
}

/** Counts discrete recipe cycles that complete inside the requested time window. */
export function estimateParallelMachines(
  recipe: Recipe,
  itemKey: string,
  categoryId: string | undefined,
  plan: ProductionPlan,
): ParallelMachineEstimate | null {
  if (
    !Number.isFinite(plan.amount) ||
    plan.amount <= 0 ||
    !Number.isFinite(plan.windowSeconds) ||
    plan.windowSeconds <= 0
  ) {
    return null;
  }
  const outputPerCycle = selectedRecipeOutput(recipe, itemKey);
  const cycleSeconds = recipeCycleSeconds(recipe, categoryId, plan.cycleSeconds);
  if (outputPerCycle == null || cycleSeconds == null) return null;
  const cyclesPerMachine = Math.floor(plan.windowSeconds / cycleSeconds);
  if (cyclesPerMachine < 1) return null;
  const cyclesRequired = Math.ceil(plan.amount / outputPerCycle);
  return {
    machines: Math.max(1, Math.ceil(cyclesRequired / cyclesPerMachine)),
    cyclesRequired,
    cyclesPerMachine,
    outputPerCycle,
    cycleSeconds,
  };
}
