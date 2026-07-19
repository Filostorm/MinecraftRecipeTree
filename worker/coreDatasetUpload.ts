import {
  CORE_DATASET_PUBLICATION_ID_PATTERN,
  MAX_CORE_DOCUMENT_BYTES,
  MAX_CORE_PACK_BYTES,
  MAX_CORE_PACK_INDEX_BYTES,
  MAX_CORE_PUBLICATION_MANIFEST_BYTES,
  type CoreContentRecord,
  type ValidatedCoreDatasetPublication,
  requireCanonicalCoreDatasetPublicationBytes,
} from './coreDatasetContract.ts';
import {registerCommittedCorePublication} from './datasetRegistry.ts';
import {
  authorizeDatasetAdmin,
  type DatasetR2Bucket,
  type DatasetR2Object,
  type DatasetRuntime,
  methodNotAllowed,
  noStoreJson,
  parseBoundedContentLength,
  sha256Hex,
} from './datasetRuntime.ts';

export const CORE_DATASET_UPLOAD_BASE_PATH = '/api/admin/core-datasets/';
const DATASET_ID_HEADER = 'x-mrt-dataset-publication-id';
const DIGEST_HEADER = 'x-mrt-content-sha256';
const IMMUTABLE_CACHE_CONTROL = 'public, max-age=31536000, immutable, no-transform';
const MAX_STAGED_MANIFEST_CACHE_ENTRIES = 8;

export interface LoadedCorePublication {
  bytes: Uint8Array;
  digest: string;
  state: ValidatedCoreDatasetPublication;
}

const stagedCaches = new WeakMap<object, Map<string, Promise<LoadedCorePublication | null>>>();

function stagedCache(bucket: DatasetR2Bucket): Map<string, Promise<LoadedCorePublication | null>> {
  let cache = stagedCaches.get(bucket as object);
  if (!cache) {
    cache = new Map();
    stagedCaches.set(bucket as object, cache);
  }
  return cache;
}

function cacheStaged(
  bucket: DatasetR2Bucket,
  publicationId: string,
  publication: LoadedCorePublication | null,
): void {
  const cache = stagedCache(bucket);
  cache.delete(publicationId);
  cache.set(publicationId, Promise.resolve(publication));
  while (cache.size > MAX_STAGED_MANIFEST_CACHE_ENTRIES) {
    const key = cache.keys().next().value as string | undefined;
    if (key === undefined) break;
    cache.delete(key);
  }
}

function stagingManifestKey(publicationId: string): string {
  return `_staging/core/${publicationId}/publication.json`;
}

export function committedCoreManifestKey(publicationId: string): string {
  return `core/${publicationId}/publication.json`;
}

export function coreObjectKey(publicationId: string, path: string): string {
  return `core/${publicationId}/${path}`;
}

function contentType(path: string): string {
  return path.endsWith('.json') ? 'application/json; charset=utf-8' : 'application/octet-stream';
}

function manifestMetadata(
  publicationId: string,
  digest: string,
  bytes: number,
): Record<string, string> {
  return {
    'mrt-kind': 'core-publication-manifest',
    'mrt-publication': publicationId,
    'mrt-path': 'publication.json',
    'mrt-sha256': digest,
    'mrt-bytes': String(bytes),
  };
}

function contentMetadata(
  publicationId: string,
  record: CoreContentRecord,
): Record<string, string> {
  return {
    'mrt-kind': 'core-publication-object',
    'mrt-publication': publicationId,
    'mrt-path': record.path,
    'mrt-sha256': record.sha256,
  };
}

function manifestObjectMatches(
  object: DatasetR2Object,
  publicationId: string,
  digest: string,
  bytes: number,
): boolean {
  const metadata = object.customMetadata ?? {};
  return (
    object.size === bytes &&
    metadata['mrt-kind'] === 'core-publication-manifest' &&
    metadata['mrt-publication'] === publicationId &&
    metadata['mrt-path'] === 'publication.json' &&
    metadata['mrt-sha256'] === digest &&
    metadata['mrt-bytes'] === String(bytes)
  );
}

