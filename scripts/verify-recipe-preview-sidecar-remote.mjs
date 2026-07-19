import {createHash} from 'node:crypto';
import {
  lstat,
  open,
  readFile,
  readdir,
} from 'node:fs/promises';
import {posix, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const SIDECAR_FORMAT = 'mrt-recipe-preview-sidecar-v1';
const CATEGORY_FORMAT = 'mrt-recipe-preview-category-v1';
const PACK_INDEX_FORMAT = 'mrt-recipe-preview-pack-index-v1';
const DATASET_ID_PATTERN = /^[a-f0-9]{64}$/;
const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const MAX_PACK_BYTES = 1024 * 1024;
const MAX_CATEGORY_BYTES = 256 * 1024;
const MAX_PACK_INDEX_BYTES = 512 * 1024;
const PACK_INDEX_MAGIC = Buffer.from('MRPI', 'ascii');
const PACK_INDEX_VERSION = 1;
const PACK_INDEX_HEADER_BYTES = 20;
const PACK_INDEX_ENTRY_BYTES = 8;
const DEFAULT_CONCURRENCY = 8;
const DEFAULT_TIMEOUT_MS = 30_000;
const MAX_CONCURRENCY = 32;
const MAX_TIMEOUT_MS = 120_000;
const RANGE_SAMPLE_BYTES = 256;

function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value, keys) {
  if (!isRecord(value)) return false;
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  return actual.length === expected.length && actual.every((key, index) => key === expected[index]);
}

function assertExactKeys(value, keys, label) {
  if (!hasExactKeys(value, keys)) {
    throw new Error(
      `${label} must contain exactly ${keys.join(', ')}; received ` +
        `${isRecord(value) ? Object.keys(value).join(', ') : typeof value}.`,
    );
  }
}

function assertNonNegativeInteger(value, label) {
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new Error(`${label} must be a non-negative safe integer; received ${value}.`);
  }
}

function assertPositiveInteger(value, label) {
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new Error(`${label} must be a positive safe integer; received ${value}.`);
  }
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function framedHashUpdate(hash, bytes) {
  const buffer = Buffer.isBuffer(bytes) ? bytes : Buffer.from(bytes, 'utf8');
  const length = Buffer.allocUnsafe(8);
  length.writeBigUInt64BE(BigInt(buffer.length));
  hash.update(length).update(buffer);
}

function computeAssetSetId(records, datasetPublicationId) {
  const hash = createHash('sha256');
  hash.update(`${SIDECAR_FORMAT}\0`);
  framedHashUpdate(hash, datasetPublicationId);
  for (const record of [...records].sort((left, right) =>
    left.path < right.path ? -1 : left.path > right.path ? 1 : 0,
  )) {
    framedHashUpdate(hash, record.path);
    framedHashUpdate(hash, Buffer.from(record.sha256, 'hex'));
  }
  return hash.digest('hex');
}

function isSafeRelativePath(value) {
  return (
    typeof value === 'string' &&
    value.length > 0 &&
    !value.startsWith('/') &&
    !value.includes('\\') &&
    posix.normalize(value) === value &&
    value !== '..' &&
    !value.startsWith('../')
  );
}

function resolveInside(root, relativePath, label) {
  if (!isSafeRelativePath(relativePath)) {
    throw new Error(`${label} is not a safe relative POSIX path: ${JSON.stringify(relativePath)}.`);
  }
  const path = resolve(root, ...relativePath.split('/'));
  const prefix = root.endsWith(sep) ? root : `${root}${sep}`;
  if (!path.startsWith(prefix)) {
    throw new Error(`${label} resolves outside the sidecar root: ${relativePath}.`);
  }
  return path;
}

async function assertPlainDirectory(path, label) {
  let info;
  try {
    info = await lstat(path);
  } catch (error) {
    throw new Error(`${label} could not be inspected at ${path}: ${error.message}`, {cause: error});
  }
  if (info.isSymbolicLink() || !info.isDirectory()) {
    throw new Error(`${label} must be a plain directory: ${path}.`);
  }
}

async function assertPlainFile(path, label) {
  let info;
  try {
    info = await lstat(path);
  } catch (error) {
    throw new Error(`${label} could not be inspected at ${path}: ${error.message}`, {cause: error});
  }
  if (info.isSymbolicLink() || !info.isFile()) {
    throw new Error(`${label} must be a plain file: ${path}.`);
  }
  return info;
}

async function listPlainFiles(root, current = root, output = []) {
  const entries = await readdir(current, {withFileTypes: true});
  for (const entry of entries) {
    const path = resolve(current, entry.name);
    if (entry.isSymbolicLink()) {
      throw new Error(`Sidecar inventory refuses symbolic link ${path}.`);
    }
    if (entry.isDirectory()) {
      await listPlainFiles(root, path, output);
    } else if (entry.isFile()) {
      output.push(path.slice(root.length + (root.endsWith(sep) ? 0 : 1)).split(sep).join('/'));
    } else {
      throw new Error(`Sidecar inventory refuses special filesystem entry ${path}.`);
    }
  }
  return output;
}

function parseJson(bytes, label) {
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch (error) {
    throw new Error(`${label} contains invalid JSON: ${error.message}`, {cause: error});
  }
}

function validateContentRecord(record, label, expectedPath) {
  assertExactKeys(record, ['path', 'bytes', 'sha256'], label);
  if (!isSafeRelativePath(record.path)) {
    throw new Error(`${label}.path is not a safe relative path: ${JSON.stringify(record.path)}.`);
  }
  if (expectedPath !== undefined && record.path !== expectedPath) {
    throw new Error(`${label}.path must be ${expectedPath}; received ${record.path}.`);
  }
  assertPositiveInteger(record.bytes, `${label}.bytes`);
  if (!SHA256_PATTERN.test(record.sha256)) {
    throw new Error(`${label}.sha256 must be a lowercase SHA-256 digest.`);
  }
}

