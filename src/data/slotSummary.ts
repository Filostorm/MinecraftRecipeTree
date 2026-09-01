import {inferIngredientTag} from './ingredientTags.ts';
import {
  normalizeIngredientAmount,
  normalizeRecipeInputAmount,
} from './ingredientQuantities.ts';
import type {SlotEntry} from '../types';

export interface SlotSummary {
  key: string;
  /** Original first member used as the stable identity after a user selects another variant. */
  selectionKey?: string;
  /** null means the exporter could not determine the quantity. */
  amount: number | null;
  /** Undefined is deterministic; null means conflicting stochastic probabilities. */
  probability?: number | null;
  /** Alternatives in this logical slot carry different exact quantities. */
  variableAmount: boolean;
  variants: number;
  alternatives: string[];
  tag?: string;
}

let warnedVariableAlternativeAmounts = false;
let warnedVariableAlternativeProbabilities = false;
let warnedMergedStochasticSlots = false;
let reportedEnderIoEnergyInput = false;

type SlotSummaryMode = 'standard' | 'input' | 'prerequisite';

function isEnderIoEnergyPseudoInput(key: string): boolean {
  return (
    key.startsWith(
      'custom_crazypants.enderio.base.integration.jei.energy.energyingredient_',
    ) && key.endsWith('|enderio:energy')
  );
}

function normalizedEntryAmount(
  [key, rawAmount]: SlotEntry,
  mode: SlotSummaryMode,
): number | null {
  return mode === 'standard'
    ? normalizeIngredientAmount(key, rawAmount)
    : normalizeRecipeInputAmount(key, rawAmount);
}

function slotAmount(
  slot: SlotEntry[],
  mode: SlotSummaryMode,
): {amount: number | null; variableAmount: boolean} {
  const amounts = slot.map(entry => normalizedEntryAmount(entry, mode));
  const amount = amounts[0] ?? null;
  const variableAmount = amounts.some(candidate => candidate !== amount);
  if (variableAmount && !warnedVariableAlternativeAmounts) {
    warnedVariableAlternativeAmounts = true;
    console.warn(
      'Ingredient alternatives have different quantities; the aggregate quantity is intentionally unknown.',
      {
        exampleKeys: slot.map(([key]) => key),
        exportedAmounts: slot.map(([, rawAmount]) => rawAmount),
      },
    );
  }
  return {amount: variableAmount ? null : amount, variableAmount};
}

function slotProbability(slot: SlotEntry[]): number | null | undefined {
  const probabilities = slot.map(entry => entry[3]);
  const probability = probabilities[0];
  const variableProbability = probabilities.some(candidate => candidate !== probability);
  if (variableProbability && !warnedVariableAlternativeProbabilities) {
    warnedVariableAlternativeProbabilities = true;
    console.error(
      'Ingredient alternatives have conflicting occurrence probabilities; the aggregate chance is intentionally unknown.',
      {
        exampleKeys: slot.map(([key]) => key),
        exportedProbabilities: probabilities,
      },
    );
  }
  return variableProbability ? null : probability;
}

/** Generic recipe slots with exact exported quantities. */
export function slotSummary(slots: SlotEntry[][] | undefined): SlotSummary[] {
  return summarizeSlots(slots, 'standard');
}

/** Consumed inputs with discrete items rounded up and JEI pseudo-resources removed. */
export function inputSlotSummary(slots: SlotEntry[][] | undefined): SlotSummary[] {
  return summarizeSlots(slots, 'input');
}

/** Prerequisite/catalyst slots are graph edges but never material consumption. */
export function prerequisiteSummary(slots: SlotEntry[][] | undefined): SlotSummary[] {
  return summarizeSlots(slots, 'prerequisite');
}

function summarizeSlots(
  slots: SlotEntry[][] | undefined,
  mode: SlotSummaryMode,
): SlotSummary[] {
  const out = new Map<string, SlotSummary>();
  for (const exportedSlot of slots ?? []) {
    const slot =
      mode === 'standard'
        ? exportedSlot
        : exportedSlot.filter(([key]) => !isEnderIoEnergyPseudoInput(key));
    if (slot.length !== exportedSlot.length && !reportedEnderIoEnergyInput) {
      reportedEnderIoEnergyInput = true;
      console.info(
        'An Ender IO JEI energy pseudo-input was excluded from recipe material inputs.',
        {exampleKey: exportedSlot.find(([key]) => isEnderIoEnergyPseudoInput(key))?.[0]},
      );
    }
    if (!slot.length) continue;
    const [key] = slot[0];
    const {amount, variableAmount} = slotAmount(slot, mode);
    const probability = slotProbability(slot);
    const alternatives = [...new Set(slot.map(([entryKey]) => entryKey))];
    const tag = inferIngredientTag(slot);
    // A singleton is an exact positional ingredient even when the exporter records an
    // OreDictionary identity that the item belongs to. Only a slot with multiple concrete
    // candidates proves that the logical identity is replaceable. Grouping singleton slots by
    // their shared tag turns shaped recipes such as Avaritia's Ultimate Stew into dozens of one
    // arbitrarily selected food, hiding the exact ingredients shown in the recipe grid.
    const logicalKey = tag && alternatives.length > 1 ? `#${tag}` : key;
    const existing = out.get(logicalKey);
    const current =
      existing ??
      {
        key,
        amount: amount == null ? null : 0,
        ...(probability === undefined ? {} : {probability}),
        variableAmount: false,
        variants: alternatives.length,
        alternatives,
        tag,
      };
    const mergedStochasticSlot =
      existing !== undefined &&
      (existing.probability !== undefined || probability !== undefined);
    if (mergedStochasticSlot) {
      current.amount = null;
      current.probability = null;
      if (!warnedMergedStochasticSlots) {
        warnedMergedStochasticSlots = true;
        console.error(
          'Multiple stochastic slots share one logical identity; aggregate quantity and probability are intentionally unknown.',
          {logicalIngredient: logicalKey},
        );
      }
    }
    current.variableAmount ||= variableAmount;
    if (current.variableAmount) {
      current.amount = null;
    } else if (!mergedStochasticSlot && current.amount != null) {
      current.amount = amount == null ? null : current.amount + amount;
    }
    current.variants = Math.max(current.variants, alternatives.length);
    current.alternatives = [...new Set([...current.alternatives, ...alternatives])];
    out.set(logicalKey, current);
  }
  return [...out.values()];
}
