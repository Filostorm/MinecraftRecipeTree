export const CORE_DATASET_PUBLICATION_FORMAT = 'mrt-core-dataset-publication-v1';
export const GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY =
  'gtnh-structured-data-only-v1';
export const CORE_DATASET_PUBLICATION_ID_PATTERN = /^[a-f0-9]{64}$/;
export const MAX_CORE_PUBLICATION_MANIFEST_BYTES = 8 * 1024 * 1024;
export const MAX_CORE_DOCUMENT_BYTES = 8 * 1024 * 1024;
export const MAX_CORE_PACK_BYTES = 1024 * 1024;
export const MAX_CORE_PACK_INDEX_BYTES = 512 * 1024;
export const CORE_PACK_INDEX_FORMAT = 'mrt-packed-image-authorization-index-v1';
export const CORE_PACK_INDEX_HEADER_BYTES = 20;
export const CORE_PACK_INDEX_ENTRY_BYTES = 8;

const CANONICAL_PADDED_INDEX = String.raw`(?:\d{3}|[1-9]\d{3,})`;
export const CORE_DATASET_DOCUMENT_ROUTE = /^[A-Za-z0-9._/-]+\.json$/;
export const CORE_DATASET_PACK_ROUTE = new RegExp(
  `^assets/pack-${CANONICAL_PADDED_INDEX}\\.bin$`,
);
export const CORE_DATASET_PACK_INDEX_ROUTE = new RegExp(
  `^indexes/pack-${CANONICAL_PADDED_INDEX}\\.bin$`,
);

export interface CoreContentRecord {
  path: string;
  bytes: number;
  sha256: string;
}

export interface CorePackIndexRecord extends CoreContentRecord {
  entries: number;
}

export interface CorePackRecord extends CoreContentRecord {
  index: CorePackIndexRecord;
}

export interface CoreDatasetPublicationManifest {
  format: typeof CORE_DATASET_PUBLICATION_FORMAT;
  publicationPolicy?: typeof GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY;
  publicationId: string;
  maxDocumentBytes: number;
  maxPackBytes: number;
  packIndexFormat: typeof CORE_PACK_INDEX_FORMAT;
  maxPackIndexBytes: number;
  counts: {
    documents: number;
    packs: number;
    packedImages: number;
    documentBytes: number;
    packBytes: number;
    packIndexBytes: number;
    objects: number;
    storedBytes: number;
  };
  documents: CoreContentRecord[];
  packs: CorePackRecord[];
}

export interface ValidatedCoreDatasetPublication {
  manifest: CoreDatasetPublicationManifest;
  contentRecordsByPath: Map<string, CoreContentRecord>;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value: unknown, keys: readonly string[]): value is Record<string, unknown> {
  if (!isRecord(value)) return false;
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  return actual.length === expected.length && actual.every((key, index) => key === expected[index]);
}

function isCanonicalPath(value: unknown, route: RegExp): value is string {
  return (
    typeof value === 'string' &&
    value.length > 0 &&
    value.length <= 1024 &&
    !value.startsWith('/') &&
    !value.includes('\\') &&
    !value.includes('//') &&
    value.split('/').every(segment => segment.length > 0 && segment !== '.' && segment !== '..') &&
    route.test(value)
  );
}

function requireContentRecord(
  value: unknown,
  route: RegExp,
  maximumBytes: number,
  label: string,
): CoreContentRecord {
  if (
    !hasExactKeys(value, ['path', 'bytes', 'sha256']) ||
    !isCanonicalPath(value.path, route) ||
    !Number.isSafeInteger(value.bytes) ||
    (value.bytes as number) <= 0 ||
    (value.bytes as number) > maximumBytes ||
    typeof value.sha256 !== 'string' ||
    !CORE_DATASET_PUBLICATION_ID_PATTERN.test(value.sha256)
  ) {
    throw new Error(`${label} is not a canonical bounded content record.`);
  }
  return value as unknown as CoreContentRecord;
}

function canonicalIndex(index: number): string {
  return String(index).padStart(3, '0');
}

export function coreDatasetContentRecords(
  manifest: CoreDatasetPublicationManifest,
): CoreContentRecord[] {
  return [
    ...manifest.documents,
    ...manifest.packs.map(pack => ({path: pack.path, bytes: pack.bytes, sha256: pack.sha256})),
    ...manifest.packs.map(pack => pack.index),
  ].sort((left, right) => left.path.localeCompare(right.path));
}

