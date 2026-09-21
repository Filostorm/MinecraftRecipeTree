import type {CatalogItem} from '../types';

/** A recognizable first tree, never a replacement for the user's saved tree. */
export function starterItem(items: readonly CatalogItem[]): CatalogItem | undefined {
  return items.find(item => item.id === 'minecraft:crafting_table')
    ?? items.find(item => item.id === 'minecraft:workbench')
    ?? items.find(item => item.id === 'minecraft:furnace');
}
