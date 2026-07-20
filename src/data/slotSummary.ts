import {inferIngredientTag} from './ingredientTags.ts';
import {normalizeIngredientAmount} from './ingredientQuantities.ts';
import type {SlotEntry} from '../types';

export interface SlotSummary {
  key: string;
  /** null means the exporter could not determine the quantity. */
  amount: number | null;
  /** Alternatives in this logical slot carry different exact quantities. */
  variableAmount: boolean;
  variants: number;
  alternatives: string[];
  tag?: string;
}

let warnedVariableAlternativeAmounts = false;

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
    const alternatives = [...new Set(slot.map(([entryKey]) => entryKey))];
    const tag = inferIngredientTag(slot);
    const logicalKey = tag ? `#${tag}` : key;
    const current = out.get(logicalKey) ?? {
      key,
      amount: amount == null ? null : 0,
      variableAmount: false,
      variants: alternatives.length,
      alternatives,
      tag,
    };
    current.variableAmount ||= variableAmount;
    if (current.variableAmount) {
      current.amount = null;
    } else if (current.amount != null) {
      current.amount = amount == null ? null : current.amount + amount;
    }
    current.variants = Math.max(current.variants, alternatives.length);
    current.alternatives = [...new Set([...current.alternatives, ...alternatives])];
    out.set(logicalKey, current);
  }
  return [...out.values()];
}
