import type {DatasetAttribution, Manifest} from '../types';

export const GTNH_1710_DATASET_PROFILE = 'gtnh-1.7.10';
export const GTNH_PACK_NAME = 'GT New Horizons';
export const GTNH_PACK_VERSION = '2.8.4';
export const GTNH_STRUCTURED_DATA_ONLY_POLICY = 'gtnh-structured-data-only-v1';
export const GTNH_VISUAL_ASSETS_POLICY = Object.freeze({
  format: 'mrt-visual-assets-policy-v1',
  mode: 'structured-data-only',
  policy: GTNH_STRUCTURED_DATA_ONLY_POLICY,
  itemIcons: 0,
  categoryIcons: 0,
  recipePreviews: 0,
  mobSprites: 0,
  packedImageFiles: 0,
} as const);
export const GTNH_DATASET_ATTRIBUTION = Object.freeze({
  sourceUrl: 'https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/tree/2.8.4',
  projectUrl: 'https://www.gtnewhorizons.com/',
  licenseIdentifier: 'CC BY-NC-SA 4.0',
  licenseUrl: 'https://creativecommons.org/licenses/by-nc-sa/4.0/',
}) satisfies Readonly<DatasetAttribution>;

export function isExactGtnhDatasetAttribution(value: unknown): value is DatasetAttribution {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const candidate = value as Record<string, unknown>;
  const keys = Object.keys(candidate).sort();
  const expectedKeys = Object.keys(GTNH_DATASET_ATTRIBUTION).sort();
  return (
    keys.length === expectedKeys.length &&
    keys.every((key, index) => key === expectedKeys[index]) &&
    expectedKeys.every(
      key => candidate[key] === GTNH_DATASET_ATTRIBUTION[key as keyof DatasetAttribution],
    )
  );
}

export function isExactGtnhVisualAssetsPolicy(
  value: unknown,
): value is typeof GTNH_VISUAL_ASSETS_POLICY {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const candidate = value as Record<string, unknown>;
  const keys = Object.keys(candidate).sort();
  const expectedKeys = Object.keys(GTNH_VISUAL_ASSETS_POLICY).sort();
  return (
    keys.length === expectedKeys.length &&
    keys.every((key, index) => key === expectedKeys[index]) &&
    expectedKeys.every(
      key => candidate[key] === GTNH_VISUAL_ASSETS_POLICY[key as keyof typeof GTNH_VISUAL_ASSETS_POLICY],
    )
  );
}

export interface LoadedDatasetAttribution {
  readonly profile: typeof GTNH_1710_DATASET_PROFILE;
  readonly packName: string;
  readonly packVersion: string;
  readonly publicationPolicy: typeof GTNH_STRUCTURED_DATA_ONLY_POLICY | null;
  readonly visualMode: 'runtime-rendered-export' | 'structured-data-only';
  readonly attribution: DatasetAttribution;
}

/**
 * Licensing UI is derived from the immutable loaded manifest, never from the mutable channel slug.
 * Other profiles retain their existing no-notice behavior.
 */
export function loadedDatasetAttribution(
  manifest: Manifest | null | undefined,
): LoadedDatasetAttribution | null {
  if (manifest?.profile !== GTNH_1710_DATASET_PROFILE) return null;
  if (
    manifest.pack?.name !== GTNH_PACK_NAME ||
    manifest.pack.version !== GTNH_PACK_VERSION ||
    manifest.pack.identitySource !== 'explicit-request' ||
    !(
      (manifest.publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_POLICY &&
        isExactGtnhVisualAssetsPolicy(manifest.web?.visualAssets)) ||
      (manifest.publicationPolicy === undefined && manifest.web?.visualAssets === undefined)
    ) ||
    !isExactGtnhDatasetAttribution(manifest.attribution)
  ) {
    throw new Error(
      `Loaded ${GTNH_1710_DATASET_PROFILE} manifest does not match its exact pack, ` +
        'visual-publication, and attribution contract.',
    );
  }
  return Object.freeze({
    profile: GTNH_1710_DATASET_PROFILE,
    packName: manifest.pack.name,
    packVersion: manifest.pack.version,
    publicationPolicy: manifest.publicationPolicy ?? null,
    visualMode:
      manifest.publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_POLICY
        ? 'structured-data-only'
        : 'runtime-rendered-export',
    attribution: manifest.attribution,
  });
}
