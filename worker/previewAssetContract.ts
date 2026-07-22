import {isDatasetPublicationId} from '../src/data/datasetIdentity.ts';

export const MAX_PREVIEW_PACK_BYTES = 1024 * 1024;
export const MAX_PREVIEW_CATEGORY_BYTES = 256 * 1024;
export const MAX_PREVIEW_PACK_INDEX_BYTES = 512 * 1024;
export const MAX_PREVIEW_MANIFEST_BYTES = 1024 * 1024;
export const RECIPE_PREVIEW_SIDECAR_V1_FORMAT = 'mrt-recipe-preview-sidecar-v1';
export const RECIPE_PREVIEW_SIDECAR_V2_FORMAT = 'mrt-recipe-preview-sidecar-v2';
export const GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY =
  'gtnh-structured-data-only-v1';
export const GTNH_STRUCTURED_DATA_ONLY_EXCLUSION_REASON =
  'third-party-artwork-rights-not-cleared';
export const PREVIEW_PACK_INDEX_FORMAT = 'mrt-recipe-preview-pack-index-v1';
export const PREVIEW_PACK_INDEX_HEADER_BYTES = 20;
export const PREVIEW_PACK_INDEX_ENTRY_BYTES = 8;
export const PREVIEW_ASSET_SET_PATTERN = /^[a-f0-9]{64}$/;
// Builder paths use exactly three zero-padded digits below 1000, then ordinary
// unpadded decimal indices. Reject alternate spellings so the manifest, Worker,
// and client cannot disagree about the canonical object key.
const CANONICAL_PADDED_INDEX = String.raw`(?:\d{3}|[1-9]\d{3,})`;
export const PREVIEW_CATEGORY_ROUTE =
  new RegExp(
    `^categories\/(?:${CANONICAL_PADDED_INDEX}\\.json|${CANONICAL_PADDED_INDEX}\/part-${CANONICAL_PADDED_INDEX}\\.json)$`,
  );
export const PREVIEW_PACK_ROUTE = new RegExp(
  `^assets\/pack-${CANONICAL_PADDED_INDEX}\\.bin$`,
);
export const PREVIEW_PACK_INDEX_ROUTE = new RegExp(
  `^indexes\/pack-${CANONICAL_PADDED_INDEX}\\.bin$`,
);

export interface PreviewContentRecord {
  path: string;
  bytes: number;
  sha256: string;
}

export interface PreviewPackIndexRecord extends PreviewContentRecord {
  entries: number;
}

export interface PreviewPackRecord extends PreviewContentRecord {
  index: PreviewPackIndexRecord;
}

export interface PreviewManifest {
  format:
    | typeof RECIPE_PREVIEW_SIDECAR_V1_FORMAT
    | typeof RECIPE_PREVIEW_SIDECAR_V2_FORMAT;
  publicationPolicy?: typeof GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY;
  exclusionReason?: typeof GTNH_STRUCTURED_DATA_ONLY_EXCLUSION_REASON;
  assetSetId: string;
  datasetPublicationId: string;
  maxPackBytes: number;
  packIndexFormat: typeof PREVIEW_PACK_INDEX_FORMAT;
  maxPackIndexBytes: number;
  counts: {
    categories?: number;
    recipes?: number;
    previews?: number;
    missing?: number;
    duplicates?: number;
    inputBytes?: number;
    hostedOmittedPngBytes?: number;
    encodedBytes?: number;
    uniqueImages: number;
    packIndexBytes: number;
    packs?: number;
    storedBytes?: number;
    [key: string]: unknown;
  };
  packs: PreviewPackRecord[];
  categoryDocuments: PreviewContentRecord[];
  [key: string]: unknown;
}

export interface ValidatedPreviewManifest {
  manifest: PreviewManifest;
  categoryDocumentsByPath: Map<string, PreviewContentRecord>;
  contentRecordsByPath: Map<string, PreviewContentRecord>;
}

const utf8 = new TextEncoder();

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value: unknown, expected: readonly string[]): value is Record<string, unknown> {
  if (!isRecord(value)) return false;
  const actual = Object.keys(value).sort();
  const wanted = [...expected].sort();
  return actual.length === wanted.length && actual.every((key, index) => key === wanted[index]);
}

function isNonNegativeSafeInteger(value: unknown): value is number {
  return Number.isSafeInteger(value) && (value as number) >= 0;
}

