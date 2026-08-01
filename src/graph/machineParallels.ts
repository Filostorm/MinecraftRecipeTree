import {slotSummary} from '../data/slotSummary.ts';
import type {Recipe} from '../types.ts';

export interface ProductionPlan {
  amount: number;
  /** Retained for saved-graph compatibility; parallel suggestions no longer use a deadline. */
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

export const MINECRAFT_TICKS_PER_SECOND = 20;

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
    return recipe.durationTicks / MINECRAFT_TICKS_PER_SECOND;
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

export function parallelMachinesForOneCycle(
  requestedAmount: number,
  outputPerCycle: number,
): number | null {
  if (
    !Number.isFinite(requestedAmount) ||
    requestedAmount <= 0 ||
    !Number.isFinite(outputPerCycle) ||
    outputPerCycle <= 0
  ) {
    return null;
  }
  return Math.max(1, Math.ceil(requestedAmount / outputPerCycle));
}

/** Suggests enough parallel machines to complete the requested amount in one recipe cycle. */
export function estimateParallelMachines(
  recipe: Recipe,
  itemKey: string,
  categoryId: string | undefined,
  plan: ProductionPlan,
): ParallelMachineEstimate | null {
  if (
    !Number.isFinite(plan.amount) ||
    plan.amount <= 0
  ) {
    return null;
  }
  const outputPerCycle = selectedRecipeOutput(recipe, itemKey);
  const cycleSeconds = recipeCycleSeconds(recipe, categoryId, plan.cycleSeconds);
  if (outputPerCycle == null || cycleSeconds == null) return null;
  const cyclesRequired = parallelMachinesForOneCycle(plan.amount, outputPerCycle);
  if (cyclesRequired == null) return null;
  return {
    machines: Math.max(1, cyclesRequired),
    cyclesRequired,
    cyclesPerMachine: 1,
    outputPerCycle,
    cycleSeconds,
  };
}