export function coreObjectMatches(
  object: Pick<DatasetR2Object, 'size' | 'customMetadata'>,
  publicationId: string,
  record: CoreContentRecord,
): boolean {
  const metadata = object.customMetadata ?? {};
  return (
    object.size === record.bytes &&
    metadata['mrt-kind'] === 'core-publication-object' &&
    metadata['mrt-publication'] === publicationId &&
    metadata['mrt-path'] === record.path &&
    metadata['mrt-sha256'] === record.sha256
  );
}

function storedHeaders(
  object: DatasetR2Object,
  publicationId: string,
  digest: string,
  type?: string,
): Headers {
  const headers = new Headers({
    'Cache-Control': type ? IMMUTABLE_CACHE_CONTROL : 'no-store',
    'Content-Length': String(object.size),
    [DATASET_ID_HEADER]: publicationId,
    [DIGEST_HEADER]: digest,
  });
  if (type) headers.set('Content-Type', type);
  if (object.etag) headers.set('ETag', `"${object.etag.replace(/^"|"$/g, '')}"`);
  return headers;
}

async function readManifestObject(
  object: DatasetR2Object,
  expectedPublicationId: string,
): Promise<LoadedCorePublication> {
  if (
    object.size <= 0 ||
    object.size > MAX_CORE_PUBLICATION_MANIFEST_BYTES ||
    !Number.isSafeInteger(object.size)
  ) {
    throw new Error(`Core publication manifest ${object.key} has invalid R2 size metadata.`);
  }
  const bytes = new Uint8Array(await object.arrayBuffer());
  if (bytes.byteLength !== object.size) {
    throw new Error(`Core publication manifest ${object.key} returned an incomplete body.`);
  }
  const digest = await sha256Hex(bytes);
  const state = requireCanonicalCoreDatasetPublicationBytes(bytes, expectedPublicationId);
  if (!manifestObjectMatches(object, expectedPublicationId, digest, bytes.byteLength)) {
    throw new Error(`Core publication manifest ${object.key} has invalid immutable metadata.`);
  }
  return {bytes, digest, state};
}

async function loadManifestAt(
  bucket: DatasetR2Bucket,
  key: string,
  publicationId: string,
): Promise<LoadedCorePublication | null> {
  const object = await bucket.get(key);
  return object ? readManifestObject(object, publicationId) : null;
}

export async function loadCommittedCorePublication(
  bucket: DatasetR2Bucket,
  publicationId: string,
): Promise<LoadedCorePublication | null> {
  return loadManifestAt(bucket, committedCoreManifestKey(publicationId), publicationId);
}

async function loadStaged(
  bucket: DatasetR2Bucket,
  publicationId: string,
): Promise<LoadedCorePublication | null> {
  const cache = stagedCache(bucket);
  const cached = cache.get(publicationId);
  if (cached) {
    cache.delete(publicationId);
    cache.set(publicationId, cached);
    return cached;
  }
  const operation = loadManifestAt(bucket, stagingManifestKey(publicationId), publicationId).catch(
    error => {
      cache.delete(publicationId);
      throw error;
    },
  );
  cache.set(publicationId, operation);
  while (cache.size > MAX_STAGED_MANIFEST_CACHE_ENTRIES) {
    const key = cache.keys().next().value as string | undefined;
    if (key === undefined) break;
    cache.delete(key);
  }
  return operation;
}

function bytesEqual(left: Uint8Array, right: Uint8Array): boolean {
  if (left.byteLength !== right.byteLength) return false;
  let difference = 0;
  for (let index = 0; index < left.byteLength; index += 1) difference |= left[index] ^ right[index];
  return difference === 0;
}

