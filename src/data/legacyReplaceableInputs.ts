import type {CatalogItem, Category, Recipe, SlotEntry} from '../types.ts';

const ENGINEERS_WORKBENCH_CATEGORY = 'ie.workbench';
const GLASS_NAME = /\bglass\b/i;

function normalizedName(item: CatalogItem): string {
  return item.n.trim().replace(/\s+/g, ' ').toLocaleLowerCase();
}

function inputFamily(
  slot: SlotEntry[],
  itemsByKey: ReadonlyMap<string, CatalogItem>,
): string | null {
  const names: string[] = [];
  for (const [key] of slot) {
    const item = itemsByKey.get(key);
    if (!item) return null;
    names.push(normalizedName(item));
  }
  if (names.length === 0) return null;
  if (names.every(name => GLASS_NAME.test(name))) return 'glass';
  return names.every(name => name === names[0]) ? `name:${names[0]}` : null;
}

function slotSignature(slot: SlotEntry[]): string {
  return JSON.stringify(slot);
}

/**
 * Finds the smallest repeated sequence of raw slots.
 *
 * A run `steel-a, steel-b, steel-a, steel-b` represents two required steel
 * inputs, each accepting either implementation. A non-repeating run such as
 * `copper-a, copper-b, copper-c` represents one replaceable input.
 */
function repeatedPeriod(slots: SlotEntry[][]): number | null {
  const signatures = slots.map(slotSignature);
  for (let period = 1; period <= Math.floor(slots.length / 2); period += 1) {
    if (slots.length % period !== 0) continue;
    if (signatures.every((signature, index) => signature === signatures[index % period])) {
      return period;
    }
  }
  return null;
}

function mergeAlternativeSlots(slots: SlotEntry[][]): SlotEntry[] {
  const out: SlotEntry[] = [];
  const seen = new Set<string>();
  for (const slot of slots) {
    for (const entry of slot) {
      const signature = JSON.stringify(entry);
      if (seen.has(signature)) continue;
      seen.add(signature);
      out.push(entry);
    }
  }
  return out;
}

function reconstructRun(slots: SlotEntry[][]): SlotEntry[][] {
  if (slots.length < 2) return slots;
  const period = repeatedPeriod(slots);
  if (period == null) return [mergeAlternativeSlots(slots)];
  const alternatives = mergeAlternativeSlots(slots.slice(0, period));
  return Array.from({length: slots.length / period}, () => [...alternatives]);
}

/**
 * Repairs the legacy Forge 1.12 Engineer's Workbench JEI encoding.
 *
 * That handler exports each member of a replaceable IngredientStack as if it
 * were a simultaneously required input. The compact publication has no slot
 * coordinates, so reconstruction is deliberately restricted to this handler
 * and only merges adjacent entries whose catalog names prove one logical
 * family. Exact repeated sequences remain repeated required inputs.
 */
export function reconstructLegacyReplaceableInputs(
  recipe: Recipe,
  category: Category,
  minecraftVersion: string,
  itemsByKey: ReadonlyMap<string, CatalogItem>,
): Recipe {
  if (
    category.id !== ENGINEERS_WORKBENCH_CATEGORY ||
    !/^1\.12(?:\.|$)/.test(minecraftVersion) ||
    !recipe.in ||
    recipe.in.length < 2
  ) {
    return recipe;
  }

  const families = recipe.in.map(slot => inputFamily(slot, itemsByKey));
  const missingCatalogIndex = families.findIndex(
    (family, index) => family == null && recipe.in![index].some(([key]) => !itemsByKey.has(key)),
  );
  if (missingCatalogIndex >= 0) {
    console.error(
      "The legacy Engineer's Workbench replaceable-input decoder could not resolve an ingredient from the item catalog; the recipe was left unchanged.",
      {
        categoryId: category.id,
        outputKey: recipe.out?.[0]?.[0]?.[0],
        inputIndex: missingCatalogIndex,
        keys: recipe.in[missingCatalogIndex].map(([key]) => key),
      },
    );
    return recipe;
  }

  const reconstructed: SlotEntry[][] = [];
  let mergedRuns = 0;
  let mergedRawSlots = 0;
  for (let start = 0; start < recipe.in.length; ) {
    const family = families[start];
    let end = start + 1;
    if (family != null) {
      while (end < recipe.in.length && families[end] === family) end += 1;
    }
    const run = recipe.in.slice(start, end);
    const repaired = family == null ? run : reconstructRun(run);
    if (repaired.length < run.length) {
      mergedRuns += 1;
      mergedRawSlots += run.length - repaired.length;
    }
    reconstructed.push(...repaired);
    start = end;
  }

  if (mergedRuns === 0) return recipe;
  console.info(
    "Reconstructed replaceable inputs flattened by the legacy Engineer's Workbench JEI handler.",
    {
      categoryId: category.id,
      outputKey: recipe.out?.[0]?.[0]?.[0],
      originalInputSlots: recipe.in.length,
      reconstructedInputSlots: reconstructed.length,
      mergedRuns,
      mergedRawSlots,
    },
  );
  return {...recipe, in: reconstructed};
}
