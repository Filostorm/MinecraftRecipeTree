import type {CatalogItem, Category, Recipe, SlotEntry} from '../types';

const CONTAINER_TOKEN =
  /(?:^|[\s_:.\-/])(bucket|bottle|bowl|can|cell|capsule|container|flask|jar|tank|reservoir|drum|canteen|vial|feeder|sponge)(?:$|[\s_:.\-/])/i;

function entryType(
  key: string,
  itemsByKey?: ReadonlyMap<string, CatalogItem>,
): string {
  const separator = key.indexOf('|');
  const keyType = separator < 0 ? 'unknown' : key.slice(0, separator);
  const catalogType = itemsByKey?.get(key)?.t ?? keyType;
  const normalized = catalogType.toLocaleLowerCase();
  if (normalized === 'fluid' || normalized.includes('hybridfluid')) return 'fluid';
  return keyType;
}

function slotType(
  slot: SlotEntry[],
  itemsByKey?: ReadonlyMap<string, CatalogItem>,
): string | null {
  const types = new Set(
    slot.map(([key]) => entryType(key, itemsByKey)),
  );
  return types.size === 1 ? [...types][0] : null;
}

function partitionSlots(
  slots: SlotEntry[][] | undefined,
  itemsByKey?: ReadonlyMap<string, CatalogItem>,
) {
  const byType = new Map<string, SlotEntry[][]>();
  for (const slot of slots ?? []) {
    if (slot.length === 0) continue;
    const type = slotType(slot, itemsByKey);
    if (!type) return null;
    const entries = byType.get(type) ?? [];
    entries.push(slot);
    byType.set(type, entries);
  }
  return byType;
}

function itemIdentityText(
  slot: SlotEntry[],
  itemsByKey?: ReadonlyMap<string, CatalogItem>,
): string[] {
  const text = new Set<string>();
  for (const [key] of slot) {
    text.add(key);
    const item = itemsByKey?.get(key);
    if (item?.id) text.add(item.id);
    if (item?.n) text.add(item.n);
  }
  return [...text];
}

function looksLikeContainerTransition(
  input: SlotEntry[],
  output: SlotEntry[],
  itemsByKey?: ReadonlyMap<string, CatalogItem>,
): boolean {
  const inputItems = input
    .map(([key]) => itemsByKey?.get(key))
    .filter((item): item is CatalogItem => !!item);
  const outputItems = output
    .map(([key]) => itemsByKey?.get(key))
    .filter((item): item is CatalogItem => !!item);
  const sameRegistryItem = inputItems.some(inputItem =>
    outputItems.some(outputItem => outputItem.id === inputItem.id),
  );
  const identityText = [
    ...itemIdentityText(input, itemsByKey),
    ...itemIdentityText(output, itemsByKey),
  ];
  const hasContainerMarker = identityText.some(value => CONTAINER_TOKEN.test(value));
  if (sameRegistryItem && hasContainerMarker) return true;

  // Vanilla changes registry items when its built-in containers change state.
  const registryIds = new Set(
    [...inputItems, ...outputItems].map(item => item.id.toLocaleLowerCase()),
  );
  const hasPair = (empty: string, filled: readonly string[]) =>
    registryIds.has(empty) && filled.some(id => registryIds.has(id));
  return (
    hasPair('minecraft:bucket', [
      'minecraft:water_bucket',
      'minecraft:lava_bucket',
      'minecraft:milk_bucket',
      'minecraft:powder_snow_bucket',
    ]) ||
    hasPair('minecraft:glass_bottle', [
      'minecraft:potion',
      'minecraft:splash_potion',
      'minecraft:lingering_potion',
      'minecraft:honey_bottle',
      'minecraft:dragon_breath',
    ]) ||
    hasPair('minecraft:bowl', [
      'minecraft:mushroom_stew',
      'minecraft:rabbit_stew',
      'minecraft:beetroot_soup',
      'minecraft:suspicious_stew',
    ])
  );
}

/**
 * Identify reversible item/fluid container-state conversions without hiding
 * legitimate machine processing such as fluid infusion or juice extraction.
 *
 * Fill:    container item + one fluid -> changed container item
 * Empty:   changed container item -> container item + one fluid
 *
 * The item transition must also use an explicit container marker. Shape alone
 * is intentionally insufficient because machines such as Fluid Transposers
 * also perform real transformations.
 */
export function isFluidContainerTransferRecipe(
  recipe: Recipe | undefined,
  itemsByKey?: ReadonlyMap<string, CatalogItem>,
  category?: Pick<Category, 'id' | 'title'>,
): boolean {
  if (!recipe || recipe.err) return false;
  if (
    category?.id === 'nuclearcraft_extractor' ||
    category?.title.trim().toLocaleLowerCase() === 'fluid extractor'
  ) {
    return false;
  }
  const inputs = partitionSlots(recipe.in, itemsByKey);
  const outputs = partitionSlots(recipe.out, itemsByKey);
  if (!inputs || !outputs) return false;

  const inputItems = inputs.get('item') ?? [];
  const outputItems = outputs.get('item') ?? [];
  const inputFluids = inputs.get('fluid') ?? [];
  const outputFluids = outputs.get('fluid') ?? [];
  const inputCount = [...inputs.values()].reduce((sum, slots) => sum + slots.length, 0);
  const outputCount = [...outputs.values()].reduce((sum, slots) => sum + slots.length, 0);

  const filling =
    inputCount === 2 &&
    outputCount === 1 &&
    inputItems.length === 1 &&
    inputFluids.length === 1 &&
    outputItems.length === 1;
  const emptying =
    inputCount === 1 &&
    outputCount === 2 &&
    inputItems.length === 1 &&
    outputItems.length === 1 &&
    outputFluids.length === 1;
  if (!filling && !emptying) return false;

  return looksLikeContainerTransition(inputItems[0], outputItems[0], itemsByKey);
}
