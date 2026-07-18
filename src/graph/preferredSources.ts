import {RecipeRef} from '../types';

const STORAGE_KEY = 'minecraft-recipe-tree.preferred-sources.v2';
const LEGACY_RECIPE_STORAGE_KEY = 'minecraft-recipe-tree.favorite-recipes.v1';

export type PreferredSource =
  | {t: 'recipe'; ref: RecipeRef}
  | {t: 'mob'; mobId: string}
  | {t: 'block'; blockKey: string};

export type PreferredSources = Record<string, PreferredSource>;

function isRecipeRef(value: unknown): value is RecipeRef {
  return (
    Array.isArray(value) &&
    value.length === 2 &&
    value.every(part => Number.isSafeInteger(part) && part >= 0)
  );
}

function isPreferredSource(value: unknown): value is PreferredSource {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const source = value as Partial<PreferredSource>;
  return (
    (source.t === 'recipe' && isRecipeRef(source.ref)) ||
    (source.t === 'mob' && typeof source.mobId === 'string' && source.mobId.length > 0) ||
    (source.t === 'block' &&
      typeof source.blockKey === 'string' &&
      source.blockKey.length > 0)
  );
}

function parseSources(value: unknown): PreferredSources | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const sources: PreferredSources = {};
  for (const [itemKey, source] of Object.entries(value)) {
    if (itemKey && isPreferredSource(source)) sources[itemKey] = source;
    else console.error('Preferred source storage contains an invalid entry.', {itemKey, source});
  }
  return sources;
}

export function loadPreferredSources(): PreferredSources {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      console.warn('Preferred sources are using memory only because localStorage is unavailable.');
      return {};
    }

    const stored = storage.getItem(STORAGE_KEY);
    if (stored) {
      const sources = parseSources(JSON.parse(stored) as unknown);
      if (sources) return sources;
      console.error('Preferred source storage contains an invalid root value.');
      return {};
    }

    // Migrate the original recipe-only preference format without discarding it.
    const legacyStored = storage.getItem(LEGACY_RECIPE_STORAGE_KEY);
    if (!legacyStored) return {};
    const legacy = JSON.parse(legacyStored) as unknown;
    if (!legacy || typeof legacy !== 'object' || Array.isArray(legacy)) {
      console.error('Legacy favorite recipe storage contains an invalid root value.');
      return {};
    }
    const migrated: PreferredSources = {};
    for (const [itemKey, ref] of Object.entries(legacy)) {
      if (itemKey && isRecipeRef(ref)) migrated[itemKey] = {t: 'recipe', ref};
      else console.error('Legacy favorite recipe storage contains an invalid entry.', {itemKey, ref});
    }
    storage.setItem(STORAGE_KEY, JSON.stringify(migrated));
    return migrated;
  } catch (error) {
    console.error('Preferred sources could not be loaded from localStorage.', error);
    return {};
  }
}

export function persistPreferredSources(sources: PreferredSources): void {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      console.warn('Preferred source changes are not persistent because localStorage is unavailable.');
      return;
    }
    storage.setItem(STORAGE_KEY, JSON.stringify(sources));
  } catch (error) {
    console.error('Preferred sources could not be saved to localStorage.', error);
  }
}
