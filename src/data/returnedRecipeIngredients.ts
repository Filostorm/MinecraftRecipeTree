import type {Recipe, SlotEntry} from '../types.ts';

function entrySignature(entry: SlotEntry): string | null {
  if (entry.length >= 4) return null;
  const [key, amount] = entry;
  return JSON.stringify([key, amount]);
}

function slotSignature(slot: SlotEntry[]): string | null {
  const entries = slot.map(entrySignature);
  if (entries.some(entry => entry == null)) return null;
  return JSON.stringify((entries as string[]).sort());
}

function includesEquivalentSlot(slots: SlotEntry[][], expected: SlotEntry[]): boolean {
  const expectedSignature = slotSignature(expected);
  return (
    expectedSignature != null &&
    slots.some(slot => slotSignature(slot) === expectedSignature)
  );
}

/**
 * Treat an ingredient returned unchanged by a recipe as a retained prerequisite.
 *
 * Exact slot equality is intentional: an input of one item and an output of two
 * remains an ordinary productive recipe, while a returned mold/bucket/tool is
 * required once regardless of how many recipe runs the tree requests.
 */
export function promoteReturnedRecipeIngredients(recipe: Recipe): Recipe {
  if (!recipe.in?.length || !recipe.out?.length) return recipe;

  const inputs = [...recipe.in];
  const outputs = [...recipe.out];
  const catalysts = [...(recipe.cat ?? [])];
  let promoted = 0;

  for (let inputIndex = inputs.length - 1; inputIndex >= 0; inputIndex -= 1) {
    const input = inputs[inputIndex];
    const signature = slotSignature(input);
    if (signature == null) continue;
    const outputIndex = outputs.findIndex(output => slotSignature(output) === signature);
    if (outputIndex < 0) continue;

    inputs.splice(inputIndex, 1);
    outputs.splice(outputIndex, 1);
    if (!includesEquivalentSlot(catalysts, input)) catalysts.push(input);
    promoted += 1;
  }

  if (promoted === 0) return recipe;
  return {...recipe, in: inputs, out: outputs, cat: catalysts};
}