function validatePackRecord(pack, index) {
  const label = `Local sidecar manifest.packs[${index}]`;
  assertExactKeys(pack, ['path', 'bytes', 'sha256', 'index'], label);
  const expectedPath = `assets/pack-${String(index).padStart(3, '0')}.bin`;
  validateContentRecord(
    {path: pack.path, bytes: pack.bytes, sha256: pack.sha256},
    label,
    expectedPath,
  );
  if (pack.bytes < 2 || pack.bytes > MAX_PACK_BYTES) {
    throw new Error(
      `${expectedPath} must contain 2..${MAX_PACK_BYTES} bytes so non-whole byte ranges can be verified.`,
    );
  }
  const indexLabel = `${label}.index`;
  assertExactKeys(pack.index, ['path', 'bytes', 'sha256', 'entries'], indexLabel);
  validateContentRecord(
    {path: pack.index.path, bytes: pack.index.bytes, sha256: pack.index.sha256},
    indexLabel,
    `indexes/pack-${String(index).padStart(3, '0')}.bin`,
  );
  assertPositiveInteger(pack.index.entries, `${indexLabel}.entries`);
  const expectedBytes = PACK_INDEX_HEADER_BYTES + pack.index.entries * PACK_INDEX_ENTRY_BYTES;
  if (pack.index.bytes !== expectedBytes || pack.index.bytes > MAX_PACK_INDEX_BYTES) {
    throw new Error(
      `${indexLabel}.bytes must be ${expectedBytes} and no greater than ${MAX_PACK_INDEX_BYTES}.`,
    );
  }
}

function parsePackIndex(bytes, packNumber, pack, label) {
  if (bytes.length !== pack.index.bytes) {
    throw new Error(`${label} contains ${bytes.length} bytes; expected ${pack.index.bytes}.`);
  }
  if (
    !bytes.subarray(0, 4).equals(PACK_INDEX_MAGIC) ||
    bytes.readUInt16BE(4) !== PACK_INDEX_VERSION ||
    bytes.readUInt16BE(6) !== PACK_INDEX_HEADER_BYTES ||
    bytes.readUInt32BE(8) !== packNumber ||
    bytes.readUInt32BE(12) !== pack.bytes ||
    bytes.readUInt32BE(16) !== pack.index.entries
  ) {
    throw new Error(`${label} has an invalid binary header.`);
  }
  const coordinateKeys = new Set();
  let cursor = 0;
  for (let entry = 0; entry < pack.index.entries; entry += 1) {
    const position = PACK_INDEX_HEADER_BYTES + entry * PACK_INDEX_ENTRY_BYTES;
    const offset = bytes.readUInt32BE(position);
    const length = bytes.readUInt32BE(position + 4);
    if (offset !== cursor || length <= 0 || cursor + length > pack.bytes) {
      throw new Error(`${label} entry ${entry} is not a canonical contiguous range.`);
    }
    coordinateKeys.add(`${packNumber}:${offset}:${length}`);
    cursor += length;
  }
  if (cursor !== pack.bytes) {
    throw new Error(`${label} ranges cover ${cursor}/${pack.bytes} pack bytes.`);
  }
  return coordinateKeys;
}

function validateCoordinate(value, packs, label) {
  if (value === null) return null;
  if (!Array.isArray(value) || value.length !== 5 || !value.every(Number.isSafeInteger)) {
    throw new Error(`${label} must be null or a five-integer preview coordinate.`);
  }
  const [packIndex, offset, length, width, height] = value;
  const pack = packs[packIndex];
  if (
    packIndex < 0 ||
    !pack ||
    offset < 0 ||
    length <= 0 ||
    width <= 0 ||
    height <= 0 ||
    offset + length > pack.bytes
  ) {
    throw new Error(`${label} is outside its declared asset pack bounds.`);
  }
  return `${packIndex}:${offset}:${length}`;
}

function validateCategoryRoot(document, categoryIndex, packs, label) {
  if (!isRecord(document)) throw new Error(`${label} must be a JSON object.`);
  const commonKeys = ['format', 'categoryIndex', 'categoryId', 'count'];
  const inline = Object.hasOwn(document, 'previews');
  const sharded = Object.hasOwn(document, 'parts');
  if (inline === sharded) {
    throw new Error(`${label} must contain exactly one of previews or parts.`);
  }
  assertExactKeys(document, [...commonKeys, inline ? 'previews' : 'parts'], label);
  if (document.format !== CATEGORY_FORMAT) {
    throw new Error(`${label}.format must be ${CATEGORY_FORMAT}.`);
  }
  if (document.categoryIndex !== categoryIndex) {
    throw new Error(`${label}.categoryIndex must be ${categoryIndex}.`);
  }
  if (typeof document.categoryId !== 'string' || document.categoryId.length === 0) {
    throw new Error(`${label}.categoryId must be a non-empty string.`);
  }
  assertNonNegativeInteger(document.count, `${label}.count`);
  if (inline) {
    if (!Array.isArray(document.previews) || document.previews.length !== document.count) {
      throw new Error(`${label}.previews must contain exactly ${document.count} entries.`);
    }
    const coordinateKeys = [];
    let previews = 0;
    let encodedBytes = 0;
    for (const [index, coordinate] of document.previews.entries()) {
      const key = validateCoordinate(coordinate, packs, `${label}.previews[${index}]`);
      if (key !== null) {
        previews += 1;
        encodedBytes += coordinate[2];
        coordinateKeys.push(key);
      }
    }
    return {
      categoryId: document.categoryId,
      recipes: document.count,
      previews,
      encodedBytes,
      coordinateKeys,
    };
  }
  if (!Array.isArray(document.parts) || (document.count > 0 && document.parts.length === 0)) {
    throw new Error(`${label}.parts must be a non-empty array when the category has recipes.`);
  }
  let covered = 0;
  for (const [partIndex, part] of document.parts.entries()) {
    assertExactKeys(part, ['path', 'start', 'count', 'bytes'], `${label}.parts[${partIndex}]`);
    const expectedPath =
      `categories/${String(categoryIndex).padStart(3, '0')}/` +
      `part-${String(partIndex).padStart(3, '0')}.json`;
    if (part.path !== expectedPath) {
      throw new Error(`${label}.parts[${partIndex}].path must be ${expectedPath}.`);
    }
    if (part.start !== covered) {
      throw new Error(`${label}.parts[${partIndex}].start must be ${covered}.`);
    }
    assertPositiveInteger(part.count, `${label}.parts[${partIndex}].count`);
    assertPositiveInteger(part.bytes, `${label}.parts[${partIndex}].bytes`);
    covered += part.count;
  }
  if (covered !== document.count) {
    throw new Error(`${label}.parts cover ${covered}/${document.count} recipes.`);
  }
  return {categoryId: document.categoryId, recipes: document.count, parts: document.parts};
}

