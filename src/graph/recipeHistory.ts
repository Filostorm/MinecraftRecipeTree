import type {DatasetDescriptor} from '../data/datasetCatalog';
import type {RecipeRef} from '../types';

const RECIPE_HISTORY_VERSION = 1;
export const MAX_RECIPE_HISTORY_ENTRIES = 50;

export interface RecipeHistoryEntry {
  itemKey: string;
  ref: RecipeRef;
  title: string;
  recipeId: string | null;
  openedAt: number;
}

interface StoredRecipeHistory {
  version: typeof RECIPE_HISTORY_VERSION;
  entries: RecipeHistoryEntry[];
}

let warnedUnavailableLoad = false;
let warnedUnavailableSave = false;

function isBoundedString(value: unknown, maxLength: number): value is string {
  return typeof value === 'string' && value.length > 0 && value.length <= maxLength;
}

function requireHistoryEntry(value: unknown, index: number): RecipeHistoryEntry {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`Recipe history entry ${index} is not an object.`);
  }
  const entry = value as Partial<RecipeHistoryEntry>;
  const ref = entry.ref;
  const openedAt = entry.openedAt;
  if (
    !isBoundedString(entry.itemKey, 512) ||
    !Array.isArray(ref) ||
    ref.length !== 2 ||
    !Number.isSafeInteger(ref[0]) ||
    ref[0] < 0 ||
    !Number.isSafeInteger(ref[1]) ||
    ref[1] < 0 ||
    !isBoundedString(entry.title, 240) ||
    (entry.recipeId !== null && entry.recipeId !== undefined && !isBoundedString(entry.recipeId, 512)) ||
    typeof openedAt !== 'number' ||
    !Number.isSafeInteger(openedAt) ||
    openedAt <= 0
  ) {
    throw new Error(`Recipe history entry ${index} does not satisfy the storage contract.`);
  }
  return {
    itemKey: entry.itemKey,
    ref: [ref[0], ref[1]],
    title: entry.title,
    recipeId: entry.recipeId ?? null,
    openedAt,
  };
}

export function parseRecipeHistory(raw: string): RecipeHistoryEntry[] {
  const parsed = JSON.parse(raw) as Partial<StoredRecipeHistory>;
  if (
    !parsed ||
    typeof parsed !== 'object' ||
    Array.isArray(parsed) ||
    parsed.version !== RECIPE_HISTORY_VERSION ||
    !Array.isArray(parsed.entries) ||
    parsed.entries.length > MAX_RECIPE_HISTORY_ENTRIES
  ) {
    throw new Error('Recipe history does not satisfy the versioned storage contract.');
  }
  return parsed.entries.map(requireHistoryEntry);
}

export function recipeHistoryStorageKey(
  descriptor: Pick<DatasetDescriptor, 'slug' | 'publicationId'>,
): string {
  return `recipeHistory:v${RECIPE_HISTORY_VERSION}:${descriptor.slug}:${descriptor.publicationId}`;
}

export function mergeRecipeHistory(
  entries: readonly RecipeHistoryEntry[],
  next: RecipeHistoryEntry,
): RecipeHistoryEntry[] {
  const duplicate = (entry: RecipeHistoryEntry) =>
    entry.itemKey === next.itemKey &&
    entry.ref[0] === next.ref[0] &&
    entry.ref[1] === next.ref[1];
  return [next, ...entries.filter(entry => !duplicate(entry))].slice(
    0,
    MAX_RECIPE_HISTORY_ENTRIES,
  );
}

export function loadRecipeHistory(
  descriptor: Pick<DatasetDescriptor, 'slug' | 'publicationId'>,
): RecipeHistoryEntry[] {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      if (!warnedUnavailableLoad) {
        warnedUnavailableLoad = true;
        console.warn('Recipe history is unavailable because localStorage is not present.');
      }
      return [];
    }
    const raw = storage.getItem(recipeHistoryStorageKey(descriptor));
    return raw === null ? [] : parseRecipeHistory(raw);
  } catch (error) {
    console.error('Recipe history could not be loaded from localStorage.', error);
    return [];
  }
}

export function recordRecipeHistory(
  descriptor: Pick<DatasetDescriptor, 'slug' | 'publicationId'>,
  entry: RecipeHistoryEntry,
): void {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      if (!warnedUnavailableSave) {
        warnedUnavailableSave = true;
        console.warn('Recipe history changes are not persistent because localStorage is unavailable.');
      }
      return;
    }
    const current = loadRecipeHistory(descriptor);
    const next: StoredRecipeHistory = {
      version: RECIPE_HISTORY_VERSION,
      entries: mergeRecipeHistory(current, entry),
    };
    storage.setItem(recipeHistoryStorageKey(descriptor), JSON.stringify(next));
  } catch (error) {
    console.error('Recipe history could not be saved to localStorage.', error);
  }
}
