export const PREVIEW_SIDECAR_FORMAT = 'mrt-recipe-preview-sidecar-v1';
export const PREVIEW_CATEGORY_FORMAT = 'mrt-recipe-preview-category-v1';
export const PREVIEW_PACK_INDEX_FORMAT = 'mrt-recipe-preview-pack-index-v1';
export const PREVIEW_IMAGE_PREFIX = 'recipe-assets/s/';
export const PREVIEW_VIRTUAL_BASE = '/dataset/previews';
export const PREVIEW_MAX_PACK_BYTES = 1024 * 1024;
export const PREVIEW_MAX_CATEGORY_BYTES = 256 * 1024;
export const PREVIEW_MAX_PACK_INDEX_BYTES = 512 * 1024;

const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const PREVIEW_IMAGE_ROUTE = /^recipe-assets\/s\/(\d+)-(\d+)-(\d+)\.webp$/;
const PREVIEW_PART_PATH = /^categories\/\d{3}\/part-\d{3}\.json$/;

function isDatasetPublicationId(value: unknown): value is string {
  return typeof value === 'string' && SHA256_PATTERN.test(value);
}

export function versionPreviewUrl(
  url: string,
  datasetPublicationId: string,
  assetSetId?: string,
): string {
  if (!isDatasetPublicationId(datasetPublicationId)) {
    throw new Error('Recipe-preview URL requires a lowercase SHA-256 dataset identity.');
  }
  if (assetSetId !== undefined && !isDatasetPublicationId(assetSetId)) {
    throw new Error('Recipe-preview URL requires a lowercase SHA-256 asset-set identity.');
  }
  const separator = url.includes('?') ? '&' : '?';
  return (
    `${url}${separator}dataset=${datasetPublicationId}` +
    (assetSetId === undefined ? '' : `&preview=${assetSetId}`)
  );
}

export type RecipePreviewCoordinate = [
  packNumber: number,
  offset: number,
  length: number,
  logicalWidth: number,
  logicalHeight: number,
];
export type RecipePreviewEntry = RecipePreviewCoordinate | null;

export interface RecipePreviewPack {
  path: string;
  bytes: number;
  sha256: string;
  index: RecipePreviewPackIndex;
}

export interface RecipePreviewPackIndex {
  path: string;
  bytes: number;
  sha256: string;
  entries: number;
}

export interface RecipePreviewManifest {
  format: typeof PREVIEW_SIDECAR_FORMAT;
  assetSetId: string;
  datasetPublicationId: string;
  maxPackBytes: typeof PREVIEW_MAX_PACK_BYTES;
  packIndexFormat: typeof PREVIEW_PACK_INDEX_FORMAT;
  maxPackIndexBytes: typeof PREVIEW_MAX_PACK_INDEX_BYTES;
  imageFormat: 'lossless-webp';
  counts: {
    categories: number;
    recipes: number;
    previews: number;
    missing: number;
    uniqueImages: number;
    duplicates: number;
    packs: number;
    inputBytes: number;
    hostedOmittedWebpBytes: number;
    encodedBytes: number;
    storedBytes: number;
    packIndexBytes: number;
  };
  packs: RecipePreviewPack[];
}

export interface RecipePreviewPart {
  path: string;
  start: number;
  count: number;
  bytes: number;
}

export type RecipePreviewCategoryDocument = {
  format: typeof PREVIEW_CATEGORY_FORMAT;
  categoryIndex: number;
  categoryId: string;
  count: number;
} & (
  | {previews: RecipePreviewEntry[]; parts?: never}
  | {parts: RecipePreviewPart[]; previews?: never}
);

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function isNonnegativeInteger(value: unknown): value is number {
  return Number.isSafeInteger(value) && (value as number) >= 0;
}

function isPositiveInteger(value: unknown): value is number {
  return Number.isSafeInteger(value) && (value as number) > 0;
}

function requirePreviewCoordinate(
  value: unknown,
  label: string,
  packs: readonly RecipePreviewPack[],
): RecipePreviewCoordinate {
  if (!Array.isArray(value) || value.length !== 5 || !value.every(Number.isSafeInteger)) {
    throw new Error(`${label} must be a five-integer preview coordinate.`);
  }
  const [packNumber, offset, length, logicalWidth, logicalHeight] = value;
  const pack = packs[packNumber];
  if (
    packNumber < 0 ||
    !pack ||
    offset < 0 ||
    length <= 0 ||
    offset + length > pack.bytes ||
    logicalWidth <= 0 ||
    logicalHeight <= 0
  ) {
    throw new Error(`${label} is outside the preview pack or logical-image bounds.`);
  }
  return value as RecipePreviewCoordinate;
}

export function requirePreviewEntries(
  value: unknown,
  expectedCount: number,
  label: string,
  packs: readonly RecipePreviewPack[],
): RecipePreviewEntry[] {
  if (!Array.isArray(value) || value.length !== expectedCount) {
    throw new Error(`${label} must contain exactly ${expectedCount} preview entries.`);
  }
  return value.map((entry, index) =>
    entry === null ? null : requirePreviewCoordinate(entry, `${label} entry ${index}`, packs),
  );
}