function validateManifestShape(manifest) {
  const manifestKeys = [
    'format',
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
  assertExactKeys(manifest, manifestKeys, 'Local sidecar manifest');
  if (manifest.format !== SIDECAR_FORMAT) {
    throw new Error(`Local sidecar manifest.format must be ${SIDECAR_FORMAT}.`);
  }
  if (!DATASET_ID_PATTERN.test(manifest.assetSetId)) {
    throw new Error('Local sidecar manifest.assetSetId must be a lowercase SHA-256 digest.');
  }
  if (!DATASET_ID_PATTERN.test(manifest.datasetPublicationId)) {
    throw new Error(
      'Local sidecar manifest.datasetPublicationId must be a lowercase SHA-256 digest.',
    );
  }
  if (manifest.maxPackBytes !== MAX_PACK_BYTES) {
    throw new Error(`Local sidecar manifest.maxPackBytes must be exactly ${MAX_PACK_BYTES}.`);
  }
  if (manifest.packIndexFormat !== PACK_INDEX_FORMAT) {
    throw new Error(`Local sidecar manifest.packIndexFormat must be ${PACK_INDEX_FORMAT}.`);
  }
  if (manifest.maxPackIndexBytes !== MAX_PACK_INDEX_BYTES) {
    throw new Error(
      `Local sidecar manifest.maxPackIndexBytes must be exactly ${MAX_PACK_INDEX_BYTES}.`,
    );
  }
  if (manifest.imageFormat !== 'lossless-webp') {
    throw new Error('Local sidecar manifest.imageFormat must be lossless-webp.');
  }
  if (manifest.categoryFormat !== CATEGORY_FORMAT) {
    throw new Error(`Local sidecar manifest.categoryFormat must be ${CATEGORY_FORMAT}.`);
  }
  assertExactKeys(
    manifest.settings,
    ['itemIconPixels', 'recipeScale', 'webpEffort', 'maxCategoryBytes'],
    'Local sidecar manifest.settings',
  );
  if (
    !Number.isSafeInteger(manifest.settings.itemIconPixels) ||
    manifest.settings.itemIconPixels < 16 ||
    manifest.settings.itemIconPixels % 16 !== 0 ||
    !Number.isSafeInteger(manifest.settings.recipeScale) ||
    manifest.settings.recipeScale <= 0 ||
    manifest.settings.webpEffort !== 4
  ) {
    throw new Error(
      'Local sidecar manifest.settings must use a positive 16-pixel-grid-aligned item canvas, ' +
        'a positive recipeScale, and lossless-WebP effort 4.',
    );
  }
  if (
    !Number.isSafeInteger(manifest.settings.maxCategoryBytes) ||
    manifest.settings.maxCategoryBytes < 256 ||
    manifest.settings.maxCategoryBytes > MAX_CATEGORY_BYTES
  ) {
    throw new Error(
      `Local sidecar manifest.settings.maxCategoryBytes must be within 256..${MAX_CATEGORY_BYTES}.`,
    );
  }

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
  assertExactKeys(manifest.counts, countKeys, 'Local sidecar manifest.counts');
  for (const key of countKeys) {
    assertNonNegativeInteger(manifest.counts[key], `Local sidecar manifest.counts.${key}`);
  }
  if (manifest.counts.previews + manifest.counts.missing !== manifest.counts.recipes) {
    throw new Error('Local sidecar preview and missing counts do not sum to recipes.');
  }
  if (manifest.counts.uniqueImages + manifest.counts.duplicates !== manifest.counts.previews) {
    throw new Error('Local sidecar unique-image and duplicate counts do not sum to previews.');
  }

  if (!Array.isArray(manifest.packs) || manifest.packs.length !== manifest.counts.packs) {
    throw new Error('Local sidecar manifest.packs length does not match counts.packs.');
  }
  for (const [index, pack] of manifest.packs.entries()) {
    validatePackRecord(pack, index);
  }
  const storedBytes = manifest.packs.reduce((sum, pack) => sum + pack.bytes, 0);
  if (!Number.isSafeInteger(storedBytes) || storedBytes !== manifest.counts.storedBytes) {
    throw new Error('Local sidecar pack byte total does not match counts.storedBytes.');
  }
  const packIndexBytes = manifest.packs.reduce((sum, pack) => sum + pack.index.bytes, 0);
  const indexedImages = manifest.packs.reduce((sum, pack) => sum + pack.index.entries, 0);
  if (
    !Number.isSafeInteger(packIndexBytes) ||
    packIndexBytes !== manifest.counts.packIndexBytes ||
    !Number.isSafeInteger(indexedImages) ||
    indexedImages !== manifest.counts.uniqueImages
  ) {
    throw new Error('Local sidecar pack-index totals are inconsistent.');
  }

  assertExactKeys(
    manifest.mapping,
    ['documents', 'parts', 'bytes'],
    'Local sidecar manifest.mapping',
  );
  for (const key of ['documents', 'parts', 'bytes']) {
    assertNonNegativeInteger(manifest.mapping[key], `Local sidecar manifest.mapping.${key}`);
  }
  if (
    !Array.isArray(manifest.categoryDocuments) ||
    manifest.categoryDocuments.length !== manifest.mapping.documents
  ) {
    throw new Error(
      'Local sidecar manifest.categoryDocuments length does not match mapping.documents.',
    );
  }
  let previousPath = '';
  for (const [index, document] of manifest.categoryDocuments.entries()) {
    validateContentRecord(document, `Local sidecar manifest.categoryDocuments[${index}]`);
    if (!document.path.startsWith('categories/') || !document.path.endsWith('.json')) {
      throw new Error(`Category document has invalid path ${document.path}.`);
    }
    if (document.path <= previousPath) {
      throw new Error('Local sidecar category document records must be strictly path-sorted.');
    }
    if (document.bytes > manifest.settings.maxCategoryBytes) {
      throw new Error(`${document.path} exceeds settings.maxCategoryBytes.`);
    }
    previousPath = document.path;
  }
  const mappingBytes = manifest.categoryDocuments.reduce((sum, document) => sum + document.bytes, 0);
  if (!Number.isSafeInteger(mappingBytes) || mappingBytes !== manifest.mapping.bytes) {
    throw new Error('Local sidecar category document byte total does not match mapping.bytes.');
  }
}

async function readAndVerifyRecord(root, record, label) {
  const path = resolveInside(root, record.path, `${label} path`);
  const info = await assertPlainFile(path, label);
  if (info.size !== record.bytes) {
    throw new Error(`${label} is ${info.size} bytes locally; manifest declares ${record.bytes}.`);
  }
  const bytes = await readFile(path);
  if (sha256(bytes) !== record.sha256) {
    throw new Error(`${label} failed its local SHA-256 digest.`);
  }
  return bytes;
}

async function mapConcurrent(values, concurrency, operation) {
  const results = new Array(values.length);
  let next = 0;
  let stopped = false;
  async function worker() {
    for (;;) {
      if (stopped) return;
      const index = next;
      next += 1;
      if (index >= values.length) return;
      try {
        results[index] = await operation(values[index], index);
      } catch (error) {
        stopped = true;
        throw error;
      }
    }
  }
  await Promise.all(Array.from({length: Math.min(concurrency, values.length)}, () => worker()));
  return results;
}

export async function validateLocalRecipePreviewSidecar(local, concurrency = DEFAULT_CONCURRENCY) {
  if (!Number.isSafeInteger(concurrency) || concurrency <= 0 || concurrency > MAX_CONCURRENCY) {
    throw new Error(`concurrency must be within 1..${MAX_CONCURRENCY}.`);
  }
  const root = resolve(local);
  await assertPlainDirectory(root, 'Local sidecar root');
  const manifestPath = resolveInside(root, 'manifest.json', 'Local manifest path');
  await assertPlainFile(manifestPath, 'Local sidecar manifest');
  const manifestBytes = await readFile(manifestPath);
  const manifest = parseJson(manifestBytes, 'Local sidecar manifest');
  validateManifestShape(manifest);

  const packIndexRecords = manifest.packs.map(pack => pack.index);
  const records = [...manifest.packs, ...packIndexRecords, ...manifest.categoryDocuments];
  const declaredPaths = new Set(['manifest.json']);
  for (const record of records) {
    if (declaredPaths.has(record.path)) {
      throw new Error(`Local sidecar manifest declares duplicate path ${record.path}.`);
    }
    declaredPaths.add(record.path);
  }
  const inventory = (await listPlainFiles(root)).sort();
  const expectedInventory = [...declaredPaths].sort();
  if (
    inventory.length !== expectedInventory.length ||
    !inventory.every((path, index) => path === expectedInventory[index])
  ) {
    const unexpected = inventory.filter(path => !declaredPaths.has(path));
    const missing = expectedInventory.filter(path => !inventory.includes(path));
    throw new Error(
      `Local sidecar file inventory differs from the manifest; ` +
        `unexpected=${JSON.stringify(unexpected)}, missing=${JSON.stringify(missing)}.`,
    );
  }

  await mapConcurrent(manifest.packs, concurrency, async (record, index) => {
    await readAndVerifyRecord(root, record, `Local asset pack ${index}`);
  });
  const packIndexBytes = await mapConcurrent(
    packIndexRecords,
    concurrency,
    (record, index) => readAndVerifyRecord(root, record, `Local pack authorization index ${index}`),
  );
  const indexedCoordinateKeys = new Set();
  for (const [packIndex, bytes] of packIndexBytes.entries()) {
    for (const key of parsePackIndex(
      bytes,
      packIndex,
      manifest.packs[packIndex],
      `Local pack authorization index ${packIndex}`,
    )) {
      indexedCoordinateKeys.add(key);
    }
  }
  const categoryBytes = await mapConcurrent(
    manifest.categoryDocuments,
    concurrency,
    (record, index) => readAndVerifyRecord(root, record, `Local category document ${index}`),
  );
  const categoriesByPath = new Map(
    manifest.categoryDocuments.map((record, index) => [record.path, categoryBytes[index]]),
  );

  let recipeCount = 0;
  let previewCount = 0;
  let encodedBytes = 0;
  let partCount = 0;
  const coordinateKeys = new Set();
  const categoryIds = new Set();
  const usedDocuments = new Set();
  for (let categoryIndex = 0; categoryIndex < manifest.counts.categories; categoryIndex += 1) {
    const name = String(categoryIndex).padStart(3, '0');
    const rootPath = `categories/${name}.json`;
    const rootBytes = categoriesByPath.get(rootPath);
    if (!rootBytes) throw new Error(`Local sidecar is missing category root ${rootPath}.`);
    usedDocuments.add(rootPath);
    const category = validateCategoryRoot(
      parseJson(rootBytes, `Local category root ${rootPath}`),
      categoryIndex,
      manifest.packs,
      `Local category root ${rootPath}`,
    );
    if (categoryIds.has(category.categoryId)) {
      throw new Error(`Local category id is duplicated: ${category.categoryId}.`);
    }
    categoryIds.add(category.categoryId);
    recipeCount += category.recipes;
    if (category.parts) {
      partCount += category.parts.length;
      for (const [partIndex, part] of category.parts.entries()) {
        const bytes = categoriesByPath.get(part.path);
        if (!bytes) throw new Error(`Local sidecar is missing category shard ${part.path}.`);
        usedDocuments.add(part.path);
        if (bytes.length !== part.bytes) {
          throw new Error(`${part.path} byte length disagrees with its category descriptor.`);
        }
        const previews = parseJson(bytes, `Local category shard ${part.path}`);
        if (!Array.isArray(previews) || previews.length !== part.count) {
          throw new Error(`${part.path} must contain exactly ${part.count} preview entries.`);
        }
        for (const [entryIndex, coordinate] of previews.entries()) {
          const key = validateCoordinate(
            coordinate,
            manifest.packs,
            `Local category shard ${part.path}[${entryIndex}]`,
          );
          if (key !== null) {
            previewCount += 1;
            encodedBytes += coordinate[2];
            coordinateKeys.add(key);
          }
        }
      }
    } else {
      previewCount += category.previews;
      encodedBytes += category.encodedBytes;
      for (const key of category.coordinateKeys) coordinateKeys.add(key);
    }
  }
  if (usedDocuments.size !== manifest.categoryDocuments.length) {
    const unused = manifest.categoryDocuments
      .map(record => record.path)
      .filter(path => !usedDocuments.has(path));
    throw new Error(`Local sidecar declares unreferenced category documents: ${unused.join(', ')}.`);
  }
  if (recipeCount !== manifest.counts.recipes || previewCount !== manifest.counts.previews) {
    throw new Error(
      `Local category mappings contain ${recipeCount} recipes/${previewCount} previews; manifest ` +
        `declares ${manifest.counts.recipes}/${manifest.counts.previews}.`,
    );
  }
  if (coordinateKeys.size !== manifest.counts.uniqueImages) {
    throw new Error(
      `Local category mappings reference ${coordinateKeys.size} unique coordinates; manifest ` +
        `declares ${manifest.counts.uniqueImages}.`,
    );
  }
  if (
    indexedCoordinateKeys.size !== coordinateKeys.size ||
    [...coordinateKeys].some(key => !indexedCoordinateKeys.has(key))
  ) {
    throw new Error(
      'Local pack authorization indexes do not exactly match the published preview coordinates.',
    );
  }
  if (encodedBytes !== manifest.counts.encodedBytes) {
    throw new Error(
      `Local category mappings reference ${encodedBytes} logical encoded bytes; manifest ` +
        `declares ${manifest.counts.encodedBytes}.`,
    );
  }
  const rangesByPack = Array.from({length: manifest.packs.length}, () => []);
  for (const key of coordinateKeys) {
    const [pack, offset, length] = key.split(':', 3).map(Number);
    rangesByPack[pack].push({offset, length});
  }
  for (const [packIndex, ranges] of rangesByPack.entries()) {
    ranges.sort((left, right) => left.offset - right.offset || left.length - right.length);
    let cursor = 0;
    for (const range of ranges) {
      if (range.offset !== cursor) {
        throw new Error(
          `Local asset pack ${packIndex} has a gap, overlap, or conflicting coordinate at ` +
            `${range.offset}; expected ${cursor}.`,
        );
      }
      cursor += range.length;
    }
    if (cursor !== manifest.packs[packIndex].bytes) {
      throw new Error(
        `Local asset pack ${packIndex} coordinates cover ${cursor}/${manifest.packs[packIndex].bytes} bytes.`,
      );
    }
  }
  if (partCount !== manifest.mapping.parts) {
    throw new Error(
      `Local category mappings contain ${partCount} parts; manifest declares ${manifest.mapping.parts}.`,
    );
  }
  const computedAssetSetId = computeAssetSetId(records, manifest.datasetPublicationId);
  if (computedAssetSetId !== manifest.assetSetId) {
    throw new Error(
      `Local sidecar assetSetId is ${manifest.assetSetId}; content computes to ${computedAssetSetId}.`,
    );
  }
  return {root, manifest, manifestBytes, categoryBytes, packIndexBytes};
}

function validateOptions({local, baseUrl, mode, concurrency, timeoutMs, logger, fetchImpl}) {
  if (typeof local !== 'string' || local.length === 0) {
    throw new Error('local must be a non-empty sidecar directory path.');
  }
  if (mode !== 'precommit' && mode !== 'committed') {
    throw new Error('mode must be either precommit or committed.');
  }
  if (!Number.isSafeInteger(concurrency) || concurrency <= 0 || concurrency > MAX_CONCURRENCY) {
    throw new Error(`concurrency must be within 1..${MAX_CONCURRENCY}.`);
  }
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs <= 0 || timeoutMs > MAX_TIMEOUT_MS) {
    throw new Error(`timeoutMs must be within 1..${MAX_TIMEOUT_MS}.`);
  }
  if (!isRecord(logger) || !['info', 'warn', 'error'].every(name => typeof logger[name] === 'function')) {
    throw new Error('logger must provide info, warn, and error functions.');
  }
  if (typeof fetchImpl !== 'function') throw new Error('fetchImpl must be a function.');
  let parsed;
  try {
    parsed = new URL(baseUrl);
  } catch (error) {
    throw new Error(`baseUrl must be an absolute URL: ${error.message}`, {cause: error});
  }
  return parsed;
}

