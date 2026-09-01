import type {DatasetDescriptor} from '../data/datasetCatalog';
import type {RecipeRef} from '../types';

const STORAGE_VERSION = 1;
const MAX_OVERRIDE_COUNT = 4096;
const MAX_ITEM_KEY_LENGTH = 512;

export type ManualRetentionOverrides = Record<string, boolean>;

function storageKey(
  descriptor: Pick<DatasetDescriptor, 'slug' | 'publicationId'>,
): string {
  return `manualRetentionOverrides:v${STORAGE_VERSION}:${descriptor.slug}:${descriptor.publicationId}`;
}

export function manualRetentionOverrideKey(
  ref: RecipeRef,
  itemKey: string,
): string {
  return `${ref[0]}:${ref[1]}\u001f${itemKey}`;
}

function parseOverrides(raw: string): ManualRetentionOverrides {
  const value = JSON.parse(raw) as unknown;
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Manual retention overrides are not an object.');
  }
  const entries = Object.entries(value as Record<string, unknown>);
  if (entries.length > MAX_OVERRIDE_COUNT) {
    throw new Error('Manual retention overrides exceed the storage limit.');
  }
  const overrides: ManualRetentionOverrides = {};
  for (const [key, reusable] of entries) {
    const separator = key.indexOf('\u001f');
    const ref = key.slice(0, separator);
    const itemKey = key.slice(separator + 1);
    if (
      separator < 0 ||
      !/^\d+:\d+$/u.test(ref) ||
      itemKey.length === 0 ||
      itemKey.length > MAX_ITEM_KEY_LENGTH ||
      reusable !== true && reusable !== false
    ) {
      throw new Error('Manual retention overrides contain an invalid entry.');
    }
    overrides[key] = reusable;
  }
  return overrides;
}

export function loadManualRetentionOverrides(
  descriptor: Pick<DatasetDescriptor, 'slug' | 'publicationId'>,
): ManualRetentionOverrides {
  try {
    const raw = globalThis.localStorage?.getItem(storageKey(descriptor));
    return raw ? parseOverrides(raw) : {};
  } catch (error) {
    console.error('Manual recipe retention overrides could not be loaded.', error);
    return {};
  }
}

export function persistManualRetentionOverrides(
  descriptor: Pick<DatasetDescriptor, 'slug' | 'publicationId'>,
  overrides: ManualRetentionOverrides,
): void {
  try {
    globalThis.localStorage?.setItem(storageKey(descriptor), JSON.stringify(overrides));
  } catch (error) {
    console.error('Manual recipe retention overrides could not be saved.', error);
  }
}

export function manualRetentionOverrideFor(
  overrides: Readonly<ManualRetentionOverrides>,
  ref: RecipeRef,
  itemKey: string,
): boolean | undefined {
  return overrides[manualRetentionOverrideKey(ref, itemKey)];
}
