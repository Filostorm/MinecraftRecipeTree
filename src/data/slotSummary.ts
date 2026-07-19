import {inferIngredientTag} from './ingredientTags.ts';
import {normalizeIngredientAmount} from './ingredientQuantities.ts';
import type {SlotEntry} from '../types';

export interface SlotSummary {
  key: string;
  /** null means the exporter could not determine the quantity. */
  amount: number | null;
  variants: number;
  alternatives: string[];
  tag?: string;
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
    const [key, rawAmount] = slot[0];
    const amount = consumed
      ? normalizeIngredientAmount(key, rawAmount)
      : Number.isFinite(rawAmount) && rawAmount > 0
        ? rawAmount
        : null;
    const alternatives = [...new Set(slot.map(([entryKey]) => entryKey))];
    const tag = inferIngredientTag(slot);
    const logicalKey = tag ? `#${tag}` : key;
    const current = out.get(logicalKey) ?? {
      key,
      amount: amount == null ? null : 0,
      variants: alternatives.length,
      alternatives,
      tag,
    };
    if (current.amount != null) {
      current.amount = amount == null ? null : current.amount + amount;
    }
    current.variants = Math.max(current.variants, alternatives.length);
    current.alternatives = [...new Set([...current.alternatives, ...alternatives])];
    out.set(logicalKey, current);
  }
  return [...out.values()];
}