function digestBytes(value: string): Uint8Array {
  const bytes = new Uint8Array(value.length / 2);
  for (let index = 0; index < bytes.length; index += 1) {
    bytes[index] = Number.parseInt(value.slice(index * 2, index * 2 + 2), 16);
  }
  return bytes;
}

/**
 * Recompute the builder's exact content address from validated immutable records.
 *
 * The format marker is unframed. Dataset identity, then each path and raw 32-byte digest,
 * use an unsigned eight-byte big-endian length prefix. Records are sorted by canonical path.
 */
export async function computePreviewAssetSetId(manifest: PreviewManifest): Promise<string> {
  const chunks: Uint8Array[] = [];
  let totalBytes = 0;
  const append = (bytes: Uint8Array): void => {
    const nextTotal = totalBytes + bytes.byteLength;
    if (!Number.isSafeInteger(nextTotal)) {
      throw new Error('Preview asset-set digest input exceeds the safe integer range.');
    }
    chunks.push(bytes);
    totalBytes = nextTotal;
  };
  const appendFramed = (bytes: Uint8Array): void => {
    const length = new Uint8Array(8);
    const view = new DataView(length.buffer);
    view.setUint32(0, Math.floor(bytes.byteLength / 0x1_0000_0000), false);
    view.setUint32(4, bytes.byteLength >>> 0, false);
    append(length);
    append(bytes);
  };

  append(utf8.encode(`${manifest.format}\0`));
  appendFramed(utf8.encode(manifest.datasetPublicationId));
  const records: PreviewContentRecord[] = [
    ...manifest.packs,
    ...manifest.packs.map(pack => pack.index),
    ...manifest.categoryDocuments,
  ];
  records.sort((left, right) => left.path < right.path ? -1 : left.path > right.path ? 1 : 0);
  for (const record of records) {
    appendFramed(utf8.encode(record.path));
    appendFramed(digestBytes(record.sha256));
  }

  const input = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    input.set(chunk, offset);
    offset += chunk.byteLength;
  }
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) {
    throw new Error('Web Crypto SHA-256 is unavailable; preview identity cannot be verified.');
  }
  const digest = new Uint8Array(await subtle.digest('SHA-256', input));
  return [...digest].map(byte => byte.toString(16).padStart(2, '0')).join('');
}

function validPreviewContentRecord(
  value: unknown,
  expectedPath: string | null,
  maxBytes: number,
): value is PreviewContentRecord {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const record = value as Partial<PreviewContentRecord>;
  return (
    typeof record.path === 'string' &&
    (expectedPath === null || record.path === expectedPath) &&
    Number.isSafeInteger(record.bytes) &&
    (record.bytes ?? 0) > 0 &&
    (record.bytes ?? 0) <= maxBytes &&
    PREVIEW_ASSET_SET_PATTERN.test(record.sha256 ?? '')
  );
}