export function requireCoreDatasetPublicationManifest(
  value: unknown,
  expectedPublicationId?: string,
): ValidatedCoreDatasetPublication {
  const dataOnly =
    isRecord(value) &&
    value.publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY;
  const topLevelKeys = [
    'format',
    'publicationId',
    'maxDocumentBytes',
    'maxPackBytes',
    'packIndexFormat',
    'maxPackIndexBytes',
    'counts',
    'documents',
    'packs',
    ...(dataOnly ? ['publicationPolicy'] : []),
  ];
  if (
    !hasExactKeys(value, topLevelKeys) ||
    value.format !== CORE_DATASET_PUBLICATION_FORMAT ||
    typeof value.publicationId !== 'string' ||
    !CORE_DATASET_PUBLICATION_ID_PATTERN.test(value.publicationId) ||
    (expectedPublicationId !== undefined && value.publicationId !== expectedPublicationId) ||
    value.maxDocumentBytes !== MAX_CORE_DOCUMENT_BYTES ||
    value.maxPackBytes !== MAX_CORE_PACK_BYTES ||
    value.packIndexFormat !== CORE_PACK_INDEX_FORMAT ||
    value.maxPackIndexBytes !== MAX_CORE_PACK_INDEX_BYTES ||
    !hasExactKeys(value.counts, [
      'documents',
      'packs',
      'packedImages',
      'documentBytes',
      'packBytes',
      'packIndexBytes',
      'objects',
      'storedBytes',
    ]) ||
    !Array.isArray(value.documents) ||
    value.documents.length === 0 ||
    !Array.isArray(value.packs) ||
    (dataOnly ? value.packs.length !== 0 : value.packs.length === 0)
  ) {
    throw new Error('Core dataset publication does not satisfy the exact v1 control-manifest contract.');
  }

  for (const [name, count] of Object.entries(value.counts)) {
    if (!Number.isSafeInteger(count) || (count as number) < 0) {
      throw new Error(`Core dataset publication count ${name} is invalid.`);
    }
  }

  const paths = new Set<string>();
  const documents: CoreContentRecord[] = [];
  let previousDocumentPath = '';
  let documentBytes = 0;
  for (const [index, rawDocument] of value.documents.entries()) {
    const document = requireContentRecord(
      rawDocument,
      CORE_DATASET_DOCUMENT_ROUTE,
      MAX_CORE_DOCUMENT_BYTES,
      `Document ${index}`,
    );
    if (
      document.path === 'publication.json' ||
      document.path.startsWith('indexes/') ||
      document.path <= previousDocumentPath ||
      paths.has(document.path)
    ) {
      throw new Error('Core dataset documents must have unique, strictly sorted paths.');
    }
    previousDocumentPath = document.path;
    paths.add(document.path);
    documents.push(document);
    documentBytes += document.bytes;
    if (!Number.isSafeInteger(documentBytes)) throw new Error('Core document bytes exceed the safe integer range.');
  }
  if (!paths.has('manifest.json')) {
    throw new Error('Core dataset documents must include manifest.json.');
  }

  const packs: CorePackRecord[] = [];
  let packBytes = 0;
  let packIndexBytes = 0;
  let packedImages = 0;
  for (const [packNumber, rawPack] of value.packs.entries()) {
    if (!hasExactKeys(rawPack, ['path', 'bytes', 'sha256', 'index'])) {
      throw new Error(`Core pack ${packNumber} has unsupported or missing fields.`);
    }
    const packRecord = requireContentRecord(
      {path: rawPack.path, bytes: rawPack.bytes, sha256: rawPack.sha256},
      CORE_DATASET_PACK_ROUTE,
      MAX_CORE_PACK_BYTES,
      `Pack ${packNumber}`,
    );
    const expectedSuffix = canonicalIndex(packNumber);
    if (packRecord.path !== `assets/pack-${expectedSuffix}.bin` || paths.has(packRecord.path)) {
      throw new Error(`Core pack ${packNumber} does not use its canonical consecutive path.`);
    }
    const rawIndex = rawPack.index;
    if (
      !hasExactKeys(rawIndex, ['path', 'bytes', 'sha256', 'entries']) ||
      !isCanonicalPath(rawIndex.path, CORE_DATASET_PACK_INDEX_ROUTE) ||
      rawIndex.path !== `indexes/pack-${expectedSuffix}.bin` ||
      !Number.isSafeInteger(rawIndex.bytes) ||
      (rawIndex.bytes as number) <= 0 ||
      (rawIndex.bytes as number) > MAX_CORE_PACK_INDEX_BYTES ||
      typeof rawIndex.sha256 !== 'string' ||
      !CORE_DATASET_PUBLICATION_ID_PATTERN.test(rawIndex.sha256) ||
      !Number.isSafeInteger(rawIndex.entries) ||
      (rawIndex.entries as number) <= 0 ||
      rawIndex.bytes !==
        CORE_PACK_INDEX_HEADER_BYTES +
          (rawIndex.entries as number) * CORE_PACK_INDEX_ENTRY_BYTES ||
      paths.has(rawIndex.path)
    ) {
      throw new Error(`Core pack ${packNumber} has an invalid MRPI authorization record.`);
    }
    const pack: CorePackRecord = {
      ...packRecord,
      index: rawIndex as unknown as CorePackIndexRecord,
    };
    paths.add(pack.path);
    paths.add(pack.index.path);
    packs.push(pack);
    packBytes += pack.bytes;
    packIndexBytes += pack.index.bytes;
    packedImages += pack.index.entries;
    if (![packBytes, packIndexBytes, packedImages].every(Number.isSafeInteger)) {
      throw new Error('Core pack totals exceed the safe integer range.');
    }
  }

  const objects = documents.length + packs.length * 2;
  const storedBytes = documentBytes + packBytes + packIndexBytes;
  const expectedCounts = {
    documents: documents.length,
    packs: packs.length,
    packedImages,
    documentBytes,
    packBytes,
    packIndexBytes,
    objects,
    storedBytes,
  };
  for (const [name, expected] of Object.entries(expectedCounts)) {
    if (value.counts[name] !== expected) {
      throw new Error(`Core dataset count ${name} is inconsistent with its exact inventory.`);
    }
  }

  const manifest = value as unknown as CoreDatasetPublicationManifest;
  return {
    manifest,
    contentRecordsByPath: new Map(coreDatasetContentRecords(manifest).map(record => [record.path, record])),
  };
}