function normalizeBaseUrl(url, allowHttpForTests) {
  if (url.protocol !== 'https:' && !(allowHttpForTests && url.protocol === 'http:')) {
    throw new Error(`Public bucket base URL must use credential-free HTTPS; received ${url.protocol}.`);
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new Error('Public bucket base URL must not contain credentials, query parameters, or a hash.');
  }
  url.pathname = url.pathname.replace(/\/+$/, '');
  return url;
}

function objectUrl(baseUrl, assetSetId, relativePath) {
  const segments = [assetSetId, ...relativePath.split('/')].map(encodeURIComponent);
  return new URL(`${baseUrl.pathname}/${segments.join('/')}`, baseUrl.origin);
}

async function fetchWithTimeout(fetchImpl, url, init, timeoutMs, label) {
  let response;
  try {
    response = await fetchImpl(url, {
      ...init,
      cache: 'no-store',
      redirect: 'error',
      signal: AbortSignal.timeout(timeoutMs),
      headers: {
        'accept-encoding': 'identity',
        'cache-control': 'no-cache',
        ...init.headers,
      },
    });
  } catch (error) {
    throw new Error(`${label} request failed for ${url}: ${error.message}`, {cause: error});
  }
  return response;
}

function parseContentLength(response, expected, label) {
  const raw = response.headers.get('content-length');
  if (!/^(0|[1-9]\d*)$/.test(raw ?? '')) {
    throw new Error(`${label} did not provide one valid Content-Length header.`);
  }
  const received = Number(raw);
  if (!Number.isSafeInteger(received) || received !== expected) {
    throw new Error(`${label} Content-Length is ${raw}; expected ${expected}.`);
  }
}