function requireGtnhStructuredDataOnlyPreviewManifest(
  value: unknown,
  expectedAssetSetId: string,
): ValidatedPreviewManifest {
  const topLevelKeys = [
    'format',
    'publicationPolicy',
    'exclusionReason',
    'assetSetId',
    'datasetPublicationId',
    'maxPackBytes',
    'packIndexFormat',
    'maxPackIndexBytes',
    'imageFormat',
    'categoryFormat',
    'settings',
    'counts',
    'packs',
    'mapping',
    'categoryDocuments',
  ];
  const countKeys = [
    'categories',
    'recipes',
    'previews',
    'missing',
    'uniqueImages',
    'duplicates',
    'packs',
    'inputBytes',
    'hostedOmittedPngBytes',
    'encodedBytes',
    'storedBytes',
    'packIndexBytes',
  ];
  if (
    !PREVIEW_ASSET_SET_PATTERN.test(expectedAssetSetId) ||
    !hasExactKeys(value, topLevelKeys)
  ) {
    throw new Error(
      'The configured preview manifest does not satisfy the exact GTNH structured-data-only v2 contract.',
    );
  }

  const candidate = value;
  if (
    !hasExactKeys(candidate.settings, [
      'itemIconPixels',
      'recipeScale',
      'webpEffort',
      'maxCategoryBytes',
    ]) ||
    !hasExactKeys(candidate.counts, countKeys) ||
    !hasExactKeys(candidate.mapping, ['documents', 'parts', 'bytes']) ||
    !Array.isArray(candidate.packs) ||
    !Array.isArray(candidate.categoryDocuments)
  ) {
    throw new Error(
      'The configured preview manifest does not satisfy the exact GTNH structured-data-only v2 contract.',
    );
  }

  const settings = candidate.settings;
  const counts = candidate.counts;
  const mapping = candidate.mapping;
  if (
    candidate.format !== RECIPE_PREVIEW_SIDECAR_V2_FORMAT ||
    candidate.publicationPolicy !== GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY ||
    candidate.exclusionReason !== GTNH_STRUCTURED_DATA_ONLY_EXCLUSION_REASON ||
    candidate.assetSetId !== expectedAssetSetId ||
    !isDatasetPublicationId(candidate.datasetPublicationId) ||
    candidate.maxPackBytes !== MAX_PREVIEW_PACK_BYTES ||
    candidate.packIndexFormat !== PREVIEW_PACK_INDEX_FORMAT ||
    candidate.maxPackIndexBytes !== MAX_PREVIEW_PACK_INDEX_BYTES ||
    candidate.imageFormat !== 'lossless-webp' ||
    candidate.categoryFormat !== 'mrt-recipe-preview-category-v1' ||
    !isNonNegativeSafeInteger(settings.itemIconPixels) ||
    settings.itemIconPixels < 16 ||
    settings.itemIconPixels % 16 !== 0 ||
    !isNonNegativeSafeInteger(settings.recipeScale) ||
    settings.recipeScale <= 0 ||
    settings.webpEffort !== 4 ||
    !isNonNegativeSafeInteger(settings.maxCategoryBytes) ||
    settings.maxCategoryBytes < 256 ||
    settings.maxCategoryBytes > MAX_PREVIEW_CATEGORY_BYTES ||
    !countKeys.every(key => isNonNegativeSafeInteger(counts[key])) ||
    (counts.categories as number) <= 0 ||
    (counts.recipes as number) <= 0 ||
    counts.previews !== 0 ||
    counts.missing !== counts.recipes ||
    counts.uniqueImages !== 0 ||
    counts.duplicates !== 0 ||
    counts.packs !== 0 ||
    counts.inputBytes !== 0 ||
    counts.encodedBytes !== 0 ||
    counts.storedBytes !== 0 ||
    counts.packIndexBytes !== 0 ||
    candidate.packs.length !== 0 ||
    mapping.documents !== 0 ||
    mapping.parts !== 0 ||
    mapping.bytes !== 0 ||
    candidate.categoryDocuments.length !== 0
  ) {
    throw new Error(
      'The configured preview manifest does not satisfy the exact GTNH structured-data-only v2 contract.',
    );
  }
  return {
    manifest: value as PreviewManifest,
    categoryDocumentsByPath: new Map(),
    contentRecordsByPath: new Map(),
  };
}

