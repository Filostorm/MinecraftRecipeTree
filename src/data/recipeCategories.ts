import type {Category} from '../types';

export const AUTOMATED_SHAPED_CATEGORY_ID = 'create:automatic_shaped';
export const STANDARD_CRAFTING_CATEGORY_ID = 'minecraft:crafting';
export const GTNH_AE2_WORLD_CRAFTING_INFORMATION_CATEGORY_ID =
  'appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler';
export const GTNH_BETTERQUESTING_INFORMATION_CATEGORY_ID =
  'bq_standard.integration.nei.QuestRecipeHandler';

/**
 * HEI 4 on Minecraft 1.12 uses dotted UIDs; modern JEI uses namespaced UIDs.
 * Some 1.12 integrations identify the vanilla crafting table as a workbench.
 */
export function isStandardCraftingCategory(category: Category | undefined): boolean {
  return /^minecraft[.:](?:crafting|workbench)(?:[/.](?:crafting|workbench))?$/i.test(
    category?.id ?? '',
  );
}

/** JEI/HEI informational pages are indexes or descriptions, not executable recipes. */
export function isMetaRecipeCategory(category: Category | undefined): boolean {
  const id = category?.id ?? '';
  return (
    id === GTNH_AE2_WORLD_CRAFTING_INFORMATION_CATEGORY_ID ||
    id === GTNH_BETTERQUESTING_INFORMATION_CATEGORY_ID ||
    /(^|[.:])tag_recipes(?:[/.]|$)/i.test(id) ||
    /^jei[.:](?:information|description)(?:[/.]|$)/i.test(id)
  );
}

export function isRepairRecipeCategory(category: Category | undefined): boolean {
  return /(^|[.:])anvil(?:[/.]|$)/i.test(category?.id ?? '');
}

export function isSecondaryRecipeCategory(category: Category | undefined): boolean {
  const id = category?.id ?? '';
  return (
    /(^|[.:])(?:anvil|smithing)(?:[/.]|$)/i.test(id) ||
    /^jeiexport[.:]trading$/i.test(id)
  );
}

/** Categories hidden by default because they duplicate a more direct crafting source. */
export function isDefaultDisabledRecipeCategory(category: Category | undefined): boolean {
  return category?.id === AUTOMATED_SHAPED_CATEGORY_ID;
}

/** Standard crafting first, then the remaining JEI types alphabetically. */
export function compareRecipeCategories(a: Category, b: Category): number {
  if (isStandardCraftingCategory(a) && !isStandardCraftingCategory(b)) return -1;
  if (isStandardCraftingCategory(b) && !isStandardCraftingCategory(a)) return 1;
  return a.title.localeCompare(b.title) || a.id.localeCompare(b.id);
}