export function isGtnhStructuredDataOnlyCorePublication(
  manifest: CoreDatasetPublicationManifest,
): boolean {
  return manifest.publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY;
}

/** Recursively sorted, compact JSON with one final newline. */
export function canonicalCoreDatasetPublicationJson(value: unknown): string {
  if (value === null || typeof value === 'boolean' || typeof value === 'string') {
    return JSON.stringify(value);
  }
  if (typeof value === 'number') {
    if (!Number.isSafeInteger(value)) throw new Error('Canonical publication JSON only accepts safe integers.');
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map(canonicalCoreDatasetPublicationJson).join(',')}]`;
  }
  if (isRecord(value)) {
    return `{${Object.keys(value)
      .sort()
      .map(key => `${JSON.stringify(key)}:${canonicalCoreDatasetPublicationJson(value[key])}`)
      .join(',')}}`;
  }
  throw new Error(`Canonical publication JSON refuses ${typeof value} values.`);
}

export function canonicalCoreDatasetPublicationBytes(value: unknown): Uint8Array {
  const {manifest} = requireCoreDatasetPublicationManifest(value);
  const bytes = new TextEncoder().encode(`${canonicalCoreDatasetPublicationJson(manifest)}\n`);
  if (bytes.byteLength > MAX_CORE_PUBLICATION_MANIFEST_BYTES) {
    throw new Error('Core publication manifest exceeds the ingestion byte bound.');
  }
  return bytes;
}

function bytesEqual(left: Uint8Array, right: Uint8Array): boolean {
  if (left.byteLength !== right.byteLength) return false;
  let difference = 0;
  for (let index = 0; index < left.byteLength; index += 1) difference |= left[index] ^ right[index];
  return difference === 0;
}

export function requireCanonicalCoreDatasetPublicationBytes(
  bytes: Uint8Array,
  expectedPublicationId: string,
): ValidatedCoreDatasetPublication {
  if (bytes.byteLength <= 0 || bytes.byteLength > MAX_CORE_PUBLICATION_MANIFEST_BYTES) {
    throw new Error('Core publication manifest bytes are outside the ingestion bound.');
  }
  let value: unknown;
  try {
    value = JSON.parse(new TextDecoder().decode(bytes)) as unknown;
  } catch (error) {
    throw new Error(`Core publication manifest is invalid JSON: ${error instanceof Error ? error.message : String(error)}`);
  }
  const state = requireCoreDatasetPublicationManifest(value, expectedPublicationId);
  if (!bytesEqual(bytes, canonicalCoreDatasetPublicationBytes(state.manifest))) {
    throw new Error('Core publication manifest is not in canonical byte form.');
  }
  return state;
}