async function handleBegin(
  request: Request,
  bucket: DatasetR2Bucket,
  publicationId: string,
): Promise<Response> {
  if (request.method !== 'POST') return methodNotAllowed('POST');
  const contentLength = parseBoundedContentLength(request, MAX_CORE_PUBLICATION_MANIFEST_BYTES);
  const declaredDigest = request.headers.get(DIGEST_HEADER);
  if (!contentLength || !CORE_DATASET_PUBLICATION_ID_PATTERN.test(declaredDigest ?? '')) {
    return noStoreJson({error: 'Begin requires bounded length and a SHA-256 content digest.'}, 400);
  }
  if (request.headers.get('content-type')?.split(';', 1)[0].trim().toLowerCase() !== 'application/json') {
    return noStoreJson({error: 'Begin requires Content-Type application/json.'}, 415);
  }
  const bytes = new Uint8Array(await request.arrayBuffer());
  if (bytes.byteLength !== contentLength) {
    return noStoreJson({error: 'Publication manifest body length does not match Content-Length.'}, 400);
  }
  const observedDigest = await sha256Hex(bytes);
  if (observedDigest !== declaredDigest) {
    console.warn('A core publication manifest failed its declared SHA-256.', {publicationId});
    return noStoreJson({error: 'Publication manifest SHA-256 mismatch.'}, 422);
  }
  let state: ValidatedCoreDatasetPublication;
  try {
    state = requireCanonicalCoreDatasetPublicationBytes(bytes, publicationId);
  } catch (error) {
    console.warn('A core publication manifest failed the exact v1 contract.', {publicationId, error});
    return noStoreJson({error: 'Publication manifest contract validation failed.'}, 422);
  }

  const committed = await loadCommittedCorePublication(bucket, publicationId);
  if (committed) {
    if (committed.digest !== declaredDigest || !bytesEqual(committed.bytes, bytes)) {
      console.error('A content-addressed core publication ID conflicts with committed control bytes.', {
        publicationId,
      });
      return noStoreJson({error: 'Publication ID is already committed with different bytes.'}, 409);
    }
    return noStoreJson({publicationId, state: 'committed'});
  }
  const staged = await loadStaged(bucket, publicationId);
  if (staged) {
    if (staged.digest !== declaredDigest || !bytesEqual(staged.bytes, bytes)) {
      return noStoreJson({error: 'Publication has a different staged control manifest.'}, 409);
    }
    return noStoreJson({publicationId, state: 'staged'});
  }

  const stored = await bucket.put(stagingManifestKey(publicationId), bytes, {
    onlyIf: {etagDoesNotMatch: '*'},
    sha256: declaredDigest,
    httpMetadata: {contentType: 'application/json; charset=utf-8', cacheControl: 'no-store'},
    customMetadata: manifestMetadata(publicationId, declaredDigest, contentLength),
  });
  if (!stored) {
    const raced = await loadStaged(bucket, publicationId);
    if (!raced || raced.digest !== declaredDigest || !bytesEqual(raced.bytes, bytes)) {
      return noStoreJson({error: 'Publication staging raced with different bytes.'}, 409);
    }
    cacheStaged(bucket, publicationId, raced);
    return noStoreJson({publicationId, state: 'staged'});
  }
  if (!manifestObjectMatches(stored as DatasetR2Object, publicationId, declaredDigest, contentLength)) {
    console.error('R2 returned invalid metadata after core manifest staging.', {publicationId});
    return noStoreJson({error: 'R2 returned invalid staged-manifest metadata.'}, 502);
  }
  cacheStaged(bucket, publicationId, {bytes, digest: declaredDigest, state});
  return noStoreJson({publicationId, state: 'staged'}, 201);
}

async function requireStaged(
  bucket: DatasetR2Bucket,
  publicationId: string,
): Promise<LoadedCorePublication | Response> {
  const staged = await loadStaged(bucket, publicationId);
  if (staged) return staged;
  return (await loadCommittedCorePublication(bucket, publicationId))
    ? noStoreJson({error: 'Core publication is already committed.'}, 409)
    : noStoreJson({error: 'Begin the core publication before uploading objects.'}, 409);
}

function maximumForPath(path: string): number {
  if (path.startsWith('assets/')) return MAX_CORE_PACK_BYTES;
  if (path.startsWith('indexes/')) return MAX_CORE_PACK_INDEX_BYTES;
  return MAX_CORE_DOCUMENT_BYTES;
}