export function requireRecipePreviewManifest(
  value: unknown,
  datasetPublicationId: string,
): RecipePreviewManifest {
  if (!isRecord(value)) throw new Error('Recipe-preview manifest must be an object.');
  if (
    value.format !== PREVIEW_SIDECAR_FORMAT ||
    !SHA256_PATTERN.test(String(value.assetSetId)) ||
    value.datasetPublicationId !== datasetPublicationId ||
    !isDatasetPublicationId(value.datasetPublicationId) ||
    value.maxPackBytes !== PREVIEW_MAX_PACK_BYTES ||
    value.packIndexFormat !== PREVIEW_PACK_INDEX_FORMAT ||
    value.maxPackIndexBytes !== PREVIEW_MAX_PACK_INDEX_BYTES ||
    value.imageFormat !== 'lossless-webp' ||
    !isRecord(value.counts) ||
    !Array.isArray(value.packs)
  ) {
    throw new Error('Recipe-preview manifest does not satisfy the immutable sidecar contract.');
  }
  const counts = value.counts;
  const countNames = [
    'categories',
    'recipes',
    'previews',
    'missing',
    'uniqueImages',
    'duplicates',
    'packs',
    'inputBytes',
    'hostedOmittedWebpBytes',
    'encodedBytes',
    'storedBytes',
    'packIndexBytes',
  ];
  if (!countNames.every(name => isNonnegativeInteger(counts[name]))) {
    throw new Error('Recipe-preview manifest contains invalid aggregate counts.');
  }
  if (
    (counts.previews as number) + (counts.missing as number) !== counts.recipes ||
    (counts.uniqueImages as number) + (counts.duplicates as number) !== counts.previews ||
    counts.packs !== value.packs.length
  ) {
    throw new Error('Recipe-preview manifest aggregate counts are internally inconsistent.');
  }
  for (const [index, pack] of value.packs.entries()) {
    if (
      !isRecord(pack) ||
      pack.path !== `assets/pack-${String(index).padStart(3, '0')}.bin` ||
      !isPositiveInteger(pack.bytes) ||
      pack.bytes > PREVIEW_MAX_PACK_BYTES ||
      !SHA256_PATTERN.test(String(pack.sha256)) ||
      !isRecord(pack.index) ||
      pack.index.path !== `indexes/pack-${String(index).padStart(3, '0')}.bin` ||
      !isPositiveInteger(pack.index.bytes) ||
      pack.index.bytes > PREVIEW_MAX_PACK_INDEX_BYTES ||
      !SHA256_PATTERN.test(String(pack.index.sha256)) ||
      !isPositiveInteger(pack.index.entries) ||
      pack.index.bytes !== 20 + pack.index.entries * 8
    ) {
      throw new Error(`Recipe-preview manifest pack ${index} is invalid.`);
    }
  }
  const storedBytes = value.packs.reduce((sum, pack) => sum + (pack as {bytes: number}).bytes, 0);
  const packIndexBytes = value.packs.reduce(
    (sum, pack) => sum + (pack as unknown as RecipePreviewPack).index.bytes,
    0,
  );
  const indexedImages = value.packs.reduce(
    (sum, pack) => sum + (pack as unknown as RecipePreviewPack).index.entries,
    0,
  );
  if (
    storedBytes !== counts.storedBytes ||
    packIndexBytes !== counts.packIndexBytes ||
    indexedImages !== counts.uniqueImages ||
    (counts.encodedBytes as number) < storedBytes
  ) {
    throw new Error('Recipe-preview manifest packed-byte totals are internally inconsistent.');
  }
  return value as unknown as RecipePreviewManifest;
}

export function requireRecipePreviewCategory(
  value: unknown,
  expectedCategoryIndex: number,
  expectedCategoryId: string,
  expectedCount: number,
  label: string,
  packs: readonly RecipePreviewPack[],
): RecipePreviewCategoryDocument {
  if (
    !isRecord(value) ||
    value.format !== PREVIEW_CATEGORY_FORMAT ||
    value.categoryIndex !== expectedCategoryIndex ||
    value.categoryId !== expectedCategoryId ||
    value.count !== expectedCount ||
    (('previews' in value) === ('parts' in value))
  ) {
    throw new Error(`${label} does not match its recipe category contract.`);
  }
  if ('previews' in value) {
    return {
      format: PREVIEW_CATEGORY_FORMAT,
      categoryIndex: expectedCategoryIndex,
      categoryId: expectedCategoryId,
      count: expectedCount,
      previews: requirePreviewEntries(value.previews, expectedCount, label, packs),
    };
  }
  if (!Array.isArray(value.parts) || (expectedCount === 0) !== (value.parts.length === 0)) {
    throw new Error(`${label} has an invalid preview-part list.`);
  }
  const parts: RecipePreviewPart[] = [];
  let expectedStart = 0;
  for (const [partIndex, part] of value.parts.entries()) {
    if (
      !isRecord(part) ||
      !PREVIEW_PART_PATH.test(String(part.path)) ||
      part.path !==
        `categories/${String(expectedCategoryIndex).padStart(3, '0')}/` +
          `part-${String(partIndex).padStart(3, '0')}.json` ||
      !isNonnegativeInteger(part.start) ||
      part.start !== expectedStart ||
      !isPositiveInteger(part.count) ||
      !isPositiveInteger(part.bytes) ||
      part.bytes > PREVIEW_MAX_CATEGORY_BYTES
    ) {
      throw new Error(`${label} preview part ${partIndex} is invalid.`);
    }
    parts.push(part as unknown as RecipePreviewPart);
    expectedStart += part.count;
  }
  if (expectedStart !== expectedCount) {
    throw new Error(`${label} preview parts cover ${expectedStart} entries, expected ${expectedCount}.`);
  }
  return {
    format: PREVIEW_CATEGORY_FORMAT,
    categoryIndex: expectedCategoryIndex,
    categoryId: expectedCategoryId,
    count: expectedCount,
    parts,
  };
}

