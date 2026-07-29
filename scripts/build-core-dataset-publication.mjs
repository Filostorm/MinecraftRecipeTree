import {availableParallelism} from 'node:os';
import {createHash, randomUUID} from 'node:crypto';
import {
  lstat,
  mkdir,
  readFile,
  realpath,
  rename,
  rm,
  writeFile,
} from 'node:fs/promises';
import {basename, dirname, join, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {
  CORE_DATASET_PUBLICATION_FORMAT,
  CORE_DATASET_PUBLICATION_ID_PATTERN,
  GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY,
  MAX_CORE_PUBLICATION_MANIFEST_BYTES,
  coreDatasetContentRecords,
  coreDatasetPublicationManifestBytes,
  requireCanonicalCoreDatasetPublicationBytes,
  requireCoreDatasetPublicationManifest,
} from './core-dataset-publication-contract.mjs';
import {collectFiles} from './export-data-utils.mjs';
import {
  MAX_PACKED_IMAGE_AUTHORIZATION_BYTES,
  PACKED_IMAGE_AUTHORIZATION_FORMAT,
  encodePackedImageAuthorizationIndex,
  parsePackedImageAuthorizationIndex,
} from './packed-image-authorization.mjs';
import {
  MAX_PACK_BYTES,
  packFileKey,
  packedImagePath,
  parsePackedImagePath,
} from './packed-assets.mjs';
import {computePublicationId} from './publication-id.mjs';
import {requireRecipeImageInventory} from './recipe-image-inventory.mjs';
import {MAX_SHARD_BYTES} from './sharded-documents.mjs';

const DEFAULT_CONCURRENCY = Math.max(1, Math.min(8, availableParallelism()));
const MAX_CONCURRENCY = 32;
const GTNH_STRUCTURED_DATA_ONLY_EXCLUSION_REASON =
  'third-party-artwork-rights-not-cleared';
const GTNH_STRUCTURED_DATA_ONLY_VISUAL_ASSETS = Object.freeze({
  format: 'mrt-visual-assets-policy-v1',
  mode: 'structured-data-only',
  policy: GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY,
  itemIcons: 0,
  categoryIcons: 0,
  recipePreviews: 0,
  mobSprites: 0,
  packedImageFiles: 0,
});
const GTNH_DATA_ATTRIBUTION = Object.freeze({
  sourceUrl: 'https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/tree/2.8.4',
  projectUrl: 'https://www.gtnewhorizons.com/',
  licenseIdentifier: 'CC BY-NC-SA 4.0',
  licenseUrl: 'https://creativecommons.org/licenses/by-nc-sa/4.0/',
});
const GTNH_PACK_IDENTITY = Object.freeze({
  name: 'GT New Horizons',
  version: '2.8.4',
  identitySource: 'explicit-request',
});

function isRecord(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactPrimitiveRecord(value, expected) {
  if (!isRecord(value)) return false;
  const actualKeys = Object.keys(value).sort();
  const expectedKeys = Object.keys(expected).sort();
  return (
    actualKeys.length === expectedKeys.length &&
    actualKeys.every((key, index) =>
      key === expectedKeys[index] && value[key] === expected[key],
    )
  );
}

function hasExactKeys(value, expectedKeys) {
  if (!isRecord(value)) return false;
  const actualKeys = Object.keys(value).sort();
  const sortedExpectedKeys = [...expectedKeys].sort();
  return (
    actualKeys.length === sortedExpectedKeys.length &&
    actualKeys.every((key, index) => key === sortedExpectedKeys[index])
  );
}

function requirePackedDatasetPublicationPolicy(manifest) {
  const hasPolicy = isRecord(manifest) && Object.hasOwn(manifest, 'publicationPolicy');
  const web = isRecord(manifest?.web) ? manifest.web : undefined;
  const visualAssets = web?.visualAssets;
  const recipeImages = web?.recipeImages;
  if (!hasPolicy) {
    if (
      visualAssets !== undefined ||
      (isRecord(recipeImages) && Object.hasOwn(recipeImages, 'policy'))
    ) {
      throw new Error(
        'Dataset manifest contains a visual-rights policy without an exact top-level publicationPolicy.',
      );
    }
    return undefined;
  }
  if (manifest.publicationPolicy !== GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY) {
    throw new Error(
      `Dataset manifest publicationPolicy ${JSON.stringify(manifest.publicationPolicy)} is unsupported.`,
    );
  }
  if (
    manifest.profile !== 'gtnh-1.7.10' ||
    manifest.minecraft !== '1.7.10' ||
    manifest.forge !== '10.13.4.1614' ||
    manifest.nei !== '2.8.44-GTNH' ||
    !hasExactPrimitiveRecord(manifest.pack, GTNH_PACK_IDENTITY) ||
    !hasExactPrimitiveRecord(manifest.attribution, GTNH_DATA_ATTRIBUTION) ||
    !hasExactPrimitiveRecord(visualAssets, GTNH_STRUCTURED_DATA_ONLY_VISUAL_ASSETS) ||
    !isRecord(manifest.counts) ||
    !Number.isSafeInteger(manifest.counts.recipes) ||
    manifest.counts.recipes <= 0
  ) {
    throw new Error(
      'The structured-data-only publication policy requires the exact GTNH 2.8.4 ' +
        'profile, runtime provenance, attribution, and zero-visual-assets contract.',
    );
  }
  if (
    !hasExactKeys(recipeImages, [
      'mode',
      'reason',
      'policy',
      'references',
      'files',
      'encoding',
      'bytes',
      'inventory',
    ]) ||
    recipeImages.mode !== 'omitted' ||
    recipeImages.reason !== GTNH_STRUCTURED_DATA_ONLY_EXCLUSION_REASON ||
    recipeImages.policy !== GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY ||
    recipeImages.encoding !== 'png' ||
    !Number.isSafeInteger(recipeImages.references) ||
    recipeImages.references < 0 ||
    recipeImages.files !== recipeImages.references ||
    !Number.isSafeInteger(recipeImages.bytes) ||
    recipeImages.bytes < 0
  ) {
    throw new Error(
      'The structured-data-only publication policy requires the exact recipe-image ' +
        'rights-exclusion declaration and inventory envelope.',
    );
  }
  const inventory = requireRecipeImageInventory(
    recipeImages.inventory,
    'Dataset manifest.web.recipeImages.inventory',
    manifest.counts.recipes,
  );
  if (
    inventory.previews !== recipeImages.references ||
    inventory.missing !== manifest.counts.recipes - recipeImages.references
  ) {
    throw new Error(
      'Dataset manifest.web.recipeImages inventory does not reconcile with the GTNH ' +
        'structured-data-only exclusion counts.',
    );
  }
  return GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY;
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function relativeKey(root, path) {
  return relative(root, path).split(sep).join('/');
}

function resolveInside(root, key) {
  const path = resolve(root, ...key.split('/'));
  const prefix = root.endsWith(sep) ? root : `${root}${sep}`;
  if (!path.startsWith(prefix)) {
    throw new Error(`Publication record resolves outside its root: ${key}.`);
  }
  return path;
}

function isInsideOrEqual(root, path) {
  const prefix = root.endsWith(sep) ? root : `${root}${sep}`;
  return path === root || path.startsWith(prefix);
}

async function canonicalProspectivePath(path) {
  let cursor = path;
  const missing = [];
  for (;;) {
    try {
      return resolve(await realpath(cursor), ...missing);
    } catch (error) {
      if (error?.code !== 'ENOENT') {
        throw new Error(`Publication path could not be canonicalized at ${cursor}: ${error.message}`, {
          cause: error,
        });
      }
      const parent = dirname(cursor);
      if (parent === cursor) {
        throw new Error(`Publication path has no canonicalizable ancestor: ${path}.`);
      }
      missing.unshift(basename(cursor));
      cursor = parent;
    }
  }
}

async function requirePlainDirectory(path, label) {
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

async function readPlainFile(path, label, maximum) {
  let info;
  try {
    info = await lstat(path);
  } catch (error) {
    throw new Error(`${label} could not be inspected at ${path}: ${error.message}`, {cause: error});
  }
  if (info.isSymbolicLink() || !info.isFile()) {
    throw new Error(`${label} must be a plain file: ${path}.`);
  }
  if (!Number.isSafeInteger(info.size) || info.size <= 0 || info.size > maximum) {
    throw new Error(`${label} has invalid byte length ${info.size}; maximum is ${maximum}.`);
  }
  let bytes;
  try {
    bytes = await readFile(path);
  } catch (error) {
    throw new Error(`${label} could not be read at ${path}: ${error.message}`, {cause: error});
  }
  if (bytes.length !== info.size) {
    throw new Error(
      `${label} changed while being read: inspected ${info.size} bytes, read ${bytes.length}.`,
    );
  }
  return bytes;
}

function parseJson(bytes, label) {
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch (error) {
    throw new Error(`${label} contains invalid JSON: ${error.message}`, {cause: error});
  }
}

function collectPackedCoordinates(value, documentPath, coordinatesByPack) {
  const pending = [value];
  while (pending.length > 0) {
    const entry = pending.pop();
    if (typeof entry === 'string') {
      if (!entry.startsWith('assets/s/')) continue;
      const coordinate = parsePackedImagePath(entry);
      if (
        coordinate === null ||
        packedImagePath(coordinate.packNumber, coordinate.offset, coordinate.length) !== entry
      ) {
        throw new Error(
          `Document ${documentPath} contains a non-canonical packed-image coordinate: ` +
            `${JSON.stringify(entry)}.`,
        );
      }
      let ranges = coordinatesByPack.get(coordinate.packNumber);
      if (!ranges) {
        ranges = new Map();
        coordinatesByPack.set(coordinate.packNumber, ranges);
      }
      const previous = ranges.get(coordinate.offset);
      if (previous !== undefined && previous !== coordinate.length) {
        throw new Error(
          `Pack ${coordinate.packNumber} offset ${coordinate.offset} is referenced with ` +
            `conflicting lengths ${previous} and ${coordinate.length}.`,
        );
      }
      ranges.set(coordinate.offset, coordinate.length);
      continue;
    }
    if (Array.isArray(entry)) {
      for (const child of entry) pending.push(child);
      continue;
    }
    if (entry && typeof entry === 'object') {
      for (const child of Object.values(entry)) pending.push(child);
    }
  }
}

function assertWebpPayload(pack, offset, length, label) {
  if (
    length < 12 ||
    pack.toString('ascii', offset, offset + 4) !== 'RIFF' ||
    pack.toString('ascii', offset + 8, offset + 12) !== 'WEBP'
  ) {
    throw new Error(`${label} does not begin with a complete WebP RIFF header.`);
  }
  const declaredLength = pack.readUInt32LE(offset + 4) + 8;
  if (declaredLength !== length) {
    throw new Error(`${label} declares ${declaredLength} RIFF bytes; coordinate length is ${length}.`);
  }
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
  await Promise.all(Array.from({length: Math.min(concurrency, values.length)}, worker));
  return results;
}

function validateConcurrency(value) {
  if (!Number.isSafeInteger(value) || value <= 0 || value > MAX_CONCURRENCY) {
    throw new Error(`concurrency must be within 1..${MAX_CONCURRENCY}.`);
  }
  return value;
}

async function analyzeExport(exportRoot, concurrency) {
  const root = resolve(exportRoot);
  await requirePlainDirectory(root, 'Core dataset export root');
  const canonicalRoot = await realpath(root);
  const datasetManifestBytes = await readPlainFile(
    join(canonicalRoot, 'manifest.json'),
    'Dataset manifest.json',
    MAX_SHARD_BYTES,
  );
  const datasetManifest = parseJson(datasetManifestBytes, 'Dataset manifest.json');
  const publicationPolicy = requirePackedDatasetPublicationPolicy(datasetManifest);
  const publicationId = datasetManifest?.publicationId;
  if (!CORE_DATASET_PUBLICATION_ID_PATTERN.test(publicationId ?? '')) {
    throw new Error('Dataset manifest.json must contain a lowercase SHA-256 publicationId.');
  }
  const computedPublicationId = await computePublicationId(canonicalRoot);
  if (computedPublicationId !== publicationId) {
    throw new Error(
      `Dataset publicationId ${publicationId} does not match canonical export content hash ` +
        `${computedPublicationId}.`,
    );
  }

  const files = (await collectFiles(canonicalRoot))
    .map(path => ({path, key: relativeKey(canonicalRoot, path)}))
    .sort((left, right) => (left.key < right.key ? -1 : left.key > right.key ? 1 : 0));
  const documentFiles = [];
  const packFiles = [];
  for (const file of files) {
    if (file.key.endsWith('.json')) documentFiles.push(file);
    else if (/^assets\/pack-(?:\d{3}|[1-9]\d{3,})\.bin$/.test(file.key)) packFiles.push(file);
    else {
      throw new Error(
        `Core dataset publication refuses unsupported source object ${file.key}; ` +
          'only JSON documents and canonical packed-image blobs are allowed.',
      );
    }
  }
  if (documentFiles.length === 0) {
    throw new Error('Core dataset publication requires JSON documents.');
  }
  if (publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY) {
    if (packFiles.length !== 0) {
      throw new Error(
        'The GTNH structured-data-only publication must contain zero packed-image blobs.',
      );
    }
  } else if (packFiles.length === 0) {
    throw new Error(
      'Core dataset publication requires packed-image blobs unless the exact GTNH ' +
        'structured-data-only policy is declared.',
    );
  }
  packFiles.sort((left, right) => {
    const leftNumber = Number(/^assets\/pack-(\d+)\.bin$/.exec(left.key)[1]);
    const rightNumber = Number(/^assets\/pack-(\d+)\.bin$/.exec(right.key)[1]);
    return leftNumber - rightNumber;
  });

  const coordinatesByPack = new Map();
  const documents = await mapConcurrent(documentFiles, concurrency, async file => {
    const bytes = await readPlainFile(file.path, `Dataset document ${file.key}`, MAX_SHARD_BYTES);
    collectPackedCoordinates(parseJson(bytes, `Dataset document ${file.key}`), file.key, coordinatesByPack);
    return {path: file.key, bytes: bytes.length, sha256: sha256(bytes)};
  });

  const indexPayloads = new Map();
  const packs = [];
  for (const [packNumber, file] of packFiles.entries()) {
    const expectedPath = packFileKey(packNumber);
    if (file.key !== expectedPath) {
      throw new Error(
        `Packed-image blobs must be consecutive: found ${file.key}, expected ${expectedPath}.`,
      );
    }
    const bytes = await readPlainFile(file.path, `Dataset pack ${file.key}`, MAX_PACK_BYTES);
    const ranges = coordinatesByPack.get(packNumber);
    if (!ranges || ranges.size === 0) {
      throw new Error(`Dataset pack ${file.key} has no document-authorized image ranges.`);
    }
    const entries = [...ranges.entries()].sort((left, right) => left[0] - right[0]);
    let cursor = 0;
    for (const [entryIndex, [offset, length]] of entries.entries()) {
      if (
        offset !== cursor ||
        !Number.isSafeInteger(length) ||
        length <= 0 ||
        offset + length > bytes.length
      ) {
        throw new Error(
          `Dataset pack ${file.key} authorization entry ${entryIndex} is not contiguous at ` +
            `offset ${cursor}.`,
        );
      }
      assertWebpPayload(bytes, offset, length, `${file.key} range ${offset}+${length}`);
      cursor += length;
    }
    if (cursor !== bytes.length) {
      throw new Error(
        `Dataset pack ${file.key} authorized ranges cover ${cursor}/${bytes.length} bytes.`,
      );
    }
    coordinatesByPack.delete(packNumber);
    const indexBytes = encodePackedImageAuthorizationIndex({
      packNumber,
      packBytes: bytes.length,
      entries,
    });
    const indexPath = `indexes/pack-${String(packNumber).padStart(3, '0')}.bin`;
    indexPayloads.set(indexPath, indexBytes);
    packs.push({
      path: file.key,
      bytes: bytes.length,
      sha256: sha256(bytes),
      index: {
        path: indexPath,
        bytes: indexBytes.length,
        sha256: sha256(indexBytes),
        entries: entries.length,
      },
    });
  }
  if (coordinatesByPack.size > 0) {
    throw new Error(
      `Dataset documents reference absent pack number(s): ${[...coordinatesByPack.keys()]
        .sort((left, right) => left - right)
        .join(', ')}.`,
    );
  }

  const documentBytes = documents.reduce((sum, record) => sum + record.bytes, 0);
  const packBytes = packs.reduce((sum, record) => sum + record.bytes, 0);
  const packIndexBytes = packs.reduce((sum, record) => sum + record.index.bytes, 0);
  const packedImages = packs.reduce((sum, record) => sum + record.index.entries, 0);
  const manifest = requireCoreDatasetPublicationManifest({
    format: CORE_DATASET_PUBLICATION_FORMAT,
    ...(publicationPolicy === undefined ? {} : {publicationPolicy}),
    publicationId,
    maxDocumentBytes: MAX_SHARD_BYTES,
    maxPackBytes: MAX_PACK_BYTES,
    packIndexFormat: PACKED_IMAGE_AUTHORIZATION_FORMAT,
    maxPackIndexBytes: MAX_PACKED_IMAGE_AUTHORIZATION_BYTES,
    counts: {
      documents: documents.length,
      packs: packs.length,
      packedImages,
      documentBytes,
      packBytes,
      packIndexBytes,
      objects: documents.length + packs.length * 2,
      storedBytes: documentBytes + packBytes + packIndexBytes,
    },
    documents,
    packs,
  }, publicationId);
  const manifestBytes = coreDatasetPublicationManifestBytes(manifest);
  return {
    exportRoot: canonicalRoot,
    publicationId,
    manifest,
    manifestBytes,
    manifestSha256: sha256(manifestBytes),
    indexPayloads,
  };
}

async function verifySourceRecords(root, records, concurrency) {
  await mapConcurrent(records, concurrency, async record => {
    if (record.path.startsWith('indexes/')) return;
    const maximum = record.path.startsWith('assets/') ? MAX_PACK_BYTES : MAX_SHARD_BYTES;
    const bytes = await readPlainFile(
      resolveInside(root, record.path),
      `Source object ${record.path}`,
      maximum,
    );
    const digest = sha256(bytes);
    if (bytes.length !== record.bytes || digest !== record.sha256) {
      throw new Error(
        `Source object changed after analysis: ${record.path} is ` +
          `${bytes.length} bytes/${digest}; expected ${record.bytes}/${record.sha256}.`,
      );
    }
  });
}

async function verifyBundleFiles(bundleRoot, analyzed) {
  await requirePlainDirectory(bundleRoot, 'Core dataset publication bundle');
  const files = (await collectFiles(bundleRoot))
    .map(path => relativeKey(bundleRoot, path))
    .sort();
  const expected = ['publication.json', ...analyzed.indexPayloads.keys()].sort();
  if (
    files.length !== expected.length ||
    files.some((path, index) => path !== expected[index])
  ) {
    throw new Error(
      'Core dataset publication bundle inventory differs from its canonical publication.json/index set.',
    );
  }
  const manifestBytes = await readPlainFile(
    join(bundleRoot, 'publication.json'),
    'Core dataset publication.json',
    MAX_CORE_PUBLICATION_MANIFEST_BYTES,
  );
  requireCanonicalCoreDatasetPublicationBytes(manifestBytes, analyzed.publicationId);
  if (!manifestBytes.equals(analyzed.manifestBytes)) {
    throw new Error('Core dataset publication.json does not exactly match the analyzed export.');
  }
  for (const pack of analyzed.manifest.packs) {
    const expectedBytes = analyzed.indexPayloads.get(pack.index.path);
    const actual = await readPlainFile(
      resolveInside(bundleRoot, pack.index.path),
      `Core dataset ${pack.index.path}`,
      MAX_PACKED_IMAGE_AUTHORIZATION_BYTES,
    );
    if (!actual.equals(expectedBytes)) {
      throw new Error(`Core dataset ${pack.index.path} does not match the derived MRPI index.`);
    }
    const parsed = parsePackedImageAuthorizationIndex(actual, {
      expectedPackNumber: analyzed.manifest.packs.indexOf(pack),
      expectedPackBytes: pack.bytes,
    });
    if (parsed.entries.length !== pack.index.entries) {
      throw new Error(`Core dataset ${pack.index.path} entry count disagrees with publication.json.`);
    }
  }
}

function recordsWithLocalPaths(analyzed, bundleRoot) {
  return coreDatasetContentRecords(analyzed.manifest).map(record => ({
    ...record,
    localPath: resolveInside(
      record.path.startsWith('indexes/') ? bundleRoot : analyzed.exportRoot,
      record.path,
    ),
  }));
}

/** Exhaustively re-derive and verify a local export + authorization bundle. */
export async function validateLocalCoreDatasetPublication({
  exportRoot,
  publication,
  concurrency = DEFAULT_CONCURRENCY,
  logger = console,
}) {
  validateConcurrency(concurrency);
  if (typeof exportRoot !== 'string' || exportRoot.length === 0) {
    throw new Error('exportRoot must be a non-empty path.');
  }
  if (typeof publication !== 'string' || publication.length === 0) {
    throw new Error('publication must be a non-empty publication.json path.');
  }
  const manifestPath = resolve(publication);
  if (basename(manifestPath) !== 'publication.json') {
    throw new Error('publication must name the canonical publication.json file.');
  }
  const bundleRoot = dirname(manifestPath);
  logger.info(`Re-deriving core publication from ${resolve(exportRoot)}.`);
  const analyzed = await analyzeExport(exportRoot, concurrency);
  if (analyzed.manifest.publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY) {
    logger.warn(
      `Rights policy ${GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY} excludes all ` +
        'dataset-carried visual assets; validating a zero-pack core publication.',
    );
  }
  await verifyBundleFiles(bundleRoot, analyzed);
  await verifySourceRecords(
    analyzed.exportRoot,
    coreDatasetContentRecords(analyzed.manifest),
    concurrency,
  );
  return {
    ...analyzed,
    bundleRoot,
    records: recordsWithLocalPaths(analyzed, bundleRoot),
  };
}

/** Build deterministic MRPI indexes and an atomic canonical control manifest. */
export async function buildCoreDatasetPublication({
  exportRoot,
  output,
  concurrency = DEFAULT_CONCURRENCY,
  logger = console,
  beforeCommit,
}) {
  validateConcurrency(concurrency);
  if (typeof exportRoot !== 'string' || exportRoot.length === 0) {
    throw new Error('exportRoot must be a non-empty path.');
  }
  if (typeof output !== 'string' || output.length === 0) {
    throw new Error('output must be a non-empty directory path.');
  }
  if (beforeCommit !== undefined && typeof beforeCommit !== 'function') {
    throw new Error('beforeCommit must be a function when provided.');
  }
  const requestedExportRoot = resolve(exportRoot);
  await requirePlainDirectory(requestedExportRoot, 'Core dataset export root');
  const canonicalExportRoot = await realpath(requestedExportRoot);
  const requestedOutputRoot = resolve(output);
  try {
    const outputInfo = await lstat(requestedOutputRoot);
    if (outputInfo.isSymbolicLink()) {
      throw new Error(`Core dataset publication output must not be a symlink: ${requestedOutputRoot}.`);
    }
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }
  const outputRoot = await canonicalProspectivePath(requestedOutputRoot);
  if (isInsideOrEqual(canonicalExportRoot, outputRoot)) {
    throw new Error('Core dataset publication output must be outside the source export root.');
  }
  const analyzed = await analyzeExport(canonicalExportRoot, concurrency);
  if (analyzed.manifest.publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY) {
    logger.warn(
      `Rights policy ${GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY} excludes all ` +
        'dataset-carried visual assets; building a zero-pack core publication.',
    );
  }

  try {
    const existing = await lstat(outputRoot);
    if (existing.isSymbolicLink() || !existing.isDirectory()) {
      throw new Error(`Existing core publication output is not a plain directory: ${outputRoot}.`);
    }
    await verifyBundleFiles(outputRoot, analyzed);
    await verifySourceRecords(
      analyzed.exportRoot,
      coreDatasetContentRecords(analyzed.manifest),
      concurrency,
    );
    logger.info(
      `Core publication ${analyzed.publicationId} already exists and matches exactly; reusing it.`,
    );
    return {
      ...analyzed,
      bundleRoot: outputRoot,
      records: recordsWithLocalPaths(analyzed, outputRoot),
      reused: true,
    };
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }

  await mkdir(dirname(outputRoot), {recursive: true});
  const stagingRoot = join(
    dirname(outputRoot),
    `.core-dataset-publication-${process.pid}-${randomUUID()}`,
  );
  try {
    await mkdir(join(stagingRoot, 'indexes'), {recursive: true});
    for (const [path, bytes] of analyzed.indexPayloads) {
      await writeFile(resolveInside(stagingRoot, path), bytes, {flag: 'wx'});
    }
    // publication.json is the local commit marker too: indexes are durable first.
    await writeFile(join(stagingRoot, 'publication.json'), analyzed.manifestBytes, {flag: 'wx'});
    if (beforeCommit) await beforeCommit(analyzed);
    const recheckedPublicationId = await computePublicationId(analyzed.exportRoot);
    if (recheckedPublicationId !== analyzed.publicationId) {
      throw new Error(
        `Core dataset changed before publication commit: expected ${analyzed.publicationId}, ` +
          `observed ${recheckedPublicationId}.`,
      );
    }
    await verifySourceRecords(
      analyzed.exportRoot,
      coreDatasetContentRecords(analyzed.manifest),
      concurrency,
    );
    await verifyBundleFiles(stagingRoot, analyzed);
    await rename(stagingRoot, outputRoot);
    logger.info(
      `Built core publication ${analyzed.publicationId}: ${analyzed.manifest.counts.objects} ` +
        `immutable objects, ${analyzed.manifest.counts.packedImages} MRPI-authorized images.`,
    );
    return {
      ...analyzed,
      bundleRoot: outputRoot,
      records: recordsWithLocalPaths(analyzed, outputRoot),
      reused: false,
    };
  } catch (error) {
    logger.error(`Core publication build failed before atomic commit: ${error.message}`);
    try {
      await rm(stagingRoot, {recursive: true, force: true});
    } catch (cleanupError) {
      logger.error(`Core publication staging cleanup failed at ${stagingRoot}: ${cleanupError.message}`);
      throw new AggregateError(
        [error, cleanupError],
        'Core publication build and staging cleanup both failed.',
      );
    }
    throw error;
  }
}

function parseCliArguments(argv) {
  const options = {};
  const names = new Map([
    ['--root', 'exportRoot'],
    ['--output', 'output'],
    ['--concurrency', 'concurrency'],
  ]);
  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    const name = names.get(flag);
    if (!name) throw new Error(`Unknown core publication builder argument: ${flag}.`);
    const value = argv[index + 1];
    if (value === undefined || value.startsWith('--')) {
      throw new Error(`${flag} requires a value.`);
    }
    if (options[name] !== undefined) throw new Error(`${flag} was provided more than once.`);
    options[name] = name === 'concurrency' ? Number(value) : value;
    index += 1;
  }
  if (!options.exportRoot || !options.output) {
    throw new Error(
      'Usage: node scripts/build-core-dataset-publication.mjs ' +
        '--root <packed-export-root> --output <new-publication-bundle> ' +
        '[--concurrency <1-32>].',
    );
  }
  return options;
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : null;
if (invokedPath === fileURLToPath(import.meta.url)) {
  try {
    await buildCoreDatasetPublication(parseCliArguments(process.argv.slice(2)));
  } catch (error) {
    console.error(`Core dataset publication builder terminated: ${error.message}`);
    process.exitCode = 1;
  }
}