async function handleObject(
  request: Request,
  bucket: DatasetR2Bucket,
  publicationId: string,
  rawPath: string,
): Promise<Response> {
  if (request.method !== 'HEAD' && request.method !== 'PUT') return methodNotAllowed('HEAD, PUT');
  let path: string;
  try {
    path = decodeURIComponent(rawPath);
  } catch (error) {
    console.warn('A core object route contains malformed percent encoding.', {
      publicationId,
      rawPath,
      error,
    });
    return noStoreJson({error: 'Object path has malformed percent encoding.'}, 400);
  }
  if (path !== rawPath || path.length === 0 || path === 'publication.json') {
    return noStoreJson({error: 'Object path must use canonical ASCII form.'}, 400);
  }
  const loaded = await requireStaged(bucket, publicationId);
  if (loaded instanceof Response) return loaded;
  const record = loaded.state.contentRecordsByPath.get(path);
  if (!record) {
    console.warn('A core upload targeted an object outside publication.json.', {publicationId, path});
    return noStoreJson({error: 'Object is not declared by publication.json.'}, 400);
  }
  const key = coreObjectKey(publicationId, path);
  const existing = await bucket.head(key);
  if (existing) {
    if (!coreObjectMatches(existing as DatasetR2Object, publicationId, record)) {
      console.error('An immutable core R2 object conflicts with publication.json.', {
        publicationId,
        path,
      });
      return noStoreJson({error: 'Stored immutable object conflicts with publication.json.'}, 409);
    }
    return new Response(null, {
      status: 200,
      headers: storedHeaders(existing as DatasetR2Object, publicationId, record.sha256, contentType(path)),
    });
  }
  if (request.method === 'HEAD') {
    return new Response(null, {status: 404, headers: {'Cache-Control': 'no-store'}});
  }
  if (
    parseBoundedContentLength(request, maximumForPath(path)) !== record.bytes ||
    request.headers.get(DIGEST_HEADER) !== record.sha256 ||
    request.headers.get('if-none-match') !== '*'
  ) {
    return noStoreJson({error: 'PUT headers do not exactly match publication.json.'}, 400);
  }
  if (!request.body) return noStoreJson({error: 'PUT object body is required.'}, 400);
  let stored: DatasetR2Object | null;
  try {
    stored = (await bucket.put(key, request.body, {
      onlyIf: {etagDoesNotMatch: '*'},
      sha256: record.sha256,
      httpMetadata: {contentType: contentType(path), cacheControl: IMMUTABLE_CACHE_CONTROL},
      customMetadata: contentMetadata(publicationId, record),
    })) as DatasetR2Object | null;
  } catch (error) {
    console.warn('R2 rejected a core object body or checksum.', {publicationId, path, error});
    return noStoreJson({error: 'R2 rejected the object body or SHA-256 checksum.'}, 422);
  }
  if (!stored) return noStoreJson({error: 'Immutable object write raced with another request.'}, 409);
  if (!coreObjectMatches(stored, publicationId, record)) {
    console.error('R2 returned invalid metadata after a core object write.', {publicationId, path});
    return noStoreJson({error: 'R2 returned invalid immutable-object metadata.'}, 502);
  }
  const headers = storedHeaders(stored, publicationId, record.sha256, contentType(path));
  headers.set('Location', new URL(request.url).pathname);
  return new Response(null, {status: 201, headers});
}

