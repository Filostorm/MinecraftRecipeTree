import {
  CORE_PACK_INDEX_ENTRY_BYTES,
  CORE_PACK_INDEX_HEADER_BYTES,
  MAX_CORE_PACK_BYTES,
  type CorePackRecord,
  type ValidatedCoreDatasetPublication,
} from './coreDatasetContract.ts';
import {
  coreObjectKey,
  coreObjectMatches,
  loadCommittedCorePublication,
} from './coreDatasetUpload.ts';
import {
  type CommittedDatasetIdentity,
  requireCommittedDatasetIdentity,
} from './datasetIdentity.ts';
import {
  type DatasetR2Bucket,
  type DatasetR2Object,
  type DatasetRuntime,
  sha256Hex,
} from './datasetRuntime.ts';
import {
  MAX_PREVIEW_MANIFEST_BYTES,
  MAX_PREVIEW_PACK_BYTES,
  PREVIEW_ASSET_SET_PATTERN,
  PREVIEW_CATEGORY_ROUTE,
  PREVIEW_PACK_INDEX_ENTRY_BYTES,
  PREVIEW_PACK_INDEX_HEADER_BYTES,
  type PreviewContentRecord,
  type PreviewPackRecord,
  type ValidatedPreviewManifest,
  requireContentAddressedPreviewManifest,
  requirePairedPublicationPolicy,
} from './previewAssetContract.ts';

export const CORE_PUBLIC_ROUTE =
  /^\/dataset\/publications\/([a-f0-9]{64})\/exports\/(.+)$/;
export const PREVIEW_PUBLIC_ROUTE =
  /^\/dataset\/preview-sets\/([a-f0-9]{64})\/(.+)$/;

const PACKED_IMAGE_ROUTE = /^assets\/s\/(\d+)-(\d+)-(\d+)\.webp$/;
const MRPI_MAGIC = 0x4d525049;
const MRPI_VERSION = 1;
const IMMUTABLE = 'public, max-age=31536000, immutable';
const IMMUTABLE_IMAGE = `${IMMUTABLE}, no-transform`;
const STORED_BYTES_HEADER = 'X-MRT-Stored-Bytes';
const MAX_MANIFEST_CACHE_ENTRIES = 12;
const MAX_INDEX_CACHE_ENTRIES = 96;
const MAX_INDEX_CACHE_BYTES = 3 * 1024 * 1024;

interface PackedCoordinate {
  packNumber: number;
  offset: number;
  length: number;
}

interface AuthorizationIndex {
  view: DataView;
  entries: number;
}

interface LoadedPreviewPublication {
  bytes: Uint8Array;
  digest: string;
  state: ValidatedPreviewManifest;
}

interface DeliveryExecutionContext {
  waitUntil(operation: Promise<unknown>): void;
}

interface IndexCacheEntry {
  bytes: number;
  promise: Promise<AuthorizationIndex>;
}

const bucketNamespaces = new WeakMap<object, number>();
let nextBucketNamespace = 1;
const coreManifestCache = new Map<string, ReturnType<typeof loadCommittedCorePublication>>();
const coreDatasetIdentityCache = new Map<string, Promise<CommittedDatasetIdentity>>();
const previewManifestCache = new Map<string, Promise<LoadedPreviewPublication>>();
const indexCache = new Map<string, IndexCacheEntry>();
let indexCacheBytes = 0;

function bucketNamespace(bucket: DatasetR2Bucket): number {
  let namespace = bucketNamespaces.get(bucket as object);
  if (namespace === undefined) {
    namespace = nextBucketNamespace;
    nextBucketNamespace += 1;
    bucketNamespaces.set(bucket as object, namespace);
  }
  return namespace;
}

function touchBounded<K, V>(cache: Map<K, V>, key: K, value: V, maximum: number): void {
  cache.delete(key);
  cache.set(key, value);
  while (cache.size > maximum) {
    const oldest = cache.keys().next().value as K | undefined;
    if (oldest === undefined) break;
    cache.delete(oldest);
  }
}

