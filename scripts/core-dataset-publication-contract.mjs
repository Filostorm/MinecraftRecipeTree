import {
  MAX_PACKED_IMAGE_AUTHORIZATION_BYTES,
  PACKED_IMAGE_AUTHORIZATION_FORMAT,
  PACKED_IMAGE_AUTHORIZATION_HEADER_BYTES,
  PACKED_IMAGE_AUTHORIZATION_ENTRY_BYTES,
} from './packed-image-authorization.mjs';
import {MAX_PACK_BYTES} from './packed-assets.mjs';
import {MAX_SHARD_BYTES} from './sharded-documents.mjs';

export const CORE_DATASET_PUBLICATION_FORMAT = 'mrt-core-dataset-publication-v1';
export const GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY =
  'gtnh-structured-data-only-v1';
export const CORE_DATASET_PUBLICATION_ID_PATTERN = /^[a-f0-9]{64}$/;
export const MAX_CORE_PUBLICATION_MANIFEST_BYTES = 8 * 1024 * 1024;
export const MAX_CORE_DATASET_OBJECT_PATH_BYTES = 1024;
export const CORE_DATASET_DOCUMENT_ROUTE = /^[A-Za-z0-9._/-]+\.json$/;
export const CORE_DATASET_PACK_ROUTE = /^assets\/pack-(\d{3}|[1-9]\d{3,})\.bin$/;
export const CORE_DATASET_PACK_INDEX_ROUTE = /^indexes\/pack-(\d{3}|[1-9]\d{3,})\.bin$/;

function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value, keys) {
  if (!isRecord(value)) return false;
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  return actual.length === expected.length && actual.every((key, index) => key === expected[index]);
}

function isSafeCanonicalPath(value, pattern) {
  return (
    typeof value === 'string' &&
    value.length > 0 &&
    value.length <= MAX_CORE_DATASET_OBJECT_PATH_BYTES &&
    !value.startsWith('/') &&
    !value.includes('\\') &&
    !value.includes('//') &&
    !value.split('/').some(segment => segment === '.' || segment === '..' || segment.length === 0) &&
    pattern.test(value)
  );
}

function requireContentRecord(value, pathPattern, maxBytes, label) {
  if (
    !hasExactKeys(value, ['path', 'bytes', 'sha256']) ||
    !isSafeCanonicalPath(value.path, pathPattern) ||
    !Number.isSafeInteger(value.bytes) ||
    value.bytes <= 0 ||
    value.bytes > maxBytes ||
    !CORE_DATASET_PUBLICATION_ID_PATTERN.test(value.sha256)
  ) {
    throw new Error(`${label} is not a canonical bounded content record.`);
  }
  return value;
}

function canonicalPackNumber(index) {
  return String(index).padStart(3, '0');
}

export function coreDatasetContentRecords(manifest) {
  return [
    ...manifest.documents,
    ...manifest.packs.map(pack => ({path: pack.path, bytes: pack.bytes, sha256: pack.sha256})),
    ...manifest.packs.map(pack => pack.index),
  ].sort((left, right) => (left.path < right.path ? -1 : left.path > right.path ? 1 : 0));
}