function assertIdentityEncoding(response, label) {
  const encoding = response.headers.get('content-encoding');
  if (encoding && encoding.toLowerCase() !== 'identity') {
    throw new Error(`${label} returned Content-Encoding ${encoding}; exact stored bytes are required.`);
  }
}

async function cancelBody(response) {
  try {
    await response.body?.cancel();
  } catch {
    // The status/header error remains authoritative; cancellation is only resource cleanup.
  }
}

async function readExactResponseBody(response, expected, label) {
  if (!response.body) {
    if (expected === 0) return Buffer.alloc(0);
    throw new Error(`${label} returned no response body.`);
  }
  const reader = response.body.getReader();
  const chunks = [];
  let bytes = 0;
  try {
    for (;;) {
      const {done, value} = await reader.read();
      if (done) break;
      bytes += value.byteLength;
      if (bytes > expected) {
        await reader.cancel();
        throw new Error(`${label} response body exceeded the declared ${expected}-byte bound.`);
      }
      chunks.push(Buffer.from(value.buffer, value.byteOffset, value.byteLength));
    }
  } finally {
    reader.releaseLock();
  }
  if (bytes !== expected) {
    throw new Error(`${label} returned ${bytes} body bytes; expected ${expected}.`);
  }
  return Buffer.concat(chunks, bytes);
}

