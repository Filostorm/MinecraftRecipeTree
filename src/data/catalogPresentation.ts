import type {CatalogItem} from '../types';

const CUSTOM_TYPE_PREFIX = 'custom_';
const EXPORTER_HASH_SUFFIX = /_[0-9a-f]{8}$/i;
const SYNTHETIC_MULTIBLOCK_TYPE = 'genericmultiblockingredient';
const SYNTHETIC_ENDER_IO_ENERGY_TYPE =
  'crazypants.enderio.base.integration.jei.energy.energyingredient';

const CUSTOM_TYPE_LABELS: ReadonlyArray<readonly [needle: string, label: string]> = [
  ['energyingredient', 'Energy'],
  ['hybridfluid', 'Fluid'],
  ['particlestack', 'Particle'],
  ['gasstack', 'Gas'],
  ['dimensioningredient', 'Dimension'],
  ['aspectlist', 'Aspect'],
  ['meklaser', 'Laser energy'],
  ['mysticalmechanics', 'Mechanical power'],
  ['embers', 'Embers'],
  ['mana', 'Mana'],
  ['energy', 'Energy'],
];

function normalizedCustomType(type: string): string {
  return type.toLocaleLowerCase().replace(EXPORTER_HASH_SUFFIX, '');
}

/**
 * Synthetic JEI render models are useful inside recipe presentations but are not
 * independently obtainable inventory entries, so they do not belong in the item browser.
 */
export function isItemCatalogEligible(item: CatalogItem): boolean {
  const normalizedType = normalizedCustomType(item.t ?? '');
  const isSyntheticEnderIoEnergy =
    item.m === 'enderio' &&
    item.id === 'enderio:energy' &&
    normalizedType.includes(SYNTHETIC_ENDER_IO_ENERGY_TYPE);
  return (
    !normalizedType.includes(SYNTHETIC_MULTIBLOCK_TYPE) &&
    !isSyntheticEnderIoEnergy
  );
}

export interface CatalogTypePresentation {
  label: string;
  recognized: boolean;
}

/** Converts exporter implementation types into short, user-facing ingredient categories. */
export function catalogTypePresentation(type?: string): CatalogTypePresentation | null {
  if (!type) return null;
  if (type === 'fluid') return {label: 'Fluid', recognized: true};
  if (type === 'enchant') return {label: 'Enchantment', recognized: true};

  const normalized = normalizedCustomType(type);
  if (normalized.startsWith(CUSTOM_TYPE_PREFIX)) {
    for (const [needle, label] of CUSTOM_TYPE_LABELS) {
      if (normalized.includes(needle)) return {label, recognized: true};
    }
    return {label: 'Custom ingredient', recognized: false};
  }

  return {label: type.replaceAll('_', ' '), recognized: false};
}
