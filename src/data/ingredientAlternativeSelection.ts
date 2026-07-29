import type {SlotSummary} from './slotSummary.ts';
import type {Recipe, SlotEntry} from '../types.ts';

export type IngredientSelections = Record<string, string>;

/**
 * Keeps the export's original first member as the stable slot identity while
 * allowing the displayed/expanded member to change.
 */
export function selectSlotAlternative(
  slot: SlotSummary,
  selectedKey: string,
): SlotSummary {
  if (!slot.alternatives.includes(selectedKey)) {
    throw new Error(
      `Ingredient alternative ${JSON.stringify(selectedKey)} is not a member of slot ${JSON.stringify(slot.key)}.`,
    );
  }
  return {
    ...slot,
    selectionKey: slot.selectionKey ?? slot.key,
    key: selectedKey,
  };
}

function reorderSelectedAlternative(
  slot: SlotEntry[],
  selections: IngredientSelections,
  matchedSelectionKeys: Set<string>,
): SlotEntry[] {
  const matchingSelections = Object.entries(selections).filter(([selectionKey]) =>
    slot.some(([key]) => key === selectionKey),
  );
  if (matchingSelections.length === 0) return slot;
  if (matchingSelections.length > 1) {
    throw new Error(
      `One recipe input slot matched multiple saved alternative selections: ${matchingSelections
        .map(([selectionKey]) => selectionKey)
        .join(', ')}.`,
    );
  }
  const [selectionKey, selectedKey] = matchingSelections[0];
  const selectedIndex = slot.findIndex(([key]) => key === selectedKey);
  if (selectedIndex < 0) {
    throw new Error(
      `Saved ingredient alternative ${JSON.stringify(selectedKey)} is not a member of recipe slot ${JSON.stringify(selectionKey)}.`,
    );
  }
  matchedSelectionKeys.add(selectionKey);
  if (selectedIndex <= 0) return slot;
  return [
    slot[selectedIndex],
    ...slot.slice(0, selectedIndex),
    ...slot.slice(selectedIndex + 1),
  ];
}

/** Applies per-slot choices without mutating the immutable exported recipe. */
export function applyIngredientSelections(
  recipe: Recipe,
  selections: IngredientSelections | undefined,
): Recipe {
  if (!selections || Object.keys(selections).length === 0) return recipe;
  const matchedSelectionKeys = new Set<string>();
  const inputs = recipe.in?.map(slot =>
    reorderSelectedAlternative(slot, selections, matchedSelectionKeys),
  );
  const unmatchedSelectionKeys = Object.keys(selections).filter(
    selectionKey => !matchedSelectionKeys.has(selectionKey),
  );
  if (unmatchedSelectionKeys.length > 0) {
    throw new Error(
      `Saved ingredient selection slots are not present in this recipe: ${unmatchedSelectionKeys.join(', ')}.`,
    );
  }
  return inputs?.some((slot, index) => slot !== recipe.in?.[index])
    ? {...recipe, in: inputs}
    : recipe;
}