async function digestExactResponseBody(response, expected, label) {
  if (!response.body) throw new Error(`${label} returned no response body.`);
  const reader = response.body.getReader();
  const hash = createHash('sha256');
  let bytes = 0;
  try {
    for (;;) {
      const {done, value} = await reader.read();
      if (done) break;
      bytes += value.byteLength;
      if (bytes > expected) {
        await reader.cancel();
        throw new Error(`${label} response body exceeded the declared ${expected}-byte bound.`);
      }
      hash.update(Buffer.from(value.buffer, value.byteOffset, value.byteLength));
    }
  } finally {
    reader.releaseLock();
  }
  if (bytes !== expected) {
    throw new Error(`${label} returned ${bytes} body bytes; expected ${expected}.`);
  }
  return hash.digest('hex');
}

async function verifyCommitMarker({localState, baseUrl, mode, fetchImpl, timeoutMs}) {
  const url = objectUrl(baseUrl, localState.manifest.assetSetId, 'manifest.json');
  if (mode === 'precommit') {
    const response = await fetchWithTimeout(fetchImpl, url, {method: 'HEAD'}, timeoutMs, 'Commit marker');
    await cancelBody(response);
    if (response.status !== 404) {
      throw new Error(
        `Precommit requires remote manifest.json to be absent (HTTP 404); received HTTP ` +
          `${response.status} at ${url}.`,
      );
    }
    return;
  }

  const response = await fetchWithTimeout(fetchImpl, url, {method: 'GET'}, timeoutMs, 'Commit marker');
  if (response.status !== 200) {
    await cancelBody(response);
    throw new Error(`Committed manifest request returned HTTP ${response.status} at ${url}.`);
  }
  assertIdentityEncoding(response, 'Committed manifest');
  parseContentLength(response, localState.manifestBytes.length, 'Committed manifest');
  const bytes = await readExactResponseBody(
    response,
    localState.manifestBytes.length,
    'Committed manifest',
  );
  if (!bytes.equals(localState.manifestBytes)) {
    throw new Error('Committed remote manifest is not byte-for-byte identical to the local manifest.');
  }
}