function parseCoordinate(path: string, maximumPackBytes: number): PackedCoordinate | null {
  const match = PACKED_IMAGE_ROUTE.exec(path);
  if (!match) return null;
  const packNumber = Number(match[1]);
  const offset = Number(match[2]);
  const length = Number(match[3]);
  if (
    !Number.isSafeInteger(packNumber) ||
    packNumber < 0 ||
    String(packNumber).padStart(3, '0') !== match[1] ||
    !Number.isSafeInteger(offset) ||
    offset < 0 ||
    String(offset) !== match[2] ||
    !Number.isSafeInteger(length) ||
    length <= 0 ||
    String(length) !== match[3] ||
    !Number.isSafeInteger(offset + length) ||
    offset + length > maximumPackBytes
  ) {
    return null;
  }
  return {packNumber, offset, length};
}

function canonicalAssetPath(rawPath: string): string | null {
  let path: string;
  try {
    path = decodeURIComponent(rawPath);
  } catch (error) {
    console.warn('An immutable dataset path has malformed percent encoding.', {rawPath, error});
    return null;
  }
  if (
    path !== rawPath ||
    path.length > 1024 ||
    path.startsWith('/') ||
    path.includes('\\') ||
    !/^[A-Za-z0-9._/-]+$/.test(path) ||
    path.split('/').some(segment => segment.length === 0 || segment === '.' || segment === '..')
  ) {
    return null;
  }
  return path;
}