/** Strictly validate the immutable core-dataset control manifest. */
export function requireCoreDatasetPublicationManifest(value, expectedPublicationId) {
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
    !CORE_DATASET_PUBLICATION_ID_PATTERN.test(value.publicationId) ||
    (expectedPublicationId !== undefined && value.publicationId !== expectedPublicationId) ||
    value.maxDocumentBytes !== MAX_SHARD_BYTES ||
    value.maxPackBytes !== MAX_PACK_BYTES ||
    value.packIndexFormat !== PACKED_IMAGE_AUTHORIZATION_FORMAT ||
    value.maxPackIndexBytes !== MAX_PACKED_IMAGE_AUTHORIZATION_BYTES ||
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
    throw new Error('Core dataset publication does not satisfy the v1 control-manifest contract.');
  }
  for (const [name, count] of Object.entries(value.counts)) {
    if (!Number.isSafeInteger(count) || count < 0) {
      throw new Error(`Core dataset publication count ${name} must be a non-negative safe integer.`);
    }
  }

  const paths = new Set();
  let previousDocumentPath = '';
  let documentBytes = 0;
  for (const [index, document] of value.documents.entries()) {
    requireContentRecord(document, CORE_DATASET_DOCUMENT_ROUTE, MAX_SHARD_BYTES, `Document ${index}`);
    if (document.path === 'publication.json' || document.path.startsWith('indexes/')) {
      throw new Error(
        `Core dataset document path ${document.path} uses a reserved publication namespace.`,
      );
    }
    if (document.path <= previousDocumentPath || paths.has(document.path)) {
      throw new Error('Core dataset documents must have unique, strictly sorted canonical paths.');
    }
    paths.add(document.path);
    previousDocumentPath = document.path;
    documentBytes += document.bytes;
    if (!Number.isSafeInteger(documentBytes)) {
      throw new Error('Core dataset document bytes exceed the safe integer range.');
    }
  }
  if (!paths.has('manifest.json')) {
    throw new Error('Core dataset documents must include the dataset bootstrap manifest.json.');
  }

  let packBytes = 0;
  let packIndexBytes = 0;
  let packedImages = 0;
  for (const [packNumber, pack] of value.packs.entries()) {
    if (!hasExactKeys(pack, ['path', 'bytes', 'sha256', 'index'])) {
      throw new Error(`Pack ${packNumber} has unsupported or missing fields.`);
    }
    const expectedSuffix = canonicalPackNumber(packNumber);
    requireContentRecord(
      {path: pack.path, bytes: pack.bytes, sha256: pack.sha256},
      CORE_DATASET_PACK_ROUTE,
      MAX_PACK_BYTES,
      `Pack ${packNumber}`,
    );
    if (pack.path !== `assets/pack-${expectedSuffix}.bin` || paths.has(pack.path)) {
      throw new Error(`Pack ${packNumber} does not use its canonical consecutive path.`);
    }
    if (
      !hasExactKeys(pack.index, ['path', 'bytes', 'sha256', 'entries']) ||
      !isSafeCanonicalPath(pack.index.path, CORE_DATASET_PACK_INDEX_ROUTE) ||
      pack.index.path !== `indexes/pack-${expectedSuffix}.bin` ||
      !Number.isSafeInteger(pack.index.bytes) ||
      pack.index.bytes <= 0 ||
      pack.index.bytes > MAX_PACKED_IMAGE_AUTHORIZATION_BYTES ||
      !CORE_DATASET_PUBLICATION_ID_PATTERN.test(pack.index.sha256) ||
      !Number.isSafeInteger(pack.index.entries) ||
      pack.index.entries <= 0 ||
      pack.index.bytes !==
        PACKED_IMAGE_AUTHORIZATION_HEADER_BYTES +
          pack.index.entries * PACKED_IMAGE_AUTHORIZATION_ENTRY_BYTES ||
      paths.has(pack.index.path)
    ) {
      throw new Error(`Pack ${packNumber} has an invalid MRPI authorization record.`);
    }
    paths.add(pack.path);
    paths.add(pack.index.path);
    packBytes += pack.bytes;
    packIndexBytes += pack.index.bytes;
    packedImages += pack.index.entries;
    if (![packBytes, packIndexBytes, packedImages].every(Number.isSafeInteger)) {
      throw new Error('Core dataset pack totals exceed the safe integer range.');
    }
  }

  const objects = value.documents.length + value.packs.length * 2;
  const storedBytes = documentBytes + packBytes + packIndexBytes;
  const expectedCounts = {
    documents: value.documents.length,
    packs: value.packs.length,
    packedImages,
    documentBytes,
    packBytes,
    packIndexBytes,
    objects,
    storedBytes,
  };
  for (const [name, expected] of Object.entries(expectedCounts)) {
    if (value.counts[name] !== expected) {
      throw new Error(
        `Core dataset count ${name} is ${value.counts[name]}; expected ${expected}.`,
      );
    }
  }
  return value;
}

export function isGtnhStructuredDataOnlyCorePublication(manifest) {
  return (
    isRecord(manifest) &&
    manifest.publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY
  );
}

/** Stable compact JSON: recursively sorted object keys, array order preserved, one final newline. */
export function canonicalCoreDatasetPublicationJson(value) {
  if (value === null || typeof value === 'boolean' || typeof value === 'string') {
    return JSON.stringify(value);
  }
  if (typeof value === 'number') {
    if (!Number.isSafeInteger(value)) {
      throw new Error('Canonical core publication JSON accepts only safe integers.');
    }
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map(canonicalCoreDatasetPublicationJson).join(',')}]`;
  }
  if (isRecord(value)) {
    return `{${Object.keys(value)
      .sort()
      .map(
        key =>
          `${JSON.stringify(key)}:${canonicalCoreDatasetPublicationJson(value[key])}`,
      )
      .join(',')}}`;
  }
  throw new Error(`Canonical core publication JSON refuses ${typeof value} values.`);
}

export function coreDatasetPublicationManifestBytes(value) {
  const manifest = requireCoreDatasetPublicationManifest(value);
  const bytes = Buffer.from(`${canonicalCoreDatasetPublicationJson(manifest)}\n`, 'utf8');
  if (bytes.length > MAX_CORE_PUBLICATION_MANIFEST_BYTES) {
    throw new Error(
      `Core dataset publication manifest is ${bytes.length} bytes, above the ` +
        `${MAX_CORE_PUBLICATION_MANIFEST_BYTES}-byte ingestion bound.`,
    );
  }
  return bytes;
}

export function requireCanonicalCoreDatasetPublicationBytes(bytes, expectedPublicationId) {
  if (!Buffer.isBuffer(bytes) || bytes.length <= 0 || bytes.length > MAX_CORE_PUBLICATION_MANIFEST_BYTES) {
    throw new Error('Core dataset publication manifest bytes are absent or outside the ingestion bound.');
  }
  let value;
  try {
    value = JSON.parse(bytes.toString('utf8'));
  } catch (error) {
    throw new Error(`Core dataset publication manifest is invalid JSON: ${error.message}`, {
      cause: error,
    });
  }
  const manifest = requireCoreDatasetPublicationManifest(value, expectedPublicationId);
  const canonical = coreDatasetPublicationManifestBytes(manifest);
  if (!bytes.equals(canonical)) {
    throw new Error('Core dataset publication manifest is not in canonical byte form.');
  }
  return manifest;
}