export async function selectRecipePreviewEntries(
  document: RecipePreviewCategoryDocument,
  requestedIndices: ReadonlySet<number>,
  loadPart: (part: RecipePreviewPart) => Promise<RecipePreviewEntry[]>,
): Promise<Map<number, RecipePreviewEntry>> {
  const selected = new Map<number, RecipePreviewEntry>();
  for (const recipeIndex of requestedIndices) {
    if (!isNonnegativeInteger(recipeIndex) || recipeIndex >= document.count) {
      throw new Error(
        `Recipe-preview reference ${document.categoryIndex}:${recipeIndex} is outside its category.`,
      );
    }
  }
  if (document.previews) {
    for (const recipeIndex of requestedIndices) {
      selected.set(recipeIndex, document.previews[recipeIndex]);
    }
    return selected;
  }

  const requestsByPath = new Map<
    string,
    {part: RecipePreviewPart; indices: number[]}
  >();
  for (const recipeIndex of requestedIndices) {
    const part = document.parts.find(
      candidate =>
        recipeIndex >= candidate.start && recipeIndex < candidate.start + candidate.count,
    );
    if (!part) {
      throw new Error(
        `Recipe-preview reference ${document.categoryIndex}:${recipeIndex} is not covered by ` +
          'its descriptor.',
      );
    }
    const request = requestsByPath.get(part.path) ?? {part, indices: []};
    request.indices.push(recipeIndex);
    requestsByPath.set(part.path, request);
  }
  await Promise.all(
    [...requestsByPath.values()].map(async ({part, indices}) => {
      const entries = await loadPart(part);
      if (entries.length !== part.count) {
        throw new Error(
          `Recipe-preview part ${part.path} returned ${entries.length} entries; expected ` +
            `${part.count}.`,
        );
      }
      for (const recipeIndex of indices) {
        selected.set(recipeIndex, entries[recipeIndex - part.start]);
      }
    }),
  );
  return selected;
}

export function recipePreviewImagePath(coordinate: RecipePreviewCoordinate): string {
  const [packNumber, offset, length] = coordinate;
  return (
    `${PREVIEW_IMAGE_PREFIX}${String(packNumber).padStart(3, '0')}-` +
    `${offset}-${length}.webp`
  );
}

export function isCanonicalRecipePreviewImagePath(value: string): boolean {
  const match = PREVIEW_IMAGE_ROUTE.exec(value);
  if (!match) return false;
  const [packText, offsetText, lengthText] = match.slice(1);
  const packNumber = Number(packText);
  const offset = Number(offsetText);
  const length = Number(lengthText);
  return (
    Number.isSafeInteger(packNumber) &&
    packNumber >= 0 &&
    String(packNumber).padStart(3, '0') === packText &&
    Number.isSafeInteger(offset) &&
    offset >= 0 &&
    String(offset) === offsetText &&
    Number.isSafeInteger(length) &&
    length > 0 &&
    String(length) === lengthText &&
    offset + length <= PREVIEW_MAX_PACK_BYTES
  );
}

export function previewAssetUrl(
  imagePath: string,
  datasetPublicationId: string,
  assetSetId: string,
  previewBase = PREVIEW_VIRTUAL_BASE,
): string {
  if (!isCanonicalRecipePreviewImagePath(imagePath)) {
    throw new Error(`Recipe-preview image path is malformed: ${imagePath}`);
  }
  if (
    typeof previewBase !== 'string' ||
    previewBase.length === 0 ||
    previewBase.includes('?') ||
    previewBase.includes('#')
  ) {
    throw new Error('Recipe-preview base URL must be a non-empty path or URL without query or fragment.');
  }
  return versionPreviewUrl(
    `${previewBase.replace(/\/+$/, '')}/${imagePath.slice('recipe-'.length)}`,
    datasetPublicationId,
    assetSetId,
  );
}