async function verifyCompleteInventory(
  bucket: DatasetR2Bucket,
  publicationId: string,
  state: ValidatedCoreDatasetPublication,
): Promise<void> {
  const observed = new Map<string, DatasetR2Object>();
  const maximumPages = Math.min(state.contentRecordsByPath.size + 1, 256);
  let cursor: string | undefined;
  for (let page = 0; page < maximumPages; page += 1) {
    const result = await bucket.list({
      prefix: `core/${publicationId}/`,
      limit: 1000,
      include: ['customMetadata'],
      ...(cursor ? {cursor} : {}),
    });
    for (const object of result.objects) {
      if (observed.has(object.key)) throw new Error(`R2 listed ${object.key} more than once.`);
      observed.set(object.key, object as DatasetR2Object);
    }
    if (!result.truncated) break;
    if (!result.cursor || result.cursor === cursor) {
      throw new Error('R2 returned a truncated core inventory without a new cursor.');
    }
    cursor = result.cursor;
    if (page === maximumPages - 1) throw new Error('R2 core inventory exceeded its safe page bound.');
  }

  const expected = new Set<string>();
  for (const [path, record] of state.contentRecordsByPath) {
    const key = coreObjectKey(publicationId, path);
    expected.add(key);
    const object = observed.get(key);
    if (!object || !coreObjectMatches(object, publicationId, record)) {
      throw new Error(`R2 core object ${key} is missing or inconsistent.`);
    }
  }
  for (const key of observed.keys()) {
    if (key !== committedCoreManifestKey(publicationId) && !expected.has(key)) {
      throw new Error(`R2 core prefix contains undeclared object ${key}.`);
    }
  }
}

async function handleStatus(
  request: Request,
  bucket: DatasetR2Bucket,
  publicationId: string,
): Promise<Response> {
  if (request.method !== 'HEAD') return methodNotAllowed('HEAD');
  try {
    const committed = await loadCommittedCorePublication(bucket, publicationId);
    const loaded = committed ?? (await loadStaged(bucket, publicationId));
    if (!loaded) return new Response(null, {status: 404, headers: {'Cache-Control': 'no-store'}});
    const key = committed
      ? committedCoreManifestKey(publicationId)
      : stagingManifestKey(publicationId);
    const object = await bucket.head(key);
    if (!object || !manifestObjectMatches(object as DatasetR2Object, publicationId, loaded.digest, loaded.bytes.byteLength)) {
      throw new Error('Core publication manifest disappeared or changed during status validation.');
    }
    const headers = storedHeaders(object as DatasetR2Object, publicationId, loaded.digest);
    headers.set('X-MRT-Manifest-Bytes', String(loaded.bytes.byteLength));
    headers.set('X-MRT-Publication-State', committed ? 'committed' : 'staged');
    return new Response(null, {status: 200, headers});
  } catch (error) {
    console.error('Core publication status failed validation.', {publicationId, error});
    return noStoreJson({error: 'Core publication status is invalid.'}, 502);
  }
}

function emptyBody(request: Request): boolean {
  return request.headers.get('content-length') === '0' && !request.headers.has('transfer-encoding');
}

