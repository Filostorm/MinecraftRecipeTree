import {isDatasetPublicationId} from './datasetIdentity.ts';

const DATASET_SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const UNSAFE_IDENTITY_TEXT_PATTERN = /[\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/u;
const DATASET_DESCRIPTOR_KEYS = [
  'slug',
  'displayName',
  'minecraftVersion',
  'packVersion',
  'publicationId',
  'previewAssetSetId',
  'isDefault',
] as const;

export interface DatasetDescriptor {
  slug: string;
  displayName: string;
  minecraftVersion: string;
  packVersion: string;
  publicationId: string;
  previewAssetSetId: string;
  isDefault: boolean;
}

export interface DatasetSource {
  descriptor: DatasetDescriptor;
  base: string;
  previewBase: string;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value: Record<string, unknown>, expected: readonly string[]): boolean {
  const actual = Object.keys(value).sort();
  const required = [...expected].sort();
  return (
    actual.length === required.length &&
    actual.every((key, index) => key === required[index])
  );
}

function isBoundedText(value: unknown, maxLength: number): value is string {
  return (
    typeof value === 'string' &&
    [...value].length > 0 &&
    [...value].length <= maxLength &&
    value.trim() === value &&
    !UNSAFE_IDENTITY_TEXT_PATTERN.test(value)
  );
}

function requireDescriptor(value: unknown, index: number): DatasetDescriptor {
  if (
    !isRecord(value) ||
    !hasExactKeys(value, DATASET_DESCRIPTOR_KEYS) ||
    typeof value.slug !== 'string' ||
    !DATASET_SLUG_PATTERN.test(value.slug) ||
    value.slug.length > 80 ||
    !isBoundedText(value.displayName, 120) ||
    !isBoundedText(value.minecraftVersion, 40) ||
    !isBoundedText(value.packVersion, 80) ||
    !isDatasetPublicationId(value.publicationId) ||
    !isDatasetPublicationId(value.previewAssetSetId) ||
    typeof value.isDefault !== 'boolean'
  ) {
    throw new Error(
      `Dataset catalog descriptor ${index} does not satisfy the exact immutable dataset contract.`,
    );
  }
  return value as unknown as DatasetDescriptor;
}

/**
 * Validates the complete catalog response before any descriptor can influence a request URL.
 * Unknown fields are rejected so a server/client contract drift cannot be ignored silently.
 */
export function requireDatasetCatalog(value: unknown): DatasetDescriptor[] {
  if (!isRecord(value) || !hasExactKeys(value, ['datasets']) || !Array.isArray(value.datasets)) {
    throw new Error('Dataset catalog response must be an exact object containing a datasets array.');
  }
  if (value.datasets.length === 0 || value.datasets.length > 256) {
    throw new Error('Dataset catalog must contain between 1 and 256 descriptors.');
  }

  const datasets = value.datasets.map(requireDescriptor);
  const slugs = new Set<string>();
  const publicationIds = new Set<string>();
  const previewAssetSetIds = new Set<string>();
  for (const dataset of datasets) {
    if (slugs.has(dataset.slug)) {
      throw new Error(`Dataset catalog repeats slug ${JSON.stringify(dataset.slug)}.`);
    }
    if (publicationIds.has(dataset.publicationId)) {
      throw new Error(
        `Dataset catalog repeats publicationId ${JSON.stringify(dataset.publicationId)}.`,
      );
    }
    if (previewAssetSetIds.has(dataset.previewAssetSetId)) {
      throw new Error(
        `Dataset catalog repeats previewAssetSetId ${JSON.stringify(dataset.previewAssetSetId)}.`,
      );
    }
    slugs.add(dataset.slug);
    publicationIds.add(dataset.publicationId);
    previewAssetSetIds.add(dataset.previewAssetSetId);
  }

  const defaults = datasets.filter(dataset => dataset.isDefault);
  if (defaults.length !== 1) {
    throw new Error(`Dataset catalog must declare exactly one default; received ${defaults.length}.`);
  }
  return datasets;
}

export function readRequestedDatasetSlug(search: string): string | null {
  const normalized = search.startsWith('?') ? search.slice(1) : search;
  const parameters = new URLSearchParams(normalized);
  const values = parameters.getAll('pack');
  if (values.length === 0) return null;
  if (values.length !== 1 || !DATASET_SLUG_PATTERN.test(values[0]) || values[0].length > 80) {
    throw new Error('The pack query parameter must contain exactly one canonical dataset slug.');
  }
  return values[0];
}

export function selectDataset(
  datasets: readonly DatasetDescriptor[],
  requestedSlug: string | null,
): DatasetDescriptor {
  if (requestedSlug === null) {
    const defaultDataset = datasets.find(dataset => dataset.isDefault);
    if (!defaultDataset) {
      throw new Error('Dataset catalog has no default descriptor.');
    }
    return defaultDataset;
  }
  const selected = datasets.find(dataset => dataset.slug === requestedSlug);
  if (!selected) {
    throw new Error(
      `Unknown dataset slug ${JSON.stringify(requestedSlug)}. Available packs: ` +
        datasets.map(dataset => dataset.slug).join(', '),
    );
  }
  return selected;
}

function normalizeOrigin(origin: string): string {
  if (origin === '') return '';
  let url: URL;
  try {
    url = new URL(origin);
  } catch {
    throw new Error(`Dataset origin must be an absolute HTTP(S) origin: ${JSON.stringify(origin)}.`);
  }
  if (
    (url.protocol !== 'http:' && url.protocol !== 'https:') ||
    url.username !== '' ||
    url.password !== '' ||
    url.pathname !== '/' ||
    url.search !== '' ||
    url.hash !== ''
  ) {
    throw new Error(`Dataset origin must be an absolute HTTP(S) origin: ${JSON.stringify(origin)}.`);
  }
  return url.origin;
}

export function datasetSource(
  descriptor: DatasetDescriptor,
  origin = '',
): DatasetSource {
  if (!isDatasetPublicationId(descriptor.publicationId)) {
    throw new Error('Dataset source requires a valid publicationId.');
  }
  if (!isDatasetPublicationId(descriptor.previewAssetSetId)) {
    throw new Error('Dataset source requires a valid previewAssetSetId.');
  }
  const prefix = normalizeOrigin(origin);
  return {
    descriptor,
    base: `${prefix}/dataset/publications/${descriptor.publicationId}/exports`,
    previewBase: `${prefix}/dataset/preview-sets/${descriptor.previewAssetSetId}`,
  };
}

/** A React key that invalidates every dataset-dependent hook and cache on publication switch. */
export function datasetMountKey(descriptor: DatasetDescriptor): string {
  return `${descriptor.slug}:${descriptor.publicationId}:${descriptor.previewAssetSetId}`;
}

export function searchWithDatasetSlug(search: string, slug: string): string {
  if (!DATASET_SLUG_PATTERN.test(slug) || slug.length > 80) {
    throw new Error(`Cannot write an invalid dataset slug to the URL: ${JSON.stringify(slug)}.`);
  }
  const normalized = search.startsWith('?') ? search.slice(1) : search;
  const parameters = new URLSearchParams(normalized);
  parameters.delete('pack');
  parameters.set('pack', slug);
  const next = parameters.toString();
  return next.length > 0 ? `?${next}` : '';
}