async function verifyRemoteHead({record, baseUrl, assetSetId, fetchImpl, timeoutMs}) {
  const url = objectUrl(baseUrl, assetSetId, record.path);
  const response = await fetchWithTimeout(fetchImpl, url, {method: 'HEAD'}, timeoutMs, `HEAD ${record.path}`);
  if (response.status !== 200) {
    await cancelBody(response);
    throw new Error(`HEAD ${record.path} returned HTTP ${response.status} at ${url}.`);
  }
  assertIdentityEncoding(response, `HEAD ${record.path}`);
  parseContentLength(response, record.bytes, `HEAD ${record.path}`);
  await cancelBody(response);
}

async function verifyRemoteSmallObject({record, localBytes, baseUrl, assetSetId, fetchImpl, timeoutMs}) {
  const url = objectUrl(baseUrl, assetSetId, record.path);
  const response = await fetchWithTimeout(fetchImpl, url, {method: 'GET'}, timeoutMs, `GET ${record.path}`);
  if (response.status !== 200) {
    await cancelBody(response);
    throw new Error(`GET ${record.path} returned HTTP ${response.status} at ${url}.`);
  }
  assertIdentityEncoding(response, `GET ${record.path}`);
  parseContentLength(response, record.bytes, `GET ${record.path}`);
  const bytes = await readExactResponseBody(response, record.bytes, `GET ${record.path}`);
  if (sha256(bytes) !== record.sha256 || !bytes.equals(localBytes)) {
    throw new Error(`GET ${record.path} failed its exact remote SHA-256/byte comparison.`);
  }
}

async function verifyRemotePackDigest({pack, baseUrl, assetSetId, fetchImpl, timeoutMs}) {
  const url = objectUrl(baseUrl, assetSetId, pack.path);
  const label = `Full digest ${pack.path}`;
  const response = await fetchWithTimeout(fetchImpl, url, {method: 'GET'}, timeoutMs, label);
  if (response.status !== 200) {
    await cancelBody(response);
    throw new Error(`${label} returned HTTP ${response.status} at ${url}.`);
  }
  assertIdentityEncoding(response, label);
  parseContentLength(response, pack.bytes, label);
  const digest = await digestExactResponseBody(response, pack.bytes, label);
  if (digest !== pack.sha256) {
    throw new Error(`${label} SHA-256 is ${digest}; manifest declares ${pack.sha256}.`);
  }
}

function deterministicRangeSamples(packBytes) {
  const length = Math.min(RANGE_SAMPLE_BYTES, Math.max(1, Math.floor(packBytes / 4)));
  const starts = [0, Math.floor((packBytes - length) / 2), packBytes - length];
  return [...new Set(starts)].map(start => ({start, end: start + length - 1, length}));
}

async function readLocalRange(root, path, start, length) {
  const file = await open(resolveInside(root, path, 'Local pack sample path'), 'r');
  try {
    const output = Buffer.allocUnsafe(length);
    const {bytesRead} = await file.read(output, 0, length, start);
    if (bytesRead !== length) {
      throw new Error(`Local pack ${path} yielded ${bytesRead}/${length} sample bytes.`);
    }
    return output;
  } finally {
    await file.close();
  }
}

async function verifyRemoteRange({pack, sample, root, baseUrl, assetSetId, fetchImpl, timeoutMs}) {
  const url = objectUrl(baseUrl, assetSetId, pack.path);
  const label = `Range ${pack.path} bytes=${sample.start}-${sample.end}`;
  const response = await fetchWithTimeout(
    fetchImpl,
    url,
    {method: 'GET', headers: {range: `bytes=${sample.start}-${sample.end}`}},
    timeoutMs,
    label,
  );
  if (response.status !== 206) {
    await cancelBody(response);
    throw new Error(`${label} requires HTTP 206; received HTTP ${response.status} at ${url}.`);
  }
  assertIdentityEncoding(response, label);
  const contentRange = response.headers.get('content-range');
  const expectedContentRange = `bytes ${sample.start}-${sample.end}/${pack.bytes}`;
  if (contentRange !== expectedContentRange) {
    await cancelBody(response);
    throw new Error(
      `${label} Content-Range is ${JSON.stringify(contentRange)}; expected ` +
        `${JSON.stringify(expectedContentRange)}.`,
    );
  }
  parseContentLength(response, sample.length, label);
  const [remoteBytes, localBytes] = await Promise.all([
    readExactResponseBody(response, sample.length, label),
    readLocalRange(root, pack.path, sample.start, sample.length),
  ]);
  if (!remoteBytes.equals(localBytes)) {
    throw new Error(`${label} does not match the local pack bytes.`);
  }
}