export function requirePreviewManifest(
  value: unknown,
  expectedAssetSetId: string,
): ValidatedPreviewManifest {
  const candidate = value as Partial<PreviewManifest> | null;
  if (candidate?.format === RECIPE_PREVIEW_SIDECAR_V2_FORMAT) {
    return requireGtnhStructuredDataOnlyPreviewManifest(value, expectedAssetSetId);
  }
  if (
    !PREVIEW_ASSET_SET_PATTERN.test(expectedAssetSetId) ||
    !value ||
    typeof value !== 'object' ||
    Array.isArray(value) ||
    candidate?.format !== RECIPE_PREVIEW_SIDECAR_V1_FORMAT ||
    Object.hasOwn(candidate, 'publicationPolicy') ||
    candidate.assetSetId !== expectedAssetSetId ||
    !isDatasetPublicationId(candidate.datasetPublicationId) ||
    candidate.maxPackBytes !== MAX_PREVIEW_PACK_BYTES ||
    candidate.packIndexFormat !== PREVIEW_PACK_INDEX_FORMAT ||
    candidate.maxPackIndexBytes !== MAX_PREVIEW_PACK_INDEX_BYTES ||
    !candidate.counts ||
    typeof candidate.counts !== 'object' ||
    !Number.isSafeInteger(candidate.counts.uniqueImages) ||
    candidate.counts.uniqueImages < 1 ||
    !Number.isSafeInteger(candidate.counts.packIndexBytes) ||
    candidate.counts.packIndexBytes < 1 ||
    !Array.isArray(candidate.packs) ||
    candidate.packs.length < 1 ||
    !Array.isArray(candidate.categoryDocuments) ||
    candidate.packs.some(
      (pack, index) =>
        !validPreviewContentRecord(
          pack,
          `assets/pack-${String(index).padStart(3, '0')}.bin`,
          MAX_PREVIEW_PACK_BYTES,
        ) ||
        !validPreviewContentRecord(
          pack.index,
          `indexes/pack-${String(index).padStart(3, '0')}.bin`,
          MAX_PREVIEW_PACK_INDEX_BYTES,
        ) ||
        !Number.isSafeInteger(pack.index.entries) ||
        pack.index.entries <= 0 ||
        pack.index.bytes !==
          PREVIEW_PACK_INDEX_HEADER_BYTES +
            pack.index.entries * PREVIEW_PACK_INDEX_ENTRY_BYTES,
    )
  ) {
    throw new Error('The configured preview manifest does not satisfy the sidecar contract.');
  }

  const manifest = value as PreviewManifest;
  const indexBytes = manifest.packs.reduce((sum, pack) => sum + pack.index.bytes, 0);
  const indexedImages = manifest.packs.reduce((sum, pack) => sum + pack.index.entries, 0);
  const storedBytes = manifest.packs.reduce((sum, pack) => sum + pack.bytes, 0);
  if (
    !Number.isSafeInteger(indexBytes) ||
    indexBytes !== manifest.counts.packIndexBytes ||
    !Number.isSafeInteger(indexedImages) ||
    indexedImages !== manifest.counts.uniqueImages ||
    (manifest.counts.packs !== undefined && manifest.counts.packs !== manifest.packs.length) ||
    (manifest.counts.storedBytes !== undefined && manifest.counts.storedBytes !== storedBytes)
  ) {
    throw new Error('The configured preview manifest has inconsistent pack totals.');
  }

  const categoryDocumentsByPath = new Map<string, PreviewContentRecord>();
  const contentRecordsByPath = new Map<string, PreviewContentRecord>();
  for (const pack of manifest.packs) {
    contentRecordsByPath.set(pack.path, pack);
    contentRecordsByPath.set(pack.index.path, pack.index);
  }

  let previousPath = '';
  for (const document of manifest.categoryDocuments) {
    if (
      !validPreviewContentRecord(document, null, MAX_PREVIEW_CATEGORY_BYTES) ||
      !PREVIEW_CATEGORY_ROUTE.test(document.path) ||
      document.path <= previousPath ||
      contentRecordsByPath.has(document.path)
    ) {
      throw new Error('The configured preview manifest has an invalid category inventory.');
    }
    categoryDocumentsByPath.set(document.path, document);
    contentRecordsByPath.set(document.path, document);
    previousPath = document.path;
  }

  return {manifest, categoryDocumentsByPath, contentRecordsByPath};
}

/** Fail closed unless core and preview manifests declare one identical publication policy. */
export function requirePairedPublicationPolicy(
  coreManifest: {publicationPolicy?: unknown},
  previewManifest: PreviewManifest,
): void {
  const corePolicy = coreManifest.publicationPolicy;
  const previewPolicy = previewManifest.publicationPolicy;
  if (corePolicy === undefined && previewPolicy === undefined) {
    if (previewManifest.format !== RECIPE_PREVIEW_SIDECAR_V1_FORMAT) {
      throw new Error('An ordinary core publication requires a v1 preview sidecar.');
    }
    return;
  }
  if (
    corePolicy === GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY &&
    previewPolicy === GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY &&
    previewManifest.format === RECIPE_PREVIEW_SIDECAR_V2_FORMAT
  ) {
    return;
  }
  throw new Error(
    'Core and preview publications do not satisfy one exact paired publication-policy contract.',
  );
}

/** Validate shape and independently prove that the configured ID addresses these exact records. */
export async function requireContentAddressedPreviewManifest(
  value: unknown,
  expectedAssetSetId: string,
): Promise<ValidatedPreviewManifest> {
  const state = requirePreviewManifest(value, expectedAssetSetId);
  const computedAssetSetId = await computePreviewAssetSetId(state.manifest);
  if (computedAssetSetId !== expectedAssetSetId) {
    throw new Error(
      `Preview manifest assetSetId ${expectedAssetSetId} does not match its independently ` +
        `computed content address ${computedAssetSetId}.`,
    );
  }
  return state;
}
