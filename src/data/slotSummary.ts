import {inferIngredientTag} from './ingredientTags.ts';
import {normalizeIngredientAmount} from './ingredientQuantities.ts';
import type {SlotEntry} from '../types';

export interface SlotSummary {
  key: string;
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

function normalizedEntryAmount([key, rawAmount]: SlotEntry, consumed: boolean): number | null {
  return consumed
    ? normalizeIngredientAmount(key, rawAmount)
    : Number.isFinite(rawAmount) && rawAmount > 0
      ? rawAmount
      : null;
}

function slotAmount(
  slot: SlotEntry[],
  consumed: boolean,
): {amount: number | null; variableAmount: boolean} {
  const amounts = slot.map(entry => normalizedEntryAmount(entry, consumed));
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

/** Logical consumed ingredients with amounts, preserving tag-resolved alternatives. */
export function slotSummary(slots: SlotEntry[][] | undefined): SlotSummary[] {
  return summarizeSlots(slots, true);
}

/** Prerequisite/catalyst slots are graph edges but never material consumption. */
export function prerequisiteSummary(slots: SlotEntry[][] | undefined): SlotSummary[] {
  return summarizeSlots(slots, false);
}

function summarizeSlots(
  slots: SlotEntry[][] | undefined,
  consumed: boolean,
): SlotSummary[] {
  const out = new Map<string, SlotSummary>();
  for (const slot of slots ?? []) {
    if (!slot.length) continue;
    const [key] = slot[0];
    const {amount, variableAmount} = slotAmount(slot, consumed);
    const probability = slotProbability(slot);
    const alternatives = [...new Set(slot.map(([entryKey]) => entryKey))];
    const tag = inferIngredientTag(slot);
    const logicalKey = tag ? `#${tag}` : key;
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