export async function verifyRemoteRecipePreviewSidecar({
  local,
  baseUrl,
  mode,
  concurrency = DEFAULT_CONCURRENCY,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  logger = console,
  fetchImpl = globalThis.fetch,
  allowHttpForTests = false,
}) {
  const parsedBaseUrl = validateOptions({
    local,
    baseUrl,
    mode,
    concurrency,
    timeoutMs,
    logger,
    fetchImpl,
  });
  const normalizedBaseUrl = normalizeBaseUrl(parsedBaseUrl, allowHttpForTests);
  logger.info(`Validating local recipe preview sidecar at ${resolve(local)}.`);
  try {
    const localState = await validateLocalRecipePreviewSidecar(local, concurrency);
    const {manifest} = localState;
    logger.info(
      `Validated local sidecar ${manifest.assetSetId}: ${manifest.packs.length} packs and ` +
        `${manifest.categoryDocuments.length} category documents.`,
    );
    await verifyCommitMarker({
      localState,
      baseUrl: normalizedBaseUrl,
      mode,
      fetchImpl,
      timeoutMs,
    });
    logger.info(
      mode === 'precommit'
        ? 'Verified that the remote manifest commit marker is absent.'
        : 'Verified the committed remote manifest byte-for-byte.',
    );

    const packIndexRecords = manifest.packs.map(pack => pack.index);
    const records = [...manifest.packs, ...packIndexRecords, ...manifest.categoryDocuments];
    await mapConcurrent(records, concurrency, record =>
      verifyRemoteHead({
        record,
        baseUrl: normalizedBaseUrl,
        assetSetId: manifest.assetSetId,
        fetchImpl,
        timeoutMs,
      }),
    );
    logger.info(`Verified exact remote Content-Length for ${records.length} immutable objects.`);

    await mapConcurrent(manifest.categoryDocuments, concurrency, (record, index) =>
      verifyRemoteSmallObject({
        record,
        localBytes: localState.categoryBytes[index],
        baseUrl: normalizedBaseUrl,
        assetSetId: manifest.assetSetId,
        fetchImpl,
        timeoutMs,
      }),
    );
    logger.info(
      `Downloaded and digest-verified ${manifest.categoryDocuments.length} category documents.`,
    );
    await mapConcurrent(packIndexRecords, concurrency, (record, index) =>
      verifyRemoteSmallObject({
        record,
        localBytes: localState.packIndexBytes[index],
        baseUrl: normalizedBaseUrl,
        assetSetId: manifest.assetSetId,
        fetchImpl,
        timeoutMs,
      }),
    );
    logger.info(
      `Downloaded and digest-verified ${packIndexRecords.length} pack authorization indexes.`,
    );

    let fullyHashedPacks = 0;
    if (mode === 'precommit') {
      await mapConcurrent(manifest.packs, concurrency, pack =>
        verifyRemotePackDigest({
          pack,
          baseUrl: normalizedBaseUrl,
          assetSetId: manifest.assetSetId,
          fetchImpl,
          timeoutMs,
        }),
      );
      fullyHashedPacks = manifest.packs.length;
      logger.info(
        `Streamed and SHA-256-verified all ${fullyHashedPacks} remote packs before publication.`,
      );
    } else {
      logger.info(
        'Committed verification intentionally performs zero full pack downloads; the exact ' +
          'manifest is the commit marker for packs cryptographically verified during precommit.',
      );
    }

    const rangeJobs = manifest.packs.flatMap(pack =>
      deterministicRangeSamples(pack.bytes).map(sample => ({pack, sample})),
    );
    await mapConcurrent(rangeJobs, concurrency, ({pack, sample}) =>
      verifyRemoteRange({
        pack,
        sample,
        root: localState.root,
        baseUrl: normalizedBaseUrl,
        assetSetId: manifest.assetSetId,
        fetchImpl,
        timeoutMs,
      }),
    );
    logger.info(
      `Verified ${rangeJobs.length} exact HTTP 206 byte-range samples across ` +
        `${manifest.packs.length} packs.`,
    );
    logger.info(
      `Remote recipe preview sidecar ${manifest.assetSetId} passed ${mode} verification.`,
    );
    return {
      mode,
      assetSetId: manifest.assetSetId,
      datasetPublicationId: manifest.datasetPublicationId,
      packs: manifest.packs.length,
      packIndexes: manifest.packs.length,
      categoryDocuments: manifest.categoryDocuments.length,
      fullyHashedPacks,
      rangeSamples: rangeJobs.length,
    };
  } catch (error) {
    logger.error(`Remote recipe preview sidecar ${mode} verification failed: ${error.message}`);
    throw error;
  }
}

function parseCliArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    if (flag === '--precommit' || flag === '--committed') {
      if (options.mode) throw new Error('Choose exactly one of --precommit or --committed.');
      options.mode = flag.slice(2);
      continue;
    }
    const names = new Map([
      ['--local', 'local'],
      ['--base-url', 'baseUrl'],
      ['--concurrency', 'concurrency'],
      ['--timeout-ms', 'timeoutMs'],
    ]);
    const name = names.get(flag);
    if (!name) throw new Error(`Unknown argument ${flag}.`);
    const value = argv[index + 1];
    if (value === undefined || value.startsWith('--')) {
      throw new Error(`Argument ${flag} requires a value.`);
    }
    if (options[name] !== undefined) throw new Error(`${flag} was provided more than once.`);
    options[name] = name === 'concurrency' || name === 'timeoutMs' ? Number(value) : value;
    index += 1;
  }
  if (!options.local || !options.baseUrl || !options.mode) {
    throw new Error(
      'Usage: node scripts/verify-recipe-preview-sidecar-remote.mjs ' +
        '--local <sidecar-root> --base-url <public-bucket-path-before-assetSetId> ' +
        '(--precommit | --committed) [--concurrency <1-32>] [--timeout-ms <1-120000>]',
    );
  }
  return options;
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath === fileURLToPath(import.meta.url)) {
  try {
    await verifyRemoteRecipePreviewSidecar(parseCliArgs(process.argv.slice(2)));
  } catch (error) {
    console.error(`Recipe preview remote verifier terminated: ${error.message}`);
    process.exitCode = 1;
  }
}
