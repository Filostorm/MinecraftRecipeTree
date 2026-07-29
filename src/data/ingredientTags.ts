import type {SlotEntry} from '../types';

/**
 * JEI exports tag ingredients as their resolved item variants, not as the
 * original tag id. Only reconstruct names for families whose membership is
 * unambiguous from every variant; unknown groups stay explicit.
 */
interface KnownItemTag {
  tag: string;
  matches: (path: string) => boolean;
  confirms?: (paths: string[]) => boolean;
}

const confirmsVanillaWoodFamily = (paths: string[]) =>
  paths.some(path => path.startsWith('oak_')) && paths.some(path => path.startsWith('spruce_'));

const KNOWN_ITEM_TAGS: KnownItemTag[] = [
  {tag: 'minecraft:planks', matches: path => path.endsWith('_planks')},
  {tag: 'minecraft:wooden_slabs', matches: path => path.endsWith('_slab'), confirms: confirmsVanillaWoodFamily},
  {tag: 'minecraft:wooden_stairs', matches: path => path.endsWith('_stairs'), confirms: confirmsVanillaWoodFamily},
  {tag: 'minecraft:wooden_fences', matches: path => path.endsWith('_fence'), confirms: confirmsVanillaWoodFamily},
  {tag: 'minecraft:wooden_doors', matches: path => path.endsWith('_door'), confirms: confirmsVanillaWoodFamily},
  {tag: 'minecraft:wooden_trapdoors', matches: path => path.endsWith('_trapdoor'), confirms: confirmsVanillaWoodFamily},
  {tag: 'minecraft:wooden_buttons', matches: path => path.endsWith('_button'), confirms: confirmsVanillaWoodFamily},
  {
    tag: 'minecraft:wooden_pressure_plates',
    matches: path => path.endsWith('_pressure_plate'),
    confirms: confirmsVanillaWoodFamily,
  },
  {tag: 'minecraft:leaves', matches: path => path.endsWith('_leaves')},
  {tag: 'minecraft:saplings', matches: path => path.endsWith('_sapling')},
  {tag: 'minecraft:wool', matches: path => path.endsWith('_wool')},
  {
    tag: 'minecraft:wool_carpets',
    matches: path => path.endsWith('_carpet'),
    confirms: paths => paths.includes('white_carpet') && paths.includes('black_carpet'),
  },
];

function itemPath(key: string): string | null {
  if (!key.startsWith('item|')) return null;
  const id = key.slice('item|'.length);
  const separator = id.indexOf(':');
  return separator >= 0 ? id.slice(separator + 1) : null;
}

export function inferIngredientTag(slot: SlotEntry[]): string | undefined {
  const explicitIdentities = [
    ...new Set(
      slot
        .map(([, , identity]) => identity)
        .filter((identity): identity is string => typeof identity === 'string'),
    ),
  ];
  if (explicitIdentities.length === 1) return explicitIdentities[0];
  if (explicitIdentities.length > 1) {
    console.error('Ingredient slot contains conflicting explicit logical identities.', {
      identities: explicitIdentities,
      slot,
    });
    return undefined;
  }

  const uniqueKeys = [...new Set(slot.map(([key]) => key))];
  if (uniqueKeys.length < 2) return undefined;
  const paths = uniqueKeys.map(itemPath);
  if (paths.some(path => path == null)) return undefined;
  const itemPaths = paths as string[];

  const woodFamilies = itemPaths.map(path => {
    const match = path.match(/^(?:stripped_)?(.+?)_(?:log|wood|stem|hyphae)$/);
    return match?.[1];
  });
  if (woodFamilies.every((family): family is string => Boolean(family))) {
    const families = [...new Set(woodFamilies)];
    if (families.length > 1) return 'minecraft:logs';
    const family = families[0];
    return `minecraft:${family}_${family === 'crimson' || family === 'warped' ? 'stems' : 'logs'}`;
  }

  return KNOWN_ITEM_TAGS.find(
    candidate =>
      itemPaths.every(path => candidate.matches(path)) &&
      (candidate.confirms?.(itemPaths) ?? true),
  )?.tag;
}

/**
 * Preserve logical ingredient identities for modern tag-based packs, while
 * presenting Forge 1.12 OreDictionary ingredients as the concrete catalog item
 * the user selected. The logical `ore:*` identity remains attached to the graph
 * and totals for aggregation, alternative selection, and CSV provenance.
 */
export function displayIngredientName(
  itemName: string,
  tag?: string,
  minecraftVersion?: string,
): string {
  if (!tag) return itemName;
  if (/^1\.12(?:\.|$)/.test(minecraftVersion ?? '') && tag.startsWith('ore:')) {
    return itemName;
  }
  return `#${tag}`;
}
