import type {RecipeRef} from '../types';
import type {IngredientSelections} from '../data/ingredientAlternativeSelection';
import type {DatasetDescriptor} from '../data/datasetCatalog';

const STORAGE_KEY = 'minecraft-recipe-tree.preferred-sources.v3';
const UNSCOPED_STORAGE_KEY = 'minecraft-recipe-tree.preferred-sources.v2';
const LEGACY_RECIPE_STORAGE_KEY = 'minecraft-recipe-tree.favorite-recipes.v1';

export type PreferredSource =
  | {
      t: 'recipe';
      ref: RecipeRef;
      allowFluidTransfer?: true;
      ingredientSelections?: IngredientSelections;
    }
  | {t: 'mob'; mobId: string}
  | {t: 'block'; blockKey: string};

export type PreferredSources = Record<string, PreferredSource>;
type PreferredSourceValidator = (itemKey: string, source: PreferredSource) => boolean;

interface ScopedPreferredSources {
  version: 3;
  scopes: Record<string, PreferredSources>;
}

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
    (source.t === 'recipe' &&
      isRecipeRef(source.ref) &&
      (source.allowFluidTransfer === undefined || source.allowFluidTransfer === true) &&
      isIngredientSelections(source.ingredientSelections)) ||
    (source.t === 'mob' && typeof source.mobId === 'string' && source.mobId.length > 0) ||
    (source.t === 'block' &&
      typeof source.blockKey === 'string' &&
      source.blockKey.length > 0)
  );
}

function isIngredientSelections(value: unknown): value is IngredientSelections | undefined {
  return (
    value === undefined ||
    (!!value &&
      typeof value === 'object' &&
      !Array.isArray(value) &&
      Object.keys(value).length <= 256 &&
      Object.entries(value).every(
        ([selectionKey, selectedKey]) =>
          selectionKey.length > 0 &&
          selectionKey.length <= 512 &&
          typeof selectedKey === 'string' &&
          selectedKey.length > 0 &&
          selectedKey.length <= 512,
      ))
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

function scopeKey(descriptor: DatasetDescriptor): string {
  return `${descriptor.slug}:${descriptor.publicationId}`;
}

function parseScopedSources(value: unknown): ScopedPreferredSources | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const record = value as Record<string, unknown>;
  if (record.version !== 3 || !record.scopes || typeof record.scopes !== 'object' || Array.isArray(record.scopes)) {
    return null;
  }
  const scopes: Record<string, PreferredSources> = {};
  for (const [key, sourcesValue] of Object.entries(record.scopes)) {
    const sources = parseSources(sourcesValue);
    if (key && sources) scopes[key] = sources;
    else console.error('Scoped preferred source storage contains an invalid pack entry.', {key});
  }
  return {version: 3, scopes};
}

function loadUnscopedSources(storage: Storage): PreferredSources {
  const stored = storage.getItem(UNSCOPED_STORAGE_KEY);
  if (stored) {
    const sources = parseSources(JSON.parse(stored) as unknown);
    if (sources) return sources;
    console.error('Unscoped preferred source storage contains an invalid root value.');
    return {};
  }

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
  return migrated;
}

function acceptedSources(
  sources: PreferredSources,
  accepts: PreferredSourceValidator,
): PreferredSources {
  return Object.fromEntries(
    Object.entries(sources).filter(([itemKey, source]) => accepts(itemKey, source)),
  );
}

export function loadPreferredSources(
  descriptor: DatasetDescriptor,
  accepts: PreferredSourceValidator,
): PreferredSources {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      console.warn('Preferred sources are using memory only because localStorage is unavailable.');
      return {};
    }

    const key = scopeKey(descriptor);
    const stored = storage.getItem(STORAGE_KEY);
    let scoped: ScopedPreferredSources = {version: 3, scopes: {}};
    if (stored) {
      const parsed = parseScopedSources(JSON.parse(stored) as unknown);
      if (!parsed) {
        console.error('Scoped preferred source storage contains an invalid root value.');
        return {};
      }
      scoped = parsed;
    }

    const existing = scoped.scopes[key];
    if (existing) {
      const accepted = acceptedSources(existing, accepts);
      if (Object.keys(accepted).length !== Object.keys(existing).length) {
        scoped.scopes[key] = accepted;
        storage.setItem(STORAGE_KEY, JSON.stringify(scoped));
        console.warn('Preferred sources unavailable in the current pack publication were removed.', {
          packSlug: descriptor.slug,
          publicationId: descriptor.publicationId,
          removed: Object.keys(existing).length - Object.keys(accepted).length,
        });
      }
      return accepted;
    }

    const unscoped = loadUnscopedSources(storage);
    const migrated = acceptedSources(unscoped, accepts);
    if (Object.keys(unscoped).length > 0) {
      scoped.scopes[key] = migrated;
      storage.setItem(STORAGE_KEY, JSON.stringify(scoped));
      console.info('Unscoped preferred sources were migrated into the current pack publication.', {
        packSlug: descriptor.slug,
        publicationId: descriptor.publicationId,
        imported: Object.keys(migrated).length,
        skipped: Object.keys(unscoped).length - Object.keys(migrated).length,
      });
    }
    return migrated;
  } catch (error) {
    console.error('Preferred sources could not be loaded from localStorage.', error);
    return {};
  }
}

export function persistPreferredSources(
  descriptor: DatasetDescriptor,
  sources: PreferredSources,
): void {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      console.warn('Preferred source changes are not persistent because localStorage is unavailable.');
      return;
    }
    const stored = storage.getItem(STORAGE_KEY);
    const scoped = stored
      ? parseScopedSources(JSON.parse(stored) as unknown)
      : {version: 3 as const, scopes: {}};
    if (!scoped) {
      console.error('Preferred source changes were not saved because scoped storage is invalid.');
      return;
    }
    scoped.scopes[scopeKey(descriptor)] = sources;
    storage.setItem(STORAGE_KEY, JSON.stringify(scoped));
  } catch (error) {
    console.error('Preferred sources could not be saved to localStorage.', error);
  }
}
