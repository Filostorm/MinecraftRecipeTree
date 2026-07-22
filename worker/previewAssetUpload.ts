import {
  MAX_PREVIEW_MANIFEST_BYTES,
  GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY,
  PREVIEW_ASSET_SET_PATTERN,
  type PreviewContentRecord,
  type ValidatedPreviewManifest,
  requireContentAddressedPreviewManifest,
} from './previewAssetContract.ts';

export const PREVIEW_UPLOAD_BASE_PATH = '/api/admin/preview-assets/';

const CONTENT_DIGEST_HEADER = 'x-mrt-content-sha256';
const DATASET_ID_HEADER = 'x-mrt-dataset-publication-id';
const IMMUTABLE_CACHE_CONTROL = 'public, max-age=31536000, immutable, no-transform';
const MAX_STAGED_MANIFEST_CACHE_ENTRIES = 8;

interface PreviewR2Object {
  key: string;
  size: number;
  etag?: string;
  customMetadata?: Record<string, string>;
  arrayBuffer?(): Promise<ArrayBuffer>;
}

interface PreviewR2Objects {
  objects: PreviewR2Object[];
  truncated: boolean;
  cursor?: string;
}

export interface PreviewUploadR2Bucket {
  head(key: string): Promise<PreviewR2Object | null>;
  get(key: string): Promise<PreviewR2Object | null>;
  put(
    key: string,
    value: ReadableStream<Uint8Array> | ArrayBuffer | Uint8Array,
    options: {
      onlyIf?: {etagDoesNotMatch: string};
      sha256: string;
      httpMetadata: {contentType: string; cacheControl: string};
      customMetadata: Record<string, string>;
    },
  ): Promise<PreviewR2Object | null>;
  list(options: {
    prefix: string;
    limit: number;
    include: ['customMetadata'];
    cursor?: string;
  }): Promise<PreviewR2Objects>;
  delete(key: string): Promise<void>;
}

export interface PreviewUploadRuntime {
  PREVIEW_ASSETS?: PreviewUploadR2Bucket;
  PREVIEW_ASSET_SET_ID?: string;
  PREVIEW_UPLOAD_ASSET_SET_ID?: string;
  PREVIEW_UPLOAD_ENABLED?: string;
  PREVIEW_UPLOAD_TOKEN?: string;
}

interface LoadedManifest {
  bytes: Uint8Array;
  digest: string;
  state: ValidatedPreviewManifest;
}

const stagedManifestCaches = new WeakMap<object, Map<string, Promise<LoadedManifest | null>>>();

function stagedManifestCache(bucket: PreviewUploadR2Bucket): Map<string, Promise<LoadedManifest | null>> {
  let cache = stagedManifestCaches.get(bucket);
  if (!cache) {
    cache = new Map();
    stagedManifestCaches.set(bucket, cache);
  }
  return cache;
}

function cacheStagedManifest(
  bucket: PreviewUploadR2Bucket,
  assetSetId: string,
  value: LoadedManifest | null,
): void {
  const cache = stagedManifestCache(bucket);
  cache.delete(assetSetId);
  cache.set(assetSetId, Promise.resolve(value));
  while (cache.size > MAX_STAGED_MANIFEST_CACHE_ENTRIES) {
    const oldest = cache.keys().next().value as string | undefined;
    if (oldest === undefined) break;
    cache.delete(oldest);
  }
}

function jsonResponse(status: number, value: Record<string, unknown>): Response {
  return new Response(`${JSON.stringify(value)}\n`, {
    status,
    headers: {
      'Cache-Control': 'no-store',
      'Content-Type': 'application/json; charset=utf-8',
      'X-Content-Type-Options': 'nosniff',
    },
  });
}

function methodNotAllowed(allow: string): Response {
  return new Response('Method not allowed', {
    status: 405,
    headers: {Allow: allow, 'Cache-Control': 'no-store'},
  });
}

async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes.slice().buffer));
  return [...digest].map(value => value.toString(16).padStart(2, '0')).join('');
}

