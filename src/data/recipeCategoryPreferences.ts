const STORAGE_KEY = 'collapsedRecipeCategories:v1';
const STORAGE_VERSION = 1;
const MAX_COLLAPSED_CATEGORIES = 512;
const MAX_CATEGORY_ID_LENGTH = 240;

interface StoredCategoryPreferences {
  version: typeof STORAGE_VERSION;
  collapsed: string[];
}

let warnedUnavailableLoad = false;
let warnedUnavailableSave = false;

export function parseCollapsedRecipeCategories(raw: string): Set<string> {
  const parsed = JSON.parse(raw) as Partial<StoredCategoryPreferences>;
  if (
    !parsed ||
    typeof parsed !== 'object' ||
    Array.isArray(parsed) ||
    parsed.version !== STORAGE_VERSION ||
    !Array.isArray(parsed.collapsed) ||
    parsed.collapsed.length > MAX_COLLAPSED_CATEGORIES ||
    parsed.collapsed.some(
      id => typeof id !== 'string' || id.length === 0 || id.length > MAX_CATEGORY_ID_LENGTH,
    )
  ) {
    throw new Error('Collapsed recipe categories do not satisfy the storage contract.');
  }
  return new Set(parsed.collapsed);
}

export function loadCollapsedRecipeCategories(): Set<string> {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      if (!warnedUnavailableLoad) {
        warnedUnavailableLoad = true;
        console.warn('Recipe category collapse settings are unavailable because localStorage is not present.');
      }
      return new Set();
    }
    const raw = storage.getItem(STORAGE_KEY);
    return raw === null ? new Set() : parseCollapsedRecipeCategories(raw);
  } catch (error) {
    console.error('Recipe category collapse settings could not be loaded from localStorage.', error);
    return new Set();
  }
}

export function persistCollapsedRecipeCategories(collapsed: ReadonlySet<string>): void {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      if (!warnedUnavailableSave) {
        warnedUnavailableSave = true;
        console.warn('Recipe category collapse changes are not persistent because localStorage is unavailable.');
      }
      return;
    }
    const categoryIds = [...collapsed].sort();
    if (categoryIds.length > MAX_COLLAPSED_CATEGORIES) {
      console.error('Recipe category collapse settings exceeded the persistence limit.', {
        categoryCount: categoryIds.length,
        maximum: MAX_COLLAPSED_CATEGORIES,
      });
      return;
    }
    const stored: StoredCategoryPreferences = {
      version: STORAGE_VERSION,
      collapsed: categoryIds,
    };
    storage.setItem(STORAGE_KEY, JSON.stringify(stored));
  } catch (error) {
    console.error('Recipe category collapse settings could not be saved to localStorage.', error);
  }
}

export function toggleCollapsedRecipeCategory(
  collapsed: ReadonlySet<string>,
  categoryId: string,
): Set<string> {
  const next = new Set(collapsed);
  if (next.has(categoryId)) next.delete(categoryId);
  else next.add(categoryId);
  return next;
}

export function keepAtLeastOneRecipeCategoryExpanded(
  collapsed: ReadonlySet<string>,
  availableCategoryIds: readonly string[],
): ReadonlySet<string> {
  const firstAvailable = availableCategoryIds[0];
  if (
    firstAvailable === undefined ||
    availableCategoryIds.some(categoryId => !collapsed.has(categoryId))
  ) {
    return collapsed;
  }
  const next = new Set(collapsed);
  next.delete(firstAvailable);
  return next;
}