function requestBucket(runtime: DatasetRuntime): DatasetR2Bucket | Response {
  const bucket = runtime.PREVIEW_ASSETS;
  if (!bucket) {
    console.error('Immutable dataset delivery requires the PREVIEW_ASSETS R2 binding.');
    return new Response('Dataset storage unavailable', {
      status: 503,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  return bucket;
}

function previewObjectKey(assetSetId: string, path: string): string {
  return `${assetSetId}/${path}`;
}

function previewObjectMatches(
  object: DatasetR2Object,
  assetSetId: string,
  datasetPublicationId: string,
  record: PreviewContentRecord,
): boolean {
  const metadata = object.customMetadata ?? {};
  return (
    object.size === record.bytes &&
    metadata['mrt-asset-set'] === assetSetId &&
    metadata['mrt-dataset'] === datasetPublicationId &&
    metadata['mrt-path'] === record.path &&
    metadata['mrt-sha256'] === record.sha256
  );
}

export async function loadCommittedPreviewPublication(
  bucket: DatasetR2Bucket,
  assetSetId: string,
): Promise<LoadedPreviewPublication> {
  if (!PREVIEW_ASSET_SET_PATTERN.test(assetSetId)) {
    throw new Error('Preview asset-set ID is not canonical.');
  }
  const cacheKey = `${bucketNamespace(bucket)}:${assetSetId}`;
  const cached = previewManifestCache.get(cacheKey);
  if (cached) {
    touchBounded(previewManifestCache, cacheKey, cached, MAX_MANIFEST_CACHE_ENTRIES);
    return cached;
  }
  const operation = (async () => {
    const key = previewObjectKey(assetSetId, 'manifest.json');
    const object = await bucket.get(key);
    if (!object) throw new Error(`Committed preview manifest is missing at ${key}.`);
    if (!Number.isSafeInteger(object.size) || object.size <= 0 || object.size > MAX_PREVIEW_MANIFEST_BYTES) {
      throw new Error(`Committed preview manifest ${key} has invalid size metadata.`);
    }
    const bytes = new Uint8Array(await object.arrayBuffer());
    if (object.key !== key || bytes.byteLength !== object.size) {
      throw new Error(`Committed preview manifest ${key} returned invalid R2 data.`);
    }
    const digest = await sha256Hex(bytes);
    let value: unknown;
    try {
      value = JSON.parse(new TextDecoder().decode(bytes)) as unknown;
    } catch (error) {
      throw new Error(`Committed preview manifest ${key} is invalid JSON: ${error instanceof Error ? error.message : String(error)}`);
    }
    const state = await requireContentAddressedPreviewManifest(value, assetSetId);
    const record = {path: 'manifest.json', bytes: bytes.byteLength, sha256: digest};
    if (!previewObjectMatches(object, assetSetId, state.manifest.datasetPublicationId, record)) {
      throw new Error(`Committed preview manifest ${key} has invalid immutable metadata.`);
    }
    return {bytes, digest, state};
  })().catch(error => {
    previewManifestCache.delete(cacheKey);
    console.error('Committed preview manifest failed validation.', {assetSetId, error});
    throw error;
  });
  touchBounded(previewManifestCache, cacheKey, operation, MAX_MANIFEST_CACHE_ENTRIES);
  return operation;
}

async function loadCorePublication(
  bucket: DatasetR2Bucket,
  publicationId: string,
) {
  const cacheKey = `${bucketNamespace(bucket)}:${publicationId}`;
  const cached = coreManifestCache.get(cacheKey);
  if (cached) {
    touchBounded(coreManifestCache, cacheKey, cached, MAX_MANIFEST_CACHE_ENTRIES);
    return cached;
  }
  const operation = loadCommittedCorePublication(bucket, publicationId)
    .then(publication => {
      // A pre-commit probe must not pin a negative lookup after the immutable marker is written.
      if (!publication) coreManifestCache.delete(cacheKey);
      return publication;
    })
    .catch(error => {
      coreManifestCache.delete(cacheKey);
      console.error('Committed core publication failed validation.', {publicationId, error});
      throw error;
    });
  touchBounded(coreManifestCache, cacheKey, operation, MAX_MANIFEST_CACHE_ENTRIES);
  return operation;
}

async function loadCoreDatasetIdentity(
  bucket: DatasetR2Bucket,
  publicationId: string,
  publication: ValidatedCoreDatasetPublication,
): Promise<CommittedDatasetIdentity> {
  const cacheKey = `${bucketNamespace(bucket)}:${publicationId}`;
  const cached = coreDatasetIdentityCache.get(cacheKey);
  if (cached) {
    touchBounded(coreDatasetIdentityCache, cacheKey, cached, MAX_MANIFEST_CACHE_ENTRIES);
    return cached;
  }
  const operation = (async () => {
    const record = publication.contentRecordsByPath.get('manifest.json');
    if (!record) throw new Error('Committed core publication omits dataset manifest.json.');
    const objectKey = coreObjectKey(publicationId, record.path);
    const object = await bucket.get(objectKey);
    if (
      !object ||
      object.key !== objectKey ||
      !coreObjectMatches(object, publicationId, record)
    ) {
      throw new Error(`Committed dataset manifest ${objectKey} is missing or has invalid immutable metadata.`);
    }
    const bytes = new Uint8Array(await object.arrayBuffer());
    if (bytes.byteLength !== record.bytes || (await sha256Hex(bytes)) !== record.sha256) {
      throw new Error(`Committed dataset manifest ${objectKey} failed byte-length or SHA-256 validation.`);
    }
    let value: unknown;
    try {
      value = JSON.parse(new TextDecoder().decode(bytes)) as unknown;
    } catch (error) {
      throw new Error(
        `Committed dataset manifest ${objectKey} is invalid JSON: ` +
          `${error instanceof Error ? error.message : String(error)}`,
      );
    }
    return requireCommittedDatasetIdentity(value, publicationId);
  })().catch(error => {
    coreDatasetIdentityCache.delete(cacheKey);
    console.error('Committed dataset manifest identity failed validation.', {publicationId, error});
    throw error;
  });
  touchBounded(coreDatasetIdentityCache, cacheKey, operation, MAX_MANIFEST_CACHE_ENTRIES);
  return operation;
}

export async function verifyCommittedDatasetPair(
  runtime: DatasetRuntime,
  publicationId: string,
  assetSetId: string,
): Promise<CommittedDatasetIdentity> {
  const bucket = runtime.PREVIEW_ASSETS;
  if (!bucket) throw new Error('Dataset R2 binding is unavailable.');
  const [core, preview] = await Promise.all([
    loadCorePublication(bucket, publicationId),
    loadCommittedPreviewPublication(bucket, assetSetId),
  ]);
  if (!core) throw new Error(`Core publication ${publicationId} is not committed.`);
  if (preview.state.manifest.datasetPublicationId !== publicationId) {
    throw new Error(
      `Preview asset set ${assetSetId} targets ${preview.state.manifest.datasetPublicationId}, not ${publicationId}.`,
    );
  }
  requirePairedPublicationPolicy(core.state.manifest, preview.state.manifest);
  return loadCoreDatasetIdentity(bucket, publicationId, core.state);
}

function validateAuthorizationIndex(
  bytes: Uint8Array,
  expectedPackNumber: number,
  expectedPackBytes: number,
  expectedEntries: number,
  headerBytes: number,
  entryBytes: number,
): AuthorizationIndex {
  if (bytes.byteLength !== headerBytes + expectedEntries * entryBytes) {
    throw new Error('MRPI authorization byte length does not match the committed manifest.');
  }
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (
    view.getUint32(0) !== MRPI_MAGIC ||
    view.getUint16(4) !== MRPI_VERSION ||
    view.getUint16(6) !== headerBytes ||
    view.getUint32(8) !== expectedPackNumber ||
    view.getUint32(12) !== expectedPackBytes ||
    view.getUint32(16) !== expectedEntries
  ) {
    throw new Error('MRPI authorization header is invalid.');
  }
  let cursor = 0;
  for (let entry = 0; entry < expectedEntries; entry += 1) {
    const position = headerBytes + entry * entryBytes;
    const offset = view.getUint32(position);
    const length = view.getUint32(position + 4);
    if (offset !== cursor || length <= 0 || offset + length > expectedPackBytes) {
      throw new Error(`MRPI authorization entry ${entry} is not canonical and contiguous.`);
    }
    cursor += length;
  }
  if (cursor !== expectedPackBytes) throw new Error('MRPI authorization does not cover the complete pack.');
  return {view, entries: expectedEntries};
}

function indexAuthorizes(
  index: AuthorizationIndex,
  coordinate: PackedCoordinate,
  headerBytes: number,
  entryBytes: number,
): boolean {
  let low = 0;
  let high = index.entries - 1;
  while (low <= high) {
    const middle = low + Math.floor((high - low) / 2);
    const position = headerBytes + middle * entryBytes;
    const offset = index.view.getUint32(position);
    if (offset < coordinate.offset) low = middle + 1;
    else if (offset > coordinate.offset) high = middle - 1;
    else return index.view.getUint32(position + 4) === coordinate.length;
  }
  return false;
}

function removeIndexCache(key: string, expected?: IndexCacheEntry): void {
  const current = indexCache.get(key);
  if (!current || (expected && current !== expected)) return;
  indexCache.delete(key);
  indexCacheBytes -= current.bytes;
}

function trimIndexCache(): void {
  while (indexCache.size > MAX_INDEX_CACHE_ENTRIES || indexCacheBytes > MAX_INDEX_CACHE_BYTES) {
    const oldest = indexCache.keys().next().value as string | undefined;
    if (oldest === undefined) break;
    removeIndexCache(oldest);
  }
}

async function coreAuthorizationIndex(
  bucket: DatasetR2Bucket,
  publicationId: string,
  packNumber: number,
  pack: CorePackRecord,
): Promise<AuthorizationIndex> {
  const key = `${bucketNamespace(bucket)}:core:${publicationId}:${pack.index.sha256}`;
  const cached = indexCache.get(key);
  if (cached) {
    indexCache.delete(key);
    indexCache.set(key, cached);
    return cached.promise;
  }
  const entry = {} as IndexCacheEntry;
  entry.bytes = pack.index.bytes;
  entry.promise = (async () => {
    const objectKey = coreObjectKey(publicationId, pack.index.path);
    const object = await bucket.get(objectKey);
    if (!object || object.key !== objectKey || !coreObjectMatches(object, publicationId, pack.index)) {
      throw new Error(`Core MRPI object ${objectKey} is missing or has invalid metadata.`);
    }
    const bytes = new Uint8Array(await object.arrayBuffer());
    if (bytes.byteLength !== pack.index.bytes || (await sha256Hex(bytes)) !== pack.index.sha256) {
      throw new Error(`Core MRPI object ${objectKey} failed byte-length or SHA-256 validation.`);
    }
    return validateAuthorizationIndex(
      bytes,
      packNumber,
      pack.bytes,
      pack.index.entries,
      CORE_PACK_INDEX_HEADER_BYTES,
      CORE_PACK_INDEX_ENTRY_BYTES,
    );
  })().catch(error => {
    removeIndexCache(key, entry);
    console.error('Core MRPI authorization index failed validation.', {publicationId, packNumber, error});
    throw error;
  });
  indexCache.set(key, entry);
  indexCacheBytes += entry.bytes;
  trimIndexCache();
  return entry.promise;
}

async function previewAuthorizationIndex(
  bucket: DatasetR2Bucket,
  assetSetId: string,
  datasetPublicationId: string,
  packNumber: number,
  pack: PreviewPackRecord,
): Promise<AuthorizationIndex> {
  const key = `${bucketNamespace(bucket)}:preview:${assetSetId}:${pack.index.sha256}`;
  const cached = indexCache.get(key);
  if (cached) {
    indexCache.delete(key);
    indexCache.set(key, cached);
    return cached.promise;
  }
  const entry = {} as IndexCacheEntry;
  entry.bytes = pack.index.bytes;
  entry.promise = (async () => {
    const objectKey = previewObjectKey(assetSetId, pack.index.path);
    const object = await bucket.get(objectKey);
    if (
      !object ||
      object.key !== objectKey ||
      !previewObjectMatches(object, assetSetId, datasetPublicationId, pack.index)
    ) {
      throw new Error(`Preview MRPI object ${objectKey} is missing or has invalid metadata.`);
    }
    const bytes = new Uint8Array(await object.arrayBuffer());
    if (bytes.byteLength !== pack.index.bytes || (await sha256Hex(bytes)) !== pack.index.sha256) {
      throw new Error(`Preview MRPI object ${objectKey} failed byte-length or SHA-256 validation.`);
    }
    return validateAuthorizationIndex(
      bytes,
      packNumber,
      pack.bytes,
      pack.index.entries,
      PREVIEW_PACK_INDEX_HEADER_BYTES,
      PREVIEW_PACK_INDEX_ENTRY_BYTES,
    );
  })().catch(error => {
    removeIndexCache(key, entry);
    console.error('Preview MRPI authorization index failed validation.', {assetSetId, packNumber, error});
    throw error;
  });
  indexCache.set(key, entry);
  indexCacheBytes += entry.bytes;
  trimIndexCache();
  return entry.promise;
}

let cacheUnavailableLogged = false;

function edgeCache(): Cache | null {
  const cache = (globalThis.caches as (CacheStorage & {default?: Cache}) | undefined)?.default;
  if (!cache && !cacheUnavailableLogged) {
    cacheUnavailableLogged = true;
    console.error('Workers Cache API is unavailable; immutable R2 delivery will continue uncached.');
  }
  return cache ?? null;
}

async function cachedResponse(request: Request, storedBytes: number): Promise<Response | null> {
  const cache = edgeCache();
  if (!cache) return null;
  try {
    const result = await cache.match(new Request(request.url, {method: 'GET'}));
    if (!result) return null;
    const headers = new Headers(result.headers);
    headers.set('X-MRT-R2-Cache', 'HIT');
    headers.set(STORED_BYTES_HEADER, String(storedBytes));
    return new Response(request.method === 'HEAD' ? null : result.body, {
      status: result.status,
      headers,
    });
  } catch (error) {
    console.error('Immutable dataset edge-cache lookup failed; continuing from validated R2.', error);
    return null;
  }
}

function storeResponse(
  request: Request,
  response: Response,
  ctx: DeliveryExecutionContext | undefined,
): void {
  if (request.method !== 'GET' || response.status !== 200) return;
  const cache = edgeCache();
  if (!cache) return;
  const operation = cache
    .put(new Request(request.url, {method: 'GET'}), response.clone())
    .catch(error => console.error('Immutable R2 response edge-cache write failed.', error));
  if (ctx?.waitUntil) ctx.waitUntil(operation);
  else {
    console.error('Worker execution context lacks waitUntil; edge-cache persistence may be incomplete.');
    void operation;
  }
}

function requestResponse(request: Request, response: Response): Response {
  return request.method === 'GET'
    ? response
    : new Response(null, {status: response.status, statusText: response.statusText, headers: response.headers});
}

async function serveWholeObject(
  request: Request,
  bucket: DatasetR2Bucket,
  objectKey: string,
  record: {path: string; bytes: number; sha256: string},
  validatesMetadata: (object: DatasetR2Object) => boolean,
  ctx: DeliveryExecutionContext | undefined,
): Promise<Response> {
  const cached = await cachedResponse(request, record.bytes);
  if (cached) return cached;
  const object = await bucket.get(objectKey);
  if (!object || object.key !== objectKey || !validatesMetadata(object)) {
    console.error('Committed immutable object is missing or has invalid R2 metadata.', {objectKey});
    return new Response('Dataset object unavailable', {status: 502, headers: {'Cache-Control': 'no-store'}});
  }
  const bytes = new Uint8Array(await object.arrayBuffer());
  if (bytes.byteLength !== record.bytes || (await sha256Hex(bytes)) !== record.sha256) {
    console.error('Committed immutable object failed byte-length or SHA-256 validation.', {objectKey});
    return new Response('Dataset object invalid', {status: 502, headers: {'Cache-Control': 'no-store'}});
  }
  const response = new Response(bytes, {
    status: 200,
    headers: {
      'Cache-Control': IMMUTABLE,
      'Content-Length': String(bytes.byteLength),
      'Content-Type': 'application/json; charset=utf-8',
      [STORED_BYTES_HEADER]: String(bytes.byteLength),
      'X-MRT-R2-Cache': 'MISS',
      'X-Content-Type-Options': 'nosniff',
    },
  });
  storeResponse(request, response, ctx);
  return requestResponse(request, response);
}

async function serveRange(
  request: Request,
  bucket: DatasetR2Bucket,
  objectKey: string,
  packBytes: number,
  coordinate: PackedCoordinate,
  validatesMetadata: (object: DatasetR2Object) => boolean,
  ctx: DeliveryExecutionContext | undefined,
): Promise<Response> {
  const cached = await cachedResponse(request, coordinate.length);
  if (cached) return cached;
  const object = await bucket.get(objectKey, {
    range: {offset: coordinate.offset, length: coordinate.length},
  });
  if (
    !object ||
    object.key !== objectKey ||
    object.size !== packBytes ||
    object.range?.offset !== coordinate.offset ||
    object.range?.length !== coordinate.length ||
    object.range?.suffix !== undefined ||
    !validatesMetadata(object)
  ) {
    console.error('R2 returned invalid immutable range metadata.', {objectKey, coordinate});
    return new Response('Dataset image unavailable', {status: 502, headers: {'Cache-Control': 'no-store'}});
  }
  const bytes = new Uint8Array(await object.arrayBuffer());
  if (bytes.byteLength !== coordinate.length) {
    console.error('R2 returned an incomplete immutable image range.', {objectKey, coordinate});
    return new Response('Dataset image unavailable', {status: 502, headers: {'Cache-Control': 'no-store'}});
  }
  const response = new Response(bytes, {
    status: 200,
    headers: {
      'Cache-Control': IMMUTABLE_IMAGE,
      'Content-Length': String(bytes.byteLength),
      'Content-Type': 'image/webp',
      [STORED_BYTES_HEADER]: String(bytes.byteLength),
      'X-MRT-R2-Cache': 'MISS',
      'X-Content-Type-Options': 'nosniff',
    },
  });
  storeResponse(request, response, ctx);
  return requestResponse(request, response);
}

export async function handleCoreDatasetRead(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
  match: RegExpExecArray,
  ctx?: DeliveryExecutionContext,
): Promise<Response> {
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    return new Response('Method not allowed', {status: 405, headers: {Allow: 'GET, HEAD', 'Cache-Control': 'no-store'}});
  }
  const publicationId = match[1];
  if (url.search !== `?dataset=${publicationId}`) {
    console.warn('Core dataset read rejected a noncanonical or mismatched dataset query.', {
      publicationId,
      search: url.search,
    });
    return new Response('Exact dataset query required', {status: 400, headers: {'Cache-Control': 'no-store'}});
  }
  const path = canonicalAssetPath(match[2]);
  if (!path) return new Response('Malformed dataset path', {status: 400, headers: {'Cache-Control': 'no-store'}});
  const bucketOrResponse = requestBucket(runtime);
  if (bucketOrResponse instanceof Response) return bucketOrResponse;
  const bucket = bucketOrResponse;
  let publication;
  try {
    publication = await loadCorePublication(bucket, publicationId);
  } catch (error) {
    console.error('Core dataset read could not validate its committed publication.', {publicationId, error});
    return new Response('Dataset publication unavailable', {status: 503, headers: {'Cache-Control': 'no-store'}});
  }
  if (!publication) {
    console.warn('Core dataset read targeted an uncommitted publication.', {publicationId});
    return new Response('Dataset publication not found', {status: 404, headers: {'Cache-Control': 'no-store'}});
  }
  const record = publication.state.contentRecordsByPath.get(path);
  if (record && path.endsWith('.json')) {
    return serveWholeObject(
      request,
      bucket,
      coreObjectKey(publicationId, path),
      record,
      object => coreObjectMatches(object, publicationId, record),
      ctx,
    );
  }
  const coordinate = parseCoordinate(path, MAX_CORE_PACK_BYTES);
  if (!coordinate) {
    console.warn('Core dataset read targeted an undeclared or malformed asset.', {publicationId, path});
    return new Response('Dataset asset is not published', {status: 400, headers: {'Cache-Control': 'no-store'}});
  }
  const pack = publication.state.manifest.packs[coordinate.packNumber];
  if (!pack || coordinate.offset + coordinate.length > pack.bytes) {
    return new Response('Dataset image coordinate is outside its published pack', {
      status: 400,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  let index: AuthorizationIndex;
  try {
    index = await coreAuthorizationIndex(bucket, publicationId, coordinate.packNumber, pack);
  } catch (error) {
    return new Response('Dataset image authorization unavailable', {
      status: 502,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  if (!indexAuthorizes(index, coordinate, CORE_PACK_INDEX_HEADER_BYTES, CORE_PACK_INDEX_ENTRY_BYTES)) {
    console.warn('Core dataset image coordinate is not an MRPI-published boundary.', {
      publicationId,
      coordinate,
    });
    return new Response('Dataset image coordinate is not published', {
      status: 400,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  return serveRange(
    request,
    bucket,
    coreObjectKey(publicationId, pack.path),
    pack.bytes,
    coordinate,
    object => coreObjectMatches(object, publicationId, pack),
    ctx,
  );
}

export async function handlePreviewDatasetRead(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
  match: RegExpExecArray,
  ctx?: DeliveryExecutionContext,
): Promise<Response> {
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    return new Response('Method not allowed', {status: 405, headers: {Allow: 'GET, HEAD', 'Cache-Control': 'no-store'}});
  }
  const assetSetId = match[1];
  const query = /^\?dataset=([a-f0-9]{64})&preview=([a-f0-9]{64})$/.exec(url.search);
  if (!query || query[2] !== assetSetId) {
    console.warn('Preview read rejected a noncanonical or mismatched immutable query.', {
      assetSetId,
      search: url.search,
    });
    return new Response('Exact dataset and preview query required', {status: 400, headers: {'Cache-Control': 'no-store'}});
  }
  const publicationId = query[1];
  const path = canonicalAssetPath(match[2]);
  if (!path) return new Response('Malformed preview path', {status: 400, headers: {'Cache-Control': 'no-store'}});
  const bucketOrResponse = requestBucket(runtime);
  if (bucketOrResponse instanceof Response) return bucketOrResponse;
  const bucket = bucketOrResponse;
  let publication: LoadedPreviewPublication;
  try {
    publication = await loadCommittedPreviewPublication(bucket, assetSetId);
  } catch (error) {
    return new Response('Preview publication unavailable', {status: 503, headers: {'Cache-Control': 'no-store'}});
  }
  if (publication.state.manifest.datasetPublicationId !== publicationId) {
    console.warn('Preview read query does not match the committed sidecar dataset binding.', {
      assetSetId,
      requestedPublicationId: publicationId,
      committedPublicationId: publication.state.manifest.datasetPublicationId,
    });
    return new Response('Preview dataset mismatch', {status: 409, headers: {'Cache-Control': 'no-store'}});
  }
  if (path === 'manifest.json') {
    const record = {path, bytes: publication.bytes.byteLength, sha256: publication.digest};
    return serveWholeObject(
      request,
      bucket,
      previewObjectKey(assetSetId, path),
      record,
      object => previewObjectMatches(object, assetSetId, publicationId, record),
      ctx,
    );
  }
  const record = publication.state.categoryDocumentsByPath.get(path);
  if (record && PREVIEW_CATEGORY_ROUTE.test(path)) {
    return serveWholeObject(
      request,
      bucket,
      previewObjectKey(assetSetId, path),
      record,
      object => previewObjectMatches(object, assetSetId, publicationId, record),
      ctx,
    );
  }
  const coordinate = parseCoordinate(path, MAX_PREVIEW_PACK_BYTES);
  if (!coordinate) {
    return new Response('Preview asset is not published', {status: 400, headers: {'Cache-Control': 'no-store'}});
  }
  const pack = publication.state.manifest.packs[coordinate.packNumber];
  if (!pack || coordinate.offset + coordinate.length > pack.bytes) {
    return new Response('Preview coordinate is outside its published pack', {
      status: 400,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  let index: AuthorizationIndex;
  try {
    index = await previewAuthorizationIndex(
      bucket,
      assetSetId,
      publicationId,
      coordinate.packNumber,
      pack,
    );
  } catch (error) {
    return new Response('Preview authorization unavailable', {status: 502, headers: {'Cache-Control': 'no-store'}});
  }
  if (!indexAuthorizes(index, coordinate, PREVIEW_PACK_INDEX_HEADER_BYTES, PREVIEW_PACK_INDEX_ENTRY_BYTES)) {
    console.warn('Preview image coordinate is not an MRPI-published boundary.', {assetSetId, coordinate});
    return new Response('Preview coordinate is not published', {status: 400, headers: {'Cache-Control': 'no-store'}});
  }
  return serveRange(
    request,
    bucket,
    previewObjectKey(assetSetId, pack.path),
    pack.bytes,
    coordinate,
    object => previewObjectMatches(object, assetSetId, publicationId, pack),
    ctx,
  );
}