async function tokensEqual(left: string, right: string): Promise<boolean> {
  const encoder = new TextEncoder();
  const [leftDigest, rightDigest] = await Promise.all([
    crypto.subtle.digest('SHA-256', encoder.encode(left)),
    crypto.subtle.digest('SHA-256', encoder.encode(right)),
  ]);
  const leftBytes = new Uint8Array(leftDigest);
  const rightBytes = new Uint8Array(rightDigest);
  let difference = leftBytes.byteLength ^ rightBytes.byteLength;
  for (let index = 0; index < leftBytes.byteLength; index += 1) {
    difference |= leftBytes[index] ^ (rightBytes[index] ?? 0);
  }
  return difference === 0;
}

async function authorizeUpload(request: Request, configuredToken: string | undefined): Promise<Response | null> {
  if (!configuredToken) {
    console.error('The preview ingestion route is disabled because PREVIEW_UPLOAD_TOKEN is unset.');
    return new Response('Preview ingestion is disabled', {
      status: 503,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  if (configuredToken.length < 32) {
    console.error('PREVIEW_UPLOAD_TOKEN is configured but does not meet the 32-character minimum.');
    return new Response('Preview ingestion is misconfigured', {
      status: 503,
      headers: {'Cache-Control': 'no-store'},
    });
  }
  const authorization = request.headers.get('authorization');
  const candidate = authorization?.startsWith('Bearer ') ? authorization.slice(7) : '';
  if (!candidate || !(await tokensEqual(candidate, configuredToken))) {
    console.warn('A preview ingestion request failed bearer-token authentication.', {
      method: request.method,
      path: new URL(request.url).pathname,
    });
    return new Response('Unauthorized', {
      status: 401,
      headers: {
        'Cache-Control': 'no-store',
        'WWW-Authenticate': 'Bearer realm="preview-assets"',
      },
    });
  }
  return null;
}

function parseContentLength(request: Request, maximum: number): number | null {
  const raw = request.headers.get('content-length');
  if (!raw || !/^(0|[1-9]\d*)$/.test(raw)) return null;
  const bytes = Number(raw);
  return Number.isSafeInteger(bytes) && bytes > 0 && bytes <= maximum ? bytes : null;
}

function requiredDigest(request: Request): string | null {
  const digest = request.headers.get(CONTENT_DIGEST_HEADER);
  return PREVIEW_ASSET_SET_PATTERN.test(digest ?? '') ? digest : null;
}

function stagingManifestKey(assetSetId: string): string {
  return `_staging/${assetSetId}/manifest.json`;
}

function publishedManifestKey(assetSetId: string): string {
  return `${assetSetId}/manifest.json`;
}

function objectKey(assetSetId: string, path: string): string {
  return `${assetSetId}/${path}`;
}

function metadataFor(
  assetSetId: string,
  datasetPublicationId: string,
  path: string,
  record: PreviewContentRecord,
): Record<string, string> {
  return {
    'mrt-asset-set': assetSetId,
    'mrt-dataset': datasetPublicationId,
    'mrt-path': path,
    'mrt-sha256': record.sha256,
  };
}

function objectMatches(
  object: PreviewR2Object,
  assetSetId: string,
  datasetPublicationId: string,
  path: string,
  record: PreviewContentRecord,
): boolean {
  const metadata = object.customMetadata ?? {};
  return (
    object.size === record.bytes &&
    metadata['mrt-asset-set'] === assetSetId &&
    metadata['mrt-dataset'] === datasetPublicationId &&
    metadata['mrt-path'] === path &&
    metadata['mrt-sha256'] === record.sha256
  );
}

function objectHeaders(
  object: PreviewR2Object,
  datasetPublicationId: string,
  digest: string,
  contentType?: string,
): Headers {
  const headers = new Headers({
    'Cache-Control': contentType ? IMMUTABLE_CACHE_CONTROL : 'no-store',
    'Content-Length': String(object.size),
    [CONTENT_DIGEST_HEADER]: digest,
    [DATASET_ID_HEADER]: datasetPublicationId,
  });
  if (contentType) headers.set('Content-Type', contentType);
  if (object.etag) headers.set('ETag', `"${object.etag.replace(/^"|"$/g, '')}"`);
  return headers;
}

async function readManifestObject(
  object: PreviewR2Object,
  expectedAssetSetId: string,
  expectedDigest?: string,
): Promise<LoadedManifest> {
  if (
    !Number.isSafeInteger(object.size) ||
    object.size <= 0 ||
    object.size > MAX_PREVIEW_MANIFEST_BYTES ||
    typeof object.arrayBuffer !== 'function'
  ) {
    throw new Error(`Preview manifest object ${object.key} has invalid R2 metadata.`);
  }
  const bytes = new Uint8Array(await object.arrayBuffer());
  if (bytes.byteLength !== object.size) {
    throw new Error(
      `Preview manifest object ${object.key} returned ${bytes.byteLength}/${object.size} bytes.`,
    );
  }
  const digest = await sha256Hex(bytes);
  if (expectedDigest && digest !== expectedDigest) {
    throw new Error(`Preview manifest object ${object.key} failed its stored SHA-256 check.`);
  }
  let value: unknown;
  try {
    value = JSON.parse(new TextDecoder().decode(bytes)) as unknown;
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`Preview manifest object ${object.key} contains invalid JSON: ${detail}`);
  }
  return {
    bytes,
    digest,
    state: await requireContentAddressedPreviewManifest(value, expectedAssetSetId),
  };
}

async function loadStagedManifest(
  bucket: PreviewUploadR2Bucket,
  assetSetId: string,
): Promise<LoadedManifest | null> {
  const cache = stagedManifestCache(bucket);
  const cached = cache.get(assetSetId);
  if (cached) {
    cache.delete(assetSetId);
    cache.set(assetSetId, cached);
    return cached;
  }
  let operation: Promise<LoadedManifest | null>;
  operation = (async () => {
    const object = await bucket.get(stagingManifestKey(assetSetId));
    if (!object) return null;
    const expectedDigest = object.customMetadata?.['mrt-sha256'];
    if (!PREVIEW_ASSET_SET_PATTERN.test(expectedDigest ?? '')) {
      throw new Error('The staged preview manifest is missing its trusted SHA-256 metadata.');
    }
    const loaded = await readManifestObject(object, assetSetId, expectedDigest);
    const record = {path: 'manifest.json', bytes: loaded.bytes.byteLength, sha256: loaded.digest};
    if (
      !objectMatches(
        object,
        assetSetId,
        loaded.state.manifest.datasetPublicationId,
        'manifest.json',
        record,
      )
    ) {
      throw new Error('The staged preview manifest has invalid immutable R2 metadata.');
    }
    return loaded;
  })().then(
    manifest => {
      // Never retain a preflight miss in isolate-local state: another Worker isolate can stage
      // the immutable manifest immediately afterward. Positive manifests remain safe to cache.
      if (manifest === null && cache.get(assetSetId) === operation) {
        cache.delete(assetSetId);
      }
      return manifest;
    },
    error => {
      if (cache.get(assetSetId) === operation) cache.delete(assetSetId);
      throw error;
    },
  );
  cache.set(assetSetId, operation);
  while (cache.size > MAX_STAGED_MANIFEST_CACHE_ENTRIES) {
    const oldest = cache.keys().next().value as string | undefined;
    if (oldest === undefined) break;
    cache.delete(oldest);
  }
  return operation;
}

async function loadPublishedManifest(
  bucket: PreviewUploadR2Bucket,
  assetSetId: string,
): Promise<LoadedManifest | null> {
  const object = await bucket.get(publishedManifestKey(assetSetId));
  if (!object) return null;
  const expectedDigest = object.customMetadata?.['mrt-sha256'];
  if (!PREVIEW_ASSET_SET_PATTERN.test(expectedDigest ?? '')) {
    throw new Error('The published preview manifest is missing its trusted SHA-256 metadata.');
  }
  const loaded = await readManifestObject(object, assetSetId, expectedDigest);
  const record = {path: 'manifest.json', bytes: loaded.bytes.byteLength, sha256: loaded.digest};
  if (
    !objectMatches(
      object,
      assetSetId,
      loaded.state.manifest.datasetPublicationId,
      'manifest.json',
      record,
    )
  ) {
    throw new Error('The published preview manifest has invalid immutable R2 metadata.');
  }
  return loaded;
}

async function handleBegin(
  request: Request,
  bucket: PreviewUploadR2Bucket,
  assetSetId: string,
): Promise<Response> {
  if (request.method !== 'POST') return methodNotAllowed('POST');
  const contentLength = parseContentLength(request, MAX_PREVIEW_MANIFEST_BYTES);
  const declaredDigest = requiredDigest(request);
  if (!contentLength || !declaredDigest) {
    return jsonResponse(400, {
      error: 'Manifest uploads require bounded Content-Length and X-MRT-Content-SHA256 headers.',
    });
  }
  const contentType = request.headers.get('content-type')?.split(';', 1)[0].trim().toLowerCase();
  if (contentType !== 'application/json') {
    return jsonResponse(415, {error: 'Manifest uploads require Content-Type application/json.'});
  }
  const bytes = new Uint8Array(await request.arrayBuffer());
  if (bytes.byteLength !== contentLength) {
    return jsonResponse(400, {
      error: `Manifest body has ${bytes.byteLength} bytes; Content-Length declares ${contentLength}.`,
    });
  }
  const observedDigest = await sha256Hex(bytes);
  if (observedDigest !== declaredDigest) {
    console.error('A preview staging manifest failed its declared SHA-256 check.', {assetSetId});
    return jsonResponse(422, {error: 'Manifest SHA-256 mismatch.'});
  }

  let value: unknown;
  try {
    value = JSON.parse(new TextDecoder().decode(bytes)) as unknown;
  } catch (error) {
    return jsonResponse(422, {
      error: `Manifest JSON is invalid: ${error instanceof Error ? error.message : String(error)}`,
    });
  }

  let state: ValidatedPreviewManifest;
  try {
    state = await requireContentAddressedPreviewManifest(value, assetSetId);
  } catch (error) {
    console.error('A preview staging manifest failed contract validation.', {assetSetId, error});
    return jsonResponse(422, {error: 'Manifest does not satisfy the preview sidecar contract.'});
  }
  if (state.manifest.publicationPolicy === GTNH_STRUCTURED_DATA_ONLY_PUBLICATION_POLICY) {
    console.info('Preview ingestion accepted the exact GTNH manifest-only rights exclusion.', {
      assetSetId,
      datasetPublicationId: state.manifest.datasetPublicationId,
      recipes: state.manifest.counts.recipes,
      contentObjects: state.contentRecordsByPath.size,
    });
  }
  const {datasetPublicationId} = state.manifest;
  if (request.headers.get(DATASET_ID_HEADER) !== datasetPublicationId) {
    return jsonResponse(400, {error: 'Dataset publication header does not match the manifest.'});
  }

  const published = await loadPublishedManifest(bucket, assetSetId);
  if (published) {
    if (published.digest !== declaredDigest || !published.bytes.every((byte, index) => byte === bytes[index])) {
      console.error('An immutable preview asset-set ID was reused for different manifest bytes.', {
        assetSetId,
      });
      return jsonResponse(409, {error: 'Asset set is already published with different bytes.'});
    }
    return jsonResponse(200, {assetSetId, datasetPublicationId, state: 'published'});
  }

  const existing = await bucket.get(stagingManifestKey(assetSetId));
  if (existing) {
    const staged = await readManifestObject(existing, assetSetId, declaredDigest);
    if (!staged.bytes.every((byte, index) => byte === bytes[index])) {
      return jsonResponse(409, {error: 'Asset set has a different staged manifest.'});
    }
    cacheStagedManifest(bucket, assetSetId, staged);
    return jsonResponse(200, {assetSetId, datasetPublicationId, state: 'staging'});
  }

  const record = {path: 'manifest.json', bytes: contentLength, sha256: declaredDigest};
  const stored = await bucket.put(stagingManifestKey(assetSetId), bytes, {
    onlyIf: {etagDoesNotMatch: '*'},
    sha256: declaredDigest,
    httpMetadata: {contentType: 'application/json; charset=utf-8', cacheControl: 'no-store'},
    customMetadata: metadataFor(assetSetId, datasetPublicationId, 'manifest.json', record),
  });
  if (!stored) {
    const raced = await bucket.get(stagingManifestKey(assetSetId));
    if (!raced) return jsonResponse(503, {error: 'Manifest staging race could not be resolved.'});
    const staged = await readManifestObject(raced, assetSetId, declaredDigest);
    if (!staged.bytes.every((byte, index) => byte === bytes[index])) {
      return jsonResponse(409, {error: 'Asset set raced with a different staged manifest.'});
    }
    cacheStagedManifest(bucket, assetSetId, staged);
    return jsonResponse(200, {assetSetId, datasetPublicationId, state: 'staging'});
  }
  if (stored.size !== contentLength) {
    console.error('R2 returned an invalid size after staging a preview manifest.', {
      assetSetId,
      expected: contentLength,
      stored: stored.size,
    });
    return jsonResponse(502, {error: 'R2 returned invalid manifest metadata.'});
  }
  cacheStagedManifest(bucket, assetSetId, {bytes, digest: declaredDigest, state});
  return jsonResponse(201, {assetSetId, datasetPublicationId, state: 'staging'});
}

async function requireStagedManifest(
  bucket: PreviewUploadR2Bucket,
  assetSetId: string,
): Promise<LoadedManifest | Response> {
  try {
    const staged = await loadStagedManifest(bucket, assetSetId);
    if (staged) return staged;
    const published = await loadPublishedManifest(bucket, assetSetId);
    return published
      ? jsonResponse(409, {error: 'Asset set is already immutable and published.'})
      : jsonResponse(409, {error: 'Begin the upload with a validated staging manifest first.'});
  } catch (error) {
    console.error('The preview staging manifest could not be loaded safely.', {assetSetId, error});
    return jsonResponse(502, {error: 'Staged manifest is unavailable or invalid.'});
  }
}

function responseForStoredObject(
  request: Request,
  object: PreviewR2Object,
  datasetPublicationId: string,
  digest: string,
  path: string,
): Response {
  const contentType = path.startsWith('categories/')
    ? 'application/json; charset=utf-8'
    : 'application/octet-stream';
  return new Response(request.method === 'HEAD' ? null : undefined, {
    status: 200,
    headers: objectHeaders(object, datasetPublicationId, digest, contentType),
  });
}

async function handleObject(
  request: Request,
  bucket: PreviewUploadR2Bucket,
  assetSetId: string,
  rawPath: string,
): Promise<Response> {
  if (request.method !== 'HEAD' && request.method !== 'PUT') {
    return methodNotAllowed('HEAD, PUT');
  }
  let path: string;
  try {
    path = decodeURIComponent(rawPath);
  } catch (error) {
    console.error('A preview ingestion object path has malformed percent encoding.', {
      assetSetId,
      rawPath,
      error,
    });
    return jsonResponse(400, {error: 'Object path has malformed percent encoding.'});
  }
  if (path !== rawPath || !path || path === 'manifest.json') {
    return jsonResponse(400, {error: 'Object path must use canonical ASCII form.'});
  }

  const loaded = await requireStagedManifest(bucket, assetSetId);
  if (loaded instanceof Response) return loaded;
  const {manifest} = loaded.state;
  const record = loaded.state.contentRecordsByPath.get(path);
  if (!record) {
    console.warn('A preview ingestion request targeted an object absent from the staged manifest.', {
      assetSetId,
      path,
    });
    return jsonResponse(400, {error: 'Object is not declared by the staged manifest.'});
  }

  const key = objectKey(assetSetId, path);
  const existing = await bucket.head(key);
  if (existing) {
    if (!objectMatches(existing, assetSetId, manifest.datasetPublicationId, path, record)) {
      console.error('An immutable R2 preview object conflicts with the staged manifest.', {
        assetSetId,
        path,
      });
      return jsonResponse(409, {error: 'Stored immutable object conflicts with the manifest.'});
    }
    return responseForStoredObject(
      request,
      existing,
      manifest.datasetPublicationId,
      record.sha256,
      path,
    );
  }
  if (request.method === 'HEAD') {
    return new Response(null, {status: 404, headers: {'Cache-Control': 'no-store'}});
  }

  if (
    parseContentLength(request, record.bytes) !== record.bytes ||
    requiredDigest(request) !== record.sha256 ||
    request.headers.get(DATASET_ID_HEADER) !== manifest.datasetPublicationId ||
    request.headers.get('if-none-match') !== '*'
  ) {
    return jsonResponse(400, {
      error: 'PUT headers must exactly match the staged immutable object declaration.',
    });
  }
  if (!request.body) return jsonResponse(400, {error: 'PUT request body is required.'});

  const contentType = path.startsWith('categories/')
    ? 'application/json; charset=utf-8'
    : 'application/octet-stream';
  let stored: PreviewR2Object | null;
  try {
    stored = await bucket.put(key, request.body, {
      onlyIf: {etagDoesNotMatch: '*'},
      sha256: record.sha256,
      httpMetadata: {contentType, cacheControl: IMMUTABLE_CACHE_CONTROL},
      customMetadata: metadataFor(assetSetId, manifest.datasetPublicationId, path, record),
    });
  } catch (error) {
    console.error('R2 rejected a preview object upload.', {assetSetId, path, error});
    return jsonResponse(422, {error: 'R2 rejected the object body or SHA-256 checksum.'});
  }
  if (!stored) {
    return new Response(null, {
      status: 409,
      headers: {'Cache-Control': 'no-store', 'Content-Type': 'application/json; charset=utf-8'},
    });
  }
  if (!objectMatches(stored, assetSetId, manifest.datasetPublicationId, path, record)) {
    console.error('R2 returned unexpected metadata after a preview object upload.', {
      assetSetId,
      path,
    });
    return jsonResponse(502, {error: 'R2 returned invalid object metadata.'});
  }
  const headers = objectHeaders(stored, manifest.datasetPublicationId, record.sha256, contentType);
  headers.set('Location', new URL(request.url).pathname);
  return new Response(null, {status: 201, headers});
}

async function verifyCompleteInventory(
  bucket: PreviewUploadR2Bucket,
  assetSetId: string,
  state: ValidatedPreviewManifest,
): Promise<void> {
  const observed = new Map<string, PreviewR2Object>();
  // R2 normally returns up to 1,000 objects. Bound pathological pagination well below the
  // Workers subrequest ceiling so a corrupt cursor fails here with a diagnostic response.
  const maxPages = Math.min(state.contentRecordsByPath.size + 1, 256);
  let cursor: string | undefined;
  for (let page = 0; page < maxPages; page += 1) {
    const result = await bucket.list({
      prefix: `${assetSetId}/`,
      limit: 1000,
      include: ['customMetadata'],
      ...(cursor ? {cursor} : {}),
    });
    for (const object of result.objects) {
      if (observed.has(object.key)) throw new Error(`R2 listed ${object.key} more than once.`);
      observed.set(object.key, object);
    }
    if (!result.truncated) break;
    if (!result.cursor || result.cursor === cursor) {
      throw new Error('R2 returned a truncated inventory without a new cursor.');
    }
    cursor = result.cursor;
    if (page === maxPages - 1) {
      throw new Error('R2 inventory exceeded its manifest-derived safe page bound.');
    }
  }

  const expectedKeys = new Set<string>();
  for (const [path, record] of state.contentRecordsByPath) {
    const key = objectKey(assetSetId, path);
    expectedKeys.add(key);
    const object = observed.get(key);
    if (!object || !objectMatches(object, assetSetId, state.manifest.datasetPublicationId, path, record)) {
      throw new Error(`R2 object ${key} is missing or does not match the staged manifest.`);
    }
  }
  for (const key of observed.keys()) {
    if (key !== publishedManifestKey(assetSetId) && !expectedKeys.has(key)) {
      throw new Error(`R2 contains unexpected object ${key} in the immutable asset-set prefix.`);
    }
  }
}

async function handlePublicationStatus(
  request: Request,
  bucket: PreviewUploadR2Bucket,
  assetSetId: string,
): Promise<Response> {
  if (request.method !== 'HEAD') return methodNotAllowed('HEAD');
  try {
    const published = await loadPublishedManifest(bucket, assetSetId);
    const loaded = published ?? (await loadStagedManifest(bucket, assetSetId));
    if (!loaded) return new Response(null, {status: 404, headers: {'Cache-Control': 'no-store'}});
    const key = published ? publishedManifestKey(assetSetId) : stagingManifestKey(assetSetId);
    const object = await bucket.head(key);
    if (!object) throw new Error('Preview manifest disappeared between its GET and HEAD checks.');
    const headers = objectHeaders(
      object,
      loaded.state.manifest.datasetPublicationId,
      loaded.digest,
    );
    headers.set('X-MRT-Manifest-Bytes', String(loaded.bytes.byteLength));
    headers.set('X-MRT-Publication-State', published ? 'committed' : 'staged');
    return new Response(null, {
      status: 200,
      headers,
    });
  } catch (error) {
    console.error('Preview publication status validation failed.', {assetSetId, error});
    return jsonResponse(502, {error: 'Preview publication status is unavailable or invalid.'});
  }
}

async function handleCommit(
  request: Request,
  bucket: PreviewUploadR2Bucket,
  assetSetId: string,
): Promise<Response> {
  if (request.method !== 'POST') return methodNotAllowed('POST');
  if (
    request.headers.get('content-length') !== '0' ||
    request.headers.has('transfer-encoding')
  ) {
    return jsonResponse(400, {error: 'Commit requests must not contain a body.'});
  }
  if (request.body !== null) {
    let bodyBytes: ArrayBuffer;
    try {
      bodyBytes = await request.arrayBuffer();
    } catch (error) {
      console.warn('Preview asset-set commit body framing could not be validated.', {
        assetSetId,
        error,
      });
      return jsonResponse(400, {error: 'Commit request body framing is invalid.'});
    }
    if (bodyBytes.byteLength !== 0) {
      console.warn('Preview asset-set commit included bytes despite Content-Length: 0.', {
        assetSetId,
        receivedBytes: bodyBytes.byteLength,
      });
      return jsonResponse(400, {error: 'Commit requests must not contain a body.'});
    }
  }

  const published = await loadPublishedManifest(bucket, assetSetId);
  if (published) {
    return jsonResponse(200, {
      assetSetId,
      datasetPublicationId: published.state.manifest.datasetPublicationId,
      objects: published.state.contentRecordsByPath.size,
      state: 'published',
    });
  }

  const loaded = await requireStagedManifest(bucket, assetSetId);
  if (loaded instanceof Response) return loaded;
  try {
    await verifyCompleteInventory(bucket, assetSetId, loaded.state);
  } catch (error) {
    console.error('Preview asset-set commit failed complete R2 inventory validation.', {
      assetSetId,
      error,
    });
    return jsonResponse(409, {error: 'R2 inventory is incomplete or inconsistent.'});
  }

  const manifestRecord = {
    path: 'manifest.json',
    bytes: loaded.bytes.byteLength,
    sha256: loaded.digest,
  };
  const stored = await bucket.put(publishedManifestKey(assetSetId), loaded.bytes, {
    onlyIf: {etagDoesNotMatch: '*'},
    sha256: loaded.digest,
    httpMetadata: {
      contentType: 'application/json; charset=utf-8',
      cacheControl: IMMUTABLE_CACHE_CONTROL,
    },
    customMetadata: metadataFor(
      assetSetId,
      loaded.state.manifest.datasetPublicationId,
      'manifest.json',
      manifestRecord,
    ),
  });
  if (!stored) {
    const raced = await loadPublishedManifest(bucket, assetSetId);
    if (!raced || raced.digest !== loaded.digest) {
      return jsonResponse(409, {error: 'Manifest commit raced with different published bytes.'});
    }
  } else if (
    !objectMatches(
      stored,
      assetSetId,
      loaded.state.manifest.datasetPublicationId,
      'manifest.json',
      manifestRecord,
    )
  ) {
    console.error('R2 returned unexpected metadata after the preview manifest commit.', {
      assetSetId,
    });
    return jsonResponse(502, {error: 'R2 returned invalid committed-manifest metadata.'});
  }

  let stagingCleanup = 'complete';
  try {
    await bucket.delete(stagingManifestKey(assetSetId));
    stagedManifestCache(bucket).delete(assetSetId);
  } catch (error) {
    stagingCleanup = 'failed';
    console.error('Published preview manifest staging cleanup failed.', {assetSetId, error});
  }
  const response = jsonResponse(201, {
    assetSetId,
    datasetPublicationId: loaded.state.manifest.datasetPublicationId,
    objects: loaded.state.contentRecordsByPath.size,
    stagingCleanup,
    state: 'published',
  });
  response.headers.set('X-MRT-Staging-Cleanup', stagingCleanup);
  return response;
}

export async function handlePreviewAssetUpload(
  request: Request,
  runtime: PreviewUploadRuntime,
  url = new URL(request.url),
): Promise<Response> {
  if (runtime.PREVIEW_UPLOAD_ENABLED !== 'true') {
    console.error(
      'The preview ingestion route is disabled because PREVIEW_UPLOAD_ENABLED is not true.',
    );
    return jsonResponse(503, {error: 'Preview ingestion is disabled.'});
  }
  const authorizationFailure = await authorizeUpload(request, runtime.PREVIEW_UPLOAD_TOKEN);
  if (authorizationFailure) return authorizationFailure;
  const bucket = runtime.PREVIEW_ASSETS;
  if (!bucket) {
    console.error('Preview ingestion requires the PREVIEW_ASSETS R2 binding.');
    return jsonResponse(503, {error: 'Preview ingestion storage is misconfigured.'});
  }
  const configuredAssetSetId = runtime.PREVIEW_UPLOAD_ASSET_SET_ID;
  if (!configuredAssetSetId) {
    console.error(
      'Preview ingestion is enabled but PREVIEW_UPLOAD_ASSET_SET_ID is unset; ' +
        'the serving PREVIEW_ASSET_SET_ID is never used as an upload fallback.',
    );
    return jsonResponse(503, {error: 'Preview ingestion target is not configured.'});
  }
  if (!PREVIEW_ASSET_SET_PATTERN.test(configuredAssetSetId)) {
    console.error(
      'PREVIEW_UPLOAD_ASSET_SET_ID must be a lowercase SHA-256 digest; ' +
        `received ${JSON.stringify(configuredAssetSetId)}.`,
    );
    return jsonResponse(503, {error: 'Preview ingestion target is misconfigured.'});
  }

  const remainder = url.pathname.slice(PREVIEW_UPLOAD_BASE_PATH.length);
  const slash = remainder.indexOf('/');
  const assetSetId = slash < 0 ? remainder : remainder.slice(0, slash);
  const action = slash < 0 ? '' : remainder.slice(slash + 1);
  if (!PREVIEW_ASSET_SET_PATTERN.test(assetSetId) || assetSetId !== configuredAssetSetId) {
    console.warn('A preview ingestion request targeted an unconfigured asset-set identity.', {
      assetSetId,
    });
    return jsonResponse(404, {error: 'Preview asset set is not configured.'});
  }

  try {
    if (action === 'begin') return await handleBegin(request, bucket, assetSetId);
    if (action === 'commit') return await handleCommit(request, bucket, assetSetId);
    if (action === 'status') {
      return await handlePublicationStatus(request, bucket, assetSetId);
    }
    if (action.startsWith('objects/')) {
      return await handleObject(request, bucket, assetSetId, action.slice('objects/'.length));
    }
    return jsonResponse(404, {error: 'Unknown preview ingestion operation.'});
  } catch (error) {
    console.error('Preview ingestion failed unexpectedly.', {
      assetSetId,
      action,
      error,
    });
    return jsonResponse(500, {error: 'Preview ingestion failed.'});
  }
}