async function handleCommit(
  request: Request,
  runtime: DatasetRuntime,
  bucket: DatasetR2Bucket,
  publicationId: string,
): Promise<Response> {
  if (request.method !== 'POST') return methodNotAllowed('POST');
  if (!emptyBody(request)) return noStoreJson({error: 'Commit requests must have Content-Length: 0.'}, 400);
  const declaredDigest = request.headers.get(DIGEST_HEADER);
  if (!CORE_DATASET_PUBLICATION_ID_PATTERN.test(declaredDigest ?? '')) {
    return noStoreJson({error: 'Commit requires the publication manifest SHA-256 header.'}, 400);
  }
  const db = runtime.DB;
  if (!db) {
    console.error('Core commit cannot register publication because DB is unavailable.', {publicationId});
    return noStoreJson({error: 'Dataset registry storage is unavailable.'}, 503);
  }
  let loaded = await loadCommittedCorePublication(bucket, publicationId);
  let status = 200;
  if (!loaded) {
    const staged = await requireStaged(bucket, publicationId);
    if (staged instanceof Response) return staged;
    loaded = staged;
    if (loaded.digest !== declaredDigest) {
      return noStoreJson({error: 'Commit digest does not match the staged publication manifest.'}, 400);
    }
    try {
      await verifyCompleteInventory(bucket, publicationId, loaded.state);
    } catch (error) {
      console.warn('Core commit refused an incomplete or inconsistent exact R2 inventory.', {
        publicationId,
        error,
      });
      return noStoreJson({error: 'Core publication inventory is incomplete or inconsistent.'}, 409);
    }
    const stored = (await bucket.put(committedCoreManifestKey(publicationId), loaded.bytes, {
      onlyIf: {etagDoesNotMatch: '*'},
      sha256: loaded.digest,
      httpMetadata: {
        contentType: 'application/json; charset=utf-8',
        cacheControl: IMMUTABLE_CACHE_CONTROL,
      },
      customMetadata: manifestMetadata(publicationId, loaded.digest, loaded.bytes.byteLength),
    })) as DatasetR2Object | null;
    if (!stored) {
      const raced = await loadCommittedCorePublication(bucket, publicationId);
      if (!raced || raced.digest !== loaded.digest || !bytesEqual(raced.bytes, loaded.bytes)) {
        return noStoreJson({error: 'Core manifest commit raced with different bytes.'}, 409);
      }
      loaded = raced;
    } else if (!manifestObjectMatches(stored, publicationId, loaded.digest, loaded.bytes.byteLength)) {
      console.error('R2 returned invalid core commit-marker metadata.', {publicationId});
      return noStoreJson({error: 'R2 returned invalid committed-manifest metadata.'}, 502);
    }
    status = 201;
  }
  if (loaded.digest !== declaredDigest) {
    return noStoreJson({error: 'Commit digest does not match the committed publication manifest.'}, 409);
  }

  try {
    await registerCommittedCorePublication(db, {
      publicationId,
      manifestSha256: loaded.digest,
      objectCount: loaded.state.manifest.counts.objects,
      storedBytes: loaded.state.manifest.counts.storedBytes,
    });
  } catch (error) {
    console.error('Committed R2 core publication could not be registered in D1; retry is safe.', {
      publicationId,
      error,
    });
    return noStoreJson({error: 'Core publication committed but D1 registration failed; retry commit.'}, 503);
  }

  let stagingCleanup = 'complete';
  try {
    await bucket.delete(stagingManifestKey(publicationId));
    stagedCache(bucket).delete(publicationId);
  } catch (error) {
    stagingCleanup = 'failed';
    console.error('Committed core publication staging cleanup failed.', {publicationId, error});
  }
  const response = noStoreJson(
    {
      publicationId,
      objects: loaded.state.manifest.counts.objects,
      state: 'committed',
      stagingCleanup,
    },
    status,
  );
  response.headers.set('X-MRT-Staging-Cleanup', stagingCleanup);
  return response;
}

export async function handleCoreDatasetUpload(
  request: Request,
  runtime: DatasetRuntime,
  url = new URL(request.url),
): Promise<Response> {
  const authorizationFailure = await authorizeDatasetAdmin(
    request,
    runtime.CORE_DATASET_UPLOAD_TOKEN,
  );
  if (authorizationFailure) return authorizationFailure;
  const bucket = runtime.PREVIEW_ASSETS;
  if (!bucket) {
    console.error('Core dataset ingestion requires the PREVIEW_ASSETS R2 binding.');
    return noStoreJson({error: 'Core dataset storage is unavailable.'}, 503);
  }
  if (url.search !== '') return noStoreJson({error: 'Core ingestion does not accept query parameters.'}, 400);
  const publicationId = request.headers.get(DATASET_ID_HEADER);
  if (!publicationId || !CORE_DATASET_PUBLICATION_ID_PATTERN.test(publicationId)) {
    return noStoreJson({error: 'A canonical X-MRT-Dataset-Publication-ID header is required.'}, 400);
  }
  const action = url.pathname.slice(CORE_DATASET_UPLOAD_BASE_PATH.length);
  try {
    if (action === 'begin') return await handleBegin(request, bucket, publicationId);
    if (action === 'status') return await handleStatus(request, bucket, publicationId);
    if (action === 'commit') return await handleCommit(request, runtime, bucket, publicationId);
    if (action.startsWith('object/')) {
      return await handleObject(request, bucket, publicationId, action.slice('object/'.length));
    }
    return noStoreJson({error: 'Unknown core dataset ingestion operation.'}, 404);
  } catch (error) {
    console.error('Core dataset ingestion failed unexpectedly.', {publicationId, action, error});
    return noStoreJson({error: 'Core dataset ingestion failed.'}, 500);
  }
}
